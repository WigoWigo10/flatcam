package org.flatcam.fx;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only per-thread CPU counters, not GPU time or statistical JFR samples.
 * Snapshot windows bracket the phase; unavailable/dead/new/reset counters are never zero costs. */
final class PlotBenchmarkThreadCpu {
    record Counter(String name, long cpuNanos, long userNanos) { }
    record Snapshot(long startNanoTime, long endNanoTime, boolean available, Map<Long, Counter> counters) {
        Snapshot { counters = Map.copyOf(counters); }
    }

    static Snapshot sample() {
        long start = System.nanoTime();
        var counters = new LinkedHashMap<Long, Counter>();
        try {
            var bean = ManagementFactory.getThreadMXBean();
            if (!bean.isThreadCpuTimeSupported() || !bean.isThreadCpuTimeEnabled())
                return new Snapshot(start, System.nanoTime(), false, Map.of());
            long[] ids = bean.getAllThreadIds();
            var info = bean.getThreadInfo(ids);
            for (int i = 0; i < ids.length; i++) if (info[i] != null) {
                String name = info[i].getThreadName();
                if (name.equals("main") || name.contains("JavaFX") || name.contains("QuantumRenderer")
                        || name.equals("WindowsNativeRunloopThread") || name.startsWith("plot-")
                        || name.startsWith("ForkJoinPool.commonPool-worker"))
                    counters.put(ids[i], new Counter(name, bean.getThreadCpuTime(ids[i]), bean.getThreadUserTime(ids[i])));
            }
            return new Snapshot(start, System.nanoTime(), true, counters);
        } catch (UnsupportedOperationException | SecurityException unavailable) {
            return new Snapshot(start, System.nanoTime(), false, Map.of());
        }
    }

    static JSONObject delta(Snapshot before, Snapshot after) {
        var result = new JSONObject().put("metric", "PLATFORM_THREAD_CPU_NOT_GPU_TIME")
                .put("status", before.available() && after.available() ? "AVAILABLE" : "UNAVAILABLE")
                .put("beforeSampleStartNanoTime", before.startNanoTime()).put("beforeSampleEndNanoTime", before.endNanoTime())
                .put("afterSampleStartNanoTime", after.startNanoTime()).put("afterSampleEndNanoTime", after.endNanoTime());
        var threads = new JSONArray(); result.put("threads", threads);
        if (!before.available() || !after.available()) return result;
        int incomparable = 0;
        for (var entry : after.counters().entrySet()) {
            var first = before.counters().get(entry.getKey()); var last = entry.getValue();
            if (first == null || !first.name().equals(last.name()) || first.cpuNanos() < 0 || last.cpuNanos() < first.cpuNanos()) {
                incomparable++; continue;
            }
            boolean user = first.userNanos() >= 0 && last.userNanos() >= first.userNanos();
            threads.put(new JSONObject().put("threadId", entry.getKey()).put("name", last.name())
                    .put("cpuMs", (last.cpuNanos() - first.cpuNanos()) / 1e6)
                    .put("userMs", user ? (last.userNanos() - first.userNanos()) / 1e6 : JSONObject.NULL));
        }
        int disappeared = (int) before.counters().keySet().stream().filter(id -> !after.counters().containsKey(id)).count();
        return result.put("newOrInvalidCounters", incomparable).put("disappearedCounters", disappeared);
    }
}
