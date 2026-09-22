package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class GerberGeometryGeneratorTest {

    private final GerberImage rectangle = new GerberParser().parse(List.of(
            "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));

    @Test
    void createsSquareBoundingBoxWithMargin() {
        Geometry result = GerberGeometryGenerator.boundingBox(rectangle, 0.5, false);

        assertEquals(-1.5, result.getEnvelopeInternal().getMinX(), 1e-9);
        assertEquals(1.5, result.getEnvelopeInternal().getMaxX(), 1e-9);
        assertEquals(-1.0, result.getEnvelopeInternal().getMinY(), 1e-9);
        assertEquals(1.0, result.getEnvelopeInternal().getMaxY(), 1e-9);
        assertEquals(6.0, result.getArea(), 1e-9);
    }

    @Test
    void createsRoundedBoundingBoxWithSmallerCornerArea() {
        Geometry rounded = GerberGeometryGenerator.boundingBox(rectangle, 0.5, true);
        Geometry square = GerberGeometryGenerator.boundingBox(rectangle, 0.5, false);

        assertEquals(square.getEnvelopeInternal(), rounded.getEnvelopeInternal());
        assertTrue(rounded.getArea() < square.getArea());
        assertTrue(rounded.getArea() > rectangle.totalArea());
    }

    @Test
    void createsNonCopperAreaInsideBoundary() {
        Geometry result = GerberGeometryGenerator.nonCopper(rectangle, 0.5, false);

        assertEquals(4.0, result.getArea(), 1e-9);
        // Their boundaries touch, but no copper area is included in the result.
        assertEquals(0.0, result.intersection(rectangle.solidGeometry()).getArea(), 1e-9);
    }
}
