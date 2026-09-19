package org.flatcam.cam.excellon;

import java.util.Map;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Result of parsing one Excellon file: per-tool diameters and hit counts,
 * plus the combined solid geometry (drills as circles, slots as
 * round-capped capsules - see ExcellonParser). Units are "IN" or "MM" as
 * declared by the file (INCH/METRIC or M72/M71) - no unit conversion.
 */
public final class ExcellonImage {

    private final String units;
    private final Map<Integer, Double> toolDiameters;
    private final Map<Integer, Integer> drillCounts;
    private final Map<Integer, Integer> slotCounts;
    private final Geometry solidGeometry;

    ExcellonImage(String units, Map<Integer, Double> toolDiameters, Map<Integer, Integer> drillCounts,
                  Map<Integer, Integer> slotCounts, Geometry solidGeometry) {
        this.units = units;
        this.toolDiameters = Map.copyOf(toolDiameters);
        this.drillCounts = Map.copyOf(drillCounts);
        this.slotCounts = Map.copyOf(slotCounts);
        this.solidGeometry = solidGeometry;
    }

    public String units() {
        return units;
    }

    public Map<Integer, Double> toolDiameters() {
        return toolDiameters;
    }

    public Map<Integer, Integer> drillCounts() {
        return drillCounts;
    }

    public Map<Integer, Integer> slotCounts() {
        return slotCounts;
    }

    public int totalDrills() {
        return drillCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalSlots() {
        return slotCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Geometry solidGeometry() {
        return solidGeometry;
    }

    public boolean isEmpty() {
        return solidGeometry == null || solidGeometry.isEmpty();
    }

    /** {@code [minX, minY, maxX, maxY]} of the actual hole geometry (buffered by tool radius), or null if empty. */
    public double[] bounds() {
        if (isEmpty()) {
            return null;
        }
        Envelope envelope = solidGeometry.getEnvelopeInternal();
        return new double[]{envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()};
    }

    public int partCount() {
        return isEmpty() ? 0 : solidGeometry.getNumGeometries();
    }

    public double totalArea() {
        return isEmpty() ? 0.0 : solidGeometry.getArea();
    }
}
