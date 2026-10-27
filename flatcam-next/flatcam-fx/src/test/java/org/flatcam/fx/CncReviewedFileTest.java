package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CncReviewedFileTest {
    @TempDir Path directory;
    @Test void realCalibrationAndPasteProgramsKeepOperationSpecificWarnings() throws Exception {
        String calibration=org.flatcam.cam.transform.Calibration.verificationGCode(
                new double[][]{{0,0},{0,10},{10,10},{10,0}},org.flatcam.cam.transform.Calibration.GCodeSettings.defaults());
        var geometry=new org.locationtech.jts.io.WKTReader().read("LINESTRING (0 0, 2 0)");
        String paste=org.flatcam.cam.solderpaste.SolderPaste.generateGCode(
                java.util.List.of(new org.flatcam.cam.geometry.ToolGeometry(.3,geometry)),
                org.flatcam.cam.solderpaste.SolderPaste.defaults(),"MM",null).gcode();
        for(String program:java.util.List.of(calibration,paste)) {
            var report=CncReviewedFile.review("Auxiliar",program,null,"Conferir operação auxiliar",CancellationToken.none(),ProgressCallback.none());
            assertTrue(report.canContinue());
            assertTrue(report.issues().stream().anyMatch(i->i.text().equals("Conferir operação auxiliar")));
            assertTrue(report.tools().stream().allMatch(t->t.feedXY().equals("Consultar código")));
        }
    }
    @Test void publicationUsesExactReviewedTextAndCleansTemporaryFiles() throws Exception {
        String text="G21\r\nG90\r\nM5\r\n";
        Path destination=directory.resolve("program.nc"); Files.writeString(destination,"old");
        var report=CncReviewedFile.review("Calibration",text,null,"Confira pontos e origem",CancellationToken.none(),ProgressCallback.none());
        assertTrue(report.issues().stream().anyMatch(i->i.text().equals("Confira pontos e origem")));
        CncReviewedFile.write(text,destination,CancellationToken.none(),()->{});
        assertEquals(text,Files.readString(destination));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void cancellationStaleSourceAndEmptyProgramPreservePreviousDestination() throws Exception {
        Path destination=directory.resolve("program.nc"); Files.writeString(destination,"old");
        var cancelled=new AtomicBoolean();
        assertThrows(CancellationException.class,()->CncReviewedFile.write("G21",destination,cancelled::get,()->cancelled.set(true)));
        assertEquals("old",Files.readString(destination));
        assertThrows(IllegalStateException.class,()->CncReviewedFile.write("G21",destination,CancellationToken.none(),()->{ throw new IllegalStateException("stale"); }));
        assertThrows(IllegalArgumentException.class,()->CncReviewedFile.write("  ",destination,CancellationToken.none(),()->{}));
        assertEquals("old",Files.readString(destination));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
}
