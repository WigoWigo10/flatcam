package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.flatcam.app.job.JobContext;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;

/** Worker-only generation/preview/write. Never touches UI or user preferences. */
final class DrillCncGeneration {
    record Generated(CncJobResult job, GCodeToolpathParser.Result preview) { }

    static Generated generate(ExcellonImage image, Map<Integer, DrillGCodeParameters> parameters,
                              List<Integer> tools, GCodeGenerator.DrillJobOptions options,
                              GCodePreprocessor profile, Path output, JobContext context,
                              Runnable validateBeforePublication) throws IOException {
        if (javafx.application.Platform.isFxApplicationThread())
            throw new IllegalStateException(I18n.text("ui.02cdd162179d"));
        CancellationToken cancellation = context::isCancelled;
        int[] lastPercent = {-1};
        CncJobResult job = GCodeGenerator.generateDrillCncJob(image, parameters, tools, options, profile,
                cancellation, fraction -> report(context, fraction * 0.8, I18n.text("ui.716687e8f15b"), lastPercent));
        context.reportProgress(Double.NaN, I18n.text("ui.9b85c8bc8bd6"));
        GCodeToolpathParser.Result preview;
        try { preview = GCodeToolpathParser.parse(job.gcode(), cancellation,
                fraction -> report(context, 0.8 + fraction * 0.15, I18n.text("ui.3b412e2ca6a2"), lastPercent)); }
        catch (IllegalArgumentException unavailable) { preview = null; }
        cancellation.throwIfCancellationRequested();
        Path destination = output.toAbsolutePath().normalize();
        if (destination.getFileName() == null || Files.isDirectory(destination))
            throw new IOException(I18n.text("ui.ba3068286137"));
        context.reportProgress(Double.NaN, I18n.text("ui.843bdb17be00"));
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-drilling-", ".tmp");
        try {
            Files.writeString(temporary, job.gcode());
            cancellation.throwIfCancellationRequested();
            validateBeforePublication.run();
            cancellation.throwIfCancellationRequested();
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Generated(job, preview);
        } finally { Files.deleteIfExists(temporary); }
    }

    // Count-based progress can fire per hole; only send changed percentages to the UI.
    private static void report(JobContext context, double fraction, String message, int[] lastPercent) {
        int percent = (int) Math.floor(fraction * 100);
        if (percent != lastPercent[0]) {
            lastPercent[0] = percent;
            context.reportProgress(fraction, message);
        }
    }

    private DrillCncGeneration() { }
}
