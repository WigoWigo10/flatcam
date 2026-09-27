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
            Geometry withGaps = manualGapAreas.isEmpty()
                    ? applyGaps(outline, params, geometryFactory, cancellationToken)
                    : applyManualGaps(outline, manualGapAreas, geometryFactory, cancellationToken);
            if (!manualGapAreas.isEmpty()
                    || params.gapPattern() != GapPattern.NONE && params.gapSize() > 0) {
                Geometry inGaps = outline.difference(withGaps);
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
            double offset = params.margin() + holeDiameter / 2.0;
            Geometry shape = params.shape() == CutoutShape.RECTANGULAR
                    ? boxFromEnvelope(part.getEnvelopeInternal(), factory) : part;
            Geometry outline = exteriorRings(shape.buffer(offset, QUADRANT_SEGMENTS));
            List<Geometry> bands = manualGapAreas.isEmpty()
                    ? buildGapBands(outline.getEnvelopeInternal(), params.gapPattern(),
                            params.gapSize() / 2.0, factory)
                    : manualGapAreas;
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

    /** Buffers the part's real outline outward by margin+radius, then takes just the exterior ring as the cut path. */
    private static Geometry freeformOutline(Geometry part, CutoutParameters params) {
        double offset = params.margin() + params.toolDiameter() / 2.0;
        Geometry buffered = part.buffer(offset, QUADRANT_SEGMENTS);
        return exteriorRings(buffered);
    }

    /** Cuts the part's bounding box instead of its real shape - CutoutParameters already rejects a negative margin here. */
    private static Geometry rectangularOutline(Geometry part, CutoutParameters params, GeometryFactory geometryFactory) {
        Geometry box = boxFromEnvelope(part.getEnvelopeInternal(), geometryFactory);
        double offset = params.margin() + params.toolDiameter() / 2.0;
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
    private static Geometry applyGaps(Geometry outline, CutoutParameters params, GeometryFactory geometryFactory,
                                      CancellationToken cancellationToken) {
        if (params.gapPattern() == GapPattern.NONE || params.gapSize() <= 0) {
            return outline;
        }
        double halfGap = params.gapSize() / 2.0 + params.toolDiameter() / 2.0;
        Envelope envelope = outline.getEnvelopeInternal();
        Geometry result = outline;
        for (Geometry band : buildGapBands(envelope, params.gapPattern(), halfGap, geometryFactory)) {
            cancellationToken.throwIfCancellationRequested();
            result = result.difference(band);
        }
        cancellationToken.throwIfCancellationRequested();
        return lineMerge(result, geometryFactory);
    }

    /** See {@link GapPattern}'s class doc for why LR/TB are one full-span band each, not two. */
    private static List<Geometry> buildGapBands(Envelope envelope, GapPattern pattern, double halfGap, GeometryFactory geometryFactory) {
        double centerX = (envelope.getMinX() + envelope.getMaxX()) / 2.0;
        double centerY = (envelope.getMinY() + envelope.getMaxY()) / 2.0;
        double quarterWidth = envelope.getWidth() / 4.0;
        double quarterHeight = envelope.getHeight() / 4.0;

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
