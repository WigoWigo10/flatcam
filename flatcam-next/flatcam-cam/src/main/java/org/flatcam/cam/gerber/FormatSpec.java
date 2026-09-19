package org.flatcam.cam.gerber;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed %FS...*% coordinate format. Only leading-zero omission ('L') and
 * absolute notation ('A') are supported - the only combination used by the
 * Fase 0 fixture corpus (tests/gerber_files/) - trailing-zero omission ('T')
 * and incremental notation ('I') raise {@link GerberParseException} rather
 * than silently mis-parsing coordinates.
 */
public final class FormatSpec {

    private static final Pattern FS_PATTERN =
            Pattern.compile("^%FS([LTD])?([AI])X(\\d)(\\d)Y(\\d)(\\d)\\*%?$");

    public final int xIntDigits;
    public final int xDecDigits;
    public final int yIntDigits;
    public final int yDecDigits;

    private FormatSpec(int xIntDigits, int xDecDigits, int yIntDigits, int yDecDigits) {
        this.xIntDigits = xIntDigits;
        this.xDecDigits = xDecDigits;
        this.yIntDigits = yIntDigits;
        this.yDecDigits = yDecDigits;
    }

    public static FormatSpec parse(String line) {
        Matcher m = FS_PATTERN.matcher(line);
        if (!m.matches()) {
            throw new GerberParseException("Unsupported or malformed %FS line: " + line);
        }
        String zeros = m.group(1);
        String notation = m.group(2);
        if (zeros != null && !zeros.equals("L")) {
            throw new GerberParseException("Only leading-zero omission (FS L..) is supported, got: " + line);
        }
        if (!notation.equals("A")) {
            throw new GerberParseException("Only absolute notation (FS .A) is supported, got: " + line);
        }
        return new FormatSpec(
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6))
        );
    }

    /** Decodes a leading-zero-omitted, fixed-decimal digit string (e.g. "2940" with 3 decimals -&gt; 2.940). */
    public double decodeX(String digits) {
        return decode(digits, xDecDigits);
    }

    public double decodeY(String digits) {
        return decode(digits, yDecDigits);
    }

    private static double decode(String digits, int decDigits) {
        boolean negative = false;
        String s = digits;
        if (s.startsWith("+")) {
            s = s.substring(1);
        } else if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return 0.0;
        }
        long value = Long.parseLong(s);
        double result = value / Math.pow(10, decDigits);
        return negative ? -result : result;
    }
}
