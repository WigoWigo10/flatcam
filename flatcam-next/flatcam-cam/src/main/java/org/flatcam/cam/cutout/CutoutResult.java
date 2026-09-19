package org.flatcam.cam.cutout;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * The cutout toolpath: a collection of open LineStrings (the outline(s),
 * already interrupted at every bridge gap) - unlike IsolationResult's closed
 * rings, these are deliberately broken, since that's the entire point of a
 * bridge (the tool never cuts through it).
 */
public final class CutoutResult {

    private final String units;
    private final Geometry geometry;

    CutoutResult(String units, Geometry geometry) {
        this.units = units;
        this.geometry = geometry;
    }

    public String units() {
        return units;
    }

    public Geometry geometry() {
        return geometry;
    }

    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }

    public int partCount() {
        return isEmpty() ? 0 : geometry.getNumGeometries();
    }

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
