package org.flatcam.fx;

import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.flatcam.cam.gcode.DrillGCodeParameters;

/**
 * Small parameter form for drilling G-code generation (safe Z, drill depth,
 * feed rate, spindle speed, tool-change pause) - the same kind of "ask for
 * generation parameters before producing output" step the legacy app has
 * for CNCJob generation (UI_INVENTORY.md section 2, CNCObjectUI's "Common
 * Parameters"/"Probe GCode Generation"), just far smaller.
 */
final class DrillGCodeDialog {

    private DrillGCodeDialog() {
    }

    static Optional<DrillGCodeParameters> show(String units, int toolCount) {
        boolean metric = "MM".equals(units);
        Dialog<DrillGCodeParameters> dialog = new Dialog<>();
        dialog.setTitle("Gerar G-code de furacao");
        dialog.setHeaderText("Parametros (unidades do arquivo: " + units + ")");

        ButtonType generateButtonType = new ButtonType("Gerar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(generateButtonType, ButtonType.CANCEL);

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
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Altura de seguranca (Z):"), safeZField);
        grid.addRow(1, new Label("Profundidade de furacao:"), depthField);
        grid.addRow(2, new Label("Avanco (feed rate, unid./min):"), feedField);
        grid.addRow(3, new Label("Spindle (RPM, 0 = nao controlar):"), spindleField);
        grid.add(pauseCheck, 0, 4, 2, 1);
        grid.add(errorLabel, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Node generateButton = dialog.getDialogPane().lookupButton(generateButtonType);
        generateButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                parseParameters(safeZField, depthField, feedField, spindleField, pauseCheck);
            } catch (RuntimeException e) {
                errorLabel.setText(e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != generateButtonType) {
                return null;
            }
            try {
                // Re-parses rather than caching the filter's result: simplest way to keep one source of
                // truth, and the event filter above already guarantees these values parse cleanly here.
                return parseParameters(safeZField, depthField, feedField, spindleField, pauseCheck);
            } catch (RuntimeException e) {
                return null;
            }
        });

        return dialog.showAndWait();
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
