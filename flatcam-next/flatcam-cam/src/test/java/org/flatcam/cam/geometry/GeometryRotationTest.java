package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryRotationTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static Geometry line(double x) { return F.createLineString(new Coordinate[]{new Coordinate(x,0,7),new Coordinate(x,2,8)}); }

    @Test void clockwiseRotationKeepsOriginalUntilAppliedAndPreservesIdsToolsSelectionAndHistory() {
        for(double u:new double[]{1,1/25.4}) {
            Geometry a=F.toGeometry(new Envelope(2*u,4*u,0,2*u)),b=line(10*u),untouched=line(20*u);
            var s=new GeometryEditSession(F.buildGeometry(List.of(a,b,untouched)),
                    List.of(new ToolGeometry(.8*u,a),new ToolGeometry(1.2*u,F.buildGeometry(List.of(b,untouched)))));
            s.selectIndices(List.of(1,0)); var ids=s.shapeRows().stream().map(GeometryEditSession.ShapeRow::id).toList();
            Coordinate pivot=new Coordinate(u,0); var request=s.prepareRotation(90,pivot);
            pivot.x=999; request.pivot().x=1000; assertEquals(u,request.pivot().x);
            var result=request.execute(CancellationToken.none(),ProgressCallback.none());
            assertFalse(s.isDirty()); assertSame(a,s.resultGeometry().getGeometryN(0));
            assertTrue(s.applyRotation(result)); assertEquals(ids,s.shapeRows().stream().map(GeometryEditSession.ShapeRow::id).toList());
            assertEquals(List.of(1,0),s.selectedIndexOrder()); assertSame(untouched,s.resultGeometry().getGeometryN(2));
            Geometry first=s.resultGeometry().getGeometryN(0); assertEquals(-u,first.getEnvelopeInternal().getMaxY(),1e-12);
            assertEquals(-3*u,first.getEnvelopeInternal().getMinY(),1e-12); // x=4 rotates clockwise about x=1.
            assertEquals(7,s.resultGeometry().getGeometryN(1).getCoordinate().getZ());
            assertEquals(.8*u,s.resultTools().get(0).toolDiameter()); assertEquals(1.2*u,s.resultTools().get(1).toolDiameter());
            assertTrue(s.undo()); assertFalse(s.isDirty()); assertTrue(s.redo()); assertTrue(s.isDirty());
        }
    }

    @Test void negativeAngleIsCounterclockwiseAndInvalidInputsNeverChangeHistory() {
        var s=new GeometryEditSession(line(2),List.of()); s.selectIndices(List.of(0));
        for(double angle:new double[]{0,360,-720,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->s.prepareRotation(angle,new Coordinate(0,0)));
        assertThrows(IllegalArgumentException.class,()->s.prepareRotation(90,new Coordinate(Double.NaN,0)));
        assertFalse(s.canUndo());
        var r=s.prepareRotation(-90,new Coordinate(0,0)).execute(CancellationToken.none(),ProgressCallback.none());
        assertTrue(s.applyRotation(r)); assertEquals(new Coordinate(0,2),s.resultGeometry().getCoordinate());
    }

    @Test void staleResultsAreRejectedWhenSelectionOrDraftChanges() {
        var s=new GeometryEditSession(F.buildGeometry(List.of(line(2),line(5))),List.of()); s.selectIndices(List.of(0));
        var r=s.prepareRotation(90,new Coordinate(0,0)).execute(CancellationToken.none(),ProgressCallback.none());
        s.selectIndices(List.of(1)); assertFalse(s.applyRotation(r)); assertFalse(s.isDirty());
        s.selectIndices(List.of(0)); s.moveSelected(1,0); assertFalse(s.applyRotation(r));
        assertTrue(s.undo()); assertFalse(s.isDirty()); assertFalse(s.applyRotation(r));
    }

    @Test void largeSingleShapeCanCancelDuringCoordinateTraversalWithoutMutatingTheSource() {
        Coordinate[] points=new Coordinate[20000]; for(int i=0;i<points.length;i++) points[i]=new Coordinate(i,i%7);
        var source=F.createLineString(points); var s=new GeometryEditSession(source,List.of()); s.selectIndices(List.of(0));
        var request=s.prepareRotation(25,new Coordinate(0,0)); var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->request.execute(()->calls.incrementAndGet()>10,ProgressCallback.none()));
        assertFalse(s.isDirty()); assertSame(source,s.resultGeometry()); assertEquals(new Coordinate(19999,19999%7),source.getCoordinateN(19999));
        var overflowing=s.prepareRotation(180,new Coordinate(Double.MAX_VALUE,Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->overflowing.execute(CancellationToken.none(),ProgressCallback.none()));
        assertFalse(s.canUndo());
    }
}
