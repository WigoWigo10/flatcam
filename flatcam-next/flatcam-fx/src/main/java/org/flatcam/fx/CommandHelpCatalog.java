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
            text = "Esta função ainda não está implementada no FX. A opção aparece para indicar o fluxo previsto, mas não executa uma operação.";
        if (text != null) ToolDescriptions.apply(item.getProperties(), item.getText(), text);
    }

    static String help(String label) {
        if (label == null) return null;
        String name = label.replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("[.…]+$", "").trim();
        return switch (name) {
            case "Abrir Gerber", "Gerber" -> "Importa ou exporta desenhos Gerber. Confira as unidades, o formato e a camada escolhida na janela correspondente.";
            case "Abrir Excellon", "Excellon" -> "Importa ou exporta furos e slots Excellon. Confira unidades, dígitos, zeros e o formato de slots na janela correspondente.";
            case "Abrir Projeto", "Projeto" -> "Abre um projeto salvo. Projetos Python podem conter opções ainda não restauradas pelo FX; confira os avisos no terminal.";
            case "G-Code" -> "Importa ou exporta um programa CNC. A interpretação e a prévia cobrem apenas os comandos suportados; não certificam segurança física.";
            case "Tools Database" -> "Abre a biblioteca de ferramentas. Aplicar parâmetros modifica a base em memória; Save DB grava no disco. A ajuda dos campos informa os limites de transferência CAM.";
            case "Sobre" -> "Mostra versão, créditos, licença e diagnóstico do sistema, incluindo o renderizador gráfico utilizado.";
            case "Preferencias" -> "Abre os ajustes de tema e visualização do plot. Não modifica as geometrias do projeto.";
            case "Mostrar/ocultar painel lateral" -> "Recolhe ou expande Projeto, Propriedades e Ferramenta, preservando a largura ajustada pelo usuário.";
            case "Linha de Comando", "Linha de Comando Tcl" -> "Abre o terminal do FX. Digite help para listar comandos; usa um dialeto Tcl reduzido, não o Tcl completo do Python.";
            case "Snap na grade" -> "Ativa ou desativa o ajuste de coordenadas à grade. A grade pode ficar visualmente oculta com o snap ativo.";
            case "Eixos" -> "Mostra ou oculta os eixos da origem do plot, sem mover os objetos.";
            case "HUD" -> "Mostra ou oculta as coordenadas absolutas e os deltas do cursor no plot.";
            case "Workspace" -> "Mostra ou oculta a referência de folha A4; não limita o tamanho do trabalho.";
            case "Aproximar", "Afastar" -> "Aumenta ou diminui o zoom. Não muda as dimensões dos objetos.";
            case "Enquadrar Objeto" -> "Centraliza e ajusta o zoom para os limites do objeto selecionado.";
            case "Replotar" -> "Atualiza o desenho dos objetos visíveis com seus dados e opções atuais.";
            case "Limpar Plot" -> "Oculta o desenho no plot sem apagar os objetos do projeto.";
            case "Copiar Selecionados" -> "Cria cópias dos objetos selecionados no projeto, preservando os originais.";
            case "Excluir Selecionados" -> "Remove os objetos selecionados do projeto; não apaga os arquivos de origem no disco.";
            case "Capturar estado agora" -> "Grava um diagnóstico do estado atual para investigar travamentos. O relatório pode conter caminhos e detalhes do ambiente; confira antes de compartilhar.";
            case "Abrir pasta desta sessao" -> "Abre a pasta de diagnóstico da sessão atual para consultar logs e capturas de estado.";
            case "Salvar Projeto" -> "Salva os objetos e as configurações suportadas em .fcnproj. Um projeto Python importado não é sobrescrito como .FlatPrj.\n\nAtenção: confira o formato e o destino exibidos na janela de salvamento.";
            case "Copiar" -> "Cria cópias dos objetos selecionados no projeto. Não usa a área de transferência de texto.";
            case "Excluir", "Remover" -> "Remove os objetos selecionados do projeto. Não apaga seus arquivos de origem no disco.";
            case "Renomear" -> "Altera o nome do objeto no projeto, sem renomear o arquivo original.\n\nAtalho: F2 também inicia a renomeação na árvore.";
            case "Propriedades" -> "Abre o painel correspondente ao objeto selecionado, com parâmetros e opções de visualização.";
            case "Exibir no Plot Area" -> "Ativa a visualização deste objeto e enquadra seus limites no plot.";
            case "Ativar Plot" -> "Torna os objetos visíveis no plot, sem alterar seus dados CAM.";
            case "Desativar Plot" -> "Oculta os objetos do plot, mantendo os dados e as configurações no projeto.";
            case "Enquadrar tudo" -> "Ajusta o zoom para mostrar os objetos visíveis. Não move nem escala a geometria.";
            case "Limpar selecao" -> "Desmarca os objetos selecionados no plot; não os oculta nem os remove.";
            case "Mover no Plot Area", "Copiar no Plot Area" -> "Usa o ponto onde o menu foi aberto como origem. Clique no destino para mover ou copiar os objetos selecionados.\n\nAtalhos: Esc cancela; Ctrl+Z desfaz; Ctrl+Y refaz.";
            case "Editar Objeto", "Editar", "Editar Excellon", "Editar Geometry" -> "Abre o editor do objeto selecionado. As alterações ficam em rascunho até serem aplicadas ao objeto.";
            case "Salvar e Fechar Editor" -> "Aplica o rascunho ao objeto em memória e fecha o editor. Salve o projeto separadamente para persistir no disco.";
            case "Editor de G-Code", "Editar G-code" -> "Abre o programa como rascunho editável, com busca e realce de sintaxe.\n\nAtenção: a prévia não certifica a segurança do programa para uma máquina CNC.";
            case "Ver G-code", "Ver Fonte", "Ver WKT" -> "Abre o código ou a representação do objeto em uma área de leitura com busca. Não modifica o arquivo de origem.";
            case "Salvar como", "Salvar WKT como" -> "Exporta o objeto selecionado para um arquivo. Confira o formato, as unidades e o destino antes de salvar.";
            case "Opacidade" -> "Ajusta a transparência do objeto no plot. 0% é transparente; 100% é opaco. Não muda os dados CAM.";
            case "Personalizada" -> "Abre o seletor para definir uma cor de visualização; não muda os dados do objeto.";
            case "Padrao" -> "Restaura a cor padrão da categoria do objeto no plot.";
            case "Sair" -> "Fecha o aplicativo. Confira as alterações de projeto e de edição ainda não salvas.";
            default -> null;
        };
    }
}
