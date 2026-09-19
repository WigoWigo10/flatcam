package org.flatcam.fx;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

/**
 * Draws a JTS {@link Geometry} onto a plain {@link Canvas}, fit-to-view once
 * on load (no pan/zoom/selection - that is the real viewport, Fase 2 per
 * CONTEXTO_FLATCAM_FX.md). This exists only so the Fase 3 vertical slice
 * (open Gerber -&gt; parse -&gt; display) has something to show without
 * waiting on the GPU viewport decision - see UI_INVENTORY.md and the
 * flatcam-cam module. Throwaway once Fase 2 lands.
 */
final class GerberCanvasRenderer {

    private static final Color BACKGROUND = Color.web("#1e1e1e");
    private static final Color COPPER_FILL = Color.web("#e1a339");
    private static final Color COPPER_STROKE = Color.web("#a06f1f");
    private static final double MARGIN = 16;

    private GerberCanvasRenderer() {
    }

    static Canvas render(Geometry geometry, double width, double height) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);

        if (geometry == null || geometry.isEmpty()) {
            return canvas;
        }

        Envelope envelope = geometry.getEnvelopeInternal();
        double scaleX = envelope.getWidth() > 0 ? (width - 2 * MARGIN) / envelope.getWidth() : 1;
        double scaleY = envelope.getHeight() > 0 ? (height - 2 * MARGIN) / envelope.getHeight() : 1;
        double scale = Math.min(scaleX, scaleY);
        if (!Double.isFinite(scale) || scale <= 0) {
            scale = 1;
        }

        gc.setFillRule(FillRule.EVEN_ODD);
        gc.setFill(COPPER_FILL);
        gc.setStroke(COPPER_STROKE);
        gc.setLineWidth(1);

        int count = geometry.getNumGeometries();
        for (int i = 0; i < count; i++) {
            if (geometry.getGeometryN(i) instanceof Polygon polygon) {
                drawPolygon(gc, polygon, envelope, scale, height);
            }
        }
        return canvas;
    }

    private static void drawPolygon(GraphicsContext gc, Polygon polygon, Envelope envelope, double scale, double canvasHeight) {
        gc.beginPath();
        addRing(gc, polygon.getExteriorRing().getCoordinates(), envelope, scale, canvasHeight);
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            addRing(gc, polygon.getInteriorRingN(i).getCoordinates(), envelope, scale, canvasHeight);
        }
        gc.fill();
        gc.stroke();
    }

    private static void addRing(GraphicsContext gc, Coordinate[] coordinates, Envelope envelope, double scale, double canvasHeight) {
        if (coordinates.length == 0) {
            return;
        }
        gc.moveTo(toScreenX(coordinates[0], envelope, scale), toScreenY(coordinates[0], envelope, scale, canvasHeight));
        for (int i = 1; i < coordinates.length; i++) {
            gc.lineTo(toScreenX(coordinates[i], envelope, scale), toScreenY(coordinates[i], envelope, scale, canvasHeight));
        }
        gc.closePath();
    }

    private static double toScreenX(Coordinate c, Envelope envelope, double scale) {
        return MARGIN + (c.x - envelope.getMinX()) * scale;
    }

    /** Flips Y: Gerber/JTS Y grows upward, Canvas Y grows downward. */
    private static double toScreenY(Coordinate c, Envelope envelope, double scale, double canvasHeight) {
        return canvasHeight - MARGIN - (c.y - envelope.getMinY()) * scale;
    }
}
