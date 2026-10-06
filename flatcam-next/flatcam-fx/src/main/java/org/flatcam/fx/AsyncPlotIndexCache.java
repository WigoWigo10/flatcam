package org.flatcam.fx;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Geometry;

/** Display-only indexes for immutable geometry versions. One pending request per binding, one worker. */
final class AsyncPlotIndexCache implements AutoCloseable {
    @FunctionalInterface
    interface Builder {
        PlotDrawableIndex build(Geometry geometry, PlotDrawableIndex previous, CancellationToken cancellation);
    }

    private static final class Request {
        final Object key;
        final Geometry geometry;
        volatile boolean cancelled;
        boolean failed;

        Request(Object key, Geometry geometry) {
            this.key = key;
            this.geometry = geometry;
        }
    }

    private final Executor ui;
    private final Executor worker;
    private final Consumer<Object> ready;
    private final Predicate<Geometry> asynchronous;
    private final Builder builder;
    private final Map<Object, PlotDrawableIndex> indexes = new HashMap<>();
    private final Map<Object, Request> requests = new HashMap<>();
    private final Map<Object, Request> pending = new LinkedHashMap<>();
    private boolean draining;
    private boolean closed;

    AsyncPlotIndexCache(Executor ui, Consumer<Object> ready, boolean enabled) {
        this(ui, ready, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "plot-index");
            thread.setDaemon(true);
            return thread;
        }), geometry -> enabled && (geometry.getNumGeometries() >= 128 || geometry.getNumPoints() >= 4096),
                AsyncPlotIndexCache::prepareIndex);
    }

    private static PlotDrawableIndex prepareIndex(Geometry geometry, PlotDrawableIndex previous,
                                                   CancellationToken cancellation) {
        long start = System.nanoTime();
        var index = new PlotDrawableIndex(geometry, previous, cancellation);
        if (Boolean.getBoolean(PlotAreaPerformance.ENABLED_PROPERTY)) {
            System.err.printf(java.util.Locale.ROOT, "[PLOT-PROFILE] index prepare (%s)=%.1fms%n",
                    Thread.currentThread().getName(), (System.nanoTime() - start) / 1_000_000.0);
        }
        return index;
    }

    AsyncPlotIndexCache(Executor ui, Consumer<Object> ready, Executor worker,
                        Predicate<Geometry> asynchronous, Builder builder) {
        this.ui = ui;
        this.ready = ready;
        this.worker = worker;
        this.asynchronous = asynchronous;
        this.builder = builder;
    }

    /** Null means preparation is pending/failed, not permission to draw the entire unindexed geometry. */
    synchronized PlotDrawableIndex getOrRequest(Object key, Geometry geometry) {
        if (closed) return null;
        PlotDrawableIndex previous = indexes.get(key);
        if (previous != null && previous.geometry() == geometry) return previous;
        Request waiting = requests.get(key);
        if (waiting != null && waiting.geometry == geometry) return null;
        if (waiting != null) waiting.cancelled = true;
        requests.remove(key);
        pending.remove(key);
        // The source object and its editor may bind the same immutable geometry under different keys.
        PlotDrawableIndex shared = indexes.values().stream().filter(index -> index.geometry() == geometry)
                .findFirst().orElse(null);
        if (shared != null) {
            indexes.put(key, shared);
            return shared;
        }
        if (!asynchronous.test(geometry)) {
            // A small result after deleting a huge layer must not scan that huge previous index on the UI thread.
            PlotDrawableIndex index = builder.build(geometry, null, CancellationToken.NONE);
            indexes.put(key, index);
            return index;
        }
        Request request = new Request(key, geometry);
        requests.put(key, request);
        pending.put(key, request);
        if (!draining) {
            draining = true;
            worker.execute(this::drain);
        }
        return null;
    }

    private void drain() {
        try {
            drainPending();
        } finally {
            // An Error must reach the executor/uncaught handler, but must not strand later requests.
            synchronized (this) {
                draining = false;
                if (!closed && !pending.isEmpty()) {
                    draining = true;
                    worker.execute(this::drain);
                }
            }
        }
    }

    private void drainPending() {
        while (true) {
            Request request;
            PlotDrawableIndex previous;
            synchronized (this) {
                if (closed || pending.isEmpty()) {
                    return;
                }
                var entry = pending.entrySet().iterator().next();
                request = entry.getValue();
                pending.remove(entry.getKey());
                previous = indexes.get(request.key);
            }
            try {
                PlotDrawableIndex index = builder.build(request.geometry, previous,
                        () -> request.cancelled || Thread.currentThread().isInterrupted());
                if (!request.cancelled) ui.execute(() -> publish(request, index));
            } catch (CancellationException cancelled) {
                // Replacing/removing a geometry version deliberately abandons its display index.
            } catch (RuntimeException failure) {
                fail(request, failure);
            } catch (Error failure) {
                fail(request, failure);
                throw failure;
            }
        }
    }

    private void fail(Request request, Throwable failure) {
        synchronized (this) {
            if (closed || request.cancelled || requests.get(request.key) != request) return;
            request.failed = true;
        }
        System.getLogger(AsyncPlotIndexCache.class.getName()).log(
                failure instanceof Error ? System.Logger.Level.ERROR : System.Logger.Level.WARNING,
                "Could not prepare plot index", failure);
        ui.execute(() -> {
            synchronized (this) {
                if (closed || request.cancelled || requests.get(request.key) != request) return;
            }
            ready.accept(request.key);
        });
    }

    private void publish(Request request, PlotDrawableIndex index) {
        synchronized (this) {
            if (closed || request.cancelled || requests.get(request.key) != request) return;
            indexes.put(request.key, index);
            requests.remove(request.key);
        }
        ready.accept(request.key);
    }

    synchronized boolean preparing() {
        return requests.values().stream().anyMatch(request -> !request.failed);
    }

    synchronized boolean failed() {
        return requests.values().stream().anyMatch(request -> request.failed);
    }

    synchronized void forget(Object key) {
        invalidate(key);
        indexes.remove(key);
    }

    /** Cancel stale preparation but retain previous immutable part metrics for the next version. */
    synchronized void invalidate(Object key) {
        Request request = requests.remove(key);
        if (request != null) request.cancelled = true;
        pending.remove(key);
    }

    synchronized void clear() {
        requests.values().forEach(request -> request.cancelled = true);
        requests.clear();
        pending.clear();
        indexes.clear();
    }

    @Override
    public synchronized void close() {
        closed = true;
        clear();
        if (worker instanceof ExecutorService service) service.shutdownNow();
    }
}
