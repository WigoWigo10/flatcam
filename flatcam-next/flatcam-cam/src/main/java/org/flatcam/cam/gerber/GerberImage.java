package org.flatcam.cam.gerber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.union.UnaryUnionOp;

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
    private final List<GerberShape> shapes;

    GerberImage(String units, Map<String, Aperture> apertures, Geometry solidGeometry,
                Geometry followGeometry, Map<String, Geometry> apertureGeometry) {
        this(units, apertures, solidGeometry, followGeometry, apertureGeometry, List.of());
    }

    GerberImage(String units, Map<String, Aperture> apertures, Geometry solidGeometry,
                Geometry followGeometry, Map<String, Geometry> apertureGeometry, List<GerberShape> shapes) {
        this.units = units;
        this.apertures = Map.copyOf(apertures);
        this.solidGeometry = solidGeometry;
        this.followGeometry = followGeometry;
        this.apertureGeometry = Map.copyOf(apertureGeometry);
        this.shapes = List.copyOf(shapes);
    }

    /**
     * Builds a GerberImage directly from already-resolved data rather than
     * parsing a file - used by project persistence (org.flatcam.app.project.flatprj)
     * to reconstruct one from a saved project's embedded geometry.
     */
    public static GerberImage of(String units, Map<String, Aperture> apertures, Geometry solidGeometry,
                                 Geometry followGeometry, Map<String, Geometry> apertureGeometry) {
        return new GerberImage(units, apertures, solidGeometry, followGeometry, apertureGeometry);
    }

    public static GerberImage of(String units, Map<String, Aperture> apertures, Geometry solidGeometry,
                                 Geometry followGeometry, Map<String, Geometry> apertureGeometry,
                                 List<GerberShape> shapes) {
        return new GerberImage(units, apertures, solidGeometry, followGeometry, apertureGeometry, shapes);
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
     * Every flash/stroke/region in file order, un-unioned. Empty when not
     * known - for example, an image restored from an older project file that
     * kept only one aggregate geometry per aperture.
     */
    public List<GerberShape> shapes() {
        return shapes;
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
        List<GerberShape> newShapes = new ArrayList<>(shapes.size());
        for (GerberShape shape : shapes) {
            newShapes.add(new GerberShape(shape.apertureCode(), op.apply(shape.geometry()), shape.clear(),
                    shape.followGeometry() == null ? null : op.apply(shape.followGeometry())));
        }
        return new GerberImage(units, apertures,
                solidGeometry == null ? null : op.apply(solidGeometry),
                followGeometry == null ? null : op.apply(followGeometry),
                newApertureGeometry, newShapes);
    }

    /** Rebuilds all derived geometries from the editor's ordered, individual shapes. */
    public GerberImage withEditedShapes(List<GerberShape> editedShapes, CancellationToken cancellation,
                                        ProgressCallback progress) {
        GeometryFactory factory = solidGeometry != null ? solidGeometry.getFactory() : new GeometryFactory();
        Geometry solid = factory.createPolygon();
        List<Geometry> pending = new ArrayList<>();
        List<Geometry> follow = new ArrayList<>();
        Map<String, List<Geometry>> byAperture = new LinkedHashMap<>();
        boolean pendingClear = false;
        progress.report(0);
        for (int i = 0; i < editedShapes.size(); i++) {
            cancellation.throwIfCancellationRequested();
            GerberShape shape = editedShapes.get(i);
            if (!apertures.containsKey(shape.apertureCode())
                    && !GerberShape.REGION_APERTURE.equals(shape.apertureCode())) {
                throw new IllegalArgumentException("Unknown Gerber aperture: " + shape.apertureCode());
            }
            Geometry geometry = shape.geometry();
            if (geometry == null || geometry.isEmpty()) {
                continue;
            }
            if (!pending.isEmpty() && shape.clear() != pendingClear) {
                solid = applyPolarityBatch(solid, pending, pendingClear, cancellation);
            }
            pendingClear = shape.clear();
            pending.add(geometry);
            if (!GerberShape.REGION_APERTURE.equals(shape.apertureCode())) {
                byAperture.computeIfAbsent(shape.apertureCode(), ignored -> new ArrayList<>()).add(geometry);
            }
            Geometry path = shape.followGeometry();
            follow.add(path != null ? path : geometry.getBoundary());
            if ((i & 127) == 0) {
                progress.report(0.05 + 0.45 * (i + 1.0) / Math.max(1, editedShapes.size()));
            }
        }
        solid = applyPolarityBatch(solid, pending, pendingClear, cancellation);
        progress.report(0.60);

        Map<String, Geometry> mergedApertures = new LinkedHashMap<>();
        int processed = 0;
        for (Map.Entry<String, List<Geometry>> entry : byAperture.entrySet()) {
            cancellation.throwIfCancellationRequested();
            List<Geometry> geometries = entry.getValue();
            mergedApertures.put(entry.getKey(), geometries.size() == 1
                    ? geometries.get(0) : UnaryUnionOp.union(geometries));
            processed++;
            progress.report(0.60 + 0.38 * processed / byAperture.size());
        }
        cancellation.throwIfCancellationRequested();
        Geometry followResult = factory.createGeometryCollection(follow.toArray(Geometry[]::new));
        progress.report(1);
        return new GerberImage(units, apertures, solid, followResult, mergedApertures, editedShapes);
    }

    private static Geometry applyPolarityBatch(Geometry solid, List<Geometry> pending,
                                               boolean clear, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (pending.isEmpty()) {
            return solid;
        }
        Geometry batch = pending.size() == 1 ? pending.get(0) : UnaryUnionOp.union(pending);
        pending.clear();
        cancellation.throwIfCancellationRequested();
        Geometry result = clear ? solid.difference(batch) : solid.union(batch);
        cancellation.throwIfCancellationRequested();
        return result;
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
