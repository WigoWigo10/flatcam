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

    private static int sum(short[] cover) {
        return java.util.stream.IntStream.range(0, cover.length).map(i -> cover[i]).sum();
    }

    @Test
    void aLineCoversExactlyItsStrokeArea() {
        short[] cover = new short[10 * 10];
        // World y = 5 maps to the pixel edge between rows 4 and 5 (offsetY = 10); a 2 px wide stroke covers both rows.
        DensityRaster.rasterize(parts(line(0, 5, 9, 5)), 1, 0, 10, 10, 10, cover, 2.0);
        for (int x = 0; x < 9; x++) {
            assertEquals(DensityRaster.UNITS, cover[4 * 10 + x], "row 4, column " + x);
            assertEquals(DensityRaster.UNITS, cover[5 * 10 + x], "row 5, column " + x);
            assertEquals(0, cover[3 * 10 + x]);
            assertEquals(0, cover[6 * 10 + x]);
        }
        // 9 px long x 2 px wide = 18 px of area.
        assertEquals(18 * DensityRaster.UNITS, sum(cover), 18);
    }

    @Test
    void aThinLineIsAntiAliasedAcrossTheRowsItStraddles() {
        short[] cover = new short[10 * 10];
        // A 1.5 px stroke centred on a pixel edge: 0.75 of each of the two rows it touches.
        DensityRaster.rasterize(parts(line(0, 5, 9, 5)), 1, 0, 10, 10, 10, cover, 1.5);
        assertEquals(0.75 * DensityRaster.UNITS, cover[4 * 10 + 4], 1);
        assertEquals(0.75 * DensityRaster.UNITS, cover[5 * 10 + 4], 1);
        assertEquals(0, cover[6 * 10 + 4]);
    }

    @Test
    void closelyPackedLinesAddUpInsteadOfLeavingGaps() {
        // Eight horizontal 1.5 px strokes 0.8 px apart: every pixel row in the span is covered by at least one line's
        // full width (1 pixel of stroke area per pixel), where 1 px aliased lines would skip rows.
        List<Geometry> lines = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            lines.add(line(0, 10 + i * 0.8, 19, 10 + i * 0.8));
        }
        short[] cover = new short[20 * 30];
        DensityRaster.rasterize(parts(FACTORY.buildGeometry(lines)), 1, 0, 30, 20, 30, cover, 1.5);
        for (int row = 16; row <= 19; row++) {
            assertTrue(cover[row * 20 + 8] >= DensityRaster.UNITS, "row " + row + " has coverage " + cover[row * 20 + 8]);
        }
    }

    @Test
    void aSlantedLineHasTheSameAreaAsALongerHorizontalOneOfEqualLength() {
        short[] flat = new short[200 * 200];
        short[] slanted = new short[200 * 200];
        DensityRaster.rasterize(parts(line(10, 100, 110, 100)), 1, 0, 200, 200, 200, flat, 1.5);
        DensityRaster.rasterize(parts(line(10, 100, 70, 180)), 1, 0, 200, 200, 200, slanted, 1.5);  // length 100
        assertEquals(sum(flat), sum(slanted), sum(flat) * 0.03);
    }

    @Test
    void aSegmentFarOutsideTheViewIsClippedInsteadOfWalkedPixelByPixel() {
        short[] cover = new short[100 * 100];
        Geometry huge = line(-1e12, 50, 1e12, 50);
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> DensityRaster.rasterize(parts(huge), 1, 0, 100, 100, 100, cover, 1.5));
        assertEquals(1.5 * 100 * DensityRaster.UNITS, sum(cover), 100);
        // Entirely outside: nothing drawn and nothing blows up.
        short[] none = new short[100 * 100];
        DensityRaster.rasterize(parts(line(500, 500, 900, 900)), 1, 0, 100, 100, 100, none, 1.5);
        assertEquals(0, sum(none));
    }

    @Test
    void parallelBandsGiveTheSameCoverageAsASingleThread() {
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
        DensityRaster.rasterize(parts, 0.8, 5, 80, 120, 90, single, 1.5, false);
        DensityRaster.rasterize(parts, 0.8, 5, 80, 120, 90, parallel, 1.5, true);
        assertArrayEquals(single, parallel);
        assertTrue(sum(single) > 1000 * DensityRaster.UNITS);
    }

    @Test
    void coverageTurnsIntoAlphaLinearlyUpToOneLineAndSaturatesBeyond() {
        int u = DensityRaster.UNITS;
        short[] cover = {0, (short) (u / 2), (short) u, (short) (2 * u), (short) (63 * u), 30000};
        int[] argb = new int[cover.length];
        DensityRaster.toPremultipliedArgb(cover, cover.length, 1, 0, 0, 0.5, argb);
        assertEquals(0, argb[0]);
        assertEquals(64, argb[1] >>> 24);                     // half a line of a 0.5 stroke: 0.25
        assertEquals(128, argb[2] >>> 24);                    // one full line: the stroke opacity
        assertEquals(191, argb[3] >>> 24);                    // two lines: 1 - 0.5^2 = 0.75
        assertEquals(255, argb[4] >>> 24);                    // very many: solid
        assertEquals(argb[4], argb[5]);                       // beyond the table looks the same
        assertEquals(128, (argb[2] >> 16) & 0xFF);            // premultiplied red
        assertEquals(0, argb[2] & 0xFFFF);
        // A fully opaque stroke: a partial pixel stays partial (anti-aliased edge), one line is solid.
        DensityRaster.toPremultipliedArgb(cover, cover.length, 0, 1, 0, 1.0, argb);
        assertEquals(128, argb[1] >>> 24);
        assertEquals(255, argb[2] >>> 24);
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

    @Test
    void wideStrokesSwitchToTheImageWithFewerSegmentsThanThinOnes() {
        // A thin stroke needs thousands of segments; a cutter-wide one only hundreds, with hysteresis once dense.
        assertEquals(false, DensityRaster.shouldRasterize(400, 4000, 10, false, 1.5));
        assertEquals(true, DensityRaster.shouldRasterize(DensityRaster.WIDE_MIN_SEGMENTS, 4000, 10, false, 9));
        assertEquals(false, DensityRaster.shouldRasterize(DensityRaster.WIDE_MIN_SEGMENTS - 1, 4000, 10, false, 9));
        assertEquals(true, DensityRaster.shouldRasterize((long) (DensityRaster.WIDE_MIN_SEGMENTS * 0.7), 4000, 10, true, 9));
        // Past the widest supported stroke the Canvas keeps it.
        assertEquals(false, DensityRaster.shouldRasterize(100_000, 4000, 10, false, DensityRaster.MAX_WIDTH + 1));
    }
}
