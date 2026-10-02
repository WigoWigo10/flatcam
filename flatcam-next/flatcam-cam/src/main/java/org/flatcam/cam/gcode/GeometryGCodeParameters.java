package org.flatcam.cam.gcode;

/**
 * Machining parameters for converting a Geometry object's paths into a CNC
 * Job. May be common or overridden per tool in a multi-tool Geometry.
 * Change/probing and rapid feed remain job-level settings.
 *
 * @param pauseForToolChange execute the profile's change sequence, including the initial tool
 * @param probing explicit Mach3 Z probing configuration; absent for legacy/basic profiles
 */
public record GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                      double depthPerPass, double feedRate, int spindleSpeedRpm,
                                      boolean pauseForToolChange, double rapidFeedRate,
                                      ProbeToolChangeParameters probing, double feedRateZ,
                                      boolean dwell, double dwellSeconds, boolean extraCut, double extraCutLength) {
    public GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                  double depthPerPass, double feedRate, int spindleSpeedRpm,
                                  boolean pauseForToolChange, double rapidFeedRate, ProbeToolChangeParameters probing) {
        this(safeZ, cutDepth, multiDepth, depthPerPass, feedRate, spindleSpeedRpm,
                pauseForToolChange, rapidFeedRate, probing, feedRate, false, 0, false, 0);
    }
    public GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                  double depthPerPass, double feedRate, int spindleSpeedRpm,
                                  boolean pauseForToolChange, double rapidFeedRate) {
        this(safeZ, cutDepth, multiDepth, depthPerPass, feedRate, spindleSpeedRpm, pauseForToolChange, rapidFeedRate, null);
    }
    public GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                  double depthPerPass, double feedRate, int spindleSpeedRpm,
                                  boolean pauseForToolChange) {
        this(safeZ, cutDepth, multiDepth, depthPerPass, feedRate, spindleSpeedRpm, pauseForToolChange, 0);
    }

    public GeometryGCodeParameters {
        if (!Double.isFinite(feedRateZ) || feedRateZ <= 0)
            throw new IllegalArgumentException("Feedrate Z must be positive");
        if (!Double.isFinite(dwellSeconds) || dwellSeconds < 0)
            throw new IllegalArgumentException("Dwell time must be finite and non-negative");
        if (!Double.isFinite(extraCutLength) || extraCutLength < 0)
            throw new IllegalArgumentException("Extra cut length must be finite and non-negative");
        if (!Double.isFinite(rapidFeedRate) || rapidFeedRate < 0) {
            throw new IllegalArgumentException("rapidFeedRate must be zero (automatic) or positive");
        }
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
        if (probing != null) probing.validateTravelZ(safeZ);
    }
}
