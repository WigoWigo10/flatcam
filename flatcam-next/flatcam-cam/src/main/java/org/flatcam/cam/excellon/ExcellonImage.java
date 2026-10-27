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

    /** Immutable sampled centreline vertices for a routed slot; empty on legacy straight slots. */
    public record RoutePoint(double x, double y) {
        public RoutePoint {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Non-finite route point");
        }
    }

    public record Slot(int toolId, double x1, double y1, double x2, double y2, List<RoutePoint> path) {
        public Slot(int toolId,double x1,double y1,double x2,double y2) { this(toolId,x1,y1,x2,y2,List.of()); }
        public Slot {
            path = path==null ? List.of() : List.copyOf(path);
            if (!path.isEmpty() && (path.size()<2 || path.getFirst().x()!=x1 || path.getFirst().y()!=y1
                    || path.getLast().x()!=x2 || path.getLast().y()!=y2))
                throw new IllegalArgumentException("Routed slot endpoints do not match its centreline");
        }
        public boolean hasPath() { return !path.isEmpty(); }
        public org.locationtech.jts.geom.LineString centerline(org.locationtech.jts.geom.GeometryFactory factory) {
            return factory.createLineString(hasPath() ? path.stream().map(p->new Coordinate(p.x(),p.y())).toArray(Coordinate[]::new)
                    : new Coordinate[]{new Coordinate(x1,y1),new Coordinate(x2,y2)});
        }
        public Slot withTool(int id) { return new Slot(id,x1,y1,x2,y2,path); }
        public Slot transformed(TransformOp op) {
            Coordinate from=op.apply(new Coordinate(x1,y1)), to=op.apply(new Coordinate(x2,y2));
            return new Slot(toolId,from.x,from.y,to.x,to.y,path.stream().map(p->{
                Coordinate point=op.apply(new Coordinate(p.x(),p.y())); return new RoutePoint(point.x,point.y);
            }).toList());
        }
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

    /**
     * Builds an ExcellonImage directly from already-resolved data rather
     * than parsing a file - used by project persistence
     * (org.flatcam.app.project.flatprj) to reconstruct one from a saved
     * project's embedded geometry.
     */
    public static ExcellonImage of(String units, Map<Integer, Double> toolDiameters, List<Drill> drills,
                                   List<Slot> slots, Geometry solidGeometry) {
        return new ExcellonImage(units, toolDiameters, drills, slots, solidGeometry);
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
        if (op instanceof TransformOp.Buffer buffer) {
            Map<Integer,Double> diameters = new java.util.LinkedHashMap<>();
            toolDiameters.forEach((id,dia) -> {
                // ParseExcellon.buffer adds the distance to DIAMETER, not twice the distance.
                double updated = buffer.factor() ? dia * buffer.value() : dia + buffer.value();
                if (!Double.isFinite(updated) || updated <= 0) throw new IllegalArgumentException("Buffer elimina o diametro da ferramenta Excellon " + id);
                diameters.put(id,updated);
            });
            var factory=solidGeometry == null ? new org.locationtech.jts.geom.GeometryFactory() : solidGeometry.getFactory();
            List<Geometry> footprints = new java.util.ArrayList<>();
            for (var drill : drills) footprints.add(factory.createPoint(new Coordinate(drill.x(),drill.y())).buffer(diameters.get(drill.toolId())/2,16));
            for (var slot : slots) footprints.add(slot.centerline(factory).buffer(diameters.get(slot.toolId())/2,16));
            return new ExcellonImage(units,diameters,drills,slots,factory.buildGeometry(footprints));
        }
        List<Drill> newDrills = drills.stream()
                .map(drill -> {
                    Coordinate p = op.apply(new Coordinate(drill.x(), drill.y()));
                    return new Drill(drill.toolId(), p.x, p.y);
                })
                .toList();
        List<Slot> newSlots = slots.stream().map(slot -> slot.transformed(op)).toList();
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
