package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * appTools/ToolCopperThieving.py: fills the empty copper area of a Gerber with solid copper, dots, squares or a grid of
 * lines (to balance the etching), adds a robber bar around it for plating, and builds a pattern-plating mask. Python's
 * defaults: clearance 0.25, margin 1.0, minimum area 0.1, 64 circle steps, dots 1.0 / 2.0, squares 1.0 / 2.0,
 * lines 0.25 / 2.0, robber bar margin 1.0 and thickness 1.0.
 */
public final class CopperThieving {

    public enum Fill { SOLID, DOT, SQUARE, LINE }

    public enum Reference { ITSELF, AREA, BOX }

    /** How the "itself" reference boxes the copper: its bounding rectangle or its convex hull. */
    public enum BoxType { RECTANGULAR, MINIMAL }

    /** What the pattern plating mask takes besides the solder mask. */
    public enum Plating { THIEVING, ROBBER, BOTH, NONE }

    public record Options(double clearance, double margin, double minArea, Reference reference, BoxType boxType,
                          int circleSteps, Fill fill, double dotDiameter, double dotSpacing, double squareSize,
                          double squareSpacing, double lineSize, double lineSpacing) {
        public static Options defaults() {
            return new Options(0.25, 1.0, 0.1, Reference.ITSELF, BoxType.RECTANGULAR, 64, Fill.SOLID, 1.0, 2.0, 1.0, 2.0,
                    0.25, 2.0);
        }
    }

    public record Robber(Polygon bar, LineString line, double thickness) {
    }

    public record PlatingMask(GerberImage image, double platedArea) {
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final int MITRE = BufferParameters.JOIN_MITRE;

    private CopperThieving() {
    }

    /**
     * The thieving polygons.
     *
     * @param copper    the Gerber being filled
     * @param reference the reference geometry: for {@link Reference#AREA} the drawn zones, for {@link Reference#BOX}
     *                  the solid geometry of the reference object; ignored for ITSELF
     * @param referenceIsGerber whether a BOX reference is a Gerber (its hull is intersected with the copper's) or a
     *                  Geometry object (its polygons are used, buffered by the margin)
     */
    public static List<Polygon> thieve(Geometry copper, Geometry reference, boolean referenceIsGerber, Options options,
                                       CancellationToken cancellation, ProgressCallback progress) {
        validate(options);
        if (copper == null || copper.isEmpty()) {
            throw new IllegalArgumentException("O Gerber esta vazio");
        }
        if (options.fill() == Fill.LINE && options.reference() != Reference.ITSELF) {
            throw new IllegalArgumentException("A grade de linhas so funciona com a referencia 'itself'");
        }
        int quad = Math.max(1, options.circleSteps() / 4);

        // The clearance over the copper features (one buffer of the whole copper: the same shape as buffering every
        // polygon and merging them, in a third of the time).
        cancellation.throwIfCancellationRequested();
        Geometry clearance = copper.buffer(options.clearance(), quad);
        progress.report(0.5);

        Geometry box = boundingBox(copper, reference, referenceIsGerber, options);
        Geometry thief = OverlayNGRobust.overlay(box, clearance, org.locationtech.jts.operation.overlay.OverlayOp.DIFFERENCE);
        List<Polygon> solid = new ArrayList<>();
        for (Polygon polygon : polygons(thief)) {
            if (polygon.getArea() >= options.minArea()) {
                solid.add(polygon);
            }
        }
        progress.report(0.7);
        cancellation.throwIfCancellationRequested();

        Envelope frame = copper.getEnvelopeInternal();
        Geometry frameBox = mitre(FACTORY.toGeometry(frame), options.margin());
        Envelope fillFrame = frameBox.getEnvelopeInternal();
        List<Polygon> result;
        switch (options.fill()) {
            case SOLID -> result = solid;
            case DOT, SQUARE -> result = grid(options, fillFrame, frameBox, solid, cancellation);
            default -> result = lines(copper, clearance, options, fillFrame, quad, cancellation);
        }
        progress.report(1);
        return result;
    }

    private static void validate(Options o) {
        if (!(o.clearance() >= 0) || !(o.margin() >= 0) || !(o.minArea() >= 0)) {
            throw new IllegalArgumentException("Distancia, margem e area minima nao podem ser negativas");
        }
        switch (o.fill()) {
            case DOT -> require(o.dotDiameter(), o.dotSpacing(), "pontos");
            case SQUARE -> require(o.squareSize(), o.squareSpacing(), "quadrados");
            case LINE -> require(o.lineSize(), o.lineSpacing(), "linhas");
            default -> { }
        }
    }

    private static void require(double size, double spacing, String what) {
        if (!(size > 0) || !(spacing >= 0)) {
            throw new IllegalArgumentException("Tamanho e espacamento invalidos para " + what);
        }
    }

    /** Shapely's buffer with a mitre join (limit 5.0), as the Python tool grows its boxes. */
    private static Geometry mitre(Geometry geometry, double distance) {
        BufferParameters parameters = new BufferParameters(8, BufferParameters.CAP_ROUND, MITRE, 5.0);
        return BufferOp.bufferOp(geometry, distance, parameters);
    }

    private static Geometry boundingBox(Geometry copper, Geometry reference, boolean referenceIsGerber, Options o) {
        int quad = Math.max(1, o.circleSteps() / 4);
        switch (o.reference()) {
            case ITSELF -> {
                Geometry base = o.boxType() == BoxType.MINIMAL ? union(List.of(copper)).convexHull()
                        : FACTORY.toGeometry(copper.getEnvelopeInternal());
                return mitre(base, o.margin());
            }
            case AREA -> {
                if (reference == null || reference.isEmpty()) {
                    throw new IllegalArgumentException("Desenhe ao menos uma area");
                }
                List<Geometry> zones = new ArrayList<>();
                for (int i = 0; i < reference.getNumGeometries(); i++) {
                    zones.add(mitre(reference.getGeometryN(i), o.margin()));
                }
                return union(zones);
            }
            default -> {
                if (reference == null || reference.isEmpty()) {
                    throw new IllegalArgumentException("O objeto de referencia esta vazio");
                }
                if (referenceIsGerber) {
                    Geometry hull = union(List.of(reference)).convexHull();
                    Geometry both = OverlayNGRobust.overlay(union(List.of(copper)).convexHull(), hull,
                            org.locationtech.jts.operation.overlay.OverlayOp.INTERSECTION);
                    return mitre(both, o.margin());
                }
                List<Geometry> grown = new ArrayList<>();
                for (int i = 0; i < reference.getNumGeometries(); i++) {
                    grown.add(mitre(reference.getGeometryN(i), o.margin()));
                }
                return union(grown);
            }
        }
    }

    /**
     * Dots or squares laid over the frame, re-centred on it, kept only where they fit inside a thieving area. Python
     * builds every cell and tests each one; here a cell is only built where an area's envelope can hold it.
     */
    private static List<Polygon> grid(Options o, Envelope frame, Geometry frameBox, List<Polygon> areas,
                                      CancellationToken cancellation) {
        boolean dots = o.fill() == Fill.DOT;
        double size = dots ? o.dotDiameter() : o.squareSize();
        double pitch = size + (dots ? o.dotSpacing() : o.squareSpacing());
        double half = size / 2;
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        for (double x = frame.getMinX() + half; x <= frame.getMaxX() - half; x += pitch) {
            lastX = x;
        }
        for (double y = frame.getMinY() + half; y <= frame.getMaxY() - half; y += pitch) {
            lastY = y;
        }
        if (Double.isNaN(lastX) || Double.isNaN(lastY) || areas.isEmpty()) {
            return List.of();
        }
        // Python centres the grid on the frame by the grid's centroid (the middle of its bounds).
        double dx = frameBox.getCentroid().getX() - ((frame.getMinX() + half - half + lastX + half) / 2);
        double dy = frameBox.getCentroid().getY() - ((frame.getMinY() + half - half + lastY + half) / 2);
        STRtree index = new STRtree();
        for (Polygon area : areas) {
            index.insert(area.getEnvelopeInternal(), PreparedGeometryFactory.prepare(area));
        }
        List<Polygon> kept = new ArrayList<>();
        for (double x = frame.getMinX() + half; x <= frame.getMaxX() - half; x += pitch) {
            cancellation.throwIfCancellationRequested();
            for (double y = frame.getMinY() + half; y <= frame.getMaxY() - half; y += pitch) {
                double cx = x + dx;
                double cy = y + dy;
                List<?> candidates = index.query(new Envelope(cx - half, cx + half, cy - half, cy + half));
                Polygon cell = null;
                for (Object candidate : candidates) {
                    PreparedGeometry area = (PreparedGeometry) candidate;
                    if (!area.getGeometry().getEnvelopeInternal().covers(cx - half, cy - half)
                            || !area.getGeometry().getEnvelopeInternal().covers(cx + half, cy + half)) {
                        continue;
                    }
                    if (cell == null) {
                        cell = dots ? (Polygon) FACTORY.createPoint(new Coordinate(cx, cy)).buffer(half, 64)
                                : FACTORY.createPolygon(new Coordinate[] {new Coordinate(cx - half, cy - half),
                                new Coordinate(cx + half, cy - half), new Coordinate(cx + half, cy + half),
                                new Coordinate(cx - half, cy + half), new Coordinate(cx - half, cy - half)});
                    }
                    if (area.contains(cell)) {
                        kept.add(cell);
                    }
                }
            }
        }
        return kept;
    }

    private static List<Polygon> lines(Geometry copper, Geometry clearance, Options o, Envelope frame, int quad,
                                       CancellationToken cancellation) {
        double half = o.lineSize() / 2;
        // A thick line that surrounds the copper features.
        List<Geometry> grown = new ArrayList<>();
        for (int i = 0; i < copper.getNumGeometries(); i++) {
            cancellation.throwIfCancellationRequested();
            grown.add(copper.getGeometryN(i).buffer(o.clearance() + half, quad));
        }
        Geometry around = union(grown);
        List<Geometry> outline = new ArrayList<>();
        for (Polygon polygon : polygons(around)) {
            outline.add(polygon.getExteriorRing().buffer(half, quad));
        }
        List<Geometry> pieces = new ArrayList<>();
        pieces.add(union(outline));

        // A line that runs inside the frame.
        Geometry inner = FACTORY.toGeometry(frame).buffer(-half);
        pieces.add(((Polygon) inner).getExteriorRing().buffer(half, quad));
        Envelope in = inner.getEnvelopeInternal();
        List<Geometry> strokes = new ArrayList<>();
        for (double x = in.getMinX(); x <= frame.getMaxX() - half; x += o.lineSize() + o.lineSpacing()) {
            strokes.add(stroke(x, in.getMinY(), x, in.getMaxY(), half, quad));
        }
        for (double y = in.getMinY(); y <= frame.getMaxY() - half; y += o.lineSize() + o.lineSpacing()) {
            strokes.add(stroke(in.getMinX(), y, in.getMaxX(), y, half, quad));
        }
        // One cut for the whole grid (Python cuts every line apart, leaving overlapping pieces).
        cancellation.throwIfCancellationRequested();
        pieces.add(OverlayNGRobust.overlay(union(strokes), clearance, org.locationtech.jts.operation.overlay.OverlayOp.DIFFERENCE));
        List<Polygon> result = new ArrayList<>();
        for (Geometry piece : pieces) {
            result.addAll(polygons(piece));
        }
        return result;
    }

    private static Geometry stroke(double x0, double y0, double x1, double y1, double half, int quad) {
        return FACTORY.createLineString(new Coordinate[] {new Coordinate(x0, y0), new Coordinate(x1, y1)})
                .buffer(half, quad);
    }

    /** The copper plus the thieving polygons, under the region aperture "0" (Python's "_thief" object). */
    public static GerberImage withThieving(GerberImage source, List<Polygon> thieving) {
        List<GerberShape> shapes = new ArrayList<>(source.shapes());
        for (Polygon polygon : thieving) {
            shapes.add(new GerberShape("0", polygon, false, polygon.getExteriorRing()));
        }
        return source.withEditedShapes(shapes, source.apertures(), CancellationToken.none(), ProgressCallback.none());
    }

    /** The robber bar: a ring of {@code thickness} around the copper's bounding box, {@code margin} away from it. */
    public static Robber robberBar(Geometry copper, double margin, double thickness) {
        if (copper == null || copper.isEmpty()) {
            throw new IllegalArgumentException("O Gerber esta vazio");
        }
        if (!(thickness > 0) || !(margin >= 0)) {
            throw new IllegalArgumentException("Espessura positiva e margem nao negativa");
        }
        Geometry outline = FACTORY.toGeometry(copper.getEnvelopeInternal()).buffer(margin + thickness / 2.0);
        LineString line = ((Polygon) outline).getExteriorRing();
        return new Robber((Polygon) line.buffer(thickness / 2.0), line, thickness);
    }

    /** The copper plus the robber bar, filed under a round aperture of the bar's thickness (Python's "_robber"). */
    public static GerberImage withRobber(GerberImage source, Robber robber) {
        Map<String, Aperture> apertures = new LinkedHashMap<>(source.apertures());
        String code = apertureFor(apertures, robber.thickness());
        List<GerberShape> shapes = new ArrayList<>(source.shapes());
        shapes.add(new GerberShape(code, robber.bar(), false, robber.line()));
        return source.withEditedShapes(shapes, apertures, CancellationToken.none(), ProgressCallback.none());
    }

    /**
     * Pattern plating mask (Python's "_plating_mask"): the solder mask openings, shrunk when {@code clearance} is
     * negative, plus the thieving and/or the robber bar, each grown by the clearance.
     */
    public static PlatingMask platingMask(GerberImage mask, double clearance, List<Polygon> thieving, Robber robber,
                                          Plating choice) {
        Map<String, Aperture> apertures = new LinkedHashMap<>(mask.apertures());
        List<GerberShape> shapes = new ArrayList<>();
        double area = 0;
        for (GerberShape shape : mask.shapes()) {
            if (clearance < 0 && !shape.clear()) {
                Geometry shrunk = shape.geometry().buffer(clearance);
                if (shrunk.isEmpty()) {
                    continue;
                }
                shapes.add(new GerberShape(shape.apertureCode(), shrunk, false, shape.followGeometry()));
            } else {
                shapes.add(shape);
            }
        }
        for (Polygon polygon : polygons(clearance < 0 ? shrinkAll(mask.solidGeometry(), clearance) : mask.solidGeometry())) {
            area += polygon.getArea();
        }
        boolean withThieving = thieving != null && !thieving.isEmpty() && (choice == Plating.BOTH || choice == Plating.THIEVING);
        boolean withRobber = robber != null && (choice == Plating.BOTH || choice == Plating.ROBBER);
        if (withThieving) {
            for (Polygon polygon : thieving) {
                area += polygon.getArea();
                Geometry grown = polygon.buffer(clearance);
                if (!grown.isEmpty()) {
                    shapes.add(new GerberShape("0", grown, false, polygon.getExteriorRing()));
                }
            }
        }
        if (withRobber) {
            area += robber.bar().getArea();
            String code = apertureFor(apertures, robber.thickness() + clearance);
            shapes.add(new GerberShape(code, robber.bar().buffer(clearance), false, robber.line()));
        }
        GerberImage image = mask.withEditedShapes(shapes, apertures, CancellationToken.none(), ProgressCallback.none());
        return new PlatingMask(image, area);
    }

    private static Geometry shrinkAll(Geometry solid, double clearance) {
        List<Geometry> shrunk = new ArrayList<>();
        for (int i = 0; i < solid.getNumGeometries(); i++) {
            shrunk.add(solid.getGeometryN(i).buffer(clearance));
        }
        return FACTORY.buildGeometry(shrunk);
    }

    private static String apertureFor(Map<String, Aperture> apertures, double diameter) {
        for (Map.Entry<String, Aperture> entry : apertures.entrySet()) {
            if (entry.getValue().kind == ApertureKind.CIRCLE && Math.abs(entry.getValue().width - diameter) < 1e-9) {
                return entry.getKey();
            }
        }
        int next = 9;
        for (String existing : apertures.keySet()) {
            try {
                next = Math.max(next, Integer.parseInt(existing));
            } catch (NumberFormatException ignored) {
                // Not numeric: irrelevant to the next free code.
            }
        }
        String code = String.valueOf(next + 1);
        apertures.put(code, Aperture.circle(Math.max(diameter, 1e-6)));
        return code;
    }

    private static Geometry union(List<? extends Geometry> geometries) {
        if (geometries.isEmpty()) {
            return FACTORY.createPolygon();
        }
        return UnaryUnionOp.union(geometries);
    }

    private static List<Polygon> polygons(Geometry geometry) {
        List<Polygon> out = new ArrayList<>();
        collect(geometry, out);
        return out;
    }

    private static void collect(Geometry geometry, List<Polygon> out) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            out.add(polygon);
        } else {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry part = geometry.getGeometryN(i);
                if (part != geometry) {
                    collect(part, out);
                }
            }
        }
    }
}
