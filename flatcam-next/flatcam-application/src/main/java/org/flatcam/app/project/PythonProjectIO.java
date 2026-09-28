package org.flatcam.app.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.flatcam.app.project.flatprj.WktJson;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Conservative, read-only import of FlatCAM Python 8.9xx .FlatPrj files. */
public final class PythonProjectIO {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private PythonProjectIO() { }

    public static ProjectFile load(Path path) throws IOException {
        JSONObject root = ProjectFileIO.parseRoot(Files.readAllBytes(path));
        double version = root.optDouble("version", Double.NaN);
        if (!Double.isFinite(version) || version < 8.9 || version >= 9.0)
            throw new IOException("Versao FlatCAM Python nao suportada: " + version);
        JSONArray objects = root.optJSONArray("objs");
        if (objects == null) throw new IOException("Projeto Python sem lista de objetos.");

        List<ProjectFile.GerberEntry> gerbers = new ArrayList<>();
        List<ProjectFile.ExcellonEntry> excellons = new ArrayList<>();
        List<ProjectFile.GeometryEntry> geometries = new ArrayList<>();
        List<ProjectFile.CncJobRecord> jobs = new ArrayList<>();
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
                                decoded.filled(), decoded.multicolor()));
                    }
                    case "geometry" -> geometries.add(readGeometry(object));
                    case "cncjob" -> jobs.add(readCncJob(object));
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
        if (objectsWithUnappliedOptions > 0) {
            warnings.add("Projeto Python: opcoes avancadas de " + objectsWithUnappliedOptions
                    + " objetos nao foram restauradas (nome e visibilidade foram preservados).");
        }
        if (toolsWithUnappliedData > 0) {
            warnings.add("Projeto Python: parametros de usinagem de " + toolsWithUnappliedData
                    + " ferramentas nao foram restaurados. Confira-os antes de gerar G-code.");
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
                if (paths == null) throw new IllegalArgumentException("Geometry sem caminhos da ferramenta " + id);
                tools.add(new ToolGeometry(tool.getDouble("tooldia"), paths,
                        ToolProfile.fromLegacy(tool.optString("tool_type", "C1"))));
            }
        }
        if ((geometry == null || geometry.isEmpty()) && !tools.isEmpty()) {
            geometry = FACTORY.buildGeometry(tools.stream().map(ToolGeometry::geometry).toList());
        }
        if (geometry == null) geometry = FACTORY.createGeometryCollection();
        return new ProjectFile.GeometryEntry(name, "", object.optString("units", "MM"),
                geometry, geometry.getDimension() < 2, List.copyOf(tools),
                object.optString("fill_color", null), object.optString("outline_color", null), plot(object));
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
