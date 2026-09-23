package org.flatcam.cam.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class TransformOpTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final double EPS = 1e-9;

    @Test
    void rotateIsCounterClockwiseAboutThePivotForAPositiveAngle() {
        // (1, 0) rotated 90 CCW about the origin lands on (0, 1) - the raw
        // math convention; UIs presenting "positive = clockwise" must negate
        // the angle themselves before building a Rotate, per the class doc.
        TransformOp op = new TransformOp.Rotate(90, new Coordinate(0, 0));
        Coordinate result = op.apply(new Coordinate(1, 0));
        assertEquals(0, result.x, EPS);
        assertEquals(1, result.y, EPS);
    }

    @Test
    void rotateAboutAnArbitraryPivotMatchesTranslateRotateTranslateBack() {
        TransformOp op = new TransformOp.Rotate(180, new Coordinate(5, 5));
        Coordinate result = op.apply(new Coordinate(6, 5));
        // 180 about (5,5): (6,5) is 1 unit right of the pivot, so it lands 1 unit left of it.
        assertEquals(4, result.x, EPS);
        assertEquals(5, result.y, EPS);
    }

    @Test
    void scaleAppliesIndependentFactorsAboutThePivot() {
        TransformOp op = new TransformOp.Scale(2, 3, new Coordinate(1, 1));
        Coordinate result = op.apply(new Coordinate(2, 2));
        // 1 unit right/up of the pivot becomes 2 units right, 3 units up.
        assertEquals(3, result.x, EPS);
        assertEquals(4, result.y, EPS);
    }

    @Test
    void mirrorXNegatesTheXCoordinateAboutThePivot() {
        TransformOp op = new TransformOp.MirrorX(new Coordinate(10, 0));
        Coordinate result = op.apply(new Coordinate(12, 7));
        assertEquals(8, result.x, EPS);
        assertEquals(7, result.y, EPS, "mirroring X must leave Y untouched");
    }

    @Test
    void mirrorYNegatesTheYCoordinateAboutThePivot() {
        TransformOp op = new TransformOp.MirrorY(new Coordinate(0, 10));
        Coordinate result = op.apply(new Coordinate(3, 12));
        assertEquals(3, result.x, EPS, "mirroring Y must leave X untouched");
        assertEquals(8, result.y, EPS);
    }

    @Test
    void skewXShiftsXByYTimesTanAngleAndLeavesYUntouched() {
        // 45 degrees -> shear factor of exactly 1; an asymmetric point (x != y)
        // rules out X/Y getting silently swapped in the shear matrix.
        TransformOp op = new TransformOp.Skew(45, 0, new Coordinate(0, 0));
        Coordinate result = op.apply(new Coordinate(3, 7));
        assertEquals(10, result.x, EPS, "x' = x + y*tan(angleX) = 3 + 7*1");
        assertEquals(7, result.y, EPS, "skewX alone must not move Y");
    }

    @Test
    void skewYShiftsYByXTimesTanAngleAndLeavesXUntouched() {
        TransformOp op = new TransformOp.Skew(0, 45, new Coordinate(0, 0));
        Coordinate result = op.apply(new Coordinate(3, 7));
        assertEquals(3, result.x, EPS, "skewY alone must not move X");
        assertEquals(10, result.y, EPS, "y' = y + x*tan(angleY) = 7 + 3*1");
    }

    @Test
    void skewAboutAnArbitraryPivotShiftsRelativeToThatPoint() {
        TransformOp op = new TransformOp.Skew(45, 0, new Coordinate(5, 5));
        // Relative to the pivot this point is (0, 2); skewX moves it to (2, 2) relative,
        // i.e. (7, 7) absolute.
        Coordinate result = op.apply(new Coordinate(5, 7));
        assertEquals(7, result.x, EPS);
        assertEquals(7, result.y, EPS);
    }

    @Test
    void offsetIsAPlainTranslateIndependentOfAnyPivot() {
        TransformOp op = new TransformOp.Offset(3, -4);
        Coordinate result = op.apply(new Coordinate(1, 1));
        assertEquals(4, result.x, EPS);
        assertEquals(-3, result.y, EPS);
    }

    @Test
    void geometryAndCoordinateOverloadsAgree() {
        TransformOp op = new TransformOp.Rotate(90, new Coordinate(0, 0));
        Geometry point = FACTORY.createPoint(new Coordinate(1, 0));
        Geometry rotated = op.apply(point);
        Envelope envelope = rotated.getEnvelopeInternal();
        assertEquals(0, envelope.getMinX(), EPS);
        assertEquals(1, envelope.getMinY(), EPS);
    }

    @Test
    void everyPivotBasedOpRejectsANullPivot() {
        assertThrows(NullPointerException.class, () -> new TransformOp.Rotate(10, null));
        assertThrows(NullPointerException.class, () -> new TransformOp.Scale(2, 2, null));
        assertThrows(NullPointerException.class, () -> new TransformOp.Skew(10, 10, null));
        assertThrows(NullPointerException.class, () -> new TransformOp.MirrorX(null));
        assertThrows(NullPointerException.class, () -> new TransformOp.MirrorY(null));
    }
}
