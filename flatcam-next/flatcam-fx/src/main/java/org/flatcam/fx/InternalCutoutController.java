package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.cutout.InternalCutoutGenerator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/** Owns only temporary overlays and placements; discovery and compensation run outside the FX thread. */
final class InternalCutoutController implements InternalCutoutToolPanel.Host, AutoCloseable {
    private final PlotAreaView plot;
    private final JobExecutor jobs;
    private final String units;
    private final Geometry source;
    private final BooleanSupplier available;
    private JobHandle<?> job;
    private long revision;
    private long interaction;
    private boolean closed;

    InternalCutoutController(PlotAreaView plot, JobExecutor jobs, String units, Geometry source, BooleanSupplier available) {
        this.plot = plot; this.jobs = jobs; this.units = units; this.source = source; this.available = available;
    }

    @Override public void load(Consumer<List<Geometry>> done, Consumer<String> failure) {
        work(context -> {
            var openings = InternalCutoutGenerator.openings(source, context::isCancelled);
            for (Geometry opening : openings) { context.checkCancelled(); opening.getEnvelopeInternal(); }
            return openings;
        }, done, failure);
    }

    @Override public void preview(List<Geometry> selected, double diameter, double margin,
                                  Consumer<InternalCutoutToolPanel.PreviewInfo> done,
                                  Consumer<InternalCutoutToolPanel.PreviewIssue> failure) {
        if (selected.isEmpty()) { clear(); return; }
        record Prepared(InternalCutoutPreview drawing, double length) { }
        Consumer<String> unavailable=message -> { clearOverlays(); failure.accept(new InternalCutoutToolPanel.PreviewIssue(message,-1)); };
        work(context -> {
            Geometry paths=InternalCutoutGenerator.generate(units,source,selected,diameter,margin,context::isCancelled).geometry();
            context.checkCancelled();
            Geometry swept=paths.buffer(diameter/2,16);
            context.checkCancelled();
            Geometry openings=source.getFactory().buildGeometry(selected);
            // Warm envelopes in the worker; publishing and frame-selection stay lightweight.
            openings.getEnvelopeInternal(); paths.getEnvelopeInternal(); swept.getEnvelopeInternal();
            return new Prepared(new InternalCutoutPreview(openings,paths,swept,null),paths.getLength());
        }, prepared -> {
            plot.setInternalCutoutPreview(prepared.drawing());
            done.accept(new InternalCutoutToolPanel.PreviewInfo(prepared.length()));
        }, unavailable, error -> {
            while(error.getCause()!=null)error=error.getCause();
            int index=error instanceof InternalCutoutGenerator.InvalidOpening invalid ? invalid.opening()-1 : -1;
            clearOverlays();
            if(index>=0 && index<selected.size())
                plot.setInternalCutoutPreview(new InternalCutoutPreview(null,null,null,selected.get(index)));
            failure.accept(new InternalCutoutToolPanel.PreviewIssue(InternalCutoutMessages.error(error),index));
        });
    }

    @Override public void frame(List<Geometry> selected) {
        if (!ready(message -> {})) return;
        Envelope bounds=new Envelope();
        for (Geometry opening:selected) bounds.expandToInclude(opening.getEnvelopeInternal());
        if(!bounds.isNull())plot.fitToBounds(bounds);
    }

    @Override public boolean pick(List<Geometry> candidates, IntConsumer selected, Consumer<String> failure, Runnable finished) {
        if (!ready(failure)) return false;
        plot.cancelPlacement();
        long gesture = ++interaction;
        return plot.beginCursorPlacement(new PlotAreaView.CursorPlacementHandler() {
            public Geometry preview(double x, double y) { return null; }
            public void onClick(double x, double y) {
                work(context -> {
                    var point = source.getFactory().createPoint(new Coordinate(x,y));
                    int best = -1; double area = Double.POSITIVE_INFINITY;
                    for (int i = 0; i < candidates.size(); i++) {
                        context.checkCancelled(); Geometry candidate = candidates.get(i);
                        if (candidate.getEnvelopeInternal().covers(point.getCoordinate()) && candidate.covers(point)
                                && candidate.getArea() < area) { best = i; area = candidate.getArea(); }
                    }
                    return best;
                }, index -> {
                    if (interaction != gesture) return;
                    if (index >= 0) selected.accept(index);
                    else failure.accept(I18n.text("cutout.internal.no_hit"));
                }, failure);
            }
            public void onFinish() {
                if (interaction == gesture) ++interaction;
                if (!closed) finished.run();
            }
        });
    }

    @Override public boolean draw(InternalCutoutToolPanel.Shape shape, Consumer<Geometry> accepted,
                                  Consumer<String> failure, Runnable cancelled) {
        if (!ready(failure)) return false;
        plot.cancelPlacement();
        if (shape == InternalCutoutToolPanel.Shape.POLYGON) {
            return plot.beginAreaPolygonPlacement(new PlotAreaView.TrackPlacementHandler() {
                public void onCommit(List<Coordinate> points) {
                    if (!ready(failure)) return;
                    var ring = new ArrayList<Coordinate>();
                    for (Coordinate point : points) if (ring.isEmpty() || !ring.getLast().equals2D(point)) ring.add(point.copy());
                    if (ring.size() > 1 && ring.getFirst().equals2D(ring.getLast())) ring.removeLast();
                    if (ring.size() < 3) { failure.accept(I18n.text("cutout.internal.draw_invalid")); return; }
                    ring.add(ring.getFirst().copy());
                    accepted.accept(source.getFactory().createPolygon(ring.toArray(Coordinate[]::new)));
                }
                public void onCancel() { if (!closed) cancelled.run(); }
            });
        }
        double[] anchor = new double[2];
        var handler = new PlotAreaView.PlacementHandler() {
            public void onAnchorChosen(double x, double y) { anchor[0] = x; anchor[1] = y; }
            public void onCommit(double dx, double dy) {
                if (!ready(failure)) return;
                double radius = Math.hypot(dx,dy);
                if (shape == InternalCutoutToolPanel.Shape.CIRCLE ? radius == 0 : dx == 0 || dy == 0) {
                    failure.accept(I18n.text("cutout.internal.draw_invalid")); return;
                }
                accepted.accept(shape == InternalCutoutToolPanel.Shape.CIRCLE
                        ? source.getFactory().createPoint(new Coordinate(anchor[0],anchor[1])).buffer(radius,32)
                        : source.getFactory().toGeometry(new Envelope(anchor[0],anchor[0]+dx,anchor[1],anchor[1]+dy)));
            }
            public void onCancel() { if (!closed) cancelled.run(); }
        };
        return shape == InternalCutoutToolPanel.Shape.CIRCLE ? plot.beginAreaCirclePlacement(128, handler)
                : plot.beginAreaRectanglePlacement(handler);
    }

    private boolean ready(Consumer<String> failure) {
        if (closed) return false;
        if (!available.getAsBoolean()) { clear(); failure.accept(I18n.text("cutout.internal.stale")); return false; }
        return true;
    }

    @FunctionalInterface private interface Calculation<T> { T run(JobContext context) throws Exception; }
    private <T> void work(Calculation<T> calculation, Consumer<T> done, Consumer<String> failure) {
        work(calculation,done,failure,null);
    }
    private <T> void work(Calculation<T> calculation, Consumer<T> done, Consumer<String> failure, Consumer<Throwable> details) {
        if (!ready(failure)) return;
        long expected = ++revision;
        if (job != null) job.cancel();
        JobHandle<T> handle = jobs.submit(calculation::run, null); job = handle;
        handle.completion().whenComplete((value,error) -> Platform.runLater(() -> {
            if (closed || revision != expected || job != handle || handle.isCancelled()) return;
            job = null;
            if (!ready(failure)) return;
            if (error != null) {
                if(details!=null)details.accept(error);else failure.accept(InternalCutoutMessages.error(error));
            } else done.accept(value);
        }));
    }

    @Override public void stop() { plot.cancelPlacement(); }
    @Override public void clear() {
        ++revision; if (job != null) job.cancel(); job = null; clearOverlays();
    }
    private void clearOverlays() { plot.setInternalCutoutPreview(null); }
    @Override public void close() { closed = true; clear(); stop(); }
}
