package org.flatcam.fx;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.merge.ExcellonJoin;
import org.flatcam.cam.merge.GeometryJoin;

/** Worker-side joins with explicit preservation/refusal rules for machining metadata. No UI or source mutations. */
final class TclObjectJoin {
    record GeometryResult(GeometryJoin.Joined joined, GeometryGCodeParameters defaults, GeometryCncSettings settings) { }
    record ExcellonResult(ExcellonImage image, Map<Integer, DrillGCodeParameters> defaults, DrillCncSettings settings) { }

    private TclObjectJoin() { }

    static GeometryResult geometry(List<ProjectFile.GeometryEntry> entries) {
        TclExecution.cancellation().throwIfCancellationRequested();
        // TclCommandJoinGeometry calls merge without fuse_tools; retain every tool and its settings.
        var joined = GeometryJoin.join(entries.stream().map(entry -> new GeometryJoin.Source(entry.units(),
                entry.geometry(), entry.strokeOnly(), entry.tools())).toList(), false);
        if (joined.tools().isEmpty()) {
            var first = entries.getFirst();
            for (var entry : entries) {
                if (!Objects.equals(first.cncDefaults(), entry.cncDefaults())
                        || !Objects.equals(first.cncSettings(), entry.cncSettings()))
                    throw new IllegalArgumentException("Single Geometry possui parametros CNC diferentes; use multi-tool antes de juntar.");
            }
            return new GeometryResult(joined, first.cncDefaults(), first.cncSettings());
        }
        GeometryGCodeParameters defaults = null;
        GeometryCncSettings model = null;
        Map<Integer, GeometryGCodeParameters> parameters = new LinkedHashMap<>();
        Map<Integer, VTipSettings> vTools = new LinkedHashMap<>();
        int base = 0;
        for (var entry : entries) {
            TclExecution.cancellation().throwIfCancellationRequested();
            var settings = entry.cncSettings();
            if (settings != null) {
                if (model != null && model.preprocessor() != settings.preprocessor())
                    throw new IllegalArgumentException("Preprocessadores Geometry diferentes; alinhe os perfis antes de juntar.");
                model = settings;
            }
            if (entry.cncDefaults() != null) {
                if (defaults != null && !sameCommonOptions(defaults, entry.cncDefaults()))
                    throw new IllegalArgumentException("Opcoes comuns de Geometry diferentes; alinhe antes de juntar.");
                defaults = entry.cncDefaults();
            }
            for (int id = 0; id < entry.tools().size(); id++) {
                var value = settings == null ? entry.cncDefaults()
                        : settings.parametersByTool().getOrDefault(id, entry.cncDefaults());
                if (value != null) parameters.put(base + id, value);
                if (settings != null && settings.vTools().containsKey(id)) vTools.put(base + id, settings.vTools().get(id));
            }
            base += entry.tools().size();
        }
        // Don't implicitly apply a configured source's common profile/depth to an unconfigured source.
        if (defaults != null && entries.stream().anyMatch(entry -> entry.cncDefaults() == null))
            throw new IllegalArgumentException("Geometry configurada e sem parametros CNC; configure todas antes de juntar.");
        if (model != null && entries.stream().anyMatch(entry -> entry.cncSettings() == null))
            throw new IllegalArgumentException("Geometry com e sem perfil CNC; configure todas antes de juntar.");
        GeometryGCodeParameters common = defaults;
        parameters.entrySet().removeIf(entry -> entry.getValue().equals(common));
        if (model != null && !parameters.isEmpty() && (model.preprocessor().isLaser() || model.preprocessor().isPlotter()
                || model.preprocessor().isRoland() || model.preprocessor().requiresProbe()))
            throw new IllegalArgumentException("Este perfil nao suporta parametros individuais; alinhe os parametros antes de juntar.");
        GeometryCncSettings settings = model == null && parameters.isEmpty() ? null
                : new GeometryCncSettings(model == null ? org.flatcam.cam.gcode.GCodePreprocessor.FX_PORTABLE
                        : model.preprocessor(), null, vTools, parameters);
        return new GeometryResult(joined, defaults, settings);
    }

    private static boolean sameCommonOptions(GeometryGCodeParameters a, GeometryGCodeParameters b) {
        return a.pauseForToolChange() == b.pauseForToolChange() && Double.compare(a.rapidFeedRate(), b.rapidFeedRate()) == 0
                && Objects.equals(a.probing(), b.probing()) && a.jobOptions().equals(b.jobOptions());
    }

    static ExcellonResult excellon(List<ProjectFile.ExcellonEntry> entries) {
        TclExecution.cancellation().throwIfCancellationRequested();
        var joined = ExcellonJoin.joinWithMapping(entries.stream().map(ProjectFile.ExcellonEntry::image).toList(), true);
        Map<Integer, DrillGCodeParameters> defaults = new LinkedHashMap<>();
        // A missing parameter set is meaningful: it must not inherit another source's depth/feed.
        Map<Integer, DrillGCodeParameters> parametersByMergedTool = new LinkedHashMap<>();
        Map<Integer, Boolean> selectionByTool = new LinkedHashMap<>();
        DrillCncSettings model = null;
        var selected = new LinkedHashSet<Integer>();
        for (int i = 0; i < entries.size(); i++) {
            TclExecution.cancellation().throwIfCancellationRequested();
            var entry = entries.get(i); var ids = joined.sourceToolIds().get(i);
            for (int sourceId : entry.drillDefaults().keySet()) {
                if (!ids.containsKey(sourceId))
                    throw new IllegalArgumentException("Parametro de furo sem ferramenta correspondente.");
            }
            for (var tool : ids.entrySet()) {
                int id = tool.getValue();
                var parameters = entry.drillDefaults().get(tool.getKey());
                if (parametersByMergedTool.containsKey(id)
                        && !Objects.equals(parametersByMergedTool.get(id), parameters))
                    throw new IllegalArgumentException("Ferramentas Excellon do mesmo diametro possuem parametros diferentes; alinhe antes de juntar.");
                parametersByMergedTool.put(id, parameters);
                if (parameters != null) defaults.put(id, parameters);
            }
            var settings = entry.cncSettings();
            if (settings != null) {
                if (model != null && (model.preprocessor() != settings.preprocessor()
                        || !model.options().equals(settings.options()) || model.toolOrder() != settings.toolOrder()))
                    throw new IllegalArgumentException("Opcoes comuns de Drilling diferentes; alinhe antes de juntar.");
                model = settings;
                for (var id : ids.entrySet()) {
                    boolean included = settings.selectedToolIds().contains(id.getKey());
                    Boolean previous = selectionByTool.putIfAbsent(id.getValue(), included);
                    if (previous != null && previous != included)
                        throw new IllegalArgumentException("Fusao Excellon mudaria a selecao Drilling; alinhe a selecao antes de juntar.");
                }
                for (int oldId : settings.selectedToolIds()) {
                    Integer id = ids.get(oldId);
                    if (id == null) throw new IllegalArgumentException("Selecao Drilling sem ferramenta correspondente.");
                    selected.add(id);
                }
            } else selected.addAll(ids.values());
        }
        if (model != null && entries.stream().anyMatch(entry -> entry.cncSettings() == null))
            throw new IllegalArgumentException("Excellon com e sem perfil Drilling; configure todos antes de juntar.");
        DrillCncSettings settings = model == null ? null
                : new DrillCncSettings(model.preprocessor(), model.options(), List.copyOf(selected), model.toolOrder());
        return new ExcellonResult(joined.image(), Map.copyOf(defaults), settings);
    }
}
