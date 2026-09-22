package org.flatcam.cam.ncc;

/** Clearing strategies exposed by the legacy NCC tool. */
public enum NccMethod {
    STANDARD("Standard"),
    SEED("Seed"),
    LINES("Lines"),
    COMBO("Combo");

    private final String label;

    NccMethod(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
