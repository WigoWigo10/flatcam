package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.gerber.edit.GerberEditSession;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

class GerberExporterTest {

    private final GerberParser parser = new GerberParser();
    private final GerberExporter exporter = new GerberExporter();

    @Test
    void editedObjectWithoutSourceFileExportsTheCurrentGeometry() {
        GerberImage source = parser.parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));
        GerberEditSession editor = new GerberEditSession("board.gbr", source);
        editor.clickSelect(0, 0, false);
        assertTrue(editor.moveSelected(10, 20));
        GerberImage edited = editor.apply().image();

        String gerber = exporter.export(edited);
        GerberImage reopened = parser.parse(List.of(gerber));

        assertEquals("MM", reopened.units());
        assertEquals(9, reopened.bounds()[0], 1e-6);
        assertEquals(19.5, reopened.bounds()[1], 1e-6);
        assertSameCopper(edited.solidGeometry(), reopened.solidGeometry());
        assertFalse(gerber.contains("X0Y0D03*"), "the old source must not be copied");
    }

    @Test
    void exportReopensInchesAndTransformedGeometry() {
        GerberImage source = parser.parse(List.of(
                "%FSLAX24Y24*%", "%MOIN*%", "%ADD10C,0.1*%", "D10*", "X10000Y20000D03*", "M02*"));
        GerberImage transformed = source.transformed(new TransformOp.Offset(-2.75, 1.5));

        GerberImage reopened = parser.parse(List.of(exporter.export(transformed)));

        assertEquals("IN", reopened.units());
        assertSameCopper(transformed.solidGeometry(), reopened.solidGeometry());
    }

    @Test
    void holesAndIslandsSurviveEvenWhenIslandPrecedesParentInCollection() throws ParseException {
        Geometry geometry = new WKTReader().read("MULTIPOLYGON ("
                + "((4 4, 6 4, 6 6, 4 6, 4 4)),"
                + "((0 0, 10 0, 10 10, 0 10, 0 0),"
                + "(2 2, 2 8, 8 8, 8 2, 2 2)))");
        GerberImage image = GerberImage.of("MM", Map.of(), geometry, null, Map.of());

        String gerber = exporter.export(image);
        GerberImage reopened = parser.parse(List.of(gerber));

        assertEquals(3, countOccurrences(gerber, "G36*"));
        assertEquals(1, countOccurrences(gerber, "%LPC*%"));
        assertSameCopper(geometry, reopened.solidGeometry());
    }

    @Test
    void emptyImageStillProducesValidGerber() {
        GerberImage empty = parser.parse(List.of("%FSLAX24Y24*%", "%MOMM*%", "M02*"));

        GerberImage reopened = parser.parse(List.of(exporter.export(empty)));

        assertTrue(reopened.isEmpty());
    }

    @Test
    void realBoardFileCanBeExportedAndReopened(@TempDir Path temporaryDirectory) throws IOException {
        Path source = findRepoRoot().resolve("tests/gerber_files/detector_copper_top.gbr");
        GerberImage original = parser.parse(source);
        Path exported = temporaryDirectory.resolve("edited.gbr");

        Files.writeString(exported, "old content");
        exporter.write(original, exported);
        GerberImage reopened = parser.parse(exported);

        assertEquals(original.units(), reopened.units());
        assertSameCopper(original.solidGeometry(), reopened.solidGeometry());
    }

    @Test
    void rejectsCoordinatesOutsideGerberFormatRange() throws ParseException {
        Geometry huge = new WKTReader().read("POLYGON ((1000000 0, 1000001 0, 1000001 1, 1000000 1, 1000000 0))");
        GerberImage image = GerberImage.of("MM", Map.of(), huge, null, Map.of());

        assertThrows(IllegalArgumentException.class, () -> exporter.export(image));
    }

    @Test
    void failedExportDoesNotOverwriteAnExistingFile(@TempDir Path temporaryDirectory)
            throws IOException, ParseException {
        Geometry huge = new WKTReader().read("POLYGON ((1000000 0, 1000001 0, 1000001 1, 1000000 1, 1000000 0))");
        GerberImage image = GerberImage.of("MM", Map.of(), huge, null, Map.of());
        Path destination = temporaryDirectory.resolve("board.gbr");
        Files.writeString(destination, "original data");

        assertThrows(IllegalArgumentException.class, () -> exporter.write(image, destination));
        assertEquals("original data", Files.readString(destination));
    }

    @Test
    void rejectsFeaturesThatDisappearAtOutputPrecision() throws ParseException {
        Geometry tiny = new WKTReader().read("POLYGON ((0 0, 0.0000004 0, 0.0000004 0.0000004, 0 0.0000004, 0 0))");
        GerberImage image = GerberImage.of("MM", Map.of(), tiny, null, Map.of());

        assertThrows(IllegalArgumentException.class, () -> exporter.export(image));
    }

    private static int countOccurrences(String text, String substring) {
        return (text.length() - text.replace(substring, "").length()) / substring.length();
    }

    private static Path findRepoRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve("tests/gerber_files"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Could not locate Gerber fixtures");
    }

    private static void assertSameCopper(Geometry expected, Geometry actual) {
        assertTrue(expected.symDifference(actual).getArea() < 1e-5,
                () -> "Copper differs: expected " + expected + ", actual " + actual);
    }
}
