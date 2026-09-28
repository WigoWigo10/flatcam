package org.flatcam.fx;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Opt-in, JavaFX-thread timing diagnostics for slow canvas redraws. */
final class PlotAreaPerformance {

    static final String ENABLED_PROPERTY = "flatcam.plot.profile";
    static final String SLOW_MS_PROPERTY = "flatcam.plot.profile.slowMs";
    private static final long DEFAULT_SLOW_MS = 50;

    record LayerSample(String name, long nanos) {
    }

    private final boolean enabled;
    private final long slowNanos;
    private long redraws;
    private long slowRedraws;
    private long totalNanos;
    private long maxNanos;

    PlotAreaPerformance(boolean enabled, long slowMs) {
        this.enabled = enabled;
        this.slowNanos = Math.min(Math.max(0, slowMs), Long.MAX_VALUE / 1_000_000) * 1_000_000;
    }

    static PlotAreaPerformance fromSystemProperties() {
        return new PlotAreaPerformance(Boolean.getBoolean(ENABLED_PROPERTY),
                Long.getLong(SLOW_MS_PROPERTY, DEFAULT_SLOW_MS));
    }

    boolean enabled() {
        return enabled;
    }

    void logPhase(String phase, long startNanos) {
        if (enabled) {
            System.err.println("[PLOT-PROFILE] " + phase + "=" + millis(System.nanoTime() - startNanos) + "ms");
        }
    }

    void recordRedraw(long total, long base, long layerTime, int visibleLayers,
                      List<LayerSample> samples) {
        if (!enabled) {
            return;
        }
        redraws++;
        totalNanos += total;
        maxNanos = Math.max(maxNanos, total);
        if (total >= slowNanos) {
            slowRedraws++;
            System.err.println(formatSlowRedraw(total, base, layerTime, visibleLayers, samples));
        }
        if (redraws % 100 == 0) {
            System.err.println("[PLOT-PROFILE] last 100 redraws: avg="
                    + millis(totalNanos / 100) + "ms max=" + millis(maxNanos)
                    + "ms slow=" + slowRedraws);
            totalNanos = 0;
            maxNanos = 0;
            slowRedraws = 0;
        }
    }

    static String formatSlowRedraw(long total, long base, long layerTime, int visibleLayers,
                                   List<LayerSample> samples) {
        StringBuilder line = new StringBuilder("[PLOT-PROFILE] slow redraw=")
                .append(millis(total)).append("ms base=").append(millis(base))
                .append("ms layers=").append(millis(layerTime))
                .append("ms other=").append(millis(Math.max(0, total - base - layerTime)))
                .append("ms visibleLayers=").append(visibleLayers);
        samples.stream().sorted(Comparator.comparingLong(LayerSample::nanos).reversed())
                .limit(3).forEach(sample -> line.append(" | ").append(sample.name())
                        .append('=').append(millis(sample.nanos())).append("ms"));
        return line.toString();
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }
}
