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
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class Mach3ProbeTest {
    private static final GCodePreprocessor PROFILE = GCodePreprocessor.TOOLCHANGE_PROBE_MACH3;
    private static final ProbeToolChangeParameters PROBE = new ProbeToolChangeParameters(15, -5, 50, 0, 7.0, 11.0);

    private static List<ToolGeometry> tools() {
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1, 2), new Coordinate(4, 2)});
        return List.of(new ToolGeometry(0.8, path));
    }

    private static GeometryGCodeParameters parameters(boolean change, int rpm, ProbeToolChangeParameters probe) {
        return new GeometryGCodeParameters(3, 1, true, 0.5, 300, rpm, change, 0, probe);
    }

    private static CncJobResult geometry(String units, GeometryGCodeParameters parameters) {
        return GCodeGenerator.generateGeometryCncJob(units, tools(), parameters, Map.of(), CancellationToken.none(), PROFILE);
    }

    @Test
    void twoProbesEachRequireContactConfirmationBeforeSettingZ() {
        String cycle = PROBE.cycle(1, 0.8, "MM", 3);
        assertTrue(cycle.startsWith("M05\nG00 Z15.0000\nM6\nM05\nG90\nG21\nG17\nG94\nG00 Z15.0000\nG00 X7.0000 Y11.0000\n"));
        assertEquals(2, cycle.lines().filter(line -> line.startsWith("G31")).count());
        assertEquals(4, cycle.lines().filter(line -> line.equals("M0")).count());
        assertTrue(cycle.contains("G31 Z-5.0000 F50.0000\n(MSG, Confirme contato real da sonda; se nao houve contato ABORTE sem continuar)\nM0\nG92 Z0.0000\nG00 Z1.5000\n"));
        assertTrue(cycle.contains("G31 Z-5.0000 F25.0000\n(MSG, Confirme contato real da sonda; se nao houve contato ABORTE sem continuar)\nM0\nG92 Z0.0000\nG00 Z3.0000\n"));
        assertTrue(cycle.endsWith("(MSG, Remova a placa e todos os clips da sonda antes de continuar)\nM0\n"));
        assertFalse(cycle.contains("M03"));
    }

    @Test
    void plateContactHeightAndOptionalXyAreExplicit() {
        var probe = new ProbeToolChangeParameters(15, -4, 40, 1, null, null);
        String cycle = probe.cycle(3, 0.8, "MM", 3);
        assertTrue(cycle.contains("G92 Z1.0000\nG00 Z2.0000"));
        assertTrue(cycle.contains("G92 Z1.0000\nG00 Z3.0000"));
        assertFalse(cycle.contains("G00 X"));
    }

    @Test
    void singleToolRequiresCycleAndRestartsSpindleOnlyAfterRemovingProbe() {
        var job = geometry("MM", parameters(true, 10000, PROBE));
        String code = job.gcode();
        assertTrue(code.contains("T1\nM05\nG00 Z15.0000\nM6"));
        assertTrue(code.contains("Remova a placa e todos os clips da sonda antes de continuar)\nM0\n"));
        int remove = code.indexOf("Remova a placa");
        assertTrue(code.indexOf("M03 S10000") > remove);
        assertTrue(code.contains("G01 Z-0.5000 F300.0000"));
        assertTrue(code.contains("G01 Z-1.0000 F300.0000"));
        assertTrue(job.cutGeometry().isEmpty());
        assertTrue(job.travelGeometry().isEmpty());
        var parsed = GCodeToolpathParser.parse(code, CancellationToken.none(), ignored -> {});
        assertFalse(parsed.plotAvailable());
        assertTrue(parsed.warning().contains("G31/G92"));
        assertNull(parsed.stats());
        assertNull(parsed.cutGeometry());
        assertNull(parsed.cutCenterlines());
    }

    @Test
    void multipleGeometryToolsReprobeEachAndKeepToolOrder() {
        var first = tools().get(0);
        var job = GCodeGenerator.generateGeometryCncJob("MM", List.of(first, new ToolGeometry(1, first.geometry())),
                parameters(true, 10000, PROBE), Map.of(), CancellationToken.none(), PROFILE);
        assertEquals(4, job.gcode().lines().filter(line -> line.startsWith("G31")).count());
        assertTrue(job.gcode().indexOf("T1\n") < job.gcode().indexOf("T2\n"));
        assertEquals(2, job.gcode().lines().filter(line -> line.equals("M03 S10000")).count());
    }

    @Test
    void drillingKeepsSlotsDepthPassesAndReprobesInRequestedOrder() {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0", "%", "T1",
                "X1.0Y1.0", "T2", "X3.0Y3.0", "X5.0Y5.0G85X7.0Y5.0", "M30"));
        var params = new DrillGCodeParameters(3, 1, 150, 9000, true, true, 0.5, true, 1, 0);
        var options = new GCodeGenerator.DrillJobOptions(true, 15, 3, 8.0, 9.0, 0, PROBE);
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, params, 2, params), List.of(2, 1), options, PROFILE);
        String code = job.gcode();
        assertEquals(4, code.lines().filter(line -> line.startsWith("G31")).count());
        assertTrue(code.indexOf("T2\n") < code.indexOf("T1\n"));
        assertTrue(code.contains("G01 X7.0000 Y5.0000 F150.0000"));
        assertTrue(code.contains("G01 Z-0.5000 F150.0000"));
        assertTrue(code.contains("G01 Z-1.0000 F150.0000"));
        assertTrue(code.contains("M03 S9000\nG4 P1.0000"));
        assertTrue(code.contains("G00 X8.0000 Y9.0000"));
        assertTrue(job.cutGeometry().isEmpty());
        assertFalse(GCodeToolpathParser.parse(code, CancellationToken.none(), ignored -> {}).plotAvailable());
    }

    @Test
    void missingConfigurationDisabledToolChangeAndZeroRpmCannotFallBackToOrdinaryMilling() {
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", parameters(true, 10000, null)));
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", parameters(false, 10000, PROBE)));
        assertThrows(IllegalArgumentException.class, () -> geometry("MM", parameters(true, 0, PROBE)));
        assertThrows(IllegalArgumentException.class, () -> PROFILE.pauseForTool(1, 0.8, "MM", 3, 100));
        assertThrows(IllegalArgumentException.class, () -> geometry("UNKNOWN", parameters(true, 10000, PROBE)));
    }

    @Test
    void oldConstructorsStillHaveNoProbing() {
        assertNull(new GeometryGCodeParameters(3, 1, false, 1, 100, 10000, false).probing());
        assertNull(new GeometryGCodeParameters(3, 1, false, 1, 100, 10000, false, 0).probing());
        assertNull(new GCodeGenerator.DrillJobOptions(false, 15, 3, null, null).probing());
        assertNull(new GCodeGenerator.DrillJobOptions(false, 15, 3, null, null, 0).probing());
    }

    @Test
    void drillingAlsoRejectsMissingCycleZeroRpmAndUnsafePerToolClearance() {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X1.0Y1.0", "M30"));
        var params = new DrillGCodeParameters(3, 1, 100, 9000, true);
        var options = new GCodeGenerator.DrillJobOptions(true, 15, 3, null, null, 0, PROBE);
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image, Map.of(1, params), List.of(1),
                new GCodeGenerator.DrillJobOptions(true, 15, 3, null, null), PROFILE));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image, Map.of(1, params), List.of(1),
                new GCodeGenerator.DrillJobOptions(false, 15, 3, null, null, 0, PROBE), PROFILE));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, true)), List.of(1), options, PROFILE));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, new DrillGCodeParameters(16, 1, 100, 9000, true)), List.of(1), options, PROFILE));
    }

    @Test
    void directIsolationAndCutoutMustUseGeometryToSupplyProbingSettings() {
        // Unsupported profiles must fail before trying to traverse source paths.
        var isolationError = assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateIsolationCncJob(null,
                new IsolationGCodeParameters(3, 1, 100, 9000), 0.8, CancellationToken.none(), PROFILE));
        assertTrue(isolationError.getMessage().contains("Geometry"));
        var cutoutError = assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateCutoutCncJob(null,
                new CutoutGCodeParameters(3, 1, false, 1, 100, 9000), 0.8, CancellationToken.none(), PROFILE));
        assertTrue(cutoutError.getMessage().contains("Geometry"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, Double.NaN, Double.POSITIVE_INFINITY, -0.00001})
    void invalidOrUnrepresentableProbeDepthIsRejected(double depth) {
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, depth, 50, 0, null, null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.00001})
    void invalidOrUnrepresentableProbeFeedIsRejected(double feed) {
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, feed, 0, null, null));
    }

    @Test
    void unsafeClearanceContactAndCoordinatePairsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(0, -5, 50, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, 50, -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, 50, Double.NaN, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, 50, 0, 1.0, null));
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, 50, 0, 1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PROBE.validateTravelZ(16));
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, 50, 3, null, null).validateTravelZ(3));
        assertThrows(IllegalArgumentException.class, () -> new ProbeToolChangeParameters(15, -5, 50, 0.9999, null, null).validateTravelZ(0.99995));
        assertThrows(IllegalArgumentException.class, () -> new GCodeGenerator.DrillJobOptions(true, 14, 3, null, null, 0, PROBE));
    }

    @Test
    void inchesUseUserSuppliedLengthsAndFeedsWithoutMetricConversion() {
        var probe = new ProbeToolChangeParameters(0.6, -0.2, 2, 0.04, null, null);
        var params = new GeometryGCodeParameters(0.1, 0.004, false, 1, 12, 9000, true, 0, probe);
        String code = geometry("IN", params).gcode();
        assertTrue(code.contains("G20"));
        assertFalse(code.contains("G21"));
        assertTrue(code.contains("G31 Z-0.2000 F2.0000"));
        assertTrue(code.contains("G31 Z-0.2000 F1.0000"));
        assertTrue(code.contains("G92 Z0.0400\nG00 Z0.0700"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"G31 Z-5 F50", "G92 Z0", "N10 G31 Z-5.0 F50", "G92 X0 Y0"})
    void importedContactOrOriginChangeNeverProducesInventedPreview(String command) {
        var parsed = GCodeToolpathParser.parse("G21\nG90\nG0 Z3\n" + command + "\nG1 Z-1 F100\nG1 X10 Y10\n",
                CancellationToken.none(), ignored -> {});
        assertFalse(parsed.plotAvailable());
        assertNull(parsed.stats());
        assertNull(parsed.travelGeometry());
        assertNull(parsed.cutGeometry());
    }

    @Test
    void cancelledGeometryGenerationStillStops() {
        assertThrows(CancellationException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", tools(),
                parameters(true, 10000, PROBE), Map.of(), () -> true, PROFILE));
    }
}
