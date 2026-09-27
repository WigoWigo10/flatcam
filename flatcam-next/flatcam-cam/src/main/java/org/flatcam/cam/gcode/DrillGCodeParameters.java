package org.flatcam.cam.gcode;

/**
 * Parameters for {@link GCodeGenerator#generateDrillGCode}. All linear values
 * (safeZ, drillDepth, depthPerPass, offsetZ and feedRate) are in the same units as the source
 * ExcellonImage (mm or inch) - no conversion is performed.
 *
 * @param safeZ               retract height above the work surface (positive)
 * @param drillDepth          how far below the surface to plunge (positive; written to G-code as negative Z)
 * @param feedRate            plunge/cut feed rate, units/minute
 * @param spindleSpeedRpm     spindle speed for M3 S...; 0 omits M3/M5 entirely (e.g. a manual/always-on spindle)
 * @param pauseForToolChange  insert M0 (and a comment naming the next tool) between tools
 * @param multiDepth          drill in repeated Z steps, retracting between passes
 * @param depthPerPass        maximum positive Z step when multiDepth is enabled
 * @param dwell               pause after starting the spindle for this tool
 * @param dwellSeconds        G4 P duration, in seconds
 * @param offsetZ             signed extra depth; positive values drill deeper, matching Python's Offset Z
 */
public record DrillGCodeParameters(
        double safeZ,
        double drillDepth,
        double feedRate,
        int spindleSpeedRpm,
        boolean pauseForToolChange,
        boolean multiDepth,
        double depthPerPass,
        boolean dwell,
        double dwellSeconds,
        double offsetZ
) {
    public DrillGCodeParameters(double safeZ, double drillDepth, double feedRate,
                                int spindleSpeedRpm, boolean pauseForToolChange) {
        this(safeZ, drillDepth, feedRate, spindleSpeedRpm, pauseForToolChange,
                false, 0, false, 0, 0);
    }

    public DrillGCodeParameters {
        if (!Double.isFinite(safeZ) || safeZ <= 0) {
            throw new IllegalArgumentException("safeZ must be positive (retract height above the surface): " + safeZ);
        }
        if (!Double.isFinite(drillDepth) || drillDepth <= 0) {
            throw new IllegalArgumentException("drillDepth must be positive (distance below the surface): " + drillDepth);
        }
        if (!Double.isFinite(feedRate) || feedRate <= 0) {
            throw new IllegalArgumentException("feedRate must be positive: " + feedRate);
        }
        if (spindleSpeedRpm < 0) {
            throw new IllegalArgumentException("spindleSpeedRpm cannot be negative: " + spindleSpeedRpm);
        }
        if (!Double.isFinite(offsetZ) || !Double.isFinite(drillDepth + offsetZ)
                || drillDepth + offsetZ <= 0) {
            throw new IllegalArgumentException("Cut Z plus Offset Z must remain below the surface");
        }
        if (!Double.isFinite(depthPerPass) || (multiDepth && depthPerPass <= 0)) {
            throw new IllegalArgumentException("Depth per pass must be positive when Multi-Depth is on");
        }
        if (!Double.isFinite(dwellSeconds) || dwellSeconds < 0) {
            throw new IllegalArgumentException("Dwell time must be finite and nonnegative");
        }
    }

    public double effectiveDepth() {
        return drillDepth + offsetZ;
    }
}
