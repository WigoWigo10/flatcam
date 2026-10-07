package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.util.Duration;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.panel.Panelize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class PanelizePreviewControllerTest {
    private static PanelizePreview.Input input(int columns) {
        var rectangle = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 8));
        return new PanelizePreview.Input(List.of(new PlotAreaView.PreviewLayer(rectangle, PlotAreaView.LayerCategory.GERBER, false)),
                rectangle.getBoundary(), rectangle,
                new Panelize.Layout(columns, 1, false, 11, 9), true, true, true);
    }
    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final CountDownLatch release = new CountDownLatch(1);
        final List<PanelizePreview.Result> shown = new ArrayList<>();
        final AtomicReference<String> status = new AtomicReference<>();
        final PanelizePreviewController controller;
        Session() throws Exception {
            var occupied = new CountDownLatch(1);
            jobs.submit(context -> { occupied.countDown(); assertTrue(release.await(15, TimeUnit.SECONDS)); return null; }, null);
            assertTrue(occupied.await(10, TimeUnit.SECONDS));
            controller = TerminalPanelTest.fx(() -> new PanelizePreviewController(jobs, shown::add, Duration.ZERO));
        }
        JobHandle<?> request(int columns, java.util.function.BooleanSupplier valid) throws Exception {
            TerminalPanelTest.fx(() -> { controller.request(input(columns), valid, status::set); return null; });
            for (int i = 0; i < 100; i++) {
                var result = TerminalPanelTest.fx(() -> {
                    var field = PanelizePreviewController.class.getDeclaredField("job"); field.setAccessible(true);
                    return (JobHandle<?>) field.get(controller);
                });
                if (result != null) return result;
                Thread.sleep(10);
            }
            throw new AssertionError("preview not scheduled");
        }
        @Override public void close() throws Exception {
            release.countDown(); TerminalPanelTest.fx(() -> { controller.close(); return null; }); jobs.shutdown();
        }
    }

    @Test void closingAfterWorkerCompletesPreventsLateGhosts() throws Exception {
        try (var s = new Session()) {
            var h = s.request(2, () -> true);
            TerminalPanelTest.fx(() -> { s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); s.controller.close(); return null; });
            TerminalPanelTest.fx(() -> { assertTrue(s.shown.stream().allMatch(result -> result == null)); return null; });
        }
    }
    @Test void latestRequestWinsEvenWhenPreviousCompletionIsQueued() throws Exception {
        try (var s = new Session()) {
            var first = s.request(1, () -> true);
            TerminalPanelTest.fx(() -> {
                s.release.countDown(); first.completion().get(10, TimeUnit.SECONDS);
                s.controller.request(input(3), () -> true, s.status::set); return null;
            });
            for (int i = 0; i < 100; i++) {
                if (TerminalPanelTest.fx(() -> s.shown.stream().anyMatch(result -> result != null))) break;
                Thread.sleep(10);
            }
            TerminalPanelTest.fx(() -> {
                var nonNull = s.shown.stream().filter(java.util.Objects::nonNull).toList();
                assertEquals(1, nonNull.size()); assertEquals(3, nonNull.getFirst().content().getNumGeometries()); return null;
            });
        }
    }
    @Test void changedSourceRejectsPreviewBeforePublishingIt() throws Exception {
        try (var s = new Session()) {
            var valid = new AtomicBoolean(true); var h = s.request(2, valid::get);
            TerminalPanelTest.fx(() -> { s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); valid.set(false); return null; });
            TerminalPanelTest.fx(() -> {
                assertTrue(s.shown.stream().allMatch(result -> result == null));
                assertTrue(s.status.get().contains("alterada")); return null;
            });
        }
    }
}
