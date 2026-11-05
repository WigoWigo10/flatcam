package org.flatcam.cam.cutout;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.index.quadtree.Quadtree;

/** Closed internal profiles only. Does not generate the board perimeter or remove pocket material. */
public final class InternalCutoutGenerator {
    public enum Problem { INVALID_BOARD, EMPTY_SELECTION, INVALID_OPENING, OUTSIDE_BOARD,
        OVERLAPPING_OPENINGS, INTERSECTS_EXISTING_HOLE, TOOL_DOES_NOT_FIT, BAD_PARAMETERS }

    public static final class InvalidOpening extends IllegalArgumentException {
        private final Problem problem;
        private final int opening;
        InvalidOpening(Problem problem, int opening) {
            super(problem + (opening > 0 ? ": " + opening : ""));
            this.problem = problem; this.opening = opening;
        }
        public Problem problem() { return problem; }
        public int opening() { return opening; }
    }

    private InternalCutoutGenerator() { }

    public static List<Geometry> openings(Geometry board, CancellationToken cancellation) {
        var parts = boardParts(board, cancellation);
        List<Geometry> openings = new ArrayList<>();
        for (Polygon part : parts) for (int i = 0; i < part.getNumInteriorRing(); i++) {
            cancellation.throwIfCancellationRequested();
            openings.add(part.getFactory().createPolygon(part.getInteriorRingN(i).getCoordinates()));
        }
        return List.copyOf(openings);
    }

    public static CutoutResult generate(String units, Geometry board, List<Geometry> selected,
                                         double diameter, double margin, CancellationToken cancellation) {
        if (!Double.isFinite(diameter) || diameter <= 0 || !Double.isFinite(margin) || margin < 0
                || !Double.isFinite(margin + diameter / 2)) throw new InvalidOpening(Problem.BAD_PARAMETERS, 0);
        var parts = boardParts(board, cancellation);
        if (selected == null || selected.isEmpty()) throw new InvalidOpening(Problem.EMPTY_SELECTION, 0);
        record Boundary(Polygon shell, STRtree holes) { }
        List<Boundary> boundaries = new ArrayList<>();
        for (Polygon part : parts) {
            STRtree holes = new STRtree();
            for (int i = 0; i < part.getNumInteriorRing(); i++) {
                cancellation.throwIfCancellationRequested();
                Geometry hole = part.getFactory().createPolygon(part.getInteriorRingN(i).getCoordinates());
                holes.insert(hole.getEnvelopeInternal(), hole);
            }
            holes.build();
            boundaries.add(new Boundary(part.getFactory().createPolygon(part.getExteriorRing().getCoordinates()), holes));
        }
        List<Geometry> paths = new ArrayList<>();
        Quadtree previousOpenings = new Quadtree();
        for (int i = 0; i < selected.size(); i++) {
            cancellation.throwIfCancellationRequested();
            Geometry opening = selected.get(i);
            if (!(opening instanceof Polygon polygon) || polygon.isEmpty() || !polygon.isValid()
                    || polygon.getArea() <= 0 || polygon.getNumInteriorRing() != 0 || !finite(polygon, cancellation))
                throw new InvalidOpening(Problem.INVALID_OPENING, i + 1);
            Boundary containing = null;
            for (Boundary boundary : boundaries) {
                cancellation.throwIfCancellationRequested();
                Polygon shell = boundary.shell();
                if (shell.getEnvelopeInternal().covers(opening.getEnvelopeInternal())
                        && shell.covers(opening) && shell.getBoundary().disjoint(opening)) {
                    if (containing != null) throw new InvalidOpening(Problem.OUTSIDE_BOARD, i + 1);
                    containing = boundary;
                }
            }
            if (containing == null) throw new InvalidOpening(Problem.OUTSIDE_BOARD, i + 1);
            for (Object candidate : containing.holes().query(opening.getEnvelopeInternal())) {
                cancellation.throwIfCancellationRequested();
                Geometry hole = (Geometry)candidate;
                if (hole.intersects(opening) && !hole.equalsTopo(opening))
                    throw new InvalidOpening(Problem.INTERSECTS_EXISTING_HOLE, i + 1);
            }
            for (Object previous : previousOpenings.query(opening.getEnvelopeInternal())) {
                cancellation.throwIfCancellationRequested();
                if (opening.intersects((Geometry)previous))
                    throw new InvalidOpening(Problem.OVERLAPPING_OPENINGS, i + 1);
            }
            previousOpenings.insert(opening.getEnvelopeInternal(), opening);
            Geometry inset = opening.buffer(-(margin + diameter / 2), 16);
            cancellation.throwIfCancellationRequested();
            if (!(inset instanceof Polygon center) || center.isEmpty() || center.getNumInteriorRing() != 0)
                throw new InvalidOpening(Problem.TOOL_DOES_NOT_FIT, i + 1);
            paths.add(center.getExteriorRing());
        }
        cancellation.throwIfCancellationRequested();
        return new CutoutResult(units, board.getFactory().buildGeometry(paths), board.getFactory().createGeometryCollection());
    }

    private static List<Polygon> boardParts(Geometry board, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (board == null || board.isEmpty() || board.getDimension() != 2 || !board.isValid() || !finite(board, cancellation))
            throw new InvalidOpening(Problem.INVALID_BOARD, 0);
        List<Polygon> parts = new ArrayList<>();
        for (int i = 0; i < board.getNumGeometries(); i++) {
            cancellation.throwIfCancellationRequested();
            if (!(board.getGeometryN(i) instanceof Polygon polygon)) throw new InvalidOpening(Problem.INVALID_BOARD, 0);
            parts.add(polygon);
        }
        return parts;
    }

    private static boolean finite(Geometry geometry, CancellationToken cancellation) {
        boolean[] finite = {true};
        geometry.apply(new org.locationtech.jts.geom.CoordinateSequenceFilter() {
            public void filter(org.locationtech.jts.geom.CoordinateSequence coordinates, int index) {
                if (index % 256 == 0) cancellation.throwIfCancellationRequested();
                finite[0] &= Double.isFinite(coordinates.getX(index)) && Double.isFinite(coordinates.getY(index));
            }
            public boolean isDone() { return !finite[0]; }
            public boolean isGeometryChanged() { return false; }
        });
        return finite[0];
    }
}
