package org.flatcam.fx;

import java.util.Map;

/**
 * What each tool of the Tools menu does, in a sentence or two, for its tooltip. The texts follow the tooltips of the
 * Python tools (appTools/*.py), shortened. Tools that are not ported yet say so.
 */
final class ToolDescriptions {

    record Description(String title, String text) {
    }

    private static final String SOON = " (Ainda não portada para o FlatCAM FX.)";

    private static final Map<String, Description> TOOLS = Map.ofEntries(
            Map.entry("double_sided", new Description("2-Sided Tool",
                    "Espelha objetos (Gerber, Excellon, Geometry) em relação a um eixo, para fresar ou imprimir o outro "
                            + "lado da placa, e cria furos de alinhamento que coincidem nas duas faces.")),
            Map.entry("align", new Description("Align Objects Tool",
                    "Alinha um objeto a outro clicando em pads ou furos correspondentes. Um ponto só translada; "
                            + "dois pontos também giram.")),
            Map.entry("extract_drills", new Description("Extract Drills Tool",
                    "Cria um Excellon com furos nos centros dos pads de um Gerber, com diâmetro fixo, proporcional "
                            + "ao pad ou deixando um anel de cobre.")),
            Map.entry("cutout", new Description("Cutout Tool",
                    "Gera o contorno de corte da placa, com pontes (gaps) que a mantêm presa à chapa até o fim.")),
            Map.entry("ncc", new Description("NCC Tool",
                    "Non-Copper Clearing: remove o cobre que não faz parte do circuito, com uma ou mais ferramentas "
                            + "(Rest Machining) e limite por área ou objeto.")),
            Map.entry("paint", new Description("Paint Tool",
                    "Preenche polígonos com caminhos de ferramenta (Standard, Seed, Lines ou Combo).")),
            Map.entry("isolation", new Description("Isolation Tool",
                    "Gera caminhos ao redor das trilhas e pads para isolá-los eletricamente, com vários passes e "
                            + "ferramentas.")),
            Map.entry("drilling", new Description("Drilling Tool",
                    "Gera o G-code para furar os furos de um Excellon, com profundidade, múltiplos passes e pós-processador.")),
            Map.entry("panelize", new Description("Panelize Tool",
                    "Replica um objeto numa grade de colunas e linhas, para fabricar várias placas de uma vez.")),
            Map.entry("film", new Description("Film Tool",
                    "Exporta um filme positivo ou negativo (SVG, PNG ou PDF) para fotolito, com escala, inclinação, "
                            + "espelho e furação.")),
            Map.entry("solderpaste", new Description("SolderPaste Tool",
                    "Gera os caminhos e o G-code de um dispensador de pasta de solda a partir do Gerber da máscara de pasta.")),
            Map.entry("subtract", new Description("Subtract Tool",
                    "Remove de um objeto a área coberta por outro (Gerber ou Geometry).")),
            Map.entry("rules", new Description("Rules Check Tool",
                    "Verifica regras de projeto: largura das trilhas, distâncias de cobre, seda, máscara e contorno, "
                            + "anel anular e furos. Cada violação pode ser localizada no plot.")),
            Map.entry("optimal", new Description("Optimal Tool",
                    "Encontra a menor distância entre os elementos de cobre de um Gerber, quantos pares estão nela e onde; "
                            + "ao escolher um local, o plot vai até lá.")),
            Map.entry("calculators", new Description("Calculators Tool",
                    "Calculadoras de unidades, de ferramenta em V e de galvanoplastia.")),
            Map.entry("transform", new Description("Transform Tool",
                    "Gira, inclina, escala, espelha ou desloca os objetos selecionados, em relação à origem, à seleção "
                            + "ou a um ponto.")),
            Map.entry("qrcode", new Description("QRCode Tool",
                    "Cria um QR Code de cobre num Gerber, no ponto em que você clicar.")),
            Map.entry("copper_thieving", new Description("Copper Thieving Tool",
                    "Preenche as áreas vazias do cobre (sólido, pontos, quadrados ou linhas) para equilibrar a corrosão; "
                            + "também cria a robber bar e a máscara de galvanoplastia.")),
            Map.entry("fiducials", new Description("Fiducials Tool",
                    "Adiciona marcas de alinhamento ao cobre (circulares, cruz ou xadrez) e aberturas na máscara de solda.")),
            Map.entry("calibration", new Description("Calibration Tool",
                    "Calibra o posicionamento da máquina com pontos de referência." + SOON)),
            Map.entry("punch", new Description("Punch Gerber Tool",
                    "Fura os pads de um Gerber a partir de um Excellon ou de um tamanho (fixo, proporcional ou por anel).")),
            Map.entry("invert", new Description("Invert Gerber Tool",
                    "Troca cobre e vazio dentro de uma caixa ao redor do Gerber, com margem e cantos escolhidos.")),
            Map.entry("corners", new Description("Corner Markers Tool",
                    "Adiciona marcadores de canto (L ou cruz) e, se quiser, furos nos cantos de um Gerber.")),
            Map.entry("etch", new Description("Etch Compensation Tool",
                    "Compensa a corrosão lateral aumentando o cobre pela espessura da folha e pelo fator de corrosão.")));

    private ToolDescriptions() {
    }

    static Description of(String id) {
        return TOOLS.get(id);
    }

    /** Puts the tip of a tool into the properties a menu item or control keeps it in. */
    static void apply(Map<Object, Object> properties, String id) {
        Description description = TOOLS.get(id);
        if (description != null) {
            properties.put(FluidTooltips.TITLE_KEY, description.title());
            properties.put(FluidTooltips.TEXT_KEY, description.text());
        }
    }

    /** A tip for a command that is not one of the tools (conversions, joins...). */
    static void apply(Map<Object, Object> properties, String title, String text) {
        properties.put(FluidTooltips.TITLE_KEY, title);
        properties.put(FluidTooltips.TEXT_KEY, text);
    }
}
