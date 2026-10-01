package org.flatcam.cam.convert;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.ArrayList;
import java.util.EnumMap;
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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolQRCode.py: a QR code made of copper squares on a Gerber. Each dark module is a square of
 * {@code boxSize / 10} units; a mask (the code's box grown by the border) is cleared under it. The negative
 * polarity draws the mask minus the modules instead. Python's defaults: version 1 (growing when the text does
 * not fit), error level L, box size 3, border 4, positive, square mask.
 *
 * <p>Differences from Python: the code is centred on the clicked point (Python anchors its corner), and the
 * mask clears every copper shape under it, not only those that wholly contain it, so partial overlaps cannot
 * short the code to nearby copper.
 */
public final class QrCodeMarker {

    public enum ErrorLevel { L, M, Q, H }

    public enum Polarity { POSITIVE, NEGATIVE }

    /**
     * @param minVersion 1..40, the smallest QR version to use; a larger one is picked if the text needs it
     * @param boxSize    module size in tenths of a unit (Python's integer "box size")
     * @param border     margin in modules, used for the cleared mask
     */
    public record Options(String text, int minVersion, ErrorLevel level, int boxSize, int border, Polarity polarity,
                          boolean roundedMask) {
        public Options {
            if (text == null || text.isEmpty()) {
                throw new IllegalArgumentException("Nao ha dados para o QR Code");
            }
            if (minVersion < 1 || minVersion > 40) {
                throw new IllegalArgumentException("A versao do QR deve estar entre 1 e 40");
            }
            if (boxSize < 1) {
                throw new IllegalArgumentException("O tamanho da caixa deve ser positivo");
            }
            if (border < 0) {
                throw new IllegalArgumentException("A borda nao pode ser negativa");
            }
        }
    }

    /** The dark modules of a code; {@code modules[row][col]} with row 0 at the top. */
    public record Code(boolean[][] modules, int version) {
        public int size() {
            return modules.length;
        }
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private QrCodeMarker() {
    }

    public static Code encode(Options options) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(options.level().name()));
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        for (int version = options.minVersion(); version <= 40; version++) {
            hints.put(EncodeHintType.QR_VERSION, version);
            try {
                BitMatrix matrix = new QRCodeWriter().encode(options.text(), BarcodeFormat.QR_CODE, 0, 0, hints);
                boolean[][] modules = new boolean[matrix.getHeight()][matrix.getWidth()];
                for (int row = 0; row < matrix.getHeight(); row++) {
                    for (int col = 0; col < matrix.getWidth(); col++) {
                        modules[row][col] = matrix.get(col, row);
                    }
                }
                return new Code(modules, version);
            } catch (WriterException tooBig) {
                // This version cannot hold the text: try the next one (Python's "fit").
            }
        }
        throw new IllegalArgumentException("O texto e grande demais para um QR Code com este nivel de correcao");
    }

    /** Side of the code (without the border) in units. */
    public static double side(Code code, Options options) {
        return code.size() * options.boxSize() / 10.0;
    }

    /** The modules as squares centred on {@code centre} (the border is not included). */
    public static List<Polygon> modules(Code code, Options options, Coordinate centre) {
        double module = options.boxSize() / 10.0;
        double half = code.size() * module / 2;
        List<Polygon> squares = new ArrayList<>();
        for (int row = 0; row < code.size(); row++) {
            for (int col = 0; col < code.size(); col++) {
                if (code.modules()[row][col]) {
                    double x = centre.x - half + col * module;
                    double y = centre.y + half - (row + 1) * module;
                    squares.add(square(x, y, module));
                }
            }
        }
        return squares;
    }

    /** The mask: the code's box grown by the border, with square or round corners. */
    public static Geometry mask(Code code, Options options, Coordinate centre) {
        double half = side(code, options) / 2;
        double grow = options.border() * options.boxSize() / 10.0;
        Envelope box = new Envelope(centre.x - half, centre.x + half, centre.y - half, centre.y + half);
        if (options.roundedMask()) {
            return FACTORY.toGeometry(box).buffer(grow, 16);
        }
        box.expandBy(grow);
        return FACTORY.toGeometry(box);
    }

    public static GerberImage place(GerberImage source, Options options, Coordinate centre) {
        Code code = encode(options);
        Geometry mask = mask(code, options, centre);
        List<Polygon> modules = modules(code, options, centre);
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("O QR Code ficou vazio");
        }
        Map<String, Aperture> apertures = new LinkedHashMap<>(source.apertures());
        List<GerberShape> shapes = new ArrayList<>(source.shapes());
        shapes.add(new GerberShape(GerberShape.REGION_APERTURE, mask, true, mask.getBoundary()));
        if (options.polarity() == Polarity.POSITIVE) {
            double module = options.boxSize() / 10.0;
            String code10 = Fiducials.apertureFor(apertures, ApertureKind.RECTANGLE, module, module,
                    Aperture.rectangle(module, module));
            for (Polygon square : modules) {
                shapes.add(new GerberShape(code10, square, false, square.getCentroid()));
            }
        } else {
            Geometry dark = OverlayNGRobust.union(new ArrayList<Geometry>(modules));
            Geometry light = OverlayNGRobust.overlay(mask, dark, OverlayNG.DIFFERENCE);
            for (int i = 0; i < light.getNumGeometries(); i++) {
                if (light.getGeometryN(i) instanceof Polygon polygon && !polygon.isEmpty()) {
                    shapes.add(new GerberShape(GerberShape.REGION_APERTURE, polygon, false, polygon.getExteriorRing()));
                }
            }
        }
        return source.withEditedShapes(shapes, apertures, CancellationToken.none(), ProgressCallback.none());
    }

    private static Polygon square(double x, double y, double side) {
        return FACTORY.createPolygon(new Coordinate[] {new Coordinate(x, y), new Coordinate(x + side, y),
                new Coordinate(x + side, y + side), new Coordinate(x, y + side), new Coordinate(x, y)});
    }
}
