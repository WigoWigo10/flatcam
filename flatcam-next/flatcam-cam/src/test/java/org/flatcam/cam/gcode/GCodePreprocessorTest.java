package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class GCodePreprocessorTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static List<ToolGeometry> tools() {
        return List.of(new ToolGeometry(0.2, FACTORY.createLineString(new Coordinate[]{
                new Coordinate(1, 2), new Coordinate(4, 2)})),
                new ToolGeometry(0.4, FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(7, 5), new Coordinate(7, 9)})));
    }

    private static CncJobResult geometry(GCodePreprocessor profile, int power, boolean pause) {
        return GCodeGenerator.generateGeometryCncJob("MM", tools(),
                new GeometryGCodeParameters(2, 1, false, 1, 100, power, pause, 900),
                Map.of(), CancellationToken.none(), profile);
    }

    private static GCodeToolpathParser.Result parse(String code) {
        return GCodeToolpathParser.parse(code, CancellationToken.none(), ignored -> {});
    }

    @Test
    void laserProfilesAreNotOfferedForDrilling() {
        assertEquals(8, GCodePreprocessor.millingProfiles().size());
        assertEquals(12, GCodePreprocessor.geometryProfiles().size());
        assertTrue(GCodePreprocessor.millingProfiles().stream().noneMatch(GCodePreprocessor::isLaser));
        assertEquals(4, GCodePreprocessor.geometryProfiles().stream().filter(GCodePreprocessor::isLaser).count());
    }

    @ParameterizedTest
    @EnumSource(value = GCodePreprocessor.class, names = {"MARLIN", "REPETIER", "BERTA_CNC"})
    void newMillingDialectsPreservePathsAndUseDistinctCommands(GCodePreprocessor profile) {
        String code = geometry(profile, 128, true).gcode();
        var parsed = parse(code);
        assertNull(parsed.warning(), code);
        assertEquals(7, parsed.cutCenterlines().getLength(), 1e-9);
        assertFalse(parsed.cutGeometry().isEmpty());
        switch (profile) {
            case MARLIN -> {
                assertTrue(code.contains("G0 X1.0000 Y2.0000 F900.0000"));
                assertTrue(code.contains("M400\nM5"));
                assertTrue(code.contains("T2\nM6"));
                assertFalse(code.contains("M30"));
            }
            case REPETIER -> {
                assertTrue(code.contains("M106 S128"));
                assertTrue(code.contains("M107"));
                assertTrue(code.contains("M400\nM84\n@pause"));
                assertFalse(code.lines().anyMatch(line -> line.matches("T\\d+|M6|M0|M30")));
            }
            case BERTA_CNC -> {
                assertTrue(code.contains("G17\nG91.1\nG64 P0.03\nM110\nG54"));
                assertTrue(code.endsWith("(Berta)\nM111\nM30\n(Berta)\n"));
                assertTrue(code.contains("M03 S128"));
            }
            default -> fail("Unexpected profile");
        }
    }

    @ParameterizedTest
    @EnumSource(value = GCodePreprocessor.class, names = {"MARLIN", "REPETIER", "BERTA_CNC"})
    void newDrillDialectsRoundTripHolesSlotsAndToolChanges(GCodePreprocessor profile) {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%",
                "T1", "X1.0Y1.0", "T2", "X3.0Y3.0", "X5.0Y5.0G85X7.0Y5.0", "M30"));
        var parameters = new DrillGCodeParameters(2, 0.7, 150, 128, true);
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, parameters, 2, parameters), List.of(1, 2),
                new GCodeGenerator.DrillJobOptions(true, 15, 3, 0.0, 0.0, 600), profile);
        var parsed = parse(job.gcode());
        assertNull(parsed.warning(), job.gcode());
        assertEquals(2, parsed.stats().tools().size());
        // The slot entry plunge is a third hit, later classified as a slot rather than a drill.
        assertEquals(3, parsed.stats().hits().size());
        assertEquals(2, parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::drills).sum());
        assertFalse(parsed.cutGeometry().isEmpty());
        if (profile.usesRapidFeed()) assertTrue(job.gcode().contains("G0 Z15.0000 F600.0000"));
        assertEquals(1, parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::slots).sum());
    }

    @ParameterizedTest
    @EnumSource(value = GCodePreprocessor.class, names = {"GRBL_LASER", "MARLIN_LASER_FAN_PIN",
            "MARLIN_LASER_SPINDLE_PIN", "Z_LASER"})
    void laserSwitchesOffBetweenPathsAndReopensAtPositiveZ(GCodePreprocessor profile) {
        var job = geometry(profile, 128, false);
        String code = job.gcode();
        assertTrue(code.contains("FCFX LASER"));
        assertFalse(code.contains("Z-"));
        assertFalse(code.lines().anyMatch(line -> line.matches("T\\d+|M6|M0")));
        boolean emitting = false;
        for (String line : code.lines().toList()) {
            if (line.startsWith(profile.spindleOn() + " S")) emitting = true;
            if (line.equals("M5") || line.equals("M107")) emitting = false;
            if (line.startsWith(profile.rapid() + " ")) assertFalse(emitting, "Laser ligado durante " + line);
        }
        var parsed = parse(code);
        assertNull(parsed.warning(), code);
        assertEquals(7, parsed.cutCenterlines().getLength(), 1e-9);
        assertEquals(job.cutGeometry().getEnvelopeInternal(), parsed.cutGeometry().getEnvelopeInternal());
        assertFalse(parsed.travelGeometry().isEmpty());
        assertTrue(parsed.stats().steps().stream().anyMatch(step -> !step.travel()));
        if (profile == GCodePreprocessor.Z_LASER) {
            assertEquals(3, code.lines().filter(line -> line.equals("G00 Z2.0000")).count());
        }
    }

    @Test
    void laserPowerAndUnsupportedOperationsAreRejectedBeforeExport() {
        assertThrows(IllegalArgumentException.class, () -> geometry(GCodePreprocessor.GRBL_LASER, 0, false));
        assertThrows(IllegalArgumentException.class, () -> geometry(GCodePreprocessor.MARLIN_LASER_FAN_PIN, 256, false));
        assertThrows(IllegalArgumentException.class, () -> geometry(GCodePreprocessor.REPETIER, 10000, false));
        assertThrows(IllegalArgumentException.class, () -> geometry(GCodePreprocessor.GRBL_LASER, 128, true));
        var point = new ToolGeometry(0.2, FACTORY.createPoint(new Coordinate(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", List.of(point),
                new GeometryGCodeParameters(2, 1, false, 1, 100, 128, false),
                Map.of(), CancellationToken.none(), GCodePreprocessor.GRBL_LASER));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", tools(),
                new GeometryGCodeParameters(2, 1, true, 0.1, 100, 128, false),
                Map.of(), CancellationToken.none(), GCodePreprocessor.GRBL_LASER));
    }

    @Test
    void laserCannotReachTheDrillingGeneratorEvenThroughTheApi() {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X1Y1", "M30"));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, new DrillGCodeParameters(2, 1, 100, 128, false)), List.of(1),
                new GCodeGenerator.DrillJobOptions(false, 2, 2, null, null), GCodePreprocessor.GRBL_LASER));
    }

    @Test
    void laserGenerationHonorsCancellation() {
        assertThrows(CancellationException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", tools(),
                new GeometryGCodeParameters(2, 1, false, 1, 100, 128, false),
                Map.of(), () -> true, GCodePreprocessor.GRBL_LASER));
    }

    @Test
    void rapidFeedDefaultRespectsInchUnitsAndInvalidRatesAreRejected() {
        String code = GCodeGenerator.generateGeometryCncJob("IN", tools(),
                new GeometryGCodeParameters(0.1, 0.01, false, 1, 12, 128, false),
                Map.of(), CancellationToken.none(), GCodePreprocessor.MARLIN).gcode();
        assertTrue(code.contains("G20"));
        assertTrue(code.contains("G0 Z0.1000 F59.0551"));
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryGCodeParameters(2, 1, false, 1, 100, 0, false, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new GCodeGenerator.DrillJobOptions(false, 2, 2, null, null, -1));
    }

    @Test
    void pythonLaserHeadersUsePowerInsteadOfTheSignOfZ() {
        String code = "(Preprocessor Geometry: GRBL_laser)\nG21\nG90\nG0 X0 Y0 Z2\n"
                + "M03 S128\nG1 X3 Y0 F100\nM5\nG1 X4 Y0\nM03 S0\nG1 X5 Y0\n"
                + "M03 S128\nG0 X6 Y0\nG1 X7 Y0\nM5\n";
        var parsed = parse(code);
        assertNull(parsed.warning());
        assertEquals(4, parsed.cutCenterlines().getLength(), 1e-9);
        assertEquals(3, parsed.travelCenterlines().getLength(), 1e-9);
    }

    @Test
    void fanLaserAndArcsRespectTheEmissionState() {
        var parsed = parse("; Preprocessor Geometry: Marlin_laser_FAN_pin\nG21\nG90\nG0 X1 Y0 Z2\n"
                + "M106 S128\nG3 X0 Y1 I-1 J0 F100\nM400\nM107\nG1 X0 Y2\n"
                + "M106 S0\nG1 X0 Y3\nM106 S128\nG1 X0 Y4\nM107\n");
        assertNull(parsed.warning());
        assertEquals(Math.PI / 2 + 1, parsed.cutCenterlines().getLength(), 0.01);
        assertEquals(2, parsed.travelCenterlines().getLength(), 1e-9);
    }

    @Test
    void ordinaryMillingStillUsesZEvenWithFanSpindleCommands() {
        var parsed = parse("; Preprocessor: REPETIER\nG21\nG0 X0 Y0 Z2\nM106 S128\n"
                + "G1 X1 Y0 F100\nG1 Z-1\nG1 X2 Y0\nM107\nG0 Z2\n");
        assertNull(parsed.warning());
        assertEquals(1, parsed.cutCenterlines().getLength(), 1e-9);
        assertEquals(1, parsed.travelCenterlines().getLength(), 1e-9);
    }
}
