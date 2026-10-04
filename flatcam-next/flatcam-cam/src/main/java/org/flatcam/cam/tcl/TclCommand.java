package org.flatcam.cam.tcl;

import java.util.List;

/**
 * A native command registered with a {@link TclInterpreter}, matching Python FlatCAM's
 * {@code tcl.createcommand(name, pythonCallable)}: a plain function from argument words to a
 * result string. Everything in this dialect is a string - same as real Tcl - so a command that
 * wants a number parses its own argument and raises {@link TclException} on a bad value.
 */
@FunctionalInterface
public interface TclCommand {
    /**
     * @param interpreter the calling interpreter - commands that need to read/write variables,
     *                     recurse into {@link TclInterpreter#eval}, or print through
     *                     {@link TclInterpreter#out()} get it here rather than through a captured
     *                     field, so one command instance can safely be shared/reused.
     * @param args         the command's own words, already substituted (braced words arrive
     *                     unsubstituted - see {@link TclInterpreter}'s class doc)
     * @return              the command's result, becoming the enclosing expression's substituted
     *                      value or the script's final result; never {@code null} - use "" for no
     *                      result (Tcl's own convention)
     */
    String execute(TclInterpreter interpreter, List<String> args) throws TclException;
}
