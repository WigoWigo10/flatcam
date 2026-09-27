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
import java.util.List;
import org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/**
 * Reads/writes the native {@code .fcnproj} format. Its outer JSON tree and
 * Gerber/Excellon WKT encoding follow the Python {@code .FlatPrj} conventions,
 * and it supports plain JSON or XZ compression. This is not yet a full
 * {@code .FlatPrj} reader/writer: the native version is 2, whereas this
 * Python writes 8.994, and only native project versions 1/2 are accepted.
 * CNC Job and Geometry entries are carried in a {@code "_java"} extension key a real FlatCAM
 * Python install would simply ignore (unknown top-level keys are never
 * consulted by its loader) - see {@link ProjectFile}'s own doc for what's
 * intentionally not part of Python's {@code objs} list yet.
 *
 * <p>Still uses the {@code .fcnproj} extension by convention (not
 * {@code .FlatPrj}) so a file's extension keeps telling a user which app
 * saved it. A separate compatibility codec and real Python fixtures are
 * needed before advertising import/export of {@code .FlatPrj} files.
 */
public final class ProjectFileIO {

    private static final int CURRENT_VERSION = 2;
    private static final int LEGACY_V1_VERSION = 1;
    private static final int XZ_PRESET = 3;

    private ProjectFileIO() {
    }

    public static void save(ProjectFile project, Path path) throws IOException {
        save(project, path, true);
    }

    public static void save(ProjectFile project, Path path, boolean compress) throws IOException {
        JSONArray objs = new JSONArray();
        for (ProjectFile.GerberEntry gerber : project.gerbers()) {
            objs.put(GerberFlatPrjCodec.toJson(gerber.name(), gerber.image(), gerber.fillColorWeb(),
                    gerber.strokeColorWeb(), gerber.visible(), gerber.filled(), gerber.multicolor(), gerber.followMode()));
        }
        for (ProjectFile.ExcellonEntry excellon : project.excellons()) {
            objs.put(ExcellonFlatPrjCodec.toJson(excellon.name(), excellon.image(), excellon.fillColorWeb(),
                    excellon.strokeColorWeb(), excellon.visible(), excellon.filled(), excellon.multicolor()));
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
            if (job.gcode() != null) {
                jobJson.put("gcode", job.gcode());
            }
            jobs.put(jobJson);
        }
        JSONObject javaExtra = new JSONObject();
        javaExtra.put("cncJobs", jobs);
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

        byte[] jsonBytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
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
    }

    public static ProjectFile load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        JSONObject root = parseRoot(raw);

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
                                decoded.filled(), decoded.multicolor()));
                    }
                    default -> {
                        // Not-yet-embedded kinds (geometry/cncjob-as-a-Python-obj) - see ProjectFile's doc.
                    }
                }
            }
        }

        return new ProjectFile(gerbers, excellons, readJavaGeometries(root), readJavaCncJobs(root));
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
                        value.optString("strokeColor", null), value.optBoolean("visible", true)));
            }
        } catch (ParseException | JSONException invalid) {
            throw new IOException("Invalid embedded Geometry object", invalid);
        }
        return result;
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
                        jobJson.optString("gcode", null)));
            }
        }
        return jobs;
    }

    private static JSONObject parseRoot(byte[] raw) throws IOException {
        try {
            return new JSONObject(new String(raw, StandardCharsets.UTF_8));
        } catch (JSONException plainFailed) {
            try (XZInputStream xzIn = new XZInputStream(new ByteArrayInputStream(raw))) {
                return new JSONObject(new String(xzIn.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException xzFailed) {
                throw new IOException("Not a valid project file (neither plain JSON nor XZ-compressed)", xzFailed);
            }
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
