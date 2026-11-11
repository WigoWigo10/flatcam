package org.flatcam.cam.ncc;

/** Clearing strategies exposed by the legacy NCC tool. */
public enum NccMethod {
    STANDARD("Standard"),
    SEED("Seed"),
    LINES("Lines"),
    COMBO("Combo"),
    REGION_LINES("Lines por regiões (experimental)"),
    SPIRAL("Espiral adaptativa (experimental)");

    private final String label;

    NccMethod(String label) {
        this.label = label;
    }

    public boolean usesRaster() { return this==LINES || this==COMBO || this==REGION_LINES; }
    public boolean usesOriginalRestAccess() { return usesRaster() || this==SPIRAL; }
    public boolean prefersSourceOrder() { return this==REGION_LINES || this==SPIRAL; }

    /** Stable Python identifiers; the experimental FX strategy has no Python numeric identifier. */
    public static NccMethod[] legacyValues() { return new NccMethod[]{STANDARD,SEED,LINES,COMBO}; }

    @Override
    public String toString() {
        return label;
    }
}
