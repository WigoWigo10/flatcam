package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

class PaintGeneratorTest {
    @Test void individualSettingsKeepMarginsAndMethodsAndRestUsesEachToolsArea() {
        var settings = java.util.Map.of(
                2.0, new PaintToolSettings(0.2, 3, NccMethod.STANDARD, false, true),
                0.5, new PaintToolSettings(0.6, 0, NccMethod.LINES, true, false));
        var parameters = new PaintParameters(List.of(2.0, 0.5), 0.4, 0,
                NccMethod.SEED, true, true, NccOrder.NONE, true, settings);
        var result = paint(box(0, 0, 20, 20), parameters);
        assertEquals(2, result.toolResults().size());
        assertTrue(result.toolResults().get(0).geometry().getEnvelopeInternal().getMinX() >= 4 - 1e-6);
        assertTrue(result.toolResults().get(1).geometry().getEnvelopeInternal().getMinX() < 1);
        assertEquals(400, result.clearingArea().getArea(), 1e-6);
        assertEquals(NccMethod.LINES, parameters.settingsFor(0.5).method());
    }

    @Test void negativePaintMarginExpandsAndOverlappingPolygonsAreNormalized() {
        var source = FACTORY.buildGeometry(List.of(box(0,0,10,10), box(5,0,15,10)));
        var result = paint(source, standard(List.of(1.0), -1, false));
        assertTrue(result.clearingArea().getArea() > 150);
        assertTrue(result.geometry().getEnvelopeInternal().getMinX() < 0);
        assertEquals(200, source.getArea(), 1e-6, "input is not mutated");
    }
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Geometry box(double x1, double y1, double x2, double y2) {
        return FACTORY.toGeometry(new Envelope(x1, x2, y1, y2));
    }

    private static NccResult paint(Geometry polygons, PaintParameters parameters) {
        return NccGenerator.paint("MM", polygons, parameters, CancellationToken.none(), ProgressCallback.none());
    }

    private static PaintParameters standard(List<Double> tools, double offset, boolean rest) {
        return new PaintParameters(tools, 0.4, offset, NccMethod.STANDARD, true, true, NccOrder.NONE, rest);
    }

    @Test
    void paintingARectangleCoversAlmostAllOfIt() {
        Geometry rectangle = box(0, 0, 20, 10);
        NccResult result = paint(rectangle, standard(List.of(1.0), 0, false));
        Geometry covered = result.geometry().buffer(0.5, 16);
        double inside = OverlayNGRobust.overlay(covered, rectangle, OverlayNG.INTERSECTION).getArea();
        assertTrue(inside / rectangle.getArea() > 0.95, "covered " + inside / rectangle.getArea());
        assertTrue(rectangle.buffer(1e-6).covers(result.geometry()), "paths stay inside the polygon");
    }

    @Test
    void theMarginKeepsThePathsAwayFromTheEdge() {
        NccResult result = paint(box(0, 0, 20, 10), standard(List.of(1.0), 2.0, false));
        Envelope paths = result.geometry().getEnvelopeInternal();
        assertTrue(paths.getMinX() >= 2.5 - 1e-6 && paths.getMaxX() <= 17.5 + 1e-6, paths.toString());
        assertThrows(IllegalArgumentException.class, () -> paint(box(0, 0, 20, 10), standard(List.of(1.0), 6.0, false)));
    }

    @Test
    void restMachiningLetsTheSmallToolFindWhatTheLargeOneMissed() {
        // A 20 x 20 square with a 1 mm wide arm sticking out to x = 30: the 3 mm tool cannot enter the arm.
        Geometry shape = OverlayNGRobust.union(List.of(box(0, 0, 20, 20), box(20, 9.5, 30, 10.5)));
        NccResult result = paint(shape, standard(List.of(3.0, 0.4), 0, true));
        assertEquals(2, result.toolResults().size());
        assertEquals(3.0, result.toolResults().get(0).toolDiameter(), 1e-9);
        Envelope large = result.toolResults().get(0).geometry().getEnvelopeInternal();
        Envelope small = result.toolResults().get(1).geometry().getEnvelopeInternal();
        assertTrue(large.getMaxX() <= 20, "the large tool stays in the square");
        assertTrue(small.getMaxX() > 25, "the small tool reaches into the arm: " + small);
    }

    @Test
    void nothingFilledToPaintIsRefused() {
        Geometry line = FACTORY.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(0, 0), new org.locationtech.jts.geom.Coordinate(5, 5)});
        assertThrows(IllegalArgumentException.class, () -> paint(line, standard(List.of(1.0), 0, false)));
    }
}
