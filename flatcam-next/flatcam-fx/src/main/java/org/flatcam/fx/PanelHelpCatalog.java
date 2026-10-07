package org.flatcam.fx;

/** Additional, context-specific help. Never infer machining semantics from an arbitrary label. */
final class PanelHelpCatalog {
    private static final String UNIT = "\n\nUnidades: unidade do objeto (mm ou in).";
    private PanelHelpCatalog() { }

    static String help(String context, String name) {
        String specific = switch (context) {
            case "Panelize Tool" -> panelize(name);
            case "2-Sided Tool" -> sided(name);
            case "Film Tool" -> film(name);
            case "Calibration Tool" -> calibration(name);
            case "Copper Thieving Tool" -> thieving(name);
            case "Rules Check Tool" -> rules(name);
            case "QRCode Tool" -> qr(name);
            case "Subtract Tool" -> switch (name) {
                case "tipo de objeto", "gerber", "geometry" -> "Escolhe a categoria dos dois objetos usados na subtração.";
                case "alvo (de onde se subtrai)" -> "Objeto do qual será retirada a região ocupada pelo subtraendo. O resultado é um novo objeto.";
                case "subtraendo (o que e retirado)" -> "Objeto que define a região a retirar do alvo. Confira o alinhamento e as unidades dos dois objetos.";
                case "subtrair" -> "Cria um novo objeto com a diferença alvo menos subtraendo. Confira o resultado antes de excluir as origens.";
                default -> null;
            };
            case "Invert Gerber Tool" -> switch (name) {
                case "gerber a inverter" -> "Gerber cujo cobre será subtraído de uma moldura para produzir o negativo em um novo objeto.";
                case "margem" -> "Margem externa da caixa usada para inverter o Gerber." + UNIT;
                case "cantos da caixa", "quadrado", "redondo", "chanfrado" -> "Formato dos cantos da moldura externa. Não altera os cantos das trilhas originais.";
                case "inverter gerber" -> "Cria o negativo do cobre dentro da caixa delimitadora acrescida da margem.";
                default -> null;
            };
            case "Fiducials Tool" -> switch (name) {
                case "tipo", "circular", "cruz", "xadrez" -> "Formato da marca usada para alinhamento óptico. A máscara correspondente é gerada separadamente.";
                case "posicao", "automatico", "manual" -> "Automático usa os limites do Gerber. Manual pede os pontos no plot.";
                case "segundo fiducial (automatico)", "acima", "abaixo", "nenhum" -> "Escolhe o canto do fiducial intermediário ou deixa apenas as marcas extremas.";
                case "adicionar fiduciais" -> "Cria uma cópia do Gerber de cobre com as marcas. No modo manual, clique nas posições solicitadas no plot.";
                case "adicionar aberturas na mascara" -> "Cria aberturas nos últimos pontos de fiduciais, com o dobro do tamanho da marca de cobre.";
                default -> null;
            };
            case "Corner Markers Tool" -> switch (name) {
                case "tipo", "canto (l)", "cruz" -> "Escolhe marcadores em L ou cruz nos cantos marcados abaixo.";
                case "cantos", "superior esq.", "superior dir.", "inferior esq.", "inferior dir.", "todos" -> "Seleciona em quais cantos da caixa do Gerber serão adicionados marcadores.";
                case "adicionar marcadores" -> "Cria um Gerber com os marcadores nos cantos escolhidos, usando margem, espessura e comprimento.";
                case "criar furos nos cantos" -> "Cria um Excellon nos cantos escolhidos. Confira o diâmetro e o alinhamento antes de gerar a furação.";
                default -> null;
            };
            case "Extract Drills Tool", "Punch Gerber Tool" -> pads(name);
            case "Etch Compensation Tool" -> switch (name) {
                case "metodo", "fator de corrosao", "lista de corrosivos", "deslocamento manual" -> "Define a compensação pela espessura e fator de corrosão, pelo corrosivo ou por um deslocamento manual.";
                case "corrosivo" -> "Seleciona o corrosivo utilizado pelo modelo de compensação. Confira se corresponde ao processo real.";
                case "oz -> um" -> "Converte o peso de cobre em oz/ft² para uma espessura estimada em micrômetros.";
                case "mils -> um" -> "Converte milésimos de polegada para micrômetros: 1 mil = 25,4 µm.";
                case "compensar" -> "Cria uma cópia com cobre expandido pela compensação lateral calculada.\n\nAtenção: o modelo não substitui a calibração do processo de corrosão.";
                default -> null;
            };
            case "Align Objects Tool" -> switch (name) {
                case "objeto a alinhar" -> "Objeto que será movido ou girado para coincidir com a referência.";
                case "objeto de referencia" -> "Objeto que permanece fixo e fornece os pontos correspondentes de alinhamento.";
                case "um ponto (translada)", "dois pontos (translada e gira)", "alinhar (clique nos pontos)" -> "Clique nos pares de pontos correspondentes conforme solicitado. Um par move; dois pares também corrigem a rotação.\n\nAtalho: Esc cancela a coleta.";
                default -> null;
            };
            case "Optimal Tool" -> switch (name) {
                case "distancia minima" -> "Menor separação calculada entre regiões de cobre; somente leitura." + UNIT;
                case "pares" -> "Quantidade de pares de regiões que apresentam a menor distância, na precisão de agrupamento escolhida.";
                case "encontrar a menor distancia" -> "Calcula a menor separação entre regiões de cobre e lista seus locais. Não modifica o Gerber.";
                case "outras distancias (entre parenteses, quantos pares)", "locais da distancia escolhida", "locais da menor distancia (clique para ir ate la)" -> "Selecione uma distância ou um local para marcar e centralizar o ponto correspondente no plot.";
                default -> null;
            };
            case "SolderPaste Tool" -> switch (name) {
                case "bicos (diametros)", "adicionar bico" -> "Diâmetros dos bicos usados para gerar caminhos de dispensa. Informe valores positivos adequados aos pads." + UNIT;
                case "gerber da mascara de pasta" -> "Gerber das aberturas de pasta; não confundir com a máscara de solda.";
                case "geometria de pasta" -> "Geometry de dispensa gerada por esta ferramenta, usada para criar o programa do dispensador.";
                case "gerar geometria de dispensa" -> "Gera caminhos para os bicos cadastrados a partir das aberturas da máscara de pasta.";
                case "gerar cnc job de pasta" -> "Gera comandos do dispensador com o perfil Paste_1.\n\nAtenção: não é um programa de fresagem; confira alturas, avanços e sentidos do mecanismo.";
                default -> null;
            };
            case "Transform Tool" -> switch (name) {
                case "reference" -> "Define o ponto fixo das transformações: origem, ponto informado, centro da seleção ou caixa de outro objeto.";
                case "distance" -> "Distância de expansão ou contração do buffer. Negativa contrai; positiva expande." + UNIT;
                case "percent (%)" -> "Percentual usado para calcular a distância de buffer a partir das dimensões do objeto. Não é fator de escala.";
                case "rounded" -> "Usa cantos arredondados no buffer; desmarcado preserva junções angulares.";
                case "rotate", "skew x", "skew y", "scale x", "scale y", "offset y", "flip on x", "flip on y", "buffer por distancia", "buffer por percentual" -> "Aplica imediatamente a transformação indicada aos objetos selecionados na árvore, usando os valores e a referência desta seção.\n\nAtenção: confira a seleção antes de aplicar.";
                default -> null;
            };
            case "Isolation Tool" -> switch (name) {
                case "forced rest" -> "Inclui também regiões que não foram cobertas pelas ferramentas anteriores no cálculo Rest. Só atua com Rest Machining habilitado.";
                case "limpar area desenhada" -> "Remove somente a exceção desenhada no plot. A Geometry de exceção escolhida permanece selecionada.";
                case "generate geometry" -> "Calcula os caminhos de isolamento e cria Geometry; não gera G-code nesta etapa.";
                default -> null;
            };
            case "NCC Tool" -> switch (name) {
                case "retangulo", "poligono" -> "Formato da área a desenhar no plot para limitar a limpeza do NCC. Não é uma forma adicionada ao objeto de origem.";
                case "obj type", "gerber", "geometry" -> "Categoria da origem usada como cobre ou região a proteger na limpeza.";
                case "object" -> "Objeto de origem do NCC. Confira as unidades e o alinhamento com o limite escolhido.";
                case "selecionar area no desenho" -> "Desenhe no plot o limite da limpeza. Não modifica o Gerber de origem.\n\nAtalho: Esc cancela o desenho.";
                case "gerar geometry" -> "Calcula os caminhos das ferramentas selecionadas e cria Geometry para posterior geração CNC.";
                default -> null;
            };
            case "Paint Tool" -> switch (name) {
                case "origem (gerber ou geometry)" -> "Objeto cujos polígonos serão preenchidos por caminhos de usinagem.";
                case "diametros das ferramentas" -> "Diâmetros positivos das ferramentas. Selecione uma linha para editar seus parâmetros individuais." + UNIT;
                case "poligonos a pintar", "todos os poligonos", "um poligono (clique)", "objeto de referencia", "area: retangulo", "area: poligono", "selecionar" -> "Define as regiões a preencher: todos os polígonos, um clicado, limite de referência ou área desenhada no plot.";
                case "atualizar lista" -> "Recarrega as opções de origem e referência a partir dos objetos atuais do projeto.";
                case "limpar selecao" -> "Remove os polígonos ou a área escolhida para o Paint, sem apagar objetos do projeto.";
                case "pintar" -> "Gera Geometry de preenchimento com os diâmetros e parâmetros individuais; o G-code é gerado depois.";
                default -> null;
            };
            case "Milling Tool" -> switch (name) {
                case "excellon" -> "Arquivo de furos e slots a converter em caminhos de fresagem.";
                case "generate geometry for drills", "generate geometry for slots" -> "Cria caminhos de centro para fresar os furos ou slots das ferramentas selecionadas. Configure profundidade e avanço depois, no CNC da Geometry.";
                default -> null;
            };
            case "Geometry CNC Job", "Drilling Tool" -> switch (name) {
                case "extra cut (caminhos fechados)" -> "Acrescenta um trecho no fechamento do caminho para completar o corte. O campo ao lado determina o comprimento adicional.";
                case "end x,y" -> "Posição final opcional: None mantém X-Y; X;Y desloca após os cortes.\n\nAtenção: confira a altura End Z e todo o percurso." + UNIT;
                case "end z" -> "Altura final opcional: None usa a altura de deslocamento. Confira a folga para o movimento final X-Y." + UNIT;
                case "gerar cnc job...", "generate cnc job" -> "Gera o programa com o perfil e os parâmetros exibidos. Conflitos de configurações comuns precisam ser revisados antes.\n\nAtenção: inspecione os caminhos, as unidades e as alturas antes de executar na máquina.";
                case "search db" -> "Busca ferramentas de furação na Tools Database pelo diâmetro. Correspondências ambíguas são recusadas; revise os parâmetros recuperados.";
                default -> null;
            };
            case "Cutout Tool" -> switch (name) {
                case "limpar gaps manuais" -> "Remove as regiões de pontes manuais e volta a usar a distribuição automática selecionada.";
                case "gerar (free-form)" -> "Cria Geometry que acompanha a borda da placa, incluindo reentrâncias, com a compensação da fresa e as pontes configuradas.";
                case "gerar (rectangular)" -> "Cria Geometry de recorte retangular pela caixa da origem, com compensação da fresa e pontes configuradas.";
                default -> null;
            };
            case "Preferencias" -> switch (name) {
                case "mostrar eixos" -> "Mostra os eixos X e Y da origem no plot. Não desloca os objetos nem altera o G-code.";
                case "aplicar preferencias" -> "Valida e salva tema e opções do plot. Não altera os dados CAM do projeto.";
                default -> null;
            };
            case "Gerber Object", "Excellon Object", "Geometry Object", "CNC Job Object" -> object(name);
            default -> null;
        };
        if (specific != null) return specific;
        if (context.startsWith("Editor ")) {
            if (context.equals("Editor Gerber") && name.startsWith("adicionar abertura ")) return editor("adicionar abertura");
            if (context.equals("Editor Gerber") && (name.startsWith("diametro (") || name.startsWith("largura (") || name.startsWith("altura (")))
                return "Dimensão positiva da nova abertura, na unidade indicada no rótulo. C/P usam diâmetro; R/O usam largura e altura.";
            String editor = editor(name);
            if (editor != null) return editor;
        }
        return switch (name) {
            case "fechar", "close" -> "Fecha este painel. Objetos já criados permanecem no projeto.";
            case "adicionar ferramenta", "adicionar" -> "Adiciona o diâmetro informado à tabela deste painel. Revise os parâmetros da nova ferramenta antes de gerar.";
            case "remover", "delete" -> "Remove as ferramentas selecionadas da tabela deste painel, sem excluir objetos do projeto.";
            case "preprocessor", "pre-processador" -> "Perfil que define os comandos e as convenções do programa CNC. Escolha o controlador real da máquina; o formato não garante compatibilidade física.";
            case "tool change x,y" -> "Posição opcional de troca: None mantém X-Y; informe X;Y para deslocar. Confira a altura de troca e o percurso." + UNIT;
            case "no", "forward", "reverse" -> java.util.List.of("Isolation Tool", "Drilling Tool", "NCC Tool").contains(context)
                    ? "Ordem das ferramentas: No usa a tabela; Forward vai do menor diâmetro ao maior; Reverse vai do maior ao menor. Rest Machining exige maior para menor." : null;
            case "calculate" -> context.equals("Calculators") ? "Calcula os valores derivados desta seção com os dados informados. Não muda o projeto nem configura automaticamente parâmetros CAM. Confira as unidades exibidas." : null;
            case "name", "nome" -> "Nome do objeto no projeto. Renomear não muda o arquivo original no disco.";
            case "plot" -> "Exibe ou oculta este objeto no plot. Os dados do objeto e o G-code não mudam.";
            case "gerber", "gerber de origem", "gerber de cobre", "gerber a analisar", "gerber a compensar", "gerber a furar" -> "Gerber de origem desta operação. Confira a camada escolhida antes de aplicar.";
            case "mascara de solda (abertura = 2x o tamanho)" -> "Gerber que receberá as aberturas de máscara nos pontos das marcas; não é a máscara de pasta.";
            default -> null;
        };
    }

    private static String panelize(String n) { return switch (n) {
        case "objeto a repetir" -> "Objeto de origem das cópias. No modo conjunto, marque também as outras camadas alinhadas.";
        case "distancia entre copias baseada em", "caixa do proprio objeto", "caixa de outro objeto" -> "A largura e altura da referência, mais os espaçamentos, definem os passos da grade.\n\nAtenção: use a mesma referência para cobre, furos e contorno, evitando deslocamentos diferentes por camada.";
        case "limitar o tamanho do painel" -> "Reduz linhas e colunas quando necessário para caber na largura e altura máximas informadas.";
        case "criar painel" -> "Cria as cópias na grade exibida. No modo conjunto, todas as camadas recebem exatamente os mesmos deslocamentos.";
        default -> null;
    }; }
    private static String sided(String n) { return switch (n) {
        case "objetos (ctrl/shift para varios)" -> "Camadas que receberão o mesmo espelhamento. Selecione cobre, furos e contorno juntos para preservar o alinhamento.";
        case "usar selecao da arvore" -> "Marca nesta lista os objetos selecionados na árvore do projeto.";
        case "referencia da linha de espelhamento", "origem", "ponto", "caixa de objeto" -> "Define por onde passa o eixo de espelhamento: origem, coordenada escolhida ou caixa de outro objeto.";
        case "pegar no plot" -> "Clique no plot para preencher o ponto de referência.\n\nAtalho: Esc cancela a coleta.";
        case "diametro" -> "Diâmetro dos furos de alinhamento criados no novo Excellon." + UNIT;
        case "adicionar furo no plot" -> "Clique na posição de um furo de alinhamento. Ao gerar, será criado também seu espelho pelo eixo configurado.";
        case "limpar" -> "Limpa a lista de furos de alinhamento deste painel, sem apagar objetos do projeto.";
        case "espelhar" -> "Espelha as camadas marcadas pelo mesmo eixo. Confira a referência; marque criar cópia para preservar os originais.";
        case "pre-visualizar no plot" -> "Mostra o espelhamento antes de aplicá-lo. A prévia não cria nem transforma objetos do projeto.";
        default -> null;
    }; }
    private static String film(String n) { return switch (n) {
        case "objeto do filme" -> "Gerber ou Geometry que fornecerá o desenho exportado.";
        case "caixa (moldura)" -> "Objeto que define os limites da moldura do filme, especialmente no negativo.";
        case "positivo", "negativo" -> "Positivo desenha as regiões do objeto. Negativo desenha a moldura com essas regiões vazadas.";
        case "furar (somente filme positivo)", "excellon", "centro dos pads", "furo" -> "Adiciona vazios aos pads do filme positivo, usando um Excellon alinhado ou o diâmetro informado para os centros." + UNIT;
        case "escala" -> "Multiplica o filme pelos fatores X e Y. 1 mantém as dimensões; não altera o objeto de origem.";
        case "inclinacao (graus)" -> "Inclina o filme nos eixos X e Y, em graus, pela referência escolhida.";
        case "espelhar" -> "Espelha o filme exportado no eixo escolhido, sem modificar o objeto de origem.";
        case "formato" -> "SVG e PDF preservam vetores. PNG gera uma imagem na resolução DPI configurada.";
        case "cor (positivo)" -> "Cor das regiões no filme positivo; não muda a cor do objeto no plot.";
        case "retrato", "paisagem" -> "Orientação da página do PDF. Confira a impressão em escala real, sem ajuste automático à folha.";
        case "gerar filme" -> "Exporta o filme com as transformações escolhidas.\n\nAtenção: confira escala, espelho e orientação antes de fabricar ou imprimir.";
        default -> null;
    }; }
    private static String calibration(String n) { return switch (n) {
        case "objeto (furos ou pads)" -> "Os cliques precisam atingir um furo ou pad da origem; a posição adotada é seu centro.";
        case "clique livre" -> "Usa diretamente as coordenadas clicadas no plot, sem procurar um centro de pad ou furo.";
        case "obter pontos" -> "Coleta os quatro pontos, começando pelo inferior esquerdo e seguindo a ordem indicada.\n\nAtalho: Esc cancela a coleta.";
        case "2º ponto", "superior esquerdo", "inferior direito" -> "Define qual ponto o programa de verificação visita depois da origem.";
        case "x alvo", "y alvo" -> "Coordenadas dos pontos obtidos no plot. São referências de cálculo, não deltas medidos." + UNIT;
        case "delta x", "delta y" -> "Desvio medido na máquina nos pontos 2 e 3. Vazio ou zero não aplica correção neste eixo." + UNIT;
        case "z de deslocamento", "z de troca de ferramenta" -> "Altura livre para deslocar ou trocar a ferramenta no programa de verificação. Confira as fixações." + UNIT;
        case "xy de troca" -> "Coordenadas X,Y da troca no programa de verificação. Confira o percurso e a altura antes de executar." + UNIT;
        case "gerar g-code de verificacao" -> "Salva um programa que visita os quatro pontos coletados.\n\nAtenção: confira alturas, origem e fixações; não executa nem mede a máquina automaticamente.";
        case "calcular fatores (a partir dos deltas)" -> "Calcula escala e inclinação a partir dos desvios medidos nos pontos 2 e 3.";
        case "aplicar escala aos pontos", "aplicar inclinacao aos pontos" -> "Atualiza somente os pontos coletados com os fatores calculados, para conferir a correção.";
        case "objeto a ajustar (escala e depois inclinacao, pela origem)", "calibrar objeto" -> "Cria uma cópia _calibrated com escala e depois inclinação pela origem. O objeto original é preservado.";
        default -> null;
    }; }
    private static String thieving(String n) { return switch (n) {
        case "distancia" -> "Afastamento entre o cobre ou a máscara original e os elementos auxiliares desta seção." + UNIT;
        case "margem" -> "Margem da região externa usada nesta seção para gerar cobre auxiliar." + UNIT;
        case "diametro", "lado", "espessura" -> "Tamanho dos pontos, quadrados, linhas ou barra de cobre da seção indicada." + UNIT;
        case "espaco" -> "Espaço livre entre elementos repetidos; não é o passo entre seus centros." + UNIT;
        case "preencher dentro de", "o proprio objeto", "areas desenhadas", "objeto de referencia" -> "Limita o preenchimento pela caixa da origem, pelas áreas desenhadas ou por um objeto de referência alinhado.";
        case "caixa", "retangular", "minima (casco convexo)" -> "Escolhe caixa retangular ou casco convexo como limite externo do preenchimento.";
        case "tipo de preenchimento", "solido", "pontos", "quadrados", "linhas" -> "Escolhe cobre sólido ou elementos repetidos. Linhas usam a própria origem como referência.";
        case "desenhar area (2 cliques)" -> "Escolha dois cantos opostos no plot para adicionar uma região de preenchimento.\n\nAtalho: Esc cancela a coleta.";
        case "limpar areas" -> "Remove as regiões desenhadas da prévia, sem apagar os Gerbers criados.";
        case "adicionar copper thieving" -> "Cria um Gerber com cobre auxiliar nas regiões livres, usando afastamento e padrão escolhidos.";
        case "adicionar robber bar" -> "Cria uma barra periférica de cobre pela caixa da origem, com a margem e espessura desta seção.";
        case "incluir", "ambos", "thieving", "robber bar", "nenhum" -> "Escolhe quais resultados auxiliares gerados neste painel serão incluídos na máscara de galvanoplastia.";
        case "area galvanizada" -> "Área calculada pelo resultado da máscara.\n\nUnidades: unidade do objeto ao quadrado (mm² ou in²).";
        case "gerber da mascara de solda", "gerar mascara de galvanoplastia" -> "Cria a máscara a partir do Gerber escolhido e dos últimos resultados de thieving/barra deste painel.";
        default -> null;
    }; }
    private static String rules(String n) { return switch (n) {
        case "cobre topo", "cobre base", "seda topo", "seda base", "mascara topo", "mascara base", "contorno", "excellon 1", "excellon 2" -> "Objeto usado no papel indicado para verificar a placa. (nenhum) omite esta camada; regras que dependem dela podem não ser avaliadas.";
        case "tamanho da trilha", "distancia cobre a cobre", "distancia cobre ao contorno", "distancia seda a seda", "distancia seda a mascara de solda", "distancia seda ao contorno", "lasca minima da mascara de solda", "anel anular minimo", "distancia furo a furo", "tamanho do furo" -> "Habilita a regra indicada. O valor ao lado é o mínimo permitido para o tamanho ou afastamento avaliado. Resultados abaixo desse limite são listados como violações." + UNIT;
        case "executar verificacao" -> "Verifica as regras marcadas nas camadas escolhidas, sem alterar os objetos.\n\nAtenção: ausência de violações não certifica fabricação nem segurança CNC; confira também as regras que não puderam ser avaliadas.";
        case "resultado (clique numa regra)", "violacoes (clique para ir ate la)" -> "Selecione uma regra para listar violações; selecione um local para centralizar e marcá-lo no plot.";
        default -> null;
    }; }
    private static String qr(String n) { return switch (n) {
        case "dados do qr code" -> "Texto ou endereço codificado. Mais dados podem aumentar a versão e o tamanho final do QR Code.";
        case "gerber de destino" -> "Gerber que receberá o QR Code na posição clicada. Confira espaço, polaridade e tamanho.";
        case "mascara", "quadrada", "arredondada" -> "Formato da região externa usada para colocar o código. Não é a máscara de solda da placa.";
        case "l", "m", "q", "h" -> "Nível de correção de erro do QR Code; L, M, Q e H oferecem recuperação crescente e podem aumentar seu tamanho.";
        case "positiva", "negativa" -> "Positiva adiciona cobre nos módulos; negativa abre os módulos numa região preenchida.";
        case "colocar qr code (clique no destino)" -> "Gera o código e pede um clique para posicioná-lo no Gerber de destino.\n\nAtalho: Esc cancela o posicionamento.";
        default -> null;
    }; }
    private static String pads(String n) { return switch (n) {
        case "tamanho do furo", "origem dos furos", "fixo", "anel anular", "proporcional", "excellon" -> "Escolhe como definir os furos: diâmetro fixo, anel de cobre, proporção do pad ou Excellon alinhado (quando disponível).";
        case "tipos de pad", "circulares", "oblongos", "quadrados", "retangulares", "outros" -> "Inclui ou exclui este formato de pad no processamento. Confira a lista de aberturas antes de gerar.";
        case "todos", "nenhum" -> "Marca ou desmarca os formatos ou aberturas oferecidos nesta seção; não apaga objetos.";
        case "extrair furos" -> "Cria um Excellon nos centros dos pads escolhidos, usando o modo de dimensionamento configurado.";
        case "furar gerber" -> "Cria uma cópia do Gerber com as regiões dos furos retiradas do cobre. Não gera G-code de furação.";
        default -> null;
    }; }
    private static String editor(String n) { return switch (n) {
        case "circulo" -> "Clique no centro e depois no perímetro para definir o raio.\n\nAtalho: Esc cancela; Ctrl+Z desfaz após inserir.";
        case "retangulo" -> "Clique em dois cantos opostos para desenhar o retângulo.\n\nAtalho: Esc cancela; Ctrl+Z desfaz após inserir.";
        case "arco" -> "Escolha exatamente três pontos: início, ponto intermediário e fim. Enter ou botão direito conclui; Esc cancela.";
        case "caminho", "poligono" -> "Clique nos vértices do caminho ou polígono. Enter ou botão direito conclui; Esc cancela. Confira o fechamento antes de aplicar.";
        case "nova abertura" -> "Formato da nova abertura: C = circular; R = retangular; O = oblonga; P = poligonal. O D-code é criado automaticamente.";
        case "d" -> "D-code da abertura Gerber selecionada. Renomear atualiza as formas associadas; não é o diâmetro.";
        case "adicionar abertura c", "adicionar abertura" -> "Cria uma abertura do formato e dimensões exibidos para desenhar novos pads; a criação da abertura não coloca uma forma no plot.";
        case "alterar d-code" -> "Renomeia a abertura selecionada e atualiza suas formas associadas. Não muda suas dimensões.";
        case "alterar dimensoes" -> "Atualiza as dimensões da abertura selecionada e suas formas associadas. Confira pads e trilhas afetados.";
        case "array", "criar array de pads" -> "Linear: X/Y define o primeiro pad, Passo define a distância e Ângulo define a direção. Circular: X/Y define o centro, Passo/raio define o raio; use ângulo inicial e passo angular.";
        case "arco (+ccw/-cw)" -> "Amplitude em graus: positiva percorre no sentido anti-horário (CCW); negativa, horário (CW).";
        case "criar disco", "criar semidisco" -> "Cria uma região circular ou setor pela posição, raio e ângulos informados. Não adiciona um furo Excellon.";
        case "area >", "e <", "selecionar por area" -> "Seleciona as formas com área entre os limites mínimo e máximo; não apaga as formas.\n\nUnidades: unidade do objeto ao quadrado (mm² ou in²).";
        case "transformacao", "angulo/fator", "transformar selecionadas" -> "Escolha a transformação. Girar/inclinar usam graus; escalar usa fator (1 mantém tamanho). Referência X/Y é o ponto fixo da operação.";
        case "transformacoes" -> "Expande o painel de rotação, escala e espelhamento das formas selecionadas.";
        case "escalar selecionadas" -> "Multiplica as dimensões e coordenadas da seleção pelo fator informado. 1 mantém o tamanho. Confira o resultado no plot.";
        case "cancelar posicionamento" -> "Cancela a operação em prévia antes de inserir ou transformar as formas.\n\nAtalho: Esc.";
        case "apagar com selecionadas x/y" -> "Usa as formas selecionadas deslocadas por X/Y como molde para apagar regiões do desenho. Não é uma translação simples da seleção.\n\nAtalho: Ctrl+Z desfaz.";
        case "aplicar" -> "Valida e aplica o rascunho ao objeto em memória; o arquivo original não é salvo automaticamente.";
        case "cancelar" -> "Descarta o rascunho e encerra o editor sem aplicar alterações ao objeto.";
        case "ferramenta para novos furos/slots" -> "Ferramenta Excellon usada nos novos furos e slots. Não altera automaticamente as ferramentas das formas existentes.";
        case "redimensionar selecionados", "redimensionar" -> "Aplica o novo diâmetro aos furos ou slots selecionados, preservando seu posicionamento.";
        case "array de furos selecionados", "array de slots selecionados", "array de furos", "array de slots" -> "Repete os furos ou slots selecionados pela grade de colunas/linhas e passos X/Y. Clique no plot conforme solicitado para posicionar.\n\nAtalho: Esc cancela; Ctrl+Z desfaz.";
        case "selecionar" -> "Volta ao modo de seleção no plot. Clique seleciona; Ctrl+clique alterna; arrastar cria uma seleção por área.";
        case "adicionar furo", "adicionar slot" -> "Usa a ferramenta escolhida para desenhar um furo ou slot no plot. Confira os pontos e dimensões antes de aplicar.";
        case "excluir" -> "Apaga do rascunho as formas selecionadas.\n\nAtalho: Ctrl+Z desfaz.";
        case "desfazer", "↶" -> "Desfaz a última alteração deste editor.\n\nAtalho: Ctrl+Z.";
        case "refazer", "↷" -> "Reaplica a alteração desfeita neste editor.\n\nAtalho: Ctrl+Y.";
        case "salvar e sair do editor", "aplicar e sair", "aplicar ao objeto", "aplicar ao cnc job" -> "Valida e aplica o rascunho ao objeto em memória. Não sobrescreve automaticamente o arquivo de origem no disco.";
        case "descartar alteracoes", "cancelar edicao" -> "Encerra a edição sem aplicar o rascunho ao objeto. Alterações não aplicadas serão descartadas.";
        case "excluir selecionadas", "excluir abertura e formas" -> "Apaga da edição as formas selecionadas ou a abertura e suas formas.\n\nAtalho: Ctrl+Z desfaz a alteração.";
        case "mover selecionadas", "mover", "copiar selecionadas", "copiar" -> "No plot, clique na origem e depois no destino para mover ou copiar a seleção.\n\nAtalho: Esc cancela o posicionamento.";
        case "x", "y", "centro x", "referencia x", "mover x/y", "copiar x/y" -> "Coordenada ou deslocamento do eixo indicado para a operação desta seção." + UNIT;
        case "quantidade", "colunas / linhas" -> "Quantidade inteira de cópias no array; em grade, informe colunas e linhas.";
        case "largura/diametro", "altura", "raio" -> "Dimensão da abertura ou forma desta seção. Use valores positivos e confira a prévia." + UNIT;
        case "graus" -> "Ângulo de rotação das formas selecionadas, em graus, pela referência indicada.";
        case "girar", "escalar", "espelhar horizontal", "espelhar vertical" -> "Transforma as formas selecionadas pelo centro de seus limites, usando os valores desta seção. A ferramenta associada é preservada.\n\nAtalho: Ctrl+Z desfaz.";
        case "confirmar paint" -> "Insere no rascunho os caminhos da última prévia calculada. Não apaga os contornos originais.\n\nAtalho: Ctrl+Z desfaz todo o preenchimento.";
        case "cancelar previa" -> "Remove a prévia do plot sem inserir caminhos no rascunho.";
        case "method" -> "Método do Paint Shape: Standard = passos para dentro; Seed = expansão; Lines = linhas paralelas; Combo = alternativas.";
        case "negrito", "italico" -> "Estilo da fonte para gerar os contornos do texto vetorial, quando disponível na fonte instalada.";
        case "gerar e posicionar texto" -> "Converte o texto em formas vetoriais e pede a posição no plot. Confira tamanho e vazios das letras antes de aplicar.";
        case "salvar arquivo..." -> "Grava uma cópia do rascunho em arquivo. Não aplica as mudanças ao CNC Job nem sobrescreve automaticamente a origem.";
        default -> null;
    }; }
    private static String object(String n) { return switch(n) {
        case "isolation routing" -> ToolDescriptions.of("isolation").text();
        case "ncc tool" -> ToolDescriptions.of("ncc").text();
        case "cutout tool" -> ToolDescriptions.of("cutout").text();
        case "drilling tool" -> ToolDescriptions.of("drilling").text();
        case "milling tool" -> "Abre a conversão de furos e slots em caminhos de fresagem; cria Geometry antes do CNC Job.";
        case "generate cnc job" -> "Abre os parâmetros CNC da Geometry. Confira perfil, diâmetros, profundidades, avanços e alturas antes de gerar.";
        case "editar geometry", "excellon editor" -> "Abre o editor do objeto; as alterações ficam em rascunho até aplicar e sair.";
        case "ver g-code" -> CommandHelpCatalog.help("Ver G-code");
        case "editar g-code" -> CommandHelpCatalog.help("Editar G-code");
        case "plot kind" -> "All mostra cortes e deslocamentos. Cut mostra os movimentos de corte; Travel mostra deslocamentos. Não modifica o programa.";
        case "percorrer", "<", ">" -> "Seleciona o primeiro, anterior ou próximo movimento da rota CNC no plot. É uma inspeção visual, não controle da máquina.";
        case "limpar" -> "Remove o destaque do passo CNC selecionado, sem apagar a rota nem o programa.";
        case "reproduzir" -> "Inicia ou pausa a animação da rota no plot. Não envia comandos à máquina nem simula colisões.";
        case "exportar csv" -> "Exporta a sequência de movimentos para inspeção em tabela. Não substitui o G-code nem valida a segurança da máquina.";
        case "mark all" -> "Marca ou desmarca todas as aberturas para destacar suas formas no plot. Não muda o Gerber.";
        case "rounded" -> "Usa cantos arredondados na caixa ou área auxiliar gerada nesta seção.";
        case "gerar geometry follow" -> "Cria Geometry pelas linhas centrais do Gerber, em vez dos limites do cobre.";
        case "offset x,y" -> "Deslocamento X e Y aplicado ao objeto, sem compensação do raio da ferramenta." + UNIT;
        case "transformations", "transformations:", "transformacoes" -> "Abre as transformações do objeto. Confira a seleção e a referência antes de aplicar.";
        default -> null;
    }; }
}
