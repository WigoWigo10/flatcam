package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/** Audit the emitted modal Z, not the 2D preview or a planned clearance argument. */
class GeometryTravelClearanceTest {
    private final GeometryFactory factory = new GeometryFactory();

    private ToolGeometry paths(double diameter, double s) {
        var a = factory.createLineString(new Coordinate[]{new Coordinate(10*s,0),new Coordinate(12*s,0)});
        var b = factory.createLineString(new Coordinate[]{new Coordinate(14*s,0),new Coordinate(16*s,0)});
        return new ToolGeometry(diameter*s, factory.createMultiLineString(new org.locationtech.jts.geom.LineString[]{a,b}));
    }

    private GeometryGCodeParameters parameters(double safe, double s, boolean change, boolean multi,
                                               GeometryPassStrategy strategy) {
        return new GeometryGCodeParameters(safe*s,.9*s,multi,.3*s,200*s,10000,change)
                .withPassStrategy(strategy).withJobOptions(new GeometryJobOptions(null,.5*s,20*s,0.0,15*s,-2*s,-3*s));
    }

    @Test void separatedPathsUseTheJobClearanceEvenWhenTheFirstToolRequestsLess() {
        for (String units : List.of("MM","IN")) for (boolean change : List.of(false,true)) {
            double s = units.equals("IN") ? 1/25.4 : 1;
            var base = parameters(3,s,change,false,GeometryPassStrategy.RETRACT_SAME_DIRECTION);
            String code = GCodeGenerator.generateGeometryCncJob(units,List.of(paths(.4,s),paths(.8,s)),base,
                    Map.of(),Map.of(1,parameters(4,s,change,false,GeometryPassStrategy.RETRACT_SAME_DIRECTION)),
                    CancellationToken.none(),GCodePreprocessor.GRBL_11_NO_M6).gcode();
            assertAllRapidXyAtLeast(code,4*s);
        }
    }

    @Test void retractingPassesNeverReturnLaterallyAtTheLowerToolHeight() {
        for (String units : List.of("MM","IN")) {
            double s = units.equals("IN") ? 1/25.4 : 1;
            var base = parameters(3,s,true,true,GeometryPassStrategy.RETRACT_SAME_DIRECTION);
            String code = GCodeGenerator.generateGeometryCncJob(units,List.of(paths(.4,s),paths(.8,s)),base,
                    Map.of(),Map.of(1,parameters(4,s,true,true,GeometryPassStrategy.RETRACT_SAME_DIRECTION)),
                    CancellationToken.none(),GCodePreprocessor.GRBL_11_NO_M6).gcode();
            assertAllRapidXyAtLeast(code,4*s);
            assertEquals(12,code.lines().filter(line -> line.startsWith("G01 Z-")).count(),code);
        }
    }

    @Test void continuousPassesStillClearTheJobHeightBetweenSeparatePaths() {
        var base = parameters(3,1,true,true,GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        String code = GCodeGenerator.generateGeometryCncJob("MM",List.of(paths(.4,1),paths(.8,1)),base,
                Map.of(),Map.of(1,parameters(4,1,true,true,GeometryPassStrategy.CONTINUOUS_ALTERNATING)),
                CancellationToken.none(),GCodePreprocessor.GRBL_11_NO_M6).gcode();
        assertAllRapidXyAtLeast(code,4);
        assertEquals(12,code.lines().filter(line -> line.startsWith("G01 Z-")).count(),code);
    }

    @Test void lowerSecondToolAndLowEndZCannotLowerTheLateralParkingMove() {
        var base = parameters(4,1,true,false,GeometryPassStrategy.RETRACT_SAME_DIRECTION);
        for (var profile : List.of(GCodePreprocessor.FX_PORTABLE,GCodePreprocessor.DEFAULT_NO_M6,
                GCodePreprocessor.GRBL_11_NO_M6,GCodePreprocessor.MARLIN)) {
            String code = GCodeGenerator.generateGeometryCncJob("MM",List.of(paths(.4,1),paths(.8,1)),base,
                    Map.of(),Map.of(1,parameters(3,1,true,false,GeometryPassStrategy.RETRACT_SAME_DIRECTION)),
                    CancellationToken.none(),profile).gcode();
            assertAllRapidXyAtLeast(code,4);
            assertTrue(code.lastIndexOf("Z0.5000") > code.lastIndexOf("X20.0000 Y0.0000"),code);
        }
    }

    private static void assertAllRapidXyAtLeast(String code, double required) {
        double z = 0; int lateral = 0;
        // Four-decimal source rounding, the same GRBL bound used by the differential audit.
        double bound = .000051;
        for (String block : code.lines().toList()) {
            String line = block.split("[;(]",2)[0].trim();
            if (!line.matches("G0?[01](?: .*|$)")) continue;
            boolean rapid = line.matches("G0?0(?: .*|$)");
            for (String word : line.split("\\s+")) if (word.startsWith("Z")) z = Double.parseDouble(word.substring(1));
            if (rapid && line.matches(".*(?:^| )X[-+0-9.]+.*")) {
                lateral++;
                assertTrue(z >= required-bound,"XY rapid at Z " + z + " below job clearance " + required + ": " + block + "\n" + code);
            }
        }
        assertTrue(lateral >= 4,code);
    }
}
