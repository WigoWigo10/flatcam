package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
    private CodeEditor editor;
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
            host.log(I18n.text("ui.d64f50edc999"));
            return;
        }
        item = selectedItem;
        originalText = CodeEditor.normalize(gcode);
        editor = new CodeEditor(gcode, CodeSyntax.Language.MACHINE, true);
        editor.onEdit(() -> {
            dirty = true;
            updateButtons();
        });
        editor.loadingProperty().addListener((observable, oldValue, value) -> updateButtons());
        tab = new Tab(I18n.text("ui.5385251c8ab1") + item.getValue(), editor);
        tab.setClosable(false);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);

        Label info = new Label(I18n.text("ui.82fedfc671f6") + item.getValue());
        info.setWrapText(true);
        Label help = new Label(I18n.text("ui.0812ae6fee63"));
        help.setWrapText(true);
        var details = new javafx.scene.control.TitledPane(I18n.text("ui.cf022c5a9525"), help);
        details.setExpanded(false);
        Label hint = new Label(I18n.text("ui.63c04841d645"));
        hint.setWrapText(true);
        stateLabel = new Label();
        stateLabel.setWrapText(true);
        applyButton = new Button(I18n.text("ui.250a5bfb7e6f"));
        saveButton = new Button(I18n.text("ui.e9a460daf1f0"));
        cancelButton = new Button(I18n.text("ui.f1cb621ee9d8"));
        for (Button button : new Button[]{applyButton, saveButton, cancelButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        applyButton.setOnAction(event -> apply());
        saveButton.setOnAction(event -> saveAs());
        cancelButton.setOnAction(event -> cancel());
        VBox panel = new VBox(10, info, hint, stateLabel, applyButton, saveButton, cancelButton, details);
        panel.setPadding(new Insets(12));
        host.openToolPanel(I18n.text("ui.7d9970b93974"), panel);
        updateButtons();
        editor.focusCode();
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
        host.log(I18n.text("ui.798532c4fd98"));
    }

    void saveAndClose() {
        apply();
    }

    private void apply() {
        if (!isActive() || busy || editor.isLoading()) {
            return;
        }
        if (!dirty || editor.getText().equals(originalText) || editor.getText().isBlank()) {
            if (editor.getText().isBlank()) {
                stateLabel.setText(I18n.text("ui.4fed05d3f81d"));
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
        if (!isActive() || busy || editor.isLoading() || editor.getText().isBlank()) {
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
        applyButton.setDisable(busy || editor.isLoading() || !dirty);
        saveButton.setDisable(busy || editor.isLoading() || editor.area().getLength() == 0);
        cancelButton.setDisable(busy);
        editor.setEditable(!busy);
        stateLabel.setText(busy ? "Processando..." : editor.isLoading() ? I18n.text("ui.e6888121aa1b")
                : dirty ? I18n.text("ui.e622c39b0ae4") : I18n.text("ui.ffda585087e1"));
    }

    private void close() {
        editor.close();
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
