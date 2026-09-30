package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

class DoubleSidedToolPanelTest {

    @Test
    void readsHolesOnePerLineOrInPythonsTupleForm() {
        assertEquals(List.of(new Coordinate(1, 2), new Coordinate(3.5, -4)),
                DoubleSidedToolPanel.parseHoles("1, 2\n3,5 -4"));
        assertEquals(List.of(new Coordinate(1, 2), new Coordinate(3, 4)),
                DoubleSidedToolPanel.parseHoles("(1, 2), (3, 4)"));
        assertEquals(List.of(new Coordinate(0.5, 10), new Coordinate(7, 8)),
                DoubleSidedToolPanel.parseHoles("[0.5, 10]; [7 8]"));
        assertEquals(List.of(), DoubleSidedToolPanel.parseHoles("  \n "));
        assertThrows(IllegalArgumentException.class, () -> DoubleSidedToolPanel.parseHoles("1, 2, 3"));
    }

    @Test
    void axisXNegatesYAndAxisYNegatesX() {
        Coordinate pivot = new Coordinate(10, 20);
        Coordinate point = new Coordinate(13, 25);
        TransformOp horizontalLine = DoubleSidedToolPanel.mirrorOp(true, pivot);
        Coordinate onX = horizontalLine.apply(point);
        assertEquals(13, onX.x, 1e-12);
        assertEquals(15, onX.y, 1e-12);
        Coordinate onY = DoubleSidedToolPanel.mirrorOp(false, pivot).apply(point);
        assertEquals(7, onY.x, 1e-12);
        assertEquals(25, onY.y, 1e-12);
    }

    @Test
    void boxPivotUsesTheCentreOrAnEdge() {
        double[] box = {0, 0, 60, 40};
        assertEquals(new Coordinate(30, 20), DoubleSidedToolPanel.boxPivot(box, 0));
        assertEquals(new Coordinate(0, 20), DoubleSidedToolPanel.boxPivot(box, 1));
        assertEquals(new Coordinate(60, 20), DoubleSidedToolPanel.boxPivot(box, 2));
        assertEquals(new Coordinate(30, 0), DoubleSidedToolPanel.boxPivot(box, 3));
        assertEquals(new Coordinate(30, 40), DoubleSidedToolPanel.boxPivot(box, 4));
    }
}
