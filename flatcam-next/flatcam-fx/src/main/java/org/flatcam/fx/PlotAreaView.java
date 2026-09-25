package org.flatcam.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
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
     * Fixed draw-order groups: Gerbers always under Excellon drills, followed
     * by derived Geometry objects, overlays and generated CNC Job toolpaths -
     * so opening files in a different order (or bringing
     * one to front via "Exibir") never makes a Gerber cover an Excellon or
     * vice versa. {@link #bringToFront} only reorders a layer relative to
     * others in its own category, matching the legacy project tree's own
     * Gerbers/Excellon/CNC Jobs grouping.
     */
    enum LayerCategory {
        GERBER, EXCELLON, GEOMETRY, OVERLAY, CNCJOB
    }

    /**
     * Receives primary-button clicks/drags in world coordinates while an
     * editor owns the canvas; other buttons keep panning. {@code additive} is
     * the multi-select key (Control - defaults.py's global_mselect_key).
     */
    interface SelectionHandler {
        void onClick(double worldX, double worldY, boolean additive);

        /** A plain double-click outside an editor can open the hit object's properties. */
        default void onDoubleClick(double worldX, double worldY) {
            onClick(worldX, worldY, false);
        }

        /** From the press point to the release point; direction is the caller's to interpret. */
        void onBox(double pressX, double pressY, double releaseX, double releaseY, boolean additive);
    }

    interface ContextRequestHandler {
        void onContextRequest(double worldX, double worldY, double screenX, double screenY);
    }

    interface PlacementHandler {
        default void onAnchorChosen() {
        }

        void onCommit(double dx, double dy);

        void onCancel();
    }

    record SelectableLayer(Object key, Geometry geometry) {
    }

    private static final double CLICK_DRAG_THRESHOLD_PX = 3;
    // defaults.py's global_sel_fill/_line (left-to-right) and global_alt_sel_fill/_line (right-to-left).
    private static final Color ENCLOSING_BOX_FILL = Color.web("#a5a5ffbf");
    private static final Color ENCLOSING_BOX_LINE = Color.web("#0000ffbf");
    private static final Color TOUCHING_BOX_FILL = Color.web("#BBF268BF");
    private static final Color TOUCHING_BOX_LINE = Color.web("#006E20BF");
    private static final Color SELECTED_OBJECT_LINE = Color.web("#ffb000");
    private static final Color PLACEMENT_FILL = Color.web("#00bfff", 0.30);
    private static final Color PLACEMENT_STROKE = Color.web("#00bfff", 0.95);

    private static final double RULER_TOP_HEIGHT = 20;
    private static final double RULER_LEFT_WIDTH = 44;
    private static final double MIN_SCALE = 0.001;
    private static final double MAX_SCALE = 10_000;
    private static final double ZOOM_STEP = 1.1;

    record PlotPalette(Color background, Color rulerBackground, Color gridLine,
                       Color axisLine, Color rulerText, Color snapCursor, Color snapOutline) {
    }

    private static final PlotPalette CUSTOM_LIGHT_PALETTE = new PlotPalette(
            Color.web("#f7f7f7"), Color.web("#e7e7e7"), Color.web("#dedede"),
            Color.web("#c44747"), Color.web("#606060"),
            Color.web("#e53935"), Color.rgb(255, 255, 255, 0.9));
    private static final PlotPalette CUSTOM_DARK_PALETTE = new PlotPalette(
            Color.web("#101010"), Color.web("#202020"), Color.web("#2b2b2b"),
            Color.web("#b33a3a"), Color.web("#9a9a9a"),
            Color.web("#ff6b6b"), Color.rgb(10, 14, 20, 0.92));
    private static final PlotPalette ATLANTAFX_LIGHT_PALETTE = new PlotPalette(
            Color.web("#f6f8fa"), Color.web("#eaeef2"), Color.web("#d8dee4"),
            Color.web("#cf4a4a"), Color.web("#57606a"),
            Color.web("#e53935"), Color.rgb(255, 255, 255, 0.9));
    private static final PlotPalette ATLANTAFX_DARK_PALETTE = new PlotPalette(
            Color.web("#0d1117"), Color.web("#161b22"), Color.web("#21262d"),
            Color.web("#c44b55"), Color.web("#8b949e"),
            Color.web("#ff6b6b"), Color.rgb(10, 14, 20, 0.92));

    private final Canvas canvas = new Canvas();
    private final Canvas snapCursorCanvas = new Canvas();
    private final Label coordLabel = new Label("Dx: 0.0000 [mm]\nDy: 0.0000 [mm]\n\nX: 0.0000 [mm]\nY: 0.0000 [mm]");
    private java.util.function.Consumer<String> coordinateListener = ignored -> {};
    private final Map<Object, RenderLayer> layers = new LinkedHashMap<>();
    private PlotPalette palette = CUSTOM_LIGHT_PALETTE;

    private double scale = 3.0;
    private double viewCenterX = 50;
    private double viewCenterY = 40;
    private double lastDragScreenX;
    private double lastDragScreenY;
    private double referenceWorldX;
    private double referenceWorldY;
    private boolean gridSnapEnabled = true;
    private double gridStepX = 1.0;
    private double gridStepY = 1.0;
    private boolean axisVisible = true;
    private boolean hudVisible = true;
    private boolean workspaceVisible;
    private String units = "MM";
    private boolean cursorInsidePlot;
    private double cursorScreenX;
    private double cursorScreenY;

    private SelectionHandler defaultSelectionHandler;
    private SelectionHandler selectionHandler;
    private ContextRequestHandler contextRequestHandler;
    private List<Envelope> selectedObjectBounds = List.of();
    private boolean selecting;
    private boolean selectionDragged;
    private double selectionPressScreenX;
    private double selectionPressScreenY;
    private double selectionCurrentScreenX;
    private double selectionCurrentScreenY;
    private boolean rightPressed;
    private boolean rightDragged;
    private double rightPressScreenX;
    private double rightPressScreenY;
    private PlacementHandler placementHandler;
    private List<Object> placementKeys = List.of();
    private List<RenderLayer> placementLayers = List.of();
    private double placementAnchorWorldX;
    private double placementAnchorWorldY;
    private double placementCurrentWorldX;
    private double placementCurrentWorldY;
    private boolean placementPrimaryPressed;
    private boolean placementAnchorChosen;

    PlotAreaView() {
        getChildren().add(canvas);
        snapCursorCanvas.setMouseTransparent(true);
        getChildren().add(snapCursorCanvas);
        StackPane.setAlignment(coordLabel, Pos.BOTTOM_LEFT);
        coordLabel.getStyleClass().add("plot-coord-label");
        coordLabel.setMouseTransparent(true);
        getChildren().add(coordLabel);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        snapCursorCanvas.widthProperty().bind(widthProperty());
        snapCursorCanvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());
        snapCursorCanvas.widthProperty().addListener((obs, oldVal, newVal) -> drawSnapCursor());
        snapCursorCanvas.heightProperty().addListener((obs, oldVal, newVal) -> drawSnapCursor());

        setOnScroll(this::handleScroll);
        setOnMousePressed(this::handlePress);
        setOnMouseDragged(this::handleDrag);
        setOnMouseReleased(this::handleRelease);
        setOnMouseMoved(this::handleMove);
        setOnMouseExited(e -> {
            cursorInsidePlot = false;
            drawSnapCursor();
            coordinateListener.accept("X: -   Y: -");
        });

        redraw();
    }

    /** Updates every canvas-owned color immediately when the application theme changes. */
    void applyTheme(ThemeOption theme) {
        palette = paletteForTheme(theme);
        redraw();
    }

    static PlotPalette paletteForTheme(ThemeOption theme) {
        return switch (theme) {
            case CUSTOM_LIGHT -> CUSTOM_LIGHT_PALETTE;
            case CUSTOM_DARK -> CUSTOM_DARK_PALETTE;
            case ATLANTAFX_LIGHT -> ATLANTAFX_LIGHT_PALETTE;
            case ATLANTAFX_DARK -> ATLANTAFX_DARK_PALETTE;
        };
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

    /** Swaps a layer's geometry in place (e.g. after a Transform) without disturbing its colors/visibility/plot-kind. */
    void updateLayerGeometry(Object key, Geometry geometry) {
        if (placementKeys.contains(key)) {
            cancelPlacement();
        }
        RenderLayer layer = layers.get(key);
        if (layer != null) {
            layers.put(key, new RenderLayer(geometry, layer.strokeOnly(), layer.fillColor(), layer.strokeColor(),
                    layer.visible(), layer.category(), layer.filled(), layer.multicolor()));
            redraw();
        }
    }

    void removeLayer(Object key) {
        if (placementKeys.contains(key)) {
            cancelPlacement();
        }
        layers.remove(key);
        redraw();
    }

    void setLayerVisible(Object key, boolean visible) {
        if (!visible && placementKeys.contains(key)) {
            cancelPlacement();
        }
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
        cancelPlacement();
        layers.clear();
        selectedObjectBounds = List.of();
        redraw();
    }

    void setCoordinateListener(java.util.function.Consumer<String> listener) {
        coordinateListener = listener == null ? ignored -> {} : listener;
    }

    void setGridSnap(boolean enabled, double stepX, double stepY) {
        if (!Double.isFinite(stepX) || stepX <= 0 || !Double.isFinite(stepY) || stepY <= 0) {
            throw new IllegalArgumentException("Grid spacing must be finite and positive");
        }
        gridSnapEnabled = enabled;
        gridStepX = stepX;
        gridStepY = stepY;
        drawSnapCursor();
    }

    void setAxisVisible(boolean visible) {
        axisVisible = visible;
        redraw();
    }

    void setHudVisible(boolean visible) {
        hudVisible = visible;
        coordLabel.setVisible(visible);
    }

    void setWorkspaceVisible(boolean visible) {
        workspaceVisible = visible;
        redraw();
    }

    void setUnits(String units) {
        this.units = units == null ? "MM" : units;
        coordLabel.setText(formatHud(0, 0, 0, 0, this.units));
        redraw();
    }

    static String formatHud(double dx, double dy, double x, double y, String units) {
        String suffix = units == null ? "mm" : units.toLowerCase(java.util.Locale.ROOT);
        return String.format(java.util.Locale.ROOT,
                "Dx: %.4f [%s]%nDy: %.4f [%s]%n%nX: %.4f [%s]%nY: %.4f [%s]",
                dx, suffix, dy, suffix, x, suffix, y, suffix);
    }

    static double snapCoordinate(double coordinate, double step) {
        return Math.round(coordinate / step) * step;
    }

    private double[] snappedWorld(double screenX, double screenY) {
        double[] world = screenToWorld(screenX - RULER_LEFT_WIDTH, screenY - RULER_TOP_HEIGHT);
        if (gridSnapEnabled) {
            world[0] = snapCoordinate(world[0], gridStepX);
            world[1] = snapCoordinate(world[1], gridStepY);
        }
        return world;
    }

    /** Visible project layers in their actual painting order, excluding editor/mark overlays. */
    List<SelectableLayer> visibleSelectableLayers() {
        List<SelectableLayer> result = new ArrayList<>();
        for (LayerCategory category : LayerCategory.values()) {
            if (category == LayerCategory.OVERLAY) {
                continue;
            }
            for (Map.Entry<Object, RenderLayer> entry : layers.entrySet()) {
                RenderLayer layer = entry.getValue();
                if (layer.category() == category && layer.visible()
                        && layer.geometry() != null && !layer.geometry().isEmpty()) {
                    result.add(new SelectableLayer(entry.getKey(), layer.geometry()));
                }
            }
        }
        return List.copyOf(result);
    }

    double pickToleranceWorld() {
        return 4.0 / scale;
    }

    void setSelectedObjectBounds(List<Envelope> bounds) {
        selectedObjectBounds = bounds.stream().map(Envelope::new).toList();
        redraw();
    }

    void fitAllVisible() {
        Envelope all = new Envelope();
        for (SelectableLayer layer : visibleSelectableLayers()) {
            all.expandToInclude(layer.geometry().getEnvelopeInternal());
        }
        if (!all.isNull()) {
            fitToEnvelope(all);
            redraw();
        }
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

    /** The project-object selector used whenever no editor owns the canvas. */
    void setDefaultSelectionHandler(SelectionHandler handler) {
        defaultSelectionHandler = handler;
    }

    /** An editor temporarily overrides the project selector; null restores it. */
    void setSelectionHandler(SelectionHandler handler) {
        if (placementHandler != null) {
            cancelPlacement();
        }
        selectionHandler = handler;
        selecting = false;
        redraw();
    }

    void setContextRequestHandler(ContextRequestHandler handler) {
        contextRequestHandler = handler;
    }

    /** Captures the visible source layers so the ghost cannot mutate the project before confirmation. */
    boolean beginPlacement(List<?> keys, double anchorWorldX, double anchorWorldY, PlacementHandler handler) {
        if (selectionHandler != null || keys.isEmpty() || handler == null) {
            return false;
        }
        List<RenderLayer> preview = new ArrayList<>();
        for (Object key : keys) {
            RenderLayer layer = layers.get(key);
            if (layer == null || !layer.visible() || layer.geometry() == null || layer.geometry().isEmpty()) {
                return false;
            }
            preview.add(layer);
        }
        return startPlacement(keys, preview, anchorWorldX, anchorWorldY, true, handler);
    }

    /** Editor shapes have no project layer keys; previewing them never mutates the edit session. */
    boolean beginEditorPlacement(List<Geometry> geometries, PlacementHandler handler) {
        if (selectionHandler == null || geometries.isEmpty() || handler == null) {
            return false;
        }
        List<RenderLayer> preview = new ArrayList<>();
        for (Geometry geometry : geometries) {
            if (geometry == null || geometry.isEmpty()) {
                return false;
            }
            preview.add(new RenderLayer(geometry, false, PLACEMENT_FILL, PLACEMENT_STROKE,
                    true, LayerCategory.OVERLAY, true, false));
        }
        return startPlacement(List.of(), preview, 0, 0, false, handler);
    }

    /** A flash is already centered at the origin: the first click places it at the cursor. */
    boolean beginEditorFlashPlacement(Geometry footprintAtOrigin, PlacementHandler handler) {
        if (selectionHandler == null || footprintAtOrigin == null || footprintAtOrigin.isEmpty() || handler == null) {
            return false;
        }
        RenderLayer preview = new RenderLayer(footprintAtOrigin, false, PLACEMENT_FILL, PLACEMENT_STROKE,
                true, LayerCategory.OVERLAY, true, false);
        return startPlacement(List.of(), List.of(preview), 0, 0, true, handler);
    }

    private boolean startPlacement(List<?> keys, List<RenderLayer> preview, double anchorWorldX,
                                   double anchorWorldY, boolean anchorChosen, PlacementHandler handler) {
        if (!Double.isFinite(anchorWorldX) || !Double.isFinite(anchorWorldY)) {
            return false;
        }
        cancelPlacement();
        placementKeys = List.copyOf(keys);
        placementLayers = List.copyOf(preview);
        placementAnchorWorldX = anchorWorldX;
        placementAnchorWorldY = anchorWorldY;
        placementCurrentWorldX = anchorWorldX;
        placementCurrentWorldY = anchorWorldY;
        placementAnchorChosen = anchorChosen;
        placementHandler = handler;
        setCursor(Cursor.CROSSHAIR);
        redraw();
        return true;
    }

    boolean isPlacementActive() {
        return placementHandler != null;
    }

    boolean isEditorActive() {
        return selectionHandler != null;
    }

    void cancelPlacement() {
        if (placementHandler == null) {
            return;
        }
        PlacementHandler handler = placementHandler;
        clearPlacement();
        handler.onCancel();
    }

    private void clearPlacement() {
        placementHandler = null;
        placementKeys = List.of();
        placementLayers = List.of();
        placementPrimaryPressed = false;
        placementAnchorChosen = false;
        setCursor(Cursor.DEFAULT);
        redraw();
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
        if (event.getButton() == MouseButton.SECONDARY) {
            rightPressed = true;
            rightDragged = false;
            rightPressScreenX = event.getX();
            rightPressScreenY = event.getY();
        }
        if (event.getButton() == MouseButton.PRIMARY && insidePlot(event.getX(), event.getY())) {
            double[] world = snappedWorld(event.getX(), event.getY());
            referenceWorldX = world[0];
            referenceWorldY = world[1];
        }
        updateCoordLabel(event.getX(), event.getY());
        if (placementHandler != null && event.getButton() == MouseButton.PRIMARY) {
            placementPrimaryPressed = insidePlot(event.getX(), event.getY());
        }
        if (placementHandler == null && activeSelectionHandler() != null && event.getButton() == MouseButton.PRIMARY
                && insidePlot(event.getX(), event.getY())) {
            selecting = true;
            selectionDragged = false;
            selectionPressScreenX = event.getX();
            selectionPressScreenY = event.getY();
            selectionCurrentScreenX = event.getX();
            selectionCurrentScreenY = event.getY();
        }
    }

    private void handleRelease(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            if (rightPressed && !rightDragged && placementHandler != null) {
                cancelPlacement();
                event.consume();
            } else if (rightPressed && !rightDragged && selectionHandler == null
                    && contextRequestHandler != null && insidePlot(event.getX(), event.getY())) {
                double[] world = screenToWorld(event.getX() - RULER_LEFT_WIDTH, event.getY() - RULER_TOP_HEIGHT);
                contextRequestHandler.onContextRequest(world[0], world[1], event.getScreenX(), event.getScreenY());
                event.consume();
            }
            rightPressed = false;
            return;
        }
        if (placementHandler != null && event.getButton() == MouseButton.PRIMARY) {
            if (placementPrimaryPressed && insidePlot(event.getX(), event.getY())) {
                double[] world = snappedWorld(event.getX(), event.getY());
                if (!placementAnchorChosen) {
                    placementAnchorWorldX = world[0];
                    placementAnchorWorldY = world[1];
                    placementCurrentWorldX = world[0];
                    placementCurrentWorldY = world[1];
                    placementAnchorChosen = true;
                    placementHandler.onAnchorChosen();
                    redraw();
                } else {
                    PlacementHandler handler = placementHandler;
                    double dx = world[0] - placementAnchorWorldX;
                    double dy = world[1] - placementAnchorWorldY;
                    clearPlacement();
                    handler.onCommit(dx, dy);
                }
                event.consume();
            }
            placementPrimaryPressed = false;
            return;
        }
        if (!selecting || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        selecting = false;
        double[] press = screenToWorld(selectionPressScreenX - RULER_LEFT_WIDTH, selectionPressScreenY - RULER_TOP_HEIGHT);
        boolean additive = event.isControlDown();
        if (selectionDragged) {
            double[] release = screenToWorld(event.getX() - RULER_LEFT_WIDTH, event.getY() - RULER_TOP_HEIGHT);
            activeSelectionHandler().onBox(press[0], press[1], release[0], release[1], additive);
        } else if (event.getClickCount() == 2 && !additive && selectionHandler == null
                && insidePlot(event.getX(), event.getY())) {
            activeSelectionHandler().onDoubleClick(press[0], press[1]);
        } else {
            activeSelectionHandler().onClick(press[0], press[1], additive);
        }
        redraw();
    }

    private void handleDrag(MouseEvent event) {
        if (placementHandler != null && event.isPrimaryButtonDown()) {
            updatePlacement(event.getX(), event.getY());
            updateCoordLabel(event.getX(), event.getY());
            return;
        }
        if (selecting) {
            selectionCurrentScreenX = event.getX();
            selectionCurrentScreenY = event.getY();
            if (Math.abs(event.getX() - selectionPressScreenX) > CLICK_DRAG_THRESHOLD_PX
                    || Math.abs(event.getY() - selectionPressScreenY) > CLICK_DRAG_THRESHOLD_PX) {
                selectionDragged = true;
            }
            redraw();
            updateCoordLabel(event.getX(), event.getY());
            return;
        }
        if (rightPressed && Math.hypot(event.getX() - rightPressScreenX,
                event.getY() - rightPressScreenY) > CLICK_DRAG_THRESHOLD_PX) {
            rightDragged = true;
        }
        if (!event.isSecondaryButtonDown() && !event.isMiddleButtonDown()) {
            return;
        }
        if (event.isSecondaryButtonDown() && !rightDragged) {
            return;
        }
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
        updatePlacement(event.getX(), event.getY());
        updateCoordLabel(event.getX(), event.getY());
    }

    private void updatePlacement(double screenX, double screenY) {
        if (placementHandler != null && placementAnchorChosen && insidePlot(screenX, screenY)) {
            double[] world = snappedWorld(screenX, screenY);
            placementCurrentWorldX = world[0];
            placementCurrentWorldY = world[1];
            redraw();
        }
    }

    private SelectionHandler activeSelectionHandler() {
        return selectionHandler != null ? selectionHandler : defaultSelectionHandler;
    }

    private boolean insidePlot(double screenX, double screenY) {
        return screenX >= RULER_LEFT_WIDTH && screenY >= RULER_TOP_HEIGHT
                && screenX < getWidth() && screenY < getHeight();
    }

    private void updateCoordLabel(double screenX, double screenY) {
        cursorScreenX = screenX;
        cursorScreenY = screenY;
        cursorInsidePlot = insidePlot(screenX, screenY);
        double[] world = snappedWorld(screenX, screenY);
        coordLabel.setText(formatHud(world[0] - referenceWorldX, world[1] - referenceWorldY,
                world[0], world[1], units));
        coordinateListener.accept(String.format(java.util.Locale.ROOT,
                "X: %.4f   Y: %.4f", world[0], world[1]));
        drawSnapCursor();
    }

    private void drawSnapCursor() {
        GraphicsContext gc = snapCursorCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, snapCursorCanvas.getWidth(), snapCursorCanvas.getHeight());
        if (!gridSnapEnabled || !cursorInsidePlot || getWidth() <= RULER_LEFT_WIDTH
                || getHeight() <= RULER_TOP_HEIGHT) {
            return;
        }
        double[] world = snappedWorld(cursorScreenX, cursorScreenY);
        double[] screen = worldToScreen(world[0], world[1],
                getWidth() - RULER_LEFT_WIDTH, getHeight() - RULER_TOP_HEIGHT);
        double x = screen[0] + RULER_LEFT_WIDTH;
        double y = screen[1] + RULER_TOP_HEIGHT;
        if (!insidePlot(x, y)) {
            return;
        }
        gc.save();
        gc.beginPath();
        gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT,
                getWidth() - RULER_LEFT_WIDTH, getHeight() - RULER_TOP_HEIGHT);
        gc.clip();
        // Match both the cursor and its halo to the canvas theme, including over copper fills.
        gc.setStroke(palette.snapOutline());
        gc.setLineWidth(3.5);
        gc.strokeLine(x - 8, y, x + 8, y);
        gc.strokeLine(x, y - 8, x, y + 8);
        gc.setStroke(palette.snapCursor());
        gc.setLineWidth(1.7);
        gc.strokeLine(x - 8, y, x + 8, y);
        gc.strokeLine(x, y - 8, x, y + 8);
        gc.restore();
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
        gc.setFill(palette.background());
        gc.fillRect(0, 0, width, height);

        double step = niceStep(80.0 / scale);
        drawGrid(gc, contentWidth, contentHeight, step);
        if (axisVisible) {
            drawAxisCrosshair(gc, contentWidth, contentHeight);
        }
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
        if (workspaceVisible) {
            drawWorkspace(gc, contentWidth, contentHeight);
        }
        drawPlacementPreview(gc, contentWidth, contentHeight);
        drawSelectedObjectBounds(gc, contentWidth, contentHeight);
        drawSelectionBox(gc);
        drawRulers(gc, width, height, contentWidth, contentHeight, step);
        drawSnapCursor();
    }

    private void drawPlacementPreview(GraphicsContext gc, double contentWidth, double contentHeight) {
        if (placementHandler == null || !placementAnchorChosen) {
            return;
        }
        gc.save();
        gc.beginPath();
        gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
        gc.clip();
        gc.translate((placementCurrentWorldX - placementAnchorWorldX) * scale,
                -(placementCurrentWorldY - placementAnchorWorldY) * scale);
        gc.setLineDashes(5, 4);
        for (RenderLayer layer : placementLayers) {
            drawLayer(gc, new RenderLayer(layer.geometry(), layer.strokeOnly(), PLACEMENT_FILL,
                    PLACEMENT_STROKE, true, layer.category(), layer.filled(), false), contentWidth, contentHeight);
        }
        gc.restore();
    }

    private void drawSelectedObjectBounds(GraphicsContext gc, double contentWidth, double contentHeight) {
        gc.setStroke(SELECTED_OBJECT_LINE);
        gc.setLineWidth(2);
        gc.setLineDashes(6, 4);
        for (Envelope bounds : selectedObjectBounds) {
            double[] topLeft = worldToScreen(bounds.getMinX(), bounds.getMaxY(), contentWidth, contentHeight);
            double[] bottomRight = worldToScreen(bounds.getMaxX(), bounds.getMinY(), contentWidth, contentHeight);
            double x = topLeft[0] + RULER_LEFT_WIDTH;
            double y = topLeft[1] + RULER_TOP_HEIGHT;
            double width = Math.max(6, bottomRight[0] - topLeft[0]);
            double height = Math.max(6, bottomRight[1] - topLeft[1]);
            gc.strokeRect(x - 3, y - 3, width + 6, height + 6);
        }
        gc.setLineDashes();
    }

    private void drawSelectionBox(GraphicsContext gc) {
        if (!selecting || !selectionDragged) {
            return;
        }
        boolean enclosing = selectionCurrentScreenX >= selectionPressScreenX;
        double x = Math.min(selectionPressScreenX, selectionCurrentScreenX);
        double y = Math.min(selectionPressScreenY, selectionCurrentScreenY);
        double w = Math.abs(selectionCurrentScreenX - selectionPressScreenX);
        double h = Math.abs(selectionCurrentScreenY - selectionPressScreenY);
        gc.setFill(enclosing ? ENCLOSING_BOX_FILL : TOUCHING_BOX_FILL);
        gc.fillRect(x, y, w, h);
        gc.setStroke(enclosing ? ENCLOSING_BOX_LINE : TOUCHING_BOX_LINE);
        gc.setLineWidth(1);
        gc.strokeRect(x, y, w, h);
    }

    private void drawGrid(GraphicsContext gc, double contentWidth, double contentHeight, double step) {
        gc.setStroke(palette.gridLine());
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
        gc.setStroke(palette.axisLine());
        gc.setLineWidth(1.5);
        double[] origin = worldToScreen(0, 0, contentWidth, contentHeight);
        double ox = origin[0] + RULER_LEFT_WIDTH;
        double oy = origin[1] + RULER_TOP_HEIGHT;
        gc.strokeLine(RULER_LEFT_WIDTH, oy, canvas.getWidth(), oy);
        gc.strokeLine(ox, RULER_TOP_HEIGHT, ox, canvas.getHeight());
    }

    private void drawWorkspace(GraphicsContext gc, double contentWidth, double contentHeight) {
        double width = "IN".equalsIgnoreCase(units) ? 210.0 / 25.4 : 210.0;
        double height = "IN".equalsIgnoreCase(units) ? 297.0 / 25.4 : 297.0;
        double[] lowerLeft = worldToScreen(0, 0, contentWidth, contentHeight);
        double[] upperRight = worldToScreen(width, height, contentWidth, contentHeight);
        gc.save();
        gc.beginPath();
        gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
        gc.clip();
        gc.setStroke(palette.axisLine());
        gc.setLineWidth(1.2);
        gc.setLineDashes(7, 4);
        gc.strokeRect(lowerLeft[0] + RULER_LEFT_WIDTH, upperRight[1] + RULER_TOP_HEIGHT,
                upperRight[0] - lowerLeft[0], lowerLeft[1] - upperRight[1]);
        gc.restore();
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
                // Not closed: a strokeOnly LineString is not always a closed ring - the
                // Cutout Tool's preview (appTools/ToolCutOut.py's bridge gaps) is
                // deliberately made of OPEN arcs, and closing each one back to its own
                // start here drew a spurious chord straight across the gap it represents.
                // An isolation ring's own coordinates already repeat the start point as
                // the end point, so leaving this open draws it correctly too either way.
                addRing(gc, coordinates, contentWidth, contentHeight, false);
                gc.stroke();
            } else if (part instanceof Polygon polygon) {
                gc.beginPath();
                addRing(gc, polygon.getExteriorRing().getCoordinates(), contentWidth, contentHeight, true);
                for (int r = 0; r < polygon.getNumInteriorRing(); r++) {
                    addRing(gc, polygon.getInteriorRingN(r).getCoordinates(), contentWidth, contentHeight, true);
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

    private void addRing(GraphicsContext gc, Coordinate[] coordinates, double contentWidth, double contentHeight, boolean close) {
        if (coordinates.length == 0) {
            return;
        }
        double[] first = worldToScreen(coordinates[0].x, coordinates[0].y, contentWidth, contentHeight);
        gc.moveTo(first[0] + RULER_LEFT_WIDTH, first[1] + RULER_TOP_HEIGHT);
        for (int i = 1; i < coordinates.length; i++) {
            double[] p = worldToScreen(coordinates[i].x, coordinates[i].y, contentWidth, contentHeight);
            gc.lineTo(p[0] + RULER_LEFT_WIDTH, p[1] + RULER_TOP_HEIGHT);
        }
        if (close) {
            gc.closePath();
        }
    }

    private void drawRulers(GraphicsContext gc, double width, double height, double contentWidth, double contentHeight, double step) {
        gc.setFill(palette.rulerBackground());
        gc.fillRect(0, 0, width, RULER_TOP_HEIGHT);
        gc.fillRect(0, 0, RULER_LEFT_WIDTH, height);

        gc.setFill(palette.rulerText());
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
