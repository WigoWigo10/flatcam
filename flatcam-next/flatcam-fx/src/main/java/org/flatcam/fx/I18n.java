package org.flatcam.fx;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** UTF-8 UI catalogs; canonical labels remain available to command/help registries. */
final class I18n {
    private static final Map<UiLanguage, Map<String, String>> CATALOGS = loadCatalogs();
    private static final Map<String, String> KEYS = sourceKeys();
    private static final Map<String, List<String>> SOURCES = translatedSources();
    private static final Map<String, List<String>> PREFIXES = prefixSources();
    private static volatile UiLanguage language = UiLanguage.PT_BR;
    private static boolean initialized;

    private I18n() { }
    static UiLanguage language() { return language; }
    static synchronized void initialize(UiLanguage selected) {
        if (!initialized) { language = Objects.requireNonNull(selected); initialized = true; }
    }
    static String text(String key) {
        String value = CATALOGS.get(language).get(key);
        if (value == null) throw new IllegalArgumentException("Unknown UI text key: " + key);
        return value;
    }
    /** Use only for canonical UI metadata (field/tool names), never project names or user documents. */
    static String caption(String canonical) {
        String key = KEYS.get(canonical);
        return key == null ? canonical : text(key);
    }
    static String source(String caption) {
        if (language == UiLanguage.PT_BR) return caption;
        var values = SOURCES.get(caption);
        return values == null ? caption : values.getFirst();
    }
    /** A registry tries its authored aliases first; translations can share a caption. */
    static List<String> sources(String caption) {
        if (caption == null) return List.of("");
        if (language == UiLanguage.PT_BR) return List.of(caption);
        var result = new LinkedHashSet<String>();
        result.add(caption);
        result.addAll(SOURCES.getOrDefault(caption, List.of()));
        // Unit/type suffixes are appended by the form: e.g. "Tool diameter (" + units + "):".
        // This only resolves help/icon metadata; it never rewrites object names or document content.
        for (var entry : PREFIXES.entrySet()) {
            String prefix = entry.getKey();
            if (caption.startsWith(prefix))
                for (String original : entry.getValue()) result.add(original + caption.substring(prefix.length()));
        }
        // Presentation-only suffixes are appended outside the catalog in a few form rows.
        for (String suffix : List.of(":", " (mm)", " (in)", "...", "…"))
            if (caption.endsWith(suffix)) {
                String base = caption.substring(0, caption.length() - suffix.length());
                for (String original : SOURCES.getOrDefault(base, List.of())) result.add(original + suffix);
            }
        return List.copyOf(result);
    }
    /** Resolve a displayed choice within this command's own domain, preserving canonical aliases. */
    static String source(String caption, String... choices) {
        for (String choice : choices) if (choice.equals(caption)) return choice;
        for (String choice : choices) if (caption(choice).equals(caption)) return choice;
        return caption;
    }
    static Map<String,String> catalog(UiLanguage selected) { return CATALOGS.get(selected); }
    /** Translate framework buttons without changing the ButtonType identities used by result converters. */
    static void dialogButtons(javafx.scene.control.DialogPane pane) {
        for (var type : pane.getButtonTypes()) {
            String canonical = type == javafx.scene.control.ButtonType.CANCEL ? "Cancelar"
                    : type == javafx.scene.control.ButtonType.CLOSE ? "Fechar"
                    : type == javafx.scene.control.ButtonType.YES ? "Sim"
                    : type == javafx.scene.control.ButtonType.NO ? "Não"
                    : type == javafx.scene.control.ButtonType.APPLY ? "Aplicar" : null;
            if (canonical != null && pane.lookupButton(type) instanceof javafx.scene.control.Button button)
                button.setText(caption(canonical));
        }
    }
    private static Map<UiLanguage, Map<String,String>> loadCatalogs() {
        Map<UiLanguage, Map<String,String>> result = new EnumMap<>(UiLanguage.class);
        for (var selected : UiLanguage.values()) {
            String path = "/org/flatcam/fx/i18n/messages_" + selected.catalog + ".properties";
            try (var stream = I18n.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing UI catalog: " + path);
                Properties properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                Map<String,String> values = new LinkedHashMap<>();
                properties.stringPropertyNames().stream().sorted().forEach(key -> values.put(key, properties.getProperty(key)));
                result.put(selected, Collections.unmodifiableMap(values));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,String> sourceKeys() {
        Map<String,String> result = new HashMap<>();
        CATALOGS.get(UiLanguage.PT_BR).forEach((key, value) -> result.putIfAbsent(value, key));
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,List<String>> translatedSources() {
        Map<String,List<String>> result = new HashMap<>();
        CATALOGS.get(UiLanguage.EN).forEach((key, value) -> {
            String canonical = CATALOGS.get(UiLanguage.PT_BR).get(key);
            if (canonical != null) result.computeIfAbsent(value, unused -> new ArrayList<>()).add(canonical);
        });
        result.replaceAll((key, values) -> List.copyOf(values));
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,List<String>> prefixSources() {
        Map<String,List<String>> result = new LinkedHashMap<>();
        SOURCES.entrySet().stream().filter(entry -> entry.getKey().length() > 3 && entry.getKey().length() < 80
                && (entry.getKey().endsWith(" (") || entry.getKey().endsWith(" ")))
                .sorted(Comparator.comparingInt((Map.Entry<String,List<String>> entry) -> entry.getKey().length()).reversed())
                .forEach(entry -> result.put(entry.getKey(),entry.getValue()));
        return Collections.unmodifiableMap(result);
    }
}
