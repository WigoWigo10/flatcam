package org.flatcam.fx;

import java.util.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;

/** Bounded, session-local history of committed CAM rows. Text controls retain their own undo. */
final class CamTableHistory<S> {
    private record State<S>(List<S> rows, List<Integer> selected) { }
    private final TableView<?> table;
    private final Supplier<List<S>> capture;
    private final Consumer<List<S>> restore;
    private final BooleanSupplier commit;
    private final BooleanSupplier loading;
    private final Deque<State<S>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private State<S> current;
    private boolean restoring, pending;
    private int transaction;
    private final MenuItem undoItem = new MenuItem(I18n.text("cam.table.undo"));
    private final MenuItem redoItem = new MenuItem(I18n.text("cam.table.redo"));

    CamTableHistory(Node form, TableView<?> table, Supplier<List<S>> capture,
                    Consumer<List<S>> restore, EditableToolDiameterColumn<?> diameter, BooleanSupplier loading) {
        this.table = table; this.capture = capture; this.restore = restore; this.commit = diameter::commit;
        this.loading = loading;
        reset(); diameter.onCommit(this::perform);
        table.getItems().addListener((ListChangeListener<Object>) change -> changed());
        table.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change -> changed());
        // Capture before an action as well as after it: consecutive clicks remain separate undo steps.
        form.addEventFilter(ActionEvent.ACTION, event -> { checkpoint(); changed(); });
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isShortcutDown() || event.isAltDown() || TableKeyboardActions.inputTarget(event.getTarget())) return;
            if (event.getCode() == KeyCode.Z) { travel(event.isShiftDown()); event.consume(); }
            else if (event.getCode() == KeyCode.Y && !event.isShiftDown()) { travel(true); event.consume(); }
        });
        var menu = table.getContextMenu();
        if (menu == null) { menu = new ContextMenu(); table.setContextMenu(menu); }
        menu.getItems().addAll(new SeparatorMenuItem(), undoItem, redoItem);
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, event -> checkpoint());
        undoItem.setOnAction(event -> travel(false)); redoItem.setOnAction(event -> travel(true));
        ToolDescriptions.apply(undoItem.getProperties(), undoItem.getText(), I18n.text("cam.table.history.help"));
        ToolDescriptions.apply(redoItem.getProperties(), redoItem.getText(), I18n.text("cam.table.history.help"));
        String help = table.getAccessibleHelp();
        ToolDescriptions.apply(table, I18n.text("ui.4957942fd71b"),
                (help == null || help.isBlank() ? "" : help + "\n\n") + I18n.text("cam.table.history.help"));
        updateMenu();
    }

    void observe(ObservableValue<?>... values) {
        for (var value : values) value.addListener((obs, old, next) -> changed());
    }
    void watchText(TextInputControl... inputs) {
        for (var input : inputs) input.focusedProperty().addListener((obs, old, focused) -> { if (!focused) checkpoint(); });
    }
    void changed() {
        if (restoring || pending) return;
        pending = true;
        Platform.runLater(() -> { pending = false; checkpoint(); });
    }
    void reset() { current = snapshot(); undo.clear(); redo.clear(); updateMenu(); }
    void perform(Runnable action) {
        if (restoring || transaction > 0) { action.run(); return; }
        checkpoint(); transaction++;
        try { action.run(); } finally { transaction--; checkpoint(); }
    }
    private State<S> snapshot() {
        return new State<>(List.copyOf(capture.get()), List.copyOf(table.getSelectionModel().getSelectedIndices()));
    }
    private void checkpoint() {
        if (restoring || transaction > 0 || loading.getAsBoolean()) return;
        var next = snapshot();
        if (!next.rows().equals(current.rows())) {
            undo.addLast(current); if (undo.size() > 100) undo.removeFirst(); redo.clear();
        }
        current = next; updateMenu();
    }
    private void travel(boolean forward) {
        if (!commit.getAsBoolean()) return;
        checkpoint();
        var from = forward ? redo : undo; var to = forward ? undo : redo;
        if (from.isEmpty()) return;
        var target = from.removeLast(); to.addLast(current);
        restoring = true;
        try {
            table.edit(-1, null); restore.accept(target.rows());
            table.getSelectionModel().clearSelection();
            for (int index : target.selected()) if (index >= 0 && index < table.getItems().size()) table.getSelectionModel().select(index);
            table.refresh(); table.requestFocus(); current = snapshot();
        } finally { restoring = false; updateMenu(); }
    }
    private void updateMenu() { undoItem.setDisable(undo.isEmpty()); redoItem.setDisable(redo.isEmpty()); }
}
