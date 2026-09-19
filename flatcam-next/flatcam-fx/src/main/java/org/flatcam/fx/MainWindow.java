package org.flatcam.fx;

import java.util.concurrent.CancellationException;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;

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

    private final JobExecutor jobExecutor;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Pronto.");
    private final Button runDemoJobButton = new Button("Executar job de demonstracao");
    private final Button cancelJobButton = new Button("Cancelar");
    private final TextArea console = new TextArea();
    private final TabPane centerTabs = new TabPane();

    private Scene scene;
    private JobHandle<Void> runningJob;

    MainWindow(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
    }

    Scene createScene() {
        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        root.setCenter(buildMainSplit());

        scene = new Scene(root);
        ThemeOption.CUSTOM_LIGHT.applyTo(scene);
        return scene;
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("Arquivo");
        MenuItem runDemoJob = new MenuItem("Executar job de demonstracao");
        runDemoJob.setOnAction(e -> runDemoJob());
        MenuItem exitItem = new MenuItem("Sair");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(runDemoJob, new SeparatorMenuItem(), exitItem);

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
                themeItem(ThemeOption.CUSTOM_LIGHT, themeGroup, true),
                themeItem(ThemeOption.CUSTOM_DARK, themeGroup, false)
        );

        Menu atlantaFxMenu = new Menu("AtlantaFX");
        atlantaFxMenu.getItems().addAll(
                themeItem(ThemeOption.ATLANTAFX_LIGHT, themeGroup, false),
                themeItem(ThemeOption.ATLANTAFX_DARK, themeGroup, false)
        );

        Menu themeMenu = new Menu("Tema");
        themeMenu.getItems().addAll(customMenu, atlantaFxMenu);
        return themeMenu;
    }

    private RadioMenuItem themeItem(ThemeOption option, ToggleGroup group, boolean selected) {
        RadioMenuItem item = new RadioMenuItem(option.label());
        item.setToggleGroup(group);
        item.setSelected(selected);
        item.setOnAction(e -> option.applyTo(scene));
        return item;
    }

    private ToolBar buildToolBar() {
        runDemoJobButton.setOnAction(e -> runDemoJob());
        cancelJobButton.setDisable(true);
        cancelJobButton.setOnAction(e -> cancelDemoJob());
        return new ToolBar(runDemoJobButton, cancelJobButton);
    }

    private SplitPane buildMainSplit() {
        SplitPane horizontal = new SplitPane(buildLeftTabs(), buildCenterTabs());
        horizontal.setDividerPositions(0.22);

        SplitPane vertical = new SplitPane(horizontal, buildBottomPanel());
        vertical.setOrientation(Orientation.VERTICAL);
        vertical.setDividerPositions(0.75);
        return vertical;
    }

    /**
     * Project / Properties / Tool sharing one tab strip - see UI_INVENTORY.md
     * section 1 (appGUI/MainGUI.py's self.notebook). Properties and Tool are
     * placeholders until Fase 3/5 give them real content to show.
     */
    private TabPane buildLeftTabs() {
        Tab projectTab = new Tab("Projeto", buildProjectTree());
        Tab propertiesTab = new Tab("Propriedades", centeredPlaceholder("Selecione um objeto\npara ver seus parametros."));
        Tab toolTab = new Tab("Ferramenta", centeredPlaceholder("Nenhuma ferramenta ativa."));

        TabPane tabs = new TabPane(projectTab, propertiesTab, toolTab);
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        tabs.getStyleClass().add("side-panel");
        tabs.setMinWidth(160);
        return tabs;
    }

    private TreeView<String> buildProjectTree() {
        TreeItem<String> root = new TreeItem<>("Projeto");
        root.setExpanded(true);
        root.getChildren().addAll(
                new TreeItem<>("Gerbers"),
                new TreeItem<>("Excellon"),
                new TreeItem<>("Geometry"),
                new TreeItem<>("CNC Jobs")
        );
        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(true);
        return tree;
    }

    /**
     * Plot Area (viewport) is the one tab that can never be closed;
     * everything else - Preferences, Tools Database, editors - opens here on
     * demand via {@link #openAuxiliaryTab}, matching appGUI/MainGUI.py's
     * plot_tab_area instead of the separate right-hand panel the skeleton
     * used to have.
     */
    private TabPane buildCenterTabs() {
        Tab plotAreaTab = new Tab("Plot Area", buildViewportPlaceholder());
        plotAreaTab.setClosable(false);
        centerTabs.getTabs().add(plotAreaTab);
        return centerTabs;
    }

    private StackPane buildViewportPlaceholder() {
        Label placeholder = new Label(
                "Viewport GPU\n(Fase 2 - ainda nao implementado)\n\nAbrir Gerber/Excellon chega na Fase 3."
        );
        placeholder.setTextAlignment(TextAlignment.CENTER);
        StackPane viewport = new StackPane(placeholder);
        viewport.getStyleClass().add("viewport-placeholder");
        return viewport;
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
        HBox progressRow = new HBox(8, progressBar, statusLabel, cancelJobButton);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(4));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

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
        appendConsole("Job de demonstracao iniciado (nao bloqueia a UI - tente redimensionar a janela).");

        runningJob = jobExecutor.submit(new DemoJob(), (fraction, message) ->
                Platform.runLater(() -> {
                    progressBar.setProgress(fraction);
                    statusLabel.setText(message);
                }));

        runningJob.completion()
                .thenAccept(result -> Platform.runLater(() -> {
                    statusLabel.setText("Concluido.");
                    appendConsole("Job de demonstracao concluido.");
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (error.getCause() instanceof CancellationException
                                || error instanceof CancellationException) {
                            statusLabel.setText("Cancelado.");
                            appendConsole("Job de demonstracao cancelado pelo usuario.");
                        } else {
                            statusLabel.setText("Falhou.");
                            appendConsole("Job de demonstracao falhou: " + error.getMessage());
                        }
                        onJobFinished();
                    });
                    return null;
                });
    }

    private void cancelDemoJob() {
        if (runningJob != null) {
            runningJob.cancel();
        }
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
