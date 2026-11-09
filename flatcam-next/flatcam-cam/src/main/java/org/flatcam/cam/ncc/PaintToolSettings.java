package org.flatcam.cam.ncc;

/** One Paint tool's parameters, independent of table order and polygon selection. */
public record PaintToolSettings(double overlapFraction, double offset, NccMethod method,
                                boolean connect, boolean contour, Double lineAngleDegrees) {
    public PaintToolSettings(double overlapFraction,double offset,NccMethod method,boolean connect,boolean contour) {
        this(overlapFraction,offset,method,connect,contour,null);
    }
    public PaintToolSettings {
        new NccToolSettings(overlapFraction, method, connect, contour, 0,NccSeedPolicy.STABLE,lineAngleDegrees);
        if (!Double.isFinite(offset)) throw new IllegalArgumentException("Paint offset must be finite");
    }
    NccToolSettings clearing() { return new NccToolSettings(overlapFraction, method, connect, contour, 0,NccSeedPolicy.STABLE,lineAngleDegrees); }
}
