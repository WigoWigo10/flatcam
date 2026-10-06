package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class DrillCncGenerationTest {
    @TempDir Path directory;
    private final GeometryFactory factory = new GeometryFactory();
    private ExcellonImage image(double x) {
        return ExcellonImage.of("MM", Map.of(1, 1.0), List.of(new ExcellonImage.Drill(1, x, 0)), List.of(), factory.createPolygon());
    }
    private GCodeGenerator.DrillJobOptions options() {
        var area = CncExclusionArea.of(factory.toGeometry(new Envelope(4, 6, -1, 1)), CncExclusionArea.Strategy.AROUND, 0);
        return new GCodeGenerator.DrillJobOptions(false, 15, .5, 0.0, 0.0).withExclusions(true, List.of(area));
    }
    private JobContext context(AtomicBoolean cancelled, java.util.function.BiConsumer<Double, String> progress) {
        return new JobContext() {
            public boolean isCancelled() { return cancelled.get(); }
            public void reportProgress(double fraction, String message) { progress.accept(fraction, message); }
        };
    }
    private DrillCncGeneration.Generated generate(ExcellonImage image, Path output, JobContext context, Runnable validate) throws IOException {
        return DrillCncGeneration.generate(image, Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, false)),
                List.of(1), options(), GCodePreprocessor.FX_PORTABLE, output, context, validate);
    }
    private void noTemporaries() throws IOException {
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".flatcam-drilling-")));
        }
    }

    @Test void workerGeneratesPreviewAndPublishesWholeFileWhileFxRemainsResponsive() throws Exception {
        var jobs = new JobExecutor(1);
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Path output = directory.resolve("drill.nc");
        Files.writeString(output, "original");
        try {
            var handle = jobs.submit(context -> generate(image(10), output, context, () -> {
                reached.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException cancelled) { throw new CancellationException(); }
            }), null);
            assertTrue(reached.await(10, TimeUnit.SECONDS));
            assertTrue(TerminalPanelTest.fx(() -> javafx.application.Platform.isFxApplicationThread()));
            assertEquals("original", Files.readString(output), "destination is intact until publication");
            release.countDown();
            var generated = handle.completion().get(10, TimeUnit.SECONDS);
            assertEquals(generated.job().gcode(), Files.readString(output));
            assertNotNull(generated.preview());
            assertFalse(generated.preview().travelCenterlines().intersects(options().exclusions().getFirst().geometry()));
            noTemporaries();
        } finally { release.countDown(); jobs.shutdown(); }
    }

    @Test void cancelledDuringWritingLeavesOldFileAndRemovesTemporary() throws Exception {
        Path output = directory.resolve("drill.nc");
        Files.writeString(output, "original");
        var cancelled = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> generate(image(10), output,
                context(cancelled, (fraction, phase) -> { if (phase.startsWith("Gravando")) cancelled.set(true); }), () -> fail("must not publish")));
        assertEquals("original", Files.readString(output));
        noTemporaries();
    }

    @Test void explicitPositionsReachPublishedFileAndPreviewAndInvalidHeightPreservesDestination() throws Exception {
        Path output = directory.resolve("positions.nc");
        var defaults = Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, true));
        var context = context(new AtomicBoolean(), (fraction, phase) -> {});
        var positions = new GCodeGenerator.DrillJobOptions(true, 15, .5, null, null)
                .withPositions(20.0, 0.0, 5.0).withExclusions(true, options().exclusions());
        var generated = DrillCncGeneration.generate(image(10), defaults, List.of(1), positions,
                GCodePreprocessor.FX_PORTABLE, output, context, () -> {});
        assertEquals(generated.job().gcode(), Files.readString(output));
        assertTrue(generated.preview().travelCenterlines().covers(factory.createPoint(new Coordinate(0, 5))));
        String original = Files.readString(output);
        var invalid = new GCodeGenerator.DrillJobOptions(true, 1, .5, null, null).withPositions(null, 0.0, 5.0);
        assertThrows(IllegalArgumentException.class, () -> DrillCncGeneration.generate(image(10), defaults, List.of(1), invalid,
                GCodePreprocessor.FX_PORTABLE, output, context, () -> fail("must not publish")));
        assertEquals(original, Files.readString(output)); noTemporaries();
    }

    @Test void invalidCutAndChangedSourceValidationCannotOverwriteDestination() throws Exception {
        Path output = directory.resolve("drill.nc");
        Files.writeString(output, "original");
        var context = context(new AtomicBoolean(), (fraction, phase) -> {});
        assertThrows(IllegalArgumentException.class, () -> generate(image(5), output, context, () -> fail("invalid cut")));
        assertEquals("original", Files.readString(output));
        assertThrows(IllegalStateException.class, () -> generate(image(10), output, context,
                () -> { throw new IllegalStateException("source changed"); }));
        assertEquals("original", Files.readString(output));
        noTemporaries();
    }

    @Test void directoryDestinationAndCancelledBeforeGenerationCannotCreateOutput() throws Exception {
        var context = context(new AtomicBoolean(), (fraction, phase) -> {});
        assertThrows(IOException.class, () -> generate(image(10), directory, context, () -> {}));
        Path output = directory.resolve("cancelled.nc");
        assertThrows(CancellationException.class, () -> generate(image(10), output,
                context(new AtomicBoolean(true), (fraction, phase) -> {}), () -> {}));
        assertFalse(Files.exists(output));
        noTemporaries();
    }

    @Test void generationIsExplicitlyRejectedOnFxThread() throws Exception {
        TerminalPanelTest.fx(() -> {
            assertThrows(IllegalStateException.class, () -> generate(image(10), directory.resolve("wrong-thread.nc"),
                    context(new AtomicBoolean(), (fraction, phase) -> {}), () -> {}));
            return null;
        });
        assertFalse(Files.exists(directory.resolve("wrong-thread.nc")));
    }

    @Test void manyHolesReportOnlyChangedPercentagesInsteadOfOneUiEventPerHole() throws Exception {
        var drills = java.util.stream.IntStream.range(0, 600).mapToObj(index -> new ExcellonImage.Drill(1, 10 + index, 0)).toList();
        var image = ExcellonImage.of("MM", Map.of(1, 1.0), drills, List.of(), factory.createPolygon());
        var reports = new AtomicInteger();
        var context = context(new AtomicBoolean(), (fraction, phase) -> { if (Double.isFinite(fraction)) reports.incrementAndGet(); });
        generate(image, directory.resolve("many.nc"), context, () -> {});
        assertTrue(reports.get() > 10);
        assertTrue(reports.get() <= 100, "avoid enqueuing one FX callback per hole or G-code line");
        noTemporaries();
    }
}
