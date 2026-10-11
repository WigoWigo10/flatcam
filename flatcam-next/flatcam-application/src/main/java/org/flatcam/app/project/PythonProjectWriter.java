package org.flatcam.app.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.app.project.flatprj.WktJson;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Python 8.994-compatible export. Private FX metadata supplements (never replaces) real Python objects. */
public final class PythonProjectWriter {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private PythonProjectWriter() { }

    public static void save(ProjectFile project, Path path) throws IOException { save(project, path, true); }

    public static void save(ProjectFile project, Path path, boolean compressed) throws IOException {
        JSONObject root = toRoot(project);
        ProjectFile.PythonLegacy legacy = project.pythonLegacy();
        if (legacy != null) {
            JSONObject baseline;
            try {
                // What the FX writes for the project exactly as it opened it: the reference for "unchanged".
                baseline = toRoot(PythonProjectIO.decode(legacy.original()));
            } catch (IOException | RuntimeException unavailable) {
                // The opened project has no legacy representation of its own: nothing to compare with.
                baseline = null;
            }
            if (baseline != null) root = PythonLegacyMerge.merge(ProjectFileIO.parseRoot(legacy.original()), baseline, root);
        }
        ProjectFileIO.writeRoot(root, path, compressed);
    }

    /** The project as a FlatCAM Python root object, from what the FX models alone. */
    static JSONObject toRoot(ProjectFile project) throws IOException {
        for (var entry : project.excellons()) {
            if (entry.cncSettings() != null && !entry.cncSettings().options().exclusions().isEmpty())
                throw new IOException("Exclusoes Drilling nao possuem representacao segura no projeto Python. Salve em .fcnproj e exporte o G-code validado. Objeto: " + entry.name());
        }
        for(var entry:project.geometries()) {
            if (entry.cncSettings() != null && !entry.cncSettings().jobDefaultsByTool().isEmpty())
                throw new IOException("Configuracoes comuns da DB ainda pendentes de revisao CNC: " + entry.name()
                        + ". Revise/gere CNC ou salve em .fcnproj; o Python nao representa esses conflitos.");
            var common=entry.cncDefaults();
            boolean hasAreas=common!=null && !common.jobOptions().exclusions().isEmpty()
                    || entry.cncSettings()!=null && entry.cncSettings().parametersByTool().values().stream().anyMatch(p -> !p.jobOptions().exclusions().isEmpty());
            if(hasAreas) throw new IOException("Exclusoes CNC nao possuem representacao segura no projeto Python. Salve em .fcnproj e exporte o G-code validado. Objeto: "+entry.name());
        }
        JSONObject root = ProjectFileIO.toJson(project);
        root.put("version", 8.994).put("_fx_format", 1);
        JSONArray objects = root.getJSONArray("objs");
        String units = !project.gerbers().isEmpty() ? project.gerbers().getFirst().image().units()
                : !project.excellons().isEmpty() ? project.excellons().getFirst().image().units()
                : !project.geometries().isEmpty() ? project.geometries().getFirst().units()
                : !project.cncJobs().isEmpty() && project.cncJobs().getFirst().gcode() != null
                        ? GCodeToolpathParser.parse(project.cncJobs().getFirst().gcode(), CancellationToken.none(), ignored -> {}).units() : "MM";
        root.getJSONObject("options").put("units", units);
        if (project.gerbers().stream().anyMatch(e -> !e.image().units().equalsIgnoreCase(units))
                || project.excellons().stream().anyMatch(e -> !e.image().units().equalsIgnoreCase(units))
                || project.geometries().stream().anyMatch(e -> !e.units().equalsIgnoreCase(units)))
            throw new IOException("Projeto com unidades mistas exige formato nativo FX; Python converte objetos para a unidade global ao abrir.");
        for (int i = 0; i < objects.length(); i++) {
            JSONObject object = objects.getJSONObject(i), extra = object.getJSONObject("_java");
            extra.put("fxOriginalFill", object.opt("fill_color")).put("fxOriginalStroke", object.opt("outline_color"));
            JSONObject options = object.getJSONObject("options");
            colors(object, object.optString("fill_color", null), object.optString("outline_color", null));
            object.put("solid_geometry", parts(WktJson.unwrap(object.opt("solid_geometry"))));
            options.put("plot", extra.optBoolean("visible", true)).put("solid", extra.optBoolean("filled", true))
                    .put("multicolored", extra.optBoolean("multicolor", false));
            if ("excellon".equals(object.getString("kind"))) {
                ProjectFile.ExcellonEntry entry = project.excellons().stream()
                        .filter(e -> e.name().equals(options.getString("name"))).findFirst().orElseThrow();
                JSONObject tools = object.getJSONObject("tools");
                for (String id : tools.keySet()) {
                    JSONObject tool = tools.getJSONObject(id);
                    double radius = tool.getDouble("tooldia") / 2;
                    // Python plots Excellon per-tool solids, not only the top-level geometry.
                    JSONArray solids = new JSONArray();
                    for (var drill : entry.image().drills()) if (drill.toolId() == Integer.parseInt(id))
                        solids.put(WktJson.wrap(FACTORY.createPoint(new org.locationtech.jts.geom.Coordinate(drill.x(),drill.y())).buffer(radius,16)));
                    for (var slot : entry.image().slots()) if (slot.toolId() == Integer.parseInt(id))
                        solids.put(WktJson.wrap(FACTORY.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                                new org.locationtech.jts.geom.Coordinate(slot.x1(),slot.y1()),
                                new org.locationtech.jts.geom.Coordinate(slot.x2(),slot.y2())}).buffer(radius,16)));
                    tool.put("solid_geometry", solids);
                    var p = entry.drillDefaults().get(Integer.parseInt(id));
                    if (p != null) tool.getJSONObject("data").put("tools_drill_cutz", -p.drillDepth())
                            .put("tools_drill_travelz",p.safeZ()).put("tools_drill_feedrate_z",p.feedRate())
                            .put("tools_drill_spindlespeed",p.spindleSpeedRpm()).put("tools_drill_multidepth",p.multiDepth())
                            .put("tools_drill_depthperpass",p.depthPerPass()).put("tools_drill_dwell",p.dwell())
                            .put("tools_drill_dwelltime",p.dwellSeconds()).put("tools_drill_offset",p.offsetZ());
                    // Explicit ordinary positions require their common settings in legacy keys too.
                    // Do not broaden the existing probe/machine-specific exporter here.
                    if (entry.cncSettings() != null && (entry.cncSettings().options().startZ() != null
                            || entry.cncSettings().options().toolChangeX() != null)) {
                        var settings = entry.cncSettings();
                        var moves = settings.options();
                        JSONObject data = tool.getJSONObject("data");
                        data.put("tools_drill_startz", moves.startZ() == null ? JSONObject.NULL : moves.startZ())
                                .put("tools_drill_toolchangexy", legacyXY(moves.toolChangeX(), moves.toolChangeY()))
                                .put("tools_drill_toolchange", moves.pauseForToolChange())
                                .put("tools_drill_toolchangez", moves.toolChangeZ())
                                .put("tools_drill_endz", moves.endMoveZ())
                                .put("tools_drill_endxy", legacyXY(moves.endMoveX(), moves.endMoveY()))
                                .put("tools_drill_feedrate_rapid", moves.rapidFeedRate())
                                .put("tools_drill_ppname_e", pythonProfile(settings.preprocessor()));
                        for (String key : List.of("tools_drill_startz", "tools_drill_toolchangexy", "tools_drill_toolchange",
                                "tools_drill_toolchangez", "tools_drill_endz", "tools_drill_endxy", "tools_drill_feedrate_rapid", "tools_drill_ppname_e"))
                            options.put(key, data.get(key));
                    }
                }
            }
        }
        for (var entry : project.geometries()) objects.put(geometry(entry));
        for (var job : project.cncJobs()) {
            JSONObject object = cncJob(job);
            if (!object.getString("units").equalsIgnoreCase(units))
                throw new IOException("CNC Job com unidade diferente do projeto: " + job.name() + ". Use .fcnproj.");
            objects.put(object);
        }
        return root;
    }

    private static JSONObject geometry(ProjectFile.GeometryEntry entry) {
        JSONObject object = new JSONObject().put("kind", "geometry").put("units", entry.units())
                .put("solid_geometry", parts(entry.geometry())).put("follow_geometry", new JSONArray())
                .put("multigeo", !entry.tools().isEmpty()).put("options", options(entry.name(), entry.visible(), entry.geometry()));
        if (entry.geometry() == null || entry.geometry().isEmpty()) {
            // A blank Geometry, written the way the Python application writes its own ("New Geometry"): no solid
            // geometry at all, multigeo, zero bounds. With an empty list instead, Python computes infinite bounds
            // when it opens the project and then cannot save it ("Out of range float values are not JSON compliant").
            object.put("solid_geometry", JSONObject.NULL).put("multigeo", true);
            object.getJSONObject("options").put("xmin", 0).put("ymin", 0).put("xmax", 0).put("ymax", 0);
        }
        JSONObject tools = new JSONObject();
        List<ToolGeometry> paths = entry.tools().isEmpty()
                ? List.of(new ToolGeometry(entry.cncSettings() != null && entry.cncSettings().singleToolDiameter() != null
                        ? entry.cncSettings().singleToolDiameter() : "MM".equalsIgnoreCase(entry.units()) ? 0.8 : 0.8/25.4,
                        entry.geometry(),entry.cncSettings() == null ? org.flatcam.cam.geometry.ToolProfile.C1
                                : entry.cncSettings().singleToolProfile())) : entry.tools();
        for (int i = 0; i < paths.size(); i++) {
            ToolGeometry tool = paths.get(i);
            if (tool.toolProfile() == org.flatcam.cam.geometry.ToolProfile.V
                    && (entry.cncSettings() == null || !entry.cncSettings().vTools().containsKey(i)))
                throw new IllegalArgumentException("Ferramenta V sem configuracao explicita de ponta: " + entry.name());
            var p = entry.cncSettings() == null ? entry.cncDefaults()
                    : entry.cncSettings().parametersByTool().getOrDefault(i, entry.cncDefaults());
            JSONObject data = p == null ? new JSONObject() : machining(p);
            var common = entry.cncDefaults() == null ? p : entry.cncDefaults();
            var positions = common == null ? org.flatcam.cam.gcode.GeometryJobOptions.AUTOMATIC : common.jobOptions();
            double clearance = common == null ? 2 : common.safeZ();
            if (entry.cncSettings() != null) clearance = Math.max(clearance, entry.cncSettings().parametersByTool().values()
                    .stream().mapToDouble(GeometryGCodeParameters::safeZ).max().orElse(clearance));
            data.put("endz",positions.endZ() == null ? clearance : positions.endZ()).put("endxy", legacyXY(positions.endX(), positions.endY()))
                    .put("startz",positions.startZ() == null ? JSONObject.NULL : positions.startZ())
                    .put("toolchangez",positions.toolChangeZ() == null ? clearance : positions.toolChangeZ())
                    .put("toolchangexy",legacyXY(positions.toolChangeX(), positions.toolChangeY()))
                    .put("area_exclusion",false).put("area_shape","polygon").put("area_strategy","over").put("area_overz",1)
                    .put("ppname_g","Default_no_M6").put("vtipdia",0.1).put("vtipangle",30);
            data.put("name",entry.name()).put("tooldia",tool.toolDiameter());
            if (entry.cncSettings() != null) {
                if (entry.cncSettings().preprocessor().requiresProbe())
                    throw new IllegalArgumentException("Geometry com sondagem exige formato nativo FX; exportacao da configuracao de sonda nao suportada.");
                data.put("ppname_g", pythonProfile(entry.cncSettings().preprocessor()));
                VTipSettings tip = entry.cncSettings().vTools().get(i);
                if (tip != null) data.put("vtipdia",tip.tipDiameter()).put("vtipangle",tip.angleDegrees())
                        .put("cutz",-tip.cutDepth(tool.toolDiameter()));
            }
            tools.put(Integer.toString(i+1), new JSONObject().put("tooldia",tool.toolDiameter())
                    .put("tool_type",tool.toolProfile().name()).put("type","Rough").put("offset",p == null ? "Path" : p.offset().label())
                    .put("offset_value",p == null ? 0 : p.offset().distance(tool.toolDiameter(), p.customOffset()))
                    .put("data",data).put("solid_geometry",parts(tool.geometry())));
            if (i == 0) {
                JSONObject options = object.getJSONObject("options");
                for (String key : List.of("startz", "endz", "endxy", "toolchangez", "toolchangexy", "toolchange"))
                    if (data.has(key)) options.put(key, data.get(key));
            }
        }
        object.put("tools",tools);
        colors(object, entry.fillColorWeb(), entry.strokeColorWeb());
        return object;
    }

    private static String legacyXY(Double x, Double y) { return x == null ? "" : x + ", " + y; }

    private static JSONObject machining(GeometryGCodeParameters p) {
        return new JSONObject().put("travelz",p.safeZ()).put("cutz",-p.cutDepth()).put("multidepth",p.multiDepth())
                .put("depthperpass",p.depthPerPass()).put("feedrate",p.feedRate()).put("feedrate_z",p.feedRateZ())
                .put("feedrate_rapid",p.rapidFeedRate()).put("spindlespeed",p.spindleSpeedRpm())
                .put("toolchange",p.pauseForToolChange()).put("dwell",p.dwell()).put("dwelltime",p.dwellSeconds())
                .put("extracut",p.extraCut()).put("extracut_length",p.extraCutLength());
    }

    static String pythonProfile(GCodePreprocessor pp) {
        return pp == GCodePreprocessor.FX_PORTABLE ? "Default_no_M6" : pp.label().split(" \\(")[0];
    }

    private static JSONObject cncJob(ProjectFile.CncJobRecord job) throws IOException {
        if (job.gcode() == null || job.gcode().isBlank()) throw new IOException("CNC Job sem codigo embutido: " + job.name());
        GCodeToolpathParser.Result preview = GCodeToolpathParser.parse(job.gcode(), CancellationToken.none(), ignored -> {});
        if (!preview.plotAvailable()) throw new IOException("CNC Job nao exportavel ao Python com previa segura: " + job.name() + ": " + preview.warning());
        if (GCodeToolpathParser.isIcpProgram(job.gcode()) || GCodeToolpathParser.isHpglProgram(job.gcode())
                || GCodeToolpathParser.isRolandProgram(job.gcode()))
            throw new IOException("Programa ICP/HPGL/Roland exige formato nativo FX: " + job.name());
        JSONArray parsed = new JSONArray();
        addParsed(parsed, preview.travelCenterlines() == null ? preview.travelGeometry() : preview.travelCenterlines(), "T");
        addParsed(parsed, preview.cutCenterlines() == null ? preview.cutGeometry() : preview.cutCenterlines(), "C");
        double dia = preview.stats() == null || preview.stats().cutterDiameter() == null ? 0 : preview.stats().cutterDiameter();
        JSONObject options = options(job.name() == null ? Path.of(job.outputPath()).getFileName().toString() : job.name(),
                job.visible(), preview.cutGeometry()).put("tooldia", dia).put("type", "Geometry");
        return new JSONObject().put("kind","cncjob").put("units",preview.units()).put("options",options)
                .put("gcode",job.gcode()).put("gcode_parsed",parsed).put("solid_geometry",WktJson.wrap(preview.cutGeometry()))
                .put("follow_geometry",new JSONArray()).put("tools",new JSONObject()).put("multitool",false)
                .put("origin_kind","geometry").put("cnc_tools",new JSONObject()).put("exc_cnc_tools",new JSONObject())
                .put("tooldia",dia).put("append_snippet","").put("prepend_snippet","").put("gc_header","")
                .put("input_geometry_bounds",new JSONArray());
    }

    private static void addParsed(JSONArray values, Geometry paths, String kind) {
        if (paths == null || paths.isEmpty()) return;
        for (int i = 0; i < paths.getNumGeometries(); i++) {
            Geometry part = paths.getGeometryN(i);
            if (part instanceof org.locationtech.jts.geom.GeometryCollection) addParsed(values,part,kind);
            else values.put(new JSONObject().put("geom",WktJson.wrap(part)).put("kind",new JSONArray(List.of(kind,"F"))));
        }
    }

    private static JSONObject options(String name, boolean visible, Geometry geometry) {
        JSONObject result = new JSONObject().put("name",name).put("plot",visible);
        var bounds = geometry.getEnvelopeInternal();
        if (!bounds.isNull()) result.put("xmin",bounds.getMinX()).put("ymin",bounds.getMinY())
                .put("xmax",bounds.getMaxX()).put("ymax",bounds.getMaxY());
        return result;
    }

    private static void colors(JSONObject object, String fill, String stroke) {
        object.put("fill_color",webColor(fill, "#99cc33bf")).put("outline_color",webColor(stroke, "#006600ff"))
                .put("alpha_level","bf");
    }

    private static String webColor(String value, String fallback) {
        if (value == null) return fallback;
        // One spelling for one colour ("#112233", "0x112233ff" and "#112233FF" are the same), so that a colour the
        // application merely passed through is not mistaken for a change when a Python project is saved back.
        String hex = value.startsWith("0x") ? value.substring(2) : value.startsWith("#") ? value.substring(1) : null;
        if (hex == null || !hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return value;
        return "#" + (hex.length() == 6 ? hex + "ff" : hex).toLowerCase(java.util.Locale.ROOT);
    }

    private static JSONArray parts(Geometry geometry) {
        JSONArray values = new JSONArray();
        if (geometry == null || geometry.isEmpty()) return values;
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof org.locationtech.jts.geom.GeometryCollection) {
                for (Object value : parts(part)) values.put(value);
            } else values.put(WktJson.wrap(part));
        }
        return values;
    }
}
