package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryAdvancedCncTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final GeometryGCodeParameters basic = new GeometryGCodeParameters(2, 0.1, false, 1, 100, 100, true);
    private LineString square(double size) {
        return factory.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(size,0),
                new Coordinate(size,size),new Coordinate(0,size),new Coordinate(0,0)});
    }
    @Test void compensationMatchesPythonMitredBufferAndNeverMutatesSource() {
        LineString ring = square(10);
        Geometry original = ring.copy();
        for (var mode : List.of(ToolPathOffset.IN, ToolPathOffset.OUT, ToolPathOffset.CUSTOM)) {
            double distance = mode.distance(2,0.25);
            Geometry result = GeometryPathCompensation.apply(ring, distance, CancellationToken.none());
            assertEquals(new Envelope(-distance,10+distance,-distance,10+distance),result.getEnvelopeInternal());
            assertEquals(Math.pow(10+distance*2,2),result.getArea(),1e-9);
            assertTrue(ring.equalsExact(original));
        }
        assertSame(ring, GeometryPathCompensation.apply(ring,0,CancellationToken.none()));
    }
    @Test void polygonsKeepHolesAndCollectionsKeepSeparateElements() {
        var outer = factory.createLinearRing(square(10).getCoordinates());
        var hole = factory.createLinearRing(new Coordinate[]{new Coordinate(3,3),new Coordinate(7,3),
                new Coordinate(7,7),new Coordinate(3,7),new Coordinate(3,3)});
        var polygon = factory.createPolygon(outer,new LinearRing[]{hole});
        var result = (Polygon) GeometryPathCompensation.apply(polygon,1,CancellationToken.none());
        assertEquals(1,result.getNumInteriorRing()); assertEquals(140,result.getArea(),1e-9);
        var collection = factory.createGeometryCollection(new Geometry[]{square(10),square(20)});
        assertEquals(2,GeometryPathCompensation.apply(collection,1,CancellationToken.none()).getNumGeometries());
    }
    @Test void collapseOpenInInvalidDistanceAndCancellationCannotProduceUnsafeJob() {
        var open = factory.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(10,0)});
        assertThrows(IllegalArgumentException.class, () -> GeometryPathCompensation.apply(open,-0.5,CancellationToken.none()));
        assertThrows(IllegalArgumentException.class, () -> GeometryPathCompensation.apply(square(1),-2,CancellationToken.none()));
        assertFalse(GeometryPathCompensation.apply(open,0.5,CancellationToken.none()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> basic.withCompensation(ToolPathOffset.CUSTOM,Double.NaN));
        assertThrows(CancellationException.class, () -> GeometryPathCompensation.apply(square(10),1,() -> true));
    }
    @Test void perToolCompensationChangesGeneratedCutPreviewAndLeavesGeometryIntact() {
        var path = square(10);
        var tools = List.of(new ToolGeometry(2,path), new ToolGeometry(1,path));
        var p = basic.withCompensation(ToolPathOffset.IN,0);
        var q = basic.withCompensation(ToolPathOffset.CUSTOM,2);
        var result = GCodeGenerator.generateGeometryCncJob("MM", tools, p, Map.of(), Map.of(1,q),
                CancellationToken.none(), GCodePreprocessor.DEFAULT_NO_M6);
        assertTrue(result.gcode().contains("X1.0000 Y1.0000"),result.gcode());
        assertTrue(result.gcode().contains("X-2.0000 Y-2.0000"),result.gcode());
        assertEquals(-2.5,result.cutGeometry().getEnvelopeInternal().getMinX(),1e-8);
        assertEquals(new Envelope(0,10,0,10),path.getEnvelopeInternal());
    }
    @Test void safeChangeAndEndParkingAreIncludedInCodeAndPreviewAcrossMillingProfiles() {
        var p = basic.withJobOptions(new GeometryJobOptions(5.0,0.5,20.0,30.0,10.0,-5.0,-6.0));
        var tools = List.of(new ToolGeometry(1,square(10)),new ToolGeometry(2,square(4)));
        for (var pp : GCodePreprocessor.geometryProfiles()) {
            if (pp.isLaser() || pp.isPlotter() || pp.isRoland() || pp.requiresProbe()) continue;
            var result = GCodeGenerator.generateGeometryCncJob("MM",tools,p,Map.of(),CancellationToken.none(),pp);
            assertTrue(result.travelGeometry().getEnvelopeInternal().contains(-5,-6),pp.name());
            assertTrue(result.travelGeometry().getEnvelopeInternal().contains(20,30),pp.name());
            if (pp == GCodePreprocessor.ISEL_ICP_CNC) { assertTrue(result.gcode().contains("MOVEABS")); continue; }
            String code = result.gcode();
            assertTrue(code.contains("Z5.0000"),pp.name());
            int change = code.indexOf("X-5.0000 Y-6.0000");
            assertTrue(change > code.indexOf("Z10.0000"),code);
            int park = code.lastIndexOf("X20.0000 Y30.0000");
            assertTrue(park >= 0,code);
            assertTrue(code.lastIndexOf("Z0.5000") > park,code);
        }
    }
    @Test void automaticPositionsRetainLegacyPortableSequenceAndInactiveChangesDoNotMove() {
        var tool = List.of(new ToolGeometry(1,square(10)));
        var noChange = new GeometryGCodeParameters(2,0.1,false,1,100,1000,false);
        String old = GCodeGenerator.generateGeometryCncJob("MM",tool,noChange).gcode();
        assertEquals(old,GCodeGenerator.generateGeometryCncJob("MM",tool,noChange.withJobOptions(GeometryJobOptions.AUTOMATIC)).gcode());
        String code = GCodeGenerator.generateGeometryCncJob("MM",tool,
                noChange.withJobOptions(new GeometryJobOptions(null,null,null,null,10.0,20.0,30.0))).gcode();
        assertFalse(code.contains("X20.0000 Y30.0000")); assertFalse(code.contains("Z10.0000"));
    }
    @Test void pauseRestoresAbsoluteModeAndClearanceBeforeMovingToCut() {
        var p = basic.withJobOptions(new GeometryJobOptions(null,null,null,null,15.0,20.0,30.0));
        var tools = List.of(new ToolGeometry(1,square(10)),new ToolGeometry(2,square(4)));
        for (var pp : List.of(GCodePreprocessor.FX_PORTABLE,GCodePreprocessor.DEFAULT_NO_M6,GCodePreprocessor.GRBL_11)) {
            String code = GCodeGenerator.generateGeometryCncJob("MM",tools,p,Map.of(),CancellationToken.none(),pp).gcode();
            String rapid = pp.rapid();
            String[] paused = code.split("(?m)^M0(?=[ ;\\n])[^\\n]*\\n");
            assertEquals(3,paused.length,code);
            for (int i = 1; i < paused.length; i++) {
                String fragment = paused[i];
                assertTrue(fragment.contains("G90\n" + rapid + " Z15.0000"),code);
                assertTrue(fragment.indexOf(rapid + " Z15.0000") < fragment.indexOf(rapid + " X"),fragment);
            }
        }
    }
    @Test void unsafeAndUnsupportedSettingsAreRejectedRatherThanSilentlyIgnored() {
        var tool = List.of(new ToolGeometry(1,square(10)));
        var low = basic.withJobOptions(new GeometryJobOptions(null,null,null,null,1.0,null,null));
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM",tool,low));
        for (var pp : List.of(GCodePreprocessor.GRBL_LASER,GCodePreprocessor.HPGL,GCodePreprocessor.ROLAND_MDX_20)) {
            assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM",tool,
                    basic.withCompensation(ToolPathOffset.OUT,0),Map.of(),CancellationToken.none(),pp));
            assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM",tool,
                    basic.withJobOptions(new GeometryJobOptions(null,1.0,null,null,null,null,null)),Map.of(),CancellationToken.none(),pp));
        }
        assertThrows(IllegalArgumentException.class, () -> new GeometryJobOptions(null,null,1.0,null,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> new GeometryJobOptions(null,Double.NaN,null,null,null,null,null));
    }
}
