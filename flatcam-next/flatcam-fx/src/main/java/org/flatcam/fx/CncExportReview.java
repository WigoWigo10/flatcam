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
        String units=I18n.text("ui.ea4502c664f4");
        if (text==null || text.isBlank()) issues.add(new CncPreflight.Issue(true,I18n.text("ui.eaa369479aec"),null));
        else {
            GCodeToolpathParser.ToolpathStats stats=cached;
            try {
                if (stats==null) {
                    var parsed=GCodeToolpathParser.parse(text,cancellation,progress);
                    stats=parsed.stats(); units=parsed.units();
                    if (parsed.warning()!=null && !parsed.warning().isBlank()) issues.add(new CncPreflight.Issue(false,I18n.text("ui.45d94a8a16dd")+parsed.warning(),null));
                }
                if (stats!=null) {
                    units=stats.units();
                    for (var tool:stats.tools()) rows.add(new CncPreflight.Tool(tool.toolId(),"T"+tool.toolId(),
                            tool.diameter()==null ? I18n.text("ui.8cb1fdc1deaf") : CncPreflight.number(tool.diameter()),
                            CncPreflight.number(tool.deepestZ()),I18n.text("ui.4be248e6ddac"),I18n.text("ui.4be248e6ddac"),I18n.text("ui.4be248e6ddac"),I18n.text("ui.4be248e6ddac"),
                            List.of(new CncPreflight.Value(I18n.text("ui.66903ace778f"),tool.drills()+" / "+tool.slots(),null))));
                }
            } catch (IllegalArgumentException unavailable) {
                issues.add(new CncPreflight.Issue(false,I18n.text("ui.45d94a8a16dd")+unavailable.getMessage()+I18n.text("ui.42a2d38b1b88"),null));
            }
        }
        cancellation.throwIfCancellationRequested();
        if (rows.isEmpty() && text!=null && !text.isBlank()) issues.add(new CncPreflight.Issue(false,I18n.text("ui.67e488f6e9e9"),null));
        issues.add(new CncPreflight.Issue(false,I18n.text("ui.5d6e1f45e6a8"),null));
        issues.add(new CncPreflight.Issue(false,I18n.text("ui.d8e3b32607a2"),null));
        return new CncPreflight.Report(name,I18n.text("ui.21550e296214"),units,I18n.text("ui.5cb29126066b"),rows,
                List.of(new CncPreflight.Value(I18n.text("ui.2428ef0f3f8a"),I18n.text("ui.e38e35e94c64"),null)),issues);
    }
    static String terminalText(CncPreflight.Report report) {
        StringBuilder text=new StringBuilder(I18n.text("ui.7977dcef501a")+report.source()+I18n.text("ui.1f7b278a923c")+report.units()+"\n");
        for (var tool:report.tools()) text.append(tool.name()).append(I18n.text("ui.b71721055eb7")).append(tool.cutZ()).append(I18n.text("ui.4c98bd5fc861")).append(tool.diameter()).append('\n');
        for (var issue:report.issues()) text.append(issue.blocking() ? I18n.text("ui.5ce3ac6affec") : I18n.text("ui.ad5f913ae8e0")).append(issue.text()).append('\n');
        return text.toString().stripTrailing();
    }
    private CncExportReview() { }
}
