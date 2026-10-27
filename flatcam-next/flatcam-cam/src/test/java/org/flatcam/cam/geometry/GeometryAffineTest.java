package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryAffineTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static Geometry source(double unit) {
        return F.createLineString(new Coordinate[]{new CoordinateXYZM(2*unit,3*unit,7,9),new CoordinateXYZM(4*unit,5*unit,8,10)});
    }
    @Test void eachTransformUsesPivotAndObjectUnitsAndPreservesIdsToolsZMAndHistory() {
        for(double u:new double[]{1,1/25.4}) {
            var transforms=List.of(EditorTransform.scale(2,3),EditorTransform.scale(-1,1),
                    EditorTransform.scale(1,-1),EditorTransform.skew(45,0),EditorTransform.offset(5*u,-4*u));
            double[][] expected={{3,7},{0,3},{2,-1},{4,3},{7,-1}};
            for(int i=0;i<transforms.size();i++) {
                Geometry source=source(u),untouched=source(10*u);
                var s=new GeometryEditSession(F.buildGeometry(List.of(source,untouched)),List.of(new ToolGeometry(.8*u,source),new ToolGeometry(1.2*u,untouched)));
                s.selectIndices(List.of(0)); var ids=s.shapeRows().stream().map(GeometryEditSession.ShapeRow::id).toList();
                var pivot=new Coordinate(u,u); var request=s.prepareTransform(transforms.get(i),pivot);
                pivot.x=999; request.pivot().x=999; assertEquals(u,request.pivot().x);
                var result=request.execute(CancellationToken.none(),ProgressCallback.none());
                assertFalse(s.isDirty()); assertTrue(s.applyTransform(result));
                var coordinate=s.resultGeometry().getGeometryN(0).getCoordinate();
                assertEquals(expected[i][0]*u,coordinate.x,1e-12); assertEquals(expected[i][1]*u,coordinate.y,1e-12);
                assertEquals(7,coordinate.getZ()); assertEquals(9,coordinate.getM());
                assertEquals(ids,s.shapeRows().stream().map(GeometryEditSession.ShapeRow::id).toList());
                assertEquals(.8*u,s.resultTools().getFirst().toolDiameter()); assertEquals(List.of(0),s.selectedIndexOrder());
                assertSame(untouched,s.resultGeometry().getGeometryN(1));
                assertTrue(s.undo()); assertFalse(s.isDirty()); assertTrue(s.redo()); assertTrue(s.isDirty());
                assertEquals(2*u,source.getCoordinate().x);
            }
        }
    }
    @Test void invalidAndCollapsedTransformsNeverChangeDraft() {
        for(double bad:new double[]{0,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->EditorTransform.scale(bad,1));
        for(double bad:new double[]{-90,90,180,Double.NaN})
            assertThrows(IllegalArgumentException.class,()->EditorTransform.skew(bad,0));
        assertThrows(IllegalArgumentException.class,()->EditorTransform.skew(45,45));
        assertThrows(IllegalArgumentException.class,()->EditorTransform.offset(0,0));
        assertThrows(IllegalArgumentException.class,()->EditorTransform.scale(1,1));
        var s=new GeometryEditSession(source(1),List.of()); s.selectIndices(List.of(0));
        assertThrows(IllegalArgumentException.class,()->s.prepareTransform(EditorTransform.scale(2,2),new Coordinate(Double.NaN,0)));
        var overflow=s.prepareTransform(EditorTransform.offset(Double.MAX_VALUE,0),new Coordinate(0,0));
        // A second transform at the numeric limit must be refused before publishing a partial draft.
        var huge=F.createLineString(new Coordinate[]{new Coordinate(Double.MAX_VALUE,0),new Coordinate(Double.MAX_VALUE,1)});
        var limit=new GeometryEditSession(huge,List.of()); limit.selectIndices(List.of(0));
        assertThrows(IllegalArgumentException.class,()->limit.prepareTransform(overflow.transform(),new Coordinate(0,0))
                .execute(CancellationToken.none(),ProgressCallback.none()));
        assertFalse(s.canUndo()); assertFalse(limit.canUndo());
    }
    @Test void staleSelectionAndRevisionAreRejectedAndSingleDenseShapeCanCancel() {
        var s=new GeometryEditSession(F.buildGeometry(List.of(source(1),source(2))),List.of()); s.selectIndices(List.of(0));
        var result=s.prepareTransform(EditorTransform.scale(2,3),new Coordinate(0,0)).execute(CancellationToken.none(),ProgressCallback.none());
        s.selectIndices(List.of(1)); assertFalse(s.applyTransform(result));
        s.selectIndices(List.of(0)); s.moveSelected(1,0); assertFalse(s.applyTransform(result));
        Coordinate[] points=new Coordinate[20000]; for(int i=0;i<points.length;i++) points[i]=new Coordinate(i,i%7);
        Geometry original=F.createLineString(points); var dense=new GeometryEditSession(original,List.of()); dense.selectIndices(List.of(0));
        var calls=new AtomicInteger(); var job=dense.prepareTransform(EditorTransform.skew(12,5),new Coordinate(0,0));
        assertThrows(CancellationException.class,()->job.execute(()->calls.incrementAndGet()>10,ProgressCallback.none()));
        assertSame(original,dense.resultGeometry()); assertFalse(dense.canUndo());
    }
    @Test void mirroredAndSkewedPolygonsKeepInteriorHoles() {
        Polygon polygon=F.createPolygon(F.createLinearRing(new Coordinate[]{new Coordinate(0,0),new Coordinate(10,0),new Coordinate(10,8),new Coordinate(0,8),new Coordinate(0,0)}),
                new LinearRing[]{F.createLinearRing(new Coordinate[]{new Coordinate(2,2),new Coordinate(4,2),new Coordinate(4,4),new Coordinate(2,4),new Coordinate(2,2)})});
        for(var transform:List.of(EditorTransform.scale(-1,1),EditorTransform.skew(30,0))) {
            var s=new GeometryEditSession(polygon,List.of()); s.selectIndices(List.of(0));
            assertTrue(s.applyTransform(s.prepareTransform(transform,new Coordinate(0,0)).execute(CancellationToken.none(),ProgressCallback.none())));
            var output=(Polygon)s.resultGeometry(); assertEquals(1,output.getNumInteriorRing());
            assertTrue(output.isValid()); assertEquals(76,output.getArea(),1e-9);
        }
    }
}
