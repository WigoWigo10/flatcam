package org.flatcam.cam.gerber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * A from-scratch RS-274X Gerber parser covering exactly the subset exercised
 * by the Fase 0 baseline corpus (tests/gerber_files/, characterized in
 * ../../../tests/test_gerber_characterization.py against the legacy Python
 * parser): leading-zero-omitted absolute coordinates, linear draws, flashes,
 * region fills, standard C/R/O apertures, one aperture-macro primitive
 * (polygon, code 5), dark/clear polarity, and circular interpolation (G02/
 * G03 with I/J offsets, both G74 single-quadrant and G75 multi-quadrant
 * modes) - ported from appParsers/ParseGerber.py's circ_re handling plus
 * camlib.py's arc()/arc_angle() helpers, needed to open board-outline
 * (Edge_Cuts) Gerbers, whose rounded corners are drawn as arcs.
 *
 * <p>Deliberately NOT yet a full Gerber implementation: step-and-repeat (%SR)
 * and some aperture-macro primitives are unimplemented. Unsupported input
 * raises {@link GerberParseException} rather than silently producing wrong
 * geometry - see GerberParserBaselineTest for what this is validated against.
 */
public final class GerberParser {

    private static final Pattern MO_LINE = Pattern.compile("^%MO(IN|MM)\\*%?$");
    private static final Pattern AD_LINE =
            Pattern.compile("^%ADD(\\d+)([a-zA-Z_$.][a-zA-Z0-9_$.\\-]*)(?:,(.*))?\\*%?$");
    private static final Pattern AM_START = Pattern.compile("^%AM([a-zA-Z_$.][a-zA-Z0-9_$.]*)\\*$");
    private static final Pattern LP_LINE = Pattern.compile("^%LP([DC])\\*%?$");
    private static final Pattern TOOL_SELECT = Pattern.compile("^(?:G54)?D0*([0-9]+)\\*$");
    /** Groups: 1=modal G-code (1/2/3), 2=X, 3=Y, 4=I (arc center offset), 5=J, 6=D-code. */
    private static final Pattern DATA_LINE = Pattern.compile(
            "^(?:G0?([123]))?(?:X([+-]?\\d+))?(?:Y([+-]?\\d+))?(?:I([+-]?\\d+))?(?:J([+-]?\\d+))?(?:D0?([1-3]))?\\*$");

    private static final int GERBER_CIRCLE_STEPS = 64; // matches legacy defaults["gerber_circle_steps"]

    private static final Set<String> IGNORABLE_EXACT = Set.of(
            "M00*", "M01*", "M02*", "M30*"
    );
    private static final List<String> IGNORABLE_PREFIXES = List.of(
            "G04", "%OF", "%SF", "%LN", "%AS", "%IP",
            // Gerber X2 attributes (%TF/%TA/%TO file/aperture/object attributes, %TD deletes one) -
            // metadata for CAM tooling (net names, component refs, ...), no effect on geometry.
            "%TF", "%TA", "%TD", "%TO"
    );

    private static final int STROKE_QUADRANT_SEGMENTS = 16;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public GerberImage parse(Path file) throws IOException {
        return parse(file, CancellationToken.none());
    }

    public GerberImage parse(Path file, CancellationToken cancellationToken) throws IOException {
        return parse(file, cancellationToken, ProgressCallback.none());
    }

    public GerberImage parse(Path file, CancellationToken cancellationToken, ProgressCallback progressCallback)
            throws IOException {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressCallback, "progressCallback");
        cancellationToken.throwIfCancellationRequested();
        progressCallback.report(0);
        List<String> lines = Files.readAllLines(file);
        cancellationToken.throwIfCancellationRequested();
        return parse(lines, cancellationToken, progressCallback);
    }

    public GerberImage parse(List<String> rawLines) {
        return parse(rawLines, CancellationToken.none());
    }

    public GerberImage parse(List<String> rawLines, CancellationToken cancellationToken) {
        return parse(rawLines, cancellationToken, ProgressCallback.none());
    }

    public GerberImage parse(List<String> rawLines, CancellationToken cancellationToken,
                             ProgressCallback progressCallback) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressCallback, "progressCallback");
        cancellationToken.throwIfCancellationRequested();
        progressCallback.report(0);
        List<String> lines = splitStatements(rawLines);
        String units = null;
        FormatSpec format = null;
        boolean incrementalMode = false;
        Map<String, Aperture> apertures = new LinkedHashMap<>();
        Map<String, ApertureMacro> macros = new HashMap<>();
        ApertureMacro macroInProgress = null;

        String currentApertureId = null;
        double posX = 0;
        double posY = 0;
        char polarity = 'D';
        int currentOperationCode = 2;
        int interpolationMode = 1; // 1=linear (G01), 2=clockwise arc (G02), 3=counter-clockwise arc (G03) - modal
        String quadrantMode = null; // "SINGLE" (G74) or "MULTI" (G75) - required before any arc
        SolidAccumulator accumulator = new SolidAccumulator(geometryFactory, cancellationToken);
        // Every flash/stroke's shape, kept per aperture regardless of polarity - purely for the
        // apertures table's "Mark" highlight (appParsers/ParseGerber.py's apertures[code]['geometry']),
        // with zero effect on the final solidGeometry above.
        Map<String, List<Geometry>> shapesByAperture = new LinkedHashMap<>();
        // ParseGerber.py's follow_buffer: unbuffered draw paths, region
        // boundaries and flash centers. Kept separately from copper solids so
        // the UI and later Geometry-object workflow can follow trace centers.
        List<Geometry> followShapes = new ArrayList<>();
        List<GerberShape> drawnShapes = new ArrayList<>();

        boolean regionMode = false;
        List<Coordinate> currentContour = null;
        List<List<Coordinate>> regionContours = null;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            cancellationToken.throwIfCancellationRequested();
            reportProgressStep(progressCallback, lineIndex, lines.size(), 0, 0.90);
            String rawLine = lines.get(lineIndex);
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            if (macroInProgress != null) {
                if (line.equals("%")) {
                    macros.put(macroInProgress.name(), macroInProgress);
                    macroInProgress = null;
                } else if (line.endsWith("*%")) {
                    // The block-closing '%' can be glued onto the last primitive line
                    // instead of standing on its own (seen in KiCad output).
                    String primitiveLine = line.substring(0, line.length() - 1);
                    if (!isMacroComment(primitiveLine)) {
                        macroInProgress.addPrimitive(splitPrimitive(primitiveLine));
                    }
                    macros.put(macroInProgress.name(), macroInProgress);
                    macroInProgress = null;
                } else if (!isMacroComment(line)) {
                    macroInProgress.addPrimitive(splitPrimitive(line));
                }
                continue;
            }

            if (isIgnorable(line)) {
                continue;
            }
            if (line.equals("G90*") || line.equals("G91*")) {
                incrementalMode = line.equals("G91*");
                continue;
            }
            if (line.equals("G70*") || line.equals("G71*")) {
                String impliedUnits = line.equals("G70*") ? "IN" : "MM";
                units = reconcileUnits(units, impliedUnits, rawLine.strip());
                continue;
            }

            Matcher m;
            if (line.startsWith("%FS")) {
                format = FormatSpec.parse(line);
                incrementalMode = format.isIncremental();
                continue;
            }
            if ((m = MO_LINE.matcher(line)).matches()) {
                units = reconcileUnits(units, m.group(1), rawLine.strip());
                continue;
            }
            if ((m = AM_START.matcher(line)).matches()) {
                macroInProgress = new ApertureMacro(m.group(1));
                continue;
            }
            if ((m = LP_LINE.matcher(line)).matches()) {
                polarity = m.group(1).charAt(0);
                continue;
            }
            if ((m = AD_LINE.matcher(line)).matches()) {
                requireFormat(format, line);
                apertures.put(String.valueOf(Integer.parseInt(m.group(1))),
                        buildAperture(m.group(2), m.group(3), macros));
                continue;
            }
            if (line.equals("G36*")) {
                regionMode = true;
                regionContours = new ArrayList<>();
                currentContour = null;
                continue;
            }
            if (line.equals("G37*")) {
                if (currentContour != null && currentContour.size() > 2) {
                    regionContours.add(currentContour);
                }
                Geometry region = buildRegionGeometry(regionContours, geometryFactory, cancellationToken);
                accumulator.add(region, polarity);
                if (!region.isEmpty()) {
                    drawnShapes.add(new GerberShape(GerberShape.REGION_APERTURE, region, polarity == 'C'));
                }
                addRegionBoundaries(region, followShapes);
                regionMode = false;
                currentContour = null;
                regionContours = null;
                continue;
            }
            if (line.equals("G74*")) {
                quadrantMode = "SINGLE";
                continue;
            }
            if (line.equals("G75*")) {
                quadrantMode = "MULTI";
                continue;
            }

            if ((m = TOOL_SELECT.matcher(line)).matches()) {
                int value = Integer.parseInt(m.group(1));
                if (value >= 4) {
                    currentApertureId = String.valueOf(value);
                    continue;
                }
                currentOperationCode = value;
                // D01/D02 alone only change the modal operation. D03 also flashes at
                // the current point, matching ParseGerber.py's opcode_re branch.
                if (value != 3) {
                    continue;
                }
            }

            m = DATA_LINE.matcher(line);
            if (!m.matches()) {
                throw new GerberParseException("Unsupported Gerber line: " + rawLine);
            }
            if (m.group(1) != null && m.group(2) == null && m.group(3) == null
                    && m.group(4) == null && m.group(5) == null && m.group(6) == null) {
                // A bare "G01*"/"G02*"/"G03*" (no coordinates, no D-code) only changes the
                // modal interpolation mode - matches appParsers/ParseGerber.py's interp_re.
                interpolationMode = Integer.parseInt(m.group(1));
                continue;
            }
            requireFormat(format, line);
            if (m.group(1) != null) {
                interpolationMode = Integer.parseInt(m.group(1));
            }
            double decodedX = m.group(2) != null ? format.decodeX(m.group(2)) : 0;
            double decodedY = m.group(3) != null ? format.decodeY(m.group(3)) : 0;
            double newX = m.group(2) != null ? (incrementalMode ? posX + decodedX : decodedX) : posX;
            double newY = m.group(3) != null ? (incrementalMode ? posY + decodedY : decodedY) : posY;
            double offsetI = m.group(4) != null ? format.decodeX(m.group(4)) : 0;
            double offsetJ = m.group(5) != null ? format.decodeY(m.group(5)) : 0;
            if (m.group(6) != null) {
                currentOperationCode = Integer.parseInt(m.group(6));
            }
            int code = currentOperationCode;

            switch (code) {
                case 1 -> {
                    List<Coordinate> segment = interpolationMode == 1
                            ? List.of(new Coordinate(posX, posY), new Coordinate(newX, newY))
                            : arcPoints(posX, posY, newX, newY, offsetI, offsetJ, interpolationMode, quadrantMode, rawLine);
                    if (regionMode) {
                        if (currentContour == null) {
                            currentContour = new ArrayList<>();
                            currentContour.add(segment.get(0));
                        }
                        currentContour.addAll(segment.subList(1, segment.size()));
                    } else {
                        Aperture aperture = requireAperture(apertures, currentApertureId, line);
                        double radius;
                        try {
                            radius = aperture.strokeRadius();
                        } catch (GerberParseException e) {
                            throw new GerberParseException(
                                    "Stroke with aperture D" + currentApertureId + " (" + aperture.kind
                                            + ") on line: " + rawLine, e);
                        }
                        LineString centerline = geometryFactory
                                .createLineString(segment.toArray(new Coordinate[0]));
                        Geometry stroke = centerline.buffer(radius, STROKE_QUADRANT_SEGMENTS);
                        accumulator.add(stroke, polarity);
                        shapesByAperture.computeIfAbsent(currentApertureId, k -> new ArrayList<>()).add(stroke);
                        drawnShapes.add(new GerberShape(currentApertureId, stroke, polarity == 'C'));
                        followShapes.add(centerline);
                    }
                }
                case 2 -> {
                    if (regionMode) {
                        if (currentContour != null && currentContour.size() > 2) {
                            regionContours.add(currentContour);
                        }
                        currentContour = new ArrayList<>();
                        currentContour.add(new Coordinate(newX, newY));
                    }
                    // Outside region mode a move is just pen-up: no geometry.
                }
                case 3 -> {
                    Aperture aperture = requireAperture(apertures, currentApertureId, line);
                    Geometry footprint = aperture.footprintAt(newX, newY, geometryFactory);
                    accumulator.add(footprint, polarity);
                    shapesByAperture.computeIfAbsent(currentApertureId, k -> new ArrayList<>()).add(footprint);
                    drawnShapes.add(new GerberShape(currentApertureId, footprint, polarity == 'C'));
                    followShapes.add(geometryFactory.createPoint(new Coordinate(newX, newY)));
                }
                default -> throw new GerberParseException("Unreachable D-code " + code + " in: " + line);
            }
            posX = newX;
            posY = newY;
        }

        progressCallback.report(0.90);
        Map<String, Geometry> apertureGeometry = new LinkedHashMap<>();
        int apertureIndex = 0;
        for (Map.Entry<String, List<Geometry>> entry : shapesByAperture.entrySet()) {
            cancellationToken.throwIfCancellationRequested();
            List<Geometry> shapes = entry.getValue();
            apertureGeometry.put(entry.getKey(), shapes.size() == 1 ? shapes.get(0) : UnaryUnionOp.union(shapes));
            cancellationToken.throwIfCancellationRequested();
            apertureIndex++;
            reportProgressStep(progressCallback, apertureIndex, shapesByAperture.size(), 0.90, 0.98);
        }

        progressCallback.report(0.98);
        Geometry solidGeometry = accumulator.result();
        Geometry followGeometry = geometryFactory.createGeometryCollection(followShapes.toArray(Geometry[]::new));
        cancellationToken.throwIfCancellationRequested();
        progressCallback.report(1);
        return new GerberImage(units == null ? "IN" : units, apertures, solidGeometry,
                followGeometry, apertureGeometry, drawnShapes);
    }

    /** Adds one directly drawable line per region ring, including holes and multipart regions. */
    private static void addRegionBoundaries(Geometry geometry, List<Geometry> target) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            target.add(polygon.getExteriorRing());
            for (int ring = 0; ring < polygon.getNumInteriorRing(); ring++) {
                target.add(polygon.getInteriorRingN(ring));
            }
            return;
        }
        for (int part = 0; part < geometry.getNumGeometries(); part++) {
            addRegionBoundaries(geometry.getGeometryN(part), target);
        }
    }

    private static void reportProgressStep(ProgressCallback callback, int completed, int total,
                                           double start, double end) {
        double fraction = total == 0 ? end : start + (end - start) * completed / total;
        double previous = total == 0 || completed == 0
                ? -1 : start + (end - start) * (completed - 1) / total;
        if (completed == 0 || Math.round(fraction * 100) != Math.round(previous * 100)) {
            callback.report(fraction);
        }
    }

    /**
     * Turns physical file lines into Gerber statements. Real generators often
     * concatenate commands ({@code G54D11*G36*}) or put an entire aperture
     * macro / multiple extended commands inside one {@code %...%} block.
     */
    private static List<String> splitStatements(List<String> rawLines) {
        String source = String.join("\n", rawLines);
        List<String> statements = new ArrayList<>();
        int cursor = 0;
        while (cursor < source.length()) {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= source.length()) {
                break;
            }

            if (source.charAt(cursor) == '%') {
                int close = source.indexOf('%', cursor + 1);
                if (close < 0) {
                    throw new GerberParseException("Unterminated extended Gerber block");
                }
                expandExtendedBlock(source.substring(cursor + 1, close), statements);
                cursor = close + 1;
                continue;
            }

            int star = source.indexOf('*', cursor);
            int newline = source.indexOf('\n', cursor);
            boolean endsAtStar = star >= 0 && (newline < 0 || star < newline);
            int end = endsAtStar ? star + 1 : newline >= 0 ? newline : source.length();
            String statement = source.substring(cursor, end).strip();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
            cursor = end;
        }
        return statements;
    }

    private static void expandExtendedBlock(String blockBody, List<String> statements) {
        String body = blockBody.strip();
        if (body.startsWith("AM")) {
            int headerEnd = body.indexOf('*');
            if (headerEnd < 0) {
                throw new GerberParseException("Malformed aperture macro block: %" + body + "%");
            }
            statements.add("%" + body.substring(0, headerEnd + 1));
            String primitives = body.substring(headerEnd + 1);
            for (String primitive : primitives.split("\\*")) {
                String trimmed = primitive.strip();
                if (!trimmed.isEmpty()) {
                    statements.add(trimmed + "*");
                }
            }
            statements.add("%");
            return;
        }

        for (String command : body.split("\\*")) {
            String trimmed = command.strip();
            if (!trimmed.isEmpty()) {
                statements.add("%" + trimmed + "*%");
            }
        }
    }

    /**
     * Accepts repeated, equivalent unit declarations while rejecting a conflict
     * regardless of whether the legacy G70/G71 code or %MO appeared first.
     */
    private static String reconcileUnits(String currentUnits, String declaredUnits, String declaration) {
        if (currentUnits != null && !currentUnits.equals(declaredUnits)) {
            throw new GerberParseException(
                    "Unit mismatch: earlier declaration selected " + currentUnits
                            + " but " + declaration + " declares " + declaredUnits);
        }
        return declaredUnits;
    }

    private Aperture buildAperture(String type, String paramsRaw, Map<String, ApertureMacro> macros) {
        String[] params = paramsRaw == null || paramsRaw.isEmpty() ? new String[0] : paramsRaw.split("X");
        return switch (type) {
            case "C" -> Aperture.circle(Double.parseDouble(params[0]));
            case "R" -> Aperture.rectangle(Double.parseDouble(params[0]), Double.parseDouble(params[1]));
            case "O" -> Aperture.obround(Double.parseDouble(params[0]), Double.parseDouble(params[1]));
            case "P" -> Aperture.polygon(Double.parseDouble(params[0]), Integer.parseInt(params[1]),
                    params.length >= 3 ? Double.parseDouble(params[2]) : 0);
            default -> {
                ApertureMacro macro = macros.get(type);
                if (macro == null) {
                    throw new GerberParseException("Unknown aperture type or undefined macro: " + type);
                }
                double[] modifiers = new double[params.length];
                for (int i = 0; i < params.length; i++) {
                    modifiers[i] = Double.parseDouble(params[i]);
                }
                yield Aperture.macro(macro, modifiers);
            }
        };
    }

    private static String[] splitPrimitive(String line) {
        String body = line.endsWith("*") ? line.substring(0, line.length() - 1) : line;
        return body.split(",");
    }

    /** Primitive code 0 is a free-text comment, e.g. "0 Rounding radius*" - not comma-separated fields at all. */
    private static boolean isMacroComment(String line) {
        String body = line.endsWith("*") ? line.substring(0, line.length() - 1) : line;
        return body.equals("0") || body.startsWith("0 ") || body.startsWith("0\t");
    }

    private static Geometry buildRegionGeometry(List<List<Coordinate>> contours, GeometryFactory geometryFactory,
                                                CancellationToken cancellationToken) {
        List<Geometry> polygons = new ArrayList<>();
        for (List<Coordinate> contour : contours) {
            cancellationToken.throwIfCancellationRequested();
            if (contour.size() < 3) {
                continue;
            }
            List<Coordinate> ring = new ArrayList<>(contour);
            if (!ring.get(0).equals2D(ring.get(ring.size() - 1))) {
                ring.add(ring.get(0));
            }
            polygons.add(geometryFactory.createPolygon(ring.toArray(new Coordinate[0])));
        }
        if (polygons.isEmpty()) {
            return geometryFactory.createPolygon();
        }
        Geometry result = UnaryUnionOp.union(polygons);
        cancellationToken.throwIfCancellationRequested();
        return result;
    }

    private static boolean isIgnorable(String line) {
        if (IGNORABLE_EXACT.contains(line)) {
            return true;
        }
        for (String prefix : IGNORABLE_PREFIXES) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The points of a G02 (clockwise)/G03 (counter-clockwise) arc from
     * (startX,startY) to (endX,endY) - ported from appParsers/ParseGerber.py's
     * G74 (single-quadrant)/G75 (multi-quadrant) circ_re handling plus
     * camlib.py's arc()/arc_angle(). In MULTI mode the center is simply
     * start+offset; in SINGLE mode the true center is whichever of the four
     * sign combinations of (offsetI, offsetJ) is equidistant from both
     * endpoints and subtends at most a quarter turn (the Gerber spec's
     * single-quadrant constraint).
     */
    private static List<Coordinate> arcPoints(double startX, double startY, double endX, double endY,
            double offsetI, double offsetJ, int interpolationMode, String quadrantMode, String rawLine) {
        if (quadrantMode == null) {
            throw new GerberParseException("Arc (G0" + interpolationMode + ") without a preceding G74/G75 quadrant mode: " + rawLine);
        }
        boolean clockwise = interpolationMode == 2;
        double radius = Math.hypot(offsetI, offsetJ);

        if ("MULTI".equals(quadrantMode)) {
            double centerX = startX + offsetI;
            double centerY = startY + offsetJ;
            double start = Math.atan2(-offsetJ, -offsetI);
            double stop = (startX == endX && startY == endY) ? start : Math.atan2(endY - centerY, endX - centerX);
            return pointsAlongArc(centerX, centerY, radius, start, stop, clockwise, endX, endY);
        }

        double[][] centerCandidates = {
                {startX + offsetI, startY + offsetJ},
                {startX - offsetI, startY + offsetJ},
                {startX + offsetI, startY - offsetJ},
                {startX - offsetI, startY - offsetJ},
        };
        for (double[] center : centerCandidates) {
            double radiusToEnd = Math.hypot(center[0] - endX, center[1] - endY);
            if (radiusToEnd < radius * 0.95 || radiusToEnd > radius * 1.05) {
                continue; // Not a valid center - end point isn't (approximately) on this circle.
            }
            double i = center[0] - startX;
            double j = center[1] - startY;
            double start = Math.atan2(-j, -i);
            double stop = Math.atan2(endY - center[1], endX - center[0]);
            double angle = arcAngle(start, stop, clockwise);
            if (angle <= (Math.PI + 1e-6) / 2) {
                return pointsAlongArc(center[0], center[1], radius, start, stop, clockwise, endX, endY);
            }
        }
        throw new GerberParseException("Invalid single-quadrant arc (no matching center found): " + rawLine);
    }

    /** Samples an arc at {@link #GERBER_CIRCLE_STEPS} segments per full circle, snapping the last point to the exact end coordinate. */
    private static List<Coordinate> pointsAlongArc(double centerX, double centerY, double radius,
            double start, double stop, boolean clockwise, double exactEndX, double exactEndY) {
        if (!clockwise && stop <= start) {
            stop += 2 * Math.PI;
        }
        if (clockwise && stop >= start) {
            stop -= 2 * Math.PI;
        }
        double angle = Math.abs(stop - start);
        int steps = Math.max((int) Math.ceil(angle / (2 * Math.PI) * GERBER_CIRCLE_STEPS), 2);
        double deltaAngle = (clockwise ? -1.0 : 1.0) * angle / steps;

        List<Coordinate> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double theta = start + deltaAngle * i;
            points.add(new Coordinate(centerX + radius * Math.cos(theta), centerY + radius * Math.sin(theta)));
        }
        points.set(points.size() - 1, new Coordinate(exactEndX, exactEndY));
        return points;
    }

    private static double arcAngle(double start, double stop, boolean clockwise) {
        if (!clockwise && stop <= start) {
            stop += 2 * Math.PI;
        }
        if (clockwise && stop >= start) {
            stop -= 2 * Math.PI;
        }
        return Math.abs(stop - start);
    }

    private static void requireFormat(FormatSpec format, String line) {
        if (format == null) {
            throw new GerberParseException("Coordinate/aperture data before %FS was declared: " + line);
        }
    }

    private static Aperture requireAperture(Map<String, Aperture> apertures, String currentApertureId, String line) {
        if (currentApertureId == null) {
            throw new GerberParseException("No aperture selected before: " + line);
        }
        Aperture aperture = apertures.get(currentApertureId);
        if (aperture == null) {
            throw new GerberParseException("Aperture D" + currentApertureId + " was never defined (used in: " + line + ")");
        }
        return aperture;
    }

    /**
     * Batches shapes of the same polarity and unions/subtracts them from the
     * running solid in one shot (JTS's {@link UnaryUnionOp} instead of many
     * sequential pairwise unions) - matters for fixtures with thousands of
     * flashes (STM32F4-spindle.cmp has 1661 on a single aperture alone).
     */
    private static final class SolidAccumulator {
        private final List<Geometry> pending = new ArrayList<>();
        private final CancellationToken cancellationToken;
        private Geometry solid;
        private char pendingPolarity = 'D';

        SolidAccumulator(GeometryFactory geometryFactory, CancellationToken cancellationToken) {
            this.solid = geometryFactory.createPolygon();
            this.cancellationToken = cancellationToken;
        }

        void add(Geometry shape, char polarity) {
            if (shape == null || shape.isEmpty()) {
                return;
            }
            if (polarity != pendingPolarity) {
                flush();
                pendingPolarity = polarity;
            }
            pending.add(shape);
        }

        Geometry result() {
            cancellationToken.throwIfCancellationRequested();
            flush();
            return solid;
        }

        private void flush() {
            cancellationToken.throwIfCancellationRequested();
            if (pending.isEmpty()) {
                return;
            }
            Geometry batch = pending.size() == 1 ? pending.get(0) : UnaryUnionOp.union(pending);
            cancellationToken.throwIfCancellationRequested();
            pending.clear();
            solid = pendingPolarity == 'C' ? solid.difference(batch) : solid.union(batch);
            cancellationToken.throwIfCancellationRequested();
        }
    }
}
