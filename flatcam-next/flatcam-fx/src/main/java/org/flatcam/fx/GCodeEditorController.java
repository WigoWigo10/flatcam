package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;

/** Edits one CNC Job's machine-code text without touching its source file. */
final class GCodeEditorController {

    interface Host {
        void openToolPanel(String label, Node content);

        void closeToolPanel();

        boolean apply(TreeItem<String> item, String text, Runnable onSuccess, Consumer<String> onFailure);

        boolean saveAs(String text, Consumer<String> onComplete);

        void log(String message);
    }

    private final TabPane tabs;
    private final Host host;
    private TreeItem<String> item;
    private Tab tab;
    private TextArea editor;
    private Button applyButton;
    private Button saveButton;
    private Button cancelButton;
    private Label stateLabel;
    private String originalText;
    private boolean dirty;
    private boolean busy;

    GCodeEditorController(TabPane tabs, Host host) {
        this.tabs = tabs;
        this.host = host;
    }

    boolean isActive() {
        return item != null;
    }

    boolean hasUnappliedChanges() {
        return isActive() && dirty && !editor.getText().equals(originalText);
    }

    boolean isEditing(TreeItem<String> target) {
        return item == target;
    }

    void start(TreeItem<String> selectedItem, String gcode) {
        if (isActive()) {
            tabs.getSelectionModel().select(tab);
            host.log("Ja existe uma edicao de G-code em andamento.");
            return;
        }
        item = selectedItem;
        originalText = gcode;
        editor = new TextArea(gcode);
        editor.setWrapText(false);
        editor.setStyle("-fx-font-family: monospace;");
        editor.textProperty().addListener((observable, oldText, newText) -> {
            dirty = true;
            updateButtons();
        });
        tab = new Tab("Editor G-Code - " + item.getValue(), editor);
        tab.setClosable(false);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);

        Label info = new Label("Editando: " + item.getValue());
        Label help = new Label("Edite o texto e clique Aplicar para atualizar o CNC Job em memoria. "
                + "Salvar arquivo grava uma copia do rascunho; nao altera o arquivo original automaticamente. "
                + "Cancelar descarta as alteracoes. A pre-visualizacao cobre movimentos G0/G1 e arcos G2/G3 em XY "
                + "e nao valida a seguranca do programa para uma maquina CNC.");
        help.setWrapText(true);
        stateLabel = new Label();
        stateLabel.setWrapText(true);
        applyButton = new Button("Aplicar ao CNC Job");
        saveButton = new Button("Salvar arquivo...");
        cancelButton = new Button("Cancelar edicao");
        for (Button button : new Button[]{applyButton, saveButton, cancelButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        applyButton.setOnAction(event -> apply());
        saveButton.setOnAction(event -> saveAs());
        cancelButton.setOnAction(event -> cancel());
        VBox panel = new VBox(10, info, help, stateLabel, applyButton, saveButton, cancelButton);
        panel.setPadding(new Insets(12));
        host.openToolPanel("Editor G-Code", panel);
        updateButtons();
        editor.requestFocus();
    }

    void cancelIfEditing(TreeItem<String> removed) {
        if (item == removed) {
            cancel();
        }
    }

    void cancel() {
        if (!isActive() || busy) {
            return;
        }
        close();
        host.log("Editor G-Code: edicao descartada.");
    }

    void saveAndClose() {
        apply();
    }

    private void apply() {
        if (!isActive() || busy) {
            return;
        }
        if (!dirty || editor.getText().equals(originalText) || editor.getText().isBlank()) {
            if (editor.getText().isBlank()) {
                stateLabel.setText("O G-code nao pode estar vazio.");
            } else {
                close();
            }
            return;
        }
        String text = editor.getText();
        TreeItem<String> editing = item;
        busy = true;
        updateButtons();
        if (!host.apply(editing, text, () -> {
            if (item == editing) {
                busy = false;
                close();
            }
        }, message -> {
            if (item == editing) {
                busy = false;
                updateButtons();
                stateLabel.setText(message);
            }
        })) {
            busy = false;
            updateButtons();
        }
    }

    private void saveAs() {
        if (!isActive() || busy || editor.getText().isBlank()) {
            return;
        }
        String text = editor.getText();
        busy = true;
        updateButtons();
        if (!host.saveAs(text, message -> {
            if (isActive()) {
                busy = false;
                updateButtons();
                stateLabel.setText(message);
            }
        })) {
            busy = false;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (applyButton == null) {
            return;
        }
        applyButton.setDisable(busy || !dirty);
        saveButton.setDisable(busy || editor.getText().isBlank());
        cancelButton.setDisable(busy);
        editor.setEditable(!busy);
        stateLabel.setText(busy ? "Processando..." : dirty ? "Alteracoes ainda nao aplicadas." : "Sem alteracoes.");
    }

    private void close() {
        tabs.getTabs().remove(tab);
        tabs.getSelectionModel().select(0);
        host.closeToolPanel();
        item = null;
        tab = null;
        editor = null;
        applyButton = null;
        saveButton = null;
        cancelButton = null;
        stateLabel = null;
        originalText = null;
        dirty = false;
        busy = false;
    }
}
