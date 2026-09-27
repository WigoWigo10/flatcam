package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.flatcam.cam.excellon.ExcellonEditSession;
import org.flatcam.cam.excellon.ExcellonImage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Drill/slot editing on the canvas with a synchronized ID table. */
final class ExcellonEditorController {

    private static final int EXACT_HIGHLIGHT_LIMIT = 500;

    interface Host {
        void openToolPanel(String label, Node content);
        void closeToolPanel();
        void showToolbar(Node toolbar);
        void hideToolbar();
        void setObjectVisible(TreeItem<String> item, boolean visible);
        void apply(TreeItem<String> item, ExcellonImage image);
        Node icon(String fileName);
        void log(String message);
    }

    private static final Object SHAPES_LAYER = new Object();
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private final PlotAreaView plotArea;
    private final Host host;
    private TreeItem<String> item;
    private ExcellonEditSession session;
    private boolean initiallyVisible;
    private TableView<ExcellonEditSession.Row> table;
    private ComboBox<Integer> toolChoice;
    private Label status;
    private Label instruction;
    private Button deleteButton;
    private Button moveButton;
    private Button copyButton;
    private Button undoButton;
    private Button redoButton;
    private Button applyButton;
    private Button drillArrayButton;
    private Button slotArrayButton;
    private Button resizeButton;
    private TextField diameterField;
    private TextField columnsField;
    private TextField rowsField;
    private TextField pitchXField;
    private TextField pitchYField;
    private boolean syncing;

    ExcellonEditorController(PlotAreaView plotArea, Host host) {
        this.plotArea = plotArea;
        this.host = host;
    }

    boolean isActive() { return session != null; }
    boolean isEditing(TreeItem<String> target) { return item == target; }
    boolean hasUnappliedChanges() { return isActive() && session.isDirty(); }

    void start(TreeItem<String> sourceItem, ExcellonImage image) {
        if (isActive()) {
            host.log("Ja existe um Editor Excellon ativo.");
            return;
        }
        item = sourceItem;
        session = new ExcellonEditSession(image);
        initiallyVisible = plotArea.isLayerVisible(sourceItem);
        host.setObjectVisible(sourceItem, false);
        plotArea.putLayer(SHAPES_LAYER, PlotAreaView.LayerCategory.OVERLAY,
                session.geometry(), Color.web("#FF0000AF"), Color.web("#FF0000AF"), false);
        plotArea.setSelectionHandler(new PlotAreaView.SelectionHandler() {
            @Override
            public void onClick(double x, double y, boolean additive) {
                session.clickSelect(x, y, plotArea.pickToleranceWorld(), additive);
                refreshSelection();
            }

            @Override
            public void onBox(double x1, double y1, double x2, double y2, boolean additive) {
                session.boxSelect(x1, y1, x2, y2, additive);
                refreshSelection();
            }
        });
        table = new TableView<>(FXCollections.observableArrayList(session.rows()));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<ExcellonEditSession.Row, String> id = new TableColumn<>("ID");
        id.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Long.toString(cell.getValue().id())));
        id.setPrefWidth(80);
        TableColumn<ExcellonEditSession.Row, String> type = new TableColumn<>("Tipo");
        type.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().type()));
        type.setPrefWidth(55);
        TableColumn<ExcellonEditSession.Row, String> tool = new TableColumn<>("Ferramenta");
        tool.setCellValueFactory(cell -> new ReadOnlyStringWrapper("T" + cell.getValue().toolId()));
        tool.setPrefWidth(85);
        TableColumn<ExcellonEditSession.Row, String> coords = new TableColumn<>("Coordenadas");
        coords.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().coordinates()));
        coords.setPrefWidth(190);
        table.getColumns().addAll(id, type, tool, coords);
        table.setPrefHeight(320);
        table.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<ExcellonEditSession.Row>) change -> {
                    if (syncing || session == null) return;
                    session.selectIds(table.getSelectionModel().getSelectedItems().stream()
                            .map(ExcellonEditSession.Row::id).toList());
                    refreshSelection();
                });
        toolChoice = new ComboBox<>();
        toolChoice.getItems().addAll(image.toolDiameters().keySet().stream().sorted().toList());
        toolChoice.getSelectionModel().selectFirst();
        toolChoice.setMaxWidth(Double.MAX_VALUE);
        diameterField = new TextField(toolChoice.getValue() == null ? ""
                : Double.toString(session.toolDiameters().get(toolChoice.getValue())));
        toolChoice.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) diameterField.setText(Double.toString(session.toolDiameters().get(newValue)));
        });
        columnsField = new TextField("2");
        rowsField = new TextField("2");
        pitchXField = new TextField("1");
        pitchYField = new TextField("1");
        for (TextField field : List.of(diameterField, columnsField, rowsField, pitchXField, pitchYField))
            field.setPrefColumnCount(6);
        status = new Label();
        instruction = new Label("Selecione furos/slots na tabela ou no desenho; Ctrl adiciona a selecao.");
        instruction.setWrapText(true);
        Button selectButton = button("Selecionar", "pointer32.png", () -> {
            plotArea.cancelPlacement();
            instruction.setText("Clique ou arraste no desenho; Ctrl adiciona a selecao.");
        });
        Button drillButton = button("Adicionar furo", "drill32.png", this::startDrill);
        Button slotButton = button("Adicionar slot", "slot26.png", this::startSlot);
        drillArrayButton = button("Array de furos", "addarray16.png", () -> startArray(false));
        slotArrayButton = button("Array de slots", "slot_array26.png", () -> startArray(true));
        resizeButton = button("Redimensionar", "resize16.png", this::resizeSelected);
        moveButton = button("Mover", "move32.png", () -> startMoveOrCopy(false));
        copyButton = button("Copiar", "copy32.png", () -> startMoveOrCopy(true));
        deleteButton = button("Excluir", "trash32.png", this::deleteSelected);
        undoButton = new Button("↶");
        undoButton.setOnAction(event -> undo());
        redoButton = new Button("↷");
        redoButton.setOnAction(event -> redo());
        applyButton = button("Aplicar", "close_edit_file32.png", this::apply);
        Button cancelButton = button("Cancelar", "power16.png", this::cancel);
        Button applyPanel = new Button("Aplicar e sair");
        applyPanel.setMaxWidth(Double.MAX_VALUE);
        applyPanel.setOnAction(event -> apply());
        Button discardPanel = new Button("Descartar alteracoes");
        discardPanel.setMaxWidth(Double.MAX_VALUE);
        discardPanel.setOnAction(event -> cancel());
        GridPane controls = new GridPane();
        controls.setHgap(6);
        controls.setVgap(6);
        controls.addRow(0, new Label("Novo diametro:"), diameterField);
        controls.addRow(1, new Label("Colunas / linhas:"), columnsField, rowsField);
        controls.addRow(2, new Label("Passo X / Y:"), pitchXField, pitchYField);
        VBox panel = new VBox(8, new Label("Excellon Editor"), table,
                new Label("Ferramenta para novos furos/slots:"), toolChoice, controls,
                button("Redimensionar selecionados", "resize16.png", this::resizeSelected),
                button("Array de furos selecionados", "addarray16.png", () -> startArray(false)),
                button("Array de slots selecionados", "slot_array26.png", () -> startArray(true)),
                status, instruction, applyPanel, discardPanel);
        panel.setPadding(new Insets(12));
        host.openToolPanel("Editor Excellon", panel);
        host.showToolbar(new ToolBar(selectButton, drillButton, slotButton,
                drillArrayButton, slotArrayButton, resizeButton, moveButton,
                copyButton, deleteButton, undoButton, redoButton, applyButton, cancelButton));
        refreshSelection();
    }

    private Button button(String label, String iconName, Runnable action) {
        Button button = new Button(label, host.icon(iconName));
        button.setOnAction(event -> action.run());
        return button;
    }

    private int selectedTool() {
        Integer id = toolChoice.getValue();
        if (id == null) throw new IllegalArgumentException("Selecione uma ferramenta Excellon.");
        return id;
    }

    void startSelection() {
        if (!isActive()) return;
        plotArea.cancelPlacement();
        instruction.setText("Clique ou arraste no desenho; Ctrl adiciona a selecao.");
    }

    void startArray(boolean slots) {
        if (!isActive()) return;
        plotArea.cancelPlacement();
        try {
            int added = session.arraySelected(slots,
                    Integer.parseInt(columnsField.getText().trim()),
                    Integer.parseInt(rowsField.getText().trim()),
                    parseDecimal(pitchXField.getText()), parseDecimal(pitchYField.getText()));
            refreshGeometry();
            instruction.setText(added + (slots ? " slots" : " furos") + " adicionados ao array.");
        } catch (NumberFormatException error) {
            instruction.setText("Informe colunas/linhas inteiras e passos X/Y numericos.");
        } catch (IllegalArgumentException error) {
            instruction.setText(error.getMessage());
        }
    }

    void resizeSelected() {
        if (!isActive()) return;
        plotArea.cancelPlacement();
        try {
            int id = session.resizeSelected(parseDecimal(diameterField.getText()));
            refreshGeometry();
            toolChoice.setValue(id);
            instruction.setText("Formas associadas a ferramenta T" + id + ".");
        } catch (NumberFormatException error) {
            instruction.setText("Informe um diametro numerico positivo.");
        } catch (IllegalArgumentException error) {
            instruction.setText(error.getMessage());
        }
    }

    private static double parseDecimal(String text) {
        return Double.parseDouble(text.trim().replace(',', '.'));
    }

    void startDrill() {
        if (!isActive()) return;
        try {
            int id = selectedTool();
            double radius = session.toolDiameters().get(id) / 2.0;
            Geometry footprint = FACTORY.createPoint(new Coordinate(0, 0)).buffer(radius, 16);
            if (plotArea.beginEditorFlashPlacement(footprint, new PlotAreaView.PlacementHandler() {
                @Override public void onCommit(double dx, double dy) {
                    session.addDrill(id, dx, dy);
                    refreshGeometry();
                    instruction.setText("Furo adicionado. Ctrl+Z desfaz.");
                }
                @Override public void onCancel() { instruction.setText("Furo cancelado."); }
            })) instruction.setText("Clique no centro do novo furo; Esc cancela.");
        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
    }

    void startSlot() {
        if (!isActive()) return;
        try {
            int id = selectedTool();
            double[] first = new double[2];
            if (plotArea.beginEditorTwoPointPlacement(PlotAreaView.TwoPointShape.SLOT,
                    session.toolDiameters().get(id), new PlotAreaView.PlacementHandler() {
                        @Override public void onAnchorChosen(double x, double y) {
                            first[0] = x; first[1] = y;
                            instruction.setText("Clique no fim do slot; Esc cancela.");
                        }
                        @Override public void onCommit(double dx, double dy) {
                            try {
                                session.addSlot(id, first[0], first[1], first[0] + dx, first[1] + dy);
                                refreshGeometry();
                                instruction.setText("Slot adicionado. Ctrl+Z desfaz.");
                            } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
                        }
                        @Override public void onCancel() { instruction.setText("Slot cancelado."); }
                    })) instruction.setText("Clique no inicio e no fim do slot; Esc cancela.");
        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
    }

    void startMoveOrCopy(boolean copy) {
        if (!isActive() || session.selectedCount() == 0) {
            if (isActive()) instruction.setText("Selecione ao menos um furo ou slot.");
            return;
        }
        Set<Long> selectedAtStart = session.selectedIds();
        Geometry preview = session.selectedCount() > EXACT_HIGHLIGHT_LIMIT
                ? session.selectedBounds() : session.selectedGeometry();
        if (plotArea.beginEditorPlacement(List.of(preview), new PlotAreaView.PlacementHandler() {
            @Override public void onCommit(double dx, double dy) {
                if (!session.selectedIds().equals(selectedAtStart)) return;
                if (copy ? session.copySelected(dx, dy) : session.moveSelected(dx, dy)) {
                    refreshGeometry();
                    instruction.setText(copy ? "Copia concluida." : "Movimento concluido.");
                }
            }
            @Override public void onCancel() { instruction.setText("Posicionamento cancelado."); }
        })) instruction.setText("Clique na origem e no destino; Esc cancela.");
    }

    boolean deleteFromShortcut() { return deleteSelected(); }
    boolean undoFromShortcut() { return undo(); }
    boolean redoFromShortcut() { return redo(); }

    private boolean deleteSelected() {
        if (!isActive()) return false;
        plotArea.cancelPlacement();
        if (!session.deleteSelected()) return false;
        refreshGeometry();
        return true;
    }

    private boolean undo() {
        if (!isActive()) return false;
        plotArea.cancelPlacement();
        if (!session.undo()) return false;
        refreshGeometry();
        return true;
    }

    private boolean redo() {
        if (!isActive()) return false;
        plotArea.cancelPlacement();
        if (!session.redo()) return false;
        refreshGeometry();
        return true;
    }

    private void refreshGeometry() {
        List<Integer> availableTools = session.toolDiameters().keySet().stream().sorted().toList();
        if (!toolChoice.getItems().equals(availableTools)) {
            Integer current = toolChoice.getValue();
            toolChoice.getItems().setAll(availableTools);
            toolChoice.setValue(availableTools.contains(current) ? current
                    : availableTools.isEmpty() ? null : availableTools.get(0));
        }
        syncing = true;
        table.getItems().setAll(session.rows());
        syncing = false;
        plotArea.updateLayerGeometry(SHAPES_LAYER, session.geometry());
        refreshSelection();
    }

    private void refreshSelection() {
        syncing = true;
        Set<Long> ids = session.selectedIds();
        table.getSelectionModel().clearSelection();
        if (!ids.isEmpty() && ids.size() == table.getItems().size()) {
            table.getSelectionModel().selectAll();
        } else {
            for (int i = 0; i < table.getItems().size(); i++)
                if (ids.contains(table.getItems().get(i).id())) table.getSelectionModel().select(i);
        }
        syncing = false;
        boolean simplified = session.selectedCount() > EXACT_HIGHLIGHT_LIMIT;
        plotArea.setEditorHighlight(simplified ? session.selectedBounds() : session.selectedGeometry(), simplified);
        status.setText(session.size() + " furos/slots; " + session.selectedCount()
                + " selecionados" + (session.isDirty() ? "; alteracoes pendentes." : "; sem alteracoes."));
        deleteButton.setDisable(session.selectedCount() == 0);
        drillArrayButton.setDisable(session.selectedCount() == 0);
        slotArrayButton.setDisable(session.selectedCount() == 0);
        resizeButton.setDisable(session.selectedCount() == 0);
        moveButton.setDisable(session.selectedCount() == 0);
        copyButton.setDisable(session.selectedCount() == 0);
        undoButton.setDisable(!session.canUndo());
        redoButton.setDisable(!session.canRedo());
        applyButton.setDisable(!session.isDirty());
    }

    void apply() {
        if (!isActive()) return;
        plotArea.cancelPlacement();
        if (session.isDirty()) host.apply(item, session.resultImage());
        end();
    }

    void cancelIfEditing(TreeItem<String> removed) { if (item == removed) cancel(); }

    void cancel() {
        if (!isActive()) return;
        plotArea.cancelPlacement();
        end();
    }

    private void end() {
        plotArea.cancelPlacement();
        plotArea.setSelectionHandler(null);
        plotArea.removeLayer(SHAPES_LAYER);
        plotArea.setEditorHighlight(null, false);
        host.setObjectVisible(item, initiallyVisible);
        host.hideToolbar();
        host.closeToolPanel();
        item = null;
        session = null;
        table = null;
        toolChoice = null;
        status = null;
        instruction = null;
    }
}
