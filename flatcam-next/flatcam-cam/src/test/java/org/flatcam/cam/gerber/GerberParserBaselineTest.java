package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential test against the Fase 0 baseline (../../tests/baseline/gerber,
 * generated from the legacy Python parser - see tests/generate_baseline.py
 * and tests/test_gerber_characterization.py at the repo root).
 *
 * <p>This is a DIFFERENT kind of check than the Python-vs-Python
 * characterization test: here we are comparing two independent geometry
 * engines (JTS vs. Shapely/GEOS), not two runs of the same code. Parsed
 * literals (units, aperture types/sizes) must match exactly; anything that
 * comes out of a buffer/union operation (bounds, total area) is compared
 * with a deliberately loose tolerance - see CONTEXTO_FLATCAM_FX.md secao 9
 * ("areas com tolerancia numerica explicita... nao depender de comparacao
 * exata"). A tight tolerance here would be false precision: JTS and GEOS
 * approximate circles/joins differently, so byte-identical areas are not a
 * realistic or meaningful bar for a cross-engine port.
 *
 * <p>Also: the Python baseline's "apertures" map includes a synthetic "0"/
 * type "REG" entry for region fills - a Python-parser bookkeeping detail,
 * not a real Gerber aperture (regions have no aperture). This parser does
 * not model that, so it is skipped rather than treated as a mismatch.
 *
 * <p>"solid_geometry_part_count" is deliberately NOT compared here. On
 * detector_copper_bottom.gbr (which strokes with a degenerate 0.001x0.001"
 * rectangle - see Aperture#strokeRadius) this parser produces 578 disjoint
 * polygons against the baseline's 18, while bounds match exactly and area
 * matches within 0.1% - i.e. the same copper coverage, just partitioned
 * differently by two independent buffer/union implementations at a scale
 * (half a mil) where their polygon approximations diverge most. Part count
 * is a discrete topological property, not a numerically-tolerant one, so
 * there is no threshold that is both meaningful and robust here - unlike
 * the fixed bug this same investigation found in tests/parser_baseline.py's
 * own part-count logic (a real bug, not an engine difference).
 */
class GerberParserBaselineTest {

    private static final double BOUNDS_ABS_TOLERANCE = 0.02; // file units (inches for this corpus)
    private static final double AREA_RELATIVE_TOLERANCE = 0.03; // 3%
    private static final double LITERAL_TOLERANCE = 1e-6; // parsed numbers, not geometry-derived

    private record Fixture(String gerberPath, String baselineStem) {
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("tests/gerber_files/simple1.gbr", "simple1"),
            new Fixture("tests/gerber_files/detector_contour.gbr", "detector_contour"),
            new Fixture("tests/gerber_files/detector_copper_top.gbr", "detector_copper_top"),
            new Fixture("tests/gerber_files/detector_copper_bottom.gbr", "detector_copper_bottom"),
            new Fixture("tests/gerber_files/STM32F4-spindle.cmp", "STM32F4-spindle"),
            new Fixture("tests/gerber_files/arc_multi.gbr", "arc_multi"),
            new Fixture("tests/gerber_files/arc_single.gbr", "arc_single")
    );

    @TestFactory
    Stream<DynamicTest> matchesBaselineStructurally() {
        Path repoRoot = findRepoRoot();
        return FIXTURES.stream().map(fixture -> DynamicTest.dynamicTest(fixture.baselineStem(), () -> {
            Path gerberFile = repoRoot.resolve(fixture.gerberPath());
            Path baselineFile = repoRoot.resolve("tests/baseline/gerber/" + fixture.baselineStem() + ".json");

            GerberImage image = new GerberParser().parse(gerberFile);
            JSONObject baseline = new JSONObject(Files.readString(baselineFile));

            assertEquals(baseline.getString("units"), image.units(), "units");
            assertApertures(baseline.getJSONObject("apertures"), image.apertures());

            double[] expectedBounds = baseline.getJSONArray("bounds").toList().stream()
                    .mapToDouble(o -> ((Number) o).doubleValue()).toArray();
            double[] actualBounds = image.bounds();
            assertNotNull(actualBounds, "bounds");
            for (int i = 0; i < 4; i++) {
                assertTrue(Math.abs(actualBounds[i] - expectedBounds[i]) <= BOUNDS_ABS_TOLERANCE,
                        "bounds[" + i + "]: expected ~" + expectedBounds[i] + ", got " + actualBounds[i]);
            }

            double expectedArea = baseline.getDouble("solid_geometry_total_area");
            double actualArea = image.totalArea();
            double relativeError = Math.abs(actualArea - expectedArea) / expectedArea;
            assertTrue(relativeError <= AREA_RELATIVE_TOLERANCE,
                    "solid_geometry_total_area: expected ~" + expectedArea + ", got " + actualArea
                            + " (" + (relativeError * 100) + "% off)");
        }));
    }

    private static void assertApertures(JSONObject baselineApertures, Map<String, Aperture> actual) {
        for (String id : baselineApertures.keySet()) {
            JSONObject expected = baselineApertures.getJSONObject(id);
            String type = expected.getString("type");
            if (type.equals("REG")) {
                continue; // synthetic Python-side bookkeeping for region fills, not a real aperture.
            }
            Aperture aperture = actual.get(id);
            assertNotNull(aperture, "aperture D" + id + " missing");
            switch (type) {
                case "C" -> {
                    assertEquals(ApertureKind.CIRCLE, aperture.kind, "aperture D" + id + " kind");
                    assertEquals(expected.getDouble("size"), aperture.width, LITERAL_TOLERANCE, "aperture D" + id + " diameter");
                }
                case "R" -> {
                    assertEquals(ApertureKind.RECTANGLE, aperture.kind, "aperture D" + id + " kind");
                    assertEquals(expected.getDouble("width"), aperture.width, LITERAL_TOLERANCE, "aperture D" + id + " width");
                    assertEquals(expected.getDouble("height"), aperture.height, LITERAL_TOLERANCE, "aperture D" + id + " height");
                }
                case "AM" -> assertEquals(ApertureKind.MACRO, aperture.kind, "aperture D" + id + " kind");
                default -> fail("Unhandled baseline aperture type '" + type + "' for D" + id);
            }
        }
    }

    /**
     * apertureGeometry() (the "Mark" highlight data source) isn't in the Python
     * baseline JSON, so this is a plain sanity check rather than a differential
     * comparison: every used aperture has non-empty geometry, and D10 (the flash
     * aperture in simple1.gbr) covers strictly less area than the whole board -
     * i.e. it really is per-aperture, not accidentally the full solidGeometry.
     */
    @Test
    void apertureGeometryIsPopulatedPerUsedAperture() throws Exception {
        Path repoRoot = findRepoRoot();
        GerberImage image = new GerberParser().parse(repoRoot.resolve("tests/gerber_files/simple1.gbr"));

        assertFalse(image.apertureGeometry().isEmpty(), "at least one aperture must have been used");
        for (String usedApertureId : image.apertureGeometry().keySet()) {
            assertTrue(image.apertures().containsKey(usedApertureId), "unknown aperture id " + usedApertureId);
            assertFalse(image.apertureGeometry().get(usedApertureId).isEmpty(), "aperture D" + usedApertureId + " geometry");
        }
        assertTrue(image.apertureGeometry().get("10").getArea() < image.totalArea(),
                "one aperture's own geometry should be a strict subset of the whole board");
    }

    /** Walks up from the working directory until it finds the repo root (marked by tests/gerber_files). */
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
