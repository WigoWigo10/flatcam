package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class I18nTest {
    @Test void catalogsHaveIdenticalKeysValidUnicodeAndMatchingFormatArguments() {
        var portuguese = I18n.catalog(UiLanguage.PT_BR);
        var english = I18n.catalog(UiLanguage.EN);
        assertEquals(portuguese.keySet(), english.keySet());
        assertTrue(portuguese.size() > 3000);
        var format = Pattern.compile("%(?:[0-9]+\\$)?[-#+0,(<]*[0-9]*(?:\\.[0-9]+)?[bBhHsScCdoxXeEfgGaAn%]");
        portuguese.forEach((key, source) -> {
            String translated = english.get(key);
            assertFalse(translated.isBlank(), key);
            assertFalse(source.contains("\ufffd") || translated.contains("\ufffd"), key);
            assertEquals(format.matcher(source).results().map(java.util.regex.MatchResult::group).toList(),
                    format.matcher(translated).results().map(java.util.regex.MatchResult::group).toList(), key);
            assertEquals(source.length() - source.stripLeading().length(), translated.length() - translated.stripLeading().length(), key + " leading whitespace");
            assertEquals(source.length() - source.stripTrailing().length(), translated.length() - translated.stripTrailing().length(), key + " trailing whitespace");
            for (var pair : List.of("()", "[]"))
                assertEquals(source.chars().filter(c -> c == pair.charAt(0)).count() - source.chars().filter(c -> c == pair.charAt(1)).count(),
                        translated.chars().filter(c -> c == pair.charAt(0)).count() - translated.chars().filter(c -> c == pair.charAt(1)).count(), key + " fragment brackets");
        });
        assertTrue(portuguese.containsValue("Preferências"));
        assertThrows(IllegalArgumentException.class, () -> I18n.text("missing.translation"));
    }

    @Test void unsupportedLanguageTagsKeepThePortugueseDefault() {
        assertEquals(UiLanguage.PT_BR, UiLanguage.fromTag(null));
        assertEquals(UiLanguage.PT_BR, UiLanguage.fromTag("fr"));
        assertEquals(UiLanguage.EN, UiLanguage.fromTag("en"));
        assertEquals("Português (Brasil)", UiLanguage.PT_BR.toString());
    }

    @Test void englishTooltipsRetainSemanticFormattingAndShortcuts() {
        var content = TooltipContent.describe("Warning: check Cut Z.\n\nUnits: mm or in.\n\nShortcut: Ctrl+Shift+S and Shift+J.");
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Warning:") && s.style() == TooltipContent.Style.NOTE));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Units:") && s.style() == TooltipContent.Style.ACCENT));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Ctrl+Shift+S") && s.style() == TooltipContent.Style.BOLD));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Shift+J") && s.style() == TooltipContent.Style.BOLD));
    }
}
