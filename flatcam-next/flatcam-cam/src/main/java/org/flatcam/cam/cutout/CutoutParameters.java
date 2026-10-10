package org.flatcam.cam.cutout;

/**
 * @param toolDiameter in the same units as the source Gerber (mm or inch)
 * @param margin       gap between the actual copper/outline and the cut path, before adding the
 *                     tool radius (appTools/ToolCutOut.py's "Margin" field) - may be negative for
 *                     FREEFORM (cutting inside the source's own boundary), but not RECTANGULAR,
 *                     which Python explicitly rejects for the same reason a buffered box can turn
 *                     inside-out at a negative-enough distance
 * @param convexShape  replace the source outline with its convex hull before anything else -
 *                     Python disables this checkbox for a Geometry source; this port only takes a
 *                     Gerber source (see CutoutGenerator's class doc), so it's always applicable
 * @param kind         SINGLE (one outline for the whole source) or PANEL (one per disjoint part)
 * @param shape        FREEFORM (trace the real outline) or RECTANGULAR (cut its bounding box)
 * @param gapSize      the bridge's own width, in file units - not yet including the tool radius
 *                     (see CutoutGenerator.buildGapBands())
 * @param gapPattern   automatic bridge placement - see {@link GapPattern}
 * @param includeInternalCuts opt-in closed internal profiles, without bridges. Requires a filled
 *                     board outline, FREEFORM, no convex hull and a nonnegative margin.
 */
public record CutoutParameters(double toolDiameter, double margin, boolean convexShape, CutoutKind kind,
                                CutoutShape shape, double gapSize, GapPattern gapPattern, boolean includeInternalCuts) {
    /** Preserve legacy perimeter-only callers and database settings. */
    public CutoutParameters(double toolDiameter, double margin, boolean convexShape, CutoutKind kind,
                            CutoutShape shape, double gapSize, GapPattern gapPattern) {
        this(toolDiameter, margin, convexShape, kind, shape, gapSize, gapPattern, false);
    }

    public CutoutParameters {
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(shape, "shape");
        java.util.Objects.requireNonNull(gapPattern, "gapPattern");
        if (!Double.isFinite(toolDiameter) || toolDiameter <= 0) {
            throw new IllegalArgumentException("toolDiameter must be positive: " + toolDiameter);
        }
        if (!Double.isFinite(margin)) {
            throw new IllegalArgumentException("margin must be finite: " + margin);
        }
        if (!Double.isFinite(gapSize) || gapSize < 0) {
            throw new IllegalArgumentException("gapSize must not be negative: " + gapSize);
        }
        if (shape == CutoutShape.RECTANGULAR && margin < 0) {
            throw new IllegalArgumentException("Rectangular cutout with negative margin is not possible");
        }
        if (includeInternalCuts && (shape != CutoutShape.FREEFORM || convexShape || margin < 0)) {
            throw new IllegalArgumentException("Recortes internos exigem Free-form, sem Convex Shape e margem nao negativa.");
        }
    }
}
