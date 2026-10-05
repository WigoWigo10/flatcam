package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

class StableInteriorPointTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void nonPolygonPartsAreIgnoredInsteadOfRecursingIntoThemselves() {
        Geometry line = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 1)});
        assertNull(StableInteriorPoint.find(line, 0.01, CancellationToken.none()));
        Geometry square = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        Coordinate point = StableInteriorPoint.find(FACTORY.createGeometryCollection(new Geometry[]{line, square}),
                0.01, CancellationToken.none());
        assertEquals(5, point.x, 0.05);
    }

    @Test
    void cancellationPrecedesEvenTheInitialGridOfAnExtremelyThinPolygon() {
        Geometry thin = FACTORY.toGeometry(new Envelope(0, 1, 0, 1e-12));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CancellationException.class,
                () -> StableInteriorPoint.find(thin, 1e-14, () -> checks.incrementAndGet() > 0));
        assertEquals(1, checks.get());
    }

    @Test
    void initialGridIsCancellableDuringConstruction() {
        Geometry thin = FACTORY.toGeometry(new Envelope(0, 1, 0, 1e-12));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CancellationException.class,
                () -> StableInteriorPoint.find(thin, 1e-14, () -> checks.incrementAndGet() > 8));
        assertEquals(9, checks.get());
    }

    @Test
    void extremeAspectRatioFinishesWithAnInteriorCandidate() {
        Geometry thin = FACTORY.toGeometry(new Envelope(0, 1, 0, 1e-12));
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            Coordinate point = StableInteriorPoint.find(thin, 1e-14, CancellationToken.none());
            assertTrue(thin.contains(FACTORY.createPoint(point)));
        });
    }

    @Test
    void largeCoordinateOffsetsDoNotStallTheGrid() {
        Geometry offset = FACTORY.toGeometry(new Envelope(1e15, 1e15 + 100, 1e15, 1e15 + 1));
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            Coordinate point = StableInteriorPoint.find(offset, 0.01, CancellationToken.none());
            assertTrue(offset.contains(FACTORY.createPoint(point)));
        });
    }

    @Test
    void invalidPrecisionIsRejected() {
        Geometry square = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        for (double precision : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> StableInteriorPoint.find(square, precision, CancellationToken.none()));
    }

    @Test
    void aSquaresPointLandsAtItsCentre() {
        Geometry square = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));

        Coordinate point = StableInteriorPoint.find(square, 0.01, CancellationToken.none());

        assertEquals(5, point.x, 0.05);
        assertEquals(5, point.y, 0.05);
    }

    @Test
    void avoidsAHoleInsteadOfLandingInsideIt() {
        Geometry withHole = FACTORY.toGeometry(new Envelope(0, 20, 0, 20))
                .difference(FACTORY.toGeometry(new Envelope(8, 12, 8, 12)));

        Coordinate point = StableInteriorPoint.find(withHole, 0.05, CancellationToken.none());

        Point found = FACTORY.createPoint(point);
        assertTrue(withHole.contains(found), "the point must be inside the shape, not in the hole");
        // Deepest point sits on a diagonal where distance to the outer edge equals distance to
        // the hole's corner: 10-t = sqrt(2)(t-2) solved for t, i.e. 16-8*sqrt(2) =~ 4.686.
        assertEquals(16 - 8 * Math.sqrt(2), withHole.getBoundary().distance(found), 0.05);
    }

    @Test
    void picksTheDeeperOfTwoDisjointPolygons() {
        Geometry small = FACTORY.toGeometry(new Envelope(0, 2, 0, 2));
        Geometry big = FACTORY.toGeometry(new Envelope(100, 140, 100, 140));
        Geometry both = small.union(big);

        Coordinate point = StableInteriorPoint.find(both, 0.05, CancellationToken.none());

        assertTrue(point.x > 90, "the point must land in the larger, deeper polygon");
    }

    @Test
    void anLShapeLandsWellInsideOneOfItsArms() {
        Polygon lShape = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0), new Coordinate(20, 6),
                new Coordinate(6, 6), new Coordinate(6, 20), new Coordinate(0, 20), new Coordinate(0, 0)});

        Coordinate point = StableInteriorPoint.find(lShape, 0.02, CancellationToken.none());

        Point found = FACTORY.createPoint(point);
        assertTrue(lShape.contains(found));
        // The deepest point is not simply the 3.0 half-width of either arm: on the diagonal
        // from the outside corner, it sits where distance to the two outer edges equals
        // distance to the concave notch corner (6,6), i.e. 6*sqrt(2)/(1+sqrt(2)) =~ 3.513.
        assertEquals(6 * Math.sqrt(2) / (1 + Math.sqrt(2)), lShape.getBoundary().distance(found), 0.05);
    }

    @Test
    void tinyInputPerturbationsMoveThePointOnlyASimilarlyTinyAmount() {
        // Reproduces INVESTIGACAM_CAM.md's minimal diamond case, where JTS/GEOS's own
        // scan-line interior point jumps by ~2mm for a 1e-12 change in a vertex - the
        // whole reason this class exists instead of Geometry.getInteriorPoint().
        Polygon reference = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(2, 2), new Coordinate(0, 4),
                new Coordinate(-2, 2), new Coordinate(0, 0)});
        Polygon perturbed = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(2, 2 + 1e-12), new Coordinate(0, 4),
                new Coordinate(-2, 2 + 1e-12), new Coordinate(0, 0)});

        Coordinate referencePoint = StableInteriorPoint.find(reference, 1e-4, CancellationToken.none());
        Coordinate perturbedPoint = StableInteriorPoint.find(perturbed, 1e-4, CancellationToken.none());

        assertTrue(referencePoint.distance(perturbedPoint) < 1e-3,
                () -> "moved " + referencePoint.distance(perturbedPoint) + " for a 1e-12 input change");
    }

    @Test
    void emptyGeometryHasNoInteriorPoint() {
        assertNull(StableInteriorPoint.find(FACTORY.createPolygon(), 0.01, CancellationToken.none()));
    }

    @Test
    void cooperativeCancellationIsHonoredOnLargeSearches() {
        Geometry huge = FACTORY.toGeometry(new Envelope(0, 10_000, 0, 10_000));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        CancellationToken cancelAfterAFewChecks = () -> checks.incrementAndGet() > 2;

        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CancellationException.class,
                () -> StableInteriorPoint.find(huge, 1e-9, cancelAfterAFewChecks));
    }

    @Test
    void finerPrecisionNeverMovesThePointFarFromACoarserResult() {
        Polygon lShape = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0), new Coordinate(20, 6),
                new Coordinate(6, 6), new Coordinate(6, 20), new Coordinate(0, 20), new Coordinate(0, 0)});

        Coordinate coarse = StableInteriorPoint.find(lShape, 1.0, CancellationToken.none());
        Coordinate fine = StableInteriorPoint.find(lShape, 1e-6, CancellationToken.none());

        assertNotNull(coarse);
        assertNotNull(fine);
        assertTrue(coarse.distance(fine) < 1.5);
    }
}
