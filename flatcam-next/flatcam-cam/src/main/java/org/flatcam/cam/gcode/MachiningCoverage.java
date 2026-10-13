package org.flatcam.cam.gcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** Nominal 2D cutter footprints. No Z/material/fixture simulation and no mutation of paths or target. */
public final class MachiningCoverage {
    public record Step(int toolIndex, double targetArea, Geometry ownRemaining, Geometry cumulativeRemaining,
                       double ownRemainingArea, double cumulativeRemainingArea) {
        public boolean hasIndividualTarget() { return ownRemaining != null; }
    }
    public record Result(double targetArea, List<Step> steps, Geometry finalRemaining, double finalRemainingArea) {
        public Result { steps = List.copyOf(steps); }
    }
    public static Result calculate(MachiningTarget target, String units, List<ToolGeometry> tools,
                                   GeometryGCodeParameters common, Map<Integer, GeometryGCodeParameters> perTool,
                                   CancellationToken cancellation, ProgressCallback progress) {
        if (!target.units().equals(units) || !target.pathSignature().equals(MachiningTarget.signature(tools, cancellation)))
            throw new IllegalArgumentException("Área-alvo não corresponde aos trajetos/unidades atuais. Regenere o NCC.");
        if (target.toolAreas().keySet().stream().anyMatch(id -> id >= tools.size()))
            throw new IllegalArgumentException("Área-alvo sem ferramenta correspondente.");
        Geometry remaining = target.area();
        var steps = new ArrayList<Step>();
        for (int index = 0; index < tools.size(); index++) {
            cancellation.throwIfCancellationRequested();
            var tool = tools.get(index); var parameters = perTool.getOrDefault(index, common);
            Geometry paths = GeometryPathCompensation.apply(tool.geometry(),
                    parameters.offset().distance(tool.toolDiameter(), parameters.customOffset()), cancellation);
            var strokes = new ArrayList<Geometry>();
            collectStrokes(paths, tool.toolDiameter() / 2, strokes, cancellation);
            Geometry footprint = strokes.isEmpty() ? paths.getFactory().createPolygon() : OverlayNGRobust.union(strokes);
            cancellation.throwIfCancellationRequested();
            remaining = OverlayNGRobust.overlay(remaining, footprint, OverlayNG.DIFFERENCE);
            Geometry ownTarget = target.toolAreas().get(index);
            Geometry ownRemaining = ownTarget == null ? null : OverlayNGRobust.overlay(ownTarget, footprint, OverlayNG.DIFFERENCE);
            steps.add(new Step(index, ownTarget == null ? 0 : ownTarget.getArea(), ownRemaining, remaining,
                    ownRemaining == null ? Double.NaN : ownRemaining.getArea(), remaining.getArea()));
            cancellation.throwIfCancellationRequested(); progress.report((index + 1.0) / tools.size());
        }
        return new Result(target.area().getArea(), steps, remaining, remaining.getArea());
    }
    private static void collectStrokes(Geometry paths, double radius, List<Geometry> strokes, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested(); if (paths.isEmpty()) return;
        if (paths instanceof Polygon polygon) {
            // A polygon supplies its boundary paths; filling its interior would falsely mark uncut material as cleared.
            collectStrokes(polygon.getExteriorRing(), radius, strokes, cancellation);
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) collectStrokes(polygon.getInteriorRingN(i), radius, strokes, cancellation);
        } else if (paths instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) collectStrokes(collection.getGeometryN(i), radius, strokes, cancellation);
        } else if (paths instanceof LineString || paths instanceof Point) {
            strokes.add(paths.buffer(radius, 16)); cancellation.throwIfCancellationRequested();
        } else throw new IllegalArgumentException("Tipo de trajeto sem análise de cobertura.");
    }
    private MachiningCoverage() { }
}
