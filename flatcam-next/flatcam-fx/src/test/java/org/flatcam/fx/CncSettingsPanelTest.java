package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.app.project.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class CncSettingsPanelTest {
    @TempDir Path directory;
    private static final ProbeToolChangeParameters PROBE = new ProbeToolChangeParameters(15, -5, 50, 0.5, 7.0, 11.0);

    private static void onFx(Runnable action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { }
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> ComboBox<T> combo(Node root, String id) { return (ComboBox<T>) root.lookup(id); }
    private static TextField field(Node root, String id) { return (TextField) root.lookup(id); }
    private static CheckBox check(Node root, String id) { return (CheckBox) root.lookup(id); }

    @ParameterizedTest
    @EnumSource(GCodePreprocessor.class)
    void restoredGeometryPanelUsesTheSavedProfileAndReproducesTheProgram(GCodePreprocessor profile) throws Exception {
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1, 2), new Coordinate(4, 2)});
        var parameters = new GeometryGCodeParameters(3, 0.123456, false, 1, 120.123456, 128, profile.requiresProbe(),
                profile.usesRapidFeed() ? 600 : 0, profile.requiresProbe() ? PROBE : null);
        var settings = new GeometryCncSettings(profile, 0.8123456, Map.of());
        var entry = new ProjectFile.GeometryEntry("path", "source", "MM", path, true, List.of(), null, null, true, parameters, settings);
        Path file = directory.resolve("geometry-" + profile + ".fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(entry), List.of()), file);
        var loaded = ProjectFileIO.load(file).geometries().getFirst();
        onFx(() -> {
            AtomicReference<GeometryCncToolPanel.Result> submitted = new AtomicReference<>();
            Node root = GeometryCncToolPanel.build(loaded.units(), loaded.geometry(), loaded.tools(), loaded.cncDefaults(),
                    loaded.cncSettings(), submitted::set, () -> {});
            assertEquals(profile, combo(root, "#cnc-preprocessor").getValue());
            assertEquals(settings.singleToolDiameter(), Double.parseDouble(field(root, "#cnc-tool-dia").getText()));
            if (profile.requiresProbe()) {
                assertFalse(check(root, "#probe-confirm").isSelected());
                ((Button) root.lookup("#cnc-generate")).fire();
                assertNull(submitted.get());
                assertTrue(((Label) root.lookup("#cnc-error")).getText().contains("Confirme"));
                check(root, "#probe-confirm").setSelected(true);
            }
            ((Button) root.lookup("#cnc-generate")).fire();
            assertNotNull(submitted.get(), ((Label) root.lookup("#cnc-error")).getText());
            var result = submitted.get();
            assertEquals(GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(settings.singleToolDiameter(), path)),
                    parameters, Map.of(), CancellationToken.none(), profile).gcode(),
                    GCodeGenerator.generateGeometryCncJob(loaded.units(), result.tools(), result.parameters(), result.vTools(),
                            CancellationToken.none(), result.preprocessor()).gcode());
        });
    }

    @Test
    void geometryRestoresVTipInputsWithoutTruncatingTheirPrecision() throws Exception {
        onFx(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1, 2), new Coordinate(4, 2)});
            var tips = Map.of(0, new VTipSettings(0.123456, 45.6789));
            var settings = new GeometryCncSettings(GCodePreprocessor.DEFAULT, null, tips);
            var parameters = new GeometryGCodeParameters(3, 0.1, false, 1, 120, 9000, false);
            AtomicReference<GeometryCncToolPanel.Result> submitted = new AtomicReference<>();
            Node root = GeometryCncToolPanel.build("MM", path, List.of(new ToolGeometry(0.8, path, ToolProfile.V)),
                    parameters, settings, submitted::set, () -> {});
            ((Button) root.lookup("#cnc-generate")).fire();
            assertNotNull(submitted.get(), ((Label) root.lookup("#cnc-error")).getText());
            assertEquals(tips, submitted.get().vTools());
        });
    }

    private static DrillGCodeToolPanel.SourceCandidate source(String name, DrillCncSettings settings) {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%", "T1",
                "X1.0Y1.0", "T2", "X3.0Y3.0", "M30"));
        var parameters = new DrillGCodeParameters(3, 0.7, 120, 9000, settings != null && settings.options().pauseForToolChange());
        return new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>(name), image, Map.of(1, parameters, 2, parameters), settings);
    }

    @Test
    void drillingReopensCommonParametersAndReproducesSubsetAndOrder() throws Exception {
        var options = new GCodeGenerator.DrillJobOptions(true, 12.123456, 4.123456, 8.123456, 9.123456, 600);
        var settings = new DrillCncSettings(GCodePreprocessor.MARLIN, options, List.of(2, 1), DrillCncSettings.ToolOrder.REVERSE);
        var original = source("drill", settings);
        Path file = directory.resolve("drill.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(new ProjectFile.ExcellonEntry("drill", original.image(),
                null, null, true, true, false, original.drillDefaults(), settings)), List.of()), file);
        var loaded = ProjectFileIO.load(file).excellons().getFirst();
        onFx(() -> {
            var restored = new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>(loaded.name()), loaded.image(),
                    loaded.drillDefaults(), loaded.cncSettings());
            AtomicReference<DrillGCodeToolPanel.Result> submitted = new AtomicReference<>();
            Node root = DrillGCodeToolPanel.build(List.of(restored), restored, List::of, submitted::set, () -> {});
            assertEquals(settings.preprocessor(), combo(root, "#drill-preprocessor").getValue());
            assertTrue(((RadioButton) root.lookup("#drill-order-reverse")).isSelected());
            ((Button) root.lookup("#drill-generate")).fire();
            assertNotNull(submitted.get(), ((Label) root.lookup("#drill-error")).getText());
            assertEquals(settings, submitted.get().cncSettings());
            assertEquals(GCodeGenerator.generateDrillCncJob(original.image(), original.drillDefaults(), settings.selectedToolIds(),
                    options, settings.preprocessor()).gcode(), GCodeGenerator.generateDrillCncJob(restored.image(),
                    submitted.get().settingsByTool(), submitted.get().orderedToolIds(), submitted.get().options(), submitted.get().preprocessor()).gcode());
        });
    }

    @Test
    void switchingDrillSourcesDoesNotLeakGlobalsOrProbeConfirmationAndResetWorks() throws Exception {
        onFx(() -> {
            var saved = new DrillCncSettings(GCodePreprocessor.TOOLCHANGE_PROBE_MACH3,
                    new GCodeGenerator.DrillJobOptions(true, 15, 4, 8.0, 9.0, 0, PROBE),
                    List.of(2), DrillCncSettings.ToolOrder.NO);
            var probed = source("probe", saved);
            var legacy = source("legacy", null);
            AtomicReference<DrillGCodeToolPanel.Result> submitted = new AtomicReference<>();
            Node root = DrillGCodeToolPanel.build(List.of(probed, legacy), probed, List::of, submitted::set, () -> {});
            assertFalse(check(root, "#probe-confirm").isSelected());
            check(root, "#probe-confirm").setSelected(true);
            ((Button) root.lookup("#drill-generate")).fire();
            assertNotNull(submitted.get(), ((Label) root.lookup("#drill-error")).getText());
            assertEquals(saved, submitted.get().cncSettings());
            CncSettingsPanelTest.<DrillGCodeToolPanel.SourceCandidate>combo(root, "#drill-source").setValue(legacy);
            assertEquals(GCodePreprocessor.FX_PORTABLE, combo(root, "#drill-preprocessor").getValue());
            assertEquals("None", field(root, "#drill-end-xy").getText());
            assertFalse(check(root, "#probe-confirm").isSelected());
            CncSettingsPanelTest.<DrillGCodeToolPanel.SourceCandidate>combo(root, "#drill-source").setValue(probed);
            assertEquals("-5.0", field(root, "#probe-depth").getText());
            assertEquals("0.5", field(root, "#probe-contact-z").getText());
            assertFalse(check(root, "#probe-confirm").isSelected());
            CncSettingsPanelTest.<DrillGCodeToolPanel.SourceCandidate>combo(root, "#drill-source").setValue(legacy);
            ((Button) root.lookup("#drill-reset")).fire();
            assertEquals(probed, combo(root, "#drill-source").getValue());
            assertEquals(GCodePreprocessor.FX_PORTABLE, combo(root, "#drill-preprocessor").getValue());
            assertFalse(check(root, "#drill-tool-change").isSelected());
        });
    }

    @Test
    void deletedSelectedToolsDoNotSilentlySelectOtherTools() throws Exception {
        onFx(() -> {
            var settings = new DrillCncSettings(GCodePreprocessor.DEFAULT,
                    new GCodeGenerator.DrillJobOptions(false, 3, 3, null, null), List.of(99), DrillCncSettings.ToolOrder.NO);
            var source = source("missing-tool", settings);
            AtomicReference<DrillGCodeToolPanel.Result> submitted = new AtomicReference<>();
            Node root = DrillGCodeToolPanel.build(List.of(source), source, List::of, submitted::set, () -> {});
            ((Button) root.lookup("#drill-generate")).fire();
            assertNull(submitted.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("Selecione"));
            assertTrue(((Label) root.lookup("#drill-feedback")).getText().contains("nao existem"));
        });
    }
}
