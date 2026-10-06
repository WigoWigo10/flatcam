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
        @Override public void close() throws Exception {
            jobs.shutdown();
            TerminalPanelTest.fx(() -> { host.disposeViewport(); return null; });
        }
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

    @Test void rotateUpdatesAllSupportedDataAndPreservesPresentationAndMachining(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("rotation.fcnproj"); ProjectFileIO.save(fixture(directory), file);
        byte[] original = Files.readAllBytes(file);
        try (Session session = new Session()) {
            session.host.openProject(file);
            var copper = (org.flatcam.cam.gerber.GerberImage) version(session, "copper");
            var route = version(session, "route");
            var op = new org.flatcam.cam.transform.TransformOp.Rotate(-90, new Coordinate(0, 0));
            session.eval("rotate copper 90 -origin origin; rotate drills 90 -origin origin; rotate route 90 -origin origin");
            var rotated = (org.flatcam.cam.gerber.GerberImage) version(session, "copper");
            assertTrue(op.apply(copper.solidGeometry()).equalsExact(rotated.solidGeometry(), 1e-10));
            assertTrue(op.apply(copper.followGeometry()).equalsExact(rotated.followGeometry(), 1e-10));
            for (int i = 0; i < copper.shapes().size(); i++) {
                assertTrue(op.apply(copper.shapes().get(i).geometry()).equalsExact(rotated.shapes().get(i).geometry(), 1e-10));
                assertTrue(op.apply(copper.shapes().get(i).followGeometry()).equalsExact(rotated.shapes().get(i).followGeometry(), 1e-10));
            }
            for (var aperture : copper.apertureGeometry().entrySet())
                assertTrue(op.apply(aperture.getValue()).equalsExact(rotated.apertureGeometry().get(aperture.getKey()), 1e-10));
            var drills = (ExcellonImage) version(session, "drills");
            assertEquals(4, drills.drills().getFirst().x(), 1e-10); assertEquals(-3, drills.drills().getFirst().y(), 1e-10);
            var slot = drills.slots().getFirst();
            assertEquals(6, slot.x1(), 1e-10); assertEquals(-5, slot.y1(), 1e-10);
            assertEquals(8, slot.x2(), 1e-10); assertEquals(-7, slot.y2(), 1e-10);
            assertEquals(Map.of(1, 0.8), drills.toolDiameters());
            Object after = version(session, "route");
            Geometry expected = op.apply(value(route, "geometry", Geometry.class));
            assertTrue(expected.equalsExact(value(after, "geometry", Geometry.class), 1e-10));
            var tool = (ToolGeometry) value(after, "tools", List.class).getFirst();
            assertTrue(expected.equalsExact(tool.geometry(), 1e-10)); assertEquals(0.8, tool.toolDiameter());
            assertEquals(value(route, "cncDefaults", GeometryGCodeParameters.class), value(after, "cncDefaults", GeometryGCodeParameters.class));
            assertEquals("copper", value(after, "sourceName", String.class));
            Path saved = directory.resolve("rotated.fcnproj"); session.host.saveProject(saved);
            var snapshot = ProjectFileIO.load(saved);
            assertFalse(snapshot.gerbers().getFirst().visible()); assertTrue(snapshot.gerbers().getFirst().followMode());
            assertFalse(snapshot.geometries().getFirst().visible());
            assertEquals(fixture(directory).cncJobs().getFirst().gcode(), snapshot.cncJobs().getFirst().gcode());
        }
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(Files.exists(directory.resolve("not-written.nc")));
    }

    @Test void rotateBoundsAndExplicitBoxAreResolvedFromCapturedSources() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); session.geometry("box"); session.eval("offset box -x 20");
            session.eval("rotate path 90 -box box");
            var coordinates = session.host.geometryOf("path").orElseThrow().getCoordinates();
            assertEquals(25, coordinates[0].x, 1e-10); assertEquals(25, coordinates[0].y, 1e-10);
            assertEquals(25, coordinates[1].x, 1e-10); assertEquals(15, coordinates[1].y, 1e-10);
        }
    }

    @Test void rotateCannotPublishAfterSourceReferenceProjectChangesOrCancellation() throws Exception {
        for (String changed : List.of("source", "reference", "project", "cancel")) {
            try (Session session = new Session()) {
                TreeItem<String> source = session.geometry("path"), box = session.geometry("box");
                Object before = version(session, "path"); AtomicBoolean cancelled = new AtomicBoolean();
                JobContext context = new JobContext() {
                    public boolean isCancelled() { return cancelled.get(); }
                    public void reportProgress(double fraction, String message) {
                        assertFalse(Platform.isFxApplicationThread());
                        try { TerminalPanelTest.fx(() -> {
                            switch (changed) {
                                case "source" -> source.setValue("renamed");
                                case "reference" -> box.setValue("renamed");
                                case "project" -> field("tclProjectEpoch").setLong(session.host, 1);
                                case "cancel" -> cancelled.set(true);
                            }
                            return null;
                        }); } catch (Exception error) { throw new AssertionError(error); }
                    }
                };
                Class<? extends RuntimeException> expected = changed.equals("cancel") ? CancellationException.class : IllegalStateException.class;
                assertThrows(expected,
                        () -> TclExecution.run(context, () -> session.eval("rotate path 90 -box box")));
                assertSame(before, version(session, changed.equals("source") ? "renamed" : "path"));
            }
        }
    }

    @Test void rotateRefusesCncAndEmptyBoundsAndNoOpDoesNotReplaceVersions() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); Object before = version(session, "path");
            session.eval("rotate path 0; rotate path 360; rotate path -720"); assertSame(before, version(session, "path"));
            session.eval("new_geometry empty; rotate empty 360");
            assertThrows(TclException.class, () -> session.eval("rotate empty 90"));
            session.eval("rotate empty 90 -origin origin");
            session.eval("cncjob path -dia 0.8 -z_cut -1 -z_move 2 -feedrate 120");
            Object cnc = version(session, "path_cnc");
            for (String script : List.of("rotate path_cnc 90", "rotate path_cnc 0", "rotate missing 90", "rotate path 90 -box missing"))
                assertThrows(TclException.class, () -> session.eval(script), script);
            assertSame(cnc, version(session, "path_cnc")); assertSame(before, version(session, "path"));
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
            for (String command : List.of("open_project", "offset", "scale", "mirror", "skew",
                    "save_project", "plot_all", "plot_objects", "set_active", "rotate",
                    "join_geometry", "join_geometries", "join_excellon", "join_excellons")) {
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

    private static String quoted(Path file) { return "{" + file.toString().replace('\\', '/') + "}"; }

    private static void assertNoSaveStages(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().endsWith(".tmp")), "save stages leaked");
        }
    }

    private static TreeItem<String> namedItem(Session session, String name) throws Exception {
        return TerminalPanelTest.fx(() -> {
            Method find = MainWindow.class.getDeclaredMethod("findTclItemByName", String.class); find.setAccessible(true);
            @SuppressWarnings("unchecked") TreeItem<String> item = (TreeItem<String>) find.invoke(session.host, name);
            return item;
        });
    }

    private static boolean visible(Session session, String name) throws Exception {
        TreeItem<String> item = namedItem(session, name);
        return TerminalPanelTest.fx(() -> {
            Method read = MainWindow.class.getDeclaredMethod("isObjectVisible", TreeItem.class); read.setAccessible(true);
            return (boolean) read.invoke(session.host, item);
        });
    }

    private static void installRealTree(Session session) throws Exception {
        TerminalPanelTest.fx(() -> {
            Method build = MainWindow.class.getDeclaredMethod("buildProjectTree"); build.setAccessible(true);
            build.invoke(session.host);
            return null;
        });
    }

    @Test void saveProjectRoundTripsAllKindsInNativeAndPythonFormats(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(fixture(directory), source);
        byte[] original = Files.readAllBytes(source);
        try (Session session = new Session()) {
            session.host.openProject(source);
            for (String extension : List.of("fcnproj", "FlatPrj")) {
                Path output = directory.resolve("saved with spaces." + extension);
                session.eval("save_project " + quoted(output) + "; new_geometry after_save");
                assertTrue(session.host.objectNames().contains("after_save"));
                session.host.openProject(output);
                assertEquals(List.of("copper", "drills", "route", "route_cnc"), session.host.objectNames());
                assertFalse(visible(session, "copper")); assertFalse(visible(session, "route"));
                assertTrue(visible(session, "drills")); assertTrue(visible(session, "route_cnc"));
                var drill = (ExcellonImage) version(session, "drills");
                assertEquals(1, drill.drills().size()); assertEquals(1, drill.slots().size());
                assertEquals(1, value(version(session, "route"), "tools", List.class).size());
                assertEquals(120, value(version(session, "route"), "cncDefaults", GeometryGCodeParameters.class).feedRate());
                assertTrue(session.eval("export_gcode route_cnc").contains("G1 X20 Y40"));
                TerminalPanelTest.fx(() -> {
                    var plot = (PlotAreaView) field("plotAreaView").get(session.host);
                    assertEquals(javafx.scene.paint.Color.web("#44cc22"), plot.layerColors(namedItemOnFx(session, "copper"))[0]);
                    return null;
                });
                assertNoSaveStages(directory);
            }
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        assertFalse(Files.exists(directory.resolve("not-written.nc")), "saving must not overwrite/export CNC source files");
    }

    private static TreeItem<String> namedItemOnFx(Session session, String name) throws Exception {
        Method find = MainWindow.class.getDeclaredMethod("findTclItemByName", String.class); find.setAccessible(true);
        @SuppressWarnings("unchecked") TreeItem<String> item = (TreeItem<String>) find.invoke(session.host, name);
        return item;
    }

    @Test void nativeSavePreservesDrillingDefaultsAndImportWarnings(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(fixture(directory), source);
        try (Session session = new Session()) {
            session.host.openProject(source);
            var parameters = new org.flatcam.cam.gcode.DrillGCodeParameters(3, 1.7, 200, 12000, false,
                    true, 0.5, true, 1.2, 0.1);
            TreeItem<String> drills = namedItem(session, "drills");
            TerminalPanelTest.fx(() -> {
                @SuppressWarnings("unchecked") var defaults = (Map<TreeItem<String>, Map<Integer, org.flatcam.cam.gcode.DrillGCodeParameters>>)
                        field("drillDefaultsByItem").get(session.host);
                defaults.put(drills, Map.of(1, parameters));
                field("currentProjectImportWarnings").set(session.host, List.of("fixture compatibility warning"));
                return null;
            });
            Path output = directory.resolve("settings.fcnproj"); session.host.saveProject(output);
            var loaded = ProjectFileIO.load(output);
            assertEquals(parameters, loaded.excellons().getFirst().drillDefaults().get(1));
            assertEquals(List.of("fixture compatibility warning"), loaded.importWarnings());
        }
    }

    @Test void cancellingBeforeOrAfterSerializationPreservesTheDestination(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            session.geometry("path");
            Path output = directory.resolve("keep.fcnproj"); Files.writeString(output, "original bytes");
            for (String phase : List.of("Serializando projeto...", "Publicando projeto...")) {
                AtomicBoolean cancelled = new AtomicBoolean();
                JobContext context = new JobContext() {
                    public boolean isCancelled() { return cancelled.get(); }
                    public void reportProgress(double fraction, String message) { if (message.equals(phase)) cancelled.set(true); }
                };
                assertThrows(CancellationException.class, () -> TclExecution.run(context, () -> {
                    session.host.saveProject(output); return null;
                }), phase);
                assertEquals("original bytes", Files.readString(output)); assertNoSaveStages(directory);
            }
            session.host.saveProject(output); assertEquals("path", ProjectFileIO.load(output).geometries().getFirst().name());
        }
    }

    @Test void changedProjectDuringSavingDoesNotOverwriteTheDestination(@TempDir Path directory) throws Exception {
        for (String mutation : List.of("rename", "visibility", "settings", "epoch", "editor")) {
            try (Session session = new Session()) {
                TreeItem<String> item = session.geometry("path");
                Path output = directory.resolve("keep-" + mutation + ".fcnproj"); Files.writeString(output, "original bytes");
                AtomicBoolean changed = new AtomicBoolean();
                java.util.concurrent.atomic.AtomicReference<GCodeEditorController> draft = new java.util.concurrent.atomic.AtomicReference<>();
                JobContext context = new JobContext() {
                    public boolean isCancelled() { return false; }
                    public void reportProgress(double fraction, String message) {
                        if (!message.equals("Publicando projeto...") || !changed.compareAndSet(false, true)) return;
                        try {
                            if (mutation.equals("editor")) { draft.set(installDraftEditor(session)); return; }
                            TerminalPanelTest.fx(() -> {
                                switch (mutation) {
                                    case "rename" -> item.setValue("renamed");
                                    case "visibility" -> session.host.plotObjects(List.of("path"), false);
                                    case "epoch" -> field("tclProjectEpoch").setLong(session.host, 1);
                                    case "settings" -> {
                                        @SuppressWarnings("unchecked") var defaults = (Map<TreeItem<String>, Map<Integer, org.flatcam.cam.gcode.DrillGCodeParameters>>)
                                                field("drillDefaultsByItem").get(session.host);
                                        defaults.put(item, Map.of(1, new org.flatcam.cam.gcode.DrillGCodeParameters(2, 1, 100, 0, false)));
                                    }
                                }
                                return null;
                            });
                        } catch (Exception error) { throw new AssertionError(error); }
                    }
                };
                try {
                    assertThrows(IllegalStateException.class, () -> TclExecution.run(context, () -> { session.host.saveProject(output); return null; }), mutation);
                    assertTrue(changed.get()); assertEquals("original bytes", Files.readString(output)); assertNoSaveStages(directory);
                } finally { if (draft.get() != null) TerminalPanelTest.fx(() -> { draft.get().cancel(); return null; }); }
            }
        }
    }

    @Test void saveRejectsOpenEditorsMainJobsAndFxThreadInvocation(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); Path output = directory.resolve("not-saved.fcnproj");
            var editor = installDraftEditor(session);
            try { assertThrows(IllegalStateException.class, () -> session.host.saveProject(output)); }
            finally { TerminalPanelTest.fx(() -> { editor.cancel(); return null; }); }
            var handle = session.jobs.submit(context -> "done", null); handle.completion().get(10, TimeUnit.SECONDS);
            TerminalPanelTest.fx(() -> { field("runningJob").set(session.host, handle); return null; });
            try { assertThrows(IllegalStateException.class, () -> session.host.saveProject(output)); }
            finally { TerminalPanelTest.fx(() -> { field("runningJob").set(session.host, null); return null; }); }
            TerminalPanelTest.fx(() -> { assertThrows(IllegalStateException.class, () -> session.host.saveProject(output)); return null; });
            assertFalse(Files.exists(output)); assertNoSaveStages(directory);
        }
    }

    @Test void unsupportedPythonExportKeepsExistingFileAndNativeSaveStillWorks(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            session.geometry("mm");
            TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addGeometryToProject", String.class, String.class, String.class, Geometry.class, boolean.class);
                add.setAccessible(true); add.invoke(session.host, "inch", "", "IN", FACTORY.createPoint(new Coordinate(1, 2)), true);
                return null;
            });
            Path output = directory.resolve("keep.FlatPrj"); Files.writeString(output, "original bytes");
            assertTrue(assertThrows(java.io.IOException.class, () -> session.host.saveProject(output)).getMessage().contains("unidades mistas"));
            assertEquals("original bytes", Files.readString(output)); assertNoSaveStages(directory);
            Path nativeFile = directory.resolve("mixed.fcnproj"); session.host.saveProject(nativeFile);
            assertEquals(List.of("MM", "IN"), ProjectFileIO.load(nativeFile).geometries().stream().map(ProjectFile.GeometryEntry::units).toList());
        }
    }

    @Test void invalidDestinationsPreserveExistingFilesAndCleanStages(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); Path text = directory.resolve("do-not-change.txt"); Files.writeString(text, "keep");
            assertThrows(TclException.class, () -> session.host.saveProject(text)); assertEquals("keep", Files.readString(text));
            assertThrows(java.io.IOException.class, () -> session.host.saveProject(directory.resolve("missing/out.fcnproj")));
            Path nonempty = Files.createDirectory(directory.resolve("directory.fcnproj"));
            Path marker = nonempty.resolve("keep.txt"); Files.writeString(marker, "keep");
            assertThrows(java.io.IOException.class, () -> session.host.saveProject(nonempty));
            assertEquals("keep", Files.readString(marker)); assertNoSaveStages(directory);
        }
    }

    @Test void savingAnEmptyProjectAndContinuingTheScriptWorks(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            Path output = directory.resolve("empty.fcnproj"); session.eval("save_project " + quoted(output) + "; new_geometry after");
            assertTrue(ProjectFileIO.load(output).geometries().isEmpty()); assertEquals(List.of("after"), session.host.objectNames());
        }
    }

    @Test void saveRunsOutsideFxAndDoesNotBlockFxWhileSerializing(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            session.geometry("path"); Path output = directory.resolve("background.fcnproj");
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            JobContext context = new JobContext() {
                public boolean isCancelled() { return false; }
                public void reportProgress(double fraction, String message) {
                    assertFalse(Platform.isFxApplicationThread());
                    if (!message.equals("Serializando projeto...")) return;
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                }
            };
            var handle = session.jobs.submit(ignored -> TclExecution.run(context, () -> { session.host.saveProject(output); return null; }), null);
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertTrue(TerminalPanelTest.fx(Platform::isFxApplicationThread)); assertFalse(handle.completion().isDone());
            } finally { release.countDown(); }
            handle.completion().get(25, TimeUnit.SECONDS); assertEquals("path", ProjectFileIO.load(output).geometries().getFirst().name());
        }
    }

    @Test void plotCommandsUpdateEveryKindAndValidateTheWholeBatch(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(fixture(directory), source);
        try (Session session = new Session()) {
            session.host.openProject(source); session.eval("plot_all -plot_status False");
            for (String name : session.host.objectNames()) assertFalse(visible(session, name), name);
            session.eval("plot_objects {copper,route_cnc} -plot_status True");
            assertTrue(visible(session, "copper")); assertTrue(visible(session, "route_cnc"));
            assertFalse(visible(session, "drills")); assertFalse(visible(session, "route"));
            assertThrows(TclException.class, () -> session.eval("plot_objects {copper,missing} -plot_status False"));
            assertTrue(visible(session, "copper"));
            Path output = directory.resolve("plot-state.fcnproj"); session.host.saveProject(output); session.host.openProject(output);
            assertTrue(visible(session, "copper")); assertTrue(visible(session, "route_cnc")); assertFalse(visible(session, "drills"));
            session.eval("plot_all"); for (String name : session.host.objectNames()) assertTrue(visible(session, name), name);
        }
    }

    @Test void selectionIsAdditiveExpandsCategoriesAndDoesNotShowHiddenObjects() throws Exception {
        try (Session session = new Session()) {
            installRealTree(session); session.geometry("first"); TreeItem<String> second = session.geometry("second");
            session.eval("plot_all -plot_status False");
            TerminalPanelTest.fx(() -> { second.getParent().setExpanded(false); return null; });
            session.eval("set_active first; set_active second");
            TerminalPanelTest.fx(() -> {
                var tree = (TreeView<?>) field("projectTree").get(session.host);
                assertEquals(2, tree.getSelectionModel().getSelectedItems().size());
                assertSame(second, tree.getSelectionModel().getSelectedItem()); assertTrue(second.getParent().isExpanded());
                return null;
            });
            assertFalse(visible(session, "first")); assertFalse(visible(session, "second"));
            assertThrows(TclException.class, () -> session.eval("set_active missing"));
            assertThrows(TclException.class, () -> session.eval("plot_objects {first,missing}"));
            assertFalse(visible(session, "first"));
            session.geometry("duplicate"); session.geometry("duplicate");
            assertThrows(TclException.class, () -> session.eval("set_active duplicate"));
            assertThrows(TclException.class, () -> session.eval("plot_objects {first,duplicate}"));
            assertFalse(visible(session, "first"));
        }
    }

    @Test void plotCommandsSynchronizeTheVisiblePropertiesCheckbox() throws Exception {
        try (Session session = new Session()) {
            installRealTree(session); session.geometry("path"); session.eval("set_active path");
            session.eval("plot_objects path -plot_status False");
            TerminalPanelTest.fx(() -> {
                var panel = (javafx.scene.layout.StackPane) field("propertiesContainer").get(session.host);
                var content = ((javafx.scene.control.ScrollPane) panel.getChildren().getFirst()).getContent();
                var checkbox = (javafx.scene.control.CheckBox) content.lookup("#object-plot");
                assertNotNull(checkbox); assertFalse(checkbox.isSelected()); return null;
            });
            session.eval("plot_all");
            TerminalPanelTest.fx(() -> {
                var panel = (javafx.scene.layout.StackPane) field("propertiesContainer").get(session.host);
                var content = ((javafx.scene.control.ScrollPane) panel.getChildren().getFirst()).getContent();
                assertTrue(((javafx.scene.control.CheckBox) content.lookup("#object-plot")).isSelected()); return null;
            });
        }
    }

    private static ProjectFile joinFixture(Path directory) throws Exception {
        var base = fixture(directory); var route = base.geometries().getFirst(); var drill = base.excellons().getFirst();
        Geometry second = new org.flatcam.cam.transform.TransformOp.Offset(20, 0).apply(route.geometry());
        var route2 = new ProjectFile.GeometryEntry("route second", route.sourceName(), route.units(), second, true,
                List.of(new ToolGeometry(0.8, second)), null, null, true, route.cncDefaults());
        var drill2 = new ProjectFile.ExcellonEntry("drills second", drill.image(), null, null, true, true, false);
        return new ProjectFile(base.gerbers(), List.of(drill, drill2), List.of(route, route2), base.cncJobs());
    }

    @Test void joinsKeepSourcesAndPublishHiddenObjectsWithCollisionSafeNames(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(joinFixture(directory), source);
        byte[] original = Files.readAllBytes(source);
        try (Session session = new Session()) {
            installRealTree(session); session.host.openProject(source); session.eval("set_active route");
            Object a = version(session, "route"), b = version(session, "route second"), drills = version(session, "drills");
            assertEquals("copper_2", session.eval("join_geometry copper route {route second}"));
            assertEquals("", session.eval("join_excellons {combined holes} drills {drills second}"));
            assertFalse(visible(session, "copper_2")); assertFalse(visible(session, "combined holes"));
            Object merged = version(session, "copper_2");
            assertEquals(2, value(merged, "tools", List.class).size());
            assertEquals(value(a, "cncDefaults", GeometryGCodeParameters.class), value(merged, "cncDefaults", GeometryGCodeParameters.class));
            var holes = (ExcellonImage) version(session, "combined holes");
            assertEquals(2, holes.totalDrills()); assertEquals(2, holes.totalSlots()); assertEquals(1, holes.toolDiameters().size());
            assertSame(a, version(session, "route")); assertSame(b, version(session, "route second"));
            assertSame(drills, version(session, "drills"));
            TerminalPanelTest.fx(() -> {
                var tree = (TreeView<?>) field("projectTree").get(session.host);
                assertEquals("route", tree.getSelectionModel().getSelectedItem().getValue()); return null;
            });
            session.eval("plot_objects {copper_2,combined holes}");
            assertTrue(visible(session, "copper_2")); assertTrue(visible(session, "combined holes"));
        }
        assertArrayEquals(original, Files.readAllBytes(source)); assertFalse(Files.exists(directory.resolve("not-written.nc")));
    }

    @Test void joinedObjectsAndMachiningRoundTripInBothProjectFormats(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(joinFixture(directory), source);
        try (Session session = new Session()) {
            session.host.openProject(source);
            session.eval("join_geometries joined route {route second}; join_excellon holes drills {drills second}");
            for (String extension : List.of(".fcnproj", ".FlatPrj")) {
                Path output = directory.resolve("joined" + extension); session.host.saveProject(output);
                try (Session restored = new Session()) {
                    restored.host.openProject(output);
                    Object joined = version(restored, "joined");
                    assertEquals(2, value(joined, "tools", List.class).size());
                    assertEquals(value(version(session, "joined"), "cncDefaults", GeometryGCodeParameters.class),
                            value(joined, "cncDefaults", GeometryGCodeParameters.class));
                    assertFalse(visible(restored, "joined")); assertFalse(visible(restored, "holes"));
                    assertEquals(2, ((ExcellonImage) version(restored, "holes")).totalDrills());
                    assertEquals(2, ((ExcellonImage) version(restored, "holes")).totalSlots());
                }
            }
        }
    }

    @Test void invalidJoinSourcesTypesUnitsAndConflictsDoNotPublish(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(joinFixture(directory), source);
        try (Session session = new Session()) {
            session.host.openProject(source); session.geometry("single");
            List<String> before = session.host.objectNames();
            for (String script : List.of("join_geometry bad route missing", "join_geometry bad route route",
                    "join_geometry bad route copper", "join_geometry bad route route_cnc", "join_geometry bad route single",
                    "join_excellon bad drills copper", "join_excellon {} drills {drills second}"))
                assertThrows(TclException.class, () -> session.eval(script), script);
            assertEquals(before, session.host.objectNames());
        }
        var base = joinFixture(directory); var second = base.geometries().getLast();
        var inch = new ProjectFile.GeometryEntry(second.name(), "", "IN", second.geometry(), true, second.tools(), null, null, true);
        Path mixed = directory.resolve("mixed.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(base.geometries().getFirst(), inch), List.of()), mixed);
        try (Session session = new Session()) {
            session.host.openProject(mixed);
            assertThrows(TclException.class, () -> session.eval("join_geometry bad route {route second}"));
            assertEquals(2, session.host.objectNames().size());
        }
    }

    @Test void joinedMachiningSettingsAreRemappedPersistedAndUsableForGcode(@TempDir Path directory) throws Exception {
        var portable = org.flatcam.cam.gcode.GCodePreprocessor.FX_PORTABLE;
        var geometrySettings = new org.flatcam.app.project.GeometryCncSettings(portable, null, Map.of());
        var a = new GeometryGCodeParameters(2, 1, false, 1, 120, 10000, false);
        var b = new GeometryGCodeParameters(3, 2, false, 1, 250, 9000, false);
        Geometry first = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});
        Geometry second = FACTORY.createLineString(new Coordinate[]{new Coordinate(20, 0), new Coordinate(30, 0)});
        var firstEntry = new ProjectFile.GeometryEntry("first", "", "MM", first, true, List.of(new ToolGeometry(0.8, first)),
                null, null, false, a, geometrySettings);
        var secondEntry = new ProjectFile.GeometryEntry("second", "", "MM", second, true, List.of(new ToolGeometry(0.8, second)),
                null, null, false, b, geometrySettings);
        var drillParams = new org.flatcam.cam.gcode.DrillGCodeParameters(2, 1.7, 300, 0, false);
        var options = new org.flatcam.cam.gcode.GCodeGenerator.DrillJobOptions(false, 15, 2, null, null, 0, null);
        var order = org.flatcam.app.project.DrillCncSettings.ToolOrder.NO;
        var drill1 = ExcellonImage.of("MM", Map.of(7, 0.8), List.of(new ExcellonImage.Drill(7, 1, 2)), List.of(),
                FACTORY.createPoint(new Coordinate(1, 2)).buffer(0.4));
        var drill2 = ExcellonImage.of("MM", Map.of(42, 0.8), List.of(new ExcellonImage.Drill(42, 3, 4)), List.of(),
                FACTORY.createPoint(new Coordinate(3, 4)).buffer(0.4));
        var exc1 = new ProjectFile.ExcellonEntry("holes1", drill1, null, null, false, true, false, Map.of(7, drillParams),
                new org.flatcam.app.project.DrillCncSettings(portable, options, List.of(7), order));
        var exc2 = new ProjectFile.ExcellonEntry("holes2", drill2, null, null, false, true, false, Map.of(42, drillParams),
                new org.flatcam.app.project.DrillCncSettings(portable, options, List.of(42), order));
        Path source = directory.resolve("settings.fcnproj"), output = directory.resolve("joined.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(exc1, exc2), List.of(firstEntry, secondEntry), List.of()), source);
        try (Session session = new Session()) {
            session.host.openProject(source);
            session.eval("join_geometry geo first second; join_excellon holes holes1 holes2");
            session.host.saveProject(output); session.host.openProject(output);
            Path resaved = directory.resolve("roundtrip.fcnproj"); session.host.saveProject(resaved);
            var saved = ProjectFileIO.load(resaved);
            var geo = saved.geometries().stream().filter(entry -> entry.name().equals("geo")).findFirst().orElseThrow();
            assertEquals(2, geo.tools().size()); assertEquals(b, geo.cncDefaults());
            assertEquals(Map.of(0, a), geo.cncSettings().parametersByTool());
            var job = org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob(geo.units(), geo.tools(), geo.cncDefaults(),
                    geo.cncSettings().vTools(), geo.cncSettings().parametersByTool(), org.flatcam.cam.CancellationToken.none(), portable);
            assertTrue(job.gcode().contains("Z-1"), job.gcode()); assertTrue(job.gcode().contains("Z-2"), job.gcode());
            assertTrue(job.gcode().contains("F120"), job.gcode()); assertTrue(job.gcode().contains("F250"), job.gcode());
            var holes = saved.excellons().stream().filter(entry -> entry.name().equals("holes")).findFirst().orElseThrow();
            assertEquals(Map.of(1, drillParams), holes.drillDefaults()); assertEquals(List.of(1), holes.cncSettings().selectedToolIds());
            var drilling = org.flatcam.cam.gcode.GCodeGenerator.generateDrillCncJob(holes.image(), holes.drillDefaults(),
                    holes.cncSettings().selectedToolIds(), holes.cncSettings().options(), portable);
            assertTrue(drilling.gcode().contains("Z-1.7"), drilling.gcode());
            assertEquals(2, holes.image().totalDrills());
        }
    }

    @Test void joinRejectsAmbiguousNamesWithoutMutatingTheProject() throws Exception {
        try (Session session = new Session()) {
            session.geometry("first"); session.geometry("duplicate"); session.geometry("duplicate");
            assertThrows(TclException.class, () -> session.eval("join_geometry late first duplicate"));
            assertEquals(3, session.host.objectNames().size());
        }
    }

    @Test void joinsCannotPublishAfterEditsSettingsChangesProjectChangesOrCancellation(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"); ProjectFileIO.save(joinFixture(directory), source);
        for (String command : List.of("join_geometry late route {route second}", "join_excellon late drills {drills second}")) {
            for (String change : List.of("rename", "remove", "settings", "project", "cancel")) {
                try (Session session = new Session()) {
                    session.host.openProject(source); AtomicBoolean cancelled = new AtomicBoolean();
                    JobContext context = new JobContext() {
                        public boolean isCancelled() { return cancelled.get(); }
                        public void reportProgress(double fraction, String message) {
                            if (!message.equals("Publicando juncao...")) return;
                            assertFalse(Platform.isFxApplicationThread());
                            try { TerminalPanelTest.fx(() -> {
                                if (change.equals("cancel")) cancelled.set(true);
                                else if (change.equals("project")) field("tclProjectEpoch").setLong(session.host,
                                        field("tclProjectEpoch").getLong(session.host) + 1);
                                else if (change.equals("settings")) {
                                    var settings = (Map<Object, Object>) field("geometryCncSettingsByItem").get(session.host);
                                    var geometries = (Map<?, ?>) field("geometryByItem").get(session.host);
                                    settings.put(geometries.keySet().iterator().next(),
                                            new org.flatcam.app.project.GeometryCncSettings(org.flatcam.cam.gcode.GCodePreprocessor.DEFAULT, null, Map.of()));
                                } else {
                                    String name = command.startsWith("join_geometry") ? "route second" : "drills second";
                                    if (change.equals("remove")) session.host.delete(name);
                                    else {
                                        Method find = MainWindow.class.getDeclaredMethod("findTclItemByName", String.class); find.setAccessible(true);
                                        ((TreeItem<String>) find.invoke(session.host, name)).setValue("renamed");
                                    }
                                }
                                return null;
                            }); } catch (Exception error) { throw new AssertionError(error); }
                        }
                    };
                    Class<? extends RuntimeException> expected = change.equals("cancel") ? CancellationException.class : IllegalStateException.class;
                    assertThrows(expected, () -> TclExecution.run(context, () -> session.eval(command)), command + " / " + change);
                    assertFalse(session.host.objectNames().contains("late"));
                }
            }
        }
    }

    @Test void joinsRequireClosedEditorsAndNeverRunTheirCalculationOnFx() throws Exception {
        try (Session session = new Session()) {
            session.geometry("first"); session.geometry("second"); var editor = installDraftEditor(session);
            assertThrows(IllegalStateException.class, () -> session.eval("join_geometry late first second"));
            TerminalPanelTest.fx(() -> { editor.cancel(); return null; });
            TerminalPanelTest.fx(() -> {
                assertThrows(IllegalStateException.class, () -> session.host.join(TclFlatcamHost.Kind.GEOMETRY, "late", List.of("first", "second")));
                return null;
            });
            assertEquals(List.of("first", "second"), session.host.objectNames());
        }
    }

    @Test void joinWorkerAllowsFxToRemainResponsiveWhileWaitingToCalculate() throws Exception {
        try (Session session = new Session()) {
            session.geometry("first"); session.geometry("second");
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            JobContext context = new JobContext() {
                public boolean isCancelled() { return false; }
                public void reportProgress(double fraction, String message) {
                    assertFalse(Platform.isFxApplicationThread());
                    if (!message.startsWith("Juntando ")) return;
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                }
            };
            var job = session.jobs.submit(ignored -> TclExecution.run(context, () -> session.eval("join_geometry joined first second")), null);
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertTrue(TerminalPanelTest.fx(Platform::isFxApplicationThread)); assertFalse(job.completion().isDone());
            } finally { release.countDown(); }
            job.completion().get(25, TimeUnit.SECONDS); assertFalse(visible(session, "joined"));
        }
    }

    @Test void terminalCanJoinRotatePlotSaveAndContinue(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"), output = directory.resolve("terminal.fcnproj");
        ProjectFileIO.save(joinFixture(directory), source);
        try (Session session = new Session()) {
            installRealTree(session);
            TerminalPanel panel = TerminalPanelTest.fx(() -> {
                var created = new TerminalPanel(session.interpreter, "teste", session.jobs);
                field("terminalPanel").set(session.host, created); new Scene(created); return created;
            });
            var idle = TerminalPanelTest.fx(() -> {
                var input = (TextField) panel.lookup("#terminal-input");
                input.setText("open_project " + quoted(source) + "; join_geometry joined route {route second}; "
                        + "join_excellon holes drills {drills second}; rotate joined 90; plot_objects {joined,holes}; "
                        + "save_project " + quoted(output) + "; get_names");
                input.getOnAction().handle(new ActionEvent()); return panel.whenIdle();
            });
            idle.get(25, TimeUnit.SECONDS);
            String text = TerminalPanelTest.fx(() -> ((TextArea) panel.lookup("#terminal-output")).getText());
            assertFalse(text.contains("ERRO:"), text); assertFalse(text.contains("Cancelado."), text);
            assertTrue(text.contains("joined"), text); assertFalse(TerminalPanelTest.fx(panel::isBusy));
            var saved = ProjectFileIO.load(output);
            assertTrue(saved.geometries().stream().filter(entry -> entry.name().equals("joined")).findFirst().orElseThrow().visible());
            assertTrue(saved.excellons().stream().filter(entry -> entry.name().equals("holes")).findFirst().orElseThrow().visible());
            assertEquals(8, session.host.objectNames().size());
        }
    }

    @Test void terminalCanOpenSelectPlotSaveAndContinueWithoutCancellingItself(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.fcnproj"), output = directory.resolve("terminal.fcnproj");
        ProjectFileIO.save(fixture(directory), source);
        try (Session session = new Session()) {
            installRealTree(session);
            TerminalPanel panel = TerminalPanelTest.fx(() -> {
                var created = new TerminalPanel(session.interpreter, "teste", session.jobs);
                field("terminalPanel").set(session.host, created); new Scene(created); return created;
            });
            var idle = TerminalPanelTest.fx(() -> {
                var input = (TextField) panel.lookup("#terminal-input");
                input.setText("open_project " + quoted(source) + "; plot_all -plot_status False; plot_objects route; "
                        + "set_active route; save_project " + quoted(output) + "; get_names");
                input.getOnAction().handle(new ActionEvent()); return panel.whenIdle();
            });
            idle.get(25, TimeUnit.SECONDS);
            String text = TerminalPanelTest.fx(() -> ((TextArea) panel.lookup("#terminal-output")).getText());
            assertFalse(text.contains("ERRO:"), text); assertFalse(text.contains("Cancelado."), text);
            assertTrue(text.contains("route_cnc"), text); assertFalse(TerminalPanelTest.fx(panel::isBusy));
            ProjectFile saved = ProjectFileIO.load(output);
            assertTrue(saved.geometries().getFirst().visible()); assertFalse(saved.gerbers().getFirst().visible());
            assertFalse(saved.cncJobs().getFirst().visible()); assertNoSaveStages(directory);
        }
    }

    @Test void cncVisibilityCountsOnlyExistingNonemptySublayers(@TempDir Path directory) throws Exception {
        try (Session session = new Session()) {
            Geometry line = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});
            Geometry empty = FACTORY.createGeometryCollection();
            TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addCncJobToProject", String.class, String.class,
                        Path.class, String.class, Geometry.class, Geometry.class); add.setAccessible(true);
                add.invoke(session.host, "cut-only", "", Path.of("cut.nc"), "G21\n", null, line);
                add.invoke(session.host, "travel-only", "", Path.of("travel.nc"), "G21\n", line, empty);
                return null;
            });
            for (String name : List.of("cut-only", "travel-only")) {
                assertTrue(visible(session, name)); session.eval("plot_objects " + name + " -plot_status False");
                assertFalse(visible(session, name));
            }
            Path output = directory.resolve("hidden.fcnproj"); session.host.saveProject(output);
            assertTrue(ProjectFileIO.load(output).cncJobs().stream().noneMatch(ProjectFile.CncJobRecord::visible));
            session.eval("plot_all"); assertTrue(visible(session, "cut-only")); assertTrue(visible(session, "travel-only"));
        }
    }

    @Test void unplottableCncJobIsExplicitlyRejectedWithoutChangingOtherObjects() throws Exception {
        try (Session session = new Session()) {
            session.geometry("path");
            TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addCncJobToProject", String.class, String.class,
                        Path.class, String.class, Geometry.class, Geometry.class); add.setAccessible(true);
                add.invoke(session.host, "no-preview", "", Path.of("unsupported.nc"), "G21\n", null, null);
                return null;
            });
            assertFalse(visible(session, "no-preview"));
            assertThrows(TclException.class, () -> session.eval("plot_objects {path,no-preview} -plot_status False"));
            assertTrue(visible(session, "path"));
            assertThrows(TclException.class, () -> session.eval("plot_all -plot_status False"));
            assertTrue(visible(session, "path"));
        }
    }

    @Test void changingVisibilityOfManyLayersProducesOnlyOneRedraw() throws Exception {
        try (Session session = new Session()) {
            for (int i = 0; i < 25; i++) session.geometry("path" + i);
            var metrics = new PlotAreaPerformance(true, 1000);
            TerminalPanelTest.fx(() -> {
                Field performance = PlotAreaView.class.getDeclaredField("performance"); performance.setAccessible(true);
                performance.set(field("plotAreaView").get(session.host), metrics); return null;
            });
            session.eval("plot_all -plot_status False");
            Field redraws = PlotAreaPerformance.class.getDeclaredField("redraws"); redraws.setAccessible(true);
            assertEquals(1, TerminalPanelTest.fx(() -> redraws.getLong(metrics)));
            session.eval("plot_all"); assertEquals(2, TerminalPanelTest.fx(() -> redraws.getLong(metrics)));
        }
    }

    @Test void plottingBothCncSublayersSynchronizesPlotKindWithoutUndoingTheCommand() throws Exception {
        try (Session session = new Session()) {
            installRealTree(session);
            Geometry line = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});
            TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addCncJobToProject", String.class, String.class,
                        Path.class, String.class, Geometry.class, Geometry.class); add.setAccessible(true);
                add.invoke(session.host, "job", "", Path.of("job.nc"), "G21\n", line, line); return null;
            });
            session.eval("set_active job");
            var picker = TerminalPanelTest.fx(() -> {
                var panel = (javafx.scene.layout.StackPane) field("propertiesContainer").get(session.host);
                var content = ((javafx.scene.control.ScrollPane) panel.getChildren().getFirst()).getContent();
                @SuppressWarnings("unchecked") var combo = (javafx.scene.control.ComboBox<String>) content.lookup("#object-plot-kind");
                assertNotNull(combo); combo.setValue("Cut"); combo.getOnAction().handle(new ActionEvent()); return combo;
            });
            session.eval("plot_objects job");
            TerminalPanelTest.fx(() -> {
                assertEquals("All", picker.getValue());
                Method layer = MainWindow.class.getDeclaredMethod("cncLayerVisible", TreeItem.class, boolean.class);
                layer.setAccessible(true); var item = namedItemOnFx(session, "job");
                assertEquals(true, layer.invoke(session.host, item, true)); assertEquals(true, layer.invoke(session.host, item, false));
                return null;
            });
        }
    }
}
