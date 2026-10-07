package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javafx.scene.paint.Color;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ToolPreviewPaletteTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void categoryInksRemainDistinctAndReadableInEveryTheme(ThemeOption theme) {
        var background = PlotAreaView.paletteForTheme(theme).background();
        boolean dark = background.getBrightness() < .5;
        var inks = List.of(PlotAreaView.LayerCategory.GERBER, PlotAreaView.LayerCategory.EXCELLON,
                PlotAreaView.LayerCategory.GEOMETRY).stream().map(kind -> PlotAreaView.toolPreviewColors(kind, dark)).toList();
        assertEquals(3, inks.stream().map(PlotAreaView.PreviewColors::stroke).distinct().count());
        for (var ink : inks) {
            double a = luminance(background), b = luminance(ink.stroke());
            assertTrue((Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 3,
                    "thin outline contrast in " + theme + ": " + ink.stroke());
            assertTrue(ink.fill().getOpacity() > 0 && ink.fill().getOpacity() < 1);
        }
        var opposite = PlotAreaView.toolPreviewColors(PlotAreaView.LayerCategory.GERBER, !dark);
        assertNotEquals(inks.getFirst(), opposite, "theme changes the inks, not the category meaning");
    }
    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }
    private static double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
