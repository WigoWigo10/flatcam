package org.flatcam.fx;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.flatcam.app.project.LegacyToolsDatabase;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import javafx.collections.FXCollections;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.app.project.GeometryCncSettings;
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
                  Map<Integer, VTipSettings> vTools, GCodePreprocessor preprocessor,
                  Map<Integer, GeometryGCodeParameters> parametersByTool) {
    }

    private GeometryCncToolPanel() {
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> tools,
                      Consumer<Result> onGenerate, Runnable onClose) {
        return build(units, combinedGeometry, tools, null, onGenerate, onClose);
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> tools,
                      GeometryGCodeParameters defaults, Consumer<Result> onGenerate, Runnable onClose) {
        return build(units, combinedGeometry, tools, defaults, null, onGenerate, onClose);
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> tools,
                      GeometryGCodeParameters defaults, GeometryCncSettings settings,
                      Consumer<Result> onGenerate, Runnable onClose) {
        return build(units, combinedGeometry, tools, defaults, settings, List::of, onGenerate, onClose);
    }

    static Node build(String units, Geometry combinedGeometry, List<ToolGeometry> sourceTools,
                      GeometryGCodeParameters defaults, GeometryCncSettings settings,
                      Supplier<List<LegacyToolsDatabase.MillingTool>> database,
                      Consumer<Result> onGenerate, Runnable onClose) {
        List<ToolGeometry> tools = new java.util.ArrayList<>(sourceTools);
        boolean metric = "MM".equalsIgnoreCase(units);
        boolean multiTool = !tools.isEmpty();
        var singleProfile = new javafx.beans.property.SimpleObjectProperty<>(ToolProfile.C1);

        TextField toolDiaField = new TextField(format(metric ? 0.8 : 0.031));
        toolDiaField.setId("cnc-tool-dia");
        if (!multiTool && settings != null && settings.singleToolDiameter() != null)
            toolDiaField.setText(Double.toString(settings.singleToolDiameter()));
        TableView<ToolGeometry> toolTable = new TableView<>();
        toolTable.setId("cnc-tools");
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
                : Double.toString(defaults.safeZ()));
        TextField cutDepthField = new TextField(defaults == null ? (metric ? "0.1" : "0.004")
                : Double.toString(defaults.cutDepth()));
        CheckBox multiDepthCb = new CheckBox("Multi-Depth");
        multiDepthCb.setSelected(defaults != null && defaults.multiDepth());
        TextField depthPerPassField = new TextField(defaults == null ? (metric ? "0.05" : "0.002")
                : Double.toString(defaults.depthPerPass()));
        depthPerPassField.disableProperty().bind(multiDepthCb.selectedProperty().not());
        TextField feedField = new TextField(defaults == null ? (metric ? "300" : "12")
                : Double.toString(defaults.feedRate()));
        TextField spindleField = new TextField(defaults == null ? "10000"
                : Integer.toString(defaults.spindleSpeedRpm()));
        TextField rapidFeedField = new TextField(defaults == null ? "0" : Double.toString(defaults.rapidFeedRate()));
        TextField feedZField = new TextField(defaults == null ? feedField.getText() : Double.toString(defaults.feedRateZ()));
        feedZField.setId("cnc-feed-z");
        CheckBox dwellCb = new CheckBox("Dwell");
        dwellCb.setSelected(defaults != null && defaults.dwell());
        dwellCb.setId("cnc-dwell");
        TextField dwellField = new TextField(defaults == null ? "1" : Double.toString(defaults.dwellSeconds()));
        dwellField.setId("cnc-dwell-time");
        CheckBox extraCb = new CheckBox("Extra Cut (caminhos fechados)");
        extraCb.setSelected(defaults != null && defaults.extraCut());
        extraCb.setId("cnc-extra-cut");
        TextField extraField = new TextField(defaults == null ? "0.1" : Double.toString(defaults.extraCutLength()));
        extraField.setId("cnc-extra-length");
        rapidFeedField.setTooltip(new Tooltip("0 = automatico: 1500 mm/min ou equivalente em polegadas. "
                + "Marlin/Repetier usam esse feed nos G0. Roland: 0 = 900 mm/min; faixa 6..900."));
        for (TextField field : List.of(toolDiaField, safeZField, cutDepthField,
                depthPerPassField, feedField, spindleField, rapidFeedField)) {
            field.setPrefColumnCount(7);
            field.setMinWidth(0);
        }
        CheckBox pauseCheck = new CheckBox("Troca de ferramenta (conforme perfil)");
        pauseCheck.setSelected(defaults == null ? tools.size() > 1 : defaults.pauseForToolChange());
        ComboBox<GCodePreprocessor> preprocessor = new ComboBox<>(
                FXCollections.observableArrayList(GCodePreprocessor.geometryProfiles()));
        preprocessor.setId("cnc-preprocessor");
        spindleField.setId("cnc-power");
        rapidFeedField.setId("cnc-rapid-feed");
        cutDepthField.setId("cnc-cut-depth");
        safeZField.setId("cnc-safe-z");
        feedField.setId("cnc-feed");
        multiDepthCb.setId("cnc-multi-depth");
        pauseCheck.setId("cnc-tool-change");
        preprocessor.setValue(GCodePreprocessor.FX_PORTABLE);
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
        preprocessor.setTooltip(new Tooltip("Perfis de fresagem, laser e plotter. Simule e faca um teste a seco antes de usar."));
        var laser = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> preprocessor.getValue().isLaser(), preprocessor.valueProperty());
        var plotter = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> preprocessor.getValue().isPlotter(), preprocessor.valueProperty());
        var roland = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> preprocessor.getValue().isRoland(), preprocessor.valueProperty());
        var noCutZ = laser.or(plotter);
        feedZField.disableProperty().bind(noCutZ);
        extraCb.disableProperty().bind(noCutZ);
        extraField.disableProperty().bind(extraCb.selectedProperty().not().or(noCutZ));
        dwellCb.disableProperty().bind(noCutZ.or(roland));
        dwellField.disableProperty().bind(dwellCb.selectedProperty().not().or(noCutZ).or(roland));
        var rapidFeed = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> preprocessor.getValue().usesRapidFeed(), preprocessor.valueProperty());
        cutDepthField.disableProperty().bind(noCutZ);
        multiDepthCb.disableProperty().bind(noCutZ);
        safeZField.disableProperty().bind(plotter);
        feedField.disableProperty().bind(plotter);
        spindleField.disableProperty().bind(plotter.or(roland));
        depthPerPassField.disableProperty().unbind();
        depthPerPassField.disableProperty().bind(multiDepthCb.selectedProperty().not().or(noCutZ));
        pauseCheck.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> !preprocessor.getValue().supportsManualToolChange() || preprocessor.getValue().requiresProbe(), preprocessor.valueProperty()));
        TextField probeChangeZ = new TextField(metric ? "15" : "0.6");
        probeChangeZ.setMinWidth(0);
        probeChangeZ.setPrefColumnCount(7);
        Mach3ProbeFields probe = new Mach3ProbeFields(metric, probeChangeZ, true,
                defaults == null ? null : defaults.probing(), preprocessor, pauseCheck);
        rapidFeedField.disableProperty().bind(rapidFeed.not());
        Label profileHelp = new Label();
        profileHelp.setWrapText(true);
        profileHelp.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> preprocessor.getValue().description(), preprocessor.valueProperty()));
        Label zLabel = new Label();
        zLabel.textProperty().bind(javafx.beans.binding.Bindings.when(laser)
                .then("Focus/End Z:").otherwise("Travel Z:"));
        Label powerLabel = new Label();
        powerLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> laser.get() || preprocessor.getValue() == GCodePreprocessor.REPETIER
                        ? "Potencia S/PWM:" : "Spindle RPM:", preprocessor.valueProperty()));
        Label diameterLabel = new Label();
        diameterLabel.textProperty().bind(javafx.beans.binding.Bindings.when(laser)
                .then("Spot Dia:").otherwise("Tool Dia:"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        if (!multiTool) {
            grid.addRow(row++, diameterLabel, toolDiaField);
        }
        grid.addRow(row++, zLabel, safeZField);
        grid.addRow(row++, new Label("Cut Z (nao V):"), cutDepthField);
        grid.addRow(row++, multiDepthCb, depthPerPassField);
        grid.addRow(row++, new Label("Feed rate:"), feedField);
        grid.addRow(row++, new Label("Feedrate Z:"), feedZField);
        grid.addRow(row++, dwellCb, dwellField);
        grid.addRow(row++, extraCb, extraField);
        grid.addRow(row++, new Label("Feed rapids:"), rapidFeedField);
        grid.addRow(row++, powerLabel, spindleField);
        grid.add(pauseCheck, 0, row++, 2, 1);
        grid.addRow(row, new Label("Preprocessor:"), preprocessor);

        Label errorLabel = new Label();
        errorLabel.setId("cnc-error");
        errorLabel.getStyleClass().add("form-error-label");
        // Raw snapshots preserve invalid edits when switching rows; all rows validate before generation.
        List<TextField> machiningFields = List.of(safeZField, cutDepthField, depthPerPassField,
                feedField, spindleField, feedZField, dwellField, extraField);
        List<CheckBox> machiningChecks = List.of(multiDepthCb, dwellCb, extraCb);
        Supplier<String[]> capture = () -> java.util.stream.Stream.concat(machiningFields.stream().map(TextField::getText),
                machiningChecks.stream().map(c -> Boolean.toString(c.isSelected()))).toArray(String[]::new);
        Consumer<String[]> restore = values -> {
            for (int i = 0; i < machiningFields.size(); i++) machiningFields.get(i).setText(values[i]);
            for (int i = 0; i < machiningChecks.size(); i++) machiningChecks.get(i).setSelected(Boolean.parseBoolean(values[8 + i]));
        };
        Map<Integer, String[]> snapshots = new LinkedHashMap<>();
        String[] initialValues = capture.get();
        if (multiTool) for (int i = 0; i < tools.size(); i++) {
            GeometryGCodeParameters p = settings == null ? null : settings.parametersByTool().get(i);
            snapshots.put(i, p == null ? initialValues.clone() : new String[]{Double.toString(p.safeZ()),
                    Double.toString(p.cutDepth()), Double.toString(p.depthPerPass()), Double.toString(p.feedRate()),
                    Integer.toString(p.spindleSpeedRpm()), Double.toString(p.feedRateZ()), Double.toString(p.dwellSeconds()),
                    Double.toString(p.extraCutLength()), Boolean.toString(p.multiDepth()), Boolean.toString(p.dwell()), Boolean.toString(p.extraCut())});
        }
        int[] editing = {0};
        if (multiTool) {
            restore.accept(snapshots.get(0));
            toolTable.getSelectionModel().select(0);
            toolTable.getSelectionModel().selectedIndexProperty().addListener((obs, old, value) -> {
                if (value.intValue() < 0) return;
                snapshots.put(editing[0], capture.get());
                editing[0] = value.intValue(); restore.accept(snapshots.get(editing[0]));
            });
        }
        Button applyAll = new Button("Aplicar parametros a todas as ferramentas");
        applyAll.setId("cnc-apply-all");
        applyAll.setMaxWidth(Double.MAX_VALUE);
        applyAll.setOnAction(event -> { for (int i = 0; i < tools.size(); i++) snapshots.put(i, capture.get()); });
        Map<Integer, TextField[]> vFields = new LinkedHashMap<>();
        VBox vSettings = new VBox(8);
        vSettings.disableProperty().bind(noCutZ);
        for (int i = 0; i < Math.max(1, tools.size()); i++) {
            ToolGeometry tool = multiTool ? tools.get(i) : new ToolGeometry(0.8, combinedGeometry);
            TextField tipDia = new TextField(metric ? "0.1" : "0.004");
            TextField tipAngle = new TextField("30");
            tipDia.setId("cnc-v-tip-dia-" + i);
            tipAngle.setId("cnc-v-tip-angle-" + i);
            VTipSettings savedTip = settings == null ? null : settings.vTools().get(i);
            if (savedTip != null) {
                tipDia.setText(Double.toString(savedTip.tipDiameter()));
                tipAngle.setText(Double.toString(savedTip.angleDegrees()));
            }
            tipDia.setPrefColumnCount(6);
            tipAngle.setPrefColumnCount(6);
            Label calculated = new Label();
            calculated.setWrapText(true);
            Runnable updateDepth = () -> {
                try {
                    double depth = new VTipSettings(parse(tipDia, "V-Tip Dia"),
                            parse(tipAngle, "V-Tip Angle")).cutDepth(multiTool ? tool.toolDiameter() : parse(toolDiaField, "Tool Dia"));
                    calculated.setText("Cut Z calculado: -" + format(depth) + " " + units);
                } catch (RuntimeException error) {
                    calculated.setText("Cut Z: " + error.getMessage());
                }
            };
            tipDia.textProperty().addListener((obs, oldValue, value) -> updateDepth.run());
            tipAngle.textProperty().addListener((obs, oldValue, value) -> updateDepth.run());
            toolDiaField.textProperty().addListener((obs, oldValue, value) -> updateDepth.run());
            updateDepth.run();
            GridPane vGrid = new GridPane();
            vGrid.setHgap(8);
            vGrid.setVgap(6);
            vGrid.addRow(0, new Label("V-Tip Dia:"), tipDia);
            vGrid.addRow(1, new Label("V-Tip Angle (graus):"), tipAngle);
            VBox toolTip = new VBox(6, new Label("Ferramenta " + (i + 1) + " - V-Tip"), vGrid, calculated);
            final int index = i;
            toolTip.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> multiTool ? tools.get(index).toolProfile() == ToolProfile.V : singleProfile.get() == ToolProfile.V,
                    singleProfile, toolTable.itemsProperty()));
            toolTip.managedProperty().bind(toolTip.visibleProperty());
            vSettings.getChildren().add(toolTip);
            vFields.put(i, new TextField[]{tipDia, tipAngle});
        }
        Button generateButton = new Button("Gerar CNC Job...");
        generateButton.setId("cnc-generate");
        generateButton.getStyleClass().add("primary-action");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                double safeZ = plotter.get() ? 1 : parse(safeZField, "Travel Z");
                double cutDepth = noCutZ.get() ? 1 : parse(cutDepthField, "Cut Z");
                boolean multiDepth = !noCutZ.get() && multiDepthCb.isSelected();
                double depthPerPass = multiDepth ? parse(depthPerPassField, "Depth per pass") : 1;
                double feed = plotter.get() ? 1 : parse(feedField, "Feed rate");
                double power = plotter.get() || roland.get() ? 0 : parse(spindleField, powerLabel.getText());
                if (!Double.isFinite(power) || power < 0 || power > Integer.MAX_VALUE || power != Math.rint(power))
                    throw new IllegalArgumentException("Potencia/RPM deve ser um inteiro nao negativo.");
                int spindle = (int) power;
                preprocessor.getValue().validatePower(spindle);
                preprocessor.getValue().unitsCode(units);
                GeometryGCodeParameters params = new GeometryGCodeParameters(
                        safeZ, cutDepth, multiDepth, depthPerPass, feed, spindle,
                        preprocessor.getValue().supportsManualToolChange() && pauseCheck.isSelected(),
                        rapidFeed.get() ? parse(rapidFeedField, "Feed rapids") : 0,
                        preprocessor.getValue().requiresProbe() ? probe.parameters() : null,
                        noCutZ.get() ? feed : parse(feedZField, "Feedrate Z"),
                        !noCutZ.get() && !roland.get() && dwellCb.isSelected(),
                        dwellCb.isSelected() && !dwellField.isDisabled() ? parse(dwellField, "Dwell time") : 0,
                        !noCutZ.get() && extraCb.isSelected(), extraCb.isSelected() && !noCutZ.get() ? parse(extraField, "Extra Cut Length") : 0);
                preprocessor.getValue().validateFeedRates(params.feedRate(), params.rapidFeedRate());
                List<ToolGeometry> resultTools = multiTool
                        ? tools
                        : List.of(new ToolGeometry(parse(toolDiaField, "Tool Dia"), combinedGeometry, singleProfile.get()));
                if (roland.get() && resultTools.stream().filter(tool -> !tool.geometry().isEmpty()).count() > 1)
                    throw new IllegalArgumentException("Roland exige uma ferramenta por arquivo.");
                Map<Integer, VTipSettings> vTools = new LinkedHashMap<>();
                for (var entry : vFields.entrySet()) {
                    if (noCutZ.get()) break;
                    if (resultTools.get(entry.getKey()).toolProfile() != ToolProfile.V) continue;
                    TextField[] fields = entry.getValue();
                    VTipSettings tipSettings = new VTipSettings(parse(fields[0], "V-Tip Dia"),
                            parse(fields[1], "V-Tip Angle"));
                    tipSettings.cutDepth(resultTools.get(entry.getKey()).toolDiameter());
                    vTools.put(entry.getKey(), tipSettings);
                }
                errorLabel.setText("");
                Map<Integer, GeometryGCodeParameters> byTool = new LinkedHashMap<>();
                if (multiTool && !noCutZ.get()) {
                    snapshots.put(editing[0], capture.get());
                    for (int i = 0; i < tools.size(); i++) {
                        String[] s = snapshots.get(i);
                        GeometryGCodeParameters p = new GeometryGCodeParameters(
                                parseText(s[0], "Tool " + (i+1) + " Travel Z"), parseText(s[1], "Cut Z"), Boolean.parseBoolean(s[8]),
                                Boolean.parseBoolean(s[8]) ? parseText(s[2], "Depth per pass") : 1,
                                parseText(s[3], "Feedrate XY"), integerPower(s[4]), params.pauseForToolChange(),
                                params.rapidFeedRate(), params.probing(), parseText(s[5], "Feedrate Z"),
                                !roland.get() && Boolean.parseBoolean(s[9]), Boolean.parseBoolean(s[9]) && !roland.get() ? parseText(s[6], "Dwell") : 0,
                                Boolean.parseBoolean(s[10]), Boolean.parseBoolean(s[10]) ? parseText(s[7], "Extra Cut Length") : 0);
                        preprocessor.getValue().validatePower(p.spindleSpeedRpm());
                        preprocessor.getValue().validateFeedRates(p.feedRate(), params.rapidFeedRate());
                        byTool.put(i, p);
                    }
                    if (byTool.values().stream().allMatch(params::equals)) byTool.clear();
                    if (!byTool.isEmpty() && (roland.get() || preprocessor.getValue().requiresProbe()))
                        throw new IllegalArgumentException("Este perfil exige parametros comuns; aplique a todas as ferramentas.");
                }
                onGenerate.accept(new Result(resultTools, params, Map.copyOf(vTools),
                        preprocessor.getValue(), Map.copyOf(byTool)));
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
            box.getChildren().addAll(new Label("Ferramentas/caminhos associados:"), toolTable);
        }
        box.getChildren().add(DatabaseToolPicker.build("cnc-db", database, selected -> {
            int index = multiTool ? toolTable.getSelectionModel().getSelectedIndex() : 0;
            if (multiTool && index < 0) throw new IllegalArgumentException("Selecione a ferramenta na tabela.");
            if (multiTool && Math.abs(tools.get(index).toolDiameter() - selected.diameter()) > 1e-6)
                throw new IllegalArgumentException("Diametro da base diferente do caminho. Regenere a Geometry para mudar a largura.");
            if (!multiTool) { toolDiaField.setText(Double.toString(selected.diameter())); singleProfile.set(selected.profile()); }
            else {
                tools.set(index, new ToolGeometry(selected.diameter(), tools.get(index).geometry(), selected.profile()));
                toolTable.setItems(FXCollections.observableArrayList(tools));
                toolTable.getSelectionModel().select(index);
            }
            GeometryGCodeParameters p = selected.parameters();
            safeZField.setText(Double.toString(p.safeZ())); cutDepthField.setText(Double.toString(p.cutDepth()));
            multiDepthCb.setSelected(p.multiDepth()); depthPerPassField.setText(Double.toString(p.depthPerPass()));
            feedField.setText(Double.toString(p.feedRate())); spindleField.setText(Integer.toString(p.spindleSpeedRpm()));
            feedZField.setText(Double.toString(p.feedRateZ())); dwellCb.setSelected(p.dwell());
            dwellField.setText(Double.toString(p.dwellSeconds())); extraCb.setSelected(p.extraCut());
            extraField.setText(Double.toString(p.extraCutLength()));
            rapidFeedField.setText(Double.toString(p.rapidFeedRate()));
            if (selected.tip() != null) {
                vFields.get(index)[0].setText(Double.toString(selected.tip().tipDiameter()));
                vFields.get(index)[1].setText(Double.toString(selected.tip().angleDegrees()));
            }
        }, errorLabel));
        box.getChildren().add(grid);
        if (multiTool) {
            Label modeHelp = new Label();
            modeHelp.setWrapText(true);
            modeHelp.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() ->
                    noCutZ.get() || roland.get() || preprocessor.getValue().requiresProbe()
                            ? "Este perfil usa os parametros comuns da ferramenta exibida; configuracoes individuais nao sao aplicadas."
                            : "Selecione uma linha para editar seus parametros. Feed rapids, troca e preprocessor sao comuns ao trabalho.",
                    preprocessor.valueProperty()));
            box.getChildren().addAll(modeHelp, applyAll);
        }
        if (!vFields.isEmpty()) box.getChildren().add(vSettings);
        box.getChildren().addAll(probe.view(), profileHelp, errorLabel, generateButton, closeButton);
        box.setPadding(new Insets(12));
        if (settings != null) preprocessor.setValue(settings.preprocessor());
        return box;
    }

    private static double parse(TextField field, String name) {
        return parseText(field.getText(), name);
    }

    private static int integerPower(String text) {
        double value = parseText(text, "Spindle RPM");
        if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE || value != Math.rint(value))
            throw new IllegalArgumentException("Potencia/RPM deve ser inteiro nao negativo.");
        return (int) value;
    }

    private static double parseText(String text, String name) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": numero invalido");
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
