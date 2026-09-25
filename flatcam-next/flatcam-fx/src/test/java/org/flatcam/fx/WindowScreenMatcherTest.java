package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WindowScreenMatcherTest {

    @Test
    void restoresTheScreenWhoseJavaFxBoundsWereSaved() {
        var primary = new WindowScreenMatcher.Bounds(0, 0, 1920, 1080);
        var secondary = new WindowScreenMatcher.Bounds(1920, 0, 2560, 1440);
        assertEquals(1, WindowScreenMatcher.bestMatch(List.of(primary, secondary), secondary));
    }

    @Test
    void acceptsSmallLayoutChangesAndRejectsMissingGeometry() {
        var saved = new WindowScreenMatcher.Bounds(1920, 0, 2560, 1440);
        var primary = new WindowScreenMatcher.Bounds(0, 0, 1920, 1080);
        var shifted = new WindowScreenMatcher.Bounds(1900, 0, 2560, 1440);
        assertEquals(1, WindowScreenMatcher.bestMatch(List.of(primary, shifted), saved));
        assertEquals(-1, WindowScreenMatcher.bestMatch(List.of(), saved));
        assertEquals(-1, WindowScreenMatcher.bestMatch(List.of(primary), null));
    }
}
