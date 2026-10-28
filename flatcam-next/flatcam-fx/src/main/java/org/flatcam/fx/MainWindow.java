package org.flatcam.fx;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.app.project.PythonProjectIO;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.app.project.PythonProjectWriter;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.app.project.ToolsDatabase;
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.convert.InvertGerber;
import org.flatcam.cam.convert.CornerMarkers;
import org.flatcam.cam.convert.EtchCompensation;
import org.flatcam.cam.convert.Fiducials;
import org.flatcam.cam.convert.QrCodeMarker;
import org.flatcam.cam.solderpaste.SolderPaste;
import org.flatcam.cam.analysis.MinimumDistance;
import org.flatcam.cam.analysis.RulesCheck;
import org.flatcam.cam.convert.CopperThieving;
import org.flatcam.cam.convert.ExtractDrills;
import org.flatcam.cam.transform.Calibration;
import org.flatcam.cam.convert.ObjectConversion;
import org.flatcam.cam.convert.OutlineToArea;
import org.flatcam.cam.convert.Punch;
import org.flatcam.cam.convert.Subtract;
import org.flatcam.cam.merge.ExcellonJoin;
import org.flatcam.cam.panel.Panelize;
import org.flatcam.cam.ncc.PaintParameters;
import org.flatcam.cam.merge.GeometryJoin;
import org.flatcam.cam.merge.GerberJoin;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutResult;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.CutoutKind;
import org.flatcam.cam.cutout.CutoutShape;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.dxf.DxfExporter;
import org.flatcam.cam.dxf.DxfImporter;
import org.flatcam.cam.excellon.ExcellonExporter;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonMillingGenerator;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.CncJobResult;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.flatcam.cam.gerber.GerberGeometryGenerator;
import org.flatcam.cam.gerber.GerberExporter;
import org.flatcam.cam.hpgl.HpglImporter;
import org.flatcam.cam.pdf.PdfImporter;
import org.flatcam.cam.pdf.PrintPdfExporter;
import org.flatcam.cam.svg.FilmExporter;
import org.flatcam.cam.svg.SvgExporter;
import org.flatcam.cam.svg.SvgImporter;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gerber.edit.GerberEditSession;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationResult;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.ncc.NccParameters;
import org.flatcam.cam.ncc.NccResult;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.transform.AlignObjects;
import org.flatcam.cam.transform.TransformOp;
import org.flatcam.cam.transform.TransformReference;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Shell shape taken from the legacy app, not from CONTEXTO_FLATCAM_FX.md's
 * secao 6 sketch - see UI_INVENTORY.md. FlatCAM/PyQt5 barely opens separate
 * windows: a left tab strip alternates Project/Properties/Tool in the same
 * space, and a center tab strip has a non-closable "Plot Area" (viewport)
 * plus auxiliary tabs (Preferences, Tools Database, editors, ...) opened on
 * demand and reused rather than duplicated. This class replicates that
 * shape; auxiliary tabs use the same open/reuse/focus pattern as Python.
 */
final class MainWindow implements TclFlatcamHost {

    private static final Color IDLE_COLOR = Color.web("#4caf50");
    private static final Color RUNNING_COLOR = Color.web("#f0ad4e");
    private static final Color CANCELLED_COLOR = Color.web("#9e9e9e");
    private static final Color ERROR_COLOR = Color.web("#e53935");
    private static final double LEGACY_OBJECT_ALPHA = 191.0 / 255.0; // defaults.py: hexadecimal BF
    private static final Color GERBER_FILL = Color.web("#BBF268", LEGACY_OBJECT_ALPHA);
    private static final Color GERBER_STROKE = Color.web("#006E20", LEGACY_OBJECT_ALPHA);
    private static final Color DRILL_FILL = Color.web("#C40000", LEGACY_OBJECT_ALPHA);
    private static final Color DRILL_STROKE = Color.web("#750000", LEGACY_OBJECT_ALPHA);
    private static final Color GEOMETRY_FILL = Color.web("#FF0000");
    private static final Color GEOMETRY_STROKE = Color.web("#ff0000");
    private static final Color ISOLATION_COLOR = Color.web("#28d0d0");
    private static final Color MARK_COLOR = Color.web("#ff2fd6", 0.65);
    // CNCJob toolpath colors - straight from defaults.py's cncjob plot defaults, confirmed
    // against camlib.py's CNCjob.plot2(): cut fully opaque, travel ~30% opacity, travel drawn
    // on top of cut (see PlotAreaView.LayerCategory's CNCJOB ordering and addCncJobToProject()).
    private static final Color CNC_CUT_FILL = Color.web("#5E6CFF");
    private static final Color CNC_CUT_STROKE = Color.web("#4650BD");
    private static final Color CNC_TRAVEL_FILL = Color.web("#F0E24D", 0.30);
    private static final Color CNC_TRAVEL_STROKE = Color.web("#B5AB3A", 0.30);
    private static final GeometryFactory CNC_GEOMETRY_FACTORY = new GeometryFactory();

    private final JobExecutor jobExecutor;
    private ToolsDatabasePanel toolsDatabasePanel;
    private TerminalPanel terminalPanel;
    private long tclProjectEpoch;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressPercentLabel = new Label("0%");
    private final Label statusLabel = new Label(I18n.text("ui.45a489c52210"));
    private final Circle statusDot = new Circle(5, Color.web("#4caf50"));
    private final Label activityLabel = new Label("Idle.");
    private final Label unitsLabel = new Label("[mm]");
    private final Button runDemoJobButton = new Button();
    private final Button cancelJobButton = new Button();
    private final CompactJobProgress compactJobProgress = new CompactJobProgress(
            progressBar.progressProperty(), cancelJobButton.disableProperty(), this::cancelDemoJob);
    private final TextArea console = new TextArea();
    private final TabPane centerTabs = new TabPane();
    private final StackPane propertiesContainer = new StackPane();
    private final Label propertiesPlaceholder = new Label(I18n.text("ui.78962f43c05e"));

    /**
     * A generated G-code file, tracked in the "CNC Jobs" tree category once GCodeGenerator
     * writes one. Edited text is kept here and embedded in project saves;
     * a supported G0/G1 XY preview can be reconstructed from that text.
     */
    private record CncJobEntry(String sourceName, Path outputFile, String gcode,
                               Geometry travelGeometry, Geometry cutGeometry,
                               Geometry travelCenterlines, Geometry cutCenterlines,
                               double previewStrokeWidth, GCodeToolpathParser.ToolpathStats stats) {
    }

    private record LoadedCncJob(String name, String sourceName, Path outputPath, String gcode,
                                Geometry travelGeometry, Geometry cutGeometry,
                                Geometry travelCenterlines, Geometry cutCenterlines,
                                double previewStrokeWidth, String units, boolean visible,
                                GCodeToolpathParser.ToolpathStats stats, org.flatcam.app.project.CncPlotSettings plotSettings) {
    }

    /** A Geometry CNC Job plus the totals read back from its own text (see GCodeToolpathParser.ToolpathStats). */
    private record GeneratedCncJob(CncJobResult job, GCodeToolpathParser.Result preview) {
    }

    private record ImportedGCode(String text, GCodeToolpathParser.Result preview) {
    }

    /** {@code tools} is empty for a plain single-purpose Geometry (no tool association); see NccToolPanel's doc. */
    private record GeometryEntry(String sourceName, String units, Geometry geometry,
                                 boolean strokeOnly, List<ToolGeometry> tools,
                                 GeometryGCodeParameters cncDefaults, org.flatcam.cam.gcode.MachiningTarget machiningTarget) {
        private GeometryEntry(String sourceName, String units, Geometry geometry,
                              boolean strokeOnly, List<ToolGeometry> tools, GeometryGCodeParameters cncDefaults) {
            this(sourceName, units, geometry, strokeOnly, tools, cncDefaults, null);
        }
        private GeometryEntry(String sourceName, String units, Geometry geometry,
                              boolean strokeOnly, List<ToolGeometry> tools) {
            this(sourceName, units, geometry, strokeOnly, tools, null);
        }
    }

    /** Gerber/Excellon entries already carry their own fully-resolved geometry (ProjectFileIO), no re-parsing needed. */
    private record LoadedProject(List<ProjectFile.GerberEntry> gerbers, List<ProjectFile.ExcellonEntry> excellons,
                                 List<ProjectFile.GeometryEntry> geometries,
                                 List<LoadedCncJob> cncJobs, List<String> warnings,
                                 List<String> importWarnings, ProjectFile.PythonLegacy pythonLegacy) {
    }

    /** PlotAreaView layer keys for a CNC Job's two toolpath layers - see {@link #addCncJobToProject}. */
    private record CncTravelLayerKey(TreeItem<String> cncJobItem) {
    }

    private record CncCutLayerKey(TreeItem<String> cncJobItem) {
    }

    /** PlotAreaView annotation key for a CNC Job's drill-order numbers ("Display Annotation"). */
    private record CncAnnotationKey(TreeItem<String> cncJobItem) {
    }

    /** PlotAreaView layer key for the apertures table's "Mark" highlight overlay - see {@link GerberAperturesTable}. */
    private record MarkLayerKey(TreeItem<String> gerberItem) {
    }

    /** Files opened/generated so far, keyed by their tree item - back the Properties tab and the item context menu. */
    private final Map<TreeItem<String>, GerberImage> gerberByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, ExcellonImage> excellonByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, Map<Integer, DrillGCodeParameters>> drillDefaultsByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, DrillCncSettings> drillCncSettingsByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, GeometryCncSettings> geometryCncSettingsByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, GeometryEntry> geometryByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, CncJobEntry> cncJobByItem = new LinkedHashMap<>();
    /** Tools unticked in a CNC Job's tools table (Python's per-row "Plot" checkbox). */
    private final Map<TreeItem<String>, Set<Integer>> hiddenCncTools = new LinkedHashMap<>();
    /** CNC Jobs whose "Display Annotation" was switched off - on by default, like Python's cncjob_annotation. */
    private final Set<TreeItem<String>> cncAnnotationsOff = new LinkedHashSet<>();
    /** CNC Jobs whose cutting-direction arrows were switched off (on by default). */
    private final Set<TreeItem<String>> cncArrowsOff = new LinkedHashSet<>();
    private final Map<TreeItem<String>, org.flatcam.app.project.CncPlotSettings> cncPlotSettings = new LinkedHashMap<>();
    /** Caption of the lit route leg in the CNC Job properties, when that panel is showing. */
    private Label cncStepLabel;
    /** Conversion caveats remain attached when an imported Python project is saved as native .fcnproj. */
    private List<String> currentProjectImportWarnings = List.of();
    /** The FlatCAM Python project that is open, so that saving as .FlatPrj keeps what the FX does not model. */
    private ProjectFile.PythonLegacy currentPythonLegacy;
    /** Stable item identity survives renames/edits. New/copied items never inherit an original object's metadata. */
    private final Map<TreeItem<String>, String> pythonOriginByItem = new LinkedHashMap<>();
    /** Gerber objects currently plotted as unbuffered trace centerlines instead of solid copper. */
    private final Set<TreeItem<String>> gerberFollowItems = new LinkedHashSet<>();
    /** Original file path for Gerber/Excellon items - what gets written to a saved project file. */
    private final Map<TreeItem<String>, Path> sourcePathByItem = new LinkedHashMap<>();
    private final RecentFiles recentFiles;
    private final ProjectChangeTracker projectChanges=new ProjectChangeTracker();
    private final UnsavedChangesGuard unsavedGuard=new UnsavedChangesGuard();
    private java.util.function.Function<Boolean,UnsavedChangesGuard.Choice> projectDecision=this::askProjectDecision;
    private java.util.function.Consumer<java.util.function.Consumer<Boolean>> guardedProjectSave=this::saveProject;
    private Runnable applicationClose=Platform::exit;
    private Path currentProjectFile;

    private final PlotAreaView plotAreaView = new PlotAreaView();

    void disposeViewport() {
        releaseActiveTool();
        plotAreaView.dispose();
    }

    private final GerberEditorController gerberEditor = new GerberEditorController(plotAreaView,
            new GerberEditorController.Host() {
                @Override
                public void openToolPanel(String label, Node content) {
                    MainWindow.this.openToolPanel(label, content);
                }

                @Override
                public Node icon(String fileName, double size) {
                    return legacyIcon(fileName, size);
                }

                @Override
                public void closeToolPanel() {
                    MainWindow.this.closeToolPanel();
                }

                @Override
                public void setObjectVisible(TreeItem<String> item, boolean visible) {
                    MainWindow.this.setObjectVisible(item, visible);
                }

                @Override
                public String addEditedGerber(String name, GerberImage image) {
                    String uniqueName = uniqueDerivedName(name);
                    TreeItem<String> created = addGerberToProject(uniqueName, null, image);
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return uniqueName;
                }

                @Override
                public boolean generateEditedGerber(GerberEditSession.ApplyRequest request,
                        Consumer<GerberEditSession.ApplyResult> onSuccess, Runnable onFailure) {
                    return MainWindow.this.generateEditedGerber(request, onSuccess, onFailure);
                }

                @Override
                public void log(String message) {
                    appendConsole(message);
                }
            });

    private final GeometryEditorController geometryEditor = new GeometryEditorController(plotAreaView,
            new GeometryEditorController.Host() {
                @Override
                public void openToolPanel(String label, Node content) {
                    MainWindow.this.openToolPanel(label, content);
                }

                @Override
                public void closeToolPanel() {
                    MainWindow.this.closeToolPanel();
                }

                @Override
                public void showEditorToolbar(Node toolbar) {
                    PanelTooltips.install(toolbar, I18n.text("ui.73031cbbeabc"));
                    MainWindow.this.showGeometryEditorToolbar(toolbar);
                }

                @Override
                public void hideEditorToolbar() {
                    MainWindow.this.hideGeometryEditorToolbar();
                }

                @Override
                public Node icon(String fileName) {
                    return legacyIcon(fileName, 18);
                }

                @Override
                public void setObjectVisible(TreeItem<String> item, boolean visible) {
                    MainWindow.this.setObjectVisible(item, visible);
                }

                @Override
                public void apply(TreeItem<String> item, Geometry geometry, List<ToolGeometry> tools) {
                    GeometryEntry old = geometryByItem.get(item);
                    if (old == null) {
                        return;
                    }
                    geometryByItem.put(item, new GeometryEntry(old.sourceName(), old.units(), geometry,
                            old.strokeOnly(), tools, old.cncDefaults()));
                    plotAreaView.updateLayerGeometry(item, geometry);
                    showProperties(item);
                }

                @Override
                public boolean runOperation(GeometryEditSession.OperationRequest request,
                        Consumer<GeometryEditSession.OperationResult> onSuccess, Consumer<String> onFailure) {
                    return MainWindow.this.runGeometryOperation(request, onSuccess, onFailure);
                }

                @Override
                public void requestCoordinates(Coordinate cursor, String units, boolean allowInsert, Consumer<ArcCoordinateDialog.Choice> selected) {
                    new ArcCoordinateDialog(scene.getWindow(),cursor,units,allowInsert,
                            "rel".equals(AppPreferences.loadText("jump.reference"))).showAndWait().ifPresent(choice -> {
                        AppPreferences.saveText("jump.reference",choice.relative() ? "rel" : "abs");
                        Platform.runLater(() -> { plotAreaView.requestFocus(); selected.accept(choice); });
                    });
                }

                @Override
                public void movePlotCursor(Coordinate point) {
                    if (scene == null || scene.getWindow() == null || !scene.getWindow().isShowing()) return;
                    PlotCamera camera = plotAreaView.cameraSnapshot();
                    var screen = plotAreaView.localToScreen(camera.screenX(point.x),camera.screenY(point.y));
                    if (screen == null) return;
                    try { new javafx.scene.robot.Robot().mouseMove(screen); }
                    catch (RuntimeException unavailable) {
                        appendConsole(I18n.text("ui.a9b69a98914f"));
                    }
                }

                @Override
                public void log(String message) {
                    appendConsole(message);
                }
            });

    private final ExcellonEditorController excellonEditor = new ExcellonEditorController(plotAreaView,
            new ExcellonEditorController.Host() {
                @Override public void openToolPanel(String label, Node content) {
                    MainWindow.this.openToolPanel(label, content);
                }
                @Override public void closeToolPanel() { MainWindow.this.closeToolPanel(); }
                @Override public void showToolbar(Node toolbar) {
                    PanelTooltips.install(toolbar, I18n.text("ui.0e54aac8a039"));
                    topBars.getChildren().set(2, toolbar);
                    excellonEditorMenu.setVisible(true);
                }
                @Override public void hideToolbar() {
                    topBars.getChildren().set(2, regularToolsToolbar);
                    excellonEditorMenu.setVisible(false);
                }
                @Override public void setObjectVisible(TreeItem<String> item, boolean visible) {
                    MainWindow.this.setObjectVisible(item, visible);
                }
                @Override public boolean prepareArray(org.flatcam.cam.excellon.ExcellonArray.Parameters p,
                        Consumer<org.flatcam.cam.excellon.ExcellonArray.Prepared> done,Consumer<String> failed) {
                    return runExcellonArray(p,done,failed);
                }
                @Override public void apply(TreeItem<String> item, ExcellonImage image) {
                    excellonByItem.put(item, image);
                    plotAreaView.updateLayerGeometry(item, image.solidGeometry());
                    showProperties(item);
                }
                @Override public Node icon(String fileName) { return legacyIcon(fileName, 18); }
                @Override public void log(String message) { appendConsole(message); }
            });

    private final GCodeEditorController gcodeEditor = new GCodeEditorController(centerTabs,
            new GCodeEditorController.Host() {
                @Override
                public void openToolPanel(String label, Node content) {
                    MainWindow.this.openToolPanel(label, content);
                }

                @Override
                public void closeToolPanel() {
                    MainWindow.this.closeToolPanel();
                }

                @Override
                public boolean apply(TreeItem<String> item, String text, Runnable onSuccess,
                                     Consumer<String> onFailure) {
                    return MainWindow.this.applyGCodeEdit(item, text, onSuccess, onFailure);
                }

                @Override
                public boolean saveAs(String text, Consumer<String> onComplete) {
                    return MainWindow.this.saveGCodeDraftAs(text, onComplete);
                }

                @Override
                public void log(String message) {
                    appendConsole(message);
                }
            });

    private boolean generateEditedGerber(GerberEditSession.ApplyRequest request,
            Consumer<GerberEditSession.ApplyResult> onSuccess, Runnable onFailure) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return false;
        }
        beginJob(I18n.text("ui.3235d08ce5ca"));
        JobHandle<GerberEditSession.ApplyResult> handle = jobExecutor.submit(context ->
                request.generate(context::isCancelled,
                        fraction -> context.reportProgress(fraction, I18n.text("ui.3235d08ce5ca"))),
                (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;
        handle.completion().thenAccept(result -> Platform.runLater(() -> {
            onJobFinished();
            updateProgress(1);
            setStatus(I18n.text("ui.a465a6d19d33"), IDLE_COLOR);
            onSuccess.accept(result);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, I18n.text("ui.62a710d38219"));
                onJobFinished();
                onFailure.run();
            });
            return null;
        });
        return true;
    }

    private boolean runGeometryOperation(GeometryEditSession.OperationRequest request,
            Consumer<GeometryEditSession.OperationResult> onSuccess, Consumer<String> onFailure) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return false;
        }
        String message = I18n.text("ui.94394dd7b8e2") + request.operation().name().toLowerCase(java.util.Locale.ROOT) + "...";
        beginJob(message);
        JobHandle<GeometryEditSession.OperationResult> handle = jobExecutor.submit(context ->
                request.execute(context::isCancelled,
                        fraction -> context.reportProgress(fraction, message)),
                (fraction, progressMessage) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(progressMessage);
                }));
        runningJob = handle;
        handle.completion().thenAccept(result -> Platform.runLater(() -> {
            onJobFinished();
            updateProgress(1);
            setStatus(I18n.text("ui.b59b89b0c853"), IDLE_COLOR);
            onSuccess.accept(result);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, I18n.text("ui.83341c266009"));
                onJobFinished();
                Throwable cause = error.getCause() == null ? error : error.getCause();
                onFailure.accept(isCancellation(error) ? I18n.text("ui.f102bb211dda")
                        : I18n.text("ui.24a376d6ed1b") + cause.getMessage());
            });
            return null;
        });
        return true;
    }

    private boolean runExcellonArray(org.flatcam.cam.excellon.ExcellonArray.Parameters params,
            Consumer<org.flatcam.cam.excellon.ExcellonArray.Prepared> done,Consumer<String> failed) {
        if(runningJob!=null) return false;
        String message=I18n.text("ui.bd17b8448894"); beginJob(message);
        JobHandle<?>[] owner=new JobHandle<?>[1];
        var handle=jobExecutor.submit(context -> org.flatcam.cam.excellon.ExcellonArray.generate(params,context::isCancelled,
                fraction->context.reportProgress(fraction,message)),cncPreviewProgress(owner));
        owner[0]=handle; runningJob=handle;
        handle.completion().thenAccept(result->Platform.runLater(()-> {
            if(runningJob!=handle) return;
            onJobFinished(); updateProgress(1); setStatus(I18n.text("ui.3c01b31677fc"),IDLE_COLOR); done.accept(result);
        })).exceptionally(error-> { Platform.runLater(()-> {
            if(runningJob!=handle) return;
            reportJobError(error,I18n.text("ui.2287911f438e")); onJobFinished();
            Throwable cause=error.getCause()==null ? error : error.getCause();
            failed.accept(isCancellation(error) ? I18n.text("ui.064325826214") : I18n.text("ui.6239176f8487")+cause.getMessage());
        }); return null; });
        return true;
    }

    /** One UI update per percentage, owned by this job even if callbacks are queued late. */
    private org.flatcam.app.job.ProgressListener cncPreviewProgress(JobHandle<?>[] owner) {
        int[] lastPercent = {-1};
        return (fraction, message) -> {
            int percent = (int) Math.floor(fraction * 100);
            if (percent == lastPercent[0]) return;
            lastPercent[0] = percent;
            Platform.runLater(() -> {
                if (owner[0] == null || runningJob != owner[0] || owner[0].isCancelled()) return;
                updateProgress(fraction);
                statusLabel.setText(message);
            });
        };
    }

    private boolean applyGCodeEdit(TreeItem<String> item, String text, Runnable onSuccess,
                                   Consumer<String> onFailure) {
        CncJobEntry original = cncJobByItem.get(item);
        if (runningJob != null || original == null) {
            appendConsole(runningJob != null ? I18n.text("ui.df12b110e3bb")
                    : I18n.text("ui.982652d6caff"));
            return false;
        }
        long epoch = tclProjectEpoch;
        JobHandle<?>[] progressOwner = {null};
        beginJob(I18n.text("ui.e18608c6246a"));
        JobHandle<GCodeToolpathParser.Result> handle = jobExecutor.submit(context ->
                GCodeToolpathParser.parse(text, context::isCancelled,
                        fraction -> context.reportProgress(fraction, I18n.text("ui.e18608c6246a"))),
                cncPreviewProgress(progressOwner));
        runningJob = handle;
        progressOwner[0] = handle;
        handle.completion().thenAccept(parsed -> Platform.runLater(() -> {
            if (runningJob != handle) return;
            onJobFinished();
            if (handle.isCancelled()) {
                setStatus(I18n.text("ui.94ee72fe53b0"), IDLE_COLOR);
                onFailure.accept(I18n.text("ui.9007d30a0cd9"));
                return;
            }
            if (tclProjectEpoch != epoch || cncJobByItem.get(item) != original) {
                setStatus(I18n.text("ui.0c233ee4094f"), IDLE_COLOR);
                onFailure.accept(I18n.text("ui.62195c445fbc"));
                return;
            }
            updateCncJob(item, original, text, parsed);
            updateProgress(1);
            setStatus(I18n.text("ui.23b02b31bb39"), IDLE_COLOR);
            appendConsole(I18n.text("ui.11bf8245329f") + item.getValue() + I18n.text("ui.6ab454731796")
                    + (parsed.warning() == null ? "" : " " + parsed.warning()));
            onSuccess.run();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                if (runningJob != handle) return;
                reportJobError(error, I18n.text("ui.a7bdc32482c4"));
                onJobFinished();
                onFailure.accept(isCancellation(error) ? I18n.text("ui.9007d30a0cd9")
                        : I18n.text("ui.6cea211a24d7")
                                + (error.getCause() == null ? error : error.getCause()).getMessage());
            });
            return null;
        });
        return true;
    }

    private void updateCncJob(TreeItem<String> item, CncJobEntry previous, String text,
                              GCodeToolpathParser.Result parsed) {
        CncCutLayerKey cutKey = new CncCutLayerKey(item);
        CncTravelLayerKey travelKey = new CncTravelLayerKey(item);
        boolean hadPlot = previous.cutGeometry() != null || previous.travelGeometry() != null;
        boolean visible = !hadPlot || plotAreaView.isLayerVisible(cutKey) || plotAreaView.isLayerVisible(travelKey);
        var appearance=snapshotCncPlotSettings(item);
        double previewWidth = previewWidthFor(parsed);
        cncJobByItem.put(item, new CncJobEntry(previous.sourceName(), previous.outputFile(), text,
                parsed.travelGeometry(), parsed.cutGeometry(), parsed.travelCenterlines(),
                parsed.cutCenterlines(), previewWidth, parsed.stats()));
        // The edit may have renumbered or dropped tools; per-tool hiding no longer maps onto them.
        hiddenCncTools.remove(item);
        if (parsed.plotAvailable()) {
            setDisplayUnits(parsed.units());
        }
        plotAreaView.beginBatchUpdate();
        try {
            plotAreaView.removeLayer(cutKey);
            plotAreaView.removeLayer(travelKey);
            if (parsed.cutGeometry() != null && !parsed.cutGeometry().isEmpty()) {
                plotAreaView.putLayer(cutKey, PlotAreaView.LayerCategory.CNCJOB,
                        parsed.cutGeometry(), CNC_CUT_FILL, CNC_CUT_STROKE, false);
                plotAreaView.setLayerCenterlineLod(cutKey, parsed.cutCenterlines(), previewWidth, strokedPreview(parsed.stats()));
                plotAreaView.setLayerVisible(cutKey, visible);
            }
            if (parsed.travelGeometry() != null && !parsed.travelGeometry().isEmpty()) {
                plotAreaView.putLayer(travelKey, PlotAreaView.LayerCategory.CNCJOB,
                        parsed.travelGeometry(), CNC_TRAVEL_FILL, CNC_TRAVEL_STROKE, false);
                plotAreaView.setLayerCenterlineLod(travelKey, parsed.travelCenterlines(), previewWidth, strokedPreview(parsed.stats()));
                plotAreaView.setLayerVisible(travelKey, visible);
            }
        } finally {
            plotAreaView.endBatchUpdate();
        }
        applyCncPlotSettings(item,appearance,visible);
        refreshPlotSelectionOutline();
        if (projectTree.getSelectionModel().getSelectedItem() == item) {
            showProperties(item);
        }
        for (Tab openTab : centerTabs.getTabs()) {
            if ((openTab.getText().equals(I18n.text("ui.04ee0affc04e") + item.getValue())
                    || openTab.getText().equals(item.getValue()))
                    && openTab.getContent() instanceof CodeEditor viewer && !viewer.isEditable()) {
                viewer.setText(text);
            }
        }
    }

    private boolean saveGCodeDraftAs(String text, Consumer<String> onComplete) {
        String extension = GCodeToolpathParser.isIcpProgram(text) ? ".imf" : GCodeToolpathParser.isHpglProgram(text)
                ? ".plt" : GCodeToolpathParser.isRolandProgram(text) ? ".rml" : ".nc";
        return saveReviewedProgram(I18n.text("ui.506598c4212b"), "program_edit" + extension,
                () -> new PendingCncProgram(text, null,
                        I18n.text("ui.dd3a87f029e5"), path -> {}),
                () -> true, onComplete);
    }

    record PendingCncProgram(String text, GCodeToolpathParser.ToolpathStats stats, String warning, Consumer<Path> saved) { }
    private record ReviewedCncProgram(PendingCncProgram program, CncPreflight.Report report) { }
    private java.util.function.Predicate<CncPreflight.Report> cncExportDecision = report -> {
        var decision = new CncReviewDialog(this.scene.getWindow(), this.currentTheme, report).showAndWait().orElse(null);
        return decision != null && decision.proceed();
    };
    private java.util.function.BiFunction<String,String,File> cncExportDestination = (name, text) -> {
        FileChooser chooser = new FileChooser(); chooser.setTitle(I18n.text("ui.995308a91ca5"));
        chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("ui.dc244cbb1ee5"),
                "*.nc", "*.gcode", "*.tap", "*.ngc", "*.imf", "*.plt", "*.hpgl", "*.hpg", "*.rml", "*.prn"));
        File initial = new File(AppPreferences.loadLastCamDirectory("."));
        if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
        return chooser.showSaveDialog(this.scene.getWindow());
    };
    private Consumer<Path> cncExportRemember = path -> AppPreferences.saveLastCamDirectory(path.getParent().toString());

    /** Generate/analyse on a worker; review on FX; revalidate before publishing the exact reviewed text. */
    boolean saveReviewedProgram(String name, String filename, java.util.function.Supplier<PendingCncProgram> prepare,
            BooleanSupplier valid, Consumer<String> complete) {
        if (runningJob != null || terminalPanel != null && terminalPanel.isBusy()) {
            appendConsole(I18n.text("ui.ac7b76696bc0")); return false;
        }
        long epoch = tclProjectEpoch;
        BooleanSupplier current = () -> tclProjectEpoch == epoch && valid.getAsBoolean();
        beginJob(I18n.text("ui.26c949209791"));
        var analysis = jobExecutor.submit(context -> TclExecution.run(context, () -> {
            TclExecution.cancellation().throwIfCancellationRequested();
            PendingCncProgram program = prepare.get();
            return new ReviewedCncProgram(program, CncReviewedFile.review(name, program.text(), program.stats(),
                    program.warning(), context::isCancelled, f -> context.reportProgress(f, I18n.text("ui.d6961c363f02"))));
        }), (fraction,message) -> Platform.runLater(() -> { updateProgress(fraction); statusLabel.setText(message); }));
        runningJob = analysis;
        analysis.completion().whenComplete((reviewed,error) -> Platform.runLater(() -> {
            if (runningJob != analysis) { complete.accept(I18n.text("ui.e77b02c77ec2")); return; }
            onJobFinished();
            if (error != null) { reportJobError(error,I18n.text("ui.d24b102a0c86")); complete.accept(I18n.text("ui.4cec24d0c5ab")); return; }
            updateProgress(1); setStatus(I18n.text("ui.f4a886782bea"), IDLE_COLOR);
            try {
                if (!current.getAsBoolean()) { complete.accept(I18n.text("ui.5ee605dbe9f9")); return; }
                if (!cncExportDecision.test(reviewed.report()) || !reviewed.report().canContinue()) {
                    setStatus(I18n.text("ui.2d89cfee4d37"),IDLE_COLOR); complete.accept(I18n.text("ui.27d1a7c672e9")); return;
                }
                if (!current.getAsBoolean()) { complete.accept(I18n.text("ui.5ee605dbe9f9")); return; }
                File selected = cncExportDestination.apply(filename, reviewed.program().text());
                if (selected == null) { setStatus(I18n.text("ui.2d89cfee4d37"),IDLE_COLOR); complete.accept(I18n.text("ui.b7a0fe342c6b")); return; }
                if (!current.getAsBoolean() || runningJob != null || terminalPanel != null && terminalPanel.isBusy()) {
                    complete.accept(I18n.text("ui.15cef3c93b56")); return;
                }
                beginJob(I18n.text("ui.765b55fdb744"));
                var saving = jobExecutor.submit(context -> TclExecution.run(context, () ->
                        CncReviewedFile.write(reviewed.program().text(), selected.toPath(), context::isCancelled, () ->
                                TclExecution.onFx(() -> {
                                    if (!current.getAsBoolean()) throw new IllegalStateException(I18n.text("ui.84fed0b04b6d"));
                                    return null;
                                }))), null);
                runningJob = saving;
                saving.completion().whenComplete((path,failure) -> Platform.runLater(() -> {
                    if (runningJob != saving) { complete.accept(I18n.text("ui.b1154fb9d439")); return; }
                    onJobFinished();
                    if (failure != null) { reportJobError(failure,I18n.text("ui.692b6e23b2f5")); complete.accept(I18n.text("ui.52ada1852cfe")); return; }
                    try { cncExportRemember.accept(path); }
                    catch (RuntimeException preferenceFailure) { appendConsole(I18n.text("ui.fdf372a19a3c") + preferenceFailure.getMessage()); }
                    appendConsole(I18n.text("ui.f84cb83978b5") + path);
                    updateProgress(1); setStatus(I18n.text("ui.98c459e50406"),IDLE_COLOR);
                    // The file has been published. A changed source must not publish a stale preview into the project.
                    try { if (current.getAsBoolean()) reviewed.program().saved().accept(path); }
                    catch (RuntimeException previewFailure) { appendConsole(I18n.text("ui.e6e5a44fbf25") + previewFailure.getMessage()); }
                    complete.accept(I18n.text("ui.3d589d922af8") + path + ".");
                }));
            } catch (RuntimeException failure) {
                reportJobError(failure,I18n.text("ui.c72be5121787")); complete.accept(I18n.text("ui.59927971fe40"));
            }
        }));
        return true;
    }

    private Scene scene;
    /** Animated tooltips for the whole window (see {@link FluidTooltips}). */
    private FluidTooltips fluidTooltips;
    private SplitPane horizontalSplit;
    private SplitPane verticalSplit;
    private AnimatedSplitPanel sidebarAnimation;
    private AnimatedSplitPanel consoleAnimation;
    private SplitPane.Divider observedConsoleDivider;
    private TreeView<String> projectTree;
    private TreeItem<String> requestedTreeRename;
    private TabPane leftTabs;
    private Tab projectTab;
    private Tab propertiesTab;
    private Tab toolTab;
    private TreeItem<String> gerbersNode;
    private TreeItem<String> excellonNode;
    private TreeItem<String> geometryNode;
    private TreeItem<String> cncJobsNode;
    private VBox bottomPanel;
    private VBox topBars;
    private ToolBar regularToolsToolbar;
    private Menu geometryEditorMenu;
    private Menu excellonEditorMenu;
    private double dividerBeforeConsoleCollapse = 0.75;
    /** Set by MainApp (initially, and again after each DPI-rescale Stage recreation) - see setCurrentScreenId. */
    private String currentScreenId = "default";
    private boolean sidebarDividerDragging;
    private boolean sidebarRestoreQueued;
    private boolean sidebarCollapsed;
    private ToggleButton sidebarToggle;
    private PlotStatusControls statusControls;
    private boolean consoleCollapsed = !AppPreferences.loadConsoleOpen(true);
    private ThemeOption currentTheme = AppPreferences.loadTheme();
    /** Saved projects restore their own presentation, independently of defaults for newly added objects. */
    private boolean restoringProject;
    private final BooleanProperty darkIcons = new SimpleBooleanProperty(currentTheme.isDark());
    private JobHandle<?> runningJob;
    private ContextMenu plotContextMenu;
    private ContextMenu projectContextMenu;
    private final PlotMoveHistory<TreeItem<String>> plotMoveHistory = new PlotMoveHistory<>();
    private boolean applyingPlotMove;

    MainWindow(JobExecutor jobExecutor) {
        this(jobExecutor, AppPreferences.recentFiles());
    }

    MainWindow(JobExecutor jobExecutor, RecentFiles recentFiles) {
        this.jobExecutor = jobExecutor;
        this.recentFiles = recentFiles;
        setDisplayUnits(UiNumbers.initialUnits());
        centerTabs.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            while (change.next()) for (Tab removed : change.getRemoved())
                if (removed.getContent() instanceof CodeEditor code) code.close();
                else if (removed.getContent() instanceof ScriptEditorPanel script) script.close();
        });
    }

    Scene createScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");
        regularToolsToolbar = buildToolsToolBar();
        topBars = new VBox(buildMenuRow(), buildToolBar(), regularToolsToolbar);
        root.setTop(topBars);
        root.setCenter(buildMainSplit());
        root.setBottom(buildStatusBar());

        scene = new Scene(root);
        fluidTooltips = new FluidTooltips(scene, () -> currentTheme);
        root.lookupAll(".menu-bar").forEach(node -> {
            if (node instanceof MenuBar bar) {
                bar.getMenus().forEach(fluidTooltips::attachMenu);
            }
        });
        configurePlotInteractions();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Tab selectedTab = centerTabs.getSelectionModel().getSelectedItem();
            // Database shortcuts must not move/delete shapes or toggle the hidden plot.
            if (selectedTab != null && "tools-database-tab".equals(selectedTab.getId())) return;
            if (pendingPointPick != null && event.getCode() == KeyCode.ESCAPE) {
                cancelPointPick();
                event.consume();
                return;
            }
            if (plotAreaView.hasStepSelection() && !plotAreaView.isPlacementActive()
                    && (plotAreaView.isFocused() || plotAreaView.isHover())
                    && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && !isTextInputTarget(event.getTarget())
                    && projectTree.getEditingItem() == null) {
                // Walking a CNC Job's route: the arrows work as soon as the pointer is over the plot,
                // even if keyboard focus was left on the project tree or a sidebar control.
                boolean handled = true;
                switch (event.getCode()) {
                    case LEFT, UP -> plotAreaView.stepBy(-1);
                    case RIGHT, DOWN -> plotAreaView.stepBy(1);
                    case ESCAPE -> plotAreaView.clearStepSelection();
                    case SPACE -> plotAreaView.toggleWalk();
                    default -> handled = false;
                }
                if (handled) {
                    event.consume();
                    return;
                }
            }
            if (event.getCode() == KeyCode.ESCAPE && (geometryEditor.cancelEraserFromShortcut() || geometryEditor.cancelRotationFromShortcut() || excellonEditor.cancelArrayFromShortcut())) {
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && plotAreaView.isPlacementActive()) {
                plotAreaView.cancelPlacement();
                event.consume();
            } else if ((geometryEditor.isActive() || excellonEditor.isActive()) && event.getCode() == KeyCode.DELETE
                    && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && !isTextInputTarget(event.getTarget())) {
                // While Geometry is being edited, Delete targets shapes even if a sidebar button
                // has focus. Consume it with no selection too: the project tree must not remove
                // the entire object while the editor is open.
                if (excellonEditor.isActive()) excellonEditor.deleteFromShortcut();
                else geometryEditor.deleteFromShortcut();
                event.consume();
            } else if (geometryEditor.isActive() && event.getCode()==KeyCode.J && !event.isControlDown() && !event.isAltDown()
                    && !event.isMetaDown() && !event.isShiftDown() && !isTextInputTarget(event.getTarget())) {
                geometryEditor.requestCoordinates(); event.consume();
            } else if (geometryEditor.isArcActive() && !event.isControlDown() && !event.isAltDown()
                    && !event.isMetaDown() && !event.isShiftDown() && !isTextInputTarget(event.getTarget())
                    && geometryEditor.handleArcKey(event.getCode())) {
                event.consume();
            } else if (plotAreaView.isTrackPlacementActive() && !event.isControlDown()
                    && !event.isAltDown() && !event.isMetaDown() && !event.isShiftDown()
                    && !isTextInputTarget(event.getTarget())) {
                boolean handled = switch (event.getCode()) {
                    case ENTER -> plotAreaView.finishEditorTrackPlacement();
                    case BACK_SPACE -> plotAreaView.backtrackEditorTrackPlacement();
                    case T -> plotAreaView.cycleEditorTrackBendMode(false);
                    case R -> plotAreaView.cycleEditorTrackBendMode(true);
                    case G -> {
                        statusControls.toggleGrid();
                        yield true;
                    }
                    default -> false;
                };
                if (handled) {
                    event.consume();
                }
            } else if (event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && (event.getCode() == KeyCode.S || event.getCode() == KeyCode.N && !event.isShiftDown())
                    && !gcodeEditor.isActive() && !plotAreaView.isPlacementActive()) {
                if (event.getCode() == KeyCode.N) newProject();
                else if (event.isShiftDown()) saveProjectAs();
                else saveProject();
                event.consume();
            } else if ((event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.ADD
                    || event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT)
                    && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && !isTextInputTarget(event.getTarget()) && !plotAreaView.isPlacementActive()
                    && projectTree.getEditingItem() == null) {
                boolean in = event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.ADD;
                plotAreaView.zoomBy(in ? MENU_ZOOM_RATIO : 1 / MENU_ZOOM_RATIO);
                event.consume();
            } else if (event.getCode() == KeyCode.A && event.isControlDown() && !event.isAltDown()
                    && !event.isMetaDown() && !event.isShiftDown() && !isTextInputTarget(event.getTarget())
                    && !anyObjectEditorActive() && !plotAreaView.isEditorActive()
                    && projectTree.getEditingItem() == null) {
                selectAllObjects();
                event.consume();
            } else if (event.getCode() == KeyCode.M && !event.isAltDown() && !event.isMetaDown()
                    && (event.isControlDown() != event.isShiftDown())
                    && !isTextInputTarget(event.getTarget()) && !plotAreaView.isPlacementActive()
                    && !anyObjectEditorActive() && !plotAreaView.isEditorActive()) {
                if (event.isControlDown()) openMeasureTool(); else openMinimumDistanceTool();
                event.consume();
            } else if ((event.getCode() == KeyCode.O || event.getCode() == KeyCode.J)
                    && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && !isTextInputTarget(event.getTarget()) && !plotAreaView.isPlacementActive()
                    && !anyObjectEditorActive() && !plotAreaView.isEditorActive()
                    && projectTree.getEditingItem() == null) {
                if (event.getCode() == KeyCode.J) { if (event.isShiftDown()) askLocateInObject(); else askJumpToLocation(); }
                else if (event.isShiftDown()) moveSelectionToOrigin();
                else beginSetOrigin();
                event.consume();
            } else if (event.getCode() == KeyCode.G && !event.isControlDown()
                    && !event.isAltDown() && !event.isMetaDown() && !event.isShiftDown()
                    && !isTextInputTarget(event.getTarget())) {
                statusControls.toggleGrid();
                event.consume();
            } else if (event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && !event.isShiftDown() && (!plotAreaView.isPlacementActive() || geometryEditor.isEraserActive())
                    && projectTree.getEditingItem() == null
                    && !isTextInputTarget(event.getTarget())) {
                boolean handled = false;
                if (event.getCode() == KeyCode.Z) {
                    handled = excellonEditor.isActive() ? excellonEditor.undoFromShortcut()
                            : geometryEditor.isActive() ? geometryEditor.undoFromShortcut()
                            : plotAreaView.isEditorActive() ? gerberEditor.undoFromShortcut() : undoPlotMove();
                } else if (event.getCode() == KeyCode.Y) {
                    handled = excellonEditor.isActive() ? excellonEditor.redoFromShortcut()
                            : geometryEditor.isActive() ? geometryEditor.redoFromShortcut()
                            : plotAreaView.isEditorActive() ? gerberEditor.redoFromShortcut() : redoPlotMove();
                }
                if (handled) {
                    event.consume();
                }
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (toolsDatabasePanel != null) toolsDatabasePanel.hideContextMenu();
            // Context menus have their own popup scene. A click anywhere in the
            // main window should dismiss them before the target handles it.
            if (projectContextMenu != null && projectContextMenu.isShowing()) {
                projectContextMenu.hide();
            }
            if (plotContextMenu != null && plotContextMenu.isShowing()) {
                plotContextMenu.hide();
            }
            sidebarDividerDragging = isSidebarDividerTarget(event.getTarget());
            if (sidebarDividerDragging && sidebarAnimation.isAnimating()) sidebarAnimation.finish();
            if (isDividerTarget(event.getTarget(), verticalSplit) && consoleAnimation.isAnimating()) consoleAnimation.finish();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (sidebarDividerDragging) {
                sidebarDividerDragging = false;
                saveSidebarDividerPosition();
            }
        });
        currentTheme.applyTo(scene);
        plotAreaView.applyTheme(currentTheme);
        if(!projectChanges.initialized() && allTclItems().isEmpty()) projectChanges.saved(projectStateStamp());
        return scene;
    }

    static boolean isTextInputTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TextInputControl || current instanceof org.fxmisc.richtext.GenericStyledArea<?, ?, ?>) {
                return true;
            }
        }
        return false;
    }

    /** Called by MainApp on window close and by the console divider listener. */
    void saveSplitPositions() {
        if (sidebarDividerDragging) {
            saveSidebarDividerPosition();
        }
        if (consoleCollapsed || panelsAnimating()) {
            return; // the collapsed (~1.0) position is not a real layout preference - see toggleConsole().
        }
        AppPreferences.saveSplitVertical(verticalSplit.getDividerPositions()[0]);
    }

    private void saveSidebarDividerPosition() {
        if (sidebarCollapsed || (sidebarAnimation != null && sidebarAnimation.isAnimating())
                || horizontalSplit.getDividers().isEmpty()) {
            return;
        }
        double position = horizontalSplit.getDividerPositions()[0];
        if (Double.isFinite(position) && position > 0 && position < 1) {
            AppPreferences.saveSplitHorizontalForScreen(currentScreenId, position);
        }
    }

    private boolean isSidebarDividerTarget(Object target) {
        return isDividerTarget(target, horizontalSplit);
    }

    private static boolean isDividerTarget(Object target, SplitPane split) {
        if (!(target instanceof Node node)) {
            return false;
        }
        boolean divider = false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof SplitPane) return divider && current == split;
            divider |= current.getStyleClass().contains("split-pane-divider");
        }
        return false;
    }

    private boolean panelsAnimating() {
        return (sidebarAnimation != null && sidebarAnimation.isAnimating())
                || (consoleAnimation != null && consoleAnimation.isAnimating());
    }

    /**
     * Called by MainApp once initially and again every time it recreates the
     * Stage on a different screen (its DPI-rescale workaround) - reapplies
     * THIS screen's own remembered sidebar width. Kept separate per screen,
     * unlike the shared vertical (console) divider, because this app's two
     * monitors can have very different resolutions/DPI, so one fraction that
     * looked right on one looked wrong-sized on the other; the user asked for
     * each monitor to keep its own.
     */
    void setCurrentScreenId(String screenId) {
        if (screenId.equals(currentScreenId)) {
            return;
        }
        currentScreenId = screenId;
        if (horizontalSplit == null) {
            return;
        }
        if (sidebarAnimation != null) sidebarAnimation.finish();
        if (consoleAnimation != null) consoleAnimation.finish();
        scheduleSidebarRestore();
    }

    String currentScreenId() {
        return currentScreenId;
    }

    private void scheduleSidebarRestore() {
        if (sidebarRestoreQueued) {
            return;
        }
        sidebarRestoreQueued = true;
        Platform.runLater(() -> {
            sidebarRestoreQueued = false;
            if (sidebarCollapsed || sidebarDividerDragging || panelsAnimating() || horizontalSplit.getWidth() <= 0) {
                return;
            }
            horizontalSplit.setDividerPositions(
                    AppPreferences.loadSplitHorizontalForScreen(currentScreenId, AppPreferences.loadSplitHorizontal(0.22)));
        });
    }

    /**
     * Collapses the resizable progress/console panel, or restores it. Moving the
     * panel is clipped while sliding and removed at the end, so its TextArea's
     * intrinsic min-height cannot leave a sliver visible. Animation positions
     * are never saved as the user's preferred expanded height.
     */
    private void toggleConsole(boolean show) {
        if (show == !consoleCollapsed) return;
        if (!show && !consoleAnimation.isAnimating()) {
            dividerBeforeConsoleCollapse = verticalSplit.getDividerPositions()[0];
        }
        consoleCollapsed = !show;
        consoleAnimation.setVisible(show);
        updateConsoleProgressVisibility();
        AppPreferences.saveConsoleOpen(show);
    }

    private void updateConsoleProgressVisibility() {
        compactJobProgress.setVisible(consoleCollapsed || (consoleAnimation != null && consoleAnimation.isAnimating()));
    }

    /** A disabled entry is an explicit roadmap marker, never a no-op click. */
    private MenuItem chromeItem(String label, String icon, Runnable action) {
        MenuItem item = new MenuItem(label);
        if (icon != null) {
            setLegacyMenuIcon(item, icon);
        }
        item.setDisable(action == null);
        if (action == null) {
            item.getStyleClass().add("planned-command");
        }
        if (action != null) {
            item.setOnAction(event -> action.run());
        }
        CommandHelpCatalog.apply(item);
        return item;
    }

    /** The Python menu_toggle_nb action hides the notebook and gives its space to the plot. */
    private void toggleSidebar() {
        setSidebarVisible(sidebarCollapsed);
    }

    private void setSidebarVisible(boolean visible) {
        if (visible == !sidebarCollapsed) {
            return;
        }
        if (!visible) saveSidebarDividerPosition();
        sidebarCollapsed = !visible;
        sidebarAnimation.setVisible(visible);
        sidebarToggle.setSelected(visible);
    }

    private MenuItem plannedItem(String label, String icon) {
        return chromeItem(label, icon, null);
    }

    private HBox buildMenuRow() {
        sidebarToggle = new ToggleButton(null, legacyIcon("notebook32.png", 16));
        sidebarToggle.setTooltip(new Tooltip(I18n.text("ui.baa81a88f254")));
        sidebarToggle.setAccessibleText(I18n.text("ui.b3ec75f86d9c"));
        sidebarToggle.getStyleClass().add("sidebar-toggle");
        sidebarToggle.setSelected(true);
        sidebarToggle.setOnAction(event -> toggleSidebar());

        MenuBar menuBar = buildMenuBar();
        menuBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(menuBar, Priority.ALWAYS);
        HBox row = new HBox(sidebarToggle, menuBar);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("menu-bar-row");
        return row;
    }

    private Button chromeButton(String label, String icon, Runnable action) {
        Button button = new Button(null, legacyIcon(icon, 18));
        button.setTooltip(new Tooltip(label + (action == null ? I18n.text("ui.af814f12eeff") : "")));
        String commandHelp = CommandHelpCatalog.help(label);
        if (action == null) commandHelp = I18n.text("ui.4e622683282a");
        if (commandHelp != null) ToolDescriptions.apply(button, label, commandHelp);
        button.setDisable(action == null);
        if (action == null) {
            button.getStyleClass().add("planned-command");
        }
        if (action != null) {
            button.setOnAction(event -> action.run());
        }
        return button;
    }

    private List<TreeItem<String>> selectedObjects() {
        return projectTree.getSelectionModel().getSelectedItems().stream()
                .filter(this::isProjectObject).distinct().toList();
    }

    private void editSelectedGerber() {
        if (gcodeEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive()) {
            appendConsole(I18n.text("ui.e613b5675fd3"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage image = gerberByItem.get(item);
        if (image == null) {
            appendConsole(I18n.text("ui.de0d39308315"));
        } else {
            gerberEditor.start(item, image);
        }
    }

    private void editSelectedObject() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (cncJobByItem.containsKey(item)) {
            editSelectedGCode();
        } else if (geometryByItem.containsKey(item)) {
            editSelectedGeometry();
        } else if (excellonByItem.containsKey(item)) {
            editSelectedExcellon();
        } else {
            editSelectedGerber();
        }
    }

    private void editSelectedExcellon() {
        if (gerberEditor.isActive() || geometryEditor.isActive() || gcodeEditor.isActive()) {
            appendConsole(I18n.text("ui.6d917ff27aff"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        ExcellonImage image = excellonByItem.get(item);
        if (image == null) {
            appendConsole(I18n.text("ui.4c0e026548e6"));
            return;
        }
        excellonEditor.start(item, image);
    }

    private void editSelectedGeometry() {
        if (gerberEditor.isActive() || gcodeEditor.isActive() || excellonEditor.isActive()) {
            appendConsole(I18n.text("ui.cbc0c8b47d95"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GeometryEntry entry = geometryByItem.get(item);
        if (entry == null) {
            appendConsole(I18n.text("ui.2e0443892924"));
            return;
        }
        geometryEditor.start(item, entry.geometry(), entry.tools(), entry.strokeOnly(), entry.units());
    }

    private void editSelectedGCode() {
        if (gerberEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive()) {
            appendConsole(I18n.text("ui.a3b87351c688"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        CncJobEntry entry = cncJobByItem.get(item);
        if (entry == null) {
            appendConsole(I18n.text("ui.64e056852205"));
            return;
        }
        gcodeEditor.start(item, entry.gcode());
    }

    private void saveAndCloseEditor() {
        if (gcodeEditor.isActive()) {
            gcodeEditor.saveAndClose();
        } else if (geometryEditor.isActive()) {
            geometryEditor.apply();
        } else if (excellonEditor.isActive()) {
            excellonEditor.apply();
        } else if (gerberEditor.isActive()) {
            gerberEditor.saveAndClose();
        } else {
            appendConsole(I18n.text("ui.506fafe44359"));
        }
    }

    /** Edit > Join Objects > Gerber(s) -> Gerber: one new "Combo_Gerber" from the selected Gerbers. */
    private void joinSelectedGerbers() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() < 2) {
            appendConsole(I18n.text("ui.05b08f4d5acd") + selected.size());
            return;
        }
        List<GerberImage> images = new ArrayList<>();
        for (TreeItem<String> item : selected) {
            GerberImage image = gerberByItem.get(item);
            if (image == null) {
                appendConsole(I18n.text("ui.3013a8627750"));
                return;
            }
            images.add(image);
        }
        try {
            GerberImage joined = GerberJoin.join(images);
            TreeItem<String> created = addGerberToProject(uniqueDerivedName("Combo_Gerber"), null, joined);
            selectProjectItem(created);
            plotAreaView.fitToLayer(created);
            appendConsole(I18n.text("ui.6ee437661092") + created.getValue() + " (" + images.size() + I18n.text("ui.3cdae7796b26")
                    + (joined.shapes().isEmpty() ? I18n.text("ui.0aeb5eebb038") : "."));
        } catch (IllegalArgumentException failed) {
            appendConsole(I18n.text("ui.2ad7a609d2bf") + failed.getMessage());
        }
    }

    /** Edit > Join Objects > Excellon(s) -> Excellon: one new "Combo_Excellon"; same-diameter tools are fused. */
    private void joinSelectedExcellons() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() < 2) {
            appendConsole(I18n.text("ui.05b08f4d5acd") + selected.size());
            return;
        }
        List<ExcellonImage> images = new ArrayList<>();
        for (TreeItem<String> item : selected) {
            ExcellonImage image = excellonByItem.get(item);
            if (image == null) {
                appendConsole(I18n.text("ui.12026d5196f8"));
                return;
            }
            images.add(image);
        }
        try {
            ExcellonImage joined = ExcellonJoin.join(images, true);
            TreeItem<String> created = addExcellonToProject(uniqueDerivedName("Combo_Excellon"), null, joined);
            selectProjectItem(created);
            plotAreaView.fitToLayer(created);
            appendConsole(I18n.text("ui.f4c7b6d72bc5") + created.getValue() + " (" + joined.totalDrills() + I18n.text("ui.0de52914c0ab")
                    + joined.totalSlots() + " slots, " + joined.toolDiameters().size() + I18n.text("ui.9aa367e06483"));
        } catch (IllegalArgumentException failed) {
            appendConsole(I18n.text("ui.2ad7a609d2bf") + failed.getMessage());
        }
    }

    /** Edit > Join Objects > Geo/Gerber/Exc -> Geo: one new Geometry ("Combo_SingleGeo" or "Combo_MultiGeo"). */
    private void joinSelectedToGeometry() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() < 2) {
            appendConsole(I18n.text("ui.05b08f4d5acd") + selected.size());
            return;
        }
        List<GeometryJoin.Source> sources = new ArrayList<>();
        for (TreeItem<String> item : selected) {
            GerberImage gerber = gerberByItem.get(item);
            ExcellonImage excellon = excellonByItem.get(item);
            GeometryEntry geometry = geometryByItem.get(item);
            if (gerber != null) {
                sources.add(new GeometryJoin.Source(gerber.units(), gerber.solidGeometry(), false, List.of()));
            } else if (excellon != null) {
                sources.add(new GeometryJoin.Source(excellon.units(), excellon.solidGeometry(), false, List.of()));
            } else if (geometry != null) {
                sources.add(new GeometryJoin.Source(geometry.units(), geometry.geometry(), geometry.strokeOnly(),
                        geometry.tools()));
            } else {
                appendConsole(I18n.text("ui.d9f31bd782a1"));
                return;
            }
        }
        try {
            GeometryJoin.Joined joined = GeometryJoin.join(sources, true);
            String name = uniqueDerivedName(joined.tools().isEmpty() ? "Combo_SingleGeo" : "Combo_MultiGeo");
            TreeItem<String> created = addGeometryToProject(name, selected.get(0).getValue(), sources.get(0).units(),
                    joined.geometry(), joined.strokeOnly(), joined.tools());
            selectProjectItem(created);
            plotAreaView.fitToLayer(created);
            appendConsole(I18n.text("ui.0077a78914c7") + created.getValue() + " (" + selected.size() + I18n.text("ui.2a2acc2ff337")
                    + (joined.tools().isEmpty() ? "" : ", " + joined.tools().size() + I18n.text("ui.ee5aa162eac7")) + ").");
        } catch (IllegalArgumentException failed) {
            appendConsole(I18n.text("ui.2ad7a609d2bf") + failed.getMessage());
        }
    }

    /** Edit > Conversion > Any to Geometry: each selected Gerber, Excellon or Geometry becomes a new "_conv" Geometry. */
    private void convertSelectedToGeometry() {
        convertSelected("Geometry", item -> {
            GerberImage gerber = gerberByItem.get(item);
            ExcellonImage excellon = excellonByItem.get(item);
            GeometryEntry entry = geometryByItem.get(item);
            if (gerber != null) {
                return addGeometryToProject(uniqueDerivedName(item.getValue() + "_conv"), item.getValue(),
                        gerber.units(), ObjectConversion.solidToGeometry(gerber.solidGeometry()), false);
            }
            if (excellon != null) {
                return addGeometryToProject(uniqueDerivedName(item.getValue() + "_conv"), item.getValue(),
                        excellon.units(), ObjectConversion.solidToGeometry(excellon.solidGeometry()), false);
            }
            if (entry != null) {
                return addGeometryToProject(uniqueDerivedName(item.getValue() + "_conv"), item.getValue(),
                        entry.units(), entry.geometry(), entry.strokeOnly(), entry.tools(), entry.cncDefaults());
            }
            return null;
        });
    }

    /** Edit > Conversion > Any to Gerber: from an Excellon (flashes and strokes) or a Geometry (regions and strokes). */
    private void convertSelectedToGerber() {
        convertSelected("Gerber", item -> {
            ExcellonImage excellon = excellonByItem.get(item);
            GeometryEntry entry = geometryByItem.get(item);
            if (excellon != null) {
                return addGerberToProject(uniqueDerivedName(item.getValue() + "_conv"), null,
                        ObjectConversion.excellonToGerber(excellon));
            }
            if (entry != null) {
                return addGerberToProject(uniqueDerivedName(item.getValue() + "_conv"), null,
                        ObjectConversion.geometryToGerber(entry.units(), entry.geometry(), entry.tools()));
            }
            return null;
        });
    }

    /** Edit > Conversion > Any to Excellon: from a Geometry (one drill per closed shape) or a Gerber (flashes, strokes). */
    private void convertSelectedToExcellon() {
        convertSelected("Excellon", item -> {
            GerberImage gerber = gerberByItem.get(item);
            GeometryEntry entry = geometryByItem.get(item);
            if (gerber != null) {
                return addExcellonToProject(uniqueDerivedName(item.getValue() + "_conv"), null,
                        ObjectConversion.gerberToExcellon(gerber));
            }
            if (entry != null) {
                return addExcellonToProject(uniqueDerivedName(item.getValue() + "_conv"), null,
                        ObjectConversion.geometryToExcellon(entry.units(), entry.geometry()));
            }
            return null;
        });
    }

    private interface Conversion {
        /** Creates the converted object, or returns null when the kind of object is not accepted. */
        TreeItem<String> convert(TreeItem<String> item);
    }

    private void convertSelected(String target, Conversion conversion) {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.isEmpty()) {
            appendConsole(I18n.text("ui.32a4794184b7"));
            return;
        }
        TreeItem<String> last = null;
        for (TreeItem<String> item : selected) {
            try {
                TreeItem<String> created = conversion.convert(item);
                if (created == null) {
                    appendConsole(item.getValue() + I18n.text("ui.3c3241f3a3ed") + target + ".");
                    continue;
                }
                appendConsole(I18n.text("ui.e850a9ee398c") + target + ": " + created.getValue() + ".");
                last = created;
            } catch (IllegalArgumentException failed) {
                appendConsole(item.getValue() + ": " + failed.getMessage());
            }
        }
        if (last != null) {
            selectProjectItem(last);
            plotAreaView.fitToLayer(last);
        }
    }

    /** Edit > Conversion > Single to Multi-Geo: the geometry goes under one tool of a diameter asked for. */
    private void convertSingleToMultiGeometry() {
        Double diameter = null;
        int converted = 0;
        for (TreeItem<String> item : selectedObjects()) {
            GeometryEntry entry = geometryByItem.get(item);
            if (entry == null || !entry.tools().isEmpty()) {
                continue;
            }
            if (diameter == null) {
                diameter = promptNumber("Single → Multi-Geometry", I18n.text("ui.4c6e267a7370"), 0.1);
                if (diameter == null) {
                    return;
                }
            }
            try {
                List<ToolGeometry> tools = ObjectConversion.singleToMulti(entry.geometry(), diameter);
                geometryByItem.put(item, new GeometryEntry(entry.sourceName(), entry.units(), entry.geometry(),
                        entry.strokeOnly(), tools, entry.cncDefaults()));
                converted++;
            } catch (IllegalArgumentException failed) {
                appendConsole(item.getValue() + ": " + failed.getMessage());
            }
        }
        finishGeometryConversion(converted, "Single-Geometry", "MultiGeo");
    }

    /** Edit > Conversion > Multi to Single-Geo: every tool's geometry joined; the tool information is dropped. */
    private void convertMultiToSingleGeometry() {
        int converted = 0;
        for (TreeItem<String> item : selectedObjects()) {
            GeometryEntry entry = geometryByItem.get(item);
            if (entry == null || entry.tools().isEmpty()) {
                continue;
            }
            Geometry joined = ObjectConversion.multiToSingle(entry.tools());
            geometryByItem.put(item, new GeometryEntry(entry.sourceName(), entry.units(), joined, entry.strokeOnly(),
                    List.of(), entry.cncDefaults()));
            plotAreaView.updateLayerGeometry(item, joined);
            converted++;
        }
        finishGeometryConversion(converted, "MultiGeo", "SingleGeo");
    }

    private void finishGeometryConversion(int converted, String from, String to) {
        if (converted == 0) {
            appendConsole(I18n.text("ui.c8d67608f477") + from + I18n.text("ui.57cc47cd5e51"));
            return;
        }
        appendConsole(converted + I18n.text("ui.aeb3369e5452") + to + ".");
        TreeItem<String> current = projectTree.getSelectionModel().getSelectedItem();
        if (current != null) {
            showProperties(current);
        }
        refreshPlotSelectionOutline();
    }

    /** A small modal prompt for one positive number; null if cancelled or invalid. */
    private Double promptNumber(String title, String contentText, double defaultValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(defaultValue));
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(contentText);
        dialog.initOwner(scene.getWindow());
        var result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(result.get().trim().replace(',', '.'));
            if (value > 0 && Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the message
        }
        appendConsole(title + I18n.text("ui.09ac227cd52b"));
        return null;
    }

    /** Edit > Conversion > Outline to Area: a filled Geometry from each selected closed Gerber/Geometry outline. */
    private void convertOutlineToArea() {
        if (runningJob != null || camEditorActive()) {
            appendConsole(I18n.text("ui.620de2c68efd"));
            return;
        }
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.isEmpty()) {
            appendConsole(I18n.text("ui.32a4794184b7"));
            return;
        }
        List<OutlineConversion> requests = new ArrayList<>();
        for (TreeItem<String> item : selected) {
            GerberImage gerber = gerberByItem.get(item);
            GeometryEntry entry = geometryByItem.get(item);
            if (gerber == null && entry == null) {
                appendConsole(I18n.text("ui.f2c6a844b170"));
                continue;
            }
            Geometry source = gerber != null && gerber.followGeometry() != null && !gerber.followGeometry().isEmpty()
                    ? gerber.followGeometry() : gerber != null ? gerber.solidGeometry() : entry.geometry();
            String units = gerber != null ? gerber.units() : entry.units();
            requests.add(new OutlineConversion(new CamInput(item, item.getValue(), gerber != null ? gerber : entry), units, source));
        }
        if (requests.isEmpty()) return;
        CamGenerationState before = new CamGenerationState(tclProjectEpoch,
                requests.stream().map(OutlineConversion::input).toList(), toolTab.getContent());
        beginJob(I18n.text("ui.a35779299fb0"));
        activeCamGeneration = before;
        JobHandle<List<OutlineConversionOutcome>> handle = jobExecutor.submit(context -> {
            List<OutlineConversionOutcome> results = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                context.checkCancelled();
                OutlineConversion request = requests.get(i);
                context.reportProgress(.9 * i / requests.size(), I18n.text("ui.a84d3f996194") + request.input().name() + "...");
                try {
                    results.add(new OutlineConversionOutcome(request, OutlineToArea.convert(request.source(), context::isCancelled), null));
                } catch (IllegalArgumentException invalid) {
                    results.add(new OutlineConversionOutcome(request, null, Objects.toString(invalid.getMessage(), I18n.text("ui.219a624f0141"))));
                }
            }
            context.checkCancelled();
            context.reportProgress(.95, I18n.text("ui.a1ba5b094850"));
            return List.copyOf(results);
        }, camProgress(before));
        runningJob = handle;
        handle.completion().whenComplete((results, failure) -> Platform.runLater(() -> {
            if (runningJob != handle) return;
            try {
                if (failure != null) throw new java.util.concurrent.CompletionException(failure);
                if (!acceptCamGeneration(before, handle)) return;
                TreeItem<String> last = null;
                plotAreaView.beginBatchUpdate();
                try {
                    for (OutlineConversionOutcome outcome : results) {
                        OutlineConversion request = outcome.request();
                        if (outcome.error() != null) {
                            appendConsole(request.input().name() + ": " + outcome.error());
                            continue;
                        }
                        Geometry area = outcome.result().area();
                        last = addGeometryToProject(uniqueDerivedName(request.input().name() + "_area"),
                                request.input().name(), request.units(), area, false);
                        int holes = 0;
                        for (int i = 0; i < area.getNumGeometries(); i++)
                            holes += ((org.locationtech.jts.geom.Polygon) area.getGeometryN(i)).getNumInteriorRing();
                        appendConsole(I18n.text("ui.15dd6cba2124") + last.getValue() + String.format(java.util.Locale.ROOT,
                                I18n.text("ui.bd022d299134"),
                                area.getArea(), request.units().toLowerCase(java.util.Locale.ROOT), area.getNumGeometries(), holes));
                    }
                    if (last != null) selectProjectItem(last);
                } finally { plotAreaView.endBatchUpdate(); }
                updateProgress(1);
                setStatus(last != null ? I18n.text("ui.c8b1ce6a5f14") : I18n.text("ui.e01178b18511"), last != null ? IDLE_COLOR : ERROR_COLOR);
            } catch (Exception error) {
                reportJobError(error, I18n.text("ui.c51b4e08578b"));
            } finally {
                if (runningJob == handle) onJobFinished();
            }
        }));
    }

    private record OutlineConversion(CamInput input, String units, Geometry source) { }
    private record OutlineConversionOutcome(OutlineConversion request, OutlineToArea.Result result, String error) { }

    private void copySelectedObjects() {
        List<TreeItem<String>> selected = selectedObjects();
        if (!selected.isEmpty()) {
            copySelection(selected);
        } else {
            appendConsole(I18n.text("ui.a413460854d7"));
        }
    }

    private void deleteSelectedObjects() {
        List<TreeItem<String>> selected = selectedObjects();
        if (!selected.isEmpty()) {
            removeSelectionFromProject(selected);
        } else {
            appendConsole(I18n.text("ui.766bea2e1450"));
        }
    }

    private void focusSelectedObject() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (item != null && isPlottable(item)) {
            focusLayer(item);
        } else {
            appendConsole(I18n.text("ui.0a84ada7d1d6"));
        }
    }

    private void openSelectedGerberTool(String label,
            java.util.function.BiConsumer<TreeItem<String>, GerberImage> action) {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage image = gerberByItem.get(item);
        if (image == null) {
            appendConsole(I18n.text("ui.b957f7260ae4") + label + ".");
        } else {
            action.accept(item, image);
        }
    }

    private void openSelectedDrillingTool() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        ExcellonImage image = excellonByItem.get(item);
        if (image == null) {
            appendConsole(I18n.text("ui.c92039b8ea59"));
        } else {
            generateDrillGCode(item, image);
        }
    }

    private void openSelectedNccTool() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage gerber = gerberByItem.get(item);
        if (gerber != null) {
            generateNcc(item, gerber);
            return;
        }
        GeometryEntry geometry = geometryByItem.get(item);
        if (geometry != null) {
            generateNcc(item, geometry);
            return;
        }
        appendConsole(I18n.text("ui.6c519c87b783"));
    }

    private Runnable toolAction(String id) {
        return switch (id) {
            case "cutout" -> () -> openSelectedGerberTool("Cutout", this::generateCutout);
            case "ncc" -> this::openSelectedNccTool;
            case "isolation" -> () -> openSelectedGerberTool(I18n.text("ui.2fd3145b90e7"), this::generateIsolation);
            case "drilling" -> this::openSelectedDrillingTool;
            case "calculators" -> () -> openToolPanel("Calculators", CalculatorsPanel.build());
            case "transform" -> this::openTransformTool;
            case "double_sided" -> this::openDoubleSidedTool;
            case "paint" -> this::openPaintTool;
            case "panelize" -> this::openPanelizeTool;
            case "invert" -> this::openInvertGerberTool;
            case "subtract" -> this::openSubtractTool;
            case "extract_drills" -> this::openExtractDrillsTool;
            case "punch" -> this::openPunchGerberTool;
            case "etch" -> this::openEtchCompensationTool;
            case "film" -> this::openFilmTool;
            case "fiducials" -> this::openFiducialsTool;
            case "corners" -> this::openCornerMarkersTool;
            case "qrcode" -> this::openQrCodeTool;
            case "solderpaste" -> this::openSolderPasteTool;
            case "align" -> this::openAlignObjectsTool;
            case "optimal" -> this::openOptimalTool;
            case "rules" -> this::openRulesCheckTool;
            case "copper_thieving" -> this::openCopperThievingTool;
            case "calibration" -> this::openCalibrationTool;
            default -> null;
        };
    }

    private void addToolCommands(Menu menu, List<LegacyUiManifest.Command> commands) {
        for (LegacyUiManifest.Command command : commands) {
            MenuItem item = chromeItem(command.label(), command.icon(), toolAction(command.id()));
            ToolDescriptions.apply(item.getProperties(), command.id());
            menu.getItems().add(item);
        }
    }

    /** Gives a menu item a tooltip (title and text) shown by {@link FluidTooltips}. */
    private static MenuItem tipped(MenuItem item, String title, String text) {
        ToolDescriptions.apply(item.getProperties(), title, text);
        return item;
    }

    private void addPlannedCommands(Menu menu, List<LegacyUiManifest.Command> commands) {
        for (LegacyUiManifest.Command command : commands) {
            menu.getItems().add(plannedItem(command.label(), command.icon()));
        }
    }

    private void addGeometryEditorCommands(Menu menu) {
        for (LegacyUiManifest.Command command : LegacyUiManifest.GEOMETRY_EDITOR) {
            Runnable action = switch (command.id()) {
                case "select" -> () -> {
                    if (geometryEditor.isActive()) {
                        geometryEditor.startSelection();
                    } else {
                        editSelectedGeometry();
                    }
                };
                case "circle" -> geometryEditor::startCircle;
                case "arc" -> geometryEditor::startArc;
                case "rectangle" -> geometryEditor::startRectangle;
                case "path" -> geometryEditor::startPath;
                case "polygon" -> geometryEditor::startPolygon;
                case "move" -> geometryEditor::startMove;
                case "copy" -> geometryEditor::startCopy;
                case "delete" -> geometryEditor::deleteFromShortcut;
                case "union" -> geometryEditor::startUnion;
                case "intersection" -> geometryEditor::startIntersection;
                case "subtract" -> geometryEditor::startSubtract;
                case "cut_path" -> geometryEditor::startCutPath;
                case "buffer" -> geometryEditor::startBuffer;
                case "text" -> geometryEditor::startText;
                case "paint" -> geometryEditor::startPaint;
                case "eraser" -> geometryEditor::startEraser;
                case "transform" -> geometryEditor::openTransforms;
                case "explode" -> geometryEditor::explode;
                default -> null;
            };
            MenuItem item = action == null ? plannedItem(command.label(), command.icon())
                    : chromeItem(command.label(), command.icon(), action);
            if (action != null) GeometryEditorDescriptions.apply(item, command.id());
            menu.getItems().add(item);
        }
    }

    private void addExcellonEditorCommands(Menu menu) {
        for (LegacyUiManifest.Command command : LegacyUiManifest.EXCELLON_EDITOR) {
            Runnable action = switch (command.id()) {
                case "select" -> excellonEditor::startSelection;
                case "drill" -> excellonEditor::startDrill;
                case "drill_array" -> () -> excellonEditor.startArray(false);
                case "slot" -> excellonEditor::startSlot;
                case "slot_array" -> () -> excellonEditor.startArray(true);
                case "resize" -> excellonEditor::resizeSelected;
                case "copy" -> () -> excellonEditor.startMoveOrCopy(true);
                case "delete" -> excellonEditor::deleteFromShortcut;
                case "move" -> () -> excellonEditor.startMoveOrCopy(false);
                default -> null;
            };
            menu.getItems().add(action == null ? plannedItem(command.label(), command.icon())
                    : chromeItem(command.label(), command.icon(), action));
        }
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu(I18n.text("ui.ba8a452f2f83"));
        Menu newMenu = new Menu(I18n.text("ui.2b1d9b4b4ba4"));
        setLegacyMenuIcon(newMenu, "new_file32.png");
        newMenu.getItems().addAll(
                tipped(chromeItem(I18n.text("ui.c7694e95e3a3"), "new_file32.png", this::newProject), I18n.text("ui.c7694e95e3a3"),
                        I18n.text("ui.abcf32faadce")),
                tipped(chromeItem("Geometry", "geometry32.png", this::newEmptyGeometryObject), I18n.text("ui.1264f2c2b06b"),
                        I18n.text("ui.39ba6885692b")),
                tipped(chromeItem("Excellon", "drill32.png", this::newEmptyExcellonObject), I18n.text("ui.4bc0ac357e88"),
                        I18n.text("ui.f16b5da25d43")),
                plannedItem("Document", "notebook32.png"));
        Menu openMenu = new Menu(I18n.text("ui.a01a5fce396e"));
        setLegacyMenuIcon(openMenu, "folder32.png");
        openMenu.getItems().addAll(
                chromeItem(I18n.text("ui.e04714bfceef"), "folder32.png", this::openProject),
                chromeItem("Gerber...", "flatcam_icon32.png", this::openGerberPrototype),
                chromeItem("Excellon...", "drill32.png", this::openExcellonPrototype),
                chromeItem("G-Code...", "cnc32.png", this::openGCode),
                plannedItem("Configuracao...", "settings18.png"));
        Menu importMenu = new Menu(I18n.text("ui.e7f0e6d956c1"));
        setLegacyMenuIcon(importMenu, "import.png");
        importMenu.getItems().addAll(
                chromeItem(I18n.text("ui.b55822d49b07"), "svg32.png", () -> importDrawing(DrawingFormat.SVG, false)),
                chromeItem(I18n.text("ui.b6ce9247cebd"), "svg32.png", () -> importDrawing(DrawingFormat.SVG, true)),
                chromeItem(I18n.text("ui.ef24c785d4f4"), "dxf16.png", () -> importDrawing(DrawingFormat.DXF, false)),
                chromeItem(I18n.text("ui.a993d8dd9ce4"), "dxf16.png", () -> importDrawing(DrawingFormat.DXF, true)),
                chromeItem("HPGL2...", "import.png", () -> importDrawing(DrawingFormat.HPGL, false)),
                chromeItem("PDF...", "pdf32.png", this::importPdf));
        Menu exportMenu = new Menu(I18n.text("ui.292f20ee64bc"));
        setLegacyMenuIcon(exportMenu, "export.png");
        exportMenu.getItems().addAll(
                chromeItem("SVG...", "svg32.png", this::exportSelectedSvg),
                chromeItem("DXF...", "dxf16.png", this::exportSelectedDxf),
                chromeItem("PNG...", "export_png32.png", this::exportPlotPng),
                chromeItem("Gerber...", "flatcam_icon32.png", () -> exportSelectedCam(true)),
                chromeItem("Excellon...", "drill32.png", () -> exportSelectedCam(false)));
        Menu scriptMenu = new Menu("Scripting");
        setLegacyMenuIcon(scriptMenu, "script16.png");
        scriptMenu.getItems().addAll(
                tipped(chromeItem(I18n.text("ui.04832ec9d8c8"), "script_new24.png", this::newScript), I18n.text("ui.04832ec9d8c8"),
                        I18n.text("ui.d4f38d4d29b4")),
                tipped(chromeItem(I18n.text("ui.9bf49376674c"), "open_script32.png", this::openScripts), I18n.text("ui.013b5406590f"),
                        I18n.text("ui.975763bb7978")),
                tipped(chromeItem(I18n.text("ui.fcc5161acca7"), "script16.png", this::runScriptFile), I18n.text("ui.d64e8d0bbe2d"),
                        I18n.text("ui.f1cff60a1bf1")));
        Menu backupMenu = new Menu("Backup");
        setLegacyMenuIcon(backupMenu, "backup24.png");
        backupMenu.getItems().addAll(
                chromeItem(I18n.text("ui.0fc4eccb4c46"), "backup_import24.png", this::importToolDefaults),
                chromeItem(I18n.text("ui.274da9b3ab3d"), "backup_export24.png", this::exportToolDefaults));
        MenuItem printPdf = tipped(chromeItem(I18n.text("ui.5b4d84680302"), "pdf32.png", this::printSelectedPdf),
                I18n.text("ui.217ce43fce3a"), I18n.text("ui.f25db0cc902a"));
        printPdf.setId("print-pdf-menu-item");
        printPdf.setAccelerator(new javafx.scene.input.KeyCodeCombination(KeyCode.P, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        fileMenu.getItems().addAll(newMenu, openMenu, RecentFilesMenu.build(recentFiles,this::openRecent,this::legacyIcon),
                new SeparatorMenuItem(),
                tipped(chromeItem(I18n.text("ui.f9e7dca283b3"), "project_save32.png", this::saveProject), I18n.text("ui.7e0a780ac03b"),
                        I18n.text("ui.648e941706f3")),
                tipped(chromeItem(I18n.text("ui.3c26ec8a71b1"), "save_as.png", this::saveProjectAs), I18n.text("ui.dbdd6b57d23d"),
                        I18n.text("ui.6707d4e855b5")),
                new SeparatorMenuItem(),
                importMenu, exportMenu, scriptMenu, backupMenu,
                printPdf, new SeparatorMenuItem(),
                chromeItem(I18n.text("ui.2d023dea2a9d"), "power16.png", this::requestApplicationClose));

        Menu editMenu = new Menu(I18n.text("ui.2eba946b2e1e"));
        Menu conversionsMenu = new Menu(I18n.text("ui.e18a39d085ea"));
        setLegacyMenuIcon(conversionsMenu, "convert32.png");
        conversionsMenu.getItems().addAll(
                tipped(chromeItem(I18n.text("ui.6494f73a898b"), "geometry32.png", this::convertOutlineToArea),
                        I18n.text("ui.6494f73a898b"), I18n.text("ui.f8030ef949f9")),
                tipped(chromeItem("Single → Multi-Geometry", "geometry32.png", this::convertSingleToMultiGeometry),
                        "Single → Multi-Geometry", I18n.text("ui.8dd14674e0e3")),
                tipped(chromeItem("Multi → Single-Geometry", "geometry32.png", this::convertMultiToSingleGeometry),
                        "Multi → Single-Geometry", I18n.text("ui.67cf8ed9edf9")),
                tipped(chromeItem(I18n.text("ui.b803f3348350"), "geometry32.png", this::convertSelectedToGeometry),
                        I18n.text("ui.b803f3348350"), I18n.text("ui.1a546a84cfac")),
                tipped(chromeItem(I18n.text("ui.5451b5591b78"), "flatcam_icon32.png", this::convertSelectedToGerber),
                        I18n.text("ui.5451b5591b78"), I18n.text("ui.1fe209e03d5e")),
                tipped(chromeItem(I18n.text("ui.745598a5f117"), "drill32.png", this::convertSelectedToExcellon),
                        I18n.text("ui.745598a5f117"), I18n.text("ui.78447fb5a8ee")));
        Menu joinMenu = new Menu(I18n.text("ui.ad7b1232ade4"));
        setLegacyMenuIcon(joinMenu, "union32.png");
        joinMenu.getItems().addAll(
                tipped(chromeItem("Geo/Gerber/Exc → Geo", "geometry32.png", this::joinSelectedToGeometry),
                        I18n.text("ui.a9b87a9ceb6f"), I18n.text("ui.cfeda342e7f8")),
                tipped(chromeItem("Excellon(s) → Excellon", "drill32.png", this::joinSelectedExcellons),
                        I18n.text("ui.36313e43b7a3"), I18n.text("ui.7a2a166e10a7")),
                tipped(chromeItem("Gerber(s) → Gerber", "flatcam_icon32.png", this::joinSelectedGerbers),
                        I18n.text("ui.dae03df29175"), I18n.text("ui.341a3dc70ee5")));
        Menu editorToolsMenu = new Menu(I18n.text("ui.281a7ac22459"));
        setLegacyMenuIcon(editorToolsMenu, "edit_file32.png");
        excellonEditorMenu = new Menu(I18n.text("ui.0e54aac8a039"));
        setLegacyMenuIcon(excellonEditorMenu, "drill32.png");
        addExcellonEditorCommands(excellonEditorMenu);
        excellonEditorMenu.setVisible(false);
        geometryEditorMenu = new Menu("Geo Editor");
        setLegacyMenuIcon(geometryEditorMenu, "geometry32.png");
        addGeometryEditorCommands(geometryEditorMenu);
        geometryEditorMenu.setVisible(false);
        editorToolsMenu.getItems().add(excellonEditorMenu);
        editMenu.getItems().addAll(
                chromeItem(I18n.text("ui.eb7aab64ae0e"), "edit_file32.png", this::editSelectedObject),
                chromeItem(I18n.text("ui.fad321b43da7"), "close_edit_file32.png", this::saveAndCloseEditor),
                chromeItem(I18n.text("ui.b9c4772b62bd"), "code_editor32.png", this::editSelectedGCode),
                editorToolsMenu,
                new SeparatorMenuItem(),
                chromeItem(I18n.text("ui.39dcf1cbeb5a"), "copy_file32.png", this::copySelectedObjects),
                chromeItem(I18n.text("ui.8b19518ff49b"), "trash32.png", this::deleteSelectedObjects),
                conversionsMenu, joinMenu,
                new SeparatorMenuItem(),
                tipped(chromeItem(I18n.text("ui.475f2d0d401e"), "distance32.png", this::openMeasureTool), I18n.text("ui.475f2d0d401e"),
                        I18n.text("ui.14f406ab079c")),
                tipped(chromeItem(I18n.text("ui.61ff53fbc31f"), "distance_min32.png", this::openMinimumDistanceTool), I18n.text("ui.61ff53fbc31f"),
                        I18n.text("ui.93067ebb3e28")),
                tipped(chromeItem(I18n.text("ui.a5a452cd4bb9"), "origin32.png", this::beginSetOrigin), I18n.text("ui.a5a452cd4bb9"),
                        I18n.text("ui.564f364b5657")),
                tipped(chromeItem(I18n.text("ui.ccf3e8a254ff"), "origin2_32.png", this::moveSelectionToOrigin), I18n.text("ui.ccf3e8a254ff"),
                        I18n.text("ui.13cd4222199c")),
                tipped(chromeItem(I18n.text("ui.31cfc316cf52"), "jump_to16.png", this::askJumpToLocation), I18n.text("ui.e1a40ac7311e"),
                        I18n.text("ui.89bfc1279fce")),
                tipped(chromeItem(I18n.text("ui.e556833d7e75"), "locate32.png", this::askLocateInObject), I18n.text("ui.e556833d7e75"),
                        I18n.text("ui.147abf5190f4")),
                new SeparatorMenuItem(),
                plannedItem(I18n.text("ui.51d0ebc35e85"), "toggle_units32.png"),
                chromeItem(I18n.text("ui.6706dbe8b723"), "settings18.png", this::openPreferences));

        Menu optionsMenu = new Menu(I18n.text("ui.eadb377837ef"));
        MenuItem toolsDbItem = chromeItem("Tools Database", "search_db32.png", this::openToolsDatabase);
        MenuItem calculatorsItem = chromeItem("Calculators", "calculator24.png",
                () -> openToolPanel("Calculators", CalculatorsPanel.build()));

        // appGUI/MainGUI.py's Options menu quick actions (Rotate/Skew X/Skew Y/Flip X/Flip Y) -
        // a separate, lighter driver than the full Transform tool: fixed "selection bbox center"
        // reference, a small dialog for Rotate/Skew, instant apply for Flip. Same icons as Python
        // (rotate.png/skewX.png/skewY.png/flipx.png/flipy.png). Python also binds bare Shift+R/
        // Shift+X/Shift+Y/X/Y accelerators; the bare X/Y ones are skipped here since a scene-global
        // accelerator with no modifier would hijack typing "x"/"y" into any text field.
        MenuItem rotateItem = new MenuItem(I18n.text("ui.653c9279cb57"));
        rotateItem.setGraphic(legacyIcon("rotate.png", 16));
        rotateItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole(I18n.text("ui.c1d7f1b2effb"));
                return;
            }
            Double angle = promptAngle(I18n.text("ui.653c9279cb57"), I18n.text("ui.3bbcf0c20ee2"), 90);
            if (angle != null) {
                // UI-positive = clockwise; negate before building Rotate (positive = CCW there),
                // exactly like Python's own on_rotate() (obj.rotate(-num, point)).
                applyTransformToSelection(selected -> new TransformOp.Rotate(-angle, selectionCenterOrOrigin(selected)));
            }
        });
        MenuItem skewXItem = new MenuItem(I18n.text("ui.04e21a077291"));
        skewXItem.setGraphic(legacyIcon("skewX.png", 16));
        skewXItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole(I18n.text("ui.bf68377ef88a"));
                return;
            }
            Double angle = promptAngle(I18n.text("ui.04e21a077291"), I18n.text("ui.fa69ece3eafa"), 0);
            if (angle != null) {
                applyTransformToSelection(selected -> new TransformOp.Skew(angle, 0, selectionCenterOrOrigin(selected)));
            }
        });
        MenuItem skewYItem = new MenuItem(I18n.text("ui.fde21af76971"));
        skewYItem.setGraphic(legacyIcon("skewY.png", 16));
        skewYItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole(I18n.text("ui.bf68377ef88a"));
                return;
            }
            Double angle = promptAngle(I18n.text("ui.fde21af76971"), I18n.text("ui.fa69ece3eafa"), 0);
            if (angle != null) {
                applyTransformToSelection(selected -> new TransformOp.Skew(0, angle, selectionCenterOrOrigin(selected)));
            }
        });
        MenuItem flipXItem = new MenuItem(I18n.text("ui.9b88de14b6a8"));
        flipXItem.setGraphic(legacyIcon("flipx.png", 16));
        flipXItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole(I18n.text("ui.82e7aebc6d3d"));
                return;
            }
            applyTransformToSelection(selected -> new TransformOp.MirrorX(selectionCenterOrOrigin(selected)));
        });
        MenuItem flipYItem = new MenuItem(I18n.text("ui.ddc43feadc34"));
        flipYItem.setGraphic(legacyIcon("flipy.png", 16));
        flipYItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole(I18n.text("ui.82e7aebc6d3d"));
                return;
            }
            applyTransformToSelection(selected -> new TransformOp.MirrorY(selectionCenterOrOrigin(selected)));
        });

        optionsMenu.getItems().addAll(toolsDbItem, calculatorsItem,
                chromeItem(I18n.text("ui.4f4d2adf3dfb"), "source32.png", () -> {
                    TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
                    if (item != null && isProjectObject(item)) {
                        viewObjectSource(item);
                    } else {
                        appendConsole(I18n.text("ui.422e8a544d03"));
                    }
                }), new SeparatorMenuItem(),
                rotateItem, skewXItem, skewYItem, flipXItem, flipYItem);

        Menu viewMenu = new Menu(I18n.text("ui.ac39c7f377e1"));
        viewMenu.getItems().addAll(
                tipped(chromeItem(I18n.text("ui.ab788130b65e"), "replot32.png", this::replotAllObjects), I18n.text("ui.ab788130b65e"),
                        I18n.text("ui.6722ab47c201")),
                tipped(chromeItem(I18n.text("ui.a7b5139835d5"), "clear_plot32.png", this::clearAllPlots), I18n.text("ui.a7b5139835d5"),
                        I18n.text("ui.879500dc29db")),
                tipped(chromeItem(I18n.text("ui.0e37130c17e3"), "zoom_in32.png", () -> plotAreaView.zoomBy(MENU_ZOOM_RATIO)), I18n.text("ui.0e37130c17e3"),
                        I18n.text("ui.cffd9d308d26")),
                tipped(chromeItem(I18n.text("ui.e45cb1f6faa7"), "zoom_out32.png", () -> plotAreaView.zoomBy(1 / MENU_ZOOM_RATIO)), I18n.text("ui.e45cb1f6faa7"),
                        I18n.text("ui.c766ac4c4a09")),
                chromeItem(I18n.text("ui.90e33a7729f0"), "zoom_fit32.png", this::focusSelectedObject),
                new SeparatorMenuItem(),
                chromeItem(I18n.text("ui.5000c1262593"), "grid32.png", () -> statusControls.toggleGrid()),
                chromeItem(I18n.text("ui.1f488bab17b5"), "axis32.png", () -> statusControls.toggleAxis()),
                chromeItem("Workspace", "workspace24.png", () -> statusControls.toggleWorkspace()),
                chromeItem("HUD", "hud_32.png", () -> statusControls.toggleHud()),
                new SeparatorMenuItem(),
                chromeItem(I18n.text("ui.baa81a88f254"), "notebook32.png", this::toggleSidebar),
                buildToolbarsMenu(),
                new SeparatorMenuItem(), buildThemeMenu());

        Menu objectsMenu = new Menu(I18n.text("ui.54a54a384ffb"));
        objectsMenu.getItems().addAll(
                tipped(chromeItem(I18n.text("ui.f388dff7124d"), "select_all.png", this::selectAllObjects), I18n.text("ui.f388dff7124d"),
                        I18n.text("ui.121dd81e92dd")),
                tipped(chromeItem(I18n.text("ui.e3c3b9cf74c6"), "deselect_all32.png", this::deselectAllObjects), I18n.text("ui.e3c3b9cf74c6"),
                        I18n.text("ui.0d1c2100e1d2")),
                new SeparatorMenuItem(),
                chromeItem(I18n.text("ui.aae9a2ff2cfc"), "copy32.png", this::copySelectedObjects),
                chromeItem(I18n.text("ui.5915e01aca50"), "trash32.png", this::deleteSelectedObjects));

        Menu toolsMenu = new Menu(I18n.text("ui.b96a67bfde3e"));
        Menu preparationMenu = new Menu(I18n.text("ui.f8c4add98bcb"));
        addToolCommands(preparationMenu, LegacyUiManifest.TOOLS_PREPARATION);
        Menu camMenu = new Menu(I18n.text("ui.3d375cd5388b"));
        addToolCommands(camMenu, LegacyUiManifest.TOOLS_CAM);
        Menu utilitiesMenu = new Menu(I18n.text("ui.849d2880152c"));
        addToolCommands(utilitiesMenu, LegacyUiManifest.TOOLS_UTILITIES);
        toolsMenu.getItems().addAll(chromeItem(I18n.text("ui.42ddf25409aa"), "shell32.png", this::openTerminal),
                new SeparatorMenuItem(), preparationMenu, camMenu, utilitiesMenu);

        Menu helpMenu = new Menu(I18n.text("ui.fe0addd70781"));
        Menu diagnosticsMenu = new Menu(I18n.text("ui.ab82b7fdb8e4"));
        setLegacyMenuIcon(diagnosticsMenu, "bug32.png");
        diagnosticsMenu.setDisable(FlatCamLauncher.diagnosticDirectory() == null);
        diagnosticsMenu.getItems().addAll(
                chromeItem(I18n.text("ui.754e5fa34dff"), "folder32.png", () -> FlatCamLauncher.openDiagnosticDirectory(this::appendConsole)),
                chromeItem(I18n.text("ui.df4894568b70"), "project_save32.png", () -> appendConsole(FlatCamLauncher.captureNow()
                        ? I18n.text("ui.c7c28e170c09")
                        : I18n.text("ui.ba305012548c"))));
        MenuItem demoJobItem = new MenuItem(I18n.text("ui.09997b84e8a8"));
        demoJobItem.setOnAction(e -> runDemoJob());
        demoJobItem.disableProperty().bind(runDemoJobButton.disableProperty());
        helpMenu.getItems().addAll(
                plannedItem(I18n.text("ui.506bd07713b1"), "help.png"),
                plannedItem("Bookmarks", "bookmarks32.png"),
                tipped(chromeItem(I18n.text("ui.627e2a1aede0"), "shortcuts24.png", this::openShortcutList), I18n.text("ui.627e2a1aede0"),
                        I18n.text("ui.221f493eb7d5")),
                plannedItem(I18n.text("ui.76dd64e93cce"), "videohelp24.png"),
                plannedItem(I18n.text("ui.ac8c8f081adb"), "bug32.png"),
                new SeparatorMenuItem(),
                diagnosticsMenu,
                demoJobItem,
                chromeItem(I18n.text("ui.97a0fa0947ad"), "about32.png", () -> new AboutDialog(scene.getWindow(), currentTheme).showAndWait()),
                chromeItem(I18n.text("ui.09997b84e8a8"), "code.png", this::runDemoJob));

        return new MenuBar(fileMenu, editMenu, optionsMenu, viewMenu, objectsMenu, toolsMenu, helpMenu,
                geometryEditorMenu);
    }

    /** Original and ice palettes, each with light and dark variants. */
    private Menu buildThemeMenu() {
        ToggleGroup themeGroup = new ToggleGroup();

        Menu classicMenu = new Menu(I18n.text("ui.88d759ea02ce"));
        classicMenu.getItems().addAll(
                themeItem(ThemeOption.CLASSIC_LIGHT, themeGroup),
                themeItem(ThemeOption.CLASSIC_DARK, themeGroup)
        );

        Menu iceMenu = new Menu(I18n.text("ui.fa1439e25b42"));
        iceMenu.getItems().addAll(
                themeItem(ThemeOption.ICE_LIGHT, themeGroup),
                themeItem(ThemeOption.ICE_DARK, themeGroup)
        );

        Menu themeMenu = new Menu(I18n.text("ui.d5df3708bc68"));
        themeMenu.getItems().addAll(classicMenu, iceMenu);
        themeMenu.setOnShowing(event -> {
            for (Menu submenu : List.of(classicMenu, iceMenu)) {
                for (MenuItem item : submenu.getItems()) {
                    if (item instanceof RadioMenuItem radio)
                        radio.setSelected(radio.getUserData() == currentTheme);
                }
            }
        });
        return themeMenu;
    }

    /** Selection reflects (and, on change, persists) {@link #currentTheme} - loaded from AppPreferences at construction. */
    private RadioMenuItem themeItem(ThemeOption option, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(option.label());
        item.setToggleGroup(group);
        item.setSelected(option == currentTheme);
        item.setUserData(option);
        item.setOnAction(e -> applyTheme(option));
        return item;
    }

    private void applyTheme(ThemeOption option) {
        option.applyTo(scene);
        currentTheme = option;
        darkIcons.set(option.isDark());
        plotAreaView.applyTheme(option);
        AppPreferences.saveTheme(option);
        // Tree cells cache their graphic nodes; rebuild them for the new icon contrast.
        if (projectTree != null) projectTree.refresh();
    }

    /** The Python file/edit/view/shell toolbar groups, with unavailable commands visibly disabled. */
    /** Python's global_toolbar_view bits (appGUI/MainGUI.py): which toolbars are showing. */
    private static final int TOOLBAR_FILE = 1;
    private static final int TOOLBAR_EDIT = 2;
    private static final int TOOLBAR_VIEW = 4;
    private static final int TOOLBAR_TOOLS = 8;
    private static final int TOOLBAR_SHELL = 256;

    private final Map<Integer, List<Node>> toolbarGroups = new LinkedHashMap<>();
    private int toolbarView = AppPreferences.loadToolbarView();

    /**
     * The File, Edit, View and Shell toolbars of appGUI/MainGUI.py in one row, in Python's order and with
     * Python's buttons. Each group can be switched off from View > Toolbars, as in Python; the FX-only
     * conveniences (Open G-Code, the demonstration job) live in the menus instead.
     */
    private ToolBar buildToolBar() {
        runDemoJobButton.setGraphic(Icons.play(16));
        runDemoJobButton.setTooltip(new Tooltip(I18n.text("ui.09997b84e8a8")));
        runDemoJobButton.setOnAction(e -> runDemoJob());

        cancelJobButton.setGraphic(Icons.cancel(14));
        cancelJobButton.getStyleClass().add("job-cancel-button");
        cancelJobButton.setTooltip(new Tooltip(I18n.text("ui.bafa9a59c966")));
        cancelJobButton.setDisable(true);
        cancelJobButton.setOnAction(e -> cancelDemoJob());

        ToolBar bar = new ToolBar();
        toolbarGroup(bar, TOOLBAR_FILE, false,
                chromeButton(I18n.text("ui.f52dc2edecf0"), "flatcam_icon32.png", this::openGerberPrototype),
                chromeButton(I18n.text("ui.17e7d1d04a6e"), "drill32.png", this::openExcellonPrototype),
                new Separator(),
                chromeButton(I18n.text("ui.0c49c87c5cb7"), "folder32.png", this::openProject),
                chromeButton(I18n.text("ui.7e0a780ac03b"), "project_save32.png", this::saveProject));
        toolbarGroup(bar, TOOLBAR_EDIT, true,
                chromeButton("Editor", "edit_file32.png", this::editSelectedObject),
                chromeButton(I18n.text("ui.fad321b43da7"), "close_edit_file32.png", this::saveAndCloseEditor),
                new Separator(),
                chromeButton(I18n.text("ui.39dcf1cbeb5a"), "copy_file32.png", this::copySelectedObjects),
                chromeButton(I18n.text("ui.8b19518ff49b"), "trash32.png", this::deleteSelectedObjects),
                new Separator(),
                chromeButton(I18n.text("ui.475f2d0d401e"), "distance32.png", this::openMeasureTool),
                chromeButton(I18n.text("ui.61ff53fbc31f"), "distance_min32.png", this::openMinimumDistanceTool),
                chromeButton(I18n.text("ui.a5a452cd4bb9"), "origin32.png", this::beginSetOrigin),
                chromeButton(I18n.text("ui.ccf3e8a254ff"), "origin2_32.png", this::moveSelectionToOrigin),
                chromeButton(I18n.text("ui.31cfc316cf52"), "jump_to16.png", this::askJumpToLocation),
                chromeButton(I18n.text("ui.e556833d7e75"), "locate32.png", this::askLocateInObject));
        toolbarGroup(bar, TOOLBAR_VIEW, true,
                chromeButton(I18n.text("ui.a97223c34623"), "replot32.png", this::replotAllObjects),
                chromeButton(I18n.text("ui.a7b5139835d5"), "clear_plot32.png", this::clearAllPlots),
                chromeButton(I18n.text("ui.0e37130c17e3"), "zoom_in32.png", () -> plotAreaView.zoomBy(MENU_ZOOM_RATIO)),
                chromeButton(I18n.text("ui.e45cb1f6faa7"), "zoom_out32.png", () -> plotAreaView.zoomBy(1 / MENU_ZOOM_RATIO)),
                chromeButton(I18n.text("ui.90e33a7729f0"), "zoom_fit32.png", this::focusSelectedObject));
        toolbarGroup(bar, TOOLBAR_SHELL, true,
                chromeButton(I18n.text("ui.27f6a42b463f"), "shell32.png", this::openTerminal),
                chromeButton(I18n.text("ui.04832ec9d8c8"), "script_new24.png", this::newScript),
                chromeButton(I18n.text("ui.013b5406590f"), "open_script32.png", this::openScripts),
                chromeButton(I18n.text("ui.d64e8d0bbe2d"), "script16.png", this::runScriptFile));
        applyToolbarView();
        return bar;
    }

    private void toolbarGroup(ToolBar bar, int bit, boolean leadingSeparator, Node... nodes) {
        List<Node> group = new ArrayList<>();
        if (leadingSeparator) {
            group.add(new Separator());
        }
        group.addAll(List.of(nodes));
        bar.getItems().addAll(group);
        toolbarGroups.put(bit, group);
    }

    /** Shows or hides each toolbar according to {@link #toolbarView}. */
    private void applyToolbarView() {
        for (Map.Entry<Integer, List<Node>> entry : toolbarGroups.entrySet()) {
            boolean visible = (toolbarView & entry.getKey()) != 0;
            for (Node node : entry.getValue()) {
                node.setVisible(visible);
                node.setManaged(visible);
            }
        }
        if (regularToolsToolbar != null) {
            boolean visible = (toolbarView & TOOLBAR_TOOLS) != 0;
            regularToolsToolbar.setVisible(visible);
            regularToolsToolbar.setManaged(visible);
        }
    }

    /** View > Toolbars: one check item per toolbar, remembered between sessions like Python's global_toolbar_view. */
    private Menu buildToolbarsMenu() {
        Menu menu = new Menu(I18n.text("ui.222f95fcca05"));
        Object[][] entries = {
                {"File", TOOLBAR_FILE}, {"Edit", TOOLBAR_EDIT}, {"View", TOOLBAR_VIEW},
                {"Shell", TOOLBAR_SHELL}, {"Tools", TOOLBAR_TOOLS}};
        for (Object[] entry : entries) {
            int bit = (Integer) entry[1];
            CheckMenuItem item = new CheckMenuItem((String) entry[0]);
            item.setSelected((toolbarView & bit) != 0);
            item.setOnAction(e -> {
                toolbarView = item.isSelected() ? toolbarView | bit : toolbarView & ~bit;
                AppPreferences.saveToolbarView(toolbarView);
                applyToolbarView();
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private ToolBar buildToolsToolBar() {
        ToolBar toolbar = new ToolBar();
        addToolbarCommands(toolbar, LegacyUiManifest.TOOLS_PREPARATION);
        toolbar.getItems().add(new Separator());
        addToolbarCommands(toolbar, LegacyUiManifest.TOOLS_CAM);
        toolbar.getItems().add(new Separator());
        addToolbarCommands(toolbar, LegacyUiManifest.TOOLS_UTILITIES);
        return toolbar;
    }

    private void showGeometryEditorToolbar(Node toolbar) {
        topBars.getChildren().set(2, toolbar);
        geometryEditorMenu.setVisible(true);
    }

    private void hideGeometryEditorToolbar() {
        if (topBars != null && regularToolsToolbar != null) {
            topBars.getChildren().set(2, regularToolsToolbar);
            geometryEditorMenu.setVisible(false);
        }
    }

    private void addToolbarCommands(ToolBar toolbar, List<LegacyUiManifest.Command> commands) {
        for (LegacyUiManifest.Command command : commands) {
            Button button = chromeButton(command.label(), command.icon(), toolAction(command.id()));
            if (ToolDescriptions.of(command.id()) != null) {
                button.setTooltip(null);
                ToolDescriptions.apply(button.getProperties(), command.id());
            }
            toolbar.getItems().add(button);
        }
    }

    /** Python's fixed infobar: feedback, coordinates, grid, canvas controls, units and activity. */
    private HBox buildStatusBar() {
        ToggleButton consoleToggle = new ToggleButton(null, legacyIcon("shell20.png", 16));
        consoleToggle.setTooltip(new Tooltip(I18n.text("ui.c340d5728a27")));
        consoleToggle.getStyleClass().addAll("status-bar-toggle", "status-console");
        consoleToggle.setSelected(!consoleCollapsed);
        consoleToggle.setOnAction(e -> toggleConsole(consoleToggle.isSelected()));

        statusControls = new PlotStatusControls(plotAreaView,
                file -> legacyIcon(file, 16), consoleToggle,
                statusLabel::setText, this::openPreferences);
        unitsLabel.setTooltip(new Tooltip(I18n.text("ui.68f94529cc46")));
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.NEVER);
        updateConsoleProgressVisibility();
        HBox feedback = new HBox(6, statusLabel, compactJobProgress);
        feedback.setAlignment(Pos.CENTER_LEFT);
        feedback.setMinWidth(0);
        HBox.setHgrow(feedback, Priority.ALWAYS);
        HBox bar = new HBox(6, feedback, statusControls.node(), unitsLabel, statusDot, activityLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void setStatus(String text, Color dotColor) {
        statusLabel.setText(text);
        statusDot.setFill(dotColor);
        activityLabel.setText(dotColor == RUNNING_COLOR ? "Working..."
                : dotColor == ERROR_COLOR ? "Failed."
                : dotColor == CANCELLED_COLOR ? "Cancelled." : "Idle.");
    }

    /** Whether the application is showing millimetres (the units of the last object loaded). */
    boolean displayUnitsMetric() {
        return !unitsLabel.getText().toLowerCase(java.util.Locale.ROOT).contains("in");
    }

    private void setDisplayUnits(String units) {
        String display = units == null || units.isBlank() ? "mm" : units.toLowerCase(java.util.Locale.ROOT);
        unitsLabel.setText("[" + display + "]");
        plotAreaView.setUnits(units);
    }

    private SplitPane buildMainSplit() {
        horizontalSplit = new SplitPane(buildLeftTabs(), buildCenterTabs());
        bottomPanel = buildBottomPanel();
        // Starts without the bottom panel at all (not just visually collapsed) if the
        // console was closed last session - same "remove from items" mechanism toggleConsole()
        // uses, just applied before the first layout instead of via a later user click.
        verticalSplit = consoleCollapsed ? new SplitPane(horizontalSplit) : new SplitPane(horizontalSplit, bottomPanel);
        verticalSplit.setOrientation(Orientation.VERTICAL);
        sidebarAnimation = new AnimatedSplitPanel(horizontalSplit, leftTabs, 0,
                () -> AppPreferences.loadSplitHorizontalForScreen(currentScreenId, AppPreferences.loadSplitHorizontal(0.22)),
                () -> { if (!sidebarCollapsed) scheduleSidebarRestore(); });
        consoleAnimation = new AnimatedSplitPanel(verticalSplit, bottomPanel, 1,
                () -> dividerBeforeConsoleCollapse, () -> {
                    if (!consoleCollapsed) attachVerticalDividerSaveListener();
                    updateConsoleProgressVisibility();
                    scheduleSidebarRestore();
                });

        // Reapply after each Stage width change. A transfer between monitors can
        // resize this SplitPane for several pulses before the new Stage settles.
        // Layout-driven divider moves must never be persisted as user choices.
        horizontalSplit.widthProperty().addListener((obs, oldWidth, newWidth) -> scheduleSidebarRestore());
        Platform.runLater(() -> {
            scheduleSidebarRestore();
            if (!consoleCollapsed) {
                verticalSplit.setDividerPositions(AppPreferences.loadSplitVertical(0.75));
                attachVerticalDividerSaveListener();
            } else {
                // No bottom panel means no divider to restore a position on yet - toggleConsole()
                // reads this the next time the console is actually reopened.
                dividerBeforeConsoleCollapse = AppPreferences.loadSplitVertical(0.75);
            }
        });
        return verticalSplit;
    }

    /**
     * SplitPane rebuilds its Divider list whenever its items change - after
     * toggleConsole() removes/re-adds the bottom panel, the old Divider
     * instance a listener was attached to is discarded, so this must be
     * called again each time the panel is restored, not just once at startup.
     */
    private void attachVerticalDividerSaveListener() {
        if (verticalSplit.getDividers().isEmpty()) return;
        SplitPane.Divider divider = verticalSplit.getDividers().getFirst();
        if (divider == observedConsoleDivider) return;
        observedConsoleDivider = divider;
        divider.positionProperty().addListener((obs, oldVal, newVal) -> saveSplitPositions());
    }

    /**
     * Project / Properties / Tool sharing one tab strip - see UI_INVENTORY.md
     * section 1 (appGUI/MainGUI.py's self.notebook - project_tab/
     * properties_tab/tool_tab). The Tool tab is where a running tool's own
     * form lives (see {@link #openToolPanel}) - appTools/ToolIsolation.py
     * and ToolDrilling.py both switch this same tab to their own UI via
     * app.ui.notebook.setCurrentWidget(app.ui.tool_tab) rather than opening
     * a separate window.
     */
    private TabPane buildLeftTabs() {
        projectTab = new Tab(I18n.text("ui.4c7877d7558f"), buildProjectTree());
        propertiesPlaceholder.setTextAlignment(TextAlignment.CENTER);
        propertiesContainer.getChildren().add(propertiesPlaceholder);
        propertiesTab = new Tab(I18n.text("ui.ecaf3589f2b5"), propertiesContainer);
        toolTab = new Tab(I18n.text("ui.c188272ba40c"), centeredPlaceholder(I18n.text("ui.2bfeb04164a2")));

        leftTabs = new TabPane(projectTab, propertiesTab, toolTab);
        leftTabs.getTabs().forEach(tab -> tab.setClosable(false));
        leftTabs.getStyleClass().add("side-panel");
        leftTabs.setMinWidth(160);
        return leftTabs;
    }

    /**
     * Switches the left sidebar to the Tool tab and loads {@code content} into it
     * (wrapped in a ScrollPane - matches every legacy tool's own
     * app.ui.tool_scroll_area, needed once a tool's form is tall enough to not fit
     * the sidebar, e.g. CalculatorsPanel's three stacked calculators).
     */
    /**
     * Undoes what the tool panel on screen left on the plot (previews, an armed click pick, a drawing in progress).
     * Set by the tools that draw on the plot while they are open; run whenever another tool takes the tab or the
     * panel closes, so switching tools never leaves ghosts behind or a click captured by a panel that is gone.
     */
    private Runnable activeToolCleanup;

    private void releaseActiveTool() {
        Runnable cleanup = activeToolCleanup;
        activeToolCleanup = null;
        if (cleanup != null) {
            cleanup.run();
        }
    }

    private void clearToolOverlays() {
        cancelPointPick();
        plotAreaView.cancelPlacement();
        plotAreaView.setEditorContent(null);
        plotAreaView.setEditorFills(null, null);
        plotAreaView.setEditorReference(null);
        plotAreaView.setEditorHighlight(null, false);
    }

    private void openToolPanel(String label, Node content) {
        releaseActiveTool();
        setSidebarVisible(true);
        toolTab.setText(label);
        if (!content.getStyleClass().contains("tool-panel")) content.getStyleClass().add("tool-panel");
        ToolPanelIcons.decorate(content, fileName -> legacyIcon(fileName, 16));
        PanelTooltips.install(content, label);
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("tool-panel-scroll");
        scroll.setFitToWidth(true);
        toolTab.setContent(scroll);
        leftTabs.getSelectionModel().select(toolTab);
    }

    /**
     * Restores the Tool tab's placeholder and switches back to Properties -
     * matching ToolIsolation.py/ToolDrilling.py's own
     * app.ui.notebook.setCurrentWidget(app.ui.properties_tab) once a tool
     * finishes (or is closed without finishing).
     */
    private void closeToolPanel() {
        releaseActiveTool();
        toolTab.setText(I18n.text("ui.c188272ba40c"));
        toolTab.setContent(centeredPlaceholder(I18n.text("ui.2bfeb04164a2")));
        leftTabs.getSelectionModel().select(propertiesTab);
    }

    /**
     * A hidden root (the "Projeto" tab title already says that - an explicit
     * "Projeto" row too was redundant) with one category node per object
     * kind, matching appObjects/ObjectCollection.py's grouping. Category
     * nodes are inert; file nodes underneath (added as Gerber/Excellon files
     * are opened, or as Geometry/CNC objects are generated) carry their model
     * in the corresponding item map and get a context menu.
     *
     * <p>Multi-selection (Ctrl/Shift-click, ObjectCollection.py's
     * ExtendedSelection) is enabled: right-clicking with more than one row
     * selected shows a shared "Ativar/Desativar/Remover" menu applying to
     * the whole selection instead of a single object's menu - matching
     * appObjects/ObjectCollection.py's on_menu_request(), which always pops
     * the same menuproject regardless of how many rows are selected, and
     * app_Main.py's on_enable_sel_plots()/on_disable_sel_plots()/on_delete(),
     * which all iterate self.collection.get_selected(). The per-object menu
     * The single-object menu mirrors every legacy action that has a real
     * counterpart in the current object model. Full Gerber/Excellon/Geometry
     * editing remains tied to the future editor subsystem.
     */
    private TreeView<String> buildProjectTree() {
        TreeItem<String> root = new TreeItem<>(I18n.text("ui.4c7877d7558f"));
        root.setExpanded(true);
        gerbersNode = new TreeItem<>("Gerbers");
        gerbersNode.setExpanded(true);
        excellonNode = new TreeItem<>("Excellon");
        excellonNode.setExpanded(true);
        geometryNode = new TreeItem<>("Geometry");
        geometryNode.setExpanded(true);
        cncJobsNode = new TreeItem<>("CNC Jobs");
        cncJobsNode.setExpanded(true);
        root.getChildren().addAll(
                gerbersNode,
                excellonNode,
                geometryNode,
                cncJobsNode
        );

        projectTree = new TreeView<>(root) {
            @Override public void edit(TreeItem<String> item) {
                // Modena also requests editing on a single click of an already-selected row.
                // Only F2/the Rename command may start it; normal edit cancellation stays allowed.
                if (item == null || item == requestedTreeRename) super.edit(item);
            }
        };
        projectTree.setShowRoot(false);
        projectTree.setEditable(true);
        projectTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        projectTree.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> showProperties(selected));
        // Category rows (Gerbers/Excellon/Geometry/CNC Jobs) are just visual grouping, not
        // real objects - they have no Properties panel and no context menu, and shouldn't be
        // selectable at all. A per-row click handler can't fully prevent that: Shift-click or
        // Ctrl+A range-selects across whatever rows fall in between, category headers included,
        // regardless of any click handling on those specific rows - so this reactively drops
        // one the instant it lands in the selection, however it got there.
        //
        // The actual clearSelection() is deferred to the next pulse (Platform.runLater)
        // rather than called synchronously from inside this listener: TreeView's
        // MultipleSelectionModelBase does not tolerate mutating the selection from within
        // its own change notification - doing so corrupts its internal index bookkeeping
        // and throws IndexOutOfBoundsException out of the very next click on ANY row (seen
        // during manual testing: every click on the tree, category or not, stopped doing
        // anything after the first hit). Deferring lets this listener's own change event
        // finish processing first.
        projectTree.getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<String>>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (TreeItem<String> added : change.getAddedSubList()) {
                    if (added != null && !isProjectObject(added)) {
                        Platform.runLater(() -> {
                            int row = projectTree.getRow(added);
                            if (row >= 0) {
                                projectTree.getSelectionModel().clearSelection(row);
                            }
                        });
                    }
                }
            }
            refreshPlotSelectionOutline();
        });
        // Renaming in-place commits by just updating the TreeItem's own value - same as
        // the Properties panel's Name field (see nameRow()), just triggered from the tree.
        projectTree.setOnEditCommit(event -> renameProjectItem(event.getTreeItem(), event.getNewValue()));
        projectTree.setOnKeyPressed(event -> {
            TreeItem<String> selected = projectTree.getSelectionModel().getSelectedItem();
            switch (event.getCode()) {
                // Double-click or Enter on a row switches the sidebar to Properties - matches
                // ObjectCollection.py's on_item_activated()/on_row_activated(), which both call
                // build_ui() then app.ui.notebook.setCurrentWidget(app.ui.properties_tab).
                case ENTER -> {
                    if (selected != null && isProjectObject(selected)) {
                        leftTabs.getSelectionModel().select(propertiesTab);
                    }
                }
                // F2 is the conventional cross-app inline-rename key (Qt's own default
                // "EditKeyPressed" trigger) - kept distinct from double-click/Enter above,
                // which this port already dedicates to "show Properties".
                case F2 -> {
                    if (selected != null && isProjectObject(selected)) {
                        requestTreeRename(selected);
                        event.consume();
                    }
                }
                case DELETE -> {
                    List<TreeItem<String>> selectedItems = projectTree.getSelectionModel().getSelectedItems().stream()
                            .filter(Objects::nonNull).distinct().toList();
                    if (!selectedItems.isEmpty()) {
                        removeSelectionFromProject(selectedItems);
                    }
                }
                // Project-tab keyboard parity: Python toggles each selected object's
                // Plot checkbox with Space and clears selection with Escape.
                case SPACE -> projectTree.getSelectionModel().getSelectedItems().stream()
                        .filter(this::isPlottable).distinct()
                        .forEach(item -> setObjectVisible(item, !isObjectVisible(item)));
                case ESCAPE -> projectTree.getSelectionModel().clearSelection();
                case C -> {
                    if (event.isControlDown()) {
                        copySelection(projectTree.getSelectionModel().getSelectedItems().stream()
                                .filter(Objects::nonNull).distinct().toList());
                        event.consume();
                    }
                }
                default -> {
                }
            }
        });
        projectTree.setCellFactory(view -> {
            // Everything renders as a single graphic Node (never the Cell's own text
            // property) - Labeled's separate text+graphic layout did not vertically
            // center a custom graphic against the text baseline reliably inside a
            // TreeCell. An HBox fully owns its own children's alignment instead.
            TextField editField = new TextField();
            Label textLabel = new Label();
            Tooltip objectTooltip = new Tooltip();
            objectTooltip.setWrapText(true);
            objectTooltip.setMaxWidth(360);
            StackPane iconHolder = new StackPane();
            iconHolder.setAlignment(Pos.CENTER);
            iconHolder.setMinSize(20, 20);
            iconHolder.setPrefSize(20, 20);
            HBox displayBox = new HBox(6, iconHolder, textLabel);
            displayBox.setAlignment(Pos.CENTER_LEFT);

            TreeCell<String> cell = new TreeCell<>() {
                @Override
                public void startEdit() {
                    if (getTreeItem() == null || !isProjectObject(getTreeItem())) {
                        return;
                    }
                    super.startEdit();
                    editField.setText(getItem());
                    setText(null);
                    setGraphic(editField);
                    editField.selectAll();
                    editField.requestFocus();
                }

                @Override
                public void cancelEdit() {
                    super.cancelEdit();
                    refreshDisplay(getItem(), getTreeItem());
                }

                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(null);
                    if (empty || value == null) {
                        setGraphic(null);
                    } else if (isEditing()) {
                        editField.setText(value);
                        setGraphic(editField);
                    } else {
                        refreshDisplay(value, getTreeItem());
                    }
                }

                private void refreshDisplay(String value, TreeItem<String> item) {
                    textLabel.setText(value);
                    boolean isObject = isProjectObject(item);
                    // Category rows (Gerbers/Excellon/Geometry/CNC Jobs) get no icon but do
                    // get a bold label, so they read as group headers rather than objects.
                    // A disabled object's row dims via opacity - ObjectCollection.py's own
                    // data()/Qt.ForegroundRole switches to a fixed "disabled" text color
                    // instead, but a fixed hex risks poor contrast in at least one of this
                    // app's four themes, where opacity adapts automatically.
                    if (!isObject) {
                        textLabel.setStyle("-fx-font-weight: bold;");
                        textLabel.setOpacity(1.0);
                        // Category headers have no icon. Keeping the empty 16 px
                        // iconHolder plus the HBox gap made them look like children of
                        // an invisible extra level, especially while the category was
                        // empty and therefore had no disclosure arrow. Let TreeView's
                        // own disclosure/indent area be their only left indentation.
                        displayBox.getChildren().setAll(textLabel);
                        setTooltip(null);
                    } else {
                        textLabel.setStyle(null);
                        textLabel.setOpacity(isPlottable(item) && !isObjectVisible(item) ? 0.5 : 1.0);
                        displayBox.getChildren().setAll(iconHolder, textLabel);
                        Path sourcePath = sourcePathByItem.get(item);
                        CncJobEntry cncJob = cncJobByItem.get(item);
                        String pathText = sourcePath != null ? sourcePath.toString()
                                : cncJob != null ? cncJob.outputFile().toString() : value;
                        new TooltipContent(List.of(new TooltipContent.Span(pathText + "\n", TooltipContent.Style.NORMAL),
                                new TooltipContent.Span(isObjectVisible(item) ? I18n.text("ui.4ca438f36edb") : I18n.text("ui.8122dd6d7d00"), TooltipContent.Style.ACCENT)))
                                .installNative(objectTooltip, currentTheme);
                        setTooltip(objectTooltip);
                    }
                    Node icon = iconShapeFor(item);
                    iconHolder.getChildren().setAll(icon == null ? List.of() : List.of(icon));
                    setGraphic(displayBox);
                }
            };
            editField.setOnAction(e -> cell.commitEdit(editField.getText()));
            editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused && cell.isEditing()) {
                    cell.commitEdit(editField.getText());
                }
            });
            editField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cell.cancelEdit();
                }
            });
            // An event FILTER on MOUSE_PRESSED, not a MOUSE_CLICKED handler: TreeView.
            // setEditable(true) (needed for F2 rename) also wires the cell's own default
            // double-click-to-edit behavior, which triggers from the cell's own
            // MOUSE_PRESSED handler (before MOUSE_CLICKED is ever dispatched) - a filter
            // on MOUSE_CLICKED ran too late to stop it. Filters run before handlers at
            // the same node, so consuming the press here reliably suppresses it.
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getClickCount() == 2 && isProjectObject(cell.getTreeItem())) {
                    leftTabs.getSelectionModel().select(propertiesTab);
                    event.consume();
                }
            });
            // Populating a ContextMenu's items inside its own setOnShowing (instead of
            // before the initial show() call) left the popup sized to zero the first
            // time - it never appeared. Building the menu synchronously in response to
            // the actual right-click, then calling show() ourselves, avoids that.
            cell.setOnContextMenuRequested(event -> {
                TreeItem<String> item = cell.getTreeItem();
                if (item == null) {
                    return;
                }
                ContextMenu menu = buildContextMenuFor(item);
                if (!menu.getItems().isEmpty()) {
                    if (projectContextMenu != null) {
                        projectContextMenu.hide();
                    }
                    projectContextMenu = menu;
                    menu.setAutoHide(true);
                    menu.setOnHidden(hidden -> {
                        if (projectContextMenu == menu) {
                            projectContextMenu = null;
                        }
                    });
                    // Anchored on projectTree, NOT cell: a selected+focused TreeCell has
                    // its own -fc-selection-text-focused override on -fx-fill (an inherited
                    // CSS property), and a ContextMenu shown via show(Node, ...) inherits
                    // that from whatever node it's anchored on - a menu anchored directly
                    // on a white-selected-text cell renders every item's text white too,
                    // invisible against the popup's own light background. Confirmed
                    // isolated (fresh off-screen Stage, no screen capture): anchoring the
                    // same menu/stylesheets on the TreeView itself instead of the cell
                    // keeps normal legible text, since the TreeView carries no such
                    // override. event.getScreenX()/getScreenY() already give the popup's
                    // exact position, so the anchor node only affects this CSS inheritance
                    // context, not where it appears.
                    fluidTooltips.attachContextMenu(menu);
                    menu.show(projectTree, event.getScreenX(), event.getScreenY());
                }
                event.consume();
            });
            return cell;
        });
        return projectTree;
    }

    /** Project-object selection is active only while no editor has claimed the canvas. */
    private void configurePlotInteractions() {
        plotAreaView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (plotContextMenu != null && plotContextMenu.isShowing()) {
                plotContextMenu.hide();
            }
        });
        plotAreaView.setStepListener(text -> {
            if (cncStepLabel != null) {
                cncStepLabel.setText(text.isEmpty() ? I18n.text("ui.0dd81daf877c") : text);
            }
        });
        plotAreaView.setDefaultSelectionHandler(new PlotAreaView.SelectionHandler() {
            @Override
            public void onClick(double worldX, double worldY, boolean additive) {
                List<PlotObjectSelection.Layer<TreeItem<String>>> layers = selectableObjectsOnPlot();
                TreeItem<String> hit = additive
                        ? PlotObjectSelection.topmostAt(layers, worldX, worldY, plotAreaView.pickToleranceWorld())
                        : PlotObjectSelection.nextAt(layers, worldX, worldY, plotAreaView.pickToleranceWorld(),
                                projectTree.getSelectionModel().getSelectedItem());
                if (hit == null) {
                    if (!additive) {
                        projectTree.getSelectionModel().clearSelection();
                    }
                    return;
                }
                int row = rowForPlotObject(hit);
                if (row < 0) {
                    return;
                }
                if (!additive) {
                    projectTree.getSelectionModel().clearAndSelect(row);
                } else if (projectTree.getSelectionModel().isSelected(row)) {
                    projectTree.getSelectionModel().clearSelection(row);
                } else {
                    projectTree.getSelectionModel().select(row);
                }
                leftTabs.getSelectionModel().select(projectTab);
            }

            @Override
            public void onDoubleClick(double worldX, double worldY) {
                TreeItem<String> hit = PlotObjectSelection.selectedOrTopmostAt(
                        selectableObjectsOnPlot(), worldX, worldY, plotAreaView.pickToleranceWorld(),
                        projectTree.getSelectionModel().getSelectedItem());
                if (hit == null) {
                    return;
                }
                int row = rowForPlotObject(hit);
                if (row < 0) {
                    return;
                }
                projectTree.getSelectionModel().clearAndSelect(row);
                showProperties(hit);
                leftTabs.getSelectionModel().select(propertiesTab);
            }

            @Override
            public void onBox(double pressX, double pressY, double releaseX, double releaseY, boolean additive) {
                List<TreeItem<String>> hits = PlotObjectSelection.inBox(
                        selectableObjectsOnPlot(), pressX, pressY, releaseX, releaseY);
                if (!additive) {
                    projectTree.getSelectionModel().clearSelection();
                }
                for (TreeItem<String> hit : hits) {
                    int row = rowForPlotObject(hit);
                    if (row < 0) {
                        continue;
                    }
                    if (additive && projectTree.getSelectionModel().isSelected(row)) {
                        projectTree.getSelectionModel().clearSelection(row);
                    } else {
                        projectTree.getSelectionModel().select(row);
                    }
                }
                if (!hits.isEmpty()) {
                    leftTabs.getSelectionModel().select(projectTab);
                }
            }
        });
        plotAreaView.setContextRequestHandler(this::showPlotContextMenu);
    }

    private List<PlotObjectSelection.Layer<TreeItem<String>>> selectableObjectsOnPlot() {
        List<PlotObjectSelection.Layer<TreeItem<String>>> result = new ArrayList<>();
        for (PlotAreaView.SelectableLayer layer : plotAreaView.visibleSelectableLayers()) {
            TreeItem<String> owner = ownerOfPlotLayer(layer.key());
            if (owner != null) {
                result.add(new PlotObjectSelection.Layer<>(owner, layer.geometry()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked") // TreeItem layer keys in this window always carry String labels.
    private TreeItem<String> ownerOfPlotLayer(Object key) {
        if (key instanceof CncCutLayerKey cut) {
            return cut.cncJobItem();
        }
        if (key instanceof CncTravelLayerKey travel) {
            return travel.cncJobItem();
        }
        if (key instanceof TreeItem<?> item) {
            TreeItem<String> owner = (TreeItem<String>) item;
            return isProjectObject(owner) ? owner : null;
        }
        return null;
    }

    private void refreshPlotSelectionOutline() {
        Map<TreeItem<String>, Envelope> bounds = PlotObjectSelection.boundsByOwner(selectableObjectsOnPlot());
        List<Envelope> selectedBounds = projectTree.getSelectionModel().getSelectedItems().stream()
                .map(bounds::get).filter(Objects::nonNull).toList();
        plotAreaView.setSelectedObjectBounds(selectedBounds);
    }

    private int rowForPlotObject(TreeItem<String> item) {
        for (TreeItem<String> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
        return projectTree.getRow(item);
    }

    private void showPlotContextMenu(double worldX, double worldY, double screenX, double screenY) {
        if (plotContextMenu != null) {
            plotContextMenu.hide();
        }
        List<TreeItem<String>> underPointer = PlotObjectSelection.at(
                selectableObjectsOnPlot(), worldX, worldY, plotAreaView.pickToleranceWorld());
        TreeItem<String> hit = underPointer.stream()
                .filter(projectTree.getSelectionModel().getSelectedItems()::contains)
                .findFirst().orElseGet(() -> underPointer.isEmpty() ? null : underPointer.get(0));
        if (hit != null) {
            int row = rowForPlotObject(hit);
            if (row >= 0 && !projectTree.getSelectionModel().isSelected(row)) {
                projectTree.getSelectionModel().clearAndSelect(row);
            }
            leftTabs.getSelectionModel().select(projectTab);
            plotContextMenu = buildContextMenuFor(hit);
            addPlotPlacementActions(plotContextMenu, hit, worldX, worldY);
        } else {
            MenuItem fitAll = new MenuItem(I18n.text("ui.db1937d6c29b"));
            setLegacyMenuIcon(fitAll, "zoom_fit32.png");
            fitAll.setOnAction(e -> plotAreaView.fitAllVisible());
            MenuItem clearSelection = new MenuItem(I18n.text("ui.33a042c0fd07"));
            setLegacyMenuIcon(clearSelection, "deselect_all32.png");
            clearSelection.setOnAction(e -> projectTree.getSelectionModel().clearSelection());
            clearSelection.setDisable(!anyProjectObjectSelected());
            plotContextMenu = new ContextMenu(fitAll, clearSelection);
        }
        if (!plotContextMenu.getItems().isEmpty()) {
            plotContextMenu.getStyleClass().add("plot-context-menu");
            plotContextMenu.setAutoHide(true);
            fluidTooltips.attachContextMenu(plotContextMenu);
            plotContextMenu.show(plotAreaView, screenX, screenY);
        }
    }

    private void addPlotPlacementActions(ContextMenu menu, TreeItem<String> hit, double anchorX, double anchorY) {
        List<TreeItem<String>> selected = projectTree.getSelectionModel().getSelectedItems().stream()
                .filter(this::isProjectObject).distinct().toList();
        List<TreeItem<String>> targets = selected.contains(hit) ? selected : List.of(hit);
        boolean available = !targets.isEmpty() && targets.stream().allMatch(this::isPlotPlacementTarget);

        MenuItem move = new MenuItem(I18n.text("ui.c689507a9905"));
        setLegacyMenuIcon(move, "move32.png");
        move.setDisable(!available);
        move.setOnAction(e -> startPlotPlacement(targets, anchorX, anchorY, false));

        MenuItem copy = new MenuItem(I18n.text("ui.5d048f660cef"));
        setLegacyMenuIcon(copy, "copy32.png");
        copy.setDisable(!available);
        copy.setOnAction(e -> startPlotPlacement(targets, anchorX, anchorY, true));

        menu.getItems().addAll(new SeparatorMenuItem(), move, copy);
    }

    private boolean isPlotPlacementTarget(TreeItem<String> item) {
        if (!(gerberByItem.containsKey(item) || excellonByItem.containsKey(item)
                || geometryByItem.containsKey(item))) {
            return false;
        }
        return plotAreaView.visibleSelectableLayers().stream().anyMatch(layer -> layer.key() == item);
    }

    private void startPlotPlacement(List<TreeItem<String>> targets, double anchorX, double anchorY, boolean copy) {
        List<TreeItem<String>> snapshot = List.copyOf(targets);
        if (snapshot.isEmpty() || !snapshot.stream().allMatch(this::isPlotPlacementTarget)) {
            appendConsole(I18n.text("ui.6bd9587443e5"));
            return;
        }
        boolean started = plotAreaView.beginPlacement(snapshot, anchorX, anchorY, new PlotAreaView.PlacementHandler() {
            @Override
            public void onCommit(double dx, double dy) {
                commitPlotPlacement(snapshot, dx, dy, copy);
            }

            @Override
            public void onCancel() {
                setStatus(I18n.text("ui.441792596876"), CANCELLED_COLOR);
            }
        });
        if (started) {
            setStatus((copy ? I18n.text("ui.39dcf1cbeb5a") : I18n.text("ui.6624c0d31888")) + I18n.text("ui.ab76c11c1cba"), RUNNING_COLOR);
            plotAreaView.requestFocus();
        }
    }

    private void commitPlotPlacement(List<TreeItem<String>> sources, double dx, double dy, boolean copy) {
        if (!sources.stream().allMatch(this::isPlotPlacementTarget)) {
            appendConsole(I18n.text("ui.6816eff4a70f"));
            setStatus(I18n.text("ui.441792596876"), CANCELLED_COLOR);
            return;
        }
        if (!copy && dx == 0 && dy == 0) {
            setStatus(I18n.text("ui.c9907d918596"), IDLE_COLOR);
            return;
        }
        TransformOp.Offset offset = new TransformOp.Offset(dx, dy);
        List<TreeItem<String>> placed = new ArrayList<>();
        applyingPlotMove = !copy;
        try {
            for (TreeItem<String> source : sources) {
                TreeItem<String> target = copy ? copyObject(source) : source;
                if (target != null && applyTransformToItem(target, offset)) {
                    placed.add(target);
                }
            }
        } finally {
            applyingPlotMove = false;
        }
        if (!copy && !placed.isEmpty()) {
            plotMoveHistory.record(placed, dx, dy);
        }
        if (copy && !placed.isEmpty()) {
            projectTree.getSelectionModel().clearSelection();
            for (TreeItem<String> item : placed) {
                projectTree.getSelectionModel().select(rowForPlotObject(item));
            }
        }
        appendConsole((copy ? I18n.text("ui.54ca306e891b") : I18n.text("ui.de35a12cc1c4")) + " " + placed.size()
                + I18n.text("ui.5b5cc46af13e") + dx + ", dy=" + dy + ".");
        setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
    }

    private boolean undoPlotMove() {
        PlotMoveHistory.Move<TreeItem<String>> move = plotMoveHistory.undo();
        return applyRecordedPlotMove(move, false);
    }

    private boolean redoPlotMove() {
        PlotMoveHistory.Move<TreeItem<String>> move = plotMoveHistory.redo();
        return applyRecordedPlotMove(move, true);
    }

    private boolean applyRecordedPlotMove(PlotMoveHistory.Move<TreeItem<String>> move, boolean forward) {
        if (move == null) {
            return false;
        }
        if (!move.targets().stream().allMatch(this::isProjectObject)) {
            plotMoveHistory.clear();
            setStatus(I18n.text("ui.f58b78bf1a0b"), CANCELLED_COLOR);
            return true;
        }
        applyingPlotMove = true;
        try {
            TransformOp.Offset offset = new TransformOp.Offset(
                    forward ? move.dx() : -move.dx(), forward ? move.dy() : -move.dy());
            for (TreeItem<String> item : move.targets()) {
                applyTransformToItem(item, offset);
            }
        } finally {
            applyingPlotMove = false;
        }
        setStatus(forward ? I18n.text("ui.e4dc6115d767") : I18n.text("ui.06ce98adf73c"), IDLE_COLOR);
        return true;
    }

    /** Category rows (Gerbers/Excellon/Geometry/CNC Jobs) aren't real objects - only actual Gerber/Excellon/CNC Job items are. */
    private boolean isProjectObject(TreeItem<String> item) {
        return gerberByItem.containsKey(item) || excellonByItem.containsKey(item)
                || geometryByItem.containsKey(item) || cncJobByItem.containsKey(item);
    }

    private boolean anyProjectObjectSelected() {
        return projectTree.getSelectionModel().getSelectedItems().stream().anyMatch(this::isProjectObject);
    }

    /** A small modal prompt for one angle - mirrors Python's FCInputDoubleSpinner quick-action dialogs. Null if cancelled or invalid. */
    private Double promptAngle(String title, String contentText, double defaultValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(defaultValue));
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(contentText);
        dialog.initOwner(scene.getWindow());
        var result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(result.get().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            appendConsole(title + I18n.text("ui.861988921a9a"));
            return null;
        }
    }

    /** {@code [minX, minY, maxX, maxY]} of whatever object kind {@code item} is, or null if it has no geometry. */
    private double[] boundsOf(TreeItem<String> item) {
        GerberImage gerber = gerberByItem.get(item);
        if (gerber != null) {
            return gerber.bounds();
        }
        ExcellonImage excellon = excellonByItem.get(item);
        if (excellon != null) {
            return excellon.bounds();
        }
        GeometryEntry geometry = geometryByItem.get(item);
        if (geometry != null && geometry.geometry() != null && !geometry.geometry().isEmpty()) {
            Envelope envelope = geometry.geometry().getEnvelopeInternal();
            return new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
        }
        return null;
    }

    /** appTools/ToolTransform.py's "Selection" reference: center of the combined bounding box, or Origin if none has geometry. */
    private Coordinate selectionCenterOrOrigin(List<TreeItem<String>> items) {
        List<double[]> boundsList = items.stream().map(this::boundsOf).filter(java.util.Objects::nonNull).toList();
        return boundsList.isEmpty() ? TransformReference.origin() : TransformReference.selectionCenter(boundsList);
    }

    /**
     * appTools/ToolTransform.py's bulk-apply: runs the op (built from the
     * final, filtered selection, so a pivot like "Selection center" is
     * computed from exactly what gets transformed) against every selected
     * Gerber/Excellon/Geometry object in place, replacing each one's own map
     * entry and plot layer. CNC Job objects are refused, same as Python
     * ("CNCJob objects can't be rotated.").
     */
    private void applyTransformToSelection(java.util.function.Function<List<TreeItem<String>>, TransformOp> opFactory) {
        List<TreeItem<String>> selected = projectTree.getSelectionModel().getSelectedItems().stream()
                .filter(this::isProjectObject).toList();
        if (selected.isEmpty()) {
            appendConsole(I18n.text("ui.53775f88b4af"));
            return;
        }
        TransformOp op = opFactory.apply(selected);
        if (op instanceof TransformOp.Buffer buffer) {
            runTransformBuffer(selected, buffer);
            return;
        }
        int applied = 0;
        for (TreeItem<String> item : selected) {
            if (applyTransformToItem(item, op)) {
                applied++;
            }
        }
        if (applied > 0) {
            appendConsole(applied + I18n.text("ui.c58dabadfc80"));
            showProperties(projectTree.getSelectionModel().getSelectedItem());
        }
    }

    // ---- Origin and location (Python Edit > Set Origin / Move to Origin / Jump to) ----

    /** Python on_set_origin: the next plot click becomes (0,0) for every object in the project. */
    private void beginSetOrigin() {
        String refusal = setOriginRefusal(allProjectObjects());
        if (refusal != null) { appendConsole(refusal); return; }
        setStatus(I18n.text("ui.0b22cb384c29"), RUNNING_COLOR);
        beginPointPick(point -> {
            if (point == null) { setStatus(I18n.text("ui.31e14a4fb784"), IDLE_COLOR); return; }
            setOrigin(point.x, point.y);
        });
    }

    private String setOriginRefusal(List<TreeItem<String>> objects) {
        if (runningJob != null) return I18n.text("ui.df12b110e3bb");
        if (anyObjectEditorActive()) return I18n.text("ui.bd7017354c0a");
        if (objects.isEmpty()) return I18n.text("ui.c0cdf51f181b");
        if (objects.stream().anyMatch(cncJobByItem::containsKey))
            return I18n.text("ui.9b31675a6c22");
        return null;
    }

    /** Python on_set_zero_click: every object is offset by (-x, -y), then the view is fitted again. */
    boolean setOrigin(double x, double y) {
        List<TreeItem<String>> objects = allProjectObjects();
        String refusal = setOriginRefusal(objects);
        if (refusal != null) { appendConsole(refusal); return false; }
        if (!Double.isFinite(x) || !Double.isFinite(y)) { appendConsole(I18n.text("ui.ffa3047c234a")); return false; }
        offsetObjects(objects, -x, -y);
        plotAreaView.fitAllVisible();
        appendConsole(String.format(java.util.Locale.ROOT, I18n.text("ui.4aed845eb87f"), x, y));
        setStatus(I18n.text("ui.dfadd620e16b"), IDLE_COLOR);
        return true;
    }

    /** Python on_move2origin: the selection's lower-left corner moves to (0,0). */
    boolean moveSelectionToOrigin() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.isEmpty()) { appendConsole(I18n.text("ui.33e597150197")); return false; }
        if (runningJob != null) { appendConsole(I18n.text("ui.df12b110e3bb")); return false; }
        if (anyObjectEditorActive()) { appendConsole(I18n.text("ui.bd7017354c0a")); return false; }
        if (selected.stream().anyMatch(cncJobByItem::containsKey)) {
            appendConsole(I18n.text("ui.6c3447255b9b"));
            return false;
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        for (TreeItem<String> item : selected) {
            double[] bounds = objectBounds(item);
            if (bounds == null) continue;
            minX = Math.min(minX, bounds[0]);
            minY = Math.min(minY, bounds[1]);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)) {
            appendConsole(I18n.text("ui.42d4c73d4f80"));
            return false;
        }
        if (minX == 0 && minY == 0) { appendConsole(I18n.text("ui.e7884221ff0c")); return true; }
        offsetObjects(selected, -minX, -minY);
        appendConsole(String.format(java.util.Locale.ROOT, I18n.text("ui.85b8af44ca41"),
                selected.size(), -minX, -minY));
        return true;
    }

    private void offsetObjects(List<TreeItem<String>> items, double dx, double dy) {
        TransformOp op = new TransformOp.Offset(dx, dy);
        plotAreaView.beginBatchUpdate();
        try {
            for (TreeItem<String> item : items) applyTransformToItem(item, op);
        } finally {
            plotAreaView.endBatchUpdate();
        }
        TreeItem<String> shown = projectTree.getSelectionModel().getSelectedItem();
        if (shown != null) showProperties(shown);
    }

    private double[] objectBounds(TreeItem<String> item) {
        Geometry geometry = gerberByItem.containsKey(item) ? gerberByItem.get(item).solidGeometry()
                : excellonByItem.containsKey(item) ? excellonByItem.get(item).solidGeometry()
                : geometryByItem.containsKey(item) ? geometryByItem.get(item).geometry() : null;
        if (geometry == null || geometry.isEmpty()) return null;
        Envelope env = geometry.getEnvelopeInternal();
        return new double[]{env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()};
    }

    /** Python on_jump_to: centers the view on an absolute point, or one relative to the last pointer position. */
    void jumpTo(double x, double y, boolean relative) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException(I18n.text("ui.772b24ccd8d1"));
        double[] base = relative ? plotAreaView.lastPointerWorld() : new double[]{0, 0};
        double targetX = base[0] + x, targetY = base[1] + y;
        plotAreaView.centerOn(targetX, targetY);
        setStatus(String.format(java.util.Locale.ROOT, I18n.text("ui.8a8dbe263388"), targetX, targetY), IDLE_COLOR);
    }

    private void askJumpToLocation() {
        if (geometryEditor.requestCoordinates()) return;
        var dialog = jumpLocationDialog();
        RadioButton relative = (RadioButton) dialog.getDialogPane().getContent().lookup("#jump-relative");
        dialog.showAndWait().ifPresent(xy -> {
            AppPreferences.saveText("jump.reference", relative.isSelected() ? "rel" : "abs");
            jumpTo(xy[0], xy[1], relative.isSelected());
        });
    }

    javafx.scene.control.Dialog<double[]> jumpLocationDialog() {
        var dialog = new javafx.scene.control.Dialog<double[]>();
        if (scene != null && scene.getWindow() != null) dialog.initOwner(scene.getWindow());
        dialog.setTitle(I18n.text("ui.e1a40ac7311e"));
        dialog.setHeaderText(null);
        TextField coordinates = new TextField();
        coordinates.setPromptText("X,Y");
        coordinates.setId("jump-coordinates");
        RadioButton absolute = new RadioButton(I18n.text("ui.f15be065b1e3"));
        RadioButton relative = new RadioButton(I18n.text("ui.bd8cadb8c654"));
        absolute.setId("jump-absolute"); relative.setId("jump-relative");
        ToolDescriptions.apply(coordinates, I18n.text("ui.289976344399"),
                I18n.text("ui.ea780ff7f036") + currentDisplayUnits() + I18n.text("ui.96cd3158445f"));
        ToolDescriptions.apply(absolute, I18n.text("ui.1c7832833afc"), I18n.text("ui.6aad695c41e1"));
        ToolDescriptions.apply(relative, I18n.text("ui.ed07552319f4"), I18n.text("ui.74f74ebb5440"));
        ToggleGroup reference = new ToggleGroup();
        absolute.setToggleGroup(reference); relative.setToggleGroup(reference);
        absolute.setSelected(!"rel".equals(AppPreferences.loadText("jump.reference")));
        relative.setSelected(!absolute.isSelected());
        Label error = new Label();
        error.getStyleClass().add("error-label");
        VBox content = new VBox(8, new Label(I18n.text("ui.23e3d71854d8")), coordinates,
                new HBox(12, absolute, relative), error);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (scene != null) dialog.getDialogPane().getScene().getStylesheets().setAll(scene.getStylesheets());
        Node ok = dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (parseXY(coordinates.getText()) == null) {
                error.setText(I18n.text("ui.9db09fc8b7e7"));
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == javafx.scene.control.ButtonType.OK ? parseXY(coordinates.getText()) : null);
        ToolDescriptions.apply(ok, I18n.text("ui.939467b35f12"), I18n.text("ui.7f9e2cf5bca6"));
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL),
                I18n.text("ui.7d79664c272b"), I18n.text("ui.e4f87c82ba98"));
        dialog.setOnShown(event -> javafx.application.Platform.runLater(coordinates::requestFocus));
        FluidTooltips.install(dialog, I18n.text("ui.939467b35f12"));
        return dialog;
    }

    // ---- File > New > Geometry / Excellon (Python AppObject.new_geometry_object / new_excellon_object) ----

    /** An empty Geometry named new_geo (suffixed if taken), selected so it can be edited right away. */
    TreeItem<String> newEmptyGeometryObject() {
        TreeItem<String> item = addGeometryToProject(uniqueDerivedName("new_geo"), "", currentDisplayUnits().toUpperCase(java.util.Locale.ROOT),
                CNC_GEOMETRY_FACTORY.createGeometryCollection(), false);
        selectOnly(item);
        appendConsole(I18n.text("ui.625b7e939e59") + item.getValue() + ".");
        return item;
    }

    /** An empty Excellon named new_exc (suffixed if taken), selected so it can be edited right away. */
    TreeItem<String> newEmptyExcellonObject() {
        ExcellonImage image = ExcellonImage.of(currentDisplayUnits().toUpperCase(java.util.Locale.ROOT), Map.of(),
                List.of(), List.of(), CNC_GEOMETRY_FACTORY.createGeometryCollection());
        TreeItem<String> item = addExcellonToProject(uniqueDerivedName("new_exc"), null, image);
        selectOnly(item);
        appendConsole(I18n.text("ui.40ec157b973c") + item.getValue() + ".");
        return item;
    }

    private void selectOnly(TreeItem<String> item) {
        int row = rowForPlotObject(item);
        if (row >= 0) projectTree.getSelectionModel().clearAndSelect(row);
    }

    // ---- Measurement (Python ToolDistance / ToolDistanceMin) and Locate in Object ----

    private MeasurementToolPanel measurePanel;

    /** Python Distance Tool: two plot clicks, results in the Tool tab, the line drawn as a highlight. */
    void openMeasureTool() {
        MeasurementToolPanel panel = new MeasurementToolPanel(false, currentDisplayUnits(), this::startMeasurement,
                xy -> jumpTo(xy[0], xy[1], false), this::closeToolPanel);
        openToolPanel(I18n.text("ui.475f2d0d401e"), panel); // releases the previous tool before this one becomes current
        measurePanel = panel;
        activeToolCleanup = () -> { clearToolOverlays(); if (measurePanel == panel) measurePanel = null; };
        startMeasurement();
    }

    private void startMeasurement() {
        MeasurementToolPanel panel = measurePanel;
        if (panel == null) return;
        panel.clear();
        plotAreaView.setEditorHighlight(null, false);
        panel.showMessage(I18n.text("ui.8c326faa4523"));
        setStatus(I18n.text("ui.77bfadac5022"), RUNNING_COLOR);
        beginPointPick(first -> {
            if (first == null || measurePanel != panel) { setStatus(I18n.text("ui.41954cfd9e7d"), IDLE_COLOR); return; }
            panel.showStart(first.x, first.y);
            setStatus(I18n.text("ui.b4ceca290c06"), RUNNING_COLOR);
            beginPointPick(second -> {
                if (second == null || measurePanel != panel) { setStatus(I18n.text("ui.41954cfd9e7d"), IDLE_COLOR); return; }
                finishMeasurement(panel, new MeasurementToolPanel.Measurement(first.x, first.y, second.x, second.y));
            });
        });
    }

    private void finishMeasurement(MeasurementToolPanel panel, MeasurementToolPanel.Measurement measurement) {
        panel.showResult(measurement);
        if (measurement.distance() > 0) {
            plotAreaView.setEditorHighlight(CNC_GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                    new Coordinate(measurement.x1(), measurement.y1()), new Coordinate(measurement.x2(), measurement.y2())}), true);
        }
        String summary = panel.summary(measurement);
        appendConsole(summary);
        setStatus(summary, IDLE_COLOR);
    }

    /** Python Minimum Distance Tool: exactly two selected objects, nearest points of their geometry. */
    void openMinimumDistanceTool() {
        MeasurementToolPanel panel = new MeasurementToolPanel(true, currentDisplayUnits(), this::measureMinimumDistance,
                xy -> jumpTo(xy[0], xy[1], false), this::closeToolPanel);
        openToolPanel(I18n.text("ui.61ff53fbc31f"), panel); // releases the previous tool before this one becomes current
        measurePanel = panel;
        activeToolCleanup = () -> { clearToolOverlays(); if (measurePanel == panel) measurePanel = null; };
        measureMinimumDistance();
    }

    MeasurementToolPanel.Measurement measureMinimumDistance() {
        MeasurementToolPanel panel = measurePanel;
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() != 2) {
            String text = I18n.text("ui.684942713175") + selected.size() + ".";
            if (panel != null) { panel.clear(); panel.showMessage(text); }
            appendConsole(text);
            plotAreaView.setEditorHighlight(null, false);
            return null;
        }
        Geometry first = measurableGeometry(selected.get(0)), second = measurableGeometry(selected.get(1));
        if (first == null || second == null) {
            String text = I18n.text("ui.7e680f45ca98");
            if (panel != null) { panel.clear(); panel.showMessage(text); }
            appendConsole(text);
            return null;
        }
        MeasurementToolPanel.Measurement measurement = MeasurementToolPanel.nearest(first, second);
        if (panel != null) panel.showResult(measurement);
        plotAreaView.setEditorHighlight(measurement.distance() > 0 ? CNC_GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                new Coordinate(measurement.x1(), measurement.y1()), new Coordinate(measurement.x2(), measurement.y2())}) : null, true);
        String text = measurement.distance() == 0
                ? I18n.text("ui.6f26658bd2eb")
                : (panel != null ? panel.summary(measurement) : I18n.text("ui.3da10b96c423") + UiNumbers.fixed(measurement.distance(),currentDisplayUnits()));
        appendConsole(text);
        setStatus(text, IDLE_COLOR);
        return measurement;
    }

    private Geometry measurableGeometry(TreeItem<String> item) {
        Geometry geometry = gerberByItem.containsKey(item) ? gerberByItem.get(item).solidGeometry()
                : excellonByItem.containsKey(item) ? excellonByItem.get(item).solidGeometry()
                : geometryByItem.containsKey(item) ? geometryByItem.get(item).geometry()
                : cncJobByItem.containsKey(item) ? cncJobByItem.get(item).cutGeometry() : null;
        return geometry == null || geometry.isEmpty() ? null : geometry;
    }

    private String currentDisplayUnits() {
        String text = unitsLabel == null ? "" : unitsLabel.getText().replace("[", "").replace("]", "").trim();
        return text.isEmpty() ? "mm" : text;
    }

    enum LocatePoint { BOTTOM_LEFT, TOP_LEFT, BOTTOM_RIGHT, TOP_RIGHT, CENTER }

    /** Python on_locate: one corner or the center of the selected object's bounds, centered in the view. */
    double[] locateInObject(TreeItem<String> item, LocatePoint where) {
        Geometry geometry = item == null ? null : measurableGeometry(item);
        if (geometry == null) return null;
        Envelope b = geometry.getEnvelopeInternal();
        double[] point = switch (where) {
            case BOTTOM_LEFT -> new double[]{b.getMinX(), b.getMinY()};
            case TOP_LEFT -> new double[]{b.getMinX(), b.getMaxY()};
            case BOTTOM_RIGHT -> new double[]{b.getMaxX(), b.getMinY()};
            case TOP_RIGHT -> new double[]{b.getMaxX(), b.getMaxY()};
            case CENTER -> new double[]{b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2};
        };
        jumpTo(point[0], point[1], false);
        return point;
    }

    private void askLocateInObject() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (item == null || !isProjectObject(item)) { appendConsole(I18n.text("ui.32a4794184b7")); return; }
        locateObjectDialog().showAndWait().ifPresent(point -> {
            AppPreferences.saveText("locate.point", point.name());
            if (locateInObject(item, point) == null) appendConsole(I18n.text("ui.8c5de8761c29"));
        });
    }

    javafx.scene.control.Dialog<LocatePoint> locateObjectDialog() {
        var dialog = new javafx.scene.control.Dialog<LocatePoint>();
        if (scene != null && scene.getWindow() != null) dialog.initOwner(scene.getWindow());
        dialog.setTitle(I18n.text("ui.e556833d7e75"));
        dialog.setHeaderText(null);
        ToggleGroup group = new ToggleGroup();
        VBox options = new VBox(6);
        LocatePoint remembered;
        try { remembered = LocatePoint.valueOf(AppPreferences.loadText("locate.point")); }
        catch (RuntimeException absent) { remembered = LocatePoint.BOTTOM_LEFT; }
        String[] labels = {I18n.text("ui.74bd046f8134"), I18n.text("ui.f3fa393016ae"), I18n.text("ui.67e7480bdc76"), I18n.text("ui.4cd508015d68"), I18n.text("ui.fed34f0449c2")};
        for (LocatePoint point : LocatePoint.values()) {
            RadioButton radio = new RadioButton(labels[point.ordinal()]);
            radio.setUserData(point); radio.setToggleGroup(group); radio.setSelected(point == remembered);
            ToolDescriptions.apply(radio, labels[point.ordinal()],
                    I18n.text("ui.354f680fe81a") + labels[point.ordinal()].toLowerCase(java.util.Locale.ROOT) + I18n.text("ui.0d0449ed5fd4"));
            options.getChildren().add(radio);
        }
        options.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(options);
        dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (scene != null) dialog.getDialogPane().getScene().getStylesheets().setAll(scene.getStylesheets());
        dialog.setResultConverter(button -> button == javafx.scene.control.ButtonType.OK && group.getSelectedToggle() != null
                ? (LocatePoint) group.getSelectedToggle().getUserData() : null);
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK),
                I18n.text("ui.430484a2c23f"), I18n.text("ui.8b770ff292d7"));
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL),
                I18n.text("ui.5ac39e38dd26"), I18n.text("ui.5daf721e832b"));
        FluidTooltips.install(dialog, I18n.text("ui.430484a2c23f"));
        return dialog;
    }

    /** "x,y" or "(x,y)" with two finite numbers; no expression evaluation (Python uses eval). */
    static double[] parseXY(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.startsWith("(") && value.endsWith(")")) value = value.substring(1, value.length() - 1);
        String[] parts = value.split(",", -1);
        if (parts.length != 2) return null;
        try {
            double x = Double.parseDouble(parts[0].trim()), y = Double.parseDouble(parts[1].trim());
            return Double.isFinite(x) && Double.isFinite(y) ? new double[]{x, y} : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** The next plot click goes to this callback (null point = cancelled), see {@link #beginPointPick}. */
    private Consumer<Coordinate> pendingPointPick;

    private void beginPointPick(Consumer<Coordinate> onPoint) {
        cancelPointPick();
        pendingPointPick = onPoint;
        plotAreaView.setSelectionHandler(new PlotAreaView.SelectionHandler() {
            @Override
            public void onClick(double worldX, double worldY, boolean additive) {
                Consumer<Coordinate> callback = pendingPointPick;
                pendingPointPick = null;
                plotAreaView.setSelectionHandler(null);
                if (callback != null) {
                    callback.accept(new Coordinate(worldX, worldY));
                }
            }

            @Override
            public void onBox(double pressX, double pressY, double releaseX, double releaseY, boolean additive) {
                onClick(releaseX, releaseY, additive);
            }
        });
    }

    private void cancelPointPick() {
        if (pendingPointPick != null) {
            Consumer<Coordinate> callback = pendingPointPick;
            pendingPointPick = null;
            plotAreaView.setSelectionHandler(null);
            callback.accept(null);
        }
    }

    /** What a panelize job hands back to the UI thread: exactly one of the three objects is set. */
    private record PanelizeOutcome(GerberImage gerber, ExcellonImage excellon, GeometryJoin.Joined geometry,
                                   String units, org.flatcam.cam.gcode.MachiningTarget target) {
        private PanelizeOutcome(GerberImage gerber,ExcellonImage excellon,GeometryJoin.Joined geometry,String units) {
            this(gerber,excellon,geometry,units,null);
        }
    }

    private static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Tools > Fiducials Tool: alignment marks on a copper Gerber (appTools/ToolFiducials.py). */
    private void openFiducialsTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.c4f373ae7d5b"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Fiducials Tool", FiducialsToolPanel.build(new FiducialsToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public double[] bounds(TreeItem<String> item) {
                return boundsOf(item);
            }

            @Override
            public String add(TreeItem<String> item, List<Coordinate> points, Fiducials.Type type, double size,
                              double thickness) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    GerberImage result = Fiducials.add(source, points, type, size, thickness);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(withoutExtension(item.getValue()) + "_fid"),
                            null, result);
                    appendConsole(I18n.text("ui.cb581d437c6c") + created.getValue() + " (" + points.size() + I18n.text("ui.b9ceb0cdbacd"));
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }

            @Override
            public String addOpenings(TreeItem<String> item, List<Coordinate> points, double diameter) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    GerberImage result = Fiducials.add(source, points, Fiducials.Type.CIRCULAR, diameter, 0);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(withoutExtension(item.getValue()) + "_fid"),
                            null, result);
                    appendConsole(I18n.text("ui.0f2d5635d795") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }
        }, this::closeToolPanel));
    }

    /** Tools > Calibration Tool: four points, verification G-code, scale/skew factors (appTools/ToolCalibration.py). */
    private void openCalibrationTool() {
        List<TreeItem<String>> sources = new ArrayList<>(gerbersNode.getChildren());
        sources.addAll(excellonNode.getChildren());
        sources.removeIf(item -> !gerberByItem.containsKey(item) && !excellonByItem.containsKey(item));
        List<TreeItem<String>> everything = new ArrayList<>(sources);
        everything.addAll(geometryNode.getChildren());
        everything.removeIf(item -> !gerberByItem.containsKey(item) && !excellonByItem.containsKey(item)
                && !geometryByItem.containsKey(item));
        if (everything.isEmpty()) {
            appendConsole(I18n.text("ui.cd5fa12b7020"));
            return;
        }
        org.locationtech.jts.geom.GeometryFactory factory = new org.locationtech.jts.geom.GeometryFactory();
        openToolPanel("Calibration Tool", CalibrationToolPanel.build(new CalibrationToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbersAndDrills() {
                return sources;
            }

            @Override
            public List<TreeItem<String>> allObjects() {
                return everything;
            }

            @Override
            public boolean inches() {
                return unitsLabel.getText().toLowerCase(java.util.Locale.ROOT).contains("in");
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }

            @Override
            public Coordinate snap(TreeItem<String> source, Coordinate click) {
                GerberImage gerber = source == null ? null : gerberByItem.get(source);
                if (gerber != null) {
                    return Calibration.snap(gerber, click);
                }
                ExcellonImage excellon = source == null ? null : excellonByItem.get(source);
                return excellon == null ? null : Calibration.snap(excellon, click);
            }

            @Override
            public void showPoints(double[][] points, int count) {
                if (count <= 0) {
                    plotAreaView.setEditorHighlight(null, false);
                    return;
                }
                double radius = 8 * plotAreaView.worldPerPixel();
                List<org.locationtech.jts.geom.Geometry> marks = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    marks.add(factory.createPoint(new Coordinate(points[i][0], points[i][1])).buffer(radius, 16)
                            .getBoundary());
                }
                plotAreaView.setEditorHighlight(factory.buildGeometry(marks), true);
            }

            @Override
            public String saveGCode(String gcode) {
                return saveReviewedProgram(I18n.text("ui.3d08edbaa09d"), "fc_ver_gcode.nc",
                        () -> new PendingCncProgram(gcode,null,
                                I18n.text("ui.78597008f7ee"), path -> {}),
                        () -> true, message -> appendConsole("Calibration: " + message))
                        ? null : I18n.text("ui.6288fa16f055");
            }

            @Override
            public String calibrate(TreeItem<String> item, Calibration.Factors factors, Coordinate origin) {
                List<TransformOp> operations = Calibration.operations(factors, origin);
                String name = item.getValue() + "_calibrated";
                try {
                    TreeItem<String> created;
                    GerberImage gerber = gerberByItem.get(item);
                    ExcellonImage excellon = excellonByItem.get(item);
                    GeometryEntry geometry = geometryByItem.get(item);
                    if (gerber != null) {
                        GerberImage result = gerber;
                        for (TransformOp op : operations) {
                            result = result.transformed(op);
                        }
                        created = addGerberToProject(uniqueDerivedName(name), null, result);
                    } else if (excellon != null) {
                        ExcellonImage result = excellon;
                        for (TransformOp op : operations) {
                            result = result.transformed(op);
                        }
                        created = addExcellonToProject(uniqueDerivedName(name), null, result);
                    } else if (geometry != null) {
                        Geometry result = geometry.geometry();
                        List<ToolGeometry> tools = geometry.tools();
                        for (TransformOp op : operations) {
                            result = op.apply(result);
                            tools = tools.stream().map(t -> t.transformed(op)).toList();
                        }
                        created = addGeometryToProject(uniqueDerivedName(name), geometry.sourceName(), geometry.units(),
                                result, geometry.strokeOnly(), tools, geometry.cncDefaults());
                    } else {
                        return I18n.text("ui.0d061133f193");
                    }
                    appendConsole(I18n.text("ui.92d3046ded98") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (RuntimeException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
        activeToolCleanup = this::clearToolOverlays;
    }

    /** The last thieving and robber bar made by the Copper Thieving tool, for its pattern plating mask. */
    private List<org.locationtech.jts.geom.Polygon> lastThieving = List.of();
    private CopperThieving.Robber lastRobber;

    /** Tools > Copper Thieving Tool: thieving, robber bar and plating mask (appTools/ToolCopperThieving.py). */
    private void openCopperThievingTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.b2de7220729f"));
            return;
        }
        List<TreeItem<String>> references = new ArrayList<>(gerbers);
        references.addAll(geometryNode.getChildren());
        references.removeIf(item -> !gerberByItem.containsKey(item) && !geometryByItem.containsKey(item));
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        lastThieving = List.of();
        lastRobber = null;
        org.locationtech.jts.geom.GeometryFactory factory = new org.locationtech.jts.geom.GeometryFactory();
        openToolPanel("Copper Thieving Tool", CopperThievingToolPanel.build(new CopperThievingToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public List<TreeItem<String>> references() {
                return references;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public void thieve(TreeItem<String> item, TreeItem<String> reference, List<double[]> zones,
                               CopperThieving.Options options, Consumer<String> onDone) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    onDone.accept(I18n.text("ui.4fb8642a3de5"));
                    return;
                }
                if (runningJob != null) {
                    onDone.accept(I18n.text("ui.47c22bbc729c"));
                    return;
                }
                org.locationtech.jts.geom.Geometry referenceGeometry = null;
                boolean referenceIsGerber = false;
                if (options.reference() == CopperThieving.Reference.AREA) {
                    List<org.locationtech.jts.geom.Geometry> rectangles = new ArrayList<>();
                    for (double[] zone : zones) {
                        rectangles.add(factory.toGeometry(new Envelope(zone[0], zone[2], zone[1], zone[3])));
                    }
                    referenceGeometry = factory.buildGeometry(rectangles);
                } else if (options.reference() == CopperThieving.Reference.BOX && reference != null) {
                    GerberImage referenceGerber = gerberByItem.get(reference);
                    GeometryEntry referenceEntry = geometryByItem.get(reference);
                    referenceIsGerber = referenceGerber != null;
                    referenceGeometry = referenceGerber != null ? referenceGerber.solidGeometry()
                            : referenceEntry == null ? null : referenceEntry.geometry();
                }
                org.locationtech.jts.geom.Geometry fixedReference = referenceGeometry;
                boolean fixedGerber = referenceIsGerber;
                beginJob(I18n.text("ui.b1f8de03a298"));
                JobHandle<List<org.locationtech.jts.geom.Polygon>> handle = jobExecutor.submit(context ->
                        CopperThieving.thieve(source.solidGeometry(), fixedReference, fixedGerber, options,
                                context::isCancelled,
                                fraction -> context.reportProgress(fraction, "Copper thieving...")),
                        (fraction, message) -> Platform.runLater(() -> {
                            updateProgress(fraction);
                            statusLabel.setText(message);
                        }));
                runningJob = handle;
                handle.completion().thenAccept(thieving -> Platform.runLater(() -> {
                    updateProgress(1);
                    onJobFinished();
                    lastThieving = thieving;
                    GerberImage result = CopperThieving.withThieving(source, thieving);
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_thief"), null, result);
                    setStatus(I18n.text("ui.bc15daf5a1cd"), IDLE_COLOR);
                    appendConsole("Copper Thieving: " + thieving.size() + I18n.text("ui.17656af0cd26") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    onDone.accept(null);
                })).exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        onJobFinished();
                        onDone.accept(isCancellation(error) ? "Cancelado." : cause.getMessage());
                    });
                    return null;
                });
            }

            @Override
            public String robberBar(TreeItem<String> item, double margin, double thickness) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    CopperThieving.Robber robber = CopperThieving.robberBar(source.solidGeometry(), margin, thickness);
                    lastRobber = robber;
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_robber"), null,
                            CopperThieving.withRobber(source, robber));
                    appendConsole(I18n.text("ui.8c61571e8b78") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }

            @Override
            public Object[] platingMask(TreeItem<String> item, double clearance, CopperThieving.Plating choice,
                                        double robberThickness) {
                GerberImage mask = gerberByItem.get(item);
                if (mask == null) {
                    return new Object[] {0.0, I18n.text("ui.4fb8642a3de5")};
                }
                try {
                    CopperThieving.PlatingMask result = CopperThieving.platingMask(mask, clearance, lastThieving,
                            lastRobber, choice);
                    String stem = withoutExtension(item.getValue());
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(stem + "_plating_mask"), null,
                            result.image());
                    appendConsole(String.format(java.util.Locale.ROOT,
                            I18n.text("ui.9c0627d23d62"), created.getValue(),
                            result.platedArea()));
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return new Object[] {result.platedArea(), null};
                } catch (IllegalArgumentException failed) {
                    return new Object[] {0.0, failed.getMessage()};
                }
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }

            @Override
            public void showZones(List<double[]> zones) {
                if (zones.isEmpty()) {
                    plotAreaView.setEditorHighlight(null, false);
                    return;
                }
                List<org.locationtech.jts.geom.Geometry> rectangles = new ArrayList<>();
                for (double[] zone : zones) {
                    rectangles.add(factory.toGeometry(new Envelope(zone[0], zone[2], zone[1], zone[3])).getBoundary());
                }
                plotAreaView.setEditorHighlight(factory.buildGeometry(rectangles), true);
            }
        }, this::closeToolPanel));
        activeToolCleanup = this::clearToolOverlays;
    }

    /** Tools > Rules Check Tool: design rules over the board's layers (appTools/ToolRulesCheck.py). */
    private void openRulesCheckTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        List<TreeItem<String>> excellons = new ArrayList<>(excellonByItem.keySet());
        if (gerbers.isEmpty() && excellons.isEmpty()) {
            appendConsole(I18n.text("ui.24e5404dd28c"));
            return;
        }
        openToolPanel("Rules Check Tool", RulesCheckToolPanel.build(new RulesCheckToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public List<TreeItem<String>> excellons() {
                return excellons;
            }

            @Override
            public void check(RulesCheckToolPanel.Selection selection, Map<RulesCheck.Rule, RulesCheck.Setting> settings,
                              Consumer<List<RulesCheck.RuleResult>> onResult, Consumer<String> onError) {
                if (runningJob != null) {
                    onError.accept(I18n.text("ui.47c22bbc729c"));
                    return;
                }
                RulesCheck.Board board = new RulesCheck.Board(named(selection.copperTop()), named(selection.copperBottom()),
                        named(selection.silkTop()), named(selection.silkBottom()), named(selection.maskTop()),
                        named(selection.maskBottom()), named(selection.outline()), namedDrills(selection.drills1()),
                        namedDrills(selection.drills2()));
                beginJob(I18n.text("ui.c05cdfe832ea"));
                JobHandle<List<RulesCheck.RuleResult>> handle = jobExecutor.submit(context ->
                        RulesCheck.check(board, settings, context::isCancelled,
                                fraction -> context.reportProgress(fraction, I18n.text("ui.7adc4b968c08"))),
                        (fraction, message) -> Platform.runLater(() -> {
                            updateProgress(fraction);
                            statusLabel.setText(message);
                        }));
                runningJob = handle;
                handle.completion().thenAccept(results -> Platform.runLater(() -> {
                    updateProgress(1);
                    setStatus(I18n.text("ui.7f934c69885b"), IDLE_COLOR);
                    onJobFinished();
                    for (RulesCheck.RuleResult result : results) {
                        appendConsole("Rules Check: " + result.title() + " - " + (!result.ran() ? I18n.text("ui.758d6ad84b25")
                                + result.error() + ")" : result.failed() ? I18n.text("ui.beb22b19c0ce") : "OK"));
                    }
                    onResult.accept(results);
                })).exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        onJobFinished();
                        onError.accept(isCancellation(error) ? "Cancelado." : cause.getMessage());
                    });
                    return null;
                });
            }

            @Override
            public void locate(List<Coordinate> all, Coordinate focus) {
                if (all == null) {
                    plotAreaView.setEditorHighlight(null, false);
                    return;
                }
                org.locationtech.jts.geom.GeometryFactory factory = new org.locationtech.jts.geom.GeometryFactory();
                double radius = 8 * plotAreaView.worldPerPixel();
                List<org.locationtech.jts.geom.Geometry> rings = new ArrayList<>();
                for (Coordinate point : all) {
                    double size = point == focus ? radius * 1.8 : radius;
                    rings.add(factory.createPoint(point).buffer(size, 16).getBoundary());
                }
                plotAreaView.setEditorHighlight(factory.buildGeometry(rings), true);
                if (focus != null) {
                    plotAreaView.centerOn(focus.x, focus.y);
                }
            }

            private RulesCheck.Named<GerberImage> named(TreeItem<String> item) {
                GerberImage image = item == null ? null : gerberByItem.get(item);
                return image == null ? null : new RulesCheck.Named<>(item.getValue(), image);
            }

            private RulesCheck.Named<ExcellonImage> namedDrills(TreeItem<String> item) {
                ExcellonImage image = item == null ? null : excellonByItem.get(item);
                return image == null ? null : new RulesCheck.Named<>(item.getValue(), image);
            }
        }, this::closeToolPanel));
        activeToolCleanup = this::clearToolOverlays;
    }

    /** Tools > Optimal Tool: the smallest gap between the copper features of a Gerber (appTools/ToolOptimal.py). */
    private void openOptimalTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.5ffedd63843a"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Optimal Tool", OptimalToolPanel.build(new OptimalToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public void find(TreeItem<String> item, int precision, Consumer<MinimumDistance.Result> onResult,
                             Consumer<String> onError) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    onError.accept(I18n.text("ui.4fb8642a3de5"));
                    return;
                }
                if (runningJob != null) {
                    onError.accept(I18n.text("ui.47c22bbc729c"));
                    return;
                }
                beginJob(I18n.text("ui.7d1aa2be17bb"));
                JobHandle<MinimumDistance.Result> handle = jobExecutor.submit(context ->
                        MinimumDistance.find(source.solidGeometry(), precision, context::isCancelled,
                                fraction -> context.reportProgress(fraction, I18n.text("ui.5e6727ca7ddc"))),
                        (fraction, message) -> Platform.runLater(() -> {
                            updateProgress(fraction);
                            statusLabel.setText(message);
                        }));
                runningJob = handle;
                handle.completion().thenAccept(result -> Platform.runLater(() -> {
                    updateProgress(1);
                    setStatus(I18n.text("ui.fcf1a9662c9f"), IDLE_COLOR);
                    onJobFinished();
                    appendConsole(String.format(java.util.Locale.ROOT,
                            I18n.text("ui.3cefca84f777") + precision + I18n.text("ui.336812b8f8eb"),
                            result.minimum(), result.frequency(), result.features()));
                    onResult.accept(result);
                })).exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        onJobFinished();
                        onError.accept(isCancellation(error) ? "Cancelado." : cause.getMessage());
                    });
                    return null;
                });
            }

            @Override
            public void locate(MinimumDistance.Pair pair) {
                if (pair == null) {
                    plotAreaView.setEditorHighlight(null, false);
                    return;
                }
                Coordinate middle = pair.middle();
                double radius = Math.max(pair.first().distance(pair.second()), 14 * plotAreaView.worldPerPixel());
                org.locationtech.jts.geom.GeometryFactory factory = new org.locationtech.jts.geom.GeometryFactory();
                plotAreaView.setEditorHighlight(factory.buildGeometry(List.of(
                        factory.createPoint(middle).buffer(radius, 24).getBoundary(),
                        MinimumDistance.segment(pair))), true);
                plotAreaView.centerOn(middle.x, middle.y);
            }
        }, this::closeToolPanel));
        activeToolCleanup = this::clearToolOverlays;
    }

    /** Tools > Align Objects Tool: align a Gerber/Excellon to another by clicking pads or drills (appTools/ToolAlignObjects.py). */
    private void openAlignObjectsTool() {
        List<TreeItem<String>> objects = new ArrayList<>(gerbersNode.getChildren());
        objects.addAll(excellonNode.getChildren());
        objects.removeIf(item -> !gerberByItem.containsKey(item) && !excellonByItem.containsKey(item));
        if (objects.size() < 2) {
            appendConsole(I18n.text("ui.447477c2609b"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(objects::contains).findFirst().orElse(null);
        openToolPanel("Align Objects Tool", AlignObjectsToolPanel.build(new AlignObjectsToolPanel.Host() {
            @Override
            public List<TreeItem<String>> objects() {
                return objects;
            }

            @Override
            public TreeItem<String> initialObject() {
                return initial;
            }

            @Override
            public Coordinate centerAt(TreeItem<String> object, Coordinate click) {
                GerberImage gerber = gerberByItem.get(object);
                if (gerber != null) {
                    return AlignObjects.padCenterAt(gerber, click);
                }
                ExcellonImage excellon = excellonByItem.get(object);
                return excellon == null ? null
                        : AlignObjects.drillCenterAt(excellon, click, 6 * plotAreaView.worldPerPixel());
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }

            @Override
            public String align(TreeItem<String> aligned, List<Coordinate> points) {
                try {
                    List<TransformOp> operations = AlignObjects.plan(points);
                    boolean ok = true;
                    for (TransformOp operation : operations) {
                        ok &= applyTransformToItem(aligned, operation);
                    }
                    if (!ok) {
                        return I18n.text("ui.caa8747b55c6") + aligned.getValue();
                    }
                    appendConsole(I18n.text("ui.aff6a49258e9") + aligned.getValue() + " ("
                            + (operations.size() == 1 ? I18n.text("ui.26bab876eb07") : I18n.text("ui.2511a23cfe46")) + ").");
                    selectProjectItem(aligned);
                    projectTree.refresh();
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > SolderPaste Tool: dispensing paths and G-code for a paste mask (appTools/ToolSolderPaste.py). */
    private void openSolderPasteTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.1bb1cbb2fe00"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("SolderPaste Tool", SolderPasteToolPanel.build(new SolderPasteToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public List<TreeItem<String>> pasteGeometries() {
                List<TreeItem<String>> found = new ArrayList<>();
                for (TreeItem<String> item : geometryNode.getChildren()) {
                    GeometryEntry entry = geometryByItem.get(item);
                    if (entry != null && !entry.tools().isEmpty()) {
                        found.add(item);
                    }
                }
                return found;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public String createGeometry(TreeItem<String> item, List<Double> nozzles) {
                GerberImage source = gerberByItem.get(item);
                if (source == null || source.solidGeometry() == null) {
                    return I18n.text("ui.4ce10e94fe7e");
                }
                try {
                    SolderPaste.Generated generated = SolderPaste.generateGeometry(source.solidGeometry(), nozzles,
                            source.units());
                    List<Geometry> all = new ArrayList<>();
                    generated.tools().forEach(tool -> all.add(tool.geometry()));
                    Geometry combined = source.solidGeometry().getFactory().buildGeometry(all);
                    TreeItem<String> created = addGeometryToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_solderpaste"), item.getValue(),
                            source.units(), combined, true, generated.tools());
                    appendConsole(I18n.text("ui.258b9976a9d2") + created.getValue() + " ("
                            + (generated.pads() - generated.unserved()) + I18n.text("ui.553c27d45c0c") + generated.pads() + " pads).");
                    if (generated.unserved() > 0) {
                        appendConsole(I18n.text("ui.3e9a08c5414d") + generated.unserved() + I18n.text("ui.ebf4b3d46ee9"));
                    }
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }

            @Override
            public String createJob(TreeItem<String> item, SolderPaste.Parameters parameters) {
                GeometryEntry entry = geometryByItem.get(item);
                if (entry == null || entry.tools().isEmpty()) {
                    return I18n.text("ui.ac93f13c0a01");
                }
                String sourceName = item.getValue();
                return saveReviewedProgram("SolderPaste — " + sourceName, sourceName + "_cnc_solderpaste.nc", () -> {
                    Envelope box = entry.geometry().getEnvelopeInternal();
                    SolderPaste.Program program = SolderPaste.generateGCode(entry.tools(), parameters, entry.units(),
                            new double[] {box.getMinX(), box.getMinY(), box.getMaxX(), box.getMaxY()}, TclExecution.cancellation());
                    TclExecution.cancellation().throwIfCancellationRequested();
                    org.locationtech.jts.geom.GeometryFactory factory = entry.geometry().getFactory();
                    List<Geometry> travel = new ArrayList<>();
                    org.locationtech.jts.geom.Coordinate previous = new org.locationtech.jts.geom.Coordinate(0, 0);
                    for (org.locationtech.jts.geom.LineString path : program.paths()) {
                        TclExecution.cancellation().throwIfCancellationRequested();
                        org.locationtech.jts.geom.Coordinate start = path.getCoordinateN(0);
                        if (previous.distance(start) > 1e-9) {
                            travel.add(factory.createLineString(
                                    new org.locationtech.jts.geom.Coordinate[] {previous, start}));
                        }
                        previous = path.getCoordinateN(path.getNumPoints() - 1);
                    }
                    Geometry travelLines = factory.buildGeometry(travel);
                    Geometry cutLines = factory.buildGeometry(new ArrayList<Geometry>(program.paths()));
                    double thinnest = entry.tools().stream().mapToDouble(ToolGeometry::toolDiameter).min().orElse(0);
                    List<Geometry> bodies = new ArrayList<>();
                    for (ToolGeometry tool : entry.tools()) {
                        TclExecution.cancellation().throwIfCancellationRequested();
                        bodies.add(tool.geometry().buffer(tool.toolDiameter() / 2, 8));
                    }
                    Geometry cutBodies = factory.buildGeometry(bodies);
                    return new PendingCncProgram(program.gcode(), null,
                            I18n.text("ui.1081e64a4d16"), destination -> {
                                TreeItem<String> created = addCncJobToProject(destination.getFileName().toString(), sourceName,
                                        destination, program.gcode(), travelLines, cutBodies, travelLines, cutLines, thinnest);
                                appendConsole(I18n.text("ui.2676ae143ffd") + destination + " (" + program.paths().size() + I18n.text("ui.f54cea355c2c"));
                                selectProjectItem(created);
                            });
                }, () -> geometryByItem.get(item) == entry && Objects.equals(item.getValue(),sourceName),
                        message -> appendConsole("SolderPaste: " + message)) ? null : I18n.text("ui.6288fa16f055");
            }
        }, this::closeToolPanel));
    }

    /** Tools > QRCode Tool: a QR code of copper squares on a Gerber (appTools/ToolQRCode.py). */
    private void openQrCodeTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.21e308994420"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("QRCode Tool", QrCodeToolPanel.build(new QrCodeToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }

            @Override
            public String place(TreeItem<String> item, QrCodeMarker.Options options, Coordinate centre) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    GerberImage result = QrCodeMarker.place(source, options, centre);
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_qrcode"), null, result);
                    appendConsole(I18n.text("ui.f98f5a5c00c8") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > Corner Markers Tool: markers and drills at the corners of a Gerber (appTools/ToolCorners.py). */
    private void openCornerMarkersTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.46faa7a2a36d"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Corner Markers Tool", CornerMarkersToolPanel.build(new CornerMarkersToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public String addMarkers(TreeItem<String> item, Set<CornerMarkers.Corner> corners,
                                     CornerMarkers.Style style, double thickness, double length, double margin) {
                GerberImage source = gerberByItem.get(item);
                double[] bounds = boundsOf(item);
                if (source == null || bounds == null) {
                    return I18n.text("ui.4ce10e94fe7e");
                }
                try {
                    GerberImage result = CornerMarkers.add(source, bounds, corners, style, thickness, length, margin);
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_corners"), null, result);
                    appendConsole(I18n.text("ui.0d485d11f1fe") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }

            @Override
            public String addDrills(TreeItem<String> item, Set<CornerMarkers.Corner> corners, double thickness,
                                    double margin, double diameter) {
                GerberImage source = gerberByItem.get(item);
                double[] bounds = boundsOf(item);
                if (source == null || bounds == null) {
                    return I18n.text("ui.4ce10e94fe7e");
                }
                try {
                    ExcellonImage drills = CornerMarkers.drills(source.units(), bounds, corners, thickness, margin,
                            diameter);
                    TreeItem<String> created = addExcellonToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_corner_drills"), null, drills);
                    appendConsole(I18n.text("ui.11ccdf96b38d") + created.getValue() + ".");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > Film Tool: a printable SVG / PNG / PDF film of a Gerber or Geometry (appTools/ToolFilm.py). */
    private void openFilmTool() {
        List<TreeItem<String>> films = new ArrayList<>(gerbersNode.getChildren());
        films.addAll(geometryNode.getChildren());
        films.removeIf(item -> !gerberByItem.containsKey(item) && !geometryByItem.containsKey(item));
        if (films.isEmpty()) {
            appendConsole(I18n.text("ui.bb8b0d4869f2"));
            return;
        }
        List<TreeItem<String>> boxes = new ArrayList<>(films);
        boxes.addAll(excellonNode.getChildren());
        boxes.removeIf(item -> !gerberByItem.containsKey(item) && !geometryByItem.containsKey(item)
                && !excellonByItem.containsKey(item));
        List<TreeItem<String>> excellons = new ArrayList<>(excellonNode.getChildren());
        excellons.removeIf(item -> !excellonByItem.containsKey(item));
        TreeItem<String> initial = selectedObjects().stream().filter(films::contains).findFirst().orElse(null);
        openToolPanel("Film Tool", FilmToolPanel.build(new FilmToolPanel.Host() {
            @Override
            public List<TreeItem<String>> filmObjects() {
                return films;
            }

            @Override
            public List<TreeItem<String>> boxObjects() {
                return boxes;
            }

            @Override
            public List<TreeItem<String>> excellons() {
                return excellons;
            }

            @Override
            public TreeItem<String> initialFilm() {
                return initial;
            }

            @Override
            public String unitsOf(TreeItem<String> item) {
                if (gerberByItem.containsKey(item)) {
                    return gerberByItem.get(item).units();
                }
                GeometryEntry entry = geometryByItem.get(item);
                return entry == null ? "MM" : entry.units();
            }

            @Override
            public String export(FilmToolPanel.Request request) {
                if (runningJob != null) {
                    return I18n.text("ui.47c22bbc729c");
                }
                org.locationtech.jts.geom.Geometry filmGeometry = filmGeometryOf(request.film());
                org.locationtech.jts.geom.Geometry boxGeometry = filmGeometryOf(request.box());
                if (filmGeometry == null || boxGeometry == null) {
                    return I18n.text("ui.7fcb69591191");
                }
                GerberImage punched = null;
                try {
                    if (request.punch() != FilmToolPanel.Punch.NONE) {
                        GerberImage source = gerberByItem.get(request.film());
                        if (source == null) {
                            return I18n.text("ui.75f12531f0b2");
                        }
                        Set<String> codes = new java.util.HashSet<>(source.apertures().keySet());
                        if (request.punch() == FilmToolPanel.Punch.EXCELLON) {
                            ExcellonImage drills = excellonByItem.get(request.excellon());
                            if (drills == null) {
                                return I18n.text("ui.ba2e22a38d88");
                            }
                            punched = Punch.byExcellon(source, drills, codes);
                        } else {
                            punched = Punch.bySize(source, new ExtractDrills.Options(ExtractDrills.Mode.FIXED,
                                    request.padSize(), 0.5, 0.2, 0.2, 0.2, 0.2, 0.2, true, true, true, true, true),
                                    codes);
                        }
                        filmGeometry = punched.solidGeometry();
                    }
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
                FilmExporter.Options options = request.options();
                String extension = "." + options.fileType().name().toLowerCase(java.util.Locale.ROOT);
                String kind = options.fileType().name();
                File destination = chooseExportFile(I18n.text("ui.16ec34b3465b") + kind, request.film(), "_film" + extension,
                        new FileChooser.ExtensionFilter(kind, "*" + extension));
                if (destination == null) {
                    return null;
                }
                String units = unitsOf(request.film());
                org.locationtech.jts.geom.Geometry finalFilm = filmGeometry;
                runExport(request.film().getValue(), I18n.text("ui.2742bc2e396f") + kind, destination,
                        path -> FilmExporter.write(finalFilm, boxGeometry, units, options, path));
                return null;
            }
        }, this::closeToolPanel));
    }

    private org.locationtech.jts.geom.Geometry filmGeometryOf(TreeItem<String> item) {
        if (gerberByItem.containsKey(item)) {
            return gerberByItem.get(item).solidGeometry();
        }
        if (excellonByItem.containsKey(item)) {
            return excellonByItem.get(item).solidGeometry();
        }
        GeometryEntry entry = geometryByItem.get(item);
        return entry == null ? null : entry.geometry();
    }

    /** Tools > Etch Compensation Tool: copper grown for the lateral etch (appTools/ToolEtchCompensation.py). */
    private void openEtchCompensationTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.1e6a38771a46"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Etch Compensation Tool", EtchCompensationToolPanel.build(new EtchCompensationToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public String unitsOf(TreeItem<String> item) {
                GerberImage image = gerberByItem.get(item);
                return image == null ? "MM" : image.units();
            }

            @Override
            public String compensate(TreeItem<String> item, double offset) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    GerberImage result = EtchCompensation.compensate(source, offset);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(item.getValue() + "_comp"), null,
                            result);
                    appendConsole(I18n.text("ui.c7da496fd835") + created.getValue() + I18n.text("ui.bcd9802766f7")
                            + String.format(java.util.Locale.ROOT, "%.5f", offset) + " "
                            + source.units().toLowerCase(java.util.Locale.ROOT) + ").");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > Punch Gerber Tool: holes in the pads of a Gerber (appTools/ToolPunchGerber.py). */
    private void openPunchGerberTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        List<TreeItem<String>> excellons = new ArrayList<>(excellonNode.getChildren());
        excellons.removeIf(item -> !excellonByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.2a52d0622172"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Punch Gerber Tool", PunchGerberToolPanel.build(new PunchGerberToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public List<TreeItem<String>> excellons() {
                return excellons;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public Map<String, String> apertures(TreeItem<String> item) {
                Map<String, String> described = new LinkedHashMap<>();
                GerberImage image = gerberByItem.get(item);
                if (image != null) {
                    image.apertures().forEach((code, aperture) -> described.put(code, aperture.kind + " "
                            + String.format(java.util.Locale.ROOT, "%.4g", aperture.width)
                            + (aperture.height > 0 && aperture.height != aperture.width
                            ? " x " + String.format(java.util.Locale.ROOT, "%.4g", aperture.height) : "")));
                }
                return described;
            }

            @Override
            public String punch(TreeItem<String> item, TreeItem<String> excellon, ExtractDrills.Options options,
                                Set<String> codes) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    GerberImage punched;
                    if (excellon != null) {
                        ExcellonImage drills = excellonByItem.get(excellon);
                        if (drills == null) {
                            return I18n.text("ui.ba2e22a38d88");
                        }
                        punched = Punch.byExcellon(source, drills, codes);
                    } else {
                        punched = Punch.bySize(source, options, codes);
                    }
                    String base = item.getValue();
                    int dot = base.lastIndexOf('.');
                    if (dot > 0) {
                        base = base.substring(0, dot);
                    }
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(base + "_punched"), null, punched);
                    appendConsole(I18n.text("ui.62927ebcb817") + created.getValue() + " ("
                            + (punched.shapes().size() - source.shapes().size()) + I18n.text("ui.72c8cf9bf560"));
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > Extract Drills Tool: an Excellon from the flashed pads of a Gerber (appTools/ToolExtractDrills.py). */
    private void openExtractDrillsTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.111b7a575492"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Extract Drills Tool", ExtractDrillsToolPanel.build(new ExtractDrillsToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public String extract(TreeItem<String> item, ExtractDrills.Options options) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    ExcellonImage drills = ExtractDrills.extract(source, options);
                    String base = item.getValue();
                    int dot = base.lastIndexOf('.');
                    if (dot > 0) {
                        base = base.substring(0, dot);
                    }
                    TreeItem<String> created = addExcellonToProject(uniqueDerivedName(base + "_drills"), null, drills);
                    appendConsole(I18n.text("ui.edc1c4ad66d7") + created.getValue() + " (" + drills.totalDrills()
                            + I18n.text("ui.0de52914c0ab") + drills.toolDiameters().size() + I18n.text("ui.9aa367e06483"));
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > Subtract Tool: remove what one object covers from another (appTools/ToolSub.py). */
    private void openSubtractTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        List<TreeItem<String>> geometries = new ArrayList<>(geometryNode.getChildren());
        geometries.removeIf(item -> !geometryByItem.containsKey(item));
        if (gerbers.isEmpty() && geometries.isEmpty()) {
            appendConsole(I18n.text("ui.7a46141cfe8d"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream()
                .filter(item -> gerbers.contains(item) || geometries.contains(item)).findFirst().orElse(null);
        openToolPanel("Subtract Tool", SubtractToolPanel.build(new SubtractToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public List<TreeItem<String>> geometries() {
                return geometries;
            }

            @Override
            public TreeItem<String> initialTarget() {
                return initial;
            }

            @Override
            public String subtractGerber(TreeItem<String> target, TreeItem<String> subtractor, boolean deleteSources) {
                GerberImage targetImage = gerberByItem.get(target);
                GerberImage subtractorImage = gerberByItem.get(subtractor);
                if (targetImage == null || subtractorImage == null) {
                    return I18n.text("ui.7fcb69591191");
                }
                if (!targetImage.units().equals(subtractorImage.units())) {
                    return I18n.text("ui.6b1e9ba5adf0");
                }
                try {
                    GerberImage result = Subtract.gerber(targetImage, subtractorImage);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(target.getValue() + "_sub"), null,
                            result);
                    finishSubtract(created, List.of(target, subtractor), deleteSources);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }

            @Override
            public String subtractGeometry(TreeItem<String> target, TreeItem<String> subtractor, boolean closePaths,
                                           boolean deleteSources) {
                GeometryEntry targetEntry = geometryByItem.get(target);
                GeometryEntry subtractorEntry = geometryByItem.get(subtractor);
                if (targetEntry == null || subtractorEntry == null) {
                    return I18n.text("ui.7fcb69591191");
                }
                if (!subtractorEntry.tools().isEmpty()) {
                    return I18n.text("ui.bfb899a069ef");
                }
                if (!targetEntry.units().equals(subtractorEntry.units())) {
                    return I18n.text("ui.20f825b5b23d");
                }
                try {
                    Subtract.GeometryResult result = Subtract.geometry(targetEntry.geometry(), targetEntry.tools(),
                            subtractorEntry.geometry(), closePaths);
                    TreeItem<String> created = addGeometryToProject(uniqueDerivedName(target.getValue() + "_sub"),
                            target.getValue(), targetEntry.units(), result.geometry(), targetEntry.strokeOnly(),
                            result.tools());
                    finishSubtract(created, List.of(target, subtractor), deleteSources);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    private void finishSubtract(TreeItem<String> created, List<TreeItem<String>> sources, boolean deleteSources) {
        appendConsole(I18n.text("ui.bf0bc11ed48d") + created.getValue() + ".");
        if (deleteSources) {
            removeSelectionFromProject(new ArrayList<>(sources));
        }
        selectProjectItem(created);
        plotAreaView.fitToLayer(created);
    }

    /** Tools > Invert Gerber Tool: a new Gerber where copper and empty space swap places (appTools/ToolInvertGerber.py). */
    private void openInvertGerberTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole(I18n.text("ui.f20888219e1d"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(gerbers::contains).findFirst().orElse(null);
        openToolPanel("Invert Gerber Tool", InvertGerberToolPanel.build(new InvertGerberToolPanel.Host() {
            @Override
            public List<TreeItem<String>> gerbers() {
                return gerbers;
            }

            @Override
            public TreeItem<String> initialGerber() {
                return initial;
            }

            @Override
            public String invert(TreeItem<String> item, double margin, InvertGerber.JoinStyle style) {
                GerberImage source = gerberByItem.get(item);
                if (source == null) {
                    return I18n.text("ui.4fb8642a3de5");
                }
                try {
                    GerberImage inverted = InvertGerber.invert(source, margin, style);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(item.getValue() + "_inverted"), null,
                            inverted);
                    appendConsole(I18n.text("ui.00365e496658") + created.getValue() + " ("
                            + InvertGerberToolPanel.describe(inverted.solidGeometry().getArea(), inverted.units()) + ").");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > Panelize Tool: repeat a Gerber, Excellon or Geometry in a grid (appTools/ToolPanelize.py). */
    private void openPanelizeTool() {
        List<TreeItem<String>> sources = new ArrayList<>();
        sources.addAll(gerbersNode.getChildren());
        sources.addAll(excellonNode.getChildren());
        sources.addAll(geometryNode.getChildren());
        sources.removeIf(item -> boundsOf(item) == null);
        if (sources.isEmpty()) {
            appendConsole(I18n.text("ui.22751f77f1a8"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(sources::contains).findFirst().orElse(null);
        releaseActiveTool();
        PanelizePreviewController preview = new PanelizePreviewController(jobExecutor, shown -> {
            plotAreaView.setToolPreviewContent(shown == null ? null : shown.content(), shown == null ? List.of() : shown.contents());
            plotAreaView.setEditorFills(shown == null ? null : shown.interior(), null);
            plotAreaView.setEditorReference(shown == null ? null : shown.boxes());
            plotAreaView.setEditorHighlight(shown == null ? null : shown.outline(), true);
        });
        openToolPanel("Panelize Tool", PanelizeToolPanel.build(new PanelizeToolPanel.Host() {
            @Override
            public List<TreeItem<String>> sources() {
                return sources;
            }

            @Override
            public TreeItem<String> initialSource() {
                return initial;
            }

            @Override public List<TreeItem<String>> selectedSources() {
                return selectedObjects().stream().filter(sources::contains).toList();
            }

            @Override public String units(TreeItem<String> item) { return panelizeUnits(item); }

            @Override public boolean canBeContour(TreeItem<String> item) {
                return gerberByItem.containsKey(item) || geometryByItem.containsKey(item);
            }

            @Override
            public double[] bounds(TreeItem<String> item) {
                return boundsOf(item);
            }

            @Override
            public boolean isGerber(TreeItem<String> item) {
                return gerberByItem.containsKey(item);
            }

            @Override
            public void preview(PanelizeToolPanel.Request request, PanelizeToolPanel.PreviewOptions options,
                                Consumer<String> state) {
                if (request == null) { preview.request(null, () -> true, state); return; }
                try {
                    TreeItem<String> contour = options.showOutline() ? options.contour() : null;
                    CamGenerationState before = capturePanelizeState(request, contour, false);
                    Geometry outline = contour == null ? null : panelizeOutline(contour);
                    double[] box = request.referenceBox();
                    Geometry referenceBox = new GeometryFactory().toGeometry(new Envelope(box[0], box[2], box[1], box[3]));
                    preview.request(new PanelizePreview.Input(request.sources().stream().map(MainWindow.this::panelizePreviewLayer).toList(),
                            outline, referenceBox, request.layout(), options.showContent(), options.showOutline(), options.fillInterior()),
                            () -> panelizeStateValid(before), state);
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    preview.request(null, () -> true, state); state.accept(invalid.getMessage());
                }
            }

            @Override
            public void panelize(PanelizeToolPanel.Request request) {
                runPanelize(request);
            }

            @Override public void fitPreview() {
                if (preview.current() != null) plotAreaView.fitToBounds(preview.current().bounds());
            }
        }, () -> {
            closeToolPanel();
        }));
        activeToolCleanup = () -> { preview.close(); clearToolOverlays(); };
    }

    private String panelizeUnits(TreeItem<String> item) {
        Object version = tclVersion(item);
        return version instanceof GerberImage image ? image.units()
                : version instanceof ExcellonImage image ? image.units()
                : version instanceof GeometryEntry entry ? entry.units() : null;
    }

    private Geometry panelizeContent(TreeItem<String> item) {
        Object version = tclVersion(item);
        return version instanceof GerberImage image ? image.solidGeometry()
                : version instanceof ExcellonImage image ? image.solidGeometry()
                : version instanceof GeometryEntry entry ? entry.geometry() : null;
    }

    private Geometry panelizeOutline(TreeItem<String> item) {
        GerberImage gerber = gerberByItem.get(item);
        return gerber != null && gerber.followGeometry() != null && !gerber.followGeometry().isEmpty()
                ? gerber.followGeometry() : panelizeContent(item);
    }

    private PlotAreaView.PreviewLayer panelizePreviewLayer(TreeItem<String> item) {
        Object version = tclVersion(item);
        var category = version instanceof GerberImage ? PlotAreaView.LayerCategory.GERBER
                : version instanceof ExcellonImage ? PlotAreaView.LayerCategory.EXCELLON : PlotAreaView.LayerCategory.GEOMETRY;
        return new PlotAreaView.PreviewLayer(panelizeContent(item), category,
                version instanceof GeometryEntry entry && entry.strokeOnly());
    }

    private CamGenerationState capturePanelizeState(PanelizeToolPanel.Request request, TreeItem<String> contour, boolean generation) {
        if (generation && runningJob != null) throw new IllegalStateException(I18n.text("ui.df12b110e3bb"));
        if (generation && camEditorActive()) throw new IllegalStateException(I18n.text("ui.6217d3d13fc6"));
        var items = new LinkedHashSet<>(request.sources());
        if (request.reference() != null) items.add(request.reference());
        if (contour != null) items.add(contour);
        String units = panelizeUnits(request.source());
        if (!("MM".equals(units) || "IN".equals(units))) throw new IllegalArgumentException(I18n.text("ui.f287c2d8185a"));
        List<CamInput> inputs = new ArrayList<>();
        for (TreeItem<String> item : items) {
            if (!units.equals(panelizeUnits(item))) throw new IllegalArgumentException(I18n.text("ui.6b1789024dda"));
            inputs.add(captureCamInput(item, panelizeContent(item)));
        }
        Envelope actual = new Envelope();
        for (TreeItem<String> item : request.reference() == null ? request.sources() : List.of(request.reference())) {
            double[] box = boundsOf(item);
            if (box == null) throw new IllegalStateException(I18n.text("ui.eacc24156caa"));
            actual.expandToInclude(new Envelope(box[0], box[2], box[1], box[3]));
        }
        if (!Arrays.equals(request.referenceBox(), new double[]{actual.getMinX(), actual.getMinY(), actual.getMaxX(), actual.getMaxY()}))
            throw new IllegalStateException(I18n.text("ui.7b6e45efe9dc"));
        return new CamGenerationState(tclProjectEpoch, List.copyOf(inputs), toolTab.getContent());
    }

    private boolean panelizeStateValid(CamGenerationState before) {
        return tclProjectEpoch == before.epoch() && before.inputs().stream().allMatch(input ->
                tclVersion(input.item()) == input.version() && input.name().equals(input.item().getValue()));
    }

    private void runPanelize(PanelizeToolPanel.Request request) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        CamGenerationState before = capturePanelizeState(request, null, true);
        List<CamInput> sources = before.inputs().stream().filter(input -> request.sources().contains(input.item())).toList();
        JobHandle<?>[] owner = {null};
        beginJob(I18n.text("ui.d11c5dd522e9"));
        activeCamGeneration = before;
        JobHandle<List<PanelizeOutcome>> handle = jobExecutor.submit(context -> {
            List<PanelizeOutcome> outcomes = new ArrayList<>();
            for (int index = 0; index < sources.size(); index++) {
                context.checkCancelled();
                CamInput input = sources.get(index);
                int position = index;
                org.flatcam.cam.ProgressCallback progress = fraction -> context.reportProgress(
                        (position + fraction) / sources.size(), I18n.text("ui.afe5b21d52c1") + input.name() + "...");
                if (input.version() instanceof GerberImage gerber) {
                    GerberImage panel = Panelize.gerber(gerber, request.layout(), context::isCancelled, progress);
                    outcomes.add(new PanelizeOutcome(request.gerberAsGeometry() ? null : panel, null,
                            request.gerberAsGeometry() ? new GeometryJoin.Joined(panel.solidGeometry(), false, List.of()) : null, gerber.units()));
                } else if (input.version() instanceof ExcellonImage excellon) {
                    outcomes.add(new PanelizeOutcome(null, Panelize.excellon(excellon, request.layout(), context::isCancelled, progress), null, excellon.units()));
                } else {
                    GeometryEntry geometry = (GeometryEntry) input.version();
                    var joined=Panelize.geometry(geometry.units(),geometry.geometry(),geometry.strokeOnly(),geometry.tools(),request.layout(),context::isCancelled,progress);
                    var target=geometry.machiningTarget()==null ? null : geometry.machiningTarget().panelized(request.layout(),geometry.tools(),joined.tools(),context::isCancelled);
                    outcomes.add(new PanelizeOutcome(null,null,joined,geometry.units(),target));
                }
            }
            context.checkCancelled(); return List.copyOf(outcomes);
        }, cncPreviewProgress(owner));
        runningJob = handle;
        owner[0] = handle;
        handle.completion()
                .thenAccept(outcomes -> Platform.runLater(() -> {
                    if (!acceptCamGeneration(before, handle)) return;
                    TreeItem<String> first = null;
                    plotAreaView.beginBatchUpdate();
                    try {
                        for (int index = 0; index < sources.size(); index++) {
                            TreeItem<String> created = publishPanelized(sources.get(index), outcomes.get(index));
                            if (first == null) first = created;
                            Panelize.Layout layout = request.layout();
                            appendConsole(I18n.text("ui.eee447f47550") + created.getValue() + " (" + layout.columns() + " x "
                                    + layout.rows() + I18n.text("ui.ab05fc923cec") + (layout.constrained() ? I18n.text("ui.5ed98fa9a87d") : "") + ").");
                        }
                    } finally { plotAreaView.endBatchUpdate(); }
                    if (toolTab.getContent() == before.panel()) {
                        selectProjectItem(first); plotAreaView.fitToLayer(first); closeToolPanel();
                    }
                    setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (runningJob != handle) return;
                        reportJobError(error, I18n.text("ui.9b64b45a6c87"));
                        onJobFinished();
                    });
                    return null;
                });
    }

    private TreeItem<String> publishPanelized(CamInput input, PanelizeOutcome outcome) {
        String name = uniqueDerivedName(input.name() + "_panelized");
        TreeItem<String> created;
        if (outcome.gerber() != null) {
            created = addGerberToProject(name, null, outcome.gerber());
        } else if (outcome.excellon() != null) {
            created = addExcellonToProject(name, null, outcome.excellon(),
                    drillDefaultsByItem.getOrDefault(input.item(), Map.of()));
        } else {
            GeometryJoin.Joined joined = outcome.geometry();
            created = addGeometryToProject(name, input.name(), outcome.units(), joined.geometry(),
                    joined.strokeOnly(), joined.tools(), input.version() instanceof GeometryEntry entry ? entry.cncDefaults() : null);
            if (outcome.target()!=null) {
                var entry=geometryByItem.get(created);
                geometryByItem.put(created,new GeometryEntry(entry.sourceName(),entry.units(),entry.geometry(),entry.strokeOnly(),entry.tools(),entry.cncDefaults(),outcome.target()));
            }
            if (input.version() instanceof GeometryEntry && geometryCncSettingsByItem.containsKey(input.item()))
                geometryCncSettingsByItem.put(created, geometryCncSettingsByItem.get(input.item()));
        }
        // Preserve colors/Solid, but new results are plotted even if the source was hidden.
        copyLayerAppearance(input.item(), created);
        plotAreaView.setLayerVisible(created, true);
        return created;
    }

    /** Tools > Paint Tool: fills Gerber or Geometry polygons with toolpaths (appTools/ToolPaint.py). */
    private void openPaintTool() {
        List<TreeItem<String>> sources = new ArrayList<>();
        sources.addAll(gerbersNode.getChildren());
        sources.addAll(geometryNode.getChildren());
        sources.removeIf(item -> !gerberByItem.containsKey(item) && !geometryByItem.containsKey(item));
        if (sources.isEmpty()) {
            appendConsole(I18n.text("ui.26494608921b"));
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(sources::contains).findFirst().orElse(null);
        releaseActiveTool();
        openToolPanel("Paint Tool", PaintToolPanel.build(new PaintToolPanel.Host() {
            @Override public List<LegacyToolsDatabase.PaintTool> databaseTools() {
                return toolsDatabaseTools(LegacyToolsDatabase::paintTools);
            }
            @Override
            public List<TreeItem<String>> sources() {
                return sources;
            }

            @Override
            public TreeItem<String> initialSource() {
                return initial;
            }

            @Override
            public String units(TreeItem<String> item) {
                GerberImage gerber = gerberByItem.get(item);
                if (gerber != null) {
                    return gerber.units();
                }
                GeometryEntry entry = geometryByItem.get(item);
                return entry == null ? "MM" : entry.units();
            }

            @Override
            public Geometry polygons(TreeItem<String> item) {
                GerberImage gerber = gerberByItem.get(item);
                if (gerber != null) {
                    return gerber.solidGeometry();
                }
                GeometryEntry entry = geometryByItem.get(item);
                return entry == null ? null : entry.geometry();
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }

            @Override
            public void selectArea(Geometry source, boolean polygonShape, Consumer<Geometry> onArea, Runnable onCancel) {
                beginNccAreaSelection(source, polygonShape ? NccToolPanel.AreaShape.POLYGON
                        : NccToolPanel.AreaShape.RECTANGLE, onArea, onCancel);
            }

            @Override
            public void cancelAreaSelection() {
                plotAreaView.cancelPlacement();
            }

            @Override
            public void preview(Geometry geometry) {
                plotAreaView.setEditorHighlight(geometry, false);
            }

            @Override
            public void paint(TreeItem<String> item, String units, Geometry polygons, PaintParameters parameters) {
                runPaintGeneration(item, units, polygons, parameters);
            }
        }, () -> {
            plotAreaView.cancelPlacement();
            closeToolPanel();
        }));
        activeToolCleanup = this::clearToolOverlays;
    }

    private void runPaintGeneration(TreeItem<String> item, String units, Geometry polygons, PaintParameters params) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        beginJob(I18n.text("ui.1ad65b51bd48"));
        JobHandle<NccJobOutcome> handle = jobExecutor.submit(context -> {
            var result=NccGenerator.paint(units,polygons,params,context::isCancelled,fraction -> context.reportProgress(fraction,I18n.text("ui.1ad65b51bd48")));
            var paths=result.toolResults().stream().filter(tool -> !tool.isEmpty()).map(tool -> new ToolGeometry(tool.toolDiameter(),tool.geometry())).toList();
            var areas=new LinkedHashMap<Integer,Geometry>(); int index=0;
            for (var tool:result.toolResults()) if (!tool.isEmpty()) { if (tool.targetArea()!=null) areas.put(index,tool.targetArea().copy()); index++; }
            var target=areas.isEmpty() ? null : new org.flatcam.cam.gcode.MachiningTarget("PAINT",units,result.clearingArea().copy(),areas,
                    org.flatcam.cam.gcode.MachiningTarget.signature(paths,context::isCancelled));
            return new NccJobOutcome(result,OptionalDouble.empty(),target);
        },
                (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;
        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    if (runningJob!=handle) return;
                    var result=outcome.result();
                    if (result.isEmpty()) {
                        appendConsole(I18n.text("ui.e06fe2d2f5dd"));
                        setStatus(I18n.text("ui.712cdeb8cbda"), ERROR_COLOR);
                    } else {
                        List<ToolGeometry> tools = result.toolResults().stream()
                                .filter(toolResult -> !toolResult.isEmpty())
                                .map(toolResult -> new ToolGeometry(toolResult.toolDiameter(), toolResult.geometry(),
                                        ToolProfile.C1))
                                .toList();
                        TreeItem<String> generated = addGeometryToProject(uniqueDerivedName(item.getValue() + "_paint"),
                                item.getValue(), units, result.geometry(), true, tools);
                        var entry=geometryByItem.get(generated);
                        geometryByItem.put(generated,new GeometryEntry(entry.sourceName(),entry.units(),entry.geometry(),entry.strokeOnly(),entry.tools(),entry.cncDefaults(),outcome.target()));
                        appendConsole(String.format(java.util.Locale.ROOT,
                                I18n.text("ui.75543b5fbb07"),
                                result.pathCount(), result.totalLength(), tools.size(),
                                result.totalFailedPolygonCount()));
                        selectProjectItem(generated);
                        plotAreaView.fitToLayer(generated);
                        plotAreaView.setEditorHighlight(null, false);
                        closeToolPanel();
                        setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, I18n.text("ui.c2720c540081"));
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Edit > Tools > 2-Sided Tool: mirror objects and make alignment holes for a double-sided board. */
    private void openDoubleSidedTool() {
        releaseActiveTool();
        openToolPanel(I18n.text("ui.b595bfb73393"), DoubleSidedToolPanel.build(new DoubleSidedToolPanel.Host() {
            @Override
            public List<TreeItem<String>> objects() {
                List<TreeItem<String>> all = new ArrayList<>();
                all.addAll(gerbersNode.getChildren());
                all.addAll(excellonNode.getChildren());
                all.addAll(geometryNode.getChildren());
                return all;
            }

            @Override
            public List<TreeItem<String>> selectedObjects() {
                return MainWindow.this.selectedObjects().stream()
                        .filter(item -> gerberByItem.containsKey(item) || excellonByItem.containsKey(item)
                                || geometryByItem.containsKey(item)).toList();
            }

            @Override
            public double[] bounds(TreeItem<String> item) {
                return boundsOf(item);
            }

            @Override
            public String units(TreeItem<String> item) {
                if (gerberByItem.containsKey(item)) {
                    return gerberByItem.get(item).units();
                }
                if (excellonByItem.containsKey(item)) {
                    return excellonByItem.get(item).units();
                }
                GeometryEntry entry = geometryByItem.get(item);
                return entry == null ? "MM" : entry.units();
            }

            @Override
            public Geometry content(TreeItem<String> item) {
                GerberImage gerber = gerberByItem.get(item);
                if (gerber != null) {
                    return gerber.solidGeometry();
                }
                ExcellonImage excellon = excellonByItem.get(item);
                if (excellon != null) {
                    return excellon.solidGeometry();
                }
                GeometryEntry entry = geometryByItem.get(item);
                return entry == null ? null : entry.geometry();
            }

            @Override
            public Geometry outline(TreeItem<String> item) {
                GerberImage gerber = gerberByItem.get(item);
                if (gerber != null) {
                    return gerber.followGeometry() != null && !gerber.followGeometry().isEmpty()
                            ? gerber.followGeometry() : gerber.solidGeometry().getBoundary();
                }
                GeometryEntry entry = geometryByItem.get(item);
                return entry == null || entry.geometry() == null ? null : entry.geometry().getBoundary().isEmpty()
                        ? entry.geometry() : entry.geometry().getBoundary();
            }

            @Override
            public void mirror(List<TreeItem<String>> items, TransformOp op, boolean asCopy) {
                int done = 0;
                TreeItem<String> last = null;
                for (TreeItem<String> item : items) {
                    TreeItem<String> target = item;
                    if (asCopy) {
                        target = copyObject(item);
                        target.setValue(uniqueDerivedName(item.getValue() + "_espelhado"));
                    }
                    if (applyTransformToItem(target, op)) {
                        done++;
                        last = target;
                    }
                }
                appendConsole(done + I18n.text("ui.a0a9bd3a3e47") + (asCopy ? I18n.text("ui.a1a4dcde7409") : I18n.text("ui.75d33b585f7b")));
                if (last != null) {
                    selectProjectItem(last);
                }
                projectTree.refresh();
            }

            @Override
            public void createAlignmentDrills(String units, double diameter, List<Coordinate> holes, TransformOp op) {
                List<ExcellonImage.Drill> drills = new ArrayList<>();
                List<Geometry> shapes = new ArrayList<>();
                GeometryFactory factory = new GeometryFactory();
                for (Coordinate hole : holes) {
                    for (Coordinate point : new Coordinate[]{hole, op.apply(hole)}) {
                        drills.add(new ExcellonImage.Drill(1, point.x, point.y));
                        shapes.add(factory.createPoint(point).buffer(diameter / 2, 16));
                    }
                }
                ExcellonImage image = ExcellonImage.of(units, java.util.Map.of(1, diameter), drills, List.of(),
                        OverlayNGRobust.union(shapes));
                TreeItem<String> created = addExcellonToProject(uniqueDerivedName("Alignment Drills"), null, image);
                selectProjectItem(created);
                plotAreaView.fitToLayer(created);
                appendConsole(I18n.text("ui.9660996c6d38") + drills.size() + I18n.text("ui.72c8cf9bf560"));
            }

            @Override
            public Coordinate snapToDrill(Coordinate click) {
                double reach = 14 * plotAreaView.worldPerPixel();
                Coordinate best = null;
                double bestDistance = reach;
                for (ExcellonImage image : excellonByItem.values()) {
                    List<Coordinate> centers = new ArrayList<>();
                    for (ExcellonImage.Drill drill : image.drills()) {
                        centers.add(new Coordinate(drill.x(), drill.y()));
                    }
                    for (ExcellonImage.Slot slot : image.slots()) {
                        centers.add(new Coordinate(slot.x1(), slot.y1()));
                        centers.add(new Coordinate(slot.x2(), slot.y2()));
                    }
                    for (Coordinate center : centers) {
                        double distance = center.distance(click);
                        if (distance <= bestDistance) {
                            bestDistance = distance;
                            best = center;
                        }
                    }
                }
                return best;
            }

            @Override
            public void pickPoint(Consumer<Coordinate> onPoint) {
                beginPointPick(onPoint);
            }

            @Override
            public void cancelPick() {
                cancelPointPick();
            }

            @Override
            public void preview(Geometry mirrored, Geometry reference, Geometry mirroredFill, Geometry referenceFill,
                                Geometry mirroredContent) {
                plotAreaView.setEditorContent(mirroredContent);
                plotAreaView.setEditorFills(mirroredFill, referenceFill);
                plotAreaView.setEditorReference(reference);
                plotAreaView.setEditorHighlight(mirrored, true);
            }

            @Override
            public void log(String message) {
                appendConsole(message);
            }
        }, this::closeToolPanel));
        activeToolCleanup = this::clearToolOverlays;
    }

    private void openTransformTool() {
        openToolPanel("Transform Tool", TransformToolPanel.build(
                this::selectionCenterOrOrigin,
                () -> projectItemsForTransform(), this::boundsOf,
                this::applyTransformToSelection, this::closeToolPanel));
    }

    private List<TreeItem<String>> projectItemsForTransform() {
        List<TreeItem<String>> items = new ArrayList<>();
        items.addAll(gerbersNode.getChildren()); items.addAll(excellonNode.getChildren()); items.addAll(geometryNode.getChildren());
        return List.copyOf(items);
    }

    private Object transformSource(TreeItem<String> item) {
        if (gerberByItem.containsKey(item)) return gerberByItem.get(item);
        if (excellonByItem.containsKey(item)) return excellonByItem.get(item);
        return geometryByItem.get(item);
    }

    /** Buffer is expensive: compute all outputs first, then publish atomically if inputs still match. */
    private void runTransformBuffer(List<TreeItem<String>> items, TransformOp.Buffer buffer) {
        if (runningJob != null || geometryEditor.isActive() || gerberEditor.isActive() || excellonEditor.isActive()) {
            appendConsole(I18n.text("ui.f5ef47a2637d")); return;
        }
        Map<TreeItem<String>,Object> inputs=new LinkedHashMap<>();
        for (var item : items) {
            Object source=transformSource(item);
            if (source == null) throw new IllegalArgumentException(I18n.text("ui.ce79d799e09a"));
            inputs.put(item,source);
        }
        long unitCount=inputs.values().stream().map(source -> source instanceof GerberImage g ? g.units()
                : source instanceof ExcellonImage e ? e.units() : ((GeometryEntry)source).units()).distinct().count();
        if (unitCount != 1) throw new IllegalArgumentException(I18n.text("ui.f1035b1b6d86"));
        beginJob("Aplicando Buffer...");
        JobHandle<Map<TreeItem<String>,Object>> handle=jobExecutor.submit(context -> {
            Map<TreeItem<String>,Object> outputs=new LinkedHashMap<>();
            for (var entry : inputs.entrySet()) {
                context.checkCancelled();
                Object source=entry.getValue(), result;
                Geometry shape;
                if (source instanceof GerberImage g) {
                    var output=g.transformed(buffer); result=output; shape=output.solidGeometry();
                } else if (source instanceof ExcellonImage e) {
                    var output=e.transformed(buffer); result=output; shape=output.solidGeometry();
                } else {
                    var g=(GeometryEntry)source;
                    List<ToolGeometry> tools=g.tools().stream().map(t -> t.transformed(buffer)).toList();
                    shape=tools.isEmpty() ? buffer.apply(g.geometry()) : g.geometry().getFactory().buildGeometry(tools.stream().map(ToolGeometry::geometry).toList());
                    if (tools.stream().anyMatch(t -> t.geometry().isEmpty())) throw new IllegalArgumentException(I18n.text("ui.9918ece09bf6"));
                    result=new GeometryEntry(g.sourceName(),g.units(),shape,false,tools,g.cncDefaults());
                }
                if (shape == null || shape.isEmpty() || !shape.isValid()) throw new IllegalArgumentException(I18n.text("ui.ca4a030fc02a"));
                outputs.put(entry.getKey(),result);
                context.reportProgress((double)outputs.size()/inputs.size(),"Aplicando Buffer...");
            }
            context.checkCancelled(); return outputs;
        },(fraction,message) -> Platform.runLater(() -> { updateProgress(fraction); statusLabel.setText(message); }));
        runningJob=handle;
        handle.completion().thenAccept(outputs -> Platform.runLater(() -> {
            onJobFinished();
            if (inputs.entrySet().stream().anyMatch(entry -> transformSource(entry.getKey()) != entry.getValue())) {
                appendConsole(I18n.text("ui.7983b5dbafea")); return;
            }
            plotMoveHistory.clear();
            outputs.forEach((item,result) -> {
                if (result instanceof GerberImage g) { gerberByItem.put(item,g); plotAreaView.updateLayerGeometry(item,gerberFollowItems.contains(item) ? g.followGeometry() : g.solidGeometry()); }
                else if (result instanceof ExcellonImage e) { excellonByItem.put(item,e); plotAreaView.updateLayerGeometry(item,e.solidGeometry()); }
                else { var g=(GeometryEntry)result; geometryByItem.put(item,g); plotAreaView.updateLayerGeometry(item,g.geometry()); }
            });
            refreshPlotSelectionOutline(); showProperties(projectTree.getSelectionModel().getSelectedItem());
            updateProgress(1); setStatus(I18n.text("ui.b4f76f6a4e42"),IDLE_COLOR); appendConsole(outputs.size()+I18n.text("ui.916f67146bf3"));
        })).exceptionally(error -> { Platform.runLater(() -> { reportJobError(error,I18n.text("ui.770908b1944c")); onJobFinished(); }); return null; });
    }

    /**
     * ObjectUI.py's shared "Transformations" block: a uniform Scale factor
     * (about Origin, since Python's on_scale_button_click passes no point)
     * plus an Offset (dx, dy) tuple, and a button opening the full Transform
     * tool - identical across Gerber/Excellon/Geometry in Python (one shared
     * panel, not three), so this is called from all three properties panels.
     */
    private Node transformationsSection(TreeItem<String> item) {
        TextField scaleField = new TextField("1.0");
        scaleField.setPrefColumnCount(6);
        scaleField.setMinWidth(0);
        Button scaleButton = new Button(I18n.text("ui.7450c09d30e8"));
        scaleButton.setOnAction(e -> {
            try {
                double factor = Double.parseDouble(scaleField.getText().trim().replace(',', '.'));
                if (applyTransformToItem(item, new TransformOp.Scale(factor, factor, TransformReference.origin()))) {
                    showProperties(item);
                }
            } catch (NumberFormatException ex) {
                appendConsole(I18n.text("ui.a23af783cdf0"));
            }
        });
        TextField offsetXField = new TextField("0.0");
        TextField offsetYField = new TextField("0.0");
        offsetXField.setPrefColumnCount(6);
        offsetYField.setPrefColumnCount(6);
        offsetXField.setMinWidth(0);
        offsetYField.setMinWidth(0);
        Button offsetButton = new Button(I18n.text("ui.813908d94cb1"));
        ToolDescriptions.apply(offsetXField, I18n.text("ui.a70a6848cb04"), I18n.text("ui.f19bb461fdb4"));
        ToolDescriptions.apply(offsetYField, I18n.text("ui.e01265310b57"), I18n.text("ui.ca109ef0962a"));
        ToolDescriptions.apply(offsetButton, I18n.text("ui.9d47ee6c1ca0"), I18n.text("ui.46b21bb37b8c"));
        offsetButton.setOnAction(e -> {
            try {
                double dx = Double.parseDouble(offsetXField.getText().trim().replace(',', '.'));
                double dy = Double.parseDouble(offsetYField.getText().trim().replace(',', '.'));
                if (applyTransformToItem(item, new TransformOp.Offset(dx, dy))) {
                    showProperties(item);
                }
            } catch (NumberFormatException ex) {
                appendConsole(I18n.text("ui.2f0d1e1dae01"));
            }
        });
        Button transformationsButton = new Button(I18n.text("ui.32788c0c09e0"));
        transformationsButton.setGraphic(legacyIcon("transform.png", 18));
        transformationsButton.setMaxWidth(Double.MAX_VALUE);
        transformationsButton.setOnAction(e -> openTransformTool());

        FlowPane scaleRow = new FlowPane(8, 6, new HBox(6, new Label(I18n.text("ui.33c281329b94")), scaleField), scaleButton);
        FlowPane offsetRow = new FlowPane(8, 6,
                new HBox(6, new Label(I18n.text("ui.121c10d778e4")), offsetXField),
                new HBox(6, new Label("Y:"), offsetYField), offsetButton);
        return new VBox(6, scaleRow, offsetRow, transformationsButton);
    }

    /** One object's share of {@link #applyTransformToSelection} - also used directly by each object's own mini "Transformations" panel. */
    private boolean applyTransformToItem(TreeItem<String> item, TransformOp op) {
        if (!applyingPlotMove) {
            plotMoveHistory.clear();
        }
        if (cncJobByItem.containsKey(item)) {
            appendConsole(I18n.text("ui.1ef26fd2c6fc") + item.getValue());
            return false;
        }
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        if (gerber != null) {
            GerberImage transformed = gerber.transformed(op);
            gerberByItem.put(item, transformed);
            plotAreaView.updateLayerGeometry(item,
                    gerberFollowItems.contains(item) ? transformed.followGeometry() : transformed.solidGeometry());
        } else if (excellon != null) {
            ExcellonImage transformed = excellon.transformed(op);
            excellonByItem.put(item, transformed);
            plotAreaView.updateLayerGeometry(item, transformed.solidGeometry());
        } else if (geometry != null) {
            List<ToolGeometry> newTools = geometry.tools().stream().map(t -> t.transformed(op)).toList();
            GeometryEntry transformed = new GeometryEntry(geometry.sourceName(), geometry.units(),
                    op.apply(geometry.geometry()), geometry.strokeOnly(), newTools, geometry.cncDefaults(),
                    geometry.machiningTarget()==null ? null : geometry.machiningTarget().transformed(op,geometry.tools(),newTools,org.flatcam.cam.CancellationToken.none()));
            geometryByItem.put(item, transformed);
            plotAreaView.updateLayerGeometry(item, transformed.geometry());
        } else {
            return false;
        }
        refreshPlotSelectionOutline();
        return true;
    }

    /**
     * One icon per object kind, copied straight from the legacy app's own assets
     * (ObjectCollection.py's icon_files: flatcam_icon16.png/drill16.png/geometry16.png/cnc16.png) -
     * see Icons.fromResource(). A plain ImageView's layout bounds are exactly its own
     * pixel box, which centers predictably in the fixed-size iconHolder; the earlier
     * Group+Scale vector glyphs did not.
     */
    private Node iconShapeFor(TreeItem<String> item) {
        Node icon;
        if (gerberByItem.containsKey(item)) {
            icon = Icons.fromResource("gerber16.png", 16);
        } else if (excellonByItem.containsKey(item)) {
            icon = Icons.fromResource("drill16.png", 16);
        } else if (geometryByItem.containsKey(item)) {
            icon = Icons.fromResource("geometry16.png", 16);
        } else if (cncJobByItem.containsKey(item)) {
            icon = Icons.fromResource("cnc16.png", 16);
        } else {
            return null;
        }
        return decorateSidebarIcon(icon);
    }

    /**
     * Legacy object icons are mostly black. On dark themes, place the original
     * bitmap in a restrained rounded frame and add a tight light halo around
     * its actual silhouette; the artwork itself remains unchanged.
     */
    private Node decorateSidebarIcon(Node icon) {
        if (!currentTheme.isDark()) {
            return icon;
        }
        icon.getStyleClass().add("sidebar-object-icon-glyph-dark");
        StackPane frame = new StackPane(icon);
        frame.getStyleClass().add("sidebar-object-icon-frame-dark");
        frame.setMinSize(20, 20);
        frame.setPrefSize(20, 20);
        frame.setMaxSize(20, 20);
        return frame;
    }

    /**
     * Built fresh on every right-click (not once per row) so it always
     * reflects the CURRENT selection at click time, same as the legacy
     * app's shared menuproject: with more than one row selected, show the
     * bulk Ativar/Desativar/Remover menu; otherwise the rich single-object
     * menu for whichever kind {@code item} is.
     */
    private ContextMenu buildContextMenuFor(TreeItem<String> item) {
        List<TreeItem<String>> selected = projectTree.getSelectionModel().getSelectedItems().stream()
                .filter(Objects::nonNull).distinct().toList();
        if (selected.size() > 1 && selected.contains(item)) {
            return new ContextMenu(buildBulkContextMenuItems(selected).toArray(new MenuItem[0]));
        }
        GerberImage gerberImage = gerberByItem.get(item);
        ExcellonImage excellonImage = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        CncJobEntry cncJob = cncJobByItem.get(item);
        if (gerberImage != null) {
            return new ContextMenu(gerberContextMenuItems(item, gerberImage).toArray(new MenuItem[0]));
        } else if (excellonImage != null) {
            return new ContextMenu(excellonContextMenuItems(item, excellonImage).toArray(new MenuItem[0]));
        } else if (geometry != null) {
            return new ContextMenu(geometryContextMenuItems(item).toArray(new MenuItem[0]));
        } else if (cncJob != null) {
            return new ContextMenu(cncJobContextMenuItems(item, cncJob).toArray(new MenuItem[0]));
        }
        return new ContextMenu();
    }

    /**
     * "Ativar Plot"/"Desativar Plot" and "Remover" for the whole current
     * selection - the multi-select counterpart of app_Main.py's
     * on_enable_sel_plots()/on_disable_sel_plots()/on_delete(), each of
     * which loops over self.collection.get_selected() instead of a single
     * object.
     */
    private List<MenuItem> buildBulkContextMenuItems(List<TreeItem<String>> selected) {
        MenuItem removeItem = new MenuItem(I18n.text("ui.b8d9e0238018") + selected.size() + ")");
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeSelectionFromProject(selected));

        MenuItem copyItem = new MenuItem(I18n.text("ui.20b9ddc7d377") + selected.size() + ")");
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copySelection(selected));

        List<MenuItem> items = new ArrayList<>(plotVisibilityMenuItems(selected));
        if (!items.isEmpty()) items.add(new SeparatorMenuItem());
        items.addAll(List.of(copyItem, removeItem));
        return items;
    }

    /** Show only useful visibility actions. Mixed selections expose each action with its affected count. */
    private List<MenuItem> plotVisibilityMenuItems(List<TreeItem<String>> selected) {
        List<MenuItem> items = new ArrayList<>();
        for (boolean visible : new boolean[]{true, false}) {
            List<TreeItem<String>> targets = selected.stream().filter(this::isPlottable)
                    .filter(item -> isObjectVisible(item) != visible).toList();
            if (targets.isEmpty()) continue;
            String text = visible ? I18n.text("ui.bada68deb0ed") : I18n.text("ui.e1a06e018b57");
            if (selected.size() > 1) text += " (" + targets.size() + ")";
            MenuItem action = new MenuItem(text);
            setLegacyMenuIcon(action, visible ? "replot32.png" : "clear_plot32.png");
            action.setOnAction(event -> {
                plotAreaView.beginBatchUpdate();
                try {
                    targets.stream().filter(this::isPlottable).forEach(item -> applyObjectVisibility(item, visible));
                    refreshObjectVisibilityUi();
                } finally { plotAreaView.endBatchUpdate(); }
            });
            items.add(action);
        }
        return items;
    }

    private List<MenuItem> objectContextMenuItems(TreeItem<String> item, MenuItem showItem, MenuItem... remaining) {
        List<MenuItem> items = new ArrayList<>();
        items.add(showItem);
        items.addAll(plotVisibilityMenuItems(List.of(item)));
        items.addAll(List.of(remaining));
        return items;
    }

    /** True for a Gerber/Excellon, or a CNC Job that actually has toolpath geometry to show (see CncJobEntry's doc). */
    private boolean isPlottable(TreeItem<String> item) {
        if (gerberByItem.containsKey(item) || excellonByItem.containsKey(item) || geometryByItem.containsKey(item)) {
            return true;
        }
        CncJobEntry entry = cncJobByItem.get(item);
        return entry != null && (entry.travelGeometry() != null && !entry.travelGeometry().isEmpty()
                || entry.cutGeometry() != null && !entry.cutGeometry().isEmpty());
    }

    /**
     * A CNC Job's visibility spans its two sub-layers (travel + cut, see
     * {@link #addCncJobToProject}) toggled together as one unit - everywhere
     * else, an object's tree item is itself the PlotAreaView layer key.
     */
    private boolean isObjectVisible(TreeItem<String> item) {
        if (cncJobByItem.containsKey(item)) {
            return cncLayerVisible(item, true) || cncLayerVisible(item, false);
        }
        return plotAreaView.isLayerVisible(item);
    }

    /** Visible and not taken off the canvas by Limpar Plot (Python keeps the Plot state through clear_plots). */
    private boolean isObjectDrawn(TreeItem<String> item) {
        if (!isObjectVisible(item)) return false;
        if (cncJobByItem.containsKey(item))
            return !(plotAreaView.isLayerCleared(new CncTravelLayerKey(item)) && plotAreaView.isLayerCleared(new CncCutLayerKey(item)));
        return !plotAreaView.isLayerCleared(item);
    }

    private List<TreeItem<String>> allProjectObjects() {
        List<TreeItem<String>> all = new ArrayList<>();
        all.addAll(gerberByItem.keySet());
        all.addAll(excellonByItem.keySet());
        all.addAll(geometryByItem.keySet());
        all.addAll(cncJobByItem.keySet());
        return all;
    }

    /** Python View > Replot (plot_all with fit_view): redraw every enabled object, then fit the view. */
    private void replotAllObjects() {
        plotAreaView.replotAll();
        cncJobByItem.keySet().forEach(this::refreshCncAnnotations);
        refreshPlotSelectionOutline();
        plotAreaView.fitAllVisible();
    }

    /** Python Clear Plot: shapes leave the canvas, each object's Plot checkbox is unchanged. */
    private void clearAllPlots() {
        plotAreaView.clearPlots();
        cncJobByItem.keySet().forEach(this::refreshCncAnnotations);
        refreshPlotSelectionOutline();
    }

    /** Python View > Zoom In/Out step: {@code global_zoom_ratio}, 1.5 by default. */
    static final double MENU_ZOOM_RATIO = 1.5;

    /** Python Ctrl+A on_selectall: every project object becomes selected. */
    private void selectAllObjects() {
        if (anyObjectEditorActive()) {
            appendConsole(I18n.text("ui.1f3e5c60f538"));
            return;
        }
        List<TreeItem<String>> objects = allProjectObjects();
        var model = projectTree.getSelectionModel();
        model.clearSelection();
        int[] rows = objects.stream().mapToInt(this::rowForPlotObject).filter(row -> row >= 0).sorted().toArray();
        if (rows.length == 0) {
            appendConsole(I18n.text("ui.ec65a5f4218c"));
            return;
        }
        model.selectIndices(rows[0], java.util.Arrays.copyOfRange(rows, 1, rows.length));
    }

    private void deselectAllObjects() {
        if (anyObjectEditorActive()) {
            appendConsole(I18n.text("ui.7fb9456a3d9e"));
            return;
        }
        projectTree.getSelectionModel().clearSelection();
    }

    private boolean anyObjectEditorActive() {
        return gerberEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive() || gcodeEditor.isActive();
    }

    private boolean cncLayerVisible(TreeItem<String> item, boolean travel) {
        CncJobEntry entry = cncJobByItem.get(item);
        if (entry == null) return false;
        Geometry geometry = travel ? entry.travelGeometry() : entry.cutGeometry();
        return geometry != null && !geometry.isEmpty()
                && plotAreaView.isLayerVisible(travel ? new CncTravelLayerKey(item) : new CncCutLayerKey(item));
    }

    private void setObjectVisible(TreeItem<String> item, boolean visible) {
        applyObjectVisibility(item, visible);
        refreshObjectVisibilityUi();
    }

    /** Mutation only: callers changing several objects refresh the tree/selection once, inside a Plot batch. */
    private void applyObjectVisibility(TreeItem<String> item, boolean visible) {
        if (cncJobByItem.containsKey(item)) {
            String kind=cncPlotSettings.getOrDefault(item,org.flatcam.app.project.CncPlotSettings.legacy()).kind();
            plotAreaView.setLayerVisible(new CncTravelLayerKey(item), visible && !kind.equals("CUT"));
            plotAreaView.setLayerVisible(new CncCutLayerKey(item), visible && !kind.equals("TRAVEL"));
            refreshCncAnnotations(item);
        } else {
            plotAreaView.setLayerVisible(item, visible);
        }
    }

    private void refreshObjectVisibilityUi() {
        // The tree doesn't otherwise know PlotAreaView's layer visibility changed - see
        // refreshDisplay()'s dimming of disabled rows (ObjectCollection.py's own
        // data()/Qt.ForegroundRole, which reads obj.options['plot'] the same way).
        projectTree.refresh();
        refreshPlotSelectionOutline();
        TreeItem<String> selected = projectTree.getSelectionModel().getSelectedItem();
        Node content = propertiesContainer.getChildren().isEmpty() ? null : propertiesContainer.getChildren().getFirst();
        // ScrollPane content may not be in its skin's children yet (e.g. a still-hidden Properties tab).
        if (content instanceof ScrollPane scroll) content = scroll.getContent();
        if (selected != null && content != null && content.lookup("#object-plot") instanceof CheckBox checkbox)
            checkbox.setSelected(isObjectVisible(selected));
        if (selected != null && cncJobByItem.containsKey(selected) && isObjectVisible(selected)
                && content != null && content.lookup("#object-plot-kind") instanceof ComboBox<?> kind) {
            @SuppressWarnings("unchecked") ComboBox<String> picker = (ComboBox<String>) kind;
            boolean travel = cncLayerVisible(selected, true), cut = cncLayerVisible(selected, false);
            picker.getProperties().put("syncing-plot-kind", true);
            try { picker.setValue(travel == cut ? "All" : cut ? "Cut" : "Travel"); }
            finally { picker.getProperties().remove("syncing-plot-kind"); }
        }
    }

    private void removeSelectionFromProject(List<TreeItem<String>> items) {
        for (TreeItem<String> item : items) {
            if (gerberByItem.containsKey(item)) {
                removeFromProject(item, gerberByItem);
            } else if (excellonByItem.containsKey(item)) {
                removeFromProject(item, excellonByItem);
            } else if (geometryByItem.containsKey(item)) {
                removeFromProject(item, geometryByItem);
            } else if (cncJobByItem.containsKey(item)) {
                removeFromProject(item, cncJobByItem);
            }
        }
    }

    /**
     * Context menu for one Gerber. Its ordering follows MainGUI.py's
     * menuproject, with the Next-only "Exibir" convenience action first and
     * its Isolation/Cutout-to-Geometry workflows grouped together.
     * Unlike Python's always-present Enable/Disable items, FX shows only the
     * action appropriate for the object's current visibility, as requested.
     */
    private List<MenuItem> gerberContextMenuItems(TreeItem<String> item, GerberImage image) {
        MenuItem showItem = new MenuItem(I18n.text("ui.f61ad3865610"));
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setOnAction(e -> focusLayer(item));

        Menu colorMenu = buildLayerColorMenu(item, ToolDefaults.color("display.gerber.fill"), ToolDefaults.color("display.gerber.stroke"));

        MenuItem editItem = new MenuItem(I18n.text("ui.2eba946b2e1e"));
        setLegacyMenuIcon(editItem, "edit_ok32.png");
        editItem.setOnAction(e -> gerberEditor.start(item, gerberByItem.getOrDefault(item, image)));

        MenuItem isolationItem = new MenuItem(I18n.text("ui.387688e2d97d"));
        setLegacyMenuIcon(isolationItem, "iso_16.png");
        isolationItem.setOnAction(e -> generateIsolation(item, image));

        MenuItem cutoutItem = new MenuItem("Cutout Tool...");
        setLegacyMenuIcon(cutoutItem, "cut32_bis.png");
        cutoutItem.setOnAction(e -> generateCutout(item, image));

        Menu createGeometryMenu = new Menu(I18n.text("ui.76a9076aef72"));
        setLegacyMenuIcon(createGeometryMenu, "geometry32.png");
        createGeometryMenu.getItems().addAll(isolationItem, cutoutItem);

        MenuItem viewSourceItem = new MenuItem(I18n.text("ui.4f4d2adf3dfb"));
        setLegacyMenuIcon(viewSourceItem, "source32.png");
        viewSourceItem.setOnAction(e -> viewObjectSource(item));

        MenuItem renameItem = new MenuItem(I18n.text("ui.48eadfc22613"));
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));

        MenuItem copyItem = new MenuItem(I18n.text("ui.39dcf1cbeb5a"));
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));

        MenuItem removeItem = new MenuItem(I18n.text("ui.a3037dc71a43"));
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, gerberByItem));

        MenuItem saveItem = new MenuItem(I18n.text("ui.63f54e88032c"));
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));

        MenuItem propertiesItem = new MenuItem(I18n.text("ui.ecaf3589f2b5"));
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return objectContextMenuItems(item, showItem, new SeparatorMenuItem(), colorMenu,
                new SeparatorMenuItem(), editItem, createGeometryMenu, viewSourceItem, renameItem, copyItem, removeItem,
                saveItem, new SeparatorMenuItem(), propertiesItem);
    }

    /** Same legacy project-menu shape as Gerber, with Excellon's drilling CNC workflow. */
    private List<MenuItem> excellonContextMenuItems(TreeItem<String> item, ExcellonImage image) {
        MenuItem showItem = new MenuItem(I18n.text("ui.f61ad3865610"));
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setOnAction(e -> focusLayer(item));

        Menu colorMenu = buildLayerColorMenu(item, ToolDefaults.color("display.excellon.fill"), ToolDefaults.color("display.excellon.stroke"));

        MenuItem editItem = new MenuItem(I18n.text("ui.56ea8270adb2"));
        setLegacyMenuIcon(editItem, "edit_file32.png");
        editItem.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedExcellon();
        });

        MenuItem gcodeItem = new MenuItem(I18n.text("ui.466244338a76"));
        setLegacyMenuIcon(gcodeItem, "cnc32.png");
        gcodeItem.setOnAction(e -> generateDrillGCode(item, image));

        MenuItem viewSourceItem = new MenuItem(I18n.text("ui.4f4d2adf3dfb"));
        setLegacyMenuIcon(viewSourceItem, "source32.png");
        viewSourceItem.setOnAction(e -> viewObjectSource(item));

        MenuItem renameItem = new MenuItem(I18n.text("ui.48eadfc22613"));
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));

        MenuItem copyItem = new MenuItem(I18n.text("ui.39dcf1cbeb5a"));
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));

        MenuItem removeItem = new MenuItem(I18n.text("ui.a3037dc71a43"));
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, excellonByItem));

        MenuItem saveItem = new MenuItem(I18n.text("ui.63f54e88032c"));
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));

        MenuItem propertiesItem = new MenuItem(I18n.text("ui.ecaf3589f2b5"));
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return objectContextMenuItems(item, showItem, new SeparatorMenuItem(), colorMenu,
                new SeparatorMenuItem(), editItem, gcodeItem, viewSourceItem, renameItem, copyItem, removeItem, saveItem,
                new SeparatorMenuItem(), propertiesItem);
    }

    /** Project-tree actions for Gerber-derived Geometry objects. */
    private List<MenuItem> geometryContextMenuItems(TreeItem<String> item) {
        GeometryEntry entry = geometryByItem.get(item);
        MenuItem showItem = new MenuItem(I18n.text("ui.f61ad3865610"));
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setOnAction(e -> focusLayer(item));

        Menu colorMenu = buildLayerColorMenu(item, ToolDefaults.color("display.geometry.stroke"), ToolDefaults.color("display.geometry.stroke"));

        MenuItem cncItem = new MenuItem(I18n.text("ui.466244338a76"));
        setLegacyMenuIcon(cncItem, "cnc32.png");
        cncItem.setOnAction(e -> generateGeometryCncJob(item, entry));

        MenuItem nccItem = new MenuItem(I18n.text("ui.3486ddd828a8"));
        setLegacyMenuIcon(nccItem, "ncc16.png");
        nccItem.setOnAction(e -> generateNcc(item, entry));

        MenuItem editItem = new MenuItem(I18n.text("ui.aa5faa2dd7cd"));
        setLegacyMenuIcon(editItem, "edit_file32.png");
        editItem.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGeometry();
        });

        MenuItem viewItem = new MenuItem(I18n.text("ui.8766f746ce41"));
        setLegacyMenuIcon(viewItem, "source32.png");
        viewItem.setOnAction(e -> viewObjectSource(item));
        MenuItem renameItem = new MenuItem(I18n.text("ui.48eadfc22613"));
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));
        MenuItem copyItem = new MenuItem(I18n.text("ui.39dcf1cbeb5a"));
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));
        MenuItem removeItem = new MenuItem(I18n.text("ui.a3037dc71a43"));
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, geometryByItem));
        MenuItem saveItem = new MenuItem(I18n.text("ui.064a6cadba1f"));
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));
        MenuItem propertiesItem = new MenuItem(I18n.text("ui.ecaf3589f2b5"));
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return objectContextMenuItems(item, showItem, new SeparatorMenuItem(), colorMenu,
                new SeparatorMenuItem(), cncItem, nccItem, editItem, viewItem, renameItem, copyItem, removeItem, saveItem,
                new SeparatorMenuItem(), propertiesItem);
    }

    private void focusLayer(TreeItem<String> item) {
        setObjectVisible(item, true);
        plotAreaView.bringToFront(item);
        plotAreaView.fitToLayer(item);
        centerTabs.getSelectionModel().select(0);
    }

    /** Only a fill color is asked for - the legacy dialog doesn't expose a separate outline color either. */
    private void editLayerColor(TreeItem<String> item) {
        Color[] current = plotAreaView.layerColors(item);
        Color currentFill = current != null ? current[0]
                : excellonByItem.containsKey(item) ? DRILL_FILL : GERBER_FILL;
        LayerColorDialog.show(currentFill).ifPresent(selected -> {
            Color fill = colorWithOpacity(selected, defaultObjectOpacity(item));
            plotAreaView.setLayerColors(item, fill, legacyOutlineColor(selected));
        });
    }

    private Menu buildLayerColorMenu(TreeItem<String> item, Color defaultFill, Color defaultStroke) {
        Menu menu = new Menu(I18n.text("ui.d109ed9d1f5e"));
        setLegacyMenuIcon(menu, "set_color32.png");
        Map<RadioMenuItem, Color> presets = new LinkedHashMap<>();
        // Exact RGB values from app_Main.py:on_set_color_action_triggered().
        presets.put(addColorPreset(menu, item, I18n.text("ui.6741b8b5e1c2"), Color.web("#FF0000")), Color.web("#FF0000"));
        presets.put(addColorPreset(menu, item, I18n.text("ui.b0bf526b23af"), Color.web("#0000FF")), Color.web("#0000FF"));
        presets.put(addColorPreset(menu, item, I18n.text("ui.1875203bcdcc"), Color.web("#FFDF00")), Color.web("#FFDF00"));
        presets.put(addColorPreset(menu, item, I18n.text("ui.40598e1cb1d2"), Color.web("#00FF00")), Color.web("#00FF00"));
        presets.put(addColorPreset(menu, item, I18n.text("ui.57a73ede5f6f"), Color.web("#FF00FF")), Color.web("#FF00FF"));
        presets.put(addColorPreset(menu, item, I18n.text("ui.6c4eae2375dc"), Color.web("#A52A2A")), Color.web("#A52A2A"));
        presets.put(addColorPreset(menu, item, I18n.text("ui.3683a74222dd"), Color.WHITE), Color.WHITE);
        presets.put(addColorPreset(menu, item, I18n.text("ui.4b652af43d8d"), Color.BLACK), Color.BLACK);

        RadioMenuItem customItem = new RadioMenuItem("Personalizada...");
        setLegacyMenuIcon(customItem, "set_color32.png");
        customItem.setOnAction(e -> editLayerColor(item));
        MenuItem opacityItem = new MenuItem(I18n.text("ui.bfaafd1c473b"));
        setLegacyMenuIcon(opacityItem, "set_color32.png");
        opacityItem.setOnAction(e -> editLayerOpacity(item));
        RadioMenuItem defaultItem = new RadioMenuItem(I18n.text("ui.0758c00e6ea0"));
        defaultItem.setGraphic(colorSwatch(defaultFill));
        defaultItem.setOnAction(e -> plotAreaView.setLayerColors(item, defaultFill, defaultStroke));
        menu.getItems().addAll(new SeparatorMenuItem(), customItem, new SeparatorMenuItem(), opacityItem, defaultItem);
        ToggleGroup colors = new ToggleGroup();
        Runnable refreshSelection = () -> {
            Color[] current = plotAreaView.layerColors(item);
            Color fill = current != null ? current[0] : defaultFill;
            // Opacity is independent. Prefer the named color when the default is also a preset.
            RadioMenuItem selected = presets.entrySet().stream()
                    .filter(entry -> sameRgb(fill, entry.getValue()))
                    .map(Map.Entry::getKey).findFirst()
                    .orElse(sameRgb(fill, defaultFill) ? defaultItem : customItem);
            colors.selectToggle(selected);
        };
        for (MenuItem choice : menu.getItems()) {
            if (choice instanceof RadioMenuItem radio) {
                radio.setToggleGroup(colors);
                var action = radio.getOnAction();
                radio.setOnAction(event -> {
                    try {
                        action.handle(event);
                    } finally {
                        // Also restores the real selection if the custom dialog was cancelled.
                        refreshSelection.run();
                    }
                });
            }
        }
        menu.setOnShowing(event -> refreshSelection.run());
        refreshSelection.run();
        return menu;
    }

    private RadioMenuItem addColorPreset(Menu menu, TreeItem<String> item, String label, Color color) {
        RadioMenuItem colorItem = new RadioMenuItem(label);
        colorItem.setGraphic(colorSwatch(color));
        colorItem.setOnAction(e -> {
            Color fill = colorWithOpacity(color, defaultObjectOpacity(item));
            plotAreaView.setLayerColors(item, fill, legacyOutlineColor(color));
        });
        menu.getItems().add(colorItem);
        return colorItem;
    }

    private static boolean sameRgb(Color first, Color second) {
        return Math.abs(first.getRed() - second.getRed()) < 1e-6
                && Math.abs(first.getGreen() - second.getGreen()) < 1e-6
                && Math.abs(first.getBlue() - second.getBlue()) < 1e-6;
    }

    /** A compact modern preview while keeping the legacy preset itself exact. */
    private static Rectangle colorSwatch(Color color) {
        Rectangle swatch = new Rectangle(14, 14, colorWithOpacity(color, 1));
        swatch.setArcWidth(5);
        swatch.setArcHeight(5);
        swatch.setStroke(Color.web("#808080", 0.72));
        swatch.setStrokeWidth(0.8);
        return swatch;
    }

    private double defaultObjectOpacity(TreeItem<String> item) {
        return geometryByItem.containsKey(item) ? 1.0 : LEGACY_OBJECT_ALPHA;
    }

    /** Python's color_variant(rgb, 0.7), including its special base for white. */
    private static Color legacyOutlineColor(Color color) {
        Color base = color.equals(Color.WHITE) ? Color.web("#DEDEDE") : color;
        return Color.rgb(
                (int) Math.round(base.getRed() * 255 * 0.7),
                (int) Math.round(base.getGreen() * 255 * 0.7),
                (int) Math.round(base.getBlue() * 255 * 0.7));
    }

    private void editLayerOpacity(TreeItem<String> item) {
        Color[] current = plotAreaView.layerColors(item);
        if (current == null) {
            return;
        }
        LayerColorDialog.showOpacity(current[0].getOpacity()).ifPresent(opacity ->
                // Legacy FlatCAM changes fill alpha only; the outline remains unchanged.
                plotAreaView.setLayerColors(item, colorWithOpacity(current[0], opacity), current[1]));
    }

    private static Color colorWithOpacity(Color color, double opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }

    private void setLegacyMenuIcon(MenuItem item, String fileName) {
        item.setGraphic(legacyIcon(fileName, 16));
    }

    private Node legacyIcon(String fileName, double size) {
        Node light = Icons.fromResource(fileName, size);
        Node dark = Icons.fromResource("dark/" + fileName, size);
        light.visibleProperty().bind(darkIcons.not());
        dark.visibleProperty().bind(darkIcons);
        return new StackPane(light, dark);
    }

    private List<MenuItem> cncJobContextMenuItems(TreeItem<String> item, CncJobEntry entry) {
        boolean plottable = isPlottable(item);

        MenuItem showItem = new MenuItem(I18n.text("ui.f61ad3865610"));
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setDisable(!plottable);
        showItem.setOnAction(e -> focusCncJob(item, entry));

        MenuItem viewItem = new MenuItem(I18n.text("ui.13f6cd934186"));
        setLegacyMenuIcon(viewItem, "source32.png");
        viewItem.setOnAction(e -> viewObjectSource(item));

        MenuItem editItem = new MenuItem(I18n.text("ui.fb1b561e4268"));
        setLegacyMenuIcon(editItem, "code_editor32.png");
        editItem.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGCode();
        });

        MenuItem renameItem = new MenuItem(I18n.text("ui.48eadfc22613"));
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));

        MenuItem copyItem = new MenuItem(I18n.text("ui.39dcf1cbeb5a"));
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));

        MenuItem removeItem = new MenuItem(I18n.text("ui.a3037dc71a43"));
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, cncJobByItem));

        MenuItem saveItem = new MenuItem(I18n.text("ui.63f54e88032c"));
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));

        MenuItem propertiesItem = new MenuItem(I18n.text("ui.ecaf3589f2b5"));
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return objectContextMenuItems(item, showItem, new SeparatorMenuItem(), editItem, viewItem, renameItem, copyItem,
                removeItem, saveItem, new SeparatorMenuItem(), propertiesItem);
    }

    private void beginRename(TreeItem<String> item) {
        int row = projectTree.getRow(item);
        if (row >= 0) {
            projectTree.getSelectionModel().clearAndSelect(row);
            Platform.runLater(() -> requestTreeRename(item));
        }
    }

    private void requestTreeRename(TreeItem<String> item) {
        if (item == null || !isProjectObject(item)) return;
        requestedTreeRename = item;
        try {
            projectTree.edit(item);
        } finally {
            requestedTreeRename = null;
        }
    }

    private boolean renameProjectItem(TreeItem<String> item, String requestedName) {
        String newName = requestedName == null ? "" : requestedName.trim();
        if (newName.isEmpty()) {
            appendConsole(I18n.text("ui.2f9984921804"));
            projectTree.refresh();
            return false;
        }
        boolean duplicate = !newName.equals(item.getValue()) && projectObjectNameExists(newName);
        if (duplicate) {
            appendConsole(I18n.text("ui.4da3aa2cdd1c") + newName + ".");
            projectTree.refresh();
            return false;
        }
        item.setValue(newName);
        return true;
    }

    private void showObjectProperties(TreeItem<String> item) {
        setSidebarVisible(true);
        int row = projectTree.getRow(item);
        if (row >= 0) {
            projectTree.getSelectionModel().clearAndSelect(row);
        }
        showProperties(item);
        leftTabs.getSelectionModel().select(propertiesTab);
    }

    private void viewObjectSource(TreeItem<String> item) {
        String source;
        CncJobEntry cncJob = cncJobByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        if (geometry != null) {
            openAuxiliaryTab(I18n.text("ui.04ee0affc04e") + item.getValue(), () -> {
                CodeEditor viewer = new CodeEditor("", CodeSyntax.Language.GEOMETRY, false);
                viewer.loadText(() -> geometry.geometry().toText(), message -> appendConsole(I18n.text("ui.238fcfb536b7") + message));
                return viewer;
            });
            return;
        } else if (cncJob != null) {
            source = cncJob.gcode();
        } else {
            Path path = sourcePathByItem.get(item);
            if (path == null) {
                appendConsole(I18n.text("ui.082bd0523a63") + item.getValue() + ".");
                return;
            }
            var language = item.getParent() == gerbersNode ? CodeSyntax.Language.GERBER : CodeSyntax.Language.EXCELLON;
            openAuxiliaryTab(I18n.text("ui.04ee0affc04e") + item.getValue(), () -> {
                CodeEditor viewer = new CodeEditor("", language, false);
                viewer.loadFile(path, message -> {
                    appendConsole(I18n.text("ui.0314c8146f18") + path + ": " + message); setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
                });
                return viewer;
            });
            return;
        }
        String tabTitle = I18n.text("ui.04ee0affc04e") + item.getValue();
        openAuxiliaryTab(tabTitle, () -> new CodeEditor(source, CodeSyntax.Language.MACHINE, false));
    }

    private void saveObjectAs(TreeItem<String> item) {
        CncJobEntry cncJob = cncJobByItem.get(item);
        if (cncJob!=null) { reviewExistingCncAndSave(item,cncJob); return; }
        GeometryEntry geometry = geometryByItem.get(item);
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        Path sourcePath = sourcePathByItem.get(item);
        if (cncJob == null && geometry == null && gerber == null && excellon == null && sourcePath == null) {
            appendConsole(I18n.text("ui.40565e2c39c9") + item.getValue() + ".");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.8b7be4a2192b"));
        String suggestedName = item.getValue();
        if (gerber != null) {
            // The editor names objects "board.gbr_edit"; suggest a real
            // Gerber extension instead of saving an unrecognizable suffix.
            suggestedName = suggestedName.replaceFirst(
                    "(?i)\\.(gbr|cmp|gtl|gbl|gm1|txt)(_edit(?:_\\d+)?)$", "$2.$1");
            if (!suggestedName.matches("(?i).*\\.(gbr|cmp|gtl|gbl|gm1|txt)$")) {
                suggestedName += ".gbr";
            }
        } else if (excellon != null && !suggestedName.matches("(?i).*\\.(drl|exc|txt|xln)$")) {
            suggestedName += ".drl";
        }
        chooser.setInitialFileName(suggestedName);
        if (geometry != null) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Well-Known Text", "*.wkt", "*.txt"));
        } else if (gerber != null) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.cmp", "*.gtl", "*.gbl", "*.gm1", "*.txt"));
        } else if (excellon != null) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
        } else {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(I18n.text("ui.dc244cbb1ee5"), "*.nc", "*.gcode", "*.tap", "*.imf", "*.plt", "*.hpgl", "*.hpg", "*.rml", "*.prn"));
        }

        Path suggestedParent = cncJob != null ? cncJob.outputFile().getParent()
                : sourcePath != null ? sourcePath.getParent() : null;
        if (suggestedParent != null && Files.isDirectory(suggestedParent)) {
            chooser.setInitialDirectory(suggestedParent.toFile());
        }
        File destination = chooser.showSaveDialog(scene.getWindow());
        if (destination == null) {
            return;
        }

        Path target = destination.toPath();
        try {
            if (geometry != null) {
                Files.writeString(target, geometry.geometry().toText());
            } else if (cncJob != null) {
                Files.writeString(target, cncJob.gcode());
            } else if (gerber != null) {
                new GerberExporter().write(gerber, CamExportDialog.loadGerber(), target);
            } else if (excellon != null) {
                new ExcellonExporter().write(excellon, CamExportDialog.loadExcellon(), target);
            } else if (!sourcePath.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
            }
            File parent = destination.getParentFile();
            if (parent != null) {
                AppPreferences.saveLastCamDirectory(parent.getAbsolutePath());
            }
            appendConsole(I18n.text("ui.257241430473") + target);
            setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
        } catch (IOException | IllegalArgumentException e) {
            appendConsole(I18n.text("ui.b4fd6ceb9110") + target + ": " + e.getMessage());
            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
        }
    }

    private void reviewExistingCncAndSave(TreeItem<String> item, CncJobEntry entry) {
        String name=item.getValue();
        String extension=GCodeToolpathParser.isIcpProgram(entry.gcode()) ? ".imf" : GCodeToolpathParser.isHpglProgram(entry.gcode()) ? ".plt"
                : GCodeToolpathParser.isRolandProgram(entry.gcode()) ? ".rml" : ".nc";
        String filename=name.matches("(?i).*\\.(nc|gcode|tap|ngc|imf|plt|hpgl|hpg|rml|prn)$") ? name : name+extension;
        saveReviewedProgram(name,filename,()->new PendingCncProgram(entry.gcode(),entry.stats(),null,path->{}),
                ()->cncJobByItem.get(item)==entry && Objects.equals(item.getValue(),name), this::appendConsole);
    }

    /**
     * File > Exportar > Gerber / Excellon - app_Main.py's on_file_exportgerber /
     * on_file_exportexcellon: only the matching object type, in the coordinate
     * format asked for by CamExportDialog.
     */
    private void exportSelectedCam(boolean asGerber) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage gerber = item == null ? null : gerberByItem.get(item);
        ExcellonImage excellon = item == null ? null : excellonByItem.get(item);
        if (!isProjectObject(item)) {
            appendConsole(I18n.text("ui.32a4794184b7"));
            return;
        }
        if (asGerber ? gerber == null : excellon == null) {
            appendConsole(asGerber ? I18n.text("ui.c53755bccba8")
                    : I18n.text("ui.e0bc17abf2b9"));
            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
            return;
        }
        if (asGerber) {
            Optional<GerberExporter.Format> format = CamExportDialog.askGerberFormat(scene.getWindow(), gerber.units());
            File destination = format.isEmpty() ? null : chooseExportFile(I18n.text("ui.b36acceab0dc"), item, ".gbr",
                    new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.gtl", "*.gbl", "*.gm1", "*.cmp", "*.txt"));
            if (destination != null) {
                runExport(item.getValue(), I18n.text("ui.17c9bcf1da4d"), destination,
                        path -> new GerberExporter().write(gerber, format.get(), path));
            }
        } else {
            Optional<ExcellonExporter.Format> format =
                    CamExportDialog.askExcellonFormat(scene.getWindow(), excellon.units(),excellon.slots().stream().anyMatch(ExcellonImage.Slot::hasPath));
            File destination = format.isEmpty() ? null : chooseExportFile(I18n.text("ui.8c2af341accc"), item, ".drl",
                    new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
            if (destination != null) {
                runExport(item.getValue(), I18n.text("ui.bfb85b024b34"), destination,
                        path -> new ExcellonExporter().write(excellon, format.get(), path));
            }
        }
    }

    /**
     * File > Exportar > SVG - app_Main.py's on_file_exportsvg/export_svg for Gerber,
     * Excellon, Geometry and CNC Job objects (see SvgExporter for the drawing).
     */
    private void exportSelectedSvg() {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (!isProjectObject(item)) {
            appendConsole(I18n.text("ui.32a4794184b7"));
            return;
        }
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        CncJobEntry cncJob = cncJobByItem.get(item);
        File destination = chooseExportFile(I18n.text("ui.b2bef9041714"), item, ".svg",
                new FileChooser.ExtensionFilter("SVG", "*.svg"));
        if (destination == null) {
            return;
        }
        runExport(item.getValue(), I18n.text("ui.45a24a8900bd"), destination, path -> {
            List<SvgExporter.Layer> layers;
            String units;
            if (cncJob != null) {
                GCodeToolpathParser.Result parsed = GCodeToolpathParser.parse(cncJob.gcode(),
                        CancellationToken.none(), fraction -> { });
                if (!parsed.plotAvailable()) {
                    throw new IllegalArgumentException(I18n.text("ui.d16adc7bdafa"));
                }
                // Python: the tool diameter, else scale_stroke_factor 0.01 (a 0.02 wide line).
                double width = parsed.stats() == null ? 0 : parsed.stats().tools().stream()
                        .map(GCodeToolpathParser.ToolUsage::diameter).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).min().orElse(0);
                double stroke = width > 0 ? width : SvgExporter.SHAPE_STROKE_WIDTH;
                layers = List.of(
                        SvgExporter.toolpath(parsed.travelCenterlines(), SvgExporter.TRAVEL_COLOR, stroke),
                        SvgExporter.toolpath(parsed.cutCenterlines(), SvgExporter.CUT_COLOR, stroke));
                units = parsed.units();
            } else if (geometry != null) {
                layers = List.of(SvgExporter.shapes(geometry.geometry()));
                units = geometry.units();
            } else if (gerber != null) {
                layers = List.of(SvgExporter.shapes(gerber.solidGeometry()));
                units = gerber.units();
            } else {
                layers = List.of(SvgExporter.shapes(excellon.solidGeometry()));
                units = excellon.units();
            }
            new SvgExporter().write(layers, units, path);
        });
    }

    private record PrintCnc(CncJobEntry job, int travelColor, int cutColor) { }
    private record PrintSelection(List<PrintPdfExporter.Layer> layers, List<PrintCnc> cncJobs) { }

    /** Capture immutable object versions and ink on FX; never read controls from the PDF worker. */
    private PrintSelection capturePrintSelection() {
        List<PrintPdfExporter.Layer> layers = new ArrayList<>();
        List<PrintCnc> cncJobs = new ArrayList<>();
        for (TreeItem<String> item : selectedObjects()) {
            GerberImage gerber = gerberByItem.get(item);
            ExcellonImage excellon = excellonByItem.get(item);
            GeometryEntry geometry = geometryByItem.get(item);
            CncJobEntry cnc = cncJobByItem.get(item);
            if (gerber != null) layers.add(new PrintPdfExporter.Layer(gerber.solidGeometry(),gerber.units(),printColor(item,GERBER_FILL),true));
            else if (excellon != null) layers.add(new PrintPdfExporter.Layer(excellon.solidGeometry(),excellon.units(),printColor(item,DRILL_FILL),true));
            else if (geometry != null) layers.add(new PrintPdfExporter.Layer(geometry.geometry(),geometry.units(),printColor(item,GEOMETRY_STROKE),!geometry.strokeOnly()));
            else if (cnc != null) cncJobs.add(new PrintCnc(cnc,printColor(new CncTravelLayerKey(item),Color.GRAY),
                    printColor(new CncCutLayerKey(item),Color.BLUE)));
        }
        return new PrintSelection(List.copyOf(layers),List.copyOf(cncJobs));
    }

    private int printColor(Object key, Color fallback) {
        Color[] colors = plotAreaView.layerColors(key);
        Color color = colors == null ? fallback : colors[plotAreaView.isLayerFilled(key) ? 0 : 1];
        return ((int)Math.round(color.getRed()*255)<<16) | ((int)Math.round(color.getGreen()*255)<<8) | (int)Math.round(color.getBlue()*255);
    }

    private void printSelectedPdf() {
        if (runningJob != null) { appendConsole(I18n.text("ui.df12b110e3bb")); return; }
        if (hasEditorChanges()) { appendConsole(I18n.text("ui.0de42d68e9ea")); return; }
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.isEmpty()) { appendConsole(I18n.text("ui.9c29adf0daf1")); return; }
        var options = new PrintPdfDialog(scene.getWindow(),selected.size()).showAndWait();
        if (options.isEmpty()) return;
        PrintSelection snapshot = capturePrintSelection();
        File file = chooseExportFile(I18n.text("ui.217ce43fce3a"),selected.getFirst(),".pdf",new FileChooser.ExtensionFilter("PDF","*.pdf"));
        if (file == null) return;
        Path destination = file.toPath();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            // Do not silently change the destination after the chooser's overwrite confirmation.
            appendConsole(I18n.text("ui.b920a5152fa6")); return;
        }
        beginJob(I18n.text("ui.f3f5a02b89c0"));
        JobHandle<Path> handle = jobExecutor.submit(context -> {
            CancellationToken cancellation = context::isCancelled;
            List<PrintPdfExporter.Layer> layers = new ArrayList<>(snapshot.layers());
            for (PrintCnc source : snapshot.cncJobs()) {
                GCodeToolpathParser.Result parsed = GCodeToolpathParser.parse(source.job().gcode(),cancellation,
                        fraction -> context.reportProgress(Double.NaN,I18n.text("ui.d690ecd40ed1")));
                if (!parsed.plotAvailable()) throw new IllegalArgumentException(I18n.text("ui.9ef5f791e31e") + parsed.warning());
                if (parsed.travelGeometry()!=null) layers.add(new PrintPdfExporter.Layer(parsed.travelGeometry(),parsed.units(),source.travelColor(),true));
                if (parsed.cutGeometry()!=null) layers.add(new PrintPdfExporter.Layer(parsed.cutGeometry(),parsed.units(),source.cutColor(),true));
            }
            PrintPdfExporter.write(layers,options.get(),destination,cancellation,
                    fraction -> context.reportProgress(fraction,I18n.text("ui.d06cd6e0bffa")));
            return destination;
        },(fraction,message) -> Platform.runLater(() -> { updateProgress(fraction); statusLabel.setText(message); }));
        runningJob = handle;
        handle.completion().whenComplete((saved,failure) -> Platform.runLater(() -> {
            if (runningJob != handle) return;
            try {
                if (failure != null) reportJobError(failure,I18n.text("ui.cb869adb3729"));
                else {
                    AppPreferences.saveLastCamDirectory(saved.toAbsolutePath().getParent().toString());
                    appendConsole(I18n.text("ui.d15f687fc4e4") + saved + I18n.text("ui.11850d323284"));
                    updateProgress(1); setStatus(I18n.text("ui.01470e2951c4"),IDLE_COLOR);
                }
            } finally { onJobFinished(); }
        }));
    }

    /**
     * File > Exportar > DXF - app_Main.py's on_file_exportdxf. Python accepts only
     * Geometry objects; the outlines of Gerber and Excellon objects are allowed too.
     */
    private void exportSelectedDxf() {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (!isProjectObject(item)) {
            appendConsole(I18n.text("ui.32a4794184b7"));
            return;
        }
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        if (geometry == null && gerber == null && excellon == null) {
            appendConsole(I18n.text("ui.46eab66d3dc1"));
            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
            return;
        }
        File destination = chooseExportFile(I18n.text("ui.d11385cb7dd1"), item, ".dxf",
                new FileChooser.ExtensionFilter("DXF", "*.dxf"));
        if (destination == null) {
            return;
        }
        Geometry shapes = geometry != null ? geometry.geometry()
                : gerber != null ? gerber.solidGeometry() : excellon.solidGeometry();
        String units = geometry != null ? geometry.units() : gerber != null ? gerber.units() : excellon.units();
        runExport(item.getValue(), I18n.text("ui.904991019f9d"), destination, path -> new DxfExporter().write(shapes, units, path));
    }

    /** File > Exportar > PNG - app_Main.py's on_file_exportpng; no object needs to be selected. */
    private void exportPlotPng() {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.8ccf4f62c24e"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        chooser.setInitialFileName("png_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png");
        File lastDirectory = new File(AppPreferences.loadLastCamDirectory(System.getProperty("user.home")));
        if (lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File destination = chooser.showSaveDialog(scene.getWindow());
        if (destination == null) {
            appendConsole("Cancelado.");
            return;
        }
        // Captured before the progress UI changes anything; only the encoding runs in the background.
        PlotPngExporter.Capture capture = PlotPngExporter.capture(plotAreaView, scene.getWindow().getOutputScaleX());
        runExport(I18n.text("ui.a7e88cd23d99"), I18n.text("ui.48ad327f9995"), destination, path -> PlotPngExporter.write(capture, path));
    }

    /** Save dialog for an export, named after the object and opened in the last CAM folder. */
    private File chooseExportFile(String title, TreeItem<String> item, String extension,
                                  FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + extension);
        File lastDirectory = new File(AppPreferences.loadLastCamDirectory(System.getProperty("user.home")));
        if (lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File destination = chooser.showSaveDialog(scene.getWindow());
        if (destination == null) {
            appendConsole("Cancelado.");
        }
        return destination;
    }

    private interface ExportWriter {
        void write(Path destination) throws Exception;
    }

    /** Saves the CNC Job's route (leg, marks, coordinates, length, time) as a CSV spreadsheet. */
    private void exportCncSteps(TreeItem<String> item, GCodeToolpathParser.ToolpathStats stats) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        File destination = chooseExportFile(I18n.text("ui.66fb95efd71a"), item, "_sequencia.csv",
                new FileChooser.ExtensionFilter("CSV", "*.csv"));
        if (destination == null) {
            return;
        }
        List<GCodeToolpathParser.PathStep> steps = stats.steps();
        String units = stats.units();
        runExport(item.getValue(), I18n.text("ui.810284b7ee57"), destination,
                path -> CncStepCsv.write(steps, units, path));
    }

    /** Writes an export off the JavaFX thread; the writers publish atomically, so there is no cancel point. */
    private void runExport(String subject, String fileKind, File destination, ExportWriter writer) {
        beginJob(I18n.text("ui.3d817c6b9ba2") + subject + "...");
        cancelJobButton.setDisable(true);
        JobHandle<Path> handle = jobExecutor.submit(context -> {
            context.reportProgress(Double.NaN, "Exportando...");
            writer.write(destination.toPath());
            return destination.toPath();
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().thenAccept(path -> Platform.runLater(() -> {
            AppPreferences.saveLastCamDirectory(destination.getParentFile().getAbsolutePath());
            appendConsole(fileKind + I18n.text("ui.c78f2282e008") + path);
            updateProgress(1);
            setStatus("Exportado.", IDLE_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, I18n.text("ui.cced6cc568fc"));
                onJobFinished();
            });
            return null;
        });
    }

    private void copySelection(List<TreeItem<String>> selected) {
        TreeItem<String> lastCopy = null;
        for (TreeItem<String> item : selected) {
            lastCopy = copyObject(item);
        }
        selectProjectItem(lastCopy);
    }

    private TreeItem<String> copyObject(TreeItem<String> sourceItem) {
        plotMoveHistory.clear();
        String copyName = uniqueCopyName(sourceItem.getValue());
        TreeItem<String> copyItem;
        GerberImage gerber = gerberByItem.get(sourceItem);
        ExcellonImage excellon = excellonByItem.get(sourceItem);
        GeometryEntry geometry = geometryByItem.get(sourceItem);
        CncJobEntry cncJob = cncJobByItem.get(sourceItem);

        if (gerber != null) {
            copyItem = addGerberToProject(copyName, sourcePathByItem.get(sourceItem), gerber);
            setGerberFollow(copyItem, gerber, gerberFollowItems.contains(sourceItem));
            copyLayerAppearance(sourceItem, copyItem);
        } else if (excellon != null) {
            copyItem = addExcellonToProject(copyName, sourcePathByItem.get(sourceItem), excellon,
                    drillDefaultsByItem.getOrDefault(sourceItem, Map.of()));
            if (drillCncSettingsByItem.containsKey(sourceItem))
                drillCncSettingsByItem.put(copyItem, drillCncSettingsByItem.get(sourceItem));
            copyLayerAppearance(sourceItem, copyItem);
        } else if (geometry != null) {
            copyItem = addGeometryToProject(copyName, geometry.sourceName(), geometry.units(),
                    geometry.geometry().copy(), geometry.strokeOnly(), geometry.tools(), geometry.cncDefaults());
            if (geometry.machiningTarget()!=null) {
                var copy=geometryByItem.get(copyItem);
                geometryByItem.put(copyItem,new GeometryEntry(copy.sourceName(),copy.units(),copy.geometry(),copy.strokeOnly(),copy.tools(),copy.cncDefaults(),
                        geometry.machiningTarget().transformed(new TransformOp.Offset(0,0),geometry.tools(),copy.tools(),org.flatcam.cam.CancellationToken.none())));
            }
            if (geometryCncSettingsByItem.containsKey(sourceItem))
                geometryCncSettingsByItem.put(copyItem, geometryCncSettingsByItem.get(sourceItem));
            copyLayerAppearance(sourceItem, copyItem);
        } else if (cncJob != null) {
            copyItem = addCncJobToProject(copyName, cncJob.sourceName(), cncJob.outputFile(), cncJob.gcode(),
                    cncJob.travelGeometry(), cncJob.cutGeometry(), cncJob.travelCenterlines(),
                    cncJob.cutCenterlines(), cncJob.previewStrokeWidth(), cncJob.stats());
            copyLayerAppearance(new CncTravelLayerKey(sourceItem), new CncTravelLayerKey(copyItem));
            copyLayerAppearance(new CncCutLayerKey(sourceItem), new CncCutLayerKey(copyItem));
            applyCncPlotSettings(copyItem,snapshotCncPlotSettings(sourceItem),isObjectVisible(sourceItem));
            refreshCncAnnotations(copyItem);
        } else {
            return null;
        }

        appendConsole(I18n.text("ui.794d08a224f1") + sourceItem.getValue() + " -> " + copyName);
        selectProjectItem(copyItem);
        return copyItem;
    }

    private void copyLayerAppearance(Object sourceKey, Object targetKey) {
        Color[] colors = plotAreaView.layerColors(sourceKey);
        if (colors == null) {
            return;
        }
        plotAreaView.setLayerColors(targetKey, colors[0], colors[1]);
        plotAreaView.setLayerFilled(targetKey, plotAreaView.isLayerFilled(sourceKey));
        plotAreaView.setLayerMulticolor(targetKey, plotAreaView.isLayerMulticolor(sourceKey));
        plotAreaView.setLayerVisible(targetKey, plotAreaView.isLayerVisible(sourceKey));
    }

    private String uniqueCopyName(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String stem = dot > 0 ? originalName.substring(0, dot) : originalName;
        String extension = dot > 0 ? originalName.substring(dot) : "";
        String candidate = stem + "_copy" + extension;
        int suffix = 2;
        while (projectObjectNameExists(candidate)) {
            candidate = stem + "_copy_" + suffix++ + extension;
        }
        return candidate;
    }

    private boolean projectObjectNameExists(String name) {
        return gerbersNode.getChildren().stream().anyMatch(item -> name.equals(item.getValue()))
                || excellonNode.getChildren().stream().anyMatch(item -> name.equals(item.getValue()))
                || geometryNode.getChildren().stream().anyMatch(item -> name.equals(item.getValue()))
                || cncJobsNode.getChildren().stream().anyMatch(item -> name.equals(item.getValue()));
    }

    private void selectProjectItem(TreeItem<String> item) {
        if (item == null) {
            return;
        }
        int row = projectTree.getRow(item);
        if (row >= 0) {
            projectTree.getSelectionModel().clearAndSelect(row);
        }
    }

    /** "Exibir no Plot Area" for a CNC Job - brings both its sub-layers to front and fits to whichever has geometry. */
    private void focusCncJob(TreeItem<String> item, CncJobEntry entry) {
        setObjectVisible(item, true);
        CncCutLayerKey cutKey = new CncCutLayerKey(item);
        CncTravelLayerKey travelKey = new CncTravelLayerKey(item);
        plotAreaView.bringToFront(cutKey);
        plotAreaView.bringToFront(travelKey);
        if (entry.cutGeometry() != null && !entry.cutGeometry().isEmpty()) {
            plotAreaView.fitToLayer(cutKey);
        } else if (entry.travelGeometry() != null && !entry.travelGeometry().isEmpty()) {
            plotAreaView.fitToLayer(travelKey);
        }
        centerTabs.getSelectionModel().select(0);
    }

    private CodeEditor buildGCodeViewer(String gcode) {
        return new CodeEditor(gcode, CodeSyntax.Language.MACHINE, false);
    }

    /**
     * @param travelGeometry the rapid (non-cutting) toolpath, or null if unavailable (a reloaded
     *                       project - see {@link CncJobEntry}'s doc)
     * @param cutGeometry    the cutting toolpath, or null likewise
     */
    private TreeItem<String> addCncJobToProject(String outputFileName, String sourceName, Path outputFile, String gcode,
            Geometry travelGeometry, Geometry cutGeometry) {
        return addCncJobToProject(outputFileName, sourceName, outputFile, gcode,
                travelGeometry, cutGeometry, null, null, 0);
    }

    private TreeItem<String> addCncJobToProject(String outputFileName, String sourceName, Path outputFile, String gcode,
            Geometry travelGeometry, Geometry cutGeometry, Geometry travelCenterlines,
            Geometry cutCenterlines, double previewStrokeWidth) {
        return addCncJobToProject(outputFileName, sourceName, outputFile, gcode, travelGeometry, cutGeometry,
                travelCenterlines, cutCenterlines, previewStrokeWidth, null);
    }

    private TreeItem<String> addCncJobToProject(String outputFileName, String sourceName, Path outputFile, String gcode,
            Geometry travelGeometry, Geometry cutGeometry, Geometry travelCenterlines,
            Geometry cutCenterlines, double previewStrokeWidth, GCodeToolpathParser.ToolpathStats stats) {
        TreeItem<String> item = new TreeItem<>(outputFileName);
        cncJobByItem.put(item, new CncJobEntry(sourceName, outputFile, gcode, travelGeometry,
                cutGeometry, travelCenterlines, cutCenterlines, previewStrokeWidth, stats));
        cncJobsNode.getChildren().add(item);
        plotAreaView.beginBatchUpdate();
        try {
            if (cutGeometry != null && !cutGeometry.isEmpty()) {
                CncCutLayerKey cutKey = new CncCutLayerKey(item);
                plotAreaView.putLayer(cutKey, PlotAreaView.LayerCategory.CNCJOB,
                        cutGeometry, CNC_CUT_FILL, CNC_CUT_STROKE, false);
                plotAreaView.setLayerCenterlineLod(cutKey, cutCenterlines, previewStrokeWidth, strokedPreview(stats));
            }
            if (travelGeometry != null && !travelGeometry.isEmpty()) {
                // Put after cut so it draws on top within the CNCJOB category, matching
                // camlib.py's CNCjob.plot2() (cut layer 1, travel layer 2).
                CncTravelLayerKey travelKey = new CncTravelLayerKey(item);
                plotAreaView.putLayer(travelKey, PlotAreaView.LayerCategory.CNCJOB,
                        travelGeometry, CNC_TRAVEL_FILL, CNC_TRAVEL_STROKE, false);
                plotAreaView.setLayerCenterlineLod(travelKey, travelCenterlines, previewStrokeWidth, strokedPreview(stats));
            }
        } finally {
            plotAreaView.endBatchUpdate();
        }
        applyCncPlotSettings(item,restoringProject ? org.flatcam.app.project.CncPlotSettings.legacy() : new org.flatcam.app.project.CncPlotSettings(
                ToolDefaults.text("display.cnc.cutfill"),ToolDefaults.text("display.cnc.cutline"),ToolDefaults.text("display.cnc.travelfill"),ToolDefaults.text("display.cnc.travelline"),
                ToolDefaults.choice("display.cnc.kind"),ToolDefaults.flag("display.cnc.annotations"),ToolDefaults.flag("display.cnc.arrows"),ToolDefaults.integer("display.cnc.fontsize"),ToolDefaults.text("display.cnc.fontcolor")),
                restoringProject || ToolDefaults.flag("display.cnc.plot"));
        return item;
    }

    /**
     * Milling jobs that state their cutter width are drawn as centerlines stroked at that
     * width at every zoom: one stroke per path instead of one buffered polygon per segment.
     */
    private static boolean strokedPreview(GCodeToolpathParser.ToolpathStats stats) {
        return stats != null && stats.cutterDiameter() != null && stats.cutterDiameter() > 0;
    }

    private static double previewStrokeWidth(String units) {
        return "IN".equalsIgnoreCase(units) ? 0.0008 : 0.02;
    }

    /**
     * Width the centerline LOD should assume: the thinnest real tool when the program
     * declares its tools (drill jobs), otherwise the hairline the preview draws.
     */
    private static double previewWidthFor(GCodeToolpathParser.Result parsed) {
        if (parsed.stats() != null && parsed.stats().cutterDiameter() != null && parsed.stats().cutterDiameter() > 0) {
            return parsed.stats().cutterDiameter();
        }
        if (parsed.stats() != null) {
            double thinnest = parsed.stats().tools().stream()
                    .map(GCodeToolpathParser.ToolUsage::diameter)
                    .filter(Objects::nonNull).mapToDouble(Double::doubleValue).min().orElse(0);
            if (thinnest > 0) {
                return thinnest;
            }
        }
        return previewStrokeWidth(parsed.units());
    }

    /** Distance/time/tool summary read back from a freshly generated program, or null if it can't be read. */
    /** The G-code re-read for its preview data (centerlines, stats), or null when it cannot be read. */
    private static GCodeToolpathParser.Result previewOf(String gcode, CancellationToken cancellation) {
        try {
            return GCodeToolpathParser.parse(gcode, cancellation, fraction -> { });
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    private static GCodeToolpathParser.ToolpathStats toolpathStats(String gcode, CancellationToken cancellation) {
        try {
            return GCodeToolpathParser.parse(gcode, cancellation, fraction -> { }).stats();
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /**
     * Python's "Display Annotation": the start and end of every travel move numbered in
     * program order, shown while the job is plotted.
     */
    private org.flatcam.app.project.CncPlotSettings snapshotCncPlotSettings(TreeItem<String> item) {
        var base=cncPlotSettings.getOrDefault(item,org.flatcam.app.project.CncPlotSettings.legacy());
        Color[] cut=plotAreaView.layerColors(new CncCutLayerKey(item)), travel=plotAreaView.layerColors(new CncTravelLayerKey(item));
        return new org.flatcam.app.project.CncPlotSettings(cut==null?base.cutFill():cut[0].toString().replace("0x","#"),cut==null?base.cutLine():cut[1].toString().replace("0x","#"),
                travel==null?base.travelFill():travel[0].toString().replace("0x","#"),travel==null?base.travelLine():travel[1].toString().replace("0x","#"),base.kind(),
                !cncAnnotationsOff.contains(item),!cncArrowsOff.contains(item),base.fontSize(),base.fontColor());
    }

    private void applyCncPlotSettings(TreeItem<String> item,org.flatcam.app.project.CncPlotSettings settings,boolean visible) {
        cncPlotSettings.put(item,settings);
        if(settings.annotations())cncAnnotationsOff.remove(item);else cncAnnotationsOff.add(item);
        if(settings.arrows())cncArrowsOff.remove(item);else cncArrowsOff.add(item);
        plotAreaView.setLayerColors(new CncCutLayerKey(item),Color.web(settings.cutFill()),Color.web(settings.cutLine()));
        plotAreaView.setLayerColors(new CncTravelLayerKey(item),Color.web(settings.travelFill()),Color.web(settings.travelLine()));
        applyObjectVisibility(item,visible);
    }

    private void refreshCncAnnotations(TreeItem<String> item) {
        var appearance=cncPlotSettings.getOrDefault(item,org.flatcam.app.project.CncPlotSettings.legacy());
        CncAnnotationKey key = new CncAnnotationKey(item);
        CncJobEntry entry = cncJobByItem.get(item);
        boolean showArrows = entry != null && entry.stats() != null && !cncArrowsOff.contains(item)
                && isObjectDrawn(item);
        boolean routeVisible = entry != null && entry.stats() != null && isObjectDrawn(item);
        plotAreaView.setSteps(key, routeVisible ? entry.stats().steps() : List.of(),
                routeVisible ? entry.stats().pathMarks() : List.of(),
                routeVisible ? entry.previewStrokeWidth() : 0, routeVisible ? entry.stats().units() : "MM");
        plotAreaView.setArrows(key, !showArrows ? List.of() : entry.stats().cutArrows().stream()
                .map(a -> new PlotAreaView.Arrow(a.x(), a.y(), a.dx(), a.dy(), a.length()))
                .toList());
        if (entry == null || entry.stats() == null || entry.stats().pathMarks().isEmpty()
                || cncAnnotationsOff.contains(item) || !isObjectDrawn(item)) {
            plotAreaView.setAnnotations(key, List.of());
            return;
        }
        // Same numbers as Python: both ends of every travel move, including the tool-change origin.
        // Marks sitting on a hole of a tool unticked in the tools table are hidden with it.
        Set<Integer> hidden = hiddenCncTools.getOrDefault(item, Set.of());
        Set<List<Double>> hiddenPositions = new java.util.HashSet<>();
        for (GCodeToolpathParser.DrillHit hit : entry.stats().hits()) {
            if (hidden.contains(hit.toolId())) {
                hiddenPositions.add(List.of(hit.x(), hit.y()));
            }
        }
        plotAreaView.setAnnotations(key, entry.stats().pathMarks().stream()
                .filter(mark -> !hiddenPositions.contains(List.of(mark.x(), mark.y())))
                .map(mark -> new PlotAreaView.Annotation(mark.x(), mark.y(), Integer.toString(mark.sequence()),Color.web(appearance.fontColor()),appearance.fontSize()))
                .toList());
    }

    /** Rebuilds a CNC Job's plot from only the tools still ticked in its tools table. */
    private void applyCncToolVisibility(TreeItem<String> item) {
        CncJobEntry entry = cncJobByItem.get(item);
        if (entry == null || entry.stats() == null || !entry.stats().hasTools()) {
            return;
        }
        Set<Integer> hidden = hiddenCncTools.getOrDefault(item, Set.of());
        CncCutLayerKey cutKey = new CncCutLayerKey(item);
        CncTravelLayerKey travelKey = new CncTravelLayerKey(item);
        plotAreaView.beginBatchUpdate();
        try {
            if (hidden.isEmpty()) {
                plotAreaView.updateLayerGeometry(cutKey, entry.cutGeometry());
                plotAreaView.setLayerCenterlineLod(cutKey, entry.cutCenterlines(), entry.previewStrokeWidth(),
                        strokedPreview(entry.stats()));
                plotAreaView.updateLayerGeometry(travelKey, entry.travelGeometry());
                plotAreaView.setLayerCenterlineLod(travelKey, entry.travelCenterlines(), entry.previewStrokeWidth(),
                        strokedPreview(entry.stats()));
            } else {
                List<Geometry> cuts = new ArrayList<>();
                List<Geometry> travels = new ArrayList<>();
                for (GCodeToolpathParser.ToolUsage tool : entry.stats().tools()) {
                    if (!hidden.contains(tool.toolId())) {
                        addParts(cuts, tool.cutGeometry());
                        addParts(travels, tool.travelGeometry());
                    }
                }
                plotAreaView.updateLayerGeometry(cutKey, CNC_GEOMETRY_FACTORY.createGeometryCollection(
                        cuts.toArray(Geometry[]::new)));
                plotAreaView.updateLayerGeometry(travelKey, CNC_GEOMETRY_FACTORY.createGeometryCollection(
                        travels.toArray(Geometry[]::new)));
            }
        } finally {
            plotAreaView.endBatchUpdate();
        }
        refreshCncAnnotations(item);
    }

    private static void addParts(List<Geometry> target, Geometry geometry) {
        if (geometry == null) {
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            target.add(geometry.getGeometryN(i));
        }
    }

    /**
     * Loads DrillGCodeToolPanel into the Tool tab (appTools/ToolDrilling.py's
     * run() switches app.ui.tool_tab to its own UI the same way - see
     * {@link #openToolPanel}), and writes plain drill G-code (GCodeGenerator)
     * to a file the user picks once "Gerar" is clicked. Generation runs in
     * JobExecutor, including keep-out routing, preview parsing and file writing.
     */
    private void generateDrillGCode(TreeItem<String> item, ExcellonImage image) {
        List<DrillGCodeToolPanel.SourceCandidate> sources = excellonByItem.entrySet().stream()
                .filter(entry -> image.units().equalsIgnoreCase(entry.getValue().units()))
                .map(entry -> new DrillGCodeToolPanel.SourceCandidate(entry.getKey(), entry.getValue(),
                        drillDefaultsByItem.getOrDefault(entry.getKey(), Map.of()), drillCncSettingsByItem.get(entry.getKey())))
                .toList();
        DrillGCodeToolPanel.SourceCandidate initialSource = sources.stream()
                .filter(candidate -> candidate.item() == item).findFirst().orElse(null);
        if (initialSource == null) {
            appendConsole(I18n.text("ui.c02ce0077a0d"));
            return;
        }
        openToolPanel("Drilling Tool", DrillGCodeToolPanel.build(sources, initialSource,
                () -> toolsDatabaseTools(LegacyToolsDatabase::drillTools),
                (polygon, onSelected, onCancelled) -> beginNccAreaSelection(image.solidGeometry(),
                        polygon ? NccToolPanel.AreaShape.POLYGON : NccToolPanel.AreaShape.RECTANGLE, onSelected, onCancelled),
                shape -> plotAreaView.setEditorHighlight(shape, false), plotAreaView::cancelPlacement,
                result -> { clearToolOverlays(); runDrillGCodeGeneration(result.source().item(), result.source().image(), result); },
                () -> { clearToolOverlays(); closeToolPanel(); }));
        activeToolCleanup = this::clearToolOverlays;
    }

    private void runDrillGCodeGeneration(TreeItem<String> item, ExcellonImage image, DrillGCodeToolPanel.Result result) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        if (!confirmCncReview(CncPreflight.drilling(result))) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.6234ff419d94"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("ui.abef442239ce") + I18n.caption(result.preprocessor().label()),
                result.preprocessor().filePatterns()));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_drill" + result.preprocessor().fileExtension());
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File outFile = chooser.showSaveDialog(scene.getWindow());
        if (outFile == null) {
            return;
        }

        startDrillGCodeGeneration(item, image, result, outFile.toPath());
    }

    private void startDrillGCodeGeneration(TreeItem<String> item, ExcellonImage image,
                                           DrillGCodeToolPanel.Result result, Path output) {
        if (runningJob != null) throw new IllegalStateException(I18n.text("ui.df12b110e3bb"));
        CamGenerationState before = captureCamGeneration(item, image.solidGeometry(), null, null);
        long epoch = tclProjectEpoch;
        String sourceName = item.getValue();
        Map<Integer, DrillGCodeParameters> savedDefaults = drillDefaultsByItem.get(item);
        DrillCncSettings savedSettings = drillCncSettingsByItem.get(item);
        Node toolContent = toolTab.getContent();
        Runnable validate = () -> {
            if (tclProjectEpoch != epoch || excellonByItem.get(item) != image || !sourceName.equals(item.getValue())
                    || !Objects.equals(savedDefaults, drillDefaultsByItem.get(item))
                    || !Objects.equals(savedSettings, drillCncSettingsByItem.get(item)))
                throw new IllegalStateException(I18n.text("ui.501c20079689"));
            if (gerberEditor.isActive() || excellonEditor.isActive() || geometryEditor.isActive() || gcodeEditor.isActive())
                throw new IllegalStateException(I18n.text("ui.c77afe9d63d7"));
        };
        validate.run();
        beginJob(I18n.text("ui.f9c5f37b2a8f"));
        activeCamGeneration = before;
        JobHandle<DrillCncGeneration.Generated> handle = jobExecutor.submit(context -> TclExecution.run(context,
                () -> DrillCncGeneration.generate(image, result.settingsByTool(), result.orderedToolIds(), result.options(),
                        result.preprocessor(), output, context, () -> TclExecution.onFx(() -> { validate.run(); return null; }))),
                camProgress(before));
        runningJob = handle;
        handle.completion().thenAccept(generated -> Platform.runLater(() -> {
            if (runningJob != handle) return;
            try {
                if (handle.isCancelled()) throw new CancellationException();
                validate.run();
                CncJobResult job = generated.job();
                Map<Integer, DrillGCodeParameters> updatedDefaults = new LinkedHashMap<>(
                        drillDefaultsByItem.getOrDefault(item, Map.of()));
                updatedDefaults.putAll(result.settingsByTool());
                drillDefaultsByItem.put(item, Map.copyOf(updatedDefaults));
                drillCncSettingsByItem.put(item, result.cncSettings());
                AppPreferences.saveLastCamDirectory(output.toAbsolutePath().getParent().toString());
                appendConsole(I18n.text("ui.6b349ed1a94b") + output + " (" + job.gcode().lines().count() + I18n.text("ui.d1862403f7c1"));
                GCodeToolpathParser.Result preview = generated.preview();
                if (preview != null && preview.warning() != null) appendConsole(output.getFileName() + ": " + preview.warning());
                addCncJobToProject(output.getFileName().toString(), sourceName, output, job.gcode(),
                        job.travelGeometry(), job.cutGeometry(), null, null, 0, preview == null ? null : preview.stats());
                if (toolTab.getContent() == toolContent) closeToolPanel();
                updateProgress(1);
                setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
            } catch (RuntimeException failure) { reportJobError(failure, I18n.text("ui.8125d6ade168")); }
            finally { onJobFinished(); }
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                if (runningJob != handle) return;
                reportJobError(error, I18n.text("ui.722c2f87025e")); onJobFinished();
            });
            return null;
        });
    }

    private void generateExcellonMilling(TreeItem<String> item, ExcellonImage image) {
        List<ExcellonMillingToolPanel.SourceCandidate> sources = excellonByItem.entrySet().stream()
                .filter(entry -> image.units().equalsIgnoreCase(entry.getValue().units()))
                .map(entry -> new ExcellonMillingToolPanel.SourceCandidate(entry.getKey(), entry.getValue()))
                .toList();
        ExcellonMillingToolPanel.SourceCandidate initial = sources.stream()
                .filter(candidate -> candidate.item() == item).findFirst().orElse(null);
        if (initial == null) return;
        openToolPanel("Milling Tool", ExcellonMillingToolPanel.build(sources, initial,
                () -> toolsDatabaseTools(LegacyToolsDatabase::millingTools),
                this::runExcellonMilling, this::closeToolPanel));
    }

    private void runExcellonMilling(ExcellonMillingToolPanel.Result result) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        TreeItem<String> item = result.source().item();
        ExcellonImage image = result.source().image();
        CamGenerationState before;
        try { before = captureCamGeneration(item, image.solidGeometry(), null, null); }
        catch (IllegalStateException invalid) { reportJobError(invalid, "Milling: "); return; }
        beginJob(I18n.text("ui.8b1b3da67767"));
        activeCamGeneration = before;
        JobHandle<Geometry> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.1, I18n.text("ui.5fa008176b6f"));
            Geometry geometry = ExcellonMillingGenerator.generate(image, result.toolIds(),
                    result.millDiameter(), result.kind(), context::isCancelled);
            context.checkCancelled();
            context.reportProgress(0.95, I18n.text("ui.37af4a7ea2dc"));
            return geometry;
        }, camProgress(before));
        runningJob = handle;
        handle.completion().thenAccept(geometry -> Platform.runLater(() -> {
            if (!acceptCamGeneration(before, handle)) return;
            if (geometry.isEmpty()) {
                appendConsole(I18n.text("ui.a4f65ab4d51a"));
                setStatus(I18n.text("ui.712cdeb8cbda"), ERROR_COLOR);
            } else {
                String suffix = result.kind() == ExcellonMillingGenerator.Kind.DRILLS ? "_mill_drills" : "_mill_slots";
                String name = uniqueDerivedName(item.getValue() + suffix);
                TreeItem<String> generated = addGeometryToProject(name, item.getValue(), image.units(),
                        geometry, true, List.of(new ToolGeometry(result.millDiameter(), geometry)));
                if (result.databaseTool() != null) {
                    var db = result.databaseTool();
                    GeometryEntry created = geometryByItem.get(generated);
                    geometryByItem.put(generated, new GeometryEntry(created.sourceName(), created.units(), geometry,
                            true, List.of(new ToolGeometry(db.diameter(), geometry, db.profile())), db.parameters()));
                    geometryCncSettingsByItem.put(generated, new GeometryCncSettings(GCodePreprocessor.FX_PORTABLE,
                            null, db.tip() == null ? Map.of() : Map.of(0, db.tip()), Map.of(), db.profile(),
                            db.jobDefaults().isEmpty() ? Map.of() : Map.of(0, db.jobDefaults())));
                }
                appendConsole(I18n.text("ui.1f920178ca3a") + name + I18n.text("ui.5157a405833f"));
                finishCamPanel(before, generated);
                setStatus(I18n.text("ui.53e331e8301c"), IDLE_COLOR);
            }
            updateProgress(1);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                if (runningJob != handle) return;
                reportJobError(error, I18n.text("ui.492b9dce65c7"));
                onJobFinished();
            });
            return null;
        });
    }

    private record CamInput(TreeItem<String> item, String name, Object version) { }
    private record CamGenerationState(long epoch, List<CamInput> inputs, Node panel) { }
    private CamGenerationState activeCamGeneration;

    private org.flatcam.app.job.ProgressListener camProgress(CamGenerationState before) {
        return (fraction, message) -> Platform.runLater(() -> {
            if (activeCamGeneration != before || runningJob == null) return;
            updateProgress(fraction);
            statusLabel.setText(message);
        });
    }

    private boolean camEditorActive() {
        return gerberEditor.isActive() || geometryEditor.isActive()
                || excellonEditor.isActive() || gcodeEditor.isActive();
    }

    private CamInput captureCamInput(TreeItem<String> item, Geometry expected) {
        Object version = tclVersion(item);
        Geometry current = version instanceof GerberImage image ? image.solidGeometry()
                : version instanceof ExcellonImage image ? image.solidGeometry()
                : version instanceof GeometryEntry entry ? entry.geometry() : null;
        if (version == null || current == null || current != expected)
            throw new IllegalStateException(I18n.text("ui.e4c6ee392984"));
        return new CamInput(item, item.getValue(), version);
    }

    /** Only computation inputs: changing Plot colors, visibility or unrelated objects is harmless. */
    private CamGenerationState captureCamGeneration(TreeItem<String> item, Geometry source,
                                                     TreeItem<String> reference, Geometry referenceGeometry) {
        if (runningJob != null) throw new IllegalStateException(I18n.text("ui.df12b110e3bb"));
        if (camEditorActive()) throw new IllegalStateException(I18n.text("ui.85f2e9fd04fb"));
        List<CamInput> inputs = new ArrayList<>();
        inputs.add(captureCamInput(item, source));
        if (referenceGeometry != null) {
            if (reference == null) throw new IllegalStateException(I18n.text("ui.515a2db8f2ee"));
            inputs.add(captureCamInput(reference, referenceGeometry));
        }
        return new CamGenerationState(tclProjectEpoch, List.copyOf(inputs), toolTab.getContent());
    }

    /** Recheck on FX immediately before publishing; cancellation may arrive after worker completion. */
    private boolean acceptCamGeneration(CamGenerationState before, JobHandle<?> handle) {
        if (runningJob != handle) return false;
        try {
            if (handle.isCancelled()) throw new CancellationException();
            if (before.epoch() != tclProjectEpoch || camEditorActive())
                throw new IllegalStateException(I18n.text("ui.bede605dfdf8"));
            for (CamInput input : before.inputs())
                if (tclVersion(input.item()) != input.version() || !input.name().equals(input.item().getValue()))
                    throw new IllegalStateException(I18n.text("ui.0c5cdb7e8ec4"));
            return true;
        } catch (IllegalStateException invalid) {
            reportJobError(invalid, I18n.text("ui.31a915cb6ee9"));
            onJobFinished();
            return false;
        }
    }

    private void finishCamPanel(CamGenerationState before, TreeItem<String> generated) {
        if (toolTab.getContent() != before.panel()) return;
        selectProjectItem(generated);
        plotAreaView.fitToLayer(generated);
        closeToolPanel();
    }

    /** Creates editable Geometry first, as the Python Isolation tool does. */
    private void generateIsolation(TreeItem<String> item, GerberImage image) {
        List<IsolationToolPanel.SourceCandidate> sources = gerberByItem.entrySet().stream()
                .filter(entry -> image.units().equalsIgnoreCase(entry.getValue().units()))
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new IsolationToolPanel.SourceCandidate(entry.getKey(), entry.getValue()))
                .toList();
        IsolationToolPanel.SourceCandidate initialSource = sources.stream()
                .filter(candidate -> candidate.item() == item).findFirst().orElse(null);
        if (initialSource == null) {
            appendConsole(I18n.text("ui.3f4188352aa7"));
            return;
        }
        List<IsolationToolPanel.ExceptionArea> exceptionAreas = geometryByItem.entrySet().stream()
                .filter(entry -> image.units().equalsIgnoreCase(entry.getValue().units()))
                .filter(entry -> entry.getValue().geometry() != null
                        && !entry.getValue().geometry().isEmpty()
                        && entry.getValue().geometry().getDimension() == 2)
                .map(entry -> new IsolationToolPanel.ExceptionArea(
                        entry.getKey().getValue(), entry.getValue().geometry(), entry.getKey()))
                .toList();
        openToolPanel("Isolation Tool", IsolationToolPanel.build(sources, initialSource, exceptionAreas,
                (source, polygon, onSelected, onCancelled) -> beginNccAreaSelection(source.image().solidGeometry(),
                        polygon ? NccToolPanel.AreaShape.POLYGON : NccToolPanel.AreaShape.RECTANGLE,
                        onSelected, onCancelled),
                plotAreaView::cancelPlacement,
                () -> toolsDatabaseTools(LegacyToolsDatabase::isolationTools),
                params -> {
                    plotAreaView.cancelPlacement();
                    runIsolationGeneration(params.source().item(), params.source().image(), params);
                }, () -> {
                    plotAreaView.cancelPlacement();
                    closeToolPanel();
                }));
    }

    private void runIsolationGeneration(TreeItem<String> item, GerberImage image, IsolationToolPanel.Result params) {
        CamGenerationState before;
        try {
            if (gerberByItem.get(item) != image) throw new IllegalStateException(I18n.text("ui.4a08a0b9775e"));
            var reference = params.exceptionReference();
            before = captureCamGeneration(item, image.solidGeometry(), reference == null ? null : reference.item(),
                    reference == null ? null : reference.geometry());
        } catch (IllegalStateException invalid) { appendConsole(invalid.getMessage()); return; }

        beginJob(I18n.text("ui.7d837b7cf072"));
        activeCamGeneration = before;
        record IsolationJobOutcome(List<IsolationGenerator.ToolResult> results, OptionalDouble clearance) { }
        JobHandle<IsolationJobOutcome> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.05, I18n.text("ui.471ec412c179"));
            OptionalDouble clearance = params.checkValidity()
                    ? NccGenerator.minimumCopperClearance(image.solidGeometry()) : OptionalDouble.empty();
            List<IsolationGenerator.ToolResult> results;
            if (params.restMachining() && !params.follow()) {
                results = IsolationGenerator.generateRest(image.units(), image.solidGeometry(),
                        params.tools(), params.forcedRest(), context::isCancelled);
            } else {
                results = new ArrayList<>();
                for (IsolationParameters tool : params.tools()) {
                    IsolationResult isolation = params.follow()
                            ? IsolationGenerator.generateFollow(image.units(), image.followGeometry(), context::isCancelled)
                            : IsolationGenerator.generate(image.units(), image.solidGeometry(), tool, context::isCancelled);
                    results.add(new IsolationGenerator.ToolResult(tool, isolation));
                }
            }
            if (params.exceptionMask() != null) {
                results = results.stream().map(result -> new IsolationGenerator.ToolResult(result.parameters(),
                        IsolationGenerator.excludeArea(result.isolation(), params.exceptionMask(),
                                context::isCancelled), result.remainingCopperCount())).toList();
            }
            context.checkCancelled();
            context.reportProgress(0.95, I18n.text("ui.0c5aca294cb2"));
            return new IsolationJobOutcome(results, clearance);
        }, camProgress(before));
        runningJob = handle;

        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    if (!acceptCamGeneration(before, handle)) return;
                    List<IsolationGenerator.ToolResult> results = outcome.results();
                    outcome.clearance().ifPresent(clearance -> {
                        boolean anySuitable = params.tools().stream()
                                .anyMatch(tool -> tool.toolDiameter() <= clearance);
                        appendConsole(String.format(java.util.Locale.ROOT,
                                anySuitable ? I18n.text("ui.f1a927b1a6b6")
                                        : I18n.text("ui.d530f23167e1"), clearance));
                    });
                    if (gerberByItem.get(item) != image) {
                        appendConsole(I18n.text("ui.5a63da8c04a8"));
                        setStatus(I18n.text("ui.a81cddec6216"), ERROR_COLOR);
                    } else if (results.stream().allMatch(result -> result.isolation().isEmpty())) {
                        appendConsole(I18n.text("ui.a0000aa0301f"));
                        if (params.restMachining() && results.get(results.size() - 1).remainingCopperCount() > 0) {
                            appendConsole(I18n.text("ui.3e9a08c5414d") + results.get(results.size() - 1).remainingCopperCount()
                                    + I18n.text("ui.3ef28654ec33"));
                        }
                        setStatus(I18n.text("ui.712cdeb8cbda"), ERROR_COLOR);
                    } else {
                        String suffix = params.follow() ? "_follow" : switch (params.tools().get(0).type()) {
                            case EXTERIOR -> "_ext_iso";
                            case INTERIOR -> "_int_iso";
                            case BOTH -> "_iso";
                        };
                        TreeItem<String> lastGenerated = null;
                        int created = 0;
                        if (params.combinePasses()) {
                            List<ToolGeometry> tools = results.stream()
                                    .filter(result -> !result.isolation().isEmpty())
                                    .map(result -> new ToolGeometry(result.parameters().toolDiameter(),
                                            result.isolation().geometry(), params.profiles().getOrDefault(
                                                    result.parameters().toolDiameter(), ToolProfile.C1))).toList();
                            Geometry combined = image.solidGeometry().getFactory().buildGeometry(
                                    tools.stream().map(ToolGeometry::geometry).toList());
                            String name = uniqueDerivedName(item.getValue()
                                    + (params.restMachining() ? "_iso_rest" : suffix));
                            lastGenerated = addGeometryToProject(name, item.getValue(), image.units(), combined,
                                    true, tools);
                            applyCamMachining(lastGenerated, params.machining());
                            created++;
                        } else {
                            for (IsolationGenerator.ToolResult result : results) {
                                List<Geometry> outputs = result.isolation().passGeometries();
                                for (int pass = 0; pass < outputs.size(); pass++) {
                                    Geometry path = outputs.get(pass);
                                    if (path.isEmpty()) continue;
                                    String toolSuffix = results.size() > 1
                                            ? "_" + result.parameters().toolDiameter() : "";
                                    String passSuffix = outputs.size() > 1 ? "_p" + (pass + 1) : "";
                                    String name = uniqueDerivedName(item.getValue() + suffix + toolSuffix
                                            + passSuffix);
                                    lastGenerated = addGeometryToProject(name, item.getValue(), image.units(), path,
                                            true, List.of(new ToolGeometry(result.parameters().toolDiameter(), path,
                                                    params.profiles().getOrDefault(
                                                            result.parameters().toolDiameter(), ToolProfile.C1))));
                                    applyCamMachining(lastGenerated, params.machining());
                                    created++;
                                }
                            }
                        }
                        appendConsole(String.format(I18n.text("ui.ff837fbcb16b"),
                                results.stream().mapToInt(result -> result.isolation().ringCount()).sum(),
                                results.stream().mapToDouble(result -> result.isolation().totalLength()).sum(),
                                lastGenerated == null ? I18n.text("ui.28d1a7953a9c")
                                        : geometryByItem.get(lastGenerated).geometry().getEnvelopeInternal()));
                        appendConsole(I18n.text("ui.78efefd4d8aa") + created + I18n.text("ui.27b0230949be"));
                        if (params.restMachining() && results.get(results.size() - 1).remainingCopperCount() > 0) {
                            appendConsole(I18n.text("ui.3e9a08c5414d") + results.get(results.size() - 1).remainingCopperCount()
                                    + I18n.text("ui.3ef28654ec33"));
                        }
                        if (lastGenerated != null) {
                            finishCamPanel(before, lastGenerated);
                        }
                        setStatus(I18n.text("ui.eb989299965a"), IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (runningJob != handle) return;
                        reportJobError(error, I18n.text("ui.cf9868d530ec"));
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Creates an editable cutout Geometry, as appTools/ToolCutOut.py does. */
    /** Transfer only explicitly imported per-tool cutting settings; no inheritance to manual tools. */
    private void applyCamMachining(TreeItem<String> item, Map<Double, LegacyToolsDatabase.MillingTool> imported) {
        if (imported.isEmpty()) return;
        GeometryEntry entry = geometryByItem.get(item);
        var parameters = new LinkedHashMap<Integer, GeometryGCodeParameters>();
        var tips = new LinkedHashMap<Integer, VTipSettings>();
        var jobDefaults = new LinkedHashMap<Integer, org.flatcam.app.project.CncJobDefaults>();
        for (int i = 0; i < entry.tools().size(); i++) {
            ToolGeometry tool = entry.tools().get(i);
            var db = imported.get(tool.toolDiameter());
            if (db == null) continue;
            if (db.parameters() != null) parameters.put(i, db.parameters().withCompensation(org.flatcam.cam.gcode.ToolPathOffset.PATH, 0));
            if (db.tip() != null) tips.put(i, db.tip());
            if (!db.jobDefaults().isEmpty()) jobDefaults.put(i, db.jobDefaults());
        }
        if (parameters.isEmpty() && jobDefaults.isEmpty()) return;
        boolean metric = "MM".equalsIgnoreCase(entry.units());
        // Keep the ordinary panel defaults for tools added manually. Explicit imported
        // settings are overrides, not implicit defaults for every other tool.
        var defaults = new GeometryGCodeParameters(metric ? 3 : .1, metric ? .1 : .004,
                false, metric ? .05 : .002, metric ? 300 : 12, 10000, entry.tools().size() > 1);
        geometryByItem.put(item, new GeometryEntry(entry.sourceName(), entry.units(), entry.geometry(),
                entry.strokeOnly(), entry.tools(), defaults, entry.machiningTarget()));
        geometryCncSettingsByItem.put(item, new GeometryCncSettings(GCodePreprocessor.FX_PORTABLE, null, tips, parameters,
                org.flatcam.cam.geometry.ToolProfile.C1, jobDefaults));
        appendConsole(I18n.text("ui.e0409cafeadb"));
    }

    private void generateCutout(TreeItem<String> item, GerberImage image) {
        openCutoutTool(item, image.units(), image.solidGeometry(), () -> gerberByItem.get(item) == image);
    }

    private void generateCutout(TreeItem<String> item, GeometryEntry entry) {
        openCutoutTool(item, entry.units(), entry.geometry(), () -> geometryByItem.get(item) == entry);
    }

    private void openCutoutTool(TreeItem<String> item, String units, Geometry source,
                                BooleanSupplier sourceAvailable) {
        if (source == null || source.isEmpty() || source.getDimension() != 2) {
            appendConsole(I18n.text("ui.db1f6976be38"));
            return;
        }
        var gaps = new CutoutGapPlacementController(plotAreaView, jobExecutor, units, source, sourceAvailable);
        openToolPanel("Cutout Tool", CutoutToolPanel.build(units,
                (polygon, onSelected, onCancelled) -> beginNccAreaSelection(source,
                        polygon ? NccToolPanel.AreaShape.POLYGON : NccToolPanel.AreaShape.RECTANGLE,
                        onSelected, onCancelled),
                gaps::cancel,
                () -> toolsDatabaseTools(LegacyToolsDatabase::cutoutTools),
                gaps::begin,
                result -> {
                    if (gaps.isBusy()) throw new IllegalArgumentException(I18n.text("ui.f296966a67c9"));
                    gaps.cancel();
                    runCutoutGeneration(item, units, source, sourceAvailable, result);
                }, () -> {
                    gaps.cancel();
                    closeToolPanel();
                }));
        activeToolCleanup = gaps::close;
    }

    private record CutoutJobOutcome(CutoutResult cutout, ExcellonImage mouseBites) {
    }

    private void runCutoutGeneration(TreeItem<String> item, String units, Geometry source,
                                     BooleanSupplier sourceAvailable, CutoutToolPanel.Result result) {
        CamGenerationState before;
        try {
            if (!sourceAvailable.getAsBoolean()) throw new IllegalStateException(I18n.text("ui.1170a4131948"));
            before = captureCamGeneration(item, source, null, null);
        } catch (IllegalStateException invalid) { appendConsole(invalid.getMessage()); return; }

        beginJob(I18n.text("ui.118d77f1ef2a"));
        activeCamGeneration = before;
        JobHandle<CutoutJobOutcome> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.05, I18n.text("ui.e4eb5e7c7a1b"));
            CutoutResult cutout = CutoutGenerator.generate(
                    units, source, result.cutoutParams(),
                    result.manualGapAreas(), context::isCancelled);
            ExcellonImage mouseBites = result.gapType() == CutoutToolPanel.GapType.M_BITES
                    ? CutoutGenerator.generateMouseBites(units, source,
                            result.cutoutParams(), result.biteDiameter(), result.biteSpacing(),
                            result.manualGapAreas(), context::isCancelled)
                    : null;
            context.checkCancelled();
            context.reportProgress(0.95, I18n.text("ui.381985c81e88"));
            return new CutoutJobOutcome(cutout, mouseBites);
        }, camProgress(before));
        runningJob = handle;

        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    if (!acceptCamGeneration(before, handle)) return;
                    CutoutResult cutout = outcome.cutout();
                    if (!sourceAvailable.getAsBoolean()) {
                        appendConsole(I18n.text("ui.b84c886cb4a7"));
                        setStatus(I18n.text("ui.a81cddec6216"), ERROR_COLOR);
                    } else if (cutout.isEmpty()) {
                        appendConsole(I18n.text("ui.ee3e020dba15"));
                        setStatus(I18n.text("ui.712cdeb8cbda"), ERROR_COLOR);
                    } else {
                        appendConsole(String.format(I18n.text("ui.16e9baccfea1"),
                                cutout.partCount(), cutout.totalLength(), Arrays.toString(cutout.bounds())));
                        String name = uniqueDerivedName(item.getValue() + "_cutout");
                        TreeItem<String> generated = addGeometryToProject(name, item.getValue(), units,
                                cutout.geometry(), true,
                                List.of(new ToolGeometry(result.cutoutParams().toolDiameter(), cutout.geometry(), result.profile())), result.machining());
                        preserveSingleJobDefaults(generated, result.jobDefaults());
                        if (result.gapType() == CutoutToolPanel.GapType.THIN) {
                            if (cutout.gapGeometry().isEmpty()) {
                                appendConsole(I18n.text("ui.5720b3bde76b"));
                            } else {
                                String thinName = uniqueDerivedName(item.getValue() + "_cutout_thin");
                                TreeItem<String> thinItem = addGeometryToProject(thinName, item.getValue(), units,
                                        cutout.gapGeometry(), true,
                                        List.of(new ToolGeometry(result.cutoutParams().toolDiameter(),
                                                cutout.gapGeometry(), result.profile())), result.thinMachining());
                                preserveSingleJobDefaults(thinItem, result.jobDefaults());
                                appendConsole(I18n.text("ui.7a0e492eef1d") + thinName
                                        + I18n.text("ui.d5a9a8ecb5a8"));
                            }
                        }
                        if (outcome.mouseBites() != null && !outcome.mouseBites().isEmpty()) {
                            String biteName = uniqueDerivedName(item.getValue() + "_mouse_bites");
                            addExcellonToProject(biteName, null, outcome.mouseBites());
                            appendConsole(I18n.text("ui.4823e3300c8b") + outcome.mouseBites().totalDrills()
                                    + I18n.text("ui.e381fe53f8ca") + biteName);
                        }
                        appendConsole(I18n.text("ui.37c122337b49") + name
                                + I18n.text("ui.d6624cdc5528"));
                        finishCamPanel(before, generated);
                        setStatus(I18n.text("ui.22dda4595045"), IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (runningJob != handle) return;
                        reportJobError(error, I18n.text("ui.09cc9fd6693a"));
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** One tool's contribution plus, optionally, the NCC-wide diameter-validity check - see NccToolPanel.Result. */
    private record NccJobOutcome(NccResult result, OptionalDouble minCopperClearance,
                                 org.flatcam.cam.gcode.MachiningTarget target) {
    }

    /** NCC accepts a Gerber or Geometry source and produces an intermediate Geometry object. */
    private void generateNcc(TreeItem<String> item, GerberImage image) {
        openNccTool(item, image.units(), image.solidGeometry());
    }

    private void generateNcc(TreeItem<String> item, GeometryEntry entry) {
        openNccTool(item, entry.units(), entry.geometry());
    }

    private void openNccTool(TreeItem<String> item, String units, Geometry source) {
        if (source == null || source.isEmpty() || source.getDimension() != 2) {
            appendConsole(I18n.text("ui.b58a53fe6493"));
            return;
        }
        List<NccToolPanel.SourceCandidate> sourceCandidates = new ArrayList<>();
        for (Map.Entry<TreeItem<String>, GerberImage> entry : gerberByItem.entrySet()) {
            if (units.equalsIgnoreCase(entry.getValue().units()) && !entry.getValue().isEmpty()) {
                sourceCandidates.add(new NccToolPanel.SourceCandidate(entry.getKey(), entry.getKey().getValue(),
                        units, true, entry.getValue().solidGeometry()));
            }
        }
        for (Map.Entry<TreeItem<String>, GeometryEntry> entry : geometryByItem.entrySet()) {
            Geometry geometry = entry.getValue().geometry();
            if (units.equalsIgnoreCase(entry.getValue().units()) && geometry != null
                    && !geometry.isEmpty() && geometry.getDimension() == 2) {
                sourceCandidates.add(new NccToolPanel.SourceCandidate(entry.getKey(), entry.getKey().getValue(),
                        units, false, geometry));
            }
        }
        NccToolPanel.SourceCandidate initialSource = sourceCandidates.stream()
                .filter(candidate -> candidate.item() == item).findFirst().orElse(null);
        if (initialSource == null) {
            appendConsole(I18n.text("ui.c44273b5a3e7"));
            return;
        }
        List<NccToolPanel.ReferenceCandidate> referenceCandidates = new ArrayList<>();
        for (Map.Entry<TreeItem<String>, GerberImage> entry : gerberByItem.entrySet()) {
            if (units.equalsIgnoreCase(entry.getValue().units()) && !entry.getValue().isEmpty()) {
                referenceCandidates.add(new NccToolPanel.ReferenceCandidate(
                        entry.getKey(), entry.getKey().getValue(), true, entry.getValue().solidGeometry()));
            }
        }
        for (Map.Entry<TreeItem<String>, GeometryEntry> entry : geometryByItem.entrySet()) {
            Geometry geometry = entry.getValue().geometry();
            if (units.equalsIgnoreCase(entry.getValue().units()) && geometry != null
                    && !geometry.isEmpty() && geometry.getDimension() == 2) {
                referenceCandidates.add(new NccToolPanel.ReferenceCandidate(
                        entry.getKey(), entry.getKey().getValue(), false, geometry));
            }
        }
        openToolPanel("NCC Tool", NccToolPanel.build(sourceCandidates, initialSource, referenceCandidates,
                (chosen, shape, onSelected, onCancelled) ->
                        beginNccAreaSelection(chosen.geometry(), shape, onSelected, onCancelled),
                plotAreaView::cancelPlacement,
                () -> toolsDatabaseTools(LegacyToolsDatabase::nccTools),
                result -> {
                    plotAreaView.cancelPlacement();
                    NccToolPanel.SourceCandidate chosen = result.source();
                    runNccGeneration(chosen.item(), chosen.units(), chosen.geometry(), chosen.gerber(), result);
                }, () -> {
                    plotAreaView.cancelPlacement();
                    closeToolPanel();
                }));
    }

    private boolean beginNccAreaSelection(Geometry source, NccToolPanel.AreaShape shape,
                                          Consumer<Geometry> onSelected, Runnable onCancelled) {
        if (shape == NccToolPanel.AreaShape.POLYGON) {
            return plotAreaView.beginAreaPolygonPlacement(new PlotAreaView.TrackPlacementHandler() {
                @Override
                public void onCommit(List<Coordinate> points) {
                    List<Coordinate> ring = new ArrayList<>();
                    for (Coordinate point : points) {
                        if (ring.isEmpty() || !ring.get(ring.size() - 1).equals2D(point)) {
                            ring.add(new Coordinate(point));
                        }
                    }
                    if (ring.size() > 1 && ring.get(0).equals2D(ring.get(ring.size() - 1))) {
                        ring.remove(ring.size() - 1);
                    }
                    if (ring.size() < 3) {
                        appendConsole(I18n.text("ui.92a62cacb47a"));
                        onCancelled.run();
                        return;
                    }
                    ring.add(new Coordinate(ring.get(0)));
                    try {
                        Geometry area = source.getFactory().createPolygon(ring.toArray(new Coordinate[0]));
                        if (!area.isValid() || area.getArea() <= 0) {
                            throw new IllegalArgumentException(I18n.text("ui.6a1377c2a0aa"));
                        }
                        onSelected.accept(area);
                    } catch (RuntimeException error) {
                        appendConsole(I18n.text("ui.9d72af057bc5") + error.getMessage());
                        onCancelled.run();
                    }
                }

                @Override
                public void onCancel() {
                    onCancelled.run();
                }
            });
        }
        double[] anchor = new double[2];
        return plotAreaView.beginAreaRectanglePlacement(new PlotAreaView.PlacementHandler() {
            @Override
            public void onAnchorChosen(double x, double y) {
                anchor[0] = x;
                anchor[1] = y;
            }

            @Override
            public void onCommit(double dx, double dy) {
                if (dx == 0 || dy == 0) {
                    onCancelled.run();
                    return;
                }
                onSelected.accept(source.getFactory().toGeometry(
                        new Envelope(anchor[0], anchor[0] + dx, anchor[1], anchor[1] + dy)));
            }

            @Override
            public void onCancel() {
                onCancelled.run();
            }
        });
    }

    private void runNccGeneration(TreeItem<String> item, String units, Geometry source, boolean gerberSource,
                                  NccToolPanel.Result panelResult) {
        CamGenerationState before;
        try {
            if (gerberSource != gerberByItem.containsKey(item))
                throw new IllegalStateException(I18n.text("ui.594d9869fd6d"));
            var reference = panelResult.reference();
            Geometry referenceGeometry = panelResult.parameters().boundary() instanceof NccBoundary.ReferenceGerber area
                    ? area.geometry() : panelResult.parameters().boundary() instanceof NccBoundary.ReferenceGeometry area
                    ? area.geometry() : null;
            before = captureCamGeneration(item, source, reference == null ? null : reference.item(), referenceGeometry);
        } catch (IllegalStateException invalid) { appendConsole(invalid.getMessage()); return; }

        NccParameters params = panelResult.parameters();
        beginJob(I18n.text("ui.6aba42688d88"));
        activeCamGeneration = before;
        JobHandle<NccJobOutcome> handle = jobExecutor.submit(context -> {
            OptionalDouble minClearance = gerberSource && panelResult.checkValidity()
                    ? NccGenerator.minimumCopperClearance(source)
                    : OptionalDouble.empty();
            NccResult result = NccGenerator.generate(units, source, params,
                    context::isCancelled,
                    fraction -> context.reportProgress(fraction, I18n.text("ui.6aba42688d88")));
            context.checkCancelled();
            var ordered = result.toolResults().stream().filter(tool -> !tool.isEmpty())
                    .map(tool -> new ToolGeometry(tool.toolDiameter(), tool.geometry())).toList();
            var areas = new LinkedHashMap<Integer, Geometry>(); int index = 0;
            for (var tool : result.toolResults()) if (!tool.isEmpty()) {
                if (tool.operation() == NccOperation.CLEAR && tool.targetArea() != null) areas.put(index, tool.targetArea());
                index++;
            }
            var target = areas.isEmpty() ? null : new org.flatcam.cam.gcode.MachiningTarget("NCC", units,
                    result.clearingArea(), areas, org.flatcam.cam.gcode.MachiningTarget.signature(ordered, context::isCancelled));
            return new NccJobOutcome(result, minClearance, target);
        }, camProgress(before));
        runningJob = handle;

        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    if (!acceptCamGeneration(before, handle)) return;
                    if (gerberSource ? !gerberByItem.containsKey(item) : !geometryByItem.containsKey(item)) {
                        appendConsole(I18n.text("ui.397db2003da2"));
                        setStatus(I18n.text("ui.a81cddec6216"), ERROR_COLOR);
                        onJobFinished();
                        return;
                    }
                    NccResult result = outcome.result();
                    outcome.minCopperClearance().ifPresent(minClearance -> {
                        boolean anySuitable = java.util.stream.Stream.concat(params.toolDiameters().stream(),
                                params.isolationToolDiameters().stream()).anyMatch(d -> d <= minClearance);
                        appendConsole(String.format(java.util.Locale.ROOT,
                                anySuitable
                                        ? I18n.text("ui.0bc88807eb14")
                                        : I18n.text("ui.180ecdcc9672"),
                                minClearance));
                    });
                    for (var toolResult : result.toolResults()) {
                        if (toolResult.operation() == NccOperation.ISO && toolResult.isEmpty()) {
                            appendConsole(I18n.text("ui.ca5836c6bd4e") + toolResult.toolDiameter()
                                    + I18n.text("ui.71a41d97bc9f"));
                        }
                    }
                    if (params.isolationToolDiameters().stream().anyMatch(d -> d > params.margin())) {
                        appendConsole(I18n.text("ui.f278fd2166e3"));
                    }
                    if (result.isEmpty()) {
                        appendConsole(I18n.text("ui.f0f8f91651f8"));
                        setStatus(I18n.text("ui.712cdeb8cbda"), ERROR_COLOR);
                    } else {
                        String name = uniqueDerivedName(item.getValue() + "_ncc");
                        List<ToolGeometry> tools = result.toolResults().stream()
                                .filter(toolResult -> !toolResult.isEmpty())
                                .map(toolResult -> new ToolGeometry(toolResult.toolDiameter(),
                                        toolResult.geometry(), panelResult.toolProfiles().getOrDefault(
                                                toolResult.toolDiameter(), ToolProfile.C1)))
                                .toList();
                        TreeItem<String> generated = addGeometryToProject(name, item.getValue(), units,
                                result.geometry(), true, tools);
                        applyCamMachining(generated, panelResult.machining());
                        var created = geometryByItem.get(generated);
                        geometryByItem.put(generated, new GeometryEntry(created.sourceName(), created.units(), created.geometry(),
                                created.strokeOnly(), created.tools(), created.cncDefaults(), outcome.target()));
                        appendConsole(String.format(
                                I18n.text("ui.cb92cd552a9f"),
                                result.pathCount(), result.totalLength(), result.toolResults().size(),
                                result.totalFailedPolygonCount(), Arrays.toString(result.bounds())));
                        finishCamPanel(before, generated);
                        setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (runningJob != handle) return;
                        reportJobError(error, I18n.text("ui.e20b661a7e7c"));
                        onJobFinished();
                    });
                    return null;
                });
    }

    private void generateGeometryCncJob(TreeItem<String> item, GeometryEntry entry) {
        generateGeometryCncJob(item, entry, null);
    }

    private void generateGeometryCncJob(TreeItem<String> item, GeometryEntry entry, GeometryCncToolPanel.Result draft) {
        GeometryCncSettings settings = draft == null ? geometryCncSettingsByItem.get(item)
                : new GeometryCncSettings(draft.preprocessor(), entry.tools().isEmpty() ? draft.tools().getFirst().toolDiameter() : null,
                        draft.vTools(), draft.parametersByTool(), draft.tools().getFirst().toolProfile());
        openToolPanel("Geometry CNC Job", GeometryCncToolPanel.build(entry.units(), entry.geometry(),
                draft == null || entry.tools().isEmpty() ? entry.tools() : draft.tools(),
                draft == null ? entry.cncDefaults() : draft.parameters(), settings,
                () -> toolsDatabaseTools(LegacyToolsDatabase::millingTools),
                (polygon,onSelected,onCancelled) -> beginNccAreaSelection(entry.geometry(),polygon?NccToolPanel.AreaShape.POLYGON:NccToolPanel.AreaShape.RECTANGLE,onSelected,onCancelled),
                shape -> plotAreaView.setEditorHighlight(shape,false),
                geometryByItem.entrySet().stream().filter(e -> CncRoutingFields.isArea(e.getValue().geometry()))
                        .map(e -> new CncRoutingFields.Reference(e.getKey().getValue(),e.getValue().units(),e.getValue().geometry())).toList(),
                result -> { plotAreaView.cancelPlacement(); clearToolOverlays(); runGeometryCncGeneration(item,entry,result); },
                () -> { plotAreaView.cancelPlacement(); clearToolOverlays(); closeToolPanel(); }));
        activeToolCleanup=this::clearToolOverlays;
    }

    private void runGeometryCncGeneration(TreeItem<String> item, GeometryEntry entry,
                                          GeometryCncToolPanel.Result result) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }

        if (!confirmCncReview(CncPreflight.geometry(item.getValue(), entry.units(), result),
                entry.machiningTarget() == null || result.preprocessor().isLaser() || result.preprocessor().isPlotter() || result.preprocessor().requiresProbe()
                        ? null : () -> showCncCoverage(item, entry, result))) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.34806b17f23b"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("ui.abef442239ce") + I18n.caption(result.preprocessor().label()),
                result.preprocessor().filePatterns()));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_cnc" + result.preprocessor().fileExtension());
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File outFile = chooser.showSaveDialog(scene.getWindow());
        if (outFile == null) {
            return;
        }

        startGeometryCncGeneration(item, entry, result, outFile.toPath());
    }

    private boolean confirmCncReview(CncPreflight.Report report) {
        return confirmCncReview(report, null);
    }
    private boolean confirmCncReview(CncPreflight.Report report, Runnable coverage) {
        Node panel = toolTab.getContent();
        long epoch = tclProjectEpoch;
        var decision = new CncReviewDialog(scene.getWindow(), currentTheme, report, coverage != null).showAndWait().orElse(null);
        if (decision == null) return false;
        if (epoch != tclProjectEpoch || panel != toolTab.getContent() || runningJob != null) {
            appendConsole(I18n.text("ui.b052bb65b800"));
            return false;
        }
        if (decision.edit() != null) {
            if ("__coverage".equals(decision.edit().field()) && coverage != null) { coverage.run(); return false; }
            setSidebarVisible(true); leftTabs.getSelectionModel().select(toolTab);
            Platform.runLater(() -> {
                if (panel == toolTab.getContent() && !CncReviewNavigation.focus(panel, decision.edit()))
                    appendConsole(I18n.text("ui.d6b8d9c51a8e") + decision.edit().field());
            });
        }
        return decision.proceed() && report.canContinue();
    }

    private void showCncCoverage(TreeItem<String> item, GeometryEntry entry, GeometryCncToolPanel.Result draft) {
        if (entry.machiningTarget() == null || geometryByItem.get(item) != entry || runningJob != null)
            throw new IllegalStateException(I18n.text("ui.9e02c0636f5d"));
        if (draft.preprocessor().isLaser() || draft.preprocessor().isPlotter() || draft.preprocessor().requiresProbe())
            throw new IllegalArgumentException(I18n.text("ui.35a7f2b8cfd7"));
        var owned = new java.util.concurrent.atomic.AtomicReference<JobHandle<?>>();
        var panel = new CncCoveragePanel(item.getValue(), entry.units(), draft.tools(), plotAreaView::setCoverageResidual,
                () -> {
                    if (geometryByItem.get(item) == entry) generateGeometryCncJob(item, entry, draft);
                    else { appendConsole(I18n.text("ui.c2179dabf8e0")); closeToolPanel(); }
                }, () -> { var handle = owned.get(); if (handle != null) handle.cancel(); });
        openToolPanel(I18n.text("ui.0a2d821cb334")+entry.machiningTarget().operation(), panel);
        var before = captureCamGeneration(item, entry.geometry(), null, null);
        beginJob(I18n.text("ui.e2b90d554a97")); activeCamGeneration = before;
        var handle = jobExecutor.submit(context -> org.flatcam.cam.gcode.MachiningCoverage.calculate(entry.machiningTarget(),
                entry.units(), draft.tools(), draft.parameters(), draft.parametersByTool(), context::isCancelled,
                fraction -> context.reportProgress(fraction, I18n.text("ui.92eeeaa49996"))), camProgress(before));
        owned.set(handle); runningJob = handle;
        activeToolCleanup = () -> { handle.cancel(); clearToolOverlays(); };
        handle.completion().thenAccept(value -> Platform.runLater(() -> {
            if (runningJob != handle) return;
            if (toolTab.getContent() != before.panel()) { onJobFinished(); return; }
            try {
                if (acceptCamGeneration(before, handle) && toolTab.getContent() == before.panel()) {
                    panel.complete(value); setStatus(I18n.text("ui.6839c6faa51a"), IDLE_COLOR);
                } else panel.failed(I18n.text("ui.52cf908bfb62"));
            } catch (CancellationException cancelled) { panel.failed(I18n.text("ui.e1999244252c")); }
            finally { if (runningJob == handle) onJobFinished(); }
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                if (runningJob != handle) return;
                if (toolTab.getContent() == before.panel()) panel.failed(isCancellation(error) ? I18n.text("ui.e1999244252c")
                        : I18n.text("ui.641b33a381cd") + (error.getCause() == null ? error.getMessage() : error.getCause().getMessage()));
                reportJobError(error, I18n.text("ui.3ffe8b4e4541")); onJobFinished();
            }); return null;
        });
    }

    private void preserveSingleJobDefaults(TreeItem<String> item, org.flatcam.app.project.CncJobDefaults defaults) {
        if (defaults.isEmpty()) return;
        geometryCncSettingsByItem.put(item, new GeometryCncSettings(GCodePreprocessor.FX_PORTABLE, null,
                Map.of(), Map.of(), geometryByItem.get(item).tools().getFirst().toolProfile(), Map.of(0, defaults)));
    }

    private void startGeometryCncGeneration(TreeItem<String> item, GeometryEntry entry,
                                            GeometryCncToolPanel.Result result, Path output) {
        CamGenerationState before = captureCamGeneration(item, entry.geometry(), null, null);
        if (geometryByItem.get(item) != entry) throw new IllegalStateException(I18n.text("ui.d427bbb9ab55"));
        GeometryCncSettings savedSettings = geometryCncSettingsByItem.get(item);
        Runnable validate = () -> {
            if (tclProjectEpoch != before.epoch() || geometryByItem.get(item) != entry
                    || !before.inputs().getFirst().name().equals(item.getValue())
                    || !Objects.equals(savedSettings, geometryCncSettingsByItem.get(item)) || camEditorActive())
                throw new IllegalStateException(I18n.text("ui.00b1758ad34c"));
        };
        validate.run();
        String sourceName = item.getValue();
        beginJob(I18n.text("ui.abecd4197ad1"));
        activeCamGeneration = before;
        JobHandle<GeometryCncGeneration.Generated> handle = jobExecutor.submit(context -> TclExecution.run(context,
                () -> GeometryCncGeneration.generate(entry.units(), result.tools(), result.parameters(), result.vTools(),
                        result.parametersByTool(), result.preprocessor(), output, context,
                        () -> TclExecution.onFx(() -> { validate.run(); return null; }))), camProgress(before));
        runningJob = handle;

        handle.completion()
                .thenAccept(generated -> Platform.runLater(() -> {
                    if (runningJob != handle) return;
                    try {
                        if (handle.isCancelled()) throw new CancellationException();
                        validate.run();
                        CncJobResult job = generated.job();
                        geometryByItem.put(item, new GeometryEntry(entry.sourceName(), entry.units(), entry.geometry(),
                                entry.strokeOnly(), entry.tools().isEmpty() ? entry.tools() : result.tools(), result.parameters(), entry.machiningTarget()));
                        geometryCncSettingsByItem.put(item, new GeometryCncSettings(result.preprocessor(),
                                entry.tools().isEmpty() ? result.tools().getFirst().toolDiameter() : null,
                                result.vTools(), result.parametersByTool(), result.tools().getFirst().toolProfile()));
                        AppPreferences.saveLastCamDirectory(output.toAbsolutePath().getParent().toString());
                        appendConsole(I18n.text("ui.d81048624959") + output
                                + " (" + job.gcode().lines().count() + I18n.text("ui.d1862403f7c1"));
                        GCodeToolpathParser.Result preview = generated.preview();
                        if (preview != null && preview.warning() != null)
                            appendConsole(output.getFileName() + ": " + preview.warning());
                        boolean centerlines = preview != null && preview.plotAvailable() && preview.stats() != null
                                && preview.stats().cutterDiameter() != null;
                        // Programs that state their cutter width get the fast stroked preview, like imported ones.
                        TreeItem<String> cncItem = addCncJobToProject(output.getFileName().toString(), sourceName,
                                output, job.gcode(), job.travelGeometry(), job.cutGeometry(),
                                centerlines ? preview.travelCenterlines() : null,
                                centerlines ? preview.cutCenterlines() : null,
                                centerlines ? previewWidthFor(preview) : 0,
                                preview == null ? null : preview.stats());
                        if (toolTab.getContent() == before.panel()) {
                            selectProjectItem(cncItem);
                            focusCncJob(cncItem, cncJobByItem.get(cncItem));
                            closeToolPanel();
                        }
                        updateProgress(1);
                        setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
                    } catch (RuntimeException failure) { reportJobError(failure, I18n.text("ui.a5ec074cf002")); }
                    finally { onJobFinished(); }
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (runningJob != handle) return;
                        reportJobError(error, I18n.text("ui.81ef77c5da86"));
                        onJobFinished();
                    });
                    return null;
                });
    }

    private void removeFromProject(TreeItem<String> item, Map<TreeItem<String>, ?> byItem) {
        if (geometryEditor.isEditing(item) && geometryEditor.isBusy()) {
            appendConsole(I18n.text("ui.30c1ee6293f7"));
            return;
        }
        if (gcodeEditor.isEditing(item) && gcodeEditor.hasUnappliedChanges()) {
            appendConsole(I18n.text("ui.e5b6dd53f80b"));
            return;
        }
        if (geometryEditor.isEditing(item) && geometryEditor.hasUnappliedChanges()) {
            appendConsole(I18n.text("ui.a8b38c6cf6d2"));
            return;
        }
        if (excellonEditor.isEditing(item) && excellonEditor.hasUnappliedChanges()) {
            appendConsole(I18n.text("ui.bc0bc8c6a6cb"));
            return;
        }
        plotMoveHistory.clear();
        gerberEditor.cancelIfEditing(item);
        geometryEditor.cancelIfEditing(item);
        excellonEditor.cancelIfEditing(item);
        gcodeEditor.cancelIfEditing(item);
        item.getParent().getChildren().remove(item);
        byItem.remove(item);
        drillDefaultsByItem.remove(item);
        drillCncSettingsByItem.remove(item);
        geometryCncSettingsByItem.remove(item);
        gerberFollowItems.remove(item);
        sourcePathByItem.remove(item);
        plotAreaView.removeLayer(item);
        plotAreaView.removeLayer(new MarkLayerKey(item));
        plotAreaView.removeLayer(new CncTravelLayerKey(item));
        plotAreaView.removeLayer(new CncCutLayerKey(item));
        plotAreaView.setAnnotations(new CncAnnotationKey(item), List.of());
        plotAreaView.setArrows(new CncAnnotationKey(item), List.of());
        hiddenCncTools.remove(item);
        cncAnnotationsOff.remove(item);
        cncArrowsOff.remove(item);
        cncPlotSettings.remove(item);
        refreshPlotSelectionOutline();
        appendConsole(I18n.text("ui.7a3b7f7244b1") + item.getValue());
    }

    /**
     * Swaps in a per-object panel matching appGUI/ObjectUI.py's GerberObjectUI/
     * ExcellonObjectUI (shown via ObjectCollection.on_list_selection_change ->
     * FlatCAMObj.build_ui() swapping the object's own persistent UI widget
     * into the Properties scroll area). Rebuilt from scratch on every
     * selection change instead of one persistent widget per object - this
     * app doesn't keep a live UI instance per object the way the legacy one
     * does, but the effect (the right panel for whatever is selected) is the
     * same. The remaining deliberate omissions are the object editor and
     * transformations, which need a mutable, persistent object model.
     */
    private void showProperties(TreeItem<String> item) {
        Node content;
        GerberImage gerberImage = item == null ? null : gerberByItem.get(item);
        ExcellonImage excellonImage = item == null ? null : excellonByItem.get(item);
        GeometryEntry geometry = item == null ? null : geometryByItem.get(item);
        CncJobEntry cncJob = item == null ? null : cncJobByItem.get(item);
        if (gerberImage != null) {
            content = buildGerberPropertiesPanel(item, gerberImage);
        } else if (excellonImage != null) {
            content = buildExcellonPropertiesPanel(item, excellonImage);
        } else if (geometry != null) {
            content = buildGeometryPropertiesPanel(item, geometry);
        } else if (cncJob != null) {
            content = buildCncJobPropertiesPanel(item, cncJob);
        } else {
            content = propertiesPlaceholder;
        }
        if (content == propertiesPlaceholder) {
            propertiesContainer.getChildren().setAll(content);
            return;
        }
        PanelTooltips.install(content, gerberImage != null ? "Gerber Object"
                : excellonImage != null ? "Excellon Object" : geometry != null ? "Geometry Object" : "CNC Job Object");
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("object-panel-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        propertiesContainer.getChildren().setAll(scroll);
    }

    /** "Gerber Object" header, Plot Options (Solid/Multi-Color), Name, Plot, Properties, Isolation Routing - see ObjectUI.py's GerberObjectUI. */
    private Node buildGerberPropertiesPanel(TreeItem<String> item, GerberImage image) {
        VBox box = objectPropertiesHeader("Gerber Object", GERBER_FILL, "flatcam_icon32.png");

        CheckBox solidCb = new CheckBox(I18n.text("ui.40ca66a274a1"));
        solidCb.setSelected(plotAreaView.isLayerFilled(item));
        solidCb.setOnAction(e -> plotAreaView.setLayerFilled(item, solidCb.isSelected()));
        CheckBox multicolorCb = new CheckBox(I18n.text("ui.1cb879e1057d"));
        multicolorCb.setSelected(plotAreaView.isLayerMulticolor(item));
        multicolorCb.setOnAction(e -> plotAreaView.setLayerMulticolor(item, multicolorCb.isSelected()));
        box.getChildren().add(labeledRow(I18n.text("ui.24bf083c7f90"), solidCb, multicolorCb));

        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setId("object-plot");
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        CheckBox followCb = new CheckBox("Follow");
        followCb.setTooltip(new Tooltip(I18n.text("ui.c0705881d87d")));
        followCb.setSelected(gerberFollowItems.contains(item));
        followCb.setOnAction(e -> {
            setGerberFollow(item, image, followCb.isSelected());
            refreshPlotSelectionOutline();
        });
        box.getChildren().add(labeledRow("Plot:", plotCb, followCb));

        Button isolationButton = new Button("Isolation Routing");
        isolationButton.setGraphic(legacyIcon("iso_16.png", 16));
        isolationButton.getStyleClass().add("primary-action");
        isolationButton.setMaxWidth(Double.MAX_VALUE);
        isolationButton.setOnAction(e -> generateIsolation(item, image));
        box.getChildren().add(isolationButton);

        Button nccButton = new Button("NCC Tool");
        nccButton.setGraphic(legacyIcon("eraser26.png", 18));
        nccButton.setMaxWidth(Double.MAX_VALUE);
        nccButton.setOnAction(e -> generateNcc(item, image));
        box.getChildren().add(nccButton);

        Button cutoutButton = new Button("Cutout Tool");
        cutoutButton.setGraphic(legacyIcon("cut32_bis.png", 18));
        cutoutButton.setMaxWidth(Double.MAX_VALUE);
        cutoutButton.setOnAction(e -> generateCutout(item, image));
        box.getChildren().add(cutoutButton);

        box.getChildren().add(buildGerberUtilities(item, image));

        TitledPane apertures = new TitledPane("Apertures Table", buildAperturesTableSection(item, image));
        apertures.setId("gerber-apertures-pane");
        apertures.setExpanded(ToolDefaults.flag("display.gerber.aperturetable"));
        ToolDescriptions.apply(apertures, I18n.text("ui.77288c427fa7"), I18n.text("ui.4f01d857e180"));
        box.getChildren().add(apertures);

        box.getChildren().add(new Label(I18n.text("ui.ff15712ed443")));
        box.getChildren().add(transformationsSection(item));

        box.getChildren().add(propertiesSection(String.format(
                I18n.text("ui.84957fe534de"),
                image.units(), image.apertures().size(), image.totalArea(), Arrays.toString(image.bounds())
        )));
        return box;
    }

    /** Follow, non-copper and bounding-box Geometry generators from GerberObjectUI's Utilities section. */
    private TitledPane buildGerberUtilities(TreeItem<String> item, GerberImage image) {
        VBox content = new VBox(7);
        content.setPadding(new Insets(8));

        Button followButton = new Button(I18n.text("ui.510485b4ef3e"));
        followButton.setGraphic(legacyIcon("geometry32.png", 16));
        followButton.setMaxWidth(Double.MAX_VALUE);
        followButton.setOnAction(e -> addDerivedGeometry(item, image, "_follow", image.followGeometry(), true));

        Label nonCopperLabel = new Label(I18n.text("ui.7033045b83cc"));
        nonCopperLabel.setStyle("-fx-font-weight: bold;");
        TextField nonCopperMargin = marginField();
        CheckBox nonCopperRounded = new CheckBox(I18n.text("ui.6851e7e583e8"));
        Button nonCopperButton = new Button(I18n.text("ui.57e96635630c"));
        ToolDescriptions.apply(nonCopperButton,I18n.text("ui.6d7b3965e71a"), I18n.text("ui.2d7f6178d48c"));
        nonCopperButton.setGraphic(legacyIcon("geometry32.png", 18));
        nonCopperButton.setOnAction(e -> generateGerberUtility(item, image, "_noncopper",
                nonCopperMargin, nonCopperRounded.isSelected(), true));

        Label bboxLabel = new Label(I18n.text("ui.2aaecdfdfac4"));
        bboxLabel.setStyle("-fx-font-weight: bold;");
        TextField bboxMargin = marginField();
        CheckBox bboxRounded = new CheckBox(I18n.text("ui.6851e7e583e8"));
        Button bboxButton = new Button(I18n.text("ui.57e96635630c"));
        ToolDescriptions.apply(bboxButton,I18n.text("ui.1d65802d2957"), I18n.text("ui.bfb4fa3f1bdd"));
        bboxButton.setGraphic(legacyIcon("geometry32.png", 18));
        bboxButton.setOnAction(e -> generateGerberUtility(item, image, "_bbox",
                bboxMargin, bboxRounded.isSelected(), false));

        content.getChildren().addAll(followButton, new Separator(), nonCopperLabel,
                labeledRow("Boundary Margin:", nonCopperMargin),
                labeledRow("", nonCopperRounded, nonCopperButton), new Separator(), bboxLabel,
                labeledRow("Boundary Margin:", bboxMargin), labeledRow("", bboxRounded, bboxButton));
        TitledPane pane = new TitledPane(I18n.text("ui.66fbad458b07"), content);
        pane.setGraphic(legacyIcon("settings18.png", 18));
        pane.setExpanded(false);
        return pane;
    }

    private static TextField marginField() {
        TextField field = new TextField("0.0");
        field.setPrefColumnCount(7);
        return field;
    }

    private void generateGerberUtility(TreeItem<String> item, GerberImage image, String suffix,
                                       TextField marginField, boolean rounded, boolean nonCopper) {
        try {
            double margin = Double.parseDouble(marginField.getText().trim().replace(',', '.'));
            Geometry generated = nonCopper
                    ? GerberGeometryGenerator.nonCopper(image, margin, rounded)
                    : GerberGeometryGenerator.boundingBox(image, margin, rounded);
            addDerivedGeometry(item, image, suffix, generated, false);
        } catch (RuntimeException ex) {
            appendConsole(I18n.text("ui.4bf6a942bd12") + ex.getMessage());
            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
        }
    }

    private void addDerivedGeometry(TreeItem<String> sourceItem, GerberImage image, String suffix,
                                    Geometry geometry, boolean strokeOnly) {
        if (geometry == null || geometry.isEmpty()) {
            appendConsole("Geometry " + suffix + I18n.text("ui.49d1a6915ac4"));
            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
            return;
        }
        String name = uniqueDerivedName(sourceItem.getValue() + suffix);
        TreeItem<String> generated = addGeometryToProject(name, sourceItem.getValue(), image.units(), geometry, strokeOnly);
        appendConsole(I18n.text("ui.2ba217335e67") + name);
        selectProjectItem(generated);
        plotAreaView.fitToLayer(generated);
        setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
    }

    private String uniqueDerivedName(String base) {
        if (!projectObjectNameExists(base)) {
            return base;
        }
        int suffix = 2;
        while (projectObjectNameExists(base + "_" + suffix)) {
            suffix++;
        }
        return base + "_" + suffix;
    }

    /**
     * Wires GerberAperturesTable's per-row "Mark" checkboxes to a highlight
     * overlay layer - purely visual, matching the confirmed Python behavior
     * (no tool reads mark state, see GerberAperturesTable's class doc).
     * Resets any marks left over from a previous view of this same object's
     * panel first, so the checkboxes (which always start unchecked, since
     * the panel is rebuilt from scratch on every selection) never disagree
     * with what's actually highlighted.
     */
    private Node buildAperturesTableSection(TreeItem<String> item, GerberImage image) {
        MarkLayerKey markKey = new MarkLayerKey(item);
        plotAreaView.removeLayer(markKey);
        Set<String> markedCodes = new LinkedHashSet<>();
        return GerberAperturesTable.build(image, (code, marked) -> {
            if (marked) {
                markedCodes.add(code);
            } else {
                markedCodes.remove(code);
            }
            if (markedCodes.isEmpty()) {
                plotAreaView.removeLayer(markKey);
                return;
            }
            List<Geometry> shapes = markedCodes.stream()
                    .map(c -> image.apertureGeometry().get(c))
                    .filter(Objects::nonNull)
                    .toList();
            Geometry union = shapes.size() == 1 ? shapes.get(0) : OverlayNGRobust.union(shapes);
            plotAreaView.putLayer(markKey, PlotAreaView.LayerCategory.OVERLAY, union, MARK_COLOR, MARK_COLOR, false);
        });
    }

    /** ExcellonObjectUI's Basic layout: editor, properties, tool totals, actions, utilities and transforms. */
    private Node buildExcellonPropertiesPanel(TreeItem<String> item, ExcellonImage image) {
        VBox box = objectPropertiesHeader("Excellon Object", DRILL_FILL, "drill32.png");

        CheckBox solidCb = new CheckBox(I18n.text("ui.40ca66a274a1"));
        solidCb.setSelected(plotAreaView.isLayerFilled(item));
        solidCb.setOnAction(e -> plotAreaView.setLayerFilled(item, solidCb.isSelected()));
        CheckBox multicolorCb = new CheckBox(I18n.text("ui.1cb879e1057d"));
        multicolorCb.setSelected(plotAreaView.isLayerMulticolor(item));
        multicolorCb.setOnAction(e -> plotAreaView.setLayerMulticolor(item, multicolorCb.isSelected()));
        box.getChildren().add(labeledRow(I18n.text("ui.24bf083c7f90"), solidCb, multicolorCb));

        box.getChildren().add(nameRow(item));

        Button editButton = new Button("Excellon Editor");
        editButton.setGraphic(legacyIcon("edit_file32.png", 18));
        editButton.setMaxWidth(Double.MAX_VALUE);
        editButton.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedExcellon();
        });
        box.getChildren().add(editButton);

        box.getChildren().add(propertiesSection(String.format(
                I18n.text("ui.96f3610043b2"),
                image.units(), image.totalDrills(), image.totalSlots(), Arrays.toString(image.bounds())
        )));

        CheckBox plotCb = new CheckBox("Plot");
        plotCb.setId("object-plot");
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setTooltip(new Tooltip(I18n.text("ui.bfcaabfdbb64")));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        Label tableLabel = new Label(I18n.text("ui.b96a67bfde3e"));
        tableLabel.setStyle("-fx-font-weight: bold;");
        Region tableHeaderSpacer = new Region();
        HBox.setHgrow(tableHeaderSpacer, Priority.ALWAYS);
        HBox tableHeader = new HBox(8, tableLabel, tableHeaderSpacer, plotCb);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(tableHeader);
        box.getChildren().add(ExcellonObjectToolsTable.build(image));

        box.getChildren().add(new Separator());
        Label toolsLabel = new Label(I18n.text("ui.429e6b85ecff"));
        toolsLabel.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(toolsLabel);

        Button gcodeButton = new Button("Drilling Tool");
        gcodeButton.getStyleClass().add("primary-action");
        gcodeButton.setGraphic(legacyIcon("drilling_tool32.png", 18));
        gcodeButton.setMaxWidth(Double.MAX_VALUE);
        gcodeButton.setOnAction(e -> generateDrillGCode(item, image));
        box.getChildren().add(gcodeButton);

        Button millingButton = new Button("Milling Tool");
        millingButton.setGraphic(legacyIcon("milling_tool32.png", 18));
        millingButton.setMaxWidth(Double.MAX_VALUE);
        millingButton.setTooltip(new Tooltip(I18n.text("ui.0bcad6293908")));
        millingButton.setOnAction(e -> generateExcellonMilling(item, image));
        box.getChildren().add(millingButton);

        VBox utilitiesContent = new VBox(8);
        utilitiesContent.setPadding(new Insets(8));
        Label utilitiesNote = new Label(I18n.text("ui.8e3eb6a1c297"));
        utilitiesNote.setWrapText(true);
        utilitiesContent.getChildren().add(utilitiesNote);
        TitledPane utilities = new TitledPane(I18n.text("ui.66fbad458b07"), utilitiesContent);
        utilities.setGraphic(legacyIcon("settings18.png", 18));
        utilities.setExpanded(false);
        box.getChildren().add(new Separator());
        box.getChildren().add(utilities);

        box.getChildren().add(new Label(I18n.text("ui.32788c0c09e0")));
        box.getChildren().add(transformationsSection(item));
        return box;
    }

    private Node buildGeometryPropertiesPanel(TreeItem<String> item, GeometryEntry entry) {
        VBox box = objectPropertiesHeader("Geometry Object", GEOMETRY_STROKE, "geometry32.png");
        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setId("object-plot");
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button cncButton = new Button(I18n.text("ui.3037c711edf6"));
        cncButton.getStyleClass().add("primary-action");
        cncButton.setGraphic(legacyIcon("cnc16.png", 16));
        cncButton.setMaxWidth(Double.MAX_VALUE);
        cncButton.setOnAction(e -> generateGeometryCncJob(item, entry));
        box.getChildren().add(cncButton);

        Button nccButton = new Button("NCC Tool");
        nccButton.setGraphic(legacyIcon("eraser26.png", 16));
        nccButton.setMaxWidth(Double.MAX_VALUE);
        nccButton.setOnAction(e -> generateNcc(item, entry));
        box.getChildren().add(nccButton);

        if (entry.geometry() != null && !entry.geometry().isEmpty() && entry.geometry().getDimension() == 2) {
            Button cutoutButton = new Button("Cutout Tool");
            cutoutButton.setGraphic(legacyIcon("cut32_bis.png", 16));
            cutoutButton.setMaxWidth(Double.MAX_VALUE);
            cutoutButton.setOnAction(e -> generateCutout(item, entry));
            box.getChildren().add(cutoutButton);
        }

        Button editButton = new Button(I18n.text("ui.aa5faa2dd7cd"));
        editButton.setGraphic(legacyIcon("edit_file32.png", 16));
        editButton.setMaxWidth(Double.MAX_VALUE);
        editButton.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGeometry();
        });
        box.getChildren().add(editButton);

        box.getChildren().add(new Label(I18n.text("ui.ff15712ed443")));
        box.getChildren().add(transformationsSection(item));

        Envelope envelope = entry.geometry().getEnvelopeInternal();
        double[] bounds = entry.geometry().isEmpty() ? null
                : new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
        String toolText = entry.tools().isEmpty() ? "" : String.format(I18n.text("ui.49e219c9bb85"), entry.tools().stream()
                .map(tool -> UiNumbers.fixed(tool.toolDiameter(),entry.units())).collect(java.util.stream.Collectors.joining(", ")));
        box.getChildren().add(propertiesSection(String.format(
                I18n.text("ui.3ed193e32442"),
                entry.sourceName(), entry.units(), toolText, entry.geometry().getArea(), entry.geometry().getLength(),
                Arrays.toString(bounds))));
        return box;
    }

    /**
     * "CNC Job Object" header, Plot Kind (All/Travel/Cut - ObjectUI.py's
     * cncplot_method_combo) and Plot, Ver G-code, Properties - see
     * ObjectUI.py's CNCObjectUI. Plot Kind/Plot stay disabled when the entry
     * has no toolpath geometry (a reloaded project - see CncJobEntry's doc).
     */
    private Node buildCncJobPropertiesPanel(TreeItem<String> item, CncJobEntry entry) {
        VBox box = objectPropertiesHeader("CNC Job Object", ISOLATION_COLOR, "cnc32.png");
        box.getChildren().add(nameRow(item));

        boolean hasGeometry = isPlottable(item);
        CncTravelLayerKey travelKey = new CncTravelLayerKey(item);
        CncCutLayerKey cutKey = new CncCutLayerKey(item);
        boolean travelVisible = cncLayerVisible(item, true);
        boolean cutVisible = cncLayerVisible(item, false);

        ComboBox<String> kindCombo = new ComboBox<>();
        kindCombo.setId("object-plot-kind");
        kindCombo.getItems().addAll("All", "Travel", "Cut");
        String storedKind=cncPlotSettings.getOrDefault(item,org.flatcam.app.project.CncPlotSettings.legacy()).kind();
        kindCombo.setValue(storedKind.equals("ALL")?"All":storedKind.equals("CUT")?"Cut":"Travel");
        kindCombo.setDisable(!hasGeometry);

        CheckBox plotCb = new CheckBox();
        plotCb.setId("object-plot");
        plotCb.setSelected(travelVisible || cutVisible);
        plotCb.setDisable(!hasGeometry);

        Runnable applyVisibility = () -> {
            if (Boolean.TRUE.equals(kindCombo.getProperties().get("syncing-plot-kind"))) return;
            boolean visible = plotCb.isSelected();
            String kind = kindCombo.getValue();
            var previous=snapshotCncPlotSettings(item);
            cncPlotSettings.put(item,new org.flatcam.app.project.CncPlotSettings(previous.cutFill(),previous.cutLine(),previous.travelFill(),previous.travelLine(),kind.toUpperCase(java.util.Locale.ROOT),previous.annotations(),previous.arrows(),previous.fontSize(),previous.fontColor()));
            plotAreaView.setLayerVisible(travelKey, visible && !"Cut".equals(kind));
            plotAreaView.setLayerVisible(cutKey, visible && !"Travel".equals(kind));
            refreshCncAnnotations(item);
            projectTree.refresh(); // see setObjectVisible()'s doc - the tree dims a disabled row's text.
        };
        plotCb.setOnAction(e -> applyVisibility.run());
        kindCombo.setOnAction(e -> applyVisibility.run());

        box.getChildren().add(labeledRow("Plot Kind:", kindCombo));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        GCodeToolpathParser.ToolpathStats stats = entry.stats();
        if (stats != null) {
            if (!stats.pathMarks().isEmpty()) {
                CheckBox annotationCb = new CheckBox(I18n.text("ui.81f9d4f73270"));
                annotationCb.setSelected(!cncAnnotationsOff.contains(item));
                annotationCb.setOnAction(e -> {
                    if (annotationCb.isSelected()) {
                        cncAnnotationsOff.remove(item);
                    } else {
                        cncAnnotationsOff.add(item);
                    }
                    refreshCncAnnotations(item);
                });
                box.getChildren().add(annotationCb);
            }
            if (!stats.steps().isEmpty()) {
                CncAnnotationKey routeKey = new CncAnnotationKey(item);
                Button startWalk = new Button(I18n.text("ui.e446824a0596"));
                startWalk.setOnAction(e -> plotAreaView.selectStep(routeKey, 0));
                Button previousLeg = new Button("<");
                previousLeg.setOnAction(e -> plotAreaView.stepBy(-1));
                Button nextLeg = new Button(">");
                nextLeg.setOnAction(e -> plotAreaView.stepBy(1));
                Button clearLegs = new Button(I18n.text("ui.5585ca453806"));
                clearLegs.setOnAction(e -> plotAreaView.clearStepSelection());
                cncStepLabel = new Label(plotAreaView.hasStepSelection() ? "" : I18n.text("ui.0dd81daf877c"));
                Button playWalk = new Button(I18n.text("ui.13e746eb73b0"));
                playWalk.setOnAction(e -> {
                    if (!plotAreaView.hasStepSelection()) {
                        plotAreaView.selectStep(routeKey, 0);
                    }
                    plotAreaView.toggleWalk();
                });
                Button exportSteps = new Button(I18n.text("ui.2f7cd80aedf2"));
                exportSteps.setOnAction(e -> exportCncSteps(item, stats));
                HBox walkRow = new HBox(6, startWalk, previousLeg, nextLeg, clearLegs);
                box.getChildren().add(labeledRow(I18n.text("ui.a4e8eda16648"), walkRow));
                box.getChildren().add(labeledRow(I18n.text("ui.fc0c25a4d29b"), new HBox(6, playWalk, exportSteps)));
                box.getChildren().add(cncStepLabel);
            }
            if (!stats.cutArrows().isEmpty()) {
                CheckBox arrowsCb = new CheckBox(I18n.text("ui.3e2f2885e9b1"));
                arrowsCb.setSelected(!cncArrowsOff.contains(item));
                arrowsCb.setOnAction(e -> {
                    if (arrowsCb.isSelected()) {
                        cncArrowsOff.remove(item);
                    } else {
                        cncArrowsOff.add(item);
                    }
                    refreshCncAnnotations(item);
                });
                box.getChildren().add(arrowsCb);
            }
            String units = stats.units().toLowerCase(java.util.Locale.ROOT);
            box.getChildren().add(labeledRow("Travelled distance:",
                    new Label(UiNumbers.fixed(stats.xyDistance(),units) + " " + units)));
            box.getChildren().add(labeledRow("Estimated time:",
                    new Label(CncJobToolsTable.formatDuration(stats.estimatedMinutes()))));
            if (stats.hasTools()) {
                Label toolsLabel = new Label(I18n.text("ui.b96a67bfde3e"));
                toolsLabel.setStyle("-fx-font-weight: bold;");
                Set<Integer> hidden = hiddenCncTools.computeIfAbsent(item, ignored -> new LinkedHashSet<>());
                box.getChildren().addAll(toolsLabel,
                        CncJobToolsTable.build(stats, units, hidden, () -> applyCncToolVisibility(item)));
            }
        }

        Button viewButton = new Button(I18n.text("ui.13f6cd934186"));
        viewButton.setGraphic(legacyIcon("source32.png", 16));
        viewButton.getStyleClass().add("primary-action");
        viewButton.setMaxWidth(Double.MAX_VALUE);
        viewButton.setOnAction(e -> openAuxiliaryTab(item.getValue(), () -> buildGCodeViewer(entry.gcode())));
        box.getChildren().add(viewButton);
        Button editButton = new Button(I18n.text("ui.fb1b561e4268"));
        editButton.setGraphic(legacyIcon("code_editor32.png", 16));
        editButton.setMaxWidth(Double.MAX_VALUE);
        editButton.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGCode();
        });
        box.getChildren().add(editButton);
        box.getChildren().add(propertiesSection(String.format(
                I18n.text("ui.bee96f16e7df"),
                entry.sourceName(), entry.outputFile(), entry.gcode().lines().count()
        )));
        return box;
    }

    private VBox objectPropertiesHeader(String title, Color swatchColor, String iconFile) {
        Rectangle swatch = new Rectangle(11, 11, swatchColor);
        swatch.setArcWidth(4);
        swatch.setArcHeight(4);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("object-title");
        HBox header = new HBox(6, legacyIcon(iconFile, 20), swatch, titleLabel);
        header.getStyleClass().add("object-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, header);
        box.getStyleClass().add("object-panel");
        box.setPadding(new Insets(10));
        return box;
    }

    private HBox labeledRow(String label, Node... controls) {
        HBox row = new HBox(10, new Label(label));
        row.getChildren().addAll(controls);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** "Name:" + an editable field - renaming just changes the TreeItem's own value (its tree label), matching FlatCAMObj.on_name_activate(). */
    private HBox nameRow(TreeItem<String> item) {
        TextField nameField = new TextField(item.getValue());
        HBox.setHgrow(nameField, Priority.ALWAYS);
        Runnable commit = () -> {
            String newName = nameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(item.getValue())) {
                if (!renameProjectItem(item, newName)) {
                    nameField.setText(item.getValue());
                }
            } else if (newName.isEmpty()) {
                nameField.setText(item.getValue());
            }
        };
        nameField.setOnAction(e -> commit.run());
        nameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commit.run();
            }
        });
        return labeledRow(I18n.text("ui.0939548aea0f"), nameField);
    }

    /** The legacy "PROPERTIES" toggle button + inline stats frame, done here as a collapsible section. */
    private TitledPane propertiesSection(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        TitledPane pane = new TitledPane(I18n.text("ui.75d9ab4970bd"), label);
        pane.setGraphic(legacyIcon("properties32.png", 18));
        pane.setExpanded(false);
        return pane;
    }

    /**
     * Plot Area (viewport) is the one tab that can never be closed;
     * everything else - Preferences, Tools Database, editors - opens here on
     * demand via {@link #openAuxiliaryTab}, matching appGUI/MainGUI.py's
     * plot_tab_area instead of the separate right-hand panel the skeleton
     * used to have.
     */
    private TabPane buildCenterTabs() {
        plotAreaView.getStyleClass().add("viewport-placeholder");
        Tab plotAreaTab = new Tab("Plot Area", plotAreaView);
        plotAreaTab.setClosable(false);
        centerTabs.getTabs().add(plotAreaTab);
        return centerTabs;
    }

    /**
     * Opens {@code title} in the center tab strip, or focuses it if already
     * open - the legacy app never duplicates these auxiliary tabs either.
     */
    private void openAuxiliaryTab(String title, java.util.function.Supplier<javafx.scene.Node> content) {
        for (Tab tab : centerTabs.getTabs()) {
            if (title.equals(tab.getText())) {
                centerTabs.getSelectionModel().select(tab);
                return;
            }
        }
        Tab tab = new Tab(title, content.get());
        centerTabs.getTabs().add(tab);
        centerTabs.getSelectionModel().select(tab);
    }

    /** Python Help > Shortcuts List: a tab in the plot area, not a dialog. */
    void openShortcutList() {
        openAuxiliaryTab(I18n.text("ui.627e2a1aede0"), ShortcutListPanel::new);
    }

    private void openPreferences() {
        openAuxiliaryTab(I18n.text("ui.6706dbe8b723"), this::buildPreferencesPanel);
    }

    private Node buildPreferencesPanel() {
        return new PreferencesPane(new PreferencesPane.Host() {
            @Override public UiLanguage language() { return AppPreferences.loadLanguage(); }
            @Override public void applyLanguage(UiLanguage language) { AppPreferences.saveLanguage(language); }
            @Override public UiNumbers.Settings numericSettings() { return AppPreferences.loadNumericSettings(); }
            @Override public void applyNumericSettings(UiNumbers.Settings settings) { AppPreferences.saveNumericSettings(settings); }
            @Override public ThemeOption theme() { return currentTheme; }
            @Override public AppPreferences.PlotStatusSettings plotSettings() { return AppPreferences.loadPlotStatusSettings(); }
            @Override public void applyTheme(ThemeOption theme) {
                MainWindow.this.applyTheme(theme);
                setStatus(I18n.text("ui.fee07ce9a050"), IDLE_COLOR);
            }
            @Override public void applyPlot(AppPreferences.PlotStatusSettings settings) {
                statusControls.applySettings(settings);
                setStatus(I18n.text("ui.9f3b50a880a5"), IDLE_COLOR);
            }
        }, icon -> legacyIcon(icon, 16));
    }

    /** File > Backup > Exportar preferencias: the tool defaults as an editable .properties file. */
    private void exportToolDefaults() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.98bfb047f0e5"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("ui.6706dbe8b723"), "*.properties"));
        chooser.setInitialFileName("flatcam-fx-ferramentas.properties");
        File file = chooser.showSaveDialog(scene.getWindow());
        if (file == null) {
            return;
        }
        try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            ToolDefaults.export().store(writer, I18n.text("ui.4ed900d1779e"));
            appendConsole(I18n.text("ui.7ab289773660") + file.getName() + ".");
        } catch (IOException failed) {
            appendConsole(I18n.text("ui.d72e9f155e09") + failed.getMessage());
        }
    }

    /** File > Backup > Importar preferencias: applies an exported file; invalid or unknown entries are skipped. */
    private void importToolDefaults() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.a06c5dc6459f"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("ui.6706dbe8b723"), "*.properties"));
        File file = chooser.showOpenDialog(scene.getWindow());
        if (file == null) {
            return;
        }
        java.util.Properties properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException failed) {
            appendConsole(I18n.text("ui.e50535491cf3") + failed.getMessage());
            return;
        }
        int[] counts = ToolDefaults.importFrom(properties);
        appendConsole(I18n.text("ui.f5a58b86f503") + file.getName() + ": " + counts[0] + I18n.text("ui.5e823980bb1a") + counts[1]
                + I18n.text("ui.0f5968221802"));
    }

    private void openToolsDatabase() {
        for (Tab tab : centerTabs.getTabs()) if ("tools-database-tab".equals(tab.getId())) {
            centerTabs.getSelectionModel().select(tab); return;
        }
        if (toolsDatabasePanel == null) {
            toolsDatabasePanel = new ToolsDatabasePanel(jobExecutor, () -> scene.getWindow(),
                    AppPreferences::saveToolsDatabasePath, file -> legacyIcon(file, 16));
            fluidTooltips.attachContextMenu(toolsDatabasePanel.contextMenuForTooltips());
            String remembered = AppPreferences.loadToolsDatabasePath();
            if (!remembered.isBlank()) toolsDatabasePanel.loadPath(Path.of(remembered));
        }
        Tab tab = new Tab(); tab.setId("tools-database-tab"); tab.setContent(toolsDatabasePanel);
        tab.setGraphic(legacyIcon("search_db32.png", 16));
        tab.textProperty().bind(javafx.beans.binding.Bindings.when(toolsDatabasePanel.dirtyProperty())
                .then("Tools Database *").otherwise("Tools Database"));
        tab.setOnCloseRequest(event -> { if (!toolsDatabasePanel.confirmClose()) event.consume(); });
        centerTabs.getTabs().add(tab); centerTabs.getSelectionModel().select(tab);
    }

    // ---- TclFlatcamHost: what the Tcl Terminal's FlatCAM commands see of this live session ----

    @Override
    public void saveProject(Path file) throws IOException, TclException {
        requireWorkerCommand();
        String filename = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        boolean pythonFormat = filename.endsWith(".flatprj");
        if (!pythonFormat && !filename.endsWith(".fcnproj"))
            throw new TclException(I18n.text("ui.e1d83df27e15"));
        TclProjectSave snapshot = TclExecution.onFx(() -> {
            requireProjectSaveAllowed();
            if (pythonFormat) appendConsole(I18n.text("ui.5bcc8dd3e7fd"));
            return new TclProjectSave(snapshotProject(), captureTclProjectState());
        });
        TclExecution.phase(I18n.text("ui.bd089e425302"));
        Path destination = file.toAbsolutePath();
        // Existing serializers stage/rename internally. Stage their complete output again so a
        // Terminal cancellation or concurrent edit can be checked before touching the user's destination.
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-tcl-project-", ".tmp");
        try {
            if (pythonFormat) PythonProjectWriter.save(snapshot.project(), temporary);
            else ProjectFileIO.save(snapshot.project(), temporary);
            TclExecution.phase(I18n.text("ui.86c197286078"));
            TclExecution.onFx(() -> {
                requireProjectSaveAllowed();
                checkTclProjectState(snapshot.before());
                return null;
            });
            TclExecution.cancellation().throwIfCancellationRequested();
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            TclExecution.progress(I18n.text("ui.3a4737de1105")).report(1);
        } finally { Files.deleteIfExists(temporary); }
        // Mark only the serialized version, never a later UI edit; do not alter last-directory preferences.
        TclExecution.onFx(() -> { markProjectSaved(file,projectStateStamp(snapshot.before())); return null; });
    }

    private record TclProjectSave(ProjectFile project, TclProjectState before) { }

    private record TclJoinSnapshot(TclProjectState before, List<ProjectFile.GeometryEntry> geometries,
                                   List<ProjectFile.ExcellonEntry> excellons) { }

    @Override
    public String join(Kind kind, String outname, List<String> names) throws TclException {
        requireWorkerCommand();
        if (kind != Kind.GEOMETRY && kind != Kind.EXCELLON) throw new TclException("Join supports Geometry or Excellon only.");
        if (outname == null || outname.isBlank()) throw new TclException("Expected a nonempty output name.");
        List<String> requested = List.copyOf(names);
        if (requested.size() < 2 || requested.stream().distinct().count() != requested.size())
            throw new TclException("Join requires at least two distinct source objects.");
        try {
            TclJoinSnapshot snapshot = TclExecution.onFx(() -> {
                requireTclJoinAllowed();
                for (String name : requested) {
                    TreeItem<String> item = findTclItemByName(name);
                    if (item == null) throw new IllegalArgumentException("Object not found: " + name);
                    if (kind == Kind.GEOMETRY ? !geometryByItem.containsKey(item) : !excellonByItem.containsKey(item))
                        throw new IllegalArgumentException("Expected " + kind + " object: " + name);
                }
                ProjectFile project = snapshotProject();
                return new TclJoinSnapshot(captureTclProjectState(), kind != Kind.GEOMETRY ? List.of()
                        : requested.stream().map(name -> project.geometries().stream()
                        .filter(entry -> entry.name().equals(name)).findFirst().orElseThrow()).toList(),
                        kind != Kind.EXCELLON ? List.of() : requested.stream().map(name -> project.excellons().stream()
                        .filter(entry -> entry.name().equals(name)).findFirst().orElseThrow()).toList());
            });
            TclExecution.phase(I18n.text("ui.338884089342") + kind + "...");
            var geometry = kind == Kind.GEOMETRY ? TclObjectJoin.geometry(snapshot.geometries()) : null;
            var excellon = kind == Kind.EXCELLON ? TclObjectJoin.excellon(snapshot.excellons()) : null;
            TclExecution.phase(I18n.text("ui.9952796cba83"));
            return TclExecution.onFx(() -> {
                requireTclJoinAllowed(); checkTclProjectState(snapshot.before());
                String actual = uniqueDerivedName(outname);
                plotAreaView.beginBatchUpdate();
                try {
                    TreeItem<String> created;
                    if (geometry != null) {
                        var joined = geometry.joined();
                        created = addGeometryToProject(actual, String.join(", ", requested), snapshot.geometries().getFirst().units(),
                                joined.geometry(), joined.strokeOnly(), joined.tools(), geometry.defaults());
                        if (geometry.settings() != null) geometryCncSettingsByItem.put(created, geometry.settings());
                    } else {
                        created = addExcellonToProject(actual, null, excellon.image(), excellon.defaults());
                        if (excellon.settings() != null) drillCncSettingsByItem.put(created, excellon.settings());
                    }
                    // Legacy Tcl joins request plot=False. Keep UI joins' visible/fit behavior unchanged.
                    applyObjectVisibility(created, false);
                    refreshObjectVisibilityUi();
                } finally { plotAreaView.endBatchUpdate(); }
                return actual;
            });
        } catch (IllegalArgumentException error) { throw new TclException(error.getMessage()); }
    }

    private void requireTclJoinAllowed() {
        if (runningJob != null || gerberEditor.isActive() || geometryEditor.isActive()
                || excellonEditor.isActive() || gcodeEditor.isActive())
            throw new IllegalStateException(I18n.text("ui.22dca3941697"));
    }

    private void requireProjectSaveAllowed() {
        if (runningJob != null) throw new IllegalStateException(I18n.text("ui.df12b110e3bb"));
        if (gcodeEditor.isActive() || gerberEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive())
            throw new IllegalStateException(I18n.text("ui.feb7dc841cb7"));
    }

    @Override
    public void plotObjects(List<String> names, boolean visible) throws TclException {
        List<String> requested = names == null ? null : List.copyOf(names);
        try { TclExecution.onFx(() -> {
            List<TreeItem<String>> targets = requested == null ? allTclItems() : requested.stream().map(name -> {
                TreeItem<String> item = findTclItemByName(name);
                if (item == null) throw new IllegalArgumentException("Object not found: " + name);
                return item;
            }).distinct().toList();
            for (TreeItem<String> item : targets)
                if (!isPlottable(item)) throw new IllegalArgumentException(I18n.text("ui.7f2a49e8113c") + item.getValue());
            plotAreaView.beginBatchUpdate();
            try {
                for (TreeItem<String> item : targets) {
                    // Tcl plot_objects explicitly requests both sublayers, unlike the tree's Plot toggle.
                    if (visible && cncJobByItem.containsKey(item)) {
                        var style=snapshotCncPlotSettings(item);
                        cncPlotSettings.put(item,new org.flatcam.app.project.CncPlotSettings(style.cutFill(),style.cutLine(),
                                style.travelFill(),style.travelLine(),"ALL",style.annotations(),style.arrows(),style.fontSize(),style.fontColor()));
                    }
                    applyObjectVisibility(item, visible);
                }
                refreshObjectVisibilityUi();
            } finally { plotAreaView.endBatchUpdate(); }
            return null;
        }); } catch (IllegalArgumentException error) { throw new TclException(error.getMessage()); }
    }

    @Override
    public void setActive(String name) throws TclException {
        try { TclExecution.onFx(() -> {
            if (gerberEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive())
                throw new IllegalStateException(I18n.text("ui.5c9a1ffd4707"));
            TreeItem<String> item = findTclItemByName(name);
            if (item == null) throw new IllegalArgumentException("Object not found: " + name);
            int row = rowForPlotObject(item);
            // Python collection.set_active is additive, not set_exclusive_active.
            projectTree.getSelectionModel().select(row);
            projectTree.scrollTo(row);
            return null;
        }); } catch (IllegalArgumentException error) { throw new TclException(error.getMessage()); }
    }

    @Override
    public void openProject(Path file) throws IOException, TclException {
        openProject(file,false);
    }
    @Override
    public void openProject(Path file,boolean discardUnsaved) throws IOException,TclException {
        requireWorkerCommand();
        TclProjectState before = TclExecution.onFx(() -> {
            requireProjectReplacementAllowed();
            if(!discardUnsaved && hasUnsavedProjectChanges())throw new IllegalStateException(I18n.text("ui.912a36a8eb8d"));
            return captureTclProjectState();
        });
        LoadedProject loaded = loadProject(file, new JobContext() {
            @Override public boolean isCancelled() { return TclExecution.cancellation().isCancellationRequested(); }
            @Override public void reportProgress(double fraction, String message) {
                TclExecution.progress(message).report(fraction);
            }
        });
        TclExecution.onFx(() -> {
            requireProjectReplacementAllowed();
            checkTclProjectState(before);
            restoreProject(loaded, false);
            reportOpenedProject(file, loaded);
            markProjectSaved(file,projectStateStamp());
            return null;
        });
    }

    /** The Tcl worker cannot wait for another job on FX or transform live JavaFX state. */
    private static void requireWorkerCommand() {
        if (Platform.isFxApplicationThread())
            throw new IllegalStateException(I18n.text("ui.0429c34154c0"));
    }

    private boolean hasEditorChanges() {
        return gcodeEditor.hasUnappliedChanges() || gerberEditor.hasUnappliedChanges()
                || geometryEditor.hasUnappliedChanges() || excellonEditor.hasUnappliedChanges();
    }

    private void requireProjectReplacementAllowed() {
        if (runningJob != null) throw new IllegalStateException(I18n.text("ui.df12b110e3bb"));
        if (hasEditorChanges())
            throw new IllegalStateException(I18n.text("ui.1c1584221734"));
    }

    private ProjectChangeTracker.State projectStateStamp() { return projectStateStamp(captureTclProjectState()); }
    private ProjectChangeTracker.State projectStateStamp(TclProjectState state) {
        return new ProjectChangeTracker.State(state.epoch(),state.items().stream()
                .map(i -> new ProjectChangeTracker.Item(i.item(),i.name(),i.version(),i.appearance())).toList(),state.settings());
    }
    boolean hasUnsavedProjectChanges() { return projectChanges.changed(projectStateStamp()); }
    private void markProjectSaved(Path file,ProjectChangeTracker.State state) {
        currentProjectFile=file.toAbsolutePath().normalize(); projectChanges.saved(state);
    }
    void setApplicationClose(Runnable close) { applicationClose=close; }
    void setProjectDecisionProvider(java.util.function.Function<Boolean,UnsavedChangesGuard.Choice> provider) { projectDecision=provider; }
    void setGuardedProjectSave(java.util.function.Consumer<java.util.function.Consumer<Boolean>> save) { guardedProjectSave=save; }

    private UnsavedChangesGuard.Choice askProjectDecision(boolean drafts) {
        // Headless hosts cannot open a modal window accidentally; their safe default is cancellation.
        if(scene==null || scene.getWindow()==null)return UnsavedChangesGuard.Choice.CANCEL;
        var dialog=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        dialog.initOwner(scene.getWindow()); dialog.setTitle(drafts ? I18n.text("ui.4578531813a9") : I18n.text("ui.84aab6a057ce")); dialog.setHeaderText(null);
        dialog.setContentText(drafts ? I18n.text("ui.b1fd0e983f09")
                : I18n.text("ui.8e0bb14ec598")+(currentProjectFile==null ? "" : I18n.text("ui.ee09035254b2")+currentProjectFile));
        var save=new javafx.scene.control.ButtonType(drafts ? I18n.text("ui.05473fa8f2f5") : I18n.text("ui.c468e1df3d21"),javafx.scene.control.ButtonBar.ButtonData.YES);
        var discard=new javafx.scene.control.ButtonType(drafts ? I18n.text("ui.a09075a45b43") : I18n.text("ui.bb372cbd6ce8"),javafx.scene.control.ButtonBar.ButtonData.NO);
        dialog.getButtonTypes().setAll(save,discard,javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getScene().getStylesheets().setAll(scene.getStylesheets());
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(save),I18n.text("ui.4668dc196351"),drafts ? I18n.text("ui.e1ad9b77e706") : I18n.text("ui.855f368c7b1f"));
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(discard),I18n.text("ui.1a878a55c594"),drafts ? I18n.text("ui.4789a0273435") : I18n.text("ui.5bd694e23806"));
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL),I18n.text("ui.bb9dbb406dcb"),I18n.text("ui.8b920a2da791"));
        FluidTooltips.install(dialog,I18n.text("ui.84aab6a057ce"));
        var choice=dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL);
        return choice==save ? UnsavedChangesGuard.Choice.SAVE : choice==discard ? UnsavedChangesGuard.Choice.DISCARD : UnsavedChangesGuard.Choice.CANCEL;
    }
    private void protectProjectTransition(Runnable proceed) {
        if(runningJob!=null || terminalPanel!=null && terminalPanel.isBusy()) {
            String message=I18n.text("ui.f34e31e31f92");
            appendConsole(message); setStatus(message,RUNNING_COLOR); return;
        }
        if(hasEditorChanges() && projectDecision.apply(true)!=UnsavedChangesGuard.Choice.DISCARD)return;
        // Leaving an unchanged editor restores its temporary plot visibility before measuring project dirtiness.
        gcodeEditor.cancel(); gerberEditor.cancel(); geometryEditor.cancel(); excellonEditor.cancel();
        if(hasEditorChanges()) { appendConsole(I18n.text("ui.94123b71fe6e")); return; }
        unsavedGuard.protect(() -> hasUnsavedProjectChanges() || hasEditorChanges(),() -> projectDecision.apply(false),guardedProjectSave,proceed);
    }
    void requestClose(Runnable close) {
        protectProjectTransition(() -> {
            if(!confirmToolsDatabaseClose())return;
            if(!confirmScriptsClose())return;
            if(fluidTooltips!=null)fluidTooltips.closeNow(); close.run();
        });
    }
    void requestApplicationClose() {
        // The Stage can be recreated after a monitor/DPI change while Save is pending.
        // Resolve its persistence/exit action at completion, not from the old Stage.
        requestClose(() -> applicationClose.run());
    }

    private record TclAppearance(boolean visible, boolean filled, boolean multicolor, Color fill, Color stroke) { }
    private record TclItemState(TreeItem<String> item, String name, Object version, TclAppearance appearance) { }
    private record TclProjectState(long epoch, List<TclItemState> items, List<Object> settings) { }

    private TclAppearance tclAppearance(Object item) {
        var colors = plotAreaView.layerColors(item);
        return new TclAppearance(plotAreaView.isLayerVisible(item), plotAreaView.isLayerFilled(item),
                plotAreaView.isLayerMulticolor(item), colors == null ? null : colors[0], colors == null ? null : colors[1]);
    }

    private List<Object> tclProjectSettings() {
        Map<TreeItem<String>, Set<Integer>> hidden = new LinkedHashMap<>();
        hiddenCncTools.forEach((item, tools) -> hidden.put(item, Set.copyOf(tools)));
        Map<TreeItem<String>, List<TclAppearance>> cncAppearance = new LinkedHashMap<>();
        cncJobByItem.keySet().forEach(item -> cncAppearance.put(item,
                List.of(tclAppearance(new CncCutLayerKey(item)), tclAppearance(new CncTravelLayerKey(item)))));
        return List.of(new LinkedHashMap<>(drillDefaultsByItem), new LinkedHashMap<>(drillCncSettingsByItem),
                new LinkedHashMap<>(geometryCncSettingsByItem), new LinkedHashSet<>(gerberFollowItems),
                hidden, new LinkedHashSet<>(cncAnnotationsOff), new LinkedHashSet<>(cncArrowsOff), cncAppearance);
    }

    private List<TreeItem<String>> allTclItems() {
        List<TreeItem<String>> items = new ArrayList<>(gerberByItem.keySet());
        items.addAll(excellonByItem.keySet()); items.addAll(geometryByItem.keySet()); items.addAll(cncJobByItem.keySet());
        return items;
    }

    private TclProjectState captureTclProjectState() {
        return new TclProjectState(tclProjectEpoch, allTclItems().stream()
                .map(item -> new TclItemState(item, item.getValue(), tclVersion(item), tclAppearance(item))).toList(),
                tclProjectSettings());
    }

    private void checkTclProjectState(TclProjectState before) {
        checkTclProject(before.epoch());
        if (allTclItems().size() != before.items().size() || !before.settings().equals(tclProjectSettings()))
            throw new IllegalStateException(I18n.text("ui.9bdd1a426eec"));
        for (TclItemState state : before.items())
            if (tclVersion(state.item()) != state.version() || !Objects.equals(state.name(), state.item().getValue())
                    || !state.appearance().equals(tclAppearance(state.item())))
                throw new IllegalStateException(I18n.text("ui.85d1f9efcd5d"));
    }

    private void requireTclTransformAllowed() {
        if (runningJob != null || gerberEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive())
            throw new IllegalStateException(I18n.text("ui.aa6cc503e8b7"));
    }

    @Override
    public void transform(String name, TclTransformRequest request) throws TclException {
        requireWorkerCommand();
        TclExecution.onFx(() -> { requireTclTransformAllowed(); return null; });
        TclSource source = tclSource(name);
        if (source.kind() == Kind.CNC_JOB)
            throw new TclException("Only Gerber, Excellon and Geometry objects can be transformed; CNC G-code is not rewritten.");
        if (request.operation() == TclTransformRequest.Operation.OFFSET && request.x() == 0 && request.y() == 0
                || request.operation() == TclTransformRequest.Operation.SKEW && request.x() == 0 && request.y() == 0
                || request.operation() == TclTransformRequest.Operation.ROTATE && request.x() % 360 == 0
                || request.operation() == TclTransformRequest.Operation.SCALE && request.x() == 1 && request.y() == 1) {
            TclExecution.cancellation().throwIfCancellationRequested();
            return;
        }
        TclSource box = request.reference() == TclTransformRequest.Reference.BOX ? tclSource(request.box()) : null;
        TclExecution.phase(I18n.text("ui.b4af3bc3b2b4") + name + "...");
        TransformOp op = request.resolve(source.geometry() == null ? null : source.geometry().getEnvelopeInternal(),
                box == null || box.geometry() == null ? null : box.geometry().getEnvelopeInternal());
        Object transformed;
        if (source.version() instanceof GerberImage image) {
            GerberImage result = image.transformed(op);
            checkFiniteGeometry(result.solidGeometry()); checkFiniteGeometry(result.followGeometry());
            for (Geometry geometry : result.apertureGeometry().values()) checkFiniteGeometry(geometry);
            for (var shape : result.shapes()) { checkFiniteGeometry(shape.geometry()); checkFiniteGeometry(shape.followGeometry()); }
            transformed = result;
        } else if (source.version() instanceof ExcellonImage image) {
            ExcellonImage result = image.transformed(op);
            checkFiniteGeometry(result.solidGeometry());
            for (var drill : result.drills()) checkFinitePoint(drill.x(), drill.y());
            for (var slot : result.slots()) { checkFinitePoint(slot.x1(), slot.y1()); checkFinitePoint(slot.x2(), slot.y2()); }
            transformed = result;
        } else {
            GeometryEntry entry = (GeometryEntry) source.version();
            Geometry geometry = op.apply(entry.geometry()); checkFiniteGeometry(geometry);
            List<ToolGeometry> tools = new ArrayList<>();
            for (ToolGeometry tool : entry.tools()) {
                TclExecution.cancellation().throwIfCancellationRequested();
                ToolGeometry result = tool.transformed(op); checkFiniteGeometry(result.geometry()); tools.add(result);
            }
            transformed = new GeometryEntry(entry.sourceName(), entry.units(), geometry,
                    entry.strokeOnly(), List.copyOf(tools), entry.cncDefaults(),
                    entry.machiningTarget()==null ? null : entry.machiningTarget().transformed(op,entry.tools(),tools,TclExecution.cancellation()));
        }
        TclExecution.cancellation().throwIfCancellationRequested();
        TclExecution.onFx(() -> {
            requireTclTransformAllowed(); checkTclSource(source);
            if (box != null) checkTclSource(box);
            plotMoveHistory.clear();
            if (transformed instanceof GerberImage image) {
                gerberByItem.put(source.item(), image);
                plotAreaView.updateLayerGeometry(source.item(), gerberFollowItems.contains(source.item())
                        ? image.followGeometry() : image.solidGeometry());
            } else if (transformed instanceof ExcellonImage image) {
                excellonByItem.put(source.item(), image);
                plotAreaView.updateLayerGeometry(source.item(), image.solidGeometry());
            } else {
                GeometryEntry entry = (GeometryEntry) transformed;
                geometryByItem.put(source.item(), entry);
                plotAreaView.updateLayerGeometry(source.item(), entry.geometry());
            }
            refreshPlotSelectionOutline();
            if (projectTree.getSelectionModel().getSelectedItem() == source.item()) showProperties(source.item());
            return null;
        });
    }

    private static void checkFinitePoint(double x, double y) {
        TclExecution.cancellation().throwIfCancellationRequested();
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Transform would create non-finite coordinates; object preserved.");
    }

    private static void checkFiniteGeometry(Geometry geometry) {
        if (geometry == null) return;
        geometry.apply(new org.locationtech.jts.geom.CoordinateSequenceFilter() {
            @Override public void filter(org.locationtech.jts.geom.CoordinateSequence sequence, int i) {
                checkFinitePoint(sequence.getX(i), sequence.getY(i));
            }
            @Override public boolean isDone() { return false; }
            @Override public boolean isGeometryChanged() { return false; }
        });
    }

    @Override
    public String openGerber(Path file, String outname) throws IOException {
        long epoch = TclExecution.onFx(() -> tclProjectEpoch);
        GerberImage image = new GerberParser(ToolDefaults.gerberImport()).parse(file, TclExecution.cancellation(),
                TclExecution.progress(I18n.text("ui.1c07d83986c8")));
        return TclExecution.onFx(() -> {
            checkTclProject(epoch);
            String name = uniqueDerivedName(outname);
            setDisplayUnits(image.units());
            addGerberToProject(name, file, image);
            return name;
        });
    }

    @Override
    public String openExcellon(Path file, String outname) throws IOException {
        long epoch = TclExecution.onFx(() -> tclProjectEpoch);
        ExcellonImage image = new ExcellonParser(ToolDefaults.excellonImport()).parse(file, TclExecution.cancellation(),
                TclExecution.progress(I18n.text("ui.9a28e15ae79c")));
        return TclExecution.onFx(() -> {
            checkTclProject(epoch);
            String name = uniqueDerivedName(outname);
            setDisplayUnits(image.units());
            addExcellonToProject(name, file, image);
            return name;
        });
    }

    @Override
    public List<String> objectNames() {
        return TclExecution.onFx(() -> {
            List<String> names = new ArrayList<>();
            for (TreeItem<String> item : gerberByItem.keySet()) names.add(item.getValue());
            for (TreeItem<String> item : excellonByItem.keySet()) names.add(item.getValue());
            for (TreeItem<String> item : geometryByItem.keySet()) names.add(item.getValue());
            for (TreeItem<String> item : cncJobByItem.keySet()) names.add(item.getValue());
            return names;
        });
    }

    /** Resolves a Tcl object name across every kind - Python's single shared {@code collection.get_by_name}. */
    private TreeItem<String> findTclItemByName(String name) {
        TreeItem<String> found = null;
        List<TreeItem<String>> items = new ArrayList<>();
        items.addAll(gerberByItem.keySet()); items.addAll(excellonByItem.keySet());
        items.addAll(geometryByItem.keySet()); items.addAll(cncJobByItem.keySet());
        for (TreeItem<String> item : items) {
            if (!name.equals(item.getValue())) continue;
            if (found != null) throw new IllegalArgumentException(I18n.text("ui.b88e39ac1c42") + name);
            found = item;
        }
        return found;
    }

    @Override
    public Optional<ObjectRef> find(String name) {
        return TclExecution.onFx(() -> {
            TreeItem<String> item = findTclItemByName(name);
            if (item == null) return Optional.empty();
            Kind kind = gerberByItem.containsKey(item) ? Kind.GERBER
                    : excellonByItem.containsKey(item) ? Kind.EXCELLON
                    : geometryByItem.containsKey(item) ? Kind.GEOMETRY
                    : Kind.CNC_JOB;
            return Optional.of(new ObjectRef(kind, name));
        });
    }

    @Override
    public void delete(String name) {
        TclExecution.onFx(() -> {
            TreeItem<String> item = findTclItemByName(name);
            if (item != null) {
                removeSelectionFromProject(List.of(item));
            }
            return null;
        });
    }

    @Override
    public void deleteAll() {
        TclExecution.onFx(() -> {
            List<TreeItem<String>> all = new ArrayList<>();
            all.addAll(gerberByItem.keySet());
            all.addAll(excellonByItem.keySet());
            all.addAll(geometryByItem.keySet());
            all.addAll(cncJobByItem.keySet());
            removeSelectionFromProject(all);
            return null;
        });
    }

    @Override
    public Optional<double[]> boundsOf(String name) {
        return TclExecution.onFx(() -> {
            TreeItem<String> item = findTclItemByName(name);
            if (item == null) {
                return Optional.empty();
            }
            GerberImage gerber = gerberByItem.get(item);
            if (gerber != null) {
                return Optional.of(gerber.bounds());
            }
            ExcellonImage excellon = excellonByItem.get(item);
            if (excellon != null) {
                return Optional.of(excellon.bounds());
            }
            GeometryEntry geometry = geometryByItem.get(item);
            if (geometry != null) {
                Envelope env = geometry.geometry().getEnvelopeInternal();
                return Optional.of(new double[]{env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()});
            }
            CncJobEntry cncJob = cncJobByItem.get(item);
            if (cncJob != null && cncJob.cutGeometry() != null && !cncJob.cutGeometry().isEmpty()) {
                Envelope env = cncJob.cutGeometry().getEnvelopeInternal();
                return Optional.of(new double[]{env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()});
            }
            return Optional.empty();
        });
    }

    @Override
    public String newEmptyGeometry(String name) {
        return TclExecution.onFx(() -> {
            String actual = uniqueDerivedName(name);
            addGeometryToProject(actual, "", "MM", CNC_GEOMETRY_FACTORY.createGeometryCollection(), false);
            return actual;
        });
    }

    /** Snapshot immutable session entries on FX; validate their identity before publishing a worker result. */
    private record TclSource(TreeItem<String> item, String name, Object version, Kind kind,
                             String units, Geometry geometry, String gcode, long epoch) { }

    private Object tclVersion(TreeItem<String> item) {
        if (gerberByItem.containsKey(item)) return gerberByItem.get(item);
        if (excellonByItem.containsKey(item)) return excellonByItem.get(item);
        if (geometryByItem.containsKey(item)) return geometryByItem.get(item);
        return cncJobByItem.get(item);
    }

    private TclSource tclSource(String name) throws TclException {
        TclSource source = TclExecution.onFx(() -> {
            TreeItem<String> item = findTclItemByName(name);
            if (item == null) return null;
            Object version = tclVersion(item);
            if (version instanceof GerberImage image)
                return new TclSource(item, name, version, Kind.GERBER, image.units(), image.solidGeometry(), null, tclProjectEpoch);
            if (version instanceof ExcellonImage image)
                return new TclSource(item, name, version, Kind.EXCELLON, image.units(), image.solidGeometry(), null, tclProjectEpoch);
            if (version instanceof GeometryEntry entry)
                return new TclSource(item, name, version, Kind.GEOMETRY, entry.units(), entry.geometry(), null, tclProjectEpoch);
            CncJobEntry entry = (CncJobEntry) version;
            return new TclSource(item, name, version, Kind.CNC_JOB, "MM", entry.cutGeometry(), entry.gcode(), tclProjectEpoch);
        });
        if (source == null) throw new TclException("Object not found: " + name);
        return source;
    }

    private void checkTclProject(long epoch) {
        if (epoch != tclProjectEpoch)
            throw new IllegalStateException(I18n.text("ui.efe65e3351f4"));
    }

    private void checkTclSource(TclSource source) {
        checkTclProject(source.epoch());
        if (tclVersion(source.item()) != source.version() || !source.name().equals(source.item().getValue()))
            throw new IllegalStateException(I18n.text("ui.b43fd78e2d55") + source.name());
    }

    private String publishTclGeometry(TclSource source, String outname, Geometry geometry, boolean strokeOnly) {
        return TclExecution.onFx(() -> {
            checkTclSource(source);
            String actual = uniqueDerivedName(outname);
            addGeometryToProject(actual, source.name(), source.units(), geometry, strokeOnly);
            return actual;
        });
    }

    /** Python's TclCommandBbox: always buffers the envelope (rounding its corners), then squares that back off unless {@code rounded}. */
    @Override
    public String newBoundingBoxGeometry(String sourceName, String outname, double margin, boolean rounded)
            throws TclException {
        TclSource source = tclSource(sourceName);
        if (source.kind() != Kind.GERBER && source.kind() != Kind.GEOMETRY) {
            throw new TclException("Expected a Gerber or Geometry object, got: " + sourceName);
        }
        TclExecution.phase(I18n.text("ui.607de04a0343"));
        Geometry envelopeGeometry = CNC_GEOMETRY_FACTORY.toGeometry(source.geometry().getEnvelopeInternal());
        Geometry buffered = envelopeGeometry.buffer(margin, 32);
        Geometry result = rounded ? buffered : CNC_GEOMETRY_FACTORY.toGeometry(buffered.getEnvelopeInternal());
        return publishTclGeometry(source, outname, result, false);
    }

    @Override
    public Optional<Kind> kindOf(String name) {
        return find(name).map(ObjectRef::kind);
    }

    @Override
    public Optional<Geometry> geometryOf(String name) {
        return TclExecution.onFx(() -> {
            TreeItem<String> item = findTclItemByName(name);
            if (item == null) {
                return Optional.empty();
            }
            GerberImage gerber = gerberByItem.get(item);
            if (gerber != null) {
                return Optional.of(gerber.solidGeometry());
            }
            ExcellonImage excellon = excellonByItem.get(item);
            if (excellon != null) {
                return Optional.of(excellon.solidGeometry());
            }
            GeometryEntry geometry = geometryByItem.get(item);
            if (geometry != null) {
                return Optional.of(geometry.geometry());
            }
            CncJobEntry cncJob = cncJobByItem.get(item);
            if (cncJob != null && cncJob.cutGeometry() != null) {
                return Optional.of(cncJob.cutGeometry());
            }
            return Optional.empty();
        });
    }

    @Override
    public String isolate(String sourceName, String outname, double toolDiameter, int passes,
                          double overlapFraction, IsolationType type) throws TclException {
        TclSource source = tclSource(sourceName);
        if (source.kind() != Kind.GERBER) {
            throw new TclException("Expected a Gerber object, got: " + sourceName);
        }
        TclExecution.phase(I18n.text("ui.9ba42f20b109"));
        IsolationParameters params = new IsolationParameters(toolDiameter, passes, overlapFraction, type);
        IsolationResult result = IsolationGenerator.generate(source.units(), source.geometry(), params, TclExecution.cancellation());
        return publishTclGeometry(source, outname, result.geometry(), true);
    }

    @Override
    public String cutoutRectangular(String sourceName, String outname, double toolDiameter, double margin,
                                    double gapSize, GapPattern gaps) throws TclException {
        TclSource source = tclSource(sourceName);
        TclExecution.phase(I18n.text("ui.584276327e87"));
        CutoutParameters params = new CutoutParameters(toolDiameter, margin, false, CutoutKind.SINGLE,
                CutoutShape.RECTANGULAR, gapSize, gaps);
        CutoutResult result = CutoutGenerator.generate(source.units(), source.geometry(), params, TclExecution.cancellation());
        return publishTclGeometry(source, outname, result.geometry(), true);
    }

    @Override
    public String nccClear(String sourceName, String outname, List<Double> toolDiameters, double overlapFraction,
                           double margin, NccMethod method, boolean connect, boolean contour, boolean rest,
                           NccBoundary boundary) throws TclException {
        TclSource source = tclSource(sourceName);
        Geometry reference = boundary instanceof NccBoundary.ReferenceGerber gerber ? gerber.geometry()
                : boundary instanceof NccBoundary.ReferenceGeometry geometry ? geometry.geometry() : null;
        TclSource referenceSource = null;
        if (reference != null) {
            String referenceName = TclExecution.onFx(() -> objectNames().stream()
                    .filter(name -> geometryOf(name).orElse(null) == reference).findFirst().orElse(null));
            if (referenceName == null) throw new TclException(I18n.text("ui.cc5bffdadf51"));
            referenceSource = tclSource(referenceName);
            if (referenceSource.geometry() != reference)
                throw new TclException(I18n.text("ui.411089b16abe"));
        }
        TclSource checkedReference = referenceSource;
        TclExecution.phase(I18n.text("ui.99d9a76262c4"));
        NccParameters params = new NccParameters(toolDiameters, overlapFraction, margin, method, connect, contour,
                0, rest, NccOrder.NONE, boundary);
        NccResult result = NccGenerator.generate(source.units(), source.geometry(), params,
                TclExecution.cancellation(), TclExecution.progress(I18n.text("ui.99d9a76262c4")));
        return TclExecution.onFx(() -> {
            if (checkedReference != null) checkTclSource(checkedReference);
            return publishTclGeometry(source, outname, result.geometry(), true);
        });
    }

    @Override
    public String cncjob(String sourceName, String outname, double toolDiameter, double zCut, double zMove,
                         double feedrate, double feedrateZ, double feedrateRapid) throws TclException {
        TclSource source = tclSource(sourceName);
        if (source.kind() != Kind.GEOMETRY)
            throw new TclException("Expected a Geometry object, got: " + sourceName);
        if (!Double.isFinite(zCut) || zCut >= 0)
            throw new TclException("z_cut must be finite and negative (for example -1.7).");
        TclExecution.phase(I18n.text("ui.6cf087e6154e"));
        GeometryGCodeParameters params = new GeometryGCodeParameters(zMove, -zCut, false, 0,
                feedrate, 0, false, feedrateRapid, null, feedrateZ, false, 0, false, 0);
        CncJobResult job = GCodeGenerator.generateGeometryCncJob(source.units(), source.geometry(), params,
                toolDiameter, TclExecution.cancellation());
        return TclExecution.onFx(() -> {
            checkTclSource(source);
            String actual = uniqueDerivedName(outname);
            addCncJobToProject(actual, sourceName, Path.of(actual), job.gcode(), job.travelGeometry(), job.cutGeometry());
            return actual;
        });
    }

    @Override
    public String drillcncjob(String sourceName, String outname, TclDrillRequest request) throws TclException {
        TclSource source = tclSource(sourceName);
        if (source.kind() != Kind.EXCELLON)
            throw new TclException("Expected an Excellon object, got: " + sourceName);
        ExcellonImage image = (ExcellonImage) source.version();
        List<Integer> ordered = TclDrillTools.select(image, request.drilledDiameters(), request.diameterTolerance());
        if (ordered.isEmpty()) throw new TclException("The Excellon object has no drills or slots to drill: " + sourceName);
        CncJobResult job;
        try {
            DrillGCodeParameters params = new DrillGCodeParameters(request.travelZ(), request.drillDepth(),
                    request.feedRateZ(), request.spindleSpeed(), request.toolChange(), request.multiDepth(),
                    request.depthPerPass(), request.dwell(), request.dwellSeconds(), 0);
            Map<Integer, DrillGCodeParameters> byTool = new LinkedHashMap<>();
            ordered.forEach(id -> byTool.put(id, params));
            double[] change = request.toolChangeXY(), end = request.endXY();
            GCodeGenerator.DrillJobOptions options = new GCodeGenerator.DrillJobOptions(request.toolChange(),
                    request.toolChangeZ(), request.endZ(), end == null ? null : end[0], end == null ? null : end[1],
                    request.rapidFeedRate(), null, false, List.of(), request.startZ(),
                    change == null ? null : change[0], change == null ? null : change[1], request.optimizeDrills());
            TclExecution.phase(I18n.text("ui.1d468bd336d0"));
            job = GCodeGenerator.generateDrillCncJob(image, byTool, ordered, options, request.preprocessor(),
                    TclExecution.cancellation(), TclExecution.progress(I18n.text("ui.1d468bd336d0")));
        } catch (IllegalArgumentException invalid) { throw new TclException(invalid.getMessage()); }
        CncJobResult generated = job;
        return TclExecution.onFx(() -> {
            checkTclSource(source);
            String actual = uniqueDerivedName(outname);
            addCncJobToProject(actual, sourceName, Path.of(actual), generated.gcode(),
                    generated.travelGeometry(), generated.cutGeometry());
            return actual;
        });
    }

    @Override
    public String exportGcode(String cncJobName, String preamble, String postamble) throws TclException {
        requireWorkerCommand();
        TclSource source = tclSource(cncJobName);
        if (source.kind() != Kind.CNC_JOB) {
            throw new TclException("Expected CNCjob, got: " + cncJobName);
        }
        TclExecution.cancellation().throwIfCancellationRequested();
        String text=preamble+source.gcode()+postamble;
        var report=CncExportReview.create(cncJobName,text,null,TclExecution.cancellation(),TclExecution.progress(I18n.text("ui.20e178a398f6")));
        TclExecution.notice(CncExportReview.terminalText(report));
        if (!report.canContinue()) throw new TclException(I18n.text("ui.6582880da723"));
        TclExecution.onFx(() -> { checkTclSource(source); return null; });
        return text;
    }

    @Override
    public void writeGcode(String cncJobName, Path outputFile, String preamble, String postamble)
            throws TclException, IOException {
        TclSource source=tclSource(cncJobName);
        String gcode = exportGcode(cncJobName, preamble, postamble);
        TclExecution.phase(I18n.text("ui.8ead655fb6e1"));
        Path destination = outputFile.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-tcl-", ".tmp");
        try {
            Files.writeString(temporary, gcode);
            TclExecution.cancellation().throwIfCancellationRequested();
            TclExecution.onFx(() -> { checkTclSource(source); return null; });
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    @Override
    public void exportExcellon(String name, Path outputFile) throws TclException, IOException {
        requireWorkerCommand();
        try {
            TclExecution.onFx(() -> { requireTclExportAllowed(); return null; });
            TclSource source = tclSource(name);
            if (source.kind() != Kind.EXCELLON) throw new TclException("Expected an Excellon object: " + name);
            ExcellonExporter.Format format = TclExecution.onFx(CamExportDialog::loadExcellon);
            TclExecution.phase(I18n.text("ui.3e3e964d59f8"));
            String text = new ExcellonExporter().export((ExcellonImage) source.version(), format);
            TclExecution.phase(I18n.text("ui.e2f7e00965ae"));
            Path destination = outputFile.toAbsolutePath();
            if (destination.getFileName() == null || Files.isDirectory(destination))
                throw new IOException("Excellon destination must be a file, not a directory.");
            Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-tcl-excellon-", ".tmp");
            try {
                Files.writeString(temporary, text, java.nio.charset.StandardCharsets.US_ASCII);
                TclExecution.phase(I18n.text("ui.19cfea5a1b5a"));
                TclExecution.onFx(() -> {
                    requireTclExportAllowed(); checkTclSource(source);
                    if (!format.equals(CamExportDialog.loadExcellon()))
                        throw new IllegalStateException(I18n.text("ui.95b34587ef24"));
                    return null;
                });
                TclExecution.cancellation().throwIfCancellationRequested();
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                TclExecution.progress(I18n.text("ui.69f8a3bdd903")).report(1);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IllegalArgumentException error) { throw new TclException(error.getMessage()); }
    }

    @Override
    public void exportGerber(String name, Path outputFile) throws TclException, IOException {
        requireWorkerCommand();
        try {
            TclExecution.onFx(() -> { requireTclExportAllowed(); return null; });
            TclSource source = tclSource(name);
            if (source.kind() != Kind.GERBER) throw new TclException("Expected a Gerber object: " + name);
            GerberExporter.Format format = TclExecution.onFx(CamExportDialog::loadGerber);
            TclExecution.phase(I18n.text("ui.488547e2f8d0"));
            String text = new GerberExporter().export((GerberImage) source.version(), format);
            publishTclDrawing(source, outputFile, text, java.nio.charset.StandardCharsets.US_ASCII, "Gerber", () -> {
                if (!format.equals(CamExportDialog.loadGerber()))
                    throw new IllegalStateException(I18n.text("ui.18ce14291bdd"));
            });
        } catch (IllegalArgumentException error) { throw new TclException(error.getMessage()); }
    }

    @Override
    public void exportSvg(String name, Path outputFile, double scaleStrokeFactor) throws TclException, IOException {
        requireWorkerCommand();
        try {
            TclExecution.onFx(() -> { requireTclExportAllowed(); return null; });
            TclSource source = tclSource(name);
            TclExecution.phase(I18n.text("ui.05425c5bde41"));
            List<SvgExporter.Layer> layers;
            String units = source.units();
            if (source.kind() == Kind.CNC_JOB) {
                var parsed = GCodeToolpathParser.parse(source.gcode(), TclExecution.cancellation(),
                        TclExecution.progress(I18n.text("ui.05425c5bde41")));
                if (!parsed.plotAvailable()) throw new TclException(I18n.text("ui.7a23a137d77c") + parsed.warning());
                // Same automatic width as File > Exportar > SVG, independent of the Plot's visibility filter.
                double diameter = parsed.stats() == null ? 0 : parsed.stats().tools().stream()
                        .map(GCodeToolpathParser.ToolUsage::diameter).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).min().orElse(0);
                double stroke = SvgExporter.strokeWidth(scaleStrokeFactor, diameter > 0 ? diameter : SvgExporter.SHAPE_STROKE_WIDTH);
                layers = List.of(SvgExporter.toolpath(parsed.travelCenterlines(), SvgExporter.TRAVEL_COLOR, stroke),
                        SvgExporter.toolpath(parsed.cutCenterlines(), SvgExporter.CUT_COLOR, stroke));
                units = parsed.units();
            } else layers = List.of(SvgExporter.shapes(source.geometry(), scaleStrokeFactor));
            TclExecution.phase(I18n.text("ui.297268be2b97"));
            String text = new SvgExporter().export(layers, units);
            publishTclDrawing(source, outputFile, text, java.nio.charset.StandardCharsets.UTF_8, "SVG", () -> {});
        } catch (IllegalArgumentException error) { throw new TclException(error.getMessage()); }
    }

    /** Serialized on the worker; a failed/cancelled/stale drawing never truncates the destination. */
    private void publishTclDrawing(TclSource source, Path outputFile, String text, java.nio.charset.Charset charset,
                                   String type, Runnable checkFormat) throws IOException {
        TclExecution.phase(I18n.text("ui.c2168134d5ae") + type + "...");
        Path destination = outputFile.toAbsolutePath().normalize();
        if (destination.getFileName() == null || Files.isDirectory(destination))
            throw new IOException(type + " destination must be a file, not a directory.");
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-tcl-" + type.toLowerCase(java.util.Locale.ROOT) + "-", ".tmp");
        try {
            Files.writeString(temporary, text, charset);
            TclExecution.phase(I18n.text("ui.589c15b6bd2a") + type + "...");
            TclExecution.onFx(() -> {
                requireTclExportAllowed(); checkTclSource(source); checkFormat.run(); return null;
            });
            TclExecution.cancellation().throwIfCancellationRequested();
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            TclExecution.progress(type + I18n.text("ui.b2c8077f7e27")).report(1);
        } finally { Files.deleteIfExists(temporary); }
    }

    private void requireTclExportAllowed() {
        if (runningJob != null || gerberEditor.isActive() || geometryEditor.isActive()
                || excellonEditor.isActive() || gcodeEditor.isActive())
            throw new IllegalStateException(I18n.text("ui.6c193e2cf53d"));
    }

    // ---- Scripting (Python File > Scripting: New / Open / Run Script) ----

    private static final String SCRIPT_DIRECTORY_KEY = "scripting.lastDirectory";

    private FileChooser scriptChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Script Tcl FlatCAM", "*.FlatScript", "*.tcl", "*.TCL", "*.txt", "*.TXT"),
                new FileChooser.ExtensionFilter(I18n.text("ui.0d4623424b45"), "*.*"));
        String remembered = AppPreferences.loadText(SCRIPT_DIRECTORY_KEY);
        if (remembered != null && !remembered.isBlank() && Files.isDirectory(Path.of(remembered)))
            chooser.setInitialDirectory(Path.of(remembered).toFile());
        return chooser;
    }

    private void rememberScriptDirectory(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) AppPreferences.saveText(SCRIPT_DIRECTORY_KEY, parent.toString());
    }

    /** Python on_filenewscript: a new script in the editor, with the Python template. */
    void newScript() {
        openScriptTab(ScriptEditorPanel.NEW_SCRIPT_TEMPLATE, null);
    }

    private void openScripts() {
        List<File> files = scriptChooser(I18n.text("ui.090ec943a77c")).showOpenMultipleDialog(scene.getWindow());
        if (files == null) return;
        for (File file : files) openScript(file.toPath());
    }

    /** Python on_fileopenscript: the file opens in the editor and is not executed. */
    void openScript(Path file) {
        Path absolute = file.toAbsolutePath();
        for (Tab tab : centerTabs.getTabs()) {
            if (tab.getContent() instanceof ScriptEditorPanel panel && absolute.equals(panel.file())) {
                centerTabs.getSelectionModel().select(tab);
                return;
            }
        }
        try {
            String text = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            rememberScriptDirectory(file);
            openScriptTab(text, absolute);
        } catch (IOException | RuntimeException error) {
            appendConsole(I18n.text("ui.5b47cb870897") + file + ": " + error.getMessage());
        }
    }

    private void runScriptFile() {
        File file = scriptChooser(I18n.text("ui.15551fbf3a7f")).showOpenDialog(scene.getWindow());
        if (file != null) runScriptFile(file.toPath());
    }

    /** Python on_filerunscript: read the file, show the Terminal and run it there. */
    boolean runScriptFile(Path file) {
        String text;
        try { text = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8); }
        catch (IOException | RuntimeException error) {
            appendConsole(I18n.text("ui.d5ebbe35932d") + file + ": " + error.getMessage());
            return false;
        }
        rememberScriptDirectory(file);
        return runInTerminal(text, file.getFileName().toString());
    }

    private boolean runInTerminal(String script, String label) {
        openTerminal();
        if (!terminalPanel.runScript(script, label)) {
            appendConsole(I18n.text("ui.f70979e19856") + label + ".");
            return false;
        }
        return true;
    }

    private void openScriptTab(String text, Path file) {
        ScriptEditorPanel panel = new ScriptEditorPanel(text, file, new ScriptEditorPanel.Host() {
            @Override public boolean run(String script, String label) { return runInTerminal(script, label); }
            @Override public Path chooseSaveTarget(Path suggested) {
                FileChooser chooser = scriptChooser(I18n.text("ui.171f16205d8a"));
                chooser.setInitialFileName(suggested == null ? "new_script.FlatScript" : suggested.getFileName().toString());
                File chosen = chooser.showSaveDialog(scene.getWindow());
                if (chosen == null) return null;
                rememberScriptDirectory(chosen.toPath());
                return chosen.toPath();
            }
            @Override public void log(String message) { appendConsole(message); }
        });
        Tab tab = new Tab();
        tab.getStyleClass().add("script-editor-tab");
        tab.setGraphic(legacyIcon("script16.png", 16));
        tab.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(panel::title, panel.dirtyProperty()));
        tab.setContent(panel);
        tab.setOnCloseRequest(event -> {
            if (!confirmScriptClose(panel)) event.consume();
        });
        centerTabs.getTabs().add(tab);
        centerTabs.getSelectionModel().select(tab);
        panel.focusCode();
    }

    private java.util.function.Predicate<ScriptEditorPanel> scriptDiscardDecision = this::askDiscardScript;
    void setScriptDiscardDecision(java.util.function.Predicate<ScriptEditorPanel> decision) { scriptDiscardDecision = decision; }

    private boolean confirmScriptClose(ScriptEditorPanel script) {
        if (!script.isDirty()) return true;
        String draft = script.text();
        return scriptDiscardDecision.test(script) && (!script.isDirty() || draft.equals(script.text()));
    }

    /** Dialogs run nested FX event loops: a previous approval must not discard a newer draft. */
    private boolean confirmScriptsClose() {
        Map<ScriptEditorPanel,String> approved = new java.util.IdentityHashMap<>();
        for (Tab tab : List.copyOf(centerTabs.getTabs())) {
            if (tab.getContent() instanceof ScriptEditorPanel script && script.isDirty()) {
                String draft = script.text();
                if (!confirmScriptClose(script)) return false;
                approved.put(script, draft);
            }
        }
        for (Tab tab : centerTabs.getTabs()) {
            if (tab.getContent() instanceof ScriptEditorPanel script && script.isDirty()
                    && !script.text().equals(approved.get(script))) {
                appendConsole(I18n.text("ui.ce874e98abd6"));
                return false;
            }
        }
        return true;
    }

    private boolean askDiscardScript(ScriptEditorPanel panel) {
        if (scene == null || scene.getWindow() == null) return false;
        var dialog = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        dialog.initOwner(scene.getWindow());
        dialog.setTitle(I18n.text("ui.1cc9044939b4"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18n.text("ui.744ea47f0cc0") + panel.title().replace(" *", "") + I18n.text("ui.d73e82604bb6"));
        var save = new javafx.scene.control.ButtonType(I18n.text("ui.aef7cd5b2081"), javafx.scene.control.ButtonBar.ButtonData.YES);
        var discard = new javafx.scene.control.ButtonType(I18n.text("ui.1a878a55c594"), javafx.scene.control.ButtonBar.ButtonData.NO);
        dialog.getButtonTypes().setAll(save, discard, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getScene().getStylesheets().setAll(scene.getStylesheets());
        var choice = dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL);
        return choice == discard || choice == save && panel.save() && !panel.isDirty();
    }

    /** Opens (or re-selects) the Tcl Terminal tab - see {@link TerminalPanel} for its scope. */
    private void openTerminal() {
        for (Tab tab : centerTabs.getTabs()) if ("terminal-tab".equals(tab.getId())) {
            centerTabs.getSelectionModel().select(tab);
            terminalPanel.focusInput();
            return;
        }
        if (terminalPanel == null) {
            terminalPanel = new TerminalPanel(new org.flatcam.cam.tcl.TclInterpreter(), I18n.text("ui.054a26b8c050"), jobExecutor);
            new TclFlatcamCommands(this).registerOn(terminalPanel.interpreter());
        }
        Tab tab = new Tab(); tab.setId("terminal-tab"); tab.setText("Terminal"); tab.setContent(terminalPanel);
        tab.setGraphic(legacyIcon("shell32.png", 16));
        tab.setOnCloseRequest(event -> {
            if (terminalPanel.isBusy()) { terminalPanel.cancel(); event.consume(); }
        });
        centerTabs.getTabs().add(tab); centerTabs.getSelectionModel().select(tab);
        terminalPanel.focusInput();
    }

    boolean confirmToolsDatabaseClose() {
        boolean allowed = toolsDatabasePanel == null || toolsDatabasePanel.confirmClose();
        if (allowed && terminalPanel != null) terminalPanel.cancel();
        return allowed;
    }

    @FunctionalInterface
    private interface DatabaseProjection<T> { List<T> read(org.json.JSONObject root) throws IOException; }

    private <T> List<T> toolsDatabaseTools(DatabaseProjection<T> projection) {
        try {
            if (toolsDatabasePanel != null) return projection.read(toolsDatabasePanel.snapshot());
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.text("ui.f77357cce603"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tools Database", "*.FlatDB", "*.flatdb", "*.json"));
            String remembered = AppPreferences.loadToolsDatabasePath();
            if (!remembered.isBlank()) {
                Path previous = Path.of(remembered);
                if (Files.isDirectory(previous.getParent())) chooser.setInitialDirectory(previous.getParent().toFile());
            }
            File selected = chooser.showOpenDialog(scene.getWindow());
            if (selected == null) return List.of();
            ToolsDatabase database = ToolsDatabase.load(selected.toPath());
            AppPreferences.saveToolsDatabasePath(selected.toPath());
            return projection.read(database.toJson());
        } catch (IOException error) { throw new IllegalArgumentException(error.getMessage(), error); }
    }

    private StackPane centeredPlaceholder(String text) {
        Label label = new Label(text);
        label.setTextAlignment(TextAlignment.CENTER);
        return new StackPane(label);
    }

    private VBox buildBottomPanel() {
        progressBar.setId("console-job-progress");
        progressPercentLabel.setId("console-job-percent");
        cancelJobButton.setId("console-job-cancel");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressPercentLabel.setMouseTransparent(true);
        progressPercentLabel.getStyleClass().add("progress-percentage");
        StackPane progressWithPercentage = new StackPane(progressBar, progressPercentLabel);
        HBox.setHgrow(progressWithPercentage, Priority.ALWAYS);

        cancelJobButton.visibleProperty().bind(cancelJobButton.disableProperty().not());
        cancelJobButton.managedProperty().bind(cancelJobButton.visibleProperty());
        HBox progressRow = new HBox(8, progressWithPercentage, cancelJobButton);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(4));

        console.setEditable(false);
        console.setPrefRowCount(6);
        VBox.setVgrow(console, Priority.ALWAYS);

        VBox panel = new VBox(progressRow, console);
        panel.getStyleClass().add("bottom-panel");
        return panel;
    }

    private void runDemoJob() {
        if (runningJob != null) {
            return;
        }
        runDemoJobButton.setDisable(true);
        cancelJobButton.setDisable(false);
        updateProgress(0);
        setStatus("Executando...", RUNNING_COLOR);
        appendConsole(I18n.text("ui.0254e728f912"));

        JobHandle<Void> handle = jobExecutor.submit(new DemoJob(), (fraction, message) ->
                Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;

        handle.completion()
                .thenAccept(result -> Platform.runLater(() -> {
                    setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
                    appendConsole(I18n.text("ui.f5d44253372a"));
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (isCancellation(error)) {
                            setStatus("Cancelado.", CANCELLED_COLOR);
                            appendConsole(I18n.text("ui.ef4855864482"));
                        } else {
                            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
                            appendConsole(I18n.text("ui.e70bab4ff2b7") + error.getMessage());
                        }
                        onJobFinished();
                    });
                    return null;
                });
    }

    /**
     * Fase 3 vertical slice, minimal: open -> parse (flatcam-cam) -> display
     * (a throwaway Canvas render - see PlotAreaView). Reuses the same
     * progress bar/cancel button/console as the demo job, processing any
     * number of selected files one at a time (matching the legacy app's
     * multi-select file-open dialogs).
     */
    private void openGerberPrototype() {
        List<File> files = pickCamFiles(I18n.text("ui.585b99a363f7"),
                new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.cmp", "*.gtl", "*.gbl", "*.gm1", "*.txt"));
        if (!files.isEmpty()) {
            openGerberQueue(files, 0);
        }
    }

    private void openRecent(RecentFiles.Entry entry) {
        if (runningJob != null) { appendConsole(I18n.text("ui.df12b110e3bb")); return; }
        List<File> files = List.of(entry.path().toFile());
        switch (entry.kind()) {
            case PROJECT -> openProjectFromMenu(entry.path());
            case GERBER -> openGerberQueue(files,0);
            case EXCELLON -> openExcellonQueue(files,0);
            case GCODE -> openGCodeQueue(files,0);
            case SVG_GEOMETRY -> importDrawing(DrawingFormat.SVG,false,files);
            case SVG_GERBER -> importDrawing(DrawingFormat.SVG,true,files);
            case DXF_GEOMETRY -> importDrawing(DrawingFormat.DXF,false,files);
            case DXF_GERBER -> importDrawing(DrawingFormat.DXF,true,files);
            case HPGL -> importDrawing(DrawingFormat.HPGL,false,files);
            case PDF -> importPdf(files);
        }
    }

    private void openGCode() {
        List<File> files = pickCamFiles(I18n.text("ui.03e51a82a635"),
                new FileChooser.ExtensionFilter(I18n.text("ui.7914f06a2b6c"), "*.nc", "*.gcode", "*.tap", "*.cnc", "*.txt", "*.imf", "*.plt", "*.hpgl", "*.hpg", "*.rml", "*.prn"));
        if (!files.isEmpty()) {
            openGCodeQueue(files, 0);
        }
    }

    private void openGCodeQueue(List<File> files, int index) {
        if (index == 0 && runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        if (index >= files.size()) {
            updateProgress(1);
            setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
            onJobFinished();
            return;
        }
        File file = files.get(index);
        long epoch = tclProjectEpoch;
        JobHandle<?>[] progressOwner = {null};
        String message = I18n.text("ui.2fbca7ff1f26") + (index + 1) + "/" + files.size() + ": " + file.getName() + "...";
        if (index == 0) {
            beginJob(message);
        } else {
            setStatus(message, RUNNING_COLOR);
            appendConsole(message);
        }
        JobHandle<ImportedGCode> handle = jobExecutor.submit(context -> {
            context.checkCancelled();
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            context.checkCancelled();
            GCodeToolpathParser.Result preview;
            try {
                preview = GCodeToolpathParser.parse(text, context::isCancelled,
                        fraction -> context.reportProgress((index + fraction) / files.size(), message));
            } catch (RuntimeException invalidCode) {
                if (invalidCode instanceof CancellationException) {
                    throw invalidCode;
                }
                preview = new GCodeToolpathParser.Result(null, null,
                        I18n.text("ui.869552d1d60c") + invalidCode.getMessage(),
                        (int) text.lines().count(), "MM");
            }
            return new ImportedGCode(text, preview);
        }, cncPreviewProgress(progressOwner));
        runningJob = handle;
        progressOwner[0] = handle;
        handle.completion().thenAccept(imported -> Platform.runLater(() -> {
            if (runningJob != handle) return;
            if (handle.isCancelled() || epoch != tclProjectEpoch) {
                reportJobError(handle.isCancelled() ? new CancellationException()
                        : new IllegalStateException(I18n.text("ui.f4676bc282ed")),
                        I18n.text("ui.bdd4d9994930"));
                onJobFinished();
                return;
            }
            GCodeToolpathParser.Result preview = imported.preview();
            String name = uniqueDerivedName(file.getName());
            TreeItem<String> item = addCncJobToProject(name, file.getName(), file.toPath(),
                    imported.text(), preview.travelGeometry(), preview.cutGeometry(),
                    preview.travelCenterlines(), preview.cutCenterlines(), previewWidthFor(preview),
                    preview.stats());
            selectProjectItem(item);
            if (preview.plotAvailable()) {
                setDisplayUnits(preview.units());
                focusCncJob(item, cncJobByItem.get(item));
            } else {
                appendConsole(file.getName() + ": " + preview.warning());
            }
            appendConsole(I18n.text("ui.fbca4e0924de") + file.getName() + " (" + preview.lineCount() + I18n.text("ui.d1862403f7c1"));
            recentFiles.remember(RecentFiles.Kind.GCODE,file.toPath());
            openGCodeQueue(files, index + 1);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                if (runningJob != handle) return;
                reportJobError(error, I18n.text("ui.e767f0b7167a") + file.getName() + ": ");
                if (isCancellation(error) || epoch != tclProjectEpoch) {
                    onJobFinished();
                } else {
                    openGCodeQueue(files, index + 1);
                }
            });
            return null;
        });
    }

    private void openGerberQueue(List<File> files, int index) {
        if (index >= files.size()) {
            updateProgress(1);
            setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
            onJobFinished();
            return;
        }
        File file = files.get(index);
        String message = I18n.text("ui.c7e9b4d643f7") + (index + 1) + "/" + files.size() + ": " + file.getName() + "...";
        if (index == 0) {
            beginJob(message);
        } else {
            setStatus(message, RUNNING_COLOR);
            appendConsole(message);
        }
        JobHandle<GerberImage> handle = jobExecutor.submit(context ->
                new GerberParser(ToolDefaults.gerberImport()).parse(file.toPath(), context::isCancelled,
                        fileFraction -> context.reportProgress(
                                (index + fileFraction) / files.size(), message)),
                (fraction, progressMessage) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(progressMessage);
                }));
        runningJob = handle;

        handle.completion()
                .thenAccept(image -> Platform.runLater(() -> {
                    setDisplayUnits(image.units());
                    appendConsole(String.format(
                            I18n.text("ui.be67cf4d0180"),
                            image.apertures().size(), image.totalArea(), Arrays.toString(image.bounds())));
                    TreeItem<String> item = addGerberToProject(file, image);
                    recentFiles.remember(RecentFiles.Kind.GERBER,file.toPath());
                    plotAreaView.fitToLayer(item);
                    openGerberQueue(files, index + 1);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, I18n.text("ui.08cc26381ea3") + file.getName() + ": ");
                        if (isCancellation(error)) {
                            onJobFinished();
                        } else {
                            openGerberQueue(files, index + 1);
                        }
                    });
                    return null;
                });
    }

    /** Fase 4 vertical slice - same shape as {@link #openGerberPrototype()}, see flatcam-cam's ExcellonParser. */
    private void openExcellonPrototype() {
        List<File> files = pickCamFiles(I18n.text("ui.db597830bf5d"),
                new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
        if (!files.isEmpty()) {
            openExcellonQueue(files, 0);
        }
    }

    private void openExcellonQueue(List<File> files, int index) {
        if (index >= files.size()) {
            updateProgress(1);
            setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
            onJobFinished();
            return;
        }
        File file = files.get(index);
        String message = I18n.text("ui.5f4789e01cb0") + (index + 1) + "/" + files.size() + ": " + file.getName() + "...";
        if (index == 0) {
            beginJob(message);
        } else {
            setStatus(message, RUNNING_COLOR);
            appendConsole(message);
        }
        JobHandle<ExcellonImage> handle = jobExecutor.submit(context ->
                new ExcellonParser(ToolDefaults.excellonImport()).parse(file.toPath(), context::isCancelled,
                        fileFraction -> context.reportProgress(
                                (index + fileFraction) / files.size(), message)),
                (fraction, progressMessage) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(progressMessage);
                }));
        runningJob = handle;

        handle.completion()
                .thenAccept(image -> Platform.runLater(() -> {
                    setDisplayUnits(image.units());
                    appendConsole(String.format(
                            I18n.text("ui.67067f3b4261"),
                            image.toolDiameters().size(), image.totalDrills(), image.totalSlots(),
                            Arrays.toString(image.bounds())));
                    TreeItem<String> item = addExcellonToProject(file, image);
                    recentFiles.remember(RecentFiles.Kind.EXCELLON,file.toPath());
                    plotAreaView.fitToLayer(item);
                    openExcellonQueue(files, index + 1);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, I18n.text("ui.283bed7cf300") + file.getName() + ": ");
                        if (isCancellation(error)) {
                            onJobFinished();
                        } else {
                            openExcellonQueue(files, index + 1);
                        }
                    });
                    return null;
                });
    }

    /** A drawing parsed by SvgImporter, DxfImporter or HpglImporter ({@code pens} only for HPGL). */
    private record ImportedDrawing(File file, Geometry shapes, Geometry copper, Map<Integer, Geometry> pens,
                                   int skippedText, String error) {
    }

    private enum DrawingFormat { SVG, DXF, HPGL }

    private interface DrawingReader {
        ImportedDrawing read(File file, String units, CancellationToken cancellation) throws IOException;
    }

    /**
     * File > Importar > SVG/DXF como Geometry/Gerber and HPGL2 - app_Main.py's
     * on_file_importsvg / on_file_importdxf / on_fileopenhpgl2, in the current units
     * (Python uses the application units). All chosen files are parsed in one
     * background job; a bad file is reported and skipped without losing the others.
     * An HPGL file drawn with several pens gives one Geometry per pen, so each pen can
     * get its own tool diameter (Python made one tool per pen, all 2.4 wide).
     */
    private void importDrawing(DrawingFormat drawingFormat, boolean asGerber) {
        String format = drawingFormat == DrawingFormat.HPGL ? "HPGL2" : drawingFormat.name();
        FileChooser.ExtensionFilter filter = switch (drawingFormat) {
            case SVG -> new FileChooser.ExtensionFilter("SVG", "*.svg");
            case DXF -> new FileChooser.ExtensionFilter("DXF", "*.dxf");
            case HPGL -> new FileChooser.ExtensionFilter("HPGL/HPGL2", "*.plt", "*.hpgl", "*.hpg", "*.hgl", "*.txt");
        };
        List<File> files = pickCamFiles(I18n.text("ui.c826abd76e49") + format + I18n.text("ui.16ae4b6060c0") + (asGerber ? "Gerber" : "Geometry"), filter);
        if (files.isEmpty()) {
            return;
        }
        importDrawing(drawingFormat,asGerber,files);
    }

    private void importDrawing(DrawingFormat drawingFormat, boolean asGerber, List<File> files) {
        String format = drawingFormat == DrawingFormat.HPGL ? "HPGL2" : drawingFormat.name();
        RecentFiles.Kind historyKind = switch (drawingFormat) {
            case SVG -> asGerber ? RecentFiles.Kind.SVG_GERBER : RecentFiles.Kind.SVG_GEOMETRY;
            case DXF -> asGerber ? RecentFiles.Kind.DXF_GERBER : RecentFiles.Kind.DXF_GEOMETRY;
            case HPGL -> RecentFiles.Kind.HPGL;
        };
        boolean dxf = drawingFormat == DrawingFormat.DXF;
        DrawingReader reader = switch (drawingFormat) {
            case DXF -> (file, units, cancellation) -> {
                DxfImporter.Result result = DxfImporter.parse(file.toPath(), units, cancellation);
                return new ImportedDrawing(file, result.shapes(), result.copper(), null,
                        result.skippedTextEntities(), null);
            };
            case SVG -> (file, units, cancellation) -> {
                SvgImporter.Result result = SvgImporter.parse(file.toPath(), units, cancellation);
                return new ImportedDrawing(file, result.shapes(), result.copper(), null,
                        result.skippedTextElements(), null);
            };
            case HPGL -> (file, units, cancellation) -> {
                HpglImporter.Result result = HpglImporter.parse(file.toPath(), units, cancellation);
                return new ImportedDrawing(file, result.all(), null, result.pens(), result.skippedLabels(), null);
            };
        };
        String units = plotAreaView.units();
        beginJob(I18n.text("ui.612cbfd9f79a") + format + "...");
        JobHandle<List<ImportedDrawing>> handle = jobExecutor.submit(context -> {
            List<ImportedDrawing> imported = new ArrayList<>();
            for (int index = 0; index < files.size(); index++) {
                File file = files.get(index);
                context.reportProgress((double) index / files.size(), I18n.text("ui.612cbfd9f79a") + file.getName() + "...");
                try {
                    imported.add(reader.read(file, units, context::isCancelled));
                } catch (IOException | IllegalArgumentException failed) {
                    imported.add(new ImportedDrawing(file, null, null, null, 0, failed.getMessage()));
                }
            }
            return imported;
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().thenAccept(imported -> Platform.runLater(() -> {
            TreeItem<String> last = null;
            for (ImportedDrawing drawing : imported) {
                String fileName = drawing.file().getName();
                if (drawing.error() != null) {
                    appendConsole(I18n.text("ui.ba3f64627ee0") + fileName + ": " + drawing.error());
                    continue;
                }
                if (asGerber && drawing.copper().isEmpty()) {
                    appendConsole(I18n.text("ui.ba3f64627ee0") + fileName + I18n.text("ui.8b1a3a1519bb")
                            + (dxf ? "." : I18n.text("ui.456d5b6ef1c3")));
                    continue;
                }
                if (drawing.pens() != null && drawing.pens().size() > 1) {
                    for (Map.Entry<Integer, Geometry> pen : drawing.pens().entrySet()) {
                        String penName = uniqueDerivedName(fileName + "_P" + pen.getKey());
                        last = addGeometryToProject(penName, fileName, units, pen.getValue(), true);
                        appendConsole(format + I18n.text("ui.67ac2e1c0cd5") + penName + I18n.text("ui.d332c67cd71d")
                                + pen.getKey() + ", " + pen.getValue().getNumGeometries() + I18n.text("ui.17f8d9bee7ed"));
                    }
                    recentFiles.remember(historyKind,drawing.file().toPath());
                    continue;
                }
                String name = uniqueDerivedName(fileName);
                last = asGerber
                        ? addGerberToProject(name, drawing.file().toPath(),
                                GerberImage.of(units, Map.of(), drawing.copper(), null, Map.of()))
                        : addGeometryToProject(name, fileName, units, drawing.shapes(), true);
                appendConsole(format + I18n.text("ui.9768a918b8fd") + (asGerber ? "Gerber" : "Geometry") + ": " + name
                        + " (" + drawing.shapes().getNumGeometries() + I18n.text("ui.179c44a87535"));
                recentFiles.remember(historyKind,drawing.file().toPath());
                if (drawing.skippedText() > 0) {
                    appendConsole("  " + drawing.skippedText() + I18n.text("ui.29c99809e58a")
                            + switch (drawingFormat) {
                                case DXF -> I18n.text("ui.2aa1dfad5fab");
                                case SVG -> I18n.text("ui.4e385ff79805");
                                case HPGL -> I18n.text("ui.aea17a89abb2");
                            });
                }
            }
            if (last != null) {
                setDisplayUnits(units);
                plotAreaView.fitToLayer(last);
                selectProjectItem(last);
            }
            updateProgress(1);
            setStatus(last != null ? I18n.text("ui.93dd97eb5672") : I18n.text("ui.1e18211d36c4"), last != null ? IDLE_COLOR : ERROR_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, I18n.text("ui.ba3f64627ee0") + format + ": ");
                onJobFinished();
            });
            return null;
        });
    }

    private record ImportedPdf(File file, PdfImporter.Result result, String error) {
    }

    /**
     * File > Importar > PDF - ToolPDF.py/ParsePDF.py: only the one PDF style FlatCAM
     * targets, a vector print of Gerber-like artwork. Every stroke-color change in the
     * file becomes its own Gerber object ({@code <arquivo>_1}, {@code _2}, ...); round
     * white-filled shapes become one Excellon object ({@code <arquivo>_0}), like Python's
     * own layer numbering. Several files import in one background job.
     */
    private void importPdf() {
        List<File> files = pickCamFiles(I18n.text("ui.b3e374bf0a7b"), new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        if (files.isEmpty()) {
            return;
        }
        importPdf(files);
    }

    private void importPdf(List<File> files) {
        String units = plotAreaView.units();
        beginJob(I18n.text("ui.2c1563afbd70"));
        JobHandle<List<ImportedPdf>> handle = jobExecutor.submit(context -> {
            List<ImportedPdf> imported = new ArrayList<>();
            for (int index = 0; index < files.size(); index++) {
                File file = files.get(index);
                context.reportProgress((double) index / files.size(), I18n.text("ui.612cbfd9f79a") + file.getName() + "...");
                try {
                    imported.add(new ImportedPdf(file, PdfImporter.parse(file.toPath(), units, context::isCancelled),
                            null));
                } catch (IOException | IllegalArgumentException failed) {
                    imported.add(new ImportedPdf(file, null, failed.getMessage()));
                }
            }
            return imported;
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().thenAccept(imported -> Platform.runLater(() -> {
            TreeItem<String> last = null;
            for (ImportedPdf pdf : imported) {
                String fileName = pdf.file().getName();
                if (pdf.error() != null) {
                    appendConsole(I18n.text("ui.ba3f64627ee0") + fileName + ": " + pdf.error());
                    continue;
                }
                PdfImporter.Result result = pdf.result();
                if (result.drills() != null || !result.layers().isEmpty())
                    recentFiles.remember(RecentFiles.Kind.PDF,pdf.file().toPath());
                if (result.drills() != null) {
                    String name = uniqueDerivedName(fileName + "_0");
                    last = addExcellonToProject(name, pdf.file().toPath(), result.drills());
                    appendConsole(I18n.text("ui.e223496c1e9b") + name + " ("
                            + result.drills().totalDrills() + I18n.text("ui.d44841d1376d"));
                }
                for (int layer = 0; layer < result.layers().size(); layer++) {
                    String name = uniqueDerivedName(fileName + "_" + (layer + 1));
                    last = addGerberToProject(name, pdf.file().toPath(), result.layers().get(layer));
                    appendConsole(I18n.text("ui.64e8c54e4719") + name);
                }
            }
            if (last != null) {
                setDisplayUnits(units);
                plotAreaView.fitToLayer(last);
                selectProjectItem(last);
            }
            updateProgress(1);
            setStatus(last != null ? I18n.text("ui.93dd97eb5672") : I18n.text("ui.1e18211d36c4"), last != null ? IDLE_COLOR : ERROR_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, I18n.text("ui.c57bfda58973"));
                onJobFinished();
            });
            return null;
        });
    }

    /** Shared "pick fabrication files" flow (multi-select): remembers the last folder across both Gerber and Excellon. */
    private List<File> pickCamFiles(String title, FileChooser.ExtensionFilter filter) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.5484cbc46e1c"));
            return List.of();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        List<File> files = chooser.showOpenMultipleDialog(scene.getWindow());
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        AppPreferences.saveLastCamDirectory(files.get(0).getParentFile().getAbsolutePath());
        return files;
    }

    private void beginJob(String statusText) {
        runDemoJobButton.setDisable(true);
        cancelJobButton.setDisable(false);
        updateProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus(statusText, RUNNING_COLOR);
        appendConsole(statusText);
    }

    private void updateProgress(double fraction) {
        double bounded = !Double.isFinite(fraction) || fraction < 0 ? -1 : Math.min(1, fraction);
        progressBar.setProgress(bounded);
        if (bounded < 0) {
            progressPercentLabel.setText("...");
            return;
        }
        progressPercentLabel.setText(Math.round(bounded * 100) + "%");
    }

    private void reportJobError(Throwable error, String failurePrefix) {
        if (isCancellation(error)) {
            setStatus("Cancelado.", CANCELLED_COLOR);
            appendConsole(I18n.text("ui.f102bb211dda"));
        } else {
            setStatus(I18n.text("ui.1e18211d36c4"), ERROR_COLOR);
            appendConsole(failurePrefix + error.getMessage());
        }
    }

    /**
     * Writes every Gerber/Excellon's own resolved geometry (WKT-embedded,
     * matching the legacy app's .FlatPrj shape - see ProjectFileIO's doc)
     * plus each Geometry object's WKT/per-tool paths and each CNC Job's current G-code text. Unlike a path, embedded
     * geometry survives a save/reload even after an in-memory edit
     * (Transformations) or if the original source file is later moved or
     * deleted.
     */
    private void saveProject() {
        saveProject(ignored -> {});
    }

    private void saveProjectAs() {
        saveProjectAs(ignored -> {});
    }

    /**
     * Python on_file_saveproject: save straight to the current project file, else Save As. Only an FX
     * {@code .fcnproj} is rewritten silently; an imported Python {@code .FlatPrj} (or no file yet) asks for
     * the destination, so Ctrl+S never replaces a Python original behind the user's back.
     */
    private void saveProject(java.util.function.Consumer<Boolean> done) {
        Path current = currentProjectFile;
        if (current == null || !current.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".fcnproj")
                || current.getParent() == null || !Files.isDirectory(current.getParent())) {
            saveProjectAs(done);
            return;
        }
        if (!projectSaveAllowed()) { done.accept(false); return; }
        writeProject(current.toFile(), snapshotProject(), projectStateStamp(), done);
    }

    private boolean projectSaveAllowed() {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return false;
        }
        if (gcodeEditor.isActive() || gerberEditor.isActive() || geometryEditor.isActive()
                || excellonEditor.isActive()) {
            appendConsole(I18n.text("ui.feb7dc841cb7"));
            return false;
        }
        return true;
    }

    /** Python on_file_saveprojectas: always asks for the destination and format. */
    private void saveProjectAs(java.util.function.Consumer<Boolean> done) {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            done.accept(false);
            return;
        }
        if (gcodeEditor.isActive() || gerberEditor.isActive() || geometryEditor.isActive()
                || excellonEditor.isActive()) {
            appendConsole(I18n.text("ui.feb7dc841cb7"));
            done.accept(false);
            return;
        }
        ProjectFile project = snapshotProject();
        ProjectChangeTracker.State before=projectStateStamp();

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.7e0a780ac03b"));
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.text("ui.a6e80a14adb7"), "*.fcnproj"),
                new FileChooser.ExtensionFilter(I18n.text("ui.9398fbb7d54b"), "*.FlatPrj"));
        // A project that came from the Python application is offered back in its own format first.
        if (currentPythonLegacy != null) chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().get(1));
        if(currentProjectFile!=null) {
            chooser.setInitialFileName(currentProjectFile.getFileName().toString());
            chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().get(currentProjectFile.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj") ? 1 : 0));
        }
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = currentProjectFile!=null && Files.isDirectory(currentProjectFile.getParent())
                ? currentProjectFile.getParent() : Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showSaveDialog(scene.getWindow());
        if (file == null) {
            done.accept(false);
            return;
        }
        boolean pythonFormat = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj");
        if(!pythonFormat && !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".fcnproj")) {
            appendConsole(I18n.text("ui.0b7157474952")); done.accept(false); return;
        }
        writeProject(file, project, before, done);
    }

    private void writeProject(File file, ProjectFile project, ProjectChangeTracker.State before,
                              java.util.function.Consumer<Boolean> done) {
        boolean pythonFormat = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj");
        if (pythonFormat) appendConsole(project.pythonLegacy() != null
                ? I18n.text("ui.43461862e527")
                : I18n.text("ui.8b235f6dbb34"));

        beginJob(I18n.text("ui.ec1021563807") + file.getName() + "...");
        // Menu save publishes via the serializers directly; compression has no cooperative cancellation point.
        cancelJobButton.setDisable(true);
        JobHandle<Path> handle = jobExecutor.submit(context -> {
            context.reportProgress(Double.NaN, I18n.text("ui.bd089e425302"));
            if (pythonFormat) PythonProjectWriter.save(project, file.toPath());
            else ProjectFileIO.save(project, file.toPath());
            context.reportProgress(1, I18n.text("ui.3a4737de1105"));
            return file.toPath();
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().whenComplete((saved,error) -> Platform.runLater(() -> {
            boolean success=false;
            try {
                if(error!=null)reportJobError(error,I18n.text("ui.b6ca594ea118"));
                else {
                    markProjectSaved(saved,before);
                    AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
                    recentFiles.remember(RecentFiles.Kind.PROJECT,saved);
                    appendConsole(I18n.text("ui.c9987f501523")+saved); updateProgress(1); setStatus(I18n.text("ui.3a4737de1105"),IDLE_COLOR);
                    success=!hasUnsavedProjectChanges() && !hasEditorChanges();
                    if(!success)appendConsole(I18n.text("ui.410322c62e73"));
                }
            } catch(RuntimeException failure) { reportJobError(failure,I18n.text("ui.12c0e06a63bd")); }
            finally { onJobFinished(); }
            done.accept(success);
        }));
    }

    /** Menu and Terminal snapshot only immutable session versions; serialization stays on the worker. */
    private ProjectFile snapshotProject() {
        List<ProjectFile.GerberEntry> gerbers = new ArrayList<>();
        for (Map.Entry<TreeItem<String>, GerberImage> entry : gerberByItem.entrySet()) {
            TreeItem<String> item = entry.getKey();
            Color[] colors = plotAreaView.layerColors(item);
            gerbers.add(new ProjectFile.GerberEntry(item.getValue(), entry.getValue(),
                    colors != null ? colors[0].toString() : null, colors != null ? colors[1].toString() : null,
                    plotAreaView.isLayerVisible(item), plotAreaView.isLayerFilled(item),
                    plotAreaView.isLayerMulticolor(item), gerberFollowItems.contains(item)));
        }
        List<ProjectFile.ExcellonEntry> excellons = new ArrayList<>();
        for (Map.Entry<TreeItem<String>, ExcellonImage> entry : excellonByItem.entrySet()) {
            TreeItem<String> item = entry.getKey();
            Color[] colors = plotAreaView.layerColors(item);
            excellons.add(new ProjectFile.ExcellonEntry(item.getValue(), entry.getValue(),
                    colors != null ? colors[0].toString() : null, colors != null ? colors[1].toString() : null,
                    plotAreaView.isLayerVisible(item), plotAreaView.isLayerFilled(item),
                    plotAreaView.isLayerMulticolor(item),
                    drillDefaultsByItem.getOrDefault(item, Map.of()), drillCncSettingsByItem.get(item)));
        }
        List<ProjectFile.CncJobRecord> jobs = cncJobByItem.entrySet().stream()
                .map(entry -> new ProjectFile.CncJobRecord(entry.getKey().getValue(),
                        entry.getValue().sourceName(), entry.getValue().outputFile().toString(),
                        entry.getValue().gcode(), isObjectVisible(entry.getKey()),snapshotCncPlotSettings(entry.getKey())))
                .toList();
        List<ProjectFile.GeometryEntry> geometries = new ArrayList<>();
        for (Map.Entry<TreeItem<String>, GeometryEntry> entry : geometryByItem.entrySet()) {
            TreeItem<String> item = entry.getKey();
            GeometryEntry geometry = entry.getValue();
            Color[] colors = plotAreaView.layerColors(item);
            geometries.add(new ProjectFile.GeometryEntry(item.getValue(), geometry.sourceName(),
                    geometry.units(), geometry.geometry(), geometry.strokeOnly(), geometry.tools(),
                    colors != null ? colors[0].toString() : null,
                    colors != null ? colors[1].toString() : null, plotAreaView.isLayerVisible(item),
                    geometry.cncDefaults(), geometryCncSettingsByItem.get(item), geometry.machiningTarget(), plotAreaView.isLayerMulticolor(item)));
        }
        ProjectFile.PythonLegacy legacy = currentPythonLegacy;
        if (legacy != null) {
            Map<String, String> origins = new LinkedHashMap<>();
            gerberByItem.keySet().forEach(item -> snapshotPythonOrigin(origins, item, "gerber"));
            excellonByItem.keySet().forEach(item -> snapshotPythonOrigin(origins, item, "excellon"));
            geometryByItem.keySet().forEach(item -> snapshotPythonOrigin(origins, item, "geometry"));
            cncJobByItem.keySet().forEach(item -> snapshotPythonOrigin(origins, item, "cncjob"));
            legacy = new ProjectFile.PythonLegacy(legacy.original(), origins);
        }
        return new ProjectFile(gerbers, excellons, geometries, jobs, currentProjectImportWarnings, legacy);
    }

    private void snapshotPythonOrigin(Map<String, String> origins, TreeItem<String> item, String kind) {
        String origin = pythonOriginByItem.get(item);
        if (origin != null) origins.put(ProjectFile.PythonLegacy.key(kind, item.getValue()), origin);
    }

    private void rememberPythonOrigin(TreeItem<String> item, String kind) {
        if (currentPythonLegacy == null) return;
        Map<String, String> origins = currentPythonLegacy.objectOrigins();
        String origin = origins == null ? item.getValue() : origins.get(ProjectFile.PythonLegacy.key(kind, item.getValue()));
        if (origin != null) pythonOriginByItem.put(item, origin);
    }

    /**
     * Loads and validates every referenced file off the JavaFX thread. The visible project is replaced only after the
     * whole CAM batch succeeds, so a malformed/missing source or cancellation leaves the current project untouched.
     */
    private void openProject() {
        if (runningJob != null) {
            appendConsole(I18n.text("ui.df12b110e3bb"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("ui.0c49c87c5cb7"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.text("ui.ffaeba6c0d30"), "*.fcnproj", "*.FlatPrj"),
                new FileChooser.ExtensionFilter(I18n.text("ui.a6e80a14adb7"), "*.fcnproj"),
                new FileChooser.ExtensionFilter(I18n.text("ui.77c3a9c0e13b"), "*.FlatPrj"));
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showOpenDialog(scene.getWindow());
        if (file == null) {
            return;
        }
        openProjectFromMenu(file.toPath());
    }

    private void openProjectFromMenu(Path path) {
        protectProjectTransition(() -> loadProjectFromMenu(path));
    }
    private void loadProjectFromMenu(Path path) {
        try { requireProjectReplacementAllowed(); }
        catch (IllegalStateException blocked) { appendConsole(blocked.getMessage()); return; }
        File file = path.toAbsolutePath().normalize().toFile();
        TclProjectState before = captureTclProjectState();
        beginJob(I18n.text("ui.d34e9289a784") + file.getName() + "...");
        JobHandle<LoadedProject> handle = jobExecutor.submit(context -> loadProject(file.toPath(), context),
                (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;
        handle.completion().whenComplete((project, failure) -> Platform.runLater(() -> {
            try {
                if (failure != null) throw new java.util.concurrent.CompletionException(failure);
                if (handle.isCancelled()) throw new CancellationException(I18n.text("ui.942c696ec9f9"));
                checkTclProjectState(before);
                if (hasEditorChanges()) throw new IllegalStateException(I18n.text("ui.929e45c23243"));
                restoreProject(project, true);
                reportOpenedProject(file.toPath(), project);
                markProjectSaved(file.toPath(),projectStateStamp());
                recentFiles.remember(RecentFiles.Kind.PROJECT,file.toPath());
                AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
                updateProgress(1);
                setStatus(I18n.text("ui.93dd97eb5672"), IDLE_COLOR);
            } catch (Exception error) {
                reportJobError(error, I18n.text("ui.f813ab7697f6"));
                appendConsole(I18n.text("ui.68204c1215da"));
            } finally {
                onJobFinished();
            }
        }));
    }

    /** Shared worker preparation for menu and Tcl; no visible project mutation. */
    private LoadedProject loadProject(Path file, JobContext context) throws IOException {
        CancellationToken cancellation = context::isCancelled;
        cancellation.throwIfCancellationRequested();
        context.reportProgress(0.05, I18n.text("ui.2841703ba4e2") + file.getFileName().toString() + "...");
        cancellation.throwIfCancellationRequested();
        long decodeStart = plotAreaView.profilingEnabled() ? System.nanoTime() : 0;
        ProjectFile project = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj")
                ? PythonProjectIO.load(file) : ProjectFileIO.load(file);
        plotAreaView.logPerformancePhase("project decode (worker)", decodeStart);
        validateProjectColors(project);
        cancellation.throwIfCancellationRequested();
        context.reportProgress(0.5, I18n.text("ui.efa993b08dac"));

        List<LoadedCncJob> cncJobs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        warnings.addAll(project.importWarnings());
        int total = Math.max(1, project.cncJobs().size());
        int processed = 0;
        for (ProjectFile.CncJobRecord job : project.cncJobs()) {
            cancellation.throwIfCancellationRequested();
            Path outputPath = Path.of(job.outputPath());
            context.reportProgress(0.5 + 0.5 * processed / total, I18n.text("ui.2fbca7ff1f26") + outputPath.getFileName() + "...");
            try {
                String gcode = job.gcode() != null ? job.gcode() : Files.readString(outputPath);
                Geometry travel = null;
                Geometry cut = null;
                Geometry travelCenterlines = null;
                Geometry cutCenterlines = null;
                double previewStrokeWidth = 0;
                String units = null;
                GCodeToolpathParser.ToolpathStats stats = null;
                try {
                    int jobIndex = processed;
                    GCodeToolpathParser.Result parsed = GCodeToolpathParser.parse(gcode, cancellation,
                            fraction -> context.reportProgress(0.5 + 0.5 * (jobIndex + fraction) / total,
                                    I18n.text("ui.84447c77473c") + outputPath.getFileName() + "..."));
                    travel = parsed.travelGeometry();
                    cut = parsed.cutGeometry();
                    travelCenterlines = parsed.travelCenterlines();
                    cutCenterlines = parsed.cutCenterlines();
                    previewStrokeWidth = previewWidthFor(parsed);
                    stats = parsed.stats();
                    units = parsed.plotAvailable() ? parsed.units() : null;
                    if (parsed.warning() != null) {
                        warnings.add(I18n.text("ui.3e9a08c5414d") + outputPath.getFileName() + ": " + parsed.warning());
                    }
                } catch (IllegalArgumentException invalidGcode) {
                    warnings.add(I18n.text("ui.22afc000f23c") + outputPath.getFileName()
                            + I18n.text("ui.b605389e5a9f") + invalidGcode.getMessage());
                }
                String name = job.name() != null ? job.name() : outputPath.getFileName().toString();
                cncJobs.add(new LoadedCncJob(name, job.sourceName(), outputPath, gcode,
                        travel, cut, travelCenterlines, cutCenterlines,
                        previewStrokeWidth, units, job.visible(), stats, job.plotSettings()));
            } catch (IOException e) {
                warnings.add(I18n.text("ui.d95527c88dc1") + outputPath + ": " + e.getMessage());
            }
            processed++;
        }

        cancellation.throwIfCancellationRequested();
        context.reportProgress(1, I18n.text("ui.c781b005bd24"));
        return new LoadedProject(project.gerbers(), project.excellons(), project.geometries(),
                List.copyOf(cncJobs), List.copyOf(warnings), project.importWarnings(), project.pythonLegacy());
    }

    /** Fail before replacing anything if a stored colour cannot be restored. */
    private static void validateProjectColors(ProjectFile project) {
        for (var entry : project.gerbers()) validateColors(entry.fillColorWeb(), entry.strokeColorWeb());
        for (var entry : project.excellons()) validateColors(entry.fillColorWeb(), entry.strokeColorWeb());
        for (var entry : project.geometries()) validateColors(entry.fillColorWeb(), entry.strokeColorWeb());
    }

    private static void validateColors(String fill, String stroke) {
        if (fill != null) Color.web(fill);
        if (stroke != null) Color.web(stroke);
    }

    /** FX-only publication. Tcl replacement must not cancel its own running script. */
    private void restoreProject(LoadedProject project, boolean cancelTerminal) {
        long restoreStart = plotAreaView.profilingEnabled() ? System.nanoTime() : 0;
        boolean previousRestoring = restoringProject;
        restoringProject = true;
        plotAreaView.beginBatchUpdate();
        try {
            clearProject(cancelTerminal);
            currentProjectImportWarnings = project.importWarnings();
            currentPythonLegacy = project.pythonLegacy();
            for (ProjectFile.GerberEntry loaded : project.gerbers()) {
                TreeItem<String> item = addGerberToProject(loaded.name(), null, loaded.image());
                rememberPythonOrigin(item, "gerber");
                applyRestoredGerberState(item, loaded.image(), loaded);
                setDisplayUnits(loaded.image().units());
            }
            for (ProjectFile.ExcellonEntry loaded : project.excellons()) {
                TreeItem<String> item = addExcellonToProject(loaded.name(), null, loaded.image(),
                        loaded.drillDefaults());
                if (loaded.cncSettings() != null) drillCncSettingsByItem.put(item, loaded.cncSettings());
                rememberPythonOrigin(item, "excellon");
                applyRestoredExcellonState(item, loaded);
                setDisplayUnits(loaded.image().units());
            }
            for (ProjectFile.GeometryEntry loaded : project.geometries()) {
                TreeItem<String> item = addGeometryToProject(loaded.name(), loaded.sourceName(),
                        loaded.units(), loaded.geometry(), loaded.strokeOnly(), loaded.tools(),
                        loaded.cncDefaults());
                if (loaded.cncSettings() != null) geometryCncSettingsByItem.put(item, loaded.cncSettings());
                if (loaded.machiningTarget() != null) {
                    var entry = geometryByItem.get(item);
                    geometryByItem.put(item, new GeometryEntry(entry.sourceName(), entry.units(), entry.geometry(),
                            entry.strokeOnly(), entry.tools(), entry.cncDefaults(), loaded.machiningTarget()));
                }
                rememberPythonOrigin(item, "geometry");
                plotAreaView.setLayerMulticolor(item, loaded.multicolor());
                if (loaded.fillColorWeb() != null && loaded.strokeColorWeb() != null) {
                    plotAreaView.setLayerColors(item, Color.web(loaded.fillColorWeb()),
                            Color.web(loaded.strokeColorWeb()));
                }
                if (!loaded.visible()) {
                    setObjectVisible(item, false);
                }
                setDisplayUnits(loaded.units());
            }
            for (LoadedCncJob loaded : project.cncJobs()) {
                TreeItem<String> item = addCncJobToProject(loaded.name(), loaded.sourceName(),
                        loaded.outputPath(), loaded.gcode(), loaded.travelGeometry(), loaded.cutGeometry(),
                        loaded.travelCenterlines(), loaded.cutCenterlines(), loaded.previewStrokeWidth(),
                        loaded.stats());
                rememberPythonOrigin(item, "cncjob");
                applyCncPlotSettings(item,loaded.plotSettings()==null?org.flatcam.app.project.CncPlotSettings.legacy():loaded.plotSettings(),loaded.visible());
                if (!loaded.visible()) setObjectVisible(item, false);
                if (loaded.units() != null) {
                    setDisplayUnits(loaded.units());
                }
            }
        } finally {
            restoringProject = previousRestoring;
            plotAreaView.endBatchUpdate();
        }
        plotAreaView.logPerformancePhase("project restore (FX thread)", restoreStart);
    }

    private void reportOpenedProject(Path file, LoadedProject project) {
        project.warnings().forEach(this::appendConsole);
        appendConsole(I18n.text("ui.cada362d23a9") + file);
        if (file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj"))
            appendConsole(I18n.text("ui.736af1183e9e"));
    }

    /** Restores a reloaded Gerber's plot appearance/visibility/follow-mode - see ProjectFile.GerberEntry. */
    private void applyRestoredGerberState(TreeItem<String> item, GerberImage image, ProjectFile.GerberEntry entry) {
        Color fill = entry.fillColorWeb() != null ? Color.web(entry.fillColorWeb()) : GERBER_FILL;
        Color stroke = entry.strokeColorWeb() != null ? Color.web(entry.strokeColorWeb()) : GERBER_STROKE;
        if (entry.followMode()) {
            gerberFollowItems.add(item);
            plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GERBER, image.followGeometry(), fill, stroke, true);
        } else {
            plotAreaView.setLayerColors(item, fill, stroke);
        }
        plotAreaView.setLayerFilled(item, entry.filled());
        plotAreaView.setLayerMulticolor(item, entry.multicolor());
        if (!entry.visible()) {
            setObjectVisible(item, false);
        }
    }

    /** Restores a reloaded Excellon's plot appearance/visibility - see ProjectFile.ExcellonEntry. */
    private void applyRestoredExcellonState(TreeItem<String> item, ProjectFile.ExcellonEntry entry) {
        if (entry.fillColorWeb() != null && entry.strokeColorWeb() != null) {
            plotAreaView.setLayerColors(item, Color.web(entry.fillColorWeb()), Color.web(entry.strokeColorWeb()));
        }
        plotAreaView.setLayerFilled(item, entry.filled());
        plotAreaView.setLayerMulticolor(item, entry.multicolor());
        if (!entry.visible()) {
            setObjectVisible(item, false);
        }
    }

    private void clearProject() {
        clearProject(true);
    }

    /**
     * Python File > New Project (on_file_new_click): same Save/Don't save/Cancel protection as opening a
     * project, then every object leaves the session and the project file is forgotten. Preferences, the
     * Terminal's variables and window layout are kept, as Python keeps its Tcl interpreter.
     */
    private void newProject() {
        protectProjectTransition(() -> {
            closeToolPanel();
            clearProject();
            currentProjectFile = null;
            projectChanges.saved(projectStateStamp());
            appendConsole(I18n.text("ui.9c0a84086008"));
            setStatus(I18n.text("ui.9c0a84086008"), IDLE_COLOR);
        });
    }

    private void clearProject(boolean cancelTerminal) {
        tclProjectEpoch++;
        if (cancelTerminal && terminalPanel != null) terminalPanel.cancel();
        plotAreaView.cancelPlacement();
        plotMoveHistory.clear();
        gerberEditor.cancel();
        geometryEditor.cancel();
        excellonEditor.cancel();
        gcodeEditor.cancel();
        gerbersNode.getChildren().clear();
        excellonNode.getChildren().clear();
        geometryNode.getChildren().clear();
        cncJobsNode.getChildren().clear();
        gerberByItem.clear();
        excellonByItem.clear();
        drillDefaultsByItem.clear();
        drillCncSettingsByItem.clear();
        geometryCncSettingsByItem.clear();
        geometryByItem.clear();
        cncJobByItem.clear();
        hiddenCncTools.clear();
        cncAnnotationsOff.clear();
        cncArrowsOff.clear();
        cncPlotSettings.clear();
        gerberFollowItems.clear();
        sourcePathByItem.clear();
        currentProjectImportWarnings = List.of();
        currentPythonLegacy = null;
        pythonOriginByItem.clear();
        plotAreaView.clearLayers();
        setDisplayUnits(UiNumbers.initialUnits());
    }

    /** Adds the tree item and, since every opened object gets its own layer now, its plot too - visible immediately. */
    private TreeItem<String> addGerberToProject(File file, GerberImage image) {
        return addGerberToProject(file.getName(), file.toPath(), image);
    }

    private TreeItem<String> addGerberToProject(String displayName, Path sourcePath, GerberImage image) {
        TreeItem<String> item = new TreeItem<>(displayName);
        gerberByItem.put(item, image);
        sourcePathByItem.put(item, sourcePath);
        gerbersNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GERBER, image.solidGeometry(), GERBER_FILL, GERBER_STROKE, false);
        applyNewPlotDefaults(item, "gerber");
        return item;
    }

    private TreeItem<String> addExcellonToProject(File file, ExcellonImage image) {
        return addExcellonToProject(file.getName(), file.toPath(), image);
    }

    private TreeItem<String> addExcellonToProject(String displayName, Path sourcePath, ExcellonImage image) {
        return addExcellonToProject(displayName, sourcePath, image, Map.of());
    }

    private TreeItem<String> addExcellonToProject(String displayName, Path sourcePath, ExcellonImage image,
                                                  Map<Integer, DrillGCodeParameters> drillDefaults) {
        TreeItem<String> item = new TreeItem<>(displayName);
        excellonByItem.put(item, image);
        drillDefaultsByItem.put(item, Map.copyOf(drillDefaults));
        sourcePathByItem.put(item, sourcePath);
        excellonNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.EXCELLON, image.solidGeometry(), DRILL_FILL, DRILL_STROKE, false);
        applyNewPlotDefaults(item, "excellon");
        return item;
    }

    private TreeItem<String> addGeometryToProject(String displayName, String sourceName, String units,
                                                  Geometry geometry, boolean strokeOnly) {
        return addGeometryToProject(displayName, sourceName, units, geometry, strokeOnly, List.of());
    }

    private TreeItem<String> addGeometryToProject(String displayName, String sourceName, String units,
                                                  Geometry geometry, boolean strokeOnly, List<ToolGeometry> tools) {
        return addGeometryToProject(displayName, sourceName, units, geometry, strokeOnly, tools, null);
    }

    private TreeItem<String> addGeometryToProject(String displayName, String sourceName, String units,
                                                  Geometry geometry, boolean strokeOnly, List<ToolGeometry> tools,
                                                  GeometryGCodeParameters cncDefaults) {
        TreeItem<String> item = new TreeItem<>(displayName);
        geometryByItem.put(item, new GeometryEntry(sourceName, units, geometry, strokeOnly, tools, cncDefaults));
        geometryNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GEOMETRY, geometry,
                GEOMETRY_FILL, GEOMETRY_STROKE, strokeOnly);
        applyNewPlotDefaults(item, "geometry");
        return item;
    }

    private void applyNewPlotDefaults(TreeItem<String> item, String kind) {
        if (restoringProject) return;
        String prefix = "display." + kind + ".";
        Color stroke = ToolDefaults.color(prefix + "stroke");
        Color fill = kind.equals("geometry") ? stroke : ToolDefaults.color(prefix + "fill");
        plotAreaView.beginBatchUpdate();
        try {
            plotAreaView.setLayerColors(item, fill, stroke);
            if (!kind.equals("geometry")) plotAreaView.setLayerFilled(item, ToolDefaults.flag(prefix + "solid"));
            plotAreaView.setLayerMulticolor(item, ToolDefaults.flag(prefix + "multicolor"));
            plotAreaView.setLayerVisible(item, ToolDefaults.flag(prefix + "plot"));
            if (kind.equals("gerber") && ToolDefaults.flag("display.gerber.follow"))
                setGerberFollow(item, gerberByItem.get(item), true);
        } finally { plotAreaView.endBatchUpdate(); }
    }

    private void setGerberFollow(TreeItem<String> item, GerberImage image, boolean follow) {
        if (follow) gerberFollowItems.add(item); else gerberFollowItems.remove(item);
        Color[] colors = plotAreaView.layerColors(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GERBER,
                follow ? image.followGeometry() : image.solidGeometry(),
                colors == null ? GERBER_FILL : colors[0], colors == null ? GERBER_STROKE : colors[1], follow);
    }

    private void cancelDemoJob() {
        if (runningJob != null) {
            runningJob.cancel();
        }
    }

    private static boolean isCancellation(Throwable error) {
        return error instanceof CancellationException || error.getCause() instanceof CancellationException;
    }

    private void onJobFinished() {
        activeCamGeneration = null;
        runningJob = null;
        runDemoJobButton.setDisable(false);
        cancelJobButton.setDisable(true);
    }

    private void appendConsole(String line) {
        console.appendText(line + System.lineSeparator());
    }
}
