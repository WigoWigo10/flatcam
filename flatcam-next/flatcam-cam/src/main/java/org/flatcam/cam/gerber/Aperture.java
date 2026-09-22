package org.flatcam.cam.gerber;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * A defined aperture (%ADDnn...). Standard shapes (C/R/O) carry width/height
 * directly; POLYGON carries its vertex count/rotation; MACRO wraps a
 * resolved {@link ApertureMacro} plus its modifiers.
 */
public final class Aperture {

    private static final int CIRCLE_QUADRANT_SEGMENTS = 16;

    public final ApertureKind kind;
    public final double width;
    public final double height;
    private final int polygonVertices;
    private final double polygonRotation;
    private final ApertureMacro macro;
    private final double[] macroModifiers;

    private Aperture(ApertureKind kind, double width, double height, int polygonVertices,
                     double polygonRotation, ApertureMacro macro, double[] macroModifiers) {
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.polygonVertices = polygonVertices;
        this.polygonRotation = polygonRotation;
        this.macro = macro;
        this.macroModifiers = macroModifiers;
    }

    public static Aperture circle(double diameter) {
        return new Aperture(ApertureKind.CIRCLE, diameter, diameter, 0, 0, null, null);
    }

    public static Aperture rectangle(double width, double height) {
        return new Aperture(ApertureKind.RECTANGLE, width, height, 0, 0, null, null);
    }

    public static Aperture obround(double width, double height) {
        return new Aperture(ApertureKind.OBROUND, width, height, 0, 0, null, null);
    }

    public static Aperture polygon(double diameter, int vertices, double rotation) {
        if (vertices < 3 || vertices > 12) {
            throw new GerberParseException("Polygon aperture vertex count must be between 3 and 12: " + vertices);
        }
        return new Aperture(ApertureKind.POLYGON, diameter, diameter, vertices, rotation, null, null);
    }

    public static Aperture macro(ApertureMacro macro, double[] modifiers) {
        return new Aperture(ApertureKind.MACRO, 0, 0, 0, 0, macro, modifiers);
    }

    /**
     * Effective radius of the pen used for a stroke (D01 outside region mode).
     * The Gerber spec only really defines this for CIRCLE, but real files do
     * occasionally stroke with a RECTANGLE/OBROUND aperture (seen in the Fase 0
     * corpus with a 0.001x0.001" rectangle - a near-point reference aperture) -
     * approximated here as a round pen using the smaller dimension. MACRO has
     * no well-defined width, so it still raises.
     */
    public double strokeRadius() {
        return switch (kind) {
            case CIRCLE -> width / 2.0;
            case RECTANGLE, OBROUND -> Math.min(width, height) / 2.0;
            case POLYGON -> width / 2.0;
            case MACRO -> throw new GerberParseException("Stroking with a macro aperture is not supported");
        };
    }

    /** The macro's name, for a MACRO aperture's "Type" column in the apertures table - null otherwise. */
    public String macroName() {
        return macro != null ? macro.name() : null;
    }

    public int polygonVertices() {
        return polygonVertices;
    }

    public double polygonRotation() {
        return polygonRotation;
    }

    /** The shape this aperture paints when flashed (D03) at (x, y). */
    public Geometry footprintAt(double x, double y, GeometryFactory geometryFactory) {
        return switch (kind) {
            case CIRCLE -> geometryFactory.createPoint(new Coordinate(x, y)).buffer(width / 2.0, CIRCLE_QUADRANT_SEGMENTS);
            case RECTANGLE -> rectangleAt(x, y, geometryFactory);
            case OBROUND -> obroundAt(x, y, geometryFactory);
            case POLYGON -> polygonAt(x, y, geometryFactory);
            case MACRO -> {
                Geometry shape = macro.evaluate(macroModifiers, geometryFactory);
                Geometry moved = AffineTransformation.translationInstance(x, y).transform(shape);
                yield moved;
            }
        };
    }

    private Geometry polygonAt(double x, double y, GeometryFactory geometryFactory) {
        Coordinate[] ring = new Coordinate[polygonVertices + 1];
        double radius = width / 2.0;
        for (int i = 0; i < polygonVertices; i++) {
            double angle = Math.toRadians(polygonRotation + i * 360.0 / polygonVertices);
            ring[i] = new Coordinate(x + radius * Math.cos(angle), y + radius * Math.sin(angle));
        }
        ring[polygonVertices] = ring[0];
        return geometryFactory.createPolygon(ring);
    }

    private Geometry rectangleAt(double x, double y, GeometryFactory geometryFactory) {
        double hw = width / 2.0;
        double hh = height / 2.0;
        Coordinate[] ring = {
                new Coordinate(x - hw, y - hh), new Coordinate(x + hw, y - hh),
                new Coordinate(x + hw, y + hh), new Coordinate(x - hw, y + hh),
                new Coordinate(x - hw, y - hh)
        };
        return geometryFactory.createPolygon(ring);
    }

    /** A stadium shape: buffer a segment along the longer axis by half the shorter dimension. */
    private Geometry obroundAt(double x, double y, GeometryFactory geometryFactory) {
        double radius = Math.min(width, height) / 2.0;
        double halfSpan = Math.abs(width - height) / 2.0;
        Coordinate a;
        Coordinate b;
        if (width >= height) {
            a = new Coordinate(x - halfSpan, y);
            b = new Coordinate(x + halfSpan, y);
        } else {
            a = new Coordinate(x, y - halfSpan);
            b = new Coordinate(x, y + halfSpan);
        }
        if (halfSpan == 0) {
            return geometryFactory.createPoint(a).buffer(radius, CIRCLE_QUADRANT_SEGMENTS);
        }
        return geometryFactory.createLineString(new Coordinate[]{a, b}).buffer(radius, CIRCLE_QUADRANT_SEGMENTS);
    }
}
