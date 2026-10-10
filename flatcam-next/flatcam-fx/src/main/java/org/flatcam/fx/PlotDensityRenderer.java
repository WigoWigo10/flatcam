package org.flatcam.fx;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.locationtech.jts.geom.Envelope;

/** FX-thread-owned density orchestration, bounded per-binding latest/overview cache and presentation.
 * DenseRenderer owns CPU scratch and immutable completed arrays on its worker; CanvasRasterPresenter
 * owns conversion/drawing only. Invalidate keeps display previews, forget/clear/close discard them.
 * Neither previews nor rasters are ever used for picking, editing or CAM. */
final class PlotDensityRenderer implements AutoCloseable {
    enum DrawResult { VECTOR, PREPARING, PREVIEW, CURRENT }
    record Stats(int latestFrames, int overviewFrames, int denseBindings, int queuedRequests,
                 long estimatedImageBytes, long syncScratchBytes, long workerScratchBytes, long publications,
                 long imagePrepareNanos, long presentationCommandsNanos, boolean timingsEnabled) { }

    private final boolean asynchronous;
    private final boolean pixelBuffer;
    private final boolean timings;
    private final Predicate<Object> acceptsFrame;
    private final Runnable redraw;
    private final DenseRenderer worker;
    private final CanvasRasterPresenter presenter = new CanvasRasterPresenter();
    private final Set<Object> dense = new HashSet<>();
    private final Map<Object, CanvasRasterPresenter.Frame> latest = new LinkedHashMap<>();
    private final Map<Object, CanvasRasterPresenter.Frame> overviews = new LinkedHashMap<>();
    private short[] cover = new short[0];
    private int[] pixels = new int[0];
    private boolean closed;
    private long publications, imagePrepareNanos, presentationCommandsNanos;

    PlotDensityRenderer(boolean asynchronous, boolean pixelBuffer, Predicate<Object> acceptsFrame, Runnable redraw) {
        this.asynchronous = asynchronous;
        this.pixelBuffer = pixelBuffer;
        this.acceptsFrame = acceptsFrame;
        this.redraw = redraw;
        timings = Boolean.getBoolean(PlotAreaPerformance.ENABLED_PROPERTY) || Boolean.getBoolean("flatcam.plot.benchmark");
        worker = new DenseRenderer(Platform::runLater, this::frameReady);
    }

    DrawResult draw(GraphicsContext gc, Object key, PlotDrawableIndex index, boolean strokeOnly, boolean multicolor,
                    Color ink, double lineWidth, PlotCamera camera, Envelope bounds) {
        requireFx();
        if (closed) return DrawResult.VECTOR;
        if (index == null || !strokeOnly || multicolor || !(lineWidth <= DensityRaster.MAX_WIDTH)) {
            suspend(key);
            return DrawResult.VECTOR;
        }
        double[] load = index.visibleLoad(bounds);
        if (!DensityRaster.shouldRasterize((long) load[0], load[1], camera.scale(), dense.contains(key), lineWidth)) {
            suspend(key);
            return DrawResult.VECTOR;
        }
        dense.add(key);
        int width = Math.max(1, (int) Math.ceil(camera.width()));
        int height = Math.max(1, (int) Math.ceil(camera.height()));
        var view = new DenseRenderer.View(index.geometry(), camera.scale(), camera.centerX(), camera.centerY(),
                camera.width() / 2.0 - camera.centerX() * camera.scale(),
                camera.height() / 2.0 + camera.centerY() * camera.scale(), width, height,
                ink.getRed(), ink.getGreen(), ink.getBlue(), ink.getOpacity(), lineWidth);
        var frame = preview(key, view);
        if (frame == null || !frame.view().sameAs(view)) {
            if (asynchronous) {
                worker.request(key, view, index, bounds);
                if (frame == null) return DrawResult.PREPARING;
                long start = timings ? System.nanoTime() : 0;
                presenter.drawPreview(gc, frame, view, camera);
                if (timings) presentationCommandsNanos += System.nanoTime() - start;
                return DrawResult.PREVIEW;
            }
            int size = Math.multiplyExact(width, height);
            if (cover.length < size) { cover = new short[size]; pixels = new int[size]; }
            DensityRaster.rasterize(index.visibleParts(bounds), camera.scale(), view.offsetX(), view.offsetY(),
                    width, height, cover, lineWidth);
            DensityRaster.toPremultipliedArgb(cover, size, ink.getRed(), ink.getGreen(), ink.getBlue(), ink.getOpacity(), pixels);
            long start = timings ? System.nanoTime() : 0;
            frame = presenter.copyScratch(frame, view, pixels);
            if (timings) imagePrepareNanos += System.nanoTime() - start;
            latest.put(key, frame); // Sync scratch is copied; no overview alias is published.
            publications++;
        }
        // A latest/overview hit supersedes even a queued publication for an intermediate camera.
        if (asynchronous) worker.suspend(key);
        long start = timings ? System.nanoTime() : 0;
        presenter.drawCurrent(gc, frame, camera);
        if (timings) presentationCommandsNanos += System.nanoTime() - start;
        return DrawResult.CURRENT;
    }

    private void frameReady(Object key) {
        requireFx();
        if (closed) return;
        if (!acceptsFrame.test(key)) { worker.forget(key); return; }
        var completed = worker.frame(key);
        if (completed == null) return;
        long start = timings ? System.nanoTime() : 0;
        var frame = presenter.publish(completed, pixelBuffer);
        if (timings) imagePrepareNanos += System.nanoTime() - start;
        publications++;
        latest.put(key, frame);
        var overview = overviews.get(key);
        if (overview == null || overview.view().geometry() != completed.view().geometry()
                || area(completed.view()) >= area(overview.view())) overviews.put(key, frame);
        redraw.run();
    }

    /** Keep current pixels as a visual-only edit preview; cancel all older worker versions. */
    void invalidate(Object key) { requireFx(); worker.forget(key); }

    void forget(Object key) {
        requireFx();
        worker.forget(key);
        dense.remove(key); latest.remove(key); overviews.remove(key);
    }

    private void suspend(Object key) { dense.remove(key); worker.suspend(key); }

    boolean preparing() { requireFx(); return !closed && worker.preparing(); }

    void clear() {
        requireFx();
        worker.clear(); dense.clear(); latest.clear(); overviews.clear();
        cover = new short[0]; pixels = new int[0];
    }

    @Override public void close() {
        requireFx();
        if (closed) return;
        closed = true;
        worker.shutdown(); clear();
    }

    /** UI-visible image backing estimate; excludes worker scratch/Prism copies/VRAM/native memory. */
    Stats stats() {
        requireFx();
        var images = new IdentityHashMap<javafx.scene.image.WritableImage, Boolean>();
        latest.values().forEach(frame -> images.put(frame.image(), true));
        overviews.values().forEach(frame -> images.put(frame.image(), true));
        long bytes = 0;
        for (var image : images.keySet()) bytes += (long) image.getWidth() * (long) image.getHeight() * 4;
        return new Stats(latest.size(), overviews.size(), dense.size(), worker.queuedRequestCount(), bytes,
                (long) cover.length * 2 + (long) pixels.length * 4, worker.scratchBytes(), publications,
                imagePrepareNanos, presentationCommandsNanos, timings);
    }

    private CanvasRasterPresenter.Frame preview(Object key, DenseRenderer.View view) {
        var last = latest.get(key);
        var overview = overviews.get(key);
        if (last != null && !sameInk(last.view(), view)) last = null;
        if (overview != null && !sameInk(overview.view(), view)) overview = null;
        if (last != null && last.view().sameAs(view)) return last;
        if (overview != null && overview.view().sameAs(view)) return overview;
        if (last != null && last.view().geometry() == view.geometry()
                && (overview == null || overview.view().geometry() != view.geometry())) return last;
        if (overview != null && overview.view().geometry() == view.geometry()
                && (last == null || last.view().geometry() != view.geometry())) return overview;
        if (overview != null && (last == null || !bounds(last.view()).covers(bounds(view)))) return overview;
        return last;
    }

    private static double area(DenseRenderer.View view) {
        return (double) view.width() * view.height() / (view.scale() * view.scale());
    }
    private static Envelope bounds(DenseRenderer.View view) {
        return new Envelope(-view.offsetX() / view.scale(), (view.width() - view.offsetX()) / view.scale(),
                (view.offsetY() - view.height()) / view.scale(), view.offsetY() / view.scale());
    }
    private static boolean sameInk(DenseRenderer.View a, DenseRenderer.View b) {
        return a.red() == b.red() && a.green() == b.green() && a.blue() == b.blue()
                && a.opacity() == b.opacity() && a.lineWidth() == b.lineWidth();
    }
    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Plot raster presentation belongs to the FX thread");
    }
}
