package org.flatcam.cam.gerber;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed %FS...*% coordinate format: leading/trailing/no zero suppression
 * and absolute/incremental notation, matching ParseGerber.py's fmt_re.
 */
public final class FormatSpec {

    private static final Pattern FS_PATTERN =
            Pattern.compile("^%FS([LTD])?([AI])X(\\d)(\\d)Y(\\d)(\\d)\\*%?$");

    public final int xIntDigits;
    public final int xDecDigits;
    public final int yIntDigits;
    public final int yDecDigits;
    private final char zeroSuppression;
    private final boolean incremental;

    private FormatSpec(int xIntDigits, int xDecDigits, int yIntDigits, int yDecDigits,
                       char zeroSuppression, boolean incremental) {
        this.xIntDigits = xIntDigits;
        this.xDecDigits = xDecDigits;
        this.yIntDigits = yIntDigits;
        this.yDecDigits = yDecDigits;
        this.zeroSuppression = zeroSuppression;
        this.incremental = incremental;
    }

    public static FormatSpec parse(String line) {
        Matcher m = FS_PATTERN.matcher(line);
        if (!m.matches()) {
            throw new GerberParseException("Unsupported or malformed %FS line: " + line);
        }
        String zeros = m.group(1);
        String notation = m.group(2);
        return new FormatSpec(
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)),
                zeros == null ? 'D' : zeros.charAt(0), notation.equals("I")
        );
    }

    public boolean isIncremental() {
        return incremental;
    }

    public double decodeX(String digits) {
        return decode(digits, xIntDigits, xDecDigits, zeroSuppression);
    }

    public double decodeY(String digits) {
        return decode(digits, yIntDigits, yDecDigits, zeroSuppression);
    }

    private static double decode(String digits, int intDigits, int decDigits, char zeroSuppression) {
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
        int scale = zeroSuppression == 'T' ? s.length() - intDigits : decDigits;
        // Convert the exact decimal once. Multiplying by a binary power-of-ten approximation
        // made identical full-width LZ/TZ coordinates differ by one ULP, changing arc sampling.
        double result = BigDecimal.valueOf(value).scaleByPowerOfTen(-scale).doubleValue();
        return negative ? -result : result;
    }
}
