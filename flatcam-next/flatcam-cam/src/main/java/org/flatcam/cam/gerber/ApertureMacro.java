package org.flatcam.cam.gerber;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * A %AM aperture macro definition: a named, ordered list of primitive
 * statements, each additive (exposure 1) or subtractive (exposure 0) from
 * the ones before it - see {@link #evaluate}. Implements the primitives
 * actually seen so far: 0 (comment, skipped before reaching here - see
 * GerberParser), 1 (circle), 4 (outline/polygon), 5 (regular polygon), and
 * 20 (vector line - the four primitives KiCad's "RoundRect" SMD pad macro
 * uses). Other primitive codes (thermal reliefs, center rectangle, ...)
 * raise {@link GerberParseException} rather than being silently skipped.
 */
final class ApertureMacro {

    private final String name;
    private final List<String[]> primitives = new ArrayList<>();

    ApertureMacro(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    /** Adds one primitive statement, already split into its comma-separated fields (code first). */
    void addPrimitive(String[] fields) {
        primitives.add(fields);
    }

    Geometry evaluate(double[] modifiers, GeometryFactory geometryFactory) {
        Geometry result = geometryFactory.createPolygon();
        for (String[] fields : primitives) {
            int code = Integer.parseInt(fields[0]);
            Geometry shape = switch (code) {
                case 1 -> circle(fields, modifiers, geometryFactory);
                case 4 -> outline(fields, modifiers, geometryFactory);
                case 5 -> polygon(fields, modifiers, geometryFactory);
                case 20 -> vectorLine(fields, modifiers, geometryFactory);
                default -> throw new GerberParseException(
                        "Aperture macro '" + name + "': unsupported primitive code " + code);
            };
            boolean additive = eval(fields[1], modifiers) >= 0.5;
            result = additive ? result.union(shape) : result.difference(shape);
        }
        return result;
    }

    /** Primitive 1: exposure, diameter, centerX, centerY[, rotation]. */
    private static Geometry circle(String[] fields, double[] modifiers, GeometryFactory gf) {
        double diameter = eval(fields[2], modifiers);
        double centerX = eval(fields[3], modifiers);
        double centerY = eval(fields[4], modifiers);
        double rotationDeg = fields.length > 5 ? eval(fields[5], modifiers) : 0.0;
        Geometry shape = gf.createPoint(new Coordinate(centerX, centerY)).buffer(diameter / 2.0, 16);
        return rotateAboutOrigin(shape, rotationDeg);
    }

    /** Primitive 4: exposure, #vertices n, (X,Y) x (n+1) points (closed), rotation. */
    private static Geometry outline(String[] fields, double[] modifiers, GeometryFactory gf) {
        int vertexCount = (int) Math.round(eval(fields[2], modifiers));
        int pointCount = vertexCount + 1;
        Coordinate[] ring = new Coordinate[pointCount];
        for (int i = 0; i < pointCount; i++) {
            double x = eval(fields[3 + 2 * i], modifiers);
            double y = eval(fields[3 + 2 * i + 1], modifiers);
            ring[i] = new Coordinate(x, y);
        }
        if (!ring[0].equals2D(ring[pointCount - 1])) {
            throw new GerberParseException("Aperture macro outline (primitive 4) is not closed");
        }
        double rotationDeg = eval(fields[3 + 2 * pointCount], modifiers);
        return rotateAboutOrigin(gf.createPolygon(ring), rotationDeg);
    }

    /** Primitive 5: exposure, #vertices, centerX, centerY, diameter, rotation. */
    private static Geometry polygon(String[] fields, double[] modifiers, GeometryFactory gf) {
        int vertexCount = (int) Math.round(eval(fields[2], modifiers));
        double centerX = eval(fields[3], modifiers);
        double centerY = eval(fields[4], modifiers);
        double diameter = eval(fields[5], modifiers);
        double rotationDeg = fields.length > 6 ? eval(fields[6], modifiers) : 0.0;

        double radius = diameter / 2.0;
        Coordinate[] ring = new Coordinate[vertexCount + 1];
        for (int i = 0; i < vertexCount; i++) {
            double angle = Math.toRadians(i * (360.0 / vertexCount));
            ring[i] = new Coordinate(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle));
        }
        ring[vertexCount] = ring[0];
        return rotateAboutOrigin(gf.createPolygon(ring), rotationDeg);
    }

    /** Primitive 20: exposure, width, startX, startY, endX, endY, rotation - a straight, square-ended stroke. */
    private static Geometry vectorLine(String[] fields, double[] modifiers, GeometryFactory gf) {
        double width = eval(fields[2], modifiers);
        double x1 = eval(fields[3], modifiers);
        double y1 = eval(fields[4], modifiers);
        double x2 = eval(fields[5], modifiers);
        double y2 = eval(fields[6], modifiers);
        double rotationDeg = eval(fields[7], modifiers);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        double ux = length == 0 ? 0 : dx / length;
        double uy = length == 0 ? 0 : dy / length;
        double px = -uy * width / 2.0;
        double py = ux * width / 2.0;

        Coordinate[] ring = {
                new Coordinate(x1 + px, y1 + py), new Coordinate(x2 + px, y2 + py),
                new Coordinate(x2 - px, y2 - py), new Coordinate(x1 - px, y1 - py),
                new Coordinate(x1 + px, y1 + py)
        };
        return rotateAboutOrigin(gf.createPolygon(ring), rotationDeg);
    }

    /**
     * Per the Gerber spec, a primitive's rotation is about the macro's own
     * origin (0,0) - NOT the primitive's own center - applied after the
     * shape is otherwise fully defined.
     */
    private static Geometry rotateAboutOrigin(Geometry shape, double rotationDeg) {
        if (rotationDeg == 0) {
            return shape;
        }
        return AffineTransformation.rotationInstance(Math.toRadians(rotationDeg)).transform(shape);
    }

    private static double eval(String field, double[] modifiers) {
        return MacroExpression.evaluate(field, modifiers);
    }
}
