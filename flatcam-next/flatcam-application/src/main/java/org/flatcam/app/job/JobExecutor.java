package org.flatcam.app.job;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs {@link Job}s off the caller's thread - in particular, off the JavaFX
 * Application Thread (CONTEXTO_FLATCAM_FX.md, secao 4.3).
 *
 * <p>Sized to leave headroom for the UI/OS rather than saturating every core
 * (secao 10): {@code max(1, availableProcessors - 1)} worker threads. This is
 * a starting heuristic, not the adaptive scheduler described in secao 10 -
 * that needs job-type/priority awareness this class does not have yet.
 */
public final class JobExecutor {

    private static final Logger LOG = System.getLogger(JobExecutor.class.getName());

    private final ExecutorService executor;

    public JobExecutor() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    public JobExecutor(int workerThreads) {
        this.executor = Executors.newFixedThreadPool(workerThreads, JobExecutor::newDaemonThread);
        LOG.log(Level.INFO, "JobExecutor started with {0} worker thread(s)", workerThreads);
    }

    public <T> JobHandle<T> submit(Job<T> job, ProgressListener progressListener) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<T> completion = new CompletableFuture<>();

        JobContext context = new JobContext() {
            @Override
            public void reportProgress(double fraction, String message) {
                if (progressListener != null) {
                    progressListener.onProgress(fraction, message);
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        executor.submit(() -> {
            try {
                T result = job.run(context);
                if (cancelled.get()) {
                    completion.cancel(false);
                } else {
                    completion.complete(result);
                }
            } catch (InterruptedException e) {
                // Cooperative cancellation via JobContext#checkCancelled(): not a failure.
                completion.cancel(false);
            } catch (CancellationException e) {
                // CAM-core operations use CancellationException so they do not need
                // to depend on this module's checked JobContext contract.
                completion.cancel(false);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Job failed", e);
                completion.completeExceptionally(e);
            }
        });

        return new JobHandle<>(cancelled, completion);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private static Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "flatcam-job-worker");
        thread.setDaemon(true);
        return thread;
    }
}
