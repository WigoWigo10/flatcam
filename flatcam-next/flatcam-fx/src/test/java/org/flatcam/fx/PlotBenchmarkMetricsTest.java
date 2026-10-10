package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotBenchmarkMetricsTest {
    @Test void percentilesAndGapsHaveExplicitEmptyAndSmallSampleSemantics() {
        var empty = PlotBenchmarkMetrics.distribution(List.of());
        assertTrue(empty.isNull("p99"));
        var result = PlotBenchmarkMetrics.distribution(List.of(16_000_000L, 51_000_000L, 120_000_000L, 300_000_000L));
        assertEquals(51, result.getDouble("p50"));
        assertEquals(300, result.getDouble("p95"));
        assertEquals(3, result.getInt("gaps50"));
        assertEquals(2, result.getInt("gaps100"));
        assertEquals(1, result.getInt("gaps250"));
    }

    @Test void onlyTheLatestRequestReceivesAReadySampleAndSupersededRequestsRemainVisible() {
        var metrics = new PlotBenchmarkMetrics();
        metrics.request(1, 10);
        metrics.request(20, 30); // First request was coalesced, not zero-latency success.
        metrics.redraw(new PlotAreaView.RenderSample(4, false, false), 50);
        metrics.request(60, 70); // Second had a preview but never settled.
        metrics.redraw(new PlotAreaView.RenderSample(5, true, false), 100);
        metrics.redraw(new PlotAreaView.RenderSample(5, true, false), 110); // No double-count.
        var report = metrics.finish();
        assertEquals(3, report.getInt("requests"));
        assertEquals(1, report.getInt("coalescedBeforeCommands"));
        assertEquals(2, report.getInt("supersededBeforeReady"));
        assertEquals(1, report.getJSONObject("requestToReadyCommandsMs").getInt("samples"));
        assertEquals(2, report.getJSONObject("requestToFirstCommandsMs").getInt("samples"));
    }
}
