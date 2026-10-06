package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class DrillingPositionsTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final DrillGCodeParameters params = new DrillGCodeParameters(3, 1, 100, 0, true);
    private ExcellonImage image() {
        return ExcellonImage.of("MM", Map.of(1, 1.0, 2, 2.0),
                List.of(new ExcellonImage.Drill(1, 10, 0), new ExcellonImage.Drill(2, 15, 0)), List.of(), factory.createPolygon());
    }
    private GCodeGenerator.DrillJobOptions options() {
        return new GCodeGenerator.DrillJobOptions(true, 15, .5, null, null);
    }
    private CncJobResult generate(GCodeGenerator.DrillJobOptions options) {
        return GCodeGenerator.generateDrillCncJob(image(), Map.of(1, params, 2, params), List.of(1, 2), options);
    }

    @ParameterizedTest @ValueSource(doubles = {0, 1, 3, 20})
    void startZIsInitialOnlyAndTravelHeightPrecedesAllXy(double z) {
        String code = generate(options().withPositions(z, null, null)).gcode();
        assertTrue(code.contains("G94\nG0 Z" + GCodeGenerator.fmt(z) + "\n"), code);
        int firstXy = code.indexOf("G0 X");
        assertTrue(code.lastIndexOf("G0 Z3.0000", firstXy) >= 0, code);
        assertFalse(code.substring(firstXy).contains("G0 Z" + GCodeGenerator.fmt(z)) && z != 3 && z != 15, code);
    }

    @Test void explicitPositionCoversInitialPortableToolAndEveryReplacementAndPreview() {
        var job = generate(options().withPositions(20.0, 0.0, 5.0));
        String code = job.gcode();
        assertEquals(2, code.lines().filter(line -> line.equals("G0 X0.0000 Y5.0000")).count(), code);
        assertEquals(2, code.lines().filter(line -> line.startsWith("M0")).count(), code);
        assertTrue(code.indexOf("G0 X0.0000 Y5.0000") < code.indexOf("M0"), code);
        assertTrue(code.indexOf("M5") < code.indexOf("G0 X0.0000 Y5.0000"), code);
        assertTrue(code.contains("G90\nG0 Z3.0000"), code);
        assertTrue(job.travelGeometry().covers(factory.createPoint(new Coordinate(0, 5))));
        var parsed = GCodeToolpathParser.parse(code, CancellationToken.none(), ProgressCallback.none());
        assertTrue(parsed.travelCenterlines().covers(factory.createPoint(new Coordinate(0, 5))));
        assertEquals(2, parsed.cutGeometry().getNumGeometries());
    }

    @ParameterizedTest @EnumSource(CncExclusionArea.Strategy.class)
    void toolChangeTravelRespectsAroundAndOver(CncExclusionArea.Strategy strategy) {
        var area = CncExclusionArea.of(factory.toGeometry(new Envelope(4, 6, -1, 1)), strategy, 25);
        var job = generate(options().withPositions(20.0, 0.0, 0.0).withExclusions(true, List.of(area)));
        if (strategy == CncExclusionArea.Strategy.AROUND) assertFalse(job.travelGeometry().intersects(area.geometry()));
        else assertTrue(job.gcode().contains("G0 Z25.0000\nG0 X0.0000 Y0.0000\nG0 Z15.0000\nM0"), job.gcode());
    }

    @Test void moveAwayWithOldDrillBeforeInstallingAWiderTool() {
        var area = CncExclusionArea.of(factory.toGeometry(new Envelope(10.8, 12, -1, 1)), CncExclusionArea.Strategy.AROUND, 0);
        var image = ExcellonImage.of("MM", Map.of(1, .2, 2, 2.0),
                List.of(new ExcellonImage.Drill(1, 10, 0), new ExcellonImage.Drill(2, 15, 0)), List.of(), factory.createPolygon());
        var defaults = Map.of(1, params, 2, params);
        var old = options().withExclusions(true, List.of(area));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image, defaults, List.of(1, 2), old));
        assertDoesNotThrow(() -> GCodeGenerator.generateDrillCncJob(image, defaults, List.of(1, 2), old.withPositions(null, 0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image, defaults, List.of(1, 2), old.withPositions(null, 10.0, 0.0)));
    }

    @Test void changeHeightCannotBeBelowAnySelectedTravelHeightEvenWithoutExclusions() {
        var tooLow = new GCodeGenerator.DrillJobOptions(true, 2, .5, null, null).withPositions(null, 0.0, 5.0);
        assertThrows(IllegalArgumentException.class, () -> generate(tooLow));
    }

    @Test void optionalValuesAndDisabledChangesCannotBeSilentlyIgnored() {
        for (double z : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> options().withPositions(z, null, null));
        assertThrows(IllegalArgumentException.class, () -> options().withPositions(null, 1.0, null));
        assertThrows(IllegalArgumentException.class, () -> options().withPositions(null, Double.NaN, 1.0));
        var disabled = new GCodeGenerator.DrillJobOptions(false, 15, .5, null, null).withPositions(null, 1.0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> generate(disabled));
        assertEquals(generate(options()).gcode(), generate(options().withPositions(null, null, null)).gcode());
    }

    @ParameterizedTest @EnumSource(value = GCodePreprocessor.class, names = {"GRBL_LASER", "MARLIN_LASER_FAN_PIN",
            "MARLIN_LASER_SPINDLE_PIN", "Z_LASER", "HPGL", "ROLAND_MDX_20", "TOOLCHANGE_PROBE_MACH3"}, mode = EnumSource.Mode.EXCLUDE)
    void eachOrdinaryMillingProfileIncludesAndParsesConfiguredPositions(GCodePreprocessor profile) {
        var options = new GCodeGenerator.DrillJobOptions(profile.supportsManualToolChange(), 15, .5, null, null)
                .withPositions(20.0, 0.0, 5.0);
        var job = GCodeGenerator.generateDrillCncJob(image(), Map.of(1, params, 2, params), List.of(1, 2), options, profile);
        var parsed = GCodeToolpathParser.parse(job.gcode(), CancellationToken.none(), ProgressCallback.none());
        assertTrue(parsed.plotAvailable(), parsed.warning());
        assertTrue(parsed.travelCenterlines().covers(factory.createPoint(new Coordinate(0, 5))), job.gcode());
        assertFalse(parsed.cutGeometry().isEmpty());
    }

    @Test void rolandAndProbeDoNotAcceptSeparatePositionsButKeepTheirExistingDefaults() {
        for (var profile : List.of(GCodePreprocessor.ROLAND_MDX_20, GCodePreprocessor.TOOLCHANGE_PROBE_MACH3)) {
            assertThrows(IllegalArgumentException.class, () -> options().withPositions(20.0, null, null).validatePositions(profile));
            assertThrows(IllegalArgumentException.class, () -> options().withPositions(null, 0.0, 0.0).validatePositions(profile));
            assertDoesNotThrow(() -> options().validatePositions(profile));
        }
    }

    @Test void inchPositionsStayInSourceUnitsIncludingOverHeightAndClearance() {
        var image = ExcellonImage.of("IN", Map.of(1, .04), List.of(new ExcellonImage.Drill(1, 1, 0)), List.of(), factory.createPolygon());
        var area = CncExclusionArea.of(factory.toGeometry(new Envelope(.4, .6, -.1, .1)), CncExclusionArea.Strategy.OVER, 1);
        var params = new DrillGCodeParameters(.12, .04, 4, 0, true);
        var options = new GCodeGenerator.DrillJobOptions(true, .6, .02, null, null)
                .withPositions(.8, 0.0, .2).withExclusions(true, List.of(area));
        String code = GCodeGenerator.generateDrillCncJob(image, Map.of(1, params), List.of(1), options).gcode();
        assertTrue(code.contains("G20\n"), code);
        assertTrue(code.contains("G94\nG0 Z0.8000\nG0 Z0.1200"), code);
        assertTrue(code.contains("G0 Z0.6000\nG0 X0.0000 Y0.2000"), code);
        assertTrue(code.contains("G0 Z1.0000\nG0 X1.0000 Y0.0000\nG0 Z0.1200"), code);
    }
}
