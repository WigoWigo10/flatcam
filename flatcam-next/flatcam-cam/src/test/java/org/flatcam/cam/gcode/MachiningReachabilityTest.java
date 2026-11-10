package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class MachiningReachabilityTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static Geometry box(double a,double b,double c,double d){return F.toGeometry(new Envelope(a,b,c,d));}
    @ParameterizedTest @ValueSource(doubles={1,0.03937007874015748})
    void rectangularCornerLossMatchesTheCircularOpeningAndUnits(double scale) {
        var target=box(0,10*scale,0,2*scale);String before=target.toText();double r=.2*scale;
        var result=MachiningReachability.calculate(target,2*r,CancellationToken.NONE);
        int q=MachiningReachability.QUADRANT_SEGMENTS;double circle=2*q*r*r*Math.sin(Math.PI/(2*q));
        assertEquals(4*r*r-circle,result.unreachable().getArea(),1e-9*scale*scale);
        assertEquals(target.getArea(),result.unreachable().getArea()+result.reachable().getArea(),1e-9*scale*scale);
        assertEquals(before,target.toText());
    }
    @Test void anEntirelyTooNarrowRegionIsUnreachableRatherThanAGenerationGap() {
        var target=box(0,10,0,.3);var result=MachiningReachability.calculate(target,.4,CancellationToken.NONE);
        assertTrue(result.cutterCenters().isEmpty());assertEquals(0,result.reachable().getArea());assertEquals(3,result.unreachable().getArea(),1e-12);
    }
    @Test void thinRestStockCanBeReachableThroughPreviouslyClearedSpace() {
        var allowed=box(0,10,0,2);var remainder=box(1,9,.995,1.005);
        assertTrue(MachiningReachability.calculate(remainder,.4,CancellationToken.NONE).reachable().isEmpty());
        var access=MachiningReachability.calculate(allowed,.4,CancellationToken.NONE);
        var result=MachiningReachability.classify(remainder,access,.0001,CancellationToken.NONE);
        assertEquals(.08,result.reachableUncut().getArea(),1e-10);assertTrue(result.coreBeyondTolerance().getArea()>.07);
        assertEquals(0,result.outsideReachable().getArea(),1e-12);
    }
    @Test void aSubToleranceStripRemainsVisibleInTheRawReport() {
        var access=MachiningReachability.calculate(box(0,10,0,2),.4,CancellationToken.NONE);
        var result=MachiningReachability.classify(box(1,9,.99999,1.00001),access,.0001,CancellationToken.NONE);
        assertTrue(result.reachableUncut().getArea()>0);assertTrue(result.coreBeyondTolerance().isEmpty());
    }
    @Test void invalidInputsAndCancellationAreNotReportedAsCoverageSuccess() {
        var area=box(0,1,0,1);
        assertThrows(IllegalArgumentException.class,()->MachiningReachability.calculate(area,0,CancellationToken.NONE));
        assertThrows(java.util.concurrent.CancellationException.class,()->MachiningReachability.calculate(area,.4,()->true));
    }
}
