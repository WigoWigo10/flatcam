package org.flatcam.cam.gerber.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;

class GerberEditSessionTest {

    /**
     * Shape indices: 0 = D10 flash at (1,1); 1 = D10 flash at (3,1); 2 = D11 stroke
     * (0,5)-(4,5); 3 = region square (6,0)-(8,2); 4 = clear D10 flash at (3,1).
     */
    private static GerberImage board() {
        return new GerberParser().parse(List.of(
                "%FSLAX23Y23*%",
                "%MOIN*%",
                "%ADD10C,0.100*%",
                "%ADD11C,0.050*%",
                "D10*",
                "X1000Y1000D03*",
                "X3000Y1000D03*",
                "D11*",
                "X0Y5000D02*",
                "G01X4000Y5000D01*",
                "G36*",
                "X6000Y0D02*",
                "G01X8000Y0D01*",
                "X8000Y2000D01*",
                "X6000Y2000D01*",
                "X6000Y0D01*",
                "G37*",
                "%LPC*%",
                "D10*",
                "X3000Y1000D03*",
                "M02*"
        ));
    }

    private static GerberEditSession session() {
        return new GerberEditSession("board", board());
    }

    @Test
    void parserKeepsEachShapeWithApertureAndPolarity() {
        List<GerberShape> shapes = board().shapes();

        assertEquals(5, shapes.size());
        assertEquals(List.of("10", "10", "11", GerberShape.REGION_APERTURE, "10"),
                shapes.stream().map(GerberShape::apertureCode).toList());
        assertEquals(List.of(false, false, false, false, true),
                shapes.stream().map(GerberShape::clear).toList());
    }

    @Test
    void clickSelectsTheShapeUnderThePoint() {
        GerberEditSession session = session();

        session.clickSelect(1, 1, false);

        assertEquals(Set.of(0), session.selectedIndices());
        assertEquals(Set.of("10"), session.selectedApertures());
    }

    @Test
    void plainClickOnEmptySpaceClearsSelection() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);

        session.clickSelect(10, 10, false);

        assertTrue(session.selectedIndices().isEmpty());
    }

    @Test
    void plainClickReplacesPreviousSelection() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);

        session.clickSelect(7, 1, false);

        assertEquals(Set.of(3), session.selectedIndices());
    }

    @Test
    void additiveClickTogglesShapes() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);

        session.clickSelect(7, 1, true);
        assertEquals(Set.of(0, 3), session.selectedIndices());

        session.clickSelect(1, 1, true);
        assertEquals(Set.of(3), session.selectedIndices());
    }

    @Test
    void clearPolarityShapesAreNeverSelected() {
        GerberEditSession session = session();

        session.clickSelect(3, 1, false);

        assertEquals(Set.of(1), session.selectedIndices());
    }

    @Test
    void leftToRightBoxSelectsOnlyEnclosedShapes() {
        GerberEditSession session = session();

        session.boxSelect(0, 0, 4, 2, false);
        assertEquals(Set.of(0, 1), session.selectedIndices());

        session.boxSelect(-1, 4.9, 2, 6, false);
        assertTrue(session.selectedIndices().isEmpty(), "stroke only partly inside must not be enclosed");
    }

    @Test
    void rightToLeftBoxSelectsTouchedShapes() {
        GerberEditSession session = session();

        session.boxSelect(2, 6, -1, 4.9, false);

        assertEquals(Set.of(2), session.selectedIndices());
    }

    @Test
    void additiveBoxTogglesShapes() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);

        session.boxSelect(0, 0, 9, 3, true);

        assertEquals(Set.of(1, 3), session.selectedIndices());
    }

    @Test
    void imageWithoutShapesFallsBackToApertureAggregateParts() {
        GerberImage parsed = board();
        GerberImage restored = GerberImage.of(parsed.units(), parsed.apertures(), parsed.solidGeometry(),
                parsed.followGeometry(), parsed.apertureGeometry());

        GerberEditSession session = new GerberEditSession("board", restored);

        assertTrue(session.shapesApproximated());
        assertEquals(3, session.shapes().size(), "two D10 parts + one D11 stroke; regions are not in any aggregate");
        session.clickSelect(1, 1, false);
        assertEquals(Set.of("10"), session.selectedApertures());
    }

    @Test
    void transformedImageMovesItsShapes() {
        GerberImage moved = board().transformed(new TransformOp.Offset(10, 0));

        GerberEditSession session = new GerberEditSession("board", moved);
        assertFalse(session.shapesApproximated());
        session.clickSelect(11, 1, false);

        assertEquals(Set.of(0), session.selectedIndices());
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
        GerberImage image = board();
        GerberEditSession session = new GerberEditSession("board", image);

        GerberEditSession.ApplyResult result = session.apply();

        assertEquals("board_edit", result.name());
        assertSame(image, result.image());
    }

    @Test
    void sessionIsNotDirtyWithoutEditOperations() {
        assertFalse(session().isDirty());
    }
}
