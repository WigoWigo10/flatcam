package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiFluidityMetricsTest {

    @Test
    void reportsTailLatenciesAndStalls() {
        UiFluidityMetrics metrics = new UiFluidityMetrics("fx", 300_000_000L);
        assertNull(metrics.tick(1_000_000_000L));
        assertNull(metrics.tick(1_016_000_000L));
        assertNull(metrics.tick(1_032_000_000L));
        String report = metrics.tick(1_300_000_000L);
        assertTrue(report.contains("app=fx"));
        assertTrue(report.contains("samples=3"));
        assertTrue(report.contains("p50_ms=16.0"));
        assertTrue(report.contains("p95_ms=268.0"));
        assertTrue(report.contains("max_ms=268.0"));
        assertTrue(report.contains("gaps50=1 gaps100=1 gaps250=1"));
    }

    @Test
    void resetDiscardsHiddenWindowGap() {
        UiFluidityMetrics metrics = new UiFluidityMetrics("fx", 20_000_000L);
        metrics.tick(1_000_000_000L);
        metrics.reset();
        assertNull(metrics.tick(100_000_000_000L));
        assertTrue(metrics.tick(100_020_000_000L).contains("gaps50=0"));
    }
}
