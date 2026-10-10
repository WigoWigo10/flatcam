package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.polygonize.Polygonizer;

/**
 * Edit > Conversion > "Convert Outline to Area" (Python's {@code convert_outline2area}): closes the
 * linework into a filled polygonal area, preserving disjoint boards. Nested boundaries alternate
 * material / void / island by containment depth, independently of winding or input order.
 * Adjacent bounded faces are joined, not discarded. This intentionally extends Python's
 * largest-region conversion. Does not manufacture missing edges or close real gaps.
 */
public final class OutlineToArea {

    /** Filled material and number of polygonized faces (including hole faces, not board count). */
    public record Result(Geometry area, int candidates) {
    }

    private OutlineToArea() {
    }

    /**
     * @throws IllegalArgumentException when the source has no linework or the outline does not close
     */
    public static Result convert(Geometry source) {
        return convert(source, CancellationToken.none());
    }

    public static Result convert(Geometry source, CancellationToken cancellation) {
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        cancellation.throwIfCancellationRequested();
        List<LineString> lines = new ArrayList<>();
        collectLinework(source, lines, cancellation);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("The selected object contains no outline linework");
        }
        // Arcs end a hair away from where the next segment starts (a few 1e-16 of rounding), which is
        // enough for the polygonizer to see an open outline: put every end on a grid far finer than any
        // real feature (a billionth of the coordinates' size) before noding.
        double extent = 1;
        for (LineString line : lines) {
            cancellation.throwIfCancellationRequested();
            if (!line.isValid()) {
                throw new IllegalArgumentException("Outline coordinates must be finite and valid");
            }
            Envelope box = line.getEnvelopeInternal();
            extent = Math.max(extent, Math.max(Math.max(Math.abs(box.getMinX()), Math.abs(box.getMaxX())),
                    Math.max(Math.abs(box.getMinY()), Math.abs(box.getMaxY()))));
        }
        PrecisionModel grid = new PrecisionModel(Math.min(1e12, 1e9 / extent));
        List<Geometry> snapped = new ArrayList<>();
        for (LineString line : lines) {
            cancellation.throwIfCancellationRequested();
            Geometry reduced = GeometryPrecisionReducer.reduce(line, grid);
            if (reduced.isEmpty()) {
                throw new IllegalArgumentException("Outline segment collapsed at numerical precision; check the source scale");
            }
            snapped.add(reduced);
        }
        if (snapped.isEmpty()) {
            throw new IllegalArgumentException("The selected object contains no outline linework");
        }
        Geometry noded = OverlayNGRobust.union(snapped);
        cancellation.throwIfCancellationRequested();
        // Do not use Polygonizer(true): it can discard adjacent faces to guarantee edge-disjoint
        // output. Extract ALL faces, then resolve holes by shell nesting before unioning material.
        // https://locationtech.github.io/jts/javadoc/org/locationtech/jts/operation/polygonize/Polygonizer.html
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(noded);
        List<Polygon> polygons = new ArrayList<>();
        for (Object polygon : polygonizer.getPolygons()) {
            cancellation.throwIfCancellationRequested();
            polygons.add((Polygon) polygon);
        }
        if (!polygonizer.getDangles().isEmpty() || !polygonizer.getCutEdges().isEmpty()
                || !polygonizer.getInvalidRingLines().isEmpty()) {
            throw new IllegalArgumentException("The outline has open, dangling or invalid segments. No partial board area was created");
        }
        polygons.removeIf(polygon -> polygon.isEmpty() || polygon.getArea() <= 0);
        if (polygons.isEmpty()) {
            throw new IllegalArgumentException("The outline is open or invalid. Close all outline segments and try again");
        }
        STRtree index = new STRtree();
        List<Shell> shells = new ArrayList<>();
        for (Polygon polygon : polygons) {
            cancellation.throwIfCancellationRequested();
            Polygon shell = polygon.getFactory().createPolygon(polygon.getExteriorRing());
            Shell entry = new Shell(polygon, shell, PreparedGeometryFactory.prepare(shell));
            shells.add(entry);
            index.insert(shell.getEnvelopeInternal(), entry);
        }
        index.build();
        List<Geometry> material = new ArrayList<>();
        for (Shell shell : shells) {
            cancellation.throwIfCancellationRequested();
            int depth = 0;
            for (Object candidate : index.query(shell.ring().getEnvelopeInternal())) {
                cancellation.throwIfCancellationRequested();
                Shell parent = (Shell) candidate;
                if (parent != shell && parent.ring().getArea() > shell.ring().getArea()
                        && parent.prepared().covers(shell.ring())) depth++;
            }
            if (depth % 2 == 0) material.add(shell.face());
        }
        cancellation.throwIfCancellationRequested();
        Geometry area = OverlayNGRobust.union(material);
        cancellation.throwIfCancellationRequested();
        if (area.isEmpty() || !area.isValid() || area.getDimension() != 2) {
            throw new IllegalArgumentException("The generated board area is invalid");
        }
        return new Result(area, polygons.size());
    }

    private record Shell(Polygon face, Polygon ring, PreparedGeometry prepared) { }

    private static void collectLinework(Geometry geometry, List<LineString> lines, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            lines.add(polygon.getExteriorRing());
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                lines.add(polygon.getInteriorRingN(i));
            }
        } else if (geometry instanceof LineString line) {
            lines.add(line);
        } else if (geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collectLinework(geometry.getGeometryN(i), lines, cancellation);
            }
        }
    }
}
