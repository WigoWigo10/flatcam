package org.flatcam.cam.ncc;

import java.util.Objects;

/**
 * Parameters for one-tool non-copper clearing.
 *
 * @param toolDiameter    cutter diameter in the Gerber's units
 * @param overlapFraction 0..1 (not percent) overlap between adjacent passes
 * @param margin           distance added around the Gerber convex hull to form the clearing boundary
 * @param method           Standard, Seed, Lines, or Combo fallback
 * @param connect          join paths when the connecting move remains inside the safe center area
 * @param contour          include a final path around the inside edge of the clearing area
 * @param copperOffset     optional extra keep-out distance around copper; zero means no extra offset
 */
public record NccParameters(double toolDiameter, double overlapFraction, double margin,
                            NccMethod method, boolean connect, boolean contour,
                            double copperOffset) {
    public NccParameters {
        if (!Double.isFinite(toolDiameter) || toolDiameter <= 0) {
            throw new IllegalArgumentException("toolDiameter must be positive: " + toolDiameter);
        }
        if (!Double.isFinite(overlapFraction) || overlapFraction < 0 || overlapFraction >= 1) {
            throw new IllegalArgumentException("overlapFraction must be in [0, 1): " + overlapFraction);
        }
        if (!Double.isFinite(margin) || margin < 0) {
            throw new IllegalArgumentException("margin cannot be negative: " + margin);
        }
        if (!Double.isFinite(copperOffset) || copperOffset < 0) {
            throw new IllegalArgumentException("copperOffset cannot be negative: " + copperOffset);
        }
        Objects.requireNonNull(method, "method");
    }
}
