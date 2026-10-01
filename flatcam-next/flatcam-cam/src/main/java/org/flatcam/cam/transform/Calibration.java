package org.flatcam.cam.transform;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * appTools/ToolCalibration.py: four calibration points (bottom-left origin, bottom-right, top-left, top-right), a
 * verification G-code that visits them, the scale and skew factors worked out from the deltas measured on the machine,
 * and the factors applied to the points and to objects. Python's defaults: travel Z 2.0, verification Z 0.1, tool
 * change Z 15, no zero-Z step, second point top-left.
 */
public final class Calibration {

    /** Indexes of the four points. */
    public static final int BOTTOM_LEFT = 0;
    public static final int BOTTOM_RIGHT = 1;
    public static final int TOP_LEFT = 2;
    public static final int TOP_RIGHT = 3;

    public record Factors(double scaleX, double scaleY, double skewX, double skewY) {
        public static Factors identity() {
            return new Factors(1, 1, 0, 0);
        }
    }

    /** What the verification G-code needs. {@code toolChangeXY} may be null. */
    public record GCodeSettings(double travelZ, double verificationZ, boolean zeroZ, double toolChangeZ,
                                double[] toolChangeXY, boolean secondIsTopLeft, boolean inches) {
        public static GCodeSettings defaults() {
            return new GCodeSettings(2.0, 0.1, false, 15, null, true, false);
        }
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private Calibration() {
    }

    /** Rounds a picked coordinate to {@code decimals} places, as Python does when it stores a calibration point. */
    public static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, java.math.RoundingMode.HALF_EVEN).doubleValue();
    }

    /** The centre of the drill hole under {@code click}, or null. */
    public static Coordinate snap(ExcellonImage excellon, Coordinate click) {
        Geometry solid = excellon.solidGeometry();
        if (solid == null) {
            return null;
        }
        Point at = FACTORY.createPoint(click);
        for (int i = 0; i < solid.getNumGeometries(); i++) {
            Geometry hole = solid.getGeometryN(i);
            if (hole.getEnvelopeInternal().contains(click) && at.within(hole)) {
                return hole.getCentroid().getCoordinate();
            }
        }
        return null;
    }

    /** The centre of the flashed pad of the Gerber under {@code click} (only flashes count, as in Python), or null. */
    public static Coordinate snap(GerberImage gerber, Coordinate click) {
        Point at = FACTORY.createPoint(click);
        for (GerberShape shape : gerber.shapes()) {
            if (!shape.clear() && shape.followGeometry() instanceof Point
                    && shape.geometry().getEnvelopeInternal().contains(click) && at.within(shape.geometry())) {
                return shape.geometry().getCentroid().getCoordinate();
            }
        }
        return null;
    }

    /**
     * Scale and skew factors from the deltas measured at the bottom-right and top-left points (null or 0 = no
     * deviation). Python compares the delta with the point's coordinate (so a blank field means "unchanged"); the
     * results are the same for any real delta. One deliberate fix: the Y skew uses the delta over the X distance to
     * the origin, where Python also added the origin's Y.
     *
     * @param points the four points {x, y}
     * @param bottomRightDelta {dx, dy} found at the bottom-right point, or null
     * @param topLeftDelta {dx, dy} found at the top-left point, or null
     */
    public static Factors calculate(double[][] points, double[] bottomRightDelta, double[] topLeftDelta) {
        checkPoints(points);
        double originX = points[BOTTOM_LEFT][0];
        double originY = points[BOTTOM_LEFT][1];
        double rightX = points[BOTTOM_RIGHT][0];
        double topY = points[TOP_LEFT][1];
        double scaleX = 1;
        double scaleY = 1;
        double skewX = 0;
        double skewY = 0;
        double rightDx = bottomRightDelta == null ? 0 : bottomRightDelta[0];
        double rightDy = bottomRightDelta == null ? 0 : bottomRightDelta[1];
        double leftDx = topLeftDelta == null ? 0 : topLeftDelta[0];
        double leftDy = topLeftDelta == null ? 0 : topLeftDelta[1];
        if (rightDx != 0) {
            requireDistance(rightX - originX, "o ponto inferior direito e a origem tem o mesmo X");
            scaleX = rightDx / (rightX - originX) + 1;
        }
        if (leftDy != 0) {
            requireDistance(topY - originY, "o ponto superior esquerdo e a origem tem o mesmo Y");
            scaleY = leftDy / (topY - originY) + 1;
        }
        if (leftDx != 0) {
            requireDistance(topY - originY, "o ponto superior esquerdo e a origem tem o mesmo Y");
            skewX = Math.toDegrees(Math.atan(leftDx / (topY - originY)));
        }
        if (rightDy != 0) {
            requireDistance(rightX - originX, "o ponto inferior direito e a origem tem o mesmo X");
            skewY = Math.toDegrees(Math.atan(rightDy / (rightX - originX)));
        }
        return new Factors(scaleX, scaleY, skewX, skewY);
    }

    private static void requireDistance(double distance, String message) {
        if (distance == 0) {
            throw new IllegalArgumentException("Nao da para calcular: " + message);
        }
    }

    private static void checkPoints(double[][] points) {
        if (points == null || points.length != 4) {
            throw new IllegalArgumentException("Sao necessarios quatro pontos de calibracao");
        }
    }

    /** The operations Python applies to an object (scale, then skew, both about the origin point). */
    public static List<TransformOp> operations(Factors factors, Coordinate origin) {
        return List.of(new TransformOp.Scale(factors.scaleX(), factors.scaleY(), origin),
                new TransformOp.Skew(factors.skewX(), factors.skewY(), origin));
    }

    /** "Apply Scale Factors" on the points: scaled about the first point. */
    public static double[][] scalePoints(double[][] points, double scaleX, double scaleY) {
        checkPoints(points);
        TransformOp op = new TransformOp.Scale(scaleX, scaleY, new Coordinate(points[0][0], points[0][1]));
        return apply(points, op);
    }

    /** "Apply Skew Factors" on the points: skewed about the first point. */
    public static double[][] skewPoints(double[][] points, double angleX, double angleY) {
        checkPoints(points);
        TransformOp op = new TransformOp.Skew(angleX, angleY, new Coordinate(points[0][0], points[0][1]));
        return apply(points, op);
    }

    private static double[][] apply(double[][] points, TransformOp op) {
        double[][] result = new double[4][];
        for (int i = 0; i < 4; i++) {
            Coordinate moved = op.apply(new Coordinate(points[i][0], points[i][1]));
            result[i] = new double[] {moved.x, moved.y};
        }
        return result;
    }

    /** The G-code that visits the four points (first = origin, then the alignment, check and verification points). */
    public static String verificationGCode(double[][] points, GCodeSettings s) {
        checkPoints(points);
        String travel = fixed(s.travelZ());
        String toolChange = fixed(s.toolChangeZ());
        String verification = fixed(s.verificationZ());
        StringBuilder g = new StringBuilder();
        g.append("(G-CODE GENERATED BY FLATCAM FX)\n\n");
        g.append("(Name: Verification GCode for FlatCAM Calibration Tool)\n");
        g.append("(Units: ").append(s.inches() ? "IN" : "MM").append(")\n\n");
        g.append("(Created on ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy 'at' HH:mm",
                Locale.ENGLISH))).append(")\n\n");
        g.append(s.inches() ? "G20\n" : "G21\n").append("G90\nG17\nG94\n\n");
        if (s.zeroZ()) {
            g.append("M5\n").append("G00 Z").append(toolChange).append('\n');
            if (s.toolChangeXY() != null) {
                g.append("G00 X").append(number(s.toolChangeXY()[0])).append(" Y").append(number(s.toolChangeXY()[1])).append('\n');
            }
            g.append("M0\nG01 Z0\nM0\n").append("G00 Z").append(toolChange).append("\nM0\n");
        }
        int[] order = s.secondIsTopLeft()
                ? new int[] {BOTTOM_LEFT, TOP_LEFT, BOTTOM_RIGHT, TOP_RIGHT}
                : new int[] {BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT};
        for (int index : order) {
            g.append("G00 Z").append(travel).append('\n');
            g.append("G00 X").append(number(points[index][0])).append(" Y").append(number(points[index][1])).append('\n');
            g.append("G01 Z").append(verification).append("\nM0\n");
        }
        g.append("G00 Z").append(travel).append("\nG00 X0 Y0\n").append("G00 Z").append(toolChange).append('\n');
        if (s.toolChangeXY() != null) {
            g.append("G00 X").append(number(s.toolChangeXY()[0])).append(" Y").append(number(s.toolChangeXY()[1])).append('\n');
        }
        g.append("M2");
        return g.toString();
    }

    private static String fixed(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /** A number the way Python prints a float: shortest form, with at least one decimal. */
    static String number(double value) {
        String text = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return text.contains(".") ? text : text + ".0";
    }
}
