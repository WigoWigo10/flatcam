package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;

/** Frozen Connect from 47f32d8a; historical ordering/predicate oracle, not a second production engine. */
final class LegacyConnectOracle {
    private static final int QUADRANT_SEGMENTS=64;
    static List<LineString> connect(List<LineString> source, Geometry safeArea,
                                             double toolDiameter, GeometryFactory factory,
                                             CancellationToken cancellation) {
        if (source.size() < 2 || safeArea == null || safeArea.isEmpty()) {
            return source;
        }
        double maxWalk = 10 * toolDiameter;
        List<LineString> remaining = new ArrayList<>(source);
        List<LineString> connected = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        // Legacy paint_connect starts at the path nearest (0,0), preserving its
        // direction even when its last endpoint was nearest.
        int firstIndex = 0;
        double firstDistance = Double.POSITIVE_INFINITY;
        Coordinate origin = new Coordinate(0, 0);
        for (int i = 0; i < remaining.size(); i++) {
            cancellation.throwIfCancellationRequested();
            Coordinate[] points = remaining.get(i).getCoordinates();
            double distance = Math.min(points[0].distance(origin), points[points.length - 1].distance(origin));
            if (distance < firstDistance) { firstIndex = i; firstDistance = distance; }
        }
        LineString first = remaining.remove(firstIndex);
        addCoordinates(current, first.getCoordinates(), false);

        while (!remaining.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            Coordinate end = current.get(current.size() - 1);
            int bestIndex = 0;
            boolean reverse = false;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < remaining.size(); i++) {
                Coordinate[] coordinates = remaining.get(i).getCoordinates();
                double toStart = end.distance(coordinates[0]);
                double toEnd = end.distance(coordinates[coordinates.length - 1]);
                if (toStart < bestDistance) {
                    bestDistance = toStart;
                    bestIndex = i;
                    reverse = false;
                }
                if (toEnd < bestDistance) {
                    bestDistance = toEnd;
                    bestIndex = i;
                    reverse = true;
                }
            }
            LineString next = remaining.remove(bestIndex);
            Coordinate[] coordinates = next.getCoordinates();
            if (reverse) {
                coordinates = reversed(coordinates);
            }
            boolean walkable = bestDistance < maxWalk
                    && factory.createLineString(new Coordinate[]{end, coordinates[0]})
                            .buffer(toolDiameter / 2.0, QUADRANT_SEGMENTS).within(safeArea);
            if (walkable) {
                addCoordinates(current, coordinates, true);
            } else {
                connected.add(factory.createLineString(current.toArray(new Coordinate[0])));
                current = new ArrayList<>();
                addCoordinates(current, coordinates, false);
            }
        }
        if (current.size() >= 2) {
            connected.add(factory.createLineString(current.toArray(new Coordinate[0])));
        }
        return connected;
    }

    private static void addCoordinates(List<Coordinate> target, Coordinate[] coordinates, boolean skipDuplicateFirst) {
        int start = skipDuplicateFirst && !target.isEmpty()
                && target.get(target.size() - 1).equals2D(coordinates[0]) ? 1 : 0;
        for (int i = start; i < coordinates.length; i++) {
            target.add(new Coordinate(coordinates[i]));
        }
    }

    private static Coordinate[] reversed(Coordinate[] source) {
        Coordinate[] reversed = new Coordinate[source.length];
        for (int i = 0; i < source.length; i++) {
            reversed[i] = new Coordinate(source[source.length - 1 - i]);
        }
        return reversed;
    }

}
