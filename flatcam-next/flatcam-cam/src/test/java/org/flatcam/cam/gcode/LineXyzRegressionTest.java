package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;

class LineXyzRegressionTest {
    @RepeatedTest(10)
    void verticalPreambleDoesNotInventAnOriginOrTurnAFeedSettingIntoAMove() {
        String source = "G21\nG90\nG94\nG00 Z15\nG01 F100\nG00 X3 Y7\nG01 Z-1 F50\n";
        assertEquals("G21\nG90\nG94\nG00 Z15.0000\nG01 F100\n"
                + "G00 X3.0000 Y7.0000 Z15.0000\nG01 X3.0000 Y7.0000 Z-1.0000 F50.0000\n",
                ControllerProgramCodec.explicitXyz(source));
    }

    @RepeatedTest(10)
    void macroInvalidatesRememberedAxesBeforeTheVerticalRestore() {
        assertEquals("G90\nG00 X3.0000 Y7.0000 Z15.0000\nT2\nM6\nM0\nG90\nG00 Z15.0000\n"
                + "G00 X8.0000 Y9.0000 Z15.0000\n",
                ControllerProgramCodec.explicitXyz("G90\nG00 X3 Y7 Z15\nT2\nM6\nM0\nG90\nG00 Z15\nG00 X8 Y9\n"));
    }

    @RepeatedTest(10)
    void finishingChecksCancellationDuringEncodingNotOnlyBeforeIt() {
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> GCodePreprocessor.LINE_XYZ.finish(
                new StringBuilder("G21\nG90\nG00 Z2\n" + "G01 X3 Y7 Z-1 F100\n".repeat(100)),
                1500, "MM", () -> checks.incrementAndGet() == 5));
        assertEquals(5, checks.get());
    }
}
