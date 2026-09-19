package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.junit.jupiter.api.Test;

class GCodeGeneratorTest {

    private static ExcellonImage parse(String... lines) {
        return new ExcellonParser().parse(List.of(lines));
    }

    @Test
    void oneDrillCycle() {
        ExcellonImage image = parse("M48", "INCH", "T1C0.0300", "%", "T1", "X1000Y2000", "M30");
        String gcode = GCodeGenerator.generateDrillGCode(image,
                new DrillGCodeParameters(3.0, 1.7, 300, 0, false));

        assertTrue(gcode.startsWith("; Gerado por FlatCAM Next"));
        assertTrue(gcode.contains("G20"), "inch file should select G20");
        assertTrue(gcode.contains("G90"));
        assertTrue(gcode.contains("G0 X0.1000 Y0.2000"));
        assertTrue(gcode.contains("G1 Z-1.7000 F300.0000"));
        assertTrue(gcode.contains("G0 Z3.0000"));
        assertTrue(gcode.trim().endsWith("M30"));
        assertEquals(0, countOccurrences(gcode, "M3 "), "spindleSpeedRpm=0 must omit M3/M5"); // "M3 " so "M30" doesn't match
        assertEquals(0, countOccurrences(gcode, "M5"), "spindleSpeedRpm=0 must omit M3/M5");
        assertEquals(0, countOccurrences(gcode, "M0"), "a single tool never pauses for a tool change");
    }

    @Test
    void metricFileSelectsG21() {
        ExcellonImage image = parse("M48", "METRIC", "T1C0.8", "%", "T1", "X1.0Y2.0", "M30");
        String gcode = GCodeGenerator.generateDrillGCode(image,
                new DrillGCodeParameters(3.0, 1.7, 300, 0, false));
        assertTrue(gcode.contains("G21"));
    }

    @Test
    void multipleToolsPauseAndCycleSpindleBetweenThem() {
        ExcellonImage image = parse(
                "M48", "METRIC", "T1C0.8", "T2C1.0", "%",
                "T1", "X1.0Y1.0", "X2.0Y1.0",
                "T2", "X3.0Y3.0",
                "M30"
        );
        String gcode = GCodeGenerator.generateDrillGCode(image,
                new DrillGCodeParameters(3.0, 1.6, 250, 12000, true));

        assertEquals(2, countOccurrences(gcode, "M3 S12000"), "spindle restarts for each of the 2 tools");
        assertEquals(2, countOccurrences(gcode, "M5"), "spindle stops between tools and at the end");
        assertEquals(1, countOccurrences(gcode, "M0"), "exactly one tool change between 2 tools");
        assertEquals(3, countOccurrences(gcode, "G1 Z-1.6000"), "one plunge per hole across both tools");
        // Tool order follows tool id order, not first-appearance order, so T1's holes precede T2's.
        assertTrue(gcode.indexOf("X1.0000 Y1.0000") < gcode.indexOf("M0"));
        assertTrue(gcode.indexOf("M0") < gcode.indexOf("X3.0000 Y3.0000"));
    }

    @Test
    void slotIsPlungeThenLinearCutThenRetract() {
        ExcellonImage image = parse("M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y1.0G85X5.0Y1.0", "M30");
        String gcode = GCodeGenerator.generateDrillGCode(image,
                new DrillGCodeParameters(2.0, 1.6, 300, 0, false));

        int plungeIndex = gcode.indexOf("G1 Z-1.6000");
        int cutIndex = gcode.indexOf("G1 X5.0000 Y1.0000 F300.0000");
        int retractIndex = gcode.indexOf("G0 Z2.0000", plungeIndex);
        assertTrue(plungeIndex >= 0 && cutIndex > plungeIndex && retractIndex > cutIndex,
                "expected plunge, then linear cut to the slot's end point, then retract");
    }

    @Test
    void rejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(0, 1.6, 300, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(3.0, 0, 300, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(3.0, 1.6, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(3.0, 1.6, 300, -1, false));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
