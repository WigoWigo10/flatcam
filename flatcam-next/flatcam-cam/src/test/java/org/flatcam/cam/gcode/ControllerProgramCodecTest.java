package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
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

class ControllerProgramCodecTest {
    private static GCodeToolpathParser.Result parse(String program) {
        return GCodeToolpathParser.parse(program, CancellationToken.none(), ignored -> {});
    }

    @Test
    void lineXyzPreservesDistinctCoordinatesAndWritesEveryAxisOnEveryMove() {
        String input = "G21\nG90\nG00 Z2\nG00 X3 Y7\nG01 Z-1 F100\nG01 X8 Y9 F100\nG00 Z2\n";
        String output = ControllerProgramCodec.explicitXyz(input);
        assertEquals("G21\nG90\nG00 X0.0000 Y0.0000 Z2.0000\nG00 X3.0000 Y7.0000 Z2.0000\n"
                + "G01 X3.0000 Y7.0000 Z-1.0000 F100.0000\nG01 X8.0000 Y9.0000 Z-1.0000 F100.0000\n"
                + "G00 X8.0000 Y9.0000 Z2.0000\n", output);
        assertNull(parse(output).warning());
        assertEquals(Math.hypot(5, 2), parse(output).cutCenterlines().getLength(), 1e-9);
    }

    @ParameterizedTest
    @EnumSource(value = GCodePreprocessor.class, names = {"LINE_XYZ", "ISEL_ICP_CNC"})
    void denseXyzAndNativeIcpDrillsKeepHitsSlotsAndToolDiameters(GCodePreprocessor profile) {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%",
                "T1", "X3.0Y7.0", "T2", "X10.0Y12.0", "X13.0Y14.0G85X16.0Y14.0", "M30"));
        var parameters = new DrillGCodeParameters(2, 0.6, 120, 12000, true, true, 0.2, true, 0.5, 0);
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, parameters, 2, parameters), List.of(1, 2),
                new GCodeGenerator.DrillJobOptions(true, 15, 3, 20.0, 21.0), profile);
        var parsed = parse(job.gcode());
        assertNull(parsed.warning(), job.gcode());
        assertEquals(2, parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::drills).sum());
        assertEquals(1, parsed.stats().tools().stream().mapToInt(GCodeToolpathParser.ToolUsage::slots).sum());
        assertEquals(0.8, parsed.stats().tools().get(0).diameter());
        assertEquals(1.0, parsed.stats().tools().get(1).diameter());
        assertEquals(job.gcode().lines().count(), parsed.lineCount());
        if (profile == GCodePreprocessor.LINE_XYZ) assertTrue(job.gcode().contains("X3.0000 Y7.0000 Z-0.2000"));
        else {
            assertTrue(job.gcode().contains("GETTOOL 2\nFASTABS Z15000"));
            assertTrue(job.gcode().contains("WAIT 500"));
            assertTrue(job.gcode().endsWith("FASTABS Z3000\nFASTABS X20000 Y21000\nPROGEND\n"));
            assertFalse(job.gcode().contains("WPCLEAR"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = GCodePreprocessor.class, names = {"LINE_XYZ", "ISEL_ICP_CNC"})
    void multiToolGeometryReopensWithIdenticalCutLengths(GCodePreprocessor profile) {
        var factory = new GeometryFactory();
        var line = factory.createLineString(new Coordinate[]{new Coordinate(3, 7), new Coordinate(6, 7)});
        var other = factory.createLineString(new Coordinate[]{new Coordinate(10, 12), new Coordinate(10, 16)});
        var job = GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(0.2, line), new ToolGeometry(0.4, other)),
                new GeometryGCodeParameters(2, 1, false, 1, 120, 12000, false),
                Map.of(), CancellationToken.none(), profile);
        var parsed = parse(job.gcode());
        assertNull(parsed.warning(), job.gcode());
        assertEquals(7, parsed.cutCenterlines().getLength(), 1e-9);
        assertFalse(parsed.cutGeometry().isEmpty());
        if (profile == GCodePreprocessor.ISEL_ICP_CNC) {
            // GETTOOL is required for each tool even when manual pausing is disabled.
            assertTrue(job.gcode().contains("GETTOOL 1\nFASTABS Z2000"));
            assertTrue(job.gcode().contains("GETTOOL 2\nFASTABS Z2000"));
            assertTrue(job.gcode().startsWith("IMF_PBL flatcam\n"));
            assertFalse(job.gcode().contains("\nM0\n"));
            assertFalse(job.gcode().contains("\nG21\n"));
        }
    }

    @Test
    void nativeIcpEncodingMatchesPythonUnitsForCoordinatesVelocityAndWait() {
        assertEquals("IMF_PBL flatcam\n; Preprocessor: ISEL_ICP_CNC\nSPINDLE OFF\n"
                        + "FASTABS Z2000\nGETTOOL 2\nSPINDLE CW RPM12000\nWAIT 500\n"
                        + "FASTABS X3000 Y7000\nVEL 2000\nMOVEABS Z-500\nVEL 2000\n"
                        + "MOVEABS X6000 Y7000\nSPINDLE OFF\nFASTABS Z2000\nPROGEND\n",
                ControllerProgramCodec.encodeIcp("; Preprocessor: ISEL_ICP_CNC\nG21\nG90\nG94\nM5\n"
                        + "G0 Z2\nT2\nM3 S12000\nG4 P0.5\nG0 X3 Y7\nG1 Z-0.5 F120\n"
                        + "G1 X6 Y7 F120\nM5\nG0 Z2\n"));
    }

    @Test
    void externalIcpSubsetWithCommentsNegativeCoordinatesAndCounterclockwiseSpindleLoads() {
        var parsed = parse("; native file\nIMF_PBL job\n; TOOL DIAMETER: 0.8 mm\n"
                + "FASTABS X-3000 Y7000 Z2000\nSPINDLE CCW RPM9000\nVEL 2000\n"
                + "MOVEABS Z-500\nMOVEABS X1000 Y7000\nWAIT 50\nSPINDLE OFF\n"
                + "FASTABS Z2000\nPROGEND\n");
        assertNull(parsed.warning());
        assertEquals("MM", parsed.units());
        assertEquals(4, parsed.cutCenterlines().getLength(), 1e-9);
        assertEquals(0.8, parsed.stats().cutterDiameter());
    }

    @Test
    void unknownOriginChangesMalformedMovesAndCommandsAfterEndAreRejected() {
        for (String command : List.of("WPCLEAR", "FASTREL X100", "HOME", "MOVEABS X0.5", "MOVEABS Q1",
                "MOVEABS X1 X2", "VEL 0", "WAIT -1", "GETTOOL -1", "SPINDLE RANDOM", "PROGEND ignored",
                "MOVEABS X9999999999999999999", "PROGEND\nFASTABS X1")) {
            assertThrows(IllegalArgumentException.class, () -> parse("IMF_PBL test\n" + command + "\n"), command);
        }
        assertThrows(IllegalArgumentException.class, () -> parse("FASTABS X1\nIMF_PBL test\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("IMF_PBL test\nIMF_PBL again\n"));
    }

    @Test
    void overflowAndUnrepresentableVelocitiesAreNotSilentlyClamped() {
        assertThrows(IllegalArgumentException.class, () -> ControllerProgramCodec.encodeIcp("G0 X3000000\n"));
        assertThrows(IllegalArgumentException.class, () -> ControllerProgramCodec.encodeIcp("G1 X1 F0.00001\n"));
        assertThrows(IllegalArgumentException.class, () -> ControllerProgramCodec.encodeIcp("G2 X1 Y2\n"));
    }

    @Test
    void icpRejectsInchesWhileLineXyzKeepsInchCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> GCodePreprocessor.ISEL_ICP_CNC.unitsCode("IN"));
        String code = ControllerProgramCodec.explicitXyz("G20\nG90\nG0 X1 Y2 Z0.1\nG1 Z-0.01 F12\nG1 X2 Y2\n");
        assertEquals("IN", parse(code).units());
        assertEquals(1, parse(code).cutCenterlines().getLength(), 1e-9);
        assertEquals(".imf", GCodePreprocessor.ISEL_ICP_CNC.fileExtension());
        assertEquals(List.of("*.imf"), GCodePreprocessor.ISEL_ICP_CNC.filePatterns());
        assertEquals(".nc", GCodePreprocessor.LINE_XYZ.fileExtension());
    }

    @Test
    void nativeDecodeChecksCancellationAndReportsMonotonicProgress() {
        String code = "IMF_PBL test\nFASTABS X0 Y0 Z2000\nVEL 2000\nMOVEABS Z-500\nMOVEABS X3000 Y0\nPROGEND\n";
        assertThrows(CancellationException.class, () -> GCodeToolpathParser.parse(code, () -> true, ignored -> {}));
        List<Double> progress = new ArrayList<>();
        var parsed = GCodeToolpathParser.parse(code, CancellationToken.none(), progress::add);
        assertNull(parsed.warning());
        assertEquals(1.0, progress.getLast());
        for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) >= progress.get(i - 1), progress.toString());
    }

    @Test
    void bomAndNativeHeaderAreRecognizedWithoutMistakingCommentsForAProgram() {
        assertTrue(GCodeToolpathParser.isIcpProgram("\uFEFFIMF_PBL test\nFASTABS X0 Y0\nPROGEND\n"));
        assertNull(parse("\uFEFFIMF_PBL test\nFASTABS X0 Y0\nPROGEND\n").warning());
        assertFalse(GCodeToolpathParser.isIcpProgram("; IMF_PBL not a header\nG21\n"));
    }
}
