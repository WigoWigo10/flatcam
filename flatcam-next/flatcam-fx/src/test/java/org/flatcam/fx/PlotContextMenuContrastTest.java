package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlotContextMenuContrastTest {

    @Test
    void focusedMenuTextHasAtLeastNormalTextContrastInEveryTheme() throws IOException {
        for (String theme : new String[]{"custom-light", "custom-dark", "atlantafx-light", "atlantafx-dark"}) {
            String css = resource("vars-" + theme + ".css");
            double contrast = contrast(color(css, "-fc-plot-menu-focus-text"),
                    color(css, "-fc-plot-menu-focus-bg"));
            assertTrue(contrast >= 4.5, () -> theme + " focused menu contrast is " + contrast);
        }
        String components = resource("components.css");
        assertTrue(components.contains(".plot-context-menu .menu-item:focused .label"));
        assertTrue(components.contains(".plot-context-menu .menu-item:focused .label .text"));
    }

    private static String resource(String fileName) throws IOException {
        try (var stream = PlotContextMenuContrastTest.class.getResourceAsStream("theme/" + fileName)) {
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
        double[] channel = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            channel[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channel[0] + 0.7152 * channel[1] + 0.0722 * channel[2];
    }
}
