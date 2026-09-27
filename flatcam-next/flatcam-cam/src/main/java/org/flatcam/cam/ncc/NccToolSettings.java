package org.flatcam.cam.ncc;

import java.util.Objects;

/** Clearing settings kept by one NCC tool-table row. Margin and boundary remain common. */
public record NccToolSettings(double overlapFraction, NccMethod method, boolean connect,
                              boolean contour, double copperOffset) {
    public NccToolSettings {
        if (!Double.isFinite(overlapFraction) || overlapFraction < 0 || overlapFraction >= 1) {
            throw new IllegalArgumentException("overlapFraction must be in [0, 1): " + overlapFraction);
        }
        Objects.requireNonNull(method, "method");
        if (!Double.isFinite(copperOffset) || copperOffset < 0) {
            throw new IllegalArgumentException("copperOffset cannot be negative: " + copperOffset);
        }
    }
}
