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
                .put("redrawCommandsMs", distribution(commands)).put("uiPulseIntervalMs", distribution(pulses));
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
