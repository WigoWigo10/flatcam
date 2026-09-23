package org.flatcam.cam.ncc;

import org.locationtech.jts.geom.Geometry;

/**
 * One tool's own contribution inside a multi-tool NCC result - see
 * {@link NccResult#toolResults()}. {@code failedPolygonCount} counts polygons
 * this tool could not clear at all (e.g. too large to fit); in Rest Machining
 * mode those polygons simply remain available for the next, smaller tool.
 */
public record NccToolResult(double toolDiameter, Geometry geometry, int failedPolygonCount) {
    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }
}
