package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gcode.GeometryGCodeParameters;

/** Parameters for the Python-equivalent Geometry -> CNCJob step. */
final class GeometryCncToolPanel {

    record Result(double toolDiameter, GeometryGCodeParameters parameters) {
    }

    private GeometryCncToolPanel() {
    }

    static Node build(String units, Double suggestedToolDiameter,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(units);
        double defaultTool = suggestedToolDiameter != null
                ? suggestedToolDiameter : (metric ? 0.8 : 0.031);
        TextField toolDiaField = new TextField(format(defaultTool));
        TextField safeZField = new TextField(metric ? "3.0" : "0.1");
        TextField cutDepthField = new TextField(metric ? "0.1" : "0.004");
        CheckBox multiDepthCb = new CheckBox("Multi-Depth");
        TextField depthPerPassField = new TextField(metric ? "0.05" : "0.002");
        depthPerPassField.disableProperty().bind(multiDepthCb.selectedProperty().not());
        TextField feedField = new TextField(metric ? "300" : "12");
        TextField spindleField = new TextField("10000");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Tool Dia:"), toolDiaField);
        grid.addRow(1, new Label("Travel Z:"), safeZField);
        grid.addRow(2, new Label("Cut Z:"), cutDepthField);
        grid.addRow(3, multiDepthCb, depthPerPassField);
        grid.addRow(4, new Label("Feed rate:"), feedField);
        grid.addRow(5, new Label("Spindle RPM:"), spindleField);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        Button generateButton = new Button("Gerar CNC Job...");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                double toolDia = parse(toolDiaField, "Tool Dia");
                double safeZ = parse(safeZField, "Travel Z");
                double cutDepth = parse(cutDepthField, "Cut Z");
                boolean multiDepth = multiDepthCb.isSelected();
                double depthPerPass = multiDepth ? parse(depthPerPassField, "Depth per pass") : 1;
                double feed = parse(feedField, "Feed rate");
                int spindle = (int) parse(spindleField, "Spindle RPM");
                GeometryGCodeParameters params = new GeometryGCodeParameters(
                        safeZ, cutDepth, multiDepth, depthPerPass, feed, spindle);
                errorLabel.setText("");
                onGenerate.accept(new Result(toolDia, params));
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        VBox box = new VBox(10, new Label("Geometry -> CNC Job (" + units + ")"), grid,
                errorLabel, generateButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static double parse(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": numero invalido");
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
