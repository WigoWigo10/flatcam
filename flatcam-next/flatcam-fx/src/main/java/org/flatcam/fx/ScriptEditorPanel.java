package org.flatcam.fx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Python's Script Editor tab (ScriptObject + AppTextEditor): an editable Tcl script with Run Code,
 * Save and Save As. Run sends the whole text to the Terminal, which evaluates it as one script with
 * the Terminal's own worker/cancellation - Python's Run Code feeds lines one by one and treats a
 * failing line as an unfinished multi-line command; this port reports the first error and stops,
 * since the dialect already parses multi-line commands. Unlike Python, a script is a file in an
 * editor tab, not a project object: it is not saved inside the project.
 */
final class ScriptEditorPanel extends BorderPane implements AutoCloseable {

    interface Host {
        /** Runs the text in the Terminal; false when another script is still running. */
        boolean run(String script, String label);

        /** Asks for a destination; null when cancelled. */
        Path chooseSaveTarget(Path suggested);

        void log(String message);
    }

    /** Python's new_script_object template. */
    static final String NEW_SCRIPT_TEMPLATE = "# CREATE A NEW FLATCAM TCL SCRIPT\n"
            + "# TCL Tutorial is here:\n"
            + "# https://www.tcl.tk/man/tcl8.5/tutorial/tcltutorial.html\n\n\n"
            + "# FlatCAM commands list:\n"
            + "# Type >help< followed by Run Code for a list of FlatCAM Tcl Commands (displayed in Tcl Shell).\n\n";

    private final CodeEditor editor;
    private final Host host;
    private final Label status = new Label();
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private Path file;
    private String savedText;

    ScriptEditorPanel(String text, Path file, Host host) {
        this.host = host;
        this.file = file;
        this.savedText = file == null ? null : CodeEditor.normalize(text);
        editor = new CodeEditor(text, CodeSyntax.Language.TCL, true);
        editor.setId("script-editor-code");
        editor.onEdit(this::updateState);
        Button run = new Button(I18n.text("ui.608d26670525"));
        run.setId("script-run");
        ToolDescriptions.apply(run, I18n.text("ui.e45be90cbb56"), I18n.text("ui.726c6034e97d"));
        run.setOnAction(event -> runScript());
        Button save = new Button(I18n.text("ui.aef7cd5b2081"));
        save.setId("script-save");
        ToolDescriptions.apply(save, I18n.text("ui.c2cf287d7bdd"), I18n.text("ui.593db9f0e6f8"));
        save.setOnAction(event -> save());
        Button saveAs = new Button(I18n.text("ui.cb5b463bee77"));
        saveAs.setId("script-save-as");
        ToolDescriptions.apply(saveAs, I18n.text("ui.26e45b210c0e"), I18n.text("ui.677908836d79"));
        saveAs.setOnAction(event -> saveAs());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.setId("script-status");
        ToolDescriptions.apply(status, I18n.text("ui.8d9aa6242f04"), I18n.text("ui.8ea08a147d56"));
        HBox bar = new HBox(6, run, save, saveAs, spacer, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4));
        setTop(bar);
        setCenter(editor);
        updateState();
    }

    String text() { return editor.getText(); }
    Path file() { return file; }
    boolean isDirty() { return dirty.get(); }
    ReadOnlyBooleanProperty dirtyProperty() { return dirty.getReadOnlyProperty(); }
    void focusCode() { editor.focusCode(); }

    String title() {
        return (file == null ? "new_script" : file.getFileName().toString()) + (isDirty() ? " *" : "");
    }

    boolean runScript() {
        String label = file == null ? "new_script" : file.getFileName().toString();
        boolean started = host.run(editor.getText(), label);
        if (!started) host.log(I18n.text("ui.cea33c3a84fd"));
        return started;
    }

    boolean save() {
        return file == null ? saveAs() : write(file);
    }

    boolean saveAs() {
        Path target = host.chooseSaveTarget(file);
        return target != null && write(target);
    }

    private boolean write(Path target) {
        String text = editor.getText();
        Path destination = target.toAbsolutePath();
        try {
            if (destination.getParent() == null || !Files.isDirectory(destination.getParent()))
                throw new IOException(I18n.text("ui.21335b513c7c") + destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-script-", ".tmp");
            try {
                Files.writeString(temporary, text, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException error) {
            host.log(I18n.text("ui.0d70017fe561") + error.getMessage());
            return false;
        }
        file = destination;
        savedText = CodeEditor.normalize(text);
        updateState();
        host.log(I18n.text("ui.1dd5325fe2a0") + destination);
        return true;
    }

    private void updateState() {
        dirty.set(savedText == null || !savedText.equals(CodeEditor.normalize(editor.getText())));
        status.setText((file == null ? I18n.text("ui.bf1952d25340") : file.toString()) + (isDirty() ? I18n.text("ui.02486a6bd4ce") : ""));
    }

    @Override
    public void close() {
        editor.close();
    }
}
