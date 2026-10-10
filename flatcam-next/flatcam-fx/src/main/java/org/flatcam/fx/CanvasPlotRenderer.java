package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import org.locationtech.jts.geom.*;

/** Stateless vector backend. The viewport owns surface, scheduling, density frames and overlays.
 * All methods drawing on GraphicsContext are FX-thread-only. This first boundary does not
 * introduce a GPU backend or hide GPU readback/presentation behind a fictitious draw API. */
final class CanvasPlotRenderer {
    void draw(GraphicsContext gc, PlotCamera camera, PlotRenderSnapshot layer) {
        Envelope bounds = layer.bounds();
        gc.setFillRule(FillRule.EVEN_ODD);
        gc.setLineWidth(layer.lineWidth());
        if (!layer.multicolor()) {
            gc.setFill(layer.fill());
            gc.setStroke(layer.stroke());
        }
        if (layer.index() != null) {
            for (PlotDrawableIndex.Part part : layer.index().visibleParts(bounds))
                drawPart(gc, camera, layer, part.geometry(), part.index(), bounds);
        } else {
            int[] ordinal = {0};
            forEachPart(layer.geometry(), part -> {
                int index = ordinal[0]++;
                if (bounds == null || part.getEnvelopeInternal().intersects(bounds))
                    drawPart(gc, camera, layer, part, index, bounds);
            });
        }
    }

    private void drawPart(GraphicsContext gc, PlotCamera camera, PlotRenderSnapshot layer,
                          Geometry part, int ordinal, Envelope bounds) {
        if (layer.multicolor()) {
            Color ink = Color.hsb((ordinal * 137.508) % 360, 0.65, 0.85);
            gc.setFill(ink);
            gc.setStroke(ink.darker());
        }
        if (layer.strokeOnly()) {
            if (part instanceof Point point) {
                double dot = layer.lineWidth();
                gc.fillOval(camera.screenX(point.getX()) - dot / 2, camera.screenY(point.getY()) - dot / 2, dot, dot);
                return;
            }
            CoordinateSequence coordinates = switch (part) {
                case LineString line -> line.getCoordinateSequence();
                case Polygon polygon -> polygon.getExteriorRing().getCoordinateSequence();
                default -> null;
            };
            if (coordinates == null || coordinates.size() == 0) return;
            gc.beginPath();
            // Do not close open paths: cutout bridge gaps must not gain chords.
            addRing(gc, coordinates, camera, false);
            gc.stroke();
        } else if (part instanceof LineString line) {
            gc.beginPath();
            addRing(gc, line.getCoordinateSequence(), camera, false);
            gc.stroke();
        } else if (part instanceof Polygon polygon) {
            gc.beginPath();
            addRing(gc, polygon.getExteriorRing().getCoordinateSequence(), camera, true);
            for (int r = 0; r < polygon.getNumInteriorRing(); r++) {
                LineString hole = polygon.getInteriorRingN(r);
                if (bounds == null || hole.getEnvelopeInternal().intersects(bounds))
                    addRing(gc, hole.getCoordinateSequence(), camera, true);
            }
            if (layer.filled()) gc.fill();
            gc.stroke();
        }
    }

    static void forEachPart(Geometry geometry, Consumer<Geometry> visitor) {
        if (geometry == null || geometry.isEmpty()) return;
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++)
                forEachPart(collection.getGeometryN(i), visitor);
        } else visitor.accept(geometry);
    }

    static void addRing(GraphicsContext gc, CoordinateSequence coordinates, PlotCamera camera, boolean close) {
        if (coordinates.size() == 0) return;
        double offsetX = camera.offsetX(), offsetY = camera.offsetY();
        double lastX = coordinates.getX(0) * camera.scale() + offsetX;
        double lastY = offsetY - coordinates.getY(0) * camera.scale();
        gc.moveTo(lastX, lastY);
        for (int i = 1; i < coordinates.size(); i++) {
            double x = coordinates.getX(i) * camera.scale() + offsetX;
            double y = offsetY - coordinates.getY(i) * camera.scale();
            if (omitSubpixelOpenPathVertex(close, i, coordinates.size(), x - lastX, y - lastY)) continue;
            gc.lineTo(x, y);
            lastX = x;
            lastY = y;
        }
        if (close) gc.closePath();
    }

    static boolean omitSubpixelOpenPathVertex(boolean closed, int index, int size, double dx, double dy) {
        return !closed && index < size - 1 && Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5;
    }
}
