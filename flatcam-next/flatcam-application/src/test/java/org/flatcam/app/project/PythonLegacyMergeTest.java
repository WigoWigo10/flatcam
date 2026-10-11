package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.gerber.GerberParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonLegacyMergeTest {

    private static JSONObject object(String kind, String name) {
        return new JSONObject().put("kind", kind).put("options", new JSONObject().put("name", name).put("plot", true))
                .put("solid_geometry", new JSONArray().put("POINT (1 1)"));
    }

    private static JSONObject root(JSONObject... objects) {
        return new JSONObject().put("version", 8.994).put("options", new JSONObject().put("units", "MM"))
                .put("objs", new JSONArray(List.of(objects)));
    }

    private static JSONObject copy(JSONObject value) {
        return new JSONObject(value.toString());
    }

    private static List<String> names(JSONObject root) {
        JSONArray objects = root.getJSONArray("objs");
        return java.util.stream.IntStream.range(0, objects.length())
                .mapToObj(i -> objects.getJSONObject(i).getJSONObject("options").getString("name")).toList();
    }

    @Test
    void whatTheFxDidNotChangeKeepsTheFilesOwnValues() {
        // The file spells the geometry its own way and has much the FX does not write.
        JSONObject originalObject = object("gerber", "top").put("solid_geometry", new JSONArray().put("POINT (1.0 1.0)"))
                .put("aperture_macros", new JSONObject().put("M1", "raw")).put("frac_digits", 6);
        originalObject.getJSONObject("options").put("isotooldia", 0.2).put("plot", 1);
        JSONObject original = root(originalObject).put("python_extra", "kept");
        original.getJSONObject("options").put("global_theme", "dark");
        JSONObject fx = root(object("gerber", "top").put("_java", new JSONObject().put("fx", true)))
                .put("_fx_format", 1).put("_java", new JSONObject());

        JSONObject merged = PythonLegacyMerge.merge(original, copy(fx), copy(fx));
        JSONObject top = merged.getJSONArray("objs").getJSONObject(0);
        assertEquals("POINT (1.0 1.0)", top.getJSONArray("solid_geometry").getString(0));
        assertEquals(6, top.getInt("frac_digits"));
        assertEquals("raw", top.getJSONObject("aperture_macros").getString("M1"));
        assertEquals(0.2, top.getJSONObject("options").getDouble("isotooldia"));
        assertEquals(1, top.getJSONObject("options").get("plot"), "the file's own spelling of an unchanged value");
        assertTrue(top.getJSONObject("_java").getBoolean("fx"), "the FX snapshot always travels with the object");
        assertEquals("kept", merged.getString("python_extra"));
        assertEquals("dark", merged.getJSONObject("options").getString("global_theme"));
        assertEquals(1, merged.getInt("_fx_format"));
        assertFalse(originalObject.has("_java"), "the opened file's objects are not modified");
    }

    @Test
    void onlyTheChangedValuesAreReplacedAndStaleDerivedTextIsDropped() {
        JSONObject originalObject = object("excellon", "drills").put("source_file", "M48").put("zeros", "T");
        originalObject.getJSONObject("options").put("python_option", 5);
        originalObject.put("tools", new JSONObject().put("1", new JSONObject().put("tooldia", 0.8)
                .put("data", new JSONObject().put("tools_drill_cutz", -1.7).put("python_only", "x"))));
        JSONObject baselineObject = object("excellon", "drills").put("tools", new JSONObject().put("1",
                new JSONObject().put("tooldia", 0.8).put("data", new JSONObject().put("tools_drill_cutz", -1.7))));
        JSONObject currentObject = copy(baselineObject);
        currentObject.getJSONObject("options").put("plot", false);
        currentObject.getJSONObject("tools").getJSONObject("1").getJSONObject("data").put("tools_drill_cutz", -2.0);

        JSONObject merged = PythonLegacyMerge.merge(root(originalObject), root(baselineObject), root(currentObject));
        JSONObject drills = merged.getJSONArray("objs").getJSONObject(0);
        assertFalse(drills.getJSONObject("options").getBoolean("plot"));
        assertEquals(5, drills.getJSONObject("options").getInt("python_option"));
        JSONObject data = drills.getJSONObject("tools").getJSONObject("1").getJSONObject("data");
        assertEquals(-2.0, data.getDouble("tools_drill_cutz"));
        assertEquals("x", data.getString("python_only"), "inside a tool's data, only what changed is replaced");
        assertEquals("T", drills.getString("zeros"));
        assertFalse(drills.has("source_file"), "the tools changed: the source text is stale");
    }

    @Test
    void aValueTheFxStoppedWritingIsRemoved() {
        JSONObject originalObject = object("geometry", "route");
        originalObject.getJSONObject("options").put("xmin", 0);
        JSONObject baselineObject = copy(originalObject);
        JSONObject currentObject = copy(originalObject);
        currentObject.getJSONObject("options").remove("xmin");
        currentObject.getJSONObject("options").put("plot", false);
        JSONObject merged = PythonLegacyMerge.merge(root(originalObject), root(baselineObject), root(currentObject));
        assertFalse(merged.getJSONArray("objs").getJSONObject(0).getJSONObject("options").has("xmin"));
    }

    @Test
    void objectsKeepTheirOrderRenamesAreFollowedAndPythonOnlyKindsStay() {
        JSONObject script = new JSONObject().put("kind", "script").put("options", new JSONObject().put("name", "notes"))
                .put("source_file", "puts hello");
        JSONObject cnc = object("cncjob", "job").put("z_cut", -1.5);
        JSONObject gerber = object("gerber", "top").put("frac_digits", 6);
        JSONObject geometry = object("geometry", "old-name").put("python_field", true)
                .put("solid_geometry", new JSONArray().put("POINT (9 9)"));
        // The file lists them in creation order; the FX writes gerbers, excellons, geometries, jobs.
        JSONObject original = root(cnc, script, geometry, gerber);
        JSONObject baseline = root(copy(gerber), copy(geometry), copy(cnc));
        JSONObject renamed = copy(geometry);
        renamed.getJSONObject("options").put("name", "new-name");
        JSONObject added = object("geometry", "added");
        JSONObject current = root(copy(gerber), renamed, added);           // the job was deleted

        JSONObject merged = PythonLegacyMerge.merge(original, baseline, current);
        assertEquals(List.of("notes", "new-name", "top", "added"), names(merged));
        JSONObject moved = merged.getJSONArray("objs").getJSONObject(1);
        assertTrue(moved.getBoolean("python_field"), "a renamed object keeps what the file had");
        assertEquals("puts hello", merged.getJSONArray("objs").getJSONObject(0).getString("source_file"));
    }

    @Test
    void numbersWrittenDifferentlyAreNotChanges() {
        JSONObject originalObject = object("geometry", "g");
        originalObject.getJSONObject("options").put("tooldia", "0.80");     // the file's own spelling
        JSONObject baselineObject = object("geometry", "g");
        baselineObject.getJSONObject("options").put("tooldia", 1);
        JSONObject currentObject = object("geometry", "g");
        currentObject.getJSONObject("options").put("tooldia", 1.0);
        JSONObject merged = PythonLegacyMerge.merge(root(originalObject), root(baselineObject), root(currentObject));
        assertEquals("0.80", merged.getJSONArray("objs").getJSONObject(0).getJSONObject("options").get("tooldia"));
    }

    @Test
    void aProjectWithScriptAndDocumentObjectsOpensAndSavesThemBack(@TempDir Path directory) throws Exception {
        Path gerberFile = directory.resolve("board.gbr");
        Files.writeString(gerberFile, "%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,1.0*%\nD10*\nX0Y0D03*\nM02*\n");
        ProjectFile seed = new ProjectFile(List.of(new ProjectFile.GerberEntry("board", new GerberParser().parse(gerberFile),
                "#44cc22", "#123456", true, true, false, false)), List.of(), List.of());
        Path seedFile = directory.resolve("seed.FlatPrj");
        PythonProjectWriter.save(seed, seedFile, false);
        JSONObject root = new JSONObject(Files.readString(seedFile));
        root.remove("_fx_format");
        root.remove("_java");
        root.getJSONArray("objs").getJSONObject(0).remove("_java");
        JSONArray objects = new JSONArray();
        objects.put(new JSONObject().put("kind", "script").put("options", new JSONObject().put("name", "macro"))
                .put("source_file", "new\nopen_gerber x"));
        objects.put(root.getJSONArray("objs").getJSONObject(0));
        objects.put(new JSONObject().put("kind", "document").put("options", new JSONObject().put("name", "readme"))
                .put("source_file", "<b>notes</b>"));
        root.put("objs", objects);
        Path python = directory.resolve("python.FlatPrj");
        Files.writeString(python, root.toString(), StandardCharsets.UTF_8);

        ProjectFile loaded = PythonProjectIO.load(python);
        assertEquals(1, loaded.gerbers().size());
        assertNotNull(loaded.pythonLegacy());
        assertTrue(loaded.importWarnings().stream().anyMatch(w -> w.contains("macro") && w.contains("readme")));

        Path saved = directory.resolve("saved.FlatPrj");
        PythonProjectWriter.save(loaded, saved, false);
        JSONObject result = new JSONObject(Files.readString(saved));
        assertEquals(List.of("macro", "board", "readme"), names(result));
        assertEquals("new\nopen_gerber x", result.getJSONArray("objs").getJSONObject(0).getString("source_file"));
        // Working in the FX's own format in between does not lose the way back.
        Path nativeFile = directory.resolve("work.fcnproj");
        ProjectFileIO.save(loaded, nativeFile);
        ProjectFile fromNative = ProjectFileIO.load(nativeFile);
        assertEquals(loaded.pythonLegacy(), fromNative.pythonLegacy());
        Path viaNative = directory.resolve("via-native.FlatPrj");
        PythonProjectWriter.save(fromNative, viaNative, false);
        assertEquals(List.of("macro", "board", "readme"), names(new JSONObject(Files.readString(viaNative))));
        assertTrue(new JSONObject(Files.readString(viaNative)).getJSONArray("objs").getJSONObject(1)
                .similar(result.getJSONArray("objs").getJSONObject(1)));

        // And the FX reopens its own save, scripts included, for another round.
        ProjectFile again = PythonProjectIO.load(saved);
        assertEquals(1, again.gerbers().size());
        Path second = directory.resolve("second.FlatPrj");
        PythonProjectWriter.save(again, second, true);
        assertEquals(List.of("macro", "board", "readme"),
                names(ProjectFileIO.parseRoot(Files.readAllBytes(second))));
    }
}
