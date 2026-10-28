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
    private final CheckBox direction=new CheckBox(I18n.text("ui.ebd1d27248dd"));
    private final CheckBox inner=new CheckBox(I18n.text("ui.f9d898783353"));
    private final TextField margin=new TextField("0");
    private final TitledPane view;
    private final CncRouting saved;
    CncRoutingFields(String units,CncRouting saved,List<Reference> references) {
        this.saved=saved;
        mode.getItems().setAll(CncRouting.Mode.values()); mode.setValue(saved.mode()); mode.setId("cnc-route-mode");
        mode.setConverter(new javafx.util.StringConverter<>() {
            public String toString(CncRouting.Mode value) { return switch(value) { case SOURCE -> I18n.text("ui.ae32e1eb789c"); case NEAREST -> I18n.text("ui.14b282343678"); case BOARDS -> I18n.text("ui.98e8dc1fff8e"); }; }
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
        ToolDescriptions.apply(mode,I18n.text("ui.8f7cf848f7cb"),I18n.text("ui.cfcc9d2148f7"));
        ToolDescriptions.apply(reference,I18n.text("ui.794a27de2c99"),I18n.text("ui.4977ec759145"));
        ToolDescriptions.apply(margin,I18n.text("ui.e5390a9fb017"),I18n.text("ui.868f178c0c8f"));
        ToolDescriptions.apply(direction,I18n.text("ui.0de75005faf5"),I18n.text("ui.dcb0af4fc86a"));
        ToolDescriptions.apply(inner,I18n.text("ui.01a31c73794f"),I18n.text("ui.f4e0136317ae"));
        GridPane grid=new GridPane(); grid.setHgap(8); grid.setVgap(8);
        grid.addRow(0,new Label(I18n.text("ui.3b41aae0bd17")),mode); grid.addRow(1,new Label(I18n.text("ui.1c6237c8c977")),reference);
        grid.addRow(2,new Label(I18n.text("ui.16bab5b0d1de")),margin); grid.add(direction,0,3,2,1); grid.add(inner,0,4,2,1);
        Label note=new Label(I18n.text("ui.0a0a9587b745")); note.setWrapText(true); grid.add(note,0,5,2,1);
        view=new TitledPane(I18n.text("ui.8f7cf848f7cb"),grid); view.setExpanded(false);
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
        else throw new IllegalArgumentException(I18n.text("ui.20582729b4da"));
    }
}
