package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class SpindleZeroContractTest {
    private final GeometryFactory f=new GeometryFactory();
    private static boolean startsSpindle(String code) {
        return code.lines().anyMatch(line -> line.trim().matches("M0?[34](?:\\s.*)?"));
    }
    @Test void zeroRpmNeverCommandsAStartWithOrWithoutRequestedChange() {
        var line=f.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var image=ExcellonImage.of("MM",Map.of(1,1.),List.of(new ExcellonImage.Drill(1,10,0)),List.of(),f.createPolygon());
        for (var pp:List.of(GCodePreprocessor.GRBL_11_NO_M6,GCodePreprocessor.DEFAULT_NO_M6,GCodePreprocessor.MARLIN))
            for (boolean change:List.of(false,true)) {
                String geometry=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,line)),
                        new GeometryGCodeParameters(3,1,false,.5,100,0,change),Map.of(),CancellationToken.none(),pp).gcode();
                String drill=GCodeGenerator.generateDrillCncJob(image,Map.of(1,new DrillGCodeParameters(3,1,100,0,change)),List.of(1),
                        new GCodeGenerator.DrillJobOptions(change,15,5,null,null),pp).gcode();
                for (String code:List.of(geometry,drill)) {
                    assertFalse(startsSpindle(code),code);
                    assertEquals(change,code.lines().anyMatch("M0"::equals),code);
                }
            }
    }
    @Test void aZeroRpmSecondToolCannotInheritAnAutomaticRunningSpindle() {
        var line=f.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var p=new GeometryGCodeParameters(3,1,false,.5,100,10000,true);
        var zero=new GeometryGCodeParameters(3,1,false,.5,100,0,true);
        var image=ExcellonImage.of("MM",Map.of(1,1.,2,2.),List.of(new ExcellonImage.Drill(1,10,0),new ExcellonImage.Drill(2,20,0)),List.of(),f.createPolygon());
        for (var pp:List.of(GCodePreprocessor.GRBL_11_NO_M6,GCodePreprocessor.DEFAULT_NO_M6,GCodePreprocessor.MARLIN)) {
            String geometry=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,line),new ToolGeometry(2,line)),p,
                    Map.of(),Map.of(1,zero),CancellationToken.none(),pp).gcode();
            String drill=GCodeGenerator.generateDrillCncJob(image,Map.of(1,new DrillGCodeParameters(3,1,100,10000,true),
                    2,new DrillGCodeParameters(3,1,100,0,true)),List.of(1,2),new GCodeGenerator.DrillJobOptions(true,15,5,null,null),pp).gcode();
            for (String code:List.of(geometry,drill)) {
                int t2=code.indexOf("T2\n"); assertTrue(t2>0 && startsSpindle(code.substring(0,t2)),code);
                assertTrue(code.lastIndexOf(pp.spindleOff(),t2)>code.indexOf(pp.spindleOn()),code);
                assertFalse(startsSpindle(code.substring(t2)),code);
            }
        }
    }
}
