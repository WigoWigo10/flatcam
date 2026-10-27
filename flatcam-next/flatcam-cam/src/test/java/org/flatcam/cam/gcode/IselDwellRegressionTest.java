package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class IselDwellRegressionTest {
    @Test void quarterSecondDwellMustBe250MillisecondsInIselGeometryAndDrilling() {
        var factory=new GeometryFactory();
        var p=new GeometryGCodeParameters(3,1,false,1,200,10000,true,0,null,75,true,.25,false,0);
        var path=factory.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var geometry=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,path)),p,
                Map.of(),CancellationToken.none(),GCodePreprocessor.ISEL_CNC).gcode();
        var image=ExcellonImage.of("MM",Map.of(1,1.),List.of(new ExcellonImage.Drill(1,10,0)),
                List.of(),factory.createPolygon());
        var drill=GCodeGenerator.generateDrillCncJob(image,
                Map.of(1,new DrillGCodeParameters(3,1,100,10000,true,false,1,true,.25,0)),List.of(1),
                new GCodeGenerator.DrillJobOptions(true,15,5,null,null),GCodePreprocessor.ISEL_CNC).gcode();
        assertAll(()->assertTrue(geometry.contains("G4 P250.0000"),geometry),
                  ()->assertTrue(drill.contains("G4 P250.0000"),drill));
    }
}
