package org.flatcam.cam.excellon;

/**
 * Raised for malformed, ambiguous or unsupported Excellon input.
 * G90/G91 and linear/circular routing are supported within the documented
 * reader subset; unsupported canned cycles and invalid arcs are refused.
 */
public class ExcellonParseException extends RuntimeException {

    public ExcellonParseException(String message) {
        super(message);
    }
}
