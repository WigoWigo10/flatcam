package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.RepeatedTest;
import org.locationtech.jts.geom.*;

class ManualToolChangeRegressionTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static GeometryGCodeParameters params(double scale, double xy, double z) {
        return new GeometryGCodeParameters(3*scale, .5*scale, false, .5*scale, xy*scale,
                10000, true, 0, null, z*scale, false, 0, false, 0);
    }

    @RepeatedTest(10)
    void verticalAdjustmentUsesTheSelectedToolsZFeedNotCommonXyFeed() {
        var path = FACTORY.createLineString(new Coordinate[]{new Coordinate(3,7),new Coordinate(6,7)});
        var tools = List.of(new ToolGeometry(.4,path),new ToolGeometry(.8,path));
        for (String units : List.of("MM","IN")) {
            double scale = units.equals("MM") ? 1 : 1/25.4;
            var base = params(scale,300,40);
            String common = GCodeGenerator.generateGeometryCncJob(units, tools, base, Map.of(),
                    CancellationToken.none(),GCodePreprocessor.TOOLCHANGE_MANUAL).gcode();
            assertEquals(2,common.lines().filter(line->line.equals("G01 Z0 F"+GCodeGenerator.fmt(40*scale))).count(),common);
            String perTool = GCodeGenerator.generateGeometryCncJob(units, tools, base, Map.of(),
                    Map.of(0,params(scale,200,25),1,params(scale,500,60)),
                    CancellationToken.none(),GCodePreprocessor.TOOLCHANGE_MANUAL).gcode();
            assertEquals(1,perTool.lines().filter(line->line.equals("G01 Z0 F"+GCodeGenerator.fmt(25*scale))).count(),perTool);
            assertEquals(1,perTool.lines().filter(line->line.equals("G01 Z0 F"+GCodeGenerator.fmt(60*scale))).count(),perTool);
        }
    }

    @RepeatedTest(10)
    void eachOperatorPauseRestoresAbsoluteModeBeforeItsNextVerticalCommand() {
        String sequence = GCodePreprocessor.TOOLCHANGE_MANUAL.pauseForTool(1,.4,"MM",15,40);
        assertTrue(sequence.contains("M0\nG90\nG01 Z0 F40.0000"),sequence);
        assertTrue(sequence.contains("M0\nG90\nG00 Z15.0000"),sequence);
    }
}
