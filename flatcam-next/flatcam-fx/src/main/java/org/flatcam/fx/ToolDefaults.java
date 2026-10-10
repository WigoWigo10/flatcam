package org.flatcam.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The values the tool panels open with (Python keeps them in {@code defaults.py} as {@code tools_*}), editable in
 * Preferences and kept between sessions. A length has one value for millimetres and one for inches, so switching the
 * project's units never converts (and rounds) what the user typed.
 *
 * <p>The values live in a {@link Store}. It is in memory until the application installs the persistent one, so tests
 * and harnesses that build panels directly always see the factory values.
 */
final class ToolDefaults {

    enum Kind { NUMBER, INTEGER, FLAG, CHOICE }

    /** {@code inches} is null for a value that does not depend on the units (a percentage, a count, a switch). */
    record Setting(String key, String tool, String label, Kind kind, String millimetres, String inches,
                   List<String> choices) {
        boolean perUnit() {
            return inches != null;
        }

        String factory(boolean metric) {
            return metric || inches == null ? millimetres : inches;
        }
    }

    interface Store {
        /** The saved text of {@code key}, or null. */
        String get(String key);

        void put(String key, String value);

        void remove(String key);
    }

    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<>();
    private static Store store = memoryStore();
    private static java.util.function.BooleanSupplier displayMetric = () -> true;

    private ToolDefaults() {
    }

    static {
        String cutout = "Cutout";
        length("cutout.tooldia", cutout, "Diâmetro da ferramenta", "2.4", "0.094");
        length("cutout.margin", cutout, "Margem", "0.1", "0.004");
        length("cutout.cutz", cutout, "Cut Z", "-1.7", "-0.067");
        flag("cutout.multidepth", cutout, "Multi-Depth", true);
        length("cutout.depthperpass", cutout, "Profundidade por passe", "0.5", "0.02");
        length("cutout.thinz", cutout, "Thin Depth (pontes)", "-0.5", "-0.02");
        length("cutout.gapsize", cutout, "Tamanho do gap", "4", "0.16");
        choice("cutout.gaptype", cutout, "Tipo de gap", "BRIDGE", "BRIDGE", "THIN", "M_BITES");
        choice("cutout.gaps", cutout, "Posição dos gaps", "FOUR", "NONE", "LR", "TB", "FOUR", "TWO_LR", "TWO_TB",
                "EIGHT");
        length("cutout.bitediameter", cutout, "Diâmetro do mouse bite", "0.8", "0.031");
        length("cutout.bitespacing", cutout, "Espaço entre mouse bites", "0.4", "0.016");
        flag("cutout.convex", cutout, "Convex Shape", false);

        String iso = "Isolation";
        length("iso.tooldia", iso, "Diâmetro da ferramenta", "0.1", "0.004");
        choice("iso.tooltype", iso, "Tipo de fresa", "C1", "C1", "C2", "C3", "C4", "B", "V");
        integer("iso.passes", iso, "Passes", 1);
        number("iso.overlap", iso, "Sobreposição (%)", "10");
        choice("iso.type", iso, "Tipo de isolação", "BOTH", "BOTH", "EXTERIOR", "INTERIOR");
        choice("iso.order", iso, "Ordem das ferramentas", "REVERSE", "NONE", "FORWARD", "REVERSE");
        flag("iso.combine", iso, "Combine", true);
        flag("iso.checkvalidity", iso, "Check validity", false);
        flag("iso.rest", iso, "Rest Machining", false);
        flag("iso.forcedrest", iso, "Forced Rest", true);
        flag("iso.follow", iso, "Follow", false);

        String ncc = "NCC";
        text("ncc.tooldia", ncc, "Diâmetros das ferramentas", "1.0, 0.5", "0.040, 0.020");
        length("ncc.newdia", ncc, "Diâmetro de nova ferramenta", "0.1", "0.004");
        choice("ncc.tooltype", ncc, "Tipo de fresa", "C1", "C1", "C2", "C3", "C4", "B", "V");
        number("ncc.overlap", ncc, "Sobreposição (%)", "40");
        length("ncc.margin", ncc, "Margem", "1.0", "0.040");
        choice("ncc.method", ncc, "Método", "SEED", "STANDARD", "SEED", "LINES", "COMBO");
        choice("ncc.seedpolicy", ncc, "Ponto inicial do Seed", "STABLE", "STABLE", "PYTHON");
        flag("ncc.connect", ncc, "Connect", true);
        flag("ncc.contour", ncc, "Contour", true);
        choice("ncc.milling", ncc, "Tipo de fresamento", "CLIMB", "CLIMB", "CONVENTIONAL");
        choice("ncc.order", ncc, "Ordem das ferramentas", "REVERSE", "NONE", "FORWARD", "REVERSE");
        flag("ncc.rest", ncc, "Rest Machining", false);

        String paint = "Paint";
        text("paint.tooldia", paint, "Diâmetros das ferramentas", "0.3", "0.3");
        number("paint.overlap", paint, "Sobreposição (%)", "20");
        length("paint.margin", paint, "Margem", "0.0", "0.0");
        choice("paint.method", paint, "Método", "STANDARD", "STANDARD", "SEED", "LINES", "COMBO");
        flag("paint.connect", paint, "Conectar caminhos", true);
        flag("paint.contour", paint, "Contorno", true);
        choice("paint.order", paint, "Ordem das ferramentas", "REVERSE", "NONE", "FORWARD", "REVERSE");

        String film = "Film";
        flag("film.negative", film, "Filme negativo", true);
        millimetres("film.boundary", film, "Borda (negativo)", "1.0");
        number("film.scalestroke", film, "Escala do traço", "0");
        millimetres("film.padsize", film, "Tamanho do furo nos pads", "1.0");
        number("film.scalex", film, "Escala X", "1.0");
        number("film.scaley", film, "Escala Y", "1.0");
        number("film.skewx", film, "Inclinação X (graus)", "0.0");
        number("film.skewy", film, "Inclinação Y (graus)", "0.0");
        integer("film.dpi", film, "DPI (PNG)", 96);

        String panelize = "Panelize";
        integer("panelize.columns", panelize, "Colunas", 2);
        integer("panelize.rows", panelize, "Linhas", 2);
        millimetres("panelize.spacingcolumns", panelize, "Espaço entre colunas", "0.0");
        millimetres("panelize.spacingrows", panelize, "Espaço entre linhas", "0.0");
        millimetres("panelize.limitwidth", panelize, "Largura máxima", "200.0");
        millimetres("panelize.limitheight", panelize, "Altura máxima", "290.0");

        millimetres("twosided.drilldia", "2-Sided", "Diâmetro do furo de alinhamento", "3.125");

        String thieving = "Copper Thieving";
        millimetres("thieving.clearance", thieving, "Distância", "0.25");
        millimetres("thieving.margin", thieving, "Margem", "1.0");
        number("thieving.minarea", thieving, "Área mínima", "0.1");
        millimetres("thieving.dotdiameter", thieving, "Diâmetro dos pontos", "1.0");
        millimetres("thieving.dotspacing", thieving, "Espaço dos pontos", "2.0");
        millimetres("thieving.squaresize", thieving, "Lado dos quadrados", "1.0");
        millimetres("thieving.squarespacing", thieving, "Espaço dos quadrados", "2.0");
        millimetres("thieving.linesize", thieving, "Espessura das linhas", "0.25");
        millimetres("thieving.linespacing", thieving, "Espaço das linhas", "2.0");
        millimetres("thieving.robbermargin", thieving, "Margem da robber bar", "1.0");
        millimetres("thieving.robberthickness", thieving, "Espessura da robber bar", "1.0");
        millimetres("thieving.maskclearance", thieving, "Distância da máscara", "0.0");

        String rules = "Rules Check";
        String[][] ruleDefaults = {{"trace_size", "Tamanho da trilha", "0.25"},
                {"copper_to_copper", "Cobre a cobre", "0.25"}, {"copper_to_outline", "Cobre ao contorno", "1.0"},
                {"silk_to_silk", "Seda a seda", "0.25"}, {"silk_to_mask", "Seda à máscara", "0.25"},
                {"silk_to_outline", "Seda ao contorno", "1.0"}, {"mask_sliver", "Lasca da máscara", "0.25"},
                {"annular_ring", "Anel anular", "0.3"}, {"hole_to_hole", "Furo a furo", "0.3"},
                {"hole_size", "Tamanho do furo", "0.3"}};
        for (String[] rule : ruleDefaults) {
            flag("rules." + rule[0] + ".enabled", rules, rule[1] + ": verificar", true);
            millimetres("rules." + rule[0], rules, rule[1], rule[2]);
        }

        String fiducials = "Fiducials";
        millimetres("fiducials.size", fiducials, "Tamanho", "1.0");
        millimetres("fiducials.margin", fiducials, "Margem", "1.0");
        millimetres("fiducials.thickness", fiducials, "Espessura da linha", "0.25");

        String corners = "Corner Markers";
        millimetres("corners.thickness", corners, "Espessura", "0.1");
        millimetres("corners.length", corners, "Comprimento", "3.0");
        millimetres("corners.margin", corners, "Margem", "0.0");
        millimetres("corners.drill", corners, "Diâmetro do furo", "0.5");

        String qrcode = "QRCode";
        integer("qrcode.version", qrcode, "Versão", 1);
        integer("qrcode.boxsize", qrcode, "Tamanho do módulo", 3);
        integer("qrcode.border", qrcode, "Borda (módulos)", 4);

        number("etch.thickness", "Etch Compensation", "Espessura do cobre (µm)", "18");

        for (String[] tool : new String[][] {{"punch", "Punch Gerber"}, {"extract", "Extract Drills"}}) {
            millimetres(tool[0] + ".fixeddiameter", tool[1], "Diâmetro fixo", "0.5");
            number(tool[0] + ".factor", tool[1], "Proporção (%)", "80");
            millimetres(tool[0] + ".ringcircular", tool[1], "Anel: circular", "0.2");
            millimetres(tool[0] + ".ringoblong", tool[1], "Anel: oblongo", "0.2");
            millimetres(tool[0] + ".ringsquare", tool[1], "Anel: quadrado", "0.2");
            millimetres(tool[0] + ".ringrectangular", tool[1], "Anel: retangular", "0.2");
            millimetres(tool[0] + ".ringother", tool[1], "Anel: outros", "0.2");
        }

        millimetres("invert.margin", "Invert Gerber", "Margem", "0.1");
        millimetres("paste.newnozzle", "SolderPaste", "Diâmetro de novo bico", "0.3");

        String calibration = "Calibration";
        millimetres("calibration.travelz", calibration, "Z de deslocamento", "2.0");
        millimetres("calibration.verificationz", calibration, "Z de verificação", "0.1");
        millimetres("calibration.toolchangez", calibration, "Z de troca de ferramenta", "15.0");

        integer("optimal.precision", "Optimal", "Precisão (casas)", 4);

        String cnc = "CNC Job (geral)";
        choice("cnc.preprocessor", cnc, "Pós-processador", "FX_PORTABLE", "FX_PORTABLE", "DEFAULT", "DEFAULT_NO_M6", "GRBL_11", "GRBL_11_NO_M6", "MARLIN", "REPETIER", "BERTA_CNC", "GRBL_LASER", "MARLIN_LASER_FAN_PIN", "MARLIN_LASER_SPINDLE_PIN", "Z_LASER", "ISEL_CNC", "TOOLCHANGE_MANUAL", "TOOLCHANGE_CUSTOM", "LINE_XYZ", "ISEL_ICP_CNC", "HPGL", "ROLAND_MDX_20", "TOOLCHANGE_PROBE_MACH3");

        String drilling = "Drilling";
        length("drilling.cutz", drilling, "Cut Z", "-1.7", "-0.07");
        length("drilling.depthperpass", drilling, "Profundidade por passe", "0.7", "0.03");
        length("drilling.travelz", drilling, "Travel Z", "2.0", "0.1");
        length("drilling.feedz", drilling, "Avanço Z", "300", "12");
        number("drilling.spindle", drilling, "Spindle (RPM/potência)", "0");
        number("drilling.dwelltime", drilling, "Tempo de dwell (s)", "1.0");
        length("drilling.offsetz", drilling, "Offset Z", "0.0", "0.0");
        length("drilling.toolchangez", drilling, "Z de troca de ferramenta", "15.0", "0.6");
        length("drilling.endz", drilling, "Z final", "0.5", "0.02");
        length("drilling.feedrapid", drilling, "Avanço rápido", "0", "0");

        String geometry = "Geometry CNC";
        length("geometry.tooldia", geometry, "Diâmetro da ferramenta", "0.8", "0.031");
        length("geometry.travelz", geometry, "Travel Z", "3.0", "0.1");
        length("geometry.cutdepth", geometry, "Profundidade de corte", "0.1", "0.004");
        length("geometry.depthperpass", geometry, "Profundidade por passe", "0.05", "0.002");
        length("geometry.feedrate", geometry, "Avanço XY", "300", "12");
        length("geometry.probechangez", geometry, "Z de troca (sonda)", "15", "0.6");
        length("geometry.vtipdia", geometry, "V-Tip: diâmetro da ponta", "0.1", "0.004");
        number("geometry.vtipangle", geometry, "V-Tip: ângulo (graus)", "30");

        length("milling.tooldia", "Excellon Milling", "Diâmetro da ferramenta", "0.8", "0.0315");

        String gerberImport = "Importação: Gerber";
        choice("import.gerber.units", gerberImport, "Unidades quando o arquivo não declara", "IN", "IN", "MM");
        integer("import.gerber.circlesteps", gerberImport, "Segmentos por círculo (arcos)", 64);

        String excellonImport = "Importação: Excellon";
        choice("import.excellon.units", excellonImport, "Unidades quando o arquivo não declara", "RECUSAR",
                "RECUSAR", "IN", "MM");
        integer("import.excellon.decimals.in", excellonImport, "Casas decimais sem ponto (polegadas)", 4);
        integer("import.excellon.decimals.mm", excellonImport, "Casas decimais sem ponto (mm)", 3);
    }

    // --- definitions ---------------------------------------------------------------------------------------------

    private static void length(String key, String tool, String label, String millimetres, String inches) {
        define(new Setting(key, tool, label, Kind.NUMBER, millimetres, inches, List.of()));
    }

    /**
     * A length given in millimetres whose inch value is the same length converted (4 decimals), as Python converts its
     * defaults when the units change.
     */
    private static void millimetres(String key, String tool, String label, String value) {
        String inches = java.math.BigDecimal.valueOf(Double.parseDouble(value) / 25.4)
                .setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        length(key, tool, label, value, inches.contains(".") ? inches : inches + ".0");
    }

    /** A free text that depends on the units (a list of diameters): validated by the panel that reads it. */
    private static void text(String key, String tool, String label, String millimetres, String inches) {
        define(new Setting(key, tool, label, Kind.CHOICE, millimetres, inches, List.of()));
    }

    private static void number(String key, String tool, String label, String value) {
        define(new Setting(key, tool, label, Kind.NUMBER, value, null, List.of()));
    }

    private static void integer(String key, String tool, String label, int value) {
        define(new Setting(key, tool, label, Kind.INTEGER, Integer.toString(value), null, List.of()));
    }

    private static void flag(String key, String tool, String label, boolean value) {
        define(new Setting(key, tool, label, Kind.FLAG, Boolean.toString(value), null, List.of()));
    }

    private static void choice(String key, String tool, String label, String value, String... choices) {
        define(new Setting(key, tool, label, Kind.CHOICE, value, null, List.of(choices)));
    }

    private static void define(Setting setting) {
        if (SETTINGS.put(setting.key(), setting) != null) {
            throw new IllegalStateException("Duplicate tool default: " + setting.key());
        }
        for (boolean metric : new boolean[] {true, false}) {
            String problem = problem(setting, setting.factory(metric));
            if (problem != null) {
                throw new IllegalStateException(setting.key() + ": " + problem);
            }
        }
    }

    // --- reading -------------------------------------------------------------------------------------------------

    static List<Setting> settings() {
        return List.copyOf(SETTINGS.values());
    }

    /** The settings grouped by tool, in the order they were defined. */
    static Map<String, List<Setting>> byTool() {
        Map<String, List<Setting>> grouped = new LinkedHashMap<>();
        for (Setting setting : SETTINGS.values()) {
            grouped.computeIfAbsent(setting.tool(), tool -> new ArrayList<>()).add(setting);
        }
        return grouped;
    }

    static Setting setting(String key) {
        Setting setting = SETTINGS.get(key);
        if (setting == null) {
            throw new IllegalArgumentException("Unknown tool default: " + key);
        }
        return setting;
    }

    /** The value as text, for a field: the saved one when it is still valid, the factory one otherwise. */
    static String text(String key, boolean metric) {
        Setting setting = setting(key);
        String saved = store.get(storeKey(setting, metric));
        return saved != null && problem(setting, saved) == null ? saved : setting.factory(metric);
    }

    /** The value for the units the application is showing, for the panels that do not belong to one object. */
    static String text(String key) {
        return text(key, displayMetric.getAsBoolean());
    }

    static double number(String key, boolean metric) {
        return Double.parseDouble(text(key, metric));
    }

    /**
     * A list of positive numbers written as "1.0, 0.5" (comma, semicolon or space separated). A saved text that is
     * not such a list falls back to the factory one, so a panel never opens without tools.
     */
    static double[] numbers(String key, boolean metric) {
        double[] saved = parseNumbers(text(key, metric));
        return saved != null ? saved : parseNumbers(setting(key).factory(metric));
    }

    private static double[] parseNumbers(String text) {
        String[] parts = text.trim().split("[,;\\s]+");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException invalid) {
                return null;
            }
            if (!Double.isFinite(values[i]) || values[i] <= 0) {
                return null;
            }
        }
        return values.length == 0 ? null : values;
    }

    static int integer(String key) {
        return Integer.parseInt(text(key, true));
    }

    static boolean flag(String key) {
        return Boolean.parseBoolean(text(key, true));
    }

    static String choice(String key) {
        return text(key, true);
    }

    static <E extends Enum<E>> E choice(String key, Class<E> type) {
        return Enum.valueOf(type, text(key, true));
    }

    // --- writing -------------------------------------------------------------------------------------------------

    /** Saves a value; throws {@link IllegalArgumentException} with a message for the user when it is not valid. */
    static void set(String key, boolean metric, String value) {
        Setting setting = setting(key);
        String cleaned = clean(setting, value);
        String problem = problem(setting, cleaned);
        if (problem != null) {
            throw new IllegalArgumentException(setting.tool() + " - " + setting.label() + ": " + problem);
        }
        if (cleaned.equals(setting.factory(metric))) {
            store.remove(storeKey(setting, metric));
        } else {
            store.put(storeKey(setting, metric), cleaned);
        }
    }

    static void resetAll() {
        for (Setting setting : SETTINGS.values()) {
            store.remove(storeKey(setting, true));
            if (setting.perUnit()) {
                store.remove(storeKey(setting, false));
            }
        }
    }

    /** Every value (not only the changed ones), so the file documents itself and can be edited by hand. */
    static Properties export() {
        Properties properties = new Properties();
        for (Setting setting : SETTINGS.values()) {
            properties.setProperty(storeKey(setting, true), text(setting.key(), true));
            if (setting.perUnit()) {
                properties.setProperty(storeKey(setting, false), text(setting.key(), false));
            }
        }
        return properties;
    }

    /**
     * Applies the values of an exported file; unknown keys and invalid values are skipped.
     *
     * @return {applied, skipped}
     */
    static int[] importFrom(Properties properties) {
        int applied = 0;
        int skipped = 0;
        for (String name : properties.stringPropertyNames()) {
            boolean metric = !name.endsWith("@in");
            String key = name.endsWith("@in") || name.endsWith("@mm") ? name.substring(0, name.length() - 3) : name;
            Setting setting = SETTINGS.get(key);
            if (setting == null || setting.perUnit() != (key.length() != name.length())) {
                skipped++;
                continue;
            }
            try {
                set(key, metric, properties.getProperty(name));
                applied++;
            } catch (IllegalArgumentException invalid) {
                skipped++;
            }
        }
        return new int[] {applied, skipped};
    }

    // --- storage -------------------------------------------------------------------------------------------------

    /**
     * The Gerber import options of Preferences. Values outside what the parser accepts (a hand-edited file) fall back
     * to the parser's own.
     */
    static org.flatcam.cam.gerber.GerberParser.Options gerberImport() {
        try {
            return new org.flatcam.cam.gerber.GerberParser.Options(choice("import.gerber.units"),
                    integer("import.gerber.circlesteps"));
        } catch (IllegalArgumentException invalid) {
            return org.flatcam.cam.gerber.GerberParser.Options.standard();
        }
    }

    /** The Excellon import options of Preferences ("RECUSAR" keeps refusing a file that never declares units). */
    static org.flatcam.cam.excellon.ExcellonParser.Options excellonImport() {
        try {
            String units = choice("import.excellon.units");
            return new org.flatcam.cam.excellon.ExcellonParser.Options("RECUSAR".equals(units) ? null : units,
                    integer("import.excellon.decimals.in"), integer("import.excellon.decimals.mm"));
        } catch (IllegalArgumentException invalid) {
            return org.flatcam.cam.excellon.ExcellonParser.Options.standard();
        }
    }

    /** Tells which units the application shows; millimetres until the application says otherwise. */
    static void useDisplayUnits(java.util.function.BooleanSupplier metric) {
        displayMetric = metric;
    }

    static void useStore(Store replacement) {
        store = replacement;
    }

    static Store memoryStore() {
        Map<String, String> values = new HashMap<>();
        return new Store() {
            @Override
            public String get(String key) {
                return values.get(key);
            }

            @Override
            public void put(String key, String value) {
                values.put(key, value);
            }

            @Override
            public void remove(String key) {
                values.remove(key);
            }
        };
    }

    private static String storeKey(Setting setting, boolean metric) {
        return setting.perUnit() ? setting.key() + (metric ? "@mm" : "@in") : setting.key();
    }

    private static String clean(Setting setting, String value) {
        String text = value == null ? "" : value.trim();
        return switch (setting.kind()) {
            case NUMBER -> text.replace(',', '.');
            case FLAG -> text.toLowerCase(Locale.ROOT);
            default -> text;
        };
    }

    /** Why {@code value} cannot be this setting, or null when it can. */
    private static String problem(Setting setting, String value) {
        switch (setting.kind()) {
            case NUMBER -> {
                try {
                    if (!Double.isFinite(Double.parseDouble(value))) {
                        return "deve ser um número";
                    }
                } catch (NumberFormatException invalid) {
                    return "deve ser um número";
                }
            }
            case INTEGER -> {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException invalid) {
                    return "deve ser um número inteiro";
                }
            }
            case FLAG -> {
                if (!value.equals("true") && !value.equals("false")) {
                    return "deve ser true ou false";
                }
            }
            case CHOICE -> {
                if (setting.choices().isEmpty() ? value.isBlank() : !setting.choices().contains(value)) {
                    return setting.choices().isEmpty() ? "não pode ficar vazio"
                            : "deve ser um de " + String.join(", ", setting.choices());
                }
            }
        }
        return null;
    }
}
