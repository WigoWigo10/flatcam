package org.flatcam.cam.gcode;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKTReader;

class GeometryPassStrategyTest {
    final GeometryGCodeParameters base=new GeometryGCodeParameters(3,1.1,true,.4,200,1000,false,0,null,75,false,0,false,0);
    @Test void continuousAlternatesWithoutLateralRapidsWhileDownAndDefaultStillRetracts() throws Exception {
        var line=new WKTReader().read("LINESTRING (10 0,12 0)");
        String continuous=GCodeGenerator.generateGeometryCncJob("MM",line,base.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING),1,CancellationToken.none()).gcode();
        assertTrue(continuous.contains("G1 X12.0000 Y0.0000 F200.0000\nG1 Z-0.8000 F75.0000\nG1 X10.0000 Y0.0000 F200.0000\nG1 Z-1.1000 F75.0000"),continuous);
        int first=continuous.indexOf("G1 Z-0.4000"),last=continuous.indexOf("G1 Z-1.1000");
        assertFalse(continuous.substring(first,last).contains("G0"));
        String original=GCodeGenerator.generateGeometryCncJob("MM",line,base,1,CancellationToken.none()).gcode();
        assertTrue(original.contains("G0 Z3.0000\nG0 X10.0000 Y0.0000\nG1 Z-0.8000"),original);
    }
    @Test void evenPassesTrackTheRealEndForTheNextTravelAndClosedLinesAlsoAlternate() throws Exception {
        var path=new WKTReader().read("MULTILINESTRING ((10 0,12 0),(20 0,22 0))");
        var p=base.withCutting(.8,true,.4).withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        var job=GCodeGenerator.generateGeometryCncJob("MM",path,p,1,CancellationToken.none());
        assertTrue(job.gcode().contains("G1 X10.0000 Y0.0000 F200.0000\nG0 Z3.0000\nG0 X20.0000 Y0.0000"),job.gcode());
        var closed=new WKTReader().read("LINESTRING (10 0,12 0,12 2,10 2,10 0)");
        String code=GCodeGenerator.generateGeometryCncJob("MM",closed,p,1,CancellationToken.none()).gcode();
        assertTrue(code.contains("G1 Z-0.8000 F75.0000\nG1 X10.0000 Y2.0000"),code);
    }
    @Test void incompatibleDirectionExtraCutAndProbeCannotSilentlyChangeTheMode() {
        var p=base.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        assertThrows(IllegalArgumentException.class,() -> p.withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.NEAREST,true,false,List.of(),0))));
        assertThrows(IllegalArgumentException.class,() -> new GeometryGCodeParameters(3,1,true,.4,100,0,false,0,null,50,false,0,true,.1).withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING));
        assertEquals(GeometryPassStrategy.CONTINUOUS_ALTERNATING,p.withCompensation(ToolPathOffset.PATH,0).withCutting(1,true,.2).withJobOptions(GeometryJobOptions.AUTOMATIC).passStrategy());
    }
    @Test void pointUsesOnlyFinalDepthAndCancellationStopsGeneration() throws Exception {
        var point=new WKTReader().read("POINT (10 0)"); var p=base.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        String code=GCodeGenerator.generateGeometryCncJob("MM",point,p,1,CancellationToken.none()).gcode();
        assertFalse(code.contains("Z-0.4000")); assertTrue(code.contains("Z-1.1000"));
        assertThrows(java.util.concurrent.CancellationException.class,() -> GCodeGenerator.generateGeometryCncJob("MM",point,p,1,() -> true));
    }
}
