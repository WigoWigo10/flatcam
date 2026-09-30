package org.flatcam.cam.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Helpers that split big JTS jobs into independent spatial groups so they run on every core
 * and stay small enough for JTS' overlay and buffer code, which slow down sharply on a single
 * huge input.
 */
public final class ParallelGeometry {

    private static final int PARALLEL_THRESHOLD = 256;

    private ParallelGeometry() {
    }

    /**
     * Union of {@code shapes} ({@code shapes} must not be empty): shapes whose bounding boxes do not
     * touch cannot overlap, so each connected group is unioned on its own thread and the results are
     * just collected. Equals a single union of everything.
     */
    public static Geometry union(List<Geometry> shapes) {
        if (shapes.size() < PARALLEL_THRESHOLD) {
            return OverlayNGRobust.union(shapes);
        }
        return unionGrouped(shapes);
    }

    /** Like {@link #union} for any number of shapes, however few: for a handful of big, far-apart copies. */
    public static Geometry unionGrouped(List<Geometry> shapes) {
        if (shapes.size() < 2) {
            return shapes.get(0);
        }
        GeometryFactory factory = shapes.get(0).getFactory();
        List<Geometry> groups = separateGroups(factory.buildGeometry(shapes), 0);
        if (groups.size() < 2) {
            return OverlayNGRobust.union(shapes);
        }
        List<Geometry> unions = groups.parallelStream()
                .map(group -> group.getNumGeometries() == 1 ? group : OverlayNGRobust.union(flatten(group)))
                .toList();
        List<Polygon> polygons = new ArrayList<>();
        for (Geometry union : unions) {
            collectPolygons(union, polygons);
        }
        return factory.createMultiPolygon(polygons.toArray(new Polygon[0]));
    }

    private static List<Geometry> flatten(Geometry group) {
        List<Geometry> parts = new ArrayList<>(group.getNumGeometries());
        for (int i = 0; i < group.getNumGeometries(); i++) {
            Geometry part = group.getGeometryN(i);
            if (part.getClass() == org.locationtech.jts.geom.GeometryCollection.class) {
                parts.addAll(flatten(part));
            } else {
                parts.add(part);
            }
        }
        return parts;
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> target) {
        if (geometry instanceof Polygon polygon) {
            if (!polygon.isEmpty()) {
                target.add(polygon);
            }
            return;
        }
        // MultiPolygons and (possibly nested) collections: a project may hand over a collection of multipolygons.
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            collectPolygons(geometry.getGeometryN(i), target);
        }
    }

    /**
     * Splits {@code geometry} into groups whose parts can touch after growing by {@code gap / 2} each:
     * parts whose bounding boxes, each widened by half the gap, overlap end up in the same group
     * (a conservative test, so no two groups can ever merge).
     */
    public static List<Geometry> separateGroups(Geometry geometry, double gap) {
        List<Geometry> parts = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.size() < 2) {
            return List.of(geometry);
        }
        int[] root = new int[parts.size()];
        for (int i = 0; i < root.length; i++) {
            root[i] = i;
        }
        STRtree index = new STRtree();
        List<Envelope> widened = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Envelope envelope = new Envelope(parts.get(i).getEnvelopeInternal());
            envelope.expandBy(gap / 2);
            widened.add(envelope);
            index.insert(envelope, i);
        }
        for (int i = 0; i < parts.size(); i++) {
            @SuppressWarnings("unchecked")
            List<Integer> near = index.query(widened.get(i));
            for (int other : near) {
                int a = find(root, i);
                int b = find(root, other);
                if (a != b) {
                    root[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        Map<Integer, List<Geometry>> byRoot = new LinkedHashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            byRoot.computeIfAbsent(find(root, i), key -> new ArrayList<>()).add(parts.get(i));
        }
        GeometryFactory factory = geometry.getFactory();
        List<Geometry> groups = new ArrayList<>(byRoot.size());
        for (List<Geometry> members : byRoot.values()) {
            groups.add(members.size() == 1 ? members.get(0) : factory.buildGeometry(members));
        }
        return groups;
    }

    private static int find(int[] root, int index) {
        while (root[index] != index) {
            root[index] = root[root[index]];
            index = root[index];
        }
        return index;
    }
}
