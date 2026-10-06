package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class DrillingPositionsPersistenceTest {
    @TempDir Path directory;
    private ProjectFile project(boolean exclusions) {
        return project("MM", exclusions);
    }
    private ProjectFile project(String units, boolean exclusions) {
        double scale = units.equals("MM") ? 1 : 1 / 25.4;
        var image = new ExcellonParser().parse(List.of("M48", units.equals("MM") ? "METRIC" : "INCH",
                "T1C" + scale, "%", "T1", "X" + 10 * scale + "Y0.0", "M30"));
        var options = new GCodeGenerator.DrillJobOptions(true, 15 * scale, .5 * scale, 0.0, 0.0)
                .withPositions(20 * scale, 0.0, 5 * scale);
        if (exclusions) options = options.withExclusions(true, List.of(CncExclusionArea.of(
                new GeometryFactory().toGeometry(new Envelope(4 * scale, 6 * scale, -scale, scale)), CncExclusionArea.Strategy.AROUND, 0)));
        var settings = new DrillCncSettings(GCodePreprocessor.DEFAULT, options, List.of(1), DrillCncSettings.ToolOrder.NO);
        var entry = new ProjectFile.ExcellonEntry("holes", image, null, null, true, true, false,
                Map.of(1, new DrillGCodeParameters(3 * scale, scale, 100 * scale, 0, true)), settings);
        return new ProjectFile(List.of(), List.of(entry), List.of());
    }
    private String generate(ProjectFile project) {
        var entry = project.excellons().getFirst();
        return GCodeGenerator.generateDrillCncJob(entry.image(), entry.drillDefaults(), List.of(1),
                entry.cncSettings().options(), entry.cncSettings().preprocessor()).gcode();
    }
    private JSONObject settings(JSONObject root) {
        return root.getJSONArray("objs").getJSONObject(0).getJSONObject("_java").getJSONObject("cncSettings");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeJsonAndXzRegenerateSameCodeIncludingAreas(boolean exclusions) throws Exception {
        for (String units : List.of("MM", "IN")) for (boolean compressed : List.of(false, true)) {
            var original = project(units, exclusions);
            Path file = directory.resolve("holes-" + units + compressed + ".fcnproj");
            ProjectFileIO.save(original, file, compressed);
            var loaded = ProjectFileIO.load(file);
            assertEquals(original.excellons().getFirst().cncSettings(), loaded.excellons().getFirst().cncSettings());
            assertEquals(generate(original), generate(loaded));
        }
    }

    @Test void olderNativeSettingsRestoreNoOptionalPositions() throws Exception {
        var root = ProjectFileIO.toJson(project(false));
        for (String key : List.of("startZ", "toolChangeX", "toolChangeY")) settings(root).remove(key);
        Path file = directory.resolve("old.fcnproj");
        Files.writeString(file, root.toString());
        var options = ProjectFileIO.load(file).excellons().getFirst().cncSettings().options();
        assertNull(options.startZ()); assertNull(options.toolChangeX()); assertNull(options.toolChangeY());
    }

    @Test void invalidPositionsAndUnsupportedProfilesRefuseLoadingInsteadOfChangingProgram() throws Exception {
        Path file = directory.resolve("invalid.fcnproj");
        for (int mutation = 0; mutation < 6; mutation++) {
            var root = ProjectFileIO.toJson(project(false));
            var settings = settings(root);
            switch (mutation) {
                case 0 -> settings.put("startZ", -1);
                case 1 -> settings.put("startZ", "broken");
                case 2 -> settings.remove("toolChangeY");
                case 3 -> settings.put("preprocessor", GCodePreprocessor.ROLAND_MDX_20.name());
                case 4 -> settings.put("preprocessor", GCodePreprocessor.TOOLCHANGE_PROBE_MACH3.name());
                default -> settings.put("pauseForToolChange", false);
            }
            Files.writeString(file, root.toString());
            assertThrows(IOException.class, () -> ProjectFileIO.load(file));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pythonExportContainsRealLegacyKeysAndPreservesFxSnapshot(boolean compressed) throws Exception {
        var original = project(false);
        Path file = directory.resolve("holes.FlatPrj");
        PythonProjectWriter.save(original, file, compressed);
        var root = ProjectFileIO.parseRoot(Files.readAllBytes(file));
        var object = root.getJSONArray("objs").getJSONObject(0);
        var data = object.getJSONObject("tools").getJSONObject("1").getJSONObject("data");
        assertEquals(20, data.getDouble("tools_drill_startz"));
        assertEquals("0.0, 5.0", data.getString("tools_drill_toolchangexy"));
        assertEquals(15, data.getDouble("tools_drill_toolchangez"));
        assertTrue(data.getBoolean("tools_drill_toolchange"));
        assertEquals("default", data.getString("tools_drill_ppname_e"));
        assertEquals(data.get("tools_drill_startz"), object.getJSONObject("options").get("tools_drill_startz"));
        var loaded = PythonProjectIO.load(file);
        assertEquals(original.excellons().getFirst().cncSettings(), loaded.excellons().getFirst().cncSettings());
        assertEquals(generate(original), generate(loaded));
    }

    @Test void positionsDoNotBypassPythonExclusionExportRestriction() throws Exception {
        Path file = directory.resolve("untouched.FlatPrj");
        Files.writeString(file, "original");
        assertThrows(IOException.class, () -> PythonProjectWriter.save(project(true), file));
        assertEquals("original", Files.readString(file));
    }
}
