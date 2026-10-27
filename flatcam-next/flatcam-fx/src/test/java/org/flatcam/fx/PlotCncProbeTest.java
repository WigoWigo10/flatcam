package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class PlotCncProbeTest {
    @Test void lineCountersAggregateSeparatelyFromCpuCommandTime() {
        var metrics = new PlotBenchmarkCncMetrics();
        metrics.sample(new PlotCncProbe.Sample(PlotCncProbe.Pass.PASS_LINES, false, 1.25, 2_000_000,
                new CanvasCncLineRenderer.Stats(true, 100, 20, 80, 4, 0)));
        metrics.sample(new PlotCncProbe.Sample(PlotCncProbe.Pass.PASS_LINES, false, 1.25, 3_000_000,
                new CanvasCncLineRenderer.Stats(true, 50, 10, 40, 2, 1)));
        var lines = metrics.finish().getJSONObject("PASS_LINES");
        assertEquals(2, lines.getLong("clippedCalls")); assertEquals(150, lines.getLong("acceptedSegments"));
        assertEquals(30, lines.getLong("emittedSegments")); assertEquals(120, lines.getLong("culledSegments"));
        assertEquals(6, lines.getLong("strokeCalls")); assertEquals(1, lines.getLong("fallbackParts"));
        assertEquals(5, lines.getDouble("commandEmissionMs"));
    }

    @Test void eachAblationOmitsOnlyTheNamedPassAndNeverMeansVisualParity() {
        for (var mode : PlotCncProbe.Mode.values()) {
            var probe = new PlotCncProbe(mode, ignored -> {});
            for (var pass : PlotCncProbe.Pass.values()) {
                boolean expected = mode == PlotCncProbe.Mode.FULL
                        || mode != PlotCncProbe.Mode.OMIT_CNC && !mode.name().equals("OMIT_" + pass.name());
                assertEquals(expected, probe.includes(pass), mode + "/" + pass);
            }
        }
    }

    @Test void wideBodyControlKeepsTheEntireThinRasterEligibleRangeAndTheDetails() {
        var probe = new PlotCncProbe(PlotCncProbe.Mode.OMIT_WIDE_BODY, ignored -> {});
        assertTrue(probe.includesBody(1.5)); assertTrue(probe.includesBody(2.5));
        assertFalse(probe.includesBody(Math.nextUp(2.5))); assertFalse(probe.includesBody(15));
        assertTrue(probe.includes(PlotCncProbe.Pass.PASS_LINES));
        assertTrue(probe.includes(PlotCncProbe.Pass.DECORATIONS));
        assertFalse(new PlotCncProbe(PlotCncProbe.Mode.OMIT_BODY, ignored -> {}).includesBody(1.5));
        assertTrue(new PlotCncProbe(PlotCncProbe.Mode.FULL, ignored -> {}).includesBody(15));
    }

    @Test void phaseCountsDistinguishVectorRasterAndLogicalStrokeWidths() {
        var metrics = new PlotBenchmarkCncMetrics();
        assertEquals(0, metrics.finish().getJSONObject("BODY").getInt("calls"));
        metrics.sample(new PlotCncProbe.Sample(PlotCncProbe.Pass.BODY, true, 1.5, 2_000_000));
        metrics.sample(new PlotCncProbe.Sample(PlotCncProbe.Pass.BODY, false, 12, 3_000_000));
        metrics.sample(new PlotCncProbe.Sample(PlotCncProbe.Pass.PASS_LINES, false, 1.25));
        var body = metrics.finish().getJSONObject("BODY");
        assertEquals(2, body.getInt("calls")); assertEquals(1, body.getInt("vectorCalls"));
        assertEquals(1, body.getInt("rasterCalls")); assertEquals(1.5, body.getDouble("lineWidthMinLogical"));
        assertEquals(12, body.getDouble("lineWidthMaxLogical"));
        assertEquals(5, body.getDouble("commandEmissionMs"));
        assertEquals(1, metrics.finish().getJSONObject("PASS_LINES").getInt("calls"));
        metrics.sample(new PlotCncProbe.Sample(PlotCncProbe.Pass.DECORATIONS, false, 0));
        assertFalse(metrics.finish().getJSONObject("DECORATIONS").getBoolean("cpuTimingAvailable"));
        assertTrue(metrics.finish().getJSONObject("DECORATIONS").isNull("commandEmissionMs"));
    }

    @Test @EnabledOnOs(OS.WINDOWS) void fullProbeMatchesNormalPixelsAndOmissionsPreserveGeometryAndLifecycle() throws Exception {
        String previous = System.getProperty("flatcam.plot.benchmark");
        try {
            System.clearProperty("flatcam.plot.benchmark");
            TerminalPanelTest.fx(() -> {
                var view = new PlotAreaView();
                try { assertThrows(IllegalStateException.class,
                        () -> view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.OMIT_BODY, ignored -> {}))); }
                finally { view.dispose(); }
                return null;
            });
            System.setProperty("flatcam.plot.benchmark", "true");
            TerminalPanelTest.fx(() -> {
                var factory = new GeometryFactory();
                var geometry = factory.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0),
                        new Coordinate(10, 10), new Coordinate(2, 10)});
                var unchanged = geometry.copy();
                var view = new PlotAreaView();
                try {
                    view.resize(500, 300);
                    view.putLayer("cnc", PlotAreaView.LayerCategory.CNCJOB, geometry, Color.LIMEGREEN, Color.LIMEGREEN, true);
                    view.setLayerCenterlineLod("cnc", geometry, 1, true);
                    view.fitAllVisible();
                    var normal = view.snapshot(null, null).getPixelReader();
                    var samples = new ArrayList<PlotCncProbe.Sample>();
                    view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.FULL, samples::add));
                    view.setLayerVisible("cnc", true); // Synchronous redraw, no pulse timing assertion.
                    var full = view.snapshot(null, null).getPixelReader();
                    for (int y = 0; y < 300; y++) for (int x = 0; x < 500; x++)
                        assertEquals(normal.getArgb(x, y), full.getArgb(x, y), x + "," + y);
                    assertFalse(samples.isEmpty());
                    assertTrue(samples.stream().anyMatch(sample -> sample.pass() == PlotCncProbe.Pass.PASS_LINES));
                    samples.clear();
                    view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.OMIT_BODY, samples::add));
                    view.setLayerVisible("cnc", true);
                    assertTrue(samples.stream().noneMatch(sample -> sample.pass() == PlotCncProbe.Pass.BODY));
                    assertTrue(samples.stream().anyMatch(sample -> sample.pass() == PlotCncProbe.Pass.PASS_LINES));
                    samples.clear();
                    view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.OMIT_PASS_LINES, samples::add));
                    view.setLayerVisible("cnc", true);
                    assertTrue(samples.stream().anyMatch(sample -> sample.pass() == PlotCncProbe.Pass.BODY));
                    assertTrue(samples.stream().noneMatch(sample -> sample.pass() == PlotCncProbe.Pass.PASS_LINES));
                    samples.clear();
                    view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.OMIT_CNC, samples::add));
                    view.setLayerVisible("cnc", true);
                    assertEquals(List.of(), samples);
                    assertTrue(geometry.equalsExact(unchanged));
                    assertTrue(view.isLayerVisible("cnc"));
                    view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.FULL, samples::add));
                    view.clearLayers(); view.putLayer("cnc", PlotAreaView.LayerCategory.CNCJOB,
                            geometry, Color.LIMEGREEN, Color.LIMEGREEN, true);
                    assertFalse(samples.isEmpty()); // Probe survives replacement like the render observer.
                    int count = samples.size(); view.dispose(); view.clearLayers();
                    assertEquals(count, samples.size());
                    assertThrows(IllegalStateException.class,
                            () -> view.setCncBenchmarkProbe(new PlotCncProbe(PlotCncProbe.Mode.FULL, samples::add)));
                    assertDoesNotThrow(() -> view.setRasterBenchmarkProbe(false));
                    assertThrows(IllegalStateException.class, () -> view.setRasterBenchmarkProbe(true));
                } finally { view.dispose(); }
                return null;
            });
        } finally {
            if (previous == null) System.clearProperty("flatcam.plot.benchmark");
            else System.setProperty("flatcam.plot.benchmark", previous);
        }
    }
}
