package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

class SpiralDenseBudgetTest {
    @ParameterizedTest @CsvSource({"MM,PAINT","IN,PAINT","MM,NCC","IN,NCC"})
    void denseRoundedPocketsFitTheExistingBudgetWithoutRadialRetry(String units,String operation){
        var f=new GeometryFactory();var board=(Polygon)f.toGeometry(new Envelope(0,50,0,38));
        var holes=new ArrayList<LinearRing>();for(int y=0;y<6;y++)for(int x=0;x<8;x++)holes.add(((Polygon)f.createPoint(new Coordinate(4+x*6,4+y*6)).buffer(.6,64)).getExteriorRing());
        double s=units.equals("IN")?1/25.4:1;var transform=AffineTransformation.scaleInstance(s,s);
        Geometry area=transform.transform(f.createPolygon(board.getExteriorRing(),holes.toArray(LinearRing[]::new)));String before=area.toText();Geometry boundary=transform.transform(board);
        var result=operation.equals("PAINT")?NccGenerator.paint(units,area,new PaintParameters(List.of(.4*s),.4,0,NccMethod.SPIRAL,false,true,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none())
                :NccGenerator.generate(units,boundary.difference(area),new NccParameters(List.of(.4*s),.4,0,NccMethod.SPIRAL,false,true,0,false,NccOrder.NONE,new NccBoundary.Area(boundary)),CancellationToken.NONE,ProgressCallback.none());
        assertFalse(result.isEmpty());assertFalse(result.toolResults().getFirst().regionDiagnostics().radialFallback(),"Repeated offsets must not exhaust the budget through vertex growth");
        assertEquals(.4*s,result.toolResults().getFirst().toolDiameter());assertEquals(before,area.toText());
        double clearance=new IndexedFacetDistance(area.getBoundary()).distance(result.geometry());
        assertTrue(clearance+.0002*s>=.2*s,"No speed gain may borrow cutter clearance");
        for(int y=0;y<5;y++)for(int x=0;x<7;x++){
            var point=f.createPoint(new Coordinate((7+x*6)*s,(7+y*6)*s));
            assertTrue(result.geometry().distance(point)<=.2*s+1e-10*s,"Every interior witness must remain covered");
        }
    }
}
