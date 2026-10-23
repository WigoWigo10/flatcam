package org.flatcam.cam.gcode;

import java.util.List;
import java.util.Locale;
import org.flatcam.cam.CancellationToken;

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
    Z_LASER("Z_laser (laser, foco Z)", true, false, true),
    ISEL_CNC("ISEL_CNC (mm, M06/M01)", true, true, false),
    TOOLCHANGE_MANUAL("Toolchange_Manual (ajuste em Z0)", true, false, true),
    TOOLCHANGE_CUSTOM("Toolchange_Custom (macro M6)", true, true, true),
    LINE_XYZ("line_xyz (XYZ explicitos)", true, true, false),
    ISEL_ICP_CNC("ISEL_ICP_CNC (mm, formato ICP)", false, false, false),
    HPGL("hpgl (plotter, canetas)", false, false, false),
    ROLAND_MDX_20("Roland_MDX_20 (mm, RML-1)", false, false, false),
    TOOLCHANGE_PROBE_MACH3("Toolchange_Probe_MACH3 (sondagem Z)", true, true, true);

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
        return java.util.Arrays.stream(values()).filter(profile -> !profile.isLaser() && !profile.isPlotter()).toList();
    }

    public static List<GCodePreprocessor> geometryProfiles() {
        return List.of(values());
    }

    public boolean isLaser() {
        return this == GRBL_LASER || this == MARLIN_LASER_FAN_PIN
                || this == MARLIN_LASER_SPINDLE_PIN || this == Z_LASER;
    }

    public boolean isPlotter() {
        return this == HPGL;
    }

    public boolean isRoland() {
        return this == ROLAND_MDX_20;
    }

    public boolean requiresProbe() {
        return this == TOOLCHANGE_PROBE_MACH3;
    }

    public boolean supportsManualToolChange() {
        return !isLaser() && !isPlotter() && !automaticToolSelection() && !isRoland();
    }

    /** The MDX-20 motor is binary, unlike profiles with an S/RPM parameter. */
    public boolean controlsSpindle(int rpm) {
        return isRoland() || rpm > 0;
    }

    public boolean usesRapidFeed() {
        return this == MARLIN || this == REPETIER || this == MARLIN_LASER_FAN_PIN
                || this == MARLIN_LASER_SPINDLE_PIN || isRoland();
    }

    public String description() {
        return switch (this) {
            case REPETIER -> "Saida FAN M106/M107 (0-255). Troca usa M84 e @pause: requer Repetier-Host; "
                    + "M84 libera motores e pode exigir novo referenciamento.";
            case MARLIN -> "Fresagem: M3/M5, com S em RPM somente se CUTTER_POWER_UNIT=RPM. "
                    + "Tn/M6 seguem o legado e exigem configuração/macro compatível; M0 exige suporte de pausa. "
                    + "G0 recebe F, mas G0_FEEDRATE fixo no firmware pode ignorá-lo. IN exige INCH_MODE_SUPPORT. "
                    + "M400 aguarda os movimentos, não a parada física do spindle.";
            case BERTA_CNC -> "Inicializacao Berta: G91.1, G64 P0.03, M110, G54; termina com M111/M30.";
            case GRBL_LASER -> "Laser M03/M5 por caminho, sem mergulho Z. Potencia S deve respeitar o controlador.";
            case Z_LASER -> "Laser M03/M5; Focus Z e aplicado no inicio de cada ferramenta, com feixe desligado.";
            case MARLIN_LASER_FAN_PIN -> "Laser FAN: M106 S1-255; M400/M107 antes dos deslocamentos.";
            case MARLIN_LASER_SPINDLE_PIN -> "Laser SPINDLE: M3/M5; M400 antes de desligar e deslocar.";
            case ISEL_CNC -> "ISEL CNC: somente mm, G71, ferramentas T1–T128 e troca M06/M01. "
                    + "No Remote DIN/ISO, M01 requer interação do operador; em outros controladores confirme a semântica da parada. "
                    + "Dwell é convertido de segundos para milissegundos. M30 termina após o estacionamento; macros M06 não são simuladas.";
            case TOOLCHANGE_MANUAL -> "Troca manual com tres M0 e movimento G01 ate Z0 para ajustar a ferramenta. "
                    + "Confira a origem Z e a altura livre; nao e sondagem automatica.";
            case TOOLCHANGE_CUSTOM -> "Troca pela macro M6 configurada na maquina, sem M0 adicional. "
                    + "Ative troca de ferramenta para emitir M6; confira a macro antes de executar.";
            case LINE_XYZ -> "G00/G01 com XYZ explicitos quando os eixos ja foram estabelecidos; troca Tn/M6/M0. "
                    + "A subida inicial e a restauracao de Z apos M6 nao inventam X/Y. "
                    + "Preserva X e Y distintos; a macro M6 nao e simulada. Confira se o controlador aceita esse preambulo vertical.";
            case ISEL_ICP_CNC -> "Formato ICP em mm: FASTABS/MOVEABS em micrometros, VEL em micrometros/s, "
                    + "FASTVEL define os rapidos. Coordenadas, velocidades e espera sao truncadas para inteiros nativos. "
                    + "GETTOOL automatico (macro nao simulada). Termina na altura/XY escolhidos, sem WPCLEAR ou retorno a Z0.";
            case HPGL -> "Plotter: IN/PU/PD/PA/SP, passo 0,025 mm. Sem Z, spindle ou velocidade programada. "
                    + "Uma passagem por caminho; canetas numeradas pela ordem das ferramentas. Limites fora da faixa sao recusados.";
            case ROLAND_MDX_20 -> "RML-1: somente MM e uma ferramenta por arquivo. Motor !MC1/!MC0, sem RPM. "
                    + "Velocidades V entre 0,1 e 15 mm/s; fora da faixa sao recusadas. Sem troca ou dwell. "
                    + "Posicione X/Y na origem e confira a altura livre antes de iniciar.";
            case TOOLCHANGE_PROBE_MACH3 -> "Mach3: troca obrigatoria com M6, duas sondagens G31 e G92 Z do contato. "
                    + "Confirme cada contato nas pausas M0; se a sonda falhar, ABORTE. Requer RPM positivo. "
                    + "G92 altera a referencia Z e permanece ativo. Previa indisponivel: contato real nao conhecido pelo FX.";
            default -> "Port parcial do perfil Python. M6 exige suporte do controlador; simule antes de usar.";
        };
    }

    public void validatePower(int power) {
        if (power < 0) throw new IllegalArgumentException("Potencia/RPM nao pode ser negativa.");
        if (isLaser() && power == 0) {
            throw new IllegalArgumentException("Potencia do laser deve ser maior que zero.");
        }
        if (requiresProbe() && power == 0)
            throw new IllegalArgumentException("Mach3 com sonda exige Spindle RPM positivo; a sondagem desliga o motor.");
        if ((this == REPETIER || this == MARLIN_LASER_FAN_PIN) && power > 255) {
            throw new IllegalArgumentException("Este perfil usa PWM FAN: potencia deve estar entre "
                    + (isLaser() ? "1" : "0") + " e 255, nao RPM.");
        }
    }

    public void validateFeedRates(double feedRate, double rapidFeedRate) {
        if (isRoland()) {
            RolandProgramCodec.velocity(feedRate);
            RolandProgramCodec.velocity(rapidFeedRate == 0 ? 900 : rapidFeedRate);
        }
    }

    /** Additional modal initialization, without changing the legacy portable output. */
    public String initialization() {
        return switch (this) {
            case BERTA_CNC -> "G91.1\nG64 P0.03\nM110\nG54\nG0\n(Berta)\nM05\n";
            case MARLIN -> "M400\nM5\n";
            case REPETIER -> "M107\n";
            case TOOLCHANGE_PROBE_MACH3 -> "M05\n(MSG, Confira origens e Z aproximado; teste a sonda e o E-stop; confirme G92/G52)\nM0\n";
            case ISEL_CNC, TOOLCHANGE_MANUAL, TOOLCHANGE_CUSTOM, LINE_XYZ, ISEL_ICP_CNC, ROLAND_MDX_20 -> "M05\n";
            default -> "";
        };
    }

    public String ending() {
        return switch (this) {
            case BERTA_CNC -> "(Berta)\nM111\nM30\n(Berta)\n";
            case MARLIN -> "M400\n"; // Include final parking moves in the completion barrier.
            case ISEL_CNC -> "M30\n";
            case REPETIER, GRBL_LASER, Z_LASER,
                    MARLIN_LASER_FAN_PIN, MARLIN_LASER_SPINDLE_PIN,
                    TOOLCHANGE_MANUAL, TOOLCHANGE_CUSTOM, LINE_XYZ, ISEL_ICP_CNC, ROLAND_MDX_20,
                    TOOLCHANGE_PROBE_MACH3 -> "";
            default -> "M30\n";
        };
    }

    public String selectTool(int number) {
        validateToolNumber(number);
        // Tn is an extruder selection on Repetier, not the milling tool identifier.
        return this == REPETIER ? comment("Ferramenta T" + number) : "T" + number;
    }

    public String pauseForTool(int number, double diameter, String units) {
        validateToolNumber(number);
        if (requiresProbe()) throw new IllegalArgumentException("Mach3 com sonda exige parametros explicitos de probing.");
        if (this == TOOLCHANGE_MANUAL) throw new IllegalArgumentException(
                "Troca manual exige altura de retorno e feed para o movimento ate Z0.");
        String message = toolChangeMessage(number, diameter, units);
        if (this == ISEL_ICP_CNC) return ""; // GETTOOL itself selects the tool, without an M0/M6 sequence.
        if (this == TOOLCHANGE_CUSTOM) return "M6\n" + comment("Troca controlada pela macro M6 da maquina");
        if (this == ISEL_CNC) return "M05\nM06\n" + message + "\nM01";
        return this == REPETIER
                ? "M400\nM84\n@pause Troque para T" + number + " - diametro "
                    + GCodeGenerator.fmt(diameter) + " " + units
                : (usesM6 ? "M6\n" : "") + message + "\nM0";
    }

    public String pauseForTool(int number, double diameter, String units, double returnZ, double feedRate) {
        if (this == ISEL_ICP_CNC) {
            if (!Double.isFinite(returnZ) || returnZ <= 0)
                throw new IllegalArgumentException("Altura de retorno ICP deve ser positiva.");
            return "G0 Z" + GCodeGenerator.fmt(returnZ);
        }
        if (this == TOOLCHANGE_CUSTOM || this == ISEL_CNC) {
            if (!Double.isFinite(returnZ) || returnZ <= 0)
                throw new IllegalArgumentException("Altura de retorno apos troca deve ser positiva.");
            // An M6 macro can move the axes or change the distance mode: retract before the next XY.
            return pauseForTool(number, diameter, units) + "\nG90\nG00 Z" + GCodeGenerator.fmt(returnZ);
        }
        if (this != TOOLCHANGE_MANUAL) return pauseForTool(number, diameter, units);
        if (!Double.isFinite(returnZ) || returnZ <= 0 || !Double.isFinite(feedRate) || feedRate <= 0)
            throw new IllegalArgumentException("Altura de retorno e feed da troca manual devem ser positivos.");
        return "M05\n" + toolChangeMessage(number, diameter, units) + "\nM0\nG01 Z0 F"
                + GCodeGenerator.fmt(feedRate) + "\n"
                + comment("Ajuste T" + number + " para tocar o material e aperte levemente") + "\nM0\nG00 Z"
                + GCodeGenerator.fmt(returnZ) + "\n"
                + comment("Aperte a ferramenta com seguranca antes de continuar") + "\nM0";
    }

    public String unitsCode(String units) {
        if (requiresProbe() && !"MM".equalsIgnoreCase(units) && !"IN".equalsIgnoreCase(units))
            throw new IllegalArgumentException("Mach3 com sonda exige unidades MM ou IN.");
        if (this == ISEL_CNC || this == ISEL_ICP_CNC || isRoland()) {
            if (!"MM".equalsIgnoreCase(units)) throw new IllegalArgumentException(
                    name() + " suporta somente MM neste port; converta o objeto para mm antes de gerar.");
            return this == ISEL_CNC ? "G71" : "G21";
        }
        return "MM".equalsIgnoreCase(units) ? "G21" : "G20";
    }

    public boolean automaticToolSelection() {
        return this == ISEL_ICP_CNC || this == HPGL;
    }

    public String fileExtension() {
        return isRoland() ? ".rml" : this == HPGL ? ".plt" : this == ISEL_ICP_CNC ? ".imf" : ".nc";
    }

    public List<String> filePatterns() {
        return isRoland() ? List.of("*.rml", "*.prn") : this == HPGL ? List.of("*.plt", "*.hpgl", "*.hpg")
                : this == ISEL_ICP_CNC ? List.of("*.imf") : List.of("*.nc", "*.gcode", "*.tap");
    }

    /** Explicit G0 feed for Marlin/Repetier. Zero selects a conservative default in source units. */
    public String finish(StringBuilder program, double rapidFeedRate, String units) {
        return finish(program, rapidFeedRate, units, CancellationToken.none());
    }

    public String finish(StringBuilder program, double rapidFeedRate, String units, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        program.append(ending());
        if (this == LINE_XYZ) return ControllerProgramCodec.explicitXyz(program.toString(), cancellation);
        if (this == ISEL_ICP_CNC) {
            unitsCode(units);
            if (!Double.isFinite(rapidFeedRate) || rapidFeedRate < 0)
                throw new IllegalArgumentException("Feed rapido ICP invalido.");
            return ControllerProgramCodec.encodeIcp(program.toString(), rapidFeedRate > 0 ? rapidFeedRate : 1500, cancellation);
        }
        if (isRoland()) return RolandProgramCodec.encode(program.toString(), rapidFeedRate, cancellation);
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
        return !isLaser() && !isRoland() && this != FX_PORTABLE;
    }

    public boolean usesG17() {
        return grbl;
    }

    /** Marlin uses native units/min; its command dispatcher has no G94 handler. */
    public boolean usesG94() { return this != MARLIN; }

    private void validateToolNumber(int number) {
        if (this == ISEL_CNC && (number < 1 || number > 128))
            throw new IllegalArgumentException("ISEL CNC exige ferramentas numeradas de T1 a T128.");
    }

    /** Public UI time is seconds; Remote DIN/ISO G4 uses milliseconds. */
    public String dwellCode(double seconds) {
        double controllerTime = this == ISEL_CNC ? seconds * 1000 : seconds;
        if (!Double.isFinite(controllerTime) || controllerTime < 0)
            throw new IllegalArgumentException("Dwell fora do intervalo representável pelo perfil.");
        return "G4 P" + GCodeGenerator.fmt(controllerTime);
    }

    public String toolChangeMessage(int toolNumber, double diameter, String units) {
        return comment(String.format(Locale.ROOT,
                "Troque para T%d - diametro %.4f %s - e continue",
                toolNumber, diameter, units));
    }
}
