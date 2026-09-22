package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the G70/G71/G91 handling fixed after the Fase 0 baseline: previously
 * these three codes were silently ignored (IGNORABLE_EXACT), which let a
 * metric-only file declaring units solely via G71 (no %MO) fall back to the
 * "IN" default, and let a G91 (incremental) file be mis-decoded as absolute
 * coordinates without any error. See GerberParser's IGNORABLE_EXACT comment
 * and its G70/G71/G91 handling above the DATA_LINE match.
 */
class GerberUnitAndCoordinateModeTest {

    private static final List<String> HEADER = List.of(
            "%FSLAX23Y23*%",
            "%ADD10C,0.010*%"
    );

    private static List<String> withHeader(String... extra) {
        List<String> lines = new java.util.ArrayList<>(HEADER);
        for (String line : extra) {
            lines.add(line);
        }
        lines.add("D10*");
        lines.add("X001000Y001000D03*");
        lines.add("M02*");
        return lines;
    }

    @Test
    void g71SetsMillimeterUnitsWithoutMO() {
        GerberImage image = new GerberParser().parse(withHeader("G71*"));
        assertEquals("MM", image.units());
    }

    @Test
    void g70SetsInchUnitsWithoutMO() {
        GerberImage image = new GerberParser().parse(withHeader("G70*"));
        assertEquals("IN", image.units());
    }

    @Test
    void g71AgreesWithMatchingMOLine() {
        GerberImage image = new GerberParser().parse(withHeader("%MOMM*%", "G71*"));
        assertEquals("MM", image.units());
    }

    @Test
    void g71ConflictingWithMOLineIsRejected() {
        GerberParseException ex = assertThrows(GerberParseException.class,
                () -> new GerberParser().parse(withHeader("%MOIN*%", "G71*")));
        assertEquals(true, ex.getMessage().contains("Unit mismatch"));
    }

    @Test
    void g91IncrementalModeIsRejectedRatherThanMisdecoded() {
        GerberParseException ex = assertThrows(GerberParseException.class,
                () -> new GerberParser().parse(withHeader("G91*")));
        assertEquals(true, ex.getMessage().contains("Incremental"));
    }
}
