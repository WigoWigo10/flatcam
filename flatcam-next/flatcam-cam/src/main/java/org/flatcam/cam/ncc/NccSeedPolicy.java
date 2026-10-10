package org.flatcam.cam.ncc;

/** Explicit starting-point policy; the legacy scan line is not numerically stable. */
public enum NccSeedPolicy {
    STABLE("Estavel (FX)"),
    PYTHON("Representativo (Python)");

    private final String label;

    NccSeedPolicy(String label) { this.label = label; }

    @Override public String toString() { return label; }
}
