package org.flatcam.testing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirDeletionStrategy;
import org.junit.jupiter.api.io.TempDirDeletionStrategy.DeletionResult;

class WindowsTempDirDeletionStrategyTest {
    @TempDir Path directory;

    private static TempDirDeletionStrategy fails(Path path, Exception cause) {
        return (root, element, context) -> DeletionResult.builder(root).addFailure(path, cause).build();
    }

    @Test
    void retriesOnlyEmptyDirectoriesWithBoundedBackoff() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var attempts = new AtomicInteger();
        var waits = new ArrayList<Long>();
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> {
                    if (attempts.incrementAndGet() < 3) throw new DirectoryNotEmptyException(path.toString());
                    Files.delete(path);
                }, waits::add);

        assertTrue(strategy.delete(owned, null, null).isSuccessful());
        assertEquals(3, attempts.get());
        assertEquals(List.of(25L, 50L), waits);
        assertFalse(Files.exists(owned));
    }

    @Test
    void persistentFailureStillFailsAfterSixAttempts() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var attempts = new AtomicInteger();
        var waits = new ArrayList<Long>();
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> { attempts.incrementAndGet(); throw new DirectoryNotEmptyException(path.toString()); },
                waits::add);

        var result = strategy.delete(owned, null, null);
        assertFalse(result.isSuccessful());
        assertTrue(result.toException().isPresent());
        assertEquals(WindowsTempDirDeletionStrategy.ATTEMPTS, attempts.get());
        assertEquals(List.of(25L, 50L, 75L, 100L, 125L), waits);
        assertTrue(Files.isDirectory(owned));
    }

    @Test
    void neverRecursivelyDeletesContentsAppearingAfterStandardCleanup() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        Path sentinel = Files.writeString(owned.resolve("sentinel.txt"), "keep");
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> fail("Non-empty directory must not be retried"),
                ms -> fail("Must not wait for a real non-empty directory"));

        assertFalse(strategy.delete(owned, null, null).isSuccessful());
        assertEquals("keep", Files.readString(sentinel));
    }

    @Test
    void stopsIfNewContentsAppearDuringRetry() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var attempts = new AtomicInteger();
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> { attempts.incrementAndGet(); throw new DirectoryNotEmptyException(path.toString()); },
                ms -> {
                    try { Files.writeString(owned.resolve("new.txt"), "keep"); }
                    catch (IOException error) { throw new AssertionError(error); }
                });

        assertFalse(strategy.delete(owned, null, null).isSuccessful());
        assertEquals(1, attempts.get());
        assertEquals("keep", Files.readString(owned.resolve("new.txt")));
    }

    @Test
    void accessDeniedFileIsNotRetriedOrSuppressed() throws Exception {
        Path file = Files.writeString(directory.resolve("blocked.txt"), "keep");
        var denied = new AccessDeniedException(file.toString());
        var strategy = new WindowsTempDirDeletionStrategy(fails(file, denied), true,
                path -> fail("File failure must not be retried"), ms -> fail("Must not sleep"));

        var result = strategy.delete(directory, null, null);
        assertSame(denied, result.failures().getFirst().cause());
        assertEquals("keep", Files.readString(file));
    }

    @Test
    void directoryAccessDeniedDuringRetryRemainsFailure() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var denied = new AccessDeniedException(owned.toString());
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> { throw denied; }, ms -> fail("Must not sleep after access denied"));

        assertSame(denied, strategy.delete(owned, null, null).failures().getFirst().cause());
    }

    @Test
    void nonWindowsPreservesStandardResult() throws Exception {
        var delegate = fails(directory, new DirectoryNotEmptyException(directory.toString()));
        var strategy = new WindowsTempDirDeletionStrategy(delegate, false,
                path -> fail("No Windows retries"), ms -> fail("No Windows wait"));

        assertFalse(strategy.delete(directory, null, null).isSuccessful());
        assertTrue(Files.isDirectory(directory));
    }

    @Test
    void successfulDelegateDoesNotWait() throws Exception {
        var strategy = new WindowsTempDirDeletionStrategy(
                TempDirDeletionStrategy.Standard.INSTANCE, true,
                path -> fail("Successful standard cleanup needs no retry"), ms -> fail("Must not sleep"));
        Path owned = Files.createDirectory(directory.resolve("owned"));
        Files.writeString(owned.resolve("file.txt"), "closed");

        assertTrue(strategy.delete(owned, null, null).isSuccessful());
        assertFalse(Files.exists(owned));
    }

    @Test
    void directoryAlreadyDeletedBeforeCleanupNeedsNoRetry() throws Exception {
        Path missing = directory.resolve("already-deleted");
        var strategy = new WindowsTempDirDeletionStrategy(
                (root, element, context) -> { fail("Already deleted root must not be traversed"); return null; },
                true, path -> fail("Already deleted root"), ms -> fail("Must not sleep"));
        assertTrue(strategy.delete(missing, null, null).isSuccessful());
    }

    @Test
    void disappearedDirectoryCountsAsSuccessfullyDeleted() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var strategy = new WindowsTempDirDeletionStrategy(
                (root, element, context) -> {
                    Files.delete(root);
                    return DeletionResult.builder(root)
                            .addFailure(root, new DirectoryNotEmptyException(root.toString())).build();
                }, true, path -> fail("Directory no longer exists"), ms -> fail("Must not sleep"));

        assertTrue(strategy.delete(owned, null, null).isSuccessful());
    }

    @Test
    void deletionRaceDoesNotCauseFalseFailure() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> { Files.delete(path); throw new NoSuchFileException(path.toString()); },
                ms -> fail("Must not sleep"));

        assertTrue(strategy.delete(owned, null, null).isSuccessful());
    }

    @Test
    void retriesChildBeforeParentAndNeverOutsideOwnedRoot() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        Path child = Files.createDirectory(owned.resolve("child"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        var order = new ArrayList<Path>();
        var strategy = new WindowsTempDirDeletionStrategy(
                (root, element, context) -> DeletionResult.builder(root)
                        .addFailure(root, new DirectoryNotEmptyException(root.toString()))
                        .addFailure(outside, new DirectoryNotEmptyException(outside.toString()))
                        .addFailure(child, new DirectoryNotEmptyException(child.toString())).build(),
                true, path -> { order.add(path); Files.delete(path); }, ms -> fail("Must not sleep"));

        var result = strategy.delete(owned, null, null);
        assertEquals(List.of(child, owned), order);
        assertEquals(outside, result.failures().getFirst().path());
        assertTrue(Files.isDirectory(outside));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void standardCleanupDoesNotFollowWindowsJunctionOutsideOwnedRoot() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel.txt"), "keep");
        Path junction = owned.resolve("junction");
        createJunction(junction, outside);

        var result = new WindowsTempDirDeletionStrategy().delete(owned, null, null);
        assertTrue(result.isSuccessful(), result.failures().toString());
        assertFalse(Files.exists(owned));
        assertEquals("keep", Files.readString(sentinel));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void retryDoesNotFollowWindowsJunctionOutsideOwnedRoot() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel.txt"), "keep");
        Path junction = owned.resolve("junction");
        createJunction(junction, outside);
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(junction, new DirectoryNotEmptyException(junction.toString())), true,
                path -> fail("Do not retry external junctions"), ms -> fail("Must not sleep"));
        try {
            assertFalse(strategy.delete(owned, null, null).isSuccessful());
            assertEquals("keep", Files.readString(sentinel));
        } finally {
            Files.deleteIfExists(junction); // Link only, never the target directory.
        }
    }

    private static void createJunction(Path junction, Path target) throws Exception {
        // Both absolute paths are direct descendants of this test's @TempDir.
        Process process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                junction.toString(), target.toString()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Creating test junction timed out");
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void interruptionIsPreservedAndReported() throws Exception {
        Path owned = Files.createDirectory(directory.resolve("owned"));
        var strategy = new WindowsTempDirDeletionStrategy(
                fails(owned, new DirectoryNotEmptyException(owned.toString())), true,
                path -> { throw new DirectoryNotEmptyException(path.toString()); },
                ms -> { throw new InterruptedException("test interruption"); });
        try {
            IOException error = assertThrows(IOException.class, () -> strategy.delete(owned, null, null));
            assertInstanceOf(InterruptedException.class, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
