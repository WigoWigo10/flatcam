package org.flatcam.fx;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.DrillGCodeParameters;

/**
 * Parameters for drilling G-code generation plus a tool-selection table, as
 * an embeddable panel rather than a modal dialog - appTools/ToolDrilling.py's
 * run() switches the LEFT sidebar's own "Tool" tab (app.ui.tool_tab) to this
 * tool's UI instead of popping a separate window, same as
 * ToolIsolation.py (see IsolationToolPanel's doc).
 *
 * <p>The tools table mirrors ToolDrilling.py's own tools_table: row
 * SELECTION (not a checkbox) decides which tools are included in the
 * generated G-code (on_cnc_button_click reads tools_table.selectedItems()) -
 * this is a different table, with different semantics, from the Excellon
 * Object panel's own informational one (ObjectUI.py's tools_table, whose "P"
 * checkbox only toggles per-tool plot visibility and has no effect on
 * G-code generation). All rows start selected so the common case ("drill
 * everything") needs no extra clicks.
 */
final class DrillGCodeToolPanel {

    record Result(DrillGCodeParameters params, Set<Integer> selectedToolIds) {
    }

    /** One row of the tool-selection table. */
    public static final class ToolRow {
        private final SimpleIntegerProperty toolId;
        private final SimpleDoubleProperty diameter;
        private final SimpleIntegerProperty drills;
        private final SimpleIntegerProperty slots;

        ToolRow(int toolId, double diameter, int drills, int slots) {
            this.toolId = new SimpleIntegerProperty(toolId);
            this.diameter = new SimpleDoubleProperty(diameter);
            this.drills = new SimpleIntegerProperty(drills);
            this.slots = new SimpleIntegerProperty(slots);
        }

        public int getToolId() {
            return toolId.get();
        }

        public double getDiameter() {
            return diameter.get();
        }

        public int getDrills() {
            return drills.get();
        }

        public int getSlots() {
            return slots.get();
        }
    }

    private DrillGCodeToolPanel() {
    }

    /**
     * @param onGenerate called with the parsed parameters and selected tool ids when "Gerar" is clicked and they're valid.
     * @param onClose    called when "Fechar" is clicked - MainWindow uses it to restore the tool tab's placeholder.
     */
    static Node build(ExcellonImage image, Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equals(image.units());

        TableView<ToolRow> table = buildToolsTableView(image);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().selectAll();

        TextField safeZField = new TextField(metric ? "3.0" : "0.1");
        TextField depthField = new TextField(metric ? "1.7" : "0.07");
        TextField feedField = new TextField(metric ? "300" : "12");
        TextField spindleField = new TextField("10000");
        for (TextField field : List.of(safeZField, depthField, feedField, spindleField)) {
            field.setPrefColumnCount(7);
            field.setMinWidth(0);
        }
        CheckBox pauseCheck = new CheckBox("Pausar para troca de ferramenta (M0)");
        pauseCheck.setSelected(image.toolDiameters().size() > 1);
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
                Set<Integer> selectedToolIds = table.getSelectionModel().getSelectedItems().stream()
                        .map(ToolRow::getToolId).collect(Collectors.toSet());
                errorLabel.setText("");
                onGenerate.accept(new Result(params, selectedToolIds));
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        closeButton.setOnAction(e -> onClose.run());

        VBox box = new VBox(10,
                new Label("Parametros (unidades do arquivo: " + image.units() + ")"),
                new Label("Ferramentas a furar:"), table,
                grid, errorLabel, generateButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    /**
     * The bare #/Diametro/Furos/Slots table, with no selection behavior wired -
     * shared with the Excellon Object properties panel's own read-only tools
     * table (ObjectUI.py's tools_table shows the same per-tool breakdown,
     * though there row selection is informational only, unlike here).
     */
    static TableView<ToolRow> buildToolsTableView(ExcellonImage image) {
        var drillCounts = image.drillCounts();
        var slotCounts = image.slotCounts();
        List<ToolRow> rows = image.toolDiameters().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new ToolRow(entry.getKey(), entry.getValue(),
                        drillCounts.getOrDefault(entry.getKey(), 0), slotCounts.getOrDefault(entry.getKey(), 0)))
                .toList();

        TableColumn<ToolRow, Number> idColumn = new TableColumn<>("#");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("toolId"));
        TableColumn<ToolRow, Number> diameterColumn = new TableColumn<>("Diametro");
        diameterColumn.setCellValueFactory(new PropertyValueFactory<>("diameter"));
        TableColumn<ToolRow, Number> drillsColumn = new TableColumn<>("Furos");
        drillsColumn.setCellValueFactory(new PropertyValueFactory<>("drills"));
        TableColumn<ToolRow, Number> slotsColumn = new TableColumn<>("Slots");
        slotsColumn.setCellValueFactory(new PropertyValueFactory<>("slots"));

        TableView<ToolRow> table = new TableView<>();
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(List.of(idColumn, diameterColumn, drillsColumn, slotsColumn));
        table.getItems().setAll(rows);
        table.setPrefHeight(Math.min(160, 28 + rows.size() * 28));
        return table;
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
