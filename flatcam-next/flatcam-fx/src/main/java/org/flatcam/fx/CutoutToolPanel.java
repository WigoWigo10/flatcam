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
 * <p>Bridge, Thin and automatic M-Bites are offered. Manual gaps are drawn
 * as filled rectangle/polygon masks for Bridge or Thin. The output is editable
 * Geometry, followed by a separate Geometry-to-CNC step as in Python.
 */
final class CutoutToolPanel {

    @FunctionalInterface
    interface AreaSelectionStarter {
        boolean begin(boolean polygon, Consumer<Geometry> onSelected, Runnable onCancelled);
    }

    enum GapType { BRIDGE, THIN, M_BITES }

    record Result(CutoutParameters cutoutParams, GapType gapType,
                  double biteDiameter, double biteSpacing, List<Geometry> manualGapAreas) {
    }

    private CutoutToolPanel() {
    }

    /**
     * @param onGenerate called with the parsed parameters when either "Gerar" button is clicked and they're valid.
     * @param onClose    called when "Fechar" is clicked - MainWindow uses it to restore the tool tab's placeholder.
     */
    static Node build(String units, AreaSelectionStarter areaStarter, Runnable cancelArea,
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
        TextField gapSizeField = new TextField(metric ? "4" : "0.16");
        ComboBox<GapType> gapTypeCombo = new ComboBox<>();
        gapTypeCombo.getItems().addAll(GapType.values());
        gapTypeCombo.setValue(GapType.BRIDGE);
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

        Button freeformButton = new Button("Gerar (Free-form)");
        freeformButton.setMaxWidth(Double.MAX_VALUE);
        Button rectangularButton = new Button("Gerar (Rectangular)");
        rectangularButton.setMaxWidth(Double.MAX_VALUE);
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);

        freeformButton.setOnAction(e -> tryGenerate(CutoutShape.FREEFORM, singleRadio, convexShapeCb, toolDiaField,
                marginField, gapSizeField, gapPatternCombo, gapTypeCombo, biteDiameterField,
                biteSpacingField, manualAreas, errorLabel, onGenerate));
        rectangularButton.setOnAction(e -> tryGenerate(CutoutShape.RECTANGULAR, singleRadio, convexShapeCb, toolDiaField,
                marginField, gapSizeField, gapPatternCombo, gapTypeCombo, biteDiameterField,
                biteSpacingField, manualAreas, errorLabel, onGenerate));
        closeButton.setOnAction(e -> onClose.run());

        Label workflowNote = new Label("O Cutout cria Geometry. Em Thin, cria outra Geometry "
                + "para as pontes: gere seu CNC Job separadamente com Cut Z mais raso. "
                + "Areas manuais removem exatamente os trechos do caminho que cobrem; "
                + "se houver alguma, substituem o padrao automatico de gaps. "
                + "Configure Multi-Depth, avanco e spindle ao criar cada CNC Job.");
        workflowNote.setWrapText(true);
        VBox box = new VBox(10,
                new Label("Parametros (unidades do arquivo: " + units + ")"),
                grid, rectangleGapButton, polygonGapButton, clearManualButton, manualStatus, workflowNote,
                errorLabel, freeformButton, rectangularButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static void tryGenerate(CutoutShape shape, RadioButton singleRadio, CheckBox convexShapeCb,
            TextField toolDiaField, TextField marginField, TextField gapSizeField,
            ComboBox<GapPattern> gapPatternCombo, ComboBox<GapType> gapTypeCombo,
            TextField biteDiameterField, TextField biteSpacingField,
            List<Geometry> manualAreas, Label errorLabel,
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
                    || gapSize <= 0 || gapPatternCombo.getValue() == GapPattern.NONE)) {
                throw new IllegalArgumentException("M-Bites exige diametro positivo, espacamento valido e gaps.");
            }
            if (bites && !manualAreas.isEmpty()) {
                throw new IllegalArgumentException("M-Bites com gaps manuais ainda nao e suportado.");
            }

            errorLabel.setText("");
            onGenerate.accept(new Result(cutoutParams, gapTypeCombo.getValue(), biteDiameter,
                    biteSpacing, List.copyOf(manualAreas)));
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
