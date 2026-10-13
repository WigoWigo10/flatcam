package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryClosedPassStrategyTest {
    private final GeometryFactory f=new GeometryFactory();
    private GeometryGCodeParameters parameters() {
        return new GeometryGCodeParameters(3,.6,true,.2,200,10000,true,0,null,75,false,0,false,0)
                .withPassStrategy(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION);
    }
    private LinearRing ring() {
        return f.createLinearRing(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0),
                new Coordinate(12,2),new Coordinate(10,2),new Coordinate(10,0)});
    }
    @Test void ringsKeepTheirDirectionAndHaveNoIntermediateLift() {
        for (Geometry geometry:List.of(ring(),f.createPolygon(ring()),f.createLineString(ring().getCoordinates()))) {
            String code=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,geometry)),parameters(),
                    Map.of(),CancellationToken.none(),GCodePreprocessor.GRBL_11_NO_M6).gcode();
            var section=code.substring(code.indexOf("G01 Z-0.2000"),code.indexOf("G01 Z-0.6000"));
            assertFalse(section.contains("G00"),code);
            assertEquals(3,code.lines().filter(line -> line.equals("G01 X12.0000 Y0.0000 F200.0000")).count(),code);
            assertTrue(code.contains("G01 Z-0.4000 F75.0000\nG01 X12.0000 Y0.0000 F200.0000"),code);
        }
    }
    @Test void holesAreCutAsContoursAndDirectionCanBePreservedExplicitly() {
        var hole=(LinearRing)org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(.25,.25,11,1).transform(ring());
        var polygon=f.createPolygon(ring(),new LinearRing[]{hole});
        var p=parameters().withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
        var result=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(.1,polygon)),p);
        assertEquals(6,result.gcode().lines().filter(line -> line.startsWith("G1 Z-")).count(),result.gcode());
        assertTrue(polygon.equalsExact(f.createPolygon(ring(),new LinearRing[]{hole})));
    }
    @Test void openPathsAreRejectedBeforeProducingAJobAndPointsUseOnlyFinalDepth() {
        var open=f.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,open)),parameters()));
        var collection=f.createGeometryCollection(new Geometry[]{ring(),open});
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,collection)),parameters()));
        String point=GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,f.createPoint(new Coordinate(10,0)))),parameters()).gcode();
        assertFalse(point.contains("Z-0.2000")); assertTrue(point.contains("Z-0.6000"));
        assertThrows(java.util.concurrent.CancellationException.class,()->GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,ring())),parameters(),()->true));
    }
    @Test void compensationCanProduceAClosedPathButExtraCutAndProbeCannotChangeTheContract() {
        var open=f.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        assertDoesNotThrow(()->GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,open)),parameters().withCompensation(ToolPathOffset.OUT,0)));
        assertThrows(IllegalArgumentException.class,()->new GeometryGCodeParameters(3,.6,true,.2,200,10000,true,0,null,75,false,0,true,.1)
                .withPassStrategy(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION));
    }
}
