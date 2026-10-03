package org.flatcam.cam.ncc.geosbuffer;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.noding.Noder;
import org.locationtech.jts.noding.ScaledNoder;
import org.locationtech.jts.noding.snapround.SnapRoundingNoder;
import org.locationtech.jts.operation.buffer.BufferParameters;

/**
 * Public entry point for this package - {@link GeosBufferBuilder#buffer} run through
 * the same double-precision-first, snap-rounding-fallback orchestration as JTS's own
 * {@code BufferOp}, so a numerically hard input degrades the same way (retrying at
 * progressively reduced precision on a {@link TopologyException}) rather than simply
 * failing where stock JTS would have recovered. Only {@code BufferOp}'s orchestration
 * is reused here, directly: {@link PrecisionModel}, {@link Noder}, {@link ScaledNoder}
 * and {@link SnapRoundingNoder} are unmodified, public JTS classes with no GEOS
 * divergence of their own - see {@link GeosOffsetSegmentGenerator} and
 * {@link GeosBufferInputLineSimplifier} for where the actual behaviour differs.
 */
public final class GeosBufferOp {

    /** A number of digits of precision that leaves headroom for floating-point ops (matches JTS's own). */
    private static final int MAX_PRECISION_DIGITS = 12;

    private GeosBufferOp() {
    }

    /** Erodes/dilates {@code geometry} by {@code distance} using GEOS 3.10.3-compatible buffer rules. */
    public static Geometry bufferOp(Geometry geometry, double distance, BufferParameters parameters) {
        Geometry result = bufferOriginalPrecision(geometry, distance, parameters);
        if (result != null) {
            return result;
        }
        PrecisionModel geometryPrecision = geometry.getFactory().getPrecisionModel();
        if (geometryPrecision.getType() == PrecisionModel.FIXED) {
            return bufferFixedPrecision(geometry, distance, parameters, geometryPrecision);
        }
        TopologyException lastFailure = null;
        for (int digits = MAX_PRECISION_DIGITS; digits >= 0; digits--) {
            try {
                double scale = precisionScaleFactor(geometry, distance, digits);
                return bufferFixedPrecision(geometry, distance, parameters, new PrecisionModel(scale));
            } catch (TopologyException failure) {
                lastFailure = failure;
            }
        }
        throw lastFailure;
    }

    private static Geometry bufferOriginalPrecision(Geometry geometry, double distance, BufferParameters parameters) {
        try {
            return new GeosBufferBuilder(parameters).buffer(geometry, distance);
        } catch (RuntimeException failure) {
            return null; // signals the caller to retry at reduced precision, like BufferOp does
        }
    }

    private static Geometry bufferFixedPrecision(Geometry geometry, double distance, BufferParameters parameters,
                                                 PrecisionModel fixedPrecision) {
        Noder snapNoder = new SnapRoundingNoder(new PrecisionModel(1.0));
        Noder noder = new ScaledNoder(snapNoder, fixedPrecision.getScale());
        GeosBufferBuilder builder = new GeosBufferBuilder(parameters);
        builder.setWorkingPrecisionModel(fixedPrecision);
        builder.setNoder(noder);
        return builder.buffer(geometry, distance);
    }

    /** Matches JTS BufferOp's own precisionScaleFactor: scale chosen from the geometry+distance magnitude. */
    private static double precisionScaleFactor(Geometry geometry, double distance, int maxPrecisionDigits) {
        org.locationtech.jts.geom.Envelope envelope = geometry.getEnvelopeInternal();
        double envelopeMax = Math.max(Math.max(Math.abs(envelope.getMaxX()), Math.abs(envelope.getMaxY())),
                Math.max(Math.abs(envelope.getMinX()), Math.abs(envelope.getMinY())));
        double expandByDistance = distance > 0.0 ? distance : 0.0;
        double bufferEnvelopeMax = envelopeMax + 2 * expandByDistance;
        int bufferEnvelopePrecisionDigits = (int) (Math.log(bufferEnvelopeMax) / Math.log(10) + 1.0);
        int minUnitLog10 = maxPrecisionDigits - bufferEnvelopePrecisionDigits;
        return Math.pow(10.0, minUnitLog10);
    }
}
