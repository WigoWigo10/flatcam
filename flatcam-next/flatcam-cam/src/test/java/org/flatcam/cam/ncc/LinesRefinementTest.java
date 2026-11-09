package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.union.UnaryUnionOp;

class LinesRefinementTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static Geometry box(double a,double b,double c,double d){return F.toGeometry(new Envelope(a,b,c,d));}
    private static NccResult generate(String operation,Geometry area,double scale,boolean contour,Double angle) {
        double diameter=.4*scale;
        if(operation.equals("Paint"))return NccGenerator.paint(scale==1?"MM":"IN",area,
                new PaintParameters(List.of(diameter),.4,0,NccMethod.LINES,false,contour,NccOrder.NONE,false,
                        Map.of(diameter,new PaintToolSettings(.4,0,NccMethod.LINES,false,contour,angle))),CancellationToken.NONE,ProgressCallback.none());
        var e=area.getEnvelopeInternal();var outside=box(e.getMaxX()+10*scale,e.getMaxX()+11*scale,e.getMaxY()+10*scale,e.getMaxY()+11*scale);
        return NccGenerator.generate(scale==1?"MM":"IN",outside,
                new NccParameters(List.of(diameter),.4,0,NccMethod.LINES,false,contour,0,false,NccOrder.NONE,
                        new NccBoundary.Area(area),List.of(),Map.of(diameter,new NccToolSettings(.4,NccMethod.LINES,false,contour,0,NccSeedPolicy.STABLE,angle))));
    }
    @ParameterizedTest @CsvSource({"Paint,1","NCC,1","Paint,0.03937007874015748","NCC,0.03937007874015748"})
    void rasterDoesNotRecutTheContourAndFinishingRemainsClosed(String operation,double scale) {
        var area=box(0,10*scale,0,8*scale);var result=generate(operation,area,scale,true,null);
        double duplicate=result.geometry().getLength()-UnaryUnionOp.union(result.geometry()).getLength();
        assertEquals(0,duplicate,1e-8*scale,"raster must leave a coincident finishing segment to Contour");
        assertTrue(java.util.stream.IntStream.range(0,result.geometry().getNumGeometries())
                .anyMatch(i->result.geometry().getGeometryN(i) instanceof LineString line && line.isClosed()),"the full finishing loop must remain closed");
        var reachable=area.buffer(-.2*scale,64).buffer(.2*scale,64);
        assertTrue(reachable.difference(result.geometry().buffer(.2*scale,64)).getArea()/reachable.getArea()<1e-5);
    }
    @ParameterizedTest @CsvSource({"Paint,1,0","NCC,1,0","Paint,0.03937007874015748,0","NCC,0.03937007874015748,0",
            "Paint,1,37.5","NCC,1,37.5"})
    void aReachableSmallRegionReceivesALineWithoutEnablingContour(String operation,double scale,double angle) {
        var area=F.createPoint(new Coordinate(0,0)).buffer(.21*scale,64);String original=area.toText();
        var result=generate(operation,area,scale,false,angle);
        assertFalse(result.isEmpty(),"a positive cutter inset cannot be silently skipped by the raster phase");
        assertEquals(0,result.totalFailedPolygonCount());
        var diagnostics=result.toolResults().getFirst().regionDiagnostics();
        assertEquals(1,diagnostics.supplementedCount());assertEquals(0,diagnostics.unservedCount());assertEquals(0,diagnostics.unreachableCount());
        for(int i=0;i<result.geometry().getNumGeometries();i++) {
            var line=(LineString)result.geometry().getGeometryN(i);assertFalse(line.isClosed(),"Contour must stay off");
            var c=line.getCoordinates();assertEquals(0,Math.sin(Math.atan2(c[c.length-1].y-c[0].y,c[c.length-1].x-c[0].x)-Math.toRadians(angle)),1e-9);
        }
        assertEquals(original,area.toText());
    }
    @ParameterizedTest @CsvSource({"Paint,1,0","NCC,1,0","Paint,0.03937007874015748,0","NCC,0.03937007874015748,0",
            "Paint,1,37.5","NCC,1,37.5"})
    void everyDisconnectedCutterInsetComponentReceivesPaths(String operation,double scale,double angle) {
        Geometry area=box(0,10,0,6).union(F.createPoint(new Coordinate(12,1.05)).buffer(.21,64))
                .union(F.createLineString(new Coordinate[]{new Coordinate(9.8,1.05),new Coordinate(12,1.05)}).buffer(.05,16));
        var transform=AffineTransformation.rotationInstance(Math.toRadians(angle));
        area=AffineTransformation.scaleInstance(scale,scale).transform(transform.transform(area));
        var center=transform.transform(new Coordinate(12,1.05),new Coordinate());center.x*=scale;center.y*=scale;
        assertTrue(area.isValid());var result=generate(operation,area,scale,false,angle);
        assertTrue(result.geometry().distance(F.createPoint(center))<.011*scale,"the large component must not hide a missed small island");
        assertEquals(1,result.toolResults().getFirst().regionDiagnostics().supplementedCount());
        assertEquals(0,result.toolResults().getFirst().regionDiagnostics().unservedCount());
    }
    @ParameterizedTest @CsvSource({"Paint,1","NCC,1","Paint,0.03937007874015748","NCC,0.03937007874015748"})
    void aCutterThatDoesNotFitIsNotReportedAsAReachableGenerationGap(String operation,double scale) {
        var area=F.createPoint(new Coordinate()).buffer(.1*scale,64);var result=generate(operation,area,scale,false,0.0);
        assertTrue(result.isEmpty());var diagnostics=result.toolResults().getFirst().regionDiagnostics();
        assertEquals(1,diagnostics.unreachableCount());assertEquals(0,diagnostics.unservedCount());assertEquals(0,diagnostics.supplementedCount());
        assertTrue(diagnostics.unreachableArea().equalsTopo(area));
    }
    @Test void restReportsEachCutterSeparatelyAndTheSmallerOneCanReachTheIsland() {
        Geometry area=box(0,10,0,6).union(F.createPoint(new Coordinate(12,1.05)).buffer(.21,64));
        var result=NccGenerator.paint("MM",area,new PaintParameters(List.of(.8,.4),.4,0,NccMethod.LINES,false,false,NccOrder.NONE,true),CancellationToken.NONE,ProgressCallback.none());
        assertEquals(2,result.toolResults().size());
        var large=result.toolResults().getFirst();var small=result.toolResults().getLast();
        assertEquals(.8,large.toolDiameter());assertEquals(1,large.regionDiagnostics().unreachableCount());
        assertTrue(small.regionDiagnostics().supplementedCount()>=1);
        assertEquals(0,small.regionDiagnostics().unservedCount());
        assertTrue(small.geometry().distance(F.createPoint(new Coordinate(12,1.05)))<.011);
    }
    @Test void anEndpointTouchAloneDoesNotCountAsAVisitedComponent() {
        var area=box(0,1,0,1);
        var touch=F.createLineString(new Coordinate[]{new Coordinate(-1,0),new Coordinate(0,0)});
        assertEquals(1,LinesRasterRefinement.unvisitedComponents(area,List.of(touch),CancellationToken.NONE).size());
    }
    @ParameterizedTest @CsvSource({"Paint,1","NCC,1","Paint,0.03937007874015748","NCC,0.03937007874015748"})
    void exportedGcodeRetainsTheRefinedPassesWithoutContourRetrace(String operation,double scale) {
        var result=generate(operation,box(0,10*scale,0,8*scale),scale,true,null);
        var tool=result.toolResults().getFirst();
        var job=org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob(result.units(),
                List.of(new org.flatcam.cam.geometry.ToolGeometry(tool.toolDiameter(),tool.geometry())),
                new org.flatcam.cam.gcode.GeometryGCodeParameters(3*scale,.1*scale,false,0,300*scale,0,false));
        var parsed=org.flatcam.cam.gcode.GCodeToolpathParser.parse(job.gcode(),CancellationToken.NONE,ProgressCallback.none());
        assertTrue(parsed.plotAvailable(),parsed.warning());var cuts=parsed.cutCenterlines();
        assertEquals(result.totalLength(),cuts.getLength(),result.totalLength()*.001);
        assertEquals(0,cuts.getLength()-UnaryUnionOp.union(cuts).getLength(),.001*scale);
        assertTrue(result.geometry().buffer(.003*scale).covers(cuts));
    }
}
