package org.flatcam.fx;

/** Additional, context-specific help. Never infer machining semantics from an arbitrary label. */
final class PanelHelpCatalog {
    private static final String UNIT = I18n.text("ui.aa1e925251e3");
    private PanelHelpCatalog() { }

    static String help(String context, String name) {
        context = PanelTooltips.canonicalContext(context);
        String specific = switch (context) {
            case "Panelize Tool" -> panelize(name);
            case "2-Sided Tool" -> sided(name);
            case "Film Tool" -> film(name);
            case "Calibration Tool" -> calibration(name);
            case "Copper Thieving Tool" -> thieving(name);
            case "Rules Check Tool" -> rules(name);
            case "QRCode Tool" -> qr(name);
            case "Subtract Tool" -> switch (name) {
                case "tipo de objeto", "gerber", "geometry" -> I18n.text("ui.8a394a983152");
                case "alvo (de onde se subtrai)" -> I18n.text("ui.81e13c6eaa29");
                case "subtraendo (o que e retirado)" -> I18n.text("ui.23b9ac5750ed");
                case "subtrair" -> I18n.text("ui.2c94cf6b44c5");
                default -> null;
            };
            case "Invert Gerber Tool" -> switch (name) {
                case "gerber a inverter" -> I18n.text("ui.ae36c9cd4ce1");
                case "margem" -> I18n.text("ui.0e41e8b5d860") + UNIT;
                case "cantos da caixa", "quadrado", "redondo", "chanfrado" -> I18n.text("ui.c16d9591ee46");
                case "inverter gerber" -> I18n.text("ui.c393e7c7ede5");
                default -> null;
            };
            case "Fiducials Tool" -> switch (name) {
                case "tipo", "circular", "cruz", "xadrez" -> I18n.text("ui.d98d96ea6d7b");
                case "posicao", "automatico", "manual" -> I18n.text("ui.2b4c50122db7");
                case "segundo fiducial (automatico)", "acima", "abaixo", "nenhum" -> I18n.text("ui.aa3a45d602cd");
                case "adicionar fiduciais" -> I18n.text("ui.66dafe7f364f");
                case "adicionar aberturas na mascara" -> I18n.text("ui.5f0974a2c174");
                default -> null;
            };
            case "Corner Markers Tool" -> switch (name) {
                case "tipo", "canto (l)", "cruz" -> I18n.text("ui.5afa08e3d2d0");
                case "cantos", "superior esq.", "superior dir.", "inferior esq.", "inferior dir.", "todos" -> I18n.text("ui.1ed3a3631d0f");
                case "adicionar marcadores" -> I18n.text("ui.d3e40018afaf");
                case "criar furos nos cantos" -> I18n.text("ui.358f68fb9174");
                default -> null;
            };
            case "Extract Drills Tool", "Punch Gerber Tool" -> pads(name);
            case "Etch Compensation Tool" -> switch (name) {
                case "metodo", "fator de corrosao", "lista de corrosivos", "deslocamento manual" -> I18n.text("ui.19d1d8744628");
                case "corrosivo" -> I18n.text("ui.ae13c40e476c");
                case "oz -> um" -> I18n.text("ui.0515deea4f12");
                case "mils -> um" -> I18n.text("ui.4c0c9f1bcf12");
                case "compensar" -> I18n.text("ui.d2eb3d37b5a2");
                default -> null;
            };
            case "Align Objects Tool" -> switch (name) {
                case "objeto a alinhar" -> I18n.text("ui.9e9920f94d6d");
                case "objeto de referencia" -> I18n.text("ui.81b020d17d32");
                case "um ponto (translada)", "dois pontos (translada e gira)", "alinhar (clique nos pontos)" -> I18n.text("ui.e3f84804105d");
                default -> null;
            };
            case "Optimal Tool" -> switch (name) {
                case "distancia minima" -> I18n.text("ui.139e5efdcb70") + UNIT;
                case "pares" -> I18n.text("ui.6a2ee883ad61");
                case "encontrar a menor distancia" -> I18n.text("ui.43fa6b711abc");
                case "outras distancias (entre parenteses, quantos pares)", "locais da distancia escolhida", "locais da menor distancia (clique para ir ate la)" -> I18n.text("ui.e8dfacc44d4e");
                default -> null;
            };
            case "SolderPaste Tool" -> switch (name) {
                case "bicos (diametros)", "adicionar bico" -> I18n.text("ui.df64f7b89a47") + UNIT;
                case "gerber da mascara de pasta" -> I18n.text("ui.0554fd22f70e");
                case "geometria de pasta" -> I18n.text("ui.9c5bab660791");
                case "gerar geometria de dispensa" -> I18n.text("ui.35d1a836ae70");
                case "gerar cnc job de pasta" -> I18n.text("ui.27d9de1f105d");
                default -> null;
            };
            case "Transform Tool" -> switch (name) {
                case "reference" -> I18n.text("ui.c4122a394641");
                case "distance" -> I18n.text("ui.d0b328a0bb90") + UNIT;
                case "percent (%)" -> I18n.text("ui.2c76613543c7");
                case "rounded", "cantos arredondados" -> I18n.text("ui.c5d017c6efb7");
                case "rotate", "skew x", "skew y", "scale x", "scale y", "offset y", "flip on x", "flip on y", "buffer por distancia", "buffer por percentual", "escala x", "escala y", "deslocar x", "deslocar y" -> I18n.text("ui.ee2bdbc7b9cb");
                default -> null;
            };
            case "Isolation Tool" -> switch (name) {
                case "forced rest" -> I18n.text("ui.9e4e148ac8d4");
                case "limpar area desenhada" -> I18n.text("ui.62c489da0458");
                case "generate geometry", "gerar geometry" -> I18n.text("ui.ef8c37ad51f7");
                default -> null;
            };
            case "NCC Tool" -> switch (name) {
                case "retangulo", "poligono" -> I18n.text("ui.715dc6702d26");
                case "obj type", "gerber", "geometry" -> I18n.text("ui.4a104d723c75");
                case "object" -> I18n.text("ui.99d511a74f2d");
                case "selecionar area no desenho" -> I18n.text("ui.89f67e6c398a");
                case "gerar geometry" -> I18n.text("ui.b89062578231");
                default -> null;
            };
            case "Paint Tool" -> switch (name) {
                case "origem (gerber ou geometry)" -> I18n.text("ui.9a134eed0f48");
                case "diametros das ferramentas" -> I18n.text("ui.5ea9e4bc4747") + UNIT;
                case "poligonos a pintar", "todos os poligonos", "um poligono (clique)", "objeto de referencia", "area: retangulo", "area: poligono", "selecionar" -> I18n.text("ui.063fff3da9b0");
                case "atualizar lista" -> I18n.text("ui.9273779ade18");
                case "limpar selecao" -> I18n.text("ui.af2babe1c4a2");
                case "pintar" -> I18n.text("ui.dc6412488abb");
                default -> null;
            };
            case "Milling Tool" -> switch (name) {
                case "excellon" -> I18n.text("ui.725a33496260");
                case "generate geometry for drills", "generate geometry for slots" -> I18n.text("ui.75fb41e697e2");
                default -> null;
            };
            case "Geometry CNC Job", "Drilling Tool" -> switch (name) {
                case "extra cut (caminhos fechados)" -> I18n.text("ui.26018d15782a");
                case "end x,y" -> I18n.text("ui.3da18b0bf8f4") + UNIT;
                case "end z" -> I18n.text("ui.0645699a7c79") + UNIT;
                case "gerar cnc job...", "generate cnc job", "gerar cnc job" -> I18n.text("ui.6c339449c934");
                case "search db" -> I18n.text("ui.d24b461eb55f");
                default -> null;
            };
            case "Cutout Tool" -> switch (name) {
                case "limpar gaps manuais" -> I18n.text("ui.00df0ee8205d");
                case "gerar (free-form)" -> I18n.text("ui.1abb22f72954");
                case "gerar (rectangular)" -> I18n.text("ui.d5fa12fbb915");
                default -> null;
            };
            case "Preferências" -> switch (name) {
                case "mostrar eixos" -> I18n.text("ui.783b738f0d28");
                case "aplicar preferencias" -> I18n.text("ui.aca19f9f3f5c");
                default -> null;
            };
            case "Gerber Object", "Excellon Object", "Geometry Object", "CNC Job Object" -> object(name);
            default -> null;
        };
        if (specific != null) return specific;
        if (context.startsWith("Editor ")) {
            if (context.equals("Editor Gerber") && name.startsWith("adicionar abertura ")) return editor("adicionar abertura");
            if (context.equals("Editor Gerber") && (name.startsWith("diametro (") || name.startsWith("largura (") || name.startsWith("altura (")))
                return I18n.text("ui.51b8a532a07f");
            String editor = editor(name);
            if (editor != null) return editor;
        }
        return switch (name) {
            case "fechar", "close" -> I18n.text("ui.99a2e103dec3");
            case "adicionar ferramenta", "adicionar" -> I18n.text("ui.47046d197eae");
            case "remover", "delete" -> I18n.text("ui.c6807bd172d6");
            case "preprocessor", "pre-processador" -> I18n.text("ui.b85b6a49aa6e");
            case "tool change x,y" -> I18n.text("ui.41e732110946") + UNIT;
            case "no", "forward", "reverse" -> java.util.List.of("Isolation Tool", "Drilling Tool", "NCC Tool").contains(context)
                    ? I18n.text("ui.6b4260f2869f") : null;
            case "calculate" -> context.equals("Calculators") ? I18n.text("ui.12894a6a1a72") : null;
            case "name", "nome" -> I18n.text("ui.529d7bd6cfa2");
            case "plot" -> I18n.text("ui.082b2b388990");
            case "gerber", "gerber de origem", "gerber de cobre", "gerber a analisar", "gerber a compensar", "gerber a furar" -> I18n.text("ui.38450c367725");
            case "mascara de solda (abertura = 2x o tamanho)" -> I18n.text("ui.5f6e7500e463");
            default -> null;
        };
    }

    private static String panelize(String n) { return switch (n) {
        case "objeto a repetir" -> I18n.text("ui.a7c4137bf4ca");
        case "distancia entre copias baseada em", "caixa do proprio objeto", "caixa de outro objeto" -> I18n.text("ui.234c9051c660");
        case "limitar o tamanho do painel" -> I18n.text("ui.3c70602fead1");
        case "criar painel" -> I18n.text("ui.10609d220c78");
        default -> null;
    }; }
    private static String sided(String n) { return switch (n) {
        case "objetos (ctrl/shift para varios)" -> I18n.text("ui.e34d551f8c2c");
        case "usar selecao da arvore" -> I18n.text("ui.268bcf114956");
        case "referencia da linha de espelhamento", "origem", "ponto", "caixa de objeto" -> I18n.text("ui.2756bbb7351a");
        case "pegar no plot" -> I18n.text("ui.6d120506c745");
        case "diametro" -> I18n.text("ui.9edf8d93ddcb") + UNIT;
        case "adicionar furo no plot" -> I18n.text("ui.636a71fc7126");
        case "limpar" -> I18n.text("ui.16168e5d01ae");
        case "espelhar" -> I18n.text("ui.f9e4e1233115");
        case "pre-visualizar no plot" -> I18n.text("ui.3d2937294c73");
        default -> null;
    }; }
    private static String film(String n) { return switch (n) {
        case "objeto do filme" -> I18n.text("ui.11aeee41a999");
        case "caixa (moldura)" -> I18n.text("ui.edd662af33a9");
        case "positivo", "negativo" -> I18n.text("ui.343874ca641c");
        case "furar (somente filme positivo)", "excellon", "centro dos pads", "furo" -> I18n.text("ui.1b490399e0af") + UNIT;
        case "escala" -> I18n.text("ui.eea9376b4f66");
        case "inclinacao (graus)" -> I18n.text("ui.c3a6b7143379");
        case "espelhar" -> I18n.text("ui.3bf1f826531e");
        case "formato" -> I18n.text("ui.fcaa857c5ebe");
        case "cor (positivo)" -> I18n.text("ui.7bb1909e352f");
        case "retrato", "paisagem" -> I18n.text("ui.fede2b1e337a");
        case "gerar filme" -> I18n.text("ui.a77938359cae");
        default -> null;
    }; }
    private static String calibration(String n) { return switch (n) {
        case "objeto (furos ou pads)" -> I18n.text("ui.6d7a0b138b2b");
        case "clique livre" -> I18n.text("ui.2a9c72e3098f");
        case "obter pontos" -> I18n.text("ui.52465e564c44");
        case "2º ponto", "superior esquerdo", "inferior direito" -> I18n.text("ui.f474402c07ca");
        case "x alvo", "y alvo" -> I18n.text("ui.acbe8b1e1e78") + UNIT;
        case "delta x", "delta y" -> I18n.text("ui.dea2eb419295") + UNIT;
        case "z de deslocamento", "z de troca de ferramenta" -> I18n.text("ui.716c4cfdaf16") + UNIT;
        case "xy de troca" -> I18n.text("ui.c0bcb59921b7") + UNIT;
        case "gerar g-code de verificacao" -> I18n.text("ui.c2c28a986421");
        case "calcular fatores (a partir dos deltas)" -> I18n.text("ui.1ee536032e46");
        case "aplicar escala aos pontos", "aplicar inclinacao aos pontos" -> I18n.text("ui.763317281bb7");
        case "objeto a ajustar (escala e depois inclinacao, pela origem)", "calibrar objeto" -> I18n.text("ui.ce55236920c1");
        default -> null;
    }; }
    private static String thieving(String n) { return switch (n) {
        case "distancia" -> I18n.text("ui.691dade54090") + UNIT;
        case "margem" -> I18n.text("ui.06b60f328958") + UNIT;
        case "diametro", "lado", "espessura" -> I18n.text("ui.cf6cbcd75de7") + UNIT;
        case "espaco" -> I18n.text("ui.42200adb3259") + UNIT;
        case "preencher dentro de", "o proprio objeto", "areas desenhadas", "objeto de referencia" -> I18n.text("ui.f5da4c67a6e8");
        case "caixa", "retangular", "minima (casco convexo)" -> I18n.text("ui.662650fa59d9");
        case "tipo de preenchimento", "solido", "pontos", "quadrados", "linhas" -> I18n.text("ui.740005be4bf9");
        case "desenhar area (2 cliques)" -> I18n.text("ui.f565538fc759");
        case "limpar areas" -> I18n.text("ui.938ce58760c0");
        case "adicionar copper thieving" -> I18n.text("ui.68eada089724");
        case "adicionar robber bar" -> I18n.text("ui.ce8e2ca3c24c");
        case "incluir", "ambos", "thieving", "robber bar", "nenhum" -> I18n.text("ui.81b419de8803");
        case "area galvanizada" -> I18n.text("ui.9cb441db76ee");
        case "gerber da mascara de solda", "gerar mascara de galvanoplastia" -> I18n.text("ui.fb7ade842504");
        default -> null;
    }; }
    private static String rules(String n) { return switch (n) {
        case "cobre topo", "cobre base", "seda topo", "seda base", "mascara topo", "mascara base", "contorno", "excellon 1", "excellon 2" -> I18n.text("ui.556f53e30621");
        case "tamanho da trilha", "distancia cobre a cobre", "distancia cobre ao contorno", "distancia seda a seda", "distancia seda a mascara de solda", "distancia seda ao contorno", "lasca minima da mascara de solda", "anel anular minimo", "distancia furo a furo", "tamanho do furo" -> I18n.text("ui.5a6e489052fd") + UNIT;
        case "executar verificacao" -> I18n.text("ui.851a7adf6c1d");
        case "resultado (clique numa regra)", "violacoes (clique para ir ate la)" -> I18n.text("ui.68746d5589ae");
        default -> null;
    }; }
    private static String qr(String n) { return switch (n) {
        case "dados do qr code" -> I18n.text("ui.2bf12ca48e4f");
        case "gerber de destino" -> I18n.text("ui.a12ed594351e");
        case "mascara", "quadrada", "arredondada" -> I18n.text("ui.69e3ca1aea3a");
        case "l", "m", "q", "h" -> I18n.text("ui.15969c5ef571");
        case "positiva", "negativa" -> I18n.text("ui.a068ad7b294a");
        case "colocar qr code (clique no destino)" -> I18n.text("ui.4a29532d2011");
        default -> null;
    }; }
    private static String pads(String n) { return switch (n) {
        case "tamanho do furo", "origem dos furos", "fixo", "anel anular", "proporcional", "excellon" -> I18n.text("ui.3f6ef22be902");
        case "tipos de pad", "circulares", "oblongos", "quadrados", "retangulares", "outros" -> I18n.text("ui.7bb62838bb16");
        case "todos", "nenhum" -> I18n.text("ui.46a295dd6efb");
        case "extrair furos" -> I18n.text("ui.4e5833a41d85");
        case "furar gerber" -> I18n.text("ui.debab6268ccf");
        default -> null;
    }; }
    private static String editor(String n) { return switch (n) {
        case "circulo" -> I18n.text("ui.740447454c26");
        case "retangulo" -> I18n.text("ui.43c9673958d4");
        case "arco" -> I18n.text("ui.ff22d0e83c00");
        case "caminho", "poligono" -> I18n.text("ui.fa11e4a775c1");
        case "nova abertura" -> I18n.text("ui.152cbca96c72");
        case "d" -> I18n.text("ui.f9b63d7988ff");
        case "adicionar abertura c", "adicionar abertura" -> I18n.text("ui.ec32f6ee44d5");
        case "alterar d-code" -> I18n.text("ui.3fc6c66b9c36");
        case "alterar dimensoes" -> I18n.text("ui.a9ef897fd2f1");
        case "array", "criar array de pads" -> I18n.text("ui.bb9d53a83eec");
        case "arco (+ccw/-cw)" -> I18n.text("ui.0ae580d73258");
        case "criar disco", "criar semidisco" -> I18n.text("ui.b05bf739421f");
        case "area >", "e <", "selecionar por area" -> I18n.text("ui.84d3e7ee32de");
        case "transformacao", "angulo/fator", "transformar selecionadas" -> I18n.text("ui.d133a2a83fb5");
        case "transformacoes" -> I18n.text("ui.9f352d96f369");
        case "escalar selecionadas" -> I18n.text("ui.6760dd9cfbca");
        case "cancelar posicionamento" -> I18n.text("ui.df54020b414d");
        case "apagar com selecionadas x/y" -> I18n.text("ui.af177e541900");
        case "aplicar" -> I18n.text("ui.874338f9f207");
        case "cancelar" -> I18n.text("ui.36d16ad151c0");
        case "ferramenta para novos furos/slots" -> I18n.text("ui.dc94fd90f6c9");
        case "redimensionar selecionados", "redimensionar" -> I18n.text("ui.d0c315e852e4");
        case "array de furos selecionados", "array de slots selecionados" -> I18n.text("ui.545d57abe001");
        case "array de furos", "array de slots" -> I18n.text("ui.1a78e294b184");
        case "selecionar" -> I18n.text("ui.6161a1797273");
        case "adicionar furo", "adicionar slot" -> I18n.text("ui.36f5d6d09d73");
        case "excluir" -> I18n.text("ui.b75964f1db6b");
        case "desfazer", "↶" -> I18n.text("ui.ff0f5a14916f");
        case "refazer", "↷" -> I18n.text("ui.6e481a7f3837");
        case "salvar e sair do editor", "aplicar e sair", "aplicar ao objeto", "aplicar ao cnc job" -> I18n.text("ui.f4fce043c6cf");
        case "descartar alteracoes", "cancelar edicao" -> I18n.text("ui.c0ba05360022");
        case "excluir selecionadas", "excluir abertura e formas" -> I18n.text("ui.b78d568e3e23");
        case "mover selecionadas", "mover", "copiar selecionadas", "copiar" -> I18n.text("ui.85dd13e44335");
        case "x", "y", "centro x", "referencia x", "mover x/y", "copiar x/y" -> I18n.text("ui.6f6b0ac72b6f") + UNIT;
        case "quantidade", "colunas / linhas" -> I18n.text("ui.e1654930c05b");
        case "largura/diametro", "altura", "raio" -> I18n.text("ui.f32fdd4733a8") + UNIT;
        case "graus" -> I18n.text("ui.2c2c37da8779");
        case "girar", "escalar", "espelhar horizontal", "espelhar vertical" -> I18n.text("ui.93b2f0b556fa");
        case "confirmar paint" -> I18n.text("ui.288ef5f04c0c");
        case "cancelar previa" -> I18n.text("ui.7dad7e55d373");
        case "method" -> I18n.text("ui.1736801198f7");
        case "negrito", "italico" -> I18n.text("ui.cbc341df17f9");
        case "gerar e posicionar texto" -> I18n.text("ui.07bbd3f06ac0");
        case "salvar arquivo..." -> I18n.text("ui.6e655d4e4e51");
        default -> null;
    }; }
    private static String object(String n) { return switch(n) {
        case "plot" -> I18n.text("ui.55f889916c34");
        case "isolation routing" -> ToolDescriptions.of("isolation").text();
        case "ncc tool" -> ToolDescriptions.of("ncc").text();
        case "cutout tool" -> ToolDescriptions.of("cutout").text();
        case "drilling tool" -> ToolDescriptions.of("drilling").text();
        case "milling tool" -> I18n.text("ui.cc88e7801d40");
        case "generate cnc job", "gerar cnc job" -> I18n.text("ui.939aacfa8c2a");
        case "editar geometry", "excellon editor" -> I18n.text("ui.c0af9572ffb9");
        case "ver g-code" -> CommandHelpCatalog.help("Ver G-code");
        case "editar g-code" -> CommandHelpCatalog.help(I18n.text("ui.fb1b561e4268"));
        case "plot kind" -> I18n.text("ui.7bf82ac55cf3");
        case "percorrer", "<", ">" -> I18n.text("ui.93b0a7891d6e");
        case "limpar" -> I18n.text("ui.b70abf81a7b7");
        case "reproduzir" -> I18n.text("ui.ad7d2e41ff01");
        case "exportar csv" -> I18n.text("ui.3a37779b3d11");
        case "mark all", "marcar todos" -> I18n.text("ui.c2cffd35285c");
        case "rounded", "cantos arredondados" -> I18n.text("ui.db3d929b8436");
        case "gerar geometry follow" -> I18n.text("ui.7a06308cd825");
        case "offset x,y", "deslocamento x,y" -> I18n.text("ui.5b56419480d6") + UNIT;
        case "transformations", "transformations:", "transformacoes" -> I18n.text("ui.fc8ef38d65df");
        default -> null;
    }; }
}
