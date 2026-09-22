package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApertureMacroCompatibilityTest {

    private final GerberParser parser = new GerberParser();

    @Test
    void evaluatesLocalVariablesAndCenterLinePrimitive() {
        GerberImage image = parseMacro("$2=$1/2*21,1,$1,$2,0,0,0*", "4");

        assertEquals(8.0, image.totalArea(), 0.01);
    }

    @Test
    void supportsVectorAndLowerLeftLinePrimitives() {
        GerberImage vector = parseMacro("2,1,1,-1,0,1,0,0*", "");
        GerberImage lowerLeft = parseMacro("22,1,2,1,-1,-2,0*", "");

        assertEquals(2.0, vector.totalArea(), 0.01);
        assertEquals(2.0, lowerLeft.totalArea(), 0.01);
        assertEquals(-1.0, lowerLeft.bounds()[0], 1e-9);
        assertEquals(-2.0, lowerLeft.bounds()[1], 1e-9);
    }

    @Test
    void supportsMoireAndThermalPrimitives() {
        GerberImage moire = parseMacro("6,0,0,4,0.3,0.2,3,0.2,5,0*", "");
        GerberImage thermal = parseMacro("7,0,0,4,2,0.3,0*", "");

        assertFalse(moire.isEmpty());
        assertFalse(thermal.isEmpty());
        assertTrue(moire.totalArea() > 0);
        assertTrue(thermal.totalArea() > 0);
        assertTrue(thermal.totalArea() < Math.PI * 3);
    }

    private GerberImage parseMacro(String macroBody, String modifiers) {
        String aperture = modifiers.isEmpty() ? "%ADD10TEST*%" : "%ADD10TEST," + modifiers + "*%";
        return parser.parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%AMTEST*" + macroBody + "%",
                aperture, "D10*", "X0Y0D03*", "M02*"));
    }
}
