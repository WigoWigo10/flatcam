package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFileIOTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsGerbersExcellonsAndCncJobs() throws IOException {
        ProjectFile original = new ProjectFile(
                List.of("C:/board/top.gbr", "C:/board/bottom.gbr"),
                List.of("C:/board/pth.drl"),
                List.of(new ProjectFile.CncJobRecord("pth.drl", "C:/board/pth_drill.nc"))
        );

        Path file = tempDir.resolve("project.fcnproj");
        ProjectFileIO.save(original, file);
        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(original.gerberPaths(), loaded.gerberPaths());
        assertEquals(original.excellonPaths(), loaded.excellonPaths());
        assertEquals(original.cncJobs(), loaded.cncJobs());
    }

    @Test
    void roundTripsEmptyProject() throws IOException {
        ProjectFile empty = new ProjectFile(List.of(), List.of(), List.of());
        Path file = tempDir.resolve("empty.fcnproj");
        ProjectFileIO.save(empty, file);
        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(List.of(), loaded.gerberPaths());
        assertEquals(List.of(), loaded.excellonPaths());
        assertEquals(List.of(), loaded.cncJobs());
    }

    @Test
    void rejectsUnknownVersion() throws IOException {
        Path file = tempDir.resolve("future.fcnproj");
        Files.writeString(file, "{\"version\": 99, \"gerbers\": [], \"excellons\": [], \"cncJobs\": []}");
        assertThrows(IOException.class, () -> ProjectFileIO.load(file));
    }
}
