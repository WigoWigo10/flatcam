package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class GeometryPerToolProjectTest {
    @TempDir Path dir;
    @Test void plainGeometryKeepsDiameterAndVProfileWithoutBecomingFixedMultigeo() throws Exception {
        var p = new GeometryGCodeParameters(2,0.1,false,1,100,0,false);
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(1,1)});
        var settings = new GeometryCncSettings(GCodePreprocessor.DEFAULT_NO_M6,0.3,Map.of(0,new VTipSettings(0.1,30)),Map.of(),org.flatcam.cam.geometry.ToolProfile.V);
        var entry = new ProjectFile.GeometryEntry("plain","","MM",path,true,List.of(),null,null,true,p,settings);
        Path file = dir.resolve("plain.fcnproj"); ProjectFileIO.save(new ProjectFile(List.of(),List.of(),List.of(entry),List.of()),file);
        var loaded = ProjectFileIO.load(file).geometries().getFirst();
        assertTrue(loaded.tools().isEmpty()); assertEquals(settings,loaded.cncSettings());
    }
    @Test void distinctParametersAndNewFieldsSurviveNativeSaveReload() throws Exception {
        var p = new GeometryGCodeParameters(3,0.2,true,0.05,200,10000,true,600,null,80,true,0.5,true,0.1)
                .withCompensation(ToolPathOffset.CUSTOM,-0.15)
                .withJobOptions(new GeometryJobOptions(4.0,0.5,10.0,20.0,15.0,0.0,0.0));
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(1,1)});
        var settings = new GeometryCncSettings(GCodePreprocessor.GRBL_11, null, Map.of(), Map.of(0,p));
        var entry = new ProjectFile.GeometryEntry("paths", "", "MM", path, true, List.of(new ToolGeometry(0.3,path)), null,null,true,p,settings);
        Path file = dir.resolve("test.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(),List.of(),List.of(entry),List.of()), file);
        var loaded = ProjectFileIO.load(file).geometries().getFirst();
        assertEquals(p, loaded.cncDefaults()); assertEquals(settings, loaded.cncSettings());
    }
    @Test void projectsWithoutNewFieldsKeepAutomaticPositionsAndPath() throws Exception {
        var p = new GeometryGCodeParameters(2,0.1,false,1,100,0,false);
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,1)});
        Path file = dir.resolve("old.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(),List.of(),List.of(new ProjectFile.GeometryEntry(
                "old","","MM",path,true,List.of(),null,null,true,p)),List.of()),file);
        var root = ProjectFileIO.parseRoot(java.nio.file.Files.readAllBytes(file));
        var fields = root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).getJSONObject("cncDefaults");
        fields.remove("offset"); fields.remove("customOffset"); fields.remove("jobOptions");
        ProjectFileIO.writeRoot(root,file,false);
        assertEquals(p,ProjectFileIO.load(file).geometries().getFirst().cncDefaults());
    }
    @Test void exclusionsRoundtripNativelyButLegacyExportFailsBeforeCreatingFile() throws Exception {
        var f=new GeometryFactory(); var area=CncExclusionArea.of(f.toGeometry(new Envelope(4,6,-1,1)),CncExclusionArea.Strategy.OVER,20);
        var p=new GeometryGCodeParameters(3,.1,false,1,100,100,false).withJobOptions(GeometryJobOptions.AUTOMATIC.withExclusions(true,List.of(area)));
        var path=f.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var project=new ProjectFile(List.of(),List.of(),List.of(new ProjectFile.GeometryEntry("paths","","MM",path,true,List.of(new ToolGeometry(1,path)),null,null,true,p)),List.of());
        var file=dir.resolve("exclusions.fcnproj"); ProjectFileIO.save(project,file);
        var loaded=ProjectFileIO.load(file).geometries().getFirst(); assertEquals(p,loaded.cncDefaults());
        assertEquals(GCodeGenerator.generateGeometryCncJob("MM",project.geometries().getFirst().tools(),p).gcode(),
                GCodeGenerator.generateGeometryCncJob("MM",loaded.tools(),loaded.cncDefaults()).gcode());
        var legacy=dir.resolve("unsafe.FlatPrj"); assertThrows(java.io.IOException.class,()->PythonProjectWriter.save(project,legacy));
        assertFalse(java.nio.file.Files.exists(legacy));
    }
}
