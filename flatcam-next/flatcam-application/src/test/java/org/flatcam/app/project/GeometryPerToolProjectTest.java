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
    @Test void continuousSameDirectionAndRingTypeSurviveNativeSave() throws Exception {
        var f=new GeometryFactory();
        var ring=f.createLinearRing(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0),new Coordinate(12,2),new Coordinate(10,2),new Coordinate(10,0)});
        var p=new GeometryGCodeParameters(3,.6,true,.2,200,10000,true).withPassStrategy(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION);
        var tools=List.of(new ToolGeometry(1,ring));
        var project=new ProjectFile(List.of(),List.of(),List.of(new ProjectFile.GeometryEntry("ring","","MM",ring,true,tools,null,null,true,p)),List.of());
        var file=dir.resolve("same-direction.fcnproj"); ProjectFileIO.save(project,file);
        var loaded=ProjectFileIO.load(file).geometries().getFirst();
        assertEquals(p,loaded.cncDefaults()); assertInstanceOf(LinearRing.class,loaded.tools().getFirst().geometry());
        assertEquals(GCodeGenerator.generateGeometryCncJob("MM",tools,p).gcode(),
                GCodeGenerator.generateGeometryCncJob("MM",loaded.tools(),loaded.cncDefaults()).gcode());
    }
    @Test void travelClearanceRoundtripsAndAbsentOldKeysKeepTheConservativeDefault() throws Exception {
        var path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var p=new GeometryGCodeParameters(3,.6,true,.2,200,10000,true).withJobOptions(
                new GeometryJobOptions(null,.5,20.,0.,15.,null,null).withTravelClearance(GeometryTravelClearance.PER_TOOL))
                .withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        var entry=new ProjectFile.GeometryEntry("paths","","MM",path,true,List.of(new ToolGeometry(.4,path)),null,null,true,p);
        var project=new ProjectFile(List.of(),List.of(),List.of(entry),List.of());
        for (boolean compressed:List.of(false,true)) {
            var file=dir.resolve("travel-"+compressed+".fcnproj");
            ProjectFileIO.save(project,file,compressed);
            var loaded=ProjectFileIO.load(file).geometries().getFirst();
            assertEquals(p,loaded.cncDefaults());
            assertEquals(GCodeGenerator.generateGeometryCncJob("MM",entry.tools(),p).gcode(),
                    GCodeGenerator.generateGeometryCncJob("MM",loaded.tools(),loaded.cncDefaults()).gcode());
        }
        var file=dir.resolve("old-travel.fcnproj"); ProjectFileIO.save(project,file);
        var root=ProjectFileIO.parseRoot(java.nio.file.Files.readAllBytes(file));
        var options=root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).getJSONObject("cncDefaults").getJSONObject("jobOptions");
        options.put("travelClearance","unknown"); ProjectFileIO.writeRoot(root,file,false);
        assertThrows(java.io.IOException.class,()->ProjectFileIO.load(file));
        options.remove("travelClearance"); ProjectFileIO.writeRoot(root,file,false);
        assertEquals(GeometryTravelClearance.JOB_MAXIMUM,ProjectFileIO.load(file).geometries().getFirst().cncDefaults().jobOptions().travelClearance());
    }
    @Test void databasePositionSuggestionsPreserveRoutingAndTravelPolicy() {
        var routing=new CncRouting(CncRouting.Mode.NEAREST,true,false,List.of(),0);
        var base=GeometryJobOptions.AUTOMATIC.withRouting(routing).withTravelClearance(GeometryTravelClearance.PER_TOOL);
        var suggestions=new CncJobDefaults(Map.of(CncJobDefaults.Field.END_Z,"5"));
        var merged=suggestions.positions(base);
        assertEquals(5.,merged.endZ()); assertEquals(routing,merged.routing());
        assertEquals(GeometryTravelClearance.PER_TOOL,merged.travelClearance());
    }
    @Test void continuousPassStrategySurvivesNativeSaveAndOldFilesKeepRetraction() throws Exception {
        var p=new GeometryGCodeParameters(3,1,true,.4,100,1000,false).withPassStrategy(GeometryPassStrategy.CONTINUOUS_ALTERNATING);
        var path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
        var project=new ProjectFile(List.of(),List.of(),List.of(new ProjectFile.GeometryEntry("paths","","MM",path,true,List.of(),null,null,true,p)),List.of());
        Path file=dir.resolve("continuous.fcnproj"); ProjectFileIO.save(project,file);
        assertEquals(p,ProjectFileIO.load(file).geometries().getFirst().cncDefaults());
        var root=ProjectFileIO.parseRoot(java.nio.file.Files.readAllBytes(file));
        root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).getJSONObject("cncDefaults").remove("passStrategy");
        ProjectFileIO.writeRoot(root,file,false);
        assertEquals(GeometryPassStrategy.RETRACT_SAME_DIRECTION,ProjectFileIO.load(file).geometries().getFirst().cncDefaults().passStrategy());
    }
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
                .withJobOptions(new GeometryJobOptions(4.0,0.5,10.0,20.0,15.0,0.0,0.0).withRouting(
                        new org.flatcam.cam.gcode.CncRouting(org.flatcam.cam.gcode.CncRouting.Mode.BOARDS,true,true,
                                List.of("POLYGON ((0 0,10 0,10 10,0 10,0 0))"),.2)));
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
