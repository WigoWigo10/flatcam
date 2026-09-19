package org.flatcam.app.job;

/**
 * What a {@link Job} body is given to report progress and check for
 * cooperative cancellation. There is no way to force-kill a job (see
 * CONTEXTO_FLATCAM_FX.md, secao 4.3: "cancelamento cooperativo") - a job must
 * poll {@link #isCancelled()} at safe points and return/throw promptly.
 */
public interface JobContext {

    /**
     * @param fraction 0.0..1.0, or {@link Double#NaN} for "progress unknown"
     * @param message  short human-readable status, may be empty
     */
    void reportProgress(double fraction, String message);

    boolean isCancelled();

    /**
     * Convenience for loops: throws if the job should stop now.
     */
    default void checkCancelled() throws InterruptedException {
        if (isCancelled()) {
            throw new InterruptedException("Job cancelled");
        }
    }
}
