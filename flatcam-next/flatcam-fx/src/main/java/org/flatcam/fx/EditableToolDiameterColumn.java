package org.flatcam.fx;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;

/** Keeps drafts outside recycled cells so an invalid or unfinished edit cannot generate with the old diameter. */
final class EditableToolDiameterColumn<R> {
    private final TableView<R> table;
    private final ToDoubleFunction<R> read;
    private final ObjDoubleConsumer<R> write;
    private final Consumer<String> message;
    private final Map<R, String> drafts = new IdentityHashMap<>();
    private boolean reportedError;

    EditableToolDiameterColumn(TableView<R> table, TableColumn<R, Double> column,
                               ToDoubleFunction<R> read, ObjDoubleConsumer<R> write, Consumer<String> message) {
        this.table = table; this.read = read; this.write = write; this.message = message;
        table.setEditable(true);
        table.getColumns().forEach(other -> other.setEditable(other == column));
        column.setEditable(true);
        column.setCellFactory(ignored -> new DiameterCell());
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F2 && table.getEditingCell() == null) {
                int row = table.getSelectionModel().getSelectedIndex();
                if (row >= 0) { table.edit(row, column); event.consume(); }
            }
        });
        table.getItems().addListener((javafx.collections.ListChangeListener<R>) change ->
                drafts.keySet().removeIf(row -> !table.getItems().contains(row)));
    }

    /** Validate the whole table before updating any row; Generate also calls this before reading settings. */
    boolean commit() {
        Map<R, Double> values = new IdentityHashMap<>();
        for (int i = 0; i < table.getItems().size(); i++) {
            R row = table.getItems().get(i);
            double value;
            try {
                value = drafts.containsKey(row) ? Double.parseDouble(drafts.get(row).trim().replace(',', '.')) : read.applyAsDouble(row);
                if (!Double.isFinite(value) || value <= 0) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                return reject(String.format(I18n.text("tool.diameter.invalid"), i + 1));
            }
            if (values.values().stream().anyMatch(other -> Math.abs(other - value) < 1e-6))
                return reject(String.format(I18n.text("tool.diameter.duplicate"), i + 1));
            values.put(row, value);
        }
        boolean changed = !drafts.isEmpty();
        values.forEach((row, value) -> { if (read.applyAsDouble(row) != value) write.accept(row, value); });
        drafts.clear();
        if (reportedError) { message.accept(""); reportedError = false; }
        table.edit(-1, null);
        if (changed) table.refresh();
        return true;
    }

    private boolean reject(String text) { reportedError = true; message.accept(text); return false; }
    private String text(R row) { return drafts.getOrDefault(row, Double.toString(read.applyAsDouble(row))); }

    private final class DiameterCell extends TableCell<R, Double> {
        private final TextField editor = new TextField();
        private R editedRow;
        private boolean loading;
        DiameterCell() {
            editor.setMinWidth(0); editor.setMaxWidth(Double.MAX_VALUE);
            editor.setMinHeight(0); editor.setPrefHeight(UiFonts.rowHeight(22));
            editor.setId("tool-diameter-editor");
            ToolDescriptions.apply(this, I18n.text("ui.7cd23abc62e0"), I18n.text("tool.diameter.edit.help"));
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !isEmpty() && !isEditing()) {
                    table.edit(getIndex(), getTableColumn()); event.consume();
                }
            });
            editor.textProperty().addListener((obs, old, value) -> {
                if (!loading && editedRow != null) drafts.put(editedRow, value);
            });
            editor.setOnAction(event -> { finish(); event.consume(); });
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    drafts.remove(editedRow);
                    table.edit(-1, null); cancelEdit();
                    if (reportedError) { message.accept(""); reportedError = false; }
                    table.requestFocus(); event.consume();
                } else if (event.getCode() == KeyCode.TAB && !finish()) event.consume();
            });
            editor.focusedProperty().addListener((obs, old, focused) -> {
                if (!focused && editedRow != null && drafts.containsKey(editedRow)) finish();
            });
        }
        private R row() { return getIndex() >= 0 && getIndex() < table.getItems().size() ? table.getItems().get(getIndex()) : null; }
        @Override public void startEdit() {
            if (isEmpty() || row() == null) return;
            super.startEdit();
            if (!isEditing()) return;
            editedRow = row(); loading = true; editor.setText(text(editedRow)); loading = false;
            setText(null); setGraphic(editor);
            Platform.runLater(() -> { if (isEditing()) { editor.requestFocus(); editor.selectAll(); } });
        }
        private boolean finish() {
            if (editedRow == null) return true;
            R edited = editedRow;
            if (!commit()) return false;
            super.commitEdit(read.applyAsDouble(edited));
            editedRow = null; setGraphic(null); setText(row() == null ? null : text(row()));
            return true;
        }
        @Override public void cancelEdit() {
            super.cancelEdit();
            // Focus/selection changes may cancel JavaFX editing before focusLost. Keep the draft for validation.
            editedRow = null; setGraphic(null); setText(row() == null ? null : text(row()));
        }
        @Override protected void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || row() == null) { setText(null); setGraphic(null); }
            else if (isEditing()) { setText(null); setGraphic(editor); }
            else { setGraphic(null); setText(text(row())); }
        }
    }
}
