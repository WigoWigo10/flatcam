package org.flatcam.cam.ncc;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/** Resulting center-line toolpaths plus the non-copper area they cover. */
public final class NccResult {

    private final String units;
    private final Geometry geometry;
    private final Geometry clearingArea;
    private final int failedPolygonCount;

    NccResult(String units, Geometry geometry, Geometry clearingArea, int failedPolygonCount) {
        this.units = units;
        this.geometry = geometry;
        this.clearingArea = clearingArea;
        this.failedPolygonCount = failedPolygonCount;
    }

    public String units() {
        return units;
    }

    public Geometry geometry() {
        return geometry;
    }

    public Geometry clearingArea() {
        return clearingArea;
    }

    public int failedPolygonCount() {
        return failedPolygonCount;
    }

    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }

    public int pathCount() {
        return isEmpty() ? 0 : geometry.getNumGeometries();
    }

    public double totalLength() {
        return isEmpty() ? 0 : geometry.getLength();
    }

    public double[] bounds() {
        if (isEmpty()) {
            return null;
        }
        Envelope envelope = geometry.getEnvelopeInternal();
        return new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
    }
}
