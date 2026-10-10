package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.app.project.ProjectFileIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in diagnostic: reads a native fixture, writes only a temporary copy; no Stage/preferences. */
@EnabledOnOs(OS.WINDOWS)
class MainProjectPerformanceTest {
    @TempDir Path directory;

    @Test void profileNativeProjectRoundTrip() throws Exception {
        String fixture = System.getProperty("flatcam.native.project.fixture");
        assumeTrue(fixture != null && !fixture.isBlank(), "Optional dense native project fixture");
        Path source = Path.of(fixture);
        byte[] originalHash = hash(source);
        long start = System.nanoTime();
        var project = ProjectFileIO.load(source);
        report("decode", start);
        Path copy = directory.resolve("copy.fcnproj");
        start = System.nanoTime();
        ProjectFileIO.save(project, copy);
        report("encode-compress", start);
        System.out.printf("[PROJECT-PROFILE] sourceBytes=%d savedBytes=%d%n", Files.size(source), Files.size(copy));
        var decoded = ProjectFileIO.load(copy);
        assertEquals(project.cncJobs(), decoded.cncJobs());
        assertEquals(project.geometries().size(), decoded.geometries().size());
        try (var session = new MainCamFlowTest.Session("MM")) {
            session.release.countDown();
            start = System.nanoTime();
            session.window.openProject(copy);
            report("open-including-previews-and-publication", start);
            var reopened = MainIsolationMachiningTest.snapshot(session.window);
            assertEquals(project.cncJobs(), reopened.cncJobs());
            assertEquals(project.gerbers().size(), reopened.gerbers().size());
            assertEquals(project.excellons().size(), reopened.excellons().size());
            for (var geometry : project.geometries()) {
                var actual = reopened.geometries().stream().filter(g -> g.name().equals(geometry.name())).findFirst().orElseThrow();
                assertTrue(geometry.geometry().equalsExact(actual.geometry()));
                assertEquals(geometry.cncDefaults(), actual.cncDefaults());
                assertEquals(geometry.cncSettings(), actual.cncSettings());
            }
        } finally { assertArrayEquals(originalHash, hash(source)); }
    }
    private static byte[] hash(Path file) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }
    private static void report(String phase, long start) {
        System.out.printf(java.util.Locale.ROOT, "[PROJECT-PROFILE] %s=%.1fms%n", phase, (System.nanoTime()-start)/1e6);
    }
}
