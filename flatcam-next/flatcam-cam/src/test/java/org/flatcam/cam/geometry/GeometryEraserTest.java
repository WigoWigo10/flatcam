package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryEraserTest {
    @Test void manyUnrelatedShapesKeepTheirIdsAndCancellationIsTransactional() {
        List<Geometry> shapes = new java.util.ArrayList<>();
        shapes.add(box(0,0,1,1));
        for (int i = 0; i < 10_000; i++) shapes.add(box(100+i*2,0,101+i*2,1));
        var session = new GeometryEditSession(F.buildGeometry(shapes),List.of());session.selectIndices(List.of(0));
        var request = session.prepareErase(mask(session),100,0);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(CancellationException.class, () -> request.execute(() -> checks.incrementAndGet() > 20,ProgressCallback.none()));
        assertFalse(session.isDirty()); assertFalse(session.canUndo());
        long lastId = session.shapeRows().getLast().id();
        var result = request.execute(CancellationToken.none(),ProgressCallback.none());
        assertEquals(List.of(1),result.replacedIndices());
        assertTrue(session.applyOperation(result));
        assertEquals(lastId,session.shapeRows().getLast().id());
        assertEquals(10_000,session.shapeCount());
    }
    private static final GeometryFactory F = new GeometryFactory();
    private static Geometry box(double x1,double y1,double x2,double y2) { return F.toGeometry(new Envelope(x1,x2,y1,y2)); }
    private static Geometry mask(GeometryEditSession session) {
        return session.prepareEraserMask().execute(CancellationToken.none(),ProgressCallback.none()).resultParts().getFirst().geometry();
    }
    @Test void cutsLinesAndPolygonsOfDifferentToolsAndPreservesOriginalTemplate() {
        Geometry template = box(0,0,2,2), target = box(10,0,14,4);
        Geometry line = F.createLineString(new Coordinate[]{new Coordinate(9,1),new Coordinate(15,1)});
        var session = new GeometryEditSession(F.buildGeometry(List.of(template,target,line)),
                List.of(new ToolGeometry(0.2,F.buildGeometry(List.of(template,target))),new ToolGeometry(1,line,ToolProfile.C2)));
        session.selectIndices(List.of(0));
        var erased = session.prepareErase(mask(session),10,0).execute(CancellationToken.none(),ProgressCallback.none());
        assertEquals(List.of(1,2),erased.replacedIndices());
        assertTrue(session.applyOperation(erased));
        assertEquals(16,session.resultGeometry().getArea(),1e-6); // 4 template + 12 target
        assertEquals(4,session.resultTools().get(1).geometry().getLength(),1e-6);
        assertEquals(ToolProfile.C2,session.resultTools().get(1).toolProfile());
        assertTrue(session.resultGeometry().covers(template));
        assertEquals(16,target.getArea(),1e-6,"source is not mutated");
        assertTrue(session.undo()); assertEquals(20,session.resultGeometry().getArea(),1e-6);
        assertTrue(session.redo()); assertEquals(16,session.resultGeometry().getArea(),1e-6);
    }
    @Test void closedLineBecomesFilledMaskAndCompleteErasureIsAllowed() {
        var outline = box(0,0,2,2).getBoundary();
        var session = new GeometryEditSession(outline,List.of()); session.selectIndices(List.of(0));
        Geometry mask = mask(session); assertEquals(4,mask.getArea(),1e-6);
        assertTrue(session.applyOperation(session.prepareErase(mask,0,0).execute(CancellationToken.none(),ProgressCallback.none())));
        assertEquals(0,session.shapeCount()); assertTrue(session.undo()); assertEquals(1,session.shapeCount());
    }
    @Test void polygonHolesAreFilledInMaskLikePython() {
        Geometry donut = box(0,0,10,10).difference(box(3,3,7,7));
        var session = new GeometryEditSession(donut,List.of());session.selectIndices(List.of(0));
        assertEquals(100,mask(session).getArea(),1e-6);
    }
    @Test void cancellationAndStaleResultsDoNotMutateDraftAndNoHitHasNoUndo() {
        var session = new GeometryEditSession(box(0,0,2,2),List.of());session.selectIndices(List.of(0));
        Geometry mask = mask(session);
        var noHit = session.prepareErase(mask,20,0).execute(CancellationToken.none(),ProgressCallback.none());
        assertFalse(session.applyOperation(noHit)); assertFalse(session.canUndo());
        var request = session.prepareErase(mask,0,0);
        assertThrows(CancellationException.class, () -> request.execute(() -> true,ProgressCallback.none()));
        assertFalse(session.isDirty());
        var stale = request.execute(CancellationToken.none(),ProgressCallback.none());
        session.moveSelected(1,0); assertFalse(session.applyOperation(stale));
        session.clearSelection(); assertThrows(IllegalArgumentException.class,session::prepareEraserMask);
    }
}
