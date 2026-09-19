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
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;

/**
 * Builds the layout described in CONTEXTO_FLATCAM_FX.md, secao 6: menu/
 * toolbar on top, resizable project tree / viewport / properties panels in
 * the middle, jobs+console at the bottom. The center panel is a placeholder -
 * the real GPU-backed viewport is Fase 2.
 */
final class MainWindow {

    private final JobExecutor jobExecutor;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Pronto.");
    private final Button runDemoJobButton = new Button("Executar job de demonstracao");
    private final Button cancelJobButton = new Button("Cancelar");
    private final TextArea console = new TextArea();

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
        Theme.LIGHT.applyTo(scene);
        return scene;
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("Arquivo");
        MenuItem runDemoJob = new MenuItem("Executar job de demonstracao");
        runDemoJob.setOnAction(e -> runDemoJob());
        MenuItem exitItem = new MenuItem("Sair");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(runDemoJob, new SeparatorMenuItem(), exitItem);

        Menu viewMenu = new Menu("Exibir");
        ToggleGroup themeGroup = new ToggleGroup();
        RadioMenuItem lightTheme = new RadioMenuItem("Tema claro");
        lightTheme.setSelected(true);
        lightTheme.setToggleGroup(themeGroup);
        lightTheme.setOnAction(e -> Theme.LIGHT.applyTo(scene));
        RadioMenuItem darkTheme = new RadioMenuItem("Tema escuro");
        darkTheme.setToggleGroup(themeGroup);
        darkTheme.setOnAction(e -> Theme.DARK.applyTo(scene));
        viewMenu.getItems().addAll(lightTheme, darkTheme);

        Menu helpMenu = new Menu("Ajuda");
        MenuItem aboutItem = new MenuItem("Sobre");
        aboutItem.setOnAction(e -> appendConsole("FlatCAM Next - esqueleto Fase 1 (CONTEXTO_FLATCAM_FX.md)"));
        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, viewMenu, helpMenu);
    }

    private ToolBar buildToolBar() {
        runDemoJobButton.setOnAction(e -> runDemoJob());
        cancelJobButton.setDisable(true);
        cancelJobButton.setOnAction(e -> cancelDemoJob());
        return new ToolBar(runDemoJobButton, cancelJobButton);
    }

    private SplitPane buildMainSplit() {
        SplitPane horizontal = new SplitPane(buildProjectPanel(), buildViewportPlaceholder(), buildPropertiesPanel());
        horizontal.setDividerPositions(0.2, 0.78);

        SplitPane vertical = new SplitPane(horizontal, buildBottomPanel());
        vertical.setOrientation(Orientation.VERTICAL);
        vertical.setDividerPositions(0.75);
        return vertical;
    }

    private VBox buildProjectPanel() {
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
        VBox.setVgrow(tree, Priority.ALWAYS);

        VBox panel = new VBox(new Label("Projeto"), tree);
        panel.getStyleClass().add("side-panel");
        panel.setMinWidth(160);
        return panel;
    }

    private StackPane buildViewportPlaceholder() {
        Label placeholder = new Label(
                "Viewport GPU\n(Fase 2 - ainda nao implementado)\n\nAbrir Gerber/Excellon chega na Fase 3."
        );
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        StackPane viewport = new StackPane(placeholder);
        viewport.getStyleClass().add("viewport-placeholder");
        return viewport;
    }

    private VBox buildPropertiesPanel() {
        Label placeholder = new Label("Selecione um objeto\npara ver seus parametros.");
        VBox panel = new VBox(new Label("Ferramenta / Propriedades"), placeholder);
        panel.getStyleClass().add("side-panel");
        panel.setMinWidth(180);
        return panel;
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
