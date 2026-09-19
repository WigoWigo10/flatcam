package org.flatcam.app.job;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle to a job submitted to a {@link JobExecutor}: lets the caller ask for
 * cooperative cancellation and observe completion, without exposing the
 * underlying thread/future machinery.
 */
public final class JobHandle<T> {

    private final AtomicBoolean cancelled;
    private final CompletableFuture<T> completion;

    JobHandle(AtomicBoolean cancelled, CompletableFuture<T> completion) {
        this.cancelled = cancelled;
        this.completion = completion;
    }

    /** Requests cancellation. The job body must poll {@link JobContext#isCancelled()} to honor it. */
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Completes with the job's result, or exceptionally on failure/cancellation. */
    public CompletableFuture<T> completion() {
        return completion;
    }
}
