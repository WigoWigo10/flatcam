package org.flatcam.fx;

import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.locationtech.jts.geom.*;

/** Conservative screen-space rejection for solid, round 1.25px CNC pass strokes only.
 * No intersection clipping, endpoint movement, path reordering, merging or geometry mutation.
 * The original subpixel-vertex decisions are made BEFORE segment rejection. All retained
 * subpaths of one original part are still submitted in one stroke, preserving translucent
 * self-overlap. Cuts occur outside a stroke/AA halo, so their caps/joins cannot reach the canvas.
 * Unsupported Canvas state and non-finite parts fall back to the unchanged vector renderer. */
final class CanvasCncLineRenderer {
    record Stats(boolean applied, long acceptedSegments, long emittedSegments, long culledSegments,
                 long strokeCalls, long fallbackParts) { }
    private final CanvasPlotRenderer reference = new CanvasPlotRenderer();

    Stats draw(GraphicsContext gc, PlotCamera camera, PlotRenderSnapshot layer) {
        double[] dashes = gc.getLineDashes();
        if (!layer.strokeOnly() || layer.multicolor() || layer.lineWidth() != 1.25 || gc.getLineCap() != StrokeLineCap.ROUND
                || gc.getLineJoin() != StrokeLineJoin.ROUND || dashes != null && dashes.length != 0
                || !gc.getTransform().isIdentity() || gc.getEffect(null) != null) {
            reference.draw(gc, camera, layer);
            return new Stats(false, 0, 0, 0, 0, 0);
        }
        gc.setFillRule(FillRule.EVEN_ODD); gc.setLineWidth(layer.lineWidth());
        gc.setFill(layer.fill()); gc.setStroke(layer.stroke());
        var bounds = layer.bounds();
        List<PlotDrawableIndex.Part> parts;
        if (layer.index() != null) parts = layer.index().visibleParts(bounds);
        else {
            var selected = new java.util.ArrayList<PlotDrawableIndex.Part>();
            CanvasPlotRenderer.forEachPart(layer.geometry(), part -> {
                if (bounds == null || part.getEnvelopeInternal().intersects(bounds))
                    selected.add(new PlotDrawableIndex.Part(selected.size(), part, part.getEnvelopeInternal(), 0, 0));
            });
            parts = selected;
        }
        long accepted = 0, emitted = 0, culled = 0, strokes = 0, fallback = 0;
        // Deliberately generous in logical pixels, including fractional/HiDPI AA coverage.
        double halo = layer.lineWidth() / 2 + 4;
        double width = gc.getCanvas().getWidth(), height = gc.getCanvas().getHeight();
        for (var part : parts) {
            Geometry geometry = part.geometry();
            if (geometry instanceof Point point) {
                double dot = layer.lineWidth();
                gc.fillOval(camera.screenX(point.getX()) - dot / 2, camera.screenY(point.getY()) - dot / 2, dot, dot);
                continue;
            }
            CoordinateSequence sequence = switch (geometry) {
                case LineString line -> line.getCoordinateSequence();
                case Polygon polygon -> polygon.getExteriorRing().getCoordinateSequence();
                default -> null;
            };
            if (sequence == null || sequence.size() == 0) continue;
            if (!finite(sequence, camera)) {
                reference.draw(gc, camera, new PlotRenderSnapshot(geometry, null, true, layer.filled(), false,
                        layer.fill(), layer.stroke(), layer.lineWidth(), null));
                fallback++;
                continue;
            }
            double lastX = sequence.getX(0) * camera.scale() + camera.offsetX();
            double lastY = camera.offsetY() - sequence.getY(0) * camera.scale();
            int previousCode = outside(lastX, lastY, width, height, halo);
            boolean pen = false, started = false;
            for (int i = 1; i < sequence.size(); i++) {
                double x = sequence.getX(i) * camera.scale() + camera.offsetX();
                double y = camera.offsetY() - sequence.getY(i) * camera.scale();
                if (CanvasPlotRenderer.omitSubpixelOpenPathVertex(false, i, sequence.size(), x - lastX, y - lastY)) continue;
                accepted++;
                int code = outside(x, y, width, height, halo);
                if ((previousCode & code) != 0) {
                    culled++; pen = false;
                } else {
                    if (!started) { gc.beginPath(); started = true; }
                    if (!pen) gc.moveTo(lastX, lastY);
                    gc.lineTo(x, y); emitted++; pen = true;
                }
                // Always retain the original last accepted vertex, even after a culled segment.
                lastX = x; lastY = y; previousCode = code;
            }
            if (started) { gc.stroke(); strokes++; }
        }
        return new Stats(true, accepted, emitted, culled, strokes, fallback);
    }

    private static boolean finite(CoordinateSequence sequence, PlotCamera camera) {
        for (int i = 0; i < sequence.size(); i++)
            if (!Double.isFinite(sequence.getX(i) * camera.scale() + camera.offsetX())
                    || !Double.isFinite(camera.offsetY() - sequence.getY(i) * camera.scale())) return false;
        return true;
    }

    private static int outside(double x, double y, double width, double height, double halo) {
        return (x < -halo ? 1 : x > width + halo ? 2 : 0) | (y < -halo ? 4 : y > height + halo ? 8 : 0);
    }
}
