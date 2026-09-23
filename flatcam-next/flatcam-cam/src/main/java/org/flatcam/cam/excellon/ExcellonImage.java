package org.flatcam.cam.excellon;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Result of parsing one Excellon file: per-tool diameters, the individual
 * drill/slot hits (needed to generate G-code - see org.flatcam.cam.gcode),
 * and the combined solid geometry for display (drills as circles, slots as
 * round-capped capsules - see ExcellonParser). Units are "IN" or "MM" as
 * declared by the file (INCH/METRIC or M72/M71) - no unit conversion.
 */
public final class ExcellonImage {

    public record Drill(int toolId, double x, double y) {
    }

    public record Slot(int toolId, double x1, double y1, double x2, double y2) {
    }

    private final String units;
    private final Map<Integer, Double> toolDiameters;
    private final List<Drill> drills;
    private final List<Slot> slots;
    private final Geometry solidGeometry;

    ExcellonImage(String units, Map<Integer, Double> toolDiameters, List<Drill> drills, List<Slot> slots,
                  Geometry solidGeometry) {
        this.units = units;
        this.toolDiameters = Map.copyOf(toolDiameters);
        this.drills = List.copyOf(drills);
        this.slots = List.copyOf(slots);
        this.solidGeometry = solidGeometry;
    }

    public String units() {
        return units;
    }

    public Map<Integer, Double> toolDiameters() {
        return toolDiameters;
    }

    public List<Drill> drills() {
        return drills;
    }

    public List<Slot> slots() {
        return slots;
    }

    public Map<Integer, Integer> drillCounts() {
        return drills.stream().collect(Collectors.groupingBy(Drill::toolId, Collectors.summingInt(d -> 1)));
    }

    public Map<Integer, Integer> slotCounts() {
        return slots.stream().collect(Collectors.groupingBy(Slot::toolId, Collectors.summingInt(s -> 1)));
    }

    public int totalDrills() {
        return drills.size();
    }

    public int totalSlots() {
        return slots.size();
    }

    public Geometry solidGeometry() {
        return solidGeometry;
    }

    public boolean isEmpty() {
        return solidGeometry == null || solidGeometry.isEmpty();
    }

    /**
     * A copy with {@code op} applied to every drill/slot position and the
     * solid geometry - appTools/ToolTransform.py's six operations applied to
     * an Excellon via {@code Excellon.rotate/mirror/skew/scale/offset}, which
     * transform each drill Point and slot start/stop Point individually
     * (not just the aggregate solid geometry).
     */
    public ExcellonImage transformed(TransformOp op) {
        List<Drill> newDrills = drills.stream()
                .map(drill -> {
                    Coordinate p = op.apply(new Coordinate(drill.x(), drill.y()));
                    return new Drill(drill.toolId(), p.x, p.y);
                })
                .toList();
        List<Slot> newSlots = slots.stream()
                .map(slot -> {
                    Coordinate p1 = op.apply(new Coordinate(slot.x1(), slot.y1()));
                    Coordinate p2 = op.apply(new Coordinate(slot.x2(), slot.y2()));
                    return new Slot(slot.toolId(), p1.x, p1.y, p2.x, p2.y);
                })
                .toList();
        return new ExcellonImage(units, toolDiameters, newDrills, newSlots,
                solidGeometry == null ? null : op.apply(solidGeometry));
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
