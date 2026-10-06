package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import javafx.scene.control.*;
import org.flatcam.app.job.*;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.cam.excellon.*;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

/** Real MainWindow publication guards; intentionally never reaches success that saves preferences. */
@EnabledOnOs(OS.WINDOWS)
class DrillingLiveJobTest {
    @TempDir Path directory;
    private static Field field(String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final CountDownLatch release = new CountDownLatch(1);
        final MainWindow window;
        final TreeItem<String> item;
        final ExcellonImage image;
        final DrillGCodeToolPanel.Result result;
        Session() throws Exception {
            window = TerminalPanelTest.fx(() -> {
                var host = new MainWindow(jobs);
                var root = new TreeItem<String>("root");
                for (String category : List.of("gerbersNode", "excellonNode", "geometryNode", "cncJobsNode")) {
                    var node = new TreeItem<String>(category); field(category).set(host, node); root.getChildren().add(node);
                }
                field("projectTree").set(host, new TreeView<>(root));
                field("toolTab").set(host, new Tab("Drilling"));
                return host;
            });
            image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "%", "T1", "X10.0Y0.0", "M30"));
            item = TerminalPanelTest.fx(() -> {
                var add = MainWindow.class.getDeclaredMethod("addExcellonToProject", String.class, Path.class, ExcellonImage.class);
                add.setAccessible(true);
                return (TreeItem<String>) add.invoke(window, "holes", null, image);
            });
            var area = CncExclusionArea.of(new GeometryFactory().toGeometry(new Envelope(4, 6, -1, 1)), CncExclusionArea.Strategy.AROUND, 0);
            var options = new GCodeGenerator.DrillJobOptions(false, 15, .5, 0.0, 0.0).withExclusions(true, List.of(area));
            result = new DrillGCodeToolPanel.Result(new DrillGCodeToolPanel.SourceCandidate(item, image, Map.of()),
                    Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, false)), List.of(1), options,
                    GCodePreprocessor.FX_PORTABLE, DrillCncSettings.ToolOrder.NO);
            var occupied = new CountDownLatch(1);
            jobs.submit(context -> { occupied.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return null; }, null);
            assertTrue(occupied.await(10, TimeUnit.SECONDS));
        }
        Method startMethod() throws Exception {
            var method = MainWindow.class.getDeclaredMethod("startDrillGCodeGeneration", TreeItem.class, ExcellonImage.class,
                    DrillGCodeToolPanel.Result.class, Path.class);
            method.setAccessible(true); return method;
        }
        JobHandle<?> start(Path output) throws Exception {
            return TerminalPanelTest.fx(() -> {
                startMethod().invoke(window, item, image, result, output);
                return (JobHandle<?>) field("runningJob").get(window);
            });
        }
        @Override public void close() throws Exception {
            release.countDown(); jobs.shutdown();
            TerminalPanelTest.fx(() -> { window.disposeViewport(); return null; });
        }
    }

    @ParameterizedTest @ValueSource(strings = {"rename", "remove", "project", "parameters"})
    void changedSourceProjectOrDefaultsCannotPublishOldWork(String change) throws Exception {
        try (var session = new Session()) {
            Path output = directory.resolve("original.nc"); Files.writeString(output, "original");
            var handle = session.start(output);
            TerminalPanelTest.fx(() -> {
                switch (change) {
                    case "rename" -> session.item.setValue("changed");
                    case "remove" -> ((Map<?, ?>) field("excellonByItem").get(session.window)).remove(session.item);
                    case "project" -> field("tclProjectEpoch").setLong(session.window, 1234);
                    default -> ((Map<TreeItem<String>, Map<Integer, DrillGCodeParameters>>) field("drillDefaultsByItem").get(session.window))
                            .put(session.item, Map.of(1, new DrillGCodeParameters(5, 2, 100, 0, false)));
                }
                return null;
            });
            session.release.countDown();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> handle.completion().get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals("original", Files.readString(output));
            TerminalPanelTest.fx(() -> {
                assertTrue(((Map<?, ?>) field("cncJobByItem").get(session.window)).isEmpty());
                return null;
            });
            try (var files = Files.list(directory)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".flatcam-drilling-")));
            }
        }
    }

    @Test void busyJobRefusesAnotherGenerationAndCancelPreservesDestination() throws Exception {
        try (var session = new Session()) {
            Path output = directory.resolve("original.nc"); Files.writeString(output, "original");
            var handle = session.start(output);
            TerminalPanelTest.fx(() -> {
                var failure = assertThrows(InvocationTargetException.class,
                        () -> session.startMethod().invoke(session.window, session.item, session.image, session.result, output));
                assertInstanceOf(IllegalStateException.class, failure.getCause());
                return null;
            });
            handle.cancel(); session.release.countDown();
            assertThrows(CancellationException.class, () -> handle.completion().get(10, TimeUnit.SECONDS));
            assertEquals("original", Files.readString(output));
        }
    }
}
