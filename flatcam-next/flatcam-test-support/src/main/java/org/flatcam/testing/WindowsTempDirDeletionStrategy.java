package org.flatcam.testing;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirDeletionStrategy;

/**
 * Retains JUnit's standard cleanup and link handling. On Windows only, retries
 * deletion of already-empty directories after DirectoryNotEmptyException.
 * Does not retry access-denied files, recursively delete new contents, or hide
 * persistent failures. This class is available only on the test classpath.
 */
public final class WindowsTempDirDeletionStrategy implements TempDirDeletionStrategy {
    static final int ATTEMPTS = 6;
    private final TempDirDeletionStrategy delegate;
    private final boolean windows;
    private final DeleteDirectory deleteDirectory;
    private final Pause pause;

    public WindowsTempDirDeletionStrategy() {
        this(Standard.INSTANCE, System.getProperty("os.name", "").startsWith("Windows"),
                Files::delete, Thread::sleep);
    }

    WindowsTempDirDeletionStrategy(TempDirDeletionStrategy delegate, boolean windows,
                                  DeleteDirectory deleteDirectory, Pause pause) {
        this.delegate = delegate;
        this.windows = windows;
        this.deleteDirectory = deleteDirectory;
        this.pause = pause;
    }

    @Override
    public DeletionResult delete(Path tempDir, AnnotatedElementContext elementContext,
                                 ExtensionContext extensionContext) throws IOException {
        // Establish the exact JUnit-owned boundary before any recursive operation.
        Path root = tempDir.toAbsolutePath().normalize();
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (NoSuchFileException deletedMeanwhile) {
            if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
                return DeletionResult.builder(tempDir).build();
            }
            throw deletedMeanwhile;
        }
        DeletionResult original = delegate.delete(tempDir, elementContext, extensionContext);
        if (original.isSuccessful() || !windows
                || !tempDir.getFileSystem().equals(FileSystems.getDefault())) {
            return original;
        }
        var result = DeletionResult.builder(tempDir);
        // Children first: an empty child may be pending deletion before its parent.
        for (var failure : original.failures().stream()
                .sorted(Comparator.comparingInt((DeletionFailure f) -> f.path().getNameCount()).reversed())
                .toList()) {
            Exception remaining = failure.cause();
            Path path = failure.path().toAbsolutePath().normalize();
            if (remaining instanceof DirectoryNotEmptyException && path.startsWith(root)) {
                remaining = retryEmptyDirectory(path, realRoot, remaining);
            }
            if (remaining != null) {
                result.addFailure(failure.path(), remaining);
            }
        }
        return result.build();
    }

    private Exception retryEmptyDirectory(Path path, Path realRoot, Exception original) throws IOException {
        Exception last = original;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                // Never follow replacement symlinks/junctions outside the owned root.
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        || !path.toRealPath().startsWith(realRoot)) {
                    if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return null;
                    return last;
                }
                try (var children = Files.newDirectoryStream(path)) {
                    if (children.iterator().hasNext()) return last;
                }
                deleteDirectory.delete(path);
                return null;
            } catch (NoSuchFileException deletedMeanwhile) {
                return null;
            } catch (DirectoryNotEmptyException pending) {
                last = pending;
            } catch (IOException persistent) {
                return persistent;
            }
            if (attempt + 1 < ATTEMPTS) {
                try {
                    pause.sleep(25L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while cleaning JUnit temporary directory " + path,
                            interrupted);
                }
            }
        }
        return last;
    }

    @FunctionalInterface
    interface DeleteDirectory {
        void delete(Path path) throws IOException;
    }

    @FunctionalInterface
    interface Pause {
        void sleep(long milliseconds) throws InterruptedException;
    }
}
