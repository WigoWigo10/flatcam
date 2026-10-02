package org.flatcam.cam.ncc;

/** One Paint tool's parameters, independent of table order and polygon selection. */
public record PaintToolSettings(double overlapFraction, double offset, NccMethod method,
                                boolean connect, boolean contour) {
    public PaintToolSettings {
        new NccToolSettings(overlapFraction, method, connect, contour, 0);
        if (!Double.isFinite(offset)) throw new IllegalArgumentException("Paint offset must be finite");
    }
    NccToolSettings clearing() { return new NccToolSettings(overlapFraction, method, connect, contour, 0); }
}
