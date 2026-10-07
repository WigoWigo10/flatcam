package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flatcam.app.project.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.ncc.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@EnabledOnOs(OS.WINDOWS)
class MainNccMachiningTest {
    @TempDir Path directory;
    @ParameterizedTest @CsvSource({"MM,itself,false", "MM,area,true", "MM,reference,false",
            "IN,itself,false", "IN,area,true", "IN,reference,false"})
    void clearIsoAndVTipKeepTheirCuttingSettingsAcrossBoundariesRestAndNativePersistence(String units, String kind, boolean rest) throws Exception {
        try (var s = new MainCamFlowTest.Session(units)) {
            double clear = .5 * s.unit, manual = .25 * s.unit, iso = .2 * s.unit;
            var clearDb = MainIsolationMachiningTest.db(clear, ToolProfile.C2, 95 * s.unit);
            var isoDb = new LegacyToolsDatabase.MillingTool("V ISO", iso, ToolProfile.V,
                    new GeometryGCodeParameters(3 * s.unit, .15 * s.unit, true, .05 * s.unit, 75 * s.unit,
                            13579, false), new VTipSettings(.05 * s.unit, 60));
            NccBoundary boundary = switch(kind) {
                case "area" -> new NccBoundary.Area(s.referenceGeometry);
                case "reference" -> new NccBoundary.ReferenceGeometry(s.referenceGeometry);
                default -> new NccBoundary.Itself();
            };
            var params = new NccParameters(List.of(clear, manual), .4, s.unit, NccMethod.STANDARD,
                    true, true, 0, rest, NccOrder.REVERSE, boundary, List.of(iso),
                    Map.of(clear, new NccToolSettings(.3, NccMethod.LINES, false, true, 0)), NccMillingType.CLIMB);
            var source = new NccToolPanel.SourceCandidate(s.item, "board", units, true, s.image.solidGeometry());
            var reference = kind.equals("reference") ? new NccToolPanel.ReferenceCandidate(s.reference, "boundary", false, s.referenceGeometry) : null;
            var result = new NccToolPanel.Result(source, params, false, Map.of(clear, ToolProfile.C2, iso, ToolProfile.V), reference,
                    Map.of(clear, clearDb, iso, isoDb));
            var h = TerminalPanelTest.fx(() -> s.startFx("NCC", result)); s.release.countDown();
            h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            Path project = directory.resolve("ncc.fcnproj"); s.window.saveProject(project); s.window.openProject(project);
            var entry = MainIsolationMachiningTest.snapshot(s.window).geometries().stream()
                    .filter(g -> !g.name().equals("boundary")).findFirst().orElseThrow();
            assertFalse(entry.geometry().isEmpty()); assertEquals(units, entry.units());
            assertTrue(entry.tools().stream().anyMatch(t -> t.toolProfile() == ToolProfile.V));
            var form = MainIsolationMachiningTest.cnc(entry);
            int imported = 0;
            for (int i = 0; i < form.tools().size(); i++) {
                var tool = form.tools().get(i); var p = form.parametersByTool().getOrDefault(i, form.parameters());
                if (tool.toolDiameter() == clear) { assertEquals(95 * s.unit, p.feedRate()); imported++; }
                else if (tool.toolDiameter() == iso) {
                    assertEquals(75 * s.unit, p.feedRate()); assertEquals(isoDb.tip(), form.vTools().get(i)); imported++;
                } else assertEquals(units.equals("MM") ? 300 : 12, p.feedRate());
                assertEquals(ToolPathOffset.PATH, p.offset());
            }
            assertTrue(imported >= 1);
            var generated = GCodeGenerator.generateGeometryCncJob(units, form.tools(), form.parameters(), form.vTools(), form.parametersByTool(),
                    org.flatcam.cam.CancellationToken.none(), form.preprocessor());
            assertTrue(generated.gcode().contains("S13579")); assertFalse(generated.cutGeometry().isEmpty());
            var preview = GCodeToolpathParser.parse(generated.gcode(), () -> false, fraction -> {});
            assertTrue(preview.plotAvailable(), preview.warning());
            assertNotNull(preview.cutGeometry());
            assertFalse(preview.cutGeometry().isEmpty());
        }
    }
    @Test void nccDatabaseProjectionCarriesMachiningAndStillMapsVToIso() throws Exception {
        var db = new JSONObject("""
                {"1":{"tooldia":0.2,"tool_type":"V","data":{"tool_target":5,
                "tools_ncc_operation":"clear","cutz":-0.1,"travelz":2,"feedrate":123,
                "vtipdia":0.05,"vtipangle":60}}}
                """);
        var tool = LegacyToolsDatabase.nccTools(db).getFirst();
        assertEquals(NccOperation.ISO, tool.operation()); assertNotNull(tool.machining());
        assertEquals(123, tool.machining().parameters().feedRate()); assertNotNull(tool.machining().tip());
        db.getJSONObject("1").getJSONObject("data").put("cutz", 1);
        assertThrows(java.io.IOException.class, () -> LegacyToolsDatabase.nccTools(db));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"Isolation", "NCC"})
    void realCamFormsCarryStoredDatabaseMachiningOnlyForSelectedRows(String tool) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            TerminalPanelTest.fx(() -> {
                var captured = new java.util.concurrent.atomic.AtomicReference<Object>();
                javafx.scene.Node root;
                if (tool.equals("Isolation")) {
                    var source = new IsolationToolPanel.SourceCandidate(s.item, s.image);
                    root = IsolationToolPanel.build(List.of(source), source, List.of(), (src,p,done,cancel) -> false,
                            () -> {}, List::of, captured::set, () -> {});
                } else {
                    var source = new NccToolPanel.SourceCandidate(s.item, "board", "MM", true, s.image.solidGeometry());
                    root = NccToolPanel.build(List.of(source), source, List.of(), (src,p,done,cancel) -> false,
                            () -> {}, List::of, captured::set, () -> {});
                }
                var table = (javafx.scene.control.TableView<?>) root.lookup(".table-view"); assertNotNull(table);
                var row = table.getItems().getFirst();
                var dia = row.getClass().getDeclaredField("diameter"); dia.setAccessible(true);
                double diameter = dia.getDouble(row);
                var db = MainIsolationMachiningTest.db(diameter, ToolProfile.C1, 87);
                var machining = row.getClass().getDeclaredField("machining"); machining.setAccessible(true); machining.set(row, db);
                table.getSelectionModel().clearAndSelect(0);
                ((javafx.scene.control.Button) root.lookup(tool.equals("Isolation") ? "#isolation-generate" : "#ncc-generate")).fire();
                assertNotNull(captured.get());
                var imported = tool.equals("Isolation") ? ((IsolationToolPanel.Result) captured.get()).machining()
                        : ((NccToolPanel.Result) captured.get()).machining();
                assertEquals(Map.of(diameter, db), imported); return null;
            });
        }
    }
}
