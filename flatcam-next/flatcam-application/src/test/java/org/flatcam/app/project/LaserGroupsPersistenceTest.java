package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class LaserGroupsPersistenceTest {
    @TempDir Path folder;
    @Test void groupParametersAndRegeneratedCodeSurviveNativeAndPythonProjects() throws Exception {
        var f=new GeometryFactory();
        var first=f.createLineString(new Coordinate[]{new Coordinate(10,7),new Coordinate(12,7)});
        var second=f.createLineString(new Coordinate[]{new Coordinate(20,7),new Coordinate(22,7)});
        for(var profile:List.of(GCodePreprocessor.GRBL_LASER,GCodePreprocessor.Z_LASER,
                GCodePreprocessor.MARLIN_LASER_FAN_PIN,GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)) {
            var a=new GeometryGCodeParameters(6,1,false,1,200,64,false,900);
            var b=new GeometryGCodeParameters(profile==GCodePreprocessor.GRBL_LASER ? 6 : 2,1,false,1,350,200,false,900);
            var tools=List.of(new ToolGeometry(.4,first,ToolProfile.V),new ToolGeometry(.8,second));
            var settings=new GeometryCncSettings(profile,null,Map.of(),Map.of(0,a,1,b));
            var entry=new ProjectFile.GeometryEntry("Laser groups","","MM",f.createGeometryCollection(new Geometry[]{first,second}),true,
                    tools,null,null,true,a,settings);
            var project=new ProjectFile(List.of(),List.of(),List.of(entry),List.of());
            String expected=GCodeGenerator.generateGeometryCncJob("MM",tools,a,Map.of(),settings.parametersByTool(),CancellationToken.none(),profile).gcode();
            for(boolean legacy:List.of(false,true)) for(boolean compressed:List.of(false,true)) {
                Path output=folder.resolve(profile+"-"+legacy+"-"+compressed+(legacy ? ".FlatPrj" : ".fcnproj"));
                if(legacy) PythonProjectWriter.save(project,output,compressed); else ProjectFileIO.save(project,output,compressed);
                var loaded=(legacy ? PythonProjectIO.load(output) : ProjectFileIO.load(output)).geometries().getFirst();
                assertEquals(settings,loaded.cncSettings());
                assertEquals(expected,generate(loaded));
                if(legacy) {
                    var root=ProjectFileIO.parseRoot(Files.readAllBytes(output));
                    var data=root.getJSONArray("objs").getJSONObject(0).getJSONObject("tools");
                    assertEquals(b.safeZ(),data.getJSONObject("2").getJSONObject("data").getDouble("travelz"));
                    assertEquals(b.safeZ(),data.getJSONObject("2").getJSONObject("data").getDouble("endz"));
                    root.remove("_fx_format"); root.remove("_java");
                    ProjectFileIO.writeRoot(root,output,compressed);
                    var withoutMetadata=PythonProjectIO.load(output).geometries().getFirst();
                    assertEquals(b.feedRate(),withoutMetadata.cncSettings().parametersByTool().get(1).feedRate());
                    assertEquals(b.spindleSpeedRpm(),withoutMetadata.cncSettings().parametersByTool().get(1).spindleSpeedRpm());
                    assertEquals(expected,generate(withoutMetadata));
                }
            }
        }
    }
    private String generate(ProjectFile.GeometryEntry entry) {
        var settings=entry.cncSettings();
        return GCodeGenerator.generateGeometryCncJob(entry.units(),entry.tools(),entry.cncDefaults(),settings.vTools(),
                settings.parametersByTool(),CancellationToken.none(),settings.preprocessor()).gcode();
    }
}
