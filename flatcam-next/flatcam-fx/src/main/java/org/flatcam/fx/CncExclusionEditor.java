package org.flatcam.fx;

import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.flatcam.cam.gcode.*;
import org.locationtech.jts.geom.*;

/** Common Geometry CNC exclusions. Stored drafts remain visible when disabled. */
final class CncExclusionEditor {
    @FunctionalInterface interface AreaSelector { void select(boolean polygon,Consumer<Geometry> selected,Runnable cancelled); }
    private final CheckBox enabled=new CheckBox("Ativar exclusoes CNC");
    private final TableView<CncExclusionArea> table=new TableView<>();
    private final TitledPane view;
    CncExclusionEditor(GeometryJobOptions saved,String units,AreaSelector selector,Consumer<Geometry> preview) {
        enabled.setId("cnc-exclusions-enabled"); enabled.setSelected(saved.exclusionsEnabled());
        table.setId("cnc-exclusions"); table.setItems(FXCollections.observableArrayList(saved.exclusions()));
        TableColumn<CncExclusionArea,String> number=new TableColumn<>("#");
        number.setCellValueFactory(c -> new ReadOnlyStringWrapper(Integer.toString(table.getItems().indexOf(c.getValue())+1)));
        TableColumn<CncExclusionArea,String> strategyCol=new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().strategy().name()));
        TableColumn<CncExclusionArea,String> height=new TableColumn<>("Over Z");
        height.setCellValueFactory(c -> new ReadOnlyStringWrapper(Double.toString(c.getValue().overZ())));
        table.getColumns().addAll(number,strategyCol,height); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(130); table.setPlaceholder(new Label("Nenhuma area definida"));
        var strategy=new ComboBox<CncExclusionArea.Strategy>(FXCollections.observableArrayList(CncExclusionArea.Strategy.values()));
        strategy.setId("cnc-exclusion-strategy"); strategy.setValue(CncExclusionArea.Strategy.AROUND);
        var over=new TextField("20"); over.setId("cnc-exclusion-over-z"); over.setPrefColumnCount(5);
        over.disableProperty().bind(strategy.valueProperty().isNotEqualTo(CncExclusionArea.Strategy.OVER));
        var error=new Label(); error.getStyleClass().add("form-error-label"); error.setWrapText(true);
        Runnable clearError=()->error.setText("");
        Consumer<Geometry> add=shape -> {
            try {
                if(table.getItems().size()>=100)throw new IllegalArgumentException("Maximo de 100 areas.");
                table.getItems().add(CncExclusionArea.of(shape,strategy.getValue(),parse(over)));
                enabled.setSelected(true); table.getSelectionModel().selectLast(); clearError.run();
            } catch(IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
        };
        var rectangle=new Button("Desenhar retangulo"); rectangle.setId("cnc-exclusion-draw-rectangle");
        var polygon=new Button("Desenhar poligono"); polygon.setId("cnc-exclusion-draw-polygon");
        rectangle.setDisable(selector==null); polygon.setDisable(selector==null);
        rectangle.setOnAction(event -> selector.select(false,add,()->error.setText("Desenho cancelado.")));
        polygon.setOnAction(event -> selector.select(true,add,()->error.setText("Desenho cancelado.")));
        TextField x1=new TextField("0"), y1=new TextField("0"), x2=new TextField("1"), y2=new TextField("1");
        var fields=List.of(x1,y1,x2,y2); var ids=List.of("x1","y1","x2","y2");
        for(int i=0;i<fields.size();i++) { fields.get(i).setPrefColumnCount(4); fields.get(i).setMinWidth(0); fields.get(i).setId("cnc-exclusion-"+ids.get(i)); }
        var addRect=new Button("Adicionar retangulo numerico"); addRect.setId("cnc-exclusion-add");
        addRect.setOnAction(event -> { try { add.accept(new GeometryFactory().toGeometry(new Envelope(parse(x1),parse(x2),parse(y1),parse(y2)))); }
            catch(IllegalArgumentException invalid) { error.setText(invalid.getMessage()); } });
        var grid=new GridPane(); grid.setHgap(6); grid.setVgap(6);
        grid.addRow(0,new Label("X1:"),x1,new Label("Y1:"),y1); grid.addRow(1,new Label("X2:"),x2,new Label("Y2:"),y2);
        var numeric=new TitledPane("Retangulo por coordenadas ("+units+")",new VBox(6,grid,addRect)); numeric.setExpanded(false);
        var apply=new Button("Aplicar estrategia a area selecionada"); apply.setId("cnc-exclusion-apply");
        apply.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        apply.setOnAction(event -> { try {
            int index=table.getSelectionModel().getSelectedIndex(); var old=table.getItems().get(index);
            table.getItems().set(index,new CncExclusionArea(old.wkt(),strategy.getValue(),parse(over))); clearError.run();
        } catch(IllegalArgumentException invalid) { error.setText(invalid.getMessage()); } });
        var delete=new Button("Excluir area"); delete.setId("cnc-exclusion-delete");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(event -> table.getItems().remove(table.getSelectionModel().getSelectedItem()));
        table.getSelectionModel().selectedItemProperty().addListener((obs,was,area) -> {
            if(area!=null) { strategy.setValue(area.strategy()); over.setText(Double.toString(area.overZ())); }
            preview.accept(area==null?null:area.geometry());
        });
        ToolDescriptions.apply(enabled,"Exclusoes CNC","Ativa areas proibidas para cortes. Desmarcar conserva areas para edicao, mas nao as usa no G-code. Apenas fresagem sem sonda; perfis incompatíveis recusam exclusoes ativas.\n\nAtenção: configure todas as fixacoes e confira o G-code; nao modela macros, hastes, cabecote ou limites da maquina.");
        ToolDescriptions.apply(strategy,"Estrategia de deslocamento","Around contorna as areas em XY. Over retrai antes do deslocamento e passa acima, usando o maior Over Z encontrado no trajeto. Ambas recusam cortes dentro da area.\n\nO FX acrescenta raio da ferramenta e margem de 0.1 mm (ou equivalente em in). Troca, retorno entre passes e estacionamento tambem sao verificados.");
        ToolDescriptions.apply(over,"Altura Over Z","Altura positiva absoluta, na unidade do objeto. Nunca reduz Travel Z. A subida ocorre antes de XY; volta a altura segura apenas no destino fora da exclusao.\n\nAtenção: deve superar grampos, com folga, sem exceder o curso da maquina.");
        Label note=new Label("Comum a todas as ferramentas. Cortes na area sao recusados; Over Z e alturas precisam de conferencia fisica. Salve em .fcnproj; exportacao de projeto Python com exclusoes e bloqueada para evitar perda silenciosa."); note.setWrapText(true);
        view=new TitledPane("Areas de exclusao CNC",new VBox(8,enabled,table,new HBox(8,new Label("Strategy:"),strategy,new Label("Over Z:"),over),
                new FlowPane(8,6,rectangle,polygon),numeric,apply,delete,note,error));
        view.setExpanded(!saved.exclusions().isEmpty());
    }
    TitledPane view() { return view; }
    GeometryJobOptions applyTo(GeometryJobOptions positions) { return positions.withExclusions(enabled.isSelected(),List.copyOf(table.getItems())); }
    private static double parse(TextField field) {
        try { double value=Double.parseDouble(field.getText().trim().replace(',','.')); if(!Double.isFinite(value))throw new NumberFormatException(); return value; }
        catch(NumberFormatException invalid) { throw new IllegalArgumentException("Informe uma coordenada/altura finita."); }
    }
}
