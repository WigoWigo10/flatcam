package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class SubtractTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @TempDir
    Path directory;

    private GerberImage gerber(String name, String aperture, String... flashes) throws IOException {
        List<String> lines = new java.util.ArrayList<>(List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10" + aperture + "*%", "D10*"));
        for (String flash : flashes) {
            lines.add(flash + "D03*");
        }
        lines.add("M02*");
        Path file = directory.resolve(name);
        Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        return new GerberParser().parse(file);
    }

    @Test
    void theTargetLosesTheCopperUnderTheWholeSubtractorNotJustOneShape() throws IOException {
        GerberImage target = gerber("t.gbr", "R,10.0X10.0", "X50000Y50000");
        // Two overlapping 6 mm pads over the 10 mm square: the union covers more than either alone.
        GerberImage subtractor = gerber("s.gbr", "C,6.0", "X40000Y50000", "X60000Y50000");
        GerberImage result = Subtract.gerber(target, subtractor);
        double expected = target.solidGeometry().difference(subtractor.solidGeometry()).getArea();
        assertEquals(expected, result.solidGeometry().getArea(), 1e-6);
        assertTrue(result.solidGeometry().getArea() < target.solidGeometry().getArea());
        assertTrue(result.solidGeometry().disjoint(subtractor.solidGeometry().buffer(-1e-6)));
    }

    @Test
    void shapesThatDoNotTouchTheSubtractorKeepTheirAperture() throws IOException {
        GerberImage target = gerber("t.gbr", "C,2.0", "X10000Y10000", "X90000Y90000");
        GerberImage subtractor = gerber("s.gbr", "C,3.0", "X10000Y10000");
        GerberImage result = Subtract.gerber(target, subtractor);
        assertEquals(1, result.shapes().stream().filter(s -> s.apertureCode().equals("10")).count());
        assertEquals(0, result.shapes().stream().filter(s -> s.apertureCode().equals("0")).count());
        assertEquals(target.solidGeometry().getArea() / 2, result.solidGeometry().getArea(), 1e-6);
    }

    @Test
    void copperWrappedInCollectionsIsSubtractedToo() throws IOException {
        GerberImage target = gerber("t.gbr", "R,10.0X10.0", "X50000Y50000");
        GerberImage plain = gerber("s.gbr", "C,6.0", "X50000Y50000");
        GerberImage wrapped = GerberImage.of(plain.units(), plain.apertures(),
                FACTORY.createGeometryCollection(new Geometry[]{FACTORY.createMultiPolygon(new org.locationtech.jts.geom.Polygon[]{
                        (org.locationtech.jts.geom.Polygon) plain.solidGeometry().getGeometryN(0)})}),
                plain.followGeometry(), java.util.Map.of(), plain.shapes());
        assertEquals(Subtract.gerber(target, plain).solidGeometry().getArea(),
                Subtract.gerber(target, wrapped).solidGeometry().getArea(), 1e-9);
    }

    private static Geometry square(double x1, double y1, double x2, double y2) {
        return FACTORY.toGeometry(new Envelope(x1, x2, y1, y2));
    }

    @Test
    void closedPathsCutTheTargetAsOneShape() {
        Geometry target = FACTORY.createGeometryCollection(new Geometry[]{square(0, 0, 10, 10), square(20, 0, 30, 10)});
        Subtract.GeometryResult result = Subtract.geometry(target, List.of(), square(5, -1, 25, 11), true);
        assertEquals(100, result.geometry().getArea(), 1e-6);
    }

    @Test
    void openPathsTurnPolygonsIntoCutRingsAndLinesAreCutToo() {
        Geometry line = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});
        Subtract.GeometryResult cutLine = Subtract.geometry(line, List.of(), square(4, -1, 6, 1), false);
        assertEquals(8, cutLine.geometry().getLength(), 1e-9);
        Subtract.GeometryResult ring = Subtract.geometry(square(0, 0, 10, 10), List.of(), square(-1, -1, 11, 5), false);
        assertEquals(20, ring.geometry().getLength(), 1e-9);
        assertEquals(0, ring.geometry().getArea(), 1e-9);
    }

    @Test
    void multiToolTargetsKeepTheirToolsAndAnEmptySubtractorIsRefused() {
        Geometry path = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});
        Subtract.GeometryResult result = Subtract.geometry(path, List.of(new ToolGeometry(0.2, path)),
                square(4, -1, 6, 1), false);
        assertEquals(1, result.tools().size());
        assertEquals(0.2, result.tools().get(0).toolDiameter(), 1e-9);
        assertEquals(8, result.tools().get(0).geometry().getLength(), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> Subtract.geometry(path, List.of(), FACTORY.createGeometryCollection(), true));
    }
}
