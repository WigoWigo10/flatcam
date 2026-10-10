package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

/** Opt-in visible Plot benchmark, not an FPS/physical CNC test. No MainApp/preferences or source writes.
 * Production host decodes/restores CNC/LOD; only the viewport is attached to the benchmark window.
 * Geometry editing exercises the real session + publication APIs, not sidebar/table event handling. */
@EnabledOnOs(OS.WINDOWS)
class MainPlotRenderBenchmarkTest {
    private PlotAreaView view;
    private Stage stage;
    private PlotBenchmarkMetrics metrics;
    private PlotBenchmarkCncMetrics cncMetrics;
    private AnimationTimer pulseTimer;
    private final JSONArray phases = new JSONArray();
    private final JSONObject report = new JSONObject();
    private int timeoutSeconds;

    @Test void runVisibleBenchmark() throws Exception {
        assumeTrue(Boolean.getBoolean("flatcam.plot.benchmark"), "Opt-in visible benchmark");
        int steps = Integer.getInteger("flatcam.plot.benchmark.steps", 240);
        int repeats = Integer.getInteger("flatcam.plot.benchmark.repeats", 3);
        timeoutSeconds = Integer.getInteger("flatcam.plot.benchmark.timeoutSeconds", 60);
        assertTrue(steps >= 4 && steps <= 3600 && repeats >= 1 && repeats <= 20 && timeoutSeconds >= 1);
        String fixture = System.getProperty("flatcam.plot.benchmark.fixture", "");
        Path source = fixture.isBlank() ? null : Path.of(fixture).toAbsolutePath().normalize();
        byte[] beforeHash = source == null ? null : hash(source);
        Path output = Path.of(System.getProperty("flatcam.plot.benchmark.output", "target/plot-benchmark"));
        Files.createDirectories(output);
        ThemeOption theme = ThemeOption.valueOf(System.getProperty("flatcam.plot.benchmark.theme", "ICE_DARK"));
        var cncProbeMode = PlotCncProbe.Mode.valueOf(System.getProperty("flatcam.plot.benchmark.cncProbe", "FULL"));
        report.put("schema", 3).put("status", "RUNNING").put("scope", "visible production Plot; scripted camera and geometry session publication")
                .put("cncProbe", cncProbeMode.name())
                .put("visualValidation", cncProbeMode == PlotCncProbe.Mode.FULL ? "REFERENCE_NO_OMISSIONS" : "NOT_VALIDATED_DIAGNOSTIC_OMISSIONS")
                .put("notMeasured", List.of("GPU-presented FPS", "physical input latency", "full editor sidebar/table flow", "CAM generation", "project save"))
                .put("source", source == null ? "synthetic-lines-v1" : source.getFileName().toString())
                .put("sourceSha256", source == null ? JSONObject.NULL : HexFormat.of().formatHex(beforeHash))
                .put("steps", steps).put("repeats", repeats).put("theme", theme.name())
                .put("java", System.getProperty("java.version")).put("javafx", System.getProperty("javafx.runtime.version", "unknown"))
                .put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"))
                .put("revision", System.getProperty("flatcam.plot.benchmark.revision", "unknown"))
                .put("workingTree", System.getProperty("flatcam.plot.benchmark.workingTree", "unknown"))
                .put("densityAsync", System.getProperty("flatcam.plot.density.async", "true"))
                .put("densityEnabled", !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density")))
                .put("pixelBuffer", !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density.pixelBuffer")))
                .put("jfrRequested", Boolean.getBoolean("flatcam.plot.benchmark.jfr"))
                .put("indexAsync", System.getProperty("flatcam.plot.index.async", "true"))
                .put("hardware", SystemHardwareInfo.collect().summary()).put("phases", phases);

        try (var session = new MainCamFlowTest.Session("MM")) {
            session.release.countDown();
            var plotField = MainWindow.class.getDeclaredField("plotAreaView"); plotField.setAccessible(true);
            view = fx(() -> (PlotAreaView) plotField.get(session.window));
            fx(() -> {
                Platform.setImplicitExit(false);
                view.setCncBenchmarkProbe(new PlotCncProbe(cncProbeMode, sample -> {
                    if (cncMetrics != null) cncMetrics.sample(sample);
                }));
                stage = new Stage();
                var scene = new Scene(view, Integer.getInteger("flatcam.plot.benchmark.width", 1280),
                        Integer.getInteger("flatcam.plot.benchmark.height", 800));
                theme.applyTo(scene); view.applyTheme(theme);
                stage.setTitle("FlatCAM FX — Plot benchmark (somente leitura)");
                stage.setScene(scene); stage.show();
                pulseTimer = new AnimationTimer() {
                    @Override public void handle(long now) { if (metrics != null) metrics.pulse(System.nanoTime()); }
                };
                pulseTimer.start();
                return null;
            });
            long loading = System.nanoTime();
            try (var marker = PlotBenchmarkJfr.phase("load-and-initial-ready", 0)) {
                if (source != null) session.window.openProject(source); // Worker-side production decode/preview and FX restore.
                else fx(() -> {
                    view.clearLayers();
                    view.putLayer("synthetic-dense", PlotAreaView.LayerCategory.GEOMETRY, synthetic(),
                            Color.LIMEGREEN, Color.LIMEGREEN, true);
                    return null;
                });
                String object = System.getProperty("flatcam.plot.benchmark.object", "");
                var sourceLayers = fx(this::layers);
                if (!object.isBlank()) {
                    assertTrue(sourceLayers.keySet().stream().anyMatch(key -> object.equals(name(key))), "Object not found: " + object);
                    var keys = List.copyOf(sourceLayers.keySet());
                    fx(() -> {
                        view.beginBatchUpdate();
                        try { for (Object key : keys) view.setLayerVisible(key, object.equals(name(key))); }
                        finally { view.endBatchUpdate(); }
                        return null;
                    });
                }
                fx(() -> { view.fitAllVisible(); return null; });
                awaitReady();
                report.put("loadAndInitialReadyMs", (System.nanoTime() - loading) / 1e6);
                marker.completed();
            }
            var graphics = fx(() -> GraphicsRuntimeInfo.query(stage)).get(10, TimeUnit.SECONDS);
            report.put("graphics", new JSONObject().put("pipeline", graphics.pipeline()).put("mode", graphics.mode())
                    .put("adapter", graphics.adapter()).put("driver", graphics.driver()).put("scope", graphics.scope()));
            report.put("viewport", fx(() -> new JSONObject().put("widthLogical", view.getWidth()).put("heightLogical", view.getHeight())
                    .put("outputScaleX", stage.getOutputScaleX()).put("outputScaleY", stage.getOutputScaleY())));
            var sourceLayers = fx(this::layers);
            JSONArray corpus = new JSONArray();
            for (var entry : sourceLayers.entrySet()) {
                var layer = entry.getValue();
                if (layer.geometry() != null) corpus.put(new JSONObject().put("name", name(entry.getKey()))
                        .put("category", layer.category().name()).put("visible", layer.visible())
                        .put("points", layer.geometry().getNumPoints()).put("parts", layer.geometry().getNumGeometries())
                        .put("strokeOnly", layer.strokeOnly()).put("filled", layer.filled()).put("multicolor", layer.multicolor()));
            }
            report.put("layers", corpus);
            if (cncProbeMode != PlotCncProbe.Mode.FULL)
                assertTrue(sourceLayers.values().stream().anyMatch(layer -> layer.visible()
                        && layer.category() == PlotAreaView.LayerCategory.CNCJOB), "CNC omission probe requires a visible CNC object");
            assertTrue(sourceLayers.values().stream().anyMatch(layer -> layer.visible() && layer.geometry() != null && !layer.geometry().isEmpty()),
                    "No visible geometry; use -ObjectName for a hidden saved object");
            PlotCamera base = fx(view::cameraSnapshot);
            // Unrecorded warm-up. Preparation cost remains reported above, not silently dropped.
            try (var marker = PlotBenchmarkJfr.phase("warm-up", 0)) {
                cameraPhase(base, 30, false); awaitReady(); marker.completed();
            }
            for (int repeat = 1; repeat <= repeats; repeat++) {
                int iteration = repeat;
                phase("pan", iteration, () -> cameraPhase(base, steps, false));
                phase("zoom-in-out", iteration, () -> cameraPhase(base, steps, true));
                fx(() -> { view.setCamera(base); return null; }); awaitReady();
                var visible = fx(view::visibleSelectableLayers);
                phase("object-selection", iteration, () -> {
                    for (int i = 0; i < 8; i++) {
                        var bounds = visible.get(i % visible.size()).geometry().getEnvelopeInternal();
                        issue(() -> view.setSelectedObjectBounds(List.of(bounds))); awaitReady();
                    }
                    issue(() -> view.setSelectedObjectBounds(List.of())); awaitReady();
                    return null;
                });
            }
            var editable = sourceLayers.entrySet().stream().filter(entry -> entry.getValue().visible()
                    && entry.getValue().category() == PlotAreaView.LayerCategory.GEOMETRY
                    && entry.getValue().geometry() != null && !entry.getValue().geometry().isEmpty())
                    .max(Comparator.comparingInt(entry -> entry.getValue().geometry().getNumPoints()));
            if (editable.isPresent()) {
                var entry = editable.get();
                Geometry original = entry.getValue().geometry();
                var edit = new GeometryEditSession(original, List.of()); // Worker-side preparation, excluded from action latency.
                report.put("editObject", name(entry.getKey()));
                for (int repeat = 1; repeat <= repeats; repeat++) {
                    phase("geometry-delete-undo", repeat, () -> {
                        for (int i = 0; i < 4; i++) {
                            issue(() -> {
                                edit.selectIndices(List.of(0)); assertTrue(edit.deleteSelected());
                                view.updateLayerGeometry(entry.getKey(), edit.resultGeometry());
                            }); awaitReady();
                            issue(() -> {
                                assertTrue(edit.undo()); view.updateLayerGeometry(entry.getKey(), edit.resultGeometry());
                            }); awaitReady();
                        }
                        return null;
                    });
                }
                assertSame(original, edit.resultGeometry(), "Undo must restore the borrowed original version");
            } else report.put("editSkipped", "No visible Geometry in this corpus; not counted as tested");
            if (Boolean.getBoolean("flatcam.plot.benchmark.jfr")) {
                PlotBenchmarkJfr.dumpSummary(output);
                report.put("jfrSummary", "jfr-summary.json");
            }
            report.put("status", "COMPLETED");
        } catch (Throwable failure) {
            report.put("status", "FAILED").put("failure", failure.toString());
            throw failure;
        } finally {
            if (view != null) fx(() -> {
                metrics = null;
                cncMetrics = null;
                view.setCncBenchmarkProbe(null);
                if (pulseTimer != null) pulseTimer.stop();
                view.setRenderObserver(null);
                if (stage != null) stage.close();
                return null;
            });
            if (source != null) {
                boolean unchanged = Arrays.equals(beforeHash, hash(source));
                report.put("sourceUnchanged", unchanged);
                if (!unchanged) report.put("status", "FAILED").put("failure", "Source SHA-256 changed");
            }
            Files.writeString(output.resolve("report.json"), report.toString(2), StandardOpenOption.CREATE_NEW);
            System.out.println("[PLOT-BENCHMARK] " + report.getString("status") + " report=" + output.resolve("report.json"));
            if (source != null) assertTrue(report.getBoolean("sourceUnchanged"), "Source SHA-256 changed");
        }
    }

    private void phase(String name, int repeat, Callable<Void> action) throws Exception {
        JSONObject memoryBefore = memory();
        JSONObject rasterBefore = fx(this::rasterStats);
        fx(() -> {
            metrics = new PlotBenchmarkMetrics(); cncMetrics = new PlotBenchmarkCncMetrics();
            view.setRenderObserver(sample -> metrics.redraw(sample, System.nanoTime())); return null;
        });
        long start = System.nanoTime();
        try (var marker = PlotBenchmarkJfr.phase(name, repeat)) {
            action.call(); awaitReady(); marker.completed();
        }
        var result = fx(() -> {
            view.setRenderObserver(null);
            JSONObject recorded = metrics.finish().put("cncPasses", cncMetrics.finish());
            metrics = null; cncMetrics = null; return recorded;
        });
        result.put("name", name).put("repeat", repeat).put("elapsedMs", (System.nanoTime() - start) / 1e6)
                .put("memoryBefore", memoryBefore).put("memoryAfter", memory())
                .put("rasterBefore", rasterBefore).put("rasterAfter", fx(this::rasterStats));
        assertTrue(result.getJSONObject("redrawCommandsMs").getInt("samples") > 0, "No redraw samples");
        phases.put(result);
        System.out.printf(Locale.ROOT, "[PLOT-BENCHMARK] %s repeat=%d command_p95=%.2fms queue_p95=%.2fms ready_samples=%d%n",
                name, repeat, result.getJSONObject("redrawCommandsMs").getDouble("p95"),
                result.getJSONObject("uiQueueMs").getDouble("p95"), result.getJSONObject("requestToReadyCommandsMs").getInt("samples"));
    }

    private Void cameraPhase(PlotCamera base, int steps, boolean zoom) throws Exception {
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / (steps - 1.0);
            double factor = zoom ? Math.pow(8, (1 - Math.cos(angle)) / 2) : 1;
            double dx = zoom ? 0 : Math.sin(angle) * base.width() / base.scale() * .2;
            double dy = zoom ? 0 : (1 - Math.cos(angle)) * base.height() / base.scale() * .1;
            var camera = new PlotCamera(base.centerX() + dx, base.centerY() + dy, base.scale() * factor,
                    base.width(), base.height(), base.insetX(), base.insetY());
            issue(() -> view.setCamera(camera));
            Thread.sleep(16); // Worker only; at most one outstanding FX input, no growing queue.
        }
        return null;
    }

    private void issue(Runnable action) throws Exception {
        long issued = System.nanoTime();
        fx(() -> {
            assertTrue(stage.isShowing() && !stage.isIconified(), "Benchmark window closed/minimized");
            if (metrics != null) metrics.request(issued, System.nanoTime());
            action.run(); return null;
        });
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            boolean ready = fx(() -> {
                assertTrue(stage.isShowing() && !stage.isIconified(), "Benchmark window closed/minimized");
                assertFalse(view.renderFailed(), "Renderer preparation failed");
                return view.renderReady();
            });
            if (ready) return;
            if (System.nanoTime() > deadline) fail("Plot did not settle within " + timeoutSeconds + "s");
            Thread.sleep(16);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, PlotAreaView.RenderLayer> layers() throws Exception {
        var field = PlotAreaView.class.getDeclaredField("layers"); field.setAccessible(true);
        return new LinkedHashMap<>((Map<Object, PlotAreaView.RenderLayer>) field.get(view));
    }

    private static String name(Object key) {
        if (key instanceof TreeItem<?> item) return String.valueOf(item.getValue());
        try {
            var method = key.getClass().getDeclaredMethod("cncJobItem"); method.setAccessible(true);
            return String.valueOf(((TreeItem<?>) method.invoke(key)).getValue());
        } catch (ReflectiveOperationException ignored) { return String.valueOf(key); }
    }

    private static JSONObject memory() {
        var runtime = Runtime.getRuntime();
        long count = 0, time = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, gc.getCollectionCount()); time += Math.max(0, gc.getCollectionTime());
        }
        return new JSONObject().put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory())
                .put("heapCommittedBytes", runtime.totalMemory()).put("heapMaxBytes", runtime.maxMemory())
                .put("gcCountCumulative", count).put("gcMillisCumulative", time);
    }

    private JSONObject rasterStats() {
        var stats = view.rasterStats();
        return new JSONObject().put("latestFrames", stats.latestFrames()).put("overviewFrames", stats.overviewFrames())
                .put("denseBindings", stats.denseBindings()).put("queuedRequests", stats.queuedRequests())
                .put("estimatedImageBytes", stats.estimatedImageBytes()).put("syncScratchBytes", stats.syncScratchBytes())
                .put("workerScratchBytes", stats.workerScratchBytes()).put("publicationsCumulative", stats.publications())
                .put("imagePrepareMsCumulative", stats.imagePrepareNanos() / 1e6)
                .put("presentationCommandsMsCumulative", stats.presentationCommandsNanos() / 1e6)
                .put("timingsEnabled", stats.timingsEnabled());
    }

    private static Geometry synthetic() {
        var factory = new GeometryFactory(); Geometry[] lines = new Geometry[20000];
        for (int i = 0; i < lines.length; i++) {
            double y = i * .0075;
            lines[i] = factory.createLineString(new Coordinate[]{new Coordinate(0, y), new Coordinate(200, y), new Coordinate(200, y + .003)});
        }
        return factory.createGeometryCollection(lines);
    }

    private static byte[] hash(Path source) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(source)) {
            byte[] buffer = new byte[65536]; int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    private static <T> T fx(Callable<T> action) throws Exception { return TerminalPanelTest.fx(action); }
}
