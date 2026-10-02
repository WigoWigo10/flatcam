package org.flatcam.app.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Limited retry for Windows pending deletions; never disables JUnit cleanup. */
final class TestTemporaryDirectories {
    private TestTemporaryDirectories() { }
    static void cleanOwnedWindowsDirectory(Path directory) throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows") || directory == null) return;
        Path owned = directory.toAbsolutePath().normalize();
        if (owned.getNameCount() < 2 || !owned.getFileName().toString().startsWith("junit-"))
            throw new IOException("Refusing cleanup outside the JUnit-owned temporary directory: " + owned);
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            if (Files.notExists(owned)) return;
            try {
                List<Path> entries;
                try (var walk = Files.walk(owned)) { entries = walk.sorted(java.util.Comparator.reverseOrder()).toList(); }
                for (Path entry : entries) {
                    if (!entry.toAbsolutePath().normalize().startsWith(owned)) throw new IOException("Unexpected cleanup path: " + entry);
                    Files.deleteIfExists(entry);
                }
                return;
            } catch (IOException pendingDelete) { last = pendingDelete; }
            try { Thread.sleep(25L * (attempt + 1)); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IOException("Temporary cleanup interrupted", interrupted);
            }
        }
        throw last;
    }
}
