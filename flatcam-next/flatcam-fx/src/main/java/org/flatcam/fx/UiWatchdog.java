package org.flatcam.fx;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One outstanding UI probe, one report per outage; never queues a growing list of runLater tasks. */
final class UiWatchdog implements AutoCloseable {
    private final Consumer<Runnable> submit;
    private final Runnable stalled;
    private final LongSupplier clock;
    private final long threshold;
    private final ScheduledExecutorService timer;
    private boolean pending;
    private boolean reported;
    private boolean closed;
    private long sent;

    UiWatchdog(Consumer<Runnable> submit, Runnable stalled, LongSupplier clock, long thresholdNanos) {
        this.submit = submit;
        this.stalled = stalled;
        this.clock = clock;
        threshold = thresholdNanos;
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "flatcam-ui-watchdog"); thread.setDaemon(true); return thread;
        });
    }

    void start() { timer.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.SECONDS); }

    synchronized void poll() {
        if (closed) return;
        if (pending) {
            if (!reported && clock.getAsLong() - sent >= threshold) {
                reported = true;
                stalled.run();
            }
            return;
        }
        pending = true; reported = false; sent = clock.getAsLong();
        try {
            submit.accept(() -> {
                synchronized (UiWatchdog.this) { pending = false; }
            });
        } catch (RuntimeException unavailable) {
            pending = false;
        }
    }

    @Override public synchronized void close() { closed = true; timer.shutdownNow(); }
}
