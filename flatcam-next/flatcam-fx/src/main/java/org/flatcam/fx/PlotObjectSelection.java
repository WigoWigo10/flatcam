package org.flatcam.fx;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/** Hit testing for visible project objects in the Plot Area, independent of JavaFX events. */
final class PlotObjectSelection {

    record Layer<T>(T owner, Geometry geometry) {
        Layer {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(geometry);
        }
    }

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private PlotObjectSelection() {
    }

    /** Hit objects in top-to-bottom draw order; multiple CNC layers count once. */
    static <T> List<T> at(List<Layer<T>> layers, double x, double y, double tolerance) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
        Set<T> hits = new LinkedHashSet<>();
        for (int index = layers.size() - 1; index >= 0; index--) {
            Layer<T> layer = layers.get(index);
            Geometry geometry = layer.geometry();
            if (geometry.isEmpty()) {
                continue;
            }
            Envelope bounds = new Envelope(geometry.getEnvelopeInternal());
            bounds.expandBy(tolerance);
            if (bounds.contains(x, y) && geometry.isWithinDistance(point, tolerance)) {
                hits.add(layer.owner());
            }
        }
        return List.copyOf(hits);
    }

    static <T> T topmostAt(List<Layer<T>> layers, double x, double y, double tolerance) {
        List<T> hits = at(layers, x, y, tolerance);
        return hits.isEmpty() ? null : hits.get(0);
    }

    /** Repeated clicks cycle through overlapping objects, like the Python Plot Area. */
    static <T> T nextAt(List<Layer<T>> layers, double x, double y, double tolerance, T current) {
        List<T> hits = at(layers, x, y, tolerance);
        if (hits.isEmpty()) {
            return null;
        }
        int currentIndex = hits.indexOf(current);
        return hits.get(currentIndex < 0 ? 0 : (currentIndex + 1) % hits.size());
    }

    /** An object's envelope combines all its visible layers (notably CNC cut and travel). */
    static <T> Map<T, Envelope> boundsByOwner(List<Layer<T>> layers) {
        Map<T, Envelope> bounds = new LinkedHashMap<>();
        for (Layer<T> layer : layers) {
            if (layer.geometry().isEmpty()) {
                continue;
            }
            bounds.computeIfAbsent(layer.owner(), ignored -> new Envelope())
                    .expandToInclude(layer.geometry().getEnvelopeInternal());
        }
        return bounds;
    }

    /** Left-to-right fully encloses; right-to-left touches, matching the legacy canvas. */
    static <T> List<T> inBox(List<Layer<T>> layers, double pressX, double pressY,
                             double releaseX, double releaseY) {
        Envelope box = new Envelope(pressX, releaseX, pressY, releaseY);
        boolean enclosing = releaseX >= pressX;
        return boundsByOwner(layers).entrySet().stream()
                .filter(entry -> enclosing ? box.contains(entry.getValue()) : box.intersects(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
