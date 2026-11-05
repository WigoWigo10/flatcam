package org.flatcam.cam.cutout;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

class InternalCutoutGeneratorTest {
    private static final GeometryFactory F = new GeometryFactory();
    private static Geometry box(double a,double b,double c,double d) { return F.toGeometry(new Envelope(a,b,c,d)); }
    private static Geometry board(double u) {
        return box(0,40*u,0,30*u).difference(box(5*u,15*u,5*u,13*u).union(box(22*u,30*u,15*u,23*u)));
    }
    private static Geometry left(List<Geometry> areas,double u) {
        return areas.stream().filter(g -> g.getEnvelopeInternal().getMinX()<10*u).findFirst().orElseThrow();
    }

    @ParameterizedTest @ValueSource(doubles={1,0.03937007874015748})
    void selectedOpeningIsInsetByRadiusAndMarginWithoutOuterPerimeterOrSourceChanges(double u) {
        Geometry board=board(u);String original=board.toText();
        var areas=InternalCutoutGenerator.openings(board,CancellationToken.NONE); assertEquals(2,areas.size());
        var result=InternalCutoutGenerator.generate(u==1?"MM":"IN",board,List.of(left(areas,u)),2*u,.25*u,CancellationToken.NONE);
        assertEquals(1,result.geometry().getNumGeometries());
        assertTrue(((LineString)result.geometry()).isClosed());
        var b=result.geometry().getEnvelopeInternal();
        assertEquals(6.25*u,b.getMinX(),1e-10);assertEquals(13.75*u,b.getMaxX(),1e-10);
        assertEquals(6.25*u,b.getMinY(),1e-10);assertEquals(11.75*u,b.getMaxY(),1e-10);
        assertEquals(26*u,result.geometry().getLength(),1e-9);
        assertTrue(result.gapGeometry().isEmpty());assertEquals(original,board.toText());
        var both=InternalCutoutGenerator.generate("MM",board,areas,2*u,0,CancellationToken.NONE);
        assertEquals(2,both.geometry().getNumGeometries());assertTrue(both.geometry().getEnvelopeInternal().getMaxX()<40*u);
    }

    @ParameterizedTest @ValueSource(doubles={1,0.03937007874015748})
    void drawnRectangleCircleAndPolygonAndPanelOpeningsKeepTheirCoordinates(double u) {
        Geometry board=board(u);
        Geometry rectangle=box(16*u,20*u,3*u,8*u);
        Geometry circle=F.createPoint(new Coordinate(20*u,26*u)).buffer(2*u,32);
        Geometry triangle=F.createPolygon(new Coordinate[]{new Coordinate(2*u,22*u),new Coordinate(6*u,22*u),
                new Coordinate(4*u,27*u),new Coordinate(2*u,22*u)});
        var drawn=InternalCutoutGenerator.generate("MM",board,List.of(rectangle,circle,triangle),.1981234567*u,0,CancellationToken.NONE);
        assertEquals(3,drawn.geometry().getNumGeometries());
        assertEquals(16*u+.1981234567*u/2,drawn.geometry().getGeometryN(0).getEnvelopeInternal().getMinX(),1e-12);
        Geometry other=AffineTransformation.translationInstance(50*u,0).transform(board);
        Geometry panel=F.createMultiPolygon(new Polygon[]{(Polygon)board,(Polygon)other});
        var areas=InternalCutoutGenerator.openings(panel,CancellationToken.NONE);assertEquals(4,areas.size());
        var selected=areas.stream().filter(g -> g.getEnvelopeInternal().getWidth()>9*u).toList();
        var output=InternalCutoutGenerator.generate("MM",panel,selected,u,0,CancellationToken.NONE).geometry();
        assertEquals(2,output.getNumGeometries());
        assertEquals(50*u,output.getGeometryN(1).getCentroid().getX()-output.getGeometryN(0).getCentroid().getX(),1e-9);
    }

    @Test void invalidAndOverlappingOrOutsideOpeningsAreRefusedWithoutPartialResults() {
        Geometry board=board(1);var hole=left(InternalCutoutGenerator.openings(board,CancellationToken.NONE),1);
        problem(InternalCutoutGenerator.Problem.EMPTY_SELECTION,board,List.of(),1,0);
        problem(InternalCutoutGenerator.Problem.BAD_PARAMETERS,board,List.of(hole),Double.NaN,0);
        problem(InternalCutoutGenerator.Problem.BAD_PARAMETERS,board,List.of(hole),1,-.1);
        problem(InternalCutoutGenerator.Problem.OUTSIDE_BOARD,board,List.of(box(0,3,20,25)),1,0);
        problem(InternalCutoutGenerator.Problem.OUTSIDE_BOARD,board,List.of(box(39,42,20,25)),1,0);
        problem(InternalCutoutGenerator.Problem.OVERLAPPING_OPENINGS,board,List.of(hole,hole),1,0);
        problem(InternalCutoutGenerator.Problem.INTERSECTS_EXISTING_HOLE,board,List.of(box(4,11,7,9)),1,0);
        problem(InternalCutoutGenerator.Problem.INVALID_OPENING,board,List.of(F.createPoint(new Coordinate(20,25))),1,0);
        Geometry line=F.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,1)});
        problem(InternalCutoutGenerator.Problem.INVALID_BOARD,line,List.of(hole),1,0);
        problem(InternalCutoutGenerator.Problem.INVALID_BOARD,F.createGeometryCollection(new Geometry[]{board,line}),List.of(hole),1,0);
        problem(InternalCutoutGenerator.Problem.TOOL_DOES_NOT_FIT,board,List.of(hole),12,0);
        Geometry dumbbell=box(16,19,1,4).union(box(21,24,1,4)).union(box(19,21,2.4,2.6));
        problem(InternalCutoutGenerator.Problem.TOOL_DOES_NOT_FIT,board,List.of(hole,dumbbell),1,0);
    }

    @Test void cancellationStopsDetectionAndGeneration() {
        assertThrows(CancellationException.class,()->InternalCutoutGenerator.openings(board(1),()->true));
        Geometry board=board(1);var selected=InternalCutoutGenerator.openings(board,CancellationToken.NONE);
        var checks=new AtomicInteger();
        assertThrows(CancellationException.class,()->InternalCutoutGenerator.generate("MM",board,selected,1,0,()->checks.incrementAndGet()>8));
        assertTrue(checks.get()>8);
    }

    private static void problem(InternalCutoutGenerator.Problem expected,Geometry source,List<Geometry> selected,double dia,double margin) {
        var failure=assertThrows(InternalCutoutGenerator.InvalidOpening.class,
                ()->InternalCutoutGenerator.generate("MM",source,selected,dia,margin,CancellationToken.NONE));
        assertEquals(expected,failure.problem());
    }
}
