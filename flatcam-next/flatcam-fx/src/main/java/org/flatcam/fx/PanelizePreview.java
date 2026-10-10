package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.convert.OutlineToArea;
import org.flatcam.cam.panel.Panelize;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.*;

/** Display only: preserves coordinates/holes and never joins or edits the project objects. */
final class PanelizePreview {
    record Input(List<PlotAreaView.PreviewLayer> contents, Geometry outline, Geometry referenceBox, Panelize.Layout layout,
                 boolean showContent, boolean showOutline, boolean fillInterior) {
        Input { contents = List.copyOf(contents); }
    }
    record Result(Geometry content, List<PlotAreaView.PreviewLayer> contents, Geometry outline, Geometry interior,
                  Geometry boxes, String notice, Envelope bounds) {
        Result { contents = List.copyOf(contents); bounds = new Envelope(bounds); }
        @Override public Envelope bounds() { return new Envelope(bounds); }
    }

    private record ContentStyle(PlotAreaView.LayerCategory category, boolean strokeOnly) { }

    static Result build(Input input, CancellationToken cancellation) {
        if (javafx.application.Platform.isFxApplicationThread())
            throw new IllegalStateException("A previa de panelizacao deve ser calculada fora da thread FX.");
        cancellation.throwIfCancellationRequested();
        Geometry outline = input.outline() == null ? input.referenceBox().getBoundary() : linework(input.outline());
        Geometry interior = null;
        String notice = input.outline() == null ? "Sem contorno escolhido: mostrando caixas de referencia." : "";
        Envelope edgeBounds = outline.getEnvelopeInternal();
        if (input.outline() != null && (input.layout().columns() > 1 && edgeBounds.getWidth() > input.layout().stepX()
                || input.layout().rows() > 1 && edgeBounds.getHeight() > input.layout().stepY()))
            notice = "Contorno maior que o passo entre copias: pode haver sobreposicao. Use sua caixa como referencia.";
        if (input.fillInterior() && input.showOutline()) {
            try { interior = OutlineToArea.convert(outline, cancellation).area(); }
            catch (IllegalArgumentException open) {
                notice = "Contorno aberto: previa das linhas disponivel, sem preencher/inventar cortes.";
            }
        }
        cancellation.throwIfCancellationRequested();
        var contents = new ArrayList<Geometry>(); var outlines = new ArrayList<Geometry>();
        var byStyle = new java.util.LinkedHashMap<ContentStyle, List<Geometry>>();
        var fills = new ArrayList<Geometry>(); var boxes = new ArrayList<Geometry>();
        for (double[] cell : input.layout().offsets()) {
            cancellation.throwIfCancellationRequested();
            TransformOp offset = new TransformOp.Offset(cell[0], cell[1]);
            if (input.showContent()) for (PlotAreaView.PreviewLayer source : input.contents()) {
                cancellation.throwIfCancellationRequested();
                Geometry geometry = offset.apply(source.geometry());
                contents.add(geometry);
                byStyle.computeIfAbsent(new ContentStyle(source.category(), source.strokeOnly()), ignored -> new ArrayList<>()).add(geometry);
            }
            if (input.showOutline()) outlines.add(offset.apply(outline));
            if (interior != null) fills.add(offset.apply(interior));
            boxes.add(offset.apply(input.referenceBox().getBoundary()));
        }
        cancellation.throwIfCancellationRequested();
        var factory = input.referenceBox().getFactory();
        Geometry content = factory.buildGeometry(contents), edges = factory.buildGeometry(outlines);
        Geometry filled = factory.buildGeometry(fills), grid = factory.buildGeometry(boxes);
        // Draw drill/slot markers last so copper or filled Geometry cannot hide them.
        List<PlotAreaView.PreviewLayer> layers = byStyle.entrySet().stream()
                .sorted(java.util.Comparator.comparingInt(entry -> switch (entry.getKey().category()) {
                    case GERBER -> 0; case GEOMETRY -> 1; default -> 2;
                }))
                .map(entry -> new PlotAreaView.PreviewLayer(factory.buildGeometry(entry.getValue()),
                        entry.getKey().category(), entry.getKey().strokeOnly())).toList();
        Envelope bounds = new Envelope();
        for (Geometry geometry : List.of(content, edges, filled, grid)) {
            cancellation.throwIfCancellationRequested(); bounds.expandToInclude(geometry.getEnvelopeInternal());
        }
        for (var layer : layers) { cancellation.throwIfCancellationRequested(); layer.geometry().getEnvelopeInternal(); }
        return new Result(content, layers, edges, filled, grid, notice, bounds);
    }

    /** Polygon exteriors and all internal rings; line collections remain lines (not endpoints). */
    static Geometry linework(Geometry geometry) {
        List<Geometry> lines = new ArrayList<>(); collectLines(geometry, lines);
        return geometry.getFactory().buildGeometry(lines);
    }
    private static void collectLines(Geometry geometry, List<Geometry> lines) {
        if (geometry instanceof Polygon polygon) {
            lines.add(polygon.getExteriorRing());
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) lines.add(polygon.getInteriorRingN(i));
        } else if (geometry instanceof LineString line) lines.add(line);
        else if (geometry instanceof GeometryCollection collection)
            for (int i = 0; i < collection.getNumGeometries(); i++) collectLines(collection.getGeometryN(i), lines);
    }
}
