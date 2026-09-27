package org.flatcam.cam.geometry;

/** Legacy Geometry/NCC tool type. C1-C4 and B are informational circular tools. */
public enum ToolProfile {
    C1, C2, C3, C4, B, V;

    public static ToolProfile fromLegacy(String value) {
        if (value == null || value.isBlank()) return C1;
        return ToolProfile.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
