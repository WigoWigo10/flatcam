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

    /** Flashes an existing C/R/O aperture at one point, preserving its D-code and center. */
    public boolean addPad(String apertureCode, double x, double y) {
        requireExactShapes();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Pad coordinates must be finite");
        }
        Aperture aperture = apertures.get(apertureCode);
        if (aperture == null || !supportsPad(aperture)) {
            throw new IllegalArgumentException("Select a C, R or O aperture with positive dimensions");
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

    private static boolean supportsPad(Aperture aperture) {
        return (aperture.kind == ApertureKind.CIRCLE || aperture.kind == ApertureKind.RECTANGLE
                || aperture.kind == ApertureKind.OBROUND)
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
