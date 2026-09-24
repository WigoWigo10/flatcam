package org.flatcam.cam.gerber.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class GerberEditSessionTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

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

    @Test
    void deleteUndoRedoAndApplyRebuildCopperWithoutMutatingSource() {
        GerberImage original = board();
        GerberEditSession session = new GerberEditSession("board", original);
        session.clickSelect(1, 1, false);

        assertTrue(session.deleteSelected());
        assertEquals(4, session.shapes().size());
        assertTrue(session.isDirty());
        assertFalse(session.apply().image().solidGeometry().covers(point(1, 1)));
        assertTrue(original.solidGeometry().covers(point(1, 1)));

        assertTrue(session.undo());
        assertFalse(session.isDirty());
        assertEquals(Set.of(0), session.selectedIndices());
        assertSame(original, session.apply().image());
        assertTrue(session.redo());
        assertTrue(session.isDirty());
        assertEquals(4, session.apply().image().shapes().size());
    }

    @Test
    void moveUpdatesSolidAndFollowGeometryAndClearsRedoAfterNewEdit() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);
        assertTrue(session.moveSelected(9, 0));
        GerberImage moved = session.apply().image();

        assertFalse(moved.solidGeometry().covers(point(1, 1)));
        assertTrue(moved.solidGeometry().covers(point(10, 1)));
        assertTrue(moved.followGeometry().covers(point(10, 1)));
        assertEquals(Set.of(0), session.selectedIndices());

        assertTrue(session.undo());
        assertTrue(session.canRedo());
        assertTrue(session.moveSelected(8, 0));
        assertFalse(session.canRedo());
        assertThrows(IllegalArgumentException.class, () -> session.moveSelected(Double.NaN, 0));
    }

    @Test
    void copyKeepsOriginalAndSelectsNewShape() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);
        assertTrue(session.copySelected(9, 0));

        assertEquals(6, session.shapes().size());
        assertEquals(Set.of(1), session.selectedIndices());
        GerberImage copied = session.apply().image();
        assertTrue(copied.solidGeometry().covers(point(1, 1)));
        assertTrue(copied.solidGeometry().covers(point(10, 1)));
        assertEquals(6, copied.shapes().size());
    }

    @Test
    void movedDarkShapeStillRespectsLaterClearPolarity() {
        GerberEditSession session = session();
        session.clickSelect(3, 1, false);
        assertEquals(Set.of(1), session.selectedIndices());

        assertTrue(session.moveSelected(2, 0));
        GerberImage result = session.apply().image();
        assertFalse(result.solidGeometry().covers(point(3, 1)));
        assertTrue(result.solidGeometry().covers(point(5, 1)));
        assertTrue(result.shapes().get(4).clear());
    }

    @Test
    void legacyAggregateProjectIsReadOnlyToAvoidLosingUnrepresentedRegions() {
        GerberImage parsed = board();
        GerberImage oldProject = GerberImage.of(parsed.units(), parsed.apertures(), parsed.solidGeometry(),
                parsed.followGeometry(), parsed.apertureGeometry());
        GerberEditSession session = new GerberEditSession("old", oldProject);
        session.clickSelect(1, 1, false);

        assertTrue(session.shapesApproximated());
        assertThrows(IllegalStateException.class, session::deleteSelected);
        assertSame(oldProject, session.apply().image());
    }

    @Test
    void rebuildingUnchangedShapesPreservesSolidAndReportsMonotonicProgress() {
        GerberImage original = board();
        List<Double> fractions = new ArrayList<>();
        GerberImage rebuilt = original.withEditedShapes(original.shapes(), () -> false, fractions::add);

        assertTrue(original.solidGeometry().equalsTopo(rebuilt.solidGeometry()));
        assertEquals(0.0, fractions.get(0));
        assertEquals(1.0, fractions.get(fractions.size() - 1));
        for (int i = 1; i < fractions.size(); i++) {
            assertTrue(fractions.get(i) >= fractions.get(i - 1), "progress cannot move backwards");
        }
        assertThrows(CancellationException.class,
                () -> original.withEditedShapes(original.shapes(), () -> true, ignored -> {}));
    }

    private static org.locationtech.jts.geom.Point point(double x, double y) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
    }
}
