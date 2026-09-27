package org.flatcam.cam.excellon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

/** Editable drills and slots, retaining tool IDs and undo/redo until Apply. */
public final class ExcellonEditSession {

    public record Row(long id, String type, int toolId, String coordinates) {
    }

    private record Hit(long id, int toolId, boolean slot, double x1, double y1, double x2, double y2) {
        Hit translated(long newId, double dx, double dy) {
            return new Hit(newId, toolId, slot, x1 + dx, y1 + dy, x2 + dx, y2 + dy);
        }
        Hit withTool(int newToolId) {
            return new Hit(id, newToolId, slot, x1, y1, x2, y2);
        }
    }

    private record Snapshot(List<Hit> hits, Set<Long> selected, Map<Integer, Double> tools, boolean dirty) {
    }

    private final GeometryFactory factory = new GeometryFactory();
    private final String units;
    private final Map<Integer, Double> toolDiameters;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private List<Hit> hits;
    private final Set<Long> selected = new LinkedHashSet<>();
    private STRtree index;
    private long nextId = 1;
    private boolean dirty;

    public ExcellonEditSession(ExcellonImage source) {
        Objects.requireNonNull(source, "source");
        units = source.units();
        toolDiameters = new LinkedHashMap<>(source.toolDiameters());
        List<Hit> initial = new ArrayList<>();
        for (ExcellonImage.Drill drill : source.drills()) {
            initial.add(new Hit(nextId++, drill.toolId(), false, drill.x(), drill.y(), drill.x(), drill.y()));
        }
        for (ExcellonImage.Slot slot : source.slots()) {
            initial.add(new Hit(nextId++, slot.toolId(), true,
                    slot.x1(), slot.y1(), slot.x2(), slot.y2()));
        }
        hits = List.copyOf(initial);
        rebuildIndex();
    }

    public Map<Integer, Double> toolDiameters() { return Map.copyOf(toolDiameters); }
    public int size() { return hits.size(); }
    public int selectedCount() { return selected.size(); }
    public boolean isDirty() { return dirty; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public Set<Long> selectedIds() { return Set.copyOf(selected); }

    public List<Row> rows() {
        return hits.stream().map(hit -> new Row(hit.id(), hit.slot() ? "Slot" : "Drill", hit.toolId(),
                hit.slot() ? format(hit.x1(), hit.y1()) + " → " + format(hit.x2(), hit.y2())
                        : format(hit.x1(), hit.y1()))).toList();
    }

    private static String format(double x, double y) {
        return String.format(java.util.Locale.ROOT, "%.4f, %.4f", x, y);
    }

    public void selectIds(Collection<Long> ids) {
        selected.clear();
        Set<Long> valid = hits.stream().map(Hit::id).collect(java.util.stream.Collectors.toSet());
        for (Long id : ids) {
            if (valid.contains(id)) selected.add(id);
        }
    }

    public void clickSelect(double x, double y, double tolerance, boolean additive) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(tolerance) || tolerance < 0) return;
        Envelope area = new Envelope(x - tolerance, x + tolerance, y - tolerance, y + tolerance);
        @SuppressWarnings("unchecked")
        List<Hit> candidates = index.query(area);
        Geometry point = factory.createPoint(new Coordinate(x, y));
        Hit closest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Hit hit : candidates) {
            double distance = footprint(hit).distance(point);
            if (distance <= tolerance && distance < best) {
                best = distance;
                closest = hit;
            }
        }
        if (!additive) selected.clear();
        if (closest != null && !selected.add(closest.id()) && additive) selected.remove(closest.id());
    }

    public void boxSelect(double x1, double y1, double x2, double y2, boolean additive) {
        Envelope area = new Envelope(x1, x2, y1, y2);
        @SuppressWarnings("unchecked")
        List<Hit> candidates = index.query(area);
        Geometry box = factory.toGeometry(area);
        if (!additive) selected.clear();
        for (Hit hit : candidates) if (footprint(hit).intersects(box)) selected.add(hit.id());
    }

    public Geometry geometry() { return geometryOf(hits); }

    public Geometry selectedGeometry() {
        return geometryOf(hits.stream().filter(hit -> selected.contains(hit.id())).toList());
    }

    /** Cheap highlight/placement preview for very large selections. */
    public Geometry selectedBounds() {
        Envelope bounds = new Envelope();
        for (Hit hit : hits) if (selected.contains(hit.id())) bounds.expandToInclude(hitBounds(hit));
        return bounds.isNull() ? factory.createGeometryCollection() : factory.toGeometry(bounds);
    }

    public boolean deleteSelected() {
        if (selected.isEmpty()) return false;
        commit(hits.stream().filter(hit -> !selected.contains(hit.id())).toList(), Set.of());
        return true;
    }

    public boolean moveSelected(double dx, double dy) {
        if (selected.isEmpty() || !finiteMove(dx, dy)) return false;
        commit(hits.stream().map(hit -> selected.contains(hit.id()) ? hit.translated(hit.id(), dx, dy) : hit).toList(),
                selected);
        return true;
    }

    public boolean copySelected(double dx, double dy) {
        if (selected.isEmpty() || !finiteMove(dx, dy)) return false;
        List<Hit> result = new ArrayList<>(hits);
        Set<Long> copies = new LinkedHashSet<>();
        for (Hit hit : hits) if (selected.contains(hit.id())) {
            Hit copy = hit.translated(nextId++, dx, dy);
            result.add(copy);
            copies.add(copy.id());
        }
        commit(result, copies);
        return true;
    }

    /** Copies selected drills or slots into a bounded rectangular array. */
    public int arraySelected(boolean slots, int columns, int rows, double pitchX, double pitchY) {
        if (columns < 1 || rows < 1 || (long) columns * rows <= 1
                || !Double.isFinite(pitchX) || !Double.isFinite(pitchY)
                || (columns > 1 && pitchX == 0) || (rows > 1 && pitchY == 0)) {
            throw new IllegalArgumentException("Array exige linhas/colunas positivas e passos finitos nao nulos.");
        }
        long cells = (long) columns * rows;
        if (cells > 10_001) throw new IllegalArgumentException("Array limitado a 10.000 novas formas.");
        List<Hit> sources = hits.stream().filter(hit -> selected.contains(hit.id()) && hit.slot() == slots).toList();
        if (sources.isEmpty()) throw new IllegalArgumentException("Selecione furos ou slots do tipo escolhido.");
        long added = (long) sources.size() * (cells - 1);
        if (added > 10_000) throw new IllegalArgumentException("Array limitado a 10.000 novas formas.");
        double maxDx = (columns - 1.0) * pitchX;
        double maxDy = (rows - 1.0) * pitchY;
        for (Hit source : sources) {
            if (!Double.isFinite(source.x1() + maxDx) || !Double.isFinite(source.x2() + maxDx)
                    || !Double.isFinite(source.y1() + maxDy) || !Double.isFinite(source.y2() + maxDy))
                throw new IllegalArgumentException("Coordenadas do array excedem o intervalo valido.");
        }
        List<Hit> result = new ArrayList<>(hits);
        Set<Long> copies = new LinkedHashSet<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (row == 0 && column == 0) continue;
                for (Hit source : sources) {
                    Hit copy = source.translated(nextId++, column * pitchX, row * pitchY);
                    result.add(copy);
                    copies.add(copy.id());
                }
            }
        }
        commit(result, copies);
        return (int) added;
    }

    /** Reassigns selected hits to a tool of the requested diameter, creating it if needed. */
    public int resizeSelected(double diameter) {
        if (selected.isEmpty()) throw new IllegalArgumentException("Selecione furos ou slots para redimensionar.");
        if (!Double.isFinite(diameter) || diameter <= 0)
            throw new IllegalArgumentException("Diametro deve ser positivo e finito.");
        int toolId = toolDiameters.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue() - diameter) <= 1e-9)
                .mapToInt(Map.Entry::getKey).min().orElse(-1);
        Map<Integer, Double> nextTools = new LinkedHashMap<>(toolDiameters);
        if (toolId < 0) {
            toolId = toolDiameters.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            nextTools.put(toolId, diameter);
        }
        final int chosenTool = toolId;
        if (hits.stream().filter(hit -> selected.contains(hit.id()))
                .allMatch(hit -> hit.toolId() == chosenTool)) return toolId;
        commit(hits.stream().map(hit -> selected.contains(hit.id()) ? hit.withTool(chosenTool) : hit).toList(),
                selected, nextTools);
        return toolId;
    }

    private static boolean finiteMove(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy))
            throw new IllegalArgumentException("Deslocamento precisa ser finito.");
        return dx != 0 || dy != 0;
    }

    public void addDrill(int toolId, double x, double y) {
        requireToolAndCoordinates(toolId, x, y);
        List<Hit> result = new ArrayList<>(hits);
        result.add(new Hit(nextId++, toolId, false, x, y, x, y));
        commit(result, Set.of());
    }

    public void addSlot(int toolId, double x1, double y1, double x2, double y2) {
        requireToolAndCoordinates(toolId, x1, y1);
        requireToolAndCoordinates(toolId, x2, y2);
        if (x1 == x2 && y1 == y2) throw new IllegalArgumentException("Slot precisa de dois pontos distintos.");
        List<Hit> result = new ArrayList<>(hits);
        result.add(new Hit(nextId++, toolId, true, x1, y1, x2, y2));
        commit(result, Set.of());
    }

    private void requireToolAndCoordinates(int toolId, double x, double y) {
        if (!toolDiameters.containsKey(toolId)) throw new IllegalArgumentException("Ferramenta Excellon inexistente.");
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Coordenadas devem ser finitas.");
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(snapshot());
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(snapshot());
        restore(redo.pop());
        return true;
    }

    public ExcellonImage resultImage() {
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<ExcellonImage.Slot> slots = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.slot()) slots.add(new ExcellonImage.Slot(hit.toolId(), hit.x1(), hit.y1(), hit.x2(), hit.y2()));
            else drills.add(new ExcellonImage.Drill(hit.toolId(), hit.x1(), hit.y1()));
        }
        return ExcellonImage.of(units, toolDiameters, drills, slots, geometry());
    }

    private Geometry geometryOf(List<Hit> entries) {
        if (entries.isEmpty()) return factory.createGeometryCollection();
        return factory.buildGeometry(entries.stream().map(this::footprint).toList());
    }

    private Geometry footprint(Hit hit) {
        double radius = toolDiameters.get(hit.toolId()) / 2.0;
        return hit.slot() ? factory.createLineString(new Coordinate[]{
                new Coordinate(hit.x1(), hit.y1()), new Coordinate(hit.x2(), hit.y2())}).buffer(radius, 16)
                : factory.createPoint(new Coordinate(hit.x1(), hit.y1())).buffer(radius, 16);
    }

    private Snapshot snapshot() { return new Snapshot(hits, Set.copyOf(selected), Map.copyOf(toolDiameters), dirty); }

    private void commit(List<Hit> changed, Collection<Long> nextSelection) {
        commit(changed, nextSelection, toolDiameters);
    }

    private void commit(List<Hit> changed, Collection<Long> nextSelection, Map<Integer, Double> nextTools) {
        Set<Long> retainedSelection = new LinkedHashSet<>(nextSelection);
        undo.push(snapshot());
        redo.clear();
        hits = List.copyOf(changed);
        selected.clear();
        selected.addAll(retainedSelection);
        if (nextTools != toolDiameters) {
            toolDiameters.clear();
            toolDiameters.putAll(nextTools);
        }
        dirty = true;
        rebuildIndex();
    }

    private void restore(Snapshot snapshot) {
        hits = snapshot.hits();
        selected.clear();
        selected.addAll(snapshot.selected());
        toolDiameters.clear();
        toolDiameters.putAll(snapshot.tools());
        dirty = snapshot.dirty();
        rebuildIndex();
    }

    private void rebuildIndex() {
        index = new STRtree();
        for (Hit hit : hits) index.insert(hitBounds(hit), hit);
        index.build();
    }

    private Envelope hitBounds(Hit hit) {
        double radius = toolDiameters.get(hit.toolId()) / 2.0;
        Envelope bounds = new Envelope(hit.x1(), hit.x2(), hit.y1(), hit.y2());
        bounds.expandBy(radius);
        return bounds;
    }
}
