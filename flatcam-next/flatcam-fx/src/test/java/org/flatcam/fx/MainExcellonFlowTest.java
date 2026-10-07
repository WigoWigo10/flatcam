package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.*;
import org.flatcam.cam.excellon.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

@EnabledOnOs(OS.WINDOWS)
class MainExcellonFlowTest {
    @TempDir Path directory;
    private static java.lang.reflect.Field field(String name) throws Exception {
        var f = MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Object call(MainWindow window, String name, Class<?>[] types, Object... args) throws Exception {
        var m = MainWindow.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(window, args);
    }
    private static ExcellonImage image(String units) {
        return new ExcellonParser().parse(List.of("M48", units.equals("MM") ? "METRIC" : "INCH", "T1C1.0", "%",
                "T1", "X1.0Y1.0", "X4.0Y3.0", "X2.0Y1.0G85X3.0Y1.0", "M30"));
    }
    private static TreeItem<String> add(MainCamFlowTest.Session s, ExcellonImage image) throws Exception {
        return TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addExcellonToProject",
                new Class<?>[]{String.class, Path.class, ExcellonImage.class}, "holes", null, image));
    }
    private static JobHandle<?> mill(MainCamFlowTest.Session s, TreeItem<String> item, ExcellonImage image,
                                     ExcellonMillingGenerator.Kind kind, LegacyToolsDatabase.MillingTool db) throws Exception {
        return TerminalPanelTest.fx(() -> {
            call(s.window, "runExcellonMilling", new Class<?>[]{ExcellonMillingToolPanel.Result.class},
                    new ExcellonMillingToolPanel.Result(new ExcellonMillingToolPanel.SourceCandidate(item, image),
                            Set.of(1), db == null ? .3 : db.diameter(), kind, db));
            return (JobHandle<?>) field("runningJob").get(s.window);
        });
    }
    @ParameterizedTest @ValueSource(strings={"rename", "remove", "replace", "project"})
    void millingCannotPublishAfterSourceOrProjectChanges(String change) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var image = image("MM"); var item = add(s, image);
            var handle = mill(s, item, image, ExcellonMillingGenerator.Kind.DRILLS, null);
            TerminalPanelTest.fx(() -> {
                var map = (Map) field("excellonByItem").get(s.window);
                switch(change) {
                    case "rename" -> item.setValue("new-name");
                    case "remove" -> map.remove(item);
                    case "replace" -> map.put(item, image("MM"));
                    default -> field("tclProjectEpoch").setLong(s.window, 123);
                }
                return null;
            });
            s.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS); s.assertNothingPublished();
        }
    }
    @Test void lateCancellationDiscardsMillingAndReleasesUiJob() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var image = image("MM"); var item = add(s, image);
            var h = mill(s, item, image, ExcellonMillingGenerator.Kind.SLOTS, null);
            TerminalPanelTest.fx(() -> {
                s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); h.cancel(); return null;
            });
            s.assertNothingPublished();
        }
    }
    @ParameterizedTest @CsvSource({"MM,DRILLS", "MM,SLOTS", "IN,DRILLS", "IN,SLOTS"})
    void millingDatabaseParametersReachCncAndNativeProjectWithoutClosingAnotherPanel(String units, String kind) throws Exception {
        try (var s = new MainCamFlowTest.Session(units)) {
            var image = image(units); var item = add(s, image);
            var params = new GeometryGCodeParameters(2, .2, true, .1, 80, 12345, false, 0, null,
                    40, true, .25, true, .05);
            var db = new LegacyToolsDatabase.MillingTool("cutter", .3, ToolProfile.C2, params, null);
            var h = mill(s, item, image, ExcellonMillingGenerator.Kind.valueOf(kind), db);
            var other = new VBox(new Label("other panel"));
            TerminalPanelTest.fx(() -> { ((Tab) field("toolTab").get(s.window)).setContent(other); return null; });
            s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            var snapshot = TerminalPanelTest.fx(() -> {
                assertSame(other, ((Tab) field("toolTab").get(s.window)).getContent());
                return (ProjectFile) call(s.window, "snapshotProject", new Class<?>[]{});
            });
            var geometry = snapshot.geometries().stream().filter(g -> !g.name().equals("boundary")).findFirst().orElseThrow();
            assertEquals(params, geometry.cncDefaults()); assertEquals(ToolProfile.C2, geometry.tools().getFirst().toolProfile());
            assertFalse(geometry.geometry().isEmpty()); assertEquals(units, geometry.units());
            var generated = GeometryCncGeneration.generate(units, geometry.tools(), geometry.cncDefaults(), Map.of(), Map.of(),
                    GCodePreprocessor.FX_PORTABLE, directory.resolve("mill.nc"), new org.flatcam.app.job.JobContext() {
                        public boolean isCancelled() { return false; }
                        public void reportProgress(double fraction, String text) { }
                    }, () -> {});
            assertTrue(generated.job().gcode().contains("S12345")); assertNotNull(generated.preview());
            Path project = directory.resolve("mill.fcnproj"); s.window.saveProject(project); s.window.openProject(project);
            var reopened = TerminalPanelTest.fx(() -> (ProjectFile) call(s.window, "snapshotProject", new Class<?>[]{}));
            var restored = reopened.geometries().stream().filter(g -> g.name().equals(geometry.name())).findFirst().orElseThrow();
            assertEquals(params, restored.cncDefaults()); assertEquals(geometry.tools(), restored.tools());
            assertEquals(2, image.totalDrills()); assertEquals(1, image.totalSlots());
        }
    }
    @ParameterizedTest @ValueSource(strings={"rename", "project", "settings"})
    void drillingRevalidatesBeforeReplacingDestination(String change) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var image = image("MM"); var item = add(s, image); Path output = directory.resolve("drill.nc");
            Files.writeString(output, "original");
            var h = TerminalPanelTest.fx(() -> {
                var source = new DrillGCodeToolPanel.SourceCandidate(item, image, Map.of());
                var settings = Map.of(1, new DrillGCodeParameters(2, 1, 100, 10000, false));
                call(s.window, "startDrillGCodeGeneration", new Class<?>[]{TreeItem.class, ExcellonImage.class, DrillGCodeToolPanel.Result.class, Path.class},
                        item, image, new DrillGCodeToolPanel.Result(source, settings, List.of(1),
                                new GCodeGenerator.DrillJobOptions(false, 15, .5, null, null), GCodePreprocessor.FX_PORTABLE,
                                DrillCncSettings.ToolOrder.FORWARD), output);
                var handle = (JobHandle<?>) field("runningJob").get(s.window);
                if (change.equals("rename")) item.setValue("renamed");
                else if (change.equals("project")) field("tclProjectEpoch").setLong(s.window, 123);
                else ((Map) field("drillDefaultsByItem").get(s.window)).put(item, settings);
                return handle;
            });
            s.release.countDown(); assertThrows(java.util.concurrent.ExecutionException.class, () -> h.completion().get(10, TimeUnit.SECONDS));
            s.awaitUi(); assertEquals("original", Files.readString(output));
            TerminalPanelTest.fx(() -> { assertTrue(((Map) field("cncJobByItem").get(s.window)).isEmpty()); return null; });
        }
    }
}
