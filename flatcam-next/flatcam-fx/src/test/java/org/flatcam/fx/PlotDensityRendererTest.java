package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

@EnabledOnOs(OS.WINDOWS)
class PlotDensityRendererTest {
    private static final PlotCamera CAMERA = new PlotCamera(50, 40, 3, 80, 60, 44, 20);

    private static Geometry packed() {
        Coordinate[] points = new Coordinate[3001];
        for (int i = 0; i < points.length; i++) points[i] = new Coordinate(50 + (i % 2 == 0 ? -.15 : .15), 40 + (i % 20) * .0001);
        return new GeometryFactory().createLineString(points);
    }

    private static PlotDensityRenderer.DrawResult draw(PlotDensityRenderer renderer, PlotDrawableIndex index, PlotCamera camera, Color color) {
        var gc = new Canvas(124, 80).getGraphicsContext2D();
        return renderer.draw(gc, "key", index, true, false, color, 1.5, camera, camera.visibleBounds());
    }

    private static void await(PlotDensityRenderer renderer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(renderer::preparing)) {
            if (System.nanoTime() > deadline) fail("raster not ready");
            Thread.sleep(10);
        }
    }

    @Test void cachedZoomEditInvalidateForgetAndClearRespectPerBindingOwnershipInBothTransferModes() throws Exception {
        var geometry = packed(); var index = new PlotDrawableIndex(geometry);
        for (boolean pixelBuffer : new boolean[]{true, false}) {
            var renderer = fx(() -> new PlotDensityRenderer(true, pixelBuffer, key -> true, () -> {}));
            try {
                assertEquals(PlotDensityRenderer.DrawResult.PREPARING, fx(() -> draw(renderer, index, CAMERA, Color.RED)));
                await(renderer);
                fx(() -> {
                    assertEquals(PlotDensityRenderer.DrawResult.CURRENT, draw(renderer, index, CAMERA, Color.RED));
                    assertEquals(1, renderer.stats().latestFrames()); assertEquals(1, renderer.stats().overviewFrames());
                    assertEquals(80 * 60 * 4, renderer.stats().estimatedImageBytes(), "latest and overview share the image, counted once");
                    var vector = new PlotCamera(50, 40, 10, 80, 60, 44, 20);
                    assertEquals(PlotDensityRenderer.DrawResult.VECTOR, draw(renderer, index, vector, Color.RED));
                    assertEquals(PlotDensityRenderer.DrawResult.CURRENT, draw(renderer, index, CAMERA, Color.RED));
                    assertFalse(renderer.preparing());
                    return null;
                });
                var changed = new PlotDrawableIndex(AffineTransformation.translationInstance(2, 0).transform(geometry));
                fx(() -> {
                    renderer.invalidate("key");
                    assertEquals(PlotDensityRenderer.DrawResult.PREVIEW, draw(renderer, changed, CAMERA, Color.RED));
                    return null;
                });
                await(renderer);
                fx(() -> {
                    assertEquals(PlotDensityRenderer.DrawResult.CURRENT, draw(renderer, changed, CAMERA, Color.RED));
                    assertEquals(1, renderer.stats().latestFrames()); assertEquals(1, renderer.stats().overviewFrames());
                    // Changed ink must not display old-colored pixels.
                    assertEquals(PlotDensityRenderer.DrawResult.PREPARING, draw(renderer, changed, CAMERA, Color.GREEN));
                    renderer.forget("key");
                    assertEquals(0, renderer.stats().estimatedImageBytes()); assertFalse(renderer.preparing());
                    return null;
                });
                Thread.sleep(120);
                assertEquals(0, fx(() -> renderer.stats().latestFrames()));
                fx(() -> { draw(renderer, index, CAMERA, Color.RED); renderer.clear(); return null; });
                Thread.sleep(120);
                assertEquals(0, fx(() -> renderer.stats().overviewFrames()));
                assertEquals(0, fx(() -> renderer.stats().estimatedImageBytes()));
            } finally { fx(() -> { renderer.close(); return null; }); }
        }
    }

    @Test void closeRejectsQueuedPublicationsAndSynchronousScratchIsReleasedOnClear() throws Exception {
        var index = new PlotDrawableIndex(packed());
        var asynchronous = fx(() -> new PlotDensityRenderer(true, true, key -> true, () -> fail("closed renderer published")));
        fx(() -> { draw(asynchronous, index, CAMERA, Color.RED); asynchronous.close(); asynchronous.close(); return null; });
        Thread.sleep(120);
        assertEquals(0, fx(() -> asynchronous.stats().estimatedImageBytes()));
        assertFalse(fx(asynchronous::preparing));
        var synchronous = fx(() -> new PlotDensityRenderer(false, false, key -> true, () -> fail("sync renderer queued work")));
        try {
            fx(() -> {
                assertEquals(PlotDensityRenderer.DrawResult.CURRENT, draw(synchronous, index, CAMERA, Color.RED));
                assertEquals(80 * 60 * 6, synchronous.stats().syncScratchBytes());
                assertEquals(0, synchronous.stats().overviewFrames());
                synchronous.clear();
                assertEquals(0, synchronous.stats().syncScratchBytes());
                assertEquals(0, synchronous.stats().estimatedImageBytes());
                return null;
            });
        } finally { fx(() -> { synchronous.close(); return null; }); }
        assertThrows(IllegalStateException.class, synchronous::clear, "presentation/cache mutation must remain on FX");
    }

    @Test void aWorkerFrameRejectedByTheViewportNeverCreatesAnImage() throws Exception {
        var index = new PlotDrawableIndex(packed());
        var renderer = fx(() -> new PlotDensityRenderer(true, true, key -> false, () -> fail("rejected frame published")));
        try {
            fx(() -> { draw(renderer, index, CAMERA, Color.RED); return null; });
            await(renderer);
            assertEquals(0, fx(() -> renderer.stats().publications()));
            assertEquals(0, fx(() -> renderer.stats().estimatedImageBytes()));
        } finally { fx(() -> { renderer.close(); return null; }); }
    }

    private static <T> T fx(Callable<T> action) throws Exception { return TerminalPanelTest.fx(action); }
}
