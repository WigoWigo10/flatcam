package org.flatcam.fx;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;

/** Canvas selection/deletion for a Geometry object; Apply updates the source object atomically. */
final class GeometryEditorController {

    interface Host {
        void openToolPanel(String label, Node content);

        void closeToolPanel();

        void setObjectVisible(TreeItem<String> item, boolean visible);

        void apply(TreeItem<String> item, Geometry geometry, List<ToolGeometry> tools);

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
    private Button deleteButton;
    private Button undoButton;
    private Button redoButton;
    private Button applyButton;
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
        initiallyVisible = plotArea.isLayerVisible(sourceItem);
        this.strokeOnly = strokeOnly;
        host.setObjectVisible(sourceItem, false);
        plotArea.putLayer(SHAPES_LAYER, PlotAreaView.LayerCategory.OVERLAY,
                session.resultGeometry(), SHAPE_COLOR, SHAPE_COLOR, strokeOnly);
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

        Label help = new Label("Clique para selecionar uma forma. Arraste da esquerda para a direita para "
                + "incluir apenas formas inteiras; da direita para a esquerda para incluir as tocadas. "
                + "Ctrl alterna a selecao. Delete exclui; Ctrl+Z/Y desfaz/refaz.");
        help.setWrapText(true);
        status = new Label();
        deleteButton = new Button("Excluir selecionadas");
        undoButton = new Button("Desfazer");
        redoButton = new Button("Refazer");
        applyButton = new Button("Aplicar ao objeto");
        Button cancelButton = new Button("Cancelar edicao");
        for (Button button : new Button[]{deleteButton, undoButton, redoButton, applyButton, cancelButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        deleteButton.setOnAction(event -> deleteSelected());
        undoButton.setOnAction(event -> undo());
        redoButton.setOnAction(event -> redo());
        applyButton.setOnAction(event -> apply());
        cancelButton.setOnAction(event -> cancel());
        VBox panel = new VBox(10, new Label("Editando: " + sourceItem.getValue()), help, status,
                deleteButton, undoButton, redoButton, applyButton, cancelButton);
        panel.setPadding(new Insets(12));
        host.openToolPanel("Editor Geometry", panel);
        refreshSelection();
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

    private boolean deleteSelected() {
        if (!isActive() || !session.deleteSelected()) {
            return false;
        }
        refreshGeometry();
        return true;
    }

    private boolean undo() {
        if (!isActive() || !session.undo()) {
            return false;
        }
        refreshGeometry();
        return true;
    }

    private boolean redo() {
        if (!isActive() || !session.redo()) {
            return false;
        }
        refreshGeometry();
        return true;
    }

    void apply() {
        if (!isActive()) {
            return;
        }
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
        if (!isActive()) {
            return;
        }
        end();
        host.log("Editor Geometry: edicao descartada.");
    }

    private void refreshGeometry() {
        plotArea.updateLayerGeometry(SHAPES_LAYER, session.resultGeometry());
        refreshSelection();
    }

    private void refreshSelection() {
        boolean simplified = session.selectedCount() > EXACT_HIGHLIGHT_LIMIT;
        plotArea.setEditorHighlight(simplified ? session.selectedBounds() : session.selectedGeometry(),
                strokeOnly || simplified);
        status.setText(session.shapeCount() + " formas; " + session.selectedCount() + " selecionadas"
                + (simplified ? "; destaque simplificado por contorno" : "")
                + (session.isDirty() ? "; alteracoes pendentes." : "; sem alteracoes."));
        deleteButton.setDisable(session.selectedCount() == 0);
        undoButton.setDisable(!session.canUndo());
        redoButton.setDisable(!session.canRedo());
        applyButton.setDisable(!session.isDirty());
    }

    private void end() {
        plotArea.setSelectionHandler(null);
        plotArea.removeLayer(SHAPES_LAYER);
        plotArea.setEditorHighlight(null, false);
        host.setObjectVisible(item, initiallyVisible);
        host.closeToolPanel();
        item = null;
        session = null;
        status = null;
        deleteButton = null;
        undoButton = null;
        redoButton = null;
        applyButton = null;
    }
}
