package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class DenseRendererTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private final List<Object> delivered = Collections.synchronizedList(new ArrayList<>());
    private CountDownLatch latch = new CountDownLatch(1);
    private DenseRenderer renderer;

    private DenseRenderer renderer(int expected) {
        latch = new CountDownLatch(expected);
        // The "UI thread" here is just the calling thread of execute(): run the task right away.
        renderer = new DenseRenderer(Runnable::run, key -> {
            delivered.add(key);
            latch.countDown();
        });
        return renderer;
    }

    @AfterEach
    void stop() {
        if (renderer != null) {
            renderer.shutdown();
        }
    }

    private static Geometry lines() {
        List<Geometry> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            list.add(FACTORY.createLineString(new Coordinate[] {new Coordinate(0, i), new Coordinate(19, i)}));
        }
        return FACTORY.buildGeometry(list);
    }

    private static DenseRenderer.View view(Geometry geometry, double scale) {
        return new DenseRenderer.View(geometry, scale, 10, 10, 0, 20 * scale, 20, 20, 1, 0, 0, 1, 1.5);
    }

    @Test
    void aRequestIsRenderedOffTheCallerAndDeliveredWithItsPixels() throws Exception {
        DenseRenderer renderer = renderer(1);
        Geometry geometry = lines();
        PlotDrawableIndex index = new PlotDrawableIndex(geometry);
        DenseRenderer.View view = view(geometry, 1);
        long start = System.nanoTime();
        renderer.request("layer", view, index, null);
        assertNull(renderer.frame("layer"), "nothing is ready before the worker has run");
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(DenseRenderer.SETTLE_MILLIS - 5),
                "the view has to settle first");
        DenseRenderer.Frame frame = renderer.frame("layer");
        assertNotNull(frame);
        assertSame(view, frame.view());
        assertEquals(20 * 20, frame.pixels().length);
        // A line along y = 5 (offsetY 20 -> row 15): the red it left is opaque in the middle of the picture.
        int centre = frame.pixels()[15 * 20 + 8];
        assertTrue((centre >>> 24) > 100, "alpha " + (centre >>> 24));
        // The same view again is not rendered twice.
        renderer.request("layer", view, index, null);
        Thread.sleep(DenseRenderer.SETTLE_MILLIS * 3);
        assertEquals(1, delivered.size());
    }

    @Test
    void theNewestRequestOfALayerWinsAndOlderOnesAreDropped() throws Exception {
        DenseRenderer renderer = renderer(1);
        Geometry geometry = lines();
        PlotDrawableIndex index = new PlotDrawableIndex(geometry);
        DenseRenderer.View first = view(geometry, 1);
        DenseRenderer.View second = view(geometry, 2);
        DenseRenderer.View third = view(geometry, 3);
        renderer.request("layer", first, index, null);
        renderer.request("layer", second, index, null);
        renderer.request("layer", third, index, null);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(DenseRenderer.SETTLE_MILLIS * 3);
        assertEquals(1, delivered.size(), "only the last view was rendered");
        assertSame(third, renderer.frame("layer").view());
    }

    @Test
    void forgettingALayerDropsItsPendingWorkAndItsFrame() throws Exception {
        DenseRenderer renderer = renderer(1);
        Geometry geometry = lines();
        PlotDrawableIndex index = new PlotDrawableIndex(geometry);
        renderer.request("layer", view(geometry, 1), index, null);
        renderer.forget("layer");
        Thread.sleep(DenseRenderer.SETTLE_MILLIS * 4);
        assertEquals(0, delivered.size());
        assertNull(renderer.frame("layer"));
        // Layers are independent: another one is still served.
        renderer.request("other", view(geometry, 1), index, null);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(renderer.frame("other"));
        renderer.forget("other");
        assertNull(renderer.frame("other"));
    }

    @Test
    void aSupersededJobStopsInsteadOfFinishingAHugeRaster() throws Exception {
        // Enough parts that the worker polls for cancellation many times; the second request makes the first stale.
        List<Geometry> list = new ArrayList<>();
        for (int i = 0; i < 40_000; i++) {
            list.add(FACTORY.createLineString(new Coordinate[] {new Coordinate(0, i % 500), new Coordinate(400, (i % 500) + 0.5)}));
        }
        Geometry geometry = FACTORY.buildGeometry(list);
        PlotDrawableIndex index = new PlotDrawableIndex(geometry);
        DenseRenderer renderer = renderer(1);
        renderer.request("layer", new DenseRenderer.View(geometry, 1, 0, 0, 0, 500, 400, 500, 1, 0, 0, 1, 1.5), index, null);
        renderer.request("layer", new DenseRenderer.View(geometry, 1, 0, 0, 0, 500, 300, 400, 1, 0, 0, 1, 1.5), index, null);
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        Thread.sleep(DenseRenderer.SETTLE_MILLIS * 3);
        assertEquals(1, delivered.size());
        assertEquals(300, renderer.frame("layer").view().width());
    }
}
