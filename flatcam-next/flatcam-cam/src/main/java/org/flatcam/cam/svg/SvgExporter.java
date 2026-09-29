package org.flatcam.cam.svg;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * File > Export > SVG - app_Main.py's export_svg. The document is sized in the
 * object's units, with a {@code scale(1,-1)} group so Y points up as in the
 * plot. Shapes use shapely's own svg() look (what Python writes), toolpaths
 * are drawn at the tool width like camlib.CNCJob.export_svg (travels under
 * cuts). Unlike Python, the viewBox really frames the flipped drawing and
 * includes the stroke width, so nothing is clipped.
 */
public final class SvgExporter {

    /** Drawing style of one layer. */
    public enum Kind { SHAPES, TOOLPATH }

    public record Layer(Geometry geometry, Kind kind, String color, double strokeWidth) {
        public Layer {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(color, "color");
            if (!(strokeWidth > 0) || !Double.isFinite(strokeWidth)) {
                throw new IllegalArgumentException("SVG stroke width must be positive: " + strokeWidth);
            }
        }
    }

    /** shapely's default fill for valid geometry. */
    public static final String SHAPE_FILL = "#66cc99";
    private static final String SHAPE_STROKE = "#555555";
    /** Python's default scale_stroke_factor 0.01 - shapely draws strokes 2x the factor wide. */
    public static final double SHAPE_STROKE_WIDTH = 0.02;
    public static final String TRAVEL_COLOR = "#F0E24D";
    public static final String CUT_COLOR = "#5E6CFF";

    /** A Gerber/Excellon/Geometry object: filled polygons and thin lines, as Python writes them. */
    public static Layer shapes(Geometry geometry) {
        return new Layer(geometry, Kind.SHAPES, SHAPE_FILL, SHAPE_STROKE_WIDTH);
    }

    /** A CNC Job path at the tool width; plunge points become tool-sized dots. */
    public static Layer toolpath(Geometry centerlines, String color, double toolDiameter) {
        return new Layer(centerlines, Kind.TOOLPATH, color, toolDiameter);
    }

    public void write(List<Layer> layers, String units, Path path) throws IOException {
        String svg = export(layers, units);
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(),
                "." + destination.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, svg, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String export(List<Layer> layers, String units) {
        Objects.requireNonNull(layers, "layers");
        String unit = "MM".equals(units) ? "mm" : "IN".equals(units) ? "in" : null;
        if (unit == null) {
            throw new IllegalArgumentException("Unsupported SVG units: " + units);
        }
        Envelope bounds = new Envelope();
        for (Layer layer : layers) {
            if (layer.geometry() != null && !layer.geometry().isEmpty()) {
                Envelope envelope = new Envelope(layer.geometry().getEnvelopeInternal());
                envelope.expandBy(layer.strokeWidth() / 2);
                bounds.expandToInclude(envelope);
            }
        }
        if (bounds.isNull()) {
            throw new IllegalArgumentException("Nothing to export: the object has no geometry");
        }
        double width = Math.max(bounds.getWidth(), 1e-6);
        double height = Math.max(bounds.getHeight(), 1e-6);

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\"")
                .append(" width=\"").append(number(width)).append(unit).append('"')
                .append(" height=\"").append(number(height)).append(unit).append('"')
                .append(" viewBox=\"").append(number(bounds.getMinX())).append(' ')
                .append(number(-bounds.getMaxY())).append(' ')
                .append(number(width)).append(' ').append(number(height)).append("\">\n")
                .append("<g transform=\"scale(1,-1)\">\n");
        for (Layer layer : layers) {
            if (layer.geometry() != null) {
                appendGeometry(svg, layer.geometry(), layer);
            }
        }
        svg.append("</g>\n</svg>\n");
        return svg.toString();
    }

    private static void appendGeometry(StringBuilder svg, Geometry geometry, Layer layer) {
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            appendPolygon(svg, polygon, layer);
        } else if (geometry instanceof LineString line) {
            appendLine(svg, line, layer);
        } else if (geometry instanceof Point point) {
            appendPoint(svg, point, layer);
        } else {
            for (int part = 0; part < geometry.getNumGeometries(); part++) {
                appendGeometry(svg, geometry.getGeometryN(part), layer);
            }
        }
    }

    private static void appendPolygon(StringBuilder svg, Polygon polygon, Layer layer) {
        boolean toolpath = layer.kind() == Kind.TOOLPATH;
        svg.append("<path fill-rule=\"evenodd\" fill=\"").append(layer.color())
                .append("\" stroke=\"").append(toolpath ? layer.color() : SHAPE_STROKE)
                .append("\" stroke-width=\"").append(number(toolpath ? SHAPE_STROKE_WIDTH : layer.strokeWidth()))
                .append("\" opacity=\"").append(toolpath ? "1" : "0.6").append("\" d=\"");
        appendRing(svg, polygon.getExteriorRing().getCoordinates());
        for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
            svg.append(' ');
            appendRing(svg, polygon.getInteriorRingN(hole).getCoordinates());
        }
        svg.append("\"/>\n");
    }

    private static void appendRing(StringBuilder svg, Coordinate[] ring) {
        for (int index = 0; index < ring.length; index++) {
            svg.append(index == 0 ? "M" : " L").append(number(ring[index].x)).append(',')
                    .append(number(ring[index].y));
        }
        svg.append(" z");
    }

    private static void appendLine(StringBuilder svg, LineString line, Layer layer) {
        svg.append("<polyline fill=\"none\" stroke=\"").append(layer.color())
                .append("\" stroke-width=\"").append(number(layer.strokeWidth())).append('"');
        if (layer.kind() == Kind.TOOLPATH) {
            svg.append(" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
        } else {
            svg.append(" opacity=\"0.8\"");
        }
        svg.append(" points=\"");
        Coordinate[] coordinates = line.getCoordinates();
        for (int index = 0; index < coordinates.length; index++) {
            if (index > 0) {
                svg.append(' ');
            }
            svg.append(number(coordinates[index].x)).append(',').append(number(coordinates[index].y));
        }
        svg.append("\"/>\n");
    }

    private static void appendPoint(StringBuilder svg, Point point, Layer layer) {
        boolean toolpath = layer.kind() == Kind.TOOLPATH;
        // shapely: r = 3 x scale factor = 1.5 x stroke width; a plunge is one tool diameter wide.
        double radius = toolpath ? layer.strokeWidth() / 2 : layer.strokeWidth() * 1.5;
        svg.append("<circle cx=\"").append(number(point.getX())).append("\" cy=\"").append(number(point.getY()))
                .append("\" r=\"").append(number(radius)).append("\" fill=\"").append(layer.color()).append('"');
        if (!toolpath) {
            svg.append(" stroke=\"").append(SHAPE_STROKE).append("\" stroke-width=\"")
                    .append(number(layer.strokeWidth())).append("\" opacity=\"0.6\"");
        }
        svg.append("/>\n");
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("SVG coordinate is not finite: " + value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }
}
