package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotBenchmarkJfrTest {
    @TempDir Path temporary;

    @Test void realRecordingRetainsCompletedAndFailedPhasesAndSeparatesPulseGaps() throws Exception {
        Path path = temporary.resolve("profile.jfr");
        try (var recording = new Recording()) {
            recording.enable(PlotBenchmarkJfr.PHASE).withoutThreshold();
            recording.enable(PlotBenchmarkJfr.GAP);
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ZERO);
            recording.start();
            // Name at thread creation: JFR may retain an existing thread's original metadata after rename.
            var action = new FutureTask<Void>(() -> {
                try (var phase = PlotBenchmarkJfr.phase("zoom-in-out", 1)) {
                    assertFalse(new CountDownLatch(1).await(60, TimeUnit.MILLISECONDS));
                    PlotBenchmarkJfr.pulseGap(60_000_000);
                    phase.completed();
                }
                try (var phase = PlotBenchmarkJfr.phase("geometry-delete-undo", 2)) {
                    assertFalse(new CountDownLatch(1).await(1, TimeUnit.MILLISECONDS));
                    // Deliberately not completed: failures must not become successful phases.
                }
                return null;
            });
            Thread.ofPlatform().name("JavaFX Application Thread").start(action);
            action.get(10, TimeUnit.SECONDS);
            recording.stop(); recording.dump(path);
        }
        var result = PlotBenchmarkJfr.summarize(path);
        assertEquals("ATTRIBUTED", result.getString("status"));
        var phases = result.getJSONArray("phases");
        assertEquals(2, phases.length());
        var zoom = phases.getJSONObject(0);
        assertEquals("zoom-in-out", zoom.getString("name"));
        assertTrue(zoom.getBoolean("completed"));
        assertEquals(1, zoom.getInt("pulseGapsOverlappingPhase"));
        assertTrue(zoom.getDouble("recordedFxThreadParkOverlapMs") > 0);
        assertEquals(0, zoom.getInt("executionSamples")); // No sampler enabled, NOT proof of no CPU work.
        assertEquals(0, phases.getJSONObject(1).getInt("pulseGapsOverlappingPhase"));
        assertFalse(phases.getJSONObject(1).getBoolean("completed"));
    }

    @Test void unmarkedRecordingIsNotReportedAsAttributedAndInvalidInputFails() throws Exception {
        Path path = temporary.resolve("unmarked.jfr");
        try (var recording = new Recording()) {
            recording.start(); recording.stop(); recording.dump(path);
        }
        var result = PlotBenchmarkJfr.summarize(path);
        assertEquals("NO_PHASE_MARKERS", result.getString("status"));
        assertEquals(0, result.getJSONArray("phases").length());
        assertThrows(IOException.class, () -> PlotBenchmarkJfr.summarize(temporary.resolve("missing.jfr")));
    }

    @Test void overlappingPhasesAreRejectedInsteadOfDoubleCountingCpuSamples() throws Exception {
        Path path = temporary.resolve("overlap.jfr");
        try (var recording = new Recording()) {
            recording.start();
            try (var outer = PlotBenchmarkJfr.phase("outer", 1)) {
                try (var inner = PlotBenchmarkJfr.phase("inner", 1)) { inner.completed(); }
                outer.completed();
            }
            recording.stop(); recording.dump(path);
        }
        assertThrows(IOException.class, () -> PlotBenchmarkJfr.summarize(path));
    }

    @Test void summarySnapshotDoesNotStopOrOverwriteTheCliRecording() throws Exception {
        assertThrows(IOException.class, () -> PlotBenchmarkJfr.dumpSummary(temporary));
        try (var recording = new Recording()) {
            recording.setDestination(temporary.resolve("recording.jfr"));
            recording.start();
            try (var phase = PlotBenchmarkJfr.phase("pan", 1)) { phase.completed(); }
            PlotBenchmarkJfr.dumpSummary(temporary);
            assertEquals(RecordingState.RUNNING, recording.getState());
            assertTrue(Files.size(temporary.resolve("phase-profile.jfr")) > 0);
            assertTrue(Files.readString(temporary.resolve("jfr-summary.json")).contains("ATTRIBUTED"));
            assertThrows(IOException.class, () -> PlotBenchmarkJfr.dumpSummary(temporary));
            recording.stop();
        }
        assertTrue(Files.size(temporary.resolve("recording.jfr")) > 0);
    }
}
