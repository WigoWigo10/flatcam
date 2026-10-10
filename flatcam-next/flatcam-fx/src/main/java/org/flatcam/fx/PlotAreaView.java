package org.flatcam.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.flatcam.cam.gerber.edit.TrackBendMode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
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

    /** A cheaper visual-only CNC path used while the precise buffered stroke is subpixel. */
    private record LodGeometry(Geometry centerlines, double strokeWidthWorld, boolean stroked) {
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

    /** Display-only tool content. Categories retain distinct inks without creating project layers. */
    record PreviewLayer(Geometry geometry, LayerCategory category, boolean strokeOnly) {
        PreviewLayer {
            java.util.Objects.requireNonNull(geometry);
            if (category != LayerCategory.GERBER && category != LayerCategory.EXCELLON && category != LayerCategory.GEOMETRY)
                throw new IllegalArgumentException("Unsupported tool preview category: " + category);
        }
    }
    private record PreviewOverlayKey(LayerCategory category, boolean strokeOnly) { }
    record PreviewColors(Color fill, Color stroke) { }

    static PreviewColors toolPreviewColors(LayerCategory category, boolean dark) {
        return switch (category) {
            case GERBER -> new PreviewColors(Color.web(dark ? "#40BFF68C" : "#007EAD73"),
                    Color.web(dark ? "#8ADAFF" : "#005778"));
            case EXCELLON -> new PreviewColors(Color.web(dark ? "#FFBC57D9" : "#C66A00BF"),
                    Color.web(dark ? "#FFDA91" : "#813B00"));
            case GEOMETRY -> new PreviewColors(Color.web(dark ? "#C694FF47" : "#8751B638"),
                    Color.web(dark ? "#CE9FFF" : "#602D91"));
            default -> throw new IllegalArgumentException("Unsupported tool preview category: " + category);
        };
    }

    /** A text label pinned to a world position - e.g. one drill's place in the machining order. */
    record Arrow(double x, double y, double dx, double dy, double length) {
    }

    /**
     * Arrows pre-thinned into zoom levels so drawing never has to choose among thousands
     * of candidates: level k keeps the longest arrow of each cell of extent/2^k world
     * units. Picking a level from the zoom and drawing that list is cheap, and because
     * positions are fixed in the world the arrows stay put while panning instead of
     * shuffling as a screen-cell scheme would.
     */
    private static final class ArrowLevels {
        private static final int MAX_LEVEL = 14;
        private final double minX;
        private final double minY;
        private final double extent;
        private final List<List<Arrow>> levels = new ArrayList<>();

        ArrowLevels(List<Arrow> source) {
            List<Arrow> sorted = new ArrayList<>(source);
            sorted.sort((a, b) -> Double.compare(b.length(), a.length()));
            double loX = Double.POSITIVE_INFINITY;
            double loY = Double.POSITIVE_INFINITY;
            double hiX = Double.NEGATIVE_INFINITY;
            double hiY = Double.NEGATIVE_INFINITY;
            for (Arrow a : sorted) {
                loX = Math.min(loX, a.x());
                loY = Math.min(loY, a.y());
                hiX = Math.max(hiX, a.x());
                hiY = Math.max(hiY, a.y());
            }
            minX = loX;
            minY = loY;
            extent = Math.max(1e-9, Math.max(hiX - loX, hiY - loY));
            for (int level = 0; level <= MAX_LEVEL; level++) {
                double cell = extent / (1L << level);
                java.util.Set<Long> claimed = new java.util.HashSet<>();
                List<Arrow> kept = new ArrayList<>();
                for (Arrow a : sorted) {
                    long cx = (long) Math.floor((a.x() - minX) / cell);
                    long cy = (long) Math.floor((a.y() - minY) / cell);
                    if (claimed.add(cx * 1_000_003L + cy)) {
                        kept.add(a);
                    }
                }
                levels.add(kept);
                if (kept.size() == sorted.size()) {
                    break;
                }
            }
        }

        /** The arrows to draw when one screen cell of {@code cellPixels} is {@code cellPixels / scale} world units. */
        List<Arrow> forZoom(double scale, double cellPixels) {
            double world = cellPixels / Math.max(scale, 1e-12);
            int level = (int) Math.ceil(Math.log(extent / world) / Math.log(2));
            return levels.get(Math.max(0, Math.min(levels.size() - 1, level)));
        }
    }

    record Annotation(double x, double y, String text) {
    }

    /** CNCJob "Display Annotation" colors: Python's cncjob_annotation_fontcolor, and its dark-theme inversion. */
    private static final Color ANNOTATION_LIGHT = Color.web("#990000");
    private static final Color ARROW_LIGHT = Color.web("#0B7A35");
    private static final Color ARROW_DARK = Color.web("#7DF59A");
    /** Stroke width (px) from which a wide milling path also shows its individual passes. */
    private static final double PASS_LINES_MIN_WIDTH = 7;
    private static final double ARROW_CELL = 64;
    private static final double ARROW_MIN_SEGMENT = 14;
    private static final Color ANNOTATION_DARK = Color.web("#66FFFF");
    private static final javafx.scene.text.Font ANNOTATION_FONT = javafx.scene.text.Font.font(11);
    /** Screen cell a label claims; later labels landing in a claimed cell are skipped instead of piling up. */
    private static final double ANNOTATION_CELL_WIDTH = 22;
    private static final double ANNOTATION_CELL_HEIGHT = 13;

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

        default void onAnchorChosen(double worldX, double worldY) {
            onAnchorChosen();
        }

        void onCommit(double dx, double dy);

        void onCancel();
    }

    interface TrackPlacementHandler {
        default void onPathChanged(int anchorCount, TrackBendMode mode) {
        }

        void onCommit(List<Coordinate> points);

        void onCancel();
    }

    enum TwoPointShape {
        RECTANGLE, CIRCLE, SLOT
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
    private static final Color EDITOR_HIGHLIGHT_COLOR = Color.web("#0000FFFF");
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

    private static final PlotPalette CLASSIC_LIGHT_PALETTE = new PlotPalette(
            Color.web("#f7f7f7"), Color.web("#e7e7e7"), Color.web("#dedede"),
            Color.web("#c44747"), Color.web("#606060"),
            Color.web("#e53935"), Color.rgb(255, 255, 255, 0.9));
    private static final PlotPalette CLASSIC_DARK_PALETTE = new PlotPalette(
            Color.web("#101010"), Color.web("#202020"), Color.web("#2b2b2b"),
            Color.web("#b33a3a"), Color.web("#9a9a9a"),
            Color.web("#ff6b6b"), Color.rgb(10, 14, 20, 0.92));
    private static final PlotPalette ICE_LIGHT_PALETTE = new PlotPalette(
            Color.web("#f7fafc"), Color.web("#e8f0f5"), Color.web("#d7e3ec"),
            Color.web("#cf4a4a"), Color.web("#57606a"),
            Color.web("#e53935"), Color.rgb(255, 255, 255, 0.9));
    private static final PlotPalette ICE_DARK_PALETTE = new PlotPalette(
            Color.web("#0d141d"), Color.web("#141e2a"), Color.web("#253443"),
            Color.web("#c44b55"), Color.web("#a6b9c8"),
            Color.web("#ff6b6b"), Color.rgb(10, 14, 20, 0.92));

    private final Canvas canvas = new Canvas();
    private final CanvasPlotRenderer vectorRenderer = new CanvasPlotRenderer();
    /** Opt-in diagnostics: completion means commands issued, not a GPU-presented frame. */
    record RenderSample(long commandsNanos, boolean ready, boolean failed) { }
    private Consumer<RenderSample> renderObserver;
    private PlotCncProbe cncProbe;

    /** Diagnostic omissions cannot be installed by normal startup/preferences. FX-owned, borrowed observer. */
    void setCncBenchmarkProbe(PlotCncProbe probe) {
        if (!javafx.application.Platform.isFxApplicationThread()) throw new IllegalStateException("Plot probe belongs to FX");
        if (disposed && probe != null) throw new IllegalStateException("Plot probe cannot outlive viewport disposal");
        if (probe != null && !Boolean.getBoolean("flatcam.plot.benchmark"))
            throw new IllegalStateException("CNC ablation requires the opt-in Plot benchmark");
        cncProbe = probe;
        requestInteractionRedraw();
    }

    private boolean includesCncPass(PlotCncProbe.Pass pass) { return cncProbe == null || cncProbe.includes(pass); }
    private boolean includesCncBody(double lineWidth) { return cncProbe == null || cncProbe.includesBody(lineWidth); }
    private void recordCncPass(PlotCncProbe.Pass pass, boolean raster, double width) {
        if (cncProbe != null) cncProbe.record(pass, raster, width);
    }

    void setRenderObserver(Consumer<RenderSample> observer) { renderObserver = observer; }
    boolean renderReady() {
        return !disposed && !interactionRedrawPending && !indexCache.preparing()
                && !indexCache.failed() && !densityRenderer.preparing();
    }
    boolean renderFailed() { return indexCache.failed(); }
    PlotCamera cameraSnapshot() {
        return camera(Math.max(1, getWidth() - RULER_LEFT_WIDTH), Math.max(1, getHeight() - RULER_TOP_HEIGHT));
    }
    /** Diagnostic/camera restoration entry: same coalesced scheduling as pan/zoom input. */
    void setCamera(PlotCamera camera) {
        viewCenterX = camera.centerX();
        viewCenterY = camera.centerY();
        scale = clamp(camera.scale());
        requestInteractionRedraw();
    }

    private PlotCamera camera(double width, double height) {
        return new PlotCamera(viewCenterX, viewCenterY, scale, width, height, RULER_LEFT_WIDTH, RULER_TOP_HEIGHT);
    }
    private final AnimationTimer interactionRedrawTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            stop();
            interactionRedrawPending = false;
            redraw();
        }
    };
    private boolean interactionRedrawPending;
    private final Canvas editorHighlightCanvas = new Canvas();
    private final Canvas snapCursorCanvas = new Canvas();
    private final Label coordLabel = new Label("Dx: 0.0000 [mm]\nDy: 0.0000 [mm]\n\nX: 0.0000 [mm]\nY: 0.0000 [mm]");
    private java.util.function.Consumer<String> coordinateListener = ignored -> {};
    private final Map<Object, RenderLayer> layers = new LinkedHashMap<>();
    private final Map<Object, LodGeometry> lodLayers = new LinkedHashMap<>();
    private static final boolean DENSITY_ASYNC = !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density.async"));
    private record IndexKey(Object layer, int kind) { }
    private final AsyncPlotIndexCache indexCache = new AsyncPlotIndexCache(javafx.application.Platform::runLater,
            ignored -> requestInteractionRedraw(), DENSITY_ASYNC
                    && !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.index.async")));
    private final Label preparingLabel = new Label("Preparando visualização...");
    private boolean disposed;
    private boolean lastLayerDense;
    private final PlotDensityRenderer densityRenderer = new PlotDensityRenderer(DENSITY_ASYNC,
            !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density.pixelBuffer")),
            this::acceptDensityFrame, this::requestInteractionRedraw);
    private boolean lastLayerStale;
    private final PlotAreaPerformance performance = PlotAreaPerformance.fromSystemProperties();
    private final UiFluidityMetrics uiFluidity = new UiFluidityMetrics("fx");
    private Timeline uiFluidityTimer;
    private int batchDepth;
    private boolean batchRedrawPending;
    private PlotPalette palette = ICE_LIGHT_PALETTE;
    private Color annotationColor = ANNOTATION_LIGHT;
    private Color arrowColor = ARROW_LIGHT;
    private final CncStepView stepView = new CncStepView();
    private java.util.function.Consumer<String> stepListener = ignored -> {};
    private double stepPressX;
    private double stepPressY;
    private boolean stepPressArmed;
    /** The caption button a primary press landed on, run on release if the pointer is still over it. */
    private int hudPressAction = CncStepView.HUD_NONE;
    private boolean hudButtonHover;
    private Cursor cursorBeforeHud = Cursor.DEFAULT;
    /** Stroke width (px) for the centerline drawing in progress, or NaN outside it. */
    private double lodLineWidth = Double.NaN;
    private final Map<Object, ArrowLevels> arrows = new LinkedHashMap<>();
    private final Map<Object, List<Annotation>> annotations = new LinkedHashMap<>();
    private Geometry editorHighlightGeometry;
    /** A faint, dashed "where it was" companion to the editor highlight (e.g. the un-mirrored outline). */
    private Geometry editorReferenceGeometry;
    private static final Color EDITOR_REFERENCE_COLOR = Color.web("#0000FF38");
    private static final Color EDITOR_FILL_COLOR = Color.web("#00B4FF2E");
    private static final Color EDITOR_REFERENCE_FILL_COLOR = Color.web("#0000FF12");
    /** Translucent interiors under the outlines: the new (mirrored) one and the original one. */
    private Geometry editorFillGeometry;
    private Geometry editorReferenceFillGeometry;
    /** The mirrored content itself (copper, drills, paths), filled, over the board interior. */
    private Geometry editorContentGeometry;
    private List<PreviewLayer> toolPreviewContents = List.of();
    private static final Color EDITOR_CONTENT_FILL_COLOR = Color.web("#00B4FF99");
    private static final Color EDITOR_CONTENT_STROKE_COLOR = Color.web("#0078B4");
    private boolean editorHighlightStrokeOnly;
    /** Density-mode keys of the editor overlays (they are not entries of {@link #layers}). */
    private static final Object HIGHLIGHT_KEY = "editor-highlight";
    private static final Object REFERENCE_KEY = "editor-reference";
    private static final Object FILL_KEY = new Object();
    private static final Object REFERENCE_FILL_KEY = new Object();
    private static final Object CONTENT_KEY = new Object();

    private double scale = 3.0;
    private double viewCenterX = 50;
    private double viewCenterY = 40;
    private double lastDragScreenX;
    private double lastDragScreenY;
    private double referenceWorldX;
    private double referenceWorldY;
    private boolean gridSnapEnabled = true;
    private boolean gridVisible = true;
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
    private boolean placementFixedPreview;
    private double placementTrackWidth;
    private boolean placementRegion;
    private boolean placementFreePath;
    private TwoPointShape placementTwoPointShape;
    private TrackPlacementHandler trackPlacementHandler;
    private List<Coordinate> placementTrackPoints = new ArrayList<>();
    private List<Integer> placementTrackUndoSizes = new ArrayList<>();
    private TrackBendMode placementTrackMode = TrackBendMode.FORTY_FIVE;

    PlotAreaView() {
        setFocusTraversable(true);
        getChildren().add(canvas);
        editorHighlightCanvas.setMouseTransparent(true);
        getChildren().add(editorHighlightCanvas);
        snapCursorCanvas.setMouseTransparent(true);
        getChildren().add(snapCursorCanvas);
        StackPane.setAlignment(coordLabel, Pos.BOTTOM_LEFT);
        coordLabel.getStyleClass().add("plot-coord-label");
        coordLabel.setMouseTransparent(true);
        getChildren().add(coordLabel);
        preparingLabel.setId("plot-preparing");
        preparingLabel.getStyleClass().add("plot-coord-label");
        preparingLabel.setMouseTransparent(true);
        preparingLabel.setVisible(false);
        StackPane.setAlignment(preparingLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(preparingLabel, new javafx.geometry.Insets(24, 12, 0, 0));
        getChildren().add(preparingLabel);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        editorHighlightCanvas.widthProperty().bind(widthProperty());
        editorHighlightCanvas.heightProperty().bind(heightProperty());
        snapCursorCanvas.widthProperty().bind(widthProperty());
        snapCursorCanvas.heightProperty().bind(heightProperty());
        // A resize updates width and height separately; repaint only once on
        // the next pulse instead of redrawing dense layers for both changes.
        widthProperty().addListener((obs, oldVal, newVal) -> requestInteractionRedraw());
        heightProperty().addListener((obs, oldVal, newVal) -> requestInteractionRedraw());
        editorHighlightCanvas.widthProperty().addListener((obs, oldVal, newVal) -> drawEditorHighlight());
        editorHighlightCanvas.heightProperty().addListener((obs, oldVal, newVal) -> drawEditorHighlight());
        snapCursorCanvas.widthProperty().addListener((obs, oldVal, newVal) -> drawSnapCursor());
        snapCursorCanvas.heightProperty().addListener((obs, oldVal, newVal) -> drawSnapCursor());

        setOnScroll(this::handleScroll);
        setOnMousePressed(this::handlePress);
        setOnMouseDragged(this::handleDrag);
        setOnMouseReleased(this::handleRelease);
        setOnMouseMoved(this::handleMove);
        setOnKeyPressed(event -> {
            if (!stepView.hasSelection()) {
                return;
            }
            switch (event.getCode()) {
                case LEFT, UP -> stepBy(-1);
                case RIGHT, DOWN -> stepBy(1);
                case ESCAPE -> clearStepSelection();
                case SPACE -> toggleWalk();
                default -> {
                    return;
                }
            }
            event.consume();
        });
        setOnMouseExited(e -> {
            cursorInsidePlot = false;
            drawSnapCursor();
            coordinateListener.accept("X: -   Y: -");
        });

        if (Boolean.getBoolean(UiFluidityMetrics.ENABLED_PROPERTY)) {
            uiFluidityTimer = new Timeline(new KeyFrame(Duration.millis(16),
                    event -> sampleUiFluidity()));
            uiFluidityTimer.setCycleCount(Timeline.INDEFINITE);
            uiFluidityTimer.play();
            System.err.println("[UI-FLUIDITY] app=fx enabled interval_ms=16 window_s=10");
        }

        redraw();
    }

    private void sampleUiFluidity() {
        if (getScene() == null || getScene().getWindow() == null
                || !getScene().getWindow().isShowing()
                || (getScene().getWindow() instanceof Stage stage && stage.isIconified())) {
            uiFluidity.reset();
            return;
        }
        String report = uiFluidity.tick(System.nanoTime());
        if (report != null) {
            System.err.println(report);
        }
    }

    /** Updates every canvas-owned color immediately when the application theme changes. */
    void applyTheme(ThemeOption theme) {
        palette = paletteForTheme(theme);
        annotationColor = theme == ThemeOption.CLASSIC_DARK || theme == ThemeOption.ICE_DARK
                ? ANNOTATION_DARK : ANNOTATION_LIGHT;
        arrowColor = annotationColor == ANNOTATION_DARK ? ARROW_DARK : ARROW_LIGHT;
        redraw();
    }

    /** Replaces (or, with an empty list, removes) the labels drawn for {@code key}. */
    void setAnnotations(Object key, List<Annotation> labels) {
        if (labels == null || labels.isEmpty()) {
            if (annotations.remove(key) == null) {
                return;
            }
        } else {
            annotations.put(key, List.copyOf(labels));
        }
        redraw();
    }

    /** Replaces (or, with an empty list, removes) the cutting-direction arrows drawn for {@code key}. */
    void setArrows(Object key, List<Arrow> list) {
        if (list == null || list.isEmpty()) {
            if (arrows.remove(key) == null) {
                return;
            }
        } else {
            arrows.put(key, new ArrowLevels(list));
        }
        redraw();
    }

    /** Gives a CNC Job's route to the step-by-step highlighter (see {@link CncStepView}); empty removes it. */
    void setSteps(Object key, List<org.flatcam.cam.gcode.GCodeToolpathParser.PathStep> steps,
                  List<org.flatcam.cam.gcode.GCodeToolpathParser.PathMark> marks, double widthWorld, String units) {
        boolean selectionLost = stepView.set(key, steps, marks, widthWorld, units);
        if (selectionLost) {
            stopWalk();
            stepListener.accept("");
        }
        redraw();
    }

    /** Called with the caption of the lit leg, or an empty string when the highlight is cleared. */
    void setStepListener(java.util.function.Consumer<String> listener) {
        stepListener = listener == null ? ignored -> {} : listener;
    }

    boolean hasStepSelection() {
        return stepView.hasSelection();
    }

    /** Moves the lit leg one step back (-1) or forward (+1); a manual move also stops a running playback. */
    boolean stepBy(int delta) {
        stopWalk();
        if (!stepView.stepBy(delta)) {
            return false;
        }
        stepListener.accept(stepView.describe());
        ensureStepVisible();
        redraw();
        return true;
    }

    /** Lights up leg {@code index} (0-based) of a CNC Job's route. */
    void selectStep(Object key, int index) {
        stopWalk();
        stepView.select(key, index);
        stepListener.accept(stepView.describe());
        ensureStepVisible();
        redraw();
    }

    void clearStepSelection() {
        if (!stepView.hasSelection()) {
            return;
        }
        stopWalk();
        stepView.clearSelection();
        stepListener.accept("");
        redraw();
    }

    // --- Automatic playback of the lit route ------------------------------------------------

    private static final double[] WALK_SPEEDS = {0.5, 1, 2, 4, 8};
    private static final double WALK_BASE_MILLIS = 700;
    private Timeline walkTimeline;
    private int walkSpeedIndex = 1;

    private String walkSpeedLabel() {
        double speed = WALK_SPEEDS[walkSpeedIndex];
        return speed < 1 ? String.format(java.util.Locale.ROOT, "%.1fx", speed) : (int) speed + "x";
    }

    boolean isWalking() {
        return walkTimeline != null;
    }

    /** Plays the route from the lit leg (from the start when it is already at the end), or pauses. */
    void toggleWalk() {
        if (walkTimeline != null) {
            stopWalk();
            redraw();
            return;
        }
        if (!stepView.hasSelection() || stepView.stepCount() < 2) {
            return;
        }
        if (stepView.atLastStep()) {
            stepView.select(stepView.selectedKey(), 0);
            stepListener.accept(stepView.describe());
            ensureStepVisible();
        }
        startWalk();
        redraw();
    }

    /** Cycles the playback speed (0.5x, 1x, 2x, 4x, 8x), also while playing. */
    void cycleWalkSpeed() {
        walkSpeedIndex = (walkSpeedIndex + 1) % WALK_SPEEDS.length;
        if (walkTimeline != null) {
            startWalk();
        } else {
            stepView.setWalkState(false, walkSpeedLabel());
        }
        redraw();
    }

    private void startWalk() {
        if (walkTimeline != null) {
            walkTimeline.stop();
        }
        walkTimeline = new Timeline(new KeyFrame(Duration.millis(WALK_BASE_MILLIS / WALK_SPEEDS[walkSpeedIndex]),
                event -> walkTick()));
        walkTimeline.setCycleCount(Timeline.INDEFINITE);
        walkTimeline.play();
        stepView.setWalkState(true, walkSpeedLabel());
    }

    private void walkTick() {
        if (!stepView.stepBy(1)) {
            stopWalk();
            redraw();
            return;
        }
        stepListener.accept(stepView.describe());
        ensureStepVisible();
        if (stepView.atLastStep()) {
            stopWalk();
        }
        redraw();
    }

    private void stopWalk() {
        if (walkTimeline != null) {
            walkTimeline.stop();
            walkTimeline = null;
        }
        stepView.setWalkState(false, walkSpeedLabel());
    }

    /** Pans (never zooms) so the lit leg is on screen. */
    private void ensureStepVisible() {
        Envelope box = stepView.selectedBounds();
        if (box == null || box.isNull()) {
            return;
        }
        double contentWidth = Math.max(1, getWidth() - RULER_LEFT_WIDTH);
        double contentHeight = Math.max(1, getHeight() - RULER_TOP_HEIGHT);
        Envelope view = visibleWorldBounds(viewCenterX, viewCenterY, scale, contentWidth, contentHeight);
        boolean fits = box.getWidth() <= view.getWidth() && box.getHeight() <= view.getHeight();
        if (fits ? !view.covers(box) : !view.intersects(box)) {
            Coordinate center = box.centre();
            viewCenterX = center.x;
            viewCenterY = center.y;
        }
    }

    boolean hasAnnotations(Object key) {
        return annotations.containsKey(key);
    }

    static PlotPalette paletteForTheme(ThemeOption theme) {
        return switch (theme) {
            case CLASSIC_LIGHT -> CLASSIC_LIGHT_PALETTE;
            case CLASSIC_DARK -> CLASSIC_DARK_PALETTE;
            case ICE_LIGHT -> ICE_LIGHT_PALETTE;
            case ICE_DARK -> ICE_DARK_PALETTE;
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
        lodLayers.remove(key);
        // Keep the previous normal index only for reusing unchanged immutable part metrics.
        indexCache.invalidate(new IndexKey(key, 0));
        indexCache.forget(new IndexKey(key, 1));
        forgetDensity(key);
        redraw();
    }

    void setLayerCenterlineLod(Object key, Geometry centerlines, double strokeWidthWorld) {
        setLayerCenterlineLod(key, centerlines, strokeWidthWorld, false);
    }

    /**
     * {@code stroked} keeps the centerline drawing at every zoom, stroked as wide as the
     * real cutter, so a filled milling path costs one stroke per path however far it is
     * zoomed in (the buffered polygons it replaces cost one fill per segment).
     */
    void setLayerCenterlineLod(Object key, Geometry centerlines, double strokeWidthWorld, boolean stroked) {
        if (!layers.containsKey(key)) {
            return;
        }
        if (centerlines == null || centerlines.isEmpty() || !Double.isFinite(strokeWidthWorld)
                || strokeWidthWorld <= 0) {
            lodLayers.remove(key);
        } else {
            lodLayers.put(key, new LodGeometry(centerlines, strokeWidthWorld, stroked));
        }
        indexCache.forget(new IndexKey(key, 1));
        forgetDensity(key);
        redraw();
    }

    /** Swaps a layer's geometry in place (e.g. after a Transform) without disturbing its colors/visibility/plot-kind. */
    void updateLayerGeometry(Object key, Geometry geometry) {
        if (placementKeys.contains(key)) {
            cancelPlacement();
        }
        RenderLayer layer = layers.get(key);
        if (layer != null) {
            lodLayers.remove(key);
            indexCache.invalidate(new IndexKey(key, 0));
            indexCache.forget(new IndexKey(key, 1));
            if (geometry == null || geometry.isEmpty()) forgetDensity(key);
            else invalidateDensity(key);
            layers.put(key, new RenderLayer(geometry, layer.strokeOnly(), layer.fillColor(), layer.strokeColor(),
                    layer.visible(), layer.category(), layer.filled(), layer.multicolor()));
            redraw();
        }
    }

    /** Small editor selections redraw on their own canvas, without repainting every project layer. */
    /** How many world units one screen pixel covers at the current zoom. */
    double worldPerPixel() {
        return 1.0 / scale;
    }

    void setEditorContent(Geometry content) {
        clearToolPreviewContents();
        invalidateEditorIndex(CONTENT_KEY, editorContentGeometry, content);
        editorContentGeometry = content;
        drawEditorHighlight();
    }

    /** Content is grouped by category/stroke style by the worker, not unioned on the FX thread. */
    void setToolPreviewContent(Geometry content, List<PreviewLayer> contents) {
        List<PreviewLayer> next = List.copyOf(contents);
        var styles = new java.util.HashSet<PreviewOverlayKey>();
        for (PreviewLayer layer : next)
            if (!styles.add(new PreviewOverlayKey(layer.category(), layer.strokeOnly())))
                throw new IllegalArgumentException("Duplicate category/style in tool preview.");
        for (PreviewLayer previous : toolPreviewContents) {
            if (next.stream().noneMatch(layer -> layer.category() == previous.category()
                    && layer.strokeOnly() == previous.strokeOnly()))
                forgetOverlay(new PreviewOverlayKey(previous.category(), previous.strokeOnly()));
        }
        for (PreviewLayer layer : next) {
            PreviewLayer previous = toolPreviewContents.stream().filter(before -> before.category() == layer.category()
                    && before.strokeOnly() == layer.strokeOnly()).findFirst().orElse(null);
            Object key = new PreviewOverlayKey(layer.category(), layer.strokeOnly());
            invalidateEditorIndex(key, previous == null ? null : previous.geometry(), layer.geometry());
            if (previous == null || previous.geometry() != layer.geometry()) invalidateDensity(key);
        }
        invalidateEditorIndex(CONTENT_KEY, editorContentGeometry, content);
        editorContentGeometry = content;
        toolPreviewContents = next;
        drawEditorHighlight();
    }

    private void clearToolPreviewContents() {
        for (PreviewLayer layer : toolPreviewContents)
            forgetOverlay(new PreviewOverlayKey(layer.category(), layer.strokeOnly()));
        toolPreviewContents = List.of();
    }

    void setEditorFills(Geometry fill, Geometry referenceFill) {
        invalidateEditorIndex(FILL_KEY, editorFillGeometry, fill);
        invalidateEditorIndex(REFERENCE_FILL_KEY, editorReferenceFillGeometry, referenceFill);
        editorFillGeometry = fill;
        editorReferenceFillGeometry = referenceFill;
        drawEditorHighlight();
    }

    private void invalidateEditorIndex(Object key, Geometry before, Geometry after) {
        if (before == after) return;
        if (after == null) indexCache.forget(new IndexKey(key, 2));
        else indexCache.invalidate(new IndexKey(key, 2));
    }

    void setEditorReference(Geometry geometry) {
        if (geometry != editorReferenceGeometry) {
            forgetOverlay(REFERENCE_KEY);
        }
        editorReferenceGeometry = geometry;
        drawEditorHighlight();
    }

    void setEditorHighlight(Geometry geometry, boolean strokeOnly) {
        if (geometry != editorHighlightGeometry) {
            forgetOverlay(HIGHLIGHT_KEY);
        }
        editorHighlightGeometry = geometry;
        editorHighlightStrokeOnly = strokeOnly;
        drawEditorHighlight();
    }

    private void forgetOverlay(Object key) {
        forgetDensity(key);
        indexCache.forget(new IndexKey(key, 2));
    }

    /** The display index of an overlay geometry, rebuilt only when the geometry object changes. */
    private PlotDrawableIndex overlayIndex(Object key, Geometry geometry) {
        return indexCache.getOrRequest(new IndexKey(key, 2), geometry);
    }

    void removeLayer(Object key) {
        if (placementKeys.contains(key)) {
            cancelPlacement();
        }
        layers.remove(key);
        lodLayers.remove(key);
        indexCache.forget(new IndexKey(key, 0));
        indexCache.forget(new IndexKey(key, 1));
        forgetDensity(key);
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
        clearToolPreviewContents();
        layers.clear();
        lodLayers.clear();
        indexCache.clear();
        densityRenderer.clear();
        annotations.clear();
        arrows.clear();
        stopWalk();
        stepView.clear();
        editorHighlightGeometry = null;
        editorReferenceGeometry = null;
        forgetOverlay(HIGHLIGHT_KEY);
        forgetOverlay(REFERENCE_KEY);
        editorFillGeometry = null;
        editorReferenceFillGeometry = null;
        editorContentGeometry = null;
        selectedObjectBounds = List.of();
        redraw();
    }

    /** Stop viewport workers/timers and reject queued completions when the application closes. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        indexCache.close();
        densityRenderer.close();
        interactionRedrawTimer.stop();
        if (uiFluidityTimer != null) uiFluidityTimer.stop();
        renderObserver = null;
        cncProbe = null;
        stopWalk();
    }

    /** Coalesces the many layer mutations performed while restoring a project into one repaint. */
    void beginBatchUpdate() {
        batchDepth++;
    }

    void endBatchUpdate() {
        if (batchDepth == 0) {
            throw new IllegalStateException("No Plot Area batch update is active");
        }
        if (--batchDepth == 0 && batchRedrawPending) {
            batchRedrawPending = false;
            redraw();
        }
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
        if (cursorInsidePlot) {
            // A toggle/spacing edit must refresh the current cursor and placement too;
            // repainting only the cross leaves the HUD and preview snapped until mouse movement.
            updatePlacement(cursorScreenX, cursorScreenY);
            updateCoordLabel(cursorScreenX, cursorScreenY);
        } else {
            drawSnapCursor();
        }
        if (trackPlacementHandler != null) {
            redraw();
        }
    }

    void setGridVisible(boolean visible) {
        if (gridVisible != visible) {
            gridVisible = visible;
            redraw();
        }
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

    String units() {
        return units;
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

    /** Fits display-only tool previews without adding selectable project layers. */
    void fitToBounds(Envelope envelope) {
        if (envelope == null || envelope.isNull()) return;
        fitToEnvelope(envelope);
        redraw();
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

    /** Moves the view so that the world point is in the middle, keeping the zoom. */
    void centerOn(double x, double y) {
        viewCenterX = x;
        viewCenterY = y;
        redraw();
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
        return beginEditorPlacement(geometries, false, handler);
    }

    boolean beginEditorPlacement(List<Geometry> geometries, boolean strokeOnly, PlacementHandler handler) {
        if (selectionHandler == null || geometries.isEmpty() || handler == null) {
            return false;
        }
        List<RenderLayer> preview = new ArrayList<>();
        for (Geometry geometry : geometries) {
            if (geometry == null || geometry.isEmpty()) {
                return false;
            }
            preview.add(new RenderLayer(geometry, strokeOnly, PLACEMENT_FILL, PLACEMENT_STROKE,
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

    /** Fixed, absolute preview: click confirms without translating the generated paths. */
    boolean beginEditorFixedPreview(Geometry paths, PlacementHandler handler) {
        if (!beginEditorFlashPlacement(paths, handler)) return false;
        placementFixedPreview = true;
        redraw();
        return true;
    }

    boolean confirmEditorFixedPreview() {
        if (!placementFixedPreview || placementHandler == null) return false;
        PlacementHandler handler = placementHandler;
        clearPlacement(); handler.onCommit(0, 0); return true;
    }

    /** A multi-point track using the five bend modes from the legacy Gerber editor. */
    boolean beginEditorTrackPlacement(double apertureDiameter, TrackPlacementHandler handler) {
        if (selectionHandler == null) {
            return false;
        }
        return beginTrackPlacement(apertureDiameter, handler);
    }

    private boolean beginTrackPlacement(double apertureDiameter, TrackPlacementHandler handler) {
        if (!Double.isFinite(apertureDiameter) || apertureDiameter <= 0 || handler == null) {
            return false;
        }
        PlacementHandler placementAdapter = new PlacementHandler() {
            @Override
            public void onCommit(double dx, double dy) {
                // Track placement commits through finishEditorTrackPlacement().
            }

            @Override
            public void onCancel() {
                handler.onCancel();
            }
        };
        if (!startPlacement(List.of(), List.of(), 0, 0, false, placementAdapter)) {
            return false;
        }
        placementTrackWidth = apertureDiameter;
        trackPlacementHandler = handler;
        placementTrackPoints = new ArrayList<>();
        placementTrackUndoSizes = new ArrayList<>();
        placementTrackMode = TrackBendMode.FORTY_FIVE;
        handler.onPathChanged(0, placementTrackMode);
        return true;
    }

    boolean beginEditorRegionPlacement(TrackPlacementHandler handler) {
        if (!beginEditorTrackPlacement(1, handler)) {
            return false;
        }
        placementRegion = true;
        redraw();
        return true;
    }

    /** Free-angle Geometry path/polygon, without Gerber's constrained bend modes. */
    boolean beginEditorGeometryPathPlacement(boolean polygon, TrackPlacementHandler handler) {
        if (!beginEditorTrackPlacement(1.5 / scale, handler)) {
            return false;
        }
        placementFreePath = true;
        placementTrackMode = TrackBendMode.FREE;
        placementRegion = polygon;
        notifyTrackPathChanged();
        redraw();
        return true;
    }

    boolean beginEditorTwoPointPlacement(TwoPointShape shape, PlacementHandler handler) {
        return beginEditorTwoPointPlacement(shape, 0, handler);
    }

    boolean beginEditorTwoPointPlacement(TwoPointShape shape, double strokeWidth, PlacementHandler handler) {
        if (selectionHandler == null || shape == null || handler == null
                || !startPlacement(List.of(), List.of(), 0, 0, false, handler)) {
            return false;
        }
        placementTwoPointShape = shape;
        placementTrackWidth = strokeWidth;
        redraw();
        return true;
    }

    /** Two-click rectangle used by CAM tools outside an editor session. */
    boolean beginAreaRectanglePlacement(PlacementHandler handler) {
        if (handler == null || !startPlacement(List.of(), List.of(), 0, 0, false, handler)) {
            return false;
        }
        placementTwoPointShape = TwoPointShape.RECTANGLE;
        redraw();
        return true;
    }

    /** Multi-click free-angle polygon used for NCC area selection outside an editor. */
    boolean beginAreaPolygonPlacement(TrackPlacementHandler handler) {
        if (selectionHandler != null || !beginTrackPlacement(1.5 / scale, handler)) {
            return false;
        }
        placementFreePath = true;
        placementTrackMode = TrackBendMode.FREE;
        placementRegion = true;
        notifyTrackPathChanged();
        redraw();
        return true;
    }

    private boolean startPlacement(List<?> keys, List<RenderLayer> preview, double anchorWorldX,
                                   double anchorWorldY, boolean anchorChosen, PlacementHandler handler) {
        if (!Double.isFinite(anchorWorldX) || !Double.isFinite(anchorWorldY)) {
            return false;
        }
        cancelPlacement();
        placementTrackWidth = 0;
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

    boolean isTrackPlacementActive() {
        return trackPlacementHandler != null;
    }

    boolean finishEditorTrackPlacement() {
        if (trackPlacementHandler == null || placementTrackUndoSizes.size() < (placementRegion ? 2 : 1)) {
            return false;
        }
        TrackPlacementHandler handler = trackPlacementHandler;
        List<Coordinate> points = placementTrackPoints.stream().map(Coordinate::new).toList();
        clearPlacement();
        handler.onCommit(points);
        return true;
    }

    boolean backtrackEditorTrackPlacement() {
        if (trackPlacementHandler == null || placementTrackPoints.isEmpty()) {
            return false;
        }
        if (placementTrackUndoSizes.isEmpty()) {
            placementTrackPoints.clear();
            placementAnchorChosen = false;
        } else {
            int previousSize = placementTrackUndoSizes.remove(placementTrackUndoSizes.size() - 1);
            placementTrackPoints.subList(previousSize, placementTrackPoints.size()).clear();
            Coordinate last = placementTrackPoints.get(placementTrackPoints.size() - 1);
            placementAnchorWorldX = last.x;
            placementAnchorWorldY = last.y;
        }
        notifyTrackPathChanged();
        redraw();
        return true;
    }

    boolean cycleEditorTrackBendMode(boolean reverse) {
        if (trackPlacementHandler == null || placementFreePath) {
            return false;
        }
        placementTrackMode = reverse ? placementTrackMode.previous() : placementTrackMode.next();
        notifyTrackPathChanged();
        redraw();
        return true;
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
        placementFixedPreview = false;
        placementTrackWidth = 0;
        placementRegion = false;
        placementFreePath = false;
        placementTwoPointShape = null;
        trackPlacementHandler = null;
        placementTrackPoints = new ArrayList<>();
        placementTrackUndoSizes = new ArrayList<>();
        placementTrackMode = TrackBendMode.FORTY_FIVE;
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
        requestInteractionRedraw();
        event.consume();
    }

    private void handlePress(MouseEvent event) {
        requestFocus();
        if (event.getButton() == MouseButton.PRIMARY) {
            hudPressAction = stepView.hudAction(event.getX(), event.getY());
            if (hudPressAction != CncStepView.HUD_NONE) {
                stepPressArmed = false;
                event.consume();
                return;
            }
        }
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
        stepPressArmed = event.getButton() == MouseButton.PRIMARY && placementHandler == null
                && insidePlot(event.getX(), event.getY());
        stepPressX = event.getX();
        stepPressY = event.getY();
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
        if (event.getButton() == MouseButton.PRIMARY && hudPressAction != CncStepView.HUD_NONE) {
            int pressed = hudPressAction;
            hudPressAction = CncStepView.HUD_NONE;
            if (stepView.hudAction(event.getX(), event.getY()) == pressed) {
                switch (pressed) {
                    case CncStepView.HUD_PREVIOUS -> stepBy(-1);
                    case CncStepView.HUD_NEXT -> stepBy(1);
                    case CncStepView.HUD_PLAY -> toggleWalk();
                    case CncStepView.HUD_SPEED -> cycleWalkSpeed();
                    case CncStepView.HUD_CLEAR -> clearStepSelection();
                    default -> { }
                }
            }
            event.consume();
            return;
        }
        if (event.getButton() == MouseButton.SECONDARY) {
            if (rightPressed && !rightDragged && placementHandler != null) {
                if (placementFreePath && trackPlacementHandler != null) {
                    if (!finishEditorTrackPlacement()) {
                        cancelPlacement();
                    }
                } else if (trackPlacementHandler != null && !placementTrackPoints.isEmpty()
                        && insidePlot(event.getX(), event.getY())) {
                    double[] world = snappedWorld(event.getX(), event.getY());
                    appendTrackSegment(new Coordinate(world[0], world[1]));
                    if (!finishEditorTrackPlacement()) {
                        cancelPlacement();
                    }
                } else {
                    cancelPlacement();
                }
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
                if (trackPlacementHandler != null) {
                    if (!placementAnchorChosen) {
                        Coordinate first = new Coordinate(world[0], world[1]);
                        placementTrackPoints.add(first);
                        placementAnchorWorldX = first.x;
                        placementAnchorWorldY = first.y;
                        placementCurrentWorldX = first.x;
                        placementCurrentWorldY = first.y;
                        placementAnchorChosen = true;
                        notifyTrackPathChanged();
                    } else {
                        appendTrackSegment(new Coordinate(world[0], world[1]));
                        if (event.getClickCount() == 2) {
                            finishEditorTrackPlacement();
                        }
                    }
                    redraw();
                } else if (placementFixedPreview) {
                    confirmEditorFixedPreview();
                } else if (!placementAnchorChosen) {
                    placementAnchorWorldX = world[0];
                    placementAnchorWorldY = world[1];
                    placementCurrentWorldX = world[0];
                    placementCurrentWorldY = world[1];
                    placementAnchorChosen = true;
                    placementHandler.onAnchorChosen(world[0], world[1]);
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
        if (event.getButton() == MouseButton.PRIMARY && stepPressArmed) {
            stepPressArmed = false;
            if (selectionHandler == null && Math.hypot(event.getX() - stepPressX, event.getY() - stepPressY)
                    <= CLICK_DRAG_THRESHOLD_PX && insidePlot(event.getX(), event.getY())) {
                double[] world = screenToWorld(event.getX() - RULER_LEFT_WIDTH, event.getY() - RULER_TOP_HEIGHT);
                if (!stepView.isEmpty() && stepView.hit(world[0], world[1], scale)) {
                    stopWalk();
                    selecting = false;
                    stepListener.accept(stepView.describe());
                    redraw();
                    // Keep the keyboard on the plot: something else may grab focus right after the click.
                    requestFocus();
                    javafx.application.Platform.runLater(this::requestFocus);
                    event.consume();
                    return;
                }
                if (stepView.hasSelection()) {
                    clearStepSelection();
                }
            }
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
        drawEditorHighlight();
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
            drawEditorHighlight();
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
        requestInteractionRedraw();
        updateCoordLabel(event.getX(), event.getY());
    }

    private void handleMove(MouseEvent event) {
        int over = stepView.hudAction(event.getX(), event.getY());
        boolean onButton = over != CncStepView.HUD_NONE && over != CncStepView.HUD_PANEL;
        if (onButton && !hudButtonHover) {
            cursorBeforeHud = getCursor();
            setCursor(Cursor.HAND);
            hudButtonHover = true;
        } else if (!onButton && hudButtonHover) {
            setCursor(cursorBeforeHud);
            hudButtonHover = false;
        }
        updatePlacement(event.getX(), event.getY());
        updateCoordLabel(event.getX(), event.getY());
    }

    private void updatePlacement(double screenX, double screenY) {
        if (placementHandler != null && !placementFixedPreview && placementAnchorChosen && insidePlot(screenX, screenY)) {
            double[] world = snappedWorld(screenX, screenY);
            placementCurrentWorldX = world[0];
            placementCurrentWorldY = world[1];
            drawEditorHighlight();
        }
    }

    private boolean appendTrackSegment(Coordinate end) {
        if (trackPlacementHandler == null || placementTrackPoints.isEmpty()) {
            return false;
        }
        Coordinate start = placementTrackPoints.get(placementTrackPoints.size() - 1);
        List<Coordinate> routed = effectiveTrackBendMode().route(start, end);
        if (routed.size() < 2) {
            return false;
        }
        placementTrackUndoSizes.add(placementTrackPoints.size());
        for (int i = 1; i < routed.size(); i++) {
            placementTrackPoints.add(new Coordinate(routed.get(i)));
        }
        placementAnchorWorldX = end.x;
        placementAnchorWorldY = end.y;
        placementCurrentWorldX = end.x;
        placementCurrentWorldY = end.y;
        notifyTrackPathChanged();
        return true;
    }

    private void notifyTrackPathChanged() {
        if (trackPlacementHandler != null) {
            int anchorCount = placementTrackPoints.isEmpty() ? 0 : placementTrackUndoSizes.size() + 1;
            trackPlacementHandler.onPathChanged(anchorCount, placementTrackMode);
        }
    }

    private TrackBendMode effectiveTrackBendMode() {
        // AppGerberEditor.py applies bend modes only while grid snap is enabled.
        return placementFreePath || !gridSnapEnabled ? TrackBendMode.FREE : placementTrackMode;
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
        PlotCamera camera = camera(contentWidth, contentHeight);
        return new double[]{camera.screenX(worldX) - RULER_LEFT_WIDTH, camera.screenY(worldY) - RULER_TOP_HEIGHT};
    }

    private CncStepView.View stepViewTransform(double contentWidth, double contentHeight) {
        return new CncStepView.View(viewCenterX, viewCenterY, scale, contentWidth, contentHeight,
                RULER_LEFT_WIDTH, RULER_TOP_HEIGHT);
    }

    private double[] screenToWorld(double contentX, double contentY) {
        PlotCamera camera = cameraSnapshot();
        return new double[]{camera.worldX(contentX + RULER_LEFT_WIDTH), camera.worldY(contentY + RULER_TOP_HEIGHT)};
    }

    // --- Drawing --------------------------------------------------------

    /** Pan and zoom may produce several input events per pulse; paint only their latest state. */
    private void requestInteractionRedraw() {
        if (disposed) return;
        if (!interactionRedrawPending) {
            interactionRedrawPending = true;
            interactionRedrawTimer.start();
        }
    }

    private void redraw() {
        if (disposed) return;
        if (interactionRedrawPending) {
            interactionRedrawTimer.stop();
            interactionRedrawPending = false;
        }
        if (batchDepth > 0) {
            batchRedrawPending = true;
            return;
        }
        boolean profiling = performance.enabled();
        long redrawStart = profiling || renderObserver != null ? System.nanoTime() : 0;
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double contentWidth = Math.max(1, width - RULER_LEFT_WIDTH);
        double contentHeight = Math.max(1, height - RULER_TOP_HEIGHT);
        Envelope viewBounds = visibleWorldBounds(viewCenterX, viewCenterY, scale,
                contentWidth, contentHeight);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(palette.background());
        gc.fillRect(0, 0, width, height);

        double step = niceStep(80.0 / scale);
        if (gridVisible) drawGrid(gc, contentWidth, contentHeight, step);
        if (axisVisible) {
            drawAxisCrosshair(gc, contentWidth, contentHeight);
        }
        long baseNanos = profiling ? System.nanoTime() - redrawStart : 0;
        long layerNanos = 0;
        int visibleLayers = 0;
        List<PlotAreaPerformance.LayerSample> samples = profiling ? new ArrayList<>() : List.of();
        // Category order (GERBER, then EXCELLON, then OVERLAY) is fixed regardless of each
        // layer's position in the map, so bringToFront (a plain reinsert-at-end) only ever
        // changes a layer's position relative to others in the same category.
        for (LayerCategory category : LayerCategory.values()) {
            for (Map.Entry<Object, RenderLayer> entry : layers.entrySet()) {
                RenderLayer layer = entry.getValue();
                LodGeometry lod = lodLayers.get(entry.getKey());
                boolean lodActive = useCenterlineLod(layer, lod, scale);
                Geometry drawnGeometry = lodActive ? lod.centerlines() : layer.geometry();
                if (layer.category() == category && layer.visible() && drawnGeometry != null) {
                    long layerStart = profiling ? System.nanoTime() : 0;
                    PlotDrawableIndex index = drawableIndex(entry.getKey(), drawnGeometry, lodActive);
                    // Never fall back to a massive unindexed traversal while preparation is pending.
                    if (index == null || !index.intersects(viewBounds)) continue;
                    if (lodActive) {
                        gc.save();
                        gc.setLineCap(StrokeLineCap.ROUND);
                        Color lodColor = layer.fillColor();
                        if (lod.stroked()) {
                            gc.setLineJoin(StrokeLineJoin.ROUND);
                            lodLineWidth = Math.max(1.5, lod.strokeWidthWorld() * scale);
                            if (lodLineWidth < 4 && lodColor.getOpacity() < 0.65) {
                                // A thin translucent travel line is nearly invisible: firm it up and dash it.
                                lodColor = lodColor.deriveColor(0, 1, 1, 0.65 / lodColor.getOpacity());
                                gc.setLineDashes(7, 5);
                            }
                        }
                        double wideStroke = lodLineWidth;
                        try {
                            RenderLayer lodLayer = new RenderLayer(drawnGeometry, true, lodColor, lodColor,
                                    true, category, true, false);
                            lastLayerDense = lastLayerStale = false;
                            if (includesCncBody(lodLineWidth)) {
                                // Packed segments use an image; wide strokes remain vector.
                                if (!drawDensityLayer(gc, entry.getKey(), lodLayer, index, viewBounds, contentWidth,
                                        contentHeight, lodLineWidth))
                                    drawLayer(gc, lodLayer, contentWidth, contentHeight, viewBounds, index);
                                recordCncPass(PlotCncProbe.Pass.BODY, lastLayerDense, lodLineWidth);
                            }
                            if (lod.stroked() && wideStroke >= PASS_LINES_MIN_WIDTH
                                    && includesCncPass(PlotCncProbe.Pass.PASS_LINES)) {
                                // Zoomed in on a wide cutter: show each pass as a thin line over the body.
                                Color passColor = lodColor.deriveColor(0, 1, 0.45, 0.9);
                                gc.setLineDashes();
                                lodLineWidth = 1.25;
                                drawLayer(gc, new RenderLayer(drawnGeometry, true, passColor, passColor,
                                        true, category, true, false), contentWidth, contentHeight, viewBounds, index);
                                recordCncPass(PlotCncProbe.Pass.PASS_LINES, false, lodLineWidth);
                            }
                        } finally {
                            lodLineWidth = Double.NaN;
                        }
                        gc.restore();
                    } else {
                        lastLayerDense = lastLayerStale = false;
                        if (category != LayerCategory.CNCJOB || includesCncBody(layer.strokeOnly() ? 1.5 : 1)) {
                            if (!drawDensityLayer(gc, entry.getKey(), layer, index, viewBounds, contentWidth,
                                    contentHeight, layer.strokeOnly() ? 1.5 : 1))
                                drawLayer(gc, layer, contentWidth, contentHeight, viewBounds, index);
                            if (category == LayerCategory.CNCJOB)
                                recordCncPass(PlotCncProbe.Pass.BODY, lastLayerDense, layer.strokeOnly() ? 1.5 : 1);
                        }
                    }
                    if (profiling) {
                        long elapsed = System.nanoTime() - layerStart;
                        layerNanos += elapsed;
                        visibleLayers++;
                        Object key = entry.getKey();
                        String name = key instanceof TreeItem<?> item ? String.valueOf(item.getValue()) : String.valueOf(key);
                        samples.add(new PlotAreaPerformance.LayerSample(
                                category + ":" + name + (lodActive ? "[LOD]" : "") + (lastLayerDense ? (lastLayerStale ? "[DENSE-STALE]" : "[DENSE]") : ""), elapsed));
                    }
                }
            }
        }
        if (workspaceVisible) {
            drawWorkspace(gc, contentWidth, contentHeight);
        }
        if (stepView.hasSelection()) {
            // Focus: fade everything drawn so far, then light up the chosen leg and its neighbours.
            Color background = palette.background();
            gc.setFill(Color.color(background.getRed(), background.getGreen(), background.getBlue(), 0.62));
            gc.fillRect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
            gc.save();
            gc.beginPath();
            gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
            gc.clip();
            stepView.drawLegs(gc, stepViewTransform(contentWidth, contentHeight), background);
            gc.restore();
        } else if (includesCncPass(PlotCncProbe.Pass.DECORATIONS)) {
            drawArrows(gc, contentWidth, contentHeight, viewBounds);
        }
        if (includesCncPass(PlotCncProbe.Pass.DECORATIONS)) {
            drawAnnotations(gc, contentWidth, contentHeight, viewBounds);
            if (!arrows.isEmpty() || !annotations.isEmpty())
                recordCncPass(PlotCncProbe.Pass.DECORATIONS, false, 0);
        }
        if (stepView.hasSelection()) {
            stepView.drawHud(gc, stepViewTransform(contentWidth, contentHeight), palette.background(), annotationColor);
        }
        drawSelectedObjectBounds(gc, contentWidth, contentHeight);
        drawRulers(gc, width, height, contentWidth, contentHeight, step);
        drawEditorHighlight();
        drawSnapCursor();
        if (profiling) {
            performance.recordRedraw(System.nanoTime() - redrawStart, baseNanos,
                    layerNanos, visibleLayers, samples);
        }
        if (renderObserver != null)
            renderObserver.accept(new RenderSample(System.nanoTime() - redrawStart, renderReady(), renderFailed()));
    }

    boolean profilingEnabled() {
        return performance.enabled();
    }

    void logPerformancePhase(String phase, long startNanos) {
        performance.logPhase(phase, startNanos);
    }

    private void forgetDensity(Object key) { densityRenderer.forget(key); }

    /** Retain pixels only as a display preview; editing/selection keep the current geometry. */
    private void invalidateDensity(Object key) { densityRenderer.invalidate(key); }

    private boolean acceptDensityFrame(Object key) {
        return !disposed && (layers.containsKey(key) || key == HIGHLIGHT_KEY || key == REFERENCE_KEY);
    }

    PlotDensityRenderer.Stats rasterStats() { return densityRenderer.stats(); }

    private boolean drawDensityLayer(GraphicsContext gc, Object key, RenderLayer layer, PlotDrawableIndex index,
                                     Envelope viewBounds, double contentWidth, double contentHeight, double lineWidth) {
        var result = densityRenderer.draw(gc, key, index, layer.strokeOnly(), layer.multicolor(), layer.strokeColor(),
                lineWidth, camera(contentWidth, contentHeight), viewBounds);
        lastLayerDense = result != PlotDensityRenderer.DrawResult.VECTOR;
        lastLayerStale = result == PlotDensityRenderer.DrawResult.PREVIEW;
        return lastLayerDense;
    }

    private static boolean useCenterlineLod(RenderLayer layer, LodGeometry lod, double scale) {
        return layer.category() == LayerCategory.CNCJOB && lod != null
                && (lod.stroked() ? layer.filled() && !layer.multicolor()
                        : shouldUseCenterlineLod(layer.filled(), layer.multicolor(),
                                lod.strokeWidthWorld(), scale));
    }

    private PlotDrawableIndex drawableIndex(Object key, Geometry geometry, boolean lod) {
        IndexKey binding = new IndexKey(key, lod ? 1 : 0);
        PlotDrawableIndex ready = indexCache.getOrRequest(binding, geometry);
        // Reuse only an already prepared index; don't traverse the new large geometry on the UI thread.
        return ready != null ? ready : indexCache.previous(binding);
    }

    static boolean shouldUseCenterlineLod(boolean filled, boolean multicolor,
                                          double strokeWidthWorld, double scale) {
        return filled && !multicolor && strokeWidthWorld > 0 && scale > 0
                && strokeWidthWorld * scale < 1.5;
    }

    private void drawEditorHighlight() {
        double width = editorHighlightCanvas.getWidth();
        double height = editorHighlightCanvas.getHeight();
        GraphicsContext gc = editorHighlightCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        double contentWidth = Math.max(1, width - RULER_LEFT_WIDTH);
        double contentHeight = Math.max(1, height - RULER_TOP_HEIGHT);
        Envelope viewBounds = visibleWorldBounds(viewCenterX, viewCenterY, scale, contentWidth, contentHeight);
        for (Geometry[] fill : new Geometry[][]{{editorReferenceFillGeometry, null}, {editorFillGeometry, null}}) {
            if (fill[0] != null && !fill[0].isEmpty()) {
                Color color = fill[0] == editorFillGeometry ? EDITOR_FILL_COLOR : EDITOR_REFERENCE_FILL_COLOR;
                gc.save();
                gc.beginPath();
                gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
                gc.clip();
                drawOverlay(gc, fill[0] == editorFillGeometry ? FILL_KEY : REFERENCE_FILL_KEY,
                        new RenderLayer(fill[0], false, color, Color.TRANSPARENT, true, LayerCategory.OVERLAY,
                                true, false), viewBounds, contentWidth, contentHeight);
                gc.restore();
            }
        }
        if (toolPreviewContents.isEmpty() && editorContentGeometry != null && !editorContentGeometry.isEmpty()) {
            gc.save();
            gc.beginPath();
            gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
            gc.clip();
            drawOverlay(gc, CONTENT_KEY, new RenderLayer(editorContentGeometry, false, EDITOR_CONTENT_FILL_COLOR,
                    EDITOR_CONTENT_STROKE_COLOR, true, LayerCategory.OVERLAY, true, false),
                    viewBounds, contentWidth, contentHeight);
            gc.restore();
        }
        if (editorReferenceGeometry != null && !editorReferenceGeometry.isEmpty()) {
            gc.save();
            gc.beginPath();
            gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
            gc.clip();
            gc.setLineDashes(8, 6);
            drawOverlay(gc, REFERENCE_KEY, new RenderLayer(editorReferenceGeometry, true, EDITOR_REFERENCE_COLOR,
                    EDITOR_REFERENCE_COLOR, true, LayerCategory.OVERLAY, true, false), viewBounds, contentWidth,
                    contentHeight);
            gc.restore();
        }
        if (editorHighlightGeometry != null && !editorHighlightGeometry.isEmpty()) {
            gc.save();
            gc.beginPath();
            gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
            gc.clip();
            drawOverlay(gc, HIGHLIGHT_KEY, new RenderLayer(editorHighlightGeometry, editorHighlightStrokeOnly,
                    EDITOR_HIGHLIGHT_COLOR, EDITOR_HIGHLIGHT_COLOR, true, LayerCategory.OVERLAY, true, false),
                    viewBounds, contentWidth, contentHeight);
            gc.restore();
        }
        if (!toolPreviewContents.isEmpty()) {
            gc.save();
            gc.beginPath();
            gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
            gc.clip();
            for (PreviewLayer layer : toolPreviewContents) {
                if (layer.geometry().isEmpty()) continue;
                PreviewColors ink = toolPreviewColors(layer.category(), palette.background().getBrightness() < .5);
                drawOverlay(gc, new PreviewOverlayKey(layer.category(), layer.strokeOnly()),
                        new RenderLayer(layer.geometry(), layer.strokeOnly(), ink.fill(), ink.stroke(),
                                true, LayerCategory.OVERLAY, true, false), viewBounds, contentWidth, contentHeight);
            }
            gc.restore();
        }
        drawPlacementPreview(gc, contentWidth, contentHeight);
        drawSelectionBox(gc);
        preparingLabel.setText(indexCache.failed() ? "Falha ao preparar visualização; consulte os diagnósticos."
                : "Preparando visualização...");
        preparingLabel.setVisible(indexCache.preparing() || indexCache.failed() || densityRenderer.preparing());
    }

    /**
     * An editor overlay (the blue selection, the faint reference outline): only the shapes in view are drawn, and a
     * stroke-only one with thousands of packed segments goes through the density level of detail like a project layer
     * does - a selected region of a very dense Geometry used to be drawn whole, as vector, on every zoom.
     */
    private void drawOverlay(GraphicsContext gc, Object key, RenderLayer layer, Envelope viewBounds,
                             double contentWidth, double contentHeight) {
        PlotDrawableIndex index = overlayIndex(key, layer.geometry());
        if (index == null) return;
        if (!drawDensityLayer(gc, key, layer, index, viewBounds, contentWidth, contentHeight,
                layer.strokeOnly() ? 1.5 : 1)) {
            drawLayer(gc, layer, contentWidth, contentHeight, viewBounds, index);
        }
    }

    private void drawPlacementPreview(GraphicsContext gc, double contentWidth, double contentHeight) {
        if (placementHandler == null || !placementAnchorChosen) {
            return;
        }
        gc.save();
        gc.beginPath();
        gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
        gc.clip();
        if (placementTwoPointShape != null) {
            double[] first = worldToScreen(placementAnchorWorldX, placementAnchorWorldY,
                    contentWidth, contentHeight);
            double[] second = worldToScreen(placementCurrentWorldX, placementCurrentWorldY,
                    contentWidth, contentHeight);
            double firstX = first[0] + RULER_LEFT_WIDTH;
            double firstY = first[1] + RULER_TOP_HEIGHT;
            double secondX = second[0] + RULER_LEFT_WIDTH;
            double secondY = second[1] + RULER_TOP_HEIGHT;
            gc.setStroke(PLACEMENT_STROKE);
            gc.setFill(PLACEMENT_FILL);
            gc.setLineWidth(1.5);
            if (placementTwoPointShape == TwoPointShape.RECTANGLE) {
                double x = Math.min(firstX, secondX);
                double y = Math.min(firstY, secondY);
                double width = Math.abs(secondX - firstX);
                double height = Math.abs(secondY - firstY);
                gc.fillRect(x, y, width, height);
                gc.strokeRect(x, y, width, height);
            } else if (placementTwoPointShape == TwoPointShape.SLOT) {
                gc.setLineCap(StrokeLineCap.ROUND);
                gc.setLineWidth(Math.max(1.5, placementTrackWidth * scale));
                gc.strokeLine(firstX, firstY, secondX, secondY);
            } else {
                double radius = Math.hypot(secondX - firstX, secondY - firstY);
                gc.fillOval(firstX - radius, firstY - radius, radius * 2, radius * 2);
                gc.strokeOval(firstX - radius, firstY - radius, radius * 2, radius * 2);
            }
            gc.restore();
            return;
        }
        if (placementTrackWidth > 0 && !placementTrackPoints.isEmpty()) {
            List<Coordinate> previewPoints = new ArrayList<>(placementTrackPoints);
            List<Coordinate> pending = effectiveTrackBendMode().route(
                    placementTrackPoints.get(placementTrackPoints.size() - 1),
                    new Coordinate(placementCurrentWorldX, placementCurrentWorldY));
            for (int i = 1; i < pending.size(); i++) {
                previewPoints.add(pending.get(i));
            }
            gc.setStroke(PLACEMENT_STROKE);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineWidth(placementRegion ? 2 : Math.max(1.5, placementTrackWidth * scale));
            if (placementRegion && previewPoints.size() >= 3) {
                double[] first = worldToScreen(previewPoints.get(0).x, previewPoints.get(0).y,
                        contentWidth, contentHeight);
                gc.beginPath();
                gc.moveTo(first[0] + RULER_LEFT_WIDTH, first[1] + RULER_TOP_HEIGHT);
                for (int i = 1; i < previewPoints.size(); i++) {
                    double[] point = worldToScreen(previewPoints.get(i).x, previewPoints.get(i).y,
                            contentWidth, contentHeight);
                    gc.lineTo(point[0] + RULER_LEFT_WIDTH, point[1] + RULER_TOP_HEIGHT);
                }
                gc.closePath();
                gc.setFill(PLACEMENT_FILL);
                gc.fill();
            }
            for (int i = 1; i < previewPoints.size(); i++) {
                double[] start = worldToScreen(previewPoints.get(i - 1).x, previewPoints.get(i - 1).y,
                        contentWidth, contentHeight);
                double[] end = worldToScreen(previewPoints.get(i).x, previewPoints.get(i).y,
                        contentWidth, contentHeight);
                gc.strokeLine(start[0] + RULER_LEFT_WIDTH, start[1] + RULER_TOP_HEIGHT,
                        end[0] + RULER_LEFT_WIDTH, end[1] + RULER_TOP_HEIGHT);
            }
            if (placementRegion && previewPoints.size() >= 3) {
                double[] last = worldToScreen(previewPoints.get(previewPoints.size() - 1).x,
                        previewPoints.get(previewPoints.size() - 1).y, contentWidth, contentHeight);
                double[] first = worldToScreen(previewPoints.get(0).x, previewPoints.get(0).y,
                        contentWidth, contentHeight);
                gc.strokeLine(last[0] + RULER_LEFT_WIDTH, last[1] + RULER_TOP_HEIGHT,
                        first[0] + RULER_LEFT_WIDTH, first[1] + RULER_TOP_HEIGHT);
            }
            gc.restore();
            return;
        }
        gc.translate((placementCurrentWorldX - placementAnchorWorldX) * scale,
                -(placementCurrentWorldY - placementAnchorWorldY) * scale);
        gc.setLineDashes(5, 4);
        for (RenderLayer layer : placementLayers) {
            drawLayer(gc, new RenderLayer(layer.geometry(), layer.strokeOnly(), PLACEMENT_FILL,
                    PLACEMENT_STROKE, true, layer.category(), layer.filled(), false), contentWidth, contentHeight);
        }
        gc.restore();
    }

    /**
     * Python draws every annotation even when zoomed out, so dense boards turn into
     * an unreadable blob. Here each label claims a small screen cell and later ones
     * that would land on it are skipped (earliest in the order wins), so the numbers
     * that do show stay legible and more appear as the user zooms in. A background-
     * colored halo keeps them readable over the filled holes.
     */
    /**
     * Small triangles at the middle of cutting moves showing which way the tool travels.
     * Only moves that are long enough on screen get one and each screen cell holds at
     * most one, so zooming in reveals more instead of piling arrows on curves.
     */
    private void drawArrows(GraphicsContext gc, double contentWidth, double contentHeight, Envelope viewBounds) {
        if (arrows.isEmpty()) {
            return;
        }
        gc.save();
        gc.beginPath();
        gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
        gc.clip();
        gc.setLineWidth(2.5);
        gc.setStroke(palette.background());
        gc.setFill(arrowColor);
        for (ArrowLevels levels : arrows.values()) {
            for (Arrow arrow : levels.forZoom(scale, ARROW_CELL)) {
                if (arrow.length() * scale < ARROW_MIN_SEGMENT || !viewBounds.contains(arrow.x(), arrow.y())) {
                    continue;
                }
                double[] screen = worldToScreen(arrow.x(), arrow.y(), contentWidth, contentHeight);
                double cx = screen[0] + RULER_LEFT_WIDTH;
                double cy = screen[1] + RULER_TOP_HEIGHT;
                double ux = arrow.dx();
                double uy = -arrow.dy(); // screen Y grows downward
                double px = -uy;
                double py = ux;
                double[] xs = {cx + ux * 6, cx - ux * 4.5 + px * 4, cx - ux * 4.5 - px * 4};
                double[] ys = {cy + uy * 6, cy - uy * 4.5 + py * 4, cy - uy * 4.5 - py * 4};
                gc.strokePolygon(xs, ys, 3);
                gc.fillPolygon(xs, ys, 3);
            }
        }
        gc.restore();
    }

    private static int parseMarkNumber(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void drawAnnotations(GraphicsContext gc, double contentWidth, double contentHeight, Envelope viewBounds) {
        if (annotations.isEmpty()) {
            return;
        }
        gc.save();
        gc.beginPath();
        gc.rect(RULER_LEFT_WIDTH, RULER_TOP_HEIGHT, contentWidth, contentHeight);
        gc.clip();
        gc.setFont(ANNOTATION_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        Color background = palette.background();
        Color badgeFill = Color.color(background.getRed(), background.getGreen(), background.getBlue(), 0.82);
        java.util.Set<Long> claimed = new java.util.HashSet<>();
        boolean focus = stepView.hasSelection();
        // With a leg lit its numbers go first (they claim their spot) in their role colour; the rest fade.
        for (int pass = 0; pass < (focus ? 2 : 1); pass++) {
            for (Map.Entry<Object, List<Annotation>> entry : annotations.entrySet()) {
                Map<Integer, Integer> roles = focus ? stepView.markRoles(entry.getKey()) : Map.of();
                for (Annotation label : entry.getValue()) {
                    Integer role = focus ? roles.get(parseMarkNumber(label.text())) : null;
                    if ((pass == 0) != (role != null) && focus) {
                        continue;
                    }
                    if (!viewBounds.contains(label.x(), label.y())) {
                        continue;
                    }
                    double[] screen = worldToScreen(label.x(), label.y(), contentWidth, contentHeight);
                    long cellX = (long) Math.floor(screen[0] / ANNOTATION_CELL_WIDTH);
                    long cellY = (long) Math.floor(screen[1] / ANNOTATION_CELL_HEIGHT);
                    if (!claimed.add(cellX * 1_000_003L + cellY)) {
                        continue;
                    }
                    double ax = screen[0] + RULER_LEFT_WIDTH;
                    double ay = screen[1] + RULER_TOP_HEIGHT;
                    if (role != null) {
                        CncStepView.drawRoleBadge(gc, ax, ay, label.text(), role, background);
                        gc.setFont(ANNOTATION_FONT);
                        continue;
                    }
                    double textWidth = label.text().length() * 6.4;
                    double badgeX = ax + 4;
                    double badgeY = ay - 4 - 13;
                    gc.setGlobalAlpha(focus ? 0.35 : 1);
                    gc.setFill(annotationColor);
                    gc.fillOval(ax - 1.5, ay - 1.5, 3, 3);
                    gc.setFill(badgeFill);
                    gc.fillRoundRect(badgeX, badgeY, textWidth + 6, 13, 6, 6);
                    gc.setLineWidth(1);
                    gc.setStroke(annotationColor.deriveColor(0, 1, 1, 0.7));
                    gc.strokeRoundRect(badgeX, badgeY, textWidth + 6, 13, 6, 6);
                    gc.setFill(annotationColor);
                    gc.fillText(label.text(), badgeX + 3, badgeY + 10);
                    gc.setGlobalAlpha(1);
                }
            }
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
        drawLayer(gc, layer, contentWidth, contentHeight, null);
    }

    private void drawLayer(GraphicsContext gc, RenderLayer layer, double contentWidth,
                           double contentHeight, Envelope viewBounds) {
        drawLayer(gc, layer, contentWidth, contentHeight, viewBounds, null);
    }

    private void drawLayer(GraphicsContext gc, RenderLayer layer, double contentWidth,
                           double contentHeight, Envelope viewBounds, PlotDrawableIndex index) {
        double width = !Double.isNaN(lodLineWidth) ? lodLineWidth : layer.strokeOnly() ? 1.5 : 1;
        // While preparation is pending the already-indexed previous display revision is intentional.
        Geometry displayed = index == null ? layer.geometry() : index.geometry();
        vectorRenderer.draw(gc, camera(contentWidth, contentHeight), new PlotRenderSnapshot(displayed, index,
                layer.strokeOnly(), layer.filled(), layer.multicolor(), layer.fillColor(), layer.strokeColor(),
                width, viewBounds));
    }

    /** Python project arrays can nest a MultiPolygon inside a GeometryCollection. */
    static void forEachDrawablePart(Geometry geometry, Consumer<Geometry> visitor) {
        CanvasPlotRenderer.forEachPart(geometry, visitor);
    }

    /** Includes a small stroke margin so paths touching the plot edge are not culled. */
    static Envelope visibleWorldBounds(double centerX, double centerY, double scale,
                                       double contentWidth, double contentHeight) {
        return new PlotCamera(centerX, centerY, scale, contentWidth, contentHeight, 0, 0).visibleBounds();
    }

    static boolean intersectsViewport(Geometry geometry, Envelope viewBounds) {
        return geometry.getEnvelopeInternal().intersects(viewBounds);
    }

    static boolean omitSubpixelOpenPathVertex(boolean closed, int index, int size,
                                              double deltaX, double deltaY) {
        return CanvasPlotRenderer.omitSubpixelOpenPathVertex(closed, index, size, deltaX, deltaY);
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
