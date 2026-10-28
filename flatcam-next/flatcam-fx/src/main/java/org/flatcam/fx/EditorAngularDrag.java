package org.flatcam.fx;

import org.locationtech.jts.geom.Coordinate;

/** Bounded scalar-only interaction; geometry is transformed once after release, in a worker. */
final class EditorAngularDrag {
    private final Coordinate pivot;
    private Double lastAngle;
    private double clockwiseDegrees;

    EditorAngularDrag(Coordinate pivot) {
        if (pivot==null || !Double.isFinite(pivot.x) || !Double.isFinite(pivot.y))
            throw new IllegalArgumentException(I18n.text("ui.e7ec788f3658"));
        this.pivot = new Coordinate(pivot);
    }

    boolean begin(Coordinate point, double minimumRadius) {
        lastAngle = null;
        clockwiseDegrees = 0;
        if (!valid(point) || pivot.distance(point) <= minimumRadius) return false;
        lastAngle = Math.atan2(point.y - pivot.y, point.x - pivot.x);
        return true;
    }

    boolean update(Coordinate point, double minimumRadius) {
        if (lastAngle == null || !valid(point) || pivot.distance(point) <= minimumRadius) return false;
        double next = Math.atan2(point.y - pivot.y, point.x - pivot.x);
        double delta = Math.atan2(Math.sin(next - lastAngle), Math.cos(next - lastAngle));
        clockwiseDegrees -= Math.toDegrees(delta);
        lastAngle = next;
        return true;
    }

    double degrees() { return clockwiseDegrees; }
    boolean started() { return lastAngle != null; }
    private static boolean valid(Coordinate point) { return point!=null && Double.isFinite(point.x) && Double.isFinite(point.y); }
}
