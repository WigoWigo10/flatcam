package org.flatcam.cam.gcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Rebuilds a conservative XY preview from edited machine code. G0/G1 and
 * G2/G3 arcs in the XY plane, with absolute/relative coordinates, are
 * supported. Unknown coordinate-changing G commands suppress the preview
 * rather than leaving a misleading old plot.
 * This is not a machine-controller validator.
 *
 * <p>Tool width is only known where the program says so: FX-generated drill
 * jobs carry a {@link #toolMarker(int, double)} comment at the start of each
 * tool, so a reloaded or edited drilling program still draws holes and travel
 * ribbons at the real drill diameter (Python gets the same effect by
 * serializing its whole per-tool {@code gcode_parsed}). Without a marker the
 * preview falls back to a hairline.
 */
public final class GCodeToolpathParser {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final Pattern WORD = Pattern.compile("([A-Za-z])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))");
    private static final Pattern TOOL_MARKER = Pattern.compile(
            "FCFX\\s+TOOL\\s+T(\\d+)\\s+D(\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
    /** Python FlatCAM's Excellon programs announce a tool as "T1" followed by "(MSG, Change to Tool Dia = 0.8 ...)". */
    private static final Pattern PYTHON_TOOL_MESSAGE = Pattern.compile(
            "Change\\s+to\\s+Tool\\s+Dia\\s*=\\s*(\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
    /** FlatCAM FX's milling programs tell the preview the cutter width, one per tool section. */
    private static final Pattern MILL_MARKER = Pattern.compile(
            "FCFX\\s+MILL\\s+D(\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
    /** Python's milling programs state the cutter width in the header, e.g. "(TOOL DIAMETER: 0.1829 mm)". */
    private static final Pattern MILLING_DIAMETER = Pattern.compile(
            "TOOL\\s+DIAMETER:\\s*(\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_WORD_LINE = Pattern.compile("^\\s*T(\\d+)\\b");
    private static final Pattern LASER_PROFILE = Pattern.compile(
            "\\b(?:FCFX\\s+LASER|Preprocessor(?:\\s+(?:Geometry|Excellon))?\\s*:\\s*"
                    + "(?:GRBL_laser|Z_laser|Marlin_laser_FAN_pin|Marlin_laser_Spindle_pin))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RAPID_FEED_PROFILE = Pattern.compile(
            "\\bPreprocessor(?:\\s+(?:Geometry|Excellon))?\\s*:\\s*"
                    + "(?:Marlin|Repetier|Marlin_laser_FAN_pin|Marlin_laser_Spindle_pin)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ISEL_PROFILE = Pattern.compile(
            "\\bPreprocessor(?:\\s+(?:Geometry|Excellon))?\\s*:\\s*ISEL_CNC\\b", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PREVIEW_SEGMENTS = 50_000;
    /**
     * G0 rate assumed for the time estimate - Python's {@code tools_drill_feedrate_rapid}
     * default, since a program never states how fast its controller rapids.
     */
    static final double RAPID_MM_PER_MINUTE = 1500;

    /** Comment text that tells the preview how wide the cutter of a milling program is. */
    public static String millMarker(double diameter) {
        return String.format(Locale.ROOT, "FCFX MILL D%.4f", diameter);
    }

    /** Comment text that tells the preview which tool (and diameter) the following moves use. */
    public static String toolMarker(int toolId, double diameter) {
        return String.format(Locale.ROOT, "FCFX TOOL T%d D%.4f", toolId, diameter);
    }

    /** Header detection for file dialogs; this is not a controller-program validity check. */
    public static boolean isIcpProgram(String program) {
        return ControllerProgramCodec.isIcp(program);
    }

    public static boolean isHpglProgram(String program) {
        return HpglProgramCodec.isHpgl(program);
    }

    public static boolean isRolandProgram(String program) {
        return RolandProgramCodec.isRoland(program);
    }

    /**
     * One tool's share of a program: its drill hits and routed slots, how deep it
     * plunged, and its own cut/travel preview so a viewer can show or hide one tool
     * at a time (Python's per-row "Plot" checkbox in the CNC Job tools table).
     */
    public record ToolUsage(int toolId, Double diameter, int drills, int slots, double deepestZ,
                            Geometry cutGeometry, Geometry travelGeometry) {
    }

    /** A plunge in machining order (1-based {@code sequence} across the whole program). */
    public record DrillHit(int sequence, int toolId, double x, double y) {
    }

    /**
     * A numbered end of a travel move in program order, Python's CNCJob annotation for
     * milling jobs: every G0 move numbers where it starts and where it ends, skipping
     * positions that already carry a number.
     */
    public record PathMark(int sequence, double x, double y) {
    }

    /**
     * The middle of one cutting move and the way the tool travels through it
     * ({@code dx,dy} is a unit vector), so a viewer can draw direction arrows.
     */
    public record CutArrow(double x, double y, double dx, double dy, double length) {
    }

    /**
     * One leg of the program in machining order: a run of travel moves, a run of cutting
     * moves, or a single drill plunge ({@code travel} false with one point). Consecutive
     * steps chain end to start, so a viewer can walk through what comes before and after.
     * {@code xy} holds x0,y0,x1,y1,...; {@code fromMark}/{@code toMark} are the
     * {@link PathMark} numbers at its two ends (0 when an end carries none). {@code endMinutes} is the
     * estimated machine time elapsed when the step finishes (NaN when a feed move has no F word).
     */
    public record PathStep(int index, boolean travel, double[] xy, double length, int fromMark, int toMark,
                           double endMinutes) {
    }

    /** Groups consecutive moves of the same kind into steps; a Z crossing of zero ends the current one. */
    private static final class StepBuilder {
        private final List<double[]> finished = new ArrayList<>();
        private final List<Boolean> finishedTravel = new ArrayList<>();
        private final List<Double> finishedLength = new ArrayList<>();
        private final List<Double> finishedMinutes = new ArrayList<>();
        /** Program time so far, kept current by the parser so a step records when it ended. */
        private double clock;
        private final List<Double> points = new ArrayList<>();
        private boolean travel;
        private double length;
        private int coordinates;

        void segment(boolean isTravel, Coordinate[] path) {
            if (path.length < 2 || coordinates >= MAX_PREVIEW_SEGMENTS) {
                return;
            }
            if (!points.isEmpty() && travel != isTravel) {
                flush();
            }
            travel = isTravel;
            int from = 0;
            if (!points.isEmpty()) {
                int last = points.size();
                if (points.get(last - 2) == path[0].x && points.get(last - 1) == path[0].y) {
                    from = 1;
                } else {
                    flush();
                    travel = isTravel;
                }
            }
            for (int i = from; i < path.length; i++) {
                int size = points.size();
                if (size >= 2) {
                    length += Math.hypot(path[i].x - points.get(size - 2), path[i].y - points.get(size - 1));
                }
                points.add(path[i].x);
                points.add(path[i].y);
                coordinates++;
            }
        }

        void drill(double x, double y) {
            flush();
            finished.add(new double[]{x, y});
            finishedTravel.add(false);
            finishedLength.add(0.0);
            finishedMinutes.add(clock);
        }

        void flush() {
            if (points.isEmpty()) {
                return;
            }
            double[] xy = new double[points.size()];
            for (int i = 0; i < xy.length; i++) {
                xy[i] = points.get(i);
            }
            finished.add(xy);
            finishedTravel.add(travel);
            finishedLength.add(length);
            finishedMinutes.add(clock);
            points.clear();
            length = 0;
        }

        List<PathStep> build(Map<List<Double>, Integer> markAt, boolean timeKnown) {
            flush();
            List<PathStep> steps = new ArrayList<>(finished.size());
            for (int i = 0; i < finished.size(); i++) {
                double[] xy = finished.get(i);
                int last = xy.length - 2;
                steps.add(new PathStep(i, finishedTravel.get(i), xy, finishedLength.get(i),
                        markAt.getOrDefault(List.of(xy[0], xy[1]), 0),
                        markAt.getOrDefault(List.of(xy[last], xy[last + 1]), 0),
                        timeKnown ? finishedMinutes.get(i) : Double.NaN));
            }
            return steps;
        }
    }

    /**
     * Per-program totals, Python's CNCJob "Travelled distance" / "Estimated time".
     * {@code estimatedMinutes} is NaN when a feed move has no F word to time it by.
     */
    /** {@code cutterDiameter} is the width a milling program states for its cutter (null for drill jobs or when unstated). */
    public record ToolpathStats(List<ToolUsage> tools, List<DrillHit> hits, List<PathMark> pathMarks,
                                List<CutArrow> cutArrows, List<PathStep> steps, Double cutterDiameter, double xyDistance,
                                double estimatedMinutes, String units) {
        public ToolpathStats {
            tools = List.copyOf(tools);
            hits = List.copyOf(hits);
            pathMarks = List.copyOf(pathMarks);
            cutArrows = List.copyOf(cutArrows);
            steps = List.copyOf(steps);
        }

        public boolean hasTools() {
            return !tools.isEmpty();
        }
    }

    public record Result(Geometry travelGeometry, Geometry cutGeometry, String warning,
                         int lineCount, String units, Geometry travelCenterlines,
                         Geometry cutCenterlines, ToolpathStats stats) {
        public Result(Geometry travelGeometry, Geometry cutGeometry, String warning,
                      int lineCount, String units, Geometry travelCenterlines,
                      Geometry cutCenterlines) {
            this(travelGeometry, cutGeometry, warning, lineCount, units,
                    travelCenterlines, cutCenterlines, null);
        }

        public Result(Geometry travelGeometry, Geometry cutGeometry, String warning,
                      int lineCount, String units) {
            this(travelGeometry, cutGeometry, warning, lineCount, units, null, null, null);
        }

        public boolean plotAvailable() {
            return warning == null;
        }
    }

    /** Mutable per-tool tally while parsing. */
    private static final class ToolTally {
        final int toolId;
        Double diameter;
        int drills;
        int slots;
        double deepestZ = Double.POSITIVE_INFINITY;
        final List<Geometry> cut = new ArrayList<>();
        final List<Geometry> travel = new ArrayList<>();

        ToolTally(int toolId, Double diameter) {
            this.toolId = toolId;
            this.diameter = diameter;
        }

        ToolUsage freeze() {
            return new ToolUsage(toolId, diameter, drills, slots,
                    Double.isFinite(deepestZ) ? deepestZ : 0,
                    FACTORY.createGeometryCollection(cut.toArray(Geometry[]::new)),
                    FACTORY.createGeometryCollection(travel.toArray(Geometry[]::new)));
        }
    }

    /** Display-only paths; the buffered toolpath geometries above remain unchanged. */
    private static final class CenterlinePreview {
        private static final int MAX_POINTS_PER_PATH = 2_000;
        private final List<Geometry> travel = new ArrayList<>();
        private final List<Geometry> cut = new ArrayList<>();
        private List<Coordinate> active = new ArrayList<>();
        private Boolean activeTravel;

        void addPath(boolean isTravel, Geometry path) {
            Coordinate[] points = path.getCoordinates();
            if (points.length < 2) {
                return;
            }
            if (activeTravel == null || activeTravel != isTravel
                    || !active.get(active.size() - 1).equals2D(points[0])) {
                flush();
                activeTravel = isTravel;
                active.add(new Coordinate(points[0]));
            }
            for (int i = 1; i < points.length; i++) {
                if (active.size() == MAX_POINTS_PER_PATH) {
                    Coordinate last = active.get(active.size() - 1);
                    flush();
                    activeTravel = isTravel;
                    active.add(new Coordinate(last));
                }
                active.add(new Coordinate(points[i]));
            }
        }

        void addPoint(boolean isTravel, Coordinate point) {
            flush();
            (isTravel ? travel : cut).add(FACTORY.createPoint(point));
        }

        Geometry travelGeometry() {
            flush();
            return FACTORY.createGeometryCollection(travel.toArray(Geometry[]::new));
        }

        Geometry cutGeometry() {
            flush();
            return FACTORY.createGeometryCollection(cut.toArray(Geometry[]::new));
        }

        private void flush() {
            if (activeTravel != null && active.size() >= 2) {
                (activeTravel ? travel : cut).add(FACTORY.createLineString(active.toArray(Coordinate[]::new)));
            }
            active = new ArrayList<>();
            activeTravel = null;
        }
    }

    private GCodeToolpathParser() {
    }

    public static Result parse(String gcode, CancellationToken cancellation, ProgressCallback progress) {
        if (gcode == null || gcode.isBlank()) {
            throw new IllegalArgumentException("O G-code nao pode estar vazio.");
        }
        if (ControllerProgramCodec.isIcp(gcode) || HpglProgramCodec.isHpgl(gcode) || RolandProgramCodec.isRoland(gcode)) {
            String normalized = RolandProgramCodec.isRoland(gcode)
                    ? RolandProgramCodec.normalize(gcode, cancellation, fraction -> progress.report(fraction * 0.25))
                    : HpglProgramCodec.isHpgl(gcode)
                    ? HpglProgramCodec.normalize(gcode, cancellation, fraction -> progress.report(fraction * 0.25))
                    : ControllerProgramCodec.normalizeIcp(gcode, cancellation, fraction -> progress.report(fraction * 0.25));
            Result parsed = parse(normalized, cancellation, fraction -> progress.report(0.25 + fraction * 0.75));
            return new Result(parsed.travelGeometry(), parsed.cutGeometry(), parsed.warning(),
                    (int) gcode.lines().count(), parsed.units(), parsed.travelCenterlines(), parsed.cutCenterlines(), parsed.stats());
        }
        List<String> lines = gcode.lines().toList();
        // Both laser emission and plotter pen state classify XY independently of Z.
        boolean laserProfile = LASER_PROFILE.matcher(gcode).find() || gcode.contains("; FCFX PLOTTER");
        boolean explicitRapidFeed = RAPID_FEED_PROFILE.matcher(gcode).find();
        boolean iselProfile = ISEL_PROFILE.matcher(gcode).find();
        boolean laserOn = false;
        Double laserPower = null;
        List<Geometry> travel = new ArrayList<>();
        List<Geometry> cut = new ArrayList<>();
        CenterlinePreview centerlines = new CenterlinePreview();
        boolean absolute = true;
        boolean absoluteArcCenter = false;
        boolean metric = true;
        Boolean lastMetric = null;
        boolean havePosition = false;
        int motion = -1;
        double x = 0;
        double y = 0;
        double z = 0;
        String warning = null;
        Map<Integer, ToolTally> tools = new LinkedHashMap<>();
        List<DrillHit> hits = new ArrayList<>();
        List<PathMark> pathMarks = new ArrayList<>();
        List<CutArrow> cutArrows = new ArrayList<>();
        StepBuilder stepBuilder = new StepBuilder();
        java.util.Set<List<Double>> markedPositions = new java.util.HashSet<>();
        ToolTally tool = null;
        int pythonToolId = 0;
        boolean pythonExcellon = false;
        Double millingDiameter = null;
        java.util.Set<Double> millingWidths = new java.util.LinkedHashSet<>();
        ToolTally lastHitTool = null;
        double lastHitX = Double.NaN;
        double lastHitY = Double.NaN;
        boolean pendingHit = false;
        double feed = 0;
        boolean timeKnown = true;
        double xyDistance = 0;
        double minutes = 0;
        progress.report(0);
        for (int index = 0; index < lines.size(); index++) {
            cancellation.throwIfCancellationRequested();
            String raw = lines.get(index);
            Matcher marker = TOOL_MARKER.matcher(raw);
            if (marker.find()) {
                double diameter = Double.parseDouble(marker.group(2));
                tool = tools.computeIfAbsent(Integer.parseInt(marker.group(1)),
                        id -> new ToolTally(id, diameter));
                tool.diameter = diameter;
            }
            Matcher mill = MILL_MARKER.matcher(raw);
            if (mill.find()) {
                millingDiameter = Double.parseDouble(mill.group(1));
                millingWidths.add(millingDiameter);
            }
            if (!pythonExcellon && raw.contains("G-code from Excellon")) {
                pythonExcellon = true;
            }
            Matcher toolWord = TOOL_WORD_LINE.matcher(raw);
            if (!pythonExcellon) {
                // Milling programs draw at the tool's width but are numbered by their travel moves, not as drills.
                Matcher milling = MILLING_DIAMETER.matcher(raw);
                if (milling.find()) {
                    millingDiameter = Double.parseDouble(milling.group(1));
                    millingWidths.add(millingDiameter);
                }
            } else if (toolWord.find()) {
                pythonToolId = Integer.parseInt(toolWord.group(1));
            } else {
                Matcher message = PYTHON_TOOL_MESSAGE.matcher(raw);
                if (message.find()) {
                    double diameter = Double.parseDouble(message.group(1));
                    tool = tools.computeIfAbsent(pythonToolId > 0 ? pythonToolId : tools.size() + 1,
                            id -> new ToolTally(id, diameter));
                    tool.diameter = diameter;
                    pythonToolId = 0;
                }
            }
            String line = raw.replaceAll("\\([^)]*\\)", "");
            if (index == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            int semicolon = line.indexOf(';');
            if (semicolon >= 0) {
                line = line.substring(0, semicolon);
            }
            line = line.trim();
            if (line.isEmpty() || line.equals("%")) {
                continue;
            }
            // Repetier-Host directive: message text is not machine code or a coordinate move.
            if (line.equalsIgnoreCase("@pause")
                    || line.toLowerCase(Locale.ROOT).startsWith("@pause ")) continue;
            boolean wasLaserActive = laserOn && (laserPower == null || laserPower > 0);
            Matcher matcher = WORD.matcher(line);
            int cursor = 0;
            Double newX = null;
            Double newY = null;
            Double newZ = null;
            Double arcI = null;
            Double arcJ = null;
            Double arcR = null;
            Boolean switchLaser = null;
            while (matcher.find()) {
                String between = line.substring(cursor, matcher.start());
                if (!between.isBlank()) {
                    throw new IllegalArgumentException("G-code invalido na linha " + (index + 1) + ": " + between.trim());
                }
                cursor = matcher.end();
                double value;
                try {
                    value = Double.parseDouble(matcher.group(2));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Numero invalido na linha " + (index + 1), exception);
                }
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Numero fora do limite na linha " + (index + 1));
                }
                switch (Character.toUpperCase(matcher.group(1).charAt(0))) {
                    case 'G' -> {
                        int code = (int) value;
                        if (value == 90.1) {
                            absoluteArcCenter = true;
                        } else if (value == 91.1) {
                            absoluteArcCenter = false;
                        } else if (value != code) {
                            warning = "G-code com comando G fracionario: pre-visualizacao indisponivel.";
                        } else if (code >= 0 && code <= 3) {
                            motion = code;
                        } else if (code == 20) {
                            metric = false;
                            if (lastMetric != null && lastMetric && havePosition) {
                                warning = "Troca de unidades no mesmo programa: pre-visualizacao indisponivel.";
                            }
                            lastMetric = false;
                        } else if (code == 21 || (code == 71 && iselProfile)) {
                            metric = true;
                            if (lastMetric != null && !lastMetric && havePosition) {
                                warning = "Troca de unidades no mesmo programa: pre-visualizacao indisponivel.";
                            }
                            lastMetric = true;
                        } else if (code == 90) {
                            absolute = true;
                        } else if (code == 91) {
                            absolute = false;
                        } else if (code == 31 || code == 92) {
                            warning = "Sondagem G31/G92: contato e referencia Z dependem da maquina; pre-visualizacao indisponivel.";
                        } else if (code != 4 && code != 17 && code != 40 && code != 49
                                && code != 54 && code != 64 && code != 94) {
                            warning = "G-code com G" + code + ": pre-visualizacao indisponivel para este comando.";
                        }
                    }
                    case 'X' -> newX = value;
                    case 'Y' -> newY = value;
                    case 'Z' -> newZ = value;
                    case 'I' -> arcI = value;
                    case 'J' -> arcJ = value;
                    case 'R' -> arcR = value;
                    case 'F' -> feed = value;
                    case 'M' -> {
                        if (laserProfile && (value == 3 || value == 4 || value == 106)) switchLaser = true;
                        else if (laserProfile && (value == 5 || value == 107)) switchLaser = false;
                    }
                    case 'S' -> { if (laserProfile) laserPower = value; }
                    default -> { /* Spindle, tool, M code and line number do not change XY. */ }
                }
            }
            if (!line.substring(cursor).isBlank()) {
                throw new IllegalArgumentException("G-code invalido na linha " + (index + 1) + ": "
                        + line.substring(cursor).trim());
            }
            if (switchLaser != null) laserOn = switchLaser;
            boolean laserActive = laserOn && (laserPower == null || laserPower > 0);
            if (laserProfile && wasLaserActive != laserActive) stepBuilder.flush();
            double nextX = newX == null ? x : absolute ? newX : x + newX;
            double nextY = newY == null ? y : absolute ? newY : y + newY;
            double nextZ = newZ == null ? z : absolute ? newZ : z + newZ;
            if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) {
                throw new IllegalArgumentException("Coordenada fora do limite na linha " + (index + 1));
            }
            boolean movesXY = newX != null || newY != null;
            if ((motion == 2 || motion == 3) && !havePosition
                    && (movesXY || arcI != null || arcJ != null || arcR != null)) {
                warning = "Arco sem posicao XY inicial: pre-visualizacao indisponivel.";
            }
            boolean arcMove = (motion == 2 || motion == 3) && havePosition
                    && (movesXY || arcI != null || arcJ != null || arcR != null);
            boolean lateral = movesXY && havePosition && (nextX != x || nextY != y)
                    && motion >= 0 && motion <= 1;
            // line_xyz repeats X/Y even on a pure Z plunge; test actual XY displacement, not word presence.
            boolean plunge = !laserProfile && !lateral && !arcMove && newZ != null && motion == 1
                    && nextZ < 0 && nextZ < z && havePosition;
            Double width = tool != null && tool.diameter != null && tool.diameter > 0 ? tool.diameter
                    : millingDiameter;
            boolean knownWidth = width != null && width > 0;
            double radius = knownWidth ? width / 2 : metric ? 0.01 : 0.0004;
            int quadrantSegments = knownWidth ? 8 : 4;
            double xyLength = 0;
            if (warning == null && (lateral || plunge || arcMove)
                    && travel.size() + cut.size() >= MAX_PREVIEW_SEGMENTS) {
                warning = "Programa muito grande para pre-visualizacao detalhada.";
                travel.clear();
                cut.clear();
            }
            if (warning == null && arcMove) {
                if (!laserProfile && (z >= 0) != (nextZ >= 0)) {
                    warning = "Arco cruzando Z=0: pre-visualizacao indisponivel.";
                } else {
                    try {
                        Geometry centerline = arcPath(x, y, nextX, nextY, arcI, arcJ, arcR,
                                absoluteArcCenter, motion == 2);
                        boolean isTravel = laserProfile ? !laserActive : nextZ >= 0;
                        addShape(isTravel, centerline.buffer(radius, quadrantSegments), travel, cut, tool);
                        centerlines.addPath(isTravel, centerline);
                        stepBuilder.segment(isTravel, centerline.getCoordinates());
                        xyLength = centerline.getLength();
                        if (!isTravel) {
                            Coordinate[] arcPoints = centerline.getCoordinates();
                            int middle = arcPoints.length / 2;
                            if (arcPoints.length >= 2) {
                                addCutArrow(cutArrows, arcPoints[middle - 1].x, arcPoints[middle - 1].y,
                                        arcPoints[middle].x, arcPoints[middle].y, xyLength);
                            }
                        }
                    } catch (IllegalArgumentException invalidArc) {
                        warning = "Arco invalido na linha " + (index + 1) + ": " + invalidArc.getMessage();
                    }
                }
            } else if (warning == null && lateral) {
                Geometry centerline = FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(x, y), new Coordinate(nextX, nextY)});
                boolean isTravel = motion == 0 || (laserProfile ? !laserActive : nextZ >= 0);
                addShape(isTravel, centerline.buffer(radius, quadrantSegments), travel, cut, tool);
                centerlines.addPath(isTravel, centerline);
                stepBuilder.segment(isTravel, centerline.getCoordinates());
                xyLength = centerline.getLength();
                if (!isTravel) {
                    addCutArrow(cutArrows, x, y, nextX, nextY);
                }
                if (isTravel) {
                    for (double[] end : new double[][]{{x, y}, {nextX, nextY}}) {
                        if (markedPositions.add(List.of(end[0], end[1]))) {
                            pathMarks.add(new PathMark(pathMarks.size() + 1, end[0], end[1]));
                        }
                    }
                }
                if (!isTravel && tool != null && pendingHit) {
                    tool.slots++;
                    pendingHit = false;
                }
            } else if (warning == null && plunge) {
                addShape(false, FACTORY.createPoint(new Coordinate(x, y)).buffer(radius, quadrantSegments),
                        travel, cut, tool);
                centerlines.addPoint(false, new Coordinate(x, y));
                // Multi-depth passes re-plunge at the same spot; they are one hole, not several.
                if (tool != null && !(lastHitTool == tool && lastHitX == x && lastHitY == y)) {
                    hits.add(new DrillHit(hits.size() + 1, tool.toolId, x, y));
                    stepBuilder.drill(x, y);
                    lastHitTool = tool;
                    lastHitX = x;
                    lastHitY = y;
                    pendingHit = true;
                }
            }
            if (tool != null && nextZ < 0) {
                tool.deepestZ = Math.min(tool.deepestZ, nextZ);
            }
            if (pendingHit && tool != null && newZ != null && nextZ >= 0) {
                tool.drills++;
                pendingHit = false;
            }
            double zLength = newZ == null ? 0 : Math.abs(nextZ - z);
            double moveLength = Math.hypot(xyLength, zLength);
            if (moveLength > 0) {
                if (motion == 1 || motion == 2 || motion == 3) {
                    if (feed > 0) {
                        minutes += moveLength / feed;
                    } else {
                        timeKnown = false;
                    }
                } else {
                    double rapid = explicitRapidFeed && feed > 0 ? feed
                            : metric ? RAPID_MM_PER_MINUTE : RAPID_MM_PER_MINUTE / 25.4;
                    minutes += moveLength / rapid;
                }
            }
            xyDistance += xyLength;
            stepBuilder.clock = minutes;
            if (!laserProfile && (z >= 0) != (nextZ >= 0)) {
                stepBuilder.flush();
            }
            x = nextX;
            y = nextY;
            z = nextZ;
            havePosition |= movesXY;
            if (index % 256 == 0 || index == lines.size() - 1) {
                progress.report((index + 1.0) / lines.size());
            }
        }
        cancellation.throwIfCancellationRequested();
        progress.report(1);
        if (warning != null) {
            return new Result(null, null, warning, lines.size(), metric ? "MM" : "IN");
        }
        if (pendingHit && tool != null) {
            tool.drills++;
        }
        ToolpathStats stats = new ToolpathStats(tools.values().stream().map(ToolTally::freeze).toList(),
                hits, pathMarks, hits.isEmpty() ? cutArrows : List.of(), stepBuilder.build(markPositions(pathMarks), timeKnown),
                hits.isEmpty() && millingWidths.size() == 1 ? millingDiameter : null, xyDistance, timeKnown ? minutes : Double.NaN, metric ? "MM" : "IN");
        return new Result(FACTORY.createGeometryCollection(travel.toArray(Geometry[]::new)),
                FACTORY.createGeometryCollection(cut.toArray(Geometry[]::new)), null,
                lines.size(), metric ? "MM" : "IN",
                centerlines.travelGeometry(), centerlines.cutGeometry(), stats);
    }

    private static Map<List<Double>, Integer> markPositions(List<PathMark> marks) {
        Map<List<Double>, Integer> byPosition = new HashMap<>();
        for (PathMark mark : marks) {
            byPosition.put(List.of(mark.x(), mark.y()), mark.sequence());
        }
        return byPosition;
    }

    private static void addCutArrow(List<CutArrow> arrows, double fromX, double fromY, double toX, double toY) {
        addCutArrow(arrows, fromX, fromY, toX, toY, Math.hypot(toX - fromX, toY - fromY));
    }

    /** Records the arrow at the middle of the segment from-to; {@code length} is the whole move's length. */
    private static void addCutArrow(List<CutArrow> arrows, double fromX, double fromY, double toX, double toY,
                                    double length) {
        double segment = Math.hypot(toX - fromX, toY - fromY);
        if (segment <= 0 || arrows.size() >= MAX_PREVIEW_SEGMENTS) {
            return;
        }
        arrows.add(new CutArrow((fromX + toX) / 2, (fromY + toY) / 2,
                (toX - fromX) / segment, (toY - fromY) / segment, length));
    }

    private static void addShape(boolean isTravel, Geometry shape, List<Geometry> travel, List<Geometry> cut,
                                 ToolTally tool) {
        (isTravel ? travel : cut).add(shape);
        if (tool != null) {
            (isTravel ? tool.travel : tool.cut).add(shape);
        }
    }

    private static Geometry arcPath(double startX, double startY, double endX, double endY,
                                    Double i, Double j, Double r, boolean absoluteCenter,
                                    boolean clockwise) {
        if (r != null && (i != null || j != null) || r == null && i == null && j == null) {
            throw new IllegalArgumentException("informe I/J ou R, nao ambos");
        }
        double cx;
        double cy;
        if (r != null) {
            double chord = Math.hypot(endX - startX, endY - startY);
            double magnitude = Math.abs(r);
            if (chord == 0 || magnitude == 0 || chord > 2 * magnitude + 1e-9) {
                throw new IllegalArgumentException("raio R incompatível com os extremos");
            }
            double half = chord / 2;
            double height = Math.sqrt(Math.max(0, magnitude * magnitude - half * half));
            double midX = (startX + endX) / 2;
            double midY = (startY + endY) / 2;
            double normalX = -(endY - startY) / chord;
            double normalY = (endX - startX) / chord;
            double ax = midX + normalX * height;
            double ay = midY + normalY * height;
            double bx = midX - normalX * height;
            double by = midY - normalY * height;
            double sweepA = Math.abs(sweep(startX, startY, endX, endY, ax, ay, clockwise));
            double sweepB = Math.abs(sweep(startX, startY, endX, endY, bx, by, clockwise));
            boolean chooseA = r > 0 ? sweepA <= sweepB : sweepA >= sweepB;
            cx = chooseA ? ax : bx;
            cy = chooseA ? ay : by;
        } else {
            if (absoluteCenter && (i == null || j == null)) {
                throw new IllegalArgumentException("centro absoluto exige I e J");
            }
            cx = absoluteCenter ? i : startX + (i == null ? 0 : i);
            cy = absoluteCenter ? j : startY + (j == null ? 0 : j);
        }
        double startRadius = Math.hypot(startX - cx, startY - cy);
        double endRadius = Math.hypot(endX - cx, endY - cy);
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(startRadius)
                || startRadius <= 0 || Math.abs(startRadius - endRadius) > Math.max(1e-5, startRadius * 1e-4)) {
            throw new IllegalArgumentException("centro e extremos nao definem o mesmo raio");
        }
        double startAngle = Math.atan2(startY - cy, startX - cx);
        double angle = sweep(startX, startY, endX, endY, cx, cy, clockwise);
        int steps = Math.max(8, (int) Math.ceil(Math.abs(angle) / (Math.PI / 36)));
        Coordinate[] points = new Coordinate[steps + 1];
        for (int step = 0; step <= steps; step++) {
            double radians = startAngle + angle * step / steps;
            points[step] = new Coordinate(cx + startRadius * Math.cos(radians),
                    cy + startRadius * Math.sin(radians));
        }
        points[0] = new Coordinate(startX, startY);
        points[steps] = new Coordinate(endX, endY);
        return FACTORY.createLineString(points);
    }

    private static double sweep(double startX, double startY, double endX, double endY,
                                double cx, double cy, boolean clockwise) {
        if (startX == endX && startY == endY) {
            return clockwise ? -2 * Math.PI : 2 * Math.PI;
        }
        double start = Math.atan2(startY - cy, startX - cx);
        double end = Math.atan2(endY - cy, endX - cx);
        double delta = end - start;
        if (clockwise) {
            if (delta >= 0) {
                delta -= 2 * Math.PI;
            }
        } else if (delta <= 0) {
            delta += 2 * Math.PI;
        }
        return delta;
    }
}
