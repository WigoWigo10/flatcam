package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThemeTooltipPaletteTest {

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
    }

    @Test
    void everyThemeHasItsOwnTooltipLookAndTheClassicDarkOneIsNotTheIceBlue() {
        Set<String> looks = new HashSet<>();
        for (ThemeOption theme : ThemeOption.values()) {
            looks.add(theme.objectTooltipStyle());
        }
        assertTrue(looks.size() == 4, "each of the four themes needs a distinct tooltip style");
        assertNotEquals(ThemeOption.CLASSIC_DARK.tooltipPalette().background(),
                ThemeOption.ICE_DARK.tooltipPalette().background());
        // Neutral grey for the classic theme: red, green and blue equal.
        String classic = ThemeOption.CLASSIC_DARK.tooltipPalette().background();
        assertTrue(classic.substring(1, 3).equals(classic.substring(3, 5)) && classic.substring(3, 5).equals(classic.substring(5, 7)),
                "classic dark tooltip is not neutral: " + classic);
    }

    @Test
    void textAlwaysContrastsWithTheBackground() {
        for (ThemeOption theme : ThemeOption.values()) {
            ThemeOption.TooltipPalette palette = theme.tooltipPalette();
            double gap = Math.abs(luminance(palette.background()) - luminance(palette.text()));
            assertTrue(gap > 0.55, theme + ": text/background luminance gap " + gap);
            assertTrue(theme.isDark() == luminance(palette.background()) < 0.5, theme + ": dark themes get dark tips");
        }
    }
}
