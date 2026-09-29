package org.flatcam.cam.svg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * File > Import > SVG - ParseSVG.py/camlib.Geometry.import_svg: paths, rects,
 * circles, ellipses, lines, polylines, polygons, groups and {@code <use>}, with
 * their transforms, flipped so Y points up. Closed shapes become polygons, open
 * ones lines (merged end to end, as Python's linemerge).
 *
 * <p>Where Python differs, this follows the SVG spec instead: lengths are
 * converted to real millimetres/inches (a unitless or px length is 1/96 in, or what
 * Illustrator and old Inkscape meant by it - see {@link #pixelsPerInch}; Python
 * treats the width's number as FlatCAM units whatever its unit), the viewBox
 * origin and the default {@code xMidYMid meet} aspect are honoured, content of
 * {@code <defs>}/{@code <symbol>}/clip paths/masks is only drawn when used, hidden
 * elements are skipped, and a path's rings are filled even-odd (Python makes
 * the first ring the exterior and every other one a hole). Text is not imported
 * (Python renders it with its own fonts); it is counted so the caller can say so.
 */
public final class SvgImporter {

    /**
     * {@code shapes}: polygons and merged lines, for a Geometry object.
     * {@code copper}: the filled area for a Gerber object - polygons plus stroked
     * lines at their stroke width (a Gerber line needs a width; Python kept them
     * as zero-width lines).
     */
    public record Result(String units, Geometry shapes, Geometry copper, int skippedTextElements) {
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final Set<String> NEVER_DRAWN = Set.of("defs", "symbol", "clipPath", "mask", "marker",
            "pattern", "linearGradient", "radialGradient", "filter", "metadata", "title", "desc", "style",
            "script", "foreignObject", "image", "font", "font-face", "namedview", "switch");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern LENGTH = Pattern.compile(
            "\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(px|pt|pc|mm|cm|in|em|ex|%)?\\s*");
    private static final Pattern TRANSFORM = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)");
    private static final int MAX_USE_DEPTH = 32;

    private final String units;
    private final double millimetresPerPixel;
    private final double segmentLength;
    private final CancellationToken cancellation;
    private final Map<String, Element> elementsById = new HashMap<>();
    private final List<Polygon> polygons = new ArrayList<>();
    private final List<LineString> lines = new ArrayList<>();
    private final List<Geometry> strokedLines = new ArrayList<>();
    private int skippedText;

    private SvgImporter(String units, double pixelsPerInch, CancellationToken cancellation) {
        this.units = units;
        this.millimetresPerPixel = 25.4 / pixelsPerInch;
        this.segmentLength = "MM".equals(units) ? 0.1 : 0.004;
        this.cancellation = cancellation;
    }

    /** Parses an SVG file into objects in {@code units} ("MM" or "IN"). */
    public static Result parse(Path file, String units, CancellationToken cancellation) throws IOException {
        return parse(Files.readAllBytes(file), units, cancellation);
    }

    public static Result parse(String svg, String units) {
        try {
            return parse(svg.getBytes(StandardCharsets.UTF_8), units, CancellationToken.none());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Result parse(byte[] svg, String units, CancellationToken cancellation) throws IOException {
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported units: " + units);
        }
        Objects.requireNonNull(cancellation, "cancellation");
        Document document;
        try {
            document = newBuilder().parse(new ByteArrayInputStream(svg));
        } catch (SAXException malformed) {
            throw new IllegalArgumentException("Not a valid SVG/XML file: " + malformed.getMessage(), malformed);
        }
        Element root = document.getDocumentElement();
        if (!"svg".equals(localName(root))) {
            throw new IllegalArgumentException("Not an SVG file: the root element is <" + localName(root) + ">");
        }
        SvgImporter importer = new SvgImporter(units, pixelsPerInch(document), cancellation);
        importer.indexIds(root);
        importer.walkChildren(root, importer.rootTransform(root), Style.ROOT, 0);
        return importer.result();
    }

    /**
     * CSS pixels are 1/96 in, but two common writers used other sizes for a unitless
     * or px length: Adobe Illustrator 72 per inch (its px are points) and Inkscape
     * before 0.92 90 per inch. Only lengths without a real unit are affected.
     */
    static double pixelsPerInch(Document document) {
        for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.COMMENT_NODE && node.getNodeValue().contains("Adobe Illustrator")) {
                return 72;
            }
        }
        String inkscape = document.getDocumentElement()
                .getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "version");
        Matcher version = Pattern.compile("^0\\.(\\d+)").matcher(inkscape);
        if (version.find() && Integer.parseInt(version.group(1)) < 92) {
            return 90;
        }
        return 96;
    }

    private static DocumentBuilder newBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder;
        } catch (ParserConfigurationException unsupported) {
            throw new IllegalStateException(unsupported);
        }
    }

    private Result result() {
        List<Geometry> shapeParts = new ArrayList<>(polygons);
        LineMerger merger = new LineMerger();
        lines.forEach(merger::add);
        for (Object merged : merger.getMergedLineStrings()) {
            shapeParts.add((Geometry) merged);
        }
        Geometry shapes = FACTORY.buildGeometry(shapeParts);
        if (shapes.isEmpty()) {
            throw new IllegalArgumentException(skippedText > 0
                    ? "The SVG only has text, which is not imported - convert the text to paths first"
                    : "The SVG has no shapes to import");
        }
        List<Geometry> copperParts = new ArrayList<>(polygons);
        copperParts.addAll(strokedLines);
        Geometry copper = copperParts.isEmpty() ? FACTORY.createGeometryCollection()
                : UnaryUnionOp.union(copperParts.stream().map(GeometryFixer::fix).toList());
        return new Result(units, shapes, copper, skippedText);
    }

    // ---- document structure --------------------------------------------------------------------------------------

    private void indexIds(Element element) {
        String id = element.getAttribute("id");
        if (!id.isEmpty()) {
            elementsById.putIfAbsent(id, element);
        }
        for (Element child : children(element)) {
            indexIds(child);
        }
    }

    /** Maps the root's user units to output units, Y up, origin at the viewport's bottom-left. */
    private AffineTransformation rootTransform(Element root) {
        double[] viewBox = numbers(root.getAttribute("viewBox"));
        boolean hasViewBox = viewBox.length == 4 && viewBox[2] > 0 && viewBox[3] > 0;
        double width = length(root.getAttribute("width"), hasViewBox ? viewBox[2] : Double.NaN);
        double height = length(root.getAttribute("height"), hasViewBox ? viewBox[3] : Double.NaN);
        if (!hasViewBox) {
            if (Double.isNaN(width) || Double.isNaN(height)) {
                width = Double.isNaN(width) ? 0 : width;
                height = Double.isNaN(height) ? 0 : height;
            }
            double scale = fromMillimetres(millimetresPerPixel);
            AffineTransformation transform = AffineTransformation.scaleInstance(scale, -scale);
            return transform.translate(0, height);
        }
        double scaleX = width / viewBox[2];
        double scaleY = height / viewBox[3];
        double offsetX = 0;
        double offsetY = 0;
        if (!root.getAttribute("preserveAspectRatio").trim().startsWith("none")) {
            double uniform = Math.min(scaleX, scaleY);
            offsetX = (width - viewBox[2] * uniform) / 2;
            offsetY = (height - viewBox[3] * uniform) / 2;
            scaleX = uniform;
            scaleY = uniform;
        }
        // x' = (x - vx) sx + ox ; y' = height - ((y - vy) sy + oy)
        return new AffineTransformation(scaleX, 0, offsetX - viewBox[0] * scaleX,
                0, -scaleY, height - offsetY + viewBox[1] * scaleY);
    }

    /** A root width/height in output units; unitless and px are pixels (see {@link #pixelsPerInch}). */
    private double length(String text, double fallbackPixels) {
        Matcher matcher = LENGTH.matcher(text == null ? "" : text);
        if (!matcher.matches() || "%".equals(matcher.group(2)) || "em".equals(matcher.group(2))
                || "ex".equals(matcher.group(2))) {
            return Double.isNaN(fallbackPixels) ? Double.NaN : fromMillimetres(fallbackPixels * millimetresPerPixel);
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "px" : matcher.group(2);
        double millimetres = switch (unit) {
            case "mm" -> value;
            case "cm" -> value * 10;
            case "in" -> value * 25.4;
            case "pt" -> value * 25.4 / 72;
            case "pc" -> value * 25.4 / 6;
            default -> value * millimetresPerPixel;
        };
        return fromMillimetres(millimetres);
    }

    private double fromMillimetres(double millimetres) {
        return "MM".equals(units) ? millimetres : millimetres / 25.4;
    }

    private void walkChildren(Element parent, AffineTransformation transform, Style style, int useDepth) {
        for (Element child : children(parent)) {
            cancellation.throwIfCancellationRequested();
            walk(child, transform, style, useDepth);
        }
    }

    private void walk(Element element, AffineTransformation parentTransform, Style parentStyle, int useDepth) {
        String kind = localName(element);
        if (NEVER_DRAWN.contains(kind)) {
            return;
        }
        Style style = parentStyle.derive(element);
        if (style.hidden()) {
            return;
        }
        AffineTransformation transform = parseTransform(element.getAttribute("transform"));
        transform.compose(parentTransform);
        switch (kind) {
            case "g", "a" -> walkChildren(element, transform, style, useDepth);
            case "svg" -> {
                AffineTransformation nested = AffineTransformation.translationInstance(
                        number(element, "x"), number(element, "y"));
                walkChildren(element, nested.compose(transform), style, useDepth);
            }
            case "use" -> use(element, transform, style, useDepth);
            case "path" -> path(element.getAttribute("d"), transform, style);
            case "rect" -> rect(element, transform, style);
            case "circle" -> ellipse(number(element, "cx"), number(element, "cy"),
                    number(element, "r"), number(element, "r"), transform, style);
            case "ellipse" -> ellipse(number(element, "cx"), number(element, "cy"),
                    number(element, "rx"), number(element, "ry"), transform, style);
            case "line" -> addOpen(List.of(new Coordinate(number(element, "x1"), number(element, "y1")),
                    new Coordinate(number(element, "x2"), number(element, "y2"))), transform, style);
            case "polyline", "polygon" -> {
                double[] values = numbers(element.getAttribute("points"));
                List<Coordinate> points = new ArrayList<>();
                for (int index = 0; index + 1 < values.length; index += 2) {
                    points.add(new Coordinate(values[index], values[index + 1]));
                }
                if ("polygon".equals(kind)) {
                    addClosed(List.of(points), transform, style);
                } else {
                    addOpen(points, transform, style);
                }
            }
            case "text", "tspan", "textPath", "flowRoot" -> skippedText++;
            default -> walkChildren(element, transform, style, useDepth); // unknown containers
        }
    }

    private void use(Element use, AffineTransformation transform, Style style, int useDepth) {
        String href = use.getAttribute("href");
        if (href.isEmpty()) {
            href = use.getAttributeNS("http://www.w3.org/1999/xlink", "href");
        }
        Element target = href.startsWith("#") ? elementsById.get(href.substring(1)) : null;
        if (target == null || useDepth >= MAX_USE_DEPTH) {
            return;
        }
        AffineTransformation placed = AffineTransformation.translationInstance(number(use, "x"), number(use, "y"));
        placed.compose(transform);
        String kind = localName(target);
        if ("symbol".equals(kind) || "defs".equals(kind)) {
            walkChildren(target, placed, style.derive(target), useDepth + 1);
        } else if (!NEVER_DRAWN.contains(kind)) {
            walk(target, placed, style, useDepth + 1);
        }
    }

    // ---- shapes ----------------------------------------------------------------------------------------------------

    private void rect(Element rect, AffineTransformation transform, Style style) {
        double x = number(rect, "x");
        double y = number(rect, "y");
        double width = number(rect, "width");
        double height = number(rect, "height");
        if (!(width > 0) || !(height > 0)) {
            return;
        }
        double rx = rect.hasAttribute("rx") ? number(rect, "rx") : Double.NaN;
        double ry = rect.hasAttribute("ry") ? number(rect, "ry") : Double.NaN;
        if (Double.isNaN(rx)) {
            rx = Double.isNaN(ry) ? 0 : ry;
        }
        if (Double.isNaN(ry)) {
            ry = rx;
        }
        rx = Math.min(Math.max(rx, 0), width / 2);
        ry = Math.min(Math.max(ry, 0), height / 2);
        List<Coordinate> ring = new ArrayList<>();
        if (rx == 0 || ry == 0) {
            ring.add(new Coordinate(x, y));
            ring.add(new Coordinate(x + width, y));
            ring.add(new Coordinate(x + width, y + height));
            ring.add(new Coordinate(x, y + height));
        } else {
            int steps = curveSteps(Math.PI / 2 * Math.max(rx, ry), transform);
            corner(ring, x + width - rx, y + ry, rx, ry, -Math.PI / 2, steps);
            corner(ring, x + width - rx, y + height - ry, rx, ry, 0, steps);
            corner(ring, x + rx, y + height - ry, rx, ry, Math.PI / 2, steps);
            corner(ring, x + rx, y + ry, rx, ry, Math.PI, steps);
        }
        addClosed(List.of(ring), transform, style);
    }

    private static void corner(List<Coordinate> ring, double cx, double cy, double rx, double ry,
                               double start, int steps) {
        for (int step = 0; step <= steps; step++) {
            double angle = start + Math.PI / 2 * step / steps;
            ring.add(new Coordinate(cx + rx * Math.cos(angle), cy + ry * Math.sin(angle)));
        }
    }

    private void ellipse(double cx, double cy, double rx, double ry, AffineTransformation transform, Style style) {
        if (!(rx > 0) || !(ry > 0)) {
            return;
        }
        int steps = Math.max(32, curveSteps(2 * Math.PI * Math.max(rx, ry), transform));
        List<Coordinate> ring = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            double angle = 2 * Math.PI * step / steps;
            ring.add(new Coordinate(cx + rx * Math.cos(angle), cy + ry * Math.sin(angle)));
        }
        addClosed(List.of(ring), transform, style);
    }

    /** Closed rings of one element, filled even-odd (a ring inside another is a hole). */
    private void addClosed(List<List<Coordinate>> rings, AffineTransformation transform, Style style) {
        Geometry filled = null;
        for (List<Coordinate> ring : rings) {
            CoordinateList coordinates = transformed(ring, transform);
            coordinates.closeRing();
            if (coordinates.size() < 4) {
                continue;
            }
            Geometry polygon = GeometryFixer.fix(FACTORY.createPolygon(coordinates.toCoordinateArray()));
            if (polygon.isEmpty()) {
                continue;
            }
            filled = filled == null ? polygon : filled.symDifference(polygon);
        }
        if (filled == null) {
            return;
        }
        for (int part = 0; part < filled.getNumGeometries(); part++) {
            if (filled.getGeometryN(part) instanceof Polygon polygon && !polygon.isEmpty()) {
                polygons.add(polygon);
            }
        }
    }

    private void addOpen(List<Coordinate> points, AffineTransformation transform, Style style) {
        CoordinateList coordinates = transformed(points, transform);
        if (coordinates.size() < 2) {
            return;
        }
        LineString line = FACTORY.createLineString(coordinates.toCoordinateArray());
        lines.add(line);
        double width = style.strokeWidth() * scaleOf(transform);
        if (style.stroked() && width > 0) {
            strokedLines.add(line.buffer(width / 2, 8));
        }
    }

    private static CoordinateList transformed(List<Coordinate> points, AffineTransformation transform) {
        CoordinateList coordinates = new CoordinateList();
        for (Coordinate point : points) {
            Coordinate target = new Coordinate();
            transform.transform(point, target);
            if (Double.isFinite(target.x) && Double.isFinite(target.y)) {
                coordinates.add(target, false);
            }
        }
        return coordinates;
    }

    // ---- path data ---------------------------------------------------------------------------------------------------

    private void path(String data, AffineTransformation transform, Style style) {
        PathTokens tokens = new PathTokens(data);
        List<List<Coordinate>> closed = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        double x = 0;
        double y = 0;
        double startX = 0;
        double startY = 0;
        double controlX = 0;
        double controlY = 0;
        char previous = ' ';
        char command = ' ';
        while (tokens.hasMore()) {
            if (tokens.nextIsCommand()) {
                command = tokens.command();
            } else if (command == ' ') {
                throw new IllegalArgumentException("SVG path data must start with a command: " + data);
            } else if (command == 'M' || command == 'm') {
                command = command == 'M' ? 'L' : 'l'; // extra move pairs are line-tos
            } else if (command == 'Z' || command == 'z') {
                throw new IllegalArgumentException("Unexpected number after Z in SVG path: " + data);
            }
            boolean relative = Character.isLowerCase(command);
            double baseX = relative ? x : 0;
            double baseY = relative ? y : 0;
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    finishOpen(current, transform, style);
                    x = baseX + tokens.number();
                    y = baseY + tokens.number();
                    startX = x;
                    startY = y;
                    current.add(new Coordinate(x, y));
                }
                case 'L' -> {
                    x = baseX + tokens.number();
                    y = baseY + tokens.number();
                    lineTo(current, x, y);
                }
                case 'H' -> {
                    x = baseX + tokens.number();
                    lineTo(current, x, y);
                }
                case 'V' -> {
                    y = baseY + tokens.number();
                    lineTo(current, x, y);
                }
                case 'C', 'S' -> {
                    double x1;
                    double y1;
                    if (Character.toUpperCase(command) == 'C') {
                        x1 = baseX + tokens.number();
                        y1 = baseY + tokens.number();
                    } else {
                        boolean smooth = "CcSs".indexOf(previous) >= 0;
                        x1 = smooth ? 2 * x - controlX : x;
                        y1 = smooth ? 2 * y - controlY : y;
                    }
                    double x2 = baseX + tokens.number();
                    double y2 = baseY + tokens.number();
                    double endX = baseX + tokens.number();
                    double endY = baseY + tokens.number();
                    cubic(current, x, y, x1, y1, x2, y2, endX, endY, transform);
                    controlX = x2;
                    controlY = y2;
                    x = endX;
                    y = endY;
                }
                case 'Q', 'T' -> {
                    double x1;
                    double y1;
                    if (Character.toUpperCase(command) == 'Q') {
                        x1 = baseX + tokens.number();
                        y1 = baseY + tokens.number();
                    } else {
                        boolean smooth = "QqTt".indexOf(previous) >= 0;
                        x1 = smooth ? 2 * x - controlX : x;
                        y1 = smooth ? 2 * y - controlY : y;
                    }
                    double endX = baseX + tokens.number();
                    double endY = baseY + tokens.number();
                    // A quadratic is the cubic with control points 2/3 of the way to its one control point.
                    cubic(current, x, y, x + 2.0 / 3 * (x1 - x), y + 2.0 / 3 * (y1 - y),
                            endX + 2.0 / 3 * (x1 - endX), endY + 2.0 / 3 * (y1 - endY), endX, endY, transform);
                    controlX = x1;
                    controlY = y1;
                    x = endX;
                    y = endY;
                }
                case 'A' -> {
                    double rx = Math.abs(tokens.number());
                    double ry = Math.abs(tokens.number());
                    double rotation = tokens.number();
                    boolean largeArc = tokens.flag();
                    boolean sweep = tokens.flag();
                    double endX = baseX + tokens.number();
                    double endY = baseY + tokens.number();
                    arc(current, x, y, rx, ry, rotation, largeArc, sweep, endX, endY, transform);
                    x = endX;
                    y = endY;
                }
                case 'Z' -> {
                    if (current.size() > 1) {
                        closed.add(current);
                    }
                    current = new ArrayList<>();
                    x = startX;
                    y = startY;
                    // A command after Z without a move starts at the closed subpath's start.
                    current.add(new Coordinate(x, y));
                }
                default -> throw new IllegalArgumentException("Unknown SVG path command '" + command + "'");
            }
            previous = command;
        }
        finishOpen(current, transform, style, closed);
        addClosed(closed, transform, style);
    }

    private void finishOpen(List<Coordinate> current, AffineTransformation transform, Style style) {
        if (current.size() > 1) {
            addOpen(new ArrayList<>(current), transform, style);
        }
        current.clear();
    }

    /** Last subpath: Python treats an unclosed one that ends where it started as a polygon. */
    private void finishOpen(List<Coordinate> current, AffineTransformation transform, Style style,
                            List<List<Coordinate>> closed) {
        if (current.size() > 3 && current.get(0).distance(current.get(current.size() - 1)) < 1e-9) {
            closed.add(new ArrayList<>(current));
            current.clear();
        } else {
            finishOpen(current, transform, style);
        }
    }

    private static void lineTo(List<Coordinate> current, double x, double y) {
        current.add(new Coordinate(x, y));
    }

    private void cubic(List<Coordinate> current, double x0, double y0, double x1, double y1, double x2, double y2,
                       double x3, double y3, AffineTransformation transform) {
        double hull = Math.hypot(x1 - x0, y1 - y0) + Math.hypot(x2 - x1, y2 - y1) + Math.hypot(x3 - x2, y3 - y2);
        int steps = curveSteps(hull, transform);
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            double u = 1 - t;
            double a = u * u * u;
            double b = 3 * u * u * t;
            double c = 3 * u * t * t;
            double d = t * t * t;
            current.add(new Coordinate(a * x0 + b * x1 + c * x2 + d * x3, a * y0 + b * y1 + c * y2 + d * y3));
        }
    }

    /** SVG endpoint arc to center parameterization - SVG 1.1 appendix F.6.5/F.6.6. */
    private void arc(List<Coordinate> current, double x0, double y0, double rx, double ry, double rotationDegrees,
                     boolean largeArc, boolean sweep, double x, double y, AffineTransformation transform) {
        if ((x0 == x && y0 == y)) {
            return;
        }
        if (rx == 0 || ry == 0) {
            current.add(new Coordinate(x, y));
            return;
        }
        double phi = Math.toRadians(rotationDegrees % 360);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double dx = (x0 - x) / 2;
        double dy = (y0 - y) / 2;
        double x1p = cos * dx + sin * dy;
        double y1p = -sin * dx + cos * dy;
        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            double grow = Math.sqrt(lambda);
            rx *= grow;
            ry *= grow;
        }
        double numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coefficient = Math.sqrt(Math.max(0, numerator / denominator)) * (largeArc == sweep ? -1 : 1);
        double cxp = coefficient * rx * y1p / ry;
        double cyp = -coefficient * ry * x1p / rx;
        double cx = cos * cxp - sin * cyp + (x0 + x) / 2;
        double cy = sin * cxp + cos * cyp + (y0 + y) / 2;
        double start = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
        double end = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
        double delta = end - start;
        if (sweep && delta < 0) {
            delta += 2 * Math.PI;
        } else if (!sweep && delta > 0) {
            delta -= 2 * Math.PI;
        }
        int steps = curveSteps(Math.abs(delta) * Math.max(rx, ry), transform);
        for (int step = 1; step <= steps; step++) {
            double angle = start + delta * step / steps;
            double ex = rx * Math.cos(angle);
            double ey = ry * Math.sin(angle);
            current.add(new Coordinate(cos * ex - sin * ey + cx, sin * ex + cos * ey + cy));
        }
        current.set(current.size() - 1, new Coordinate(x, y));
    }

    /** Segments for a curve of the given user-unit length, about {@link #segmentLength} long once drawn. */
    private int curveSteps(double userLength, AffineTransformation transform) {
        double drawn = userLength * scaleOf(transform);
        if (!Double.isFinite(drawn)) {
            return 8;
        }
        return (int) Math.max(8, Math.min(2000, Math.ceil(drawn / segmentLength)));
    }

    private static double scaleOf(AffineTransformation transform) {
        double[] m = transform.getMatrixEntries();
        return Math.sqrt(Math.abs(m[0] * m[4] - m[1] * m[3]));
    }

    // ---- attributes ------------------------------------------------------------------------------------------------

    /** SVG transform list: the rightmost function applies first. */
    static AffineTransformation parseTransform(String text) {
        AffineTransformation result = new AffineTransformation();
        if (text == null || text.isBlank()) {
            return result;
        }
        List<AffineTransformation> functions = new ArrayList<>();
        Matcher matcher = TRANSFORM.matcher(text);
        while (matcher.find()) {
            double[] v = numbers(matcher.group(2));
            AffineTransformation function = switch (matcher.group(1)) {
                case "matrix" -> v.length == 6
                        ? new AffineTransformation(v[0], v[2], v[4], v[1], v[3], v[5]) : new AffineTransformation();
                case "translate" -> AffineTransformation.translationInstance(at(v, 0, 0), at(v, 1, 0));
                case "scale" -> AffineTransformation.scaleInstance(at(v, 0, 1), at(v, 1, at(v, 0, 1)));
                case "rotate" -> AffineTransformation.rotationInstance(Math.toRadians(at(v, 0, 0)),
                        at(v, 1, 0), at(v, 2, 0));
                case "skewX" -> AffineTransformation.shearInstance(Math.tan(Math.toRadians(at(v, 0, 0))), 0);
                case "skewY" -> AffineTransformation.shearInstance(0, Math.tan(Math.toRadians(at(v, 0, 0))));
                default -> new AffineTransformation();
            };
            functions.add(function);
        }
        for (int index = functions.size() - 1; index >= 0; index--) {
            result.compose(functions.get(index));
        }
        return result;
    }

    private static double at(double[] values, int index, double fallback) {
        return index < values.length ? values[index] : fallback;
    }

    private static double[] numbers(String text) {
        if (text == null || text.isBlank()) {
            return new double[0];
        }
        List<Double> values = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group()));
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** A coordinate/length attribute in user units (a unit suffix is ignored, as in Python). */
    private static double number(Element element, String attribute) {
        Matcher matcher = NUMBER.matcher(element.getAttribute(attribute));
        return matcher.lookingAt() || matcher.find() ? Double.parseDouble(matcher.group()) : 0;
    }

    private static String localName(Node node) {
        String name = node.getLocalName();
        return name != null ? name : node.getNodeName();
    }

    private static List<Element> children(Element element) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }

    /** The inherited presentation properties that matter for geometry. */
    private record Style(boolean stroked, double strokeWidth, boolean hidden) {
        static final Style ROOT = new Style(false, 1, false);

        Style derive(Element element) {
            Map<String, String> properties = new HashMap<>();
            for (String attribute : List.of("stroke", "stroke-width", "display", "visibility")) {
                if (element.hasAttribute(attribute)) {
                    properties.put(attribute, element.getAttribute(attribute).trim());
                }
            }
            for (String declaration : element.getAttribute("style").split(";")) {
                int colon = declaration.indexOf(':');
                if (colon > 0) {
                    properties.put(declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            declaration.substring(colon + 1).trim());
                }
            }
            boolean nowStroked = properties.containsKey("stroke")
                    ? !"none".equalsIgnoreCase(properties.get("stroke")) : stroked;
            double width = strokeWidth;
            if (properties.containsKey("stroke-width")) {
                Matcher matcher = NUMBER.matcher(properties.get("stroke-width"));
                if (matcher.lookingAt()) {
                    width = Double.parseDouble(matcher.group());
                }
            }
            boolean nowHidden = "none".equalsIgnoreCase(properties.get("display"))
                    || "hidden".equalsIgnoreCase(properties.get("visibility"));
            return new Style(nowStroked, width, hidden || nowHidden);
        }
    }

    /** Path data tokenizer: commands, numbers written back to back ("1.5.5", "1-2") and arc flags ("011"). */
    private static final class PathTokens {
        private final String data;
        private int position;

        PathTokens(String data) {
            this.data = data == null ? "" : data;
        }

        boolean hasMore() {
            skipSeparators();
            return position < data.length();
        }

        boolean nextIsCommand() {
            skipSeparators();
            return position < data.length() && Character.isLetter(data.charAt(position))
                    && "eE".indexOf(data.charAt(position)) < 0;
        }

        char command() {
            skipSeparators();
            return data.charAt(position++);
        }

        double number() {
            skipSeparators();
            Matcher matcher = NUMBER.matcher(data);
            matcher.region(position, data.length());
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("Malformed SVG path data near: "
                        + data.substring(position, Math.min(data.length(), position + 20)));
            }
            position = matcher.end();
            return Double.parseDouble(matcher.group());
        }

        boolean flag() {
            skipSeparators();
            if (position < data.length() && (data.charAt(position) == '0' || data.charAt(position) == '1')) {
                return data.charAt(position++) == '1';
            }
            throw new IllegalArgumentException("Malformed SVG arc flag in path data");
        }

        private void skipSeparators() {
            while (position < data.length()
                    && (Character.isWhitespace(data.charAt(position)) || data.charAt(position) == ',')) {
                position++;
            }
        }
    }
}
