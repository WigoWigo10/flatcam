package org.flatcam.cam.ncc;

import java.util.List;
import java.util.Objects;

/**
 * @param toolDiameters   one or more cutter diameters, in the Gerber's units, in the order they were
 *                        entered - {@link #order()}/{@link #restMachining()} decide the actual
 *                        processing order (see NccGenerator)
 * @param overlapFraction 0..1 (not percent) overlap between adjacent passes - shared by every tool
 * @param margin          distance added around the boundary ({@link #boundary()}) to form the
 *                        clearing extent
 * @param method          Standard, Seed, Lines, or Combo fallback - shared by every tool
 * @param connect         join paths when the connecting move remains inside the safe center area
 * @param contour         include a final path around the inside edge of the clearing area
 * @param copperOffset    optional extra keep-out distance around copper; zero means no extra offset
 * @param restMachining   when true, tools are processed largest-first and each one only clears
 *                        whatever the previous, larger tool physically could not reach - see
 *                        NccGenerator's class doc for the exact algorithm
 * @param order           processing order when restMachining is false; ignored (forced
 *                        largest-first) when it's true, matching Python's own behavior
 * @param boundary        what delimits the area to be cleared before subtracting copper - see
 *                        {@link NccBoundary}; defaults to {@code Itself} via the convenience
 *                        constructors below
 *
 * <p>appTools/ToolNCC.py lets overlap/margin/method/connect/contour/copperOffset vary PER TOOL (each
 * row keeps its own copy of these in its "data" dict) when Rest Machining is off, and collapses them
 * to a single shared value (the "rest_ncc_*" widgets) only once Rest Machining is on. This port always
 * shares one set of values across every tool, in both modes - a deliberate v1 simplification, since a
 * full per-tool parameter table is a much larger UI lift than the Rest Machining algorithm itself.
 * Revisit if per-tool tuning turns out to matter in practice.
 */
public record NccParameters(List<Double> toolDiameters, double overlapFraction, double margin,
                            NccMethod method, boolean connect, boolean contour,
                            double copperOffset, boolean restMachining, NccOrder order,
                            NccBoundary boundary) {

    private static final double DUPLICATE_TOLERANCE = 1e-6;

    public NccParameters {
        if (toolDiameters == null || toolDiameters.isEmpty()) {
            throw new IllegalArgumentException("At least one tool diameter is required");
        }
        toolDiameters = List.copyOf(toolDiameters);
        for (double diameter : toolDiameters) {
            if (!Double.isFinite(diameter) || diameter <= 0) {
                throw new IllegalArgumentException("toolDiameter must be positive: " + diameter);
            }
        }
        for (int i = 0; i < toolDiameters.size(); i++) {
            for (int j = i + 1; j < toolDiameters.size(); j++) {
                if (Math.abs(toolDiameters.get(i) - toolDiameters.get(j)) < DUPLICATE_TOLERANCE) {
                    throw new IllegalArgumentException("Duplicate tool diameter: " + toolDiameters.get(i));
                }
            }
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
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(boundary, "boundary");
    }

    /** Convenience defaulting {@link #boundary()} to {@code Itself} - this port's original multi-tool shape. */
    public NccParameters(List<Double> toolDiameters, double overlapFraction, double margin,
                         NccMethod method, boolean connect, boolean contour, double copperOffset,
                         boolean restMachining, NccOrder order) {
        this(toolDiameters, overlapFraction, margin, method, connect, contour, copperOffset,
                restMachining, order, new NccBoundary.Itself());
    }

    /** Convenience for a single tool, non-rest-machining, boundary Itself - this port's original one-tool shape. */
    public NccParameters(double toolDiameter, double overlapFraction, double margin,
                         NccMethod method, boolean connect, boolean contour, double copperOffset) {
        this(List.of(toolDiameter), overlapFraction, margin, method, connect, contour, copperOffset,
                false, NccOrder.NONE, new NccBoundary.Itself());
    }
}
