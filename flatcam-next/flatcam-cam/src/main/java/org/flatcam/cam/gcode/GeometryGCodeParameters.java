package org.flatcam.cam.gcode;

/**
 * Machining parameters for converting a Geometry object's paths into a CNC
 * Job. May be common or overridden per tool in a multi-tool Geometry.
 * Change/probing and rapid feed remain job-level settings.
 *
 * @param safeZ travel Z coordinate; positive in Standard, signed in Machinist
 * @param cutDepth signed distance below Z0 (the negative of the actual Cut Z coordinate)
 * @param pauseForToolChange execute the profile's change sequence, including the initial tool
 * @param probing explicit Mach3 Z probing configuration; absent for legacy/basic profiles
 * @param zPolicy coordinate policy captured on generation; old constructors retain Standard semantics
 */
public record GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                      double depthPerPass, double feedRate, int spindleSpeedRpm,
                                      boolean pauseForToolChange, double rapidFeedRate,
                                      ProbeToolChangeParameters probing, double feedRateZ,
                                      boolean dwell, double dwellSeconds, boolean extraCut, double extraCutLength,
                                      ToolPathOffset offset, double customOffset, GeometryJobOptions jobOptions, GeometryPassStrategy passStrategy,
                                      MachiningZPolicy zPolicy) {
    /** Legacy API stores depth below Z0; its existing positive values keep the same output. */
    public GeometryGCodeParameters(double safeZ,double cutDepth,boolean multiDepth,double depthPerPass,double feedRate,int spindleSpeedRpm,
                                   boolean pauseForToolChange,double rapidFeedRate,ProbeToolChangeParameters probing,double feedRateZ,
                                   boolean dwell,double dwellSeconds,boolean extraCut,double extraCutLength,
                                   ToolPathOffset offset,double customOffset,GeometryJobOptions jobOptions,GeometryPassStrategy passStrategy) {
        this(safeZ,cutDepth,multiDepth,depthPerPass,feedRate,spindleSpeedRpm,pauseForToolChange,rapidFeedRate,probing,feedRateZ,
                dwell,dwellSeconds,extraCut,extraCutLength,offset,customOffset,jobOptions,passStrategy,MachiningZPolicy.STANDARD);
    }
    public GeometryGCodeParameters(double safeZ,double cutDepth,boolean multiDepth,double depthPerPass,double feedRate,int spindleSpeedRpm,
                                   boolean pauseForToolChange,double rapidFeedRate,ProbeToolChangeParameters probing,double feedRateZ,
                                   boolean dwell,double dwellSeconds,boolean extraCut,double extraCutLength,
                                   ToolPathOffset offset,double customOffset,GeometryJobOptions jobOptions) {
        this(safeZ,cutDepth,multiDepth,depthPerPass,feedRate,spindleSpeedRpm,pauseForToolChange,rapidFeedRate,probing,feedRateZ,
                dwell,dwellSeconds,extraCut,extraCutLength,offset,customOffset,jobOptions,GeometryPassStrategy.RETRACT_SAME_DIRECTION);
    }
    public GeometryGCodeParameters(double safeZ, double cutDepth, boolean multiDepth,
                                  double depthPerPass, double feedRate, int spindleSpeedRpm,
                                  boolean pauseForToolChange, double rapidFeedRate, ProbeToolChangeParameters probing,
                                  double feedRateZ, boolean dwell, double dwellSeconds, boolean extraCut, double extraCutLength) {
        this(safeZ, cutDepth, multiDepth, depthPerPass, feedRate, spindleSpeedRpm, pauseForToolChange,
                rapidFeedRate, probing, feedRateZ, dwell, dwellSeconds, extraCut, extraCutLength,
                ToolPathOffset.PATH, 0, GeometryJobOptions.AUTOMATIC);
    }
    public GeometryGCodeParameters withCompensation(ToolPathOffset mode, double distance) {
        return new GeometryGCodeParameters(safeZ, cutDepth, multiDepth, depthPerPass, feedRate, spindleSpeedRpm,
                pauseForToolChange, rapidFeedRate, probing, feedRateZ, dwell, dwellSeconds, extraCut, extraCutLength,
                mode, distance, jobOptions,passStrategy,zPolicy);
    }
    public GeometryGCodeParameters withJobOptions(GeometryJobOptions options) {
        return new GeometryGCodeParameters(safeZ, cutDepth, multiDepth, depthPerPass, feedRate, spindleSpeedRpm,
                pauseForToolChange, rapidFeedRate, probing, feedRateZ, dwell, dwellSeconds, extraCut, extraCutLength,
                offset, customOffset, options,passStrategy,zPolicy);
    }
    public GeometryGCodeParameters withCutting(double depth, boolean multiple, double perPass) {
        return new GeometryGCodeParameters(safeZ, depth, multiple, perPass, feedRate, spindleSpeedRpm,
                pauseForToolChange, rapidFeedRate, probing, feedRateZ, dwell, dwellSeconds, extraCut, extraCutLength,
                offset, customOffset, jobOptions,passStrategy,zPolicy);
    }
    public GeometryGCodeParameters withPassStrategy(GeometryPassStrategy strategy) {
        return new GeometryGCodeParameters(safeZ,cutDepth,multiDepth,depthPerPass,feedRate,spindleSpeedRpm,pauseForToolChange,
                rapidFeedRate,probing,feedRateZ,dwell,dwellSeconds,extraCut,extraCutLength,offset,customOffset,jobOptions,strategy,zPolicy);
    }
    public GeometryGCodeParameters withZPolicy(MachiningZPolicy policy) {
        return new GeometryGCodeParameters(safeZ,cutDepth,multiDepth,depthPerPass,feedRate,spindleSpeedRpm,pauseForToolChange,
                rapidFeedRate,probing,feedRateZ,dwell,dwellSeconds,extraCut,extraCutLength,offset,customOffset,jobOptions,passStrategy,policy);
    }
    public GeometryGCodeParameters withZCoordinates(double travelZ,double cutZ,boolean multiple,double step,MachiningZPolicy policy) {
        return new GeometryGCodeParameters(travelZ,-cutZ,multiple,step,feedRate,spindleSpeedRpm,pauseForToolChange,
                rapidFeedRate,probing,feedRateZ,dwell,dwellSeconds,extraCut,extraCutLength,offset,customOffset,jobOptions,passStrategy,policy);
    }
    /** Actual target coordinate; cutDepth remains the signed distance below Z0 for file compatibility. */
    public double cutZ() { return -cutDepth; }
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
        java.util.Objects.requireNonNull(offset, "offset");
        java.util.Objects.requireNonNull(jobOptions, "jobOptions");
        java.util.Objects.requireNonNull(passStrategy,"passStrategy");
        java.util.Objects.requireNonNull(zPolicy,"zPolicy");
        if(multiDepth && passStrategy!=GeometryPassStrategy.RETRACT_SAME_DIRECTION) {
            if(extraCut) throw new IllegalArgumentException("Passes contínuos não aceitam Extra Cut neste incremento.");
            if(passStrategy==GeometryPassStrategy.CONTINUOUS_ALTERNATING && jobOptions.routing().preserveDirection()) throw new IllegalArgumentException("Passes alternados invertem a direção: desmarque Preservar direção ou use retração entre passes.");
            if(probing!=null) throw new IllegalArgumentException("Passes contínuos não são habilitados para sondagem.");
        }
        if (!Double.isFinite(customOffset)) throw new IllegalArgumentException("Custom Offset deve ser finito.");
        if (offset == ToolPathOffset.CUSTOM && customOffset == 0)
            throw new IllegalArgumentException("Custom Offset nao pode ser zero; use Path para manter os caminhos.");
        if (!Double.isFinite(feedRateZ) || feedRateZ <= 0)
            throw new IllegalArgumentException("Feedrate Z must be positive");
        if (!Double.isFinite(dwellSeconds) || dwellSeconds < 0)
            throw new IllegalArgumentException("Dwell time must be finite and non-negative");
        if (!Double.isFinite(extraCutLength) || extraCutLength < 0)
            throw new IllegalArgumentException("Extra cut length must be finite and non-negative");
        if (!Double.isFinite(rapidFeedRate) || rapidFeedRate < 0) {
            throw new IllegalArgumentException("rapidFeedRate must be zero (automatic) or positive");
        }
        zPolicy.validateTravel(safeZ);
        zPolicy.validateDepth(cutDepth);
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
