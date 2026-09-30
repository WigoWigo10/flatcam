package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolInvertGerber.py: swaps copper and empty space. The box around the Gerber (grown by a margin,
 * with round, bevelled or square corners) loses every copper shape; what is left becomes a new Gerber made
 * of regions, e.g. to etch a negative.
 */
public final class InvertGerber {

    /** Corner style of the margin around the box; the names are Python's Round / Bevel / Square (mitre). */
    public enum JoinStyle {
        ROUND(BufferParameters.JOIN_ROUND),
        BEVEL(BufferParameters.JOIN_BEVEL),
        SQUARE(BufferParameters.JOIN_MITRE);

        private final int jts;

        JoinStyle(int jts) {
            this.jts = jts;
        }
    }

    private static final int CIRCLE_STEPS = 64;

    private InvertGerber() {
    }

    /**
     * @param margin how far the box extends past the copper; Python turns 0 into a hair so the box is still buffered
     * @throws IllegalArgumentException when the Gerber has no copper to invert or the margin is negative
     */
    public static GerberImage invert(GerberImage source, double margin, JoinStyle style) {
        if (source.isEmpty() || source.solidGeometry() == null || source.solidGeometry().isEmpty()) {
            throw new IllegalArgumentException("O Gerber nao tem cobre para inverter");
        }
        if (!Double.isFinite(margin) || margin < 0) {
            throw new IllegalArgumentException("A margem nao pode ser negativa");
        }
        GeometryFactory factory = source.solidGeometry().getFactory();
        double[] bounds = source.bounds();
        Geometry box = factory.toGeometry(new Envelope(bounds[0], bounds[2], bounds[1], bounds[3]));
        BufferParameters parameters = new BufferParameters(CIRCLE_STEPS, BufferParameters.CAP_ROUND, style.jts, 5.0);
        Geometry grown = BufferOp.bufferOp(box, Math.round(margin * 1e6) == 0 ? 1e-10 : margin, parameters);
        Geometry inverted = OverlayNGRobust.overlay(grown, source.solidGeometry(), OverlayNG.DIFFERENCE);

        List<GerberShape> shapes = new ArrayList<>();
        List<Geometry> regions = new ArrayList<>();
        collectPolygons(inverted, regions);
        for (Geometry region : regions) {
            shapes.add(new GerberShape(GerberShape.REGION_APERTURE, region, false,
                    ((Polygon) region).getExteriorRing()));
        }
        Map<String, Aperture> apertures = new LinkedHashMap<>();
        return GerberImage.of(source.units(), apertures, inverted, factory.buildGeometry(
                regions.stream().map(region -> (Geometry) ((Polygon) region).getExteriorRing()).toList()),
                Map.of(GerberShape.REGION_APERTURE, inverted), shapes);
    }

    private static void collectPolygons(Geometry geometry, List<Geometry> target) {
        if (geometry instanceof Polygon polygon) {
            if (!polygon.isEmpty()) {
                target.add(polygon);
            }
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            collectPolygons(geometry.getGeometryN(i), target);
        }
    }
}
