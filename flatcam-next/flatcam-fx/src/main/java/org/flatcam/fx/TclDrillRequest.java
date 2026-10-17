package org.flatcam.fx;

import java.util.List;
import org.flatcam.cam.gcode.GCodePreprocessor;

/**
 * Parsed, unit-free arguments of Python's {@code drillcncjob} (TclCommandDrillcncjob.py), already
 * validated as finite and normalized to this port's conventions: {@code drillDepth} is the positive
 * distance below the surface (Python's {@code -drillz} is negative), toolchange is on only when
 * {@code -toolchangez} was given, and an empty {@code drilledDiameters} means every tool.
 *
 * @param diameterTolerance Python's {@code -diatol} as a 0..1 fraction, or {@code null} for an exact
 *                          match at four decimals (Python compares at the object's own decimals)
 */
record TclDrillRequest(List<Double> drilledDiameters, Double diameterTolerance, double drillDepth,
                       boolean multiDepth, double depthPerPass, double travelZ, double feedRateZ,
                       double rapidFeedRate, int spindleSpeed, boolean toolChange, double toolChangeZ,
                       double[] toolChangeXY, Double startZ, double endZ, double[] endXY,
                       boolean dwell, double dwellSeconds, GCodePreprocessor preprocessor,
                       boolean optimizeDrills) {
    TclDrillRequest {
        drilledDiameters = List.copyOf(drilledDiameters);
        toolChangeXY = toolChangeXY == null ? null : toolChangeXY.clone();
        endXY = endXY == null ? null : endXY.clone();
    }
}
