package org.flatcam.cam.geometry;

import java.util.Objects;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Geometry;

/**
 * One tool's toolpaths inside a multi-tool ("multigeo", in Python's
 * appObjects/FlatCAMGeometry.py terms) Geometry object - e.g. one row of an
 * NCC Tool result. A Geometry with no tool association (a plain derived
 * outline/bounding-box shape) is represented elsewhere as a bare
 * {@link Geometry}, not this type.
 */
public record ToolGeometry(double toolDiameter, Geometry geometry) {
    public ToolGeometry {
        if (!Double.isFinite(toolDiameter) || toolDiameter <= 0) {
            throw new IllegalArgumentException("toolDiameter must be positive: " + toolDiameter);
        }
        Objects.requireNonNull(geometry, "geometry");
    }

    /** GeometryObject.scale()/.offset() etc. keep each tool's own solid_geometry in sync - same idea here. */
    public ToolGeometry transformed(TransformOp op) {
        return new ToolGeometry(toolDiameter, op.apply(geometry));
    }
}
