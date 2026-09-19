package org.flatcam.cam.gcode;

/**
 * @param safeZ           retract height above the work surface (positive)
 * @param cutDepth        how far below the surface to cut (positive; written to G-code as negative Z)
 * @param feedRate        cut feed rate, units/minute
 * @param spindleSpeedRpm spindle speed for M3 S...; 0 omits M3/M5
 */
public record IsolationGCodeParameters(double safeZ, double cutDepth, double feedRate, int spindleSpeedRpm) {
    public IsolationGCodeParameters {
        if (safeZ <= 0) {
            throw new IllegalArgumentException("safeZ must be positive (retract height above the surface): " + safeZ);
        }
        if (cutDepth <= 0) {
            throw new IllegalArgumentException("cutDepth must be positive (distance below the surface): " + cutDepth);
        }
        if (feedRate <= 0) {
            throw new IllegalArgumentException("feedRate must be positive: " + feedRate);
        }
        if (spindleSpeedRpm < 0) {
            throw new IllegalArgumentException("spindleSpeedRpm cannot be negative: " + spindleSpeedRpm);
        }
    }
}
