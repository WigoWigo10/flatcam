package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ThemeDesignTokensTest {

    @Test
    void sharedComponentsHaveTokensAndReadableTextInEveryTheme() throws IOException {
        String components = resource("components.css");
        for (String selector : new String[]{".tool-panel .button.primary-action",
                ".tool-panel .table-row-cell:selected", ".object-panel .label.object-title",
                ".tool-panel .label.form-error-label"}) {
            assertTrue(components.contains(selector), selector);
        }
        for (String theme : new String[]{"custom-light", "custom-dark", "atlantafx-light", "atlantafx-dark"}) {
            String css = resource("vars-" + theme + ".css");
            assertContrast(css, theme, "-fc-panel-text", "-fc-panel-bg", 4.5);
            assertContrast(css, theme, "-fc-muted-text", "-fc-panel-bg", 4.5);
            assertContrast(css, theme, "-fc-on-accent", "-fc-accent", 4.5);
            assertContrast(css, theme, "-fc-selection-row-text", "-fc-selection-row", 4.5);
        }
    }

    private static void assertContrast(String css, String theme, String foreground,
                                       String background, double minimum) {
        double first = luminance(color(css, foreground));
        double second = luminance(color(css, background));
        double contrast = (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
        assertTrue(contrast >= minimum, () -> theme + " " + foreground + "/" + background
                + " contrast is " + contrast);
    }

    private static String resource(String name) throws IOException {
        try (var stream = ThemeDesignTokensTest.class.getResourceAsStream("theme/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String color(String css, String token) {
        Matcher matcher = Pattern.compile(Pattern.quote(token) + ":\\s*(#[0-9a-fA-F]{6})")
                .matcher(css);
        assertTrue(matcher.find(), token);
        return matcher.group(1);
    }

    private static double luminance(String color) {
        double[] weights = {0.2126, 0.7152, 0.0722};
        double result = 0;
        for (int i = 0; i < 3; i++) {
            double channel = Integer.parseInt(color.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            result += weights[i] * (channel <= 0.04045
                    ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return result;
    }
}
