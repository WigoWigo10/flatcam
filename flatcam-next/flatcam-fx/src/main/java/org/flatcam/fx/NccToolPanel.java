package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccParameters;

/** One-tool NCC form; the result is a Geometry object, matching Python's workflow. */
final class NccToolPanel {

    private NccToolPanel() {
    }

    static Node build(String units, Consumer<NccParameters> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(units);
        TextField toolDiaField = new TextField(metric ? "0.5" : "0.020");
        TextField overlapField = new TextField("40");
        TextField marginField = new TextField(metric ? "1.0" : "0.040");
        ComboBox<NccMethod> methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll(NccMethod.values());
        methodCombo.setValue(NccMethod.STANDARD);
        CheckBox connectCb = new CheckBox("Connect");
        connectCb.setSelected(true);
        CheckBox contourCb = new CheckBox("Contour");
        contourCb.setSelected(true);
        CheckBox offsetCb = new CheckBox("Copper offset");
        TextField offsetField = new TextField("0.0");
        offsetField.disableProperty().bind(offsetCb.selectedProperty().not());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Tool Dia:"), toolDiaField);
        grid.addRow(1, new Label("Overlap (%):"), overlapField);
        grid.addRow(2, new Label("Margin:"), marginField);
        grid.addRow(3, new Label("Method:"), methodCombo);
        grid.addRow(4, connectCb, contourCb);
        grid.addRow(5, offsetCb, offsetField);

        Label note = new Label("Esta primeira etapa usa uma ferramenta. O resultado sera criado em Geometry.");
        note.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        Button generateButton = new Button("Gerar Geometry NCC");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                double toolDia = parse(toolDiaField, "Tool Dia");
                double overlap = parse(overlapField, "Overlap") / 100.0;
                double margin = parse(marginField, "Margin");
                double offset = offsetCb.isSelected() ? parse(offsetField, "Copper offset") : 0;
                NccParameters params = new NccParameters(toolDia, overlap, margin,
                        methodCombo.getValue(), connectCb.isSelected(), contourCb.isSelected(), offset);
                errorLabel.setText("");
                onGenerate.accept(params);
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        VBox box = new VBox(10, new Label("NCC Tool (" + units + ")"), grid, note,
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
}
