package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;

class DrillParkingTest {
    @Test void lowEndZIsReachedAfterTheFinalXyInMmAndIn() {
        for (String units:List.of("MM","IN")) {
            double scale=units.equals("IN") ? 1/25.4 : 1;
            var image=ExcellonImage.of(units,Map.of(1,scale),List.of(new ExcellonImage.Drill(1,10*scale,0)),List.of(),new GeometryFactory().createPolygon());
            for (var profile:List.of(GCodePreprocessor.FX_PORTABLE,GCodePreprocessor.GRBL_11_NO_M6)) {
                var params=new DrillGCodeParameters(3*scale,scale,100*scale,1000,false);
                var options=new GCodeGenerator.DrillJobOptions(false,15*scale,.5*scale,0.0,0.0);
                String code=GCodeGenerator.generateDrillCncJob(image,Map.of(1,params),List.of(1),options,profile).gcode();
                String travel=String.format(java.util.Locale.ROOT,"%s Z%.4f",profile.rapid(),3*scale);
                String end=String.format(java.util.Locale.ROOT,"%s Z%.4f",profile.rapid(),.5*scale);
                String zero=units.equals("IN") ? "0.000000" : "0.0000";
                int xy=code.lastIndexOf(profile.rapid()+" X"+zero+" Y"+zero);
                assertTrue(xy>0,code); assertTrue(code.lastIndexOf(travel,xy)>0,code);
                assertTrue(code.indexOf(end)>xy,code);
            }
        }
    }
    @Test void highEndZIsRaisedBeforeXyAndNoXyKeepsDirectEndZ() {
        var image=ExcellonImage.of("MM",Map.of(1,1.0),List.of(new ExcellonImage.Drill(1,10,0)),List.of(),new GeometryFactory().createPolygon());
        var params=new DrillGCodeParameters(3,1,100,0,false);
        String high=GCodeGenerator.generateDrillCncJob(image,Map.of(1,params),List.of(1),new GCodeGenerator.DrillJobOptions(false,15,8,0.0,0.0)).gcode();
        assertTrue(high.contains("G0 Z8.0000\nG0 X0.0000 Y0.0000"),high);
        String direct=GCodeGenerator.generateDrillCncJob(image,Map.of(1,params),List.of(1),new GCodeGenerator.DrillJobOptions(false,15,.5,null,null)).gcode();
        assertFalse(direct.contains("G0 X0.0000 Y0.0000")); assertTrue(direct.contains("G0 Z0.5000"));
    }
    @Test void lastSelectedToolDefinesParkingClearanceNotUnselectedSettings() {
        var image=ExcellonImage.of("MM",Map.of(1,1.0,2,1.0),List.of(new ExcellonImage.Drill(1,10,0),new ExcellonImage.Drill(2,20,0)),List.of(),new GeometryFactory().createPolygon());
        var settings=Map.of(1,new DrillGCodeParameters(3,1,100,0,false),2,new DrillGCodeParameters(7,1,100,0,false));
        String code=GCodeGenerator.generateDrillCncJob(image,settings,List.of(1,2),new GCodeGenerator.DrillJobOptions(false,15,.5,0.0,0.0)).gcode();
        assertTrue(code.contains("G0 Z7.0000\nG0 X0.0000 Y0.0000\nG0 Z0.5000"),code);
    }
}
