package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlotAreaPerformanceTest {

    @Test
    void slowRedrawIdentifiesTheCostliestLayersAndSeparatesPhases() {
        String result = PlotAreaPerformance.formatSlowRedraw(100_000_000, 10_000_000,
                70_000_000, 4, List.of(
                        new PlotAreaPerformance.LayerSample("GERBER:front", 50_000_000),
                        new PlotAreaPerformance.LayerSample("GERBER:back", 20_000_000),
                        new PlotAreaPerformance.LayerSample("EXCELLON:drills", 1_000_000),
                        new PlotAreaPerformance.LayerSample("GEOMETRY:extra", 500_000)));

        assertTrue(result.contains("redraw=100.0ms base=10.0ms layers=70.0ms other=20.0ms visibleLayers=4"));
        assertTrue(result.indexOf("GERBER:front") < result.indexOf("GERBER:back"));
        assertFalse(result.contains("GEOMETRY:extra"), "only the three slowest layers are listed");
    }

    @Test
    void disabledProbeCanBeUsedWithoutChangingDefaultRun() {
        PlotAreaPerformance probe = new PlotAreaPerformance(false, 50);
        assertFalse(probe.enabled());
        assertEquals("flatcam.plot.profile", PlotAreaPerformance.ENABLED_PROPERTY);
    }
}
