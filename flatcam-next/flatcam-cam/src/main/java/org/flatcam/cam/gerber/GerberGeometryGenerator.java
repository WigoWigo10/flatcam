package org.flatcam.cam.gerber;

import java.util.Objects;
import org.locationtech.jts.geom.Geometry;

/** Derived Geometry-object operations from the legacy Gerber object utilities. */
public final class GerberGeometryGenerator {

    private static final int BUFFER_QUADRANT_SEGMENTS = 16;

    private GerberGeometryGenerator() {
    }

    /**
     * Creates the Gerber's envelope expanded by {@code margin}. A rounded
     * result preserves the buffer's round corners; a square result takes its
     * rectangular envelope, matching FlatCAMGerber.on_generatebb_button_click().
     */
    public static Geometry boundingBox(GerberImage image, double margin, boolean rounded) {
        Geometry copper = requireCopper(image, margin);
        Geometry boundary = copper.getEnvelope().buffer(margin, BUFFER_QUADRANT_SEGMENTS);
        return rounded ? boundary : boundary.getEnvelope();
    }

    /** Creates the area inside the expanded envelope that is not occupied by copper. */
    public static Geometry nonCopper(GerberImage image, double margin, boolean rounded) {
        Geometry copper = requireCopper(image, margin);
        return boundingBox(image, margin, rounded).difference(copper);
    }

    private static Geometry requireCopper(GerberImage image, double margin) {
        Objects.requireNonNull(image, "image");
        if (!Double.isFinite(margin)) {
            throw new IllegalArgumentException("Margin must be a finite number");
        }
        Geometry copper = image.solidGeometry();
        if (copper == null || copper.isEmpty()) {
            throw new IllegalArgumentException("Cannot derive geometry from an empty Gerber");
        }
        return copper;
    }
}
