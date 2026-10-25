package org.flatcam.fx;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.locationtech.jts.geom.Coordinate;

/** Numeric point entry: jumping previews; adding is a separate explicit action. */
final class ArcCoordinateDialog extends Dialog<ArcCoordinateDialog.Choice> {
    record Choice(Coordinate point, boolean insert) { }
    final ButtonType preview = new ButtonType("Posicionar cursor", ButtonBar.ButtonData.OK_DONE);
    final ButtonType insert = new ButtonType("Adicionar ponto", ButtonBar.ButtonData.APPLY);
    final TextField x = new TextField(), y = new TextField();
    final CheckBox relative = new CheckBox("Relativas ao cursor atual");
    final Label error = new Label();

    ArcCoordinateDialog(Window owner, Coordinate reference, String units) {
        setTitle("Coordenadas do arco");
        if (owner != null) initOwner(owner);
        Coordinate origin = new Coordinate(reference);
        x.setId("arc-coordinate-x"); y.setId("arc-coordinate-y"); relative.setId("arc-coordinate-relative");
        x.setText(number(origin.x)); y.setText(number(origin.y));
        for (TextField field : new TextField[]{x,y}) { field.setPrefColumnCount(12); field.setMinWidth(0); }
        relative.selectedProperty().addListener((obs, old, selected) -> {
            x.setText(selected ? "0" : number(origin.x)); y.setText(selected ? "0" : number(origin.y)); error.setText("");
        });
        ToolDescriptions.apply(x,"Coordenada X","Valor de X em " + units + ". Aceita ponto ou vírgula decimal, sem separador de milhares e sem expressões.");
        ToolDescriptions.apply(y,"Coordenada Y","Valor de Y em " + units + ". Em referência relativa, este valor é um deslocamento do cursor atual.");
        ToolDescriptions.apply(relative,"Referência das coordenadas",
                "Desmarcado: X/Y absolutos no projeto. Marcado: deslocamentos a partir do cursor capturado ao abrir este diálogo. Alternar reinicia os campos; não move objetos.");
        Label referenceLabel = new Label("Cursor de referência: X " + number(origin.x) + "; Y " + number(origin.y) + " [" + units + "]");
        referenceLabel.setWrapText(true);
        Label note = new Label("Posicionar cursor centraliza a vista e atualiza a prévia, sem adicionar ponto; o Snap ativo será respeitado.\nAdicionar ponto usa os valores exatos digitados, sem Snap, como o próximo clique do arco.");
        note.setWrapText(true); note.setMaxWidth(440);
        error.getStyleClass().add("form-error-label"); error.setWrapText(true); error.setId("arc-coordinate-error");
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(12));
        Label lx = new Label("X [" + units + "]:"), ly = new Label("Y [" + units + "]:");
        lx.setLabelFor(x); ly.setLabelFor(y);
        grid.add(referenceLabel,0,0,2,1); grid.addRow(1,lx,x); grid.addRow(2,ly,y);
        grid.add(relative,0,3,2,1); grid.add(note,0,4,2,1); grid.add(error,0,5,2,1);
        getDialogPane().setContent(grid);
        ButtonType cancel = new ButtonType("Cancelar",ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(preview,insert,cancel);
        for (ButtonType action : new ButtonType[]{preview,insert}) {
            getDialogPane().lookupButton(action).addEventFilter(ActionEvent.ACTION,event -> {
                try { resolve(x.getText(),y.getText(),relative.isSelected(),origin); error.setText(""); }
                catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); event.consume(); }
            });
        }
        ToolDescriptions.apply(getDialogPane().lookupButton(preview),"Posicionar cursor",
                "Move a vista e o cursor para conferir a prévia. Mantém os pontos anteriores; não confirma outro ponto. O Snap da barra inferior vale para o cursor.");
        ToolDescriptions.apply(getDialogPane().lookupButton(insert),"Adicionar ponto exato",
                "Adiciona exatamente X/Y como o próximo ponto, sem arredondar à grade. O terceiro ponto válido conclui o arco; um ponto geométrico inválido é recusado pelo editor.");
        ToolDescriptions.apply(getDialogPane().lookupButton(cancel),"Cancelar coordenadas","Fecha sem mover o cursor, inserir pontos ou cancelar o arco em andamento.");
        setResultConverter(action -> action == preview || action == insert
                ? new Choice(resolve(x.getText(),y.getText(),relative.isSelected(),origin),action == insert) : null);
        if (owner != null && owner.getScene() != null)
            getDialogPane().getScene().getStylesheets().setAll(owner.getScene().getStylesheets());
        FluidTooltips.install(this,"Coordenadas do arco");
    }

    static Coordinate resolve(String x, String y, boolean relative, Coordinate origin) {
        try {
            double px = Double.parseDouble(x.trim().replace(',','.'));
            double py = Double.parseDouble(y.trim().replace(',','.'));
            if (relative) { px += origin.x; py += origin.y; }
            if (!Double.isFinite(px) || !Double.isFinite(py)) throw new NumberFormatException();
            return new Coordinate(px,py);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Informe X e Y finitos. Use ponto ou vírgula decimal, sem expressões.");
        }
    }

    private static String number(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
