package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Compatibility cases accepted by the legacy ParseGerber.py parser. */
class GerberCompatibilityTest {

    @Test
    void decodesTrailingZeroSuppression() {
        GerberImage image = new GerberParser().parse(List.of(
                "%FSTAX24Y24*%", "%MOIN*%", "%ADD10C,0.010*%", "D10*", "X1Y2D03*", "M02*"));

        assertEquals(9.995, image.bounds()[0], 1e-6);
        assertEquals(19.995, image.bounds()[1], 1e-6);
        assertEquals(10.005, image.bounds()[2], 1e-6);
        assertEquals(20.005, image.bounds()[3], 1e-6);
    }

    @Test
    void supportsIncrementalNotationDeclaredByFormat() {
        GerberImage image = new GerberParser().parse(List.of(
                "%FSLIX24Y24*%", "%MOIN*%", "%ADD10C,0.010*%", "D10*",
                "X10000Y10000D03*", "X10000Y0D03*", "M02*"));

        assertEquals(2.005, image.bounds()[2], 1e-6);
    }

    @Test
    void splitsConcatenatedStandardAndExtendedCommandsAndKeepsDCodeModal() {
        GerberImage image = new GerberParser().parse(List.of(
                "%FSLAX24Y24*MOIN*%",
                "%ADD10C,0.010*%",
                "D10*X10000Y10000D03*X20000Y10000*",
                "M02*"));

        assertEquals("IN", image.units());
        assertEquals(0.995, image.bounds()[0], 1e-6);
        assertEquals(2.005, image.bounds()[2], 1e-6);
    }

    @Test
    void flashesRegularPolygonAperture() {
        GerberImage image = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOIN*%", "%ADD10P,2.0X4X0*%", "D10*", "X0Y0D03*", "M02*"));

        Aperture aperture = image.apertures().get("10");
        assertEquals(ApertureKind.POLYGON, aperture.kind);
        assertEquals(4, aperture.polygonVertices());
        assertEquals(2.0, image.totalArea(), 1e-6);
        assertTrue(image.bounds()[0] <= -1.0);
        assertTrue(image.bounds()[2] >= 1.0);
    }
}
