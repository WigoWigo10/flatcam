package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class ExcellonParserRoutingTest {

    private final ExcellonParser parser = new ExcellonParser();

    @Test
    void readsFlatcamPythonRoutedSlotsAndReturnsToDrilling() {
        // FlatCAM Python's default Excellon export ("routing" slots), zero-suppressed with leading zeros.
        ExcellonImage image = parser.parse(List.of(
                "M48", ";FILE_FORMAT=2:4", "INCH,LZ", "T01F00S00C0.0394", "T02F00S00C0.0472", "%",
                "T01", "X001000Y002000",
                "G05", "T02",
                "G00X003000Y002000", "M15", "G01X005000Y002000", "M16",
                "G05", "X006000Y001000",
                "M30"));

        assertEquals("IN", image.units());
        assertEquals(List.of(new ExcellonImage.Drill(1, 0.1, 0.2), new ExcellonImage.Drill(2, 0.6, 0.1)),
                image.drills(), "G05 returns to drilling, so the last hit is a hole, not a move");
        assertEquals(List.of(new ExcellonImage.Slot(2, 0.3, 0.2, 0.5, 0.2)), image.slots());
        assertTrue(image.solidGeometry().covers(new GeometryFactory().createPoint(new Coordinate(0.4, 0.2))));
    }

    @Test
    void everyPlungedLinearMoveIsASlotAndRapidsAreNot() {
        ExcellonImage image = parser.parse(List.of(
                "M48", "METRIC", "T1C1.0", "%", "T1",
                "G00X0.0Y0.0", "M15", "G01X10.0Y0.0", "X10.0Y5.0", "M17",
                "G00X20.0Y0.0", "G01X25.0Y0.0",
                "M30"));

        assertEquals(List.of(
                new ExcellonImage.Slot(1, 0, 0, 10, 0),
                new ExcellonImage.Slot(1, 10, 0, 10, 5)), image.slots(),
                "a bare coordinate repeats G01 while plunged; a G01 after M17 only moves");
        assertTrue(image.drills().isEmpty());
    }

    @Test
    void plungeOutsideARouteIsRejected() {
        assertThrows(ExcellonParseException.class, () -> parser.parse(List.of(
                "M48", "METRIC", "T1C1.0", "%", "T1", "M15", "X1.0Y1.0")));
    }
}
