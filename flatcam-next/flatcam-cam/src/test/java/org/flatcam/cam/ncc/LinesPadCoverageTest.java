package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;

/** Round/rectangular pads plus their traces, in both public clearing entry points. */
class LinesPadCoverageTest {
    static final GeometryFactory F=new GeometryFactory();
    static Geometry box(double x1,double x2,double y1,double y2){return F.toGeometry(new Envelope(x1,x2,y1,y2));}
    @ParameterizedTest @CsvSource({"NCC,false,,1", "NCC,true,,1", "Paint,false,,1", "Paint,true,,1",
            "NCC,false,37.5,1", "NCC,true,37.5,1", "Paint,false,37.5,1", "Paint,true,37.5,1",
            "NCC,true,90,0.03937007874015748", "Paint,true,90,0.03937007874015748"})
    void linesAroundPadsRemainSafeAndContourFillsReachableEdgeGaps(String tool,boolean connect,Double angle,double scale) {
        Geometry board=box(0,40,0,24);
        Geometry copper=F.createPoint(new Coordinate(12,16)).buffer(3,64)
                .union(box(8,16,5,9)).union(box(4,8,5.5,6.5))
                .union(box(4,11,15,16)).union(box(29,33,11,17).buffer(.8,32));
        var transform=org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(scale,scale);
        board=transform.transform(board); copper=transform.transform(copper);
        Geometry area=board.difference(copper);String original=copper.toText();double d=.8*scale;
        String units=scale==1 ? "MM" : "IN";
        var settings=new NccToolSettings(.4,NccMethod.LINES,connect,true,0,NccSeedPolicy.STABLE,angle);
        NccResult result=tool.equals("NCC")
                ?NccGenerator.generate(units,copper,new NccParameters(List.of(d),.4,0,NccMethod.LINES,connect,true,0,false,NccOrder.NONE,new NccBoundary.Area(board),List.of(),Map.of(d,settings)))
                :NccGenerator.paint(units,area,new PaintParameters(List.of(d),.4,0,NccMethod.LINES,connect,true,NccOrder.NONE,false,
                        Map.of(d,new PaintToolSettings(.4,0,NccMethod.LINES,connect,true,angle))),CancellationToken.NONE,ProgressCallback.none());
        Geometry swept=result.geometry().buffer(d/2,64);
        assertFalse(result.isEmpty());assertEquals(0,result.toolResults().getFirst().failedPolygonCount());
        // Buffer's documented default input simplification is 1% of radius;
        // Compare the solid pad interior, allowing simplification and polygonal arc error.
        double tolerance=d/2*(BufferParameters.DEFAULT_SIMPLIFY_FACTOR+1-Math.cos(Math.PI/256));
        assertTrue(swept.intersection(copper.buffer(-tolerance,64)).isEmpty(),"cutter must not shortcut pad interiors");
        Geometry reachable=area.buffer(-d/2,64).buffer(d/2,64);
        double residual=reachable.difference(swept).getArea();
        assertTrue(residual/reachable.getArea()<.001,"uncovered reachable material: "+residual);
        assertEquals(original,copper.toText());
    }
}
