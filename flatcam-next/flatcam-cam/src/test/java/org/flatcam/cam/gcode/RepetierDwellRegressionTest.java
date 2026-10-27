package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.RepeatedTest;
import org.locationtech.jts.geom.*;

class RepetierDwellRegressionTest {
    @RepeatedTest(10) void quarterSecondUsesMillisecondsInBothUnitsAndOperations() {
        var factory = new GeometryFactory();
        for (var profile : List.of(GCodePreprocessor.REPETIER, GCodePreprocessor.MARLIN)) {
            for (String units : List.of("MM", "IN")) {
                double s = units.equals("MM") ? 1 : 1 / 25.4;
                var p = new GeometryGCodeParameters(3*s, s, false, s, 200*s, 128,
                        true, 900*s, null, 75*s, true, .25, false, 0);
                var path = factory.createLineString(new Coordinate[]{new Coordinate(10*s, 0), new Coordinate(12*s, 0)});
                String geometry = GCodeGenerator.generateGeometryCncJob(units, List.of(new ToolGeometry(s, path)),
                        p, Map.of(), CancellationToken.none(), profile).gcode();
                var image = ExcellonImage.of(units, Map.of(1, s), List.of(new ExcellonImage.Drill(1, 10*s, 0)),
                        List.of(), factory.createPolygon());
                String drill = GCodeGenerator.generateDrillCncJob(image,
                        Map.of(1, new DrillGCodeParameters(3*s, s, 100*s, 128, true, false, s, true, .25, 0)),
                        List.of(1), new GCodeGenerator.DrillJobOptions(true, 15*s, 5*s, null, null, 900*s), profile).gcode();
                for (String code : List.of(geometry, drill)) {
                    assertTrue(code.contains("G4 P250.0000"), profile + " " + units + "\n" + code);
                    assertFalse(code.lines().anyMatch(line -> line.equals("G94")), profile + "\n" + code);
                }
            }
        }
        for (var profile : List.of(GCodePreprocessor.MARLIN_LASER_FAN_PIN, GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN))
            assertEquals("G4 P250.0000", profile.dwellCode(.25));
        assertEquals("G4 P0.2500", GCodePreprocessor.BERTA_CNC.dwellCode(.25));
    }
}
