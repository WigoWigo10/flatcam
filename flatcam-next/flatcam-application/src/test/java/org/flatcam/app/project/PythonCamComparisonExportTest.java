package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutKind;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.CutoutShape;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.NccParameters;
import org.flatcam.cam.ncc.PaintParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/** Reproducible export for tools/compare_cam_python.py; private fixtures stay outside Git. */
class PythonCamComparisonExportTest {
    @TempDir Path temporary;

    @Test
    void exportsCamCasesAndChecksTheirGcodePreview() throws Exception {
        String fixture = System.getProperty("flatcam.python.project.fixture");
        Geometry copper;
        String units = "MM";
        String name = "synthetic";
        if (fixture != null && !fixture.isBlank()) {
            ProjectFile project = PythonProjectIO.load(Path.of(fixture));
            var entry = project.gerbers().stream()
                    .filter(g -> g.name().toLowerCase(java.util.Locale.ROOT).contains("b_cu"))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Fixture needs a B_Cu Gerber"));
            copper = entry.image().solidGeometry();
            units = entry.image().units();
            name = entry.name();
        } else {
            copper = new WKTReader().read("POLYGON ((0 0, 30 0, 30 20, 0 20, 0 0), "
                    + "(4 4, 4 16, 26 16, 26 4, 4 4))");
        }
        double mm = "IN".equals(units) || "INCH".equals(units) ? 1 / 25.4 : 1;
        JSONArray cases = new JSONArray();
        for (int passes : List.of(1, 3)) {
            var params = new IsolationParameters(0.1 * mm, passes, 0.15, IsolationType.BOTH);
            Geometry paths = IsolationGenerator.generate(units, copper, params).geometry();
            add(cases, "isolation-" + passes, "isolation", copper, paths, units, params.toolDiameter(),
                    new JSONObject().put("passes", passes).put("overlap", params.overlapFraction()));
        }
        Geometry paintArea = null;
        for (NccMethod method : List.of(NccMethod.STANDARD, NccMethod.SEED, NccMethod.LINES)) {
            var params = new NccParameters(0.5 * mm, 0.4, mm, method, false, true, 0);
            var result = NccGenerator.generate(units, copper, params);
            add(cases, "ncc-" + method.name().toLowerCase(java.util.Locale.ROOT), "ncc", copper,
                    result.geometry(), units, params.toolDiameters().getFirst(),
                    new JSONObject().put("method", method.name()).put("margin", mm)
                            .put("overlap", 0.4).put("connect", false).put("contour", true)
                            .put("fxClearingAreaWkt", new WKTWriter().write(result.clearingArea()))
                            .put("fxFailedPolygons", result.totalFailedPolygonCount()));
            paintArea = result.clearingArea();
        }
        // Same explicit selected area on both sides: this isolates Paint from NCC boundary creation.
        var paintParams = new PaintParameters(List.of(0.5 * mm), 0.4, 0, NccMethod.STANDARD,
                false, true, NccOrder.NONE, false);
        var paint = NccGenerator.paint(units, paintArea, paintParams,
                CancellationToken.none(), ProgressCallback.none());
        add(cases, "paint-standard", "paint", paintArea, paint.geometry(), units, 0.5 * mm,
                new JSONObject().put("method", "STANDARD").put("overlap", 0.4)
                        .put("connect", false).put("contour", true));
        for (double margin : List.of(0.0, mm)) {
            for (GapPattern gaps : List.of(GapPattern.NONE, GapPattern.FOUR)) {
                var params = new CutoutParameters(mm, margin, false, CutoutKind.SINGLE,
                        CutoutShape.RECTANGULAR, 2 * mm, gaps);
                Geometry paths = CutoutGenerator.generate(units, copper, params).geometry();
                add(cases, "cutout-" + (margin == 0 ? "zero" : "margin") + "-"
                                + gaps.name().toLowerCase(java.util.Locale.ROOT),
                        "cutout", copper, paths, units, mm,
                        new JSONObject().put("margin", margin).put("gapSize", 2 * mm)
                                .put("gaps", gaps == GapPattern.NONE ? "None" : "4"));
            }
        }
        JSONObject export = new JSONObject().put("schema", 1).put("sourceName", name).put("units", units)
                .put("sourceWkt", new WKTWriter().write(copper)).put("cases", cases);
        String output = System.getProperty("flatcam.cam.comparison.output");
        Path directory = output == null || output.isBlank() ? temporary : Path.of(output);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fx-cam.json"), export.toString(2), StandardCharsets.UTF_8);
        assertEquals(10, cases.length());
    }

    private static void add(JSONArray cases, String id, String operation, Geometry input, Geometry paths,
                            String units, double diameter, JSONObject params) {
        assertFalse(paths.isEmpty(), id);
        assertTrue(paths.isValid(), id);
        assertTrue(paths.getLength() > 0, id);
        boolean inch = "IN".equals(units) || "INCH".equals(units);
        var job = GCodeGenerator.generateGeometryCncJob(units, paths,
                new GeometryGCodeParameters(inch ? 3 / 25.4 : 3, inch ? .1 / 25.4 : .1,
                        false, 0, inch ? 300 / 25.4 : 300, 0, false), diameter);
        var preview = GCodeToolpathParser.parse(job.gcode(), CancellationToken.none(), ProgressCallback.none());
        assertFalse(job.cutGeometry().isEmpty(), id);
        assertTrue(job.gcode().contains("M30"), id);
        assertTrue(preview.plotAvailable(), id + ": " + preview.warning());
        assertFalse(preview.cutGeometry().isEmpty(), id);
        cases.put(new JSONObject().put("id", id).put("operation", operation).put("diameter", diameter)
                .put("inputWkt", new WKTWriter().write(input)).put("fxWkt", new WKTWriter().write(paths))
                .put("parameters", params).put("gcode", job.gcode())
                .put("fxDetailedPreviewAvailable", preview.plotAvailable())
                .put("fxPreviewWarning", preview.warning() == null ? "" : preview.warning()));
    }
}
