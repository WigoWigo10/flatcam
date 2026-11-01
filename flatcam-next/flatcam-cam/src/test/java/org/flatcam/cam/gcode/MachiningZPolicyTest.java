package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonParser;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKTReader;

class MachiningZPolicyTest {
    @Test void standardNormalizesSignsAndRejectsZeroAndNonfiniteValues() {
        var standard=MachiningZPolicy.STANDARD;
        assertEquals(-.2,standard.cutZ(.2)); assertEquals(-.2,standard.cutZ(-.2));
        assertEquals(2,standard.travelZ(-2));
        assertThrows(IllegalArgumentException.class,()->standard.cutZ(0));
        assertThrows(IllegalArgumentException.class,()->standard.travelZ(0));
        for(var mode:MachiningZPolicy.values()) {
            assertThrows(IllegalArgumentException.class,()->mode.cutZ(Double.NaN));
            assertThrows(IllegalArgumentException.class,()->mode.travelZ(Double.POSITIVE_INFINITY));
        }
    }
    @Test void machinistPassesReachTheSignedTargetIncludingZeroWithoutSignCorruption() throws Exception {
        var path=new WKTReader().read("LINESTRING (1 0,2 0)");
        var base=new GeometryGCodeParameters(3,.1,false,.4,100,0,false);
        var positive=base.withZCoordinates(-.1,1.1,true,.4,MachiningZPolicy.MACHINIST);
        String code=GCodeGenerator.generateGeometryCncJob("MM",path,positive,1,CancellationToken.none()).gcode();
        for(String z:List.of("Z0.4000","Z0.8000","Z1.1000")) assertTrue(code.contains("G1 "+z),code);
        assertTrue(code.contains("G0 Z-0.1000"),code); assertFalse(code.contains("Z--"));
        var parsed=GCodeToolpathParser.parse(code,CancellationToken.none(),ignored->{});
        assertTrue(parsed.plotAvailable()); assertEquals(3,parsed.cutCenterlines().getLength(),1e-9);
        var zero=base.withZCoordinates(0,0,true,.4,MachiningZPolicy.MACHINIST);
        code=GCodeGenerator.generateGeometryCncJob("MM",path,zero,1,CancellationToken.none()).gcode();
        assertTrue(code.contains("G1 Z0.0000"),code); assertFalse(code.contains("Z-0.0000"),code);
        assertEquals(1,MachiningPasses.count(0,true,.4,MachiningZPolicy.MACHINIST));
        assertEquals(3,MachiningPasses.count(-1.1,true,.4,MachiningZPolicy.MACHINIST));
    }
    @Test void drillOffsetUsesTheSameSignedCoordinateConventionAndNegativeTravelWorks() {
        var image=new ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y0.0","M30"));
        var p=new DrillGCodeParameters(-.1,-.4,100,0,false,true,.1,false,0,.1,DrillSlotParameters.milling(),MachiningZPolicy.MACHINIST);
        assertEquals(.3,p.effectiveCutZ(),1e-12);
        String code=GCodeGenerator.generateDrillCncJob(image,p,Set.of(1)).gcode();
        for(String z:List.of("Z0.1000","Z0.2000","Z0.3000")) assertTrue(code.contains("G1 "+z),code);
        assertTrue(code.contains("G0 Z-0.1000"),code); assertFalse(code.contains("Z--"));
        var parsed=GCodeToolpathParser.parse(code,CancellationToken.none(),ignored->{});
        assertTrue(parsed.plotAvailable()); assertFalse(parsed.cutGeometry().isEmpty());
        assertEquals(.3,parsed.stats().tools().getFirst().deepestZ(),1e-12);
        assertEquals(1,parsed.stats().tools().getFirst().drills());
        assertThrows(IllegalArgumentException.class,()->new DrillGCodeParameters(2,0,100,0,false));
    }
}
