package org.flatcam.fx;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;

/** Debounced independent worker; closing/replacing the tool invalidates every queued publication. */
final class PanelizePreviewController implements AutoCloseable {
    private final JobExecutor jobs;
    private final Consumer<PanelizePreview.Result> display;
    private final PauseTransition pause;
    private JobHandle<PanelizePreview.Result> job;
    private long revision;
    private boolean closed;
    private PanelizePreview.Result current;

    PanelizePreview.Result current() { return current; }

    private void show(PanelizePreview.Result result) { current = result; display.accept(result); }

    PanelizePreviewController(JobExecutor jobs, Consumer<PanelizePreview.Result> display) {
        this(jobs, display, Duration.millis(180));
    }
    PanelizePreviewController(JobExecutor jobs, Consumer<PanelizePreview.Result> display, Duration delay) {
        this.jobs = jobs; this.display = display; pause = new PauseTransition(delay);
    }

    void request(PanelizePreview.Input input, BooleanSupplier valid, Consumer<String> status) {
        if (closed) return;
        long current = ++revision;
        pause.stop(); if (job != null) job.cancel(); job = null;
        if (input == null) { show(null); status.accept(""); return; }
        status.accept("Preparando previa...");
        pause.setOnFinished(event -> {
            if (closed || current != revision) return;
            JobHandle<PanelizePreview.Result> handle = jobs.submit(context -> PanelizePreview.build(input, context::isCancelled), null);
            job = handle;
            handle.completion().whenComplete((result, error) -> Platform.runLater(() -> {
                if (closed || current != revision || job != handle) return;
                job = null;
                if (handle.isCancelled() || !valid.getAsBoolean()) {
                    show(null); status.accept("Origem/referencia alterada; atualize a previa."); return;
                }
                if (error != null) {
                    show(null); status.accept("Falha na previa: " + error.getMessage()); return;
                }
                show(result); status.accept(result.notice());
            }));
        });
        pause.playFromStart();
    }

    @Override public void close() {
        closed = true; ++revision; pause.stop();
        if (job != null) job.cancel(); job = null;
        show(null);
    }
}
