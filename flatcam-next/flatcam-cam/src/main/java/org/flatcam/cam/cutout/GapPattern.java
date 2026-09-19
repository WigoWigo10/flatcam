package org.flatcam.cam.cutout;

/**
 * appTools/ToolCutOut.py's "Gaps" combo (automatic bridge placement, keyed
 * to the outline's own bounding box - the manual click-to-place workflow is
 * a separate, not-yet-ported feature). Each pattern is one or two full-span
 * bands across the outline: a band centered on the bbox's vertical midpoint
 * spanning the full width crosses a (roughly convex) outline exactly twice -
 * once on its left side, once on its right - so LR/TB need only one band
 * each to produce two bridges, not two separate rectangles. See
 * CutoutGenerator.buildGapBands() for the exact geometry.
 */
public enum GapPattern {
    /** No automatic gaps. */
    NONE,
    /** One horizontal band at the vertical midpoint - crosses the outline's left and right sides. */
    LR,
    /** One vertical band at the horizontal midpoint - crosses the outline's top and bottom sides. */
    TB,
    /** LR + TB - one bridge per side of the bounding box (4 total). */
    FOUR,
    /** Two horizontal bands at the vertical quarter-points (4 bridges: 2 left, 2 right). */
    TWO_LR,
    /** Two vertical bands at the horizontal quarter-points (4 bridges: 2 top, 2 bottom). */
    TWO_TB,
    /** TWO_LR + TWO_TB (8 total). */
    EIGHT
}
