package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class LineXyzContractTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @ParameterizedTest @ValueSource(strings = {"MM", "IN"})
    void geometryAndDrillingKeepDistinctChangeCoordinatesPecksAndSafeParking(String units) {
        double s = units.equals("MM") ? 1 : 1 / 25.4;
        var positions = new GeometryJobOptions(12*s, .5*s, 25*s, 2*s, 15*s, -2*s, -3*s);
        var p = new GeometryGCodeParameters(3*s, .9*s, true, .3*s, 200*s, 10000, true, 900*s,
                null, 75*s, true, .25, false, 0).withJobOptions(positions);
        var a = FACTORY.createLineString(new Coordinate[]{new Coordinate(3*s, 7*s), new Coordinate(6*s, 7*s)});
        var b = FACTORY.createLineString(new Coordinate[]{new Coordinate(10*s, 12*s), new Coordinate(10*s, 16*s)});
        String geometry = GCodeGenerator.generateGeometryCncJob(units,
                List.of(new ToolGeometry(.4*s, a), new ToolGeometry(.8*s, b)), p, Map.of(),
                CancellationToken.none(), GCodePreprocessor.LINE_XYZ).gcode();
        assertContract(geometry, s, 2);

        var image = ExcellonImage.of(units, Map.of(2, s, 7, 2*s),
                List.of(new ExcellonImage.Drill(2, 3*s, 7*s), new ExcellonImage.Drill(7, 10*s, 12*s)),
                List.of(new ExcellonImage.Slot(7, 13*s, 14*s, 16*s, 14*s)), FACTORY.createPolygon());
        var drill = new DrillGCodeParameters(3*s, .9*s, 100*s, 10000, true, true, .3*s, true, .25, 0);
        var options = new GCodeGenerator.DrillJobOptions(true, 15*s, .5*s, 25*s, 2*s, 900*s)
                .withPositions(12*s, -2*s, -3*s);
        String drilling = GCodeGenerator.generateDrillCncJob(image, Map.of(2, drill, 7, drill),
                List.of(2, 7), options, GCodePreprocessor.LINE_XYZ).gcode();
        assertContract(drilling, s, 2);
        var parsed = GCodeToolpathParser.parse(drilling, CancellationToken.none(), ignored -> {});
        assertNull(parsed.warning());
        assertEquals(2, parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::drills).sum());
        assertEquals(1, parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::slots).sum());
    }

    private static void assertContract(String code, double s, int expectedPauses) {
        assertTrue(code.contains("\nG90\n") && code.contains("\nG94\n"), code);
        assertFalse(code.contains("NaN"));
        assertFalse(code.contains("M30")); // Generic profile has no controller-specific program-end assumption.
        double x = 43*s, y = 61*s, z = -2*s;
        boolean knownX = false, knownY = false, knownZ = false, stopped = false, afterMacro = false;
        int pauses = 0, dwells = 0;
        for (String line : code.lines().toList()) {
            if (line.equals("M5") || line.equals("M05")) stopped = true;
            if (line.startsWith("M3") || line.startsWith("M03")) stopped = false;
            if (line.equals("M0")) {
                pauses++;
                assertTrue(stopped);
                assertEquals(-2*s, x, .000051); assertEquals(-3*s, y, .000051);
                assertEquals(15*s, z, .000051);
            }
            if (line.equals("M6")) {
                knownX = knownY = knownZ = false;
                afterMacro = true;
            }
            if (line.startsWith("G4 ")) { dwells++; assertEquals("G4 P0.2500", line); }
            if (!line.startsWith("G00 ") && !line.startsWith("G01 ")) continue;
            var words = ControllerProgramCodec.words(line);
            boolean xy = words.containsKey('X') || words.containsKey('Y');
            if (!knownX && !knownY && !knownZ) {
                assertFalse(xy, "Initial and post-M6 clearance must be vertical: " + line);
                assertTrue(words.containsKey('Z'));
                if (afterMacro) assertEquals(15*s, words.get('Z'), .000051);
            }
            if (knownX && knownY && knownZ) {
                assertTrue(words.containsKey('X') && words.containsKey('Y') && words.containsKey('Z'), line);
            }
            if (xy && !knownX && !knownY) assertTrue(z >= 3*s - .000051, "Establish XY only at clearance");
            if (words.containsKey('X')) { x = words.get('X'); knownX = true; }
            if (words.containsKey('Y')) { y = words.get('Y'); knownY = true; }
            if (words.containsKey('Z')) { z = words.get('Z'); knownZ = true; }
        }
        assertEquals(expectedPauses, pauses); assertEquals(2, dwells);
        assertEquals(25*s, x, .000051); assertEquals(2*s, y, .000051); assertEquals(.5*s, z, .000051);
        assertTrue(stopped);
    }
}
