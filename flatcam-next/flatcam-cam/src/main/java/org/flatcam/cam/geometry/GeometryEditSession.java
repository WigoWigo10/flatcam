package org.flatcam.cam.geometry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.geom.util.AffineTransformation;

/** An in-memory geometry edit. Atomic lines and polygons remain individually selectable. */
public final class GeometryEditSession {

    private record Part(Geometry geometry, int toolIndex) {
    }

    private record Snapshot(List<Part> parts, Set<Integer> selection) {
    }

    private final Geometry source;
    private final List<ToolGeometry> sourceTools;
    private final GeometryFactory factory;
    private final List<Part> original;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private List<Part> parts;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private STRtree spatialIndex;
    private Geometry cachedResult;

    public GeometryEditSession(Geometry source, List<ToolGeometry> tools) {
        this.source = Objects.requireNonNull(source);
        this.sourceTools = List.copyOf(Objects.requireNonNull(tools));
        this.factory = source.getFactory();
        List<Part> flattened = new ArrayList<>();
        if (sourceTools.isEmpty()) {
            flatten(source, -1, flattened);
        } else {
            for (int i = 0; i < sourceTools.size(); i++) {
                flatten(sourceTools.get(i).geometry(), i, flattened);
            }
        }
        original = List.copyOf(flattened);
        parts = original;
        rebuildIndex();
    }

    private static void flatten(Geometry geometry, int toolIndex, List<Part> out) {
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                flatten(collection.getGeometryN(i), toolIndex, out);
            }
        } else {
            out.add(new Part(geometry, toolIndex));
        }
    }

    public int shapeCount() {
        return parts.size();
    }

    public int selectedCount() {
        return selected.size();
    }

    public Set<Integer> selectedIndices() {
        return Set.copyOf(selected);
    }

    public boolean isDirty() {
        return !parts.equals(original);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void clearSelection() {
        selected.clear();
    }

    /** Picks the uppermost shape within a world-space tolerance, including thin paths. */
    public void clickSelect(double x, double y, double tolerance, boolean additive) {
        Point point = factory.createPoint(new Coordinate(x, y));
        int hit = -1;
        Envelope search = new Envelope(x, x, y, y);
        search.expandBy(Math.max(0, tolerance));
        for (Object candidate : spatialIndex.query(search)) {
            int i = (Integer) candidate;
            Geometry shape = parts.get(i).geometry();
            if (i > hit && shape.isWithinDistance(point, Math.max(0, tolerance))) {
                hit = i;
            }
        }
        if (!additive) {
            selected.clear();
        }
        if (hit >= 0 && !selected.remove(hit)) {
            selected.add(hit);
        }
    }

    /** Left-to-right encloses; right-to-left touches. Control toggles hits. */
    public void boxSelect(double x1, double y1, double x2, double y2, boolean additive) {
        Envelope box = new Envelope(x1, x2, y1, y2);
        Geometry rectangle = factory.toGeometry(box);
        boolean enclosing = x2 >= x1;
        if (!additive) {
            selected.clear();
        }
        for (Object candidate : spatialIndex.query(box)) {
            int i = (Integer) candidate;
            Geometry shape = parts.get(i).geometry();
            boolean hit = enclosing ? rectangle.covers(shape) : rectangle.intersects(shape);
            if (hit && (!additive || !selected.remove(i))) {
                selected.add(i);
            }
        }
    }

    public boolean deleteSelected() {
        if (selected.isEmpty()) {
            return false;
        }
        undo.push(snapshot());
        redo.clear();
        List<Part> remaining = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (!selected.contains(i)) {
                remaining.add(parts.get(i));
            }
        }
        parts = List.copyOf(remaining);
        selected.clear();
        rebuildIndex();
        return true;
    }

    public boolean moveSelected(double dx, double dy) {
        if (selected.isEmpty() || !finiteDisplacement(dx, dy)) {
            return false;
        }
        AffineTransformation translation = AffineTransformation.translationInstance(dx, dy);
        List<Part> moved = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            moved.add(selected.contains(i) ? new Part(translation.transform(part.geometry()), part.toolIndex()) : part);
        }
        commit(moved, Set.copyOf(selected));
        return true;
    }

    public boolean copySelected(double dx, double dy) {
        if (selected.isEmpty() || !finiteDisplacement(dx, dy)) {
            return false;
        }
        AffineTransformation translation = AffineTransformation.translationInstance(dx, dy);
        List<Part> copied = new ArrayList<>(parts);
        Set<Integer> newSelection = new LinkedHashSet<>();
        for (int i = 0; i < parts.size(); i++) {
            if (selected.contains(i)) {
                Part part = parts.get(i);
                newSelection.add(copied.size());
                copied.add(new Part(translation.transform(part.geometry()), part.toolIndex()));
            }
        }
        commit(copied, newSelection);
        return true;
    }

    private static boolean finiteDisplacement(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
            throw new IllegalArgumentException("Deslocamento X/Y deve ser finito.");
        }
        return dx != 0 || dy != 0;
    }

    public void addPath(List<Coordinate> points, int toolIndex) {
        Coordinate[] coordinates = coordinates(points, 2);
        Geometry path = factory.createLineString(coordinates);
        if (path.getLength() <= 0) {
            throw new IllegalArgumentException("O caminho precisa de dois pontos distintos.");
        }
        addShape(path, toolIndex);
    }

    public void addPolygon(List<Coordinate> points, int toolIndex) {
        Coordinate[] coordinates = coordinates(points, 3);
        if (coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            coordinates = java.util.Arrays.copyOf(coordinates, coordinates.length - 1);
        }
        if (coordinates.length < 3) {
            throw new IllegalArgumentException("O poligono precisa de tres pontos distintos.");
        }
        Coordinate[] ring = java.util.Arrays.copyOf(coordinates, coordinates.length + 1);
        ring[ring.length - 1] = new Coordinate(coordinates[0]);
        Polygon polygon = factory.createPolygon(ring);
        if (polygon.isEmpty() || polygon.getArea() <= 0 || !polygon.isValid()) {
            throw new IllegalArgumentException("O poligono precisa ter area positiva e nao pode se cruzar.");
        }
        addShape(polygon, toolIndex);
    }

    public void addRectangle(double x1, double y1, double x2, double y2, int toolIndex) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)
                || x1 == x2 || y1 == y2) {
            throw new IllegalArgumentException("Escolha dois cantos diferentes para o retangulo.");
        }
        addShape(factory.toGeometry(new Envelope(x1, x2, y1, y2)), toolIndex);
    }

    public void addCircle(double centerX, double centerY, double perimeterX, double perimeterY, int toolIndex) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(perimeterX) || !Double.isFinite(perimeterY)) {
            throw new IllegalArgumentException("As coordenadas do circulo devem ser finitas.");
        }
        double radius = Math.hypot(perimeterX - centerX, perimeterY - centerY);
        if (!(radius > 0) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("Escolha um ponto do perimetro diferente do centro.");
        }
        // Python's geometry_circle_steps defaults to 64 segments: 16 per quadrant.
        addShape(factory.createPoint(new Coordinate(centerX, centerY)).buffer(radius, 16), toolIndex);
    }

    private static Coordinate[] coordinates(List<Coordinate> points, int minimum) {
        Objects.requireNonNull(points);
        if (points.size() < minimum) {
            throw new IllegalArgumentException("Pontos insuficientes para criar a forma.");
        }
        Coordinate[] result = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Coordinate point = Objects.requireNonNull(points.get(i));
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                throw new IllegalArgumentException("As coordenadas devem ser finitas.");
            }
            result[i] = new Coordinate(point);
        }
        return result;
    }

    private void addShape(Geometry shape, int toolIndex) {
        if (sourceTools.isEmpty() ? toolIndex != -1 : toolIndex < 0 || toolIndex >= sourceTools.size()) {
            throw new IllegalArgumentException("Selecione uma ferramenta valida para a nova forma.");
        }
        List<Part> updated = new ArrayList<>(parts);
        updated.add(new Part(shape, toolIndex));
        commit(updated, Set.of());
    }

    private void commit(List<Part> updated, Set<Integer> newSelection) {
        undo.push(snapshot());
        redo.clear();
        parts = List.copyOf(updated);
        selected.clear();
        selected.addAll(newSelection);
        rebuildIndex();
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(snapshot());
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(snapshot());
        restore(redo.pop());
        return true;
    }

    private Snapshot snapshot() {
        return new Snapshot(parts, Set.copyOf(selected));
    }

    private void restore(Snapshot snapshot) {
        parts = snapshot.parts();
        selected.clear();
        selected.addAll(snapshot.selection());
        rebuildIndex();
    }

    public Geometry selectedGeometry() {
        List<Geometry> shapes = new ArrayList<>(selected.size());
        for (int index : selected) {
            shapes.add(parts.get(index).geometry());
        }
        return build(shapes);
    }

    /** Cheap highlight for very large selections; editing still targets every selected part. */
    public Geometry selectedBounds() {
        Envelope bounds = new Envelope();
        for (int index : selected) {
            bounds.expandToInclude(parts.get(index).geometry().getEnvelopeInternal());
        }
        return selected.isEmpty() ? factory.createGeometryCollection() : factory.toGeometry(bounds);
    }

    public Geometry resultGeometry() {
        if (!isDirty()) {
            return source;
        }
        if (cachedResult == null) {
            cachedResult = geometryFor(-2);
        }
        return cachedResult;
    }

    public List<ToolGeometry> resultTools() {
        if (!isDirty()) {
            return sourceTools;
        }
        List<ToolGeometry> result = new ArrayList<>(sourceTools.size());
        for (int i = 0; i < sourceTools.size(); i++) {
            result.add(new ToolGeometry(sourceTools.get(i).toolDiameter(), geometryFor(i)));
        }
        return List.copyOf(result);
    }

    private Geometry geometryFor(int toolIndex) {
        List<Geometry> geometries = new ArrayList<>();
        for (Part part : parts) {
            if (toolIndex == -2 || part.toolIndex() == toolIndex) {
                geometries.add(part.geometry());
            }
        }
        return build(geometries);
    }

    private Geometry build(List<Geometry> geometries) {
        return geometries.isEmpty() ? factory.createGeometryCollection() : factory.buildGeometry(geometries);
    }

    private void rebuildIndex() {
        spatialIndex = new STRtree();
        for (int i = 0; i < parts.size(); i++) {
            spatialIndex.insert(parts.get(i).geometry().getEnvelopeInternal(), i);
        }
        spatialIndex.build();
        cachedResult = null;
    }
}
