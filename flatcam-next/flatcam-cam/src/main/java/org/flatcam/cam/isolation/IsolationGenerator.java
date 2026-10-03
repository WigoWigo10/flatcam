package org.flatcam.cam.isolation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ParallelGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
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
 * a rectangle/polygon drawn in the UI. Rest machining evaluates each copper
 * polygon against its neighbors for every requested pass and sends polygons
 * not isolated by the current tool to the next smaller tool.
 *
 * <p>Retains each pass separately as well as a combined view, allowing the UI
 * to create either one Geometry or one Geometry per pass.
 */
public final class IsolationGenerator {

    private static final int QUADRANT_SEGMENTS = 64;

    private IsolationGenerator() {
    }

    public record ToolResult(IsolationParameters parameters, IsolationResult isolation,
                             int remainingCopperCount) {
        public ToolResult(IsolationParameters parameters, IsolationResult isolation) {
            this(parameters, isolation, 0);
        }
    }

    /**
     * Python ToolIsolation.generate_rest_geometry's clearance test: a copper
     * polygon is safe for a pass only if its buffer at twice the isolation
     * offset does not touch another copper polygon. A polygon with no safe
     * pass is retried by the next smaller selected tool.
     */
    public static List<ToolResult> generateRest(String units, Geometry copperGeometry,
                                                List<IsolationParameters> tools,
                                                CancellationToken cancellationToken) {
        return generateRest(units, copperGeometry, tools, true, cancellationToken);
    }

    /**
     * @param forcedRest Python's "Forced Rest" checkbox (defaults checked, {@code tools_iso_force}):
     *                   on the first pass, a copper polygon with more than one hole is rejected for
     *                   the current tool - not just skipped for that pass, the whole polygon is
     *                   retried by the next smaller tool - if the tool was too wide to isolate every
     *                   hole as its own ring (some merged into the exterior or each other). With
     *                   this off, whatever the buffer produces is accepted even if holes were lost.
     */
    public static List<ToolResult> generateRest(String units, Geometry copperGeometry,
                                                List<IsolationParameters> tools, boolean forcedRest,
                                                CancellationToken cancellationToken) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (tools.isEmpty()) throw new IllegalArgumentException("Select at least one isolation tool");
        List<IsolationParameters> ordered = tools.stream()
                .sorted(Comparator.comparingDouble(IsolationParameters::toolDiameter).reversed()).toList();
        GeometryFactory factory = copperGeometry == null ? new GeometryFactory() : copperGeometry.getFactory();
        List<Polygon> remaining = new ArrayList<>();
        if (copperGeometry != null) collectPolygons(copperGeometry, remaining);
        List<ToolResult> results = new ArrayList<>();
        for (IsolationParameters tool : ordered) {
            cancellationToken.throwIfCancellationRequested();
            List<Polygon> retry = new ArrayList<>();
            List<List<LineString>> passes = new ArrayList<>();
            for (int pass = 0; pass < tool.passes(); pass++) passes.add(new ArrayList<>());
            for (int index = 0; index < remaining.size(); index++) {
                cancellationToken.throwIfCancellationRequested();
                Polygon polygon = remaining.get(index);
                boolean isolated = false;
                for (int pass = 0; pass < tool.passes(); pass++) {
                    double offset = tool.toolDiameter()
                            * (pass + 0.5 - pass * tool.overlapFraction());
                    Geometry clearance = polygon.buffer(2.0 * offset, QUADRANT_SEGMENTS);
                    boolean blocked = false;
                    for (int other = 0; other < remaining.size(); other++) {
                        if (other != index && clearance.intersects(remaining.get(other))) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) continue;
                    Geometry buffered = polygon.buffer(offset, QUADRANT_SEGMENTS);
                    if (pass == 0 && forcedRest && polygon.getNumInteriorRing() > 1) {
                        int expectedParts = 1 + polygon.getNumInteriorRing();
                        if (countTopLevelParts(buffered) != expectedParts) {
                            // This tool merged away at least one hole on the very first pass - not
                            // just missing this pass, the whole polygon is unusable with it.
                            break;
                        }
                    }
                    collectRings(buffered, tool.type(), passes.get(pass), factory, cancellationToken);
                    isolated = true;
                }
                if (!isolated) retry.add(polygon);
            }
            List<Geometry> passGeometries = new ArrayList<>();
            List<LineString> all = new ArrayList<>();
            for (List<LineString> pass : passes) {
                all.addAll(pass);
                passGeometries.add(factory.createGeometryCollection(pass.toArray(new LineString[0])));
            }
            results.add(new ToolResult(tool, new IsolationResult(units,
                    factory.createGeometryCollection(all.toArray(new LineString[0])), passGeometries),
                    retry.size()));
            remaining = retry;
        }
        cancellationToken.throwIfCancellationRequested();
        return List.copyOf(results);
    }

    /** Python's "1 + len(temp_geo.interiors)" for a single Polygon, or "len(temp_geo)" for a MultiPolygon. */
    private static int countTopLevelParts(Geometry geometry) {
        if (geometry instanceof Polygon polygon) {
            return 1 + polygon.getNumInteriorRing();
        }
        return geometry.getNumGeometries();
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> polygons) {
        if (geometry instanceof Polygon polygon) {
            polygons.add(polygon);
        } else if (geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++)
                collectPolygons(geometry.getGeometryN(i), polygons);
        }
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

        double[] offsets = new double[params.passes()];
        double largestOffset = 0;
        for (int pass = 0; pass < offsets.length; pass++) {
            offsets[pass] = params.toolDiameter() * (pass + 0.5 - pass * params.overlapFraction());
            largestOffset = Math.max(largestOffset, offsets[pass]);
        }
        // Copper that is further apart than twice the largest offset cannot grow into each other, so each
        // group can be buffered on its own (what JTS' buffer of one huge multipolygon does not scale to)
        // and every (group, pass) job runs in parallel. Same rings as buffering everything at once.
        List<Geometry> groups = ParallelGeometry.separateGroups(copperGeometry, 2 * largestOffset);
        int jobs = groups.size() * offsets.length;
        Geometry[] buffered = new Geometry[jobs];
        IntStream.range(0, jobs).parallel().forEach(job -> {
            cancellationToken.throwIfCancellationRequested();
            buffered[job] = groups.get(job % groups.size()).buffer(offsets[job / groups.size()], QUADRANT_SEGMENTS);
        });

        for (int pass = 0; pass < offsets.length; pass++) {
            cancellationToken.throwIfCancellationRequested();
            List<LineString> passRings = new ArrayList<>();
            for (int group = 0; group < groups.size(); group++) {
                collectRings(buffered[pass * groups.size() + group], params.type(), passRings, geometryFactory,
                        cancellationToken);
            }
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
