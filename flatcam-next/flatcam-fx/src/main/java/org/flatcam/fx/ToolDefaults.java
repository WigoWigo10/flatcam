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
    }

    // --- definitions ---------------------------------------------------------------------------------------------

    private static void length(String key, String tool, String label, String millimetres, String inches) {
        define(new Setting(key, tool, label, Kind.NUMBER, millimetres, inches, List.of()));
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
