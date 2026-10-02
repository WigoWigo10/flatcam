package org.flatcam.fx;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.project.ToolsDatabase;
import org.json.JSONObject;

/** Python ToolsDB2 layout, with responsive parameter cards and transactional field edits. */
final class ToolsDatabasePanel extends BorderPane {
    record Row(int id, String name, double diameter, String target) { }
    private ToolsDatabase database = new ToolsDatabase();
    private JSONObject saved = new JSONObject();
    private Path path;
    private final JobExecutor jobs;
    private final Supplier<Window> owner;
    private final Consumer<Path> remember;
    private final Function<String, Node> icons;
    private final BooleanProperty dirty = new SimpleBooleanProperty();
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final TableView<Row> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final Label location = new Label("Nova base — ainda nao salva");
    private final Label feedback = new Label();
    private final Map<ToolsDatabaseFields.Field, Control> controls = new LinkedHashMap<>();
    private final Map<ToolsDatabaseFields.Group, TitledPane> groups = new EnumMap<>(ToolsDatabaseFields.Group.class);
    private final Set<ToolsDatabaseFields.Field> changed = new HashSet<>();
    private Integer editing;
    private boolean loading;

    ToolsDatabasePanel(JobExecutor jobs, Supplier<Window> owner, Consumer<Path> remember, Function<String, Node> icons) {
        this.jobs = jobs; this.owner = owner; this.remember = remember; this.icons = icons;
        setId("tools-database"); getStyleClass().add("tool-panel"); setPadding(new Insets(12));
        Label title = new Label("Tools Database", icons.apply("search_db32.png"));
        title.getStyleClass().add("tool-title");
        ToolsDatabaseDescriptions.apply(title, "Tools Database", "Biblioteca reutilizável de ferramentas, compatível com .FlatDB do Python. Edite uma ferramenta por vez e use Save DB para persistir as alterações.");
        location.setWrapText(true); location.setId("db-location");
        ToolsDatabaseDescriptions.apply(location, "Arquivo da base", "Mostra o arquivo associado à biblioteca. Nova base indica que ainda não há destino de gravação. O último arquivo importado ou salvo é lembrado entre sessões.");
        Label scope = new Label("Transferencia CAM: Isolation, NCC e Drilling. Os demais parametros sao editaveis e preservados no banco.");
        scope.setWrapText(true); scope.setMinWidth(0);
        scope.setId("db-scope");
        ToolsDatabaseDescriptions.apply(scope, "Integração CAM e unidades", "Isolation, NCC e Drilling usam os parâmetros suportados da base aberta. Os demais campos são preservados, mas nem todos são transferidos para o CAM. A base não declara unidades: use valores na unidade do trabalho.");
        VBox header = new VBox(6, title, location, scope); header.setPadding(new Insets(0, 0, 10, 0)); setTop(header);
        search.setId("db-search"); search.setPromptText("Buscar nome, ID ou diametro...");
        ToolsDatabaseDescriptions.apply(search, "Buscar ferramentas", "Filtra a lista por trecho do nome, ID ou diâmetro. Não remove ferramentas da base. Limpe o texto para voltar a mostrar os resultados. Atalho: Ctrl+F.");
        filter.setId("db-filter"); filter.getItems().add("Todas as operacoes"); filter.getItems().addAll(ToolsDatabase.TARGETS);
        filter.getSelectionModel().selectFirst(); filter.setMaxWidth(Double.MAX_VALUE);
        ToolsDatabaseDescriptions.apply(filter, "Filtrar por operação", "Mostra as ferramentas da operação escolhida ou de todas as operações. O filtro é combinado com a busca e não altera nem exclui os registros da base.");
        table.setId("db-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ToolsDatabaseDescriptions.apply(table, "Ferramentas da base", "ID identifica o registro; Tool Name é o nome; Dia é o diâmetro na unidade do trabalho. Selecione uma linha para editar. Use Ctrl/Shift para selecionar várias e copiar/excluir; o botão direito também oferece esses comandos.");
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<Row, Number> id = new TableColumn<>("ID"); id.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().id()));
        id.setMinWidth(36); id.setMaxWidth(65);
        TableColumn<Row, String> name = new TableColumn<>("Tool Name"); name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        TableColumn<Row, Number> dia = new TableColumn<>("Dia"); dia.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().diameter()));
        dia.setMinWidth(55); dia.setMaxWidth(95);
        table.getColumns().add(id); table.getColumns().add(name); table.getColumns().add(dia);
        table.setPlaceholder(new Label("Nenhuma ferramenta. Use Adicionar ferramenta ou Import DB."));
        table.setContextMenu(contextMenu());
        VBox left = new VBox(8, search, filter, table); left.setMinWidth(180); VBox.setVgrow(table, Priority.ALWAYS);
        FlowPane cards = new FlowPane(12, 12); cards.setPadding(new Insets(0, 6, 6, 6));
        cards.setAlignment(Pos.TOP_LEFT); cards.setRowValignment(VPos.TOP);
        for (var group : ToolsDatabaseFields.Group.values()) {
            GridPane fields = new GridPane(); fields.setHgap(8); fields.setVgap(7); fields.setPadding(new Insets(10));
            ColumnConstraints label = new ColumnConstraints(); label.setMinWidth(95);
            ColumnConstraints value = new ColumnConstraints(); value.setHgrow(Priority.ALWAYS); value.setMinWidth(90);
            fields.getColumnConstraints().addAll(label, value);
            int row = 0;
            for (var field : ToolsDatabaseFields.ALL) if (field.group() == group) {
                Control control = createControl(field); controls.put(field, control);
                control.setId("db-" + field.key());
                String help = ToolsDatabaseDescriptions.fieldText(field);
                ToolsDatabaseDescriptions.apply(control, group.label + " · " + field.label(), help);
                control.setMaxWidth(Double.MAX_VALUE); GridPane.setHgrow(control, Priority.ALWAYS);
                Label fieldLabel = new Label(field.label() + ":"); fieldLabel.setId("db-label-" + field.key());
                // Labels remain enabled, so help is accessible when an optional input is disabled.
                ToolsDatabaseDescriptions.apply(fieldLabel, group.label + " · " + field.label(), help);
                fields.addRow(row++, fieldLabel, control);
            }
            TitledPane pane = new TitledPane(group.label, fields); pane.setGraphic(icons.apply(group.icon));
            pane.setId("db-group-" + group.name().toLowerCase(Locale.ROOT));
            ToolsDatabaseDescriptions.apply(pane, group.label, ToolsDatabaseDescriptions.groupText(group) + " Clique no título para recolher ou expandir a seção.");
            pane.setPrefWidth(310); pane.setMinWidth(275); pane.setExpanded(true);
            groups.put(group, pane);
        }
        VBox description = new VBox(10);
        for (var group : List.of(ToolsDatabaseFields.Group.DESCRIPTION, ToolsDatabaseFields.Group.ISOLATION,
                ToolsDatabaseFields.Group.PAINT, ToolsDatabaseFields.Group.NCC, ToolsDatabaseFields.Group.CUTOUT))
            description.getChildren().add(groups.get(group));
        description.setPrefWidth(310);
        // Stack the two machining cards in the second column, so Milling does not
        // wrap below the entire (long) General description/CAM column on small plots.
        VBox machining = new VBox(10, groups.get(ToolsDatabaseFields.Group.DRILLING), groups.get(ToolsDatabaseFields.Group.MILLING));
        machining.setPrefWidth(310); cards.getChildren().addAll(description, machining);
        ScrollPane parameters = new ScrollPane(cards); parameters.setFitToWidth(true); parameters.setMinWidth(0);
        SplitPane split = new SplitPane(left, parameters); split.setDividerPositions(0.27);
        split.disableProperty().bind(busy);
        setCenter(split);
        Button add = button("Adicionar ferramenta", "plus16.png", "db-add", this::add);
        Button copy = button("Copiar", "copy32.png", "db-copy", this::duplicate);
        Button remove = button("Excluir", "trash16.png", "db-delete", this::delete);
        Button apply = button("Aplicar parametros", "apply32.png", "db-apply", () -> applyDraft());
        Button create = button("Nova base", "file32.png", "db-new", this::newDatabase);
        Button load = button("Import DB", "import.png", "db-import", this::importDatabase);
        Button export = button("Export DB", "export.png", "db-export", () -> save(true));
        Button save = button("Save DB", "save_as.png", "db-save", () -> save(false)); save.getStyleClass().add("primary-action");
        FlowPane actions = new FlowPane(8, 8, add, copy, remove, apply, create, load, export, save);
        actions.disableProperty().bind(busy);
        feedback.setId("db-feedback"); feedback.setWrapText(true); feedback.setMinWidth(0); feedback.setMaxWidth(Double.MAX_VALUE);
        ProgressIndicator progress = new ProgressIndicator(); progress.setMaxSize(18, 18);
        progress.visibleProperty().bind(busy); progress.managedProperty().bind(busy);
        HBox message = new HBox(8, progress, feedback); HBox.setHgrow(feedback, Priority.ALWAYS);
        VBox footer = new VBox(8, actions, message); footer.setPadding(new Insets(10, 0, 0, 0)); setBottom(footer);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
            if (loading) return;
            if (!applyDraft(false)) { refresh(editing); return; }
            loadEditor(selected == null ? null : selected.id());
        });
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Row>) change -> updateDependencies());
        search.textProperty().addListener((o, a, b) -> refilter());
        filter.valueProperty().addListener((o, a, b) -> refilter());
        setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) { save(false); event.consume(); }
            else if (event.isShortcutDown() && event.getCode() == KeyCode.F) { search.requestFocus(); event.consume(); }
            else if (event.getCode() == KeyCode.DELETE && event.getTarget() == table) { delete(); event.consume(); }
        });
        loadEditor(null);
    }

    private Button button(String label, String icon, String id, Runnable action) {
        Button button = new Button(label, icons.apply(icon)); button.setId(id);
        if (ToolsDatabaseDescriptions.hasActionHelp(label))
            ToolsDatabaseDescriptions.apply(button, label, ToolsDatabaseDescriptions.actionText(label));
        button.setOnAction(e -> { if (!busy.get()) action.run(); }); return button;
    }
    private ContextMenu contextMenu() {
        ContextMenu menu = new ContextMenu();
        for (var action : List.of(button("Adicionar ferramenta", "plus16.png", "", this::add),
                button("Copiar", "copy32.png", "", this::duplicate), button("Excluir", "trash16.png", "", this::delete))) {
            MenuItem item = new MenuItem(action.getText(), action.getGraphic());
            item.setOnAction(e -> action.fire()); menu.getItems().add(item);
        }
        menu.setAutoHide(true); return menu;
    }
    private Control createControl(ToolsDatabaseFields.Field field) {
        if (!field.choices().isEmpty()) {
            ComboBox<ToolsDatabaseFields.Choice> combo = new ComboBox<>(FXCollections.observableArrayList(field.choices()));
            if (field.key().equals("tools_paint_method")) combo.setCellFactory(list -> new ListCell<>() {
                @Override protected void updateItem(ToolsDatabaseFields.Choice item, boolean empty) {
                    super.updateItem(item, empty); setText(empty || item == null ? null : item.label());
                    // Python exposes Laser_lines in the inventory, but disables it.
                    setDisable(!empty && item != null && Integer.valueOf(3).equals(item.value()));
                }
            });
            combo.valueProperty().addListener((o, a, b) -> fieldChanged(field)); return combo;
        }
        if (field.fallback() instanceof Boolean) {
            CheckBox check = new CheckBox(); check.selectedProperty().addListener((o, a, b) -> fieldChanged(field)); return check;
        }
        TextField text = new TextField(); text.setPrefColumnCount(9);
        text.textProperty().addListener((o, a, b) -> fieldChanged(field)); return text;
    }
    private void fieldChanged(ToolsDatabaseFields.Field field) {
        if (!loading && editing != null) {
            changed.add(field); dirty.set(true);
            if (field.key().equals("tool_target")) updateVisibility();
            updateDependencies();
            feedback.setText("Parametros alterados. Aplicar ou salvar valida os valores antes de usa-los.");
        }
    }
    private void updateVisibility() {
        Object value = option("tool_target");
        int target = ToolsDatabase.targetIndex(value);
        groups.forEach((group, pane) -> { boolean visible = group.visible(target); pane.setVisible(visible); pane.setManaged(visible); });
    }
    private Control control(String key) {
        return controls.entrySet().stream().filter(e -> e.getKey().key().equals(key)).findFirst().orElseThrow().getValue();
    }
    private Object option(String key) { return ((ToolsDatabaseFields.Choice) ((ComboBox<?>) control(key)).getValue()).value(); }
    private boolean checked(String key) { return ((CheckBox) control(key)).isSelected(); }
    private void updateDependencies() {
        if (controls.isEmpty()) return;
        boolean locked = editing == null || table.getSelectionModel().getSelectedItems().size() > 1;
        controls.values().forEach(c -> c.setDisable(locked));
        if (locked) return;
        control("tooldia").setDisable("V".equals(option("tool_type")));
        control("vtipdia").setDisable(!"V".equals(option("tool_type")));
        control("vtipangle").setDisable(!"V".equals(option("tool_type")));
        control("offset_value").setDisable(!"Custom".equals(option("offset")));
        for (String prefix : List.of("", "tools_drill_")) {
            control(prefix + "depthperpass").setDisable(!checked(prefix + "multidepth"));
            control(prefix + "dwelltime").setDisable(!checked(prefix + "dwell"));
        }
        control("extracut_length").setDisable(!checked("extracut"));
        control("tools_ncc_offset_value").setDisable(!checked("tools_ncc_offset_choice"));
        control("tools_drill_drill_overlap").setDisable(!checked("tools_drill_drill_slots"));
        control("tools_drill_last_drill").setDisable(!checked("tools_drill_drill_slots"));
        control("tools_cutout_gap_depth").setDisable(!"bt".equals(option("tools_cutout_gap_type")));
        control("tools_cutout_mb_dia").setDisable(!"mb".equals(option("tools_cutout_gap_type")));
        control("tools_cutout_mb_spacing").setDisable(!"mb".equals(option("tools_cutout_gap_type")));
    }
    private Object value(Control control, ToolsDatabaseFields.Field field) {
        if (control instanceof ComboBox<?> combo) return ((ToolsDatabaseFields.Choice) combo.getValue()).value();
        if (control instanceof CheckBox check) return check.isSelected();
        return field.parse(((TextField) control).getText());
    }
    @SuppressWarnings("unchecked")
    private void loadEditor(Integer id) {
        loading = true;
        try {
            editing = id; changed.clear();
            JSONObject entry = id == null ? ToolsDatabaseFields.newEntry("new_tool") : database.entry(id);
            for (var pair : controls.entrySet()) {
                Object value = pair.getKey().read(entry); Control control = pair.getValue();
                if (control instanceof ComboBox<?> raw) {
                    var combo = (ComboBox<ToolsDatabaseFields.Choice>) raw;
                    combo.getItems().setAll(pair.getKey().choices());
                    Object matchValue = pair.getKey().key().equals("tool_target") && ToolsDatabase.targetIndex(value) >= 0
                            ? ToolsDatabase.targetIndex(value) : value;
                    var choice = combo.getItems().stream().filter(c -> Objects.equals(c.value(), matchValue)
                            || c.value() instanceof Number a && matchValue instanceof Number b && a.doubleValue() == b.doubleValue())
                            .findFirst().orElse(new ToolsDatabaseFields.Choice(String.valueOf(value) + " (importado)", value));
                    if (!combo.getItems().contains(choice)) combo.getItems().add(choice);
                    combo.setValue(choice);
                } else if (control instanceof CheckBox check) check.setSelected(Boolean.TRUE.equals(value));
                else ((TextField) control).setText(String.valueOf(value));
                control.setDisable(id == null);
            }
            updateVisibility();
            updateDependencies();
        } finally { loading = false; }
    }

    boolean applyDraft() { return applyDraft(true); }
    private boolean applyDraft(boolean refreshRows) {
        if (editing == null || changed.isEmpty()) return true;
        try {
            JSONObject entry = database.entry(editing);
            for (var field : changed) field.write(entry, value(controls.get(field), field));
            if (entry.optString("name").isBlank()) throw new IllegalArgumentException("Name nao pode ficar vazio.");
            if ("V".equals(entry.optString("tool_type")) && changed.stream().anyMatch(f -> Set.of("tool_type", "vtipdia", "vtipangle", "cutz").contains(f.key()))) {
                JSONObject data = entry.getJSONObject("data");
                double angle = data.optDouble("vtipangle", 30), tip = data.optDouble("vtipdia", 0.1), depth = data.optDouble("cutz", -2.4);
                if (!Double.isFinite(angle) || angle <= 0 || angle >= 180 || !Double.isFinite(tip) || tip < 0 || !Double.isFinite(depth))
                    throw new IllegalArgumentException("V-Angle deve estar entre 0 e 180; V-Dia nao pode ser negativo; Cut Z deve ser finito.");
                entry.put("tooldia", tip + 2 * Math.abs(depth) * Math.tan(Math.toRadians(angle / 2)));
            }
            database.update(editing, entry); changed.clear();
            if (refreshRows) { refresh(editing); loadEditor(editing); }
            else {
                for (int i = 0; i < table.getItems().size(); i++) if (table.getItems().get(i).id() == editing) {
                    table.getItems().set(i, new Row(editing, entry.optString("name"), entry.getDouble("tooldia"), ToolsDatabase.targetLabel(entry)));
                    break;
                }
            }
            feedback.setText("Parametros aplicados. Salve a base para persistir."); return true;
        } catch (IllegalArgumentException error) { feedback.setText(error.getMessage()); return false; }
    }
    JSONObject snapshot() {
        if (busy.get()) throw new IllegalArgumentException("Tools Database ainda esta carregando/salvando.");
        if (!applyDraft()) throw new IllegalArgumentException(feedback.getText());
        return database.toJson();
    }
    BooleanProperty dirtyProperty() { return dirty; }
    boolean isBusy() { return busy.get(); }
    void hideContextMenu() { table.getContextMenu().hide(); }
    ContextMenu contextMenuForTooltips() { return table.getContextMenu(); }

    private void refresh(Integer select) {
        loading = true;
        try {
            String term = search.getText().trim().toLowerCase(Locale.ROOT);
            int target = filter.getSelectionModel().getSelectedIndex() - 1;
            List<Row> rows = new ArrayList<>();
            for (int id : database.ids()) {
                JSONObject entry = database.entry(id);
                Row row = new Row(id, entry.optString("name", "Tool " + id), entry.getDouble("tooldia"), ToolsDatabase.targetLabel(entry));
                if (target >= 0 && ToolsDatabase.targetIndex(entry.getJSONObject("data").opt("tool_target")) != target) continue;
                if (!(row.id() + " " + row.name() + " " + row.diameter()).toLowerCase(Locale.ROOT).contains(term)) continue;
                rows.add(row);
            }
            table.getItems().setAll(rows);
            if (select != null) rows.stream().filter(row -> row.id() == select).findFirst().ifPresent(row -> table.getSelectionModel().select(row));
        } finally { loading = false; }
    }
    private void refilter() {
        if (loading || !applyDraft()) return;
        refresh(editing); Row selected = table.getSelectionModel().getSelectedItem(); loadEditor(selected == null ? null : selected.id());
    }
    private void selectUnfiltered(int id) {
        loading = true; search.clear(); filter.getSelectionModel().selectFirst(); loading = false;
        refresh(id); loadEditor(id);
    }
    private void add() {
        if (!applyDraft()) return;
        int id = database.add(ToolsDatabaseFields.newEntry("new_tool_" + (database.ids().isEmpty() ? 1 : database.ids().getLast() + 1)));
        dirty.set(true); selectUnfiltered(id);
    }
    private void duplicate() {
        List<Integer> ids = table.getSelectionModel().getSelectedItems().stream().map(Row::id).toList();
        if (ids.isEmpty() || !applyDraft()) return;
        int last = 0; for (int id : ids) last = database.duplicate(id);
        dirty.set(true); selectUnfiltered(last);
    }
    private void delete() {
        List<Integer> ids = table.getSelectionModel().getSelectedItems().stream().map(Row::id).toList();
        if (ids.isEmpty() || !confirm("Excluir " + ids.size() + " ferramenta(s)?", "A base no disco so sera alterada ao salvar.")) return;
        database.remove(ids); changed.clear(); editing = null; dirty.set(true); refresh(null); loadEditor(null);
    }
    private boolean confirm(String title, String message) {
        return confirmationDialog(title, message).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    Alert confirmationDialog(String title, String message) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        dialog.setTitle("Tools Database"); dialog.setHeaderText(title);
        Window window = owner.get();
        if (window != null) {
            dialog.initOwner(window);
            // Alerts have their own scene; inherit the current palette explicitly.
            if (window.getScene() != null) dialog.getDialogPane().getStylesheets().setAll(window.getScene().getStylesheets());
        }
        return dialog;
    }
    boolean confirmClose() {
        if (busy.get()) { feedback.setText("Aguarde o carregamento/salvamento antes de fechar."); return false; }
        if (!dirty.get()) return true;
        if (!confirm("Descartar alteracoes nao salvas?", "Use Save DB antes de fechar para manter as alteracoes. Cancelar retorna ao editor.")) return false;
        replace(ToolsDatabase.fromJson(saved), path); return true;
    }
    private boolean allowReplace() { return !dirty.get() || confirm("Substituir a base e descartar alteracoes?", "Salve primeiro se quiser mante-las."); }
    private void newDatabase() { if (allowReplace()) replace(new ToolsDatabase(), null); }
    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tools Database (*.FlatDB, *.json)", "*.FlatDB", "*.flatdb", "*.json"));
        chooser.setInitialFileName(path == null ? "tools_db.FlatDB" : path.getFileName().toString());
        if (path != null && java.nio.file.Files.isDirectory(path.getParent())) chooser.setInitialDirectory(path.getParent().toFile());
        return chooser;
    }
    private void importDatabase() {
        File file = chooser("Import DB — FlatCAM Python").showOpenDialog(owner.get());
        if (file != null && allowReplace()) loadPath(file.toPath());
    }
    void loadPath(Path file) {
        if (busy.get()) return;
        busy.set(true); feedback.setText("Carregando Tools Database...");
        jobs.submit(context -> ToolsDatabase.load(file), (fraction, message) -> {}).completion().whenComplete((loaded, error) -> Platform.runLater(() -> {
            busy.set(false);
            if (error != null) { failure(error); return; }
            replace(loaded, file.toAbsolutePath().normalize()); remember.accept(path);
            feedback.setText("Base carregada: " + database.ids().size() + " ferramenta(s).");
        }));
    }
    private void replace(ToolsDatabase loaded, Path file) {
        database = loaded; saved = database.toJson(); path = file;
        editing = null; changed.clear(); dirty.set(false);
        loading = true; search.clear(); filter.getSelectionModel().selectFirst(); loading = false;
        Integer first = database.ids().isEmpty() ? null : database.ids().getFirst(); refresh(first); loadEditor(first);
        location.setText(path == null ? "Nova base — ainda nao salva" : path.toString());
    }
    private void save(boolean saveAs) {
        if (busy.get() || !applyDraft()) return;
        Path destination = path;
        if (saveAs || destination == null) {
            File chosen = chooser("Save Tools Database").showSaveDialog(owner.get());
            if (chosen == null) return;
            destination = chosen.toPath();
        }
        saveTo(destination, saveAs);
    }
    void saveTo(Path destination, boolean saveAs) {
        if (busy.get() || !applyDraft()) return;
        final Path target = destination.toAbsolutePath().normalize();
        ToolsDatabase snapshot = ToolsDatabase.fromJson(database.toJson());
        busy.set(true); feedback.setText("Salvando base e backup...");
        jobs.submit(context -> snapshot.save(target), (fraction, message) -> {}).completion().whenComplete((backup, error) -> Platform.runLater(() -> {
            busy.set(false);
            if (error != null) { failure(error); return; }
            if (!saveAs || target.equals(path)) { path = target; saved = snapshot.toJson(); dirty.set(false); location.setText(path.toString()); remember.accept(path); }
            feedback.setText((saveAs ? "Base exportada: " : "Base salva: ") + target + (backup == null ? "" : " · Backup: " + backup));
        }));
    }
    private void failure(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        feedback.setText("Tools Database: " + error.getMessage() + " (a base atual foi mantida).");
    }
}
