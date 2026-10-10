package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.RecordingState;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.json.JSONArray;
import org.json.JSONObject;

/** Test-only phase markers and offline CPU sample attribution. No JFR work on normal Plot redraws.
 * Samples and overlapping recorded waits/GC are diagnostics, NOT GPU time, FPS or causal proof. */
final class PlotBenchmarkJfr {
    static final String PHASE = "org.flatcam.plot.BenchmarkPhase";
    static final String GAP = "org.flatcam.plot.BenchmarkPulseGap";

    @Name(PHASE) @Label("Plot benchmark phase") @Category("FlatCAM Plot") @StackTrace(false)
    static class PhaseEvent extends Event {
        String phase;
        int repeat;
        boolean completed;
    }

    @Name(GAP) @Label("Plot UI pulse interval >= 50 ms") @Category("FlatCAM Plot") @StackTrace(false)
    static class GapEvent extends Event { long intervalNanos; }

    static final class Marker implements AutoCloseable {
        private final PhaseEvent event = new PhaseEvent();
        Marker(String phase, int repeat) { event.phase = phase; event.repeat = repeat; event.begin(); }
        void completed() { event.completed = true; }
        @Override public void close() { event.end(); event.commit(); }
    }

    static Marker phase(String name, int repeat) { return new Marker(name, repeat); }
    static void pulseGap(long intervalNanos) {
        var event = new GapEvent();
        if (event.isEnabled()) { event.intervalNanos = intervalNanos; event.commit(); }
    }

    /** Dump after measured phases, while the CLI recording is still running. Do not stop/own it.
     * The dump is distinct from dumponexit recording.jfr; later summary work is not in measured phases. */
    static void dumpSummary(Path output) throws IOException {
        Path destination = output.resolve("recording.jfr").toAbsolutePath().normalize();
        var recording = FlightRecorder.getFlightRecorder().getRecordings().stream()
                .filter(r -> r.getState() == RecordingState.RUNNING && r.getDestination() != null
                        && r.getDestination().toAbsolutePath().normalize().equals(destination))
                .findFirst().orElseThrow(() -> new IOException("Requested benchmark JFR recording not running"));
        Path snapshot = output.resolve("phase-profile.jfr");
        if (Files.exists(snapshot)) throw new IOException("Refusing to overwrite " + snapshot);
        recording.dump(snapshot);
        Files.writeString(output.resolve("jfr-summary.json"), summarize(snapshot).toString(2), StandardOpenOption.CREATE_NEW);
    }

    private record Interval(Instant start, Instant end) {
        boolean contains(Instant time) { return !time.isBefore(start) && time.isBefore(end); }
        long overlap(Instant otherStart, Instant otherEnd) {
            Instant a = start.isAfter(otherStart) ? start : otherStart;
            Instant b = end.isBefore(otherEnd) ? end : otherEnd;
            return b.isAfter(a) ? Duration.between(a, b).toNanos() : 0;
        }
    }

    private static final class Phase {
        final Interval interval;
        final String name;
        final int repeat;
        final boolean completed;
        final Map<String, Long> threads = new LinkedHashMap<>(), topFrames = new LinkedHashMap<>();
        final List<Interval> gaps = new ArrayList<>();
        long samples, marlin, prism, density, duringGap, gcNanos, fxParkNanos, quantumParkNanos;
        Phase(RecordedEvent event) {
            interval = new Interval(event.getStartTime(), event.getEndTime());
            name = event.getString("phase"); repeat = event.getInt("repeat"); completed = event.getBoolean("completed");
        }
        JSONObject json() {
            return new JSONObject().put("name", name).put("repeat", repeat).put("completed", completed)
                    .put("startTime", interval.start.toString()).put("endTime", interval.end.toString())
                    .put("durationMs", Duration.between(interval.start, interval.end).toNanos() / 1e6)
                    .put("executionSamples", samples).put("samplesByThread", new JSONObject(threads))
                    .put("samplesWithMarlinFrame", marlin).put("samplesWithPrismFrame", prism)
                    .put("samplesWithDensityRasterFrame", density).put("samplesDuringPulseGap", duringGap)
                    .put("pulseGapsOverlappingPhase", gaps.size()).put("topSampledFrames", top(topFrames, 10))
                    .put("recordedGcPauseOverlapMs", gcNanos / 1e6)
                    .put("recordedFxThreadParkOverlapMs", fxParkNanos / 1e6)
                    .put("recordedQuantumThreadParkOverlapMs", quantumParkNanos / 1e6);
        }
    }

    static JSONObject summarize(Path path) throws IOException {
        var phases = new ArrayList<Phase>();
        var gaps = new ArrayList<Interval>();
        // Two streaming passes: bounded retained metadata, not every sampled stack in memory.
        read(path, event -> {
            String type = event.getEventType().getName();
            if (type.equals(PHASE)) phases.add(new Phase(event));
            if (type.equals(GAP)) {
                long nanos = event.getLong("intervalNanos");
                if (nanos >= 50_000_000) gaps.add(new Interval(event.getStartTime().minusNanos(nanos), event.getStartTime()));
            }
        });
        phases.sort(Comparator.comparing(p -> p.interval.start));
        for (int i = 1; i < phases.size(); i++)
            if (phases.get(i).interval.start.isBefore(phases.get(i - 1).interval.end))
                throw new IOException("Benchmark phases overlap; cannot attribute samples uniquely");
        for (var phase : phases) for (var gap : gaps)
            if (phase.interval.overlap(gap.start, gap.end) > 0) phase.gaps.add(gap);
        long[] outside = {0};
        read(path, event -> {
            String type = event.getEventType().getName();
            if (type.equals("jdk.ExecutionSample")) {
                var phase = phases.stream().filter(p -> p.interval.contains(event.getStartTime())).findFirst().orElse(null);
                if (phase == null) { outside[0]++; return; }
                phase.samples++;
                var thread = event.getThread("sampledThread");
                String threadName = thread == null ? "unknown" : thread.getJavaName();
                phase.threads.merge(threadName == null ? "unknown" : threadName, 1L, Long::sum);
                if (phase.gaps.stream().anyMatch(gap -> gap.contains(event.getStartTime()))) phase.duringGap++;
                var stack = event.getStackTrace();
                if (stack == null || stack.getFrames().isEmpty()) return;
                boolean marlin = false, prism = false, density = false;
                for (var frame : stack.getFrames()) {
                    String owner = frame.getMethod().getType().getName();
                    marlin |= owner.startsWith("com.sun.marlin.");
                    prism |= owner.startsWith("com.sun.prism.");
                    density |= owner.equals("org.flatcam.fx.DensityRaster");
                }
                if (marlin) phase.marlin++; if (prism) phase.prism++; if (density) phase.density++;
                var method = stack.getFrames().getFirst().getMethod();
                phase.topFrames.merge(method.getType().getName() + "." + method.getName(), 1L, Long::sum);
            } else if (type.equals("jdk.GCPhasePause") || type.equals("jdk.ThreadPark")) {
                for (var phase : phases) {
                    long overlap = phase.interval.overlap(event.getStartTime(), event.getEndTime());
                    if (type.equals("jdk.GCPhasePause")) phase.gcNanos += overlap;
                    else if (event.getThread() != null) {
                        String thread = event.getThread().getJavaName();
                        if ("JavaFX Application Thread".equals(thread)) phase.fxParkNanos += overlap;
                        if (thread != null && thread.startsWith("QuantumRenderer")) phase.quantumParkNanos += overlap;
                    }
                }
            }
        });
        return new JSONObject().put("schema", 1).put("recording", path.getFileName().toString())
                .put("status", phases.isEmpty() ? "NO_PHASE_MARKERS" : "ATTRIBUTED")
                .put("limits", List.of("CPU statistical samples, not GPU time or percentages",
                        "stack categories can overlap; samples are not additive costs",
                        "pulse gap endpoint is approximate; overlap is not causation",
                        "recorded waits and GC depend on JFR thresholds; absence does not prove zero",
                        "zero samples in a short phase means insufficient evidence, not zero cost"))
                .put("executionSamplesOutsideMarkedPhases", outside[0])
                .put("phases", new JSONArray(phases.stream().map(Phase::json).toList()));
    }

    private static JSONArray top(Map<String, Long> counts, int limit) {
        return new JSONArray(counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit).map(e -> new JSONObject().put("method", e.getKey()).put("samples", e.getValue())).toList());
    }

    private static void read(Path path, java.util.function.Consumer<RecordedEvent> consumer) throws IOException {
        try (var recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) consumer.accept(recording.readEvent());
        }
    }
}
