package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

class TinyClosedFootprintTest {
    @Test void aTinyClosedFinishingPathStillSweepsTheFullDiskAtItsVertices()throws Exception {
        // Minimized from the Air Quality Sensor finishing ring. A closed-line buffer can discard its endcaps.
        var path=new WKTReader().read("LINESTRING (116.4911883294847 -107.334000001,116.49112015393438 -107.33405056343415,116.48700016296824 -107.334000001,116.4911883294847 -107.334000001)");
        var f=path.getFactory();Geometry allowed=f.toGeometry(new Envelope(116,117,-108,-107));String before=path.toText();
        var tools=List.of(new ToolGeometry(.4,path));var target=new MachiningTarget("NCC","MM",allowed,Map.of(0,allowed),MachiningTarget.signature(tools,CancellationToken.NONE));
        var result=MachiningCoverage.calculate(target,"MM",tools,new GeometryGCodeParameters(3,.1,false,0,300,10000,false),Map.of(),CancellationToken.NONE,ProgressCallback.none());
        Geometry definitelyCut=f.createPoint(path.getCoordinate()).buffer(.19,64);
        assertEquals(0,result.finalRemaining().intersection(definitelyCut).getArea(),1e-10,"Every vertex sweeps a full cutter disk, even for an almost collapsed closed ring");
        assertEquals(before,path.toText());
    }
}
