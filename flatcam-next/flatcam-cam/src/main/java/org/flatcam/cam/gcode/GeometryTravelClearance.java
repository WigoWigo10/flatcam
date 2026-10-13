package org.flatcam.cam.gcode;

/** Explicit XY clearance policy. Tool-change height is still validated against the job maximum. */
public enum GeometryTravelClearance {
    JOB_MAXIMUM("Maior Travel Z do trabalho"),
    PER_TOOL("Travel Z de cada ferramenta");

    private final String label;
    GeometryTravelClearance(String label) { this.label=label; }
    public double effective(double toolZ,double jobMaximum) { return this==PER_TOOL ? toolZ : jobMaximum; }
    @Override public String toString() { return label; }
}
