package org.flatcam.fx;

import java.util.List;
import java.util.ArrayList;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.flatcam.cam.gcode.CncRouting;
import org.locationtech.jts.geom.*;

final class CncRoutingFields {
    record Reference(String name,String units,Geometry area) { public String toString() { return name; } }
    private final ComboBox<CncRouting.Mode> mode=new ComboBox<>();
    private final ComboBox<Reference> reference=new ComboBox<>();
    private final CheckBox direction=new CheckBox("Preservar direção dos caminhos abertos");
    private final CheckBox inner=new CheckBox("Contornos internos/fechados primeiro");
    private final TextField margin=new TextField("0");
    private final TitledPane view;
    private final CncRouting saved;
    CncRoutingFields(String units,CncRouting saved,List<Reference> references) {
        this.saved=saved;
        mode.getItems().setAll(CncRouting.Mode.values()); mode.setValue(saved.mode()); mode.setId("cnc-route-mode");
        mode.setConverter(new javafx.util.StringConverter<>() {
            public String toString(CncRouting.Mode value) { return switch(value) { case SOURCE -> "Ordem original"; case NEAREST -> "Por proximidade"; case BOARDS -> "Terminar cada placa (por fresa)"; }; }
            public CncRouting.Mode fromString(String value) { throw new UnsupportedOperationException(); }
        });
        reference.getItems().setAll(references.stream().filter(r -> units.equalsIgnoreCase(r.units())).toList());
        reference.setId("cnc-route-reference"); margin.setId("cnc-route-margin"); margin.setPrefColumnCount(6);
        direction.setId("cnc-route-direction"); inner.setId("cnc-route-inner-first");
        direction.setSelected(saved.preserveDirection()); inner.setSelected(saved.innerFirst()); margin.setText(Double.toString(saved.boardMargin()));
        reference.disableProperty().bind(mode.valueProperty().isNotEqualTo(CncRouting.Mode.BOARDS));
        margin.disableProperty().bind(reference.disableProperty());
        direction.disableProperty().bind(mode.valueProperty().isEqualTo(CncRouting.Mode.SOURCE));
        inner.disableProperty().bind(direction.disableProperty());
        ToolDescriptions.apply(mode,"Ordem dos trajetos","Original mantém a ordem/entrada da Geometry. Proximidade visita o próximo início mais perto. Terminar cada placa agrupa por caixas de uma Geometry de área, por fresa; não executa todas as fresas de uma placa antes das demais. Pode aumentar deslocamentos. Contornos fechados podem começar em outro vértice sem inverter sua direção.");
        ToolDescriptions.apply(reference,"Referência das placas","Escolha uma Geometry de área preenchida, com um polígono por placa, na mesma origem e unidade. Use a área da borda panelizada, não ilhas de cobre. A referência é capturada neste painel e salva como geometria; trajetos fora das caixas ou em duas caixas são recusados. Sem escolha, reutiliza a referência salva, quando houver.");
        ToolDescriptions.apply(margin,"Margem das caixas","Expansão extra das caixas da referência na unidade do objeto; o raio da fresa já é incluído. Use só a folga necessária para o offset externo. Margem excessiva pode tornar placas vizinhas ambíguas.");
        ToolDescriptions.apply(direction,"Direção","Não inverte um caminho aberto para reduzir deslocamento. Preserva a orientação que define Climb/Conventional; contornos fechados sempre conservam a direção, mudando apenas o vértice inicial.");
        ToolDescriptions.apply(inner,"Prioridade de recortes","Anéis fechados contidos em outro anel são visitados antes dele; contornos fechados precedem segmentos abertos com pontes. Use para recorte, não automaticamente para todo NCC/Isolation. Não reconstrói perfis abertos internos nem certifica fixação da peça.");
        GridPane grid=new GridPane(); grid.setHgap(8); grid.setVgap(8);
        grid.addRow(0,new Label("Ordem:"),mode); grid.addRow(1,new Label("Área das placas:"),reference);
        grid.addRow(2,new Label("Margem:"),margin); grid.add(direction,0,3,2,1); grid.add(inner,0,4,2,1);
        Label note=new Label("Agrupamento usa uma referência de área; não deduz placas pelo cobre. Uma referência já salva pode ser reutilizada."); note.setWrapText(true); grid.add(note,0,5,2,1);
        view=new TitledPane("Ordem dos trajetos",grid); view.setExpanded(false);
    }
    TitledPane view() { return view; }
    CncRouting options() {
        List<String> boards=List.of();
        if (mode.getValue()==CncRouting.Mode.BOARDS) {
            if (reference.getValue()==null) boards=saved.boardAreas();
            else { List<String> result=new ArrayList<>(); collect(reference.getValue().area(),result); boards=List.copyOf(result); }
        }
        return new CncRouting(mode.getValue(),direction.isSelected(),inner.isSelected(),boards,
                mode.getValue()==CncRouting.Mode.BOARDS ? Double.parseDouble(margin.getText().trim().replace(',','.')) : 0);
    }
    static boolean isArea(Geometry g) { return g instanceof Polygon || g instanceof MultiPolygon; }
    private static void collect(Geometry g,List<String> output) {
        if (g instanceof Polygon p) output.add(p.toText());
        else if (g instanceof MultiPolygon) for(int i=0;i<g.getNumGeometries();i++) collect(g.getGeometryN(i),output);
        else throw new IllegalArgumentException("Referência precisa ser uma área Polygon/MultiPolygon, não contornos ou cobre fragmentado.");
    }
}
