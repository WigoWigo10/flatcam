package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class ClearingRoutePreviewTest {
    private final GeometryFactory f=new GeometryFactory();
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void sourceAndNearestMatchActualSinglePassGrblXyAndKeepSourceUntouched(String units){
        double s=units.equals("IN")?1/25.4:1;Geometry paths=f.createMultiLineString(new LineString[]{line(s,3,0,4,0),line(s,1,0,2,0)});
        var result=new NccResult(units,paths,f.createPolygon(),List.of(new NccToolResult(.4*s,paths,0)));String before=paths.toText();
        for(boolean source:List.of(false,true)) {
            var preview=ClearingRoutePreview.calculate(result,source,CancellationToken.NONE);
            assertEquals(2,preview.entryCount());assertEquals(2*s,preview.cutLength(),1e-10);
            assertEquals((source?6:2)*s,preview.travelLength(),1e-10);
            assertEquals(2/300.+(source?6:2)/1500.,preview.xyMinutes(),1e-10);
            var parameters=new GeometryGCodeParameters(3*s,.1*s,false,0,300*s,10000,false);
            if(source)parameters=parameters.withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
            var code=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(.4*s,paths)),parameters,Map.of(),CancellationToken.NONE,GCodePreprocessor.GRBL_11_NO_M6);
            var parsed=GCodeToolpathParser.parse(code.gcode(),CancellationToken.NONE,ProgressCallback.none());assertTrue(parsed.plotAvailable());
            assertEquals(preview.cutLength(),parsed.cutCenterlines().getLength(),units.equals("IN")?3e-6:3e-4);
            // A standalone program has no known prior machine XY; the parser cannot draw its first rapid.
            double initial=preview.entries().getGeometryN(0).getCoordinate().distance(new Coordinate(0,0));
            assertEquals(preview.travelLength(),parsed.travelCenterlines().getLength()+initial,units.equals("IN")?3e-6:3e-4);
        }
        assertEquals(before,paths.toText());
    }
    @Test void countsCoincidentEntriesAndKeepsToolContributionsAndCancellation(){
        Geometry a=line(1,0,0,1,0),b=line(1,1,0,2,0),empty=f.createGeometryCollection();
        var result=new NccResult("MM",f.buildGeometry(List.of(a,b)),f.createPolygon(),List.of(new NccToolResult(.8,a,0),new NccToolResult(.6,empty,0),new NccToolResult(.198,b,0)));
        var preview=ClearingRoutePreview.calculate(result,true,CancellationToken.NONE);
        assertEquals(2,preview.entryCount());assertEquals(0,preview.travelLength());assertEquals(2,preview.tools().size());
        assertEquals(.198,preview.tools().get(1).diameter());assertEquals(2,preview.entries().getNumGeometries());
        assertThrows(java.util.concurrent.CancellationException.class,()->ClearingRoutePreview.calculate(result,true,()->true));
    }
    private LineString line(double s,double x1,double y1,double x2,double y2){return f.createLineString(new Coordinate[]{new Coordinate(x1*s,y1*s),new Coordinate(x2*s,y2*s)});}
}
