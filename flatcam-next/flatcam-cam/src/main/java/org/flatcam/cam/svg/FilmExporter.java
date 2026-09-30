package org.flatcam.cam.svg;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * appTools/ToolFilm.py: a printable film of a Gerber or Geometry, as SVG, PNG or PDF. A positive film draws the
 * features in a colour; a negative one is a black sheet with the features in white. The sheet is framed by the
 * bounding box of a second "box" object (plus a margin), after the optional scale, skew and mirror.
 *
 * <p>Differences from Python: the scale, skew and mirror act on the feature geometry itself (Python hands them to
 * the object's own SVG export), the PNG is rendered at the requested DPI instead of the odd DPI-rate scaling, and
 * the PDF is written directly as vector paths, so no extra library is needed.
 */
public final class FilmExporter {

    public enum FileType { SVG, PNG, PDF }

    public enum Mirror { NONE, X, Y, BOTH }

    public enum SkewReference { CENTER, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT }

    /**
     * @param boundary     margin around the box (Python: the negative film's "Boundary"; the positive film always
     *                     uses 1 mm / 0.0393701 in, pass that)
     * @param strokeFactor Python's "Scale Stroke": the outline is 2x this wide and drawn in the feature colour;
     *                     zero or less means Python's 0.01
     * @param pageSize     "Bounds" or a paper name (A0..A4, letter, legal, ...), for PDF only
     */
    public record Options(boolean negative, double boundary, double strokeFactor, double scaleX, double scaleY,
                          double skewX, double skewY, SkewReference skewReference, Mirror mirror, String color,
                          FileType fileType, String pageSize, boolean portrait, int pngDpi) {
        public Options {
            if (boundary < 0 || !Double.isFinite(boundary)) {
                throw new IllegalArgumentException("A borda deve ser zero ou positiva");
            }
            if (!(scaleX > 0) || !(scaleY > 0)) {
                throw new IllegalArgumentException("A escala deve ser positiva");
            }
            if (pngDpi < 1) {
                throw new IllegalArgumentException("O DPI deve ser positivo");
            }
            if (mirror == null || skewReference == null || fileType == null) {
                throw new IllegalArgumentException("options");
            }
        }

        public static Options defaults(boolean negative, String units) {
            double margin = negative ? 1.0 : "IN".equalsIgnoreCase(units) ? 0.0393701 : 1.0;
            return new Options(negative, margin, 0, 1, 1, 0, 0, SkewReference.BOTTOM_LEFT, Mirror.NONE, "#000000",
                    FileType.SVG, "A4", true, 96);
        }
    }

    /** The film content after scale/skew/mirror, and the frame (box) it is drawn in. */
    private record Prepared(Geometry film, Envelope frame) {
    }

    private FilmExporter() {
    }

    public static void write(Geometry film, Geometry box, String units, Options options, Path path) throws IOException {
        Prepared prepared = prepare(film, box, options);
        byte[] bytes = switch (options.fileType()) {
            case SVG -> svg(prepared, units, options).getBytes(StandardCharsets.UTF_8);
            case PNG -> png(prepared, units, options);
            case PDF -> pdf(prepared, units, options);
        };
        Files.write(path, bytes);
    }

    public static String svg(Geometry film, Geometry box, String units, Options options) {
        return svg(prepare(film, box, options), units, options);
    }

    // --- geometry ---------------------------------------------------------------------------------------------

    private static Prepared prepare(Geometry film, Geometry box, Options options) {
        if (film == null || film.isEmpty()) {
            throw new IllegalArgumentException("O objeto do filme nao tem geometria");
        }
        Geometry frame = box == null || box.isEmpty() ? film : box;
        AffineTransformation transform = new AffineTransformation();
        Envelope bounds = frame.getEnvelopeInternal();
        if (options.scaleX() != 1 || options.scaleY() != 1) {
            transform.compose(AffineTransformation.scaleInstance(options.scaleX(), options.scaleY(),
                    bounds.getMinX(), bounds.getMinY()));
            frame = transform(frame, transform);
        }
        if (options.skewX() != 0 || options.skewY() != 0) {
            Envelope b = frame.getEnvelopeInternal();
            Coordinate origin = switch (options.skewReference()) {
                case BOTTOM_LEFT -> new Coordinate(b.getMinX(), b.getMinY());
                case BOTTOM_RIGHT -> new Coordinate(b.getMaxX(), b.getMinY());
                case TOP_LEFT -> new Coordinate(b.getMinX(), b.getMaxY());
                case TOP_RIGHT -> new Coordinate(b.getMaxX(), b.getMaxY());
                case CENTER -> new Coordinate((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
            };
            AffineTransformation skew = new AffineTransformation();
            skew.translate(-origin.x, -origin.y);
            skew.shear(Math.tan(Math.toRadians(options.skewX())), Math.tan(Math.toRadians(options.skewY())));
            skew.translate(origin.x, origin.y);
            transform.compose(skew);
            frame = transform(frame, skew);
        }
        if (options.mirror() != Mirror.NONE) {
            Envelope b = frame.getEnvelopeInternal();
            double cx = (b.getMinX() + b.getMaxX()) / 2;
            double cy = (b.getMinY() + b.getMaxY()) / 2;
            double sx = options.mirror() == Mirror.Y || options.mirror() == Mirror.BOTH ? -1 : 1;
            double sy = options.mirror() == Mirror.X || options.mirror() == Mirror.BOTH ? -1 : 1;
            AffineTransformation mirror = AffineTransformation.scaleInstance(sx, sy, cx, cy);
            transform.compose(mirror);
            frame = transform(frame, mirror);
        }
        return new Prepared(transform(film, transform), new Envelope(frame.getEnvelopeInternal()));
    }

    private static Geometry transform(Geometry geometry, AffineTransformation transformation) {
        return transformation.transform(geometry);
    }

    private static double strokeWidth(Options options) {
        return 2 * (options.strokeFactor() > 0 ? options.strokeFactor() : 0.01);
    }

    private static List<Polygon> polygons(Geometry geometry) {
        List<Polygon> polygons = new ArrayList<>();
        collect(geometry, polygons, new ArrayList<>());
        return polygons;
    }

    private static void collect(Geometry geometry, List<Polygon> polygons, List<LineString> lines) {
        if (geometry instanceof Polygon polygon) {
            if (!polygon.isEmpty()) {
                polygons.add(polygon);
            }
        } else if (geometry instanceof LineString line) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        } else {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry part = geometry.getGeometryN(i);
                if (part != geometry) {
                    collect(part, polygons, lines);
                }
            }
        }
    }

    private static List<LineString> lines(Geometry geometry) {
        List<LineString> lines = new ArrayList<>();
        collect(geometry, new ArrayList<>(), lines);
        return lines;
    }

    // --- SVG --------------------------------------------------------------------------------------------------

    private static String svg(Prepared prepared, String units, Options options) {
        String unit = "IN".equalsIgnoreCase(units) ? "in" : "mm";
        Envelope frame = prepared.frame();
        double margin = options.boundary();
        double width = frame.getWidth() + 2 * margin;
        double height = frame.getHeight() + 2 * margin;
        double minX = frame.getMinX() - margin;
        double minY = frame.getMinY() - margin;
        String color = options.negative() ? "#FFFFFF" : options.color();
        double stroke = strokeWidth(options);

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\"")
                .append(" width=\"").append(number(width)).append(unit).append('"')
                .append(" height=\"").append(number(height)).append(unit).append('"')
                .append(" viewBox=\"").append(number(minX)).append(' ').append(number(-(minY + height))).append(' ')
                .append(number(width)).append(' ').append(number(height)).append("\">\n")
                .append("<g transform=\"scale(1,-1)\">\n");
        if (options.negative()) {
            svg.append("<rect id=\"neg_rect\" x=\"").append(number(minX)).append("\" y=\"").append(number(minY))
                    .append("\" width=\"").append(number(width)).append("\" height=\"").append(number(height))
                    .append("\" style=\"fill:#000000;opacity:1.0;stroke-width:0.0\"/>\n");
        }
        for (Polygon polygon : polygons(prepared.film())) {
            svg.append("<path fill-rule=\"evenodd\" fill=\"").append(color).append("\" stroke=\"").append(color)
                    .append("\" stroke-width=\"").append(number(stroke)).append("\" opacity=\"1.0\" d=\"");
            appendRing(svg, polygon.getExteriorRing().getCoordinates());
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
                svg.append(' ');
                appendRing(svg, polygon.getInteriorRingN(hole).getCoordinates());
            }
            svg.append("\"/>\n");
        }
        for (LineString line : lines(prepared.film())) {
            svg.append("<polyline fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"")
                    .append(number(stroke)).append("\" points=\"");
            Coordinate[] c = line.getCoordinates();
            for (int i = 0; i < c.length; i++) {
                svg.append(i == 0 ? "" : " ").append(number(c[i].x)).append(',').append(number(c[i].y));
            }
            svg.append("\"/>\n");
        }
        return svg.append("</g>\n</svg>\n").toString();
    }

    private static void appendRing(StringBuilder svg, Coordinate[] ring) {
        for (int i = 0; i < ring.length; i++) {
            svg.append(i == 0 ? "M" : " L").append(number(ring[i].x)).append(',').append(number(ring[i].y));
        }
        svg.append(" z");
    }

    private static String number(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }

    // --- PNG --------------------------------------------------------------------------------------------------

    private static byte[] png(Prepared prepared, String units, Options options) throws IOException {
        Envelope frame = prepared.frame();
        double margin = options.boundary();
        double width = frame.getWidth() + 2 * margin;
        double height = frame.getHeight() + 2 * margin;
        double pixelsPerUnit = options.pngDpi() / ("IN".equalsIgnoreCase(units) ? 1.0 : 25.4);
        int w = (int) Math.max(1, Math.round(width * pixelsPerUnit));
        int h = (int) Math.max(1, Math.round(height * pixelsPerUnit));
        if ((long) w * h > 200_000_000L) {
            throw new IllegalArgumentException("A imagem ficaria grande demais: reduza o DPI");
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            boolean negative = options.negative();
            g.setColor(negative ? Color.BLACK : Color.WHITE);
            g.fillRect(0, 0, w, h);
            // World (x, y up) -> pixels (y down), origin at the sheet's lower-left corner.
            AffineTransform world = new AffineTransform(pixelsPerUnit, 0, 0, -pixelsPerUnit,
                    -(frame.getMinX() - margin) * pixelsPerUnit, (frame.getMinY() - margin + height) * pixelsPerUnit);
            g.setColor(negative ? Color.WHITE : parseColor(options.color()));
            g.setStroke(new java.awt.BasicStroke((float) (strokeWidth(options) * pixelsPerUnit)));
            for (Polygon polygon : polygons(prepared.film())) {
                Path2D path = path(polygon, world);
                g.fill(path);
                g.draw(path);
            }
            for (LineString line : lines(prepared.film())) {
                g.draw(path(line, world));
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static Path2D path(Geometry geometry, AffineTransform world) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        if (geometry instanceof Polygon polygon) {
            addRing(path, polygon.getExteriorRing().getCoordinates(), world, true);
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                addRing(path, polygon.getInteriorRingN(i).getCoordinates(), world, true);
            }
        } else {
            addRing(path, geometry.getCoordinates(), world, false);
        }
        return path;
    }

    private static void addRing(Path2D.Double path, Coordinate[] c, AffineTransform world, boolean close) {
        double[] point = new double[2];
        for (int i = 0; i < c.length; i++) {
            point[0] = c[i].x;
            point[1] = c[i].y;
            world.transform(point, 0, point, 0, 1);
            if (i == 0) {
                path.moveTo(point[0], point[1]);
            } else {
                path.lineTo(point[0], point[1]);
            }
        }
        if (close) {
            path.closePath();
        }
    }

    private static Color parseColor(String text) {
        try {
            return Color.decode(text.trim());
        } catch (NumberFormatException invalid) {
            return Color.BLACK;
        }
    }

    // --- PDF --------------------------------------------------------------------------------------------------

    private static final double POINTS_PER_MM = 72 / 25.4;

    private static double[] paper(String name) {
        return switch (name == null ? "" : name.toUpperCase(Locale.ROOT)) {
            case "A0" -> new double[] {841, 1189};
            case "A1" -> new double[] {594, 841};
            case "A2" -> new double[] {420, 594};
            case "A3" -> new double[] {297, 420};
            case "A5" -> new double[] {148, 210};
            case "A6" -> new double[] {105, 148};
            case "LETTER" -> new double[] {215.9, 279.4};
            case "LEGAL" -> new double[] {215.9, 355.6};
            case "TABLOID", "B" -> new double[] {279.4, 431.8};
            default -> new double[] {210, 297};
        };
    }

    /** One vector page: "Bounds" sizes it to the film, otherwise the drawing sits at the paper's lower-left corner. */
    private static byte[] pdf(Prepared prepared, String units, Options options) {
        Envelope frame = prepared.frame();
        double margin = options.boundary();
        double unitToMm = "IN".equalsIgnoreCase(units) ? 25.4 : 1.0;
        double width = (frame.getWidth() + 2 * margin) * unitToMm;
        double height = (frame.getHeight() + 2 * margin) * unitToMm;
        double pageWidth = width;
        double pageHeight = height;
        if (options.pageSize() != null && !"Bounds".equalsIgnoreCase(options.pageSize())) {
            double[] paper = paper(options.pageSize());
            pageWidth = options.portrait() ? paper[0] : paper[1];
            pageHeight = options.portrait() ? paper[1] : paper[0];
        }
        double scale = POINTS_PER_MM * unitToMm;
        StringBuilder content = new StringBuilder();
        // Lower-left of the sheet at the page origin; PDF already has y up.
        content.append(String.format(Locale.ROOT, "1 0 0 1 %.4f %.4f cm%n", 0.0, 0.0));
        String sheet = String.format(Locale.ROOT, "0 0 %.4f %.4f re", width * POINTS_PER_MM, height * POINTS_PER_MM);
        if (options.negative()) {
            content.append("0 0 0 rg\n").append(sheet).append(" f\n1 1 1 rg 1 1 1 RG\n");
        } else {
            Color color = parseColor(options.color());
            content.append(String.format(Locale.ROOT, "%.4f %.4f %.4f rg %.4f %.4f %.4f RG%n", color.getRed() / 255.0,
                    color.getGreen() / 255.0, color.getBlue() / 255.0, color.getRed() / 255.0,
                    color.getGreen() / 255.0, color.getBlue() / 255.0));
        }
        content.append(String.format(Locale.ROOT, "%.4f w%n", strokeWidth(options) * scale));
        double ox = frame.getMinX() - margin;
        double oy = frame.getMinY() - margin;
        for (Polygon polygon : polygons(prepared.film())) {
            pdfRing(content, polygon.getExteriorRing().getCoordinates(), ox, oy, scale, true);
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                pdfRing(content, polygon.getInteriorRingN(i).getCoordinates(), ox, oy, scale, true);
            }
            content.append("B*\n");
        }
        for (LineString line : lines(prepared.film())) {
            pdfRing(content, line.getCoordinates(), ox, oy, scale, false);
            content.append("S\n");
        }
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.ISO_8859_1));
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.ISO_8859_1));
        objects.add(String.format(Locale.ROOT, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.4f %.4f] /Contents 4 0 R "
                + "/Resources << >> >>", pageWidth * POINTS_PER_MM, pageHeight * POINTS_PER_MM)
                .getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        contents.writeBytes(("<< /Length " + stream.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
        contents.writeBytes(stream);
        contents.writeBytes("endstream".getBytes(StandardCharsets.ISO_8859_1));
        objects.add(contents.toByteArray());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.writeBytes(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            out.writeBytes(objects.get(i));
            out.writeBytes("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format(Locale.ROOT, "%010d 00000 n %n", offset).replace("\r", ""));
        }
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        out.writeBytes(table.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static void pdfRing(StringBuilder content, Coordinate[] c, double ox, double oy, double scale, boolean close) {
        for (int i = 0; i < c.length; i++) {
            content.append(String.format(Locale.ROOT, "%.4f %.4f %s%n", (c[i].x - ox) * scale, (c[i].y - oy) * scale,
                    i == 0 ? "m" : "l"));
        }
        if (close) {
            content.append("h\n");
        }
    }
}
