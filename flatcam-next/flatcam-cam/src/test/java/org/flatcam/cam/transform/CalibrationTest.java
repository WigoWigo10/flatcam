package org.flatcam.cam.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.transform.Calibration.Factors;
import org.flatcam.cam.transform.Calibration.GCodeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class CalibrationTest {

    @TempDir
    Path directory;

    private static final double[][] POINTS = {{10, 20}, {110, 20}, {10, 120}, {110, 120}};

    @Test
    void noDeltaMeansNoCorrection() {
        Factors factors = Calibration.calculate(POINTS, null, null);
        assertEquals(1.0, factors.scaleX());
        assertEquals(1.0, factors.scaleY());
        assertEquals(0.0, factors.skewX());
        assertEquals(0.0, factors.skewY());
    }

    @Test
    void scaleAndSkewComeFromTheDeltas() {
        // 100 mm apart: a delta of 0.5 gives 1.005; a skew of 1 over 100 is atan(0.01).
        Factors factors = Calibration.calculate(POINTS, new double[] {0.5, 1}, new double[] {1, 0.25});
        assertEquals(1.005, factors.scaleX(), 1e-12);
        assertEquals(1.0025, factors.scaleY(), 1e-12);
        assertEquals(Math.toDegrees(Math.atan(0.01)), factors.skewX(), 1e-12);
        assertEquals(Math.toDegrees(Math.atan(0.01)), factors.skewY(), 1e-12);
        double[][] same = {{10, 20}, {10, 20}, {10, 120}, {110, 120}};
        assertThrows(IllegalArgumentException.class, () -> Calibration.calculate(same, new double[] {1, 0}, null));
        assertThrows(IllegalArgumentException.class, () -> Calibration.calculate(new double[][] {{0, 0}}, null, null));
    }

    @Test
    void pointsScaleAndSkewAboutTheFirstPoint() {
        double[][] scaled = Calibration.scalePoints(POINTS, 2, 3);
        assertEquals(10, scaled[0][0], 1e-9);
        assertEquals(20, scaled[0][1], 1e-9);
        assertEquals(210, scaled[1][0], 1e-9);
        assertEquals(320, scaled[2][1], 1e-9);
        double[][] skewed = Calibration.skewPoints(POINTS, 45, 0);
        assertEquals(110, skewed[2][0], 1e-9, "the top-left point moves right by its height");
        assertEquals(120, skewed[2][1], 1e-9);
        assertEquals(110, skewed[1][0], 1e-9);
    }

    @Test
    void theOperationsAreScaleThenSkewAboutTheOrigin() {
        List<TransformOp> operations = Calibration.operations(new Factors(2, 1, 0, 0), new Coordinate(10, 20));
        Coordinate moved = operations.get(1).apply(operations.get(0).apply(new Coordinate(110, 20)));
        assertEquals(210, moved.x, 1e-9);
        assertEquals(20, moved.y, 1e-9);
    }

    @Test
    void theVerificationGCodeVisitsThePointsInTheChosenOrder() {
        String top = Calibration.verificationGCode(POINTS, GCodeSettings.defaults());
        assertTrue(top.contains("G21\nG90\nG17\nG94"));
        String[] moves = top.lines().filter(line -> line.startsWith("G00 X")).toArray(String[]::new);
        assertEquals("G00 X10.0 Y20.0", moves[0]);
        assertEquals("G00 X10.0 Y120.0", moves[1]);
        assertEquals("G00 X110.0 Y20.0", moves[2]);
        assertEquals("G00 X110.0 Y120.0", moves[3]);
        assertEquals("G00 X0 Y0", moves[4]);
        assertTrue(top.endsWith("G00 Z15.0000\nM2"));
        assertEquals(4, top.lines().filter(line -> line.equals("G01 Z0.1000")).count());

        GCodeSettings bottom = new GCodeSettings(2, 0.1, true, 15, new double[] {5, 6}, false, true);
        String other = Calibration.verificationGCode(POINTS, bottom);
        assertTrue(other.contains("G20\n"));
        assertTrue(other.contains("M5\nG00 Z15.0000\nG00 X5.0 Y6.0\nM0\nG01 Z0\nM0\nG00 Z15.0000\nM0\n"));
        String[] order = other.lines().filter(line -> line.startsWith("G00 X1")).toArray(String[]::new);
        assertEquals("G00 X10.0 Y20.0", order[0]);
        assertEquals("G00 X110.0 Y20.0", order[1]);
        assertEquals("G00 X10.0 Y120.0", order[2]);
    }

    @Test
    void numbersPrintLikePythonFloats() {
        assertEquals("5.0", Calibration.number(5));
        assertEquals("12.3456", Calibration.number(12.3456));
        assertEquals("0.0001", Calibration.number(1e-4));
        assertEquals(1.2346, Calibration.round(1.23456, 4), 0);
    }

    @Test
    void clicksSnapToTheCentreOfADrillOrAFlashedPad() throws IOException {
        GeometryFactory factory = new GeometryFactory();
        ExcellonImage drills = ExcellonImage.of("MM", Map.of(1, 1.0), List.of(new ExcellonImage.Drill(1, 5, 5)), List.of(),
                factory.createPoint(new Coordinate(5, 5)).buffer(0.5, 16));
        Coordinate hit = Calibration.snap(drills, new Coordinate(5.2, 5.1));
        assertNotNull(hit);
        assertEquals(5, hit.x, 1e-6);
        assertNull(Calibration.snap(drills, new Coordinate(7, 7)));

        Path file = directory.resolve("pads.gbr");
        Files.writeString(file, "%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,2.0*%\n%ADD11C,0.5*%\nD10*\nX100000Y100000D03*\n"
                + "D11*\nX0Y0D02*\nX200000Y0D01*\nM02*\n");
        var gerber = new GerberParser().parse(file);
        Coordinate pad = Calibration.snap(gerber, new Coordinate(10.4, 10.2));
        assertNotNull(pad);
        assertEquals(10, pad.x, 1e-6);
        assertNull(Calibration.snap(gerber, new Coordinate(10, 0)), "a track is not a flashed pad");
    }
}
