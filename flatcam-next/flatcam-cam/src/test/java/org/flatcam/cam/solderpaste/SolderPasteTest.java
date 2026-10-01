package org.flatcam.cam.solderpaste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

class SolderPasteTest {

    private static Geometry wkt(String text) throws ParseException {
        return new WKTReader().read(text);
    }

    /** A 3 x 1 pad, a 1 x 1 pad and a 0.2 x 0.2 pad; the last one is too small for a 0.3 nozzle. */
    private Geometry mask() throws ParseException {
        return wkt("GEOMETRYCOLLECTION(MULTIPOLYGON(((0 0, 3 0, 3 1, 0 1, 0 0)), ((10 0, 11 0, 11 1, 10 1, 10 0))),"
                + " POLYGON((20 0, 20.2 0, 20.2 0.2, 20 0.2, 20 0)))");
    }

    @Test
    void padsGetOneLineEachFromTheWidestNozzleThatFits() throws Exception {
        SolderPaste.Generated generated = SolderPaste.generateGeometry(mask(), List.of(0.3, 1.2), "MM");
        assertEquals(3, generated.pads());
        assertEquals(1, generated.unserved());
        // 1.2 nozzle: nothing fits (the pads are at most 1 high); 0.3 serves the 3x1 and the 1x1 pads.
        assertEquals(1, generated.tools().size());
        assertEquals(0.3, generated.tools().get(0).toolDiameter(), 1e-9);
        Geometry lines = generated.tools().get(0).geometry();
        assertEquals(2, lines.getNumGeometries());
        // 3 x 1 pad: a horizontal line at y = 0.5 from x = 0.15 to 2.85 (shrunk by the nozzle radius).
        Geometry horizontal = lines.getGeometryN(0);
        assertEquals(2.7, horizontal.getLength(), 1e-6);
        assertEquals(0.5, horizontal.getEnvelopeInternal().getMinY(), 1e-6);
        // 1 x 1 pad: width == height, so a vertical line at x = 10.5, 0.7 long.
        Geometry vertical = lines.getGeometryN(1);
        assertEquals(0.7, vertical.getLength(), 1e-6);
        assertEquals(10.5, vertical.getEnvelopeInternal().getMinX(), 1e-6);
    }

    @Test
    void widerNozzlesServeThePadsTheyFitAndNarrowOnesTheRest() throws Exception {
        Geometry pads = wkt("MULTIPOLYGON(((0 0, 4 0, 4 2, 0 2, 0 0)), ((10 0, 10.6 0, 10.6 0.5, 10 0.5, 10 0)))");
        SolderPaste.Generated generated = SolderPaste.generateGeometry(pads, List.of(0.2, 1.0), "MM");
        assertEquals(2, generated.tools().size());
        assertEquals(1.0, generated.tools().get(0).toolDiameter(), 1e-9);
        assertEquals(0.2, generated.tools().get(1).toolDiameter(), 1e-9);
        assertEquals(0, generated.unserved());
        assertThrows(IllegalArgumentException.class, () -> SolderPaste.generateGeometry(pads, List.of(), "MM"));
        assertThrows(IllegalArgumentException.class, () -> SolderPaste.generateGeometry(pads, List.of(5.0), "MM"));
    }

    @Test
    void gcodeFollowsPaste1AndVisitsThePathsNearestFirst() throws Exception {
        SolderPaste.Generated generated = SolderPaste.generateGeometry(mask(), List.of(0.3), "MM");
        SolderPaste.Program program = SolderPaste.generateGCode(generated.tools(), SolderPaste.defaults(), "MM",
                new double[] {0, 0, 11, 1});
        String gcode = program.gcode();
        assertTrue(gcode.startsWith("(TOOL DIAMETER: 0.3 mm)"), gcode);
        assertTrue(gcode.contains("(Preprocessor SolderPaste Dispensing Geometry: Paste_1)"));
        assertTrue(gcode.contains("G21\nG90\nG94\n") && gcode.contains("T1\nM6\n(MSG, Change to Tool with Nozzle Dia = 0.3000)"));
        assertEquals(2, program.paths().size());
        // One dispense cycle per path: start Z, dwell, dispense Z, stop Z.
        assertEquals(2, gcode.split("M03 S300.0", -1).length - 1);
        assertTrue(gcode.contains("G00 X0.1500 Y0.5000\nG00 Z0.1000\nG01 F150.00\nG01 Z0.0500\nM03 S300.0\nG4 P1.0\n"
                + "G01 F1.00\nG01 Z0.1000\nG01 F150.00\nG01 X2.8500 Y0.5000\nM05\nM04 S200.0\nG01 Z0.0500\nM05\nG4 P1.0\n"), gcode);
        // The first path starts nearest the origin, the second after it.
        assertEquals(0.15, program.paths().get(0).getCoordinateN(0).x, 1e-9);
        assertEquals(10.5, program.paths().get(1).getCoordinateN(0).x, 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> SolderPaste.generateGCode(List.of(), SolderPaste.defaults(), "MM", null));
    }
}
