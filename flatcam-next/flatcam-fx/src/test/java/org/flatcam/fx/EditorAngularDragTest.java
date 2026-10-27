package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

class EditorAngularDragTest {
    @Test void signedAngleUnwrapsAcrossTheNegativeXAxisAndSupportsFullTurns() {
        var drag = new EditorAngularDrag(new Coordinate(2,3));
        assertTrue(drag.begin(point(170),.01));
        assertTrue(drag.update(point(-170),.01)); assertEquals(-20,drag.degrees(),1e-10);
        for (int angle : new int[]{-90,0,90,170}) drag.update(point(angle),.01);
        assertEquals(-360,drag.degrees(),1e-10);
        assertTrue(drag.begin(point(0),.01));
        drag.update(point(-90),.01); assertEquals(90,drag.degrees(),1e-10);
    }
    @Test void pivotAndInvalidPointsDoNotIntroduceAnAngleOrCorruptAnExistingDrag() {
        var drag = new EditorAngularDrag(new Coordinate(2,3));
        assertFalse(drag.begin(new Coordinate(2,3),.1)); assertFalse(drag.started());
        assertFalse(drag.update(point(0),.1));
        assertTrue(drag.begin(point(0),.1));
        assertFalse(drag.update(new Coordinate(2,3),.1));
        assertFalse(drag.update(new Coordinate(Double.NaN,0),.1));
        drag.update(point(90),.1); assertEquals(-90,drag.degrees(),1e-10);
        assertThrows(IllegalArgumentException.class,()->new EditorAngularDrag(new Coordinate(Double.NaN,0)));
    }
    private Coordinate point(double angle) {
        return new Coordinate(2+5*Math.cos(Math.toRadians(angle)),3+5*Math.sin(Math.toRadians(angle)));
    }
}
