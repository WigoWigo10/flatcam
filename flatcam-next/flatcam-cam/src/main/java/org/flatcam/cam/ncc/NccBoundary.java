package org.flatcam.cam.ncc;

import java.util.Objects;
import org.locationtech.jts.geom.Geometry;

/**
 * appTools/ToolNCC.py's "select_combo" boundary source for the area to be cleared.
 */
public sealed interface NccBoundary {

    /** Convex hull of the NCC source's own copper - Python's "Itself", the default. */
    record Itself() implements NccBoundary {
    }

    /** A rectangle selected on the canvas, before the common margin is applied. */
    record Area(Geometry geometry) implements NccBoundary {
        public Area {
            Objects.requireNonNull(geometry, "geometry");
            if (geometry.isEmpty() || geometry.getDimension() != 2 || geometry.getArea() <= 0) {
                throw new IllegalArgumentException("Area selection must have positive area");
            }
        }
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
     * Another Geometry object's own shape, with no convex hull -
     * Python's "Reference Object" option with a Geometry kind reference.
     * Python takes the raw shape here (unlike the Gerber case) because a
     * Geometry object's own outline is already whatever the user intended,
     * not a raw copper pour that needs hulling to make sense as a boundary.
     * The margin is applied to each member before union, including buffer(0)
     * at zero margin; it is not a request to bypass boundary preparation.
     */
    record ReferenceGeometry(Geometry geometry) implements NccBoundary {
        public ReferenceGeometry {
            Objects.requireNonNull(geometry, "geometry");
        }
    }
}
