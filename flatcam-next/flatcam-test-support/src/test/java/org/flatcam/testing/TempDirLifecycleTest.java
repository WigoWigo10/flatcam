package org.flatcam.testing;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirDeletionStrategy;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** Verifies actual JUnit lifecycle behavior, not just the deletion helper. */
class TempDirLifecycleTest {
    private static TestExecutionSummary runFixture(Class<?> fixture) {
        var request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .configurationParameter("junit.jupiter.tempdir.cleanup.mode.default", "ALWAYS")
                .build();
        var listener = new SummaryGeneratingListener();
        try (var session = LauncherFactory.openSession()) {
            session.getLauncher().execute(request, listener);
        }
        return listener.getSummary();
    }

    @Test
    void propertiesSelectSharedStrategy() throws Exception {
        try (var stream = getClass().getResourceAsStream("/junit-platform.properties")) {
            assertNotNull(stream);
            var properties = new java.util.Properties();
            properties.load(stream);
            assertEquals(WindowsTempDirDeletionStrategy.class.getName(),
                    properties.getProperty("junit.jupiter.tempdir.deletion.strategy.default"));
            assertNull(properties.getProperty("junit.jupiter.tempdir.cleanup.mode.default"),
                    "Do not override JUnit's normal ALWAYS policy");
        }
    }

    @Test
    void repeatedClosedResourcesLeaveNoTempDirectoriesInSameJvm() throws Exception {
        ClosedResourcesFixture.created.clear();
        var summary = runFixture(ClosedResourcesFixture.class);

        assertEquals(100, summary.getTestsSucceededCount(), summary.getFailures().toString());
        assertEquals(0, summary.getTotalFailureCount());
        assertEquals(100, ClosedResourcesFixture.created.size());
        for (Path directory : ClosedResourcesFixture.created) {
            assertTrue(Files.notExists(directory), "Cleanup must finish before JVM exit: " + directory);
        }
    }

    @Test
    void failedAssertionRemainsFailureAndStillCleansDirectory() {
        var summary = runFixture(FailedAssertionFixture.class);

        assertEquals(1, summary.getTestsFailedCount());
        assertTrue(Files.notExists(FailedAssertionFixture.created));
    }

    @Test
    void explicitNeverIsRespectedForDiagnosis() throws Exception {
        var summary = runFixture(KeepForDiagnosisFixture.class);
        try {
            assertEquals(1, summary.getTestsSucceededCount());
            assertTrue(Files.isDirectory(KeepForDiagnosisFixture.created));
        } finally {
            // Retained fixture belongs to this test; empty directory only.
            Files.deleteIfExists(KeepForDiagnosisFixture.created);
        }
    }

    @Test
    void trulyOpenWindowsFileStillFailsCleanup() throws Exception {
        assumeTrue(System.getProperty("os.name", "").startsWith("Windows"));
        OpenFileFixture.completed = false;
        try {
            var summary = runFixture(OpenFileFixture.class);
            assertTrue(OpenFileFixture.completed, "Control must open/write the handle successfully");
            assertEquals(1, summary.getTestsFailedCount(),
                    "Cleanup must reject a real open handle, not turn the test green");
            assertInstanceOf(TempDirDeletionStrategy.DeletionException.class,
                    summary.getFailures().getFirst().getException().getCause());
        } finally {
            if (OpenFileFixture.open != null) OpenFileFixture.open.close();
            if (OpenFileFixture.created != null) {
                var result = new WindowsTempDirDeletionStrategy().delete(OpenFileFixture.created, null, null);
                assertTrue(result.isSuccessful(), result.failures().toString());
            }
        }
    }

    static class ClosedResourcesFixture {
        static final List<Path> created = new ArrayList<>();

        @RepeatedTest(100)
        void closesEverything(@TempDir Path directory) throws Exception {
            created.add(directory);
            Path nested = Files.createDirectories(directory.resolve("nested/inner"));
            for (int i = 0; i < 4; i++) {
                Path file = nested.resolve("file-" + i + ".txt");
                Files.writeString(file, "closed");
                assertEquals("closed", Files.readString(file));
            }
        }
    }

    static class FailedAssertionFixture {
        static Path created;

        @Test void failsNormally(@TempDir Path directory) throws Exception {
            created = directory;
            Files.writeString(directory.resolve("closed.txt"), "closed");
            fail("Intentional assertion failure: infrastructure must preserve it");
        }
    }

    static class KeepForDiagnosisFixture {
        static Path created;

        @Test void keepsExplicitly(@TempDir(cleanup = CleanupMode.NEVER) Path directory) {
            created = directory;
        }
    }

    static class OpenFileFixture {
        static Path created;
        static FileChannel open;
        static boolean completed;

        @Test void intentionallyLeaksUntilOuterTestClosesIt(@TempDir Path directory) throws Exception {
            created = directory;
            open = FileChannel.open(directory.resolve("open.txt"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, ExtendedOpenOption.NOSHARE_DELETE);
            open.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
            completed = true;
        }
    }
}
