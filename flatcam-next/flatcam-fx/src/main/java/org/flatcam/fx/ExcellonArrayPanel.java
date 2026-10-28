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
    final ComboBox<String> shape=choice("shape",I18n.text("ui.23d8b077a75e"),"Slots");
    final ComboBox<String> axis=choice("axis","X","Y","ANGLE");
    final TextField count,pitch,angle,x,y,radius,start,step,slotLength,slotAngle;
    final CheckBox clockwise=new CheckBox(I18n.text("ui.913903e8f7cb")),rotateSlots=new CheckBox(I18n.text("ui.fb37044b6c97"));
    final Button preview=new Button(I18n.text("ui.309c62afa1e3")),pick=new Button(I18n.text("ui.2c5c590898f9")),
            confirm=new Button(I18n.text("ui.8dd31c88be3b")),cancel=new Button(I18n.text("ui.e7918951df55"));
    ExcellonArrayPanel(boolean metric,Node grid,Runnable onPreview,Runnable onPick,Runnable onConfirm,Runnable onCancel,Runnable onChange) {
        super(6);
        mode.setValue(ToolDefaults.choice("editor.excellon.arraymode")); shape.setValue(I18n.text("ui.23d8b077a75e")); axis.setValue(ToolDefaults.choice("editor.excellon.axis"));
        count=field("count",ToolDefaults.text("editor.excellon.count",true),I18n.text("ui.110e898c80d7"));
        pitch=field("pitch",ToolDefaults.text("editor.excellon.pitch",metric),I18n.text("ui.0007a9bac736"));
        angle=field("angle",ToolDefaults.text("editor.excellon.angle",true),I18n.text("ui.9d681ea2b6cd"));
        x=field("x","0",I18n.text("ui.25a604d85e72"));
        y=field("y","0",I18n.text("ui.d60e3bff3e9e"));
        radius=field("radius",ToolDefaults.text("editor.excellon.radius",metric),I18n.text("ui.7f56df937058"));
        start=field("start",ToolDefaults.text("editor.excellon.startangle",true),I18n.text("ui.8586c45beaf7"));
        step=field("step",ToolDefaults.text("editor.excellon.stepangle",true),I18n.text("ui.0b81fd1d732c"));
        slotLength=field("slot-length",ToolDefaults.text("editor.excellon.slotlength",metric),I18n.text("ui.07471d356c1a"));
        slotAngle=field("slot-angle",ToolDefaults.text("editor.excellon.slotangle",true),I18n.text("ui.91d035d8a376"));
        angle.disableProperty().bind(axis.valueProperty().isNotEqualTo("ANGLE"));
        rotateSlots.disableProperty().bind(mode.valueProperty().isNotEqualTo("CIRCULAR"));
        clockwise.setSelected(ToolDefaults.flag("editor.excellon.clockwise")); rotateSlots.setSelected(ToolDefaults.flag("editor.excellon.rotateslots"));
        clockwise.setId("excellon-array-clockwise"); rotateSlots.setId("excellon-array-rotate-slots");
        var linear=new VBox(6,row("Eixo:",axis),row(I18n.text("ui.ad13af65c512"),pitch),row(I18n.text("ui.cecd43eb7285"),angle));
        var circular=new VBox(6,row(I18n.text("ui.675e0525c0ab"),radius),row(I18n.text("ui.aa5255cc9fd6"),start),row(I18n.text("ui.f49cc08a77d4"),step),clockwise);
        var slots=new VBox(6,row(I18n.text("ui.0b431cc7476e"),slotLength),row(I18n.text("ui.75e9796154be"),slotAngle),rotateSlots);
        linear.visibleProperty().bind(mode.valueProperty().isEqualTo("LINEAR")); circular.visibleProperty().bind(mode.valueProperty().isEqualTo("CIRCULAR"));
        grid.visibleProperty().bind(mode.valueProperty().isEqualTo("GRID")); slots.visibleProperty().bind(shape.valueProperty().isEqualTo("Slots").and(mode.valueProperty().isNotEqualTo("GRID")));
        var origin=new HBox(6,new Label(I18n.text("ui.6ce786297f66")),x,new Label("Y:"),y);
        origin.visibleProperty().bind(mode.valueProperty().isNotEqualTo("GRID"));
        var quantity=row("Quantidade:",count); quantity.visibleProperty().bind(mode.valueProperty().isNotEqualTo("GRID"));
        for(var node:List.of(linear,circular,slots,grid,origin,quantity)) node.managedProperty().bind(node.visibleProperty());
        preview.setId("excellon-array-preview"); pick.setId("excellon-array-pick"); confirm.setId("excellon-array-confirm"); cancel.setId("excellon-array-cancel");
        preview.setOnAction(e->onPreview.run()); pick.setOnAction(e->onPick.run()); confirm.setOnAction(e->onConfirm.run()); cancel.setOnAction(e->onCancel.run());
        ToolDescriptions.apply(mode,I18n.text("ui.35cd8851ebfd"),I18n.text("ui.abd5a6b44faf"));
        ToolDescriptions.apply(shape,I18n.text("ui.e0f72e42fd1e"),I18n.text("ui.6433f93fe2e1"));
        ToolDescriptions.apply(axis,I18n.text("ui.b3cbfa0c7823"),I18n.text("ui.da10fda643f5"));
        ToolDescriptions.apply(clockwise,I18n.text("ui.53c4ec109b1b"),I18n.text("ui.b711d691f8f7"));
        ToolDescriptions.apply(rotateSlots,I18n.text("ui.bfb459654362"),I18n.text("ui.ac4005547e64"));
        ToolDescriptions.apply(preview,I18n.text("ui.1f1349498a7a"),I18n.text("ui.4efe6695e2d4"));
        ToolDescriptions.apply(pick,I18n.text("ui.100b124ce5a9"),I18n.text("ui.5f551f48c0d6"));
        ToolDescriptions.apply(confirm,I18n.text("ui.8dd31c88be3b"),I18n.text("ui.83ffad343947"));
        ToolDescriptions.apply(cancel,I18n.text("ui.7ab2af8f493a"),I18n.text("ui.12c01c1b456b"));
        for(var field:List.of(count,pitch,angle,x,y,radius,start,step,slotLength,slotAngle)) field.textProperty().addListener((o,a,b)->onChange.run());
        for(var prop:List.<javafx.beans.value.ObservableValue<?>>of(mode.valueProperty(),shape.valueProperty(),axis.valueProperty(),clockwise.selectedProperty(),rotateSlots.selectedProperty()))
            prop.addListener((o,a,b)->onChange.run());
        getChildren().addAll(mode,shape,quantity,origin,linear,circular,slots,grid,pick,preview,new FlowPane(6,6,confirm,cancel));
        state(false,false,false,false);
    }
    boolean grid() { return "GRID".equals(mode.getValue()); }
    boolean circular() { return "CIRCULAR".equals(mode.getValue()); }
    boolean slots() { return "Slots".equals(shape.getValue()); }
    void slots(boolean slots) { shape.setValue(slots ? "Slots" : I18n.text("ui.23d8b077a75e")); }
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
            case "count" -> I18n.text("ui.14de0eb44a50");
            case "pitch" -> I18n.text("ui.ad2879272fb1");
            case "angle" -> I18n.text("ui.c940d813743a");
            case "x", "y" -> I18n.text("ui.aec7ff3cf4ff") + id.toUpperCase(java.util.Locale.ROOT);
            case "radius" -> I18n.text("ui.9ee8a6c5f806");
            case "start" -> I18n.text("ui.43f526946ebc");
            case "step" -> I18n.text("ui.73d3d883fd19");
            case "slot-length" -> I18n.text("ui.aa3e43d79709");
            case "slot-angle" -> I18n.text("ui.bd16e16d742c");
            default -> I18n.text("ui.c1ffdcdc2c9a");
        };
        ToolDescriptions.apply(f,title,help+I18n.text("ui.238533ddfa15")); return f;
    }
    private static HBox row(String label,Node value) { return new HBox(6,new Label(label),value); }
}
