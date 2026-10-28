package org.flatcam.fx;

import java.util.Locale;
import java.util.Objects;

/** Readout precision only. Never use this formatter to persist coordinates or create CAM inputs. */
final class UiNumbers {
    record Settings(String initialUnits, int metricDecimals, int inchDecimals) {
        static final Settings DEFAULT = new Settings("MM", 4, 4);
        Settings {
            if (!"MM".equals(initialUnits) && !"IN".equals(initialUnits))
                throw new IllegalArgumentException("Initial units must be MM or IN");
            if (metricDecimals < 2 || metricDecimals > 16 || inchDecimals < 2 || inchDecimals > 16)
                throw new IllegalArgumentException("Display precision must be between 2 and 16");
        }
        int decimals(String units) { return "IN".equalsIgnoreCase(units) ? inchDecimals : metricDecimals; }
        String pattern(String units) { return "%." + decimals(units) + "f"; }
        String format(double value, String units) { return String.format(Locale.ROOT, pattern(units), value); }
    }
    private static volatile Settings active = Settings.DEFAULT;
    private static boolean initialized;
    private UiNumbers() { }
    static boolean validPrecision(int value) { return value >= 2 && value <= 16; }
    static Settings settings() { return active; }
    static synchronized void initialize(Settings saved) {
        if (!initialized) { active = Objects.requireNonNull(saved); initialized = true; }
    }
    static String initialUnits() { return active.initialUnits(); }
    static String fixed(double value, String units) { return active.format(value, units); }
    static String pattern(String units) { return active.pattern(units); }
    static String coordinates(double x, double y, String units) {
        String pattern = active.pattern(units);
        return String.format(Locale.ROOT,"X: " + pattern + "   Y: " + pattern,x,y);
    }
}
