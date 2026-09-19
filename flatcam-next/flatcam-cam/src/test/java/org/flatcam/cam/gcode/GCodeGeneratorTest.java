package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutKind;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.CutoutResult;
import org.flatcam.cam.cutout.CutoutShape;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationResult;
import org.flatcam.cam.isolation.IsolationType;
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
    void selectedToolIdsFiltersWhichToolsAreDrilled() {
        ExcellonImage image = parse(
                "M48", "METRIC", "T1C0.8", "T2C1.0", "%",
                "T1", "X1.0Y1.0",
                "T2", "X3.0Y3.0",
                "M30"
        );
        String gcode = GCodeGenerator.generateDrillGCode(image,
                new DrillGCodeParameters(3.0, 1.6, 250, 0, false), java.util.Set.of(2));

        assertTrue(gcode.contains("X3.0000 Y3.0000"), "the selected tool's hole must be drilled");
        assertTrue(!gcode.contains("X1.0000 Y1.0000"), "the unselected tool's hole must be skipped");
    }

    @Test
    void nullOrEmptySelectedToolIdsMeansAllTools() {
        ExcellonImage image = parse(
                "M48", "METRIC", "T1C0.8", "T2C1.0", "%",
                "T1", "X1.0Y1.0",
                "T2", "X3.0Y3.0",
                "M30"
        );
        DrillGCodeParameters params = new DrillGCodeParameters(3.0, 1.6, 250, 0, false);
        String withNull = GCodeGenerator.generateDrillGCode(image, params, null);
        String withEmpty = GCodeGenerator.generateDrillGCode(image, params, java.util.Set.of());
        for (String gcode : List.of(withNull, withEmpty)) {
            assertTrue(gcode.contains("X1.0000 Y1.0000"));
            assertTrue(gcode.contains("X3.0000 Y3.0000"));
        }
    }

    @Test
    void drillCncJobGeometrySeparatesTravelFromCut() {
        ExcellonImage image = parse(
                "M48", "METRIC", "T1C0.8", "%",
                "T1", "X1.0Y1.0", "X5.0Y1.0",
                "M30"
        );
        CncJobResult result = GCodeGenerator.generateDrillCncJob(image,
                new DrillGCodeParameters(3.0, 1.6, 250, 0, false), null);

        assertTrue(!result.travelGeometry().isEmpty(), "a rapid move connects the two holes");
        // Each hole becomes its own disjoint cut-shape circle (the two holes are 4mm apart,
        // far wider than the 0.8mm tool diameter) - camlib.py's own gcode_parse() does the
        // same thing since a drill doesn't move laterally while cutting.
        assertEquals(2, result.cutGeometry().getNumGeometries());
    }

    @Test
    void isolationCncJobGeometrySeparatesTravelFromCut() throws Exception {
        var gerber = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        IsolationResult isolation = IsolationGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new IsolationParameters(0.02, 1, 0.0, IsolationType.BOTH));

        CncJobResult result = GCodeGenerator.generateIsolationCncJob(isolation,
                new IsolationGCodeParameters(0.1, 0.003, 10, 0), 0.02);

        assertTrue(!result.cutGeometry().isEmpty(), "the isolation rings themselves are the cut geometry");
        // At most one cut ribbon per ring - fewer if two rings' buffered ribbons happen to touch
        // and merge in the union, which unioning can only ever reduce the count by, never increase.
        assertTrue(result.cutGeometry().getNumGeometries() <= isolation.ringCount());
    }

    @Test
    void cutoutMultiDepthRetracesTheWholePathAtEachStep() throws Exception {
        var gerber = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        CutoutResult cutout = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));

        // depthPerPass 0.05 into a cutDepth of 0.12 must step 0.05, 0.10, 0.12 - three passes, not one.
        CncJobResult result = GCodeGenerator.generateCutoutCncJob(cutout,
                new CutoutGCodeParameters(0.1, 0.12, true, 0.05, 10, 0), 0.02);

        assertEquals(1, countOccurrences(result.gcode(), "G1 Z-0.0500"));
        assertEquals(1, countOccurrences(result.gcode(), "G1 Z-0.1000"));
        assertEquals(1, countOccurrences(result.gcode(), "G1 Z-0.1200"));
        assertTrue(result.gcode().trim().endsWith("M30"));
    }

    @Test
    void cutoutSinglePassPlungesStraightToCutDepth() throws Exception {
        var gerber = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        CutoutResult cutout = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));

        CncJobResult result = GCodeGenerator.generateCutoutCncJob(cutout,
                new CutoutGCodeParameters(0.1, 0.12, false, 0.05, 10, 0), 0.02);

        assertEquals(1, countOccurrences(result.gcode(), "G1 Z-0.1200"));
        assertEquals(0, countOccurrences(result.gcode(), "G1 Z-0.0500"), "no intermediate step without multi-depth");
    }

    @Test
    void cutoutTravelBetweenGapArcsStaysShortInsteadOfCrossingTheBoard() throws Exception {
        var gerber = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        // FOUR bridge gaps split the one outline into 4 open arcs. The bug this guards against:
        // GeometryCollection part order after gap-splitting doesn't follow the perimeter, so naively
        // travelling arcs in that raw order produced a travel move straight across the board's
        // diagonal instead of a short hop to the physically nearest arc end.
        CutoutResult cutout = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.05, GapPattern.FOUR));
        double[] bounds = cutout.bounds();
        double boardDiagonal = Math.hypot(bounds[2] - bounds[0], bounds[3] - bounds[1]);
        assertEquals(4, cutout.partCount(), "FOUR gaps must split the one outline into 4 open arcs");

        // Exercises the ordering fix directly, starting from a point already on the board (not the
        // implied machine origin at (0,0), whose own unavoidably-long first hop would otherwise
        // swamp any bounding-box-based measurement of the REST of the hops).
        var ordered = GCodeGenerator.orderedByNearestNeighbor(cutout.geometry(), bounds[0], bounds[1]);
        assertEquals(4, ordered.size());
        for (int i = 1; i < ordered.size(); i++) {
            var previousEnd = ordered.get(i - 1)[ordered.get(i - 1).length - 1];
            var nextStart = ordered.get(i)[0];
            double hop = Math.hypot(nextStart.x - previousEnd.x, nextStart.y - previousEnd.y);
            assertTrue(hop < boardDiagonal * 0.5,
                    "hop " + i + " between adjacent gap arcs should stay local (" + hop + " vs board diagonal " + boardDiagonal + ")");
        }
    }

    @Test
    void rejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(0, 1.6, 300, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(3.0, 0, 300, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(3.0, 1.6, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new DrillGCodeParameters(3.0, 1.6, 300, -1, false));
        assertThrows(IllegalArgumentException.class, () -> new CutoutGCodeParameters(0, 1.6, false, 0, 300, 0));
        assertThrows(IllegalArgumentException.class, () -> new CutoutGCodeParameters(3.0, 0, false, 0, 300, 0));
        assertThrows(IllegalArgumentException.class, () -> new CutoutGCodeParameters(3.0, 1.6, true, 0, 300, 0),
                "depthPerPass must be positive when multiDepth is on");
    }

    @Test
    void isolationTracesEachRingWithPlungeAndRetract() throws Exception {
        var gerber = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        IsolationResult result = IsolationGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new IsolationParameters(0.02, 1, 0.0, IsolationType.BOTH));

        String gcode = GCodeGenerator.generateIsolationGCode(result, new IsolationGCodeParameters(0.1, 0.003, 10, 0));

        assertTrue(gcode.contains("G20"), "inch file should select G20");
        assertEquals(result.ringCount(), countOccurrences(gcode, "G1 Z-0.0030"),
                "one plunge per ring in the result");
        assertTrue(gcode.trim().endsWith("M30"));
        assertEquals(0, countOccurrences(gcode, "M3 "), "spindleSpeedRpm=0 must omit M3/M5");
    }

    @Test
    void isolationRejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new IsolationGCodeParameters(0, 0.003, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new IsolationGCodeParameters(0.1, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new IsolationGCodeParameters(0.1, 0.003, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new IsolationGCodeParameters(0.1, 0.003, 10, -1));
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

    private static java.nio.file.Path findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        while (dir != null) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/gerber_files"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (no ancestor has tests/gerber_files)");
    }
}
