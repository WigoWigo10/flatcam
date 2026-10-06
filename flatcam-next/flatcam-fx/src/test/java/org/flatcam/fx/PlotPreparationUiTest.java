package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class PlotPreparationUiTest {
    private static <T> T fx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @Test void largeLayerShowsPreparationAndPublishesExactPixelsWithoutChangingGeometry() throws Exception {
        var factory = new GeometryFactory();
        Geometry[] polygons = new Geometry[130];
        for (int i = 0; i < polygons.length; i++) polygons[i] = factory.toGeometry(new Envelope(49, 51, 39, 41));
        var geometry = factory.createGeometryCollection(polygons);
        String original = geometry.toText();
        var plot = fx(() -> {
            var view = new PlotAreaView();
            view.resize(800, 600);
            view.putLayer("large", PlotAreaView.LayerCategory.GERBER, geometry, Color.RED, Color.RED, false);
            assertTrue(((Label) view.lookup("#plot-preparing")).isVisible());
            // The UI can accept changes before the worker's queued completion is published.
            view.setGridSnap(false, 1, 1);
            return view;
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (fx(() -> ((Label) plot.lookup("#plot-preparing")).isVisible()) && System.nanoTime() < deadline)
                Thread.sleep(20);
            fx(() -> {
                assertFalse(((Label) plot.lookup("#plot-preparing")).isVisible());
                var canvas = (Canvas) plot.getChildren().getFirst();
                Color center = canvas.snapshot(null, null).getPixelReader().getColor(422, 310);
                assertTrue(center.getRed() > 0.9 && center.getGreen() < 0.1 && center.getBlue() < 0.1);
                assertEquals(original, geometry.toText());
                plot.clearLayers();
                assertFalse(((Label) plot.lookup("#plot-preparing")).isVisible());
                return null;
            });
        } finally { fx(() -> { plot.dispose(); return null; }); }
    }
}
