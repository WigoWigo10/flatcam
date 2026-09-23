package org.flatcam.fx;

import java.io.File;
import java.io.IOException;
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
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CancellationException;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
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
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutResult;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.CncJobResult;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberGeometryGenerator;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationResult;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccParameters;
import org.flatcam.cam.ncc.NccResult;
import org.flatcam.cam.transform.TransformOp;
import org.flatcam.cam.transform.TransformReference;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * Shell shape taken from the legacy app, not from CONTEXTO_FLATCAM_FX.md's
 * secao 6 sketch - see UI_INVENTORY.md. FlatCAM/PyQt5 barely opens separate
 * windows: a left tab strip alternates Project/Properties/Tool in the same
 * space, and a center tab strip has a non-closable "Plot Area" (viewport)
 * plus auxiliary tabs (Preferences, Tools Database, editors, ...) opened on
 * demand and reused rather than duplicated. This class replicates that
 * shape; the two auxiliary tab actions here are placeholders demonstrating
 * the open/reuse/focus pattern, not real Preferences/Tools Database screens.
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

    private final JobExecutor jobExecutor;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressPercentLabel = new Label("0%");
    private final Label statusLabel = new Label("Pronto.");
    private final Circle statusDot = new Circle(5, Color.web("#4caf50"));
    private final Label unitsLabel = new Label("Unidades: -");
    private final Button runDemoJobButton = new Button();
    private final Button cancelJobButton = new Button();
    private final TextArea console = new TextArea();
    private final TabPane centerTabs = new TabPane();
    private final StackPane propertiesContainer = new StackPane();
    private final Label propertiesPlaceholder = new Label("Selecione um objeto\npara ver seus parametros.");

    /**
     * A generated G-code file, tracked in the "CNC Jobs" tree category once GCodeGenerator
     * writes one. travelGeometry/cutGeometry are null when reopening a saved project (the
     * lightweight .fcnproj format only stores the G-code file path, not its toolpath geometry -
     * see ProjectFile's doc - so a reloaded CNC Job has no plot until regenerated).
     */
    private record CncJobEntry(String sourceName, Path outputFile, String gcode, Geometry travelGeometry, Geometry cutGeometry) {
    }

    private record IsolationJobOutput(IsolationResult toolpath, CncJobResult cncJob) {
    }

    private record CutoutJobOutput(CutoutResult toolpath, CncJobResult cncJob) {
    }

    private record LoadedCncJob(String sourceName, Path outputPath, String gcode) {
    }

    /** {@code tools} is empty for a plain single-purpose Geometry (no tool association); see NccToolPanel's doc. */
    private record GeometryEntry(String sourceName, String units, Geometry geometry,
                                 boolean strokeOnly, List<ToolGeometry> tools) {
    }

    /** Gerber/Excellon entries already carry their own fully-resolved geometry (ProjectFileIO), no re-parsing needed. */
    private record LoadedProject(List<ProjectFile.GerberEntry> gerbers, List<ProjectFile.ExcellonEntry> excellons,
                                 List<LoadedCncJob> cncJobs, List<String> warnings) {
    }

    /** PlotAreaView layer keys for a CNC Job's two toolpath layers - see {@link #addCncJobToProject}. */
    private record CncTravelLayerKey(TreeItem<String> cncJobItem) {
    }

    private record CncCutLayerKey(TreeItem<String> cncJobItem) {
    }

    /** PlotAreaView layer key for the apertures table's "Mark" highlight overlay - see {@link GerberAperturesTable}. */
    private record MarkLayerKey(TreeItem<String> gerberItem) {
    }

    /** Files opened/generated so far, keyed by their tree item - back the Properties tab and the item context menu. */
    private final Map<TreeItem<String>, GerberImage> gerberByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, ExcellonImage> excellonByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, GeometryEntry> geometryByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, CncJobEntry> cncJobByItem = new LinkedHashMap<>();
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
                public void closeToolPanel() {
                    MainWindow.this.closeToolPanel();
                }

                @Override
                public void setObjectVisible(TreeItem<String> item, boolean visible) {
                    MainWindow.this.setObjectVisible(item, visible);
                }

                @Override
                public void addEditedGerber(String name, GerberImage image) {
                    addGerberToProject(name, null, image);
                }

                @Override
                public void log(String message) {
                    appendConsole(message);
                }
            });

    private Scene scene;
    private SplitPane horizontalSplit;
    private SplitPane verticalSplit;
    private TreeView<String> projectTree;
    private TabPane leftTabs;
    private Tab propertiesTab;
    private Tab toolTab;
    private TreeItem<String> gerbersNode;
    private TreeItem<String> excellonNode;
    private TreeItem<String> geometryNode;
    private TreeItem<String> cncJobsNode;
    private VBox bottomPanel;
    private double dividerBeforeConsoleCollapse = 0.75;
    private boolean consoleCollapsed = !AppPreferences.loadConsoleOpen(true);
    private ThemeOption currentTheme = AppPreferences.loadTheme(ThemeOption.CUSTOM_LIGHT);
    private JobHandle<?> runningJob;

    MainWindow(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
    }

    Scene createScene() {
        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        root.setCenter(buildMainSplit());
        root.setBottom(buildStatusBar());

        scene = new Scene(root);
        currentTheme.applyTo(scene);
        plotAreaView.applyTheme(currentTheme);
        return scene;
    }

    /** Called by MainApp on window close - split-divider positions have no natural "save now" moment otherwise. */
    void saveSplitPositions() {
        if (consoleCollapsed) {
            return; // the collapsed (~1.0) position is not a real layout preference - see toggleConsole().
        }
        AppPreferences.saveSplitPositions(horizontalSplit.getDividerPositions()[0], verticalSplit.getDividerPositions()[0]);
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

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("Arquivo");
        MenuItem openProjectItem = new MenuItem("Abrir Projeto...");
        openProjectItem.setOnAction(e -> openProject());
        MenuItem saveProjectItem = new MenuItem("Salvar Projeto...");
        saveProjectItem.setOnAction(e -> saveProject());
        MenuItem openGerberItem = new MenuItem("Abrir Gerber (prototipo Fase 3)");
        openGerberItem.setOnAction(e -> openGerberPrototype());
        MenuItem openExcellonItem = new MenuItem("Abrir Excellon (prototipo Fase 4)");
        openExcellonItem.setOnAction(e -> openExcellonPrototype());
        MenuItem runDemoJob = new MenuItem("Executar job de demonstracao");
        runDemoJob.setOnAction(e -> runDemoJob());
        MenuItem exitItem = new MenuItem("Sair");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(
                openProjectItem, saveProjectItem, new SeparatorMenuItem(),
                openGerberItem, openExcellonItem, runDemoJob, new SeparatorMenuItem(), exitItem);

        Menu editMenu = new Menu("Editar");
        MenuItem preferencesItem = new MenuItem("Preferencias");
        preferencesItem.setOnAction(e -> openAuxiliaryTab("Preferencias", this::buildPreferencesPlaceholder));
        editMenu.getItems().add(preferencesItem);

        Menu optionsMenu = new Menu("Opcoes");
        MenuItem toolsDbItem = new MenuItem("Tools Database");
        toolsDbItem.setOnAction(e -> openAuxiliaryTab("Tools Database", this::buildToolsDbPlaceholder));
        // Python launches this from a toolbar icon (Alt+C) rather than a menu (appGUI/MainGUI.py's
        // calculators_btn) - this app doesn't have that full tools toolbar yet, so a menu item is
        // the reasonable equivalent entry point. Unlike Isolation/Drilling, it needs no selected
        // object at all.
        MenuItem calculatorsItem = new MenuItem("Calculators");
        calculatorsItem.setOnAction(e -> openToolPanel("Calculators", CalculatorsPanel.build()));

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

        optionsMenu.getItems().addAll(toolsDbItem, calculatorsItem, new SeparatorMenuItem(),
                rotateItem, skewXItem, skewYItem, flipXItem, flipYItem);

        Menu viewMenu = new Menu("Exibir");
        viewMenu.getItems().add(buildThemeMenu());

        Menu helpMenu = new Menu("Ajuda");
        MenuItem aboutItem = new MenuItem("Sobre");
        aboutItem.setOnAction(e -> appendConsole("FlatCAM Next - esqueleto Fase 1 (CONTEXTO_FLATCAM_FX.md)"));
        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, editMenu, optionsMenu, viewMenu, helpMenu);
    }

    /**
     * Both theme families side by side (CSS puro / AtlantaFX), light and
     * dark in each, one ToggleGroup shared across both submenus so only one
     * option is ever selected at a time.
     */
    private Menu buildThemeMenu() {
        ToggleGroup themeGroup = new ToggleGroup();

        Menu customMenu = new Menu("CSS puro");
        customMenu.getItems().addAll(
                themeItem(ThemeOption.CUSTOM_LIGHT, themeGroup),
                themeItem(ThemeOption.CUSTOM_DARK, themeGroup)
        );

        Menu atlantaFxMenu = new Menu("AtlantaFX");
        atlantaFxMenu.getItems().addAll(
                themeItem(ThemeOption.ATLANTAFX_LIGHT, themeGroup),
                themeItem(ThemeOption.ATLANTAFX_DARK, themeGroup)
        );

        Menu themeMenu = new Menu("Tema");
        themeMenu.getItems().addAll(customMenu, atlantaFxMenu);
        return themeMenu;
    }

    /** Selection reflects (and, on change, persists) {@link #currentTheme} - loaded from AppPreferences at construction. */
    private RadioMenuItem themeItem(ThemeOption option, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(option.label());
        item.setToggleGroup(group);
        item.setSelected(option == currentTheme);
        item.setOnAction(e -> {
            option.applyTo(scene);
            currentTheme = option;
            plotAreaView.applyTheme(option);
            AppPreferences.saveTheme(option);
            // Tree cells cache their graphic nodes; rebuild them so dark-only
            // icon outlines appear/disappear immediately with the theme.
            if (projectTree != null) {
                projectTree.refresh();
            }
        });
        return item;
    }

    /** Icon-only buttons with tooltips, matching the legacy app's toolbar convention (UI_INVENTORY.md section 1). */
    private ToolBar buildToolBar() {
        Button openGerberButton = new Button(null, Icons.folderOpen(16));
        openGerberButton.setTooltip(new Tooltip("Abrir Gerber (prototipo Fase 3)"));
        openGerberButton.setOnAction(e -> openGerberPrototype());

        Button openExcellonButton = new Button(null, Icons.drill(16));
        openExcellonButton.setTooltip(new Tooltip("Abrir Excellon (prototipo Fase 4)"));
        openExcellonButton.setOnAction(e -> openExcellonPrototype());

        runDemoJobButton.setGraphic(Icons.play(16));
        runDemoJobButton.setTooltip(new Tooltip("Executar job de demonstracao"));
        runDemoJobButton.setOnAction(e -> runDemoJob());

        cancelJobButton.setGraphic(Icons.stop(16));
        cancelJobButton.setTooltip(new Tooltip("Cancelar"));
        cancelJobButton.setDisable(true);
        cancelJobButton.setOnAction(e -> cancelDemoJob());

        return new ToolBar(openGerberButton, openExcellonButton, new Separator(), runDemoJobButton, cancelJobButton);
    }

    /**
     * A fixed status bar at the very bottom of the window (appGUI/MainGUI.py's
     * statusBar(), UI_INVENTORY.md section 1) - separate from the resizable
     * job progress/console panel, always visible regardless of which tabs are
     * open. The units label reflects the last-opened Gerber's real units;
     * everything else the legacy status bar shows (grid, workspace size, HUD
     * toggles) needs state this phase doesn't have yet.
     *
     * <p>The console/shell toggle lives here, not the top toolbar - confirmed
     * against appGUI/MainGUI.py: shell_status_label is a clickable icon added
     * to status_toolbar, which is added to infobar (the bottom status bar),
     * not any top toolbar.
     */
    private HBox buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton consoleToggle = new ToggleButton(null, Icons.terminal(14));
        consoleToggle.setTooltip(new Tooltip("Mostrar/ocultar console"));
        consoleToggle.getStyleClass().add("status-bar-toggle");
        consoleToggle.setSelected(!consoleCollapsed);
        consoleToggle.setOnAction(e -> toggleConsole(consoleToggle.isSelected()));

        HBox bar = new HBox(6, statusDot, statusLabel, spacer, consoleToggle, unitsLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void setStatus(String text, Color dotColor) {
        statusLabel.setText(text);
        statusDot.setFill(dotColor);
    }

    private SplitPane buildMainSplit() {
        horizontalSplit = new SplitPane(buildLeftTabs(), buildCenterTabs());
        bottomPanel = buildBottomPanel();
        // Starts without the bottom panel at all (not just visually collapsed) if the
        // console was closed last session - same "remove from items" mechanism toggleConsole()
        // uses, just applied before the first layout instead of via a later user click.
        verticalSplit = consoleCollapsed ? new SplitPane(horizontalSplit) : new SplitPane(horizontalSplit, bottomPanel);
        verticalSplit.setOrientation(Orientation.VERTICAL);

        // SplitPane does not reliably honor a divider position set before its first
        // layout pass - measured drift on a loaded 0.55: it settled at ~0.51 by the next
        // launch, because the save listener (attached below) caught that settling as if
        // it were a real change and persisted it. Deferring the initial set past the
        // first layout, then attaching the listener, avoids that drift entirely.
        Platform.runLater(() -> {
            horizontalSplit.setDividerPositions(AppPreferences.loadSplitHorizontal(0.22));
            horizontalSplit.getDividers().get(0).positionProperty()
                    .addListener((obs, oldVal, newVal) -> saveSplitPositions());
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
        Tab projectTab = new Tab("Projeto", buildProjectTree());
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
    private void openToolPanel(String label, Node content) {
        toolTab.setText(label);
        ScrollPane scroll = new ScrollPane(content);
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
                    menu.show(cell, event.getScreenX(), event.getScreenY());
                }
                event.consume();
            });
            return cell;
        });
        return projectTree;
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

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Scale:"), scaleField, scaleButton);
        grid.addRow(1, new Label("Offset X,Y:"), offsetXField, offsetYField, offsetButton);
        return new VBox(6, grid, transformationsButton);
    }

    /** One object's share of {@link #applyTransformToSelection} - also used directly by each object's own mini "Transformations" panel. */
    private boolean applyTransformToItem(TreeItem<String> item, TransformOp op) {
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
                    op.apply(geometry.geometry()), geometry.strokeOnly(), newTools);
            geometryByItem.put(item, transformed);
            plotAreaView.updateLayerGeometry(item, transformed.geometry());
        } else {
            return false;
        }
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
        } else {
            plotAreaView.setLayerVisible(item, visible);
        }
        // The tree doesn't otherwise know PlotAreaView's layer visibility changed - see
        // refreshDisplay()'s dimming of disabled rows (ObjectCollection.py's own
        // data()/Qt.ForegroundRole, which reads obj.options['plot'] the same way).
        projectTree.refresh();
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
     * its two available CNC workflows grouped under "Criar CNC Job".
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

        MenuItem isolationItem = new MenuItem("Gerar Isolamento...");
        setLegacyMenuIcon(isolationItem, "iso_16.png");
        isolationItem.setOnAction(e -> generateIsolation(item, image));

        MenuItem cutoutItem = new MenuItem("Cutout Tool...");
        setLegacyMenuIcon(cutoutItem, "cut32_bis.png");
        cutoutItem.setOnAction(e -> generateCutout(item, image));

        Menu createCncMenu = new Menu("Criar CNC Job");
        setLegacyMenuIcon(createCncMenu, "cnc32.png");
        createCncMenu.getItems().addAll(isolationItem, cutoutItem);

        MenuItem viewSourceItem = new MenuItem("Ver Fonte");
        setLegacyMenuIcon(viewSourceItem, "source32.png");
        viewSourceItem.setOnAction(e -> viewObjectSource(item));

        MenuItem renameItem = new MenuItem("Renomear");
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
                new SeparatorMenuItem(), editItem, createCncMenu, viewSourceItem, renameItem, copyItem, removeItem,
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

        MenuItem gcodeItem = new MenuItem("Criar CNC Job...");
        setLegacyMenuIcon(gcodeItem, "cnc32.png");
        gcodeItem.setOnAction(e -> generateDrillGCode(item, image));

        MenuItem viewSourceItem = new MenuItem("Ver Fonte");
        setLegacyMenuIcon(viewSourceItem, "source32.png");
        viewSourceItem.setOnAction(e -> viewObjectSource(item));

        MenuItem renameItem = new MenuItem("Renomear");
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
                new SeparatorMenuItem(), gcodeItem, viewSourceItem, renameItem, copyItem, removeItem, saveItem,
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

        MenuItem viewItem = new MenuItem("Ver WKT");
        setLegacyMenuIcon(viewItem, "source32.png");
        viewItem.setOnAction(e -> viewObjectSource(item));
        MenuItem renameItem = new MenuItem("Renomear");
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
                new SeparatorMenuItem(), cncItem, viewItem, renameItem, copyItem, removeItem, saveItem,
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
        String resource = currentTheme.isDark() ? "dark/" + fileName : fileName;
        return Icons.fromResource(resource, size);
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

        MenuItem renameItem = new MenuItem("Renomear");
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

        return List.of(showItem, enableItem, disableItem, new SeparatorMenuItem(), viewItem, renameItem, copyItem,
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
        Path sourcePath = sourcePathByItem.get(item);
        if (cncJob == null && geometry == null && sourcePath == null) {
            appendConsole("Nao ha conteudo exportavel para " + item.getValue() + ".");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar objeto como");
        chooser.setInitialFileName(item.getValue());
        if (geometry != null) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Well-Known Text", "*.wkt", "*.txt"));
        } else if (gerberByItem.containsKey(item)) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.cmp", "*.gtl", "*.gbl", "*.gm1", "*.txt"));
        } else if (excellonByItem.containsKey(item)) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
        } else {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
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
            } else if (!sourcePath.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
            }
            File parent = destination.getParentFile();
            if (parent != null) {
                AppPreferences.saveLastCamDirectory(parent.getAbsolutePath());
            }
            appendConsole("Objeto salvo em " + target);
            setStatus("Concluido.", IDLE_COLOR);
        } catch (IOException e) {
            appendConsole("Falha ao salvar " + target + ": " + e.getMessage());
            setStatus("Falhou.", ERROR_COLOR);
        }
    }

    private void copySelection(List<TreeItem<String>> selected) {
        TreeItem<String> lastCopy = null;
        for (TreeItem<String> item : selected) {
            lastCopy = copyObject(item);
        }
        selectProjectItem(lastCopy);
    }

    private TreeItem<String> copyObject(TreeItem<String> sourceItem) {
        String copyName = uniqueCopyName(sourceItem.getValue());
        TreeItem<String> copyItem;
        GerberImage gerber = gerberByItem.get(sourceItem);
        ExcellonImage excellon = excellonByItem.get(sourceItem);
        GeometryEntry geometry = geometryByItem.get(sourceItem);
        CncJobEntry cncJob = cncJobByItem.get(sourceItem);

        if (gerber != null) {
            copyItem = addGerberToProject(copyName, sourcePathByItem.get(sourceItem), gerber);
            copyLayerAppearance(sourceItem, copyItem);
        } else if (excellon != null) {
            copyItem = addExcellonToProject(copyName, sourcePathByItem.get(sourceItem), excellon);
            copyLayerAppearance(sourceItem, copyItem);
        } else if (geometry != null) {
            copyItem = addGeometryToProject(copyName, geometry.sourceName(), geometry.units(),
                    geometry.geometry().copy(), geometry.strokeOnly(), geometry.tools());
            copyLayerAppearance(sourceItem, copyItem);
        } else if (cncJob != null) {
            copyItem = addCncJobToProject(copyName, cncJob.sourceName(), cncJob.outputFile(), cncJob.gcode(),
                    cncJob.travelGeometry(), cncJob.cutGeometry());
            copyLayerAppearance(new CncTravelLayerKey(sourceItem), new CncTravelLayerKey(copyItem));
            copyLayerAppearance(new CncCutLayerKey(sourceItem), new CncCutLayerKey(copyItem));
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
        TreeItem<String> item = new TreeItem<>(outputFileName);
        cncJobByItem.put(item, new CncJobEntry(sourceName, outputFile, gcode, travelGeometry, cutGeometry));
        cncJobsNode.getChildren().add(item);
        if (cutGeometry != null && !cutGeometry.isEmpty()) {
            plotAreaView.putLayer(new CncCutLayerKey(item), PlotAreaView.LayerCategory.CNCJOB,
                    cutGeometry, CNC_CUT_FILL, CNC_CUT_STROKE, false);
        }
        if (travelGeometry != null && !travelGeometry.isEmpty()) {
            // Put after cut so it draws on top within the CNCJOB category, matching
            // camlib.py's CNCjob.plot2() (cut layer 1, travel layer 2).
            plotAreaView.putLayer(new CncTravelLayerKey(item), PlotAreaView.LayerCategory.CNCJOB,
                    travelGeometry, CNC_TRAVEL_FILL, CNC_TRAVEL_STROKE, false);
        }
        return item;
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
        openToolPanel("Drilling Tool", DrillGCodeToolPanel.build(image,
                result -> runDrillGCodeGeneration(item, image, result), this::closeToolPanel));
    }

    private void runDrillGCodeGeneration(TreeItem<String> item, ExcellonImage image, DrillGCodeToolPanel.Result result) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de furacao");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_drill.nc");
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
            CncJobResult job = GCodeGenerator.generateDrillCncJob(image, result.params(), result.selectedToolIds());
            Files.writeString(outFile.toPath(), job.gcode());
            AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
            appendConsole("G-code de furacao salvo em " + outFile + " (" + job.gcode().lines().count() + " linhas).");
            addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), job.gcode(),
                    job.travelGeometry(), job.cutGeometry());
            closeToolPanel();
        } catch (Exception e) {
            appendConsole("Falha ao gerar/salvar G-code: " + e.getMessage());
        }
    }

    /**
     * Loads IsolationToolPanel into the Tool tab (appTools/ToolIsolation.py's
     * run() switches app.ui.tool_tab to its own UI the same way - see
     * {@link #openToolPanel}). Once "Gerar" is clicked, generates an
     * isolation toolpath around the Gerber's copper (IsolationGenerator,
     * ported from appTools/ToolIsolation.py + camlib.py's
     * Gerber.isolation_geometry() - see its class doc for exactly what was
     * and wasn't carried over), generates its G-code and writes it in one
     * cancellable background job. The resulting CNC Job supplies the visible
     * toolpath layer, avoiding a duplicate preview overlay.
     */
    private void generateIsolation(TreeItem<String> item, GerberImage image) {
        openToolPanel("Isolation Tool", IsolationToolPanel.build(image.units(),
                params -> runIsolationGeneration(item, image, params), this::closeToolPanel));
    }

    private void runIsolationGeneration(TreeItem<String> item, GerberImage image, IsolationToolPanel.Result params) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de isolamento");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_isolation.nc");
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File outFile = chooser.showSaveDialog(scene.getWindow());
        if (outFile == null) {
            return;
        }

        beginJob("Gerando isolamento...");
        JobHandle<IsolationJobOutput> handle = jobExecutor.submit(context -> {
            CancellationToken cancellation = context::isCancelled;
            IsolationResult isolation = IsolationGenerator.generate(
                    image.units(), image.solidGeometry(), params.geometryParams(), cancellation);
            context.reportProgress(0.5, "Gerando G-code de isolamento...");
            if (isolation.isEmpty()) {
                return new IsolationJobOutput(isolation, null);
            }
            CncJobResult job = GCodeGenerator.generateIsolationCncJob(
                    isolation, params.gcodeParams(), params.geometryParams().toolDiameter(), cancellation);
            context.checkCancelled();
            context.reportProgress(0.9, "Salvando G-code de isolamento...");
            Files.writeString(outFile.toPath(), job.gcode());
            return new IsolationJobOutput(isolation, job);
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(output -> Platform.runLater(() -> {
                    if (output.toolpath().isEmpty()) {
                        appendConsole("Isolamento nao gerou nenhum anel (geometria de cobre vazia?).");
                    } else {
                        appendConsole(String.format("Isolamento: %d aneis, comprimento total=%.4f, bounds=%s",
                                output.toolpath().ringCount(), output.toolpath().totalLength(),
                                Arrays.toString(output.toolpath().bounds())));
                        CncJobResult job = output.cncJob();
                        AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
                        appendConsole("G-code de isolamento salvo em " + outFile
                                + " (" + job.gcode().lines().count() + " linhas).");
                        addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), job.gcode(),
                                job.travelGeometry(), job.cutGeometry());
                        closeToolPanel();
                    }
                    updateProgress(1);
                    setStatus("Concluido.", IDLE_COLOR);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao gerar/salvar G-code de isolamento: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /**
     * Loads CutoutToolPanel into the Tool tab (appTools/ToolCutOut.py's
     * run() switches app.ui.tool_tab to its own UI the same way - see
     * {@link #openToolPanel}). Once either "Gerar" button is clicked,
     * generates a board-cutout toolpath around the Gerber's outline
     * (CutoutGenerator, ported from appTools/ToolCutOut.py - see its class
     * doc for exactly what was and wasn't carried over: only the automatic
     * Bridge gap patterns, no Thin/M-Bites, no manual click-to-place gaps,
     * and no intermediate Geometry object - straight to G-code, same as
     * Isolation Routing), then stores the resulting cancellable background
     * job as a CNC Job with its own visible toolpath.
     */
    private void generateCutout(TreeItem<String> item, GerberImage image) {
        openToolPanel("Cutout Tool", CutoutToolPanel.build(image.units(),
                result -> runCutoutGeneration(item, image, result), this::closeToolPanel));
    }

    private void runCutoutGeneration(TreeItem<String> item, GerberImage image, CutoutToolPanel.Result result) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de cutout");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_cutout.nc");
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File outFile = chooser.showSaveDialog(scene.getWindow());
        if (outFile == null) {
            return;
        }

        beginJob("Gerando cutout...");
        JobHandle<CutoutJobOutput> handle = jobExecutor.submit(context -> {
            CancellationToken cancellation = context::isCancelled;
            CutoutResult cutout = CutoutGenerator.generate(
                    image.units(), image.solidGeometry(), result.cutoutParams(), cancellation);
            context.reportProgress(0.5, "Gerando G-code de cutout...");
            if (cutout.isEmpty()) {
                return new CutoutJobOutput(cutout, null);
            }
            CncJobResult job = GCodeGenerator.generateCutoutCncJob(
                    cutout, result.gcodeParams(), result.cutoutParams().toolDiameter(), cancellation);
            context.checkCancelled();
            context.reportProgress(0.9, "Salvando G-code de cutout...");
            Files.writeString(outFile.toPath(), job.gcode());
            return new CutoutJobOutput(cutout, job);
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(output -> Platform.runLater(() -> {
                    if (output.toolpath().isEmpty()) {
                        appendConsole("Cutout nao gerou nenhum caminho (geometria de cobre vazia?).");
                    } else {
                        appendConsole(String.format("Cutout: %d caminhos, comprimento total=%.4f, bounds=%s",
                                output.toolpath().partCount(), output.toolpath().totalLength(),
                                Arrays.toString(output.toolpath().bounds())));
                        CncJobResult job = output.cncJob();
                        AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
                        appendConsole("G-code de cutout salvo em " + outFile
                                + " (" + job.gcode().lines().count() + " linhas).");
                        addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), job.gcode(),
                                job.travelGeometry(), job.cutGeometry());
                        closeToolPanel();
                    }
                    updateProgress(1);
                    setStatus("Concluido.", IDLE_COLOR);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao gerar/salvar G-code de cutout: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** One tool's contribution plus, optionally, the NCC-wide diameter-validity check - see NccToolPanel.Result. */
    private record NccJobOutcome(NccResult result, OptionalDouble minCopperClearance) {
    }

    /** Opens the legacy-style NCC form; unlike Isolation/Cutout, NCC produces an intermediate Geometry object. */
    private void generateNcc(TreeItem<String> item, GerberImage image) {
        List<NccToolPanel.ReferenceCandidate> referenceCandidates = new ArrayList<>();
        for (Map.Entry<TreeItem<String>, GerberImage> entry : gerberByItem.entrySet()) {
            if (entry.getKey() != item) {
                referenceCandidates.add(new NccToolPanel.ReferenceCandidate(
                        entry.getKey().getValue(), true, entry.getValue().solidGeometry()));
            }
        }
        for (Map.Entry<TreeItem<String>, GeometryEntry> entry : geometryByItem.entrySet()) {
            referenceCandidates.add(new NccToolPanel.ReferenceCandidate(
                    entry.getKey().getValue(), false, entry.getValue().geometry()));
        }
        openToolPanel("NCC Tool", NccToolPanel.build(image.units(), referenceCandidates,
                result -> runNccGeneration(item, image, result), this::closeToolPanel));
    }

    private void runNccGeneration(TreeItem<String> item, GerberImage image, NccToolPanel.Result panelResult) {
        if (runningJob != null) {
            appendConsole("Ja existe uma operacao em andamento.");
            return;
        }

        NccParameters params = panelResult.parameters();
        beginJob("Gerando Non-Copper Clearing...");
        JobHandle<NccJobOutcome> handle = jobExecutor.submit(context -> {
            OptionalDouble minClearance = panelResult.checkValidity()
                    ? NccGenerator.minimumCopperClearance(image.solidGeometry())
                    : OptionalDouble.empty();
            NccResult result = NccGenerator.generate(image.units(), image.solidGeometry(), params,
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
                    NccResult result = outcome.result();
                    outcome.minCopperClearance().ifPresent(minClearance -> {
                        boolean anySuitable = params.toolDiameters().stream().anyMatch(d -> d <= minClearance);
                        appendConsole(String.format(java.util.Locale.ROOT,
                                anySuitable
                                        ? "Verificacao de validade: ao menos uma ferramenta consegue fazer isolamento completo (distancia minima de cobre = %.4f)."
                                        : "Verificacao de validade: nenhuma ferramenta selecionada consegue fazer isolamento completo (distancia minima de cobre = %.4f).",
                                minClearance));
                    });
                    if (result.isEmpty()) {
                        appendConsole("NCC nao gerou caminhos. A ferramenta pode ser grande demais para a area livre.");
                        setStatus("Sem caminhos.", ERROR_COLOR);
                    } else {
                        String name = uniqueDerivedName(item.getValue() + "_ncc");
                        List<ToolGeometry> tools = result.toolResults().stream()
                                .filter(toolResult -> !toolResult.isEmpty())
                                .map(toolResult -> new ToolGeometry(toolResult.toolDiameter(), toolResult.geometry()))
                                .toList();
                        TreeItem<String> generated = addGeometryToProject(name, item.getValue(), image.units(),
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
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_cnc.nc");
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
        JobHandle<CncJobResult> handle = jobExecutor.submit(context -> {
            context.reportProgress(0.05, "Ordenando caminhos de Geometry...");
            CncJobResult job = GCodeGenerator.generateGeometryCncJob(entry.units(), result.tools(),
                    result.parameters(), context::isCancelled);
            context.checkCancelled();
            context.reportProgress(0.90, "Salvando G-code de Geometry...");
            Files.writeString(outFile.toPath(), job.gcode());
            return job;
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(job -> Platform.runLater(() -> {
                    AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
                    appendConsole("G-code de Geometry salvo em " + outFile
                            + " (" + job.gcode().lines().count() + " linhas).");
                    TreeItem<String> cncItem = addCncJobToProject(outFile.getName(), item.getValue(),
                            outFile.toPath(), job.gcode(), job.travelGeometry(), job.cutGeometry());
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
        gerberEditor.cancelIfEditing(item);
        item.getParent().getChildren().remove(item);
        byItem.remove(item);
        gerberFollowItems.remove(item);
        sourcePathByItem.remove(item);
        plotAreaView.removeLayer(item);
        plotAreaView.removeLayer(new MarkLayerKey(item));
        plotAreaView.removeLayer(new CncTravelLayerKey(item));
        plotAreaView.removeLayer(new CncCutLayerKey(item));
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
        propertiesContainer.getChildren().setAll(content);
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
        });
        box.getChildren().add(labeledRow("Plot:", plotCb, followCb));

        Button isolationButton = new Button("Isolation Routing");
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
            Geometry union = shapes.size() == 1 ? shapes.get(0) : UnaryUnionOp.union(shapes);
            plotAreaView.putLayer(markKey, PlotAreaView.LayerCategory.OVERLAY, union, MARK_COLOR, MARK_COLOR, false);
        });
    }

    /** Same shape as {@link #buildGerberPropertiesPanel}, minus isolation, plus drilling G-code - see ObjectUI.py's ExcellonObjectUI. */
    private Node buildExcellonPropertiesPanel(TreeItem<String> item, ExcellonImage image) {
        VBox box = objectPropertiesHeader("Excellon Object", DRILL_FILL);

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
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button gcodeButton = new Button("Gerar G-code de furacao...");
        gcodeButton.setMaxWidth(Double.MAX_VALUE);
        gcodeButton.setOnAction(e -> generateDrillGCode(item, image));
        box.getChildren().add(gcodeButton);

        // Read-only per-tool breakdown - ObjectUI.py's tools_table, minus the per-tool
        // "P" plot-visibility checkbox (needs per-tool sub-layers our renderer doesn't
        // have yet) and the milling-conversion buttons (no milling tool ported yet).
        box.getChildren().add(new Label("Tools Table:"));
        box.getChildren().add(DrillGCodeToolPanel.buildToolsTableView(image));

        box.getChildren().add(new Label("Transformations:"));
        box.getChildren().add(transformationsSection(item));

        box.getChildren().add(propertiesSection(String.format(
                "Unidades: %s%nFuros totais: %d%nSlots totais: %d%nBounds: %s",
                image.units(), image.totalDrills(), image.totalSlots(), Arrays.toString(image.bounds())
        )));
        return box;
    }

    private Node buildGeometryPropertiesPanel(TreeItem<String> item, GeometryEntry entry) {
        VBox box = objectPropertiesHeader("Geometry Object", GEOMETRY_STROKE);
        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> setObjectVisible(item, plotCb.isSelected()));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button cncButton = new Button("Generate CNC Job");
        cncButton.setGraphic(legacyIcon("cnc16.png", 16));
        cncButton.setMaxWidth(Double.MAX_VALUE);
        cncButton.setOnAction(e -> generateGeometryCncJob(item, entry));
        box.getChildren().add(cncButton);

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
        VBox box = objectPropertiesHeader("CNC Job Object", ISOLATION_COLOR);
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
            projectTree.refresh(); // see setObjectVisible()'s doc - the tree dims a disabled row's text.
        };
        plotCb.setOnAction(e -> applyVisibility.run());
        kindCombo.setOnAction(e -> applyVisibility.run());

        box.getChildren().add(labeledRow("Plot Kind:", kindCombo));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button viewButton = new Button("Ver G-code");
        viewButton.setMaxWidth(Double.MAX_VALUE);
        viewButton.setOnAction(e -> openAuxiliaryTab(item.getValue(), () -> buildGCodeViewer(entry.gcode())));
        box.getChildren().add(viewButton);
        box.getChildren().add(propertiesSection(String.format(
                "Origem: %s%nArquivo: %s%nLinhas: %d",
                entry.sourceName(), entry.outputFile(), entry.gcode().lines().count()
        )));
        return box;
    }

    private VBox objectPropertiesHeader(String title, Color swatchColor) {
        return objectPropertiesHeader(title, swatchColor, null);
    }

    private VBox objectPropertiesHeader(String title, Color swatchColor, String iconFile) {
        Node leadingGraphic;
        if (iconFile == null) {
            Rectangle swatch = new Rectangle(14, 14, swatchColor);
            swatch.setArcWidth(3);
            swatch.setArcHeight(3);
            leadingGraphic = swatch;
        } else {
            leadingGraphic = legacyIcon(iconFile, 24);
        }
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        HBox header = new HBox(6, leadingGraphic, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, header);
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

    private StackPane buildPreferencesPlaceholder() {
        return centeredPlaceholder("Preferencias\n(placeholder - ver UI_INVENTORY.md secao 4)");
    }

    private StackPane buildToolsDbPlaceholder() {
        return centeredPlaceholder("Tools Database\n(placeholder - ver UI_INVENTORY.md secao 6)");
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
                    unitsLabel.setText("Unidades: " + image.units());
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
                    unitsLabel.setText("Unidades: " + image.units());
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
     * plus which G-code jobs were generated. Unlike a path, embedded
     * geometry survives a save/reload even after an in-memory edit
     * (Transformations) or if the original source file is later moved or
     * deleted.
     */
    private void saveProject() {
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
                    plotAreaView.isLayerMulticolor(item)));
        }
        List<ProjectFile.CncJobRecord> jobs = cncJobByItem.values().stream()
                .map(entry -> new ProjectFile.CncJobRecord(entry.sourceName(), entry.outputFile().toString()))
                .toList();
        ProjectFile project = new ProjectFile(gerbers, excellons, jobs);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Projeto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto FlatCAM Next", "*.fcnproj"));
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showSaveDialog(scene.getWindow());
        if (file == null) {
            return;
        }

        try {
            ProjectFileIO.save(project, file.toPath());
            AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
            appendConsole("Projeto salvo em " + file);
        } catch (IOException e) {
            appendConsole("Falha ao salvar projeto: " + e.getMessage());
        }
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

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir Projeto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto FlatCAM Next", "*.fcnproj"));
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
            ProjectFile project = ProjectFileIO.load(file.toPath());
            cancellation.throwIfCancellationRequested();
            context.reportProgress(0.5, "Projeto decodificado.");

            List<LoadedCncJob> cncJobs = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int total = Math.max(1, project.cncJobs().size());
            int processed = 0;
            for (ProjectFile.CncJobRecord job : project.cncJobs()) {
                cancellation.throwIfCancellationRequested();
                Path outputPath = Path.of(job.outputPath());
                context.reportProgress(0.5 + 0.5 * processed / total, "Lendo G-code " + outputPath.getFileName() + "...");
                try {
                    cncJobs.add(new LoadedCncJob(job.sourceName(), outputPath, Files.readString(outputPath)));
                } catch (IOException e) {
                    warnings.add("Aviso: nao foi possivel ler G-code " + outputPath + ": " + e.getMessage());
                }
                processed++;
            }

            cancellation.throwIfCancellationRequested();
            context.reportProgress(1, "Projeto carregado.");
            return new LoadedProject(project.gerbers(), project.excellons(), List.copyOf(cncJobs), List.copyOf(warnings));
        }, (fraction, message) -> Platform.runLater(() -> {
            updateProgress(fraction);
            statusLabel.setText(message);
        }));
        runningJob = handle;

        handle.completion()
                .thenAccept(project -> Platform.runLater(() -> {
                    clearProject();
                    for (ProjectFile.GerberEntry loaded : project.gerbers()) {
                        TreeItem<String> item = addGerberToProject(loaded.name(), null, loaded.image());
                        applyRestoredGerberState(item, loaded.image(), loaded);
                        unitsLabel.setText("Unidades: " + loaded.image().units());
                    }
                    for (ProjectFile.ExcellonEntry loaded : project.excellons()) {
                        TreeItem<String> item = addExcellonToProject(loaded.name(), null, loaded.image());
                        applyRestoredExcellonState(item, loaded);
                        unitsLabel.setText("Unidades: " + loaded.image().units());
                    }
                    for (LoadedCncJob loaded : project.cncJobs()) {
                        // No toolpath geometry to plot on a reload - see CncJobEntry's doc.
                        addCncJobToProject(loaded.outputPath().getFileName().toString(), loaded.sourceName(),
                                loaded.outputPath(), loaded.gcode(), null, null);
                    }
                    project.warnings().forEach(this::appendConsole);
                    AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
                    appendConsole("Projeto aberto: " + file);
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
        gerberEditor.cancel();
        gerbersNode.getChildren().clear();
        excellonNode.getChildren().clear();
        geometryNode.getChildren().clear();
        cncJobsNode.getChildren().clear();
        gerberByItem.clear();
        excellonByItem.clear();
        geometryByItem.clear();
        cncJobByItem.clear();
        gerberFollowItems.clear();
        sourcePathByItem.clear();
        plotAreaView.clearLayers();
        unitsLabel.setText("Unidades: -");
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
        TreeItem<String> item = new TreeItem<>(displayName);
        excellonByItem.put(item, image);
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
        TreeItem<String> item = new TreeItem<>(displayName);
        geometryByItem.put(item, new GeometryEntry(sourceName, units, geometry, strokeOnly, tools));
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
