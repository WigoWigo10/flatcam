package org.flatcam.fx;

import javafx.scene.Node;
import javafx.scene.control.MenuItem;

/** One explanation for the Geometry toolbar and its corresponding menu command. */
final class GeometryEditorDescriptions {
    private GeometryEditorDescriptions() { }

    static ToolDescriptions.Description of(String id) {
        return switch (id) {
            case "paint" -> new ToolDescriptions.Description("Paint Shape",
                    "Acrescenta caminhos de preenchimento nas areas selecionadas, sem apagar os contornos originais. Aceita poligonos e aneis fechados simples; linhas abertas sao recusadas.\n\nCalcule a previa em segundo plano e confirme por clique no plot ou Confirmar Paint; Esc cancela. Ctrl+Z desfaz todo o preenchimento. Diametro deve coincidir com a ferramenta associada; sem associacao, confira-o ao gerar CNC.");
            case "text" -> new ToolDescriptions.Description("Texto vetorial",
                    "Converte texto em polígonos editáveis, com os vazios das letras preservados. Escolha fonte, tamanho e estilo no painel.\n\nGerar e posicionar mostra uma prévia: clique no plot para inserir pela linha de base da primeira linha; Esc/botão direito cancela. Ctrl+Z desfaz a inserção inteira. O resultado usa a ferramenta escolhida para novas formas.");
            case "eraser" -> new ToolDescriptions.Description("Borracha por molde",
                    "Selecione uma ou mais formas como molde; clique na origem e depois no destino para apagar nessa região, sem mover as formas originais.\n\nAtenção: anéis fechados e exteriores de polígonos viram áreas preenchidas, inclusive seus vazios. O recorte atinge todas as ferramentas e também o molde original se estiver dentro da região apagada.\n\nEsc/botão direito cancela antes de confirmar. Durante o cálculo, use Cancelar na barra de status. Ctrl+Z desfaz; para repetir, acione Borracha novamente.");
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
