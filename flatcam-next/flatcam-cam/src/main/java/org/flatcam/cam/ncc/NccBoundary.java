package org.flatcam.cam.ncc;

import java.util.Objects;
import org.locationtech.jts.geom.Geometry;

/**
 * appTools/ToolNCC.py's "select_combo" boundary source for the area to be
 * cleared. Python also offers "Area Selection" (a canvas-drawn rectangle) -
 * deferred here since this port has no canvas-level area-selection
 * interaction yet (see CONTEXTO_E_PROGRESSO.md).
 */
public sealed interface NccBoundary {

    /** Convex hull of the NCC source's own copper - Python's "Itself", the default. */
    record Itself() implements NccBoundary {
    }

    /**
     * Convex hull of the source intersected with the convex hull of another
     * Gerber's copper - Python's "Reference Object" option with a Gerber kind
     * reference (ToolNCC.py's calculate_bounding_box, ncc_select == 2 branch).
     */
    record ReferenceGerber(Geometry geometry) implements NccBoundary {
        public ReferenceGerber {
            Objects.requireNonNull(geometry, "geometry");
        }
    }

    /**
     * Another Geometry object's own shape, used AS-IS with no convex hull -
     * Python's "Reference Object" option with a Geometry kind reference.
     * Python takes the raw shape here (unlike the Gerber case) because a
     * Geometry object's own outline is already whatever the user intended,
     * not a raw copper pour that needs hulling to make sense as a boundary.
     */
    record ReferenceGeometry(Geometry geometry) implements NccBoundary {
        public ReferenceGeometry {
            Objects.requireNonNull(geometry, "geometry");
        }
    }
}
