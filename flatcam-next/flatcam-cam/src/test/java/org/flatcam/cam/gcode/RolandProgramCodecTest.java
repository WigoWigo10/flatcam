package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class RolandProgramCodecTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static GCodeToolpathParser.Result parse(String code) {
        return GCodeToolpathParser.parse(code, CancellationToken.none(), ignored -> {});
    }

    private static List<ToolGeometry> tools() {
        return List.of(new ToolGeometry(0.8, FACTORY.createLineString(new Coordinate[]{
                new Coordinate(3, 7), new Coordinate(6, 7)})));
    }

    private static CncJobResult geometry(String units, List<ToolGeometry> tools, GeometryGCodeParameters parameters) {
        return GCodeGenerator.generateGeometryCncJob(units, tools, parameters, Map.of(),
                CancellationToken.none(), GCodePreprocessor.ROLAND_MDX_20);
    }

    @Test
    void nativeEncodingWritesEveryAxisMotorAndMillimetersPerSecond() {
        assertEquals(";;^IN;\n^PA;\n!MC0;\nV15.0;\nZ0.0,0.0,80.0;\n!MC1;\n"
                        + "V15.0;\nZ120.0,280.0,80.0;\nV2.0;\nZ120.0,280.0,-20.0;\n"
                        + "V2.0;\nZ240.0,280.0,-20.0;\n!MC0;\nV15.0;\nZ240.0,280.0,80.0;\n",
                RolandProgramCodec.encode("; Preprocessor: ROLAND_MDX_20\nG21\nG90\nG94\nM5\nG0 Z2\n"
                        + "M3 S12000\nG0 X3 Y7\nG1 Z-0.5 F120\nG1 X6 Y7 F120\nM5\nG0 Z2\n", 0));
    }

    @Test
    void geometryReopensWithSamePathAndNativeMotorStartsEvenWhenRpmIsZero() {
        var job = geometry("MM", tools(), new GeometryGCodeParameters(2, 0.5, false, 1, 120, 0, false, 600));
        assertTrue(job.gcode().contains("!MC1;"));
        assertTrue(job.gcode().endsWith("V10.0;\nZ240.0,280.0,80.0;\n"));
        assertFalse(job.gcode().contains("G21"));
        assertFalse(job.gcode().contains("FCFX"));
        assertFalse(job.gcode().contains("S12000"));
        var parsed = parse(job.gcode());
        assertNull(parsed.warning());
        assertEquals("MM", parsed.units());
        assertEquals(3, parsed.cutCenterlines().getLength(), 1e-9);
        assertFalse(parsed.cutGeometry().isEmpty());
        assertTrue(Double.isFinite(parsed.stats().estimatedMinutes()));
        assertEquals(job.gcode().lines().count(), parsed.lineCount());
        // RML has no embedded tool metadata: do not invent a cutter width on standalone reload.
        assertNull(parsed.stats().cutterDiameter());
        assertEquals(".rml", GCodePreprocessor.ROLAND_MDX_20.fileExtension());
        assertEquals(List.of("*.rml", "*.prn"), GCodePreprocessor.ROLAND_MDX_20.filePatterns());
    }

    @Test
    void drillsSlotsAndMultidepthAreVisibleWithExplicitXyzOnPlunges() {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1",
                "X3.0Y7.0", "X13.0Y14.0G85X16.0Y14.0", "M30"));
        var parameters = new DrillGCodeParameters(2, 0.6, 120, 0, false, true, 0.2, false, 0, 0);
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, parameters), List.of(1),
                new GCodeGenerator.DrillJobOptions(false, 15, 3, 20.0, 21.0, 600), GCodePreprocessor.ROLAND_MDX_20);
        var parsed = parse(job.gcode());
        assertNull(parsed.warning());
        assertFalse(parsed.cutGeometry().isEmpty());
        assertTrue(java.util.Arrays.stream(parsed.cutCenterlines().getCoordinates())
                .anyMatch(point -> point.x == 3 && point.y == 7));
        assertEquals(9, parsed.cutCenterlines().getLength(), 1e-9); // three passes along the slot
        assertTrue(job.gcode().endsWith("V10.0;\nZ800.0,840.0,120.0;\n"));
    }

    @Test
    void nativeLegacyMotorWithoutSemicolonAndSingleLineStreamsLoad() {
        var parsed = parse("\uFEFF;;^IN;^PA;!MC0\nV15;Z-120,280,80;!MC1\nV2;Z-120,280,-20;Z0,280,-20;!MC0\nV15;Z0,280,80;");
        assertNull(parsed.warning());
        assertEquals(3, parsed.cutCenterlines().getLength(), 1e-9);
        assertTrue(GCodeToolpathParser.isRolandProgram(";;^IN;\n^PA;"));
        assertFalse(GCodeToolpathParser.isRolandProgram("; ^IN not a native header\nG21\n"));
        assertFalse(GCodeToolpathParser.isHpglProgram(";;^IN;^PA;"));
    }

    @Test
    void actualFeedIncludingTravelIsTimedInsteadOfUsingG0Assumptions() {
        var parsed = parse(";;^IN;^PA;V1;Z0,0,40;Z240,0,40;V2;Z240,0,-40;Z480,0,-40;V1;Z480,0,40;");
        assertNull(parsed.warning());
        // Initial rise: 1s, travel: 6s, plunge: 1s, cut: 3s, retract: 2s.
        assertEquals(13.0 / 60, parsed.stats().estimatedMinutes(), 1e-12);
    }

    @Test
    void velocityRangeIsValidatedWithoutLegacyMinimumBugOrSilentClamping() {
        assertEquals(0.1, RolandProgramCodec.velocity(6));
        assertEquals(15, RolandProgramCodec.velocity(900));
        assertEquals(2.1, RolandProgramCodec.velocity(125));
        for (double value : new double[]{0, -1, 5.99, 900.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> RolandProgramCodec.velocity(value));
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", tools(),
                new GeometryGCodeParameters(2, 0.5, false, 1, 5, 0, false)));
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", tools(),
                new GeometryGCodeParameters(2, 0.5, false, 1, 100, 0, false, 1000)));
    }

    @Test
    void unitAndToolRestrictionsAreExplicitInsteadOfSilentChanges() {
        assertThrows(IllegalArgumentException.class, () -> geometry("IN", tools(),
                new GeometryGCodeParameters(0.1, 0.01, false, 1, 12, 0, false)));
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", List.of(tools().get(0), tools().get(0)),
                new GeometryGCodeParameters(2, 0.5, false, 1, 100, 0, false)));
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", tools(),
                new GeometryGCodeParameters(2, 0.5, false, 1, 100, 0, true)));
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%", "T1", "X1.0Y1.0", "T2", "X2.0Y2.0", "M30"));
        var parameters = new DrillGCodeParameters(2, 0.5, 100, 0, false);
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, parameters, 2, parameters), List.of(1, 2),
                new GCodeGenerator.DrillJobOptions(false, 15, 3, null, null), GCodePreprocessor.ROLAND_MDX_20));
    }

    @Test
    void drillDwellIsRejectedWhileNoDwellIsKeptOff() {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X1.0Y1.0", "M30"));
        var parameters = new DrillGCodeParameters(2, 0.5, 100, 0, false, false, 0, true, 0.5, 0);
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, parameters), List.of(1), new GCodeGenerator.DrillJobOptions(false, 15, 3, null, null),
                GCodePreprocessor.ROLAND_MDX_20));
    }

    @Test
    void nativeReaderRefusesUnknownResetsRelativeCoordinatesAndMalformedCommands() {
        for (String command : List.of("^IN;", "^PR;", "!MC2;", "!MC10;", "V0;", "V16;", "VNaN;",
                "Z1,2;", "Z1,2,3,4;", "Z1,no,3;", "Z1,,3;", "Z1,2,99999999999999999;",
                "Z1,2,3", "!ZZ1;", "PZ0,1;", "^O0,0,0;", "V1;Z1,2,3;^IN;")) {
            assertThrows(IllegalArgumentException.class, () -> parse(";;^IN;^PA;V1;" + command), command);
        }
        assertThrows(IllegalArgumentException.class, () -> parse(";;^IN;Z1,2,3;"));
        assertThrows(IllegalArgumentException.class, () -> parse(";;^IN;^PA;Z1,2,3;"));
    }

    @Test
    void encoderDoesNotLeakUnsupportedGcodeOrUnrepresentableHeightsIntoRml() {
        for (String code : List.of("G20\n", "G91\n", "G2 X1 Y2\n", "G4 P1\n", "M6\n", "M0\n", "T2\n",
                "G0 X99999999999\n", "G0 Z0.0001\n", "G1 Z-0.0001 F100\n"))
            assertThrows(IllegalArgumentException.class, () -> RolandProgramCodec.encode(code, 0), code);
        assertEquals(120.1, RolandProgramCodec.coordinate(3.0025), 1e-9);
    }

    @Test
    void vToolNeedsItsSettingsAndUsesCalculatedCutDepth() {
        var vTool = new ToolGeometry(0.8, tools().get(0).geometry(), ToolProfile.V);
        var parameters = new GeometryGCodeParameters(2, 0.5, false, 1, 100, 0, false);
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", List.of(vTool), parameters));
        var job = GCodeGenerator.generateGeometryCncJob("MM", List.of(vTool), parameters,
                Map.of(0, new VTipSettings(0.1, 30)), CancellationToken.none(), GCodePreprocessor.ROLAND_MDX_20);
        assertNull(parse(job.gcode()).warning());
        assertEquals(3, parse(job.gcode()).cutCenterlines().getLength(), 1e-9);
    }

    @Test
    void decodingAndGeometryGenerationCheckCancellationAndProgressIsMonotonic() {
        String code = ";;^IN;^PA;V1;Z0,0,40;V2;Z0,0,-20;Z40,0,-20;";
        assertThrows(CancellationException.class, () -> GCodeToolpathParser.parse(code, () -> true, ignored -> {}));
        assertThrows(CancellationException.class, () -> RolandProgramCodec.encode("G21\nG90\nG0 Z2\n", 0, () -> true));
        assertThrows(CancellationException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", tools(),
                new GeometryGCodeParameters(2, 0.5, false, 1, 100, 0, false), Map.of(), () -> true,
                GCodePreprocessor.ROLAND_MDX_20));
        List<Double> progress = new ArrayList<>();
        assertTrue(GCodeToolpathParser.parse(code, CancellationToken.none(), progress::add).plotAvailable());
        assertEquals(1, progress.getLast());
        for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) >= progress.get(i - 1), progress.toString());
    }
}
