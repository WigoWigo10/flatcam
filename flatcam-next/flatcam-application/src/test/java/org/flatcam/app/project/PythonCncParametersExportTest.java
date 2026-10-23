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
    private final GCodePreprocessor profile=GCodePreprocessor.valueOf(System.getProperty("flatcam.cnc.comparison.profile","GRBL_11_NO_M6"));
    @TempDir Path temporary;
    private final GeometryFactory factory = new GeometryFactory();

    @Test void exportsMetricAndInchMachiningCases() throws Exception {
        var cases = new JSONArray();
        for (String units : List.of("MM", "IN")) {
            if (metricOnly() && units.equals("IN")) continue; // explicit profile constraint, not a skipped failure
            double scale = units.equals("IN") ? 1 / 25.4 : 1;
            for (String recipe : List.of("single", "peck", "offset", "negative-offset", "two-tools",
                    "selected-tool", "positions", "dwell", "zero-dwell", "spindle-off", "around", "over",
                    "over-rounded-diameter", "over-separated", "over-overlap")) {
                cases.put(drilling(units, scale, recipe));
            }
            for (String recipe : List.of("single", "peck-open", "peck-closed", "positions", "dwell", "continuous-open", "continuous-closed", "over")) {
                cases.put(geometry(units, scale, recipe));
            }
            cases.put(multitoolGeometry(units, scale, false));
            cases.put(multitoolGeometry(units, scale, true));
            cases.put(multitoolGeometry(units, scale, false, true));
            cases.put(multitoolGeometry(units, scale, true, true));
            for (boolean continuous:List.of(false,true)) for (boolean separated:List.of(false,true))
                cases.put(multitoolGeometry(units,scale,continuous,separated,GeometryTravelClearance.PER_TOOL));
            for (String recipe:List.of("ring-single","ring-continuous","ring-alternating","polygon-single","polygon-continuous",
                    "point-single","point-continuous","point-retract","closed-line-same")) cases.put(typedGeometry(units,scale,recipe));
            for (String recipe:List.of("source","nearest","peck-nearest","two-tools-nearest","slot-single","slot-peck",
                    "slot-chain-single","slot-chain-peck","slot-chain-no-last","slot-chain-zero-overlap","slot-chain-nearest","slot-chain-dwell"))
                cases.put(multipleDrills(units,scale,recipe));
        }
        assertEquals(metricOnly() ? 52 : 104, cases.length());
        var output = new JSONObject().put("schema", 1).put("profile", switch(profile) {
            case GRBL_11_NO_M6 -> "GRBL_11_no_M6";
            case GRBL_11 -> "grbl_11";
            case DEFAULT -> "default";
            case DEFAULT_NO_M6 -> "Default_no_M6";
            case MARLIN -> "Marlin";
            case LINE_XYZ -> "line_xyz";
            case ISEL_CNC -> "ISEL_CNC";
            case ISEL_ICP_CNC -> "ISEL_ICP_CNC";
            case TOOLCHANGE_MANUAL -> "Toolchange_Manual";
            case TOOLCHANGE_CUSTOM -> "Toolchange_Custom";
            default -> throw new IllegalArgumentException("Perfil ainda fora do leitor XYZ comparativo");
        })
                .put("scope", "Public synthetic CNC parameters; not UI, machine safety certification or all profiles")
                .put("excludedUnitCases",metricOnly()
                        ? new JSONArray().put("IN: " + profile + " FX supports MM only; not counted as equivalence") : new JSONArray())
                .put("cases", cases);
        Path directory = Path.of(System.getProperty("flatcam.cnc.comparison.output", temporary.toString()));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fx-cnc.json"), output.toString(2), StandardCharsets.UTF_8);
    }

    private boolean metricOnly() { return profile == GCodePreprocessor.ISEL_CNC || profile == GCodePreprocessor.ISEL_ICP_CNC; }

    private JSONObject drilling(String units, double s, String recipe) {
        boolean two = recipe.equals("two-tools") || recipe.equals("selected-tool");
        boolean change = !recipe.equals("spindle-off");
        var first = new DrillGCodeParameters(3*s, 1.1*s, 100*s,
                recipe.equals("spindle-off") ? 0 : 10000, change,
                recipe.equals("peck"), .4*s, recipe.equals("dwell") || recipe.equals("zero-dwell"),
                recipe.equals("dwell") ? .25 : 0,
                recipe.equals("offset") ? .2*s : recipe.equals("negative-offset") ? -.2*s : 0);
        var second = new DrillGCodeParameters(4*s, 1.3*s, 75*s, 12000, change, true, .5*s, true, .5, .1*s);
        double firstDiameter=recipe.equals("over-rounded-diameter") ? Math.rint(s*10000)/10000 : s;
        var image = ExcellonImage.of(units, Map.of(1, firstDiameter, 2, 2*s),
                List.of(new ExcellonImage.Drill(1, 10*s, 0), new ExcellonImage.Drill(2, 20*s, 0)),
                List.of(), factory.createPolygon());
        List<Integer> order = recipe.equals("selected-tool") ? List.of(2) : two ? List.of(1, 2) : List.of(1);
        var settings = new LinkedHashMap<Integer, DrillGCodeParameters>();
        settings.put(1, first); settings.put(2, second);
        var options = new GCodeGenerator.DrillJobOptions(change, 15*s, 5*s, null, null);
        if (recipe.equals("positions")) options = new GCodeGenerator.DrillJobOptions(true, 15*s, 5*s, 25*s, 2*s)
                .withPositions(12*s, -2*s, -3*s);
        if (recipe.equals("around") || recipe.startsWith("over")) options = options.withExclusions(true,
                List.of(CncExclusionArea.of(factory.toGeometry(new Envelope(4*s, 6*s, -s, s)),
                        recipe.equals("around") ? CncExclusionArea.Strategy.AROUND : CncExclusionArea.Strategy.OVER, 8*s)));
        if (recipe.equals("over-separated") || recipe.equals("over-overlap")) options=options.withExclusions(true,List.of(
                CncExclusionArea.of(factory.toGeometry(new Envelope(4*s,6*s,-s,s)),CncExclusionArea.Strategy.OVER,8*s),
                CncExclusionArea.of(factory.toGeometry(new Envelope((recipe.equals("over-overlap") ? 5 : 8)*s,
                        (recipe.equals("over-overlap") ? 7 : 9)*s,-s,s)),CncExclusionArea.Strategy.OVER,12*s)));
        String code = GCodeGenerator.generateDrillCncJob(image, settings, order, options,
                profile).gcode();
        assertFalse(code.isBlank());
        var tools = new JSONArray();
        for (int id : order) tools.put(new JSONObject().put("id", id).put("diameter", image.toolDiameters().get(id))
                .put("points", new JSONArray().put(new JSONArray().put(id*10*s).put(0)))
                .put("parameters", drillParameters(settings.get(id))));
        return base(units, recipe, "drilling", code).put("tools", tools).put("job", job(options,units));
    }

    private JSONObject geometry(String units, double s, String recipe) throws Exception {
        var shape = new WKTReader().read(recipe.endsWith("closed")
                ? "LINESTRING (10 0, 12 0, 12 2, 10 2, 10 0)" : "LINESTRING (10 0, 12 0)");
        shape = org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(s,s).transform(shape);
        var positions = new GeometryJobOptions(null, 5*s, null, null, 15*s, null, null);
        if (recipe.equals("positions")) positions = new GeometryJobOptions(12*s,5*s,25*s,2*s,15*s,-2*s,-3*s);
        if (recipe.equals("over")) positions=positions.withExclusions(true,List.of(CncExclusionArea.of(
                factory.toGeometry(new Envelope(4*s,6*s,-s,s)),CncExclusionArea.Strategy.OVER,8*s)));
        var p = new GeometryGCodeParameters(3*s, 1.1*s, recipe.startsWith("peck") || recipe.startsWith("continuous"), .4*s, 200*s, 10000,
                true, 1500*s, null, 75*s, recipe.equals("dwell"), .25, false, 0).withJobOptions(positions);
        if(recipe.startsWith("continuous"))p=p.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        String code = GCodeGenerator.generateGeometryCncJob(units, List.of(new ToolGeometry(1*s,shape)),
                p, Map.of(), CancellationToken.none(), profile).gcode();
        var params = new JSONObject().put("safeZ",p.safeZ()).put("depth",p.cutDepth()).put("multiDepth",p.multiDepth())
                .put("depthPerPass",p.depthPerPass()).put("feed",p.feedRate()).put("feedZ",p.feedRateZ())
                .put("rpm",p.spindleSpeedRpm()).put("dwell",p.dwell()).put("dwellSeconds",p.dwellSeconds());
        var areas=new JSONArray();
        for (var area:positions.exclusions()) areas.put(new JSONObject().put("wkt",area.wkt())
                .put("strategy",area.strategy().name().toLowerCase(java.util.Locale.ROOT)).put("overZ",area.overZ()));
        var job = new JSONObject().put("toolChange",true).put("toolChangeZ",15*s).put("endZ",5*s)
                .put("rapidFeed",p.rapidFeedRate())
                .put("startZ",nullable(positions.startZ())).put("endX",nullable(positions.endX()))
                .put("endY",nullable(positions.endY())).put("changeX",nullable(positions.toolChangeX()))
                .put("changeY",nullable(positions.toolChangeY())).put("exclusions",areas);
        return base(units,recipe,"geometry",code).put("tools",new JSONArray().put(new JSONObject().put("id",1)
                .put("diameter",1*s).put("wkt",new WKTWriter().write(shape)).put("parameters",params))).put("job",job);
    }

    private static JSONObject base(String units,String recipe,String operation,String code) {
        return new JSONObject().put("id",operation+"-"+recipe+"-"+units.toLowerCase(java.util.Locale.ROOT))
                .put("operation",operation).put("units",units).put("gcode",code);
    }
    private JSONObject multitoolGeometry(String units, double s, boolean continuous) throws Exception {
        return multitoolGeometry(units, s, continuous, false);
    }
    private JSONObject multitoolGeometry(String units, double s, boolean continuous, boolean separatedPaths) throws Exception {
        return multitoolGeometry(units,s,continuous,separatedPaths,GeometryTravelClearance.JOB_MAXIMUM);
    }
    private JSONObject multitoolGeometry(String units, double s, boolean continuous, boolean separatedPaths, GeometryTravelClearance policy) throws Exception {
        var shapeA = factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(10*s, 0), new org.locationtech.jts.geom.Coordinate(12*s, 0)});
        var shapeB = factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(20*s, 2*s), new org.locationtech.jts.geom.Coordinate(22*s, 2*s)});
        org.locationtech.jts.geom.Geometry firstPaths = separatedPaths ? factory.createMultiLineString(
                new org.locationtech.jts.geom.LineString[]{shapeA, factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                        new org.locationtech.jts.geom.Coordinate(14*s,0),new org.locationtech.jts.geom.Coordinate(16*s,0)})}) : shapeA;
        var positions = new GeometryJobOptions(null, 5*s, 25*s, 2*s, 15*s, -2*s, -3*s).withTravelClearance(policy);
        var first = new GeometryGCodeParameters(3*s, .6*s, continuous, .2*s, 200*s, 10000, true,
                1500*s, null, 75*s, true, .25, false, 0).withJobOptions(positions);
        var second = new GeometryGCodeParameters(4*s, .9*s, continuous, .3*s, 140*s, 12000, true,
                1500*s, null, 50*s, true, .5, false, 0).withJobOptions(positions);
        if (continuous) {
            first = first.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
            second = second.withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        }
        String code = GCodeGenerator.generateGeometryCncJob(units,
                List.of(new ToolGeometry(.4*s, firstPaths), new ToolGeometry(.8*s, shapeB)), first,
                Map.of(), Map.of(1, second), CancellationToken.none(), profile).gcode();
        var tools = new JSONArray();
        var settings = List.of(first, second); var paths = List.of(firstPaths, shapeB);
        for (int i = 0; i < 2; i++) {
            var p = settings.get(i);
            tools.put(new JSONObject().put("id", i + 1).put("diameter", (i == 0 ? .4 : .8)*s)
                    .put("wkt", new WKTWriter().write(paths.get(i))).put("parameters", new JSONObject()
                            .put("safeZ", p.safeZ()).put("depth", p.cutDepth()).put("multiDepth", p.multiDepth())
                            .put("depthPerPass", p.depthPerPass()).put("feed", p.feedRate()).put("feedZ", p.feedRateZ())
                            .put("rpm", p.spindleSpeedRpm()).put("dwell", p.dwell()).put("dwellSeconds", p.dwellSeconds())));
        }
        var job = new JSONObject().put("toolChange", true).put("toolChangeZ", 15*s).put("endZ", 5*s)
                .put("rapidFeed",first.rapidFeedRate())
                .put("fxTravelClearance",policy.name())
                .put("startZ", JSONObject.NULL).put("endX", 25*s).put("endY", 2*s)
                .put("changeX", -2*s).put("changeY", -3*s).put("exclusions", new JSONArray());
        return base(units, (policy==GeometryTravelClearance.PER_TOOL ? "individual-" : "")
                + (separatedPaths ? "multipath-" : "multitool-") + (continuous ? "continuous" : "single"), "geometry", code)
                .put("tools", tools).put("job", job);
    }
    private static Object nullable(Double value) { return value == null ? JSONObject.NULL : value; }
    private JSONObject typedGeometry(String units,double s,String recipe) throws Exception {
        var reader=new WKTReader();
        var shell=factory.createLinearRing(reader.read("LINESTRING (10 0,20 0,20 10,10 10,10 0)").getCoordinates());
        org.locationtech.jts.geom.Geometry shape=recipe.startsWith("point") ? factory.createPoint(new org.locationtech.jts.geom.Coordinate(10,0))
                : recipe.startsWith("polygon") ? factory.createPolygon(shell,new org.locationtech.jts.geom.LinearRing[]{
                    factory.createLinearRing(reader.read("LINESTRING (13 3,17 3,17 7,13 7,13 3)").getCoordinates())})
                : recipe.startsWith("closed-line") ? factory.createLineString(shell.getCoordinates()) : shell;
        shape=org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(s,s).transform(shape);
        boolean multiple=!recipe.endsWith("single");
        var strategy=recipe.endsWith("alternating") ? GeometryPassStrategy.CONTINUOUS_ALTERNATING
                : recipe.endsWith("retract") || !multiple ? GeometryPassStrategy.RETRACT_SAME_DIRECTION : GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION;
        var positions=new GeometryJobOptions(null,5*s,null,null,15*s,null,null);
        var p=new GeometryGCodeParameters(3*s,.6*s,multiple,.2*s,200*s,10000,true,1500*s,null,75*s,false,0,false,0)
                .withJobOptions(positions).withPassStrategy(strategy);
        String code=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(s,shape)),p,Map.of(),CancellationToken.none(),profile).gcode();
        // Standard WKT plus an explicit type: do not silently turn a ring into a LineString in the oracle.
        var wktShape=shape instanceof org.locationtech.jts.geom.LinearRing ? factory.createLineString(shape.getCoordinates()) : shape;
        var params=new JSONObject().put("safeZ",p.safeZ()).put("depth",p.cutDepth()).put("multiDepth",multiple)
                .put("depthPerPass",p.depthPerPass()).put("feed",p.feedRate()).put("feedZ",p.feedRateZ()).put("rpm",p.spindleSpeedRpm())
                .put("dwell",false).put("dwellSeconds",0).put("fxPassStrategy",strategy.name());
        var tool=new JSONObject().put("id",1).put("diameter",s).put("wkt",new WKTWriter().write(wktShape))
                .put("geometryType",shape.getGeometryType()).put("parameters",params);
        var job=new JSONObject().put("toolChange",true).put("toolChangeZ",15*s).put("endZ",5*s).put("startZ",JSONObject.NULL)
                .put("rapidFeed",p.rapidFeedRate())
                .put("endX",JSONObject.NULL).put("endY",JSONObject.NULL).put("changeX",JSONObject.NULL).put("changeY",JSONObject.NULL).put("exclusions",new JSONArray());
        return base(units,recipe,"geometry",code).put("tools",new JSONArray().put(tool)).put("job",job);
    }
    private JSONObject multipleDrills(String units,double s,String recipe) {
        boolean slot=recipe.startsWith("slot"); boolean two=recipe.equals("two-tools-nearest");
        boolean peck=recipe.contains("peck");
        boolean optimize=recipe.endsWith("nearest");
        var first=new DrillGCodeParameters(3*s,1.1*s,100*s,10000,true,peck,.4*s,recipe.endsWith("dwell"),.5,0);
        if (recipe.contains("chain")) first=first.withSlots(new org.flatcam.cam.gcode.DrillSlotParameters(true,
                recipe.endsWith("zero-overlap") ? 0 : 25, !recipe.endsWith("no-last")));
        var second=new DrillGCodeParameters(4*s,1.3*s,75*s,12000,true,true,.5*s,false,0,0);
        var drills=new java.util.ArrayList<ExcellonImage.Drill>();
        if (!slot) for (double x:new double[]{14,10,12,10}) drills.add(new ExcellonImage.Drill(1,x*s,0));
        if (two) for (double x:new double[]{30,24,26}) drills.add(new ExcellonImage.Drill(2,x*s,0));
        var slots=slot ? List.of(new ExcellonImage.Slot(1,10*s,0,12*s,0)) : List.<ExcellonImage.Slot>of();
        var image=ExcellonImage.of(units,Map.of(1,s,2,2*s),drills,slots,factory.createPolygon());
        var options=new GCodeGenerator.DrillJobOptions(true,15*s,5*s,null,null).withOptimizedDrills(optimize);
        String code=GCodeGenerator.generateDrillCncJob(image,Map.of(1,first,2,second),two ? List.of(1,2) : List.of(1),options,profile).gcode();
        var tools=new JSONArray();
        for (int id:two ? List.of(1,2) : List.of(1)) {
            var points=new JSONArray(); for (var drill:drills) if (drill.toolId()==id) points.put(new JSONArray().put(drill.x()).put(drill.y()));
            var slotPairs=new JSONArray(); for (var path:slots) if (path.toolId()==id) slotPairs.put(new JSONArray()
                    .put(new JSONArray().put(path.x1()).put(path.y1())).put(new JSONArray().put(path.x2()).put(path.y2())));
            tools.put(new JSONObject().put("id",id).put("diameter",id*s).put("points",points).put("slots",slotPairs)
                    .put("parameters",drillParameters(id==1 ? first : second)));
        }
        var job=job(options,units).put("fxDrillOrder",optimize ? "NEAREST" : "SOURCE");
        if (slot) job.put("pythonSlotOverlap",25).put("pythonSlotLastHole",true);
        return base(units,"multiple-"+recipe,"drilling",code).put("tools",tools).put("job",job);
    }
    private static JSONObject drillParameters(DrillGCodeParameters p) {
        return new JSONObject().put("safeZ",p.safeZ()).put("depth",p.drillDepth()).put("feedZ",p.feedRate()).put("feed",p.feedRate())
                .put("rpm",p.spindleSpeedRpm()).put("multiDepth",p.multiDepth()).put("depthPerPass",p.depthPerPass())
                .put("dwell",p.dwell()).put("dwellSeconds",p.dwellSeconds()).put("offsetZ",p.offsetZ())
                .put("fxSlotDrilling",p.slots().enabled()).put("slotOverlap",p.slots().overlapPercent()).put("slotLastHole",p.slots().addLastHole());
    }
    private static JSONObject job(GCodeGenerator.DrillJobOptions p,String units) {
        var exclusions = new JSONArray();
        for (var area : p.exclusions()) exclusions.put(new JSONObject().put("wkt",new WKTWriter().write(area.geometry()))
                .put("strategy",area.strategy().name().toLowerCase(java.util.Locale.ROOT)).put("overZ",area.overZ()));
        return new JSONObject().put("toolChange",p.pauseForToolChange()).put("toolChangeZ",p.toolChangeZ())
                .put("rapidFeed",p.rapidFeedRate()>0 ? p.rapidFeedRate() : units.equals("IN") ? 1500/25.4 : 1500)
                .put("endZ",p.endMoveZ()).put("endX",nullable(p.endMoveX())).put("endY",nullable(p.endMoveY()))
                .put("startZ",nullable(p.startZ())).put("changeX",nullable(p.toolChangeX())).put("changeY",nullable(p.toolChangeY()))
                .put("exclusions",exclusions);
    }
}
