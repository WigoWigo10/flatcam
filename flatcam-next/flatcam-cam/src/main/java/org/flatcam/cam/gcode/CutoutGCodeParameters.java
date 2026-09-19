package org.flatcam.cam.gcode;

/**
 * @param safeZ         retract height above the work surface (positive)
 * @param cutDepth      how far below the surface to cut (positive; written to G-code as negative Z)
 * @param multiDepth    step down in multiple passes instead of plunging straight to cutDepth - appTools/
 *                      ToolCutOut.py's "Multi-Depth" checkbox (default on) - re-traces the whole path at
 *                      each intermediate Z, which is why a bridge left uncut by this port's Bridge-only
 *                      gap type still gets crossed cleanly at every pass, not just the first
 * @param depthPerPass  maximum Z step per pass when multiDepth is on (positive); ignored otherwise
 * @param feedRate      cut feed rate, units/minute
 * @param spindleSpeedRpm spindle speed for M3 S...; 0 omits M3/M5
 */
public record CutoutGCodeParameters(double safeZ, double cutDepth, boolean multiDepth, double depthPerPass,
                                     double feedRate, int spindleSpeedRpm) {
    public CutoutGCodeParameters {
        if (safeZ <= 0) {
            throw new IllegalArgumentException("safeZ must be positive (retract height above the surface): " + safeZ);
        }
        if (cutDepth <= 0) {
            throw new IllegalArgumentException("cutDepth must be positive (distance below the surface): " + cutDepth);
        }
        if (multiDepth && depthPerPass <= 0) {
            throw new IllegalArgumentException("depthPerPass must be positive when multiDepth is on: " + depthPerPass);
        }
        if (feedRate <= 0) {
            throw new IllegalArgumentException("feedRate must be positive: " + feedRate);
        }
        if (spindleSpeedRpm < 0) {
            throw new IllegalArgumentException("spindleSpeedRpm cannot be negative: " + spindleSpeedRpm);
        }
    }
}
