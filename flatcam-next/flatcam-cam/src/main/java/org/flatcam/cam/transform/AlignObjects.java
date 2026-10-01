package org.flatcam.cam.transform;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * appTools/ToolAlignObjects.py: moves (and, with two point pairs, rotates) one object so that chosen pads or drills
 * of it land on chosen pads or drills of another. The points are clicked in this order: where on the moved object,
 * where on the reference object, and for the two-point mode the same pair again.
 *
 * <p>Difference from Python: the rotation comes from {@code atan2} of the two vectors, so a vertical vector or a
 * turn past 90 degrees works (Python's {@code atan(dy / dx)} fails on {@code dx = 0} and picks the wrong quadrant).
 */
public final class AlignObjects {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private AlignObjects() {
    }

    /** The centre of the flashed pad of {@code gerber} under {@code click}, or null when there is none. */
    public static Coordinate padCenterAt(GerberImage gerber, Coordinate click) {
        Point point = FACTORY.createPoint(click);
        Envelope probe = new Envelope(click);
        for (GerberShape shape : gerber.shapes()) {
            if (shape.clear() || !(shape.followGeometry() instanceof Point)) {
                continue;
            }
            if (shape.geometry().getEnvelopeInternal().intersects(probe) && shape.geometry().contains(point)) {
                return shape.geometry().getCentroid().getCoordinate();
            }
        }
        return null;
    }

    /**
     * The centre of the drill hole (or slot) of {@code excellon} that {@code click} falls in, widened by
     * {@code extraReach} (world units) so a click near a small hole still finds it; the nearest one wins.
     */
    public static Coordinate drillCenterAt(ExcellonImage excellon, Coordinate click, double extraReach) {
        Coordinate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ExcellonImage.Drill drill : excellon.drills()) {
            Double diameter = excellon.toolDiameters().get(drill.toolId());
            double reach = (diameter == null ? 0 : diameter / 2) + extraReach;
            double distance = Math.hypot(drill.x() - click.x, drill.y() - click.y);
            if (distance <= reach && distance < bestDistance) {
                bestDistance = distance;
                best = new Coordinate(drill.x(), drill.y());
            }
        }
        for (ExcellonImage.Slot slot : excellon.slots()) {
            Double diameter = excellon.toolDiameters().get(slot.toolId());
            double reach = (diameter == null ? 0 : diameter / 2) + extraReach;
            double distance = org.locationtech.jts.algorithm.Distance.pointToSegment(click,
                    new Coordinate(slot.x1(), slot.y1()), new Coordinate(slot.x2(), slot.y2()));
            if (distance <= reach && distance < bestDistance) {
                bestDistance = distance;
                best = new Coordinate((slot.x1() + slot.x2()) / 2, (slot.y1() + slot.y2()) / 2);
            }
        }
        return best;
    }

    /**
     * The operations that carry the moved object onto the reference: a translation by the first pair, then (two-point
     * mode) a rotation about the first destination that brings the second start point onto the second destination.
     *
     * @param points {start1, destination1} or {start1, destination1, start2, destination2}
     */
    public static List<TransformOp> plan(List<Coordinate> points) {
        if (points.size() != 2 && points.size() != 4) {
            throw new IllegalArgumentException("Sao necessarios 2 pontos (translacao) ou 4 (translacao e rotacao)");
        }
        double dx = points.get(1).x - points.get(0).x;
        double dy = points.get(1).y - points.get(0).y;
        List<TransformOp> operations = new ArrayList<>();
        operations.add(new TransformOp.Offset(dx, dy));
        if (points.size() == 4) {
            Coordinate pivot = points.get(1);
            // Where the second start point sits after the translation, and where it has to go.
            double startX = points.get(2).x + dx - pivot.x;
            double startY = points.get(2).y + dy - pivot.y;
            double destX = points.get(3).x - pivot.x;
            double destY = points.get(3).y - pivot.y;
            if (Math.hypot(startX, startY) < 1e-9 || Math.hypot(destX, destY) < 1e-9) {
                throw new IllegalArgumentException(
                        "O segundo par de pontos coincide com o primeiro: nao da para definir a rotacao");
            }
            double angle = Math.toDegrees(Math.atan2(destY, destX) - Math.atan2(startY, startX));
            angle = ((angle + 180) % 360 + 360) % 360 - 180;
            if (Math.abs(angle) > 1e-9) {
                operations.add(new TransformOp.Rotate(angle, pivot));
            }
        }
        return operations;
    }
}
