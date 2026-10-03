package org.flatcam.cam.ncc.geosbuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

/**
 * Regression anchor for the GEOS 3.10.3 compatibility fork - see this package's class docs
 * and {@code INVESTIGACAO_CAM.md} for the full causal story (NCC/Paint Standard's erosion
 * disagreeing with the legacy Python/Shapely/GEOS toolchain by as much as ~0.021 mm on a
 * real board). The golden value in {@link #erosionMatchesAGeosOracleOnThePublicFixture} was
 * captured from an actual GEOS run (3.10.3 and 3.13.1, same result) on this exact input via
 * {@code tools/CamKernelProbe.java} and {@code tools/compare_cam_kernel_probe.py} - see those
 * files and this project's root for the reproducible procedure. It is not re-derived from
 * GEOS at test time: this project has no GEOS/Shapely runtime dependency.
 */
class GeosBufferOpTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void erosionMatchesAGeosOracleOnThePublicFixture() {
        // tools/CamKernelProbe.java's synthetic-standard fixture, resolution 4, after the same
        // 8 successive erosion passes (radius .5/1.999999, then step .3) that amplify the two
        // engines' divergence - see class doc. Only the final area is asserted; the point is
        // reaching the same place after repeated erosion, not matching after a single pass
        // (pass 0 alone is near-identical between engines - the divergence accumulates).
        Geometry hole = FACTORY.createPoint(new Coordinate(6, 6)).buffer(1, 4);
        Geometry area = FACTORY.toGeometry(new Envelope(0, 12, 0, 12)).difference(hole);
        BufferParameters parameters = new BufferParameters(64);

        Geometry eroded = erodeEightPasses(area, parameters, GeosBufferOp::bufferOp);
        Geometry stockJts = erodeEightPasses(area, parameters, BufferOp::bufferOp);

        double geosOracleArea = 18.20853174669319; // captured from GEOS 3.10.3/3.13.1, see class doc
        assertEquals(geosOracleArea, eroded.getArea(), 1e-6,
                "GeosBufferOp must match the real GEOS oracle on this public fixture");
        assertTrue(Math.abs(stockJts.getArea() - geosOracleArea) > 1e-3,
                "this input must actually exercise the fix - stock JTS should visibly disagree with GEOS here");
    }

    private interface Erode {
        Geometry apply(Geometry geometry, double distance, BufferParameters parameters);
    }

    private static Geometry erodeEightPasses(Geometry area, BufferParameters parameters, Erode erode) {
        Geometry current = erode.apply(area, -(.5 / 1.999999), parameters);
        for (int pass = 0; pass < 7; pass++) {
            current = erode.apply(current, -.3, parameters);
        }
        return current;
    }

    @Test
    void smallConcaveNotchSurvivesErosionLikePaintGeneratorTestExpects() {
        // Same shape as PaintGeneratorTest's notch regression, exercised directly at this layer.
        Geometry source = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0), new Coordinate(20, 10), new Coordinate(0, 10),
                new Coordinate(0, 6), new Coordinate(.002, 5.5), new Coordinate(0, 5), new Coordinate(0, 0)});
        double diameter = .5;
        Coordinate notch = new Coordinate(.002, 5.5);
        BufferParameters parameters = new BufferParameters(64);

        Geometry eroded = GeosBufferOp.bufferOp(source, -(diameter / 1.999999), parameters);

        assertTrue(eroded.distance(FACTORY.createPoint(notch)) >= diameter / 2 - 1e-5,
                "the input simplifier must not shortcut the notch before erosion");
    }

    @Test
    void erodingAwayCompletelyProducesAnEmptyPolygonNotAnException() {
        Geometry square = FACTORY.toGeometry(new Envelope(0, 1, 0, 1));
        BufferParameters parameters = new BufferParameters(64);

        Geometry eroded = GeosBufferOp.bufferOp(square, -2, parameters);

        assertTrue(eroded.isEmpty());
    }

    @Test
    void aPlainRectangleWithNoConcaveCornersErodesTheSameAsStockJts() {
        // Nothing here is concave, so the (GEOS- vs JTS-specific) input simplifier never
        // touches it - this is the baseline confirming the fork does not gratuitously diverge.
        Geometry rectangle = FACTORY.toGeometry(new Envelope(0, 40, 0, 25));
        BufferParameters parameters = new BufferParameters(64);

        Geometry geosCompat = GeosBufferOp.bufferOp(rectangle, -3, parameters);
        Geometry stockJts = BufferOp.bufferOp(rectangle, -3, parameters);

        assertEquals(stockJts.getArea(), geosCompat.getArea(), 1e-9);
        assertEquals(0, stockJts.symDifference(geosCompat).getArea(), 1e-9);
    }

    @Test
    void positiveDistanceDilatesInsteadOfEroding() {
        Geometry square = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        BufferParameters parameters = new BufferParameters(64);

        Geometry dilated = GeosBufferOp.bufferOp(square, 2, parameters);

        assertTrue(dilated.covers(square));
        assertTrue(dilated.getArea() > square.getArea());
    }

    @Test
    void aPolygonWithAHoleErodesTheHoleOutwardAndTheShellInward() {
        Geometry shape = FACTORY.toGeometry(new Envelope(0, 20, 0, 20))
                .difference(FACTORY.toGeometry(new Envelope(8, 12, 8, 12)));
        BufferParameters parameters = new BufferParameters(64);

        Geometry eroded = GeosBufferOp.bufferOp(shape, -1, parameters);

        Envelope bounds = eroded.getEnvelopeInternal();
        assertEquals(1, bounds.getMinX(), 1e-9);
        assertEquals(19, bounds.getMaxX(), 1e-9);
        assertFalse(eroded.covers(FACTORY.createPoint(new Coordinate(9, 9))), "the hole must have grown, not shrunk");
        assertTrue(eroded.covers(FACTORY.createPoint(new Coordinate(2, 2))));
    }
}
