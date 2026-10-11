package org.flatcam.app.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.flatcam.app.project.flatprj.WktJson;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Import of FlatCAM Python 8.9xx .FlatPrj files, including FX compatibility exports. */
public final class PythonProjectIO {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private PythonProjectIO() { }

    /**
     * Opens a FlatCAM Python project. The result remembers the file it came from ({@link ProjectFile#pythonLegacy()}),
     * so that {@link PythonProjectWriter} can write back unchanged everything the FX does not model.
     */
    public static ProjectFile load(Path path) throws IOException {
        byte[] original = Files.readAllBytes(path);
        return decode(original).withPythonLegacy(new ProjectFile.PythonLegacy(original));
    }

    static ProjectFile decode(byte[] original) throws IOException {
        JSONObject root = ProjectFileIO.parseRoot(original);
        double version = root.optDouble("version", Double.NaN);
        if (!Double.isFinite(version) || version < 8.9 || version >= 9.0)
            throw new IOException("Versao FlatCAM Python nao suportada: " + version);
        if (root.optInt("_fx_format", 0) == 1 && root.optJSONObject("_java") != null) {
            JSONArray encoded = root.getJSONArray("objs");
            for (int i = 0; i < encoded.length(); i++) {
                JSONObject object = encoded.getJSONObject(i), extra = object.optJSONObject("_java");
                if (extra != null && extra.has("fxOriginalFill"))
                    object.put("fill_color",extra.get("fxOriginalFill")).put("outline_color",extra.get("fxOriginalStroke"));
            }
            root.put("version", 2);
            return ProjectFileIO.fromJson(root);
        }
        JSONArray objects = root.optJSONArray("objs");
        if (objects == null) throw new IOException("Projeto Python sem lista de objetos.");

        List<ProjectFile.GerberEntry> gerbers = new ArrayList<>();
        List<ProjectFile.ExcellonEntry> excellons = new ArrayList<>();
        List<ProjectFile.GeometryEntry> geometries = new ArrayList<>();
        List<ProjectFile.CncJobRecord> jobs = new ArrayList<>();
        List<String> passedThrough = new ArrayList<>();
        int objectsWithUnappliedOptions = 0;
        int toolsWithUnappliedData = 0;
        for (int index = 0; index < objects.length(); index++) {
            try {
                JSONObject object = objects.getJSONObject(index);
                String kind = object.getString("kind");
                switch (kind) {
                    case "gerber" -> {
                        GerberFlatPrjCodec.Decoded decoded = GerberFlatPrjCodec.fromJson(object);
                        gerbers.add(new ProjectFile.GerberEntry(decoded.name(), decoded.image(),
                                decoded.fillColorWeb(), decoded.strokeColorWeb(), plot(object),
                                decoded.filled(), decoded.multicolor(), decoded.followMode()));
                    }
                    case "excellon" -> {
                        ExcellonFlatPrjCodec.Decoded decoded = ExcellonFlatPrjCodec.fromJson(object);
                        excellons.add(new ProjectFile.ExcellonEntry(decoded.name(), decoded.image(),
                                decoded.fillColorWeb(), decoded.strokeColorWeb(), plot(object),
                                decoded.filled(), decoded.multicolor(), readDrillDefaults(object)));
                    }
                    case "geometry" -> geometries.add(readGeometry(object));
                    case "cncjob" -> jobs.add(readCncJob(object));
                    case "script", "document" -> {
                        // Text objects of the Python application: the FX has no view for them, but they stay in
                        // the file when it is saved back (see PythonLegacyMerge).
                        passedThrough.add(name(object, kind));
                        continue;
                    }
                    default -> throw new IllegalArgumentException("Tipo de objeto nao suportado: " + kind);
                }
                JSONObject options = object.optJSONObject("options");
                if (options != null && options.keySet().stream()
                        .anyMatch(key -> !"name".equals(key) && !"plot".equals(key))) {
                    objectsWithUnappliedOptions++;
                }
                JSONObject tools = object.optJSONObject("tools");
                if (tools != null) {
                    for (String toolId : tools.keySet()) {
                        JSONObject tool = tools.optJSONObject(toolId);
                        if (tool != null && tool.optJSONObject("data") != null
                                && !tool.getJSONObject("data").isEmpty()) {
                            toolsWithUnappliedData++;
                        }
                    }
                }
            } catch (RuntimeException invalid) {
                throw new IOException("Objeto " + (index + 1) + " invalido no projeto Python: "
                        + invalid.getMessage(), invalid);
            }
        }
        List<String> warnings = new ArrayList<>();
        JSONObject globalOptions = root.optJSONObject("options");
        if (globalOptions != null && !globalOptions.isEmpty()) {
            warnings.add("Projeto Python: " + globalOptions.length()
                    + " preferencias globais nao foram aplicadas no FX.");
        }
        if (!passedThrough.isEmpty()) {
            warnings.add("Projeto Python: " + passedThrough.size() + " objeto(s) de script/documento nao sao exibidos no FX ("
                    + String.join(", ", passedThrough) + "); sao mantidos ao salvar em .FlatPrj.");
        }
        if (objectsWithUnappliedOptions > 0) {
            warnings.add("Projeto Python: opcoes avancadas de " + objectsWithUnappliedOptions
                    + " objetos nao foram restauradas (nome e visibilidade foram preservados).");
        }
        if (toolsWithUnappliedData > 0) {
            warnings.add("Projeto Python: parte dos parametros de usinagem de " + toolsWithUnappliedData
                    + " ferramentas nao foi restaurada. Confira-os antes de gerar G-code.");
        }
        return new ProjectFile(List.copyOf(gerbers), List.copyOf(excellons),
                List.copyOf(geometries), List.copyOf(jobs), warnings);
    }

    private static ProjectFile.GeometryEntry readGeometry(JSONObject object) {
        String name = name(object, "geometry");
        Geometry geometry = WktJson.unwrap(object.opt("solid_geometry"));
        List<ToolGeometry> tools = new ArrayList<>();
        JSONObject toolJson = object.optJSONObject("tools");
        if (toolJson != null) {
            List<String> ids = new ArrayList<>(toolJson.keySet());
            ids.sort(Comparator.comparingInt(Integer::parseInt));
            for (String id : ids) {
                JSONObject tool = toolJson.getJSONObject(id);
                Geometry paths = WktJson.unwrap(tool.opt("solid_geometry"));
                if (paths == null || paths.isEmpty()) {
                    if (ids.size() != 1) throw new IllegalArgumentException(
                            "Geometry multi-tool sem caminhos da ferramenta " + id);
                    paths = geometry;
                }
                // A blank Geometry ("New Geometry" in the Python application): one tool defined, nothing drawn yet.
                if (paths == null || paths.isEmpty()) continue;
                tools.add(new ToolGeometry(tool.getDouble("tooldia"), paths,
                        ToolProfile.fromLegacy(tool.optString("tool_type", "C1"))));
            }
        }
        if ((geometry == null || geometry.isEmpty()) && !tools.isEmpty()) {
            geometry = FACTORY.buildGeometry(tools.stream().map(ToolGeometry::geometry).toList());
        }
        if (geometry == null) geometry = FACTORY.createGeometryCollection();
        return new ProjectFile.GeometryEntry(name, "", org.flatcam.app.project.flatprj.LegacyUnits.normalize(object.optString("units", "MM")),
                geometry, geometry.getDimension() < 2, List.copyOf(tools),
                object.optString("fill_color", null), object.optString("outline_color", null), plot(object),
                readGeometryCncDefaults(toolJson, object.optJSONObject("options")), readGeometrySettings(toolJson, object.optJSONObject("options")));
    }

    private static GeometryCncSettings readGeometrySettings(JSONObject tools, JSONObject options) {
        if (tools == null || tools.isEmpty()) return null;
        List<String> ids = new ArrayList<>(tools.keySet()); ids.sort(Comparator.comparingInt(Integer::parseInt));
        Map<Integer, GeometryGCodeParameters> parameters = new LinkedHashMap<>();
        Map<Integer, org.flatcam.cam.gcode.VTipSettings> tips = new LinkedHashMap<>();
        org.flatcam.cam.gcode.GCodePreprocessor profile = org.flatcam.cam.gcode.GCodePreprocessor.FX_PORTABLE;
        for (int i = 0; i < ids.size(); i++) {
            JSONObject tool = tools.getJSONObject(ids.get(i)), data = tool.optJSONObject("data");
            if (data == null) continue;
            GeometryGCodeParameters p = readMachiningTool(tool, options);
            if (p != null) parameters.put(i,p);
            String name = data.optString("ppname_g", "");
            if (i == 0) for (var candidate : org.flatcam.cam.gcode.GCodePreprocessor.geometryProfiles())
                if (PythonProjectWriter.pythonProfile(candidate).equalsIgnoreCase(name) && !candidate.requiresProbe()) profile = candidate;
            if (ToolProfile.fromLegacy(tool.optString("tool_type","C1")) == ToolProfile.V) try {
                var tip = new org.flatcam.cam.gcode.VTipSettings(data.getDouble("vtipdia"),data.getDouble("vtipangle"));
                tip.cutDepth(tool.getDouble("tooldia")); tips.put(i,tip);
            } catch (RuntimeException invalid) { /* Optional tip does not discard paths. */ }
        }
        if (readGeometryCncDefaults(tools, options) == null) return null;
        return new GeometryCncSettings(profile,null,tips,parameters);
    }

    private static GeometryGCodeParameters readGeometryCncDefaults(JSONObject tools, JSONObject options) {
        if (tools == null || tools.isEmpty()) return null;
        List<String> ids = new ArrayList<>(tools.keySet());
        ids.sort(Comparator.comparingInt(Integer::parseInt));
        JSONObject first = tools.getJSONObject(ids.get(0));
        JSONObject data = first.optJSONObject("data");
        if (data == null) return null;
        return readMachiningTool(first, options);
    }

    private static GeometryGCodeParameters readMachiningTool(JSONObject tool, JSONObject options) {
        JSONObject original = tool.optJSONObject("data");
        if (original == null) return null;
        JSONObject data = new JSONObject(original.toString());
        if (options != null) {
            for (String key : List.of("startz", "endz", "endxy", "toolchangez", "toolchangexy"))
                if ((!data.has(key) || key.equals("endxy")) && options.has(key)) data.put(key, options.get(key));
        }
        GeometryGCodeParameters p = readMachiningData(data);
        if (p == null) return null;
        try {
            return p.withCompensation(org.flatcam.cam.gcode.ToolPathOffset.fromLegacy(tool.optString("offset", "Path")),
                    tool.optDouble("offset_value", 0));
        } catch (RuntimeException invalid) { return null; }
    }

    private static GeometryGCodeParameters readMachiningData(JSONObject data) {
        try {
            double cutZ = data.getDouble("cutz");
            if (!Double.isFinite(cutZ) || cutZ >= 0) return null;
            return new GeometryGCodeParameters(data.getDouble("travelz"),
                    -cutZ, data.optBoolean("multidepth", false),
                    data.optDouble("depthperpass", 0), data.getDouble("feedrate"),
                    data.optInt("spindlespeed", 0), data.optBoolean("toolchange", false),
                    data.optDouble("feedrate_rapid", 0), null, data.optDouble("feedrate_z",data.getDouble("feedrate")),
                    data.optBoolean("dwell",false), data.optDouble("dwelltime",0),
                    data.optBoolean("extracut",false), data.optDouble("extracut_length",0))
                    .withJobOptions(readLegacyPositions(data));
        } catch (RuntimeException invalid) {
            // Optional CAM settings must not make otherwise valid project geometry disappear.
            return null;
        }
    }

    private static org.flatcam.cam.gcode.GeometryJobOptions readLegacyPositions(JSONObject data) {
        Double[] end = legacyXY(data.opt("endxy")), change = legacyXY(data.opt("toolchangexy"));
        return new org.flatcam.cam.gcode.GeometryJobOptions(legacyOptional(data.opt("startz")), legacyOptional(data.opt("endz")),
                end[0], end[1], legacyOptional(data.opt("toolchangez")), change[0], change[1]);
    }
    private static Double legacyOptional(Object value) {
        if (value == null || value == JSONObject.NULL || value.toString().isBlank() || value.toString().equalsIgnoreCase("None")) return null;
        return Double.valueOf(value.toString().trim());
    }
    private static Double[] legacyXY(Object value) {
        if (value == null || value == JSONObject.NULL || value.toString().isBlank() || value.toString().equalsIgnoreCase("None"))
            return new Double[]{null, null};
        if (value instanceof JSONArray a) {
            if (a.length() != 2) throw new IllegalArgumentException("Posicao XY invalida.");
            return new Double[]{a.getDouble(0), a.getDouble(1)};
        }
        String[] xy = value.toString().replace("(", "").replace(")", "").replace("[", "").replace("]", "").split(",", -1);
        if (xy.length != 2) throw new IllegalArgumentException("Posicao XY invalida.");
        return new Double[]{Double.valueOf(xy[0].trim()), Double.valueOf(xy[1].trim())};
    }

    private static Map<Integer, DrillGCodeParameters> readDrillDefaults(JSONObject object) {
        JSONObject tools = object.optJSONObject("tools");
        if (tools == null) return Map.of();
        Map<Integer, DrillGCodeParameters> defaults = new LinkedHashMap<>();
        for (String id : tools.keySet()) {
            JSONObject tool = tools.optJSONObject(id);
            JSONObject data = tool == null ? null : tool.optJSONObject("data");
            if (data == null) continue;
            try {
                double cutZ = data.getDouble("tools_drill_cutz");
                if (!Double.isFinite(cutZ) || cutZ >= 0) continue;
                defaults.put(Integer.parseInt(id), new DrillGCodeParameters(
                        data.getDouble("tools_drill_travelz"),
                        -cutZ,
                        data.getDouble("tools_drill_feedrate_z"),
                        data.optInt("tools_drill_spindlespeed", 0),
                        data.optBoolean("tools_drill_toolchange", false),
                        data.optBoolean("tools_drill_multidepth", false),
                        data.optDouble("tools_drill_depthperpass", 0),
                        data.optBoolean("tools_drill_dwell", false),
                        data.optDouble("tools_drill_dwelltime", 0),
                        data.optDouble("tools_drill_offset", 0)));
            } catch (RuntimeException invalid) {
                // Invalid optional CAM defaults must not discard the drills themselves.
            }
        }
        return Map.copyOf(defaults);
    }

    private static ProjectFile.CncJobRecord readCncJob(JSONObject object) {
        String name = name(object, "cncjob");
        String gcode = object.optString("gcode", null);
        if (gcode == null || gcode.isBlank())
            throw new IllegalArgumentException("CNC Job sem G-code embutido: " + name);
        String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return new ProjectFile.CncJobRecord(name, "", safeName + ".nc", gcode, plot(object));
    }

    private static String name(JSONObject object, String fallback) {
        JSONObject options = object.optJSONObject("options");
        return options == null ? fallback : options.optString("name", fallback);
    }

    private static boolean plot(JSONObject object) {
        JSONObject options = object.optJSONObject("options");
        return options == null || options.optBoolean("plot", true);
    }
}
