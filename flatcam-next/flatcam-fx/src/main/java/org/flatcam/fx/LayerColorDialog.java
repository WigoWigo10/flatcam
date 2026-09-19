package org.flatcam.fx;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;

/**
 * Fill color for one object's plot - mirrors the legacy app's per-object
 * "Set Color" (UI_INVENTORY.md section 1: fill_color/outline_color/
 * alpha_level on every Gerber/Excellon/Geometry object). The legacy dialog
 * only exposes one color control per object (not a separate outline color),
 * so this mirrors that: the outline is derived from the chosen fill via
 * Color.darker(), same as the built-in GERBER_FILL/GERBER_STROKE and
 * DRILL_FILL/DRILL_STROKE default pairs. JavaFX's ColorPicker already has a
 * preset swatch grid plus a custom picker with an opacity slider built in,
 * covering the legacy menu's preset-colors-plus-custom-plus-opacity in one
 * control.
 */
final class LayerColorDialog {

    private LayerColorDialog() {
    }

    static Optional<Color> show(Color currentFill) {
        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle("Definir Cor");

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        ColorPicker fillPicker = new ColorPicker(currentFill);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Cor:"), fillPicker);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == okType ? fillPicker.getValue() : null);

        return dialog.showAndWait();
    }
}
