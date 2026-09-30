package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

class ParallelGeometryTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    /** Three far-apart islands of overlapping squares, enough shapes to take the parallel path. */
    private static List<Geometry> islands() {
        List<Geometry> shapes = new ArrayList<>();
        for (int island = 0; island < 3; island++) {
            for (int i = 0; i < 20; i++) {
                for (int j = 0; j < 10; j++) {
                    double x = island * 1000 + i * 1.5;
                    double y = j * 1.5;
                    shapes.add(FACTORY.toGeometry(new org.locationtech.jts.geom.Envelope(x, x + 2, y, y + 2)));
                }
            }
        }
        return shapes;
    }

    @Test
    void groupedUnionEqualsTheSingleUnion() {
        List<Geometry> shapes = islands();
        Geometry expected = OverlayNGRobust.union(shapes);
        Geometry actual = ParallelGeometry.union(shapes);
        assertEquals(expected.getArea(), actual.getArea(), 1e-6);
        assertEquals(expected.getNumGeometries(), actual.getNumGeometries());
        assertTrue(expected.symDifference(actual).getArea() < 1e-9);
    }

    @Test
    void separatesOnlyPartsThatCannotTouch() {
        Geometry close = FACTORY.buildGeometry(List.of(
                FACTORY.toGeometry(new org.locationtech.jts.geom.Envelope(0, 1, 0, 1)),
                FACTORY.toGeometry(new org.locationtech.jts.geom.Envelope(1.5, 2.5, 0, 1)),
                FACTORY.toGeometry(new org.locationtech.jts.geom.Envelope(50, 51, 0, 1))));
        assertEquals(2, ParallelGeometry.separateGroups(close, 1.0).size());
        assertEquals(3, ParallelGeometry.separateGroups(close, 0.1).size());
    }
}
