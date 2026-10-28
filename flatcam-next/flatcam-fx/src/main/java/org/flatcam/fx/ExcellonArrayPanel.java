package org.flatcam.fx;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.flatcam.cam.excellon.ExcellonArray;

/** Form only; preview generation and publication belong to the editor controller. */
final class ExcellonArrayPanel extends VBox {
    final ComboBox<String> mode=choice("mode","LINEAR","CIRCULAR","GRID");
    final ComboBox<String> shape=choice("shape","Furos","Slots");
    final ComboBox<String> axis=choice("axis","X","Y","ANGLE");
    final TextField count,pitch,angle,x,y,radius,start,step,slotLength,slotAngle;
    final CheckBox clockwise=new CheckBox("Horário (CW)"),rotateSlots=new CheckBox("Girar slots com o círculo");
    final Button preview=new Button("Pré-visualizar array"),pick=new Button("Escolher início/centro no plot"),
            confirm=new Button("Confirmar array"),cancel=new Button("Cancelar prévia");
    ExcellonArrayPanel(boolean metric,Node grid,Runnable onPreview,Runnable onPick,Runnable onConfirm,Runnable onCancel,Runnable onChange) {
        super(6);
        mode.setValue(ToolDefaults.choice("editor.excellon.arraymode")); shape.setValue("Furos"); axis.setValue(ToolDefaults.choice("editor.excellon.axis"));
        count=field("count",ToolDefaults.text("editor.excellon.count",true),"Quantidade TOTAL de novos elementos, incluindo o primeiro: 1 a 10.000.");
        pitch=field("pitch",ToolDefaults.text("editor.excellon.pitch",metric),"Passo entre centros nas unidades do objeto. Não pode ser zero para vários elementos.");
        angle=field("angle",ToolDefaults.text("editor.excellon.angle",true),"Ângulo linear em graus, anti-horário a partir de X. Usado no eixo ANGLE.");
        x=field("x","0","X absoluto do início linear ou centro circular, nas unidades do objeto.");
        y=field("y","0","Y absoluto do início linear ou centro circular, nas unidades do objeto.");
        radius=field("radius",ToolDefaults.text("editor.excellon.radius",metric),"Raio positivo do círculo de centros, nas unidades do objeto.");
        start=field("start",ToolDefaults.text("editor.excellon.startangle",true),"Ângulo inicial em graus, anti-horário a partir de X.");
        step=field("step",ToolDefaults.text("editor.excellon.stepangle",true),"Passo angular positivo. Quantidade × passo não pode exceder 360°; não duplica o primeiro elemento.");
        slotLength=field("slot-length",ToolDefaults.text("editor.excellon.slotlength",metric),"Comprimento TOTAL do slot incluindo extremidades arredondadas; deve ser maior que o diâmetro.");
        slotAngle=field("slot-angle",ToolDefaults.text("editor.excellon.slotangle",true),"Direção do slot em graus a partir de X; com rotação circular ativa, relativa ao raio de cada elemento.");
        angle.disableProperty().bind(axis.valueProperty().isNotEqualTo("ANGLE"));
        rotateSlots.disableProperty().bind(mode.valueProperty().isNotEqualTo("CIRCULAR"));
        clockwise.setSelected(ToolDefaults.flag("editor.excellon.clockwise")); rotateSlots.setSelected(ToolDefaults.flag("editor.excellon.rotateslots"));
        clockwise.setId("excellon-array-clockwise"); rotateSlots.setId("excellon-array-rotate-slots");
        var linear=new VBox(6,row("Eixo:",axis),row("Passo:",pitch),row("Graus:",angle));
        var circular=new VBox(6,row("Raio:",radius),row("Graus iniciais:",start),row("Passo angular:",step),clockwise);
        var slots=new VBox(6,row("Comprimento:",slotLength),row("Graus do slot:",slotAngle),rotateSlots);
        linear.visibleProperty().bind(mode.valueProperty().isEqualTo("LINEAR")); circular.visibleProperty().bind(mode.valueProperty().isEqualTo("CIRCULAR"));
        grid.visibleProperty().bind(mode.valueProperty().isEqualTo("GRID")); slots.visibleProperty().bind(shape.valueProperty().isEqualTo("Slots").and(mode.valueProperty().isNotEqualTo("GRID")));
        var origin=new HBox(6,new Label("Origem X:"),x,new Label("Y:"),y);
        origin.visibleProperty().bind(mode.valueProperty().isNotEqualTo("GRID"));
        var quantity=row("Quantidade:",count); quantity.visibleProperty().bind(mode.valueProperty().isNotEqualTo("GRID"));
        for(var node:List.of(linear,circular,slots,grid,origin,quantity)) node.managedProperty().bind(node.visibleProperty());
        preview.setId("excellon-array-preview"); pick.setId("excellon-array-pick"); confirm.setId("excellon-array-confirm"); cancel.setId("excellon-array-cancel");
        preview.setOnAction(e->onPreview.run()); pick.setOnAction(e->onPick.run()); confirm.setOnAction(e->onConfirm.run()); cancel.setOnAction(e->onCancel.run());
        ToolDescriptions.apply(mode,"Tipo de array","LINEAR cria novos elementos em X, Y ou ângulo. CIRCULAR distribui por raio e passo. GRID replica os furos/slots selecionados; aplicação direta como no FX anterior.");
        ToolDescriptions.apply(shape,"Elementos do array","LINEAR/CIRCULAR criam novos furos ou slots com o diâmetro informado, sem exigir seleção. A ferramenta é reutilizada pelo diâmetro ou criada ao confirmar.");
        ToolDescriptions.apply(axis,"Eixo linear","X horizontal; Y vertical; ANGLE usa os graus do campo abaixo.");
        ToolDescriptions.apply(clockwise,"Sentido circular","Marcado: horário; desmarcado: anti-horário. O ângulo inicial é sempre medido a partir de X.");
        ToolDescriptions.apply(rotateSlots,"Rotação dos slots","Gira a direção do slot com cada raio no array circular. Desmarcado mantém todos paralelos ao ângulo informado.");
        ToolDescriptions.apply(preview,"Prévia do array","LINEAR/CIRCULAR: calcula fora da interface sem criar ferramentas ou mudar o rascunho. Confirme pelo botão ou clique no plot.\n\nAtenção: GRID aplica diretamente a cópia da seleção, sem uma etapa de confirmação.\n\nAtalho: Esc descarta a prévia linear/circular.");
        ToolDescriptions.apply(pick,"Origem no plot","Linear: clique no primeiro centro. Circular: clique no centro e no primeiro elemento para definir raio e ângulo, em qualquer quadrante. Respeita o Snap; depois mostra a prévia.");
        ToolDescriptions.apply(confirm,"Confirmar array","Aplica exatamente a prévia ao rascunho. O clique de confirmação não desloca o array.\n\nAtalhos: Ctrl+Z desfaz o array inteiro e a ferramenta nova; Ctrl+Y refaz.");
        ToolDescriptions.apply(cancel,"Cancelar array","Descarta a prévia ou invalida o resultado pendente, sem remover um array já confirmado.\n\nLimitação: use Cancelar na barra de progresso para solicitar a interrupção de um cálculo em andamento.");
        for(var field:List.of(count,pitch,angle,x,y,radius,start,step,slotLength,slotAngle)) field.textProperty().addListener((o,a,b)->onChange.run());
        for(var prop:List.<javafx.beans.value.ObservableValue<?>>of(mode.valueProperty(),shape.valueProperty(),axis.valueProperty(),clockwise.selectedProperty(),rotateSlots.selectedProperty()))
            prop.addListener((o,a,b)->onChange.run());
        getChildren().addAll(mode,shape,quantity,origin,linear,circular,slots,grid,pick,preview,new FlowPane(6,6,confirm,cancel));
        state(false,false,false,false);
    }
    boolean grid() { return "GRID".equals(mode.getValue()); }
    boolean circular() { return "CIRCULAR".equals(mode.getValue()); }
    boolean slots() { return "Slots".equals(shape.getValue()); }
    void slots(boolean slots) { shape.setValue(slots ? "Slots" : "Furos"); }
    void origin(double px,double py) { x.setText(Double.toString(px)); y.setText(Double.toString(py)); }
    void state(boolean busy,boolean active,boolean ready,boolean hasSelection) {
        preview.setDisable(busy || grid()&&!hasSelection); pick.setDisable(busy||grid());
        confirm.setDisable(busy||!ready); cancel.setDisable(!active);
    }
    ExcellonArray.Parameters parameters(double diameter) {
        double direction=circular() ? 0 : switch(axis.getValue()) { case "X"->0; case "Y"->90; default->number(angle); };
        return new ExcellonArray.Parameters(ExcellonArray.Kind.valueOf(mode.getValue()),Integer.parseInt(count.getText().trim()),diameter,slots(),
                slots() ? number(slotLength) : 0,slots() ? number(slotAngle) : 0,rotateSlots.isSelected(),
                number(x),number(y),circular() ? 0 : number(pitch),direction,
                circular() ? number(radius) : 0,circular() ? number(start) : 0,circular() ? number(step) : 0,clockwise.isSelected());
    }
    private static double number(TextField f) { return Double.parseDouble(f.getText().trim().replace(',','.')); }
    private static ComboBox<String> choice(String id,String... options) {
        var box=new ComboBox<>(FXCollections.observableArrayList(options)); box.setId("excellon-array-"+id); box.setMaxWidth(Double.MAX_VALUE); box.setMinWidth(0); return box;
    }
    private static TextField field(String id,String value,String help) {
        var f=new TextField(value); f.setId("excellon-array-"+id); f.setMinWidth(0); f.setPrefColumnCount(5);
        String title = switch (id) {
            case "count" -> "Quantidade do array";
            case "pitch" -> "Passo linear";
            case "angle" -> "Ângulo linear";
            case "x", "y" -> "Origem " + id.toUpperCase(java.util.Locale.ROOT);
            case "radius" -> "Raio do array";
            case "start" -> "Ângulo inicial";
            case "step" -> "Passo angular";
            case "slot-length" -> "Comprimento do slot";
            case "slot-angle" -> "Ângulo do slot";
            default -> "Array Excellon";
        };
        ToolDescriptions.apply(f,title,help+"\n\nPrévia: alterar descarta o resultado anterior; somente Confirmar array modifica o rascunho."); return f;
    }
    private static HBox row(String label,Node value) { return new HBox(6,new Label(label),value); }
}
