package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ThemeOptionTest {

    @Test
    void oldThemePreferencesMigrateWithoutLosingLightOrDarkChoice() {
        assertEquals(ThemeOption.ICE_LIGHT,
                ThemeOption.fromSavedName("CUSTOM_LIGHT", ThemeOption.CLASSIC_LIGHT));
        assertEquals(ThemeOption.ICE_DARK,
                ThemeOption.fromSavedName("CUSTOM_DARK", ThemeOption.CLASSIC_LIGHT));
        assertEquals(ThemeOption.CLASSIC_LIGHT,
                ThemeOption.fromSavedName("ATLANTAFX_LIGHT", ThemeOption.ICE_LIGHT));
        assertEquals(ThemeOption.CLASSIC_DARK,
                ThemeOption.fromSavedName("ATLANTAFX_DARK", ThemeOption.ICE_LIGHT));
        assertEquals(ThemeOption.ICE_LIGHT,
                ThemeOption.fromSavedName("ICE_LIGHT", ThemeOption.CLASSIC_LIGHT));
        assertEquals(ThemeOption.CLASSIC_LIGHT,
                ThemeOption.fromSavedName("unknown", ThemeOption.CLASSIC_LIGHT));
    }

    @Test
    void allFourThemesUseBundledStylesAndCorrectDarkMode() {
        assertEquals(4, ThemeOption.values().length);
        for (ThemeOption option : ThemeOption.values()) {
            assertEquals(option.name().endsWith("DARK"), option.isDark());
        }
        for (String resource : new String[]{"vars-classic-light.css", "vars-classic-dark.css",
                "vars-ice-light.css", "vars-ice-dark.css", "components-classic.css", "components.css"}) {
            assertNotNull(ThemeOption.class.getResource("theme/" + resource), resource);
        }
        assertFalse(ThemeOption.CLASSIC_LIGHT.isDark());
        assertTrue(ThemeOption.ICE_DARK.isDark());
    }

    @Test
    void originalAndIceKeepDistinctHistoricalPalettes() throws IOException {
        String classicLight = resource("vars-classic-light.css");
        String classicDark = resource("vars-classic-dark.css");
        String iceLight = resource("vars-ice-light.css");
        String iceDark = resource("vars-ice-dark.css");
        assertTrue(classicLight.contains("-fx-base: #f2f2f2;"));
        assertTrue(classicDark.contains("-fx-base: #2b2b2b;"));
        assertTrue(iceLight.contains("-fc-panel-bg: #f8fbfd;"));
        assertTrue(iceDark.contains("-fc-panel-bg: #1b2633;"));
        assertFalse(resource("components-classic.css").contains(".object-header {"));
        assertTrue(resource("components.css").contains(".object-header {"));
    }

    @Test
    void objectTooltipsHaveExplicitReadableColorsInPopupScenes() {
        for (ThemeOption theme : ThemeOption.values()) {
            String style = theme.objectTooltipStyle();
            assertTrue(style.contains("-fx-background-color:"), theme.name());
            assertTrue(style.contains("-fx-text-fill:"), theme.name());
            ThemeOption.TooltipPalette palette = theme.tooltipPalette();
            assertTrue(style.contains(palette.text()) && style.contains(palette.background())
                    && style.contains(palette.border()), theme.name());
        }
    }

    private static String resource(String name) throws IOException {
        try (var stream = ThemeOption.class.getResourceAsStream("theme/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
