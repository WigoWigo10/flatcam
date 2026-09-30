package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolExtractDrills.py: turns the flashed pads of a Gerber into an Excellon. Every flash of a chosen
 * kind of aperture becomes a drill at its centre; the diameter is one fixed value, a share of the pad's smaller
 * side, or that side minus the annular ring to leave. Drills of equal diameter share a tool.
 */
public final class ExtractDrills {

    public enum Mode {
        FIXED, PROPORTIONAL, RING
    }

    /**
     * @param fixedDiameter     hole size for {@link Mode#FIXED}
     * @param proportion        share (0..1) of the smaller side for {@link Mode#PROPORTIONAL}
     * @param ringCircular      annular ring left around the hole, per kind of pad, for {@link Mode#RING}
     * @param circular          which kinds of pad are processed; square and rectangular are the two halves of
     *                          Gerber rectangles (Python's test is equal width and height to four decimals)
     */
    public record Options(Mode mode, double fixedDiameter, double proportion, double ringCircular, double ringOblong,
                          double ringSquare, double ringRectangular, double ringOther, boolean circular,
                          boolean oblong, boolean square, boolean rectangular, boolean other) {
        public Options {
            if (mode == null) {
                throw new IllegalArgumentException("mode");
            }
            if (mode == Mode.FIXED && !(fixedDiameter > 0)) {
                throw new IllegalArgumentException("O diametro do furo deve ser positivo");
            }
            if (mode == Mode.PROPORTIONAL && !(proportion > 0)) {
                throw new IllegalArgumentException("A proporcao do furo deve ser positiva");
            }
        }
    }

    private static final int DECIMALS = 4;

    private ExtractDrills() {
    }

    /** @throws IllegalArgumentException when no pad matched (Python: "No drills extracted. Try different parameters.") */
    public static ExcellonImage extract(GerberImage source, Options options) {
        Map<Integer, Double> tools = new LinkedHashMap<>();
        Map<Long, Integer> toolByDiameter = new LinkedHashMap<>();
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<Geometry> holes = new ArrayList<>();
        GeometryFactory factory = source.solidGeometry() == null ? new GeometryFactory()
                : source.solidGeometry().getFactory();
        for (Map.Entry<String, Aperture> entry : source.apertures().entrySet()) {
            Aperture aperture = entry.getValue();
            Double side = smallerSide(aperture, entry.getKey(), source, options);
            if (side == null) {
                continue;
            }
            double diameter = switch (options.mode()) {
                case FIXED -> options.fixedDiameter();
                case PROPORTIONAL -> side * options.proportion();
                case RING -> side - 2 * ring(aperture, options);
            };
            if (!(diameter > 0)) {
                continue;
            }
            for (GerberShape shape : source.shapes()) {
                if (shape.clear() || !entry.getKey().equals(shape.apertureCode())
                        || !(shape.followGeometry() instanceof Point center)) {
                    continue;
                }
                long key = Math.round(diameter * Math.pow(10, DECIMALS));
                Integer tool = toolByDiameter.get(key);
                if (tool == null) {
                    tool = tools.size() + 1;
                    tools.put(tool, diameter);
                    toolByDiameter.put(key, tool);
                }
                drills.add(new ExcellonImage.Drill(tool, center.getX(), center.getY()));
                holes.add(factory.createPoint(center.getCoordinate()).buffer(diameter / 2, 16));
            }
        }
        if (drills.isEmpty()) {
            throw new IllegalArgumentException("Nenhum furo extraido. Tente outros parametros");
        }
        Geometry solid = holes.size() == 1 ? holes.get(0) : OverlayNGRobust.union(holes);
        return ExcellonImage.of(source.units(), tools, drills, List.of(), solid);
    }

    /** The smaller side of the pad when its kind is selected, else null (the pad is skipped). */
    private static Double smallerSide(Aperture aperture, String code, GerberImage source, Options options) {
        ApertureKind kind = aperture.kind;
        if (kind == ApertureKind.CIRCLE) {
            return options.circular() ? aperture.width : null;
        }
        if (kind == ApertureKind.OBROUND) {
            return options.oblong() ? Math.min(aperture.width, aperture.height) : null;
        }
        if (kind == ApertureKind.RECTANGLE) {
            boolean squarePad = Math.abs(round(aperture.width) - round(aperture.height)) < Math.pow(10, -DECIMALS);
            if (squarePad ? !options.square() : !options.rectangular()) {
                return null;
            }
            return Math.min(aperture.width, aperture.height);
        }
        if (!options.other()) {
            return null;
        }
        if (kind == ApertureKind.MACRO) {
            for (GerberShape shape : source.shapes()) {
                if (code.equals(shape.apertureCode()) && !shape.clear()) {
                    Envelope box = shape.geometry().getEnvelopeInternal();
                    return Math.min(box.getWidth(), box.getHeight());
                }
            }
            return null;
        }
        return aperture.width;
    }

    private static double ring(Aperture aperture, Options options) {
        return switch (aperture.kind) {
            case CIRCLE -> options.ringCircular();
            case OBROUND -> options.ringOblong();
            case RECTANGLE -> Math.abs(round(aperture.width) - round(aperture.height)) < Math.pow(10, -DECIMALS)
                    ? options.ringSquare() : options.ringRectangular();
            default -> options.ringOther();
        };
    }

    private static double round(double value) {
        double scale = Math.pow(10, DECIMALS);
        return Math.round(value * scale) / scale;
    }
}
