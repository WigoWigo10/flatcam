package org.flatcam.fx;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;

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
    private static final Color DRILL_FILL = Color.web("#c9c9c9");
    private static final Color DRILL_STROKE = Color.web("#8a8a8a");

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

    /** Files opened so far, keyed by their tree item - back the Properties tab and the item context menu. */
    private final Map<TreeItem<String>, GerberImage> gerberByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, ExcellonImage> excellonByItem = new LinkedHashMap<>();

    private final PlotAreaView plotAreaView = new PlotAreaView();

    private Scene scene;
    private SplitPane horizontalSplit;
    private SplitPane verticalSplit;
    private TreeItem<String> gerbersNode;
    private TreeItem<String> excellonNode;
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
        AppPreferences.saveSplitPositions(horizontalSplit.getDividerPositions()[0], verticalSplit.getDividerPositions()[0]);
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("Arquivo");
        MenuItem openGerberItem = new MenuItem("Abrir Gerber (prototipo Fase 3)");
        openGerberItem.setOnAction(e -> openGerberPrototype());
        MenuItem openExcellonItem = new MenuItem("Abrir Excellon (prototipo Fase 4)");
        openExcellonItem.setOnAction(e -> openExcellonPrototype());
        MenuItem runDemoJob = new MenuItem("Executar job de demonstracao");
        runDemoJob.setOnAction(e -> runDemoJob());
        MenuItem exitItem = new MenuItem("Sair");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(openGerberItem, openExcellonItem, runDemoJob, new SeparatorMenuItem(), exitItem);

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
     */
    private HBox buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(6, statusDot, statusLabel, spacer, unitsLabel);
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
        horizontalSplit.setDividerPositions(AppPreferences.loadSplitHorizontal(0.22));

        verticalSplit = new SplitPane(horizontalSplit, buildBottomPanel());
        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.setDividerPositions(AppPreferences.loadSplitVertical(0.75));

        // Save on every drag, not just on window close - relying only on the close
        // handler lost divider positions in practice (see AppPreferences).
        horizontalSplit.getDividers().get(0).positionProperty()
                .addListener((obs, oldVal, newVal) -> saveSplitPositions());
        verticalSplit.getDividers().get(0).positionProperty()
                .addListener((obs, oldVal, newVal) -> saveSplitPositions());
        return verticalSplit;
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
     * are opened - Geometry/CNC Jobs have no Java parser/generator yet)
     * carry a {@link GerberImage} or {@link ExcellonImage} in {@link #gerberByItem}/
     * {@link #excellonByItem} and get a context menu. Only "Exibir no Plot
     * Area" and "Remover" are wired - the legacy per-object menu also has Set
     * Color, Create CNCJob, Edit, Copy, Save (see UI_INVENTORY.md section 1),
     * which need subsystems (plot state, CNCJob generation, editors, project
     * format) this phase doesn't have yet.
     */
    private TreeView<String> buildProjectTree() {
        TreeItem<String> root = new TreeItem<>("Projeto");
        root.setExpanded(true);
        gerbersNode = new TreeItem<>("Gerbers");
        gerbersNode.setExpanded(true);
        excellonNode = new TreeItem<>("Excellon");
        excellonNode.setExpanded(true);
        root.getChildren().addAll(
                gerbersNode,
                excellonNode,
                new TreeItem<>("Geometry"),
                new TreeItem<>("CNC Jobs")
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
                if (gerberImage != null) {
                    setContextMenu(buildFileContextMenu(
                            () -> showGerber(gerberImage),
                            () -> removeFromProject(item, gerberByItem)));
                } else if (excellonImage != null) {
                    setContextMenu(buildFileContextMenu(
                            () -> showExcellon(excellonImage),
                            () -> removeFromProject(item, excellonByItem)));
                } else {
                    setContextMenu(null);
                }
            }
        });
        return tree;
    }

    private ContextMenu buildFileContextMenu(Runnable onShow, Runnable onRemove) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        showItem.setOnAction(e -> {
            onShow.run();
            centerTabs.getSelectionModel().select(0);
        });

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> onRemove.run());

        return new ContextMenu(showItem, removeItem);
    }

    private void removeFromProject(TreeItem<String> item, Map<TreeItem<String>, ?> byItem) {
        item.getParent().getChildren().remove(item);
        byItem.remove(item);
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
     * progress bar/cancel button/console as the demo job, one job at a time.
     */
    private void openGerberPrototype() {
        File file = pickCamFile("Abrir Gerber (prototipo)",
                new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.cmp", "*.gtl", "*.gbl", "*.txt"));
        if (file == null) {
            return;
        }

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
                    showGerber(image);
                    addGerberToProject(file.getName(), image);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir Gerber: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Fase 4 vertical slice - same shape as {@link #openGerberPrototype()}, see flatcam-cam's ExcellonParser. */
    private void openExcellonPrototype() {
        File file = pickCamFile("Abrir Excellon (prototipo)",
                new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
        if (file == null) {
            return;
        }

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
                    showExcellon(image);
                    addExcellonToProject(file.getName(), image);
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir Excellon: ");
                        onJobFinished();
                    });
                    return null;
                });
    }

    /** Shared "pick a fabrication file" flow: remembers the last folder across both Gerber and Excellon. */
    private File pickCamFile(String title, FileChooser.ExtensionFilter filter) {
        if (runningJob != null) {
            appendConsole("Ja ha um job em andamento.");
            return null;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showOpenDialog(scene.getWindow());
        if (file != null) {
            AppPreferences.saveLastCamDirectory(file.getParentFile().getAbsolutePath());
        }
        return file;
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

    private void addGerberToProject(String name, GerberImage image) {
        TreeItem<String> item = new TreeItem<>(name);
        gerberByItem.put(item, image);
        gerbersNode.getChildren().add(item);
    }

    private void addExcellonToProject(String name, ExcellonImage image) {
        TreeItem<String> item = new TreeItem<>(name);
        excellonByItem.put(item, image);
        excellonNode.getChildren().add(item);
    }

    private void showGerber(GerberImage image) {
        plotAreaView.setGeometry(image.solidGeometry());
    }

    private void showExcellon(ExcellonImage image) {
        plotAreaView.setGeometry(image.solidGeometry(), DRILL_FILL, DRILL_STROKE);
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
