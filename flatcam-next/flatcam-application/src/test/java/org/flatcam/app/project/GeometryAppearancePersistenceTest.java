package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class GeometryAppearancePersistenceTest {
    static ProjectFile fixture() {
        var line=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1,2),new Coordinate(3,4)});
        var entry=new ProjectFile.GeometryEntry("route","","MM",line,true,List.of(),"#12345680","#AABBCC40",true,null,null,null,true);
        return new ProjectFile(List.of(),List.of(),List.of(entry),List.of());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void nativeAndPythonKeepGeometryMulticolor(boolean python,@TempDir Path directory) throws Exception {
        Path file=directory.resolve(python?"styles.FlatPrj":"styles.fcnproj");
        if(python) PythonProjectWriter.save(fixture(),file); else ProjectFileIO.save(fixture(),file);
        var loaded=python?PythonProjectIO.load(file):ProjectFileIO.load(file);
        assertTrue(loaded.geometries().getFirst().multicolor());
        assertEquals("#AABBCC40",loaded.geometries().getFirst().strokeColorWeb());
    }
    @Test void oldNativeMetadataWithoutTheOptionalFlagStillLoads() throws Exception {
        JSONObject root=ProjectFileIO.toJson(fixture());
        root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).remove("multicolor");
        assertFalse(ProjectFileIO.fromJson(root).geometries().getFirst().multicolor());
    }
    @Test void pythonOptionsAreReadWithoutFxMetadataAndOldMissingOptionDefaultsToFalse() throws Exception {
        JSONObject root=PythonProjectWriter.toRoot(fixture());
        root.remove("_java"); root.remove("_fx_format");
        var object=root.getJSONArray("objs").getJSONObject(0);
        assertTrue(object.getJSONObject("options").getBoolean("multicolored"));
        assertTrue(PythonProjectIO.decode(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).geometries().getFirst().multicolor());
        object.getJSONObject("options").remove("multicolored");
        assertFalse(PythonProjectIO.decode(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).geometries().getFirst().multicolor());
    }
}
