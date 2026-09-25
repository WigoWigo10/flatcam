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
 */
public final class GerberExporter {

    private static final int DECIMAL_DIGITS = 6;
    private static final long SCALE = 1_000_000L;
    private static final long MAX_SCALED_COORDINATE = 999_999_999_999L;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** Publishes a complete file without truncating an existing destination on failure. */
    public void write(GerberImage image, Path path) throws IOException {
        String gerber = export(image);
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
        String units = image.units();
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported Gerber units: " + units);
        }

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
                largestCoordinate = Math.max(largestCoordinate, Math.abs(scaled(coordinate.x)));
                largestCoordinate = Math.max(largestCoordinate, Math.abs(scaled(coordinate.y)));
            }
        }
        int integerDigits = Math.max(3, Long.toString(largestCoordinate / SCALE).length());
        if (integerDigits > 6) {
            throw new IllegalArgumentException("Gerber coordinates exceed the six-digit integer range");
        }

        StringBuilder output = new StringBuilder();
        output.append("G04 FlatCAM FX - resolved Gerber image*\n");
        output.append("%FSLAX").append(integerDigits).append(DECIMAL_DIGITS)
                .append("Y").append(integerDigits).append(DECIMAL_DIGITS).append("*%\n");
        output.append("%MO").append(units).append("*%\n");
        // A selected aperture is required by some readers, though its size is
        // irrelevant to G36/G37 region fills.
        output.append("%ADD10C,0.001*%\nD10*\nG01*\n");
        for (Polygon polygon : polygons) {
            appendRegion(output, polygon.getExteriorRing().getCoordinates(), false);
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
                appendRegion(output, polygon.getInteriorRingN(hole).getCoordinates(), true);
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

    private static void appendRegion(StringBuilder output, Coordinate[] ring, boolean clear) {
        List<Coordinate> vertices = new ArrayList<>(ring.length);
        for (Coordinate coordinate : ring) {
            if (vertices.isEmpty() || scaled(vertices.get(vertices.size() - 1).x) != scaled(coordinate.x)
                    || scaled(vertices.get(vertices.size() - 1).y) != scaled(coordinate.y)) {
                vertices.add(coordinate);
            }
        }
        if (vertices.size() > 1 && scaled(vertices.get(0).x) == scaled(vertices.get(vertices.size() - 1).x)
                && scaled(vertices.get(0).y) == scaled(vertices.get(vertices.size() - 1).y)) {
            vertices.remove(vertices.size() - 1);
        }
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("Gerber region collapsed at six decimal places");
        }
        Coordinate[] roundedRing = new Coordinate[vertices.size() + 1];
        for (int index = 0; index < vertices.size(); index++) {
            roundedRing[index] = new Coordinate(scaled(vertices.get(index).x), scaled(vertices.get(index).y));
        }
        roundedRing[vertices.size()] = roundedRing[0];
        Polygon roundedPolygon = GEOMETRY_FACTORY.createPolygon(roundedRing);
        if (!roundedPolygon.isValid() || roundedPolygon.getArea() == 0) {
            throw new IllegalArgumentException("Gerber region becomes invalid at six decimal places");
        }
        output.append(clear ? "%LPC*%\n" : "%LPD*%\n").append("G36*\n");
        for (int index = 0; index <= vertices.size(); index++) {
            Coordinate coordinate = vertices.get(index % vertices.size());
            output.append('X').append(scaled(coordinate.x))
                    .append('Y').append(scaled(coordinate.y))
                    .append(index == 0 ? "D02*\n" : "D01*\n");
        }
        output.append("G37*\n");
    }

    private static long scaled(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Gerber coordinate is not finite");
        }
        long result;
        try {
            result = BigDecimal.valueOf(value).movePointRight(DECIMAL_DIGITS)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Gerber coordinate is out of range: " + value, exception);
        }
        if (result < -MAX_SCALED_COORDINATE || result > MAX_SCALED_COORDINATE) {
            throw new IllegalArgumentException("Gerber coordinate is out of range: " + value);
        }
        return result;
    }
}
