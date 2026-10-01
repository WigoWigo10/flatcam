package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
import javafx.util.StringConverter;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.app.project.DrillCncSettings;

/** Python-style drilling panel. Machining values are stored per Excellon tool. */
final class DrillGCodeToolPanel {

    record SourceCandidate(TreeItem<String> item, ExcellonImage image,
                           Map<Integer, DrillGCodeParameters> drillDefaults, DrillCncSettings cncSettings) {
        SourceCandidate(TreeItem<String> item, ExcellonImage image, Map<Integer, DrillGCodeParameters> drillDefaults) {
            this(item, image, drillDefaults, null);
        }
        @Override public String toString() { return item.getValue(); }
    }

    record Result(SourceCandidate source, Map<Integer, DrillGCodeParameters> settingsByTool,
                  List<Integer> orderedToolIds, GCodeGenerator.DrillJobOptions options,
                  GCodePreprocessor preprocessor, DrillCncSettings.ToolOrder toolOrder) {
        DrillCncSettings cncSettings() { return new DrillCncSettings(preprocessor, options, orderedToolIds, toolOrder); }
    }

    private static final class ToolRow {
        final int id;
        final double diameter;
        final int drills;
        final int slots;
        String cutZ;
        String travelZ;
        String feedZ;
        String spindle;
        boolean multiDepth;
        String depthPerPass;
        boolean dwell;
        String dwellTime;
        String offsetZ;

        ToolRow(int id, double diameter, int drills, int slots, boolean metric) {
            this.id = id;
            this.diameter = diameter;
            this.drills = drills;
            this.slots = slots;
            cutZ = metric ? "-1.7" : "-0.07";
            travelZ = metric ? "2.0" : "0.1";
            feedZ = metric ? "300" : "12";
            spindle = "0";
            depthPerPass = metric ? "0.7" : "0.03";
            dwellTime = "1.0";
            offsetZ = "0.0";
        }

        DrillGCodeParameters parameters(boolean toolChange) {
            return parameters(toolChange, false);
        }

        DrillGCodeParameters parameters(boolean toolChange, boolean roland) {
            double cut = parse(cutZ, "Cut Z (Tool " + id + ")");
            if (cut >= 0) throw new IllegalArgumentException("Cut Z deve ser negativo para Tool " + id + ".");
            return new DrillGCodeParameters(parse(travelZ, "Travel Z (Tool " + id + ")"), -cut,
                    parse(feedZ, "Feedrate Z (Tool " + id + ")"),
                    roland ? 0 : parseInt(spindle, "Spindle speed (Tool " + id + ")"), toolChange,
                    multiDepth, multiDepth ? parse(depthPerPass, "Depth per pass (Tool " + id + ")") : 0,
                    !roland && dwell, !roland && dwell ? parse(dwellTime, "Dwell time (Tool " + id + ")") : 0,
                    parse(offsetZ, "Offset Z (Tool " + id + ")"));
        }

        void applyDatabaseTool(LegacyToolsDatabase.DrillTool tool) {
            applyDefaults(tool.parameters());
        }

        void applyDefaults(DrillGCodeParameters values) {
            cutZ = Double.toString(-values.drillDepth());
            travelZ = Double.toString(values.safeZ());
            feedZ = Double.toString(values.feedRate());
            spindle = Integer.toString(values.spindleSpeedRpm());
            multiDepth = values.multiDepth();
            depthPerPass = Double.toString(values.depthPerPass());
            dwell = values.dwell();
            dwellTime = Double.toString(values.dwellSeconds());
            offsetZ = Double.toString(values.offsetZ());
        }
    }

    private DrillGCodeToolPanel() { }

    static Node build(List<SourceCandidate> sources, SourceCandidate initialSource,
                      Supplier<List<LegacyToolsDatabase.DrillTool>> databaseLoader,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(initialSource.image().units());
        ComboBox<SourceCandidate> sourceCombo = new ComboBox<>(FXCollections.observableArrayList(sources));
        sourceCombo.setValue(initialSource);
        sourceCombo.setId("drill-source");
        sourceCombo.setMinWidth(0);
        sourceCombo.setPrefWidth(180);
        sourceCombo.setMaxWidth(Double.MAX_VALUE);

        TableView<ToolRow> table = new TableView<>();
        table.setId("drill-tools");
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
            boolean sourceMetric = "MM".equalsIgnoreCase(image.units());
            List<ToolRow> rows = image.toolDiameters().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> {
                        ToolRow row = new ToolRow(entry.getKey(), entry.getValue(),
                                image.drillCounts().getOrDefault(entry.getKey(), 0),
                                image.slotCounts().getOrDefault(entry.getKey(), 0), sourceMetric);
                        DrillGCodeParameters defaults = sourceCombo.getValue().drillDefaults().get(row.id);
                        if (defaults != null) row.applyDefaults(defaults);
                        return row;
                    })
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
        noOrder.setId("drill-order-no");
        forwardOrder.setId("drill-order-forward");
        reverseOrder.setId("drill-order-reverse");
        noOrder.setSelected(true);
        HBox orderRow = new HBox(8, new Label("Tool order:"), noOrder, forwardOrder, reverseOrder);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        Button searchDb = new Button("Search DB");
        searchDb.setMaxWidth(Double.MAX_VALUE);
        searchDb.setTooltip(new Tooltip("Carrega parametros das ferramentas de furacao da Tools Database do Python."));

        Label selectedTitle = heading("Parameters for: Multiple Tools");
        TextField cutZ = field(metric ? "-1.7" : "-0.07");
        TextField depthPerPass = field(metric ? "0.7" : "0.03");
        TextField travelZ = field(metric ? "2.0" : "0.1");
        TextField feedZ = field(metric ? "300" : "12");
        TextField spindle = field("0");
        spindle.setId("drill-power");
        TextField dwellTime = field("1.0");
        TextField offsetZ = field("0.0");
        CheckBox multiDepth = new CheckBox("Multi-Depth");
        CheckBox dwell = new CheckBox("Dwell");
        dwell.setId("drill-dwell");
        multiDepth.setTooltip(new Tooltip("Desce em etapas e retrai entre passes."));
        dwell.setTooltip(new Tooltip("Espera apos ligar o spindle antes de furar."));
        offsetZ.setTooltip(new Tooltip("Valor positivo aumenta a profundidade; negativo reduz."));
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
            multiDepth.setSelected(selected.stream().allMatch(tool -> tool.multiDepth));
            depthPerPass.setText(selected.stream().allMatch(tool -> tool.depthPerPass.equals(row.depthPerPass))
                    ? row.depthPerPass : "");
            dwell.setSelected(selected.stream().allMatch(tool -> tool.dwell));
            dwellTime.setText(selected.stream().allMatch(tool -> tool.dwellTime.equals(row.dwellTime))
                    ? row.dwellTime : "");
            offsetZ.setText(selected.stream().allMatch(tool -> tool.offsetZ.equals(row.offsetZ))
                    ? row.offsetZ : "");
            loading[0] = false;
        };
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ToolRow>) change -> loadSelection.run());
        loadSelection.run();
        bindDraft(cutZ, table, loading, (row, value) -> row.cutZ = value);
        bindDraft(travelZ, table, loading, (row, value) -> row.travelZ = value);
        bindDraft(feedZ, table, loading, (row, value) -> row.feedZ = value);
        bindDraft(spindle, table, loading, (row, value) -> row.spindle = value);
        bindDraft(depthPerPass, table, loading, (row, value) -> row.depthPerPass = value);
        bindDraft(dwellTime, table, loading, (row, value) -> row.dwellTime = value);
        bindDraft(offsetZ, table, loading, (row, value) -> row.offsetZ = value);
        multiDepth.selectedProperty().addListener((observable, oldValue, value) -> {
            if (!loading[0]) List.copyOf(table.getSelectionModel().getSelectedItems())
                    .forEach(row -> row.multiDepth = value);
        });
        dwell.selectedProperty().addListener((observable, oldValue, value) -> {
            if (!loading[0]) List.copyOf(table.getSelectionModel().getSelectedItems())
                    .forEach(row -> row.dwell = value);
        });
        var noneSelected = Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItems().isEmpty(),
                table.getSelectionModel().getSelectedItems());
        for (TextField field : List.of(cutZ, travelZ, feedZ, spindle)) field.disableProperty().bind(noneSelected);
        multiDepth.disableProperty().bind(noneSelected);
        dwell.disableProperty().bind(noneSelected);
        depthPerPass.disableProperty().bind(noneSelected.or(multiDepth.selectedProperty().not()));
        dwellTime.disableProperty().bind(noneSelected.or(dwell.selectedProperty().not()));
        offsetZ.disableProperty().bind(noneSelected);

        GridPane perTool = new GridPane();
        perTool.setHgap(8);
        perTool.setVgap(8);
        perTool.addRow(0, new Label("Cut Z:"), cutZ);
        perTool.addRow(1, multiDepth, depthPerPass);
        perTool.addRow(2, new Label("Travel Z:"), travelZ);
        perTool.addRow(3, new Label("Feedrate Z:"), feedZ);
        Label spindleLabel = new Label("Spindle RPM:");
        perTool.addRow(4, spindleLabel, spindle);
        perTool.addRow(5, dwell, dwellTime);
        perTool.addRow(6, new Label("Offset Z:"), offsetZ);
        Label machiningNote = new Label("Dwell espera apos iniciar o spindle; Offset Z altera Cut Z.");
        machiningNote.setWrapText(true);
        machiningNote.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Button applyAll = new Button("Apply parameters to all tools");
        applyAll.setMaxWidth(Double.MAX_VALUE);
        applyAll.disableProperty().bind(Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItems().size() != 1 || table.getItems().size() < 2,
                table.getSelectionModel().getSelectedItems(), table.getItems()));
        Label feedback = new Label();
        feedback.setId("drill-feedback");
        feedback.setWrapText(true);
        feedback.managedProperty().bind(feedback.textProperty().isNotEmpty());
        if (!initialSource.drillDefaults().isEmpty()) {
            feedback.setText("Parametros de furacao recuperados do projeto; confira-os antes de gerar G-code.");
        }
        sourceCombo.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null) feedback.setText(value.drillDefaults().isEmpty() ? ""
                    : "Parametros de furacao recuperados do projeto; confira-os antes de gerar G-code.");
        });
        searchDb.setOnAction(event -> {
            try {
                List<LegacyToolsDatabase.DrillTool> database = databaseLoader.get();
                if (database.isEmpty()) {
                    feedback.setText("Nenhuma ferramenta de furacao carregada da base.");
                    return;
                }
                Map<ToolRow, LegacyToolsDatabase.DrillTool> matches = new LinkedHashMap<>();
                List<String> ambiguous = new ArrayList<>();
                for (ToolRow row : table.getItems()) {
                    try {
                        LegacyToolsDatabase.matchDrillTool(database, row.diameter)
                                .ifPresent(tool -> matches.put(row, tool));
                    } catch (IllegalArgumentException error) {
                        ambiguous.add("T" + row.id);
                    }
                }
                if (!ambiguous.isEmpty()) {
                    feedback.setText("Base ambigua para " + String.join(", ", ambiguous)
                            + "; nenhum parametro foi alterado.");
                    return;
                }
                matches.forEach(ToolRow::applyDatabaseTool);
                loadSelection.run();
                feedback.setText(matches.size() + " ferramenta(s) atualizada(s) pela base; "
                        + (table.getItems().size() - matches.size()) + " sem correspondencia. "
                        + "Diametros do Excellon preservados.");
            } catch (RuntimeException error) {
                feedback.setText("Tools Database: " + error.getMessage());
            }
        });
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
                    row.multiDepth = source.multiDepth;
                    row.depthPerPass = source.depthPerPass;
                    row.dwell = source.dwell;
                    row.dwellTime = source.dwellTime;
                    row.offsetZ = source.offsetZ;
                }
                feedback.setText("Parametros copiados para todas as ferramentas.");
            } catch (RuntimeException error) { feedback.setText(error.getMessage()); }
        });

        CheckBox toolChange = new CheckBox("Tool change");
        toolChange.setId("drill-tool-change");
        toolChange.setSelected(initialSource.drillDefaults().values().stream()
                .anyMatch(DrillGCodeParameters::pauseForToolChange));
        sourceCombo.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null) toolChange.setSelected(value.drillDefaults().values().stream()
                    .anyMatch(DrillGCodeParameters::pauseForToolChange));
        });
        TextField toolChangeZ = field(metric ? "15.0" : "0.6");
        toolChangeZ.disableProperty().bind(toolChange.selectedProperty().not());
        TextField endMoveZ = field(metric ? "0.5" : "0.02");
        TextField endMoveXY = field("None");
        endMoveZ.setId("drill-end-z");
        endMoveXY.setId("drill-end-xy");
        endMoveXY.setPromptText("None ou X,Y");
        endMoveXY.setTooltip(new Tooltip("O movimento XY final usa End move Z como altura; confirme que esta livre de obstaculos."));
        ComboBox<GCodePreprocessor> preprocessor = new ComboBox<>(
                FXCollections.observableArrayList(GCodePreprocessor.millingProfiles()));
        preprocessor.setId("drill-preprocessor");
        preprocessor.setValue(GCodePreprocessor.FX_PORTABLE);
        var automaticTools = Bindings.createBooleanBinding(
                () -> !preprocessor.getValue().supportsManualToolChange(), preprocessor.valueProperty());
        var roland = Bindings.createBooleanBinding(
                () -> preprocessor.getValue().isRoland(), preprocessor.valueProperty());
        spindle.disableProperty().unbind();
        spindle.disableProperty().bind(noneSelected.or(roland));
        dwell.disableProperty().unbind();
        dwell.disableProperty().bind(noneSelected.or(roland));
        dwellTime.disableProperty().unbind();
        dwellTime.disableProperty().bind(noneSelected.or(dwell.selectedProperty().not()).or(roland));
        var probing = Bindings.createBooleanBinding(
                () -> preprocessor.getValue().requiresProbe(), preprocessor.valueProperty());
        toolChange.disableProperty().bind(automaticTools.or(probing));
        Mach3ProbeFields probe = new Mach3ProbeFields(metric, toolChangeZ, false, null, preprocessor, toolChange);
        sourceCombo.valueProperty().addListener((observable, oldValue, value) -> probe.resetConfirmation());
        Label probeUnits = new Label();
        probeUnits.textProperty().bind(Bindings.createStringBinding(
                () -> "Unidades atuais: " + sourceCombo.getValue().image().units(), sourceCombo.valueProperty()));
        probe.view().getChildren().addFirst(probeUnits);
        toolChangeZ.disableProperty().unbind();
        toolChangeZ.disableProperty().bind(toolChange.selectedProperty().not().or(automaticTools));
        spindleLabel.textProperty().bind(Bindings.createStringBinding(
                () -> preprocessor.getValue() == GCodePreprocessor.REPETIER ? "Potencia PWM (0-255):" : "Spindle RPM:",
                preprocessor.valueProperty()));
        preprocessor.setConverter(new StringConverter<>() {
            @Override public String toString(GCodePreprocessor value) {
                return value == null ? "" : value.label();
            }
            @Override public GCodePreprocessor fromString(String value) {
                return preprocessor.getValue();
            }
        });
        preprocessor.setMinWidth(0);
        preprocessor.setMaxWidth(Double.MAX_VALUE);
        preprocessor.setTooltip(new Tooltip("Port parcial dos perfis Python de fresagem. "
                + "M6 exige suporte do controlador; simule o G-code antes de usar na maquina."));
        TextField rapidFeed = field("0");
        rapidFeed.setId("drill-rapid-feed");
        rapidFeed.setTooltip(new Tooltip("0 = automatico: 1500 mm/min ou equivalente em polegadas. "
                + "Marlin/Repetier usam esse feed nos G0. Roland: 0 = 900 mm/min; faixa 6..900."));
        rapidFeed.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> !preprocessor.getValue().usesRapidFeed(), preprocessor.valueProperty()));
        Label profileHelp = new Label();
        profileHelp.setWrapText(true);
        profileHelp.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> preprocessor.getValue().description(), preprocessor.valueProperty()));
        GridPane common = new GridPane();
        common.setHgap(8);
        common.setVgap(8);
        common.addRow(0, toolChange);
        common.addRow(1, new Label("Tool change Z:"), toolChangeZ);
        common.addRow(2, new Label("End move Z:"), endMoveZ);
        common.addRow(3, new Label("End move X,Y:"), endMoveXY);
        common.addRow(4, new Label("Preprocessor:"), preprocessor);
        common.addRow(5, new Label("Feed rapids:"), rapidFeed);
        common.add(profileHelp, 0, 6, 2, 1);

        Label errorLabel = new Label();
        errorLabel.setId("drill-error");
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        Button generate = new Button("Generate CNC Job");
        generate.setId("drill-generate");
        generate.getStyleClass().add("primary-action");
        generate.setMaxWidth(Double.MAX_VALUE);
        generate.setOnAction(event -> {
            try {
                List<ToolRow> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
                preprocessor.getValue().unitsCode(sourceCombo.getValue().image().units());
                if (selected.isEmpty()) throw new IllegalArgumentException("Selecione ferramentas para furar.");
                if (roland.get() && selected.size() > 1)
                    throw new IllegalArgumentException("Roland exige uma ferramenta por arquivo.");
                if (!toolChange.isSelected() && !preprocessor.getValue().automaticToolSelection()
                        && selected.stream().map(row -> row.diameter).distinct().count() > 1)
                    throw new IllegalArgumentException("Ha diametros diferentes no mesmo G-code. "
                            + "Ative Tool change para pausar entre ferramentas.");
                if (forwardOrder.isSelected()) selected.sort(Comparator.comparingDouble(row -> row.diameter));
                else if (reverseOrder.isSelected())
                    selected.sort(Comparator.comparingDouble((ToolRow row) -> row.diameter).reversed());
                else selected.sort(Comparator.comparingInt(row -> table.getItems().indexOf(row)));
                Map<Integer, DrillGCodeParameters> settings = new LinkedHashMap<>();
                boolean mechanicalChange = toolChange.isSelected() && preprocessor.getValue().supportsManualToolChange();
                List<Integer> orderedIds = new ArrayList<>();
                for (ToolRow row : selected) {
                    preprocessor.getValue().validatePower(roland.get() ? 0 : parseInt(row.spindle, "Potencia/RPM (Tool " + row.id + ")"));
                    settings.put(row.id, row.parameters(mechanicalChange, roland.get()));
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
                double changeZ = mechanicalChange ? parse(toolChangeZ.getText(), "Tool change Z")
                        : settings.get(orderedIds.get(0)).safeZ();
                var options = new GCodeGenerator.DrillJobOptions(
                        mechanicalChange, changeZ, endZ, endX, endY,
                        preprocessor.getValue().usesRapidFeed() ? parse(rapidFeed.getText(), "Feed rapids") : 0,
                        probing.get() ? probe.parameters() : null);
                for (DrillGCodeParameters values : settings.values()) {
                    preprocessor.getValue().validateFeedRates(values.feedRate(), options.rapidFeedRate());
                    if (probing.get()) options.probing().validateTravelZ(values.safeZ());
                }
                errorLabel.setText("");
                onGenerate.accept(new Result(sourceCombo.getValue(), Map.copyOf(settings),
                        List.copyOf(orderedIds), options, preprocessor.getValue(),
                        forwardOrder.isSelected() ? DrillCncSettings.ToolOrder.FORWARD
                                : reverseOrder.isSelected() ? DrillCncSettings.ToolOrder.REVERSE : DrillCncSettings.ToolOrder.NO));
            } catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
        });
        Button reset = new Button("Reset Tool");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setId("drill-reset");
        reset.setOnAction(event -> {
            sourceCombo.setValue(initialSource);
            updateRows.run();
            preprocessor.setValue(GCodePreprocessor.FX_PORTABLE);
            probe.reset(metric);
            noOrder.setSelected(true);
            toolChange.setSelected(false);
            toolChangeZ.setText(metric ? "15.0" : "0.6");
            endMoveZ.setText(metric ? "0.5" : "0.02");
            endMoveXY.setText("None");
            rapidFeed.setText("0");
            feedback.setText("");
            errorLabel.setText("");
        });
        Button close = new Button("Fechar");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(event -> onClose.run());

        Label title = heading("Drilling Tool");
        title.getStyleClass().remove("form-section-title");
        title.getStyleClass().add("tool-title");
        VBox box = new VBox(8, title, heading("EXCELLON:"), sourceCombo,
                new Separator(), table, totals, orderRow, searchDb, new Separator(),
                selectedTitle, perTool, machiningNote, applyAll, feedback, new Separator(),
                heading("Common Parameters"), common, probe.view(), errorLabel, generate, reset, close);
        box.setPadding(new Insets(12));
        Runnable restoreCommon = () -> {
            SourceCandidate source = sourceCombo.getValue();
            boolean sourceMetric = "MM".equalsIgnoreCase(source.image().units());
            DrillCncSettings saved = source.cncSettings();
            // Never leak a previous source's common/probe settings into the new source.
            preprocessor.setValue(saved == null ? GCodePreprocessor.FX_PORTABLE : saved.preprocessor());
            probe.reset(sourceMetric);
            noOrder.setSelected(true);
            toolChange.setSelected(saved == null ? source.drillDefaults().values().stream()
                    .anyMatch(DrillGCodeParameters::pauseForToolChange) : saved.options().pauseForToolChange());
            toolChangeZ.setText(saved == null ? (sourceMetric ? "15.0" : "0.6") : Double.toString(saved.options().toolChangeZ()));
            endMoveZ.setText(saved == null ? (sourceMetric ? "0.5" : "0.02") : Double.toString(saved.options().endMoveZ()));
            endMoveXY.setText(saved == null || saved.options().endMoveX() == null ? "None"
                    : saved.options().endMoveX() + ";" + saved.options().endMoveY());
            rapidFeed.setText(saved == null ? "0" : Double.toString(saved.options().rapidFeedRate()));
            if (saved != null) {
                if (saved.options().probing() != null) probe.restore(saved.options().probing());
                if (saved.toolOrder() == DrillCncSettings.ToolOrder.FORWARD) forwardOrder.setSelected(true);
                else if (saved.toolOrder() == DrillCncSettings.ToolOrder.REVERSE) reverseOrder.setSelected(true);
                table.getSelectionModel().clearSelection();
                for (int i = 0; i < table.getItems().size(); i++)
                    if (saved.selectedToolIds().contains(table.getItems().get(i).id)) table.getSelectionModel().select(i);
                feedback.setText("Perfil, parametros CNC e selecao recuperados; confira antes de gerar. "
                        + (saved.preprocessor().requiresProbe() ? "Confirme novamente os cuidados de sondagem. " : "")
                        + (table.getSelectionModel().getSelectedItems().size() < saved.selectedToolIds().size()
                            ? "Ferramentas salvas nao existem mais na origem; confira a selecao." : ""));
            }
            errorLabel.setText("");
            probe.resetConfirmation();
        };
        sourceCombo.valueProperty().addListener((observable, oldValue, value) -> { if (value != null) restoreCommon.run(); });
        restoreCommon.run();
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
