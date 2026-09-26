package org.flatcam.cam.gerber.edit;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * An open editing session for one Gerber object - the Gerber Editor's
 * session lifecycle (CONTEXTO_E_PROGRESSO.md section 9.4, slice 1) plus
 * shape selection/hit-testing (slice 2), ported from AppGerberEditor.py's
 * edit_fcgerber()/update_fcgerber()/deactivate_grb_editor() and
 * SelectEditorGrb/draw_selection_area_handler().
 *
 * <p>Python's editor loads the source object's per-aperture shapes into an
 * editable storage_dict and, on Apply, builds a NEW Gerber object named
 * "&lt;name&gt;_edit" (or "&lt;name&gt;_edit_N") rather than overwriting the
 * original; Cancel discards the session with no new object.
 *
 * <p>Edits operate on the individual shapes, with undo/redo snapshots of the
 * shape list. {@link #apply()} rebuilds the solid, follow and per-aperture
 * geometries and publishes a new image. Selection follows Python's rules:
 * <ul>
 *   <li>Only dark shapes are selectable - Python stores clear shapes under
 *       'clear' only, and its hit tests read 'solid'.</li>
 *   <li>A plain click clears the selection and selects every shape under the
 *       point; with the multi-select key (Python's default: Control) it
 *       toggles those shapes instead. Python actually toggles at most one hit
 *       per 77-shape chunk of its multiprocessing split - an artifact of how
 *       it parallelized the test, not a rule, so every hit is toggled here.</li>
 *   <li>A box dragged left-to-right selects shapes it fully contains; right-
 *       to-left, shapes it touches (app_Main.py's selection_type). Plain
 *       replaces the selection; with the multi-select key, toggles.</li>
 * </ul>
 */
public final class GerberEditSession {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int HISTORY_LIMIT = 100;

    private record EditState(List<GerberShape> shapes, Map<String, Aperture> apertures, Set<Integer> selected) {
    }

    private final String sourceName;
    private final GerberImage sourceImage;
    private final List<GerberShape> originalShapes;
    private final Map<String, Aperture> originalApertures;
    private List<GerberShape> shapes;
    private Map<String, Aperture> apertures;
    private final boolean shapesApproximated;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final Deque<EditState> undoStack = new ArrayDeque<>();
    private final Deque<EditState> redoStack = new ArrayDeque<>();
    private GerberImage cachedWorkingImage;

    public GerberEditSession(String sourceName, GerberImage sourceImage) {
        this.sourceName = sourceName;
        this.sourceImage = sourceImage;
        this.cachedWorkingImage = sourceImage;
        this.originalApertures = sourceImage.apertures();
        this.apertures = originalApertures;
        if (!sourceImage.shapes().isEmpty()) {
            this.shapes = sourceImage.shapes();
            this.shapesApproximated = false;
        } else {
            this.shapes = shapesFromApertureAggregates(sourceImage);
            this.shapesApproximated = true;
        }
        this.originalShapes = this.shapes;
    }

    public String sourceName() {
        return sourceName;
    }

    public Map<String, Aperture> apertures() {
        return apertures;
    }

    public GerberImage workingImage() {
        if (cachedWorkingImage == null) {
            cachedWorkingImage = sourceImage.withEditedShapes(shapes, apertures, () -> false, ignored -> {});
        }
        return cachedWorkingImage;
    }

    public boolean isDirty() {
        return shapes != originalShapes || apertures != originalApertures;
    }

    /** Every editable shape, dark and clear, in file order; selection indices refer to this list. */
    public List<GerberShape> shapes() {
        return shapes;
    }

    /**
     * True when the source image didn't carry its individual shapes (restored
     * from a project file, which keeps one aggregate per aperture) and
     * {@link #shapes()} was rebuilt by splitting each aperture's aggregate into
     * its disjoint parts - touching pads/tracks of one aperture then come out
     * as a single shape, and regions (not part of any aperture's aggregate)
     * are absent.
     */
    public boolean shapesApproximated() {
        return shapesApproximated;
    }

    public Set<Integer> selectedIndices() {
        return Collections.unmodifiableSet(selected);
    }

    public List<GerberShape> selectedShapes() {
        List<GerberShape> result = new ArrayList<>(selected.size());
        for (int index : selected) {
            result.add(shapes.get(index));
        }
        return result;
    }

    /** Apertures of the selected shapes, in selection order - Python highlights these rows in its apertures table. */
    public Set<String> selectedApertures() {
        Set<String> result = new LinkedHashSet<>();
        for (int index : selected) {
            result.add(shapes.get(index).apertureCode());
        }
        return result;
    }

    public void clearSelection() {
        selected.clear();
    }

    public void clickSelect(double x, double y, boolean additive) {
        if (!additive) {
            selected.clear();
        }
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
        for (int i = 0; i < shapes.size(); i++) {
            GerberShape shape = shapes.get(i);
            if (shape.clear() || !shape.geometry().getEnvelopeInternal().intersects(x, y)) {
                continue;
            }
            if (point.intersects(shape.geometry())) {
                toggle(i);
            }
        }
    }

    /**
     * Rubber-band selection from the press point (x1, y1) to the release
     * point (x2, y2); the drag direction picks enclosing (x2 &gt;= x1) vs.
     * touching (x2 &lt; x1), exactly as app_Main.py's {@code dx} test does.
     */
    public void boxSelect(double x1, double y1, double x2, double y2, boolean additive) {
        boolean enclosing = x2 >= x1;
        if (!additive) {
            selected.clear();
        }
        Envelope box = new Envelope(x1, x2, y1, y2);
        Geometry rectangle = GEOMETRY_FACTORY.toGeometry(box);
        for (int i = 0; i < shapes.size(); i++) {
            GerberShape shape = shapes.get(i);
            if (shape.clear()) {
                continue;
            }
            Envelope envelope = shape.geometry().getEnvelopeInternal();
            boolean hit = enclosing
                    ? box.covers(envelope) && rectangle.covers(shape.geometry())
                    : box.intersects(envelope) && rectangle.intersects(shape.geometry());
            if (!hit) {
                continue;
            }
            if (additive) {
                toggle(i);
            } else {
                selected.add(i);
            }
        }
    }

    private void toggle(int index) {
        if (!selected.remove(index)) {
            selected.add(index);
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo() {
        if (!canUndo()) {
            return false;
        }
        redoStack.push(snapshot());
        restore(undoStack.pop());
        return true;
    }

    public boolean redo() {
        if (!canRedo()) {
            return false;
        }
        undoStack.push(snapshot());
        restore(redoStack.pop());
        return true;
    }

    public boolean deleteSelected() {
        requireExactShapes();
        if (selected.isEmpty()) {
            return false;
        }
        List<GerberShape> remaining = new ArrayList<>(shapes.size() - selected.size());
        for (int i = 0; i < shapes.size(); i++) {
            if (!selected.contains(i)) {
                remaining.add(shapes.get(i));
            }
        }
        commit(remaining, Set.of());
        return true;
    }

    public boolean moveSelected(double dx, double dy) {
        requireExactShapes();
        validateOffset(dx, dy);
        if (selected.isEmpty() || (dx == 0 && dy == 0)) {
            return false;
        }
        TransformOp.Offset offset = new TransformOp.Offset(dx, dy);
        List<GerberShape> moved = new ArrayList<>(shapes);
        for (int index : selected) {
            moved.set(index, translated(shapes.get(index), offset));
        }
        commit(moved, selected);
        return true;
    }

    public boolean copySelected(double dx, double dy) {
        requireExactShapes();
        validateOffset(dx, dy);
        if (selected.isEmpty()) {
            return false;
        }
        TransformOp.Offset offset = new TransformOp.Offset(dx, dy);
        List<GerberShape> copied = new ArrayList<>(shapes.size() + selected.size());
        Set<Integer> newSelection = new LinkedHashSet<>();
        for (int i = 0; i < shapes.size(); i++) {
            copied.add(shapes.get(i));
            if (selected.contains(i)) {
                newSelection.add(copied.size());
                copied.add(translated(shapes.get(i), offset));
            }
        }
        commit(copied, newSelection);
        return true;
    }

    /** Flashes an existing standard or resolved macro aperture at one point. */
    public boolean addPad(String apertureCode, double x, double y) {
        requireExactShapes();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Pad coordinates must be finite");
        }
        Aperture aperture = apertures.get(apertureCode);
        if (aperture == null || !supportsPad(aperture)) {
            throw new IllegalArgumentException("Select a valid C, R, O, P or macro aperture");
        }
        Point center = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
        GerberShape pad = new GerberShape(apertureCode,
                aperture.footprintAt(x, y, GEOMETRY_FACTORY), false, center);
        List<GerberShape> updated = new ArrayList<>(shapes);
        updated.add(pad);
        commit(updated, Set.of(updated.size() - 1));
        return true;
    }

    public boolean addCircularPad(String apertureCode, double x, double y) {
        Aperture aperture = apertures.get(apertureCode);
        if (aperture == null || aperture.kind != ApertureKind.CIRCLE) {
            throw new IllegalArgumentException("Select a circular aperture with a positive diameter");
        }
        return addPad(apertureCode, x, y);
    }

    /** Places a linear pad array; the whole array is one undo step. */
    public boolean addLinearPadArray(String code, double x, double y, int count,
                                     double pitch, double angleDegrees) {
        validateArray(count, x, y, pitch, angleDegrees);
        if (pitch <= 0 && count > 1) {
            throw new IllegalArgumentException("Pad pitch must be positive");
        }
        double radians = Math.toRadians(angleDegrees);
        List<Coordinate> centers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            centers.add(new Coordinate(x + i * pitch * Math.cos(radians),
                    y + i * pitch * Math.sin(radians)));
        }
        return addPadArray(code, centers);
    }

    /** Places a circular pad array around a center; positive angles rotate counterclockwise. */
    public boolean addCircularPadArray(String code, double centerX, double centerY, int count,
                                       double radius, double startDegrees, double stepDegrees) {
        validateArray(count, centerX, centerY, radius, startDegrees);
        if (!Double.isFinite(stepDegrees) || radius <= 0) {
            throw new IllegalArgumentException("Circular pad array needs a positive radius and finite angle");
        }
        List<Coordinate> centers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double radians = Math.toRadians(startDegrees + i * stepDegrees);
            centers.add(new Coordinate(centerX + radius * Math.cos(radians),
                    centerY + radius * Math.sin(radians)));
        }
        return addPadArray(code, centers);
    }

    private static void validateArray(int count, double x, double y, double distance, double angle) {
        if (count < 1 || count > 1000 || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(distance) || !Double.isFinite(angle)) {
            throw new IllegalArgumentException("Pad array needs 1-1000 items and finite coordinates and spacing");
        }
    }

    private boolean addPadArray(String code, List<Coordinate> centers) {
        requireExactShapes();
        Aperture aperture = apertures.get(code);
        if (aperture == null || !supportsPad(aperture)) {
            throw new IllegalArgumentException("Select a valid pad aperture");
        }
        List<GerberShape> updated = new ArrayList<>(shapes);
        Set<Integer> newSelection = new LinkedHashSet<>();
        for (Coordinate center : centers) {
            if (!Double.isFinite(center.x) || !Double.isFinite(center.y)) {
                throw new IllegalArgumentException("Pad array coordinates overflowed");
            }
            Point point = GEOMETRY_FACTORY.createPoint(center);
            updated.add(new GerberShape(code, aperture.footprintAt(center.x, center.y, GEOMETRY_FACTORY),
                    false, point));
            newSelection.add(updated.size() - 1);
        }
        commit(updated, newSelection);
        return true;
    }

    /** Marks dark polygonal shapes whose area is strictly between the limits. */
    public int selectAreaRange(double lower, double upper) {
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower < 0 || upper <= lower) {
            throw new IllegalArgumentException("Area limits must be finite, nonnegative and increasing");
        }
        selected.clear();
        for (int index = 0; index < shapes.size(); index++) {
            GerberShape shape = shapes.get(index);
            double area = shape.geometry().getArea();
            if (!shape.clear() && area > lower && area < upper) {
                selected.add(index);
            }
        }
        return selected.size();
    }

    /** Subtracts translated selected shapes from dark geometry, as an atomic eraser action. */
    public boolean eraseWithSelected(double dx, double dy) {
        requireExactShapes();
        validateOffset(dx, dy);
        if (selected.isEmpty()) {
            return false;
        }
        TransformOp.Offset offset = new TransformOp.Offset(dx, dy);
        Geometry footprint = UnaryUnionOp.union(selected.stream()
                .map(index -> offset.apply(shapes.get(index).geometry())).toList());
        if (footprint == null || footprint.isEmpty() || !footprint.isValid()) {
            throw new IllegalArgumentException("Eraser footprint is invalid");
        }
        List<GerberShape> erased = new ArrayList<>(shapes.size());
        boolean changed = false;
        for (GerberShape shape : shapes) {
            if (shape.clear() || !shape.geometry().intersects(footprint)) {
                erased.add(shape);
                continue;
            }
            Geometry difference = shape.geometry().difference(footprint);
            if (difference.equalsTopo(shape.geometry())) {
                erased.add(shape);
                continue;
            }
            changed = true;
            if (!difference.isEmpty()) {
                if (!difference.isValid()) {
                    throw new IllegalArgumentException("Eraser would create invalid geometry");
                }
                erased.add(new GerberShape(shape.apertureCode(), difference, false, difference.getBoundary()));
            }
        }
        if (changed) {
            commit(erased, Set.of());
        }
        return changed;
    }

    /** Adds one straight D01 stroke with its centerline, using an existing circular aperture. */
    public boolean addTrack(String apertureCode, double x1, double y1, double x2, double y2) {
        return addTrack(apertureCode, List.of(new Coordinate(x1, y1), new Coordinate(x2, y2)));
    }

    /**
     * Adds one multi-segment D01 stroke as a single editable shape. Consecutive
     * duplicate points are ignored, and the entire track is one undo step.
     */
    public boolean addTrack(String apertureCode, List<Coordinate> points) {
        requireExactShapes();
        Aperture aperture = apertures.get(apertureCode);
        if (aperture == null || aperture.kind != ApertureKind.CIRCLE
                || !Double.isFinite(aperture.width) || aperture.width <= 0) {
            throw new IllegalArgumentException("Select a circular aperture with a positive diameter");
        }
        if (points == null) {
            throw new IllegalArgumentException("Track points are required");
        }
        List<Coordinate> normalized = new ArrayList<>(points.size());
        for (Coordinate point : points) {
            if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                throw new IllegalArgumentException("Track coordinates must be finite");
            }
            if (normalized.isEmpty() || !normalized.get(normalized.size() - 1).equals2D(point)) {
                normalized.add(new Coordinate(point));
            }
        }
        if (normalized.size() < 2) {
            return false;
        }
        LineString centerline = GEOMETRY_FACTORY.createLineString(normalized.toArray(Coordinate[]::new));
        GerberShape track = new GerberShape(apertureCode,
                centerline.buffer(aperture.strokeRadius(), 16), false, centerline);
        List<GerberShape> updated = new ArrayList<>(shapes);
        updated.add(track);
        commit(updated, Set.of(updated.size() - 1));
        return true;
    }

    /** Scales each selected shape about its own envelope center, like the legacy editor. */
    public boolean scaleSelected(double factor) {
        requireExactShapes();
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("Scale factor must be finite and positive");
        }
        if (selected.isEmpty() || factor == 1) {
            return false;
        }
        List<GerberShape> scaled = new ArrayList<>(shapes);
        for (int index : selected) {
            GerberShape shape = shapes.get(index);
            Envelope bounds = shape.geometry().getEnvelopeInternal();
            double cx = (bounds.getMinX() + bounds.getMaxX()) / 2;
            double cy = (bounds.getMinY() + bounds.getMaxY()) / 2;
            AffineTransformation transform = AffineTransformation.scaleInstance(factor, factor, cx, cy);
            scaled.set(index, new GerberShape(shape.apertureCode(), transform.transform(shape.geometry()),
                    shape.clear(), shape.followGeometry() == null ? null : transform.transform(shape.followGeometry())));
        }
        commit(scaled, selected);
        return true;
    }

    /** Applies an affine transform to the current selection around an explicit reference point. */
    public boolean transformSelected(String operation, double value, double pivotX, double pivotY) {
        requireExactShapes();
        if (!Double.isFinite(value) || !Double.isFinite(pivotX) || !Double.isFinite(pivotY)) {
            throw new IllegalArgumentException("Transform values and reference point must be finite");
        }
        if (selected.isEmpty()) {
            return false;
        }
        AffineTransformation transform = switch (operation) {
            case "rotate" -> AffineTransformation.rotationInstance(Math.toRadians(value), pivotX, pivotY);
            case "mirror_x" -> AffineTransformation.scaleInstance(1, -1, pivotX, pivotY);
            case "mirror_y" -> AffineTransformation.scaleInstance(-1, 1, pivotX, pivotY);
            case "scale_x" -> AffineTransformation.scaleInstance(positiveScale(value), 1, pivotX, pivotY);
            case "scale_y" -> AffineTransformation.scaleInstance(1, positiveScale(value), pivotX, pivotY);
            case "skew_x", "skew_y" -> {
                if (Math.abs(value) >= 89.9) {
                    throw new IllegalArgumentException("Skew angle must stay below 89.9 degrees");
                }
                double tangent = Math.tan(Math.toRadians(value));
                yield operation.equals("skew_x")
                        ? new AffineTransformation(1, tangent, -tangent * pivotY, 0, 1, 0)
                        : new AffineTransformation(1, 0, 0, tangent, 1, -tangent * pivotX);
            }
            default -> throw new IllegalArgumentException("Unknown Gerber transform");
        };
        if (value == 0 && (operation.equals("rotate") || operation.startsWith("skew_"))
                || value == 1 && operation.startsWith("scale_")) {
            return false;
        }
        List<GerberShape> changed = new ArrayList<>(shapes);
        for (int index : selected) {
            GerberShape shape = shapes.get(index);
            Geometry geometry = transform.transform(shape.geometry());
            Geometry follow = shape.followGeometry() == null ? null : transform.transform(shape.followGeometry());
            if (geometry.isEmpty() || !geometry.isValid()) {
                throw new IllegalArgumentException("Transform produced invalid geometry");
            }
            changed.set(index, new GerberShape(shape.apertureCode(), geometry, shape.clear(), follow));
        }
        commit(changed, selected);
        return true;
    }

    private static double positiveScale(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Scale factor must be positive");
        }
        return factor;
    }

    /** Buffers selected shapes; the new boundary becomes their editable follow path. */
    public boolean bufferSelected(double distance, int joinStyle) {
        requireExactShapes();
        if (!Double.isFinite(distance) || (joinStyle != BufferParameters.JOIN_ROUND
                && joinStyle != BufferParameters.JOIN_MITRE && joinStyle != BufferParameters.JOIN_BEVEL)) {
            throw new IllegalArgumentException("Buffer distance or corner style is invalid");
        }
        if (selected.isEmpty() || distance == 0) {
            return false;
        }
        BufferParameters parameters = new BufferParameters(16, BufferParameters.CAP_ROUND, joinStyle, 5);
        List<GerberShape> buffered = new ArrayList<>(shapes);
        for (int index : selected) {
            GerberShape shape = shapes.get(index);
            Geometry geometry = BufferOp.bufferOp(shape.geometry(), distance, parameters);
            if (geometry.isEmpty() || !geometry.isValid()) {
                throw new IllegalArgumentException("Buffer would remove or invalidate a selected shape");
            }
            buffered.set(index, new GerberShape(shape.apertureCode(), geometry, shape.clear(), geometry.getBoundary()));
        }
        commit(buffered, selected);
        return true;
    }

    /** Fuses selected dark shapes into region geometry in one undo step. */
    public boolean polygonizeSelected() {
        requireExactShapes();
        if (selected.isEmpty()) {
            return false;
        }
        int first = selected.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int last = selected.stream().mapToInt(Integer::intValue).max().orElseThrow();
        String code = shapes.get(first).apertureCode();
        for (int index : selected) {
            GerberShape shape = shapes.get(index);
            if (shape.clear() || !code.equals(shape.apertureCode()) || !shape.geometry().isValid()
                    || shape.geometry().getDimension() != 2) {
                throw new IllegalArgumentException("Poligonizar exige formas validas da mesma abertura");
            }
        }
        for (int index = first; index <= last; index++) {
            if (shapes.get(index).clear()) {
                throw new IllegalArgumentException("Nao e seguro poligonizar atraves de uma operacao clear");
            }
        }
        Geometry merged = UnaryUnionOp.union(selected.stream().sorted()
                .map(index -> shapes.get(index).geometry()).toList());
        if (merged.isEmpty() || !merged.isValid() || merged.getDimension() != 2) {
            throw new IllegalArgumentException("As formas nao produziram uma regiao valida");
        }
        List<GerberShape> updated = new ArrayList<>(shapes.size() - selected.size() + 1);
        Set<Integer> newSelection = new LinkedHashSet<>();
        for (int index = 0; index < shapes.size(); index++) {
            if (index == first) {
                newSelection.add(updated.size());
                updated.add(new GerberShape(GerberShape.REGION_APERTURE, merged, false,
                        merged.getBoundary()));
            }
            if (!selected.contains(index)) {
                updated.add(shapes.get(index));
            }
        }
        commit(updated, newSelection);
        return true;
    }

    /** Adds one filled G36/G37 region, independent of the selected D-code. */
    public boolean addRegion(List<Coordinate> points) {
        requireExactShapes();
        List<Coordinate> ring = normalizedPoints(points);
        if (ring.size() > 1 && ring.get(0).equals2D(ring.get(ring.size() - 1))) {
            ring.remove(ring.size() - 1);
        }
        if (ring.size() < 3) {
            return false;
        }
        ring.add(new Coordinate(ring.get(0)));
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(ring.toArray(Coordinate[]::new));
        if (polygon.isEmpty() || polygon.getArea() <= 0 || !polygon.isValid()) {
            throw new IllegalArgumentException("Region must be a simple polygon with nonzero area");
        }
        List<GerberShape> updated = new ArrayList<>(shapes);
        updated.add(new GerberShape(GerberShape.REGION_APERTURE, polygon, false, polygon.getExteriorRing()));
        commit(updated, Set.of(updated.size() - 1));
        return true;
    }

    /** Adds a filled circular region with the requested radius. */
    public boolean addDisc(double centerX, double centerY, double radius) {
        requireExactShapes();
        validateCircle(centerX, centerY, radius);
        Polygon disc = (Polygon) GEOMETRY_FACTORY.createPoint(new Coordinate(centerX, centerY)).buffer(radius, 64);
        return addRegion(List.of(disc.getExteriorRing().getCoordinates()));
    }

    /** Adds a filled circular segment between arc endpoints, closed by their chord. */
    public boolean addSemiDisc(double centerX, double centerY, double radius,
                               double startDegrees, double sweepDegrees) {
        requireExactShapes();
        validateCircle(centerX, centerY, radius);
        if (!Double.isFinite(startDegrees) || !Double.isFinite(sweepDegrees)
                || Math.abs(sweepDegrees) < 0.01 || Math.abs(sweepDegrees) >= 360) {
            throw new IllegalArgumentException("Arc sweep must be finite, nonzero and below 360 degrees");
        }
        int segments = Math.max(2, (int) Math.ceil(Math.abs(sweepDegrees) / 360 * 256));
        List<Coordinate> arc = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(startDegrees + sweepDegrees * i / segments);
            arc.add(new Coordinate(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle)));
        }
        return addRegion(arc);
    }

    private static void validateCircle(double x, double y, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("Circle needs finite coordinates and positive radius");
        }
    }

    private static List<Coordinate> normalizedPoints(List<Coordinate> points) {
        if (points == null) {
            throw new IllegalArgumentException("Points are required");
        }
        List<Coordinate> normalized = new ArrayList<>(points.size());
        for (Coordinate point : points) {
            if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)) {
                throw new IllegalArgumentException("Coordinates must be finite");
            }
            if (normalized.isEmpty() || !normalized.get(normalized.size() - 1).equals2D(point)) {
                normalized.add(new Coordinate(point));
            }
        }
        return normalized;
    }

    /** Creates the next free D-code (D10+) for a standard C/R/O aperture. */
    public String addAperture(ApertureKind kind, double width, double height) {
        requireExactShapes();
        if (kind != ApertureKind.CIRCLE && kind != ApertureKind.RECTANGLE && kind != ApertureKind.OBROUND) {
            throw new IllegalArgumentException("Only C, R and O apertures can be created here");
        }
        if (!Double.isFinite(width) || width <= 0
                || (kind != ApertureKind.CIRCLE && (!Double.isFinite(height) || height <= 0))) {
            throw new IllegalArgumentException("Aperture dimensions must be finite and positive");
        }
        int nextCode = 10;
        while (nextCode <= 9999 && apertures.containsKey(Integer.toString(nextCode))) {
            nextCode++;
        }
        if (nextCode > 9999) {
            throw new IllegalStateException("No free Gerber D-code is available");
        }
        String code = Integer.toString(nextCode);
        Map<String, Aperture> updated = new java.util.LinkedHashMap<>(apertures);
        updated.put(code, switch (kind) {
            case CIRCLE -> Aperture.circle(width);
            case RECTANGLE -> Aperture.rectangle(width, height);
            case OBROUND -> Aperture.obround(width, height);
            default -> throw new IllegalArgumentException("Unsupported aperture type");
        });
        undoStack.push(snapshot());
        if (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
        apertures = Map.copyOf(updated);
        cachedWorkingImage = null;
        return code;
    }

    public String addCircularAperture(double diameter) {
        return addAperture(ApertureKind.CIRCLE, diameter, diameter);
    }

    /** Adds a regular polygon aperture with its original diameter, vertex count and rotation. */
    public String addPolygonAperture(double diameter, int vertices, double rotation) {
        requireExactShapes();
        if (!Double.isFinite(diameter) || diameter <= 0 || !Double.isFinite(rotation)
                || vertices < 3 || vertices > 12) {
            throw new IllegalArgumentException("Polygon aperture needs a positive diameter, 3-12 vertices and finite rotation");
        }
        int nextCode = nextFreeApertureCode();
        String code = Integer.toString(nextCode);
        Map<String, Aperture> updated = new java.util.LinkedHashMap<>(apertures);
        updated.put(code, Aperture.polygon(diameter, vertices, rotation));
        commitApertures(updated);
        return code;
    }

    /** Changes a D-code without changing its geometry. */
    public boolean renameAperture(String oldCode, String newCode) {
        requireExactShapes();
        if (!apertures.containsKey(oldCode)) {
            throw new IllegalArgumentException("Unknown aperture D" + oldCode);
        }
        validateApertureCode(newCode);
        if (oldCode.equals(newCode)) {
            return false;
        }
        if (apertures.containsKey(newCode)) {
            throw new IllegalArgumentException("Aperture D" + newCode + " already exists");
        }
        Map<String, Aperture> updated = new java.util.LinkedHashMap<>(apertures);
        Aperture aperture = updated.remove(oldCode);
        updated.put(newCode, aperture);
        List<GerberShape> renamed = new ArrayList<>(shapes.size());
        for (GerberShape shape : shapes) {
            renamed.add(oldCode.equals(shape.apertureCode())
                    ? new GerberShape(newCode, shape.geometry(), shape.clear(), shape.followGeometry()) : shape);
        }
        commit(renamed, selected);
        apertures = Map.copyOf(updated);
        return true;
    }

    /** Deletes an aperture and every shape drawn with it, as the legacy editor does. */
    public boolean deleteAperture(String code) {
        requireExactShapes();
        if (!apertures.containsKey(code)) {
            return false;
        }
        Map<String, Aperture> updated = new java.util.LinkedHashMap<>(apertures);
        updated.remove(code);
        List<GerberShape> remaining = shapes.stream()
                .filter(shape -> !code.equals(shape.apertureCode())).toList();
        commit(remaining, Set.of());
        apertures = Map.copyOf(updated);
        return true;
    }

    /** Resizes an aperture and rebuilds its flashes and strokes from their follow geometry. */
    public boolean resizeAperture(String code, Aperture replacement) {
        requireExactShapes();
        Aperture previous = apertures.get(code);
        if (previous == null || replacement == null || previous.kind != replacement.kind
                || previous.kind == ApertureKind.MACRO) {
            throw new IllegalArgumentException("Select an existing standard aperture of the same type");
        }
        if (!Double.isFinite(replacement.width) || replacement.width <= 0
                || !Double.isFinite(replacement.height) || replacement.height <= 0) {
            throw new IllegalArgumentException("Aperture dimensions must be positive");
        }
        List<GerberShape> resized = new ArrayList<>(shapes.size());
        for (GerberShape shape : shapes) {
            if (!code.equals(shape.apertureCode())) {
                resized.add(shape);
                continue;
            }
            Geometry follow = shape.followGeometry();
            if (follow instanceof Point center) {
                resized.add(new GerberShape(code,
                        replacement.footprintAt(center.getX(), center.getY(), GEOMETRY_FACTORY),
                        shape.clear(), follow));
            } else if (follow instanceof LineString line && previous.kind == ApertureKind.CIRCLE) {
                resized.add(new GerberShape(code, line.buffer(replacement.strokeRadius(), 16),
                        shape.clear(), follow));
            } else {
                throw new IllegalStateException("Cannot safely resize an aperture with shapes lacking a supported centerline");
            }
        }
        Map<String, Aperture> updated = new java.util.LinkedHashMap<>(apertures);
        updated.put(code, replacement);
        commit(resized, selected);
        apertures = Map.copyOf(updated);
        return true;
    }

    private static void validateApertureCode(String code) {
        try {
            int number = Integer.parseInt(code);
            if (number >= 10 && number <= 9999 && code.equals(Integer.toString(number))) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // Handled below.
        }
        throw new IllegalArgumentException("D-code must be an integer between 10 and 9999");
    }

    private int nextFreeApertureCode() {
        int next = 10;
        while (next <= 9999 && apertures.containsKey(Integer.toString(next))) {
            next++;
        }
        if (next > 9999) {
            throw new IllegalStateException("No free Gerber D-code is available");
        }
        return next;
    }

    private void commitApertures(Map<String, Aperture> updated) {
        commit(shapes, selected);
        apertures = Map.copyOf(updated);
    }

    private static boolean supportsPad(Aperture aperture) {
        if (aperture.kind == ApertureKind.MACRO) {
            return true;
        }
        return (aperture.kind == ApertureKind.CIRCLE || aperture.kind == ApertureKind.RECTANGLE
                || aperture.kind == ApertureKind.OBROUND || aperture.kind == ApertureKind.POLYGON)
                && Double.isFinite(aperture.width) && aperture.width > 0
                && Double.isFinite(aperture.height) && aperture.height > 0;
    }

    private static GerberShape translated(GerberShape shape, TransformOp.Offset offset) {
        return new GerberShape(shape.apertureCode(), offset.apply(shape.geometry()), shape.clear(),
                shape.followGeometry() == null ? null : offset.apply(shape.followGeometry()));
    }

    private static void validateOffset(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
            throw new IllegalArgumentException("Move/copy offset must be finite");
        }
    }

    private void requireExactShapes() {
        if (shapesApproximated) {
            throw new IllegalStateException("This older project lacks individual Gerber shapes; open the source Gerber to edit it");
        }
    }

    private EditState snapshot() {
        return new EditState(shapes, apertures, new LinkedHashSet<>(selected));
    }

    private void restore(EditState state) {
        shapes = state.shapes();
        apertures = state.apertures();
        selected.clear();
        selected.addAll(state.selected());
        cachedWorkingImage = !isDirty() ? sourceImage : null;
    }

    private void commit(List<GerberShape> newShapes, Set<Integer> newSelection) {
        Set<Integer> selectionCopy = new LinkedHashSet<>(newSelection);
        undoStack.push(snapshot());
        if (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
        shapes = List.copyOf(newShapes);
        selected.clear();
        selected.addAll(selectionCopy);
        cachedWorkingImage = null;
    }

    /** The name and geometry Apply will publish as a new object. */
    public record ApplyResult(String name, GerberImage image) {
    }

    /** Immutable work request safe to generate on a background worker. */
    public record ApplyRequest(String name, GerberImage sourceImage, List<GerberShape> shapes,
                               Map<String, Aperture> apertures, boolean dirty) {
        public ApplyResult generate(CancellationToken cancellation, ProgressCallback progress) {
            if (!dirty) {
                progress.report(1);
                return new ApplyResult(name, sourceImage);
            }
            return new ApplyResult(name, sourceImage.withEditedShapes(shapes, apertures, cancellation, progress));
        }
    }

    public ApplyRequest prepareApply() {
        return new ApplyRequest(nextEditedName(sourceName), sourceImage, shapes, apertures, isDirty());
    }

    public ApplyResult apply() {
        return prepareApply().generate(() -> false, ignored -> {});
    }

    /**
     * Ported from AppGerberEditor.py's update_fcgerber(): "&lt;name&gt;_edit" the
     * first time a name is edited; if the name already contains "_edit", the
     * last character is parsed as a digit and incremented (append "_1" if the
     * last character isn't a digit) - faithfully reproduced, including
     * Python's own multi-digit quirk past "_edit_9" (single-character slice,
     * not a real decimal increment), since this is existing legacy behavior
     * to match, not a bug to fix here.
     */
    public static String nextEditedName(String currentName) {
        if (!currentName.contains("_edit")) {
            return currentName + "_edit";
        }
        char lastChar = currentName.charAt(currentName.length() - 1);
        if (Character.isDigit(lastChar)) {
            int next = Character.getNumericValue(lastChar) + 1;
            return currentName.substring(0, currentName.length() - 1) + next;
        }
        return currentName + "_1";
    }

    private static List<GerberShape> shapesFromApertureAggregates(GerberImage image) {
        List<GerberShape> result = new ArrayList<>();
        for (Map.Entry<String, Geometry> entry : image.apertureGeometry().entrySet()) {
            addPolygons(entry.getKey(), entry.getValue(), result);
        }
        return List.copyOf(result);
    }

    private static void addPolygons(String apertureCode, Geometry geometry, List<GerberShape> out) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon) {
            out.add(new GerberShape(apertureCode, geometry, false));
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part != geometry) {
                addPolygons(apertureCode, part, out);
            }
        }
    }
}
