package org.flatcam.cam.gerber.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

class TrackBendModeTest {

    @Test
    void routesTheFiveLegacyBendModes() {
        Coordinate start = new Coordinate(0, 0);
        Coordinate end = new Coordinate(6, 2);

        assertRoute(TrackBendMode.FORTY_FIVE.route(start, end), 0, 0, 4, 0, 6, 2);
        assertRoute(TrackBendMode.REVERSE_FORTY_FIVE.route(start, end), 0, 0, 2, 2, 6, 2);
        assertRoute(TrackBendMode.NINETY.route(start, end), 0, 0, 6, 0, 6, 2);
        assertRoute(TrackBendMode.REVERSE_NINETY.route(start, end), 0, 0, 0, 2, 6, 2);
        assertRoute(TrackBendMode.FREE.route(start, end), 0, 0, 6, 2);
    }

    @Test
    void verticalAndDiagonalRoutesDoNotCreateDuplicateCorners() {
        assertRoute(TrackBendMode.FORTY_FIVE.route(new Coordinate(1, 1), new Coordinate(1, 5)),
                1, 1, 1, 5);
        assertRoute(TrackBendMode.REVERSE_FORTY_FIVE.route(new Coordinate(1, 1), new Coordinate(4, 4)),
                1, 1, 4, 4);
        assertEquals(1, TrackBendMode.FREE.route(new Coordinate(2, 2), new Coordinate(2, 2)).size());
    }

    @Test
    void modeCyclingWrapsInBothDirections() {
        assertEquals(TrackBendMode.REVERSE_FORTY_FIVE, TrackBendMode.FORTY_FIVE.next());
        assertEquals(TrackBendMode.FORTY_FIVE, TrackBendMode.FREE.next());
        assertEquals(TrackBendMode.FREE, TrackBendMode.FORTY_FIVE.previous());
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> TrackBendMode.FREE.route(new Coordinate(Double.NaN, 0), new Coordinate(1, 1)));
    }

    private static void assertRoute(List<Coordinate> actual, double... xy) {
        assertEquals(xy.length / 2, actual.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(xy[i * 2], actual.get(i).x, 1e-9);
            assertEquals(xy[i * 2 + 1], actual.get(i).y, 1e-9);
        }
    }
}
