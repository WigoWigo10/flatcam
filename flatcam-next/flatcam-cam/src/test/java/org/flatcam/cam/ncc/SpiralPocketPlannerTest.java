package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpiralPocketPlannerTest {
    final GeometryFactory f=new GeometryFactory();
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void elongatedPocketUsesBoundaryProfilesWithShortLinksInsteadOfCrossingItEveryTurn(String units){
        double s=units.equals("IN")?1/25.4:1,d=.4*s,pitch=.24*s;
        var center=(Polygon)f.toGeometry(new Envelope(0,30*s,0,6*s));String before=center.toText();
        var paths=SpiralPocketPlanner.generate(center,d,pitch,true,new SpiralClearing.Budget(),CancellationToken.NONE);
        assertFalse(paths.isEmpty());assertTrue(center.covers(f.buildGeometry(paths)));assertEquals(before,center.toText());
        double length=paths.stream().mapToDouble(LineString::getLength).sum();assertTrue(length<1.25*center.getArea()/pitch);
        var all=new ArrayList<>(paths);all.add(f.createLineString(center.getExteriorRing().getCoordinates()));
        Geometry allowed=center.buffer(.201*s,64);
        var connected=SpiralConnector.connect(all,allowed,center,d,true,CancellationToken.NONE);assertEquals(1,connected.size());
    }
    @Test void narrowNeckCreatesSeparateBranchesAndKeepsEveryBranch(){
        Geometry area=f.toGeometry(new Envelope(0,8,0,8)).union(f.toGeometry(new Envelope(12,20,0,8))).union(f.toGeometry(new Envelope(8,12,3.9,4.1)));
        var root=SpiralPocketPlanner.partition((Polygon)area,.4,.24,true,new SpiralClearing.Budget(),CancellationToken.NONE);
        assertEquals(2,root.children.size());
        var paths=SpiralPocketPlanner.generate((Polygon)area,.4,.24,true,new SpiralClearing.Budget(),CancellationToken.NONE);
        Geometry geometry=f.buildGeometry(paths);assertTrue(geometry.getEnvelopeInternal().getMinX()<1);assertTrue(geometry.getEnvelopeInternal().getMaxX()>19);
        assertTrue(area.covers(geometry));
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void thinPocketWithoutContourStillReceivesPathsInsideItsBoundary(String units){
        double s=units.equals("IN")?1/25.4:1;
        var area=(Polygon)f.toGeometry(new Envelope(0,10*s,0,.05*s));
        var paths=SpiralPocketPlanner.generate(area,.4*s,.24*s,false,new SpiralClearing.Budget(),CancellationToken.NONE);
        assertFalse(paths.isEmpty());Geometry result=f.buildGeometry(paths);assertTrue(area.contains(result));
        assertTrue(result.getEnvelopeInternal().getWidth()>9*s);
    }
    @Test void requestedStepIsAnUpperBoundAndAcuteCornersUseTheNominalRadius(){
        var rectangle=(Polygon)f.toGeometry(new Envelope(0,10,0,5));
        assertEquals(.24,SpiralPocketPlanner.cornerStep(rectangle,.4,.24));
        assertEquals(.2*(1+Math.sqrt(.5))*(1-1e-6),SpiralPocketPlanner.cornerStep(rectangle,.4,.4),1e-12);
        var triangle=f.createPolygon(new Coordinate[]{new Coordinate(0,0),new Coordinate(20,0),new Coordinate(0,1),new Coordinate(0,0)});
        double step=SpiralPocketPlanner.cornerStep(triangle,.4,.24);assertTrue(step>=.199999&&step<.21);
        assertThrows(java.util.concurrent.CancellationException.class,()->SpiralPocketPlanner.generate(rectangle,.4,.24,true,new SpiralClearing.Budget(),()->true));
        assertThrows(SpiralClearing.SampleLimitException.class,()->SpiralPocketPlanner.generate(rectangle,1e-8,6e-9,true,new SpiralClearing.Budget(),CancellationToken.NONE));
    }
}
