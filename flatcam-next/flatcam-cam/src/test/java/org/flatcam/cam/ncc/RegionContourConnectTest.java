package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class RegionContourConnectTest {
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void integratesFinishingWithoutDroppingCutsAndBeatsOrdinaryNearestCncOnASplitRegion(String units) {
        double s=units.equals("IN")?1/25.4:1;var f=new GeometryFactory();
        Geometry area=f.toGeometry(new Envelope(0,24*s,0,18*s)).difference(f.toGeometry(new Envelope(7*s,17*s,2*s,16*s)));
        var ordinary=NccGenerator.paint(units,area,new PaintParameters(List.of(.6*s),.4,0,NccMethod.LINES,true,true,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
        var regional=NccGenerator.paint(units,area,new PaintParameters(List.of(.6*s),.4,0,NccMethod.REGION_LINES,true,true,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
        assertTrue(regional.pathCount()<ordinary.pathCount(),"Integrating finishing should reduce separate cutting sequences");
        Geometry raw=NccGenerator.paint(units,area,new PaintParameters(List.of(.6*s),.4,0,NccMethod.LINES,false,true,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none()).geometry();
        assertEquals(0,raw.difference(regional.geometry().buffer(1e-8*s)).getLength(),1e-7*s,"Original cutting segments must all remain covered");
        var common=new GeometryGCodeParameters(3*s,.1*s,false,0,300*s,10000,false);
        var source=common.withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
        var old=parse(units,ordinary,common);var fresh=parse(units,regional,source);
        assertTrue(fresh.stats().estimatedMinutes()<old.stats().estimatedMinutes(),"Compare actual exported programs: ordinary NEAREST versus regional SOURCE");
        assertTrue(regional.totalLength()<raw.getLength()*1.25,"Short boundary walks must not duplicate long raster passes");
    }
    private static GCodeToolpathParser.Result parse(String units,NccResult paths,GeometryGCodeParameters parameters){
        var parsed=GCodeToolpathParser.parse(GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(paths.toolResults().get(0).toolDiameter(),paths.geometry())),parameters).gcode(),CancellationToken.NONE,ProgressCallback.none());
        assertTrue(parsed.plotAvailable(),parsed.warning());return parsed;
    }
}
