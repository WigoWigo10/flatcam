package org.flatcam.cam.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

class MinimumDistanceTest {

    private static Geometry wkt(String text) throws ParseException {
        return new WKTReader().read(text);
    }

    private static MinimumDistance.Result find(Geometry geometry, int precision) {
        return MinimumDistance.find(geometry, precision, CancellationToken.none(), ProgressCallback.none());
    }

    /** Three 1 x 1 squares in a row: gaps 1 (A-B), 2 (B-C) and 4 (A-C). */
    private static Geometry row() throws ParseException {
        return wkt("MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)), ((2 0, 3 0, 3 1, 2 1, 2 0)), ((5 0, 6 0, 6 1, 5 1, 5 0)))");
    }

    @Test
    void theSmallestGapItsFrequencyItsLocationAndTheOtherGaps() throws Exception {
        MinimumDistance.Result result = find(row(), 4);
        assertEquals(3, result.features());
        assertEquals(1.0, result.minimum(), 1e-9);
        assertEquals(1, result.frequency());
        MinimumDistance.Pair pair = result.minimumLocations().get(0);
        assertEquals(1.0, Math.min(pair.first().x, pair.second().x), 1e-9);
        assertEquals(2.0, Math.max(pair.first().x, pair.second().x), 1e-9);
        assertEquals(1.5, pair.middle().x, 1e-9);
        assertEquals(java.util.List.of(2.0, 4.0), java.util.List.copyOf(result.others().keySet()));
    }

    @Test
    void pairsAtTheSameGapAreCountedTogether() throws Exception {
        // Four squares on a grid, 1 apart: four pairs at gap 1 (the two diagonals are sqrt(2)).
        Geometry grid = wkt("MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)), ((2 0, 3 0, 3 1, 2 1, 2 0)), "
                + "((0 2, 1 2, 1 3, 0 3, 0 2)), ((2 2, 3 2, 3 3, 2 3, 2 2)))");
        MinimumDistance.Result result = find(grid, 4);
        assertEquals(1.0, result.minimum(), 1e-9);
        assertEquals(4, result.frequency());
        assertEquals(1.4142, result.others().firstKey(), 1e-4);
        assertEquals(2, result.others().firstEntry().getValue().size());
    }

    @Test
    void distancesAreRoundedToThePrecisionAndGroupedByIt() throws Exception {
        Geometry close = wkt("MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)), ((1.30004 0, 2 0, 2 1, 1.30004 1, 1.30004 0)), "
                + "((3.29996 0, 4 0, 4 1, 3.29996 1, 3.29996 0)))");
        MinimumDistance.Result coarse = find(close, 2);
        assertEquals(0.3, coarse.minimum(), 1e-9);
        assertEquals(1, coarse.frequency(), "A-B = 0.30004 and B-C = 1.29992 differ even rounded to 2 decimals");
        assertEquals(1.3, coarse.others().firstKey(), 1e-9);
    }

    @Test
    void touchingPiecesMergeIntoOneAndOnePieceIsAnError() throws Exception {
        Geometry touching = wkt("MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)), ((1 0, 2 0, 2 1, 1 1, 1 0)))");
        assertThrows(IllegalArgumentException.class, () -> find(touching, 4));
        assertThrows(IllegalArgumentException.class, () -> find(wkt("POLYGON EMPTY"), 4));
        assertThrows(IllegalArgumentException.class, () -> MinimumDistance.find(row(), 11, CancellationToken.none(),
                ProgressCallback.none()));
    }

    @Test
    void aPieceInsideAHoleIsMeasuredFromTheHoleEdgeAndCancellationStops() throws Exception {
        Geometry ring = wkt("MULTIPOLYGON(((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 8 2, 8 8, 2 8, 2 2)), "
                + "((4 4, 6 4, 6 6, 4 6, 4 4)))");
        MinimumDistance.Result result = find(ring, 4);
        assertEquals(2.0, result.minimum(), 1e-9);
        assertThrows(CancellationException.class, () -> MinimumDistance.find(row(), 4, () -> true, ProgressCallback.none()));
        assertTrue(MinimumDistance.segment(result.minimumLocations().get(0)).getLength() > 0);
    }
}
