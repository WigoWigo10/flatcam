package org.flatcam.cam.cutout;

/**
 * appTools/ToolCutOut.py's "Kind" RadioSet (Single/Panel). Single unions the
 * whole source into one outline (or, if that union is still disjoint, boxes
 * it - see CutoutGenerator); Panel treats every disjoint part of the source
 * as its own separate board and outlines/gaps each independently, for a
 * source Gerber holding several board outlines side by side.
 */
public enum CutoutKind {
    SINGLE, PANEL
}
