package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class I18nTest {
    @Test void catalogsHaveIdenticalKeysValidUnicodeAndMatchingFormatArguments() {
        var portuguese = I18n.catalog(UiLanguage.PT_BR);
        assertTrue(portuguese.size() > 3000);
        var format = Pattern.compile("%(?:[0-9]+\\$)?[-#+0,(<]*[0-9]*(?:\\.[0-9]+)?[bBhHsScCdoxXeEfgGaAn%]");
        for (var language : UiLanguage.values()) {
            var translatedCatalog = I18n.catalog(language);
            assertEquals(portuguese.keySet(), translatedCatalog.keySet(), language.tag);
            portuguese.forEach((key, source) -> assertTranslation(key, source, translatedCatalog.get(key), format));
        }
        assertTrue(portuguese.containsValue("Preferências"));
        assertThrows(IllegalArgumentException.class, () -> I18n.text("missing.translation"));
    }

    private static void assertTranslation(String key, String source, String translated, Pattern format) {
            assertFalse(translated.isBlank(), key);
            assertFalse(source.contains("\ufffd") || translated.contains("\ufffd"), key);
            assertEquals(format.matcher(source).results().map(java.util.regex.MatchResult::group).toList(),
                    format.matcher(translated).results().map(java.util.regex.MatchResult::group).toList(), key);
            assertEquals(source.length() - source.stripLeading().length(), translated.length() - translated.stripLeading().length(), key + " leading whitespace");
            assertEquals(source.length() - source.stripTrailing().length(), translated.length() - translated.stripTrailing().length(), key + " trailing whitespace");
            assertEquals(source.chars().filter(c -> c == '\n').count(), translated.chars().filter(c -> c == '\n').count(), key + " line breaks");
            assertEquals(source.split("\\*\\*", -1).length, translated.split("\\*\\*", -1).length, key + " guide emphasis");
            assertFalse(translated.matches("(?s).*__FX(?:ROW|TOKEN|NL)\\d*__.*"), key);
            for (var pair : List.of("()", "[]"))
                assertEquals(source.chars().filter(c -> c == pair.charAt(0)).count() - source.chars().filter(c -> c == pair.charAt(1)).count(),
                        translated.chars().filter(c -> c == pair.charAt(0)).count() - translated.chars().filter(c -> c == pair.charAt(1)).count(), key + " fragment brackets");
    }

    @Test void unsupportedLanguageTagsKeepThePortugueseDefault() {
        assertEquals(UiLanguage.PT_BR, UiLanguage.fromTag(null));
        assertEquals(UiLanguage.PT_BR, UiLanguage.fromTag("unsupported"));
        assertEquals(UiLanguage.EN, UiLanguage.fromTag("en"));
        assertEquals(UiLanguage.ES, UiLanguage.fromTag("es"));
        assertEquals(UiLanguage.FR, UiLanguage.fromTag("fr"));
        assertEquals("Português (Brasil)", UiLanguage.PT_BR.toString());
        assertEquals("Español", UiLanguage.ES.toString());
        assertEquals("Français", UiLanguage.FR.toString());
    }

    @Test void englishTooltipsRetainSemanticFormattingAndShortcuts() {
        var content = TooltipContent.describe("Warning: check Cut Z.\n\nUnits: mm or in.\n\nShortcut: Ctrl+Shift+S and Shift+J.");
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Warning:") && s.style() == TooltipContent.Style.NOTE));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Units:") && s.style() == TooltipContent.Style.ACCENT));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Ctrl+Shift+S") && s.style() == TooltipContent.Style.BOLD));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Shift+J") && s.style() == TooltipContent.Style.BOLD));
    }

    @Test void spanishAndFrenchWarningsUnitsAndShortcutsRetainTheirStyles() {
        for (String text : List.of("Advertencia: compruebe Cut Z.\n\nUnidades: mm o in.\n\nAtajo: Ctrl+Shift+S.",
                "Attention : vérifiez Cut Z.\n\nUnités : mm ou in.\n\nRaccourci : Ctrl+Shift+S.")) {
            var content = TooltipContent.describe(text);
            assertEquals(text, content.plainText());
            assertTrue(content.spans().stream().anyMatch(s -> s.style() == TooltipContent.Style.NOTE), text);
            assertTrue(content.spans().stream().anyMatch(s -> s.style() == TooltipContent.Style.ACCENT), text);
            assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Ctrl+Shift+S") && s.style() == TooltipContent.Style.BOLD), text);
        }
    }
}
