package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;

class GeometryEditSessionTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Geometry line(double x) {
        return FACTORY.createLineString(new Coordinate[]{new Coordinate(x, 0), new Coordinate(x, 10)});
    }

    private static Geometry rectangle(double x1, double y1, double x2, double y2) {
        return FACTORY.toGeometry(new Envelope(x1, x2, y1, y2));
    }

    private static GeometryEditSession overlappingRectangles() {
        Geometry first = rectangle(0, 0, 2, 2);
        Geometry second = rectangle(1, 1, 3, 3);
        GeometryEditSession session = new GeometryEditSession(FACTORY.buildGeometry(List.of(first, second)),
                List.of());
        session.clickSelect(0.5, 0.5, 0, false);
        session.clickSelect(2.5, 2.5, 0, true);
        return session;
    }

    private static void executeAndApply(GeometryEditSession session, GeometryEditSession.Operation operation,
                                        double distance) {
        var request = session.prepareOperation(operation, distance);
        assertTrue(session.applyOperation(request.execute(CancellationToken.none(), ProgressCallback.none())));
    }

    @Test
    void booleanOperationsUseSelectionOrderAndSupportUndoRedo() {
        GeometryEditSession union = overlappingRectangles();
        executeAndApply(union, GeometryEditSession.Operation.UNION, 0);
        assertEquals(1, union.shapeCount());
        assertEquals(7, union.resultGeometry().getArea(), 1e-9);
        assertTrue(union.undo());
        assertEquals(2, union.shapeCount());
        assertTrue(union.redo());
        assertEquals(1, union.shapeCount());

        GeometryEditSession intersection = overlappingRectangles();
        executeAndApply(intersection, GeometryEditSession.Operation.INTERSECTION, 0);
        assertEquals(1, intersection.resultGeometry().getArea(), 1e-9);

        GeometryEditSession subtraction = overlappingRectangles();
        executeAndApply(subtraction, GeometryEditSession.Operation.SUBTRACT, 0);
        assertEquals(3, subtraction.resultGeometry().getArea(), 1e-9);
        assertTrue(subtraction.resultGeometry().covers(FACTORY.createPoint(new Coordinate(0.5, 0.5))));
        assertFalse(subtraction.resultGeometry().covers(FACTORY.createPoint(new Coordinate(2.5, 2.5))));
    }

    @Test
    void tableRowsHaveStableIdsAndTableSelectionControlsSubtractionOrder() {
        GeometryEditSession session = overlappingRectangles();
        List<GeometryEditSession.ShapeRow> originalRows = session.shapeRows();
        assertEquals(List.of("Polygon", "Polygon"), originalRows.stream()
                .map(GeometryEditSession.ShapeRow::type).toList());
        assertEquals(List.of("Geo Elem", "Geo Elem"), originalRows.stream()
                .map(GeometryEditSession.ShapeRow::name).toList());
        assertEquals(2, originalRows.stream().map(GeometryEditSession.ShapeRow::id).distinct().count());
        session.selectIndices(List.of(1, 0));
        assertEquals(List.of(1, 0), session.selectedIndexOrder());
        executeAndApply(session, GeometryEditSession.Operation.SUBTRACT, 0);
        assertTrue(session.resultGeometry().covers(FACTORY.createPoint(new Coordinate(2.5, 2.5))));
        assertFalse(session.resultGeometry().covers(FACTORY.createPoint(new Coordinate(0.5, 0.5))));
        assertTrue(session.undo());
        assertEquals(originalRows, session.shapeRows());
        assertEquals(List.of(1, 0), session.selectedIndexOrder());
        assertTrue(session.moveSelected(1, 0));
        assertEquals(originalRows.stream().map(GeometryEditSession.ShapeRow::id).toList(),
                session.shapeRows().stream().map(GeometryEditSession.ShapeRow::id).toList());
        session.selectIndices(List.of(0));
        assertTrue(session.deleteSelected());
        assertEquals(1, session.shapeRows().size());
        assertTrue(session.undo());
        assertEquals(originalRows, session.shapeRows());
    }

    @Test
    void bufferAddsResultsWithoutRemovingOriginalAndPreservesTools() {
        Geometry first = rectangle(0, 0, 2, 2);
        Geometry second = rectangle(10, 0, 12, 2);
        GeometryEditSession session = new GeometryEditSession(FACTORY.buildGeometry(List.of(first, second)),
                List.of(new ToolGeometry(0.8, first), new ToolGeometry(1.2, second)));
        session.clickSelect(1, 1, 0, false);
        session.clickSelect(11, 1, 0, true);
        executeAndApply(session, GeometryEditSession.Operation.BUFFER_FULL, 0.2);
        assertEquals(4, session.shapeCount());
        assertEquals(2, session.resultTools().get(0).geometry().getNumGeometries());
        assertEquals(2, session.resultTools().get(1).geometry().getNumGeometries());
        assertTrue(session.undo());
        assertEquals(2, session.shapeCount());

        session.clearSelection();
        session.clickSelect(1, 1, 0, false);
        executeAndApply(session, GeometryEditSession.Operation.BUFFER_INTERIOR, 0.2);
        assertEquals(2, session.resultTools().get(0).geometry().getNumGeometries());
        assertEquals(2.56, session.resultGeometry().getGeometryN(2).getArea(), 1e-9);
    }

    @Test
    void rejectsCrossToolBooleanEmptyIntersectionInvalidBufferAndStaleResult() {
        Geometry first = rectangle(0, 0, 2, 2);
        Geometry second = rectangle(5, 0, 7, 2);
        GeometryEditSession tools = new GeometryEditSession(FACTORY.buildGeometry(List.of(first, second)),
                List.of(new ToolGeometry(0.8, first), new ToolGeometry(1.2, second)));
        tools.clickSelect(1, 1, 0, false);
        tools.clickSelect(6, 1, 0, true);
        assertThrows(IllegalArgumentException.class,
                () -> tools.prepareOperation(GeometryEditSession.Operation.UNION, 0));
        assertFalse(tools.isDirty());

        GeometryEditSession session = new GeometryEditSession(FACTORY.buildGeometry(List.of(first, second)),
                List.of());
        session.clickSelect(1, 1, 0, false);
        session.clickSelect(6, 1, 0, true);
        assertThrows(IllegalArgumentException.class, () -> session.prepareOperation(
                GeometryEditSession.Operation.BUFFER_FULL, Double.NaN));
        var request = session.prepareOperation(GeometryEditSession.Operation.INTERSECTION, 0);
        assertThrows(IllegalArgumentException.class,
                () -> request.execute(CancellationToken.none(), ProgressCallback.none()));
        assertEquals(2, session.shapeCount());
        assertFalse(session.isDirty());

        var union = session.prepareOperation(GeometryEditSession.Operation.UNION, 0);
        var result = union.execute(CancellationToken.none(), ProgressCallback.none());
        session.clickSelect(1, 1, 0, false);
        assertFalse(session.applyOperation(result));
        assertEquals(2, session.shapeCount());
        session.clickSelect(6, 1, 0, true);
        session.moveSelected(1, 0);
        assertFalse(session.applyOperation(result));
    }

    @Test
    void explodeCreatesEditableEdgesAndRejectsNonPolygons() {
        GeometryEditSession session = new GeometryEditSession(rectangle(0, 0, 2, 2), List.of());
        session.clickSelect(1, 1, 0, false);
        assertEquals(4, session.explodeSelected());
        assertEquals(4, session.shapeCount());
        assertEquals(8, session.resultGeometry().getLength(), 1e-9);
        assertTrue(session.undo());
        assertEquals(1, session.shapeCount());
        assertTrue(session.redo());
        session.clickSelect(1, 0, 0, false);
        assertThrows(IllegalArgumentException.class, session::explodeSelected);
    }

    @Test
    void explodeIncludesHoleEdgesAndRejectsOversizedSelectionWithoutMutation() {
        LinearRing outside = FACTORY.createLinearRing(new Coordinate[]{new Coordinate(0, 0),
                new Coordinate(10, 0), new Coordinate(10, 10), new Coordinate(0, 10), new Coordinate(0, 0)});
        LinearRing hole = FACTORY.createLinearRing(new Coordinate[]{new Coordinate(3, 3),
                new Coordinate(3, 7), new Coordinate(7, 7), new Coordinate(7, 3), new Coordinate(3, 3)});
        GeometryEditSession withHole = new GeometryEditSession(
                FACTORY.createPolygon(outside, new LinearRing[]{hole}), List.of());
        withHole.clickSelect(1, 1, 0, false);
        assertEquals(8, withHole.explodeSelected());
        assertEquals(8, withHole.shapeCount());

        Coordinate[] many = new Coordinate[10_002];
        for (int i = 0; i < many.length - 1; i++) {
            double angle = Math.PI * 2 * i / (many.length - 1);
            many[i] = new Coordinate(Math.cos(angle), Math.sin(angle));
        }
        many[many.length - 1] = new Coordinate(many[0]);
        GeometryEditSession oversized = new GeometryEditSession(FACTORY.createPolygon(many), List.of());
        oversized.clickSelect(0, 0, 0, false);
        assertThrows(IllegalArgumentException.class, oversized::explodeSelected);
        assertFalse(oversized.isDirty());
        assertEquals(1, oversized.shapeCount());
    }

    @Test
    void cancelledBackgroundOperationDoesNotMutateDraft() {
        GeometryEditSession session = overlappingRectangles();
        var request = session.prepareOperation(GeometryEditSession.Operation.UNION, 0);
        assertThrows(CancellationException.class, () -> request.execute(() -> true, ProgressCallback.none()));
        assertFalse(session.isDirty());
        assertEquals(2, session.shapeCount());
    }

    @Test
    void selectsThinPathsDeletesAndRestoresWithUndoRedo() {
        Geometry source = FACTORY.buildGeometry(List.of(line(0), line(10), line(20)));
        GeometryEditSession session = new GeometryEditSession(source, List.of());

        session.clickSelect(0.03, 5, 0.05, false);
        assertEquals(1, session.selectedCount());
        session.clickSelect(10.02, 5, 0.05, true);
        assertEquals(2, session.selectedCount());
        assertTrue(session.deleteSelected());
        assertEquals(1, session.shapeCount());
        assertTrue(session.isDirty());
        assertTrue(session.resultGeometry().equalsTopo(line(20)));
        assertTrue(session.undo());
        assertFalse(session.isDirty());
        assertEquals(3, session.shapeCount());
        assertTrue(session.redo());
        assertEquals(1, session.shapeCount());
    }

    @Test
    void enclosingAndTouchingBoxesHaveDifferentResults() {
        GeometryEditSession session = new GeometryEditSession(
                FACTORY.buildGeometry(List.of(line(0), line(10), line(20))), List.of());

        session.boxSelect(5, -1, 15, 5, false);
        assertEquals(0, session.selectedCount());
        session.boxSelect(15, 5, 5, -1, false);
        assertEquals(1, session.selectedCount());
        session.boxSelect(21, 11, -1, -1, true);
        assertEquals(2, session.selectedCount(), "Control toggles the already selected middle line");
    }

    @Test
    void deletionPreservesPerToolAssignments() {
        Geometry first = line(0);
        Geometry second = line(10);
        List<ToolGeometry> tools = List.of(new ToolGeometry(0.8, first), new ToolGeometry(1.2, second));
        GeometryEditSession session = new GeometryEditSession(FACTORY.buildGeometry(List.of(first, second)), tools);

        session.clickSelect(0, 5, 0.01, false);
        assertTrue(session.deleteSelected());
        assertEquals(2, session.resultTools().size());
        assertEquals(0.8, session.resultTools().get(0).toolDiameter());
        assertTrue(session.resultTools().get(0).geometry().isEmpty());
        assertTrue(session.resultTools().get(1).geometry().equalsTopo(second));
        assertTrue(session.resultGeometry().equalsTopo(second));
    }

    @Test
    void deletingEverythingProducesEmptyGeometry() {
        GeometryEditSession session = new GeometryEditSession(line(0), List.of());
        session.clickSelect(0, 5, 0, false);
        assertTrue(session.deleteSelected());
        assertTrue(session.resultGeometry().isEmpty());
        assertFalse(session.deleteSelected());
    }

    @Test
    void movesAndCopiesSelectedPathsWithUndoRedoAndFreshHitIndex() {
        GeometryEditSession session = new GeometryEditSession(line(0), List.of());
        session.clickSelect(0, 5, 0.01, false);
        assertTrue(session.moveSelected(5, 0));
        assertFalse(session.moveSelected(0, 0));
        session.clickSelect(0, 5, 0.01, false);
        assertEquals(0, session.selectedCount());
        session.clickSelect(5, 5, 0.01, false);
        assertEquals(1, session.selectedCount());
        assertTrue(session.copySelected(10, 0));
        assertEquals(2, session.shapeCount());
        assertEquals(1, session.selectedCount());
        assertTrue(session.undo());
        assertEquals(1, session.shapeCount());
        assertTrue(session.undo());
        assertFalse(session.isDirty());
        assertTrue(session.redo());
        assertTrue(session.redo());
        assertEquals(2, session.shapeCount());
    }

    @Test
    void createsPathPolygonRectangleAndCircleAsEditableShapes() {
        GeometryEditSession session = new GeometryEditSession(FACTORY.createGeometryCollection(), List.of());
        session.addPath(List.of(new Coordinate(0, 0), new Coordinate(0, 5), new Coordinate(5, 5)), -1);
        session.addPolygon(List.of(new Coordinate(10, 0), new Coordinate(12, 0),
                new Coordinate(12, 2), new Coordinate(10, 2)), -1);
        session.addRectangle(20, 0, 23, 3, -1);
        session.addCircle(30, 0, 31, 0, -1);

        assertEquals(4, session.shapeCount());
        assertEquals(10, session.resultGeometry().getGeometryN(0).getLength(), 1e-9);
        assertEquals(4, session.resultGeometry().getGeometryN(1).getArea(), 1e-9);
        assertEquals(9, session.resultGeometry().getGeometryN(2).getArea(), 1e-9);
        assertEquals(Math.PI, session.resultGeometry().getGeometryN(3).getArea(), 0.01);
        session.clickSelect(22, 1, 0.01, false);
        assertEquals(1, session.selectedCount());
        assertTrue(session.deleteSelected());
        assertEquals(3, session.shapeCount());
    }

    @Test
    void rejectsDegenerateDrawingAndWrongToolWithoutChangingSession() {
        GeometryEditSession session = new GeometryEditSession(FACTORY.createGeometryCollection(), List.of());
        assertThrows(IllegalArgumentException.class, () -> session.addPath(
                List.of(new Coordinate(0, 0), new Coordinate(0, 0)), -1));
        assertThrows(IllegalArgumentException.class, () -> session.addRectangle(0, 0, 0, 2, -1));
        assertThrows(IllegalArgumentException.class, () -> session.addCircle(0, 0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> session.addPolygon(
                List.of(new Coordinate(0, 0), new Coordinate(2, 2),
                        new Coordinate(0, 2), new Coordinate(2, 0)), -1));
        assertThrows(IllegalArgumentException.class, () -> session.addRectangle(0, 0, 2, 2, 0));
        assertEquals(0, session.shapeCount());
        assertFalse(session.isDirty());
    }

    @Test
    void newShapeAndCopyStayWithTheirChosenTool() {
        Geometry first = line(0);
        Geometry second = line(10);
        List<ToolGeometry> tools = List.of(new ToolGeometry(0.8, first), new ToolGeometry(1.2, second));
        GeometryEditSession session = new GeometryEditSession(FACTORY.buildGeometry(List.of(first, second)), tools);
        session.addPath(List.of(new Coordinate(20, 0), new Coordinate(20, 10)), 1);
        session.clickSelect(20, 5, 0.01, false);
        assertTrue(session.copySelected(10, 0));
        assertEquals(1, session.resultTools().get(0).geometry().getNumGeometries());
        assertEquals(3, session.resultTools().get(1).geometry().getNumGeometries());
        assertTrue(session.resultTools().get(1).geometry().getEnvelopeInternal().contains(30, 5));
    }

    @Test
    void largeCollectionUsesSameBaseGeometryAcrossSelectionChanges() {
        List<Geometry> paths = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            paths.add(line(i * 2));
        }
        Geometry source = FACTORY.buildGeometry(paths);
        GeometryEditSession session = new GeometryEditSession(source, List.of());
        for (int i = 0; i < 100; i++) {
            session.clickSelect(i * 2, 5, 0.01, false);
            assertEquals(1, session.selectedCount());
            assertSame(source, session.resultGeometry());
        }
        session.boxSelect(-1, -1, 40_001, 11, false);
        assertEquals(20_000, session.selectedCount());
        assertEquals(0, session.selectedBounds().getEnvelopeInternal().getMinX());
        assertEquals(39_998, session.selectedBounds().getEnvelopeInternal().getMaxX());
    }
}
