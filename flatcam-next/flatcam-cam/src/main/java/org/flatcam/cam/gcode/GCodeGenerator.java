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
import org.flatcam.cam.geometry.ParallelGeometry;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.isolation.IsolationResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Generates GRBL-compatible drilling G-code from a parsed {@link ExcellonImage}:
 * absolute positioning, one rapid+plunge+retract per hole and per slot
 * (optionally with stepped passes and a retract between them),
 * grouped and ordered by tool. Deliberately avoids canned cycles (G81/G82) -
 * GRBL (the firmware on common hobby routers, e.g. the Genmitsu 3030) does
 * not support them - so this is plain G0/G1 rather than the shorter but
 * less portable canned-cycle form.
 *
 * <p>The UI and per-tool settings follow appTools/ToolDrilling.py and
 * camlib.py; this generator deliberately keeps a portable G0/G1 dialect.
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
        SortedSet<Integer> activeIds = new TreeSet<>();
        image.drills().forEach(drill -> activeIds.add(drill.toolId()));
        image.slots().forEach(slot -> activeIds.add(slot.toolId()));
        if (selectedToolIds != null && !selectedToolIds.isEmpty()) activeIds.retainAll(selectedToolIds);
        Map<Integer, DrillGCodeParameters> byTool = new java.util.LinkedHashMap<>();
        activeIds.forEach(id -> byTool.put(id, params));
        return generateDrillCncJob(image, byTool, List.copyOf(activeIds),
                new DrillJobOptions(params.pauseForToolChange(), params.safeZ(), params.safeZ(), null, null));
    }

    /** Common job-level moves; null end X/Y keeps the tool at the final hole. */
    public record DrillJobOptions(boolean pauseForToolChange, double toolChangeZ,
                                  double endMoveZ, Double endMoveX, Double endMoveY, double rapidFeedRate,
                                  ProbeToolChangeParameters probing) {
        public DrillJobOptions(boolean pauseForToolChange, double toolChangeZ,
                               double endMoveZ, Double endMoveX, Double endMoveY, double rapidFeedRate) {
            this(pauseForToolChange, toolChangeZ, endMoveZ, endMoveX, endMoveY, rapidFeedRate, null);
        }
        public DrillJobOptions(boolean pauseForToolChange, double toolChangeZ,
                               double endMoveZ, Double endMoveX, Double endMoveY) {
            this(pauseForToolChange, toolChangeZ, endMoveZ, endMoveX, endMoveY, 0);
        }

        public DrillJobOptions {
            if (!Double.isFinite(rapidFeedRate) || rapidFeedRate < 0)
                throw new IllegalArgumentException("Rapid feed must be zero (automatic) or positive");
            if (!Double.isFinite(toolChangeZ) || toolChangeZ <= 0)
                throw new IllegalArgumentException("Tool change Z must be positive");
            if (!Double.isFinite(endMoveZ) || endMoveZ <= 0)
                throw new IllegalArgumentException("End move Z must be positive");
            if ((endMoveX == null) != (endMoveY == null))
                throw new IllegalArgumentException("End move X/Y must both be set or both be empty");
            if (endMoveX != null && (!Double.isFinite(endMoveX) || !Double.isFinite(endMoveY)))
                throw new IllegalArgumentException("End move X/Y must be finite");
            if (probing != null && Double.compare(probing.toolChangeZ(), toolChangeZ) != 0)
                throw new IllegalArgumentException("Tool change Z deve coincidir com a configuracao da sonda.");
        }
    }

    /** Drilling with per-tool machining settings and explicit tool order. */
    public static CncJobResult generateDrillCncJob(ExcellonImage image,
                                                    Map<Integer, DrillGCodeParameters> settingsByTool,
                                                    List<Integer> orderedToolIds,
                                                    DrillJobOptions options) {
        return generateDrillCncJob(image, settingsByTool, orderedToolIds, options, GCodePreprocessor.FX_PORTABLE);
    }

    public static CncJobResult generateDrillCncJob(ExcellonImage image,
                                                    Map<Integer, DrillGCodeParameters> settingsByTool,
                                                    List<Integer> orderedToolIds,
                                                    DrillJobOptions options,
                                                    GCodePreprocessor preprocessor) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(settingsByTool, "settingsByTool");
        Objects.requireNonNull(orderedToolIds, "orderedToolIds");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(preprocessor, "preprocessor");
        requireMilling(preprocessor);
        Map<Integer, List<ExcellonImage.Drill>> drillsByTool =
                image.drills().stream().collect(Collectors.groupingBy(ExcellonImage.Drill::toolId));
        Map<Integer, List<ExcellonImage.Slot>> slotsByTool =
                image.slots().stream().collect(Collectors.groupingBy(ExcellonImage.Slot::toolId));

        SortedSet<Integer> toolIds = new TreeSet<>();
        toolIds.addAll(drillsByTool.keySet());
        toolIds.addAll(slotsByTool.keySet());
        List<Integer> ordered = orderedToolIds.isEmpty() ? List.copyOf(toolIds)
                : orderedToolIds.stream().distinct().filter(toolIds::contains).toList();
        if (preprocessor.isRoland() && (ordered.size() > 1 || options.pauseForToolChange()))
            throw new IllegalArgumentException("Roland exige uma ferramenta por arquivo e nao suporta troca mecanica.");
        validateProbing(preprocessor, options.pauseForToolChange(), options.probing());
        for (int id : ordered) {
            if (!settingsByTool.containsKey(id))
                throw new IllegalArgumentException("Missing drilling parameters for tool " + id);
            preprocessor.validatePower(settingsByTool.get(id).spindleSpeedRpm());
            if (preprocessor.isRoland() && settingsByTool.get(id).dwell())
                throw new IllegalArgumentException("Roland nao suporta dwell neste perfil; desative a espera.");
            if (preprocessor.requiresProbe()) options.probing().validateTravelZ(settingsByTool.get(id).safeZ());
        }

        StringBuilder gcode = new StringBuilder();
        line(gcode, "%s", preprocessor.comment(preprocessor == GCodePreprocessor.FX_PORTABLE
                ? "Gerado por FlatCAM FX (prototipo) - furacao" : "Gerado por FlatCAM FX - furacao"));
        line(gcode, "%s", preprocessor.comment("Unidades do arquivo de origem: " + image.units()));
        if (preprocessor != GCodePreprocessor.FX_PORTABLE) line(gcode, "%s", preprocessor.header());
        line(gcode, "%s", preprocessor.unitsCode(image.units()));
        line(gcode, "G90");
        if (preprocessor.usesG17()) line(gcode, "G17");
        gcode.append(preprocessor.initialization());
        line(gcode, "G94");
        double initialZ = ordered.isEmpty() ? options.endMoveZ() : settingsByTool.get(ordered.get(0)).safeZ();
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(initialZ));

        List<Geometry> travelShapes = new ArrayList<>();
        Footprints cutShapes = new Footprints();
        double lastX = 0;
        double lastY = 0;
        double lastRadius = 0.1;

        boolean firstTool = true;
        DrillGCodeParameters previous = null;
        for (int toolId : ordered) {
            DrillGCodeParameters params = settingsByTool.get(toolId);
            // Falls back to a thin nominal radius for a tool diameter this Excellon file
            // never declared - only the toolpath preview is affected, not the G-code itself.
            double toolDiameter = image.toolDiameters().getOrDefault(toolId, 0.2);
            double radius = toolDiameter / 2.0;
            lastRadius = radius;

            if (!firstTool) {
                if (previous != null && preprocessor.controlsSpindle(previous.spindleSpeedRpm())) {
                    line(gcode, "%s", preprocessor.spindleOff());
                }
                if (options.pauseForToolChange() || preprocessor.automaticToolSelection()) {
                    if (Double.compare(options.toolChangeZ(), previous.safeZ()) != 0)
                        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(options.toolChangeZ()));
                    Double diameter = image.toolDiameters().get(toolId);
                    if (preprocessor == GCodePreprocessor.FX_PORTABLE) {
                        line(gcode, "M0 ; troque para a ferramenta T%d (diametro %s %s) e continue",
                                toolId, diameter != null ? fmt(diameter) : "?", image.units());
                    } else {
                        line(gcode, "%s", preprocessor.selectTool(toolId));
                        line(gcode, "%s", toolChangeCode(preprocessor, options.probing(), toolId,
                                diameter != null ? diameter : 0, image.units(),
                                preprocessor.requiresProbe() ? params.safeZ() : options.toolChangeZ(), params.feedRate()));
                    }
                    if (!preprocessor.requiresProbe() && Double.compare(options.toolChangeZ(), params.safeZ()) != 0)
                        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
                } else if (Double.compare(previous.safeZ(), params.safeZ()) != 0) {
                    line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
                }
            } else if (preprocessor.emitsToolNumber()) {
                if (options.pauseForToolChange()
                        && Double.compare(options.toolChangeZ(), params.safeZ()) != 0)
                    line(gcode, "%s Z%s", preprocessor.rapid(), fmt(options.toolChangeZ()));
                line(gcode, "%s", preprocessor.selectTool(toolId));
                if (options.pauseForToolChange() || preprocessor.automaticToolSelection()) {
                    line(gcode, "%s", toolChangeCode(preprocessor, options.probing(), toolId, toolDiameter, image.units(),
                            preprocessor.requiresProbe() ? params.safeZ() : options.toolChangeZ(), params.feedRate()));
                    if (!preprocessor.requiresProbe() && Double.compare(options.toolChangeZ(), params.safeZ()) != 0)
                        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
                }
            }
            firstTool = false;
            previous = params;
            // Lets GCodeToolpathParser redraw this tool's holes at their real size after a reload/edit.
            line(gcode, "%s", preprocessor.comment(GCodeToolpathParser.toolMarker(toolId, toolDiameter)));

            if (preprocessor.controlsSpindle(params.spindleSpeedRpm())) {
                line(gcode, "%s S%d", preprocessor.spindleOn(), params.spindleSpeedRpm());
            }
            if (params.dwell()) {
                line(gcode, "G4 P%s", fmt(params.dwellSeconds()));
            }

            List<Double> depths = passDepths(params.effectiveDepth(),
                    params.multiDepth(), params.depthPerPass());

            for (ExcellonImage.Drill drill : drillsByTool.getOrDefault(toolId, List.of())) {
                // A drill doesn't move laterally while cutting - camlib.py's own gcode_parse()
                // fabricates a circle at the hole to represent the "cut" shape too.
                if (!preprocessor.requiresProbe()) {
                    addTravel(travelShapes, lastX, lastY, drill.x(), drill.y(), radius);
                    cutShapes.addBuffered(circle(drill.x(), drill.y(), radius));
                }
                lastX = drill.x();
                lastY = drill.y();

                line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(drill.x()), fmt(drill.y()));
                for (double depth : depths) {
                    line(gcode, "%s Z-%s F%s", preprocessor.linear(), fmt(depth), fmt(params.feedRate()));
                    line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
                }
            }
            for (ExcellonImage.Slot slot : slotsByTool.getOrDefault(toolId, List.of())) {
                if (!preprocessor.requiresProbe()) {
                    addTravel(travelShapes, lastX, lastY, slot.x1(), slot.y1(), radius);
                    cutShapes.addBuffered(strokeSegment(slot.x1(), slot.y1(), slot.x2(), slot.y2(), radius));
                }
                lastX = slot.x2();
                lastY = slot.y2();

                line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(slot.x1()), fmt(slot.y1()));
                for (int pass = 0; pass < depths.size(); pass++) {
                    if (pass > 0) line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(slot.x1()), fmt(slot.y1()));
                    line(gcode, "%s Z-%s F%s", preprocessor.linear(), fmt(depths.get(pass)), fmt(params.feedRate()));
                    line(gcode, "%s X%s Y%s F%s", preprocessor.linear(), fmt(slot.x2()), fmt(slot.y2()), fmt(params.feedRate()));
                    line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
                }
            }
        }
        if (previous != null && preprocessor.controlsSpindle(previous.spindleSpeedRpm())) {
            line(gcode, "%s", preprocessor.spindleOff());
        }
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(options.endMoveZ()));
        if (options.endMoveX() != null) {
            if (!preprocessor.requiresProbe())
                addTravel(travelShapes, lastX, lastY, options.endMoveX(), options.endMoveY(), lastRadius);
            line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(options.endMoveX()), fmt(options.endMoveY()));
        }
        String code = preprocessor.finish(gcode, options.rapidFeedRate(), image.units());
        if (preprocessor.requiresProbe()) return withoutProbePreview(code);
        return new CncJobResult(code,
                travelFootprint(travelShapes), cutShapes.result());
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
        return generateIsolationCncJob(result, params, toolDiameter, cancellationToken,
                GCodePreprocessor.FX_PORTABLE);
    }

    public static CncJobResult generateIsolationCncJob(IsolationResult result, IsolationGCodeParameters params,
                                                        double toolDiameter, CancellationToken cancellationToken,
                                                        GCodePreprocessor preprocessor) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(preprocessor, "preprocessor");
        requireMilling(preprocessor);
        preprocessor.validatePower(params.spindleSpeedRpm());
        rejectDirectProbing(preprocessor);
        cancellationToken.throwIfCancellationRequested();
        StringBuilder gcode = new StringBuilder();
        line(gcode, "%s", preprocessor.comment(preprocessor == GCodePreprocessor.FX_PORTABLE
                ? "Gerado por FlatCAM FX (prototipo) - isolamento" : "Gerado por FlatCAM FX - isolamento"));
        line(gcode, "%s", preprocessor.comment("Unidades do arquivo de origem: " + result.units()));
        if (preprocessor != GCodePreprocessor.FX_PORTABLE) line(gcode, "%s", preprocessor.header());
        line(gcode, "%s", preprocessor.comment(GCodeToolpathParser.millMarker(toolDiameter)));
        line(gcode, "%s", preprocessor.unitsCode(result.units()));
        line(gcode, "G90");
        if (preprocessor.usesG17()) line(gcode, "G17");
        gcode.append(preprocessor.initialization());
        line(gcode, "G94");
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
        if (preprocessor.controlsSpindle(params.spindleSpeedRpm())) {
            line(gcode, "%s S%d", preprocessor.spindleOn(), params.spindleSpeedRpm());
        }

        double radius = toolDiameter / 2.0;
        List<Geometry> travelShapes = new ArrayList<>();
        Footprints cutShapes = new Footprints();
        double lastX = 0;
        double lastY = 0;

        for (Coordinate[] coordinates : orderedByNearestNeighbor(
                result.geometry(), lastX, lastY, cancellationToken)) {
            cancellationToken.throwIfCancellationRequested();
            addTravel(travelShapes, lastX, lastY, coordinates[0].x, coordinates[0].y, radius);
            cutShapes.add(GEOMETRY_FACTORY.createLineString(coordinates), radius);
            Coordinate last = coordinates[coordinates.length - 1];
            lastX = last.x;
            lastY = last.y;

            line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(coordinates[0].x), fmt(coordinates[0].y));
            line(gcode, "%s Z-%s F%s", preprocessor.linear(), fmt(params.cutDepth()), fmt(params.feedRate()));
            for (int p = 1; p < coordinates.length; p++) {
                cancellationToken.throwIfCancellationRequested();
                line(gcode, "%s X%s Y%s F%s", preprocessor.linear(), fmt(coordinates[p].x),
                        fmt(coordinates[p].y), fmt(params.feedRate()));
            }
            line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
        }

        if (preprocessor.controlsSpindle(params.spindleSpeedRpm())) {
            line(gcode, "%s", preprocessor.spindleOff());
        }
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
        cancellationToken.throwIfCancellationRequested();
        return new CncJobResult(preprocessor.finish(gcode, 0, result.units()),
                travelFootprint(travelShapes), cutShapes.result());
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
        return generateCutoutCncJob(result, params, toolDiameter, cancellationToken,
                GCodePreprocessor.FX_PORTABLE);
    }

    public static CncJobResult generateCutoutCncJob(CutoutResult result, CutoutGCodeParameters params,
                                                     double toolDiameter, CancellationToken cancellationToken,
                                                     GCodePreprocessor preprocessor) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(preprocessor, "preprocessor");
        requireMilling(preprocessor);
        preprocessor.validatePower(params.spindleSpeedRpm());
        rejectDirectProbing(preprocessor);
        cancellationToken.throwIfCancellationRequested();
        StringBuilder gcode = new StringBuilder();
        line(gcode, "%s", preprocessor.comment(preprocessor == GCodePreprocessor.FX_PORTABLE
                ? "Gerado por FlatCAM FX (prototipo) - recorte de placa (cutout)"
                : "Gerado por FlatCAM FX - recorte de placa - cutout"));
        line(gcode, "%s", preprocessor.comment("Unidades do arquivo de origem: " + result.units()));
        if (preprocessor != GCodePreprocessor.FX_PORTABLE) line(gcode, "%s", preprocessor.header());
        line(gcode, "%s", preprocessor.comment(GCodeToolpathParser.millMarker(toolDiameter)));
        line(gcode, "%s", preprocessor.unitsCode(result.units()));
        line(gcode, "G90");
        if (preprocessor.usesG17()) line(gcode, "G17");
        gcode.append(preprocessor.initialization());
        line(gcode, "G94");
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
        if (preprocessor.controlsSpindle(params.spindleSpeedRpm())) {
            line(gcode, "%s S%d", preprocessor.spindleOn(), params.spindleSpeedRpm());
        }

        double radius = toolDiameter / 2.0;
        List<Geometry> travelShapes = new ArrayList<>();
        Footprints cutShapes = new Footprints();
        double lastX = 0;
        double lastY = 0;

        List<Double> depths = passDepths(params.cutDepth(), params.multiDepth(), params.depthPerPass());

        for (Coordinate[] coordinates : orderedByNearestNeighbor(
                result.geometry(), lastX, lastY, cancellationToken)) {
            cancellationToken.throwIfCancellationRequested();
            addTravel(travelShapes, lastX, lastY, coordinates[0].x, coordinates[0].y, radius);
            cutShapes.add(GEOMETRY_FACTORY.createLineString(coordinates), radius);
            Coordinate last = coordinates[coordinates.length - 1];
            lastX = last.x;
            lastY = last.y;

            line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(coordinates[0].x), fmt(coordinates[0].y));
            for (double depth : depths) {
                cancellationToken.throwIfCancellationRequested();
                line(gcode, "%s Z-%s F%s", preprocessor.linear(), fmt(depth), fmt(params.feedRate()));
                for (int p = 1; p < coordinates.length; p++) {
                    cancellationToken.throwIfCancellationRequested();
                    line(gcode, "%s X%s Y%s F%s", preprocessor.linear(), fmt(coordinates[p].x),
                            fmt(coordinates[p].y), fmt(params.feedRate()));
                }
                if (depth != depths.get(depths.size() - 1)) {
                    line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
                    line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(coordinates[0].x), fmt(coordinates[0].y));
                }
            }
            line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
        }

        if (preprocessor.controlsSpindle(params.spindleSpeedRpm())) {
            line(gcode, "%s", preprocessor.spindleOff());
        }
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));
        cancellationToken.throwIfCancellationRequested();
        return new CncJobResult(preprocessor.finish(gcode, 0, result.units()),
                travelFootprint(travelShapes), cutShapes.result());
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
        return generateGeometryCncJob(units, tools, params, Map.of(), cancellationToken);
    }

    /** V-tool settings are keyed by the tool's zero-based position in {@code tools}. */
    public static CncJobResult generateGeometryCncJob(String units, List<ToolGeometry> tools,
                                                       GeometryGCodeParameters params,
                                                       Map<Integer, VTipSettings> vTools,
                                                       CancellationToken cancellationToken) {
        return generateGeometryCncJob(units, tools, params, vTools, cancellationToken,
                GCodePreprocessor.FX_PORTABLE);
    }

    public static CncJobResult generateGeometryCncJob(String units, List<ToolGeometry> tools,
                                                       GeometryGCodeParameters params,
                                                       Map<Integer, VTipSettings> vTools,
                                                       CancellationToken cancellationToken,
                                                       GCodePreprocessor preprocessor) {
        return generateGeometryCncJob(units, tools, params, vTools, Map.of(), cancellationToken, preprocessor);
    }

    /** Per-tool machining parameters indexed by the original Geometry tool order; job-level change/rapid settings stay common. */
    public static CncJobResult generateGeometryCncJob(String units, List<ToolGeometry> tools,
            GeometryGCodeParameters params, Map<Integer, VTipSettings> vTools,
            Map<Integer, GeometryGCodeParameters> parametersByTool,
            CancellationToken cancellationToken, GCodePreprocessor preprocessor) {
        Objects.requireNonNull(parametersByTool, "parametersByTool");
        if (parametersByTool.keySet().stream().anyMatch(id -> id < 0 || id >= tools.size()))
            throw new IllegalArgumentException("Invalid tool parameter index");
        for (var p : parametersByTool.values()) {
            preprocessor.validatePower(p.spindleSpeedRpm());
            preprocessor.validateFeedRates(p.feedRate(), params.rapidFeedRate());
        }
        if ((preprocessor.isLaser() || preprocessor.isPlotter() || preprocessor.isRoland() || preprocessor.requiresProbe())
                && !parametersByTool.isEmpty())
            throw new IllegalArgumentException("Individual machining parameters currently require a milling G-code profile without probing.");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(vTools, "vTools");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(preprocessor, "preprocessor");
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("At least one tool geometry is required");
        }
        preprocessor.validatePower(params.spindleSpeedRpm());
        if (preprocessor.isPlotter()) {
            return generateHpglCncJob(units, tools, params, cancellationToken);
        }
        if (preprocessor.isLaser()) {
            return generateLaserCncJob(units, tools, params, cancellationToken, preprocessor);
        }
        if (preprocessor.isRoland() && (params.pauseForToolChange()
                || tools.stream().filter(tool -> !tool.geometry().isEmpty()).count() > 1))
            throw new IllegalArgumentException("Roland exige uma ferramenta por arquivo e nao suporta troca mecanica.");
        validateProbing(preprocessor, params.pauseForToolChange(), params.probing());
        List<List<Double>> depthsByTool = new ArrayList<>(tools.size());
        for (int i = 0; i < tools.size(); i++) {
            ToolGeometry tool = tools.get(i);
            GeometryGCodeParameters machining = parametersByTool.getOrDefault(i, params);
            double depth = machining.cutDepth();
            if (tool.toolProfile() == ToolProfile.V && !tool.geometry().isEmpty()) {
                VTipSettings settings = vTools.get(i);
                if (settings == null)
                    throw new IllegalArgumentException("Ferramenta V " + (i + 1)
                            + " exige V-Tip Dia e V-Tip Angle.");
                depth = settings.cutDepth(tool.toolDiameter());
            }
            depthsByTool.add(passDepths(depth, machining.multiDepth(), machining.depthPerPass()));
        }
        cancellationToken.throwIfCancellationRequested();

        StringBuilder gcode = new StringBuilder();
        line(gcode, "%s", preprocessor.comment(preprocessor == GCodePreprocessor.FX_PORTABLE
                ? "Gerado por FlatCAM FX (prototipo) - Geometry" : "Gerado por FlatCAM FX - Geometry"));
        line(gcode, "%s", preprocessor.comment("Unidades do objeto de origem: " + units));
        if (preprocessor != GCodePreprocessor.FX_PORTABLE) line(gcode, "%s", preprocessor.header());
        line(gcode, "%s", preprocessor.unitsCode(units));
        line(gcode, "G90");
        if (preprocessor.usesG17()) line(gcode, "G17");
        gcode.append(preprocessor.initialization());
        line(gcode, "G94");
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(params.safeZ()));

        List<Geometry> travelShapes = new ArrayList<>();
        Footprints cutShapes = new Footprints();
        double lastX = 0;
        double lastY = 0;
        boolean firstTool = true;
        int previousSpindle = 0;
        double clearance = Math.max(params.safeZ(), parametersByTool.values().stream()
                .mapToDouble(GeometryGCodeParameters::safeZ).max().orElse(params.safeZ()));

        for (int toolIndex = 0; toolIndex < tools.size(); toolIndex++) {
            ToolGeometry tool = tools.get(toolIndex);
            cancellationToken.throwIfCancellationRequested();
            if (tool.geometry() == null || tool.geometry().isEmpty()) {
                continue;
            }
            GeometryGCodeParameters machining = parametersByTool.getOrDefault(toolIndex, params);
            List<Double> depths = depthsByTool.get(toolIndex);
            line(gcode, "%s Z%s", preprocessor.rapid(), fmt(clearance));
            if (!firstTool) {
                if (preprocessor.controlsSpindle(previousSpindle)) {
                    line(gcode, "%s", preprocessor.spindleOff());
                }
                if (params.pauseForToolChange() || preprocessor.automaticToolSelection()) {
                    if (preprocessor == GCodePreprocessor.FX_PORTABLE) {
                        line(gcode, "M0 ; troque para a ferramenta (diametro %s %s) e continue",
                                fmt(tool.toolDiameter()), units);
                    } else {
                        line(gcode, "%s", preprocessor.selectTool(toolIndex + 1));
                        line(gcode, "%s", toolChangeCode(preprocessor, params.probing(), toolIndex + 1,
                                tool.toolDiameter(), units, params.safeZ(), params.feedRate()));
                    }
                }
            } else if (preprocessor.emitsToolNumber()) {
                line(gcode, "%s", preprocessor.selectTool(toolIndex + 1));
                if (params.pauseForToolChange() || preprocessor.automaticToolSelection()) {
                    line(gcode, "%s", toolChangeCode(preprocessor, params.probing(), toolIndex + 1,
                            tool.toolDiameter(), units, params.safeZ(), params.feedRate()));
                }
            }
            firstTool = false;
            line(gcode, "%s", preprocessor.comment(GCodeToolpathParser.millMarker(tool.toolDiameter())));
            if (tool.toolProfile() == ToolProfile.V) {
                VTipSettings settings = vTools.get(toolIndex);
                line(gcode, "%s", preprocessor.comment(String.format(Locale.ROOT,
                        "V tool %d: dia %s, tip %s, angle %s deg, cut Z -%s",
                        toolIndex + 1, fmt(tool.toolDiameter()), fmt(settings.tipDiameter()),
                        fmt(settings.angleDegrees()), fmt(depths.get(depths.size() - 1)))));
            }
            previousSpindle = machining.spindleSpeedRpm();
            if (preprocessor.controlsSpindle(previousSpindle)) {
                line(gcode, "%s S%d", preprocessor.spindleOn(), previousSpindle);
                if (machining.dwell()) line(gcode, "G4 P%s", fmt(machining.dwellSeconds()));
            }

            double radius = tool.toolDiameter() / 2.0;
            for (Coordinate[] original : orderedByNearestNeighbor(
                    tool.geometry(), lastX, lastY, cancellationToken)) {
                cancellationToken.throwIfCancellationRequested();
                Coordinate[] coordinates = machining.extraCut() ? extraCut(original, machining.extraCutLength()) : original;
                if (coordinates.length == 0) {
                    continue;
                }
                if (!preprocessor.requiresProbe()) {
                    addTravel(travelShapes, lastX, lastY, coordinates[0].x, coordinates[0].y, radius);
                    cutShapes.add(coordinates.length == 1
                            ? GEOMETRY_FACTORY.createPoint(coordinates[0])
                            : GEOMETRY_FACTORY.createLineString(coordinates), radius);
                }
                Coordinate last = coordinates[coordinates.length - 1];
                lastX = last.x;
                lastY = last.y;

                line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(coordinates[0].x), fmt(coordinates[0].y));
                for (double depth : depths) {
                    cancellationToken.throwIfCancellationRequested();
                    line(gcode, "%s Z-%s F%s", preprocessor.linear(), fmt(depth), fmt(machining.feedRateZ()));
                    for (int p = 1; p < coordinates.length; p++) {
                        cancellationToken.throwIfCancellationRequested();
                        line(gcode, "%s X%s Y%s F%s", preprocessor.linear(), fmt(coordinates[p].x),
                                fmt(coordinates[p].y), fmt(machining.feedRate()));
                    }
                    if (depth != depths.get(depths.size() - 1)) {
                        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(machining.safeZ()));
                        line(gcode, "%s X%s Y%s", preprocessor.rapid(), fmt(coordinates[0].x), fmt(coordinates[0].y));
                    }
                }
                line(gcode, "%s Z%s", preprocessor.rapid(), fmt(machining.safeZ()));
            }
        }

        if (preprocessor.controlsSpindle(previousSpindle)) {
            line(gcode, "%s", preprocessor.spindleOff());
        }
        line(gcode, "%s Z%s", preprocessor.rapid(), fmt(clearance));
        cancellationToken.throwIfCancellationRequested();
        String code = preprocessor.finish(gcode, params.rapidFeedRate(), units, cancellationToken);
        if (preprocessor.requiresProbe()) return withoutProbePreview(code);
        return new CncJobResult(code,
                travelFootprint(travelShapes), cutShapes.result());
    }

    /** Append up to one additional circuit to a closed path; never extend open paths. */
    private static Coordinate[] extraCut(Coordinate[] path, double length) {
        if (length == 0 || path.length < 3 || !path[0].equals2D(path[path.length - 1])) return path;
        List<Coordinate> result = new ArrayList<>(List.of(path));
        double remaining = length;
        for (int i = 1; i < path.length && remaining > 0; i++) {
            double segment = path[i - 1].distance(path[i]);
            if (segment == 0) continue;
            double t = Math.min(1, remaining / segment);
            result.add(new Coordinate(path[i - 1].x + t * (path[i].x - path[i - 1].x),
                    path[i - 1].y + t * (path[i].y - path[i - 1].y)));
            remaining -= segment;
        }
        if (remaining > 1e-9) throw new IllegalArgumentException("Extra Cut Length exceeds the closed path perimeter.");
        return result.toArray(Coordinate[]::new);
    }

    private static void requireMilling(GCodePreprocessor profile) {
        if (profile.isLaser() || profile.isPlotter()) throw new IllegalArgumentException(
                "Perfil laser/plotter exige Geometry -> CNC Job; nao use para furacao ou geracao direta de fresagem.");
    }

    private static void rejectDirectProbing(GCodePreprocessor profile) {
        if (profile.requiresProbe()) throw new IllegalArgumentException(
                "Sondagem exige parametros explicitos: crie Geometry e use Geometry -> CNC Job.");
    }

    private static void validateProbing(GCodePreprocessor profile, boolean toolChange, ProbeToolChangeParameters probe) {
        if (profile.requiresProbe() && (!toolChange || probe == null))
            throw new IllegalArgumentException("Mach3 com sonda exige troca de ferramenta ativada e parametros explicitos de probing.");
    }

    private static String toolChangeCode(GCodePreprocessor profile, ProbeToolChangeParameters probe,
                                         int tool, double diameter, String units, double returnZ, double feed) {
        return profile.requiresProbe() ? probe.cycle(tool, diameter, units, returnZ)
                : profile.pauseForTool(tool, diameter, units, returnZ, feed);
    }

    private static CncJobResult withoutProbePreview(String code) {
        // Never draw the target G31 depth as though it were the real contact point, or assume a G92 offset.
        return new CncJobResult(code, GEOMETRY_FACTORY.createGeometryCollection(), GEOMETRY_FACTORY.createGeometryCollection());
    }

    private static CncJobResult generateHpglCncJob(String units, List<ToolGeometry> tools,
                                                  GeometryGCodeParameters params, CancellationToken cancellation) {
        if (!"MM".equalsIgnoreCase(units) && !"IN".equalsIgnoreCase(units))
            throw new IllegalArgumentException("Unidades HPGL devem ser MM ou IN.");
        if (params.multiDepth() || params.pauseForToolChange())
            throw new IllegalArgumentException("HPGL nao usa Multi-Depth nem troca mecanica; as canetas usam SP.");
        StringBuilder program = new StringBuilder("IN;\nCO \"Preprocessor: HPGL\";\nCO \"FCFX HPGL UNITS ")
                .append(units.toUpperCase(Locale.ROOT)).append("\";\nPU;\nPA;\n");
        List<Geometry> travels = new ArrayList<>();
        Footprints cuts = new Footprints();
        double scale = "IN".equalsIgnoreCase(units) ? 1016 : 40;
        double lastX = 0, lastY = 0;
        for (int index = 0; index < tools.size(); index++) {
            cancellation.throwIfCancellationRequested();
            ToolGeometry tool = tools.get(index);
            if (tool.geometry().isEmpty()) continue;
            program.append("CO \"FCFX PEN P").append(index + 1).append(" D")
                    .append(java.math.BigDecimal.valueOf(tool.toolDiameter()).toPlainString())
                    .append("\";\nSP").append(index + 1).append(";\n");
            for (Coordinate[] path : orderedByNearestNeighbor(tool.geometry(), lastX, lastY, cancellation)) {
                if (path.length < 2) throw new IllegalArgumentException("HPGL requer caminhos XY; pontos isolados nao sao suportados.");
                boolean distinct = false;
                int x = 0, y = 0, previousX = 0, previousY = 0;
                List<Coordinate> quantized = new ArrayList<>();
                for (int p = 0; p < path.length; p++) {
                    cancellation.throwIfCancellationRequested();
                    x = HpglProgramCodec.coordinate(path[p].x, units);
                    y = HpglProgramCodec.coordinate(path[p].y, units);
                    if (p == 0) {
                        program.append("PU;\nPA").append(x).append(',').append(y).append(";\nPD;\n");
                        quantized.add(new Coordinate(x / scale, y / scale));
                    } else if (x != previousX || y != previousY) {
                        program.append("PA").append(x).append(',').append(y).append(";\n");
                        quantized.add(new Coordinate(x / scale, y / scale));
                        distinct = true;
                    }
                    previousX = x;
                    previousY = y;
                }
                if (!distinct) throw new IllegalArgumentException("Caminho menor que a resolucao HPGL de 0,025 mm.");
                program.append("PU;\n");
                Coordinate first = quantized.getFirst();
                addTravel(travels, lastX, lastY, first.x, first.y, tool.toolDiameter() / 2);
                cuts.add(GEOMETRY_FACTORY.createLineString(quantized.toArray(Coordinate[]::new)), tool.toolDiameter() / 2);
                lastX = x / scale;
                lastY = y / scale;
            }
        }
        program.append("PU;\nSP0;\n");
        // Use the quantized paths actually sent to the plotter. Generation is independent of the editor's preview limit.
        cancellation.throwIfCancellationRequested();
        return new CncJobResult(program.toString(), travelFootprint(travels), cuts.result());
    }

    /** Laser emission is controlled per path, never by a negative Z plunge. */
    private static CncJobResult generateLaserCncJob(String units, List<ToolGeometry> tools,
                                                   GeometryGCodeParameters params, CancellationToken cancellation,
                                                   GCodePreprocessor profile) {
        if (params.multiDepth() || params.pauseForToolChange()) throw new IllegalArgumentException(
                "Laser nao usa Multi-Depth nem troca mecanica de ferramenta. Desative essas opcoes.");
        StringBuilder gcode = new StringBuilder();
        line(gcode, "%s", profile.comment("Gerado por FlatCAM FX - laser"));
        line(gcode, "%s", profile.header());
        line(gcode, "%s", profile.comment("FCFX LASER"));
        line(gcode, "MM".equalsIgnoreCase(units) ? "G21" : "G20");
        line(gcode, "G90");
        if (profile.usesG17()) line(gcode, "G17");
        line(gcode, "G94");
        // Power off even before the first positioning move; never inherit controller state.
        line(gcode, "%s", profile.spindleOff());
        if (profile == GCodePreprocessor.MARLIN_LASER_FAN_PIN
                || profile == GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN) {
            line(gcode, "%s Z%s", profile.rapid(), fmt(params.safeZ()));
        }
        List<Geometry> travels = new ArrayList<>();
        Footprints cuts = new Footprints();
        double lastX = 0, lastY = 0;
        for (ToolGeometry tool : tools) {
            cancellation.throwIfCancellationRequested();
            if (tool.geometry().isEmpty()) continue;
            line(gcode, "%s", profile.comment(GCodeToolpathParser.millMarker(tool.toolDiameter())));
            if (profile == GCodePreprocessor.Z_LASER) {
                line(gcode, "%s Z%s", profile.rapid(), fmt(params.safeZ()));
            }
            for (Coordinate[] path : orderedByNearestNeighbor(tool.geometry(), lastX, lastY, cancellation)) {
                cancellation.throwIfCancellationRequested();
                if (path.length < 2) throw new IllegalArgumentException(
                        "Laser requer caminhos XY; pontos isolados exigem tempo de exposicao ainda nao suportado.");
                addTravel(travels, lastX, lastY, path[0].x, path[0].y, tool.toolDiameter() / 2);
                line(gcode, "%s X%s Y%s", profile.rapid(), fmt(path[0].x), fmt(path[0].y));
                line(gcode, "%s S%d", profile.spindleOn(), params.spindleSpeedRpm());
                for (int p = 1; p < path.length; p++) {
                    cancellation.throwIfCancellationRequested();
                    line(gcode, "%s X%s Y%s F%s", profile.linear(), fmt(path[p].x), fmt(path[p].y),
                            fmt(params.feedRate()));
                }
                line(gcode, "%s", profile.spindleOff());
                cuts.add(GEOMETRY_FACTORY.createLineString(path), tool.toolDiameter() / 2);
                lastX = path[path.length - 1].x;
                lastY = path[path.length - 1].y;
            }
        }
        line(gcode, "%s", profile.spindleOff());
        // Matches the Python profiles' end Z, after the laser has been switched off.
        line(gcode, "%s Z%s", profile.rapid(), fmt(params.safeZ()));
        cancellation.throwIfCancellationRequested();
        return new CncJobResult(profile.finish(gcode, params.rapidFeedRate(), units),
                travelFootprint(travels), cuts.result());
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
            if (depths.size() >= 10_000)
                throw new IllegalArgumentException("Depth per pass would require over 10000 passes");
            depths.add(depth);
            double next = depth + depthPerPass;
            if (next <= depth)
                throw new IllegalArgumentException("Depth per pass is too small to advance Z");
            depth = next;
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

    /**
     * The cut footprints of a job. The moves are collected as raw lines/points and buffered on all cores
     * when the job ends (one JTS buffer per move is the bulk of the serial work otherwise), then unioned.
     */
    private static final class Footprints {
        private final List<Geometry> raw = new ArrayList<>();
        private final List<Double> radii = new ArrayList<>();
        private final List<Geometry> buffered = new ArrayList<>();

        void add(Geometry linesOrPoint, double radius) {
            raw.add(linesOrPoint);
            radii.add(radius);
        }

        void addBuffered(Geometry footprint) {
            buffered.add(footprint);
        }

        Geometry result() {
            List<Geometry> all = new ArrayList<>(buffered);
            if (!raw.isEmpty()) {
                all.addAll(java.util.stream.IntStream.range(0, raw.size()).parallel()
                        .mapToObj(i -> raw.get(i).buffer(radii.get(i), STROKE_QUADRANT_SEGMENTS)).toList());
            }
            return unionOrEmpty(all);
        }
    }

    /** Above this many rapids the footprint is not unioned (see {@link #travelFootprint}). */
    private static final int MAX_UNIONED_TRAVELS = 2000;

    /**
     * The footprint of the rapids. They criss-cross the whole job, so unioning them is one huge,
     * single-threaded overlay (about half of a 4x4 panel's generation time) that only tidies overlaps
     * of a translucent preview. Big jobs keep the individual footprints in a collection instead;
     * FX draws those jobs from their stroked centerlines anyway.
     */
    private static Geometry travelFootprint(List<Geometry> shapes) {
        if (shapes.size() > MAX_UNIONED_TRAVELS) {
            return GEOMETRY_FACTORY.createGeometryCollection(shapes.toArray(new Geometry[0]));
        }
        return unionOrEmpty(shapes);
    }

    private static Geometry unionOrEmpty(List<Geometry> shapes) {
        if (shapes.isEmpty()) {
            return GEOMETRY_FACTORY.createGeometryCollection();
        }
        return shapes.size() == 1 ? shapes.get(0) : ParallelGeometry.union(shapes);
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
        if (geometry instanceof Point point) {
            if (!point.isEmpty()) {
                target.add(new Coordinate[]{point.getCoordinate()});
            }
            return;
        }
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
        int argument = 0;
        int start = sb.length();
        int length = format.length();
        for (int i = 0; i < length; i++) {
            char c = format.charAt(i);
            if (c != '%') {
                sb.append(c);
                continue;
            }
            char kind = i + 1 < length ? format.charAt(i + 1) : 0;
            if ((kind == 's' || kind == 'd') && argument < args.length) {
                sb.append(args[argument++]);
                i++;
            } else {
                // Anything fancier (widths, %%, ...): let Formatter handle the whole line.
                sb.setLength(start);
                sb.append(String.format(Locale.ROOT, format, args)).append('\n');
                return;
            }
        }
        sb.append('\n');
    }

    /** Same text as {@code String.format(Locale.ROOT, "%.4f", value)}, without the Formatter's per-call cost. */
    static String fmt(double value) {
        if (!Double.isFinite(value)) {
            return String.format(Locale.ROOT, "%.4f", value);
        }
        String text = java.math.BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
        // BigDecimal has no negative zero; Formatter prints "-0.0000" for values that round to zero from below.
        if (Double.doubleToRawLongBits(value) < 0 && text.charAt(0) != '-') {
            return "-" + text;
        }
        return text;
    }
}
