package org.flatcam.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Categorized preferences for settings actually consumed by FX. Pages retain unsaved drafts. */
final class PreferencesPane extends BorderPane {
    interface Host {
        ThemeOption theme();
        AppPreferences.PlotStatusSettings plotSettings();
        void applyTheme(ThemeOption theme);
        void applyPlot(AppPreferences.PlotStatusSettings settings);
    }
    record Section(String id, String label, String category, String group, String icon) {
        @Override public String toString() { return label; }
    }
    private final Host host;
    private final Function<String, Node> icons;
    private final TextField search = new TextField();
    private final TreeView<Section> navigation = new TreeView<>();
    private final BorderPane content = new BorderPane();
    private final Label result = new Label();
    private final Map<String, Node> pages = new LinkedHashMap<>();
    private final List<Section> sections = new ArrayList<>();
    private String selectedId = "general";

    PreferencesPane(Host host, Function<String, Node> icons) {
        this.host = host;
        this.icons = icons;
        setId("preferences-pane");
        getStyleClass().addAll("preferences-root", "tool-panel");
        setPadding(new Insets(14));
        Label title = new Label("Preferências"); title.getStyleClass().add("tool-title");
        search.setId("preferences-search"); search.setPromptText("Buscar configuração…");
        search.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(search, Priority.ALWAYS);
        Button clear = new Button("Limpar"); clear.setId("preferences-search-clear");
        clear.setOnAction(e -> search.clear());
        help(search, "Buscar preferências", "Filtra seções e campos pelo nome, descrição ou chave. A busca ignora acentos e maiúsculas; todas as palavras devem aparecer. Não descarta nem salva rascunhos.");
        help(clear, "Limpar busca", "Mostra novamente as categorias e os campos, mantendo os rascunhos desta aba.");
        VBox header = new VBox(8, title, new HBox(8, search, clear), result);
        header.setPadding(new Insets(0, 0, 12, 0)); setTop(header);
        navigation.setId("preferences-navigation"); navigation.setShowRoot(false);
        navigation.setMinWidth(145); navigation.setPrefWidth(205);
        help(navigation, "Categorias de preferências", "Escolha uma seção para editar. Os rascunhos são mantidos ao navegar nesta aba; use o botão de salvar da seção. A busca também filtra a árvore.");
        navigation.setCellFactory(v -> new TreeCell<>() {
            @Override protected void updateItem(Section item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
                setGraphic(empty || item == null ? null : icons.apply(item.icon()));
            }
        });
        content.setMinWidth(0);
        SplitPane split = new SplitPane(navigation, content);
        split.setId("preferences-split"); split.setDividerPositions(.24);
        setCenter(split);
        sections.add(new Section("general", "Geral", null, null, "settings18.png"));
        sections.add(new Section("plot", "Plot Area", null, null, "replot32.png"));
        for (String group : ToolDefaults.byTool().keySet()) {
            String category = switch (group) {
                case "Importação: Gerber", "Gerber: Exibição", "Gerber: Avançadas" -> "Gerber";
                case "Importação: Excellon", "Excellon: Exibição" -> "Excellon";
                case "Editor Geometry", "Geometry: Exibição" -> "Geometry";
                case "CNC Job (geral)", "Geometry CNC", "Drilling", "Excellon Milling" -> "CNC Job";
                default -> "Ferramentas";
            };
            String icon = switch (category) {
                case "Gerber" -> "flatcam_icon32.png";
                case "Excellon" -> "drill32.png";
                case "Geometry" -> "geometry32.png";
                case "CNC Job" -> "cnc32.png";
                default -> java.util.stream.Stream.of(LegacyUiManifest.TOOLS_PREPARATION, LegacyUiManifest.TOOLS_CAM, LegacyUiManifest.TOOLS_UTILITIES)
                        .flatMap(List::stream).filter(c -> c.label().equals(group) || c.label().equals(group + " Tool"))
                        .map(LegacyUiManifest.Command::icon).findFirst().orElse("settings18.png");
            };
            sections.add(new Section(group, group, category, group, icon));
        }
        navigation.getSelectionModel().selectedItemProperty().addListener((o, before, now) -> {
            if (now == null) return;
            if (!now.isLeaf()) {
                now.setExpanded(true); navigation.getSelectionModel().select(now.getChildren().getFirst()); return;
            }
            selectedId = now.getValue().id(); show(now.getValue());
        });
        search.textProperty().addListener((o, before, now) -> filterNavigation());
        filterNavigation();
    }

    List<Section> sections() { return List.copyOf(sections); }

    private boolean matches(Section section) {
        if (section.group() == null) return PreferencesSearch.matches(search.getText(), section.label(),
                section.id().equals("general") ? "aparência tema branco preto gelo" : "grade snap passo X Y eixos HUD coordenadas área A4");
        return ToolDefaults.byTool().get(section.group()).stream().anyMatch(s -> PreferencesSearch.matches(search.getText(),
                s.tool(), s.label(), s.key(), ToolDefaultsDescriptions.text(s, true), ToolDefaultsDescriptions.text(s, false)));
    }

    private void filterNavigation() {
        String previous = selectedId;
        TreeItem<Section> root = new TreeItem<>(); root.setExpanded(true);
        Map<String, TreeItem<Section>> categories = new LinkedHashMap<>();
        // Fixed category order, independent of the tool registry's definition order.
        for (String category : List.of("Gerber", "Excellon", "Geometry", "CNC Job", "Ferramentas")) {
            String icon = switch (category) {
                case "Gerber" -> "flatcam_icon32.png";
                case "Excellon" -> "drill32.png";
                case "Geometry" -> "geometry32.png";
                case "CNC Job" -> "cnc32.png";
                default -> "settings18.png";
            };
            categories.put(category, new TreeItem<>(new Section("category:" + category, category, null, null, icon)));
        }
        List<TreeItem<Section>> leaves = new ArrayList<>();
        for (Section section : sections) if (matches(section)) {
            TreeItem<Section> leaf = new TreeItem<>(section); leaves.add(leaf);
            if (section.category() == null) root.getChildren().add(leaf);
            else categories.get(section.category()).getChildren().add(leaf);
        }
        categories.values().stream().filter(c -> !c.getChildren().isEmpty()).forEach(c -> { c.setExpanded(true); root.getChildren().add(c); });
        navigation.setRoot(root);
        result.setText(search.getText().isBlank() ? "Escolha uma seção. Use os botões dela para confirmar as alterações."
                : leaves.size() + " seção(ões) encontrada(s). Os rascunhos permanecem nesta aba.");
        result.setWrapText(true);
        if (leaves.isEmpty()) {
            Label empty = new Label("Nenhuma configuração encontrada. Tente outro termo ou limpe a busca."); empty.setWrapText(true);
            content.setCenter(empty);
        } else navigation.getSelectionModel().select(leaves.stream().filter(l -> l.getValue().id().equals(previous)).findFirst().orElse(leaves.getFirst()));
    }

    private void show(Section section) {
        Node page = pages.computeIfAbsent(section.id(), id -> createPage(section));
        content.setCenter(page);
    }

    private Node createPage(Section section) {
        Label heading = new Label(section.category() == null ? section.label() : section.category() + " › " + section.label());
        heading.getStyleClass().add("tool-title");
        Label description = new Label(section.group() == null
                ? "Aplique para salvar e atualizar a interface. Restaurar prepara os valores de fábrica; aplicar confirma a mudança."
                : "Padrões para as próximas operações. Alternar seções, buscar ou escolher mm/in mantém os rascunhos; salvar vale somente para esta seção.");
        description.setWrapText(true);
        Node form = section.group() != null ? ToolDefaultsPane.build(List.of(section.group()), search.textProperty())
                : section.id().equals("general") ? appearance() : plot();
        VBox page = new VBox(12, heading, description, new Separator(), form);
        page.setMinWidth(0); page.setPadding(new Insets(12)); page.getStyleClass().add("preferences-page");
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true); scroll.setMinWidth(0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private Node appearance() {
        ComboBox<ThemeOption> theme = new ComboBox<>(); theme.setId("preferences-theme");
        theme.getItems().setAll(ThemeOption.values()); theme.setValue(host.theme());
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ThemeOption option) { return option == null ? "" : option.label(); }
            @Override public ThemeOption fromString(String value) { return theme.getValue(); }
        });
        help(theme, "Tema da interface", "Escolha Branco, Preto, Branco gelo ou Preto gelo. Aplicar atualiza a janela e salva a escolha; não altera o projeto.");
        Label label = new Label("Tema:"); label.setLabelFor(theme);
        Label feedback = new Label(); feedback.setWrapText(true);
        Button apply = button("Aplicar e salvar", "preferences-general-apply", () -> {
            host.applyTheme(theme.getValue()); feedback.setText("Tema aplicado e salvo.");
        }, "Aplica e salva somente o tema escolhido. Não salva padrões das outras seções.");
        Button reset = button("Restaurar padrão", "preferences-general-reset", () -> {
            theme.setValue(ThemeOption.ICE_LIGHT); feedback.setText("Branco gelo preparado. Clique em Aplicar e salvar para confirmar.");
        }, "Prepara o tema Branco gelo, padrão inicial do FX. A mudança só é salva ao aplicar.");
        return new VBox(12, new HBox(10, label, theme), new FlowPane(8, 8, apply, reset), feedback);
    }

    private Node plot() {
        var saved = host.plotSettings();
        CheckBox snap = check("Ativar snap na grade", "preferences-grid-snap", saved.gridSnap(), "Prende os pontos à grade quando ativo; é independente da visibilidade das linhas da grade.");
        CheckBox grid = check("Mostrar grade visual", "preferences-grid-visible", saved.gridVisible(), "Mostra as linhas da grade sem alterar o estado do snap.");
        CheckBox linked = check("Usar passo X também em Y", "preferences-grid-linked", saved.gridLinked(), "Mantém os passos X e Y iguais. Desmarque para editar Y separadamente.");
        TextField x = new TextField(Double.toString(saved.gridX())); x.setId("preferences-grid-x"); x.setPrefColumnCount(8);
        TextField y = new TextField(Double.toString(saved.gridY())); y.setId("preferences-grid-y"); y.setPrefColumnCount(8);
        y.disableProperty().bind(linked.selectedProperty());
        help(x, "Passo X", "Espaçamento horizontal da grade nas unidades atuais do Plot. Use um número positivo e finito; aceita ponto ou vírgula decimal.");
        help(y, "Passo Y", "Espaçamento vertical da grade. É usado quando os passos não estão vinculados e deve ser positivo e finito.");
        CheckBox axis = check("Mostrar eixos", "preferences-axis", saved.axisVisible(), "Mostra os eixos de origem no Plot.");
        CheckBox hud = check("Mostrar HUD de coordenadas", "preferences-hud", saved.hudVisible(), "Mostra X, Y, Dx e Dy no Plot.");
        CheckBox workspace = check("Mostrar área A4", "preferences-workspace", saved.workspaceVisible(), "Mostra a referência de página A4; não recorta nem muda a geometria.");
        GridPane steps = new GridPane(); steps.setHgap(10); steps.setVgap(8);
        Label lx = new Label("Passo X:"); lx.setLabelFor(x); Label ly = new Label("Passo Y:"); ly.setLabelFor(y);
        steps.addRow(0, lx, x); steps.addRow(1, ly, y);
        Label feedback = new Label(); feedback.setId("preferences-plot-feedback"); feedback.setWrapText(true);
        Button apply = button("Aplicar e salvar", "preferences-plot-apply", () -> {
            try {
                double dx = positive(x.getText(), "Passo X"); double dy = linked.isSelected() ? dx : positive(y.getText(), "Passo Y");
                host.applyPlot(new AppPreferences.PlotStatusSettings(snap.isSelected(), grid.isSelected(), dx, dy,
                        linked.isSelected(), axis.isSelected(), hud.isSelected(), workspace.isSelected()));
                feedback.setText("Preferências do Plot aplicadas e salvas.");
            } catch (IllegalArgumentException invalid) { feedback.setText(invalid.getMessage()); }
        }, "Valida os passos antes de aplicar e salvar os controles do Plot. Não altera objetos ou padrões das ferramentas.");
        Button reset = button("Restaurar padrão", "preferences-plot-reset", () -> {
            snap.setSelected(true); grid.setSelected(true); x.setText("1.0"); y.setText("1.0"); linked.setSelected(true);
            axis.setSelected(true); hud.setSelected(true); workspace.setSelected(false);
            feedback.setText("Padrões do Plot preparados. Clique em Aplicar e salvar para confirmar.");
        }, "Prepara grade e snap ativos, passo 1, eixos e HUD visíveis e área A4 oculta. Só salva ao aplicar.");
        return new VBox(10, snap, grid, linked, steps, axis, hud, workspace, new FlowPane(8, 8, apply, reset), feedback);
    }

    private static double positive(String text, String label) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isFinite(value) && value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(label + " deve ser um número positivo e finito.");
    }
    private static void help(Node node, String title, String text) { ToolDescriptions.apply(node, title, text); }
    private static CheckBox check(String label, String id, boolean selected, String description) {
        CheckBox box = new CheckBox(label); box.setId(id); box.setSelected(selected); box.setWrapText(true); help(box, label, description); return box;
    }
    private static Button button(String label, String id, Runnable action, String description) {
        Button button = new Button(label); button.setId(id); button.setOnAction(e -> action.run());
        if (id.endsWith("-apply")) button.getStyleClass().add("primary-action");
        help(button, label, description); return button;
    }
}
