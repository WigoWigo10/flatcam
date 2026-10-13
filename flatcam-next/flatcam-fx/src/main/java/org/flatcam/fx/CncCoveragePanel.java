package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gcode.MachiningCoverage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;

/** Worker-produced residuals; selection only chooses an existing geometry for the Plot overlay. */
final class CncCoveragePanel extends VBox {
    private final TableView<MachiningCoverage.Step> table = new TableView<>();
    private final ComboBox<String> scope = new ComboBox<>();
    private final Label status = new Label("Calculando cobertura nominal 2D em segundo plano…");
    private final Button back = new Button("Voltar aos parâmetros CNC");
    private final Button cancel = new Button("Cancelar cálculo");
    private final Button finalResidue = new Button("Mostrar resíduo final");
    private final Consumer<Geometry> highlight;
    private final String units;
    private MachiningCoverage.Result result;

    CncCoveragePanel(String source, String units, List<ToolGeometry> tools, Consumer<Geometry> highlight,
                     Runnable onBack, Runnable onCancel) {
        super(8); this.highlight = highlight; this.units = units; setPadding(new javafx.geometry.Insets(12));
        setId("cnc-coverage-panel"); setMinWidth(0);
        var title = new Label("Cobertura NCC — " + source); title.setWrapText(true); title.getStyleClass().add("tool-title");
        status.setId("cnc-coverage-status"); status.setWrapText(true);
        table.setId("cnc-coverage-tools"); table.setMinWidth(0); table.setPrefHeight(Math.min(200, 32 + tools.size() * 28));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(28);
        column("Fresa", step -> "T" + (step.toolIndex() + 1) + " · " + CncPreflight.number(tools.get(step.toolIndex()).toolDiameter()));
        column("Individual", step -> step.hasIndividualTarget() ? area(step.ownRemainingArea()) : "Sem alvo individual");
        column("Acumulado", step -> area(step.cumulativeRemainingArea()));
        scope.getItems().setAll("Após esta fresa (acumulado)", "Somente esta fresa"); scope.getSelectionModel().selectFirst();
        scope.setId("cnc-coverage-scope"); scope.setMinWidth(0); scope.setMaxWidth(Double.MAX_VALUE);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> showSelection());
        scope.valueProperty().addListener((obs, old, value) -> showSelection());
        finalResidue.setId("cnc-coverage-final"); finalResidue.setMaxWidth(Double.MAX_VALUE); finalResidue.setDisable(true);
        finalResidue.setOnAction(event -> {
            table.getSelectionModel().clearSelection(); highlight.accept(result.finalRemaining());
            status.setText("Resíduo final: " + area(result.finalRemainingArea()) + " " + squareUnit() + ". Laranja = material nominal ainda não coberto.");
        });
        back.setId("cnc-coverage-back"); back.setMaxWidth(Double.MAX_VALUE); back.setDisable(true); back.setOnAction(event -> onBack.run());
        cancel.setId("cnc-coverage-cancel"); cancel.setMaxWidth(Double.MAX_VALUE); cancel.setOnAction(event -> onCancel.run());
        cancel.visibleProperty().bind(cancel.disableProperty().not()); cancel.managedProperty().bind(cancel.visibleProperty());
        Label limits = new Label("Áreas em " + squareUnit() + ". Laranja mostra o restante nominal, não uma simulação da peça. "
                + "Após esta fresa inclui as anteriores; Somente esta fresa compara com seu alvo NCC (em Rest, o alvo pode já ser residual). "
                + "Ferramentas ISO não têm alvo individual de limpeza. Não valida Z, grampos, material, exclusões ou a máquina."); limits.setWrapText(true);
        getChildren().addAll(title, status, table, scope, finalResidue, limits, cancel, back);
        ToolDescriptions.apply(table, "Resíduos por fresa", "Selecione uma linha para destacar o restante no Plot. Restante da fresa é seu alvo menos seu próprio corte; o acumulado usa todas as fresas até essa linha. ISO pode contribuir ao acumulado sem ter um alvo individual de limpeza.");
        ToolDescriptions.apply(scope, "Escopo do mapa", "Alterna entre o resíduo acumulado após a fresa selecionada e o resíduo do alvo individual dessa fresa. Não muda ordem, parâmetros ou trajetos.");
        ToolDescriptions.apply(finalResidue, "Resíduo final", "Destaca a área-alvo global que nenhum trajeto nominal das fresas cobre. Valores pequenos não são removidos por tolerância para declarar cobertura total.");
        ToolDescriptions.apply(back, "Retornar ao CNC", "Volta ao painel com os parâmetros revisados preservados. Não grava G-code nem altera a Geometry. Durante o cálculo, cancele e aguarde a conclusão.");
        ToolDescriptions.apply(cancel, "Cancelar análise", "Solicita cancelamento do worker. Operações individuais do JTS podem terminar antes de observar o pedido. Não modifica geometria ou arquivos.");
    }
    void complete(MachiningCoverage.Result value) {
        result = value; table.getItems().setAll(value.steps()); setIdle(); finalResidue.setDisable(false); table.getSelectionModel().selectLast();
    }
    void failed(String message) { status.setText(message); setIdle(); highlight.accept(null); }
    private void setIdle() { back.setDisable(false); cancel.setDisable(true); }
    private void showSelection() {
        var step = table.getSelectionModel().getSelectedItem(); if (step == null) return;
        Geometry shape = scope.getSelectionModel().getSelectedIndex() == 0 ? step.cumulativeRemaining() : step.ownRemaining();
        double residualArea = scope.getSelectionModel().getSelectedIndex() == 0 ? step.cumulativeRemainingArea() : step.ownRemainingArea();
        highlight.accept(shape);
        status.setText(shape == null ? "Esta fresa não tem área individual de limpeza. Use acumulado ou resíduo final."
                : "T" + (step.toolIndex() + 1) + ": " + scope.getValue() + " — " + area(residualArea) + " " + squareUnit() + ". Laranja = restante nominal.");
    }
    private void column(String title, java.util.function.Function<MachiningCoverage.Step, String> value) {
        var column = new TableColumn<MachiningCoverage.Step, String>(title); column.setSortable(false); column.setMinWidth(60);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue()))); table.getColumns().add(column);
    }
    private String squareUnit() { return "MM".equals(units) ? "mm²" : "in²"; }
    private static String area(double value) { return value > 0 && value < .000001 ? String.format(Locale.ROOT, "%.3g", value) : CncPreflight.number(value); }
}
