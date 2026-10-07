package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.flatcam.app.project.flatprj.*;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.excellon.ExcellonParser;
import org.json.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.locationtech.jts.geom.*;

class PythonMainFlowImportTest {
    @TempDir Path directory;
    private static JSONObject gerber(String units) {
        var image = new GerberParser().parse(List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,2X0.5*%",
                "D10*", "X10000Y10000D03*", "M02*"));
        return GerberFlatPrjCodec.toJson("copper", image, "#FFFF00BF", "#FFFF00", true, true, false, false).put("units", units);
    }
    private static JSONObject geometry(String units, double cutZ) {
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(2,0)});
        return new JSONObject().put("kind", "geometry").put("units", units)
                .put("options", new JSONObject().put("name", "paths").put("plot", false))
                .put("solid_geometry", WktJson.wrap(path)).put("tools", new JSONObject().put("1", new JSONObject()
                        .put("tooldia", .2).put("tool_type", "C2").put("solid_geometry", WktJson.wrap(path))
                        .put("data", new JSONObject().put("cutz", cutZ).put("travelz", 2).put("feedrate", 100))));
    }
    private ProjectFile load(JSONObject... objects) throws Exception {
        Path file = directory.resolve("input.FlatPrj");
        Files.writeString(file, new JSONObject().put("version", 8.994).put("objs", new JSONArray(List.of(objects))).toString());
        return PythonProjectIO.load(file);
    }
    @ParameterizedTest @CsvSource({"INCH,IN", "METRIC,MM", "in,IN", "mm,MM"})
    void allImportedCamUnitsAreCanonicalWithoutRescalingEmbeddedCoordinates(String alias, String expected) throws Exception {
        var original = load(gerber(alias), geometry(alias, -.1));
        var copper = original.gerbers().getFirst(); var paths = original.geometries().getFirst();
        assertEquals(expected, copper.image().units()); assertEquals(expected, paths.units());
        assertEquals(2, paths.geometry().getLength()); assertFalse(paths.visible());
        assertEquals(1, copper.image().apertures().size()); assertFalse(copper.image().solidGeometry().isEmpty());
        assertEquals(.1, paths.cncDefaults().cutDepth());
        Path nativeFile = directory.resolve("native.fcnproj"); ProjectFileIO.save(original, nativeFile);
        var restored = ProjectFileIO.load(nativeFile);
        assertEquals(paths.geometry().toText(), restored.geometries().getFirst().geometry().toText());
        Path legacyFile = directory.resolve("export.FlatPrj"); PythonProjectWriter.save(restored, legacyFile);
        var again = PythonProjectIO.load(legacyFile);
        assertEquals(expected, again.gerbers().getFirst().image().units());
        assertEquals(copper.image().solidGeometry().getArea(), again.gerbers().getFirst().image().solidGeometry().getArea(), 1e-9);
        assertEquals(copper.fillColorWeb(), again.gerbers().getFirst().fillColorWeb());
    }
    @ParameterizedTest @ValueSource(strings={"gerber", "geometry"})
    void unknownUnitsAreRejectedInsteadOfAssumingMillimeters(String kind) {
        assertThrows(java.io.IOException.class, () -> load(kind.equals("gerber") ? gerber("CM") : geometry("CM", -.1)));
    }
    @ParameterizedTest @ValueSource(strings={"geometry", "excellon"})
    void positiveOptionalCutZDoesNotBecomeANegativeMachiningDepth(String kind) throws Exception {
        JSONObject object;
        if (kind.equals("geometry")) object = geometry("MM", 1);
        else {
            var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y1.0", "M30"));
            object = ExcellonFlatPrjCodec.toJson("drills", image, null, null, true, true, false);
            object.getJSONObject("tools").getJSONObject("1").put("data", new JSONObject()
                    .put("tools_drill_cutz", 1).put("tools_drill_travelz", 2).put("tools_drill_feedrate_z", 100));
        }
        var project = load(object); assertFalse(project.importWarnings().isEmpty());
        if (kind.equals("geometry")) {
            assertFalse(project.geometries().getFirst().geometry().isEmpty());
            assertNull(project.geometries().getFirst().cncDefaults());
        } else {
            assertEquals(1, project.excellons().getFirst().image().totalDrills());
            assertTrue(project.excellons().getFirst().drillDefaults().isEmpty());
        }
    }
}
