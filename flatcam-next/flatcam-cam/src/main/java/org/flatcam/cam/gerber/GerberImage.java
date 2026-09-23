package org.flatcam.cam.gerber;

import java.util.LinkedHashMap;
import java.util.Map;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Result of parsing one Gerber file: the resolved apertures, the final
 * (unioned/cleared) solid geometry, the unbuffered paths used to construct it,
 * and each aperture's own shapes (every flash/stroke that used it, unioned,
 * regardless of polarity) for the apertures table's "Mark" highlight - see
 * GerberParser's class doc. Units are always inches or millimeters as declared
 * by the file's %MO - no unit conversion is performed.
 */
public final class GerberImage {

    private final String units;
    private final Map<String, Aperture> apertures;
    private final Geometry solidGeometry;
    private final Geometry followGeometry;
    private final Map<String, Geometry> apertureGeometry;

    GerberImage(String units, Map<String, Aperture> apertures, Geometry solidGeometry,
                Geometry followGeometry, Map<String, Geometry> apertureGeometry) {
        this.units = units;
        this.apertures = Map.copyOf(apertures);
        this.solidGeometry = solidGeometry;
        this.followGeometry = followGeometry;
        this.apertureGeometry = Map.copyOf(apertureGeometry);
    }

    public String units() {
        return units;
    }

    public Map<String, Aperture> apertures() {
        return apertures;
    }

    public Geometry solidGeometry() {
        return solidGeometry;
    }

    /**
     * Unbuffered Gerber paths: draw centerlines, region boundaries and flash
     * centers. This is the legacy Gerber object's {@code follow_geometry}.
     */
    public Geometry followGeometry() {
        return followGeometry;
    }

    /** One aperture's own shapes (every flash/stroke that used it), for the "Mark" highlight - empty if the aperture was never used. */
    public Map<String, Geometry> apertureGeometry() {
        return apertureGeometry;
    }

    /**
     * A copy with {@code op} applied to every geometry this object carries
     * (solid, follow, and each aperture's own shapes) - appTools/ToolTransform.py's
     * six operations applied to a Gerber via {@code Gerber.rotate/mirror/skew/scale/offset}.
     * Aperture width/height/diameter fields are intentionally left untouched:
     * they only feed the apertures table's display, not any CAM calculation
     * (which reads {@link #solidGeometry()}/{@link #apertureGeometry()}
     * directly), so leaving them stale after a transform is a deliberate v1
     * simplification rather than a correctness gap.
     */
    public GerberImage transformed(TransformOp op) {
        Map<String, Geometry> newApertureGeometry = new LinkedHashMap<>();
        for (Map.Entry<String, Geometry> entry : apertureGeometry.entrySet()) {
            newApertureGeometry.put(entry.getKey(), op.apply(entry.getValue()));
        }
        return new GerberImage(units, apertures,
                solidGeometry == null ? null : op.apply(solidGeometry),
                followGeometry == null ? null : op.apply(followGeometry),
                newApertureGeometry);
    }

    public boolean isEmpty() {
        return solidGeometry == null || solidGeometry.isEmpty();
    }

    /** {@code [minX, minY, maxX, maxY]}, or null if there is no geometry. */
    public double[] bounds() {
        if (isEmpty()) {
            return null;
        }
        Envelope envelope = solidGeometry.getEnvelopeInternal();
        return new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
    }

    /** Number of disjoint polygon parts in the solid geometry (matches Python's "solid_geometry_part_count"). */
    public int partCount() {
        if (isEmpty()) {
            return 0;
        }
        return solidGeometry.getNumGeometries();
    }

    public double totalArea() {
        return isEmpty() ? 0.0 : solidGeometry.getArea();
    }
}
