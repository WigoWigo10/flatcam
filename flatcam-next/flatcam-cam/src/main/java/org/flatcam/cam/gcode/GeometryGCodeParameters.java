package org.flatcam.cam.gcode;

/** Machining parameters for converting a Geometry object's paths into a CNC Job. */
public record GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                      double depthPerPass, double feedRate, int spindleSpeedRpm) {
    public GeometryGCodeParameters {
        if (!Double.isFinite(safeZ) || safeZ <= 0) {
            throw new IllegalArgumentException("safeZ must be positive: " + safeZ);
        }
        if (!Double.isFinite(cutDepth) || cutDepth <= 0) {
            throw new IllegalArgumentException("cutDepth must be positive: " + cutDepth);
        }
        if (multiDepth && (!Double.isFinite(depthPerPass) || depthPerPass <= 0)) {
            throw new IllegalArgumentException("depthPerPass must be positive when multiDepth is on: " + depthPerPass);
        }
        if (!Double.isFinite(feedRate) || feedRate <= 0) {
            throw new IllegalArgumentException("feedRate must be positive: " + feedRate);
        }
        if (spindleSpeedRpm < 0) {
            throw new IllegalArgumentException("spindleSpeedRpm cannot be negative: " + spindleSpeedRpm);
        }
    }
}
