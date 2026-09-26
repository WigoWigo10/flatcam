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
import org.flatcam.cam.gerber.ApertureKind;
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
    void circularPadCanBeAppliedUndoneAndRedone() {
        GerberEditSession session = session();
        int originalCount = session.shapes().size();

        assertTrue(session.addCircularPad("10", 10, 10));
        assertEquals(originalCount + 1, session.shapes().size());
        assertEquals(Set.of(originalCount), session.selectedIndices());
        GerberShape pad = session.shapes().get(originalCount);
        assertEquals("10", pad.apertureCode());
        assertTrue(pad.geometry().covers(point(10, 10)));
        assertEquals(0.1, pad.geometry().getEnvelopeInternal().getWidth(), 1e-9);
        assertTrue(pad.followGeometry().equalsExact(point(10, 10)));
        assertTrue(session.apply().image().solidGeometry().covers(point(10, 10)));

        assertTrue(session.undo());
        assertEquals(originalCount, session.shapes().size());
        assertFalse(session.apply().image().solidGeometry().covers(point(10, 10)));
        assertTrue(session.redo());
        assertTrue(session.apply().image().solidGeometry().covers(point(10, 10)));
    }

    @Test
    void circularPadRejectsUnknownApertureAndInvalidCoordinates() {
        GerberEditSession session = session();
        assertThrows(IllegalArgumentException.class, () -> session.addCircularPad("99", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> session.addCircularPad("10", Double.NaN, 1));
        assertFalse(session.isDirty());

        GerberImage rectangle = new GerberParser().parse(List.of(
                "%FSLAX23Y23*%", "%MOIN*%", "%ADD12R,1X2*%", "D12*", "X0Y0D03*", "M02*"));
        GerberEditSession rectangleSession = new GerberEditSession("rect", rectangle);
        assertThrows(IllegalArgumentException.class, () -> rectangleSession.addCircularPad("12", 1, 1));
        assertFalse(rectangleSession.isDirty());
    }

    @Test
    void straightTrackKeepsCenterlineAndParticipatesInUndoRedo() {
        GerberEditSession session = session();
        int originalCount = session.shapes().size();

        assertTrue(session.addTrack("11", 10, 10, 14, 10));
        GerberShape track = session.shapes().get(originalCount);
        assertEquals(Set.of(originalCount), session.selectedIndices());
        assertEquals("11", track.apertureCode());
        assertFalse(track.clear());
        assertEquals(0.05, track.geometry().getEnvelopeInternal().getHeight(), 1e-9);
        assertEquals(4.05, track.geometry().getEnvelopeInternal().getWidth(), 1e-9);
        assertEquals(2, track.followGeometry().getNumPoints());
        assertEquals(10, track.followGeometry().getCoordinates()[0].x, 1e-9);
        assertEquals(14, track.followGeometry().getCoordinates()[1].x, 1e-9);
        assertTrue(session.apply().image().solidGeometry().covers(point(12, 10)));

        assertTrue(session.undo());
        assertEquals(originalCount, session.shapes().size());
        assertFalse(session.apply().image().solidGeometry().covers(point(12, 10)));
        assertTrue(session.redo());
        assertTrue(session.apply().image().solidGeometry().covers(point(12, 10)));
    }

    @Test
    void polylineTrackIsOneEditableShapeAndOneHistoryStep() {
        GerberEditSession session = session();
        int originalCount = session.shapes().size();

        assertTrue(session.addTrack("11", List.of(
                new Coordinate(10, 10), new Coordinate(12, 10),
                new Coordinate(12, 12), new Coordinate(15, 12))));

        GerberShape track = session.shapes().get(originalCount);
        assertEquals(4, track.followGeometry().getNumPoints());
        assertEquals(7.0, track.followGeometry().getLength(), 1e-9);
        assertTrue(track.geometry().covers(point(12, 11)));
        assertEquals(Set.of(originalCount), session.selectedIndices());

        assertTrue(session.undo());
        assertEquals(originalCount, session.shapes().size());
        assertFalse(session.canUndo());
        assertTrue(session.redo());
        assertEquals(4, session.shapes().get(originalCount).followGeometry().getNumPoints());
    }

    @Test
    void regionCreatesFilledCopperAndRoundTripsThroughHistory() {
        GerberEditSession session = session();
        int originalCount = session.shapes().size();
        assertTrue(session.addRegion(List.of(new Coordinate(20, 20), new Coordinate(24, 20),
                new Coordinate(24, 23), new Coordinate(20, 23))));
        GerberShape region = session.shapes().get(originalCount);
        assertEquals(GerberShape.REGION_APERTURE, region.apertureCode());
        assertEquals(12, region.geometry().getArea(), 1e-9);
        assertTrue(((org.locationtech.jts.geom.LineString) region.followGeometry()).isClosed());
        assertTrue(session.apply().image().solidGeometry().covers(point(22, 21)));
        assertTrue(session.undo());
        assertFalse(session.apply().image().solidGeometry().covers(point(22, 21)));
        assertTrue(session.redo());
        assertTrue(session.apply().image().solidGeometry().covers(point(22, 21)));
    }

    @Test
    void regionRejectsDegenerateOrCrossedRingsWithoutChangingHistory() {
        GerberEditSession session = session();
        assertFalse(session.addRegion(List.of(new Coordinate(0, 0), new Coordinate(1, 0))));
        assertThrows(IllegalArgumentException.class, () -> session.addRegion(List.of(
                new Coordinate(0, 0), new Coordinate(2, 2), new Coordinate(0, 2), new Coordinate(2, 0))));
        assertThrows(IllegalArgumentException.class, () -> session.addRegion(List.of(
                new Coordinate(0, 0), new Coordinate(Double.NaN, 1), new Coordinate(1, 1))));
        assertFalse(session.isDirty());
    }

    @Test
    void polygonAperturePadsAndApertureEditsAreUndoable() {
        GerberEditSession session = session();
        String code = session.addPolygonAperture(2, 6, 30);
        assertTrue(session.addPad(code, 20, 20));
        assertEquals(6, session.apertures().get(code).polygonVertices());
        assertTrue(session.renameAperture(code, "50"));
        assertEquals("50", session.shapes().get(session.shapes().size() - 1).apertureCode());
        assertThrows(IllegalArgumentException.class, () -> session.renameAperture("50", "10"));
        assertTrue(session.resizeAperture("50", org.flatcam.cam.gerber.Aperture.polygon(4, 6, 30)));
        assertTrue(session.shapes().get(session.shapes().size() - 1).geometry().getArea() > 8);
        assertTrue(session.deleteAperture("50"));
        assertFalse(session.apertures().containsKey("50"));
        assertTrue(session.undo());
        assertTrue(session.apertures().containsKey("50"));
        assertTrue(session.undo());
        assertEquals(2, session.apertures().get("50").width, 1e-9);
        assertTrue(session.undo());
        assertTrue(session.apertures().containsKey(code));
    }

    @Test
    void scaleAndBufferSelectedShapesPreserveUndoHistory() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);
        double before = session.shapes().get(0).geometry().getArea();
        assertTrue(session.scaleSelected(2));
        double scaled = session.shapes().get(0).geometry().getArea();
        assertEquals(before * 4, scaled, 1e-8);
        assertTrue(session.bufferSelected(0.1,
                org.locationtech.jts.operation.buffer.BufferParameters.JOIN_ROUND));
        assertTrue(session.shapes().get(0).geometry().getArea() > scaled);
        assertTrue(session.undo());
        assertEquals(scaled, session.shapes().get(0).geometry().getArea(), 1e-8);
        assertTrue(session.undo());
        assertEquals(before, session.shapes().get(0).geometry().getArea(), 1e-8);
        assertThrows(IllegalArgumentException.class, () -> session.scaleSelected(0));
        assertThrows(IllegalArgumentException.class, () -> session.bufferSelected(Double.NaN, 1));
    }

    @Test
    void padArraysAreAtomicAndValidateGeometry() {
        GerberEditSession session = session();
        int before = session.shapes().size();
        assertTrue(session.addLinearPadArray("10", 20, 20, 3, 2, 90));
        assertEquals(before + 3, session.shapes().size());
        assertTrue(session.shapes().get(before + 2).followGeometry().equalsExact(point(20, 24), 1e-9));
        assertEquals(Set.of(before, before + 1, before + 2), session.selectedIndices());
        assertTrue(session.undo());
        assertEquals(before, session.shapes().size());
        assertFalse(session.canUndo());
        assertTrue(session.addCircularPadArray("10", 30, 30, 4, 2, 0, 90));
        assertTrue(session.shapes().get(before).followGeometry().equalsExact(point(32, 30), 1e-9));
        assertTrue(session.shapes().get(before + 1).followGeometry().equalsExact(point(30, 32), 1e-9));
        assertThrows(IllegalArgumentException.class,
                () -> session.addLinearPadArray("10", 0, 0, 1001, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> session.addCircularPadArray("10", 0, 0, 3, 0, 0, 90));
        assertEquals(before + 4, session.shapes().size());
    }

    @Test
    void polygonizeReplacesSameApertureShapesAndPreservesHistory() {
        GerberEditSession session = session();
        session.boxSelect(0, 0, 4, 2, false);
        assertEquals(Set.of(0, 1), session.selectedIndices());
        assertTrue(session.polygonizeSelected());
        assertEquals(4, session.shapes().size());
        assertEquals(GerberShape.REGION_APERTURE, session.shapes().get(0).apertureCode());
        assertTrue(session.shapes().get(0).geometry().covers(point(1, 1)));
        assertTrue(session.shapes().get(0).geometry().covers(point(3, 1)));
        assertTrue(session.undo());
        assertEquals(5, session.shapes().size());
        session.boxSelect(0, 0, 9, 6, false);
        assertThrows(IllegalArgumentException.class, session::polygonizeSelected);
    }

    @Test
    void discAndSemiDiscCreateRegionGeometry() {
        GerberEditSession session = session();
        int before = session.shapes().size();
        assertTrue(session.addDisc(20, 20, 2));
        assertEquals(GerberShape.REGION_APERTURE, session.shapes().get(before).apertureCode());
        assertTrue(session.shapes().get(before).geometry().covers(point(20, 20)));
        assertEquals(Math.PI * 4, session.shapes().get(before).geometry().getArea(), 0.01);
        assertTrue(session.addSemiDisc(30, 30, 2, 0, 180));
        assertEquals(Math.PI * 2, session.shapes().get(before + 1).geometry().getArea(), 0.01);
        assertTrue(session.shapes().get(before + 1).geometry().covers(point(30, 31)));
        assertFalse(session.shapes().get(before + 1).geometry().covers(point(30, 29)));
        assertTrue(session.undo());
        assertEquals(before + 1, session.shapes().size());
        assertThrows(IllegalArgumentException.class, () -> session.addDisc(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> session.addSemiDisc(0, 0, 1, 0, 360));
    }

    @Test
    void markAreaSelectsByStrictThresholdWithoutEditing() {
        GerberEditSession session = session();
        assertEquals(1, session.selectAreaRange(3, 5));
        assertEquals(Set.of(3), session.selectedIndices());
        assertFalse(session.isDirty());
        assertThrows(IllegalArgumentException.class, () -> session.selectAreaRange(5, 3));
    }

    @Test
    void eraserSubtractsSelectedFootprintAtOffsetAndIsUndoable() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);
        assertTrue(session.eraseWithSelected(2, 0));
        assertTrue(session.shapes().get(0).geometry().covers(point(1, 1)));
        assertFalse(session.shapes().get(1).geometry().covers(point(3, 1)));
        assertTrue(session.undo());
        assertTrue(session.shapes().get(1).geometry().covers(point(3, 1)));
    }

    @Test
    void affineTransformsUseExplicitPivotAndPreserveFollowPaths() {
        GerberEditSession session = session();
        session.clickSelect(1, 1, false);
        assertTrue(session.transformSelected("rotate", 90, 0, 0));
        assertTrue(session.shapes().get(0).geometry().covers(point(-1, 1)));
        assertTrue(session.shapes().get(0).followGeometry().equalsExact(point(-1, 1), 1e-9));
        assertTrue(session.transformSelected("mirror_y", 0, 0, 0));
        assertTrue(session.shapes().get(0).geometry().covers(point(1, 1)));
        assertTrue(session.transformSelected("scale_x", 2, 0, 0));
        assertTrue(session.shapes().get(0).geometry().covers(point(2, 1)));
        assertTrue(session.transformSelected("skew_x", 45, 0, 0));
        assertTrue(session.shapes().get(0).geometry().covers(point(3, 1)));
        assertTrue(session.undo());
        assertTrue(session.shapes().get(0).geometry().covers(point(2, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> session.transformSelected("scale_y", 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> session.transformSelected("skew_x", 90, 0, 0));
    }

    @Test
    void straightTrackRejectsInvalidApertureAndCoordinatesWithoutHistory() {
        GerberEditSession session = session();
        assertFalse(session.addTrack("11", 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> session.addTrack("99", 1, 1, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> session.addTrack("11", Double.NaN, 1, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> session.addTrack("11", (List<Coordinate>) null));
        assertThrows(IllegalArgumentException.class,
                () -> session.addTrack("11", List.of(new Coordinate(1, 1), new Coordinate(Double.NaN, 2))));
        assertFalse(session.addTrack("11", List.of(
                new Coordinate(1, 1), new Coordinate(1, 1), new Coordinate(1, 1))));
        assertFalse(session.isDirty());
        assertFalse(session.canUndo());

        GerberImage rectangle = new GerberParser().parse(List.of(
                "%FSLAX23Y23*%", "%MOIN*%", "%ADD12R,1X2*%", "D12*", "X0Y0D03*", "M02*"));
        GerberEditSession rectangleSession = new GerberEditSession("rect", rectangle);
        assertThrows(IllegalArgumentException.class, () -> rectangleSession.addTrack("12", 1, 1, 2, 2));
        assertFalse(rectangleSession.isDirty());
    }

    @Test
    void newCircularApertureParticipatesInHistoryAndPadCreation() {
        GerberEditSession session = session();
        String code = session.addCircularAperture(0.25);
        assertEquals("12", code);
        assertEquals(0.25, session.apertures().get(code).width);
        assertTrue(session.isDirty());
        assertEquals(0.25, session.apply().image().apertures().get(code).width);

        assertTrue(session.addCircularPad(code, 10, 10));
        assertTrue(session.apply().image().solidGeometry().covers(point(10, 10)));
        assertTrue(session.undo()); // pad
        assertFalse(session.apply().image().solidGeometry().covers(point(10, 10)));
        assertTrue(session.undo()); // aperture
        assertFalse(session.apertures().containsKey(code));
        assertFalse(session.isDirty());
        assertTrue(session.redo());
        assertTrue(session.redo());
        assertTrue(session.apply().image().solidGeometry().covers(point(10, 10)));

        assertThrows(IllegalArgumentException.class, () -> session.addCircularAperture(0));
        assertThrows(IllegalArgumentException.class, () -> session.addCircularAperture(Double.POSITIVE_INFINITY));
    }

    @Test
    void rectangularAndObroundPadsUseExistingAperturesAndPreserveTheirCenters() {
        GerberImage image = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "%ADD11O,1X3*%",
                "D10*", "X0Y0D03*", "M02*"));
        GerberEditSession session = new GerberEditSession("board", image);
        int originalCount = session.shapes().size();

        assertTrue(session.addPad("10", 4, 5));
        assertTrue(session.addPad("11", 8, 9));
        assertEquals(originalCount + 2, session.shapes().size());
        assertEquals(2, session.shapes().get(originalCount).geometry().getEnvelopeInternal().getWidth(), 1e-9);
        assertEquals(1, session.shapes().get(originalCount).geometry().getEnvelopeInternal().getHeight(), 1e-9);
        assertEquals(1, session.shapes().get(originalCount + 1).geometry().getEnvelopeInternal().getWidth(), 1e-9);
        assertEquals(3, session.shapes().get(originalCount + 1).geometry().getEnvelopeInternal().getHeight(), 1e-9);
        assertTrue(session.shapes().get(originalCount).followGeometry().equalsExact(point(4, 5)));
        assertTrue(session.shapes().get(originalCount + 1).followGeometry().equalsExact(point(8, 9)));
        assertTrue(session.apply().image().solidGeometry().covers(point(4, 5)));
        assertTrue(session.apply().image().solidGeometry().covers(point(8, 9)));
        assertTrue(session.undo());
        assertEquals(originalCount + 1, session.shapes().size());
        assertTrue(session.redo());
        assertEquals(originalCount + 2, session.shapes().size());
    }

    @Test
    void newRectangularAndObroundAperturesAreUndoableAndRejectBadDimensions() {
        GerberEditSession session = session();
        String rectangle = session.addAperture(ApertureKind.RECTANGLE, 2, 1);
        String obround = session.addAperture(ApertureKind.OBROUND, 1, 3);
        assertEquals("12", rectangle);
        assertEquals("13", obround);
        assertEquals(ApertureKind.RECTANGLE, session.apertures().get(rectangle).kind);
        assertEquals(ApertureKind.OBROUND, session.apertures().get(obround).kind);
        assertTrue(session.addPad(rectangle, 10, 10));
        assertTrue(session.addPad(obround, 15, 15));
        assertEquals(2, session.apply().image().apertures().get(rectangle).width, 1e-9);
        assertEquals(3, session.apply().image().apertures().get(obround).height, 1e-9);
        assertTrue(session.undo()); // obround pad
        assertTrue(session.undo()); // rectangular pad
        assertTrue(session.undo()); // obround aperture
        assertFalse(session.apertures().containsKey(obround));
        assertTrue(session.redo());
        assertTrue(session.redo());
        assertTrue(session.redo());
        assertTrue(session.apply().image().solidGeometry().covers(point(15, 15)));

        assertThrows(IllegalArgumentException.class,
                () -> session.addAperture(ApertureKind.RECTANGLE, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> session.addAperture(ApertureKind.OBROUND, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> session.addAperture(ApertureKind.POLYGON, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> session.addPad("99", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> session.addPad(rectangle, Double.POSITIVE_INFINITY, 1));
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
        assertThrows(IllegalStateException.class, () -> session.addCircularAperture(0.2));
        assertThrows(IllegalStateException.class, () -> session.addCircularPad("10", 2, 2));
        assertThrows(IllegalStateException.class,
                () -> session.addAperture(ApertureKind.RECTANGLE, 2, 1));
        assertThrows(IllegalStateException.class, () -> session.addPad("10", 2, 2));
        assertThrows(IllegalStateException.class, () -> session.addTrack("10", 1, 1, 2, 2));
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
