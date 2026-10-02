package org.flatcam.fx;

import java.util.Map;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Control;

/** Portuguese help based on ToolsDB2UI, with explicit FX transfer limitations. */
final class ToolsDatabaseDescriptions {
    private static final Set<String> TRANSFERRED = Set.of("name", "tooldia", "tool_type", "tool_target", "tol_min", "tol_max",
            "tools_iso_passes", "tools_iso_overlap", "tools_iso_isotype",
            "tools_ncc_operation", "tools_ncc_overlap", "tools_ncc_method", "tools_ncc_connect", "tools_ncc_contour",
            "tools_ncc_offset_choice", "tools_ncc_offset_value", "tools_drill_cutz", "tools_drill_multidepth",
            "tools_drill_depthperpass", "tools_drill_travelz", "tools_drill_feedrate_z", "tools_drill_spindlespeed",
            "tools_drill_dwell", "tools_drill_dwelltime", "tools_drill_offset");
    private static final Set<String> LINEAR = Set.of("tooldia", "tol_min", "tol_max", "vtipdia", "offset_value", "cutz",
            "depthperpass", "travelz", "extracut_length", "tools_drill_cutz", "tools_drill_depthperpass", "tools_drill_travelz",
            "tools_drill_offset", "tools_paint_offset", "tools_ncc_margin", "tools_ncc_offset_value", "tools_cutout_margin",
            "tools_cutout_gapsize", "tools_cutout_gap_depth", "tools_cutout_mb_dia", "tools_cutout_mb_spacing");

    static String fieldText(ToolsDatabaseFields.Field field) {
        String body = switch (field.key()) {
            case "name" -> "Nome para identificar a ferramenta na biblioteca. Não altera o diâmetro nem a geometria do trabalho.";
            case "tooldia" -> "Diâmetro de corte da ferramenta, maior que zero. Para Shape V, é calculado ao aplicar alterações em V-Dia, V-Angle ou Cut Z.";
            case "tol_min" -> "Limite inferior absoluto do intervalo de diâmetros aceito na busca de brocas. Não é uma tolerância ±. Exemplo: Min = 0,78 e Max = 0,82 aceita brocas nesse intervalo. O diâmetro exato tem prioridade.";
            case "tol_max" -> "Limite superior do intervalo de diâmetros aceito na busca de brocas; deve ser maior ou igual a Min. O intervalo só é usado quando Max > Min; resultados ambíguos são recusados.";
            case "tool_target" -> "Define a operação à qual a ferramenta se destina: General, Milling, Drilling, Isolation, Paint, NCC ou Cutout. General é elegível para as três buscas CAM já integradas ao FX. A seleção também determina as seções exibidas.";
            case "tool_type" -> "Formato da ferramenta:\nC1 a C4 = fresa circular com 1 a 4 cortes.\nB = ponta esférica.\nV = ponta em V.\n\nAo escolher V, V-Dia, V-Angle e Cut Z determinam o diâmetro efetivo.";
            case "vtipdia" -> "Diâmetro da ponta da ferramenta em V, não negativo. Disponível para Shape V; participa do cálculo do diâmetro de corte.";
            case "vtipangle" -> "Ângulo total da ponta da ferramenta em V, em graus, entre 0 e 180 (sem incluir os extremos). Participa do cálculo do diâmetro na profundidade Cut Z.";
            case "type" -> "Classificação da usinagem: Iso = isolamento; Rough = desbaste; Finish = acabamento. É uma descrição da ferramenta, não um ajuste automático de velocidade.";
            case "offset" -> "Posição do caminho: Path = sem compensação; In = para dentro por meio diâmetro; Out = para fora por meio diâmetro; Custom = usa Custom Offset.";
            case "offset_value" -> "Deslocamento personalizado em relação ao caminho original. O campo fica habilitado somente quando Tool Offset é Custom.";
            case "cutz" -> "Coordenada Z de corte em relação à superfície. Normalmente negativa para entrar no material. Em Shape V, também determina o diâmetro efetivo de corte.";
            case "tools_drill_cutz" -> "Profundidade de furação abaixo da superfície, normalmente escrita como Z negativo. O FX utiliza sua magnitude e aplica a compensação Offset Z ao gerar.";
            case "multidepth", "tools_drill_multidepth" -> "Divide o corte em passes de profundidade limitada até atingir Cut Z. Habilita Depth / Pass; na furação, há retração entre os passes.";
            case "depthperpass", "tools_drill_depthperpass" -> "Profundidade máxima adicionada em cada passe, como valor positivo. Só fica habilitada quando Multi-Depth está marcado.";
            case "travelz", "tools_drill_travelz" -> "Altura livre para deslocar a ferramenta entre cortes ou furos. Deve ficar acima da superfície e das fixações do trabalho.";
            case "feedrate" -> "Velocidade de avanço no plano X-Y enquanto a ferramenta está cortando. Informe um valor positivo em unidades do trabalho por minuto (mm/min ou in/min).";
            case "feedrate_z", "tools_drill_feedrate_z" -> "Velocidade de mergulho no eixo Z durante o corte ou a furação. Informe um valor positivo em unidades do trabalho por minuto (mm/min ou in/min).";
            case "feedrate_rapid", "tools_drill_feedrate_rapid" -> "Velocidade dos deslocamentos rápidos, em mm/min ou in/min, conforme a unidade do trabalho. Seu uso depende do pós-processador; alguns perfis de impressoras usam avanço explícito nos rápidos.";
            case "spindlespeed", "tools_drill_spindlespeed" -> "Rotação do spindle em RPM. Zero omite os comandos de spindle nos perfis que permitem isso; perfis como Mach3 com sonda exigem RPM positivo ao gerar.";
            case "dwell", "tools_drill_dwell" -> "Habilita uma pausa após ligar o spindle, para permitir que o motor alcance a rotação definida. Habilita o campo Dwell Time.";
            case "dwelltime", "tools_drill_dwelltime" -> "Duração da pausa após ligar o spindle, em segundos. Disponível quando Dwell está marcado; deve ser não negativa.";
            case "extracut" -> "Acrescenta um trecho de corte no encontro entre o início e o fim de um caminho fechado, para ajudar a completar o isolamento. Habilita Extra Cut Length.";
            case "extracut_length" -> "Comprimento do trecho adicional no fechamento do caminho. Disponível somente quando Extra Cut está marcado.";
            case "tools_drill_offset" -> "Compensação da ponta da broca. No FX, um valor positivo aumenta a profundidade de Cut Z; um negativo a reduz. A profundidade resultante deve continuar abaixo da superfície.";
            case "tools_drill_drill_slots" -> "Permite tratar os slots da ferramenta como uma sequência de furos. Habilita Overlap e Last Drill para configurar essa sequência.";
            case "tools_drill_drill_overlap" -> "Sobreposição entre furos consecutivos ao perfurar slots, em porcentagem do diâmetro da broca. Só fica habilitada com Drill Slots; maior sobreposição cria mais furos.";
            case "tools_drill_last_drill" -> "Adiciona um furo na extremidade do slot quando a sequência regular não cobre todo o seu comprimento. Só fica habilitado com Drill Slots.";
            case "tools_iso_passes" -> "Quantidade inteira de passes de isolamento ao redor do cobre. Mais passes aumentam a largura isolada, em conjunto com o diâmetro e Overlap.";
            case "tools_iso_overlap" -> "Sobreposição entre passes de isolamento, em porcentagem da largura da ferramenta. O valor deve ficar entre 0 e menos de 100%.";
            case "tools_iso_milling_type", "tools_ncc_milling_type" -> "Escolhe o sentido de fresagem: Climb (concordante) ou Conventional (discordante). Considere as características mecânicas da máquina ao configurar o trabalho.";
            case "tools_iso_follow" -> "Gera um caminho que segue o centro das trilhas Gerber, em vez de isolar suas bordas. Esse modo corta sobre a trilha.";
            case "tools_iso_isotype" -> "Região a isolar: Both = bordas externas e internas; Exterior = apenas externas; Interior = apenas contornos de aberturas internas do polígono.";
            case "tools_paint_overlap", "tools_ncc_overlap" -> "Sobreposição entre caminhos, em porcentagem da largura da ferramenta (0 a menos de 100%). Valores maiores criam mais trajetos e podem aumentar o tempo de cálculo e de usinagem.";
            case "tools_paint_offset" -> "Distância para afastar o preenchimento das bordas do polígono a pintar. Define a margem entre o preenchimento e o contorno.";
            case "tools_paint_method" -> "Método de preenchimento:\nStandard = passos para dentro.\nSeed = expansão de uma semente.\nLines = linhas paralelas.\nCombo = tenta métodos alternativos.\n\nLaser_lines aparece desabilitado nesta base, como no Python.";
            case "tools_paint_connect", "tools_ncc_connect" -> "Liga segmentos resultantes para reduzir levantamentos da ferramenta. A conexão deve permanecer dentro da área permitida para usinagem.";
            case "tools_paint_contour", "tools_ncc_contour" -> "Adiciona um caminho ao redor do perímetro do polígono para completar o acabamento das bordas.";
            case "tools_ncc_operation" -> "Clear remove o cobre livre. Isolation cria passes de isolamento antes da limpeza; ferramentas V são tratadas como Isolation pelo adaptador NCC do FX.";
            case "tools_ncc_margin" -> "Margem ao redor da caixa delimitadora usada para limitar a limpeza de cobre. Não é o mesmo parâmetro que Offset das trilhas.";
            case "tools_ncc_method" -> "Método de limpeza:\nStandard = passos para dentro.\nSeed = expansão de uma semente.\nLines = linhas paralelas.\nCombo = tenta alternativas para cobrir a área.";
            case "tools_ncc_offset_choice" -> "Ativa um afastamento das trilhas e pads ao limpar o cobre. Habilita Offset Value, que define essa distância.";
            case "tools_ncc_offset_value" -> "Afastamento das regiões de cobre ao executar a limpeza. Só é aplicado quando Offset está marcado.";
            case "tools_cutout_margin" -> "Margem em relação ao limite da placa. Um valor positivo afasta o contorno de corte da borda original.";
            case "tools_cutout_gapsize" -> "Largura das pontes que mantêm a placa presa ao material durante o corte. O tipo de ponte é escolhido em Gap Type.";
            case "tools_cutout_gaps_ff" -> "Distribuição das pontes:\nNone = nenhuma.\nLR = esquerda/direita.\nTB = superior/inferior.\n4 = uma por lado.\n2LR e 2TB = duas em cada lado indicado.\n8 = duas por lado.";
            case "tools_cutout_convexshape" -> "Cria um contorno convexo que envolve toda a placa, sem acompanhar suas reentrâncias. No Python, essa opção se destina a objetos Gerber.";
            case "tools_cutout_gap_type" -> "Tipo de ponte: Bridge = interrupção do corte; Thin = ponte parcialmente fresada para ficar mais fina; M-Bites = ponte perfurada com furos para facilitar a separação.";
            case "tools_cutout_gap_depth" -> "Coordenada Z do corte parcial usado para afinar as pontes. Só fica habilitada com Gap Type Thin; deve preservar material suficiente para manter a ponte.";
            case "tools_cutout_mb_dia" -> "Diâmetro dos furos que perfuram as pontes Mouse Bites. Só fica habilitado com Gap Type M-Bites.";
            case "tools_cutout_mb_spacing" -> "Espaço entre as bordas dos furos Mouse Bites. No Python, o passo entre centros é o diâmetro mais esse valor. Só fica habilitado com Gap Type M-Bites.";
            default -> throw new IllegalArgumentException("Sem tooltip para " + field.key());
        };
        if (LINEAR.contains(field.key())) body += "\n\nUnidades: valor na unidade do trabalho (mm ou in); a base não converte unidades automaticamente.";
        if (!TRANSFERRED.contains(field.key())) body += "\n\nIntegração FX: este parâmetro é salvo na base, mas sua transferência para o CAM ainda não está implementada.";
        return body;
    }

    static String actionText(String label) {
        return switch (label) {
            case "Adicionar ferramenta" -> "Adiciona uma ferramenta à base em memória com parâmetros iniciais editáveis. Use Save DB para gravá-la no arquivo.";
            case "Copiar" -> "Duplica uma ou várias ferramentas selecionadas, incluindo seus parâmetros e campos desconhecidos. As cópias recebem novos IDs e podem ser editadas separadamente.";
            case "Excluir" -> "Remove as ferramentas selecionadas após confirmação. O arquivo no disco só é alterado quando a base é salva.";
            case "Aplicar parametros" -> "Valida os campos editados e aplica as alterações à ferramenta em memória. Não grava no disco; Save DB persiste a base. Valores inválidos mantêm o rascunho para correção.";
            case "Nova base" -> "Inicia uma biblioteca vazia sem apagar o arquivo atual. Se houver alterações não salvas, pede confirmação antes de descartá-las.";
            case "Import DB" -> "Abre uma base .FlatDB ou JSON do FlatCAM Python e substitui a biblioteca em memória após leitura válida. Pede confirmação para descartar alterações; em caso de erro, mantém a base atual.";
            case "Export DB" -> "Grava uma cópia .FlatDB/JSON em outro arquivo. Mantém a associação e as alterações pendentes da base atual; exportar para seu próprio arquivo equivale a salvar. Cria backup ao sobrescrever.";
            case "Save DB" -> "Valida e salva a biblioteca no arquivo associado, ou pede um destino na primeira gravação. Cria backup antes de sobrescrever e limpa o indicador de alterações. Atalho: Ctrl+S.";
            default -> throw new IllegalArgumentException("Sem tooltip para " + label);
        };
    }

    static String groupText(ToolsDatabaseFields.Group group) {
        return switch (group) {
            case DESCRIPTION -> "Identificação, diâmetro, tolerância e operação da ferramenta. Selecione apenas uma ferramenta para editar; seleção múltipla permite copiar ou excluir.";
            case MILLING -> "Formato e parâmetros de fresagem da ferramenta. A transferência dos parâmetros de Milling da base para o CAM do FX ainda está pendente.";
            case DRILLING -> "Parâmetros de furação, profundidade por passe, avanço, spindle e compensação Z. A ajuda de cada campo indica se ele já é transferido para o Drilling do FX.";
            case ISOLATION -> "Passes de isolamento, sobreposição, região e opções de fresagem. A ajuda de cada campo indica os parâmetros já transferidos para Isolation no FX.";
            case PAINT -> "Parâmetros de preenchimento de polígonos. São editáveis e salvos na base; a transferência para o Paint do FX ainda está pendente.";
            case NCC -> "Parâmetros de limpeza de cobre e isolamento. A ajuda de cada campo indica os parâmetros já transferidos para NCC no FX.";
            case CUTOUT -> "Margem de corte e pontes Bridge, Thin ou Mouse Bites. São editáveis e salvos na base; a transferência para o Cutout do FX ainda está pendente.";
        };
    }

    static boolean hasActionHelp(String label) {
        return !Set.of("Adicionar ferramenta", "Copiar", "Excluir").contains(label);
    }

    static TooltipContent content(String text) {
        return TooltipContent.describe(text);
    }

    static void apply(Map<Object, Object> properties, String title, String text) {
        ToolDescriptions.apply(properties, title, text);
    }

    static void apply(Node node, String title, String text) {
        if (node instanceof Control control) control.setTooltip(null); // Avoid competing native and animated popups.
        node.setAccessibleHelp(text);
        apply(node.getProperties(), title, text);
    }

    private ToolsDatabaseDescriptions() { }
}
