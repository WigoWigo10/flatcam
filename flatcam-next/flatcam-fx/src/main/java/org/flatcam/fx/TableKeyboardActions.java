package org.flatcam.fx;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;

/** Keyboard actions belong to the focused table, never to an unrelated plot selection. */
final class TableKeyboardActions {
    private static final String SHARED_HISTORY = TableKeyboardActions.class.getName() + ".editorHistory";
    private TableKeyboardActions() { }

    static boolean inputTarget(Object target) {
        if (!(target instanceof Node node)) return false;
        for (Node current = node; current != null && !(current instanceof TableView<?>); current = current.getParent())
            if (current instanceof TextInputControl || current instanceof ComboBoxBase<?> || current instanceof ChoiceBox<?>
                    || current instanceof Spinner<?> || current instanceof ButtonBase) return true;
        return false;
    }

    static boolean plain(KeyEvent event) {
        return !event.isControlDown() && !event.isAltDown() && !event.isMetaDown() && !event.isShiftDown();
    }

    static boolean prefersLocalHandling(KeyEvent event) {
        if (!(event.getTarget() instanceof Node node)) return false;
        for (Node current = node; current != null; current = current.getParent()) if (current instanceof TableView<?> table) {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) return false;
            if (event.isShortcutDown() && (event.getCode() == KeyCode.Z || event.getCode() == KeyCode.Y)
                    && Boolean.TRUE.equals(table.getProperties().get(SHARED_HISTORY))) return false;
            // Escape still cancels a plot placement when no table cell/input is being edited.
            if (event.getCode() == KeyCode.ESCAPE && table.getEditingCell() == null && !inputTarget(event.getTarget())) return false;
            return true;
        }
        return false;
    }

    static void deleteWith(TableView<?> table, Button button) { deleteWith(table, button, false); }

    static void deleteWith(TableView<?> table, Button button, boolean sharedEditorHistory) {
        table.getProperties().put(SHARED_HISTORY, sharedEditorHistory);
        String existingHelp = button.getAccessibleHelp();
        ToolDescriptions.apply(button, button.getText(), (existingHelp == null || existingHelp.isBlank() ? "" : existingHelp + "\n\n")
                + I18n.text("table.delete.help"));
        var menu = table.getContextMenu();
        if (menu == null) { menu = new ContextMenu(); table.setContextMenu(menu); }
        var existing = menu.getItems().stream().filter(item -> button.getText().equals(item.getText())).findFirst();
        var item = existing.orElseGet(MenuItem::new); item.setText(I18n.text("table.delete.selected"));
        item.disableProperty().bind(button.disabledProperty());
        ToolDescriptions.apply(item.getProperties(), item.getText(), button.getAccessibleHelp());
        item.setOnAction(event -> invokeDelete(table, button));
        if (existing.isEmpty()) {
            if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
            menu.getItems().add(item);
        }
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.DELETE || !plain(event) || inputTarget(event.getTarget())) return;
            invokeDelete(table, button); event.consume();
        });
    }

    private static void invokeDelete(TableView<?> table, Button button) {
        int anchor = table.getSelectionModel().getSelectedIndices().stream().mapToInt(Integer::intValue).min().orElse(-1);
        int count = table.getItems().size();
        if (!button.isDisabled()) {
            table.edit(-1, null); button.fire();
            if (table.getItems().size() < count && !table.getItems().isEmpty())
                table.getSelectionModel().clearAndSelect(Math.min(Math.max(anchor, 0), table.getItems().size() - 1));
        }
        table.requestFocus();
    }

    static <R> void removeSelected(TableView<R> table) {
        int anchor = table.getSelectionModel().getSelectedIndices().stream().mapToInt(Integer::intValue).min().orElse(-1);
        if (anchor < 0) return;
        var selected = table.getSelectionModel().getSelectedIndices().stream().sorted(java.util.Comparator.reverseOrder()).toList();
        table.edit(-1, null);
        // Equal-valued rows may be distinct entries (for example, two identical exclusion areas).
        for (int index : selected) table.getItems().remove(index);
        if (!table.getItems().isEmpty()) table.getSelectionModel().clearAndSelect(Math.min(anchor, table.getItems().size() - 1));
    }
}
