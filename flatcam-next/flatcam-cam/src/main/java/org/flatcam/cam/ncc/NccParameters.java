package org.flatcam.cam.ncc;

import java.util.List;
import java.util.Map;
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
 * @param isolationToolDiameters selected tools marked ISO in the Python NCC table; they create
 *                        isolation contours before the CLEAR tools run
 * @param toolSettings    per-CLEAR-tool settings; omitted tools use the common defaults above
 * @param millingType     direction of ISO exterior contours (global for the NCC operation)
 *
 * <p>appTools/ToolNCC.py uses per-tool overlap/method/connect/contour/copperOffset
 * when Rest Machining is off, and shared rest settings when it is on. Margin is
 * read from the common NCC field. This port still shares one set of clearing
 * parameters as defaults and accepts per-tool overrides.
 */
public record NccParameters(List<Double> toolDiameters, double overlapFraction, double margin,
                            NccMethod method, boolean connect, boolean contour,
                            double copperOffset, boolean restMachining, NccOrder order,
                            NccBoundary boundary, List<Double> isolationToolDiameters,
                            Map<Double, NccToolSettings> toolSettings, NccMillingType millingType) {

    private static final double DUPLICATE_TOLERANCE = 1e-6;

    public NccParameters {
        if (toolDiameters == null || toolDiameters.isEmpty()) {
            throw new IllegalArgumentException("At least one tool diameter is required");
        }
        toolDiameters = List.copyOf(toolDiameters);
        isolationToolDiameters = List.copyOf(isolationToolDiameters);
        List<Double> allDiameters = java.util.stream.Stream.concat(toolDiameters.stream(),
                isolationToolDiameters.stream()).toList();
        for (double diameter : allDiameters) {
            if (!Double.isFinite(diameter) || diameter <= 0) {
                throw new IllegalArgumentException("toolDiameter must be positive: " + diameter);
            }
        }
        for (int i = 0; i < allDiameters.size(); i++) {
            for (int j = i + 1; j < allDiameters.size(); j++) {
                if (Math.abs(allDiameters.get(i) - allDiameters.get(j)) < DUPLICATE_TOLERANCE) {
                    throw new IllegalArgumentException("Duplicate tool diameter: " + allDiameters.get(i));
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
        Objects.requireNonNull(millingType, "millingType");
        toolSettings = Map.copyOf(toolSettings);
        for (Double diameter : toolSettings.keySet()) {
            if (!toolDiameters.contains(diameter)) {
                throw new IllegalArgumentException("Settings for unknown CLEAR tool: " + diameter);
            }
        }
    }

    public NccParameters(List<Double> toolDiameters, double overlapFraction, double margin,
                         NccMethod method, boolean connect, boolean contour, double copperOffset,
                         boolean restMachining, NccOrder order, NccBoundary boundary,
                         List<Double> isolationToolDiameters, Map<Double, NccToolSettings> toolSettings) {
        this(toolDiameters, overlapFraction, margin, method, connect, contour, copperOffset,
                restMachining, order, boundary, isolationToolDiameters, toolSettings, NccMillingType.CLIMB);
    }

    /** Per-tool overlap/method always apply; Rest Machining shares connect/contour/offset. */
    public NccToolSettings settingsFor(double diameter) {
        NccToolSettings defaults = new NccToolSettings(overlapFraction, method, connect, contour, copperOffset);
        NccToolSettings selected = toolSettings.getOrDefault(diameter, defaults);
        return restMachining
                ? new NccToolSettings(selected.overlapFraction(), selected.method(),
                        connect, contour, copperOffset, selected.seedPolicy())
                : selected;
    }

    public NccParameters(List<Double> toolDiameters, double overlapFraction, double margin,
                         NccMethod method, boolean connect, boolean contour, double copperOffset,
                         boolean restMachining, NccOrder order, NccBoundary boundary,
                         List<Double> isolationToolDiameters) {
        this(toolDiameters, overlapFraction, margin, method, connect, contour, copperOffset,
                restMachining, order, boundary, isolationToolDiameters, Map.of());
    }

    public NccParameters(List<Double> toolDiameters, double overlapFraction, double margin,
                         NccMethod method, boolean connect, boolean contour, double copperOffset,
                         boolean restMachining, NccOrder order, NccBoundary boundary) {
        this(toolDiameters, overlapFraction, margin, method, connect, contour, copperOffset,
                restMachining, order, boundary, List.of());
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
