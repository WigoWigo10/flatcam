package org.flatcam.cam.ncc;

import org.locationtech.jts.geom.Geometry;

/**
 * Lines/Combo diagnostics for one cutter's attempted area, before a following Rest tool.
 * These geometries are read-only result data. They do not measure the remaining stock:
 * a component receiving a pass can still require Contour or another operation for full coverage.
 */
public record NccRegionDiagnostics(Geometry unreachableArea, Geometry supplementedArea, Geometry unservedArea) {
    public int unreachableCount() { return count(unreachableArea); }
    public int supplementedCount() { return count(supplementedArea); }
    public int unservedCount() { return count(unservedArea); }
    private static int count(Geometry geometry) { return geometry==null || geometry.isEmpty()?0:geometry.getNumGeometries(); }
}
