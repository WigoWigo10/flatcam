package org.flatcam.cam.gerber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * A %AM aperture macro definition: a named, ordered list of primitive
 * statements, each additive (exposure 1) or subtractive (exposure 0) from
 * the ones before it - see {@link #evaluate}. Implements the primitives
 * supported by the legacy FlatCAM parser: 0 (comment, skipped before reaching
 * here), 1 (circle), 2/20 (vector line), 21 (center line), 22 (lower-left
 * line), 4 (outline), 5 (regular polygon), 6 (moire) and 7 (thermal).
 * Local variable assignments ({@code $n=expression}) are evaluated in source
 * order before subsequent primitives use them.
 */
final class ApertureMacro {

    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("\\$(\\d+)");
    private static final Pattern VARIABLE_ASSIGNMENT = Pattern.compile("^\\$(\\d+)=(.+)$");

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
        double[] variables = Arrays.copyOf(modifiers, Math.max(modifiers.length, highestVariableIndex()));
        Geometry result = geometryFactory.createPolygon();
        for (String[] fields : primitives) {
            Matcher assignment = VARIABLE_ASSIGNMENT.matcher(fields[0].replace(" ", ""));
            if (fields.length == 1 && assignment.matches()) {
                int index = Integer.parseInt(assignment.group(1));
                variables[index - 1] = MacroExpression.evaluate(assignment.group(2), variables);
                continue;
            }

            int code;
            try {
                code = Integer.parseInt(fields[0].strip());
            } catch (NumberFormatException e) {
                throw new GerberParseException("Aperture macro '" + name
                        + "': invalid statement " + String.join(",", fields), e);
            }
            Geometry shape = switch (code) {
                case 1 -> circle(fields, variables, geometryFactory);
                case 2, 20 -> vectorLine(fields, variables, geometryFactory);
                case 21 -> centerLine(fields, variables, geometryFactory);
                case 22 -> lowerLeftLine(fields, variables, geometryFactory);
                case 4 -> outline(fields, variables, geometryFactory);
                case 5 -> polygon(fields, variables, geometryFactory);
                case 6 -> moire(fields, variables, geometryFactory);
                case 7 -> thermal(fields, variables, geometryFactory);
                default -> throw new GerberParseException(
                        "Aperture macro '" + name + "': unsupported primitive code " + code);
            };
            // Moire and thermal primitives have no exposure field and are
            // always dark; all other primitive layouts put exposure first.
            boolean additive = code == 6 || code == 7 || eval(fields[1], variables) >= 0.5;
            result = additive ? result.union(shape) : result.difference(shape);
        }
        return result;
    }

    private int highestVariableIndex() {
        int highest = 0;
        for (String[] statement : primitives) {
            for (String field : statement) {
                Matcher matcher = VARIABLE_REFERENCE.matcher(field);
                while (matcher.find()) {
                    highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return highest;
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

    /** Primitive 21: exposure, width, height, centerX, centerY, rotation. */
    private static Geometry centerLine(String[] fields, double[] modifiers, GeometryFactory gf) {
        double width = eval(fields[2], modifiers);
        double height = eval(fields[3], modifiers);
        double centerX = eval(fields[4], modifiers);
        double centerY = eval(fields[5], modifiers);
        double rotationDeg = fields.length > 6 ? eval(fields[6], modifiers) : 0.0;
        return rotateAboutOrigin(rectangle(centerX - width / 2, centerY - height / 2,
                centerX + width / 2, centerY + height / 2, gf), rotationDeg);
    }

    /** Primitive 22: exposure, width, height, lowerLeftX, lowerLeftY, rotation. */
    private static Geometry lowerLeftLine(String[] fields, double[] modifiers, GeometryFactory gf) {
        double width = eval(fields[2], modifiers);
        double height = eval(fields[3], modifiers);
        double x = eval(fields[4], modifiers);
        double y = eval(fields[5], modifiers);
        double rotationDeg = fields.length > 6 ? eval(fields[6], modifiers) : 0.0;
        return rotateAboutOrigin(rectangle(x, y, x + width, y + height, gf), rotationDeg);
    }

    /** Primitive 6: center, outer diameter, ring thickness/gap/count, crosshair and rotation. */
    private static Geometry moire(String[] fields, double[] modifiers, GeometryFactory gf) {
        double centerX = eval(fields[1], modifiers);
        double centerY = eval(fields[2], modifiers);
        double outerDiameter = eval(fields[3], modifiers);
        double thickness = eval(fields[4], modifiers);
        double gap = eval(fields[5], modifiers);
        int maxRings = Math.max(0, (int) Math.floor(eval(fields[6], modifiers)));
        double crossThickness = eval(fields[7], modifiers);
        double crossLength = eval(fields[8], modifiers);
        double rotationDeg = fields.length > 9 ? eval(fields[9], modifiers) : 0.0;

        Geometry result = gf.createPolygon();
        double diameter = outerDiameter;
        for (int ring = 0; ring < maxRings && diameter > 0 && thickness > 0; ring++) {
            Geometry outer = gf.createPoint(new Coordinate(centerX, centerY)).buffer(diameter / 2, 16);
            double innerDiameter = diameter - 2 * thickness;
            Geometry annulus = innerDiameter > 0
                    ? outer.difference(gf.createPoint(new Coordinate(centerX, centerY)).buffer(innerDiameter / 2, 16))
                    : outer;
            result = result.union(annulus);
            diameter -= 2 * (thickness + gap);
        }
        if (crossThickness > 0 && crossLength > 0) {
            result = result.union(rectangle(centerX - crossLength / 2, centerY - crossThickness / 2,
                    centerX + crossLength / 2, centerY + crossThickness / 2, gf));
            result = result.union(rectangle(centerX - crossThickness / 2, centerY - crossLength / 2,
                    centerX + crossThickness / 2, centerY + crossLength / 2, gf));
        }
        return rotateAboutOrigin(result, rotationDeg);
    }

    /** Primitive 7: center, outer/inner diameter, gap thickness and rotation. */
    private static Geometry thermal(String[] fields, double[] modifiers, GeometryFactory gf) {
        double centerX = eval(fields[1], modifiers);
        double centerY = eval(fields[2], modifiers);
        double outerDiameter = eval(fields[3], modifiers);
        double innerDiameter = eval(fields[4], modifiers);
        double gap = eval(fields[5], modifiers);
        double rotationDeg = fields.length > 6 ? eval(fields[6], modifiers) : 0.0;

        Geometry outer = gf.createPoint(new Coordinate(centerX, centerY)).buffer(outerDiameter / 2, 16);
        Geometry ring = innerDiameter > 0
                ? outer.difference(gf.createPoint(new Coordinate(centerX, centerY)).buffer(innerDiameter / 2, 16))
                : outer;
        Geometry horizontalGap = rectangle(centerX - outerDiameter / 2, centerY - gap / 2,
                centerX + outerDiameter / 2, centerY + gap / 2, gf);
        Geometry verticalGap = rectangle(centerX - gap / 2, centerY - outerDiameter / 2,
                centerX + gap / 2, centerY + outerDiameter / 2, gf);
        return rotateAboutOrigin(ring.difference(horizontalGap.union(verticalGap)), rotationDeg);
    }

    private static Geometry rectangle(double minX, double minY, double maxX, double maxY, GeometryFactory gf) {
        return gf.createPolygon(new Coordinate[]{
                new Coordinate(minX, minY), new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY), new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        });
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
