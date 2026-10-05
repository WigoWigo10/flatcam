package org.flatcam.fx;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/** Presentation-only lexical colours: never validates or executes machine code. */
final class CodeSyntax {
    enum Language {
        MACHINE("G-code / CNC"), GERBER("Gerber"), EXCELLON("Excellon"), GEOMETRY("Geometry / WKT");
        final String label;
        Language(String label) { this.label = label; }
    }
    private static final Pattern TOKENS = Pattern.compile(
            "(?<comment>\\([^)]*\\)?|;[^\\r\\n]*)"
            + "|(?<string>\"[^\"]*\")"
            + "|(?<command>(?i:[GMDTO])[+-]?\\d+(?:\\.\\d+)?|\\^[A-Z]{2}|![A-Z]+|(?i:IN|PA|PU|PD|SP)(?=[\\d; ,+-]|$))"
            + "|(?<axis>(?i:[XYZIJKABCRFSV])(?=[+-]?(?:\\d|\\.)))"
            + "|(?<number>[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][+-]?\\d+)?)"
            + "|(?<keyword>[A-Za-z_][A-Za-z_0-9]*)|(?<punctuation>[%*^!#=,;])");
    private static final Pattern TERMINATED_COMMANDS = Pattern.compile(TOKENS.pattern().replace("|;[^\\r\\n]*", ""));
    private static final Pattern WKT = Pattern.compile(TOKENS.pattern().replace("\\([^)]*\\)?|", ""));
    private CodeSyntax() { }

    static StyleSpans<Collection<String>> highlight(String line, Language language) {
        var builder = new StyleSpansBuilder<Collection<String>>();
        // Pathological lines stay visible/editable without thousands of styled segments.
        if (line.length() > 8192) { builder.add(List.of(), line.length()); return builder.create(); }
        if (language == Language.GERBER && line.stripLeading().startsWith("G04")) {
            builder.add(List.of("code-comment"), line.length()); return builder.create();
        }
        boolean terminated = line.stripLeading().matches("(?i)(?:\\^IN|IN|PA|PU|PD|SP|\\^PA|!MC).*?");
        var matcher = (language == Language.GEOMETRY ? WKT : terminated ? TERMINATED_COMMANDS : TOKENS).matcher(line); int last = 0;
        while (matcher.find()) {
            builder.add(List.of(), matcher.start() - last);
            String kind = matcher.group("comment") != null ? "comment" : matcher.group("string") != null ? "string"
                    : matcher.group("command") != null ? "command" : matcher.group("axis") != null ? "axis"
                    : matcher.group("number") != null ? "number" : matcher.group("keyword") != null ? "keyword" : "punctuation";
            if (language == Language.GEOMETRY && (kind.equals("axis") || kind.equals("command"))) kind = "keyword";
            builder.add(List.of("code-" + kind), matcher.end() - matcher.start()); last = matcher.end();
        }
        builder.add(List.of(), line.length() - last); return builder.create();
    }
}
