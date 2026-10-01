package org.flatcam.cam.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolOptimal.py: the smallest gap between the copper features of a Gerber, how many pairs of features sit at
 * that gap, where, and every other gap that occurs. The copper is merged into its separate pieces and the distance
 * between every two pieces is measured; distances are rounded to {@code precision} decimals and grouped.
 *
 * <p>Differences from Python: the pairs are measured in parallel with an indexed distance (Python does them one by one
 * with {@code geo.distance}), and the answer to "only one polygon" is an {@link IllegalArgumentException} with the same
 * explanation.
 */
public final class MinimumDistance {

    /** The two nearest points of a pair of features (rounded to the precision). */
    public record Pair(Coordinate first, Coordinate second) {

        /** The middle of the gap: where the view jumps to. */
        public Coordinate middle() {
            return new Coordinate((first.x + second.x) / 2, (first.y + second.y) / 2);
        }
    }

    /**
     * @param minimum          the smallest gap
     * @param minimumLocations every pair of features at that gap
     * @param others           the other gaps, ascending, each with its pairs
     * @param features         how many separate pieces of copper were compared
     */
    public record Result(double minimum, List<Pair> minimumLocations, NavigableMap<Double, List<Pair>> others,
                         int features) {
        public int frequency() {
            return minimumLocations.size();
        }
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private MinimumDistance() {
    }

    public static Result find(Geometry copper, int precision, CancellationToken cancellation, ProgressCallback progress) {
        if (precision < 0 || precision > 10) {
            throw new IllegalArgumentException("A precisao deve estar entre 0 e 10 casas");
        }
        if (copper == null || copper.isEmpty()) {
            throw new IllegalArgumentException("O Gerber nao tem cobre");
        }
        List<Polygon> features = new ArrayList<>();
        Geometry merged = OverlayNGRobust.union(parts(copper));
        for (int i = 0; i < merged.getNumGeometries(); i++) {
            if (merged.getGeometryN(i) instanceof Polygon polygon && !polygon.isEmpty()) {
                features.add(polygon);
            }
        }
        if (features.size() < 2) {
            throw new IllegalArgumentException("O Gerber tem um unico poligono: nao ha distancias entre elementos");
        }
        double scale = Math.pow(10, precision);
        int count = features.size();
        long totalPairs = (long) count * (count - 1) / 2;
        AtomicLong done = new AtomicLong();
        ConcurrentLinkedQueue<Entry> found = new ConcurrentLinkedQueue<>();
        IntStream.range(0, count - 1).parallel().forEach(i -> {
            cancellation.throwIfCancellationRequested();
            IndexedFacetDistance from = new IndexedFacetDistance(features.get(i));
            List<Entry> local = new ArrayList<>(count - i - 1);
            for (int j = i + 1; j < count; j++) {
                Coordinate[] nearest = from.nearestPoints(features.get(j));
                double gap = nearest[0].distance(nearest[1]);
                local.add(new Entry(Math.round(gap * scale) / scale,
                        new Pair(round(nearest[0], scale), round(nearest[1], scale))));
            }
            found.addAll(local);
            long soFar = done.addAndGet(count - i - 1);
            progress.report(Math.min(0.95, (double) soFar / totalPairs));
        });
        cancellation.throwIfCancellationRequested();
        TreeMap<Double, List<Pair>> byDistance = new TreeMap<>();
        for (Entry entry : found) {
            byDistance.computeIfAbsent(entry.distance(), key -> new ArrayList<>()).add(entry.pair());
        }
        Map.Entry<Double, List<Pair>> smallest = byDistance.pollFirstEntry();
        progress.report(1.0);
        return new Result(smallest.getKey(), List.copyOf(smallest.getValue()), byDistance, count);
    }

    private record Entry(double distance, Pair pair) {
    }

    private static Coordinate round(Coordinate point, double scale) {
        return new Coordinate(Math.round(point.x * scale) / scale, Math.round(point.y * scale) / scale);
    }

    private static List<Geometry> parts(Geometry geometry) {
        List<Geometry> parts = new ArrayList<>();
        collect(geometry, parts);
        return parts;
    }

    private static void collect(Geometry geometry, List<Geometry> parts) {
        if (geometry instanceof org.locationtech.jts.geom.GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collect(geometry.getGeometryN(i), parts);
            }
        } else if (!geometry.isEmpty()) {
            parts.add(geometry);
        }
    }

    /** A segment between the two nearest points, for drawing the gap. */
    public static Geometry segment(Pair pair) {
        return FACTORY.createLineString(new Coordinate[] {pair.first(), pair.second()});
    }
}
