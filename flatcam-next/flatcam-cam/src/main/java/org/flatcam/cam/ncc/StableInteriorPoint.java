package org.flatcam.cam.ncc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * A "pole of inaccessibility" seed point - the point deepest inside the area, found by
 * grid-refinement search (Mapbox's polylabel algorithm) - used by NCC/Paint Seed instead
 * of JTS's {@code Geometry.getInteriorPoint()}.
 *
 * <p>This is a deliberate difference from FlatCAM Python, not an implementation gap: Python's
 * own seed point ({@code Polygon.representative_point()}) and JTS's are backed by the very
 * same scan-line algorithm in both Shapely/GEOS and JTS (GEOS's {@code GEOSPointOnSurface}
 * is a thin wrapper over its own {@code InteriorPointArea}). That algorithm picks a scan-line
 * Y midway between whichever two polygon vertices most tightly straddle the vertical centre,
 * then the midpoint of that scan-line's widest interior crossing - a choice that is a
 * discontinuous function of the input: a vertex moving by 1e-12 across the centre threshold
 * can swap which vertex bounds the scan-line and move the resulting point by orders of
 * magnitude more than the perturbation (reproduced in isolation in INVESTIGACAO_CAM.md, and
 * observed on a real board's independently-decoded Gerber, where it shifted the Seed point -
 * and so Seed's whole ring pattern - by over 0.4mm with no deliberate change on either side).
 * Fixing NCC/Paint Standard and Lines's buffer kernel did not fix this; a different, explicitly
 * stable choice of starting point does.
 *
 * <p>The distance-to-boundary function this searches is continuous in the polygon's vertices,
 * so it has no equivalent discontinuity: a small change to the input moves the found point by
 * a correspondingly small amount, not an unrelated jump. The practical cost is that this does
 * not reproduce Python/JTS's own point when the two sides' input happens to agree exactly -
 * Seed's path pattern on FX will differ from Python's even on identical input, by design, in
 * exchange for being stable under exactly the kind of independent re-decoding every real
 * comparison across the two toolchains involves.
 */
final class StableInteriorPoint {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    /** Safety net only - precision-based pruning below converges in a few hundred cells. */
    private static final int MAX_CELLS = 20_000;

    private StableInteriorPoint() {
    }

    /**
     * The best interior point across every polygonal part of {@code area} (erosion can split
     * one polygon into several), or {@code null} if {@code area} has no polygonal part at all.
     * {@code precision} bounds how exactly the true deepest point is approximated; pruning stops
     * refining a region once it provably cannot beat the best point found by more than this.
     */
    static Coordinate find(Geometry area, double precision, CancellationToken cancellation) {
        List<Polygon> parts = new ArrayList<>();
        collectPolygons(area, parts);
        Coordinate best = null;
        double bestDistance = Double.NEGATIVE_INFINITY;
        for (Polygon polygon : parts) {
            Cell candidate = poleOfInaccessibility(polygon, precision, cancellation);
            if (candidate != null && candidate.distance > bestDistance) {
                bestDistance = candidate.distance;
                best = new Coordinate(candidate.x, candidate.y);
            }
        }
        return best;
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> target) {
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            target.add(polygon);
            return;
        }
        for (int part = 0; part < geometry.getNumGeometries(); part++) {
            collectPolygons(geometry.getGeometryN(part), target);
        }
    }

    private record Cell(double x, double y, double halfSize, double distance, double maxPossibleDistance) {
    }

    private static Cell poleOfInaccessibility(Polygon polygon, double precision, CancellationToken cancellation) {
        Envelope envelope = polygon.getEnvelopeInternal();
        if (envelope.isNull() || envelope.getWidth() <= 0 || envelope.getHeight() <= 0) {
            return null;
        }
        double cellSize = Math.min(envelope.getWidth(), envelope.getHeight());
        double half = cellSize / 2;
        if (half <= 0) {
            return null;
        }

        // Best-first: always explore the cell that could possibly contain the deepest point next.
        PriorityQueue<Cell> queue = new PriorityQueue<>(
                Comparator.comparingDouble(Cell::maxPossibleDistance).reversed());
        for (double x = envelope.getMinX(); x < envelope.getMaxX(); x += cellSize) {
            for (double y = envelope.getMinY(); y < envelope.getMaxY(); y += cellSize) {
                queue.add(cellAt(polygon, x + half, y + half, half));
            }
        }

        Cell best = cellAt(polygon, envelope.getMinX() + envelope.getWidth() / 2,
                envelope.getMinY() + envelope.getHeight() / 2, 0);
        Point centroid = polygon.getCentroid();
        if (!centroid.isEmpty()) {
            Cell centroidCell = cellAt(polygon, centroid.getX(), centroid.getY(), 0);
            if (centroidCell.distance > best.distance) {
                best = centroidCell;
            }
        }

        int processed = 0;
        while (!queue.isEmpty() && processed++ < MAX_CELLS) {
            cancellation.throwIfCancellationRequested();
            Cell cell = queue.poll();
            if (cell.distance > best.distance) {
                best = cell;
            }
            // No remaining cell - including this one's children - can beat the current best
            // by more than the requested precision: nothing left to gain from subdividing further.
            if (cell.maxPossibleDistance - best.distance <= precision) {
                continue;
            }
            double childHalf = cell.halfSize / 2;
            if (childHalf <= 0) {
                continue;
            }
            queue.add(cellAt(polygon, cell.x - childHalf, cell.y - childHalf, childHalf));
            queue.add(cellAt(polygon, cell.x + childHalf, cell.y - childHalf, childHalf));
            queue.add(cellAt(polygon, cell.x - childHalf, cell.y + childHalf, childHalf));
            queue.add(cellAt(polygon, cell.x + childHalf, cell.y + childHalf, childHalf));
        }
        return best;
    }

    private static Cell cellAt(Polygon polygon, double x, double y, double halfSize) {
        double distance = signedDistanceToBoundary(polygon, x, y);
        double maxPossibleDistance = distance + halfSize * Math.sqrt(2);
        return new Cell(x, y, halfSize, distance, maxPossibleDistance);
    }

    /** Positive inside the polygon, negative outside - the quantity polylabel-style search maximizes. */
    private static double signedDistanceToBoundary(Polygon polygon, double x, double y) {
        Point point = FACTORY.createPoint(new Coordinate(x, y));
        double distance = polygon.getBoundary().distance(point);
        return polygon.contains(point) ? distance : -distance;
    }
}
