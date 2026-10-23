package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.panel.Panelize;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class MachiningTargetTransportTest {
    private final GeometryFactory factory=new GeometryFactory();
    @Test void affineAndPanelTransportRebindKnownAreasWithoutReusingOldCoverage() {
        for (String units:List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            var area=factory.toGeometry(new Envelope(0,10*s,0,2*s));
            var tools=List.of(new ToolGeometry(s,factory.createLineString(new Coordinate[]{new Coordinate(0,s),new Coordinate(10*s,s)})));
            var target=new MachiningTarget("PAINT",units,area,Map.of(0,area),MachiningTarget.signature(tools,CancellationToken.none()));
            var params=new GeometryGCodeParameters(3*s,.1*s,false,s,100*s,10000,true);
            for (TransformOp op:List.of(new TransformOp.Offset(5*s,4*s),new TransformOp.Rotate(90,new Coordinate()),
                    new TransformOp.MirrorX(new Coordinate()),new TransformOp.Skew(10,0,new Coordinate()))) {
                var paths=tools.stream().map(tool -> tool.transformed(op)).toList();
                var moved=target.transformed(op,tools,paths,CancellationToken.none());
                assertNotNull(moved); assertEquals(area.getArea(),moved.area().getArea(),1e-10);
                assertDoesNotThrow(()->MachiningCoverage.calculate(moved,units,paths,params,Map.of(),CancellationToken.none(),f -> {}));
            }
            var scale=new TransformOp.Scale(2,2,new Coordinate());
            var scaled=tools.stream().map(tool -> tool.transformed(scale)).toList();
            var enlarged=target.transformed(scale,tools,scaled,CancellationToken.none());
            var result=MachiningCoverage.calculate(enlarged,units,scaled,params,Map.of(),CancellationToken.none(),f -> {});
            assertEquals(80*s*s,result.targetArea(),1e-10); assertEquals(60*s*s,result.finalRemainingArea(),1e-10);
            var layout=new Panelize.Layout(2,2,false,20*s,5*s);
            var panel=Panelize.geometry(units,tools.getFirst().geometry(),true,tools,layout,CancellationToken.none(),f -> {});
            var repeated=target.panelized(layout,tools,panel.tools(),CancellationToken.none());
            assertEquals(area.getArea()*4,repeated.area().getArea(),1e-10);
            assertEquals(area.getArea(),target.area().getArea());
            assertDoesNotThrow(()->MachiningCoverage.calculate(repeated,units,panel.tools(),params,Map.of(),CancellationToken.none(),f -> {}));
        }
    }
    @Test void buffersStaleSignaturesWrongTransformsAndCancellationCannotBecomeKnownCoverage() {
        var area=factory.toGeometry(new Envelope(0,10,0,2));
        var tools=List.of(new ToolGeometry(1,area.getBoundary()));
        var target=new MachiningTarget("NCC","MM",area,Map.of(0,area),MachiningTarget.signature(tools,CancellationToken.none()));
        assertNull(target.transformed(new TransformOp.Buffer(.1,false,true),tools,tools,CancellationToken.none()));
        assertThrows(IllegalArgumentException.class,()->target.transformed(new TransformOp.Offset(1,0),tools,tools,CancellationToken.none()));
        assertThrows(java.util.concurrent.CancellationException.class,()->target.transformed(new TransformOp.Offset(1,0),tools,tools,()->true));
        var different=List.of(new ToolGeometry(2,area.getBoundary()));
        assertNull(target.transformed(new TransformOp.Offset(0,0),different,different,CancellationToken.none()));
    }
}
