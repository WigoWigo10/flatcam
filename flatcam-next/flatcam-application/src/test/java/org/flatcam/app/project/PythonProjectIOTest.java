package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.app.project.flatprj.ExcellonFlatPrjCodec;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.flatcam.app.project.flatprj.WktJson;
import org.flatcam.cam.excellon.ExcellonExporter;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class PythonProjectIOTest {
    @TempDir Path tempDir;

    @Test
    void importsPythonStyleGeometryListsAndAllFourObjectKinds() throws IOException {
        var gerber = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1*%", "D10*", "X10000Y10000D03*", "M02*"));
        var excellon = new ExcellonParser().parse(List.of(
                "M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y1.0", "M30"));
        JSONObject gerberJson = GerberFlatPrjCodec.toJson("top.gbr", gerber,
                "#00FF00bf", "#00b200", true, true, false, false);
        gerberJson.put("solid_geometry", new JSONArray().put(gerberJson.get("solid_geometry")));
        JSONObject excellonJson = ExcellonFlatPrjCodec.toJson("holes.drl", excellon,
                "#FF00FFbf", "#b200b2", true, true, false);
        excellonJson.put("solid_geometry", new JSONArray().put(excellonJson.get("solid_geometry")));
        Geometry path = new GeometryFactory().createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 0)});
        JSONObject geometryJson = new JSONObject().put("kind", "geometry").put("units", "MM")
                .put("options", new JSONObject().put("name", "isolation_top").put("plot", false))
                .put("solid_geometry", new JSONArray().put(WktJson.wrap(path)))
                .put("tools", new JSONObject().put("1", new JSONObject().put("tooldia", 0.2)
                        .put("tool_type", "C2")
                        .put("solid_geometry", new JSONArray().put(WktJson.wrap(path)))));
        JSONObject jobJson = new JSONObject().put("kind", "cncjob")
                .put("options", new JSONObject().put("name", "isolation_top_cnc").put("plot", false))
                .put("gcode", "G21\nG0 X0 Y0\nG1 Z-0.1\nG1 X10\nM30\n");
        JSONObject root = new JSONObject().put("version", 8.994)
                .put("objs", new JSONArray().put(gerberJson).put(excellonJson).put(geometryJson).put(jobJson));
        Path file = tempDir.resolve("python.FlatPrj");
        Files.writeString(file, root.toString());

        ProjectFile project = PythonProjectIO.load(file);
        assertEquals(1, project.gerbers().size());
        assertFalse(project.gerbers().get(0).image().solidGeometry().isEmpty());
        assertEquals(1, project.excellons().size());
        assertEquals(1, project.excellons().get(0).image().totalDrills());
        assertFalse(project.excellons().get(0).image().solidGeometry().isEmpty());
        assertEquals(1, project.geometries().size());
        assertTrue(project.geometries().get(0).strokeOnly());
        assertEquals(0.2, project.geometries().get(0).tools().get(0).toolDiameter());
        assertFalse(project.geometries().get(0).visible());
        assertEquals(1, project.cncJobs().size());
        assertFalse(project.cncJobs().get(0).visible());
        assertTrue(project.cncJobs().get(0).gcode().contains("G1 X10"));
        assertThrows(IOException.class, () -> ProjectFileIO.load(file));
    }

    @Test
    void rejectsUnknownObjectInsteadOfSilentlyDroppingIt() throws IOException {
        Path file = tempDir.resolve("unknown.FlatPrj");
        Files.writeString(file, new JSONObject().put("version", 8.994).put("objs",
                new JSONArray().put(new JSONObject().put("kind", "unknown"))).toString());
        assertThrows(IOException.class, () -> PythonProjectIO.load(file));
    }

    @Test
    void normalizesPythonInchLabelForExcellonOperations() throws IOException {
        var image = new ExcellonParser().parse(List.of(
                "M48", "INCH", "T1C0.03125", "%", "T1", "X1.0Y2.0", "M30"));
        JSONObject object = ExcellonFlatPrjCodec.toJson("holes.drl", image,
                null, null, true, true, false);
        object.put("excellon_units", "INCH");
        Path file = tempDir.resolve("inch.FlatPrj");
        Files.writeString(file, new JSONObject().put("version", 8.994)
                .put("objs", new JSONArray().put(object)).toString());

        var restored = PythonProjectIO.load(file).excellons().get(0).image();

        assertEquals("IN", restored.units());
        assertEquals(image.drills(), new ExcellonParser()
                .parse(new ExcellonExporter().export(restored).lines().toList()).drills());
    }

    @Test
    void optionalRealPythonProjectFixture() throws IOException {
        String fixture = System.getProperty("flatcam.python.project.fixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());
        ProjectFile project = PythonProjectIO.load(Path.of(fixture));
        assertEquals(3, project.gerbers().size());
        assertEquals(3, project.excellons().size());
        assertEquals(3, project.geometries().size());
        assertEquals(9, project.cncJobs().size());
        assertTrue(project.cncJobs().stream().noneMatch(ProjectFile.CncJobRecord::visible));
        assertTrue(project.gerbers().stream().allMatch(entry -> !entry.image().isEmpty()));
        assertTrue(project.gerbers().stream().allMatch(entry ->
                "MM".equals(entry.image().units()) || "IN".equals(entry.image().units())));
        assertTrue(project.excellons().stream().allMatch(entry -> !entry.image().isEmpty()));
        ExcellonExporter exporter = new ExcellonExporter();
        ExcellonParser parser = new ExcellonParser();
        for (ProjectFile.ExcellonEntry entry : project.excellons()) {
            var reopened = parser.parse(exporter.export(entry.image()).lines().toList());
            assertEquals(entry.image().toolDiameters(), reopened.toolDiameters());
            assertEquals(entry.image().drills(), reopened.drills());
            assertEquals(entry.image().slots(), reopened.slots());
        }
        assertTrue(project.geometries().stream().allMatch(entry -> !entry.geometry().isEmpty()));
        assertTrue(project.cncJobs().stream().allMatch(entry -> !entry.gcode().isBlank()));

        Path nativeCopy = tempDir.resolve("imported.fcnproj");
        ProjectFileIO.save(project, nativeCopy);
        ProjectFile reopened = ProjectFileIO.load(nativeCopy);
        assertEquals(project.gerbers().size(), reopened.gerbers().size());
        assertEquals(project.excellons().size(), reopened.excellons().size());
        assertEquals(project.geometries().size(), reopened.geometries().size());
        assertEquals(project.cncJobs().size(), reopened.cncJobs().size());
        for (int index = 0; index < project.gerbers().size(); index++) {
            var original = project.gerbers().get(index);
            var saved = reopened.gerbers().get(index);
            assertEquals(original.name(), saved.name());
            assertEquals(original.image().solidGeometry().getArea(),
                    saved.image().solidGeometry().getArea(), 1e-6);
        }
        for (int index = 0; index < project.excellons().size(); index++) {
            assertEquals(project.excellons().get(index).image().drills(),
                    reopened.excellons().get(index).image().drills());
            assertEquals(project.excellons().get(index).image().slots(),
                    reopened.excellons().get(index).image().slots());
        }
        for (int index = 0; index < project.geometries().size(); index++) {
            var original = project.geometries().get(index);
            var saved = reopened.geometries().get(index);
            assertEquals(original.name(), saved.name());
            assertEquals(original.tools().size(), saved.tools().size());
            assertEquals(original.geometry().getNumPoints(), saved.geometry().getNumPoints());
        }
        for (int index = 0; index < project.cncJobs().size(); index++) {
            assertEquals(project.cncJobs().get(index).gcode(), reopened.cncJobs().get(index).gcode());
            assertEquals(project.cncJobs().get(index).visible(), reopened.cncJobs().get(index).visible());
        }
    }
}
