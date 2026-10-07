package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.*;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.cutout.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.isolation.*;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

/** Real UI CAM publication and a UI CAM -> Tcl CNC/export -> native project round-trip.
 * No Stage, user preferences, private projects, or physical machine are touched. */
@EnabledOnOs(OS.WINDOWS)
class MainCamFlowTest {
    @TempDir Path directory;
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static Field field(String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object invoke(MainWindow window, String name, Class<?>[] types, Object... values) throws Exception {
        Method method = MainWindow.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(window, values);
    }
    private static Object component(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }

    private static javafx.scene.Node control(javafx.scene.Node root, String id) {
        if (id.equals(root.getId())) return root;
        if (root instanceof TitledPane pane) {
            var found = control(pane.getContent(), id); if (found != null) return found;
        }
        if (root instanceof javafx.scene.Parent parent) for (var child : parent.getChildrenUnmodifiable()) {
            var found = control(child, id); if (found != null) return found;
        }
        return null;
    }

    static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final CountDownLatch release = new CountDownLatch(1);
        final MainWindow window;
        final GerberImage image;
        final TreeItem<String> item;
        final TreeItem<String> reference;
        final Geometry referenceGeometry;
        final double unit;
        Session(String units) throws Exception {
            unit = units.equals("MM") ? 1 : 1 / 25.4;
            Geometry copper = FACTORY.toGeometry(new Envelope(0, 10 * unit, 0, 8 * unit));
            image = GerberImage.of(units, Map.of(), copper, copper.getBoundary(), Map.of());
            referenceGeometry = FACTORY.toGeometry(new Envelope(-2 * unit, 12 * unit, -2 * unit, 10 * unit));
            window = TerminalPanelTest.fx(() -> {
                var host = new MainWindow(jobs);
                var root = new TreeItem<String>("root"); root.setExpanded(true);
                for (String category : List.of("gerbersNode", "excellonNode", "geometryNode", "cncJobsNode")) {
                    var node = new TreeItem<String>(category); node.setExpanded(true);
                    field(category).set(host, node); root.getChildren().add(node);
                }
                field("projectTree").set(host, new TreeView<>(root));
                var tool = new Tab("CAM", new VBox()); var properties = new Tab("Properties");
                field("toolTab").set(host, tool); field("propertiesTab").set(host, properties);
                field("leftTabs").set(host, new TabPane(properties, tool));
                field("unitsLabel").set(host, new Label(units));
                return host;
            });
            item = TerminalPanelTest.fx(() -> (TreeItem<String>) invoke(window, "addGerberToProject",
                    new Class<?>[]{String.class, Path.class, GerberImage.class}, "board", null, image));
            reference = TerminalPanelTest.fx(() -> (TreeItem<String>) invoke(window, "addGeometryToProject",
                    new Class<?>[]{String.class, String.class, String.class, Geometry.class, boolean.class},
                    "boundary", "", units, referenceGeometry, false));
            var occupied = new CountDownLatch(1);
            jobs.submit(context -> { occupied.countDown(); assertTrue(release.await(15, TimeUnit.SECONDS)); return null; }, null);
            assertTrue(occupied.await(10, TimeUnit.SECONDS));
        }
        Object result(String tool, boolean useReference) {
            double diameter = .5 * unit;
            if (tool.equals("Isolation")) return new IsolationToolPanel.Result(
                    new IsolationToolPanel.SourceCandidate(item, image),
                    List.of(new IsolationParameters(diameter, 2, .1, IsolationType.BOTH)), Map.of(diameter, ToolProfile.C2),
                    false, true, true, false, false, useReference ? referenceGeometry : null,
                    useReference ? new IsolationToolPanel.ExceptionArea("boundary", referenceGeometry, reference) : null);
            if (tool.equals("Cutout")) {
                var parameters = new GeometryGCodeParameters(2 * unit, 1.6 * unit, true, .4 * unit, 120 * unit, 10000, false);
                return new CutoutToolPanel.Result(new CutoutParameters(diameter, .2 * unit, false, CutoutKind.SINGLE,
                        CutoutShape.RECTANGULAR, .6 * unit, GapPattern.FOUR), CutoutToolPanel.GapType.BRIDGE,
                        .4 * unit, .2 * unit, List.of(), parameters, parameters, ToolProfile.C3);
            }
            var source = new NccToolPanel.SourceCandidate(item, "board", image.units(), true, image.solidGeometry());
            var parameters = new NccParameters(List.of(diameter), .4, 1 * unit, NccMethod.STANDARD, true, true, 0,
                    false, NccOrder.REVERSE, useReference ? new NccBoundary.ReferenceGeometry(referenceGeometry)
                            : new NccBoundary.Itself(), List.of());
            return new NccToolPanel.Result(source, parameters, false, Map.of(diameter, ToolProfile.C4),
                    useReference ? new NccToolPanel.ReferenceCandidate(reference, "boundary", false, referenceGeometry) : null);
        }
        JobHandle<?> start(String tool, boolean useReference) throws Exception {
            return TerminalPanelTest.fx(() -> startFx(tool, result(tool, useReference)));
        }
        JobHandle<?> startFx(String tool, Object result) throws Exception {
            switch (tool) {
                case "Isolation" -> invoke(window, "runIsolationGeneration",
                        new Class<?>[]{TreeItem.class, GerberImage.class, IsolationToolPanel.Result.class}, item, image, result);
                case "Cutout" -> invoke(window, "runCutoutGeneration",
                        new Class<?>[]{TreeItem.class, String.class, Geometry.class, BooleanSupplier.class, CutoutToolPanel.Result.class},
                        item, image.units(), image.solidGeometry(), (BooleanSupplier) () -> true, result);
                default -> invoke(window, "runNccGeneration",
                        new Class<?>[]{TreeItem.class, String.class, Geometry.class, boolean.class, NccToolPanel.Result.class},
                        item, image.units(), image.solidGeometry(), true, result);
            }
            return (JobHandle<?>) field("runningJob").get(window);
        }
        Map<TreeItem<String>, Object> geometries() throws Exception {
            return (Map<TreeItem<String>, Object>) field("geometryByItem").get(window);
        }
        void awaitUi() throws Exception {
            for (int attempt = 0; attempt < 100; attempt++) {
                if (TerminalPanelTest.fx(() -> field("runningJob").get(window) == null)) return;
                Thread.sleep(10);
            }
            fail("CAM publication did not finish on FX");
        }
        void assertNothingPublished() throws Exception {
            awaitUi();
            TerminalPanelTest.fx(() -> { assertEquals(List.of(reference), List.copyOf(geometries().keySet())); return null; });
        }
        @Override public void close() throws Exception {
            release.countDown(); jobs.shutdown();
            TerminalPanelTest.fx(() -> { window.disposeViewport(); return null; });
        }
    }

    @ParameterizedTest @CsvSource({"Isolation,rename", "Isolation,remove", "Isolation,replace", "Isolation,project",
            "NCC,rename", "NCC,remove", "NCC,replace", "NCC,project",
            "Cutout,rename", "Cutout,remove", "Cutout,replace", "Cutout,project"})
    void changedInputsCannotPublishComputedGeometry(String tool, String change) throws Exception {
        try (var session = new Session("MM")) {
            var handle = session.start(tool, false); assertNotNull(handle);
            TerminalPanelTest.fx(() -> {
                var map = (Map<TreeItem<String>, GerberImage>) field("gerberByItem").get(session.window);
                switch (change) {
                    case "rename" -> session.item.setValue("renamed");
                    case "remove" -> map.remove(session.item);
                    case "replace" -> map.put(session.item, GerberImage.of("MM", Map.of(), session.image.solidGeometry().copy(),
                            session.image.followGeometry().copy(), Map.of()));
                    default -> field("tclProjectEpoch").setLong(session.window, 42);
                }
                return null;
            });
            session.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS); session.assertNothingPublished();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Isolation", "NCC", "Cutout"})
    void stalePanelRefusesBeforeSubmittingAndBusyClickCannotReplaceJob(String tool) throws Exception {
        try (var session = new Session("MM")) {
            var handle = session.start(tool, false); assertNotNull(handle);
            assertSame(handle, session.start(tool, false));
            handle.cancel(); session.release.countDown();
            assertThrows(java.util.concurrent.CancellationException.class, () -> handle.completion().get(10, TimeUnit.SECONDS));
            session.assertNothingPublished();
            TerminalPanelTest.fx(() -> {
                ((Map<?, ?>) field("gerberByItem").get(session.window)).remove(session.item); return null;
            });
            assertNull(session.start(tool, false)); session.assertNothingPublished();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Isolation", "NCC", "Cutout"})
    void cancellationAfterWorkerSuccessBeforeFxPublicationStillDiscardsTheResult(String tool) throws Exception {
        try (var session = new Session("MM")) {
            TerminalPanelTest.fx(() -> {
                var handle = session.startFx(tool, session.result(tool, false));
                session.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS);
                handle.cancel(); // Publication callback is queued behind this FX action.
                return null;
            });
            session.assertNothingPublished();
        }
    }

    @ParameterizedTest @CsvSource({"Isolation,remove", "Isolation,replace", "Isolation,rename",
            "NCC,remove", "NCC,replace", "NCC,rename"})
    void changedReferenceOrExceptionObjectCannotPublish(String tool, String change) throws Exception {
        try (var session = new Session("MM")) {
            var handle = session.start(tool, true);
            TerminalPanelTest.fx(() -> {
                if (change.equals("rename")) session.reference.setValue("renamed");
                else if (change.equals("remove")) session.geometries().remove(session.reference);
                else session.geometries().put(session.reference, null);
                return null;
            });
            session.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS); session.awaitUi();
            TerminalPanelTest.fx(() -> { assertTrue(session.geometries().keySet().stream().allMatch(i -> i == session.reference)); return null; });
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Isolation", "NCC"})
    void referenceAlreadyRemovedWhenGenerateIsClickedRefusesToStart(String tool) throws Exception {
        try (var session = new Session("MM")) {
            TerminalPanelTest.fx(() -> { session.geometries().remove(session.reference); return null; });
            assertNull(session.start(tool, true));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Isolation", "NCC", "Cutout"})
    void anotherPanelAndHarmlessPlotChangesSurviveCompletion(String tool) throws Exception {
        try (var session = new Session("MM")) {
            var handle = session.start(tool, false);
            var replacement = new VBox(new Label("Another tool"));
            TerminalPanelTest.fx(() -> {
                ((Tab) field("toolTab").get(session.window)).setContent(replacement);
                session.reference.setValue("unrelated rename");
                var plot = (PlotAreaView) field("plotAreaView").get(session.window);
                plot.setLayerVisible(session.item, false); return null;
            });
            session.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS); session.awaitUi();
            TerminalPanelTest.fx(() -> {
                assertEquals(2, session.geometries().size());
                assertSame(replacement, ((Tab) field("toolTab").get(session.window)).getContent());
                assertNull(((TreeView<?>) field("projectTree").get(session.window)).getSelectionModel().getSelectedItem());
                assertFalse(((PlotAreaView) field("plotAreaView").get(session.window)).isLayerVisible(session.item));
                return null;
            });
        }
    }

    private static GCodeEditorController editor(Session session) throws Exception {
        return TerminalPanelTest.fx(() -> {
            var controller = new GCodeEditorController(new TabPane(), new GCodeEditorController.Host() {
                public void openToolPanel(String name, javafx.scene.Node content) { }
                public void closeToolPanel() { }
                public boolean apply(TreeItem<String> item, String text, Runnable success, java.util.function.Consumer<String> failure) { return false; }
                public boolean saveAs(String text, java.util.function.Consumer<String> done) { return false; }
                public void log(String text) { }
            });
            field("gcodeEditor").set(session.window, controller);
            controller.start(new TreeItem<>("draft"), "G21\n"); return controller;
        });
    }

    @ParameterizedTest @CsvSource({"Isolation,before", "Isolation,during", "NCC,before", "NCC,during", "Cutout,before", "Cutout,during"})
    void activeEditorPreventsStartingOrPublishingCam(String tool, String when) throws Exception {
        try (var session = new Session("MM")) {
            var handle = when.equals("during") ? session.start(tool, false) : null;
            var editor = editor(session);
            try {
                if (handle == null) assertNull(session.start(tool, false));
                else { session.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS); }
                session.assertNothingPublished();
            } finally { TerminalPanelTest.fx(() -> { editor.cancel(); return null; }); }
        }
    }

    @Test void isolationPanelCarriesTheChosenExceptionObjectNotOnlyItsGeometry() throws Exception {
        try (var session = new Session("MM")) {
            TerminalPanelTest.fx(() -> {
                var source = new IsolationToolPanel.SourceCandidate(session.item, session.image);
                var area = new IsolationToolPanel.ExceptionArea("boundary", session.referenceGeometry, session.reference);
                var captured = new java.util.concurrent.atomic.AtomicReference<IsolationToolPanel.Result>();
                var root = IsolationToolPanel.build(List.of(source), source, List.of(area), (s,p,done,cancel) -> false,
                        () -> {}, List::of, captured::set, () -> {});
                ((ComboBox<IsolationToolPanel.ExceptionArea>) control(root, "isolation-exception-object")).setValue(area);
                ((Button) control(root, "isolation-generate")).fire();
                assertNotNull(captured.get()); assertSame(area, captured.get().exceptionReference());
                assertSame(session.referenceGeometry, captured.get().exceptionMask()); return null;
            });
        }
    }

    @Test void nccPanelCarriesTheChosenReferenceObjectNotOnlyItsGeometry() throws Exception {
        try (var session = new Session("MM")) {
            TerminalPanelTest.fx(() -> {
                var source = new NccToolPanel.SourceCandidate(session.item, "board", "MM", true, session.image.solidGeometry());
                var reference = new NccToolPanel.ReferenceCandidate(session.reference, "boundary", false, session.referenceGeometry);
                var captured = new java.util.concurrent.atomic.AtomicReference<NccToolPanel.Result>();
                var root = NccToolPanel.build(List.of(source), source, List.of(reference), (s,p,done,cancel) -> false,
                        () -> {}, List::of, captured::set, () -> {});
                ((ComboBox<String>) control(root, "ncc-boundary-kind")).setValue("Reference Object");
                ((ComboBox<NccToolPanel.ReferenceCandidate>) control(root, "ncc-reference-object")).setValue(reference);
                ((Button) control(root, "ncc-generate")).fire();
                assertNotNull(captured.get()); assertSame(reference, captured.get().reference());
                assertSame(session.referenceGeometry, ((NccBoundary.ReferenceGeometry) captured.get().parameters().boundary()).geometry());
                return null;
            });
        }
    }

    @Test void realIsolationWithDrawnExceptionKeepsSeparatePassOutputs() throws Exception {
        try (var session = new Session("MM")) {
            var original = (IsolationToolPanel.Result) session.result("Isolation", false);
            Geometry mask = FACTORY.toGeometry(new Envelope(-2, 5, -2, 10));
            var result = new IsolationToolPanel.Result(original.source(), original.tools(), original.profiles(), false,
                    true, false, false, false, mask, null);
            var handle = TerminalPanelTest.fx(() -> session.startFx("Isolation", result));
            session.release.countDown(); handle.completion().get(10, TimeUnit.SECONDS); session.awaitUi();
            TerminalPanelTest.fx(() -> {
                assertEquals(3, session.geometries().size());
                for (var entry : session.geometries().entrySet()) if (entry.getKey() != session.reference) {
                    var path = (Geometry) component(entry.getValue(), "geometry");
                    assertFalse(path.isEmpty()); assertEquals(5, path.getEnvelopeInternal().getMinX(), 1e-9);
                    assertTrue(entry.getKey().getValue().contains("_p"));
                }
                return null;
            });
        }
    }

    @ParameterizedTest @CsvSource({"Isolation,MM", "NCC,MM", "Cutout,MM", "Isolation,IN", "NCC,IN", "Cutout,IN"})
    void uiCamToCncExportAndNativeReopenPreservesPathsToolsAndUnits(String tool, String units) throws Exception {
        try (var session = new Session(units)) {
            var handle = session.start(tool, false); session.release.countDown();
            handle.completion().get(10, TimeUnit.SECONDS); session.awaitUi();
            var generated = TerminalPanelTest.fx(() -> session.geometries().keySet().stream()
                    .filter(i -> i != session.reference).findFirst().orElseThrow());
            ProjectFile before = TerminalPanelTest.fx(() -> (ProjectFile) invoke(session.window, "snapshotProject", new Class<?>[]{}));
            var geometry = before.geometries().stream().filter(g -> g.name().equals(generated.getValue())).findFirst().orElseThrow();
            assertFalse(geometry.geometry().isEmpty()); assertEquals(units, geometry.units());
            assertEquals("board", geometry.sourceName()); assertTrue(geometry.strokeOnly());
            assertEquals(1, geometry.tools().size()); assertEquals(.5 * session.unit, geometry.tools().getFirst().toolDiameter());
            assertEquals(tool.equals("Isolation") ? ToolProfile.C2 : tool.equals("NCC") ? ToolProfile.C4 : ToolProfile.C3,
                    geometry.tools().getFirst().toolProfile());
            if (tool.equals("Cutout")) assertEquals(1.6 * session.unit, geometry.cncDefaults().cutDepth());
            session.window.cncjob(generated.getValue(), "job", .5 * session.unit, -.1 * session.unit,
                    2 * session.unit, 100 * session.unit, 50 * session.unit, 10000);
            Path code = directory.resolve("job.nc"); session.window.writeGcode("job", code, "", "");
            String text = Files.readString(code);
            assertTrue(text.contains(units.equals("MM") ? "G21" : "G20"));
            var preview = GCodeToolpathParser.parse(text, () -> false, fraction -> {});
            assertTrue(preview.plotAvailable()); assertEquals(units, preview.units());
            Path project = directory.resolve("flow.fcnproj"); session.window.saveProject(project); session.window.openProject(project);
            ProjectFile after = TerminalPanelTest.fx(() -> (ProjectFile) invoke(session.window, "snapshotProject", new Class<?>[]{}));
            var reopened = after.geometries().stream().filter(g -> g.name().equals(geometry.name())).findFirst().orElseThrow();
            assertTrue(geometry.geometry().equalsExact(reopened.geometry(), 1e-9));
            assertEquals(geometry.tools().getFirst().toolProfile(), reopened.tools().getFirst().toolProfile());
            assertEquals(geometry.cncDefaults(), reopened.cncDefaults()); assertEquals(units, reopened.units());
            assertEquals(text, after.cncJobs().getFirst().gcode());
        }
    }
}
