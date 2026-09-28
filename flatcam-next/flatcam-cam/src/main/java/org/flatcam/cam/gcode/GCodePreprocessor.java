package org.flatcam.cam.gcode;

import java.util.List;
import java.util.Locale;

/**
 * The milling dialects currently ported from the Python preprocessors.
 *
 * <p>This is deliberately a closed registry: Python preprocessors are executable
 * code, not templates that can safely be loaded into the Java generator. Laser,
 * paste, HPGL and controller-specific profiles must each be ported and tested
 * before they can appear in the FX selector. The legacy names describe the
 * controller commands, not byte-for-byte parity with Python's verbose header.
 */
public enum GCodePreprocessor {
    FX_PORTABLE("FX portable (atual)", false, false, false),
    DEFAULT("default (Mach3, M6)", true, true, false),
    DEFAULT_NO_M6("Default_no_M6 (Mach3)", true, false, false),
    GRBL_11("grbl_11 (M6)", true, true, true),
    GRBL_11_NO_M6("GRBL_11_no_M6", true, false, true);

    private final String label;
    private final boolean pythonStyle;
    private final boolean usesM6;
    private final boolean grbl;

    GCodePreprocessor(String label, boolean pythonStyle, boolean usesM6, boolean grbl) {
        this.label = label;
        this.pythonStyle = pythonStyle;
        this.usesM6 = usesM6;
        this.grbl = grbl;
    }

    public static List<GCodePreprocessor> millingProfiles() {
        return List.of(values());
    }

    public String label() {
        return label;
    }

    public String rapid() {
        return pythonStyle ? "G00" : "G0";
    }

    public String linear() {
        return pythonStyle ? "G01" : "G1";
    }

    public String spindleOn() {
        return pythonStyle ? "M03" : "M3";
    }

    public String spindleOff() {
        return pythonStyle ? "M05" : "M5";
    }

    public String comment(String text) {
        return pythonStyle ? "(" + text + ")" : "; " + text;
    }

    public String header() {
        return comment("Preprocessor: " + name());
    }

    public boolean usesM6() {
        return usesM6;
    }

    public boolean emitsToolNumber() {
        return pythonStyle;
    }

    public boolean usesG17() {
        return grbl;
    }

    public String toolChangeMessage(int toolNumber, double diameter, String units) {
        return comment(String.format(Locale.ROOT,
                "Troque para T%d - diametro %.4f %s - e continue",
                toolNumber, diameter, units));
    }
}
