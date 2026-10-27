package org.flatcam.cam.excellon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

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
 * <p>Per-token decimals are read literally. Implicit decimals use the declared
 * LZ/TZ convention and integer/fractional widths; preferences only fill absent
 * declarations. Full-width files keep their previous decoding. Short LZ tokens
 * now correctly retain leading zeros and pad the missing trailing digits.
 *
 * <p>Scope: drilling, G85 slots and straight routed slots (G00 to the start,
 * M15 plunge, G01 to the end, M16/M17 retract, G05 back to drilling - the
 * form FlatCAM Python's own Excellon export writes by default). Each routed
 * move with the tool down becomes one slot. Circular G02/G03 routs retain
 * sampled centrelines (A radius or CNC-7 I/J offsets, <=180 degrees).
 * G90/G91 switch absolute/incremental XY; scalar radius/centre offsets do not accumulate. X2-style attribute comments ("; #@! TA...."/"; #@! TF...",
 * KiCad 6+) are ignored like any other comment.
 */
public final class ExcellonParser {

    private static final int DEFAULT_LOWER_IN = 4;
    private static final int DEFAULT_LOWER_MM = 3;
    private static final int CIRCLE_QUADRANT_SEGMENTS = 16;

    private static final Pattern FORMAT_OVERRIDE =
            Pattern.compile("^;\\s*(?:FILE_FORMAT|Format)\\s*[=:]\\s*(\\d+)[:.](\\d+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_DEFINITION = Pattern.compile("^T0*([0-9]+)(?:[FSBH][0-9.]+)*C([0-9.]+).*$");
    private static final Pattern TOOL_SELECT = Pattern.compile("^T0*([0-9]+)$");
    private static final Pattern SLOT_LINE =
            Pattern.compile("^(?:X([+-]?[0-9.]+))?(?:Y([+-]?[0-9.]+))?G85(?:X([+-]?[0-9.]+))?(?:Y([+-]?[0-9.]+))?$");
    private static final Pattern ROUTE_LINE =
            Pattern.compile("^(?:G0?([0-3]))?((?:[XYAIJ][+-]?[0-9.]+)*)$");
    private static final Pattern ROUTE_WORD = Pattern.compile("([XYAIJ])([+-]?[0-9.]+)");
    private static final Pattern COORD_LINE = Pattern.compile("^(?:X([+-]?[0-9.]+))?(?:Y([+-]?[0-9.]+))?$");

    private static final java.util.Set<String> IGNORABLE_EXACT = java.util.Set.of(
            "M48", "FMAT,1", "FMAT,2", "M00", "%", "M95"
    );

    private final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * What to assume when the file does not say (legacy defaults "excellon_units" and "excellon_format_lower_*").
     *
     * @param defaultUnits "IN" or "MM" for a file that never declares its units; null refuses such a file
     * @param lowerDigitsInch decimal digits of an inch coordinate written without a decimal point
     * @param lowerDigitsMm   the same for millimetres
     */
    public record Options(String defaultUnits, int lowerDigitsInch, int lowerDigitsMm,
                          int upperDigitsInch, int upperDigitsMm, String defaultZeros) {
        public Options(String defaultUnits,int lowerDigitsInch,int lowerDigitsMm) {
            this(defaultUnits,lowerDigitsInch,lowerDigitsMm,2,3,"TZ");
        }
        public Options {
            if (defaultUnits != null && !defaultUnits.equals("IN") && !defaultUnits.equals("MM")) {
                throw new IllegalArgumentException("Units must be IN, MM or null: " + defaultUnits);
            }
            if (lowerDigitsInch < 0 || lowerDigitsInch > 9 || lowerDigitsMm < 0 || lowerDigitsMm > 9) {
                throw new IllegalArgumentException("Decimal digits must be between 0 and 9");
            }
            if(upperDigitsInch<1 || upperDigitsInch>9 || upperDigitsMm<1 || upperDigitsMm>9)
                throw new IllegalArgumentException("Integer digits must be between 1 and 9");
            if(!"LZ".equals(defaultZeros) && !"TZ".equals(defaultZeros))
                throw new IllegalArgumentException("Zeros must be LZ (leading kept) or TZ (trailing kept)");
        }

        public static Options standard() {
            return new Options(null, DEFAULT_LOWER_IN, DEFAULT_LOWER_MM);
        }
    }

    private final Options options;

    public ExcellonParser() {
        this(Options.standard());
    }

    public ExcellonParser(Options options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public ExcellonImage parse(Path file) throws IOException {
        return parse(file, CancellationToken.none());
    }

    public ExcellonImage parse(Path file, CancellationToken cancellationToken) throws IOException {
        return parse(file, cancellationToken, ProgressCallback.none());
    }

    public ExcellonImage parse(Path file, CancellationToken cancellationToken, ProgressCallback progressCallback)
            throws IOException {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressCallback, "progressCallback");
        cancellationToken.throwIfCancellationRequested();
        progressCallback.report(0);
        List<String> lines = Files.readAllLines(file);
        cancellationToken.throwIfCancellationRequested();
        return parse(lines, cancellationToken, progressCallback);
    }

    public ExcellonImage parse(List<String> rawLines) {
        return parse(rawLines, CancellationToken.none());
    }

    public ExcellonImage parse(List<String> rawLines, CancellationToken cancellationToken) {
        return parse(rawLines, cancellationToken, ProgressCallback.none());
    }

    public ExcellonImage parse(List<String> rawLines, CancellationToken cancellationToken,
                               ProgressCallback progressCallback) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressCallback, "progressCallback");
        cancellationToken.throwIfCancellationRequested();
        progressCallback.report(0);
        String units = null;
        Integer formatLowerOverride = null;
        Integer formatUpperOverride = null;
        String zeros=options.defaultZeros();
        Map<Integer, Double> toolDiameters = new LinkedHashMap<>();
        List<Geometry> shapes = new ArrayList<>();
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<ExcellonImage.Slot> slots = new ArrayList<>();

        Integer currentTool = null;
        double posX = 0;
        double posY = 0;
        boolean routeMode = false;
        boolean toolDown = false;
        int routeMotion = 0;
        boolean incremental = false;
        ExcellonRouteArc.Spec arcSpec = null;

        for (int lineIndex = 0; lineIndex < rawLines.size(); lineIndex++) {
            cancellationToken.throwIfCancellationRequested();
            reportProgressStep(progressCallback, lineIndex, rawLines.size(), 0, 0.95);
            String rawLine = rawLines.get(lineIndex);
            String line = rawLine.strip();
            if (line.isEmpty() || IGNORABLE_EXACT.contains(line)) {
                continue;
            }
            if (line.equals("M30")) break;
            if (line.equals("G90") || line.equals("G91")) {
                incremental = line.equals("G91"); continue;
            }
            if (line.equals("M71")) {
                units = "MM";
                continue;
            }
            if (line.equals("M72")) {
                units = "IN";
                continue;
            }
            if (line.equals("G05")) {
                routeMode = false;
                toolDown = false;
                continue;
            }
            if (line.equals("M15")) {
                if (!routeMode) {
                    throw new ExcellonParseException("M15 (plunge) outside a routed move: " + rawLine);
                }
                toolDown = true;
                continue;
            }
            if (line.equals("M16") || line.equals("M17")) {
                toolDown = false;
                continue;
            }
            if (line.startsWith(";")) {
                Matcher format = FORMAT_OVERRIDE.matcher(line);
                if (format.matches()) {
                    formatUpperOverride = Integer.parseInt(format.group(1));
                    formatLowerOverride = Integer.parseInt(format.group(2));
                    validateFormat(formatUpperOverride,formatLowerOverride);
                }
                continue; // any other comment, including KiCad's "; #@!" X2 attributes.
            }
            if(line.equals("LZ") || line.equals("TZ") || line.equals("L") || line.equals("T")) {
                zeros=line.startsWith("L") ? "LZ" : "TZ"; continue;
            }
            if(line.equals("METRIC") || line.startsWith("METRIC,") || line.equals("INCH") || line.startsWith("INCH,")) {
                units=line.startsWith("METRIC") ? "MM" : "IN";
                String[] parts=line.split(",");
                for(int i=1;i<parts.length;i++) {
                    String part=parts[i].strip();
                    if(part.equals("LZ") || part.equals("TZ") || part.equals("L") || part.equals("T")) zeros=part.startsWith("L") ? "LZ" : "TZ";
                    else if(part.matches("0+\\.0+")) {
                        int dot=part.indexOf('.'); formatUpperOverride=dot; formatLowerOverride=part.length()-dot-1;
                        validateFormat(formatUpperOverride,formatLowerOverride);
                    } else if(!part.isEmpty()) throw new ExcellonParseException("Unsupported units/format declaration: "+line);
                }
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
                units = unitsOrDefault(units, line);
                int lower = resolveLowerDigits(units, formatLowerOverride);
                int upper = resolveUpperDigits(units, formatUpperOverride);
                double x1 = position(slot.group(1), posX, incremental, upper, lower, zeros);
                double y1 = position(slot.group(2), posY, incremental, upper, lower, zeros);
                double x2 = position(slot.group(3), x1, incremental, upper, lower, zeros);
                double y2 = position(slot.group(4), y1, incremental, upper, lower, zeros);
                double diameter = requireToolDiameter(toolDiameters, currentTool, line);

                shapes.add(geometryFactory
                        .createLineString(new Coordinate[]{new Coordinate(x1, y1), new Coordinate(x2, y2)})
                        .buffer(diameter / 2.0, CIRCLE_QUADRANT_SEGMENTS));
                cancellationToken.throwIfCancellationRequested();
                slots.add(new ExcellonImage.Slot(currentTool, x1, y1, x2, y2));
                posX = x2;
                posY = y2;
                continue;
            }

            Matcher route = ROUTE_LINE.matcher(line);
            Matcher coord = COORD_LINE.matcher(line);
            boolean coordLine = coord.matches() && (coord.group(1) != null || coord.group(2) != null);
            boolean routeWords = route.matches();
            boolean routeCommand = routeWords && route.group(1)!=null;
            boolean parameters = routeWords && (line.indexOf('A')>=0 || line.indexOf('I')>=0 || line.indexOf('J')>=0);
            if (routeCommand || routeWords && (routeMode || parameters)) {
                if (routeCommand) { routeMode = true; routeMotion = Integer.parseInt(route.group(1)); }
                units = unitsOrDefault(units, line);
                int lower=resolveLowerDigits(units,formatLowerOverride), upper=resolveUpperDigits(units,formatUpperOverride);
                Map<Character,String> words = new LinkedHashMap<>();
                Matcher word = ROUTE_WORD.matcher(route.group(2));
                while(word.find()) {
                    char key=word.group(1).charAt(0);
                    if(words.put(key,word.group(2))!=null) throw new ExcellonParseException("Repeated route word: "+line);
                }
                if(words.containsKey('A')) {
                    if(words.containsKey('I') || words.containsKey('J')) throw new ExcellonParseException("Use A or I/J, not both: "+line);
                    arcSpec=new ExcellonRouteArc.Spec(decode(words.get('A'),upper,lower,zeros),null,null);
                } else if(words.containsKey('I') || words.containsKey('J')) {
                    if(!words.containsKey('I') || !words.containsKey('J')) throw new ExcellonParseException("Excellon circular rout requires both I and J: "+line);
                    arcSpec=new ExcellonRouteArc.Spec(null,decode(words.get('I'),upper,lower,zeros),decode(words.get('J'),upper,lower,zeros));
                }
                if (!words.containsKey('X') && !words.containsKey('Y')) continue;
                if (!routeMode) throw new ExcellonParseException("Circular parameters outside route mode: "+line);
                double x=position(words.get('X'),posX,incremental,upper,lower,zeros);
                double y=position(words.get('Y'),posY,incremental,upper,lower,zeros);
                if (routeMotion!=0) {
                    double diameter = requireToolDiameter(toolDiameters, currentTool, line);
                    ExcellonImage.Slot routed = routeMotion==1 ? new ExcellonImage.Slot(currentTool,posX,posY,x,y)
                            : ExcellonRouteArc.build(currentTool,posX,posY,x,y,routeMotion==2,arcSpec,units,cancellationToken);
                    if(toolDown) {
                        shapes.add(routed.centerline(geometryFactory).buffer(diameter/2,CIRCLE_QUADRANT_SEGMENTS));
                        cancellationToken.throwIfCancellationRequested(); slots.add(routed);
                    }
                }
                posX=x; posY=y;
                continue;
            }

            if (coordLine) {
                units = unitsOrDefault(units, line);
                int lower = resolveLowerDigits(units, formatLowerOverride);
                int upper = resolveUpperDigits(units, formatUpperOverride);
                double x = position(coord.group(1), posX, incremental, upper, lower, zeros);
                double y = position(coord.group(2), posY, incremental, upper, lower, zeros);
                double diameter = requireToolDiameter(toolDiameters, currentTool, line);

                shapes.add(geometryFactory.createPoint(new Coordinate(x, y)).buffer(diameter / 2.0, CIRCLE_QUADRANT_SEGMENTS));
                cancellationToken.throwIfCancellationRequested();
                drills.add(new ExcellonImage.Drill(currentTool, x, y));
                posX = x;
                posY = y;
                continue;
            }

            throw new ExcellonParseException("Unsupported Excellon line: " + rawLine);
        }

        progressCallback.report(0.95);
        cancellationToken.throwIfCancellationRequested();
        Geometry solid = shapes.isEmpty() ? geometryFactory.createPolygon() : OverlayNGRobust.union(shapes);
        cancellationToken.throwIfCancellationRequested();
        progressCallback.report(1);
        return new ExcellonImage(units != null ? units : options.defaultUnits() != null ? options.defaultUnits() : "IN", toolDiameters, drills, slots, solid);
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

    /** Explicit decimals win. LZ retains leading digits and pads missing trailing zeros;
     * TZ retains trailing digits and pads missing leading zeros. Signs do not count as digits. */
    private static double decode(String token, int upper, int lower,String zeros) {
        double value;
        if(token.indexOf('.')>=0) value=Double.parseDouble(token);
        else {
            int digits=token.length()-((token.startsWith("-") || token.startsWith("+")) ? 1 : 0);
            int scale="LZ".equals(zeros) ? digits-upper : lower;
            value=new java.math.BigDecimal(token).movePointLeft(scale).doubleValue();
        }
        if(!Double.isFinite(value)) throw new ExcellonParseException("Non-finite coordinate: "+token);
        return value;
    }
    private static double position(String token,double previous,boolean incremental,int upper,int lower,String zeros) {
        if(token==null) return previous;
        double decoded=decode(token,upper,lower,zeros), result=incremental ? previous+decoded : decoded;
        if(!Double.isFinite(result)) throw new ExcellonParseException("Incremental coordinate overflow: "+token);
        return result;
    }
    private static void validateFormat(int upper,int lower) {
        if(upper<1 || upper>9 || lower<0 || lower>9) throw new ExcellonParseException("Coordinate format must have 1–9 integer digits and 0–9 decimal digits");
    }
    private int resolveUpperDigits(String units,Integer override) {
        return override!=null ? override : "MM".equals(units) ? options.upperDigitsMm() : options.upperDigitsInch();
    }

    private int resolveLowerDigits(String units, Integer override) {
        if (override != null) {
            return override;
        }
        return "MM".equals(units) ? options.lowerDigitsMm() : options.lowerDigitsInch();
    }

    private String unitsOrDefault(String units, String line) {
        if (units != null) {
            return units;
        }
        if (options.defaultUnits() == null) {
            throw new ExcellonParseException("Coordinate data before units (INCH/METRIC/M71/M72) were declared: " + line);
        }
        return options.defaultUnits();
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
