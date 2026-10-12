package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class TooltipTimingTest {
    @Test void initialAndNeighbourDelaysAreNonzeroAndWarmModeExpires() {
        assertEquals(Duration.seconds(1),TooltipTiming.delay(false,Long.MAX_VALUE));
        assertEquals(Duration.millis(300),TooltipTiming.delay(true,Long.MAX_VALUE));
        assertEquals(Duration.millis(300),TooltipTiming.delay(false,100_000_000));
        assertEquals(Duration.seconds(1),TooltipTiming.delay(false,700_000_000));
        assertEquals(Duration.seconds(1),TooltipTiming.delay(false,-1));
    }
    @Test @EnabledOnOs(OS.WINDOWS) void controlsAndCellsShareTheInitialPolicyWithoutOpeningAWindow() throws Exception {
        TerminalPanelTest.fx(() -> {
            Button first = new Button("A"), second = new Button("B");
            ToolDescriptions.apply(first,"A","Primeira descrição.");
            ToolDescriptions.apply(second,"B","Segunda descrição.");
            VBox root = new VBox(first,second);
            var handler = new FluidTooltips(new Scene(root),()->ThemeOption.CLASSIC_LIGHT);
            try {
                FluidTooltips.simulateEnter(first);
                assertEquals(TooltipTiming.INITIAL,handler.pendingDelay());
                var open = FluidTooltips.class.getDeclaredField("open"); open.setAccessible(true); open.setBoolean(handler,true);
                FluidTooltips.simulateEnter(second);
                assertEquals(TooltipTiming.RESHOW,handler.pendingDelay(),"open tooltips must not glide instantly");
                handler.closeNow();
                FluidTooltips.simulateEnter(first);
                assertEquals(TooltipTiming.INITIAL,handler.pendingDelay(),"dismissal cancels warm browsing");
                TableCell<String,String> cell = new TableCell<>(); Tooltip tooltip = new Tooltip("Objeto A");
                tooltip.setShowDelay(Duration.ZERO); tooltip.setShowDuration(Duration.seconds(30)); cell.setTooltip(tooltip);
                VBox parent = new VBox(cell); ToolDescriptions.apply(parent,"Tabela","Ajuda geral da tabela."); root.getChildren().add(parent);
                assertNull(handler.ownerOf(cell),"native cell tip wins over ancestor help");
                assertSame(tooltip,cell.getTooltip()); assertEquals(TooltipTiming.INITIAL,tooltip.getShowDelay());
                assertEquals(Duration.seconds(30),tooltip.getShowDuration());
                tooltip.setText("Objeto B"); assertNull(handler.ownerOf(cell)); assertEquals("Objeto B",cell.getTooltip().getText());
                TooltipContent.describe("Nome completo do objeto").installNative(tooltip,ThemeOption.ICE_DARK);
                assertEquals(TooltipTiming.INITIAL,tooltip.getShowDelay());
            } finally { handler.closeNow(); }
            return null;
        });
    }
}
