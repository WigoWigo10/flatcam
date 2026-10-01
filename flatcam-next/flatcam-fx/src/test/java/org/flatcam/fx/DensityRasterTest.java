package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class DensityRasterTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Geometry line(double... xy) {
        Coordinate[] c = new Coordinate[xy.length / 2];
        for (int i = 0; i < c.length; i++) {
            c[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
        }
        return FACTORY.createLineString(c);
    }

    private static List<PlotDrawableIndex.Part> parts(Geometry geometry) {
        return new PlotDrawableIndex(geometry).visibleParts(null);
    }

    @Test
    void aLineFillsItsRowAndNothingElse() {
        short[] cover = new short[10 * 10];
        // World y = 5 maps to pixel row 10 - 5 = 5 with offsetY = 10.
        DensityRaster.rasterize(parts(line(0, 5, 9, 5)), 1, 0, 10, 10, 10, cover);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                assertEquals(y == 5 && x <= 9 ? 1 : 0, cover[y * 10 + x], "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void aSegmentFarOutsideTheViewIsClippedInsteadOfWalkedPixelByPixel() {
        short[] cover = new short[100 * 100];
        Geometry huge = line(-1e12, 50, 1e12, 50);
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> DensityRaster.rasterize(parts(huge), 1, 0, 100, 100, 100, cover));
        int row = 0;
        for (int x = 0; x < 100; x++) {
            row += cover[50 * 100 + x];
        }
        assertEquals(100, row);
        // Entirely outside: nothing drawn and nothing blows up.
        short[] none = new short[100 * 100];
        DensityRaster.rasterize(parts(line(500, 500, 900, 900)), 1, 0, 100, 100, 100, none);
        assertEquals(0, java.util.stream.IntStream.range(0, none.length).map(i -> none[i]).sum());
    }

    @Test
    void parallelBandsGiveTheSameCountsAsASingleThread() {
        Random random = new Random(7);
        List<Geometry> lines = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            lines.add(line(random.nextDouble() * 200 - 50, random.nextDouble() * 200 - 50,
                    random.nextDouble() * 200 - 50, random.nextDouble() * 200 - 50,
                    random.nextDouble() * 200 - 50, random.nextDouble() * 200 - 50));
        }
        List<PlotDrawableIndex.Part> parts = parts(FACTORY.buildGeometry(lines));
        short[] single = new short[120 * 90];
        short[] parallel = new short[120 * 90];
        DensityRaster.rasterize(parts, 0.8, 5, 80, 120, 90, single, false);
        DensityRaster.rasterize(parts, 0.8, 5, 80, 120, 90, parallel, true);
        assertArrayEquals(single, parallel);
        assertTrue(java.util.stream.IntStream.range(0, single.length).map(i -> single[i]).sum() > 1000);
    }

    @Test
    void overlappingLinesSaturateTowardsTheStrokeOpacity() {
        short[] cover = {0, 1, 2, 63, 500};
        int[] argb = new int[cover.length];
        DensityRaster.toPremultipliedArgb(cover, cover.length, 1, 0, 0, 0.5, argb);
        assertEquals(0, argb[0]);
        assertEquals(128, argb[1] >>> 24);                    // one line: half opacity
        assertEquals(191, argb[2] >>> 24);                    // two lines: 1 - 0.5^2 = 0.75
        assertEquals(255, argb[3] >>> 24);                    // many lines: solid
        assertEquals(argb[3], argb[4]);                       // counts past the table look the same
        assertEquals(128, (argb[1] >> 16) & 0xFF);            // premultiplied red
        assertEquals(0, argb[1] & 0xFFFF);
    }

    @Test
    void theModeNeedsManyShortSegmentsAndHasHysteresis() {
        // Few segments: always vector, however short.
        assertFalse(DensityRaster.shouldRasterize(1_000, 100, 1, false));
        // Heavy: raster even when each segment is long.
        assertTrue(DensityRaster.shouldRasterize(20_000, 20_000 * 50.0, 1, false));
        // Medium count: only when the segments are about a pixel long.
        assertTrue(DensityRaster.shouldRasterize(5_000, 5_000 * 0.5, 1, false));
        assertFalse(DensityRaster.shouldRasterize(5_000, 5_000 * 20.0, 1, false));
        // Already dense: stays in the mode a little beyond the entry thresholds.
        assertFalse(DensityRaster.shouldRasterize(13_000, 13_000 * 50.0, 1, false));
        assertTrue(DensityRaster.shouldRasterize(13_000, 13_000 * 50.0, 1, true));
        assertTrue(DensityRaster.shouldRasterize(5_000, 5_000 * 1.8, 1, true));
        assertFalse(DensityRaster.shouldRasterize(5_000, 5_000 * 1.8, 1, false));
        // Far below the thresholds it leaves the mode.
        assertFalse(DensityRaster.shouldRasterize(1_200, 1_200 * 0.2, 1, true));
    }

    @Test
    void theIndexReportsTheSegmentsAndLengthInView() {
        Geometry geometry = FACTORY.buildGeometry(List.of(line(0, 0, 10, 0, 10, 10), line(100, 100, 110, 100)));
        PlotDrawableIndex index = new PlotDrawableIndex(geometry);
        double[] all = index.visibleLoad(null);
        assertEquals(3, all[0]);
        assertEquals(30, all[1], 1e-9);
        double[] near = index.visibleLoad(new org.locationtech.jts.geom.Envelope(-1, 20, -1, 20));
        assertEquals(2, near[0]);
        assertEquals(20, near[1], 1e-9);
    }
}
