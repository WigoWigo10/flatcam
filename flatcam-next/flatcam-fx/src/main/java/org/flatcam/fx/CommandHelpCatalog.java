package org.flatcam.fx;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/** Help for application commands, independent from form fields and code clipboard actions. */
final class CommandHelpCatalog {
    private CommandHelpCatalog() { }

    static void apply(MenuItem item) {
        if (item instanceof SeparatorMenuItem || item instanceof Menu
                || item.getProperties().containsKey(FluidTooltips.TEXT_KEY)) return;
        String text = help(item.getText());
        if (item.getStyleClass().contains("planned-command"))
            text = I18n.text("ui.acc46e003b3b");
        if (text != null) ToolDescriptions.apply(item.getProperties(), item.getText(), text);
    }

    static String help(String label) {
        if (label == null) return null;
        for (String candidate : I18n.sources(label)) {
            String help = lookup(candidate);
            if (help != null) return help;
        }
        return null;
    }

    private static String lookup(String label) {
        String name = label.replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("[.…]+$", "").trim();
        return switch (name) {
            case "Abrir Gerber", "Gerber" -> I18n.text("ui.86c7aee50967");
            case "Abrir Excellon", "Excellon" -> I18n.text("ui.86516a89eb86");
            case "Abrir Projeto", "Projeto" -> I18n.text("ui.d3f231defb2e");
            case "G-Code" -> I18n.text("ui.3a54ac0f5d0e");
            case "Tools Database" -> I18n.text("ui.4fa07a48b193");
            case "Sobre" -> I18n.text("ui.4cbc307e4d2b");
            case "Preferências" -> I18n.text("ui.52b5b3e2164a");
            case "Mostrar/ocultar painel lateral" -> I18n.text("ui.4bbbe7439de5");
            case "Linha de Comando", "Linha de Comando Tcl" -> I18n.text("ui.d61a89354c52");
            case "Snap na grade" -> I18n.text("ui.daa99425b066");
            case "Eixos" -> I18n.text("ui.45d7bb02b57e");
            case "HUD" -> I18n.text("ui.c1d32e05a9c2");
            case "Workspace" -> I18n.text("ui.72217cf7fae1");
            case "Aproximar", "Afastar" -> I18n.text("ui.200285ca38c0");
            case "Enquadrar Objeto" -> I18n.text("ui.a400682427ff");
            case "Replotar" -> I18n.text("ui.c0d1c4bea8a0");
            case "Limpar Plot" -> I18n.text("ui.6adc09bdff68");
            case "Copiar Selecionados" -> I18n.text("ui.03ffa188ce46");
            case "Excluir Selecionados" -> I18n.text("ui.a8f1b00ac1e9");
            case "Capturar estado agora" -> I18n.text("ui.ca1d71650ff5");
            case "Abrir pasta desta sessão" -> I18n.text("ui.45aeb4f93336");
            case "Salvar Projeto" -> I18n.text("ui.708de581aaed");
            case "Copiar" -> I18n.text("ui.3407cc8a2274");
            case "Excluir", "Remover" -> I18n.text("ui.85ffed6c23cb");
            case "Renomear" -> I18n.text("ui.007683382be3");
            case "Propriedades" -> I18n.text("ui.e1c2e0f76b1e");
            case "Exibir no Plot Area" -> I18n.text("ui.8a5abd31984e");
            case "Ativar Plot" -> I18n.text("ui.dc1d51dbeba7");
            case "Desativar Plot" -> I18n.text("ui.69186e6c9e4a");
            case "Enquadrar tudo" -> I18n.text("ui.06f16d6f24f9");
            case "Limpar seleção" -> I18n.text("ui.ab5dd60dd505");
            case "Mover no Plot Area", "Copiar no Plot Area" -> I18n.text("ui.5f4af8410aa0");
            case "Editar Objeto", "Editar", "Editar Excellon", "Editar Geometry" -> I18n.text("ui.4c5ca8535457");
            case "Salvar e Fechar Editor" -> I18n.text("ui.1b1ac5c405aa");
            case "Editor de G-Code", "Editar G-code" -> I18n.text("ui.b60ab1bbe5d7");
            case "Ver G-code", "Ver Fonte", "Ver WKT" -> I18n.text("ui.d2428d9a8abb");
            case "Salvar como", "Salvar WKT como" -> I18n.text("ui.1091d2f422ae");
            case "Opacidade" -> I18n.text("ui.ea404e0f2363");
            case "Personalizada" -> I18n.text("ui.6bbd8fc5813f");
            case "Padrão" -> I18n.text("ui.7f5e1fe45af9");
            case "Sair" -> I18n.text("ui.0512f93be2a0");
            default -> null;
        };
    }
}
