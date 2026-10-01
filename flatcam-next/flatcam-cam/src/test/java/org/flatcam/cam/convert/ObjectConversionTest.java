package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

class ObjectConversionTest {

    @TempDir
    Path directory;

    private static Geometry wkt(String text) throws ParseException {
        return new WKTReader().read(text);
    }

    private ExcellonImage drills() {
        return ExcellonImage.of("MM", Map.of(1, 1.0, 2, 0.8),
                List.of(new ExcellonImage.Drill(1, 5, 5), new ExcellonImage.Drill(2, 10, 5)),
                List.of(new ExcellonImage.Slot(2, 20, 5, 24, 5)), null);
    }

    /** Two round pads (2.0 and 3.0), a rectangular pad 4 x 2 and a two-point stroke 0.5 wide. */
    private GerberImage pads() throws IOException {
        Path file = directory.resolve("pads.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,2.0*%",
                "%ADD11C,3.0*%", "%ADD12R,4.0X2.0*%", "%ADD13C,0.5*%",
                "D10*", "X100000Y100000D03*", "X200000Y100000D03*", "D11*", "X300000Y100000D03*",
                "D12*", "X400000Y100000D03*", "D13*", "X500000Y100000D02*", "X700000Y100000D01*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    @Test
    void solidsAreFlattenedToPolygonsEvenWhenNestedInCollections() throws Exception {
        Geometry nested = wkt("GEOMETRYCOLLECTION(MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0))), "
                + "GEOMETRYCOLLECTION(POLYGON((5 5, 6 5, 6 6, 5 6, 5 5))))");
        Geometry flat = ObjectConversion.solidToGeometry(nested);
        assertEquals(2, flat.getNumGeometries());
        assertEquals(2.0, flat.getArea(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> ObjectConversion.solidToGeometry(wkt("POLYGON EMPTY")));
    }

    @Test
    void excellonBecomesAGerberWithOneRoundApertureEachAndFlashesAndSlots() {
        GerberImage gerber = ObjectConversion.excellonToGerber(drills());
        assertEquals(2, gerber.apertures().size());
        assertEquals(1.0, gerber.apertures().get("10").width, 1e-9);
        assertEquals(0.8, gerber.apertures().get("11").width, 1e-9);
        assertEquals(3, gerber.shapes().size());
        double expected = Math.PI * 0.25 + Math.PI * 0.16 + (Math.PI * 0.16 + 4 * 0.8);
        assertEquals(expected, gerber.solidGeometry().getArea(), 0.06);
    }

    @Test
    void geometryBecomesAGerberWithRegionsAndLinesStrokedByTheirTool() throws Exception {
        Geometry polygon = wkt("POLYGON((0 0, 10 0, 10 5, 0 5, 0 0))");
        GerberImage region = ObjectConversion.geometryToGerber("MM", polygon, List.of());
        assertEquals(50.0, region.solidGeometry().getArea(), 1e-9);

        Geometry line = wkt("LINESTRING(0 0, 10 0)");
        assertThrows(IllegalArgumentException.class, () -> ObjectConversion.geometryToGerber("MM", line, List.of()));
        GerberImage stroked = ObjectConversion.geometryToGerber("MM", line, List.of(new ToolGeometry(0.4, line)));
        assertEquals(1, stroked.apertures().size());
        assertEquals(10 * 0.4 + Math.PI * 0.04, stroked.solidGeometry().getArea(), 0.02);
    }

    @Test
    void geometryBecomesDrillsAtTheCentreOfEachClosedShapeWithTheSmallerSideAsDiameter() throws Exception {
        Geometry shapes = wkt("GEOMETRYCOLLECTION(POLYGON((0 0, 2 0, 2 2, 0 2, 0 0)), "
                + "POLYGON((10 0, 14 0, 14 2, 10 2, 10 0)), POLYGON((20 0, 22 0, 22 2, 20 2, 20 0)), LINESTRING(0 0, 5 5))");
        ExcellonImage image = ObjectConversion.geometryToExcellon("MM", shapes);
        assertEquals(3, image.totalDrills());
        assertEquals(1, image.toolDiameters().size(), "all three are 2 wide in their smaller side: one tool");
        assertEquals(2.0, image.toolDiameters().get(1), 1e-12);
        assertTrue(image.drills().stream().anyMatch(d -> Math.abs(d.x() - 12) < 1e-9 && Math.abs(d.y() - 1) < 1e-9));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectConversion.geometryToExcellon("MM", wkt("LINESTRING(0 0, 5 5)")));
    }

    @Test
    void gerberFlashesBecomeDrillsAndTwoPointStrokesBecomeSlots() throws IOException {
        ExcellonImage image = ObjectConversion.gerberToExcellon(pads());
        // Flashes: 2.0 (x2), 3.0, and the 4 x 2 rectangle (smaller side 2.0): tools 2.0 and 3.0.
        assertEquals(4, image.totalDrills());
        assertEquals(3, image.toolDiameters().size());
        assertTrue(image.toolDiameters().containsValue(2.0) && image.toolDiameters().containsValue(3.0)
                && image.toolDiameters().containsValue(0.5));
        assertEquals(1, image.slots().size());
        assertEquals(50.0, Math.min(image.slots().get(0).x1(), image.slots().get(0).x2()), 1e-6);
        assertEquals(70.0, Math.max(image.slots().get(0).x1(), image.slots().get(0).x2()), 1e-6);
    }

    @Test
    void singleAndMultiGeoConvertBackAndForth() throws Exception {
        Geometry geometry = wkt("MULTILINESTRING((0 0, 5 0), (0 1, 5 1))");
        List<ToolGeometry> multi = ObjectConversion.singleToMulti(geometry, 0.3);
        assertEquals(1, multi.size());
        assertEquals(0.3, multi.get(0).toolDiameter(), 1e-9);
        Geometry back = ObjectConversion.multiToSingle(List.of(multi.get(0),
                new ToolGeometry(0.2, wkt("LINESTRING(0 2, 5 2)"))));
        assertEquals(3, back.getNumGeometries());
        assertEquals(15.0, back.getLength(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> ObjectConversion.singleToMulti(geometry, 0));
        assertThrows(IllegalArgumentException.class, () -> ObjectConversion.multiToSingle(List.of()));
    }

    @Test
    void toolDiametersAreStoredAtFourDecimalsWithoutFloatNoise() throws Exception {
        // 1.8000000000000114 and 1.7999999999999 are the same tool.
        Geometry shapes = wkt("GEOMETRYCOLLECTION(POLYGON((0 0, 1.8000000000000114 0, 1.8000000000000114 1.8000000000000114, "
                + "0 1.8000000000000114, 0 0)), POLYGON((5 0, 6.7999999999999 0, 6.7999999999999 1.7999999999999, "
                + "5 1.7999999999999, 5 0)))");
        ExcellonImage image = ObjectConversion.geometryToExcellon("MM", shapes);
        assertEquals(1, image.toolDiameters().size());
        assertEquals(1.8, image.toolDiameters().get(1), 1e-12);
    }
}
