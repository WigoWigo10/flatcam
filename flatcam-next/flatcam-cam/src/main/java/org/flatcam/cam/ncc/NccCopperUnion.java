package org.flatcam.cam.ncc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Polygon-list preparation matching the GEOS 3.10 Windows reference: capacity-10
 * STR leaf order and a balanced binary union over the flat leaves. Equal-key
 * choices matter to Connect's endpoints, even when the copper area is unchanged.
 * This is not a replacement for general JTS union or a cross-platform GEOS port.
 * No ring rotation, coordinate rounding, native runtime or source mutation.
 */
public final class NccCopperUnion {
    private NccCopperUnion() { }

    public static Geometry union(Geometry source, CancellationToken cancellation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellation, "cancellation");
        var polygons = new ArrayList<Geometry>();
        collect(source, polygons, cancellation, true);
        if (polygons.isEmpty()) return source.getFactory().createPolygon();
        LegacyMsvcSort.sort(polygons, (left, right) -> compareKey(xKey(left), xKey(right)), cancellation);
        int slices = (int) Math.ceil(Math.sqrt(Math.ceil(polygons.size() / 10.0)));
        int capacity = (int) Math.ceil(polygons.size() / (double) slices);
        for (int start = 0; start < polygons.size(); start += capacity) {
            LegacyMsvcSort.sort(polygons.subList(start, Math.min(start + capacity, polygons.size())),
                    (left, right) -> compareKey(yKey(left), yKey(right)), cancellation);
        }
        return binaryUnion(polygons, 0, polygons.size(), cancellation);
    }

    private static double xKey(Geometry polygon) {
        var bounds = polygon.getEnvelopeInternal();
        return bounds.getMinX() + bounds.getMaxX();
    }

    private static double yKey(Geometry polygon) {
        var bounds = polygon.getEnvelopeInternal();
        return bounds.getMinY() + bounds.getMaxY();
    }

    private static int compareKey(double left, double right) {
        // C++ '<' treats signed zero as equal, unlike Double.compare.
        return left < right ? -1 : right < left ? 1 : 0;
    }

    private static void collect(Geometry source, List<Geometry> target, CancellationToken cancellation, boolean repair) {
        cancellation.throwIfCancellationRequested();
        if (source.isEmpty()) return;
        if (source instanceof Polygon polygon) {
            if (polygon.isValid()) target.add(polygon);
            else if (repair) collect(polygon.buffer(0), target, cancellation, false);
            else throw new IllegalArgumentException("O cobre continua invalido apos o reparo para NCC");
        } else if (source instanceof GeometryCollection) {
            for (int i = 0; i < source.getNumGeometries(); i++) collect(source.getGeometryN(i), target, cancellation, repair);
        }
    }

    private static Geometry binaryUnion(List<Geometry> polygons, int start, int end, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (end - start == 1) return polygons.get(start).copy();
        int middle = (start + end) / 2;
        Geometry left = binaryUnion(polygons, start, middle, cancellation);
        Geometry right = binaryUnion(polygons, middle, end, cancellation);
        Geometry result = OverlayNGRobust.overlay(left, right, OverlayNG.UNION);
        cancellation.throwIfCancellationRequested();
        return result;
    }
}
