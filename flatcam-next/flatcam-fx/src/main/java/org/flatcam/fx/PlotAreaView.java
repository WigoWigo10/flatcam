package org.flatcam.fx;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.control.Label;
import javafx.scene.text.TextAlignment;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

/**
 * A pan/zoom/grid viewport on a plain Canvas - the Fase 2 GPU viewport
 * decision (CONTEXTO_FLATCAM_FX.md, secao 11.4) is still open; this only
 * proves the interaction model (drag to pan, scroll to zoom, adaptive grid,
 * rulers, origin crosshair, X/Y/Dx/Dy readout - matching appGUI/MainGUI.py's
 * Plot Area, UI_INVENTORY.md section 1) works before committing to a
 * rendering technology for scale. Geometry drawing itself is still the
 * simple even-odd polygon fill from the old GerberCanvasRenderer, just
 * routed through the same world-to-screen transform as everything else here.
 */
final class PlotAreaView extends StackPane {

    private static final double RULER_TOP_HEIGHT = 20;
    private static final double RULER_LEFT_WIDTH = 44;
    private static final double MIN_SCALE = 0.001;
    private static final double MAX_SCALE = 10_000;
    private static final double ZOOM_STEP = 1.1;

    private static final Color BACKGROUND = Color.web("#1e1e1e");
    private static final Color RULER_BACKGROUND = Color.web("#252525");
    private static final Color GRID_LINE = Color.web("#333333");
    private static final Color AXIS_LINE = Color.web("#b33a3a");
    private static final Color RULER_TEXT = Color.web("#9a9a9a");
    private static final Color COPPER_FILL = Color.web("#e1a339");
    private static final Color COPPER_STROKE = Color.web("#a06f1f");

    private final Canvas canvas = new Canvas();
    private final Label coordLabel = new Label("X: -   Y: -");

    private Geometry geometry;
    private double scale = 3.0;
    private double viewCenterX = 50;
    private double viewCenterY = 40;
    private double lastDragScreenX;
    private double lastDragScreenY;
    private double referenceWorldX;
    private double referenceWorldY;
    private boolean hasReference;

    PlotAreaView() {
        getChildren().add(canvas);
        StackPane.setAlignment(coordLabel, Pos.BOTTOM_LEFT);
        coordLabel.getStyleClass().add("plot-coord-label");
        getChildren().add(coordLabel);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());

        setOnScroll(this::handleScroll);
        setOnMousePressed(this::handlePress);
        setOnMouseDragged(this::handleDrag);
        setOnMouseMoved(this::handleMove);
        setOnMouseExited(e -> coordLabel.setText(hasReference ? "" : "X: -   Y: -"));

        redraw();
    }

    /** Replaces the displayed geometry and fits the view to it. Pass null to show an empty grid. */
    void setGeometry(Geometry newGeometry) {
        this.geometry = newGeometry;
        fitToView();
        redraw();
    }

    private void fitToView() {
        double contentWidth = Math.max(1, getWidth() - RULER_LEFT_WIDTH);
        double contentHeight = Math.max(1, getHeight() - RULER_TOP_HEIGHT);
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        Envelope envelope = geometry.getEnvelopeInternal();
        double margin = 20;
        double scaleX = envelope.getWidth() > 0 ? (contentWidth - 2 * margin) / envelope.getWidth() : scale;
        double scaleY = envelope.getHeight() > 0 ? (contentHeight - 2 * margin) / envelope.getHeight() : scale;
        double fitted = Math.min(scaleX, scaleY);
        if (Double.isFinite(fitted) && fitted > 0) {
            scale = clamp(fitted);
        }
        viewCenterX = envelope.getMinX() + envelope.getWidth() / 2.0;
        viewCenterY = envelope.getMinY() + envelope.getHeight() / 2.0;
    }

    // --- Input handling -----------------------------------------------

    private void handleScroll(ScrollEvent event) {
        double factor = event.getDeltaY() > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
        double newScale = clamp(scale * factor);
        if (newScale == scale) {
            return;
        }
        double contentX = event.getX() - RULER_LEFT_WIDTH;
        double contentY = event.getY() - RULER_TOP_HEIGHT;
        double contentWidth = getWidth() - RULER_LEFT_WIDTH;
        double contentHeight = getHeight() - RULER_TOP_HEIGHT;

        double factorInverse = 1.0 / scale - 1.0 / newScale;
        viewCenterX += (contentX - contentWidth / 2.0) * factorInverse;
        viewCenterY -= (contentY - contentHeight / 2.0) * factorInverse;
        scale = newScale;
        redraw();
        event.consume();
    }

    private void handlePress(MouseEvent event) {
        lastDragScreenX = event.getX();
        lastDragScreenY = event.getY();
        double[] world = screenToWorld(event.getX() - RULER_LEFT_WIDTH, event.getY() - RULER_TOP_HEIGHT);
        referenceWorldX = world[0];
        referenceWorldY = world[1];
        hasReference = true;
        updateCoordLabel(event.getX(), event.getY());
    }

    private void handleDrag(MouseEvent event) {
        double dx = event.getX() - lastDragScreenX;
        double dy = event.getY() - lastDragScreenY;
        viewCenterX -= dx / scale;
        viewCenterY += dy / scale;
        lastDragScreenX = event.getX();
        lastDragScreenY = event.getY();
        redraw();
        updateCoordLabel(event.getX(), event.getY());
    }

    private void handleMove(MouseEvent event) {
        updateCoordLabel(event.getX(), event.getY());
    }

    private void updateCoordLabel(double screenX, double screenY) {
        double[] world = screenToWorld(screenX - RULER_LEFT_WIDTH, screenY - RULER_TOP_HEIGHT);
        StringBuilder text = new StringBuilder();
        if (hasReference) {
            text.append(String.format("Dx: %.4f   Dy: %.4f%n", world[0] - referenceWorldX, world[1] - referenceWorldY));
        }
        text.append(String.format("X: %.4f   Y: %.4f", world[0], world[1]));
        coordLabel.setText(text.toString());
    }

    private static double clamp(double value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    // --- Coordinate transform (operates in content-local pixels, i.e. excluding the ruler strips) ---

    private double[] worldToScreen(double worldX, double worldY, double contentWidth, double contentHeight) {
        double screenX = (worldX - viewCenterX) * scale + contentWidth / 2.0;
        double screenY = contentHeight / 2.0 - (worldY - viewCenterY) * scale;
        return new double[]{screenX, screenY};
    }

    private double[] screenToWorld(double contentX, double contentY) {
        double contentWidth = getWidth() - RULER_LEFT_WIDTH;
        double contentHeight = getHeight() - RULER_TOP_HEIGHT;
        double worldX = (contentX - contentWidth / 2.0) / scale + viewCenterX;
        double worldY = viewCenterY - (contentY - contentHeight / 2.0) / scale;
        return new double[]{worldX, worldY};
    }

    // --- Drawing --------------------------------------------------------

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double contentWidth = Math.max(1, width - RULER_LEFT_WIDTH);
        double contentHeight = Math.max(1, height - RULER_TOP_HEIGHT);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);

        double step = niceStep(80.0 / scale);
        drawGrid(gc, contentWidth, contentHeight, step);
        drawAxisCrosshair(gc, contentWidth, contentHeight);
        if (geometry != null && !geometry.isEmpty()) {
            drawGeometry(gc, contentWidth, contentHeight);
        }
        drawRulers(gc, width, height, contentWidth, contentHeight, step);
    }

    private void drawGrid(GraphicsContext gc, double contentWidth, double contentHeight, double step) {
        gc.setStroke(GRID_LINE);
        gc.setLineWidth(1);

        double worldLeft = screenToWorld(0, 0)[0];
        double worldRight = screenToWorld(contentWidth, 0)[0];
        double worldTop = screenToWorld(0, 0)[1];
        double worldBottom = screenToWorld(0, contentHeight)[1];

        for (double x = Math.floor(worldLeft / step) * step; x <= worldRight; x += step) {
            double sx = worldToScreen(x, 0, contentWidth, contentHeight)[0] + RULER_LEFT_WIDTH;
            gc.strokeLine(sx, RULER_TOP_HEIGHT, sx, canvas.getHeight());
        }
        for (double y = Math.floor(worldBottom / step) * step; y <= worldTop; y += step) {
            double sy = worldToScreen(0, y, contentWidth, contentHeight)[1] + RULER_TOP_HEIGHT;
            gc.strokeLine(RULER_LEFT_WIDTH, sy, canvas.getWidth(), sy);
        }
    }

    private void drawAxisCrosshair(GraphicsContext gc, double contentWidth, double contentHeight) {
        gc.setStroke(AXIS_LINE);
        gc.setLineWidth(1.5);
        double[] origin = worldToScreen(0, 0, contentWidth, contentHeight);
        double ox = origin[0] + RULER_LEFT_WIDTH;
        double oy = origin[1] + RULER_TOP_HEIGHT;
        gc.strokeLine(RULER_LEFT_WIDTH, oy, canvas.getWidth(), oy);
        gc.strokeLine(ox, RULER_TOP_HEIGHT, ox, canvas.getHeight());
    }

    private void drawGeometry(GraphicsContext gc, double contentWidth, double contentHeight) {
        gc.setFillRule(FillRule.EVEN_ODD);
        gc.setFill(COPPER_FILL);
        gc.setStroke(COPPER_STROKE);
        gc.setLineWidth(1);

        int count = geometry.getNumGeometries();
        for (int i = 0; i < count; i++) {
            if (geometry.getGeometryN(i) instanceof Polygon polygon) {
                gc.beginPath();
                addRing(gc, polygon.getExteriorRing().getCoordinates(), contentWidth, contentHeight);
                for (int r = 0; r < polygon.getNumInteriorRing(); r++) {
                    addRing(gc, polygon.getInteriorRingN(r).getCoordinates(), contentWidth, contentHeight);
                }
                gc.fill();
                gc.stroke();
            }
        }
    }

    private void addRing(GraphicsContext gc, Coordinate[] coordinates, double contentWidth, double contentHeight) {
        if (coordinates.length == 0) {
            return;
        }
        double[] first = worldToScreen(coordinates[0].x, coordinates[0].y, contentWidth, contentHeight);
        gc.moveTo(first[0] + RULER_LEFT_WIDTH, first[1] + RULER_TOP_HEIGHT);
        for (int i = 1; i < coordinates.length; i++) {
            double[] p = worldToScreen(coordinates[i].x, coordinates[i].y, contentWidth, contentHeight);
            gc.lineTo(p[0] + RULER_LEFT_WIDTH, p[1] + RULER_TOP_HEIGHT);
        }
        gc.closePath();
    }

    private void drawRulers(GraphicsContext gc, double width, double height, double contentWidth, double contentHeight, double step) {
        gc.setFill(RULER_BACKGROUND);
        gc.fillRect(0, 0, width, RULER_TOP_HEIGHT);
        gc.fillRect(0, 0, RULER_LEFT_WIDTH, height);

        gc.setFill(RULER_TEXT);
        gc.setTextAlign(TextAlignment.CENTER);

        double worldLeft = screenToWorld(0, 0)[0];
        double worldRight = screenToWorld(contentWidth, 0)[0];
        for (double x = Math.floor(worldLeft / step) * step; x <= worldRight; x += step) {
            double sx = worldToScreen(x, 0, contentWidth, contentHeight)[0] + RULER_LEFT_WIDTH;
            gc.fillText(formatTick(x), sx, RULER_TOP_HEIGHT - 6);
        }

        double worldTop = screenToWorld(0, 0)[1];
        double worldBottom = screenToWorld(0, contentHeight)[1];
        gc.setTextAlign(TextAlignment.RIGHT);
        for (double y = Math.floor(worldBottom / step) * step; y <= worldTop; y += step) {
            double sy = worldToScreen(0, y, contentWidth, contentHeight)[1] + RULER_TOP_HEIGHT;
            gc.fillText(formatTick(y), RULER_LEFT_WIDTH - 4, sy + 4);
        }
    }

    private static String formatTick(double value) {
        double rounded = Math.round(value * 1000.0) / 1000.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** Picks a "nice" grid step (1/2/5 x a power of ten) closest to the raw suggestion. */
    private static double niceStep(double raw) {
        if (!Double.isFinite(raw) || raw <= 0) {
            return 1;
        }
        double exponent = Math.floor(Math.log10(raw));
        double base = Math.pow(10, exponent);
        double fraction = raw / base;
        double niceFraction = fraction < 1.5 ? 1 : fraction < 3.5 ? 2 : fraction < 7.5 ? 5 : 10;
        return niceFraction * base;
    }
}
