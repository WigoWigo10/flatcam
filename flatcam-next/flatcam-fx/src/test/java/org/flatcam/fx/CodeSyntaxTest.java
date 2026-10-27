package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeSyntaxTest {
    private static List<String> styles(String text, CodeSyntax.Language language) {
        var result = new ArrayList<String>();
        for (var span : CodeSyntax.highlight(text, language))
            for (int index = 0; index < span.getLength(); index++) result.add(span.getStyle().stream().findFirst().orElse("plain"));
        assertEquals(text.length(), result.size()); return result;
    }
    @Test void commandsAxesNumbersAndCommentsAreDistinct() {
        String code = "G01 X-2.4 F100 (pad) ; end";
        var styles = styles(code, CodeSyntax.Language.MACHINE);
        assertEquals("code-command", styles.get(0)); assertEquals("code-axis", styles.get(4));
        assertEquals("code-number", styles.get(5)); assertEquals("code-comment", styles.get(code.indexOf('(')));
        assertEquals("code-comment", styles.get(code.indexOf(';')));
    }
    @Test void gerberG04CommentDoesNotMislabelGCodeDwell() {
        assertEquals("code-comment", styles("G04 Gerber comment*", CodeSyntax.Language.GERBER).getFirst());
        assertEquals("code-command", styles("G04 P0.1", CodeSyntax.Language.MACHINE).getFirst());
    }
    @Test void hpglAndRolandSemicolonsAreTerminatorsNotComments() {
        String hpgl = "IN;PA10,20;PD30,40;";
        assertEquals("code-command", styles(hpgl, CodeSyntax.Language.MACHINE).get(hpgl.indexOf("PD")));
        assertFalse(styles("^IN;!MC1;", CodeSyntax.Language.MACHINE).contains("code-comment"));
    }
    @Test void allLanguagesAndEmptyLinesPreserveOffsets() {
        for (var language : CodeSyntax.Language.values()) {
            styles("%FSLAX24Y24*%", language); styles("T01C0.8", language); styles("LINESTRING (0 0, 1 2)", language); styles("", language);
        }
        assertFalse(styles("LINESTRING (0 0, 1 2)", CodeSyntax.Language.GEOMETRY).contains("code-comment"));
    }
    @Test void tclScriptsShowCommentsVariablesOptionsAndNegativeNumbers() {
        assertTrue(styles("  # set PATH x", CodeSyntax.Language.TCL).stream().skip(2).allMatch("code-comment"::equals));
        String line = "cutout ${PATH}/a.gbr -dia 1.2 -margin -0.5 [get_sys x] \"q\"";
        var styles = styles(line, CodeSyntax.Language.TCL);
        assertEquals("code-keyword", styles.get(0));
        assertEquals("code-axis", styles.get(line.indexOf("${")));
        assertEquals("code-command", styles.get(line.indexOf("-dia")));
        assertEquals("code-number", styles.get(line.indexOf("1.2")));
        assertEquals("code-number", styles.get(line.indexOf("-0.5")), "a negative value is not an option");
        assertEquals("code-punctuation", styles.get(line.indexOf('[')));
        assertEquals("code-string", styles.get(line.indexOf('"')));
        assertFalse(styles("set a 1 ;# not a comment start", CodeSyntax.Language.TCL).getFirst().equals("code-comment"));
    }
    @Test void pathologicalLongLinesHaveNoUnboundedTokenCount() {
        var spans = CodeSyntax.highlight("G1 X0 ".repeat(10000), CodeSyntax.Language.MACHINE);
        assertEquals(60000, spans.length()); assertEquals(1, spans.getSpanCount());
    }
}
