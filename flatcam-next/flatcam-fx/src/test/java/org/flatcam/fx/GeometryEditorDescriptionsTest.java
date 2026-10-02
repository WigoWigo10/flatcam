package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class GeometryEditorDescriptionsTest {
    @Test void helpDescribesEffectsLimitsAndKeyboardWithoutClaimingFullParity() {
        for (String id : List.of("text","eraser","buffer","cut_path","subtract","union","intersection","explode","move","copy")) {
            var help = GeometryEditorDescriptions.of(id);
            assertNotNull(help,id); assertTrue(help.text().length() > 100,id);
            assertTrue(help.text().contains("Ctrl+Z"),id);
        }
        assertTrue(GeometryEditorDescriptions.of("eraser").text().contains("todas as ferramentas"));
        assertTrue(GeometryEditorDescriptions.of("eraser").text().contains("molde original"));
        assertTrue(GeometryEditorDescriptions.of("eraser").text().contains("Durante o cálculo"));
        assertTrue(GeometryEditorDescriptions.of("buffer").text().contains("distância negativa"));
        assertTrue(GeometryEditorDescriptions.of("cut_path").text().contains("mantém os cortadores"));
        assertNull(GeometryEditorDescriptions.of("delete")); assertNotNull(GeometryEditorDescriptions.of("paint"));
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void menuAndIconUseIdenticalRichHelpWithoutTwoTooltips() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        var task = new FutureTask<Void>(() -> {
            Button icon = new Button(); icon.setTooltip(new Tooltip("Borracha"));
            MenuItem menu = new MenuItem("Borracha");
            GeometryEditorDescriptions.apply(icon,"eraser"); GeometryEditorDescriptions.apply(menu,"eraser");
            assertNull(icon.getTooltip()); assertEquals("Borracha por molde",icon.getAccessibleText());
            assertEquals(icon.getAccessibleHelp(),menu.getProperties().get(FluidTooltips.TEXT_KEY));
            assertEquals(icon.getProperties().get(FluidTooltips.CONTENT_KEY),menu.getProperties().get(FluidTooltips.CONTENT_KEY));
            var content=(TooltipContent) icon.getProperties().get(FluidTooltips.CONTENT_KEY);
            assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Atenção:") && s.style()==TooltipContent.Style.NOTE));
            var tooltips = new FluidTooltips(new Scene(new VBox(icon)),() -> ThemeOption.ICE_DARK);
            assertSame(icon,tooltips.ownerOf(icon)); tooltips.closeNow();
            return null;
        });
        Platform.runLater(task); task.get(15,TimeUnit.SECONDS);
    }
}
