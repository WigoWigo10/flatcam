package org.flatcam.fx;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;

/**
 * Parameters for the Python-equivalent Geometry -> CNCJob step. When the
 * source Geometry already carries its own tool associations (a multigeo
 * object - e.g. an NCC result), those tools/diameters are fixed and shown
 * read-only: there is nothing to ask the user, since Python's own
 * mtool_gen_cncjob likewise just iterates the object's existing tools.
 * Otherwise (a plain single-purpose Geometry, e.g. a boundary/non-copper
 * outline) the user must supply one tool diameter, as before.
 */
final class GeometryCncToolPanel {

    record Result(List<ToolGeometry> tools, GeometryGCodeParameters parameters) {
    }

    private GeometryCncToolPanel() {
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> tools,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(units);
        boolean multiTool = !tools.isEmpty();

        TextField toolDiaField = new TextField(format(metric ? 0.8 : 0.031));
        TableView<ToolGeometry> toolTable = new TableView<>();
        if (multiTool) {
            TableColumn<ToolGeometry, Number> idColumn = new TableColumn<>("#");
            idColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(
                    toolTable.getItems().indexOf(cellData.getValue()) + 1));
            TableColumn<ToolGeometry, Number> diaColumn = new TableColumn<>("Diametro");
            diaColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().toolDiameter()));
            toolTable.getColumns().addAll(List.of(idColumn, diaColumn));
            toolTable.getItems().setAll(tools);
            toolTable.setPrefHeight(Math.min(160, 28 + tools.size() * 28));
        } else if (tools.size() == 1) {
            toolDiaField.setText(format(tools.get(0).toolDiameter()));
        }

        TextField safeZField = new TextField(metric ? "3.0" : "0.1");
        TextField cutDepthField = new TextField(metric ? "0.1" : "0.004");
        CheckBox multiDepthCb = new CheckBox("Multi-Depth");
        TextField depthPerPassField = new TextField(metric ? "0.05" : "0.002");
        depthPerPassField.disableProperty().bind(multiDepthCb.selectedProperty().not());
        TextField feedField = new TextField(metric ? "300" : "12");
        TextField spindleField = new TextField("10000");
        CheckBox pauseCheck = new CheckBox("Pausar para troca de ferramenta (M0)");
        pauseCheck.setSelected(tools.size() > 1);
        pauseCheck.setDisable(tools.size() <= 1);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        if (!multiTool) {
            grid.addRow(row++, new Label("Tool Dia:"), toolDiaField);
        }
        grid.addRow(row++, new Label("Travel Z:"), safeZField);
        grid.addRow(row++, new Label("Cut Z:"), cutDepthField);
        grid.addRow(row++, multiDepthCb, depthPerPassField);
        grid.addRow(row++, new Label("Feed rate:"), feedField);
        grid.addRow(row++, new Label("Spindle RPM:"), spindleField);
        grid.add(pauseCheck, 0, row, 2, 1);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        Button generateButton = new Button("Gerar CNC Job...");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                double safeZ = parse(safeZField, "Travel Z");
                double cutDepth = parse(cutDepthField, "Cut Z");
                boolean multiDepth = multiDepthCb.isSelected();
                double depthPerPass = multiDepth ? parse(depthPerPassField, "Depth per pass") : 1;
                double feed = parse(feedField, "Feed rate");
                int spindle = (int) parse(spindleField, "Spindle RPM");
                GeometryGCodeParameters params = new GeometryGCodeParameters(
                        safeZ, cutDepth, multiDepth, depthPerPass, feed, spindle, pauseCheck.isSelected());
                List<ToolGeometry> resultTools = multiTool
                        ? tools
                        : List.of(new ToolGeometry(parse(toolDiaField, "Tool Dia"), combinedGeometry));
                errorLabel.setText("");
                onGenerate.accept(new Result(resultTools, params));
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        VBox box = new VBox(10, new Label("Geometry -> CNC Job (" + units + ")"));
        if (multiTool) {
            box.getChildren().addAll(new Label("Ferramentas (associadas ao NCC):"), toolTable);
        }
        box.getChildren().addAll(grid, errorLabel, generateButton, closeButton);
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
