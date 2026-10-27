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
    private boolean benchmarkOmitDrawing;
    private long benchmarkDrawAttempts, benchmarkDrawOmissions;

    /** Diagnostic only: keep computation, image creation, caches and readiness; omit only drawImage. */
    void setBenchmarkOmitDrawing(boolean omit) {
        requireFx();
        if (closed) throw new IllegalStateException("Raster probe cannot outlive disposal");
        if (omit && !Boolean.getBoolean("flatcam.plot.benchmark"))
            throw new IllegalStateException("Raster omission requires the opt-in Plot benchmark");
        benchmarkOmitDrawing = omit;
    }
    long benchmarkDrawAttempts() { requireFx(); return benchmarkDrawAttempts; }
    long benchmarkDrawOmissions() { requireFx(); return benchmarkDrawOmissions; }
    private boolean presentImage() {
        if (timings) benchmarkDrawAttempts++;
        if (benchmarkOmitDrawing) { benchmarkDrawOmissions++; return false; }
        return true;
    }

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
        return draw(gc, key, index, strokeOnly, multicolor, ink, lineWidth, camera, bounds, false);
    }

    DrawResult draw(GraphicsContext gc, Object key, PlotDrawableIndex index, boolean strokeOnly, boolean multicolor,
                    Color ink, double lineWidth, PlotCamera camera, Envelope bounds, boolean worldWidth) {
        requireFx();
        if (closed) return DrawResult.VECTOR;
        if (index == null || !strokeOnly || multicolor || !(lineWidth <= DensityRaster.MAX_WIDTH)
                // Segment coverage adds overlaps within a path; it is not a translucent Canvas stroke union.
                // Until per-path compositing is available, wide translucent bodies must remain vector.
                || lineWidth > DensityRaster.ROUND_FROM_WIDTH && ink.getOpacity() < 1) {
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
                ink.getRed(), ink.getGreen(), ink.getBlue(), ink.getOpacity(), lineWidth, worldWidth);
        var frame = preview(key, view);
        if (frame == null || !frame.view().sameAs(view)) {
            if (asynchronous) {
                worker.request(key, view, index, bounds);
                if (frame == null) return DrawResult.PREPARING;
                long start = timings ? System.nanoTime() : 0;
                if (presentImage()) presenter.drawPreview(gc, frame, view, camera);
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
        if (presentImage()) presenter.drawCurrent(gc, frame, camera);
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
        benchmarkOmitDrawing = false;
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
                && a.opacity() == b.opacity() && a.worldWidth() == b.worldWidth()
                && (a.worldWidth() ? sameWorldWidth(a, b) : a.lineWidth() == b.lineWidth());
    }
    private static boolean sameWorldWidth(DenseRenderer.View a, DenseRenderer.View b) {
        double x = a.lineWidth() / a.scale(), y = b.lineWidth() / b.scale();
        return Math.abs(x - y) <= Math.max(Math.abs(x), Math.abs(y)) * 1e-12;
    }
    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Plot raster presentation belongs to the FX thread");
    }
}
