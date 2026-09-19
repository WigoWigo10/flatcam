package org.flatcam.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Ports appTools/ToolCalculators.py's CalcUI/ToolCalculator as-is: three
 * independent calculators (Units, V-Shape Tool, ElectroPlating), none of
 * them touching a selected object - unlike Isolation/Drilling Tool, this
 * has nothing to generate or save, so there's no "Fechar" button; the user
 * just navigates away via the tab strip like any other Tool tab content.
 */
final class CalculatorsPanel {

    private CalculatorsPanel() {
    }

    static Node build() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(12));

        Label title = new Label("Calculators");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        box.getChildren().addAll(title, buildUnitsSection(), buildVShapeSection(), buildElectroplatingSection());
        return box;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    // --- Units Calculator: appTools/ToolCalculators.py's on_calculate_inch_units()/on_calculate_mm_units() ---
    private static Node buildUnitsSection() {
        TextField mmField = new TextField("0");
        TextField inchField = new TextField("0");

        mmField.setOnAction(e -> inchField.setText(format(parse(mmField.getText()) / 25.4)));
        mmField.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                inchField.setText(format(parse(mmField.getText()) / 25.4));
            }
        });
        inchField.setOnAction(e -> mmField.setText(format(parse(inchField.getText()) * 25.4)));
        inchField.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                mmField.setText(format(parse(inchField.getText()) * 25.4));
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("MM"), new Label("INCH"));
        grid.addRow(1, mmField, inchField);

        return new VBox(6, sectionTitle("Units Calculator"), grid);
    }

    // --- V-Shape Tool Calculator: appTools/ToolCalculators.py's on_calculate_tool_dia() ---
    private static Node buildVShapeSection() {
        TextField tipDiaField = new TextField("0.2");
        TextField tipAngleField = new TextField("30");
        TextField cutDepthField = new TextField("0.05");
        TextField toolDiaField = new TextField("0.0000");
        toolDiaField.setEditable(false);

        Runnable calculate = () -> {
            double tipDia = parse(tipDiaField.getText());
            double halfTipAngle = parse(tipAngleField.getText()) / 2.0;
            double cutDepth = Math.abs(parse(cutDepthField.getText()));
            double toolDia = tipDia + 2 * cutDepth * Math.tan(Math.toRadians(halfTipAngle));
            toolDiaField.setText(format(toolDia));
        };

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Tip Diameter:"), tipDiaField);
        grid.addRow(1, new Label("Tip Angle:"), tipAngleField);
        grid.addRow(2, new Label("Cut Z:"), cutDepthField);
        grid.addRow(3, new Label("Tool Diameter:"), toolDiaField);

        Button calculateButton = new Button("Calculate");
        calculateButton.setMaxWidth(Double.MAX_VALUE);
        calculateButton.setOnAction(e -> calculate.run());

        return new VBox(6, sectionTitle("V-Shape Tool Calculator"), grid, calculateButton);
    }

    // --- ElectroPlating Calculator: appTools/ToolCalculators.py's on_calculate_eplate() ---
    private static Node buildElectroplatingSection() {
        RadioButton dimensionsRadio = new RadioButton("Dimensions");
        RadioButton areaRadio = new RadioButton("Area");
        ToggleGroup areaModeGroup = new ToggleGroup();
        dimensionsRadio.setToggleGroup(areaModeGroup);
        areaRadio.setToggleGroup(areaModeGroup);
        dimensionsRadio.setSelected(true);

        TextField lengthField = new TextField("10.0");
        TextField widthField = new TextField("10.0");
        TextField areaField = new TextField("100.0");
        TextField densityField = new TextField("13.0");
        TextField growthField = new TextField("10.0");
        TextField currentField = new TextField("0.00");
        currentField.setEditable(false);
        TextField timeField = new TextField("0.0");
        timeField.setEditable(false);

        HBox lengthRow = new HBox(6, new Label("Board Length:"), lengthField, new Label("cm"));
        HBox widthRow = new HBox(6, new Label("Board Width:"), widthField, new Label("cm"));
        HBox areaRow = new HBox(6, new Label("Area:"), areaField, new Label("cm²"));
        HBox.setHgrow(lengthField, Priority.ALWAYS);
        HBox.setHgrow(widthField, Priority.ALWAYS);
        HBox.setHgrow(areaField, Priority.ALWAYS);
        lengthRow.setAlignment(Pos.CENTER_LEFT);
        widthRow.setAlignment(Pos.CENTER_LEFT);
        areaRow.setAlignment(Pos.CENTER_LEFT);
        areaRow.setVisible(false);
        areaRow.setManaged(false);

        areaModeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            boolean byDimensions = newToggle == dimensionsRadio;
            lengthRow.setVisible(byDimensions);
            lengthRow.setManaged(byDimensions);
            widthRow.setVisible(byDimensions);
            widthRow.setManaged(byDimensions);
            areaRow.setVisible(!byDimensions);
            areaRow.setManaged(!byDimensions);
        });

        Runnable calculate = () -> {
            boolean byDimensions = areaModeGroup.getSelectedToggle() == dimensionsRadio;
            double density = parse(densityField.getText());
            double growth = parse(growthField.getText());
            double current = byDimensions
                    ? parse(lengthField.getText()) * parse(widthField.getText()) * density * 0.0021527820833419
                    : parse(areaField.getText()) * density * 0.0021527820833419;
            double time = density == 0 ? 0 : growth * 2.142857142857143 * (20.0 / density);
            currentField.setText(String.format(java.util.Locale.ROOT, "%.2f", current));
            timeField.setText(String.format(java.util.Locale.ROOT, "%.1f", time));
        };

        Button calculateButton = new Button("Calculate");
        calculateButton.setMaxWidth(Double.MAX_VALUE);
        calculateButton.setOnAction(e -> calculate.run());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Current Density (ASF):"), densityField);
        grid.addRow(1, new Label("Copper Growth (µm):"), growthField);
        grid.addRow(2, new Label("Current (A):"), currentField);
        grid.addRow(3, new Label("Time (min):"), timeField);

        // lengthRow/widthRow/areaRow toggle visibility instead of occupying fixed grid
        // rows - simpler to reason about than juggling GridPane row indices for a
        // variable set of visible rows.
        VBox areaInputs = new VBox(6, new HBox(10, dimensionsRadio, areaRadio), lengthRow, widthRow, areaRow);

        return new VBox(6, sectionTitle("ElectroPlating Calculator"),
                new Label("Area Calculation:"), areaInputs, grid, calculateButton);
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
