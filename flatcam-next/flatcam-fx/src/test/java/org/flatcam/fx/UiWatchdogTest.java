package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class UiWatchdogTest {
    private final List<Runnable> probes = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger reports = new AtomicInteger();

    @Test void onlyOneProbeIsQueuedWhileTheUiIsBlocked() {
        try (var monitor = new UiWatchdog(probes::add, reports::incrementAndGet, clock::get, 5)) {
            for (int index = 0; index < 100; index++) monitor.poll();
            assertEquals(1, probes.size()); assertEquals(0, reports.get());
        }
    }
    @Test void reportsOnceAtThresholdAndDoesNotSpamAnOngoingOutage() {
        try (var monitor = new UiWatchdog(probes::add, reports::incrementAndGet, clock::get, 5)) {
            monitor.poll(); clock.set(4); monitor.poll(); assertEquals(0, reports.get());
            clock.set(5); monitor.poll(); assertEquals(1, reports.get());
            clock.set(100); monitor.poll(); assertEquals(1, reports.get());
        }
    }
    @Test void recoveryAllowsAnotherProbeAndAnotherIncident() {
        try (var monitor = new UiWatchdog(probes::add, reports::incrementAndGet, clock::get, 5)) {
            monitor.poll(); clock.set(5); monitor.poll(); probes.getFirst().run();
            monitor.poll(); assertEquals(2, probes.size());
            clock.set(10); monitor.poll(); assertEquals(2, reports.get());
        }
    }
    @Test void respondingUiDoesNotReportEvenAfterAClockAdvance() {
        try (var monitor = new UiWatchdog(Runnable::run, reports::incrementAndGet, clock::get, 5)) {
            monitor.poll(); clock.set(100); monitor.poll(); assertEquals(0, reports.get());
        }
    }
    @Test void shutdownStopsProbesAndReports() {
        var monitor = new UiWatchdog(probes::add, reports::incrementAndGet, clock::get, 5);
        monitor.poll(); monitor.close(); clock.set(100); monitor.poll();
        assertEquals(1, probes.size()); assertEquals(0, reports.get());
    }
    @Test void unavailableToolkitIsNotMistakenForAnUnresponsiveUi() {
        try (var monitor = new UiWatchdog(task -> { throw new IllegalStateException("not running"); },
                reports::incrementAndGet, clock::get, 5)) {
            monitor.poll(); clock.set(100); monitor.poll(); assertEquals(0, reports.get());
        }
    }
}
