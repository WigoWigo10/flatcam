package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolPanelIconsTest {

    @Test
    void actionLabelsResolveToLegacyArtwork() {
        assertEquals("geometry32.png", ToolPanelIcons.buttonIcon("Gerar Geometry"));
        assertEquals("cnc16.png", ToolPanelIcons.buttonIcon("Generate CNC Job"));
        assertEquals("param_all32.png", ToolPanelIcons.buttonIcon("Apply parameters to all tools"));
        assertNull(ToolPanelIcons.buttonIcon("Controle desconhecido"));
    }

    @Test
    void everyToolIconExistsForBothThemes() {
        for (String file : java.util.stream.Stream.concat(ToolPanelIcons.iconFiles().stream(),
                List.of("deselect_all32.png", "edit16.png", "iso_16.png", "geometry32.png",
                        "eraser26.png", "cut32_bis.png", "code_editor32.png", "cnc32.png",
                        "source32.png", "apply32.png").stream()).distinct().toList()) {
            assertNotNull(ToolPanelIcons.class.getResource("icons/" + file), file + " (light)");
            assertNotNull(ToolPanelIcons.class.getResource("icons/dark/" + file), file + " (dark)");
        }
    }
}
