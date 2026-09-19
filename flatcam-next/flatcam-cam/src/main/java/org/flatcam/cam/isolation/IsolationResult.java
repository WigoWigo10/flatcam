package org.flatcam.cam.isolation;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * The isolation toolpath: a collection of rings (closed LineStrings), one per
 * isolated copper boundary per pass - not filled polygons, since the
 * toolpath follows the boundary, not the buffered area. See
 * IsolationGenerator's doc for how it's built.
 */
public final class IsolationResult {

    private final String units;
    private final Geometry geometry;

    IsolationResult(String units, Geometry geometry) {
        this.units = units;
        this.geometry = geometry;
    }

    public String units() {
        return units;
    }

    /** A GeometryCollection of closed LineString rings. */
    public Geometry geometry() {
        return geometry;
    }

    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }

    public int ringCount() {
        return isEmpty() ? 0 : geometry.getNumGeometries();
    }

    /** Total toolpath length (all rings, all passes) - useful for a rough machining-time estimate later. */
    public double totalLength() {
        return isEmpty() ? 0.0 : geometry.getLength();
    }

    public double[] bounds() {
        if (isEmpty()) {
            return null;
        }
        Envelope envelope = geometry.getEnvelopeInternal();
        return new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
    }
}
