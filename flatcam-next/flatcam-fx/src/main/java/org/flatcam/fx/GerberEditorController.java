package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.paint.Color;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.gerber.edit.GerberEditSession;
import org.flatcam.cam.gerber.edit.TrackBendMode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

/**
 * Runs one Gerber Editor session against the main window: owns the
 * {@link GerberEditSession}, its sidebar panel, its two plot layers and the
 * canvas selection handler, so none of that state lives in MainWindow
 * (CONTEXTO_E_PROGRESSO.md section 7 asks the editor not to grow that class).
 *
 * <p>Ported from AppGerberEditor.py: edit_fcgerber() hides the source object
 * and plots the editor's own shapes instead; plot_all() draws each dark shape
 * in global_draw_color and selected ones in global_sel_draw_color, both with
 * alpha 'AF' for line and face (plot_shape()); Apply publishes a new
 * "&lt;name&gt;_edit" object; Cancel/Apply both restore the source object.
 */
final class GerberEditorController {

    interface Host {
        void openToolPanel(String label, Node content);

        Node icon(String fileName, double size);

        void closeToolPanel();

        void setObjectVisible(TreeItem<String> item, boolean visible);

        String addEditedGerber(String name, GerberImage image);

        boolean generateEditedGerber(GerberEditSession.ApplyRequest request,
                                     Consumer<GerberEditSession.ApplyResult> onSuccess, Runnable onFailure);

        void log(String message);
    }

    private record LayerKey(String name) {
    }

    record PlacementPreview(List<Geometry> geometries) {
    }

    private static final LayerKey SHAPES_LAYER = new LayerKey("gerber-editor-shapes");
    private static final LayerKey SELECTION_LAYER = new LayerKey("gerber-editor-selection");
    private static final Color SHAPE_COLOR = Color.web("#FF0000AF");
    private static final Color SELECTED_COLOR = Color.web("#0000FFAF");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final PlotAreaView plotArea;
    private final Host host;

    private TreeItem<String> item;
    private GerberEditSession session;
    private GerberEditToolPanel panel;

    GerberEditorController(PlotAreaView plotArea, Host host) {
        this.plotArea = plotArea;
        this.host = host;
    }

    void start(TreeItem<String> sourceItem, GerberImage image) {
        if (session != null) {
            host.log("Ja existe uma edicao em andamento em " + item.getValue() + ".");
            return;
        }
        item = sourceItem;
        session = new GerberEditSession(sourceItem.getValue(), image);
        panel = new GerberEditToolPanel(sourceItem.getValue(), image.units(), image.apertures(),
                session.shapesApproximated(), host::icon,
                this::clearSelection,
                this::deleteSelected, this::startCanvasMove, this::startCanvasCopy,
                this::moveSelected, this::copySelected, this::startPadPlacement, this::startTrackPlacement,
                this::addAperture, plotArea::cancelPlacement,
                this::undo, this::redo, this::apply, this::cancel);

        host.setObjectVisible(sourceItem, false);
        plotArea.putLayer(SHAPES_LAYER, PlotAreaView.LayerCategory.OVERLAY, GEOMETRY_FACTORY.createGeometryCollection(),
                SHAPE_COLOR, SHAPE_COLOR, false);
        plotArea.putLayer(SELECTION_LAYER, PlotAreaView.LayerCategory.OVERLAY, GEOMETRY_FACTORY.createGeometryCollection(),
                SELECTED_COLOR, SELECTED_COLOR, false);
        refreshSelection();
        plotArea.setSelectionHandler(new PlotAreaView.SelectionHandler() {
            @Override
            public void onClick(double worldX, double worldY, boolean additive) {
                session.clickSelect(worldX, worldY, additive);
                refreshSelection();
            }

            @Override
            public void onBox(double pressX, double pressY, double releaseX, double releaseY, boolean additive) {
                session.boxSelect(pressX, pressY, releaseX, releaseY, additive);
                refreshSelection();
            }
        });
        host.openToolPanel("Editor Gerber", panel.node());
    }

    /** Ends the session without creating anything if {@code removed} is the object being edited. */
    void cancelIfEditing(TreeItem<String> removed) {
        if (session != null && item == removed) {
            cancel();
        }
    }

    void cancel() {
        if (session == null) {
            return;
        }
        end();
        host.log("Editor: edicao cancelada.");
    }

    private void apply() {
        plotArea.cancelPlacement();
        if (session == null || !session.isDirty()) {
            return;
        }
        GerberEditSession editing = session;
        if (host.generateEditedGerber(editing.prepareApply(), result -> {
            if (session != editing) {
                return;
            }
            end();
            String name = host.addEditedGerber(result.name(), result.image());
            host.log("Editor: objeto \"" + name + "\" criado a partir da edicao.");
        }, () -> {
            if (session == editing) {
                panel.setBusy(false);
            }
        })) {
            panel.setBusy(true);
        }
    }

    private void deleteSelected() {
        plotArea.cancelPlacement();
        if (session != null && session.deleteSelected()) {
            refreshSelection();
        }
    }

    private void clearSelection() {
        plotArea.cancelPlacement();
        if (session != null) {
            session.clearSelection();
            refreshSelection();
        }
    }

    private void moveSelected(double dx, double dy) {
        plotArea.cancelPlacement();
        if (session != null && session.moveSelected(dx, dy)) {
            refreshSelection();
        }
    }

    private void copySelected(double dx, double dy) {
        plotArea.cancelPlacement();
        if (session != null && session.copySelected(dx, dy)) {
            refreshSelection();
        }
    }

    private void startCanvasMove() {
        startCanvasPlacement(false);
    }

    private void startCanvasCopy() {
        startCanvasPlacement(true);
    }

    private void startPadPlacement(String apertureCode) {
        if (session == null || session.shapesApproximated() || panel.isBusy() || apertureCode == null) {
            return;
        }
        Aperture aperture = session.apertures().get(apertureCode);
        if (aperture == null || (aperture.kind != ApertureKind.CIRCLE
                && aperture.kind != ApertureKind.RECTANGLE && aperture.kind != ApertureKind.OBROUND)
                || !Double.isFinite(aperture.width) || aperture.width <= 0
                || !Double.isFinite(aperture.height) || aperture.height <= 0) {
            panel.showError("Selecione uma abertura C, R ou O com dimensoes positivas.");
            return;
        }
        GerberEditSession editing = session;
        boolean started = plotArea.beginEditorFlashPlacement(
                aperture.footprintAt(0, 0, GEOMETRY_FACTORY), new PlotAreaView.PlacementHandler() {
                    @Override
                    public void onCommit(double x, double y) {
                        if (session != editing) {
                            return;
                        }
                        panel.setPadPlacing(false);
                        try {
                            editing.addPad(apertureCode, x, y);
                            panel.showError("");
                            refreshSelection();
                        } catch (RuntimeException exception) {
                            panel.showError(exception.getMessage());
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (session == editing) {
                            panel.setPadPlacing(false);
                        }
                    }
                });
        if (started) {
            panel.setPadPlacing(true);
            panel.showError("");
            host.log("Editor: clique no Plot Area para adicionar pad D" + apertureCode + "; Esc cancela.");
        }
    }

    private void startTrackPlacement(String apertureCode) {
        if (session == null || session.shapesApproximated() || panel.isBusy() || apertureCode == null) {
            return;
        }
        Aperture aperture = session.apertures().get(apertureCode);
        if (aperture == null || aperture.kind != ApertureKind.CIRCLE
                || !Double.isFinite(aperture.width) || aperture.width <= 0) {
            panel.showError("Selecione uma abertura circular C com diametro positivo.");
            return;
        }
        GerberEditSession editing = session;
        boolean started = plotArea.beginEditorTrackPlacement(aperture.width, new PlotAreaView.TrackPlacementHandler() {
            @Override
            public void onPathChanged(int anchorCount, TrackBendMode mode) {
                if (session == editing) {
                    panel.showTrackProgress(anchorCount, mode);
                }
            }

            @Override
            public void onCommit(List<Coordinate> points) {
                if (session != editing) {
                    return;
                }
                panel.setTrackPlacing(false);
                try {
                    if (editing.addTrack(apertureCode, points)) {
                        panel.showError("");
                        refreshSelection();
                    } else {
                        panel.showError("A trilha precisa de pelo menos dois pontos diferentes.");
                    }
                } catch (RuntimeException exception) {
                    panel.showError(exception.getMessage());
                }
            }

            @Override
            public void onCancel() {
                if (session == editing) {
                    panel.setTrackPlacing(false);
                }
            }
        });
        if (started) {
            panel.setTrackPlacing(true);
            panel.showTrackProgress(0, TrackBendMode.FORTY_FIVE);
            panel.showError("");
            host.log("Editor: trilha D" + apertureCode
                    + " - clique para adicionar pontos; Enter/duplo clique/botao direito conclui; "
                    + "Backspace volta; T/R muda o modo; Esc cancela.");
        }
    }

    private String addAperture(ApertureKind kind, double width, double height) {
        if (session == null || session.shapesApproximated() || panel.isBusy()) {
            throw new IllegalStateException("Editor indisponivel para adicionar abertura.");
        }
        String code = session.addAperture(kind, width, height);
        panel.refreshApertures(session.apertures(), code);
        refreshSelection();
        host.log("Editor: abertura " + kind + " D" + code + " adicionada.");
        return code;
    }

    private void startCanvasPlacement(boolean copy) {
        if (session == null || session.shapesApproximated() || session.selectedIndices().isEmpty()
                || panel.isBusy()) {
            return;
        }
        GerberEditSession editing = session;
        Set<Integer> selectedAtStart = Set.copyOf(editing.selectedIndices());
        PlacementPreview preview = placementPreview(editing.selectedShapes());
        if (preview == null) {
            return;
        }
        boolean started = plotArea.beginEditorPlacement(preview.geometries(),
                new PlotAreaView.PlacementHandler() {
                    @Override
                    public void onAnchorChosen() {
                        if (session == editing) {
                            panel.setPlacementAnchorChosen();
                        }
                    }

                    @Override
                    public void onCommit(double dx, double dy) {
                        if (session != editing || !editing.selectedIndices().equals(selectedAtStart)) {
                            host.log("Editor: posicionamento cancelado; a selecao mudou.");
                            return;
                        }
                        panel.setPlacing(false);
                        boolean changed = copy ? editing.copySelected(dx, dy) : editing.moveSelected(dx, dy);
                        if (changed) {
                            refreshSelection();
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (session == editing) {
                            panel.setPlacing(false);
                        }
                    }
                });
        if (started) {
            panel.setPlacing(true);
            host.log("Editor: " + (copy ? "copiar" : "mover")
                    + " formas — clique na origem e depois no destino; Esc ou botao direito cancela.");
        }
    }

    static PlacementPreview placementPreview(List<GerberShape> shapes) {
        List<Geometry> preview = new ArrayList<>();
        for (GerberShape shape : shapes) {
            if (!shape.clear() && shape.geometry() != null && !shape.geometry().isEmpty()) {
                addPolygons(shape.geometry(), preview);
            }
        }
        if (preview.isEmpty()) {
            return null;
        }
        return new PlacementPreview(List.copyOf(preview));
    }

    boolean undoFromShortcut() {
        plotArea.cancelPlacement();
        if (session != null && !panel.isBusy() && session.undo()) {
            panel.refreshApertures(session.apertures(), panel.selectedPadAperture());
            refreshSelection();
            return true;
        }
        return false;
    }

    boolean redoFromShortcut() {
        plotArea.cancelPlacement();
        if (session != null && !panel.isBusy() && session.redo()) {
            panel.refreshApertures(session.apertures(), panel.selectedPadAperture());
            refreshSelection();
            return true;
        }
        return false;
    }

    private void undo() {
        undoFromShortcut();
    }

    private void redo() {
        redoFromShortcut();
    }

    private void end() {
        plotArea.cancelPlacement();
        plotArea.setSelectionHandler(null);
        plotArea.removeLayer(SHAPES_LAYER);
        plotArea.removeLayer(SELECTION_LAYER);
        host.setObjectVisible(item, true);
        host.closeToolPanel();
        item = null;
        session = null;
        panel = null;
    }

    private void refreshSelection() {
        Set<Integer> selected = session.selectedIndices();
        List<Geometry> unselectedParts = new ArrayList<>();
        List<Geometry> selectedParts = new ArrayList<>();
        List<GerberShape> shapes = session.shapes();
        for (int i = 0; i < shapes.size(); i++) {
            GerberShape shape = shapes.get(i);
            if (!shape.clear()) {
                addPolygons(shape.geometry(), selected.contains(i) ? selectedParts : unselectedParts);
            }
        }
        plotArea.updateLayerGeometry(SHAPES_LAYER, collection(unselectedParts));
        plotArea.updateLayerGeometry(SELECTION_LAYER, collection(selectedParts));
        panel.showSelection(selected.size(), session.selectedApertures());
        panel.showState(session.isDirty(), session.canUndo(), session.canRedo());
    }

    /** PlotAreaView draws one level of parts, so nested multi-polygons are flattened first. */
    private static void addPolygons(Geometry geometry, List<Geometry> out) {
        if (geometry instanceof Polygon) {
            out.add(geometry);
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part != geometry) {
                addPolygons(part, out);
            }
        }
    }

    private static Geometry collection(List<Geometry> parts) {
        return GEOMETRY_FACTORY.createGeometryCollection(parts.toArray(Geometry[]::new));
    }
}
