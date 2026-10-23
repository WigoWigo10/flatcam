package org.flatcam.fx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.GCodeToolpathParser;

/** Shared worker-side analysis/publication for reviewed machine-code exports. */
final class CncReviewedFile {
    static CncPreflight.Report review(String name, String text, GCodeToolpathParser.ToolpathStats stats,
            String warning, CancellationToken cancellation, ProgressCallback progress) {
        var report = CncExportReview.create(name, text, stats, cancellation, progress);
        if (warning == null || warning.isBlank()) return report;
        var issues = new ArrayList<>(report.issues());
        issues.add(new CncPreflight.Issue(false, warning, null));
        return new CncPreflight.Report(report.source(), report.operation(), report.units(), report.profile(),
                report.tools(), report.common(), issues);
    }

    static Path write(String text, Path destination, CancellationToken cancellation, Runnable validate) throws IOException {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Programa vazio não pode ser exportado.");
        cancellation.throwIfCancellationRequested();
        Path target = destination.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".flatcam-reviewed-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            cancellation.throwIfCancellationRequested();
            validate.run();
            cancellation.throwIfCancellationRequested();
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
    private CncReviewedFile() { }
}
