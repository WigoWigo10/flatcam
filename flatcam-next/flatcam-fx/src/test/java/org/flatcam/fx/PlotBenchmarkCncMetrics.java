package org.flatcam.fx;

import java.util.EnumMap;
import org.json.JSONObject;

/** FX-only per-phase counter of CNC pass submissions, not paths/pixels/CPU or GPU time.
 * Raster calls include initial preparation/previews. Decorations count groups, not individual arrows/labels. */
final class PlotBenchmarkCncMetrics {
    private static final class Counts {
        int calls, raster, vector;
        double min = Double.POSITIVE_INFINITY, max;
    }
    private final EnumMap<PlotCncProbe.Pass, Counts> counts = new EnumMap<>(PlotCncProbe.Pass.class);
    void sample(PlotCncProbe.Sample sample) {
        var value = counts.computeIfAbsent(sample.pass(), ignored -> new Counts());
        value.calls++;
        if (sample.raster()) value.raster++; else value.vector++;
        value.min = Math.min(value.min, sample.lineWidth()); value.max = Math.max(value.max, sample.lineWidth());
    }
    JSONObject finish() {
        var result = new JSONObject();
        for (var pass : PlotCncProbe.Pass.values()) {
            var value = counts.get(pass);
            result.put(pass.name(), value == null ? new JSONObject().put("calls", 0)
                    : new JSONObject().put("calls", value.calls).put("rasterCalls", value.raster).put("vectorCalls", value.vector)
                            .put("lineWidthMinLogical", value.min).put("lineWidthMaxLogical", value.max));
        }
        return result;
    }
}
