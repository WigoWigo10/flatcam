package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.junit.jupiter.api.Test;

class NccGeneratorTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void allLegacyStrategiesProduceSafeToolpaths() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        for (NccMethod method : NccMethod.values()) {
            NccParameters params = new NccParameters(0.5, 0.15, 2.0, method, true, true, 0);
            NccResult result = NccGenerator.generate("MM", copper, params);

            assertFalse(result.isEmpty(), method + " should clear the area around the copper");
            assertTrue(result.pathCount() > 0);
            assertTrue(result.totalLength() > 0);
            assertEquals(0, result.failedPolygonCount());

            // Cutter-center paths inset by r must keep the physical cutter inside
            // the non-copper area, allowing only polygon-approximation noise.
            Geometry footprint = result.geometry().buffer(params.toolDiameter() / 2.0, 64);
            double escapedArea = footprint.difference(result.clearingArea().buffer(1e-4)).getArea();
            assertTrue(escapedArea < 5e-4,
                    method + " cutter footprint escaped the clearing area by " + escapedArea);
        }
    }

    @Test
    void copperOffsetCreatesExtraKeepOutDistance() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccResult plain = NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.15, 2, NccMethod.STANDARD, false, true, 0));
        NccResult offset = NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.15, 2, NccMethod.STANDARD, false, true, 0.4));

        assertTrue(offset.clearingArea().getArea() < plain.clearingArea().getArea());
        assertTrue(offset.geometry().distance(copper) > plain.geometry().distance(copper));
    }

    @Test
    void reportsMonotonicProgressAndCompletesAtOne() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        List<Double> reports = new ArrayList<>();
        NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.15, 2, NccMethod.LINES, true, true, 0),
                CancellationToken.none(), reports::add);

        assertFalse(reports.isEmpty());
        assertEquals(1.0, reports.get(reports.size() - 1));
        for (int i = 1; i < reports.size(); i++) {
            assertTrue(reports.get(i) >= reports.get(i - 1), "progress must be monotonic");
        }
    }

    @Test
    void honorsCooperativeCancellation() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() >= 4;

        assertThrows(CancellationException.class, () -> NccGenerator.generate("MM", copper,
                new NccParameters(0.1, 0.5, 5, NccMethod.STANDARD, true, true, 0),
                cancellation, fraction -> { }));
        assertTrue(checks.get() >= 4);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(0, 0.15, 1, NccMethod.STANDARD, true, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(1, 1, 1, NccMethod.STANDARD, true, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(1, 0.15, -1, NccMethod.STANDARD, true, true, 0));
        assertThrows(NullPointerException.class,
                () -> new NccParameters(1, 0.15, 1, null, true, true, 0));
    }

    @Test
    void clearsARealParsedGerber() throws Exception {
        GerberImage image = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        NccResult result = NccGenerator.generate(image.units(), image.solidGeometry(),
                new NccParameters(0.02, 0.40, 0.04, NccMethod.COMBO, true, true, 0));

        assertFalse(result.isEmpty());
        assertEquals(image.units(), result.units());
        assertTrue(result.clearingArea().getArea() > 0);
        assertTrue(result.totalLength() > 0);
    }

    private static java.nio.file.Path findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        while (dir != null) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/gerber_files"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root");
    }
}
