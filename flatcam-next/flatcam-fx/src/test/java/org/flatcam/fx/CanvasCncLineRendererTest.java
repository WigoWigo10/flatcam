package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class CanvasCncLineRendererTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test void clippedSubpathsMatchReferencePixelsAtFractionalZoomHiDpiAndTranslucentCrossings() throws Exception {
        Geometry geometry = fixture(); Geometry unchanged = geometry.copy();
        for (double zoom : new double[]{1.75, 4.125, 9}) for (double physical : new double[]{1, 1.25, 2}) {
            TerminalPanelTest.fx(() -> {
                for (double opacity : new double[]{.9, 1}) for (boolean indexed : List.of(false, true))
                    for (double width : new double[]{1.25, 7}) {
                        var camera = new PlotCamera(.173, -.381, zoom, 280, 180, 16, 12);
                        var layer = new PlotRenderSnapshot(geometry, indexed ? new PlotDrawableIndex(geometry) : null,
                                true, true, false, Color.color(.2, .3, .7, opacity), Color.color(.1, .5, .9, opacity),
                                width, camera.visibleBounds());
                        var current = canvas(); var original = canvas();
                        var stats = new CanvasCncLineRenderer().draw(current.getGraphicsContext2D(), camera, layer);
                        new CanvasPlotRenderer().draw(original.getGraphicsContext2D(), camera, layer);
                        assertEquals(width == 1.25, stats.applied());
                        if (stats.applied()) assertTrue(stats.culledSegments() > 0);
                        assertEquals(stats.acceptedSegments(), stats.emittedSegments() + stats.culledSegments());
                        assertPixels(original, current, physical, "zoom=" + zoom + ", scale=" + physical + ", width=" + width
                                + ", opacity=" + opacity + ", indexed=" + indexed);
                    }
                return null;
            });
        }
        assertTrue(geometry.equalsExact(unchanged), "display clipping must never mutate CAM/source geometry");
    }

    @Test void unsupportedCanvasStateFallsBackWithoutChangingPixels() throws Exception {
        TerminalPanelTest.fx(() -> {
            var camera = new PlotCamera(0, 0, 4, 280, 180, 16, 12);
            for (int state = 0; state < 6; state++) {
                var a = canvas(); var b = canvas();
                for (var c : new Canvas[]{a, b}) {
                    var gc = c.getGraphicsContext2D();
                    switch (state) {
                        case 0 -> gc.setLineCap(StrokeLineCap.BUTT);
                        case 1 -> gc.setLineJoin(StrokeLineJoin.MITER);
                        case 2 -> { gc.setLineDashes(7, 5); gc.setLineDashOffset(3); }
                        case 3 -> gc.scale(.8, .7);
                        case 4 -> gc.setEffect(new GaussianBlur(2));
                        default -> { }
                    }
                }
                var layer = new PlotRenderSnapshot(fixture(), null, state != 5, true, false,
                        Color.BLUE, Color.color(.4, .2, .8, .9), 1.25, camera.visibleBounds());
                assertFalse(new CanvasCncLineRenderer().draw(a.getGraphicsContext2D(), camera, layer).applied());
                new CanvasPlotRenderer().draw(b.getGraphicsContext2D(), camera, layer);
                assertPixels(b, a, 1.25, "fallback state=" + state);
            }
            return null;
        });
    }

    @Test void fullyExternalClosedContoursNeedNoStrokeAndCrossingSegmentsAreKept() throws Exception {
        TerminalPanelTest.fx(() -> {
            var camera = new PlotCamera(0, 0, 4, 280, 180, 16, 12);
            var outside = line(-100, -100, 100, -100, 100, 100, -100, 100, -100, -100);
            var layer = new PlotRenderSnapshot(outside, null, true, true, false, Color.RED, Color.RED, 1.25, null);
            var stats = new CanvasCncLineRenderer().draw(canvas().getGraphicsContext2D(), camera, layer);
            assertEquals(4, stats.culledSegments()); assertEquals(0, stats.strokeCalls());
            var crossing = line(-100, 0, 100, 0);
            stats = new CanvasCncLineRenderer().draw(canvas().getGraphicsContext2D(), camera,
                    new PlotRenderSnapshot(crossing, null, true, true, false, Color.RED, Color.RED, 1.25, null));
            assertEquals(0, stats.culledSegments()); assertEquals(1, stats.emittedSegments());
            return null;
        });
    }

    private Canvas canvas() {
        var canvas = new Canvas(296, 192); var gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#c7daf2")); gc.fillRect(0, 0, 296, 192);
        gc.setLineCap(StrokeLineCap.ROUND); gc.setLineJoin(StrokeLineJoin.ROUND);
        return canvas;
    }

    private Geometry fixture() {
        var arc = new Coordinate[1801];
        for (int i = 0; i < arc.length; i++) {
            double angle = i * Math.PI / 180;
            arc[i] = new Coordinate(110 * Math.cos(angle), 65 * Math.sin(angle));
        }
        return factory.createGeometryCollection(new Geometry[]{
                line(-100,-100, 100,-100, 100,100, -100,100, -100,-100),
                line(-100,0, 100,0, 100,100, 0,100, 0,-100, -100,-100, -100,15, 100,15),
                line(-100,4, -99.99,4.01, -99.98,4.02, 15,4, 15,4, 15.001,4, 15,15, -15,-15, 15,-15, -15,15),
                line(-12,-12, -12,12, 12,12, 12,-12, -12,-12),
                factory.createLineString(arc), factory.createPoint(new Coordinate(3, 2)),
                factory.createGeometryCollection(new Geometry[]{line(-150, 8, 150, 9), line(-3,5, 8,-3)})});
    }

    private LineString line(double... xy) {
        var points = new Coordinate[xy.length / 2];
        for (int i = 0; i < points.length; i++) points[i] = new Coordinate(xy[2*i], xy[2*i+1]);
        return factory.createLineString(points);
    }

    private static void assertPixels(Canvas expected, Canvas actual, double scale, String context) {
        var parameters = new SnapshotParameters(); parameters.setTransform(new Scale(scale, scale));
        var a = expected.snapshot(parameters, null); var b = actual.snapshot(parameters, null);
        assertEquals(a.getWidth(), b.getWidth()); assertEquals(a.getHeight(), b.getHeight());
        int width = (int) a.getWidth(), height = (int) a.getHeight();
        int[] reference = new int[width * height], current = new int[width * height];
        a.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),reference,0,width);
        b.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),current,0,width);
        int mismatch = Arrays.mismatch(reference, current);
        assertEquals(-1, mismatch, () -> context + ", pixel=" + (mismatch % width) + "," + (mismatch / width)
                + (mismatch < 0 ? "" : ", expected=" + Integer.toHexString(reference[mismatch]) + ", actual=" + Integer.toHexString(current[mismatch])));
    }
}
