package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class PythonProjectWriterTest {
    @TempDir Path dir;
    private ProjectFile sample() throws Exception {
        var gerber = new GerberParser().parse(List.of("%FSLAX24Y24*%","%MOMM*%","%ADD10C,1*%","D10*","X10000Y10000D03*","M02*"));
        var drill = new ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y1.0","M30"));
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(2,0)});
        var a = new GeometryGCodeParameters(2,0.1,false,1,100,0,false,0,null,50,false,0,false,0);
        var b = new GeometryGCodeParameters(3,0.2,true,0.05,200,10000,false,0,null,60,true,1,false,0);
        var settings = new GeometryCncSettings(GCodePreprocessor.DEFAULT_NO_M6,null,Map.of(),Map.of(0,a,1,b));
        return new ProjectFile(List.of(new ProjectFile.GerberEntry("copper",gerber,"0x99cc33bf",null,true,true,false,false)),
                List.of(new ProjectFile.ExcellonEntry("holes",drill,null,null,false,true,false)),
                List.of(new ProjectFile.GeometryEntry("paths","copper","MM",path,true,
                        List.of(new ToolGeometry(0.3,path),new ToolGeometry(0.8,path)),null,null,true,a,settings)),
                List.of(new ProjectFile.CncJobRecord("paths.nc","paths","paths.nc","G21\nG90\nG0 Z2\nG0 X0 Y0\nG1 Z-0.1 F50\nG1 X2 Y0 F100\nG0 Z2\nM30\n")));
    }
    @Test void plainAndCompressedExportsContainRealPythonObjectsAndKeepFxSettings() throws Exception {
        ProjectFile original = sample();
        for (boolean compressed : List.of(false,true)) {
            Path file = dir.resolve(compressed + ".FlatPrj");
            PythonProjectWriter.save(original,file,compressed);
            var root = ProjectFileIO.parseRoot(Files.readAllBytes(file));
            assertEquals(8.994,root.getDouble("version")); assertEquals(4,root.getJSONArray("objs").length());
            assertFalse(root.getJSONArray("objs").getJSONObject(1).getJSONObject("tools").getJSONObject("1").getJSONArray("solid_geometry").isEmpty());
            ProjectFile loaded = PythonProjectIO.load(file);
            assertEquals(original.geometries().getFirst().cncSettings(),loaded.geometries().getFirst().cncSettings());
            assertEquals(original.cncJobs().getFirst().gcode(),loaded.cncJobs().getFirst().gcode());
            assertEquals(original.gerbers().getFirst().fillColorWeb(),loaded.gerbers().getFirst().fillColorWeb());
            assertEquals(original.gerbers().getFirst().image().solidGeometry().getArea(),loaded.gerbers().getFirst().image().solidGeometry().getArea(),1e-9);
            // Simulate Python discarding private FX root metadata on its own save.
            root.remove("_fx_format"); root.remove("_java");
            ProjectFileIO.writeRoot(root,file,compressed);
            ProjectFile legacy = PythonProjectIO.load(file);
            assertEquals(original.geometries().getFirst().tools().size(),legacy.geometries().getFirst().tools().size());
            assertEquals(200,legacy.geometries().getFirst().cncSettings().parametersByTool().get(1).feedRate());
            assertEquals(60,legacy.geometries().getFirst().cncSettings().parametersByTool().get(1).feedRateZ());
        }
    }
    @Test void failedExportDoesNotOverwriteExistingFile() throws Exception {
        Path file = dir.resolve("keep.FlatPrj"); Files.writeString(file,"original");
        ProjectFile invalid = new ProjectFile(List.of(),List.of(),List.of(),
                List.of(new ProjectFile.CncJobRecord("bad","","bad.nc",null)));
        assertThrows(IOException.class, () -> PythonProjectWriter.save(invalid,file));
        assertEquals("original",Files.readString(file));
    }
    @Test void compensationAndCommonPositionsSurviveWithoutPrivateFxMetadata() throws Exception {
        var positions = new GeometryJobOptions(5.0,0.5,20.0,30.0,15.0,-1.0,-2.0);
        var p = new GeometryGCodeParameters(2,0.1,false,1,100,0,true)
                .withCompensation(ToolPathOffset.CUSTOM,-0.25).withJobOptions(positions);
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(10,0),
                new Coordinate(10,10),new Coordinate(0,10),new Coordinate(0,0)});
        var settings = new GeometryCncSettings(GCodePreprocessor.DEFAULT_NO_M6,null,Map.of(),Map.of(0,p));
        String code = GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(0.5,path)),p,Map.of(),
                org.flatcam.cam.CancellationToken.none(),GCodePreprocessor.DEFAULT_NO_M6).gcode();
        var project = new ProjectFile(List.of(),List.of(),List.of(new ProjectFile.GeometryEntry("outline","","MM",path,true,
                List.of(new ToolGeometry(0.5,path)),null,null,true,p,settings)),
                List.of(new ProjectFile.CncJobRecord("outline.nc","outline","outline.nc",code)));
        Path file = dir.resolve("positions.FlatPrj"); PythonProjectWriter.save(project,file,false);
        var root = ProjectFileIO.parseRoot(Files.readAllBytes(file));
        var obj = root.getJSONArray("objs").getJSONObject(0);
        assertEquals("Custom",obj.getJSONObject("tools").getJSONObject("1").getString("offset"));
        assertEquals(-0.25,obj.getJSONObject("tools").getJSONObject("1").getDouble("offset_value"));
        assertEquals(positions.endZ(),obj.getJSONObject("options").getDouble("endz"));
        root.remove("_java"); root.remove("_fx_format"); ProjectFileIO.writeRoot(root,file,false);
        var loaded = PythonProjectIO.load(file).geometries().getFirst();
        assertEquals(p,loaded.cncDefaults()); assertEquals(p,loaded.cncSettings().parametersByTool().get(0));
        assertTrue(path.equalsExact(loaded.tools().getFirst().geometry()));
        // Python takes End XY from the Geometry object's common options, not the tool's stale copy.
        obj.getJSONObject("options").put("endxy",new org.json.JSONArray(List.of(40,50)));
        var data = obj.getJSONObject("tools").getJSONObject("1").getJSONObject("data");
        for (String key : List.of("startz", "endz", "toolchangez", "toolchangexy")) data.remove(key);
        ProjectFileIO.writeRoot(root,file,false);
        var common = PythonProjectIO.load(file).geometries().getFirst().cncDefaults().jobOptions();
        assertEquals(40,common.endX()); assertEquals(50,common.endY());
        assertEquals(positions.startZ(),common.startZ()); assertEquals(positions.toolChangeX(),common.toolChangeX());
        String output = System.getProperty("flatcam.compat.output");
        if (output != null) PythonProjectWriter.save(project,Path.of(output).resolve("advanced-positions.FlatPrj"),false);
        String resaved = System.getProperty("flatcam.compat.advancedResaved");
        if (resaved != null) {
            var restored = PythonProjectIO.load(Path.of(resaved));
            assertEquals(p,restored.geometries().getFirst().cncDefaults());
            assertEquals(p,restored.geometries().getFirst().cncSettings().parametersByTool().get(0));
            assertEquals(code,restored.cncJobs().getFirst().gcode());
        }
    }
    @Test void cncOnlyInchesGetsMatchingGlobalUnitsAndMixedProjectsAreRefused() throws Exception {
        var inches = new ProjectFile.CncJobRecord("in.nc","","in.nc","G20\nG90\nG0 Z0.1\nG1 X1 F2\nM30\n");
        Path file = dir.resolve("units.FlatPrj");
        PythonProjectWriter.save(new ProjectFile(List.of(),List.of(),List.of(),List.of(inches)),file,false);
        assertEquals("IN",ProjectFileIO.parseRoot(Files.readAllBytes(file)).getJSONObject("options").getString("units"));
        ProjectFile metric = sample();
        assertThrows(IOException.class, () -> PythonProjectWriter.save(new ProjectFile(metric.gerbers(),metric.excellons(),metric.geometries(),List.of(inches)),file));
    }
    @Test void legacyExcellonGeometryUsesCurrentUnitsNotStaleSourceHeader() throws Exception {
        var entry = sample().excellons().getFirst();
        var encoded = org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec.toJson(entry.name(),entry.image(),null,null,true,true,false);
        encoded.put("units","MM").put("excellon_units","INCH");
        var decoded = org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec.fromJson(encoded).image();
        assertEquals("MM",decoded.units());
        assertEquals(entry.image().drills(),decoded.drills());
        assertEquals(entry.image().toolDiameters(),decoded.toolDiameters());
    }
    @Test void optionalCompatibilityArtifactsAndRealFixtureRoundtrip() throws Exception {
        String output = System.getProperty("flatcam.compat.output");
        org.junit.jupiter.api.Assumptions.assumeTrue(output != null);
        Path folder = Path.of(output); Files.createDirectories(folder);
        PythonProjectWriter.save(sample(),folder.resolve("sample.FlatPrj"),false);
        PythonProjectWriter.save(sample(),folder.resolve("sample-compressed.FlatPrj"),true);
        String real = System.getProperty("flatcam.compat.realProject");
        if (real != null) {
            ProjectFile original = PythonProjectIO.load(Path.of(real));
            Path file = folder.resolve("real-export.FlatPrj"); PythonProjectWriter.save(original,file);
            ProjectFile loaded = PythonProjectIO.load(file);
            assertEquals(original.gerbers().size(),loaded.gerbers().size());
            assertEquals(original.excellons().size(),loaded.excellons().size());
            assertEquals(original.geometries().size(),loaded.geometries().size());
            for (int i=0;i<original.geometries().size();i++) assertTrue(original.geometries().get(i).geometry().equalsExact(loaded.geometries().get(i).geometry()));
        }
    }
    @Test void optionalPythonResavedArtifactRestoresPerToolCncAndAllObjects() throws Exception {
        String path = System.getProperty("flatcam.compat.resaved");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null);
        ProjectFile loaded = PythonProjectIO.load(Path.of(path));
        assertEquals(1,loaded.gerbers().size()); assertEquals(1,loaded.excellons().size());
        assertEquals(1,loaded.geometries().size()); assertEquals(1,loaded.cncJobs().size());
        assertEquals(sample().cncJobs().getFirst().gcode(),loaded.cncJobs().getFirst().gcode());
        var p = loaded.geometries().getFirst().cncSettings().parametersByTool().get(1);
        assertEquals(60,p.feedRateZ()); assertEquals(200,p.feedRate()); assertTrue(p.dwell());
    }
}
