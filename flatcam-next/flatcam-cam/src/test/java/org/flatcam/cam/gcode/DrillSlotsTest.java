package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class DrillSlotsTest {
    @Test void diagonalSlotsInMetricAndInchPanelsKeepToolIdsPeckCountsAndMixedExclusions() {
        for (String units : List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            var source=ExcellonImage.of(units,Map.of(1,s,2,s),List.of(new ExcellonImage.Drill(1,8*s,0)),
                    List.of(new ExcellonImage.Slot(1,3*s,4*s,0,0),new ExcellonImage.Slot(2,10*s,s,12*s,s)),factory.createPolygon());
            var panel=org.flatcam.cam.panel.Panelize.excellon(source,new org.flatcam.cam.panel.Panelize.Layout(2,2,false,20*s,10*s));
            var chain=new DrillGCodeParameters(3*s,.8*s,100*s,10000,true,true,.4*s,false,0,0)
                    .withSlots(new DrillSlotParameters(true,0,true));
            var mill=chain.withSlots(DrillSlotParameters.milling());
            var over=CncExclusionArea.of(factory.toGeometry(new Envelope(4*s,6*s,-.1*s,.1*s)),CncExclusionArea.Strategy.OVER,7*s);
            var around=CncExclusionArea.of(factory.toGeometry(new Envelope(8*s,9*s,4*s,6*s)),CncExclusionArea.Strategy.AROUND,0);
            var options=new GCodeGenerator.DrillJobOptions(true,15*s,5*s,null,null)
                    .withOptimizedDrills(true).withExclusions(true,List.of(over,around));
            var job=GCodeGenerator.generateDrillCncJob(panel,Map.of(1,chain,2,mill),List.of(1,2),options,GCodePreprocessor.GRBL_11_NO_M6);
            assertEquals(64,job.gcode().lines().filter(line -> line.matches("G0?1 Z-.*")).count());
            assertEquals(8,job.gcode().lines().filter(line -> line.matches("G0?1 X.*")).count());
            assertEquals(8,panel.totalSlots()); assertEquals(4,panel.totalDrills());
            assertEquals(2,source.totalSlots()); assertEquals(1,source.totalDrills());
            assertFalse(job.cutGeometry().intersects(over.geometry()));
            assertFalse(job.cutGeometry().intersects(around.geometry()));
        }
    }
    private final GeometryFactory factory = new GeometryFactory();
    private List<ExcellonImage.Drill> points(ExcellonImage.Slot slot, double diameter, double overlap, boolean last) {
        var points = new ArrayList<ExcellonImage.Drill>();
        new DrillSlotParameters(true, overlap, last).emit(slot, diameter, CancellationToken.none(), fraction -> {}, points::add);
        return points;
    }

    @Test void interpolationEndpointThresholdDegenerateAndInchAreExplicit() {
        for (double s : List.of(1.0, 1 / 25.4)) {
            var slot = new ExcellonImage.Slot(1, 10*s, 0, 12*s, 0);
            var drilled = points(slot, s, 25, true);
            assertEquals(4, drilled.size());
            double[] expected = {10, 10.75, 11.5, 12};
            for (int i=0; i<expected.length; i++) assertEquals(expected[i]*s, drilled.get(i).x(), 1e-12);
            assertEquals(3, points(slot, s, 25, false).size());
            // Original Python also includes a nearly-endpoint interior hit after inch floating subtraction.
            assertEquals(s == 1 ? 2 : 3, points(slot, s, 0, false).size());
            assertEquals(3, points(slot, s, 0, true).size());
            assertEquals(1, points(new ExcellonImage.Slot(1, 0, 0, .04*s, 0), s, 0, true).size());
            assertEquals(2, points(new ExcellonImage.Slot(1, 0, 0, .2*s, 0), s, 0, true).size());
            assertEquals(1, points(new ExcellonImage.Slot(1, 3*s, 4*s, 3*s, 4*s), s, 0, true).size());
            var diagonal = points(new ExcellonImage.Slot(1, 3*s, 4*s, 0, 0), s, 0, true);
            assertEquals(6, diagonal.size());
            assertEquals(2.4*s, diagonal.get(1).x(), 1e-12);
            assertEquals(3.2*s, diagonal.get(1).y(), 1e-12);
        }
    }

    @Test void repeatedHolesPeckMixedToolsAndSourceArePreserved() {
        for (String units : List.of("MM", "IN")) {
            double s = units.equals("MM") ? 1 : 1 / 25.4;
            var image = ExcellonImage.of(units, Map.of(1, s, 2, 2*s),
                    List.of(new ExcellonImage.Drill(1, 10*s, 0), new ExcellonImage.Drill(1, 10*s, 0)),
                    List.of(new ExcellonImage.Slot(1, 10*s, 0, 12*s, 0), new ExcellonImage.Slot(2, 20*s, 0, 22*s, 0)), factory.createPolygon());
            var params = new DrillGCodeParameters(3*s, 1.1*s, 100*s, 10000, true, true, .4*s, false, 0, 0);
            var options = new GCodeGenerator.DrillJobOptions(true, 15*s, 5*s, null, null);
            double[] last = {-1};
            var job = GCodeGenerator.generateDrillCncJob(image,
                    Map.of(1, params.withSlots(new DrillSlotParameters(true, 25, true)), 2, params), List.of(1,2),
                    options, GCodePreprocessor.GRBL_11_NO_M6, CancellationToken.none(), fraction -> {
                        assertTrue(fraction >= last[0]); last[0] = fraction;
                    });
            assertEquals(1, last[0]);
            assertEquals(21, job.gcode().lines().filter(line -> line.matches("G0?1 Z-.*")).count()); // 6 holes*3 + 1 milled slot*3
            assertEquals(3, job.gcode().lines().filter(line -> line.matches("G0?1 X.*")).count());
            assertEquals(2, image.drills().size()); assertEquals(2, image.slots().size());
            var holesOnly = GCodeGenerator.generateDrillCncJob(image,
                    Map.of(1, params.withSlots(new DrillSlotParameters(true,25,true))), List.of(1), options,
                    GCodePreprocessor.GRBL_11_NO_M6);
            assertFalse(holesOnly.gcode().lines().anyMatch(line -> line.matches("G0?1 X.*")));
            assertEquals(18, holesOnly.gcode().lines().filter(line -> line.matches("G0?1 Z-.*")).count());
            assertFalse(holesOnly.cutGeometry().isEmpty());
            assertNotEquals(holesOnly.gcode(), GCodeGenerator.generateDrillCncJob(image, Map.of(1, params), List.of(1), options).gcode());
        }
    }

    @Test void exclusionsValidateConvertedHolesIncludingTheirRadiusBeforePublication() {
        var image = ExcellonImage.of("MM", Map.of(1, 1.0), List.of(),
                List.of(new ExcellonImage.Slot(1, 10, 0, 12, 0)), factory.createPolygon());
        var params = new DrillGCodeParameters(3, 1, 100, 0, false).withSlots(new DrillSlotParameters(true,25,true));
        for (var strategy : CncExclusionArea.Strategy.values()) {
            var area = CncExclusionArea.of(factory.toGeometry(new Envelope(11.7, 11.8, -.1, .1)), strategy, 10);
            var options = new GCodeGenerator.DrillJobOptions(false,15,5,null,null).withExclusions(true,List.of(area));
            assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,Map.of(1,params),List.of(1),options));
        }
    }

    @Test void badOverlapDiameterResourceExplosionAndCancellationAreNotSilentlyIgnored() {
        for (double overlap : List.of(-1.0,100.0,Double.NaN,Double.POSITIVE_INFINITY))
            assertThrows(IllegalArgumentException.class, () -> new DrillSlotParameters(true,overlap,true));
        var slot = new ExcellonImage.Slot(1,0,0,10,0);
        assertThrows(IllegalArgumentException.class, () -> points(slot,0,25,true));
        assertThrows(IllegalArgumentException.class, () -> points(slot,1,99.999999,true));
        var calls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new DrillSlotParameters(true,0,true).emit(slot,.001,
                () -> calls.incrementAndGet()>20, fraction -> {}, point -> {}));
        var image = ExcellonImage.of("MM", Map.of(), List.of(), List.of(slot), factory.createPolygon());
        var params = new DrillGCodeParameters(3,1,100,0,false).withSlots(new DrillSlotParameters(true,25,true));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,Map.of(1,params),List.of(1),
                new GCodeGenerator.DrillJobOptions(false,15,5,null,null)));
    }
}
