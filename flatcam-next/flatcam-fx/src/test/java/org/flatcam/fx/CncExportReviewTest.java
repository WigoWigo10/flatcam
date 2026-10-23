package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;

class CncExportReviewTest {
    @Test void actualProgramUnitsAndDepthAreReviewedWithoutInventingCamParameters() {
        var report=CncExportReview.create("existing","G20\nG90\n; FCFX TOOL T1 D0.04\nT1\nG0 Z0.1\nG0 X1 Y2\nG1 Z-0.02 F4\nG1 X2 Y2\nG0 Z0.1\nM30",null,CancellationToken.none(),ProgressCallback.none());
        assertTrue(report.canContinue()); assertEquals("IN",report.units());
        assertEquals("-0.02",report.tools().getFirst().cutZ());
        assertEquals("Consultar código",report.tools().getFirst().travelZ());
        assertTrue(CncExportReview.terminalText(report).contains("[REVISÃO CNC]"));
        assertTrue(report.tools().stream().flatMap(t -> t.details().stream()).allMatch(value -> value.target()==null));
    }
    @Test void unsupportedPreviewWarnsButEmptyProgramsAndCancellationAreNotIgnored() {
        var report=CncExportReview.create("probe","G21\nG31 Z-5 F50\nG92 Z0\n",null,CancellationToken.none(),ProgressCallback.none());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("Prévia indisponível")));
        assertFalse(CncExportReview.create("empty","",null,CancellationToken.none(),ProgressCallback.none()).canContinue());
        assertThrows(CancellationException.class,()->CncExportReview.create("cancel","G21",null,()->true,ProgressCallback.none()));
    }
}
