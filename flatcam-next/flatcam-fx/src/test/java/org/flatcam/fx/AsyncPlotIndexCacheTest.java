package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class AsyncPlotIndexCacheTest {
    private final GeometryFactory factory = new GeometryFactory();

    private Geometry point(int x) { return factory.createPoint(new Coordinate(x, 0)); }

    @Test void manyRevisionsKeepOnlyOnePendingBuildAndTheNewestGeometryWins() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var built = new ArrayList<Geometry>();
        var delivered = new ArrayList<Object>();
        try (var cache = new AsyncPlotIndexCache(ui::add, delivered::add, worker::add, g -> true,
                (g, old, token) -> { built.add(g); return new PlotDrawableIndex(g, old, token); })) {
            Geometry newest = null;
            for (int i = 0; i < 1000; i++) assertNull(cache.getOrRequest("layer", newest = point(i)));
            assertEquals(1, worker.size(), "one drain task, not a task per camera/geometry revision");
            worker.remove().run();
            assertEquals(List.of(newest), built);
            assertNull(cache.getOrRequest("layer", newest), "publication has not run yet");
            ui.remove().run();
            assertSame(newest, cache.getOrRequest("layer", newest).geometry());
            assertEquals(List.of("layer"), delivered);
            assertFalse(cache.preparing());
            assertTrue(worker.isEmpty());
        }
    }

    @Test void removedOrReplacedBindingsCannotBeResurrectedByQueuedPublications() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var delivered = new ArrayList<Object>();
        try (var cache = new AsyncPlotIndexCache(ui::add, delivered::add, worker::add, g -> true,
                PlotDrawableIndex::new)) {
            var first = point(1); var second = point(2);
            cache.getOrRequest("layer", first);
            worker.remove().run();
            cache.getOrRequest("layer", second);
            ui.remove().run();
            assertTrue(delivered.isEmpty());
            worker.remove().run();
            cache.clear();
            ui.remove().run();
            assertTrue(delivered.isEmpty());
            assertFalse(cache.preparing());
            cache.getOrRequest("layer", second);
            cache.forget("layer");
            worker.remove().run();
            assertTrue(ui.isEmpty());
            assertFalse(cache.preparing());
        }
    }

    @Test void closingCacheRejectsNewRequestsAndPendingCallbacks() {
        var worker = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var cache = new AsyncPlotIndexCache(ui::add, key -> fail("closed cache publication"),
                worker::add, g -> true, PlotDrawableIndex::new);
        cache.getOrRequest("layer", point(1));
        worker.remove().run();
        cache.close();
        ui.remove().run();
        assertNull(cache.getOrRequest("layer", point(2)));
        assertFalse(cache.preparing());
    }

    @Test void smallIndexesAndSharedImmutableGeometryAreReusedWithoutWorkerTasks() {
        var worker = new ArrayDeque<Runnable>();
        try (var cache = new AsyncPlotIndexCache(Runnable::run, key -> {}, worker::add, g -> false,
                PlotDrawableIndex::new)) {
            var geometry = point(1);
            var source = cache.getOrRequest("source", geometry);
            assertSame(source, cache.getOrRequest("source", geometry));
            assertSame(source, cache.getOrRequest("editor", geometry));
            cache.forget("source");
            assertSame(source, cache.getOrRequest("editor", geometry));
            assertTrue(worker.isEmpty());
        }
    }

    @Test void activeBuildRunsOffCallerAndIsCooperativelyCancelledWhenForgotten() throws Exception {
        var entered = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        Thread caller = Thread.currentThread();
        try (var cache = new AsyncPlotIndexCache(Runnable::run, key -> fail("cancelled publication"),
                Executors.newSingleThreadExecutor(), g -> true, (g, old, token) -> {
                    assertNotSame(caller, Thread.currentThread());
                    entered.countDown();
                    while (!token.isCancellationRequested()) Thread.onSpinWait();
                    cancelled.countDown();
                    token.throwIfCancellationRequested();
                    return null;
                })) {
            assertNull(cache.getOrRequest("layer", point(1)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.forget("layer");
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            assertFalse(cache.preparing());
        }
    }

    @Test void smallReplacementDoesNotRevisitThePreviousLargeIndexOnTheCaller() {
        var worker = new ArrayDeque<Runnable>();
        var old = point(1); var small = point(2);
        try (var cache = new AsyncPlotIndexCache(Runnable::run, key -> {}, worker::add,
                g -> g == old, (g, previous, token) -> {
                    if (g == small) assertNull(previous, "do not scan old parts during a synchronous tiny build");
                    return new PlotDrawableIndex(g, previous, token);
                })) {
            cache.getOrRequest("layer", old);
            worker.remove().run();
            assertSame(small, cache.getOrRequest("layer", small).geometry());
        }
    }

    @Test void failedPreparationStopsRetryingEveryFrameUntilTheBindingChanges() {
        var worker = new ArrayDeque<Runnable>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var geometry = point(1);
        try (var cache = new AsyncPlotIndexCache(Runnable::run, key -> {}, worker::add, g -> true,
                (g, previous, token) -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("intentional index preparation failure");
                })) {
            cache.getOrRequest("layer", geometry);
            worker.remove().run();
            assertTrue(cache.failed());
            assertFalse(cache.preparing());
            for (int i = 0; i < 100; i++) assertNull(cache.getOrRequest("layer", geometry));
            assertEquals(1, calls.get());
            assertTrue(worker.isEmpty());
            cache.forget("layer");
            assertFalse(cache.failed());
        }
    }
}
