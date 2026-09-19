package org.flatcam.cam.gerber;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * A %AM aperture macro definition: a named list of primitive statements.
 * Only primitive code 5 (regular polygon) is implemented - the only one
 * used by the Fase 0 fixture corpus (STM32F4-spindle.cmp's OC8 macro).
 * Other primitive codes raise {@link GerberParseException} when the macro
 * is evaluated, rather than being silently skipped.
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
        List<Geometry> shapes = new ArrayList<>();
        for (String[] fields : primitives) {
            int code = Integer.parseInt(fields[0]);
            switch (code) {
                case 5 -> shapes.add(evaluatePolygon(fields, modifiers, geometryFactory));
                default -> throw new GerberParseException(
                        "Aperture macro '" + name + "': unsupported primitive code " + code);
            }
        }
        if (shapes.isEmpty()) {
            return geometryFactory.createPolygon();
        }
        return UnaryUnionOp.union(shapes);
    }

    /** Primitive 5: exposure, #vertices, centerX, centerY, diameter, rotation(deg). */
    private Polygon evaluatePolygon(String[] fields, double[] modifiers, GeometryFactory geometryFactory) {
        int vertexCount = (int) Math.round(eval(fields[2], modifiers));
        double centerX = eval(fields[3], modifiers);
        double centerY = eval(fields[4], modifiers);
        double diameter = eval(fields[5], modifiers);
        double rotationDeg = fields.length > 6 ? eval(fields[6], modifiers) : 0.0;

        double radius = diameter / 2.0;
        Coordinate[] ring = new Coordinate[vertexCount + 1];
        for (int i = 0; i < vertexCount; i++) {
            double angle = Math.toRadians(rotationDeg + i * (360.0 / vertexCount));
            ring[i] = new Coordinate(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle));
        }
        ring[vertexCount] = ring[0];
        return geometryFactory.createPolygon(ring);
    }

    private static double eval(String field, double[] modifiers) {
        return MacroExpression.evaluate(field, modifiers);
    }
}
