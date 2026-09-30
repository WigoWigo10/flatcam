package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * appTools/ToolCorners.py: alignment markers at the corners of a Gerber's bounding box, outside it by a margin
 * (and half the line width): an L at each corner ("safe") or a cross centred on it, plus optional drill holes at
 * the same spots. Python's defaults: 0.1 thick, 3.0 long, margin 0, safe corners, 0.5 drill.
 */
public final class CornerMarkers {

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public enum Style { CORNER, CROSS }

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private CornerMarkers() {
    }

    /** The marker centre of each chosen corner of {@code bounds} = {xmin, ymin, xmax, ymax}. */
    public static Map<Corner, Coordinate> anchors(double[] bounds, Set<Corner> corners, double thickness, double margin) {
        if (corners.isEmpty()) {
            throw new IllegalArgumentException("Escolha ao menos um canto");
        }
        double out = margin + thickness / 2;
        Map<Corner, Coordinate> anchors = new LinkedHashMap<>();
        for (Corner corner : EnumSet.copyOf(corners)) {
            anchors.put(corner, switch (corner) {
                case TOP_LEFT -> new Coordinate(bounds[0] - out, bounds[3] + out);
                case TOP_RIGHT -> new Coordinate(bounds[2] + out, bounds[3] + out);
                case BOTTOM_LEFT -> new Coordinate(bounds[0] - out, bounds[1] - out);
                case BOTTOM_RIGHT -> new Coordinate(bounds[2] + out, bounds[1] - out);
            });
        }
        return anchors;
    }

    /** @param length total length of a line (each arm of a corner is that long, a cross' arms half of it each way) */
    public static GerberImage add(GerberImage source, double[] bounds, Set<Corner> corners, Style style,
                                  double thickness, double length, double margin) {
        if (!(thickness > 0) || !(length > 0)) {
            throw new IllegalArgumentException("Espessura e comprimento devem ser positivos");
        }
        double half = length / 2;
        Map<String, Aperture> apertures = new LinkedHashMap<>(source.apertures());
        String code = null;
        for (Map.Entry<String, Aperture> entry : apertures.entrySet()) {
            if (entry.getValue().kind == ApertureKind.CIRCLE && Math.abs(entry.getValue().width - thickness) < 1e-9) {
                code = entry.getKey();
                break;
            }
        }
        if (code == null) {
            int next = 9;
            for (String existing : apertures.keySet()) {
                try {
                    next = Math.max(next, Integer.parseInt(existing));
                } catch (NumberFormatException ignored) {
                    // Not numeric: irrelevant to the next free code.
                }
            }
            code = String.valueOf(next + 1);
            apertures.put(code, Aperture.circle(thickness));
        }
        List<GerberShape> shapes = new ArrayList<>(source.shapes());
        for (Map.Entry<Corner, Coordinate> anchor : anchors(bounds, corners, thickness, margin).entrySet()) {
            double x = anchor.getValue().x;
            double y = anchor.getValue().y;
            boolean left = anchor.getKey() == Corner.TOP_LEFT || anchor.getKey() == Corner.BOTTOM_LEFT;
            boolean top = anchor.getKey() == Corner.TOP_LEFT || anchor.getKey() == Corner.TOP_RIGHT;
            List<LineString> lines = new ArrayList<>();
            if (style == Style.CORNER) {
                lines.add(line(x, y, x + (left ? half : -half), y));
                lines.add(line(x, y, x, y + (top ? -half : half)));
            } else {
                lines.add(line(x - half, y, x + half, y));
                lines.add(line(x, y - half, x, y + half));
            }
            for (LineString line : lines) {
                shapes.add(new GerberShape(code, line.buffer(thickness / 2, 16), false, line));
            }
        }
        return source.withEditedShapes(shapes, apertures, CancellationToken.none(), ProgressCallback.none());
    }

    /** An Excellon with one hole of {@code diameter} at each marker centre. */
    public static ExcellonImage drills(String units, double[] bounds, Set<Corner> corners, double thickness,
                                       double margin, double diameter) {
        if (!(diameter > 0)) {
            throw new IllegalArgumentException("O diametro da broca e zero");
        }
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<Geometry> discs = new ArrayList<>();
        for (Coordinate point : anchors(bounds, corners, thickness, margin).values()) {
            drills.add(new ExcellonImage.Drill(1, point.x, point.y));
            discs.add(FACTORY.createPoint(point).buffer(diameter / 2, 16));
        }
        return ExcellonImage.of(units, Map.of(1, diameter), drills, List.of(), FACTORY.buildGeometry(discs));
    }

    private static LineString line(double x0, double y0, double x1, double y1) {
        return FACTORY.createLineString(new Coordinate[] {new Coordinate(x0, y0), new Coordinate(x1, y1)});
    }
}
