package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.*;

class ToolChangeContractTest {
    private static final GeometryFactory FACTORY=new GeometryFactory();

    @ParameterizedTest @EnumSource(value=GCodePreprocessor.class,names={"TOOLCHANGE_MANUAL","TOOLCHANGE_CUSTOM"})
    void geometryAndSparseDrillingKeepChangeHeightsAndParkingInBothUnits(GCodePreprocessor profile) {
        for(String units:List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            var a=FACTORY.createLineString(new Coordinate[]{new Coordinate(3*s,7*s),new Coordinate(6*s,7*s)});
            var b=FACTORY.createLineString(new Coordinate[]{new Coordinate(10*s,12*s),new Coordinate(10*s,16*s)});
            var p=new GeometryGCodeParameters(3*s,.6*s,true,.2*s,200*s,10000,true,900*s,
                    null,50*s,true,.25,false,0).withJobOptions(new GeometryJobOptions(12*s,.5*s,25*s,2*s,15*s,-2*s,-3*s));
            String geometry=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(.4*s,a),new ToolGeometry(.8*s,b)),
                    p,Map.of(),CancellationToken.none(),profile).gcode();
            assertContract(geometry,profile,s,2);
            assertNull(GCodeToolpathParser.parse(geometry,CancellationToken.none(),ignored->{}).warning());
            var image=ExcellonImage.of(units,Map.of(2,s,7,2*s),
                    List.of(new ExcellonImage.Drill(2,3*s,7*s),new ExcellonImage.Drill(7,10*s,12*s)),
                    List.of(new ExcellonImage.Slot(7,13*s,14*s,16*s,14*s)),FACTORY.createPolygon());
            var drill=new DrillGCodeParameters(3*s,.6*s,50*s,10000,true,true,.2*s,true,.25,0);
            var options=new GCodeGenerator.DrillJobOptions(true,15*s,.5*s,25*s,2*s,900*s).withPositions(12*s,-2*s,-3*s);
            String drilling=GCodeGenerator.generateDrillCncJob(image,Map.of(2,drill,7,drill),List.of(2,7),options,profile).gcode();
            assertContract(drilling,profile,s,2);
            var parsed=GCodeToolpathParser.parse(drilling,CancellationToken.none(),ignored->{});
            assertNull(parsed.warning());
            assertEquals(List.of(2,7),parsed.stats().tools().stream().map(GCodeToolpathParser.ToolUsage::toolId).toList());
            assertEquals(2,parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::drills).sum());
            assertEquals(1,parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::slots).sum());
        }
    }

    @ParameterizedTest @EnumSource(value=GCodePreprocessor.class,names={"TOOLCHANGE_MANUAL","TOOLCHANGE_CUSTOM"})
    void disablingChangeDoesNotLeavePausesOrMacroCalls(GCodePreprocessor profile) {
        var path=FACTORY.createLineString(new Coordinate[]{new Coordinate(3,7),new Coordinate(6,7)});
        String code=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(.4,path)),
                new GeometryGCodeParameters(3,.5,false,.5,100,10000,false),Map.of(),CancellationToken.none(),profile).gcode();
        assertFalse(code.contains("\nM0\n")); assertFalse(code.contains("\nM6\n"));
        assertFalse(code.contains("G01 Z0 F"));
    }

    private static void assertContract(String code,GCodePreprocessor profile,double s,int tools) {
        assertTrue(code.contains("G00 Z"+GCodeGenerator.fmt(12*s)+"\n"));
        assertFalse(code.contains("M30"));
        assertEquals(tools* (profile==GCodePreprocessor.TOOLCHANGE_MANUAL ? 3 : 0),code.lines().filter(l->l.equals("M0")).count());
        assertEquals(profile==GCodePreprocessor.TOOLCHANGE_CUSTOM ? tools : 0,code.lines().filter(l->l.equals("M6")).count());
        double x=0,y=0,z=0; boolean stopped=false; int phase=0;
        for(String line:code.lines().toList()) {
            if(line.equals("M05")||line.equals("M5")) stopped=true;
            if(line.startsWith("M03 ")) stopped=false;
            if(line.equals("M0")||line.equals("M6")) {
                assertTrue(stopped);
                assertEquals(-2*s,x,.000051); assertEquals(-3*s,y,.000051);
                assertEquals(line.equals("M0") && phase++%3==1 ? 0 : 15*s,z,.000051);
            }
            if(!line.startsWith("G00 ")&&!line.startsWith("G01 ")) continue;
            var words=ControllerProgramCodec.words(line);
            if(words.containsKey('X')) x=words.get('X'); if(words.containsKey('Y')) y=words.get('Y');
            if(words.containsKey('Z')) z=words.get('Z');
        }
        assertEquals(25*s,x,.000051); assertEquals(2*s,y,.000051); assertEquals(.5*s,z,.000051); assertTrue(stopped);
        if(profile==GCodePreprocessor.TOOLCHANGE_MANUAL) {
            assertEquals(tools,code.lines().filter(l->l.equals("G01 Z0 F"+GCodeGenerator.fmt(50*s))).count());
            assertTrue(code.contains("M0\nG90\nG01 Z0"));
        } else assertTrue(code.contains("M6\n(Troca controlada pela macro M6 da maquina)\nG90\nG00 Z"+GCodeGenerator.fmt(15*s)));
    }
}
