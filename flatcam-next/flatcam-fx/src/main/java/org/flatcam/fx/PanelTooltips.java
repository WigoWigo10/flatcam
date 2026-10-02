package org.flatcam.fx;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

/** Contextual help for actual form rows, not for skin internals or arbitrary label matches. */
final class PanelTooltips {
    private static final String INSTALLED = "fx.tip.panel-installed";
    private static final String OWN_HELP = "fx.tip.panel-help";
    private static final String ROW_VALUE = "fx.tip.row-value";
    private static final String LINEAR = "\n\nUnidades: unidade do objeto (mm ou in).";
    private static final String FEED = "\n\nUnidades: unidades do objeto por minuto (mm/min ou in/min).";

    private PanelTooltips() { }

    static void install(Node root, String context) {
        if (root == null || root.getProperties().putIfAbsent(INSTALLED, context) != null) return;
        if (root instanceof Labeled labeled) {
            update(labeled, context, null);
            labeled.textProperty().addListener((obs, oldValue, text) -> update(labeled, context, null));
        }
        if (root instanceof TitledPane pane) {
            install(pane.getContent(), context);
            pane.contentProperty().addListener((obs, was, content) -> install(content, context));
        } else if (root instanceof ScrollPane pane) {
            install(pane.getContent(), context);
            pane.contentProperty().addListener((obs, was, content) -> install(content, context));
        } else if (root instanceof Pane pane) {
            List.copyOf(pane.getChildren()).forEach(child -> install(child, context));
            rows(pane, context);
            // Some tools add parameter rows after a selection changes. Never traverse virtualized control skins.
            pane.getChildren().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) if (change.wasAdded())
                    List.copyOf(change.getAddedSubList()).forEach(child -> install(child, context));
                rows(pane, context);
            });
        }
    }

    private static void rows(Pane pane, String context) {
        var children = List.copyOf(pane.getChildren());
        for (int i = 0; i < children.size(); i++) {
            Node caption = children.get(i);
            if (!(caption instanceof Label || caption instanceof CheckBox)) continue;
            Labeled label = (Labeled) caption;
            Node value = null;
            if (pane instanceof GridPane) {
                int row = index(GridPane.getRowIndex(label)), col = index(GridPane.getColumnIndex(label));
                value = children.stream().filter(n -> index(GridPane.getRowIndex(n)) == row
                        && index(GridPane.getColumnIndex(n)) == col + 1).findFirst().orElse(null);
            } else if (i + 1 < children.size()) value = children.get(i + 1);
            if (!isValue(value)) {
                label.getProperties().remove(ROW_VALUE);
                continue;
            }
            Node field = value;
            Object previous = label.getProperties().put(ROW_VALUE, field);
            if (previous instanceof Node oldField && oldField != field) clearOwn(oldField);
            update(label, context, field);
            String bindingKey = "fx.tip.row-bound";
            if (label.getProperties().putIfAbsent(bindingKey, Boolean.TRUE) == null)
                label.textProperty().addListener((obs, was, text) ->
                        update(label, context, (Node) label.getProperties().get(ROW_VALUE)));
        }
    }

    private static int index(Integer value) { return value == null ? 0 : value; }

    private static boolean isValue(Node node) {
        return node instanceof Pane || node instanceof Control && !(node instanceof Labeled)
                && !(node instanceof ScrollPane) && !(node instanceof TitledPane);
    }

    private static void update(Labeled label, String context, Node value) {
        // Existing, deliberately authored descriptions take priority over the shared catalog.
        String text = help(context, label.getText());
        if (value instanceof Control control && control.getTooltip() != null)
            text = control.getTooltip().getText();
        else if (value != null && !isOwnHelp(value)
                && value.getProperties().get(FluidTooltips.TEXT_KEY) instanceof String authored)
            text = authored;
        if (text == null || text.isBlank()) {
            clearOwn(label);
            if (value != null) clearOwn(value);
            return;
        }
        applyUnlessAuthored(label, label.getText(), text);
        if (value != null) {
            String valueText = label instanceof CheckBox ? companion(label.getText(), text) : text;
            applyUnlessAuthored(value, label.getText(), valueText);
            if (label instanceof Label caption && value instanceof Control control) caption.setLabelFor(control);
        }
    }

    private static void applyUnlessAuthored(Node node, String title, String text) {
        if (node instanceof Control control && control.getTooltip() != null) {
            // Keep the native description intact until FluidTooltips adopts it on hover.
            node.getProperties().putIfAbsent(FluidTooltips.TITLE_KEY, title == null ? "" : title.replaceFirst(":$", ""));
            if (node.getAccessibleHelp() == null) node.setAccessibleHelp(control.getTooltip().getText());
            return;
        }
        if (node.getProperties().containsKey(FluidTooltips.TEXT_KEY) && !isOwnHelp(node)) return;
        ToolDescriptions.apply(node.getProperties(), title == null ? "" : title.replaceFirst(":$", ""), text);
        node.setAccessibleHelp(text);
        node.getProperties().put(OWN_HELP, text);
    }

    private static void clearOwn(Node node) {
        Object previous = node.getProperties().remove(OWN_HELP);
        if (previous == null || !previous.equals(node.getProperties().get(FluidTooltips.TEXT_KEY))) return;
        node.getProperties().remove(FluidTooltips.TITLE_KEY);
        node.getProperties().remove(FluidTooltips.TEXT_KEY);
        node.getProperties().remove(FluidTooltips.CONTENT_KEY);
        if (previous.equals(node.getAccessibleHelp())) node.setAccessibleHelp(null);
    }

    private static boolean isOwnHelp(Node node) {
        Object previous = node.getProperties().get(OWN_HELP);
        return previous != null && previous.equals(node.getProperties().get(FluidTooltips.TEXT_KEY));
    }

    private static String companion(String label, String fallback) {
        return switch (key(label)) {
            case "multi-depth" -> "Profundidade máxima acrescentada em cada passe, como valor positivo. Disponível quando Multi-Depth está marcado." + LINEAR;
            case "dwell" -> "Tempo de espera após ligar o spindle, em segundos. Disponível quando Dwell está marcado.";
            case "offset", "copper offset" -> "Distância adicional mantida entre a ferramenta e o cobre. Disponível quando Offset está marcado." + LINEAR;
            default -> fallback;
        };
    }

    static String help(String context, String label) {
        String name = key(label);
        String scoped = contextual(context, name);
        if (scoped != null) return scoped;
        return switch (name) {
            case "tool dia", "tool diameter", "tool diameter (mm)", "tool diameter (in)", "diametro da broca" ->
                    "Diâmetro de corte, maior que zero. Define a largura do caminho usinado, não a profundidade de corte." + LINEAR;
            case "spot dia" -> "Diâmetro do ponto do laser usado para calcular a largura dos caminhos." + LINEAR;
            case "passes" -> "Quantidade inteira de passes de Isolation. Mais passes aumentam a largura isolada em conjunto com o diâmetro e Overlap.";
            case "overlap (%)", "sobreposicao (%)" -> "Sobreposição entre caminhos em porcentagem do diâmetro da ferramenta, de 0 a menos de 100%. Valores maiores criam mais trajetos e aumentam o tempo de cálculo e usinagem.";
            case "method", "metodo" -> null; // These labels have different meanings in different tools.
            case "connect", "conectar caminhos" -> "Liga segmentos para reduzir levantamentos da ferramenta, mantendo a ligação dentro da área permitida.";
            case "contour", "contorno" -> "Adiciona um caminho ao redor do perímetro para completar o acabamento das bordas.";
            case "rest", "rest machining" -> "Cada ferramenta menor trabalha apenas onde as maiores não alcançaram. As ferramentas são usadas da maior para a menor.";
            case "tool order", "ordem" -> "No = ordem da tabela.\nForward = diâmetros do menor para o maior.\nReverse = do maior para o menor.\n\nRest Machining usa a ordem do maior para o menor.";
            case "check validity", "verificar validade dos diametros" -> "Verifica se os diâmetros escolhidos cabem nos espaços entre regiões de cobre. A verificação não substitui a inspeção dos caminhos gerados.";
            case "combine" -> "Combina passes e ferramentas em uma Geometry. Desmarque para produzir saídas separadas e inspecionar cada resultado.";
            case "multi-depth" -> "Divide a profundidade total em passes limitados pelo valor ao lado. Na furação, a ferramenta retrai entre os passes.";
            case "cut z", "cut z (nao v)" -> "Profundidade abaixo da superfície. O FX aceita a magnitude positiva ou Z negativo e gera o corte em Z negativo. Para ferramentas V, a Geometry calcula a profundidade pela largura e pela ponta." + LINEAR;
            case "travel z", "z de deslocamento" -> "Altura de deslocamento entre cortes. Deve liberar a peça e suas fixações; confirme a referência Z antes de executar." + LINEAR;
            case "focus/end z" -> "Altura de foco e de término no perfil laser. Não é a profundidade de fresagem." + LINEAR;
            case "feedrate z", "avanco z" -> "Velocidade de mergulho no eixo Z. Informe um valor positivo adequado à ferramenta e ao material." + FEED;
            case "feed rate", "avanco xy" -> "Velocidade de corte no plano X-Y. O avanço de mergulho e os rápidos podem ter ajustes próprios." + FEED;
            case "spindle rpm" -> "Rotação do spindle em RPM. Zero omite comandos de spindle nos perfis que permitem isso; Mach3 com sonda exige rotação positiva.";
            case "potencia s/pwm" -> "Valor inteiro S/PWM usado pelo perfil selecionado; não é necessariamente RPM. O intervalo permitido depende do pós-processador.";
            case "dwell" -> "Ativa uma espera após ligar o spindle para permitir que alcance a rotação configurada. O valor ao lado é o tempo em segundos.";
            case "offset z" -> "Compensação da ponta da broca: positivo aumenta a profundidade; negativo reduz. O corte resultante deve continuar abaixo da superfície." + LINEAR;
            case "tool change", "troca de ferramenta (conforme perfil)" -> "Ativa a sequência de troca de ferramenta compatível com o pós-processador. A troca manual pausa o programa; os perfis com sonda têm uma sequência própria.";
            case "tool change z", "z de troca de ferramenta" -> "Altura para trocar a ferramenta, em relação à referência Z. Deve liberar a peça e permitir a troca com segurança." + LINEAR;
            case "end move z" -> "Altura Z final do programa. O movimento X-Y final, quando informado, acontece nessa altura: confirme que está livre de obstáculos." + LINEAR;
            case "end move x,y", "xy de troca", "troca x,y" -> "Informe None para não deslocar em X-Y, ou duas coordenadas separadas por ponto e vírgula, por exemplo 10;20. Confirme que o percurso está livre." + LINEAR;
            case "probe z final (< 0)" -> "Limite Z negativo para procurar contato com G31.\n\nAtenção: confirme o sensor, a origem e o curso disponível antes de executar." + LINEAR;
            case "probe feed" -> "Avanço positivo usado ao procurar contato com a sonda. Use um valor compatível com o sensor e a máquina." + FEED;
            case "contact z / placa" -> "Coordenada Z conhecida da superfície de contato, incluindo a espessura da placa de sondagem conforme a referência adotada." + LINEAR;
            case "troca x,y (opcional)" -> "Posição para a troca e a sondagem. None mantém X-Y; para deslocar, informe X;Y. Revise o percurso e a macro M6 da máquina." + LINEAR;
            case "v-tip dia", "tip diameter" -> "Diâmetro da ponta da ferramenta V, não da largura de corte na profundidade escolhida." + LINEAR;
            case "v-tip angle (graus)", "tip angle" -> "Ângulo total da ponta V, em graus, maior que 0 e menor que 180. Junto com a ponta e a profundidade, determina a largura de corte.";
            case "search and add" -> "Busca na Tools Database pelo diâmetro e pela operação. Um único resultado compatível transfere os parâmetros suportados; sem correspondência, usa os valores padrão.";
            case "pick from db" -> "Escolhe uma ferramenta explicitamente na Tools Database. Confira o diâmetro, o tipo e os parâmetros transferidos antes de gerar.";
            case "optimal" -> "Calcula em segundo plano a menor distância entre regiões de cobre para sugerir um diâmetro que caiba entre elas. Não define automaticamente avanço ou profundidade.";
            case "apply parameters to all tools", "aplicar parametros a todas as ferramentas" -> "Copia os parâmetros da ferramenta selecionada para todas as ferramentas da tabela. Revise os valores por ferramenta antes de gerar.";
            case "reset tool" -> "Restaura os parâmetros iniciais do painel; não apaga os objetos já gerados no projeto.";
            case "solid" -> "Preenche as formas no desenho; desmarque para visualizar somente seus contornos. Não modifica os dados CAM.";
            case "multi-color" -> "Diferencia aberturas ou ferramentas por cores no desenho. Não altera o objeto nem o G-code.";
            case "display annotation" -> "Mostra os números dos movimentos do CNC Job no desenho para localizar trechos do programa.";
            case "display direction arrows" -> "Mostra o sentido dos movimentos do CNC Job. É apenas uma ajuda visual; não inverte os caminhos.";
            case "boundary margin" -> "Margem acrescentada à caixa ao redor do objeto para gerar a área auxiliar." + LINEAR;
            case "scale" -> "Multiplica todas as coordenadas e dimensões pelo fator informado. 1 mantém o tamanho; 2 dobra o tamanho.";
            case "offset x" -> "Translada o objeto pelo deslocamento X informado; não é compensação do raio da ferramenta." + LINEAR;
            default -> null;
        };
    }

    private static String contextual(String context, String name) {
        if (List.of("NCC Tool", "Paint Tool").contains(context)) {
            switch (name) {
                case "method", "metodo" : return "Método de preenchimento:\nStandard = passos para dentro.\nSeed = expansão de uma semente.\nLines = linhas paralelas.\nCombo = tenta métodos alternativos.";
                case "margin (comum)", "margin", "margem" : return context.equals("NCC Tool")
                        ? "Margem da caixa usada como limite da limpeza. Não é Offset: o afastamento das trilhas é configurado separadamente." + LINEAR
                        : "Margem individual entre o preenchimento e a borda do polígono. Positiva contrai a área; negativa expande.\n\nAtenção: valores negativos podem criar caminhos fora do contorno original." + LINEAR;
                case "offset", "copper offset" : return "Ativa uma distância adicional de proteção ao redor das trilhas e pads durante a limpeza. Habilita o valor de Offset.";
                case "selection" : return "Itself = caixa da origem.\nArea = área desenhada no plot.\nReference = limite dado por outro objeto.\n\nDefine onde a limpeza pode ocorrer, sem trocar o cobre de origem.";
                case "operation" : return "Clear remove o cobre fora do circuito.\nIsolation cria caminhos ao redor das trilhas antes da limpeza.\n\nA operação é configurada por ferramenta.";
                case "milling type" : return "Climb = fresagem concordante.\nConventional = fresagem discordante.\n\nNo NCC, essa escolha se aplica à operação Isolation.";
            }
        }
        if (context.equals("Isolation Tool")) {
            switch (name) {
                case "isolation type" : return "Both = bordas externas e internas.\nExterior = apenas externas.\nInterior = apenas bordas das aberturas internas.";
                case "excluir area" : return "Retira dos caminhos de isolamento a área preenchida da Geometry selecionada. Escolha Nenhuma para não usar um objeto de exceção.";
                case "follow" : return "Segue o centro das trilhas em vez de isolar suas bordas; usa somente uma ferramenta.\n\nAtenção: esse modo corta sobre a trilha.";
                case "desenhar retangulo de excecao", "desenhar poligono de excecao" : return "Desenhe no plot a região onde os caminhos de isolamento serão suprimidos. A área desenhada é uma exceção adicional à Geometry escolhida.";
            }
        }
        if (context.equals("Cutout Tool")) {
            return switch (name) {
                case "kind" -> "Single processa uma placa.\nPanel processa os componentes da origem como placas separadas, preservando pontes em cada uma.";
                case "convex shape" -> "Usa um contorno convexo que envolve a placa, sem acompanhar suas reentrâncias.";
                case "margin" -> "Margem em relação ao limite da placa. Um valor positivo afasta o caminho de corte da borda original." + LINEAR;
                case "gap size" -> "Largura das pontes que mantêm a placa presa ao material durante o corte." + LINEAR;
                case "gaps" -> "None = sem pontes.\nLR/TB = esquerda-direita/superior-inferior.\n4 = uma por lado.\n2LR/2TB = duas nos lados indicados.\n8 = duas por lado.";
                case "tipo de gap" -> "Bridge interrompe o corte.\nThin cria uma Geometry adicional com Thin Depth para afinar as pontes; gere seu CNC Job separadamente.\nM-Bites cria um Excellon de furos nas pontes; configure sua furação depois.";
                case "cut z" -> "Z negativo do recorte completo, salvo na Geometry gerada. Exemplo: -1,7. Revise a espessura da placa e a entrada no material de sacrifício." + LINEAR;
                case "thin depth" -> "Z negativo da Geometry das pontes, mais raso que Cut Z. Exemplo: recorte -1,7 e Thin Depth -0,5. Só se aplica ao tipo Thin; gere os dois CNC Jobs separadamente." + LINEAR;
                case "m-bites dia" -> "Diâmetro dos furos Mouse Bites. Aplicado somente ao tipo M-Bites." + LINEAR;
                case "m-bites spacing" -> "Espaço entre as bordas dos furos Mouse Bites. O passo entre centros é o diâmetro mais esse valor." + LINEAR;
                case "adicionar gap por retangulo", "adicionar gap por poligono" -> "Desenhe uma região no plot para abrir uma ponte manual onde ela cruza o caminho de corte.\n\nAtenção: se existir qualquer gap manual, ele substitui o padrão automático; sua largura depende da região desenhada. Limpar gaps manuais volta ao padrão automático.";
                default -> null;
            };
        }
        if (context.equals("Transform Tool")) {
            return switch (name) {
                case "angle" -> "Ângulo de rotação em graus. Positivo = horário; negativo = anti-horário. A transformação usa a referência escolhida.";
                case "angle x", "angle y" -> "Ângulo de inclinação no eixo indicado, em graus, relativo à referência escolhida.";
                case "factor x", "factor y" -> "Fator de escala no eixo indicado. 1 mantém o tamanho. Link aplica o mesmo fator nos dois eixos.";
                case "value x", "value y" -> "Deslocamento das coordenadas no eixo indicado. Positivo e negativo movem em sentidos opostos." + LINEAR;
                case "link" -> "Usa o mesmo valor nos dois eixos para a transformação desta seção.";
                default -> null;
            };
        }
        if (context.equals("Preferencias")) {
            return switch (name) {
                case "tema" -> "Seleciona Branco, Preto, Branco gelo ou Preto gelo. Aplicar salva a escolha; não modifica o projeto nem os dados CAM.";
                case "passo x", "passo y" -> "Distância positiva entre posições do snap neste eixo. O passo Y é ignorado quando os eixos estão vinculados." + LINEAR;
                case "ativar snap na grade" -> "Ajusta cliques e desenhos aos pontos da grade. Pode permanecer ativo mesmo com a grade visual oculta.";
                case "usar passo x tambem em y" -> "Vincula os dois eixos ao passo X; o campo Y fica desabilitado enquanto essa opção estiver ativa.";
                case "mostrar hud de coordenadas" -> "Mostra no plot as coordenadas absolutas e o deslocamento do cursor. Não modifica a origem da peça.";
                case "mostrar area a4" -> "Exibe uma referência visual do tamanho de uma folha A4. Não limita a usinagem nem muda a escala do objeto.";
                default -> null;
            };
        }
        if (context.startsWith("Exportar ")) {
            return switch (name) {
                case "unidades" -> "Converte as coordenadas para MM (milímetros) ou IN (polegadas) no arquivo exportado. O objeto no projeto não é alterado.";
                case "digitos inteiros" -> "Quantidade de dígitos antes do ponto implícito. Deve comportar a maior coordenada do trabalho. Em Excellon decimal, não é utilizada.";
                case "digitos decimais" -> "Quantidade de casas decimais das coordenadas exportadas. Mais casas aumentam a precisão numérica, não a precisão física da máquina.";
                case "zeros" -> context.equals("Exportar Gerber")
                        ? "L omite zeros à esquerda; T omite à direita. O formato declarado no Gerber permite reconstruir o ponto decimal."
                        : "LZ mantém zeros à esquerda; TZ mantém à direita. Aplica-se somente ao formato sem ponto decimal.\n\nAtenção: a convenção Excellon difere de L/T do Gerber.";
                case "formato" -> "Decimal grava o ponto explicitamente. Sem ponto decimal exige a configuração de dígitos e zeros para interpretar as coordenadas.";
                case "slots" -> "Roteado representa slots com G00/M15/G01/M16. Furado usa o ciclo G85. Escolha o formato aceito pelo software que lerá o arquivo.";
                default -> null;
            };
        }
        if (context.equals("Layer Color")) {
            return switch (name) {
                case "cor" -> "Cor de preenchimento do objeto no plot; o contorno é derivado dessa cor. Não modifica Gerber, Geometry ou G-code.";
                case "opacidade" -> "0% = totalmente transparente; 100% = opaco. É apenas uma configuração de visualização.";
                default -> null;
            };
        }
        return secondary(context, name);
    }

    private static String secondary(String context, String name) {
        if (context.equals("Panelize Tool")) return switch (name) {
            case "colunas", "linhas" -> "Quantidade inteira de cópias neste eixo, maior que zero. O total é colunas × linhas.";
            case "espacamento colunas", "espacamento linhas" -> "Espaço entre as caixas das cópias, não a distância entre suas origens." + LINEAR;
            case "largura", "altura" -> "Tamanho máximo do painel. Quando o limite está ativo, a quantidade de linhas e colunas é reduzida para caber." + LINEAR;
            default -> null;
        };
        if (context.equals("Film Tool")) return switch (name) {
            case "dpi (png)" -> "Resolução do PNG em pontos por polegada. Mais DPI produz uma imagem maior; SVG e PDF mantêm a geometria vetorial.";
            case "borda (negativo)" -> "Margem externa da moldura do filme negativo, aplicada na unidade do objeto." + LINEAR;
            case "espessura do traco" -> "Espessura usada para representar caminhos no filme exportado. Não muda a largura do cobre no projeto." + LINEAR;
            case "pagina (pdf)" -> "Define a folha do PDF. Revise a orientação e a escala no programa de impressão para preservar as dimensões da placa.";
            case "referencia" -> "Ponto de referência usado para inclinar o filme. Afeta a posição após a transformação, não apenas o ângulo.";
            default -> null;
        };
        if (context.equals("QRCode Tool")) return switch (name) {
            case "tamanho da caixa" -> "Tamanho inteiro de cada módulo em décimos da unidade do objeto. Exemplo: 10 corresponde a um módulo de 1 mm em um objeto MM. A dimensão total também depende da versão e da borda.";
            case "versao (1-40)" -> "Versão mínima do QR Code. Versões maiores têm mais módulos; o código pode crescer automaticamente para comportar os dados.";
            case "borda (modulos)" -> "Quantidade de módulos da margem livre ao redor do código, para separá-lo dos elementos próximos e facilitar a leitura.";
            case "correcao de erro" -> "L, M, Q e H permitem níveis crescentes de recuperação de dados danificados. Mais correção pode aumentar o tamanho do código.";
            case "polaridade" -> "Positivo adiciona cobre nos módulos do QR Code. Negativo retira os módulos de uma região preenchida.";
            default -> null;
        };
        if (context.equals("Extract Drills Tool") || context.equals("Punch Gerber Tool")) return switch (name) {
            case "diametro fixo" -> "Diâmetro comum para os furos criados nos pads escolhidos. Confira se cabe no menor pad." + LINEAR;
            case "proporcao (%)" -> "Diâmetro do furo como porcentagem da dimensão do pad. Aplica-se ao modo proporcional.";
            case "anel - circular", "anel - oblongo", "anel - quadrado", "anel - retangular", "anel - outros" -> "Largura do anel de cobre a preservar ao redor do furo, para este tipo de pad. Aplica-se ao modo por anel." + LINEAR;
            default -> null;
        };
        if (context.equals("Calculators")) return switch (name) {
            case "cut z" -> "Profundidade abaixo da superfície, usada com a ponta e o ângulo V para calcular a largura efetiva de corte. Aceita a magnitude ou Z negativo." + LINEAR;
            case "tool diameter" -> "Largura efetiva de corte da ferramenta V na profundidade Cut Z, calculada pela ponta e pelo ângulo." + LINEAR;
            case "board length", "board width" -> "Dimensão da placa em centímetros, usada para calcular sua área. Não utiliza a unidade atual do projeto.";
            case "area" -> "Área para a calculadora de galvanoplastia, em cm².";
            case "current density (asf)" -> "Densidade de corrente em amperes por pé quadrado (ASF), usada para estimar corrente e tempo.";
            case "copper growth (µm)" -> "Espessura de cobre a depositar em micrômetros, usada para estimar o tempo.";
            case "current (a)" -> "Corrente calculada, em amperes, a partir da área e da densidade escolhidas.";
            case "time (min)" -> "Tempo estimado de deposição em minutos. É uma estimativa do modelo, não uma medição do processo.";
            default -> null;
        };
        if (context.equals("Etch Compensation Tool")) return switch (name) {
            case "espessura do cobre (um)", "deslocamento (um)" -> "Valor em micrômetros, não na unidade do projeto. Define a espessura ou a compensação lateral conforme o método selecionado.";
            case "fator" -> "Fator de corrosão usado com a espessura do cobre para calcular a compensação lateral. Não é fator de escala do desenho.";
            default -> null;
        };
        if (context.equals("Optimal Tool") && name.equals("precisao (casas)"))
            return "Casas decimais usadas para agrupar distâncias iguais no relatório. Não melhora a precisão do Gerber original.";
        if (context.equals("Copper Thieving Tool") && name.equals("area minima"))
            return "Ignora regiões vazias menores que este valor de área.\n\nUnidades: unidade do objeto ao quadrado (mm² ou in²).";
        if (context.equals("Align Objects Tool") && name.equals("tipo"))
            return "Um ponto aplica apenas translação. Dois pontos também corrigem a rotação. Clique em pontos correspondentes na origem e na referência.";
        if (context.equals("Fiducials Tool")) return switch (name) {
            case "tamanho" -> "Dimensão da marca de alinhamento. As aberturas geradas na máscara de solda têm o dobro desse tamanho." + LINEAR;
            case "margem (auto)" -> "Distância das marcas automáticas em relação à caixa do objeto. Não se aplica aos pontos posicionados manualmente." + LINEAR;
            case "espessura (cruz)" -> "Largura dos braços de uma marca em cruz. Não altera o tamanho das marcas circulares." + LINEAR;
            default -> null;
        };
        if (context.equals("Corner Markers Tool")) return switch (name) {
            case "espessura" -> "Largura dos traços dos marcadores de canto." + LINEAR;
            case "comprimento" -> "Comprimento dos braços dos marcadores L ou cruz." + LINEAR;
            case "margem" -> "Distância dos marcadores em relação à caixa delimitadora do Gerber." + LINEAR;
            default -> null;
        };
        if (context.equals("SolderPaste Tool")) return switch (name) {
            case "z inicio da dispensa" -> "Altura para iniciar a aplicação da pasta antes do movimento de dispensa." + LINEAR;
            case "z dispensa" -> "Altura do bico enquanto aplica a pasta. Não é profundidade de fresagem." + LINEAR;
            case "z parada" -> "Altura usada ao encerrar a aplicação da pasta." + LINEAR;
            case "z deslocamento", "z troca de bico" -> "Altura livre para deslocar ou trocar o bico. Confira a peça e suas fixações." + LINEAR;
            case "x,y troca de bico" -> "Posição de troca do bico como duas coordenadas X,Y. Confira se a máquina pode alcançá-la com segurança." + LINEAR;
            case "avanco z dispensa" -> "Velocidade do movimento Z durante a aplicação da pasta." + FEED;
            case "velocidade frente (rpm)", "velocidade reversa (rpm)" -> "Rotação do mecanismo de dispensa no sentido indicado, em RPM. A reversão ajuda a interromper o fluxo de pasta.";
            case "espera frente (s)", "espera reversa (s)" -> "Tempo de espera com o mecanismo de dispensa no sentido indicado, em segundos.";
            default -> null;
        };
        if (context.equals("Calibration Tool")) return switch (name) {
            case "z de verificacao" -> "Coordenada Z nos pontos de verificação. Revise a superfície e a origem antes de executar o programa." + LINEAR;
            case "escala x", "escala y" -> "Fator de correção calculado a partir dos desvios medidos. A escala é aplicada pela origem antes da inclinação.";
            case "inclinacao x", "inclinacao y" -> "Correção angular calculada a partir dos desvios medidos, em graus. É aplicada após a escala.";
            case "zerar z antes" -> "Insere a redefinição da referência Z no início do programa de verificação.\n\nAtenção: confira a posição física da ferramenta antes de executar.";
            default -> null;
        };
        if (context.equals("Milling Tool") && name.startsWith("tool diameter ("))
            return "Diâmetro da fresa usada para abrir furos ou slots maiores. Não pode exceder o diâmetro da ferramenta Excellon selecionada. Gera uma Geometry; a profundidade e o avanço são configurados depois, no CNC Job." + LINEAR;
        if (context.equals("Editor Geometry")) {
            String geometryHelp = switch (name) {
                case "fonte" -> "Fonte instalada usada para criar contornos vetoriais. Letras viram polígonos editáveis com seus vazios preservados. Fontes ausentes ou caracteres não suportados são recusados.";
                case "tamanho" -> "Escala do tamanho da fonte, de 0.1 a 1000; não é a altura exata da letra. MM/IN usam a escala do ParseFont Python, mas as métricas podem diferir. Confira as dimensões na prévia.";
                case "buffer", "criar buffer arredondado" -> GeometryEditorDescriptions.of("buffer").text();
                default -> null;
            };
            if (geometryHelp != null) return geometryHelp;
        }
        if (context.startsWith("Editor ")) return editor(name);
        return null;
    }

    private static String editor(String name) {
        return switch (name) {
            case "buffer selecionadas", "buffer" -> "Expande as formas pela distância informada; valores negativos contraem. O tipo de junção determina o desenho dos cantos." + LINEAR;
            case "poligonizar selecionadas", "poligonizar" -> "Reconstrói polígonos a partir dos contornos selecionados. Caminhos abertos ou sem fechamento válido podem não formar um polígono.";
            case "simplificar selecionadas", "simplificar" -> "Reduz vértices usando a tolerância informada. Uma tolerância maior pode remover detalhes: confira o resultado antes de aplicar ao objeto.";
            case "fator" -> "Fator de escala das formas selecionadas. 1 mantém o tamanho; 2 dobra o tamanho.";
            case "distancia", "tolerancia" -> "Distância para expansão/contração ou tolerância da operação indicada. Revise os detalhes pequenos após a transformação." + LINEAR;
            case "vertices" -> "Quantidade inteira de lados da abertura poligonal, no mínimo três. Aplica-se a aberturas do tipo P.";
            case "rotacao", "angulo inicial", "passo angular" -> "Ângulo em graus usado pela abertura ou pelo array indicado. Não usa a unidade linear do objeto.";
            case "passo/raio" -> "Array linear: espaçamento entre cópias. Array circular: raio em torno do centro X-Y." + LINEAR;
            case "novo diametro" -> "Diâmetro aplicado às ferramentas dos furos ou slots selecionados. O posicionamento é preservado." + LINEAR;
            case "passo x / y" -> "Distância entre centros de cópias do array nos eixos X e Y." + LINEAR;
            default -> null;
        };
    }

    private static String key(String label) {
        if (label == null) return "";
        return Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim().replaceFirst(":$", "");
    }
}
