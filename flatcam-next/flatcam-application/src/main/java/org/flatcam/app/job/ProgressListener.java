package org.flatcam.app.job;

/**
 * Called from whatever thread the job runs on - NOT the JavaFX Application
 * Thread. Callers that update UI from this must marshal back themselves
 * (e.g. {@code Platform.runLater}); this module has no JavaFX dependency.
 */
@FunctionalInterface
public interface ProgressListener {
    void onProgress(double fraction, String message);
}
