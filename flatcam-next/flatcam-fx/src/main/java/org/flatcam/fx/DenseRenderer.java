package org.flatcam.fx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Draws density images (see {@link DensityRaster}) on a background thread, so that panning and zooming never wait for
 * one. The Plot Area asks for the image of the view it wants; the newest request of a layer wins - an older one that is
 * still waiting or running is dropped - and a request only starts once the view has stayed still for a moment, so a
 * continuous drag does not rasterize every intermediate view. Until the new image arrives the view keeps showing the
 * previous one, moved and scaled to fit.
 *
 * <p>No JavaFX in here: the finished pixels are handed to {@code ui} (the JavaFX thread in the application), which turns
 * them into an image.
 */
final class DenseRenderer {

    /** How long a view must stay unchanged before it is rasterized. */
    static final long SETTLE_MILLIS = 60;

    /**
     * Everything that decides what the image looks like. {@code geometry} is compared by identity (it is a big
     * object), the rest by value.
     */
    record View(Geometry geometry, double scale, double centerX, double centerY, double offsetX, double offsetY,
                int width, int height, double red, double green, double blue, double opacity, double lineWidth) {

        boolean sameAs(View other) {
            return other != null && geometry == other.geometry && scale == other.scale && centerX == other.centerX
                    && centerY == other.centerY && offsetX == other.offsetX && offsetY == other.offsetY
                    && width == other.width && height == other.height && red == other.red && green == other.green
                    && blue == other.blue && opacity == other.opacity && lineWidth == other.lineWidth;
        }
    }

    /** A finished image: premultiplied ARGB pixels ({@code view.width() * view.height()}) of {@code view}. */
    record Frame(View view, int[] pixels) {
    }

    private final Executor ui;
    private final Consumer<Object> onFrame;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "plot-density");
        thread.setDaemon(true);
        return thread;
    });
    /** Per layer: the id of its newest request (older ones are stale) and the view it asked for. */
    private final Map<Object, Long> latest = new HashMap<>();
    private final Map<Object, View> requested = new HashMap<>();
    private final Map<Object, Frame> frames = new HashMap<>();
    private long nextId;
    private short[] cover = new short[0];

    /**
     * @param ui      runs a task on the thread that owns the displayed images
     * @param onFrame called on {@code ui} with the layer's key when a new frame for it can be read with {@link #frame}
     */
    DenseRenderer(Executor ui, Consumer<Object> onFrame) {
        this.ui = ui;
        this.onFrame = onFrame;
    }

    /** The newest finished frame of a layer, or null. Call from the {@code ui} thread. */
    synchronized Frame frame(Object key) {
        return frames.get(key);
    }

    /**
     * Asks for the image of {@code view}. Does nothing when that view is already the one being waited for or already
     * done. {@code index} and {@code bounds} choose the parts to draw; the work happens on the background thread.
     */
    synchronized void request(Object key, View view, PlotDrawableIndex index, Envelope bounds) {
        Frame done = frames.get(key);
        if (done != null && done.view().sameAs(view)) {
            return;
        }
        View waiting = requested.get(key);
        if (waiting != null && waiting.sameAs(view)) {
            return;
        }
        long id = ++nextId;
        latest.put(key, id);
        requested.put(key, view);
        worker.schedule(() -> render(key, id, view, index, bounds), SETTLE_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Forgets a layer (removed or its geometry replaced): pending work is dropped and its frame discarded. */
    synchronized void forget(Object key) {
        latest.put(key, ++nextId);
        requested.remove(key);
        frames.remove(key);
    }

    synchronized void clear() {
        latest.replaceAll((key, id) -> ++nextId);
        requested.clear();
        frames.clear();
    }

    /** Stops the worker (the view is going away). */
    void shutdown() {
        worker.shutdownNow();
    }

    private synchronized boolean isStale(Object key, long id) {
        Long newest = latest.get(key);
        return newest == null || newest != id;
    }

    private void render(Object key, long id, View view, PlotDrawableIndex index, Envelope bounds) {
        if (isStale(key, id)) {
            return;
        }
        int pixels = view.width() * view.height();
        if (cover.length < pixels) {
            cover = new short[pixels];
        }
        List<PlotDrawableIndex.Part> parts = index.visibleParts(bounds);
        boolean finished = DensityRaster.rasterize(parts, view.scale(), view.offsetX(), view.offsetY(), view.width(),
                view.height(), cover, view.lineWidth(), true, () -> isStale(key, id));
        if (!finished || isStale(key, id)) {
            return;
        }
        int[] argb = new int[pixels];
        DensityRaster.toPremultipliedArgb(cover, pixels, view.red(), view.green(), view.blue(), view.opacity(), argb);
        Frame frame = new Frame(view, argb);
        ui.execute(() -> {
            synchronized (this) {
                if (isStale(key, id)) {
                    return;
                }
                frames.put(key, frame);
                requested.remove(key);
            }
            onFrame.accept(key);
        });
    }
}
