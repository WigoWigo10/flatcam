package org.flatcam.cam.hpgl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;

/**
 * File > Import > HPGL2 - ParseHPGL2.py: pen-down moves become paths grouped by
 * the selected pen (SP), in plotter units of 1/40 mm. Handles PU/PD/PA/PR with
 * any number of coordinate pairs, circles (CI), arcs (AA/AR/AT/RT) and rectangle
 * outlines (EA/ER/RA/RR).
 *
 * <p>Python only read one command per line, needed IN first, ignored the points
 * of "PD x,y", never ran PR, drew CI radii 40x too big and lost the position after
 * AA. This reads the command stream as HPGL defines it (commands may share a line
 * and need no separator), skips PCL escape sequences and comments, and counts
 * labels (LB) and encoded polylines (PE), which are not imported.
 */
public final class HpglImporter {

    /** Paths per pen number, in the order the pens were first used. */
    public record Result(String units, Map<Integer, Geometry> pens, int skippedLabels, int skippedEncodedPolylines) {
        public Geometry all() {
            return FACTORY.buildGeometry(new ArrayList<>(pens.values()));
        }
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final double MM_PER_PLOTTER_UNIT = 0.025;

    private final double scale;
    private final double segmentLength;
    private final Map<Integer, List<LineString>> lines = new LinkedHashMap<>();
    private final List<Coordinate> path = new ArrayList<>();
    private int pen = 1;
    private boolean penDown;
    private boolean absolute = true;
    private double x;
    private double y;
    private int labels;
    private int encoded;

    private HpglImporter(String units) {
        this.scale = "MM".equals(units) ? MM_PER_PLOTTER_UNIT : MM_PER_PLOTTER_UNIT / 25.4;
        this.segmentLength = ("MM".equals(units) ? 0.1 : 0.004) / scale;
    }

    public static Result parse(Path file, String units, CancellationToken cancellation) throws IOException {
        return parse(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1), units, cancellation);
    }

    public static Result parse(String hpgl, String units) {
        return parse(hpgl, units, CancellationToken.none());
    }

    private static Result parse(String hpgl, String units, CancellationToken cancellation) {
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported units: " + units);
        }
        Objects.requireNonNull(cancellation, "cancellation");
        HpglImporter importer = new HpglImporter(units);
        importer.run(hpgl, cancellation);
        importer.finishPath();
        Map<Integer, Geometry> pens = new LinkedHashMap<>();
        importer.lines.forEach((pen, penLines) -> {
            if (!penLines.isEmpty()) {
                LineMerger merger = new LineMerger();
                penLines.forEach(merger::add);
                List<Geometry> merged = new ArrayList<>();
                for (Object line : merger.getMergedLineStrings()) {
                    merged.add((Geometry) line);
                }
                pens.put(pen, FACTORY.buildGeometry(merged));
            }
        });
        if (pens.isEmpty()) {
            throw new IllegalArgumentException(importer.labels > 0 || importer.encoded > 0
                    ? "The HPGL file only has labels or encoded polylines (PE), which are not imported"
                    : "No pen-down drawing found - is this an HPGL/HPGL2 file?");
        }
        return new Result(units, pens, importer.labels, importer.encoded);
    }

    private void run(String text, CancellationToken cancellation) {
        char labelTerminator = 3; // ETX
        int index = 0;
        int commands = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == 27) { // PCL escape sequence, e.g. ESC%-1B or ESC E: skip up to its final letter
                index++;
                while (index < text.length() && !Character.isLetter(text.charAt(index))) {
                    index++;
                }
                index++;
                continue;
            }
            if (!Character.isLetter(c) || index + 1 >= text.length() || !Character.isLetter(text.charAt(index + 1))) {
                index++;
                continue;
            }
            if (++commands % 4096 == 0) {
                cancellation.throwIfCancellationRequested();
            }
            String mnemonic = text.substring(index, index + 2).toUpperCase(Locale.ROOT);
            index += 2;
            switch (mnemonic) {
                case "LB" -> {
                    int end = text.indexOf(labelTerminator, index);
                    index = end < 0 ? text.length() : end + 1;
                    labels++;
                    continue;
                }
                case "DT" -> {
                    if (index < text.length() && text.charAt(index) != ';') {
                        labelTerminator = text.charAt(index);
                    } else {
                        labelTerminator = 3;
                    }
                    index = skipTo(text, index, ';');
                    continue;
                }
                case "CO" -> {
                    int open = text.indexOf('"', index);
                    int close = open < 0 ? -1 : text.indexOf('"', open + 1);
                    index = close < 0 ? skipTo(text, index, ';') : skipTo(text, close + 1, ';');
                    continue;
                }
                case "SM", "BP", "PE", "WD", "LO" -> {
                    // Symbol, begin plot, encoded polyline and text: parameters that are not plain numbers.
                    if ("PE".equals(mnemonic)) {
                        encoded++;
                    }
                    index = skipTo(text, index, ';');
                    continue;
                }
                default -> { }
            }
            int end = index;
            while (end < text.length() && text.charAt(end) != ';' && !Character.isLetter(text.charAt(end))
                    && text.charAt(end) != 27) {
                end++;
            }
            command(mnemonic, numbers(text.substring(index, end)));
            index = end;
        }
    }

    private static int skipTo(String text, int from, char terminator) {
        int end = text.indexOf(terminator, from);
        return end < 0 ? text.length() : end + 1;
    }

    private static double[] numbers(String parameters) {
        String trimmed = parameters.trim();
        if (trimmed.isEmpty()) {
            return new double[0];
        }
        String[] parts = trimmed.split("[\\s,]+");
        List<Double> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                try {
                    values.add(Double.parseDouble(part));
                } catch (NumberFormatException ignored) {
                    // A malformed parameter is dropped, like a plotter would.
                }
            }
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private void command(String mnemonic, double[] p) {
        switch (mnemonic) {
            case "IN", "DF" -> {
                finishPath();
                penDown = false;
                absolute = true;
                if ("IN".equals(mnemonic)) {
                    x = 0;
                    y = 0;
                }
            }
            case "SP" -> {
                finishPath();
                pen = p.length > 0 ? (int) p[0] : 0;
            }
            case "PU" -> {
                finishPath();
                penDown = false;
                moves(p);
            }
            case "PD" -> {
                penDown = true;
                moves(p);
            }
            case "PA" -> {
                absolute = true;
                moves(p);
            }
            case "PR" -> {
                absolute = false;
                moves(p);
            }
            case "CI" -> {
                if (p.length > 0 && p[0] != 0) {
                    List<Coordinate> ring = arc(x, y, Math.abs(p[0]), 0, 2 * Math.PI);
                    ring.add(new Coordinate(ring.get(0)));
                    addLine(ring);
                }
            }
            case "AA", "AR" -> {
                if (p.length >= 3) {
                    double cx = "AA".equals(mnemonic) ? p[0] : x + p[0];
                    double cy = "AA".equals(mnemonic) ? p[1] : y + p[1];
                    double radius = Math.hypot(x - cx, y - cy);
                    double start = Math.atan2(y - cy, x - cx);
                    arcTo(arc(cx, cy, radius, start, Math.toRadians(p[2])));
                }
            }
            case "AT", "RT" -> {
                if (p.length >= 4) {
                    double ox = "AT".equals(mnemonic) ? 0 : x;
                    double oy = "AT".equals(mnemonic) ? 0 : y;
                    threePointArc(ox + p[0], oy + p[1], ox + p[2], oy + p[3]);
                }
            }
            case "EA", "RA", "ER", "RR" -> {
                if (p.length >= 2) {
                    boolean relative = mnemonic.charAt(1) == 'R';
                    double ex = relative ? x + p[0] : p[0];
                    double ey = relative ? y + p[1] : p[1];
                    addLine(List.of(new Coordinate(x, y), new Coordinate(ex, y), new Coordinate(ex, ey),
                            new Coordinate(x, ey), new Coordinate(x, y)));
                }
            }
            default -> { } // SC, IP, IW, LT, PW, VS, ...: no geometry
        }
    }

    /** PU/PD/PA/PR coordinate pairs: pen down draws, pen up only moves. */
    private void moves(double[] p) {
        for (int index = 0; index + 1 < p.length; index += 2) {
            double nx = absolute ? p[index] : x + p[index];
            double ny = absolute ? p[index + 1] : y + p[index + 1];
            if (penDown) {
                if (path.isEmpty()) {
                    path.add(new Coordinate(x, y));
                }
                path.add(new Coordinate(nx, ny));
            } else {
                finishPath();
            }
            x = nx;
            y = ny;
        }
    }

    private void arcTo(List<Coordinate> points) {
        Coordinate end = points.get(points.size() - 1);
        if (penDown) {
            if (path.isEmpty()) {
                path.add(new Coordinate(x, y));
            }
            path.addAll(points.subList(1, points.size()));
        }
        x = end.x;
        y = end.y;
    }

    private void threePointArc(double ix, double iy, double ex, double ey) {
        // Circle through the current point, the intermediate and the end point.
        double ax = x;
        double ay = y;
        double d = 2 * (ax * (iy - ey) + ix * (ey - ay) + ex * (ay - iy));
        if (Math.abs(d) < 1e-12) {
            arcTo(List.of(new Coordinate(x, y), new Coordinate(ex, ey))); // collinear: a straight line
            return;
        }
        double a2 = ax * ax + ay * ay;
        double i2 = ix * ix + iy * iy;
        double e2 = ex * ex + ey * ey;
        double cx = (a2 * (iy - ey) + i2 * (ey - ay) + e2 * (ay - iy)) / d;
        double cy = (a2 * (ex - ix) + i2 * (ax - ex) + e2 * (ix - ax)) / d;
        double start = Math.atan2(ay - cy, ax - cx);
        double middle = Math.atan2(iy - cy, ix - cx);
        double end = Math.atan2(ey - cy, ex - cx);
        double counterClockwise = normalize(end - start);
        boolean passesMiddle = normalize(middle - start) < counterClockwise;
        double sweep = passesMiddle ? counterClockwise : counterClockwise - 2 * Math.PI;
        List<Coordinate> points = arc(cx, cy, Math.hypot(ax - cx, ay - cy), start, sweep);
        points.set(points.size() - 1, new Coordinate(ex, ey));
        arcTo(points);
    }

    private static double normalize(double angle) {
        double result = angle % (2 * Math.PI);
        return result < 0 ? result + 2 * Math.PI : result;
    }

    private List<Coordinate> arc(double cx, double cy, double radius, double start, double sweep) {
        int steps = (int) Math.max(8, Math.min(4000, Math.ceil(Math.abs(sweep) * radius / segmentLength)));
        List<Coordinate> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double angle = start + sweep * step / steps;
            points.add(new Coordinate(cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)));
        }
        return points;
    }

    private void finishPath() {
        if (path.size() >= 2) {
            addLine(new ArrayList<>(path));
        }
        path.clear();
    }

    private void addLine(List<Coordinate> points) {
        if (pen <= 0) {
            return; // SP0: no pen in the holder draws nothing
        }
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int index = 0; index < coordinates.length; index++) {
            coordinates[index] = new Coordinate(points.get(index).x * scale, points.get(index).y * scale);
        }
        lines.computeIfAbsent(pen, ignored -> new ArrayList<>()).add(FACTORY.createLineString(coordinates));
    }
}
