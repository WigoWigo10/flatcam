package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Accordion;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Control;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class ToolDefaultsDescriptionsTest {
    @Test void everyPersistedSettingHasContextualHelpInBothUnits() {
        List<String> missing = new ArrayList<>();
        for (var setting : ToolDefaults.settings()) for (boolean metric : List.of(true,false)) {
            try {
                String text = ToolDefaultsDescriptions.text(setting,metric);
                assertTrue(text.contains(setting.factory(metric)));
                assertTrue(text.contains("painéis já abertos"));
                assertTrue(text.contains("validação completa"));
            } catch (IllegalArgumentException absent) { missing.add(absent.getMessage()); }
        }
        assertEquals(List.of(),missing);
    }
    @Test void feedsAndSpecialUnitsDoNotClaimEveryNumberIsMillimetres() {
        java.util.function.Function<String,ToolDefaults.Setting> setting = key -> ToolDefaults.settings().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
        assertTrue(ToolDefaultsDescriptions.text(setting.apply("drilling.feedz"),true).contains("mm/min"));
        assertTrue(ToolDefaultsDescriptions.text(setting.apply("geometry.feedrate"),false).contains("in/min"));
        assertTrue(ToolDefaultsDescriptions.text(setting.apply("etch.thickness"),true).contains("micrômetros"));
        assertTrue(ToolDefaultsDescriptions.text(setting.apply("thieving.minarea"),true).contains("mm²"));
        assertTrue(ToolDefaultsDescriptions.text(setting.apply("ncc.seedpolicy"),true).contains("PYTHON"));
        assertTrue(ToolDefaultsDescriptions.text(setting.apply("import.excellon.units"),true).contains("RECUSAR"));
    }
    @Test @EnabledOnOs(OS.WINDOWS) void preferenceFieldsAndRebuiltInchFieldsHaveDirectAccessibleRichHelp() throws Exception {
        TerminalPanelTest.fx(() -> {
            Node root = ToolDefaultsPane.build();
            PanelTooltips.install(root,"Preferencias");
            assertFields(root);
            List<Node> nodes = new ArrayList<>(); collect(root,nodes);
            nodes.stream().filter(n -> n instanceof RadioButton radio && "in".equals(radio.getText()))
                    .map(n -> (RadioButton)n).findFirst().orElseThrow().setSelected(true);
            assertFields(root);
            return null;
        });
    }
    private static void assertFields(Node root) {
        List<Node> nodes = new ArrayList<>(); collect(root,nodes);
        for (var setting : ToolDefaults.settings()) {
            Node editor = nodes.stream().filter(n -> ("tool-default-"+setting.key().replace('.','-')).equals(n.getId())).findFirst().orElseThrow();
            assertNotNull(editor.getAccessibleHelp(),setting.key());
            assertInstanceOf(TooltipContent.class,editor.getProperties().get(FluidTooltips.CONTENT_KEY));
            assertTrue(editor.getAccessibleHelp().contains("Padrão de fábrica:"));
        }
        assertTrue(nodes.stream().filter(n -> n.getId()!=null && n.getId().startsWith("tool-defaults-") && n instanceof javafx.scene.control.Button)
                .allMatch(n -> n.getAccessibleHelp()!=null));
    }
    private static void collect(Node node,List<Node> nodes) {
        nodes.add(node);
        if (node instanceof Accordion accordion) accordion.getPanes().forEach(pane -> collect(pane,nodes));
        else if (node instanceof TitledPane pane) collect(pane.getContent(),nodes);
        else if (node instanceof Pane pane) List.copyOf(pane.getChildren()).forEach(child -> collect(child,nodes));
    }
}
