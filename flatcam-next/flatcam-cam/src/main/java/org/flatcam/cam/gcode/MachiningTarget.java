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
        if (!"NCC".equals(operation) && !"PAINT".equals(operation)) throw new IllegalArgumentException("Unsupported machining target");
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

    /** Transport a proven target; never infer a new objective from an arbitrary path edit/buffer. */
    public MachiningTarget transformed(org.flatcam.cam.transform.TransformOp op, List<ToolGeometry> before,
                                       List<ToolGeometry> after, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (op instanceof org.flatcam.cam.transform.TransformOp.Buffer) return null;
        if (op instanceof org.flatcam.cam.transform.TransformOp.Scale s && s.xFactor()*s.yFactor()==0) return null;
        if (!pathSignature.equals(signature(before,cancellation))) return null;
        String updated=signature(after,cancellation);
        if (!updated.equals(signature(before.stream().map(tool -> tool.transformed(op)).toList(),cancellation)))
            throw new IllegalArgumentException("Alvo exige os mesmos trajetos transformados.");
        var areas=new java.util.LinkedHashMap<Integer,Geometry>();
        for (var entry:toolAreas.entrySet()) { cancellation.throwIfCancellationRequested(); areas.put(entry.getKey(),op.apply(entry.getValue())); }
        return new MachiningTarget(operation,units,op.apply(area),areas,updated);
    }

    public MachiningTarget panelized(org.flatcam.cam.panel.Panelize.Layout layout, List<ToolGeometry> before,
                                     List<ToolGeometry> after, CancellationToken cancellation) {
        if (!pathSignature.equals(signature(before,cancellation))) return null;
        var expected=org.flatcam.cam.panel.Panelize.geometry(units,area,true,before,layout,cancellation,fraction -> {});
        String updated=signature(after,cancellation);
        if (!updated.equals(signature(expected.tools(),cancellation))) throw new IllegalArgumentException("Alvo não corresponde aos trajetos panelizados.");
        var areas=new java.util.LinkedHashMap<Integer,Geometry>();
        for (var entry:toolAreas.entrySet()) areas.put(entry.getKey(),repeatArea(entry.getValue(),layout,cancellation));
        return new MachiningTarget(operation,units,repeatArea(area,layout,cancellation),areas,updated);
    }

    private static Geometry repeatArea(Geometry original, org.flatcam.cam.panel.Panelize.Layout layout, CancellationToken cancellation) {
        var copies=new java.util.ArrayList<Geometry>();
        for (double[] offset:layout.offsets()) {
            cancellation.throwIfCancellationRequested();
            copies.add(new org.flatcam.cam.transform.TransformOp.Offset(offset[0],offset[1]).apply(original));
        }
        var result=org.locationtech.jts.operation.overlayng.OverlayNGRobust.union(copies);
        cancellation.throwIfCancellationRequested(); return result;
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
