package org.flatcam.cam.gcode;

/** Explicit physical cutting policy, not an invisible compatibility workaround. */
public enum GeometryPassStrategy {
    RETRACT_SAME_DIRECTION,
    CONTINUOUS_ALTERNATING,
    CONTINUOUS_SAME_DIRECTION
}
