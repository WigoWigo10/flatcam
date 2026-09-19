package org.flatcam.fx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationResult;

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
    private static final Color GERBER_FILL = Color.web("#e1a339");
    private static final Color GERBER_STROKE = Color.web("#a06f1f");
    private static final Color DRILL_FILL = Color.web("#c9c9c9");
    private static final Color DRILL_STROKE = Color.web("#8a8a8a");
    private static final Color ISOLATION_COLOR = Color.web("#28d0d0");

    private final JobExecutor jobExecutor;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Pronto.");
    private final Circle statusDot = new Circle(5, Color.web("#4caf50"));
    private final Label unitsLabel = new Label("Unidades: -");
    private final Button runDemoJobButton = new Button();
    private final Button cancelJobButton = new Button();
    private final TextArea console = new TextArea();
    private final TabPane centerTabs = new TabPane();
    private final Label propertiesLabel = new Label("Selecione um objeto\npara ver seus parametros.");

    /** A generated G-code file, tracked in the "CNC Jobs" tree category once GCodeGenerator writes one. */
    private record CncJobEntry(String sourceName, Path outputFile, String gcode) {
    }

    /**
     * PlotAreaView layer key for an isolation preview, distinct from the
     * Gerber's own key (its TreeItem) so the toolpath layer sits alongside
     * the copper layer instead of replacing it. Re-generating isolation for
     * the same Gerber item produces an equal key, so the preview layer is
     * replaced rather than duplicated.
     */
    private record IsolationLayerKey(TreeItem<String> gerberItem) {
    }

    /** Files opened/generated so far, keyed by their tree item - back the Properties tab and the item context menu. */
    private final Map<TreeItem<String>, GerberImage> gerberByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, ExcellonImage> excellonByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, CncJobEntry> cncJobByItem = new LinkedHashMap<>();
    /** Original file path for Gerber/Excellon items - what gets written to a saved project file. */
    private final Map<TreeItem<String>, Path> sourcePathByItem = new LinkedHashMap<>();

    private final PlotAreaView plotAreaView = new PlotAreaView();

    private Scene scene;
    private SplitPane horizontalSplit;
    private SplitPane verticalSplit;
    private TreeItem<String> gerbersNode;
    private TreeItem<String> excellonNode;
    private TreeItem<String> cncJobsNode;
    private VBox bottomPanel;
    private double dividerBeforeConsoleCollapse = 0.75;
    private boolean consoleCollapsed;
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
        optionsMenu.getItems().add(toolsDbItem);

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
            AppPreferences.saveTheme(option);
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
        consoleToggle.setSelected(true);
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
        verticalSplit = new SplitPane(horizontalSplit, bottomPanel);
        verticalSplit.setOrientation(Orientation.VERTICAL);

        // SplitPane does not reliably honor a divider position set before its first
        // layout pass - measured drift on a loaded 0.55: it settled at ~0.51 by the next
        // launch, because the save listener (attached below) caught that settling as if
        // it were a real change and persisted it. Deferring the initial set past the
        // first layout, then attaching the listener, avoids that drift entirely.
        Platform.runLater(() -> {
            horizontalSplit.setDividerPositions(AppPreferences.loadSplitHorizontal(0.22));
            verticalSplit.setDividerPositions(AppPreferences.loadSplitVertical(0.75));

            horizontalSplit.getDividers().get(0).positionProperty()
                    .addListener((obs, oldVal, newVal) -> saveSplitPositions());
            attachVerticalDividerSaveListener();
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
     * section 1 (appGUI/MainGUI.py's self.notebook). Properties and Tool are
     * placeholders until Fase 3/5 give them real content to show.
     */
    private TabPane buildLeftTabs() {
        Tab projectTab = new Tab("Projeto", buildProjectTree());
        propertiesLabel.setTextAlignment(TextAlignment.CENTER);
        Tab propertiesTab = new Tab("Propriedades", new StackPane(propertiesLabel));
        Tab toolTab = new Tab("Ferramenta", centeredPlaceholder("Nenhuma ferramenta ativa."));

        TabPane tabs = new TabPane(projectTab, propertiesTab, toolTab);
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        tabs.getStyleClass().add("side-panel");
        tabs.setMinWidth(160);
        return tabs;
    }

    /**
     * A hidden root (the "Projeto" tab title already says that - an explicit
     * "Projeto" row too was redundant) with one category node per object
     * kind, matching appObjects/ObjectCollection.py's grouping. Category
     * nodes are inert; file nodes underneath (added as Gerber/Excellon files
     * are opened, or as drilling G-code is generated - Geometry has no Java
     * generator yet) carry a {@link GerberImage}, {@link ExcellonImage} or
     * {@link CncJobEntry} in {@link #gerberByItem}/{@link #excellonByItem}/
     * {@link #cncJobByItem} and get a context menu. Only "Exibir no Plot
     * Area"/"Ver G-code" and "Remover" are wired - the legacy per-object menu
     * also has Set Color, Edit, Copy, Save (see UI_INVENTORY.md section 1),
     * which need subsystems (plot state, editors, project format) this phase
     * doesn't have yet.
     */
    private TreeView<String> buildProjectTree() {
        TreeItem<String> root = new TreeItem<>("Projeto");
        root.setExpanded(true);
        gerbersNode = new TreeItem<>("Gerbers");
        gerbersNode.setExpanded(true);
        excellonNode = new TreeItem<>("Excellon");
        excellonNode.setExpanded(true);
        cncJobsNode = new TreeItem<>("CNC Jobs");
        cncJobsNode.setExpanded(true);
        root.getChildren().addAll(
                gerbersNode,
                excellonNode,
                new TreeItem<>("Geometry"),
                cncJobsNode
        );

        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> showProperties(selected));
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setContextMenu(null);
                    return;
                }
                setText(value);
                TreeItem<String> item = getTreeItem();
                GerberImage gerberImage = gerberByItem.get(item);
                ExcellonImage excellonImage = excellonByItem.get(item);
                CncJobEntry cncJob = cncJobByItem.get(item);
                if (gerberImage != null) {
                    setContextMenu(buildGerberContextMenu(item, gerberImage));
                } else if (excellonImage != null) {
                    setContextMenu(buildExcellonContextMenu(item, excellonImage));
                } else if (cncJob != null) {
                    setContextMenu(buildCncJobContextMenu(item, cncJob));
                } else {
                    setContextMenu(null);
                }
            }
        });
        return tree;
    }

    /**
     * "Exibir no Plot Area", "Ativar/Desativar Plot", "Definir Cor...",
     * "Gerar Isolamento..." and "Remover" for a Gerber tree item - the
     * visibility toggle and color picker mirror the legacy per-object menu's
     * Enable/Disable Plot and Set Color (UI_INVENTORY.md section 1), now
     * that each object is its own PlotAreaView layer instead of one
     * replacing another.
     */
    private ContextMenu buildGerberContextMenu(TreeItem<String> item, GerberImage image) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem visibilityItem = new MenuItem();
        visibilityItem.setOnAction(e -> plotAreaView.setLayerVisible(item, !plotAreaView.isLayerVisible(item)));

        MenuItem colorItem = new MenuItem("Definir Cor...");
        colorItem.setOnAction(e -> editLayerColor(item));

        MenuItem isolationItem = new MenuItem("Gerar Isolamento...");
        isolationItem.setOnAction(e -> generateIsolation(item, image));

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> removeFromProject(item, gerberByItem));

        ContextMenu menu = new ContextMenu(showItem, visibilityItem, colorItem, isolationItem, removeItem);
        menu.setOnShowing(e -> visibilityItem.setText(plotAreaView.isLayerVisible(item) ? "Desativar Plot" : "Ativar Plot"));
        return menu;
    }

    /** Same as {@link #buildGerberContextMenu}, minus isolation, plus "Gerar G-code de furacao". */
    private ContextMenu buildExcellonContextMenu(TreeItem<String> item, ExcellonImage image) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem visibilityItem = new MenuItem();
        visibilityItem.setOnAction(e -> plotAreaView.setLayerVisible(item, !plotAreaView.isLayerVisible(item)));

        MenuItem colorItem = new MenuItem("Definir Cor...");
        colorItem.setOnAction(e -> editLayerColor(item));

        MenuItem gcodeItem = new MenuItem("Gerar G-code de furacao...");
        gcodeItem.setOnAction(e -> generateDrillGCode(item, image));

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> removeFromProject(item, excellonByItem));

        ContextMenu menu = new ContextMenu(showItem, visibilityItem, colorItem, gcodeItem, removeItem);
        menu.setOnShowing(e -> visibilityItem.setText(plotAreaView.isLayerVisible(item) ? "Desativar Plot" : "Ativar Plot"));
        return menu;
    }

    private void focusLayer(TreeItem<String> item) {
        plotAreaView.setLayerVisible(item, true);
        plotAreaView.bringToFront(item);
        plotAreaView.fitToLayer(item);
        centerTabs.getSelectionModel().select(0);
    }

    /** Only a fill color is asked for - the legacy dialog doesn't expose a separate outline color either. */
    private void editLayerColor(TreeItem<String> item) {
        Color[] current = plotAreaView.layerColors(item);
        Color currentFill = current != null ? current[0] : GERBER_FILL;
        LayerColorDialog.show(currentFill)
                .ifPresent(fill -> plotAreaView.setLayerColors(item, fill, fill.darker()));
    }

    private ContextMenu buildCncJobContextMenu(TreeItem<String> item, CncJobEntry entry) {
        MenuItem viewItem = new MenuItem("Ver G-code");
        viewItem.setOnAction(e -> openAuxiliaryTab(item.getValue(), () -> buildGCodeViewer(entry.gcode())));

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> removeFromProject(item, cncJobByItem));

        return new ContextMenu(viewItem, removeItem);
    }

    private TextArea buildGCodeViewer(String gcode) {
        TextArea area = new TextArea(gcode);
        area.setEditable(false);
        area.setStyle("-fx-font-family: monospace;");
        return area;
    }

    private void addCncJobToProject(String outputFileName, String sourceName, Path outputFile, String gcode) {
        TreeItem<String> item = new TreeItem<>(outputFileName);
        cncJobByItem.put(item, new CncJobEntry(sourceName, outputFile, gcode));
        cncJobsNode.getChildren().add(item);
    }

    /**
     * Fase 5 first slice: asks for a few generation parameters (DrillGCodeDialog),
     * then writes plain drill G-code (GCodeGenerator) to a file the user picks.
     * Runs on the FX thread directly - string-building over a few hundred/
     * thousand points is not the kind of work secao 4.3 is about; move this to
     * JobExecutor if a pathological input ever makes it worth it.
     */
    private void generateDrillGCode(TreeItem<String> item, ExcellonImage image) {
        Optional<DrillGCodeParameters> params = DrillGCodeDialog.show(image.units(), image.toolDiameters().size());
        if (params.isEmpty()) {
            return;
        }

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
            String gcode = GCodeGenerator.generateDrillGCode(image, params.get());
            Files.writeString(outFile.toPath(), gcode);
            AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
            appendConsole("G-code de furacao salvo em " + outFile + " (" + gcode.lines().count() + " linhas).");
            addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), gcode);
        } catch (Exception e) {
            appendConsole("Falha ao gerar/salvar G-code: " + e.getMessage());
        }
    }

    /**
     * Fase 5 first slice: generates an isolation toolpath around the
     * Gerber's copper (IsolationGenerator, ported from appTools/
     * ToolIsolation.py + camlib.py's Gerber.isolation_geometry() - see its
     * class doc for exactly what was and wasn't carried over), shows it as
     * its own cyan stroke-only layer alongside the copper's own layer (the
     * copper stays visible, unlike opening a different file), then writes
     * G-code the same way generateDrillGCode() does.
     */
    private void generateIsolation(TreeItem<String> item, GerberImage image) {
        Optional<IsolationDialog.Result> params = IsolationDialog.show(image.units());
        if (params.isEmpty()) {
            return;
        }

        IsolationResult isolation = IsolationGenerator.generate(image.units(), image.solidGeometry(), params.get().geometryParams());
        if (isolation.isEmpty()) {
            appendConsole("Isolamento nao gerou nenhum anel (geometria de cobre vazia?).");
            return;
        }

        plotAreaView.putLayer(new IsolationLayerKey(item), PlotAreaView.LayerCategory.OVERLAY,
                isolation.geometry(), ISOLATION_COLOR, ISOLATION_COLOR, true);
        centerTabs.getSelectionModel().select(0);
        appendConsole(String.format("Isolamento: %d aneis, comprimento total=%.4f, bounds=%s",
                isolation.ringCount(), isolation.totalLength(), Arrays.toString(isolation.bounds())));

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

        try {
            String gcode = GCodeGenerator.generateIsolationGCode(isolation, params.get().gcodeParams());
            Files.writeString(outFile.toPath(), gcode);
            AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
            appendConsole("G-code de isolamento salvo em " + outFile + " (" + gcode.lines().count() + " linhas).");
            addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), gcode);
        } catch (Exception e) {
            appendConsole("Falha ao gerar/salvar G-code de isolamento: " + e.getMessage());
        }
    }

    private void removeFromProject(TreeItem<String> item, Map<TreeItem<String>, ?> byItem) {
        item.getParent().getChildren().remove(item);
        byItem.remove(item);
        sourcePathByItem.remove(item);
        plotAreaView.removeLayer(item);
        plotAreaView.removeLayer(new IsolationLayerKey(item));
        appendConsole("Removido do projeto: " + item.getValue());
    }

    private void showProperties(TreeItem<String> item) {
        GerberImage gerberImage = item == null ? null : gerberByItem.get(item);
        if (gerberImage != null) {
            propertiesLabel.setText(String.format(
                    "%s%n%nUnidades: %s%nAperturas: %d%nArea: %.4f%nBounds: %s",
                    item.getValue(), gerberImage.units(), gerberImage.apertures().size(), gerberImage.totalArea(),
                    Arrays.toString(gerberImage.bounds())
            ));
            return;
        }
        ExcellonImage excellonImage = item == null ? null : excellonByItem.get(item);
        if (excellonImage != null) {
            propertiesLabel.setText(String.format(
                    "%s%n%nUnidades: %s%nFerramentas: %d%nFuros: %d%nSlots: %d%nBounds: %s",
                    item.getValue(), excellonImage.units(), excellonImage.toolDiameters().size(),
                    excellonImage.totalDrills(), excellonImage.totalSlots(), Arrays.toString(excellonImage.bounds())
            ));
            return;
        }
        CncJobEntry cncJob = item == null ? null : cncJobByItem.get(item);
        if (cncJob != null) {
            propertiesLabel.setText(String.format(
                    "%s%n%nOrigem: %s%nArquivo: %s%nLinhas: %d",
                    item.getValue(), cncJob.sourceName(), cncJob.outputFile(), cncJob.gcode().lines().count()
            ));
            return;
        }
        propertiesLabel.setText("Selecione um objeto\npara ver seus parametros.");
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
        HBox progressRow = new HBox(8, progressBar);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(4));
        HBox.setHgrow(progressBar, Priority.ALWAYS);

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
        progressBar.setProgress(0);
        setStatus("Executando...", RUNNING_COLOR);
        appendConsole("Job de demonstracao iniciado (nao bloqueia a UI - tente redimensionar a janela).");

        JobHandle<Void> handle = jobExecutor.submit(new DemoJob(), (fraction, message) ->
                Platform.runLater(() -> {
                    progressBar.setProgress(fraction);
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
            onJobFinished();
            return;
        }
        File file = files.get(index);
        beginJob("Analisando " + file.getName() + "...");
        JobHandle<GerberImage> handle = jobExecutor.submit(context -> new GerberParser().parse(file.toPath()), null);
        runningJob = handle;

        handle.completion()
                .thenAccept(image -> Platform.runLater(() -> {
                    setStatus("Concluido.", IDLE_COLOR);
                    progressBar.setProgress(1);
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
                        openGerberQueue(files, index + 1);
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
            onJobFinished();
            return;
        }
        File file = files.get(index);
        beginJob("Analisando " + file.getName() + "...");
        JobHandle<ExcellonImage> handle = jobExecutor.submit(context -> new ExcellonParser().parse(file.toPath()), null);
        runningJob = handle;

        handle.completion()
                .thenAccept(image -> Platform.runLater(() -> {
                    setStatus("Concluido.", IDLE_COLOR);
                    progressBar.setProgress(1);
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
                        openExcellonQueue(files, index + 1);
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
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus(statusText, RUNNING_COLOR);
        appendConsole(statusText);
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
     * Writes which Gerber/Excellon files are open and which G-code jobs were
     * generated - see ProjectFile's doc for why this is a list of paths to
     * re-parse, not a geometry snapshot like the legacy .FlatPrj.
     */
    private void saveProject() {
        List<String> gerberPaths = gerberByItem.keySet().stream()
                .map(sourcePathByItem::get).map(Path::toString).toList();
        List<String> excellonPaths = excellonByItem.keySet().stream()
                .map(sourcePathByItem::get).map(Path::toString).toList();
        List<ProjectFile.CncJobRecord> jobs = cncJobByItem.values().stream()
                .map(entry -> new ProjectFile.CncJobRecord(entry.sourceName(), entry.outputFile().toString()))
                .toList();
        ProjectFile project = new ProjectFile(gerberPaths, excellonPaths, jobs);

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

    /** Clears the current project, then re-parses every file the loaded ProjectFile references. */
    private void openProject() {
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

        ProjectFile project;
        try {
            project = ProjectFileIO.load(file.toPath());
        } catch (IOException e) {
            appendConsole("Falha ao abrir projeto: " + e.getMessage());
            return;
        }
        AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());

        clearProject();
        for (String path : project.gerberPaths()) {
            loadGerberFileSync(new File(path));
        }
        for (String path : project.excellonPaths()) {
            loadExcellonFileSync(new File(path));
        }
        for (ProjectFile.CncJobRecord job : project.cncJobs()) {
            Path outputPath = Path.of(job.outputPath());
            if (!Files.exists(outputPath)) {
                appendConsole("Aviso: G-code nao encontrado (arquivo movido/apagado?): " + outputPath);
                continue;
            }
            try {
                String gcode = Files.readString(outputPath);
                addCncJobToProject(outputPath.getFileName().toString(), job.sourceName(), outputPath, gcode);
            } catch (IOException e) {
                appendConsole("Aviso: nao foi possivel ler G-code " + outputPath + ": " + e.getMessage());
            }
        }
        appendConsole("Projeto aberto: " + file);
    }

    private void clearProject() {
        gerbersNode.getChildren().clear();
        excellonNode.getChildren().clear();
        cncJobsNode.getChildren().clear();
        gerberByItem.clear();
        excellonByItem.clear();
        cncJobByItem.clear();
        sourcePathByItem.clear();
        plotAreaView.clearLayers();
    }

    /**
     * Synchronous (not via JobExecutor) re-parse used only by openProject() -
     * loading a handful of files at project-open time is a batch operation,
     * not the single cancellable action the JobExecutor-based open flows are
     * built around, and parsing every fixture/real file checked so far takes
     * well under a second. Revisit if a pathological project makes this show.
     */
    private void loadGerberFileSync(File file) {
        try {
            GerberImage image = new GerberParser().parse(file.toPath());
            addGerberToProject(file, image);
        } catch (Exception e) {
            appendConsole("Falha ao reabrir Gerber " + file + ": " + e.getMessage());
        }
    }

    private void loadExcellonFileSync(File file) {
        try {
            ExcellonImage image = new ExcellonParser().parse(file.toPath());
            addExcellonToProject(file, image);
        } catch (Exception e) {
            appendConsole("Falha ao reabrir Excellon " + file + ": " + e.getMessage());
        }
    }

    /** Adds the tree item and, since every opened object gets its own layer now, its plot too - visible immediately. */
    private TreeItem<String> addGerberToProject(File file, GerberImage image) {
        TreeItem<String> item = new TreeItem<>(file.getName());
        gerberByItem.put(item, image);
        sourcePathByItem.put(item, file.toPath());
        gerbersNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GERBER, image.solidGeometry(), GERBER_FILL, GERBER_STROKE, false);
        return item;
    }

    private TreeItem<String> addExcellonToProject(File file, ExcellonImage image) {
        TreeItem<String> item = new TreeItem<>(file.getName());
        excellonByItem.put(item, image);
        sourcePathByItem.put(item, file.toPath());
        excellonNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.EXCELLON, image.solidGeometry(), DRILL_FILL, DRILL_STROKE, false);
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
