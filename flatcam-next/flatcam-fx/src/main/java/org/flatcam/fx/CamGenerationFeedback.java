package org.flatcam.fx;

import java.util.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

/** Keeps the submitted recipe distinct from the editable draft, including edits made while a job runs. */
final class CamGenerationFeedback extends VBox {
    private static final String KEY = CamGenerationFeedback.class.getName();
    private final Label status = new Label();
    private final TextArea recipe = new TextArea();
    private final TitledPane details;
    private final Button show = new Button(I18n.text("cam.result.show"));
    private final Button previous = new Button(I18n.text("cam.result.previous"));
    private final Node form;
    private final Supplier<String> rows;
    private String submitted, submittedRecipe;
    private TreeItem<String> submittedSource;
    private boolean busy, queued;
    private record Output(TreeItem<String> item, Consumer<TreeItem<String>> show, String recipe) { }
    private final List<Output> outputs = new ArrayList<>();

    CamGenerationFeedback(VBox form, Supplier<String> rows) {
        super(6); this.form = form; this.rows = rows; setId("cam-generation-feedback");
        status.setId("cam-generation-status"); status.setWrapText(true);
        status.getStyleClass().add("cam-generation-status");
        recipe.setId("cam-generation-recipe"); recipe.setEditable(false); recipe.setWrapText(true); recipe.setPrefRowCount(8);
        Button copy = new Button(I18n.text("cam.result.copy"));
        show.setId("cam-show-current"); previous.setId("cam-show-previous");
        for (Button button : List.of(copy, show, previous)) {
            button.setMaxWidth(Double.MAX_VALUE); button.setWrapText(true);
        }
        ToolDescriptions.apply(copy, copy.getText(), I18n.text("cam.result.copy.help"));
        ToolDescriptions.apply(show, show.getText(), I18n.text("cam.result.show.help"));
        ToolDescriptions.apply(previous, previous.getText(), I18n.text("cam.result.show.help"));
        copy.setOnAction(event -> { var content = new ClipboardContent(); content.putString(recipe.getText()); Clipboard.getSystemClipboard().setContent(content); });
        show.setOnAction(event -> { if (!outputs.isEmpty()) { var output = outputs.getLast(); output.show().accept(output.item()); } });
        previous.setOnAction(event -> { if (outputs.size() > 1) { var output = outputs.get(outputs.size() - 2); output.show().accept(output.item()); } });
        details = new TitledPane(I18n.text("cam.result.recipe"), new VBox(6, recipe, copy, show, previous));
        details.setExpanded(false); getChildren().addAll(status, details);
        setVisible(false); managedProperty().bind(visibleProperty());
        watch(form); form.getProperties().put(KEY, this);
    }

    static CamGenerationFeedback of(Node panel) {
        Node form = panel instanceof ScrollPane scroll ? scroll.getContent() : panel;
        return form == null ? null : (CamGenerationFeedback) form.getProperties().get(KEY);
    }
    void started(String summary, TreeItem<String> source) {
        if (submittedSource != source) outputs.clear();
        submittedSource = source;
        submitted = fingerprint(form); submittedRecipe = summary; busy = true;
        recipe.setText(summary); setVisible(true); refresh();
    }
    void completed(TreeItem<String> item, Consumer<TreeItem<String>> showOutput) {
        busy = false;
        String difference = outputs.isEmpty() ? "" : difference(outputs.getLast().recipe(), submittedRecipe);
        outputs.add(new Output(item, showOutput, submittedRecipe));
        recipe.setText(item.getValue() + "\n" + submittedRecipe + difference); refresh();
    }
    private static String difference(String before, String after) {
        // Version/time identify the run, not a parameter change. Preserve the complete submitted recipe above.
        var oldLines = before.lines().skip(2).toList(); var newLines = after.lines().skip(2).toList();
        var oldSet = new HashSet<>(oldLines); var newSet = new HashSet<>(newLines);
        var removed = oldLines.stream().filter(line -> !newSet.contains(line)).toList();
        var added = newLines.stream().filter(line -> !oldSet.contains(line)).toList();
        if (removed.isEmpty() && added.isEmpty()) return "\n\n" + I18n.text("cam.result.same");
        return "\n\n" + I18n.text("cam.result.difference") + "\n" + I18n.text("cam.result.before") + "\n"
                + String.join("\n", removed) + "\n" + I18n.text("cam.result.after") + "\n" + String.join("\n", added);
    }
    void stopped() {
        if (!busy) return;
        busy = false; submitted = null; status.setText(I18n.text("cam.result.stopped"));
    }
    private void refresh() {
        if (submitted == null) return;
        boolean changed = !submitted.equals(fingerprint(form));
        status.setText(I18n.text(busy ? changed ? "cam.result.running.changed" : "cam.result.running"
                : changed ? "cam.result.changed" : "cam.result.current"));
        show.setDisable(busy || outputs.isEmpty()); previous.setDisable(busy || outputs.size() < 2);
    }
    private void schedule() {
        if (queued) return; queued = true;
        Platform.runLater(() -> { queued = false; refresh(); });
    }
    private void watch(Node node) {
        if (node == this) return;
        if (node instanceof Spinner<?> spinner) { watch(spinner.getEditor()); return; }
        if (node instanceof TextInputControl text) text.textProperty().addListener((o,a,b) -> schedule());
        else if (node instanceof ComboBox<?> combo) combo.valueProperty().addListener((o,a,b) -> schedule());
        else if (node instanceof CheckBox check) check.selectedProperty().addListener((o,a,b) -> schedule());
        else if (node instanceof RadioButton radio) radio.selectedProperty().addListener((o,a,b) -> schedule());
        else if (node instanceof TableView<?> table) {
            EditableToolDiameterColumn.revision(table).addListener((o,a,b) -> schedule());
            table.getItems().addListener((javafx.collections.ListChangeListener<Object>) change -> schedule());
            table.getSelectionModel().getSelectedIndices().addListener((javafx.collections.ListChangeListener<Integer>) change -> schedule());
            table.editingCellProperty().addListener((o,a,b) -> schedule()); return;
        }
        if (node instanceof Parent p) for (Node child : p.getChildrenUnmodifiable()) watch(child);
    }
    private String fingerprint(Node node) {
        if (node == this) return "";
        if (node instanceof Spinner<?> spinner) return "S[" + spinner.getEditor().getText() + "]";
        if (node instanceof TextInputControl text) return "T[" + text.getText() + "]";
        if (node instanceof ComboBox<?> combo) return "C[" + combo.getValue() + "]";
        if (node instanceof CheckBox check) return "B" + check.isSelected();
        if (node instanceof RadioButton radio) return "R" + radio.isSelected();
        if (node instanceof TableView<?> table) return "ROWS[" + rows.get() + "]" + table.getSelectionModel().getSelectedIndices();
        StringBuilder result = new StringBuilder();
        if (node instanceof Parent p) for (Node child : p.getChildrenUnmodifiable()) result.append(fingerprint(child));
        return result.toString();
    }
}
