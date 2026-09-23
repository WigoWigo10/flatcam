package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

class ToolGeometryTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void transformedKeepsTheToolDiameterAndMovesOnlyTheGeometry() {
        ToolGeometry original = new ToolGeometry(0.5, FACTORY.toGeometry(new Envelope(0, 1, 0, 1)));

        ToolGeometry moved = original.transformed(new TransformOp.Offset(10, 0));

        assertEquals(0.5, moved.toolDiameter(), 1e-9);
        assertEquals(10, moved.geometry().getEnvelopeInternal().getMinX(), 1e-9);
        assertEquals(0, original.geometry().getEnvelopeInternal().getMinX(), 1e-9, "original must stay untouched");
    }
}
