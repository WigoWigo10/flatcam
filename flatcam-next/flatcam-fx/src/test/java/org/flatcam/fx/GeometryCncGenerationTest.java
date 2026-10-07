package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.flatcam.app.job.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class GeometryCncGenerationTest {
    @TempDir Path directory;
    private static List<ToolGeometry> tools(double scale) {
        return List.of(new ToolGeometry(.5 * scale, new GeometryFactory().createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10 * scale, 0)}), ToolProfile.C2));
    }
    private static GeometryGCodeParameters parameters(double scale) {
        return new GeometryGCodeParameters(2 * scale, .2 * scale, true, .1 * scale, 100 * scale, 10000, false);
    }
    private static JobContext context(java.util.function.Consumer<String> phase) {
        return new JobContext() {
            public boolean isCancelled() { return false; }
            public void reportProgress(double fraction, String text) { phase.accept(text); }
        };
    }
    @ParameterizedTest @ValueSource(strings = {"MM", "IN"})
    void preparesPreviewAndThenReplacesCompleteFile(String units) throws Exception {
        double scale = units.equals("MM") ? 1 : 1 / 25.4;
        Path output = directory.resolve("job.nc"); Files.writeString(output, "original");
        var phases = new ArrayList<String>();
        var result = GeometryCncGeneration.generate(units, tools(scale), parameters(scale), Map.of(), Map.of(),
                GCodePreprocessor.FX_PORTABLE, output, context(phases::add), () -> {
                    try { assertEquals("original", Files.readString(output)); } catch (Exception failure) { throw new AssertionError(failure); }
                });
        assertEquals(result.job().gcode(), Files.readString(output)); assertNotNull(result.preview());
        assertEquals(units, result.preview().units()); assertTrue(result.preview().plotAvailable());
        assertTrue(phases.indexOf("Preparando previa de Geometry...") < phases.indexOf("Publicando G-code de Geometry..."));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @ParameterizedTest @ValueSource(strings = {"Gerando caminhos", "Preparando previa", "Gravando G-code", "Publicando G-code"})
    void cancelAtEachPhasePreservesDestination(String phase) throws Exception {
        Path output = directory.resolve("job.nc"); Files.writeString(output, "original");
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        JobContext context = new JobContext() {
            public boolean isCancelled() { return cancelled.get(); }
            public void reportProgress(double fraction, String text) { if (text.startsWith(phase)) cancelled.set(true); }
        };
        assertThrows(java.util.concurrent.CancellationException.class, () -> GeometryCncGeneration.generate("MM", tools(1),
                parameters(1), Map.of(), Map.of(), GCodePreprocessor.FX_PORTABLE, output, context, () -> {}));
        assertEquals("original", Files.readString(output));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void validationFailurePreservesExistingFileAndCleansStage() throws Exception {
        Path output = directory.resolve("job.nc"); Files.writeString(output, "original");
        assertThrows(IllegalStateException.class, () -> GeometryCncGeneration.generate("MM", tools(1), parameters(1), Map.of(),
                Map.of(), GCodePreprocessor.FX_PORTABLE, output, context(p -> {}), () -> { throw new IllegalStateException("stale"); }));
        assertEquals("original", Files.readString(output));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @ParameterizedTest @ValueSource(strings = {"rename", "remove", "project", "settings"})
    void realUiRevalidatesSourceBeforePublishing(String change) throws Exception {
        try (var session = new MainCamFlowTest.Session("MM")) {
            Path output = directory.resolve("job.nc"); Files.writeString(output, "original");
            var handle = TerminalPanelTest.fx(() -> {
                Object entry = session.geometries().get(session.reference);
                var method = MainWindow.class.getDeclaredMethod("startGeometryCncGeneration", javafx.scene.control.TreeItem.class,
                        entry.getClass(), GeometryCncToolPanel.Result.class, Path.class);
                method.setAccessible(true);
                method.invoke(session.window, session.reference, entry, new GeometryCncToolPanel.Result(tools(1), parameters(1),
                        Map.of(), GCodePreprocessor.FX_PORTABLE, Map.of()), output);
                var running = MainWindow.class.getDeclaredField("runningJob"); running.setAccessible(true);
                var job = (JobHandle<?>) running.get(session.window);
                if (change.equals("rename")) session.reference.setValue("renamed");
                else if (change.equals("remove")) session.geometries().remove(session.reference);
                else {
                    var changed = MainWindow.class.getDeclaredField(change.equals("project") ? "tclProjectEpoch" : "geometryCncSettingsByItem");
                    changed.setAccessible(true);
                    if (change.equals("project")) changed.setLong(session.window, 999);
                    else ((Map) changed.get(session.window)).put(session.reference, new org.flatcam.app.project.GeometryCncSettings(
                            GCodePreprocessor.FX_PORTABLE, .5, Map.of()));
                }
                return job;
            });
            session.release.countDown();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> handle.completion().get(10, java.util.concurrent.TimeUnit.SECONDS));
            session.awaitUi(); assertEquals("original", Files.readString(output));
            TerminalPanelTest.fx(() -> {
                var cnc = MainWindow.class.getDeclaredField("cncJobByItem"); cnc.setAccessible(true);
                assertTrue(((Map<?, ?>) cnc.get(session.window)).isEmpty()); return null;
            });
        }
    }
}
