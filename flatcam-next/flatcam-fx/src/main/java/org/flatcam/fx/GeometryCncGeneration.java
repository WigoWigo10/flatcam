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
            throw new IllegalStateException("Geometry CNC deve ser calculado fora da thread FX.");
        org.flatcam.cam.CancellationToken cancellation = context::isCancelled;
        cancellation.throwIfCancellationRequested();
        context.reportProgress(Double.NaN, "Gerando caminhos e G-code de Geometry...");
        CncJobResult job = GCodeGenerator.generateGeometryCncJob(units, tools, parameters, vTools,
                parametersByTool, context::isCancelled, profile);
        context.reportProgress(Double.NaN, "Preparando previa de Geometry...");
        int[] lastPercent = {-1};
        GCodeToolpathParser.Result preview;
        try { preview = GCodeToolpathParser.parse(job.gcode(), context::isCancelled,
                fraction -> {
                    int percent = (int) Math.floor(fraction * 100);
                    if (percent != lastPercent[0]) {
                        lastPercent[0] = percent;
                        context.reportProgress(fraction, "Lendo previa de Geometry...");
                    }
                }); }
        catch (IllegalArgumentException unavailable) { preview = null; }
        cancellation.throwIfCancellationRequested();
        Path destination = output.toAbsolutePath().normalize();
        if (destination.getFileName() == null || Files.isDirectory(destination)) throw new IOException("O destino deve ser um arquivo.");
        context.reportProgress(Double.NaN, "Gravando G-code de Geometry...");
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-geometry-cnc-", ".tmp");
        try {
            Files.writeString(temporary, job.gcode());
            context.reportProgress(Double.NaN, "Publicando G-code de Geometry...");
            validate.run(); cancellation.throwIfCancellationRequested();
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            return new Generated(job, preview);
        } finally { Files.deleteIfExists(temporary); }
    }
    private GeometryCncGeneration() { }
}
