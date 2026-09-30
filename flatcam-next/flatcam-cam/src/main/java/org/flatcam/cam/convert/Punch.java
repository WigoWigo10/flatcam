package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

/**
 * appTools/ToolPunchGerber.py: cuts holes in the flashed pads of a Gerber. The holes come from an Excellon
 * (the drills that fall inside a selected pad), or from the same fixed / annular-ring / proportional sizing as
 * Extract Drills. Each hole is added as a clear circle, so the copper keeps its individual shapes.
 */
public final class Punch {

    private Punch() {
    }

    /**
     * Holes sized by {@code options} in the flashes of {@code codes}. Python's fixed mode refuses a hole as big
     * as the pad it would punch.
     *
     * @throws IllegalArgumentException when the hole does not fit a pad or nothing would be punched
     */
    public static GerberImage bySize(GerberImage source, ExtractDrills.Options options, Set<String> codes) {
        List<ExtractDrills.Hole> holes = ExtractDrills.holes(source, options, codes);
        List<Geometry> discs = new ArrayList<>();
        List<Point> centres = new ArrayList<>();
        for (ExtractDrills.Hole hole : holes) {
            if (options.mode() == ExtractDrills.Mode.FIXED && hole.diameter() >= hole.padSide()) {
                throw new IllegalArgumentException("Falhou: o furo e maior que alguns dos apertures do Gerber");
            }
            centres.add(hole.center());
            discs.add(hole.center().buffer(hole.diameter() / 2, 16));
        }
        return withHoles(source, centres, discs);
    }

    /** Holes from the drills of an Excellon that land inside a flash of the chosen apertures. */
    public static GerberImage byExcellon(GerberImage source, ExcellonImage excellon, Set<String> codes) {
        List<Geometry> discs = new ArrayList<>();
        List<Point> centres = new ArrayList<>();
        var factory = new org.locationtech.jts.geom.GeometryFactory();
        for (ExcellonImage.Drill drill : excellon.drills()) {
            Double diameter = excellon.toolDiameters().get(drill.toolId());
            if (diameter == null || !(diameter > 0)) {
                continue;
            }
            Point point = factory.createPoint(new org.locationtech.jts.geom.Coordinate(drill.x(), drill.y()));
            boolean inPad = false;
            for (GerberShape shape : source.shapes()) {
                if (!shape.clear() && codes.contains(shape.apertureCode()) && shape.followGeometry() instanceof Point
                        && shape.geometry().getEnvelopeInternal().covers(point.getCoordinate())
                        && point.within(shape.geometry())) {
                    inPad = true;
                    break;
                }
            }
            if (inPad) {
                centres.add(point);
                discs.add(point.buffer(diameter / 2, 16));
            }
        }
        return withHoles(source, centres, discs);
    }

    private static GerberImage withHoles(GerberImage source, List<Point> centres, List<Geometry> discs) {
        if (discs.isEmpty()) {
            throw new IllegalArgumentException("Nenhum furo cai dentro dos pads escolhidos");
        }
        List<GerberShape> shapes = new ArrayList<>(source.shapes());
        for (int i = 0; i < discs.size(); i++) {
            shapes.add(new GerberShape(GerberShape.REGION_APERTURE, discs.get(i), true, centres.get(i)));
        }
        return source.withEditedShapes(shapes, CancellationToken.none(), ProgressCallback.none());
    }
}
