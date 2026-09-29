package org.flatcam.cam.dxf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * File > Import > DXF - ParseDXF.py/camlib.import_dxf_as_geo: LINE, ARC, CIRCLE,
 * ELLIPSE, LWPOLYLINE, POLYLINE, SPLINE, POINT, SOLID, TRACE and block INSERTs
 * (with scale, rotation and arrays) from the model space of an ASCII DXF.
 * Circles and filled shapes become polygons, everything else lines merged end to
 * end (Python's linemerge); closed polylines stay closed paths, as in Python.
 *
 * <p>Where Python falls short this goes further: $INSUNITS converts the drawing
 * to the requested units (Python ignores it; a drawing without units is still
 * taken as already in them), polyline bulges are drawn as the arcs they are
 * (Python drew straight chords), SOLID/TRACE keep all four corners, nested
 * blocks and extrusion-mirrored entities are placed correctly, and a Gerber
 * gets the closed outlines filled even-odd - a board outline with holes becomes
 * copper with holes, where Python kept zero-width outline lines. Text,
 * dimensions and hatches are not imported; text is counted for the caller.
 */
public final class DxfImporter {

    public record Result(String units, Geometry shapes, Geometry copper, int skippedTextEntities) {
    }

    private record Pair(int code, String value) {
    }

    private record Entity(String type, List<Pair> pairs) {
        String text(int code, String fallback) {
            for (Pair pair : pairs) {
                if (pair.code() == code) {
                    return pair.value();
                }
            }
            return fallback;
        }

        double number(int code, double fallback) {
            String text = text(code, null);
            if (text == null) {
                return fallback;
            }
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException malformed) {
                throw new IllegalArgumentException("Malformed number in DXF " + type + ": " + text);
            }
        }

        int integer(int code, int fallback) {
            return (int) number(code, fallback);
        }
    }

    private record Block(double baseX, double baseY, List<Entity> entities) {
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final int MAX_BLOCK_DEPTH = 16;

    private final String units;
    private final double segmentLength;
    private final CancellationToken cancellation;
    private final Map<String, Block> blocks = new HashMap<>();
    private final List<Polygon> polygons = new ArrayList<>();
    private final List<Polygon> filled = new ArrayList<>();
    private final List<LineString> lines = new ArrayList<>();
    private int skippedText;
    private double scale = 1;

    private DxfImporter(String units, CancellationToken cancellation) {
        this.units = units;
        this.segmentLength = "MM".equals(units) ? 0.1 : 0.004;
        this.cancellation = cancellation;
    }

    public static Result parse(Path file, String units, CancellationToken cancellation) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > 22 && new String(bytes, 0, 18, StandardCharsets.ISO_8859_1).startsWith("AutoCAD Binary DXF")) {
            throw new IllegalArgumentException("Binary DXF is not supported - save it as ASCII DXF");
        }
        return parse(new String(bytes, StandardCharsets.ISO_8859_1), units, cancellation);
    }

    public static Result parse(String dxf, String units) {
        return parse(dxf, units, CancellationToken.none());
    }

    private static Result parse(String dxf, String units, CancellationToken cancellation) {
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported units: " + units);
        }
        Objects.requireNonNull(cancellation, "cancellation");
        DxfImporter importer = new DxfImporter(units, cancellation);
        List<Entity> modelSpace = importer.read(pairs(dxf));
        AffineTransformation toUnits = AffineTransformation.scaleInstance(importer.scale, importer.scale);
        for (Entity entity : modelSpace) {
            cancellation.throwIfCancellationRequested();
            if (entity.integer(67, 0) != 1) { // 67=1: paper space
                importer.entity(entity, toUnits, 0);
            }
        }
        return importer.result();
    }

    private static List<Pair> pairs(String dxf) {
        String[] rows = dxf.split("\r\n|\n|\r", -1);
        List<Pair> pairs = new ArrayList<>(rows.length / 2);
        for (int index = 0; index + 1 < rows.length; index += 2) {
            String code = rows[index].trim();
            if (code.isEmpty() && index + 2 >= rows.length) {
                break;
            }
            try {
                pairs.add(new Pair(Integer.parseInt(code), rows[index + 1].trim()));
            } catch (NumberFormatException malformed) {
                throw new IllegalArgumentException("Not an ASCII DXF file (bad group code \"" + code
                        + "\" at line " + (index + 1) + ")");
            }
        }
        if (pairs.isEmpty() || pairs.stream().noneMatch(pair -> pair.code() == 0 && pair.value().equals("SECTION"))) {
            throw new IllegalArgumentException("Not a DXF file: no SECTION found");
        }
        return pairs;
    }

    /** Reads the header units and the block table; returns the ENTITIES section. */
    private List<Entity> read(List<Pair> pairs) {
        List<Entity> modelSpace = new ArrayList<>();
        String section = null;
        Block currentBlock = null;
        String currentBlockName = null;
        for (int index = 0; index < pairs.size(); index++) {
            Pair pair = pairs.get(index);
            if (pair.code() == 0 && pair.value().equals("SECTION")) {
                section = index + 1 < pairs.size() ? pairs.get(index + 1).value() : null;
                continue;
            }
            if (pair.code() == 0 && pair.value().equals("ENDSEC")) {
                section = null;
                continue;
            }
            if ("HEADER".equals(section) && pair.code() == 9 && pair.value().equals("$INSUNITS")
                    && index + 1 < pairs.size()) {
                scale = unitScale((int) Double.parseDouble(pairs.get(index + 1).value()));
                continue;
            }
            if (pair.code() != 0 || !("ENTITIES".equals(section) || "BLOCKS".equals(section))) {
                continue;
            }
            int end = index + 1;
            while (end < pairs.size() && pairs.get(end).code() != 0) {
                end++;
            }
            Entity entity = new Entity(pair.value(), pairs.subList(index + 1, end));
            index = end - 1;
            if ("BLOCKS".equals(section)) {
                if (entity.type().equals("BLOCK")) {
                    currentBlockName = entity.text(2, "");
                    currentBlock = new Block(entity.number(10, 0), entity.number(20, 0), new ArrayList<>());
                } else if (entity.type().equals("ENDBLK")) {
                    if (currentBlockName != null) {
                        blocks.put(currentBlockName, new Block(currentBlock.baseX(), currentBlock.baseY(),
                                joinPolylines(currentBlock.entities())));
                    }
                    currentBlock = null;
                    currentBlockName = null;
                } else if (currentBlock != null) {
                    currentBlock.entities().add(entity);
                }
            } else {
                modelSpace.add(entity);
            }
        }
        return joinPolylines(modelSpace);
    }

    /** $INSUNITS to the requested units; unitless (0) is taken as already in them, as Python does. */
    private double unitScale(int insunits) {
        double millimetres = switch (insunits) {
            case 1 -> 25.4;
            case 2 -> 304.8;
            case 4 -> 1;
            case 5 -> 10;
            case 6 -> 1000;
            case 8 -> 25.4e-6;
            case 9 -> 25.4e-3;
            case 10 -> 914.4;
            case 13 -> 1e-3;
            case 14 -> 100;
            default -> Double.NaN;
        };
        if (Double.isNaN(millimetres)) {
            return 1;
        }
        return "MM".equals(units) ? millimetres : millimetres / 25.4;
    }

    private Result result() {
        List<Geometry> shapeParts = new ArrayList<>(polygons);
        shapeParts.addAll(filled);
        LineMerger merger = new LineMerger();
        snapEndpoints(lines).forEach(merger::add);
        List<Geometry> merged = new ArrayList<>();
        for (Object line : merger.getMergedLineStrings()) {
            merged.add((Geometry) line);
        }
        shapeParts.addAll(merged);
        Geometry shapes = FACTORY.buildGeometry(shapeParts);
        if (shapes.isEmpty()) {
            throw new IllegalArgumentException(skippedText > 0
                    ? "The DXF only has text, which is not imported"
                    : "The DXF has no supported entities in its model space");
        }
        return new Result(units, shapes, copper(merged), skippedText);
    }

    /**
     * Makes line ends that CAD meant to touch identical, so they merge and close:
     * an arc's end computed from its angles, or a coordinate rounded on export,
     * misses the next entity by a hair (Python's exact linemerge left those open).
     */
    private List<LineString> snapEndpoints(List<LineString> source) {
        double tolerance = "MM".equals(units) ? 0.001 : 0.00004;
        Map<Long, List<Coordinate>> grid = new HashMap<>();
        List<LineString> snapped = new ArrayList<>(source.size());
        for (LineString line : source) {
            Coordinate[] coordinates = line.getCoordinates().clone();
            coordinates[0] = snap(coordinates[0], grid, tolerance);
            coordinates[coordinates.length - 1] = snap(coordinates[coordinates.length - 1], grid, tolerance);
            if (coordinates.length > 2 || !coordinates[0].equals2D(coordinates[1])) {
                snapped.add(FACTORY.createLineString(coordinates));
            }
        }
        return snapped;
    }

    private static Coordinate snap(Coordinate point, Map<Long, List<Coordinate>> grid, double tolerance) {
        long cellX = (long) Math.floor(point.x / tolerance);
        long cellY = (long) Math.floor(point.y / tolerance);
        for (long dx = -1; dx <= 1; dx++) {
            for (long dy = -1; dy <= 1; dy++) {
                for (Coordinate known : grid.getOrDefault(cell(cellX + dx, cellY + dy), List.of())) {
                    if (known.distance(point) <= tolerance) {
                        return known;
                    }
                }
            }
        }
        Coordinate representative = new Coordinate(point.x, point.y);
        grid.computeIfAbsent(cell(cellX, cellY), ignored -> new ArrayList<>()).add(representative);
        return representative;
    }

    private static long cell(long x, long y) {
        return x * 73_856_093L ^ y * 19_349_663L;
    }

    /** Closed outlines filled even-odd (holes stay holes), plus the filled SOLID/TRACE/POINT shapes. */
    private Geometry copper(List<Geometry> mergedLines) {
        List<Geometry> outlines = new ArrayList<>(mergedLines);
        for (Polygon polygon : polygons) {
            outlines.add(polygon.getBoundary());
        }
        List<Geometry> copperParts = new ArrayList<>();
        if (!outlines.isEmpty()) {
            Geometry noded = UnaryUnionOp.union(outlines);
            Polygonizer polygonizer = new Polygonizer(true);
            polygonizer.add(noded);
            copperParts.add(polygonizer.getGeometry());
        }
        filled.forEach(shape -> copperParts.add(GeometryFixer.fix(shape)));
        return copperParts.isEmpty() ? FACTORY.createGeometryCollection() : UnaryUnionOp.union(copperParts);
    }

    // ---- entities --------------------------------------------------------------------------------------------------

    private void entity(Entity entity, AffineTransformation transform, int depth) {
        AffineTransformation placed = withExtrusion(entity, transform);
        switch (entity.type()) {
            case "LINE" -> addLine(List.of(new Coordinate(entity.number(10, 0), entity.number(20, 0)),
                    new Coordinate(entity.number(11, 0), entity.number(21, 0))), transform);
            case "CIRCLE" -> {
                double radius = entity.number(40, 0);
                if (radius > 0) {
                    addPolygon(arcPoints(entity.number(10, 0), entity.number(20, 0), radius, radius, 0,
                            0, 2 * Math.PI, false, placed), placed);
                }
            }
            case "ARC" -> {
                double radius = entity.number(40, 0);
                double start = Math.toRadians(entity.number(50, 0));
                double end = Math.toRadians(entity.number(51, 360));
                double sweep = end - start;
                while (sweep <= 0) {
                    sweep += 2 * Math.PI;
                }
                if (radius > 0) {
                    addLine(arcPoints(entity.number(10, 0), entity.number(20, 0), radius, radius, 0,
                            start, sweep, true, placed), placed);
                }
            }
            case "ELLIPSE" -> ellipse(entity, transform);
            case "LWPOLYLINE" -> lwPolyline(entity, placed);
            case "POLYLINE" -> polyline(entity, placed);
            case "SPLINE" -> spline(entity, transform);
            case "POINT" -> {
                // Python: a dot of radius 0.01 drawing units, so points still show up and survive as copper.
                Coordinate point = new Coordinate(entity.number(10, 0), entity.number(20, 0));
                Coordinate target = new Coordinate();
                transform.transform(point, target);
                filled.add((Polygon) FACTORY.createPoint(target).buffer(0.01 * scale, 4));
            }
            case "SOLID", "TRACE" -> {
                // DXF lists the 3rd and 4th corners crosswise; a triangle repeats the 3rd.
                List<Coordinate> corners = new ArrayList<>(List.of(
                        new Coordinate(entity.number(10, 0), entity.number(20, 0)),
                        new Coordinate(entity.number(11, 0), entity.number(21, 0)),
                        new Coordinate(entity.number(13, entity.number(12, 0)), entity.number(23, entity.number(22, 0))),
                        new Coordinate(entity.number(12, 0), entity.number(22, 0))));
                CoordinateList ring = transformed(corners, placed);
                ring.closeRing();
                if (ring.size() >= 4) {
                    Geometry solid = GeometryFixer.fix(FACTORY.createPolygon(ring.toCoordinateArray()));
                    for (int part = 0; part < solid.getNumGeometries(); part++) {
                        if (solid.getGeometryN(part) instanceof Polygon polygon && !polygon.isEmpty()) {
                            filled.add(polygon);
                        }
                    }
                }
            }
            case "INSERT" -> insert(entity, placed, depth);
            case "TEXT", "MTEXT", "ATTRIB", "ATTDEF" -> skippedText++;
            default -> { } // DIMENSION, HATCH, VIEWPORT, 3D entities...: not geometry to cut.
        }
    }

    /** An extrusion of (0,0,-1) - common in mirrored drawings - flips the entity's own X axis. */
    private static AffineTransformation withExtrusion(Entity entity, AffineTransformation transform) {
        if (entity.number(230, 1) >= 0) {
            return transform;
        }
        AffineTransformation mirrored = AffineTransformation.scaleInstance(-1, 1);
        mirrored.compose(transform);
        return mirrored;
    }

    private void insert(Entity insert, AffineTransformation transform, int depth) {
        Block block = blocks.get(insert.text(2, ""));
        if (block == null || depth >= MAX_BLOCK_DEPTH) {
            return;
        }
        double scaleX = insert.number(41, 1);
        double scaleY = insert.number(42, 1);
        double rotation = Math.toRadians(insert.number(50, 0));
        int columns = Math.max(1, insert.integer(70, 1));
        int rows = Math.max(1, insert.integer(71, 1));
        double columnSpacing = insert.number(44, 0);
        double rowSpacing = insert.number(45, 0);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                // Block point -> relative to its base -> scaled -> array offset -> rotated -> at the insert point.
                AffineTransformation placement = AffineTransformation.translationInstance(-block.baseX(), -block.baseY());
                placement.scale(scaleX, scaleY);
                placement.translate(column * columnSpacing, row * rowSpacing);
                placement.rotate(rotation);
                placement.translate(insert.number(10, 0), insert.number(20, 0));
                placement.compose(transform);
                for (Entity entity : block.entities()) {
                    cancellation.throwIfCancellationRequested();
                    entity(entity, placement, depth + 1);
                }
            }
        }
    }

    private void ellipse(Entity ellipse, AffineTransformation transform) {
        double majorX = ellipse.number(11, 0);
        double majorY = ellipse.number(21, 0);
        double major = Math.hypot(majorX, majorY);
        double minor = major * ellipse.number(40, 1);
        if (!(major > 0) || !(minor > 0)) {
            return;
        }
        double start = ellipse.number(41, 0);
        double end = ellipse.number(42, 2 * Math.PI);
        double sweep = end - start;
        while (sweep <= 0) {
            sweep += 2 * Math.PI;
        }
        boolean full = Math.abs(sweep - 2 * Math.PI) < 1e-9;
        List<Coordinate> points = arcPoints(ellipse.number(10, 0), ellipse.number(20, 0), major, minor,
                Math.atan2(majorY, majorX), start, sweep, !full, transform);
        if (full) {
            addPolygon(points, transform);
        } else {
            addLine(points, transform);
        }
    }

    private void lwPolyline(Entity polyline, AffineTransformation transform) {
        List<double[]> vertices = new ArrayList<>(); // x, y, bulge
        for (Pair pair : polyline.pairs()) {
            if (pair.code() == 10) {
                vertices.add(new double[]{Double.parseDouble(pair.value()), 0, 0});
            } else if (pair.code() == 20 && !vertices.isEmpty()) {
                vertices.get(vertices.size() - 1)[1] = Double.parseDouble(pair.value());
            } else if (pair.code() == 42 && !vertices.isEmpty()) {
                vertices.get(vertices.size() - 1)[2] = Double.parseDouble(pair.value());
            }
        }
        addVertices(vertices, (polyline.integer(70, 0) & 1) != 0, transform);
    }

    /** A POLYLINE as joined by {@link #joinPolylines}: its header, then each vertex's 10/20/42. */
    private void polyline(Entity polyline, AffineTransformation transform) {
        int flags = polyline.integer(70, 0);
        if ((flags & (16 | 64)) != 0) {
            return; // 3D polygon mesh / polyface mesh
        }
        lwPolyline(polyline, transform);
    }

    /**
     * The VERTEX entities of an old-style POLYLINE follow it up to SEQEND as separate
     * entities; this folds them into one entity shaped like an LWPOLYLINE. The header's
     * own 10/20 is a dummy elevation point and is dropped, as are spline frame points.
     */
    private static List<Entity> joinPolylines(List<Entity> entities) {
        List<Entity> joined = new ArrayList<>();
        for (int index = 0; index < entities.size(); index++) {
            Entity entity = entities.get(index);
            if (!entity.type().equals("POLYLINE")) {
                if (!entity.type().equals("VERTEX") && !entity.type().equals("SEQEND")) {
                    joined.add(entity);
                }
                continue;
            }
            List<Pair> pairs = new ArrayList<>();
            for (Pair pair : entity.pairs()) {
                if (pair.code() != 10 && pair.code() != 20 && pair.code() != 30) {
                    pairs.add(pair);
                }
            }
            while (index + 1 < entities.size() && entities.get(index + 1).type().equals("VERTEX")) {
                Entity vertex = entities.get(++index);
                if ((vertex.integer(70, 0) & 16) != 0) {
                    continue; // spline frame control point
                }
                pairs.add(new Pair(10, vertex.text(10, "0")));
                pairs.add(new Pair(20, vertex.text(20, "0")));
                pairs.add(new Pair(42, vertex.text(42, "0")));
            }
            joined.add(new Entity("POLYLINE", pairs));
        }
        return joined;
    }

    /** Polyline vertices with bulges: a bulge is tan(arc angle / 4), positive counter-clockwise. */
    private void addVertices(List<double[]> vertices, boolean closed, AffineTransformation transform) {
        if (vertices.size() < 2) {
            return;
        }
        List<Coordinate> points = new ArrayList<>();
        int segments = closed ? vertices.size() : vertices.size() - 1;
        points.add(new Coordinate(vertices.get(0)[0], vertices.get(0)[1]));
        for (int index = 0; index < segments; index++) {
            double[] from = vertices.get(index);
            double[] to = vertices.get((index + 1) % vertices.size());
            double bulge = from[2];
            double chord = Math.hypot(to[0] - from[0], to[1] - from[1]);
            if (Math.abs(bulge) > 1e-12 && chord > 0) {
                // The center sits on the chord's left normal, (chord/2)(1 - b^2)/(2b) from its midpoint.
                double offset = chord / 2 * (1 - bulge * bulge) / (2 * bulge);
                double centerX = (from[0] + to[0]) / 2 - (to[1] - from[1]) / chord * offset;
                double centerY = (from[1] + to[1]) / 2 + (to[0] - from[0]) / chord * offset;
                double radius = Math.hypot(from[0] - centerX, from[1] - centerY);
                double start = Math.atan2(from[1] - centerY, from[0] - centerX);
                List<Coordinate> arc = arcPoints(centerX, centerY, radius, radius, 0, start,
                        4 * Math.atan(bulge), true, transform);
                points.addAll(arc.subList(1, arc.size()));
                points.set(points.size() - 1, new Coordinate(to[0], to[1]));
            } else {
                points.add(new Coordinate(to[0], to[1]));
            }
        }
        // Python keeps closed polylines as closed paths, not polygons.
        addLine(points, transform);
    }

    private void spline(Entity spline, AffineTransformation transform) {
        int degree = spline.integer(71, 3);
        List<Double> knots = new ArrayList<>();
        List<double[]> control = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<double[]> fit = new ArrayList<>();
        for (Pair pair : spline.pairs()) {
            switch (pair.code()) {
                case 40 -> knots.add(Double.parseDouble(pair.value()));
                case 41 -> weights.add(Double.parseDouble(pair.value()));
                case 10 -> control.add(new double[]{Double.parseDouble(pair.value()), 0});
                case 20 -> {
                    if (!control.isEmpty()) {
                        control.get(control.size() - 1)[1] = Double.parseDouble(pair.value());
                    }
                }
                case 11 -> fit.add(new double[]{Double.parseDouble(pair.value()), 0});
                case 21 -> {
                    if (!fit.isEmpty()) {
                        fit.get(fit.size() - 1)[1] = Double.parseDouble(pair.value());
                    }
                }
                default -> { }
            }
        }
        List<Coordinate> points = new ArrayList<>();
        if (control.size() > degree && knots.size() == control.size() + degree + 1) {
            double first = knots.get(degree);
            double last = knots.get(control.size());
            double polygonLength = 0;
            for (int index = 1; index < control.size(); index++) {
                polygonLength += Math.hypot(control.get(index)[0] - control.get(index - 1)[0],
                        control.get(index)[1] - control.get(index - 1)[1]);
            }
            int steps = curveSteps(polygonLength, transform);
            for (int step = 0; step <= steps; step++) {
                double u = first + (last - first) * step / steps;
                points.add(deBoor(degree, knots, control, weights, u));
            }
        } else if (fit.size() >= 2) {
            fit.forEach(point -> points.add(new Coordinate(point[0], point[1]))); // no knots: follow the fit points
        } else {
            control.forEach(point -> points.add(new Coordinate(point[0], point[1])));
        }
        addLine(points, transform);
    }

    /** Rational B-spline point by de Boor's algorithm (weights default to 1). */
    private static Coordinate deBoor(int degree, List<Double> knots, List<double[]> control, List<Double> weights,
                                     double u) {
        int span = degree;
        while (span < control.size() - 1 && u >= knots.get(span + 1)) {
            span++;
        }
        double[][] d = new double[degree + 1][3];
        for (int j = 0; j <= degree; j++) {
            int index = span - degree + j;
            double weight = weights.size() == control.size() ? weights.get(index) : 1;
            d[j][0] = control.get(index)[0] * weight;
            d[j][1] = control.get(index)[1] * weight;
            d[j][2] = weight;
        }
        for (int r = 1; r <= degree; r++) {
            for (int j = degree; j >= r; j--) {
                int index = span - degree + j;
                double denominator = knots.get(index + degree - r + 1) - knots.get(index);
                double alpha = denominator == 0 ? 0 : (u - knots.get(index)) / denominator;
                for (int k = 0; k < 3; k++) {
                    d[j][k] = (1 - alpha) * d[j - 1][k] + alpha * d[j][k];
                }
            }
        }
        return new Coordinate(d[degree][0] / d[degree][2], d[degree][1] / d[degree][2]);
    }

    // ---- geometry helpers ------------------------------------------------------------------------------------------

    /** Points of an (elliptical) arc; {@code includeEnd} false for a closed ring's last point. */
    private List<Coordinate> arcPoints(double cx, double cy, double rx, double ry, double rotation,
                                       double start, double sweep, boolean includeEnd, AffineTransformation transform) {
        int steps = Math.max(includeEnd ? 4 : 32, curveSteps(Math.abs(sweep) * Math.max(rx, ry), transform));
        List<Coordinate> points = new ArrayList<>(steps + 1);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        int last = includeEnd ? steps : steps - 1;
        for (int step = 0; step <= last; step++) {
            double angle = start + sweep * step / steps;
            double x = rx * Math.cos(angle);
            double y = ry * Math.sin(angle);
            points.add(new Coordinate(cx + x * cos - y * sin, cy + x * sin + y * cos));
        }
        return points;
    }

    private int curveSteps(double length, AffineTransformation transform) {
        double[] m = transform.getMatrixEntries();
        double drawn = length * Math.sqrt(Math.abs(m[0] * m[4] - m[1] * m[3]));
        if (!Double.isFinite(drawn)) {
            return 8;
        }
        return (int) Math.max(8, Math.min(4000, Math.ceil(drawn / segmentLength)));
    }

    private void addLine(List<Coordinate> points, AffineTransformation transform) {
        CoordinateList coordinates = transformed(points, transform);
        if (coordinates.size() >= 2) {
            lines.add(FACTORY.createLineString(coordinates.toCoordinateArray()));
        }
    }

    private void addPolygon(List<Coordinate> ring, AffineTransformation transform) {
        CoordinateList coordinates = transformed(ring, transform);
        coordinates.closeRing();
        if (coordinates.size() >= 4) {
            Geometry polygon = GeometryFixer.fix(FACTORY.createPolygon(coordinates.toCoordinateArray()));
            for (int part = 0; part < polygon.getNumGeometries(); part++) {
                if (polygon.getGeometryN(part) instanceof Polygon piece && !piece.isEmpty()) {
                    polygons.add(piece);
                }
            }
        }
    }

    private static CoordinateList transformed(List<Coordinate> points, AffineTransformation transform) {
        CoordinateList coordinates = new CoordinateList();
        for (Coordinate point : points) {
            Coordinate target = new Coordinate();
            transform.transform(point, target);
            if (Double.isFinite(target.x) && Double.isFinite(target.y)) {
                coordinates.add(target, false);
            }
        }
        return coordinates;
    }
}
