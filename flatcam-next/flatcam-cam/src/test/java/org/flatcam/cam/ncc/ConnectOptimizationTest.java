package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

class ConnectOptimizationTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static LineString line(double... xy){var c=new Coordinate[xy.length/2];for(int i=0;i<c.length;i++)c[i]=new Coordinate(xy[i*2],xy[i*2+1]);return F.createLineString(c);}
    private static List<String> text(List<LineString> paths){return paths.stream().map(Geometry::toText).toList();}
    private static void sameAsHistorical(List<LineString> paths,Geometry area,double diameter) {
        var before=text(paths);var expected=LegacyConnectOracle.connect(paths,area,diameter,F,CancellationToken.NONE);
        var actual=NccGenerator.connectSafePaths(paths,area,diameter,F,CancellationToken.NONE);
        assertEquals(text(expected),text(actual),"path order, direction, duplicate points and lift boundaries must match");
        assertEquals(before,text(paths),"Connect must not mutate source paths");
    }
    private static final class CountingLine extends LineString {
        int coordinateReads;
        CountingLine(Coordinate[] points){super(F.getCoordinateSequenceFactory().create(points),F);}
        @Override public Coordinate[] getCoordinates(){coordinateReads++;return super.getCoordinates();}
    }
    private static final class CountingRing extends LinearRing {
        int coordinateReads;
        CountingRing(Coordinate[] points){super(F.getCoordinateSequenceFactory().create(points),F);}
        @Override public Coordinate[] getCoordinates(){coordinateReads++;return super.getCoordinates();}
    }
    @Test void repeatedSafetyChecksDoNotRebuildTheWholeBoundaryForEveryConnector() {
        var hole=new CountingRing(F.createPoint(new Coordinate(70,70)).buffer(10,32).getCoordinates());
        var shell=F.createLinearRing(new Coordinate[]{new Coordinate(0,0),new Coordinate(100,0),new Coordinate(100,100),new Coordinate(0,100),new Coordinate(0,0)});
        var area=F.createPolygon(shell,new LinearRing[]{hole});var paths=new ArrayList<LineString>();
        for(int row=0;row<64;row++) {
            double y=1+row*.4;paths.add(row%2==0?line(1,y,10,y):line(10,y,1,y));
        }
        var result=NccGenerator.connectSafePaths(paths,area,.2,F,CancellationToken.NONE);
        assertEquals(1,result.size());
        assertTrue(hole.coordinateReads<=4,"boundary data should be reused across safety checks; boundary reads="+hole.coordinateReads);
    }
    @Test void nearestSearchFetchesCoordinatesAtMostOncePerPath() {
        var paths=new ArrayList<LineString>();
        for(int row=0;row<64;row++) {
            var points=new Coordinate[512];for(int i=0;i<points.length;i++)points[i]=new Coordinate(i*.001,row*10);
            paths.add(new CountingLine(points));
        }
        var result=NccGenerator.connectSafePaths(paths,F.toGeometry(new Envelope(-1,2,-1,650)),.01,F,CancellationToken.NONE);
        assertEquals(paths.size(),result.size());
        int reads=paths.stream().mapToInt(path->((CountingLine)path).coordinateReads).sum();
        assertTrue(reads<=paths.size(),"one coordinate fetch per source path is sufficient; coordinate fetches="+reads);
    }
    @Test void equidistantEndpointsPreserveOriginalIndexThenStartPreference() {
        sameAsHistorical(List.of(line(0,0,10,0),line(11,1,11,-1),line(11,-1,12,0),line(11,1,12,2)),
                F.toGeometry(new Envelope(-5,20,-5,20)),.2);
        // sqrt rounds both distances to 1: squared-distance tie breaking must not change the legacy result.
        sameAsHistorical(List.of(line(-.1,0,0,0),line(1,1e-9,2,1e-9),line(1,0,2,0)),
                F.toGeometry(new Envelope(-5,5,-5,5)),.2);
    }
    @Test void coincidentAndClosedEndpointsRemainDeterministicAfterManyRemovals() {
        var paths=new ArrayList<LineString>();
        for(int i=0;i<180;i++)paths.add(i%3==0?line(0,0,0,0):i%3==1?line(0,0,1,1,0,0):line(0,0,-1,1,0,0));
        sameAsHistorical(paths,F.toGeometry(new Envelope(-3,3,-3,3)),.1);
    }
    @ParameterizedTest @ValueSource(doubles={1,0.03937007874015748})
    void tangentAndJustOutsideConnectorsMatchTheHistoricalBoundaryDecision(double scale) {
        Geometry area=F.toGeometry(new Envelope(0,10*scale,0,10*scale))
                .difference(F.toGeometry(new Envelope(4*scale,6*scale,4*scale,6*scale)));
        for(double x:new double[]{.25*scale,Math.nextDown(.25*scale),3.75*scale,Math.nextUp(3.75*scale)}) {
            double y=x<scale?.25*scale:4.25*scale;
            sameAsHistorical(List.of(line(x,y,x,y+.75*scale),line(x,y+.95*scale,x,y+1.75*scale)),area,.5*scale);
        }
    }
    @ParameterizedTest @ValueSource(doubles={1,0.03937007874015748})
    void randomizedPathsMatchHistoricalOrderAndSafetyAcrossUnits(double scale) {
        var random=new Random(0xC011EC7);
        Geometry area=F.toGeometry(new Envelope(-20,20,-20,20))
                .difference(F.createPoint(new Coordinate()).buffer(3,32));
        var transform=AffineTransformation.scaleInstance(scale,scale);
        for(int fixture=0;fixture<12;fixture++) {
            var paths=new ArrayList<LineString>();
            for(int i=0;i<80;i++) {
                double x=random.nextInt(35)-17,y=random.nextInt(35)-17;
                var path=line(x,y,x+random.nextDouble()*1.2,y+random.nextDouble()*1.2);
                paths.add((LineString)transform.transform(path));
            }
            sameAsHistorical(paths,transform.transform(area),.5*scale);
        }
    }
    @Test void passthroughAndCancellationRemainAvailable() {
        var one=List.of(line(0,0,1,0));var area=F.toGeometry(new Envelope(-2,2,-2,2));
        assertSame(one,NccGenerator.connectSafePaths(one,area,.1,F,CancellationToken.NONE));
        assertThrows(CancellationException.class,()->NccGenerator.connectSafePaths(List.of(one.getFirst(),line(1,1,0,1)),area,.1,F,()->true));
    }
}
