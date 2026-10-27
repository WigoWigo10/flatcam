package org.flatcam.fx;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-insensitive AND search, shared by navigation and fields. */
final class PreferencesSearch {
    private PreferencesSearch() { }
    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
    static boolean matches(String query, String... values) {
        String haystack = normalize(String.join(" ", values));
        String normalized = normalize(query);
        if (normalized.isEmpty()) return true;
        for (String word : normalized.split("\\s+"))
            if (!java.util.regex.Pattern.compile("(^|[^\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(word)).matcher(haystack).find()) return false;
        return true;
    }
}
