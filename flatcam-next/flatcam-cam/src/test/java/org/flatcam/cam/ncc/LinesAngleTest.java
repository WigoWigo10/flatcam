package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class LinesAngleTest {
    private static final GeometryFactory F=new GeometryFactory();
    private static final Geometry TALL=F.toGeometry(new Envelope(-3,3,5,17));
    private static NccResult paint(Geometry area, Double angle, NccMethod method) {
        return NccGenerator.paint("MM",area,new PaintParameters(List.of(.8),.3,0,method,false,false,NccOrder.NONE,false,
                Map.of(.8,new PaintToolSettings(.3,0,method,false,false,angle))),CancellationToken.NONE,ProgressCallback.none());
    }
    @ParameterizedTest @ValueSource(doubles={0,30.5123456,45,90,135,179.9})
    void everyRasterSegmentHasTheRequestedAngleAndLeavesTheSourceUntouched(double angle) {
        String before=TALL.toText();
        var result=paint(TALL,angle,NccMethod.LINES); assertFalse(result.isEmpty());
        for(int i=0;i<result.geometry().getNumGeometries();i++) {
            var path=result.geometry().getGeometryN(i);var c=path.getCoordinates();
            assertEquals(2,c.length);
            double measured=Math.atan2(c[1].y-c[0].y,c[1].x-c[0].x);
            assertEquals(0,Math.sin(measured-Math.toRadians(angle)),1e-10);
            assertTrue(TALL.covers(path));
        }
        assertEquals(before,TALL.toText());
    }
    @Test void automaticPreservesTheLegacyTallObjectDirectionAndComboUsesLinesFirst() {
        var legacy=NccGenerator.paint("MM",TALL,new PaintParameters(List.of(.8),.3,0,NccMethod.LINES,false,false,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
        assertEquals(legacy.geometry().toText(),paint(TALL,null,NccMethod.LINES).geometry().toText());
        for(var c:legacy.geometry().getCoordinates())assertTrue(Double.isFinite(c.x));
        var first=legacy.geometry().getGeometryN(0).getCoordinates();assertEquals(first[0].x,first[1].x);
        assertEquals(paint(TALL,45.0,NccMethod.LINES).geometry().toText(),paint(TALL,45.0,NccMethod.COMBO).geometry().toText());
    }
    @Test void restRetainsEachToolsAngleWhileUsingCommonConnectAndContour() {
        var selected=new NccToolSettings(.3,NccMethod.LINES,false,false,.1,NccSeedPolicy.STABLE,37.25);
        var p=new NccParameters(List.of(.8),.4,0,NccMethod.STANDARD,true,true,0,true,NccOrder.NONE,new NccBoundary.Itself(),List.of(),Map.of(.8,selected));
        assertEquals(37.25,p.settingsFor(.8).lineAngleDegrees());assertTrue(p.settingsFor(.8).connect());assertTrue(p.settingsFor(.8).contour());
        assertEquals(0,p.settingsFor(.8).copperOffset());
    }
    @ParameterizedTest @ValueSource(doubles={-1,180,Double.NaN,Double.POSITIVE_INFINITY})
    void invalidAnglesAreRejected(double angle) {
        assertThrows(IllegalArgumentException.class,()->new PaintToolSettings(.3,0,NccMethod.LINES,true,true,angle));
        assertThrows(IllegalArgumentException.class,()->new NccToolSettings(.3,NccMethod.LINES,true,true,0,NccSeedPolicy.STABLE,angle));
    }
}
