package org.flatcam.fx;

/** Context-aware help for every persisted default; never infer units from a caption alone. */
final class ToolDefaultsDescriptions {
    private ToolDefaultsDescriptions() { }

    static String text(ToolDefaults.Setting setting, boolean metric) {
        String body = specific(setting);
        if (body == null) {
            String caption = caption(setting);
            body = PanelTooltips.help(context(setting), caption);
        }
        if (body == null) throw new IllegalArgumentException("Sem descrição do padrão " + setting.key());
        body = body.replace("\n\nUnidades: unidade do objeto (mm ou in).", "")
                .replace("\n\nUnidades: unidades do objeto por minuto (mm/min ou in/min).", "");
        if (setting.perUnit()) body += "\n\nUnidades: " + (metric ? "mm" : "in")
                + (isFeed(setting) ? "/min" : "") + ". Os valores mm e in são salvos separadamente; alternar não converte o rascunho.";
        if (!setting.choices().isEmpty()) body += "\n\nOpções: " + String.join(", ", setting.choices()) + ".";
        return body + "\n\nPadrão de fábrica: " + setting.factory(metric)
                + ". Só é persistido ao salvar os padrões; vale para painéis abertos depois. "
                + "Não altera objetos, CNC Jobs nem painéis já abertos. A validação completa ocorre ao gerar a operação.";
    }

    private static boolean isFeed(ToolDefaults.Setting s) {
        return s.key().endsWith(".feedz") || s.key().endsWith(".feedrate") || s.key().endsWith(".feedrapid");
    }

    private static String context(ToolDefaults.Setting s) {
        return switch (s.tool()) {
            case "Cutout" -> "Cutout Tool";
            case "Isolation" -> "Isolation Tool";
            case "NCC" -> "NCC Tool";
            case "Paint" -> "Paint Tool";
            case "Film" -> "Film Tool";
            case "Panelize" -> "Panelize Tool";
            case "2-Sided" -> "2-Sided Tool";
            case "Copper Thieving" -> "Copper Thieving Tool";
            case "Rules Check" -> "Rules Check Tool";
            case "Fiducials" -> "Fiducials Tool";
            case "Corner Markers" -> "Corner Markers Tool";
            case "QRCode" -> "QRCode Tool";
            case "Etch Compensation" -> "Etch Compensation Tool";
            case "Punch Gerber" -> "Punch Gerber Tool";
            case "Extract Drills" -> "Extract Drills Tool";
            case "Invert Gerber" -> "Invert Gerber Tool";
            case "SolderPaste" -> "SolderPaste Tool";
            case "Calibration" -> "Calibration Tool";
            case "Optimal" -> "Optimal Tool";
            case "Drilling" -> "Drilling Tool";
            case "Geometry CNC", "CNC Job (geral)" -> "Geometry CNC Job";
            case "Excellon Milling" -> "Milling Tool";
            default -> "Preferencias";
        };
    }

    private static String caption(ToolDefaults.Setting s) {
        String field = s.key().substring(s.key().indexOf('.')+1);
        return switch (field) {
            case "tooldia", "newdia", "newnozzle" -> s.tool().equals("Excellon Milling") ? "Tool diameter (mm)" : "Tool dia";
            case "tooltype" -> "TT";
            case "type" -> "Isolation type";
            case "order" -> "Tool order";
            case "checkvalidity" -> "Check validity";
            case "forcedrest" -> "Forced Rest";
            case "milling" -> "Milling type";
            case "thinz" -> "Thin Depth";
            case "gapsize" -> "Gap size";
            case "gaptype" -> "Tipo de gap";
            case "gaps" -> "Gaps";
            case "bitediameter" -> "M-Bites Dia";
            case "bitespacing" -> "M-Bites Spacing";
            case "convex" -> "Convex Shape";
            case "spacingcolumns" -> "Espaçamento colunas";
            case "spacingrows" -> "Espaçamento linhas";
            case "limitwidth" -> "Largura";
            case "limitheight" -> "Altura";
            case "drilldia" -> "Diâmetro";
            case "dotdiameter", "squaresize", "linesize", "robberthickness", "thickness", "size" -> "Espessura";
            case "dotspacing", "squarespacing", "linespacing" -> "Espaço";
            case "robbermargin", "maskclearance", "clearance" -> "Distância";
            case "travelz" -> "Travel Z";
            case "toolchangez", "probechangez" -> "Z de troca de ferramenta";
            case "verificationz" -> "Z de verificação";
            case "vtipdia" -> "V-Tip Dia";
            case "vtipangle" -> "V-Tip Angle (graus)";
            default -> s.label();
        };
    }

    private static String specific(ToolDefaults.Setting s) {
        String key = s.key();
        if (key.equals("ncc.tooldia") || key.equals("paint.tooldia"))
            return "Lista de diâmetros iniciais das ferramentas. Separe os itens com vírgula e use ponto decimal, por exemplo 1.0, 0.5. Não é uma única ferramenta nem uma conversão automática de unidades.";
        if (key.equals("ncc.seedpolicy"))
            return "STABLE usa um início determinístico para os anéis Seed do FX. PYTHON usa o ponto representativo do algoritmo Python. Podem gerar percursos diferentes na mesma área; confira o resultado.";
        if (key.endsWith(".tooltype")) return "C1–C4 identificam fresas planas de um a quatro dentes; B é esférica e V tem ponta em V. O perfil não define avanço ou RPM automaticamente. Para V, confira V-Tip Dia e Angle ao gerar CNC.";
        if (key.equals("cutout.margin")) return PanelTooltips.help("Cutout Tool","Margin");
        if (key.equals("fiducials.margin")) return "Margem das posições automáticas dos fiduciais em relação à caixa do Gerber. Não é o tamanho da marca nem a abertura da máscara.";
        if (key.equals("cnc.preprocessor"))
            return "Perfil inicial de CNC. Deve ser compatível com o controlador e o tipo de operação. Nem todos os perfis aceitam furação, laser, sonda ou múltiplas ferramentas; a seleção final é validada no painel CNC.";
        if (key.startsWith("import.")) return switch (key) {
            case "import.gerber.units" -> "Unidade de fallback somente quando o Gerber não declara unidades. Uma declaração no arquivo tem prioridade. Não converte projetos já abertos.";
            case "import.gerber.circlesteps" -> "Segmentação usada para aproximar arcos e círculos do Gerber. Mais segmentos aumentam detalhe, memória e custo de cálculo; não aumentam a precisão física da máquina.";
            case "import.excellon.units" -> "RECUSAR impede importar um Excellon sem unidade declarada. IN/MM definem o fallback explícito para esse caso; a unidade declarada no arquivo tem prioridade.";
            case "import.excellon.decimals.in", "import.excellon.decimals.mm" -> "Casas decimais para coordenadas Excellon sem ponto, quando o arquivo não declara formato suficiente. Um ponto decimal explícito ou formato declarado tem prioridade. Só afeta novas importações na unidade indicada.";
            default -> null;
        };
        if (key.startsWith("rules.")) {
            boolean enabled = key.endsWith(".enabled");
            String rule = key.substring(6).replace(".enabled", "");
            String meaning = switch (rule) {
                case "trace_size" -> "largura da trilha";
                case "copper_to_copper" -> "distância cobre a cobre";
                case "copper_to_outline" -> "distância cobre ao contorno";
                case "silk_to_silk" -> "distância seda a seda";
                case "silk_to_mask" -> "distância seda à máscara";
                case "silk_to_outline" -> "distância seda ao contorno";
                case "mask_sliver" -> "largura de lasca da máscara";
                case "annular_ring" -> "largura do anel anular";
                case "hole_to_hole" -> "distância furo a furo";
                case "hole_size" -> "diâmetro do furo";
                default -> throw new IllegalArgumentException("Regra desconhecida " + key);
            };
            return enabled ? "Habilita inicialmente a verificação de " + meaning + ". Só é avaliada quando as camadas necessárias forem escolhidas."
                    : "Mínimo permitido para " + meaning + ". Resultados abaixo desse limite são violações; ausência de violações não certifica fabricação.";
        }
        if (key.endsWith(".depthperpass")) return "Profundidade máxima adicionada em cada passe, como valor positivo. Só é usada com Multi-Depth ativado. Não é a profundidade final do corte.";
        if (key.endsWith(".multidepth")) return "Divide a profundidade total em passes até alcançar Cut Z. Configure também a profundidade por passe; o modo de retração depende da operação CNC.";
        if (key.endsWith(".spindle")) return "Rotação em RPM ou potência conforme o perfil. Zero não comanda rotação nos perfis que permitem isso; a sequência de troca pode parar o spindle antes da pausa. Sonda e outros perfis têm requisitos próprios.";
        if (key.endsWith(".dwelltime")) return "Duração da espera após ligar o spindle, em segundos, quando Dwell estiver ativado. Zero não espera; não é uma porcentagem.";
        if (key.endsWith(".feedrapid")) return "Avanço rápido explícito apenas nos perfis que o utilizam. Zero usa o automático do perfil (normalmente 1500 mm/min, Roland 900 mm/min), convertido para a unidade correspondente. Em GRBL, os limites de G0 são definidos pelo controlador.";
        if (key.equals("drilling.endz")) return "Altura final da furação após desligar o spindle. Sem exclusões, End XY é executado nessa altura; com exclusões, desloca em altura protegida e só depois desce. Atenção: confira obstáculos antes de escolher uma altura baixa.";
        if (key.equals("geometry.cutdepth")) return "Magnitude positiva da profundidade de corte; o CNC escreve Z negativo. Não confundir com o Cut Z negativo dos outros painéis. Ferramentas V podem calcular a profundidade a partir da ponta e largura.";
        if (key.equals("geometry.probechangez")) return "Altura de troca/retorno usada pelo painel de sondagem Mach3. Não ativa a sonda por si só. Ao gerar, confira os parâmetros e as confirmações exigidas pela sequência.";
        if (key.equals("film.boundary")) return "Largura da moldura externa do filme negativo. Não altera a Geometry de origem.";
        if (key.equals("film.scalestroke")) return "Ajuste da largura do traço no filme exportado. Zero conserva o padrão do exportador; não é o fator de escala X/Y da placa.";
        if (key.equals("film.padsize")) return "Diâmetro dos vazios nos centros dos pads quando a furação por pads do filme positivo estiver ativada. Não cria um Excellon.";
        if (key.equals("film.scalex") || key.equals("film.scaley")) return "Fator de escala do filme no eixo indicado. 1 preserva o tamanho; não transforma o objeto do projeto.";
        if (key.equals("film.skewx") || key.equals("film.skewy")) return "Inclinação do filme no eixo indicado, em graus. Não é uma distância nem transformação do objeto de origem.";
        if (key.equals("film.dpi")) return "Resolução da imagem PNG em pontos por polegada. Não controla a geometria vetorial de SVG/PDF nem a precisão da máquina.";
        if (key.equals("film.negative")) return "Seleciona inicialmente filme negativo: moldura preenchida com as regiões do objeto vazadas. Não inverte o Gerber do projeto.";
        if (key.equals("thieving.minarea")) return "Área mínima de uma região para receber cobre auxiliar. Unidades: unidade do objeto ao quadrado (mm² ou in²); não é uma distância e o seletor de comprimentos não converte este valor.";
        if (key.equals("fiducials.size")) return "Diâmetro/tamanho da marca de alinhamento no cobre. A abertura correspondente de máscara usa o dobro desse tamanho.";
        if (key.equals("fiducials.thickness")) return "Espessura dos traços da marca de alinhamento quando o formato escolhido utiliza linhas.";
        if (key.equals("corners.drill")) return "Diâmetro dos furos opcionais criados nos cantos selecionados. Não adiciona os furos até executar a ferramenta.";
        if (key.equals("corners.length")) return "Comprimento dos braços dos marcadores de canto; não é margem nem diâmetro de furo.";
        if (key.startsWith("qrcode.")) return switch (key) {
            case "qrcode.version" -> "Versão inicial do QR Code. Define sua capacidade/tamanho em módulos; o texto também precisa caber na versão escolhida.";
            case "qrcode.boxsize" -> "Tamanho numérico de cada módulo do QR Code, usado ao construir o desenho. Não é a versão nem a margem em módulos.";
            case "qrcode.border" -> "Largura da borda livre do QR Code, em quantidade de módulos, não em mm/in.";
            default -> null;
        };
        if (key.equals("etch.thickness")) return "Espessura da folha de cobre em micrômetros (µm), não mm/in. Serve ao cálculo de compensação lateral por corrosão.";
        if (key.startsWith("punch.") || key.startsWith("extract.")) {
            if (key.endsWith(".fixeddiameter")) return "Diâmetro do furo quando o método Fixo estiver selecionado. Punch cria vazios no Gerber; Extract Drills cria um novo Excellon.";
            if (key.endsWith(".factor")) return "Proporção do tamanho do pad usada para calcular o furo, em porcentagem. Só vale no método proporcional; não é uma distância.";
            return "Largura de cobre a conservar ao redor do furo para o formato de pad indicado. Só é usada no método por anel anular; não é o diâmetro do furo.";
        }
        if (key.equals("optimal.precision")) return "Casas decimais usadas ao comparar/agrupar distâncias de cobre no Optimal. Não define as casas do G-code nem melhora a precisão física da máquina.";
        return null;
    }
}
