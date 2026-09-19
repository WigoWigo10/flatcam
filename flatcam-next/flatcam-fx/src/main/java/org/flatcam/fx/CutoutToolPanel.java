package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.cutout.CutoutKind;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.CutoutShape;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.gcode.CutoutGCodeParameters;

/**
 * Parameters for the Cutout Tool, as an embeddable panel rather than a modal
 * dialog - appTools/ToolCutOut.py's run() switches the LEFT sidebar's own
 * "Tool" tab the same way ToolIsolation.py/ToolDrilling.py do (see
 * IsolationToolPanel's doc). Two separate "Gerar" buttons (Free-form vs
 * Rectangular) mirror Python's own two separate generation buttons sharing
 * this one form.
 *
 * <p>Only the "Bridge" gap type is offered ("Thin" and "M-Bites" are
 * deferred - see CutoutGenerator's class doc), and this always goes
 * straight to G-code instead of Python's intermediate Geometry-object step
 * (same deliberate simplification, same doc).
 */
final class CutoutToolPanel {

    record Result(CutoutParameters cutoutParams, CutoutGCodeParameters gcodeParams, CutoutShape shape) {
    }

    private CutoutToolPanel() {
    }

    /**
     * @param onGenerate called with the parsed parameters when either "Gerar" button is clicked and they're valid.
     * @param onClose    called when "Fechar" is clicked - MainWindow uses it to restore the tool tab's placeholder.
     */
    static Node build(String units, Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equals(units);

        RadioButton singleRadio = new RadioButton("Single");
        RadioButton panelRadio = new RadioButton("Panel");
        ToggleGroup kindGroup = new ToggleGroup();
        singleRadio.setToggleGroup(kindGroup);
        panelRadio.setToggleGroup(kindGroup);
        singleRadio.setSelected(true);

        CheckBox convexShapeCb = new CheckBox("Convex Shape");

        TextField toolDiaField = new TextField(metric ? "2.4" : "0.094");
        TextField cutZField = new TextField(metric ? "1.8" : "0.07");

        CheckBox multiDepthCb = new CheckBox("Multi-Depth");
        multiDepthCb.setSelected(true);
        TextField depthPerPassField = new TextField(metric ? "0.6" : "0.024");
        depthPerPassField.disableProperty().bind(multiDepthCb.selectedProperty().not());

        TextField marginField = new TextField(metric ? "0.1" : "0.004");
        TextField gapSizeField = new TextField(metric ? "4" : "0.16");

        ComboBox<GapPattern> gapPatternCombo = new ComboBox<>();
        gapPatternCombo.getItems().addAll(GapPattern.values());
        gapPatternCombo.setValue(GapPattern.FOUR);

        TextField safeZField = new TextField(metric ? "3.0" : "0.1");
        TextField feedField = new TextField(metric ? "300" : "12");
        TextField spindleField = new TextField("10000");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Kind:"), new HBox(10, singleRadio, panelRadio));
        grid.addRow(1, new Label("Convex Shape:"), convexShapeCb);
        grid.addRow(2, new Label("Tool Dia:"), toolDiaField);
        grid.addRow(3, new Label("Cut Z:"), cutZField);
        grid.addRow(4, multiDepthCb, depthPerPassField);
        grid.addRow(5, new Label("Margin:"), marginField);
        grid.addRow(6, new Label("Gap size:"), gapSizeField);
        grid.addRow(7, new Label("Gaps:"), gapPatternCombo);
        grid.addRow(8, new Label("Altura de seguranca (Z):"), safeZField);
        grid.addRow(9, new Label("Avanco (feed rate, unid./min):"), feedField);
        grid.addRow(10, new Label("Spindle (RPM, 0 = nao controlar):"), spindleField);

        Button freeformButton = new Button("Gerar (Free-form)");
        freeformButton.setMaxWidth(Double.MAX_VALUE);
        Button rectangularButton = new Button("Gerar (Rectangular)");
        rectangularButton.setMaxWidth(Double.MAX_VALUE);
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);

        freeformButton.setOnAction(e -> tryGenerate(CutoutShape.FREEFORM, singleRadio, convexShapeCb, toolDiaField,
                cutZField, multiDepthCb, depthPerPassField, marginField, gapSizeField, gapPatternCombo,
                safeZField, feedField, spindleField, errorLabel, onGenerate));
        rectangularButton.setOnAction(e -> tryGenerate(CutoutShape.RECTANGULAR, singleRadio, convexShapeCb, toolDiaField,
                cutZField, multiDepthCb, depthPerPassField, marginField, gapSizeField, gapPatternCombo,
                safeZField, feedField, spindleField, errorLabel, onGenerate));
        closeButton.setOnAction(e -> onClose.run());

        VBox box = new VBox(10,
                new Label("Parametros (unidades do arquivo: " + units + ")"),
                grid, errorLabel, freeformButton, rectangularButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static void tryGenerate(CutoutShape shape, RadioButton singleRadio, CheckBox convexShapeCb,
            TextField toolDiaField, TextField cutZField, CheckBox multiDepthCb, TextField depthPerPassField,
            TextField marginField, TextField gapSizeField, ComboBox<GapPattern> gapPatternCombo,
            TextField safeZField, TextField feedField, TextField spindleField, Label errorLabel,
            Consumer<Result> onGenerate) {
        try {
            double toolDia = parseDouble(toolDiaField.getText(), "Tool Dia");
            double cutZ = parseDouble(cutZField.getText(), "Cut Z");
            double margin = parseDouble(marginField.getText(), "Margin");
            double gapSize = parseDouble(gapSizeField.getText(), "Gap size");
            CutoutKind kind = singleRadio.isSelected() ? CutoutKind.SINGLE : CutoutKind.PANEL;
            CutoutParameters cutoutParams = new CutoutParameters(toolDia, margin, convexShapeCb.isSelected(),
                    kind, shape, gapSize, gapPatternCombo.getValue());

            double safeZ = parseDouble(safeZField.getText(), "Altura de seguranca");
            double feed = parseDouble(feedField.getText(), "Avanco");
            int spindle = (int) parseDouble(spindleField.getText(), "Spindle");
            boolean multiDepth = multiDepthCb.isSelected();
            double depthPerPass = multiDepth ? parseDouble(depthPerPassField.getText(), "Depth per pass") : 1.0;
            CutoutGCodeParameters gcodeParams = new CutoutGCodeParameters(safeZ, cutZ, multiDepth, depthPerPass, feed, spindle);

            errorLabel.setText("");
            onGenerate.accept(new Result(cutoutParams, gcodeParams, shape));
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static double parseDouble(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + ": numero invalido");
        }
    }
}
