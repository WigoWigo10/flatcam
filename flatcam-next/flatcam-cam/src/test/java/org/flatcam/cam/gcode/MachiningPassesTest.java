package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKTReader;

class MachiningPassesTest {
    @Test void exactDecimalMultiplesDoNotAddAnotherFinalPassInEitherUnit() {
        for (double scale : new double[]{1, 1 / 25.4}) {
            assertEquals(3, MachiningPasses.count(.9 * scale, true, .3 * scale));
            var depths = MachiningPasses.depths(.9 * scale, true, .3 * scale);
            assertEquals(3, depths.size()); assertEquals(.9 * scale, depths.getLast());
        }
    }
    @Test void genuineRemaindersTinyRatiosAndThePassLimitRemainExplicit() {
        assertEquals(4, MachiningPasses.count(.900001, true, .3));
        assertEquals(1, MachiningPasses.count(.00001, true, 100));
        assertEquals(List.of(.9), MachiningPasses.depths(.9, false, 0));
        assertEquals(10_000, MachiningPasses.count(1, true, .0001));
        assertThrows(IllegalArgumentException.class, () -> MachiningPasses.count(1.0001, true, .0001));
        assertThrows(IllegalArgumentException.class, () -> MachiningPasses.count(1, true, Double.MIN_VALUE));
    }
    @Test void generatorEmitsThreeFinalTraversalsNotFourWithBothPolicies() throws Exception {
        var path = new WKTReader().read("LINESTRING (10 0,12 0)");
        for (var strategy : GeometryPassStrategy.values()) {
            var p = new GeometryGCodeParameters(3, .9, true, .3, 200, 10000, false)
                    .withPassStrategy(strategy);
            String code = GCodeGenerator.generateGeometryCncJob("MM", path, p, .4, CancellationToken.none()).gcode();
            assertEquals(3, code.lines().filter(line -> line.startsWith("G1 Z-")).count(), code);
            assertEquals(3, code.lines().filter(line -> line.startsWith("G1 X")).count(), code);
            assertEquals(1, code.lines().filter(line -> line.startsWith("G1 Z-0.9000")).count(), code);
        }
    }
}
