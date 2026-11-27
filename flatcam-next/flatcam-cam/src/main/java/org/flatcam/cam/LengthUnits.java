package org.flatcam.cam;

/** Conversion between the two units used by CAM images. */
public final class LengthUnits {
    private LengthUnits() {}

    public static double factor(String source, String target) {
        return millimetresPerUnit(source) / millimetresPerUnit(target);
    }

    private static double millimetresPerUnit(String units) {
        return switch (units) {
            case "MM" -> 1;
            case "IN" -> 25.4;
            default -> throw new IllegalArgumentException("Unsupported CAM units: " + units);
        };
    }
}
