package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.flatcam.cam.convert.CornerMarkers.Corner;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

class MarkersTest {

    @TempDir
    Path directory;

    /** One 2 x 2 square pad centred at (10, 10): bounds 9..11. */
    private GerberImage board() throws IOException {
        Path file = directory.resolve("board.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2.0X2.0*%",
                "D10*", "X100000Y100000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    private static double[] bounds(GerberImage image) {
        Envelope e = image.solidGeometry().getEnvelopeInternal();
        return new double[] {e.getMinX(), e.getMinY(), e.getMaxX(), e.getMaxY()};
    }

    @Test
    void autoFiducialsSitAtTheGrownBoxCorners() {
        List<Coordinate> points = Fiducials.autoPoints(new double[] {0, 0, 10, 6}, 1.0, Fiducials.SecondPoint.UP);
        assertEquals(List.of(new Coordinate(-1, -1), new Coordinate(11, 7), new Coordinate(-1, 7)), points);
        assertEquals(new Coordinate(11, -1),
                Fiducials.autoPoints(new double[] {0, 0, 10, 6}, 1.0, Fiducials.SecondPoint.DOWN).get(2));
        assertEquals(2, Fiducials.autoPoints(new double[] {0, 0, 10, 6}, 1.0, Fiducials.SecondPoint.NONE).size());
    }

    @Test
    void fiducialShapesAreAddedAndShareTheirAperture() throws IOException {
        GerberImage board = board();
        List<Coordinate> points = List.of(new Coordinate(0, 0), new Coordinate(20, 20));
        GerberImage round = Fiducials.add(board, points, Fiducials.Type.CIRCULAR, 1.0, 0.25);
        assertEquals(board.shapes().size() + 2, round.shapes().size());
        assertEquals(board.solidGeometry().getArea() + 2 * Math.PI * 0.25, round.solidGeometry().getArea(), 0.03);
        GerberImage again = Fiducials.add(round, points, Fiducials.Type.CIRCULAR, 1.0, 0.25);
        assertEquals(round.apertures().size(), again.apertures().size());
        GerberImage cross = Fiducials.add(board, points, Fiducials.Type.CROSS, 2.0, 0.2);
        assertEquals(board.shapes().size() + 4, cross.shapes().size());
        GerberImage chess = Fiducials.add(board, points, Fiducials.Type.CHESS, 2.0, 0.2);
        assertEquals(board.solidGeometry().getArea() + 2 * 2.0, chess.solidGeometry().getArea(), 1e-6);
        assertThrows(IllegalArgumentException.class,
                () -> Fiducials.add(board, points, Fiducials.Type.CROSS, 1.0, 1.0));
    }

    @Test
    void cornerMarkersSitOutsideTheBoxByMarginAndHalfTheLine() throws IOException {
        GerberImage board = board();
        var anchors = CornerMarkers.anchors(bounds(board), Set.of(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT), 0.2, 0.5);
        assertEquals(new Coordinate(8.4, 11.6), anchors.get(Corner.TOP_LEFT));
        assertEquals(new Coordinate(11.6, 8.4), anchors.get(Corner.BOTTOM_RIGHT));
        GerberImage marked = CornerMarkers.add(board, bounds(board), Set.of(Corner.TOP_LEFT),
                CornerMarkers.Style.CORNER, 0.2, 3.0, 0.5);
        assertEquals(board.shapes().size() + 2, marked.shapes().size());
        Envelope box = marked.solidGeometry().getEnvelopeInternal();
        assertEquals(8.3, box.getMinX(), 1e-6);
        assertEquals(11.7, box.getMaxY(), 1e-6);
        assertEquals(11.0, box.getMaxX(), 1e-6);
        assertThrows(IllegalArgumentException.class,
                () -> CornerMarkers.add(board, bounds(board), Set.of(), CornerMarkers.Style.CORNER, 0.2, 3, 0));
    }

    @Test
    void cornerDrillsAreOneHolePerCorner() throws IOException {
        GerberImage board = board();
        ExcellonImage drills = CornerMarkers.drills("MM", bounds(board), Set.of(Corner.values()), 0.2, 0.0, 0.5);
        assertEquals(4, drills.totalDrills());
        assertTrue(drills.drills().stream().anyMatch(d -> Math.abs(d.x() - 8.9) < 1e-9 && Math.abs(d.y() - 11.1) < 1e-9));
    }
}
