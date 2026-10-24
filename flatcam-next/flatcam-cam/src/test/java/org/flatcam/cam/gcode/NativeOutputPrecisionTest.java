package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class NativeOutputPrecisionTest {
    @Test void rolandReviewMatchesActualEncodedFeedDepthAndFormattedCoordinates() {
        var f=new GeometryFactory();
        var line=f.createLineString(new Coordinate[]{new Coordinate(10.00125,7),new Coordinate(12.00125,7)});
        var p=new GeometryGCodeParameters(3,.6006,false,1,125,0,false,0,null,75,false,0,false,0);
        String code=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(.8,line)),p,Map.of(),CancellationToken.none(),GCodePreprocessor.ROLAND_MDX_20).gcode();
        assertEquals(126,NativeOutputPrecision.rolandFeed(125,false));
        assertEquals(72,NativeOutputPrecision.rolandFeed(75,false));
        assertEquals(900,NativeOutputPrecision.rolandFeed(0,true));
        assertEquals(-.6,NativeOutputPrecision.coordinate(GCodePreprocessor.ROLAND_MDX_20,"MM",-.6006),1e-12);
        assertEquals(10.0025,NativeOutputPrecision.coordinate(GCodePreprocessor.ROLAND_MDX_20,"MM",10.00125),1e-12);
        assertTrue(code.contains("V2.1;"),code); assertTrue(code.contains("V1.2;"),code);
        assertTrue(code.contains("Z400.1,280.0,-24.0;"),code);
        assertThrows(IllegalArgumentException.class,()->NativeOutputPrecision.coordinate(GCodePreprocessor.ROLAND_MDX_20,"IN",1));
    }
    @Test void hpglReviewMatchesIntegerCommandsInBothSourceUnitsWithoutClipping() {
        var f=new GeometryFactory();
        for(String units:List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            var line=f.createLineString(new Coordinate[]{new Coordinate(10.013*s,7.013*s),new Coordinate(12.013*s,7.013*s)});
            var p=new GeometryGCodeParameters(3*s,s,false,s,100*s,0,false);
            String code=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(.4*s,line)),p,Map.of(),CancellationToken.none(),GCodePreprocessor.HPGL).gcode();
            assertTrue(code.contains("PA401,281;"),code);
            assertEquals(10.025*s,NativeOutputPrecision.coordinate(GCodePreprocessor.HPGL,units,10.013*s),1e-12);
            assertEquals(-819.175*s,NativeOutputPrecision.hpglMinimum(units),1e-12);
            assertEquals(819.2*s,NativeOutputPrecision.hpglMaximum(units),1e-12);
            assertThrows(IllegalArgumentException.class,()->NativeOutputPrecision.coordinate(GCodePreprocessor.HPGL,units,820*s));
        }
    }
}
