package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolProfile;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.cutout.CutoutKind;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.CutoutShape;
import org.flatcam.cam.cutout.GapPattern;
import org.locationtech.jts.geom.Geometry;

/**
 * Parameters for the Cutout Tool, as an embeddable panel rather than a modal
 * dialog - appTools/ToolCutOut.py's run() switches the LEFT sidebar's own
 * "Tool" tab the same way ToolIsolation.py/ToolDrilling.py do (see
 * IsolationToolPanel's doc). Two separate "Gerar" buttons (Free-form vs
 * Rectangular) mirror Python's own two separate generation buttons sharing
 * this one form.
 *
 * <p>Bridge, Thin and M-Bites are offered. Manual gaps use cursor-sized squares
 * or filled rectangle/polygon masks for all three modes. The output is editable
 * Geometry, followed by a separate Geometry-to-CNC step as in Python.
 */
final class CutoutToolPanel {

    @FunctionalInterface
    interface AreaSelectionStarter {
        boolean begin(boolean polygon, Consumer<Geometry> onSelected, Runnable onCancelled);
    }

    @FunctionalInterface
    interface CursorSelectionStarter {
        boolean begin(CutoutParameters parameters, List<Geometry> existing,
                      Consumer<Geometry> onSelected, Consumer<String> status);
    }

    enum GapType { BRIDGE, THIN, M_BITES }

    record Result(CutoutParameters cutoutParams, GapType gapType,
                  double biteDiameter, double biteSpacing, List<Geometry> manualGapAreas,
                  GeometryGCodeParameters machining, GeometryGCodeParameters thinMachining, ToolProfile profile,
                  org.flatcam.app.project.CncJobDefaults jobDefaults) {
        Result(CutoutParameters cutoutParams, GapType gapType, double biteDiameter, double biteSpacing, List<Geometry> manualGapAreas,
               GeometryGCodeParameters machining, GeometryGCodeParameters thinMachining, ToolProfile profile) {
            this(cutoutParams, gapType, biteDiameter, biteSpacing, manualGapAreas, machining, thinMachining, profile,
                    org.flatcam.app.project.CncJobDefaults.EMPTY);
        }
    }

    private CutoutToolPanel() {
    }

    /**
     * @param onGenerate called with the parsed parameters when either "Gerar" button is clicked and they're valid.
     * @param onClose    called when "Fechar" is clicked - MainWindow uses it to restore the tool tab's placeholder.
     */
    static Node build(String units, AreaSelectionStarter areaStarter, Runnable cancelArea,
                      Consumer<Result> onGenerate, Runnable onClose) {
        return build(units, areaStarter, cancelArea, List::of, onGenerate, onClose);
    }

    static Node build(String units, AreaSelectionStarter areaStarter, Runnable cancelArea,
                      java.util.function.Supplier<List<org.flatcam.app.project.LegacyToolsDatabase.CutoutTool>> database,
                      Consumer<Result> onGenerate, Runnable onClose) {
        return build(units, areaStarter, cancelArea, database, null, onGenerate, onClose);
    }

    static Node build(String units, AreaSelectionStarter areaStarter, Runnable cancelArea,
                      java.util.function.Supplier<List<org.flatcam.app.project.LegacyToolsDatabase.CutoutTool>> database,
                      CursorSelectionStarter cursorStarter, Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equals(units);

        RadioButton singleRadio = new RadioButton("Single");
        RadioButton panelRadio = new RadioButton("Panel");
        ToolDescriptions.apply(singleRadio, I18n.text("ui.8da2f1a39d30"),
                I18n.text("ui.cdb654060a78"));
        ToolDescriptions.apply(panelRadio, I18n.text("ui.2cb1fcb9f73a"),
                I18n.text("ui.804cc12e4532"));
        ToggleGroup kindGroup = new ToggleGroup();
        singleRadio.setToggleGroup(kindGroup);
        panelRadio.setToggleGroup(kindGroup);
        singleRadio.setSelected(true);

        CheckBox convexShapeCb = new CheckBox("Convex Shape");
        convexShapeCb.setSelected(ToolDefaults.flag("cutout.convex"));
        CheckBox internalCuts = new CheckBox(I18n.text("ui.c0e44b2e3355"));
        internalCuts.setId("cutout-internal-cuts");
        internalCuts.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) convexShapeCb.setSelected(false);
        });
        convexShapeCb.disableProperty().bind(internalCuts.selectedProperty());
        ToolDescriptions.apply(internalCuts, I18n.text("ui.dbb1e2a0ed01"),
                I18n.text("ui.615fb942b47a"));

        TextField toolDiaField = new TextField(ToolDefaults.text("cutout.tooldia", metric));
        toolDiaField.setId("cutout-tool-diameter");
        TextField marginField = new TextField(ToolDefaults.text("cutout.margin", metric));
        marginField.setId("cutout-margin");
        TextField cutZ = new TextField(ToolDefaults.text("cutout.cutz", metric)); cutZ.setId("cutout-cut-z");
        CheckBox multiDepth = new CheckBox("Multi-Depth"); multiDepth.setId("cutout-multi-depth"); multiDepth.setSelected(ToolDefaults.flag("cutout.multidepth"));
        TextField perPass = new TextField(ToolDefaults.text("cutout.depthperpass", metric)); perPass.setId("cutout-depth-per-pass");
        perPass.disableProperty().bind(multiDepth.selectedProperty().not());
        TextField thinZ = new TextField(ToolDefaults.text("cutout.thinz", metric)); thinZ.setId("cutout-thin-z");
        var seed = new java.util.concurrent.atomic.AtomicReference<>(new GeometryGCodeParameters(
                metric ? 3 : 0.12, metric ? 1.7 : 0.067, true, metric ? 0.5 : 0.02, metric ? 120 : 5, 0, false));
        var profile = new java.util.concurrent.atomic.AtomicReference<>(ToolProfile.C1);
        var commonDefaults = new java.util.concurrent.atomic.AtomicReference<>(org.flatcam.app.project.CncJobDefaults.EMPTY);
        cutZ.setTooltip(new Tooltip(I18n.text("ui.b3b282224871")));
        thinZ.setTooltip(new Tooltip(I18n.text("ui.42adc98e530c")));
        TextField gapSizeField = new TextField(ToolDefaults.text("cutout.gapsize", metric));
        gapSizeField.setId("cutout-gap-size");
        ComboBox<GapType> gapTypeCombo = new ComboBox<>();
        gapTypeCombo.getItems().addAll(GapType.values());
        gapTypeCombo.setValue(ToolDefaults.choice("cutout.gaptype", GapType.class));
        gapTypeCombo.setId("cutout-gap-type");
        thinZ.disableProperty().bind(gapTypeCombo.valueProperty().isNotEqualTo(GapType.THIN));
        TextField biteDiameterField = new TextField(ToolDefaults.text("cutout.bitediameter", metric));
        TextField biteSpacingField = new TextField(ToolDefaults.text("cutout.bitespacing", metric));
        biteDiameterField.disableProperty().bind(gapTypeCombo.valueProperty().isNotEqualTo(GapType.M_BITES));
        biteSpacingField.disableProperty().bind(gapTypeCombo.valueProperty().isNotEqualTo(GapType.M_BITES));

        ComboBox<GapPattern> gapPatternCombo = new ComboBox<>();
        gapPatternCombo.getItems().addAll(GapPattern.values());
        gapPatternCombo.setValue(ToolDefaults.choice("cutout.gaps", GapPattern.class));
        gapPatternCombo.setId("cutout-gap-pattern");
        ToolDescriptions.apply(gapPatternCombo, I18n.text("ui.63b0047e887c"),
                I18n.text("ui.ca024fd61793"));
        ToolDescriptions.apply(gapSizeField, I18n.text("ui.114efec780f5"),
                I18n.text("ui.e1f7a2333650"));
        List<Geometry> manualAreas = new ArrayList<>();
        List<Geometry> cursorAreas = new ArrayList<>();
        var cursorShape = new java.util.concurrent.atomic.AtomicReference<CutoutShape>();
        Label manualStatus = new Label(I18n.text("ui.47a3159fcd80"));
        manualStatus.setId("cutout-manual-status");
        manualStatus.setWrapText(true);
        Button cursorFreeform = new Button(I18n.text("ui.169ba4efad11"));
        cursorFreeform.setId("cutout-cursor-freeform");
        Button cursorRectangular = new Button(I18n.text("ui.6e12c6d0fb1b"));
        cursorRectangular.setId("cutout-cursor-rectangular");
        cursorFreeform.setDisable(cursorStarter == null);
        cursorRectangular.disableProperty().bind(internalCuts.selectedProperty().or(
                new javafx.beans.property.SimpleBooleanProperty(cursorStarter == null)));
        for (Button button : List.of(cursorFreeform, cursorRectangular)) {
            button.setMaxWidth(Double.MAX_VALUE);
            ToolDescriptions.apply(button, I18n.text("ui.6996a24e5c82"),
                    I18n.text("ui.b9de2c3acb5e"));
            button.setOnAction(event -> {
                try {
                    CutoutShape shape = button == cursorFreeform ? CutoutShape.FREEFORM : CutoutShape.RECTANGULAR;
                    if (!cursorAreas.isEmpty() && cursorShape.get() != shape)
                        throw new IllegalArgumentException(I18n.text("ui.85cd5e2a7946"));
                    var parameters = new CutoutParameters(parseDouble(toolDiaField.getText(), "Tool Dia"),
                            parseDouble(marginField.getText(), "Margin"), convexShapeCb.isSelected(),
                            singleRadio.isSelected() ? CutoutKind.SINGLE : CutoutKind.PANEL, shape,
                            parseDouble(gapSizeField.getText(), "Gap size"), GapPattern.NONE, internalCuts.isSelected());
                    if (parameters.gapSize() <= 0) throw new IllegalArgumentException(I18n.text("ui.5881ea269543"));
                    cancelArea.run();
                    cursorStarter.begin(parameters, combinedAreas(manualAreas, cursorAreas), area -> {
                        cursorShape.set(shape); cursorAreas.add(area);
                    }, manualStatus::setText);
                } catch (RuntimeException invalid) { manualStatus.setText(invalid.getMessage()); }
            });
        }
        Button rectangleGapButton = new Button(I18n.text("ui.78e35a79a363"));
        Button polygonGapButton = new Button(I18n.text("ui.4e49b9134503"));
        for (Button button : new Button[]{rectangleGapButton, polygonGapButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> {
                cancelArea.run();
                boolean polygon = button == polygonGapButton;
                if (areaStarter.begin(polygon, area -> {
                    manualAreas.add(area);
                    manualStatus.setText((manualAreas.size() + cursorAreas.size()) + I18n.text("ui.989906f4e351"));
                }, () -> manualStatus.setText(I18n.text("ui.8919d048711a") + (manualAreas.size() + cursorAreas.size()) + I18n.text("ui.5cc7ddc54040")))) {
                    manualStatus.setText(polygon
                            ? I18n.text("ui.26f797b352ee")
                            : I18n.text("ui.5c4de2e250f6"));
                } else {
                    manualStatus.setText(I18n.text("ui.5041471c02a3"));
                }
            });
        }
        Button clearManualButton = new Button(I18n.text("ui.c4edfc7d11a9"));
        clearManualButton.setId("cutout-clear-gaps");
        clearManualButton.setMaxWidth(Double.MAX_VALUE);
        clearManualButton.setOnAction(event -> {
            cancelArea.run();
            manualAreas.clear();
            cursorAreas.clear(); cursorShape.set(null);
            manualStatus.setText(I18n.text("ui.47a3159fcd80"));
        });

        Runnable invalidateCursor = () -> {
            cancelArea.run();
            if (!cursorAreas.isEmpty()) {
                cursorAreas.clear(); cursorShape.set(null);
                manualStatus.setText(I18n.text("ui.c7d289c355c5") + manualAreas.size() + I18n.text("ui.e5499501abc9"));
            }
        };
        for (TextField field : List.of(toolDiaField, marginField, gapSizeField))
            field.textProperty().addListener((obs, old, value) -> invalidateCursor.run());
        convexShapeCb.selectedProperty().addListener((obs, old, value) -> invalidateCursor.run());
        internalCuts.selectedProperty().addListener((obs, old, value) -> invalidateCursor.run());
        kindGroup.selectedToggleProperty().addListener((obs, old, value) -> invalidateCursor.run());

        for (TextField field : new TextField[]{toolDiaField, marginField, gapSizeField,
                biteDiameterField, biteSpacingField}) {
            field.setPrefColumnCount(7);
            field.setMinWidth(0);
        }
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true); errorLabel.setId("cutout-error");
        VBox picker = DatabaseToolPicker.build("cutout-db", database, selected -> {
            CutoutParameters p = selected.parameters();
            toolDiaField.setText(Double.toString(p.toolDiameter())); marginField.setText(Double.toString(p.margin()));
            gapSizeField.setText(Double.toString(p.gapSize())); convexShapeCb.setSelected(p.convexShape());
            gapPatternCombo.setValue(p.gapPattern());
            gapTypeCombo.setValue(switch (selected.gapType()) { case "bt" -> GapType.THIN; case "mb" -> GapType.M_BITES; default -> GapType.BRIDGE; });
            biteDiameterField.setText(Double.toString(selected.biteDiameter()));
            biteSpacingField.setText(Double.toString(selected.biteSpacing()));
            seed.set(selected.machining()); profile.set(selected.profile());
            commonDefaults.set(selected.jobDefaults());
            cutZ.setText(Double.toString(-selected.machining().cutDepth()));
            multiDepth.setSelected(selected.machining().multiDepth());
            perPass.setText(Double.toString(selected.machining().depthPerPass()));
            thinZ.setText(Double.toString(-selected.thinDepth()));
        }, errorLabel);
        Label transferNote = new Label(I18n.text("ui.2ce66d821bed"));
        transferNote.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Kind:"), new HBox(10, singleRadio, panelRadio));
        grid.addRow(1, new Label("Convex Shape:"), convexShapeCb);
        grid.addRow(2, new Label("Tool Dia:"), toolDiaField);
        grid.addRow(3, new Label("Margin:"), marginField);
        grid.addRow(4, new Label("Gap size:"), gapSizeField);
        grid.addRow(5, new Label("Gaps:"), gapPatternCombo);
        grid.addRow(6, new Label(I18n.text("ui.5a96ffdb7ee4")), gapTypeCombo);
        grid.addRow(7, new Label("M-Bites dia:"), biteDiameterField);
        grid.addRow(8, new Label("M-Bites spacing:"), biteSpacingField);
        grid.addRow(9, new Label("Cut Z:"), cutZ);
        grid.addRow(10, multiDepth, perPass);
        grid.addRow(11, new Label("Thin Depth:"), thinZ);
        grid.add(internalCuts, 0, 12, 2, 1);
        for (TextField field : List.of(cutZ, perPass, thinZ)) { field.setMinWidth(0); field.setPrefColumnCount(7); }

        Button freeformButton = new Button(I18n.text("ui.c77b1cc8fa6a"));
        freeformButton.setId("cutout-generate-freeform");
        freeformButton.getStyleClass().add("primary-action");
        freeformButton.setMaxWidth(Double.MAX_VALUE);
        Button rectangularButton = new Button(I18n.text("ui.e74bbee0a833"));
        rectangularButton.setId("cutout-generate-rectangular");
        rectangularButton.disableProperty().bind(internalCuts.selectedProperty());
        rectangularButton.getStyleClass().add("primary-action");
        rectangularButton.setMaxWidth(Double.MAX_VALUE);
        ToolDescriptions.apply(freeformButton, I18n.text("ui.f82c600dc0d0"),
                I18n.text("ui.98574eca7142"));
        ToolDescriptions.apply(rectangularButton, I18n.text("ui.255eadb704d9"),
                I18n.text("ui.b5bfd49aa0d4"));
        Button closeButton = new Button(I18n.text("ui.0f2bd88ef0ac"));
        closeButton.setMaxWidth(Double.MAX_VALUE);

        freeformButton.setOnAction(e -> tryGenerate(CutoutShape.FREEFORM, singleRadio, convexShapeCb, internalCuts, toolDiaField,
                marginField, gapSizeField, gapPatternCombo, gapTypeCombo, biteDiameterField,
                biteSpacingField, combinedAreas(manualAreas, cursorAreas), cutZ, multiDepth, perPass, thinZ, seed.get(), profile.get(), commonDefaults.get(), errorLabel,
                result -> generateWithCursorShape(result, cursorAreas, cursorShape.get(), onGenerate)));
        rectangularButton.setOnAction(e -> tryGenerate(CutoutShape.RECTANGULAR, singleRadio, convexShapeCb, internalCuts, toolDiaField,
                marginField, gapSizeField, gapPatternCombo, gapTypeCombo, biteDiameterField,
                biteSpacingField, combinedAreas(manualAreas, cursorAreas), cutZ, multiDepth, perPass, thinZ, seed.get(), profile.get(), commonDefaults.get(), errorLabel,
                result -> generateWithCursorShape(result, cursorAreas, cursorShape.get(), onGenerate)));
        closeButton.setOnAction(e -> onClose.run());

        Label workflowNote = new Label(I18n.text("ui.ee8fd6aaa02c"));
        workflowNote.setWrapText(true);
        Label title = new Label("Cutout Tool");
        title.getStyleClass().add("tool-title");
        VBox box = new VBox(10,
                title, new Label(I18n.text("ui.58d9bc7dde89") + units),
                picker, transferNote, grid, cursorFreeform, cursorRectangular, rectangleGapButton, polygonGapButton, clearManualButton, manualStatus, workflowNote,
                errorLabel, freeformButton, rectangularButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static List<Geometry> combinedAreas(List<Geometry> drawn, List<Geometry> cursor) {
        var result = new ArrayList<>(drawn); result.addAll(cursor); return List.copyOf(result);
    }

    private static void generateWithCursorShape(Result result, List<Geometry> cursorAreas,
                                                 CutoutShape shape, Consumer<Result> generate) {
        if (!cursorAreas.isEmpty() && result.cutoutParams().shape() != shape)
            throw new IllegalArgumentException(I18n.text("ui.9d8fe933dbf6") + shape + I18n.text("ui.8ba2a5f8348d"));
        generate.accept(result);
    }

    private static void tryGenerate(CutoutShape shape, RadioButton singleRadio, CheckBox convexShapeCb, CheckBox internalCuts,
            TextField toolDiaField, TextField marginField, TextField gapSizeField,
            ComboBox<GapPattern> gapPatternCombo, ComboBox<GapType> gapTypeCombo,
            TextField biteDiameterField, TextField biteSpacingField,
            List<Geometry> manualAreas, TextField cutZ, CheckBox multiDepth, TextField perPass, TextField thinZ,
            GeometryGCodeParameters seed, ToolProfile profile, org.flatcam.app.project.CncJobDefaults jobDefaults, Label errorLabel,
            Consumer<Result> onGenerate) {
        try {
            double toolDia = parseDouble(toolDiaField.getText(), "Tool Dia");
            double margin = parseDouble(marginField.getText(), "Margin");
            double gapSize = parseDouble(gapSizeField.getText(), "Gap size");
            CutoutKind kind = singleRadio.isSelected() ? CutoutKind.SINGLE : CutoutKind.PANEL;
            CutoutParameters cutoutParams = new CutoutParameters(toolDia, margin, convexShapeCb.isSelected(),
                    kind, shape, gapSize, gapPatternCombo.getValue(), internalCuts.isSelected());
            boolean bites = gapTypeCombo.getValue() == GapType.M_BITES;
            double biteDiameter = bites ? parseDouble(biteDiameterField.getText(), "M-Bites dia") : 0;
            double biteSpacing = bites ? parseDouble(biteSpacingField.getText(), "M-Bites spacing") : 0;
            if (bites && (biteDiameter <= 0 || biteSpacing < 0
                    || manualAreas.isEmpty() && (gapSize <= 0 || gapPatternCombo.getValue() == GapPattern.NONE))) {
                throw new IllegalArgumentException(I18n.text("ui.5d704b5da566"));
            }

            errorLabel.setText("");
            double cut = parseDouble(cutZ.getText(), "Cut Z");
            if (!Double.isFinite(cut) || cut >= 0) throw new IllegalArgumentException(I18n.text("ui.ef3a99fccd3f"));
            var machining = seed.withCutting(-cut, multiDepth.isSelected(),
                    multiDepth.isSelected() ? parseDouble(perPass.getText(), "Depth per pass") : seed.depthPerPass());
            GeometryGCodeParameters thin = null;
            if (gapTypeCombo.getValue() == GapType.THIN) {
                double thinCut = parseDouble(thinZ.getText(), "Thin Depth");
                if (!Double.isFinite(thinCut) || thinCut >= 0 || thinCut <= cut)
                    throw new IllegalArgumentException(I18n.text("ui.60538e9ad822"));
                thin = machining.withCutting(-thinCut, machining.multiDepth(), machining.depthPerPass());
            }
            onGenerate.accept(new Result(cutoutParams, gapTypeCombo.getValue(), biteDiameter,
                    biteSpacing, List.copyOf(manualAreas), machining, thin, profile, jobDefaults));
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static double parseDouble(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + I18n.text("ui.a8a35e0360a2"));
        }
    }
}
