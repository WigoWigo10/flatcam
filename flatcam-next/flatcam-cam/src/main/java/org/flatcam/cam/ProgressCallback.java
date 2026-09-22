package org.flatcam.cam;

/** Receives monotonic 0.0..1.0 progress updates from a CAM operation. */
@FunctionalInterface
public interface ProgressCallback {

    ProgressCallback NONE = fraction -> {
    };

    void report(double fraction);

    static ProgressCallback none() {
        return NONE;
    }
}
