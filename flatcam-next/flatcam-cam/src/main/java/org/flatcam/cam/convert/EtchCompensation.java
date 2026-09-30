package org.flatcam.cam.convert;

import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolEtchCompensation.py: grows (or, with a negative offset, shrinks) the copper to make up for the
 * lateral etch. The offset is the copper thickness times the lateral-to-depth ratio, or a manual value.
 *
 * <p>Differences from Python, which treats every Gerber as millimetres: the micron values are converted to
 * the Gerber's own units, and clear (hole) shapes shrink by the same offset so the holes stay consistent with
 * the grown copper. The aperture sizes are left as they were.
 */
public final class EtchCompensation {

    /** Python's preselected etchants: CuCl2 etches laterally 0.33 per unit of depth, the others 0.25. */
    public enum Etchant {
        CUCL2(0.33), FE3CL(0.25), ALKALINE(0.25);

        final double factor;

        Etchant(double factor) {
            this.factor = factor;
        }
    }

    private EtchCompensation() {
    }

    /** Offset in the Gerber's units for a copper thickness in microns and an etch factor (depth / lateral). */
    public static double offsetFromFactor(double thicknessMicrons, double etchFactor, String units) {
        if (!(etchFactor > 0)) {
            throw new IllegalArgumentException("O fator de corrosao deve ser positivo");
        }
        return fromMicrons(thicknessMicrons / etchFactor, units);
    }

    /** Python's etchants list: the lateral etch is the thickness divided by the etchant's ratio. */
    public static double offsetFromEtchant(double thicknessMicrons, Etchant etchant, String units) {
        return fromMicrons(thicknessMicrons / etchant.factor, units);
    }

    public static double fromMicrons(double microns, String units) {
        return "IN".equalsIgnoreCase(units) || "INCH".equalsIgnoreCase(units) ? microns / 25400.0 : microns / 1000.0;
    }

    public static GerberImage compensate(GerberImage source, double offset) {
        if (offset == 0) {
            throw new IllegalArgumentException("O deslocamento e zero: nada a compensar");
        }
        if (source.shapes().isEmpty()) {
            Geometry solid = OverlayNGRobust.union(flat(source.solidGeometry()));
            return GerberImage.of(source.units(), source.apertures(), solid.buffer(offset, 16),
                    source.followGeometry(), Map.of(), List.of());
        }
        List<GerberShape> shapes = source.shapes().parallelStream().map(shape -> {
            Geometry grown = shape.geometry().buffer(shape.clear() ? -offset : offset, 16);
            return new GerberShape(shape.apertureCode(), grown, shape.clear(), shape.followGeometry());
        }).filter(shape -> !shape.geometry().isEmpty()).toList();
        return source.withEditedShapes(shapes, CancellationToken.none(), ProgressCallback.none());
    }

    private static List<Geometry> flat(Geometry geometry) {
        List<Geometry> parts = new java.util.ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part.getClass() == org.locationtech.jts.geom.GeometryCollection.class) {
                parts.addAll(flat(part));
            } else {
                parts.add(part);
            }
        }
        return parts;
    }
}
