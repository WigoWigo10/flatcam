package org.flatcam.cam.ncc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * A "pole of inaccessibility" seed point - the point deepest inside the area, found by
 * grid-refinement search (Mapbox's polylabel algorithm) - the default NCC/Paint Seed
 * policy instead of JTS's {@code Geometry.getInteriorPoint()}. NCC also exposes an
 * explicit {@link NccSeedPolicy#PYTHON} option without removing this stable default.
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
 * <p>This avoids the scan-line threshold above, but does not guarantee a continuous choice of
 * point: equally deep regions can still exchange priority after a small perturbation. It does
 * not reproduce Python/JTS's own point when the two sides' input happens to agree exactly -
 * Seed's path pattern on FX will differ from Python's even on identical input, by design, in
 * exchange for avoiding that specific scan-line sensitivity during independent re-decoding.
 */
final class StableInteriorPoint {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    /** Safety net only - precision-based pruning below converges in a few hundred cells. */
    private static final int MAX_CELLS = 20_000;
    private static final int MAX_INITIAL_CELLS = 256;

    private StableInteriorPoint() {
    }

    /**
     * The best interior point across every polygonal part of {@code area} (erosion can split
     * one polygon into several), or {@code null} if {@code area} has no polygonal part at all.
     * {@code precision} is the refinement target, not a guarantee if the bounded search budget
     * is exhausted. In that case the best interior candidate found so far is returned.
     */
    static Coordinate find(Geometry area, double precision, CancellationToken cancellation) {
        if (!Double.isFinite(precision) || precision <= 0)
            throw new IllegalArgumentException("Seed precision must be finite and positive.");
        cancellation.throwIfCancellationRequested();
        List<Polygon> parts = new ArrayList<>();
        collectPolygons(area, parts, cancellation);
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

    private static void collectPolygons(Geometry geometry, List<Polygon> target, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            target.add(polygon);
            return;
        }
        if (geometry instanceof GeometryCollection) {
            for (int part = 0; part < geometry.getNumGeometries(); part++) {
                collectPolygons(geometry.getGeometryN(part), target, cancellation);
            }
        }
    }

    private record Cell(double x, double y, double halfSize, double distance, double maxPossibleDistance) {
    }

    private static Cell poleOfInaccessibility(Polygon polygon, double precision, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        Envelope envelope = polygon.getEnvelopeInternal();
        if (envelope.isNull() || envelope.getWidth() <= 0 || envelope.getHeight() <= 0) {
            return null;
        }
        double width = envelope.getWidth(), height = envelope.getHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height))
            throw new IllegalArgumentException("Seed bounds must be finite.");
        // The former min(width,height) grid allocated unbounded cells for thin polygons.
        // A coarser square grid still covers the whole envelope; refinement stays best-first.
        double cellSize = Math.max(Math.min(width, height), Math.max(width, height) / MAX_INITIAL_CELLS);
        double half = cellSize / 2;
        if (half <= 0) {
            return null;
        }

        // Best-first: always explore the cell that could possibly contain the deepest point next.
        PriorityQueue<Cell> queue = new PriorityQueue<>(
                Comparator.comparingDouble(Cell::maxPossibleDistance).reversed());
        int columns = Math.min(MAX_INITIAL_CELLS, (int) Math.ceil(width / cellSize));
        int rows = Math.min(MAX_INITIAL_CELLS, (int) Math.ceil(height / cellSize));
        // Integer indices also avoid x += cellSize stalling at large coordinate offsets.
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                cancellation.throwIfCancellationRequested();
                queue.add(cellAt(polygon, envelope.getMinX() + (column + 0.5) * cellSize,
                        envelope.getMinY() + (row + 0.5) * cellSize, half));
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
        // Guarantee an interior fallback even if all grid centres and the centroid lie outside.
        Point interior = polygon.getInteriorPoint();
        if (!interior.isEmpty()) {
            Cell fallback = cellAt(polygon, interior.getX(), interior.getY(), 0);
            if (fallback.distance > best.distance) best = fallback;
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
            if (childHalf <= 0 || (cell.x - childHalf == cell.x && cell.x + childHalf == cell.x
                    && cell.y - childHalf == cell.y && cell.y + childHalf == cell.y)) {
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
