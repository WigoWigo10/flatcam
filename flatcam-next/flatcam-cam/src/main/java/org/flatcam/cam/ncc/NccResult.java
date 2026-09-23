package org.flatcam.cam.ncc;

import java.util.List;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Resulting center-line toolpaths plus the non-copper area they cover.
 * {@code toolResults} holds each tool's own contribution, in the order it was
 * processed (see NccGenerator) - mirrors Python's multigeo Geometry object,
 * where each NCC tool owns its own solid_geometry. {@code geometry} is every
 * tool's paths combined into one, for plotting and for single-tool consumers
 * that don't care about the per-tool split.
 */
public final class NccResult {

    private final String units;
    private final Geometry geometry;
    private final Geometry clearingArea;
    private final List<NccToolResult> toolResults;

    NccResult(String units, Geometry geometry, Geometry clearingArea, List<NccToolResult> toolResults) {
        this.units = units;
        this.geometry = geometry;
        this.clearingArea = clearingArea;
        this.toolResults = List.copyOf(toolResults);
    }

    public String units() {
        return units;
    }

    public Geometry geometry() {
        return geometry;
    }

    public Geometry clearingArea() {
        return clearingArea;
    }

    /** Each tool's own paths, in the order they were processed. */
    public List<NccToolResult> toolResults() {
        return toolResults;
    }

    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }

    public int pathCount() {
        return isEmpty() ? 0 : geometry.getNumGeometries();
    }

    public double totalLength() {
        return isEmpty() ? 0 : geometry.getLength();
    }

    public int totalFailedPolygonCount() {
        return toolResults.stream().mapToInt(NccToolResult::failedPolygonCount).sum();
    }

    public double[] bounds() {
        if (isEmpty()) {
            return null;
        }
        Envelope envelope = geometry.getEnvelopeInternal();
        return new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
    }
}
