package org.flatcam.cam.excellon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * An Excellon (NC drill) parser covering both coordinate styles seen across
 * major EDA tools:
 *
 * <ul>
 *   <li><b>Classic zero-suppressed, fixed-width</b> (older Altium, Eagle,
 *       DipTrace, KiCad &lt;6): {@code INCH,LZ}/{@code METRIC,TZ}/M71/M72,
 *       coordinates with no decimal point (e.g. "018204"). Default digit
 *       widths follow the legacy Python parser's own defaults - 2:4 for
 *       inch, 3:3 for metric - overridable via an Altium-style
 *       {@code ;FILE_FORMAT=U:L} or {@code ;Format=U:L} comment.</li>
 *   <li><b>Modern decimal</b> (KiCad 6+, and generally recommended going
 *       forward): bare {@code INCH}/{@code METRIC}, coordinates always carry
 *       an explicit decimal point (e.g. "X3.4Y-30.0") - no digit-width
 *       config needed at all.</li>
 * </ul>
 *
 * <p>Per-token decoding checks for a literal '.' and switches behavior
 * accordingly, so both styles are handled by the same code path without
 * needing to detect the file's style up front. This also matches the
 * legacy Python parser's actual number-decoding behavior on every
 * well-formed (fixed-width) file in the Fase 0 corpus - see
 * ExcellonParserBaselineTest for why the leading/trailing zero distinction
 * itself doesn't need to be tracked (both reduce to the same formula once
 * a tool always emits full-width digit strings, which they do in practice).
 *
 * <p>Scope: drilling and G85 slots only - no routing (G00/G01/G02/G03 with
 * actual moves) and no incremental positioning (G91), matching the legacy
 * parser's own documented limits (flatcam.org/fileformats: "FlatCAM
 * supports only the drilling subset of Excellon. Routing is not
 * supported."). X2-style attribute comments ("; #@! TA...."/"; #@! TF...",
 * KiCad 6+) are ignored like any other comment.
 */
public final class ExcellonParser {

    private static final int DEFAULT_LOWER_IN = 4;
    private static final int DEFAULT_LOWER_MM = 3;
    private static final int CIRCLE_QUADRANT_SEGMENTS = 16;

    private static final Pattern FORMAT_OVERRIDE =
            Pattern.compile("^;\\s*(?:FILE_FORMAT|Format)\\s*[=:]\\s*(\\d+)[:.](\\d+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_DEFINITION = Pattern.compile("^T0*([0-9]+)C([0-9.]+).*$");
    private static final Pattern TOOL_SELECT = Pattern.compile("^T0*([0-9]+)$");
    private static final Pattern SLOT_LINE =
            Pattern.compile("^(?:X([+-]?[0-9.]+))?(?:Y([+-]?[0-9.]+))?G85(?:X([+-]?[0-9.]+))?(?:Y([+-]?[0-9.]+))?$");
    private static final Pattern COORD_LINE = Pattern.compile("^(?:X([+-]?[0-9.]+))?(?:Y([+-]?[0-9.]+))?$");

    private static final java.util.Set<String> IGNORABLE_EXACT = java.util.Set.of(
            "M48", "FMAT,1", "FMAT,2", "G90", "G05", "M00", "M30", "%", "M95"
    );

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public ExcellonImage parse(Path file) throws IOException {
        return parse(Files.readAllLines(file));
    }

    public ExcellonImage parse(List<String> rawLines) {
        String units = null;
        Integer formatLowerOverride = null;
        Map<Integer, Double> toolDiameters = new LinkedHashMap<>();
        List<Geometry> shapes = new ArrayList<>();
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<ExcellonImage.Slot> slots = new ArrayList<>();

        Integer currentTool = null;
        double posX = 0;
        double posY = 0;

        for (String rawLine : rawLines) {
            String line = rawLine.strip();
            if (line.isEmpty() || IGNORABLE_EXACT.contains(line)) {
                continue;
            }
            if (line.equals("M71")) {
                units = "MM";
                continue;
            }
            if (line.equals("M72")) {
                units = "IN";
                continue;
            }
            if (line.equals("G91")) {
                throw new ExcellonParseException("Incremental positioning (G91) is not supported");
            }
            if (line.startsWith(";")) {
                Matcher format = FORMAT_OVERRIDE.matcher(line);
                if (format.matches()) {
                    formatLowerOverride = Integer.parseInt(format.group(2));
                }
                continue; // any other comment, including KiCad's "; #@!" X2 attributes.
            }
            if (line.equals("METRIC") || line.startsWith("METRIC,")) {
                units = "MM";
                continue;
            }
            if (line.equals("INCH") || line.startsWith("INCH,")) {
                units = "IN";
                continue;
            }

            Matcher toolDefinition = TOOL_DEFINITION.matcher(line);
            if (toolDefinition.matches()) {
                int id = Integer.parseInt(toolDefinition.group(1));
                toolDiameters.put(id, Double.parseDouble(toolDefinition.group(2)));
                continue;
            }
            Matcher toolSelect = TOOL_SELECT.matcher(line);
            if (toolSelect.matches()) {
                currentTool = Integer.parseInt(toolSelect.group(1));
                continue;
            }

            Matcher slot = SLOT_LINE.matcher(line);
            if (slot.matches()) {
                requireUnits(units, line);
                int lower = resolveLowerDigits(units, formatLowerOverride);
                double x1 = slot.group(1) != null ? decode(slot.group(1), lower) : posX;
                double y1 = slot.group(2) != null ? decode(slot.group(2), lower) : posY;
                double x2 = slot.group(3) != null ? decode(slot.group(3), lower) : x1;
                double y2 = slot.group(4) != null ? decode(slot.group(4), lower) : y1;
                double diameter = requireToolDiameter(toolDiameters, currentTool, line);

                shapes.add(geometryFactory
                        .createLineString(new Coordinate[]{new Coordinate(x1, y1), new Coordinate(x2, y2)})
                        .buffer(diameter / 2.0, CIRCLE_QUADRANT_SEGMENTS));
                slots.add(new ExcellonImage.Slot(currentTool, x1, y1, x2, y2));
                posX = x2;
                posY = y2;
                continue;
            }

            Matcher coord = COORD_LINE.matcher(line);
            if (coord.matches() && (coord.group(1) != null || coord.group(2) != null)) {
                requireUnits(units, line);
                int lower = resolveLowerDigits(units, formatLowerOverride);
                double x = coord.group(1) != null ? decode(coord.group(1), lower) : posX;
                double y = coord.group(2) != null ? decode(coord.group(2), lower) : posY;
                double diameter = requireToolDiameter(toolDiameters, currentTool, line);

                shapes.add(geometryFactory.createPoint(new Coordinate(x, y)).buffer(diameter / 2.0, CIRCLE_QUADRANT_SEGMENTS));
                drills.add(new ExcellonImage.Drill(currentTool, x, y));
                posX = x;
                posY = y;
                continue;
            }

            throw new ExcellonParseException("Unsupported Excellon line: " + rawLine);
        }

        Geometry solid = shapes.isEmpty() ? geometryFactory.createPolygon() : UnaryUnionOp.union(shapes);
        return new ExcellonImage(units == null ? "IN" : units, toolDiameters, drills, slots, solid);
    }

    /**
     * Decodes one coordinate token. A literal '.' means the modern decimal
     * style - parse directly. Otherwise, a fixed-width digit string: divide
     * by 10^lowerDigits, which is correct for both leading- and
     * trailing-zero suppression as long as the writer always emits
     * full-width digit strings (true of every real-world tool checked) -
     * see this class's doc comment.
     */
    private static double decode(String token, int lowerDigits) {
        if (token.indexOf('.') >= 0) {
            return Double.parseDouble(token);
        }
        boolean negative = token.startsWith("-");
        String digits = (negative || token.startsWith("+")) ? token.substring(1) : token;
        if (digits.isEmpty()) {
            return 0.0;
        }
        double value = Long.parseLong(digits) / Math.pow(10, lowerDigits);
        return negative ? -value : value;
    }

    private static int resolveLowerDigits(String units, Integer override) {
        if (override != null) {
            return override;
        }
        return "MM".equals(units) ? DEFAULT_LOWER_MM : DEFAULT_LOWER_IN;
    }

    private static void requireUnits(String units, String line) {
        if (units == null) {
            throw new ExcellonParseException("Coordinate data before units (INCH/METRIC/M71/M72) were declared: " + line);
        }
    }

    private static double requireToolDiameter(Map<Integer, Double> toolDiameters, Integer currentTool, String line) {
        if (currentTool == null) {
            throw new ExcellonParseException("No tool selected before: " + line);
        }
        Double diameter = toolDiameters.get(currentTool);
        if (diameter == null) {
            throw new ExcellonParseException("Tool T" + currentTool + " was never defined (used in: " + line + ")");
        }
        return diameter;
    }
}
