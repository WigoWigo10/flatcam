package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

class CncInitialToolChangeTest {
    private final GeometryFactory factory = new GeometryFactory();
    private ExcellonImage holes() {
        return ExcellonImage.of("MM",Map.of(1,1.0,2,2.0),List.of(new ExcellonImage.Drill(1,10,0),
                new ExcellonImage.Drill(2,20,0)),List.of(),factory.createPolygon());
    }
    @ParameterizedTest @EnumSource(value=GCodePreprocessor.class,names={"GRBL_11_NO_M6","GRBL_11","DEFAULT","DEFAULT_NO_M6","MARLIN"})
    void initialDrillingChangeStopsUnknownSpindleAndEveryPauseRestoresAbsoluteHeight(GCodePreprocessor profile) {
        var p = new DrillGCodeParameters(3,1,100,10000,true);
        var options = new GCodeGenerator.DrillJobOptions(true,15,5,null,null);
        String code = GCodeGenerator.generateDrillCncJob(holes(),Map.of(1,p,2,p),List.of(1,2),options,profile).gcode();
        assertTrue(code.indexOf(profile.spindleOff()) < code.indexOf("M0\n"),code);
        assertTrue(code.indexOf(profile.spindleOff()) >= 0,code);
        String[] changes = code.split("(?m)^M0\\s*\\n",-1);
        assertEquals(3,changes.length,code);
        for (int i=1;i<changes.length;i++) {
            var lines = changes[i].stripLeading().lines().limit(3).toList();
            assertEquals("G90",lines.get(0),code);
            assertTrue(lines.get(1).startsWith(profile.rapid()+" Z15.0000"),code);
            assertTrue(lines.get(2).startsWith(profile.rapid()+" Z3.0000"),code);
        }
    }
    @Test void geometryInitialChangeAlsoStopsTheSpindleBeforeThePause() throws Exception {
        var p = new GeometryGCodeParameters(3,1,false,.5,200,10000,true).withJobOptions(
                new GeometryJobOptions(null,5.0,null,null,15.0,null,null));
        String code = GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,
                new WKTReader().read("LINESTRING (10 0,12 0)"))),p,Map.of(),CancellationToken.none(),
                GCodePreprocessor.GRBL_11_NO_M6).gcode();
        assertTrue(code.indexOf("M05") >= 0 && code.indexOf("M05") < code.indexOf("M0\n"),code);
        assertTrue(code.contains("M0\nG90\nG00 Z15.0000\nG00 Z3.0000"),code);
        assertFalse(code.substring(0,code.indexOf("M0\n")).contains("G00 Z3.0000"),code);
    }
    @Test void lowChangeHeightIsRejectedEvenWithoutAnXyChangePositionOrExclusions() {
        var p = new DrillGCodeParameters(3,1,100,10000,true);
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateDrillCncJob(holes(),Map.of(1,p),
                List.of(1),new GCodeGenerator.DrillJobOptions(true,2,5,null,null),GCodePreprocessor.GRBL_11_NO_M6));
        assertDoesNotThrow(()->GCodeGenerator.generateDrillCncJob(holes(),Map.of(1,p),List.of(1),
                new GCodeGenerator.DrillJobOptions(false,2,5,null,null),GCodePreprocessor.GRBL_11_NO_M6));
    }
    @Test void equalTravelAndChangeHeightStillRaisesBeforeTheInitialPause() {
        var p = new DrillGCodeParameters(3,1,100,10000,true);
        String code = GCodeGenerator.generateDrillCncJob(holes(),Map.of(1,p),List.of(1),
                new GCodeGenerator.DrillJobOptions(true,3,5,null,null),GCodePreprocessor.GRBL_11_NO_M6).gcode();
        assertTrue(code.indexOf("G00 Z3.0000") >= 0 && code.indexOf("G00 Z3.0000") < code.indexOf("T1\n"),code);
    }
    @Test void automaticIcpSelectionAlsoRaisesBeforeSelectingTheInitialTool() {
        var p = new DrillGCodeParameters(3,1,100,10000,false);
        String code = GCodeGenerator.generateDrillCncJob(holes(),Map.of(1,p),List.of(1),
                new GCodeGenerator.DrillJobOptions(false,15,5,null,null),GCodePreprocessor.ISEL_ICP_CNC).gcode();
        assertTrue(code.indexOf("FASTABS Z15000") >= 0 && code.indexOf("FASTABS Z15000") < code.indexOf("GETTOOL 1"),code);
    }
    @Test void portableWithoutAnInitialChangeAndZeroRpmKeepsItsManualSpindleContract() {
        String code = GCodeGenerator.generateDrillGCode(holes(),new DrillGCodeParameters(3,1,100,0,false));
        assertFalse(code.contains("M5"),code);
        assertFalse(code.lines().anyMatch(line -> line.matches("M3(?:\\s.*)?")),code);
        assertFalse(code.contains("M0"),code);
    }
}
