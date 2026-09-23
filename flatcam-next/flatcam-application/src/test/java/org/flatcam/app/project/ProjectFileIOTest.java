package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.json.JSONObject;

class ProjectFileIOTest {

    @TempDir
    Path tempDir;

    private static final GerberImage RECTANGLE_GERBER = new GerberParser().parse(List.of(
            "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));

    private static final ExcellonImage DRILL_EXCELLON = new ExcellonParser().parse(List.of(
            "M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y1.0", "X3.0Y1.0G85X5.0Y1.0", "M30"));

    @Test
    void roundTripsEmbeddedGerberAndExcellonGeometryAndDisplayState() throws IOException {
        ProjectFile.GerberEntry gerber = new ProjectFile.GerberEntry(
                "top.gbr", RECTANGLE_GERBER, "0xbbf268ff", "0x006e20ff", false, true, true, false);
        ProjectFile.ExcellonEntry excellon = new ProjectFile.ExcellonEntry(
                "pth.drl", DRILL_EXCELLON, "0xc40000ff", "0x750000ff", true, false, true);
        ProjectFile.CncJobRecord job = new ProjectFile.CncJobRecord("pth.drl", "C:/board/pth_drill.nc");
        ProjectFile original = new ProjectFile(List.of(gerber), List.of(excellon), List.of(job));

        Path file = tempDir.resolve("project.fcnproj");
        ProjectFileIO.save(original, file);
        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(1, loaded.gerbers().size());
        ProjectFile.GerberEntry loadedGerber = loaded.gerbers().get(0);
        assertEquals("top.gbr", loadedGerber.name());
        assertEquals("0xbbf268ff", loadedGerber.fillColorWeb());
        assertEquals("0x006e20ff", loadedGerber.strokeColorWeb());
        assertFalse(loadedGerber.visible());
        assertTrue(loadedGerber.filled());
        assertTrue(loadedGerber.multicolor());
        assertFalse(loadedGerber.followMode());
        assertEquals(RECTANGLE_GERBER.units(), loadedGerber.image().units());
        assertTrue(RECTANGLE_GERBER.solidGeometry().equalsExact(loadedGerber.image().solidGeometry(), 1e-9),
                "Gerber solid_geometry must survive the WKT round-trip exactly");
        assertEquals(RECTANGLE_GERBER.apertures().keySet(), loadedGerber.image().apertures().keySet());

        assertEquals(1, loaded.excellons().size());
        ProjectFile.ExcellonEntry loadedExcellon = loaded.excellons().get(0);
        assertEquals("pth.drl", loadedExcellon.name());
        assertTrue(loadedExcellon.visible());
        assertFalse(loadedExcellon.filled());
        assertTrue(loadedExcellon.multicolor());
        assertEquals(DRILL_EXCELLON.units(), loadedExcellon.image().units());
        assertEquals(DRILL_EXCELLON.totalDrills(), loadedExcellon.image().totalDrills());
        assertEquals(DRILL_EXCELLON.totalSlots(), loadedExcellon.image().totalSlots());
        assertEquals(DRILL_EXCELLON.drills().get(0).x(), loadedExcellon.image().drills().get(0).x(), 1e-9);
        assertEquals(DRILL_EXCELLON.drills().get(0).y(), loadedExcellon.image().drills().get(0).y(), 1e-9);

        assertEquals(List.of(job), loaded.cncJobs());
    }

    @Test
    void roundTripsEmptyProject() throws IOException {
        ProjectFile empty = new ProjectFile(List.of(), List.of(), List.of());
        Path file = tempDir.resolve("empty.fcnproj");
        ProjectFileIO.save(empty, file);
        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(List.of(), loaded.gerbers());
        assertEquals(List.of(), loaded.excellons());
        assertEquals(List.of(), loaded.cncJobs());
    }

    @Test
    void savedFileIsActuallyXzCompressedByDefault() throws IOException {
        ProjectFile project = new ProjectFile(
                List.of(new ProjectFile.GerberEntry("g.gbr", RECTANGLE_GERBER, null, null, true, true, false, false)),
                List.of(), List.of());
        Path file = tempDir.resolve("compressed.fcnproj");
        ProjectFileIO.save(project, file);

        byte[] raw = Files.readAllBytes(file);
        assertThrows(RuntimeException.class, () -> new JSONObject(new String(raw)),
                "a compressed file's raw bytes should not parse as plain JSON");

        ProjectFile loaded = ProjectFileIO.load(file);
        assertEquals(1, loaded.gerbers().size());
    }

    @Test
    void uncompressedSaveStillLoadsViaAutoDetection() throws IOException {
        ProjectFile project = new ProjectFile(
                List.of(new ProjectFile.GerberEntry("g.gbr", RECTANGLE_GERBER, null, null, true, true, false, false)),
                List.of(), List.of());
        Path file = tempDir.resolve("plain.fcnproj");
        ProjectFileIO.save(project, file, false);

        // A plain save really is plain JSON text - readable without decompression.
        JSONObject plain = new JSONObject(Files.readString(file));
        assertEquals(2, plain.getInt("version"));

        ProjectFile loaded = ProjectFileIO.load(file);
        assertEquals(1, loaded.gerbers().size());
    }

    @Test
    void loadsLegacyV1ProjectsByReparsingTheirSourceFiles() throws IOException {
        Path gerberFile = tempDir.resolve("legacy.gbr");
        Files.writeString(gerberFile, String.join("\n",
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));

        JSONObject legacy = new JSONObject();
        legacy.put("version", 1);
        legacy.put("gerbers", new org.json.JSONArray(List.of(gerberFile.toString().replace('\\', '/'))));
        legacy.put("excellons", new org.json.JSONArray());
        legacy.put("cncJobs", new org.json.JSONArray());
        Path file = tempDir.resolve("legacy.fcnproj");
        Files.writeString(file, legacy.toString());

        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(1, loaded.gerbers().size());
        assertTrue(loaded.gerbers().get(0).image().solidGeometry().getArea() > 0);
        assertEquals(0, loaded.excellons().size());
    }

    @Test
    void rejectsUnknownVersion() throws IOException {
        Path file = tempDir.resolve("future.fcnproj");
        Files.writeString(file, "{\"version\": 99, \"objs\": [], \"options\": {}}");
        assertThrows(IOException.class, () -> ProjectFileIO.load(file));
    }
}
