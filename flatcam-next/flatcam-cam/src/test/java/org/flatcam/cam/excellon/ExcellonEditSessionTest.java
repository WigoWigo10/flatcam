package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExcellonEditSessionTest {

    private static ExcellonImage sample() {
        return new ExcellonParser().parse(List.of(
                "M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y1.0", "X2.0Y2.0G85X4.0Y2.0", "M30"));
    }

    @Test
    void selectsMovesCopiesDeletesAndRestoresIndividualHits() {
        ExcellonImage original = sample();
        ExcellonEditSession editor = new ExcellonEditSession(original);
        assertEquals(2, editor.size());
        assertEquals(List.of("Drill", "Slot"), editor.rows().stream().map(ExcellonEditSession.Row::type).toList());
        editor.clickSelect(1, 1, 0, false);
        assertEquals(1, editor.selectedCount());
        assertTrue(editor.moveSelected(1, 0));
        assertEquals(2, editor.resultImage().drills().get(0).x());
        assertEquals(1, editor.selectedCount());
        assertTrue(editor.copySelected(0, 1));
        assertEquals(3, editor.size());
        assertTrue(editor.deleteSelected());
        assertEquals(2, editor.size());
        assertTrue(editor.undo());
        assertEquals(3, editor.size());
        assertTrue(editor.redo());
        assertEquals(2, editor.size());
        assertEquals(1, editor.resultImage().totalDrills());
        assertEquals(1, editor.resultImage().totalSlots());
        assertEquals(original.toolDiameters(), editor.resultImage().toolDiameters());
    }

    @Test
    void addsDrillAndSlotWithExistingToolsOnly() {
        ExcellonEditSession editor = new ExcellonEditSession(sample());
        editor.addDrill(1, 6, 7);
        editor.addSlot(1, 8, 9, 10, 9);
        assertEquals(2, editor.resultImage().totalDrills());
        assertEquals(2, editor.resultImage().totalSlots());
        assertFalse(editor.resultImage().solidGeometry().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> editor.addDrill(999, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> editor.addSlot(1, 0, 0, 0, 0));
    }

    @Test
    void undoBackToOriginalClearsDirtyState() {
        ExcellonEditSession editor = new ExcellonEditSession(sample());
        editor.addDrill(1, 9, 9);
        assertTrue(editor.isDirty());
        assertTrue(editor.undo());
        assertFalse(editor.isDirty());
    }
}
