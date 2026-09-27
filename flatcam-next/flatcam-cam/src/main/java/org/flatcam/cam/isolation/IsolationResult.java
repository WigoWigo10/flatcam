package org.flatcam.cam.isolation;

import java.util.List;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * The isolation toolpath: normally a collection of rings (closed LineStrings),
 * one per isolated copper boundary per pass. Follow mode instead retains the
 * Gerber's unbuffered centerlines and flash centers. See
 * IsolationGenerator's doc for how it's built.
 */
public final class IsolationResult {

    private final String units;
    private final Geometry geometry;
    private final List<Geometry> passGeometries;

    IsolationResult(String units, Geometry geometry, List<Geometry> passGeometries) {
        this.units = units;
        this.geometry = geometry;
        this.passGeometries = List.copyOf(passGeometries);
    }

    public String units() {
        return units;
    }

    /** The toolpaths, including points for follow-mode flash centers. */
    public Geometry geometry() {
        return geometry;
    }

    /** One path collection per requested pass, in machining order. Empty passes remain in the list. */
    public List<Geometry> passGeometries() {
        return passGeometries;
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
