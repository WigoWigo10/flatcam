package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcellonCurvedPersistenceTest {
    @TempDir Path directory;
    private ProjectFile fixture() {
        var image=new ExcellonParser().parse(List.of("M48","METRIC","T1C0.2","%","T1",
                "G00X1.0Y0.0","M15","G02X0.0Y-1.0A1.0","M16","M30"));
        return new ProjectFile(List.of(),List.of(new ProjectFile.ExcellonEntry("curve",image,"#FF0000FF","#FF0000FF",true,true,false)),List.of());
    }
    @Test void nativeSaveReopenKeepsAllRouteVerticesAndFootprint() throws Exception {
        var project=fixture(); Path path=directory.resolve("curve.fcnproj");
        assertEquals(3,ProjectFileIO.toJson(project).getInt("version"),"older FX readers must refuse curves rather than load endpoint chords");
        ProjectFileIO.save(project,path); var read=ProjectFileIO.load(path);
        assertEquals(project.excellons().getFirst().image().slots(),read.excellons().getFirst().image().slots());
        assertTrue(project.excellons().getFirst().image().solidGeometry().equalsExact(read.excellons().getFirst().image().solidGeometry(),1e-12)); // Existing WKT footprint precision.
    }
    @Test void pythonExportRefusesCurvedSlotsAndPreservesAnExistingDestination() throws Exception {
        Path path=directory.resolve("curve.FlatPrj"); Files.writeString(path,"existing");
        var error=assertThrows(IOException.class,()->PythonProjectWriter.save(fixture(),path));
        assertTrue(error.getMessage().contains(".fcnproj")); assertEquals("existing",Files.readString(path));
    }
    @Test void malformedNativeCentrelineIsRejectedRatherThanReplacingItByTheChord() throws Exception {
        var json=ProjectFileIO.toJson(fixture());
        var slot=json.getJSONArray("objs").getJSONObject(0).getJSONObject("tools").getJSONObject("1").getJSONArray("slots").getJSONObject(0);
        slot.put("_fx_path",org.flatcam.app.project.flatprj.WktJson.wrap(new org.locationtech.jts.geom.GeometryFactory().createPoint(new org.locationtech.jts.geom.Coordinate(1,2))));
        Path path=directory.resolve("broken.fcnproj"); ProjectFileIO.writeRoot(json,path,false);
        assertThrows(IOException.class,()->ProjectFileIO.load(path));
    }
}
