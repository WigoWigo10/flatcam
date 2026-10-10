package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.NccParameters;
import org.flatcam.cam.ncc.NccToolSettings;
import org.flatcam.cam.ncc.PaintParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/** Reproducible export for tools/compare_cam_python.py; private fixtures stay outside Git. */
class PythonCamComparisonExportTest {
    @TempDir Path temporary;

    @Test
    void exportsCamCasesAndChecksTheirGcodePreview() throws Exception {
        export(false);
    }

    @Test
    void exportsSyntheticInchCasesAndChecksTheirGcodePreview() throws Exception {
        export(true);
    }

    private void export(boolean syntheticInch) throws Exception {
        String fixture = System.getProperty("flatcam.python.project.fixture");
        Geometry copper;
        String units = "MM";
        String name = "synthetic";
        if (!syntheticInch && fixture != null && !fixture.isBlank()) {
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
            if (syntheticInch) {
                copper = AffineTransformation.scaleInstance(1 / 25.4, 1 / 25.4).transform(copper);
                units = "IN"; name = "synthetic-in";
            }
        }
        double mm = "IN".equals(units) || "INCH".equals(units) ? 1 / 25.4 : 1;
        JSONArray cases = new JSONArray();
        for (int passes : List.of(1, 3)) {
            var params = new IsolationParameters(0.1 * mm, passes, 0.15, IsolationType.BOTH);
            Geometry paths = IsolationGenerator.generate(units, copper, params).geometry();
            add(cases, "isolation-" + passes, "isolation", copper, paths, units, params.toolDiameter(),
                    new JSONObject().put("passes", passes).put("overlap", params.overlapFraction()));
        }
        for (IsolationType type : List.of(IsolationType.EXTERIOR, IsolationType.INTERIOR)) {
            var params = new IsolationParameters(.1 * mm, 1, .15, type);
            var paths = IsolationGenerator.generate(units, copper, params).geometry();
            // Real boards may have no internal rings; do not claim a comparison for an empty output.
            if (!paths.isEmpty()) add(cases,"isolation-" + type.name().toLowerCase(java.util.Locale.ROOT),"isolation",
                    copper,paths,units,params.toolDiameter(),new JSONObject().put("passes",1).put("overlap",.15).put("isoType",type.ordinal()));
        }
        var bounds = copper.getEnvelopeInternal();
        var factory = copper.getFactory();
        Geometry mask = factory.toGeometry(new Envelope(bounds.getMinX() + bounds.getWidth() * .4,
                bounds.getMinX() + bounds.getWidth() * .6, bounds.getMinY() - mm, bounds.getMaxY() + mm));
        var isolation = IsolationGenerator.generate(units,copper,new IsolationParameters(.1 * mm,3,.15,IsolationType.BOTH));
        var clipped = IsolationGenerator.excludeArea(isolation,mask,CancellationToken.none());
        add(cases,"isolation-exceptions","isolation",copper,clipped.geometry(),units,.1 * mm,
                new JSONObject().put("passes",3).put("overlap",.15).put("exceptionWkt",new WKTWriter().write(mask)));
        // Explicit line input shared by both engines, not a substitute for parsing Gerber follow data.
        // Imported projects can preserve nested polygon collections. JTS cannot
        // getBoundary directly on generic collections; this is an explicitly
        // shared line fixture, not a claim about reconstructed Gerber follow data.
        var follow = copper.buffer(0).getBoundary();
        var followed = IsolationGenerator.generateFollow(units,follow,CancellationToken.none());
        add(cases,"isolation-follow","isolation",follow,followed.geometry(),units,.1 * mm,
                new JSONObject().put("follow",true).put("passes",1).put("overlap",.15));
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
        var pythonSeed = new NccParameters(List.of(.5 * mm),.4,mm,NccMethod.SEED,false,true,0,
                false,NccOrder.NONE,new NccBoundary.Itself(),List.of(),Map.of(.5 * mm,
                new org.flatcam.cam.ncc.NccToolSettings(.4,NccMethod.SEED,false,true,0,
                        org.flatcam.cam.ncc.NccSeedPolicy.PYTHON)));
        var pythonSeedResult = NccGenerator.generate(units,copper,pythonSeed);
        add(cases,"ncc-seed-python","ncc",copper,pythonSeedResult.geometry(),units,.5*mm,
                new JSONObject().put("method","SEED").put("seedPolicy","PYTHON")
                        .put("margin",mm).put("overlap",.4).put("connect",false).put("contour",true)
                        .put("fxClearingAreaWkt",new WKTWriter().write(pythonSeedResult.clearingArea()))
                        .put("fxFailedPolygons",pythonSeedResult.totalFailedPolygonCount()));
        Geometry reference = factory.toGeometry(new Envelope(bounds.getMinX() - 2 * mm,bounds.getMaxX() + 2 * mm,
                bounds.getMinY() + bounds.getHeight() * .35,bounds.getMaxY() + 2 * mm));
        Geometry concaveReference = reference.difference(factory.toGeometry(new Envelope(
                bounds.getMinX() + bounds.getWidth() * .7, bounds.getMaxX() + 3 * mm,
                bounds.getMinY() + bounds.getHeight() * .7, bounds.getMaxY() + 3 * mm)));
        for (String variant : List.of("connect","no-contour","area","reference-gerber","reference-geometry")) {
            NccBoundary boundary = switch(variant) {
                case "area" -> new NccBoundary.Area(reference);
                case "reference-gerber" -> new NccBoundary.ReferenceGerber(concaveReference);
                case "reference-geometry" -> new NccBoundary.ReferenceGeometry(concaveReference);
                default -> new NccBoundary.Itself();
            };
            var params = new NccParameters(List.of(.5 * mm),.4,mm,NccMethod.STANDARD,variant.equals("connect"),
                    !variant.equals("no-contour"),0,false,NccOrder.NONE,boundary,List.of());
            var result = NccGenerator.generate(units,copper,params);
            var json = new JSONObject().put("method","STANDARD").put("margin",mm).put("overlap",.4)
                    .put("connect",params.connect()).put("contour",params.contour()).put("boundary",variant)
                    .put("fxClearingAreaWkt",new WKTWriter().write(result.clearingArea())).put("fxFailedPolygons",result.totalFailedPolygonCount());
            if (!(boundary instanceof NccBoundary.Itself)) json.put("referenceWkt",new WKTWriter().write(
                    variant.equals("area") ? reference : concaveReference));
            add(cases,"ncc-" + variant,"ncc",copper,result.geometry(),units,.5 * mm,json);
        }
        for (String variant : List.of("multi-none","multi-forward","multi-reverse","multi-settings",
                "rest","rest-area","rest-reference-geometry","rest-connect")) {
            boolean rest = variant.startsWith("rest");
            var boundary = variant.equals("rest-area") ? new NccBoundary.Area(reference)
                    : variant.equals("rest-reference-geometry") ? new NccBoundary.ReferenceGeometry(concaveReference)
                    : new NccBoundary.Itself();
            NccOrder order = variant.equals("multi-forward") ? NccOrder.FORWARD
                    : variant.equals("multi-reverse") ? NccOrder.REVERSE : NccOrder.NONE;
            Map<Double,NccToolSettings> settings = variant.equals("multi-settings")
                    ? Map.of(.2 * mm,new NccToolSettings(.25,NccMethod.STANDARD,false,true,.1 * mm)) : Map.of();
            var params = new NccParameters(List.of(.2 * mm,mm),.4,mm,NccMethod.STANDARD,
                    variant.equals("rest-connect"),true,0,rest,order,boundary,List.of(),settings);
            var result = NccGenerator.generate(units,copper,params);
            var toolInputs = new JSONArray();
            for (double diameter : params.toolDiameters()) {
                var resolved = params.settingsFor(diameter);
                toolInputs.put(new JSONObject().put("diameter",diameter).put("method",resolved.method().name())
                        .put("overlap",resolved.overlapFraction()).put("connect",resolved.connect())
                        .put("contour",resolved.contour()).put("copperOffset",resolved.copperOffset()));
            }
            var toolOutputs = new JSONArray();
            for (var tool : result.toolResults()) {
                toolOutputs.put(new JSONObject().put("diameter",tool.toolDiameter())
                        .put("fxWkt",new WKTWriter().write(tool.geometry()))
                        .put("fxFailedPolygons",tool.failedPolygonCount())
                        .put("gcode",tool.isEmpty() ? "" : generateProgram(units,tool.geometry(),tool.toolDiameter())));
            }
            var json = new JSONObject().put("method","STANDARD").put("margin",mm).put("overlap",.4)
                    .put("connect",params.connect()).put("contour",true).put("copperOffset",0)
                    .put("order",order.name()).put("restMachining",rest).put("tools",toolInputs)
                    .put("fxToolResults",toolOutputs).put("fxFailedPolygons",result.totalFailedPolygonCount())
                    .put("fxClearingAreaWkt",new WKTWriter().write(result.clearingArea()))
                    .put("boundary",variant.equals("rest-area") ? "area"
                            : variant.equals("rest-reference-geometry") ? "reference-geometry" : "itself");
            if (!(boundary instanceof NccBoundary.Itself)) json.put("referenceWkt",new WKTWriter().write(
                    variant.equals("rest-area") ? reference : concaveReference));
            add(cases,"ncc-" + variant,"ncc",copper,result.geometry(),units,.2 * mm,json);
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
        Files.writeString(directory.resolve(syntheticInch ? "fx-cam-in.json" : "fx-cam.json"), export.toString(2), StandardCharsets.UTF_8);
        assertTrue(cases.length() >= 26);
        if (fixture == null || fixture.isBlank() || syntheticInch) assertEquals(28,cases.length());
    }

    private static void add(JSONArray cases, String id, String operation, Geometry input, Geometry paths,
                            String units, double diameter, JSONObject params) throws org.locationtech.jts.io.ParseException {
        assertFalse(paths.isEmpty(), id);
        assertTrue(paths.isValid(), id);
        assertTrue(paths.getLength() > 0, id);
        boolean inch = "IN".equals(units) || "INCH".equals(units);
        var cncParams = new GeometryGCodeParameters(inch ? 3 / 25.4 : 3, inch ? .1 / 25.4 : .1,
                false, 0, inch ? 300 / 25.4 : 300, 0, params.has("fxToolResults"));
        List<ToolGeometry> tools = new java.util.ArrayList<>();
        if (params.has("fxToolResults")) {
            var outputs = params.getJSONArray("fxToolResults");
            for (int i = 0; i < outputs.length(); i++) {
                var output = outputs.getJSONObject(i);
                tools.add(new ToolGeometry(output.getDouble("diameter"),new WKTReader().read(output.getString("fxWkt"))));
            }
        } else tools.add(new ToolGeometry(diameter,paths));
        // Multi-tool cases use the real multi-tool generator and tool-change
        // metadata, not a combined drawing masquerading as a single cutter.
        var job = GCodeGenerator.generateGeometryCncJob(units,tools,cncParams);
        var preview = GCodeToolpathParser.parse(job.gcode(), CancellationToken.none(), ProgressCallback.none());
        assertFalse(job.cutGeometry().isEmpty(), id);
        assertTrue(job.gcode().contains("M30"), id);
        assertTrue(preview.plotAvailable(), id + ": " + preview.warning());
        assertFalse(preview.cutGeometry().isEmpty(), id);
        cases.put(new JSONObject().put("id", id).put("operation", operation).put("diameter", diameter).put("units",units)
                .put("inputWkt", new WKTWriter().write(input)).put("fxWkt", new WKTWriter().write(paths))
                .put("parameters", params).put("gcode", job.gcode())
                .put("fxDetailedPreviewAvailable", preview.plotAvailable())
                .put("fxPreviewWarning", preview.warning() == null ? "" : preview.warning()));
    }

    private static String generateProgram(String units,Geometry paths,double diameter) {
        boolean inch = "IN".equals(units) || "INCH".equals(units);
        var job = GCodeGenerator.generateGeometryCncJob(units,paths,
                new GeometryGCodeParameters(inch ? 3 / 25.4 : 3,inch ? .1 / 25.4 : .1,
                        false,0,inch ? 300 / 25.4 : 300,0,false),diameter);
        var preview = GCodeToolpathParser.parse(job.gcode(),CancellationToken.none(),ProgressCallback.none());
        assertTrue(preview.plotAvailable(),preview.warning());
        assertFalse(preview.cutGeometry().isEmpty());
        return job.gcode();
    }
}
