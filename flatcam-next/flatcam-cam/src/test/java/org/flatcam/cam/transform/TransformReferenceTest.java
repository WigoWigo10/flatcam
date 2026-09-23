package org.flatcam.cam.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

class TransformReferenceTest {

    @Test
    void originIsAlwaysZeroZero() {
        Coordinate origin = TransformReference.origin();
        assertEquals(0, origin.x);
        assertEquals(0, origin.y);
    }

    @Test
    void selectionCenterIsTheMidpointOfOneBoundsArray() {
        Coordinate center = TransformReference.selectionCenter(List.of(new double[]{0, 0, 10, 4}));
        assertEquals(5, center.x, 1e-9);
        assertEquals(2, center.y, 1e-9);
    }

    @Test
    void selectionCenterCombinesMultipleBoundsIntoOneEnclosingBox() {
        Coordinate center = TransformReference.selectionCenter(List.of(
                new double[]{0, 0, 2, 2},
                new double[]{8, 8, 10, 10}));
        // Combined bounds: (0,0)-(10,10) -> center (5,5), NOT the average of the two individual centers.
        assertEquals(5, center.x, 1e-9);
        assertEquals(5, center.y, 1e-9);
    }

    @Test
    void selectionCenterIgnoresNullEntriesFromObjectsWithNoGeometry() {
        Coordinate center = TransformReference.selectionCenter(java.util.Arrays.asList(
                new double[]{0, 0, 10, 10}, null));
        assertEquals(5, center.x, 1e-9);
        assertEquals(5, center.y, 1e-9);
    }

    @Test
    void selectionCenterRejectsAnEmptyOrAllNullList() {
        assertThrows(IllegalArgumentException.class, () -> TransformReference.selectionCenter(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TransformReference.selectionCenter(java.util.Collections.singletonList(null)));
    }
}
