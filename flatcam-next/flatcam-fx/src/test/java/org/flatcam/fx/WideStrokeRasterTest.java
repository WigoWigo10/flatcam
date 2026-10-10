package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.SnapshotParameters;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Wide CNC strokes drawn as a density image must match the Canvas stroke (round caps and joins, opaque ink): the
 * density path replaces the Canvas for them, so any visible difference is a regression.
 */
@EnabledOnOs(OS.WINDOWS)
class WideStrokeRasterTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final int SIZE = 240;

    private static Geometry path(double... xy) {
        Coordinate[] c = new Coordinate[xy.length / 2];
        for (int i = 0; i < c.length; i++) c[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
        return FACTORY.createLineString(c);
    }

    /** {meanAbsoluteAlphaDifference, fractionOfStrokePixelsDifferingBy0.25} of the density image against the Canvas. */
    private static double[] compare(Geometry geometry, double lineWidth) throws Exception {
        double[] result = new double[2];
        TerminalPanelTest.fx(() -> {
            Canvas canvas = new Canvas(SIZE, SIZE);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setStroke(Color.web("#5E6CFF"));
            gc.setLineWidth(lineWidth);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                var coordinates = ((org.locationtech.jts.geom.LineString) geometry.getGeometryN(i)).getCoordinates();
                gc.beginPath();
                gc.moveTo(coordinates[0].x, SIZE - coordinates[0].y);
                for (int k = 1; k < coordinates.length; k++) gc.lineTo(coordinates[k].x, SIZE - coordinates[k].y);
                gc.stroke();
            }
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            WritableImage image = canvas.snapshot(parameters, null);
            short[] cover = new short[SIZE * SIZE];
            DensityRaster.rasterize(new PlotDrawableIndex(geometry).visibleParts(null), 1, 0, SIZE, SIZE, SIZE, cover,
                    lineWidth);
            double sum = 0;
            int strokePixels = 0;
            int bad = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double reference = ((image.getPixelReader().getArgb(x, y) >>> 24) & 0xFF) / 255.0;
                    double density = Math.min(1, cover[y * SIZE + x] / (double) DensityRaster.UNITS);
                    double difference = Math.abs(reference - density);
                    sum += difference;
                    if (reference > 0 || density > 0) {
                        strokePixels++;
                        if (difference > 0.25) bad++;
                    }
                }
            }
            result[0] = sum / (SIZE * SIZE);
            result[1] = strokePixels == 0 ? 1 : bad / (double) strokePixels;
            System.out.printf("wide-stroke width=%.1f meanDiff=%.5f bad=%.4f pixels=%d%n", lineWidth, result[0], result[1],
                    strokePixels);
            return null;
        });
        return result;
    }

    @Test
    void roundCapsJoinsAndBendsMatchTheCanvas() throws Exception {
        Geometry zigzag = path(30, 40, 90, 120, 60, 160, 150, 190, 200, 60, 120, 30);
        for (double width : new double[] {3, 7, 15, 30}) {
            double[] difference = compare(zigzag, width);
            assertTrue(difference[0] < 0.004, "width " + width + " mean alpha difference " + difference[0]);
            assertTrue(difference[1] < 0.03, "width " + width + " pixels off by > 25%: " + difference[1]);
        }
    }

    @Test
    void parallelPassesAndASinglePointMatchToo() throws Exception {
        Geometry passes = FACTORY.createMultiLineString(new org.locationtech.jts.geom.LineString[] {
                (org.locationtech.jts.geom.LineString) path(20, 60, 220, 60),
                (org.locationtech.jts.geom.LineString) path(20, 80, 220, 80),
                (org.locationtech.jts.geom.LineString) path(20, 100, 120, 200)});
        double[] difference = compare(passes, 9);
        assertTrue(difference[0] < 0.004, "mean alpha difference " + difference[0]);
        assertTrue(difference[1] < 0.03, "pixels off by > 25%: " + difference[1]);
    }
}
