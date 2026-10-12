package org.flatcam.fx;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.flatcam.cam.pdf.PrintPdfExporter;

final class PrintPdfDialog extends Dialog<PrintPdfExporter.Options> {
    PrintPdfDialog(Window owner, int objects) {
        setTitle("Imprimir como PDF"); setHeaderText(objects + " objeto(s) selecionado(s) — escala física 1:1");
        if (owner != null) initOwner(owner);
        ComboBox<String> paper = new ComboBox<>(); paper.getItems().setAll(PrintPdfExporter.PAPERS); paper.setValue("A4"); paper.setId("print-pdf-paper");
        CheckBox landscape = new CheckBox("Paisagem"); landscape.setId("print-pdf-landscape");
        landscape.disableProperty().bind(paper.valueProperty().isEqualTo("Bounds"));
        CheckBox monochrome = new CheckBox("Preto e branco"); monochrome.setSelected(true); monochrome.setId("print-pdf-monochrome");
        ToolDescriptions.apply(paper,"Página","Bounds ajusta a página aos limites da seleção com margem de 1 mm. Os demais formatos têm margem mínima de 5 mm e desenho centralizado. Não reduz nem corta o desenho para fazê-lo caber.");
        ToolDescriptions.apply(landscape,"Orientação","Troca largura e altura do papel. Não gira, espelha ou modifica os objetos. Bounds usa as dimensões da seleção.");
        ToolDescriptions.apply(monochrome,"Cores","Ativado: desenha em preto sobre fundo branco. Desativado: usa a cor de cada objeto/camada, sem transparência ou cores por abertura. Não inclui grade, seleção ou HUD.");
        GridPane content = new GridPane(); content.setHgap(12); content.setVgap(10);
        content.addRow(0,new Label("Página:"),paper); content.add(landscape,1,1); content.add(monochrome,1,2);
        Label note = new Label("Inclui os objetos selecionados, mesmo ocultos. Não inclui rascunhos do editor.\nAo imprimir o PDF, use Tamanho real / 100%; não Ajustar à página.");
        note.setWrapText(true); note.setMaxWidth(440); content.add(note,0,3,2,1);
        getDialogPane().setContent(content);
        ButtonType print = new ButtonType("Salvar PDF…",ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(print,ButtonType.CANCEL);
        ToolDescriptions.apply(getDialogPane().lookupButton(print),"Salvar PDF","Escolha o destino. A geometria será escrita como vetores em segundo plano, com progresso e cancelamento. Se não couber, escolha uma página maior ou Bounds.");
        ToolDescriptions.apply(getDialogPane().lookupButton(ButtonType.CANCEL),"Cancelar","Fecha sem gerar ou alterar arquivos.");
        if (owner != null && owner.getScene() != null)
            getDialogPane().getScene().getStylesheets().setAll(owner.getScene().getStylesheets());
        FluidTooltips.install(this,"Imprimir como PDF");
        setResultConverter(button -> button==print ? new PrintPdfExporter.Options(paper.getValue(),!landscape.isSelected(),monochrome.isSelected()) : null);
    }
}
