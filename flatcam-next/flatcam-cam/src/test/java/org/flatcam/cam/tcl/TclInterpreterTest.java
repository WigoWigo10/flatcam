package org.flatcam.cam.tcl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TclInterpreterTest {

    @Test
    void cancellationStopsLoopsAndRestoresTheDefaultToken() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        interpreter.register("cancel_now", (interp, args) -> { cancelled.set(true); return ""; });
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> interpreter.eval("set x 1; while {1} {cancel_now; incr x}", cancelled::get));
        assertEquals("1", interpreter.eval("set x"));
        assertEquals("2", interpreter.eval("incr x"));
    }

    @Test
    void recursiveSubstitutionInheritsCancellation() {
        TclInterpreter interpreter = new TclInterpreter();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        interpreter.register("cancel_now", (interp, args) -> { cancelled.set(true); return ""; });
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> interpreter.eval("set x [cancel_now; set y 1]", cancelled::get));
        assertThrows(TclException.class, () -> interpreter.eval("set y"));
    }

    @Test
    void parsingLargeBracedWordsIsCancellable() {
        TclInterpreter interpreter = new TclInterpreter();
        String script = "puts {" + "x".repeat(100_000) + "}";
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> interpreter.eval(script, () -> checks.incrementAndGet() > script.length() + 20));
    }

    @Test
    void setStoresAndReadsAVariable() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("5", interpreter.eval("set x 5"));
        assertEquals("5", interpreter.eval("set x"));
    }

    @Test
    void readingAnUndefinedVariableIsAnError() {
        TclInterpreter interpreter = new TclInterpreter();
        TclException error = assertThrows(TclException.class, () -> interpreter.eval("set y"));
        assertTrue(error.getMessage().contains("y"));
    }

    @Test
    void dollarSubstitutesABareOrBracedVariableReference() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set PATH /tmp");
        assertEquals("/tmp/file.gbr", interpreter.eval("set out $PATH/file.gbr"));
        assertEquals("/tmp/file.gbr", interpreter.eval("set out2 ${PATH}/file.gbr"));
    }

    @Test
    void bracketSubstitutesARecursivelyEvaluatedCommand() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set a 2");
        interpreter.eval("set b 3");
        assertEquals("5", interpreter.eval("expr $a + $b"));
        assertEquals("5", interpreter.eval("set c [expr $a + $b]"));
    }

    @Test
    void quotedWordsKeepEmbeddedSpaces() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("hello world", interpreter.eval("set phrase \"hello world\""));
    }

    @Test
    void bracedWordsAreNeverSubstituted() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set x 5");
        assertEquals("literal $x and [cmd]", interpreter.eval("set y {literal $x and [cmd]}"));
    }

    @Test
    void backslashEscapesAreInterpretedInQuotedAndBareWords() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("a\"b", interpreter.eval("set x \"a\\\"b\""));
        assertEquals("a\tb", interpreter.eval("set y \"a\\tb\""));
        assertEquals("$literal", interpreter.eval("set z \\$literal"));
    }

    @Test
    void semicolonAndNewlineBothSeparateCommandsReturningTheLastResult() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("2", interpreter.eval("set a 1; set a 2"));
        assertEquals("4", interpreter.eval("set b 3\nset b 4"));
    }

    @Test
    void commentLinesAreIgnored() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("1", interpreter.eval("# a comment\nset a 1\n# another comment"));
    }

    @Test
    void backslashNewlineJoinsTwoLinesIntoOneCommand() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("3", interpreter.eval("expr 1 + \\\n2"));
    }

    @Test
    void ifRunsTheTrueBranchOnly() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set x 5");
        assertEquals("big", interpreter.eval("if {$x > 1} {set result big} else {set result small}; set result"));
        interpreter.eval("set x 0");
        assertEquals("small", interpreter.eval("if {$x > 1} {set result big} else {set result small}; set result"));
    }

    @Test
    void ifSupportsElseif() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set x 2");
        String script = "if {$x == 1} {set r one} elseif {$x == 2} {set r two} else {set r other}; set r";
        assertEquals("two", interpreter.eval(script));
    }

    @Test
    void ifConditionIsReEvaluatedEachTimeAroundALoopNotFrozenAtParseTime() throws TclException {
        // Regression for the deferred-evaluation rule the class doc describes: a braced body must
        // see the loop variable's current value on every iteration, not whatever it was when the
        // foreach command itself was first dispatched.
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set total 0");
        interpreter.eval("foreach n {1 2 3} {incr total $n}");
        assertEquals("6", interpreter.eval("set total"));
    }

    @Test
    void whileLoopsUntilTheConditionGoesFalse() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        interpreter.eval("set i 0");
        interpreter.eval("while {$i < 5} {incr i}");
        assertEquals("5", interpreter.eval("set i"));
    }

    @Test
    void whileGuardsAgainstAnInfiniteLoop() {
        TclInterpreter interpreter = new TclInterpreter();
        assertThrows(TclException.class, () -> interpreter.eval("while {1} {set x 1}"));
    }

    @Test
    void exprHandlesArithmeticComparisonAndLogic() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("7", interpreter.eval("expr 1 + 2 * 3"));
        assertEquals("1", interpreter.eval("expr (1 + 2) * 3 == 9"));
        assertEquals("1", interpreter.eval("expr 1 && 1"));
        assertEquals("0", interpreter.eval("expr 1 && 0"));
        assertEquals("1", interpreter.eval("expr !0"));
        assertEquals("1", interpreter.eval("expr \"MM\" == \"MM\""));
        assertEquals("0", interpreter.eval("expr \"MM\" == \"IN\""));
    }

    @Test
    void customCommandsReceiveAlreadySubstitutedArguments() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        List<String> captured = new ArrayList<>();
        interpreter.register("echo", (interp, args) -> {
            captured.addAll(args);
            return String.join(",", args);
        });
        interpreter.eval("set name gerber_1");
        assertEquals("gerber_1,-dia,1.2", interpreter.eval("echo $name -dia 1.2"));
        assertEquals(List.of("gerber_1", "-dia", "1.2"), captured);
    }

    @Test
    void unknownCommandNameIsAnError() {
        TclInterpreter interpreter = new TclInterpreter();
        TclException error = assertThrows(TclException.class, () -> interpreter.eval("this_does_not_exist 1 2"));
        assertTrue(error.getMessage().contains("this_does_not_exist"));
    }

    @Test
    void putsWithOneArgumentReturnsTextInsteadOfPrinting() throws TclException {
        // Matches Python FlatCAM's own shell override, so each line a user types shows its text
        // as the Terminal's displayed result rather than requiring a real stdout channel.
        TclInterpreter interpreter = new TclInterpreter();
        List<String> printed = new ArrayList<>();
        interpreter.setOutputSink(printed::add);
        assertEquals("hello", interpreter.eval("puts hello"));
        assertTrue(printed.isEmpty());
    }

    @Test
    void putsWithAChannelArgumentPrintsThroughTheOutputSink() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        List<String> printed = new ArrayList<>();
        interpreter.setOutputSink(printed::add);
        interpreter.eval("puts stdout hello");
        assertEquals(List.of("hello\n"), printed);
    }

    @Test
    void listCommandsOperateOnWhitespaceSeparatedElements() throws TclException {
        TclInterpreter interpreter = new TclInterpreter();
        assertEquals("a b c", interpreter.eval("list a b c"));
        assertEquals("3", interpreter.eval("llength {a b c}"));
        assertEquals("b", interpreter.eval("lindex {a b c} 1"));
        assertEquals("", interpreter.eval("lindex {a b c} 9"));
    }

    @Test
    void realExampleScriptShapeRunsEndToEnd() throws TclException {
        // Shaped like assets/examples/open_file.FlatScript, with "open_gerber"/"set_path"
        // stubbed as custom commands the way the FX command layer will register them.
        TclInterpreter interpreter = new TclInterpreter();
        List<String> opened = new ArrayList<>();
        interpreter.register("get_sys", (interp, args) -> "/opt/flatcam");
        interpreter.register("set_path", (interp, args) -> "");
        interpreter.register("open_gerber", (interp, args) -> {
            opened.add(String.join(" ", args));
            return "gerber_obj";
        });
        String script = """
                set ROOT_FOLDER [get_sys root_folder_path]
                set PATH ${ROOT_FOLDER}/assets/examples/files
                set_path $PATH
                open_gerber test.gbr -outname gerber_obj
                """;
        interpreter.eval(script);
        assertEquals(List.of("test.gbr -outname gerber_obj"), opened);
        assertEquals("/opt/flatcam/assets/examples/files", interpreter.eval("set PATH"));
    }

    @Test
    void missingClosingBraceIsAnError() {
        TclInterpreter interpreter = new TclInterpreter();
        assertThrows(TclException.class, () -> interpreter.eval("set x {unterminated"));
    }

    @Test
    void missingClosingQuoteIsAnError() {
        TclInterpreter interpreter = new TclInterpreter();
        assertThrows(TclException.class, () -> interpreter.eval("set x \"unterminated"));
    }
}
