package org.flatcam.cam.isolation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gcode.CncJobResult;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTReader;
import org.junit.jupiter.api.Test;

/**
 * Differential check against appTools/ToolIsolation.py + camlib.py's
 * Gerber.isolation_geometry(), called directly (not through the UI tool) via
 * the same tests/parser_baseline.py stub Fase 0 uses, for these exact cases:
 *
 * <pre>
 * simple1.gbr,              tool=0.02, passes=1            -> total_len=3.8269347087031136, bounds=(2.408, 0.817, 2.97362, 1.26101), rings=2
 * simple1.gbr,              tool=0.02, passes=2, overlap=.15 -> total_len=7.401819852418933,  bounds=(2.391,  0.800, 2.99062, 1.27801), rings=4
 * detector_copper_top.gbr,  tool=0.024, passes=1            -> total_len=10.041400203942958,  bounds=(0.28747,0.02250,1.49850,1.03353), rings=18
 * </pre>
 *
 * Bounds/length are compared loosely, not exactly - same rationale as
 * GerberParserBaselineTest: JTS and GEOS/Shapely approximate buffered
 * circular arcs differently, so byte-identical results are not a realistic
 * bar. Ring COUNT is intentionally not compared: it's ambiguous whether the
 * Python reference is counting extracted rings or filled polygons (both
 * happen to give the same length/bounds, so those are safe to compare, but
 * not count) - see IsolationGenerator's class doc.
 */
class IsolationGeneratorTest {

    private static final double LENGTH_RELATIVE_TOLERANCE = 0.03;
    private static final double BOUNDS_TOLERANCE = 0.02;

    @Test
    void simple1SinglePass() throws IOException {
        assertMatches("tests/gerber_files/simple1.gbr",
                new IsolationParameters(0.02, 1, 0.0, IsolationType.BOTH),
                3.8269347087031136, new double[]{2.408, 0.817, 2.97362, 1.26101});
    }

    @Test
    void simple1TwoPassesWithOverlap() throws IOException {
        assertMatches("tests/gerber_files/simple1.gbr",
                new IsolationParameters(0.02, 2, 0.15, IsolationType.BOTH),
                7.401819852418933, new double[]{2.391, 0.800, 2.99062, 1.27801});
    }

    @Test
    void detectorCopperTopSinglePass() throws IOException {
        assertMatches("tests/gerber_files/detector_copper_top.gbr",
                new IsolationParameters(0.024, 1, 0.0, IsolationType.BOTH),
                10.041400203942958, new double[]{0.28747, 0.02250, 1.49850, 1.03353});
    }

    @Test
    void stopsAtACooperativeCancellationCheckpoint() throws Exception {
        GerberImage gerber = new GerberParser().parse(
                findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() >= 3;

        assertThrows(CancellationException.class, () -> IsolationGenerator.generate(
                gerber.units(), gerber.solidGeometry(),
                new IsolationParameters(0.02, 3, 0.15, IsolationType.BOTH), cancellation));
        assertTrue(checks.get() >= 3);
    }

    @Test
    void retainsEachIsolationPassForSeparateGeometryOutputs() throws IOException {
        GerberImage gerber = new GerberParser().parse(
                findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        IsolationResult result = IsolationGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new IsolationParameters(0.02, 2, 0.15, IsolationType.BOTH));

        assertEquals(2, result.passGeometries().size());
        assertFalse(result.passGeometries().get(0).isEmpty());
        assertFalse(result.passGeometries().get(1).isEmpty());
        assertEquals(result.geometry().getLength(),
                result.passGeometries().stream().mapToDouble(g -> g.getLength()).sum(), 1e-9);
        assertTrue(result.passGeometries().get(1).getEnvelopeInternal().getWidth()
                > result.passGeometries().get(0).getEnvelopeInternal().getWidth());
    }

    @Test
    void followReturnsUnbufferedGerberPaths() throws IOException {
        GerberImage gerber = new GerberParser().parse(
                findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        IsolationResult result = IsolationGenerator.generateFollow(
                gerber.units(), gerber.followGeometry(), CancellationToken.none());

        assertFalse(result.isEmpty());
        assertEquals(gerber.followGeometry(), result.geometry());
        assertEquals(1, result.passGeometries().size());
    }

    @Test
    void exceptionAreaRemovesOnlyCoveredPartsOfEveryPass() throws Exception {
        WKTReader reader = new WKTReader();
        var first = reader.read("LINESTRING (0 0, 10 0)");
        var second = reader.read("LINESTRING (0 1, 10 1)");
        var combined = reader.read("MULTILINESTRING ((0 0, 10 0), (0 1, 10 1))");
        var mask = reader.read("POLYGON ((4 -1, 6 -1, 6 2, 4 2, 4 -1))");
        IsolationResult source = new IsolationResult("MM", combined, java.util.List.of(first, second));

        IsolationResult clipped = IsolationGenerator.excludeArea(source, mask, CancellationToken.none());

        assertEquals(2, clipped.passGeometries().size());
        assertEquals(8, clipped.passGeometries().get(0).getLength(), 1e-9);
        assertEquals(8, clipped.passGeometries().get(1).getLength(), 1e-9);
        assertEquals(16, clipped.totalLength(), 1e-9);
    }

    @Test void exceptionAreaClipsRealGeneratedCollectionsWithoutLosingPasses() throws Exception {
        var reader = new WKTReader();
        var copper = reader.read("MULTIPOLYGON (((0 0, 4 0, 4 4, 0 4, 0 0)), ((8 0, 12 0, 12 4, 8 4, 8 0)))");
        var original = IsolationGenerator.generate("MM", copper, new IsolationParameters(.5, 2, .1, IsolationType.BOTH));
        var mask = reader.read("POLYGON ((-1 -1, 5 -1, 5 5, -1 5, -1 -1))");
        var clipped = IsolationGenerator.excludeArea(original, mask, CancellationToken.none());
        assertEquals(2, clipped.passGeometries().size()); assertFalse(clipped.isEmpty());
        assertEquals(original.totalLength() / 2, clipped.totalLength(), 1e-9);
        assertTrue(clipped.geometry().getEnvelopeInternal().getMinX() > 7);
        assertEquals(original.totalLength(), original.geometry().getLength(), 1e-9);
    }

    @Test void exceptionsKeepUncoveredFollowPointsAndLinesInsideNestedCollections() throws Exception {
        var reader = new WKTReader();
        var follow = reader.read("GEOMETRYCOLLECTION (POINT (1 0), GEOMETRYCOLLECTION (POINT (9 0), LINESTRING (0 0, 10 0)))");
        var original = IsolationGenerator.generateFollow("MM", follow, CancellationToken.none());
        var mask = reader.read("POLYGON ((-1 -1, 3 -1, 3 1, -1 1, -1 -1))");
        var clipped = IsolationGenerator.excludeArea(original, mask, CancellationToken.none());
        assertEquals(1, clipped.passGeometries().size()); assertEquals(7, clipped.totalLength(), 1e-9);
        assertEquals(2, clipped.geometry().getNumGeometries());
        assertEquals("POINT (9 0)", clipped.geometry().getGeometryN(0).toText());
        assertEquals(follow, original.geometry());
    }

    @Test void nestedPolygonMasksAreUnitedAndCompleteRemovalKeepsEmptyPassSlots() throws Exception {
        var reader = new WKTReader();
        var copper = reader.read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))");
        var original = IsolationGenerator.generate("MM", copper, new IsolationParameters(.2, 2, 0, IsolationType.BOTH));
        var mask = reader.read("GEOMETRYCOLLECTION (POLYGON ((-1 -1, 2 -1, 2 5, -1 5, -1 -1)),"
                + " GEOMETRYCOLLECTION (POLYGON ((1 -1, 5 -1, 5 5, 1 5, 1 -1))))");
        var clipped = IsolationGenerator.excludeArea(original, mask, CancellationToken.none());
        assertTrue(clipped.isEmpty()); assertEquals(2, clipped.passGeometries().size());
        assertTrue(clipped.passGeometries().stream().allMatch(Geometry::isEmpty));
    }

    @Test void clippingNestedCollectionsHonorsCancellationBetweenParts() throws Exception {
        var reader = new WKTReader();
        var follow = reader.read("GEOMETRYCOLLECTION (POINT (1 0), POINT (9 0), LINESTRING (0 0, 10 0))");
        var mask = reader.read("POLYGON ((4 -1, 6 -1, 6 1, 4 1, 4 -1))");
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> IsolationGenerator.excludeArea(
                IsolationGenerator.generateFollow("MM", follow, CancellationToken.none()), mask, () -> checks.incrementAndGet() >= 5));
        assertEquals(10, follow.getLength());
    }

    @Test void polygonMasksInInchesUseSourceUnitsAndRejectMixedNonFilledMasks() throws Exception {
        var reader = new WKTReader();
        var follow = reader.read("GEOMETRYCOLLECTION (LINESTRING (0 0, 1 0))");
        var mask = reader.read("POLYGON ((0.4 -1, 0.6 -1, 0.6 1, 0.4 1, 0.4 -1))");
        var original = IsolationGenerator.generateFollow("IN", follow, CancellationToken.none());
        var clipped = IsolationGenerator.excludeArea(original, mask, CancellationToken.none());
        assertEquals("IN", clipped.units()); assertEquals(.8, clipped.totalLength(), 1e-9);
        var mixed = reader.read("GEOMETRYCOLLECTION (POLYGON ((0 -1, 1 -1, 1 1, 0 1, 0 -1)), POINT (5 5))");
        assertThrows(IllegalArgumentException.class, () -> IsolationGenerator.excludeArea(original, mixed, CancellationToken.none()));
    }

    @Test
    void restMachiningLeavesTightCopperForSmallerTool() throws Exception {
        WKTReader reader = new WKTReader();
        var copper = reader.read("MULTIPOLYGON ("
                + "((0 0, 1 0, 1 1, 0 1, 0 0)),"
                + "((1.3 0, 2.3 0, 2.3 1, 1.3 1, 1.3 0)),"
                + "((10 0, 11 0, 11 1, 10 1, 10 0)))");
        var small = new IsolationParameters(0.1, 1, 0, IsolationType.BOTH);
        var large = new IsolationParameters(0.4, 1, 0, IsolationType.BOTH);

        var output = IsolationGenerator.generateRest("MM", copper, List.of(small, large),
                CancellationToken.none());

        assertEquals(List.of(0.4, 0.1), output.stream()
                .map(result -> result.parameters().toolDiameter()).toList());
        assertEquals(1, output.get(0).isolation().ringCount());
        assertEquals(2, output.get(1).isolation().ringCount());
        assertEquals(1, output.get(0).isolation().passGeometries().size());
        assertEquals(1, output.get(1).isolation().passGeometries().size());
        assertEquals(0, output.get(1).remainingCopperCount());

        var onlyLarge = IsolationGenerator.generateRest("MM", copper, List.of(large),
                CancellationToken.none());
        assertEquals(2, onlyLarge.get(0).remainingCopperCount());

        List<ToolGeometry> assigned = output.stream()
                .map(result -> new ToolGeometry(result.parameters().toolDiameter(),
                        result.isolation().geometry()))
                .toList();
        CncJobResult cnc = GCodeGenerator.generateGeometryCncJob("MM", assigned,
                new GeometryGCodeParameters(1, 0.1, false, 1, 12, 10000, false));
        assertFalse(cnc.gcode().isBlank());
        assertFalse(cnc.cutGeometry().isEmpty());
    }

    @Test
    void forcedRestRejectsAToolThatMergesAwayAHoleOnTheFirstPass() throws Exception {
        // A tool whose first-pass offset is wider than a hole swallows it entirely when the
        // polygon is buffered outward - "Forced Rest" (checked by default in Python,
        // tools_iso_force) means that tool is rejected outright for this polygon, not just for
        // that one pass, since it could not isolate every hole; the next smaller tool retries it.
        WKTReader reader = new WKTReader();
        var twoSmallHoles = reader.read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), "
                + "(2 2, 2.3 2, 2.3 2.3, 2 2.3, 2 2), (6 6, 6.3 6, 6.3 6.3, 6 6.3, 6 6))");
        var large = new IsolationParameters(1.0, 1, 0, IsolationType.BOTH); // pass-0 offset 0.5: swallows a 0.3-wide hole
        var small = new IsolationParameters(0.2, 1, 0, IsolationType.BOTH); // pass-0 offset 0.1: both holes survive

        var forced = IsolationGenerator.generateRest("MM", twoSmallHoles, List.of(small, large), true,
                CancellationToken.none());
        assertEquals(1, forced.get(0).remainingCopperCount(), "the large tool must reject the (single) polygon entirely");
        assertEquals(0, forced.get(0).isolation().ringCount(), "no rings at all, not even a first pass, from the large tool");
        assertEquals(0, forced.get(1).remainingCopperCount(), "the small tool isolates what the large one rejected");
        assertEquals(3, forced.get(1).isolation().ringCount(), "exterior + both interiors");

        var lenient = IsolationGenerator.generateRest("MM", twoSmallHoles, List.of(small, large), false,
                CancellationToken.none());
        assertEquals(0, lenient.get(0).remainingCopperCount(),
                "without Forced Rest the large tool keeps the polygon even though a hole vanished");
        assertTrue(lenient.get(0).isolation().ringCount() > 0);
    }

    @Test
    void forcedRestRejectsAToolThatMergesAwayAHoleOnTheFirstPassInInchesToo() throws Exception {
        // Same fixture as forcedRestRejectsAToolThatMergesAwayAHoleOnTheFirstPass, scaled by
        // 1/25.4 and relabeled "IN": generateRest takes every distance as a plain number, with
        // no internal MM<->IN conversion (the units string is carried through as metadata
        // only), so the same relative outcome should hold regardless of which unit the numbers
        // represent.
        WKTReader reader = new WKTReader();
        Geometry twoSmallHoles = reader.read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), "
                + "(2 2, 2.3 2, 2.3 2.3, 2 2.3, 2 2), (6 6, 6.3 6, 6.3 6.3, 6 6.3, 6 6))");
        twoSmallHoles = AffineTransformation.scaleInstance(1.0 / 25.4, 1.0 / 25.4).transform(twoSmallHoles);
        var large = new IsolationParameters(1.0 / 25.4, 1, 0, IsolationType.BOTH);
        var small = new IsolationParameters(0.2 / 25.4, 1, 0, IsolationType.BOTH);

        var forced = IsolationGenerator.generateRest("IN", twoSmallHoles, List.of(small, large), true,
                CancellationToken.none());
        assertEquals(1, forced.get(0).remainingCopperCount(), "the large tool must reject the (single) polygon entirely");
        assertEquals(0, forced.get(0).isolation().ringCount(), "no rings at all, not even a first pass, from the large tool");
        assertEquals(0, forced.get(1).remainingCopperCount(), "the small tool isolates what the large one rejected");
        assertEquals(3, forced.get(1).isolation().ringCount(), "exterior + both interiors");
    }

    @Test
    void isolationGeometryCanBeEditedThenUsedForGeometryCncJob() throws Exception {
        GerberImage gerber = new GerberParser().parse(
                findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        IsolationResult isolation = IsolationGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new IsolationParameters(0.02, 1, 0.0, IsolationType.BOTH));
        ToolGeometry tool = new ToolGeometry(0.02, isolation.geometry());

        assertFalse(isolation.isEmpty());
        assertEquals(isolation.geometry(), tool.geometry());
        CncJobResult cncJob = GCodeGenerator.generateGeometryCncJob(gerber.units(),
                java.util.List.of(tool), new GeometryGCodeParameters(0.1, 0.004,
                        false, 1, 12, 10000, false));
        assertFalse(cncJob.gcode().isBlank());
        assertFalse(cncJob.cutGeometry().isEmpty());
    }

    @Test
    void rejectsNonFiniteIsolationParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new IsolationParameters(Double.NaN, 1, 0, IsolationType.BOTH));
        assertThrows(IllegalArgumentException.class,
                () -> new IsolationParameters(0.2, 1, Double.NaN, IsolationType.BOTH));
    }

    private void assertMatches(String repoRelativePath, IsolationParameters params,
                                double expectedLength, double[] expectedBounds) throws IOException {
        Path repoRoot = findRepoRoot();
        GerberImage gerber = new GerberParser().parse(repoRoot.resolve(repoRelativePath));
        IsolationResult result = IsolationGenerator.generate(gerber.units(), gerber.solidGeometry(), params);

        double relativeError = Math.abs(result.totalLength() - expectedLength) / expectedLength;
        assertTrue(relativeError <= LENGTH_RELATIVE_TOLERANCE,
                "totalLength: expected ~" + expectedLength + ", got " + result.totalLength()
                        + " (" + (relativeError * 100) + "% off)");

        double[] actualBounds = result.bounds();
        for (int i = 0; i < 4; i++) {
            assertTrue(Math.abs(actualBounds[i] - expectedBounds[i]) <= BOUNDS_TOLERANCE,
                    "bounds[" + i + "]: expected ~" + expectedBounds[i] + ", got " + actualBounds[i]);
        }
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("tests/gerber_files"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (no ancestor has tests/gerber_files)");
    }
}
