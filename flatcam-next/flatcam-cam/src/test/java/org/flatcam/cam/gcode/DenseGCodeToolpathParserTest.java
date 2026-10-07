package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class DenseGCodeToolpathParserTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static String straightProgram() {
        StringBuilder code = new StringBuilder("; FCFX MILL D2\nG21\nG0 X0 Y0\nG1 Z-1 F60\n");
        for (int x = 1; x <= 60_000; x++) code.append("G1 X").append(x).append(" Y0\n");
        return code.toString();
    }

    private static String dense(String code) {
        return "; padding\n".repeat(50_001) + code;
    }

    private static GCodeToolpathParser.Result parse(String code) {
        return GCodeToolpathParser.parse(code, () -> false, fraction -> {});
    }

    private static boolean covers(Geometry geometry, double x, double y) {
        var point = FACTORY.createPoint(new Coordinate(x, y));
        for (int i = 0; i < geometry.getNumGeometries(); i++)
            if (geometry.getGeometryN(i).covers(point)) return true;
        return false;
    }

    @Test void parsesBeyondOldLimitWithoutTruncatingRouteOrTotals() {
        String code = straightProgram();
        List<Double> progress = new ArrayList<>();
        var result = GCodeToolpathParser.parse(code, () -> false, progress::add);
        assertTrue(result.plotAvailable(), result.warning());
        assertEquals(60_004, result.lineCount());
        assertEquals(60_000, result.stats().xyDistance());
        assertEquals(60_001 / 60.0, result.stats().estimatedMinutes(), 1e-8);
        assertEquals(2, result.stats().cutterDiameter());
        assertEquals(60_001, result.cutGeometry().getEnvelopeInternal().getMaxX());
        assertTrue(covers(result.cutGeometry(), 59_999, .9));
        assertTrue(result.cutGeometry().getNumGeometries() < 500,
                "bounded buffers replace 60,001 per-move polygons");
        assertEquals(3, result.cutCenterlines().getNumPoints()); // plunge plus two endpoints
        var step = result.stats().steps().getLast();
        assertEquals(120_002, step.xy().length);
        assertEquals(60_000, step.xy()[step.xy().length - 2]);
        assertEquals(60_000, step.length());
        assertEquals(result.stats().estimatedMinutes(), step.endMinutes(), 1e-8);
        assertEquals(0, progress.getFirst());
        assertEquals(1, progress.getLast());
        for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) >= progress.get(i - 1));
        assertTrue(progress.contains(.97), "100% is published after final assembly");
    }

    @Test void keepsBendsBacktrackingRapidsAndDiscontinuities() {
        var result = parse(dense("""
                G21
                G0 X0 Y0
                G1 Z-1 F60
                G1 X10 Y0
                G1 X5 Y0
                G1 X5 Y10
                G0 Z2
                G0 X30 Y10
                G1 Z-1
                G1 X40 Y10
                """));
        assertTrue(result.plotAvailable());
        assertTrue(covers(result.cutGeometry(), 9, 0), "do not remove reversal extrema");
        assertTrue(covers(result.cutGeometry(), 5, 8));
        assertFalse(covers(result.cutGeometry(), 20, 10), "rapid must not become cutting");
        assertTrue(covers(result.travelGeometry(), 20, 10));
        assertEquals(60, result.stats().xyDistance());
        assertEquals(3, result.stats().steps().size());
        assertEquals(40, result.stats().steps().getLast().xy()[2]);
    }

    @Test void keepsPerToolDiametersHolesAndSlots() {
        var result = parse(dense("""
                G21
                ; FCFX TOOL T1 D2
                G0 X0 Y0
                G1 Z-1 F60
                G1 X10 Y0
                G0 Z2
                ; FCFX TOOL T2 D4
                G0 X30 Y0
                G1 Z-2
                G1 X40 Y0
                G0 Z2
                G0 X50 Y0
                G1 Z-2
                G0 Z2
                """));
        assertTrue(result.plotAvailable());
        var first = result.stats().tools().getFirst();
        var second = result.stats().tools().getLast();
        assertTrue(covers(first.cutGeometry(), 5, .9));
        assertFalse(covers(first.cutGeometry(), 5, 1.1));
        assertFalse(covers(first.cutGeometry(), 35, 0));
        assertTrue(covers(second.cutGeometry(), 35, 1.9));
        assertTrue(covers(second.cutGeometry(), 50, 1.9));
        assertEquals(1, first.slots());
        assertEquals(1, second.slots());
        assertEquals(1, second.drills());
        assertEquals(3, result.stats().hits().size());
    }

    @Test void flushesAtMillingWidthChangeAndPreservesArc() {
        var result = parse(dense("""
                G21
                ; FCFX MILL D2
                G0 X0 Y0
                G1 Z-1 F60
                G1 X10 Y0
                ; FCFX MILL D4
                G1 X20 Y0
                G3 X20 Y10 I0 J5
                """));
        assertTrue(result.plotAvailable());
        assertNull(result.stats().cutterDiameter(), "do not stroke mixed widths as a single cutter");
        assertFalse(covers(result.cutGeometry(), 5, 1.5));
        assertTrue(covers(result.cutGeometry(), 15, 1.9));
        assertTrue(covers(result.cutGeometry(), 25, 5));
        assertTrue(result.cutCenterlines().getEnvelopeInternal().getMaxX() >= 25);
    }

    @Test void unknownCommandAfterOldLimitStillSuppressesEntirePreview() {
        var result = parse(straightProgram() + "G92 X0\n");
        assertFalse(result.plotAvailable());
        assertNull(result.cutGeometry());
        assertNull(result.travelGeometry());
        assertNull(result.stats());
    }

    @Test void canCancelDuringReadingAndFinalAssembly() {
        String code = straightProgram();
        for (double threshold : new double[]{.2, .97, 1}) {
            AtomicBoolean cancelled = new AtomicBoolean();
            assertThrows(CancellationException.class, () -> GCodeToolpathParser.parse(code, cancelled::get,
                    fraction -> { if (fraction >= threshold) cancelled.set(true); }));
        }
    }

    @Test void keepsTinyNoncollinearBendsAndInchUnits() {
        var result = parse(dense("G20\nG0 X0 Y0\nG1 Z-0.01 F1\nG1 X1 Y0\nG1 X2 Y0.000000000001\n"));
        assertEquals("IN", result.units());
        assertEquals(4, result.cutCenterlines().getNumPoints()); // point plus three-vertex path
        assertEquals(6, result.stats().steps().getLast().xy().length);
    }

    @Test void keepsEveryBendAcrossBufferAndCenterlineChunkBoundaries() {
        var code = new StringBuilder("; FCFX MILL D0.02\nG21\nG0 X0 Y0\nG1 Z-1 F60\n");
        for (int x = 1; x <= 51_000; x++) code.append("G1 X").append(x).append(" Y").append(x % 2).append('\n');
        var result = parse(code.toString());
        assertTrue(result.plotAvailable());
        assertTrue(covers(result.cutGeometry(), 50_999.5, .5));
        assertEquals(51_000 * Math.sqrt(2), result.stats().xyDistance(), 1e-6);
        assertEquals(102_002, result.stats().steps().getLast().xy().length);
        assertEquals(51_000, result.cutCenterlines().getEnvelopeInternal().getMaxX());
        assertTrue(result.cutCenterlines().getNumPoints() >= 51_002,
                "noncollinear bends are not decimated");
        assertTrue(result.cutGeometry().getNumGeometries() < 500);
    }

    @Test void reportsProgressAndCanCancelEvenInsideCommentOnlyInput() {
        var cancelled = new AtomicBoolean();
        List<Double> progress = new ArrayList<>();
        assertThrows(CancellationException.class, () -> GCodeToolpathParser.parse(dense(""), cancelled::get,
                fraction -> { progress.add(fraction); if (fraction >= .1) cancelled.set(true); }));
        assertTrue(progress.getLast() < .2, "comments must not jump straight to final assembly");
        assertFalse(progress.contains(1.0));
    }
}
