package org.flatcam.cam.tcl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.flatcam.cam.CancellationToken;

/**
 * A small, deliberately reduced Tcl-like command interpreter - not a Tcl implementation. Python
 * FlatCAM's Terminal embeds a real Tcl 8.6 interpreter ({@code tkinter.Tcl()}) and registers its
 * ~69 FlatCAM commands as Tcl procs; there is no actively maintained pure-Java Tcl, and the only
 * one on Maven Central (`jacl:jacl:1.2.6`) is an unmaintained, year-2000, Tcl 8.0/8.2-era jar of
 * unclear provenance. This class instead covers exactly what FlatCAM's own example scripts
 * ({@code assets/examples/*.FlatScript}) and realistic automation use: variables, command/variable
 * substitution, and the handful of control commands below - not full Tcl (no {@code proc}, no
 * real Tcl list quoting, no {@code catch}/{@code switch}/{@code string}/{@code array}). A script
 * that leans on more of real Tcl than that will not run here; this is a scoped, documented
 * divergence from the Python oracle (see PLANO_PARIDADE.md), not a parity bug.
 *
 * <p><b>Word types, same distinction Tcl itself makes:</b> a {@code {braced}} word is passed to
 * its command exactly as written, with no substitution ever applied to it by this class - that is
 * what lets {@code if}/{@code while}/{@code foreach} defer evaluating their condition/body until
 * each time it actually runs, and what lets {@code puts {literal $text}} print a dollar sign
 * un-substituted. Every other word ({@code "quoted"} or bare) is substituted exactly once, before
 * its command runs. {@code if}/{@code while}/{@code expr} additionally re-run substitution
 * themselves on their own condition/expression text - harmless on already-substituted plain text -
 * so a condition works whether or not the script happened to brace it.
 *
 * <p><b>Substitution</b> recognizes {@code $name}/{@code ${name}} (reading an undefined variable
 * is an error, matching Tcl), {@code [command]} (recursively {@link #eval evaluated}, substituted
 * with its result), and the backslash escapes {@code \\ \" \$ \[ \] \{ \} \n \t \r} (an
 * unrecognized {@code \X} drops the backslash, keeping {@code X}).
 *
 * <p>Built in: {@code set}, {@code unset}, {@code incr}, {@code append}, {@code puts}, {@code if}
 * /{@code elseif}/{@code else}, {@code while}, {@code foreach}, {@code expr}, {@code list},
 * {@code llength}, {@code lindex}. {@code list}/{@code llength}/{@code lindex} use plain
 * whitespace-separated elements, not real Tcl's brace-quoting list format - another deliberate
 * simplification, fine for the simple name/number lists FlatCAM commands pass around.
 *
 * <p>A {@code while}/{@code foreach} loop is capped at {@value #MAX_LOOP_ITERATIONS} iterations
 * and raises a {@link TclException} past that - a defensive addition with no Python equivalent,
 * so a runaway script can't hang the caller forever.
 */
public final class TclInterpreter {

    private static final int MAX_LOOP_ITERATIONS = 1_000_000;

    private enum WordType { BARE, QUOTED, BRACED }

    private record Word(String raw, WordType type) {
    }

    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Map<String, TclCommand> commands = new LinkedHashMap<>();
    private Consumer<String> outputSink = text -> { };
    private CancellationToken cancellation = CancellationToken.none();

    public TclInterpreter() {
        registerBuiltins();
    }

    /** Registers (or overrides) a command - this is how a FlatCAM-specific command set is added. */
    public void register(String name, TclCommand command) {
        commands.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(command, "command"));
    }

    public Set<String> commandNames() {
        return Set.copyOf(commands.keySet());
    }

    /** Routes {@code puts}' multi-argument form (the real-channel form Python falls back to). */
    public void setOutputSink(Consumer<String> sink) {
        this.outputSink = Objects.requireNonNull(sink, "sink");
    }

    void print(String text) {
        outputSink.accept(text);
    }

    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    public String getVariable(String name) throws TclException {
        String value = variables.get(name);
        if (value == null) {
            throw new TclException("can't read \"" + name + "\": no such variable");
        }
        return value;
    }

    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }

    public void unsetVariable(String name) {
        variables.remove(name);
    }

    /**
     * Evaluates a script (one or more commands) and returns the last command's result - same
     * convention as Tcl's own {@code eval}. A script with no commands (blank, or comments only)
     * returns "".
     */
    public String eval(String script) throws TclException {
        cancellation.throwIfCancellationRequested();
        String continued = collapseLineContinuations(script);
        List<List<Word>> parsedCommands = splitScript(continued);
        String result = "";
        for (List<Word> words : parsedCommands) {
            cancellation.throwIfCancellationRequested();
            List<String> resolved = new ArrayList<>(words.size());
            for (Word word : words) {
                resolved.add(word.type() == WordType.BRACED ? word.raw() : substitute(word.raw()));
            }
            String name = resolved.get(0);
            TclCommand command = commands.get(name);
            if (command == null) {
                throw new TclException("invalid command name \"" + name + "\"");
            }
            result = command.execute(this, resolved.subList(1, resolved.size()));
        }
        return result;
    }

    /** Single-worker execution; recursive evaluations inherit the same cancellation token. */
    public String eval(String script, CancellationToken token) throws TclException {
        CancellationToken previous = cancellation;
        cancellation = Objects.requireNonNull(token);
        try { return eval(script); }
        finally { cancellation = previous; }
    }

    /**
     * Applies {@code $var}/{@code [cmd]} substitution and backslash escapes to raw word text -
     * exposed so control commands can re-apply it to their own (possibly still-raw) condition or
     * expression text. See the class doc's word-type rule for when this already happened.
     */
    public String substitute(String raw) throws TclException {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            cancellation.throwIfCancellationRequested();
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < n) {
                result.append(unescape(raw.charAt(i + 1)));
                i += 2;
                continue;
            }
            if (c == '$') {
                if (i + 1 < n && raw.charAt(i + 1) == '{') {
                    int close = raw.indexOf('}', i + 2);
                    if (close < 0) {
                        throw new TclException("missing close-brace for variable name");
                    }
                    result.append(getVariable(raw.substring(i + 2, close)));
                    i = close + 1;
                } else {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(raw.charAt(j)) || raw.charAt(j) == '_')) {
                        j++;
                    }
                    if (j == i + 1) {
                        result.append('$');
                        i++;
                    } else {
                        result.append(getVariable(raw.substring(i + 1, j)));
                        i = j;
                    }
                }
                continue;
            }
            if (c == '[') {
                int depth = 1;
                int j = i + 1;
                boolean inQuotes = false;
                while (j < n && depth > 0) {
                    char cj = raw.charAt(j);
                    if (cj == '"') {
                        inQuotes = !inQuotes;
                    } else if (!inQuotes && cj == '[') {
                        depth++;
                    } else if (!inQuotes && cj == ']') {
                        depth--;
                    }
                    if (depth > 0) j++;
                }
                if (depth != 0) {
                    throw new TclException("missing close-bracket");
                }
                result.append(eval(raw.substring(i + 1, j)));
                i = j + 1;
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            default -> c;
        };
    }

    /** A backslash immediately followed by a newline (and any indentation after it) joins the two lines with a space. */
    private String collapseLineContinuations(String script) {
        StringBuilder result = new StringBuilder(script.length());
        int i = 0;
        int n = script.length();
        while (i < n) {
            cancellation.throwIfCancellationRequested();
            char c = script.charAt(i);
            if (c == '\\' && i + 1 < n && (script.charAt(i + 1) == '\n'
                    || (script.charAt(i + 1) == '\r' && i + 2 < n && script.charAt(i + 2) == '\n'))) {
                i += script.charAt(i + 1) == '\r' ? 3 : 2;
                while (i < n && (script.charAt(i) == ' ' || script.charAt(i) == '\t')) {
                    cancellation.throwIfCancellationRequested();
                    i++;
                }
                result.append(' ');
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private List<List<Word>> splitScript(String script) throws TclException {
        List<List<Word>> commandList = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        int[] cursor = {0};
        int n = script.length();
        while (cursor[0] < n) {
            cancellation.throwIfCancellationRequested();
            char c = script.charAt(cursor[0]);
            if (c == ' ' || c == '\t' || c == '\r') {
                cursor[0]++;
                continue;
            }
            if (c == '\n' || c == ';') {
                cursor[0]++;
                if (!current.isEmpty()) {
                    commandList.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            if (c == '#' && current.isEmpty()) {
                while (cursor[0] < n && script.charAt(cursor[0]) != '\n') {
                    cancellation.throwIfCancellationRequested();
                    cursor[0]++;
                }
                continue;
            }
            current.add(parseWord(script, cursor));
        }
        if (!current.isEmpty()) {
            commandList.add(current);
        }
        return commandList;
    }

    private Word parseWord(String s, int[] cursor) throws TclException {
        char c = s.charAt(cursor[0]);
        if (c == '{') {
            return parseBraced(s, cursor);
        }
        if (c == '"') {
            return parseQuoted(s, cursor);
        }
        return parseBare(s, cursor);
    }

    private Word parseBraced(String s, int[] cursor) throws TclException {
        int n = s.length();
        cursor[0]++; // consume '{'
        int depth = 1;
        StringBuilder sb = new StringBuilder();
        while (cursor[0] < n) {
            cancellation.throwIfCancellationRequested();
            char ch = s.charAt(cursor[0]);
            if (ch == '\\' && cursor[0] + 1 < n && (s.charAt(cursor[0] + 1) == '{' || s.charAt(cursor[0] + 1) == '}')) {
                sb.append(ch).append(s.charAt(cursor[0] + 1));
                cursor[0] += 2;
                continue;
            }
            if (ch == '{') {
                depth++;
                sb.append(ch);
                cursor[0]++;
                continue;
            }
            if (ch == '}') {
                depth--;
                cursor[0]++;
                if (depth == 0) {
                    return new Word(sb.toString(), WordType.BRACED);
                }
                sb.append(ch);
                continue;
            }
            sb.append(ch);
            cursor[0]++;
        }
        throw new TclException("missing close-brace");
    }

    private Word parseQuoted(String s, int[] cursor) throws TclException {
        int n = s.length();
        cursor[0]++; // consume opening quote
        StringBuilder sb = new StringBuilder();
        int bracketDepth = 0;
        while (cursor[0] < n) {
            cancellation.throwIfCancellationRequested();
            char ch = s.charAt(cursor[0]);
            if (ch == '\\' && cursor[0] + 1 < n) {
                sb.append(ch).append(s.charAt(cursor[0] + 1));
                cursor[0] += 2;
                continue;
            }
            if (ch == '[') {
                bracketDepth++;
                sb.append(ch);
                cursor[0]++;
                continue;
            }
            if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
                sb.append(ch);
                cursor[0]++;
                continue;
            }
            if (ch == '"' && bracketDepth == 0) {
                cursor[0]++;
                return new Word(sb.toString(), WordType.QUOTED);
            }
            sb.append(ch);
            cursor[0]++;
        }
        throw new TclException("missing closing \"");
    }

    private Word parseBare(String s, int[] cursor) throws TclException {
        int n = s.length();
        StringBuilder sb = new StringBuilder();
        int bracketDepth = 0;
        int braceDepth = 0;
        while (cursor[0] < n) {
            cancellation.throwIfCancellationRequested();
            char ch = s.charAt(cursor[0]);
            if (ch == '\\' && cursor[0] + 1 < n) {
                sb.append(ch).append(s.charAt(cursor[0] + 1));
                cursor[0] += 2;
                continue;
            }
            if (bracketDepth == 0 && braceDepth == 0
                    && (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == ';')) {
                break;
            }
            if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            } else if (ch == '{') {
                braceDepth++;
            } else if (ch == '}' && braceDepth > 0) {
                braceDepth--;
            }
            sb.append(ch);
            cursor[0]++;
        }
        if (sb.isEmpty()) {
            throw new TclException("internal parser error: empty word at position " + cursor[0]);
        }
        return new Word(sb.toString(), WordType.BARE);
    }

    private void registerBuiltins() {
        register("set", (interp, args) -> {
            if (args.isEmpty() || args.size() > 2) {
                throw new TclException("wrong # args: should be \"set varName ?newValue?\"");
            }
            if (args.size() == 1) {
                return interp.getVariable(args.get(0));
            }
            interp.setVariable(args.get(0), args.get(1));
            return args.get(1);
        });

        register("unset", (interp, args) -> {
            for (String name : args) {
                interp.unsetVariable(name);
            }
            return "";
        });

        register("incr", (interp, args) -> {
            if (args.isEmpty() || args.size() > 2) {
                throw new TclException("wrong # args: should be \"incr varName ?increment?\"");
            }
            double current = parseNumber(interp.getVariable(args.get(0)));
            double delta = args.size() == 2 ? parseNumber(args.get(1)) : 1;
            String updated = formatOperand(current + delta);
            interp.setVariable(args.get(0), updated);
            return updated;
        });

        register("append", (interp, args) -> {
            if (args.isEmpty()) {
                throw new TclException("wrong # args: should be \"append varName ?value value ...?\"");
            }
            StringBuilder value = new StringBuilder(interp.hasVariable(args.get(0)) ? interp.getVariable(args.get(0)) : "");
            for (int i = 1; i < args.size(); i++) {
                value.append(args.get(i));
            }
            interp.setVariable(args.get(0), value.toString());
            return value.toString();
        });

        register("puts", (interp, args) -> {
            if (args.size() == 1) {
                // Matches Python FlatCAM's own shell: a single-argument puts returns its text as
                // the command's result (shown by the Terminal) instead of printing to a channel.
                return args.get(0);
            }
            if (args.isEmpty()) {
                throw new TclException("wrong # args: should be \"puts ?-nonewline? ?channel? string\"");
            }
            boolean noNewline = "-nonewline".equals(args.get(0));
            String text = args.get(args.size() - 1);
            interp.print(noNewline ? text : text + "\n");
            return "";
        });

        register("if", (interp, args) -> {
            int i = 0;
            while (i < args.size()) {
                if (i + 1 >= args.size()) {
                    throw new TclException("wrong # args: should be \"if condition body ?elseif ... ? ?else body?\"");
                }
                boolean condition = TclExpr.evaluateBoolean(interp.substitute(args.get(i)));
                if (condition) {
                    return interp.eval(args.get(i + 1));
                }
                i += 2;
                if (i >= args.size()) {
                    return "";
                }
                String keyword = args.get(i);
                if ("else".equals(keyword)) {
                    if (i + 1 >= args.size()) {
                        throw new TclException("wrong # args: \"if ... else body\"");
                    }
                    return interp.eval(args.get(i + 1));
                }
                if ("elseif".equals(keyword)) {
                    i += 1;
                    continue;
                }
                throw new TclException("illegal if syntax near \"" + keyword + "\"");
            }
            return "";
        });

        register("while", (interp, args) -> {
            if (args.size() != 2) {
                throw new TclException("wrong # args: should be \"while condition body\"");
            }
            String conditionRaw = args.get(0);
            String bodyRaw = args.get(1);
            int iterations = 0;
            while (TclExpr.evaluateBoolean(interp.substitute(conditionRaw))) {
                interp.cancellation.throwIfCancellationRequested();
                interp.eval(bodyRaw);
                if (++iterations > MAX_LOOP_ITERATIONS) {
                    throw new TclException("too many iterations (possible infinite loop) in \"while\"");
                }
            }
            return "";
        });

        register("foreach", (interp, args) -> {
            if (args.size() != 3) {
                throw new TclException("wrong # args: should be \"foreach varName list body\"");
            }
            String variableName = args.get(0);
            String listText = args.get(1);
            String bodyRaw = args.get(2);
            int iterations = 0;
            for (String element : listText.isBlank() ? new String[0] : listText.trim().split("\\s+")) {
                interp.cancellation.throwIfCancellationRequested();
                interp.setVariable(variableName, element);
                interp.eval(bodyRaw);
                if (++iterations > MAX_LOOP_ITERATIONS) {
                    throw new TclException("too many iterations (possible infinite loop) in \"foreach\"");
                }
            }
            return "";
        });

        register("expr", (interp, args) -> {
            if (args.isEmpty()) {
                throw new TclException("wrong # args: should be \"expr arg ?arg ...?\"");
            }
            return TclExpr.evaluate(interp.substitute(String.join(" ", args)));
        });

        register("list", (interp, args) -> String.join(" ", args));

        register("llength", (interp, args) -> {
            if (args.size() != 1) {
                throw new TclException("wrong # args: should be \"llength list\"");
            }
            String list = args.get(0);
            return Integer.toString(list.isBlank() ? 0 : list.trim().split("\\s+").length);
        });

        register("lindex", (interp, args) -> {
            if (args.size() != 2) {
                throw new TclException("wrong # args: should be \"lindex list index\"");
            }
            String list = args.get(0);
            String[] elements = list.isBlank() ? new String[0] : list.trim().split("\\s+");
            int index;
            try {
                index = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                throw new TclException("expected integer but got \"" + args.get(1) + "\"");
            }
            return index >= 0 && index < elements.length ? elements[index] : "";
        });
    }

    private static double parseNumber(String text) throws TclException {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new TclException("expected number but got \"" + text + "\"");
        }
    }

    private static String formatOperand(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15
                ? Long.toString((long) value)
                : Double.toString(value);
    }
}
