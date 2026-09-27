package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator;

/** Python-style drilling panel. Machining values are stored per Excellon tool. */
final class DrillGCodeToolPanel {

    record SourceCandidate(TreeItem<String> item, ExcellonImage image) {
        @Override public String toString() { return item.getValue(); }
    }

    record Result(SourceCandidate source, Map<Integer, DrillGCodeParameters> settingsByTool,
                  List<Integer> orderedToolIds, GCodeGenerator.DrillJobOptions options) { }

    private static final class ToolRow {
        final int id;
        final double diameter;
        final int drills;
        final int slots;
        String cutZ;
        String travelZ;
        String feedZ;
        String spindle;

        ToolRow(int id, double diameter, int drills, int slots, boolean metric) {
            this.id = id;
            this.diameter = diameter;
            this.drills = drills;
            this.slots = slots;
            cutZ = metric ? "-1.7" : "-0.07";
            travelZ = metric ? "2.0" : "0.1";
            feedZ = metric ? "300" : "12";
            spindle = "0";
        }

        DrillGCodeParameters parameters(boolean toolChange) {
            double cut = parse(cutZ, "Cut Z (Tool " + id + ")");
            if (cut >= 0) throw new IllegalArgumentException("Cut Z deve ser negativo para Tool " + id + ".");
            return new DrillGCodeParameters(parse(travelZ, "Travel Z (Tool " + id + ")"), -cut,
                    parse(feedZ, "Feedrate Z (Tool " + id + ")"),
                    parseInt(spindle, "Spindle speed (Tool " + id + ")"), toolChange);
        }
    }

    private DrillGCodeToolPanel() { }

    static Node build(List<SourceCandidate> sources, SourceCandidate initialSource,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(initialSource.image().units());
        ComboBox<SourceCandidate> sourceCombo = new ComboBox<>(FXCollections.observableArrayList(sources));
        sourceCombo.setValue(initialSource);
        sourceCombo.setMinWidth(0);
        sourceCombo.setPrefWidth(180);
        sourceCombo.setMaxWidth(Double.MAX_VALUE);

        TableView<ToolRow> table = new TableView<>();
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<ToolRow, String> numberColumn = column("#", row -> Integer.toString(row.id));
        numberColumn.setMinWidth(34);
        numberColumn.setMaxWidth(34);
        TableColumn<ToolRow, String> diameterColumn = column("Diameter",
                row -> String.format(java.util.Locale.ROOT, "%.4f", row.diameter));
        TableColumn<ToolRow, String> drillsColumn = column("Drills",
                row -> row.drills == 0 ? "" : Integer.toString(row.drills));
        drillsColumn.setMinWidth(55);
        TableColumn<ToolRow, String> slotsColumn = column("Slots",
                row -> row.slots == 0 ? "" : Integer.toString(row.slots));
        slotsColumn.setMinWidth(50);
        table.getColumns().addAll(numberColumn, diameterColumn, drillsColumn, slotsColumn);
        Label totals = new Label();
        totals.setStyle("-fx-font-weight: bold; -fx-text-fill: #70a7ff;");
        Runnable updateRows = () -> {
            ExcellonImage image = sourceCombo.getValue().image();
            List<ToolRow> rows = image.toolDiameters().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new ToolRow(entry.getKey(), entry.getValue(),
                            image.drillCounts().getOrDefault(entry.getKey(), 0),
                            image.slotCounts().getOrDefault(entry.getKey(), 0), metric))
                    .toList();
            table.getItems().setAll(rows);
            table.setPrefHeight(Math.min(200, 32 + rows.size() * 27));
            table.getSelectionModel().selectAll();
            totals.setText("Total Drills: " + image.totalDrills() + "     Total Slots: " + image.totalSlots());
        };
        updateRows.run();
        sourceCombo.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null) updateRows.run();
        });

        ToggleGroup orderGroup = new ToggleGroup();
        RadioButton noOrder = radio("No", orderGroup);
        RadioButton forwardOrder = radio("Forward", orderGroup);
        RadioButton reverseOrder = radio("Reverse", orderGroup);
        noOrder.setSelected(true);
        HBox orderRow = new HBox(8, new Label("Tool order:"), noOrder, forwardOrder, reverseOrder);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        Button searchDb = new Button("Search DB");
        searchDb.setMaxWidth(Double.MAX_VALUE);
        searchDb.setDisable(true);
        searchDb.setTooltip(new Tooltip("Busca automatica de parametros de furacao na Tools Database ainda indisponivel."));

        Label selectedTitle = heading("Parameters for: Multiple Tools");
        TextField cutZ = field(metric ? "-1.7" : "-0.07");
        TextField depthPerPass = field(metric ? "0.7" : "0.03");
        TextField travelZ = field(metric ? "2.0" : "0.1");
        TextField feedZ = field(metric ? "300" : "12");
        TextField spindle = field("0");
        TextField dwellTime = field("1.0");
        TextField offsetZ = field("0.0");
        CheckBox multiDepth = new CheckBox("Multi-Depth");
        CheckBox dwell = new CheckBox("Dwell");
        for (Node node : List.of(multiDepth, depthPerPass, dwell, dwellTime, offsetZ)) node.setDisable(true);
        String pending = "Opcao ainda nao aplicada ao G-code pelo FX.";
        for (Node node : List.of(multiDepth, depthPerPass, dwell, dwellTime, offsetZ))
            Tooltip.install(node, new Tooltip(pending));
        boolean[] loading = {false};
        Runnable loadSelection = () -> {
            List<ToolRow> selected = table.getSelectionModel().getSelectedItems();
            selectedTitle.setText(selected.isEmpty() ? "Parameters for: No Tool Selected"
                    : selected.size() > 1 ? "Parameters for: Multiple Tools"
                    : "Parameters for: Tool " + selected.get(0).id);
            if (selected.isEmpty()) return;
            ToolRow row = selected.get(0);
            loading[0] = true;
            cutZ.setText(selected.stream().allMatch(tool -> tool.cutZ.equals(row.cutZ)) ? row.cutZ : "");
            travelZ.setText(selected.stream().allMatch(tool -> tool.travelZ.equals(row.travelZ)) ? row.travelZ : "");
            feedZ.setText(selected.stream().allMatch(tool -> tool.feedZ.equals(row.feedZ)) ? row.feedZ : "");
            spindle.setText(selected.stream().allMatch(tool -> tool.spindle.equals(row.spindle)) ? row.spindle : "");
            loading[0] = false;
        };
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ToolRow>) change -> loadSelection.run());
        loadSelection.run();
        bindDraft(cutZ, table, loading, (row, value) -> row.cutZ = value);
        bindDraft(travelZ, table, loading, (row, value) -> row.travelZ = value);
        bindDraft(feedZ, table, loading, (row, value) -> row.feedZ = value);
        bindDraft(spindle, table, loading, (row, value) -> row.spindle = value);
        var noneSelected = Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItems().isEmpty(),
                table.getSelectionModel().getSelectedItems());
        for (TextField field : List.of(cutZ, travelZ, feedZ, spindle)) field.disableProperty().bind(noneSelected);

        GridPane perTool = new GridPane();
        perTool.setHgap(8);
        perTool.setVgap(8);
        perTool.addRow(0, new Label("Cut Z:"), cutZ);
        perTool.addRow(1, multiDepth, depthPerPass);
        perTool.addRow(2, new Label("Travel Z:"), travelZ);
        perTool.addRow(3, new Label("Feedrate Z:"), feedZ);
        perTool.addRow(4, new Label("Spindle speed:"), spindle);
        perTool.addRow(5, dwell, dwellTime);
        perTool.addRow(6, new Label("Offset Z:"), offsetZ);
        Label unsupportedNote = new Label("Multi-Depth, Dwell e Offset Z ainda nao geram comandos no FX.");
        unsupportedNote.setWrapText(true);
        unsupportedNote.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Button applyAll = new Button("Apply parameters to all tools");
        applyAll.setMaxWidth(Double.MAX_VALUE);
        applyAll.disableProperty().bind(Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItems().size() != 1 || table.getItems().size() < 2,
                table.getSelectionModel().getSelectedItems(), table.getItems()));
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.managedProperty().bind(feedback.textProperty().isNotEmpty());
        applyAll.setOnAction(event -> {
            ToolRow source = table.getSelectionModel().getSelectedItem();
            if (source == null) return;
            try {
                source.parameters(false);
                for (ToolRow row : table.getItems()) {
                    row.cutZ = source.cutZ;
                    row.travelZ = source.travelZ;
                    row.feedZ = source.feedZ;
                    row.spindle = source.spindle;
                }
                feedback.setText("Parametros copiados para todas as ferramentas.");
            } catch (RuntimeException error) { feedback.setText(error.getMessage()); }
        });

        CheckBox toolChange = new CheckBox("Tool change");
        TextField toolChangeZ = field(metric ? "15.0" : "0.6");
        toolChangeZ.disableProperty().bind(toolChange.selectedProperty().not());
        TextField endMoveZ = field(metric ? "0.5" : "0.02");
        TextField endMoveXY = field("None");
        endMoveXY.setPromptText("None ou X,Y");
        endMoveXY.setTooltip(new Tooltip("O movimento XY final usa End move Z como altura; confirme que esta livre de obstaculos."));
        ComboBox<String> preprocessor = new ComboBox<>(FXCollections.observableArrayList("default"));
        preprocessor.setValue("default");
        preprocessor.setDisable(true);
        preprocessor.setTooltip(new Tooltip("O gerador do FX possui apenas o preprocessor default."));
        GridPane common = new GridPane();
        common.setHgap(8);
        common.setVgap(8);
        common.addRow(0, toolChange);
        common.addRow(1, new Label("Tool change Z:"), toolChangeZ);
        common.addRow(2, new Label("End move Z:"), endMoveZ);
        common.addRow(3, new Label("End move X,Y:"), endMoveXY);
        common.addRow(4, new Label("Preprocessor:"), preprocessor);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        Button generate = new Button("Generate CNC Job");
        generate.setMaxWidth(Double.MAX_VALUE);
        generate.setOnAction(event -> {
            try {
                List<ToolRow> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
                if (selected.isEmpty()) throw new IllegalArgumentException("Selecione ferramentas para furar.");
                if (!toolChange.isSelected()
                        && selected.stream().map(row -> row.diameter).distinct().count() > 1)
                    throw new IllegalArgumentException("Ha diametros diferentes no mesmo G-code. "
                            + "Ative Tool change para pausar entre ferramentas.");
                if (forwardOrder.isSelected()) selected.sort(Comparator.comparingDouble(row -> row.diameter));
                else if (reverseOrder.isSelected())
                    selected.sort(Comparator.comparingDouble((ToolRow row) -> row.diameter).reversed());
                else selected.sort(Comparator.comparingInt(row -> table.getItems().indexOf(row)));
                Map<Integer, DrillGCodeParameters> settings = new LinkedHashMap<>();
                List<Integer> orderedIds = new ArrayList<>();
                for (ToolRow row : selected) {
                    settings.put(row.id, row.parameters(toolChange.isSelected()));
                    orderedIds.add(row.id);
                }
                Double endX = null, endY = null;
                if (!endMoveXY.getText().trim().equalsIgnoreCase("None")
                        && !endMoveXY.getText().isBlank()) {
                    String[] xy = endMoveXY.getText().split("[,;]");
                    if (xy.length != 2)
                        throw new IllegalArgumentException("End move X,Y: use None ou X,Y.");
                    endX = parse(xy[0], "End move X");
                    endY = parse(xy[1], "End move Y");
                }
                double endZ = parse(endMoveZ.getText(), "End move Z");
                double changeZ = toolChange.isSelected() ? parse(toolChangeZ.getText(), "Tool change Z")
                        : settings.get(orderedIds.get(0)).safeZ();
                var options = new GCodeGenerator.DrillJobOptions(
                        toolChange.isSelected(), changeZ, endZ, endX, endY);
                errorLabel.setText("");
                onGenerate.accept(new Result(sourceCombo.getValue(), Map.copyOf(settings),
                        List.copyOf(orderedIds), options));
            } catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
        });
        Button reset = new Button("Reset Tool");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(event -> {
            sourceCombo.setValue(initialSource);
            updateRows.run();
            noOrder.setSelected(true);
            toolChange.setSelected(false);
            toolChangeZ.setText(metric ? "15.0" : "0.6");
            endMoveZ.setText(metric ? "0.5" : "0.02");
            endMoveXY.setText("None");
            feedback.setText("");
            errorLabel.setText("");
        });
        Button close = new Button("Fechar");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(event -> onClose.run());

        VBox box = new VBox(8, heading("Drilling Tool"), heading("EXCELLON:"), sourceCombo,
                new Separator(), table, totals, orderRow, searchDb, new Separator(),
                selectedTitle, perTool, unsupportedNote, applyAll, feedback, new Separator(),
                heading("Common Parameters"), common, errorLabel, generate, reset, close);
        box.setPadding(new Insets(12));
        return box;
    }

    private static TableColumn<ToolRow, String> column(String title,
                                                        java.util.function.Function<ToolRow, String> value) {
        TableColumn<ToolRow, String> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(value.apply(data.getValue())));
        return column;
    }

    private static RadioButton radio(String label, ToggleGroup group) {
        RadioButton button = new RadioButton(label);
        button.setToggleGroup(group);
        return button;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-section-title");
        return label;
    }

    private static TextField field(String value) {
        TextField field = new TextField(value);
        field.setPrefColumnCount(8);
        field.setMinWidth(0);
        return field;
    }

    private static void bindDraft(TextField field, TableView<ToolRow> table, boolean[] loading,
                                  java.util.function.BiConsumer<ToolRow, String> update) {
        field.textProperty().addListener((observable, oldValue, value) -> {
            if (!loading[0])
                List.copyOf(table.getSelectionModel().getSelectedItems())
                        .forEach(row -> update.accept(row, value));
        });
    }

    private static double parse(String text, String label) {
        try { return Double.parseDouble(text.trim().replace(',', '.')); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + ": numero invalido."); }
    }

    private static int parseInt(String text, String label) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + ": inteiro invalido."); }
    }
}
