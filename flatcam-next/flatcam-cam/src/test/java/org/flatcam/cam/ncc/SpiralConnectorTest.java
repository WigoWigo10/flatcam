package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class SpiralConnectorTest {
    final GeometryFactory f=new GeometryFactory();
    @Test void roundPocketFinishingStartsAtTheSpiralContactAndAvoidsItsSeparateEntry(){
        var area=(Polygon)f.createPoint(new Coordinate()).buffer(4,64);
        // Slightly conservative center boundary makes the full nominal disk strictly fit despite arc chords.
        var centers=(Polygon)area.buffer(-.2001,64);var ring=centers.getExteriorRing();
        var contact=ring.getCoordinateN((ring.getNumPoints()-1)/2);
        var paths=List.of(line(new Coordinate(),contact),f.createLineString(ring.getCoordinates()));
        var old=NccGenerator.connectOrderedPaths(paths,area,.4,f,CancellationToken.NONE);
        var result=SpiralConnector.connect(paths,area,centers,.4,true,CancellationToken.NONE);
        assertEquals(2,old.size());assertEquals(1,result.size());
        assertEquals(paths.getFirst().getCoordinateN(0),result.getFirst().getCoordinateN(0));
        assertTrue(f.buildGeometry(paths).difference(result.getFirst().buffer(1e-8,1)).getLength()<1e-8);
    }
    @Test void shortBoundaryWalkAvoidsTheRoundPadInsteadOfCuttingAcrossIt(){
        var area=(Polygon)f.toGeometry(new Envelope(-3,3,-2,2)).difference(f.createPoint(new Coordinate()).buffer(.7,64));
        var safe=(Polygon)area.buffer(-.2001,64);var hole=safe.getInteriorRingN(0);Coordinate a=hole.getCoordinateN(0),b=hole.getCoordinateN((hole.getNumPoints()-1)/2);
        var paths=List.of(line(new Coordinate(a.x*1.5,a.y*1.5),a),line(b,new Coordinate(b.x*1.5,b.y*1.5)));
        assertEquals(2,NccGenerator.connectOrderedPaths(paths,area,.4,f,CancellationToken.NONE).size());
        var joined=SpiralConnector.connect(paths,area,safe,.4,false,CancellationToken.NONE);assertEquals(1,joined.size());
        assertTrue(joined.getFirst().distance(f.createPoint(new Coordinate()))>=.8999,"Do not replace a boundary walk with a copper chord");
        assertTrue(area.contains(joined.getFirst()));assertTrue(joined.getFirst().distance(area.getBoundary())>=.2);
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void preservesCutsAndFinishingAndDoesNotIncreaseEntriesWithOrWithoutContour(String units){
        double s=units.equals("IN")?1/25.4:1,d=.6*s;
        var area=(Polygon)f.toGeometry(new Envelope(0,12*s,0,8*s)).difference(f.createPoint(new Coordinate(5*s,4*s)).buffer(1.2*s,64));
        String before=area.toText();
        for(boolean contour:List.of(false,true)){
            var raw=SpiralClearing.generate(area,d,new NccToolSettings(.4,NccMethod.SPIRAL,true,contour,0),new SpiralClearing.Budget(),CancellationToken.NONE);
            var old=NccGenerator.connectOrderedPaths(raw.paths(),area,d,f,CancellationToken.NONE);
            var fresh=SpiralConnector.connect(raw.paths(),area,raw.centers(),d,contour,CancellationToken.NONE);
            assertTrue(fresh.size()<=old.size());Geometry candidate=f.buildGeometry(fresh),required=f.buildGeometry(raw.paths());
            assertTrue(required.difference(candidate.buffer(1e-8*s,1)).getLength()<1e-7*s,"No original cut interval or finishing edge may disappear");
            assertTrue(candidate.distance(f.createPoint(new Coordinate(5*s,4*s)))>=(1.2+d/(2*s)-.0001)*s);
        }
        assertEquals(before,area.toText());
        assertThrows(java.util.concurrent.CancellationException.class,()->SpiralConnector.connect(List.of(line(new Coordinate(1,1),new Coordinate(2,1)),line(new Coordinate(3,1),new Coordinate(4,1))),area,area,d,false,()->true));
    }
    @Test void finishesOneDisconnectedPocketBeforeVisitingTheOther(){
        Geometry left=f.toGeometry(new Envelope(-3,3,-2,2)).difference(f.createPoint(new Coordinate()).buffer(.7,64));
        Geometry area=left.union(f.toGeometry(new Envelope(10,14,-2,2))),centers=area.buffer(-.2001,64);
        var a=(Polygon)centers.getGeometryN(0);var b=(Polygon)centers.getGeometryN(1);
        if(a.getEnvelopeInternal().getMinX()>b.getEnvelopeInternal().getMinX()){var swap=a;a=b;b=swap;}
        var hole=a.getInteriorRingN(0);int half=(hole.getNumPoints()-1)/2;var source=new ArrayList<LineString>();
        for(int i=0;i<5;i++){
            int start=i%2==0?0:half;var first=hole.getCoordinateN(start);var last=hole.getCoordinateN(start+1);
            source.add(f.createLineString(new Coordinate[]{first,new Coordinate(first.x*1.5,first.y*1.5),new Coordinate(last.x*1.5,last.y*1.5),last}));
        }
        source.add(f.createLineString(a.getExteriorRing().getCoordinates()));source.add(f.createLineString(hole.getCoordinates()));
        source.add(line(new Coordinate(12,0),new Coordinate(12.5,0)));source.add(f.createLineString(b.getExteriorRing().getCoordinates()));
        var fresh=SpiralConnector.connect(source,area,centers,.4,true,CancellationToken.NONE);
        assertEquals(2,fresh.size());assertTrue(fresh.getFirst().getEnvelopeInternal().getMaxX()<4);
        assertTrue(fresh.getLast().getEnvelopeInternal().getMinX()>9,"Finishing must not return to a distant previous pocket");
    }
    private LineString line(Coordinate a,Coordinate b){return f.createLineString(new Coordinate[]{a,b});}
}
