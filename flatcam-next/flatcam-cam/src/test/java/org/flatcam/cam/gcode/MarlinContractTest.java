package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

/** CNC-configured Marlin contract, not an emulation of a printer's tool changer. */
class MarlinContractTest {
    private static final GeometryFactory FACTORY=new GeometryFactory();
    private static final Pattern WORD=Pattern.compile("([XYZF])([+-]?[0-9.]+)");

    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void geometryUsesNativeFeedModeExplicitFeedsAndFinalBarrierAfterParking(String units) {
        double s=units.equals("MM") ? 1 : 1/25.4;
        var first=new ToolGeometry(s,line(10*s,0,12*s,0));
        var second=new ToolGeometry(2*s,line(20*s,2*s,22*s,2*s));
        var positions=new GeometryJobOptions(12*s,.5*s,25*s,2*s,15*s,-2*s,-3*s);
        var p=new GeometryGCodeParameters(3*s,.9*s,true,.3*s,200*s,10000,true,900*s,
                null,75*s,true,.25,false,0).withJobOptions(positions);
        var result=GCodeGenerator.generateGeometryCncJob(units,List.of(first,second),p,Map.of(),
                CancellationToken.none(),GCodePreprocessor.MARLIN);
        assertContract(result.gcode(),s,900*s,2);
        assertTrue(GCodePreprocessor.GRBL_11_NO_M6.usesG94());
        assertFalse(GCodePreprocessor.MARLIN.usesG94());
        assertNull(GCodeToolpathParser.parse(result.gcode(),CancellationToken.none(),ignored->{}).warning());
    }

    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void drillingPeckSlotsAndTwoToolsPreserveChangeHeightAndParking(String units) {
        double s=units.equals("MM") ? 1 : 1/25.4;
        var image=ExcellonImage.of(units,Map.of(2,s,7,2*s),
                List.of(new ExcellonImage.Drill(2,10*s,0),new ExcellonImage.Drill(7,20*s,2*s)),
                List.of(new ExcellonImage.Slot(7,21*s,2*s,22*s,2*s)),FACTORY.createPolygon());
        var p=new DrillGCodeParameters(3*s,.9*s,100*s,10000,true,true,.3*s,true,.25,0);
        var options=new GCodeGenerator.DrillJobOptions(true,15*s,.5*s,25*s,2*s,900*s)
                .withPositions(12*s,-2*s,-3*s);
        var result=GCodeGenerator.generateDrillCncJob(image,Map.of(2,p,7,p),List.of(2,7),options,GCodePreprocessor.MARLIN);
        assertContract(result.gcode(),s,900*s,2);
        assertEquals(2,image.drills().size()); assertEquals(1,image.slots().size());
    }

    private static LineString line(double x1,double y1,double x2,double y2) {
        return FACTORY.createLineString(new Coordinate[]{new Coordinate(x1,y1),new Coordinate(x2,y2)});
    }

    private static void assertContract(String code,double s,double rapid,int expectedPauses) {
        assertFalse(code.lines().anyMatch(line->line.equals("G94")),code);
        assertFalse(code.contains("M30")); assertTrue(code.endsWith("M400\n"));
        double x=0,y=0,z=0; boolean stopped=false; int pauses=0;
        for(String line:code.lines().toList()) {
            if(line.equals("M5")) stopped=true;
            if(line.startsWith("M3")) stopped=false;
            if(line.equals("M0")) {
                pauses++; assertTrue(stopped,"Spindle must be explicitly stopped before a tool-change pause");
                assertEquals(15*s,z,.000051,"Start Z must not replace the requested tool-change height");
                assertEquals(-2*s,x,.000051); assertEquals(-3*s,y,.000051);
            }
            if(!line.startsWith("G0 ") && !line.startsWith("G1 ")) continue;
            var words=WORD.matcher(line); Double feed=null;
            while(words.find()) switch(words.group(1)) {
                case "X" -> x=Double.parseDouble(words.group(2));
                case "Y" -> y=Double.parseDouble(words.group(2));
                case "Z" -> z=Double.parseDouble(words.group(2));
                case "F" -> feed=Double.parseDouble(words.group(2));
            }
            assertNotNull(feed,"Every movement declares F, independent of the G0/G1 firmware feed mode: "+line);
            if(line.startsWith("G0 ")) assertEquals(rapid,feed,.000051,line);
        }
        assertEquals(expectedPauses,pauses); assertTrue(stopped);
        assertEquals(25*s,x,.000051); assertEquals(2*s,y,.000051); assertEquals(.5*s,z,.000051);
    }
}
