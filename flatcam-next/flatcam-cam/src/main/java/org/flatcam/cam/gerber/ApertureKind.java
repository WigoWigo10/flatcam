package org.flatcam.cam.gerber;

/** Standard Gerber aperture template codes this parser understands, plus MACRO for a named %AM aperture. */
public enum ApertureKind {
    CIRCLE,
    RECTANGLE,
    OBROUND,
    POLYGON,
    MACRO
}
