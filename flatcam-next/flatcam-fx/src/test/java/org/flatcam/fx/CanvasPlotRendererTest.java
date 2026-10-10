package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

/** Pixel oracle frozen from the pre-extraction vector implementation (780728b5). */
@EnabledOnOs(OS.WINDOWS)
class CanvasPlotRendererTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test void matchesOriginalPixelsForHolesOpenPathsPointsCullingAndMulticolor() throws Exception {
        var shell = factory.createLinearRing(new Coordinate[]{new Coordinate(0, 0), new Coordinate(30, 0),
                new Coordinate(30, 30), new Coordinate(0, 30), new Coordinate(0, 0)});
        var hole = factory.createLinearRing(new Coordinate[]{new Coordinate(8, 8), new Coordinate(16, 8),
                new Coordinate(16, 16), new Coordinate(8, 16), new Coordinate(8, 8)});
        Geometry geometry = factory.createGeometryCollection(new Geometry[]{
                factory.createMultiPolygon(new Polygon[]{factory.createPolygon(shell, new LinearRing[]{hole})}),
                factory.createLineString(new Coordinate[]{new Coordinate(-5, -5), new Coordinate(-4.999, -5),
                        new Coordinate(35, -5), new Coordinate(35, 12)}),
                factory.createPoint(new Coordinate(20, 20)), factory.toGeometry(new Envelope(90, 100, 90, 100))});
        Geometry unchanged = geometry.copy();
        TerminalPanelTest.fx(() -> {
            for (double scale : new double[]{3, 12}) for (boolean strokes : List.of(false, true))
                for (boolean filled : List.of(false, true)) for (boolean multi : List.of(false, true))
                    for (boolean indexed : List.of(false, true)) {
                        var camera = new PlotCamera(15, 15, scale, 420, 320, 44, 20);
                        var bounds = camera.visibleBounds();
                        var layer = new PlotRenderSnapshot(geometry, indexed ? new PlotDrawableIndex(geometry) : null,
                                strokes, filled, multi, Color.web("#bbcc0066"), Color.RED, strokes ? 1.5 : 1, bounds);
                        var current = new Canvas(464, 340);
                        var original = new Canvas(464, 340);
                        new CanvasPlotRenderer().draw(current.getGraphicsContext2D(), camera, layer);
                        original(original.getGraphicsContext2D(), camera, layer);
                        var a = current.snapshot(null, null).getPixelReader();
                        var b = original.snapshot(null, null).getPixelReader();
                        for (int y = 0; y < 340; y++) for (int x = 0; x < 464; x++)
                            if (a.getArgb(x, y) != b.getArgb(x, y))
                                fail("pixel differs at " + x + "," + y + " " + layer);
                    }
            return null;
        });
        assertTrue(geometry.equalsExact(unchanged));
    }

    private static void original(GraphicsContext gc, PlotCamera camera, PlotRenderSnapshot layer) {
        gc.setFillRule(FillRule.EVEN_ODD);
        gc.setLineWidth(layer.lineWidth());
        gc.setFill(layer.fill()); gc.setStroke(layer.stroke());
        int[] index = {0};
        // Flattening is independently defined here, not delegated to the extracted renderer.
        visit(layer.geometry(), part -> {
            int ordinal = index[0]++;
            if (!part.getEnvelopeInternal().intersects(layer.bounds())) return;
            if (layer.multicolor()) {
                var ink = Color.hsb((ordinal * 137.508) % 360, .65, .85);
                gc.setFill(ink); gc.setStroke(ink.darker());
            }
            if (layer.strokeOnly() && part instanceof Point p) {
                double x = (p.getX() - camera.centerX()) * camera.scale() + camera.width() / 2;
                double y = camera.height() / 2 - (p.getY() - camera.centerY()) * camera.scale();
                gc.fillOval(x + camera.insetX() - layer.lineWidth() / 2,
                        y + camera.insetY() - layer.lineWidth() / 2, layer.lineWidth(), layer.lineWidth());
            } else if (layer.strokeOnly() || part instanceof LineString) {
                CoordinateSequence sequence = part instanceof LineString line ? line.getCoordinateSequence()
                        : part instanceof Polygon polygon ? polygon.getExteriorRing().getCoordinateSequence() : null;
                if (sequence == null || sequence.size() == 0) return;
                gc.beginPath(); ring(gc, sequence, camera, false); gc.stroke();
            } else if (part instanceof Polygon polygon) {
                gc.beginPath(); ring(gc, polygon.getExteriorRing().getCoordinateSequence(), camera, true);
                for (int r = 0; r < polygon.getNumInteriorRing(); r++) {
                    var hole = polygon.getInteriorRingN(r);
                    if (hole.getEnvelopeInternal().intersects(layer.bounds())) ring(gc, hole.getCoordinateSequence(), camera, true);
                }
                if (layer.filled()) gc.fill();
                gc.stroke();
            }
        });
    }

    private static void visit(Geometry geometry, java.util.function.Consumer<Geometry> visitor) {
        if (geometry.isEmpty()) return;
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) visit(collection.getGeometryN(i), visitor);
        } else visitor.accept(geometry);
    }

    private static void ring(GraphicsContext gc, CoordinateSequence points, PlotCamera camera, boolean closed) {
        double ox = camera.insetX() + camera.width() / 2 - camera.centerX() * camera.scale();
        double oy = camera.insetY() + camera.height() / 2 + camera.centerY() * camera.scale();
        double lastX = points.getX(0) * camera.scale() + ox, lastY = oy - points.getY(0) * camera.scale();
        gc.moveTo(lastX, lastY);
        for (int i = 1; i < points.size(); i++) {
            double x = points.getX(i) * camera.scale() + ox, y = oy - points.getY(i) * camera.scale();
            if (!closed && i < points.size() - 1 && Math.abs(x - lastX) < .5 && Math.abs(y - lastY) < .5) continue;
            gc.lineTo(x, y); lastX = x; lastY = y;
        }
        if (closed) gc.closePath();
    }
}
