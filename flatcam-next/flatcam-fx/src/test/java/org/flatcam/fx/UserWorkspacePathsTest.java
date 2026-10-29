package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserWorkspacePathsTest {
    @TempDir Path temporary;
    @Test void installFolderAndItsChildrenAreProtectedButSiblingDocumentsAreAllowed() throws Exception {
        Path install = Files.createDirectory(temporary.resolve("FlatCAM FX Bêta"));
        Path docs = Files.createDirectory(temporary.resolve("Documentos"));
        assertTrue(UserWorkspacePaths.insideInstallation(install.resolve("projeto.fcnproj"), install));
        assertTrue(UserWorkspacePaths.insideInstallation(install.resolve("sub/../sub/placa.nc"), install));
        assertFalse(UserWorkspacePaths.insideInstallation(docs.resolve("placa.nc"), install));
        assertFalse(UserWorkspacePaths.insideInstallation(temporary.resolve("FlatCAM FX Bêta-cópia/placa.nc"), install));
        assertFalse(UserWorkspacePaths.insideInstallation(docs.resolve("placa.nc"), null));
    }
}
