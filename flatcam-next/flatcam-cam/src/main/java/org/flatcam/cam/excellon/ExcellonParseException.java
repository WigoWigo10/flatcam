package org.flatcam.cam.excellon;

/**
 * Raised for Excellon input outside what this parser supports - notably
 * routing mode (G00/G01/G02/G03 with actual route moves) and incremental
 * positioning (G91). Matches the legacy Python parser's own stated scope:
 * "FlatCAM supports only the drilling subset of Excellon. Routing is not
 * supported." (flatcam.org/fileformats).
 */
public class ExcellonParseException extends RuntimeException {

    public ExcellonParseException(String message) {
        super(message);
    }
}
