package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolSub.py: removes what one object covers from another. For Gerbers, the copper under the
 * subtractor disappears from the target; for Geometries the subtractor cuts the target's shapes.
 *
 * <p>One deliberate difference from Python: its Gerber subtraction takes the union of the target minus each
 * subtractor shape one at a time, which leaves copper that only one of several overlapping subtractor shapes
 * covers. Here the target loses the union of all of them, which is what "subtract" means.
 */
public final class Subtract {

    /** A Geometry result: the combined shapes and, for a multi-tool target, each tool's shapes. */
    public record GeometryResult(Geometry geometry, List<ToolGeometry> tools) {
    }

    private Subtract() {
    }

    /**
     * Target shapes that touch the subtractor are cut by it and filed under the region aperture "0"
     * (Python's behaviour); untouched ones keep their aperture. The copper is rebuilt from the shapes in
     * order, so clear polarity keeps working.
     */
    public static GerberImage gerber(GerberImage target, GerberImage subtractor) {
        Geometry copper = subtractor.solidGeometry();
        if (copper == null || copper.isEmpty()) {
            throw new IllegalArgumentException("O subtraendo nao tem cobre");
        }
        // A project can hand over its copper wrapped in collections, which the overlay code refuses.
        Geometry cover = OverlayNGRobust.union(partsOf(copper));
        if (target.shapes().isEmpty()) {
            // Only the unioned copper is known (an older project): subtract from that.
            Geometry solid = OverlayNGRobust.overlay(OverlayNGRobust.union(partsOf(target.solidGeometry())), cover,
                    OverlayNG.DIFFERENCE);
            return GerberImage.of(target.units(), target.apertures(), solid, target.followGeometry(),
                    java.util.Map.of(), List.of());
        }
        List<GerberShape> shapes = new ArrayList<>();
        for (GerberShape shape : target.shapes()) {
            if (shape.clear() || !shape.geometry().intersects(cover)) {
                shapes.add(shape);
                continue;
            }
            Geometry remaining = OverlayNGRobust.overlay(shape.geometry(), cover, OverlayNG.DIFFERENCE);
            if (remaining.isEmpty()) {
                continue;
            }
            for (int i = 0; i < remaining.getNumGeometries(); i++) {
                Geometry piece = remaining.getGeometryN(i);
                if (piece instanceof Polygon polygon && !polygon.isEmpty()) {
                    shapes.add(new GerberShape(GerberShape.REGION_APERTURE, polygon, false, polygon.getExteriorRing()));
                }
            }
        }
        return target.withEditedShapes(shapes, CancellationToken.none(), ProgressCallback.none());
    }

    /**
     * @param closePaths Python's "Close paths": when true the target's shapes are unioned and cut as one; when
     *                   false every polygon becomes its rings and every line is cut on its own
     * @param tools      the target's per-tool shapes (empty for a single-tool target)
     */
    public static GeometryResult geometry(Geometry target, List<ToolGeometry> tools, Geometry subtractor,
                                          boolean closePaths) {
        if (subtractor == null || subtractor.isEmpty()) {
            throw new IllegalArgumentException("O subtraendo esta vazio");
        }
        Geometry cutter = OverlayNGRobust.union(partsOf(subtractor));
        if (tools.isEmpty()) {
            Geometry cut = cut(target, cutter, closePaths);
            return new GeometryResult(cut, List.of());
        }
        List<ToolGeometry> cutTools = new ArrayList<>();
        List<Geometry> everything = new ArrayList<>();
        for (ToolGeometry tool : tools) {
            Geometry cut = cut(tool.geometry(), cutter, closePaths);
            cutTools.add(new ToolGeometry(tool.toolDiameter(), cut, tool.toolProfile()));
            for (int i = 0; i < cut.getNumGeometries(); i++) {
                everything.add(cut.getGeometryN(i));
            }
        }
        GeometryFactory factory = target.getFactory();
        return new GeometryResult(factory.buildGeometry(everything), cutTools);
    }

    private static Geometry cut(Geometry shapes, Geometry cutter, boolean closePaths) {
        GeometryFactory factory = shapes.getFactory();
        List<Geometry> pieces = new ArrayList<>();
        if (closePaths) {
            Geometry joined = OverlayNGRobust.union(partsOf(shapes));
            Geometry remaining = OverlayNGRobust.overlay(joined, cutter, OverlayNG.DIFFERENCE);
            if (!remaining.isEmpty()) {
                pieces.add(remaining);
            }
        } else {
            for (Geometry part : partsOf(shapes)) {
                List<Geometry> lines = new ArrayList<>();
                if (part instanceof Polygon polygon) {
                    lines.add(polygon.getExteriorRing());
                    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                        lines.add(polygon.getInteriorRingN(i));
                    }
                } else if (part instanceof LineString) {
                    lines.add(part);
                }
                for (Geometry line : lines) {
                    Geometry remaining = OverlayNGRobust.overlay(line, cutter, OverlayNG.DIFFERENCE);
                    if (!remaining.isEmpty()) {
                        pieces.add(remaining);
                    }
                }
            }
        }
        return pieces.isEmpty() ? factory.createGeometryCollection() : factory.buildGeometry(pieces);
    }

    private static List<Geometry> partsOf(Geometry geometry) {
        List<Geometry> parts = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part.getClass() == org.locationtech.jts.geom.GeometryCollection.class) {
                parts.addAll(partsOf(part));
            } else {
                parts.add(part);
            }
        }
        return parts;
    }
}
