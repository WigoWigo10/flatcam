package org.flatcam.fx;

import java.nio.file.*;

/** User documents belong outside the application's replaceable installation directory. */
final class UserWorkspacePaths {
    private UserWorkspacePaths() { }
    static Path installationRoot() {
        return ProcessHandle.current().info().command().map(Path::of).map(Path::getParent)
                .filter(root -> Files.isRegularFile(root.resolve("flatcam-release.json"))
                        && Files.isDirectory(root.resolve("app")) && Files.isDirectory(root.resolve("runtime"))).orElse(null);
    }
    static boolean insideInstallation(Path file, Path root) {
        if (root == null) return false;
        Path path = file.toAbsolutePath().normalize(), base = root.toAbsolutePath().normalize();
        if (path.startsWith(base)) return true;
        try {
            Path existing = path;
            while (existing != null && !Files.exists(existing)) existing = existing.getParent();
            return existing != null && existing.toRealPath().resolve(existing.relativize(path)).normalize().startsWith(base.toRealPath());
        } catch (java.io.IOException invalid) { return false; }
    }
    static Path documents() {
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return Files.isDirectory(documents) ? documents : home;
    }
}
