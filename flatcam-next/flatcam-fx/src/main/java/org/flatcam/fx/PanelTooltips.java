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
    private static final String LINEAR = I18n.text("ui.aa1e925251e3");
    private static final String FEED = I18n.text("ui.d35139417031");

    private PanelTooltips() { }

    static void install(Node root, String context) {
        if (root == null || root.getProperties().putIfAbsent(INSTALLED, context) != null) return;
        if (root instanceof javafx.scene.control.TableView<?> table && table.getTooltip() == null
                && !table.getProperties().containsKey(FluidTooltips.TEXT_KEY)) {
            String tableHelp = tableHelp(context);
            if (tableHelp != null) ToolDescriptions.apply(table, I18n.text("ui.8dc883ace9a2") + context, tableHelp);
        }
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
        } else if (root instanceof javafx.scene.control.TabPane tabs) {
            List.copyOf(tabs.getTabs()).forEach(tab -> {
                install(tab.getContent(), context);
                tab.contentProperty().addListener((obs,was,content) -> install(content,context));
            });
            tabs.getTabs().addListener((ListChangeListener<javafx.scene.control.Tab>) change -> {
                while(change.next()) if(change.wasAdded()) List.copyOf(change.getAddedSubList()).forEach(tab -> {
                    install(tab.getContent(),context);
                    tab.contentProperty().addListener((obs,was,content) -> install(content,context));
                });
            });
        } else if (root instanceof javafx.scene.control.SplitPane split) {
            List.copyOf(split.getItems()).forEach(child -> install(child, context));
            split.getItems().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) if (change.wasAdded())
                    List.copyOf(change.getAddedSubList()).forEach(child -> install(child, context));
            });
        } else if (root instanceof javafx.scene.control.Accordion accordion) {
            List.copyOf(accordion.getPanes()).forEach(pane -> install(pane, context));
            accordion.getPanes().addListener((ListChangeListener<TitledPane>) change -> {
                while (change.next()) if (change.wasAdded())
                    List.copyOf(change.getAddedSubList()).forEach(pane -> install(pane, context));
            });
        } else if (root instanceof javafx.scene.control.ToolBar toolbar) {
            List.copyOf(toolbar.getItems()).forEach(child -> install(child, context));
            toolbar.getItems().addListener((ListChangeListener<Node>) change -> {
                while(change.next()) if(change.wasAdded()) List.copyOf(change.getAddedSubList()).forEach(child -> install(child,context));
            });
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
            // A caption can precede multiple inputs (columns/rows, X/Y, etc.) in the same grid row.
            // Stop at the next caption; never attach row help to action buttons or unrelated rows.
            if (label instanceof Label && pane instanceof GridPane) {
                int row = index(GridPane.getRowIndex(label)), col = index(GridPane.getColumnIndex(field));
                for (int next = col + 1; ; next++) {
                    int column = next;
                    Node companion = children.stream().filter(n -> index(GridPane.getRowIndex(n)) == row
                            && index(GridPane.getColumnIndex(n)) == column).findFirst().orElse(null);
                    if (!(companion instanceof javafx.scene.control.TextInputControl || companion instanceof javafx.scene.control.Spinner<?>)) break;
                    String help = field.getAccessibleHelp();
                    if (help != null) applyUnlessAuthored(companion, label.getText(), help);
                }
            }
            String bindingKey = "fx.tip.row-bound";
            if (label.getProperties().putIfAbsent(bindingKey, Boolean.TRUE) == null)
                label.textProperty().addListener((obs, was, text) ->
                        update(label, context, (Node) label.getProperties().get(ROW_VALUE)));
        }
    }

    private static int index(Integer value) { return value == null ? 0 : value; }

    private static String tableHelp(String context) {
        context = canonicalContext(context);
        if (context.startsWith("Editor ")) return I18n.text("ui.436bc30a4940");
        if (List.of("Isolation Tool", "NCC Tool", "Paint Tool", "Geometry CNC Job", "Drilling Tool", "Milling Tool").contains(context))
            return I18n.text("ui.ad2d9c120fc7");
        if (context.equals("Gerber Object")) return I18n.text("ui.17ff5079da43");
        if (context.equals("Excellon Object")) return I18n.text("ui.389187de5ff7");
        if (context.equals("CNC Job Object")) return I18n.text("ui.e166946231ba");
        return null;
    }

    private static boolean isValue(Node node) {
        return node instanceof CheckBox checkbox && (checkbox.getText() == null || checkbox.getText().isBlank())
                || node instanceof Pane || node instanceof Control && !(node instanceof Labeled)
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
            if (node instanceof Labeled label && control.getTooltip().getText().equals(label.getText()) && !text.equals(label.getText())) {
                ToolDescriptions.apply(node, title, text);
                return;
            }
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
            case "multi-depth" -> I18n.text("ui.b539b0ae3d10") + LINEAR;
            case "dwell" -> I18n.text("ui.44e779231dad");
            case "offset", "copper offset" -> I18n.text("ui.07f2028856a4") + LINEAR;
            default -> fallback;
        };
    }

    static String help(String context, String label) {
        for (String candidate : I18n.sources(label)) {
            String help = lookup(context, candidate);
            if (help != null) return help;
        }
        return null;
    }

    static String canonicalContext(String context) {
        return I18n.source(context, "Gerber Object", "Excellon Object", "Geometry Object", "CNC Job Object",
                "Editor Gerber", "Editor Excellon", "Editor Geometry", "Geometry CNC Job", "Drilling Tool", "Milling Tool",
                "Isolation Tool", "NCC Tool", "Paint Tool", "Cutout Tool", "Calibration Tool", "Align Objects Tool",
                "Panelize Tool", "2-Sided Tool", "Film Tool", "Copper Thieving Tool", "Corner Markers Tool", "Fiducials Tool",
                "Rules Check Tool", "QRCode Tool", "Subtract Tool", "Invert Gerber Tool", "Extract Drills Tool", "Punch Gerber Tool",
                "Etch Compensation Tool", "Optimal Tool", "SolderPaste Tool", "Transform Tool", "Calculators", "Preferências",
                "Layer Color", "Exportar Gerber", "Exportar Excellon", "Ir para localização", "Localizar no objeto", "Imprimir como PDF");
    }

    private static String lookup(String context, String label) {
        context = canonicalContext(context);
        String name = key(label);
        String scoped = contextual(context, name);
        if (scoped != null) return scoped;
        return switch (name) {
            case "tool dia", "tool diameter", "tool diameter (mm)", "tool diameter (in)", "diametro da broca", "diametro da ferramenta", "diametro da ferramenta (mm)", "diametro da ferramenta (in)" ->
                    I18n.text("ui.f5dc232a0e84") + LINEAR;
            case "spot dia" -> I18n.text("ui.cb319b057e03") + LINEAR;
            case "passes" -> I18n.text("ui.16197e80938e");
            case "overlap (%)", "sobreposicao (%)" -> I18n.text("ui.3ef9009da869");
            case "method", "metodo" -> PanelHelpCatalog.help(context, name);
            case "connect", "conectar caminhos" -> I18n.text("ui.e118b0da1cdf");
            case "contour", "contorno" -> I18n.text("ui.dd089b7e79e7");
            case "rest", "rest machining" -> I18n.text("ui.ee5e6c23aef0");
            case "tool order", "ordem" -> I18n.text("ui.d22fd9511bb4");
            case "check validity", "verificar validade dos diametros" -> I18n.text("ui.5a756dcbcbb6");
            case "combine" -> I18n.text("ui.51eec4336df5");
            case "multi-depth" -> I18n.text("ui.0d20ffdbcfcc");
            case "cut z", "cut z (nao v)" -> I18n.text("ui.f9bdd0393884") + LINEAR;
            case "travel z", "z de deslocamento" -> I18n.text("ui.dc3ffee94380") + LINEAR;
            case "focus/end z" -> I18n.text("ui.c5cedf78c41e") + LINEAR;
            case "feedrate z", "avanco z" -> I18n.text("ui.f6e1cad50dcc") + FEED;
            case "feed rate", "avanco xy" -> I18n.text("ui.5d82bb7fdc0d") + FEED;
            case "spindle rpm" -> I18n.text("ui.4a1f713ddacb");
            case "potencia s/pwm" -> I18n.text("ui.203a95b2695c");
            case "dwell" -> I18n.text("ui.4dc0ad534d81");
            case "offset z" -> I18n.text("ui.03ee6b58a9e1") + LINEAR;
            case "tool change", "troca de ferramenta (conforme perfil)" -> I18n.text("ui.785425641416");
            case "tool change z", "z de troca de ferramenta" -> I18n.text("ui.91ccd63a71eb") + LINEAR;
            case "end move z" -> I18n.text("ui.01e9b9ebc3e1") + LINEAR;
            case "end move x,y", "xy de troca", "troca x,y" -> I18n.text("ui.d7b5370b513a") + LINEAR;
            case "probe z final (< 0)" -> I18n.text("ui.d90372d2a658") + LINEAR;
            case "probe feed" -> I18n.text("ui.b813f6af443f") + FEED;
            case "contact z / placa" -> I18n.text("ui.7ac40cf21a38") + LINEAR;
            case "troca x,y (opcional)" -> I18n.text("ui.041770004fa0") + LINEAR;
            case "v-tip dia", "tip diameter", "diametro da ponta" -> I18n.text("ui.8960a1f464af") + LINEAR;
            case "v-tip angle (graus)", "tip angle" -> I18n.text("ui.7d107a31029d");
            case "search and add" -> I18n.text("ui.d00ced7068bd");
            case "pick from db", "escolher da base" -> I18n.text("ui.003dcc4efcc7");
            case "optimal" -> I18n.text("ui.c63d5e901969");
            case "apply parameters to all tools", "aplicar parametros a todas as ferramentas" -> I18n.text("ui.263308029a5e");
            case "reset tool" -> I18n.text("ui.c494dda8fb44");
            case "solid", "solido" -> I18n.text("ui.f4d2e8d05a99");
            case "multi-color", "multicor" -> I18n.text("ui.689e677b09ba");
            case "display annotation", "exibir anotacoes" -> I18n.text("ui.e249cee220d4");
            case "display direction arrows", "exibir sentido dos movimentos" -> I18n.text("ui.92d9b4392453");
            case "boundary margin" -> I18n.text("ui.bb59dff2e0f8") + LINEAR;
            case "scale", "escala", "escalar" -> I18n.text("ui.abf69650ee82");
            case "offset x", "deslocamento x", "deslocar x" -> I18n.text("ui.ff163904e0e7") + LINEAR;
            default -> PanelHelpCatalog.help(context, name);
        };
    }

    private static String contextual(String context, String name) {
        if (context.equals("Geometry CNC Job") && name.equals("multi-depth"))
            return I18n.text("ui.4b26bfb73b97");
        if (List.of("NCC Tool", "Paint Tool").contains(context)) {
            switch (name) {
                case "method", "metodo" : return I18n.text("ui.f1e1a3617f23");
                case "margin (comum)", "margin", "margem" : return context.equals("NCC Tool")
                        ? I18n.text("ui.b9c15c9a9358") + LINEAR
                        : I18n.text("ui.fab7aa889bcb") + LINEAR;
                case "offset", "copper offset" : return I18n.text("ui.bd769a9b9060");
                case "selection" : return I18n.text("ui.c0dc07fd4dd3");
                case "operation" : return I18n.text("ui.be87706fb56a");
                case "milling type" : return I18n.text("ui.69fd75d1132d");
            }
        }
        if (context.equals("Isolation Tool")) {
            switch (name) {
                case "isolation type" : return I18n.text("ui.2860353558e0");
                case "excluir area" : return I18n.text("ui.1de3b2940ea0");
                case "follow" : return I18n.text("ui.11cb97544ccc");
                case "desenhar retangulo de excecao", "desenhar poligono de excecao" : return I18n.text("ui.ce5f1f7ee6d6");
            }
        }
        if (context.equals("Cutout Tool")) {
            return switch (name) {
                case "kind" -> I18n.text("ui.cf2d82536de7");
                case "convex shape" -> I18n.text("ui.1aa0f032c351");
                case "margin" -> I18n.text("ui.a784fc5e1345") + LINEAR;
                case "gap size" -> I18n.text("ui.e153bbf0f21e") + LINEAR;
                case "gaps" -> I18n.text("ui.2002c78dd5d8");
                case "tipo de gap" -> I18n.text("ui.b857c52a46ea");
                case "cut z" -> I18n.text("ui.d3ca7681ff0d") + LINEAR;
                case "thin depth" -> I18n.text("ui.7f1ec33d4658") + LINEAR;
                case "m-bites dia" -> I18n.text("ui.f71d028c7cb5") + LINEAR;
                case "m-bites spacing" -> I18n.text("ui.2e649de459f4") + LINEAR;
                case "adicionar gap por retangulo", "adicionar gap por poligono" -> I18n.text("ui.669b706abacc");
                default -> null;
            };
        }
        if (context.equals("Transform Tool")) {
            return switch (name) {
                case "angle" -> I18n.text("ui.137105ed53a8");
                case "angle x", "angle y" -> I18n.text("ui.a8ecb31dff59");
                case "factor x", "factor y" -> I18n.text("ui.00c9ede8c4ac");
                case "value x", "value y" -> I18n.text("ui.26cd317f9e31") + LINEAR;
                case "link" -> I18n.text("ui.269a72f32deb");
                default -> null;
            };
        }
        if (context.equals("Preferências")) {
            return switch (name) {
                case "tema" -> I18n.text("ui.20f84da739ba");
                case "passo x", "passo y" -> I18n.text("ui.4c021678cad6") + LINEAR;
                case "ativar snap na grade" -> I18n.text("ui.9bc5c6ee8750");
                case "usar passo x tambem em y" -> I18n.text("ui.910a65ecc56d");
                case "mostrar hud de coordenadas" -> I18n.text("ui.9442e92cf350");
                case "mostrar area a4" -> I18n.text("ui.3ea733c6ed68");
                default -> null;
            };
        }
        if (context.startsWith("Exportar ")) {
            return switch (name) {
                case "unidades" -> I18n.text("ui.57f8e10d16ad");
                case "digitos inteiros" -> I18n.text("ui.0215460711ca");
                case "digitos decimais" -> I18n.text("ui.41be4ff42d58");
                case "zeros" -> context.equals("Exportar Gerber")
                        ? I18n.text("ui.6b1165b1804d")
                        : I18n.text("ui.ba98fc666b44");
                case "formato" -> I18n.text("ui.896aea2964e5");
                case "slots" -> I18n.text("ui.8185359a4e03");
                default -> null;
            };
        }
        if (context.equals("Layer Color")) {
            return switch (name) {
                case "cor" -> I18n.text("ui.7dab125ea378");
                case "opacidade" -> I18n.text("ui.ae3b269ff29d");
                default -> null;
            };
        }
        return secondary(context, name);
    }

    private static String secondary(String context, String name) {
        if (context.equals("Panelize Tool")) return switch (name) {
            case "colunas", "linhas" -> I18n.text("ui.988650ae48d4");
            case "espacamento colunas", "espacamento linhas" -> I18n.text("ui.6fe0c601e0f2") + LINEAR;
            case "largura", "altura" -> I18n.text("ui.2ff9d207571f") + LINEAR;
            default -> null;
        };
        if (context.equals("Film Tool")) return switch (name) {
            case "dpi (png)" -> I18n.text("ui.686da55c093e");
            case "borda (negativo)" -> I18n.text("ui.a4a28c7edd4b") + LINEAR;
            case "espessura do traco" -> I18n.text("ui.5058f29a20d3") + LINEAR;
            case "pagina (pdf)" -> I18n.text("ui.8b6df7eabd30");
            case "referencia" -> I18n.text("ui.a61b5c516a4a");
            default -> null;
        };
        if (context.equals("QRCode Tool")) return switch (name) {
            case "tamanho da caixa" -> I18n.text("ui.f8c09afdaa53");
            case "versao (1-40)" -> I18n.text("ui.13ca79a66b25");
            case "borda (modulos)" -> I18n.text("ui.0fdf2fbd0630");
            case "correcao de erro" -> I18n.text("ui.f70dc3a27ddc");
            case "polaridade" -> I18n.text("ui.817a406a2107");
            default -> null;
        };
        if (context.equals("Extract Drills Tool") || context.equals("Punch Gerber Tool")) return switch (name) {
            case "diametro fixo" -> I18n.text("ui.e26c7731342b") + LINEAR;
            case "proporcao (%)" -> I18n.text("ui.22b14867f83e");
            case "anel - circular", "anel - oblongo", "anel - quadrado", "anel - retangular", "anel - outros" -> I18n.text("ui.28dfcb56ff82") + LINEAR;
            default -> null;
        };
        if (context.equals("Calculators")) return switch (name) {
            case "cut z" -> I18n.text("ui.438e6e0f7cbe") + LINEAR;
            case "tool diameter" -> I18n.text("ui.6908e296c590") + LINEAR;
            case "board length", "board width" -> I18n.text("ui.6d7dbdadbbfa");
            case "area" -> I18n.text("ui.63d63819cdf1");
            case "current density (asf)" -> I18n.text("ui.0596138aeb7d");
            case "copper growth (µm)" -> I18n.text("ui.319f48939af2");
            case "current (a)" -> I18n.text("ui.c4737c34a069");
            case "time (min)" -> I18n.text("ui.9710a4b7e7cd");
            default -> null;
        };
        if (context.equals("Etch Compensation Tool")) return switch (name) {
            case "espessura do cobre (um)", "deslocamento (um)" -> I18n.text("ui.85339b5bcafb");
            case "fator" -> I18n.text("ui.ce5dd3bb5a57");
            default -> null;
        };
        if (context.equals("Optimal Tool") && name.equals("precisao (casas)"))
            return I18n.text("ui.dc10d84e88fe");
        if (context.equals("Copper Thieving Tool") && name.equals("area minima"))
            return I18n.text("ui.f5047733c3e6");
        if (context.equals("Align Objects Tool") && name.equals("tipo"))
            return I18n.text("ui.12a1b2532b74");
        if (context.equals("Fiducials Tool")) return switch (name) {
            case "tamanho" -> I18n.text("ui.4394a22b898b") + LINEAR;
            case "margem (auto)" -> I18n.text("ui.d8262d718585") + LINEAR;
            case "espessura (cruz)" -> I18n.text("ui.eebae22479eb") + LINEAR;
            default -> null;
        };
        if (context.equals("Corner Markers Tool")) return switch (name) {
            case "espessura" -> I18n.text("ui.ca008894cb7f") + LINEAR;
            case "comprimento" -> I18n.text("ui.83c1f058f544") + LINEAR;
            case "margem" -> I18n.text("ui.501d1c1a18e9") + LINEAR;
            default -> null;
        };
        if (context.equals("SolderPaste Tool")) return switch (name) {
            case "z inicio da dispensa" -> I18n.text("ui.3808deb2c5f8") + LINEAR;
            case "z dispensa" -> I18n.text("ui.ed8faf1619bd") + LINEAR;
            case "z parada" -> I18n.text("ui.022ec261bee7") + LINEAR;
            case "z deslocamento", "z troca de bico" -> I18n.text("ui.dda0b3ab6375") + LINEAR;
            case "x,y troca de bico" -> I18n.text("ui.ef790b7b7aad") + LINEAR;
            case "avanco z dispensa" -> I18n.text("ui.42eb22956f37") + FEED;
            case "velocidade frente (rpm)", "velocidade reversa (rpm)" -> I18n.text("ui.14b80e57bd98");
            case "espera frente (s)", "espera reversa (s)" -> I18n.text("ui.ef98d4ba28d6");
            default -> null;
        };
        if (context.equals("Calibration Tool")) return switch (name) {
            case "z de verificacao" -> I18n.text("ui.e1b71b807c5d") + LINEAR;
            case "escala x", "escala y" -> I18n.text("ui.8018deac272c");
            case "inclinacao x", "inclinacao y" -> I18n.text("ui.d37535f5926d");
            case "zerar z antes" -> I18n.text("ui.1903c2e66213");
            default -> null;
        };
        if (context.equals("Milling Tool") && name.startsWith("tool diameter ("))
            return I18n.text("ui.7023f4980a16") + LINEAR;
        if (context.equals("Editor Geometry")) {
            String geometryHelp = switch (name) {
                case "fonte" -> I18n.text("ui.d17246b0329f");
                case "tamanho" -> I18n.text("ui.8ded6d60485b");
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
            case "buffer selecionadas", "buffer" -> I18n.text("ui.6a47efa6694c") + LINEAR;
            case "poligonizar selecionadas", "poligonizar" -> I18n.text("ui.090933a127bb");
            case "simplificar selecionadas", "simplificar" -> I18n.text("ui.3d2a54050498");
            case "fator" -> I18n.text("ui.327ac3d04cad");
            case "distancia", "tolerancia" -> I18n.text("ui.d5cc74bc2b81") + LINEAR;
            case "vertices" -> I18n.text("ui.5228a314e369");
            case "rotacao", "angulo inicial", "passo angular" -> I18n.text("ui.25d4948929b3");
            case "passo/raio" -> I18n.text("ui.17f4b57d2f30") + LINEAR;
            case "novo diametro" -> I18n.text("ui.a0467692519f") + LINEAR;
            case "passo x / y" -> I18n.text("ui.57352386d232") + LINEAR;
            default -> null;
        };
    }

    private static String key(String label) {
        if (label == null) return "";
        return Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim().replaceFirst(":$", "");
    }
}
