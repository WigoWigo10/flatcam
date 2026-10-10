package org.flatcam.fx;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.application.Platform;
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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.TreeItem;
import javafx.util.StringConverter;
import javafx.util.Duration;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccMillingType;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.NccParameters;
import org.flatcam.cam.ncc.NccToolSettings;
import org.flatcam.cam.ncc.NccSeedPolicy;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.locationtech.jts.geom.Geometry;

/**
 * Multi-tool NCC form; the result is a multigeo Geometry object (one entry
 * per tool), matching Python's workflow. The tool list is a simple ordered
 * table of diameters and per-CLEAR-tool settings. Python's Tools Database can
 * be read to insert an NCC row; margin/boundary remain common.
 */
final class NccToolPanel {

    private static final List<ToolProfile> TOOL_TYPES = List.of(ToolProfile.values());

    private static final class ToolRow {
        final double diameter;
        ToolProfile toolProfile = ToolDefaults.choice("ncc.tooltype", ToolProfile.class);
        NccOperation operation;
        String overlapPercent = ToolDefaults.text("ncc.overlap", true);
        NccMethod method = ToolDefaults.choice("ncc.method", NccMethod.class);
        NccSeedPolicy seedPolicy = ToolDefaults.choice("ncc.seedpolicy", NccSeedPolicy.class);
        boolean connect = ToolDefaults.flag("ncc.connect");
        boolean contour = ToolDefaults.flag("ncc.contour");
        boolean offsetEnabled;
        String offset = "0.0";
        LegacyToolsDatabase.MillingTool machining;

        ToolRow(double diameter) {
            this.diameter = diameter;
            this.operation = NccOperation.CLEAR;
        }

        NccToolSettings settings() {
            String prefix = "Ferramenta " + format(diameter) + ": ";
            return new NccToolSettings(parseNumber(overlapPercent, prefix + "Overlap") / 100.0,
                    method, connect, contour,
                    offsetEnabled ? parseNumber(offset, prefix + "Copper offset") : 0, seedPolicy);
        }
    }

    private static final String BOUNDARY_ITSELF = "Itself";
    private static final String BOUNDARY_AREA = "Area Selection";
    private static final String BOUNDARY_REFERENCE = "Reference Object";

    enum AreaShape {
        RECTANGLE("Retangulo"),
        POLYGON("Poligono");

        private final String label;

        AreaShape(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FunctionalInterface
    interface AreaSelector {
        boolean select(SourceCandidate source, AreaShape shape,
                       Consumer<Geometry> onSelected, Runnable onCancelled);
    }

    record SourceCandidate(TreeItem<String> item, String name, String units,
                           boolean gerber, Geometry geometry) {
        @Override
        public String toString() { return name; }
    }

    /** One selectable entry for the "Reference Object" boundary combo - see MainWindow.generateNcc. */
    record ReferenceCandidate(TreeItem<String> item, String displayName, boolean isGerber, Geometry geometry) {
        @Override
        public String toString() {
            return displayName + (isGerber ? " (Gerber)" : " (Geometry)");
        }
    }

    record Result(SourceCandidate source, NccParameters parameters, boolean checkValidity,
                  Map<Double, ToolProfile> toolProfiles, ReferenceCandidate reference,
                  Map<Double, LegacyToolsDatabase.MillingTool> machining) {
        Result(SourceCandidate source, NccParameters parameters, boolean checkValidity,
               Map<Double, ToolProfile> toolProfiles, ReferenceCandidate reference) {
            this(source, parameters, checkValidity, toolProfiles, reference, Map.of());
        }
        Result(SourceCandidate source, NccParameters parameters, boolean checkValidity,
               Map<Double, ToolProfile> toolProfiles) { this(source, parameters, checkValidity, toolProfiles, null); }
        Result { machining = Map.copyOf(machining); }
    }

    private NccToolPanel() {
    }

    static Node build(List<SourceCandidate> sourceCandidates, SourceCandidate initialSource,
                      List<ReferenceCandidate> referenceCandidates,
                      AreaSelector areaSelector,
                      Runnable cancelArea,
                      Supplier<List<LegacyToolsDatabase.NccTool>> databaseLoader,
                      Consumer<Result> onGenerate, Runnable onClose) {
        String units = initialSource.units();
        boolean metric = "MM".equalsIgnoreCase(units);
        ComboBox<SourceCandidate> sourceCombo = new ComboBox<>();
        sourceCombo.setId("ncc-source-object");
        sourceCombo.setMinWidth(0);
        sourceCombo.setPrefWidth(180);
        sourceCombo.setMaxWidth(Double.MAX_VALUE);
        ToggleGroup sourceTypeGroup = new ToggleGroup();
        RadioButton geometrySourceRadio = radio("Geometry", sourceTypeGroup);
        RadioButton gerberSourceRadio = radio("Gerber", sourceTypeGroup);
        geometrySourceRadio.setDisable(sourceCandidates.stream().noneMatch(candidate -> !candidate.gerber()));
        gerberSourceRadio.setDisable(sourceCandidates.stream().noneMatch(SourceCandidate::gerber));
        (initialSource.gerber() ? gerberSourceRadio : geometrySourceRadio).setSelected(true);
        Runnable updateSourceList = () -> {
            boolean gerber = gerberSourceRadio.isSelected();
            SourceCandidate current = sourceCombo.getValue();
            List<SourceCandidate> available = sourceCandidates.stream()
                    .filter(candidate -> candidate.gerber() == gerber).toList();
            sourceCombo.getItems().setAll(available);
            sourceCombo.setValue(available.contains(current) ? current
                    : available.contains(initialSource) ? initialSource
                    : available.isEmpty() ? null : available.get(0));
        };
        sourceTypeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> updateSourceList.run());
        updateSourceList.run();
        GridPane sourceGrid = new GridPane();
        sourceGrid.setHgap(8);
        sourceGrid.setVgap(6);
        sourceGrid.addRow(0, new Label("Obj Type:"), new HBox(10, geometrySourceRadio, gerberSourceRadio));
        sourceGrid.addRow(1, new Label("Object:"), sourceCombo);
        GridPane.setHgrow(sourceCombo, Priority.ALWAYS);
        ObservableList<ToolRow> tools = FXCollections.observableArrayList(defaultTools(metric));
        Label toolError = new Label();
        toolError.getStyleClass().add("form-error-label");
        toolError.setWrapText(true);
        Runnable[] refreshOperation = {() -> { }};

        TableView<ToolRow> toolTable = new TableView<>(tools);
        toolTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        toolTable.setPlaceholder(new Label("Nenhuma ferramenta"));
        TableColumn<ToolRow, String> numberColumn = new TableColumn<>("#");
        numberColumn.setSortable(false);
        numberColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(""));
        numberColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Integer.toString(getIndex() + 1));
            }
        });
        TableColumn<ToolRow, Double> diaColumn = new TableColumn<>("Diametro");
        diaColumn.setSortable(false);
        diaColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().diameter));
        diaColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format(item));
            }
        });
        TableColumn<ToolRow, ToolProfile> typeColumn = new TableColumn<>("TT");
        typeColumn.setSortable(false);
        typeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().toolProfile));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<ToolProfile> choice = new ComboBox<>(FXCollections.observableArrayList(TOOL_TYPES));
            private boolean updating;
            {
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
                setPadding(new javafx.geometry.Insets(1, 2, 1, 2));
                choice.getStyleClass().add("table-editor-combo");
                choice.setMinWidth(0);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.prefWidthProperty().bind(typeColumn.widthProperty().subtract(6));
                choice.setOnAction(event -> {
                    if (updating || getTableRow() == null) return;
                    ToolRow row = getTableRow().getItem();
                    ToolProfile profile = choice.getValue();
                    if (row == null || profile == null) return;
                    if (profile == ToolProfile.V && (sourceCombo.getValue() == null
                            || !sourceCombo.getValue().gerber())) {
                        toolError.setText("Ferramenta V exige origem Gerber e operacao Isolation.");
                        updating = true;
                        choice.setValue(row.toolProfile);
                        updating = false;
                        return;
                    }
                    row.toolProfile = profile;
                    if (profile == ToolProfile.V) row.operation = NccOperation.ISO;
                    refreshOperation[0].run();
                });
            }
            @Override protected void updateItem(ToolProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                updating = true;
                choice.setValue(profile);
                setGraphic(empty ? null : choice);
                updating = false;
            }
        });
        Label typeHeader = new Label("TT");
        typeHeader.setTooltip(tooltip("C1-C4: fresas circulares (numero de dentes). B: ball nose. "
                + "V: exige Gerber e Isolation; configure V-Tip Dia/Angle ao gerar CNC Job."));
        typeColumn.setText(null);
        typeColumn.setGraphic(typeHeader);
        toolTable.getColumns().addAll(numberColumn, diaColumn, typeColumn);
        CompactToolsTable.configure(toolTable, numberColumn, diaColumn, typeColumn);
        Runnable updateTableHeight = () ->
                toolTable.setPrefHeight(Math.min(150, 32 + Math.max(tools.size(), 1) * 28));
        tools.addListener((javafx.collections.ListChangeListener<ToolRow>) change -> {
            updateTableHeight.run();
            toolTable.refresh();
        });
        updateTableHeight.run();
        toolTable.getSelectionModel().selectFirst();

        Spinner<Double> newDiaSpinner = spinner(0.0001, 10_000, ToolDefaults.number("ncc.newdia", metric),
                metric ? 0.1 : 0.001);
        TextField newDiaField = newDiaSpinner.getEditor();
        newDiaSpinner.setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(newDiaSpinner, tooltip("Diametro da nova ferramenta a adicionar"));
        Button addToolButton = new Button("Adicionar");
        Button databaseButton = new Button("Pick from DB");
        Button optimalButton = new Button("Optimal");
        Button removeToolButton = new Button("Remover");
        removeToolButton.disableProperty().bind(Bindings.isEmpty(toolTable.getSelectionModel().getSelectedItems()));
        optimalButton.setDisable(!initialSource.gerber());
        optimalButton.setTooltip(tooltip("Calcula em segundo plano a menor distancia entre regioes de cobre."));
        optimalButton.setOnAction(event -> {
            SourceCandidate selectedSource = sourceCombo.getValue();
            if (selectedSource == null || !selectedSource.gerber()) return;
            optimalButton.setDisable(true);
            toolError.setText("Calculando diametro seguro...");
            CompletableFuture.supplyAsync(() -> NccGenerator.minimumCopperClearance(selectedSource.geometry()))
                    .whenComplete((clearance, error) -> Platform.runLater(() -> {
                        if (optimalButton.getScene() == null) return;
                        optimalButton.setDisable(sourceCombo.getValue() == null
                                || !sourceCombo.getValue().gerber());
                        if (error != null) {
                            toolError.setText("Falha ao calcular diametro: " + error.getMessage());
                        } else if (clearance.isEmpty() || clearance.getAsDouble() <= 0) {
                            toolError.setText("Nao ha distancia positiva entre regioes de cobre.");
                        } else if (sourceCombo.getValue() == selectedSource) {
                            newDiaSpinner.getValueFactory().setValue(clearance.getAsDouble());
                            toolError.setText("Diametro seguro estimado: " + format(clearance.getAsDouble())
                                    + " " + units + ". Confira antes de usinar.");
                        }
                    }));
        });

        Runnable addTool = () -> {
            try {
                double dia = parse(newDiaField, "Diametro da ferramenta");
                if (dia <= 0) {
                    throw new IllegalArgumentException("Diametro da ferramenta deve ser positivo");
                }
                for (ToolRow existing : tools) {
                    if (Math.abs(existing.diameter - dia) < 1e-6) {
                        throw new IllegalArgumentException("Cancelado. Ferramenta ja esta na tabela.");
                    }
                }
                ToolRow added = new ToolRow(dia);
                tools.add(added);
                toolTable.getSelectionModel().clearAndSelect(tools.size() - 1);
                toolError.setText("");
            } catch (RuntimeException ex) {
                toolError.setText(ex.getMessage());
            }
        };
        addToolButton.setOnAction(e -> addTool.run());
        databaseButton.setOnAction(e -> {
            try {
                List<LegacyToolsDatabase.NccTool> entries = databaseLoader.get();
                if (entries.isEmpty()) {
                    toolError.setText("Nenhuma ferramenta NCC encontrada no arquivo, ou selecao cancelada.");
                    return;
                }
                ChoiceDialog<LegacyToolsDatabase.NccTool> dialog = new ChoiceDialog<>(entries.get(0), entries);
                dialog.setTitle("Tools Database");
                dialog.setHeaderText("Escolha uma ferramenta NCC ou General. Confirme se o diametro "
                        + "do banco esta nas unidades do objeto (" + units + ").");
                dialog.setContentText("Ferramenta:");
                dialog.showAndWait().ifPresent(selected -> {
                    if (!sourceCombo.getValue().gerber() && selected.operation() == NccOperation.ISO) {
                        toolError.setText("Ferramenta ISO exige Gerber como origem.");
                        return;
                    }
                    if (tools.stream().anyMatch(row -> Math.abs(row.diameter - selected.diameter()) < 1e-6)) {
                        toolError.setText("Ferramenta com este diametro ja esta na tabela.");
                        return;
                    }
                    ToolRow row = new ToolRow(selected.diameter());
                    row.operation = selected.operation();
                    row.toolProfile = selected.toolProfile();
                    row.machining = selected.machining();
                    NccToolSettings settings = selected.settings();
                    row.overlapPercent = format(settings.overlapFraction() * 100);
                    row.method = settings.method();
                    row.seedPolicy = settings.seedPolicy();
                    row.connect = settings.connect();
                    row.contour = settings.contour();
                    row.offsetEnabled = settings.copperOffset() > 0;
                    row.offset = format(settings.copperOffset());
                    tools.add(row);
                    toolTable.getSelectionModel().clearAndSelect(tools.size() - 1);
                    toolError.setText("");
                });
            } catch (RuntimeException error) {
                toolError.setText("Falha ao ler Tools Database: " + error.getMessage());
            }
        });
        newDiaField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                addTool.run();
            }
        });
        removeToolButton.setOnAction(e -> {
            List<ToolRow> selected = List.copyOf(toolTable.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                return;
            }
            if (tools.size() - selected.size() < 1) {
                toolError.setText("E preciso manter ao menos uma ferramenta.");
                return;
            }
            tools.removeAll(selected);
            toolTable.getSelectionModel().selectFirst();
            toolError.setText("");
        });

        ToggleGroup operationGroup = new ToggleGroup();
        RadioButton clearRadio = radio("Clear", operationGroup);
        RadioButton isoRadio = radio("Isolation", operationGroup);
        clearRadio.setSelected(true);
        isoRadio.setDisable(!sourceCombo.getValue().gerber());
        HBox operationRow = new HBox(10, new Label("Operation:"), clearRadio, isoRadio);
        operationRow.setAlignment(Pos.CENTER_LEFT);
        Tooltip operationHelp = tooltip("Clear: limpa a area nao-cobre. Isolation: contorna o cobre antes da limpeza.");
        clearRadio.setTooltip(operationHelp);
        isoRadio.setTooltip(operationHelp);
        Label selectedToolTitle = sectionTitle("Parameters for: Tool 1");
        Runnable updateOperation = () -> {
            List<ToolRow> selected = toolTable.getSelectionModel().getSelectedItems();
            int index = selected.isEmpty() ? -1 : tools.indexOf(selected.get(0));
            selectedToolTitle.setText(selected.isEmpty() ? "Parameters for: No Tool Selected"
                    : selected.size() > 1 ? "Parameters for: Multiple Tools"
                    : "Parameters for: Tool " + (index + 1));
            NccOperation operation = selected.isEmpty() ? null : selected.get(0).operation;
            NccOperation firstOperation = operation;
            if (firstOperation != null && selected.stream().anyMatch(row -> row.operation != firstOperation))
                operation = null;
            operationGroup.selectToggle(operation == NccOperation.CLEAR ? clearRadio
                    : operation == NccOperation.ISO ? isoRadio : null);
        };
        toolTable.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ToolRow>) change -> updateOperation.run());
        clearRadio.setOnAction(event -> {
            boolean hadVTool = toolTable.getSelectionModel().getSelectedItems().stream()
                    .anyMatch(row -> row.toolProfile == ToolProfile.V);
            toolTable.getSelectionModel().getSelectedItems().forEach(row -> {
                row.operation = NccOperation.CLEAR;
                if (row.toolProfile == ToolProfile.V) row.toolProfile = ToolProfile.C1;
            });
            toolTable.refresh();
            if (hadVTool) toolError.setText("TT V mudou para C1: V exige operacao Isolation.");
        });
        isoRadio.setOnAction(event -> {
            toolTable.getSelectionModel().getSelectedItems().forEach(row -> row.operation = NccOperation.ISO);
        });
        updateOperation.run();
        refreshOperation[0] = updateOperation;
        ToggleGroup millingGroup = new ToggleGroup();
        RadioButton climbRadio = radio("Climb", millingGroup);
        RadioButton conventionalRadio = radio("Conventional", millingGroup);
        ("CONVENTIONAL".equals(ToolDefaults.choice("ncc.milling")) ? conventionalRadio : climbRadio).setSelected(true);
        HBox millingRow = new HBox(10, new Label("Milling Type:"), climbRadio, conventionalRadio);
        millingRow.setAlignment(Pos.CENTER_LEFT);
        Tooltip millingHelp = tooltip("Sentido de fresagem dos contornos ISO; e comum a todas as ferramentas ISO.");
        climbRadio.setTooltip(millingHelp);
        conventionalRadio.setTooltip(millingHelp);

        ComboBox<String> boundaryKindCombo = new ComboBox<>();
        boundaryKindCombo.setId("ncc-boundary-kind");
        boundaryKindCombo.getItems().addAll(BOUNDARY_ITSELF, BOUNDARY_AREA);
        if (sourceCandidates.size() > 1) {
            boundaryKindCombo.getItems().add(BOUNDARY_REFERENCE);
        }
        boundaryKindCombo.setValue(BOUNDARY_ITSELF);
        boundaryKindCombo.setTooltip(tooltip(
                "Itself: usa o contorno convexo da origem como limite.\n"
                + "Area Selection: delimita um retangulo ou poligono no desenho.\n"
                + "Reference Object: usa outro objeto (Gerber ou Geometry) ja carregado como limite."));
        ComboBox<ReferenceCandidate> referenceCombo = new ComboBox<>();
        referenceCombo.setId("ncc-reference-object");
        ToolDescriptions.apply(referenceCombo,"Limite de referência", "Gerber ou Geometry alinhado que limita a região de limpeza. Usado no modo Reference Object; não substitui o cobre de origem.");
        referenceCombo.setMinWidth(0);
        referenceCombo.setPrefWidth(180);
        referenceCombo.setMaxWidth(Double.MAX_VALUE);
        Runnable updateReferences = () -> {
            ReferenceCandidate previous = referenceCombo.getValue();
            SourceCandidate selectedSource = sourceCombo.getValue();
            List<ReferenceCandidate> available = referenceCandidates.stream()
                    .filter(candidate -> selectedSource == null || candidate.item() != selectedSource.item())
                    .toList();
            referenceCombo.getItems().setAll(available);
            referenceCombo.setValue(available.contains(previous) ? previous
                    : available.isEmpty() ? null : available.get(0));
        };
        updateReferences.run();
        javafx.beans.binding.BooleanBinding referenceChosen = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> BOUNDARY_REFERENCE.equals(boundaryKindCombo.getValue()), boundaryKindCombo.valueProperty());
        referenceCombo.visibleProperty().bind(referenceChosen);
        referenceCombo.managedProperty().bind(referenceChosen);
        javafx.beans.binding.BooleanBinding areaChosen = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> BOUNDARY_AREA.equals(boundaryKindCombo.getValue()), boundaryKindCombo.valueProperty());
        Geometry[] selectedArea = {null};
        ToggleGroup areaShapeGroup = new ToggleGroup();
        RadioButton rectangleShapeRadio = radio("Retangulo", areaShapeGroup);
        RadioButton polygonShapeRadio = radio("Poligono", areaShapeGroup);
        rectangleShapeRadio.setSelected(true);
        HBox areaShapeRow = new HBox(8, rectangleShapeRadio, polygonShapeRadio);
        areaShapeRow.visibleProperty().bind(areaChosen);
        areaShapeRow.managedProperty().bind(areaChosen);
        Button selectAreaButton = new Button("Selecionar area no desenho");
        Label selectedAreaLabel = new Label("Nenhuma area selecionada.");
        selectedAreaLabel.setWrapText(true);
        areaShapeGroup.selectedToggleProperty().addListener((observable, oldShape, newShape) -> {
            cancelArea.run();
            selectedArea[0] = null;
            selectedAreaLabel.setText("Nenhuma area selecionada.");
        });
        boundaryKindCombo.valueProperty().addListener((observable, oldKind, newKind) -> {
            cancelArea.run();
            selectedArea[0] = null;
            selectedAreaLabel.setText("Nenhuma area selecionada.");
        });
        selectAreaButton.visibleProperty().bind(areaChosen);
        selectAreaButton.managedProperty().bind(areaChosen);
        selectedAreaLabel.visibleProperty().bind(areaChosen);
        selectedAreaLabel.managedProperty().bind(areaChosen);
        selectAreaButton.setOnAction(event -> {
            selectedArea[0] = null;
            AreaShape requestedShape = polygonShapeRadio.isSelected()
                    ? AreaShape.POLYGON : AreaShape.RECTANGLE;
            selectedAreaLabel.setText(requestedShape == AreaShape.POLYGON
                    ? "Clique nos vertices; Enter ou botao direito conclui, Esc cancela."
                    : "Clique em dois cantos opostos no Plot Area; Esc cancela.");
            SourceCandidate areaSource = sourceCombo.getValue();
            if (!areaSelector.select(areaSource, requestedShape, area -> {
                if (sourceCombo.getValue() != areaSource) return;
                if ((polygonShapeRadio.isSelected() ? AreaShape.POLYGON : AreaShape.RECTANGLE)
                        != requestedShape) {
                    return;
                }
                selectedArea[0] = area;
                selectedAreaLabel.setText("Area: " + area.getEnvelopeInternal());
            }, () -> selectedAreaLabel.setText("Selecao de area cancelada."))) {
                selectedAreaLabel.setText("Nao foi possivel iniciar a selecao de area.");
            }
        });

        GridPane boundaryGrid = new GridPane();
        boundaryGrid.setHgap(8);
        boundaryGrid.setVgap(8);
        boundaryGrid.addRow(0, new Label("Selection:"), boundaryKindCombo);
        boundaryGrid.add(referenceCombo, 1, 1);
        boundaryGrid.add(areaShapeRow, 1, 2);
        boundaryGrid.add(selectAreaButton, 1, 3);
        boundaryGrid.add(selectedAreaLabel, 1, 4);

        CheckBox checkValidityCb = new CheckBox("Verificar validade dos diametros");
        checkValidityCb.setSelected(initialSource.gerber());
        checkValidityCb.setDisable(!initialSource.gerber());
        checkValidityCb.setTooltip(tooltip(
                "Se marcado, compara cada diametro com a menor distancia entre elementos de\n"
                + "cobre do Gerber e avisa se alguma ferramenta e grande demais para fazer um\n"
                + "isolamento completo. Apenas informativo - nao impede a geracao."));

        Spinner<Double> overlapSpinner = spinner(0, 99.9999, ToolDefaults.number("ncc.overlap", true), 0.1);
        TextField overlapField = overlapSpinner.getEditor();
        Spinner<Double> marginSpinner = spinner(0, 10_000, ToolDefaults.number("ncc.margin", metric),
                metric ? 0.1 : 0.01);
        TextField marginField = marginSpinner.getEditor();
        ComboBox<NccMethod> methodCombo = new ComboBox<>();
        methodCombo.setId("ncc-method");
        methodCombo.getItems().addAll(NccMethod.values());
        methodCombo.setValue(ToolDefaults.choice("ncc.method", NccMethod.class));
        methodCombo.setTooltip(tooltip(
                "Standard: passes concentricas para dentro.\n"
                + "Seed: aneis crescentes a partir de um ponto central.\n"
                + "Lines: varredura paralela (raster).\n"
                + "Combo: tenta Lines, depois Seed, depois Standard."));
        ComboBox<NccSeedPolicy> seedPolicyCombo = new ComboBox<>();
        seedPolicyCombo.setId("ncc-seed-policy");
        seedPolicyCombo.getItems().addAll(NccSeedPolicy.values());
        seedPolicyCombo.setValue(ToolDefaults.choice("ncc.seedpolicy", NccSeedPolicy.class));
        seedPolicyCombo.setMinWidth(0);
        seedPolicyCombo.setPrefWidth(180);
        seedPolicyCombo.setMaxWidth(Double.MAX_VALUE);
        seedPolicyCombo.setTooltip(tooltip("Ponto inicial dos aneis Seed (tambem usado no fallback Combo).\n\n"
                + "Estavel (FX): conserva o comportamento atual e reduz mudancas bruscas por pequenas variacoes da geometria.\n"
                + "Representativo (Python): usa a mesma regra de scan-line do legado, para reproduzir seus aneis. "
                + "Pequenas diferencas numericas na entrada ainda podem mudar o ponto e os trajetos.\n\n"
                + "Esta escolha pertence a cada ferramenta; Standard e Lines nao a utilizam."));
        CheckBox connectCb = new CheckBox("Connect");
        connectCb.setSelected(ToolDefaults.flag("ncc.connect"));
        connectCb.setTooltip(tooltip("Une trajetos proximos quando o percurso de ligacao continua dentro da area segura da ferramenta."));
        CheckBox contourCb = new CheckBox("Contour");
        contourCb.setSelected(ToolDefaults.flag("ncc.contour"));
        contourCb.setTooltip(tooltip("Adiciona um passe final contornando a borda interna da area limpa."));
        CheckBox offsetCb = new CheckBox("Copper offset");
        offsetCb.setTooltip(tooltip("Ativa uma distância adicional de proteção ao redor das trilhas e pads. Habilita o valor de Offset.\n\nNão é a margem: Margin define o limite externo da área de limpeza; Offset protege o cobre dentro dessa área."));
        Spinner<Double> offsetSpinner = spinner(0, 10, 0, metric ? 0.1 : 0.01);
        TextField offsetField = offsetSpinner.getEditor();
        CheckBox restCb = new CheckBox("Rest Machining");
        restCb.setStyle("-fx-font-weight: bold;");
        restCb.setSelected(ToolDefaults.flag("ncc.rest"));
        restCb.setTooltip(tooltip(
                "Quando ativado, as ferramentas sao processadas da maior para a menor e cada uma\n"
                + "so limpa o que a anterior, maior, nao conseguiu alcancar. Forca a ordem Reverse\n"
                + "e desativa o controle de Order abaixo, assim como no FlatCAM Python."));
        ToggleGroup orderGroup = new ToggleGroup();
        RadioButton noOrderRadio = radio("No", orderGroup);
        RadioButton forwardOrderRadio = radio("Forward", orderGroup);
        RadioButton reverseOrderRadio = radio("Reverse", orderGroup);
        Runnable defaultOrder = () -> (switch (ToolDefaults.choice("ncc.order")) {
            case "NONE" -> noOrderRadio;
            case "FORWARD" -> forwardOrderRadio;
            default -> reverseOrderRadio;
        }).setSelected(true);
        defaultOrder.run();
        Tooltip orderHelp = tooltip(
                "No: usa a ordem da tabela acima.\n"
                + "Forward: da menor para a maior ferramenta.\n"
                + "Reverse: da maior para a menor ferramenta.");
        for (RadioButton radio : List.of(noOrderRadio, forwardOrderRadio, reverseOrderRadio))
            radio.setTooltip(orderHelp);
        // Rest Machining always forces largest-tool-first, same as Python
        // disabling its order radio the moment Rest Machining is checked.
        for (RadioButton radio : List.of(noOrderRadio, forwardOrderRadio, reverseOrderRadio))
            radio.disableProperty().bind(restCb.selectedProperty());
        Label restHint = new Label("Rest usa a ordem Reverse.");
        restHint.setStyle("-fx-font-size: 10px; -fx-opacity: 0.75;");
        restHint.setWrapText(true);
        restHint.visibleProperty().bind(restCb.selectedProperty());
        restHint.managedProperty().bind(restHint.visibleProperty());

        BooleanBinding noSingleTool = Bindings.createBooleanBinding(
                () -> toolTable.getSelectionModel().getSelectedItems().size() != 1,
                toolTable.getSelectionModel().getSelectedItems());
        BooleanBinding noClearTool = noSingleTool.or(isoRadio.selectedProperty());
        seedPolicyCombo.disableProperty().bind(noClearTool.or(Bindings.createBooleanBinding(
                () -> methodCombo.getValue() != NccMethod.SEED && methodCombo.getValue() != NccMethod.COMBO,
                methodCombo.valueProperty())));
        overlapSpinner.disableProperty().bind(noClearTool);
        methodCombo.disableProperty().bind(noClearTool);
        connectCb.disableProperty().bind(noClearTool.or(restCb.selectedProperty()));
        contourCb.disableProperty().bind(noClearTool.or(restCb.selectedProperty()));
        offsetCb.disableProperty().bind(noClearTool.or(restCb.selectedProperty()));
        offsetSpinner.disableProperty().bind(noClearTool.or(restCb.selectedProperty())
                .or(offsetCb.selectedProperty().not()));
        clearRadio.disableProperty().bind(Bindings.isEmpty(toolTable.getSelectionModel().getSelectedItems()));
        isoRadio.disableProperty().bind(Bindings.isEmpty(toolTable.getSelectionModel().getSelectedItems())
                .or(Bindings.createBooleanBinding(() -> sourceCombo.getValue() == null
                        || !sourceCombo.getValue().gerber(), sourceCombo.valueProperty())));
        climbRadio.disableProperty().bind(noSingleTool.or(isoRadio.selectedProperty().not()));
        conventionalRadio.disableProperty().bind(noSingleTool.or(isoRadio.selectedProperty().not()));

        boolean[] loadingToolSettings = {false};
        Runnable loadSelectedSettings = () -> {
            List<ToolRow> selected = toolTable.getSelectionModel().getSelectedItems();
            if (selected.size() != 1) {
                return;
            }
            ToolRow row = selected.get(0);
            loadingToolSettings[0] = true;
            overlapField.setText(row.overlapPercent);
            try { overlapSpinner.getValueFactory().setValue(parseNumber(row.overlapPercent, "Overlap")); }
            catch (IllegalArgumentException ignored) { /* Preserve invalid draft for validation on Generate. */ }
            methodCombo.setValue(row.method);
            seedPolicyCombo.setValue(row.seedPolicy);
            connectCb.setSelected(row.connect);
            contourCb.setSelected(row.contour);
            offsetCb.setSelected(row.offsetEnabled);
            offsetField.setText(row.offset);
            try { offsetSpinner.getValueFactory().setValue(parseNumber(row.offset, "Offset")); }
            catch (IllegalArgumentException ignored) { /* Preserve invalid draft for validation on Generate. */ }
            loadingToolSettings[0] = false;
        };
        toolTable.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ToolRow>) change -> loadSelectedSettings.run());
        loadSelectedSettings.run();
        overlapField.textProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().overlapPercent = value;
            }
        });
        methodCombo.valueProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && value != null
                    && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().method = value;
            }
        });
        connectCb.selectedProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().connect = value;
            }
        });
        seedPolicyCombo.valueProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && value != null && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().seedPolicy = value;
            }
        });
        contourCb.selectedProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().contour = value;
            }
        });
        offsetCb.selectedProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().offsetEnabled = value;
            }
        });
        offsetField.textProperty().addListener((observable, oldValue, value) -> {
            if (!loadingToolSettings[0] && toolTable.getSelectionModel().getSelectedItems().size() == 1) {
                toolTable.getSelectionModel().getSelectedItem().offset = value;
            }
        });

        CheckBox restConnectCb = new CheckBox("Connect");
        restConnectCb.setSelected(true);
        CheckBox restContourCb = new CheckBox("Contour");
        restContourCb.setSelected(true);
        CheckBox restOffsetCb = new CheckBox("Copper offset");
        Spinner<Double> restOffsetSpinner = spinner(0, 10, 0, metric ? 0.1 : 0.01);
        TextField restOffsetField = restOffsetSpinner.getEditor();
        restOffsetSpinner.disableProperty().bind(restOffsetCb.selectedProperty().not());
        GridPane restSettingsGrid = new GridPane();
        restSettingsGrid.setHgap(8);
        restSettingsGrid.setVgap(8);
        restSettingsGrid.addRow(0, restConnectCb, restContourCb);
        restSettingsGrid.addRow(1, restOffsetCb, restOffsetSpinner);
        restSettingsGrid.visibleProperty().bind(restCb.selectedProperty());
        restSettingsGrid.managedProperty().bind(restSettingsGrid.visibleProperty());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Overlap (%):"), overlapSpinner);
        grid.addRow(1, new Label("Method:"), methodCombo);
        grid.addRow(2, new Label("Margin (comum):"), marginSpinner);
        grid.addRow(3, connectCb, contourCb);
        grid.addRow(4, offsetCb, offsetSpinner);
        grid.addRow(5, new Label("Seed inicial:"), seedPolicyCombo);

        GridPane restGrid = new GridPane();
        restGrid.setHgap(8);
        restGrid.setVgap(8);
        restGrid.addRow(0, restCb);
        restGrid.add(restHint, 0, 1);
        restGrid.add(restSettingsGrid, 0, 2);

        Button applyAllButton = new Button("Aplicar parametros a todas as ferramentas");
        applyAllButton.setMaxWidth(Double.MAX_VALUE);
        applyAllButton.setWrapText(true);
        applyAllButton.disableProperty().bind(noSingleTool.or(Bindings.size(tools).lessThan(2)));
        applyAllButton.setTooltip(tooltip("Copia a operacao e os parametros da ferramenta selecionada para toda a tabela."));
        applyAllButton.setOnAction(event -> {
            ToolRow selected = toolTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            try {
                selected.settings();
                for (ToolRow row : tools) {
                    if (row == selected) continue;
                    row.operation = selected.operation;
                    row.overlapPercent = selected.overlapPercent;
                    row.method = selected.method;
                    row.seedPolicy = selected.seedPolicy;
                    row.connect = selected.connect;
                    row.contour = selected.contour;
                    row.offsetEnabled = selected.offsetEnabled;
                    row.offset = selected.offset;
                }
                toolError.setText("Parametros copiados para todas as ferramentas.");
            } catch (IllegalArgumentException invalid) {
                toolError.setText(invalid.getMessage());
            }
        });

        sourceCombo.valueProperty().addListener((observable, oldSource, newSource) -> {
            if (newSource == null) return;
            cancelArea.run();
            selectedArea[0] = null;
            selectedAreaLabel.setText("Nenhuma area selecionada.");
            updateReferences.run();
            optimalButton.setDisable(!newSource.gerber());
            checkValidityCb.setDisable(!newSource.gerber());
            checkValidityCb.setSelected(newSource.gerber());
            if (!newSource.gerber()) {
                boolean changed = false;
                for (ToolRow row : tools) {
                    if (row.operation == NccOperation.ISO) {
                        row.operation = NccOperation.CLEAR;
                        changed = true;
                    }
                    if (row.toolProfile == ToolProfile.V) {
                        row.toolProfile = ToolProfile.C1;
                        changed = true;
                    }
                }
                if (changed) toolError.setText("Ferramentas ISO/V mudaram para Clear/C1: Geometry nao aceita ISO/V.");
                toolTable.refresh();
            }
            updateOperation.run();
        });

        Label note = new Label("Selecione na tabela as ferramentas a executar. ISO contorna o cobre; "
                + "ao menos uma ferramenta CLEAR e necessaria. O objeto Geometry tera uma entrada por ferramenta.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);
        Button generateButton = new Button("Gerar Geometry");
        generateButton.setId("ncc-generate");
        generateButton.getStyleClass().add("primary-action");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                SourceCandidate chosenSource = sourceCombo.getValue();
                if (chosenSource == null) throw new IllegalArgumentException("Selecione um objeto de origem.");
                double margin = parse(marginField, "Margin");
                NccBoundary boundary = new NccBoundary.Itself();
                if (BOUNDARY_AREA.equals(boundaryKindCombo.getValue())) {
                    if (selectedArea[0] == null) {
                        throw new IllegalArgumentException("Selecione uma area no desenho.");
                    }
                    boundary = new NccBoundary.Area(selectedArea[0]);
                } else if (BOUNDARY_REFERENCE.equals(boundaryKindCombo.getValue())) {
                    ReferenceCandidate candidate = referenceCombo.getValue();
                    if (candidate == null) {
                        throw new IllegalArgumentException("Selecione um objeto de referencia para o Boundary.");
                    }
                    boundary = candidate.isGerber()
                            ? new NccBoundary.ReferenceGerber(candidate.geometry())
                            : new NccBoundary.ReferenceGeometry(candidate.geometry());
                }
                List<Double> clearDiameters = new java.util.ArrayList<>();
                List<Double> isoDiameters = new java.util.ArrayList<>();
                Map<Double, NccToolSettings> individualSettings = new LinkedHashMap<>();
                Map<Double, ToolProfile> selectedProfiles = new LinkedHashMap<>();
                Map<Double, LegacyToolsDatabase.MillingTool> machining = new LinkedHashMap<>();
                for (int i = 0; i < tools.size(); i++) {
                    if (toolTable.getSelectionModel().isSelected(i)) {
                        ToolRow row = tools.get(i);
                        selectedProfiles.put(row.diameter, row.toolProfile);
                        if (row.machining != null) {
                            if (row.machining.profile() != row.toolProfile)
                                throw new IllegalArgumentException("TT mudou apos importar a DB; remova/reimporte a ferramenta.");
                            machining.put(row.diameter, row.machining);
                        }
                        if (row.operation == NccOperation.ISO) {
                            isoDiameters.add(row.diameter);
                        } else {
                            clearDiameters.add(row.diameter);
                            individualSettings.put(row.diameter, row.settings());
                        }
                    }
                }
                if (clearDiameters.isEmpty()) {
                    throw new IllegalArgumentException("Selecione ao menos uma ferramenta CLEAR na tabela.");
                }
                NccToolSettings first = individualSettings.get(clearDiameters.get(0));
                double commonOffset = restCb.isSelected()
                        ? (restOffsetCb.isSelected() ? parse(restOffsetField, "Rest copper offset") : 0)
                        : first.copperOffset();
                NccParameters params = new NccParameters(clearDiameters, first.overlapFraction(), margin,
                        first.method(), restCb.isSelected() ? restConnectCb.isSelected() : first.connect(),
                        restCb.isSelected() ? restContourCb.isSelected() : first.contour(), commonOffset,
                        restCb.isSelected(), forwardOrderRadio.isSelected() ? NccOrder.FORWARD
                                : reverseOrderRadio.isSelected() ? NccOrder.REVERSE : NccOrder.NONE,
                        boundary, isoDiameters, individualSettings,
                        conventionalRadio.isSelected() ? NccMillingType.CONVENTIONAL : NccMillingType.CLIMB);
                errorLabel.setText("");
                onGenerate.accept(new Result(chosenSource, params, checkValidityCb.isSelected(),
                        Map.copyOf(selectedProfiles), BOUNDARY_REFERENCE.equals(boundaryKindCombo.getValue())
                                ? referenceCombo.getValue() : null, Map.copyOf(machining)));
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        HBox orderRow = new HBox(8, new Label("Tool order:"), noOrderRadio,
                forwardOrderRadio, reverseOrderRadio);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        HBox diameterRow = new HBox(6, new Label("Tool Dia:"), newDiaSpinner, optimalButton);
        diameterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(newDiaSpinner, Priority.ALWAYS);
        HBox addRow = new HBox(6, addToolButton, databaseButton);
        addToolButton.setMaxWidth(Double.MAX_VALUE);
        databaseButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(addToolButton, Priority.ALWAYS);
        HBox.setHgrow(databaseButton, Priority.ALWAYS);
        removeToolButton.setMaxWidth(Double.MAX_VALUE);
        Button resetButton = new Button("Reset Tool");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(event -> {
            cancelArea.run();
            (initialSource.gerber() ? gerberSourceRadio : geometrySourceRadio).setSelected(true);
            sourceCombo.setValue(initialSource);
            tools.setAll(defaultTools(metric));
            toolTable.getSelectionModel().clearAndSelect(0);
            newDiaSpinner.getValueFactory().setValue(ToolDefaults.number("ncc.newdia", metric));
            marginSpinner.getValueFactory().setValue(ToolDefaults.number("ncc.margin", metric));
            restCb.setSelected(ToolDefaults.flag("ncc.rest"));
            defaultOrder.run();
            ("CONVENTIONAL".equals(ToolDefaults.choice("ncc.milling")) ? conventionalRadio : climbRadio).setSelected(true);
            restConnectCb.setSelected(true);
            restContourCb.setSelected(true);
            restOffsetCb.setSelected(false);
            restOffsetSpinner.getValueFactory().setValue(0.0);
            boundaryKindCombo.setValue(BOUNDARY_ITSELF);
            rectangleShapeRadio.setSelected(true);
            selectedArea[0] = null;
            selectedAreaLabel.setText("Nenhuma area selecionada.");
            checkValidityCb.setSelected(initialSource.gerber());
            toolError.setText("");
            errorLabel.setText("");
            updateOperation.run();
            loadSelectedSettings.run();
        });
        HBox finalButtons = new HBox(6, resetButton, closeButton);
        HBox.setHgrow(resetButton, Priority.ALWAYS);
        HBox.setHgrow(closeButton, Priority.ALWAYS);
        Label title = new Label("Non-Copper Clearing");
        title.getStyleClass().add("tool-title");
        VBox box = new VBox(8, title, sourceGrid, new Separator(),
                sectionTitle("Tools Table"), toolTable, orderRow, new Separator(),
                sectionTitle("Add from DB"), diameterRow, addRow, removeToolButton,
                toolError, new Separator(), selectedToolTitle, operationRow, millingRow,
                grid, applyAllButton, new Separator(), sectionTitle("Common Parameters"),
                restGrid, boundaryGrid, checkValidityCb,
                new Separator(), note, errorLabel, generateButton, finalButtons);
        box.setPadding(new Insets(12));
        return box;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-section-title");
        return label;
    }

    private static RadioButton radio(String text, ToggleGroup group) {
        RadioButton button = new RadioButton(text);
        button.setToggleGroup(group);
        return button;
    }

    /** The tools the panel opens with: the diameters of the "ncc.tooldia" default, each with the default settings. */
    private static List<ToolRow> defaultTools(boolean metric) {
        List<ToolRow> rows = new java.util.ArrayList<>();
        for (double diameter : ToolDefaults.numbers("ncc.tooldia", metric)) rows.add(new ToolRow(diameter));
        return rows;
    }

    private static Spinner<Double> spinner(double min, double max, double initial, double step) {
        SpinnerValueFactory.DoubleSpinnerValueFactory values =
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);
        values.setConverter(new StringConverter<>() {
            @Override public String toString(Double value) {
                return value == null ? "" : format(value);
            }
            @Override public Double fromString(String text) {
                return parseNumber(text, "Valor");
            }
        });
        Spinner<Double> spinner = new Spinner<>(values);
        spinner.setEditable(true);
        spinner.setMinWidth(0);
        spinner.setPrefWidth(115);
        spinner.getEditor().setMinWidth(0);
        return spinner;
    }

    /**
     * JavaFX's default Tooltip hides itself after a few seconds even while
     * the mouse stays put (showDuration defaults to 5s) - too short for the
     * longer, multi-line explanations used in this panel. Keep it open for
     * as long as the mouse actually hovers instead.
     */
    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDuration(Duration.INDEFINITE);
        return tooltip;
    }

    private static double parse(TextField field, String name) {
        return parseNumber(field.getText(), name);
    }

    private static double parseNumber(String value, String name) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": numero invalido");
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
