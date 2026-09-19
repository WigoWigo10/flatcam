package org.flatcam.cam.isolation;

/**
 * @param toolDiameter    in the same units as the source Gerber (mm or inch)
 * @param passes          number of concentric isolation passes (1 = single pass)
 * @param overlapFraction 0..1 (not a percent) - how much each pass beyond the first overlaps the previous one;
 *                        see IsolationGenerator's doc for the exact offset formula
 * @param type            which rings to keep (exterior/interior/both)
 */
public record IsolationParameters(double toolDiameter, int passes, double overlapFraction, IsolationType type) {
    public IsolationParameters {
        if (toolDiameter <= 0) {
            throw new IllegalArgumentException("toolDiameter must be positive: " + toolDiameter);
        }
        if (passes < 1) {
            throw new IllegalArgumentException("passes must be at least 1: " + passes);
        }
        if (overlapFraction < 0 || overlapFraction >= 1) {
            throw new IllegalArgumentException("overlapFraction must be in [0, 1): " + overlapFraction);
        }
    }
}
