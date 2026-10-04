package org.flatcam.cam.tcl;

/**
 * Mirrors {@code tkinter.TclError} / Python FlatCAM's {@code TclErrorException}: the single
 * checked error type raised by a failed command, a syntax error, or an undefined variable
 * reference. The interpreter never lets a native command's own {@link RuntimeException} escape
 * uncaught - see {@link TclInterpreter}'s command dispatch - so callers only ever need to catch
 * this one type, same as the Python shell only ever needs to catch {@code tk.TclError}.
 */
public final class TclException extends Exception {
    public TclException(String message) {
        super(message);
    }

    public TclException(String message, Throwable cause) {
        super(message, cause);
    }
}
