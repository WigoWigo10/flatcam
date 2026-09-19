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
import org.flatcam.cam.gcode.DrillGCodeParameters;

/**
 * Parameters for drilling G-code generation, as an embeddable panel rather
 * than a modal dialog - appTools/ToolDrilling.py's run() switches the LEFT
 * sidebar's own "Tool" tab (app.ui.tool_tab) to this tool's UI instead of
 * popping a separate window, same as ToolIsolation.py (see
 * IsolationToolPanel's doc). MainWindow wires this the same way via its own
 * "Ferramenta" tab.
 */
final class DrillGCodeToolPanel {

    private DrillGCodeToolPanel() {
    }

    /**
     * @param onGenerate called with the parsed parameters when "Gerar" is clicked and they're valid.
     * @param onClose    called when "Fechar" is clicked - MainWindow uses it to restore the tool tab's placeholder.
     */
    static Node build(String units, int toolCount, Consumer<DrillGCodeParameters> onGenerate, Runnable onClose) {
        boolean metric = "MM".equals(units);

        TextField safeZField = new TextField(metric ? "3.0" : "0.1");
        TextField depthField = new TextField(metric ? "1.7" : "0.07");
        TextField feedField = new TextField(metric ? "300" : "12");
        TextField spindleField = new TextField("10000");
        CheckBox pauseCheck = new CheckBox("Pausar para troca de ferramenta (M0)");
        pauseCheck.setSelected(toolCount > 1);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Altura de seguranca (Z):"), safeZField);
        grid.addRow(1, new Label("Profundidade de furacao:"), depthField);
        grid.addRow(2, new Label("Avanco (feed rate, unid./min):"), feedField);
        grid.addRow(3, new Label("Spindle (RPM, 0 = nao controlar):"), spindleField);
        grid.add(pauseCheck, 0, 4, 2, 1);

        Button generateButton = new Button("Gerar");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                DrillGCodeParameters params = parseParameters(safeZField, depthField, feedField, spindleField, pauseCheck);
                errorLabel.setText("");
                onGenerate.accept(params);
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        closeButton.setOnAction(e -> onClose.run());

        VBox box = new VBox(10,
                new Label("Parametros (unidades do arquivo: " + units + ")"),
                grid, errorLabel, generateButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static DrillGCodeParameters parseParameters(
            TextField safeZField, TextField depthField, TextField feedField, TextField spindleField, CheckBox pauseCheck) {
        double safeZ = parseDouble(safeZField.getText(), "Altura de seguranca");
        double depth = parseDouble(depthField.getText(), "Profundidade de furacao");
        double feed = parseDouble(feedField.getText(), "Avanco");
        int spindle = parseInt(spindleField.getText(), "Spindle");
        return new DrillGCodeParameters(safeZ, depth, feed, spindle, pauseCheck.isSelected());
    }

    private static double parseDouble(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + ": numero invalido");
        }
    }

    private static int parseInt(String text, String fieldName) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + ": numero invalido");
        }
    }
}
