package org.flatcam.fx;

import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;

/** Edits one CNC Job; applying to the project and explicitly saving to disk are separate actions. */
final class GCodeEditorController {

    record SaveResult(Path file, String message) { }

    interface Host {
        void openToolPanel(String label, Node content);

        void closeToolPanel();

        boolean apply(TreeItem<String> item, String text, Runnable onSuccess, Consumer<String> onFailure);

        boolean save(TreeItem<String> item, String text, Path target, boolean copy, Consumer<SaveResult> onComplete);

        void log(String message);
    }

    private final TabPane tabs;
    private final Host host;
    private TreeItem<String> item;
    private Tab tab;
    private CodeEditor editor;
    private Button applyButton;
    private Button saveButton;
    private Button saveAsButton;
    private Button cancelButton;
    private Label stateLabel;
    private Label fileLabel;
    private Path file;
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
        start(selectedItem,gcode,null);
    }

    void start(TreeItem<String> selectedItem, String gcode, Path originalFile) {
        if (isActive()) {
            tabs.getSelectionModel().select(tab);
            host.log(I18n.text("ui.d64f50edc999"));
            return;
        }
        item = selectedItem;
        file = originalFile;
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
        saveButton = new Button(I18n.text("gcode.save"));
        saveButton.setId("gcode-save");
        ToolDescriptions.apply(saveButton,I18n.text("gcode.save"),I18n.text("gcode.save.help"));
        saveAsButton = new Button(I18n.text("gcode.saveAs"));
        saveAsButton.setId("gcode-save-as");
        ToolDescriptions.apply(saveAsButton,I18n.text("gcode.saveAs"),I18n.text("gcode.saveAs.help"));
        fileLabel = new Label(); fileLabel.setId("gcode-file"); fileLabel.setWrapText(true);
        updateFileLabel();
        cancelButton = new Button(I18n.text("ui.f1cb621ee9d8"));
        cancelButton.setId("gcode-cancel");
        ToolDescriptions.apply(cancelButton,I18n.text("ui.f1cb621ee9d8"),I18n.text("gcode.cancel.help"));
        for (Button button : new Button[]{applyButton, saveButton, saveAsButton, cancelButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        applyButton.setOnAction(event -> apply());
        saveButton.setOnAction(event -> save(false));
        saveAsButton.setOnAction(event -> save(true));
        cancelButton.setOnAction(event -> cancel());
        VBox panel = new VBox(10, info, fileLabel, hint, stateLabel, applyButton, saveButton, saveAsButton, cancelButton, details);
        panel.setPadding(new Insets(12));
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> saveShortcut = event -> {
            if (event.isShortcutDown() && !event.isAltDown() && event.getCode() == javafx.scene.input.KeyCode.S) {
                save(event.isShiftDown()); event.consume();
            }
        };
        editor.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,saveShortcut);
        panel.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,saveShortcut);
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

    void save(boolean copy) {
        if (!isActive() || busy || editor.isLoading() || editor.getText().isBlank()) {
            return;
        }
        String text = editor.getText();
        TreeItem<String> editing = item;
        busy = true;
        updateButtons();
        if (!host.save(editing, text, copy ? null : file, copy, result -> {
            if (item == editing) {
                if (result.file() != null && !copy) { file = result.file(); updateFileLabel(); }
                busy = false;
                updateButtons();
                stateLabel.setText(result.message());
            }
        })) {
            busy = false;
            updateButtons();
        }
    }

    private void updateFileLabel() {
        fileLabel.setText(file == null ? I18n.text("gcode.file.none") : I18n.text("gcode.file.current") + file);
        fileLabel.setAccessibleHelp(fileLabel.getText());
    }

    private void updateButtons() {
        if (applyButton == null) {
            return;
        }
        applyButton.setDisable(busy || editor.isLoading() || !dirty);
        saveButton.setDisable(busy || editor.isLoading() || editor.area().getLength() == 0);
        saveAsButton.setDisable(saveButton.isDisabled());
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
        saveAsButton = null;
        fileLabel = null;
        file = null;
        cancelButton = null;
        stateLabel = null;
        originalText = null;
        dirty = false;
        busy = false;
    }
}
