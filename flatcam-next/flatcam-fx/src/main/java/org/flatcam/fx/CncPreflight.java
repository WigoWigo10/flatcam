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
        if (profile.isLaser()) clearance = java.util.stream.IntStream.range(0,result.tools().size())
                .filter(i -> !result.tools().get(i).geometry().isEmpty())
                .mapToDouble(i -> result.parametersByTool().getOrDefault(i,base).safeZ()).max().orElse(base.safeZ());
        var travelPolicy=base.jobOptions().travelClearance();
        double lastTravelZ=clearance;
        var activeDiameters = new java.util.HashSet<Double>();
        for (int i = 0; i < result.tools().size(); i++) {
            var tool = result.tools().get(i);
            var p = result.parametersByTool().getOrDefault(i, base);
            boolean empty = tool.geometry().isEmpty();
            double effectiveTravel=profile.isLaser() ? p.safeZ() : travelPolicy.effective(p.safeZ(),clearance);
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
            if (profile == GCodePreprocessor.TOOLCHANGE_MANUAL && base.pauseForToolChange() && !empty)
                details.add(value("Ajuste manual em Z0", "Feed Z " + number(p.feedRateZ()) + " " + unit(units)
                        + "/min; spindle parado; não é sondagem", i, "cnc-feed-z"));
            if (cutting && !profile.requiresProbe() && !profile.isRoland())
                details.add(value("Retração / Travel Z efetivo", number(effectiveTravel) + " " + unit(units)
                        + (travelPolicy==GeometryTravelClearance.PER_TOOL ? "; usa o Travel Z desta fresa" : "; usa o maior Travel Z do trabalho")
                        + " (configurado nesta fresa: " + number(p.safeZ()) + ")", i, "cnc-safe-z"));
            details.add(value("Passes", cutting ? passes(depth, p.multiDepth(), p.depthPerPass())
                    + (p.multiDepth() && p.passStrategy() != GeometryPassStrategy.RETRACT_SAME_DIRECTION ? "; pontos isolados usam somente a profundidade final" : "")
                    : "Sem passes de fresagem", i, "cnc-multi-depth"));
            details.add(value("Estratégia", p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING && p.multiDepth()
                    ? "Contínua, direção alternada" : cutting && p.multiDepth() && p.passStrategy()==GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION
                    ? "Contínua, mesma direção (contornos fechados)" : cutting ? "Retração; mesma direção" : "Não se aplica", i, "cnc-pass-strategy"));
            if (profile == GCodePreprocessor.Z_LASER || profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN
                    || profile == GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)
                details.add(value("Foco / posição Z", number(p.safeZ()), i, "cnc-safe-z"));
            details.add(value("Dwell", !p.dwell() ? "Desativado" : profile.isLaser() ? "Não aplicado: o perfil laser não emite Dwell" : profile.controlsSpindle(p.spindleSpeedRpm())
                    ? number(p.dwellSeconds()) + " s" : "Inativo: spindle não comandado pelo perfil", i, "cnc-dwell"));
            details.add(value("Compensação", p.offset() + (p.offset() == ToolPathOffset.CUSTOM ? " (" + number(p.customOffset()) + ")" : ""), i, "cnc-offset"));
            details.add(value("Extra Cut", profile.isLaser() ? "Não suportado neste perfil" : p.extraCut() ? number(p.extraCutLength()) + " " + unit(units) : "Desativado", i, "cnc-extra-length"));
            details.add(value("Trajetos", empty ? "Nenhum: esta ferramenta será omitida" : "Associados à ferramenta " + (i + 1), i, "cnc-tools"));
            rows.add(new Tool(i, (profile.isLaser() ? "Grupo " : "T") + (i + 1) + (empty ? " (vazia)" : ""), number(tool.toolDiameter()),
                    cutting ? number(-depth) : "—", cutting || profile.isLaser() && profile != GCodePreprocessor.GRBL_LASER ? number(p.safeZ()) : "—",
                    profile.isPlotter() ? "—" : number(p.feedRate()), cutting ? number(p.feedRateZ()) : "—",
                    profile.isPlotter() || profile.isRoland() ? "—" : powerText(profile, p.spindleSpeedRpm()), details));
            if (empty) issues.add(new Issue(false, "T" + (i + 1) + " não tem trajetos e não produzirá cortes.", target(i, "cnc-tools")));
            if (!empty && cutting && p.spindleSpeedRpm() == 0 && !profile.isRoland() && profile != GCodePreprocessor.REPETIER)
                issues.add(new Issue(false, "T" + (i + 1) + ": RPM zero não liga automaticamente o spindle nem garante a parada final. "
                        + "Confira acionamento/parada manuais; trocas ativadas podem pará-lo antes da pausa.", target(i, "cnc-power")));
            if (!empty && cutting && p.multiDepth() && p.passStrategy() == GeometryPassStrategy.CONTINUOUS_ALTERNATING)
                issues.add(new Issue(false, "T" + (i + 1) + ": passes alternados invertem o sentido de corte entre profundidades.", target(i, "cnc-pass-strategy")));
            if (!empty && cutting && p.multiDepth() && p.passStrategy()==GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION)
                issues.add(new Issue(false,"T"+(i+1)+": passes contínuos na mesma direção exigem contornos fechados ou pontos; o gerador recusa caminhos abertos.",target(i,"cnc-pass-strategy")));
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
        } else if (profile.isLaser()) {
            double end=pos.endZ()==null ? lastTravelZ : pos.endZ();
            common.add(value("Z final (laser desligado)", number(end) + " " + unit(units), -1, "cnc-end-z"));
            common.add(value("End X,Y (laser desligado)",xy(pos.endX(),pos.endY())+"; deslocamento em Z "+number(Math.max(clearance,end))+" "+unit(units), -1,"cnc-end-xy"));
            common.add(value("Parâmetros por grupo", "Avanço e potência individuais; foco individual em Z_laser/Marlin. Feed rapids e posições finais comuns. Não há troca mecânica.", -1, "cnc-tools"));
            if(profile!=GCodePreprocessor.GRBL_LASER)
                common.add(value("Altura XY entre grupos",number(clearance)+" "+unit(units)+" (maior foco dos grupos com trajetos; potência desligada)",-1,"cnc-safe-z"));
            try { GCodeGenerator.validateLaserGroups(profile,base,result.parametersByTool()); }
            catch(IllegalArgumentException ex) { issues.add(new Issue(true,ex.getMessage(),target(-1,"cnc-tools"))); }
            common.add(value("Posições laser", "Z_laser e Marlin laser posicionam o foco Z; GRBL_laser simples não altera Z antes dos trajetos. End XY ocorre com potência desligada antes de descer para End Z. Start Z e troca mecânica continuam indisponíveis.", -1, "cnc-preprocessor"));
        } else common.add(value("Posições de fresagem", "Não se aplicam a este perfil", -1, "cnc-preprocessor"));
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
        double parkingZ=profile.isLaser() ? clearance : lastTravelZ;
        if (pos.endX() != null && pos.endZ() != null && pos.endZ() < parkingZ)
            issues.add(new Issue(false, "Estacionamento XY em Z " + number(parkingZ) + "; só depois desce para End Z " + number(pos.endZ()) + ".", target(-1, "cnc-end-z")));
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
            var details = new ArrayList<>(List.of(value("Furos / rasgos", count[0] + " / " + count[1], id, "drill-tools"),
                    value("Profundidade efetiva", number(-p.effectiveDepth()) + " " + unit(image.units()) + " (Cut Z + Offset Z)", id, "drill-cut-z"),
                    value("Offset Z", number(p.offsetZ()), id, "drill-offset-z"),
                    value("Passes", passes(p.effectiveDepth(), p.multiDepth(), p.depthPerPass()), id, "drill-multi-depth"),
                    value("Dwell", p.dwell() ? number(p.dwellSeconds()) + " s" : "Desativado", id, "drill-dwell-time"),
                    value("Operação dos rasgos", p.slots().enabled() ? "Drill slots: furos sobrepostos" : "Fresagem lateral", id, "drill-slots"),
                    value("Sobreposição / último furo", number(p.slots().overlapPercent()) + "% / "
                            + (p.slots().addLastHole() ? "Ativado" : "Desativado"), id, "drill-slot-overlap")));
            if (profile == GCodePreprocessor.TOOLCHANGE_MANUAL && result.options().pauseForToolChange() && count[0]+count[1]>0)
                details.add(value("Ajuste manual em Z0", "Feed Z " + number(p.feedRate()) + " " + unit(image.units())
                        + "/min; spindle parado; não é sondagem", id, "drill-feed-z"));
            rows.add(new Tool(id, "T" + id + (count[0] + count[1] == 0 ? " (vazia)" : ""), number(image.toolDiameters().get(id)), number(-p.effectiveDepth()),
                    number(p.safeZ()), "—", number(p.feedRate()), profile.isRoland() ? "—" : powerText(profile, p.spindleSpeedRpm()), details));
            if (count[0] + count[1] == 0) issues.add(new Issue(false, "T" + id + " não contém furos nem rasgos.", target(id, "drill-tools")));
            if (count[1] > 0) issues.add(new Issue(false, p.slots().enabled()
                    ? "T" + id + ": Drill slots converte rasgos em furos. A sequência pode deixar material entre círculos; não equivale à fresagem integral. Confira a prévia e a adequação da broca."
                    : "T" + id + ": rasgos são fresados ao longo do eixo; confirme a ferramenta e o avanço lateral. Não é apenas mergulho de broca.", target(id, "drill-slots")));
            if (p.spindleSpeedRpm() == 0 && !profile.isRoland() && profile != GCodePreprocessor.REPETIER)
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
        if (profile.isLaser()) {
            issues.add(new Issue(false, "Laser: potência é o valor S/PWM solicitado ao controlador, não RPM físico. A prévia mostra trajetos, mas não simula feixe, material, intertravamentos nem configurações elétricas; não é validação de segurança.", null));
            issues.add(new Issue(false, "Laser: uma passagem por caminho, sem Multi-Depth, troca mecânica, Dwell ou Extra Cut. Pontos isolados exigem tempo de exposição e são recusados. End Z/XY é comum; avanço/potência são individuais por grupo e Z_laser/Marlin permitem foco individual.", null));
            if (profile == GCodePreprocessor.GRBL_LASER || profile == GCodePreprocessor.Z_LASER)
                issues.add(new Issue(false, "GRBL laser: confirme $30 (escala de S), $32 (modo laser) e a ligação de controle. O FX comanda M5 antes dos posicionamentos; não detecta a configuração da máquina nem assume que S representa uma porcentagem.", null));
            else {
                issues.add(new Issue(false, "Marlin laser: confirme INCH_MODE_SUPPORT para IN, a configuração da saída e o modo padrão de controle, não inline. G0_FEEDRATE fixo pode ignorar Feed rapids. M400 inclui o estacionamento final, mas não confirma o desligamento físico do feixe.", null));
                if (profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN)
                    issues.add(new Issue(false, "Marlin FAN: M106/M107 usam PWM de 1 a 255 neste perfil. Confirme FAN pin e LASER_SYNCHRONOUS_M106_M107; velocidade de ventilador não equivale a potência óptica calibrada.", null));
            }
        }
        if (profile == GCodePreprocessor.REPETIER) {
            issues.add(new Issue(false, "Repetier: o valor de potência é PWM FAN de 0 a 255, não RPM. M106/M107 exigem uma saída FAN e circuito adequados; não confirmam rotação física. Potência zero não liga a saída nem garante parada final se ela já estava ligada.", null));
            issues.add(new Issue(false, "Repetier: @pause solicita a troca ao operador via Repetier-Host; não seleciona uma ferramenta automaticamente. M84 libera os motores e pode perder a referência. Confira fixação, origem e altura antes de retomar; a prévia não simula essa perda.", null));
        }
        if (profile == GCodePreprocessor.BERTA_CNC) {
            issues.add(new Issue(false, "Berta: confirme a configuração dos comandos M110/M111 e da origem G54. Seus efeitos dependem da máquina e não são simulados; não use o perfil apenas por aceitar G-code genérico.", null));
            issues.add(new Issue(false, "Berta: G64 P0.03 mantém o valor do perfil Python na unidade do programa: 0,03 mm em MM ou 0,762 mm em IN. É uma tolerância de trajetória configurada, não precisão garantida. Confira antes de exportar.", null));
        }
        if (profile == GCodePreprocessor.REPETIER || profile == GCodePreprocessor.MARLIN) {
            issues.add(new Issue(false, "Dwell: o tempo da interface continua em segundos; G4 P é emitido em milissegundos, inclusive em IN. O avanço nativo é por minuto, sem G94.", null));
        }
        if (profile == GCodePreprocessor.TOOLCHANGE_MANUAL) {
            issues.add(new Issue(false, "Troca manual: três pausas M0 por ferramenta. Entre elas há um movimento comandado até Z0 usando o Feed Z da fresa e retorno ao Tool change Z; G90 é restaurado antes dos movimentos verticais. Não é sondagem nem detecção automática de contato.", null));
            issues.add(new Issue(false, "Confirme o zero de peça, a posição XY e a altura livre antes de continuar cada pausa. Movimentos do operador e mudanças de origem/unidades não são simulados.", null));
        }
        if (profile == GCodePreprocessor.TOOLCHANGE_CUSTOM) {
            issues.add(new Issue(false, "Troca Custom: Tn/M6 quando a troca está ativada, sem M0 adicional. A altura e o XY são comandados antes da macro; depois, G90 e Z são restaurados antes do próximo XY. A macro, a seleção física da ferramenta e mudanças de origem não são simuladas.", null));
        }
        if (profile == GCodePreprocessor.ISEL_ICP_CNC) {
            issues.add(new Issue(false, "ISEL ICP: somente mm; coordenadas em micrometros, VEL/FASTVEL em micrometros por segundo e WAIT em milissegundos. Valores sao truncados para inteiros, sem aumentar a precisao do controlador.", null));
            issues.add(new Issue(false, "ISEL ICP: GETTOOL executa a troca automatica mesmo sem pausa manual. Confirme a macro, o referenciamento e as alturas; a previa nao simula essa troca nem o tempo dos rapidos. O estacionamento preserva a origem, sem WPCLEAR.", null));
        }
        if (profile == GCodePreprocessor.LINE_XYZ) {
            issues.add(new Issue(false, "line_xyz: a subida inicial e a restauracao de Z apos M6 mantem X/Y atuais. XYZ sao explicitados quando conhecidos; confirme que o controlador aceita o preambulo vertical. A macro M6 nao e simulada.", null));
        }
        if (profile == GCodePreprocessor.ISEL_CNC) {
            issues.add(new Issue(false, "ISEL CNC: o perfil usa mm, G71/G94 e T1–T128. Os tempos Dwell configurados em segundos são convertidos para milissegundos no G-code.", null));
            issues.add(new Issue(false, "ISEL Remote DIN/ISO: M01 é parada com interação do operador. Em outros controladores confirme se a parada é opcional e está habilitada; não presuma que M01 sempre terá o mesmo comportamento.", null));
            issues.add(new Issue(false, "ISEL CNC: alturas/XY de troca são emitidos antes de M06 e Z é restaurado depois. A macro de troca e o referenciamento físico não são simulados. M30 termina após o estacionamento final.", null));
        }
        if (profile == GCodePreprocessor.MARLIN) {
            issues.add(new Issue(false, "Marlin: confirme SPINDLE_FEATURE e CUTTER_POWER_UNIT=RPM. O valor S só representa RPM nessa configuração.", null));
            issues.add(new Issue(false, "Marlin: Tn pode selecionar extrusores; Tn/M6 exigem configuração ou macro compatível com CNC. M0 depende do suporte de pausa; IN exige INCH_MODE_SUPPORT.", null));
            issues.add(new Issue(false, "Marlin: cada G0 recebe Feed rapids, mas G0_FEEDRATE fixo pode ignorar F. M400 espera os movimentos do planejador, não confirma parada física do spindle.", null));
        }
        issues.add(new Issue(false, profile.requiresProbe()
                ? "A prévia de sondagem não simula G31, contato, G92 ou macros de troca. Não valida a segurança da máquina."
                : "A prévia é 2D: não simula remoção de material, grampos, limites da máquina nem movimentos de macros M6.", null));
    }
    private static Target target(int tool, String field) { return new Target(tool, field); }
    private static String powerText(GCodePreprocessor profile, int value) {
        return number(value) + (profile == GCodePreprocessor.REPETIER ? " PWM (0–255)"
                : profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN ? " PWM (1–255)" : profile.isLaser() ? " S (laser)" : "");
    }
    private static Value value(String label, String text, int tool, String field) { return new Value(label, text, target(tool, field)); }
    private static String xy(Double x, Double y) { return x == null ? "Mantém a posição atual" : number(x) + "; " + number(y); }
    static String number(double value) { return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static String unit(String units) { return "MM".equalsIgnoreCase(units) ? "mm" : "in"; }
    private static String passes(double depth, boolean multi, double step) {
        return !multi ? "1 passe" : MachiningPasses.count(depth, true, step) + " passe(s); passo máximo " + number(step);
    }
    private CncPreflight() { }
}
