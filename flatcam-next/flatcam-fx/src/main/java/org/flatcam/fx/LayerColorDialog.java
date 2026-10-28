package org.flatcam.fx;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
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
        return colorDialog(currentFill).showAndWait();
    }

    static Dialog<Color> colorDialog(Color currentFill) {
        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle(I18n.text("ui.d109ed9d1f5e"));
        FluidTooltips.install(dialog, "Layer Color");

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        ColorPicker fillPicker = new ColorPicker(currentFill);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label(I18n.text("ui.16479976a8e7")), fillPicker);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == okType ? fillPicker.getValue() : null);

        PanelTooltips.install(grid, "Layer Color");
        actions(dialog, okType, I18n.text("ui.0d65c4ffc7fe"), I18n.text("ui.03c8d9930388"));
        return dialog;
    }

    static Optional<Double> showOpacity(double currentOpacity) {
        return opacityDialog(currentOpacity).showAndWait();
    }

    static Dialog<Double> opacityDialog(double currentOpacity) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle(I18n.text("ui.212b6b7b0d29"));
        FluidTooltips.install(dialog, "Layer Color");

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Slider slider = new Slider(0, 100, currentOpacity * 100);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(25);
        slider.setBlockIncrement(5);
        Label value = new Label(Math.round(slider.getValue()) + "%");
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
                value.setText(Math.round(newValue.doubleValue()) + "%"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label(I18n.text("ui.b560c17969f7")), slider, value);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == okType ? slider.getValue() / 100.0 : null);
        PanelTooltips.install(grid, "Layer Color");
        actions(dialog, okType, I18n.text("ui.e779e380b62f"), I18n.text("ui.fc8266041de5"));
        return dialog;
    }

    private static void actions(Dialog<?> dialog, ButtonType confirm, String title, String text) {
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(confirm), title, text);
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(ButtonType.CANCEL), I18n.text("ui.ce6c93b2e203"),
                I18n.text("ui.29020b696007"));
    }
}
