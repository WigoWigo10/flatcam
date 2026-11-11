package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class SpiralClearingTest {
    private static final GeometryFactory F=new GeometryFactory();
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void aRoundPocketHasOneProgressivelyExpandingPathRatherThanSeparateRings(String units) {
        double s=units.equals("IN")?1/25.4:1;
        Geometry pocket=F.createPoint(new Coordinate(0,0)).buffer(8*s,64);String before=pocket.toText();
        var result=paint(units,pocket,.8*s,false,false,false);
        assertEquals(1,result.pathCount());Coordinate[] points=result.geometry().getCoordinates();double radius=-1;
        for(var point:points){double r=Math.hypot(point.x,point.y);assertTrue(r>=radius-1e-9*s);radius=r;}
        assertTrue(points[0].distance(new Coordinate(0,0))<.001*s);assertTrue(radius>7.5*s);
        double theta=0,previous=0;
        for(int i=1;i<points.length;i++) {
            double angle=Math.atan2(points[i].y,points[i].x),delta=angle-previous;if(delta<0)delta+=2*Math.PI;
            theta+=delta;previous=angle;
            // The final point may be an intersection inside the last adaptive chord.
            assertEquals(.8*s*.6*theta/(2*Math.PI),Math.hypot(points[i].x,points[i].y),(i==points.length-1?.0008:1e-8)*s,"Pitch must honor the requested cutter/overlap");
        }
        assertEquals(before,pocket.toText());
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void islandsDisconnectedComponentsAndStockStayInsideTheOriginalAllowedArea(String units) {
        double s=units.equals("IN")?1/25.4:1;
        Geometry area=F.toGeometry(new Envelope(0,24*s,0,16*s)).difference(F.createPoint(new Coordinate(7*s,8*s)).buffer(2*s,64));
        area=area.difference(F.toGeometry(new Envelope(13*s,19*s,5*s,11*s)).buffer(.5*s,64));
        area=area.union(F.toGeometry(new Envelope(26*s,30*s,0,4*s)));String before=area.toText();
        var result=paint(units,area,.6*s,true,true,true);
        for(var tool:result.toolResults())if(!tool.isEmpty()) {
            assertTrue(area.buffer(-tool.toolDiameter()*.499).covers(tool.geometry()),"Every point must retain almost the full cutter clearance");
        }
        assertTrue(result.geometry().getEnvelopeInternal().getMaxX()>29*s,"Disconnected region must also receive a spiral");
        assertEquals(before,area.toText());
        var tools=result.toolResults().stream().filter(t->!t.isEmpty()).map(t->new ToolGeometry(t.toolDiameter(),t.geometry())).toList();
        var own=new HashMap<Integer,Geometry>();for(int i=0;i<tools.size();i++)own.put(i,area);
        var target=new MachiningTarget("PAINT",units,area,own,MachiningTarget.signature(tools,CancellationToken.NONE));
        var coverage=MachiningCoverage.calculate(target,units,tools,new GeometryGCodeParameters(3*s,.1*s,false,0,300*s,10000,false),Map.of(),CancellationToken.NONE,ProgressCallback.none());
        var access=MachiningReachability.calculate(area,.3*s,CancellationToken.NONE);
        var stock=MachiningReachability.classify(coverage.finalRemaining(),access,.001*s,CancellationToken.NONE);
        assertTrue(stock.stableCore().getArea()<.001*s*s,"Contour + spiral must cover the tested reachable regions");
    }
    @Test void preservesRestCornerAccessAndExplicitCutterDiameter() {
        Geometry area=F.toGeometry(new Envelope(0,4,0,2));
        var result=paint("MM",area,.396,false,true,true);
        assertEquals(.198,result.toolResults().get(1).toolDiameter());
        var witness=F.createPoint(new Coordinate(.045,.045));
        assertTrue(result.toolResults().get(1).geometry().distance(witness)<.099);
    }
    @Test void cancellationAndExcessiveSamplingAreExplicit() {
        var p=(Polygon)F.toGeometry(new Envelope(0,10,0,10));
        var settings=new NccToolSettings(.4,NccMethod.SPIRAL,false,true,0);
        assertThrows(java.util.concurrent.CancellationException.class,()->SpiralClearing.generate(p,.4,settings,new SpiralClearing.Budget(),()->true));
        assertThrows(SpiralClearing.SampleLimitException.class,()->SpiralClearing.generate(p,1e-8,settings,new SpiralClearing.Budget(),CancellationToken.NONE));
        // The generator may retry radially, but must still refuse if the radial plan also exceeds the budget.
        String before=p.toText();
        assertThrows(SpiralClearing.SampleLimitException.class,()->paint("MM",p,1e-8,false,true,false));
        assertThrows(java.util.concurrent.CancellationException.class,()->NccGenerator.paint("MM",p,
                new PaintParameters(List.of(.4),.4,0,NccMethod.SPIRAL,false,true,NccOrder.NONE,false),()->true,ProgressCallback.none()));
        assertEquals(before,p.toText());
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void bothRestCuttersRetainTheirPathSequenceAndDirectionInActualGrblExport(String units) {
        double s=units.equals("IN")?1/25.4:1;
        Geometry area=F.toGeometry(new Envelope(0,6*s,0,4*s)).difference(F.createPoint(new Coordinate(2*s,2*s)).buffer(.6*s,32));
        var result=paint(units,area,.8*s,false,true,true);
        var source=new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0);
        var parameters=new GeometryGCodeParameters(3*s,.1*s,false,0,300*s,10000,false)
                .withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(source));
        double rounding=Math.sqrt(2)*.5*(units.equals("IN")?1e-6:1e-4)*1.001;
        for(var tool:result.toolResults()) {
            assertFalse(tool.isEmpty());
            String code=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(tool.toolDiameter(),tool.geometry())),parameters,Map.of(),CancellationToken.NONE,GCodePreprocessor.GRBL_11_NO_M6).gcode();
            var parsed=GCodeToolpathParser.parse(code,CancellationToken.NONE,ProgressCallback.none());assertTrue(parsed.plotAvailable(),parsed.warning());
            var actual=new ArrayList<LineString>();lines(parsed.cutCenterlines(),actual);var expected=new ArrayList<LineString>();lines(tool.geometry(),expected);
            assertEquals(expected.size(),actual.size(),"No source path may disappear or be split by the export");
            for(int i=0;i<actual.size();i++) {
                assertTrue(expected.get(i).getStartPoint().distance(actual.get(i).getStartPoint())<=rounding,"Source start order must survive");
                assertTrue(expected.get(i).getEndPoint().distance(actual.get(i).getEndPoint())<=rounding,"Source direction must survive");
                var index=new org.locationtech.jts.operation.distance.IndexedFacetDistance(actual.get(i));
                for(var vertex:expected.get(i).getCoordinates())assertTrue(index.distance(F.createPoint(vertex))<=rounding,"Every sampled spiral vertex must survive coordinate rounding");
            }
        }
    }
    @Test void legacyMethodIdsAndSpiralAngleIndependenceArePreserved() {
        assertArrayEquals(new NccMethod[]{NccMethod.STANDARD,NccMethod.SEED,NccMethod.LINES,NccMethod.COMBO},NccMethod.legacyValues());
        Geometry area=F.toGeometry(new Envelope(0,6,0,4));
        Geometry first=null;
        for(double angle:List.of(0.,37.5,90.)) {
            var settings=Map.of(.4,new PaintToolSettings(.4,0,NccMethod.SPIRAL,true,true,angle));
            var result=NccGenerator.paint("MM",area,new PaintParameters(List.of(.4),.4,0,NccMethod.SPIRAL,true,true,NccOrder.NONE,false,settings),CancellationToken.NONE,ProgressCallback.none());
            if(first!=null)assertTrue(first.equalsExact(result.geometry()),"Raster angle does not rotate or reorder the spiral");first=result.geometry();
        }
    }
    private static void lines(Geometry g,List<LineString> out){if(g instanceof LineString line)out.add(line);else if(g instanceof GeometryCollection)for(int i=0;i<g.getNumGeometries();i++)lines(g.getGeometryN(i),out);}
    private static NccResult paint(String units,Geometry area,double d,boolean connect,boolean contour,boolean rest){
        return NccGenerator.paint(units,area,new PaintParameters(rest?List.of(d,d/2):List.of(d),.4,0,NccMethod.SPIRAL,connect,contour,NccOrder.NONE,rest),CancellationToken.NONE,ProgressCallback.none());
    }
}
