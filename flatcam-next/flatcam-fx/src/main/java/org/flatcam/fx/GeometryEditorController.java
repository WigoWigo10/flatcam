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
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.flatcam.cam.geometry.EditorArc;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.TextGeometry;
import org.locationtech.jts.geom.util.AffineTransformation;
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

        default void requestArcCoordinates(Coordinate cursor, String units, Consumer<ArcCoordinateDialog.Choice> selected) { }
        default void requestCoordinates(Coordinate cursor, String units, boolean allowInsert, Consumer<ArcCoordinateDialog.Choice> selected) {
            requestArcCoordinates(cursor,units,selected);
        }
        default void movePlotCursor(Coordinate point) { }
    }

    private record LayerKey(String name) {
    }

    private static final LayerKey SHAPES_LAYER = new LayerKey("geometry-editor-shapes");
    private Color shapeColor = Color.web("#FF0000AF");
    private int exactHighlightLimit = 500;
    private Color selectedColor = Color.BLUE;
    private static final javafx.css.PseudoClass ERASER_ACTIVE = javafx.css.PseudoClass.getPseudoClass("eraser-active");

    private final PlotAreaView plotArea;
    private final Host host;
    private final AsyncFontCatalog fontCatalog;
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
    private Button transformButton;
    private Button textButton;
    private Button eraserButton;
    private Button paintButton;
    private TitledPane paintPane;
    private TextField paintDiameter, paintOverlap, paintMargin;
    private ComboBox<org.flatcam.cam.ncc.NccMethod> paintMethod;
    private CheckBox paintConnect, paintContour;
    private TitledPane textPane;
    private TextArea textContent;
    private ComboBox<String> textFont;
    private TextField textSize;
    private CheckBox textBold;
    private CheckBox textItalic;
    private String units;
    private TextField bufferDistance;
    private ComboBox<String> bufferMode;
    private List<Button> editorButtons;
    private boolean busy;
    private boolean initiallyVisible;
    private boolean strokeOnly;
    private TitledPane arcPane;
    private ComboBox<EditorArc.Mode> arcMode;
    private CheckBox arcClockwise;
    private Object arcGesture;
    private long arcPointRevision;
    private TextField curveSteps;
    private boolean drawingCircle;
    private TextField rotationAngle, rotationX, rotationY;
    private TextField scaleX, scaleY, skewX, skewY, offsetX, offsetY;
    private ComboBox<TransformKind> transformKind;
    enum TransformKind {
        ROTATE(I18n.text("ui.ae0506abfa72")), SCALE(I18n.text("ui.52fe2a68ac59")), MIRROR_X(I18n.text("ui.9aeacccf0112")),
        MIRROR_Y(I18n.text("ui.d4f925bde049")), SKEW(I18n.text("ui.4a855af9b30e")), OFFSET(I18n.text("ui.7e407025886e"));
        final String label;
        TransformKind(String label) { this.label=label; }
        @Override public String toString() { return label; }
    }
    private ComboBox<RotationReference> rotationReference;
    private TitledPane transformsPane;
    private Button rotationConfirm, rotationCancel;
    private Object rotationGesture;
    private boolean rotationReady;
    private long coordinateRevision;

    enum RotationReference {
        SELECTION(I18n.text("ui.84d5c22a1c5c")), ORIGIN(I18n.text("ui.b821269b039c")), POINT(I18n.text("ui.29eb9b6ceb01")),
        LOWER_LEFT(I18n.text("ui.b11ed988a375")), LOWER_RIGHT(I18n.text("ui.68fa9b1439ce")),
        UPPER_RIGHT(I18n.text("ui.61fc75869a23")), UPPER_LEFT(I18n.text("ui.a2db40298836"));
        final String label;
        RotationReference(String label) { this.label=label; }
        @Override public String toString() { return label; }
    }
    private EraserGesture eraser;

    /** Frozen stencil: erasing the source shape must not silently change the next erase area. */
    private static final class EraserGesture {
        final GeometryEditSession editing;
        Geometry mask;
        Coordinate anchor;
        boolean suspending;
        EraserGesture(GeometryEditSession editing) { this.editing = editing; }
    }

    GeometryEditorController(PlotAreaView plotArea, Host host) {
        this(plotArea, host, AsyncFontCatalog.SYSTEM);
    }

    GeometryEditorController(PlotAreaView plotArea, Host host, AsyncFontCatalog fontCatalog) {
        this.plotArea = plotArea;
        this.host = host;
        this.fontCatalog = fontCatalog;
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
        start(sourceItem, geometry, tools, strokeOnly, "MM");
    }

    void start(TreeItem<String> sourceItem, Geometry geometry, List<ToolGeometry> tools, boolean strokeOnly, String units) {
        if (isActive()) {
            host.log(I18n.text("ui.4cd434d7b5a5"));
            return;
        }
        item = sourceItem;
        this.units = units;
        Color drawColor = ToolDefaults.color("editor.geometry.draw");
        shapeColor = drawColor;
        selectedColor = ToolDefaults.color("editor.geometry.selected");
        exactHighlightLimit = ToolDefaults.integer("editor.geometry.selectionlimit");
        session = new GeometryEditSession(geometry, tools);
        session.setClimbMilling(AppPreferences.loadGeometryEditorClimb());
        implicitToolIndex = tools.isEmpty() ? -1 : 0;
        initiallyVisible = plotArea.isLayerVisible(sourceItem);
        this.strokeOnly = strokeOnly;
        host.setObjectVisible(sourceItem, false);
        plotArea.putLayer(SHAPES_LAYER, PlotAreaView.LayerCategory.OVERLAY,
                session.resultGeometry(), shapeColor, shapeColor, strokeOnly);
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
        instruction = new Label(I18n.text("ui.e2a9c46ac34f"));
        instruction.setWrapText(true);
        shapeTable = new TableView<>(FXCollections.observableArrayList(session.shapeRows()));
        shapeTable.setId("geometry-shapes");
        instruction.setId("geometry-instruction");
        shapeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        shapeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<GeometryEditSession.ShapeRow, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(Long.toString(row.getValue().id())));
        idColumn.setPrefWidth(72);
        idColumn.setSortable(false);
        TableColumn<GeometryEditSession.ShapeRow, String> typeColumn = new TableColumn<>(I18n.text("ui.3868d2843d59"));
        typeColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().type()));
        typeColumn.setPrefWidth(65);
        typeColumn.setSortable(false);
        TableColumn<GeometryEditSession.ShapeRow, String> nameColumn = new TableColumn<>(I18n.text("ui.5086900635fe"));
        nameColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().name()));
        nameColumn.setPrefWidth(95);
        nameColumn.setSortable(false);
        shapeTable.getColumns().addAll(idColumn, typeColumn, nameColumn);
        shapeTable.setPrefHeight(360);
        shapeTable.setTooltip(new Tooltip(I18n.text("ui.f791943af647")));
        shapeTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change -> {
            if (syncingTable || busy || session == null) {
                return;
            }
            Set<Integer> current = new LinkedHashSet<>(shapeTable.getSelectionModel().getSelectedIndices());
            syncingTable = true;
            try {
                // Cancel callbacks may refresh the panel; do not let them restore the old table selection.
                plotArea.cancelPlacement();
                tableSelectionOrder.retainAll(current);
                for (int index : current) tableSelectionOrder.add(index);
                session.selectIndices(tableSelectionOrder);
                refreshSelection();
            } finally { syncingTable = false; }
        });
        Button selectButton = new Button(I18n.text("ui.6a57618c2593"));
        Button pathButton = new Button(I18n.text("ui.31591bad64b8"));
        Button polygonButton = new Button(I18n.text("ui.2c7f33880993"));
        Button rectangleButton = new Button(I18n.text("ui.0ff1131199c8"));
        Button circleButton = new Button(I18n.text("ui.1b3a8227c7e0"));
        Button arcButton = new Button(I18n.text("ui.a7dee6988001"));
        arcButton.setId("geometry-arc");
        deleteButton = new Button(I18n.text("ui.089c36593824"));
        moveButton = new Button(I18n.text("ui.82ba228461bf"));
        copyButton = new Button(I18n.text("ui.5569bb78f11f"));
        undoButton = new Button(I18n.text("ui.786b48222848"));
        redoButton = new Button(I18n.text("ui.7b659c9caa5e"));
        applyButton = new Button(I18n.text("ui.5c057b676f16"));
        applyButton.getStyleClass().add("primary-action");
        unionButton = new Button(I18n.text("ui.dd11d305ef9e"));
        intersectionButton = new Button(I18n.text("ui.5b95c96431f2"));
        subtractButton = new Button(I18n.text("ui.e55569bab807"));
        cutPathButton = new Button(I18n.text("ui.32b69f96fbc3"));
        bufferButton = new Button(I18n.text("ui.63d06fdb3cd8"));
        explodeButton = new Button(I18n.text("ui.09b64c236839"));
        transformButton = new Button(I18n.text("ui.32788c0c09e0"));
        textButton = new Button(I18n.text("ui.6df3d6661c09")); textButton.setId("geometry-text");
        eraserButton = new Button(I18n.text("ui.bc0687f51345")); eraserButton.setId("geometry-eraser");
        paintButton = new Button("Paint Shape"); paintButton.setId("geometry-paint");
        bufferDistance = new TextField();
        bufferDistance.setPromptText(I18n.text("ui.842c36fa8cb2"));
        bufferMode = new ComboBox<>();
        bufferMode.getItems().addAll(I18n.text("ui.23e3685fbe6c"), "Interior", "Exterior");
        bufferMode.getSelectionModel().selectFirst();
        bufferMode.setMaxWidth(Double.MAX_VALUE);
        Button cancelButton = new Button(I18n.text("ui.f1cb621ee9d8"));
        editorButtons = List.of(selectButton, pathButton, polygonButton, rectangleButton, circleButton, arcButton,
                deleteButton, moveButton, copyButton, unionButton, intersectionButton, subtractButton,
                bufferButton, explodeButton, cutPathButton, transformButton, textButton, eraserButton, paintButton,
                undoButton, redoButton, applyButton, cancelButton);
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
        undoButton.setTooltip(new Tooltip(I18n.text("ui.e8908ee8f72b")));
        redoButton.setText("↷");
        redoButton.setTooltip(new Tooltip(I18n.text("ui.189406ffe91a")));
        iconize(applyButton, "close_edit_file32.png");
        iconize(unionButton, "union32.png");
        iconize(intersectionButton, "intersection32.png");
        iconize(subtractButton, "subtract32.png");
        iconize(cutPathButton, "cutpath32.png");
        iconize(bufferButton, "buffer16-2.png");
        iconize(explodeButton, "explode32.png");
        iconize(transformButton, "transform.png");
        iconize(textButton, "text32.png");
        iconize(eraserButton, "eraser26.png");
        iconize(paintButton, "paint20_1.png");
        GeometryEditorDescriptions.apply(paintButton, "paint");
        GeometryEditorDescriptions.apply(textButton, "text");
        GeometryEditorDescriptions.apply(eraserButton, "eraser");
        GeometryEditorDescriptions.apply(arcButton, "arc");
        GeometryEditorDescriptions.apply(transformButton,"transform");
        GeometryEditorDescriptions.apply(bufferButton, "buffer");
        GeometryEditorDescriptions.apply(cutPathButton, "cut_path");
        GeometryEditorDescriptions.apply(subtractButton, "subtract");
        GeometryEditorDescriptions.apply(unionButton, "union");
        GeometryEditorDescriptions.apply(intersectionButton, "intersection");
        GeometryEditorDescriptions.apply(explodeButton, "explode");
        GeometryEditorDescriptions.apply(moveButton, "move");
        GeometryEditorDescriptions.apply(copyButton, "copy");
        ToolDescriptions.apply(bufferMode, I18n.text("ui.0e193ba8b68e"), GeometryEditorDescriptions.of("buffer").text());
        ToolDescriptions.apply(bufferDistance, I18n.text("ui.d5a33f3a788b"),
                I18n.text("ui.e6a9a7f742ea"));
        iconize(cancelButton, "power16.png");
        selectButton.setOnAction(event -> startSelection());
        pathButton.setOnAction(event -> startPath());
        polygonButton.setOnAction(event -> startPolygon());
        rectangleButton.setOnAction(event -> startRectangle());
        circleButton.setOnAction(event -> startCircle());
        arcButton.setOnAction(event -> startArc());
        deleteButton.setOnAction(event -> deleteSelected());
        TableKeyboardActions.deleteWith(shapeTable, deleteButton, true);
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
        textButton.setOnAction(event -> startText());
        eraserButton.setOnAction(event -> startEraser());
        paintButton.setOnAction(event -> startPaint());
        VBox panel = new VBox(8, new Label("Geometry Editor"), shapeTable, status, instruction);
        arcMode = new ComboBox<>(FXCollections.observableArrayList(EditorArc.Mode.values()));
        arcMode.setId("geometry-arc-mode"); arcMode.setMaxWidth(Double.MAX_VALUE);
        arcMode.setConverter(new javafx.util.StringConverter<>() {
            public String toString(EditorArc.Mode mode) { return mode == null ? "" : arcModeLabel(mode); }
            public EditorArc.Mode fromString(String text) { throw new UnsupportedOperationException(); }
        });
        arcMode.setValue(EditorArc.Mode.CENTER_START_END);
        arcClockwise = new CheckBox(I18n.text("ui.c3966703acd9")); arcClockwise.setSelected(true);
        arcClockwise.setId("geometry-arc-clockwise");
        arcClockwise.disableProperty().bind(arcMode.valueProperty().isEqualTo(EditorArc.Mode.START_END_THROUGH));
        ToolDescriptions.apply(arcMode, I18n.text("ui.e34640639f85"), GeometryEditorDescriptions.of("arc").text());
        ToolDescriptions.apply(arcClockwise, I18n.text("ui.eb4436de40dc"),
                I18n.text("ui.e7cb2a7851b6"));
        arcMode.valueProperty().addListener((obs, old, value) -> {
            if (arcGesture != null) plotArea.resetEditorThreePointPlacement();
        });
        arcClockwise.selectedProperty().addListener((obs, old, value) -> {
            if (arcGesture != null) { plotArea.refreshEditorThreePointPreview(); arcInstruction(-1); }
        });
        curveSteps = new TextField(ToolDefaults.text("geometry.editorcirclesteps",true));
        curveSteps.setId("geometry-curve-steps"); curveSteps.setPrefColumnCount(5); curveSteps.setMinWidth(0);
        ToolDescriptions.apply(curveSteps,I18n.text("ui.1f83845e817d"),
                ToolDefaultsDescriptions.text(ToolDefaults.setting("geometry.editorcirclesteps"),true));
        Button saveCurveSteps = new Button(I18n.text("ui.68a214833311")); saveCurveSteps.setId("geometry-curve-steps-save");
        ToolDescriptions.apply(saveCurveSteps,I18n.text("ui.68a214833311"),
                I18n.text("ui.3c337cc108ea"));
        saveCurveSteps.setOnAction(event -> {
            try { ToolDefaults.set("geometry.editorcirclesteps",true,Integer.toString(curveSteps())); instruction.setText(I18n.text("ui.4fcc545f9d3e")); }
            catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
        });
        curveSteps.textProperty().addListener((obs, old, value) -> {
            if (arcGesture != null) {
                plotArea.refreshEditorThreePointPreview();
                try { curveSteps(); arcInstruction(-1); } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
            } else if (drawingCircle) { plotArea.cancelPlacement(); instruction.setText(I18n.text("ui.b861d64073ea")); }
        });
        Label arcHelp = new Label(I18n.text("ui.6db3f0d0e624"));
        arcHelp.setWrapText(true);
        arcPane = new TitledPane(I18n.text("ui.eb1bd0729860"), new VBox(6, arcMode, arcClockwise,
                new HBox(8,new Label("Segmentos/volta:"),curveSteps),saveCurveSteps,arcHelp));
        arcPane.setExpanded(false); panel.getChildren().add(arcPane);
        textContent = new TextArea(); textContent.setId("geometry-text-content");
        textContent.setPromptText(I18n.text("ui.7482a7c63c2b")); textContent.setPrefRowCount(3);
        textContent.setWrapText(true);
        ToolDescriptions.apply(textContent, I18n.text("ui.002816b942d6"),
                I18n.text("ui.241249f1800a"));
        // Logical fonts work before enumeration; opening the editor must not scan the font directory.
        textFont = new ComboBox<>(FXCollections.observableArrayList("SansSerif", "Serif", "Monospaced"));
        textFont.setId("geometry-text-font"); textFont.setMaxWidth(Double.MAX_VALUE);
        textFont.setValue("SansSerif");
        ToolDescriptions.apply(textFont, I18n.text("ui.2d66bbb54d3f"),
                I18n.text("ui.a13285182ae8"));
        textSize = new TextField("10"); textSize.setId("geometry-text-size"); textSize.setPrefColumnCount(5);
        ToolDescriptions.apply(textSize, I18n.text("ui.243cf75e8126"),
                I18n.text("ui.bf7a0cc18cec") + units + I18n.text("ui.1357f3644c4d"));
        textBold = new CheckBox(I18n.text("ui.0466381bc96d")); textItalic = new CheckBox(I18n.text("ui.f3db44313704"));
        for (var property : List.of(textContent.textProperty(), textSize.textProperty(), textFont.valueProperty()))
            property.addListener((obs, old, value) -> {
                if (!busy && !Boolean.TRUE.equals(textFont.getProperties().get("fonts-updating")))
                    plotArea.cancelPlacement();
            });
        textBold.selectedProperty().addListener((obs, old, value) -> { if (!busy) plotArea.cancelPlacement(); });
        textItalic.selectedProperty().addListener((obs, old, value) -> { if (!busy) plotArea.cancelPlacement(); });
        Button placeText = new Button(I18n.text("ui.3c9949cad786")); placeText.setId("geometry-text-place");
        placeText.getStyleClass().add("primary-action"); placeText.setMaxWidth(Double.MAX_VALUE);
        ToolDescriptions.apply(placeText, I18n.text("ui.67562d368c2c"),
                I18n.text("ui.9b6c926a524a"));
        placeText.setOnAction(event -> generateText());
        Label textHelp = new Label(I18n.text("ui.67009982f3ba"));
        textHelp.setWrapText(true);
        textPane = new TitledPane(I18n.text("ui.39409c7cadff"), new VBox(8, new Label(I18n.text("ui.0dd6da8d793f")), textFont,
                new HBox(8, new Label(I18n.text("ui.999e8aab92fc")), textSize), new HBox(8, textBold, textItalic),
                textContent, textHelp, placeText));
        textPane.setExpanded(false); panel.getChildren().add(textPane);
        GeometryEditSession fontSession = session;
        ComboBox<String> fontPicker = textFont;
        textPane.expandedProperty().addListener((obs, was, expanded) -> {
            if (!expanded || Boolean.TRUE.equals(fontPicker.getProperties().get("fonts-loaded"))
                    || Boolean.TRUE.equals(fontPicker.getProperties().get("fonts-loading"))) return;
            fontPicker.getProperties().put("fonts-loading", true);
            fontPicker.setDisable(true);
            fontPicker.setPromptText(I18n.text("ui.367abdb3b77d"));
            fontCatalog.load().whenComplete((fonts, failure) -> javafx.application.Platform.runLater(() -> {
                // Closing/reopening an editor must not let an old completion update its new controls.
                if (session != fontSession || textFont != fontPicker) return;
                fontPicker.getProperties().remove("fonts-loading");
                fontPicker.setDisable(false);
                if (failure != null) {
                    fontPicker.setPromptText(I18n.text("ui.23c6009cb53a"));
                    host.log(I18n.text("ui.32bdb4e7a1f3"));
                    return;
                }
                String selected = fontPicker.getValue();
                fontPicker.getProperties().put("fonts-updating", true);
                try {
                    fontPicker.getItems().setAll(fonts);
                    fontPicker.setValue(fonts.contains(selected) ? selected
                            : fonts.contains("Arial") ? "Arial" : "SansSerif");
                } finally {
                    fontPicker.getProperties().remove("fonts-updating");
                }
                fontPicker.setPromptText(null);
                fontPicker.getProperties().put("fonts-loaded", true);
            }));
        });
        paintDiameter = new TextField("0.3"); paintDiameter.setId("geometry-paint-diameter");
        paintOverlap = new TextField("40"); paintOverlap.setId("geometry-paint-overlap");
        paintMargin = new TextField("0"); paintMargin.setId("geometry-paint-margin");
        paintMethod = new ComboBox<>(FXCollections.observableArrayList(org.flatcam.cam.ncc.NccMethod.values()));
        paintMethod.setValue(org.flatcam.cam.ncc.NccMethod.STANDARD); paintMethod.setId("geometry-paint-method");
        paintConnect = new CheckBox("Connect"); paintConnect.setSelected(true);
        paintContour = new CheckBox("Contour"); paintContour.setSelected(true);
        Button paintPreview = new Button(I18n.text("ui.e84776dcfcee")); paintPreview.setId("geometry-paint-preview");
        Button paintConfirm = new Button(I18n.text("ui.2beae1d1d468")); paintConfirm.setId("geometry-paint-confirm");
        Button paintCancel = new Button(I18n.text("ui.e7918951df55"));
        paintPreview.setOnAction(event -> generatePaint());
        paintConfirm.setOnAction(event -> { if (rotationGesture == null) plotArea.confirmEditorFixedPreview(); });
        paintCancel.setOnAction(event -> plotArea.cancelPlacement());
        paintConfirm.disableProperty().bind(paintPreview.disableProperty());
        for (javafx.beans.value.ObservableValue<?> property : List.<javafx.beans.value.ObservableValue<?>>of(paintDiameter.textProperty(), paintOverlap.textProperty(), paintMargin.textProperty(), paintMethod.valueProperty()))
            property.addListener((obs, was, value) -> { if (!busy) plotArea.cancelPlacement(); });
        paintConnect.selectedProperty().addListener((obs, was, value) -> { if (!busy) plotArea.cancelPlacement(); });
        paintContour.selectedProperty().addListener((obs, was, value) -> { if (!busy) plotArea.cancelPlacement(); });
        ToolDescriptions.apply(paintDiameter, I18n.text("ui.ad6a86804cf3"), I18n.text("ui.60924936d8f5"));
        ToolDescriptions.apply(paintMargin, I18n.text("ui.99351510cba8"), I18n.text("ui.922bec7ea03a"));
        ToolDescriptions.apply(paintPreview, I18n.text("ui.6df54939cb6b"), GeometryEditorDescriptions.of("paint").text());
        paintPane = new TitledPane("Paint Shape", new VBox(8,
                new HBox(8, new Label("Tool Dia:"), paintDiameter), new HBox(8, new Label("Overlap (%):"), paintOverlap),
                new HBox(8, new Label(I18n.text("ui.16bab5b0d1de")), paintMargin), new HBox(8, new Label("Method:"), paintMethod),
                new HBox(8, paintConnect, paintContour), paintPreview, paintConfirm, paintCancel));
        paintPane.setExpanded(false); panel.getChildren().add(paintPane);
        rotationAngle = new TextField("90"); rotationAngle.setId("geometry-rotation-angle");
        rotationAngle.setPrefColumnCount(6); rotationAngle.setMinWidth(0);
        rotationReference = new ComboBox<>(FXCollections.observableArrayList(RotationReference.values()));
        rotationReference.setId("geometry-rotation-reference"); rotationReference.setValue(RotationReference.SELECTION);
        rotationReference.setMinWidth(0); rotationReference.setMaxWidth(Double.MAX_VALUE);
        rotationX = new TextField("0"); rotationY = new TextField("0");
        rotationX.setId("geometry-rotation-x"); rotationY.setId("geometry-rotation-y");
        for (TextField f : List.of(rotationX,rotationY)) {
            f.setPrefColumnCount(6); f.setMinWidth(0);
            f.disableProperty().bind(rotationReference.valueProperty().isNotEqualTo(RotationReference.POINT));
            ToolDescriptions.apply(f,I18n.text("ui.452ea9732e44"),I18n.text("ui.6e822dac184e") + units + I18n.text("ui.0b39ba63c9cd"));
        }
        transformKind=new ComboBox<>(FXCollections.observableArrayList(TransformKind.values()));
        transformKind.setId("geometry-transform-kind"); transformKind.setValue(TransformKind.ROTATE);
        transformKind.setMaxWidth(Double.MAX_VALUE); transformKind.setMinWidth(0);
        scaleX=transformField("scale-x","1"); scaleY=transformField("scale-y","1");
        skewX=transformField("skew-x","0"); skewY=transformField("skew-y","0");
        offsetX=transformField("offset-x","0"); offsetY=transformField("offset-y","0");
        Button rotate = new Button(I18n.text("ui.9311ad39c7a2")); rotate.setId("geometry-rotation-preview");
        Button dragRotate = new Button(I18n.text("ui.32575822e865")); dragRotate.setId("geometry-rotation-drag");
        Button pickPivot = new Button(I18n.text("ui.84c7cb9dcb9b")); pickPivot.setId("geometry-rotation-pick");
        rotationConfirm = new Button(I18n.text("ui.9e2e8c968cb0")); rotationConfirm.setId("geometry-rotation-confirm");
        rotationCancel = new Button(I18n.text("ui.e7918951df55")); rotationCancel.setId("geometry-rotation-cancel");
        ToolDescriptions.apply(rotationAngle,I18n.text("ui.01139f1fb1bb"),GeometryEditorDescriptions.of("rotate").text());
        ToolDescriptions.apply(pickPivot,I18n.text("ui.3f47f23a6f1b"),I18n.text("ui.871a6e6565ff"));
        rotate.setOnAction(event -> previewTransform());
        dragRotate.setOnAction(event -> startAngularRotation());
        ToolDescriptions.apply(dragRotate,I18n.text("ui.c86ca6156b2b"),I18n.text("ui.57ca4fd08ceb"));
        pickPivot.setOnAction(event -> pickRotationPivot());
        rotationConfirm.setOnAction(event -> { if (rotationReady) plotArea.confirmEditorFixedPreview(); });
        rotationCancel.setOnAction(event -> cancelRotationFromShortcut());
        for (var property : List.<javafx.beans.value.ObservableValue<?>>of(rotationAngle.textProperty(),rotationX.textProperty(),rotationY.textProperty(),rotationReference.valueProperty(),
                transformKind.valueProperty(),scaleX.textProperty(),scaleY.textProperty(),skewX.textProperty(),skewY.textProperty(),offsetX.textProperty(),offsetY.textProperty()))
            property.addListener((obs, old, value) -> { if (rotationGesture != null) cancelRotationFromShortcut(); });
        for (Button button : List.of(rotate, pickPivot, dragRotate)) button.disableProperty().bind(transformButton.disableProperty());
        transformRow(dragRotate,TransformKind.ROTATE);
        var angleRow=new HBox(8,new Label(I18n.text("ui.c2ea553b4a08")),rotationAngle);
        var scaleRow=new HBox(8,new Label(I18n.text("ui.eef7a3ba37cc")),scaleX,new Label("Y:"),scaleY);
        var skewRow=new HBox(8,new Label(I18n.text("ui.e3db2a27ead4")),skewX,new Label("Y:"),skewY);
        var offsetRow=new HBox(8,new Label(I18n.text("ui.10e3451c13ab")),offsetX,new Label("Y:"),offsetY);
        transformRow(angleRow,TransformKind.ROTATE); transformRow(scaleRow,TransformKind.SCALE);
        transformRow(skewRow,TransformKind.SKEW); transformRow(offsetRow,TransformKind.OFFSET);
        ToolDescriptions.apply(transformKind,I18n.text("ui.b3dc418dd371"),I18n.text("ui.a802bb0a1ea8"));
        ToolDescriptions.apply(rotationReference,I18n.text("ui.293a024bff6e"),I18n.text("ui.4113792862db"));
        ToolDescriptions.apply(rotate,I18n.text("ui.ab950571c152"),I18n.text("ui.fcc98ef7694d"));
        ToolDescriptions.apply(rotationConfirm,I18n.text("ui.9e2e8c968cb0"),I18n.text("ui.01c5862f2a9c"));
        ToolDescriptions.apply(rotationCancel,I18n.text("ui.e7918951df55"),I18n.text("ui.ee758110e80a"));
        var pivotRow=new HBox(8,new Label(I18n.text("ui.6974db62f04c")),rotationX,new Label("Y:"),rotationY);
        for(Node node:List.of(rotationReference,pivotRow,pickPivot)) {
            node.visibleProperty().bind(transformKind.valueProperty().isNotEqualTo(TransformKind.OFFSET));
            node.managedProperty().bind(node.visibleProperty());
        }
        VBox transformControls = new VBox(8,transformKind,angleRow,scaleRow,skewRow,offsetRow,rotationReference,
                pivotRow,pickPivot,dragRotate,rotate,rotationConfirm,rotationCancel);
        transformsPane = new TitledPane(I18n.text("ui.28799639a7b3"), transformControls);
        transformsPane.setExpanded(false);
        transformButton.setOnAction(event -> transformsPane.setExpanded(!transformsPane.isExpanded()));
        panel.getChildren().add(transformsPane);
        RadioButton climbRadio = new RadioButton("Climb");
        RadioButton conventionalRadio = new RadioButton("Conventional");
        ToggleGroup millingGroup = new ToggleGroup();
        climbRadio.setToggleGroup(millingGroup);
        conventionalRadio.setToggleGroup(millingGroup);
        (session.climbMilling() ? climbRadio : conventionalRadio).setSelected(true);
        millingGroup.selectedToggleProperty().addListener((observable, previous, selectedToggle) -> {
            if (selectedToggle != null) {
                boolean climb = selectedToggle == climbRadio;
                session.setClimbMilling(climb);
                AppPreferences.saveGeometryEditorClimb(climb);
            }
        });
        Tooltip millingHelp = new Tooltip(I18n.text("ui.8f468eddb095"));
        climbRadio.setTooltip(millingHelp);
        conventionalRadio.setTooltip(millingHelp);
        panel.getChildren().add(new HBox(10, new Label(I18n.text("ui.42f7fc68175e")), climbRadio, conventionalRadio));
        if (tools.size() > 1) {
            toolChoice = new ComboBox<>();
            for (int i = 0; i < tools.size(); i++) {
                toolChoice.getItems().add(String.format(java.util.Locale.ROOT,
                        I18n.text("ui.315e0a9f9291"), i + 1, tools.get(i).toolDiameter()));
            }
            toolChoice.getSelectionModel().selectFirst();
            toolChoice.setMaxWidth(Double.MAX_VALUE);
            ToolDescriptions.apply(toolChoice, I18n.text("ui.0dc35b7445a7"),
                    I18n.text("ui.84db682ec545"));
            toolChoice.valueProperty().addListener((obs, old, value) -> plotArea.cancelPlacement());
        }
        exitButton = new Button(I18n.text("ui.8d911fc2825e"));
        exitButton.getStyleClass().add("primary-action");
        exitButton.setMaxWidth(Double.MAX_VALUE);
        exitButton.setOnAction(event -> apply());
        discardButton = new Button(I18n.text("ui.534c4da81431"));
        discardButton.setMaxWidth(Double.MAX_VALUE);
        discardButton.setOnAction(event -> cancel());
        panel.getChildren().addAll(exitButton, discardButton);
        ToolBar toolbar = new ToolBar(selectButton, circleButton, arcButton, rectangleButton,
                new Separator(), pathButton, polygonButton,
                new Separator(), textButton, bufferButton,
                paintButton,
                eraserButton,
                new Separator(), unionButton, explodeButton, intersectionButton, subtractButton,
                new Separator(), cutPathButton,
                copyButton, deleteButton, transformButton, moveButton,
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
        host.openToolPanel(I18n.text("ui.73031cbbeabc"), panel);
        host.showEditorToolbar(toolbar);
        refreshSelection();
    }

    private void iconize(Button button, String icon) {
        String label = button.getText();
        button.setText(null);
        button.setGraphic(host.icon(icon));
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
    }

    private double parseTransformNumber(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + I18n.text("ui.f894e7675102"));
        }
    }

    void openTransforms() {
        if (readyForTool()) transformsPane.setExpanded(true);
    }

    private TextField transformField(String id,String initial) {
        var field=new TextField(initial); field.setId("geometry-transform-"+id);
        field.setPrefColumnCount(5); field.setMinWidth(0);
        String help=id.startsWith("scale") ? I18n.text("ui.f66a78d4f790")
                : id.startsWith("skew") ? I18n.text("ui.41dbf3a1b6db")
                : I18n.text("ui.ff7653c4e326") + units + I18n.text("ui.dd7887465bb4");
        ToolDescriptions.apply(field,I18n.text("ui.e8f20ca549fe"),help+I18n.text("ui.ccbd247f09e2"));
        return field;
    }

    private void transformRow(Node row,TransformKind kind) {
        row.visibleProperty().bind(transformKind.valueProperty().isEqualTo(kind));
        row.managedProperty().bind(row.visibleProperty());
    }

    private Coordinate rotationPivot() {
        if (rotationReference.getValue() == RotationReference.ORIGIN) return new Coordinate(0,0);
        if (rotationReference.getValue() == RotationReference.POINT)
            return new Coordinate(parseTransformNumber(rotationX,I18n.text("ui.81e9bde31be5")),parseTransformNumber(rotationY,I18n.text("ui.5dfc296ef2f7")));
        var bounds=session.selectedBounds().getEnvelopeInternal();
        return switch(rotationReference.getValue()) {
            case LOWER_LEFT -> new Coordinate(bounds.getMinX(),bounds.getMinY());
            case LOWER_RIGHT -> new Coordinate(bounds.getMaxX(),bounds.getMinY());
            case UPPER_RIGHT -> new Coordinate(bounds.getMaxX(),bounds.getMaxY());
            case UPPER_LEFT -> new Coordinate(bounds.getMinX(),bounds.getMaxY());
            default -> bounds.centre();
        };
    }

    private Geometry pivotCross(Coordinate pivot) {
        var factory=new org.locationtech.jts.geom.GeometryFactory(); double r=plotArea.worldPerPixel()*5;
        return factory.buildGeometry(List.of(factory.createLineString(new Coordinate[]{new Coordinate(pivot.x-r,pivot.y),new Coordinate(pivot.x+r,pivot.y)}),
                factory.createLineString(new Coordinate[]{new Coordinate(pivot.x,pivot.y-r),new Coordinate(pivot.x,pivot.y+r)})));
    }

    private void pickRotationPivot() {
        if (!readyForTool() || session.selectedCount()==0) return;
        plotArea.cancelPlacement(); Object gesture=new Object(); rotationGesture=gesture; rotationReady=false;
        GeometryEditSession editing=session; var selection=editing.selectedIndexOrder();
        boolean started=plotArea.beginEditorFlashPlacement(pivotCross(new Coordinate(0,0)),new PlotAreaView.PlacementHandler() {
            public void onCommit(double x,double y) {
                if (session!=editing || rotationGesture!=gesture || !selection.equals(editing.selectedIndexOrder())) return;
                rotationGesture=null;
                rotationX.setText(Double.toString(x)); rotationY.setText(Double.toString(y));
                rotationReference.setValue(RotationReference.POINT); previewTransform();
            }
            public void onCancel() { if (rotationGesture==gesture) stopRotation(gesture,I18n.text("ui.481484c95e65")); }
        });
        if (!started) stopRotation(gesture,I18n.text("ui.ab51e8db7fc6"));
        else instruction.setText(I18n.text("ui.4c1e1001e6b5"));
        refreshSelection();
    }

    private void previewTransform() {
        if (!readyForTool()) return;
        if (session.selectedCount()==0) { instruction.setText(I18n.text("ui.9c67cb343e77")); return; }
        plotArea.cancelPlacement();
        GeometryEditSession editing=session;
        try {
            Coordinate pivot=transformKind.getValue()==TransformKind.OFFSET
                    ? session.selectedBounds().getEnvelopeInternal().centre() : rotationPivot();
            var request=switch(transformKind.getValue()) {
                case ROTATE -> editing.prepareRotation(parseTransformNumber(rotationAngle,I18n.text("ui.c578e844cecc")),pivot);
                case SCALE -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.scale(
                        parseTransformNumber(scaleX,I18n.text("ui.d21e6beed4f9")),parseTransformNumber(scaleY,I18n.text("ui.149363d48e5a"))),pivot);
                case MIRROR_X -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.scale(-1,1),pivot);
                case MIRROR_Y -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.scale(1,-1),pivot);
                case SKEW -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.skew(
                        parseTransformNumber(skewX,I18n.text("ui.3dae7c444115")),parseTransformNumber(skewY,I18n.text("ui.7f12ee5de1d6"))),pivot);
                case OFFSET -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.offset(
                        parseTransformNumber(offsetX,I18n.text("ui.1c82f02bf236")),parseTransformNumber(offsetY,I18n.text("ui.3c4d14a15b40"))),new Coordinate(0,0));
            };
            Object gesture=new Object(); rotationGesture=gesture; rotationReady=false;
            calculate(request,result -> {
                if (rotationGesture!=gesture) return;
                if (result.resultParts().isEmpty()) { stopRotation(gesture,I18n.text("ui.d0c7d683a67b")); return; }
                var factory=result.resultParts().getFirst().geometry().getFactory();
                // Use the indexed overlay for dense shapes; only the small pivot marker is a placement preview.
                Geometry preview=factory.buildGeometry(result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
                boolean started=plotArea.beginEditorFixedPreview(pivotCross(pivot),new PlotAreaView.PlacementHandler() {
                    public void onCommit(double dx,double dy) {
                        if (session!=editing || rotationGesture!=gesture) return;
                        rotationGesture=null; rotationReady=false; plotArea.setEditorReference(null);
                        boolean changed=editing.applyTransform(result); refreshGeometry();
                        instruction.setText(changed ? I18n.text("ui.8f42e3711726")
                                : I18n.text("ui.7e7497645ead"));
                    }
                    public void onCancel() { if (rotationGesture==gesture) stopRotation(gesture,I18n.text("ui.7f19e9e5be41")); }
                });
                if (!started) { stopRotation(gesture,I18n.text("ui.1c4f0f1be6d3")); return; }
                plotArea.setEditorReference(preview); rotationReady=true; refreshSelection();
                instruction.setText(I18n.text("ui.e582db8def1e"));
            },() -> stopRotation(gesture,null));
        } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
    }

    private void startAngularRotation() {
        if (!readyForTool() || session.selectedCount()==0) return;
        plotArea.cancelPlacement();
        try {
            Coordinate pivot = rotationPivot();
            GeometryEditSession editing = session;
            var selected = editing.selectedIndexOrder();
            Object gesture = new Object(); rotationGesture = gesture; rotationReady = false;
            boolean started = plotArea.beginEditorAngularPlacement(pivot,editing.selectedBounds(),new PlotAreaView.AngularPlacementHandler() {
                public void onAngleChanged(double degrees) {
                    if (rotationGesture==gesture) instruction.setText(String.format(java.util.Locale.ROOT,
                            I18n.text("ui.dd9f13ce5476"),degrees));
                }
                public void onRelease(double degrees) {
                    if (session!=editing || rotationGesture!=gesture || !selected.equals(editing.selectedIndexOrder())) return;
                    rotationGesture = null;
                    rotationAngle.setText(Double.toString(degrees));
                    previewTransform();
                }
                public void onCancel() { stopRotation(gesture,I18n.text("ui.ceeb8282f34c")); }
            });
            if (!started) stopRotation(gesture,I18n.text("ui.1e4953e17bc7"));
            else {
                refreshSelection();
                instruction.setText(I18n.text("ui.3caac4066413"));
            }
        } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
    }

    boolean cancelRotationFromShortcut() {
        if (rotationGesture==null) return false;
        Object gesture=rotationGesture;
        stopRotation(gesture,busy ? I18n.text("ui.68fd2074c092") : I18n.text("ui.7f19e9e5be41"));
        plotArea.cancelPlacement(); return true;
    }

    private void stopRotation(Object gesture,String message) {
        if (rotationGesture!=gesture) return;
        rotationGesture=null; rotationReady=false; plotArea.setEditorReference(null);
        refreshSelection(); if (message!=null) instruction.setText(message);
    }

    private Button plannedToolButton(String label, String icon) {
        Button button = new Button(null, host.icon(icon));
        button.setTooltip(new Tooltip(label + I18n.text("ui.af814f12eeff")));
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
        instruction.setText(I18n.text("ui.4ff64d95e708"));
    }

    void startPath() {
        startMultiPoint(false);
    }

    void startPolygon() {
        startMultiPoint(true);
    }

    void startArc() {
        if (!readyForTool()) return;
        try { curveSteps(); } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); return; }
        GeometryEditSession editing = session;
        int toolIndex = selectedToolIndex();
        plotArea.cancelPlacement();
        Object gesture = new Object(); arcGesture = gesture; arcPane.setExpanded(true);
        if (!plotArea.beginEditorThreePointPlacement(new PlotAreaView.ThreePointPlacementHandler() {
            public Geometry preview(List<Coordinate> points, Coordinate cursor) {
                if (points.isEmpty()) return null;
                var factory = new org.locationtech.jts.geom.GeometryFactory();
                if (points.size() == 1) return factory.createLineString(new Coordinate[]{points.getFirst(), cursor});
                try {
                    var arc = EditorArc.build(factory, arcMode.getValue(), arcClockwise.isSelected(),
                            List.of(points.get(0), points.get(1), cursor),curveSteps());
                    double cross = plotArea.worldPerPixel() * 4;
                    Coordinate c = arc.center();
                    return factory.buildGeometry(List.of(arc.path(),
                            factory.createLineString(new Coordinate[]{new Coordinate(c.x - cross, c.y), new Coordinate(c.x + cross, c.y)}),
                            factory.createLineString(new Coordinate[]{new Coordinate(c.x, c.y - cross), new Coordinate(c.x, c.y + cross)})));
                } catch (IllegalArgumentException invalid) {
                    return factory.createLineString(new Coordinate[]{points.getFirst(), points.get(1), cursor});
                }
            }
            public void onPointsChanged(int count) { ++arcPointRevision; if (arcGesture == gesture) arcInstruction(count); }
            public boolean onCommit(List<Coordinate> points) {
                if (session != editing || arcGesture != gesture) return false;
                try {
                    editing.addArc(arcMode.getValue(), arcClockwise.isSelected(), points, toolIndex,curveSteps());
                    arcGesture = null;
                    refreshGeometry();
                    instruction.setText(I18n.text("ui.0a617f0e0e81"));
                    return true;
                } catch (IllegalArgumentException invalid) {
                    instruction.setText(invalid.getMessage() + I18n.text("ui.1068b15b9ed6"));
                    return false;
                }
            }
            public void onCancel() {
                if (arcGesture == gesture) {
                    arcGesture = null; instruction.setText(I18n.text("ui.cb495e78bafa"));
                }
            }
        })) {
            arcGesture = null; instruction.setText(I18n.text("ui.b6ad2dc1fc79"));
        }
    }

    boolean isArcActive() { return arcGesture != null; }

    boolean handleArcKey(javafx.scene.input.KeyCode key) {
        if (arcGesture == null) return false;
        return switch (key) {
            case J -> { requestCoordinates(); yield true; }
            case M -> { arcMode.setValue(EditorArc.Mode.values()[(arcMode.getValue().ordinal() + 1) % 3]); yield true; }
            case D -> {
                if (arcMode.getValue() != EditorArc.Mode.START_END_THROUGH) arcClockwise.setSelected(!arcClockwise.isSelected());
                else instruction.setText(I18n.text("ui.248eb38000a2"));
                yield true;
            }
            case BACK_SPACE -> { plotArea.backtrackEditorThreePointPlacement(); yield true; }
            case ENTER -> { instruction.setText(I18n.text("ui.f01c93e6e8ea")); yield true; }
            default -> false;
        };
    }

    private int curveSteps() {
        try { int steps = Integer.parseInt(curveSteps.getText().trim()); EditorArc.validateSteps(steps); return steps; }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(I18n.text("ui.b88c30ccbeda")); }
    }

    boolean requestCoordinates() {
        if (!isActive()) return false;
        if (busy) { host.log(I18n.text("ui.4d2d14fced43")); return true; }
        GeometryEditSession editing = session;
        Object gesture = arcGesture;
        long revision = arcPointRevision;
        long placement = plotArea.placementRevision(), draft = coordinateRevision;
        var selected = editing.selectedIndexOrder();
        host.requestCoordinates(plotArea.editorCursorPoint(),units,gesture!=null,choice -> {
            if (choice == null || session!=editing || arcGesture != gesture || arcPointRevision != revision || busy
                    || plotArea.placementRevision()!=placement || coordinateRevision!=draft || !selected.equals(editing.selectedIndexOrder())) return;
            if (choice.insert() && gesture!=null) plotArea.acceptEditorThreePoint(choice.point());
            else if (!choice.insert()) {
                Coordinate cursor = plotArea.previewEditorCursorAt(choice.point());
                if (cursor != null) {
                    host.movePlotCursor(cursor);
                    instruction.setText(gesture!=null ? I18n.text("ui.f7b7ba8b06ac")
                            : I18n.text("ui.f7c814daf792"));
                }
            }
        });
        return true;
    }

    private static String arcModeLabel(EditorArc.Mode mode) {
        return switch (mode) {
            case CENTER_START_END -> I18n.text("ui.e9867c655edf");
            case START_END_CENTER -> I18n.text("ui.23c7e2902139");
            case START_END_THROUGH -> I18n.text("ui.fb51bc1053dc");
        };
    }

    private void arcInstruction(int count) {
        String[] next = switch (arcMode.getValue()) {
            case CENTER_START_END -> new String[]{I18n.text("ui.1f580bb27589"), I18n.text("ui.baa633b8c818"), I18n.text("ui.0c3ed450a39f")};
            case START_END_CENTER -> new String[]{I18n.text("ui.baa633b8c818"), I18n.text("ui.469b7098e2fc"), I18n.text("ui.b1b381c1f9f5")};
            case START_END_THROUGH -> new String[]{I18n.text("ui.baa633b8c818"), I18n.text("ui.469b7098e2fc"), I18n.text("ui.ae178cbf94cf")};
        };
        instruction.setText(I18n.text("ui.0deb00d717f3") + arcModeLabel(arcMode.getValue())
                + (count < 0 ? "" : I18n.text("ui.c591076dc5ea") + next[Math.min(count, 2)])
                + ". " + (arcMode.getValue() == EditorArc.Mode.START_END_THROUGH ? I18n.text("ui.5ed2604e1c72") : arcClockwise.isSelected() ? I18n.text("ui.913903e8f7cb") : I18n.text("ui.e49dd9df6495"))
                + I18n.text("ui.a2387e78d9d6"));
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
                    instruction.setText((polygon ? I18n.text("ui.2c7f33880993") : I18n.text("ui.31591bad64b8")) + ": " + anchorCount
                            + I18n.text("ui.f30b4909d943"));
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
                    instruction.setText((polygon ? I18n.text("ui.2c7f33880993") : I18n.text("ui.31591bad64b8")) + I18n.text("ui.33352c72e8f0"));
                    refreshGeometry();
                } catch (IllegalArgumentException invalid) {
                    instruction.setText(invalid.getMessage());
                }
            }

            @Override
            public void onCancel() {
                if (session == editing) {
                    instruction.setText(I18n.text("ui.abcf44dd4e39"));
                }
            }
        })) {
            host.log(I18n.text("ui.98249764696d"));
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
        int resolution;
        try { resolution = circle ? curveSteps() : EditorArc.DEFAULT_STEPS; } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); return; }
        plotArea.cancelPlacement(); drawingCircle = circle;
        double[] anchor = new double[2];
        var handler = new PlotAreaView.PlacementHandler() {
                    @Override
                    public void onAnchorChosen(double x, double y) {
                        anchor[0] = x;
                        anchor[1] = y;
                        if (session == editing) {
                            instruction.setText(circle ? I18n.text("ui.e60b26d3cfcb")
                                    : I18n.text("ui.cf62f342d641"));
                        }
                    }

                    @Override
                    public void onCommit(double dx, double dy) {
                        drawingCircle = false;
                        if (session != editing) {
                            return;
                        }
                        try {
                            if (circle) {
                                editing.addCircle(anchor[0], anchor[1], anchor[0] + dx, anchor[1] + dy,
                                        toolIndex,resolution);
                            } else {
                                editing.addRectangle(anchor[0], anchor[1], anchor[0] + dx, anchor[1] + dy,
                                        toolIndex);
                            }
                            instruction.setText((circle ? I18n.text("ui.1b3a8227c7e0") : I18n.text("ui.0ff1131199c8")) + I18n.text("ui.33352c72e8f0"));
                            refreshGeometry();
                        } catch (IllegalArgumentException invalid) {
                            instruction.setText(invalid.getMessage());
                        }
                    }

                    @Override
                    public void onCancel() {
                        drawingCircle = false;
                        if (session == editing) {
                            instruction.setText(I18n.text("ui.abcf44dd4e39"));
                        }
                    }
                };
        boolean started = circle ? plotArea.beginEditorCirclePlacement(resolution,handler)
                : plotArea.beginEditorTwoPointPlacement(PlotAreaView.TwoPointShape.RECTANGLE,handler);
        if (started) {
            instruction.setText(circle ? I18n.text("ui.1ca41cea335f") : I18n.text("ui.2619b9af69bf"));
        }
    }

    void startMove() {
        startMoveOrCopy(false);
    }

    void startCopy() {
        startMoveOrCopy(true);
    }

    void startText() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        textPane.setExpanded(true);
        textContent.requestFocus();
        instruction.setText(I18n.text("ui.fae6d6d8eb7a"));
    }

    void startPaint() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        paintDiameter.setText(Double.toString(session.selectedToolDiameter(0.3)));
        paintPane.setExpanded(true);
        instruction.setText(I18n.text("ui.cb9b84414676"));
    }

    private void generatePaint() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        GeometryEditSession editing = session;
        try {
            var p = new org.flatcam.cam.ncc.PaintParameters(List.of(parseTransformNumber(paintDiameter, I18n.text("ui.7cd23abc62e0"))),
                    parseTransformNumber(paintOverlap, "Overlap") / 100, parseTransformNumber(paintMargin, I18n.text("ui.c707749bff8f")),
                    paintMethod.getValue(), paintConnect.isSelected(), paintContour.isSelected(), org.flatcam.cam.ncc.NccOrder.NONE, false);
            calculate(editing.preparePaint(p, units), result -> {
                Geometry paths = new org.locationtech.jts.geom.GeometryFactory().buildGeometry(result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
                boolean started = plotArea.beginEditorFixedPreview(paths, new PlotAreaView.PlacementHandler() {
                    public void onCommit(double dx, double dy) {
                        if (session != editing) return;
                        if (editing.applyOperation(result)) { refreshGeometry(); instruction.setText(I18n.text("ui.a6b4e06b8a5e")); }
                        else instruction.setText(I18n.text("ui.4f1df7a1d5f0"));
                    }
                    public void onCancel() { if (session == editing) instruction.setText(I18n.text("ui.58e0ca44a6f2")); }
                });
                instruction.setText(started ? I18n.text("ui.ce8bed9a1fef") : I18n.text("ui.1c536e6dd07a"));
            });
        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
    }

    private void generateText() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        GeometryEditSession editing = session;
        int toolIndex = selectedToolIndex();
        try {
            var p = new TextGeometry.Parameters(textContent.getText(), textFont.getValue(),
                    parseTransformNumber(textSize, I18n.text("ui.21bed0eaf1e5")), textBold.isSelected(), textItalic.isSelected(), units);
            calculate(editing.prepareText(p, toolIndex), result -> {
                Geometry text = new org.locationtech.jts.geom.GeometryFactory().buildGeometry(
                        result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
                boolean started = plotArea.beginEditorFlashPlacement(text, new PlotAreaView.PlacementHandler() {
                    public void onCommit(double x, double y) {
                        if (session != editing) return;
                        try {
                            editing.addGeneratedGeometry(AffineTransformation.translationInstance(x, y).transform(text), toolIndex);
                            refreshGeometry(); instruction.setText(I18n.text("ui.69a760e44a81"));
                        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
                    }
                    public void onCancel() { if (session == editing) instruction.setText(I18n.text("ui.7b76c2f35cb9")); }
                });
                instruction.setText(started ? I18n.text("ui.9909a2e7e75b")
                        : I18n.text("ui.5c1569e92b19"));
            });
        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
    }

    void startEraser() {
        if (!readyForTool()) return;
        if (eraser != null) { cancelEraserFromShortcut(); return; }
        plotArea.cancelPlacement();
        EraserGesture gesture = new EraserGesture(session);
        eraser = gesture;
        eraserButton.pseudoClassStateChanged(ERASER_ACTIVE, true);
        try {
            calculate(session.prepareEraserMask(), result -> {
                if (eraser != gesture) return;
                gesture.mask = result.resultParts().getFirst().geometry();
                armEraser(gesture);
            }, () -> stopEraser(gesture));
        } catch (IllegalArgumentException error) {
            stopEraser(gesture); instruction.setText(error.getMessage());
        }
    }

    boolean isEraserActive() { return eraser != null; }

    /** Also handles Escape while the worker runs, when there is no ordinary placement to cancel. */
    boolean cancelEraserFromShortcut() {
        if (eraser == null) return false;
        EraserGesture gesture = eraser;
        stopEraser(gesture);
        plotArea.cancelPlacement();
        refreshSelection();
        instruction.setText(busy ? I18n.text("ui.e22ebd28fea1")
                : I18n.text("ui.5a952a2e07de"));
        return true;
    }

    private void stopEraser(EraserGesture gesture) {
        if (eraser == gesture) {
            eraser = null;
            eraserButton.pseudoClassStateChanged(ERASER_ACTIVE, false);
        }
    }

    private void armEraser(EraserGesture gesture) {
        if (eraser != gesture || session != gesture.editing) return;
        boolean started = plotArea.beginEditorPlacement(List.of(gesture.mask), false, gesture.anchor,
                new PlotAreaView.PlacementHandler() {
                    public void onAnchorChosen(double x, double y) {
                        gesture.anchor = new Coordinate(x, y);
                        instruction.setText(I18n.text("ui.b5a8a6cd95b1"));
                    }
                    public void onCommit(double dx, double dy) {
                        if (eraser != gesture || session != gesture.editing) return;
                        try {
                            calculate(session.prepareErase(gesture.mask, dx, dy), erased -> {
                                if (eraser != gesture) return;
                                boolean changed = session.applyOperation(erased);
                                refreshGeometry();
                                armEraser(gesture);
                                instruction.setText((changed ? I18n.text("ui.f9c948c711ad") : I18n.text("ui.15196a3dfd68"))
                                        + I18n.text("ui.ef8884bcefd0"));
                            }, () -> stopEraser(gesture));
                        } catch (IllegalArgumentException error) {
                            stopEraser(gesture); instruction.setText(error.getMessage());
                        }
                    }
                    public void onCancel() {
                        if (eraser == gesture && !gesture.suspending) {
                            stopEraser(gesture);
                            refreshSelection();
                            instruction.setText(I18n.text("ui.5a952a2e07de"));
                        }
                    }
                });
        if (!started) stopEraser(gesture);
        instruction.setText(!started ? I18n.text("ui.643fd686e3c4")
                : gesture.anchor == null ? I18n.text("ui.6862113a1357")
                : I18n.text("ui.154a08a49429"));
    }

    private void startMoveOrCopy(boolean copy) {
        if (!readyForTool() || session.selectedCount() == 0) {
            if (isActive()) {
                instruction.setText(I18n.text("ui.60a9ab0acecc"));
            }
            return;
        }
        GeometryEditSession editing = session;
        Set<Integer> selectedAtStart = editing.selectedIndices();
        boolean simplified = editing.selectedCount() > exactHighlightLimit;
        Geometry preview = simplified ? editing.selectedBounds() : editing.selectedGeometry();
        boolean started = plotArea.beginEditorPlacement(List.of(preview), strokeOnly || simplified,
                new PlotAreaView.PlacementHandler() {
                    @Override
                    public void onAnchorChosen() {
                        if (session == editing) {
                            instruction.setText(I18n.text("ui.910a38c70863"));
                        }
                    }

                    @Override
                    public void onCommit(double dx, double dy) {
                        if (session != editing || !editing.selectedIndices().equals(selectedAtStart)) {
                            return;
                        }
                        if (copy ? editing.copySelected(dx, dy) : editing.moveSelected(dx, dy)) {
                            refreshGeometry();
                            instruction.setText(copy ? I18n.text("ui.d66b4df0eb8a") : I18n.text("ui.76deecdeadc1"));
                        } else {
                            instruction.setText(I18n.text("ui.7551fd5c7a27"));
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (session == editing) {
                            instruction.setText(I18n.text("ui.441792596876"));
                        }
                    }
                });
        if (started) {
            instruction.setText(I18n.text("ui.5d04b11e19e7"));
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
            host.log(I18n.text("ui.e141a8c647fd"));
            return false;
        }
        host.log(I18n.text("ui.9d3d6eba6d09"));
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
        return changeHistory(false);
    }

    private boolean redo() {
        if (!readyForTool()) {
            return false;
        }
        return changeHistory(true);
    }

    private boolean changeHistory(boolean redo) {
        EraserGesture gesture = eraser;
        if (gesture != null) gesture.suspending = true;
        plotArea.cancelPlacement();
        if (gesture != null) gesture.suspending = false;
        boolean changed = redo ? session.redo() : session.undo();
        if (changed) refreshGeometry();
        if (gesture != null) armEraser(gesture);
        return changed;
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
            instruction.setText(I18n.text("ui.395df8e39782"));
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
        calculate(request, result -> {
            if (editing.applyOperation(result)) {
                refreshGeometry();
                instruction.setText(I18n.text("ui.774047688c15"));
            } else {
                refreshSelection();
                instruction.setText(I18n.text("ui.cbd9e7d0d8e5"));
            }
        });
    }

    private void calculate(GeometryEditSession.OperationRequest request, Consumer<GeometryEditSession.OperationResult> success) {
        calculate(request, success, () -> { });
    }

    private void calculate(GeometryEditSession.OperationRequest request, Consumer<GeometryEditSession.OperationResult> success,
                           Runnable failure) {
        GeometryEditSession editing = session;
        busy = true;
        refreshSelection();
        instruction.setText(I18n.text("ui.94394dd7b8e2") + (request.operation()==GeometryEditSession.Operation.AFFINE ? I18n.text("ui.a65b5a1027b0") : request.operation()==GeometryEditSession.Operation.ROTATE ? I18n.text("ui.9f98b02b4da2")
                : request.operation().name().toLowerCase(java.util.Locale.ROOT)) + "...");
        boolean accepted = host.runOperation(request, result -> {
            if (session != editing) {
                return;
            }
            busy = false;
            refreshSelection();
            success.accept(result);
        }, error -> {
            if (session == editing) {
                busy = false;
                failure.run();
                refreshSelection();
                instruction.setText(error);
            }
        });
        if (!accepted) {
            busy = false;
            failure.run();
            refreshSelection();
            instruction.setText(I18n.text("ui.9d368ea65dff"));
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
            instruction.setText(edges + I18n.text("ui.0dbe5f0ca2f0"));
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
            host.log(I18n.text("ui.c545d29fc0ff") + item.getValue() + ".");
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
        host.log(I18n.text("ui.2412fc58777d"));
    }

    private void refreshGeometry() {
        ++coordinateRevision;
        syncingTable = true;
        shapeTable.getItems().setAll(session.shapeRows());
        syncingTable = false;
        plotArea.updateLayerGeometry(SHAPES_LAYER, session.resultGeometry());
        refreshSelection();
    }

    private void refreshSelection() {
        editorButtons.forEach(button -> button.setDisable(busy));
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
        boolean simplified = session.selectedCount() > exactHighlightLimit;
        plotArea.setEditorHighlight(simplified ? session.selectedBounds() : session.selectedGeometry(),
                strokeOnly || simplified, selectedColor);
        status.setText(session.shapeCount() + I18n.text("ui.6d6772b2b2a3") + session.selectedCount() + I18n.text("ui.de5b2b723fc7")
                + (simplified ? I18n.text("ui.190906d4c9b0") : "")
                + (session.isDirty() ? I18n.text("ui.d56199d7cd0c") : I18n.text("ui.c830b9666887")));
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
        transformButton.setDisable(session.selectedCount() == 0);
        eraserButton.setDisable(session.selectedCount() == 0 && eraser == null);
        paintButton.setDisable(session.selectedCount() == 0);
        paintPane.setDisable(busy);
        textPane.setDisable(busy);
        arcPane.setDisable(busy);
        transformsPane.setDisable(busy);
        rotationConfirm.setDisable(busy || !rotationReady);
        rotationCancel.setDisable(rotationGesture==null);
        if (toolChoice != null) toolChoice.setDisable(busy);
        if (busy) {
            editorButtons.forEach(button -> button.setDisable(true));
        }
        shapeTable.setDisable(busy);
        exitButton.setDisable(busy);
        discardButton.setDisable(busy);
    }

    private void end() {
        plotArea.cancelPlacement();
        eraser = null;
        arcGesture = null;
        drawingCircle = false;
        rotationGesture=null; rotationReady=false; plotArea.setEditorReference(null);
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
        transformButton = null;
        textButton = null; eraserButton = null; textPane = null; textContent = null;
        paintButton = null; paintPane = null; paintDiameter = null; paintOverlap = null; paintMargin = null;
        paintMethod = null; paintConnect = null; paintContour = null;
        textFont = null; textSize = null; textBold = null; textItalic = null; units = null;
        arcPane = null; arcMode = null; arcClockwise = null;
        curveSteps = null;
        transformKind=null; scaleX=null; scaleY=null; skewX=null; skewY=null; offsetX=null; offsetY=null; rotationAngle=null; rotationX=null; rotationY=null; rotationReference=null; rotationConfirm=null; rotationCancel=null; transformsPane=null;
        bufferDistance = null;
        bufferMode = null;
        editorButtons = null;
        busy = false;
    }
}
