package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.GeometryFactory;

class DrillSlotsPersistenceTest {
    @Test void legacyCannotFlattenDifferentPerToolParametersIntoGlobalDefaults() throws Exception {
        var base=project("MM"); var entry=base.excellons().getFirst();
        var different=new ProjectFile.ExcellonEntry(entry.name(),entry.image(),null,null,true,true,false,
                Map.of(1,entry.drillDefaults().get(1),2,entry.drillDefaults().get(1).withSlots(DrillSlotParameters.milling())),entry.cncSettings());
        var mixed=new ProjectFile(List.of(),List.of(different),List.of());
        Path destination=directory.resolve("different.FlatPrj"); Files.writeString(destination,"original");
        assertThrows(java.io.IOException.class,()->PythonProjectWriter.save(mixed,destination));
        assertEquals("original",Files.readString(destination));
        Path nativeFile=directory.resolve("different.fcnproj"); ProjectFileIO.save(mixed,nativeFile,false);
        assertEquals(different.drillDefaults(),ProjectFileIO.load(nativeFile).excellons().getFirst().drillDefaults());
    }
    @TempDir Path directory;
    private ProjectFile project(String units) {
        double s = units.equals("MM") ? 1 : 1 / 25.4;
        var image = ExcellonImage.of(units, Map.of(1,s), List.of(),
                List.of(new ExcellonImage.Slot(1,10*s,0,12*s,0)), new GeometryFactory().createPolygon());
        var p = new DrillGCodeParameters(3*s,s,100*s,10000,true).withSlots(new DrillSlotParameters(true,25,false));
        var settings = new DrillCncSettings(GCodePreprocessor.GRBL_11_NO_M6,
                new GCodeGenerator.DrillJobOptions(true,15*s,5*s,null,null), List.of(1), DrillCncSettings.ToolOrder.NO);
        return new ProjectFile(List.of(), List.of(new ProjectFile.ExcellonEntry("slots",image,null,null,true,true,false,Map.of(1,p),settings)), List.of());
    }
    private String generate(ProjectFile project) {
        var entry = project.excellons().getFirst();
        return GCodeGenerator.generateDrillCncJob(entry.image(),entry.drillDefaults(),List.of(1),entry.cncSettings().options(),entry.cncSettings().preprocessor()).gcode();
    }
    @Test void publicFixturesAndOptionalRealPythonAppResave() throws Exception {
        Path output = Path.of(System.getProperty("flatcam.slots.roundtrip.output", directory.toString()));
        Files.createDirectories(output);
        for (String units : List.of("MM", "IN")) {
            var original = project(units);
            PythonProjectWriter.save(original, output.resolve(units+"-fx.FlatPrj"), false);
            String resaved = System.getProperty("flatcam.slots.roundtrip.resaved");
            if (resaved == null) continue;
            var loaded = PythonProjectIO.load(Path.of(resaved).resolve(units+"-python.FlatPrj"));
            assertEquals(original.excellons().getFirst().drillDefaults(), loaded.excellons().getFirst().drillDefaults());
            assertEquals(original.excellons().getFirst().cncSettings(),loaded.excellons().getFirst().cncSettings());
            assertEquals(generate(original),generate(loaded));
            var expectedSlots=original.excellons().getFirst().image().slots(); var actualSlots=loaded.excellons().getFirst().image().slots();
            assertEquals(expectedSlots.size(),actualSlots.size());
            for (int i=0;i<expectedSlots.size();i++) {
                var a=expectedSlots.get(i); var b=actualSlots.get(i); assertEquals(a.toolId(),b.toolId());
                // JSON -> Python -> JSON rounds decimal spellings by one binary ULP in IN.
                assertEquals(a.x1(),b.x1(),1e-14); assertEquals(a.y1(),b.y1(),1e-14);
                assertEquals(a.x2(),b.x2(),1e-14); assertEquals(a.y2(),b.y2(),1e-14);
            }
            var expected=original.excellons().getFirst(); var actual=loaded.excellons().getFirst();
            assertEquals(generate(original),GCodeGenerator.generateDrillCncJob(actual.image(),actual.drillDefaults(),List.of(1),
                    expected.cncSettings().options(),expected.cncSettings().preprocessor()).gcode());
            assertEquals(units, loaded.excellons().getFirst().image().units());
            Path nativeCopy = output.resolve(units+"-reopened.fcnproj");
            ProjectFileIO.save(loaded, nativeCopy, true);
            assertEquals(loaded.excellons().getFirst().drillDefaults(), ProjectFileIO.load(nativeCopy).excellons().getFirst().drillDefaults());
        }
    }
    @Test void nativeJsonXzAndPythonSnapshotRetainOperationOverlapAndLastHole() throws Exception {
        for (String units : List.of("MM","IN")) for (boolean compressed : List.of(false,true)) {
            var original = project(units);
            Path file = directory.resolve(units+compressed+".fcnproj");
            ProjectFileIO.save(original,file,compressed);
            var loaded = ProjectFileIO.load(file);
            assertEquals(original.excellons().getFirst().drillDefaults(),loaded.excellons().getFirst().drillDefaults());
            assertEquals(generate(original),generate(loaded));
            Path legacy = directory.resolve(units+compressed+".FlatPrj");
            PythonProjectWriter.save(original,legacy,compressed);
            var json = ProjectFileIO.parseRoot(Files.readAllBytes(legacy));
            var object = json.getJSONArray("objs").getJSONObject(0);
            var data = object.getJSONObject("tools").getJSONObject("1").getJSONObject("data");
            assertTrue(data.getBoolean("tools_drill_drill_slots"));
            assertEquals(25,data.getDouble("tools_drill_drill_overlap"));
            assertFalse(data.getBoolean("tools_drill_last_drill"));
            assertEquals(generate(original),generate(PythonProjectIO.load(legacy)));
            object.remove("_java"); // prove real legacy keys, not just the FX snapshot
            json.remove("_fx_format"); json.remove("_java");
            Path bare = directory.resolve("bare-"+units+compressed+".FlatPrj");
            Files.writeString(bare,json.toString());
            assertEquals(new DrillSlotParameters(true,25,false),PythonProjectIO.load(bare).excellons().getFirst().drillDefaults().get(1).slots());
        }
    }
    @Test void missingOptionalKeysKeepOldMillingAndInvalidKeysRefuseNativeLoading() throws Exception {
        var root = ProjectFileIO.toJson(project("MM"));
        var p = root.getJSONArray("objs").getJSONObject(0).getJSONObject("_java").getJSONObject("drillDefaults").getJSONObject("1");
        for (String key : List.of("drillSlots","slotOverlap","slotLastHole")) p.remove(key);
        Path file = directory.resolve("old.fcnproj"); Files.writeString(file,root.toString());
        assertEquals(DrillSlotParameters.milling(),ProjectFileIO.load(file).excellons().getFirst().drillDefaults().get(1).slots());
        p.put("drillSlots",true).put("slotOverlap",100);
        Files.writeString(file,root.toString());
        assertThrows(IllegalArgumentException.class, () -> ProjectFileIO.load(file));
    }
}
