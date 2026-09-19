package org.flatcam.cam.cutout;

/**
 * appTools/ToolCutOut.py has two separate generation buttons/algorithms
 * sharing the same parameter form: FREEFORM traces the source's actual
 * outline (buffer + exterior ring); RECTANGULAR discards the real shape and
 * cuts its bounding box instead.
 */
public enum CutoutShape {
    FREEFORM, RECTANGULAR
}
