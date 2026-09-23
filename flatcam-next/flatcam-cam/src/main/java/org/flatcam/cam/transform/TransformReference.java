package org.flatcam.cam.transform;

import java.util.List;
import org.locationtech.jts.geom.Coordinate;

/**
 * Computes the pivot {@link Coordinate} for a chosen reference mode -
 * appTools/ToolTransform.py's {@code on_calculate_reference()}/{@code alt_bounds()}.
 * Only Origin and Selection are ported here; Python's third option, a
 * reference Object's own bounding-box center, is deferred (see
 * CONTEXTO_E_PROGRESSO.md section 9.2) - a UI can already build that pivot
 * itself with {@link #selectionCenter} passed a single object's bounds.
 */
public final class TransformReference {

    private TransformReference() {
    }

    public static Coordinate origin() {
        return new Coordinate(0, 0);
    }

    /** Center of the combined bounding box of every given {@code [minX, minY, maxX, maxY]} array. */
    public static Coordinate selectionCenter(List<double[]> boundsList) {
        if (boundsList == null || boundsList.isEmpty()) {
            throw new IllegalArgumentException("At least one bounds array is required");
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] bounds : boundsList) {
            if (bounds == null) {
                continue;
            }
            minX = Math.min(minX, bounds[0]);
            minY = Math.min(minY, bounds[1]);
            maxX = Math.max(maxX, bounds[2]);
            maxY = Math.max(maxY, bounds[3]);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX)) {
            throw new IllegalArgumentException("No valid (non-null) bounds provided");
        }
        return new Coordinate((minX + maxX) / 2.0, (minY + maxY) / 2.0);
    }
}
