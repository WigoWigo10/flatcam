package org.flatcam.fx;

import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.flatcam.cam.gcode.IsolationGCodeParameters;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;

/**
 * Parameters for isolation routing: both the geometry-generation side
 * (tool diameter, passes, overlap, which rings to keep - IsolationParameters)
 * and the G-code side (safe Z, cut depth, feed, spindle - IsolationGCodeParameters),
 * gathered in one form since generating isolation without exporting it isn't
 * very useful on its own yet (no in-app toolpath editor exists).
 */
final class IsolationDialog {

    record Result(IsolationParameters geometryParams, IsolationGCodeParameters gcodeParams) {
    }

    private IsolationDialog() {
    }

    static Optional<Result> show(String units) {
        boolean metric = "MM".equals(units);
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("Gerar Isolamento");
        dialog.setHeaderText("Parametros (unidades do arquivo: " + units + ")");

        ButtonType generateButtonType = new ButtonType("Gerar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(generateButtonType, ButtonType.CANCEL);

        TextField toolDiaField = new TextField(metric ? "0.2" : "0.008");
        TextField passesField = new TextField("1");
        TextField overlapField = new TextField("15");
        ComboBox<IsolationType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(IsolationType.values());
        typeCombo.setValue(IsolationType.BOTH);

        TextField safeZField = new TextField(metric ? "3.0" : "0.1");
        TextField depthField = new TextField(metric ? "0.1" : "0.004");
        TextField feedField = new TextField(metric ? "300" : "12");
        TextField spindleField = new TextField("10000");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Diametro da ferramenta:"), toolDiaField);
        grid.addRow(1, new Label("Numero de passes:"), passesField);
        grid.addRow(2, new Label("Sobreposicao entre passes (%):"), overlapField);
        grid.addRow(3, new Label("Aneis a manter:"), typeCombo);
        grid.addRow(4, new Label("Altura de seguranca (Z):"), safeZField);
        grid.addRow(5, new Label("Profundidade de corte:"), depthField);
        grid.addRow(6, new Label("Avanco (feed rate, unid./min):"), feedField);
        grid.addRow(7, new Label("Spindle (RPM, 0 = nao controlar):"), spindleField);
        grid.add(errorLabel, 0, 8, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Node generateButton = dialog.getDialogPane().lookupButton(generateButtonType);
        generateButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                parseResult(toolDiaField, passesField, overlapField, typeCombo, safeZField, depthField, feedField, spindleField);
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
                return parseResult(toolDiaField, passesField, overlapField, typeCombo, safeZField, depthField, feedField, spindleField);
            } catch (RuntimeException e) {
                return null;
            }
        });

        return dialog.showAndWait();
    }

    private static Result parseResult(
            TextField toolDiaField, TextField passesField, TextField overlapField, ComboBox<IsolationType> typeCombo,
            TextField safeZField, TextField depthField, TextField feedField, TextField spindleField) {
        double toolDia = parseDouble(toolDiaField.getText(), "Diametro da ferramenta");
        int passes = (int) parseDouble(passesField.getText(), "Numero de passes");
        double overlapPercent = parseDouble(overlapField.getText(), "Sobreposicao");
        IsolationType type = typeCombo.getValue();

        IsolationParameters geometryParams = new IsolationParameters(toolDia, passes, overlapPercent / 100.0, type);

        double safeZ = parseDouble(safeZField.getText(), "Altura de seguranca");
        double depth = parseDouble(depthField.getText(), "Profundidade de corte");
        double feed = parseDouble(feedField.getText(), "Avanco");
        int spindle = (int) parseDouble(spindleField.getText(), "Spindle");
        IsolationGCodeParameters gcodeParams = new IsolationGCodeParameters(safeZ, depth, feed, spindle);

        return new Result(geometryParams, gcodeParams);
    }

    private static double parseDouble(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + ": numero invalido");
        }
    }
}
