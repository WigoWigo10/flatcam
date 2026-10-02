package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryPerToolTest {
    private final GeometryFactory factory = new GeometryFactory();
    private GeometryGCodeParameters parameters(double depth, double feed, int rpm) {
        return new GeometryGCodeParameters(2, depth, false, 1, feed, rpm, true, 600, null,
                feed / 2, true, 0.5, false, 0);
    }
    @Test void individualDepthFeedZSpindleAndDwellReachAllSupportedMillingProfiles() {
        var path = factory.createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(2,0)});
        var a = parameters(0.1, 100, 100); var b = parameters(0.3, 200, 200);
        for (var pp : GCodePreprocessor.geometryProfiles()) {
            if (pp.isLaser() || pp.isPlotter() || pp.isRoland() || pp.requiresProbe()) continue;
            String code = GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(0.4,path), new ToolGeometry(0.8,path)),
                    a, Map.of(), Map.of(0,a,1,b), CancellationToken.none(), pp).gcode();
            assertFalse(code.isBlank(), pp.name());
            if (pp == GCodePreprocessor.ISEL_ICP_CNC) { assertTrue(code.contains("WAIT 500")); continue; }
            assertTrue(code.contains("Z-0.1000 F50.0000"), pp.name() + code);
            assertTrue(code.contains("Z-0.3000 F100.0000"), pp.name());
            assertTrue(code.contains("F200.0000"), pp.name());
            assertTrue(code.contains("S200"), pp.name());
            assertTrue(code.contains("G4 P0.5"), pp.name());
        }
    }
    @Test void extraCutOnlyExtendsClosedPathsAndRejectsExcess() {
        var path = factory.createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(2,0), new Coordinate(2,2), new Coordinate(0,0)});
        var p = new GeometryGCodeParameters(2,0.1,false,1,100,0,false,0,null,50,false,0,true,0.5);
        String code = GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(0.4,path)), p).gcode();
        assertTrue(code.contains("X0.5000 Y0.0000 F100.0000"), code);
        var tooLong = new GeometryGCodeParameters(2,0.1,false,1,100,0,false,0,null,50,false,0,true,100);
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(0.4,path)), tooLong));
    }
}
