package org.flatcam.cam.gcode;

/**
 * Parameters for {@link GCodeGenerator#generateDrillGCode}. All linear values
 * (safeZ, drillDepth, depthPerPass, offsetZ and feedRate) are in the same units as the source
 * ExcellonImage (mm or inch) - no conversion is performed.
 *
 * @param safeZ               retract Z coordinate; positive in Standard, any finite coordinate in Machinist
 * @param drillDepth          signed distance below Z0, written as its negative in G-code; Standard requires positive
 * @param feedRate            plunge/cut feed rate, units/minute
 * @param spindleSpeedRpm     spindle speed for M3 S...; 0 omits automatic spindle start/end commands;
 *                            a tool-change sequence may still stop the spindle explicitly before a pause
 * @param pauseForToolChange  insert M0 (and a comment naming the next tool) between tools
 * @param multiDepth          drill in repeated Z steps, retracting between passes
 * @param depthPerPass        maximum positive Z step when multiDepth is enabled
 * @param dwell               pause after starting the spindle for this tool
 * @param dwellSeconds        G4 P duration, in seconds
 * @param offsetZ             signed extra depth; positive values drill deeper, matching Python's Offset Z
 * @param slots               optional per-tool overlapping drill chain; milling remains the backward-compatible default
 * @param zPolicy             coordinate policy captured when generating the job
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
        double offsetZ,
        DrillSlotParameters slots,
        MachiningZPolicy zPolicy
) {
    public DrillGCodeParameters(double safeZ,double drillDepth,double feedRate,int spindleSpeedRpm,boolean pauseForToolChange,
                                boolean multiDepth,double depthPerPass,boolean dwell,double dwellSeconds,double offsetZ,DrillSlotParameters slots) {
        this(safeZ,drillDepth,feedRate,spindleSpeedRpm,pauseForToolChange,multiDepth,depthPerPass,dwell,dwellSeconds,offsetZ,slots,MachiningZPolicy.STANDARD);
    }
    public DrillGCodeParameters(double safeZ, double drillDepth, double feedRate, int spindleSpeedRpm,
                                boolean pauseForToolChange, boolean multiDepth, double depthPerPass,
                                boolean dwell, double dwellSeconds, double offsetZ) {
        this(safeZ, drillDepth, feedRate, spindleSpeedRpm, pauseForToolChange, multiDepth, depthPerPass,
                dwell, dwellSeconds, offsetZ, DrillSlotParameters.milling());
    }

    public DrillGCodeParameters withSlots(DrillSlotParameters value) {
        return new DrillGCodeParameters(safeZ, drillDepth, feedRate, spindleSpeedRpm, pauseForToolChange,
                multiDepth, depthPerPass, dwell, dwellSeconds, offsetZ, value,zPolicy);
    }
    public DrillGCodeParameters(double safeZ, double drillDepth, double feedRate,
                                int spindleSpeedRpm, boolean pauseForToolChange) {
        this(safeZ, drillDepth, feedRate, spindleSpeedRpm, pauseForToolChange,
                false, 0, false, 0, 0);
    }

    public DrillGCodeParameters {
        java.util.Objects.requireNonNull(slots, "slots");
        java.util.Objects.requireNonNull(zPolicy, "zPolicy");
        zPolicy.validateTravel(safeZ); zPolicy.validateDepth(drillDepth);
        if (!Double.isFinite(feedRate) || feedRate <= 0) {
            throw new IllegalArgumentException("feedRate must be positive: " + feedRate);
        }
        if (spindleSpeedRpm < 0) {
            throw new IllegalArgumentException("spindleSpeedRpm cannot be negative: " + spindleSpeedRpm);
        }
        if (!Double.isFinite(offsetZ) || !Double.isFinite(drillDepth + offsetZ)
                || zPolicy == MachiningZPolicy.STANDARD && drillDepth + offsetZ <= 0) {
            throw new IllegalArgumentException("Effective Cut Z (Cut Z - Offset Z) must be finite and, in Standard mode, below Z0");
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
    public double cutZ() { return -drillDepth; }
    public double effectiveCutZ() { return -effectiveDepth(); }
}
