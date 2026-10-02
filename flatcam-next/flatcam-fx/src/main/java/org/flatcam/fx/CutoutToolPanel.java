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
 * <p>Bridge, Thin and M-Bites are offered. Manual gaps are drawn
 * as filled rectangle/polygon masks for all three modes. The output is editable
 * Geometry, followed by a separate Geometry-to-CNC step as in Python.
 */
final class CutoutToolPanel {

    @FunctionalInterface
    interface AreaSelectionStarter {
        boolean begin(boolean polygon, Consumer<Geometry> onSelected, Runnable onCancelled);
    }

    enum GapType { BRIDGE, THIN, M_BITES }

    record Result(CutoutParameters cutoutParams, GapType gapType,
                  double biteDiameter, double biteSpacing, List<Geometry> manualGapAreas,
                  GeometryGCodeParameters machining, GeometryGCodeParameters thinMachining, ToolProfile profile) {
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
        boolean metric = "MM".equals(units);

        RadioButton singleRadio = new RadioButton("Single");
        RadioButton panelRadio = new RadioButton("Panel");
        ToggleGroup kindGroup = new ToggleGroup();
        singleRadio.setToggleGroup(kindGroup);
        panelRadio.setToggleGroup(kindGroup);
        singleRadio.setSelected(true);

        CheckBox convexShapeCb = new CheckBox("Convex Shape");

        TextField toolDiaField = new TextField(metric ? "2.4" : "0.094");
        TextField marginField = new TextField(metric ? "0.1" : "0.004");
        TextField cutZ = new TextField(metric ? "-1.7" : "-0.067"); cutZ.setId("cutout-cut-z");
        CheckBox multiDepth = new CheckBox("Multi-Depth"); multiDepth.setId("cutout-multi-depth"); multiDepth.setSelected(true);
        TextField perPass = new TextField(metric ? "0.5" : "0.02"); perPass.setId("cutout-depth-per-pass");
        perPass.disableProperty().bind(multiDepth.selectedProperty().not());
        TextField thinZ = new TextField(metric ? "-0.5" : "-0.02"); thinZ.setId("cutout-thin-z");
        var seed = new java.util.concurrent.atomic.AtomicReference<>(new GeometryGCodeParameters(
                metric ? 3 : 0.12, metric ? 1.7 : 0.067, true, metric ? 0.5 : 0.02, metric ? 120 : 5, 0, false));
        var profile = new java.util.concurrent.atomic.AtomicReference<>(ToolProfile.C1);
        cutZ.setTooltip(new Tooltip("Cut Z negativo. O valor e gravado na Geometry de recorte e recuperado ao gerar CNC. Confira a espessura e o sacrificio da placa."));
        thinZ.setTooltip(new Tooltip("Thin Depth: Cut Z negativo e mais raso que o recorte completo. E aplicado somente a Geometry das pontes; o recorte principal conserva sua profundidade."));
        TextField gapSizeField = new TextField(metric ? "4" : "0.16");
        ComboBox<GapType> gapTypeCombo = new ComboBox<>();
        gapTypeCombo.getItems().addAll(GapType.values());
        gapTypeCombo.setValue(GapType.BRIDGE);
        gapTypeCombo.setId("cutout-gap-type");
        thinZ.disableProperty().bind(gapTypeCombo.valueProperty().isNotEqualTo(GapType.THIN));
        TextField biteDiameterField = new TextField(metric ? "0.8" : "0.031");
        TextField biteSpacingField = new TextField(metric ? "0.4" : "0.016");
        biteDiameterField.disableProperty().bind(gapTypeCombo.valueProperty().isNotEqualTo(GapType.M_BITES));
        biteSpacingField.disableProperty().bind(gapTypeCombo.valueProperty().isNotEqualTo(GapType.M_BITES));

        ComboBox<GapPattern> gapPatternCombo = new ComboBox<>();
        gapPatternCombo.getItems().addAll(GapPattern.values());
        gapPatternCombo.setValue(GapPattern.FOUR);
        List<Geometry> manualAreas = new ArrayList<>();
        Label manualStatus = new Label("Sem gaps manuais; sera usado o padrao automatico.");
        manualStatus.setWrapText(true);
        Button rectangleGapButton = new Button("Adicionar gap por retangulo");
        Button polygonGapButton = new Button("Adicionar gap por poligono");
        for (Button button : new Button[]{rectangleGapButton, polygonGapButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> {
                boolean polygon = button == polygonGapButton;
                if (areaStarter.begin(polygon, area -> {
                    manualAreas.add(area);
                    manualStatus.setText(manualAreas.size() + " area(s) de gap desenhada(s).");
                }, () -> manualStatus.setText("Desenho cancelado; " + manualAreas.size() + " gap(s) mantidos."))) {
                    manualStatus.setText(polygon
                            ? "Clique nos vertices; Enter ou botao direito conclui; Esc cancela."
                            : "Clique em dois cantos; Esc cancela.");
                } else {
                    manualStatus.setText("Nao foi possivel iniciar o desenho.");
                }
            });
        }
        Button clearManualButton = new Button("Limpar gaps manuais");
        clearManualButton.setMaxWidth(Double.MAX_VALUE);
        clearManualButton.setOnAction(event -> {
            cancelArea.run();
            manualAreas.clear();
            manualStatus.setText("Sem gaps manuais; sera usado o padrao automatico.");
        });

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
            cutZ.setText(Double.toString(-selected.machining().cutDepth()));
            multiDepth.setSelected(selected.machining().multiDepth());
            perPass.setText(Double.toString(selected.machining().depthPerPass()));
            thinZ.setText(Double.toString(-selected.thinDepth()));
        }, errorLabel);
        Label transferNote = new Label("A base transfere diametro, margem, gaps, Cut Z, Multi-Depth e Thin Depth; avanco, Travel Z, spindle e espera acompanham a Geometry. Gaps manuais sao preservados. Tool Offset nao e reaplicado: o recorte ja compensa o diametro.");
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
        grid.addRow(6, new Label("Tipo de gap:"), gapTypeCombo);
        grid.addRow(7, new Label("M-Bites dia:"), biteDiameterField);
        grid.addRow(8, new Label("M-Bites spacing:"), biteSpacingField);
        grid.addRow(9, new Label("Cut Z:"), cutZ);
        grid.addRow(10, multiDepth, perPass);
        grid.addRow(11, new Label("Thin Depth:"), thinZ);
        for (TextField field : List.of(cutZ, perPass, thinZ)) { field.setMinWidth(0); field.setPrefColumnCount(7); }

        Button freeformButton = new Button("Gerar (Free-form)");
        freeformButton.getStyleClass().add("primary-action");
        freeformButton.setMaxWidth(Double.MAX_VALUE);
        Button rectangularButton = new Button("Gerar (Rectangular)");
        rectangularButton.getStyleClass().add("primary-action");
        rectangularButton.setMaxWidth(Double.MAX_VALUE);
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);

        freeformButton.setOnAction(e -> tryGenerate(CutoutShape.FREEFORM, singleRadio, convexShapeCb, toolDiaField,
                marginField, gapSizeField, gapPatternCombo, gapTypeCombo, biteDiameterField,
                biteSpacingField, manualAreas, cutZ, multiDepth, perPass, thinZ, seed.get(), profile.get(), errorLabel, onGenerate));
        rectangularButton.setOnAction(e -> tryGenerate(CutoutShape.RECTANGULAR, singleRadio, convexShapeCb, toolDiaField,
                marginField, gapSizeField, gapPatternCombo, gapTypeCombo, biteDiameterField,
                biteSpacingField, manualAreas, cutZ, multiDepth, perPass, thinZ, seed.get(), profile.get(), errorLabel, onGenerate));
        closeButton.setOnAction(e -> onClose.run());

        Label workflowNote = new Label("O Cutout cria Geometry. Em Thin, cria outra Geometry "
                + "para as pontes, com Thin Depth configurado: gere seu CNC Job separadamente. "
                + "Areas manuais removem exatamente os trechos do caminho que cobrem; "
                + "se houver alguma, substituem o padrao automatico de gaps. "
                + "Revise alturas, avanco e spindle antes de criar cada CNC Job. M-Bites cria Excellon: configure a furacao separadamente.");
        workflowNote.setWrapText(true);
        Label title = new Label("Cutout Tool");
        title.getStyleClass().add("tool-title");
        VBox box = new VBox(10,
                title, new Label("Unidades do arquivo: " + units),
                picker, transferNote, grid, rectangleGapButton, polygonGapButton, clearManualButton, manualStatus, workflowNote,
                errorLabel, freeformButton, rectangularButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static void tryGenerate(CutoutShape shape, RadioButton singleRadio, CheckBox convexShapeCb,
            TextField toolDiaField, TextField marginField, TextField gapSizeField,
            ComboBox<GapPattern> gapPatternCombo, ComboBox<GapType> gapTypeCombo,
            TextField biteDiameterField, TextField biteSpacingField,
            List<Geometry> manualAreas, TextField cutZ, CheckBox multiDepth, TextField perPass, TextField thinZ,
            GeometryGCodeParameters seed, ToolProfile profile, Label errorLabel,
            Consumer<Result> onGenerate) {
        try {
            double toolDia = parseDouble(toolDiaField.getText(), "Tool Dia");
            double margin = parseDouble(marginField.getText(), "Margin");
            double gapSize = parseDouble(gapSizeField.getText(), "Gap size");
            CutoutKind kind = singleRadio.isSelected() ? CutoutKind.SINGLE : CutoutKind.PANEL;
            CutoutParameters cutoutParams = new CutoutParameters(toolDia, margin, convexShapeCb.isSelected(),
                    kind, shape, gapSize, gapPatternCombo.getValue());
            boolean bites = gapTypeCombo.getValue() == GapType.M_BITES;
            double biteDiameter = bites ? parseDouble(biteDiameterField.getText(), "M-Bites dia") : 0;
            double biteSpacing = bites ? parseDouble(biteSpacingField.getText(), "M-Bites spacing") : 0;
            if (bites && (biteDiameter <= 0 || biteSpacing < 0
                    || manualAreas.isEmpty() && (gapSize <= 0 || gapPatternCombo.getValue() == GapPattern.NONE))) {
                throw new IllegalArgumentException("M-Bites exige diametro positivo, espacamento valido e gaps.");
            }

            errorLabel.setText("");
            double cut = parseDouble(cutZ.getText(), "Cut Z");
            if (!Double.isFinite(cut) || cut >= 0) throw new IllegalArgumentException("Cut Z deve ser negativo e finito.");
            var machining = seed.withCutting(-cut, multiDepth.isSelected(),
                    multiDepth.isSelected() ? parseDouble(perPass.getText(), "Depth per pass") : seed.depthPerPass());
            GeometryGCodeParameters thin = null;
            if (gapTypeCombo.getValue() == GapType.THIN) {
                double thinCut = parseDouble(thinZ.getText(), "Thin Depth");
                if (!Double.isFinite(thinCut) || thinCut >= 0 || thinCut <= cut)
                    throw new IllegalArgumentException("Thin Depth deve ser negativo e mais raso que Cut Z.");
                thin = machining.withCutting(-thinCut, machining.multiDepth(), machining.depthPerPass());
            }
            onGenerate.accept(new Result(cutoutParams, gapTypeCombo.getValue(), biteDiameter,
                    biteSpacing, List.copyOf(manualAreas), machining, thin, profile));
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static double parseDouble(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + ": numero invalido");
        }
    }
}
