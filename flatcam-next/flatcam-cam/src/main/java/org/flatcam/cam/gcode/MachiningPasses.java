package org.flatcam.cam.gcode;

import java.util.ArrayList;
import java.util.List;

/** Counts configured depth steps without accumulating binary rounding into an extra final pass. */
public final class MachiningPasses {
    public static int count(double depth, boolean multiple, double step) {
        if (!Double.isFinite(depth) || depth <= 0) throw new IllegalArgumentException("Cut depth must be positive");
        if (!multiple) return 1;
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Depth per pass must be positive");
        double ratio = depth / step;
        double integer = Math.rint(ratio);
        // Only binary arithmetic noise (four ULPs), not a machining/coordinate tolerance.
        if (Double.isFinite(ratio) && Math.abs(ratio - integer) <= 4 * Math.ulp(ratio)) ratio = integer;
        double count = Math.max(1, Math.ceil(ratio));
        if (!Double.isFinite(count) || count > 10_000)
            throw new IllegalArgumentException("Depth per pass would require over 10000 passes");
        return (int) count;
    }
    static List<Double> depths(double depth, boolean multiple, double step) {
        int count = count(depth, multiple, step);
        var depths = new ArrayList<Double>(count);
        for (int pass = 1; pass < count; pass++) depths.add(pass * step);
        depths.add(depth); // Exact requested final depth, not the rounded product.
        return depths;
    }
    private MachiningPasses() { }
}
