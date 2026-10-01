package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDescriptionsTest {

    private static List<LegacyUiManifest.Command> allTools() {
        List<LegacyUiManifest.Command> tools = new java.util.ArrayList<>(LegacyUiManifest.TOOLS_PREPARATION);
        tools.addAll(LegacyUiManifest.TOOLS_CAM);
        tools.addAll(LegacyUiManifest.TOOLS_UTILITIES);
        return tools;
    }

    @Test
    void everyToolOfTheMenuHasATitleAndATextForItsTooltip() {
        for (LegacyUiManifest.Command tool : allTools()) {
            ToolDescriptions.Description description = ToolDescriptions.of(tool.id());
            assertNotNull(description, "no tooltip for the tool " + tool.id());
            assertTrue(description.title().toLowerCase().contains(tool.label().toLowerCase().replace(" tool", "")),
                    tool.id() + ": the title '" + description.title() + "' does not name '" + tool.label() + "'");
            assertTrue(description.text().length() > 30, tool.id() + ": the text is too short to help");
        }
    }

    @Test
    void everyToolIsPortedSoNoneClaimsOtherwise() {
        for (ToolDescriptions.Description description : java.util.stream.Stream.of("film", "rules", "copper_thieving",
                "calibration").map(ToolDescriptions::of).toList()) {
            assertTrue(!description.text().contains("Ainda não portada"), description.title());
        }
    }

    @Test
    void applyPutsTheTitleAndTheTextWhereTheTooltipManagerLooks() {
        Map<Object, Object> properties = new HashMap<>();
        ToolDescriptions.apply(properties, "qrcode");
        assertEquals("QRCode Tool", properties.get(FluidTooltips.TITLE_KEY));
        assertTrue(properties.get(FluidTooltips.TEXT_KEY).toString().contains("QR"));
        Map<Object, Object> unknown = new HashMap<>();
        ToolDescriptions.apply(unknown, "does_not_exist");
        assertTrue(unknown.isEmpty());
        assertNull(ToolDescriptions.of("does_not_exist"));
        ToolDescriptions.apply(unknown, "Titulo", "Texto");
        assertEquals("Titulo", unknown.get(FluidTooltips.TITLE_KEY));
    }
}
