package org.flatcam.fx;

import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Confirmation before the FileChooser, not before machine execution. Closing never exports anything. */
final class CncReviewDialog extends Dialog<CncReviewDialog.Decision> {
    record Decision(boolean proceed, CncPreflight.Target edit) { }
    static final ButtonType CONTINUE = new ButtonType("Continuar e escolher arquivo…", ButtonBar.ButtonData.OK_DONE);
    static final ButtonType COVERAGE = new ButtonType("Ver cobertura no Plot", ButtonBar.ButtonData.LEFT);
    private final GridPane toolDetails = new GridPane();

    CncReviewDialog(Window owner, ThemeOption theme, CncPreflight.Report report) {
        this(owner, theme, report, false);
    }
    CncReviewDialog(Window owner, ThemeOption theme, CncPreflight.Report report, boolean coverageAvailable) {
        setTitle("Revisar CNC antes de salvar"); setHeaderText(null); setResizable(true);
        if (owner != null) initOwner(owner);
        var pane = getDialogPane(); pane.setId("cnc-review-dialog"); pane.setPrefSize(900, 650); pane.setMinSize(460, 360);
        Label title = label(report.operation() + " — " + report.source()); title.getStyleClass().add("tool-title");
        Label units = label("Unidades: " + report.units() + " • Avanços: " + report.units() + "/min • Perfil: " + report.profile());
        boolean editable=report.operation().startsWith("Geometry") || report.operation().startsWith("Excellon");
        Label explanation = label(editable ? "Confira os valores efetivos de cada ferramenta, na ordem de execução. Clique em um parâmetro para voltar ao campo correspondente. Nenhum arquivo foi gravado."
                : "Confira as informações do programa existente. Campos não inferidos precisam ser consultados no editor de código; não são rascunhos CAM. Nenhum arquivo foi gravado.");
        toolDetails.setHgap(12); toolDetails.setVgap(5);
        var detailLeft = new ColumnConstraints(); detailLeft.setPercentWidth(50);
        var detailRight = new ColumnConstraints(); detailRight.setPercentWidth(50);
        toolDetails.getColumnConstraints().setAll(detailLeft, detailRight);
        TableView<CncPreflight.Tool> table = new TableView<>(); table.setId("cnc-review-tools");
        table.getItems().setAll(report.tools()); table.setMinWidth(0); table.setFixedCellSize(28);
        table.setPrefHeight(Math.min(200, 32 + 28 * Math.max(1, report.tools().size())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        column(table, "Ferramenta", CncPreflight.Tool::name);
        column(table, "Diâmetro", CncPreflight.Tool::diameter);
        column(table, editable ? "Cut Z efetivo" : "Z mínimo (prévia)", CncPreflight.Tool::cutZ);
        column(table, "Travel Z", CncPreflight.Tool::travelZ);
        column(table, "Avanço XY", CncPreflight.Tool::feedXY);
        column(table, "Avanço Z", CncPreflight.Tool::feedZ);
        column(table, "RPM / potência", CncPreflight.Tool::power);
        ToolDescriptions.apply(table, "Parâmetros por ferramenta", editable
                ? "A ordem é a enviada ao gerador. Cut Z efetivo inclui Offset Z na furação e a profundidade calculada para a ponta V. Valores — não se aplicam ao perfil. Selecione uma linha para revisar seus detalhes."
                : "Dados extraídos do texto ou da prévia disponível. Z mínimo não é profundidade de usinagem validada. Consultar código indica valores variáveis ou não inferidos; nenhum rascunho CAM substitui o programa exportado.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            toolDetails.getChildren().clear();
            if (selected != null) {
                var entries = new java.util.ArrayList<>(selected.details());
                String prefix = report.operation().startsWith("Geometry") ? "cnc" : "drill";
                entries.add(new CncPreflight.Value("Travel Z", selected.travelZ(), editable ? new CncPreflight.Target(selected.key(), prefix.equals("cnc") ? "cnc-safe-z" : "drill-travel-z") : null));
                if (!selected.feedXY().equals("—")) entries.add(new CncPreflight.Value("Avanço XY", selected.feedXY(), editable ? new CncPreflight.Target(selected.key(), "cnc-feed") : null));
                entries.add(new CncPreflight.Value("Avanço Z", selected.feedZ(), editable ? new CncPreflight.Target(selected.key(), prefix + "-feed-z") : null));
                entries.add(new CncPreflight.Value("RPM / potência", selected.power(), editable ? new CncPreflight.Target(selected.key(), prefix + "-power") : null));
                for (int i = 0; i < entries.size(); i++) toolDetails.add(editLink(entries.get(i)), i % 2, i / 2);
            }
        });
        table.getSelectionModel().selectFirst();
        VBox common = new VBox(5); report.common().forEach(value -> common.getChildren().add(editLink(value)));
        VBox notices = new VBox(7);
        for (var issue : report.issues()) {
            String text = (issue.blocking() ? "Erro: " : "Aviso: ") + issue.text();
            if (coverageAvailable && issue.text().startsWith("Cobertura não calculada"))
                text = "Área-alvo NCC disponível: use Ver cobertura no Plot para calcular os resíduos nominais por fresa, sem salvar G-code.";
            if (issue.target() == null) notices.getChildren().add(label(text));
            else {
                Hyperlink link = (Hyperlink)editLink(new CncPreflight.Value(text, "Revisar…", issue.target()));
                if (issue.blocking()) link.getStyleClass().add("form-error-label");
                notices.getChildren().add(link);
            }
        }
        var commonPane = new TitledPane("Parâmetros comuns e posições", common); commonPane.setExpanded(false);
        var toolPane = new TitledPane("Detalhes da ferramenta selecionada", toolDetails); toolPane.setExpanded(true);
        var warningPane = new TitledPane("Avisos e limitações (" + report.issues().size() + ")", notices); warningPane.setExpanded(true);
        ToolDescriptions.apply(commonPane,"Configurações do trabalho", "Posições, ordem e exclusões são comuns ao arquivo, não individuais por fresa. Expanda para revisar os valores enviados. Os deslocamentos XY podem usar uma altura maior que a retração individual.");
        ToolDescriptions.apply(toolPane,"Detalhes por fresa", "Mostra passes, ponta V, compensação e outros valores da linha selecionada. Clique em um valor para voltar ao painel sem salvar; os rascunhos são preservados.");
        ToolDescriptions.apply(warningPane,"Erros e avisos", "Erro bloqueia Continuar. Avisos pedem conferência, mas não impedem escolher o arquivo. Cobertura não calculada não significa usinagem completa; este resumo não simula a máquina.");
        VBox body = new VBox(10, title, units, explanation, table, warningPane, toolPane, commonPane,
                label("Esta revisão confere configurações; não aprova a usinagem nem substitui conferência de origem, fixação e teste a seco."));
        body.setPadding(new Insets(8)); body.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true); scroll.setMinSize(0, 0); pane.setContent(scroll);
        var back = new ButtonType(editable ? "Voltar ao painel" : "Fechar revisão", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(CONTINUE, back);
        if (coverageAvailable) {
            pane.getButtonTypes().addFirst(COVERAGE);
            ToolDescriptions.apply(pane.lookupButton(COVERAGE), "Resíduos de usinagem", "Calcula em segundo plano a área-alvo NCC menos a faixa nominal varrida pelas fresas, com a compensação configurada. Mostra o restante por fresa e acumulado no Plot. Não simula Z, material ou a máquina e não grava G-code.");
        }
        pane.lookupButton(CONTINUE).setDisable(!report.canContinue());
        ToolDescriptions.apply(pane.lookupButton(CONTINUE), "Escolher o destino", "Abre o seletor de arquivo. Só depois da escolha o G-code será calculado e salvo em segundo plano. Erros de configuração impedem continuar; avisos não são certificação de segurança.");
        ToolDescriptions.apply(pane.lookupButton(back), "Voltar sem salvar", "Fecha a revisão, preservando os parâmetros no painel. Não grava nem substitui arquivos.");
        setResultConverter(button -> button == CONTINUE ? new Decision(true, null)
                : button == COVERAGE ? new Decision(false, new CncPreflight.Target(-1, "__coverage")) : null);
        theme.applyTo(pane.getScene()); FluidTooltips.install(this, "Revisão CNC");
    }

    private javafx.scene.Node editLink(CncPreflight.Value value) {
        if (value.target()==null) {
            var text=label(value.label()+": "+value.text());
            ToolDescriptions.apply(text,"Valor do programa", "Informação extraída do programa existente. Para alterar, feche o resumo e use o editor de código.");
            return text;
        }
        Hyperlink link = new Hyperlink(value.label() + ": " + value.text()); link.setWrapText(true); link.setMinWidth(0);
        link.setMaxWidth(Double.MAX_VALUE); link.setUserData(value.target());
        ToolDescriptions.apply(link, "Revisar parâmetro", "Fecha este resumo e leva ao campo correspondente no painel, selecionando a ferramenta quando necessário. Não altera valores nem grava arquivos.");
        link.setOnAction(event -> { setResult(new Decision(false, value.target())); close(); }); return link;
    }
    private static Label label(String text) {
        Label label = new Label(text); label.setWrapText(true); label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
    private static void column(TableView<CncPreflight.Tool> table, String title, Function<CncPreflight.Tool, String> text) {
        var column = new TableColumn<CncPreflight.Tool, String>(title); column.setSortable(false); column.setMinWidth(48);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
        table.getColumns().add(column);
    }
}
