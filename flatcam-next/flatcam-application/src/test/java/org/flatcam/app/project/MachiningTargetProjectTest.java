package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class MachiningTargetProjectTest {
    @TempDir Path dir;
    @Test void targetAndBindingRoundTripInNativeAndFxPythonContainersIncludingInches() throws Exception {
        var factory = new GeometryFactory();
        for (String units : List.of("MM", "IN")) {
            double s = units.equals("MM") ? 1 : 1 / 25.4;
            var line = factory.createLineString(new Coordinate[]{new Coordinate(0, .5*s), new Coordinate(10*s,.5*s)});
            var tools = List.of(new ToolGeometry(s,line)); var area = factory.toGeometry(new Envelope(0,10*s,0,2*s));
            var target = new MachiningTarget("NCC",units,area,Map.of(0,area),MachiningTarget.signature(tools,CancellationToken.none()));
            var p = new GeometryGCodeParameters(3*s,.1*s,false,1,100*s,10000,true);
            var entry = new ProjectFile.GeometryEntry("NCC","Copper",units,line,true,tools,null,null,true,p,null,target);
            var project = new ProjectFile(List.of(),List.of(),List.of(entry),List.of());
            var encoded = ProjectFileIO.toJson(project);
            var encodedTarget = encoded.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).getJSONObject("machiningTarget");
            assertEquals(1,encodedTarget.getJSONArray("areas").length(),"Shared global/tool areas must not be serialized twice");
            for (String suffix : List.of(".fcnproj", ".FlatPrj")) {
                Path path = dir.resolve(units + suffix);
                if (suffix.equals(".FlatPrj")) PythonProjectWriter.save(project,path); else ProjectFileIO.save(project,path);
                var loaded = (suffix.equals(".FlatPrj") ? PythonProjectIO.load(path) : ProjectFileIO.load(path)).geometries().getFirst();
                assertNotNull(loaded.machiningTarget());
                assertSame(loaded.machiningTarget().area(),loaded.machiningTarget().toolAreas().get(0));
                assertEquals(target.pathSignature(), MachiningTarget.signature(loaded.tools(),CancellationToken.none()));
                assertEquals(target.area().getArea(),loaded.machiningTarget().area().getArea(),1e-10);
                var result = MachiningCoverage.calculate(loaded.machiningTarget(),units,loaded.tools(),p,Map.of(),CancellationToken.none(),fraction -> {});
                assertEquals(10*s*s, result.finalRemaining().getArea(),1e-10);
            }
            var old = new ProjectFile.GeometryEntry("Old","",units,line,true,tools,null,null,true,p);
            assertNull(old.machiningTarget());
            encodedTarget.put("areaRef",10);
            assertThrows(java.io.IOException.class,()->ProjectFileIO.fromJson(encoded));
        }
    }
}
