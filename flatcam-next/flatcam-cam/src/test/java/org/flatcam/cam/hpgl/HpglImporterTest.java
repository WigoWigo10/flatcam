package org.flatcam.cam.hpgl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

class HpglImporterTest {

    @Test
    void penDownMovesWithCoordinatesAreDrawnPerPenInFortiethsOfAMillimetre() {
        HpglImporter.Result result = HpglImporter.parse(
                "IN;SP1;PU0,0;PD400,0,400,400;PU;SP2;PA800,0;PD;PA1200,0;PU;", "MM");

        assertEquals(List.of(1, 2), List.copyOf(result.pens().keySet()));
        assertEquals(20, result.pens().get(1).getLength(), 1e-9, "400 + 400 plotter units = 20 mm");
        assertEquals(10, result.pens().get(2).getLength(), 1e-9);
        assertEquals(10, result.pens().get(2).getEnvelopeInternal().getMaxX() - 20, 1e-9);
    }

    @Test
    void commandsMayShareLinesAndRelativeMovesAccumulate() {
        HpglImporter.Result result = HpglImporter.parse("IN SP1 PU 40 40 PR PD 40 0 0 40 -40 0 0 -40 PU PA 0 0",
                "MM");

        LineString square = (LineString) result.pens().get(1).getGeometryN(0);
        assertTrue(square.isClosed());
        assertEquals(4, square.getLength(), 1e-9);
        Envelope box = square.getEnvelopeInternal();
        assertEquals(1, box.getMinX(), 1e-9);
        assertEquals(2, box.getMaxY(), 1e-9);
    }

    @Test
    void circlesAndArcsUsePlotterUnitsAndKeepThePosition() {
        HpglImporter.Result result = HpglImporter.parse(
                "IN;SP1;PA400,400;CI200;PU0,0;PD;AA0,400,90;PD400,400;PU;", "MM");

        Geometry pen = result.pens().get(1);
        assertEquals(2, pen.getNumGeometries());
        LineString first = (LineString) pen.getGeometryN(0);
        LineString circle = first.isClosed() ? first : (LineString) pen.getGeometryN(1);
        LineString arc = first.isClosed() ? (LineString) pen.getGeometryN(1) : first;
        assertEquals(5, circle.getEnvelopeInternal().getWidth() / 2, 1e-3, "CI200 is a 5 mm radius (Python used 200 mm)");
        assertEquals(10, circle.getCentroid().getX(), 1e-6);
        // The quarter arc around (0,10 mm) from the origin ends at (10,10) mm.
        assertEquals(10, arc.getEndPoint().getX(), 1e-9);
        assertEquals(10, arc.getEndPoint().getY(), 1e-9);
        assertEquals(Math.PI * 10 / 2, arc.getLength(), 0.01);
    }

    @Test
    void threePointArcsPassThroughTheIntermediatePoint() {
        HpglImporter.Result result = HpglImporter.parse("IN;SP1;PU0,0;PD;AT400,400,800,0;PU;", "MM");

        Envelope box = result.pens().get(1).getEnvelopeInternal();
        assertEquals(10, box.getMaxY(), 1e-3, "a half circle over the 20 mm chord");
        assertEquals(20, box.getMaxX(), 1e-9);
    }

    @Test
    void escapesCommentsLabelsAndInchOutputAreHandled() {
        HpglImporter.Result result = HpglImporter.parse("\u001b%-1BIN;CO\"made by X\";SP1;PU0,0;LBHELLO; PD\u0003;"
                + "PD1016,0;PU;\u001b%0A", "IN");

        assertEquals(1, result.skippedLabels());
        assertEquals(1, result.pens().get(1).getLength(), 1e-9, "1016 plotter units = 25.4 mm = 1 in");
    }

    @Test
    void filesWithoutDrawingAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> HpglImporter.parse("IN;SP1;PU100,100;", "MM"));
        assertThrows(IllegalArgumentException.class, () -> HpglImporter.parse("hello world", "MM"));
    }
}
