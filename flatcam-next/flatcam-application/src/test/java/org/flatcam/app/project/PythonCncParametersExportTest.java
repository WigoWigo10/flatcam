package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/** Public fixtures only. The Python side regenerates code, not expected G-code strings. */
class PythonCncParametersExportTest {
    @TempDir Path temporary;
    private final GeometryFactory factory = new GeometryFactory();

    @Test void exportsMetricAndInchMachiningCases() throws Exception {
        var cases = new JSONArray();
        for (String units : List.of("MM", "IN")) {
            double scale = units.equals("IN") ? 1 / 25.4 : 1;
            for (String recipe : List.of("single", "peck", "offset", "negative-offset", "two-tools",
                    "selected-tool", "positions", "dwell", "zero-dwell", "spindle-off", "around", "over")) {
                cases.put(drilling(units, scale, recipe));
            }
            for (String recipe : List.of("single", "peck-open", "peck-closed", "positions", "dwell", "continuous-open", "continuous-closed")) {
                cases.put(geometry(units, scale, recipe));
            }
            cases.put(multitoolGeometry(units, scale, false));
            cases.put(multitoolGeometry(units, scale, true));
        }
        assertEquals(42, cases.length());
        var output = new JSONObject().put("schema", 1).put("profile", "GRBL_11_no_M6")
                .put("scope", "Public synthetic CNC parameters; not UI, machine safety certification or all profiles")
                .put("cases", cases);
        Path directory = Path.of(System.getProperty("flatcam.cnc.comparison.output", temporary.toString()));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fx-cnc.json"), output.toString(2), StandardCharsets.UTF_8);
    }

    private JSONObject drilling(String units, double s, String recipe) {
        boolean two = recipe.equals("two-tools") || recipe.equals("selected-tool");
        boolean change = !recipe.equals("spindle-off");
        var first = new DrillGCodeParameters(3*s, 1.1*s, 100*s,
                recipe.equals("spindle-off") ? 0 : 10000, change,
                recipe.equals("peck"), .4*s, recipe.equals("dwell") || recipe.equals("zero-dwell"),
                recipe.equals("dwell") ? .25 : 0,
                recipe.equals("offset") ? .2*s : recipe.equals("negative-offset") ? -.2*s : 0);
        var second = new DrillGCodeParameters(4*s, 1.3*s, 75*s, 12000, change, true, .5*s, true, .5, .1*s);
        var image = ExcellonImage.of(units, Map.of(1, 1*s, 2, 2*s),
                List.of(new ExcellonImage.Drill(1, 10*s, 0), new ExcellonImage.Drill(2, 20*s, 0)),
                List.of(), factory.createPolygon());
        List<Integer> order = recipe.equals("selected-tool") ? List.of(2) : two ? List.of(1, 2) : List.of(1);
        var settings = new LinkedHashMap<Integer, DrillGCodeParameters>();
        settings.put(1, first); settings.put(2, second);
        var options = new GCodeGenerator.DrillJobOptions(change, 15*s, 5*s, null, null);
        if (recipe.equals("positions")) options = new GCodeGenerator.DrillJobOptions(true, 15*s, 5*s, 25*s, 2*s)
                .withPositions(12*s, -2*s, -3*s);
        if (recipe.equals("around") || recipe.equals("over")) options = options.withExclusions(true,
                List.of(CncExclusionArea.of(factory.toGeometry(new Envelope(4*s, 6*s, -s, s)),
                        recipe.equals("around") ? CncExclusionArea.Strategy.AROUND : CncExclusionArea.Strategy.OVER, 8*s)));
        String code = GCodeGenerator.generateDrillCncJob(image, settings, order, options,
                GCodePreprocessor.GRBL_11_NO_M6).gcode();
        assertFalse(code.isBlank());
        var tools = new JSONArray();
        for (int id : order) tools.put(new JSONObject().put("id", id).put("diameter", image.toolDiameters().get(id))
                .put("points", new JSONArray().put(new JSONArray().put(id*10*s).put(0)))
                .put("parameters", drillParameters(settings.get(id))));
        return base(units, recipe, "drilling", code).put("tools", tools).put("job", job(options));
    }

    private JSONObject geometry(String units, double s, String recipe) throws Exception {
        var shape = new WKTReader().read(recipe.endsWith("closed")
                ? "LINESTRING (10 0, 12 0, 12 2, 10 2, 10 0)" : "LINESTRING (10 0, 12 0)");
        shape = org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(s,s).transform(shape);
        var positions = new GeometryJobOptions(null, 5*s, null, null, 15*s, null, null);
        if (recipe.equals("positions")) positions = new GeometryJobOptions(12*s,5*s,25*s,2*s,15*s,-2*s,-3*s);
        var p = new GeometryGCodeParameters(3*s, 1.1*s, recipe.startsWith("peck") || recipe.startsWith("continuous"), .4*s, 200*s, 10000,
                true, 1500*s, null, 75*s, recipe.equals("dwell"), .25, false, 0).withJobOptions(positions);
        if(recipe.startsWith("continuous"))p=p.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        String code = GCodeGenerator.generateGeometryCncJob(units, List.of(new ToolGeometry(1*s,shape)),
                p, Map.of(), CancellationToken.none(), GCodePreprocessor.GRBL_11_NO_M6).gcode();
        var params = new JSONObject().put("safeZ",p.safeZ()).put("depth",p.cutDepth()).put("multiDepth",p.multiDepth())
                .put("depthPerPass",p.depthPerPass()).put("feed",p.feedRate()).put("feedZ",p.feedRateZ())
                .put("rpm",p.spindleSpeedRpm()).put("dwell",p.dwell()).put("dwellSeconds",p.dwellSeconds());
        var job = new JSONObject().put("toolChange",true).put("toolChangeZ",15*s).put("endZ",5*s)
                .put("startZ",nullable(positions.startZ())).put("endX",nullable(positions.endX()))
                .put("endY",nullable(positions.endY())).put("changeX",nullable(positions.toolChangeX()))
                .put("changeY",nullable(positions.toolChangeY())).put("exclusions",new JSONArray());
        return base(units,recipe,"geometry",code).put("tools",new JSONArray().put(new JSONObject().put("id",1)
                .put("diameter",1*s).put("wkt",new WKTWriter().write(shape)).put("parameters",params))).put("job",job);
    }

    private static JSONObject base(String units,String recipe,String operation,String code) {
        return new JSONObject().put("id",operation+"-"+recipe+"-"+units.toLowerCase(java.util.Locale.ROOT))
                .put("operation",operation).put("units",units).put("gcode",code);
    }
    private JSONObject multitoolGeometry(String units, double s, boolean continuous) throws Exception {
        var shapeA = factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(10*s, 0), new org.locationtech.jts.geom.Coordinate(12*s, 0)});
        var shapeB = factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(20*s, 2*s), new org.locationtech.jts.geom.Coordinate(22*s, 2*s)});
        var positions = new GeometryJobOptions(null, 5*s, 25*s, 2*s, 15*s, -2*s, -3*s);
        var first = new GeometryGCodeParameters(3*s, .6*s, continuous, .2*s, 200*s, 10000, true,
                1500*s, null, 75*s, true, .25, false, 0).withJobOptions(positions);
        var second = new GeometryGCodeParameters(4*s, .9*s, continuous, .3*s, 140*s, 12000, true,
                1500*s, null, 50*s, true, .5, false, 0).withJobOptions(positions);
        if (continuous) {
            first = first.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
            second = second.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        }
        String code = GCodeGenerator.generateGeometryCncJob(units,
                List.of(new ToolGeometry(.4*s, shapeA), new ToolGeometry(.8*s, shapeB)), first,
                Map.of(), Map.of(1, second), CancellationToken.none(), GCodePreprocessor.GRBL_11_NO_M6).gcode();
        var tools = new JSONArray();
        var settings = List.of(first, second); var paths = List.of(shapeA, shapeB);
        for (int i = 0; i < 2; i++) {
            var p = settings.get(i);
            tools.put(new JSONObject().put("id", i + 1).put("diameter", (i == 0 ? .4 : .8)*s)
                    .put("wkt", new WKTWriter().write(paths.get(i))).put("parameters", new JSONObject()
                            .put("safeZ", p.safeZ()).put("depth", p.cutDepth()).put("multiDepth", p.multiDepth())
                            .put("depthPerPass", p.depthPerPass()).put("feed", p.feedRate()).put("feedZ", p.feedRateZ())
                            .put("rpm", p.spindleSpeedRpm()).put("dwell", p.dwell()).put("dwellSeconds", p.dwellSeconds())));
        }
        var job = new JSONObject().put("toolChange", true).put("toolChangeZ", 15*s).put("endZ", 5*s)
                .put("startZ", JSONObject.NULL).put("endX", 25*s).put("endY", 2*s)
                .put("changeX", -2*s).put("changeY", -3*s).put("exclusions", new JSONArray());
        return base(units, continuous ? "multitool-continuous" : "multitool-single", "geometry", code)
                .put("tools", tools).put("job", job);
    }
    private static Object nullable(Double value) { return value == null ? JSONObject.NULL : value; }
    private static JSONObject drillParameters(DrillGCodeParameters p) {
        return new JSONObject().put("safeZ",p.safeZ()).put("depth",p.drillDepth()).put("feedZ",p.feedRate())
                .put("rpm",p.spindleSpeedRpm()).put("multiDepth",p.multiDepth()).put("depthPerPass",p.depthPerPass())
                .put("dwell",p.dwell()).put("dwellSeconds",p.dwellSeconds()).put("offsetZ",p.offsetZ());
    }
    private static JSONObject job(GCodeGenerator.DrillJobOptions p) {
        var exclusions = new JSONArray();
        for (var area : p.exclusions()) exclusions.put(new JSONObject().put("wkt",new WKTWriter().write(area.geometry()))
                .put("strategy",area.strategy().name().toLowerCase(java.util.Locale.ROOT)).put("overZ",area.overZ()));
        return new JSONObject().put("toolChange",p.pauseForToolChange()).put("toolChangeZ",p.toolChangeZ())
                .put("endZ",p.endMoveZ()).put("endX",nullable(p.endMoveX())).put("endY",nullable(p.endMoveY()))
                .put("startZ",nullable(p.startZ())).put("changeX",nullable(p.toolChangeX())).put("changeY",nullable(p.toolChangeY()))
                .put("exclusions",exclusions);
    }
}
