package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.RepeatedTest;

class IcpRegressionTest {
    @RepeatedTest(10)
    void finishDeclaresRequestedRapidSpeedBeforeAnyMotion() {
        String output = GCodePreprocessor.ISEL_ICP_CNC.finish(
                new StringBuilder("G21\nG90\nG94\nG0 Z3\nG1 Z-1 F120\n"), 900, "MM");
        assertTrue(output.contains("FASTVEL 15000\n"), output);
        assertTrue(output.indexOf("FASTVEL 15000") < output.indexOf("FASTABS Z3000"));
    }

    @RepeatedTest(10)
    void finishChecksCancellationDuringConversion() {
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> GCodePreprocessor.ISEL_ICP_CNC.finish(
                new StringBuilder("G21\nG90\n" + "G1 X3 Y7 Z-1 F120\n".repeat(100)),
                900, "MM", () -> checks.incrementAndGet() == 5));
        assertEquals(5, checks.get());
    }

    @RepeatedTest(10)
    void truncatedNativeProgramCannotPassAsACompletePreview() {
        assertThrows(IllegalArgumentException.class, () -> ControllerProgramCodec.normalizeIcp(
                "IMF_PBL test\nFASTABS Z3000\nVEL 2000\nMOVEABS Z-500\nMOVEABS X3000\n",
                CancellationToken.none(), ignored -> {}));
    }
}
