package org.flatcam.app.project;

import java.util.List;
import java.util.Objects;
import org.flatcam.cam.gcode.GCodeGenerator.DrillJobOptions;
import org.flatcam.cam.gcode.GCodePreprocessor;

/** Validated settings of the last successful drilling generation, not unsubmitted UI drafts. */
public record DrillCncSettings(GCodePreprocessor preprocessor, DrillJobOptions options,
                               List<Integer> selectedToolIds, ToolOrder toolOrder) {
    public enum ToolOrder { NO, FORWARD, REVERSE }

    public DrillCncSettings {
        Objects.requireNonNull(preprocessor, "preprocessor");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(toolOrder, "toolOrder");
        options.validateExclusions(preprocessor);
        options.validatePositions(preprocessor);
        selectedToolIds = List.copyOf(selectedToolIds);
        if (!GCodePreprocessor.millingProfiles().contains(preprocessor))
            throw new IllegalArgumentException("Perfil laser/plotter nao pode ser restaurado em Drilling.");
        if (selectedToolIds.isEmpty() || selectedToolIds.stream().anyMatch(id -> id < 0)
                || selectedToolIds.stream().distinct().count() != selectedToolIds.size())
            throw new IllegalArgumentException("Selecao de ferramentas de Drilling invalida.");
        if (preprocessor.requiresProbe() && (!options.pauseForToolChange() || options.probing() == null))
            throw new IllegalArgumentException("Mach3 com sonda exige troca e parametros de sondagem.");
    }
}
