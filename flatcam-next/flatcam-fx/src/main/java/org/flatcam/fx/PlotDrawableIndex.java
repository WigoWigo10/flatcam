package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        this.geometry = geometry;
        List<Part> flattened = new ArrayList<>();
        collect(geometry, flattened);
        parts = List.copyOf(flattened);
        bounds = geometry == null ? new Envelope() : geometry.getEnvelopeInternal();
        if (parts.size() >= TREE_THRESHOLD) {
            STRtree built = new STRtree();
            for (Part part : parts) {
                built.insert(part.bounds(), part);
            }
            built.build();
            tree = built;
        } else {
            tree = null;
        }
    }

    Geometry geometry() {
        return geometry;
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

    private static void collect(Geometry geometry, List<Part> parts) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                collect(collection.getGeometryN(i), parts);
            }
        } else {
            parts.add(new Part(parts.size(), geometry, geometry.getEnvelopeInternal(), segmentsOf(geometry),
                    lengthOf(geometry)));
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

    private static double lengthOf(Geometry geometry) {
        if (geometry instanceof org.locationtech.jts.geom.Polygon polygon) {
            return polygon.getExteriorRing().getLength();
        }
        return geometry.getLength();
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
