package org.flatcam.fx;

import java.util.Map;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;

/** Legacy artwork for already-functional tool/editor actions missing a graphic. */
final class ToolPanelIcons {

    private static final Map<String, String> BUTTONS = Map.ofEntries(
            // Shared tool actions.
            Map.entry("Adicionar", "plus16.png"),
            Map.entry("Adicionar ferramenta", "plus16.png"),
            Map.entry("Remover", "delete32.png"),
            Map.entry("Delete", "delete32.png"),
            Map.entry("Pick from DB", "search_db32.png"),
            Map.entry("Search DB", "search_db32.png"),
            Map.entry("Optimal", "open_excellon32.png"),
            Map.entry("Apply parameters to all tools", "param_all32.png"),
            Map.entry("Aplicar parametros a todas as ferramentas", "param_all32.png"),
            Map.entry("Reset Tool", "reset32.png"),
            Map.entry("Fechar", "cancel_edit16.png"),
            Map.entry("Close", "cancel_edit16.png"),
            Map.entry("Calculate", "calculator24.png"),
            // CAM and area selection.
            Map.entry("Generate CNC Job", "cnc16.png"),
            Map.entry("Gerar CNC Job...", "cnc16.png"),
            Map.entry("Generate Geometry", "geometry32.png"),
            Map.entry("Gerar Geometry", "geometry32.png"),
            Map.entry("Generate Geometry for Drills", "geometry32.png"),
            Map.entry("Generate Geometry for Slots", "geometry32.png"),
            Map.entry("Selecionar area no desenho", "markarea32.png"),
            Map.entry("Desenhar retangulo de excecao", "rectangle32.png"),
            Map.entry("Desenhar poligono de excecao", "polygon32.png"),
            Map.entry("Limpar area desenhada", "clear_plot32.png"),
            Map.entry("Adicionar gap por retangulo", "rectangle32.png"),
            Map.entry("Adicionar gap por poligono", "polygon32.png"),
            Map.entry("Limpar gaps manuais", "clear_plot32.png"),
            Map.entry("Gerar (Free-form)", "cut32_bis.png"),
            Map.entry("Gerar (Rectangular)", "cut32_bis.png"),
            // Transform panel and secondary editor actions.
            Map.entry("Rotate", "rotate.png"),
            Map.entry("Skew X", "skewX.png"),
            Map.entry("Skew Y", "skewY.png"),
            Map.entry("Scale X", "scale32.png"),
            Map.entry("Scale Y", "scale32.png"),
            Map.entry("Flip on X", "flipx.png"),
            Map.entry("Flip on Y", "flipy.png"),
            Map.entry("Offset X", "offsetx32.png"),
            Map.entry("Offset Y", "offsety32.png"),
            Map.entry("Girar", "rotate.png"),
            Map.entry("Escalar", "scale32.png"),
            Map.entry("Espelhar horizontal", "flipx.png"),
            Map.entry("Espelhar vertical", "flipy.png"),
            Map.entry("Transformacoes", "transform.png"),
            // Gerber/Excellon/Geometry/G-code editors.
            Map.entry("Desfazer", "left_arrow32.png"),
            Map.entry("Refazer", "right_arrow32.png"),
            Map.entry("Mover", "move32.png"),
            Map.entry("Copiar", "copy32.png"),
            Map.entry("Mover X/Y", "move32.png"),
            Map.entry("Copiar X/Y", "copy32.png"),
            Map.entry("Excluir selecionadas", "trash32.png"),
            Map.entry("Cancelar posicionamento", "cancel_edit16.png"),
            Map.entry("Cancelar edicao", "cancel_edit16.png"),
            Map.entry("Aplicar", "apply32.png"),
            Map.entry("Cancelar", "cancel_edit16.png"),
            Map.entry("Adicionar abertura C", "aperture32.png"),
            Map.entry("Alterar D-code", "edit16.png"),
            Map.entry("Alterar dimensoes", "resize16.png"),
            Map.entry("Excluir abertura e formas", "trash32.png"),
            Map.entry("Escalar selecionadas", "scale32.png"),
            Map.entry("Buffer selecionadas", "buffer16-2.png"),
            Map.entry("Poligonizar selecionadas", "poligonize32.png"),
            Map.entry("Criar array de pads", "padarray32.png"),
            Map.entry("Criar disco", "disc32.png"),
            Map.entry("Criar semidisco", "semidisc32.png"),
            Map.entry("Selecionar por area", "markarea32.png"),
            Map.entry("Apagar com selecionadas X/Y", "eraser26.png"),
            Map.entry("Transformar selecionadas", "transform.png"),
            Map.entry("Salvar e sair do editor", "close_edit_file32.png"),
            Map.entry("Descartar alteracoes", "cancel_edit16.png"),
            Map.entry("Aplicar e sair", "close_edit_file32.png"),
            Map.entry("Redimensionar selecionados", "resize16.png"),
            Map.entry("Array de furos selecionados", "addarray16.png"),
            Map.entry("Array de slots selecionados", "slot_array26.png"),
            Map.entry("Aplicar ao CNC Job", "close_edit_file32.png"),
            Map.entry("Salvar arquivo...", "save_as.png"));

    private static final Map<String, String> SECTIONS = Map.of(
            "Opcoes avancadas", "settings18.png",
            "Transformacoes das selecionadas", "transform.png");

    private ToolPanelIcons() {
    }

    static String buttonIcon(String label) {
        return BUTTONS.get(label);
    }

    static void decorate(Node root, Function<String, Node> icon) {
        if (root instanceof Button button && button.getGraphic() == null) {
            String name = buttonIcon(button.getText());
            if (name != null) {
                button.setGraphic(icon.apply(name));
                button.setMinWidth(0);
                button.setTextOverrun(OverrunStyle.ELLIPSIS);
                if (button.getTooltip() == null) button.setTooltip(new Tooltip(button.getText()));
            }
        }
        if (root instanceof TitledPane pane) {
            String name = SECTIONS.get(pane.getText());
            if (name != null && pane.getGraphic() == null) pane.setGraphic(icon.apply(name));
            if (pane.getContent() != null) decorate(pane.getContent(), icon);
        }
        if (root instanceof ScrollPane pane && pane.getContent() != null) {
            decorate(pane.getContent(), icon);
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) decorate(child, icon);
        }
    }

    static java.util.Collection<String> iconFiles() {
        return java.util.stream.Stream.concat(BUTTONS.values().stream(), SECTIONS.values().stream())
                .distinct().toList();
    }
}
