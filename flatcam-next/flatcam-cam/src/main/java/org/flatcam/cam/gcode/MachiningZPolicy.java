package org.flatcam.cam.gcode;

/** Python's ordinary Z normalization versus explicitly unrestricted signed Z coordinates. */
public enum MachiningZPolicy {
    STANDARD, MACHINIST;

    public double cutZ(double value) {
        finite(value, "Cut Z");
        if (this == MACHINIST) return value;
        if (value == 0) throw new IllegalArgumentException("Cut Z cannot be zero in standard mode");
        return -Math.abs(value);
    }
    public double travelZ(double value) {
        finite(value, "Travel Z");
        if (this == MACHINIST) return value;
        if (value == 0) throw new IllegalArgumentException("Travel Z cannot be zero in standard mode");
        return Math.abs(value);
    }
    void validateDepth(double depth) {
        finite(depth, "Cut depth");
        if (this == STANDARD && depth <= 0) throw new IllegalArgumentException("Cut depth must be positive in standard mode");
    }
    void validateTravel(double z) {
        finite(z, "Travel Z");
        if (this == STANDARD && z <= 0) throw new IllegalArgumentException("Travel Z must be positive in standard mode");
    }
    static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
