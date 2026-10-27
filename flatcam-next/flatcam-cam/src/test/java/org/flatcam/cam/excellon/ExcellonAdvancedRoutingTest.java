package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class ExcellonAdvancedRoutingTest {
    private static final GeometryFactory F=new GeometryFactory();
    static List<String> file(String header,String... body) {
        var lines=new ArrayList<>(List.of("M48",header,"T1C0.2","%","T1"));
        lines.addAll(List.of(body)); lines.add("M30"); return lines;
    }
    static ExcellonImage quarter(boolean clockwise) {
        return new ExcellonParser().parse(file("METRIC","G00X1.0Y0.0","M15",
                clockwise ? "G02X0.0Y-1.0A1.0" : "G03X0.0Y1.0A1.0","M16"));
    }

    @Test void incrementalDrillsAccumulateOnlyPresentAxesAndG90RestoresAbsoluteCoordinates() {
        var image=new ExcellonParser().parse(file("METRIC","X10.0Y20.0","G91","X1.0Y-2.0","Y3.0","X-4.0","G90","Y30.0"));
        assertEquals(List.of(new ExcellonImage.Drill(1,10,20),new ExcellonImage.Drill(1,11,18),
                new ExcellonImage.Drill(1,11,21),new ExcellonImage.Drill(1,7,21),new ExcellonImage.Drill(1,7,30)),image.drills());
        assertTrue(image.slots().isEmpty());
        assertEquals(new ExcellonImage.Drill(1,0,2),new ExcellonParser().parse(file("METRIC","G91","Y2.0")).drills().getFirst());
    }

    @Test void incrementalFixedCoordinatesG85AndLinearRoutingShareTheAccumulatedPosition() {
        var image=new ExcellonParser().parse(file("INCH,LZ,00.0000","X001000Y002000","G91",
                "X000500G85Y000250","X000100","G00X000100Y000100","M15","G01X000100","X000000Y-000050","M16","G05","G90","X1.0Y2.0"));
        assertEquals(3,image.drills().size()); assertEquals(.16,image.drills().get(1).x(),1e-12);
        assertEquals(.225,image.drills().get(1).y(),1e-12);
        assertEquals(3,image.totalSlots());
        assertEquals(.15,image.slots().getFirst().x1(),1e-12); assertEquals(.225,image.slots().getFirst().y2(),1e-12);
        assertEquals(.18,image.slots().getLast().x2(),1e-12); assertEquals(.23,image.slots().getLast().y2(),1e-12);
        assertEquals(new ExcellonImage.Drill(1,1,2),image.drills().getLast());
        var tz=new ExcellonParser().parse(file("METRIC,TZ","G91","X001000Y002000","X000500"));
        assertEquals(new ExcellonImage.Drill(1,1.5,2),tz.drills().getLast());
    }

    @Test void radiusArcsHaveCorrectDirectionFootprintAndPhysicalErrorInMmAndInch() {
        for(double unit : new double[]{1,1/25.4}) for(boolean cw : new boolean[]{true,false}) {
            String units=unit==1 ? "METRIC" : "INCH";
            double end=cw ? -unit : unit;
            var image=new ExcellonParser().parse(file(units,"G00X"+unit+"Y0.0","M15",
                    (cw ? "G02" : "G03")+"X0.0Y"+end+"A"+unit,"M16"));
            assertEquals(1,image.totalSlots()); assertEquals(0,image.totalDrills());
            var slot=image.slots().getFirst(); assertTrue(slot.hasPath()); assertTrue(slot.path().size()>5);
            assertEquals(unit,slot.x1()); assertEquals(end,slot.y2());
            for(int i=0;i<slot.path().size();i++) {
                var point=slot.path().get(i); assertEquals(unit,Math.hypot(point.x(),point.y()),1e-12);
                assertTrue(cw ? point.y()<=1e-12 : point.y()>=-1e-12);
                if(i>0) {
                    var previous=slot.path().get(i-1);
                    double sagitta=unit-Math.hypot((point.x()+previous.x())/2,(point.y()+previous.y())/2);
                    assertTrue(sagitta <= .001*(unit==1 ? 1 : 1/25.4)+1e-12);
                }
            }
            if(unit==1) {
                assertTrue(image.solidGeometry().covers(F.createPoint(new Coordinate(Math.sqrt(.5),cw ? -Math.sqrt(.5) : Math.sqrt(.5)))));
                assertFalse(image.solidGeometry().covers(F.createPoint(new Coordinate(.5,cw ? -.5 : .5))),"the chord must not be milled");
            }
        }
    }

    @Test void centreOffsetsUseCnc7ConventionAndRadiusMotionAreModalInIncrementalMode() {
        var image=new ExcellonParser().parse(file("METRIC","G00X1.0Y0.0","I1.0J0.0","M15","G02X0.0Y-1.0",
                "G91","X-1.0Y1.0A1.0","M16","G05","G90","X2.0Y3.0"));
        assertEquals(2,image.totalSlots());
        assertEquals(-1,image.slots().getLast().x2()); assertEquals(0,image.slots().getLast().y2());
        assertEquals(new ExcellonImage.Drill(1,2,3),image.drills().getFirst());
        for(var slot:image.slots()) for(var p:slot.path()) assertEquals(1,Math.hypot(p.x(),p.y()),1e-12);
        var oppositeOffset=new ExcellonParser().parse(file("METRIC","G00X1.0Y0.0","M15","G02X0.0Y-1.0I-1.0J0.0","M16"));
        assertEquals(image.slots().getFirst().path(),oppositeOffset.slots().getFirst().path(),"endpoints must select the same unique circle for either vector convention");
        var semis=new ExcellonParser().parse(file("METRIC","G00X1.0Y0.0","M15","G03X-1.0Y0.0A1.0","G03X1.0Y0.0A1.0","M16"));
        assertEquals(2,semis.totalSlots()); assertTrue(semis.slots().getFirst().path().stream().anyMatch(p->p.y()>.99));
    }

    @Test void invalidOrAmbiguousArcsAreRefusedInsteadOfBecomingStraightSlots() {
        for(String command:List.of("G02X0.0Y-1.0","G02X0.0Y-1.0A0.0","G02X0.0Y-1.0A-1.0",
                "G02X0.0Y-1.0A0.1","G02X0.0Y-1.0I1.0","G02X0.0Y-1.0A1.0I1.0J0.0",
                "G02X0.0Y-1.0I2.0J0.0","G02X1.0Y0.0A1.0","G02X0.0X2.0Y-1.0A1.0",
                "G03X0.0Y-1.0I1.0J0.0"))
            assertThrows(ExcellonParseException.class,()->new ExcellonParser().parse(file("METRIC","G00X1.0Y0.0","M15",command)),command);
        assertThrows(ExcellonParseException.class,()->new ExcellonParser().parse(file("METRIC","G00X10000000000.0Y0.0","M15","G02X-10000000000.0Y0.0A10000000000.0")));
        String maximum=java.math.BigDecimal.valueOf(Double.MAX_VALUE).toPlainString()+".0";
        assertThrows(ExcellonParseException.class,()->new ExcellonParser().parse(file("METRIC","G00X"+maximum+"Y0.0","G91","G00X"+maximum)));
        var count=new AtomicInteger();
        assertThrows(CancellationException.class,()->ExcellonRouteArc.build(1,10,0,0,-10,true,
                new ExcellonRouteArc.Spec(10.0,null,null),"MM",()->count.incrementAndGet()>3));
    }

    @Test void toolUpArcsOnlyMoveAndM30StopsReadingTheProgram() {
        var image=new ExcellonParser().parse(file("METRIC","G00X1.0Y0.0","G02X0.0Y-1.0A1.0","G05","X2.0Y2.0","M30","G91","X5.0"));
        assertEquals(0,image.totalSlots()); assertEquals(List.of(new ExcellonImage.Drill(1,2,2)),image.drills());
    }

    @Test void writesPublicReaderControlsForTheOriginalPythonProbe() throws Exception {
        var cases=new LinkedHashMap<String,List<String>>();
        cases.put("absolute",file("METRIC","X10.0Y20.0","X11.0Y18.0"));
        cases.put("incremental",file("METRIC","X10.0Y20.0","G91","X1.0Y-2.0","G90","X3.0Y4.0"));
        cases.put("cw-radius",file("METRIC","G00X1.0Y0.0","M15","G02X0.0Y-1.0A1.0","M16","G05"));
        cases.put("ccw-centre",file("METRIC","G00X1.0Y0.0","M15","G03X0.0Y1.0I1.0J0.0","M16","G05"));
        Path directory=Path.of("target/excellon-routing-controls"); Files.createDirectories(directory);
        var rows=new ArrayList<String>(); rows.add("name,drills,slots,curved");
        for(var entry:cases.entrySet()) {
            Files.writeString(directory.resolve(entry.getKey()+".drl"),String.join("\n",entry.getValue())+"\n",StandardCharsets.UTF_8);
            var parsed=new ExcellonParser().parse(entry.getValue());
            String drills=parsed.drills().stream().map(p->p.x()+":"+p.y()).collect(java.util.stream.Collectors.joining("|"));
            rows.add(entry.getKey()+","+drills+","+parsed.totalSlots()+","+parsed.slots().stream().filter(ExcellonImage.Slot::hasPath).count());
        }
        Files.write(directory.resolve("fx.csv"),rows,StandardCharsets.UTF_8);
        assertEquals(4,cases.size());
    }
}
