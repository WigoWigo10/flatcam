package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class PanelTooltipsTest {
    @Test void updatedCutoutAndGeometryHelpDoesNotReuseWrongUnitsOrSigns() {
        String thin = PanelTooltips.help("Cutout Tool", "Tipo de gap:");
        assertTrue(thin.contains("Thin Depth")); assertTrue(thin.contains("CNC Job separadamente"));
        assertTrue(PanelTooltips.help("Cutout Tool", "Cut Z:").contains("Z negativo"));
        assertFalse(PanelTooltips.help("Cutout Tool", "Cut Z:").contains("magnitude positiva"));
        assertTrue(PanelTooltips.help("Cutout Tool", "Adicionar gap por retangulo").contains("substitui o padrão automático"));
        assertTrue(PanelTooltips.help("Paint Tool", "Margem:").contains("negativa expande"));
        assertTrue(PanelTooltips.help("Editor Geometry", "Buffer").contains("não aceita distância negativa"));
        assertTrue(PanelTooltips.help("Editor Gerber", "Buffer").contains("negativos contraem"));
        assertNull(PanelTooltips.help("Unknown Tool", "Fonte:"));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void authoredRichHelpIsCopiedToDisabledFieldCaptionAndSurvivesLaterRowUpdates() throws Exception {
        fx(() -> {
            Label label = new Label("Spindle RPM:"); TextField input = new TextField("0");
            GridPane row = new GridPane(); row.addRow(0,label,input);
            PanelTooltips.install(row,"Geometry CNC Job");
            String authored = "Unidades: valor particular deste perfil.\n\nAtenção: ajuda escrita para este controle.";
            ToolDescriptions.apply(input,"Perfil específico",authored); input.setDisable(true);
            label.setText("Parâmetro específico:");
            assertEquals(authored,input.getAccessibleHelp()); assertEquals(authored,label.getAccessibleHelp());
            assertEquals(TooltipContent.describe(authored),input.getProperties().get(FluidTooltips.CONTENT_KEY));
            assertEquals(TooltipContent.describe(authored),label.getProperties().get(FluidTooltips.CONTENT_KEY));
            assertNull(input.getTooltip()); assertSame(input,label.getLabelFor());
            row.getChildren().add(new Button("Excluir")); label.setText("Spindle RPM:");
            assertEquals(authored,input.getAccessibleHelp()); assertEquals(authored,label.getAccessibleHelp());
        });
    }
    @Test void sameLabelUsesOperationContextInsteadOfDatabaseTransferWarnings() {
        String ncc = PanelTooltips.help("NCC Tool", "Margin (comum):");
        String paint = PanelTooltips.help("Paint Tool", "Margem:");
        String cutout = PanelTooltips.help("Cutout Tool", "Margin:");
        assertTrue(ncc.contains("caixa")); assertTrue(ncc.contains("Não é Offset"));
        assertTrue(paint.contains("preenchimento")); assertTrue(paint.contains("contorno"));
        assertTrue(cutout.contains("placa")); assertTrue(cutout.contains("corte"));
        for (String text : new String[]{ncc, paint, cutout}) assertFalse(text.contains("transferência"));
        assertNull(PanelTooltips.help("Unknown Tool", "Margem:"));
        assertNull(PanelTooltips.help("Unknown Tool", "Método:"));
    }

    @Test void nonObviousUnitsAndExportConventionsAreExplained() {
        assertTrue(PanelTooltips.help("Exportar Gerber", "Zeros:").contains("omite"));
        assertTrue(PanelTooltips.help("Exportar Excellon", "Zeros:").contains("mantém"));
        assertTrue(PanelTooltips.help("QRCode Tool", "Tamanho da caixa:").contains("décimos"));
        assertTrue(PanelTooltips.help("Copper Thieving Tool", "Área mínima:").contains("mm²"));
        assertTrue(PanelTooltips.help("Calculators", "Board Length:").contains("centímetros"));
        assertTrue(PanelTooltips.help("Cutout Tool", "Tipo de gap:").contains("Geometry adicional"));
        assertTrue(PanelTooltips.help("Geometry CNC Job", "Probe Z final (< 0):").contains("Atenção:"));
    }

    @Test void allToolMenuDescriptionsShareRichFormatting() {
        for (String id : new String[]{"double_sided", "align", "extract_drills", "cutout", "ncc", "paint", "isolation",
                "drilling", "panelize", "film", "solderpaste", "subtract", "rules", "optimal", "calculators",
                "transform", "qrcode", "copper_thieving", "fiducials", "calibration", "punch", "invert", "corners", "etch"}) {
            var properties = new HashMap<Object, Object>();
            ToolDescriptions.apply(properties, id);
            assertInstanceOf(TooltipContent.class, properties.get(FluidTooltips.CONTENT_KEY), id);
            assertEquals(TooltipContent.describe(ToolDescriptions.of(id).text()), properties.get(FluidTooltips.CONTENT_KEY));
        }
        assertTrue(PanelTooltips.help("NCC Tool", "Delete").contains("sem excluir objetos"));
        assertTrue(PanelTooltips.help("Geometry CNC Job", "Fechar").contains("permanecem"));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void formRowsHaveRichAccessibleHelpEvenWhenInputIsDisabledAndRepeatedInstallIsSafe() throws Exception {
        fx(() -> {
            Label label = new Label("Overlap (%):");
            TextField input = new TextField("40"); input.setDisable(true);
            GridPane grid = new GridPane(); grid.addRow(0, label, input);
            Button delete = new Button("Delete");
            VBox panel = new VBox(grid, delete);
            PanelTooltips.install(panel, "NCC Tool"); PanelTooltips.install(panel, "NCC Tool");
            assertInstanceOf(TooltipContent.class, input.getProperties().get(FluidTooltips.CONTENT_KEY));
            assertEquals(label.getAccessibleHelp(), input.getAccessibleHelp());
            assertSame(input, label.getLabelFor()); assertTrue(input.isDisabled());
            assertTrue(delete.getProperties().containsKey(FluidTooltips.TEXT_KEY));
            assertNull(delete.getTooltip());
        });
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void updatesNewRowsAndProfileLabelsWithoutStaleUnitsOrDuplicateBindings() throws Exception {
        fx(() -> {
            VBox panel = new VBox(); PanelTooltips.install(panel, "Geometry CNC Job");
            Label power = new Label("Spindle RPM:"); TextField input = new TextField("10000");
            HBox row = new HBox(power, input); panel.getChildren().add(row);
            assertTrue(input.getAccessibleHelp().contains("Rotação"));
            power.setText("Potencia S/PWM:");
            assertTrue(input.getAccessibleHelp().contains("não é necessariamente RPM"));
            power.setText("Sem parâmetro conhecido:");
            assertNull(input.getAccessibleHelp()); assertFalse(input.getProperties().containsKey(FluidTooltips.CONTENT_KEY));
            power.setText("Spindle RPM:"); assertTrue(input.getAccessibleHelp().contains("Rotação"));
            GridPane multi = new GridPane(); CheckBox depth = new CheckBox("Multi-Depth");
            TextField step = new TextField("0.1"); multi.addRow(0, depth, step); panel.getChildren().add(multi);
            assertTrue(step.getAccessibleHelp().contains("valor positivo"));
            assertTrue(step.getAccessibleHelp().contains("Unidades:"));
            assertTrue(depth.getAccessibleHelp().contains("profundidade total"));
        });
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void replacingAFieldDoesNotLeaveTheCaptionBoundToTheOldInput() throws Exception {
        fx(() -> {
            Label power = new Label("Spindle RPM:"); TextField oldField = new TextField();
            HBox row = new HBox(power, oldField); PanelTooltips.install(row, "Geometry CNC Job");
            TextField replacement = new TextField(); row.getChildren().set(1, replacement);
            assertNull(oldField.getAccessibleHelp());
            power.setText("Potencia S/PWM:");
            assertTrue(replacement.getAccessibleHelp().contains("S/PWM"));
            assertNull(oldField.getAccessibleHelp());
        });
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void authoredTipsSurviveAndCaptionsRetainHelpForDisabledNativeControls() throws Exception {
        fx(() -> {
            Label label = new Label("Feed rapids:"); TextField input = new TextField("0");
            String authored = "0 = automático; limites dependem do perfil. Atenção: revise os rápidos.";
            input.setTooltip(new Tooltip(authored)); input.setDisable(true);
            GridPane grid = new GridPane(); grid.addRow(0, label, input);
            PanelTooltips.install(grid, "Geometry CNC Job");
            assertEquals(authored, label.getAccessibleHelp());
            assertEquals(authored, input.getTooltip().getText());
            FluidTooltips handler = new FluidTooltips(new Scene(new VBox(grid)), () -> ThemeOption.ICE_DARK);
            assertSame(input, handler.ownerOf(input)); assertNull(input.getTooltip());
            assertEquals(authored, input.getAccessibleHelp());
            assertInstanceOf(TooltipContent.class, input.getProperties().get(FluidTooltips.CONTENT_KEY));
            handler.closeNow();
        });
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void nativeHelpIsFormattedButIconIdentificationAndVirtualizedCellsArePreserved() throws Exception {
        fx(() -> {
            Button obvious = new Button("Excluir"); obvious.setTooltip(new Tooltip("Excluir"));
            Button icon = new Button(); icon.setTooltip(new Tooltip("Salvar projeto"));
            TableCell<String, String> cell = new TableCell<>(); Tooltip nativeCell = new Tooltip("Objeto A");
            cell.setTooltip(nativeCell);
            FluidTooltips handler = new FluidTooltips(new Scene(new VBox(obvious, icon, cell)), () -> ThemeOption.CLASSIC_LIGHT);
            assertNull(handler.ownerOf(obvious)); assertNull(obvious.getTooltip());
            assertSame(icon, handler.ownerOf(icon)); assertInstanceOf(TooltipContent.class, icon.getProperties().get(FluidTooltips.CONTENT_KEY));
            assertNull(handler.ownerOf(cell)); assertSame(nativeCell, cell.getTooltip());
            nativeCell.setText("Objeto B"); assertFalse(cell.getProperties().containsKey(FluidTooltips.TEXT_KEY));
            handler.closeNow();
        });
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void dialogScenesReceiveTheSameFormattedHelpAndPreserveEventHandlers() throws Exception {
        fx(() -> {
            Dialog<Void> dialog = new Dialog<>();
            Label label = new Label("Digitos decimais:"); Spinner<Integer> digits = new Spinner<>(1, 6, 4);
            GridPane grid = new GridPane(); grid.addRow(0, label, digits); dialog.getDialogPane().setContent(grid);
            boolean[] events = new boolean[2];
            dialog.setOnShown(event -> events[0] = true); dialog.setOnHidden(event -> events[1] = true);
            FluidTooltips.install(dialog, "Exportar Gerber");
            dialog.getOnShown().handle(new DialogEvent(dialog, DialogEvent.DIALOG_SHOWN));
            assertTrue(events[0]); assertTrue(digits.getAccessibleHelp().contains("precisão física"));
            assertInstanceOf(TooltipContent.class, label.getProperties().get(FluidTooltips.CONTENT_KEY));
            dialog.getOnHidden().handle(new DialogEvent(dialog, DialogEvent.DIALOG_HIDDEN));
            assertTrue(events[1]); dialog.close();
        });
    }

    private static void fx(Runnable action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
}
