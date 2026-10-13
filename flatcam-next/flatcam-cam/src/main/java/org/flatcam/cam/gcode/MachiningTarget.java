package org.flatcam.cam.gcode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

/** Immutable-by-convention CAM target bound to the original ordered paths/diameters, not a bounding box. */
public record MachiningTarget(String operation, String units, Geometry area,
                              Map<Integer, Geometry> toolAreas, String pathSignature) {
    public MachiningTarget {
        if (!"NCC".equals(operation)) throw new IllegalArgumentException("Unsupported machining target");
        if (!"MM".equals(units) && !"IN".equals(units)) throw new IllegalArgumentException("Unsupported target units");
        if (area == null || !Double.isFinite(area.getArea()) || !area.isEmpty() && (area.getDimension() != 2 || !area.isValid()))
            throw new IllegalArgumentException("Invalid machining target area");
        toolAreas = Map.copyOf(toolAreas);
        if (toolAreas.isEmpty() || toolAreas.keySet().stream().anyMatch(id -> id < 0))
            throw new IllegalArgumentException("Target requires tool areas");
        for (var toolArea : toolAreas.values())
            if (!Double.isFinite(toolArea.getArea()) || !toolArea.isEmpty() && (toolArea.getDimension() != 2 || !toolArea.isValid()))
                throw new IllegalArgumentException("Invalid tool target area");
        if (pathSignature == null || !pathSignature.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid target path signature");
    }

    /** Same canonical WKT precision used by native project persistence; do not hash unrecoverable binary digits. */
    public static String signature(List<ToolGeometry> tools, CancellationToken cancellation) {
        try {
            var digest = MessageDigest.getInstance("SHA-256"); var writer = new WKTWriter();
            for (var tool : tools) {
                cancellation.throwIfCancellationRequested();
                digest.update((Double.toString(tool.toolDiameter()) + "\n").getBytes(StandardCharsets.UTF_8));
                digest.update(writer.write(tool.geometry()).getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            }
            cancellation.throwIfCancellationRequested(); return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
