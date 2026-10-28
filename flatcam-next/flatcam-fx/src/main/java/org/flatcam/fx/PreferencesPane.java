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
        default boolean splashEnabled() { return AppPreferences.loadSplashScreen(); }
        default void applySplash(boolean enabled) { AppPreferences.saveSplashScreen(enabled); }
        UiLanguage language();
        void applyLanguage(UiLanguage language);
        UiNumbers.Settings numericSettings();
        void applyNumericSettings(UiNumbers.Settings settings);
    }
    record Section(String id, String label, String category, String group, String icon) {
        @Override public String label() { return I18n.caption(label); }
        @Override public String toString() { return label(); }
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
        Label title = new Label(I18n.text("ui.6706dbe8b723")); title.getStyleClass().add("tool-title");
        search.setId("preferences-search"); search.setPromptText(I18n.text("ui.5176d0b93e29"));
        search.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(search, Priority.ALWAYS);
        Button clear = new Button(I18n.text("ui.5585ca453806")); clear.setId("preferences-search-clear");
        clear.setOnAction(e -> search.clear());
        help(search, I18n.text("ui.4781458b5409"), I18n.text("ui.7f0e916515a2"));
        help(clear, I18n.text("ui.9f3ebbde555a"), I18n.text("ui.867059d7ad63"));
        VBox header = new VBox(8, title, new HBox(8, search, clear), result);
        header.setPadding(new Insets(0, 0, 12, 0)); setTop(header);
        navigation.setId("preferences-navigation"); navigation.setShowRoot(false);
        navigation.setMinWidth(145); navigation.setPrefWidth(205);
        help(navigation, I18n.text("ui.dd45337f9b0a"), I18n.text("ui.bc0900fb7f6f"));
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
        sections.add(new Section("general", I18n.text("ui.2130a3b5e09e"), null, null, "settings18.png"));
        sections.add(new Section("numbers", I18n.text("preferences.numbers.title"), null, null, "settings18.png"));
        sections.add(new Section("plot", "Plot Area", null, null, "replot32.png"));
        for (String group : ToolDefaults.byTool().keySet()) {
            String category = switch (group) {
                case "Importação: Gerber", "Gerber: Exibição", "Gerber: Avançadas", "Editor Gerber", "Exportação: Gerber" -> "Gerber";
                case "Importação: Excellon", "Excellon: Exibição", "Editor Excellon", "Exportação: Excellon" -> "Excellon";
                case "Editor Geometry", "Geometry: Exibição" -> "Geometry";
                case "CNC Job (geral)", "Geometry CNC", "Drilling", "Excellon Milling", "CNC Job: Exibição" -> "CNC Job";
                default -> I18n.text("ui.b96a67bfde3e");
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
                section.id().equals("general") ? I18n.text("ui.4faae991c5c8")
                        : section.id().equals("numbers") ? I18n.text("preferences.numbers.search") : I18n.text("ui.5862761850cf"));
        return ToolDefaults.byTool().get(section.group()).stream().anyMatch(s -> PreferencesSearch.matches(search.getText(),
                s.tool(), s.label(), s.key(), ToolDefaultsDescriptions.text(s, true), ToolDefaultsDescriptions.text(s, false)));
    }

    private void filterNavigation() {
        String previous = selectedId;
        TreeItem<Section> root = new TreeItem<>(); root.setExpanded(true);
        Map<String, TreeItem<Section>> categories = new LinkedHashMap<>();
        // Fixed category order, independent of the tool registry's definition order.
        for (String category : List.of("Gerber", "Excellon", "Geometry", "CNC Job", I18n.text("ui.b96a67bfde3e"))) {
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
        result.setText(search.getText().isBlank() ? I18n.text("ui.cb92eae88787")
                : leaves.size() + I18n.text("ui.3a2a4f3cfd5a"));
        result.setWrapText(true);
        if (leaves.isEmpty()) {
            Label empty = new Label(I18n.text("ui.7bd617f3cf42")); empty.setWrapText(true);
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
                ? I18n.text(section.id().equals("numbers") ? "preferences.numbers.description" : "ui.d4c406979fce")
                : I18n.text("ui.1a7db3f67ad0"));
        description.setWrapText(true);
        Node form = section.group() != null ? ToolDefaultsPane.build(List.of(section.group()), search.textProperty())
                : section.id().equals("general") ? appearance() : section.id().equals("numbers") ? numbers() : plot();
        VBox page = new VBox(12, heading, description, new Separator(), form);
        page.setMinWidth(0); page.setPadding(new Insets(12)); page.getStyleClass().add("preferences-page");
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true); scroll.setMinWidth(0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private Node numbers() {
        UiNumbers.Settings saved = host.numericSettings();
        ComboBox<String> units = new ComboBox<>(); units.getItems().setAll("MM","IN"); units.setValue(saved.initialUnits());
        units.setId("preferences-initial-units");
        units.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) {
                return value == null ? "" : I18n.text(value.equals("IN") ? "preferences.numbers.in" : "preferences.numbers.mm");
            }
            @Override public String fromString(String value) { return units.getValue(); }
        });
        TextField metric = new TextField(Integer.toString(saved.metricDecimals())); metric.setId("preferences-decimals-mm");
        TextField inches = new TextField(Integer.toString(saved.inchDecimals())); inches.setId("preferences-decimals-in");
        for (TextField field : List.of(metric,inches)) { field.setPrefColumnCount(4); field.setMaxWidth(100); }
        help(units,I18n.text("preferences.numbers.units"),I18n.text("preferences.numbers.units.help"));
        help(metric,I18n.text("preferences.numbers.metric"),I18n.text("preferences.numbers.precision.help"));
        help(inches,I18n.text("preferences.numbers.inch"),I18n.text("preferences.numbers.precision.help"));
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10);
        for (var row : List.of(new Object[]{"preferences.numbers.units",units},new Object[]{"preferences.numbers.metric",metric},
                new Object[]{"preferences.numbers.inch",inches})) {
            Label label = new Label(I18n.text((String)row[0])+":"); label.setWrapText(true); label.setMinWidth(0);
            label.setLabelFor((Node)row[1]); grid.addRow(grid.getRowCount(),label,(Node)row[1]);
        }
        Label scope = new Label(I18n.text("preferences.numbers.scope")); scope.setWrapText(true);
        Label feedback = new Label(); feedback.setWrapText(true); feedback.setId("preferences-numbers-feedback");
        Button apply = button(I18n.text("ui.7a3e61cf6af5"),"preferences-numbers-apply",() -> {
            try {
                int mm = precision(metric,I18n.text("preferences.numbers.metric"));
                int in = precision(inches,I18n.text("preferences.numbers.inch"));
                var next = new UiNumbers.Settings(units.getValue(),mm,in);
                host.applyNumericSettings(next);
                feedback.setText(I18n.text(next.equals(UiNumbers.settings()) ? "preferences.numbers.saved" : "preferences.numbers.restart"));
            } catch (IllegalArgumentException invalid) { feedback.setText(invalid.getMessage()); }
        },I18n.text("preferences.numbers.apply.help"));
        Button reset = button(I18n.text("ui.ae9f4b2807bc"),"preferences-numbers-reset",() -> {
            units.setValue("MM"); metric.setText("4"); inches.setText("4");
            feedback.setText(I18n.text("preferences.numbers.reset"));
        },I18n.text("preferences.numbers.reset.help"));
        return new VBox(12,grid,scope,new FlowPane(8,8,apply,reset),feedback);
    }

    private static int precision(TextField field, String label) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (UiNumbers.validPrecision(value)) return value;
        } catch (NumberFormatException invalid) { /* Keep the draft, including invalid text. */ }
        field.requestFocus();
        throw new IllegalArgumentException(I18n.text("preferences.numbers.invalid").formatted(label));
    }

    private Node appearance() {
        ComboBox<UiLanguage> language = new ComboBox<>(); language.setId("preferences-language");
        language.getItems().setAll(UiLanguage.values()); language.setValue(host.language());
        help(language, I18n.text("preferences.language"), I18n.text("preferences.language.help"));
        Label languageLabel = new Label(I18n.text("preferences.language") + ":"); languageLabel.setLabelFor(language);
        ComboBox<ThemeOption> theme = new ComboBox<>(); theme.setId("preferences-theme");
        theme.getItems().setAll(ThemeOption.values()); theme.setValue(host.theme());
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ThemeOption option) { return option == null ? "" : option.label(); }
            @Override public ThemeOption fromString(String value) { return theme.getValue(); }
        });
        help(theme, I18n.text("ui.3b2740389fef"), I18n.text("ui.73f98c17fc9c"));
        Label label = new Label(I18n.text("ui.30ffa5818ad3")); label.setLabelFor(theme);
        CheckBox splash=check(I18n.text("ui.b4bb331f6ec1"), "preferences-splash",host.splashEnabled(),
                I18n.text("ui.c8fbe4790d9e"));
        Label feedback = new Label(); feedback.setWrapText(true);
        Button apply = button(I18n.text("ui.7a3e61cf6af5"), "preferences-general-apply", () -> {
            host.applyTheme(theme.getValue()); host.applySplash(splash.isSelected());
            host.applyLanguage(language.getValue());
            feedback.setText(language.getValue() != I18n.language() ? I18n.text("preferences.language.restart")
                    : I18n.text("ui.4fcb08961129"));
        }, I18n.text("ui.345b2ef2735e"));
        Button reset = button(I18n.text("ui.ae9f4b2807bc"), "preferences-general-reset", () -> {
            theme.setValue(ThemeOption.DEFAULT); splash.setSelected(true);
            language.setValue(UiLanguage.PT_BR);
            feedback.setText(I18n.text("ui.337a057ee4cf"));
        }, I18n.text("ui.4e844b9f37ac"));
        return new VBox(12, new HBox(10, languageLabel, language), new HBox(10, label, theme), splash, new FlowPane(8, 8, apply, reset), feedback);
    }

    private Node plot() {
        var saved = host.plotSettings();
        CheckBox snap = check(I18n.text("ui.746c54313d06"), "preferences-grid-snap", saved.gridSnap(), I18n.text("ui.37a438ebd3a8"));
        CheckBox grid = check(I18n.text("ui.676825bb9d77"), "preferences-grid-visible", saved.gridVisible(), I18n.text("ui.83d92aa62877"));
        CheckBox linked = check(I18n.text("ui.da65c5d686a5"), "preferences-grid-linked", saved.gridLinked(), I18n.text("ui.174bf2873510"));
        TextField x = new TextField(Double.toString(saved.gridX())); x.setId("preferences-grid-x"); x.setPrefColumnCount(8);
        TextField y = new TextField(Double.toString(saved.gridY())); y.setId("preferences-grid-y"); y.setPrefColumnCount(8);
        y.disableProperty().bind(linked.selectedProperty());
        help(x, I18n.text("ui.5eddb268193e"), I18n.text("ui.55cfc189b641"));
        help(y, I18n.text("ui.e992db09c000"), I18n.text("ui.4ec95b8ddd90"));
        CheckBox axis = check(I18n.text("ui.ab85d54a5fc2"), "preferences-axis", saved.axisVisible(), I18n.text("ui.5fd0925c17db"));
        CheckBox hud = check(I18n.text("ui.ad4e9ea0f9f2"), "preferences-hud", saved.hudVisible(), I18n.text("ui.cdc661efbe5d"));
        CheckBox workspace = check(I18n.text("ui.4d785fa8c5ab"), "preferences-workspace", saved.workspaceVisible(), I18n.text("ui.ada5ec8f9f35"));
        GridPane steps = new GridPane(); steps.setHgap(10); steps.setVgap(8);
        Label lx = new Label(I18n.text("ui.407d9bb4c286")); lx.setLabelFor(x); Label ly = new Label(I18n.text("ui.eb8e36212411")); ly.setLabelFor(y);
        steps.addRow(0, lx, x); steps.addRow(1, ly, y);
        Label feedback = new Label(); feedback.setId("preferences-plot-feedback"); feedback.setWrapText(true);
        Button apply = button(I18n.text("ui.7a3e61cf6af5"), "preferences-plot-apply", () -> {
            try {
                double dx = positive(x.getText(), I18n.text("ui.5eddb268193e")); double dy = linked.isSelected() ? dx : positive(y.getText(), I18n.text("ui.e992db09c000"));
                host.applyPlot(new AppPreferences.PlotStatusSettings(snap.isSelected(), grid.isSelected(), dx, dy,
                        linked.isSelected(), axis.isSelected(), hud.isSelected(), workspace.isSelected()));
                feedback.setText(I18n.text("ui.1ae4f2c358c2"));
            } catch (IllegalArgumentException invalid) { feedback.setText(invalid.getMessage()); }
        }, I18n.text("ui.ad81f23c9168"));
        Button reset = button(I18n.text("ui.ae9f4b2807bc"), "preferences-plot-reset", () -> {
            snap.setSelected(true); grid.setSelected(true); x.setText("1.0"); y.setText("1.0"); linked.setSelected(true);
            axis.setSelected(true); hud.setSelected(true); workspace.setSelected(false);
            feedback.setText(I18n.text("ui.ab96a7082c86"));
        }, I18n.text("ui.a86d14a823f1"));
        return new VBox(10, snap, grid, linked, steps, axis, hud, workspace, new FlowPane(8, 8, apply, reset), feedback);
    }

    private static double positive(String text, String label) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isFinite(value) && value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(label + I18n.text("ui.ccf894c2fbbd"));
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
