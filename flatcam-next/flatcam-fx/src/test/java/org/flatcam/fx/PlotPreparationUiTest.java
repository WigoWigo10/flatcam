package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.flatcam.cam.geometry.GeometryEditSession;

@EnabledOnOs(OS.WINDOWS)
class PlotPreparationUiTest {
    private static <T> T fx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static Color pixel(PlotAreaView view, int x) {
        return ((Canvas) view.getChildren().getFirst()).snapshot(null, null).getPixelReader().getColor(x, 310);
    }

    private static boolean red(Color color) {
        return color.getRed() > 0.9 && color.getGreen() < 0.15 && color.getBlue() < 0.15;
    }

    private static void awaitReady(PlotAreaView view) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fx(() -> ((Label) view.lookup("#plot-preparing")).isVisible()) && System.nanoTime() < deadline)
            Thread.sleep(20);
        assertFalse(fx(() -> ((Label) view.lookup("#plot-preparing")).isVisible()), "preview must settle");
    }

    private static PlotAreaView plot(Geometry geometry, boolean strokes, ThemeOption theme) throws Exception {
        return fx(() -> {
            var view = new PlotAreaView();
            view.resize(800, 600);
            view.applyTheme(theme);
            view.putLayer("edit", PlotAreaView.LayerCategory.GEOMETRY, geometry, Color.RED, Color.RED, strokes);
            return view;
        });
    }

    private static Geometry packedLine(double x, int segments) {
        Coordinate[] points = new Coordinate[segments + 1];
        for (int i = 0; i < points.length; i++)
            points[i] = new Coordinate(x + (i % 2 == 0 ? -0.15 : 0.15), 40 + (i % 20) * 0.0001);
        return new GeometryFactory().createLineString(points);
    }

    private static void scale(PlotAreaView view, double value) throws Exception {
        var field = PlotAreaView.class.getDeclaredField("scale");
        field.setAccessible(true);
        field.setDouble(view, value);
        var redraw = PlotAreaView.class.getDeclaredMethod("redraw");
        redraw.setAccessible(true);
        redraw.invoke(view);
    }

    private static int overviewCount(PlotAreaView view) throws Exception {
        return view.rasterStats().overviewFrames();
    }

    @Test void deleteAndUndoKeepTheOtherShapesVisibleWhileTheIndexChanges() throws Exception {
        var factory = new GeometryFactory();
        Geometry[] parts = new Geometry[130];
        parts[0] = factory.toGeometry(new Envelope(49, 51, 39, 41));
        for (int i = 1; i < parts.length; i++) parts[i] = factory.toGeometry(new Envelope(59, 61, 39, 41));
        var original = factory.createGeometryCollection(parts);
        for (ThemeOption theme : ThemeOption.values()) {
            var session = new GeometryEditSession(original, List.of());
            var view = plot(session.resultGeometry(), false, theme);
            try {
                awaitReady(view);
                fx(() -> {
                    assertTrue(red(pixel(view, 422)));
                    session.selectIndices(List.of(0));
                    assertTrue(session.deleteSelected());
                    var updated = session.resultGeometry();
                    view.updateLayerGeometry("edit", updated);
                    assertSame(updated, view.visibleSelectableLayers().getFirst().geometry(), "editing uses current data");
                    assertTrue(red(pixel(view, 452)), "unchanged shapes must not vanish during deletion");
                    assertTrue(((Label) view.lookup("#plot-preparing")).isVisible());
                    return null;
                });
                awaitReady(view);
                fx(() -> {
                    assertFalse(red(pixel(view, 422)), "deleted shape must disappear in the published revision");
                    assertTrue(red(pixel(view, 452)));
                    assertTrue(session.undo());
                    view.updateLayerGeometry("edit", session.resultGeometry());
                    assertTrue(red(pixel(view, 452)), "unchanged shapes must not vanish during undo");
                    return null;
                });
                awaitReady(view);
                assertTrue(fx(() -> red(pixel(view, 422))), "undo restores the original shape");
            } finally { fx(() -> { view.dispose(); return null; }); }
        }
    }

    @Test void denseRevisionKeepsPixelsUntilTheReplacementAndUndoFramesAreReady() throws Exception {
        var original = packedLine(50, 3000);
        var replacement = AffineTransformation.translationInstance(2, 0).transform(original);
        var view = plot(original, true, ThemeOption.ICE_DARK);
        try {
            awaitReady(view);
            fx(() -> {
                assertTrue(red(pixel(view, 422)));
                view.updateLayerGeometry("edit", replacement);
                assertTrue(red(pixel(view, 422)), "keep display preview during raster preparation");
                assertSame(replacement, view.visibleSelectableLayers().getFirst().geometry());
                assertTrue(((Label) view.lookup("#plot-preparing")).isVisible());
                return null;
            });
            awaitReady(view);
            fx(() -> {
                assertFalse(red(pixel(view, 422)));
                assertTrue(red(pixel(view, 428)));
                view.updateLayerGeometry("edit", original);
                assertTrue(red(pixel(view, 428)), "undo must not produce an empty frame");
                return null;
            });
            awaitReady(view);
            assertTrue(fx(() -> red(pixel(view, 422))));
            assertFalse(fx(() -> red(pixel(view, 428))));
        } finally { fx(() -> { view.dispose(); return null; }); }
    }

    @Test void returningFromVectorZoomReusesTheDensityFrameImmediatelyInEveryTheme() throws Exception {
        for (ThemeOption theme : ThemeOption.values()) {
            var view = plot(packedLine(50, 3000), true, theme);
            try {
                awaitReady(view);
                fx(() -> {
                    assertTrue(red(pixel(view, 422)));
                    scale(view, 10); // Average segment size leaves density mode.
                    scale(view, 3);
                    assertTrue(red(pixel(view, 422)), "returning zoom must not wait for a new raster");
                    assertFalse(((Label) view.lookup("#plot-preparing")).isVisible());
                    return null;
                });
            } finally { fx(() -> { view.dispose(); return null; }); }
        }
    }

    @Test void wideOverviewSurvivesACompletedCroppedDensityZoom() throws Exception {
        var geometry = new GeometryFactory().buildGeometry(List.of(packedLine(50, 15000), packedLine(100, 15000)));
        var view = plot(geometry, true, ThemeOption.ICE_DARK);
        try {
            awaitReady(view);
            fx(() -> {
                assertTrue(red(pixel(view, 572)), "far shape belongs to the wide camera frame");
                scale(view, 30); // Still dense, but the far shape is now outside the viewport.
                return null;
            });
            awaitReady(view);
            fx(() -> {
                scale(view, 3);
                assertTrue(red(pixel(view, 422)));
                assertTrue(red(pixel(view, 572)), "overview must restore areas outside the cropped frame immediately");
                assertFalse(((Label) view.lookup("#plot-preparing")).isVisible());
                assertEquals(1, overviewCount(view), "bounded to one overview per binding");
                return null;
            });
        } finally { fx(() -> { view.dispose(); return null; }); }
    }

    @Test void emptyGeometryAndClearingTheProjectNeverKeepAGhostPreview() throws Exception {
        var original = packedLine(50, 3000);
        var replacement = AffineTransformation.translationInstance(2, 0).transform(original);
        var view = plot(original, true, ThemeOption.ICE_DARK);
        try {
            awaitReady(view);
            fx(() -> {
                view.updateLayerGeometry("edit", replacement);
                view.updateLayerGeometry("edit", original.getFactory().createGeometryCollection());
                assertFalse(red(pixel(view, 422)));
                assertFalse(red(pixel(view, 428)));
                assertEquals(0, overviewCount(view));
                view.updateLayerGeometry("edit", original);
                view.clearLayers();
                assertFalse(red(pixel(view, 422)));
                assertTrue(view.visibleSelectableLayers().isEmpty());
                assertEquals(0, overviewCount(view));
                return null;
            });
            awaitReady(view);
            assertFalse(fx(() -> red(pixel(view, 422))));
        } finally { fx(() -> { view.dispose(); return null; }); }
    }

    @Test void supersededFramesCannotUndoTheLatestEditOrResurrectARemovedLayer() throws Exception {
        var original = packedLine(50, 3000);
        var intermediate = AffineTransformation.translationInstance(2, 0).transform(original);
        var newest = AffineTransformation.translationInstance(4, 0).transform(original);
        var view = plot(original, true, ThemeOption.ICE_DARK);
        try {
            awaitReady(view);
            fx(() -> {
                view.updateLayerGeometry("edit", intermediate);
                view.updateLayerGeometry("edit", newest);
                assertTrue(red(pixel(view, 422)));
                assertSame(newest, view.visibleSelectableLayers().getFirst().geometry());
                return null;
            });
            awaitReady(view);
            fx(() -> {
                assertFalse(red(pixel(view, 428)), "intermediate revision must not be published");
                assertTrue(red(pixel(view, 434)));
                view.updateLayerGeometry("edit", original);
                view.setLayerVisible("edit", false);
                assertFalse(red(pixel(view, 434)), "hidden layers must not show a preview");
                view.removeLayer("edit");
                assertEquals(0, overviewCount(view));
                return null;
            });
            awaitReady(view);
            assertFalse(fx(() -> red(pixel(view, 422))));
            assertFalse(fx(() -> red(pixel(view, 434))));
        } finally { fx(() -> { view.dispose(); return null; }); }
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
