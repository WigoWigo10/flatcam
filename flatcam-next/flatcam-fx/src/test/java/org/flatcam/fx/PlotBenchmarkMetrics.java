package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** FX-thread-only recorder for scripted requests. Never treats pulses/commands as presented FPS. */
final class PlotBenchmarkMetrics {
    final List<Long> queue = new ArrayList<>(), firstCommands = new ArrayList<>(), ready = new ArrayList<>();
    final List<Long> commands = new ArrayList<>(), pulses = new ArrayList<>();
    private long requested, lastPulse;
    private boolean first, settled;
    private int requests, coalesced, superseded;

    void request(long issued, long handled) {
        retire();
        requested = issued;
        first = settled = false;
        requests++;
        queue.add(Math.max(0, handled - issued));
    }

    void redraw(PlotAreaView.RenderSample sample, long now) {
        commands.add(sample.commandsNanos());
        if (requested == 0) return;
        if (!first) { firstCommands.add(Math.max(0, now - requested)); first = true; }
        if (sample.ready() && !settled) { ready.add(Math.max(0, now - requested)); settled = true; }
    }

    void pulse(long now) {
        if (lastPulse != 0) {
            long interval = Math.max(0, now - lastPulse);
            pulses.add(interval);
            if (interval >= 50_000_000) PlotBenchmarkJfr.pulseGap(interval);
        }
        lastPulse = now;
    }

    private void retire() {
        if (requested == 0) return;
        if (!first) coalesced++;
        if (!settled) superseded++;
        requested = 0;
    }

    JSONObject finish() {
        retire();
        return new JSONObject().put("requests", requests).put("coalescedBeforeCommands", coalesced)
                .put("supersededBeforeReady", superseded).put("uiQueueMs", distribution(queue))
                .put("requestToFirstCommandsMs", distribution(firstCommands)).put("requestToReadyCommandsMs", distribution(ready))
                .put("redrawCommandsMs", distribution(commands)).put("uiPulseIntervalMs", distribution(pulses))
                .put("uiPulseCadence", cadence(pulses));
    }

    /** Explicit UI Hz, never labelled as GPU/display FPS; 1% low = inverse mean of slowest 1%. */
    static JSONObject cadence(List<Long> intervals) {
        var positive = intervals.stream().filter(value -> value > 0).sorted().toList();
        var result = new JSONObject().put("metric", "UI_PULSE_HZ_NOT_PRESENTED_FPS")
                .put("intervalSamples", positive.size()).put("averageHz", JSONObject.NULL).put("onePercentLowHz", JSONObject.NULL);
        if (!positive.isEmpty()) {
            double average = positive.stream().mapToDouble(Long::doubleValue).average().orElseThrow();
            int slowest = Math.max(1, (int) Math.ceil(positive.size() * .01));
            double slowMean = positive.subList(positive.size() - slowest, positive.size()).stream()
                    .mapToDouble(Long::doubleValue).average().orElseThrow();
            result.put("averageHz", 1e9 / average).put("onePercentLowHz", 1e9 / slowMean);
        }
        return result;
    }

    static JSONObject distribution(List<Long> nanos) {
        var sorted = nanos.stream().sorted().toList();
        var result = new JSONObject().put("samples", sorted.size()).put("rawMs", nanos.stream().map(n -> n / 1e6).toList());
        for (int percentile : new int[]{50, 95, 99})
            result.put("p" + percentile, sorted.isEmpty() ? JSONObject.NULL
                    : sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1)) / 1e6);
        return result.put("max", sorted.isEmpty() ? JSONObject.NULL : sorted.getLast() / 1e6)
                .put("gaps50", nanos.stream().filter(n -> n >= 50_000_000).count())
                .put("gaps100", nanos.stream().filter(n -> n >= 100_000_000).count())
                .put("gaps250", nanos.stream().filter(n -> n >= 250_000_000).count());
    }
}
