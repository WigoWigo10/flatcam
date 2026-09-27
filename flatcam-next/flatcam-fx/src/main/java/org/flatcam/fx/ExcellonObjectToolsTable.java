package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.flatcam.cam.excellon.ExcellonImage;

/** Read-only Excellon object table, including the two total rows shown by FlatCAM Python. */
final class ExcellonObjectToolsTable {

    private record Row(String number, String diameter, String drills, String slots, boolean total) { }

    private ExcellonObjectToolsTable() { }

    static Node build(ExcellonImage image) {
        var drillCounts = image.drillCounts();
        var slotCounts = image.slotCounts();
        List<Row> rows = new ArrayList<>();
        image.toolDiameters().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int drills = drillCounts.getOrDefault(entry.getKey(), 0);
                    int slots = slotCounts.getOrDefault(entry.getKey(), 0);
                    rows.add(new Row(Integer.toString(entry.getKey()),
                            String.format(java.util.Locale.ROOT, "%.4f", entry.getValue()),
                            drills == 0 ? "" : Integer.toString(drills),
                            slots == 0 ? "" : Integer.toString(slots), false));
                });
        rows.add(new Row("", "Total Drills", Integer.toString(image.totalDrills()), "", true));
        rows.add(new Row("", "Total Slots", "", Integer.toString(image.totalSlots()), true));

        TableColumn<Row, String> numberColumn = column("#", Row::number);
        numberColumn.setMinWidth(34);
        numberColumn.setMaxWidth(34);
        TableColumn<Row, String> diameterColumn = column("Diameter", Row::diameter);
        TableColumn<Row, String> drillsColumn = column("Drills", Row::drills);
        drillsColumn.setMinWidth(58);
        TableColumn<Row, String> slotsColumn = column("Slots", Row::slots);
        slotsColumn.setMinWidth(52);
        TableView<Row> table = new TableView<>();
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(numberColumn, diameterColumn, drillsColumn, slotsColumn);
        table.getItems().setAll(rows);
        table.setFixedCellSize(27);
        table.setPrefHeight(30 + rows.size() * 27 + 2);
        table.setMaxHeight(table.getPrefHeight());
        table.getSelectionModel().clearSelection();
        table.setFocusTraversable(false);
        return table;
    }

    private static TableColumn<Row, String> column(String title,
                                                    java.util.function.Function<Row, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                boolean total = !empty && getTableRow() != null
                        && getTableRow().getItem() != null && getTableRow().getItem().total();
                if (total) {
                    if (!getStyleClass().contains("total-cell")) getStyleClass().add("total-cell");
                } else {
                    getStyleClass().remove("total-cell");
                }
            }
        });
        return column;
    }
}
