package org.flatcam.fx;

import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.flatcam.cam.gcode.*;
import org.locationtech.jts.geom.*;

/** Common Geometry/Drilling CNC exclusions. Stored drafts remain visible when disabled. */
final class CncExclusionEditor {
    @FunctionalInterface interface AreaSelector { void select(boolean polygon,Consumer<Geometry> selected,Runnable cancelled); }
    private final CheckBox enabled=new CheckBox(I18n.text("ui.ec707badba9d"));
    private final TableView<CncExclusionArea> table=new TableView<>();
    private final TitledPane view;
    CncExclusionEditor(GeometryJobOptions saved,String units,AreaSelector selector,Consumer<Geometry> preview) {
        this(saved.exclusionsEnabled(), saved.exclusions(), units, selector, preview);
    }
    CncExclusionEditor(boolean active,List<CncExclusionArea> areas,String units,AreaSelector selector,Consumer<Geometry> preview) {
        enabled.setId("cnc-exclusions-enabled"); enabled.setSelected(active);
        table.setId("cnc-exclusions"); table.setItems(FXCollections.observableArrayList(areas));
        TableColumn<CncExclusionArea,String> number=new TableColumn<>("#");
        number.setCellValueFactory(c -> new ReadOnlyStringWrapper(Integer.toString(table.getItems().indexOf(c.getValue())+1)));
        TableColumn<CncExclusionArea,String> strategyCol=new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().strategy().name()));
        TableColumn<CncExclusionArea,String> height=new TableColumn<>("Over Z");
        height.setCellValueFactory(c -> new ReadOnlyStringWrapper(Double.toString(c.getValue().overZ())));
        table.getColumns().addAll(number,strategyCol,height); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(130); table.setPlaceholder(new Label(I18n.text("ui.094b8f2019b4")));
        var strategy=new ComboBox<CncExclusionArea.Strategy>(FXCollections.observableArrayList(CncExclusionArea.Strategy.values()));
        strategy.setId("cnc-exclusion-strategy"); strategy.setValue(CncExclusionArea.Strategy.AROUND);
        var over=new TextField("IN".equalsIgnoreCase(units) ? Double.toString(20 / 25.4) : "20"); over.setId("cnc-exclusion-over-z"); over.setPrefColumnCount(5);
        over.disableProperty().bind(strategy.valueProperty().isNotEqualTo(CncExclusionArea.Strategy.OVER));
        var error=new Label(); error.getStyleClass().add("form-error-label"); error.setWrapText(true);
        Runnable clearError=()->error.setText("");
        Consumer<Geometry> add=shape -> {
            try {
                if(table.getItems().size()>=100)throw new IllegalArgumentException(I18n.text("ui.74738edd5a58"));
                table.getItems().add(CncExclusionArea.of(shape,strategy.getValue(),parse(over)));
                enabled.setSelected(true); table.getSelectionModel().selectLast(); clearError.run();
            } catch(IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
        };
        var rectangle=new Button(I18n.text("ui.2f25bbf2bc99")); rectangle.setId("cnc-exclusion-draw-rectangle");
        var polygon=new Button(I18n.text("ui.48ab37e40cca")); polygon.setId("cnc-exclusion-draw-polygon");
        rectangle.setDisable(selector==null); polygon.setDisable(selector==null);
        rectangle.setOnAction(event -> selector.select(false,add,()->error.setText(I18n.text("ui.abcf44dd4e39"))));
        polygon.setOnAction(event -> selector.select(true,add,()->error.setText(I18n.text("ui.abcf44dd4e39"))));
        TextField x1=new TextField("0"), y1=new TextField("0"), x2=new TextField("1"), y2=new TextField("1");
        var fields=List.of(x1,y1,x2,y2); var ids=List.of("x1","y1","x2","y2");
        for(int i=0;i<fields.size();i++) { fields.get(i).setPrefColumnCount(4); fields.get(i).setMinWidth(0); fields.get(i).setId("cnc-exclusion-"+ids.get(i)); }
        var addRect=new Button(I18n.text("ui.c7c44665d3d6")); addRect.setId("cnc-exclusion-add");
        addRect.setOnAction(event -> { try { add.accept(new GeometryFactory().toGeometry(new Envelope(parse(x1),parse(x2),parse(y1),parse(y2)))); }
            catch(IllegalArgumentException invalid) { error.setText(invalid.getMessage()); } });
        var grid=new GridPane(); grid.setHgap(6); grid.setVgap(6);
        grid.addRow(0,new Label("X1:"),x1,new Label("Y1:"),y1); grid.addRow(1,new Label("X2:"),x2,new Label("Y2:"),y2);
        var numeric=new TitledPane(I18n.text("ui.adaada7e86c3")+units+")",new VBox(6,grid,addRect)); numeric.setExpanded(false);
        var apply=new Button(I18n.text("ui.71a36df4f1a7")); apply.setId("cnc-exclusion-apply");
        apply.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        apply.setOnAction(event -> { try {
            int index=table.getSelectionModel().getSelectedIndex(); var old=table.getItems().get(index);
            table.getItems().set(index,new CncExclusionArea(old.wkt(),strategy.getValue(),parse(over))); clearError.run();
        } catch(IllegalArgumentException invalid) { error.setText(invalid.getMessage()); } });
        var delete=new Button(I18n.text("ui.b7863532472c")); delete.setId("cnc-exclusion-delete");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(event -> TableKeyboardActions.removeSelected(table));
        table.getSelectionModel().selectedItemProperty().addListener((obs,was,area) -> {
            if(area!=null) { strategy.setValue(area.strategy()); over.setText(Double.toString(area.overZ())); }
            preview.accept(area==null?null:area.geometry());
        });
        ToolDescriptions.apply(enabled,I18n.text("ui.cdc798261cd7"),I18n.text("ui.c5795d9ddd66"));
        ToolDescriptions.apply(strategy,I18n.text("ui.dc49a4c8a767"),I18n.text("ui.c75681fd3399"));
        ToolDescriptions.apply(over,I18n.text("ui.431de278c0b2"),I18n.text("ui.f47cbba69b65"));
        ToolDescriptions.apply(table,I18n.text("ui.09a4cb6d6aa2"), I18n.text("ui.82264b389348"));
        for (Button draw : List.of(rectangle, polygon)) ToolDescriptions.apply(draw, draw.getText(), I18n.text("ui.5ad5a102cb3a"));
        for (int i=0;i<fields.size();i++) ToolDescriptions.apply(fields.get(i), ids.get(i).toUpperCase(), I18n.text("ui.0053f1f99f15") + units + ".");
        ToolDescriptions.apply(addRect,I18n.text("ui.182d4e027267"), I18n.text("ui.426c05aef19b"));
        ToolDescriptions.apply(apply,I18n.text("ui.e387a297e5b7"), I18n.text("ui.784ecc2e5dc0"));
        ToolDescriptions.apply(delete,I18n.text("ui.b7863532472c"), I18n.text("ui.0df55b232d64"));
        TableKeyboardActions.deleteWith(table, delete);
        Label note=new Label(I18n.text("ui.4003a8475ff3")); note.setWrapText(true);
        view=new TitledPane(I18n.text("ui.37007692370b"),new VBox(8,enabled,table,new HBox(8,new Label("Strategy:"),strategy,new Label("Over Z:"),over),
                new FlowPane(8,6,rectangle,polygon),numeric,apply,delete,note,error));
        view.setId("cnc-exclusions-pane");
        view.setExpanded(!areas.isEmpty());
    }
    TitledPane view() { return view; }
    GeometryJobOptions applyTo(GeometryJobOptions positions) { return positions.withExclusions(enabled.isSelected(),List.copyOf(table.getItems())); }
    GCodeGenerator.DrillJobOptions applyTo(GCodeGenerator.DrillJobOptions options) {
        return options.withExclusions(enabled.isSelected(), List.copyOf(table.getItems()));
    }
    void restore(boolean active,List<CncExclusionArea> areas) {
        table.getSelectionModel().clearSelection();
        table.getItems().setAll(areas);
        enabled.setSelected(active);
        view.setExpanded(!areas.isEmpty());
    }
    private static double parse(TextField field) {
        try { double value=Double.parseDouble(field.getText().trim().replace(',','.')); if(!Double.isFinite(value))throw new NumberFormatException(); return value; }
        catch(NumberFormatException invalid) { throw new IllegalArgumentException(I18n.text("ui.0a6b33c098d2")); }
    }
}
