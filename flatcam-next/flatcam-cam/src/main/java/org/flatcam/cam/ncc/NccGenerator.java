package org.flatcam.cam.ncc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Ports the geometry-producing portion of {@code ToolNCC.py} and
 * {@code camlib.Geometry.clear_polygon*}. The source Gerber's convex hull,
 * expanded by the requested margin, is the boundary; subtracting copper
 * produces the polygons to clear. Toolpaths are cutter-center lines, so every
 * strategy works on the empty area inset by half the tool diameter.
 *
 * <p><b>Multi-tool / Rest Machining</b> (ToolNCC.py's {@code gen_clear_area}
 * vs {@code gen_clear_area_rest}): without Rest Machining, every configured
 * tool independently clears the SAME full non-copper area - each tool's
 * result is unrelated to the others', just processed in
 * {@link NccParameters#order()}. With Rest Machining on, tools are always
 * processed largest-first; each tool clears only what remains of the area
 * after subtracting the actual swept footprint (its cleared center-line
 * paths, buffered back out by its own radius) of every larger tool that ran
 * before it. A polygon a tool can't clear at all (too small an opening for
 * that tool) is simply left untouched - since nothing gets subtracted for it,
 * it naturally stays available for the next, smaller tool, matching Python's
 * separate "rest_geo" bookkeeping without needing to reproduce it explicitly.
 */
public final class NccGenerator {

    private static final int QUADRANT_SEGMENTS = 64;

    /**
     * Paint's independent conservative footprint is shrunk below the true
     * radius. GUI NCC Rest instead uses its original expansion/repair policy;
     * that is not the legacy Tcl {@code tool_used = tool - 1e-12} policy.
     */
    private static final double FOOTPRINT_SHRINK = 1e-6;

    private NccGenerator() {
    }

    public static NccResult generate(String units, Geometry copper, NccParameters params) {
        return generate(units, copper, params, CancellationToken.none(), ProgressCallback.none());
    }

    public static NccResult generate(String units, Geometry copper, NccParameters params,
                                     CancellationToken cancellation, ProgressCallback progress) {
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(progress, "progress");
        cancellation.throwIfCancellationRequested();
        GeometryFactory factory = copper != null ? copper.getFactory() : new GeometryFactory();
        if (copper == null || copper.isEmpty()) {
            Geometry empty = factory.createGeometryCollection();
            return new NccResult(units, empty, empty, List.of());
        }
        if (copper.getArea() <= 0) {
            throw new IllegalArgumentException("A origem do NCC precisa ter area preenchida; "
                    + "converta o contorno para area.");
        }

        progress.report(0.02);
        // Python rejects outline-only sources: lines cannot represent copper
        // to subtract. Repair the filled part before the Boolean operations.
        Geometry repairedCopper = copper.buffer(0);
        // Imported Python lists of multipart polygons are represented by a
        // generic GeometryCollection. The GUI unions that list before
        // subtraction; buffer(0) alone preserves different ring starts and
        // changes Connect's endpoints despite an identical copper area.
        // Do not apply list semantics to an ordinary Polygon/MultiPolygon.
        Geometry cleanCopper = copper.getGeometryType().equals("GeometryCollection")
                ? OverlayNGRobust.union(repairedCopper) : repairedCopper;
        cancellation.throwIfCancellationRequested();
        Geometry rawBoundary = switch (params.boundary()) {
            case NccBoundary.Itself ignored -> cleanCopper.convexHull();
            case NccBoundary.Area area -> area.geometry();
            case NccBoundary.ReferenceGerber ref -> cleanCopper.convexHull()
                    .intersection(ref.geometry().buffer(0).convexHull());
            // Keep lines until the margin is applied: buffer(0) erases a LineString.
            // Python buffers each reference shape by the margin before unioning it.
            case NccBoundary.ReferenceGeometry ref -> ref.geometry();
        };
        Geometry boundary = mitreBuffer(rawBoundary, params.margin());
        if (boundary.isEmpty() || boundary.getArea() <= 0) {
            throw new IllegalArgumentException("O limite do NCC precisa ter area preenchida; "
                    + "ajuste a referencia ou a margem.");
        }
        // ISO contours precede clearing, and the widest selected ISO cutter
        // sets the copper envelope the CLEAR cutters must stay outside.
        double isolationRadius = params.isolationToolDiameters().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0) / 2.0;
        List<Double> orderedTools = orderedToolDiameters(params);
        double initialOffset = params.restMachining() ? params.copperOffset()
                : params.settingsFor(orderedTools.get(0)).copperOffset();
        Geometry clearingArea = clearingArea(boundary, cleanCopper,
                isolationRadius + initialOffset);
        cancellation.throwIfCancellationRequested();
        progress.report(0.08);

        Map<Double, Geometry> areaByOffset = new HashMap<>();
        areaByOffset.put(initialOffset, clearingArea);
        List<NccToolResult> toolResults = new ArrayList<>();
        List<Geometry> combinedPaths = new ArrayList<>();
        Geometry representedArea = clearingArea;
        double smallestToolOffset = Double.POSITIVE_INFINITY;
        for (double diameter : params.isolationToolDiameters()) {
            cancellation.throwIfCancellationRequested();
            Geometry envelope = cleanCopper.buffer(diameter / 2.0, QUADRANT_SEGMENTS);
            List<LineString> rings = new ArrayList<>();
            collectIsoBoundaryLines(envelope, params.millingType(), rings);
            List<LineString> clipped = new ArrayList<>();
            for (LineString ring : rings) {
                cancellation.throwIfCancellationRequested();
                collectLines(ring.intersection(boundary), clipped);
            }
            Geometry paths = clipped.isEmpty() ? factory.createGeometryCollection()
                    : factory.buildGeometry(new ArrayList<>(clipped));
            toolResults.add(new NccToolResult(diameter, paths, 0, NccOperation.ISO));
            if (!paths.isEmpty()) {
                combinedPaths.add(paths);
            }
        }
        Geometry remainingArea = clearingArea;

        for (int t = 0; t < orderedTools.size(); t++) {
            double toolDiameter = orderedTools.get(t);
            NccToolSettings settings = params.settingsFor(toolDiameter);
            Geometry areaForThisTool = params.restMachining() ? remainingArea
                    : areaByOffset.computeIfAbsent(settings.copperOffset(), offset ->
                            clearingArea(boundary, cleanCopper, isolationRadius + offset));
            // All tools share one boundary and their copper keep-outs are
            // nested. The union of their clearable areas is the one with the
            // smallest offset; no expensive polygon union is needed here.
            if (!params.restMachining() && settings.copperOffset() < smallestToolOffset) {
                representedArea = areaForThisTool;
                smallestToolOffset = settings.copperOffset();
            }
            int toolIndex = t;
            int toolCount = orderedTools.size();
            ToolClearResult toolClear = clearArea(areaForThisTool, toolDiameter, settings, params.restMachining(), cancellation,
                    fraction -> progress.report(0.08 + 0.90 * (toolIndex + fraction) / toolCount));
            toolResults.add(new NccToolResult(toolDiameter, toolClear.geometry(), toolClear.failures(),
                    NccOperation.CLEAR));
            if (!toolClear.geometry().isEmpty()) {
                combinedPaths.add(toolClear.geometry());
            }
            if (params.restMachining() && !toolClear.footprint().isEmpty()) {
                remainingArea = OverlayNGRobust.overlay(remainingArea,toolClear.footprint(),OverlayNG.DIFFERENCE);
            }
            cancellation.throwIfCancellationRequested();
        }

        Geometry combined = unionGeometries(factory, combinedPaths);
        progress.report(1.0);
        return new NccResult(units, combined, representedArea, toolResults);
    }

    /**
     * appTools/ToolPaint.py: fills the polygons of {@code polygons} with toolpaths, tool by tool. It shares
     * the NCC's clearing strategies (Standard, Seed, Lines, Combo); what differs is the area: the polygons
     * themselves, each shrunk by the margin, instead of the space around copper.
     *
     * @throws IllegalArgumentException when there is nothing filled to paint
     */
    public static NccResult paint(String units, Geometry polygons, PaintParameters params,
                                  CancellationToken cancellation, ProgressCallback progress) {
        Objects.requireNonNull(params, "params");
        cancellation.throwIfCancellationRequested();
        List<Polygon> parts = new ArrayList<>();
        if (polygons != null) {
            collectPolygons(polygons, parts);
        }
        parts.removeIf(polygon -> polygon.isEmpty() || polygon.getArea() <= 0);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Nao ha poligonos preenchidos para pintar");
        }
        GeometryFactory factory = parts.get(0).getFactory();
        List<Double> tools = new ArrayList<>(params.toolDiameters());
        if (params.restMachining() || params.order() == NccOrder.REVERSE) {
            tools.sort(java.util.Comparator.reverseOrder());
        } else if (params.order() == NccOrder.FORWARD) {
            tools.sort(java.util.Comparator.naturalOrder());
        }
        Geometry area = org.locationtech.jts.operation.overlayng.OverlayNGRobust.union(new ArrayList<Geometry>(parts));
        Geometry cleared = factory.createGeometryCollection();
        List<Geometry> requestedAreas = new ArrayList<>();
        List<NccToolResult> toolResults = new ArrayList<>();
        List<Geometry> combinedPaths = new ArrayList<>();
        for (int t = 0; t < tools.size(); t++) {
            cancellation.throwIfCancellationRequested();
            double diameter = tools.get(t);
            PaintToolSettings toolSettings = params.settingsFor(diameter);
            NccToolSettings settings = toolSettings.clearing();
            Geometry desired = toolSettings.offset() == 0 ? area : area.buffer(-toolSettings.offset(), QUADRANT_SEGMENTS);
            if (!desired.isEmpty()) requestedAreas.add(desired);
            Geometry areaForTool = params.restMachining() ? desired.difference(cleared).buffer(0) : desired;
            int index = t;
            ToolClearResult result = areaForTool.isEmpty()
                    ? new ToolClearResult(factory.createGeometryCollection(), factory.createGeometryCollection(), 0)
                    : clearArea(areaForTool, diameter, settings, false, cancellation,
                            fraction -> progress.report((index + fraction) / tools.size()));
            toolResults.add(new NccToolResult(diameter, result.geometry(), result.failures(), NccOperation.CLEAR));
            if (!result.geometry().isEmpty()) {
                combinedPaths.add(result.geometry());
            }
            if (params.restMachining() && !result.footprint().isEmpty()) {
                cleared = cleared.union(result.footprint()).buffer(0);
            }
        }
        progress.report(1.0);
        if (requestedAreas.isEmpty()) throw new IllegalArgumentException("A margem e grande demais: nenhum poligono sobrou para pintar");
        return new NccResult(units, unionGeometries(factory, combinedPaths),
                org.locationtech.jts.operation.overlayng.OverlayNGRobust.union(requestedAreas), toolResults);
    }

    private static Geometry clearingArea(Geometry boundary, Geometry copper, double keepOutOffset) {
        Geometry keepOut = keepOutOffset == 0 ? copper
                : copper.buffer(keepOutOffset, QUADRANT_SEGMENTS);
        // GEOS 3.10 uses OverlayNG for polygon subtraction. JTS's default
        // classic overlay represents the same area but starts rings on different
        // vertices, changing paint_connect's endpoints and thus cutting moves.
        // Use the robust modern operation, not a fixture-specific ring rotation.
        return OverlayNGRobust.overlay(boundary, keepOut, OverlayNG.DIFFERENCE).buffer(0);
    }

    /**
     * The smallest gap between any two disjoint copper features in {@code copper} - appTools/ToolNCC.py's
     * "Check validity" (find_safe_tooldia_multiprocessing/find_optim_mp): any tool wider than this gap
     * will leave that gap un-milled, no matter how the clearing area is computed, since it physically
     * can't fit through. Advisory only - NccGenerator.generate() does not consult this itself. Empty
     * when there are fewer than two disjoint copper parts (nothing to measure a gap between).
     */
    public static java.util.OptionalDouble minimumCopperClearance(Geometry copper) {
        if (copper == null || copper.isEmpty()) {
            return java.util.OptionalDouble.empty();
        }
        Geometry clean = copper.buffer(0);
        List<Polygon> parts = new ArrayList<>();
        collectPolygons(clean, parts);
        if (parts.size() < 2) {
            return java.util.OptionalDouble.empty();
        }
        double minDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                minDistance = Math.min(minDistance, parts.get(i).distance(parts.get(j)));
            }
        }
        return java.util.OptionalDouble.of(minDistance);
    }

    /** {@link NccParameters#toolDiameters()} in the order NccGenerator should process them. */
    private static List<Double> orderedToolDiameters(NccParameters params) {
        List<Double> tools = new ArrayList<>(params.toolDiameters());
        if (params.restMachining()) {
            tools.sort(Comparator.reverseOrder());
            return tools;
        }
        switch (params.order()) {
            case FORWARD -> tools.sort(Comparator.naturalOrder());
            case REVERSE -> tools.sort(Comparator.reverseOrder());
            case NONE -> {
                // keep the table/insertion order as given
            }
        }
        return tools;
    }

    private record ToolClearResult(Geometry geometry, Geometry footprint, int failures) {
    }

    /** Clears every polygon in {@code area} with one tool, and reports the actual swept footprint (for Rest Machining). */
    private static ToolClearResult clearArea(Geometry area, double toolDiameter, NccToolSettings settings,
                                             boolean nccRest,
                                             CancellationToken cancellation, DoubleConsumer progressWithinTool) {
        GeometryFactory factory = area.getFactory();
        List<Polygon> polygons = new ArrayList<>();
        collectPolygons(area, polygons);
        List<LineString> allPaths = new ArrayList<>();
        List<Geometry> footprints = new ArrayList<>();
        int failures = 0;
        // GUI NCC Rest differs from the Tcl implementation: Shapely's default
        // resolution (16), tool/1.9999999 and a final +1e-7 repair buffer.
        // Preserve Paint's independent conservative footprint policy.
        double footprintRadius = nccRest ? toolDiameter / 1.9999999 : toolDiameter / 2.0 * (1 - FOOTPRINT_SHRINK);
        for (int i = 0; i < polygons.size(); i++) {
            cancellation.throwIfCancellationRequested();
            Polygon polygon = polygons.get(i);
            // The GUI Rest loop deliberately leaves polygons that cannot admit
            // this cutter for the next smaller tool. They are not algorithm
            // failures and must not make an otherwise completed Rest job partial.
            if (nccRest && preciseRoundBuffer(polygon, -toolDiameter / 2.0).isEmpty()) {
                progressWithinTool.accept((i + 1.0) / Math.max(1, polygons.size()));
                continue;
            }
            List<LineString> paths = clearPolygon(polygon, toolDiameter, settings, cancellation);
            if (paths.isEmpty()) {
                failures++;
            } else {
                if (settings.connect()) {
                    // paint_connect tests the swept tool against the ORIGINAL polygon.
                    // Eroding here as well would subtract the radius twice.
                    paths = connectSafePaths(paths, polygon, toolDiameter, factory, cancellation);
                }
                allPaths.addAll(paths);
                for (LineString path : paths) {
                    footprints.add(nccRest
                            ? GeosBufferOp.bufferOp(path,footprintRadius,new BufferParameters(16))
                            : path.buffer(footprintRadius, QUADRANT_SEGMENTS));
                }
            }
            progressWithinTool.accept((i + 1.0) / Math.max(1, polygons.size()));
        }
        Geometry geometry = allPaths.isEmpty()
                ? factory.createGeometryCollection() : factory.buildGeometry(new ArrayList<>(allPaths));
        Geometry footprint = footprints.isEmpty() ? factory.createGeometryCollection() : OverlayNGRobust.union(footprints);
        if (nccRest && !footprint.isEmpty()) {
            footprint = GeosBufferOp.bufferOp(footprint,1e-7,new BufferParameters(16));
        }
        return new ToolClearResult(geometry, footprint, failures);
    }

    private static Geometry mitreBuffer(Geometry geometry, double distance) {
        if (distance == 0) {
            return geometry;
        }
        BufferParameters parameters = new BufferParameters(
                QUADRANT_SEGMENTS, BufferParameters.CAP_ROUND, BufferParameters.JOIN_MITRE, 5.0);
        // NCC's margin has the same GEOS near-parallel corner rules as its
        // erosion. Stock JTS collapses shallow convex corners differently.
        return GeosBufferOp.bufferOp(geometry, distance, parameters);
    }

    /**
     * Preserve small notches/necks when computing the cutter-center keep-in area, with GEOS 3.10.3's
     * buffer rules instead of JTS's own - see {@link GeosBufferOp}'s package for why and how, and
     * INVESTIGACAO_CAM.md for the measurements. The GEOS-faithful input simplifier (left at its
     * default tolerance here, unlike stock JTS buffering below) is what actually protects small
     * notches; it is not a separate "simplification off" workaround layered on top.
     */
    private static Geometry preciseRoundBuffer(Geometry geometry, double distance) {
        BufferParameters parameters = new BufferParameters(QUADRANT_SEGMENTS);
        return GeosBufferOp.bufferOp(geometry, distance, parameters);
    }

    private static List<LineString> clearPolygon(Polygon polygon, double toolDiameter, NccToolSettings settings,
                                                  CancellationToken cancellation) {
        return switch (settings.method()) {
            case STANDARD -> standardPaths(polygon, toolDiameter, settings, cancellation);
            case SEED -> seedPaths(polygon, toolDiameter, settings, cancellation);
            case LINES -> linePaths(polygon, toolDiameter, settings, cancellation);
            case COMBO -> {
                List<LineString> paths = linePaths(polygon, toolDiameter, settings, cancellation);
                if (paths.isEmpty()) {
                    paths = seedPaths(polygon, toolDiameter, settings, cancellation);
                }
                if (paths.isEmpty()) {
                    paths = standardPaths(polygon, toolDiameter, settings, cancellation);
                }
                yield paths;
            }
        };
    }

    /**
     * Inward-offset strategy: the legacy clear_polygon() method.
     *
     * <p>Each erosion pass uses {@link GeosBufferOp}, a local port of JTS's buffer algorithm
     * aligned to GEOS 3.10.3's offset-curve and input-simplification rules instead of JTS's own
     * (see that package's class docs and INVESTIGACAO_CAM.md). Successive JTS/GEOS erosions of the
     * same real board copper disagreed by as much as ~0.021 mm by the time Rest Machining's later,
     * smaller tools ran; on the public synthetic fixtures this port's own output matches a real GEOS
     * 3.10.3/3.13.1 oracle to within floating-point noise (~1e-14 mm), at every resolution and pass
     * tested. It is not a general buffer replacement: only Standard's repeated erosion and the Seed/
     * Lines/Rest-Machining safe-area erosion in {@link #preciseRoundBuffer} switch to it.
     * GUI NCC Rest footprints also use it with the legacy default resolution 16,
     * as do NCC's mitred margins. Copper offsets and Paint footprints retain
     * their own policies.
     */
    private static List<LineString> standardPaths(Polygon polygon, double toolDiameter, NccToolSettings settings,
                                                   CancellationToken cancellation) {
        List<LineString> paths = new ArrayList<>();
        // Match clear_polygon's inward epsilon, avoiding exactly tangent/zero-width remnants.
        double radius = toolDiameter / 1.999999;
        double step = toolDiameter * (1.0 - settings.overlapFraction());
        BufferParameters parameters = new BufferParameters(QUADRANT_SEGMENTS);
        Geometry current = GeosBufferOp.bufferOp(polygon, -radius, parameters);
        Envelope envelope = polygon.getEnvelopeInternal();
        int maxPasses = (int) Math.ceil(Math.max(envelope.getWidth(), envelope.getHeight()) / step) + 4;
        for (int pass = 0; pass < maxPasses && current != null && !current.isEmpty(); pass++) {
            cancellation.throwIfCancellationRequested();
            collectBoundaryLines(current, paths);
            Geometry next = GeosBufferOp.bufferOp(current, -step, parameters);
            if (next.isEmpty() || Math.abs(next.getArea() - current.getArea()) < 1e-14) {
                break;
            }
            current = next;
        }
        return paths;
    }

    /**
     * Expanding-ring strategy: the legacy clear_polygon2() method.
     *
     * <p>STABLE preserves the FX's {@link StableInteriorPoint} policy. PYTHON explicitly
     * selects the legacy representative-point scan line (shared by JTS/GEOS), whose
     * discontinuities are documented in INVESTIGACAO_CAM.md. No source rounding is applied.
     */
    private static List<LineString> seedPaths(Polygon polygon, double toolDiameter, NccToolSettings settings,
                                               CancellationToken cancellation) {
        List<LineString> paths = new ArrayList<>();
        double toolRadius = toolDiameter / 2.0;
        double step = toolDiameter * (1.0 - settings.overlapFraction());
        Geometry safeArea = preciseRoundBuffer(polygon, -toolRadius);
        if (safeArea.isEmpty()) {
            return paths;
        }
        Coordinate seed = settings.seedPolicy() == NccSeedPolicy.PYTHON
                ? safeArea.getInteriorPoint().getCoordinate()
                : StableInteriorPoint.find(safeArea, toolRadius * 0.01, cancellation);
        cancellation.throwIfCancellationRequested();
        if (seed == null) {
            return paths;
        }
        Envelope envelope = safeArea.getEnvelopeInternal();
        double farthest = Math.max(
                Math.max(seed.distance(new Coordinate(envelope.getMinX(), envelope.getMinY())),
                         seed.distance(new Coordinate(envelope.getMaxX(), envelope.getMinY()))),
                Math.max(seed.distance(new Coordinate(envelope.getMinX(), envelope.getMaxY())),
                         seed.distance(new Coordinate(envelope.getMaxX(), envelope.getMaxY()))));
        double radius = toolRadius * (1.0 - settings.overlapFraction());
        int maxPasses = (int) Math.ceil((farthest + step) / step) + 2;
        for (int pass = 0; pass < maxPasses; pass++, radius += step) {
            cancellation.throwIfCancellationRequested();
            Geometry ring = safeArea.getFactory().createPoint(seed)
                    .buffer(radius, QUADRANT_SEGMENTS).getBoundary().intersection(safeArea);
            if (!ring.isEmpty()) {
                collectLines(ring, paths);
            } else {
                // clear_polygon2 stops at the first ring outside the eroded area.
                // Do not jump across an empty annulus to disconnected remnants.
                break;
            }
        }
        if (settings.contour()) {
            collectBoundaryLines(safeArea, paths);
        }
        return paths;
    }

    /** Parallel raster strategy: the legacy clear_polygon3() method. */
    private static List<LineString> linePaths(Polygon polygon, double toolDiameter, NccToolSettings settings,
                                               CancellationToken cancellation) {
        List<LineString> paths = new ArrayList<>();
        double toolRadius = toolDiameter / 1.99999999;
        double step = toolDiameter * (1.0 - settings.overlapFraction());
        Geometry safeArea = preciseRoundBuffer(polygon, -toolRadius);
        if (safeArea.isEmpty()) {
            return paths;
        }
        Envelope envelope = polygon.getEnvelopeInternal();
        GeometryFactory factory = polygon.getFactory();
        if (envelope.getWidth() >= envelope.getHeight()) {
            int row = 0;
            for (double y = envelope.getMaxY() - toolRadius;
                 y >= envelope.getMinY() + toolRadius; y -= step, row++) {
                cancellation.throwIfCancellationRequested();
                LineString line = factory.createLineString(new Coordinate[]{
                        new Coordinate(envelope.getMinX(), y), new Coordinate(envelope.getMaxX(), y)});
                List<LineString> rowLines = new ArrayList<>();
                collectLines(line.intersection(safeArea), rowLines);
                if ((row & 1) == 1) {
                    reverseAll(rowLines);
                }
                paths.addAll(rowLines);
            }
        } else {
            int column = 0;
            for (double x = envelope.getMinX() + toolRadius;
                 x <= envelope.getMaxX() - toolRadius; x += step, column++) {
                cancellation.throwIfCancellationRequested();
                LineString line = factory.createLineString(new Coordinate[]{
                        new Coordinate(x, envelope.getMaxY()), new Coordinate(x, envelope.getMinY())});
                List<LineString> columnLines = new ArrayList<>();
                collectLines(line.intersection(safeArea), columnLines);
                if ((column & 1) == 1) {
                    reverseAll(columnLines);
                }
                paths.addAll(columnLines);
            }
        }
        if (settings.contour()) {
            collectBoundaryLines(safeArea, paths);
        }
        return paths;
    }

    private static void reverseAll(List<LineString> lines) {
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, lines.get(i).reverse());
        }
    }

    /** Greedily orders paths and joins only connectors contained by the safe cutter-center area. */
    /**
     * camlib.py's {@code paint_connect}: joins disjoint paths that can be reached by a straight
     * tool travel without lifting, to cut down on lifts. Python buffers each candidate connecting
     * segment by the tool's own radius before testing it against the safe area - a bare centerline
     * can be fully inside while the tool sweeping along it would still clip the boundary - and
     * refuses a connection longer than {@code 10 * toolDiameter} ({@code max_walk}'s default) even
     * when the swept connector is safe, so joining does not turn into one long meandering travel
     * across an otherwise-fine area.
     */
    static List<LineString> connectSafePaths(List<LineString> source, Geometry safeArea,
                                             double toolDiameter, GeometryFactory factory,
                                             CancellationToken cancellation) {
        if (source.size() < 2 || safeArea == null || safeArea.isEmpty()) {
            return source;
        }
        double maxWalk = 10 * toolDiameter;
        List<LineString> remaining = new ArrayList<>(source);
        List<LineString> connected = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        // Legacy paint_connect starts at the path nearest (0,0), preserving its
        // direction even when its last endpoint was nearest.
        int firstIndex = 0;
        double firstDistance = Double.POSITIVE_INFINITY;
        Coordinate origin = new Coordinate(0, 0);
        for (int i = 0; i < remaining.size(); i++) {
            cancellation.throwIfCancellationRequested();
            Coordinate[] points = remaining.get(i).getCoordinates();
            double distance = Math.min(points[0].distance(origin), points[points.length - 1].distance(origin));
            if (distance < firstDistance) { firstIndex = i; firstDistance = distance; }
        }
        LineString first = remaining.remove(firstIndex);
        addCoordinates(current, first.getCoordinates(), false);

        while (!remaining.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            Coordinate end = current.get(current.size() - 1);
            int bestIndex = 0;
            boolean reverse = false;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < remaining.size(); i++) {
                Coordinate[] coordinates = remaining.get(i).getCoordinates();
                double toStart = end.distance(coordinates[0]);
                double toEnd = end.distance(coordinates[coordinates.length - 1]);
                if (toStart < bestDistance) {
                    bestDistance = toStart;
                    bestIndex = i;
                    reverse = false;
                }
                if (toEnd < bestDistance) {
                    bestDistance = toEnd;
                    bestIndex = i;
                    reverse = true;
                }
            }
            LineString next = remaining.remove(bestIndex);
            Coordinate[] coordinates = next.getCoordinates();
            if (reverse) {
                coordinates = reversed(coordinates);
            }
            boolean walkable = bestDistance < maxWalk
                    && factory.createLineString(new Coordinate[]{end, coordinates[0]})
                            .buffer(toolDiameter / 2.0, QUADRANT_SEGMENTS).within(safeArea);
            if (walkable) {
                addCoordinates(current, coordinates, true);
            } else {
                connected.add(factory.createLineString(current.toArray(new Coordinate[0])));
                current = new ArrayList<>();
                addCoordinates(current, coordinates, false);
            }
        }
        if (current.size() >= 2) {
            connected.add(factory.createLineString(current.toArray(new Coordinate[0])));
        }
        return connected;
    }

    private static void addCoordinates(List<Coordinate> target, Coordinate[] coordinates, boolean skipDuplicateFirst) {
        int start = skipDuplicateFirst && !target.isEmpty()
                && target.get(target.size() - 1).equals2D(coordinates[0]) ? 1 : 0;
        for (int i = start; i < coordinates.length; i++) {
            target.add(new Coordinate(coordinates[i]));
        }
    }

    private static Coordinate[] reversed(Coordinate[] source) {
        Coordinate[] reversed = new Coordinate[source.length];
        for (int i = 0; i < source.length; i++) {
            reversed[i] = new Coordinate(source[source.length - 1 - i]);
        }
        return reversed;
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> target) {
        if (geometry instanceof Polygon polygon) {
            if (!polygon.isEmpty()) {
                target.add(polygon);
            }
        } else if (geometry instanceof MultiPolygon || geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collectPolygons(geometry.getGeometryN(i), target);
            }
        }
    }

    private static void collectBoundaryLines(Geometry geometry, List<LineString> target) {
        if (geometry instanceof Polygon polygon) {
            target.add(polygon.getFactory().createLineString(polygon.getExteriorRing().getCoordinateSequence()));
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                target.add(polygon.getFactory().createLineString(polygon.getInteriorRingN(i).getCoordinateSequence()));
            }
        } else {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collectBoundaryLines(geometry.getGeometryN(i), target);
            }
        }
    }

    /** Python's Climb mode reverses envelope exteriors, leaving hole rings as generated. */
    private static void collectIsoBoundaryLines(Geometry geometry, NccMillingType millingType,
                                                List<LineString> target) {
        if (geometry instanceof Polygon polygon) {
            LineString exterior = polygon.getFactory().createLineString(
                    polygon.getExteriorRing().getCoordinateSequence());
            target.add(millingType == NccMillingType.CLIMB ? (LineString) exterior.reverse() : exterior);
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                target.add(polygon.getFactory().createLineString(
                        polygon.getInteriorRingN(i).getCoordinateSequence()));
            }
        } else if (geometry instanceof MultiPolygon || geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collectIsoBoundaryLines(geometry.getGeometryN(i), millingType, target);
            }
        }
    }

    private static void collectLines(Geometry geometry, List<LineString> target) {
        if (geometry instanceof LineString line) {
            if (!line.isEmpty() && line.getNumPoints() >= 2) {
                target.add(line);
            }
        } else if (geometry instanceof MultiLineString || geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collectLines(geometry.getGeometryN(i), target);
            }
        }
    }

    /**
     * Unions several (possibly already multi-part) geometries into one flat
     * result. Flattens to individual parts first - passing a list containing
     * ONE already-multi-part Geometry straight to buildGeometry would wrap it
     * one level too deep instead of returning it as-is.
     */
    private static Geometry unionGeometries(GeometryFactory factory, List<Geometry> parts) {
        List<Geometry> flat = new ArrayList<>();
        for (Geometry part : parts) {
            for (int i = 0; i < part.getNumGeometries(); i++) {
                flat.add(part.getGeometryN(i));
            }
        }
        return flat.isEmpty() ? factory.createGeometryCollection() : factory.buildGeometry(flat);
    }
}
