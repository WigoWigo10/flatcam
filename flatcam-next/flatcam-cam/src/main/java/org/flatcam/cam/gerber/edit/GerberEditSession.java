package org.flatcam.cam.gerber.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
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
 * <p>There are still no edit operations - {@link #workingImage()} is always
 * the source image and {@link #apply()} never produces edited geometry. What
 * this class does add is the editable shape list ({@link #shapes()}) and a
 * selection over it with Python's rules:
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

    private final String sourceName;
    private final GerberImage sourceImage;
    private final GerberImage workingImage;
    private final List<GerberShape> shapes;
    private final boolean shapesApproximated;
    private final Set<Integer> selected = new LinkedHashSet<>();

    public GerberEditSession(String sourceName, GerberImage sourceImage) {
        this.sourceName = sourceName;
        this.sourceImage = sourceImage;
        this.workingImage = sourceImage;
        if (!sourceImage.shapes().isEmpty()) {
            this.shapes = sourceImage.shapes();
            this.shapesApproximated = false;
        } else {
            this.shapes = shapesFromApertureAggregates(sourceImage);
            this.shapesApproximated = true;
        }
    }

    public String sourceName() {
        return sourceName;
    }

    public GerberImage workingImage() {
        return workingImage;
    }

    /** Always false until this port has real edit operations that replace {@link #workingImage()}. */
    public boolean isDirty() {
        return workingImage != sourceImage;
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

    /** The name and geometry Apply will publish as a new object. */
    public record ApplyResult(String name, GerberImage image) {
    }

    public ApplyResult apply() {
        return new ApplyResult(nextEditedName(sourceName), workingImage);
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
