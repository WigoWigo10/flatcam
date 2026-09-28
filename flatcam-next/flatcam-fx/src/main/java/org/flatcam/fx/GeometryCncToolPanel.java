package org.flatcam.fx;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
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

    record Result(List<ToolGeometry> tools, GeometryGCodeParameters parameters,
                  Map<Integer, VTipSettings> vTools) {
    }

    private GeometryCncToolPanel() {
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> tools,
                      Consumer<Result> onGenerate, Runnable onClose) {
        return build(units, combinedGeometry, tools, null, onGenerate, onClose);
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> tools,
                      GeometryGCodeParameters defaults, Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(units);
        boolean multiTool = !tools.isEmpty();

        TextField toolDiaField = new TextField(format(metric ? 0.8 : 0.031));
        TableView<ToolGeometry> toolTable = new TableView<>();
        toolTable.setMinWidth(0);
        toolTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        if (multiTool) {
            TableColumn<ToolGeometry, Number> idColumn = new TableColumn<>("#");
            idColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(
                    toolTable.getItems().indexOf(cellData.getValue()) + 1));
            TableColumn<ToolGeometry, Number> diaColumn = new TableColumn<>("Diametro");
            diaColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().toolDiameter()));
            TableColumn<ToolGeometry, String> typeColumn = new TableColumn<>("TT");
            typeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                    cellData.getValue().toolProfile().name()));
            toolTable.getColumns().addAll(List.of(idColumn, diaColumn, typeColumn));
            toolTable.getItems().setAll(tools);
            toolTable.setPrefHeight(Math.min(160, 28 + tools.size() * 28));
        } else if (tools.size() == 1) {
            toolDiaField.setText(format(tools.get(0).toolDiameter()));
        }

        TextField safeZField = new TextField(defaults == null ? (metric ? "3.0" : "0.1")
                : format(defaults.safeZ()));
        TextField cutDepthField = new TextField(defaults == null ? (metric ? "0.1" : "0.004")
                : format(defaults.cutDepth()));
        CheckBox multiDepthCb = new CheckBox("Multi-Depth");
        multiDepthCb.setSelected(defaults != null && defaults.multiDepth());
        TextField depthPerPassField = new TextField(defaults == null ? (metric ? "0.05" : "0.002")
                : format(defaults.depthPerPass()));
        depthPerPassField.disableProperty().bind(multiDepthCb.selectedProperty().not());
        TextField feedField = new TextField(defaults == null ? (metric ? "300" : "12")
                : format(defaults.feedRate()));
        TextField spindleField = new TextField(defaults == null ? "10000"
                : Integer.toString(defaults.spindleSpeedRpm()));
        for (TextField field : List.of(toolDiaField, safeZField, cutDepthField,
                depthPerPassField, feedField, spindleField)) {
            field.setPrefColumnCount(7);
            field.setMinWidth(0);
        }
        CheckBox pauseCheck = new CheckBox("Pausar para troca de ferramenta (M0)");
        pauseCheck.setSelected(defaults == null ? tools.size() > 1 : defaults.pauseForToolChange());
        pauseCheck.setDisable(tools.size() <= 1);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        if (!multiTool) {
            grid.addRow(row++, new Label("Tool Dia:"), toolDiaField);
        }
        grid.addRow(row++, new Label("Travel Z:"), safeZField);
        grid.addRow(row++, new Label("Cut Z (nao V):"), cutDepthField);
        grid.addRow(row++, multiDepthCb, depthPerPassField);
        grid.addRow(row++, new Label("Feed rate:"), feedField);
        grid.addRow(row++, new Label("Spindle RPM:"), spindleField);
        grid.add(pauseCheck, 0, row, 2, 1);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        Map<Integer, TextField[]> vFields = new LinkedHashMap<>();
        VBox vSettings = new VBox(8);
        for (int i = 0; i < tools.size(); i++) {
            ToolGeometry tool = tools.get(i);
            if (tool.toolProfile() != ToolProfile.V || tool.geometry().isEmpty()) continue;
            TextField tipDia = new TextField(metric ? "0.1" : "0.004");
            TextField tipAngle = new TextField("30");
            tipDia.setPrefColumnCount(6);
            tipAngle.setPrefColumnCount(6);
            Label calculated = new Label();
            calculated.setWrapText(true);
            Runnable updateDepth = () -> {
                try {
                    double depth = new VTipSettings(parse(tipDia, "V-Tip Dia"),
                            parse(tipAngle, "V-Tip Angle")).cutDepth(tool.toolDiameter());
                    calculated.setText("Cut Z calculado: -" + format(depth) + " " + units);
                } catch (RuntimeException error) {
                    calculated.setText("Cut Z: " + error.getMessage());
                }
            };
            tipDia.textProperty().addListener((obs, oldValue, value) -> updateDepth.run());
            tipAngle.textProperty().addListener((obs, oldValue, value) -> updateDepth.run());
            updateDepth.run();
            GridPane vGrid = new GridPane();
            vGrid.setHgap(8);
            vGrid.setVgap(6);
            vGrid.addRow(0, new Label("V-Tip Dia:"), tipDia);
            vGrid.addRow(1, new Label("V-Tip Angle (graus):"), tipAngle);
            vSettings.getChildren().addAll(new Label("Ferramenta " + (i + 1)
                    + " — largura " + format(tool.toolDiameter()) + " " + units), vGrid, calculated);
            vFields.put(i, new TextField[]{tipDia, tipAngle});
        }
        Button generateButton = new Button("Gerar CNC Job...");
        generateButton.getStyleClass().add("primary-action");
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
                Map<Integer, VTipSettings> vTools = new LinkedHashMap<>();
                for (var entry : vFields.entrySet()) {
                    TextField[] fields = entry.getValue();
                    VTipSettings settings = new VTipSettings(parse(fields[0], "V-Tip Dia"),
                            parse(fields[1], "V-Tip Angle"));
                    settings.cutDepth(resultTools.get(entry.getKey()).toolDiameter());
                    vTools.put(entry.getKey(), settings);
                }
                errorLabel.setText("");
                onGenerate.accept(new Result(resultTools, params, Map.copyOf(vTools)));
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        Label title = new Label("Geometry -> CNC Job");
        title.getStyleClass().add("tool-title");
        VBox box = new VBox(10, title, new Label("Unidades: " + units));
        if (defaults != null) {
            Label restored = new Label("Parametros basicos de corte recuperados do projeto; "
                    + "confira os valores antes de gerar G-code.");
            restored.setWrapText(true);
            box.getChildren().add(restored);
        }
        if (multiTool) {
            box.getChildren().addAll(new Label("Ferramentas (associadas ao NCC):"), toolTable);
        }
        box.getChildren().add(grid);
        if (!vFields.isEmpty()) box.getChildren().add(vSettings);
        box.getChildren().addAll(errorLabel, generateButton, closeButton);
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
