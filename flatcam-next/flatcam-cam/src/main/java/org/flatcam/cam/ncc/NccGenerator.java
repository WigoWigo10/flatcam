package org.flatcam.cam.ncc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.union.UnaryUnionOp;

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
     * Rest Machining's swept-footprint buffer is shrunk very slightly below
     * the true tool radius (Python's {@code tool_used = tool - 1e-12} /
     * {@code tool / 1.9999999}) so floating-point noise in the buffer never
     * lets the footprint claim a hair more area than the tool actually swept -
     * which would wrongly steal reachable material from the next, smaller tool.
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

        progress.report(0.02);
        Geometry cleanCopper = copper.buffer(0);
        cancellation.throwIfCancellationRequested();
        Geometry rawBoundary = switch (params.boundary()) {
            case NccBoundary.Itself ignored -> cleanCopper.convexHull();
            case NccBoundary.ReferenceGerber ref -> cleanCopper.convexHull()
                    .intersection(ref.geometry().buffer(0).convexHull());
            case NccBoundary.ReferenceGeometry ref -> ref.geometry().buffer(0);
        };
        Geometry boundary = mitreBuffer(rawBoundary, params.margin());
        Geometry keepOut = params.copperOffset() == 0
                ? cleanCopper : cleanCopper.buffer(params.copperOffset(), QUADRANT_SEGMENTS);
        Geometry clearingArea = boundary.difference(keepOut).buffer(0);
        cancellation.throwIfCancellationRequested();
        progress.report(0.08);

        List<Double> orderedTools = orderedToolDiameters(params);
        List<NccToolResult> toolResults = new ArrayList<>();
        List<Geometry> combinedPaths = new ArrayList<>();
        Geometry remainingArea = clearingArea;

        for (int t = 0; t < orderedTools.size(); t++) {
            double toolDiameter = orderedTools.get(t);
            Geometry areaForThisTool = params.restMachining() ? remainingArea : clearingArea;
            int toolIndex = t;
            int toolCount = orderedTools.size();
            ToolClearResult toolClear = clearArea(areaForThisTool, toolDiameter, params, cancellation,
                    fraction -> progress.report(0.08 + 0.90 * (toolIndex + fraction) / toolCount));
            toolResults.add(new NccToolResult(toolDiameter, toolClear.geometry(), toolClear.failures()));
            if (!toolClear.geometry().isEmpty()) {
                combinedPaths.add(toolClear.geometry());
            }
            if (params.restMachining() && !toolClear.footprint().isEmpty()) {
                remainingArea = remainingArea.difference(toolClear.footprint()).buffer(0);
            }
            cancellation.throwIfCancellationRequested();
        }

        Geometry combined = unionGeometries(factory, combinedPaths);
        progress.report(1.0);
        return new NccResult(units, combined, clearingArea, toolResults);
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
    private static ToolClearResult clearArea(Geometry area, double toolDiameter, NccParameters params,
                                             CancellationToken cancellation, DoubleConsumer progressWithinTool) {
        GeometryFactory factory = area.getFactory();
        List<Polygon> polygons = new ArrayList<>();
        collectPolygons(area, polygons);
        List<LineString> allPaths = new ArrayList<>();
        List<Geometry> footprints = new ArrayList<>();
        int failures = 0;
        double footprintRadius = toolDiameter / 2.0 * (1 - FOOTPRINT_SHRINK);
        for (int i = 0; i < polygons.size(); i++) {
            cancellation.throwIfCancellationRequested();
            Polygon polygon = polygons.get(i);
            List<LineString> paths = clearPolygon(polygon, toolDiameter, params, cancellation);
            if (paths.isEmpty()) {
                failures++;
            } else {
                if (params.connect()) {
                    Geometry safeCenterArea = polygon.buffer(-toolDiameter / 2.0, QUADRANT_SEGMENTS);
                    paths = connectSafePaths(paths, safeCenterArea, factory, cancellation);
                }
                allPaths.addAll(paths);
                for (LineString path : paths) {
                    footprints.add(path.buffer(footprintRadius, QUADRANT_SEGMENTS));
                }
            }
            progressWithinTool.accept((i + 1.0) / Math.max(1, polygons.size()));
        }
        Geometry geometry = allPaths.isEmpty()
                ? factory.createGeometryCollection() : factory.buildGeometry(new ArrayList<>(allPaths));
        Geometry footprint = footprints.isEmpty() ? factory.createGeometryCollection() : UnaryUnionOp.union(footprints);
        return new ToolClearResult(geometry, footprint, failures);
    }

    private static Geometry mitreBuffer(Geometry geometry, double distance) {
        if (distance == 0) {
            return geometry;
        }
        BufferParameters parameters = new BufferParameters(
                QUADRANT_SEGMENTS, BufferParameters.CAP_ROUND, BufferParameters.JOIN_MITRE, 5.0);
        return BufferOp.bufferOp(geometry, distance, parameters);
    }

    private static List<LineString> clearPolygon(Polygon polygon, double toolDiameter, NccParameters params,
                                                  CancellationToken cancellation) {
        return switch (params.method()) {
            case STANDARD -> standardPaths(polygon, toolDiameter, params, cancellation);
            case SEED -> seedPaths(polygon, toolDiameter, params, cancellation);
            case LINES -> linePaths(polygon, toolDiameter, params, cancellation);
            case COMBO -> {
                List<LineString> paths = linePaths(polygon, toolDiameter, params, cancellation);
                if (paths.isEmpty()) {
                    paths = seedPaths(polygon, toolDiameter, params, cancellation);
                }
                if (paths.isEmpty()) {
                    paths = standardPaths(polygon, toolDiameter, params, cancellation);
                }
                yield paths;
            }
        };
    }

    /** Inward-offset strategy: the legacy clear_polygon() method. */
    private static List<LineString> standardPaths(Polygon polygon, double toolDiameter, NccParameters params,
                                                   CancellationToken cancellation) {
        List<LineString> paths = new ArrayList<>();
        double radius = toolDiameter / 2.0;
        double step = toolDiameter * (1.0 - params.overlapFraction());
        Geometry current = polygon.buffer(-radius, QUADRANT_SEGMENTS);
        Envelope envelope = polygon.getEnvelopeInternal();
        int maxPasses = (int) Math.ceil(Math.max(envelope.getWidth(), envelope.getHeight()) / step) + 4;
        for (int pass = 0; pass < maxPasses && current != null && !current.isEmpty(); pass++) {
            cancellation.throwIfCancellationRequested();
            collectBoundaryLines(current, paths);
            Geometry next = current.buffer(-step, QUADRANT_SEGMENTS);
            if (next.isEmpty() || Math.abs(next.getArea() - current.getArea()) < 1e-14) {
                break;
            }
            current = next;
        }
        return paths;
    }

    /** Expanding-ring strategy: the legacy clear_polygon2() method. */
    private static List<LineString> seedPaths(Polygon polygon, double toolDiameter, NccParameters params,
                                               CancellationToken cancellation) {
        List<LineString> paths = new ArrayList<>();
        double toolRadius = toolDiameter / 2.0;
        double step = toolDiameter * (1.0 - params.overlapFraction());
        Geometry safeArea = polygon.buffer(-toolRadius, QUADRANT_SEGMENTS);
        if (safeArea.isEmpty()) {
            return paths;
        }
        Coordinate seed = safeArea.getInteriorPoint().getCoordinate();
        Envelope envelope = safeArea.getEnvelopeInternal();
        double farthest = Math.max(
                Math.max(seed.distance(new Coordinate(envelope.getMinX(), envelope.getMinY())),
                         seed.distance(new Coordinate(envelope.getMaxX(), envelope.getMinY()))),
                Math.max(seed.distance(new Coordinate(envelope.getMinX(), envelope.getMaxY())),
                         seed.distance(new Coordinate(envelope.getMaxX(), envelope.getMaxY()))));
        double radius = toolRadius * (1.0 - params.overlapFraction());
        int maxPasses = (int) Math.ceil((farthest + step) / step) + 2;
        for (int pass = 0; pass < maxPasses; pass++, radius += step) {
            cancellation.throwIfCancellationRequested();
            Geometry ring = safeArea.getFactory().createPoint(seed)
                    .buffer(radius, QUADRANT_SEGMENTS).getBoundary().intersection(safeArea);
            if (!ring.isEmpty()) {
                collectLines(ring, paths);
            } else if (radius > farthest) {
                break;
            }
        }
        if (params.contour()) {
            collectBoundaryLines(safeArea, paths);
        }
        return paths;
    }

    /** Parallel raster strategy: the legacy clear_polygon3() method. */
    private static List<LineString> linePaths(Polygon polygon, double toolDiameter, NccParameters params,
                                               CancellationToken cancellation) {
        List<LineString> paths = new ArrayList<>();
        double toolRadius = toolDiameter / 2.0;
        double step = toolDiameter * (1.0 - params.overlapFraction());
        Geometry safeArea = polygon.buffer(-toolRadius, QUADRANT_SEGMENTS);
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
        if (params.contour()) {
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
    private static List<LineString> connectSafePaths(List<LineString> source, Geometry safeArea,
                                                      GeometryFactory factory, CancellationToken cancellation) {
        if (source.size() < 2 || safeArea == null || safeArea.isEmpty()) {
            return source;
        }
        List<LineString> remaining = new ArrayList<>(source);
        List<LineString> connected = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        Geometry relaxedSafeArea = safeArea.buffer(1e-10);
        LineString first = remaining.remove(0);
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
            LineString connector = factory.createLineString(new Coordinate[]{end, coordinates[0]});
            if (bestDistance < 1e-12 || relaxedSafeArea.covers(connector)) {
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
