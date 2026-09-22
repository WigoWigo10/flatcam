package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.flatcam.cam.CancellationToken;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential test against the Fase 0 baseline (../../tests/baseline/excellon,
 * generated from the legacy Python parser - see tests/generate_baseline.py).
 *
 * <p>Units, per-tool diameters, and drill/slot counts are parsed literals -
 * exact match. Bounds are NOT exactly comparable: the Fase 0 Python baseline
 * (tests/parser_baseline.py's summarize_excellon) deliberately computes
 * bounds from raw hit-point coordinates only (parsing-only scope, no
 * create_geometry() call - see that file's docstring), while this parser's
 * bounds come from the actual buffered hole geometry. The two differ
 * systematically by roughly one tool radius on each side, so bounds are
 * compared with a tolerance sized to the corpus's largest tool.
 */
class ExcellonParserBaselineTest {

    private static final double BOUNDS_TOLERANCE = 0.2; // covers largest tool radius in the corpus, generously
    private static final double LITERAL_TOLERANCE = 1e-6;

    private record Fixture(String excellonPath, String baselineStem) {
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("tests/excellon_files/case1.drl", "case1"),
            new Fixture("tests/gerber_files/detector_drill.txt", "detector_drill")
    );

    @TestFactory
    Stream<DynamicTest> matchesBaselineStructurally() {
        Path repoRoot = findRepoRoot();
        return FIXTURES.stream().map(fixture -> DynamicTest.dynamicTest(fixture.baselineStem(), () -> {
            Path excellonFile = repoRoot.resolve(fixture.excellonPath());
            Path baselineFile = repoRoot.resolve("tests/baseline/excellon/" + fixture.baselineStem() + ".json");

            ExcellonImage image = new ExcellonParser().parse(excellonFile);
            JSONObject baseline = new JSONObject(Files.readString(baselineFile));

            assertEquals(baseline.getString("units"), image.units(), "units");
            assertEquals(baseline.getInt("tool_count"), image.toolDiameters().size(), "tool_count");
            assertEquals(baseline.getInt("total_drills"), image.totalDrills(), "total_drills");
            assertEquals(baseline.getInt("total_slots"), image.totalSlots(), "total_slots");

            JSONObject baselineTools = baseline.getJSONObject("tools");
            for (String id : baselineTools.keySet()) {
                JSONObject expected = baselineTools.getJSONObject(id);
                int toolId = Integer.parseInt(id);
                assertEquals(expected.getDouble("tooldia"), image.toolDiameters().get(toolId),
                        LITERAL_TOLERANCE, "tool " + id + " diameter");
                assertEquals(expected.getInt("drill_count"), image.drillCounts().getOrDefault(toolId, 0),
                        "tool " + id + " drill_count");
                assertEquals(expected.getInt("slot_count"), image.slotCounts().getOrDefault(toolId, 0),
                        "tool " + id + " slot_count");
            }

            double[] expectedBounds = baseline.getJSONArray("bounds").toList().stream()
                    .mapToDouble(o -> ((Number) o).doubleValue()).toArray();
            double[] actualBounds = image.bounds();
            assertNotNull(actualBounds, "bounds");
            for (int i = 0; i < 4; i++) {
                assertTrue(Math.abs(actualBounds[i] - expectedBounds[i]) <= BOUNDS_TOLERANCE,
                        "bounds[" + i + "]: expected ~" + expectedBounds[i] + ", got " + actualBounds[i]);
            }
        }));
    }

    @Test
    void stopsAtCooperativeCancellationCheckpoint() throws Exception {
        List<String> lines = Files.readAllLines(findRepoRoot().resolve("tests/gerber_files/detector_drill.txt"));
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() >= 5;

        assertThrows(CancellationException.class, () -> new ExcellonParser().parse(lines, cancellation));
        assertTrue(checks.get() >= 5);
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
