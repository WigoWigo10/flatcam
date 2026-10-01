package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class DensityRasterAxisTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static List<PlotDrawableIndex.Part> parts(double... xy) {
        Coordinate[] c = new Coordinate[xy.length / 2];
        for (int i = 0; i < c.length; i++) {
            c[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
        }
        Geometry line = FACTORY.createLineString(c);
        return new PlotDrawableIndex(line).visibleParts(null);
    }

    private static int sum(short[] cover) {
        return java.util.stream.IntStream.range(0, cover.length).map(i -> cover[i]).sum();
    }

    @Test
    void verticalAndHorizontalSegmentsOfTheSameLengthCoverTheSameArea() {
        short[] horizontal = new short[100 * 100];
        short[] vertical = new short[100 * 100];
        short[] down = new short[100 * 100];
        DensityRaster.rasterize(parts(10, 50, 70, 50), 1, 0, 100, 100, 100, horizontal, 1.5);
        DensityRaster.rasterize(parts(50, 10, 50, 70), 1, 0, 100, 100, 100, vertical, 1.5);
        DensityRaster.rasterize(parts(50, 70, 50, 10), 1, 0, 100, 100, 100, down, 1.5);
        int expected = (int) (60 * 1.5 * DensityRaster.UNITS);
        assertEquals(expected, sum(horizontal), expected * 0.02);
        assertEquals(expected, sum(vertical), expected * 0.02);
        assertEquals(expected, sum(down), expected * 0.02);
    }

    @Test
    void aClosedRectangleRingDrawsAllFourSides() {
        short[] cover = new short[100 * 100];
        DensityRaster.rasterize(parts(20, 20, 80, 20, 80, 80, 20, 80, 20, 20), 1, 0, 100, 100, 100, cover, 1.5);
        int expected = (int) (240 * 1.5 * DensityRaster.UNITS);
        assertEquals(expected, sum(cover), expected * 0.03);
    }

    @Test
    void verticalAndHorizontalLinesAreTheSameInParallelBandsAsInASingleThread() {
        java.util.List<Geometry> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double x = 5 + i * 1.7;
            lines.add(FACTORY.createLineString(new Coordinate[] {new Coordinate(x, 5), new Coordinate(x, 190)}));
            lines.add(FACTORY.createLineString(new Coordinate[] {new Coordinate(5, 5 + i * 1.8), new Coordinate(190, 5 + i * 1.8)}));
        }
        List<PlotDrawableIndex.Part> parts = new PlotDrawableIndex(FACTORY.buildGeometry(lines)).visibleParts(null);
        short[] single = new short[200 * 200];
        short[] parallel = new short[200 * 200];
        DensityRaster.rasterize(parts, 1, 0, 200, 200, 200, single, 1.5, false);
        DensityRaster.rasterize(parts, 1, 0, 200, 200, 200, parallel, 1.5, true);
        int expected = (int) (200 * 185 * 1.5 * DensityRaster.UNITS);
        assertEquals(expected, sum(single), expected * 0.15);   // lines overlap at crossings; just not far off
        org.junit.jupiter.api.Assertions.assertArrayEquals(single, parallel);
    }

    @Test
    void aLineChoppedIntoShortSegmentsCoversTheSameAsTheWholeLine() {
        java.util.Random random = new java.util.Random(11);
        for (double[] ends : new double[][] {{10, 50, 90, 50}, {50.5, 10, 50.5, 90}, {10, 20, 80, 70}, {20, 80, 85, 15}}) {
            // The whole line, and the same line cut into pieces of 0.2 to 3 pixels.
            List<PlotDrawableIndex.Part> whole = parts(ends);
            java.util.List<Double> cuts = new java.util.ArrayList<>(List.of(0.0));
            double t = 0;
            double length = Math.hypot(ends[2] - ends[0], ends[3] - ends[1]);
            while (t < length) {
                t = Math.min(length, t + 0.2 + random.nextDouble() * 2.8);
                cuts.add(t / length);
            }
            double[] chopped = new double[cuts.size() * 2];
            for (int i = 0; i < cuts.size(); i++) {
                chopped[2 * i] = ends[0] + (ends[2] - ends[0]) * cuts.get(i);
                chopped[2 * i + 1] = ends[1] + (ends[3] - ends[1]) * cuts.get(i);
            }
            short[] a = new short[100 * 100];
            short[] b = new short[100 * 100];
            DensityRaster.rasterize(whole, 1, 0, 100, 100, 100, a, 1.5);
            DensityRaster.rasterize(parts(chopped), 1, 0, 100, 100, 100, b, 1.5);
            int worst = 0;
            for (int i = 0; i < a.length; i++) {
                worst = Math.max(worst, Math.abs(a[i] - b[i]));
            }
            // Straight lines: within 3/32 of a pixel everywhere (each piece rounds on its own), never the whole-sample
            // jumps of before. A slanted stroke crosses each pixel as a parallelogram that is approximated by thin
            // strips, and the exact 45-degree cases mix column and row strips: up to half a pixel at hard edges.
            double slope = Math.abs((ends[3] - ends[1]) / (ends[2] - ends[0]));
            int allowed = slope < 0.01 || slope > 100 ? 3 : 17;
            org.junit.jupiter.api.Assertions.assertTrue(worst <= allowed,
                    "worst difference " + worst + " for " + java.util.Arrays.toString(ends));
        }
    }

    @Test
    void aLineCentredOnAPixelFillsItsCentreFullyAndTheNeighboursAQuarter() {
        short[] cover = new short[100 * 100];
        // x = 50.5 is the middle of column 50: a 1.5 px stroke covers it fully and a quarter of each neighbour.
        DensityRaster.rasterize(parts(50.5, 10, 50.5, 90), 1, 0, 100, 100, 100, cover, 1.5);
        for (int row = 15; row < 85; row++) {
            assertEquals(DensityRaster.UNITS, cover[row * 100 + 50], 1, "row " + row);
            assertEquals(DensityRaster.UNITS / 4, cover[row * 100 + 49], 1);
            assertEquals(DensityRaster.UNITS / 4, cover[row * 100 + 51], 1);
        }
    }
}
