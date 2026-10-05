package org.flatcam.fx;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.ProgressListener;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;

/** Worker-scoped context; never blocks the FX thread waiting for a worker. */
final class TclExecution {
    private record Context(CancellationToken cancellation, ProgressListener progress) { }
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private TclExecution() { }

    static <T> T run(JobContext job, Callable<T> action) throws Exception {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(job::isCancelled, job::reportProgress));
        try { return action.call(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    static CancellationToken cancellation() {
        Context context = CURRENT.get();
        return context == null ? CancellationToken.none() : context.cancellation();
    }

    static ProgressCallback progress(String phase) {
        Context context = CURRENT.get();
        return context == null ? ProgressCallback.none() : fraction -> context.progress().onProgress(fraction, phase);
    }

    static void phase(String message) {
        Context context = CURRENT.get();
        if (context != null) context.progress().onProgress(Double.NaN, message);
        cancellation().throwIfCancellationRequested();
    }

    static <T> T onFx(Supplier<T> action) {
        CancellationToken cancellation = cancellation();
        Supplier<T> checked = () -> {
            cancellation.throwIfCancellationRequested();
            return action.get();
        };
        if (Platform.isFxApplicationThread()) return checked.get();
        FutureTask<T> task = new FutureTask<>(checked::get);
        Platform.runLater(task);
        try {
            while (true) {
                cancellation.throwIfCancellationRequested();
                try { return task.get(50, TimeUnit.MILLISECONDS); }
                catch (TimeoutException waitingForFx) { /* cancellation remains responsive */ }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Terminal interrompido.");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failure.getCause());
        } finally { task.cancel(false); }
    }
}
