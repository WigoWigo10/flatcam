package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Label;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.tcl.TclInterpreter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Exercises the real live host and generators, not the argument-only FakeHost. */
@EnabledOnOs(OS.WINDOWS)
class TclLiveHostTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final MainWindow host;
        final TclInterpreter interpreter = new TclInterpreter();
        Session() throws Exception {
            host = TerminalPanelTest.fx(() -> {
                MainWindow window = new MainWindow(jobs);
                TreeItem<String> root = new TreeItem<>("root");
                for (String category : new String[]{"gerbersNode", "excellonNode", "geometryNode", "cncJobsNode"}) {
                    TreeItem<String> node = new TreeItem<>(category);
                    field(category).set(window, node); root.getChildren().add(node);
                }
                field("projectTree").set(window, new TreeView<>(root));
                field("unitsLabel").set(window, new Label());
                return window;
            });
            new TclFlatcamCommands(host).registerOn(interpreter);
        }
        TreeItem<String> geometry(String name) throws Exception {
            return TerminalPanelTest.fx(() -> {
                Method method = MainWindow.class.getDeclaredMethod("addGeometryToProject",
                        String.class, String.class, String.class, Geometry.class, boolean.class);
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                TreeItem<String> item = (TreeItem<String>) method.invoke(host, name, "", "MM",
                        FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)}), true);
                return item;
            });
        }
        String eval(String script) throws Exception { return interpreter.eval(script); }
        @Override public void close() { jobs.shutdown(); }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static Path gerber(Path directory) throws Exception {
        Path file = directory.resolve("copper.gbr");
        Files.writeString(file, "%FSLAX24Y24*%\n%MOMM*%\n%ADD10R,10.0X10.0*%\nD10*\nX0Y0D03*\nM02*\n");
        return file;
    }

    @Test void importsAndCamCommandsShareTheGlobalNamespace(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            Path gerber = gerber(directory);
            assertEquals("board", session.host.openGerber(gerber, "board"));
            assertEquals("board_2", session.host.openGerber(gerber, "board"));
            Path drill = directory.resolve("drill.drl");
            Files.writeString(drill, "M48\nMETRIC,TZ\nT01C0.8\n%\nT01\nX001000Y001000\nM30\n");
            assertEquals("board_3", session.host.openExcellon(drill, "board"));
            assertThrows(TclException.class, () -> session.host.cncjob("board", "bad", 0.8, -1.7, 2, 120, 80, 0));
            assertEquals("board_4", session.host.isolate("board", "board", 0.8, 1, 0,
                    org.flatcam.cam.isolation.IsolationType.BOTH));
            assertEquals("board_5", session.host.cutoutRectangular("board", "board", 0.8, 1, 1,
                    org.flatcam.cam.cutout.GapPattern.FOUR));
            assertEquals("board_6", session.host.nccClear("board", "board", java.util.List.of(0.8), 0.1, 1,
                    org.flatcam.cam.ncc.NccMethod.STANDARD, false, false, false,
                    new org.flatcam.cam.ncc.NccBoundary.Itself()));
            assertEquals(6, session.host.objectNames().stream().distinct().count());
        }
    }

    @Test void changedProjectDuringParsingCannotPublishAnImport(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            Path gerber = gerber(directory);
            AtomicBoolean changed = new AtomicBoolean();
            JobContext context = new JobContext() {
                @Override public boolean isCancelled() { return false; }
                @Override public void reportProgress(double fraction, String message) {
                    if (changed.compareAndSet(false, true)) {
                        try { TerminalPanelTest.fx(() -> { field("tclProjectEpoch").setLong(session.host, 1); return null; }); }
                        catch (Exception error) { throw new AssertionError(error); }
                    }
                }
            };
            assertThrows(IllegalStateException.class, () -> TclExecution.run(context,
                    () -> session.host.openGerber(gerber, "late")));
            assertTrue(session.host.objectNames().isEmpty());
        }
    }

    @Test void signedCutDepthProducesNegativeZInActualGcode(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            session.geometry("path");
            session.eval("cncjob path -dia 0.8 -z_cut -1.7 -z_move 2 -feedrate 120");
            String gcode = session.eval("export_gcode path_cnc");
            assertTrue(gcode.contains("Z-1.7"), gcode);
            assertFalse(gcode.contains("Z--"));
            Path file = directory.resolve("job.nc");
            session.host.writeGcode("path_cnc", file, "prefix\n", "\nsuffix");
            assertEquals("prefix\n" + gcode + "\nsuffix", Files.readString(file));
            try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        }
    }

    @Test void invalidDepthsDoNotPublishJobs() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path");
            for (double depth : new double[]{0, 1.7, Double.NaN, Double.NEGATIVE_INFINITY})
                assertThrows(TclException.class, () -> session.host.cncjob("path", "bad", 0.8, depth, 2, 120, 80, 0));
            assertEquals(java.util.List.of("path"), session.host.objectNames());
        }
    }

    @Test void everyPublishedObjectUsesAGloballyUniqueName() throws Exception {
        try (Session session = new Session()) {
            session.geometry("same");
            assertEquals("same_2", session.host.newEmptyGeometry("same"));
            assertEquals("same_3", session.eval("new_geometry same"));
            assertEquals("same_4", session.host.cncjob("same", "same", 0.8, -1.7, 2, 120, 80, 0));
            assertEquals("same_5", session.host.newBoundingBoxGeometry("same", "same", 1, false));
            assertEquals(5, session.host.objectNames().stream().distinct().count());
            assertEquals(TclFlatcamHost.Kind.CNC_JOB, session.host.kindOf("same_4").orElseThrow());
        }
    }

    @Test void existingAmbiguousNamesAreRejectedInsteadOfChoosingTheFirst() throws Exception {
        try (Session session = new Session()) {
            session.geometry("duplicate"); session.geometry("duplicate");
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> session.host.find("duplicate"));
            assertTrue(failure.getMessage().contains("ambiguo"));
        }
    }

    @Test void changedSourceCannotPublishAStaleResult() throws Exception {
        try (Session session = new Session()) {
            TreeItem<String> item = session.geometry("path");
            JobContext context = new JobContext() {
                @Override public boolean isCancelled() { return false; }
                @Override public void reportProgress(double fraction, String message) {
                    try { TerminalPanelTest.fx(() -> { item.setValue("renamed"); return null; }); }
                    catch (Exception error) { throw new AssertionError(error); }
                }
            };
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    TclExecution.run(context, () -> session.host.cncjob("path", "late", 0.8, -1.7, 2, 120, 80, 0)));
            assertTrue(failure.getMessage().contains("alterado ou removido"));
            assertFalse(session.host.objectNames().contains("late"));
        }
    }

    @Test void removedSourceCannotPublishAStaleResult() throws Exception {
        try (Session session = new Session()) {
            TreeItem<String> item = session.geometry("path");
            JobContext context = new JobContext() {
                @Override public boolean isCancelled() { return false; }
                @Override public void reportProgress(double fraction, String message) {
                    try {
                        TerminalPanelTest.fx(() -> {
                            ((Map<?, ?>) field("geometryByItem").get(session.host)).remove(item);
                            return null;
                        });
                    } catch (Exception error) { throw new AssertionError(error); }
                }
            };
            assertThrows(IllegalStateException.class, () -> TclExecution.run(context,
                    () -> session.host.cncjob("path", "late", 0.8, -1.7, 2, 120, 80, 0)));
            assertFalse(session.host.objectNames().contains("late"));
        }
    }

    @Test void cancellationReachesGeneratorsAndTheContextDoesNotLeak() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path");
            AtomicBoolean cancelled = new AtomicBoolean();
            JobContext context = new JobContext() {
                @Override public boolean isCancelled() { return cancelled.get(); }
                @Override public void reportProgress(double fraction, String message) { cancelled.set(true); }
            };
            assertThrows(CancellationException.class, () -> TclExecution.run(context,
                    () -> session.host.cncjob("path", "cancelled", 0.8, -1.7, 2, 120, 80, 0)));
            assertFalse(session.host.objectNames().contains("cancelled"));
            assertEquals("after", session.host.newEmptyGeometry("after"));
        }
    }
}
