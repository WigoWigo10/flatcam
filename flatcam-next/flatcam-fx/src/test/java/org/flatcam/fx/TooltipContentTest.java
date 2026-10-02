package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TooltipContentTest {
    @Test void formattingRetainsOptionLinesAndMakesUnitAndIntegrationNotesDistinct() {
        var field = ToolsDatabaseFields.ALL.stream().filter(f -> f.key().equals("tools_paint_method")).findFirst().orElseThrow();
        TooltipContent content = ToolsDatabaseDescriptions.content(ToolsDatabaseDescriptions.fieldText(field));
        assertTrue(content.plainText().contains("Standard = passos para dentro.\nSeed = expansão"));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Standard") && s.style() == TooltipContent.Style.BOLD));
        assertTrue(content.spans().stream().anyMatch(s -> s.text().equals("Integração FX:") && s.style() == TooltipContent.Style.NOTE));
        var units = ToolsDatabaseDescriptions.content("Unidades: mm ou in; a base não converte unidades.");
        assertEquals(TooltipContent.Style.ACCENT, units.spans().getFirst().style());
        assertEquals("Unidades: mm ou in; a base não converte unidades.", units.plainText());
    }

    @EnabledOnOs(OS.WINDOWS)
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void richHelpWrapsWithReadableColorsAndPlainHelpDoesNotInheritFormatting(ThemeOption theme) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            Label owner = new Label("Cut Z");
            Scene scene = new Scene(new VBox(owner));
            FluidTooltips tooltips = new FluidTooltips(scene, () -> theme);
            String explanation = "Cut Z define a profundidade de corte. Normalmente negativa para entrar no material.\n\n"
                    + "Unidades: mm ou in.\n\nIntegração FX: salvo na base; transferência ainda pendente.";
            ToolsDatabaseDescriptions.apply(owner, "Profundidade", explanation);
            tooltips.fill(owner);
            VBox box = (VBox) tooltips.popup().getContent().getFirst();
            TextFlow rich = (TextFlow) box.getChildren().get(2);
            assertTrue(rich.isVisible());
            assertFalse(box.getChildren().get(1).isManaged());
            assertEquals(ToolsDatabaseDescriptions.content(explanation).plainText(), rich.getAccessibleText());
            Color background = Color.web(theme.tooltipPalette().background());
            for (var node : rich.getChildren()) {
                Text span = (Text) node;
                assertTrue(contrast((Color) span.getFill(), background) >= 4.5, theme + ": " + span.getText());
            }
            box.resize(300, box.prefHeight(300)); box.applyCss(); box.layout();
            assertTrue(rich.getHeight() > 80, "paragraphs must occupy multiple wrapped lines");
            Label plain = new Label();
            ToolDescriptions.apply(plain.getProperties(), "Outra ferramenta", "Descrição simples.");
            tooltips.fill(plain);
            assertFalse(rich.isVisible()); assertFalse(rich.isManaged());
            assertTrue(rich.getChildren().isEmpty());
            Label body = (Label) box.getChildren().get(1);
            assertTrue(body.isVisible()); assertTrue(body.isManaged());
            assertEquals("Descrição simples.", body.getText());
            tooltips.closeNow();
            return null;
        });
        Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }

    private static double channel(double v) { return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    private static double luminance(Color c) { return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue()); }
    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }
}
