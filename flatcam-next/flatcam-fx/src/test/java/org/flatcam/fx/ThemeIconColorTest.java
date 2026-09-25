package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ThemeIconColorTest {

    @Test
    void vectorIconsUseReadableColorDefinedByEveryTheme() throws IOException {
        String components = resource("components.css");
        assertTrue(components.contains("-fx-stroke: -fc-panel-text;"));
        assertTrue(components.contains("-fx-fill: -fc-panel-text;"));

        for (String theme : new String[]{"custom-light", "custom-dark", "atlantafx-light", "atlantafx-dark"}) {
            String css = resource("vars-" + theme + ".css");
            double contrast = contrast(color(css, "-fc-panel-text"), color(css, "-fc-panel-bg"));
            assertTrue(contrast >= 4.5, () -> theme + " icon contrast is " + contrast);
        }
    }

    private static String resource(String fileName) throws IOException {
        try (var stream = ThemeIconColorTest.class.getResourceAsStream("theme/" + fileName)) {
            assertNotNull(stream, fileName);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String color(String css, String property) {
        Matcher matcher = Pattern.compile(Pattern.quote(property) + ":\\s*(#[0-9a-fA-F]{6})")
                .matcher(css);
        assertTrue(matcher.find(), property);
        return matcher.group(1);
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        double[] weights = {0.2126, 0.7152, 0.0722};
        double value = 0;
        for (int i = 0; i < 3; i++) {
            double channel = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            value += weights[i] * (channel <= 0.04045
                    ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return value;
    }
}
