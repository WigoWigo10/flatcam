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

    record Part(int index, Geometry geometry, Envelope bounds) {
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
            parts.add(new Part(parts.size(), geometry, geometry.getEnvelopeInternal()));
        }
    }
}
