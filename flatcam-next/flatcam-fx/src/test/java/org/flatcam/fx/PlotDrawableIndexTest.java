package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class PlotDrawableIndexTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test
    void nestedCollectionsKeepOriginalPartOrderWhenViewportCulls() {
        Point first = point(0);
        Point second = point(10);
        Point third = point(20);
        Geometry nested = factory.createGeometryCollection(new Geometry[]{
                first, factory.createMultiPoint(new Point[]{second, third})});
        PlotDrawableIndex index = new PlotDrawableIndex(nested);

        List<PlotDrawableIndex.Part> visible = index.visibleParts(new Envelope(9, 21, -1, 1));
        assertEquals(List.of(1, 2), visible.stream().map(PlotDrawableIndex.Part::index).toList());
        assertSame(second, visible.get(0).geometry());
        assertSame(third, visible.get(1).geometry());
        assertEquals(3, index.visibleParts(null).size());
    }

    @Test
    void spatialTreeReturnsOnlyVisiblePartsInDrawOrder() {
        Point[] points = new Point[300];
        for (int i = 0; i < points.length; i++) {
            points[i] = point(i);
        }
        PlotDrawableIndex index = new PlotDrawableIndex(factory.createMultiPoint(points));

        List<PlotDrawableIndex.Part> visible = index.visibleParts(new Envelope(180, 185, -1, 1));
        assertEquals(List.of(180, 181, 182, 183, 184, 185),
                visible.stream().map(PlotDrawableIndex.Part::index).toList());
        assertEquals(300, index.visibleParts(new Envelope(-1, 301, -1, 1)).size());
    }

    private Point point(double x) {
        return factory.createPoint(new Coordinate(x, 0));
    }
}
