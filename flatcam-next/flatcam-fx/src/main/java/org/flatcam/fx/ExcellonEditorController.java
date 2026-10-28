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
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import java.util.function.Consumer;
import org.flatcam.cam.excellon.ExcellonArray;
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

    private int exactHighlightLimit = 500;
    private Color selectedColor = Color.BLUE;

    interface Host {
        void openToolPanel(String label, Node content);
        void closeToolPanel();
        void showToolbar(Node toolbar);
        void hideToolbar();
        void setObjectVisible(TreeItem<String> item, boolean visible);
        void apply(TreeItem<String> item, ExcellonImage image);
        Node icon(String fileName);
        void log(String message);
        default boolean prepareArray(ExcellonArray.Parameters p,Consumer<ExcellonArray.Prepared> done,Consumer<String> failed) { return false; }
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
    private boolean syncing,busy,arrayReady;
    private Object arrayGesture;
    private ExcellonArrayPanel arrays;
    private TitledPane arrayPane;

    ExcellonEditorController(PlotAreaView plotArea, Host host) {
        this.plotArea = plotArea;
        this.host = host;
    }

    boolean isActive() { return session != null; }
    boolean isEditing(TreeItem<String> target) { return item == target; }
    boolean hasUnappliedChanges() { return isActive() && session.isDirty(); }

    void start(TreeItem<String> sourceItem, ExcellonImage image) {
        if (isActive()) {
            host.log(I18n.text("ui.b6fb6d40304f"));
            return;
        }
        busy=false; arrayReady=false; arrayGesture=null;
        item = sourceItem;
        Color drawColor = ToolDefaults.color("editor.excellon.draw");
        selectedColor = ToolDefaults.color("editor.excellon.selected");
        exactHighlightLimit = ToolDefaults.integer("editor.excellon.selectionlimit");
        session = new ExcellonEditSession(image);
        initiallyVisible = plotArea.isLayerVisible(sourceItem);
        host.setObjectVisible(sourceItem, false);
        plotArea.putLayer(SHAPES_LAYER, PlotAreaView.LayerCategory.OVERLAY,
                session.geometry(), drawColor, drawColor, false);
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
        table.setId("excellon-shapes");
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<ExcellonEditSession.Row, String> id = new TableColumn<>("ID");
        id.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Long.toString(cell.getValue().id())));
        id.setPrefWidth(80);
        TableColumn<ExcellonEditSession.Row, String> type = new TableColumn<>(I18n.text("ui.3868d2843d59"));
        type.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().type()));
        type.setPrefWidth(55);
        TableColumn<ExcellonEditSession.Row, String> tool = new TableColumn<>(I18n.text("ui.c188272ba40c"));
        tool.setCellValueFactory(cell -> new ReadOnlyStringWrapper("T" + cell.getValue().toolId()));
        tool.setPrefWidth(85);
        TableColumn<ExcellonEditSession.Row, String> coords = new TableColumn<>(I18n.text("ui.b1fd9aa859e0"));
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
        diameterField = new TextField(toolChoice.getValue() == null ? ToolDefaults.text("editor.excellon.diameter", image.units().equals("MM"))
                : Double.toString(session.toolDiameters().get(toolChoice.getValue())));
        diameterField.setId("excellon-new-diameter");
        ToolDescriptions.apply(diameterField,I18n.text("ui.0636658f09fa"),I18n.text("ui.ee01e57b03bd"));
        toolChoice.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) diameterField.setText(Double.toString(session.toolDiameters().get(newValue)));
        });
        columnsField = new TextField(ToolDefaults.text("editor.excellon.columns", image.units().equals("MM")));
        rowsField = new TextField(ToolDefaults.text("editor.excellon.rows", image.units().equals("MM")));
        pitchXField = new TextField(ToolDefaults.text("editor.excellon.pitchx", image.units().equals("MM")));
        pitchYField = new TextField(ToolDefaults.text("editor.excellon.pitchy", image.units().equals("MM")));
        for (TextField field : List.of(diameterField, columnsField, rowsField, pitchXField, pitchYField))
            field.setPrefColumnCount(6);
        for(var field:List.of(columnsField,rowsField)) ToolDescriptions.apply(field,I18n.text("ui.8f97b986b0c1"),I18n.text("ui.389eb670e26e"));
        ToolDescriptions.apply(pitchXField,I18n.text("ui.e0b0f2d3ffc1"),I18n.text("ui.33a716b0f22a"));
        ToolDescriptions.apply(pitchYField,I18n.text("ui.07d67e8d6839"),I18n.text("ui.1c9fadc9fe6e"));
        status = new Label();
        instruction = new Label(I18n.text("ui.0dc2af8eaa69"));
        instruction.setWrapText(true);
        Button selectButton = button(I18n.text("ui.6a57618c2593"), "pointer32.png", () -> {
            plotArea.cancelPlacement();
            instruction.setText(I18n.text("ui.ac94f3b01ef0"));
        });
        Button drillButton = button(I18n.text("ui.9e5afc54f4d6"), "drill32.png", this::startDrill);
        Button slotButton = button(I18n.text("ui.64796d74dfd9"), "slot26.png", this::startSlot);
        drillArrayButton = button(I18n.text("ui.fc2f746e600d"), "addarray16.png", () -> startArray(false));
        slotArrayButton = button(I18n.text("ui.42503fe77fa9"), "slot_array26.png", () -> startArray(true));
        resizeButton = button(I18n.text("ui.2c5c10830368"), "resize16.png", this::resizeSelected);
        moveButton = button(I18n.text("ui.6624c0d31888"), "move32.png", () -> startMoveOrCopy(false));
        copyButton = button(I18n.text("ui.39dcf1cbeb5a"), "copy32.png", () -> startMoveOrCopy(true));
        deleteButton = button(I18n.text("ui.8b19518ff49b"), "trash32.png", this::deleteSelected);
        undoButton = new Button("↶");
        undoButton.setOnAction(event -> undo());
        redoButton = new Button("↷");
        redoButton.setOnAction(event -> redo());
        applyButton = button(I18n.text("ui.3a04898a6b8b"), "close_edit_file32.png", this::apply);
        applyButton.getStyleClass().add("primary-action");
        Button cancelButton = button(I18n.text("ui.bb9dbb406dcb"), "power16.png", this::cancel);
        Button applyPanel = new Button(I18n.text("ui.7fbcc2d88ec0"));
        applyPanel.setMaxWidth(Double.MAX_VALUE);
        applyPanel.setOnAction(event -> apply());
        Button discardPanel = new Button(I18n.text("ui.534c4da81431"));
        discardPanel.setMaxWidth(Double.MAX_VALUE);
        discardPanel.setOnAction(event -> cancel());
        GridPane controls = new GridPane();
        controls.setHgap(6);
        controls.setVgap(6);
        controls.addRow(0, new Label(I18n.text("ui.8fa41aaf2a55")), diameterField);
        var grid=new VBox(6,new Label(I18n.text("ui.8a0af16df362")),
                new HBox(6,new Label(I18n.text("ui.67d67f5a779c")),columnsField,new Label(I18n.text("ui.aec624c5358e")),rowsField),
                new HBox(6,new Label(I18n.text("ui.407d9bb4c286")),pitchXField,new Label("Y:"),pitchYField));
        arrays=new ExcellonArrayPanel(image.units().equals("MM"),grid,
                ()->startArray(arrays.slots()),this::pickArrayOrigin,()->plotArea.confirmEditorFixedPreview(),
                this::cancelArrayFromShortcut,()-> { cancelArrayFromShortcut(); if(session!=null) refreshSelection(); });
        diameterField.textProperty().addListener((o,a,b)->cancelArrayFromShortcut());
        arrayPane=new TitledPane(I18n.text("ui.09d418cd7f24"),arrays); arrayPane.setId("excellon-array-panel"); arrayPane.setExpanded(false);
        VBox panel = new VBox(8, new Label("Excellon Editor"), table,
                new Label(I18n.text("ui.2ad42ffddf90")), toolChoice, controls,
                button(I18n.text("ui.c4e06f684551"), "resize16.png", this::resizeSelected),
                arrayPane,
                status, instruction, applyPanel, discardPanel);
        panel.setPadding(new Insets(12));
        host.openToolPanel(I18n.text("ui.0e54aac8a039"), panel);
        host.showToolbar(new ToolBar(selectButton, drillButton, slotButton,
                drillArrayButton, slotArrayButton, resizeButton, moveButton,
                copyButton, deleteButton, undoButton, redoButton, applyButton, cancelButton));
        refreshSelection();
    }

    void startArray(boolean slots) {
        if(!isActive() || busy) return;
        arrayPane.setExpanded(true); arrays.slots(slots);
        if(arrays.grid()) { replicateGrid(slots); return; }
        cancelArrayFromShortcut(); plotArea.cancelPlacement();
        try {
            var params=arrays.parameters(parseDecimal(diameterField.getText()));
            var editing=session; long revision=editing.revision(); Object gesture=new Object();
            arrayGesture=gesture; arrayReady=false; busy=true; refreshSelection();
            instruction.setText(I18n.text("ui.80dea17fc774"));
            boolean accepted=host.prepareArray(params,result-> {
                if(session!=editing) return;
                busy=false;
                if(arrayGesture!=gesture || editing.revision()!=revision) { if(arrayGesture==gesture) stopArray(gesture); refreshSelection(); return; }
                boolean started=plotArea.beginEditorFixedPreview(arrayCross(params.x(),params.y()),new PlotAreaView.PlacementHandler() {
                    public void onCommit(double x,double y) {
                        if(session!=editing || arrayGesture!=gesture) return;
                        arrayGesture=null; arrayReady=false; plotArea.setEditorReference(null);
                        try { int count=editing.addArray(result,revision); refreshGeometry(); instruction.setText(count+I18n.text("ui.1dad644d06d5")); }
                        catch(IllegalArgumentException error) { refreshSelection(); instruction.setText(error.getMessage()); }
                    }
                    public void onCancel() { if(arrayGesture==gesture) stopArray(gesture); }
                });
                if(!started) { stopArray(gesture); instruction.setText(I18n.text("ui.3a79d7e20810")); return; }
                plotArea.setEditorReference(result.geometry()); arrayReady=true; refreshSelection();
                instruction.setText(params.count()+I18n.text("ui.babbf290a305"));
            },error-> { if(session==editing) { busy=false; stopArray(gesture); refreshSelection(); instruction.setText(error); } });
            if(!accepted) { busy=false; stopArray(gesture); instruction.setText(I18n.text("ui.bc2ac080752f")); }
        } catch(IllegalArgumentException error) { instruction.setText("Array: "+error.getMessage()); }
    }
    private Geometry arrayCross(double x,double y) {
        double r=plotArea.worldPerPixel()*5;
        return FACTORY.buildGeometry(List.of(FACTORY.createLineString(new Coordinate[]{new Coordinate(x-r,y),new Coordinate(x+r,y)}),
                FACTORY.createLineString(new Coordinate[]{new Coordinate(x,y-r),new Coordinate(x,y+r)})));
    }
    boolean cancelArrayFromShortcut() {
        if(arrayGesture==null) return false;
        Object gesture=arrayGesture; stopArray(gesture); plotArea.cancelPlacement(); return true;
    }
    private void stopArray(Object gesture) {
        if(arrayGesture!=gesture) return;
        arrayGesture=null; arrayReady=false; plotArea.setEditorReference(null); refreshSelection();
        instruction.setText(I18n.text("ui.4448708e7ce1"));
    }
    private void pickArrayOrigin() {
        if(!isActive() || busy || arrays.grid()) return;
        cancelArrayFromShortcut(); plotArea.cancelPlacement();
        Object gesture=new Object(); arrayGesture=gesture; var editing=session;
        if(arrays.circular()) {
            double[] centre=new double[2];
            plotArea.beginEditorTwoPointPlacement(PlotAreaView.TwoPointShape.CIRCLE,new PlotAreaView.PlacementHandler() {
                public void onAnchorChosen(double x,double y) { centre[0]=x; centre[1]=y; instruction.setText(I18n.text("ui.2f56fbb96c3a")); }
                public void onCommit(double dx,double dy) {
                    if(session!=editing || arrayGesture!=gesture) return;
                    arrayGesture=null; arrays.origin(centre[0],centre[1]);
                    arrays.radius.setText(Double.toString(Math.hypot(dx,dy))); arrays.start.setText(Double.toString(Math.toDegrees(Math.atan2(dy,dx))));
                    startArray(arrays.slots());
                }
                public void onCancel() { if(arrayGesture==gesture) stopArray(gesture); }
            });
            instruction.setText(I18n.text("ui.6b2d6bf14330"));
        } else {
            plotArea.beginEditorFlashPlacement(arrayCross(0,0),new PlotAreaView.PlacementHandler() {
                public void onCommit(double x,double y) {
                    if(session!=editing || arrayGesture!=gesture) return;
                    arrayGesture=null; arrays.origin(x,y); startArray(arrays.slots());
                }
                public void onCancel() { if(arrayGesture==gesture) stopArray(gesture); }
            });
            instruction.setText(I18n.text("ui.e1718eb06e68"));
        }
        refreshSelection();
    }

    private Button button(String label, String iconName, Runnable action) {
        Button button = new Button(label, host.icon(iconName));
        button.setOnAction(event -> action.run());
        return button;
    }

    private int selectedTool() {
        Integer id = toolChoice.getValue();
        if (id == null) throw new IllegalArgumentException(I18n.text("ui.6c0fd0615def"));
        return id;
    }

    void startSelection() {
        if (!isActive()) return;
        cancelArrayFromShortcut(); plotArea.cancelPlacement();
        instruction.setText(I18n.text("ui.ac94f3b01ef0"));
    }

    private void replicateGrid(boolean slots) {
        if (!isActive() || busy) return;
        plotArea.cancelPlacement();
        try {
            int added = session.arraySelected(slots,
                    Integer.parseInt(columnsField.getText().trim()),
                    Integer.parseInt(rowsField.getText().trim()),
                    parseDecimal(pitchXField.getText()), parseDecimal(pitchYField.getText()));
            refreshGeometry();
            instruction.setText(added + (slots ? " slots" : I18n.text("ui.dc238449e661")) + I18n.text("ui.ef5c4f837208"));
        } catch (NumberFormatException error) {
            instruction.setText(I18n.text("ui.da7a17e63816"));
        } catch (IllegalArgumentException error) {
            instruction.setText(error.getMessage());
        }
    }

    void resizeSelected() {
        if (!isActive() || busy) return;
        plotArea.cancelPlacement();
        try {
            int id = session.resizeSelected(parseDecimal(diameterField.getText()));
            refreshGeometry();
            toolChoice.setValue(id);
            instruction.setText(I18n.text("ui.36936f34674c") + id + ".");
        } catch (NumberFormatException error) {
            instruction.setText(I18n.text("ui.97ed5a3c85d5"));
        } catch (IllegalArgumentException error) {
            instruction.setText(error.getMessage());
        }
    }

    private static double parseDecimal(String text) {
        return Double.parseDouble(text.trim().replace(',', '.'));
    }

    void startDrill() {
        if (!isActive() || busy) return;
        try {
            int id = selectedTool();
            double radius = session.toolDiameters().get(id) / 2.0;
            Geometry footprint = FACTORY.createPoint(new Coordinate(0, 0)).buffer(radius, 16);
            if (plotArea.beginEditorFlashPlacement(footprint, new PlotAreaView.PlacementHandler() {
                @Override public void onCommit(double dx, double dy) {
                    session.addDrill(id, dx, dy);
                    refreshGeometry();
                    instruction.setText(I18n.text("ui.f784ea9ece06"));
                }
                @Override public void onCancel() { instruction.setText(I18n.text("ui.1de05dea5710")); }
            })) instruction.setText(I18n.text("ui.670ad0ac8ad6"));
        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
    }

    void startSlot() {
        if (!isActive() || busy) return;
        try {
            int id = selectedTool();
            double[] first = new double[2];
            if (plotArea.beginEditorTwoPointPlacement(PlotAreaView.TwoPointShape.SLOT,
                    session.toolDiameters().get(id), new PlotAreaView.PlacementHandler() {
                        @Override public void onAnchorChosen(double x, double y) {
                            first[0] = x; first[1] = y;
                            instruction.setText(I18n.text("ui.bd32300caebf"));
                        }
                        @Override public void onCommit(double dx, double dy) {
                            try {
                                session.addSlot(id, first[0], first[1], first[0] + dx, first[1] + dy);
                                refreshGeometry();
                                instruction.setText(I18n.text("ui.da59ac6ab13c"));
                            } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
                        }
                        @Override public void onCancel() { instruction.setText(I18n.text("ui.4718683257b1")); }
                    })) instruction.setText(I18n.text("ui.3a2551437635"));
        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
    }

    void startMoveOrCopy(boolean copy) {
        if (!isActive() || busy || session.selectedCount() == 0) {
            if (isActive()) instruction.setText(I18n.text("ui.d8f692237380"));
            return;
        }
        Set<Long> selectedAtStart = session.selectedIds();
        Geometry preview = session.selectedCount() > exactHighlightLimit
                ? session.selectedBounds() : session.selectedGeometry();
        if (plotArea.beginEditorPlacement(List.of(preview), new PlotAreaView.PlacementHandler() {
            @Override public void onCommit(double dx, double dy) {
                if (!session.selectedIds().equals(selectedAtStart)) return;
                if (copy ? session.copySelected(dx, dy) : session.moveSelected(dx, dy)) {
                    refreshGeometry();
                    instruction.setText(copy ? I18n.text("ui.d66b4df0eb8a") : I18n.text("ui.76deecdeadc1"));
                }
            }
            @Override public void onCancel() { instruction.setText(I18n.text("ui.441792596876")); }
        })) instruction.setText(I18n.text("ui.1f9d2c27383e"));
    }

    boolean deleteFromShortcut() { return deleteSelected(); }
    boolean undoFromShortcut() { return undo(); }
    boolean redoFromShortcut() { return redo(); }

    private boolean deleteSelected() {
        if (!isActive() || busy) return false;
        plotArea.cancelPlacement();
        if (!session.deleteSelected()) return false;
        refreshGeometry();
        return true;
    }

    private boolean undo() {
        if (!isActive() || busy) return false;
        plotArea.cancelPlacement();
        if (!session.undo()) return false;
        refreshGeometry();
        return true;
    }

    private boolean redo() {
        if (!isActive() || busy) return false;
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
        boolean simplified = session.selectedCount() > exactHighlightLimit;
        plotArea.setEditorHighlight(simplified ? session.selectedBounds() : session.selectedGeometry(), simplified, selectedColor);
        status.setText(session.size() + I18n.text("ui.1f3f2919d027") + session.selectedCount()
                + I18n.text("ui.5349cebdbed4") + (session.isDirty() ? I18n.text("ui.d56199d7cd0c") : I18n.text("ui.c830b9666887")));
        deleteButton.setDisable(session.selectedCount() == 0);
        boolean needsSelection=arrays!=null && arrays.grid();
        drillArrayButton.setDisable(busy || needsSelection && session.selectedCount()==0);
        slotArrayButton.setDisable(busy || needsSelection && session.selectedCount()==0);
        if(arrays!=null) arrays.state(busy,arrayGesture!=null,arrayReady,session.selectedCount()>0);
        resizeButton.setDisable(session.selectedCount() == 0);
        moveButton.setDisable(session.selectedCount() == 0);
        copyButton.setDisable(session.selectedCount() == 0);
        undoButton.setDisable(!session.canUndo());
        redoButton.setDisable(!session.canRedo());
        applyButton.setDisable(busy || !session.isDirty());
    }

    void apply() {
        if (!isActive() || busy) return;
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
        arrayGesture=null; busy=false; arrayReady=false; plotArea.setEditorReference(null);
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
