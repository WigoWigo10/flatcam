package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class GCodeToolpathParserTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void parsesGeneratedStyleRapidCutAndDrillWithProgress() {
        List<Double> progress = new ArrayList<>();
        GCodeToolpathParser.Result result = GCodeToolpathParser.parse("""
                ; generated
                G21
                G90
                G94
                G0 Z2
                G0 X1 Y1
                G1 Z-0.2 F100
                G1 X3 Y1
                G0 Z2
                G0 X4 Y1
                M2
                """, () -> false, progress::add);
        assertTrue(result.plotAvailable());
        assertEquals(11, result.lineCount());
        assertTrue(anyPartCovers(result.cutGeometry(), 2, 1));
        assertTrue(anyPartCovers(result.travelGeometry(), 3.5, 1));
        assertTrue(result.cutCenterlines().getNumGeometries() > 0);
        assertTrue(result.travelCenterlines().getNumGeometries() > 0);
        assertEquals(0, progress.get(0));
        assertEquals(1, progress.get(progress.size() - 1));
    }

    @Test
    void joinsDenseContinuousMovesOnlyInDisplayPreview() {
        StringBuilder gcode = new StringBuilder("G21\nG0 X0 Y0\nG1 Z-1\n");
        for (int x = 1; x <= 1_000; x++) {
            gcode.append("G1 X").append(x).append(" Y0\n");
        }
        GCodeToolpathParser.Result result = parse(gcode.toString());

        assertEquals(1_001, result.cutGeometry().getNumGeometries()); // plunge + 1,000 precise buffers
        assertEquals(2, result.cutCenterlines().getNumGeometries()); // plunge point + one joined path
        assertEquals(1_001, result.cutCenterlines().getGeometryN(1).getNumPoints());
    }

    @Test
    void relativeMovesAndUnsupportedModesNeverShowStalePlot() {
        GCodeToolpathParser.Result relative = GCodeToolpathParser.parse("""
                G0 X1 Y1
                G91
                G1 Z-1
                G1 X2 Y0
                """, () -> false, ignored -> {});
        assertTrue(anyPartCovers(relative.cutGeometry(), 2, 1));
        GCodeToolpathParser.Result arc = GCodeToolpathParser.parse("G0 X0 Y0\nG18\nG2 X1 Y1 I0 J1\n",
                () -> false, ignored -> {});
        assertFalse(arc.plotAvailable());
        assertEquals(null, arc.cutGeometry());
    }

    @Test
    void plotsClockwiseAndCounterclockwiseIjArcs() {
        GCodeToolpathParser.Result ccw = parse("G0 X1 Y0\nG1 Z-1\nG3 X-1 Y0 I-1 J0\n");
        GCodeToolpathParser.Result cw = parse("G0 X1 Y0\nG1 Z-1\nG2 X-1 Y0 I-1 J0\n");
        assertTrue(ccw.plotAvailable());
        assertTrue(cw.plotAvailable());
        assertTrue(anyPartCovers(ccw.cutGeometry(), 0, 1));
        assertFalse(anyPartCovers(ccw.cutGeometry(), 0, -1));
        assertTrue(anyPartCovers(cw.cutGeometry(), 0, -1));
        assertFalse(anyPartCovers(cw.cutGeometry(), 0, 1));
    }

    @Test
    void radiusSignChoosesMinorOrMajorArc() {
        GCodeToolpathParser.Result minor = parse("G0 X1 Y0\nG1 Z-1\nG3 X0 Y1 R1\n");
        GCodeToolpathParser.Result major = parse("G0 X1 Y0\nG1 Z-1\nG3 X0 Y1 R-1\n");
        assertTrue(anyPartCovers(minor.cutGeometry(), Math.sqrt(0.5), Math.sqrt(0.5)));
        assertFalse(anyPartCovers(minor.cutGeometry(), 2, 1));
        assertTrue(anyPartCovers(major.cutGeometry(), 2, 1));
    }

    @Test
    void fullCircleAndAbsoluteCenterAreSupported() {
        GCodeToolpathParser.Result circle = parse("G0 X1 Y0\nG1 Z-1\nG3 I-1 J0\n");
        GCodeToolpathParser.Result absolute = parse("G0 X1 Y0\nG1 Z-1\nG90.1\nG3 X-1 Y0 I0 J0\n");
        assertTrue(anyPartCovers(circle.cutGeometry(), 0, 1));
        assertTrue(anyPartCovers(circle.cutGeometry(), 0, -1));
        assertTrue(anyPartCovers(absolute.cutGeometry(), 0, 1));
        assertEquals("MM", absolute.units());
    }

    @Test
    void invalidArcAndMixedUnitsSuppressPreviewWithoutDiscardingText() {
        GCodeToolpathParser.Result radiusMismatch = parse("G0 X1 Y0\nG3 X2 Y0 I-1 J0\n");
        GCodeToolpathParser.Result mixedUnits = parse("G21\nG0 X1 Y0\nG20\nG1 X2 Y0\n");
        GCodeToolpathParser.Result helicalCrossing = parse("G0 X1 Y0\nG3 X-1 Y0 Z-1 I-1 J0\n");
        assertFalse(radiusMismatch.plotAvailable());
        assertFalse(mixedUnits.plotAvailable());
        assertFalse(helicalCrossing.plotAvailable());
        assertEquals(null, radiusMismatch.travelGeometry());
        assertEquals(null, mixedUnits.cutGeometry());
    }

    @Test
    void invalidTextAndCancellationAreReported() {
        assertThrows(IllegalArgumentException.class,
                () -> GCodeToolpathParser.parse("G1 XABC", () -> false, ignored -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> GCodeToolpathParser.parse(" ", () -> false, ignored -> {}));
        assertThrows(CancellationException.class,
                () -> GCodeToolpathParser.parse("G0 X1", () -> true, ignored -> {}));
    }

    private static boolean anyPartCovers(Geometry geometry, double x, double y) {
        for (int index = 0; index < geometry.getNumGeometries(); index++) {
            if (geometry.getGeometryN(index).covers(FACTORY.createPoint(new Coordinate(x, y)))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void reparsedDrillJobKeepsRealHoleSizeToolTableAndMachiningOrder() {
        ExcellonImage image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%",
                "T1", "X1.0Y1.0", "X5.0Y1.0", "T2", "X1.0Y5.0G85X4.0Y5.0", "M30"));
        // Multi-depth: each hole is plunged twice; it must still count as one hole.
        DrillGCodeParameters params = new DrillGCodeParameters(2, 1.5, 100, 0, true, true, 0.8, false, 0, 0);
        String gcode = GCodeGenerator.generateDrillCncJob(image, params, null).gcode();

        GCodeToolpathParser.Result result = parse(gcode);

        assertTrue(result.plotAvailable());
        assertTrue(anyPartCovers(result.cutGeometry(), 5.3, 1),
                "a 0.8 mm hole must be drawn at its real radius, not as a hairline dot");
        GCodeToolpathParser.ToolpathStats stats = result.stats();
        assertEquals(2, stats.tools().size());
        GCodeToolpathParser.ToolUsage t1 = stats.tools().get(0);
        assertEquals(1, t1.toolId());
        assertEquals(0.8, t1.diameter(), 1e-9);
        assertEquals(2, t1.drills());
        assertEquals(0, t1.slots());
        assertEquals(-1.5, t1.deepestZ(), 1e-9);
        GCodeToolpathParser.ToolUsage t2 = stats.tools().get(1);
        assertEquals(2, t2.toolId());
        assertEquals(0, t2.drills());
        assertEquals(1, t2.slots());
        assertTrue(anyPartCovers(t1.cutGeometry(), 5.3, 1), "each tool keeps its own preview");
        assertFalse(anyPartCovers(t2.cutGeometry(), 5.3, 1));
        assertTrue(anyPartCovers(t2.cutGeometry(), 2.5, 5), "the slot is routed by T2");
        assertEquals(List.of(
                new GCodeToolpathParser.DrillHit(1, 1, 1, 1),
                new GCodeToolpathParser.DrillHit(2, 1, 5, 1),
                new GCodeToolpathParser.DrillHit(3, 2, 1, 5)), stats.hits());
    }

    @Test
    void reportsTravelledDistanceAndEstimatedTime() {
        GCodeToolpathParser.Result result = parse("""
                G21
                G90
                G0 Z2
                G0 X3 Y4
                G1 Z-1 F60
                G1 X6 Y8
                G0 Z2
                """);
        GCodeToolpathParser.ToolpathStats stats = result.stats();
        assertEquals(5, stats.xyDistance(), 1e-9);
        double expected = 2 / 1500.0 + 3 / 60.0 + 5 / 60.0 + 3 / 1500.0;
        assertEquals(expected, stats.estimatedMinutes(), 1e-9);
    }

    @Test
    void estimatedTimeIsUnknownWhenAFeedMoveHasNoFeedRate() {
        GCodeToolpathParser.Result result = parse("G21\nG0 Z2\nG0 X1 Y1\nG1 Z-1\n");
        assertTrue(Double.isNaN(result.stats().estimatedMinutes()));
    }

    @Test
    void programsWithoutToolMarkersHaveNoToolTableOrDrillSequence() {
        GCodeToolpathParser.Result result = parse("G21\nG0 Z2\nG0 X1 Y1\nG1 Z-1 F100\nG0 Z2\n");
        assertFalse(result.stats().hasTools());
        assertTrue(result.stats().hits().isEmpty());
        assertFalse(anyPartCovers(result.cutGeometry(), 1.2, 1),
                "without a marker the width is unknown, so the preview stays a hairline");
    }

    @Test
    void readsToolsAndDrillHitsFromPythonFlatCamExcellonProgram() {
        String gcode = String.join("\n",
                "(Type: G-code from Excellon)", "G21", "G90", "G00 Z15.0000", "G00 X20.0000 Y92.0000",
                "T1", "(MSG, Change to Tool Dia = 0.8000 ||| Total drills for tool T1 = 2)", "M0",
                "G00 Z15.0000", "G01 F40.00", "M03 S10000.0",
                "G00 X23.4000 Y62.0000", "G01 Z-2.5000", "G00 Z2.0000",
                "G00 X30.0000 Y40.0000", "G01 Z-2.5000", "G00 Z2.0000",
                "M05", "G00 X20.0000 Y92.0000", "");
        GCodeToolpathParser.ToolpathStats stats = parse(gcode).stats();
        org.junit.jupiter.api.Assertions.assertEquals(2, stats.hits().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, stats.hits().get(0).toolId());
        org.junit.jupiter.api.Assertions.assertEquals(23.4, stats.hits().get(0).x(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(2, stats.tools().get(0).drills());
        org.junit.jupiter.api.Assertions.assertEquals(0.8, stats.tools().get(0).diameter(), 1e-9);
    }

    @Test
    void numbersStartAndEndOfEachTravelMoveOfMillingJobLikePython() {
        String gcode = String.join("\n",
                "G21", "G90", "G00 Z2.0", "G00 X0 Y0",
                "G00 X10 Y0", "G01 Z-0.1 F100", "G01 X10 Y5", "G00 Z2.0",
                "G00 X20 Y5", "G01 Z-0.1", "G01 X20 Y9", "G00 Z2.0",
                "G00 X0 Y0", "");
        var marks = parse(gcode).stats().pathMarks();
        // travels: (0,0)->(10,0) = 1,2 ; (10,5)->(20,5) = 3,4 ; (20,9)->(0,0) = 5, (0,0) already numbered.
        org.junit.jupiter.api.Assertions.assertEquals(5, marks.size());
        org.junit.jupiter.api.Assertions.assertEquals(10, marks.get(1).x(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(5, marks.get(4).sequence());
        org.junit.jupiter.api.Assertions.assertEquals(20, marks.get(4).x(), 1e-9);
    }

    @Test
    void recordsDirectionOfCuttingMovesForMillingJobs() {
        var arrows = parse(String.join("\n", "G21", "G90", "G00 Z2.0", "G00 X0 Y0", "G01 Z-0.1 F100",
                "G01 X10 Y0", "G01 X10 Y4", "G00 Z2.0", ""))
                .stats().cutArrows();
        org.junit.jupiter.api.Assertions.assertEquals(2, arrows.size());
        org.junit.jupiter.api.Assertions.assertEquals(5, arrows.get(0).x(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1, arrows.get(0).dx(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1, arrows.get(1).dy(), 1e-9);
    }

    @Test
    void drawsPythonMillingProgramAtTheHeaderToolDiameterWithoutCountingDrills() {
        var result = parse(String.join(System.lineSeparator(), "(Type: G-code from Geometry)",
                "(TOOL DIAMETER: 0.4 mm)", "G21", "G90", "G00 Z2.0", "G00 X0 Y0", "T1",
                "(MSG, Change to Tool Dia = 0.4)", "G01 Z-0.1 F100", "G01 X10 Y0", "G00 Z2.0", ""));
        org.junit.jupiter.api.Assertions.assertEquals(0.4,
                result.cutGeometry().getEnvelopeInternal().getHeight(), 1e-6);
        org.junit.jupiter.api.Assertions.assertTrue(result.stats().hits().isEmpty());
    }

    private static GCodeToolpathParser.Result parse(String gcode) {
        return GCodeToolpathParser.parse(gcode, () -> false, ignored -> {});
    }
}
