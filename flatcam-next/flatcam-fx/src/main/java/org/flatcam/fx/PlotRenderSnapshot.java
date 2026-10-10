package org.flatcam.fx;

import java.util.Objects;
import javafx.scene.paint.Color;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/** One vector draw's borrowed, read-only geometry version and immutable ink.
 * Geometry identity is the version, as in AsyncPlotIndexCache. Callers must replace,
 * never mutate, geometry while it is indexed/rendered. No model copy or CAM modification.
 * A previous display version during editor preparation is explicitly permitted. */
record PlotRenderSnapshot(Geometry geometry, PlotDrawableIndex index, boolean strokeOnly,
                          boolean filled, boolean multicolor, Color fill, Color stroke,
                          double lineWidth, Envelope bounds) {
    PlotRenderSnapshot {
        Objects.requireNonNull(geometry);
        Objects.requireNonNull(fill);
        Objects.requireNonNull(stroke);
        if (index != null && index.geometry() != geometry)
            throw new IllegalArgumentException("Index and displayed geometry versions differ");
        if (!Double.isFinite(lineWidth) || lineWidth <= 0)
            throw new IllegalArgumentException("Invalid stroke width");
        bounds = bounds == null ? null : new Envelope(bounds);
    }

    @Override public Envelope bounds() { return bounds == null ? null : new Envelope(bounds); }
}
