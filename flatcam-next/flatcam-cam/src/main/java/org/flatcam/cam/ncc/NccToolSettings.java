package org.flatcam.cam.ncc;

import java.util.Objects;

/** Clearing settings kept by one NCC tool-table row. Margin and boundary remain common.
 * @param lineAngleDegrees null preserves the legacy automatic direction; otherwise counterclockwise
 *                         from horizontal, in [0, 180), for Lines and Combo's Lines attempt.
 */
public record NccToolSettings(double overlapFraction, NccMethod method, boolean connect,
                              boolean contour, double copperOffset, NccSeedPolicy seedPolicy, Double lineAngleDegrees) {
    public NccToolSettings(double overlapFraction, NccMethod method, boolean connect,
                           boolean contour, double copperOffset, NccSeedPolicy seedPolicy) {
        this(overlapFraction,method,connect,contour,copperOffset,seedPolicy,null);
    }
    /** Preserve the FX's existing stable policy for existing callers. */
    public NccToolSettings(double overlapFraction, NccMethod method, boolean connect,
                           boolean contour, double copperOffset) {
        this(overlapFraction,method,connect,contour,copperOffset,NccSeedPolicy.STABLE);
    }

    public NccToolSettings {
        if (!Double.isFinite(overlapFraction) || overlapFraction < 0 || overlapFraction >= 1) {
            throw new IllegalArgumentException("overlapFraction must be in [0, 1): " + overlapFraction);
        }
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(seedPolicy, "seedPolicy");
        if(lineAngleDegrees!=null && (!Double.isFinite(lineAngleDegrees) || lineAngleDegrees<0 || lineAngleDegrees>=180))
            throw new IllegalArgumentException("lineAngleDegrees must be in [0, 180)");
        if (!Double.isFinite(copperOffset) || copperOffset < 0) {
            throw new IllegalArgumentException("copperOffset cannot be negative: " + copperOffset);
        }
    }
}
