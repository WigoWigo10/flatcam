package org.flatcam.cam.cutout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.CncJobResult;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineString;

class CutoutGeneratorTest {

    @Test
    void internalCutsPreservePanelOffsetsAndNeverReceiveBridgesInMmOrIn() {
        GeometryFactory factory = new GeometryFactory();
        for (double unit : new double[]{1, 1 / 25.4}) {
            Geometry outer = factory.toGeometry(new Envelope(10*unit, 30*unit, 20*unit, 36*unit));
            Geometry hole = factory.toGeometry(new Envelope(17*unit, 23*unit, 25*unit, 31*unit));
            Geometry board = outer.difference(hole);
            Geometry panel = factory.buildGeometry(java.util.List.of(board,
                    org.locationtech.jts.geom.util.AffineTransformation.translationInstance(25*unit, 0).transform(board)));
            var params = new CutoutParameters(.8*unit, .2*unit, false, CutoutKind.PANEL,
                    CutoutShape.FREEFORM, 2*unit, GapPattern.FOUR, true);
            Geometry expectedHole = hole.buffer(-.6*unit, 32).getBoundary();
            for (boolean manual : new boolean[]{false, true}) {
                var masks = manual ? java.util.List.of(factory.toGeometry(new Envelope(19*unit, 21*unit, 15*unit, 40*unit)))
                        : java.util.List.<Geometry>of();
                var result = CutoutGenerator.generate("MM", panel, params, masks, CancellationToken.none());
                int closed = 0;
                for (int i=0; i<result.geometry().getNumGeometries(); i++) {
                    var line = (LineString) result.geometry().getGeometryN(i);
                    if (line.isClosed() && (hole.covers(line)
                            || org.locationtech.jts.geom.util.AffineTransformation.translationInstance(25*unit, 0).transform(hole).covers(line))) {
                        closed++;
                        double offset = line.getEnvelopeInternal().getMinX() > 30*unit ? 25*unit : 0;
                        Geometry expected = org.locationtech.jts.geom.util.AffineTransformation.translationInstance(offset, 0).transform(expectedHole);
                        assertEquals(expected.getLength(), line.getLength(), 1e-9*unit);
                        assertTrue(org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance.distance(line, expected) < 1e-9*unit);
                        assertTrue(result.gapGeometry().intersection(line).isEmpty(), "Thin is external only");
                    }
                }
                assertEquals(2, closed, "One uninterrupted internal profile per board");
                var bites = CutoutGenerator.generateMouseBites("MM", panel, params, .4*unit, .2*unit,
                        masks, CancellationToken.none());
                assertTrue(bites.totalDrills() > 0);
                assertTrue(hole.intersection(bites.solidGeometry()).isEmpty());
            }
        }
    }

    @Test
    void internalCutsAreOptInAndRefuseIncompatibleModesOrUnmachinableHoles() {
        var f = new GeometryFactory();
        Geometry board = f.toGeometry(new Envelope(0,20,0,16))
                .difference(f.toGeometry(new Envelope(9,11,7,9)));
        var legacy = new CutoutParameters(1,0,false,CutoutKind.SINGLE,CutoutShape.FREEFORM,0,GapPattern.NONE);
        assertFalse(legacy.includeInternalCuts());
        assertEquals(1, CutoutGenerator.generate("MM",board,legacy).partCount());
        for (double diameter : new double[]{2, 3}) {
            var params = new CutoutParameters(diameter,0,false,CutoutKind.SINGLE,CutoutShape.FREEFORM,0,GapPattern.NONE,true);
            assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generate("MM",board,params));
        }
        assertThrows(IllegalArgumentException.class, () -> new CutoutParameters(1,0,true,CutoutKind.SINGLE,CutoutShape.FREEFORM,0,GapPattern.NONE,true));
        assertThrows(IllegalArgumentException.class, () -> new CutoutParameters(1,0,false,CutoutKind.SINGLE,CutoutShape.RECTANGULAR,0,GapPattern.NONE,true));
        assertThrows(IllegalArgumentException.class, () -> new CutoutParameters(1,-.1,false,CutoutKind.SINGLE,CutoutShape.FREEFORM,0,GapPattern.NONE,true));
        var params = new CutoutParameters(1,0,false,CutoutKind.SINGLE,CutoutShape.FREEFORM,0,GapPattern.NONE,true);
        assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generate("MM",board.getBoundary(),params));
        Geometry second = org.locationtech.jts.geom.util.AffineTransformation.translationInstance(25,0).transform(board);
        assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generate("MM",f.buildGeometry(java.util.List.of(board,second)),params));
        Geometry narrowHole = f.toGeometry(new Envelope(3,7,5,10))
                .union(f.toGeometry(new Envelope(10,14,5,10)))
                .union(f.toGeometry(new Envelope(6,11,7,7.5)));
        Geometry narrowBoard = f.toGeometry(new Envelope(0,20,0,16)).difference(narrowHole);
        assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generate("MM",narrowBoard,params),
                "A narrow neck splits the compensated hole: do not silently emit two incomplete profiles");
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> CutoutGenerator.generate("MM",board,params, () -> true));
    }

    @Test
    void rectangularBridgesAndThinSegmentsFollowPythonSourceCenterPlusMargin() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = factory.toGeometry(new Envelope(0, 20, 0, 10));
        CutoutParameters params = new CutoutParameters(1, 1, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 2, GapPattern.FOUR);
        CutoutResult result = CutoutGenerator.generate("MM", source, params);
        assertEquals(4, result.partCount());
        // Original bbox center (10,5) + margin (1,1), NOT buffered bbox center.
        Geometry bands = factory.toGeometry(new Envelope(-3, 23, 4.5, 7.5))
                .union(factory.toGeometry(new Envelope(9.5, 12.5, -3, 13)));
        Geometry outline = source.buffer(1.5, 32).getBoundary();
        assertEquals(0, result.geometry().symDifference(outline.difference(bands)).getLength(), 1e-8);
        assertEquals(0, result.gapGeometry().symDifference(outline.intersection(bands)).getLength(), 1e-8,
                "Thin must use exactly the portions removed from the main cut path");
    }

    @Test
    void quarterGapSpacingIncludesMarginButNotTheCutterRadius() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = factory.toGeometry(new Envelope(0, 20, 0, 10));
        for (GapPattern pattern : java.util.List.of(GapPattern.TWO_LR, GapPattern.TWO_TB, GapPattern.EIGHT)) {
            CutoutParameters params = new CutoutParameters(1, 1, false,
                    CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 1, pattern);
            CutoutResult result = CutoutGenerator.generate("MM", source, params);
            Geometry masks = factory.createGeometryCollection();
            // y center = 6; quarter spacing = (10 + 2) / 4 = 3.
            if (pattern != GapPattern.TWO_TB) {
                masks = masks.union(factory.toGeometry(new Envelope(-4, 24, 2, 4)))
                        .union(factory.toGeometry(new Envelope(-4, 24, 8, 10)));
            }
            // x center = 11; quarter spacing = (20 + 2) / 4 = 5.5.
            if (pattern != GapPattern.TWO_LR) {
                masks = masks.union(factory.toGeometry(new Envelope(4.5, 6.5, -4, 14)))
                        .union(factory.toGeometry(new Envelope(15.5, 17.5, -4, 14)));
            }
            assertEquals(pattern == GapPattern.EIGHT ? 8 : 4, result.partCount());
            assertEquals(0, result.geometry().symDifference(source.buffer(1.5, 32)
                    .getBoundary().difference(masks)).getLength(), 1e-8, pattern.name());
        }
    }

    @Test
    void largeMarginExtendsGapBandsAcrossActualOutlineInsteadOfDroppingBridges() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = factory.toGeometry(new Envelope(0, 20, 0, 10));
        CutoutParameters params = new CutoutParameters(1, 5, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 2, GapPattern.FOUR);
        CutoutResult result = CutoutGenerator.generate("MM", source, params);
        assertEquals(4, result.partCount());
        Geometry masks = factory.toGeometry(new Envelope(-8, 28, 8.5, 11.5))
                .union(factory.toGeometry(new Envelope(13.5, 16.5, -8, 18)));
        Geometry expected = source.buffer(5.5, 32).getBoundary().intersection(masks);
        assertEquals(expected.getLength(), result.gapGeometry().getLength(), 1e-8);
        // Curved segments acquire sub-ULP coordinate differences from consecutive overlays.
        assertTrue(expected.buffer(1e-8).covers(result.gapGeometry()));
        assertTrue(result.gapGeometry().buffer(1e-8).covers(expected));
        for (int i = 0; i < result.partCount(); i++) {
            assertFalse(((LineString) result.geometry().getGeometryN(i)).isClosed());
        }
    }

    @Test
    void mouseBitesShareRectangularBridgePlacementWithMainAndThinPaths() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = factory.toGeometry(new Envelope(0, 20, 0, 10));
        // Large margin also exercises the formerly too-short legacy transverse span.
        for (double margin : new double[]{1, 5}) {
            CutoutParameters params = new CutoutParameters(1, margin, false,
                    CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 2, GapPattern.FOUR);
            ExcellonImage bites = CutoutGenerator.generateMouseBites("MM", source, params,
                    0.4, 0.2, CancellationToken.none());
            double cx = 10 + margin, cy = 5 + margin;
            boolean left = false, right = false, top = false, bottom = false;
            for (ExcellonImage.Drill drill : bites.drills()) {
                if (drill.x() < 0) {
                    left = true;
                    assertTrue(Math.abs(drill.y() - cy) <= 1 + 1e-8);
                } else if (drill.x() > 20) {
                    right = true;
                    assertTrue(Math.abs(drill.y() - cy) <= 1 + 1e-8);
                } else {
                    top |= Math.abs(drill.y() - 10 - margin - 0.2) < 1e-8;
                    bottom |= Math.abs(drill.y() + margin + 0.2) < 1e-8;
                    assertTrue(Math.abs(drill.x() - cx) <= 1 + 1e-8);
                }
            }
            assertTrue(left && right && top && bottom, "Every bridge must receive holes");
        }
    }

    @Test
    void rejectsAutomaticPatternsThatLoseBridgesOrRemoveTheEntireCut() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = square(factory, 0, 0, 10);
        for (CutoutParameters params : java.util.List.of(
                new CutoutParameters(1, 1, false, CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 100, GapPattern.FOUR),
                new CutoutParameters(1, 20, false, CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 2, GapPattern.EIGHT))) {
            assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generate("MM", source, params));
            assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generateMouseBites("MM",
                    source, params, 0.4, 0.2, CancellationToken.none()));
        }
    }

    @Test
    void rejectsNonfiniteCutoutParametersAndMissingModes() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new CutoutParameters(invalid, 0, false,
                    CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 1, GapPattern.FOUR));
            assertThrows(IllegalArgumentException.class, () -> new CutoutParameters(1, invalid, false,
                    CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 1, GapPattern.FOUR));
            assertThrows(IllegalArgumentException.class, () -> new CutoutParameters(1, 0, false,
                    CutoutKind.SINGLE, CutoutShape.RECTANGULAR, invalid, GapPattern.FOUR));
        }
        assertThrows(NullPointerException.class, () -> new CutoutParameters(1, 0, false,
                null, CutoutShape.RECTANGULAR, 1, GapPattern.FOUR));
        assertThrows(NullPointerException.class, () -> new CutoutParameters(1, 0, false,
                CutoutKind.SINGLE, null, 1, GapPattern.FOUR));
        assertThrows(NullPointerException.class, () -> new CutoutParameters(1, 0, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 1, null));
    }

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
        assertTrue(result.gapGeometry().isEmpty());
        assertEquals(1, result.partCount(), "Single kind, no gaps: one closed outline");
        assertTrue(result.geometry().getGeometryN(0) instanceof LineString path && path.isClosed(),
                "with no gaps the outline ring must still be closed");
    }

    @Test
    void cutoutGeometryCanBeReviewedThenConvertedToCncJob() throws Exception {
        GerberImage gerber = simple1();
        CutoutResult cutout = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE,
                        CutoutShape.FREEFORM, 0.05, GapPattern.FOUR));
        ToolGeometry tool = new ToolGeometry(0.02, cutout.geometry());

        assertFalse(cutout.isEmpty());
        assertFalse(cutout.gapGeometry().isEmpty());
        assertTrue(cutout.gapGeometry().getLength() > 0);
        assertTrue(cutout.geometry().intersection(cutout.gapGeometry()).getLength() < 1e-9);
        CncJobResult job = GCodeGenerator.generateGeometryCncJob(gerber.units(),
                java.util.List.of(tool), new GeometryGCodeParameters(0.1, 0.004,
                        true, 0.002, 12, 10000, false));
        assertFalse(job.gcode().isBlank());
        assertFalse(job.cutGeometry().isEmpty());
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

    @Test
    void mouseBitesProducesDrillableExcellonBesideCutoutGeometry() throws Exception {
        GerberImage gerber = simple1();
        CutoutParameters params = new CutoutParameters(0.02, 0.02, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 0.05, GapPattern.FOUR);
        CutoutResult cutout = CutoutGenerator.generate(gerber.units(), gerber.solidGeometry(), params);
        ExcellonImage bites = CutoutGenerator.generateMouseBites(gerber.units(),
                gerber.solidGeometry(), params, 0.006, 0.004, CancellationToken.none());

        assertFalse(cutout.isEmpty());
        assertTrue(bites.totalDrills() >= 4);
        assertEquals(0.006, bites.toolDiameters().get(1), 1e-12);
        assertFalse(bites.solidGeometry().isEmpty());
        String gcode = GCodeGenerator.generateDrillGCode(bites,
                new org.flatcam.cam.gcode.DrillGCodeParameters(0.1, 0.02, 12, 0, false));
        assertTrue(gcode.contains("G1 Z-0.0200"));
    }

    @Test
    void mouseBitesRejectsMissingGaps() throws Exception {
        GerberImage gerber = simple1();
        CutoutParameters params = new CutoutParameters(0.02, 0.02, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 0.05, GapPattern.NONE);
        assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generateMouseBites(
                gerber.units(), gerber.solidGeometry(), params, 0.006, 0.004, CancellationToken.none()));
    }

    @Test
    void mouseBitesWithNegativeMarginSubtractsTheDrillRadiusTooLikePython() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry tenByTen = square(geometryFactory, 0, 0, 10);
        double holeDiameter = 0.6;
        double margin = -1.0;
        CutoutParameters params = new CutoutParameters(1.0, margin, false, CutoutKind.SINGLE,
                CutoutShape.FREEFORM, 0.5, GapPattern.LR);

        ExcellonImage bites = CutoutGenerator.generateMouseBites("MM", tenByTen, params,
                holeDiameter, 0.2, CancellationToken.none());

        // LR places its bridges on the left/right edges of the eroded outline, so the bites
        // drilled there sit right on its min-X: the 10-wide square eroded inward from x=0 by
        // Python's margin-radius (1.3), not the "always add" bug's margin+radius (0.7) - a 0.6
        // difference, easily distinguished at 0.05 tolerance.
        double expectedMinX = -(margin - holeDiameter / 2.0);
        double actualMinX = bites.drills().stream().mapToDouble(ExcellonImage.Drill::x).min().orElseThrow();
        assertEquals(expectedMinX, actualMinX, 0.05);
    }

    @Test
    void manualMouseBitesUseOnlyDrawnAreaAndWorkWithoutAutomaticPattern() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = square(factory, 0, 0, 10);
        CutoutParameters params = new CutoutParameters(2, 0, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 0, GapPattern.NONE);
        Geometry area = factory.toGeometry(new Envelope(4, 6, -2, 2));
        ExcellonImage bites = CutoutGenerator.generateMouseBites("MM", source, params,
                0.8, 0.4, java.util.List.of(area), CancellationToken.none());
        assertTrue(bites.totalDrills() > 0);
        for (ExcellonImage.Drill drill : bites.drills()) {
            assertTrue(area.covers(factory.createPoint(
                    new org.locationtech.jts.geom.Coordinate(drill.x(), drill.y()))));
        }
        assertThrows(IllegalArgumentException.class, () -> CutoutGenerator.generateMouseBites(
                "MM", source, params, 0.8, 0.4,
                java.util.List.of(factory.toGeometry(new Envelope(20, 21, 20, 21))),
                CancellationToken.none()));
    }

    @Test
    void drawnManualGapReplacesAutomaticPatternAndRetainsThinSegment() {
        GeometryFactory factory = new GeometryFactory();
        Geometry source = square(factory, 0, 0, 10);
        CutoutParameters params = new CutoutParameters(2, 0, false,
                CutoutKind.SINGLE, CutoutShape.RECTANGULAR, 1, GapPattern.FOUR);
        Geometry manualArea = factory.toGeometry(new Envelope(4, 6, -2, 2));

        CutoutResult automatic = CutoutGenerator.generate("MM", source, params);
        CutoutResult manual = CutoutGenerator.generate("MM", source, params,
                java.util.List.of(manualArea), CancellationToken.none());

        assertEquals(4, automatic.partCount());
        assertEquals(1, manual.partCount());
        assertTrue(manual.gapGeometry().getLength() > 0);
        assertEquals(0, manual.geometry().intersection(manualArea).getLength(), 1e-9);
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
    void negativeMarginSubtractsTheToolRadiusLikeCutoutHandlerDoes() {
        // appTools/ToolCutOut.py's cutout_handler: margin>=0 buffers by margin+radius (grow
        // outward, same sign as the margin itself), but margin<0 buffers by margin-radius
        // (grow the erosion, not shrink it) - a plain "+radius" for every sign gets this wrong
        // by a full tool diameter once margin goes negative.
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry tenByTen = square(geometryFactory, 0, 0, 10);
        double toolDiameter = 1.0;
        double margin = -2.0;

        CutoutResult result = CutoutGenerator.generate("MM", tenByTen,
                new CutoutParameters(toolDiameter, margin, false, CutoutKind.SINGLE,
                        CutoutShape.FREEFORM, 0.0, GapPattern.NONE));

        double expectedOffset = margin - toolDiameter / 2.0; // Python: -2.5, eroding the 10x10 square to 5x5
        double[] bounds = result.bounds();
        assertEquals(-expectedOffset, bounds[0], 1e-9);
        assertEquals(-expectedOffset, bounds[1], 1e-9);
        assertEquals(10 + expectedOffset, bounds[2], 1e-9);
        assertEquals(10 + expectedOffset, bounds[3], 1e-9);
    }

    @Test
    void negativeMarginSubtractsTheToolRadiusInInchesToo() {
        // CutoutGenerator.signedOffset takes margin/toolDiameter as plain numbers, with no
        // internal MM<->IN conversion (Python's cutout_handler works the same way: callers
        // pass values already in the object's own units). Scaling every input from the MM
        // test above by 1/25.4 and asserting the same relative geometry confirms that holds -
        // the units string itself is carried through as metadata only.
        GeometryFactory geometryFactory = new GeometryFactory();
        double side = 10.0 / 25.4;
        Geometry tenByTen = square(geometryFactory, 0, 0, side);
        double toolDiameter = 1.0 / 25.4;
        double margin = -2.0 / 25.4;

        CutoutResult result = CutoutGenerator.generate("IN", tenByTen,
                new CutoutParameters(toolDiameter, margin, false, CutoutKind.SINGLE,
                        CutoutShape.FREEFORM, 0.0, GapPattern.NONE));

        double expectedOffset = margin - toolDiameter / 2.0;
        double[] bounds = result.bounds();
        assertEquals(-expectedOffset, bounds[0], 1e-9);
        assertEquals(-expectedOffset, bounds[1], 1e-9);
        assertEquals(side + expectedOffset, bounds[2], 1e-9);
        assertEquals(side + expectedOffset, bounds[3], 1e-9);
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

    @Test
    void stopsAtACooperativeCancellationCheckpoint() throws Exception {
        GerberImage gerber = simple1();
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() >= 4;

        assertThrows(CancellationException.class, () -> CutoutGenerator.generate(
                gerber.units(), gerber.solidGeometry(),
                new CutoutParameters(0.02, 0.02, false, CutoutKind.SINGLE,
                        CutoutShape.FREEFORM, 0.05, GapPattern.FOUR),
                cancellation));
        assertTrue(checks.get() >= 4);
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
