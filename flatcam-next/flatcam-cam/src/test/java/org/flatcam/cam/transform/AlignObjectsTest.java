package org.flatcam.cam.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;

class AlignObjectsTest {

    @TempDir
    Path directory;

    private GerberImage pads() throws IOException {
        Path file = directory.resolve("pads.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2.0X2.0*%",
                "D10*", "X100000Y100000D03*", "X300000Y100000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    private static Coordinate apply(List<TransformOp> operations, Coordinate point) {
        Coordinate moved = point;
        for (TransformOp operation : operations) {
            moved = operation.apply(moved);
        }
        return moved;
    }

    @Test
    void aPadIsFoundByAClickInsideItAndItsCentreReturned() throws IOException {
        GerberImage gerber = pads();
        Coordinate centre = AlignObjects.padCenterAt(gerber, new Coordinate(10.4, 9.7));
        assertNotNull(centre);
        assertEquals(10.0, centre.x, 1e-6);
        assertEquals(10.0, centre.y, 1e-6);
        assertEquals(30.0, AlignObjects.padCenterAt(gerber, new Coordinate(30.9, 10.9)).x, 1e-6);
        assertNull(AlignObjects.padCenterAt(gerber, new Coordinate(20, 10)));
    }

    @Test
    void aDrillIsFoundWithinItsRadiusPlusTheExtraReachAndTheNearestWins() {
        ExcellonImage drills = ExcellonImage.of("MM", Map.of(1, 1.0),
                List.of(new ExcellonImage.Drill(1, 5, 5), new ExcellonImage.Drill(1, 5.8, 5)), List.of(), null);
        assertEquals(5.0, AlignObjects.drillCenterAt(drills, new Coordinate(5.2, 5.1), 0).x, 1e-9);
        assertEquals(5.8, AlignObjects.drillCenterAt(drills, new Coordinate(5.7, 5.0), 0).x, 1e-9);
        assertNull(AlignObjects.drillCenterAt(drills, new Coordinate(8, 5), 0));
        assertNotNull(AlignObjects.drillCenterAt(drills, new Coordinate(6.7, 5), 0.5));
        assertNull(AlignObjects.drillCenterAt(drills, new Coordinate(6.9, 5), 0.5));
    }

    @Test
    void onePairIsATranslation() {
        List<TransformOp> plan = AlignObjects.plan(List.of(new Coordinate(10, 10), new Coordinate(25, 4)));
        assertEquals(1, plan.size());
        Coordinate moved = apply(plan, new Coordinate(10, 10));
        assertEquals(25, moved.x, 1e-9);
        assertEquals(4, moved.y, 1e-9);
    }

    @Test
    void twoPairsAlsoRotateAboutTheFirstDestinationSoBothPointsLand() {
        // The moved object has pads at (10,10) and (30,10); the reference has them at (50,50) and (50,70): a quarter turn.
        List<Coordinate> points = List.of(new Coordinate(10, 10), new Coordinate(50, 50),
                new Coordinate(30, 10), new Coordinate(50, 70));
        List<TransformOp> plan = AlignObjects.plan(points);
        assertEquals(2, plan.size());
        Coordinate first = apply(plan, new Coordinate(10, 10));
        Coordinate second = apply(plan, new Coordinate(30, 10));
        assertEquals(50, first.x, 1e-9);
        assertEquals(50, first.y, 1e-9);
        assertEquals(50, second.x, 1e-9);
        assertEquals(70, second.y, 1e-9);
    }

    @Test
    void aVerticalVectorAndATurnPastNinetyDegreesWork() {
        // start vector (0, 10) -> destination vector (-10, 0): +90 degrees, and (0, -10) from (0, 10): 180 degrees.
        List<TransformOp> quarter = AlignObjects.plan(List.of(new Coordinate(0, 0), new Coordinate(0, 0),
                new Coordinate(0, 10), new Coordinate(-10, 0)));
        Coordinate moved = apply(quarter, new Coordinate(0, 10));
        assertEquals(-10, moved.x, 1e-9);
        assertEquals(0, moved.y, 1e-9);
        List<TransformOp> half = AlignObjects.plan(List.of(new Coordinate(0, 0), new Coordinate(0, 0),
                new Coordinate(0, 10), new Coordinate(0, -10)));
        Coordinate flipped = apply(half, new Coordinate(0, 10));
        assertEquals(0, flipped.x, 1e-9);
        assertEquals(-10, flipped.y, 1e-9);
    }

    @Test
    void anAlreadyAlignedPairNeedsNoRotationAndBadInputIsRejected() {
        List<TransformOp> plan = AlignObjects.plan(List.of(new Coordinate(0, 0), new Coordinate(5, 5),
                new Coordinate(10, 0), new Coordinate(15, 5)));
        assertEquals(1, plan.size(), "same direction: translation only");
        assertThrows(IllegalArgumentException.class, () -> AlignObjects.plan(List.of(new Coordinate(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> AlignObjects.plan(List.of(new Coordinate(0, 0),
                new Coordinate(5, 5), new Coordinate(0, 0), new Coordinate(5, 5))));
    }
}
