package org.flatcam.cam.isolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * Ports appTools/ToolIsolation.py + camlib.py's Gerber.isolation_geometry():
 * grows the copper outward by a per-pass offset and keeps only the resulting
 * boundary rings - the isolation toolpath runs just outside the copper, not
 * on it. Mapped against the legacy source (not just observed behavior):
 *
 * <ul>
 *   <li>Per-pass offset: {@code toolDiameter * (pass + 0.5 - pass * overlapFraction)},
 *       pass 0-based - camlib.py's {@code iso_offset = tool_dia * ((2*i+1)/2) - i*overlap*tool_dia},
 *       algebraically the same formula. Each pass is measured from the
 *       original copper edge, not incrementally from the previous pass.</li>
 *   <li>Buffer join style: round, resolution 64 - matches
 *       {@code geo_steps_per_circle} (default 64) fed into Shapely's
 *       {@code buffer(offset, resolution=64, join_style=1)}; JTS's
 *       quadrantSegments has the same meaning as Shapely's resolution
 *       (segments per quarter circle), and round is JTS's buffer() default
 *       for the 2-arg overload used here.</li>
 *   <li>Result is the buffer's boundary (exterior/interior rings per
 *       {@link IsolationType}), not the filled buffered polygon - the router
 *       follows a path, it doesn't mill an area.</li>
 * </ul>
 *
 * <p>Exception areas are supported through a filled Geometry mask, including
 * a rectangle/polygon drawn in the UI. Rest-machining (per-polygon self-intersection checks to
 * detect copper too tight for the chosen tool, escalating to a smaller
 * tool) remain future work.
 *
 * <p>Retains each pass separately as well as a combined view, allowing the UI
 * to create either one Geometry or one Geometry per pass.
 */
public final class IsolationGenerator {

    private static final int QUADRANT_SEGMENTS = 64;

    private IsolationGenerator() {
    }

    /** Python's follow mode returns the Gerber's unbuffered follow_geometry unchanged. */
    public static IsolationResult generateFollow(String units, Geometry followGeometry,
                                                 CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (followGeometry == null || followGeometry.isEmpty()) {
            return new IsolationResult(units, new GeometryFactory().createGeometryCollection(), List.of());
        }
        return new IsolationResult(units, followGeometry, List.of(followGeometry));
    }

    /** Removes paths inside a filled Geometry mask while retaining pass boundaries. */
    public static IsolationResult excludeArea(IsolationResult source, Geometry mask,
                                              CancellationToken cancellationToken) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (mask == null || mask.isEmpty()) {
            return source;
        }
        if (mask.getDimension() != 2 || !mask.isValid()) {
            throw new IllegalArgumentException("Exception area must be valid, filled Geometry");
        }
        GeometryFactory factory = source.geometry().getFactory();
        List<Geometry> passes = new ArrayList<>();
        List<Geometry> paths = new ArrayList<>();
        for (Geometry pass : source.passGeometries()) {
            cancellationToken.throwIfCancellationRequested();
            Geometry clipped = pass.difference(mask);
            passes.add(clipped);
            for (int i = 0; i < clipped.getNumGeometries(); i++) {
                paths.add(clipped.getGeometryN(i));
            }
        }
        cancellationToken.throwIfCancellationRequested();
        return new IsolationResult(source.units(),
                paths.isEmpty() ? factory.createGeometryCollection() : factory.buildGeometry(paths), passes);
    }

    public static IsolationResult generate(String units, Geometry copperGeometry, IsolationParameters params) {
        return generate(units, copperGeometry, params, CancellationToken.none());
    }

    public static IsolationResult generate(String units, Geometry copperGeometry, IsolationParameters params,
                                           CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (copperGeometry == null || copperGeometry.isEmpty()) {
            return new IsolationResult(units, new GeometryFactory().createGeometryCollection(), List.of());
        }

        GeometryFactory geometryFactory = copperGeometry.getFactory();
        List<LineString> rings = new ArrayList<>();
        List<Geometry> passGeometries = new ArrayList<>();

        for (int pass = 0; pass < params.passes(); pass++) {
            cancellationToken.throwIfCancellationRequested();
            double offset = params.toolDiameter() * (pass + 0.5 - pass * params.overlapFraction());
            Geometry buffered = copperGeometry.buffer(offset, QUADRANT_SEGMENTS);
            cancellationToken.throwIfCancellationRequested();
            List<LineString> passRings = new ArrayList<>();
            collectRings(buffered, params.type(), passRings, geometryFactory, cancellationToken);
            rings.addAll(passRings);
            passGeometries.add(geometryFactory.createGeometryCollection(passRings.toArray(new LineString[0])));
        }

        cancellationToken.throwIfCancellationRequested();
        Geometry combined = geometryFactory.createGeometryCollection(rings.toArray(new LineString[0]));
        return new IsolationResult(units, combined, passGeometries);
    }

    private static void collectRings(Geometry buffered, IsolationType type, List<LineString> rings,
                                     GeometryFactory geometryFactory, CancellationToken cancellationToken) {
        int count = buffered.getNumGeometries();
        for (int i = 0; i < count; i++) {
            cancellationToken.throwIfCancellationRequested();
            if (!(buffered.getGeometryN(i) instanceof Polygon polygon)) {
                continue;
            }
            if (type != IsolationType.INTERIOR) {
                rings.add(geometryFactory.createLineString(polygon.getExteriorRing().getCoordinateSequence()));
            }
            if (type != IsolationType.EXTERIOR) {
                for (int r = 0; r < polygon.getNumInteriorRing(); r++) {
                    cancellationToken.throwIfCancellationRequested();
                    rings.add(geometryFactory.createLineString(polygon.getInteriorRingN(r).getCoordinateSequence()));
                }
            }
        }
    }
}
