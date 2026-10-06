package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class DrillingExclusionTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final DrillGCodeParameters parameters = new DrillGCodeParameters(3, 1, 100, 0, false);
    private ExcellonImage image(String units, Map<Integer, Double> diameters, List<ExcellonImage.Drill> drills,
                                List<ExcellonImage.Slot> slots) {
        return ExcellonImage.of(units, diameters, drills, slots, factory.createPolygon());
    }
    private ExcellonImage holes() {
        return image("MM", Map.of(1, 1.0), List.of(new ExcellonImage.Drill(1, 10, 0), new ExcellonImage.Drill(1, 12, 0)), List.of());
    }
    private CncExclusionArea area(double x1, double x2, double y1, double y2, CncExclusionArea.Strategy strategy, double z) {
        return CncExclusionArea.of(factory.toGeometry(new Envelope(x1, x2, y1, y2)), strategy, z);
    }
    private GCodeGenerator.DrillJobOptions options(CncExclusionArea... areas) {
        return new GCodeGenerator.DrillJobOptions(false, 15, .5, 0.0, 0.0).withExclusions(true, List.of(areas));
    }
    private CncJobResult generate(ExcellonImage image, GCodeGenerator.DrillJobOptions options) {
        return GCodeGenerator.generateDrillCncJob(image, Map.of(1, parameters), List.of(1), options);
    }

    @Test void aroundRoutesInitialAndParkingMovesAndBothPreviewsContainTheDetour() {
        var area = area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0);
        var job = generate(holes(), options(area));
        assertFalse(job.travelGeometry().intersects(area.geometry()));
        assertTrue(job.travelGeometry().getEnvelopeInternal().getMinY() < -1
                || job.travelGeometry().getEnvelopeInternal().getMaxY() > 1);
        var parsed = GCodeToolpathParser.parse(job.gcode(), CancellationToken.none(), ProgressCallback.none());
        assertFalse(parsed.travelCenterlines().intersects(area.geometry()));
        assertTrue(job.gcode().lastIndexOf("G0 Z0.5000") > job.gcode().lastIndexOf("G0 X0.0000 Y0.0000"));
    }

    @Test void overRaisesBeforeXyUsesMaximumHeightAndRestoresSafeZOnlyAtDestination() {
        var job = generate(holes(), options(area(4, 6, -1, 1, CncExclusionArea.Strategy.OVER, 8),
                area(7, 8, -1, 1, CncExclusionArea.Strategy.OVER, 12)));
        assertTrue(job.gcode().contains("G0 Z12.0000\nG0 X10.0000 Y0.0000\nG0 Z3.0000\nG1 Z-1.0000"), job.gcode());
        assertTrue(job.gcode().contains("G0 Z12.0000\nG0 X0.0000 Y0.0000\nG0 Z3.0000\nG0 Z0.5000"), job.gcode());
    }

    @Test void overNeverLowersTravelHeightAndMissingEndXyNeedsNoParkingRoute() {
        var area = area(4, 6, -1, 1, CncExclusionArea.Strategy.OVER, 2);
        var options = new GCodeGenerator.DrillJobOptions(false, 15, .5, null, null).withExclusions(true, List.of(area));
        String code = generate(holes(), options).gcode();
        assertFalse(code.contains("G0 Z2.0000"));
        assertTrue(code.contains("G0 Z3.0000\nG0 X10.0000 Y0.0000"));
    }

    @Test void holesIntersectingOrNearTheProtectedBorderAreRejectedForBothStrategies() {
        for (var strategy : CncExclusionArea.Strategy.values()) {
            assertThrows(IllegalArgumentException.class, () -> generate(holes(), options(area(9, 11, -1, 1, strategy, 10))));
            // Center is outside; the radius plus 0.1 mm margin still touches the area.
            assertThrows(IllegalArgumentException.class, () -> generate(holes(), options(area(8, 9.5, -1, 1, strategy, 10))));
        }
    }

    @Test void slotCrossingAnAreaIsRejectedEvenWithBothEndpointsOutside() {
        var image = image("MM", Map.of(1, 1.0), List.of(), List.of(new ExcellonImage.Slot(1, 10, 0, 20, 0)));
        for (var strategy : CncExclusionArea.Strategy.values())
            assertThrows(IllegalArgumentException.class, () -> generate(image, options(area(14, 16, -1, 1, strategy, 10))));
    }

    @Test void slotPassReturnsAreIncludedInTravelPreviewAndKeepTheConfiguredDepths() {
        var image = image("MM", Map.of(1, 1.0), List.of(), List.of(new ExcellonImage.Slot(1, 10, 0, 12, 0)));
        var parameters = new DrillGCodeParameters(3, 1, 100, 0, false, true, .5, false, 0, 0);
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, parameters), List.of(1),
                options(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0)));
        assertTrue(job.gcode().contains("G0 X10.0000 Y0.0000\nG1 Z-1.0000"), job.gcode());
        assertTrue(job.travelGeometry().covers(factory.createPoint(new Coordinate(11, 0))));
        assertFalse(job.travelGeometry().intersects(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0).geometry()));
    }

    @Test void onlySelectedToolsAreValidatedAndSourceDataIsUntouched() {
        var drills = List.of(new ExcellonImage.Drill(1, 10, 0), new ExcellonImage.Drill(2, 5, 0));
        var image = image("MM", Map.of(1, 1.0, 2, 1.0), drills, List.of());
        assertDoesNotThrow(() -> generate(image, options(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0))));
        assertEquals(drills, image.drills());
    }

    @Test void aWiderReplacementToolCannotBeInstalledAtAnUnsafePreviousHole() {
        var image = image("MM", Map.of(1, .2, 2, 2.0),
                List.of(new ExcellonImage.Drill(1, 10, 0), new ExcellonImage.Drill(2, 15, 0)), List.of());
        var options = new GCodeGenerator.DrillJobOptions(true, 15, 3, null, null)
                .withExclusions(true, List.of(area(10.8, 11.2, -.2, .2, CncExclusionArea.Strategy.AROUND, 0)));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, parameters, 2, parameters), List.of(1, 2), options));
    }

    @Test void changesBetweenToolGroupsAreRoutedWithTheirRespectiveDiametersAndClearanceIsChecked() {
        var image = image("MM", Map.of(1, .2, 2, 1.0),
                List.of(new ExcellonImage.Drill(1, 10, 0), new ExcellonImage.Drill(2, 0, 0)), List.of());
        var options = new GCodeGenerator.DrillJobOptions(true, 15, 3, null, null)
                .withExclusions(true, List.of(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0)));
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, parameters, 2, parameters), List.of(1, 2), options);
        assertTrue(job.gcode().contains("troque para a ferramenta T2"));
        assertFalse(job.travelGeometry().intersects(options.exclusions().getFirst().geometry()));
        var tooLow = new GCodeGenerator.DrillJobOptions(true, 1, 3, null, null).withExclusions(true, options.exclusions());
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, parameters, 2, parameters), List.of(1, 2), tooLow));
    }

    @Test void originOrParkingInsideAnAreaIsRefusedForAroundAndOver() {
        for (var strategy : CncExclusionArea.Strategy.values()) {
            assertThrows(IllegalArgumentException.class, () -> generate(holes(), options(area(-1, 1, -1, 1, strategy, 10))));
            var options = new GCodeGenerator.DrillJobOptions(false, 15, 3, 5.0, 0.0)
                    .withExclusions(true, List.of(area(4, 6, -1, 1, strategy, 10)));
            assertThrows(IllegalArgumentException.class, () -> generate(holes(), options));
        }
    }

    @Test void inchRoutingUsesConvertedMarginRatherThanPointOneInch() {
        var image = image("IN", Map.of(1, .02), List.of(new ExcellonImage.Drill(1, 1, 0)), List.of());
        var options = new GCodeGenerator.DrillJobOptions(false, .6, .1, null, null)
                .withExclusions(true, List.of(area(.4, .6, -.1, .1, CncExclusionArea.Strategy.AROUND, 0)));
        var job = GCodeGenerator.generateDrillCncJob(image, Map.of(1, new DrillGCodeParameters(.1, .04, 4, 0, false)), List.of(1), options);
        assertTrue(job.gcode().contains("G20"));
        assertTrue(job.travelGeometry().getEnvelopeInternal().getHeight() < .16);
        assertFalse(job.travelGeometry().intersects(options.exclusions().getFirst().geometry()));
    }

    @Test void missingOrInvalidDiametersCannotUseThePreviewFallbackForSafety() {
        for (var diameters : List.of(Map.<Integer, Double>of(), Map.of(1, 0.0), Map.of(1, -1.0), Map.of(1, Double.NaN))) {
            var image = image("MM", diameters, holes().drills(), List.of());
            assertThrows(IllegalArgumentException.class, () -> generate(image, options(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0))));
        }
    }

    @Test void incompatibleProfilesCannotIgnoreActiveAreasAndDisabledDraftsKeepPlainCode() {
        var options = options(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0));
        for (var profile : List.of(GCodePreprocessor.ROLAND_MDX_20, GCodePreprocessor.TOOLCHANGE_PROBE_MACH3,
                GCodePreprocessor.HPGL, GCodePreprocessor.GRBL_LASER))
            assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(holes(), Map.of(1, parameters), List.of(1), options, profile));
        var plain = new GCodeGenerator.DrillJobOptions(false, 15, .5, 0.0, 0.0);
        assertEquals(generate(holes(), plain).gcode(), generate(holes(), plain.withExclusions(false, options.exclusions())).gcode());
        assertThrows(IllegalArgumentException.class, () -> plain.withExclusions(true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> plain.withExclusions(false, java.util.Collections.nCopies(101, options.exclusions().getFirst())));
    }

    @Test void progressCountsWorkIsMonotonicAndCancellationStopsBetweenValidationAndGeneration() {
        var options = options(area(4, 6, -1, 1, CncExclusionArea.Strategy.AROUND, 0));
        var fractions = new ArrayList<Double>();
        GCodeGenerator.generateDrillCncJob(holes(), Map.of(1, parameters), List.of(1), options,
                GCodePreprocessor.FX_PORTABLE, CancellationToken.none(), fractions::add);
        assertEquals(0.0, fractions.getFirst());
        assertEquals(1.0, fractions.getLast());
        assertTrue(fractions.contains(.5));
        for (int i = 1; i < fractions.size(); i++) assertTrue(fractions.get(i) >= fractions.get(i - 1));
        var cancelled = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> GCodeGenerator.generateDrillCncJob(holes(), Map.of(1, parameters), List.of(1),
                options, GCodePreprocessor.FX_PORTABLE, cancelled::get, fraction -> { if (fraction >= .5) cancelled.set(true); }));
        assertThrows(CancellationException.class, () -> GCodeGenerator.generateDrillCncJob(holes(), Map.of(1, parameters), List.of(1),
                options, GCodePreprocessor.FX_PORTABLE, () -> true, ProgressCallback.none()));
    }
}
