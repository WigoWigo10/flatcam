package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class CncSettingsPersistenceTest {
    @TempDir Path directory;
    private static final ProbeToolChangeParameters PROBE = new ProbeToolChangeParameters(15, -5, 50, 0.5, 7.0, 11.0);

    private static ProjectFile.GeometryEntry geometry(GCodePreprocessor profile, boolean vTool) {
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1, 2), new Coordinate(4, 2)});
        var params = new GeometryGCodeParameters(3, 0.1, false, 1, 120, 128, profile.requiresProbe(),
                profile.usesRapidFeed() ? 600 : 0, profile.requiresProbe() ? PROBE : null);
        var settings = new GeometryCncSettings(profile, vTool ? null : 0.8,
                vTool ? Map.of(0, new VTipSettings(0.123456, 45.6789)) : Map.of());
        return new ProjectFile.GeometryEntry("paths", "source", "MM", path, true,
                vTool ? List.of(new ToolGeometry(0.8, path, ToolProfile.V)) : List.of(),
                null, null, true, params, settings);
    }

    private static String generate(ProjectFile.GeometryEntry entry) {
        var settings = entry.cncSettings();
        List<ToolGeometry> tools = entry.tools().isEmpty()
                ? List.of(new ToolGeometry(settings.singleToolDiameter(), entry.geometry())) : entry.tools();
        return GCodeGenerator.generateGeometryCncJob(entry.units(), tools, entry.cncDefaults(), settings.vTools(),
                CancellationToken.none(), settings.preprocessor()).gcode();
    }

    @ParameterizedTest
    @EnumSource(GCodePreprocessor.class)
    void eachGeometryProfileRegeneratesIdenticalProgramAfterSaving(GCodePreprocessor profile) throws IOException {
        var original = geometry(profile, false);
        Path file = directory.resolve(profile.name() + ".fcnproj");
        // Exercise both JSON and compressed native projects.
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(original), List.of()), file, profile.ordinal() % 2 == 0);
        var loaded = ProjectFileIO.load(file).geometries().getFirst();
        assertEquals(original.cncSettings(), loaded.cncSettings());
        assertEquals(original.cncDefaults(), loaded.cncDefaults());
        assertEquals(generate(original), generate(loaded));
    }

    @ParameterizedTest
    @EnumSource(value = GCodePreprocessor.class, names = {"GRBL_LASER", "MARLIN_LASER_FAN_PIN",
            "MARLIN_LASER_SPINDLE_PIN", "Z_LASER", "HPGL"}, mode = EnumSource.Mode.EXCLUDE)
    void eachDrillingProfilePreservesCommonMovesOrderAndSelection(GCodePreprocessor profile) throws IOException {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%", "T1",
                "X1.0Y1.0", "T2", "X3.0Y3.0", "M30"));
        boolean change = profile.requiresProbe() || profile.supportsManualToolChange();
        var parameters = new DrillGCodeParameters(3, 0.7, 120, 128, change);
        var options = new GCodeGenerator.DrillJobOptions(change, 15, 4, 8.0, 9.0,
                profile.usesRapidFeed() ? 600 : 0, profile.requiresProbe() ? PROBE : null);
        var selection = profile.isRoland() ? List.of(2) : List.of(2, 1);
        var settings = new DrillCncSettings(profile, options, selection, DrillCncSettings.ToolOrder.REVERSE);
        var entry = new ProjectFile.ExcellonEntry("drills", image, null, null, true, true, false,
                Map.of(1, parameters, 2, parameters), settings);
        // Roland does not permit a mechanical tool change.
        if (profile.isRoland()) assertFalse(change);
        Path file = directory.resolve("drill-" + profile.name() + ".fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(entry), List.of()), file);
        var loaded = ProjectFileIO.load(file).excellons().getFirst();
        assertEquals(settings, loaded.cncSettings());
        assertEquals(entry.drillDefaults(), loaded.drillDefaults());
        assertEquals(GCodeGenerator.generateDrillCncJob(image, entry.drillDefaults(), selection, options, profile).gcode(),
                GCodeGenerator.generateDrillCncJob(loaded.image(), loaded.drillDefaults(), loaded.cncSettings().selectedToolIds(),
                        loaded.cncSettings().options(), loaded.cncSettings().preprocessor()).gcode());
    }

    @Test
    void vTipParametersPreserveTheirPrecisionAndCutDepth() throws IOException {
        var original = geometry(GCodePreprocessor.DEFAULT, true);
        Path file = directory.resolve("v-tip.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(original), List.of()), file);
        var loaded = ProjectFileIO.load(file).geometries().getFirst();
        assertEquals(original.cncSettings(), loaded.cncSettings());
        assertEquals(generate(original), generate(loaded));
    }

    @Test
    void olderProjectsWithoutSettingsStillLoadTheirExistingDefaults() throws IOException {
        var geometry = geometry(GCodePreprocessor.DEFAULT, false);
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X1.0Y1.0", "M30"));
        var drilling = new ProjectFile.ExcellonEntry("drill", image, null, null, true, true, false);
        Path file = directory.resolve("old.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(drilling), List.of(geometry), List.of()), file, false);
        var root = new JSONObject(Files.readString(file));
        root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).remove("cncSettings");
        Files.writeString(file, root.toString());
        var loaded = ProjectFileIO.load(file);
        assertNull(loaded.excellons().getFirst().cncSettings());
        assertNull(loaded.geometries().getFirst().cncSettings());
        assertEquals(geometry.cncDefaults(), loaded.geometries().getFirst().cncDefaults());
    }

    @Test
    void unknownProfileCannotSilentlyGenerateADifferentMachineDialect() throws IOException {
        Path file = directory.resolve("unsupported.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(geometry(GCodePreprocessor.DEFAULT, false)), List.of()), file, false);
        var root = new JSONObject(Files.readString(file));
        root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0)
                .getJSONObject("cncSettings").put("preprocessor", "NOT_INSTALLED");
        Files.writeString(file, root.toString());
        assertThrows(IOException.class, () -> ProjectFileIO.load(file));
    }

    @Test
    void invalidDrillProfileAndIncompleteProbeConfigurationAreRejected() {
        var options = new GCodeGenerator.DrillJobOptions(false, 15, 3, null, null);
        assertThrows(IllegalArgumentException.class, () -> new DrillCncSettings(GCodePreprocessor.HPGL, options,
                List.of(1), DrillCncSettings.ToolOrder.NO));
        assertThrows(IllegalArgumentException.class, () -> new DrillCncSettings(GCodePreprocessor.TOOLCHANGE_PROBE_MACH3,
                options, List.of(1), DrillCncSettings.ToolOrder.NO));
        assertThrows(IllegalArgumentException.class, () -> new DrillCncSettings(GCodePreprocessor.DEFAULT, options,
                List.of(1, 1), DrillCncSettings.ToolOrder.NO));
        var original = geometry(GCodePreprocessor.DEFAULT, false);
        assertThrows(IllegalArgumentException.class, () -> new ProjectFile.GeometryEntry("path", "source", "MM",
                original.geometry(), true, List.of(), null, null, true, original.cncDefaults(),
                new GeometryCncSettings(GCodePreprocessor.TOOLCHANGE_PROBE_MACH3, 0.8, Map.of())));
    }

    @Test
    void malformedDrillingSettingsCannotFallBackToPortableProfile() throws IOException {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X1.0Y1.0", "M30"));
        Path file = directory.resolve("bad-drill-config.fcnproj");
        var entry = new ProjectFile.ExcellonEntry("drill", image, null, null, true, true, false);
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(entry), List.of()), file, false);
        var json = new JSONObject(Files.readString(file));
        json.getJSONArray("objs").getJSONObject(0).getJSONObject("_java").put("cncSettings", "not-an-object");
        Files.writeString(file, json.toString());
        IOException error = assertThrows(IOException.class, () -> ProjectFileIO.load(file));
        assertTrue(error.getMessage().contains("nenhum perfil alternativo"));
    }
}
