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
    private R invalidRow;
    private static final javafx.css.PseudoClass INVALID = javafx.css.PseudoClass.getPseudoClass("invalid");
    private static final String REVISION = EditableToolDiameterColumn.class.getName() + ".revision";
    private static javafx.beans.property.SimpleLongProperty revisionProperty(TableView<?> table) {
        return (javafx.beans.property.SimpleLongProperty) table.getProperties().computeIfAbsent(REVISION, key -> new javafx.beans.property.SimpleLongProperty());
    }
    static javafx.beans.value.ObservableValue<Number> revision(TableView<?> table) { return revisionProperty(table); }
    private void changed() { var revision = revisionProperty(table); revision.set(revision.get() + 1); }

    EditableToolDiameterColumn(TableView<R> table, TableColumn<R, Double> column,
                               ToDoubleFunction<R> read, ObjDoubleConsumer<R> write, Consumer<String> message) {
        this.table = table; this.read = read; this.write = write; this.message = message;
        table.setEditable(true);
        table.getColumns().forEach(other -> other.setEditable(other == column));
        column.setEditable(true);
        column.setCellFactory(ignored -> new DiameterCell());
        var correct = new MenuItem(I18n.text("tool.diameter.correct"));
        var discard = new MenuItem(I18n.text("tool.diameter.discard"));
        correct.setOnAction(event -> {
            int index = table.getItems().indexOf(invalidRow);
            if (index >= 0) { table.scrollTo(index); table.edit(index, column); }
        });
        discard.setOnAction(event -> {
            drafts.remove(invalidRow); invalidRow = null; reportedError = false;
            changed();
            message.accept(""); table.edit(-1, null); table.refresh();
        });
        var menu = table.getContextMenu();
        if (menu == null) { menu = new ContextMenu(); table.setContextMenu(menu); }
        menu.getItems().addAll(correct, discard);
        menu.setOnShowing(event -> { correct.setDisable(invalidRow == null); discard.setDisable(invalidRow == null); });
        table.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY && invalidRow != null) event.consume();
        });
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.getCode() == KeyCode.F2 || event.getCode() == KeyCode.ENTER) && TableKeyboardActions.plain(event)
                    && !TableKeyboardActions.inputTarget(event.getTarget()) && table.getEditingCell() == null) {
                int focused = table.getFocusModel().getFocusedIndex();
                int row = table.getSelectionModel().isSelected(focused) ? focused : table.getSelectionModel().getSelectedIndex();
                if (row >= 0) { table.getFocusModel().focus(row, column); table.edit(row, column); event.consume(); }
            }
        });
        table.getItems().addListener((javafx.collections.ListChangeListener<R>) change -> {
            drafts.keySet().removeIf(row -> !table.getItems().contains(row));
            if (invalidRow != null && !table.getItems().contains(invalidRow)) {
                invalidRow = null; reportedError = false; message.accept("");
            }
        });
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
                return reject(row, String.format(I18n.text("tool.diameter.invalid"), i + 1));
            }
            var duplicate = values.entrySet().stream().filter(other -> Math.abs(other.getValue() - value) < 1e-6).findFirst();
            if (duplicate.isPresent()) {
                R culprit = drafts.containsKey(row) ? row : drafts.containsKey(duplicate.get().getKey()) ? duplicate.get().getKey() : row;
                return reject(culprit, String.format(I18n.text("tool.diameter.duplicate"), table.getItems().indexOf(culprit) + 1));
            }
            values.put(row, value);
        }
        boolean changed = !drafts.isEmpty();
        values.forEach((row, value) -> { if (read.applyAsDouble(row) != value) write.accept(row, value); });
        drafts.clear();
        invalidRow = null;
        if (changed) changed();
        if (reportedError) { message.accept(""); reportedError = false; }
        table.edit(-1, null);
        if (changed) table.refresh();
        return true;
    }

    private boolean reject(R row, String text) {
        invalidRow = row; reportedError = true; message.accept(text);
        table.scrollTo(table.getItems().indexOf(row)); table.refresh(); return false;
    }
    private String text(R row) { return drafts.getOrDefault(row, Double.toString(read.applyAsDouble(row))); }
    String draft(R row) { return drafts.get(row); }
    void restoreDraft(R row, String text) { if (text != null) { drafts.put(row, text); changed(); } }

    private final class DiameterCell extends TableCell<R, Double> {
        private final TextField editor = new TextField();
        private R editedRow;
        private boolean loading;
        DiameterCell() {
            editor.setMinWidth(0); editor.setMaxWidth(Double.MAX_VALUE);
            editor.setMinHeight(0); editor.setPrefHeight(UiFonts.rowHeight(22));
            editor.setId("tool-diameter-editor");
            editor.getStyleClass().add("tool-diameter-input");
            ToolDescriptions.apply(this, I18n.text("ui.7cd23abc62e0"), I18n.text("tool.diameter.edit.help"));
            ToolDescriptions.apply(editor, I18n.text("ui.7cd23abc62e0"), I18n.text("tool.diameter.edit.help"));
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !isEmpty() && !isEditing()) {
                    table.edit(getIndex(), getTableColumn()); event.consume();
                }
            });
            editor.textProperty().addListener((obs, old, value) -> {
                if (!loading && editedRow != null) { drafts.put(editedRow, value); changed(); }
            });
            editor.setOnAction(event -> { if (finish()) table.requestFocus(); event.consume(); });
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    boolean clearsError = invalidRow == editedRow;
                    drafts.remove(editedRow);
                    changed();
                    if (clearsError) invalidRow = null;
                    table.edit(-1, null); cancelEdit();
                    if (reportedError && clearsError) { message.accept(""); reportedError = false; }
                    table.requestFocus(); event.consume();
                } else if (event.getCode() == KeyCode.TAB && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
                    int next = getIndex() + (event.isShiftDown() ? -1 : 1);
                    if (finish()) {
                        if (next >= 0 && next < table.getItems().size()) {
                            table.getSelectionModel().clearAndSelect(next); table.scrollTo(next);
                            table.getFocusModel().focus(next, getTableColumn()); table.edit(next, getTableColumn());
                        } else {
                            table.requestFocus(); table.requestFocusTraversal(event.isShiftDown()
                                    ? javafx.scene.TraversalDirection.PREVIOUS : javafx.scene.TraversalDirection.NEXT);
                        }
                    }
                    event.consume();
                }
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
            pseudoClassStateChanged(INVALID, !empty && row() == invalidRow);
            editor.pseudoClassStateChanged(INVALID, !empty && row() == invalidRow);
            if (empty || row() == null) { setText(null); setGraphic(null); }
            else if (isEditing()) { setText(null); setGraphic(editor); }
            else { setGraphic(null); setText(text(row())); }
        }
    }
}
