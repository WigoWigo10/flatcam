package org.flatcam.cam.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolRulesCheck.py: design rules checked on the Gerbers (copper, silk, solder mask, outline) and Excellons of a
 * board - trace size, clearances between copper / silk / mask / outline, minimum annular ring, hole to hole clearance and
 * hole size. Each rule yields the places where it is violated (the middle of the gap, the centre of a bad hole...).
 *
 * <p>Differences from Python, which are fixes: distances are found through a spatial index instead of comparing every pair;
 * the solder mask sliver rule is gated by its own switch (Python tests the silk-to-silk one); silk to outline uses its own
 * value (Python reads the copper-to-outline one); one piece of copper is a pass with a note instead of a failure;
 * hole size is reported per Excellon (Python's report only shows the first); and gaps under 0.000002 count as touching
 * (Python merges the copper with a 0.000001 buffer first).
 */
public final class RulesCheck {

    /** The ten rules, in the order Python runs them. */
    public enum Rule {
        TRACE_SIZE("Tamanho da trilha"),
        COPPER_TO_COPPER("Distancia cobre a cobre"),
        COPPER_TO_OUTLINE("Distancia cobre ao contorno"),
        SILK_TO_SILK("Distancia seda a seda"),
        SILK_TO_MASK("Distancia seda a mascara de solda"),
        SILK_TO_OUTLINE("Distancia seda ao contorno"),
        MASK_SLIVER("Lasca minima da mascara de solda"),
        ANNULAR_RING("Anel anular minimo"),
        HOLE_TO_HOLE("Distancia furo a furo"),
        HOLE_SIZE("Tamanho do furo");

        private final String title;

        Rule(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** A rule's switch and its limit (Python's defaults in {@link #defaults()}). */
    public record Setting(boolean enabled, double value) {
    }

    public static Map<Rule, Setting> defaults() {
        Map<Rule, Setting> defaults = new LinkedHashMap<>();
        defaults.put(Rule.TRACE_SIZE, new Setting(true, 0.25));
        defaults.put(Rule.COPPER_TO_COPPER, new Setting(true, 0.25));
        defaults.put(Rule.COPPER_TO_OUTLINE, new Setting(true, 1.0));
        defaults.put(Rule.SILK_TO_SILK, new Setting(true, 0.25));
        defaults.put(Rule.SILK_TO_MASK, new Setting(true, 0.25));
        defaults.put(Rule.SILK_TO_OUTLINE, new Setting(true, 1.0));
        defaults.put(Rule.MASK_SLIVER, new Setting(true, 0.25));
        defaults.put(Rule.ANNULAR_RING, new Setting(true, 0.3));
        defaults.put(Rule.HOLE_TO_HOLE, new Setting(true, 0.3));
        defaults.put(Rule.HOLE_SIZE, new Setting(true, 0.3));
        return defaults;
    }

    /** An object with the name it has in the project (null object = not chosen). */
    public record Named<T>(String name, T image) {
    }

    /** The objects of the board; each may be null. */
    public record Board(Named<GerberImage> copperTop, Named<GerberImage> copperBottom, Named<GerberImage> silkTop,
                        Named<GerberImage> silkBottom, Named<GerberImage> maskTop, Named<GerberImage> maskBottom,
                        Named<GerberImage> outline, Named<ExcellonImage> drills1, Named<ExcellonImage> drills2) {
    }

    /**
     * @param title    e.g. "TOP -> Distancia cobre a cobre"
     * @param files    the objects involved
     * @param points   where the rule is violated
     * @param sizes    the offending sizes (trace and hole size rules)
     * @param error    why the rule could not run, or null
     * @param note     an explanation that does not change the verdict, or null
     */
    public record RuleResult(Rule rule, String title, List<String> files, List<Coordinate> points, List<Double> sizes,
                             String error, String note) {
        public boolean ran() {
            return error == null;
        }

        public boolean failed() {
            return error == null && (!points.isEmpty() || !sizes.isEmpty());
        }
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final double TOUCHING = 2e-6;
    private static final int DECIMALS = 4;

    private RulesCheck() {
    }

    public static List<RuleResult> check(Board board, Map<Rule, Setting> settings, CancellationToken cancellation,
                                         ProgressCallback progress) {
        List<RuleResult> results = new ArrayList<>();
        int total = (int) settings.values().stream().filter(Setting::enabled).count();
        int done = 0;
        for (Rule rule : Rule.values()) {
            Setting setting = settings.get(rule);
            if (setting == null || !setting.enabled()) {
                continue;
            }
            cancellation.throwIfCancellationRequested();
            if (!(setting.value() > 0) || !Double.isFinite(setting.value())) {
                results.add(error(rule, rule.title(), "O valor nao e valido"));
            } else {
                run(rule, setting.value(), board, cancellation, results);
            }
            progress.report((double) ++done / Math.max(1, total));
        }
        return results;
    }

    private static void run(Rule rule, double value, Board board, CancellationToken cancellation, List<RuleResult> out) {
        switch (rule) {
            case TRACE_SIZE -> {
                boolean any = false;
                for (Named<GerberImage> copper : List.of(nullToEmpty(board.copperTop()), nullToEmpty(board.copperBottom()))) {
                    if (copper.image() != null) {
                        any = true;
                        out.add(traceSize(copper, value));
                    }
                }
                if (!any) {
                    out.add(error(rule, rule.title(), "Escolha ao menos um Gerber de cobre"));
                }
            }
            case COPPER_TO_COPPER -> within(rule, "TOPO", board.copperTop(), "BASE", board.copperBottom(), value,
                    cancellation, out, "Escolha ao menos um Gerber de cobre");
            case SILK_TO_SILK -> within(rule, "TOPO", board.silkTop(), "BASE", board.silkBottom(), value, cancellation,
                    out, "Escolha ao menos um Gerber de seda");
            case MASK_SLIVER -> within(rule, "TOPO", board.maskTop(), "BASE", board.maskBottom(), value, cancellation,
                    out, "Escolha ao menos um Gerber de mascara de solda");
            case COPPER_TO_OUTLINE -> toOutline(rule, java.util.Arrays.asList(board.copperTop(), board.copperBottom()), board.outline(),
                    value, cancellation, out, "Escolha um Gerber de cobre e o do contorno");
            case SILK_TO_OUTLINE -> toOutline(rule, java.util.Arrays.asList(board.silkTop(), board.silkBottom()), board.outline(), value,
                    cancellation, out, "Escolha um Gerber de seda e o do contorno");
            case SILK_TO_MASK -> {
                boolean top = board.silkTop() != null && board.maskTop() != null;
                boolean bottom = board.silkBottom() != null && board.maskBottom() != null;
                if (!top && !bottom) {
                    out.add(error(rule, rule.title(), (board.silkTop() != null || board.silkBottom() != null)
                            && (board.maskTop() != null || board.maskBottom() != null)
                            ? "A seda e a mascara de solda devem ser ambas do topo ou ambas da base"
                            : "Escolha um Gerber de seda e um de mascara de solda"));
                }
                if (top) {
                    cross(rule, "TOPO", board.silkTop(), board.maskTop(), value, cancellation, out);
                }
                if (bottom) {
                    cross(rule, "BASE", board.silkBottom(), board.maskBottom(), value, cancellation, out);
                }
            }
            case ANNULAR_RING -> annularRing(board, value, cancellation, out);
            case HOLE_TO_HOLE -> holeToHole(board, value, cancellation, out);
            case HOLE_SIZE -> holeSize(board, value, out);
        }
    }

    // --- rules ---------------------------------------------------------------------------------------------------

    private static RuleResult traceSize(Named<GerberImage> copper, double limit) {
        List<Double> sizes = new ArrayList<>();
        List<Coordinate> points = new ArrayList<>();
        for (Map.Entry<String, Aperture> entry : copper.image().apertures().entrySet()) {
            Aperture aperture = entry.getValue();
            if (aperture.kind == ApertureKind.MACRO) {
                continue;
            }
            // Python's "size": the diameter of a circle, the diagonal of a rectangle or obround.
            double size = aperture.kind == ApertureKind.CIRCLE || aperture.kind == ApertureKind.POLYGON ? aperture.width
                    : Math.hypot(aperture.width, aperture.height);
            if (size < limit && size != 0.0) {
                sizes.add(round(size));
                for (GerberShape shape : copper.image().shapes()) {
                    if (!shape.clear() && entry.getKey().equals(shape.apertureCode())) {
                        points.add(shape.geometry().getInteriorPoint().getCoordinate());
                    }
                }
            }
        }
        return new RuleResult(Rule.TRACE_SIZE, Rule.TRACE_SIZE.title(), List.of(copper.name()), dedupe(points), sizes,
                null, null);
    }

    private static void within(Rule rule, String firstLabel, Named<GerberImage> first, String secondLabel,
                               Named<GerberImage> second, double limit, CancellationToken cancellation,
                               List<RuleResult> out, String missing) {
        if (first == null && second == null) {
            out.add(error(rule, rule.title(), missing));
            return;
        }
        for (Object[] side : new Object[][] {{firstLabel, first}, {secondLabel, second}}) {
            @SuppressWarnings("unchecked")
            Named<GerberImage> layer = (Named<GerberImage>) side[1];
            if (layer == null) {
                continue;
            }
            String title = side[0] + " -> " + rule.title();
            List<Polygon> pieces = pieces(layer.image().solidGeometry());
            if (pieces.size() < 2) {
                out.add(new RuleResult(rule, title, List.of(layer.name()), List.of(), List.of(), null,
                        "Ha um unico poligono: nao ha o que comparar"));
            } else {
                out.add(new RuleResult(rule, title, List.of(layer.name()),
                        gaps(pieces, pieces, true, limit, cancellation), List.of(), null, null));
            }
        }
    }

    private static void cross(Rule rule, String label, Named<GerberImage> first, Named<GerberImage> second, double limit,
                              CancellationToken cancellation, List<RuleResult> out) {
        List<Polygon> a = pieces(first.image().solidGeometry());
        List<Polygon> b = pieces(second.image().solidGeometry());
        out.add(new RuleResult(rule, label + " -> " + rule.title(), List.of(first.name(), second.name()),
                gaps(a, b, false, limit, cancellation), List.of(), null, null));
    }

    private static void toOutline(Rule rule, List<Named<GerberImage>> layers, Named<GerberImage> outline, double limit,
                                  CancellationToken cancellation, List<RuleResult> out, String missing) {
        List<Named<GerberImage>> chosen = layers.stream().filter(layer -> layer != null).toList();
        if (chosen.isEmpty() || outline == null) {
            out.add(error(rule, rule.title(), missing));
            return;
        }
        List<Polygon> outlinePieces = pieces(outline.image().solidGeometry());
        List<String> files = new ArrayList<>();
        List<Polygon> layerPieces = new ArrayList<>();
        for (Named<GerberImage> layer : chosen) {
            files.add(layer.name());
            layerPieces.addAll(pieces(layer.image().solidGeometry()));
        }
        files.add(outline.name());
        out.add(new RuleResult(rule, rule.title(), files, gaps(layerPieces, outlinePieces, false, limit, cancellation),
                List.of(), null, null));
    }

    private static void annularRing(Board board, double limit, CancellationToken cancellation, List<RuleResult> out) {
        Rule rule = Rule.ANNULAR_RING;
        List<Named<GerberImage>> coppers = new ArrayList<>();
        for (Named<GerberImage> copper : List.of(nullToEmpty(board.copperTop()), nullToEmpty(board.copperBottom()))) {
            if (copper.image() != null) {
                coppers.add(copper);
            }
        }
        List<Named<ExcellonImage>> drills = new ArrayList<>();
        for (Named<ExcellonImage> drill : List.of(nullToEmptyDrill(board.drills1()), nullToEmptyDrill(board.drills2()))) {
            if (drill.image() != null) {
                drills.add(drill);
            }
        }
        if (coppers.isEmpty() || drills.isEmpty()) {
            out.add(error(rule, rule.title(), "Escolha ao menos um Gerber de cobre e um Excellon"));
            return;
        }
        List<Polygon> copper = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (Named<GerberImage> layer : coppers) {
            files.add(layer.name());
            copper.addAll(pieces(layer.image().solidGeometry()));
        }
        List<Geometry> holes = new ArrayList<>();
        for (Named<ExcellonImage> drill : drills) {
            files.add(drill.name());
            holes.addAll(holes(drill.image()));
        }
        STRtree tree = new STRtree();
        for (int i = 0; i < copper.size(); i++) {
            tree.insert(copper.get(i).getEnvelopeInternal(), i);
        }
        tree.build();
        ConcurrentLinkedQueue<Coordinate> found = new ConcurrentLinkedQueue<>();
        IntStream.range(0, holes.size()).parallel().forEach(h -> {
            cancellation.throwIfCancellationRequested();
            Geometry hole = holes.get(h);
            Envelope probe = new Envelope(hole.getEnvelopeInternal());
            probe.expandBy(limit);
            for (Object candidate : tree.query(probe)) {
                LineString exterior = copper.get((Integer) candidate).getExteriorRing();
                DistanceOp operation = new DistanceOp(exterior, hole);
                double distance = operation.distance();
                if (distance > 0 && distance < limit) {
                    Coordinate[] nearest = operation.nearestPoints();
                    found.add(new Coordinate((nearest[0].x + nearest[1].x) / 2, (nearest[0].y + nearest[1].y) / 2));
                } else if (distance == 0) {
                    // The hole reaches the edge of the copper: no ring at all.
                    found.add(hole.getInteriorPoint().getCoordinate());
                }
            }
        });
        out.add(new RuleResult(rule, rule.title(), files, dedupe(new ArrayList<>(found)), List.of(), null, null));
    }

    private static void holeToHole(Board board, double limit, CancellationToken cancellation, List<RuleResult> out) {
        Rule rule = Rule.HOLE_TO_HOLE;
        List<Named<ExcellonImage>> drills = new ArrayList<>();
        for (Named<ExcellonImage> drill : List.of(nullToEmptyDrill(board.drills1()), nullToEmptyDrill(board.drills2()))) {
            if (drill.image() != null) {
                drills.add(drill);
            }
        }
        if (drills.isEmpty()) {
            out.add(error(rule, rule.title(), "Escolha ao menos um Excellon"));
            return;
        }
        List<Geometry> holes = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (Named<ExcellonImage> drill : drills) {
            files.add(drill.name());
            holes.addAll(holes(drill.image()));
        }
        out.add(new RuleResult(rule, rule.title(), files, gaps(holes, holes, true, limit, cancellation), List.of(), null,
                null));
    }

    private static void holeSize(Board board, double limit, List<RuleResult> out) {
        Rule rule = Rule.HOLE_SIZE;
        boolean any = false;
        for (Named<ExcellonImage> drill : List.of(nullToEmptyDrill(board.drills1()), nullToEmptyDrill(board.drills2()))) {
            if (drill.image() == null) {
                continue;
            }
            any = true;
            List<Double> small = new ArrayList<>();
            for (double diameter : drill.image().toolDiameters().values()) {
                double rounded = round(diameter);
                if (rounded < limit) {
                    small.add(rounded);
                }
            }
            out.add(new RuleResult(rule, rule.title(), List.of(drill.name()), List.of(), small, null, null));
        }
        if (!any) {
            out.add(error(rule, rule.title(), "Escolha ao menos um Excellon"));
        }
    }

    // --- geometry helpers ---------------------------------------------------------------------------------------

    /**
     * The middles of the gaps smaller than {@code limit} between a piece of {@code first} and a piece of {@code second}
     * (each unordered pair once when they are the same list); gaps of {@link #TOUCHING} or less count as touching.
     */
    private static List<Coordinate> gaps(List<? extends Geometry> first, List<? extends Geometry> second, boolean same,
                                         double limit, CancellationToken cancellation) {
        STRtree tree = new STRtree();
        for (int i = 0; i < second.size(); i++) {
            tree.insert(second.get(i).getEnvelopeInternal(), i);
        }
        tree.build();
        ConcurrentLinkedQueue<Coordinate> found = new ConcurrentLinkedQueue<>();
        IntStream.range(0, first.size()).parallel().forEach(i -> {
            cancellation.throwIfCancellationRequested();
            Envelope probe = new Envelope(first.get(i).getEnvelopeInternal());
            probe.expandBy(limit);
            IndexedFacetDistance distance = null;
            for (Object candidate : tree.query(probe)) {
                int j = (Integer) candidate;
                if (same && j <= i) {
                    continue;
                }
                if (distance == null) {
                    distance = new IndexedFacetDistance(first.get(i));
                }
                Coordinate[] nearest = distance.nearestPoints(second.get(j));
                double gap = nearest[0].distance(nearest[1]);
                if (gap < limit && gap > TOUCHING) {
                    found.add(new Coordinate((nearest[0].x + nearest[1].x) / 2, (nearest[0].y + nearest[1].y) / 2));
                }
            }
        });
        return dedupe(new ArrayList<>(found));
    }

    private static List<Coordinate> dedupe(List<Coordinate> points) {
        Set<String> seen = new LinkedHashSet<>();
        List<Coordinate> unique = new ArrayList<>();
        // Sorted so the report does not depend on the order the threads finished in.
        points.sort((a, b) -> a.x != b.x ? Double.compare(a.x, b.x) : Double.compare(a.y, b.y));
        for (Coordinate point : points) {
            if (seen.add(Math.round(point.x * 1e6) + "," + Math.round(point.y * 1e6))) {
                unique.add(new Coordinate(point.x, point.y));
            }
        }
        return unique;
    }

    /** The separate pieces of copper (or silk...) of a Gerber's solid. */
    private static List<Polygon> pieces(Geometry solid) {
        List<Polygon> pieces = new ArrayList<>();
        if (solid == null || solid.isEmpty()) {
            return pieces;
        }
        List<Geometry> parts = new ArrayList<>();
        flatten(solid, parts);
        Geometry merged = OverlayNGRobust.union(parts);
        for (int i = 0; i < merged.getNumGeometries(); i++) {
            if (merged.getGeometryN(i) instanceof Polygon polygon && !polygon.isEmpty()) {
                pieces.add(polygon);
            }
        }
        return pieces;
    }

    private static void flatten(Geometry geometry, List<Geometry> parts) {
        if (geometry instanceof org.locationtech.jts.geom.GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                flatten(geometry.getGeometryN(i), parts);
            }
        } else if (!geometry.isEmpty()) {
            parts.add(geometry);
        }
    }

    /** Each drill hole (and slot) of an Excellon as a round (or capsule) shape. */
    private static List<Geometry> holes(ExcellonImage excellon) {
        List<Geometry> holes = new ArrayList<>();
        for (ExcellonImage.Drill drill : excellon.drills()) {
            Double diameter = excellon.toolDiameters().get(drill.toolId());
            if (diameter != null) {
                holes.add(FACTORY.createPoint(new Coordinate(drill.x(), drill.y())).buffer(diameter / 2, 16));
            }
        }
        for (ExcellonImage.Slot slot : excellon.slots()) {
            Double diameter = excellon.toolDiameters().get(slot.toolId());
            if (diameter != null) {
                holes.add(FACTORY.createLineString(new Coordinate[] {new Coordinate(slot.x1(), slot.y1()),
                        new Coordinate(slot.x2(), slot.y2())}).buffer(diameter / 2, 16));
            }
        }
        return holes;
    }

    private static double round(double value) {
        double scale = Math.pow(10, DECIMALS);
        return Math.round(value * scale) / scale;
    }

    private static RuleResult error(Rule rule, String title, String message) {
        return new RuleResult(rule, title, List.of(), List.of(), List.of(), message, null);
    }

    private static Named<GerberImage> nullToEmpty(Named<GerberImage> named) {
        return named == null ? new Named<>("", null) : named;
    }

    private static Named<ExcellonImage> nullToEmptyDrill(Named<ExcellonImage> named) {
        return named == null ? new Named<>("", null) : named;
    }
}
