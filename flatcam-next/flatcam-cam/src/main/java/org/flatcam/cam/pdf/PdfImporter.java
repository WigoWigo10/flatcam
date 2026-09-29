package org.flatcam.cam.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * File > Import > PDF - ToolPDF.py/ParsePDF.py: a hand-written reader for the
 * one PDF style FlatCAM targets - a vector print of Gerber-like artwork, drawn
 * with plain path operators in the page's content streams. Round white-filled
 * shapes become drill holes (an Excellon layer); every stroke-color change
 * starts a new Gerber layer.
 *
 * <p>Unlike Python, operators are read as the token stream a PDF content
 * stream actually is, not one operator per source line: Python's own regexes
 * are anchored to a whole line, so any operator sharing a line with another
 * is silently dropped - which real generators do (reportlab, for one, writes
 * {@code n} immediately followed by a whole {@code re S} on the same line).
 *
 * <p>This is not a general PDF reader: no xref/object-stream tables, no
 * encryption, no images or fonts, no rotation/skew (a {@code cm} matrix is
 * read only for its axis-aligned scale and translation, exactly like Python -
 * a rotated or sheared page silently keeps the untransformed coordinates), and
 * no follow/centreline geometry (Gerber objects created this way only carry
 * solid copper, like this importer's SVG/DXF siblings). Content is found by
 * scanning for {@code FlateDecode ... stream ... endstream} the way Python's
 * own regex does, not by parsing the object/xref structure, so a compressed
 * stream that happens to contain the literal bytes "endstream" would be cut
 * short - a risk Python's own extraction carries too.
 *
 * <p>Bugs fixed relative to ParsePDF.py: a filled path built from several
 * chained Bezier subpaths produced extra, wrong polygons (one growing polygon
 * per curve segment, from points that were never reset between segments)
 * instead of one polygon for the whole subpath; the last point of every
 * Bezier segment was dropped (its sampling never reaches t=1); a rectangle's
 * width/height had the position offset added a second time; the {@code y}
 * curve operator never marked its subpath as a curve, so a subpath drawn only
 * with {@code y} lost its geometry; and {@code s}/{@code b}/{@code b*}
 * (close+stroke, close+fill+stroke) were never recognized at all, even though
 * Python compiled patterns for them. Everything else - the layering, the
 * transform heuristic, which fills count as holes, the empirical
 * hole-diameter correction - follows Python as the intended behaviour. A
 * subpath that switches between straight, curved and rectangular drawing
 * without an intervening {@code h} keeps only its most recent kind (matching
 * Python's own per-kind buffers), continuing from the pen's current point
 * rather than Python's own inconsistent behaviour there (which sometimes
 * reconnects to the subpath's original start instead) - an obscure case
 * real Gerber-style PDFs are not expected to hit.
 */
public final class PdfImporter {

    /** {@code layers} in stroke-color order; {@code drills} is null when no round white fill was found. */
    public record Result(String units, ExcellonImage drills, List<GerberImage> layers) {
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();
    /** Python's gerber_circle_steps default - quadrant segments for Bezier flattening and stroke-width buffering. */
    private static final int CIRCLE_STEPS = 64;
    /** Python's bbox-to-diameter correction for a hole rendered as a filled circle; derivation unknown. */
    private static final double DRILL_DIAMETER_CORRECTION = 0.974;

    private PdfImporter() {
    }

    public static Result parse(Path file, String units, CancellationToken cancellation) throws IOException {
        return parse(Files.readAllBytes(file), units, cancellation);
    }

    public static Result parse(byte[] pdf, String units, CancellationToken cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        String content = extractContentStreams(pdf);
        if (content.isEmpty()) {
            throw new IllegalArgumentException("No FlateDecode content stream found - "
                    + "this importer only reads a plain vector print of Gerber-like artwork");
        }
        return parseContent(content, units, cancellation);
    }

    /** Parses an already-decompressed, concatenated content stream - the seam tests reach past PDF/zlib. */
    static Result parseContent(String content, String units, CancellationToken cancellation) {
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported units: " + units);
        }
        Result result = new ContentStreamParser(units, cancellation).run(content);
        if (result.drills() == null && result.layers().isEmpty()) {
            throw new IllegalArgumentException("No supported drawing found in the PDF content streams");
        }
        return result;
    }

    // ---- PDF container: find and decompress every FlateDecode stream -----------------------------------------------

    /**
     * Mirrors ParsePDF's own extraction: scan for the next "FlateDecode", then the next
     * "stream" after it, then the next "endstream" after that, decompress what is between
     * "stream" and "endstream" (trimming leading/trailing CR/LF), and resume scanning after
     * that "endstream". No xref or per-object {@code /Length} is consulted.
     */
    private static String extractContentStreams(byte[] pdf) {
        String latin1 = new String(pdf, StandardCharsets.ISO_8859_1); // 1:1 byte<->char for keyword scanning
        StringBuilder content = new StringBuilder();
        int position = 0;
        while (true) {
            int flateAt = latin1.indexOf("FlateDecode", position);
            if (flateAt < 0) {
                break;
            }
            int streamAt = latin1.indexOf("stream", flateAt);
            if (streamAt < 0) {
                break;
            }
            int endAt = latin1.indexOf("endstream", streamAt + "stream".length());
            if (endAt < 0) {
                break;
            }
            byte[] raw = stripCrLf(pdf, streamAt + "stream".length(), endAt);
            content.append(inflateUtf8(raw)).append("\r\n");
            position = endAt + "endstream".length();
        }
        return content.toString();
    }

    private static byte[] stripCrLf(byte[] data, int from, int to) {
        int start = from;
        int end = to;
        while (start < end && (data[start] == '\r' || data[start] == '\n')) {
            start++;
        }
        while (end > start && (data[end - 1] == '\r' || data[end - 1] == '\n')) {
            end--;
        }
        return Arrays.copyOfRange(data, start, end);
    }

    private static String inflateUtf8(byte[] compressed) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream inflated = new ByteArrayOutputStream(Math.max(64, compressed.length * 3));
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    // Truncated or otherwise incomplete stream - Python's zlib.decompress() raises here too.
                    throw new IllegalArgumentException("A PDF content stream is truncated or incomplete");
                }
                inflated.write(buffer, 0, count);
            }
        } catch (DataFormatException malformed) {
            throw new IllegalArgumentException("A PDF content stream is not valid FlateDecode data: "
                    + malformed.getMessage(), malformed);
        } finally {
            inflater.end();
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(inflated.toByteArray())).toString();
        } catch (CharacterCodingException notUtf8) {
            throw new IllegalArgumentException("A PDF content stream is not UTF-8 text", notUtf8);
        }
    }

    // ---- content stream operators ------------------------------------------------------------------------------------

    /** One stroke-color layer being accumulated: copper to keep, local white fills to cut out of it. */
    private static final class Layer {
        final List<Geometry> solids = new ArrayList<>();
        final List<Geometry> localClears = new ArrayList<>();

        boolean isEmpty() {
            return solids.isEmpty() && localClears.isEmpty();
        }
    }

    private enum SubpathKind { LINES, BEZIER, RECT }

    private record Subpath(SubpathKind kind, List<Coordinate> points) {
    }

    private static final class ContentStreamParser {
        private final String units;
        private final CancellationToken cancellation;
        private final double pointToUnit;

        // ---- current path being built (possibly several m..h subpaths before one paint op) ----
        private final List<Subpath> chainedSubpaths = new ArrayList<>();
        private SubpathKind openKind;
        private final List<Coordinate> openLines = new ArrayList<>();
        private final List<double[][]> openBezierSegments = new ArrayList<>(); // each: {start,c1,c2,stop} x,y pairs
        private List<Coordinate> openRect;
        private boolean pathClosed; // Python's close_subpath: true right after 'h'
        private Coordinate currentPoint = new Coordinate(0, 0);

        // ---- graphics state ----
        private double offsetX;
        private double offsetY;
        private double scaleX = 1;
        private double scaleY = 1;
        private double strokeWidthPoints;
        private final Deque<double[]> stateStack = new ArrayDeque<>(); // {offX,offY,scaleX,scaleY,strokeWidth}
        private double[] strokeColor;
        private boolean fillIsWhite;

        // ---- layers ----
        private final Map<Integer, Layer> objectLayers = new TreeMap<>();
        private final List<Geometry> globalClears = new ArrayList<>();
        private int layerNumber = 1;
        private Layer currentLayer = new Layer();

        ContentStreamParser(String units, CancellationToken cancellation) {
            this.units = units;
            this.cancellation = cancellation;
            this.pointToUnit = "MM".equals(units) ? 25.4 / 72.0 : 1.0 / 72.0;
        }

        /**
         * A parenthesized string, a {@code <...>} hex string/dictionary, or any other run of
         * non-space characters - real content streams are a token stream, not one operator per
         * line (Python assumed the latter, so it silently drops every operator sharing a line
         * with another, which real-world generators do - reportlab, for one, writes {@code n}
         * directly followed by a whole {@code re S} on one line).
         */
        private static final Pattern TOKEN =
                Pattern.compile("\\((?:[^()\\\\]|\\\\.)*\\)|<[^>]*>|[^\\s]+");

        Result run(String content) {
            Matcher matcher = TOKEN.matcher(content);
            List<Double> pending = new ArrayList<>();
            int tokenCount = 0;
            while (matcher.find()) {
                if ((++tokenCount & 0xFFF) == 0) {
                    cancellation.throwIfCancellationRequested();
                }
                String token = matcher.group();
                Double number = asNumber(token);
                if (number != null) {
                    pending.add(number);
                } else {
                    operator(token, pending);
                    pending.clear();
                }
            }
            return buildResult();
        }

        private static Double asNumber(String token) {
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException notNumeric) {
                return null;
            }
        }

        /** {@code operands} holds every number seen since the previous operator; only the last few matter. */
        private void operator(String op, List<Double> operands) {
            switch (op) {
                case "RG" -> { double[] n = takeLast(operands, 3); if (n != null) strokeColorChanged(n); }
                case "rg" -> {
                    double[] n = takeLast(operands, 3);
                    if (n != null) fillIsWhite = n[0] == 1 && n[1] == 1 && n[2] == 1;
                }
                case "cm" -> { double[] n = takeLast(operands, 6); if (n != null) combinedTransform(n); }
                case "m" -> { double[] n = takeLast(operands, 2); if (n != null) moveTo(n[0], n[1]); }
                case "l" -> { double[] n = takeLast(operands, 2); if (n != null) lineTo(n[0], n[1]); }
                case "c" -> {
                    double[] n = takeLast(operands, 6);
                    if (n != null) curveTo(n[0], n[1], n[2], n[3], n[4], n[5]);
                }
                case "v" -> { double[] n = takeLast(operands, 4); if (n != null) curveV(n[0], n[1], n[2], n[3]); }
                case "y" -> { double[] n = takeLast(operands, 4); if (n != null) curveY(n[0], n[1], n[2], n[3]); }
                case "re" -> { double[] n = takeLast(operands, 4); if (n != null) rectangle(n[0], n[1], n[2], n[3]); }
                case "h" -> closeSubpath();
                case "w" -> { double[] n = takeLast(operands, 1); if (n != null) strokeWidthPoints = n[0]; }
                case "n" -> { chainedSubpaths.clear(); clearOpenSubpath(); } // Python only cleared the open subpath
                case "S" -> paint(true, false);
                case "s" -> { closeSubpath(); paint(true, false); } // Python never recognized 's'
                case "f", "F", "f*" -> paint(false, true);
                case "B", "B*" -> paint(true, true);
                case "b", "b*" -> { closeSubpath(); paint(true, true); } // Python never recognized 'b'/'b*'
                case "W", "W*" -> clip();
                case "q" -> pushState();
                case "Q" -> popState();
                default -> { } // an operator this importer has no use for (text, color space, images, ...)
            }
        }

        private static double[] takeLast(List<Double> operands, int count) {
            if (operands.size() < count) {
                return null;
            }
            double[] result = new double[count];
            int start = operands.size() - count;
            for (int index = 0; index < count; index++) {
                result[index] = operands.get(start + index);
            }
            return result;
        }

        // ---- transforms ------------------------------------------------------------------------------------------------

        /** {@code a b c d e f cm}: only a pure translate or a pure scale is extracted, like Python. */
        private void combinedTransform(double[] n) {
            double a = n[0];
            double b = n[1];
            double c = n[2];
            double d = n[3];
            double e = n[4];
            double f = n[5];
            if (b == 0 && c == 0 && (e != 0 || f != 0)) {
                offsetX += e;
                offsetY += f;
            }
            if (a != 1 && d != 1) {
                scaleX *= a;
                scaleY *= d;
            }
        }

        private void pushState() {
            stateStack.push(new double[]{offsetX, offsetY, scaleX, scaleY, strokeWidthPoints});
        }

        private void popState() {
            double[] state = stateStack.poll();
            if (state != null) {
                offsetX = state[0];
                offsetY = state[1];
                scaleX = state[2];
                scaleY = state[3];
                strokeWidthPoints = state[4];
            }
        }

        // ---- path construction ------------------------------------------------------------------------------------

        private Coordinate toOutputUnits(double x, double y) {
            return new Coordinate((x + offsetX) * pointToUnit * scaleX, (y + offsetY) * pointToUnit * scaleY);
        }

        private void moveTo(double x, double y) {
            clearOpenSubpath();
            pathClosed = false;
            currentPoint = toOutputUnits(x, y);
        }

        private void lineTo(double x, double y) {
            if (openKind != SubpathKind.LINES) {
                clearOpenSubpath();
                openKind = SubpathKind.LINES;
                openLines.add(currentPoint);
            }
            currentPoint = toOutputUnits(x, y);
            openLines.add(currentPoint);
        }

        private void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
            addBezierSegment(toOutputUnits(x1, y1), toOutputUnits(x2, y2), toOutputUnits(x3, y3));
        }

        /** {@code v}: the current point doubles as the first control point. */
        private void curveV(double x2, double y2, double x3, double y3) {
            addBezierSegment(currentPoint, toOutputUnits(x2, y2), toOutputUnits(x3, y3));
        }

        /** {@code y}: the stop point doubles as the second control point. */
        private void curveY(double x1, double y1, double x3, double y3) {
            Coordinate stop = toOutputUnits(x3, y3);
            addBezierSegment(toOutputUnits(x1, y1), stop, stop);
        }

        private void addBezierSegment(Coordinate c1, Coordinate c2, Coordinate stop) {
            if (openKind != SubpathKind.BEZIER) {
                clearOpenSubpath();
                openKind = SubpathKind.BEZIER;
            }
            openBezierSegments.add(new double[][]{{currentPoint.x, currentPoint.y}, {c1.x, c1.y},
                    {c2.x, c2.y}, {stop.x, stop.y}});
            currentPoint = stop;
        }

        private void rectangle(double x, double y, double width, double height) {
            clearOpenSubpath();
            pathClosed = false; // 're' implicitly starts (and closes) its own subpath, per the PDF spec
            Coordinate p1 = toOutputUnits(x, y);
            // Width/height are sizes, not points - only the origin gets the position offset (Python added it twice).
            double x2 = p1.x + width * pointToUnit * scaleX;
            double y2 = p1.y + height * pointToUnit * scaleY;
            openKind = SubpathKind.RECT;
            openRect = new ArrayList<>(List.of(p1, new Coordinate(x2, p1.y), new Coordinate(x2, y2),
                    new Coordinate(p1.x, y2), p1));
            currentPoint = p1;
        }

        private void closeSubpath() {
            pathClosed = true;
            if (openKind != null) {
                List<Coordinate> points = flattenOpenSubpath();
                if (openKind != SubpathKind.BEZIER && !points.get(0).equals2D(points.get(points.size() - 1))) {
                    points.add(points.get(0));
                }
                chainedSubpaths.add(new Subpath(openKind, points));
            }
            clearOpenSubpath();
        }

        /** {@code W}/{@code W*}: the current path marks a clip region and paints nothing. */
        private void clip() {
            if (pathClosed && !chainedSubpaths.isEmpty()) {
                chainedSubpaths.remove(chainedSubpaths.size() - 1);
            }
            clearOpenSubpath();
        }

        private void clearOpenSubpath() {
            openKind = null;
            openLines.clear();
            openBezierSegments.clear();
            openRect = null;
        }

        private List<Coordinate> flattenOpenSubpath() {
            if (openKind == null) {
                return null;
            }
            return switch (openKind) {
                case LINES -> new ArrayList<>(openLines);
                case RECT -> new ArrayList<>(openRect);
                case BEZIER -> flattenBezier(openBezierSegments);
            };
        }

        /** Cubic Bezier flattening, including each segment's own endpoint (Python's sampling never reaches it). */
        private static List<Coordinate> flattenBezier(List<double[][]> segments) {
            List<Coordinate> points = new ArrayList<>();
            for (double[][] segment : segments) {
                double[] p0 = segment[0];
                double[] p1 = segment[1];
                double[] p2 = segment[2];
                double[] p3 = segment[3];
                for (int step = 0; step <= CIRCLE_STEPS; step++) {
                    double t = (double) step / CIRCLE_STEPS;
                    double u = 1 - t;
                    double a = u * u * u;
                    double b = 3 * u * u * t;
                    double c = 3 * u * t * t;
                    double d = t * t * t;
                    points.add(new Coordinate(a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0],
                            a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]));
                }
            }
            return points;
        }

        // ---- painting ---------------------------------------------------------------------------------------------

        private void paint(boolean stroke, boolean fill) {
            List<Subpath> subpaths = new ArrayList<>(chainedSubpaths);
            if (openKind != null) {
                subpaths.add(new Subpath(openKind, flattenOpenSubpath()));
            }
            chainedSubpaths.clear();
            clearOpenSubpath();
            if (subpaths.isEmpty()) {
                return;
            }
            double appliedStrokeWidth = strokeWidthPoints * scaleX * pointToUnit;

            for (Subpath subpath : subpaths) {
                if (stroke && appliedStrokeWidth > 0) {
                    LineString line = toLineString(subpath.points());
                    if (line != null) {
                        // A white stroke still counts as copper in Python - only a white FILL is a hole.
                        addExploded(line.buffer(appliedStrokeWidth / 2, CIRCLE_STEPS), currentLayer.solids);
                    }
                }
                if (fill) {
                    Geometry polygon = toClosedPolygon(subpath);
                    if (polygon != null) {
                        if (fillIsWhite) {
                            addExploded(polygon, currentLayer.localClears);
                            if (!stroke && subpath.kind() == SubpathKind.BEZIER) {
                                // Python only feeds the global Excellon layer from a plain (unstroked) curve fill.
                                addExploded(polygon, globalClears);
                            }
                        } else {
                            addExploded(polygon, currentLayer.solids);
                        }
                    }
                }
            }
        }

        private static void addExploded(Geometry geometry, List<Geometry> target) {
            for (int index = 0; index < geometry.getNumGeometries(); index++) {
                Geometry part = geometry.getGeometryN(index);
                if (!part.isEmpty()) {
                    target.add(part);
                }
            }
        }

        /** A subpath closed if needed (fill always closes an otherwise-open ring) and healed if self-intersecting. */
        private Geometry toClosedPolygon(Subpath subpath) {
            List<Coordinate> points = subpath.points();
            if (points.size() < 3) {
                return null;
            }
            if (!points.get(0).equals2D(points.get(points.size() - 1))) {
                points = new ArrayList<>(points);
                points.add(points.get(0));
            }
            try {
                Geometry fixed = GeometryFixer.fix(FACTORY.createPolygon(points.toArray(new Coordinate[0])));
                return fixed.isEmpty() ? null : fixed;
            } catch (RuntimeException invalid) {
                return null;
            }
        }

        private LineString toLineString(List<Coordinate> points) {
            return points.size() < 2 ? null : FACTORY.createLineString(points.toArray(new Coordinate[0]));
        }

        // ---- layers -------------------------------------------------------------------------------------------------

        private void strokeColorChanged(double[] color) {
            if (strokeColor != null && strokeColor[0] == color[0] && strokeColor[1] == color[1]
                    && strokeColor[2] == color[2]) {
                return;
            }
            flushLayer();
            strokeColor = color;
        }

        private void flushLayer() {
            if (!currentLayer.isEmpty()) {
                objectLayers.put(layerNumber++, currentLayer);
                currentLayer = new Layer();
            }
        }

        private Result buildResult() {
            flushLayer();
            List<GerberImage> layers = new ArrayList<>();
            for (Layer layer : objectLayers.values()) {
                if (layer.solids.isEmpty()) {
                    continue;
                }
                Geometry solid = UnaryUnionOp.union(layer.solids);
                for (Geometry clear : layer.localClears) {
                    if (solid.covers(clear)) {
                        Geometry difference = solid.difference(clear);
                        if (!difference.isEmpty()) {
                            solid = difference;
                        }
                    }
                }
                if (!solid.isEmpty()) {
                    layers.add(GerberImage.of(units, Map.of(), solid, null, Map.of()));
                }
            }
            ExcellonImage drills = globalClears.isEmpty() ? null : buildDrills(globalClears);
            return new Result(units, drills, layers);
        }

        /** Each white-filled curve subpath becomes one hole: diameter from its bounding box, position from its centre. */
        private ExcellonImage buildDrills(List<Geometry> clears) {
            Map<Double, List<Coordinate>> byDiameter = new LinkedHashMap<>();
            for (Geometry clear : clears) {
                Envelope box = clear.getEnvelopeInternal();
                double diameter = round(box.getWidth() * DRILL_DIAMETER_CORRECTION, 3);
                if (diameter <= 0) {
                    continue;
                }
                byDiameter.computeIfAbsent(diameter, ignored -> new ArrayList<>())
                        .add(new Coordinate(box.getMinX() + box.getWidth() / 2, box.getMinY() + box.getHeight() / 2));
            }
            if (byDiameter.isEmpty()) {
                return null;
            }
            Map<Integer, Double> toolDiameters = new LinkedHashMap<>();
            List<ExcellonImage.Drill> drills = new ArrayList<>();
            List<Geometry> shapes = new ArrayList<>();
            int toolId = 0;
            for (double diameter : byDiameter.keySet().stream().sorted().toList()) {
                toolId++;
                toolDiameters.put(toolId, diameter);
                for (Coordinate point : byDiameter.get(diameter)) {
                    drills.add(new ExcellonImage.Drill(toolId, point.x, point.y));
                    shapes.add(FACTORY.createPoint(point).buffer(diameter / 2, 16));
                }
            }
            return ExcellonImage.of(units, toolDiameters, drills, List.of(), UnaryUnionOp.union(shapes));
        }

        private static double round(double value, int decimals) {
            double scale = Math.pow(10, decimals);
            return Math.round(value * scale) / scale;
        }
    }
}
