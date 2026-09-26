package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
