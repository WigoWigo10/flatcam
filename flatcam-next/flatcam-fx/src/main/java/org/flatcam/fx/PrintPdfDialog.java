package org.flatcam.fx;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.flatcam.cam.pdf.PrintPdfExporter;

final class PrintPdfDialog extends Dialog<PrintPdfExporter.Options> {
    PrintPdfDialog(Window owner, int objects) {
        setTitle(I18n.text("ui.217ce43fce3a")); setHeaderText(objects + I18n.text("ui.a00d85a38d8e"));
        if (owner != null) initOwner(owner);
        ComboBox<String> paper = new ComboBox<>(); paper.getItems().setAll(PrintPdfExporter.PAPERS); paper.setValue("A4"); paper.setId("print-pdf-paper");
        CheckBox landscape = new CheckBox(I18n.text("ui.d88a305edb3b")); landscape.setId("print-pdf-landscape");
        landscape.disableProperty().bind(paper.valueProperty().isEqualTo("Bounds"));
        CheckBox monochrome = new CheckBox(I18n.text("ui.bdf1d1632994")); monochrome.setSelected(true); monochrome.setId("print-pdf-monochrome");
        ToolDescriptions.apply(paper,I18n.text("ui.8f63f4d7594a"),I18n.text("ui.1ee0fa81ae2e"));
        ToolDescriptions.apply(landscape,I18n.text("ui.3126f23d71e8"),I18n.text("ui.fff2073e444a"));
        ToolDescriptions.apply(monochrome,I18n.text("ui.7bff6181add2"),I18n.text("ui.fb364c022197"));
        GridPane content = new GridPane(); content.setHgap(12); content.setVgap(10);
        content.addRow(0,new Label(I18n.text("ui.97c2b20d2f1c")),paper); content.add(landscape,1,1); content.add(monochrome,1,2);
        Label note = new Label(I18n.text("ui.8895e0c78193"));
        note.setWrapText(true); note.setMaxWidth(440); content.add(note,0,3,2,1);
        getDialogPane().setContent(content);
        ButtonType print = new ButtonType(I18n.text("ui.08359f6f7004"),ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(print,ButtonType.CANCEL);
        ToolDescriptions.apply(getDialogPane().lookupButton(print),I18n.text("ui.31bfaa9207f4"),I18n.text("ui.83740f3d486c"));
        ToolDescriptions.apply(getDialogPane().lookupButton(ButtonType.CANCEL),I18n.text("ui.bb9dbb406dcb"),I18n.text("ui.babe8e8ff6f6"));
        if (owner != null && owner.getScene() != null)
            getDialogPane().getScene().getStylesheets().setAll(owner.getScene().getStylesheets());
        FluidTooltips.install(this,I18n.text("ui.217ce43fce3a"));
        setResultConverter(button -> button==print ? new PrintPdfExporter.Options(paper.getValue(),!landscape.isSelected(),monochrome.isSelected()) : null);
    }
}
