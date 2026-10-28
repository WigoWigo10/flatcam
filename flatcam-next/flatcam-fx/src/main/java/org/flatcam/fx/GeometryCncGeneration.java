package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import org.flatcam.app.job.JobContext;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;

/** Worker-only generation, preview and staged publication; never writes preferences. */
final class GeometryCncGeneration {
    record Generated(CncJobResult job, GCodeToolpathParser.Result preview) { }

    static Generated generate(String units, List<ToolGeometry> tools, GeometryGCodeParameters parameters,
            Map<Integer, VTipSettings> vTools, Map<Integer, GeometryGCodeParameters> parametersByTool,
            GCodePreprocessor profile, Path output, JobContext context, Runnable validate) throws IOException {
        if (javafx.application.Platform.isFxApplicationThread())
            throw new IllegalStateException(I18n.text("ui.78db309e187a"));
        org.flatcam.cam.CancellationToken cancellation = context::isCancelled;
        cancellation.throwIfCancellationRequested();
        context.reportProgress(Double.NaN, I18n.text("ui.832f88353e51"));
        CncJobResult job = GCodeGenerator.generateGeometryCncJob(units, tools, parameters, vTools,
                parametersByTool, context::isCancelled, profile);
        context.reportProgress(Double.NaN, I18n.text("ui.9f6776cb8646"));
        int[] lastPercent = {-1};
        GCodeToolpathParser.Result preview;
        try { preview = GCodeToolpathParser.parse(job.gcode(), context::isCancelled,
                fraction -> {
                    int percent = (int) Math.floor(fraction * 100);
                    if (percent != lastPercent[0]) {
                        lastPercent[0] = percent;
                        context.reportProgress(fraction, I18n.text("ui.d48fe0353648"));
                    }
                }); }
        catch (IllegalArgumentException unavailable) { preview = null; }
        cancellation.throwIfCancellationRequested();
        Path destination = output.toAbsolutePath().normalize();
        if (destination.getFileName() == null || Files.isDirectory(destination)) throw new IOException(I18n.text("ui.b7ca6cd19f8b"));
        context.reportProgress(Double.NaN, I18n.text("ui.7c9ad31afe1e"));
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-geometry-cnc-", ".tmp");
        try {
            Files.writeString(temporary, job.gcode());
            context.reportProgress(Double.NaN, I18n.text("ui.44451c63fb43"));
            validate.run(); cancellation.throwIfCancellationRequested();
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            return new Generated(job, preview);
        } finally { Files.deleteIfExists(temporary); }
    }
    private GeometryCncGeneration() { }
}
