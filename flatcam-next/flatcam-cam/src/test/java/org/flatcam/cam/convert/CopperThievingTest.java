package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.convert.CopperThieving.Fill;
import org.flatcam.cam.convert.CopperThieving.Options;
import org.flatcam.cam.convert.CopperThieving.Plating;
import org.flatcam.cam.convert.CopperThieving.Reference;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;

class CopperThievingTest {

    @TempDir
    Path directory;

    /** Two 2 x 2 mm pads at (0,0) and (20,0): the copper spans x -1..21, y -1..1. */
    private GerberImage board() throws IOException {
        Path file = directory.resolve("board.gbr");
        Files.writeString(file, "%FSLAX24Y24*%\n%MOMM*%\n%ADD10R,2.0X2.0*%\nD10*\nX0Y0D03*\nX200000Y0D03*\nM02*\n");
        return new GerberParser().parse(file);
    }

    private static Options with(Fill fill, double clearance, double margin) {
        Options d = Options.defaults();
        return new Options(clearance, margin, d.minArea(), Reference.ITSELF, d.boxType(), d.circleSteps(), fill,
                d.dotDiameter(), d.dotSpacing(), d.squareSize(), d.squareSpacing(), d.lineSize(), d.lineSpacing());
    }

    private static List<Polygon> thieve(GerberImage image, Options options) {
        return CopperThieving.thieve(image.solidGeometry(), null, false, options, CancellationToken.none(),
                ProgressCallback.none());
    }

    private static double area(List<Polygon> polygons) {
        return polygons.stream().mapToDouble(Polygon::getArea).sum();
    }

    @Test
    void solidThievingIsTheBoxMinusTheClearedCopper() throws Exception {
        GerberImage board = board();
        List<Polygon> thief = thieve(board, with(Fill.SOLID, 0.5, 1.0));
        // Box: x -2..22, y -2..2 (24 x 4 = 96). The pads grown by 0.5 (rounded corners) are removed.
        Geometry cleared2 = board.solidGeometry().buffer(0.5, 16);
        assertEquals(96.0 - cleared2.getArea(), area(thief), 1e-6);
        // No thieving inside the clearance.
        for (Polygon polygon : thief) {
            assertFalse(polygon.intersects(board.solidGeometry()));
        }
    }

    @Test
    void tinyAreasAreDropped() throws Exception {
        GerberImage board = board();
        Options options = new Options(0.5, 1.0, 1000.0, Reference.ITSELF, Options.defaults().boxType(), 64, Fill.SOLID,
                1, 2, 1, 2, 0.25, 2);
        assertTrue(thieve(board, options).isEmpty());
    }

    @Test
    void dotsAndSquaresStayInsideTheFreeArea() throws Exception {
        GerberImage board = board();
        for (Fill fill : List.of(Fill.DOT, Fill.SQUARE)) {
            List<Polygon> cells = thieve(board, with(fill, 0.5, 1.0));
            assertFalse(cells.isEmpty(), fill.name());
            Geometry grown = board.solidGeometry().buffer(0.5, 16);
            for (Polygon cell : cells) {
                assertFalse(cell.intersects(grown), fill.name());
                assertEquals(fill == Fill.DOT ? Math.PI * 0.25 : 1.0, cell.getArea(), 0.01);
            }
        }
    }

    @Test
    void lineGridMakesAnOutlineAStrokeFrameAndLinesOutsideTheClearance() throws Exception {
        GerberImage board = board();
        List<Polygon> lines = thieve(board, with(Fill.LINE, 0.5, 1.0));
        assertFalse(lines.isEmpty());
        Geometry grown = board.solidGeometry().buffer(0.5 - 1e-6, 16);
        // Only the ring around the copper hugs it (at the clearance); nothing enters the cleared zone.
        for (Polygon line : lines) {
            assertFalse(line.getInteriorPoint().intersects(grown));
        }
        Options notItself = new Options(0.5, 1.0, 0.1, Reference.BOX, Options.defaults().boxType(), 64, Fill.LINE, 1, 2, 1,
                2, 0.25, 2);
        assertThrows(IllegalArgumentException.class, () -> CopperThieving.thieve(board.solidGeometry(),
                board.solidGeometry(), true, notItself, CancellationToken.none(), ProgressCallback.none()));
    }

    @Test
    void anAreaReferenceFillsOnlyTheDrawnZones() throws Exception {
        GerberImage board = board();
        Geometry zone = new WKTReader().read("POLYGON((4 -1, 16 -1, 16 1, 4 1, 4 -1))");
        Options options = new Options(0.5, 0.0, 0.1, Reference.AREA, Options.defaults().boxType(), 64, Fill.SOLID, 1, 2, 1,
                2, 0.25, 2);
        List<Polygon> thief = CopperThieving.thieve(board.solidGeometry(), zone, false, options, CancellationToken.none(),
                ProgressCallback.none());
        assertEquals(12 * 2, area(thief), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> CopperThieving.thieve(board.solidGeometry(), null, false,
                options, CancellationToken.none(), ProgressCallback.none()));
    }

    @Test
    void theRobberBarIsARingAroundTheBoundingBox() throws Exception {
        GerberImage board = board();
        CopperThieving.Robber robber = CopperThieving.robberBar(board.solidGeometry(), 1.0, 1.0);
        // Centre line 1.5 outside the copper box (22 x 2 -> 25 x 5); thickness 1 -> bar between 1.0 and 2.0 outside.
        assertEquals(-3.5, robber.line().getEnvelopeInternal().getMinX(), 1e-9 + 2.0);
        assertEquals(1.0, robber.thickness());
        Geometry outer = board.solidGeometry().getEnvelope().buffer(2.0);
        Geometry inner = board.solidGeometry().getEnvelope().buffer(1.0);
        assertEquals(outer.getArea() - inner.getArea(), robber.bar().getArea(), outer.getArea() * 0.03);
        GerberImage withBar = CopperThieving.withRobber(board, robber);
        assertTrue(withBar.apertures().size() > board.apertures().size());
        assertTrue(withBar.solidGeometry().getArea() > board.solidGeometry().getArea() + 40);
    }

    @Test
    void theCopperPlusThievingGoesIntoAnObjectWithTheSameCopper() throws Exception {
        GerberImage board = board();
        List<Polygon> thief = thieve(board, with(Fill.SOLID, 0.5, 1.0));
        GerberImage filled = CopperThieving.withThieving(board, thief);
        assertEquals(board.solidGeometry().getArea() + area(thief), filled.solidGeometry().getArea(), 1e-3);
    }

    @Test
    void thePlatingMaskTakesThievingAndRobberAndReportsTheArea() throws Exception {
        GerberImage mask = board();
        List<Polygon> thief = thieve(mask, with(Fill.SOLID, 0.5, 1.0));
        CopperThieving.Robber robber = CopperThieving.robberBar(mask.solidGeometry(), 1.0, 1.0);
        double maskArea = mask.solidGeometry().getArea();
        CopperThieving.PlatingMask both = CopperThieving.platingMask(mask, 0.0, thief, robber, Plating.BOTH);
        assertEquals(maskArea + area(thief) + robber.bar().getArea(), both.platedArea(), 1e-6);
        CopperThieving.PlatingMask none = CopperThieving.platingMask(mask, 0.0, thief, robber, Plating.NONE);
        assertEquals(maskArea, none.platedArea(), 1e-6);
        // A negative clearance shrinks the mask openings.
        CopperThieving.PlatingMask shrunk = CopperThieving.platingMask(mask, -0.25, thief, robber, Plating.NONE);
        assertEquals(2 * 1.5 * 1.5, shrunk.platedArea(), 1e-6);
    }
}
