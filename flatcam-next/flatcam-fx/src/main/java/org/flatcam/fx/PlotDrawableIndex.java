package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.index.strtree.STRtree;

/**
 * Immutable display-only index for a plot layer. A zoomed-in redraw visits only
 * shapes in the viewport instead of walking every shape in a dense Gerber or
 * CNC Job. Original part indices keep draw order and multi-color stable.
 */
final class PlotDrawableIndex {
    private static final int TREE_THRESHOLD = 128;

    /** {@code segments} and {@code length} (world units) feed the density level of detail. */
    record Part(int index, Geometry geometry, Envelope bounds, int segments, double length) {
    }

    private final Geometry geometry;
    private final List<Part> parts;
    private final Envelope bounds;
    private final STRtree tree;

    PlotDrawableIndex(Geometry geometry) {
        this(geometry, null, CancellationToken.NONE);
    }

    /** Unchanged immutable parts retain their bounds/length after an editor deletion or addition. */
    PlotDrawableIndex(Geometry geometry, PlotDrawableIndex previous, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        this.geometry = geometry;
        Map<Geometry, Part> reusable = new IdentityHashMap<>();
        if (previous != null) {
            for (Part part : previous.parts) {
                cancellation.throwIfCancellationRequested();
                reusable.put(part.geometry(), part);
            }
        }
        List<Part> flattened = new ArrayList<>();
        collect(geometry, flattened, reusable, cancellation);
        parts = List.copyOf(flattened);
        bounds = new Envelope();
        for (Part part : parts) bounds.expandToInclude(part.bounds());
        if (parts.size() >= TREE_THRESHOLD) {
            STRtree built = new STRtree();
            for (Part part : parts) {
                cancellation.throwIfCancellationRequested();
                built.insert(part.bounds(), part);
            }
            built.build();
            tree = built;
        } else {
            tree = null;
        }
        cancellation.throwIfCancellationRequested();
    }

    Geometry geometry() {
        return geometry;
    }

    boolean intersects(Envelope viewport) {
        return bounds.intersects(viewport);
    }

    List<Part> visibleParts(Envelope viewport) {
        if (viewport == null || viewport.contains(bounds)) {
            return parts;
        }
        List<Part> visible = new ArrayList<>();
        if (tree == null) {
            for (Part part : parts) {
                if (part.bounds().intersects(viewport)) {
                    visible.add(part);
                }
            }
        } else {
            List<?> candidates = tree.query(viewport);
            // When most shapes are visible, a linear walk is cheaper than sorting
            // the R-tree result back into the layer's original paint order.
            if (candidates.size() > parts.size() / 3) {
                for (Part part : parts) {
                    if (part.bounds().intersects(viewport)) {
                        visible.add(part);
                    }
                }
                return visible;
            }
            for (Object candidate : candidates) {
                Part part = (Part) candidate;
                if (part.bounds().intersects(viewport)) {
                    visible.add(part);
                }
            }
            visible.sort(Comparator.comparingInt(Part::index));
        }
        return visible;
    }

    private static void collect(Geometry geometry, List<Part> parts, Map<Geometry, Part> reusable,
                                CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (geometry == null) {
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                collect(collection.getGeometryN(i), parts, reusable, cancellation);
            }
        } else if (!geometry.isEmpty()) {
            Part old = reusable.get(geometry);
            double length = old == null ? lengthOf(geometry, cancellation) : old.length();
            parts.add(new Part(parts.size(), geometry, old == null ? geometry.getEnvelopeInternal() : old.bounds(),
                    old == null ? segmentsOf(geometry) : old.segments(), length));
        }
    }

    /** What the Canvas would stroke for this part: a stroke-only layer draws a polygon's exterior ring. */
    private static int segmentsOf(Geometry geometry) {
        if (geometry instanceof org.locationtech.jts.geom.LineString line) {
            return Math.max(0, line.getNumPoints() - 1);
        }
        if (geometry instanceof org.locationtech.jts.geom.Polygon polygon) {
            return Math.max(0, polygon.getExteriorRing().getNumPoints() - 1);
        }
        return 1;
    }

    private static double lengthOf(Geometry geometry, CancellationToken cancellation) {
        CoordinateSequence sequence = switch (geometry) {
            case org.locationtech.jts.geom.Polygon polygon -> polygon.getExteriorRing().getCoordinateSequence();
            case org.locationtech.jts.geom.LineString line -> line.getCoordinateSequence();
            default -> null;
        };
        if (sequence == null) return geometry.getLength();
        double length = 0;
        for (int i = 1; i < sequence.size(); i++) {
            if ((i & 1023) == 0) cancellation.throwIfCancellationRequested();
            length += Math.hypot(sequence.getX(i) - sequence.getX(i - 1),
                    sequence.getY(i) - sequence.getY(i - 1));
        }
        return length;
    }

    /** Segment count and total length of the parts that intersect {@code viewport} ({@code null} = all). */
    double[] visibleLoad(Envelope viewport) {
        long segments = 0;
        double length = 0;
        for (Part part : visibleParts(viewport)) {
            segments += part.segments();
            length += part.length();
        }
        return new double[] {segments, length};
    }
}
