package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class GCodeToolpathParserTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void parsesGeneratedStyleRapidCutAndDrillWithProgress() {
        List<Double> progress = new ArrayList<>();
        GCodeToolpathParser.Result result = GCodeToolpathParser.parse("""
                ; generated
                G21
                G90
                G94
                G0 Z2
                G0 X1 Y1
                G1 Z-0.2 F100
                G1 X3 Y1
                G0 Z2
                G0 X4 Y1
                M2
                """, () -> false, progress::add);
        assertTrue(result.plotAvailable());
        assertEquals(11, result.lineCount());
        assertTrue(anyPartCovers(result.cutGeometry(), 2, 1));
        assertTrue(anyPartCovers(result.travelGeometry(), 3.5, 1));
        assertEquals(0, progress.get(0));
        assertEquals(1, progress.get(progress.size() - 1));
    }

    @Test
    void relativeMovesAndUnsupportedArcsNeverShowStalePlot() {
        GCodeToolpathParser.Result relative = GCodeToolpathParser.parse("""
                G0 X1 Y1
                G91
                G1 Z-1
                G1 X2 Y0
                """, () -> false, ignored -> {});
        assertTrue(anyPartCovers(relative.cutGeometry(), 2, 1));
        GCodeToolpathParser.Result arc = GCodeToolpathParser.parse("G0 X0 Y0\nG2 X1 Y1 I0 J1\n",
                () -> false, ignored -> {});
        assertFalse(arc.plotAvailable());
        assertEquals(null, arc.cutGeometry());
    }

    @Test
    void invalidTextAndCancellationAreReported() {
        assertThrows(IllegalArgumentException.class,
                () -> GCodeToolpathParser.parse("G1 XABC", () -> false, ignored -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> GCodeToolpathParser.parse(" ", () -> false, ignored -> {}));
        assertThrows(CancellationException.class,
                () -> GCodeToolpathParser.parse("G0 X1", () -> true, ignored -> {}));
    }

    private static boolean anyPartCovers(Geometry geometry, double x, double y) {
        for (int index = 0; index < geometry.getNumGeometries(); index++) {
            if (geometry.getGeometryN(index).covers(FACTORY.createPoint(new Coordinate(x, y)))) {
                return true;
            }
        }
        return false;
    }
}
