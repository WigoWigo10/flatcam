package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class IcpContractTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test void geometryKeepsNativeVelocitiesAutomaticChangesAndSafeEndWithoutClearingOrigin() {
        var a = FACTORY.createLineString(new Coordinate[]{new Coordinate(3,7),new Coordinate(6,7)});
        var b = FACTORY.createLineString(new Coordinate[]{new Coordinate(10,12),new Coordinate(10,16)});
        var p = new GeometryGCodeParameters(3, .9, true, .3, 120, 10000, false, 900,
                null, 60, true, .25, false, 0)
                .withJobOptions(new GeometryJobOptions(12., .5, 25., 2., 15., -2., -3.));
        var result = GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(.4,a),new ToolGeometry(.8,b)),
                p, Map.of(), CancellationToken.none(), GCodePreprocessor.ISEL_ICP_CNC);
        assertContract(result.gcode(), "GETTOOL 1", "GETTOOL 2");
        assertTrue(result.gcode().contains("VEL 1000\nMOVEABS Z-300\n"));
        assertTrue(result.gcode().contains("VEL 2000\nMOVEABS X6000 Y7000\n"));
        assertNull(GCodeToolpathParser.parse(result.gcode(),CancellationToken.none(),ignored->{}).warning());
    }

    @Test void sparseToolsDrillPecksSlotsAndPreviewAcceptIndependentRapidVelocity() {
        var image=ExcellonImage.of("MM",Map.of(2,1.,7,2.),
                List.of(new ExcellonImage.Drill(2,3,7),new ExcellonImage.Drill(7,10,12)),
                List.of(new ExcellonImage.Slot(7,13,14,16,14)),FACTORY.createPolygon());
        var p=new DrillGCodeParameters(3,.9,120,10000,false,true,.3,true,.25,0);
        var options=new GCodeGenerator.DrillJobOptions(false,15,.5,25.,2.,900).withPositions(12.,-2.,-3.);
        String code=GCodeGenerator.generateDrillCncJob(image,Map.of(2,p,7,p),List.of(2,7),options,GCodePreprocessor.ISEL_ICP_CNC).gcode();
        assertContract(code,"GETTOOL 2","GETTOOL 7");
        var parsed=GCodeToolpathParser.parse(code,CancellationToken.none(),ignored->{});
        assertNull(parsed.warning());
        assertEquals(2,parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::drills).sum());
        assertEquals(1,parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::slots).sum());
        String normalized=ControllerProgramCodec.normalizeIcp("IMF_PBL test\nVEL 2000\nFASTVEL 15000\n"
                +"FASTABS Z3000\nMOVEABS Z-500\nPROGEND\n",CancellationToken.none(),ignored->{});
        assertTrue(normalized.contains("F120.0000\n; ICP FASTVEL 15000\n"), normalized);
        assertFalse(normalized.contains("F900")); // FASTVEL must not overwrite the MOVEABS modal feed in the preview.
    }

    @Test void unsupportedUnitsSpeedsAndMalformedRapidCommandsDoNotSilentlyClamp() {
        var profile=GCodePreprocessor.ISEL_ICP_CNC;
        assertThrows(IllegalArgumentException.class,()->profile.finish(new StringBuilder("G0 Z3\n"),900,"IN"));
        for(double speed:new double[]{-.1,Double.NaN,Double.POSITIVE_INFINITY,.00001,1e20})
            assertThrows(IllegalArgumentException.class,()->profile.finish(new StringBuilder("G0 Z3\n"),speed,"MM"));
        assertTrue(profile.finish(new StringBuilder("G0 Z3\n"),0,"MM").contains("FASTVEL 25000\n"));
        for(String command:List.of("FASTVEL 0","FASTVEL -1","FASTVEL 1.5","FASTVEL 2147483648"))
            assertThrows(IllegalArgumentException.class,()->ControllerProgramCodec.normalizeIcp(
                    "IMF_PBL test\n"+command+"\nPROGEND\n",CancellationToken.none(),ignored->{}));
    }

    private static void assertContract(String code,String first,String second) {
        assertTrue(code.startsWith("IMF_PBL flatcam\nFASTVEL 15000\n"),code);
        assertTrue(code.contains("FASTABS Z12000\n"));
        assertTrue(code.contains("FASTABS X-2000 Y-3000\n"+first+"\nFASTABS Z15000\n"),code);
        assertTrue(code.contains(second+"\nFASTABS Z15000\n"),code);
        assertTrue(code.indexOf("SPINDLE OFF")<code.indexOf(first));
        assertEquals(2,code.lines().filter(line->line.equals("WAIT 250")).count());
        assertTrue(code.endsWith("FASTABS X25000 Y2000\nFASTABS Z500\nPROGEND\n"),code);
        assertFalse(code.contains("WPCLEAR")); assertFalse(code.contains("\nM0\n"));
    }
}
