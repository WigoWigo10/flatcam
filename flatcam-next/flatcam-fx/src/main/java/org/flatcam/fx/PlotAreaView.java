package org.flatcam.fx;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.text.TextAlignment;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * A pan/zoom/grid viewport on a plain Canvas - the Fase 2 GPU viewport
 * decision (CONTEXTO_FLATCAM_FX.md, secao 11.4) is still open; this only
 * proves the interaction model (drag to pan, scroll to zoom, adaptive grid,
 * rulers, origin crosshair, X/Y/Dx/Dy readout - matching appGUI/MainGUI.py's
 * Plot Area, UI_INVENTORY.md section 1) works before committing to a
 * rendering technology for scale.
 *
 * <p>Holds an ordered stack of named layers (one per opened Gerber/Excellon/
 * isolation result, keyed by whatever the caller wants - MainWindow uses the
 * project tree's TreeItem) instead of a single geometry, so multiple objects
 * show at once - matching the legacy app's project tree, where every object
 * has its own plot rather than one replacing another (UI_INVENTORY.md
 * section 1's "Enable/Disable Plot" and "Set Color" per-object actions).
 * Draw order follows insertion order (a LinkedHashMap) - no explicit
 * z-ordering control yet.
 */
final class PlotAreaView extends StackPane {

    /**
     * One object's plot: its geometry, whether it's a stroke-only toolpath
     * (isolation preview - a bare LineString, not a filled/outline choice),
     * colors, the legacy "Solid"/"Multi-Color" plot options (filled vs.
     * outline-only; a single color vs. one per geometry part), visibility
     * and category.
     */
    record RenderLayer(Geometry geometry, boolean strokeOnly, Color fillColor, Color strokeColor,
                        boolean visible, LayerCategory category, boolean filled, boolean multicolor) {
    }

    /**
     * Fixed draw-order groups: Gerbers always under Excellon drills, which
     * are always under isolation previews, which are always under generated
     * CNC Job toolpaths - so opening files in a different order (or bringing
     * one to front via "Exibir") never makes a Gerber cover an Excellon or
     * vice versa. {@link #bringToFront} only reorders a layer relative to
     * others in its own category, matching the legacy project tree's own
     * Gerbers/Excellon/CNC Jobs grouping.
     */
    enum LayerCategory {
        GERBER, EXCELLON, OVERLAY, CNCJOB
    }

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

    private final Canvas canvas = new Canvas();
    private final Label coordLabel = new Label("X: -   Y: -");
    private final Map<Object, RenderLayer> layers = new LinkedHashMap<>();

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

    /**
     * Adds or replaces a layer's geometry/appearance. Visibility is
     * preserved if the key already existed (so recoloring an object doesn't
     * un-hide it), else defaults to visible. Does not change the view - call
     * {@link #fitToLayer} for that (typically right after adding a new one).
     */
    void putLayer(Object key, LayerCategory category, Geometry geometry, Color fillColor, Color strokeColor, boolean strokeOnly) {
        RenderLayer existing = layers.get(key);
        boolean visible = existing == null || existing.visible();
        boolean filled = existing == null || existing.filled();
        boolean multicolor = existing != null && existing.multicolor();
        layers.put(key, new RenderLayer(geometry, strokeOnly, fillColor, strokeColor, visible, category, filled, multicolor));
        redraw();
    }

    void removeLayer(Object key) {
        layers.remove(key);
        redraw();
    }

    void setLayerVisible(Object key, boolean visible) {
        RenderLayer layer = layers.get(key);
        if (layer != null) {
            layers.put(key, new RenderLayer(layer.geometry(), layer.strokeOnly(), layer.fillColor(), layer.strokeColor(),
                    visible, layer.category(), layer.filled(), layer.multicolor()));
            redraw();
        }
    }

    boolean isLayerVisible(Object key) {
        RenderLayer layer = layers.get(key);
        return layer == null || layer.visible();
    }

    void setLayerColors(Object key, Color fillColor, Color strokeColor) {
        RenderLayer layer = layers.get(key);
        if (layer != null) {
            layers.put(key, new RenderLayer(layer.geometry(), layer.strokeOnly(), fillColor, strokeColor,
                    layer.visible(), layer.category(), layer.filled(), layer.multicolor()));
            redraw();
        }
    }

    /** {fillColor, strokeColor} for a layer, or null if the key isn't a layer - for pre-filling {@link LayerColorDialog}. */
    Color[] layerColors(Object key) {
        RenderLayer layer = layers.get(key);
        return layer == null ? null : new Color[]{layer.fillColor(), layer.strokeColor()};
    }

    /** The legacy "Solid" plot option: filled polygons (with holes) vs. outline-only. */
    void setLayerFilled(Object key, boolean filled) {
        RenderLayer layer = layers.get(key);
        if (layer != null) {
            layers.put(key, new RenderLayer(layer.geometry(), layer.strokeOnly(), layer.fillColor(), layer.strokeColor(),
                    layer.visible(), layer.category(), filled, layer.multicolor()));
            redraw();
        }
    }

    boolean isLayerFilled(Object key) {
        RenderLayer layer = layers.get(key);
        return layer == null || layer.filled();
    }

    /** The legacy "Multi-Color" plot option: each geometry part gets a distinct hue instead of the layer's one fill color. */
    void setLayerMulticolor(Object key, boolean multicolor) {
        RenderLayer layer = layers.get(key);
        if (layer != null) {
            layers.put(key, new RenderLayer(layer.geometry(), layer.strokeOnly(), layer.fillColor(), layer.strokeColor(),
                    layer.visible(), layer.category(), layer.filled(), multicolor));
            redraw();
        }
    }

    boolean isLayerMulticolor(Object key) {
        RenderLayer layer = layers.get(key);
        return layer != null && layer.multicolor();
    }

    void clearLayers() {
        layers.clear();
        redraw();
    }

    /**
     * Moves a layer to the end of the draw order (drawn last = on top of
     * everything else) - a LinkedHashMap in insertion-order mode keeps a
     * key's original position on a plain put(), so re-inserting after
     * removal is what actually reorders it. Used by "Exibir no Plot Area"
     * so re-selecting an object brings it above whatever was covering it.
     */
    void bringToFront(Object key) {
        RenderLayer layer = layers.remove(key);
        if (layer != null) {
            layers.put(key, layer);
            redraw();
        }
    }

    /** Fits the view to one layer's bounds (e.g. "Exibir no Plot Area" on a specific object). */
    void fitToLayer(Object key) {
        RenderLayer layer = layers.get(key);
        if (layer != null && layer.geometry() != null && !layer.geometry().isEmpty()) {
            fitToEnvelope(layer.geometry().getEnvelopeInternal());
            redraw();
        }
    }

    private void fitToEnvelope(Envelope envelope) {
        double contentWidth = Math.max(1, getWidth() - RULER_LEFT_WIDTH);
        double contentHeight = Math.max(1, getHeight() - RULER_TOP_HEIGHT);
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
        // Category order (GERBER, then EXCELLON, then OVERLAY) is fixed regardless of each
        // layer's position in the map, so bringToFront (a plain reinsert-at-end) only ever
        // changes a layer's position relative to others in the same category.
        for (LayerCategory category : LayerCategory.values()) {
            for (RenderLayer layer : layers.values()) {
                if (layer.category() == category && layer.visible() && layer.geometry() != null && !layer.geometry().isEmpty()) {
                    drawLayer(gc, layer, contentWidth, contentHeight);
                }
            }
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

    private void drawLayer(GraphicsContext gc, RenderLayer layer, double contentWidth, double contentHeight) {
        gc.setFillRule(FillRule.EVEN_ODD);
        gc.setLineWidth(layer.strokeOnly() ? 1.5 : 1);

        Geometry geometry = layer.geometry();
        int count = geometry.getNumGeometries();
        for (int i = 0; i < count; i++) {
            Geometry part = geometry.getGeometryN(i);
            Color partColor = layer.multicolor() ? multicolorHue(i) : layer.fillColor();
            gc.setFill(partColor);
            gc.setStroke(layer.multicolor() ? partColor.darker() : layer.strokeColor());
            if (layer.strokeOnly()) {
                Coordinate[] coordinates = switch (part) {
                    case LineString line -> line.getCoordinates();
                    case Polygon polygon -> polygon.getExteriorRing().getCoordinates();
                    default -> null;
                };
                if (coordinates == null || coordinates.length == 0) {
                    continue;
                }
                gc.beginPath();
                addRing(gc, coordinates, contentWidth, contentHeight);
                gc.stroke();
            } else if (part instanceof Polygon polygon) {
                gc.beginPath();
                addRing(gc, polygon.getExteriorRing().getCoordinates(), contentWidth, contentHeight);
                for (int r = 0; r < polygon.getNumInteriorRing(); r++) {
                    addRing(gc, polygon.getInteriorRingN(r).getCoordinates(), contentWidth, contentHeight);
                }
                // The legacy app's "Solid" plot option: filled copper/holes vs. outline-only.
                if (layer.filled()) {
                    gc.fill();
                }
                gc.stroke();
            }
        }
    }

    /** A deterministic, well-spread hue per part index for the "Multi-Color" plot option - not literally random, so redraws don't flicker. */
    private static Color multicolorHue(int partIndex) {
        double hue = (partIndex * 137.508) % 360; // golden-angle spacing avoids adjacent parts landing on similar hues
        return Color.hsb(hue, 0.65, 0.85);
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
