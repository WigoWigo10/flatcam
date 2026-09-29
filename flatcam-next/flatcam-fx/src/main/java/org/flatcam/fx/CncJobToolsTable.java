package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.flatcam.cam.gcode.GCodeToolpathParser;

/**
 * Tools Table of a drill CNC Job - ObjectUI.py's CNCObjectUI cnc_tools_table
 * for Excellon jobs (#, Dia, Drills, Slots, Cut Z, Plot). Unlike Python, the
 * Plot checkbox of each row really hides that tool's toolpath and its drill
 * order labels.
 */
final class CncJobToolsTable {

    private CncJobToolsTable() { }

    static Node build(GCodeToolpathParser.ToolpathStats stats, Set<Integer> hiddenTools, Runnable onToggle) {
        List<GCodeToolpathParser.ToolUsage> rows = stats.tools();

        TableColumn<GCodeToolpathParser.ToolUsage, String> numberColumn =
                column("#", tool -> Integer.toString(tool.toolId()));
        numberColumn.setMinWidth(34);
        numberColumn.setMaxWidth(34);
        TableColumn<GCodeToolpathParser.ToolUsage, String> diameterColumn = column("Dia", tool ->
                tool.diameter() == null ? "" : String.format(Locale.ROOT, "%.4f", tool.diameter()));
        TableColumn<GCodeToolpathParser.ToolUsage, String> drillsColumn =
                column("Drills", tool -> tool.drills() == 0 ? "" : Integer.toString(tool.drills()));
        drillsColumn.setMinWidth(52);
        TableColumn<GCodeToolpathParser.ToolUsage, String> slotsColumn =
                column("Slots", tool -> tool.slots() == 0 ? "" : Integer.toString(tool.slots()));
        slotsColumn.setMinWidth(46);
        TableColumn<GCodeToolpathParser.ToolUsage, String> cutZColumn =
                column("Cut Z", tool -> String.format(Locale.ROOT, "%.4f", tool.deepestZ()));

        TableColumn<GCodeToolpathParser.ToolUsage, GCodeToolpathParser.ToolUsage> plotColumn = new TableColumn<>("P");
        plotColumn.setSortable(false);
        plotColumn.setMinWidth(30);
        plotColumn.setMaxWidth(30);
        plotColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        plotColumn.setCellFactory(ignored -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                setAlignment(Pos.CENTER);
                checkBox.setOnAction(e -> {
                    GCodeToolpathParser.ToolUsage tool = getItem();
                    if (tool == null) return;
                    if (checkBox.isSelected()) {
                        hiddenTools.remove(tool.toolId());
                    } else {
                        hiddenTools.add(tool.toolId());
                    }
                    onToggle.run();
                });
            }

            @Override protected void updateItem(GCodeToolpathParser.ToolUsage tool, boolean empty) {
                super.updateItem(tool, empty);
                if (empty || tool == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(!hiddenTools.contains(tool.toolId()));
                    setGraphic(checkBox);
                }
            }
        });

        TableView<GCodeToolpathParser.ToolUsage> table = new TableView<>();
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(List.of(numberColumn, diameterColumn, drillsColumn, slotsColumn, cutZColumn, plotColumn));
        table.getItems().setAll(rows);
        table.setFixedCellSize(27);
        table.setPrefHeight(30 + rows.size() * 27 + 2);
        table.setMinHeight(table.getPrefHeight());
        table.setMaxHeight(table.getPrefHeight());
        table.getSelectionModel().clearSelection();
        table.setFocusTraversable(false);
        return table;
    }

    /** Python's CNCJob "Estimated time": whole minutes (rounded up) above one minute, seconds below. */
    static String formatDuration(double minutes) {
        if (Double.isNaN(minutes)) return "—";
        if (minutes > 1) return String.format(Locale.ROOT, "%d min", (long) Math.ceil(minutes));
        return String.format(Locale.ROOT, "%.1f s", minutes * 60);
    }

    private static TableColumn<GCodeToolpathParser.ToolUsage, String> column(
            String title, Function<GCodeToolpathParser.ToolUsage, String> value) {
        TableColumn<GCodeToolpathParser.ToolUsage, String> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        return column;
    }
}
