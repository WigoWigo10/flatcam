package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

class CncRoutingTest {
    final GeometryFactory f=new GeometryFactory();
    Geometry read(String wkt) throws Exception { return new WKTReader().read(wkt); }
    @Test void closedEntryRotatesWithoutReversingOrModifyingSource() throws Exception {
        Geometry g=read("LINESTRING (100 0,100 10,0 10,0 0,100 0)"); String original=g.toText();
        var ordered=CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.NEAREST,true,false,List.of(),0),CancellationToken.none());
        assertEquals(new Coordinate(0,0),ordered.getFirst()[0]); assertEquals(new Coordinate(100,0),ordered.getFirst()[1]);
        assertEquals(original,g.toText()); assertEquals(5,ordered.getFirst().length);
    }
    @Test void openDirectionIsExplicitAndOriginalOrderKeepsItsEntry() throws Exception {
        Geometry g=read("LINESTRING (10 0,0 0)");
        assertEquals(10,CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.NEAREST,true,false,List.of(),0),CancellationToken.none()).getFirst()[0].x);
        assertEquals(0,CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.NEAREST,false,true,List.of(),0),CancellationToken.none()).getFirst()[0].x);
        assertEquals(10,CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.SOURCE,false,false,List.of(),0),CancellationToken.none()).getFirst()[0].x);
    }
    @Test void innerProfilesPrecedeTheirParentAndOpenBridgeSegments() throws Exception {
        Geometry g=read("MULTILINESTRING ((0 0,10 0,10 10,0 10,0 0),(3 3,7 3,7 7,3 7,3 3),(0 0,0 10))");
        var ordered=CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.NEAREST,true,true,List.of(),0),CancellationToken.none());
        assertEquals(3,ordered.getFirst()[0].x); assertEquals(5,ordered.get(1).length); assertEquals(2,ordered.get(2).length);
    }
    @Test void boardGroupingFinishesEachReferenceBoxEvenWhenAnotherBoardIsNearer() throws Exception {
        Geometry g=read("MULTILINESTRING ((0 0,100 0),(10 5,20 5),(110 5,120 5))");
        List<String> boards=List.of(f.toGeometry(new Envelope(0,100,0,10)).toText(),f.toGeometry(new Envelope(110,200,0,10)).toText());
        var ordered=CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.BOARDS,true,false,boards,0),CancellationToken.none());
        assertEquals(10,ordered.get(1)[0].x); assertEquals(110,ordered.get(2)[0].x);
        var global=CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.NEAREST,true,false,List.of(),0),CancellationToken.none());
        assertEquals(110,global.get(1)[0].x);
    }
    @Test void ambiguousOutsideAndInvalidReferencesAreRefusedAndCancellationIsHonoured() throws Exception {
        Geometry g=read("LINESTRING (1 1,2 1)"); String board=f.toGeometry(new Envelope(0,10,0,10)).toText();
        assertThrows(IllegalArgumentException.class,() -> CncRouting.order(g,0,0,0,new CncRouting(CncRouting.Mode.BOARDS,true,false,List.of(board,board),0),CancellationToken.none()));
        assertThrows(IllegalArgumentException.class,() -> CncRouting.order(read("LINESTRING (30 0,40 0)"),0,0,0,new CncRouting(CncRouting.Mode.BOARDS,true,false,List.of(board),0),CancellationToken.none()));
        assertThrows(IllegalArgumentException.class,() -> new CncRouting(CncRouting.Mode.BOARDS,true,false,List.of(),0));
        assertThrows(java.util.concurrent.CancellationException.class,() -> CncRouting.order(g,0,0,0,CncRouting.LEGACY,() -> true));
    }
    @Test void drillOptimizationReordersWithinAToolWithoutDroppingRepeatedHoles() {
        var drills=List.of(new ExcellonImage.Drill(1,1,0),new ExcellonImage.Drill(1,100,0),new ExcellonImage.Drill(1,2,0),new ExcellonImage.Drill(1,99,0),new ExcellonImage.Drill(1,2,0));
        var image=ExcellonImage.of("MM",Map.of(1,1.0),drills,List.of(),f.createPolygon());
        var options=new GCodeGenerator.DrillJobOptions(false,15,3,0.0,0.0);
        var settings=Map.of(1,new DrillGCodeParameters(3,1,100,0,false));
        String code=GCodeGenerator.generateDrillCncJob(image,settings,List.of(1),options.withOptimizedDrills(true)).gcode();
        assertTrue(code.indexOf("X2.0000")<code.indexOf("X99.0000")); assertTrue(code.indexOf("X99.0000")<code.indexOf("X100.0000"));
        assertEquals(5,code.lines().filter(s -> s.startsWith("G1 Z-1.0000")).count()); assertEquals(drills,image.drills());
        assertTrue(options.withOptimizedDrills(true).withPositions(null,null,null).withExclusions(false,List.of()).optimizeDrills());
    }
    @Test void geometryGeneratorActuallyUsesTheRoutingPolicy() throws Exception {
        Geometry g=read("MULTILINESTRING ((0 0,10 0,10 10,0 10,0 0),(3 3,7 3,7 7,3 7,3 3))");
        var options=GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.NEAREST,true,true,List.of(),0));
        var params=new GeometryGCodeParameters(3,.2,false,1,100,0,false).withJobOptions(options);
        String code=GCodeGenerator.generateGeometryCncJob("MM",g,params,.2,CancellationToken.none()).gcode();
        assertTrue(code.indexOf("X3.0000 Y3.0000")<code.indexOf("X0.0000 Y0.0000"),code);
    }
}
