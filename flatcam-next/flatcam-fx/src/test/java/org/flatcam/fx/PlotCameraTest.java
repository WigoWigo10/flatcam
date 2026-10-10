package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import javafx.scene.paint.Color;

class PlotCameraTest {
    @Test void transformRoundTripsWithInsetsAndBothUnitScales() {
        for (double unit : new double[]{1, 1 / 25.4}) {
            var camera = new PlotCamera(-30 * unit, 80 * unit, 12 / unit, 1200, 700, 44, 20);
            for (double x : new double[]{-45, 0, 125}) for (double y : new double[]{-10, 80, 200}) {
                assertEquals(x * unit, camera.worldX(camera.screenX(x * unit)), 1e-12);
                assertEquals(y * unit, camera.worldY(camera.screenY(y * unit)), 1e-12);
            }
            assertEquals(644, camera.screenX(camera.centerX()));
            assertEquals(370, camera.screenY(camera.centerY()));
            assertTrue(camera.visibleBounds().contains(camera.centerX(), camera.centerY()));
        }
    }

    @Test void boundsHaveTheOriginalStrokeMarginAndCannotMutateTheCamera() {
        var camera = new PlotCamera(0, 0, 10, 100, 100, 44, 20);
        assertEquals(new Envelope(-5.2, 5.2, -5.2, 5.2), camera.visibleBounds());
        camera.visibleBounds().expandBy(900);
        assertEquals(5.2, camera.visibleBounds().getMaxX());
        assertThrows(IllegalArgumentException.class, () -> new PlotCamera(0, 0, 0, 100, 100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlotCamera(Double.NaN, 0, 1, 100, 100, 0, 0));
    }

    @Test void snapshotBorrowsAnExplicitGeometryVersionAndOwnsBounds() {
        var geometry = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 10));
        var bounds = new Envelope(0, 20, 0, 20);
        var index = new PlotDrawableIndex(geometry);
        var snapshot = new PlotRenderSnapshot(geometry, index, false, true, false, Color.RED, Color.BLACK, 1, bounds);
        assertSame(geometry, snapshot.geometry());
        bounds.expandBy(100);
        snapshot.bounds().expandBy(50);
        assertEquals(20, snapshot.bounds().getMaxX());
        assertThrows(IllegalArgumentException.class, () -> new PlotRenderSnapshot(geometry.copy(), index,
                false, true, false, Color.RED, Color.BLACK, 1, bounds));
    }
}
