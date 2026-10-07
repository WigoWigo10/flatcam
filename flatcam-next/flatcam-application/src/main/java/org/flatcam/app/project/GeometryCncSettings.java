package org.flatcam.app.project;

import java.util.Map;
import java.util.Objects;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolProfile;

/** Profile and tool settings supplementing GeometryGCodeParameters in the native project. */
public record GeometryCncSettings(GCodePreprocessor preprocessor, Double singleToolDiameter,
                                  Map<Integer, VTipSettings> vTools,
                                  Map<Integer, GeometryGCodeParameters> parametersByTool,
                                  ToolProfile singleToolProfile, Map<Integer, CncJobDefaults> jobDefaultsByTool) {
    public GeometryCncSettings(GCodePreprocessor preprocessor, Double singleToolDiameter, Map<Integer, VTipSettings> vTools,
                               Map<Integer, GeometryGCodeParameters> parametersByTool, ToolProfile singleToolProfile) {
        this(preprocessor, singleToolDiameter, vTools, parametersByTool, singleToolProfile, Map.of());
    }
    public GeometryCncSettings(GCodePreprocessor preprocessor, Double singleToolDiameter,
                              Map<Integer, VTipSettings> vTools, Map<Integer, GeometryGCodeParameters> parametersByTool) {
        this(preprocessor, singleToolDiameter, vTools, parametersByTool, ToolProfile.C1);
    }
    public GeometryCncSettings(GCodePreprocessor preprocessor, Double singleToolDiameter,
                              Map<Integer, VTipSettings> vTools) {
        this(preprocessor, singleToolDiameter, vTools, Map.of());
    }
    public GeometryCncSettings {
        Objects.requireNonNull(preprocessor, "preprocessor");
        Objects.requireNonNull(singleToolProfile, "singleToolProfile");
        vTools = Map.copyOf(vTools);
        parametersByTool = Map.copyOf(parametersByTool);
        jobDefaultsByTool = Map.copyOf(jobDefaultsByTool);
        if (jobDefaultsByTool.keySet().stream().anyMatch(id -> id < 0))
            throw new IllegalArgumentException("Indice de configuracao comum CNC invalido.");
        if (parametersByTool.keySet().stream().anyMatch(id -> id < 0))
            throw new IllegalArgumentException("Indice CNC invalido.");
        if (singleToolDiameter != null && (!Double.isFinite(singleToolDiameter) || singleToolDiameter <= 0))
            throw new IllegalArgumentException("Diametro CNC de Geometry deve ser positivo.");
        if (vTools.keySet().stream().anyMatch(id -> id < 0))
            throw new IllegalArgumentException("Indice de ferramenta V invalido.");
    }
}
