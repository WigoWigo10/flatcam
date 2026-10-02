package org.flatcam.cam.gcode;

/** Geometry compensation, applied before CAM generation (not controller G41/G42). */
public enum ToolPathOffset {
    PATH("Path"), IN("In"), OUT("Out"), CUSTOM("Custom");

    private final String label;
    ToolPathOffset(String label) { this.label = label; }
    public String label() { return label; }
    public double distance(double diameter, double custom) {
        return switch (this) {
            case PATH -> 0;
            case IN -> -diameter / 2;
            case OUT -> diameter / 2;
            case CUSTOM -> custom;
        };
    }
    public static ToolPathOffset fromLegacy(String value) {
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
    @Override public String toString() { return label; }
}
