package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class IselContractTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static final Pattern WORD=Pattern.compile("([XYZ])([+-]?[0-9.]+)");
    @Test void geometryChangesAtRequestedHeightAndDistinctXYBeforeParkingAndProgramEnd() {
        var path=F.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var other=F.createLineString(new Coordinate[]{new Coordinate(20,2),new Coordinate(22,2)});
        var positions=new GeometryJobOptions(12.,.5,25.,2.,15.,-2.,-3.);
        var parameters=new GeometryGCodeParameters(3,.9,true,.3,200,10000,true,0,null,75,true,.25,false,0)
                .withJobOptions(positions);
        var result=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,path),new ToolGeometry(2,other)),
                parameters,Map.of(),CancellationToken.none(),GCodePreprocessor.ISEL_CNC);
        assertSequence(result.gcode());
        assertEquals(2,result.gcode().lines().filter(l->l.equals("G4 P250.0000")).count());
    }
    @Test void drillsSlotsAndSparseToolNumbersPreserveMachiningAndChangePositions() {
        var image=ExcellonImage.of("MM",Map.of(2,1.,128,2.),
                List.of(new ExcellonImage.Drill(2,10,0),new ExcellonImage.Drill(128,20,2)),
                List.of(new ExcellonImage.Slot(128,21,2,22,2)),F.createPolygon());
        var p=new DrillGCodeParameters(3,.9,100,10000,true,true,.3,true,.25,0);
        var options=new GCodeGenerator.DrillJobOptions(true,15,.5,25.,2.).withPositions(12.,-2.,-3.);
        var result=GCodeGenerator.generateDrillCncJob(image,Map.of(2,p,128,p),List.of(2,128),options,GCodePreprocessor.ISEL_CNC);
        assertSequence(result.gcode());
        assertTrue(result.gcode().contains("T128\n"));
        assertEquals(2,result.gcode().lines().filter(l->l.equals("G4 P250.0000")).count());
        var parsed=GCodeToolpathParser.parse(result.gcode(),CancellationToken.none(),ignored->{});
        assertNull(parsed.warning()); assertEquals(2,parsed.stats().tools().size());
        assertEquals(2,image.drills().size()); assertEquals(1,image.slots().size());
    }
    @Test void toolRangeDwellBoundsAndDisabledChangeAreNotSilentlyIgnored() {
        assertEquals("T128",GCodePreprocessor.ISEL_CNC.selectTool(128));
        for(int id:new int[]{0,-1,129}) {
            assertThrows(IllegalArgumentException.class,()->GCodePreprocessor.ISEL_CNC.selectTool(id));
            assertThrows(IllegalArgumentException.class,()->GCodePreprocessor.ISEL_CNC.pauseForTool(id,1,"MM"));
        }
        assertEquals("T129",GCodePreprocessor.DEFAULT.selectTool(129));
        assertEquals("G4 P1500.0000",GCodePreprocessor.ISEL_CNC.dwellCode(1.5));
        assertEquals("G4 P1.5000",GCodePreprocessor.GRBL_11_NO_M6.dwellCode(1.5));
        assertEquals("G4 P0.0000",GCodePreprocessor.ISEL_CNC.dwellCode(0));
        assertThrows(IllegalArgumentException.class,()->GCodePreprocessor.ISEL_CNC.dwellCode(Double.MAX_VALUE));
        var image=ExcellonImage.of("MM",Map.of(1,1.),List.of(new ExcellonImage.Drill(1,10,0)),List.of(),F.createPolygon());
        var code=GCodeGenerator.generateDrillCncJob(image,Map.of(1,new DrillGCodeParameters(3,1,100,10000,false)),List.of(1),
                new GCodeGenerator.DrillJobOptions(false,15,5,null,null),GCodePreprocessor.ISEL_CNC).gcode();
        assertFalse(code.lines().anyMatch(l->l.equals("M01")||l.equals("M06")));
        assertTrue(code.endsWith("M30\n"));
    }
    private static void assertSequence(String code) {
        assertTrue(code.contains("G71\nG90")); assertTrue(code.contains("G94\n"));
        assertTrue(code.endsWith("M30\n"));
        double x=0,y=0,z=0; int changes=0,stops=0; boolean stopped=false;
        for(String line:code.lines().toList()) {
            if(line.equals("M05")) stopped=true;
            if(line.startsWith("M03")) stopped=false;
            if(line.equals("M06")||line.equals("M01")) {
                assertTrue(stopped); assertEquals(15,z,1e-9); assertEquals(-2,x,1e-9); assertEquals(-3,y,1e-9);
                if(line.equals("M06")) changes++; else stops++;
            }
            if(!line.startsWith("G00 ")&&!line.startsWith("G01 "))continue;
            double beforeZ=z; var words=WORD.matcher(line);
            while(words.find()) switch(words.group(1)) {
                case "X" -> x=Double.parseDouble(words.group(2));
                case "Y" -> y=Double.parseDouble(words.group(2));
                case "Z" -> z=Double.parseDouble(words.group(2));
            }
            if(line.startsWith("G00 ")&&(line.contains(" X")||line.contains(" Y")))
                assertTrue(Math.min(beforeZ,z)>=3,"End Z must not be lowered before XY: "+line);
        }
        assertEquals(2,changes); assertEquals(2,stops); assertTrue(stopped);
        assertEquals(25,x,1e-9); assertEquals(2,y,1e-9); assertEquals(.5,z,1e-9);
    }
}
