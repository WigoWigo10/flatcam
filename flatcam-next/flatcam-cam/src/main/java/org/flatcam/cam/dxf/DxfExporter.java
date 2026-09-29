package org.flatcam.cam.dxf;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

/**
 * File > Export > DXF - FlatCAMGeometry.export_dxf: every polygon ring and
 * line becomes one polyline on layer "0". Written as plain R12 POLYLINE/VERTEX
 * entities, which every DXF reader accepts without the handle and object
 * tables a newer LWPOLYLINE file needs. Unlike Python (ezdxf R2010 with no
 * units), the header declares the units ($INSUNITS/$MEASUREMENT) and rings are
 * flagged closed. Points are skipped, as in Python.
 */
public final class DxfExporter {

    public void write(Geometry geometry, String units, Path path) throws IOException {
        String dxf = export(geometry, units);
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(),
                "." + destination.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, dxf, StandardCharsets.US_ASCII);
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

    public String export(Geometry geometry, String units) {
        Objects.requireNonNull(geometry, "geometry");
        boolean metric;
        if ("MM".equals(units)) {
            metric = true;
        } else if ("IN".equals(units)) {
            metric = false;
        } else {
            throw new IllegalArgumentException("Unsupported DXF units: " + units);
        }
        StringBuilder dxf = new StringBuilder();
        pair(dxf, 0, "SECTION");
        pair(dxf, 2, "HEADER");
        pair(dxf, 9, "$ACADVER");
        pair(dxf, 1, "AC1009");
        pair(dxf, 9, "$INSUNITS");
        pair(dxf, 70, metric ? "4" : "1");
        pair(dxf, 9, "$MEASUREMENT");
        pair(dxf, 70, metric ? "1" : "0");
        pair(dxf, 0, "ENDSEC");
        pair(dxf, 0, "SECTION");
        pair(dxf, 2, "ENTITIES");
        int[] count = {0};
        appendGeometry(dxf, geometry, count);
        if (count[0] == 0) {
            throw new IllegalArgumentException("Nothing to export: the object has no lines or polygons");
        }
        pair(dxf, 0, "ENDSEC");
        pair(dxf, 0, "EOF");
        return dxf.toString();
    }

    private static void appendGeometry(StringBuilder dxf, Geometry geometry, int[] count) {
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            appendPolyline(dxf, polygon.getExteriorRing().getCoordinates(), true, count);
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
                appendPolyline(dxf, polygon.getInteriorRingN(hole).getCoordinates(), true, count);
            }
        } else if (geometry instanceof LineString line) {
            appendPolyline(dxf, line.getCoordinates(), line instanceof LinearRing || line.isClosed(), count);
        } else if (geometry.getNumGeometries() > 1 || geometry.getGeometryN(0) != geometry) {
            for (int part = 0; part < geometry.getNumGeometries(); part++) {
                appendGeometry(dxf, geometry.getGeometryN(part), count);
            }
        }
    }

    private static void appendPolyline(StringBuilder dxf, Coordinate[] coordinates, boolean closed, int[] count) {
        int vertices = closed && coordinates.length > 1 ? coordinates.length - 1 : coordinates.length;
        if (vertices < 2) {
            return;
        }
        pair(dxf, 0, "POLYLINE");
        pair(dxf, 8, "0");
        pair(dxf, 66, "1");
        pair(dxf, 10, "0.0");
        pair(dxf, 20, "0.0");
        pair(dxf, 30, "0.0");
        pair(dxf, 70, closed ? "1" : "0");
        for (int index = 0; index < vertices; index++) {
            pair(dxf, 0, "VERTEX");
            pair(dxf, 8, "0");
            pair(dxf, 10, number(coordinates[index].x));
            pair(dxf, 20, number(coordinates[index].y));
            pair(dxf, 30, "0.0");
        }
        pair(dxf, 0, "SEQEND");
        pair(dxf, 8, "0");
        count[0]++;
    }

    private static void pair(StringBuilder dxf, int code, String value) {
        String text = Integer.toString(code);
        dxf.append(" ".repeat(Math.max(0, 3 - text.length()))).append(text).append('\n').append(value).append('\n');
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("DXF coordinate is not finite: " + value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(9, RoundingMode.HALF_UP).stripTrailingZeros();
        String text = rounded.signum() == 0 ? "0" : rounded.toPlainString();
        return text.contains(".") ? text : text + ".0";
    }
}
