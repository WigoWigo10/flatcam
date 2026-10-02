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
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.app.project.PythonProjectIO;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.app.project.ToolsDatabase;
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.cam.CancellationToken;
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
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.flatcam.cam.gerber.GerberGeometryGenerator;
import org.flatcam.cam.gerber.GerberExporter;
import org.flatcam.cam.hpgl.HpglImporter;
import org.flatcam.cam.pdf.PdfImporter;
import org.flatcam.cam.svg.FilmExporter;
import org.flatcam.cam.svg.SvgExporter;
import org.flatcam.cam.svg.SvgImporter;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gerber.edit.GerberEditSession;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationResult;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.ncc.NccParameters;
import org.flatcam.cam.ncc.NccResult;
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
final class MainWindow {

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

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressPercentLabel = new Label("0%");
    private final Label statusLabel = new Label("Pronto.");
    private final Circle statusDot = new Circle(5, Color.web("#4caf50"));
    private final Label activityLabel = new Label("Idle.");
    private final Label unitsLabel = new Label("[mm]");
    private final Button runDemoJobButton = new Button();
    private final Button cancelJobButton = new Button();
    private final TextArea console = new TextArea();
    private final TabPane centerTabs = new TabPane();
    private final StackPane propertiesContainer = new StackPane();
    private final Label propertiesPlaceholder = new Label("Selecione um objeto\npara ver seus parametros.");

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
                                GCodeToolpathParser.ToolpathStats stats) {
    }

    /** A Geometry CNC Job plus the totals read back from its own text (see GCodeToolpathParser.ToolpathStats). */
    private record GeneratedCncJob(CncJobResult job, GCodeToolpathParser.Result preview) {
    }

    private record ImportedGCode(String text, GCodeToolpathParser.Result preview) {
    }

    /** {@code tools} is empty for a plain single-purpose Geometry (no tool association); see NccToolPanel's doc. */
    private record GeometryEntry(String sourceName, String units, Geometry geometry,
                                 boolean strokeOnly, List<ToolGeometry> tools,
                                 GeometryGCodeParameters cncDefaults) {
        private GeometryEntry(String sourceName, String units, Geometry geometry,
                              boolean strokeOnly, List<ToolGeometry> tools) {
            this(sourceName, units, geometry, strokeOnly, tools, null);
        }
    }

    /** Gerber/Excellon entries already carry their own fully-resolved geometry (ProjectFileIO), no re-parsing needed. */
    private record LoadedProject(List<ProjectFile.GerberEntry> gerbers, List<ProjectFile.ExcellonEntry> excellons,
                                 List<ProjectFile.GeometryEntry> geometries,
                                 List<LoadedCncJob> cncJobs, List<String> warnings,
                                 List<String> importWarnings) {
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
    /** Caption of the lit route leg in the CNC Job properties, when that panel is showing. */
    private Label cncStepLabel;
    /** Conversion caveats remain attached when an imported Python project is saved as native .fcnproj. */
    private List<String> currentProjectImportWarnings = List.of();
    /** Gerber objects currently plotted as unbuffered trace centerlines instead of solid copper. */
    private final Set<TreeItem<String>> gerberFollowItems = new LinkedHashSet<>();
    /** Original file path for Gerber/Excellon items - what gets written to a saved project file. */
    private final Map<TreeItem<String>, Path> sourcePathByItem = new LinkedHashMap<>();

    private final PlotAreaView plotAreaView = new PlotAreaView();

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
            appendConsole("Ja existe uma operacao em andamento.");
            return false;
        }
        beginJob("Aplicando edicao Gerber...");
        JobHandle<GerberEditSession.ApplyResult> handle = jobExecutor.submit(context ->
                request.generate(context::isCancelled,
                        fraction -> context.reportProgress(fraction, "Aplicando edicao Gerber...")),
                (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;
        handle.completion().thenAccept(result -> Platform.runLater(() -> {
            onJobFinished();
            updateProgress(1);
            setStatus("Edicao Gerber concluida.", IDLE_COLOR);
            onSuccess.accept(result);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao aplicar edicao Gerber: ");
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
            appendConsole("Ja existe uma operacao em andamento.");
            return false;
        }
        String message = "Calculando " + request.operation().name().toLowerCase(java.util.Locale.ROOT) + "...";
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
            setStatus("Operacao Geometry concluida.", IDLE_COLOR);
            onSuccess.accept(result);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha na operacao Geometry: ");
                onJobFinished();
                Throwable cause = error.getCause() == null ? error : error.getCause();
                onFailure.accept(isCancellation(error) ? "Operacao cancelada."
                        : "Falha na operacao: " + cause.getMessage());
            });
            return null;
        });
        return true;
    }

    private boolean applyGCodeEdit(TreeItem<String> item, String text, Runnable onSuccess,
                                   Consumer<String> onFailure) {
        CncJobEntry original = cncJobByItem.get(item);
        if (runningJob != null || original == null) {
            appendConsole(runningJob != null ? "Ja existe uma operacao em andamento."
                    : "O CNC Job nao esta mais no projeto.");
            return false;
        }
        beginJob("Analisando G-code editado...");
        JobHandle<GCodeToolpathParser.Result> handle = jobExecutor.submit(context ->
                GCodeToolpathParser.parse(text, context::isCancelled,
                        fraction -> context.reportProgress(fraction, "Analisando G-code editado...")),
                (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;
        handle.completion().thenAccept(parsed -> Platform.runLater(() -> {
            onJobFinished();
            if (!cncJobByItem.containsKey(item)) {
                onFailure.accept("O CNC Job foi removido durante a edicao.");
                return;
            }
            updateCncJob(item, original, text, parsed);
            updateProgress(1);
            setStatus("G-code atualizado.", IDLE_COLOR);
            appendConsole("Editor G-Code: " + item.getValue() + " atualizado em memoria."
                    + (parsed.warning() == null ? "" : " " + parsed.warning()));
            onSuccess.run();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao aplicar G-code: ");
                onJobFinished();
                onFailure.accept(isCancellation(error) ? "Analise cancelada; nenhuma alteracao aplicada."
                        : "Falha ao analisar G-code: "
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
                plotAreaView.setLayerCenterlineLod(cutKey, parsed.cutCenterlines(), previewWidth);
                plotAreaView.setLayerVisible(cutKey, visible);
            }
            if (parsed.travelGeometry() != null && !parsed.travelGeometry().isEmpty()) {
                plotAreaView.putLayer(travelKey, PlotAreaView.LayerCategory.CNCJOB,
                        parsed.travelGeometry(), CNC_TRAVEL_FILL, CNC_TRAVEL_STROKE, false);
                plotAreaView.setLayerCenterlineLod(travelKey, parsed.travelCenterlines(), previewWidth);
                plotAreaView.setLayerVisible(travelKey, visible);
            }
        } finally {
            plotAreaView.endBatchUpdate();
        }
        refreshCncAnnotations(item);
        refreshPlotSelectionOutline();
        if (projectTree.getSelectionModel().getSelectedItem() == item) {
            showProperties(item);
        }
        for (Tab openTab : centerTabs.getTabs()) {
            if ((openTab.getText().equals("Fonte - " + item.getValue())
                    || openTab.getText().equals(item.getValue()))
                    && openTab.getContent() instanceof TextArea viewer && !viewer.isEditable()) {
                viewer.setText(text);
            }
        }
    }

    private boolean saveGCodeDraftAs(String text, Consumer<String> onComplete) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return false;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar rascunho G-code");
        boolean icp = GCodeToolpathParser.isIcpProgram(text);
        boolean hpgl = GCodeToolpathParser.isHpglProgram(text);
        boolean roland = GCodeToolpathParser.isRolandProgram(text);
        chooser.getExtensionFilters().add(roland ? new FileChooser.ExtensionFilter("Roland RML-1", "*.rml", "*.prn")
                : hpgl ? new FileChooser.ExtensionFilter("HPGL", "*.plt", "*.hpgl", "*.hpg")
                : icp ? new FileChooser.ExtensionFilter("ISEL ICP", "*.imf")
                : new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(roland ? "program_edit.rml" : hpgl ? "program_edit.plt" : icp ? "program_edit.imf" : "gcode_edit.nc");
        File file = chooser.showSaveDialog(scene.getWindow());
        if (file == null) {
            return false;
        }
        Path destination = file.toPath().toAbsolutePath();
        beginJob("Salvando G-code...");
        // A completed file replacement cannot be rolled back safely; only analysis jobs are cancellable.
        cancelJobButton.setDisable(true);
        JobHandle<Path> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.1, "Preparando arquivo G-code...");
            Path temporary = Files.createTempFile(destination.getParent(),
                    "." + destination.getFileName() + ".", ".tmp");
            try {
                Files.writeString(temporary, text, StandardCharsets.UTF_8);
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                context.reportProgress(1, "G-code salvo.");
                return destination;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().thenAccept(path -> Platform.runLater(() -> {
            onJobFinished();
            setStatus("G-code salvo.", IDLE_COLOR);
            appendConsole("Rascunho G-code salvo em " + path);
            onComplete.accept("Arquivo salvo em " + path + ". O CNC Job ainda nao foi alterado.");
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao salvar G-code: ");
                onJobFinished();
                onComplete.accept(isCancellation(error) ? "Gravacao cancelada."
                        : "Falha ao salvar G-code: "
                                + (error.getCause() == null ? error : error.getCause()).getMessage());
            });
            return null;
        });
        return true;
    }

    private Scene scene;
    /** Animated tooltips for the whole window (see {@link FluidTooltips}). */
    private FluidTooltips fluidTooltips;
    private SplitPane horizontalSplit;
    private SplitPane verticalSplit;
    private TreeView<String> projectTree;
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
    private ThemeOption currentTheme = AppPreferences.loadTheme(ThemeOption.ICE_LIGHT);
    private final BooleanProperty darkIcons = new SimpleBooleanProperty(currentTheme.isDark());
    private JobHandle<?> runningJob;
    private ContextMenu plotContextMenu;
    private ContextMenu projectContextMenu;
    private final PlotMoveHistory<TreeItem<String>> plotMoveHistory = new PlotMoveHistory<>();
    private boolean applyingPlotMove;

    MainWindow(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
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
            if (event.getCode() == KeyCode.ESCAPE && plotAreaView.isPlacementActive()) {
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
            } else if (event.getCode() == KeyCode.G && !event.isControlDown()
                    && !event.isAltDown() && !event.isMetaDown() && !event.isShiftDown()
                    && !isTextInputTarget(event.getTarget())) {
                statusControls.toggleGrid();
                event.consume();
            } else if (event.isControlDown() && !event.isAltDown() && !event.isMetaDown()
                    && !event.isShiftDown() && !plotAreaView.isPlacementActive()
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
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (sidebarDividerDragging) {
                sidebarDividerDragging = false;
                saveSidebarDividerPosition();
            }
        });
        currentTheme.applyTo(scene);
        plotAreaView.applyTheme(currentTheme);
        return scene;
    }

    private static boolean isTextInputTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TextInputControl) {
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
        if (consoleCollapsed) {
            return; // the collapsed (~1.0) position is not a real layout preference - see toggleConsole().
        }
        AppPreferences.saveSplitVertical(verticalSplit.getDividerPositions()[0]);
    }

    private void saveSidebarDividerPosition() {
        if (sidebarCollapsed || horizontalSplit.getDividers().isEmpty()) {
            return;
        }
        double position = horizontalSplit.getDividerPositions()[0];
        if (Double.isFinite(position) && position > 0 && position < 1) {
            AppPreferences.saveSplitHorizontalForScreen(currentScreenId, position);
        }
    }

    private boolean isSidebarDividerTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null && current != horizontalSplit; current = current.getParent()) {
            if (current.getStyleClass().contains("split-pane-divider")) {
                return true;
            }
        }
        return false;
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
            if (sidebarCollapsed || sidebarDividerDragging || horizontalSplit.getWidth() <= 0) {
                return;
            }
            horizontalSplit.setDividerPositions(
                    AppPreferences.loadSplitHorizontalForScreen(currentScreenId, AppPreferences.loadSplitHorizontal(0.22)));
        });
    }

    /**
     * Collapses the resizable progress/console panel, or restores it. Moving the
     * divider to 1.0 alone does not reach a true 100% collapse - the console
     * TextArea has a nonzero intrinsic min-height, so a sliver always stayed
     * visible. Removing the pane from the SplitPane's items entirely (and
     * re-adding it to restore) has no such floor.
     */
    private void toggleConsole(boolean show) {
        if (!show) {
            dividerBeforeConsoleCollapse = verticalSplit.getDividerPositions()[0];
            consoleCollapsed = true;
            verticalSplit.getItems().remove(bottomPanel);
        } else {
            consoleCollapsed = false;
            verticalSplit.getItems().add(bottomPanel);
            verticalSplit.setDividerPositions(dividerBeforeConsoleCollapse);
            attachVerticalDividerSaveListener(); // re-adding creates a new Divider instance.
        }
        AppPreferences.saveConsoleOpen(show);
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
        if (visible) {
            sidebarCollapsed = false;
            horizontalSplit.getItems().add(0, leftTabs);
            scheduleSidebarRestore();
        } else {
            saveSidebarDividerPosition();
            sidebarCollapsed = true;
            horizontalSplit.getItems().remove(leftTabs);
        }
        sidebarToggle.setSelected(visible);
    }

    private MenuItem plannedItem(String label, String icon) {
        return chromeItem(label, icon, null);
    }

    private HBox buildMenuRow() {
        sidebarToggle = new ToggleButton(null, legacyIcon("notebook32.png", 16));
        sidebarToggle.setTooltip(new Tooltip("Mostrar/ocultar painel lateral"));
        sidebarToggle.setAccessibleText("Mostrar ou ocultar Projeto, Propriedades e Ferramenta");
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
        button.setTooltip(new Tooltip(label + (action == null ? " — em desenvolvimento" : "")));
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
            appendConsole("Conclua ou cancele o editor atual antes de abrir outro editor.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage image = gerberByItem.get(item);
        if (image == null) {
            appendConsole("Selecione um Gerber para editar.");
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
            appendConsole("Conclua ou cancele o editor atual antes de abrir o Editor Excellon.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        ExcellonImage image = excellonByItem.get(item);
        if (image == null) {
            appendConsole("Selecione um objeto Excellon para editar.");
            return;
        }
        excellonEditor.start(item, image);
    }

    private void editSelectedGeometry() {
        if (gerberEditor.isActive() || gcodeEditor.isActive() || excellonEditor.isActive()) {
            appendConsole("Conclua ou cancele o editor atual antes de abrir o Editor Geometry.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GeometryEntry entry = geometryByItem.get(item);
        if (entry == null) {
            appendConsole("Selecione um objeto Geometry para editar.");
            return;
        }
        geometryEditor.start(item, entry.geometry(), entry.tools(), entry.strokeOnly());
    }

    private void editSelectedGCode() {
        if (gerberEditor.isActive() || geometryEditor.isActive() || excellonEditor.isActive()) {
            appendConsole("Conclua ou cancele o editor atual antes de abrir o Editor G-Code.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        CncJobEntry entry = cncJobByItem.get(item);
        if (entry == null) {
            appendConsole("Selecione um CNC Job para editar o G-code.");
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
            appendConsole("Nenhum editor esta aberto.");
        }
    }

    /** Edit > Join Objects > Gerber(s) -> Gerber: one new "Combo_Gerber" from the selected Gerbers. */
    private void joinSelectedGerbers() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() < 2) {
            appendConsole("Juntar exige pelo menos dois objetos. Selecionados agora: " + selected.size());
            return;
        }
        List<GerberImage> images = new ArrayList<>();
        for (TreeItem<String> item : selected) {
            GerberImage image = gerberByItem.get(item);
            if (image == null) {
                appendConsole("Falhou. Juntar Gerbers funciona apenas com objetos Gerber.");
                return;
            }
            images.add(image);
        }
        try {
            GerberImage joined = GerberJoin.join(images);
            TreeItem<String> created = addGerberToProject(uniqueDerivedName("Combo_Gerber"), null, joined);
            selectProjectItem(created);
            plotAreaView.fitToLayer(created);
            appendConsole("Gerbers unidos em " + created.getValue() + " (" + images.size() + " objetos)"
                    + (joined.shapes().isEmpty() ? "; o resultado nao e editavel no Gerber Editor." : "."));
        } catch (IllegalArgumentException failed) {
            appendConsole("Falhou ao juntar: " + failed.getMessage());
        }
    }

    /** Edit > Join Objects > Excellon(s) -> Excellon: one new "Combo_Excellon"; same-diameter tools are fused. */
    private void joinSelectedExcellons() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() < 2) {
            appendConsole("Juntar exige pelo menos dois objetos. Selecionados agora: " + selected.size());
            return;
        }
        List<ExcellonImage> images = new ArrayList<>();
        for (TreeItem<String> item : selected) {
            ExcellonImage image = excellonByItem.get(item);
            if (image == null) {
                appendConsole("Falhou. Juntar Excellons funciona apenas com objetos Excellon.");
                return;
            }
            images.add(image);
        }
        try {
            ExcellonImage joined = ExcellonJoin.join(images, true);
            TreeItem<String> created = addExcellonToProject(uniqueDerivedName("Combo_Excellon"), null, joined);
            selectProjectItem(created);
            plotAreaView.fitToLayer(created);
            appendConsole("Excellons unidos em " + created.getValue() + " (" + joined.totalDrills() + " furos, "
                    + joined.totalSlots() + " slots, " + joined.toolDiameters().size() + " ferramentas).");
        } catch (IllegalArgumentException failed) {
            appendConsole("Falhou ao juntar: " + failed.getMessage());
        }
    }

    /** Edit > Join Objects > Geo/Gerber/Exc -> Geo: one new Geometry ("Combo_SingleGeo" or "Combo_MultiGeo"). */
    private void joinSelectedToGeometry() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.size() < 2) {
            appendConsole("Juntar exige pelo menos dois objetos. Selecionados agora: " + selected.size());
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
                appendConsole("Falhou. Somente objetos Geometry, Gerber ou Excellon podem ser unidos em um Geometry.");
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
            appendConsole("Objetos unidos em " + created.getValue() + " (" + selected.size() + " objetos"
                    + (joined.tools().isEmpty() ? "" : ", " + joined.tools().size() + " ferramentas") + ").");
        } catch (IllegalArgumentException failed) {
            appendConsole("Falhou ao juntar: " + failed.getMessage());
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
            appendConsole("Nenhum objeto selecionado.");
            return;
        }
        TreeItem<String> last = null;
        for (TreeItem<String> item : selected) {
            try {
                TreeItem<String> created = conversion.convert(item);
                if (created == null) {
                    appendConsole(item.getValue() + ": este objeto nao pode ser convertido em " + target + ".");
                    continue;
                }
                appendConsole("Convertido em " + target + ": " + created.getValue() + ".");
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
                diameter = promptNumber("Single → Multi-Geometry", "Diametro da ferramenta:", 0.1);
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
            appendConsole("Falhou. Selecione um objeto Geometry " + from + " e tente de novo.");
            return;
        }
        appendConsole(converted + " Geometry convertida(s) para " + to + ".");
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
        appendConsole(title + ": use um numero positivo.");
        return null;
    }

    /** Edit > Conversion > Outline to Area: a filled Geometry from each selected closed Gerber/Geometry outline. */
    private void convertOutlineToArea() {
        List<TreeItem<String>> selected = selectedObjects();
        if (selected.isEmpty()) {
            appendConsole("Nenhum objeto selecionado.");
            return;
        }
        for (TreeItem<String> item : selected) {
            GerberImage gerber = gerberByItem.get(item);
            GeometryEntry entry = geometryByItem.get(item);
            if (gerber == null && entry == null) {
                appendConsole("Somente objetos Gerber ou Geometry podem ser convertidos de contorno para area.");
                continue;
            }
            Geometry source = gerber != null && gerber.followGeometry() != null && !gerber.followGeometry().isEmpty()
                    ? gerber.followGeometry() : gerber != null ? gerber.solidGeometry() : entry.geometry();
            String units = gerber != null ? gerber.units() : entry.units();
            try {
                OutlineToArea.Result result = OutlineToArea.convert(source);
                TreeItem<String> created = addGeometryToProject(uniqueDerivedName(item.getValue() + "_area"),
                        item.getValue(), units, result.area(), false);
                appendConsole("Area do contorno criada: " + created.getValue() + String.format(java.util.Locale.ROOT,
                        " (%.4f %s^2)", result.area().getArea(), units.toLowerCase(java.util.Locale.ROOT)));
                if (result.candidates() > 1) {
                    appendConsole("Foram encontradas varias areas fechadas; a maior foi usada como area da placa.");
                }
                selectProjectItem(created);
            } catch (IllegalArgumentException failed) {
                appendConsole(item.getValue() + ": " + failed.getMessage());
            }
        }
    }

    private void copySelectedObjects() {
        List<TreeItem<String>> selected = selectedObjects();
        if (!selected.isEmpty()) {
            copySelection(selected);
        } else {
            appendConsole("Selecione um objeto para copiar.");
        }
    }

    private void deleteSelectedObjects() {
        List<TreeItem<String>> selected = selectedObjects();
        if (!selected.isEmpty()) {
            removeSelectionFromProject(selected);
        } else {
            appendConsole("Selecione um objeto para excluir.");
        }
    }

    private void focusSelectedObject() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (item != null && isPlottable(item)) {
            focusLayer(item);
        } else {
            appendConsole("Selecione um objeto para enquadrar.");
        }
    }

    private void openSelectedGerberTool(String label,
            java.util.function.BiConsumer<TreeItem<String>, GerberImage> action) {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage image = gerberByItem.get(item);
        if (image == null) {
            appendConsole("Selecione um Gerber para " + label + ".");
        } else {
            action.accept(item, image);
        }
    }

    private void openSelectedDrillingTool() {
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        ExcellonImage image = excellonByItem.get(item);
        if (image == null) {
            appendConsole("Selecione um Excellon para a ferramenta de furacao.");
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
        appendConsole("Selecione um Gerber ou Geometry para NCC.");
    }

    private Runnable toolAction(String id) {
        return switch (id) {
            case "cutout" -> () -> openSelectedGerberTool("Cutout", this::generateCutout);
            case "ncc" -> this::openSelectedNccTool;
            case "isolation" -> () -> openSelectedGerberTool("Isolamento", this::generateIsolation);
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
                case "explode" -> geometryEditor::explode;
                default -> null;
            };
            menu.getItems().add(action == null ? plannedItem(command.label(), command.icon())
                    : chromeItem(command.label(), command.icon(), action));
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
        Menu fileMenu = new Menu("Arquivo");
        Menu newMenu = new Menu("Novo");
        setLegacyMenuIcon(newMenu, "new_file32.png");
        newMenu.getItems().addAll(
                plannedItem("Novo Projeto", "new_file32.png"),
                plannedItem("Geometry", "geometry32.png"),
                plannedItem("Excellon", "drill32.png"),
                plannedItem("Document", "notebook32.png"));
        Menu openMenu = new Menu("Abrir");
        setLegacyMenuIcon(openMenu, "folder32.png");
        openMenu.getItems().addAll(
                chromeItem("Projeto...", "folder32.png", this::openProject),
                chromeItem("Gerber...", "flatcam_icon32.png", this::openGerberPrototype),
                chromeItem("Excellon...", "drill32.png", this::openExcellonPrototype),
                chromeItem("G-Code...", "cnc32.png", this::openGCode),
                plannedItem("Configuracao...", "settings18.png"));
        Menu importMenu = new Menu("Importar");
        setLegacyMenuIcon(importMenu, "import.png");
        importMenu.getItems().addAll(
                chromeItem("SVG como Geometry...", "svg32.png", () -> importDrawing(DrawingFormat.SVG, false)),
                chromeItem("SVG como Gerber...", "svg32.png", () -> importDrawing(DrawingFormat.SVG, true)),
                chromeItem("DXF como Geometry...", "dxf16.png", () -> importDrawing(DrawingFormat.DXF, false)),
                chromeItem("DXF como Gerber...", "dxf16.png", () -> importDrawing(DrawingFormat.DXF, true)),
                chromeItem("HPGL2...", "import.png", () -> importDrawing(DrawingFormat.HPGL, false)),
                chromeItem("PDF...", "pdf32.png", this::importPdf));
        Menu exportMenu = new Menu("Exportar");
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
                plannedItem("Novo Script", "script_new24.png"),
                plannedItem("Abrir Script", "open_script32.png"),
                plannedItem("Executar Script", "script16.png"));
        Menu backupMenu = new Menu("Backup");
        setLegacyMenuIcon(backupMenu, "backup24.png");
        backupMenu.getItems().addAll(
                plannedItem("Importar preferencias", "backup_import24.png"),
                plannedItem("Exportar preferencias", "backup_export24.png"));
        fileMenu.getItems().addAll(newMenu, openMenu, plannedItem("Recentes", "recent_files.png"),
                new SeparatorMenuItem(), chromeItem("Salvar Projeto...", "project_save32.png", this::saveProject),
                plannedItem("Salvar Projeto Como...", "save_as.png"), new SeparatorMenuItem(),
                importMenu, exportMenu, scriptMenu, backupMenu,
                plannedItem("Imprimir PDF", "pdf32.png"), new SeparatorMenuItem(),
                chromeItem("Sair", "power16.png", () -> {
                    if (!confirmToolsDatabaseClose()) return;
                    fluidTooltips.closeNow();
                    Platform.exit();
                }));

        Menu editMenu = new Menu("Editar");
        Menu conversionsMenu = new Menu("Converter");
        setLegacyMenuIcon(conversionsMenu, "convert32.png");
        conversionsMenu.getItems().addAll(
                tipped(chromeItem("Contorno → Area", "geometry32.png", this::convertOutlineToArea),
                        "Contorno → Área", "Fecha o contorno de um Gerber ou Geometry e cria uma Geometry com a "
                        + "área da placa (a maior região fechada)."),
                tipped(chromeItem("Single → Multi-Geometry", "geometry32.png", this::convertSingleToMultiGeometry),
                        "Single → Multi-Geometry", "Coloca a geometria sob uma ferramenta de diâmetro escolhido, "
                        + "tornando-a multi-ferramenta."),
                tipped(chromeItem("Multi → Single-Geometry", "geometry32.png", this::convertMultiToSingleGeometry),
                        "Multi → Single-Geometry", "Une a geometria de todas as ferramentas numa só; a informação "
                        + "de ferramenta é descartada."),
                tipped(chromeItem("Objeto → Geometry", "geometry32.png", this::convertSelectedToGeometry),
                        "Objeto → Geometry", "Cria uma Geometry a partir de um Gerber, Excellon ou Geometry."),
                tipped(chromeItem("Objeto → Gerber", "flatcam_icon32.png", this::convertSelectedToGerber),
                        "Objeto → Gerber", "Cria um Gerber a partir de um Excellon (furos e slots) ou de uma "
                        + "Geometry (regiões e linhas com a largura da ferramenta)."),
                tipped(chromeItem("Objeto → Excellon", "drill32.png", this::convertSelectedToExcellon),
                        "Objeto → Excellon", "Cria furos nos centros das formas fechadas de uma Geometry ou dos "
                        + "flashes de um Gerber (traços de 2 pontos viram slots)."));
        Menu joinMenu = new Menu("Juntar Objetos");
        setLegacyMenuIcon(joinMenu, "union32.png");
        joinMenu.getItems().addAll(
                tipped(chromeItem("Geo/Gerber/Exc → Geo", "geometry32.png", this::joinSelectedToGeometry),
                        "Juntar em Geometry", "Une os objetos selecionados (Geometry, Gerber ou Excellon) numa "
                        + "só Geometry."),
                tipped(chromeItem("Excellon(s) → Excellon", "drill32.png", this::joinSelectedExcellons),
                        "Juntar Excellons", "Une os Excellons selecionados; ferramentas de mesmo diâmetro "
                        + "viram uma só."),
                tipped(chromeItem("Gerber(s) → Gerber", "flatcam_icon32.png", this::joinSelectedGerbers),
                        "Juntar Gerbers", "Une os Gerbers selecionados num só, juntando aberturas, formas e "
                        + "geometria."));
        Menu editorToolsMenu = new Menu("Ferramentas dos editores");
        setLegacyMenuIcon(editorToolsMenu, "edit_file32.png");
        excellonEditorMenu = new Menu("Editor Excellon");
        setLegacyMenuIcon(excellonEditorMenu, "drill32.png");
        addExcellonEditorCommands(excellonEditorMenu);
        excellonEditorMenu.setVisible(false);
        geometryEditorMenu = new Menu("Geo Editor");
        setLegacyMenuIcon(geometryEditorMenu, "geometry32.png");
        addGeometryEditorCommands(geometryEditorMenu);
        geometryEditorMenu.setVisible(false);
        editorToolsMenu.getItems().add(excellonEditorMenu);
        editMenu.getItems().addAll(
                chromeItem("Editar Objeto", "edit_file32.png", this::editSelectedObject),
                chromeItem("Salvar e Fechar Editor", "close_edit_file32.png", this::saveAndCloseEditor),
                chromeItem("Editor de G-Code", "code_editor32.png", this::editSelectedGCode),
                editorToolsMenu,
                new SeparatorMenuItem(),
                chromeItem("Copiar", "copy_file32.png", this::copySelectedObjects),
                chromeItem("Excluir", "trash32.png", this::deleteSelectedObjects),
                conversionsMenu, joinMenu,
                new SeparatorMenuItem(),
                plannedItem("Medir Distancia", "distance32.png"),
                plannedItem("Distancia Minima", "distance_min32.png"),
                plannedItem("Definir Origem", "origin32.png"),
                plannedItem("Mover para Origem", "origin2_32.png"),
                plannedItem("Ir para Localizacao", "jump_to16.png"),
                plannedItem("Localizar no Objeto", "locate32.png"),
                new SeparatorMenuItem(),
                plannedItem("Alternar Unidades", "toggle_units32.png"),
                chromeItem("Preferencias", "settings18.png", this::openPreferences));

        Menu optionsMenu = new Menu("Opcoes");
        MenuItem toolsDbItem = chromeItem("Tools Database", "search_db32.png", this::openToolsDatabase);
        MenuItem calculatorsItem = chromeItem("Calculators", "calculator24.png",
                () -> openToolPanel("Calculators", CalculatorsPanel.build()));

        // appGUI/MainGUI.py's Options menu quick actions (Rotate/Skew X/Skew Y/Flip X/Flip Y) -
        // a separate, lighter driver than the full Transform tool: fixed "selection bbox center"
        // reference, a small dialog for Rotate/Skew, instant apply for Flip. Same icons as Python
        // (rotate.png/skewX.png/skewY.png/flipx.png/flipy.png). Python also binds bare Shift+R/
        // Shift+X/Shift+Y/X/Y accelerators; the bare X/Y ones are skipped here since a scene-global
        // accelerator with no modifier would hijack typing "x"/"y" into any text field.
        MenuItem rotateItem = new MenuItem("Girar Selecao");
        rotateItem.setGraphic(legacyIcon("rotate.png", 16));
        rotateItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole("Selecione ao menos um objeto para girar.");
                return;
            }
            Double angle = promptAngle("Girar Selecao", "Angulo (graus, positivo = sentido horario):", 90);
            if (angle != null) {
                // UI-positive = clockwise; negate before building Rotate (positive = CCW there),
                // exactly like Python's own on_rotate() (obj.rotate(-num, point)).
                applyTransformToSelection(selected -> new TransformOp.Rotate(-angle, selectionCenterOrOrigin(selected)));
            }
        });
        MenuItem skewXItem = new MenuItem("Inclinar em X");
        skewXItem.setGraphic(legacyIcon("skewX.png", 16));
        skewXItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole("Selecione ao menos um objeto para inclinar.");
                return;
            }
            Double angle = promptAngle("Inclinar em X", "Angulo (graus):", 0);
            if (angle != null) {
                applyTransformToSelection(selected -> new TransformOp.Skew(angle, 0, selectionCenterOrOrigin(selected)));
            }
        });
        MenuItem skewYItem = new MenuItem("Inclinar em Y");
        skewYItem.setGraphic(legacyIcon("skewY.png", 16));
        skewYItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole("Selecione ao menos um objeto para inclinar.");
                return;
            }
            Double angle = promptAngle("Inclinar em Y", "Angulo (graus):", 0);
            if (angle != null) {
                applyTransformToSelection(selected -> new TransformOp.Skew(0, angle, selectionCenterOrOrigin(selected)));
            }
        });
        MenuItem flipXItem = new MenuItem("Espelhar em X");
        flipXItem.setGraphic(legacyIcon("flipx.png", 16));
        flipXItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole("Selecione ao menos um objeto para espelhar.");
                return;
            }
            applyTransformToSelection(selected -> new TransformOp.MirrorX(selectionCenterOrOrigin(selected)));
        });
        MenuItem flipYItem = new MenuItem("Espelhar em Y");
        flipYItem.setGraphic(legacyIcon("flipy.png", 16));
        flipYItem.setOnAction(e -> {
            if (!anyProjectObjectSelected()) {
                appendConsole("Selecione ao menos um objeto para espelhar.");
                return;
            }
            applyTransformToSelection(selected -> new TransformOp.MirrorY(selectionCenterOrOrigin(selected)));
        });

        optionsMenu.getItems().addAll(toolsDbItem, calculatorsItem,
                chromeItem("Ver Fonte", "source32.png", () -> {
                    TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
                    if (item != null && isProjectObject(item)) {
                        viewObjectSource(item);
                    } else {
                        appendConsole("Selecione um objeto para ver a fonte.");
                    }
                }), new SeparatorMenuItem(),
                rotateItem, skewXItem, skewYItem, flipXItem, flipYItem);

        Menu viewMenu = new Menu("Exibir");
        viewMenu.getItems().addAll(
                plannedItem("Replotar Tudo", "replot32.png"),
                plannedItem("Limpar Plot", "clear_plot32.png"),
                plannedItem("Aproximar", "zoom_in32.png"),
                plannedItem("Afastar", "zoom_out32.png"),
                chromeItem("Enquadrar Objeto", "zoom_fit32.png", this::focusSelectedObject),
                new SeparatorMenuItem(),
                chromeItem("Snap na grade", "grid32.png", () -> statusControls.toggleGrid()),
                chromeItem("Eixos", "axis32.png", () -> statusControls.toggleAxis()),
                chromeItem("Workspace", "workspace24.png", () -> statusControls.toggleWorkspace()),
                chromeItem("HUD", "hud_32.png", () -> statusControls.toggleHud()),
                new SeparatorMenuItem(),
                chromeItem("Mostrar/ocultar painel lateral", "notebook32.png", this::toggleSidebar),
                buildToolbarsMenu(),
                new SeparatorMenuItem(), buildThemeMenu());

        Menu objectsMenu = new Menu("Objetos");
        objectsMenu.getItems().addAll(
                plannedItem("Selecionar Todos", "select_all.png"),
                plannedItem("Desmarcar Todos", "deselect_all32.png"),
                new SeparatorMenuItem(),
                chromeItem("Copiar Selecionados", "copy32.png", this::copySelectedObjects),
                chromeItem("Excluir Selecionados", "trash32.png", this::deleteSelectedObjects));

        Menu toolsMenu = new Menu("Ferramentas");
        Menu preparationMenu = new Menu("Preparacao da placa");
        addToolCommands(preparationMenu, LegacyUiManifest.TOOLS_PREPARATION);
        Menu camMenu = new Menu("CAM / Fabricacao");
        addToolCommands(camMenu, LegacyUiManifest.TOOLS_CAM);
        Menu utilitiesMenu = new Menu("Utilitarios");
        addToolCommands(utilitiesMenu, LegacyUiManifest.TOOLS_UTILITIES);
        toolsMenu.getItems().addAll(plannedItem("Linha de Comando Tcl", "shell32.png"),
                new SeparatorMenuItem(), preparationMenu, camMenu, utilitiesMenu);

        Menu helpMenu = new Menu("Ajuda");
        MenuItem demoJobItem = new MenuItem("Executar job de demonstracao");
        demoJobItem.setOnAction(e -> runDemoJob());
        demoJobItem.disableProperty().bind(runDemoJobButton.disableProperty());
        helpMenu.getItems().addAll(
                plannedItem("Ajuda Online", "help.png"),
                plannedItem("Bookmarks", "bookmarks32.png"),
                plannedItem("Lista de Atalhos", "shortcuts24.png"),
                plannedItem("Como Usar", "videohelp24.png"),
                plannedItem("Reportar Problema", "bug32.png"),
                new SeparatorMenuItem(),
                demoJobItem,
                chromeItem("Sobre", "about32.png", () ->
                        appendConsole("FlatCAM FX - em desenvolvimento.")),
                chromeItem("Executar job de demonstracao", "code.png", this::runDemoJob));

        return new MenuBar(fileMenu, editMenu, optionsMenu, viewMenu, objectsMenu, toolsMenu, helpMenu,
                geometryEditorMenu);
    }

    /** Original and ice palettes, each with light and dark variants. */
    private Menu buildThemeMenu() {
        ToggleGroup themeGroup = new ToggleGroup();

        Menu classicMenu = new Menu("Original");
        classicMenu.getItems().addAll(
                themeItem(ThemeOption.CLASSIC_LIGHT, themeGroup),
                themeItem(ThemeOption.CLASSIC_DARK, themeGroup)
        );

        Menu iceMenu = new Menu("Gelo");
        iceMenu.getItems().addAll(
                themeItem(ThemeOption.ICE_LIGHT, themeGroup),
                themeItem(ThemeOption.ICE_DARK, themeGroup)
        );

        Menu themeMenu = new Menu("Tema");
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
        runDemoJobButton.setTooltip(new Tooltip("Executar job de demonstracao"));
        runDemoJobButton.setOnAction(e -> runDemoJob());

        cancelJobButton.setGraphic(Icons.stop(16));
        cancelJobButton.setTooltip(new Tooltip("Cancelar a operacao em andamento"));
        cancelJobButton.setDisable(true);
        cancelJobButton.setOnAction(e -> cancelDemoJob());

        ToolBar bar = new ToolBar();
        toolbarGroup(bar, TOOLBAR_FILE, false,
                chromeButton("Abrir Gerber", "flatcam_icon32.png", this::openGerberPrototype),
                chromeButton("Abrir Excellon", "drill32.png", this::openExcellonPrototype),
                new Separator(),
                chromeButton("Abrir Projeto", "folder32.png", this::openProject),
                chromeButton("Salvar Projeto", "project_save32.png", this::saveProject));
        toolbarGroup(bar, TOOLBAR_EDIT, true,
                chromeButton("Editor", "edit_file32.png", this::editSelectedObject),
                chromeButton("Salvar e Fechar Editor", "close_edit_file32.png", this::saveAndCloseEditor),
                new Separator(),
                chromeButton("Copiar", "copy_file32.png", this::copySelectedObjects),
                chromeButton("Excluir", "trash32.png", this::deleteSelectedObjects),
                new Separator(),
                chromeButton("Medir Distancia", "distance32.png", null),
                chromeButton("Distancia Minima", "distance_min32.png", null),
                chromeButton("Definir Origem", "origin32.png", null),
                chromeButton("Mover para Origem", "origin2_32.png", null),
                chromeButton("Ir para Localizacao", "jump_to16.png", null),
                chromeButton("Localizar no Objeto", "locate32.png", null));
        toolbarGroup(bar, TOOLBAR_VIEW, true,
                chromeButton("Replotar", "replot32.png", null),
                chromeButton("Limpar Plot", "clear_plot32.png", null),
                chromeButton("Aproximar", "zoom_in32.png", null),
                chromeButton("Afastar", "zoom_out32.png", null),
                chromeButton("Enquadrar Objeto", "zoom_fit32.png", this::focusSelectedObject));
        toolbarGroup(bar, TOOLBAR_SHELL, true,
                chromeButton("Linha de Comando", "shell32.png", null),
                chromeButton("Novo Script", "script_new24.png", null),
                chromeButton("Abrir Script", "open_script32.png", null),
                chromeButton("Executar Script", "script16.png", null));
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
        Menu menu = new Menu("Barras de ferramentas");
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
        consoleToggle.setTooltip(new Tooltip("Mostrar/ocultar console"));
        consoleToggle.getStyleClass().addAll("status-bar-toggle", "status-console");
        consoleToggle.setSelected(!consoleCollapsed);
        consoleToggle.setOnAction(e -> toggleConsole(consoleToggle.isSelected()));

        statusControls = new PlotStatusControls(plotAreaView,
                file -> legacyIcon(file, 16), consoleToggle,
                statusLabel::setText, this::openPreferences);
        unitsLabel.setTooltip(new Tooltip("Unidades do projeto"));
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        cancelJobButton.visibleProperty().bind(cancelJobButton.disableProperty().not());
        cancelJobButton.managedProperty().bind(cancelJobButton.visibleProperty());
        HBox bar = new HBox(6, statusLabel, statusControls.node(), cancelJobButton, unitsLabel, statusDot, activityLabel);
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
        verticalSplit.getDividers().get(0).positionProperty()
                .addListener((obs, oldVal, newVal) -> saveSplitPositions());
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
        projectTab = new Tab("Projeto", buildProjectTree());
        propertiesPlaceholder.setTextAlignment(TextAlignment.CENTER);
        propertiesContainer.getChildren().add(propertiesPlaceholder);
        propertiesTab = new Tab("Propriedades", propertiesContainer);
        toolTab = new Tab("Ferramenta", centeredPlaceholder("Nenhuma ferramenta ativa."));

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
        toolTab.setText("Ferramenta");
        toolTab.setContent(centeredPlaceholder("Nenhuma ferramenta ativa."));
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
        TreeItem<String> root = new TreeItem<>("Projeto");
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

        projectTree = new TreeView<>(root);
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
                        projectTree.edit(selected);
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
                        objectTooltip.setText(pathText + System.lineSeparator()
                                + (isObjectVisible(item) ? "Plot ativo" : "Plot desativado"));
                        objectTooltip.setStyle(currentTheme.objectTooltipStyle());
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
                cncStepLabel.setText(text.isEmpty() ? "ou clique num numero ou linha do plot" : text);
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
            MenuItem fitAll = new MenuItem("Enquadrar tudo");
            setLegacyMenuIcon(fitAll, "zoom_fit32.png");
            fitAll.setOnAction(e -> plotAreaView.fitAllVisible());
            MenuItem clearSelection = new MenuItem("Limpar selecao");
            setLegacyMenuIcon(clearSelection, "deselect_all32.png");
            clearSelection.setOnAction(e -> projectTree.getSelectionModel().clearSelection());
            clearSelection.setDisable(!anyProjectObjectSelected());
            plotContextMenu = new ContextMenu(fitAll, clearSelection);
        }
        if (!plotContextMenu.getItems().isEmpty()) {
            plotContextMenu.getStyleClass().add("plot-context-menu");
            plotContextMenu.setAutoHide(true);
            plotContextMenu.show(plotAreaView, screenX, screenY);
        }
    }

    private void addPlotPlacementActions(ContextMenu menu, TreeItem<String> hit, double anchorX, double anchorY) {
        List<TreeItem<String>> selected = projectTree.getSelectionModel().getSelectedItems().stream()
                .filter(this::isProjectObject).distinct().toList();
        List<TreeItem<String>> targets = selected.contains(hit) ? selected : List.of(hit);
        boolean available = !targets.isEmpty() && targets.stream().allMatch(this::isPlotPlacementTarget);

        MenuItem move = new MenuItem("Mover no Plot Area");
        setLegacyMenuIcon(move, "move32.png");
        move.setDisable(!available);
        move.setOnAction(e -> startPlotPlacement(targets, anchorX, anchorY, false));

        MenuItem copy = new MenuItem("Copiar no Plot Area");
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
            appendConsole("Mover/copiar no Plot Area requer Gerber, Excellon ou Geometry visivel.");
            return;
        }
        boolean started = plotAreaView.beginPlacement(snapshot, anchorX, anchorY, new PlotAreaView.PlacementHandler() {
            @Override
            public void onCommit(double dx, double dy) {
                commitPlotPlacement(snapshot, dx, dy, copy);
            }

            @Override
            public void onCancel() {
                setStatus("Posicionamento cancelado.", CANCELLED_COLOR);
            }
        });
        if (started) {
            setStatus((copy ? "Copiar" : "Mover") + ": clique no destino; Esc cancela.", RUNNING_COLOR);
            plotAreaView.requestFocus();
        }
    }

    private void commitPlotPlacement(List<TreeItem<String>> sources, double dx, double dy, boolean copy) {
        if (!sources.stream().allMatch(this::isPlotPlacementTarget)) {
            appendConsole("Posicionamento cancelado: um objeto de origem mudou ou foi removido.");
            setStatus("Posicionamento cancelado.", CANCELLED_COLOR);
            return;
        }
        if (!copy && dx == 0 && dy == 0) {
            setStatus("Nenhum deslocamento.", IDLE_COLOR);
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
        appendConsole((copy ? "Copiado(s)" : "Movido(s)") + " " + placed.size()
                + " objeto(s) no Plot Area: dx=" + dx + ", dy=" + dy + ".");
        setStatus("Concluido.", IDLE_COLOR);
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
            setStatus("Historico de movimento indisponivel.", CANCELLED_COLOR);
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
        setStatus(forward ? "Movimento refeito." : "Movimento desfeito.", IDLE_COLOR);
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
            appendConsole(title + ": angulo invalido.");
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
            appendConsole("Selecione ao menos um objeto para transformar.");
            return;
        }
        TransformOp op = opFactory.apply(selected);
        int applied = 0;
        for (TreeItem<String> item : selected) {
            if (applyTransformToItem(item, op)) {
                applied++;
            }
        }
        if (applied > 0) {
            appendConsole(applied + " objeto(s) transformado(s).");
            showProperties(projectTree.getSelectionModel().getSelectedItem());
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
                                   String units) {
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
            appendConsole("Fiducials: carregue um Gerber.");
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
                    return "O Gerber foi removido";
                }
                try {
                    GerberImage result = Fiducials.add(source, points, type, size, thickness);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(withoutExtension(item.getValue()) + "_fid"),
                            null, result);
                    appendConsole("Fiduciais adicionados: " + created.getValue() + " (" + points.size() + " pontos).");
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
                    return "O Gerber foi removido";
                }
                try {
                    GerberImage result = Fiducials.add(source, points, Fiducials.Type.CIRCULAR, diameter, 0);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(withoutExtension(item.getValue()) + "_fid"),
                            null, result);
                    appendConsole("Aberturas de mascara adicionadas: " + created.getValue() + ".");
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
            appendConsole("Calibration: carregue um objeto.");
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
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Salvar G-code de verificacao");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
                chooser.setInitialFileName("fc_ver_gcode.nc");
                File file = chooser.showSaveDialog(scene.getWindow());
                if (file == null) {
                    return null;
                }
                try {
                    Files.writeString(file.toPath(), gcode);
                    appendConsole("G-code de verificacao salvo em " + file.getName() + ".");
                    return null;
                } catch (IOException failed) {
                    return "Falha ao salvar: " + failed.getMessage();
                }
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
                        return "O objeto foi removido";
                    }
                    appendConsole("Objeto calibrado: " + created.getValue() + ".");
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
            appendConsole("Copper Thieving: carregue um Gerber.");
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
                    onDone.accept("O Gerber foi removido");
                    return;
                }
                if (runningJob != null) {
                    onDone.accept("Ja existe uma operacao em andamento");
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
                beginJob("Copper Thieving: preenchendo...");
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
                    setStatus("Copper Thieving concluido.", IDLE_COLOR);
                    appendConsole("Copper Thieving: " + thieving.size() + " areas em " + created.getValue() + ".");
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
                    return "O Gerber foi removido";
                }
                try {
                    CopperThieving.Robber robber = CopperThieving.robberBar(source.solidGeometry(), margin, thickness);
                    lastRobber = robber;
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_robber"), null,
                            CopperThieving.withRobber(source, robber));
                    appendConsole("Robber bar adicionada: " + created.getValue() + ".");
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
                    return new Object[] {0.0, "O Gerber foi removido"};
                }
                try {
                    CopperThieving.PlatingMask result = CopperThieving.platingMask(mask, clearance, lastThieving,
                            lastRobber, choice);
                    String stem = withoutExtension(item.getValue());
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(stem + "_plating_mask"), null,
                            result.image());
                    appendConsole(String.format(java.util.Locale.ROOT,
                            "Mascara de galvanoplastia: %s, area galvanizada %.4f.", created.getValue(),
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
            appendConsole("Rules Check: carregue Gerbers ou Excellons.");
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
                    onError.accept("Ja existe uma operacao em andamento");
                    return;
                }
                RulesCheck.Board board = new RulesCheck.Board(named(selection.copperTop()), named(selection.copperBottom()),
                        named(selection.silkTop()), named(selection.silkBottom()), named(selection.maskTop()),
                        named(selection.maskBottom()), named(selection.outline()), namedDrills(selection.drills1()),
                        namedDrills(selection.drills2()));
                beginJob("Rules Check: verificando regras...");
                JobHandle<List<RulesCheck.RuleResult>> handle = jobExecutor.submit(context ->
                        RulesCheck.check(board, settings, context::isCancelled,
                                fraction -> context.reportProgress(fraction, "Verificando regras...")),
                        (fraction, message) -> Platform.runLater(() -> {
                            updateProgress(fraction);
                            statusLabel.setText(message);
                        }));
                runningJob = handle;
                handle.completion().thenAccept(results -> Platform.runLater(() -> {
                    updateProgress(1);
                    setStatus("Rules Check concluido.", IDLE_COLOR);
                    onJobFinished();
                    for (RulesCheck.RuleResult result : results) {
                        appendConsole("Rules Check: " + result.title() + " - " + (!result.ran() ? "nao executada ("
                                + result.error() + ")" : result.failed() ? "FALHOU" : "OK"));
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
            appendConsole("Optimal: carregue um Gerber.");
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
                    onError.accept("O Gerber foi removido");
                    return;
                }
                if (runningJob != null) {
                    onError.accept("Ja existe uma operacao em andamento");
                    return;
                }
                beginJob("Optimal: procurando a menor distancia...");
                JobHandle<MinimumDistance.Result> handle = jobExecutor.submit(context ->
                        MinimumDistance.find(source.solidGeometry(), precision, context::isCancelled,
                                fraction -> context.reportProgress(fraction, "Comparando elementos de cobre...")),
                        (fraction, message) -> Platform.runLater(() -> {
                            updateProgress(fraction);
                            statusLabel.setText(message);
                        }));
                runningJob = handle;
                handle.completion().thenAccept(result -> Platform.runLater(() -> {
                    updateProgress(1);
                    setStatus("Optimal concluido.", IDLE_COLOR);
                    onJobFinished();
                    appendConsole(String.format(java.util.Locale.ROOT,
                            "Optimal: menor distancia %." + precision + "f (%d par(es)) entre %d elementos de cobre.",
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
            appendConsole("Align Objects: carregue ao menos dois objetos (Gerber ou Excellon).");
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
                        return "Nao foi possivel transformar " + aligned.getValue();
                    }
                    appendConsole("Objeto alinhado: " + aligned.getValue() + " ("
                            + (operations.size() == 1 ? "translacao" : "translacao e rotacao") + ").");
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
            appendConsole("SolderPaste: carregue o Gerber da mascara de pasta.");
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
                    return "O Gerber foi removido ou esta vazio";
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
                    appendConsole("Geometria de pasta criada: " + created.getValue() + " ("
                            + (generated.pads() - generated.unserved()) + " de " + generated.pads() + " pads).");
                    if (generated.unserved() > 0) {
                        appendConsole("Aviso: " + generated.unserved() + " pad(s) nao cabem em nenhum bico da tabela.");
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
                    return "A geometria foi removida ou nao e uma geometria de pasta";
                }
                try {
                    Envelope box = entry.geometry().getEnvelopeInternal();
                    SolderPaste.Program program = SolderPaste.generateGCode(entry.tools(), parameters, entry.units(),
                            new double[] {box.getMinX(), box.getMinY(), box.getMaxX(), box.getMaxY()});
                    File destination = chooseExportFile("Salvar G-code de pasta", item, "_cnc_solderpaste.nc",
                            new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.ngc"));
                    if (destination == null) {
                        return null;
                    }
                    Files.writeString(destination.toPath(), program.gcode());
                    AppPreferences.saveLastCamDirectory(destination.getParentFile().getAbsolutePath());
                    org.locationtech.jts.geom.GeometryFactory factory = entry.geometry().getFactory();
                    List<Geometry> travel = new ArrayList<>();
                    org.locationtech.jts.geom.Coordinate previous = new org.locationtech.jts.geom.Coordinate(0, 0);
                    for (org.locationtech.jts.geom.LineString path : program.paths()) {
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
                        bodies.add(tool.geometry().buffer(tool.toolDiameter() / 2, 8));
                    }
                    Geometry cutBodies = factory.buildGeometry(bodies);
                    TreeItem<String> created = addCncJobToProject(destination.getName(), item.getValue(),
                            destination.toPath(), program.gcode(), travelLines, cutBodies, travelLines, cutLines,
                            thinnest);
                    appendConsole("G-code de pasta salvo em " + destination + " (" + program.gcode().lines().count()
                            + " linhas, " + program.paths().size() + " caminhos).");
                    selectProjectItem(created);
                    return null;
                } catch (IllegalArgumentException failed) {
                    return failed.getMessage();
                } catch (java.io.IOException failed) {
                    return "Nao foi possivel salvar o G-code: " + failed.getMessage();
                }
            }
        }, this::closeToolPanel));
    }

    /** Tools > QRCode Tool: a QR code of copper squares on a Gerber (appTools/ToolQRCode.py). */
    private void openQrCodeTool() {
        List<TreeItem<String>> gerbers = new ArrayList<>(gerbersNode.getChildren());
        gerbers.removeIf(item -> !gerberByItem.containsKey(item));
        if (gerbers.isEmpty()) {
            appendConsole("QRCode: carregue um Gerber.");
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
                    return "O Gerber foi removido";
                }
                try {
                    GerberImage result = QrCodeMarker.place(source, options, centre);
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_qrcode"), null, result);
                    appendConsole("QR Code adicionado: " + created.getValue() + ".");
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
            appendConsole("Corner Markers: carregue um Gerber.");
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
                    return "O Gerber foi removido ou esta vazio";
                }
                try {
                    GerberImage result = CornerMarkers.add(source, bounds, corners, style, thickness, length, margin);
                    TreeItem<String> created = addGerberToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_corners"), null, result);
                    appendConsole("Marcadores de canto adicionados: " + created.getValue() + ".");
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
                    return "O Gerber foi removido ou esta vazio";
                }
                try {
                    ExcellonImage drills = CornerMarkers.drills(source.units(), bounds, corners, thickness, margin,
                            diameter);
                    TreeItem<String> created = addExcellonToProject(
                            uniqueDerivedName(withoutExtension(item.getValue()) + "_corner_drills"), null, drills);
                    appendConsole("Furos de canto criados: " + created.getValue() + ".");
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
            appendConsole("Film: carregue um Gerber ou Geometry.");
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
                    return "Ja existe uma operacao em andamento";
                }
                org.locationtech.jts.geom.Geometry filmGeometry = filmGeometryOf(request.film());
                org.locationtech.jts.geom.Geometry boxGeometry = filmGeometryOf(request.box());
                if (filmGeometry == null || boxGeometry == null) {
                    return "Um dos objetos foi removido";
                }
                GerberImage punched = null;
                try {
                    if (request.punch() != FilmToolPanel.Punch.NONE) {
                        GerberImage source = gerberByItem.get(request.film());
                        if (source == null) {
                            return "Furar so vale para Gerbers";
                        }
                        Set<String> codes = new java.util.HashSet<>(source.apertures().keySet());
                        if (request.punch() == FilmToolPanel.Punch.EXCELLON) {
                            ExcellonImage drills = excellonByItem.get(request.excellon());
                            if (drills == null) {
                                return "O Excellon foi removido";
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
                File destination = chooseExportFile("Exportar filme " + kind, request.film(), "_film" + extension,
                        new FileChooser.ExtensionFilter(kind, "*" + extension));
                if (destination == null) {
                    return null;
                }
                String units = unitsOf(request.film());
                org.locationtech.jts.geom.Geometry finalFilm = filmGeometry;
                runExport(request.film().getValue(), "Filme " + kind, destination,
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
            appendConsole("Etch Compensation: carregue um Gerber.");
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
                    return "O Gerber foi removido";
                }
                try {
                    GerberImage result = EtchCompensation.compensate(source, offset);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(item.getValue() + "_comp"), null,
                            result);
                    appendConsole("Gerber compensado criado: " + created.getValue() + " (deslocamento "
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
            appendConsole("Punch Gerber: carregue um Gerber.");
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
                    return "O Gerber foi removido";
                }
                try {
                    GerberImage punched;
                    if (excellon != null) {
                        ExcellonImage drills = excellonByItem.get(excellon);
                        if (drills == null) {
                            return "O Excellon foi removido";
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
                    appendConsole("Gerber furado criado: " + created.getValue() + " ("
                            + (punched.shapes().size() - source.shapes().size()) + " furos).");
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
            appendConsole("Extract Drills: carregue um Gerber.");
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
                    return "O Gerber foi removido";
                }
                try {
                    ExcellonImage drills = ExtractDrills.extract(source, options);
                    String base = item.getValue();
                    int dot = base.lastIndexOf('.');
                    if (dot > 0) {
                        base = base.substring(0, dot);
                    }
                    TreeItem<String> created = addExcellonToProject(uniqueDerivedName(base + "_drills"), null, drills);
                    appendConsole("Furos extraidos: " + created.getValue() + " (" + drills.totalDrills()
                            + " furos, " + drills.toolDiameters().size() + " ferramentas).");
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
            appendConsole("Subtract: carregue Gerbers ou Geometrys.");
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
                    return "Um dos objetos foi removido";
                }
                if (!targetImage.units().equals(subtractorImage.units())) {
                    return "Os Gerbers tem unidades diferentes; converta um deles antes";
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
                    return "Um dos objetos foi removido";
                }
                if (!subtractorEntry.tools().isEmpty()) {
                    return "No momento o subtraendo nao pode ser um Geometry multi-ferramenta";
                }
                if (!targetEntry.units().equals(subtractorEntry.units())) {
                    return "Os Geometrys tem unidades diferentes; converta um deles antes";
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
        appendConsole("Subtracao criada: " + created.getValue() + ".");
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
            appendConsole("Invert Gerber: carregue um Gerber.");
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
                    return "O Gerber foi removido";
                }
                try {
                    GerberImage inverted = InvertGerber.invert(source, margin, style);
                    TreeItem<String> created = addGerberToProject(uniqueDerivedName(item.getValue() + "_inverted"), null,
                            inverted);
                    appendConsole("Gerber invertido criado: " + created.getValue() + " ("
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
            appendConsole("Panelize: carregue um Gerber, um Excellon ou um Geometry.");
            return;
        }
        TreeItem<String> initial = selectedObjects().stream().filter(sources::contains).findFirst().orElse(null);
        releaseActiveTool();
        openToolPanel("Panelize Tool", PanelizeToolPanel.build(new PanelizeToolPanel.Host() {
            @Override
            public List<TreeItem<String>> sources() {
                return sources;
            }

            @Override
            public TreeItem<String> initialSource() {
                return initial;
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
            public void preview(Geometry outlines) {
                plotAreaView.setEditorHighlight(outlines, true);
            }

            @Override
            public void panelize(PanelizeToolPanel.Request request) {
                runPanelize(request);
            }
        }, () -> {
            plotAreaView.setEditorHighlight(null, false);
            closeToolPanel();
        }));
        activeToolCleanup = this::clearToolOverlays;
    }

    private void runPanelize(PanelizeToolPanel.Request request) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        TreeItem<String> item = request.source();
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        beginJob("Criando painel...");
        JobHandle<PanelizeOutcome> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.1, "Copiando " + item.getValue() + "...");
            if (gerber != null) {
                GerberImage panel = Panelize.gerber(gerber, request.layout());
                return new PanelizeOutcome(request.gerberAsGeometry() ? null : panel, null,
                        request.gerberAsGeometry() ? new GeometryJoin.Joined(panel.solidGeometry(), false, List.of())
                                : null, gerber.units());
            }
            if (excellon != null) {
                return new PanelizeOutcome(null, Panelize.excellon(excellon, request.layout()), null, excellon.units());
            }
            return new PanelizeOutcome(null, null, Panelize.geometry(geometry.units(), geometry.geometry(),
                    geometry.strokeOnly(), geometry.tools(), request.layout()), geometry.units());
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    String name = uniqueDerivedName(item.getValue() + "_panelized");
                    TreeItem<String> created;
                    if (outcome.gerber() != null) {
                        created = addGerberToProject(name, null, outcome.gerber());
                    } else if (outcome.excellon() != null) {
                        created = addExcellonToProject(name, null, outcome.excellon());
                    } else {
                        GeometryJoin.Joined joined = outcome.geometry();
                        created = addGeometryToProject(name, item.getValue(), outcome.units(), joined.geometry(),
                                joined.strokeOnly(), joined.tools());
                    }
                    Panelize.Layout layout = request.layout();
                    appendConsole("Painel criado: " + created.getValue() + " (" + layout.columns() + " x "
                            + layout.rows() + " copias" + (layout.constrained() ? ", reduzido pelo limite" : "") + ").");
                    selectProjectItem(created);
                    plotAreaView.fitToLayer(created);
                    plotAreaView.setEditorHighlight(null, false);
                    closeToolPanel();
                    setStatus("Concluido.", IDLE_COLOR);
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao criar o painel: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Tools > Paint Tool: fills Gerber or Geometry polygons with toolpaths (appTools/ToolPaint.py). */
    private void openPaintTool() {
        List<TreeItem<String>> sources = new ArrayList<>();
        sources.addAll(gerbersNode.getChildren());
        sources.addAll(geometryNode.getChildren());
        sources.removeIf(item -> !gerberByItem.containsKey(item) && !geometryByItem.containsKey(item));
        if (sources.isEmpty()) {
            appendConsole("Paint: carregue um Gerber ou um Geometry.");
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
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        beginJob("Pintando poligonos...");
        JobHandle<NccResult> handle = jobExecutor.submit(context -> NccGenerator.paint(units, polygons, params,
                context::isCancelled, fraction -> context.reportProgress(fraction, "Pintando poligonos...")),
                (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;
        handle.completion()
                .thenAccept(result -> Platform.runLater(() -> {
                    if (result.isEmpty()) {
                        appendConsole("Paint nao gerou caminhos. A ferramenta pode ser grande demais para os poligonos.");
                        setStatus("Sem caminhos.", ERROR_COLOR);
                    } else {
                        List<ToolGeometry> tools = result.toolResults().stream()
                                .filter(toolResult -> !toolResult.isEmpty())
                                .map(toolResult -> new ToolGeometry(toolResult.toolDiameter(), toolResult.geometry(),
                                        ToolProfile.C1))
                                .toList();
                        TreeItem<String> generated = addGeometryToProject(uniqueDerivedName(item.getValue() + "_paint"),
                                item.getValue(), units, result.geometry(), true, tools);
                        appendConsole(String.format(java.util.Locale.ROOT,
                                "Paint: %d caminhos, comprimento total=%.4f, %d ferramenta(s), falhas=%d.",
                                result.pathCount(), result.totalLength(), tools.size(),
                                result.totalFailedPolygonCount()));
                        selectProjectItem(generated);
                        plotAreaView.fitToLayer(generated);
                        plotAreaView.setEditorHighlight(null, false);
                        closeToolPanel();
                        setStatus("Concluido.", IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao pintar: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Edit > Tools > 2-Sided Tool: mirror objects and make alignment holes for a double-sided board. */
    private void openDoubleSidedTool() {
        releaseActiveTool();
        openToolPanel("2-Sided Tool", DoubleSidedToolPanel.build(new DoubleSidedToolPanel.Host() {
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
                appendConsole(done + " objeto(s) " + (asCopy ? "copiado(s) e espelhado(s)." : "espelhado(s)."));
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
                appendConsole("Excellon com furos de alinhamento criado (" + drills.size() + " furos).");
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
                this::selectionCenterOrOrigin, this::applyTransformToSelection, this::closeToolPanel));
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
        Button scaleButton = new Button("Scale");
        scaleButton.setOnAction(e -> {
            try {
                double factor = Double.parseDouble(scaleField.getText().trim().replace(',', '.'));
                if (applyTransformToItem(item, new TransformOp.Scale(factor, factor, TransformReference.origin()))) {
                    showProperties(item);
                }
            } catch (NumberFormatException ex) {
                appendConsole("Scale: numero invalido.");
            }
        });
        TextField offsetXField = new TextField("0.0");
        TextField offsetYField = new TextField("0.0");
        offsetXField.setPrefColumnCount(6);
        offsetYField.setPrefColumnCount(6);
        offsetXField.setMinWidth(0);
        offsetYField.setMinWidth(0);
        Button offsetButton = new Button("Offset");
        offsetButton.setOnAction(e -> {
            try {
                double dx = Double.parseDouble(offsetXField.getText().trim().replace(',', '.'));
                double dy = Double.parseDouble(offsetYField.getText().trim().replace(',', '.'));
                if (applyTransformToItem(item, new TransformOp.Offset(dx, dy))) {
                    showProperties(item);
                }
            } catch (NumberFormatException ex) {
                appendConsole("Offset: numero invalido.");
            }
        });
        Button transformationsButton = new Button("Transformations");
        transformationsButton.setGraphic(legacyIcon("transform.png", 18));
        transformationsButton.setMaxWidth(Double.MAX_VALUE);
        transformationsButton.setOnAction(e -> openTransformTool());

        FlowPane scaleRow = new FlowPane(8, 6, new HBox(6, new Label("Scale:"), scaleField), scaleButton);
        FlowPane offsetRow = new FlowPane(8, 6,
                new HBox(6, new Label("Offset X:"), offsetXField),
                new HBox(6, new Label("Y:"), offsetYField), offsetButton);
        return new VBox(6, scaleRow, offsetRow, transformationsButton);
    }

    /** One object's share of {@link #applyTransformToSelection} - also used directly by each object's own mini "Transformations" panel. */
    private boolean applyTransformToItem(TreeItem<String> item, TransformOp op) {
        if (!applyingPlotMove) {
            plotMoveHistory.clear();
        }
        if (cncJobByItem.containsKey(item)) {
            appendConsole("CNC Job nao pode ser transformado: " + item.getValue());
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
                    op.apply(geometry.geometry()), geometry.strokeOnly(), newTools, geometry.cncDefaults());
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
        long plottable = selected.stream().filter(this::isPlottable).count();

        MenuItem enableItem = new MenuItem("Ativar Plot (" + plottable + ")");
        setLegacyMenuIcon(enableItem, "replot32.png");
        enableItem.setDisable(plottable == 0);
        enableItem.setOnAction(e -> selected.stream().filter(this::isPlottable)
                .forEach(i -> setObjectVisible(i, true)));

        MenuItem disableItem = new MenuItem("Desativar Plot (" + plottable + ")");
        setLegacyMenuIcon(disableItem, "clear_plot32.png");
        disableItem.setDisable(plottable == 0);
        disableItem.setOnAction(e -> selected.stream().filter(this::isPlottable)
                .forEach(i -> setObjectVisible(i, false)));

        MenuItem removeItem = new MenuItem("Remover (" + selected.size() + ")");
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeSelectionFromProject(selected));

        MenuItem copyItem = new MenuItem("Copiar (" + selected.size() + ")");
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copySelection(selected));

        return List.of(enableItem, disableItem, new SeparatorMenuItem(), copyItem, removeItem);
    }

    /** True for a Gerber/Excellon, or a CNC Job that actually has toolpath geometry to show (see CncJobEntry's doc). */
    private boolean isPlottable(TreeItem<String> item) {
        if (gerberByItem.containsKey(item) || excellonByItem.containsKey(item) || geometryByItem.containsKey(item)) {
            return true;
        }
        CncJobEntry entry = cncJobByItem.get(item);
        return entry != null && (entry.travelGeometry() != null || entry.cutGeometry() != null);
    }

    /**
     * A CNC Job's visibility spans its two sub-layers (travel + cut, see
     * {@link #addCncJobToProject}) toggled together as one unit - everywhere
     * else, an object's tree item is itself the PlotAreaView layer key.
     */
    private boolean isObjectVisible(TreeItem<String> item) {
        if (cncJobByItem.containsKey(item)) {
            return plotAreaView.isLayerVisible(new CncTravelLayerKey(item)) || plotAreaView.isLayerVisible(new CncCutLayerKey(item));
        }
        return plotAreaView.isLayerVisible(item);
    }

    private void setObjectVisible(TreeItem<String> item, boolean visible) {
        if (cncJobByItem.containsKey(item)) {
            plotAreaView.setLayerVisible(new CncTravelLayerKey(item), visible);
            plotAreaView.setLayerVisible(new CncCutLayerKey(item), visible);
            refreshCncAnnotations(item);
        } else {
            plotAreaView.setLayerVisible(item, visible);
        }
        // The tree doesn't otherwise know PlotAreaView's layer visibility changed - see
        // refreshDisplay()'s dimming of disabled rows (ObjectCollection.py's own
        // data()/Qt.ForegroundRole, which reads obj.options['plot'] the same way).
        projectTree.refresh();
        refreshPlotSelectionOutline();
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
     * Enable/Disable Plot are two separate, always-present items - not one
     * dynamic toggle - matching appGUI/MainGUI.py's actual menuproject
     * (menuprojectenable/menuprojectdisable are both always in the menu;
     * Python doesn't hide/rename one based on current state either). Set
     */
    private List<MenuItem> gerberContextMenuItems(TreeItem<String> item, GerberImage image) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem enableItem = new MenuItem("Ativar Plot");
        setLegacyMenuIcon(enableItem, "replot32.png");
        enableItem.setOnAction(e -> setObjectVisible(item, true));
        MenuItem disableItem = new MenuItem("Desativar Plot");
        setLegacyMenuIcon(disableItem, "clear_plot32.png");
        disableItem.setOnAction(e -> setObjectVisible(item, false));

        Menu colorMenu = buildLayerColorMenu(item, GERBER_FILL, GERBER_STROKE);

        MenuItem editItem = new MenuItem("Editar");
        setLegacyMenuIcon(editItem, "edit_ok32.png");
        editItem.setOnAction(e -> gerberEditor.start(item, gerberByItem.getOrDefault(item, image)));

        MenuItem isolationItem = new MenuItem("Gerar Geometry de Isolamento...");
        setLegacyMenuIcon(isolationItem, "iso_16.png");
        isolationItem.setOnAction(e -> generateIsolation(item, image));

        MenuItem cutoutItem = new MenuItem("Cutout Tool...");
        setLegacyMenuIcon(cutoutItem, "cut32_bis.png");
        cutoutItem.setOnAction(e -> generateCutout(item, image));

        Menu createGeometryMenu = new Menu("Criar Geometry");
        setLegacyMenuIcon(createGeometryMenu, "geometry32.png");
        createGeometryMenu.getItems().addAll(isolationItem, cutoutItem);

        MenuItem viewSourceItem = new MenuItem("Ver Fonte");
        setLegacyMenuIcon(viewSourceItem, "source32.png");
        viewSourceItem.setOnAction(e -> viewObjectSource(item));

        MenuItem renameItem = new MenuItem("Renomear");
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));

        MenuItem copyItem = new MenuItem("Copiar");
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));

        MenuItem removeItem = new MenuItem("Remover");
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, gerberByItem));

        MenuItem saveItem = new MenuItem("Salvar como...");
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));

        MenuItem propertiesItem = new MenuItem("Propriedades");
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return List.of(showItem, enableItem, disableItem, new SeparatorMenuItem(), colorMenu,
                new SeparatorMenuItem(), editItem, createGeometryMenu, viewSourceItem, renameItem, copyItem, removeItem,
                saveItem, new SeparatorMenuItem(), propertiesItem);
    }

    /** Same legacy project-menu shape as Gerber, with Excellon's drilling CNC workflow. */
    private List<MenuItem> excellonContextMenuItems(TreeItem<String> item, ExcellonImage image) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem enableItem = new MenuItem("Ativar Plot");
        setLegacyMenuIcon(enableItem, "replot32.png");
        enableItem.setOnAction(e -> setObjectVisible(item, true));
        MenuItem disableItem = new MenuItem("Desativar Plot");
        setLegacyMenuIcon(disableItem, "clear_plot32.png");
        disableItem.setOnAction(e -> setObjectVisible(item, false));

        Menu colorMenu = buildLayerColorMenu(item, DRILL_FILL, DRILL_STROKE);

        MenuItem editItem = new MenuItem("Editar Excellon");
        setLegacyMenuIcon(editItem, "edit_file32.png");
        editItem.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedExcellon();
        });

        MenuItem gcodeItem = new MenuItem("Criar CNC Job...");
        setLegacyMenuIcon(gcodeItem, "cnc32.png");
        gcodeItem.setOnAction(e -> generateDrillGCode(item, image));

        MenuItem viewSourceItem = new MenuItem("Ver Fonte");
        setLegacyMenuIcon(viewSourceItem, "source32.png");
        viewSourceItem.setOnAction(e -> viewObjectSource(item));

        MenuItem renameItem = new MenuItem("Renomear");
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));

        MenuItem copyItem = new MenuItem("Copiar");
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));

        MenuItem removeItem = new MenuItem("Remover");
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, excellonByItem));

        MenuItem saveItem = new MenuItem("Salvar como...");
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));

        MenuItem propertiesItem = new MenuItem("Propriedades");
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return List.of(showItem, enableItem, disableItem, new SeparatorMenuItem(), colorMenu,
                new SeparatorMenuItem(), editItem, gcodeItem, viewSourceItem, renameItem, copyItem, removeItem, saveItem,
                new SeparatorMenuItem(), propertiesItem);
    }

    /** Project-tree actions for Gerber-derived Geometry objects. */
    private List<MenuItem> geometryContextMenuItems(TreeItem<String> item) {
        GeometryEntry entry = geometryByItem.get(item);
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem enableItem = new MenuItem("Ativar Plot");
        setLegacyMenuIcon(enableItem, "replot32.png");
        enableItem.setOnAction(e -> setObjectVisible(item, true));
        MenuItem disableItem = new MenuItem("Desativar Plot");
        setLegacyMenuIcon(disableItem, "clear_plot32.png");
        disableItem.setOnAction(e -> setObjectVisible(item, false));

        Menu colorMenu = buildLayerColorMenu(item, GEOMETRY_FILL, GEOMETRY_STROKE);

        MenuItem cncItem = new MenuItem("Criar CNC Job...");
        setLegacyMenuIcon(cncItem, "cnc32.png");
        cncItem.setOnAction(e -> generateGeometryCncJob(item, entry));

        MenuItem nccItem = new MenuItem("Gerar NCC...");
        setLegacyMenuIcon(nccItem, "ncc16.png");
        nccItem.setOnAction(e -> generateNcc(item, entry));

        MenuItem editItem = new MenuItem("Editar Geometry");
        setLegacyMenuIcon(editItem, "edit_file32.png");
        editItem.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGeometry();
        });

        MenuItem viewItem = new MenuItem("Ver WKT");
        setLegacyMenuIcon(viewItem, "source32.png");
        viewItem.setOnAction(e -> viewObjectSource(item));
        MenuItem renameItem = new MenuItem("Renomear");
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));
        MenuItem copyItem = new MenuItem("Copiar");
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));
        MenuItem removeItem = new MenuItem("Remover");
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, geometryByItem));
        MenuItem saveItem = new MenuItem("Salvar WKT como...");
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));
        MenuItem propertiesItem = new MenuItem("Propriedades");
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return List.of(showItem, enableItem, disableItem, new SeparatorMenuItem(), colorMenu,
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
        Menu menu = new Menu("Definir Cor");
        setLegacyMenuIcon(menu, "set_color32.png");
        // Exact RGB values from app_Main.py:on_set_color_action_triggered().
        addColorPreset(menu, item, "Vermelho", Color.web("#FF0000"));
        addColorPreset(menu, item, "Azul", Color.web("#0000FF"));
        addColorPreset(menu, item, "Amarelo", Color.web("#FFDF00"));
        addColorPreset(menu, item, "Verde", Color.web("#00FF00"));
        addColorPreset(menu, item, "Roxo", Color.web("#FF00FF"));
        addColorPreset(menu, item, "Marrom", Color.web("#A52A2A"));
        addColorPreset(menu, item, "Branco", Color.WHITE);
        addColorPreset(menu, item, "Preto", Color.BLACK);

        MenuItem customItem = new MenuItem("Personalizada...");
        setLegacyMenuIcon(customItem, "set_color32.png");
        customItem.setOnAction(e -> editLayerColor(item));
        MenuItem opacityItem = new MenuItem("Opacidade...");
        setLegacyMenuIcon(opacityItem, "set_color32.png");
        opacityItem.setOnAction(e -> editLayerOpacity(item));
        MenuItem defaultItem = new MenuItem("Padrao");
        defaultItem.setGraphic(colorSwatch(defaultFill));
        defaultItem.setOnAction(e -> plotAreaView.setLayerColors(item, defaultFill, defaultStroke));
        menu.getItems().addAll(new SeparatorMenuItem(), customItem, new SeparatorMenuItem(), opacityItem, defaultItem);
        return menu;
    }

    private void addColorPreset(Menu menu, TreeItem<String> item, String label, Color color) {
        MenuItem colorItem = new MenuItem(label);
        colorItem.setGraphic(colorSwatch(color));
        colorItem.setOnAction(e -> {
            Color fill = colorWithOpacity(color, defaultObjectOpacity(item));
            plotAreaView.setLayerColors(item, fill, legacyOutlineColor(color));
        });
        menu.getItems().add(colorItem);
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

        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        setLegacyMenuIcon(showItem, "zoom_fit32.png");
        showItem.setDisable(!plottable);
        showItem.setOnAction(e -> focusCncJob(item, entry));

        MenuItem enableItem = new MenuItem("Ativar Plot");
        setLegacyMenuIcon(enableItem, "replot32.png");
        enableItem.setDisable(!plottable);
        enableItem.setOnAction(e -> setObjectVisible(item, true));
        MenuItem disableItem = new MenuItem("Desativar Plot");
        setLegacyMenuIcon(disableItem, "clear_plot32.png");
        disableItem.setDisable(!plottable);
        disableItem.setOnAction(e -> setObjectVisible(item, false));

        MenuItem viewItem = new MenuItem("Ver G-code");
        setLegacyMenuIcon(viewItem, "source32.png");
        viewItem.setOnAction(e -> viewObjectSource(item));

        MenuItem editItem = new MenuItem("Editar G-code");
        setLegacyMenuIcon(editItem, "code_editor32.png");
        editItem.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGCode();
        });

        MenuItem renameItem = new MenuItem("Renomear");
        setLegacyMenuIcon(renameItem, "edit16.png");
        renameItem.setOnAction(e -> beginRename(item));

        MenuItem copyItem = new MenuItem("Copiar");
        setLegacyMenuIcon(copyItem, "copy32.png");
        copyItem.setOnAction(e -> copyObject(item));

        MenuItem removeItem = new MenuItem("Remover");
        setLegacyMenuIcon(removeItem, "delete32.png");
        removeItem.setOnAction(e -> removeFromProject(item, cncJobByItem));

        MenuItem saveItem = new MenuItem("Salvar como...");
        setLegacyMenuIcon(saveItem, "save_as.png");
        saveItem.setOnAction(e -> saveObjectAs(item));

        MenuItem propertiesItem = new MenuItem("Propriedades");
        setLegacyMenuIcon(propertiesItem, "properties32.png");
        propertiesItem.setOnAction(e -> showObjectProperties(item));

        return List.of(showItem, enableItem, disableItem, new SeparatorMenuItem(), editItem, viewItem, renameItem, copyItem,
                removeItem, saveItem, new SeparatorMenuItem(), propertiesItem);
    }

    private void beginRename(TreeItem<String> item) {
        int row = projectTree.getRow(item);
        if (row >= 0) {
            projectTree.getSelectionModel().clearAndSelect(row);
            Platform.runLater(() -> projectTree.edit(item));
        }
    }

    private boolean renameProjectItem(TreeItem<String> item, String requestedName) {
        String newName = requestedName == null ? "" : requestedName.trim();
        if (newName.isEmpty()) {
            appendConsole("O nome do objeto nao pode ficar vazio.");
            projectTree.refresh();
            return false;
        }
        boolean duplicate = !newName.equals(item.getValue()) && projectObjectNameExists(newName);
        if (duplicate) {
            appendConsole("Ja existe um objeto chamado " + newName + ".");
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
            source = geometry.geometry().toText();
        } else if (cncJob != null) {
            source = cncJob.gcode();
        } else {
            Path path = sourcePathByItem.get(item);
            if (path == null) {
                appendConsole("Fonte indisponivel para " + item.getValue() + ".");
                return;
            }
            try {
                source = Files.readString(path);
            } catch (IOException e) {
                appendConsole("Falha ao ler a fonte " + path + ": " + e.getMessage());
                setStatus("Falhou.", ERROR_COLOR);
                return;
            }
        }
        String tabTitle = "Fonte - " + item.getValue();
        openAuxiliaryTab(tabTitle, () -> buildGCodeViewer(source));
    }

    private void saveObjectAs(TreeItem<String> item) {
        CncJobEntry cncJob = cncJobByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        Path sourcePath = sourcePathByItem.get(item);
        if (cncJob == null && geometry == null && gerber == null && excellon == null && sourcePath == null) {
            appendConsole("Nao ha conteudo exportavel para " + item.getValue() + ".");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar objeto como");
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
                    new FileChooser.ExtensionFilter("Programas CNC", "*.nc", "*.gcode", "*.tap", "*.imf", "*.plt", "*.hpgl", "*.hpg", "*.rml", "*.prn"));
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
                new GerberExporter().write(gerber, target);
            } else if (excellon != null) {
                new ExcellonExporter().write(excellon, target);
            } else if (!sourcePath.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
            }
            File parent = destination.getParentFile();
            if (parent != null) {
                AppPreferences.saveLastCamDirectory(parent.getAbsolutePath());
            }
            appendConsole("Objeto salvo em " + target);
            setStatus("Concluido.", IDLE_COLOR);
        } catch (IOException | IllegalArgumentException e) {
            appendConsole("Falha ao salvar " + target + ": " + e.getMessage());
            setStatus("Falhou.", ERROR_COLOR);
        }
    }

    /**
     * File > Exportar > Gerber / Excellon - app_Main.py's on_file_exportgerber /
     * on_file_exportexcellon: only the matching object type, in the coordinate
     * format asked for by CamExportDialog.
     */
    private void exportSelectedCam(boolean asGerber) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        GerberImage gerber = item == null ? null : gerberByItem.get(item);
        ExcellonImage excellon = item == null ? null : excellonByItem.get(item);
        if (!isProjectObject(item)) {
            appendConsole("Nenhum objeto selecionado.");
            return;
        }
        if (asGerber ? gerber == null : excellon == null) {
            appendConsole(asGerber ? "Falhou. Somente objetos Gerber podem ser exportados como Gerber."
                    : "Falhou. Somente objetos Excellon podem ser exportados como Excellon.");
            setStatus("Falhou.", ERROR_COLOR);
            return;
        }
        if (asGerber) {
            Optional<GerberExporter.Format> format = CamExportDialog.askGerberFormat(scene.getWindow(), gerber.units());
            File destination = format.isEmpty() ? null : chooseExportFile("Exportar Gerber", item, ".gbr",
                    new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.gtl", "*.gbl", "*.gm1", "*.cmp", "*.txt"));
            if (destination != null) {
                runExport(item.getValue(), "Arquivo Gerber", destination,
                        path -> new GerberExporter().write(gerber, format.get(), path));
            }
        } else {
            Optional<ExcellonExporter.Format> format =
                    CamExportDialog.askExcellonFormat(scene.getWindow(), excellon.units());
            File destination = format.isEmpty() ? null : chooseExportFile("Exportar Excellon", item, ".drl",
                    new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
            if (destination != null) {
                runExport(item.getValue(), "Arquivo Excellon", destination,
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
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (!isProjectObject(item)) {
            appendConsole("Nenhum objeto selecionado.");
            return;
        }
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        CncJobEntry cncJob = cncJobByItem.get(item);
        File destination = chooseExportFile("Exportar SVG", item, ".svg",
                new FileChooser.ExtensionFilter("SVG", "*.svg"));
        if (destination == null) {
            return;
        }
        runExport(item.getValue(), "Arquivo SVG", destination, path -> {
            List<SvgExporter.Layer> layers;
            String units;
            if (cncJob != null) {
                GCodeToolpathParser.Result parsed = GCodeToolpathParser.parse(cncJob.gcode(),
                        CancellationToken.none(), fraction -> { });
                if (!parsed.plotAvailable()) {
                    throw new IllegalArgumentException("o G-code nao pode ser plotado");
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

    /**
     * File > Exportar > DXF - app_Main.py's on_file_exportdxf. Python accepts only
     * Geometry objects; the outlines of Gerber and Excellon objects are allowed too.
     */
    private void exportSelectedDxf() {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        TreeItem<String> item = projectTree.getSelectionModel().getSelectedItem();
        if (!isProjectObject(item)) {
            appendConsole("Nenhum objeto selecionado.");
            return;
        }
        GerberImage gerber = gerberByItem.get(item);
        ExcellonImage excellon = excellonByItem.get(item);
        GeometryEntry geometry = geometryByItem.get(item);
        if (geometry == null && gerber == null && excellon == null) {
            appendConsole("Somente objetos Geometry, Gerber e Excellon podem ser usados.");
            setStatus("Falhou.", ERROR_COLOR);
            return;
        }
        File destination = chooseExportFile("Exportar DXF", item, ".dxf",
                new FileChooser.ExtensionFilter("DXF", "*.dxf"));
        if (destination == null) {
            return;
        }
        Geometry shapes = geometry != null ? geometry.geometry()
                : gerber != null ? gerber.solidGeometry() : excellon.solidGeometry();
        String units = geometry != null ? geometry.units() : gerber != null ? gerber.units() : excellon.units();
        runExport(item.getValue(), "Arquivo DXF", destination, path -> new DxfExporter().write(shapes, units, path));
    }

    /** File > Exportar > PNG - app_Main.py's on_file_exportpng; no object needs to be selected. */
    private void exportPlotPng() {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar imagem PNG");
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
        runExport("a area de plotagem", "Imagem PNG", destination, path -> PlotPngExporter.write(capture, path));
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
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        File destination = chooseExportFile("Exportar sequencia do trajeto (CSV)", item, "_sequencia.csv",
                new FileChooser.ExtensionFilter("CSV", "*.csv"));
        if (destination == null) {
            return;
        }
        List<GCodeToolpathParser.PathStep> steps = stats.steps();
        String units = stats.units();
        runExport(item.getValue(), "Sequencia do trajeto (CSV)", destination,
                path -> CncStepCsv.write(steps, units, path));
    }

    /** Writes an export off the JavaFX thread; the writers publish atomically, so there is no cancel point. */
    private void runExport(String subject, String fileKind, File destination, ExportWriter writer) {
        beginJob("Exportando " + subject + "...");
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
            appendConsole(fileKind + " exportado para " + path);
            updateProgress(1);
            setStatus("Exportado.", IDLE_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Nao foi possivel exportar: ");
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
            if (gerberFollowItems.contains(sourceItem)) {
                gerberFollowItems.add(copyItem);
                plotAreaView.putLayer(copyItem, PlotAreaView.LayerCategory.GERBER,
                        gerber.followGeometry(), GERBER_FILL, GERBER_STROKE, true);
            }
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
            if (geometryCncSettingsByItem.containsKey(sourceItem))
                geometryCncSettingsByItem.put(copyItem, geometryCncSettingsByItem.get(sourceItem));
            copyLayerAppearance(sourceItem, copyItem);
        } else if (cncJob != null) {
            copyItem = addCncJobToProject(copyName, cncJob.sourceName(), cncJob.outputFile(), cncJob.gcode(),
                    cncJob.travelGeometry(), cncJob.cutGeometry(), cncJob.travelCenterlines(),
                    cncJob.cutCenterlines(), cncJob.previewStrokeWidth(), cncJob.stats());
            copyLayerAppearance(new CncTravelLayerKey(sourceItem), new CncTravelLayerKey(copyItem));
            copyLayerAppearance(new CncCutLayerKey(sourceItem), new CncCutLayerKey(copyItem));
            refreshCncAnnotations(copyItem);
        } else {
            return null;
        }

        appendConsole("Objeto copiado: " + sourceItem.getValue() + " -> " + copyName);
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

    private TextArea buildGCodeViewer(String gcode) {
        TextArea area = new TextArea(gcode);
        area.setEditable(false);
        area.setStyle("-fx-font-family: monospace;");
        return area;
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
        refreshCncAnnotations(item);
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
    private void refreshCncAnnotations(TreeItem<String> item) {
        CncAnnotationKey key = new CncAnnotationKey(item);
        CncJobEntry entry = cncJobByItem.get(item);
        boolean showArrows = entry != null && entry.stats() != null && !cncArrowsOff.contains(item)
                && isObjectVisible(item);
        boolean routeVisible = entry != null && entry.stats() != null && isObjectVisible(item);
        plotAreaView.setSteps(key, routeVisible ? entry.stats().steps() : List.of(),
                routeVisible ? entry.stats().pathMarks() : List.of(),
                routeVisible ? entry.previewStrokeWidth() : 0, routeVisible ? entry.stats().units() : "MM");
        plotAreaView.setArrows(key, !showArrows ? List.of() : entry.stats().cutArrows().stream()
                .map(a -> new PlotAreaView.Arrow(a.x(), a.y(), a.dx(), a.dy(), a.length()))
                .toList());
        if (entry == null || entry.stats() == null || entry.stats().pathMarks().isEmpty()
                || cncAnnotationsOff.contains(item) || !isObjectVisible(item)) {
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
                .map(mark -> new PlotAreaView.Annotation(mark.x(), mark.y(), Integer.toString(mark.sequence())))
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
     * to a file the user picks once "Gerar" is clicked. Runs on the FX
     * thread directly - string-building over a few hundred/thousand points
     * is not the kind of work secao 4.3 is about; move this to JobExecutor
     * if a pathological input ever makes it worth it.
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
            appendConsole("Drilling Tool: o Excellon selecionado nao esta disponivel.");
            return;
        }
        openToolPanel("Drilling Tool", DrillGCodeToolPanel.build(sources, initialSource,
                () -> toolsDatabaseTools(LegacyToolsDatabase::drillTools),
                result -> runDrillGCodeGeneration(result.source().item(), result.source().image(), result),
                this::closeToolPanel));
    }

    private void runDrillGCodeGeneration(TreeItem<String> item, ExcellonImage image, DrillGCodeToolPanel.Result result) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de furacao");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Programa " + result.preprocessor().label(),
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

        try {
            CncJobResult job = GCodeGenerator.generateDrillCncJob(image, result.settingsByTool(),
                    result.orderedToolIds(), result.options(), result.preprocessor());
            Files.writeString(outFile.toPath(), job.gcode());
            Map<Integer, DrillGCodeParameters> updatedDefaults = new LinkedHashMap<>(
                    drillDefaultsByItem.getOrDefault(item, Map.of()));
            updatedDefaults.putAll(result.settingsByTool());
            drillDefaultsByItem.put(item, Map.copyOf(updatedDefaults));
            drillCncSettingsByItem.put(item, result.cncSettings());
            AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
            appendConsole("G-code de furacao salvo em " + outFile + " (" + job.gcode().lines().count() + " linhas).");
            GCodeToolpathParser.Result preview = previewOf(job.gcode(), CancellationToken.none());
            if (preview.warning() != null) appendConsole(outFile.getName() + ": " + preview.warning());
            addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), job.gcode(),
                    job.travelGeometry(), job.cutGeometry(), null, null, 0,
                    preview.stats());
            closeToolPanel();
        } catch (Exception e) {
            appendConsole("Falha ao gerar/salvar G-code: " + e.getMessage());
        }
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
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        TreeItem<String> item = result.source().item();
        ExcellonImage image = result.source().image();
        beginJob("Gerando Geometry de fresagem Excellon...");
        JobHandle<Geometry> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.1, "Calculando caminhos de fresagem...");
            Geometry geometry = ExcellonMillingGenerator.generate(image, result.toolIds(),
                    result.millDiameter(), result.kind(), context::isCancelled);
            context.checkCancelled();
            context.reportProgress(0.95, "Preparando Geometry...");
            return geometry;
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().thenAccept(geometry -> Platform.runLater(() -> {
            if (excellonByItem.get(item) != image) {
                appendConsole("O Excellon de origem foi removido; Geometry de fresagem descartada.");
                setStatus("Origem removida.", ERROR_COLOR);
            } else if (geometry.isEmpty()) {
                appendConsole("Nenhum caminho foi gerado para as ferramentas selecionadas.");
                setStatus("Sem caminhos.", ERROR_COLOR);
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
                            null, db.tip() == null ? Map.of() : Map.of(0, db.tip())));
                }
                appendConsole("Geometry de fresagem criada: " + name + ". Revise os caminhos antes de gerar CNC Job.");
                selectProjectItem(generated);
                plotAreaView.fitToLayer(generated);
                closeToolPanel();
                setStatus("Geometry de fresagem concluida.", IDLE_COLOR);
            }
            updateProgress(1);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao gerar Geometry de fresagem: ");
                onJobFinished();
            });
            return null;
        });
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
            appendConsole("Isolation: o Gerber selecionado nao esta disponivel.");
            return;
        }
        List<IsolationToolPanel.ExceptionArea> exceptionAreas = geometryByItem.entrySet().stream()
                .filter(entry -> image.units().equalsIgnoreCase(entry.getValue().units()))
                .filter(entry -> entry.getValue().geometry() != null
                        && !entry.getValue().geometry().isEmpty()
                        && entry.getValue().geometry().getDimension() == 2)
                .map(entry -> new IsolationToolPanel.ExceptionArea(
                        entry.getKey().getValue(), entry.getValue().geometry()))
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
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        beginJob("Gerando Geometry de isolamento...");
        record IsolationJobOutcome(List<IsolationGenerator.ToolResult> results, OptionalDouble clearance) { }
        JobHandle<IsolationJobOutcome> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.05, "Calculando caminhos de isolamento...");
            OptionalDouble clearance = params.checkValidity()
                    ? NccGenerator.minimumCopperClearance(image.solidGeometry()) : OptionalDouble.empty();
            List<IsolationGenerator.ToolResult> results;
            if (params.restMachining() && !params.follow()) {
                results = IsolationGenerator.generateRest(image.units(), image.solidGeometry(),
                        params.tools(), context::isCancelled);
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
            context.reportProgress(0.95, "Preparando Geometry de isolamento...");
            return new IsolationJobOutcome(results, clearance);
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    List<IsolationGenerator.ToolResult> results = outcome.results();
                    outcome.clearance().ifPresent(clearance -> {
                        boolean anySuitable = params.tools().stream()
                                .anyMatch(tool -> tool.toolDiameter() <= clearance);
                        appendConsole(String.format(java.util.Locale.ROOT,
                                anySuitable ? "Isolation: ao menos uma ferramenta pode isolar completamente "
                                        + "(distancia minima %.4f)."
                                        : "Isolation: nenhuma ferramenta pode isolar completamente "
                                        + "(distancia minima %.4f).", clearance));
                    });
                    if (gerberByItem.get(item) != image) {
                        appendConsole("O Gerber de origem foi removido; Geometry de isolamento descartada.");
                        setStatus("Origem removida.", ERROR_COLOR);
                    } else if (results.stream().allMatch(result -> result.isolation().isEmpty())) {
                        appendConsole("Isolation nao gerou caminhos (origem vazia ou area de excecao cobriu tudo).");
                        if (params.restMachining() && results.get(results.size() - 1).remainingCopperCount() > 0) {
                            appendConsole("Aviso: " + results.get(results.size() - 1).remainingCopperCount()
                                    + " regioes de cobre nao foram isoladas por nenhuma ferramenta selecionada.");
                        }
                        setStatus("Sem caminhos.", ERROR_COLOR);
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
                                    created++;
                                }
                            }
                        }
                        appendConsole(String.format("Isolation: %d elementos, comprimento total=%.4f, bounds=%s",
                                results.stream().mapToInt(result -> result.isolation().ringCount()).sum(),
                                results.stream().mapToDouble(result -> result.isolation().totalLength()).sum(),
                                lastGenerated == null ? "vazio"
                                        : geometryByItem.get(lastGenerated).geometry().getEnvelopeInternal()));
                        appendConsole("Isolation criou " + created + " Geometry(s). Confira os caminhos "
                                + "e gere o CNC Job pela Geometry quando estiver pronto.");
                        if (params.restMachining() && results.get(results.size() - 1).remainingCopperCount() > 0) {
                            appendConsole("Aviso: " + results.get(results.size() - 1).remainingCopperCount()
                                    + " regioes de cobre nao foram isoladas por nenhuma ferramenta selecionada.");
                        }
                        if (lastGenerated != null) {
                            selectProjectItem(lastGenerated);
                            plotAreaView.fitToLayer(lastGenerated);
                        }
                        closeToolPanel();
                        setStatus("Geometry de isolamento concluida.", IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao gerar Geometry de isolamento: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Creates an editable cutout Geometry, as appTools/ToolCutOut.py does. */
    private void generateCutout(TreeItem<String> item, GerberImage image) {
        openCutoutTool(item, image.units(), image.solidGeometry(), () -> gerberByItem.get(item) == image);
    }

    private void generateCutout(TreeItem<String> item, GeometryEntry entry) {
        openCutoutTool(item, entry.units(), entry.geometry(), () -> geometryByItem.get(item) == entry);
    }

    private void openCutoutTool(TreeItem<String> item, String units, Geometry source,
                                BooleanSupplier sourceAvailable) {
        if (source == null || source.isEmpty() || source.getDimension() != 2) {
            appendConsole("Cutout exige um Gerber ou Geometry preenchida como origem.");
            return;
        }
        openToolPanel("Cutout Tool", CutoutToolPanel.build(units,
                (polygon, onSelected, onCancelled) -> beginNccAreaSelection(source,
                        polygon ? NccToolPanel.AreaShape.POLYGON : NccToolPanel.AreaShape.RECTANGLE,
                        onSelected, onCancelled),
                plotAreaView::cancelPlacement,
                () -> toolsDatabaseTools(LegacyToolsDatabase::cutoutTools),
                result -> {
                    plotAreaView.cancelPlacement();
                    runCutoutGeneration(item, units, source, sourceAvailable, result);
                }, () -> {
                    plotAreaView.cancelPlacement();
                    closeToolPanel();
                }));
    }

    private record CutoutJobOutcome(CutoutResult cutout, ExcellonImage mouseBites) {
    }

    private void runCutoutGeneration(TreeItem<String> item, String units, Geometry source,
                                     BooleanSupplier sourceAvailable, CutoutToolPanel.Result result) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        beginJob("Gerando Geometry de cutout...");
        JobHandle<CutoutJobOutcome> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.05, "Calculando caminhos de cutout...");
            CutoutResult cutout = CutoutGenerator.generate(
                    units, source, result.cutoutParams(),
                    result.manualGapAreas(), context::isCancelled);
            ExcellonImage mouseBites = result.gapType() == CutoutToolPanel.GapType.M_BITES
                    ? CutoutGenerator.generateMouseBites(units, source,
                            result.cutoutParams(), result.biteDiameter(), result.biteSpacing(),
                            result.manualGapAreas(), context::isCancelled)
                    : null;
            context.checkCancelled();
            context.reportProgress(0.95, "Preparando Geometry de cutout...");
            return new CutoutJobOutcome(cutout, mouseBites);
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    CutoutResult cutout = outcome.cutout();
                    if (!sourceAvailable.getAsBoolean()) {
                        appendConsole("A origem foi removida ou alterada; Geometry de cutout descartada.");
                        setStatus("Origem removida.", ERROR_COLOR);
                    } else if (cutout.isEmpty()) {
                        appendConsole("Cutout nao gerou nenhum caminho (geometria de cobre vazia?).");
                        setStatus("Sem caminhos.", ERROR_COLOR);
                    } else {
                        appendConsole(String.format("Cutout: %d caminhos, comprimento total=%.4f, bounds=%s",
                                cutout.partCount(), cutout.totalLength(), Arrays.toString(cutout.bounds())));
                        String name = uniqueDerivedName(item.getValue() + "_cutout");
                        TreeItem<String> generated = addGeometryToProject(name, item.getValue(), units,
                                cutout.geometry(), true,
                                List.of(new ToolGeometry(result.cutoutParams().toolDiameter(), cutout.geometry())));
                        if (result.gapType() == CutoutToolPanel.GapType.THIN) {
                            if (cutout.gapGeometry().isEmpty()) {
                                appendConsole("Thin: nenhuma ponte restante para usinagem rasa.");
                            } else {
                                String thinName = uniqueDerivedName(item.getValue() + "_cutout_thin");
                                addGeometryToProject(thinName, item.getValue(), units,
                                        cutout.gapGeometry(), true,
                                        List.of(new ToolGeometry(result.cutoutParams().toolDiameter(),
                                                cutout.gapGeometry())));
                                appendConsole("Thin criou Geometry para as pontes: " + thinName
                                        + ". Gere um CNC Job separado com Cut Z mais raso.");
                            }
                        }
                        if (outcome.mouseBites() != null && !outcome.mouseBites().isEmpty()) {
                            String biteName = uniqueDerivedName(item.getValue() + "_mouse_bites");
                            addExcellonToProject(biteName, null, outcome.mouseBites());
                            appendConsole("M-Bites criou Excellon com " + outcome.mouseBites().totalDrills()
                                    + " furos: " + biteName);
                        }
                        appendConsole("Geometry de cutout criada: " + name
                                + ". Gere o CNC Job pela Geometry apos revisar os caminhos.");
                        selectProjectItem(generated);
                        plotAreaView.fitToLayer(generated);
                        closeToolPanel();
                        setStatus("Geometry de cutout concluida.", IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao gerar Geometry de cutout: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** One tool's contribution plus, optionally, the NCC-wide diameter-validity check - see NccToolPanel.Result. */
    private record NccJobOutcome(NccResult result, OptionalDouble minCopperClearance) {
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
            appendConsole("NCC exige um Gerber ou Geometry preenchida como origem.");
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
            appendConsole("NCC: a origem selecionada nao esta disponivel.");
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
                        appendConsole("NCC: o poligono precisa de pelo menos tres vertices distintos.");
                        onCancelled.run();
                        return;
                    }
                    ring.add(new Coordinate(ring.get(0)));
                    try {
                        Geometry area = source.getFactory().createPolygon(ring.toArray(new Coordinate[0]));
                        if (!area.isValid() || area.getArea() <= 0) {
                            throw new IllegalArgumentException("poligono sem area valida");
                        }
                        onSelected.accept(area);
                    } catch (RuntimeException error) {
                        appendConsole("NCC: selecao poligonal invalida: " + error.getMessage());
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
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        NccParameters params = panelResult.parameters();
        beginJob("Gerando Non-Copper Clearing...");
        JobHandle<NccJobOutcome> handle = jobExecutor.submit(context -> {
            OptionalDouble minClearance = gerberSource && panelResult.checkValidity()
                    ? NccGenerator.minimumCopperClearance(source)
                    : OptionalDouble.empty();
            NccResult result = NccGenerator.generate(units, source, params,
                    context::isCancelled,
                    fraction -> context.reportProgress(fraction, "Gerando Non-Copper Clearing..."));
            return new NccJobOutcome(result, minClearance);
        }, (fraction, message) -> Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;

        handle.completion()
                .thenAccept(outcome -> Platform.runLater(() -> {
                    if (gerberSource ? !gerberByItem.containsKey(item) : !geometryByItem.containsKey(item)) {
                        appendConsole("A origem do NCC foi removida; resultado descartado.");
                        setStatus("Origem removida.", ERROR_COLOR);
                        onJobFinished();
                        return;
                    }
                    NccResult result = outcome.result();
                    outcome.minCopperClearance().ifPresent(minClearance -> {
                        boolean anySuitable = java.util.stream.Stream.concat(params.toolDiameters().stream(),
                                params.isolationToolDiameters().stream()).anyMatch(d -> d <= minClearance);
                        appendConsole(String.format(java.util.Locale.ROOT,
                                anySuitable
                                        ? "Verificacao de validade: ao menos uma ferramenta consegue fazer isolamento completo (distancia minima de cobre = %.4f)."
                                        : "Verificacao de validade: nenhuma ferramenta selecionada consegue fazer isolamento completo (distancia minima de cobre = %.4f).",
                                minClearance));
                    });
                    for (var toolResult : result.toolResults()) {
                        if (toolResult.operation() == NccOperation.ISO && toolResult.isEmpty()) {
                            appendConsole("NCC: a ferramenta ISO de diametro " + toolResult.toolDiameter()
                                    + " nao gerou contorno dentro do limite selecionado.");
                        }
                    }
                    if (params.isolationToolDiameters().stream().anyMatch(d -> d > params.margin())) {
                        appendConsole("NCC: a margem e menor que o diametro de uma ferramenta ISO; "
                                + "confira se os contornos nao foram cortados pelo limite.");
                    }
                    if (result.isEmpty()) {
                        appendConsole("NCC nao gerou caminhos. A ferramenta pode ser grande demais para a area livre.");
                        setStatus("Sem caminhos.", ERROR_COLOR);
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
                        appendConsole(String.format(
                                "NCC: %d caminhos, comprimento total=%.4f, %d ferramenta(s), falhas=%d, bounds=%s",
                                result.pathCount(), result.totalLength(), result.toolResults().size(),
                                result.totalFailedPolygonCount(), Arrays.toString(result.bounds())));
                        selectProjectItem(generated);
                        plotAreaView.fitToLayer(generated);
                        closeToolPanel();
                        setStatus("Concluido.", IDLE_COLOR);
                    }
                    updateProgress(1);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao gerar Non-Copper Clearing: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    private void generateGeometryCncJob(TreeItem<String> item, GeometryEntry entry) {
        openToolPanel("Geometry CNC Job", GeometryCncToolPanel.build(entry.units(), entry.geometry(), entry.tools(),
                entry.cncDefaults(), geometryCncSettingsByItem.get(item),
                () -> toolsDatabaseTools(LegacyToolsDatabase::millingTools),
                result -> runGeometryCncGeneration(item, entry, result), this::closeToolPanel));
    }

    private void runGeometryCncGeneration(TreeItem<String> item, GeometryEntry entry,
                                          GeometryCncToolPanel.Result result) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de Geometry");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Programa " + result.preprocessor().label(),
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

        beginJob("Gerando CNC Job de Geometry...");
        JobHandle<GeneratedCncJob> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.05, "Ordenando caminhos de Geometry...");
            CncJobResult job = GCodeGenerator.generateGeometryCncJob(entry.units(), result.tools(),
                    result.parameters(), result.vTools(), result.parametersByTool(), context::isCancelled, result.preprocessor());
            context.checkCancelled();
            context.reportProgress(0.85, "Salvando G-code de Geometry...");
            Files.writeString(outFile.toPath(), job.gcode());
            context.reportProgress(0.92, "Calculando distancia e tempo estimado...");
            return new GeneratedCncJob(job, previewOf(job.gcode(), context::isCancelled));
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(generated -> Platform.runLater(() -> {
                    CncJobResult job = generated.job();
                    if (geometryByItem.get(item) == entry) {
                        geometryByItem.put(item, new GeometryEntry(entry.sourceName(), entry.units(), entry.geometry(),
                                entry.strokeOnly(), result.tools(), result.parameters()));
                        geometryCncSettingsByItem.put(item, new GeometryCncSettings(result.preprocessor(),
                                null, result.vTools(), result.parametersByTool()));
                    }
                    AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
                    appendConsole("G-code de Geometry salvo em " + outFile
                            + " (" + job.gcode().lines().count() + " linhas).");
                    GCodeToolpathParser.Result preview = generated.preview();
                    if (preview != null && preview.warning() != null)
                        appendConsole(outFile.getName() + ": " + preview.warning());
                    boolean centerlines = preview != null && preview.plotAvailable() && preview.stats() != null
                            && preview.stats().cutterDiameter() != null;
                    // Programs that state their cutter width get the fast stroked preview, like imported ones.
                    TreeItem<String> cncItem = addCncJobToProject(outFile.getName(), item.getValue(),
                            outFile.toPath(), job.gcode(), job.travelGeometry(), job.cutGeometry(),
                            centerlines ? preview.travelCenterlines() : null,
                            centerlines ? preview.cutCenterlines() : null,
                            centerlines ? previewWidthFor(preview) : 0,
                            preview == null ? null : preview.stats());
                    selectProjectItem(cncItem);
                    focusCncJob(cncItem, cncJobByItem.get(cncItem));
                    closeToolPanel();
                    updateProgress(1);
                    setStatus("Concluido.", IDLE_COLOR);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao gerar/salvar CNC Job de Geometry: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    private void removeFromProject(TreeItem<String> item, Map<TreeItem<String>, ?> byItem) {
        if (geometryEditor.isEditing(item) && geometryEditor.isBusy()) {
            appendConsole("Aguarde ou cancele a operacao Geometry antes de remover este objeto.");
            return;
        }
        if (gcodeEditor.isEditing(item) && gcodeEditor.hasUnappliedChanges()) {
            appendConsole("Aplique ou cancele a edicao de G-code antes de remover este CNC Job.");
            return;
        }
        if (geometryEditor.isEditing(item) && geometryEditor.hasUnappliedChanges()) {
            appendConsole("Aplique ou cancele a edicao de Geometry antes de remover este objeto.");
            return;
        }
        if (excellonEditor.isEditing(item) && excellonEditor.hasUnappliedChanges()) {
            appendConsole("Aplique ou cancele a edicao de Excellon antes de remover este objeto.");
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
        refreshPlotSelectionOutline();
        appendConsole("Removido do projeto: " + item.getValue());
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

        CheckBox solidCb = new CheckBox("Solid");
        solidCb.setSelected(plotAreaView.isLayerFilled(item));
        solidCb.setOnAction(e -> plotAreaView.setLayerFilled(item, solidCb.isSelected()));
        CheckBox multicolorCb = new CheckBox("Multi-Color");
        multicolorCb.setSelected(plotAreaView.isLayerMulticolor(item));
        multicolorCb.setOnAction(e -> plotAreaView.setLayerMulticolor(item, multicolorCb.isSelected()));
        box.getChildren().add(labeledRow("Plot Options:", solidCb, multicolorCb));

        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        CheckBox followCb = new CheckBox("Follow");
        followCb.setTooltip(new Tooltip("Exibe a linha central das trilhas Gerber."));
        followCb.setSelected(gerberFollowItems.contains(item));
        followCb.setOnAction(e -> {
            boolean follow = followCb.isSelected();
            if (follow) {
                gerberFollowItems.add(item);
            } else {
                gerberFollowItems.remove(item);
            }
            plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GERBER,
                    follow ? image.followGeometry() : image.solidGeometry(),
                    GERBER_FILL, GERBER_STROKE, follow);
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

        box.getChildren().add(new Label("Apertures Table:"));
        box.getChildren().add(buildAperturesTableSection(item, image));

        box.getChildren().add(new Label("Transformations:"));
        box.getChildren().add(transformationsSection(item));

        box.getChildren().add(propertiesSection(String.format(
                "Unidades: %s%nAperturas: %d%nArea total: %.4f%nBounds: %s",
                image.units(), image.apertures().size(), image.totalArea(), Arrays.toString(image.bounds())
        )));
        return box;
    }

    /** Follow, non-copper and bounding-box Geometry generators from GerberObjectUI's Utilities section. */
    private TitledPane buildGerberUtilities(TreeItem<String> item, GerberImage image) {
        VBox content = new VBox(7);
        content.setPadding(new Insets(8));

        Button followButton = new Button("Gerar Geometry Follow");
        followButton.setGraphic(legacyIcon("geometry32.png", 16));
        followButton.setMaxWidth(Double.MAX_VALUE);
        followButton.setOnAction(e -> addDerivedGeometry(item, image, "_follow", image.followGeometry(), true));

        Label nonCopperLabel = new Label("Non-copper regions");
        nonCopperLabel.setStyle("-fx-font-weight: bold;");
        TextField nonCopperMargin = marginField();
        CheckBox nonCopperRounded = new CheckBox("Rounded");
        Button nonCopperButton = new Button("Generate Geometry");
        nonCopperButton.setGraphic(legacyIcon("geometry32.png", 18));
        nonCopperButton.setOnAction(e -> generateGerberUtility(item, image, "_noncopper",
                nonCopperMargin, nonCopperRounded.isSelected(), true));

        Label bboxLabel = new Label("Bounding Box");
        bboxLabel.setStyle("-fx-font-weight: bold;");
        TextField bboxMargin = marginField();
        CheckBox bboxRounded = new CheckBox("Rounded");
        Button bboxButton = new Button("Generate Geometry");
        bboxButton.setGraphic(legacyIcon("geometry32.png", 18));
        bboxButton.setOnAction(e -> generateGerberUtility(item, image, "_bbox",
                bboxMargin, bboxRounded.isSelected(), false));

        content.getChildren().addAll(followButton, new Separator(), nonCopperLabel,
                labeledRow("Boundary Margin:", nonCopperMargin),
                labeledRow("", nonCopperRounded, nonCopperButton), new Separator(), bboxLabel,
                labeledRow("Boundary Margin:", bboxMargin), labeledRow("", bboxRounded, bboxButton));
        TitledPane pane = new TitledPane("UTILITIES", content);
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
            appendConsole("Falha ao gerar Geometry: " + ex.getMessage());
            setStatus("Falhou.", ERROR_COLOR);
        }
    }

    private void addDerivedGeometry(TreeItem<String> sourceItem, GerberImage image, String suffix,
                                    Geometry geometry, boolean strokeOnly) {
        if (geometry == null || geometry.isEmpty()) {
            appendConsole("Geometry " + suffix + " ficou vazia.");
            setStatus("Falhou.", ERROR_COLOR);
            return;
        }
        String name = uniqueDerivedName(sourceItem.getValue() + suffix);
        TreeItem<String> generated = addGeometryToProject(name, sourceItem.getValue(), image.units(), geometry, strokeOnly);
        appendConsole("Geometry gerada: " + name);
        selectProjectItem(generated);
        plotAreaView.fitToLayer(generated);
        setStatus("Concluido.", IDLE_COLOR);
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

        CheckBox solidCb = new CheckBox("Solid");
        solidCb.setSelected(plotAreaView.isLayerFilled(item));
        solidCb.setOnAction(e -> plotAreaView.setLayerFilled(item, solidCb.isSelected()));
        CheckBox multicolorCb = new CheckBox("Multi-Color");
        multicolorCb.setSelected(plotAreaView.isLayerMulticolor(item));
        multicolorCb.setOnAction(e -> plotAreaView.setLayerMulticolor(item, multicolorCb.isSelected()));
        box.getChildren().add(labeledRow("Plot Options:", solidCb, multicolorCb));

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
                "Unidades: %s%nFuros totais: %d%nSlots totais: %d%nBounds: %s",
                image.units(), image.totalDrills(), image.totalSlots(), Arrays.toString(image.bounds())
        )));

        CheckBox plotCb = new CheckBox("Plot");
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setTooltip(new Tooltip("Exibe ou oculta este objeto Excellon no desenho."));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        Label tableLabel = new Label("Tools Table");
        tableLabel.setStyle("-fx-font-weight: bold;");
        Region tableHeaderSpacer = new Region();
        HBox.setHgrow(tableHeaderSpacer, Priority.ALWAYS);
        HBox tableHeader = new HBox(8, tableLabel, tableHeaderSpacer, plotCb);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(tableHeader);
        box.getChildren().add(ExcellonObjectToolsTable.build(image));

        box.getChildren().add(new Separator());
        Label toolsLabel = new Label("TOOLS");
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
        millingButton.setTooltip(new Tooltip("Cria Geometry editavel para fresar furos ou slots selecionados."));
        millingButton.setOnAction(e -> generateExcellonMilling(item, image));
        box.getChildren().add(millingButton);

        VBox utilitiesContent = new VBox(8);
        utilitiesContent.setPadding(new Insets(8));
        Label utilitiesNote = new Label("Mill Drills e Mill Slots estao no Milling Tool acima.");
        utilitiesNote.setWrapText(true);
        utilitiesContent.getChildren().add(utilitiesNote);
        TitledPane utilities = new TitledPane("UTILITIES", utilitiesContent);
        utilities.setGraphic(legacyIcon("settings18.png", 18));
        utilities.setExpanded(false);
        box.getChildren().add(new Separator());
        box.getChildren().add(utilities);

        box.getChildren().add(new Label("Transformations"));
        box.getChildren().add(transformationsSection(item));
        return box;
    }

    private Node buildGeometryPropertiesPanel(TreeItem<String> item, GeometryEntry entry) {
        VBox box = objectPropertiesHeader("Geometry Object", GEOMETRY_STROKE, "geometry32.png");
        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button cncButton = new Button("Generate CNC Job");
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

        Button editButton = new Button("Editar Geometry");
        editButton.setGraphic(legacyIcon("edit_file32.png", 16));
        editButton.setMaxWidth(Double.MAX_VALUE);
        editButton.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGeometry();
        });
        box.getChildren().add(editButton);

        box.getChildren().add(new Label("Transformations:"));
        box.getChildren().add(transformationsSection(item));

        Envelope envelope = entry.geometry().getEnvelopeInternal();
        double[] bounds = entry.geometry().isEmpty() ? null
                : new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
        String toolText = entry.tools().isEmpty() ? "" : String.format("%nFerramentas: %s", entry.tools().stream()
                .map(tool -> String.format("%.4f", tool.toolDiameter())).collect(java.util.stream.Collectors.joining(", ")));
        box.getChildren().add(propertiesSection(String.format(
                "Origem: %s%nUnidades: %s%s%nArea: %.4f%nComprimento: %.4f%nBounds: %s",
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

        boolean hasGeometry = entry.travelGeometry() != null || entry.cutGeometry() != null;
        CncTravelLayerKey travelKey = new CncTravelLayerKey(item);
        CncCutLayerKey cutKey = new CncCutLayerKey(item);
        boolean travelVisible = hasGeometry && plotAreaView.isLayerVisible(travelKey);
        boolean cutVisible = hasGeometry && plotAreaView.isLayerVisible(cutKey);

        ComboBox<String> kindCombo = new ComboBox<>();
        kindCombo.getItems().addAll("All", "Travel", "Cut");
        kindCombo.setValue(!cutVisible ? "Travel" : !travelVisible ? "Cut" : "All");
        kindCombo.setDisable(!hasGeometry);

        CheckBox plotCb = new CheckBox();
        plotCb.setSelected(travelVisible || cutVisible);
        plotCb.setDisable(!hasGeometry);

        Runnable applyVisibility = () -> {
            boolean visible = plotCb.isSelected();
            String kind = kindCombo.getValue();
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
                CheckBox annotationCb = new CheckBox("Display Annotation");
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
                Button startWalk = new Button("Percorrer");
                startWalk.setOnAction(e -> plotAreaView.selectStep(routeKey, 0));
                Button previousLeg = new Button("<");
                previousLeg.setOnAction(e -> plotAreaView.stepBy(-1));
                Button nextLeg = new Button(">");
                nextLeg.setOnAction(e -> plotAreaView.stepBy(1));
                Button clearLegs = new Button("Limpar");
                clearLegs.setOnAction(e -> plotAreaView.clearStepSelection());
                cncStepLabel = new Label(plotAreaView.hasStepSelection() ? "" : "ou clique num numero ou linha do plot");
                Button playWalk = new Button("Reproduzir");
                playWalk.setOnAction(e -> {
                    if (!plotAreaView.hasStepSelection()) {
                        plotAreaView.selectStep(routeKey, 0);
                    }
                    plotAreaView.toggleWalk();
                });
                Button exportSteps = new Button("Exportar CSV");
                exportSteps.setOnAction(e -> exportCncSteps(item, stats));
                HBox walkRow = new HBox(6, startWalk, previousLeg, nextLeg, clearLegs);
                box.getChildren().add(labeledRow("Trajeto passo a passo:", walkRow));
                box.getChildren().add(labeledRow("Reproducao / sequencia:", new HBox(6, playWalk, exportSteps)));
                box.getChildren().add(cncStepLabel);
            }
            if (!stats.cutArrows().isEmpty()) {
                CheckBox arrowsCb = new CheckBox("Display Direction Arrows");
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
                    new Label(String.format(java.util.Locale.ROOT, "%.4f %s", stats.xyDistance(), units))));
            box.getChildren().add(labeledRow("Estimated time:",
                    new Label(CncJobToolsTable.formatDuration(stats.estimatedMinutes()))));
            if (stats.hasTools()) {
                Label toolsLabel = new Label("Tools Table");
                toolsLabel.setStyle("-fx-font-weight: bold;");
                Set<Integer> hidden = hiddenCncTools.computeIfAbsent(item, ignored -> new LinkedHashSet<>());
                box.getChildren().addAll(toolsLabel,
                        CncJobToolsTable.build(stats, hidden, () -> applyCncToolVisibility(item)));
            }
        }

        Button viewButton = new Button("Ver G-code");
        viewButton.setGraphic(legacyIcon("source32.png", 16));
        viewButton.getStyleClass().add("primary-action");
        viewButton.setMaxWidth(Double.MAX_VALUE);
        viewButton.setOnAction(e -> openAuxiliaryTab(item.getValue(), () -> buildGCodeViewer(entry.gcode())));
        box.getChildren().add(viewButton);
        Button editButton = new Button("Editar G-code");
        editButton.setGraphic(legacyIcon("code_editor32.png", 16));
        editButton.setMaxWidth(Double.MAX_VALUE);
        editButton.setOnAction(e -> {
            selectProjectItem(item);
            editSelectedGCode();
        });
        box.getChildren().add(editButton);
        box.getChildren().add(propertiesSection(String.format(
                "Origem: %s%nArquivo: %s%nLinhas: %d",
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
        return labeledRow("Name:", nameField);
    }

    /** The legacy "PROPERTIES" toggle button + inline stats frame, done here as a collapsible section. */
    private TitledPane propertiesSection(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        TitledPane pane = new TitledPane("PROPERTIES", label);
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

    private void openPreferences() {
        openAuxiliaryTab("Preferencias", this::buildPreferencesPanel);
    }

    private Node buildPreferencesPanel() {
        AppPreferences.PlotStatusSettings saved = AppPreferences.loadPlotStatusSettings();
        Label title = new Label("Preferencias");
        title.getStyleClass().add("tool-title");
        ComboBox<ThemeOption> theme = new ComboBox<>();
        theme.getItems().addAll(ThemeOption.values());
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ThemeOption option) {
                return option == null ? "" : option.label();
            }
            @Override public ThemeOption fromString(String value) {
                return theme.getValue();
            }
        });
        theme.setValue(currentTheme);
        CheckBox snap = new CheckBox("Ativar snap na grade");
        snap.setSelected(saved.gridSnap());
        CheckBox showGrid = new CheckBox("Mostrar grade visual");
        showGrid.setSelected(saved.gridVisible());
        showGrid.setTooltip(new Tooltip("A grade visual pode ficar oculta sem desativar o snap."));
        CheckBox linked = new CheckBox("Usar passo X tambem em Y");
        linked.setSelected(saved.gridLinked());
        TextField gridX = new TextField(Double.toString(saved.gridX()));
        TextField gridY = new TextField(Double.toString(saved.gridY()));
        gridX.setPrefColumnCount(8);
        gridY.setPrefColumnCount(8);
        gridY.setDisable(linked.isSelected());
        linked.selectedProperty().addListener((obs, oldValue, value) -> gridY.setDisable(value));
        CheckBox axis = new CheckBox("Mostrar eixos");
        axis.setSelected(saved.axisVisible());
        CheckBox hud = new CheckBox("Mostrar HUD de coordenadas");
        hud.setSelected(saved.hudVisible());
        CheckBox workspace = new CheckBox("Mostrar area A4");
        workspace.setSelected(saved.workspaceVisible());
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Tema:"), theme);
        grid.addRow(1, new Label("Passo X:"), gridX);
        grid.addRow(2, new Label("Passo Y:"), gridY);
        Label feedback = new Label();
        feedback.setWrapText(true);
        Button apply = new Button("Aplicar preferencias");
        apply.setGraphic(legacyIcon("apply32.png", 16));
        apply.getStyleClass().add("primary-action");
        apply.setOnAction(event -> {
            try {
                double x = preferenceStep(gridX.getText(), "Passo X");
                double y = linked.isSelected() ? x : preferenceStep(gridY.getText(), "Passo Y");
                AppPreferences.PlotStatusSettings settings = new AppPreferences.PlotStatusSettings(
                        snap.isSelected(), showGrid.isSelected(), x, y, linked.isSelected(), axis.isSelected(),
                        hud.isSelected(), workspace.isSelected());
                statusControls.applySettings(settings);
                applyTheme(theme.getValue());
                feedback.setText("Preferencias aplicadas e salvas.");
                setStatus("Preferencias salvas.", IDLE_COLOR);
            } catch (IllegalArgumentException error) {
                feedback.setText(error.getMessage());
            }
        });
        VBox panel = new VBox(12, title,
                new Label("Aparencia"), grid,
                new Separator(), new Label("Plot Area"), snap, showGrid, linked, axis, hud, workspace,
                feedback, apply);
        panel.setPadding(new Insets(18));
        panel.setMaxWidth(460);
        PanelTooltips.install(panel, "Preferencias");
        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static double preferenceStep(String text, String label) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isFinite(value) && value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(label + " deve ser numerico e positivo.");
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

    boolean confirmToolsDatabaseClose() {
        return toolsDatabasePanel == null || toolsDatabasePanel.confirmClose();
    }

    @FunctionalInterface
    private interface DatabaseProjection<T> { List<T> read(org.json.JSONObject root) throws IOException; }

    private <T> List<T> toolsDatabaseTools(DatabaseProjection<T> projection) {
        try {
            if (toolsDatabasePanel != null) return projection.read(toolsDatabasePanel.snapshot());
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Abrir Tools Database do FlatCAM Python");
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
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressPercentLabel.setMouseTransparent(true);
        progressPercentLabel.getStyleClass().add("progress-percentage");
        StackPane progressWithPercentage = new StackPane(progressBar, progressPercentLabel);
        HBox.setHgrow(progressWithPercentage, Priority.ALWAYS);

        HBox progressRow = new HBox(8, progressWithPercentage);
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
        appendConsole("Job de demonstracao iniciado (nao bloqueia a UI - tente redimensionar a janela).");

        JobHandle<Void> handle = jobExecutor.submit(new DemoJob(), (fraction, message) ->
                Platform.runLater(() -> {
                    updateProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;

        handle.completion()
                .thenAccept(result -> Platform.runLater(() -> {
                    setStatus("Concluido.", IDLE_COLOR);
                    appendConsole("Job de demonstracao concluido.");
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (isCancellation(error)) {
                            setStatus("Cancelado.", CANCELLED_COLOR);
                            appendConsole("Job de demonstracao cancelado pelo usuario.");
                        } else {
                            setStatus("Falhou.", ERROR_COLOR);
                            appendConsole("Job de demonstracao falhou: " + error.getMessage());
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
        List<File> files = pickCamFiles("Abrir Gerber (prototipo)",
                new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.cmp", "*.gtl", "*.gbl", "*.gm1", "*.txt"));
        if (!files.isEmpty()) {
            openGerberQueue(files, 0);
        }
    }

    private void openGCode() {
        List<File> files = pickCamFiles("Abrir G-Code",
                new FileChooser.ExtensionFilter("Programas CNC (G-code / ICP / HPGL / RML)", "*.nc", "*.gcode", "*.tap", "*.cnc", "*.txt", "*.imf", "*.plt", "*.hpgl", "*.hpg", "*.rml", "*.prn"));
        if (!files.isEmpty()) {
            openGCodeQueue(files, 0);
        }
    }

    private void openGCodeQueue(List<File> files, int index) {
        if (index >= files.size()) {
            updateProgress(1);
            setStatus("Concluido.", IDLE_COLOR);
            onJobFinished();
            return;
        }
        File file = files.get(index);
        String message = "Lendo G-code " + (index + 1) + "/" + files.size() + ": " + file.getName() + "...";
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
                        "Pre-visualizacao indisponivel: " + invalidCode.getMessage(),
                        (int) text.lines().count(), "MM");
            }
            return new ImportedGCode(text, preview);
        }, (fraction, progressMessage) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(progressMessage);
        }));
        runningJob = handle;
        handle.completion().thenAccept(imported -> Platform.runLater(() -> {
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
            appendConsole("G-code aberto: " + file.getName() + " (" + preview.lineCount() + " linhas).");
            openGCodeQueue(files, index + 1);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao abrir G-code " + file.getName() + ": ");
                if (isCancellation(error)) {
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
            setStatus("Concluido.", IDLE_COLOR);
            onJobFinished();
            return;
        }
        File file = files.get(index);
        String message = "Analisando Gerber " + (index + 1) + "/" + files.size() + ": " + file.getName() + "...";
        if (index == 0) {
            beginJob(message);
        } else {
            setStatus(message, RUNNING_COLOR);
            appendConsole(message);
        }
        JobHandle<GerberImage> handle = jobExecutor.submit(context ->
                new GerberParser().parse(file.toPath(), context::isCancelled,
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
                            "Gerber OK: %d aperturas, area=%.4f, bounds=%s",
                            image.apertures().size(), image.totalArea(), Arrays.toString(image.bounds())));
                    TreeItem<String> item = addGerberToProject(file, image);
                    plotAreaView.fitToLayer(item);
                    openGerberQueue(files, index + 1);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir Gerber " + file.getName() + ": ");
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
        List<File> files = pickCamFiles("Abrir Excellon (prototipo)",
                new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
        if (!files.isEmpty()) {
            openExcellonQueue(files, 0);
        }
    }

    private void openExcellonQueue(List<File> files, int index) {
        if (index >= files.size()) {
            updateProgress(1);
            setStatus("Concluido.", IDLE_COLOR);
            onJobFinished();
            return;
        }
        File file = files.get(index);
        String message = "Analisando Excellon " + (index + 1) + "/" + files.size() + ": " + file.getName() + "...";
        if (index == 0) {
            beginJob(message);
        } else {
            setStatus(message, RUNNING_COLOR);
            appendConsole(message);
        }
        JobHandle<ExcellonImage> handle = jobExecutor.submit(context ->
                new ExcellonParser().parse(file.toPath(), context::isCancelled,
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
                            "Excellon OK: %d ferramentas, %d furos, %d slots, bounds=%s",
                            image.toolDiameters().size(), image.totalDrills(), image.totalSlots(),
                            Arrays.toString(image.bounds())));
                    TreeItem<String> item = addExcellonToProject(file, image);
                    plotAreaView.fitToLayer(item);
                    openExcellonQueue(files, index + 1);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir Excellon " + file.getName() + ": ");
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
        List<File> files = pickCamFiles("Importar " + format + " como " + (asGerber ? "Gerber" : "Geometry"), filter);
        if (files.isEmpty()) {
            return;
        }
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
        beginJob("Importando " + format + "...");
        JobHandle<List<ImportedDrawing>> handle = jobExecutor.submit(context -> {
            List<ImportedDrawing> imported = new ArrayList<>();
            for (int index = 0; index < files.size(); index++) {
                File file = files.get(index);
                context.reportProgress((double) index / files.size(), "Importando " + file.getName() + "...");
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
                    appendConsole("Falha ao importar " + fileName + ": " + drawing.error());
                    continue;
                }
                if (asGerber && drawing.copper().isEmpty()) {
                    appendConsole("Falha ao importar " + fileName + ": nenhuma area fechada"
                            + (dxf ? "." : " nem linha com espessura de traco."));
                    continue;
                }
                if (drawing.pens() != null && drawing.pens().size() > 1) {
                    for (Map.Entry<Integer, Geometry> pen : drawing.pens().entrySet()) {
                        String penName = uniqueDerivedName(fileName + "_P" + pen.getKey());
                        last = addGeometryToProject(penName, fileName, units, pen.getValue(), true);
                        appendConsole(format + " importado como Geometry: " + penName + " (caneta "
                                + pen.getKey() + ", " + pen.getValue().getNumGeometries() + " caminhos)");
                    }
                    continue;
                }
                String name = uniqueDerivedName(fileName);
                last = asGerber
                        ? addGerberToProject(name, drawing.file().toPath(),
                                GerberImage.of(units, Map.of(), drawing.copper(), null, Map.of()))
                        : addGeometryToProject(name, fileName, units, drawing.shapes(), true);
                appendConsole(format + " importado como " + (asGerber ? "Gerber" : "Geometry") + ": " + name
                        + " (" + drawing.shapes().getNumGeometries() + " formas)");
                if (drawing.skippedText() > 0) {
                    appendConsole("  " + drawing.skippedText() + " texto(s) ignorado(s) - converta o texto em "
                            + switch (drawingFormat) {
                                case DXF -> "linhas no CAD.";
                                case SVG -> "caminho no editor de SVG.";
                                case HPGL -> "linhas antes de gerar o HPGL.";
                            });
                }
            }
            if (last != null) {
                setDisplayUnits(units);
                plotAreaView.fitToLayer(last);
                selectProjectItem(last);
            }
            updateProgress(1);
            setStatus(last != null ? "Concluido." : "Falhou.", last != null ? IDLE_COLOR : ERROR_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao importar " + format + ": ");
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
        List<File> files = pickCamFiles("Importar PDF", new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        if (files.isEmpty()) {
            return;
        }
        String units = plotAreaView.units();
        beginJob("Importando PDF...");
        JobHandle<List<ImportedPdf>> handle = jobExecutor.submit(context -> {
            List<ImportedPdf> imported = new ArrayList<>();
            for (int index = 0; index < files.size(); index++) {
                File file = files.get(index);
                context.reportProgress((double) index / files.size(), "Importando " + file.getName() + "...");
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
                    appendConsole("Falha ao importar " + fileName + ": " + pdf.error());
                    continue;
                }
                PdfImporter.Result result = pdf.result();
                if (result.drills() != null) {
                    String name = uniqueDerivedName(fileName + "_0");
                    last = addExcellonToProject(name, pdf.file().toPath(), result.drills());
                    appendConsole("PDF importado como Excellon: " + name + " ("
                            + result.drills().totalDrills() + " furos)");
                }
                for (int layer = 0; layer < result.layers().size(); layer++) {
                    String name = uniqueDerivedName(fileName + "_" + (layer + 1));
                    last = addGerberToProject(name, pdf.file().toPath(), result.layers().get(layer));
                    appendConsole("PDF importado como Gerber: " + name);
                }
            }
            if (last != null) {
                setDisplayUnits(units);
                plotAreaView.fitToLayer(last);
                selectProjectItem(last);
            }
            updateProgress(1);
            setStatus(last != null ? "Concluido." : "Falhou.", last != null ? IDLE_COLOR : ERROR_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao importar PDF: ");
                onJobFinished();
            });
            return null;
        });
    }

    /** Shared "pick fabrication files" flow (multi-select): remembers the last folder across both Gerber and Excellon. */
    private List<File> pickCamFiles(String title, FileChooser.ExtensionFilter filter) {
        if (runningJob != null) {
            appendConsole("Ja ha um job em andamento.");
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
        progressBar.setProgress(fraction);
        if (Double.isNaN(fraction) || fraction < 0) {
            progressPercentLabel.setText("...");
            return;
        }
        double bounded = Math.max(0, Math.min(1, fraction));
        progressPercentLabel.setText(Math.round(bounded * 100) + "%");
    }

    private void reportJobError(Throwable error, String failurePrefix) {
        if (isCancellation(error)) {
            setStatus("Cancelado.", CANCELLED_COLOR);
            appendConsole("Operacao cancelada.");
        } else {
            setStatus("Falhou.", ERROR_COLOR);
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
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        if (gcodeEditor.isActive() || gerberEditor.isActive() || geometryEditor.isActive()
                || excellonEditor.isActive()) {
            appendConsole("Aplique ou cancele o editor antes de salvar o projeto.");
            return;
        }
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
                        entry.getValue().gcode(), isObjectVisible(entry.getKey())))
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
                    geometry.cncDefaults(), geometryCncSettingsByItem.get(item)));
        }
        ProjectFile project = new ProjectFile(gerbers, excellons, geometries, jobs,
                currentProjectImportWarnings);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Projeto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto FlatCAM FX", "*.fcnproj"));
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showSaveDialog(scene.getWindow());
        if (file == null) {
            return;
        }
        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj")) {
            appendConsole("Salvar no formato .FlatPrj do Python ainda nao e suportado. Use .fcnproj.");
            return;
        }

        beginJob("Salvando projeto " + file.getName() + "...");
        // Serialization and XZ compression can take time now that Gerber
        // projects retain every individual flash/stroke for editing.
        // The writer publishes atomically; there is no cooperative cancel
        // point inside compression, so do not offer a misleading Cancel button.
        cancelJobButton.setDisable(true);
        JobHandle<Path> handle = jobExecutor.submit(context -> {
            context.reportProgress(Double.NaN, "Serializando projeto...");
            ProjectFileIO.save(project, file.toPath());
            context.reportProgress(1, "Projeto salvo.");
            return file.toPath();
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;
        handle.completion().thenAccept(saved -> Platform.runLater(() -> {
            AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
            appendConsole("Projeto salvo em " + saved);
            updateProgress(1);
            setStatus("Projeto salvo.", IDLE_COLOR);
            onJobFinished();
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                reportJobError(error, "Falha ao salvar projeto: ");
                onJobFinished();
            });
            return null;
        });
    }

    /**
     * Loads and validates every referenced file off the JavaFX thread. The visible project is replaced only after the
     * whole CAM batch succeeds, so a malformed/missing source or cancellation leaves the current project untouched.
     */
    private void openProject() {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }
        if (gcodeEditor.hasUnappliedChanges() || gerberEditor.hasUnappliedChanges()
                || geometryEditor.hasUnappliedChanges() || excellonEditor.hasUnappliedChanges()) {
            appendConsole("Aplique ou cancele as alteracoes do editor antes de abrir outro projeto.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir Projeto");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Projetos FlatCAM", "*.fcnproj", "*.FlatPrj"),
                new FileChooser.ExtensionFilter("Projeto FlatCAM FX", "*.fcnproj"),
                new FileChooser.ExtensionFilter("Projeto FlatCAM Python", "*.FlatPrj"));
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showOpenDialog(scene.getWindow());
        if (file == null) {
            return;
        }

        beginJob("Abrindo projeto " + file.getName() + "...");
        JobHandle<LoadedProject> handle = jobExecutor.submit(context -> {
            CancellationToken cancellation = context::isCancelled;
            cancellation.throwIfCancellationRequested();
            context.reportProgress(0.05, "Lendo " + file.getName() + "...");
            long decodeStart = plotAreaView.profilingEnabled() ? System.nanoTime() : 0;
            ProjectFile project = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj")
                    ? PythonProjectIO.load(file.toPath()) : ProjectFileIO.load(file.toPath());
            plotAreaView.logPerformancePhase("project decode (worker)", decodeStart);
            cancellation.throwIfCancellationRequested();
            context.reportProgress(0.5, "Projeto decodificado.");

            List<LoadedCncJob> cncJobs = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            warnings.addAll(project.importWarnings());
            int total = Math.max(1, project.cncJobs().size());
            int processed = 0;
            for (ProjectFile.CncJobRecord job : project.cncJobs()) {
                cancellation.throwIfCancellationRequested();
                Path outputPath = Path.of(job.outputPath());
                context.reportProgress(0.5 + 0.5 * processed / total, "Lendo G-code " + outputPath.getFileName() + "...");
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
                                        "Analisando G-code " + outputPath.getFileName() + "..."));
                        travel = parsed.travelGeometry();
                        cut = parsed.cutGeometry();
                        travelCenterlines = parsed.travelCenterlines();
                        cutCenterlines = parsed.cutCenterlines();
                        previewStrokeWidth = previewWidthFor(parsed);
                        stats = parsed.stats();
                        units = parsed.plotAvailable() ? parsed.units() : null;
                        if (parsed.warning() != null) {
                            warnings.add("Aviso: " + outputPath.getFileName() + ": " + parsed.warning());
                        }
                    } catch (IllegalArgumentException invalidGcode) {
                        warnings.add("Aviso: pre-visualizacao de " + outputPath.getFileName()
                                + " indisponivel: " + invalidGcode.getMessage());
                    }
                    String name = job.name() != null ? job.name() : outputPath.getFileName().toString();
                    cncJobs.add(new LoadedCncJob(name, job.sourceName(), outputPath, gcode,
                            travel, cut, travelCenterlines, cutCenterlines,
                            previewStrokeWidth, units, job.visible(), stats));
                } catch (IOException e) {
                    warnings.add("Aviso: nao foi possivel ler G-code " + outputPath + ": " + e.getMessage());
                }
                processed++;
            }

            cancellation.throwIfCancellationRequested();
            context.reportProgress(1, "Projeto carregado.");
            return new LoadedProject(project.gerbers(), project.excellons(), project.geometries(),
                    List.copyOf(cncJobs), List.copyOf(warnings), project.importWarnings());
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(project -> Platform.runLater(() -> {
                    long restoreStart = plotAreaView.profilingEnabled() ? System.nanoTime() : 0;
                    plotAreaView.beginBatchUpdate();
                    try {
                        clearProject();
                        currentProjectImportWarnings = project.importWarnings();
                        for (ProjectFile.GerberEntry loaded : project.gerbers()) {
                            TreeItem<String> item = addGerberToProject(loaded.name(), null, loaded.image());
                            applyRestoredGerberState(item, loaded.image(), loaded);
                            setDisplayUnits(loaded.image().units());
                        }
                        for (ProjectFile.ExcellonEntry loaded : project.excellons()) {
                            TreeItem<String> item = addExcellonToProject(loaded.name(), null, loaded.image(),
                                    loaded.drillDefaults());
                            if (loaded.cncSettings() != null) drillCncSettingsByItem.put(item, loaded.cncSettings());
                            applyRestoredExcellonState(item, loaded);
                            setDisplayUnits(loaded.image().units());
                        }
                        for (ProjectFile.GeometryEntry loaded : project.geometries()) {
                            TreeItem<String> item = addGeometryToProject(loaded.name(), loaded.sourceName(),
                                    loaded.units(), loaded.geometry(), loaded.strokeOnly(), loaded.tools(),
                                    loaded.cncDefaults());
                            if (loaded.cncSettings() != null) geometryCncSettingsByItem.put(item, loaded.cncSettings());
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
                            if (!loaded.visible()) setObjectVisible(item, false);
                            if (loaded.units() != null) {
                                setDisplayUnits(loaded.units());
                            }
                        }
                    } finally {
                        plotAreaView.endBatchUpdate();
                    }
                    plotAreaView.logPerformancePhase("project restore (FX thread)", restoreStart);
                    project.warnings().forEach(this::appendConsole);
                    AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
                    appendConsole("Projeto aberto: " + file);
                    if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".flatprj")) {
                        appendConsole("Projeto Python importado. Salvar Projeto grava apenas .fcnproj; "
                                + "o arquivo .FlatPrj original nao sera alterado.");
                    }
                    updateProgress(1);
                    setStatus("Concluido.", IDLE_COLOR);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir projeto: ");
                        appendConsole("O projeto atual foi preservado.");
                        onJobFinished();
                    });
                    return null;
                });
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
        gerberFollowItems.clear();
        sourcePathByItem.clear();
        currentProjectImportWarnings = List.of();
        plotAreaView.clearLayers();
        setDisplayUnits("MM");
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
        return item;
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
        runningJob = null;
        runDemoJobButton.setDisable(false);
        cancelJobButton.setDisable(true);
    }

    private void appendConsole(String line) {
        console.appendText(line + System.lineSeparator());
    }
}
