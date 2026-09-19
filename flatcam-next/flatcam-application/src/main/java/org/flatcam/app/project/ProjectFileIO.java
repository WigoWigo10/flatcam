package org.flatcam.app.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads/writes {@link ProjectFile} as plain JSON (not XZ-compressed like the
 * legacy .FlatPrj - there is no large embedded geometry here to make that
 * worthwhile). Extension is ".fcnproj" by convention, kept distinct from the
 * legacy ".FlatPrj" so the two are never confused for one another.
 */
public final class ProjectFileIO {

    private static final int CURRENT_VERSION = 1;

    private ProjectFileIO() {
    }

    public static void save(ProjectFile project, Path path) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", CURRENT_VERSION);
        root.put("gerbers", new JSONArray(project.gerberPaths()));
        root.put("excellons", new JSONArray(project.excellonPaths()));

        JSONArray jobs = new JSONArray();
        for (ProjectFile.CncJobRecord job : project.cncJobs()) {
            JSONObject jobJson = new JSONObject();
            jobJson.put("sourceName", job.sourceName());
            jobJson.put("outputPath", job.outputPath());
            jobs.put(jobJson);
        }
        root.put("cncJobs", jobs);

        Files.writeString(path, root.toString(2));
    }

    public static ProjectFile load(Path path) throws IOException {
        JSONObject root = new JSONObject(Files.readString(path));
        int version = root.optInt("version", -1);
        if (version != CURRENT_VERSION) {
            throw new IOException("Unsupported project file version: " + version);
        }

        List<String> gerbers = toStringList(root.optJSONArray("gerbers"));
        List<String> excellons = toStringList(root.optJSONArray("excellons"));

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
