package org.flatcam.fx;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Shared # / Diameter / TT layout, matching Python's stretching diameter column. */
final class CompactToolsTable {
    private CompactToolsTable() { }

    static void configure(TableView<?> table, TableColumn<?, ?> number,
                          TableColumn<?, ?> diameter, TableColumn<?, ?> profile) {
        table.getStyleClass().add("compact-tools-table");
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(28);
        number.setMinWidth(32);
        number.setPrefWidth(32);
        number.setMaxWidth(32);
        diameter.setMinWidth(64);
        diameter.setPrefWidth(120);
        // Diameter absorbs spare width; TT must not stretch its two-letter editor.
        profile.setMinWidth(72);
        profile.setPrefWidth(84);
        profile.setMaxWidth(96);
    }
}
