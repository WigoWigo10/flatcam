package org.flatcam.cam.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.convert.OutlineToArea;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class ObjectMergeTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @TempDir
    Path directory;

    private GerberImage gerber(String name, String units, String aperture, String flash) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MO" + units + "*%",
                "%ADD10" + aperture + "*%", "D10*", flash + "D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    @Test
    void joinKeepsBothCoppersAndGivesAClashingApertureANewCode() throws Exception {
        GerberImage small = gerber("a.gbr", "MM", "C,1.0", "X10000Y10000");
        GerberImage large = gerber("b.gbr", "MM", "C,2.0", "X50000Y50000");
        GerberImage joined = GerberJoin.join(List.of(small, large));
        assertEquals(small.solidGeometry().getArea() + large.solidGeometry().getArea(),
                joined.solidGeometry().getArea(), 1e-6);
        assertEquals(2, joined.apertures().size());
        assertTrue(joined.apertures().containsKey("10"));
        assertTrue(joined.apertures().containsKey("11"));
        assertEquals(2, joined.shapes().size());
        assertEquals("11", joined.shapes().get(1).apertureCode());
    }

    @Test
    void joinSharesIdenticalApertures() throws Exception {
        GerberImage first = gerber("a.gbr", "MM", "C,1.0", "X10000Y10000");
        GerberImage second = gerber("b.gbr", "MM", "C,1.0", "X50000Y50000");
        GerberImage joined = GerberJoin.join(List.of(first, second));
        assertEquals(1, joined.apertures().size());
        assertEquals("10", joined.shapes().get(1).apertureCode());
    }

    @Test
    void joinNeedsTwoGerbersWithTheSameUnits() throws Exception {
        GerberImage millimetres = gerber("a.gbr", "MM", "C,1.0", "X10000Y10000");
        GerberImage inches = gerber("b.gbr", "IN", "C,0.05", "X10000Y10000");
        assertThrows(IllegalArgumentException.class, () -> GerberJoin.join(List.of(millimetres)));
        assertThrows(IllegalArgumentException.class, () -> GerberJoin.join(List.of(millimetres, inches)));
    }

    @Test
    void outlineToAreaFillsAllAdjacentFacesInsteadOfPickingOne() {
        Geometry outline = FACTORY.createMultiLineString(new org.locationtech.jts.geom.LineString[]{
                FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0),
                        new Coordinate(10, 5), new Coordinate(0, 5), new Coordinate(0, 0)}),
                FACTORY.createLineString(new Coordinate[]{new Coordinate(5, 0), new Coordinate(5, 5)})});
        OutlineToArea.Result result = OutlineToArea.convert(outline);
        assertEquals(2, result.candidates());
        assertEquals(50, result.area().getArea(), 1e-9);
    }

    @Test
    void outlineToAreaClosesSegmentsThatMissByFloatingPointRounding() {
        double hair = 4.4e-16;
        Geometry outline = FACTORY.createMultiLineString(new org.locationtech.jts.geom.LineString[]{
                FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)}),
                FACTORY.createLineString(new Coordinate[]{new Coordinate(10, hair), new Coordinate(10, 5)}),
                FACTORY.createLineString(new Coordinate[]{new Coordinate(10 - hair, 5), new Coordinate(0, 5)}),
                FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 5 + hair), new Coordinate(0, 0)})});
        assertEquals(50, OutlineToArea.convert(outline).area().getArea(), 1e-6);
    }

    @Test
    void outlineToAreaRejectsAnOpenOutline() {
        Geometry open = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0),
                new Coordinate(10, 5)});
        assertThrows(IllegalArgumentException.class, () -> OutlineToArea.convert(open));
    }
}
