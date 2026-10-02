package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryPaintTest {
    final GeometryFactory f = new GeometryFactory();
    PaintParameters parameters(NccMethod method) {
        return new PaintParameters(List.of(1.0), .4, 0, method, true, true, NccOrder.NONE, false);
    }
    @Test void methodsAppendWithHolesToolAssociationAndSingleUndo() {
        Geometry polygon = f.toGeometry(new Envelope(0,10,0,10)).difference(f.toGeometry(new Envelope(4,6,4,6)));
        for (var method : NccMethod.values()) {
            var session = new GeometryEditSession(polygon, List.of(new ToolGeometry(1, polygon)));
            session.selectIndices(List.of(0));
            var result = session.preparePaint(parameters(method), "MM").execute(CancellationToken.none(), ProgressCallback.none());
            assertFalse(session.isDirty()); assertFalse(result.resultParts().isEmpty());
            for (var part : result.resultParts()) {
                assertEquals(0, part.toolIndex()); assertEquals(1, part.geometry().getDimension());
                assertTrue(polygon.covers(part.geometry()));
            }
            assertTrue(session.applyOperation(result)); assertTrue(session.shapeCount() > 1);
            assertEquals(1, session.resultTools().getFirst().toolDiameter());
            assertTrue(session.undo()); assertEquals(1, session.shapeCount()); assertFalse(session.isDirty());
            assertTrue(session.redo()); assertTrue(session.isDirty());
        }
    }
    @Test void closedRingAcceptedButOpenPathWrongDiameterAndStaleResultsRejected() {
        Polygon polygon = (Polygon) f.toGeometry(new Envelope(0,10,0,10));
        var session = new GeometryEditSession(polygon.getExteriorRing(), List.of());
        session.selectIndices(List.of(0));
        var result = session.preparePaint(parameters(NccMethod.LINES), "IN").execute(CancellationToken.none(), ProgressCallback.none());
        session.clearSelection(); assertFalse(session.applyOperation(result)); assertFalse(session.isDirty());
        var open = new GeometryEditSession(f.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,1)}), List.of());
        open.selectIndices(List.of(0)); assertThrows(IllegalArgumentException.class, () -> open.preparePaint(parameters(NccMethod.LINES), "MM"));
        var wrong = new GeometryEditSession(polygon, List.of(new ToolGeometry(.5, polygon)));
        wrong.selectIndices(List.of(0)); assertThrows(IllegalArgumentException.class, () -> wrong.preparePaint(parameters(NccMethod.LINES), "MM"));
    }
    @Test void cancellationDoesNotChangeDraft() {
        var session = new GeometryEditSession(f.toGeometry(new Envelope(0,10,0,10)), List.of());
        session.selectIndices(List.of(0));
        var request = session.preparePaint(parameters(NccMethod.STANDARD), "MM");
        assertThrows(java.util.concurrent.CancellationException.class, () -> request.execute(() -> true, ProgressCallback.none()));
        assertFalse(session.isDirty()); assertFalse(session.canUndo());
    }
}
