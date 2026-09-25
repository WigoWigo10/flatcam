package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlotMoveHistoryTest {

    @Test
    void undoRedoKeepsGroupAndOffsetInOrder() {
        PlotMoveHistory<String> history = new PlotMoveHistory<>();
        history.record(List.of("Gerber", "Excellon"), 12, -4);
        history.record(List.of("Geometry"), -2, 5);

        assertEquals(List.of("Geometry"), history.undo().targets());
        PlotMoveHistory.Move<String> group = history.undo();
        assertEquals(List.of("Gerber", "Excellon"), group.targets());
        assertEquals(12, group.dx());
        assertEquals(-4, group.dy());
        assertNull(history.undo());
        assertEquals(group, history.redo());
        assertEquals(List.of("Geometry"), history.redo().targets());
        assertNull(history.redo());
    }

    @Test
    void newMoveClearsRedoAndExplicitInvalidationClearsBothStacks() {
        PlotMoveHistory<String> history = new PlotMoveHistory<>();
        history.record(List.of("A"), 1, 2);
        history.undo();
        history.record(List.of("B"), 3, 4);
        assertNull(history.redo());
        history.clear();
        assertNull(history.undo());
        assertNull(history.redo());
    }
}
