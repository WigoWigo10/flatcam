package org.flatcam.cam.ncc;

import java.util.List;
import java.util.Objects;

/**
 * appTools/ToolPaint.py's settings for painting (filling) polygons with toolpaths.
 *
 * @param toolDiameters   one or more cutter diameters, in the polygons' units
 * @param overlapFraction 0..1 overlap between adjacent passes
 * @param offset          Python's "Margin": every polygon shrinks by this before it is painted
 * @param method          Standard, Seed, Lines or Combo (Python's Laser Lines needs Gerber apertures and is not offered)
 * @param connect         join paths when the connecting move stays inside the polygon
 * @param contour         also cut once around the inside edge of the polygon
 * @param order           processing order of the tools (ignored when rest machining: largest first)
 * @param restMachining   each smaller tool only paints what the larger ones could not reach
 */
public record PaintParameters(List<Double> toolDiameters, double overlapFraction, double offset, NccMethod method,
                              boolean connect, boolean contour, NccOrder order, boolean restMachining) {

    public PaintParameters {
        if (toolDiameters == null || toolDiameters.isEmpty()) {
            throw new IllegalArgumentException("At least one tool diameter is required");
        }
        toolDiameters = List.copyOf(toolDiameters);
        for (double diameter : toolDiameters) {
            if (!Double.isFinite(diameter) || diameter <= 0) {
                throw new IllegalArgumentException("toolDiameter must be positive: " + diameter);
            }
        }
        if (!Double.isFinite(overlapFraction) || overlapFraction < 0 || overlapFraction >= 1) {
            throw new IllegalArgumentException("overlapFraction must be in [0, 1): " + overlapFraction);
        }
        if (!Double.isFinite(offset) || offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative: " + offset);
        }
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(order, "order");
    }

    NccToolSettings settings() {
        return new NccToolSettings(overlapFraction, method, connect, contour, 0);
    }
}
