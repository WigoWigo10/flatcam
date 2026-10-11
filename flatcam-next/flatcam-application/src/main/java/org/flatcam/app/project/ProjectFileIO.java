package org.flatcam.app.project;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.ProbeToolChangeParameters;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator.DrillJobOptions;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;
import org.tukaani.xz.XZ;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/**
 * Reads/writes the native {@code .fcnproj} format. Its outer JSON tree and
 * Gerber/Excellon WKT encoding follow the Python {@code .FlatPrj} conventions,
 * and it supports plain JSON or XZ compression. This is not yet a full
 * {@code .FlatPrj} reader/writer: the native version is 2, whereas Python
 * writes 8.994. {@link PythonProjectIO} handles Python imports and
 * {@link PythonProjectWriter} provides a separate compatibility exporter;
 * this class accepts only native project versions 1/2.
 * CNC Job and Geometry entries are carried in a {@code "_java"} extension key a real FlatCAM
 * Python install would simply ignore (unknown top-level keys are never
 * consulted by its loader) - see {@link ProjectFile}'s own doc for what's
 * intentionally not part of Python's {@code objs} list yet.
 *
 * <p>Still uses the {@code .fcnproj} extension by convention (not
 * {@code .FlatPrj}) so a file's extension keeps telling a user which app
 * saved it. Compatibility export is validated with the legacy Python serializers;
 * see {@link PythonProjectWriter} for supported objects and restrictions.
 */
public final class ProjectFileIO {

    private static final int CURRENT_VERSION = 2;
    private static final int LEGACY_V1_VERSION = 1;
    // Interactive saves prioritize latency over the last few percent of compression.
    // Same XZ/JSON format; no rounding, simplification or loss of project data.
    private static final int XZ_PRESET = 1;

    private static final String PYTHON_ORIGINAL = "pythonOriginal";

    private ProjectFileIO() {
    }

    public static void save(ProjectFile project, Path path) throws IOException {
        save(project, path, true);
    }

    public static void save(ProjectFile project, Path path, boolean compress) throws IOException {
        long start = System.nanoTime();
        JSONObject root = toJson(project);
        if (project.pythonLegacy() != null) {
            // The Python project this one came from rides along, so a later save as .FlatPrj still has it.
            root.getJSONObject("_java").put(PYTHON_ORIGINAL,
                    java.util.Base64.getEncoder().encodeToString(project.pythonLegacy().original()));
        }
        logPhase("JSON/WKT encode", start);
        writeRoot(root, path, compress);
    }

    static JSONObject toJson(ProjectFile project) {
        JSONArray objs = new JSONArray();
        for (ProjectFile.GerberEntry gerber : project.gerbers()) {
            objs.put(GerberFlatPrjCodec.toJson(gerber.name(), gerber.image(), gerber.fillColorWeb(),
                    gerber.strokeColorWeb(), gerber.visible(), gerber.filled(), gerber.multicolor(), gerber.followMode()));
        }
        for (ProjectFile.ExcellonEntry excellon : project.excellons()) {
            JSONObject object = ExcellonFlatPrjCodec.toJson(excellon.name(), excellon.image(),
                    excellon.fillColorWeb(), excellon.strokeColorWeb(), excellon.visible(),
                    excellon.filled(), excellon.multicolor());
            object.getJSONObject("_java").put("drillDefaults", drillDefaultsToJson(excellon.drillDefaults()));
            if (excellon.cncSettings() != null)
                object.getJSONObject("_java").put("cncSettings", drillSettingsToJson(excellon.cncSettings()));
            objs.put(object);
        }

        JSONObject root = new JSONObject();
        root.put("objs", objs);
        root.put("options", new JSONObject());
        root.put("version", CURRENT_VERSION);

        JSONArray jobs = new JSONArray();
        for (ProjectFile.CncJobRecord job : project.cncJobs()) {
            JSONObject jobJson = new JSONObject();
            if (job.name() != null) {
                jobJson.put("name", job.name());
            }
            jobJson.put("sourceName", job.sourceName());
            jobJson.put("outputPath", job.outputPath());
            jobJson.put("visible", job.visible());
            if (job.gcode() != null) {
                jobJson.put("gcode", job.gcode());
            }
            jobs.put(jobJson);
        }
        JSONObject javaExtra = new JSONObject();
        javaExtra.put("cncJobs", jobs);
        javaExtra.put("importWarnings", new JSONArray(project.importWarnings()));
        JSONArray geometries = new JSONArray();
        WKTWriter wktWriter = new WKTWriter();
        for (ProjectFile.GeometryEntry entry : project.geometries()) {
            JSONObject geometryJson = new JSONObject();
            geometryJson.put("name", entry.name());
            geometryJson.put("sourceName", entry.sourceName());
            geometryJson.put("units", entry.units());
            geometryJson.put("wkt", wktWriter.write(entry.geometry()));
            geometryJson.put("strokeOnly", entry.strokeOnly());
            geometryJson.put("visible", entry.visible());
            if (entry.cncDefaults() != null) {
                GeometryGCodeParameters defaults = entry.cncDefaults();
                geometryJson.put("cncDefaults", geometryParametersToJson(defaults));
            }
            if (entry.cncSettings() != null) {
                GeometryCncSettings settings = entry.cncSettings();
                JSONObject vTools = new JSONObject();
                settings.vTools().forEach((id, tip) -> vTools.put(id.toString(), new JSONObject()
                        .put("tipDiameter", tip.tipDiameter()).put("angleDegrees", tip.angleDegrees())));
                geometryJson.put("cncSettings", new JSONObject().put("preprocessor", settings.preprocessor().name())
                        .put("singleToolDiameter", settings.singleToolDiameter()).put("vTools", vTools));
                JSONObject byTool = new JSONObject();
                settings.parametersByTool().forEach((id, p) -> byTool.put(id.toString(), geometryParametersToJson(p)));
                geometryJson.getJSONObject("cncSettings").put("parametersByTool", byTool);
                geometryJson.getJSONObject("cncSettings").put("singleToolProfile", settings.singleToolProfile().name());
                JSONObject jobDefaults = new JSONObject();
                settings.jobDefaultsByTool().forEach((id, p) -> jobDefaults.put(id.toString(), p.toJson()));
                geometryJson.getJSONObject("cncSettings").put("jobDefaultsByTool", jobDefaults);
            }
            if (entry.fillColorWeb() != null) {
                geometryJson.put("fillColor", entry.fillColorWeb());
            }
            if (entry.strokeColorWeb() != null) {
                geometryJson.put("strokeColor", entry.strokeColorWeb());
            }
            JSONArray tools = new JSONArray();
            for (ToolGeometry tool : entry.tools()) {
                tools.put(new JSONObject().put("diameter", tool.toolDiameter())
                        .put("toolType", tool.toolProfile().name())
                        .put("wkt", wktWriter.write(tool.geometry())));
            }
            geometryJson.put("tools", tools);
            geometries.put(geometryJson);
        }
        javaExtra.put("geometries", geometries);
        root.put("_java", javaExtra);

        return root;
    }

    static void writeRoot(JSONObject root, Path path, boolean compress) throws IOException {
        long start = System.nanoTime();
        byte[] jsonBytes = root.toString().getBytes(StandardCharsets.UTF_8);
        logPhase("JSON UTF-8 encode", start);
        start = System.nanoTime();
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(),
                "." + destination.getFileName() + ".", ".tmp");
        try {
            if (compress) {
                try (OutputStream fileOut = Files.newOutputStream(temporary);
                     XZOutputStream xzOut = new XZOutputStream(fileOut, new LZMA2Options(XZ_PRESET))) {
                    xzOut.write(jsonBytes);
                }
            } else {
                Files.write(temporary, jsonBytes);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        logPhase(compress ? "XZ compress/write" : "plain write", start);
    }

    public static ProjectFile load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        JSONObject root = parseRoot(raw);
        long start = System.nanoTime();
        ProjectFile project = fromJson(root);
        logPhase("objects/WKT decode", start);
        JSONObject javaExtra = root.optJSONObject("_java");
        String pythonOriginal = javaExtra == null ? null : javaExtra.optString(PYTHON_ORIGINAL, null);
        if (pythonOriginal != null) {
            try {
                project = project.withPythonLegacy(
                        new ProjectFile.PythonLegacy(java.util.Base64.getDecoder().decode(pythonOriginal)));
            } catch (IllegalArgumentException damaged) {
                // Not valid Base64: the project itself is intact, only the way back to Python is gone.
            }
        }
        return project;
    }

    static ProjectFile fromJson(JSONObject root) throws IOException {
        int version = root.optInt("version", -1);
        if (version == LEGACY_V1_VERSION) {
            return loadLegacyV1(root);
        }
        if (version != CURRENT_VERSION) {
            throw new IOException("Unsupported project file version: " + version);
        }

        List<ProjectFile.GerberEntry> gerbers = new ArrayList<>();
        List<ProjectFile.ExcellonEntry> excellons = new ArrayList<>();
        JSONArray objs = root.optJSONArray("objs");
        if (objs != null) {
            for (int i = 0; i < objs.length(); i++) {
                JSONObject obj = objs.getJSONObject(i);
                switch (obj.optString("kind", "")) {
                    case "gerber" -> {
                        GerberFlatPrjCodec.Decoded decoded = GerberFlatPrjCodec.fromJson(obj);
                        gerbers.add(new ProjectFile.GerberEntry(decoded.name(), decoded.image(),
                                decoded.fillColorWeb(), decoded.strokeColorWeb(), decoded.visible(),
                                decoded.filled(), decoded.multicolor(), decoded.followMode()));
                    }
                    case "excellon" -> {
                        ExcellonFlatPrjCodec.Decoded decoded = ExcellonFlatPrjCodec.fromJson(obj);
                        excellons.add(new ProjectFile.ExcellonEntry(decoded.name(), decoded.image(),
                                decoded.fillColorWeb(), decoded.strokeColorWeb(), decoded.visible(),
                                decoded.filled(), decoded.multicolor(),
                                readDrillDefaults(obj.optJSONObject("_java")),
                                readDrillSettings(obj.optJSONObject("_java"))));
                    }
                    default -> {
                        // Not-yet-embedded kinds (geometry/cncjob-as-a-Python-obj) - see ProjectFile's doc.
                    }
                }
            }
        }

        return new ProjectFile(gerbers, excellons, readJavaGeometries(root), readJavaCncJobs(root),
                readImportWarnings(root));
    }

    private static List<String> readImportWarnings(JSONObject root) {
        JSONObject javaExtra = root.optJSONObject("_java");
        JSONArray array = javaExtra == null ? null : javaExtra.optJSONArray("importWarnings");
        List<String> warnings = new ArrayList<>();
        if (array != null) {
            for (int index = 0; index < array.length(); index++) {
                warnings.add(array.getString(index));
            }
        }
        return warnings;
    }

    private static JSONObject drillDefaultsToJson(Map<Integer, DrillGCodeParameters> defaults) {
        JSONObject result = new JSONObject();
        for (Map.Entry<Integer, DrillGCodeParameters> entry : defaults.entrySet()) {
            DrillGCodeParameters value = entry.getValue();
            result.put(Integer.toString(entry.getKey()), new JSONObject()
                    .put("safeZ", value.safeZ()).put("drillDepth", value.drillDepth())
                    .put("feedRate", value.feedRate()).put("spindleSpeedRpm", value.spindleSpeedRpm())
                    .put("pauseForToolChange", value.pauseForToolChange())
                    .put("multiDepth", value.multiDepth()).put("depthPerPass", value.depthPerPass())
                    .put("dwell", value.dwell()).put("dwellSeconds", value.dwellSeconds())
                    .put("offsetZ", value.offsetZ()));
        }
        return result;
    }

    private static JSONObject probeToJson(ProbeToolChangeParameters probe) {
        return new JSONObject().put("toolChangeZ", probe.toolChangeZ()).put("probeDepth", probe.probeDepth())
                .put("feedRate", probe.feedRate()).put("contactZ", probe.contactZ())
                .put("toolChangeX", probe.toolChangeX()).put("toolChangeY", probe.toolChangeY());
    }

    private static JSONObject drillSettingsToJson(DrillCncSettings settings) {
        DrillJobOptions options = settings.options();
        JSONObject json = new JSONObject().put("preprocessor", settings.preprocessor().name())
                .put("toolOrder", settings.toolOrder().name()).put("selectedToolIds", new JSONArray(settings.selectedToolIds()))
                .put("pauseForToolChange", options.pauseForToolChange()).put("toolChangeZ", options.toolChangeZ())
                .put("endMoveZ", options.endMoveZ()).put("endMoveX", options.endMoveX()).put("endMoveY", options.endMoveY())
                .put("rapidFeedRate", options.rapidFeedRate())
                .put("startZ", options.startZ()).put("toolChangeX", options.toolChangeX()).put("toolChangeY", options.toolChangeY())
                .put("exclusionsEnabled", options.exclusionsEnabled())
                .put("exclusions", new JSONArray(options.exclusions().stream().map(area -> new JSONObject()
                        .put("wkt", area.wkt()).put("strategy", area.strategy().name()).put("overZ", area.overZ())).toList()));
        if (options.probing() != null) json.put("probing", probeToJson(options.probing()));
        return json;
    }

    private static DrillCncSettings readDrillSettings(JSONObject javaExtra) throws IOException {
        try {
            JSONObject json = optionalObject(javaExtra, "cncSettings");
            if (json == null) return null;
            List<Integer> selected = new ArrayList<>();
            JSONArray ids = json.getJSONArray("selectedToolIds");
            for (int i = 0; i < ids.length(); i++) selected.add(ids.getInt(i));
            return new DrillCncSettings(GCodePreprocessor.valueOf(json.getString("preprocessor")),
                    new DrillJobOptions(json.getBoolean("pauseForToolChange"), json.getDouble("toolChangeZ"),
                            json.getDouble("endMoveZ"), optionalDouble(json, "endMoveX"), optionalDouble(json, "endMoveY"),
                            json.optDouble("rapidFeedRate", 0), readProbeParameters(json.optJSONObject("probing")),
                            json.has("exclusionsEnabled") && json.getBoolean("exclusionsEnabled"),
                            readCncExclusions(json.has("exclusions") ? json.getJSONArray("exclusions") : null),
                            optionalDouble(json, "startZ"), optionalDouble(json, "toolChangeX"), optionalDouble(json, "toolChangeY")),
                    selected, DrillCncSettings.ToolOrder.valueOf(json.getString("toolOrder")));
        } catch (RuntimeException invalid) {
            throw new IOException("Configuracao CNC de Drilling invalida; nenhum perfil alternativo foi aplicado.", invalid);
        }
    }

    private static JSONObject optionalObject(JSONObject json, String key) {
        return json == null || !json.has(key) || json.isNull(key) ? null : json.getJSONObject(key);
    }

    private static Double optionalDouble(JSONObject json, String key) {
        return json.has(key) && !json.isNull(key) ? json.getDouble(key) : null;
    }

    private static GeometryCncSettings readGeometrySettings(JSONObject json) {
        if (json == null) return null;
        Map<Integer, VTipSettings> tips = new LinkedHashMap<>();
        JSONObject values = json.optJSONObject("vTools");
        if (values != null) for (String id : values.keySet()) {
            JSONObject tip = values.getJSONObject(id);
            tips.put(Integer.parseInt(id), new VTipSettings(tip.getDouble("tipDiameter"), tip.getDouble("angleDegrees")));
        }
        return new GeometryCncSettings(GCodePreprocessor.valueOf(json.getString("preprocessor")),
                optionalDouble(json, "singleToolDiameter"), tips, readGeometryToolParameters(json.optJSONObject("parametersByTool")),
                ToolProfile.fromLegacy(json.optString("singleToolProfile","C1")), readJobDefaults(json.optJSONObject("jobDefaultsByTool")));
    }

    private static Map<Integer, CncJobDefaults> readJobDefaults(JSONObject values) {
        var result = new LinkedHashMap<Integer, CncJobDefaults>();
        if (values != null) for (String id : values.keySet())
            result.put(Integer.parseInt(id), CncJobDefaults.fromJson(values.getJSONObject(id)));
        return Map.copyOf(result);
    }

    private static Map<Integer, GeometryGCodeParameters> readGeometryToolParameters(JSONObject values) {
        if (values == null) return Map.of();
        Map<Integer, GeometryGCodeParameters> result = new LinkedHashMap<>();
        for (String id : values.keySet()) result.put(Integer.parseInt(id), readGeometryCncDefaults(values.getJSONObject(id)));
        return Map.copyOf(result);
    }

    static JSONObject geometryParametersToJson(GeometryGCodeParameters p) {
        JSONObject value = new JSONObject().put("safeZ", p.safeZ()).put("cutDepth", p.cutDepth())
                .put("multiDepth", p.multiDepth()).put("depthPerPass", p.depthPerPass()).put("feedRate", p.feedRate())
                .put("feedRateZ", p.feedRateZ()).put("spindleSpeedRpm", p.spindleSpeedRpm())
                .put("pauseForToolChange", p.pauseForToolChange()).put("rapidFeedRate", p.rapidFeedRate())
                .put("dwell", p.dwell()).put("dwellSeconds", p.dwellSeconds())
                .put("extraCut", p.extraCut()).put("extraCutLength", p.extraCutLength());
        value.put("offset", p.offset().name()).put("customOffset", p.customOffset());
        var positions = p.jobOptions();
        value.put("jobOptions", new JSONObject().put("startZ", positions.startZ()).put("endZ", positions.endZ())
                .put("endX", positions.endX()).put("endY", positions.endY()).put("toolChangeZ", positions.toolChangeZ())
                .put("toolChangeX", positions.toolChangeX()).put("toolChangeY", positions.toolChangeY())
                .put("exclusionsEnabled",positions.exclusionsEnabled()).put("exclusions",new JSONArray(positions.exclusions().stream()
                        .map(area -> new JSONObject().put("wkt",area.wkt()).put("strategy",area.strategy().name()).put("overZ",area.overZ())).toList())));
        if (p.probing() != null) value.put("probing", probeToJson(p.probing()));
        return value;
    }

    private static Map<Integer, DrillGCodeParameters> readDrillDefaults(JSONObject javaExtra) {
        JSONObject json = javaExtra == null ? null : javaExtra.optJSONObject("drillDefaults");
        if (json == null) return Map.of();
        Map<Integer, DrillGCodeParameters> result = new LinkedHashMap<>();
        for (String id : json.keySet()) {
            JSONObject value = json.getJSONObject(id);
            result.put(Integer.parseInt(id), new DrillGCodeParameters(
                    value.getDouble("safeZ"), value.getDouble("drillDepth"),
                    value.getDouble("feedRate"), value.getInt("spindleSpeedRpm"),
                    value.getBoolean("pauseForToolChange"), value.getBoolean("multiDepth"),
                    value.getDouble("depthPerPass"), value.getBoolean("dwell"),
                    value.getDouble("dwellSeconds"), value.getDouble("offsetZ")));
        }
        return Map.copyOf(result);
    }

    private static List<ProjectFile.GeometryEntry> readJavaGeometries(JSONObject root) throws IOException {
        List<ProjectFile.GeometryEntry> result = new ArrayList<>();
        JSONObject javaExtra = root.optJSONObject("_java");
        JSONArray array = javaExtra == null ? null : javaExtra.optJSONArray("geometries");
        if (array == null) {
            return result;
        }
        WKTReader reader = new WKTReader();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.getJSONObject(i);
                List<ToolGeometry> tools = new ArrayList<>();
                JSONArray toolArray = value.optJSONArray("tools");
                if (toolArray != null) {
                    for (int j = 0; j < toolArray.length(); j++) {
                        JSONObject tool = toolArray.getJSONObject(j);
                        tools.add(new ToolGeometry(tool.getDouble("diameter"), reader.read(tool.getString("wkt")),
                                ToolProfile.fromLegacy(tool.optString("toolType", "C1"))));
                    }
                }
                result.add(new ProjectFile.GeometryEntry(value.getString("name"),
                        value.optString("sourceName", ""), value.optString("units", "MM"),
                        reader.read(value.getString("wkt")), value.optBoolean("strokeOnly", false),
                        List.copyOf(tools), value.optString("fillColor", null),
                        value.optString("strokeColor", null), value.optBoolean("visible", true),
                        readGeometryCncDefaults(value.optJSONObject("cncDefaults")),
                        readGeometrySettings(optionalObject(value, "cncSettings"))));
            }
        } catch (ParseException | JSONException | IllegalArgumentException invalid) {
            throw new IOException("Invalid embedded Geometry object", invalid);
        }
        return result;
    }

    private static GeometryGCodeParameters readGeometryCncDefaults(JSONObject json) {
        if (json == null) return null;
        return new GeometryGCodeParameters(json.getDouble("safeZ"), json.getDouble("cutDepth"),
                json.getBoolean("multiDepth"), json.getDouble("depthPerPass"),
                json.getDouble("feedRate"), json.getInt("spindleSpeedRpm"),
                json.getBoolean("pauseForToolChange"), json.optDouble("rapidFeedRate", 0),
                readProbeParameters(json.optJSONObject("probing")), json.optDouble("feedRateZ", json.getDouble("feedRate")),
                json.optBoolean("dwell", false), json.optDouble("dwellSeconds", 0),
                json.optBoolean("extraCut", false), json.optDouble("extraCutLength", 0),
                org.flatcam.cam.gcode.ToolPathOffset.fromLegacy(json.optString("offset", "Path")),
                json.optDouble("customOffset", 0), readGeometryJobOptions(json.optJSONObject("jobOptions")));
    }

    private static org.flatcam.cam.gcode.GeometryJobOptions readGeometryJobOptions(JSONObject json) {
        if (json == null) return org.flatcam.cam.gcode.GeometryJobOptions.AUTOMATIC;
        return new org.flatcam.cam.gcode.GeometryJobOptions(optionalDouble(json, "startZ"), optionalDouble(json, "endZ"),
                optionalDouble(json, "endX"), optionalDouble(json, "endY"), optionalDouble(json, "toolChangeZ"),
                optionalDouble(json, "toolChangeX"), optionalDouble(json, "toolChangeY"),json.optBoolean("exclusionsEnabled",false),readCncExclusions(json.optJSONArray("exclusions")));
    }

    private static List<org.flatcam.cam.gcode.CncExclusionArea> readCncExclusions(JSONArray array) {
        if(array==null)return List.of();
        List<org.flatcam.cam.gcode.CncExclusionArea> areas=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            var area=array.getJSONObject(i);
            areas.add(new org.flatcam.cam.gcode.CncExclusionArea(area.getString("wkt"),
                    org.flatcam.cam.gcode.CncExclusionArea.Strategy.valueOf(area.getString("strategy")),area.getDouble("overZ")));
        }
        return List.copyOf(areas);
    }

    private static ProbeToolChangeParameters readProbeParameters(JSONObject json) {
        if (json == null) return null;
        return new ProbeToolChangeParameters(json.getDouble("toolChangeZ"), json.getDouble("probeDepth"),
                json.getDouble("feedRate"), json.getDouble("contactZ"),
                json.has("toolChangeX") && !json.isNull("toolChangeX") ? json.getDouble("toolChangeX") : null,
                json.has("toolChangeY") && !json.isNull("toolChangeY") ? json.getDouble("toolChangeY") : null);
    }

    private static List<ProjectFile.CncJobRecord> readJavaCncJobs(JSONObject root) {
        List<ProjectFile.CncJobRecord> jobs = new ArrayList<>();
        JSONObject javaExtra = root.optJSONObject("_java");
        JSONArray jobsArray = javaExtra != null ? javaExtra.optJSONArray("cncJobs") : null;
        if (jobsArray != null) {
            for (int i = 0; i < jobsArray.length(); i++) {
                JSONObject jobJson = jobsArray.getJSONObject(i);
                jobs.add(new ProjectFile.CncJobRecord(jobJson.optString("name", null),
                        jobJson.getString("sourceName"), jobJson.getString("outputPath"),
                        jobJson.optString("gcode", null), jobJson.optBoolean("visible", true)));
            }
        }
        return jobs;
    }

    static JSONObject parseRoot(byte[] raw) throws IOException {
        // Detect the container first. Decoding compressed binary as UTF-8/JSON used to
        // allocate a large useless String and throw on every ordinary project open.
        long start = System.nanoTime();
        boolean compressed = raw.length >= XZ.HEADER_MAGIC.length
                && java.util.Arrays.equals(raw, 0, XZ.HEADER_MAGIC.length,
                        XZ.HEADER_MAGIC, 0, XZ.HEADER_MAGIC.length);
        try {
            if (compressed) {
                try (XZInputStream xzIn = new XZInputStream(new ByteArrayInputStream(raw))) {
                    raw = xzIn.readAllBytes();
                }
                logPhase("XZ decompress", start);
                start = System.nanoTime();
            }
            JSONObject root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            logPhase("JSON decode", start);
            return root;
        } catch (JSONException | IOException invalid) {
            throw new IOException("Not a valid project file (neither plain JSON nor XZ-compressed)", invalid);
        }
    }

    private static void logPhase(String phase, long start) {
        if (Boolean.getBoolean("flatcam.plot.profile")) {
            System.getLogger(ProjectFileIO.class.getName()).log(System.Logger.Level.INFO,
                    String.format(java.util.Locale.ROOT, "[PROJECT-PROFILE] %s (%s)=%.1fms",
                            phase, Thread.currentThread().getName(), (System.nanoTime()-start)/1e6));
        }
    }

    /**
     * The pre-embedded-geometry format ({@code {"version":1,"gerbers":[...paths...],
     * "excellons":[...],"cncJobs":[...]}}) only remembered source paths, so
     * restoring it means re-parsing those files - the one place this module
     * still touches a parser directly, purely for this one-time backward
     * compatibility path. A path that no longer exists is skipped with the
     * object simply missing from the restored project, rather than failing
     * the whole load.
     */
    private static ProjectFile loadLegacyV1(JSONObject root) {
        List<ProjectFile.GerberEntry> gerbers = new ArrayList<>();
        for (String pathText : toStringList(root.optJSONArray("gerbers"))) {
            try {
                Path sourcePath = Path.of(pathText);
                var image = new GerberParser().parse(sourcePath);
                gerbers.add(new ProjectFile.GerberEntry(sourcePath.getFileName().toString(), image,
                        null, null, true, true, false, false));
            } catch (Exception e) {
                // Source file moved/deleted since this old-format project was saved - skip it.
            }
        }
        List<ProjectFile.ExcellonEntry> excellons = new ArrayList<>();
        for (String pathText : toStringList(root.optJSONArray("excellons"))) {
            try {
                Path sourcePath = Path.of(pathText);
                var image = new ExcellonParser().parse(sourcePath);
                excellons.add(new ProjectFile.ExcellonEntry(sourcePath.getFileName().toString(), image,
                        null, null, true, true, false));
            } catch (Exception e) {
                // Source file moved/deleted since this old-format project was saved - skip it.
            }
        }

        List<ProjectFile.CncJobRecord> jobs = new ArrayList<>();
        JSONArray jobsArray = root.optJSONArray("cncJobs");
        if (jobsArray != null) {
            for (int i = 0; i < jobsArray.length(); i++) {
                JSONObject jobJson = jobsArray.getJSONObject(i);
                jobs.add(new ProjectFile.CncJobRecord(jobJson.getString("sourceName"), jobJson.getString("outputPath")));
            }
        }
        return new ProjectFile(gerbers, excellons, jobs);
    }

    private static List<String> toStringList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
        }
        return result;
    }
}
