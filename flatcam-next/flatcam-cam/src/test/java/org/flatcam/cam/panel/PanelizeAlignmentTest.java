package org.flatcam.cam.panel;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class PanelizeAlignmentTest {
    private static final GeometryFactory F = new GeometryFactory();
    private static Geometry copper() {
        return F.toGeometry(new Envelope(110, 155, 205, 235))
                .difference(F.createPoint(new Coordinate(115, 210)).buffer(.5));
    }
    private static GerberImage gerber() { return GerberImage.of("MM", Map.of(), copper(), copper().getBoundary(), Map.of()); }
    private static ExcellonImage drills() {
        return ExcellonImage.of("MM", Map.of(7, .812345, 19, .812346),
                List.of(new ExcellonImage.Drill(7, 115, 210)),
                List.of(new ExcellonImage.Slot(19, 119, 211, 120, 211)),
                F.createPoint(new Coordinate(115, 210)).buffer(.812345 / 2));
    }
    private static Panelize.Layout layout() {
        return Panelize.layout(new double[]{100, 200, 160, 240}, 3, 2, 5, 3, Double.NaN, Double.NaN);
    }

    @Test void differentCopperAndDrillBoundsStayRegisteredWithOneLayout() {
        var source = gerber(); var holes = drills();
        var g = Panelize.gerber(source, layout()); var e = Panelize.excellon(holes, layout());
        assertEquals(6, e.totalDrills()); assertEquals(6, e.totalSlots());
        assertEquals(holes.toolDiameters(), e.toolDiameters());
        for (int i = 0; i < 6; i++) {
            double[] offset = layout().offsets().get(i);
            var hit = e.drills().get(i);
            assertEquals(115 + offset[0], hit.x()); assertEquals(210 + offset[1], hit.y());
            assertEquals(7, hit.toolId()); assertEquals(19, e.slots().get(i).toolId());
            assertEquals(119 + offset[0], e.slots().get(i).x1());
            assertFalse(g.solidGeometry().covers(F.createPoint(new Coordinate(hit.x(), hit.y()))));
            assertTrue(g.solidGeometry().covers(F.createPoint(new Coordinate(hit.x() + 1, hit.y()))));
        }
        assertEquals(6 * source.solidGeometry().getArea(), g.solidGeometry().getArea(), 1e-7);
        assertEquals(1, holes.totalDrills()); assertEquals(copper().getArea(), source.solidGeometry().getArea());
    }

    @Test void equalDiameterGeometryToolsKeepTheirIndicesAndIndividualPaths() {
        var first = F.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 0)});
        var second = F.createLineString(new Coordinate[]{new Coordinate(0, 1), new Coordinate(1, 1)});
        var tools = List.of(new ToolGeometry(.2, first), new ToolGeometry(.2, second));
        var result = Panelize.geometry("MM", F.buildGeometry(List.of(first, second)), true, tools,
                new Panelize.Layout(2, 1, false, 5, 3));
        assertEquals(2, result.tools().size());
        assertEquals(0, result.tools().get(0).geometry().getEnvelopeInternal().getMinY());
        assertEquals(1, result.tools().get(1).geometry().getEnvelopeInternal().getMinY());
        assertEquals(6, result.tools().get(1).geometry().getEnvelopeInternal().getMaxX());
    }

    @ParameterizedTest @ValueSource(strings={"gerber", "excellon", "geometry"})
    void eachKindCanCancelBetweenCopiesOrAtCompletion(String kind) {
        for (double threshold : new double[]{.2, 1}) {
            var cancelled = new AtomicBoolean();
            org.flatcam.cam.ProgressCallback progress = fraction -> { if (fraction >= threshold) cancelled.set(true); };
            assertThrows(CancellationException.class, () -> {
                switch(kind) {
                    case "gerber" -> Panelize.gerber(gerber(), layout(), cancelled::get, progress);
                    case "excellon" -> Panelize.excellon(drills(), layout(), cancelled::get, progress);
                    default -> Panelize.geometry("MM", copper(), false, List.of(), layout(), cancelled::get, progress);
                }
            });
        }
    }

    @Test void invalidAndHugeGridInputsCannotHangTheUi() {
        var box = new double[]{0, 0, 60, 40};
        assertThrows(IllegalArgumentException.class, () -> Panelize.layout(box, 2, 2, Double.NaN, 0, Double.NaN, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Panelize.layout(box, 2, 2, 0, 0, Double.POSITIVE_INFINITY, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Panelize.Layout(Integer.MAX_VALUE, 2, false, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Panelize.Layout(2, 2, false, Double.POSITIVE_INFINITY, 1));
        var fitted = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () ->
                Panelize.layout(box, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 200, 100));
        assertEquals(3, fitted.columns()); assertEquals(2, fitted.rows());
    }
}
