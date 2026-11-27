package org.flatcam.cam.gerber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.LengthUnits;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Result of parsing one Gerber file: the resolved apertures, the final
 * solid geometry (resolved copper, or a collection of translated panel copies),
 * the unbuffered paths used to construct it,
 * and each aperture's own shapes (every flash/stroke that used it, unioned,
 * regardless of polarity) for the apertures table's "Mark" highlight - see
 * GerberParser's class doc. Units are always inches or millimeters as declared
 * by the file's %MO until the image is explicitly converted.
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
        this.apertureGeometry = apertureGeometry instanceof LazyApertureGeometry
                ? apertureGeometry : Map.copyOf(apertureGeometry);
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
     * A uniform scale also resizes the aperture definitions used for new
     * flashes and size checks. Other transforms act on the existing shapes.
     */
    public GerberImage transformed(TransformOp op) {
        if (op instanceof TransformOp.Scale scale) {
            double x = Math.abs(scale.xFactor()), y = Math.abs(scale.yFactor());
            if (x == y && x > 0 && Double.isFinite(x) && x != 1)
                return transformed(scale, units, scaledApertures(x), true);
        }
        return transformed(op, units, apertures, false);
    }

    /** Changes the measurement unit of all the geometries and aperture footprints. */
    public GerberImage convertedTo(String targetUnits) {
        if (units.equals(targetUnits)) return this;
        double factor = LengthUnits.factor(units, targetUnits);
        var scale = new TransformOp.Scale(factor, factor, new Coordinate(0, 0));
        return transformed(scale, targetUnits, scaledApertures(factor), true);
    }

    private Map<String, Aperture> scaledApertures(double factor) {
        Map<String, Aperture> result = new LinkedHashMap<>();
        apertures.forEach((code, aperture) -> result.put(code, aperture.convertedBy(factor)));
        return result;
    }

    private GerberImage transformed(TransformOp op, String resultUnits, Map<String, Aperture> resultApertures,
                                    boolean preserveLazyApertures) {
        Map<String, Geometry> newApertureGeometry = new LinkedHashMap<>();
        if (preserveLazyApertures && op instanceof TransformOp.Scale scale
                && apertureGeometry instanceof LazyApertureGeometry lazy) {
            newApertureGeometry = lazy.scaled(scale);
        } else {
            for (Map.Entry<String, Geometry> entry : apertureGeometry.entrySet()) {
                newApertureGeometry.put(entry.getKey(), op.apply(entry.getValue()));
            }
        }
        List<GerberShape> newShapes = new ArrayList<>(shapes.size());
        for (GerberShape shape : shapes) {
            newShapes.add(new GerberShape(shape.apertureCode(), op.apply(shape.geometry()), shape.clear(),
                    shape.followGeometry() == null ? null : op instanceof TransformOp.Buffer ? shape.followGeometry() : op.apply(shape.followGeometry())));
        }
        return new GerberImage(resultUnits, resultApertures,
                solidGeometry == null ? null : op.apply(solidGeometry),
                followGeometry == null ? null : op instanceof TransformOp.Buffer ? followGeometry : op.apply(followGeometry),
                newApertureGeometry, newShapes);
    }

    /** Rebuilds all derived geometries from the editor's ordered, individual shapes. */
    public GerberImage withEditedShapes(List<GerberShape> editedShapes, CancellationToken cancellation,
                                        ProgressCallback progress) {
        return withEditedShapes(editedShapes, apertures, cancellation, progress);
    }

    /** Rebuilds an editor result that may also contain newly defined apertures. */
    public GerberImage withEditedShapes(List<GerberShape> editedShapes, Map<String, Aperture> editedApertures,
                                        CancellationToken cancellation, ProgressCallback progress) {
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
            if (!editedApertures.containsKey(shape.apertureCode())
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
                    ? geometries.get(0) : OverlayNGRobust.union(geometries));
            processed++;
            progress.report(0.60 + 0.38 * processed / byAperture.size());
        }
        cancellation.throwIfCancellationRequested();
        Geometry followResult = factory.createGeometryCollection(follow.toArray(Geometry[]::new));
        progress.report(1);
        return new GerberImage(units, editedApertures, solid, followResult, mergedApertures, editedShapes);
    }

    private static Geometry applyPolarityBatch(Geometry solid, List<Geometry> pending,
                                               boolean clear, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (pending.isEmpty()) {
            return solid;
        }
        Geometry batch = pending.size() == 1 ? pending.get(0) : OverlayNGRobust.union(pending);
        pending.clear();
        cancellation.throwIfCancellationRequested();
        Geometry result = OverlayNGRobust.overlay(solid, batch, clear ? OverlayNG.DIFFERENCE : OverlayNG.UNION);
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
