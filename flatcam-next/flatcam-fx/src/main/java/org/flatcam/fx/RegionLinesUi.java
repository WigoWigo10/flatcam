package org.flatcam.fx;

import java.util.List;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.gcode.*;

/** Opt-in strategy and persisted CNC ordering defaults, shared by NCC and Paint. */
final class RegionLinesUi {
    static void configure(ComboBox<NccMethod> combo) {
        combo.setMinWidth(0);combo.setPrefWidth(180);combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(NccMethod m){return m==null?"":I18n.caption(m.toString());}
            @Override public NccMethod fromString(String value){return combo.getValue();}
        });
        Runnable help=()-> {
            boolean spiral=combo.getValue()==NccMethod.SPIRAL;
            ToolDescriptions.apply(combo,I18n.text(spiral?"cam.spiral.label":"cam.regionLines.label"),
                    spiral?I18n.text("cam.spiral.help"):I18n.text("ui.f3e1e0c3c5c5")+"\n\n"+I18n.text("cam.regionLines.help"));
        };
        combo.valueProperty().addListener((o,a,b)->help.run());help.run();
    }
    static GeometryGCodeParameters sourceOrder(String units,int toolCount,GeometryGCodeParameters existing) {
        boolean metric="MM".equalsIgnoreCase(units);
        var p=existing!=null?existing:new GeometryGCodeParameters(metric?3:.1,metric?.1:.004,false,metric?.05:.002,metric?300:12,10000,toolCount>1);
        // Imported/custom job positions and exclusion settings survive. Explicit alternate routing wins.
        if(!p.jobOptions().routing().equals(CncRouting.LEGACY))return p;
        return p.withJobOptions(p.jobOptions().withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
    }
    private RegionLinesUi() { }
}
