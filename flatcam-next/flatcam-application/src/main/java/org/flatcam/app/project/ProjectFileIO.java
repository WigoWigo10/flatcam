package org.flatcam.app.project;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Reads/writes {@link ProjectFile} in the same shape as the legacy app's own
 * .FlatPrj format: a JSON tree ({@code {"objs": [...], "options": {...},
 * "version": ...}}), each Gerber/Excellon embedding its own geometry as WKT
 * (see org.flatcam.app.project.flatprj), optionally XZ-compressed - matching
 * {@code app_Main.py}'s save_project/open_project exactly (lzma preset 3,
 * auto-detect plain-vs-compressed on load by trying plain JSON first). CNC
 * Job entries are carried in a {@code "_java"} extension key a real FlatCAM
 * Python install would simply ignore (unknown top-level keys are never
 * consulted by its loader) - see {@link ProjectFile}'s own doc for what's
 * intentionally not part of the Python-compatible {@code objs} list yet.
 *
 * <p>Still uses the {@code .fcnproj} extension by convention (not
 * {@code .FlatPrj}) so a file's extension keeps telling a user which app
 * saved it, even though the two are now largely interchangeable for Gerber/
 * Excellon content.
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
            jobJson.put("sourceName", job.sourceName());
            jobJson.put("outputPath", job.outputPath());
            jobs.put(jobJson);
        }
        JSONObject javaExtra = new JSONObject();
        javaExtra.put("cncJobs", jobs);
        root.put("_java", javaExtra);

        byte[] jsonBytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
        if (compress) {
            try (OutputStream fileOut = Files.newOutputStream(path);
                 XZOutputStream xzOut = new XZOutputStream(fileOut, new LZMA2Options(XZ_PRESET))) {
                xzOut.write(jsonBytes);
            }
        } else {
            Files.write(path, jsonBytes);
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

        return new ProjectFile(gerbers, excellons, readJavaCncJobs(root));
    }

    private static List<ProjectFile.CncJobRecord> readJavaCncJobs(JSONObject root) {
        List<ProjectFile.CncJobRecord> jobs = new ArrayList<>();
        JSONObject javaExtra = root.optJSONObject("_java");
        JSONArray jobsArray = javaExtra != null ? javaExtra.optJSONArray("cncJobs") : null;
        if (jobsArray != null) {
            for (int i = 0; i < jobsArray.length(); i++) {
                JSONObject jobJson = jobsArray.getJSONObject(i);
                jobs.add(new ProjectFile.CncJobRecord(jobJson.getString("sourceName"), jobJson.getString("outputPath")));
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
