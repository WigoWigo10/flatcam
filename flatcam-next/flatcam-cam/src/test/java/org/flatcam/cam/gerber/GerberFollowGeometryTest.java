package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

class GerberFollowGeometryTest {

    private final GerberParser parser = new GerberParser();

    @Test
    void retainsLinearDrawCenterlinesAndFlashCenters() {
        GerberImage image = parser.parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,0.100*%", "D10*",
                "X0Y0D02*", "X1000Y0D01*", "X1000Y1000D01*", "X2000Y2000D03*", "M02*"));

        Geometry follow = image.followGeometry();
        assertEquals(3, follow.getNumGeometries());
        assertEquals(0.2, follow.getLength(), 1e-9);
        assertEquals(2, countParts(follow, LineString.class));
        assertEquals(1, countParts(follow, Point.class));
    }

    @Test
    void retainsArcCenterlineInsteadOfItsBufferedCopper() {
        GerberImage image = parser.parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,0.100*%", "D10*", "G75*",
                "X10000Y0D02*", "G03X0Y10000I-10000J0D01*", "M02*"));

        Geometry arc = image.followGeometry().getGeometryN(0);
        assertTrue(arc instanceof LineString);
        assertTrue(arc.getNumPoints() > 2);
        assertEquals(Math.PI / 2, arc.getLength(), 0.001);
    }

    @Test
    void retainsClosedRegionBoundary() {
        GerberImage image = parser.parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "G36*", "X0Y0D02*",
                "X1000Y0D01*", "X1000Y1000D01*", "X0Y1000D01*", "X0Y0D01*", "G37*", "M02*"));

        Geometry boundary = image.followGeometry().getGeometryN(0);
        assertTrue(boundary instanceof LineString);
        assertTrue(((LineString) boundary).isClosed());
        assertEquals(0.4, boundary.getLength(), 1e-9);
    }

    private static int countParts(Geometry geometry, Class<? extends Geometry> type) {
        int count = 0;
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            if (type.isInstance(geometry.getGeometryN(i))) {
                count++;
            }
        }
        return count;
    }
}
