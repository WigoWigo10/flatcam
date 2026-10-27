package org.flatcam.fx;

import javafx.scene.Node;
import javafx.scene.control.MenuItem;

/** One explanation for the Geometry toolbar and its corresponding menu command. */
final class GeometryEditorDescriptions {
    private GeometryEditorDescriptions() { }

    static ToolDescriptions.Description of(String id) {
        return switch (id) {
            case "transform" -> new ToolDescriptions.Description("Transformações das selecionadas",
                    "Abre o painel de transformações do rascunho. Rotação, escala X/Y, espelhos e inclinação usam o pivô escolhido e uma prévia antes de confirmar. Deslocamento X/Y soma coordenadas nas unidades do objeto, sem depender do pivô.\n\n" + of("rotate").text());
            case "rotate" -> new ToolDescriptions.Description("Rotação com prévia",
                    "Selecione formas, escolha o pivô e informe o ângulo. Positivo gira no sentido horário, como no painel Python; negativo gira no anti-horário. Origem, centro/cantos da seleção e ponto X/Y estão disponíveis. Escolher pivô no plot respeita o Snap.\n\nPré-visualizar transformação calcula o novo contorno sem mudar o rascunho. Confirme por botão ou clique no plot; Esc/botão direito descarta. Alterar parâmetros ou seleção invalida a prévia. Durante o cálculo, Esc descarta o resultado pendente e Cancelar na barra de status interrompe o trabalho quando possível.\n\nMantém IDs, ferramentas e seleção. Ctrl+Z desfaz toda a rotação; Ctrl+Y refaz. Salvar e sair aplica o rascunho ao objeto. Escala, espelhos e inclinação também usam o pivô escolhido e o mesmo fluxo de prévia/confirmar/cancelar.");
            case "arc" -> new ToolDescriptions.Description("Arco por três cliques",
                    "C12: centro, início e direção do fim; o raio vem do início. 12C: início, fim e referência do centro; o centro fica na mediatriz, usando a distância da referência ao meio da corda, como no Python. 132: início, fim e um ponto no arco; o sentido é automático.\n\nM alterna o modo e limpa os pontos anteriores; D alterna horário/anti-horário nos modos com centro. J abre coordenadas absolutas/relativas: Posicionar cursor só atualiza a prévia; Adicionar ponto usa X/Y exatos, sem Snap. Backspace remove o último ponto. O terceiro ponto válido conclui; Esc/botão direito cancela sem inserir. O Snap vale para cliques do mouse.\n\nA prévia mostra o arco e uma cruz no centro. Resolução configurável em Arcos e curvas: 4–4096 segmentos por círculo; padrão 64. Salvar resolução padrão vale para novos editores e não altera formas existentes. Milling Type Climb inverte os vértices finais como no Python. Ctrl+Z desfaz o arco inteiro; Ctrl+Y refaz.");
            case "paint" -> new ToolDescriptions.Description("Paint Shape",
                    "Acrescenta caminhos de preenchimento nas areas selecionadas, sem apagar os contornos originais. Aceita poligonos e aneis fechados simples; linhas abertas sao recusadas.\n\nCalcule a previa em segundo plano e confirme por clique no plot ou Confirmar Paint; Esc cancela. Ctrl+Z desfaz todo o preenchimento. Diametro deve coincidir com a ferramenta associada; sem associacao, confira-o ao gerar CNC.");
            case "text" -> new ToolDescriptions.Description("Texto vetorial",
                    "Converte texto em polígonos editáveis, com os vazios das letras preservados. Escolha fonte, tamanho e estilo no painel.\n\nGerar e posicionar mostra uma prévia: clique no plot para inserir pela linha de base da primeira linha; Esc/botão direito cancela. Ctrl+Z desfaz a inserção inteira. O resultado usa a ferramenta escolhida para novas formas.");
            case "eraser" -> new ToolDescriptions.Description("Borracha por molde",
                    "Selecione uma ou mais formas como molde; clique uma vez na origem e depois nos destinos para apagar nessas regiões. O molde e a origem são mantidos entre operações; a prévia mostra a próxima área.\n\nAtenção: anéis fechados e exteriores de polígonos viram áreas preenchidas, inclusive seus vazios. O recorte atinge todas as ferramentas e também o molde original se estiver dentro da região apagada. O molde da borracha continua com a forma inicial, mesmo se sua origem for apagada.\n\nEsc/botão direito ou clicar novamente em Borracha encerra, mantendo as operações confirmadas. Durante o cálculo, Esc descarta o resultado pendente; Cancelar na barra de status interrompe o trabalho quando possível. Ctrl+Z desfaz uma operação; Ctrl+Y refaz, mantendo a borracha ativa. Trocar ferramenta ou seleção encerra o gesto.");
            case "buffer" -> new ToolDescriptions.Description("Buffer arredondado",
                    "Cria novas áreas, sem substituir as selecionadas. Informe uma distância positiva e escolha o modo.\nCompleto = faixa ao redor da linha ou do exterior do polígono.\nInterior = contrai o polígono.\nExterior = expande o polígono.\n\nInterior/Exterior exigem polígonos; o FX não aceita distância negativa neste comando. Unidades do objeto (mm ou in). Ctrl+Z desfaz.");
            case "cut_path" -> new ToolDescriptions.Description("Cortar Caminho",
                    "A primeira forma selecionada é o alvo; as demais são os cortadores. Um alvo poligonal vira seu contorno antes do recorte.\n\nSubstitui somente o alvo e mantém os cortadores, mesmo que o corte elimine todo o alvo. Ctrl+Z desfaz.");
            case "subtract" -> new ToolDescriptions.Description("Subtrair",
                    "Retira da primeira forma selecionada a região coberta pelas demais. A ordem de seleção define o alvo.\n\nSubstitui as formas selecionadas pelo resultado; todas devem pertencer à mesma ferramenta. Resultado vazio é recusado. Para manter cortadores, use Cortar Caminho. Ctrl+Z desfaz.");
            case "union" -> new ToolDescriptions.Description("Unir selecionadas",
                    "Combina as formas selecionadas e as substitui pelo resultado geométrico. Todas devem pertencer à mesma ferramenta. Não muda o diâmetro de corte. Ctrl+Z desfaz.");
            case "intersection" -> new ToolDescriptions.Description("Interseção",
                    "Mantém apenas a parte comum a todas as formas selecionadas e substitui as selecionadas pelo resultado. Exige formas da mesma ferramenta; resultado vazio é recusado. Ctrl+Z desfaz.");
            case "explode" -> new ToolDescriptions.Description("Explodir polígonos",
                    "Substitui polígonos selecionados por segmentos individuais dos contornos externos e internos. Cada segmento pode ser selecionado e apagado. A operação aceita até 10.000 segmentos e preserva a ferramenta. Ctrl+Z desfaz.");
            case "move", "copy" -> new ToolDescriptions.Description(id.equals("move") ? "Mover selecionadas" : "Copiar selecionadas",
                    "Selecione as formas; clique na origem de referência e depois no destino. A prévia não altera o rascunho antes da confirmação. "
                            + (id.equals("copy") ? "As formas originais são mantidas." : "As formas selecionadas mudam de posição.")
                            + "\n\nA ferramenta associada a cada forma é preservada. Esc/botão direito cancela; Ctrl+Z desfaz.");
            default -> null;
        };
    }

    static void apply(Node node, String id) {
        var description = of(id);
        if (description != null) ToolDescriptions.apply(node, description.title(), description.text());
    }

    static void apply(MenuItem item, String id) {
        var description = of(id);
        if (description != null) ToolDescriptions.apply(item.getProperties(), description.title(), description.text());
    }
}
