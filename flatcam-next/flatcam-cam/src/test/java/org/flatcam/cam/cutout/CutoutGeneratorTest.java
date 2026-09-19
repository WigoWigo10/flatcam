package org.flatcam.cam.cutout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

class CutoutGeneratorTest {

    private static GerberImage simple1() throws Exception {
        return new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
    }

    // simple1.gbr's own bounds are roughly 0.55 x 0.42 inches (see tests/baseline/gerber/simple1.json) -
    // tool/margin/gap sizes here are deliberately small fractions of an inch to match, not the mm-scale
    // values a real board might use, so a gap band doesn't accidentally swallow the whole tiny outline.
    @Test
    void noGapsProducesOneClosedRingPerSinglePart() throws Exception {
        GerberImage gerber = simple1();
        CutoutResult result = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.05, GapPattern.NONE));

        assertTrue(!result.isEmpty());
        assertEquals(1, result.partCount(), "Single kind, no gaps: one closed outline");
        assertTrue(result.geometry().getGeometryN(0) instanceof LineString path && path.isClosed(),
                "with no gaps the outline ring must still be closed");
    }

    @Test
    void gapPatternsInterruptTheRingIntoTheExpectedNumberOfPieces() throws Exception {
        GerberImage gerber = simple1();
        CutoutParameters base = new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 0.05, GapPattern.NONE);

        assertEquals(1, piecesFor(gerber, base, GapPattern.NONE));
        assertEquals(2, piecesFor(gerber, base, GapPattern.LR), "one band crossing left+right = 2 open pieces");
        assertEquals(2, piecesFor(gerber, base, GapPattern.TB));
        assertEquals(4, piecesFor(gerber, base, GapPattern.FOUR), "LR+TB bands = 4 pieces");
        assertEquals(4, piecesFor(gerber, base, GapPattern.TWO_LR));
        assertEquals(4, piecesFor(gerber, base, GapPattern.TWO_TB));
        assertEquals(8, piecesFor(gerber, base, GapPattern.EIGHT));
    }

    private static int piecesFor(GerberImage gerber, CutoutParameters base, GapPattern pattern) {
        CutoutParameters params = new CutoutParameters(base.toolDiameter(), base.margin(), base.convexShape(),
                base.kind(), base.shape(), base.gapSize(), pattern);
        CutoutResult result = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(), params);
        return result.partCount();
    }

    @Test
    void rectangularShapeProducesAnAxisAlignedBox() throws Exception {
        GerberImage gerber = simple1();
        CutoutResult result = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.0, false, CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 0.0, GapPattern.NONE));

        double[] cutoutBounds = result.bounds();
        double[] gerberBounds = gerber.bounds();
        // A margin of 0 plus tool radius 0.01 should sit just outside the Gerber's own bounding box.
        assertTrue(cutoutBounds[0] < gerberBounds[0] && cutoutBounds[1] < gerberBounds[1]
                        && cutoutBounds[2] > gerberBounds[2] && cutoutBounds[3] > gerberBounds[3],
                "the rectangular cutout must fully enclose the source's bounding box");
    }

    @Test
    void panelKindOutlinesEachDisjointPartSeparately() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry twoSquares = geometryFactory.createGeometryCollection(new Geometry[]{
                square(geometryFactory, 0, 0, 10),
                square(geometryFactory, 100, 100, 10)
        }).union(); // union() normalizes two disjoint polygons into one clean MultiPolygon

        CutoutResult single = CutoutGenerator.generate("MM", twoSquares,
                new CutoutParameters(1.0, 1.0, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));
        CutoutResult panel = CutoutGenerator.generate("MM", twoSquares,
                new CutoutParameters(1.0, 1.0, false, CutoutKind.PANEL, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));

        assertEquals(1, single.partCount(), "Single kind boxes disjoint parts into one shared outline");
        assertEquals(2, panel.partCount(), "Panel kind outlines each disjoint square on its own");
    }

    @Test
    void convexShapeUsesTheConvexHullInstead() {
        GeometryFactory geometryFactory = new GeometryFactory();
        // An L-shape: its own outline perimeter is longer than its convex hull's.
        Geometry lShape = geometryFactory.createPolygon(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(0, 0), new org.locationtech.jts.geom.Coordinate(10, 0),
                new org.locationtech.jts.geom.Coordinate(10, 5), new org.locationtech.jts.geom.Coordinate(5, 5),
                new org.locationtech.jts.geom.Coordinate(5, 10), new org.locationtech.jts.geom.Coordinate(0, 10),
                new org.locationtech.jts.geom.Coordinate(0, 0)
        });

        CutoutResult direct = CutoutGenerator.generate("MM", lShape,
                new CutoutParameters(0.1, 0.0, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));
        CutoutResult convex = CutoutGenerator.generate("MM", lShape,
                new CutoutParameters(0.1, 0.0, true, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));

        assertTrue(convex.totalLength() < direct.totalLength(), "the convex hull's perimeter must be shorter than the L-shape's own");
    }

    @Test
    void rejectsNegativeMarginForRectangularShape() {
        assertThrows(IllegalArgumentException.class,
                () -> new CutoutParameters(1.0, -0.5, false, CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 0.0, GapPattern.NONE));
    }

    @Test
    void rejectsNonPositiveToolDiameter() {
        assertThrows(IllegalArgumentException.class,
                () -> new CutoutParameters(0, 0.5, false, CutoutKind.SINGLE, CutoutShape.FREEFORM, 0.0, GapPattern.NONE));
    }

    private static Geometry square(GeometryFactory geometryFactory, double x, double y, double size) {
        return geometryFactory.createPolygon(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(x, y), new org.locationtech.jts.geom.Coordinate(x + size, y),
                new org.locationtech.jts.geom.Coordinate(x + size, y + size), new org.locationtech.jts.geom.Coordinate(x, y + size),
                new org.locationtech.jts.geom.Coordinate(x, y)
        });
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("tests/gerber_files"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (no ancestor has tests/gerber_files)");
    }
}
