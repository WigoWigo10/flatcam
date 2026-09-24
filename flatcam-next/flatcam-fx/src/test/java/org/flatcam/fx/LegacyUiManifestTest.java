package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LegacyUiManifestTest {

    @Test
    void everyDisplayedLegacyToolHasBothThemeIcons() {
        List<LegacyUiManifest.Command> commands = Stream.of(
                LegacyUiManifest.TOOLS_PREPARATION,
                LegacyUiManifest.TOOLS_CAM,
                LegacyUiManifest.TOOLS_UTILITIES,
                LegacyUiManifest.GERBER_EDITOR,
                LegacyUiManifest.EXCELLON_EDITOR,
                LegacyUiManifest.GEOMETRY_EDITOR)
                .flatMap(List::stream).toList();

        for (LegacyUiManifest.Command command : commands) {
            assertNotNull(LegacyUiManifest.class.getResource("icons/" + command.icon()),
                    () -> "Missing light icon for " + command.label());
            assertNotNull(LegacyUiManifest.class.getResource("icons/dark/" + command.icon()),
                    () -> "Missing dark icon for " + command.label());
        }
        assertEquals(24, LegacyUiManifest.TOOLS_PREPARATION.size()
                + LegacyUiManifest.TOOLS_CAM.size() + LegacyUiManifest.TOOLS_UTILITIES.size());
    }
}
