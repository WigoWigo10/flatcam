package org.flatcam.cam.isolation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
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
