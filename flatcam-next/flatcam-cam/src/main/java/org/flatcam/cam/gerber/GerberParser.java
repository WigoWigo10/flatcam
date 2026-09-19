package org.flatcam.cam.gerber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * A from-scratch RS-274X Gerber parser covering exactly the subset exercised
 * by the Fase 0 baseline corpus (tests/gerber_files/, characterized in
 * ../../../tests/test_gerber_characterization.py against the legacy Python
 * parser): leading-zero-omitted absolute coordinates, linear draws, flashes,
 * region fills, standard C/R/O apertures, one aperture-macro primitive
 * (polygon, code 5), and dark/clear polarity.
 *
 * <p>Deliberately NOT a full Gerber implementation: circular interpolation
 * (G02/G03), trailing-zero/incremental coordinate formats, step-and-repeat
 * (%SR), polygon-template apertures (P), and most aperture-macro primitives
 * are unimplemented. Unsupported input raises {@link GerberParseException}
 * rather than silently producing wrong geometry - see GerberParserBaselineTest
 * for what this is validated against.
 */
public final class GerberParser {

    private static final Pattern MO_LINE = Pattern.compile("^%MO(IN|MM)\\*%?$");
    private static final Pattern AD_LINE =
            Pattern.compile("^%ADD(\\d+)([a-zA-Z_$.][a-zA-Z0-9_$.\\-]*)(?:,(.*))?\\*%?$");
    private static final Pattern AM_START = Pattern.compile("^%AM([a-zA-Z_$.][a-zA-Z0-9_$.]*)\\*$");
    private static final Pattern LP_LINE = Pattern.compile("^%LP([DC])\\*%?$");
    private static final Pattern TOOL_SELECT = Pattern.compile("^(?:G54)?D0*([0-9]+)\\*$");
    private static final Pattern DATA_LINE =
            Pattern.compile("^(?:G0?[123])?(?:X([+-]?\\d+))?(?:Y([+-]?\\d+))?(?:D0?([1-3]))?\\*$");

    private static final Set<String> IGNORABLE_EXACT = Set.of(
            "G90*", "G91*", "G70*", "G71*", "G01*", "G1*", "G74*", "G75*",
            "M00*", "M01*", "M02*", "M30*"
    );
    private static final List<String> IGNORABLE_PREFIXES =
            List.of("G04", "%OF", "%SF", "%LN", "%AS", "%IP");

    private static final int STROKE_QUADRANT_SEGMENTS = 16;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public GerberImage parse(Path file) throws IOException {
        return parse(Files.readAllLines(file));
    }

    public GerberImage parse(List<String> rawLines) {
        String units = null;
        FormatSpec format = null;
        Map<String, Aperture> apertures = new LinkedHashMap<>();
        Map<String, ApertureMacro> macros = new HashMap<>();
        ApertureMacro macroInProgress = null;

        String currentApertureId = null;
        double posX = 0;
        double posY = 0;
        char polarity = 'D';
        SolidAccumulator accumulator = new SolidAccumulator(geometryFactory);

        boolean regionMode = false;
        List<Coordinate> currentContour = null;
        List<List<Coordinate>> regionContours = null;

        for (String rawLine : rawLines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            if (macroInProgress != null) {
                if (line.equals("%")) {
                    macros.put(macroInProgress.name(), macroInProgress);
                    macroInProgress = null;
                } else {
                    macroInProgress.addPrimitive(splitPrimitive(line));
                }
                continue;
            }

            if (isIgnorable(line)) {
                continue;
            }

            Matcher m;
            if (line.startsWith("%FS")) {
                format = FormatSpec.parse(line);
                continue;
            }
            if ((m = MO_LINE.matcher(line)).matches()) {
                units = m.group(1);
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
                apertures.put(m.group(1), buildAperture(m.group(2), m.group(3), macros));
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
                accumulator.add(buildRegionGeometry(regionContours, geometryFactory), polarity);
                regionMode = false;
                currentContour = null;
                regionContours = null;
                continue;
            }

            if ((m = TOOL_SELECT.matcher(line)).matches()) {
                int value = Integer.parseInt(m.group(1));
                if (value >= 4) {
                    currentApertureId = String.valueOf(value);
                    continue;
                }
                // value 1-3: bare D01/D02/D03 op-code with no coordinates - falls through to DATA_LINE.
            }

            m = DATA_LINE.matcher(line);
            if (!m.matches()) {
                throw new GerberParseException("Unsupported Gerber line: " + rawLine);
            }
            requireFormat(format, line);
            double newX = m.group(1) != null ? format.decodeX(m.group(1)) : posX;
            double newY = m.group(2) != null ? format.decodeY(m.group(2)) : posY;
            int code = m.group(3) != null
                    ? Integer.parseInt(m.group(3))
                    : (m.group(1) != null || m.group(2) != null ? 1 : 2);

            switch (code) {
                case 1 -> {
                    if (regionMode) {
                        if (currentContour == null) {
                            currentContour = new ArrayList<>();
                            currentContour.add(new Coordinate(posX, posY));
                        }
                        currentContour.add(new Coordinate(newX, newY));
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
                        Geometry stroke = geometryFactory
                                .createLineString(new Coordinate[]{new Coordinate(posX, posY), new Coordinate(newX, newY)})
                                .buffer(radius, STROKE_QUADRANT_SEGMENTS);
                        accumulator.add(stroke, polarity);
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
                    accumulator.add(aperture.footprintAt(newX, newY, geometryFactory), polarity);
                }
                default -> throw new GerberParseException("Unreachable D-code " + code + " in: " + line);
            }
            posX = newX;
            posY = newY;
        }

        return new GerberImage(units == null ? "IN" : units, apertures, accumulator.result());
    }

    private Aperture buildAperture(String type, String paramsRaw, Map<String, ApertureMacro> macros) {
        String[] params = paramsRaw == null || paramsRaw.isEmpty() ? new String[0] : paramsRaw.split("X");
        return switch (type) {
            case "C" -> Aperture.circle(Double.parseDouble(params[0]));
            case "R" -> Aperture.rectangle(Double.parseDouble(params[0]), Double.parseDouble(params[1]));
            case "O" -> Aperture.obround(Double.parseDouble(params[0]), Double.parseDouble(params[1]));
            case "P" -> throw new GerberParseException("Polygon-template apertures (P) are not implemented");
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

    private static Geometry buildRegionGeometry(List<List<Coordinate>> contours, GeometryFactory geometryFactory) {
        List<Geometry> polygons = new ArrayList<>();
        for (List<Coordinate> contour : contours) {
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
        return UnaryUnionOp.union(polygons);
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
        private Geometry solid;
        private char pendingPolarity = 'D';

        SolidAccumulator(GeometryFactory geometryFactory) {
            this.solid = geometryFactory.createPolygon();
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
            flush();
            return solid;
        }

        private void flush() {
            if (pending.isEmpty()) {
                return;
            }
            Geometry batch = pending.size() == 1 ? pending.get(0) : UnaryUnionOp.union(pending);
            pending.clear();
            solid = pendingPolarity == 'C' ? solid.difference(batch) : solid.union(batch);
        }
    }
}
