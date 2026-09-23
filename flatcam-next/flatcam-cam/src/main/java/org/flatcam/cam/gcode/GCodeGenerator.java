package org.flatcam.cam.gcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.cutout.CutoutResult;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.isolation.IsolationResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * Generates GRBL-compatible drilling G-code from a parsed {@link ExcellonImage}:
 * absolute positioning, one rapid+plunge+retract per hole and per slot
 * (a straight plunge-cut-retract, single pass - no stepped/pecking cycles),
 * grouped and ordered by tool. Deliberately avoids canned cycles (G81/G82) -
 * GRBL (the firmware on common hobby routers, e.g. the Genmitsu 3030) does
 * not support them - so this is plain G0/G1 rather than the shorter but
 * less portable canned-cycle form.
 *
 * <p>This is new functionality, not a port of legacy behavior - there is no
 * Python oracle to diff against here (see the Fase 0 baseline tests for
 * that pattern elsewhere in this codebase). Correctness is covered by a
 * plain unit test (GCodeGeneratorTest) instead.
 */
public final class GCodeGenerator {

    private static final int STROKE_QUADRANT_SEGMENTS = 16;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private GCodeGenerator() {
    }

    public static String generateDrillGCode(ExcellonImage image, DrillGCodeParameters params) {
        return generateDrillCncJob(image, params, null).gcode();
    }

    /**
     * @param selectedToolIds which tools to include, or {@code null}/empty for all of them - mirrors
     *                        appTools/ToolDrilling.py's own tools table, where row SELECTION (not a
     *                        checkbox) decides which tools' drills/slots go into the generated G-code.
     */
    public static String generateDrillGCode(ExcellonImage image, DrillGCodeParameters params, Set<Integer> selectedToolIds) {
        return generateDrillCncJob(image, params, selectedToolIds).gcode();
    }

    /** Same as {@link #generateDrillGCode(ExcellonImage, DrillGCodeParameters, Set)}, plus the CNCJob's own toolpath geometry (see {@link CncJobResult}). */
    public static CncJobResult generateDrillCncJob(ExcellonImage image, DrillGCodeParameters params, Set<Integer> selectedToolIds) {
        Map<Integer, List<ExcellonImage.Drill>> drillsByTool =
                image.drills().stream().collect(Collectors.groupingBy(ExcellonImage.Drill::toolId));
        Map<Integer, List<ExcellonImage.Slot>> slotsByTool =
                image.slots().stream().collect(Collectors.groupingBy(ExcellonImage.Slot::toolId));

        SortedSet<Integer> toolIds = new TreeSet<>();
        toolIds.addAll(drillsByTool.keySet());
        toolIds.addAll(slotsByTool.keySet());
        if (selectedToolIds != null && !selectedToolIds.isEmpty()) {
            toolIds.retainAll(selectedToolIds);
        }

        StringBuilder gcode = new StringBuilder();
        line(gcode, "; Gerado por FlatCAM Next (prototipo) - furacao");
        line(gcode, "; Unidades do arquivo de origem: %s", image.units());
        line(gcode, image.units().equals("MM") ? "G21" : "G20");
        line(gcode, "G90");
        line(gcode, "G94");
        line(gcode, "G0 Z%s", fmt(params.safeZ()));

        List<Geometry> travelShapes = new ArrayList<>();
        List<Geometry> cutShapes = new ArrayList<>();
        double lastX = 0;
        double lastY = 0;

        boolean firstTool = true;
        for (int toolId : toolIds) {
            // Falls back to a thin nominal radius for a tool diameter this Excellon file
            // never declared - only the toolpath preview is affected, not the G-code itself.
            double toolDiameter = image.toolDiameters().getOrDefault(toolId, 0.2);
            double radius = toolDiameter / 2.0;

            if (!firstTool) {
                if (params.spindleSpeedRpm() > 0) {
                    line(gcode, "M5");
                }
                if (params.pauseForToolChange()) {
                    Double diameter = image.toolDiameters().get(toolId);
                    line(gcode, "M0 ; troque para a ferramenta T%d (diametro %s %s) e continue",
                            toolId, diameter != null ? fmt(diameter) : "?", image.units());
                }
            }
            firstTool = false;

            if (params.spindleSpeedRpm() > 0) {
                line(gcode, "M3 S%d", params.spindleSpeedRpm());
            }

            for (ExcellonImage.Drill drill : drillsByTool.getOrDefault(toolId, List.of())) {
                addTravel(travelShapes, lastX, lastY, drill.x(), drill.y(), radius);
                // A drill doesn't move laterally while cutting - camlib.py's own gcode_parse()
                // fabricates a circle at the hole to represent the "cut" shape too.
                cutShapes.add(circle(drill.x(), drill.y(), radius));
                lastX = drill.x();
                lastY = drill.y();

                line(gcode, "G0 X%s Y%s", fmt(drill.x()), fmt(drill.y()));
                line(gcode, "G1 Z-%s F%s", fmt(params.drillDepth()), fmt(params.feedRate()));
                line(gcode, "G0 Z%s", fmt(params.safeZ()));
            }
            for (ExcellonImage.Slot slot : slotsByTool.getOrDefault(toolId, List.of())) {
                addTravel(travelShapes, lastX, lastY, slot.x1(), slot.y1(), radius);
                cutShapes.add(strokeSegment(slot.x1(), slot.y1(), slot.x2(), slot.y2(), radius));
                lastX = slot.x2();
                lastY = slot.y2();

                line(gcode, "G0 X%s Y%s", fmt(slot.x1()), fmt(slot.y1()));
                line(gcode, "G1 Z-%s F%s", fmt(params.drillDepth()), fmt(params.feedRate()));
                line(gcode, "G1 X%s Y%s F%s", fmt(slot.x2()), fmt(slot.y2()), fmt(params.feedRate()));
                line(gcode, "G0 Z%s", fmt(params.safeZ()));
            }
        }
        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M5");
        }
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        line(gcode, "M30");
        return new CncJobResult(gcode.toString(), unionOrEmpty(travelShapes), unionOrEmpty(cutShapes));
    }

    public static String generateIsolationGCode(IsolationResult result, IsolationGCodeParameters params) {
        return generateIsolationGCode(result, params, 0.2);
    }

    /**
     * @param toolDiameter the isolation tool's own diameter (IsolationParameters.toolDiameter()) -
     *                     only used to size the toolpath preview's ribbon width, not the G-code itself.
     */
    public static String generateIsolationGCode(IsolationResult result, IsolationGCodeParameters params, double toolDiameter) {
        return generateIsolationCncJob(result, params, toolDiameter).gcode();
    }

    /**
     * Generates isolation-routing G-code: one rapid+plunge+follow-ring+retract
     * per ring in the result (see IsolationGenerator) - the tool traces the
     * ring itself (already closed, first coordinate == last), no separate
     * closing move needed. A single tool per job, unlike drilling's tool
     * table - isolation is normally cut with one bit - so no tool-change
     * pause here. Also returns the CNCJob's own toolpath geometry (see
     * {@link CncJobResult}).
     */
    public static CncJobResult generateIsolationCncJob(IsolationResult result, IsolationGCodeParameters params, double toolDiameter) {
        return generateIsolationCncJob(result, params, toolDiameter, CancellationToken.none());
    }

    public static CncJobResult generateIsolationCncJob(IsolationResult result, IsolationGCodeParameters params,
                                                        double toolDiameter, CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        StringBuilder gcode = new StringBuilder();
        line(gcode, "; Gerado por FlatCAM Next (prototipo) - isolamento");
        line(gcode, "; Unidades do arquivo de origem: %s", result.units());
        line(gcode, result.units().equals("MM") ? "G21" : "G20");
        line(gcode, "G90");
        line(gcode, "G94");
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M3 S%d", params.spindleSpeedRpm());
        }

        double radius = toolDiameter / 2.0;
        List<Geometry> travelShapes = new ArrayList<>();
        List<Geometry> cutShapes = new ArrayList<>();
        double lastX = 0;
        double lastY = 0;

        for (Coordinate[] coordinates : orderedByNearestNeighbor(
                result.geometry(), lastX, lastY, cancellationToken)) {
            cancellationToken.throwIfCancellationRequested();
            addTravel(travelShapes, lastX, lastY, coordinates[0].x, coordinates[0].y, radius);
            cutShapes.add(GEOMETRY_FACTORY.createLineString(coordinates).buffer(radius, STROKE_QUADRANT_SEGMENTS));
            Coordinate last = coordinates[coordinates.length - 1];
            lastX = last.x;
            lastY = last.y;

            line(gcode, "G0 X%s Y%s", fmt(coordinates[0].x), fmt(coordinates[0].y));
            line(gcode, "G1 Z-%s F%s", fmt(params.cutDepth()), fmt(params.feedRate()));
            for (int p = 1; p < coordinates.length; p++) {
                cancellationToken.throwIfCancellationRequested();
                line(gcode, "G1 X%s Y%s F%s", fmt(coordinates[p].x), fmt(coordinates[p].y), fmt(params.feedRate()));
            }
            line(gcode, "G0 Z%s", fmt(params.safeZ()));
        }

        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M5");
        }
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        line(gcode, "M30");
        cancellationToken.throwIfCancellationRequested();
        return new CncJobResult(gcode.toString(), unionOrEmpty(travelShapes), unionOrEmpty(cutShapes));
    }

    /**
     * Generates cutout G-code: one rapid+plunge(es)+follow-path+retract per
     * disjoint path in the result (see CutoutGenerator) - a path is open,
     * not closed, wherever a bridge gap interrupts it, so (unlike
     * isolation's closed rings) the tool simply lifts, stops tracing, and
     * the next path starts fresh; it never needs to jump over a gap
     * mid-path. When params.multiDepth() is on, the ENTIRE path is
     * re-traced at each intermediate Z step down to cutDepth (appTools/
     * ToolCutOut.py's "Multi-Depth" behavior), not just plunged deeper once.
     */
    public static CncJobResult generateCutoutCncJob(CutoutResult result, CutoutGCodeParameters params, double toolDiameter) {
        return generateCutoutCncJob(result, params, toolDiameter, CancellationToken.none());
    }

    public static CncJobResult generateCutoutCncJob(CutoutResult result, CutoutGCodeParameters params,
                                                     double toolDiameter, CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        StringBuilder gcode = new StringBuilder();
        line(gcode, "; Gerado por FlatCAM Next (prototipo) - recorte de placa (cutout)");
        line(gcode, "; Unidades do arquivo de origem: %s", result.units());
        line(gcode, result.units().equals("MM") ? "G21" : "G20");
        line(gcode, "G90");
        line(gcode, "G94");
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M3 S%d", params.spindleSpeedRpm());
        }

        double radius = toolDiameter / 2.0;
        List<Geometry> travelShapes = new ArrayList<>();
        List<Geometry> cutShapes = new ArrayList<>();
        double lastX = 0;
        double lastY = 0;

        List<Double> depths = passDepths(params.cutDepth(), params.multiDepth(), params.depthPerPass());

        for (Coordinate[] coordinates : orderedByNearestNeighbor(
                result.geometry(), lastX, lastY, cancellationToken)) {
            cancellationToken.throwIfCancellationRequested();
            addTravel(travelShapes, lastX, lastY, coordinates[0].x, coordinates[0].y, radius);
            cutShapes.add(GEOMETRY_FACTORY.createLineString(coordinates).buffer(radius, STROKE_QUADRANT_SEGMENTS));
            Coordinate last = coordinates[coordinates.length - 1];
            lastX = last.x;
            lastY = last.y;

            line(gcode, "G0 X%s Y%s", fmt(coordinates[0].x), fmt(coordinates[0].y));
            for (double depth : depths) {
                cancellationToken.throwIfCancellationRequested();
                line(gcode, "G1 Z-%s F%s", fmt(depth), fmt(params.feedRate()));
                for (int p = 1; p < coordinates.length; p++) {
                    cancellationToken.throwIfCancellationRequested();
                    line(gcode, "G1 X%s Y%s F%s", fmt(coordinates[p].x), fmt(coordinates[p].y), fmt(params.feedRate()));
                }
                if (depth != depths.get(depths.size() - 1)) {
                    line(gcode, "G0 Z%s", fmt(params.safeZ()));
                    line(gcode, "G0 X%s Y%s", fmt(coordinates[0].x), fmt(coordinates[0].y));
                }
            }
            line(gcode, "G0 Z%s", fmt(params.safeZ()));
        }

        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M5");
        }
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        line(gcode, "M30");
        cancellationToken.throwIfCancellationRequested();
        return new CncJobResult(gcode.toString(), unionOrEmpty(travelShapes), unionOrEmpty(cutShapes));
    }

    /**
     * Converts the center lines stored by a Geometry object (including an NCC
     * result) to a normal CNC Job. Polygon inputs are traced around every
     * exterior/interior ring; line inputs are followed directly. A
     * single-tool convenience over {@link #generateGeometryCncJob(String, List, GeometryGCodeParameters)}.
     */
    public static CncJobResult generateGeometryCncJob(String units, Geometry geometry,
                                                       GeometryGCodeParameters params, double toolDiameter) {
        return generateGeometryCncJob(units, List.of(new ToolGeometry(toolDiameter, geometry)), params);
    }

    /** Single-tool convenience over {@link #generateGeometryCncJob(String, List, GeometryGCodeParameters, CancellationToken)}. */
    public static CncJobResult generateGeometryCncJob(String units, Geometry geometry,
                                                       GeometryGCodeParameters params, double toolDiameter,
                                                       CancellationToken cancellationToken) {
        return generateGeometryCncJob(units, List.of(new ToolGeometry(toolDiameter, geometry)), params, cancellationToken);
    }

    /**
     * Converts one or more tools' worth of center lines (a multi-tool
     * "multigeo" Geometry object - e.g. an NCC Tool result with Rest
     * Machining) into a single CNC Job with embedded tool-change sections,
     * matching Python's own {@code mtool_gen_cncjob}: ONE G-code file, each
     * tool's section concatenated in the order given, not one CNC Job per
     * tool. Every tool shares the same {@code params} (see
     * GeometryGCodeParameters's own doc for why).
     */
    public static CncJobResult generateGeometryCncJob(String units, List<ToolGeometry> tools,
                                                       GeometryGCodeParameters params) {
        return generateGeometryCncJob(units, tools, params, CancellationToken.none());
    }

    public static CncJobResult generateGeometryCncJob(String units, List<ToolGeometry> tools,
                                                       GeometryGCodeParameters params,
                                                       CancellationToken cancellationToken) {
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("At least one tool geometry is required");
        }
        cancellationToken.throwIfCancellationRequested();

        StringBuilder gcode = new StringBuilder();
        line(gcode, "; Gerado por FlatCAM Next (prototipo) - Geometry");
        line(gcode, "; Unidades do objeto de origem: %s", units);
        line(gcode, "MM".equalsIgnoreCase(units) ? "G21" : "G20");
        line(gcode, "G90");
        line(gcode, "G94");
        line(gcode, "G0 Z%s", fmt(params.safeZ()));

        List<Geometry> travelShapes = new ArrayList<>();
        List<Geometry> cutShapes = new ArrayList<>();
        List<Double> depths = passDepths(params.cutDepth(), params.multiDepth(), params.depthPerPass());
        double lastX = 0;
        double lastY = 0;
        boolean firstTool = true;

        for (ToolGeometry tool : tools) {
            cancellationToken.throwIfCancellationRequested();
            if (tool.geometry() == null || tool.geometry().isEmpty()) {
                continue;
            }
            if (!firstTool) {
                if (params.spindleSpeedRpm() > 0) {
                    line(gcode, "M5");
                }
                if (params.pauseForToolChange()) {
                    line(gcode, "M0 ; troque para a ferramenta (diametro %s %s) e continue",
                            fmt(tool.toolDiameter()), units);
                }
            }
            firstTool = false;
            if (params.spindleSpeedRpm() > 0) {
                line(gcode, "M3 S%d", params.spindleSpeedRpm());
            }

            double radius = tool.toolDiameter() / 2.0;
            for (Coordinate[] coordinates : orderedByNearestNeighbor(
                    tool.geometry(), lastX, lastY, cancellationToken)) {
                cancellationToken.throwIfCancellationRequested();
                if (coordinates.length < 2) {
                    continue;
                }
                addTravel(travelShapes, lastX, lastY, coordinates[0].x, coordinates[0].y, radius);
                cutShapes.add(GEOMETRY_FACTORY.createLineString(coordinates)
                        .buffer(radius, STROKE_QUADRANT_SEGMENTS));
                Coordinate last = coordinates[coordinates.length - 1];
                lastX = last.x;
                lastY = last.y;

                line(gcode, "G0 X%s Y%s", fmt(coordinates[0].x), fmt(coordinates[0].y));
                for (double depth : depths) {
                    cancellationToken.throwIfCancellationRequested();
                    line(gcode, "G1 Z-%s F%s", fmt(depth), fmt(params.feedRate()));
                    for (int p = 1; p < coordinates.length; p++) {
                        cancellationToken.throwIfCancellationRequested();
                        line(gcode, "G1 X%s Y%s F%s", fmt(coordinates[p].x),
                                fmt(coordinates[p].y), fmt(params.feedRate()));
                    }
                    if (depth != depths.get(depths.size() - 1)) {
                        line(gcode, "G0 Z%s", fmt(params.safeZ()));
                        line(gcode, "G0 X%s Y%s", fmt(coordinates[0].x), fmt(coordinates[0].y));
                    }
                }
                line(gcode, "G0 Z%s", fmt(params.safeZ()));
            }
        }

        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M5");
        }
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        line(gcode, "M30");
        cancellationToken.throwIfCancellationRequested();
        return new CncJobResult(gcode.toString(), unionOrEmpty(travelShapes), unionOrEmpty(cutShapes));
    }

    /** [depthPerPass, 2*depthPerPass, ..., cutDepth] when multiDepth is on, else just [cutDepth]. */
    private static List<Double> passDepths(double cutDepth, boolean multiDepth, double depthPerPass) {
        List<Double> depths = new ArrayList<>();
        if (!multiDepth) {
            depths.add(cutDepth);
            return depths;
        }
        double depth = depthPerPass;
        while (depth < cutDepth) {
            depths.add(depth);
            depth += depthPerPass;
        }
        depths.add(cutDepth);
        return depths;
    }

    private static void addTravel(List<Geometry> travelShapes, double fromX, double fromY, double toX, double toY, double radius) {
        if (fromX == toX && fromY == toY) {
            return;
        }
        travelShapes.add(strokeSegment(fromX, fromY, toX, toY, radius));
    }

    private static Geometry strokeSegment(double x1, double y1, double x2, double y2, double radius) {
        return GEOMETRY_FACTORY
                .createLineString(new Coordinate[]{new Coordinate(x1, y1), new Coordinate(x2, y2)})
                .buffer(radius, STROKE_QUADRANT_SEGMENTS);
    }

    private static Geometry circle(double x, double y, double radius) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(x, y)).buffer(radius, STROKE_QUADRANT_SEGMENTS);
    }

    private static Geometry unionOrEmpty(List<Geometry> shapes) {
        if (shapes.isEmpty()) {
            return GEOMETRY_FACTORY.createGeometryCollection();
        }
        return shapes.size() == 1 ? shapes.get(0) : UnaryUnionOp.union(shapes);
    }

    /**
     * A GeometryCollection's part order (e.g. after CutoutGenerator splits one
     * outline into several open arcs at each bridge gap, or LineMerger's own
     * internal edge bookkeeping for isolation's rings) reflects however the
     * union/merge algorithm happened to build it, not perimeter-adjacency -
     * connecting travel moves in that raw order produced long diagonal chords
     * across the board instead of short hops between adjacent arc ends. This
     * greedily visits, from the current position, whichever remaining piece's
     * start OR end is nearest (reversing it if approaching from its end is
     * closer), the standard "traveling salesman, nearest neighbor" heuristic
     * - not optimal, but more than enough to turn corner-to-corner jumps into
     * hops of a few gap-widths.
     */
    static List<Coordinate[]> orderedByNearestNeighbor(Geometry geometry, double startX, double startY) {
        return orderedByNearestNeighbor(geometry, startX, startY, CancellationToken.none());
    }

    private static List<Coordinate[]> orderedByNearestNeighbor(
            Geometry geometry, double startX, double startY, CancellationToken cancellationToken) {
        List<Coordinate[]> remaining = new ArrayList<>();
        collectCoordinatePaths(geometry, remaining, cancellationToken);

        List<Coordinate[]> ordered = new ArrayList<>(remaining.size());
        double currentX = startX;
        double currentY = startY;
        while (!remaining.isEmpty()) {
            cancellationToken.throwIfCancellationRequested();
            int bestIndex = 0;
            boolean bestReversed = false;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                cancellationToken.throwIfCancellationRequested();
                Coordinate[] candidate = remaining.get(i);
                Coordinate first = candidate[0];
                Coordinate last = candidate[candidate.length - 1];
                double distanceToStart = Math.hypot(first.x - currentX, first.y - currentY);
                double distanceToEnd = Math.hypot(last.x - currentX, last.y - currentY);
                if (distanceToStart < bestDistance) {
                    bestDistance = distanceToStart;
                    bestIndex = i;
                    bestReversed = false;
                }
                if (distanceToEnd < bestDistance) {
                    bestDistance = distanceToEnd;
                    bestIndex = i;
                    bestReversed = true;
                }
            }
            Coordinate[] chosen = remaining.remove(bestIndex);
            if (bestReversed) {
                chosen = reversed(chosen);
            }
            ordered.add(chosen);
            Coordinate end = chosen[chosen.length - 1];
            currentX = end.x;
            currentY = end.y;
        }
        return ordered;
    }

    private static Coordinate[] reversed(Coordinate[] coordinates) {
        Coordinate[] result = new Coordinate[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            result[i] = coordinates[coordinates.length - 1 - i];
        }
        return result;
    }

    private static void collectCoordinatePaths(Geometry geometry, List<Coordinate[]> target,
                                               CancellationToken cancellationToken) {
        cancellationToken.throwIfCancellationRequested();
        if (geometry instanceof LineString line) {
            if (!line.isEmpty() && line.getNumPoints() >= 2) {
                target.add(line.getCoordinates());
            }
            return;
        }
        if (geometry instanceof Polygon polygon) {
            target.add(polygon.getExteriorRing().getCoordinates());
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                cancellationToken.throwIfCancellationRequested();
                target.add(polygon.getInteriorRingN(i).getCoordinates());
            }
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            collectCoordinatePaths(geometry.getGeometryN(i), target, cancellationToken);
        }
    }

    private static void line(StringBuilder sb, String format, Object... args) {
        sb.append(String.format(Locale.ROOT, format, args)).append('\n');
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
