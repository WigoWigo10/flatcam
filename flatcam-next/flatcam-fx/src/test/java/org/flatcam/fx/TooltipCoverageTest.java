package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class TooltipCoverageTest {
    @Test void sameCaptionsHaveScopedUnitsAndEffects() {
        assertTrue(PanelTooltips.help("Invert Gerber Tool","Margem:").contains("inverter"));
        assertTrue(PanelTooltips.help("Copper Thieving Tool","Margem:").contains("cobre auxiliar"));
        assertTrue(PanelTooltips.help("Calibration Tool","Delta X").contains("Desvio medido"));
        assertTrue(PanelTooltips.help("Calibration Tool","X alvo").contains("não deltas"));
        assertTrue(PanelTooltips.help("QRCode Tool","Máscara:").contains("Não é a máscara de solda"));
        assertNull(PanelTooltips.help("Unknown Tool","Máscara:"));
        assertNull(PanelTooltips.help("Unknown Tool","Método:"));
        assertTrue(PanelTooltips.help("Rules Check Tool","Anel anular minimo").contains("mínimo permitido"));
        assertTrue(PanelTooltips.help("CNC Job Object","Reproduzir").contains("Não envia comandos"));
        assertTrue(PanelTooltips.help("Panelize Tool","Criar painel").contains("mesmos deslocamentos"));
    }
    @Test void formatSingleLineBreaksShortcutsWithoutInterpretingMarkupOrDecimals() {
        String text = "Cut Z = -0.125.\nUnidades: mm ou in.\nAtalhos: Ctrl+Z / Ctrl+Y.\nAtenção: confira <Z> e **origem**.";
        TooltipContent content = TooltipContent.describe(text);
        assertEquals(text,content.plainText());
        for(String prefix : new String[]{"Unidades:","Atalhos:","Atenção:"})
            assertTrue(content.spans().stream().anyMatch(s -> s.text().equals(prefix) && s.style()!=TooltipContent.Style.NORMAL));
        assertTrue(content.plainText().contains("<Z> e **origem**"));
    }
    @Test @EnabledOnOs(OS.WINDOWS) void disabledCommandsAndNativeCellRefreshKeepTruthfulAccessibleHelp() throws Exception {
        try {Platform.startup(()->{});} catch(IllegalStateException started){}
        FutureTask<Void> task=new FutureTask<>(()->{
            MenuItem planned = new MenuItem("Limpar Plot"); planned.getStyleClass().add("planned-command"); planned.setDisable(true);
            CommandHelpCatalog.apply(planned);
            assertTrue(planned.getProperties().get(FluidTooltips.TEXT_KEY).toString().contains("não executa"));
            MenuItem authored = new MenuItem("Copiar"); ToolDescriptions.apply(authored.getProperties(),"Código","Copia o texto selecionado.");
            CommandHelpCatalog.apply(authored);
            assertEquals("Copia o texto selecionado.",authored.getProperties().get(FluidTooltips.TEXT_KEY));
            Tooltip tooltip=new Tooltip();
            TooltipContent.describe("Objeto A\nUnidades: mm.").installNative(tooltip,ThemeOption.CLASSIC_DARK);
            TooltipContent.describe("Objeto B\nUnidades: in.").installNative(tooltip,ThemeOption.CLASSIC_LIGHT);
            assertEquals("Objeto B\nUnidades: in.",tooltip.getText());
            assertEquals(tooltip.getText(),((TextFlow)tooltip.getGraphic()).getAccessibleText());
            assertTrue(tooltip.isWrapText()); assertTrue(tooltip.getStyle().contains("#ffffff"));
            try (CodeEditor code = new CodeEditor("G21\n",CodeSyntax.Language.MACHINE,true)) {
                var copy = code.area().getContextMenu().getItems().stream().filter(item -> item.getText()!=null && item.getText().equals("Copiar")).findFirst().orElseThrow();
                CommandHelpCatalog.apply(copy);
                assertTrue(copy.getProperties().get(FluidTooltips.TEXT_KEY).toString().contains("Não copia objetos"));
            }
            return null;
        }); Platform.runLater(task);task.get(15,TimeUnit.SECONDS);
    }
}
