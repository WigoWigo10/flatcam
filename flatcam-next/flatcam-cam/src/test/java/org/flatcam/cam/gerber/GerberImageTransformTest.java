package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

class GerberImageTransformTest {

    /** A 2x1mm rectangle aperture flashed at the origin -> bounds (-1,-0.5) to (1,0.5). */
    private final GerberImage rectangleAtOrigin = new GerberParser().parse(List.of(
            "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));

    @Test
    void offsetShiftsSolidAndApertureGeometryTogether() {
        GerberImage moved = rectangleAtOrigin.transformed(new TransformOp.Offset(10, 20));

        Envelope solid = moved.solidGeometry().getEnvelopeInternal();
        assertEquals(9, solid.getMinX(), 1e-9);
        assertEquals(11, solid.getMaxX(), 1e-9);
        assertEquals(19.5, solid.getMinY(), 1e-9);
        assertEquals(20.5, solid.getMaxY(), 1e-9);

        for (Geometry apertureShape : moved.apertureGeometry().values()) {
            Envelope apEnv = apertureShape.getEnvelopeInternal();
            assertEquals(9, apEnv.getMinX(), 1e-9, "aperture geometry must move in lockstep with solidGeometry");
        }
        // The units/apertures map itself is untouched by a pure offset.
        assertEquals(rectangleAtOrigin.units(), moved.units());
        assertEquals(rectangleAtOrigin.apertures().keySet(), moved.apertures().keySet());
    }

    @Test
    void mirrorXNegatesOnlyTheXAxisOfEveryGeometry() {
        GerberImage mirrored = rectangleAtOrigin.transformed(new TransformOp.MirrorX(new Coordinate(5, 0)));

        Envelope original = rectangleAtOrigin.solidGeometry().getEnvelopeInternal();
        Envelope mirroredBounds = mirrored.solidGeometry().getEnvelopeInternal();
        // Mirrored about x=5: a point at x=-1 (1 left of... actually pivot is x=5, so -1 is 6 left -> lands at 11).
        assertEquals(10 - original.getMaxX(), mirroredBounds.getMinX(), 1e-9);
        assertEquals(10 - original.getMinX(), mirroredBounds.getMaxX(), 1e-9);
        assertEquals(original.getMinY(), mirroredBounds.getMinY(), 1e-9, "mirroring X must not move Y");
        assertEquals(original.getMaxY(), mirroredBounds.getMaxY(), 1e-9, "mirroring X must not move Y");
    }

    @Test
    void originalImageIsNeverMutated() {
        Envelope before = rectangleAtOrigin.solidGeometry().getEnvelopeInternal();
        rectangleAtOrigin.transformed(new TransformOp.Offset(100, 100));
        Envelope after = rectangleAtOrigin.solidGeometry().getEnvelopeInternal();
        assertTrue(before.equals(after), "transformed() must return a copy, not mutate the receiver");
    }
}
