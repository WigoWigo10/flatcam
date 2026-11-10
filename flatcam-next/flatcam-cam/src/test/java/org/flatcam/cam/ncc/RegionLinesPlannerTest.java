package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTReader;

class RegionLinesPlannerTest {
    final GeometryFactory factory=new GeometryFactory();

    @ParameterizedTest @CsvSource({"MM,0","MM,37.5","MM,90","IN,0","IN,37.5","IN,90"})
    void keepsEveryRasterSegmentAndFinishingRing(String units,double angle)throws Exception {
        double scale=units.equals("IN")?1/25.4:1;
        Geometry area=new WKTReader().read("POLYGON ((0 0,24 0,24 18,0 18,0 0),(7 4,7 14,17 14,17 4,7 4))");
        // Rounded pads plus narrow disconnected islands exercise splitting, merging and supplement paths.
        area=area.difference(factory.createPoint(new Coordinate(4,9)).buffer(2,64));
        area=area.union(factory.toGeometry(new Envelope(26,26.6,1,1.6)));
        area=AffineTransformation.scaleInstance(scale,scale).transform(area);String before=area.toText();
        for(boolean contour:List.of(false,true)) {
            var ordinary=paint(units,area,.4*scale,angle,contour,NccMethod.LINES,false);
            var regional=paint(units,area,.4*scale,angle,contour,NccMethod.REGION_LINES,false);
            assertEquals(multiset(ordinary.geometry()),multiset(regional.geometry()),"Only order/direction may change");
            assertEquals(ordinary.totalFailedPolygonCount(),regional.totalFailedPolygonCount());
            assertEquals(before,area.toText());
        }
    }
    @Test void completesCellsRatherThanAlternatingBetweenDistantSides()throws Exception {
        Geometry safe=new WKTReader().read("POLYGON ((0 0,12 0,12 9,0 9,0 0),(4 1,4 8,8 8,8 1,4 1))");
        var rows=new ArrayList<LineString>();
        for(int y=7;y>=2;y--) {rows.add(line(0,y,4,y));rows.add(line(8,y,12,y));}
        var result=RegionLinesPlanner.order(rows,safe,0,1,CancellationToken.NONE);
        assertEquals(12,result.size());
        for(int i=0;i<6;i++)assertTrue(result.get(i).getEnvelopeInternal().getMaxX()<=4);
        for(int i=6;i<12;i++)assertTrue(result.get(i).getEnvelopeInternal().getMinX()>=8);
        assertTrue(travel(result)<travel(rows)*.5,"Finish a side before crossing to the other side");
    }
    @Test void orderedConnectRejectsShortcutsThroughRoundPadsAndOverLongMoves() {
        Geometry area=factory.toGeometry(new Envelope(0,30,0,10)).difference(factory.createPoint(new Coordinate(5,5)).buffer(1,64));
        var paths=List.of(line(1,5,3,5),line(7,5,9,5),line(25,5,27,5));
        var result=NccGenerator.connectOrderedPaths(paths,area,1,factory,CancellationToken.NONE);
        assertEquals(3,result.size());assertEquals(paths,result);
        var safe=NccGenerator.connectOrderedPaths(List.of(line(1,1,3,1),line(3,2,1,2)),area,1,factory,CancellationToken.NONE);
        assertEquals(1,safe.size());assertTrue(area.covers(safe.get(0).buffer(.5,64)));
        assertThrows(CancellationException.class,()->NccGenerator.connectOrderedPaths(paths,area,1,factory,()->true));
    }
    @ParameterizedTest @CsvSource({"MM,1","IN,0.03937007874015748"})
    void sourceOrderedGcodePreservesTheRegionalPassSequence(String units,double scale)throws Exception {
        var area=AffineTransformation.scaleInstance(scale,scale).transform(new WKTReader().read(
                "POLYGON ((0 0,24 0,24 18,0 18,0 0),(7 2,7 16,17 16,17 2,7 2))"));
        var result=paint(units,area,.6*scale,0,true,NccMethod.REGION_LINES,true);
        var tools=List.of(new ToolGeometry(.6*scale,result.geometry()));
        var p=new GeometryGCodeParameters(3*scale,.1*scale,false,0,300*scale,10000,false).withJobOptions(
                GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
        var parsed=GCodeToolpathParser.parse(GCodeGenerator.generateGeometryCncJob(units,tools,p).gcode(),CancellationToken.NONE,ProgressCallback.none());
        assertTrue(parsed.plotAvailable(),parsed.warning());
        // The preview represents a vertical plunge by a repeated XY coordinate. Compare ordered XY moves.
        Coordinate[] source=distinct(result.geometry().getCoordinates()),actual=distinct(parsed.cutCenterlines().getCoordinates());
        assertEquals(source.length,actual.length);
        double bound=units.equals("IN")?1e-6:1e-4;
        for(int i=0;i<source.length;i++)assertTrue(source[i].distance(actual[i])<=bound,"G-code must keep the planned coordinate sequence");
    }
    @Test void cancelsCellPlanningAndKeepsPythonMethodIdentifiers() {
        assertArrayEquals(new NccMethod[]{NccMethod.STANDARD,NccMethod.SEED,NccMethod.LINES,NccMethod.COMBO},NccMethod.legacyValues());
        assertThrows(CancellationException.class,()->RegionLinesPlanner.order(List.of(line(0,0,1,0),line(0,1,1,1)),factory.toGeometry(new Envelope(0,2,0,2)),0,1,()->true));
    }
    NccResult paint(String units,Geometry area,double d,double angle,boolean contour,NccMethod method,boolean connect) {
        return NccGenerator.paint(units,area,new PaintParameters(List.of(d),.4,0,method,connect,contour,NccOrder.NONE,false,
                Map.of(d,new PaintToolSettings(.4,0,method,connect,contour,angle))),CancellationToken.NONE,ProgressCallback.none());
    }
    LineString line(double x,double y,double xx,double yy){return factory.createLineString(new Coordinate[]{new Coordinate(x,y),new Coordinate(xx,yy)});}
    static Coordinate[] distinct(Coordinate[] p){var result=new ArrayList<Coordinate>();for(var c:p)if(result.isEmpty()||!result.getLast().equals2D(c))result.add(c);return result.toArray(Coordinate[]::new);}
    static double travel(List<LineString> lines){double sum=0;for(int i=1;i<lines.size();i++)sum+=lines.get(i-1).getEndPoint().distance(lines.get(i).getStartPoint());return sum;}
    static Map<String,Integer> multiset(Geometry geometry){var counts=new TreeMap<String,Integer>();for(int i=0;i<geometry.getNumGeometries();i++){
        Geometry line=geometry.getGeometryN(i).copy();line.normalize();counts.merge(line.toText(),1,Integer::sum);
    }return counts;}
}
