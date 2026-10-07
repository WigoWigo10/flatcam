package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.scene.control.TreeItem;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Offscreen real MainWindow worker/callback tests; no preferences or private files. */
@EnabledOnOs(OS.WINDOWS)
class MainDenseCncPreviewTest {
    @TempDir Path directory;

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object call(MainWindow window, String name, Class<?>[] types, Object... args) throws Exception {
        var method = MainWindow.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(window, args);
    }
    private static Map<TreeItem<String>, Object> entries(MainCamFlowTest.Session s) throws Exception {
        return (Map<TreeItem<String>, Object>) field("cncJobByItem").get(s.window);
    }
    private static Object component(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }
    private static String denseCode() {
        var code = new StringBuilder("; FCFX MILL D0.2\nG21\nG0 X0 Y0\nG1 Z-1 F60\n");
        for (int x = 1; x <= 60_000; x++) code.append("G1 X").append(x).append(" Y0\n");
        return code.toString();
    }
    private JobHandle<?> open(MainCamFlowTest.Session s, String code) throws Exception {
        Path file = directory.resolve("dense.nc"); Files.writeString(file, code);
        return TerminalPanelTest.fx(() -> {
            call(s.window, "openGCodeQueue", new Class<?>[]{List.class, int.class}, List.of(file.toFile()), 0);
            return (JobHandle<?>) field("runningJob").get(s.window);
        });
    }
    private static TreeItem<String> add(MainCamFlowTest.Session s) throws Exception {
        String code = "G21\nG0 X0 Y0\nG1 Z-1 F60\nG1 X1 Y0\n";
        var preview = GCodeToolpathParser.parse(code, () -> false, fraction -> {});
        return TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addCncJobToProject",
                new Class<?>[]{String.class, String.class, Path.class, String.class,
                        org.locationtech.jts.geom.Geometry.class, org.locationtech.jts.geom.Geometry.class},
                "original.nc", "original", null, code, preview.travelGeometry(), preview.cutGeometry()));
    }
    private static JobHandle<?> edit(MainCamFlowTest.Session s, TreeItem<String> item, String code,
                                    AtomicBoolean applied, AtomicReference<String> failure) throws Exception {
        return TerminalPanelTest.fx(() -> {
            assertTrue((boolean) call(s.window, "applyGCodeEdit",
                    new Class<?>[]{TreeItem.class, String.class, Runnable.class, Consumer.class},
                    item, code, (Runnable) () -> applied.set(true), (Consumer<String>) failure::set));
            return (JobHandle<?>) field("runningJob").get(s.window);
        });
    }

    @Test void importsAllDenseMovesThroughWorkerWithoutChangingFile() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            String code = denseCode(); var h = open(s, code);
            assertNotNull(h);
            TerminalPanelTest.fx(() -> { assertFalse(h.completion().isDone()); assertTrue(entries(s).isEmpty()); return null; });
            s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            TerminalPanelTest.fx(() -> {
                assertEquals(1, entries(s).size());
                var entry = entries(s).values().iterator().next();
                assertEquals(code, component(entry, "gcode"));
                var stats = (GCodeToolpathParser.ToolpathStats) component(entry, "stats");
                assertEquals(60_000, stats.xyDistance());
                assertEquals(60_000, stats.steps().getLast().xy()[120_000]);
                return null;
            });
            assertEquals(code, Files.readString(directory.resolve("dense.nc")));
        }
    }

    @ParameterizedTest @ValueSource(strings={"cancel", "project"})
    void lateImportCancellationOrProjectChangeDiscardsResult(String change) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var h = open(s, denseCode());
            TerminalPanelTest.fx(() -> {
                s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS);
                if (change.equals("cancel")) h.cancel(); else field("tclProjectEpoch").setLong(s.window, 123);
                return null;
            });
            s.awaitUi();
            TerminalPanelTest.fx(() -> { assertTrue(entries(s).isEmpty()); return null; });
        }
    }

    @Test void editedDenseProgramPreservesCodeAndCompleteNavigation() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var item = add(s); var applied = new AtomicBoolean(); var failure = new AtomicReference<String>();
            String code = denseCode(); var h = edit(s, item, code, applied, failure);
            s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            TerminalPanelTest.fx(() -> {
                assertTrue(applied.get()); assertNull(failure.get());
                var entry = entries(s).get(item);
                assertEquals(code, component(entry, "gcode"));
                var stats = (GCodeToolpathParser.ToolpathStats) component(entry, "stats");
                assertEquals(60_000, stats.steps().getLast().length());
                assertNotNull(component(entry, "cutCenterlines"));
                return null;
            });
        }
    }

    @ParameterizedTest @ValueSource(strings={"cancel", "project", "remove"})
    void lateEditCancellationOrSourceChangeKeepsOriginal(String change) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var item = add(s); var original = TerminalPanelTest.fx(() -> entries(s).get(item));
            var applied = new AtomicBoolean(); var failure = new AtomicReference<String>();
            var h = edit(s, item, denseCode(), applied, failure);
            TerminalPanelTest.fx(() -> {
                s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS);
                switch (change) {
                    case "cancel" -> h.cancel();
                    case "project" -> field("tclProjectEpoch").setLong(s.window, 123);
                    default -> entries(s).remove(item);
                }
                return null;
            });
            s.awaitUi();
            TerminalPanelTest.fx(() -> {
                assertFalse(applied.get()); assertNotNull(failure.get());
                if (!change.equals("remove")) assertSame(original, entries(s).get(item));
                else assertFalse(entries(s).containsKey(item));
                return null;
            });
        }
    }

    @Test void queuedOldProgressAndCompletionCannotEndANewerJob() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var item = add(s); var original = TerminalPanelTest.fx(() -> entries(s).get(item));
            var applied = new AtomicBoolean(); var failure = new AtomicReference<String>();
            var h = edit(s, item, denseCode(), applied, failure);
            var newer = TerminalPanelTest.fx(() -> {
                s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS);
                var next = s.jobs.submit(context -> "newer", null);
                field("runningJob").set(s.window, next);
                ((javafx.scene.control.Label) field("statusLabel").get(s.window)).setText("newer");
                return next;
            });
            TerminalPanelTest.fx(() -> {
                assertSame(newer, field("runningJob").get(s.window));
                assertEquals("newer", ((javafx.scene.control.Label) field("statusLabel").get(s.window)).getText());
                assertSame(original, entries(s).get(item));
                assertFalse(applied.get()); assertNull(failure.get());
                call(s.window, "onJobFinished", new Class<?>[]{});
                return null;
            });
        }
    }
}
