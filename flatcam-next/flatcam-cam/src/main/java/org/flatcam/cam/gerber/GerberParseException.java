package org.flatcam.cam.gerber;

/**
 * Raised for Gerber input outside what this parser supports. This parser is
 * deliberately scoped to the RS-274X subset exercised by the Fase 0 baseline
 * corpus (tests/gerber_files/) - see GerberParser's class doc - rather than
 * the full spec, so unsupported constructs fail loudly instead of silently
 * producing wrong geometry.
 */
public class GerberParseException extends RuntimeException {

    public GerberParseException(String message) {
        super(message);
    }

    public GerberParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
