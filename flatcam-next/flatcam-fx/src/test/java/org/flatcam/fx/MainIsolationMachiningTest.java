package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Button;
import org.flatcam.app.project.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.isolation.*;
import org.json.JSONObject;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@EnabledOnOs(OS.WINDOWS)
class MainIsolationMachiningTest {
    @TempDir Path directory;
    static LegacyToolsDatabase.MillingTool db(double diameter, ToolProfile profile, double feed) {
        return new LegacyToolsDatabase.MillingTool("CAM", diameter, profile,
                new GeometryGCodeParameters(3, .15, true, .05, feed, 12000, false, 0, null, 40, true, .25, true, .01), null);
    }
    static ProjectFile snapshot(MainWindow window) throws Exception {
        return TerminalPanelTest.fx(() -> {
            var m = MainWindow.class.getDeclaredMethod("snapshotProject"); m.setAccessible(true); return (ProjectFile) m.invoke(window);
        });
    }
    static GeometryCncToolPanel.Result cnc(ProjectFile.GeometryEntry entry) throws Exception {
        return TerminalPanelTest.fx(() -> {
            var captured = new AtomicReference<GeometryCncToolPanel.Result>();
            var panel = GeometryCncToolPanel.build(entry.units(), entry.geometry(), entry.tools(), entry.cncDefaults(), entry.cncSettings(),
                    captured::set, () -> {});
            ((Button) panel.lookup("#cnc-generate")).fire();
            assertNotNull(captured.get(), "restored CNC form must accept imported settings"); return captured.get();
        });
    }
    @ParameterizedTest @CsvSource({"MM,true", "MM,false", "IN,true", "IN,false"})
    void dbCuttingSettingsFollowToolIndicesAcrossCombinedAndSeparatePasses(String units, boolean combined) throws Exception {
        try (var s = new MainCamFlowTest.Session(units)) {
            double a = .5 * s.unit, b = .25 * s.unit;
            var tool = db(a, ToolProfile.C2, 105 * s.unit);
            var result = new IsolationToolPanel.Result(new IsolationToolPanel.SourceCandidate(s.item, s.image),
                    List.of(new IsolationParameters(a, 2, .1, IsolationType.BOTH), new IsolationParameters(b, 1, .1, IsolationType.BOTH)),
                    Map.of(a, ToolProfile.C2, b, ToolProfile.C4), false, true, combined, false, false, null, null, Map.of(a, tool));
            var h = TerminalPanelTest.fx(() -> s.startFx("Isolation", result)); s.release.countDown();
            h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            Path path = directory.resolve("isolation.fcnproj"); s.window.saveProject(path); s.window.openProject(path);
            var project = snapshot(s.window);
            int imported = 0;
            for (var entry : project.geometries()) {
                if (entry.name().equals("boundary")) continue;
                boolean usesDb = entry.tools().stream().anyMatch(t -> t.toolDiameter() == a);
                if (!usesDb) { assertNull(entry.cncSettings()); continue; }
                imported++;
                var settings = entry.cncSettings(); assertNotNull(settings);
                for (var indexed : settings.parametersByTool().entrySet()) {
                    assertEquals(a, entry.tools().get(indexed.getKey()).toolDiameter());
                    assertEquals(tool.parameters(), indexed.getValue());
                }
                var form = cnc(entry);
                for (int i = 0; i < form.tools().size(); i++) {
                    var p = form.parametersByTool().getOrDefault(i, form.parameters());
                    if (form.tools().get(i).toolDiameter() == a) assertEquals(105 * s.unit, p.feedRate());
                    else assertEquals(units.equals("MM") ? 300 : 12, p.feedRate(), "manual cutter must not inherit DB feed");
                }
                var job = GCodeGenerator.generateGeometryCncJob(units, form.tools(), form.parameters(), form.vTools(), form.parametersByTool(),
                        org.flatcam.cam.CancellationToken.none(), form.preprocessor());
                assertTrue(job.gcode().contains("S12000")); assertFalse(job.cutGeometry().isEmpty());
            }
            assertEquals(combined ? 1 : 2, imported);
        }
    }
    @ParameterizedTest @CsvSource({"C2,false", "V,true"})
    void isolationProjectionCarriesMachiningAndVTipAndPreventsDoubleCompensation(String profile, boolean v) throws Exception {
        var root = new JSONObject("""
                {"1":{"name":"iso", "tooldia":0.3,"tool_type":"%s", "offset":"Out", "data":{
                "tool_target":3,"tools_iso_passes":2,"cutz":-0.2,"travelz":3,"feedrate":90,
                "feedrate_z":30,"multidepth":true,"depthperpass":0.05,"spindlespeed":12000,
                "vtipdia":0.05,"vtipangle":60}}}
                """.formatted(profile));
        var tool = LegacyToolsDatabase.isolationTools(root).getFirst();
        assertNotNull(tool.machining()); assertEquals(90, tool.machining().parameters().feedRate());
        assertEquals(ToolPathOffset.PATH, tool.machining().parameters().offset());
        assertEquals(v, tool.machining().tip() != null);
        var broken = new JSONObject(root.toString()); broken.getJSONObject("1").getJSONObject("data").remove("travelz");
        assertThrows(java.io.IOException.class, () -> LegacyToolsDatabase.isolationTools(broken));
    }
}
