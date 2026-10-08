package org.flatcam.cam.ncc;

import org.locationtech.jts.geom.Geometry;

/**
 * One tool's own contribution inside a multi-tool NCC result - see
 * {@link NccResult#toolResults()}. {@code failedPolygonCount} counts polygons
 * this tool attempted but could not clear. GUI NCC Rest skips polygons which
 * cannot admit the cutter without counting them as failures; they remain
 * available for the next smaller tool.
 */
public record NccToolResult(double toolDiameter, Geometry geometry, int failedPolygonCount,
                            NccOperation operation) {
    public NccToolResult(double toolDiameter, Geometry geometry, int failedPolygonCount) {
        this(toolDiameter, geometry, failedPolygonCount, NccOperation.CLEAR);
    }

    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }
}
