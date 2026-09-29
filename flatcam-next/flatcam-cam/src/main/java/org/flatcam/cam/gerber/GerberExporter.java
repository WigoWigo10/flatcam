package org.flatcam.cam.gerber;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

/**
 * Exports the current, resolved copper image as a standard Gerber layer.
 *
 * <p>Editing and geometric transforms can change a flash into an arbitrary
 * polygon, so replaying the source file or its old aperture definitions would
 * not reproduce the image. This writer emits the final solid as filled regions.
 * Original aperture identities, attributes and draw/flash commands are not
 * retained, but the manufactured dark/clear image is. Polygon exteriors are
 * emitted largest first, followed by their clear holes, so islands inside a
 * hole are painted back on top even if JTS listed them before their parent.
 *
 * <p>{@link #export(GerberImage)} keeps the image's units at six decimals;
 * {@link #export(GerberImage, Format)} writes the File > Export > Gerber
 * coordinate format chosen by the user (Python's gerber_exp_* preferences).
 */
public final class GerberExporter {

    private static final int LOSSLESS_DECIMAL_DIGITS = 6;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /**
     * Output units and %FS coordinate format. {@code omitLeadingZeros} selects
     * FSLA (Python's "L", the Gerber default) or FSTA (trailing zeros omitted,
     * Python's "T").
     */
    public record Format(String units, int integerDigits, int decimalDigits, boolean omitLeadingZeros) {
        public Format {
            if (!"MM".equals(units) && !"IN".equals(units)) {
                throw new IllegalArgumentException("Unsupported Gerber units: " + units);
            }
            if (integerDigits < 1 || integerDigits > 6 || decimalDigits < 1 || decimalDigits > 6) {
                throw new IllegalArgumentException("Gerber digits must be between 1 and 6");
            }
        }

        /** FlatCAM Python's export defaults: inch, 2:4, leading zeros omitted. */
        public static Format flatcamDefaults() {
            return new Format("IN", 2, 4, true);
        }
    }

    /** Publishes a complete file without truncating an existing destination on failure. */
    public void write(GerberImage image, Path path) throws IOException {
        writeText(export(image), path);
    }

    /** {@link #write(GerberImage, Path)} in a user-chosen coordinate format. */
    public void write(GerberImage image, Format format, Path path) throws IOException {
        writeText(export(image, format), path);
    }

    private static void writeText(String gerber, Path path) throws IOException {
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(),
                "." + destination.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, gerber, StandardCharsets.US_ASCII);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String export(GerberImage image) {
        Objects.requireNonNull(image, "image");
        requireSupportedUnits(image);
        return export(image, image.units(), 0, LOSSLESS_DECIMAL_DIGITS, true);
    }

    public String export(GerberImage image, Format format) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(format, "format");
        requireSupportedUnits(image);
        return export(image, format.units(), format.integerDigits(), format.decimalDigits(),
                format.omitLeadingZeros());
    }

    private static void requireSupportedUnits(GerberImage image) {
        if (!"MM".equals(image.units()) && !"IN".equals(image.units())) {
            throw new IllegalArgumentException("Unsupported Gerber units: " + image.units());
        }
    }

    /** {@code fixedIntegerDigits} 0 picks the smallest width (at least 3) that fits the image. */
    private static String export(GerberImage image, String units, int fixedIntegerDigits, int decimalDigits,
                                 boolean omitLeadingZeros) {
        double factor = image.units().equals(units) ? 1 : "MM".equals(units) ? 25.4 : 1 / 25.4;
        Coordinates coordinates = new Coordinates(factor, decimalDigits);

        Geometry solid = image.solidGeometry();
        List<Polygon> polygons = new ArrayList<>();
        if (solid != null && !solid.isEmpty()) {
            if (!solid.isValid()) {
                throw new IllegalArgumentException("Gerber solid geometry is invalid");
            }
            collectPolygons(solid, polygons);
        }
        polygons.sort(Comparator.comparingDouble(GerberExporter::exteriorArea).reversed());

        long largestCoordinate = 0;
        for (Polygon polygon : polygons) {
            for (Coordinate coordinate : polygon.getCoordinates()) {
                largestCoordinate = Math.max(largestCoordinate, Math.abs(coordinates.scaled(coordinate.x)));
                largestCoordinate = Math.max(largestCoordinate, Math.abs(coordinates.scaled(coordinate.y)));
            }
        }
        int neededDigits = Long.toString(largestCoordinate / coordinates.scale).length();
        int integerDigits = fixedIntegerDigits > 0 ? fixedIntegerDigits : Math.max(3, neededDigits);
        if (largestCoordinate / coordinates.scale > 0 && neededDigits > integerDigits) {
            throw new IllegalArgumentException("Gerber coordinates exceed the " + integerDigits
                    + "-digit integer range of the chosen format");
        }
        if (integerDigits > 6) {
            throw new IllegalArgumentException("Gerber coordinates exceed the six-digit integer range");
        }
        int width = integerDigits + decimalDigits;

        StringBuilder output = new StringBuilder();
        output.append("G04 FlatCAM FX - resolved Gerber image*\n");
        output.append("%FS").append(omitLeadingZeros ? 'L' : 'T').append("AX")
                .append(integerDigits).append(decimalDigits)
                .append("Y").append(integerDigits).append(decimalDigits).append("*%\n");
        output.append("%MO").append(units).append("*%\n");
        // A selected aperture is required by some readers, though its size is
        // irrelevant to G36/G37 region fills.
        output.append("%ADD10C,0.001*%\nD10*\nG01*\n");
        for (Polygon polygon : polygons) {
            appendRegion(output, coordinates, width, omitLeadingZeros,
                    polygon.getExteriorRing().getCoordinates(), false);
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
                appendRegion(output, coordinates, width, omitLeadingZeros,
                        polygon.getInteriorRingN(hole).getCoordinates(), true);
            }
        }
        output.append("M02*\n");
        return output.toString();
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> target) {
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            target.add(polygon);
            return;
        }
        if (geometry.getNumGeometries() == 1 && geometry.getGeometryN(0) == geometry) {
            throw new IllegalArgumentException("Gerber solid contains non-polygonal geometry: "
                    + geometry.getGeometryType());
        }
        for (int part = 0; part < geometry.getNumGeometries(); part++) {
            collectPolygons(geometry.getGeometryN(part), target);
        }
    }

    private static double exteriorArea(Polygon polygon) {
        return polygon.getFactory().createPolygon(polygon.getExteriorRing().getCoordinates()).getArea();
    }

    private static void appendRegion(StringBuilder output, Coordinates coordinates, int width,
                                     boolean omitLeadingZeros, Coordinate[] ring, boolean clear) {
        List<long[]> vertices = new ArrayList<>(ring.length);
        for (Coordinate coordinate : ring) {
            long[] vertex = {coordinates.scaled(coordinate.x), coordinates.scaled(coordinate.y)};
            if (vertices.isEmpty() || !sameVertex(vertices.get(vertices.size() - 1), vertex)) {
                vertices.add(vertex);
            }
        }
        if (vertices.size() > 1 && sameVertex(vertices.get(0), vertices.get(vertices.size() - 1))) {
            vertices.remove(vertices.size() - 1);
        }
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("Gerber region collapsed at "
                    + coordinates.decimals + " decimal places");
        }
        Coordinate[] roundedRing = new Coordinate[vertices.size() + 1];
        for (int index = 0; index < vertices.size(); index++) {
            roundedRing[index] = new Coordinate(vertices.get(index)[0], vertices.get(index)[1]);
        }
        roundedRing[vertices.size()] = roundedRing[0];
        Polygon roundedPolygon = GEOMETRY_FACTORY.createPolygon(roundedRing);
        if (!roundedPolygon.isValid() || roundedPolygon.getArea() == 0) {
            throw new IllegalArgumentException("Gerber region becomes invalid at "
                    + coordinates.decimals + " decimal places");
        }
        output.append(clear ? "%LPC*%\n" : "%LPD*%\n").append("G36*\n");
        for (int index = 0; index <= vertices.size(); index++) {
            long[] vertex = vertices.get(index % vertices.size());
            output.append('X').append(digits(vertex[0], width, omitLeadingZeros))
                    .append('Y').append(digits(vertex[1], width, omitLeadingZeros))
                    .append(index == 0 ? "D02*\n" : "D01*\n");
        }
        output.append("G37*\n");
    }

    private static boolean sameVertex(long[] a, long[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    /** L: plain digits; T: zero-padded to the full width, then trailing zeros dropped. */
    private static String digits(long value, int width, boolean omitLeadingZeros) {
        String sign = value < 0 ? "-" : "";
        String magnitude = Long.toString(Math.abs(value));
        if (omitLeadingZeros || value == 0) {
            return sign + magnitude;
        }
        String padded = "0".repeat(Math.max(0, width - magnitude.length())) + magnitude;
        int end = padded.length();
        while (end > 1 && padded.charAt(end - 1) == '0') {
            end--;
        }
        return sign + padded.substring(0, end);
    }

    /** Unit conversion plus rounding to the output resolution. */
    private static final class Coordinates {
        private final double factor;
        private final int decimals;
        private final long scale;

        Coordinates(double factor, int decimals) {
            this.factor = factor;
            this.decimals = decimals;
            this.scale = BigDecimal.ONE.movePointRight(decimals).longValueExact();
        }

        long scaled(double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Gerber coordinate is not finite");
            }
            long result;
            try {
                result = BigDecimal.valueOf(value * factor).movePointRight(decimals)
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Gerber coordinate is out of range: " + value, exception);
            }
            if (Math.abs(result) / scale >= 1_000_000L) {
                throw new IllegalArgumentException("Gerber coordinate is out of range: " + value);
            }
            return result;
        }
    }
}
