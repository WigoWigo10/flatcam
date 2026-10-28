package org.flatcam.fx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Help > Shortcut List (Python's on_shortcut_list / "Key Shortcut List" tab), listing only the keys this
 * port actually handles - MainWindow's scene filter and project tree, PlotAreaView, CodeEditor,
 * TerminalPanel and ToolsDatabasePanel. Python's own list has many more (its editors' tool letters, the
 * object-type hotkeys); those are not offered here until they are implemented. Keep this table in step
 * with those handlers.
 */
final class ShortcutListPanel extends VBox {

    record Shortcut(String keys, String action) { }

    static final Map<String, List<Shortcut>> GROUPS = groups();

    private static Map<String, List<Shortcut>> groups() {
        Map<String, List<Shortcut>> groups = new LinkedHashMap<>();
        groups.put(I18n.text("ui.2130a3b5e09e"), List.of(
                new Shortcut("Ctrl+N", I18n.text("ui.c260f7a00b7e")),
                new Shortcut("Ctrl+S", I18n.text("ui.077beb3fe78a")),
                new Shortcut("Ctrl+Shift+S", I18n.text("ui.0bcac467f215")),
                new Shortcut("Ctrl+P", I18n.text("ui.217ce43fce3a")),
                new Shortcut("Ctrl+A", I18n.text("ui.7d6f8fab06e5"))));
        groups.put(I18n.text("ui.722776b7616d"), List.of(
                new Shortcut(I18n.text("ui.375fbb91b842"), I18n.text("ui.bab958fa6832")),
                new Shortcut(I18n.text("ui.74945b3c433c"), I18n.text("ui.99461df705fa")),
                new Shortcut(I18n.text("ui.a4a65e55b24b"), I18n.text("ui.66945815e1e8")),
                new Shortcut(I18n.text("ui.06f8a2b4d661"), I18n.text("ui.5f779b9c2ee1")),
                new Shortcut("G", I18n.text("ui.c951eda47080")),
                new Shortcut("O", I18n.text("ui.ee01a7a58aa4")),
                new Shortcut("Shift+O", I18n.text("ui.6effd2f0f6e0")),
                new Shortcut("J", I18n.text("ui.939467b35f12")),
                new Shortcut("Shift+J", I18n.text("ui.430484a2c23f")),
                new Shortcut("Ctrl+M", I18n.text("ui.54c2c6b06815")),
                new Shortcut("Shift+M", I18n.text("ui.f5564c5ac4d5")),
                new Shortcut("Ctrl+Z / Ctrl+Y", I18n.text("ui.f264a1435ac7")),
                new Shortcut("Esc", I18n.text("ui.c9c43ea0683c"))));
        groups.put(I18n.text("ui.ced303d87e12"), List.of(
                new Shortcut("Enter", I18n.text("ui.6d5ce482d0c3")),
                new Shortcut("F2", I18n.text("ui.48eadfc22613")),
                new Shortcut("Delete", I18n.text("ui.3cdff6d5dd74")),
                new Shortcut(I18n.text("ui.f53807aa6cf5"), I18n.text("ui.b2d2e5d170a6")),
                new Shortcut("Esc", I18n.text("ui.33a042c0fd07")),
                new Shortcut("Ctrl+C", I18n.text("ui.95d5b7319427"))));
        groups.put(I18n.text("ui.27b62e49bc29"), List.of(
                new Shortcut("← / ↑", I18n.text("ui.f390829e7158")),
                new Shortcut("→ / ↓", I18n.text("ui.8979984026f0")),
                new Shortcut(I18n.text("ui.f53807aa6cf5"), I18n.text("ui.035f73fb5b2e")),
                new Shortcut("Esc", I18n.text("ui.5c93aded4a96"))));
        groups.put(I18n.text("ui.cd3a4d3d1222"), List.of(
                new Shortcut("Ctrl+Z / Ctrl+Y", I18n.text("ui.c535160781e2")),
                new Shortcut("Delete", I18n.text("ui.5f8bc1179e54")),
                new Shortcut("Enter", I18n.text("ui.b6483984ed5e")),
                new Shortcut("Backspace", I18n.text("ui.3ae5d6c86242")),
                new Shortcut("T / R", I18n.text("ui.c5c79ebd54e3")),
                new Shortcut("G", I18n.text("ui.dc71ce317db0"))));
        groups.put(I18n.text("ui.f271099e0af9"), List.of(
                new Shortcut("Ctrl+F", I18n.text("ui.5f55edf90089")),
                new Shortcut("F3 / Shift+F3", I18n.text("ui.03c1f0a31f1c")),
                new Shortcut(I18n.text("ui.0015b88adb11"), I18n.text("ui.03c1f0a31f1c")),
                new Shortcut("Esc", I18n.text("ui.54269ec489b5"))));
        groups.put("Terminal Tcl", List.of(
                new Shortcut("Enter", I18n.text("ui.b176c1ef5b17")),
                new Shortcut("↑ / ↓", I18n.text("ui.1c4eca7d513e")),
                new Shortcut("Esc", I18n.text("ui.6314f38654b2"))));
        groups.put("Tools Database", List.of(
                new Shortcut("Ctrl+S", I18n.text("ui.696c6ca28b9b")),
                new Shortcut("Ctrl+F", I18n.text("ui.5f55edf90089")),
                new Shortcut("Delete", I18n.text("ui.0a32fc261b66"))));
        return java.util.Collections.unmodifiableMap(groups);
    }

    ShortcutListPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        Label title = new Label(I18n.text("ui.627e2a1aede0"));
        title.getStyleClass().add("tool-title");
        Label note = new Label(I18n.text("ui.0e1b28dfddd0"));
        note.setWrapText(true);
        TableView<List<String>> table = new TableView<>();
        table.setId("shortcut-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        String[] headers = {I18n.text("ui.7c6cbc5826d4"), I18n.text("ui.b367a7b98923"), I18n.text("ui.1dfbdde6410a")};
        for (int index = 0; index < headers.length; index++) {
            int column = index;
            TableColumn<List<String>, String> col = new TableColumn<>(headers[index]);
            col.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().get(column)));
            col.setCellFactory(ignored -> new TableCell<>() {
                private final Label label = new Label();
                {
                    label.setWrapText(true);
                    label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    label.prefWidthProperty().bind(col.widthProperty().subtract(12));
                    label.maxWidthProperty().bind(label.prefWidthProperty());
                }
                @Override protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty);
                    setText(null);
                    label.setText(empty ? null : text);
                    setGraphic(empty ? null : label);
                }
            });
            col.setSortable(false);
            col.setPrefWidth(index == 2 ? 360 : 190);
            table.getColumns().add(col);
        }
        GROUPS.forEach((group, shortcuts) -> shortcuts.forEach(shortcut ->
                table.getItems().add(List.of(group, shortcut.keys(), shortcut.action()))));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(title, note, table);
    }
}
