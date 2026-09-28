package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Optional UI-thread heartbeat; it does not measure GPU presentation FPS. */
final class UiFluidityMetrics {

    static final String ENABLED_PROPERTY = "flatcam.ui.fluidity";
    private static final long WINDOW_NANOS = 10_000_000_000L;

    private final String appName;
    private final long windowNanos;
    private final List<Double> intervalsMs = new ArrayList<>();
    private long windowStart;
    private long lastTick;

    UiFluidityMetrics(String appName) {
        this(appName, WINDOW_NANOS);
    }

    UiFluidityMetrics(String appName, long windowNanos) {
        this.appName = appName;
        this.windowNanos = windowNanos;
    }

    void reset() {
        windowStart = 0;
        lastTick = 0;
        intervalsMs.clear();
    }

    String tick(long nowNanos) {
        if (lastTick == 0) {
            lastTick = nowNanos;
            windowStart = nowNanos;
            return null;
        }
        if (nowNanos < lastTick) {
            reset();
            return null;
        }
        intervalsMs.add((nowNanos - lastTick) / 1_000_000.0);
        lastTick = nowNanos;
        long elapsed = nowNanos - windowStart;
        if (elapsed < windowNanos) {
            return null;
        }

        Collections.sort(intervalsMs);
        int count = intervalsMs.size();
        long gaps50 = intervalsMs.stream().filter(value -> value >= 50).count();
        long gaps100 = intervalsMs.stream().filter(value -> value >= 100).count();
        long gaps250 = intervalsMs.stream().filter(value -> value >= 250).count();
        String report = String.format(Locale.ROOT,
                "[UI-FLUIDITY] app=%s window_s=%.1f samples=%d "
                + "p50_ms=%.1f p95_ms=%.1f p99_ms=%.1f max_ms=%.1f "
                + "gaps50=%d gaps100=%d gaps250=%d",
                appName, elapsed / 1_000_000_000.0, count,
                percentile(0.50), percentile(0.95), percentile(0.99),
                intervalsMs.get(count - 1), gaps50, gaps100, gaps250);
        windowStart = nowNanos;
        intervalsMs.clear();
        return report;
    }

    private double percentile(double fraction) {
        return intervalsMs.get(Math.max(0, (int) Math.ceil(fraction * intervalsMs.size()) - 1));
    }
}
