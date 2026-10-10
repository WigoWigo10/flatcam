package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

class OutlineToAreaTest {
    private static final GeometryFactory F = new GeometryFactory();
    private static Geometry box(double x1, double x2, double y1, double y2) {
        return F.toGeometry(new Envelope(x1,x2,y1,y2));
    }
    private static int holes(Geometry area) {
        int count = 0;
        for (int i=0; i<area.getNumGeometries(); i++) count += ((Polygon)area.getGeometryN(i)).getNumInteriorRing();
        return count;
    }

    @ParameterizedTest @ValueSource(doubles={1, 0.03937007874015748})
    void panelKeepsEveryBoardAndHoleIndependentOfOrderWindingAndUnits(double unit) {
        Geometry board = box(10,30,20,36).difference(box(16,20,24,29)).difference(box(24,27,30,33));
        List<Geometry> copies = new ArrayList<>(), lines = new ArrayList<>();
        for (int row=0; row<2; row++) for (int col=0; col<3; col++) {
            Geometry copy = AffineTransformation.translationInstance(col*25,row*21).transform(board);
            copy = AffineTransformation.scaleInstance(unit,unit).transform(copy);
            copies.add(copy);
            Geometry boundary = copy.getBoundary();
            for (int i=0; i<boundary.getNumGeometries(); i++) lines.add(boundary.getGeometryN(i).reverse());
        }
        Collections.shuffle(lines, new java.util.Random(7));
        Geometry source = F.buildGeometry(lines), original = source.copy();
        var result = OutlineToArea.convert(source);
        assertEquals(6, result.area().getNumGeometries());
        assertEquals(12, holes(result.area()));
        assertEquals(board.getArea()*6*unit*unit, result.area().getArea(), 1e-4*unit*unit);
        assertTrue(result.area().symDifference(F.buildGeometry(copies)).getArea() < 1e-4*unit*unit);
        assertTrue(result.area().isValid());
        assertTrue(source.equalsExact(original), "Conversion must not modify the source");
        assertEquals(18, result.candidates(), "Candidates include hole faces, not only board count");
    }

    @Test void nestedHoleIslandAndIslandHoleAlternateByDepth() {
        List<Geometry> rings = List.of(box(0,40,0,40).getBoundary(), box(5,35,5,35).getBoundary().reverse(),
                box(10,30,10,30).getBoundary(), box(15,25,15,25).getBoundary());
        Geometry expected = box(0,40,0,40).difference(box(5,35,5,35))
                .union(box(10,30,10,30).difference(box(15,25,15,25)));
        var result = OutlineToArea.convert(F.buildGeometry(rings));
        assertEquals(2, result.area().getNumGeometries()); assertEquals(2, holes(result.area()));
        assertTrue(expected.equalsTopo(result.area()));
    }
    @Test void aLargeHoleIsNotMistakenForMaterialBecauseItsAreaExceedsTheBoardFrame() {
        Geometry frame = box(0,100,0,100).difference(box(1,99,1,99));
        var result = OutlineToArea.convert(frame.getBoundary());
        assertTrue(frame.equalsTopo(result.area()));
        assertEquals(396,result.area().getArea(),1e-8);
        assertEquals(1,holes(result.area()));
    }

    @Test void sharedEdgesDuplicateRingsAndDifferentBoardSizesDoNotLoseMaterial() {
        Geometry large = box(0,20,0,10), touching = box(20,25,0,10), small = box(30,32,0,2);
        var result = OutlineToArea.convert(F.buildGeometry(List.of(large.getBoundary(), touching.getBoundary(),
                small.getBoundary(), large.getBoundary().reverse())));
        assertTrue(result.area().equalsTopo(large.union(touching).union(small)));
        assertEquals(2, result.area().getNumGeometries());
        assertEquals(254, result.area().getArea(), 1e-8);
    }

    @Test void mixedOpenAndClosedOrDisconnectedCutEdgesAreRejectedInsteadOfPartialOutput() {
        Geometry closed = box(0,20,0,10).getBoundary();
        Geometry open = F.createLineString(new Coordinate[]{new Coordinate(30,0),new Coordinate(40,0),new Coordinate(40,10)});
        assertThrows(IllegalArgumentException.class, () -> OutlineToArea.convert(F.buildGeometry(List.of(closed,open))));
        Geometry inner = box(5,10,3,7).getBoundary();
        Geometry bridge = F.createLineString(new Coordinate[]{new Coordinate(0,5),new Coordinate(5,5)});
        assertThrows(IllegalArgumentException.class, () -> OutlineToArea.convert(F.buildGeometry(List.of(closed,inner,bridge))));
        assertThrows(IllegalArgumentException.class, () -> OutlineToArea.convert(F.createPoint(new Coordinate(1,2))));
        assertThrows(IllegalArgumentException.class, () -> OutlineToArea.convert(null));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY}) {
            Geometry line = F.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(invalid,2)});
            assertThrows(IllegalArgumentException.class, () -> OutlineToArea.convert(line));
        }
    }

    @Test void polygonalSourcesAndLargeGridSupportCooperativeCancellation() {
        Geometry source = box(0,20,0,10).difference(box(5,10,3,7));
        assertTrue(source.equalsTopo(OutlineToArea.convert(source).area()));
        assertThrows(CancellationException.class, () -> OutlineToArea.convert(source, () -> true));
        List<Geometry> copies = new ArrayList<>();
        for (int i=0; i<100; i++) copies.add(AffineTransformation.translationInstance(i*25,0).transform(source).getBoundary());
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() > 50;
        assertThrows(CancellationException.class, () -> OutlineToArea.convert(F.buildGeometry(copies), cancellation));
    }
}
