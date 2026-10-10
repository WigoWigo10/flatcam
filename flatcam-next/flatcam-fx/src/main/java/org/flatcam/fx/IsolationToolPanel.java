package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccGenerator;
import org.locationtech.jts.geom.Geometry;

/** Isolation form, organized like the Python tool: tools first, per-tool parameters, common parameters. */
final class IsolationToolPanel {

    @FunctionalInterface
    interface AreaSelectionStarter {
        boolean begin(SourceCandidate source, boolean polygon, Consumer<Geometry> onSelected, Runnable onCancelled);
    }

    record SourceCandidate(TreeItem<String> item, GerberImage image) {
        @Override public String toString() { return item.getValue(); }
    }

    record ExceptionArea(String name, Geometry geometry, TreeItem<String> item) {
        ExceptionArea(String name, Geometry geometry) { this(name, geometry, null); }
        @Override public String toString() { return name; }
    }

    record Result(SourceCandidate source, List<IsolationParameters> tools, Map<Double, ToolProfile> profiles,
                  boolean restMachining, boolean forcedRest, boolean combinePasses, boolean follow,
                  boolean checkValidity, Geometry exceptionMask, ExceptionArea exceptionReference,
                  Map<Double, LegacyToolsDatabase.MillingTool> machining) {
        Result(SourceCandidate source, List<IsolationParameters> tools, Map<Double, ToolProfile> profiles,
               boolean restMachining, boolean forcedRest, boolean combinePasses, boolean follow,
               boolean checkValidity, Geometry exceptionMask, ExceptionArea exceptionReference) {
            this(source, tools, profiles, restMachining, forcedRest, combinePasses, follow,
                    checkValidity, exceptionMask, exceptionReference, Map.of());
        }
        Result { machining = Map.copyOf(machining); }
    }

    private static final class ToolRow {
        final double diameter;
        ToolProfile profile;
        String passes;
        String overlap;
        IsolationType type;
        LegacyToolsDatabase.MillingTool machining;

        ToolRow(double diameter, ToolProfile profile, int passes, double overlap, IsolationType type) {
            this.diameter = diameter;
            this.profile = profile;
            this.passes = Integer.toString(passes);
            this.overlap = format(overlap * 100);
            this.type = type;
        }

        IsolationParameters parameters() {
            int count;
            try { count = Integer.parseInt(passes.trim()); }
            catch (NumberFormatException error) {
                throw new IllegalArgumentException("Tool " + format(diameter) + ": Passes deve ser inteiro.");
            }
            return new IsolationParameters(diameter, count,
                    parse(overlap, "Tool " + format(diameter) + ": Overlap") / 100, type);
        }
    }

    private IsolationToolPanel() { }

    static Node build(List<SourceCandidate> sources, SourceCandidate initialSource,
                      List<ExceptionArea> exceptionAreas, AreaSelectionStarter areaStarter,
                      Runnable cancelArea, Supplier<List<LegacyToolsDatabase.IsolationTool>> databaseLoader,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(initialSource.image().units());
        double initialDiameter = ToolDefaults.number("iso.tooldia", metric);
        java.util.function.Supplier<ToolRow> defaultRow = () -> new ToolRow(initialDiameter,
                ToolDefaults.choice("iso.tooltype", ToolProfile.class), ToolDefaults.integer("iso.passes"),
                ToolDefaults.number("iso.overlap", metric) / 100, ToolDefaults.choice("iso.type", IsolationType.class));
        ComboBox<SourceCandidate> sourceCombo = new ComboBox<>(FXCollections.observableArrayList(sources));
        sourceCombo.setValue(initialSource);
        sourceCombo.setMinWidth(0);
        sourceCombo.setPrefWidth(180);
        sourceCombo.setMaxWidth(Double.MAX_VALUE);

        ObservableList<ToolRow> rows = FXCollections.observableArrayList(
                defaultRow.get());
        Label toolMessage = new Label();
        toolMessage.setWrapText(true);
        toolMessage.getStyleClass().add("form-error-label");
        toolMessage.managedProperty().bind(toolMessage.textProperty().isNotEmpty());
        TableView<ToolRow> table = new TableView<>(rows);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<ToolRow, String> numberColumn = new TableColumn<>("#");
        numberColumn.setSortable(false);
        numberColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(""));
        numberColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Integer.toString(getIndex() + 1));
            }
        });
        TableColumn<ToolRow, Number> diameterColumn = new TableColumn<>("Diameter");
        diameterColumn.setSortable(false);
        diameterColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleDoubleProperty(cell.getValue().diameter));
        TableColumn<ToolRow, ToolProfile> typeColumn = new TableColumn<>("TT");
        typeColumn.setSortable(false);
        typeColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().profile));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<ToolProfile> choice = new ComboBox<>(
                    FXCollections.observableArrayList(ToolProfile.values()));
            private boolean updating;
            {
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
                setPadding(new javafx.geometry.Insets(1, 2, 1, 2));
                choice.getStyleClass().add("table-editor-combo");
                ToolProfilePicker.decorate(choice);
                choice.setMinWidth(0);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.prefWidthProperty().bind(typeColumn.widthProperty().subtract(6));
                choice.setOnAction(event -> {
                    if (!updating && getTableRow() != null && getTableRow().getItem() != null) {
                        getTableRow().getItem().profile = choice.getValue();
                        if (choice.getValue() == ToolProfile.V)
                            toolMessage.setText("TT V: configure V-Tip Dia e Angle ao criar o CNC Job da Geometry.");
                    }
                });
            }
            @Override protected void updateItem(ToolProfile item, boolean empty) {
                super.updateItem(item, empty);
                updating = true;
                choice.setValue(item);
                setGraphic(empty ? null : choice);
                updating = false;
            }
        });
        Label typeHeader = new Label("TT");
        typeHeader.setTooltip(new javafx.scene.control.Tooltip(
                "C1-C4 e B: tipo informativo. V: exige calculo de profundidade por V-Tip Dia/Angle no CNC Job."));
        typeColumn.setText(null);
        typeColumn.setGraphic(typeHeader);
        table.getColumns().addAll(numberColumn, diameterColumn, typeColumn);
        CompactToolsTable.configure(table, numberColumn, diameterColumn, typeColumn);
        table.setPrefHeight(64);
        rows.addListener((javafx.collections.ListChangeListener<ToolRow>) change -> {
            table.setPrefHeight(Math.min(160, 36 + Math.max(1, rows.size()) * 28));
            table.refresh();
        });
        table.getSelectionModel().selectFirst();

        ToggleGroup orderGroup = new ToggleGroup();
        RadioButton noOrder = radio("No", orderGroup);
        RadioButton forwardOrder = radio("Forward", orderGroup);
        RadioButton reverseOrder = radio("Reverse", orderGroup);
        Runnable defaultOrder = () -> (switch (ToolDefaults.choice("iso.order")) {
            case "NONE" -> noOrder;
            case "FORWARD" -> forwardOrder;
            default -> reverseOrder;
        }).setSelected(true);
        defaultOrder.run();
        HBox orderRow = new HBox(8, new Label("Tool order:"), noOrder, forwardOrder, reverseOrder);
        orderRow.setAlignment(Pos.CENTER_LEFT);

        Spinner<Double> diameterSpinner = spinner(0.0001, 10000, initialDiameter, metric ? 0.1 : 0.001);
        Button optimalButton = new Button("Optimal");
        optimalButton.setOnAction(event -> {
            SourceCandidate source = sourceCombo.getValue();
            optimalButton.setDisable(true);
            toolMessage.setText("Calculando diametro seguro...");
            CompletableFuture.supplyAsync(() -> NccGenerator.minimumCopperClearance(
                    source.image().solidGeometry())).whenComplete((clearance, failure) -> Platform.runLater(() -> {
                if (optimalButton.getScene() == null) return;
                optimalButton.setDisable(false);
                if (failure != null) toolMessage.setText("Falha: " + failure.getMessage());
                else if (clearance.isEmpty() || clearance.getAsDouble() <= 0)
                    toolMessage.setText("Nao ha distancia positiva entre regioes de cobre.");
                else if (sourceCombo.getValue() == source) {
                    diameterSpinner.getValueFactory().setValue(clearance.getAsDouble());
                    toolMessage.setText("Diametro seguro estimado: " + format(clearance.getAsDouble())
                            + " " + source.image().units() + ". Confira antes de usinar.");
                }
            }));
        });
        Button addButton = new Button("Adicionar ferramenta");
        Button dbButton = new Button("Pick from DB");
        Button deleteButton = new Button("Delete");
        deleteButton.disableProperty().bind(Bindings.isEmpty(table.getSelectionModel().getSelectedItems()));
        addButton.setOnAction(event -> {
            try {
                double diameter = parse(diameterSpinner.getEditor().getText(), "Tool Dia");
                if (diameter <= 0) throw new IllegalArgumentException("Tool Dia deve ser positivo.");
                if (rows.stream().anyMatch(row -> Math.abs(row.diameter - diameter) < 1e-6))
                    throw new IllegalArgumentException("Esta ferramenta ja existe na tabela.");
                rows.add(new ToolRow(diameter, ToolProfile.C1, 1, 0.10, IsolationType.BOTH));
                table.getSelectionModel().clearAndSelect(rows.size() - 1);
                toolMessage.setText("");
            } catch (RuntimeException error) { toolMessage.setText(error.getMessage()); }
        });
        diameterSpinner.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) addButton.fire();
        });
        dbButton.setOnAction(event -> {
            try {
                List<LegacyToolsDatabase.IsolationTool> available = databaseLoader.get();
                if (available.isEmpty()) return;
                ChoiceDialog<LegacyToolsDatabase.IsolationTool> dialog =
                        new ChoiceDialog<>(available.get(0), available);
                dialog.setTitle("Tools Database");
                dialog.setHeaderText("Escolha uma ferramenta Isolation. Confira as unidades do diametro.");
                dialog.showAndWait().ifPresent(chosen -> {
                    IsolationParameters parameters = chosen.parameters();
                    if (rows.stream().anyMatch(row -> Math.abs(row.diameter - parameters.toolDiameter()) < 1e-6)) {
                        toolMessage.setText("Esta ferramenta ja existe na tabela.");
                        return;
                    }
                    ToolRow imported = new ToolRow(parameters.toolDiameter(), chosen.toolProfile(),
                            parameters.passes(), parameters.overlapFraction(), parameters.type());
                    imported.machining = chosen.machining();
                    rows.add(imported);
                    table.getSelectionModel().clearAndSelect(rows.size() - 1);
                    toolMessage.setText(chosen.machining() != null
                            ? "Parametros de corte da DB serao preservados. Confira unidades e campos comuns ao criar CNC."
                            : chosen.toolProfile() == ToolProfile.V
                            ? "TT V: configure V-Tip Dia e Angle ao criar o CNC Job da Geometry." : "");
                });
            } catch (RuntimeException error) { toolMessage.setText(error.getMessage()); }
        });
        deleteButton.setOnAction(event -> {
            List<ToolRow> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
            if (rows.size() - selected.size() < 1) {
                toolMessage.setText("Mantenha ao menos uma ferramenta na tabela.");
                return;
            }
            rows.removeAll(selected);
            table.getSelectionModel().selectFirst();
            toolMessage.setText("");
        });

        Spinner<Integer> passesSpinner = new Spinner<>(1, 999, ToolDefaults.integer("iso.passes"));
        passesSpinner.setEditable(true);
        passesSpinner.setMinWidth(0);
        passesSpinner.setPrefWidth(125);
        Spinner<Double> overlapSpinner = spinner(0, 99.9999, ToolDefaults.number("iso.overlap", metric), 0.1);
        ComboBox<IsolationType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(IsolationType.values()));
        typeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(IsolationType value) {
                if (value == null) return "";
                return switch (value) {
                    case BOTH -> "Full";
                    case EXTERIOR -> "Ext";
                    case INTERIOR -> "Int";
                };
            }
            @Override public IsolationType fromString(String value) {
                return switch (value) {
                    case "Full" -> IsolationType.BOTH;
                    case "Ext" -> IsolationType.EXTERIOR;
                    case "Int" -> IsolationType.INTERIOR;
                    default -> throw new IllegalArgumentException("Isolation Type desconhecido: " + value);
                };
            }
        });
        typeCombo.setValue(ToolDefaults.choice("iso.type", IsolationType.class));
        Label parameterTitle = heading("Parameters for: Tool 1");
        boolean[] loading = {false};
        Runnable loadSelected = () -> {
            List<ToolRow> selected = table.getSelectionModel().getSelectedItems();
            parameterTitle.setText(selected.isEmpty() ? "Parameters for: No Tool Selected"
                    : selected.size() > 1 ? "Parameters for: Multiple Tools"
                    : "Parameters for: Tool " + (rows.indexOf(selected.get(0)) + 1));
            if (selected.size() != 1) return;
            ToolRow row = selected.get(0);
            loading[0] = true;
            try { passesSpinner.getValueFactory().setValue(Integer.parseInt(row.passes.trim())); }
            catch (NumberFormatException ignored) { /* Keep invalid draft visible until Generate validates it. */ }
            passesSpinner.getEditor().setText(row.passes);
            try { overlapSpinner.getValueFactory().setValue(parse(row.overlap, "Overlap")); }
            catch (IllegalArgumentException ignored) { /* Keep invalid draft visible until Generate validates it. */ }
            overlapSpinner.getEditor().setText(row.overlap);
            typeCombo.setValue(row.type);
            loading[0] = false;
        };
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ToolRow>) change -> loadSelected.run());
        loadSelected.run();
        passesSpinner.getEditor().textProperty().addListener((observable, oldValue, value) -> {
            if (!loading[0] && table.getSelectionModel().getSelectedItems().size() == 1)
                table.getSelectionModel().getSelectedItem().passes = value;
        });
        overlapSpinner.getEditor().textProperty().addListener((observable, oldValue, value) -> {
            if (!loading[0] && table.getSelectionModel().getSelectedItems().size() == 1)
                table.getSelectionModel().getSelectedItem().overlap = value;
        });
        typeCombo.valueProperty().addListener((observable, oldValue, value) -> {
            if (!loading[0] && value != null && table.getSelectionModel().getSelectedItems().size() == 1)
                table.getSelectionModel().getSelectedItem().type = value;
        });
        var notOne = Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItems().size() != 1,
                table.getSelectionModel().getSelectedItems());
        passesSpinner.disableProperty().bind(notOne);
        overlapSpinner.disableProperty().bind(notOne);
        typeCombo.disableProperty().bind(notOne);
        Button applyAll = new Button("Apply parameters to all tools");
        applyAll.setMaxWidth(Double.MAX_VALUE);
        applyAll.disableProperty().bind(notOne.or(Bindings.size(rows).lessThan(2)));
        applyAll.setOnAction(event -> {
            try {
                ToolRow chosen = table.getSelectionModel().getSelectedItem();
                chosen.parameters();
                for (ToolRow row : rows) {
                    row.passes = chosen.passes;
                    row.overlap = chosen.overlap;
                    row.type = chosen.type;
                }
                toolMessage.setText("Parametros copiados para todas as ferramentas.");
            } catch (RuntimeException error) { toolMessage.setText(error.getMessage()); }
        });

        CheckBox combine = new CheckBox("Combine");
        combine.setSelected(ToolDefaults.flag("iso.combine"));
        CheckBox checkValidity = new CheckBox("Check validity");
        checkValidity.setSelected(ToolDefaults.flag("iso.checkvalidity"));
        CheckBox rest = new CheckBox("Rest Machining");
        rest.selectedProperty().addListener((observable, oldValue, selected) -> {
            for (RadioButton radio : List.of(noOrder, forwardOrder, reverseOrder)) radio.setDisable(selected);
            if (selected) reverseOrder.setSelected(true);
        });
        CheckBox forcedRest = new CheckBox("Forced Rest");
        forcedRest.setSelected(ToolDefaults.flag("iso.forcedrest"));
        forcedRest.setTooltip(new javafx.scene.control.Tooltip(
                "Com Rest Machining: se a ferramenta atual nao conseguir isolar todos os furos de um "
                        + "poligono logo na primeira passada, o poligono inteiro e descartado para essa "
                        + "ferramenta e tentado de novo pela proxima ferramenta, menor.\n\n"
                        + "Desmarcado, o resultado da ferramenta atual e aceito mesmo que algum furo "
                        + "tenha se fundido com o contorno."));
        forcedRest.disableProperty().bind(rest.selectedProperty().not());
        CheckBox follow = new CheckBox("Follow");
        follow.setTooltip(new javafx.scene.control.Tooltip("Segue o centro das trilhas Gerber, em vez de isolar as bordas; usa somente uma ferramenta.\n\nAtenção: Follow corta sobre a trilha. Confira a Geometry gerada antes de criar o CNC Job."));
        follow.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) rest.setSelected(false);
        });
        rest.disableProperty().bind(follow.selectedProperty());
        rest.setSelected(ToolDefaults.flag("iso.rest"));
        follow.setSelected(ToolDefaults.flag("iso.follow"));
        passesSpinner.disableProperty().unbind();
        passesSpinner.disableProperty().bind(notOne.or(follow.selectedProperty()));
        overlapSpinner.disableProperty().unbind();
        overlapSpinner.disableProperty().bind(notOne.or(follow.selectedProperty()));
        typeCombo.disableProperty().unbind();
        typeCombo.disableProperty().bind(notOne.or(follow.selectedProperty()));

        ExceptionArea none = new ExceptionArea("Nenhuma", null);
        ComboBox<ExceptionArea> exceptionCombo = new ComboBox<>();
        exceptionCombo.setId("isolation-exception-object");
        exceptionCombo.getItems().add(none);
        exceptionCombo.getItems().addAll(exceptionAreas);
        exceptionCombo.setValue(none);
        exceptionCombo.setMinWidth(0);
        exceptionCombo.setPrefWidth(180);
        exceptionCombo.setMaxWidth(Double.MAX_VALUE);
        Geometry[] drawnMask = {null};
        Label areaStatus = new Label("Nenhuma area desenhada.");
        areaStatus.setWrapText(true);
        Button rectangle = new Button("Desenhar retangulo de excecao");
        Button polygon = new Button("Desenhar poligono de excecao");
        for (Button button : List.of(rectangle, polygon)) {
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> {
                SourceCandidate source = sourceCombo.getValue();
                boolean isPolygon = button == polygon;
                drawnMask[0] = null;
                areaStatus.setText(isPolygon ? "Clique nos vertices; Enter conclui, Esc cancela."
                        : "Clique em dois cantos; Esc cancela.");
                if (!areaStarter.begin(source, isPolygon, area -> {
                    if (sourceCombo.getValue() != source) return;
                    drawnMask[0] = area;
                    areaStatus.setText("Area de excecao selecionada.");
                }, () -> areaStatus.setText("Selecao cancelada.")))
                    areaStatus.setText("Nao foi possivel iniciar a selecao.");
            });
        }
        Button clearArea = new Button("Limpar area desenhada");
        clearArea.setMaxWidth(Double.MAX_VALUE);
        clearArea.setOnAction(event -> {
            cancelArea.run();
            drawnMask[0] = null;
            areaStatus.setText("Nenhuma area desenhada.");
        });
        sourceCombo.valueProperty().addListener((observable, oldValue, value) -> {
            cancelArea.run();
            drawnMask[0] = null;
            areaStatus.setText("Nenhuma area desenhada.");
        });
        GridPane advancedGrid = new GridPane();
        advancedGrid.setHgap(8);
        advancedGrid.setVgap(8);
        advancedGrid.addRow(0, rest, follow);
        advancedGrid.addRow(1, forcedRest);
        GridPane.setColumnSpan(forcedRest, 2);
        advancedGrid.addRow(2, new Label("Isolation Type:"), typeCombo);
        advancedGrid.addRow(3, new Label("Excluir area:"), exceptionCombo);
        VBox advancedBox = new VBox(8, advancedGrid, rectangle, polygon, clearArea, areaStatus);
        TitledPane advanced = new TitledPane("Opcoes avancadas", advancedBox);
        advanced.setExpanded(false);
        advanced.setAnimated(false);

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        Button generate = new Button("Generate Geometry");
        generate.setId("isolation-generate");
        generate.getStyleClass().add("primary-action");
        generate.setMaxWidth(Double.MAX_VALUE);
        generate.setOnAction(event -> {
            try {
                List<ToolRow> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
                if (selected.isEmpty()) throw new IllegalArgumentException("Selecione uma ferramenta na tabela.");
                if (follow.isSelected() && selected.size() > 1)
                    throw new IllegalArgumentException("Follow usa uma ferramenta por vez.");
                if (rest.isSelected() && follow.isSelected())
                    throw new IllegalArgumentException("Follow e Rest Machining nao podem ser combinados.");
                if (!rest.isSelected() && forwardOrder.isSelected())
                    selected = selected.stream().sorted(Comparator.comparingDouble(row -> row.diameter)).toList();
                else if (rest.isSelected() || reverseOrder.isSelected())
                    selected = selected.stream().sorted(Comparator.comparingDouble((ToolRow row) -> row.diameter)
                            .reversed()).toList();
                List<IsolationParameters> tools = new ArrayList<>();
                Map<Double, ToolProfile> profiles = new LinkedHashMap<>();
                Map<Double, LegacyToolsDatabase.MillingTool> machining = new LinkedHashMap<>();
                for (ToolRow row : selected) {
                    tools.add(row.parameters());
                    profiles.put(row.diameter, row.profile);
                    if (row.machining != null) {
                        if (row.machining.profile() != row.profile)
                            throw new IllegalArgumentException("TT mudou apos importar a DB; remova/reimporte a ferramenta.");
                        machining.put(row.diameter, row.machining);
                    }
                }
                errorLabel.setText("");
                onGenerate.accept(new Result(sourceCombo.getValue(), List.copyOf(tools), Map.copyOf(profiles),
                        rest.isSelected(), forcedRest.isSelected(), combine.isSelected(), follow.isSelected(),
                        checkValidity.isSelected(), drawnMask[0] != null ? drawnMask[0]
                                : exceptionCombo.getValue().geometry(), drawnMask[0] == null
                                && exceptionCombo.getValue() != none ? exceptionCombo.getValue() : null,
                        Map.copyOf(machining)));
            } catch (RuntimeException error) { errorLabel.setText(error.getMessage()); }
        });
        Button reset = new Button("Reset Tool");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(event -> {
            cancelArea.run();
            sourceCombo.setValue(initialSource);
            rows.setAll(defaultRow.get());
            table.getSelectionModel().clearAndSelect(0);
            diameterSpinner.getValueFactory().setValue(initialDiameter);
            rest.setSelected(ToolDefaults.flag("iso.rest"));
            defaultOrder.run();
            combine.setSelected(ToolDefaults.flag("iso.combine"));
            checkValidity.setSelected(ToolDefaults.flag("iso.checkvalidity"));
            forcedRest.setSelected(ToolDefaults.flag("iso.forcedrest"));
            follow.setSelected(ToolDefaults.flag("iso.follow"));
            typeCombo.setValue(ToolDefaults.choice("iso.type", IsolationType.class));
            exceptionCombo.setValue(none);
            drawnMask[0] = null;
            areaStatus.setText("Nenhuma area desenhada.");
            toolMessage.setText("");
            errorLabel.setText("");
            loadSelected.run();
        });
        Button close = new Button("Fechar");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(event -> onClose.run());
        HBox newDiaRow = new HBox(6, new Label("Tool Dia:"), diameterSpinner, optimalButton);
        newDiaRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(diameterSpinner, Priority.ALWAYS);
        HBox addRow = new HBox(6, addButton, dbButton);
        addButton.setMaxWidth(Double.MAX_VALUE);
        dbButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(addButton, Priority.ALWAYS);
        HBox.setHgrow(dbButton, Priority.ALWAYS);
        GridPane parametersGrid = new GridPane();
        parametersGrid.setHgap(8);
        parametersGrid.setVgap(8);
        parametersGrid.addRow(0, new Label("Passes:"), passesSpinner);
        parametersGrid.addRow(1, new Label("Overlap (%):"), overlapSpinner);
        Label title = heading("Isolation Tool");
        title.getStyleClass().remove("form-section-title");
        title.getStyleClass().add("tool-title");
        VBox box = new VBox(8, title, heading("GERBER:"), sourceCombo,
                new Separator(), heading("Tools Table"), table, orderRow, new Separator(),
                heading("Add from DB"), newDiaRow, addRow, deleteButton, toolMessage,
                new Separator(), parameterTitle, parametersGrid, applyAll, new Separator(),
                heading("Common Parameters"), combine, checkValidity, advanced,
                errorLabel, generate, reset, close);
        box.setPadding(new Insets(12));
        return box;
    }

    private static Label heading(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("form-section-title");
        return label;
    }

    private static RadioButton radio(String title, ToggleGroup group) {
        RadioButton button = new RadioButton(title);
        button.setToggleGroup(group);
        return button;
    }

    private static Spinner<Double> spinner(double min, double max, double initial, double step) {
        Spinner<Double> spinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(
                min, max, initial, step));
        spinner.setEditable(true);
        spinner.setMinWidth(0);
        spinner.setPrefWidth(115);
        return spinner;
    }

    private static double parse(String value, String label) {
        try { return Double.parseDouble(value.trim().replace(',', '.')); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + ": numero invalido."); }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
