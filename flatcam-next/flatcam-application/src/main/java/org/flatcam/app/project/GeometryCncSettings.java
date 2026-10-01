package org.flatcam.app.project;

import java.util.Map;
import java.util.Objects;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gcode.VTipSettings;

/** Profile and tool settings supplementing GeometryGCodeParameters in the native project. */
public record GeometryCncSettings(GCodePreprocessor preprocessor, Double singleToolDiameter,
                                  Map<Integer, VTipSettings> vTools) {
    public GeometryCncSettings {
        Objects.requireNonNull(preprocessor, "preprocessor");
        vTools = Map.copyOf(vTools);
        if (singleToolDiameter != null && (!Double.isFinite(singleToolDiameter) || singleToolDiameter <= 0))
            throw new IllegalArgumentException("Diametro CNC de Geometry deve ser positivo.");
        if (vTools.keySet().stream().anyMatch(id -> id < 0))
            throw new IllegalArgumentException("Indice de ferramenta V invalido.");
    }
}
