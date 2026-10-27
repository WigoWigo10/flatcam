package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.RepeatedTest;
import org.locationtech.jts.geom.*;

class LaserControllerRegressionTest {
    @RepeatedTest(10) void marlinLaserUsesNativeFeedModeAndWaitsForFinalParking() {
        var factory = new GeometryFactory();
        for (var profile : List.of(GCodePreprocessor.MARLIN_LASER_FAN_PIN, GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)) {
            for (String units : List.of("MM", "IN")) {
                double s = units.equals("MM") ? 1 : 1 / 25.4;
                var path = factory.createLineString(new Coordinate[]{new Coordinate(10*s, 0), new Coordinate(12*s, 0)});
                var p = new GeometryGCodeParameters(3*s, s, false, s, 200*s, 128, false, 900*s);
                String code = GCodeGenerator.generateGeometryCncJob(units, List.of(new ToolGeometry(s, path)),
                        p, Map.of(), CancellationToken.none(), profile).gcode();
                assertAll(profile + " " + units,
                    () -> assertFalse(code.lines().anyMatch(line -> line.equals("G94")), code),
                    () -> assertTrue(code.endsWith("G0 Z" + GCodeGenerator.fmt(3*s) + " F" + GCodeGenerator.fmt(900*s) + "\nM400\n"), code));
            }
        }
    }
}
