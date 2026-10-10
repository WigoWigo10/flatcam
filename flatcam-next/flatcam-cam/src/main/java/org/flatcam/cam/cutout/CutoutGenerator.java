package org.flatcam.cam.cutout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.linemerge.LineMerger;

/**
 * Ports appTools/ToolCutOut.py's automatic (non-manual) cutout generation:
 * buffers a Gerber's copper outward into a cut path, then subtracts small
 * rectangles at bridge-gap positions so the finished board can be snapped
 * free of the surrounding stock. The source may be Gerber copper or a filled
 * Geometry. Bridge paths, Thin bridge segments and
 * automatic or manually located M-Bites drill points are available. Thin segments become a
 * separate Geometry so the user can choose their shallower Cut Z at the
 * Geometry-to-CNC step. Manual masks can cut one or more arbitrary gap areas;
 * the Python cursor-oriented gap gesture is not reproduced yet.
 */
public final class CutoutGenerator {

    private static final int QUADRANT_SEGMENTS = 32;

    private CutoutGenerator() {
    }

    public static CutoutResult generate(String units, Geometry copperGeometry, CutoutParameters params) {
        return generate(units, copperGeometry, params, CancellationToken.none());
    }

    public static CutoutResult generate(String units, Geometry copperGeometry, CutoutParameters params,
                                        CancellationToken cancellationToken) {
        return generate(units, copperGeometry, params, List.of(), cancellationToken);
    }

    /** Manual masks directly remove the enclosed portions of the cut path. */
    public static CutoutResult generate(String units, Geometry copperGeometry, CutoutParameters params,
                                        List<Geometry> manualGapAreas, CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(manualGapAreas, "manualGapAreas");
        for (Geometry area : manualGapAreas) {
            if (area == null || area.isEmpty() || area.getDimension() != 2 || !area.isValid()) {
                throw new IllegalArgumentException("Manual gap area must be a valid, filled shape");
            }
        }
        cancellationToken.throwIfCancellationRequested();
        GeometryFactory geometryFactory = copperGeometry.getFactory();
        if (params.includeInternalCuts() && (copperGeometry.isEmpty()
                || copperGeometry.getDimension() != 2 || !copperGeometry.isValid())) {
            throw new IllegalArgumentException("Recortes internos exigem uma area de placa preenchida e valida.");
        }
        if (params.includeInternalCuts() && params.kind() == CutoutKind.SINGLE
                && copperGeometry.getNumGeometries() > 1) {
            throw new IllegalArgumentException("Use Panel para recortar varias placas com recortes internos.");
        }
        Geometry source = params.convexShape() ? copperGeometry.convexHull() : copperGeometry;
        cancellationToken.throwIfCancellationRequested();

        List<Geometry> parts = params.kind() == CutoutKind.PANEL ? explode(source) : List.of(unionOrBox(source, geometryFactory));

        // Flattened, not one list entry per part: a part with gaps applied is itself a
        // multi-piece result (the whole point of a gap is to split one ring into several
        // open arcs), and GeometryFactory.buildGeometry() below needs a flat list of
        // individual LineStrings to produce a clean MultiLineString - handing it a list
        // containing an already-multi-part Geometry as a single element left the result
        // nested one level too deep (a 1-element GeometryCollection wrapping the real
        // MultiLineString) instead of the flat MultiLineString itself.
        List<Geometry> paths = new ArrayList<>();
        List<Geometry> gapPaths = new ArrayList<>();
        for (Geometry part : parts) {
            cancellationToken.throwIfCancellationRequested();
            Geometry outline = params.shape() == CutoutShape.RECTANGULAR
                    ? rectangularOutline(part, params, geometryFactory)
                    : freeformOutline(part, params);
            if (outline == null || outline.isEmpty()) {
                continue;
            }
            cancellationToken.throwIfCancellationRequested();
            List<Geometry> gapAreas = !manualGapAreas.isEmpty() ? manualGapAreas
                    : params.gapPattern() == GapPattern.NONE || params.gapSize() <= 0 ? List.of()
                    : automaticGapBands(outline, part.getEnvelopeInternal(), params,
                            (params.gapSize() + params.toolDiameter()) / 2.0, geometryFactory);
            Geometry withGaps = manualGapAreas.isEmpty()
                    ? applyGaps(outline, gapAreas, params, geometryFactory, cancellationToken)
                    : applyManualGaps(outline, manualGapAreas, geometryFactory, cancellationToken);
            if (!gapAreas.isEmpty()) {
                // Comparing the original with the overlaid result can reintroduce
                // rounded-corner fragments due to noding roundoff. Extract Thin
                // directly from the exact masks used to remove the bridges.
                Geometry inGaps = outline.intersection(geometryFactory.buildGeometry(gapAreas).union());
                for (int i = 0; i < inGaps.getNumGeometries(); i++) {
                    cancellationToken.throwIfCancellationRequested();
                    if (!inGaps.getGeometryN(i).isEmpty()) {
                        gapPaths.add(inGaps.getGeometryN(i));
                    }
                }
            }
            for (int i = 0; i < withGaps.getNumGeometries(); i++) {
                cancellationToken.throwIfCancellationRequested();
                paths.add(withGaps.getGeometryN(i));
            }
            // Work from each ORIGINAL hole, not the outer buffer: small holes may vanish
            // there silently. Internal profiles must never acquire automatic/manual bridges,
            // Thin segments or M-Bites. Fail the whole job if any hole cannot fit the tool.
            if (params.includeInternalCuts()) {
                addInternalCuts(part, params, paths, cancellationToken);
            }
        }

        cancellationToken.throwIfCancellationRequested();
        Geometry combined = paths.isEmpty() ? geometryFactory.createGeometryCollection() : geometryFactory.buildGeometry(paths);
        Geometry gaps = gapPaths.isEmpty() ? geometryFactory.createGeometryCollection()
                : geometryFactory.buildGeometry(gapPaths);
        return new CutoutResult(units, combined, gaps);
    }

    private static Geometry applyManualGaps(Geometry outline, List<Geometry> areas,
                                             GeometryFactory factory, CancellationToken cancellationToken) {
        Geometry result = outline;
        for (Geometry area : areas) {
            cancellationToken.throwIfCancellationRequested();
            result = result.difference(area);
        }
        return lineMerge(result, factory);
    }

    private static void addInternalCuts(Geometry part, CutoutParameters params, List<Geometry> paths,
                                        CancellationToken cancellationToken) {
        if (!(part instanceof Polygon polygon)) {
            throw new IllegalArgumentException("Recortes internos exigem poligonos de placa preenchidos.");
        }
        for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
            cancellationToken.throwIfCancellationRequested();
            Geometry hole = part.getFactory().createPolygon(polygon.getInteriorRingN(index).getCoordinates());
            Geometry centerArea = hole.buffer(-(params.margin() + params.toolDiameter() / 2), QUADRANT_SEGMENTS);
            cancellationToken.throwIfCancellationRequested();
            if (!(centerArea instanceof Polygon center) || center.isEmpty() || center.getNumInteriorRing() != 0) {
                throw new IllegalArgumentException("Recorte interno " + (index + 1)
                        + " nao comporta a fresa/margem ou se divide. Use uma fresa menor ou usine separadamente.");
            }
            paths.add(center.getExteriorRing());
        }
    }

    /** Creates the separate Excellon object used by Python's automatic M-Bites mode. */
    public static ExcellonImage generateMouseBites(String units, Geometry copperGeometry,
                                                   CutoutParameters params, double holeDiameter,
                                                   double holeSpacing, CancellationToken cancellationToken) {
        return generateMouseBites(units, copperGeometry, params, holeDiameter, holeSpacing,
                List.of(), cancellationToken);
    }

    /** Manual gap masks replace the automatic gap pattern for M-Bites too. */
    public static ExcellonImage generateMouseBites(String units, Geometry copperGeometry,
                                                   CutoutParameters params, double holeDiameter,
                                                   double holeSpacing, List<Geometry> manualGapAreas,
                                                   CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(manualGapAreas, "manualGapAreas");
        if (!Double.isFinite(holeDiameter) || holeDiameter <= 0
                || !Double.isFinite(holeSpacing) || holeSpacing < 0) {
            throw new IllegalArgumentException("M-Bites diameter must be positive and spacing nonnegative");
        }
        if (manualGapAreas.isEmpty() && (params.gapPattern() == GapPattern.NONE || params.gapSize() <= 0)) {
            throw new IllegalArgumentException("M-Bites requires at least one nonzero gap");
        }
        for (Geometry area : manualGapAreas) {
            if (area == null || area.isEmpty() || area.getDimension() != 2 || !area.isValid())
                throw new IllegalArgumentException("Manual M-Bites gap must be a valid filled area");
        }
        cancellationToken.throwIfCancellationRequested();
        GeometryFactory factory = copperGeometry.getFactory();
        Geometry source = params.convexShape() ? copperGeometry.convexHull() : copperGeometry;
        List<Geometry> parts = params.kind() == CutoutKind.PANEL
                ? explode(source) : List.of(unionOrBox(source, factory));
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<Geometry> footprints = new ArrayList<>();
        double step = holeDiameter + holeSpacing;
        for (Geometry part : parts) {
            cancellationToken.throwIfCancellationRequested();
            // Python shifts the M-Bites row by half the drill diameter instead of
            // half the cutter diameter, so the holes touch the remaining bridge.
            double offset = signedOffset(params.margin(), holeDiameter / 2.0);
            Geometry shape = params.shape() == CutoutShape.RECTANGULAR
                    ? boxFromEnvelope(part.getEnvelopeInternal(), factory) : part;
            Geometry outline = exteriorRings(shape.buffer(offset, QUADRANT_SEGMENTS));
            List<Geometry> bands = manualGapAreas.isEmpty()
                    ? automaticGapBands(outline, part.getEnvelopeInternal(), params,
                            params.gapSize() / 2.0, factory)
                    : manualGapAreas;
            if (manualGapAreas.isEmpty() && params.shape() == CutoutShape.RECTANGULAR) {
                // M-Bites must target every requested bridge too, even with a large margin.
                Geometry remaining = subtractBands(outline, bands, factory, cancellationToken);
                validateRectangularGaps(remaining, params.gapPattern());
            }
            for (Geometry band : bands) {
                cancellationToken.throwIfCancellationRequested();
                addBiteHoles(outline.intersection(band), holeDiameter, step,
                        drills, footprints, factory, cancellationToken);
            }
        }
        if (!manualGapAreas.isEmpty() && drills.isEmpty())
            throw new IllegalArgumentException("Os gaps manuais nao cruzam a linha de M-Bites.");
        Geometry solid = footprints.isEmpty()
                ? factory.createGeometryCollection() : factory.buildGeometry(footprints);
        return ExcellonImage.of(units, Map.of(1, holeDiameter), drills, List.of(), solid);
    }

    private static void addBiteHoles(Geometry geometry, double diameter, double step,
                                     List<ExcellonImage.Drill> drills, List<Geometry> footprints,
                                     GeometryFactory factory, CancellationToken cancellationToken) {
        cancellationToken.throwIfCancellationRequested();
        if (geometry instanceof LineString line && line.getLength() > 0) {
            LengthIndexedLine indexed = new LengthIndexedLine(line);
            for (int index = 0; index < 100_000; index++) {
                cancellationToken.throwIfCancellationRequested();
                double distance = index * step;
                if (distance >= line.getLength()) {
                    return;
                }
                if (drills.size() >= 100_000) {
                    throw new IllegalArgumentException("M-Bites would exceed 100000 drill holes");
                }
                Coordinate point = indexed.extractPoint(distance);
                drills.add(new ExcellonImage.Drill(1, point.x, point.y));
                footprints.add(factory.createPoint(point).buffer(diameter / 2.0, QUADRANT_SEGMENTS));
            }
            throw new IllegalArgumentException("M-Bites would exceed 100000 drill holes");
        }
        if (geometry.getNumGeometries() == 1 && geometry.getGeometryN(0) == geometry) {
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            addBiteHoles(geometry.getGeometryN(i), diameter, step,
                    drills, footprints, factory, cancellationToken);
        }
    }

    /** A single-mode source's own copper: if it's already one shape, use it as-is; a disjoint Gerber gets boxed instead of outlined part-by-part. */
    private static Geometry unionOrBox(Geometry source, GeometryFactory geometryFactory) {
        if (source instanceof MultiPolygon && source.getNumGeometries() > 1) {
            return boxFromEnvelope(source.getEnvelopeInternal(), geometryFactory);
        }
        return source;
    }

    private static List<Geometry> explode(Geometry source) {
        List<Geometry> parts = new ArrayList<>();
        for (int i = 0; i < source.getNumGeometries(); i++) {
            parts.add(source.getGeometryN(i));
        }
        return parts;
    }

    /** Buffers the part's real outline by margin+/-radius (see {@link #signedOffset}), then takes just the exterior ring. */
    private static Geometry freeformOutline(Geometry part, CutoutParameters params) {
        double offset = signedOffset(params.margin(), params.toolDiameter() / 2.0);
        Geometry buffered = part.buffer(offset, QUADRANT_SEGMENTS);
        return exteriorRings(buffered);
    }

    /**
     * cutout_handler's margin compensation: for margin &gt;= 0 (cutting outside the source,
     * clearance around the board) the radius grows the same outward buffer; for margin &lt; 0
     * (cutting inside the source, trimming the board itself) the radius grows the erosion
     * instead, i.e. it is subtracted, not added. A plain "margin + radius" for every sign
     * would make the cut path a full tool diameter closer to the board than Python's once
     * margin goes negative.
     */
    private static double signedOffset(double margin, double radius) {
        return margin >= 0 ? margin + radius : margin - radius;
    }

    /** Cuts the part's bounding box instead of its real shape - CutoutParameters already rejects a negative margin here. */
    private static Geometry rectangularOutline(Geometry part, CutoutParameters params, GeometryFactory geometryFactory) {
        Geometry box = boxFromEnvelope(part.getEnvelopeInternal(), geometryFactory);
        double offset = signedOffset(params.margin(), params.toolDiameter() / 2.0);
        Geometry buffered = box.buffer(offset, QUADRANT_SEGMENTS);
        return exteriorRings(buffered);
    }

    private static Geometry exteriorRings(Geometry geometry) {
        List<LineString> rings = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            if (geometry.getGeometryN(i) instanceof Polygon polygon) {
                rings.add(polygon.getExteriorRing());
            }
        }
        if (rings.isEmpty()) {
            return geometry.getFactory().createGeometryCollection();
        }
        return geometry.getFactory().buildGeometry(rings);
    }

    /**
     * Subtracts one or two full-span bands from the outline at the positions
     * {@link GapPattern} calls for, then re-merges whatever line pieces
     * remain touching end-to-end (appTools/ToolCutOut.py's own
     * subtract_poly_from_geo() + linemerge()).
     */
    private static Geometry applyGaps(Geometry outline, List<Geometry> gapAreas, CutoutParameters params, GeometryFactory geometryFactory,
                                      CancellationToken cancellationToken) {
        if (params.gapPattern() == GapPattern.NONE || params.gapSize() <= 0) {
            return outline;
        }
        Geometry result = subtractBands(outline, gapAreas, geometryFactory, cancellationToken);
        if (params.shape() == CutoutShape.RECTANGULAR) {
            validateRectangularGaps(result, params.gapPattern());
        }
        return result;
    }

    private static Geometry subtractBands(Geometry outline, List<Geometry> bands, GeometryFactory factory,
                                          CancellationToken cancellationToken) {
        Geometry result = outline;
        for (Geometry band : bands) {
            cancellationToken.throwIfCancellationRequested();
            result = result.difference(band);
        }
        cancellationToken.throwIfCancellationRequested();
        return lineMerge(result, factory);
    }

    private static void validateRectangularGaps(Geometry remaining, GapPattern pattern) {
        int expected = switch (pattern) {
            case NONE -> 1;
            case LR, TB -> 2;
            case FOUR, TWO_LR, TWO_TB -> 4;
            case EIGHT -> 8;
        };
        if (remaining.isEmpty() || remaining.getNumGeometries() != expected) {
            throw new IllegalArgumentException("Nao foi possivel preservar todos os gaps retangulares. "
                    + "Reduza a margem/largura ou use gaps manuais.");
        }
        for (int i = 0; i < remaining.getNumGeometries(); i++) {
            if (remaining.getGeometryN(i) instanceof LineString line && line.isClosed()) {
                throw new IllegalArgumentException("O padrao de gaps deixou um contorno fechado sem bridges.");
            }
        }
    }

    private static List<Geometry> automaticGapBands(Geometry outline, Envelope sourceBounds,
                                                     CutoutParameters params, double halfGap,
                                                     GeometryFactory factory) {
        Envelope span = outline.getEnvelopeInternal();
        // ToolCutOut.cutout_rect_handler uses ORIGINAL bounds for placement:
        // center += margin, quarter spacing = (source dimension + 2 * margin) / 4.
        // Extend only the band's transverse span to the actual buffered outline.
        // The legacy source-bounds span may miss both edges when margin > gap / 2.
        // Freeform keeps its existing placement pending a separate oracle comparison.
        Envelope placement = params.shape() == CutoutShape.RECTANGULAR ? sourceBounds : span;
        double margin = params.shape() == CutoutShape.RECTANGULAR ? params.margin() : 0;
        return buildGapBands(span, placement, margin, params.gapPattern(), halfGap, factory);
    }

    /** See {@link GapPattern}'s class doc for why LR/TB are one full-span band each, not two. */
    private static List<Geometry> buildGapBands(Envelope envelope, Envelope placement, double margin,
                                                GapPattern pattern, double halfGap, GeometryFactory geometryFactory) {
        double centerX = (placement.getMinX() + placement.getMaxX()) / 2.0 + margin;
        double centerY = (placement.getMinY() + placement.getMaxY()) / 2.0 + margin;
        double quarterWidth = (placement.getWidth() + 2 * margin) / 4.0;
        double quarterHeight = (placement.getHeight() + 2 * margin) / 4.0;

        List<Geometry> bands = new ArrayList<>();
        switch (pattern) {
            case LR -> bands.add(horizontalBand(envelope, centerY, halfGap, geometryFactory));
            case TB -> bands.add(verticalBand(envelope, centerX, halfGap, geometryFactory));
            case FOUR -> {
                bands.add(horizontalBand(envelope, centerY, halfGap, geometryFactory));
                bands.add(verticalBand(envelope, centerX, halfGap, geometryFactory));
            }
            case TWO_LR -> {
                bands.add(horizontalBand(envelope, centerY - quarterHeight, halfGap, geometryFactory));
                bands.add(horizontalBand(envelope, centerY + quarterHeight, halfGap, geometryFactory));
            }
            case TWO_TB -> {
                bands.add(verticalBand(envelope, centerX - quarterWidth, halfGap, geometryFactory));
                bands.add(verticalBand(envelope, centerX + quarterWidth, halfGap, geometryFactory));
            }
            case EIGHT -> {
                bands.add(horizontalBand(envelope, centerY - quarterHeight, halfGap, geometryFactory));
                bands.add(horizontalBand(envelope, centerY + quarterHeight, halfGap, geometryFactory));
                bands.add(verticalBand(envelope, centerX - quarterWidth, halfGap, geometryFactory));
                bands.add(verticalBand(envelope, centerX + quarterWidth, halfGap, geometryFactory));
            }
            case NONE -> {
            }
        }
        return bands;
    }

    /** Narrow in Y (the bridge's own width), spanning well past the outline's X extent - crosses a left AND a right edge in one shape. */
    private static Geometry horizontalBand(Envelope envelope, double y, double halfGap, GeometryFactory geometryFactory) {
        return boxFromCoords(envelope.getMinX() - halfGap, y - halfGap, envelope.getMaxX() + halfGap, y + halfGap, geometryFactory);
    }

    /** Narrow in X, spanning well past the outline's Y extent - crosses a top AND a bottom edge in one shape. */
    private static Geometry verticalBand(Envelope envelope, double x, double halfGap, GeometryFactory geometryFactory) {
        return boxFromCoords(x - halfGap, envelope.getMinY() - halfGap, x + halfGap, envelope.getMaxY() + halfGap, geometryFactory);
    }

    private static Geometry boxFromEnvelope(Envelope envelope, GeometryFactory geometryFactory) {
        return boxFromCoords(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY(), geometryFactory);
    }

    private static Geometry boxFromCoords(double minX, double minY, double maxX, double maxY, GeometryFactory geometryFactory) {
        Coordinate[] ring = {
                new Coordinate(minX, minY), new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY), new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };
        return geometryFactory.createPolygon(ring);
    }

    private static Geometry lineMerge(Geometry geometry, GeometryFactory geometryFactory) {
        LineMerger merger = new LineMerger();
        merger.add(geometry);
        Collection<LineString> merged = merger.getMergedLineStrings();
        if (merged.isEmpty()) {
            return geometryFactory.createGeometryCollection();
        }
        return geometryFactory.buildGeometry(new ArrayList<>(merged));
    }
}
