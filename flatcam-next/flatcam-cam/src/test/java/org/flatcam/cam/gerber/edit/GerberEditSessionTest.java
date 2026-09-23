package org.flatcam.cam.gerber.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;

class GerberEditSessionTest {

    private static GerberImage minimalImage() {
        return new GerberParser().parse(List.of(
                "%FSLAX23Y23*%",
                "%MOIN*%",
                "%ADD10C,0.010*%",
                "D10*",
                "X001000Y001000D03*",
                "M02*"
        ));
    }

    @Test
    void nextEditedNameAppendsEditSuffixTheFirstTime() {
        assertEquals("board_edit", GerberEditSession.nextEditedName("board"));
    }

    @Test
    void nextEditedNameIncrementsTrailingDigit() {
        assertEquals("board_edit_1", GerberEditSession.nextEditedName("board_edit"));
        assertEquals("board_edit_2", GerberEditSession.nextEditedName("board_edit_1"));
        assertEquals("board_edit_10", GerberEditSession.nextEditedName("board_edit_9"));
    }

    @Test
    void applyProducesNextNameWithWorkingImage() {
        GerberImage image = minimalImage();
        GerberEditSession session = new GerberEditSession("board", image);

        GerberEditSession.ApplyResult result = session.apply();

        assertEquals("board_edit", result.name());
        assertSame(image, result.image());
    }

    @Test
    void sessionIsNotDirtyWithoutEditOperations() {
        GerberEditSession session = new GerberEditSession("board", minimalImage());
        assertFalse(session.isDirty());
    }
}
