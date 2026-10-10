package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.panel.Panelize;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class PanelizePreviewTest {
    private static final GeometryFactory F = new GeometryFactory();
    private static Geometry board() {
        return F.toGeometry(new Envelope(100, 160, 200, 240)).difference(F.toGeometry(new Envelope(125, 135, 215, 225)));
    }
    private static PanelizePreview.Input input(Geometry outline, boolean content, boolean edge, boolean fill) {
        Geometry copper = board().difference(F.createPoint(new Coordinate(115, 210)).buffer(.5));
        return new PanelizePreview.Input(List.of(new PlotAreaView.PreviewLayer(copper, PlotAreaView.LayerCategory.GERBER, false)),
                outline, F.toGeometry(new Envelope(100, 160, 200, 240)),
                new Panelize.Layout(2, 2, false, 65, 43), content, edge, fill);
    }
    @Test void actualCopiesPreserveHolesInternalCutsAndOriginalCoordinates() {
        var original = board();
        var result = PanelizePreview.build(input(original.getBoundary(), true, true, true), () -> false);
        assertEquals(4, result.content().getNumGeometries());
        assertEquals(4, result.interior().getNumGeometries());
        for (int i = 0; i < 4; i++) {
            var offset = new Panelize.Layout(2, 2, false, 65, 43).offsets().get(i);
            assertFalse(result.content().getGeometryN(i).covers(F.createPoint(new Coordinate(115 + offset[0], 210 + offset[1]))));
            assertFalse(result.interior().getGeometryN(i).covers(F.createPoint(new Coordinate(130 + offset[0], 220 + offset[1]))));
            assertTrue(result.interior().getGeometryN(i).covers(F.createPoint(new Coordinate(110 + offset[0], 210 + offset[1]))));
        }
        assertEquals(225, result.outline().getEnvelopeInternal().getMaxX());
        assertEquals(2300, original.getArea());
        assertEquals("", result.notice());
    }
    @Test void openOutlineIsNotInventedOrFilledByAConvexHull() {
        var line = F.createLineString(new Coordinate[]{new Coordinate(100, 200), new Coordinate(160, 200)});
        var result = PanelizePreview.build(input(line, true, true, true), () -> false);
        assertTrue(result.interior().isEmpty());
        assertEquals(4, result.outline().getNumGeometries());
        assertTrue(result.notice().contains("Contorno aberto"));
    }
    @Test void multipartSourcePreviewFillsEveryBoardAndKeepsEveryCutout() {
        Geometry source = F.buildGeometry(List.of(board(),
                org.locationtech.jts.geom.util.AffineTransformation.translationInstance(65,0).transform(board())));
        var input = new PanelizePreview.Input(List.of(), source.getBoundary(), F.toGeometry(source.getEnvelopeInternal()),
                new Panelize.Layout(2,2,false,130,43),false,true,true);
        var result = PanelizePreview.build(input, () -> false);
        assertEquals(source.getArea()*4,result.interior().getArea(),1e-4);
        assertEquals("",result.notice());
        for(double[] offset : input.layout().offsets()) for(double dx : new double[]{0,65}) {
            assertTrue(result.interior().covers(F.createPoint(new Coordinate(110+dx+offset[0],210+offset[1]))));
            assertFalse(result.interior().covers(F.createPoint(new Coordinate(130+dx+offset[0],220+offset[1]))));
        }
    }
    @Test void optionsAndFallbackAreExplicit() {
        var result = PanelizePreview.build(input(null, false, false, false), () -> false);
        assertTrue(result.content().isEmpty()); assertTrue(result.outline().isEmpty()); assertTrue(result.interior().isEmpty());
        assertEquals(4, result.boxes().getNumGeometries());
        assertTrue(result.notice().contains("Sem contorno"));
        assertThrows(CancellationException.class, () -> PanelizePreview.build(input(null, true, true, true), () -> true));
        var overlapping = new PanelizePreview.Input(List.of(new PlotAreaView.PreviewLayer(board(), PlotAreaView.LayerCategory.GERBER, false)), board().getBoundary(),
                F.toGeometry(new Envelope(100, 160, 200, 240)),
                new Panelize.Layout(2, 2, false, 50, 35), true, true, true);
        assertTrue(PanelizePreview.build(overlapping, () -> false).notice().contains("sobreposicao"));
    }
    @Test void lineworkIncludesPolygonHolesButDoesNotTurnLinesIntoEndpoints() {
        var original = board();
        assertEquals(2, PanelizePreview.linework(original).getNumGeometries());
        assertTrue(PanelizePreview.linework(original.getBoundary()).equalsExact(original.getBoundary()));
    }

    @Test void categoryAndStrokeGroupsKeepIdenticalOffsetsAndDrawDrillsLast() {
        Geometry box = F.toGeometry(new Envelope(0, 10, 0, 8));
        Geometry drill = F.createPoint(new Coordinate(3, 3)).buffer(.5);
        var sources = List.of(
                new PlotAreaView.PreviewLayer(drill, PlotAreaView.LayerCategory.EXCELLON, false),
                new PlotAreaView.PreviewLayer(box.getBoundary(), PlotAreaView.LayerCategory.GEOMETRY, true),
                new PlotAreaView.PreviewLayer(box, PlotAreaView.LayerCategory.GERBER, false),
                new PlotAreaView.PreviewLayer(drill, PlotAreaView.LayerCategory.GERBER, false),
                new PlotAreaView.PreviewLayer(box, PlotAreaView.LayerCategory.GEOMETRY, false));
        var request = new PanelizePreview.Input(sources, box.getBoundary(), box,
                new Panelize.Layout(2, 2, false, 12, 9), true, true, true);
        var result = PanelizePreview.build(request, () -> false);
        assertEquals(4, result.contents().size(), "same category/style grouped, not flattened with other kinds");
        assertEquals(PlotAreaView.LayerCategory.GERBER, result.contents().getFirst().category());
        assertEquals(8, result.contents().getFirst().geometry().getNumGeometries());
        assertEquals(PlotAreaView.LayerCategory.EXCELLON, result.contents().getLast().category());
        assertTrue(result.contents().get(1).strokeOnly()); assertFalse(result.contents().get(2).strokeOnly());
        for (var layer : result.contents()) {
            double sourceMinX = layer.category() == PlotAreaView.LayerCategory.EXCELLON ? 2.5 : 0;
            assertEquals(sourceMinX, layer.geometry().getEnvelopeInternal().getMinX(), 1e-9);
            assertEquals(12, layer.geometry().getGeometryN(layer.geometry().getNumGeometries() - 1).getEnvelopeInternal().getMinX()
                    - layer.geometry().getGeometryN(layer.geometry().getNumGeometries() == 8 ? 1 : 0).getEnvelopeInternal().getMinX(), 1e-9);
        }
        assertEquals(80, box.getArea());
        var off = PanelizePreview.build(new PanelizePreview.Input(sources, box.getBoundary(), box, request.layout(), false, true, true), () -> false);
        assertTrue(off.contents().isEmpty()); assertTrue(off.content().isEmpty());
    }
}
