package org.flatcam.fx;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Density level of detail for the Plot Area. Past a few thousand tiny, packed line segments the Canvas spends tens
 * to hundreds of milliseconds executing the strokes of a picture that is already a solid colour. This draws the same
 * segments straight into a per-pixel count on the CPU - in parallel horizontal bands, each owning its own rows - and
 * turns the counts into one image: a pixel crossed by {@code n} lines gets {@code 1 - (1 - a)^n} of the stroke
 * opacity {@code a}, so thin lines stay lines and a packed area saturates.
 *
 * <p>Pure computation, no JavaFX: the view only wraps the pixels in an image.
 */
final class DensityRaster {

    /** {@code -Dflatcam.plot.density=false} turns the mode off, to compare against the plain vector drawing. */
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density"));

    /** Counts above this look the same as it; the table below stops there. */
    static final int MAX_COUNT = 63;

    /** Below this many segments the vector Canvas is both faster and prettier. */
    static final int MIN_SEGMENTS = 2_500;

    /** Many segments, however long: the Canvas is slow even if they are visible one by one. */
    static final int HEAVY_SEGMENTS = 15_000;

    /** Segments this short, on average, are fused into a solid colour (pixels). */
    static final double FUSED_SEGMENT_PIXELS = 1.5;

    /** Hysteresis: a layer already in density mode stays there until clearly out of the thresholds. */
    static final double LEAVE_FACTOR = 0.6;

    private DensityRaster() {
    }

    /**
     * Whether a layer of {@code segments} visible segments totalling {@code worldLength} (world units) should be drawn
     * as a density image at {@code scale} pixels per unit.
     */
    static boolean shouldRasterize(long segments, double worldLength, double scale, boolean alreadyDense) {
        if (!ENABLED) {
            return false;
        }
        if (segments < MIN_SEGMENTS * (alreadyDense ? LEAVE_FACTOR : 1.0)) {
            return false;
        }
        double heavy = HEAVY_SEGMENTS * (alreadyDense ? LEAVE_FACTOR : 1.0);
        if (segments >= heavy) {
            return true;
        }
        double averagePixels = worldLength * scale / segments;
        return averagePixels < FUSED_SEGMENT_PIXELS * (alreadyDense ? 1.3 : 1.0);
    }

    /**
     * Counts every segment of {@code parts} into {@code cover} ({@code width * height} counts, row 0 on top). A world
     * point maps to pixel {@code (x * scale + offsetX, offsetY - y * scale)}.
     */
    static void rasterize(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                          int width, int height, short[] cover) {
        rasterize(parts, scale, offsetX, offsetY, width, height, cover, true);
    }

    static void rasterize(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                          int width, int height, short[] cover, boolean parallel) {
        Arrays.fill(cover, 0, width * height, (short) 0);
        int bands = Math.max(1, Math.min(height, Runtime.getRuntime().availableProcessors()));
        if (!parallel || parts.size() < 64 || bands == 1) {
            drawBand(parts, scale, offsetX, offsetY, width, height, 0, height, cover);
            return;
        }
        IntStream.range(0, bands).parallel().forEach(band ->
                drawBand(parts, scale, offsetX, offsetY, width, height, height * band / bands,
                        height * (band + 1) / bands, cover));
    }

    private static void drawBand(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                                 int width, int height, int rowStart, int rowEnd, short[] cover) {
        for (PlotDrawableIndex.Part part : parts) {
            // Skip parts that cannot touch this band's rows (screen y grows as world y falls).
            double top = offsetY - part.bounds().getMaxY() * scale;
            double bottom = offsetY - part.bounds().getMinY() * scale;
            if (bottom < rowStart - 1 || top > rowEnd + 1) {
                continue;
            }
            drawGeometry(part.geometry(), scale, offsetX, offsetY, width, height, rowStart, rowEnd, cover);
        }
    }

    private static void drawGeometry(Geometry geometry, double scale, double offsetX, double offsetY, int width,
                                     int height, int rowStart, int rowEnd, short[] cover) {
        if (geometry instanceof LineString line) {
            drawSequence(line.getCoordinateSequence(), scale, offsetX, offsetY, width, height, rowStart, rowEnd, cover);
        } else if (geometry instanceof Polygon polygon) {
            // Same as the vector path of a stroke-only layer: the exterior ring.
            drawSequence(polygon.getExteriorRing().getCoordinateSequence(), scale, offsetX, offsetY, width, height,
                    rowStart, rowEnd, cover);
        } else if (geometry instanceof Point point && !point.isEmpty()) {
            plot(cover, width, rowStart, rowEnd, (int) Math.floor(point.getX() * scale + offsetX),
                    (int) Math.floor(offsetY - point.getY() * scale));
        }
    }

    private static void drawSequence(CoordinateSequence sequence, double scale, double offsetX, double offsetY,
                                     int width, int height, int rowStart, int rowEnd, short[] cover) {
        int size = sequence.size();
        if (size == 0) {
            return;
        }
        double previousX = sequence.getX(0) * scale + offsetX;
        double previousY = offsetY - sequence.getY(0) * scale;
        if (size == 1) {
            plot(cover, width, rowStart, rowEnd, (int) Math.floor(previousX), (int) Math.floor(previousY));
            return;
        }
        for (int i = 1; i < size; i++) {
            double x = sequence.getX(i) * scale + offsetX;
            double y = offsetY - sequence.getY(i) * scale;
            drawSegment(previousX, previousY, x, y, width, height, rowStart, rowEnd, cover);
            previousX = x;
            previousY = y;
        }
    }

    /**
     * Liang-Barsky clip to the whole image (so a segment far outside the view costs nothing, and every band sees the
     * very same clipped segment), then the DDA walk of that segment. Each band only plots the steps that land in its
     * rows, taken from the same step positions, so the counts do not depend on how many bands there are.
     */
    private static void drawSegment(double x0, double y0, double x1, double y1, int width, int height, int rowStart,
                                    int rowEnd, short[] cover) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
            return;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double t0 = 0;
        double t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0, width - x0, y0, height - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) {
                    return;
                }
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) {
                    if (t > t1) {
                        return;
                    }
                    t0 = Math.max(t0, t);
                } else {
                    if (t < t0) {
                        return;
                    }
                    t1 = Math.min(t1, t);
                }
            }
        }
        double cx = x0 + t0 * dx;
        double cy = y0 + t0 * dy;
        double ex = x0 + t1 * dx;
        double ey = y0 + t1 * dy;
        int steps = (int) Math.ceil(Math.max(Math.abs(ex - cx), Math.abs(ey - cy)));
        if (steps <= 0) {
            plot(cover, width, rowStart, rowEnd, (int) Math.floor(cx), (int) Math.floor(cy));
            return;
        }
        double stepX = (ex - cx) / steps;
        double stepY = (ey - cy) / steps;
        int from = 0;
        int to = steps;
        if (Math.abs(stepY) > 1e-12) {
            double a = (rowStart - cy) / stepY;
            double b = (rowEnd - cy) / stepY;
            from = Math.max(0, (int) Math.floor(Math.min(a, b)) - 1);
            to = Math.min(steps, (int) Math.ceil(Math.max(a, b)) + 1);
        } else if (cy < rowStart || cy >= rowEnd) {
            return;
        }
        for (int s = from; s <= to; s++) {
            plot(cover, width, rowStart, rowEnd, (int) Math.floor(cx + s * stepX), (int) Math.floor(cy + s * stepY));
        }
    }

    private static void plot(short[] cover, int width, int rowStart, int rowEnd, int x, int y) {
        if (x >= 0 && x < width && y >= rowStart && y < rowEnd) {
            int index = y * width + x;
            if (cover[index] < Short.MAX_VALUE) {
                cover[index]++;
            }
        }
    }

    /**
     * The counts as premultiplied ARGB pixels for a colour with the given 0..1 channels and opacity: a pixel crossed
     * by {@code n} lines carries {@code 1 - (1 - opacity)^n}.
     */
    static void toPremultipliedArgb(short[] cover, int pixels, double red, double green, double blue, double opacity,
                                    int[] out) {
        int[] table = new int[MAX_COUNT + 1];
        for (int n = 1; n <= MAX_COUNT; n++) {
            double alpha = 1 - Math.pow(1 - Math.min(1, Math.max(0, opacity)), n);
            int a = (int) Math.round(alpha * 255);
            table[n] = (a << 24) | ((int) Math.round(red * alpha * 255) << 16)
                    | ((int) Math.round(green * alpha * 255) << 8) | (int) Math.round(blue * alpha * 255);
        }
        IntStream.range(0, Math.max(1, Math.min(8, pixels / 100_000 + 1))).parallel().forEach(chunk -> {
            int chunks = Math.max(1, Math.min(8, pixels / 100_000 + 1));
            int from = (int) ((long) pixels * chunk / chunks);
            int to = (int) ((long) pixels * (chunk + 1) / chunks);
            for (int i = from; i < to; i++) {
                int n = cover[i];
                out[i] = n <= 0 ? 0 : table[Math.min(n, MAX_COUNT)];
            }
        });
    }
}
