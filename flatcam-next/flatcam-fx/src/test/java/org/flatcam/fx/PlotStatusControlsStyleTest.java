package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlotStatusControlsStyleTest {

    @Test
    void activeControlsContrastWithDarkIconsOnLightThemesAndLightIconsOnDarkThemes() throws IOException {
        String components = resource("components.css");
        Map<String, String> lightColors = Map.of(
                "grid", "#4a8bff",
                "axis", "#ffa500",
                "hud", "#9370db",
                "workspace", "#6b8e23",
                "console", "#f08080",
                "preferences", "#20b2aa");
        assertTrue(components.contains("-fc-status-active: -fc-status-grid;"));
        for (String control : lightColors.keySet()) {
            if (!control.equals("grid")) {
                assertTrue(components.contains(".status-" + control
                        + " { -fc-status-active: -fc-status-" + control + "; }"), control);
            }
            for (String family : new String[]{"custom", "atlantafx"}) {
                String light = color(resource("vars-" + family + "-light.css"), control);
                String dark = color(resource("vars-" + family + "-dark.css"), control);
                assertTrue(lightColors.get(control).equalsIgnoreCase(light), family + " " + control);
                assertTrue(luminance(dark) < luminance(light), family + " " + control);
                assertTrue(contrastWithBlack(light) >= 4.5, family + " light " + control);
                assertTrue(contrastWithWhite(dark) >= 7.5, family + " dark " + control);
                // The legacy dark-resource icons use #d3d3d3 rather than pure white.
                assertTrue(contrast("#d3d3d3", dark) >= 4.5, family + " dark icon " + control);
            }
        }
        assertTrue(components.contains("-fx-text-fill: -fc-status-active-text;"));
        for (String family : new String[]{"custom", "atlantafx"}) {
            assertTrue(resource("vars-" + family + "-light.css")
                    .contains("-fc-status-active-text: #111111;"));
            assertTrue(resource("vars-" + family + "-dark.css")
                    .contains("-fc-status-active-text: #ffffff;"));
        }
        assertTrue(components.contains(".status-control:selected:hover"));
        assertTrue(components.contains(".status-bar-toggle:selected:hover"));
    }

    @Test
    void everyThemeDefinesInactiveHoverColors() throws IOException {
        for (String theme : new String[]{"custom-light", "custom-dark", "atlantafx-light", "atlantafx-dark"}) {
            String css = resource("vars-" + theme + ".css");
            assertTrue(css.contains("-fc-status-hover:"), theme);
            assertTrue(css.contains("-fc-status-hover-border:"), theme);
        }
    }

    private static String resource(String fileName) throws IOException {
        try (var stream = PlotStatusControlsStyleTest.class.getResourceAsStream("theme/" + fileName)) {
            assertNotNull(stream, fileName);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String color(String css, String control) {
        Matcher matcher = Pattern.compile("-fc-status-" + control + ":\\s*(#[0-9a-fA-F]{6})")
                .matcher(css);
        assertTrue(matcher.find(), control);
        return matcher.group(1);
    }

    private static double contrastWithBlack(String hex) {
        return (luminance(hex) + 0.05) / 0.05;
    }

    private static double contrastWithWhite(String hex) {
        return 1.05 / (luminance(hex) + 0.05);
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        double luminance = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double channel = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            luminance += weights[i] * (channel <= 0.04045
                    ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return luminance;
    }
}
