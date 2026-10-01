package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * appTools/ToolFiducials.py: alignment marks added to a copper Gerber (or, as openings twice the size, to a
 * solder-mask Gerber). Marks are round pads, crosses made of two lines, or a two-square chess pattern. The
 * positions are the corners of the object's bounding box grown by a margin, or clicked points.
 */
public final class Fiducials {

    public enum Type { CIRCULAR, CROSS, CHESS }

    /** Where the third mark goes in automatic mode: Python's "Second fiducial" up (top left), down (bottom right) or none. */
    public enum SecondPoint { UP, DOWN, NONE }

    private static final int STEPS = 16;
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private Fiducials() {
    }

    /** Bottom-left, top-right and (optionally) the second point of the box {@code bounds} grown by {@code margin}. */
    public static List<Coordinate> autoPoints(double[] bounds, double margin, SecondPoint second) {
        double x0 = bounds[0] - margin;
        double y0 = bounds[1] - margin;
        double x1 = bounds[2] + margin;
        double y1 = bounds[3] + margin;
        List<Coordinate> points = new ArrayList<>(List.of(new Coordinate(x0, y0), new Coordinate(x1, y1)));
        if (second == SecondPoint.UP) {
            points.add(new Coordinate(x0, y1));
        } else if (second == SecondPoint.DOWN) {
            points.add(new Coordinate(x1, y0));
        }
        return points;
    }

    /**
     * @param size      overall size of the mark (diameter; side of the chess pattern; arm-to-arm length of a cross)
     * @param thickness line width of a cross
     */
    public static GerberImage add(GerberImage source, List<Coordinate> points, Type type, double size, double thickness) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Nenhum ponto para os fiduciais");
        }
        if (!(size > 0)) {
            throw new IllegalArgumentException("O tamanho do fiducial deve ser positivo");
        }
        if (type == Type.CROSS && !(thickness > 0 && thickness < size)) {
            throw new IllegalArgumentException("A espessura da cruz deve ser positiva e menor que o tamanho");
        }
        Map<String, Aperture> apertures = new LinkedHashMap<>(source.apertures());
        List<GerberShape> shapes = new ArrayList<>(source.shapes());
        double radius = size / 2;
        switch (type) {
            case CIRCULAR -> {
                String code = apertureFor(apertures, ApertureKind.CIRCLE, size, size, Aperture.circle(size));
                for (Coordinate point : points) {
                    org.locationtech.jts.geom.Point centre = FACTORY.createPoint(point);
                    shapes.add(new GerberShape(code, centre.buffer(radius, STEPS), false, centre));
                }
            }
            case CROSS -> {
                String code = apertureFor(apertures, ApertureKind.CIRCLE, thickness, thickness,
                        Aperture.circle(thickness));
                double arm = radius - thickness / 2;
                for (Coordinate point : points) {
                    LineString horizontal = FACTORY.createLineString(new Coordinate[] {
                            new Coordinate(point.x - arm, point.y), new Coordinate(point.x + arm, point.y)});
                    LineString vertical = FACTORY.createLineString(new Coordinate[] {
                            new Coordinate(point.x, point.y - arm), new Coordinate(point.x, point.y + arm)});
                    for (LineString line : List.of(horizontal, vertical)) {
                        Geometry solid = line.buffer(thickness / 2, STEPS);
                        shapes.add(new GerberShape(code, solid, false, line.getCentroid()));
                    }
                }
            }
            case CHESS -> {
                String code = apertureFor(apertures, ApertureKind.RECTANGLE, size / 2, size / 2,
                        Aperture.rectangle(size / 2, size / 2));
                for (Coordinate point : points) {
                    shapes.add(square(code, point.x - size / 4, point.y + size / 4, size / 2));
                    shapes.add(square(code, point.x + size / 4, point.y - size / 4, size / 2));
                }
            }
        }
        return source.withEditedShapes(shapes, apertures, CancellationToken.none(), ProgressCallback.none());
    }

    private static GerberShape square(String code, double cx, double cy, double side) {
        double h = side / 2;
        Polygon square = FACTORY.createPolygon(new Coordinate[] {new Coordinate(cx - h, cy - h),
                new Coordinate(cx + h, cy - h), new Coordinate(cx + h, cy + h), new Coordinate(cx - h, cy + h),
                new Coordinate(cx - h, cy - h)});
        return new GerberShape(code, square, false, FACTORY.createPoint(new Coordinate(cx, cy)));
    }

    /** Reuses an aperture of the same kind and size, else defines a new one (the next free code, 10 at least). */
    static String apertureFor(Map<String, Aperture> apertures, ApertureKind kind, double width, double height,
                                      Aperture fresh) {
        for (Map.Entry<String, Aperture> entry : apertures.entrySet()) {
            Aperture aperture = entry.getValue();
            if (aperture.kind == kind && Math.abs(aperture.width - width) < 1e-9
                    && (kind == ApertureKind.CIRCLE || Math.abs(aperture.height - height) < 1e-9)) {
                return entry.getKey();
            }
        }
        int next = 9;
        for (String code : apertures.keySet()) {
            try {
                next = Math.max(next, Integer.parseInt(code));
            } catch (NumberFormatException ignored) {
                // Not a numeric code: irrelevant to the next free one.
            }
        }
        String code = String.valueOf(next + 1);
        apertures.put(code, fresh);
        return code;
    }
}
