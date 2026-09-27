package org.flatcam.fx;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Coordinate;

/** Canvas selection/deletion for a Geometry object; Apply updates the source object atomically. */
final class GeometryEditorController {

    interface Host {
        void openToolPanel(String label, Node content);

        void closeToolPanel();

        void showEditorToolbar(Node toolbar);

        void hideEditorToolbar();

        Node icon(String fileName);

        void setObjectVisible(TreeItem<String> item, boolean visible);

        void apply(TreeItem<String> item, Geometry geometry, List<ToolGeometry> tools);

        boolean runOperation(GeometryEditSession.OperationRequest request,
                             Consumer<GeometryEditSession.OperationResult> onSuccess,
                             Consumer<String> onFailure);

        void log(String message);
    }

    private record LayerKey(String name) {
    }

    private static final LayerKey SHAPES_LAYER = new LayerKey("geometry-editor-shapes");
    private static final Color SHAPE_COLOR = Color.web("#FF0000AF");
    private static final int EXACT_HIGHLIGHT_LIMIT = 2_000;

    private final PlotAreaView plotArea;
    private final Host host;
    private TreeItem<String> item;
    private GeometryEditSession session;
    private Label status;
    private Label instruction;
    private ComboBox<String> toolChoice;
    private int implicitToolIndex;
    private TableView<GeometryEditSession.ShapeRow> shapeTable;
    private final Set<Integer> tableSelectionOrder = new LinkedHashSet<>();
    private boolean syncingTable;
    private Button deleteButton;
    private Button moveButton;
    private Button copyButton;
    private Button undoButton;
    private Button redoButton;
    private Button applyButton;
    private Button exitButton;
    private Button discardButton;
    private Button unionButton;
    private Button intersectionButton;
    private Button subtractButton;
    private Button cutPathButton;
    private Button bufferButton;
    private Button explodeButton;
    private TextField bufferDistance;
    private ComboBox<String> bufferMode;
    private List<Button> editorButtons;
    private boolean busy;
    private boolean initiallyVisible;
    private boolean strokeOnly;

    GeometryEditorController(PlotAreaView plotArea, Host host) {
        this.plotArea = plotArea;
        this.host = host;
    }

    boolean isActive() {
        return session != null;
    }

    boolean hasUnappliedChanges() {
        return isActive() && session.isDirty();
    }

    boolean isBusy() {
        return busy;
    }

    boolean isEditing(TreeItem<String> target) {
        return item == target;
    }

    void start(TreeItem<String> sourceItem, Geometry geometry, List<ToolGeometry> tools, boolean strokeOnly) {
        if (isActive()) {
            host.log("Ja existe uma edicao de Geometry em andamento.");
            return;
        }
        item = sourceItem;
        session = new GeometryEditSession(geometry, tools);
        implicitToolIndex = tools.isEmpty() ? -1 : 0;
        initiallyVisible = plotArea.isLayerVisible(sourceItem);
        this.strokeOnly = strokeOnly;
        host.setObjectVisible(sourceItem, false);
        plotArea.putLayer(SHAPES_LAYER, PlotAreaView.LayerCategory.OVERLAY,
                session.resultGeometry(), SHAPE_COLOR, SHAPE_COLOR, strokeOnly);
        plotArea.setSelectionHandler(new PlotAreaView.SelectionHandler() {
            @Override
            public void onClick(double x, double y, boolean additive) {
                if (busy) {
                    return;
                }
                session.clickSelect(x, y, plotArea.pickToleranceWorld(), additive);
                refreshSelection();
            }

            @Override
            public void onBox(double x1, double y1, double x2, double y2, boolean additive) {
                if (busy) {
                    return;
                }
                session.boxSelect(x1, y1, x2, y2, additive);
                refreshSelection();
            }
        });

        status = new Label();
        instruction = new Label("Selecione uma linha na tabela ou uma forma no desenho.");
        instruction.setWrapText(true);
        shapeTable = new TableView<>(FXCollections.observableArrayList(session.shapeRows()));
        shapeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        shapeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<GeometryEditSession.ShapeRow, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(Long.toString(row.getValue().id())));
        idColumn.setPrefWidth(72);
        idColumn.setSortable(false);
        TableColumn<GeometryEditSession.ShapeRow, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().type()));
        typeColumn.setPrefWidth(65);
        typeColumn.setSortable(false);
        TableColumn<GeometryEditSession.ShapeRow, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().name()));
        nameColumn.setPrefWidth(95);
        nameColumn.setSortable(false);
        shapeTable.getColumns().addAll(idColumn, typeColumn, nameColumn);
        shapeTable.setPrefHeight(360);
        shapeTable.setTooltip(new Tooltip("Clique para selecionar; Ctrl+clique seleciona varias linhas. "
                + "Delete exclui as formas selecionadas no desenho."));
        shapeTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change -> {
            if (syncingTable || busy || session == null) {
                return;
            }
            Set<Integer> current = new LinkedHashSet<>(shapeTable.getSelectionModel().getSelectedIndices());
            tableSelectionOrder.retainAll(current);
            for (int index : current) {
                tableSelectionOrder.add(index);
            }
            syncingTable = true;
            session.selectIndices(tableSelectionOrder);
            refreshSelection();
            syncingTable = false;
        });
        Button selectButton = new Button("Selecionar");
        Button pathButton = new Button("Caminho");
        Button polygonButton = new Button("Poligono");
        Button rectangleButton = new Button("Retangulo");
        Button circleButton = new Button("Circulo");
        Button arcButton = new Button("Arco");
        deleteButton = new Button("Excluir selecionadas");
        moveButton = new Button("Mover selecionadas");
        copyButton = new Button("Copiar selecionadas");
        undoButton = new Button("Desfazer");
        redoButton = new Button("Refazer");
        applyButton = new Button("Aplicar ao objeto");
        unionButton = new Button("Unir selecionadas");
        intersectionButton = new Button("Intersecao selecionadas");
        subtractButton = new Button("Subtrair da primeira selecionada");
        cutPathButton = new Button("Cortar Caminho");
        bufferButton = new Button("Criar buffer arredondado");
        explodeButton = new Button("Explodir poligonos");
        bufferDistance = new TextField();
        bufferDistance.setPromptText("Distancia positiva (unidade do projeto)");
        bufferMode = new ComboBox<>();
        bufferMode.getItems().addAll("Completo", "Interior", "Exterior");
        bufferMode.getSelectionModel().selectFirst();
        bufferMode.setMaxWidth(Double.MAX_VALUE);
        Button cancelButton = new Button("Cancelar edicao");
        editorButtons = List.of(selectButton, pathButton, polygonButton, rectangleButton, circleButton, arcButton,
                deleteButton, moveButton, copyButton, unionButton, intersectionButton, subtractButton,
                bufferButton, explodeButton, cutPathButton, undoButton, redoButton, applyButton, cancelButton);
        iconize(selectButton, "pointer32.png");
        iconize(pathButton, "path32.png");
        iconize(polygonButton, "polygon32.png");
        iconize(rectangleButton, "rectangle32.png");
        iconize(circleButton, "circle32.png");
        iconize(arcButton, "arc32.png");
        iconize(deleteButton, "trash32.png");
        iconize(moveButton, "move32.png");
        iconize(copyButton, "copy32.png");
        undoButton.setText("↶");
        undoButton.setTooltip(new Tooltip("Desfazer (Ctrl+Z)"));
        redoButton.setText("↷");
        redoButton.setTooltip(new Tooltip("Refazer (Ctrl+Y)"));
        iconize(applyButton, "close_edit_file32.png");
        iconize(unionButton, "union32.png");
        iconize(intersectionButton, "intersection32.png");
        iconize(subtractButton, "subtract32.png");
        iconize(cutPathButton, "cutpath32.png");
        iconize(bufferButton, "buffer16-2.png");
        iconize(explodeButton, "explode32.png");
        iconize(cancelButton, "power16.png");
        selectButton.setOnAction(event -> startSelection());
        pathButton.setOnAction(event -> startPath());
        polygonButton.setOnAction(event -> startPolygon());
        rectangleButton.setOnAction(event -> startRectangle());
        circleButton.setOnAction(event -> startCircle());
        arcButton.setOnAction(event -> startArc());
        deleteButton.setOnAction(event -> deleteSelected());
        moveButton.setOnAction(event -> startMove());
        copyButton.setOnAction(event -> startCopy());
        unionButton.setOnAction(event -> startUnion());
        intersectionButton.setOnAction(event -> startIntersection());
        subtractButton.setOnAction(event -> startSubtract());
        cutPathButton.setOnAction(event -> startCutPath());
        bufferButton.setOnAction(event -> startBuffer());
        explodeButton.setOnAction(event -> explode());
        undoButton.setOnAction(event -> undo());
        redoButton.setOnAction(event -> redo());
        applyButton.setOnAction(event -> apply());
        cancelButton.setOnAction(event -> cancel());
        VBox panel = new VBox(8, new Label("Geometry Editor"), shapeTable, status, instruction);
        if (tools.size() > 1) {
            toolChoice = new ComboBox<>();
            for (int i = 0; i < tools.size(); i++) {
                toolChoice.getItems().add(String.format(java.util.Locale.ROOT,
                        "Ferramenta %d - Ø %.4f", i + 1, tools.get(i).toolDiameter()));
            }
            toolChoice.getSelectionModel().selectFirst();
            toolChoice.setMaxWidth(Double.MAX_VALUE);
        }
        exitButton = new Button("Salvar e sair do editor");
        exitButton.setMaxWidth(Double.MAX_VALUE);
        exitButton.setOnAction(event -> apply());
        discardButton = new Button("Descartar alteracoes");
        discardButton.setMaxWidth(Double.MAX_VALUE);
        discardButton.setOnAction(event -> cancel());
        panel.getChildren().addAll(exitButton, discardButton);
        ToolBar toolbar = new ToolBar(selectButton, circleButton, arcButton, rectangleButton,
                new Separator(), pathButton, polygonButton,
                new Separator(), plannedToolButton("Texto", "text32.png"), bufferButton,
                plannedToolButton("Paint Shape", "paint20_1.png"),
                plannedToolButton("Borracha", "eraser26.png"),
                new Separator(), unionButton, explodeButton, intersectionButton, subtractButton,
                new Separator(), cutPathButton,
                copyButton, deleteButton, plannedToolButton("Transformacoes", "transform.png"), moveButton,
                new Separator(), undoButton, redoButton, applyButton, cancelButton);
        if (toolChoice != null) {
            toolbar.getItems().addAll(new Separator(), toolChoice);
        }
        toolbar.getItems().addAll(new Separator(), new Label("Buffer"), bufferDistance, bufferMode);
        bufferDistance.setPrefColumnCount(6);
        bufferDistance.setMaxWidth(75);
        bufferMode.setPrefWidth(90);
        bufferMode.setMaxWidth(90);
        panel.setPadding(new Insets(12));
        host.openToolPanel("Editor Geometry", panel);
        host.showEditorToolbar(toolbar);
        refreshSelection();
    }

    private void iconize(Button button, String icon) {
        String label = button.getText();
        button.setText(null);
        button.setGraphic(host.icon(icon));
        button.setTooltip(new Tooltip(label));
    }

    private Button plannedToolButton(String label, String icon) {
        Button button = new Button(null, host.icon(icon));
        button.setTooltip(new Tooltip(label + " — em desenvolvimento"));
        button.setDisable(true);
        return button;
    }

    boolean deleteFromShortcut() {
        return deleteSelected();
    }

    boolean undoFromShortcut() {
        return undo();
    }

    boolean redoFromShortcut() {
        return redo();
    }

    void startSelection() {
        if (!readyForTool()) {
            return;
        }
        plotArea.cancelPlacement();
        instruction.setText("Selecione formas no desenho; Ctrl alterna a selecao.");
    }

    void startPath() {
        startMultiPoint(false);
    }

    void startPolygon() {
        startMultiPoint(true);
    }

    void startArc() {
        if (!readyForTool()) {
            return;
        }
        GeometryEditSession editing = session;
        int toolIndex = selectedToolIndex();
        if (plotArea.beginEditorGeometryPathPlacement(false, new PlotAreaView.TrackPlacementHandler() {
            @Override
            public void onPathChanged(int anchorCount, org.flatcam.cam.gerber.edit.TrackBendMode mode) {
                if (session == editing) {
                    instruction.setText("Arco: " + anchorCount
                            + " ponto(s). Escolha inicio, ponto intermediario e fim; "
                            + "Enter ou botao direito conclui; Esc cancela.");
                }
            }

            @Override
            public void onCommit(List<Coordinate> points) {
                if (session != editing) {
                    return;
                }
                if (points.size() != 3) {
                    instruction.setText("O arco exige exatamente tres pontos: inicio, meio e fim.");
                    return;
                }
                try {
                    editing.addArc(points.get(0), points.get(1), points.get(2), toolIndex);
                    refreshGeometry();
                    instruction.setText("Arco adicionado. Ctrl+Z desfaz.");
                } catch (IllegalArgumentException invalid) {
                    instruction.setText(invalid.getMessage());
                }
            }

            @Override
            public void onCancel() {
                if (session == editing) {
                    instruction.setText("Arco cancelado.");
                }
            }
        })) {
            instruction.setText("Clique no inicio, em um ponto do arco e no fim; Enter conclui.");
        }
    }

    private void startMultiPoint(boolean polygon) {
        if (!readyForTool()) {
            return;
        }
        GeometryEditSession editing = session;
        int toolIndex = selectedToolIndex();
        if (plotArea.beginEditorGeometryPathPlacement(polygon, new PlotAreaView.TrackPlacementHandler() {
            @Override
            public void onPathChanged(int anchorCount, org.flatcam.cam.gerber.edit.TrackBendMode mode) {
                if (session == editing) {
                    instruction.setText((polygon ? "Poligono" : "Caminho") + ": " + anchorCount
                            + " ponto(s). Clique no proximo; Enter ou botao direito conclui; Esc cancela.");
                }
            }

            @Override
            public void onCommit(List<Coordinate> points) {
                if (session != editing) {
                    return;
                }
                try {
                    if (polygon) {
                        editing.addPolygon(points, toolIndex);
                    } else {
                        editing.addPath(points, toolIndex);
                    }
                    instruction.setText((polygon ? "Poligono" : "Caminho") + " adicionado.");
                    refreshGeometry();
                } catch (IllegalArgumentException invalid) {
                    instruction.setText(invalid.getMessage());
                }
            }

            @Override
            public void onCancel() {
                if (session == editing) {
                    instruction.setText("Desenho cancelado.");
                }
            }
        })) {
            host.log("Editor Geometry: clique nos pontos; Enter ou duplo clique conclui.");
        }
    }

    void startRectangle() {
        startTwoPoint(false);
    }

    void startCircle() {
        startTwoPoint(true);
    }

    private void startTwoPoint(boolean circle) {
        if (!readyForTool()) {
            return;
        }
        GeometryEditSession editing = session;
        int toolIndex = selectedToolIndex();
        double[] anchor = new double[2];
        boolean started = plotArea.beginEditorTwoPointPlacement(
                circle ? PlotAreaView.TwoPointShape.CIRCLE : PlotAreaView.TwoPointShape.RECTANGLE,
                new PlotAreaView.PlacementHandler() {
                    @Override
                    public void onAnchorChosen(double x, double y) {
                        anchor[0] = x;
                        anchor[1] = y;
                        if (session == editing) {
                            instruction.setText(circle ? "Clique no perimetro do circulo."
                                    : "Clique no canto oposto do retangulo.");
                        }
                    }

                    @Override
                    public void onCommit(double dx, double dy) {
                        if (session != editing) {
                            return;
                        }
                        try {
                            if (circle) {
                                editing.addCircle(anchor[0], anchor[1], anchor[0] + dx, anchor[1] + dy,
                                        toolIndex);
                            } else {
                                editing.addRectangle(anchor[0], anchor[1], anchor[0] + dx, anchor[1] + dy,
                                        toolIndex);
                            }
                            instruction.setText((circle ? "Circulo" : "Retangulo") + " adicionado.");
                            refreshGeometry();
                        } catch (IllegalArgumentException invalid) {
                            instruction.setText(invalid.getMessage());
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (session == editing) {
                            instruction.setText("Desenho cancelado.");
                        }
                    }
                });
        if (started) {
            instruction.setText(circle ? "Clique no centro do circulo." : "Clique no primeiro canto.");
        }
    }

    void startMove() {
        startMoveOrCopy(false);
    }

    void startCopy() {
        startMoveOrCopy(true);
    }

    private void startMoveOrCopy(boolean copy) {
        if (!readyForTool() || session.selectedCount() == 0) {
            if (isActive()) {
                instruction.setText("Selecione ao menos uma forma primeiro.");
            }
            return;
        }
        GeometryEditSession editing = session;
        Set<Integer> selectedAtStart = editing.selectedIndices();
        boolean simplified = editing.selectedCount() > EXACT_HIGHLIGHT_LIMIT;
        Geometry preview = simplified ? editing.selectedBounds() : editing.selectedGeometry();
        boolean started = plotArea.beginEditorPlacement(List.of(preview), strokeOnly || simplified,
                new PlotAreaView.PlacementHandler() {
                    @Override
                    public void onAnchorChosen() {
                        if (session == editing) {
                            instruction.setText("Clique no destino das formas selecionadas.");
                        }
                    }

                    @Override
                    public void onCommit(double dx, double dy) {
                        if (session != editing || !editing.selectedIndices().equals(selectedAtStart)) {
                            return;
                        }
                        if (copy ? editing.copySelected(dx, dy) : editing.moveSelected(dx, dy)) {
                            refreshGeometry();
                            instruction.setText(copy ? "Copia concluida." : "Movimento concluido.");
                        } else {
                            instruction.setText("Nenhum deslocamento aplicado.");
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (session == editing) {
                            instruction.setText("Posicionamento cancelado.");
                        }
                    }
                });
        if (started) {
            instruction.setText("Clique na origem e depois no destino; Esc cancela.");
        }
    }

    private int selectedToolIndex() {
        return toolChoice == null ? implicitToolIndex : toolChoice.getSelectionModel().getSelectedIndex();
    }

    private boolean readyForTool() {
        if (isActive() && !busy) {
            return true;
        }
        if (busy) {
            host.log("Aguarde o calculo geometrico em andamento.");
            return false;
        }
        host.log("Selecione um objeto Geometry e abra o Editor antes de usar esta ferramenta.");
        return false;
    }

    private boolean deleteSelected() {
        if (!readyForTool()) {
            return false;
        }
        plotArea.cancelPlacement();
        if (!session.deleteSelected()) {
            return false;
        }
        refreshGeometry();
        return true;
    }

    private boolean undo() {
        if (!readyForTool()) {
            return false;
        }
        plotArea.cancelPlacement();
        if (!session.undo()) {
            return false;
        }
        refreshGeometry();
        return true;
    }

    private boolean redo() {
        if (!readyForTool()) {
            return false;
        }
        plotArea.cancelPlacement();
        if (!session.redo()) {
            return false;
        }
        refreshGeometry();
        return true;
    }

    void startUnion() {
        runOperation(GeometryEditSession.Operation.UNION, 0);
    }

    void startIntersection() {
        runOperation(GeometryEditSession.Operation.INTERSECTION, 0);
    }

    void startSubtract() {
        runOperation(GeometryEditSession.Operation.SUBTRACT, 0);
    }

    void startCutPath() {
        runOperation(GeometryEditSession.Operation.CUT_PATH, 0);
    }

    void startBuffer() {
        if (!readyForTool()) {
            return;
        }
        try {
            String raw = bufferDistance.getText().trim().replace(',', '.');
            double distance = Double.parseDouble(raw);
            GeometryEditSession.Operation operation = switch (bufferMode.getSelectionModel().getSelectedIndex()) {
                case 1 -> GeometryEditSession.Operation.BUFFER_INTERIOR;
                case 2 -> GeometryEditSession.Operation.BUFFER_EXTERIOR;
                default -> GeometryEditSession.Operation.BUFFER_FULL;
            };
            runOperation(operation, distance);
        } catch (NumberFormatException invalid) {
            instruction.setText("Informe uma distancia numerica positiva para o buffer.");
        }
    }

    private void runOperation(GeometryEditSession.Operation operation, double distance) {
        if (!readyForTool()) {
            return;
        }
        plotArea.cancelPlacement();
        GeometryEditSession editing = session;
        GeometryEditSession.OperationRequest request;
        try {
            request = editing.prepareOperation(operation, distance);
        } catch (IllegalArgumentException invalid) {
            instruction.setText(invalid.getMessage());
            return;
        }
        busy = true;
        refreshSelection();
        instruction.setText("Calculando " + operation.name().toLowerCase(java.util.Locale.ROOT) + "...");
        boolean accepted = host.runOperation(request, result -> {
            if (session != editing) {
                return;
            }
            busy = false;
            if (editing.applyOperation(result)) {
                refreshGeometry();
                instruction.setText("Operacao concluida. Ctrl+Z desfaz.");
            } else {
                refreshSelection();
                instruction.setText("O rascunho mudou durante o calculo; resultado descartado.");
            }
        }, error -> {
            if (session == editing) {
                busy = false;
                refreshSelection();
                instruction.setText(error);
            }
        });
        if (!accepted) {
            busy = false;
            refreshSelection();
            instruction.setText("Nao foi possivel iniciar: ja existe outra operacao em andamento.");
        }
    }

    void explode() {
        if (!readyForTool()) {
            return;
        }
        plotArea.cancelPlacement();
        try {
            int edges = session.explodeSelected();
            refreshGeometry();
            instruction.setText(edges + " segmentos criados. Ctrl+Z desfaz.");
        } catch (IllegalArgumentException invalid) {
            instruction.setText(invalid.getMessage());
        }
    }

    void apply() {
        if (!readyForTool()) {
            return;
        }
        plotArea.cancelPlacement();
        if (session.isDirty()) {
            host.apply(item, session.resultGeometry(), session.resultTools());
            host.log("Editor Geometry: alteracoes aplicadas em " + item.getValue() + ".");
        }
        end();
    }

    void cancelIfEditing(TreeItem<String> removed) {
        if (item == removed) {
            cancel();
        }
    }

    void cancel() {
        if (!isActive() || busy) {
            return;
        }
        plotArea.cancelPlacement();
        end();
        host.log("Editor Geometry: edicao descartada.");
    }

    private void refreshGeometry() {
        syncingTable = true;
        shapeTable.getItems().setAll(session.shapeRows());
        syncingTable = false;
        plotArea.updateLayerGeometry(SHAPES_LAYER, session.resultGeometry());
        refreshSelection();
    }

    private void refreshSelection() {
        if (!syncingTable) {
            syncingTable = true;
            tableSelectionOrder.clear();
            tableSelectionOrder.addAll(session.selectedIndexOrder());
            shapeTable.getSelectionModel().clearSelection();
            if (tableSelectionOrder.size() == shapeTable.getItems().size() && !tableSelectionOrder.isEmpty()) {
                shapeTable.getSelectionModel().selectAll();
            } else {
                for (int index : tableSelectionOrder) {
                    shapeTable.getSelectionModel().select(index);
                }
            }
            syncingTable = false;
        }
        boolean simplified = session.selectedCount() > EXACT_HIGHLIGHT_LIMIT;
        plotArea.setEditorHighlight(simplified ? session.selectedBounds() : session.selectedGeometry(),
                strokeOnly || simplified);
        status.setText(session.shapeCount() + " formas; " + session.selectedCount() + " selecionadas"
                + (simplified ? "; destaque simplificado por contorno" : "")
                + (session.isDirty() ? "; alteracoes pendentes." : "; sem alteracoes."));
        deleteButton.setDisable(session.selectedCount() == 0);
        moveButton.setDisable(session.selectedCount() == 0);
        copyButton.setDisable(session.selectedCount() == 0);
        undoButton.setDisable(!session.canUndo());
        redoButton.setDisable(!session.canRedo());
        applyButton.setDisable(!session.isDirty());
        unionButton.setDisable(session.selectedCount() < 2);
        intersectionButton.setDisable(session.selectedCount() < 2);
        subtractButton.setDisable(session.selectedCount() < 2);
        cutPathButton.setDisable(session.selectedCount() < 2);
        bufferButton.setDisable(session.selectedCount() == 0);
        explodeButton.setDisable(session.selectedCount() == 0);
        if (busy) {
            editorButtons.forEach(button -> button.setDisable(true));
        }
        shapeTable.setDisable(busy);
        exitButton.setDisable(busy);
        discardButton.setDisable(busy);
    }

    private void end() {
        plotArea.cancelPlacement();
        plotArea.setSelectionHandler(null);
        plotArea.removeLayer(SHAPES_LAYER);
        plotArea.setEditorHighlight(null, false);
        host.setObjectVisible(item, initiallyVisible);
        host.hideEditorToolbar();
        host.closeToolPanel();
        item = null;
        session = null;
        status = null;
        instruction = null;
        toolChoice = null;
        implicitToolIndex = -1;
        shapeTable = null;
        tableSelectionOrder.clear();
        deleteButton = null;
        moveButton = null;
        copyButton = null;
        undoButton = null;
        redoButton = null;
        applyButton = null;
        exitButton = null;
        discardButton = null;
        unionButton = null;
        intersectionButton = null;
        subtractButton = null;
        cutPathButton = null;
        bufferButton = null;
        explodeButton = null;
        bufferDistance = null;
        bufferMode = null;
        editorButtons = null;
        busy = false;
    }
}
