package org.flatcam.cam.gcode;

/**
 * Parameters for {@link GCodeGenerator#generateDrillGCode}. All linear values
 * (safeZ, drillDepth, feedRate) are in the same units as the source
 * ExcellonImage (mm or inch) - no conversion is performed.
 *
 * @param safeZ               retract height above the work surface (positive)
 * @param drillDepth          how far below the surface to plunge (positive; written to G-code as negative Z)
 * @param feedRate            plunge/cut feed rate, units/minute
 * @param spindleSpeedRpm     spindle speed for M3 S...; 0 omits M3/M5 entirely (e.g. a manual/always-on spindle)
 * @param pauseForToolChange  insert M0 (and a comment naming the next tool) between tools
 */
public record DrillGCodeParameters(
        double safeZ,
        double drillDepth,
        double feedRate,
        int spindleSpeedRpm,
        boolean pauseForToolChange
) {
    public DrillGCodeParameters {
        if (safeZ <= 0) {
            throw new IllegalArgumentException("safeZ must be positive (retract height above the surface): " + safeZ);
        }
        if (drillDepth <= 0) {
            throw new IllegalArgumentException("drillDepth must be positive (distance below the surface): " + drillDepth);
        }
        if (feedRate <= 0) {
            throw new IllegalArgumentException("feedRate must be positive: " + feedRate);
        }
        if (spindleSpeedRpm < 0) {
            throw new IllegalArgumentException("spindleSpeedRpm cannot be negative: " + spindleSpeedRpm);
        }
    }
}
