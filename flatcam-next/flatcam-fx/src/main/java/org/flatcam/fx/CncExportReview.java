package org.flatcam.fx;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.GCodeToolpathParser;

/** Review the actual exported text, never substitute current CAM drafts or claim machine safety. */
final class CncExportReview {
    static CncPreflight.Report create(String name, String text, GCodeToolpathParser.ToolpathStats cached,
                                      CancellationToken cancellation, ProgressCallback progress) {
        cancellation.throwIfCancellationRequested();
        var issues=new ArrayList<CncPreflight.Issue>(); var rows=new ArrayList<CncPreflight.Tool>();
        String units="Não inferidas";
        if (text==null || text.isBlank()) issues.add(new CncPreflight.Issue(true,"Programa vazio; nada será exportado.",null));
        else {
            GCodeToolpathParser.ToolpathStats stats=cached;
            try {
                if (stats==null) {
                    var parsed=GCodeToolpathParser.parse(text,cancellation,progress);
                    stats=parsed.stats(); units=parsed.units();
                    if (parsed.warning()!=null && !parsed.warning().isBlank()) issues.add(new CncPreflight.Issue(false,"Prévia indisponível: "+parsed.warning(),null));
                }
                if (stats!=null) {
                    units=stats.units();
                    for (var tool:stats.tools()) rows.add(new CncPreflight.Tool(tool.toolId(),"T"+tool.toolId(),
                            tool.diameter()==null ? "Não declarado" : CncPreflight.number(tool.diameter()),
                            CncPreflight.number(tool.deepestZ()),"Consultar código","Consultar código","Consultar código","Consultar código",
                            List.of(new CncPreflight.Value("Mergulhos / percursos",tool.drills()+" / "+tool.slots(),null))));
                }
            } catch (IllegalArgumentException unavailable) {
                issues.add(new CncPreflight.Issue(false,"Prévia indisponível: "+unavailable.getMessage()+". Revise o texto no editor; parâmetros não serão inventados.",null));
            }
        }
        cancellation.throwIfCancellationRequested();
        if (rows.isEmpty() && text!=null && !text.isBlank()) issues.add(new CncPreflight.Issue(false,"Ferramentas não identificadas pela prévia; confira seleção, diâmetros e profundidades no editor.",null));
        issues.add(new CncPreflight.Issue(false,"Programa existente: valores acima vêm do texto/prévia, não dos rascunhos atuais. Feed, spindle e Travel Z podem variar ao longo do programa; confira o editor.",null));
        issues.add(new CncPreflight.Issue(false,"Prévia 2D não simula grampos, limites físicos, G92, sondagem ou macros M6. Exige conferência de origem, ferramenta e teste a seco.",null));
        return new CncPreflight.Report(name,"Revisão do programa existente",units,"Do próprio programa",rows,
                List.of(new CncPreflight.Value("Texto exportado","Programa atual, incluindo preâmbulo/pós-âmbulo quando solicitados",null)),issues);
    }
    static String terminalText(CncPreflight.Report report) {
        StringBuilder text=new StringBuilder("[REVISÃO CNC] "+report.source()+" — unidades: "+report.units()+"\n");
        for (var tool:report.tools()) text.append(tool.name()).append(": Z mínimo ").append(tool.cutZ()).append("; diâmetro ").append(tool.diameter()).append('\n');
        for (var issue:report.issues()) text.append(issue.blocking() ? "ERRO: " : "AVISO: ").append(issue.text()).append('\n');
        return text.toString().stripTrailing();
    }
    private CncExportReview() { }
}
