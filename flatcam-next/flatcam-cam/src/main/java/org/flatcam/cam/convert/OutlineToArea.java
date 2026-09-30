package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.polygonize.Polygonizer;

/**
 * Edit > Conversion > "Convert Outline to Area" (Python's {@code convert_outline2area}): closes the
 * linework of a board outline into a filled polygon. Every closed region the lines enclose is a
 * candidate and the largest one is the board.
 */
public final class OutlineToArea {

    /** The board area and how many closed regions the outline enclosed (more than one merits a warning). */
    public record Result(Geometry area, int candidates) {
    }

    private OutlineToArea() {
    }

    /**
     * @throws IllegalArgumentException when the source has no linework or the outline does not close
     */
    public static Result convert(Geometry source) {
        List<LineString> lines = new ArrayList<>();
        collectLinework(source, lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("The selected object contains no outline linework");
        }
        // Arcs end a hair away from where the next segment starts (a few 1e-16 of rounding), which is
        // enough for the polygonizer to see an open outline: put every end on a grid far finer than any
        // real feature (a billionth of the coordinates' size) before noding.
        double extent = 1;
        for (LineString line : lines) {
            Envelope box = line.getEnvelopeInternal();
            extent = Math.max(extent, Math.max(Math.max(Math.abs(box.getMinX()), Math.abs(box.getMaxX())),
                    Math.max(Math.abs(box.getMinY()), Math.abs(box.getMaxY()))));
        }
        PrecisionModel grid = new PrecisionModel(Math.min(1e12, 1e9 / extent));
        List<Geometry> snapped = new ArrayList<>();
        for (LineString line : lines) {
            Geometry reduced = GeometryPrecisionReducer.reduce(line, grid);
            if (!reduced.isEmpty()) {
                snapped.add(reduced);
            }
        }
        if (snapped.isEmpty()) {
            throw new IllegalArgumentException("The selected object contains no outline linework");
        }
        Geometry noded = snapped.size() == 1 ? snapped.get(0) : OverlayNGRobust.union(snapped);
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(noded);
        List<Polygon> polygons = new ArrayList<>();
        for (Object polygon : polygonizer.getPolygons()) {
            polygons.add((Polygon) polygon);
        }
        polygons.removeIf(polygon -> polygon.isEmpty() || polygon.getArea() <= 0);
        if (polygons.isEmpty()) {
            throw new IllegalArgumentException("The outline is open or invalid. Close all outline segments and try again");
        }
        Polygon board = polygons.stream().max(Comparator.comparingDouble(Polygon::getArea)).orElseThrow();
        Geometry area = board.isValid() ? board : board.buffer(0);
        if (area.isEmpty()) {
            throw new IllegalArgumentException("The generated board area is invalid");
        }
        return new Result(area, polygons.size());
    }

    private static void collectLinework(Geometry geometry, List<LineString> lines) {
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
        } else {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                collectLinework(geometry.getGeometryN(i), lines);
            }
        }
    }
}
