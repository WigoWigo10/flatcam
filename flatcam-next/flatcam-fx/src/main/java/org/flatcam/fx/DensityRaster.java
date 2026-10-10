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
 * segments straight into a per-pixel <em>coverage</em> on the CPU - in parallel horizontal bands, each owning its own
 * rows - and turns the coverage into one image.
 *
 * <p>Each segment is a stroke of the layer's real width: every pixel it touches gets the exact area the stroke covers
 * in it (so the picture is anti-aliased like the vector one, and lines packed closer than a pixel add up instead of
 * leaving gaps). Coverage {@code S} (in line-widths) of opacity {@code a} becomes the alpha {@code a * S} up to one
 * full line and {@code 1 - (1 - a)^S} beyond, so a lone line keeps its look and a packed area saturates.
 *
 * <p>Pure computation, no JavaFX: the view only wraps the pixels in an image.
 */
final class DensityRaster {

    /** {@code -Dflatcam.plot.density=false} turns the mode off, to compare against the plain vector drawing. */
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density"));

    /** Coverage is kept in 1/32 of a pixel of stroke area. */
    static final int UNITS = 32;

    /** Coverage beyond this many full pixels of stroke looks the same as it; the table below stops there. */
    static final int MAX_COVERAGE = 63;

    /** Below this many segments the vector Canvas is both faster and prettier. */
    static final int MIN_SEGMENTS = 2_500;

    /** Many segments, however long: the Canvas is slow even if they are visible one by one. */
    static final int HEAVY_SEGMENTS = 15_000;

    /** Segments this short, on average, are fused into a solid colour (pixels). */
    static final double FUSED_SEGMENT_PIXELS = 1.5;

    /** Hysteresis: a layer already in density mode stays there until clearly out of the thresholds. */
    static final double LEAVE_FACTOR = 0.6;

    /**
     * Strokes wider than this many pixels get round caps and joins, as the Canvas draws them: the area under each
     * segment alone would leave a notch at every bend and a flat end at every open path.
     */
    static final double ROUND_FROM_WIDTH = 2.5;

    /** {@code -Dflatcam.plot.density.wide=false} keeps wide strokes (CNC cutter paths) on the vector Canvas. */
    private static final boolean WIDE_ENABLED = !"false".equalsIgnoreCase(System.getProperty("flatcam.plot.density.wide"));

    /** Visible segments from which a wide stroke is drawn as an image. */
    static final int WIDE_MIN_SEGMENTS = Integer.getInteger("flatcam.plot.density.wideMin", 300);

    /** Widest stroke the density image takes over from the Canvas (a wider one is a handful of huge strokes). */
    static final double MAX_WIDTH = 96;

    private DensityRaster() {
    }

    /**
     * Whether a layer of {@code segments} visible segments totalling {@code worldLength} (world units) should be drawn
     * as a density image at {@code scale} pixels per unit.
     */
    static boolean shouldRasterize(long segments, double worldLength, double scale, boolean alreadyDense) {
        return shouldRasterize(segments, worldLength, scale, alreadyDense, 1);
    }

    /**
     * As above for a stroke of {@code lineWidth} pixels. A wide stroke costs the Canvas far more per segment than a
     * thin one (the rasterizer fills the stroke's area, not just its centre line), so it switches to the image with
     * fewer segments.
     */
    static boolean shouldRasterize(long segments, double worldLength, double scale, boolean alreadyDense,
                                   double lineWidth) {
        if (!ENABLED) {
            return false;
        }
        if (lineWidth > ROUND_FROM_WIDTH) {
            return WIDE_ENABLED && lineWidth <= MAX_WIDTH
                    && segments >= WIDE_MIN_SEGMENTS * (alreadyDense ? LEAVE_FACTOR : 1.0);
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
     * Accumulates the stroke coverage (in {@link #UNITS} per pixel of area) of every segment of {@code parts} into
     * {@code cover} ({@code width * height} values, row 0 on top). A world point maps to pixel
     * {@code (x * scale + offsetX, offsetY - y * scale)}; {@code lineWidth} is the stroke width in pixels.
     */
    static void rasterize(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                          int width, int height, short[] cover, double lineWidth) {
        rasterize(parts, scale, offsetX, offsetY, width, height, cover, lineWidth, true);
    }

    static void rasterize(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                          int width, int height, short[] cover, double lineWidth, boolean parallel) {
        rasterize(parts, scale, offsetX, offsetY, width, height, cover, lineWidth, parallel, () -> false);
    }

    /**
     * As above, polling {@code cancelled} every few dozen parts; returns {@code false} (and leaves {@code cover} partly
     * drawn) when it asked to stop.
     */
    static boolean rasterize(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                             int width, int height, short[] cover, double lineWidth, boolean parallel,
                             java.util.function.BooleanSupplier cancelled) {
        Arrays.fill(cover, 0, width * height, (short) 0);
        int bands = Math.max(1, Math.min(height, Runtime.getRuntime().availableProcessors()));
        if (!parallel || parts.size() < 64 || bands == 1) {
            drawBand(parts, scale, offsetX, offsetY, width, height, 0, height, cover, lineWidth, cancelled);
        } else {
            IntStream.range(0, bands).parallel().forEach(band ->
                    drawBand(parts, scale, offsetX, offsetY, width, height, height * band / bands,
                            height * (band + 1) / bands, cover, lineWidth, cancelled));
        }
        return !cancelled.getAsBoolean();
    }

    private static void drawBand(List<PlotDrawableIndex.Part> parts, double scale, double offsetX, double offsetY,
                                 int width, int height, int rowStart, int rowEnd, short[] cover, double lineWidth,
                                 java.util.function.BooleanSupplier cancelled) {
        double reach = lineWidth / 2 + 1;
        int visited = 0;
        for (PlotDrawableIndex.Part part : parts) {
            if ((++visited & 63) == 0 && cancelled.getAsBoolean()) {
                return;
            }
            // Skip parts that cannot touch this band's rows (screen y grows as world y falls).
            double top = offsetY - part.bounds().getMaxY() * scale;
            double bottom = offsetY - part.bounds().getMinY() * scale;
            if (bottom < rowStart - reach || top > rowEnd + reach) {
                continue;
            }
            drawGeometry(part.geometry(), scale, offsetX, offsetY, width, height, rowStart, rowEnd, cover, lineWidth);
        }
    }

    private static void drawGeometry(Geometry geometry, double scale, double offsetX, double offsetY, int width,
                                     int height, int rowStart, int rowEnd, short[] cover, double lineWidth) {
        if (geometry instanceof LineString line) {
            drawSequence(line.getCoordinateSequence(), scale, offsetX, offsetY, width, height, rowStart, rowEnd, cover,
                    lineWidth);
        } else if (geometry instanceof Polygon polygon) {
            // Same as the vector path of a stroke-only layer: the exterior ring.
            drawSequence(polygon.getExteriorRing().getCoordinateSequence(), scale, offsetX, offsetY, width, height,
                    rowStart, rowEnd, cover, lineWidth);
        } else if (geometry instanceof Point point && !point.isEmpty()) {
            double x = point.getX() * scale + offsetX;
            double y = offsetY - point.getY() * scale;
            dot(x, y, width, rowStart, rowEnd, cover, lineWidth);
        }
    }

    private static void drawSequence(CoordinateSequence sequence, double scale, double offsetX, double offsetY,
                                     int width, int height, int rowStart, int rowEnd, short[] cover,
                                     double lineWidth) {
        int size = sequence.size();
        if (size == 0) {
            return;
        }
        double previousX = sequence.getX(0) * scale + offsetX;
        double previousY = offsetY - sequence.getY(0) * scale;
        if (size == 1) {
            dot(previousX, previousY, width, rowStart, rowEnd, cover, lineWidth);
            return;
        }
        boolean round = lineWidth > ROUND_FROM_WIDTH;
        double radius = lineWidth / 2;
        if (round) {
            disc(previousX, previousY, radius, width, rowStart, rowEnd, cover);
        }
        double directionX = 0;
        double directionY = 0;
        boolean hasDirection = false;
        for (int i = 1; i < size; i++) {
            double x = sequence.getX(i) * scale + offsetX;
            double y = offsetY - sequence.getY(i) * scale;
            drawSegment(previousX, previousY, x, y, width, height, rowStart, rowEnd, cover, lineWidth);
            if (round) {
                double dx = x - previousX;
                double dy = y - previousY;
                double length = Math.hypot(dx, dy);
                if (length > 1e-9) {
                    dx /= length;
                    dy /= length;
                    // A join only shows when the path turns enough for the wedge to reach a visible width.
                    if (hasDirection) {
                        double cosine = directionX * dx + directionY * dy;
                        if (radius * Math.sqrt(Math.max(0, (1 - cosine) / 2)) > 0.12) {
                            disc(previousX, previousY, radius, width, rowStart, rowEnd, cover);
                        }
                    }
                    directionX = dx;
                    directionY = dy;
                    hasDirection = true;
                }
                if (i == size - 1) {
                    disc(x, y, radius, width, rowStart, rowEnd, cover);
                }
            }
            previousX = x;
            previousY = y;
        }
    }

    /**
     * A round cap or join: the disc of {@code radius} around the point, with the coverage a pixel gets from a disc
     * (the distance to its edge, clamped to a pixel). It is merged with {@code max}, not added: the body of the stroke
     * already covers most of the disc and adding would thicken its edges there.
     */
    private static void disc(double cx, double cy, double radius, int width, int rowStart, int rowEnd, short[] cover) {
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
            return;
        }
        int firstRow = (int) Math.max(rowStart, Math.floor(cy - radius - 1));
        int lastRow = (int) Math.min(rowEnd - 1, Math.ceil(cy + radius + 1));
        int firstColumn = (int) Math.max(0, Math.floor(cx - radius - 1));
        int lastColumn = (int) Math.min(width - 1, Math.ceil(cx + radius + 1));
        for (int row = firstRow; row <= lastRow; row++) {
            double dy = row + 0.5 - cy;
            for (int column = firstColumn; column <= lastColumn; column++) {
                double dx = column + 0.5 - cx;
                double edge = radius + 0.5 - Math.sqrt(dx * dx + dy * dy);
                if (edge > 0) {
                    short units = (short) Math.round(Math.min(1, edge) * UNITS);
                    int index = row * width + column;
                    if (cover[index] < units) {
                        cover[index] = units;
                    }
                }
            }
        }
    }

    /** A single point: a small square of the stroke width, put in the pixel it falls in. */
    private static void dot(double x, double y, int width, int rowStart, int rowEnd, short[] cover, double lineWidth) {
        if (lineWidth > ROUND_FROM_WIDTH) {
            disc(x, y, lineWidth / 2, width, rowStart, rowEnd, cover);
            return;
        }
        int column = (int) Math.floor(x);
        int row = (int) Math.floor(y);
        if (column >= 0 && column < width && row >= rowStart && row < rowEnd) {
            add(cover, row * width + column, Math.min(1, lineWidth * lineWidth));
        }
    }

    /**
     * Clips the segment to the whole image (so a segment far outside the view costs nothing and every band sees the very
     * same clipped segment), then walks it one pixel of its major axis at a time, adding to each pixel crossed the area
     * of the stroke inside it. A band only handles the pixels of its own rows, from the same samples, so the result
     * does not depend on how many bands there are.
     */
    private static void drawSegment(double x0, double y0, double x1, double y1, int width, int height, int rowStart,
                                    int rowEnd, short[] cover, double lineWidth) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
            return;
        }
        // The stroke reaches half a width past the segment: clip a little beyond the image so its edge pixels count.
        double margin = lineWidth / 2 + 1;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double cx = x0;
        double cy = y0;
        double sx = dx;
        double sy = dy;
        boolean inside = x0 >= -margin && x0 <= width + margin && x1 >= -margin && x1 <= width + margin
                && y0 >= -margin && y0 <= height + margin && y1 >= -margin && y1 <= height + margin;
        if (!inside) {
            // Liang-Barsky, written out (this runs for millions of segments: no arrays, no allocation).
            double t0 = 0;
            double t1 = 1;
            for (int side = 0; side < 4; side++) {
                double pp = side == 0 ? -dx : side == 1 ? dx : side == 2 ? -dy : dy;
                double qq = side == 0 ? x0 + margin : side == 1 ? width + margin - x0
                        : side == 2 ? y0 + margin : height + margin - y0;
                if (pp == 0) {
                    if (qq < 0) {
                        return;
                    }
                } else {
                    double t = qq / pp;
                    if (pp < 0) {
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
            cx = x0 + t0 * dx;
            cy = y0 + t0 * dy;
            sx = (x0 + t1 * dx) - cx;
            sy = (y0 + t1 * dy) - cy;
        }
        double length = Math.hypot(sx, sy);
        if (length < 1e-9) {
            dot(cx, cy, width, rowStart, rowEnd, cover, lineWidth);
            return;
        }
        boolean xMajor = Math.abs(sx) >= Math.abs(sy);
        double major = xMajor ? Math.abs(sx) : Math.abs(sy);
        // Half the stroke measured along the minor axis: a slanted line is wider there by length / major.
        double half = lineWidth / 2 * length / major;
        // Major coordinate p (columns for an x-major segment, rows otherwise) and minor coordinate q, p increasing.
        double p0 = xMajor ? cx : cy;
        double p1 = xMajor ? cx + sx : cy + sy;
        double q0 = xMajor ? cy : cx;
        double q1 = xMajor ? cy + sy : cx + sx;
        if (p0 > p1) {
            double swap = p0;
            p0 = p1;
            p1 = swap;
            swap = q0;
            q0 = q1;
            q1 = swap;
        }
        double dp = p1 - p0;
        double dq = q1 - q0;
        int first = (int) Math.floor(p0);
        int last = Math.max(first, (int) Math.ceil(p1) - 1);
        if (xMajor) {
            // Columns run along the major axis and rows along the minor one: keep the columns whose stroke can reach the
            // band's rows (the stroke reaches half a width past the centre line).
            if (Math.abs(dq) > 1e-12) {
                double ta = (rowStart - half - q0) / dq;
                double tb = (rowEnd + half - q0) / dq;
                // Clamp in double before the cast: a nearly horizontal segment gives |t| around 1e11 and more, which an
                // int cast saturates and the "- 1" then wraps around, dropping the whole segment.
                double pa = p0 + Math.min(ta, tb) * dp;
                double pb = p0 + Math.max(ta, tb) * dp;
                first = (int) Math.max(first, Math.min(last + 1, Math.floor(pa) - 1));
                last = (int) Math.min(last, Math.max(first - 1, Math.floor(pb) + 1));
            } else if (q0 + half < rowStart || q0 - half >= rowEnd) {
                return;
            }
        } else {
            // Rows are the major axis: this band owns exactly its own rows.
            first = Math.max(first, rowStart);
            last = Math.min(last, rowEnd - 1);
        }
        // Exact integration along the major axis: each pixel cell the segment crosses gets the length of segment inside
        // it (at most one pixel), at the segment's minor position in the middle of that stretch.
        // A slanted stroke crosses a pixel cell as a parallelogram, not a rectangle: split the cell in as many parts as
        // the slope needs (1 for a nearly straight line, up to 4 at 45 degrees) and add each at its own minor position.
        double slope = Math.abs(dq / dp);
        int parts = (int) Math.max(1, Math.min(4, Math.ceil(slope * 4)));
        for (int c = first; c <= last; c++) {
            double lo = Math.max(p0, c);
            double hi = Math.min(p1, c + 1);
            if (hi - lo <= 0) {
                continue;
            }
            double piece = (hi - lo) / parts;
            for (int k = 0; k < parts; k++) {
                double pieceMid = lo + (k + 0.5) * piece;
                double q = q0 + (pieceMid - p0) / dp * dq;
                if (xMajor) {
                    addColumn(cover, width, rowStart, rowEnd, c, q - half, q + half, piece);
                } else {
                    addRow(cover, width, rowStart, rowEnd, c, q - half, q + half, piece);
                }
            }
        }
    }

    /** Adds the area of the vertical interval [low, high) in column {@code column}, weighted by {@code extent}. */
    private static void addColumn(short[] cover, int width, int rowStart, int rowEnd, int column, double low,
                                  double high, double extent) {
        if (column < 0 || column >= width) {
            return;
        }
        int first = Math.max(rowStart, (int) Math.floor(low));
        int last = Math.min(rowEnd - 1, (int) Math.floor(high));
        for (int row = first; row <= last; row++) {
            double overlap = Math.min(high, row + 1) - Math.max(low, row);
            if (overlap > 0) {
                add(cover, row * width + column, overlap * extent);
            }
        }
    }

    /** Adds the area of the horizontal interval [low, high) in row {@code row}, weighted by {@code extent}. */
    private static void addRow(short[] cover, int width, int rowStart, int rowEnd, int row, double low, double high,
                               double extent) {
        if (row < rowStart || row >= rowEnd) {
            return;
        }
        int first = Math.max(0, (int) Math.floor(low));
        int last = Math.min(width - 1, (int) Math.floor(high));
        for (int column = first; column <= last; column++) {
            double overlap = Math.min(high, column + 1) - Math.max(low, column);
            if (overlap > 0) {
                add(cover, row * width + column, overlap * extent);
            }
        }
    }

    private static void add(short[] cover, int index, double area) {
        int units = (int) (area * UNITS + 0.5);
        if (units > 0) {
            cover[index] = (short) Math.min(Short.MAX_VALUE, cover[index] + units);
        }
    }

    /** The coverage as premultiplied ARGB pixels for a colour with the given 0..1 channels and opacity. */
    static void toPremultipliedArgb(short[] cover, int pixels, double red, double green, double blue, double opacity,
                                    int[] out) {
        double a = Math.min(1, Math.max(0, opacity));
        int[] table = new int[MAX_COVERAGE * UNITS + 1];
        for (int u = 1; u < table.length; u++) {
            double s = (double) u / UNITS;
            double alpha = s <= 1 ? a * s : 1 - Math.pow(1 - a, s);
            int alpha8 = (int) Math.round(alpha * 255);
            table[u] = (alpha8 << 24) | ((int) Math.round(red * alpha * 255) << 16)
                    | ((int) Math.round(green * alpha * 255) << 8) | (int) Math.round(blue * alpha * 255);
        }
        int chunks = Math.max(1, Math.min(8, pixels / 100_000 + 1));
        IntStream.range(0, chunks).parallel().forEach(chunk -> {
            int from = (int) ((long) pixels * chunk / chunks);
            int to = (int) ((long) pixels * (chunk + 1) / chunks);
            for (int i = from; i < to; i++) {
                int u = cover[i];
                out[i] = u <= 0 ? 0 : table[Math.min(u, table.length - 1)];
            }
        });
    }
}
