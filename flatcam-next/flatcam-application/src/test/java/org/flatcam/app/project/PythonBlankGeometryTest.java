package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.GeometryFactory;

class PythonBlankGeometryTest {

    /** Found with the real Python application: an empty list made it compute infinite bounds and fail to save. */
    @Test
    void aBlankGeometryIsWrittenAsThePythonApplicationWritesItsOwn(@TempDir Path directory) throws Exception {
        var blank = new ProjectFile.GeometryEntry("blank", "", "MM", new GeometryFactory().createGeometryCollection(),
                true, List.of(), null, null, true, null, null);
        Path file = directory.resolve("blank.FlatPrj");
        PythonProjectWriter.save(new ProjectFile(List.of(), List.of(), List.of(blank), List.of(), List.of()), file, false);
        JSONObject object = new JSONObject(Files.readString(file)).getJSONArray("objs").getJSONObject(0);
        assertTrue(object.isNull("solid_geometry"));
        assertTrue(object.getBoolean("multigeo"));
        for (String bound : List.of("xmin", "ymin", "xmax", "ymax")) {
            assertEquals(0, object.getJSONObject("options").getInt(bound), bound);
        }
        assertTrue(object.getJSONObject("tools").getJSONObject("1").getJSONArray("solid_geometry").isEmpty());

        // The FX reads it back, with and without its own metadata (as after a save by the Python application).
        assertTrue(PythonProjectIO.load(file).geometries().get(0).geometry().isEmpty());
        JSONObject root = new JSONObject(Files.readString(file));
        root.remove("_fx_format");
        root.remove("_java");
        Path python = directory.resolve("python.FlatPrj");
        Files.writeString(python, root.toString());
        ProjectFile reopened = PythonProjectIO.load(python);
        assertEquals("blank", reopened.geometries().get(0).name());
        assertTrue(reopened.geometries().get(0).geometry().isEmpty());
    }
}
