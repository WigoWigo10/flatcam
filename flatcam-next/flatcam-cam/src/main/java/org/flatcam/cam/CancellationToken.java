package org.flatcam.cam;

import java.util.concurrent.CancellationException;

/**
 * Dependency-free cooperative cancellation contract for CAM algorithms.
 * Callers such as the JavaFX job layer adapt their own cancellation state
 * through a method reference; the CAM core remains independent of JavaFX and
 * flatcam-application.
 */
@FunctionalInterface
public interface CancellationToken {

    CancellationToken NONE = () -> false;

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationException("CAM operation cancelled");
        }
    }

    static CancellationToken none() {
        return NONE;
    }
}
