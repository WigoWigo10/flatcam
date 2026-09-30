package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaintToolPanelTest {
    @Test
    void readsToolDiametersSeparatedByCommasSemicolonsOrSpacesWithoutRepeats() {
        assertEquals(List.of(0.3, 1.0, 2.5), PaintToolPanel.parseDiameters("0.3, 1.0; 2.5 0.3"));
        assertEquals(List.of(0.2), PaintToolPanel.parseDiameters(" 0.2 "));
    }

    @Test
    void refusesEmptyOrInvalidDiameters() {
        assertThrows(IllegalArgumentException.class, () -> PaintToolPanel.parseDiameters(" , "));
        assertThrows(IllegalArgumentException.class, () -> PaintToolPanel.parseDiameters("0.3, abc"));
        assertThrows(IllegalArgumentException.class, () -> PaintToolPanel.parseDiameters("-1"));
    }
}
