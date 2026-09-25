package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.flatcam.cam.gerber.GerberShape;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class GerberEditorPlacementTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void previewIncludesAllSelectedDarkShapesButNotClearPolarity() {
        Geometry first = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        Geometry second = FACTORY.toGeometry(new Envelope(20, 30, 0, 10));
        Geometry clear = FACTORY.toGeometry(new Envelope(100, 110, 0, 10));

        var preview = GerberEditorController.placementPreview(List.of(
                new GerberShape("10", first, false),
                new GerberShape("11", second, false),
                new GerberShape("10", clear, true)));

        assertEquals(2, preview.geometries().size());
        assertNull(GerberEditorController.placementPreview(List.of(new GerberShape("10", clear, true))));
    }
}
