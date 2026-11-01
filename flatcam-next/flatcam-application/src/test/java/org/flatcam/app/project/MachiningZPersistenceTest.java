package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.io.WKTReader;

class MachiningZPersistenceTest {
    @TempDir Path directory;
    @ParameterizedTest @ValueSource(strings={"fcnproj","FlatPrj"})
    void signedCoordinatesPolicyAndOffsetsSurviveSavingAndReopening(String extension) throws Exception {
        var p=new GeometryGCodeParameters(3,.1,false,.1,100,0,false)
                .withZCoordinates(-.1,.3,true,.1,MachiningZPolicy.MACHINIST)
                .withJobOptions(new GeometryJobOptions(-.2,-.1,null,null,null,null,null,false,List.of(),
                        CncRouting.LEGACY,GeometryTravelClearance.JOB_MAXIMUM,MachiningZPolicy.MACHINIST));
        var path=new WKTReader().read("LINESTRING (1 0,2 0)");
        var geometry=new ProjectFile.GeometryEntry("path","source","MM",path,true,List.of(),null,null,true,p);
        var image=new ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y0.0","M30"));
        var drill=new DrillGCodeParameters(-.1,-.4,100,0,false,true,.1,false,0,.1,DrillSlotParameters.milling(),MachiningZPolicy.MACHINIST);
        var options=new GCodeGenerator.DrillJobOptions(false,-.1,-.1,null,null,0,null,false,List.of(),null,null,null,false,MachiningZPolicy.MACHINIST);
        var settings=new DrillCncSettings(GCodePreprocessor.FX_PORTABLE,options,List.of(1),DrillCncSettings.ToolOrder.NO);
        var excellon=new ProjectFile.ExcellonEntry("drills",image,null,null,true,true,false,Map.of(1,drill),settings);
        var project=new ProjectFile(List.of(),List.of(excellon),List.of(geometry),List.of());
        Path file=directory.resolve("signed."+extension);
        if(extension.equals("FlatPrj")) PythonProjectWriter.save(project,file); else ProjectFileIO.save(project,file);
        var restored=extension.equals("FlatPrj") ? PythonProjectIO.load(file) : ProjectFileIO.load(file);
        assertEquals(p,restored.geometries().getFirst().cncDefaults());
        assertEquals(drill,restored.excellons().getFirst().drillDefaults().get(1));
        assertEquals(settings,restored.excellons().getFirst().cncSettings());
        assertEquals(.3,restored.geometries().getFirst().cncDefaults().cutZ());
        assertEquals(.3,restored.excellons().getFirst().drillDefaults().get(1).effectiveCutZ(),1e-12);
    }
}
