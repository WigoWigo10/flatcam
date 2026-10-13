package org.flatcam.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;

/** Read-only review of the submitted settings. No buffering, G-code generation or machine certification. */
final class CncPreflight {
    record Target(int tool, String field) { }
    record Value(String label, String text, Target target) { }
    record Issue(boolean blocking, String text, Target target) { }
    record Tool(int key, String name, String diameter, String cutZ, String travelZ,
                String feedXY, String feedZ, String power, List<Value> details) {
        Tool { details = List.copyOf(details); }
    }
    record Report(String source, String operation, String units, String profile,
                  List<Tool> tools, List<Value> common, List<Issue> issues) {
        Report { tools = List.copyOf(tools); common = List.copyOf(common); issues = List.copyOf(issues); }
        boolean canContinue() { return issues.stream().noneMatch(Issue::blocking); }
    }

    static Report geometry(String source, String units, GeometryCncToolPanel.Result result) {
        var rows = new ArrayList<Tool>();
        var issues = new ArrayList<Issue>();
        var common = new ArrayList<Value>();
        var profile = result.preprocessor();
        var base = result.parameters();
        boolean cutting = !profile.isLaser() && !profile.isPlotter();
        double clearance = Math.max(base.safeZ(), result.parametersByTool().values().stream()
                .mapToDouble(GeometryGCodeParameters::safeZ).max().orElse(base.safeZ()));
        var travelPolicy=base.jobOptions().travelClearance();
        double lastTravelZ=clearance;
        var activeDiameters = new java.util.HashSet<Double>();
        for (int i = 0; i < result.tools().size(); i++) {
            var tool = result.tools().get(i);
            var p = result.parametersByTool().getOrDefault(i, base);
            boolean empty = tool.geometry().isEmpty();
            double effectiveTravel=travelPolicy.effective(p.safeZ(),clearance);
            if (!empty) lastTravelZ=effectiveTravel;
            if (!empty) activeDiameters.add(tool.toolDiameter());
            double depth = p.cutDepth();
            var details = new ArrayList<Value>();
            if (cutting && !empty && tool.toolProfile() == ToolProfile.V) {
                var tip = result.vTools().get(i);
                if (tip == null) issues.add(new Issue(true, "T" + (i + 1) + ": faltam os parâmetros da ponta V.", target(i, "cnc-v-tip-dia-" + i)));
                else {
                    depth = tip.cutDepth(tool.toolDiameter());
                    details.add(value("Ponta V", "Diâmetro " + number(tip.tipDiameter()) + "; ângulo " + number(tip.angleDegrees())
                            + "°; Cut Z calculado pela largura do corte", i, "cnc-v-tip-angle-" + i));
                }
            }
            details.add(value("Profundidade efetiva", cutting ? number(-depth) + " " + unit(units) : "Não se aplica ao corte neste perfil", i, "cnc-cut-depth"));
            if (cutting && !profile.requiresProbe() && !profile.isRoland())
                details.add(value("Retração / Travel Z efetivo", number(effectiveTravel) + " " + unit(units)
                        + (travelPolicy==GeometryTravelClearance.PER_TOOL ? "; usa o Travel Z desta fresa" : "; usa o maior Travel Z do trabalho")
                        + " (configurado nesta fresa: " + number(p.safeZ()) + ")", i, "cnc-safe-z"));
            details.add(value("Passes", cutting ? passes(depth, p.multiDepth(), p.depthPerPass())
                    + (p.multiDepth() && p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING ? "; pontos isolados usam somente a profundidade final" : "")
                    : "Sem passes de fresagem", i, "cnc-multi-depth"));
            details.add(value("Estratégia", p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING && p.multiDepth()
                    ? "Contínua, direção alternada" : cutting ? "Retração; mesma direção" : "Não se aplica", i, "cnc-pass-strategy"));
            if (profile == GCodePreprocessor.Z_LASER) details.add(value("Foco / posição Z", number(p.safeZ()), i, "cnc-safe-z"));
            details.add(value("Dwell", !p.dwell() ? "Desativado" : profile.controlsSpindle(p.spindleSpeedRpm())
                    ? number(p.dwellSeconds()) + " s" : "Inativo: spindle não comandado pelo perfil", i, "cnc-dwell"));
            details.add(value("Compensação", p.offset() + (p.offset() == ToolPathOffset.CUSTOM ? " (" + number(p.customOffset()) + ")" : ""), i, "cnc-offset"));
            details.add(value("Extra Cut", p.extraCut() ? number(p.extraCutLength()) + " " + unit(units) : "Desativado", i, "cnc-extra-length"));
            details.add(value("Trajetos", empty ? "Nenhum: esta ferramenta será omitida" : "Associados à ferramenta " + (i + 1), i, "cnc-tools"));
            rows.add(new Tool(i, "T" + (i + 1) + (empty ? " (vazia)" : ""), number(tool.toolDiameter()),
                    cutting ? number(-depth) : "—", cutting || profile == GCodePreprocessor.Z_LASER ? number(p.safeZ()) : "—",
                    profile.isPlotter() ? "—" : number(p.feedRate()), cutting ? number(p.feedRateZ()) : "—",
                    profile.isPlotter() || profile.isRoland() ? "—" : number(p.spindleSpeedRpm()), details));
            if (empty) issues.add(new Issue(false, "T" + (i + 1) + " não tem trajetos e não produzirá cortes.", target(i, "cnc-tools")));
            if (!empty && cutting && p.spindleSpeedRpm() == 0 && !profile.isRoland())
                issues.add(new Issue(false, "T" + (i + 1) + ": RPM zero não liga automaticamente o spindle nem garante a parada final. "
                        + "Confira acionamento/parada manuais; trocas ativadas podem pará-lo antes da pausa.", target(i, "cnc-power")));
            if (!empty && cutting && p.multiDepth() && p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING)
                issues.add(new Issue(false, "T" + (i + 1) + ": passes alternados invertem o sentido de corte entre profundidades.", target(i, "cnc-pass-strategy")));
            if (!empty && p.offset() != ToolPathOffset.PATH)
                issues.add(new Issue(false, "T" + (i + 1) + ": haverá compensação adicional. Isolation/NCC/Cutout podem já ter compensado o raio da fresa.", target(i, "cnc-offset")));
        }
        if (activeDiameters.isEmpty()) issues.add(new Issue(true, "Nenhuma ferramenta contém trajetos para gerar.", null));
        if (cutting && activeDiameters.size() > 1 && !base.pauseForToolChange() && !profile.automaticToolSelection())
            issues.add(new Issue(true, "Há diâmetros diferentes sem troca de ferramenta. Ative a troca ou gere arquivos separados.", target(-1, "cnc-tool-change")));
        var pos = base.jobOptions();
        common.add(value("Troca de ferramenta", profile.automaticToolSelection() ? "Seleção automática do perfil"
                : base.pauseForToolChange() ? "Ativada" : "Desativada", -1, "cnc-tool-change"));
        if (cutting) {
            positions(common, "cnc", pos.startZ(), pos.toolChangeZ() == null ? clearance : pos.toolChangeZ(),
                    pos.toolChangeX(), pos.toolChangeY(), pos.endZ() == null ? lastTravelZ : pos.endZ(), pos.endX(), pos.endY());
            common.add(value("Política de Travel Z",travelPolicy.toString(),-1,"cnc-travel-clearance"));
            common.add(value("Altura dos deslocamentos XY",travelPolicy==GeometryTravelClearance.PER_TOOL ? "Individual: confira o valor efetivo de cada fresa"
                    : number(clearance) + " " + unit(units) + " (maior Travel Z do trabalho)", -1, "cnc-travel-clearance"));
        } else common.add(value("Posições de fresagem", "Não se aplicam a este perfil; Z_laser usa somente seu foco Z", -1, "cnc-preprocessor"));
        if (profile.requiresProbe() && base.probing() != null) probe(common, base.probing());
        startChangeNotice(issues,pos.startZ(),pos.toolChangeZ()==null ? clearance : pos.toolChangeZ(),
                base.pauseForToolChange() || profile.automaticToolSelection(),profile,"cnc");
        exclusionNotice(issues,pos.exclusionsEnabled(),pos.exclusions());
        common.add(value("Ordem dos trajetos", switch (pos.routing().mode()) {
            case SOURCE -> "Ordem de origem"; case NEAREST -> "Proximidade"; case BOARDS -> "Agrupar por placa";
        }, -1, "cnc-route-mode"));
        common.add(value("Exclusões", pos.exclusionsEnabled() ? pos.exclusions().size() + " área(s) protegida(s)" : "Desativadas", -1, "cnc-exclusions-pane"));
        rapid(common, profile, base.rapidFeedRate(), units, "cnc-rapid-feed");
        if (travelPolicy==GeometryTravelClearance.PER_TOOL)
            issues.add(new Issue(false,"Travel Z individual pode ser menor que o de outras fresas. Confira cada altura e fixação; a troca ainda usa a altura comum validada.",target(-1,"cnc-travel-clearance")));
        if (pos.endX() != null && pos.endZ() != null && pos.endZ() < lastTravelZ)
            issues.add(new Issue(false, "Estacionamento XY em Z " + number(lastTravelZ) + "; só depois desce para End Z " + number(pos.endZ()) + ".", target(-1, "cnc-end-z")));
        issues.add(new Issue(false, "Cobertura não calculada: Geometry contém trajetos, não uma área-alvo. Esta revisão não identifica todas as regiões não usinadas.", null));
        limitations(issues, profile);
        return new Report(source, "Geometry → CNC", unit(units), profile.label(), rows, common, issues);
    }

    static Report drilling(DrillGCodeToolPanel.Result result) {
        var image = result.source().image();
        var rows = new ArrayList<Tool>();
        var issues = new ArrayList<Issue>();
        var common = new ArrayList<Value>();
        var counts = new LinkedHashMap<Integer, int[]>();
        image.drills().forEach(hit -> counts.computeIfAbsent(hit.toolId(), key -> new int[2])[0]++);
        image.slots().forEach(hit -> counts.computeIfAbsent(hit.toolId(), key -> new int[2])[1]++);
        var profile = result.preprocessor();
        for (int id : result.orderedToolIds()) {
            var p = result.settingsByTool().get(id);
            int[] count = counts.getOrDefault(id, new int[2]);
            var details = List.of(value("Furos / rasgos", count[0] + " / " + count[1], id, "drill-tools"),
                    value("Profundidade efetiva", number(-p.effectiveDepth()) + " " + unit(image.units()) + " (Cut Z + Offset Z)", id, "drill-cut-z"),
                    value("Offset Z", number(p.offsetZ()), id, "drill-offset-z"),
                    value("Passes", passes(p.effectiveDepth(), p.multiDepth(), p.depthPerPass()), id, "drill-multi-depth"),
                    value("Dwell", p.dwell() ? number(p.dwellSeconds()) + " s" : "Desativado", id, "drill-dwell-time"));
            rows.add(new Tool(id, "T" + id + (count[0] + count[1] == 0 ? " (vazia)" : ""), number(image.toolDiameters().get(id)), number(-p.effectiveDepth()),
                    number(p.safeZ()), "—", number(p.feedRate()), profile.isRoland() ? "—" : number(p.spindleSpeedRpm()), details));
            if (count[0] + count[1] == 0) issues.add(new Issue(false, "T" + id + " não contém furos nem rasgos.", target(id, "drill-tools")));
            if (count[1] > 0) issues.add(new Issue(false, "T" + id + ": rasgos são fresados ao longo do eixo; confirme a ferramenta e o avanço lateral. Não é apenas mergulho de broca.", target(id, "drill-feed-z")));
            if (p.spindleSpeedRpm() == 0 && !profile.isRoland())
                issues.add(new Issue(false, "T" + id + ": RPM zero não liga automaticamente o spindle nem garante a parada final. "
                        + "Confira acionamento/parada manuais; trocas ativadas podem pará-lo antes da pausa.", target(id, "drill-power")));
        }
        var selected = java.util.Set.copyOf(result.orderedToolIds());
        int omitted = counts.entrySet().stream().filter(entry -> !selected.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue()[0] + entry.getValue()[1]).sum();
        if (omitted > 0) issues.add(new Issue(false, omitted + " furo(s)/rasgo(s) de ferramentas não selecionadas ficarão fora deste arquivo.", target(-1, "drill-tools")));
        var options = result.options();
        common.add(value("Troca de ferramenta", profile.automaticToolSelection() ? "Seleção automática do perfil"
                : options.pauseForToolChange() ? "Ativada" : "Desativada", -1, "drill-tool-change"));
        positions(common, "drill", options.startZ(), options.toolChangeZ(), options.toolChangeX(), options.toolChangeY(),
                options.endMoveZ(), options.endMoveX(), options.endMoveY());
        if (profile.requiresProbe() && options.probing() != null) probe(common, options.probing());
        startChangeNotice(issues,options.startZ(),options.toolChangeZ(),
                options.pauseForToolChange() || profile.automaticToolSelection(),profile,"drill");
        exclusionNotice(issues,options.exclusionsEnabled(),options.exclusions());
        common.add(value("Ordem dos furos", options.optimizeDrills() ? "Proximidade, por ferramenta" : "Ordem de origem", -1, "drill-optimize-order"));
        common.add(value("Exclusões", options.exclusionsEnabled() ? options.exclusions().size() + " área(s) protegida(s)" : "Desativadas", -1, "cnc-exclusions-pane"));
        rapid(common, profile, options.rapidFeedRate(), image.units(), "drill-rapid-feed");
        var active = result.orderedToolIds().stream().filter(id -> {
            int[] count = counts.getOrDefault(id, new int[2]); return count[0] + count[1] > 0;
        }).toList();
        if (!active.isEmpty() && options.endMoveX() != null) {
            double z = Math.max(options.endMoveZ(), result.settingsByTool().get(active.getLast()).safeZ());
            if (z > options.endMoveZ()) issues.add(new Issue(false, "Estacionamento XY em Z " + number(z)
                    + "; só depois desce para End Z " + number(options.endMoveZ()) + ".", target(-1, "drill-end-z")));
        }
        if (rows.isEmpty()) issues.add(new Issue(true, "Selecione ao menos uma ferramenta.", target(-1, "drill-tools")));
        else if (counts.entrySet().stream().filter(entry -> selected.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue()[0] + entry.getValue()[1]).sum() == 0)
            issues.add(new Issue(true, "As ferramentas selecionadas não contêm furos nem rasgos.", target(-1, "drill-tools")));
        limitations(issues, profile);
        return new Report(result.source().item().getValue(), "Excellon → CNC", unit(image.units()), profile.label(), rows, common, issues);
    }

    private static void startChangeNotice(List<Issue> issues, Double start, double change, boolean enabled,
                                          GCodePreprocessor profile, String prefix) {
        if (enabled && !profile.requiresProbe() && start!=null && Double.compare(start,change)!=0)
            issues.add(new Issue(false,"Start Z " + number(start) + " é apenas o movimento inicial; a troca usa Tool change Z "
                    + number(change) + ", inclusive na primeira ferramenta. Start Z não substitui a altura de troca.",
                    target(-1,prefix.equals("drill") ? "probe-change-z" : "cnc-change-z")));
    }
    private static void exclusionNotice(List<Issue> issues, boolean enabled, List<CncExclusionArea> areas) {
        if (enabled && areas.stream().anyMatch(area -> area.strategy()==CncExclusionArea.Strategy.OVER))
            issues.add(new Issue(false,"Over sobe antes de entrar e desce depois de sair da região ampliada pelo raio e margem. "
                    + "Áreas sobrepostas usam a maior altura necessária no trecho; nunca desce abaixo do Travel Z efetivo.",target(-1,"cnc-exclusions-pane")));
    }
    private static void positions(List<Value> values, String prefix, Double start, double change, Double changeX,
                                  Double changeY, double end, Double endX, Double endY) {
        values.add(value("Start Z", start == null ? "Automático" : number(start), -1, prefix + "-start-z"));
        values.add(value("Tool change Z", number(change) + " (quando há troca)", -1, prefix.equals("drill") ? "probe-change-z" : "cnc-change-z"));
        values.add(value("Tool change X,Y", xy(changeX, changeY), -1, prefix.equals("drill") ? "drill-tool-change-xy" : "cnc-change-xy"));
        values.add(value("End Z", number(end), -1, prefix + "-end-z"));
        values.add(value("End X,Y", xy(endX, endY), -1, prefix + "-end-xy"));
    }
    private static void rapid(List<Value> values, GCodePreprocessor profile, double rate, String units, String field) {
        values.add(value("Avanço rápido", profile.usesRapidFeed() ? (rate == 0 ? "Automático" : number(rate) + " " + unit(units) + "/min")
                : "Definido pelo controlador (G0); este perfil não envia F para limitar o rápido", -1, field));
    }
    private static void probe(List<Value> values, ProbeToolChangeParameters probe) {
        values.removeIf(value -> value.label().startsWith("Tool change"));
        values.add(value("Tool change Z (sonda)", number(probe.toolChangeZ()), -1, "probe-change-z"));
        values.add(value("Tool change X,Y (sonda)", xy(probe.toolChangeX(), probe.toolChangeY()), -1, "probe-xy"));
        values.add(value("Limite de sondagem", number(probe.probeDepth()), -1, "probe-depth"));
        values.add(value("Avanço de sondagem", number(probe.feedRate()) + "; segundo contato: " + number(probe.feedRate() / 2), -1, "probe-feed"));
        values.add(value("Contact Z / referência G92", number(probe.contactZ()), -1, "probe-contact-z"));
    }
    private static void limitations(List<Issue> issues, GCodePreprocessor profile) {
        issues.add(new Issue(false, profile.requiresProbe()
                ? "A prévia de sondagem não simula G31, contato, G92 ou macros de troca. Não valida a segurança da máquina."
                : "A prévia é 2D: não simula remoção de material, grampos, limites da máquina nem movimentos de macros M6.", null));
    }
    private static Target target(int tool, String field) { return new Target(tool, field); }
    private static Value value(String label, String text, int tool, String field) { return new Value(label, text, target(tool, field)); }
    private static String xy(Double x, Double y) { return x == null ? "Mantém a posição atual" : number(x) + "; " + number(y); }
    static String number(double value) { return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static String unit(String units) { return "MM".equalsIgnoreCase(units) ? "mm" : "in"; }
    private static String passes(double depth, boolean multi, double step) {
        return !multi ? "1 passe" : MachiningPasses.count(depth, true, step) + " passe(s); passo máximo " + number(step);
    }
    private CncPreflight() { }
}
