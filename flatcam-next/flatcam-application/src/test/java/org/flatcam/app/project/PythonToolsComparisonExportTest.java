package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.analysis.RulesCheck;
import org.flatcam.cam.analysis.RulesCheck.Board;
import org.flatcam.cam.analysis.RulesCheck.Named;
import org.flatcam.cam.analysis.RulesCheck.Rule;
import org.flatcam.cam.convert.CopperThieving;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.transform.Calibration;
import org.flatcam.cam.transform.TransformOp;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;

/**
 * Reproducible export for tools/compare_tools_python.py: what Rules Check, Copper Thieving and Calibration produce in
 * the FX, with the inputs the original Python routines need. Private fixtures stay outside Git (target/).
 *
 * <p>The synthetic board is written as Gerber/Excellon text so that the Python side parses it with its own parsers.
 * A project fixture is decoded by the FX and its geometry is shared with the Python routines.
 */
class PythonToolsComparisonExportTest {
    @TempDir Path temporary;

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /** Pads 0.1 apart, round pads for the drills, a thin and a wide trace, and a pad close to the outline. */
    private static final List<String> COPPER = List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,1.0X1.0*%", "%ADD11C,2.0*%",
            "%ADD12C,0.2*%", "%ADD13C,0.5*%", "D10*", "X0Y0D03*", "X11000Y0D03*", "X100000Y0D03*", "X220000Y0D03*",
            "D11*", "X0Y100000D03*", "X100000Y100000D03*", "X200000Y100000D03*",
            "D12*", "X0Y200000D02*", "X100000Y200000D01*", "D13*", "X0Y250000D02*", "X100000Y250000D01*", "M02*");
    private static final List<String> OUTLINE = List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,0.1*%", "D10*",
            "X-30000Y-30000D02*", "X230000Y-30000D01*", "X230000Y280000D01*", "X-30000Y280000D01*",
            "X-30000Y-30000D01*", "M02*");
    private static final List<String> SILK = List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2.0X1.0*%", "D10*",
            "X50000Y50000D03*", "X71500Y50000D03*", "X150000Y50000D03*", "X-18000Y50000D03*", "M02*");
    private static final List<String> MASK = List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,1.2X1.2*%", "D10*",
            "X50000Y62000D03*", "X50000Y75000D03*", "X150000Y150000D03*", "M02*");
    private static final List<String> DRILLS = List.of("M48", "METRIC", "T1C1.8", "T2C1.0", "T3C3.0", "T4C0.2", "%",
            "T1", "X0.0Y10.0", "T2", "X10.0Y10.0", "T3", "X20.0Y10.0", "T4", "X15.0Y20.0", "X15.4Y20.0", "M30");

    @Test
    void exportsRulesThievingAndCalibrationCases() throws Exception {
        String output = System.getProperty("flatcam.tools.comparison.output");
        Path directory = output == null || output.isBlank() ? temporary : Path.of(output);
        String fixture = System.getProperty("flatcam.python.project.fixture");

        JSONObject objects = new JSONObject();
        JSONArray cases = new JSONArray();
        Named<GerberImage> copper = gerber(objects, "copper", "synthetic-copper", COPPER);
        Named<GerberImage> outline = gerber(objects, "outline", "synthetic-outline", OUTLINE);
        Named<GerberImage> silk = gerber(objects, "silk", "synthetic-silk", SILK);
        Named<GerberImage> mask = gerber(objects, "mask", "synthetic-mask", MASK);
        Named<ExcellonImage> drills = excellon(objects, "drills", "synthetic-drills", DRILLS);
        rules(cases, "synthetic", copper, outline, silk, mask, drills, "copper", "outline", "silk", "mask", "drills", 1);
        // One piece of copper only: Python reports it as a failure, the FX passes it with a note (documented).
        Named<GerberImage> single = gerber(objects, "single", "synthetic-single",
                List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,1.0X1.0*%", "D10*", "X0Y0D03*", "M02*"));
        rule(cases, "rules-synthetic-single-piece", Rule.COPPER_TO_COPPER, 0.25,
                new Board(single, null, null, null, null, null, null, null, null), "check_inside_gerber_clearance",
                List.of("single"), List.of(), true);
        thieving(cases, "synthetic", copper.image(), "copper", outline.image(), "outline", mask.image(), "mask");
        calibration(cases, copper.image(), "copper");

        if (fixture != null && !fixture.isBlank()) {
            ProjectFile project = PythonProjectIO.load(Path.of(fixture));
            Named<GerberImage> top = decoded(objects, "real-top", find(project, "f_cu"));
            Named<GerberImage> bottom = decoded(objects, "real-bottom", find(project, "b_cu"));
            Named<GerberImage> edge = decoded(objects, "real-outline", find(project, "edge"));
            ProjectFile.ExcellonEntry holes = project.excellons().stream()
                    .filter(e -> !e.name().toLowerCase(Locale.ROOT).contains("npth")).findFirst().orElseThrow();
            Named<ExcellonImage> plated = new Named<>(holes.name(), holes.image());
            objects.put("real-drills", excellonJson(holes.name(), holes.image()));
            double mm = "MM".equalsIgnoreCase(top.image().units()) ? 1 : 1 / 25.4;
            rule(cases, "rules-real-trace-size", Rule.TRACE_SIZE, 0.25 * mm, board(top, null, null), "check_traces_size",
                    List.of("real-top"), List.of(), false);
            rule(cases, "rules-real-copper-top", Rule.COPPER_TO_COPPER, 0.25 * mm, board(top, null, null),
                    "check_inside_gerber_clearance", List.of("real-top"), List.of(), false);
            rule(cases, "rules-real-copper-top-wide", Rule.COPPER_TO_COPPER, 0.4 * mm, board(top, null, null),
                    "check_inside_gerber_clearance", List.of("real-top"), List.of(), false);
            rule(cases, "rules-real-copper-bottom-wide", Rule.COPPER_TO_COPPER, 0.45 * mm, board(bottom, null, null),
                    "check_inside_gerber_clearance", List.of("real-bottom"), List.of(), false);
            // Gaps of exactly the limit: Python grows the copper by 1e-6 before measuring, so it flags them; the FX
            // only flags a gap smaller than the limit (documented).
            rule(cases, "rules-real-copper-bottom-at-limit", Rule.COPPER_TO_COPPER, 0.4 * mm, board(bottom, null, null),
                    "check_inside_gerber_clearance", List.of("real-bottom"), List.of(), true);
            rule(cases, "rules-real-copper-outline", Rule.COPPER_TO_OUTLINE, 1.0 * mm,
                    new Board(top, bottom, null, null, null, null, edge, null, null), "check_gerber_clearance",
                    List.of("real-top", "real-bottom", "real-outline"), List.of(), false);
            rule(cases, "rules-real-annular-ring", Rule.ANNULAR_RING, 0.3 * mm, board(top, null, plated),
                    "check_gerber_annular_ring", List.of("real-top"), List.of("real-drills"), false);
            rule(cases, "rules-real-annular-ring-wide", Rule.ANNULAR_RING, 0.6 * mm, board(top, null, plated),
                    "check_gerber_annular_ring", List.of("real-top"), List.of("real-drills"), false);
            rule(cases, "rules-real-hole-to-hole", Rule.HOLE_TO_HOLE, 1.0 * mm, board(null, null, plated),
                    "check_holes_clearance", List.of(), List.of("real-drills"), false);
            rule(cases, "rules-real-hole-size", Rule.HOLE_SIZE, 0.9 * mm, board(null, null, plated),
                    "check_holes_size", List.of(), List.of("real-drills"), false);
            thieving(cases, "real", top.image(), "real-top", edge.image(), "real-outline", bottom.image(), "real-bottom");
        }

        JSONObject export = new JSONObject().put("schema", 1).put("units", "MM").put("objects", objects)
                .put("cases", cases);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fx-tools.json"), export.toString(1), StandardCharsets.UTF_8);
        assertTrue(cases.length() >= 28, "cases: " + cases.length());
    }

    // --- inputs --------------------------------------------------------------------------------------------------

    private static Named<GerberImage> gerber(JSONObject objects, String key, String name, List<String> source) {
        GerberImage image = new GerberParser().parse(source);
        objects.put(key, gerberJson(name, image).put("source", String.join("\n", source)));
        return new Named<>(name, image);
    }

    private static Named<ExcellonImage> excellon(JSONObject objects, String key, String name, List<String> source) {
        ExcellonImage image = new ExcellonParser().parse(source);
        objects.put(key, excellonJson(name, image).put("source", String.join("\n", source)));
        return new Named<>(name, image);
    }

    private static Named<GerberImage> decoded(JSONObject objects, String key, ProjectFile.GerberEntry entry) {
        objects.put(key, gerberJson(entry.name(), entry.image()));
        return new Named<>(entry.name(), entry.image());
    }

    private static ProjectFile.GerberEntry find(ProjectFile project, String part) {
        return project.gerbers().stream().filter(g -> g.name().toLowerCase(Locale.ROOT).contains(part)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fixture needs a Gerber named *" + part + "*"));
    }

    /** The aperture table as Python keeps it: a size (diameter, or the diagonal of a rectangle/obround) and shapes. */
    private static JSONObject gerberJson(String name, GerberImage image) {
        JSONObject apertures = new JSONObject();
        WKTWriter writer = new WKTWriter();
        for (Map.Entry<String, Aperture> entry : image.apertures().entrySet()) {
            Aperture aperture = entry.getValue();
            JSONObject json = new JSONObject().put("kind", aperture.kind.name()).put("geometry", new JSONArray());
            if (aperture.kind != ApertureKind.MACRO) {
                json.put("size", aperture.kind == ApertureKind.CIRCLE || aperture.kind == ApertureKind.POLYGON
                        ? aperture.width : Math.hypot(aperture.width, aperture.height));
            }
            apertures.put(entry.getKey(), json);
        }
        for (GerberShape shape : image.shapes()) {
            if (!apertures.has(shape.apertureCode())) {
                apertures.put(shape.apertureCode(), new JSONObject().put("kind", "REGION").put("size", 0.0)
                        .put("geometry", new JSONArray()));
            }
            apertures.getJSONObject(shape.apertureCode()).getJSONArray("geometry")
                    .put(new JSONObject().put(shape.clear() ? "clear" : "solid", writer.write(shape.geometry())));
        }
        return new JSONObject().put("type", "gerber").put("name", name).put("units", image.units())
                .put("apertures", apertures).put("solidWkt", writer.write(image.solidGeometry()));
    }

    private static JSONObject excellonJson(String name, ExcellonImage image) {
        JSONObject tools = new JSONObject();
        for (Map.Entry<Integer, Double> tool : image.toolDiameters().entrySet()) {
            tools.put(String.valueOf(tool.getKey()), new JSONObject().put("diameter", tool.getValue())
                    .put("drills", new JSONArray()).put("slots", new JSONArray()));
        }
        for (ExcellonImage.Drill drill : image.drills()) {
            tools.getJSONObject(String.valueOf(drill.toolId())).getJSONArray("drills")
                    .put(new JSONArray().put(drill.x()).put(drill.y()));
        }
        for (ExcellonImage.Slot slot : image.slots()) {
            tools.getJSONObject(String.valueOf(slot.toolId())).getJSONArray("slots")
                    .put(new JSONArray().put(slot.x1()).put(slot.y1()).put(slot.x2()).put(slot.y2()));
        }
        return new JSONObject().put("type", "excellon").put("name", name).put("units", image.units()).put("tools", tools);
    }

    private static Board board(Named<GerberImage> top, Named<GerberImage> outline, Named<ExcellonImage> drills) {
        return new Board(top, null, null, null, null, null, outline, drills, null);
    }

    // --- rules check ---------------------------------------------------------------------------------------------

    private static void rules(JSONArray cases, String prefix, Named<GerberImage> copper, Named<GerberImage> outline,
                              Named<GerberImage> silk, Named<GerberImage> mask, Named<ExcellonImage> drills,
                              String copperKey, String outlineKey, String silkKey, String maskKey, String drillsKey,
                              double mm) {
        String p = "rules-" + prefix + "-";
        rule(cases, p + "trace-size", Rule.TRACE_SIZE, 0.25 * mm, board(copper, null, null), "check_traces_size",
                List.of(copperKey), List.of(), false);
        rule(cases, p + "copper-to-copper", Rule.COPPER_TO_COPPER, 0.25 * mm, board(copper, null, null),
                "check_inside_gerber_clearance", List.of(copperKey), List.of(), false);
        rule(cases, p + "copper-to-outline", Rule.COPPER_TO_OUTLINE, 1.0 * mm, board(copper, outline, null),
                "check_gerber_clearance", List.of(copperKey, outlineKey), List.of(), false);
        rule(cases, p + "silk-to-silk", Rule.SILK_TO_SILK, 0.25 * mm,
                new Board(null, null, silk, null, null, null, null, null, null), "check_inside_gerber_clearance",
                List.of(silkKey), List.of(), false);
        rule(cases, p + "silk-to-mask", Rule.SILK_TO_MASK, 0.25 * mm,
                new Board(null, null, silk, null, mask, null, null, null, null), "check_gerber_clearance",
                List.of(silkKey, maskKey), List.of(), false);
        rule(cases, p + "silk-to-outline", Rule.SILK_TO_OUTLINE, 1.0 * mm,
                new Board(null, null, silk, null, null, null, outline, null, null), "check_gerber_clearance",
                List.of(silkKey, outlineKey), List.of(), false);
        rule(cases, p + "mask-sliver", Rule.MASK_SLIVER, 0.25 * mm,
                new Board(null, null, null, null, mask, null, null, null, null), "check_inside_gerber_clearance",
                List.of(maskKey), List.of(), false);
        rule(cases, p + "annular-ring", Rule.ANNULAR_RING, 0.3 * mm, board(copper, null, drills),
                "check_gerber_annular_ring", List.of(copperKey), List.of(drillsKey), false);
        rule(cases, p + "hole-to-hole", Rule.HOLE_TO_HOLE, 0.3 * mm, board(null, null, drills),
                "check_holes_clearance", List.of(), List.of(drillsKey), false);
        rule(cases, p + "hole-size", Rule.HOLE_SIZE, 0.3 * mm, board(null, null, drills), "check_holes_size",
                List.of(), List.of(drillsKey), false);
    }

    private static void rule(JSONArray cases, String id, Rule rule, double limit, Board board, String function,
                             List<String> gerbers, List<String> excellons, boolean deliberate) {
        Map<Rule, RulesCheck.Setting> settings = new EnumMap<>(Rule.class);
        settings.put(rule, new RulesCheck.Setting(true, limit));
        List<RulesCheck.RuleResult> results = RulesCheck.check(board, settings, CancellationToken.none(),
                ProgressCallback.none());
        assertEquals(1, results.size(), id);
        RulesCheck.RuleResult result = results.get(0);
        assertTrue(result.ran(), id + ": " + result.error());
        JSONArray points = new JSONArray();
        for (Coordinate point : result.points()) {
            points.put(new JSONArray().put(point.x).put(point.y));
        }
        cases.put(new JSONObject().put("id", id).put("tool", "rules").put("rule", rule.name()).put("limit", limit)
                .put("function", function).put("gerbers", new JSONArray(gerbers))
                .put("excellons", new JSONArray(excellons)).put("fxPoints", points)
                .put("fxSizes", new JSONArray(result.sizes())).put("fxNote", result.note() == null ? "" : result.note())
                .put("documentedDifference", deliberate));
    }

    // --- copper thieving -----------------------------------------------------------------------------------------

    private static void thieving(JSONArray cases, String prefix, GerberImage copper, String copperKey,
                                 GerberImage reference, String referenceKey, GerberImage mask, String maskKey) {
        double mm = "MM".equalsIgnoreCase(copper.units()) ? 1 : 1 / 25.4;
        CopperThieving.Options d = CopperThieving.Options.defaults();
        WKTWriter writer = new WKTWriter();
        Envelope box = copper.solidGeometry().getEnvelopeInternal();
        Geometry zones = FACTORY.createMultiPolygon(new Polygon[] {
                (Polygon) FACTORY.toGeometry(new Envelope(box.getMinX(), box.getMinX() + box.getWidth() * 0.45,
                        box.getMinY(), box.getMaxY())),
                (Polygon) FACTORY.toGeometry(new Envelope(box.getMinX() + box.getWidth() * 0.6, box.getMaxX(),
                        box.getMinY() + box.getHeight() * 0.5, box.getMaxY()))});
        record Variant(String name, CopperThieving.Fill fill, CopperThieving.Reference reference,
                       CopperThieving.BoxType boxType) { }
        List<Variant> variants = List.of(
                new Variant("solid", CopperThieving.Fill.SOLID, CopperThieving.Reference.ITSELF,
                        CopperThieving.BoxType.RECTANGULAR),
                new Variant("solid-minimal", CopperThieving.Fill.SOLID, CopperThieving.Reference.ITSELF,
                        CopperThieving.BoxType.MINIMAL),
                new Variant("dots", CopperThieving.Fill.DOT, CopperThieving.Reference.ITSELF,
                        CopperThieving.BoxType.RECTANGULAR),
                new Variant("squares", CopperThieving.Fill.SQUARE, CopperThieving.Reference.ITSELF,
                        CopperThieving.BoxType.RECTANGULAR),
                new Variant("lines", CopperThieving.Fill.LINE, CopperThieving.Reference.ITSELF,
                        CopperThieving.BoxType.RECTANGULAR),
                new Variant("solid-area", CopperThieving.Fill.SOLID, CopperThieving.Reference.AREA,
                        CopperThieving.BoxType.RECTANGULAR),
                new Variant("dots-area", CopperThieving.Fill.DOT, CopperThieving.Reference.AREA,
                        CopperThieving.BoxType.RECTANGULAR),
                new Variant("solid-box-gerber", CopperThieving.Fill.SOLID, CopperThieving.Reference.BOX,
                        CopperThieving.BoxType.RECTANGULAR));
        List<Polygon> solid = null;
        for (Variant variant : variants) {
            CopperThieving.Options options = new CopperThieving.Options(d.clearance() * mm, d.margin() * mm,
                    d.minArea() * mm * mm, variant.reference(), variant.boxType(), d.circleSteps(), variant.fill(),
                    d.dotDiameter() * mm, d.dotSpacing() * mm, d.squareSize() * mm, d.squareSpacing() * mm,
                    d.lineSize() * mm, d.lineSpacing() * mm);
            Geometry referenceGeometry = variant.reference() == CopperThieving.Reference.AREA ? zones
                    : variant.reference() == CopperThieving.Reference.BOX ? reference.solidGeometry() : null;
            List<Polygon> result = CopperThieving.thieve(copper.solidGeometry(), referenceGeometry, true, options,
                    CancellationToken.none(), ProgressCallback.none());
            assertFalse(result.isEmpty(), prefix + " " + variant.name());
            if (variant.name().equals("solid")) {
                solid = result;
            }
            JSONObject parameters = new JSONObject().put("clearance", options.clearance())
                    .put("margin", options.margin()).put("minArea", options.minArea())
                    .put("reference", variant.reference().name()).put("boxType", variant.boxType().name())
                    .put("circleSteps", options.circleSteps()).put("fill", variant.fill().name())
                    .put("dotDiameter", options.dotDiameter()).put("dotSpacing", options.dotSpacing())
                    .put("squareSize", options.squareSize()).put("squareSpacing", options.squareSpacing())
                    .put("lineSize", options.lineSize()).put("lineSpacing", options.lineSpacing());
            JSONObject json = new JSONObject().put("id", "thieving-" + prefix + "-" + variant.name())
                    .put("tool", "thieving").put("gerber", copperKey).put("parameters", parameters)
                    .put("fxCount", result.size())
                    .put("fxWkt", writer.write(FACTORY.buildGeometry(result)))
                    .put("documentedDifference", false);
            if (variant.reference() == CopperThieving.Reference.AREA) {
                json.put("zonesWkt", writer.write(zones));
            }
            if (variant.reference() == CopperThieving.Reference.BOX) {
                json.put("referenceGerber", referenceKey);
            }
            cases.put(json);
        }
        CopperThieving.Robber robber = CopperThieving.robberBar(copper.solidGeometry(), 1.0 * mm, 1.0 * mm);
        cases.put(new JSONObject().put("id", "robber-" + prefix).put("tool", "robber").put("gerber", copperKey)
                .put("margin", 1.0 * mm).put("thickness", 1.0 * mm).put("fxWkt", writer.write(robber.bar()))
                .put("documentedDifference", false));
        for (double clearance : new double[] {0.0, 0.1 * mm}) {
            CopperThieving.PlatingMask plating = CopperThieving.platingMask(mask, clearance, solid, robber,
                    CopperThieving.Plating.BOTH);
            cases.put(new JSONObject().put("id", "plating-" + prefix + (clearance == 0 ? "" : "-clearance"))
                    .put("tool", "plating").put("gerber", copperKey).put("mask", maskKey)
                    .put("clearance", clearance).put("margin", 1.0 * mm).put("thickness", 1.0 * mm)
                    .put("thievingCase", "thieving-" + prefix + "-solid").put("fxArea", plating.platedArea())
                    .put("fxWkt", writer.write(plating.image().solidGeometry()))
                    .put("documentedDifference", false));
        }
    }

    // --- calibration ---------------------------------------------------------------------------------------------

    private static void calibration(JSONArray cases, GerberImage copper, String copperKey) {
        double[][] atOrigin = {{0, 0}, {100, 0}, {0, 80}, {100, 80}};
        double[][] offset = {{10, 20}, {110, 20}, {10, 100}, {110, 100}};
        factors(cases, "calibration-factors-origin", atOrigin, new double[] {0.5, 1.0}, new double[] {0.8, 0.25}, false);
        factors(cases, "calibration-factors-scale-only", atOrigin, new double[] {0.5, 0}, new double[] {0, 0.25}, false);
        // Python adds the origin's Y to the Y-skew delta; the FX uses the delta alone (documented).
        factors(cases, "calibration-factors-offset-origin", offset, new double[] {0.5, 1.0}, new double[] {0.8, 0.25},
                true);
        gcode(cases, "calibration-gcode-top-left", offset, Calibration.GCodeSettings.defaults());
        gcode(cases, "calibration-gcode-bottom-right-zero-z", offset,
                new Calibration.GCodeSettings(3.5, 0.05, true, 12, new double[] {5, 6.25}, false, false));
        gcode(cases, "calibration-gcode-inches", new double[][] {{0.5, 0.25}, {4.5, 0.25}, {0.5, 3.125}, {4.5, 3.125}},
                new Calibration.GCodeSettings(0.1, 0.004, false, 0.6, null, true, true));

        Calibration.Factors factors = new Calibration.Factors(1.002, 0.998, 0.1, 0.05);
        Coordinate origin = new Coordinate(10, 20);
        GerberImage moved = copper;
        for (TransformOp op : Calibration.operations(factors, origin)) {
            moved = moved.transformed(op);
        }
        cases.put(new JSONObject().put("id", "calibration-object").put("tool", "calibration-object")
                .put("gerber", copperKey).put("scaleX", factors.scaleX()).put("scaleY", factors.scaleY())
                .put("skewX", factors.skewX()).put("skewY", factors.skewY())
                .put("origin", new JSONArray().put(origin.x).put(origin.y))
                .put("fxWkt", new WKTWriter().write(moved.solidGeometry())).put("documentedDifference", false));
    }

    private static void factors(JSONArray cases, String id, double[][] points, double[] bottomRight, double[] topLeft,
                                boolean deliberate) {
        Calibration.Factors factors = Calibration.calculate(points, bottomRight, topLeft);
        cases.put(new JSONObject().put("id", id).put("tool", "calibration-factors").put("points", new JSONArray(points))
                .put("bottomRightDelta", new JSONArray(bottomRight)).put("topLeftDelta", new JSONArray(topLeft))
                .put("fxScaleX", factors.scaleX()).put("fxScaleY", factors.scaleY())
                .put("fxSkewX", factors.skewX()).put("fxSkewY", factors.skewY())
                .put("documentedDifference", deliberate));
    }

    private static void gcode(JSONArray cases, String id, double[][] points, Calibration.GCodeSettings settings) {
        JSONObject json = new JSONObject().put("id", id).put("tool", "calibration-gcode")
                .put("points", new JSONArray(points)).put("travelZ", settings.travelZ())
                .put("verificationZ", settings.verificationZ()).put("zeroZ", settings.zeroZ())
                .put("toolChangeZ", settings.toolChangeZ()).put("secondIsTopLeft", settings.secondIsTopLeft())
                .put("inches", settings.inches()).put("fxGcode", Calibration.verificationGCode(points, settings))
                .put("documentedDifference", false);
        if (settings.toolChangeXY() != null) {
            json.put("toolChangeXY", new JSONArray(settings.toolChangeXY()));
        }
        cases.put(json);
    }
}
