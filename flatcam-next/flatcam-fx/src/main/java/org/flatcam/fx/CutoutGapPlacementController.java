package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.GapPattern;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Worker-backed draft: no project object is changed until the ordinary Generate action. */
final class CutoutGapPlacementController implements AutoCloseable {
    private final PlotAreaView plot;
    private final JobExecutor jobs;
    private final String units;
    private final Geometry source;
    private final BooleanSupplier sourceAvailable;
    private JobHandle<Geometry> job;
    private long revision;
    private boolean active;
    private boolean closed;
    private Consumer<String> feedback = message -> { };

    CutoutGapPlacementController(PlotAreaView plot, JobExecutor jobs, String units, Geometry source,
                                 BooleanSupplier sourceAvailable) {
        this.plot = plot; this.jobs = jobs; this.units = units; this.source = source;
        this.sourceAvailable = sourceAvailable;
    }

    static Geometry cursorMask(GeometryFactory factory, double x, double y, double diameter, double gap) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(diameter)
                || !Double.isFinite(gap) || diameter <= 0 || gap <= 0)
            throw new IllegalArgumentException(I18n.text("ui.6853d2b0b855"));
        // ToolCutOut.cutting_geo: the removed centerline includes the tool diameter.
        double half = (diameter + gap) / 2;
        if (!Double.isFinite(half) || !Double.isFinite(x - half) || !Double.isFinite(x + half)
                || !Double.isFinite(y - half) || !Double.isFinite(y + half))
            throw new IllegalArgumentException(I18n.text("ui.3e6aff9eb4bd"));
        return factory.toGeometry(new Envelope(x - half, x + half, y - half, y + half));
    }

    boolean begin(CutoutParameters params, List<Geometry> existing, Consumer<Geometry> accepted,
                  Consumer<String> status) {
        if (closed || !sourceAvailable.getAsBoolean()) {
            status.accept(I18n.text("ui.1170a4131948")); return false;
        }
        cursorMask(source.getFactory(), 0, 0, params.toolDiameter(), params.gapSize());
        cancel();
        feedback = status;
        long current = ++revision;
        var masks = new ArrayList<>(existing);
        var remaining = new Geometry[1];
        // Exterior only: the gesture must not put bridges on opt-in internal cuts.
        var outlineParams = new CutoutParameters(params.toolDiameter(), params.margin(), params.convexShape(),
                params.kind(), params.shape(), params.gapSize(), GapPattern.NONE, false);
        active = plot.beginCursorPlacement(new PlotAreaView.CursorPlacementHandler() {
            public Geometry preview(double x, double y) {
                return cursorMask(source.getFactory(), x, y, params.toolDiameter(), params.gapSize());
            }
            public void onClick(double x, double y) {
                if (!sourceAvailable.getAsBoolean()) { cancel(); status.accept(I18n.text("ui.1170a4131948")); return; }
                if (job != null || remaining[0] == null) { status.accept(I18n.text("ui.d30cc2702ef8")); return; }
                Geometry mask = preview(x, y);
                var candidate = new ArrayList<>(masks); candidate.add(mask);
                status.accept(I18n.text("ui.668d2553dd7e"));
                JobHandle<Geometry> handle = jobs.submit(context -> CutoutGenerator.generate(units, source,
                        outlineParams, candidate, context::isCancelled).geometry(), null);
                job = handle;
                handle.completion().whenComplete((paths, error) -> Platform.runLater(() -> {
                    if (!isCurrent(current, handle)) return;
                    job = null;
                    if (error != null) { status.accept(I18n.text("ui.01b00c34f439") + cause(error)); return; }
                    if (paths.isEmpty()) { status.accept(I18n.text("ui.a1ec6b9d91fa")); return; }
                    double removed = remaining[0].getLength() - paths.getLength();
                    if (removed <= Math.max(1e-12, params.toolDiameter() * 1e-9)) {
                        status.accept(I18n.text("ui.148f741b77d1")); return;
                    }
                    masks.add(mask); remaining[0] = paths;
                    accepted.accept(mask);
                    show(paths, masks);
                    status.accept(masks.size() + I18n.text("ui.f83902a17905"));
                }));
            }
            public void onFinish() {
                active = false; ++revision;
                boolean pending = job != null;
                if (job != null) job.cancel(); job = null;
                status.accept((pending ? I18n.text("ui.246de6dcc427") : I18n.text("ui.c6d2c2e6107d"))
                        + I18n.text("ui.5451438445d2"));
            }
        });
        if (!active) { status.accept(I18n.text("ui.58937b12ac18")); return false; }
        status.accept(I18n.text("ui.4a927427cce7"));
        JobHandle<Geometry> handle = jobs.submit(context -> CutoutGenerator.generate(units, source,
                outlineParams, masks, context::isCancelled).geometry(), null);
        job = handle;
        handle.completion().whenComplete((paths, error) -> Platform.runLater(() -> {
            if (!isCurrent(current, handle)) return;
            job = null;
            if (error != null || paths.isEmpty()) {
                cancel(); status.accept(error == null ? I18n.text("ui.5eab3716c6d4") : I18n.text("ui.01b00c34f439") + cause(error)); return;
            }
            remaining[0] = paths; show(paths, masks);
            status.accept(I18n.text("ui.854d5a798117"));
        }));
        return true;
    }

    private boolean isCurrent(long expected, JobHandle<Geometry> handle) {
        if (closed || !active || revision != expected || job != handle || handle.isCancelled()) return false;
        if (!sourceAvailable.getAsBoolean()) { cancel(); feedback.accept(I18n.text("ui.1170a4131948")); return false; }
        return true;
    }

    private void show(Geometry paths, List<Geometry> masks) {
        plot.setEditorReference(paths);
        plot.setEditorHighlight(masks.isEmpty() ? null : source.getFactory().buildGeometry(masks), false);
    }

    void cancel() {
        ++revision; active = false;
        if (job != null) job.cancel(); job = null;
        plot.cancelPlacement();
        plot.setEditorReference(null); plot.setEditorHighlight(null, false);
    }

    boolean isBusy() { return job != null; }

    @Override public void close() { cancel(); closed = true; }

    private static String cause(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage();
    }
}
