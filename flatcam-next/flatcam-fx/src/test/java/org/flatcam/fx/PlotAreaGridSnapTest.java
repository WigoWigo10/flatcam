package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

class PlotAreaGridSnapTest {

    @Test
    void snapsPositiveAndNegativeCoordinatesToConfiguredSteps() {
        assertEquals(4.0, PlotAreaView.snapCoordinate(4.2, 1.0));
        assertEquals(-3.5, PlotAreaView.snapCoordinate(-3.6, 0.5));
        assertEquals(2.4, PlotAreaView.snapCoordinate(2.36, 0.2), 1e-9);
    }

    @Test
    void horizontalAndVerticalStepsCanDiffer() {
        assertEquals(2.0, PlotAreaView.snapCoordinate(2.4, 1.0));
        assertEquals(2.5, PlotAreaView.snapCoordinate(2.4, 0.5));
    }

    @Test
    void hudIncludesRelativeAndAbsoluteCoordinatesWithUnits() {
        assertEquals("Dx: 2.5000 [mm]" + System.lineSeparator()
                        + "Dy: -1.0000 [mm]" + System.lineSeparator() + System.lineSeparator()
                        + "X: 5.0000 [mm]" + System.lineSeparator() + "Y: 3.0000 [mm]",
                PlotAreaView.formatHud(2.5, -1, 5, 3, "MM"));
    }

    @Test
    void snapCrossUsesBrighterRedAndDarkHaloInDarkThemes() {
        for (ThemeOption theme : ThemeOption.values()) {
            PlotAreaView.PlotPalette palette = PlotAreaView.paletteForTheme(theme);
            assertEquals(theme.isDark() ? Color.web("#ff6b6b") : Color.web("#e53935"),
                    palette.snapCursor(), theme.name());
            if (theme.isDark()) {
                assertTrue(palette.snapOutline().getBrightness() < 0.2, theme.name());
            } else {
                assertTrue(palette.snapOutline().getBrightness() > 0.9, theme.name());
            }
        }
    }
}
