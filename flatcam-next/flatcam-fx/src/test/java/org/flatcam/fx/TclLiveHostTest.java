package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.app.project.PythonProjectWriter;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
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

    private static ProjectFile fixture(Path directory) throws Exception {
        var copper = new GerberParser().parse(gerber(directory), org.flatcam.cam.CancellationToken.none(), ignored -> {});
        var hole = FACTORY.createPoint(new Coordinate(3, 4)).buffer(0.4);
        var drill = ExcellonImage.of("MM", Map.of(1, 0.8), List.of(new ExcellonImage.Drill(1, 3, 4)),
                List.of(new ExcellonImage.Slot(1, 5, 6, 7, 8)), hole);
        var path = FACTORY.createLineString(new Coordinate[]{new Coordinate(10, 20), new Coordinate(20, 40)});
        return new ProjectFile(List.of(new ProjectFile.GerberEntry("copper", copper, "#44cc22", "#123456", false, true, false, true)),
                List.of(new ProjectFile.ExcellonEntry("drills", drill, "#cc2211", "#882200", true, false, true)),
                List.of(new ProjectFile.GeometryEntry("route", "copper", "MM", path, true,
                        List.of(new ToolGeometry(0.8, path)), "#2299dd", "#112233", false,
                        new GeometryGCodeParameters(2, 1, false, 1, 120, 10000, false))),
                List.of(new ProjectFile.CncJobRecord("route_cnc", "route", directory.resolve("not-written.nc").toString(),
                        "G21\nG90\nG0 X10 Y20 Z2\nG1 Z-1 F80\nG1 X20 Y40 F120\nG0 Z2\n", true)));
    }

    private static <T> T value(Object entry, String accessor, Class<T> type) throws Exception {
        Method method = entry.getClass().getDeclaredMethod(accessor); method.setAccessible(true);
        return type.cast(method.invoke(entry));
    }

    private static Object version(Session session, String name) throws Exception {
        return TerminalPanelTest.fx(() -> {
            Method method = MainWindow.class.getDeclaredMethod("findTclItemByName", String.class); method.setAccessible(true);
            var item = (TreeItem<?>) method.invoke(session.host, name);
            Method version = MainWindow.class.getDeclaredMethod("tclVersion", TreeItem.class); version.setAccessible(true);
            return version.invoke(session.host, item);
        });
    }

    @Test void nativeProjectRestoresAllKindsAndPresentation(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("complete project.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        byte[] before = Files.readAllBytes(file);
        try (Session session = new Session()) {
            session.geometry("old"); session.eval("open_project {" + file.toString().replace('\\', '/') + "}");
            assertEquals(List.of("copper", "drills", "route", "route_cnc"), session.host.objectNames());
            assertTrue(session.eval("export_gcode route_cnc").contains("G1 X20 Y40"));
            assertEquals(1, value(version(session, "route"), "tools", List.class).size());
            TerminalPanelTest.fx(() -> {
                var plot = (PlotAreaView) field("plotAreaView").get(session.host);
                var gerbers = (Map<?, ?>) field("gerberByItem").get(session.host);
                var item = gerbers.keySet().iterator().next();
                assertFalse(plot.isLayerVisible(item));
                assertEquals(javafx.scene.paint.Color.web("#44cc22"), plot.layerColors(item)[0]);
                assertTrue(((java.util.Set<?>) field("gerberFollowItems").get(session.host)).contains(item));
                var drills = (Map<?, ?>) field("excellonByItem").get(session.host);
                assertTrue(plot.isLayerMulticolor(drills.keySet().iterator().next()));
                return null;
            });
        }
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void pythonProjectOpensThroughActualTerminalWithoutCancellingItsOwnScript(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("legacy project.FlatPrj"); PythonProjectWriter.save(fixture(directory), file);
        byte[] before = Files.readAllBytes(file);
        try (Session session = new Session()) {
            TerminalPanel panel = TerminalPanelTest.fx(() -> {
                var created = new TerminalPanel(session.interpreter, "teste", session.jobs);
                field("terminalPanel").set(session.host, created);
                new Scene(created); created.applyCss(); created.layout(); return created;
            });
            var idle = TerminalPanelTest.fx(() -> {
                var input = (TextField) panel.lookup("#terminal-input");
                input.setText("open_project {" + file.toString().replace('\\', '/') + "}; offset route -x 2; get_names");
                input.getOnAction().handle(new ActionEvent()); return panel.whenIdle();
            });
            idle.get(25, TimeUnit.SECONDS);
            String output = TerminalPanelTest.fx(() -> ((TextArea) panel.lookup("#terminal-output")).getText());
            assertFalse(output.contains("ERRO:"), output); assertFalse(output.contains("Cancelado."), output);
            assertTrue(output.contains("route_cnc"), output);
            assertEquals(12, session.host.geometryOf("route").orElseThrow().getEnvelopeInternal().getMinX());
            assertFalse(TerminalPanelTest.fx(panel::isBusy));
        }
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void invalidProjectAndInvalidStoredColorPreserveCurrentProject(@TempDir Path directory) throws Exception {
        Path bad = directory.resolve("bad.fcnproj"); Files.writeString(bad, "not json");
        ProjectFile good = fixture(directory); var entry = good.geometries().getFirst();
        Path colour = directory.resolve("colour.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(new ProjectFile.GeometryEntry(
                entry.name(), entry.sourceName(), entry.units(), entry.geometry(), true, List.of(),
                "invalid-colour", "#112233", true)), List.of()), colour);
        try (Session session = new Session()) {
            session.geometry("old"); Object previous = version(session, "old");
            for (Path file : List.of(bad, colour, directory.resolve("missing.fcnproj"))) {
                assertThrows(Exception.class, () -> session.host.openProject(file));
                assertEquals(List.of("old"), session.host.objectNames()); assertSame(previous, version(session, "old"));
            }
        }
    }

    @Test void cancellationAfterPreparationStillPreservesProject(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        try (Session session = new Session()) {
            session.geometry("old");
            for (double cancelAt : new double[]{0.5, 1}) {
                AtomicBoolean cancelled = new AtomicBoolean();
                JobContext context = new JobContext() {
                    public boolean isCancelled() { return cancelled.get(); }
                    public void reportProgress(double fraction, String message) { if (fraction >= cancelAt) cancelled.set(true); }
                };
                assertThrows(CancellationException.class, () -> TclExecution.run(context, () -> { session.host.openProject(file); return null; }));
                assertEquals(List.of("old"), session.host.objectNames());
            }
        }
    }

    @Test void changedProjectOrEditedObjectsCannotBeReplacedByLateLoad(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        for (String mutation : List.of("epoch", "rename", "add", "remove", "version", "appearance")) {
            try (Session session = new Session()) {
                TreeItem<String> item = session.geometry("old"); AtomicBoolean changed = new AtomicBoolean();
                JobContext context = new JobContext() {
                    public boolean isCancelled() { return false; }
                    public void reportProgress(double fraction, String message) {
                        if (fraction != 1 || !changed.compareAndSet(false, true)) return;
                        try {
                            switch (mutation) {
                                case "epoch" -> TerminalPanelTest.fx(() -> { field("tclProjectEpoch").setLong(session.host, 99); return null; });
                                case "rename" -> TerminalPanelTest.fx(() -> { item.setValue("renamed"); return null; });
                                case "add" -> session.geometry("new");
                                case "remove" -> session.host.delete("old");
                                case "version" -> session.host.transform("old", new TclTransformRequest(TclTransformRequest.Operation.OFFSET,
                                        1, 0, TclTransformRequest.Reference.ORIGIN, 0, 0, null));
                                case "appearance" -> TerminalPanelTest.fx(() -> {
                                    ((PlotAreaView) field("plotAreaView").get(session.host)).setLayerColors(item,
                                            javafx.scene.paint.Color.BLUE, javafx.scene.paint.Color.WHITE); return null;
                                });
                            }
                        } catch (Exception error) { throw new AssertionError(error); }
                    }
                };
                assertThrows(IllegalStateException.class, () -> TclExecution.run(context, () -> { session.host.openProject(file); return null; }), mutation);
                assertFalse(session.host.objectNames().contains("copper")); assertTrue(changed.get());
            }
        }
    }

    @Test void transformUpdatesGerberShapesFollowAndExcellonDrillsSlots(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        try (Session session = new Session()) {
            session.host.openProject(file);
            var before = (org.flatcam.cam.gerber.GerberImage) version(session, "copper");
            session.eval("offset copper -x 2 -y -3");
            var after = (org.flatcam.cam.gerber.GerberImage) version(session, "copper");
            var offset = new org.flatcam.cam.transform.TransformOp.Offset(2, -3);
            assertTrue(offset.apply(before.solidGeometry()).equalsExact(after.solidGeometry()));
            assertTrue(offset.apply(before.followGeometry()).equalsExact(after.followGeometry()));
            assertTrue(offset.apply(before.shapes().getFirst().geometry()).equalsExact(after.shapes().getFirst().geometry()));
            session.eval("mirror drills -axis X -origin 0,0");
            var drill = (ExcellonImage) version(session, "drills");
            assertEquals(new ExcellonImage.Drill(1, 3, -4), drill.drills().getFirst());
            assertEquals(new ExcellonImage.Slot(1, 5, -6, 7, -8), drill.slots().getFirst());
            assertEquals(Map.of(1, 0.8), drill.toolDiameters());
        }
    }

    @Test void transformUpdatesToolPathsAndKeepsMetadataAndColors(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        try (Session session = new Session()) {
            session.host.openProject(file); Object before = version(session, "route");
            session.eval("scale route 2; skew route -x 45; offset route -x 3 -y -2");
            Object after = version(session, "route");
            Geometry main = value(after, "geometry", Geometry.class);
            var tools = value(after, "tools", List.class);
            assertTrue(main.equalsExact(((ToolGeometry) tools.getFirst()).geometry()));
            assertEquals(0.8, ((ToolGeometry) tools.getFirst()).toolDiameter());
            assertEquals(value(before, "cncDefaults", GeometryGCodeParameters.class), value(after, "cncDefaults", GeometryGCodeParameters.class));
            assertEquals("copper", value(after, "sourceName", String.class));
            TerminalPanelTest.fx(() -> {
                var plot = (PlotAreaView) field("plotAreaView").get(session.host);
                var objects = (Map<?, ?>) field("geometryByItem").get(session.host);
                var item = objects.keySet().iterator().next(); assertFalse(plot.isLayerVisible(item));
                assertEquals(javafx.scene.paint.Color.web("#2299dd"), plot.layerColors(item)[0]); return null;
            });
            session.eval("mirror route -axis Y -box copper");
            assertEquals(List.of("copper", "drills", "route", "route_cnc"), session.host.objectNames());
        }
    }

    @Test void transformRejectsCncJobsMissingObjectsAndCoordinateOverflow() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); session.eval("cncjob path -dia 0.8 -z_cut -1 -z_move 2 -feedrate 120");
            String cnc = session.eval("export_gcode path_cnc"); Object before = version(session, "path");
            for (String script : List.of("offset path_cnc -x 2", "offset missing -x 1", "mirror path -box missing", "scale path 1e308 -origin origin"))
                assertThrows(TclException.class, () -> session.eval(script), script);
            assertSame(before, version(session, "path")); assertEquals(cnc, session.eval("export_gcode path_cnc"));
        }
    }

    @Test void transformationCannotPublishAfterSourceOrReferenceChanges() throws Exception {
        for (String changed : List.of("source", "reference", "project", "cancel")) {
            try (Session session = new Session()) {
                TreeItem<String> source = session.geometry("path"), box = session.geometry("box");
                Object before = version(session, "path"); AtomicBoolean cancelled = new AtomicBoolean();
                JobContext context = new JobContext() {
                    public boolean isCancelled() { return cancelled.get(); }
                    public void reportProgress(double fraction, String message) {
                        try {
                            TerminalPanelTest.fx(() -> {
                                switch (changed) {
                                    case "source" -> source.setValue("renamed");
                                    case "reference" -> box.setValue("renamed");
                                    case "project" -> field("tclProjectEpoch").setLong(session.host, 1);
                                    case "cancel" -> cancelled.set(true);
                                }
                                return null;
                            });
                        } catch (Exception error) { throw new AssertionError(error); }
                    }
                };
                Class<? extends RuntimeException> expected = changed.equals("cancel") ? CancellationException.class : IllegalStateException.class;
                assertThrows(expected,
                        () -> TclExecution.run(context, () -> session.eval("mirror path -box box")));
                assertSame(before, version(session, changed.equals("source") ? "renamed" : "path"));
            }
        }
    }

    @Test void projectDecodeAndTransformRunOutsideFxWhileFxRemainsResponsive(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        try (Session session = new Session()) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicBoolean first = new AtomicBoolean();
            JobContext context = new JobContext() {
                public boolean isCancelled() { return false; }
                public void reportProgress(double fraction, String message) {
                    assertFalse(Platform.isFxApplicationThread());
                    if (!first.compareAndSet(false, true)) return;
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                }
            };
            var job = session.jobs.submit(ignored -> TclExecution.run(context, () -> {
                session.host.openProject(file); return session.eval("offset route -x 2");
            }), null);
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertTrue(TerminalPanelTest.fx(Platform::isFxApplicationThread));
                assertFalse(job.completion().isDone());
            } finally { release.countDown(); }
            job.completion().get(25, TimeUnit.SECONDS);
            assertEquals(12, session.host.geometryOf("route").orElseThrow().getEnvelopeInternal().getMinX());
            TerminalPanelTest.fx(() -> {
                assertThrows(IllegalStateException.class, () -> session.host.openProject(file));
                assertThrows(IllegalStateException.class, () -> session.host.transform("route", new TclTransformRequest(
                        TclTransformRequest.Operation.OFFSET, 1, 0, TclTransformRequest.Reference.ORIGIN, 0, 0, null)));
                return null;
            });
        }
    }

    @Test void noOpTransformsDoNotReplaceEntries() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); Object before = version(session, "path");
            session.eval("offset path; scale path 1; skew path");
            assertSame(before, version(session, "path"));
        }
    }

    @Test void newCommandsHaveUsefulHelpInTheTerminal() throws Exception {
        try (Session session = new Session()) {
            TerminalPanel panel = TerminalPanelTest.fx(() -> new TerminalPanel(session.interpreter, "teste", session.jobs));
            for (String command : List.of("open_project", "offset", "scale", "mirror", "skew")) {
                String help = session.eval("help " + command);
                assertTrue(help.startsWith(command), help);
                assertFalse(help.contains("sem texto"), help);
            }
            assertFalse(TerminalPanelTest.fx(panel::isBusy));
        }
    }

    private static GCodeEditorController installDraftEditor(Session session) throws Exception {
        return TerminalPanelTest.fx(() -> {
            var controller = new GCodeEditorController(new javafx.scene.control.TabPane(), new GCodeEditorController.Host() {
                public void openToolPanel(String name, javafx.scene.Node content) { }
                public void closeToolPanel() { }
                public boolean apply(TreeItem<String> item, String text, Runnable success, java.util.function.Consumer<String> failure) { return false; }
                public boolean saveAs(String text, java.util.function.Consumer<String> done) { return false; }
                public void log(String message) { }
            });
            field("gcodeEditor").set(session.host, controller);
            controller.start(new TreeItem<>("draft"), "G21\n");
            Field editorField = GCodeEditorController.class.getDeclaredField("editor"); editorField.setAccessible(true);
            ((CodeEditor) editorField.get(controller)).area().appendText("G90\n");
            assertTrue(controller.hasUnappliedChanges()); return controller;
        });
    }

    @Test void unappliedDraftBlocksProjectReplacementBeforeAndAfterLoading(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        try (Session session = new Session()) {
            session.geometry("old"); var editor = installDraftEditor(session);
            try { assertThrows(IllegalStateException.class, () -> session.host.openProject(file)); }
            finally { TerminalPanelTest.fx(() -> { editor.cancel(); return null; }); }
            AtomicBoolean changed = new AtomicBoolean();
            java.util.concurrent.atomic.AtomicReference<GCodeEditorController> draft = new java.util.concurrent.atomic.AtomicReference<>();
            JobContext context = new JobContext() {
                public boolean isCancelled() { return false; }
                public void reportProgress(double fraction, String message) {
                    if (fraction == 1 && changed.compareAndSet(false, true)) {
                        try { draft.set(installDraftEditor(session)); }
                        catch (Exception error) { throw new AssertionError(error); }
                    }
                }
            };
            try {
                assertThrows(IllegalStateException.class, () -> TclExecution.run(context, () -> { session.host.openProject(file); return null; }));
                assertEquals(List.of("old"), session.host.objectNames());
            } finally { if (draft.get() != null) TerminalPanelTest.fx(() -> { draft.get().cancel(); return null; }); }
        }
    }

    @Test void anotherMainJobBlocksProjectOpeningAndTransforms(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("board.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        try (Session session = new Session()) {
            session.geometry("path"); Object before = version(session, "path");
            var handle = session.jobs.submit(context -> "done", null); handle.completion().get(10, TimeUnit.SECONDS);
            TerminalPanelTest.fx(() -> { field("runningJob").set(session.host, handle); return null; });
            assertThrows(IllegalStateException.class, () -> session.host.openProject(file));
            assertThrows(IllegalStateException.class, () -> session.eval("offset path -x 2"));
            assertSame(before, version(session, "path"));
            TerminalPanelTest.fx(() -> { field("runningJob").set(session.host, null); return null; });
        }
    }
}
