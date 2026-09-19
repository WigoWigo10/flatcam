package org.flatcam.cam.cutout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.linemerge.LineMerger;

/**
 * Ports appTools/ToolCutOut.py's automatic (non-manual) cutout generation:
 * buffers a Gerber's copper outward into a cut path, then subtracts small
 * rectangles at bridge-gap positions so the finished board can be snapped
 * free of the surrounding stock. Only a Gerber source is supported (Python
 * also accepts an existing Geometry object as source, used as-is with no
 * buffering - this port has no Geometry object type at all yet); only the
 * "Bridge" gap type is implemented ("Thin" - a second, shallower toolpath
 * just at the gaps - and "M-Bites" - a row of drill holes instead of a
 * physical gap, exported as a separate Excellon object - are both deferred);
 * and the manual click-to-place gap workflow (a dedicated interactive canvas
 * mode with a rotating preview shape - appTools/ToolCutOut.py's
 * on_manual_gap_click()) is deferred too.
 *
 * <p>Also deliberately NOT built the way Python does: appTools/ToolCutOut.py
 * always produces an intermediate multi-tool "Geometry" object in the
 * project tree, which the user must then separately run "Generate CNCJob"
 * on. This port has no Geometry object type, and building one solely so
 * Cutout could immediately consume it - with no other current beneficiary -
 * would be a large detour for zero user-facing difference, since a cutout
 * always has exactly one (Bridge) or two (Thin, deferred) fixed-purpose
 * tools, never arbitrary per-tool milling parameters the way a real Geometry
 * object supports. This generates the toolpath geometry directly, the same
 * shape MainWindow already uses for Isolation Routing (straight to G-code +
 * a CNC Job in the tree).
 */
public final class CutoutGenerator {

    private static final int QUADRANT_SEGMENTS = 32;

    private CutoutGenerator() {
    }

    public static CutoutResult generate(String units, Geometry copperGeometry, CutoutParameters params) {
        GeometryFactory geometryFactory = copperGeometry.getFactory();
        Geometry source = params.convexShape() ? copperGeometry.convexHull() : copperGeometry;

        List<Geometry> parts = params.kind() == CutoutKind.PANEL ? explode(source) : List.of(unionOrBox(source, geometryFactory));

        // Flattened, not one list entry per part: a part with gaps applied is itself a
        // multi-piece result (the whole point of a gap is to split one ring into several
        // open arcs), and GeometryFactory.buildGeometry() below needs a flat list of
        // individual LineStrings to produce a clean MultiLineString - handing it a list
        // containing an already-multi-part Geometry as a single element left the result
        // nested one level too deep (a 1-element GeometryCollection wrapping the real
        // MultiLineString) instead of the flat MultiLineString itself.
        List<Geometry> paths = new ArrayList<>();
        for (Geometry part : parts) {
            Geometry outline = params.shape() == CutoutShape.RECTANGULAR
                    ? rectangularOutline(part, params, geometryFactory)
                    : freeformOutline(part, params);
            if (outline == null || outline.isEmpty()) {
                continue;
            }
            Geometry withGaps = applyGaps(outline, params, geometryFactory);
            for (int i = 0; i < withGaps.getNumGeometries(); i++) {
                paths.add(withGaps.getGeometryN(i));
            }
        }

        Geometry combined = paths.isEmpty() ? geometryFactory.createGeometryCollection() : geometryFactory.buildGeometry(paths);
        return new CutoutResult(units, combined);
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
    private static Geometry applyGaps(Geometry outline, CutoutParameters params, GeometryFactory geometryFactory) {
        if (params.gapPattern() == GapPattern.NONE || params.gapSize() <= 0) {
            return outline;
        }
        double halfGap = params.gapSize() / 2.0 + params.toolDiameter() / 2.0;
        Envelope envelope = outline.getEnvelopeInternal();
        Geometry result = outline;
        for (Geometry band : buildGapBands(envelope, params.gapPattern(), halfGap, geometryFactory)) {
            result = result.difference(band);
        }
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
