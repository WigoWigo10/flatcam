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

class DrillingExclusionPersistenceTest {
    @TempDir Path directory;
    private ProjectFile project(boolean active) {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "%", "T1", "X10.0Y0.0", "M30"));
        var area = CncExclusionArea.of(new GeometryFactory().toGeometry(new Envelope(4, 6, -1, 1)), CncExclusionArea.Strategy.AROUND, 0);
        var options = new GCodeGenerator.DrillJobOptions(false, 15, .5, 0.0, 0.0).withExclusions(active, List.of(area));
        var settings = new DrillCncSettings(GCodePreprocessor.DEFAULT, options, List.of(1), DrillCncSettings.ToolOrder.NO);
        var entry = new ProjectFile.ExcellonEntry("holes", image, null, null, true, true, false,
                Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, false)), settings);
        return new ProjectFile(List.of(), List.of(entry), List.of());
    }
    private String generate(ProjectFile project) {
        var entry = project.excellons().getFirst();
        return GCodeGenerator.generateDrillCncJob(entry.image(), entry.drillDefaults(),
                entry.cncSettings().selectedToolIds(), entry.cncSettings().options(), entry.cncSettings().preprocessor()).gcode();
    }
    private JSONObject settings(JSONObject root) {
        return root.getJSONArray("objs").getJSONObject(0).getJSONObject("_java").getJSONObject("cncSettings");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void jsonAndXzPreserveActiveAndInactiveAreasAndRegenerateTheSameCode(boolean active) throws Exception {
        var original = project(active);
        for (boolean compressed : List.of(false, true)) {
            Path file = directory.resolve("holes-" + compressed + ".fcnproj");
            ProjectFileIO.save(original, file, compressed);
            var loaded = ProjectFileIO.load(file);
            assertEquals(original.excellons().getFirst().cncSettings(), loaded.excellons().getFirst().cncSettings());
            assertEquals(generate(original), generate(loaded));
        }
    }

    @Test void oldProjectsWithoutTheOptionalFieldsStillLoadWithNoAreas() throws Exception {
        var root = ProjectFileIO.toJson(project(false));
        settings(root).remove("exclusionsEnabled");
        settings(root).remove("exclusions");
        Path file = directory.resolve("old.fcnproj");
        Files.writeString(file, root.toString());
        var options = ProjectFileIO.load(file).excellons().getFirst().cncSettings().options();
        assertFalse(options.exclusionsEnabled());
        assertTrue(options.exclusions().isEmpty());
    }

    @Test void corruptSafetySettingsCannotBeSilentlyDroppedOrAppliedToRoland() throws Exception {
        Path file = directory.resolve("invalid.fcnproj");
        for (int mutation = 0; mutation < 5; mutation++) {
            var root = ProjectFileIO.toJson(project(true));
            var options = settings(root);
            switch (mutation) {
                case 0 -> options.put("exclusionsEnabled", "broken");
                case 1 -> options.put("exclusions", "not an array");
                case 2 -> options.getJSONArray("exclusions").getJSONObject(0).put("wkt", "LINESTRING (0 0, 1 1)");
                case 3 -> options.put("preprocessor", GCodePreprocessor.ROLAND_MDX_20.name());
                default -> options.put("exclusions", new org.json.JSONArray());
            }
            Files.writeString(file, root.toString());
            assertThrows(IOException.class, () -> ProjectFileIO.load(file));
        }
    }

    @Test void pythonExportRefusesEvenInactiveDraftAreasBeforeOverwritingTheDestination() throws Exception {
        Path file = directory.resolve("original.FlatPrj");
        Files.writeString(file, "original");
        for (boolean active : List.of(false, true)) {
            assertThrows(IOException.class, () -> PythonProjectWriter.save(project(active), file));
            assertEquals("original", Files.readString(file));
        }
    }
}
