package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

class GCodeCoordinatePrecisionTest {
    @Test void inchXYFormattingIsSixPlacesWhileMillimetresAndOtherValuesStayUnchanged() {
        assertEquals("0.123457",GCodeGenerator.fmtCoordinate(.1234567,"IN"));
        assertEquals("-0.000000",GCodeGenerator.fmtCoordinate(-1e-9,"INCH"));
        assertEquals("0.1235",GCodeGenerator.fmtCoordinate(.1234567,"MM"));
        assertEquals("0.1235",GCodeGenerator.fmt(.1234567));
        var random = new Random(123);
        for(int i=0;i<1000;i++) {
            double coordinate = random.nextDouble() * 20 - 10;
            assertEquals(String.format(Locale.ROOT,"%.6f",coordinate),GCodeGenerator.fmtCoordinate(coordinate,"IN"));
        }
    }

    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void denseConcaveReferenceRetainsPathLengthWhenWrittenAndParsed(String units) {
        double scale = units.equals("IN") ? 1/25.4 : 1;
        var f = new GeometryFactory();
        Geometry copper = f.toGeometry(new Envelope(0,30,0,20)).difference(f.toGeometry(new Envelope(4,26,4,16)));
        Geometry reference = f.toGeometry(new Envelope(-2,32,7,22)).difference(f.toGeometry(new Envelope(21,33,14,23)));
        var transform = AffineTransformation.scaleInstance(scale,scale);
        copper = transform.transform(copper); reference = transform.transform(reference);
        var params = new NccParameters(List.of(.5 * scale),.4,scale,NccMethod.STANDARD,false,true,0,false,
                NccOrder.NONE,new NccBoundary.ReferenceGeometry(reference),List.of());
        var geometry = NccGenerator.generate(units,copper,params).geometry();
        var machining = new GeometryGCodeParameters(3 * scale,.1 * scale,false,1,300 * scale,0,false);
        var job = GCodeGenerator.generateGeometryCncJob(units,geometry,machining,.5 * scale);
        var parsed = GCodeToolpathParser.parse(job.gcode(),CancellationToken.none(),ProgressCallback.none());
        assertTrue(parsed.plotAvailable(),parsed.warning());
        // Read the actual XY points from G1, not the pre-serialization CAM footprint.
        double total = 0; Coordinate previous = null;
        for(String line : job.gcode().lines().toList()) {
            if(line.startsWith("G0 X")) previous = xy(line);
            if(line.startsWith("G1 X")) { Coordinate next = xy(line); total += previous.distance(next); previous = next; }
        }
        assertTrue(Math.abs(total - geometry.getLength()) / geometry.getLength() < .001,
                "four places in inches inflated dense path length by 0.26%; retain the existing 0.1% criterion");
    }

    @Test void inchDrillingSlotsParkingAndLaserUseTheSameXYPrecision() {
        var f = new GeometryFactory();
        var image = ExcellonImage.of("IN",Map.of(1,.02),List.of(new ExcellonImage.Drill(1,.12345678,.23456789)),
                List.of(new ExcellonImage.Slot(1,.34567891,.45678912,.56789123,.67891234)),f.createGeometryCollection());
        var params = new DrillGCodeParameters(.1,.01,4,0,false);
        var options = new GCodeGenerator.DrillJobOptions(false,.1,.1,.78912345,.89123456);
        var code = GCodeGenerator.generateDrillCncJob(image,Map.of(1,params),List.of(1),options).gcode();
        assertTrue(code.contains("G0 X0.123457 Y0.234568"));
        assertTrue(code.contains("G0 X0.345679 Y0.456789"));
        assertTrue(code.contains("G1 X0.567891 Y0.678912 F4.0000"));
        assertTrue(code.contains("G0 X0.789123 Y0.891235"));
        var path = f.createLineString(new Coordinate[]{new Coordinate(.12345678,.23456789),new Coordinate(.34567891,.45678912)});
        var laser = GCodeGenerator.generateGeometryCncJob("IN",List.of(new ToolGeometry(.02,path)),
                new GeometryGCodeParameters(.1,.01,false,1,4,100,false),Map.of(),CancellationToken.none(),GCodePreprocessor.GRBL_LASER).gcode();
        assertTrue(laser.contains("X0.123457 Y0.234568"));
        assertTrue(laser.contains("X0.345679 Y0.456789"));
    }
    private static Coordinate xy(String line) {
        String[] values = line.split(" ");
        return new Coordinate(Double.parseDouble(values[1].substring(1)),Double.parseDouble(values[2].substring(1)));
    }
}
