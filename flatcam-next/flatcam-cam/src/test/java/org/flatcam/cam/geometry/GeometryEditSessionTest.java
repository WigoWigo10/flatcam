package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class GeometryEditSessionTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Geometry line(double x) {
        return FACTORY.createLineString(new Coordinate[]{new Coordinate(x, 0), new Coordinate(x, 10)});
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
