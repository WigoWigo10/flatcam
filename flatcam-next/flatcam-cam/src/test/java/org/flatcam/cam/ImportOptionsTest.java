package org.flatcam.cam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParseException;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;

/** The import options only decide what a file leaves unsaid; whatever the file declares still wins. */
class ImportOptionsTest {

    private static final List<String> EXCELLON_WITHOUT_UNITS = List.of("M48", "T1C0.8", "%", "T1", "X10000Y20000", "M30");

    @Test
    void excellonWithoutUnitsIsRefusedUnlessADefaultIsGiven() {
        assertThrows(ExcellonParseException.class, () -> new ExcellonParser().parse(EXCELLON_WITHOUT_UNITS));
        ExcellonImage inches = new ExcellonParser(new ExcellonParser.Options("IN", 4, 3)).parse(EXCELLON_WITHOUT_UNITS);
        assertEquals("IN", inches.units());
        assertEquals(1.0, inches.drills().get(0).x(), 1e-12);
        assertEquals(2.0, inches.drills().get(0).y(), 1e-12);
        ExcellonImage millimetres = new ExcellonParser(new ExcellonParser.Options("MM", 4, 3))
                .parse(EXCELLON_WITHOUT_UNITS);
        assertEquals("MM", millimetres.units());
        assertEquals(10.0, millimetres.drills().get(0).x(), 1e-12);
    }

    @Test
    void excellonDecimalDigitsApplyOnlyToCoordinatesWithoutAPoint() {
        List<String> metric = List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X10000Y2.5", "M30");
        ExcellonImage three = new ExcellonParser().parse(metric);
        assertEquals(10.0, three.drills().get(0).x(), 1e-12);
        ExcellonImage two = new ExcellonParser(new ExcellonParser.Options(null, 4, 2)).parse(metric);
        assertEquals(100.0, two.drills().get(0).x(), 1e-12);
        assertEquals(2.5, two.drills().get(0).y(), 1e-12, "a written decimal point is taken as it is");
        assertEquals("MM", two.units(), "the declared units win over the default");
    }

    @Test
    void invalidOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExcellonParser.Options("CM", 4, 3));
        assertThrows(IllegalArgumentException.class, () -> new ExcellonParser.Options(null, 12, 3));
        assertThrows(IllegalArgumentException.class, () -> new GerberParser.Options(null, 64));
        assertThrows(IllegalArgumentException.class, () -> new GerberParser.Options("IN", 2));
    }

    @Test
    void gerberDefaultUnitsAndCircleStepsAreUsedWhenTheFileIsSilent() {
        List<String> silent = List.of("%FSLAX24Y24*%", "%ADD10C,0.01*%", "D10*", "X0Y0D03*", "M02*");
        assertEquals("IN", new GerberParser().parse(silent).units());
        assertEquals("MM", new GerberParser(new GerberParser.Options("MM", 64)).parse(silent).units());
        List<String> declared = List.of("%FSLAX24Y24*%", "%MOIN*%", "%ADD10C,0.01*%", "D10*", "X0Y0D03*", "M02*");
        assertEquals("IN", new GerberParser(new GerberParser.Options("MM", 64)).parse(declared).units());

        // A half circle of radius 1: more steps follow the arc more closely (its length tends to pi).
        List<String> arc = List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,0.01*%", "D10*", "G75*", "X10000Y0D02*",
                "G03X-10000Y0I-10000J0D01*", "M02*");
        GerberImage coarse = new GerberParser(new GerberParser.Options("MM", 8)).parse(arc);
        GerberImage fine = new GerberParser(new GerberParser.Options("MM", 256)).parse(arc);
        double coarseLength = coarse.followGeometry().getLength();
        double fineLength = fine.followGeometry().getLength();
        assertTrue(coarseLength < fineLength, coarseLength + " < " + fineLength);
        assertEquals(Math.PI, fineLength, 1e-3);
        assertEquals(new GerberParser().parse(arc).followGeometry().getLength(),
                new GerberParser(GerberParser.Options.standard()).parse(arc).followGeometry().getLength(), 0);
    }
}
