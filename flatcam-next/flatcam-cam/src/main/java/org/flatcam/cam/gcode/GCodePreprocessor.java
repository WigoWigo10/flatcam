package org.flatcam.cam.gcode;

import java.util.List;
import java.util.Locale;

/**
 * Controller dialects explicitly ported from the Python preprocessors.
 *
 * <p>This is deliberately a closed registry: Python preprocessors are executable
 * code, not templates that can safely be loaded into the Java generator. Paste,
 * HPGL and other controller-specific profiles must each be ported and tested
 * before they can appear in the FX selector. The legacy names describe the
 * controller commands, not byte-for-byte parity with Python's verbose header.
 */
public enum GCodePreprocessor {
    FX_PORTABLE("FX portable (atual)", false, false, false),
    DEFAULT("default (Mach3, M6)", true, true, false),
    DEFAULT_NO_M6("Default_no_M6 (Mach3)", true, false, false),
    GRBL_11("grbl_11 (M6)", true, true, true),
    GRBL_11_NO_M6("GRBL_11_no_M6", true, false, true),
    MARLIN("Marlin (fresagem, M6)", false, true, false),
    REPETIER("Repetier (FAN, Repetier-Host)", false, false, false),
    BERTA_CNC("Berta_CNC", true, true, true),
    GRBL_LASER("GRBL_laser (laser)", true, false, true),
    MARLIN_LASER_FAN_PIN("Marlin_laser_FAN_pin (laser, 0-255)", false, false, false),
    MARLIN_LASER_SPINDLE_PIN("Marlin_laser_Spindle_pin (laser)", false, false, false),
    Z_LASER("Z_laser (laser, foco Z)", true, false, true);

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
        return java.util.Arrays.stream(values()).filter(profile -> !profile.isLaser()).toList();
    }

    public static List<GCodePreprocessor> geometryProfiles() {
        return List.of(values());
    }

    public boolean isLaser() {
        return this == GRBL_LASER || this == MARLIN_LASER_FAN_PIN
                || this == MARLIN_LASER_SPINDLE_PIN || this == Z_LASER;
    }

    public boolean usesRapidFeed() {
        return this == MARLIN || this == REPETIER || this == MARLIN_LASER_FAN_PIN
                || this == MARLIN_LASER_SPINDLE_PIN;
    }

    public String description() {
        return switch (this) {
            case REPETIER -> "Saida FAN M106/M107 (0-255). Troca usa M84 e @pause: requer Repetier-Host; "
                    + "M84 libera motores e pode exigir novo referenciamento.";
            case MARLIN -> "Fresagem: M3/M5 e M400 antes de parar. M6 segue o perfil Python; "
                    + "confira suporte no firmware. Feed rapids limita os movimentos G0.";
            case BERTA_CNC -> "Inicializacao Berta: G91.1, G64 P0.03, M110, G54; termina com M111/M30.";
            case GRBL_LASER -> "Laser M03/M5 por caminho, sem mergulho Z. Potencia S deve respeitar o controlador.";
            case Z_LASER -> "Laser M03/M5; Focus Z e aplicado no inicio de cada ferramenta, com feixe desligado.";
            case MARLIN_LASER_FAN_PIN -> "Laser FAN: M106 S1-255; M400/M107 antes dos deslocamentos.";
            case MARLIN_LASER_SPINDLE_PIN -> "Laser SPINDLE: M3/M5; M400 antes de desligar e deslocar.";
            default -> "Port parcial do perfil Python. M6 exige suporte do controlador; simule antes de usar.";
        };
    }

    public void validatePower(int power) {
        if (power < 0) throw new IllegalArgumentException("Potencia/RPM nao pode ser negativa.");
        if (isLaser() && power == 0) {
            throw new IllegalArgumentException("Potencia do laser deve ser maior que zero.");
        }
        if ((this == REPETIER || this == MARLIN_LASER_FAN_PIN) && power > 255) {
            throw new IllegalArgumentException("Este perfil usa PWM FAN: potencia deve estar entre "
                    + (isLaser() ? "1" : "0") + " e 255, nao RPM.");
        }
    }

    /** Additional modal initialization, without changing the legacy portable output. */
    public String initialization() {
        return switch (this) {
            case BERTA_CNC -> "G91.1\nG64 P0.03\nM110\nG54\nG0\n(Berta)\nM05\n";
            case MARLIN -> "M400\nM5\n";
            case REPETIER -> "M107\n";
            default -> "";
        };
    }

    public String ending() {
        return switch (this) {
            case BERTA_CNC -> "(Berta)\nM111\nM30\n(Berta)\n";
            case MARLIN, REPETIER, GRBL_LASER, Z_LASER,
                    MARLIN_LASER_FAN_PIN, MARLIN_LASER_SPINDLE_PIN -> "";
            default -> "M30\n";
        };
    }

    public String selectTool(int number) {
        // Tn is an extruder selection on Repetier, not the milling tool identifier.
        return this == REPETIER ? comment("Ferramenta T" + number) : "T" + number;
    }

    public String pauseForTool(int number, double diameter, String units) {
        String message = toolChangeMessage(number, diameter, units);
        return this == REPETIER
                ? "M400\nM84\n@pause Troque para T" + number + " - diametro "
                    + GCodeGenerator.fmt(diameter) + " " + units
                : (usesM6 ? "M6\n" : "") + message + "\nM0";
    }

    /** Explicit G0 feed for Marlin/Repetier. Zero selects a conservative default in source units. */
    public String finish(StringBuilder program, double rapidFeedRate, String units) {
        program.append(ending());
        if (!usesRapidFeed()) return program.toString();
        double rate = rapidFeedRate > 0 ? rapidFeedRate : ("MM".equalsIgnoreCase(units) ? 1500 : 1500 / 25.4);
        String suffix = " F" + GCodeGenerator.fmt(rate);
        StringBuilder result = new StringBuilder(program.length());
        int start = 0;
        while (start < program.length()) {
            int end = program.indexOf("\n", start);
            if (end < 0) end = program.length();
            String line = program.substring(start, end);
            result.append(line);
            if ((line.startsWith("G0 ") || line.startsWith("G00 "))
                    && (line.contains(" X") || line.contains(" Y") || line.contains(" Z"))) result.append(suffix);
            result.append('\n');
            start = end + 1;
        }
        return result.toString();
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
        if (this == REPETIER || this == MARLIN_LASER_FAN_PIN) return "M106";
        return pythonStyle ? "M03" : "M3";
    }

    public String spindleOff() {
        if (this == REPETIER) return "M107";
        if (this == MARLIN_LASER_FAN_PIN) return "M400\nM107";
        if (this == MARLIN || this == MARLIN_LASER_SPINDLE_PIN) return "M400\nM5";
        if (isLaser()) return "M5";
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
        return !isLaser() && this != FX_PORTABLE;
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
