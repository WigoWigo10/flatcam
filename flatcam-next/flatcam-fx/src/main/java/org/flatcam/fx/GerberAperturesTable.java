package org.flatcam.fx;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;

/**
 * The Gerber object's apertures table - ports appGUI/ObjectUI.py's
 * GerberObjectUI (columns #/Code/Type/Size/Dim/M, an FCTable) plus
 * appObjects/FlatCAMGerber.py's on_mark_all_click()/on_mark_cb_click_table().
 * "Mark" is purely a visual highlight (confirmed: no Python tool reads mark
 * state) so {@code onMarkToggle} only needs to add/remove a highlight
 * overlay - it has no effect on isolation or any other generation step.
 */
final class GerberAperturesTable {

    /** One row: the aperture's own data, plus a live "marked" flag the M column's checkbox binds to. */
    public static final class ApertureRow {
        private final String code;
        private final String type;
        private final double size;
        private final String dim;
        private final SimpleBooleanProperty marked = new SimpleBooleanProperty(false);

        ApertureRow(String code, String type, double size, String dim) {
            this.code = code;
            this.type = type;
            this.size = size;
            this.dim = dim;
        }

        public String getCode() {
            return code;
        }

        public String getType() {
            return type;
        }

        public double getSize() {
            return size;
        }

        public String getDim() {
            return dim;
        }

        public SimpleBooleanProperty markedProperty() {
            return marked;
        }
    }

    private GerberAperturesTable() {
    }

    /** @param onMarkToggle called with (apertureCode, marked) whenever a row's checkbox (or "Mark All") changes. */
    static Node build(GerberImage image, BiConsumer<String, Boolean> onMarkToggle) {
        List<ApertureRow> rows = image.apertures().entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> parseCodeSafe(entry.getKey())))
                .map(entry -> toRow(entry.getKey(), entry.getValue()))
                .toList();
        for (ApertureRow row : rows) {
            row.markedProperty().addListener((obs, wasMarked, isMarked) -> onMarkToggle.accept(row.getCode(), isMarked));
        }

        TableColumn<ApertureRow, Number> indexColumn = new TableColumn<>("#");
        indexColumn.setCellValueFactory(cb -> new ReadOnlyObjectWrapper<>(rows.indexOf(cb.getValue()) + 1));
        TableColumn<ApertureRow, String> codeColumn = new TableColumn<>("Code");
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("code"));
        TableColumn<ApertureRow, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        TableColumn<ApertureRow, Number> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        TableColumn<ApertureRow, String> dimColumn = new TableColumn<>("Dim");
        dimColumn.setCellValueFactory(new PropertyValueFactory<>("dim"));
        TableColumn<ApertureRow, Boolean> markColumn = new TableColumn<>("M");
        markColumn.setCellValueFactory(cb -> cb.getValue().markedProperty());
        markColumn.setCellFactory(CheckBoxTableCell.forTableColumn(markColumn));

        // Keep the short data columns compact; Dim absorbs the available width
        // instead of leaving an unlabelled filler column beside M.
        indexColumn.setMinWidth(30);
        indexColumn.setPrefWidth(30);
        indexColumn.setMaxWidth(30);
        codeColumn.setMinWidth(45);
        codeColumn.setPrefWidth(50);
        codeColumn.setMaxWidth(65);
        typeColumn.setMinWidth(45);
        typeColumn.setPrefWidth(65);
        typeColumn.setMaxWidth(85);
        sizeColumn.setMinWidth(50);
        sizeColumn.setPrefWidth(60);
        sizeColumn.setMaxWidth(75);
        dimColumn.setMinWidth(75);
        dimColumn.setPrefWidth(140);
        markColumn.setMinWidth(32);
        markColumn.setPrefWidth(32);
        markColumn.setMaxWidth(32);

        TableView<ApertureRow> table = new TableView<>();
        table.setEditable(true);
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(List.of(indexColumn, codeColumn, typeColumn, sizeColumn, dimColumn, markColumn));
        table.getItems().setAll(rows);
        table.setPrefHeight(Math.min(200, 32 + rows.size() * 28));

        CheckBox markAllCb = new CheckBox("Mark All");
        markAllCb.setOnAction(e -> rows.forEach(row -> row.markedProperty().set(markAllCb.isSelected())));

        return new VBox(6, markAllCb, table);
    }

    private static ApertureRow toRow(String code, Aperture aperture) {
        String type = switch (aperture.kind) {
            case CIRCLE -> "C";
            case RECTANGLE -> "R";
            case OBROUND -> "O";
            case POLYGON -> "P";
            case MACRO -> aperture.macroName() != null ? aperture.macroName() : "AM";
        };
        double size = aperture.kind == ApertureKind.MACRO ? 0 : aperture.width;
        String dim = switch (aperture.kind) {
            case RECTANGLE, OBROUND -> String.format("%.4f x %.4f", aperture.width, aperture.height);
            case POLYGON -> String.format("%.4f x %d @ %.2f deg", aperture.width,
                    aperture.polygonVertices(), aperture.polygonRotation());
            default -> "";
        };
        return new ApertureRow(code, type, size, dim);
    }

    /** Aperture codes are usually numeric ("10", "11", ...) but this must not throw on a non-numeric one. */
    private static int parseCodeSafe(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
