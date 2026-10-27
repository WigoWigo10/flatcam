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
        ROTATE("Rotação"), SCALE("Escala X/Y"), MIRROR_X("Espelhar X (esquerda/direita)"),
        MIRROR_Y("Espelhar Y (cima/baixo)"), SKEW("Inclinação X/Y"), OFFSET("Deslocamento X/Y");
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
        SELECTION("Centro da seleção"), ORIGIN("Origem (0, 0)"), POINT("Ponto X/Y"),
        LOWER_LEFT("Canto inferior esquerdo"), LOWER_RIGHT("Canto inferior direito"),
        UPPER_RIGHT("Canto superior direito"), UPPER_LEFT("Canto superior esquerdo");
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
            host.log("Ja existe uma edicao de Geometry em andamento.");
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
        instruction = new Label("Selecione uma linha na tabela ou uma forma no desenho.");
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
        Button selectButton = new Button("Selecionar");
        Button pathButton = new Button("Caminho");
        Button polygonButton = new Button("Poligono");
        Button rectangleButton = new Button("Retangulo");
        Button circleButton = new Button("Circulo");
        Button arcButton = new Button("Arco");
        arcButton.setId("geometry-arc");
        deleteButton = new Button("Excluir selecionadas");
        moveButton = new Button("Mover selecionadas");
        copyButton = new Button("Copiar selecionadas");
        undoButton = new Button("Desfazer");
        redoButton = new Button("Refazer");
        applyButton = new Button("Aplicar ao objeto");
        applyButton.getStyleClass().add("primary-action");
        unionButton = new Button("Unir selecionadas");
        intersectionButton = new Button("Intersecao selecionadas");
        subtractButton = new Button("Subtrair da primeira selecionada");
        cutPathButton = new Button("Cortar Caminho");
        bufferButton = new Button("Criar buffer arredondado");
        explodeButton = new Button("Explodir poligonos");
        transformButton = new Button("Transformacoes");
        textButton = new Button("Texto"); textButton.setId("geometry-text");
        eraserButton = new Button("Borracha"); eraserButton.setId("geometry-eraser");
        paintButton = new Button("Paint Shape"); paintButton.setId("geometry-paint");
        bufferDistance = new TextField();
        bufferDistance.setPromptText("Distancia positiva (unidade do projeto)");
        bufferMode = new ComboBox<>();
        bufferMode.getItems().addAll("Completo", "Interior", "Exterior");
        bufferMode.getSelectionModel().selectFirst();
        bufferMode.setMaxWidth(Double.MAX_VALUE);
        Button cancelButton = new Button("Cancelar edicao");
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
        ToolDescriptions.apply(bufferMode, "Modo do Buffer", GeometryEditorDescriptions.of("buffer").text());
        ToolDescriptions.apply(bufferDistance, "Distância do Buffer",
                "Distância positiva e finita na unidade do objeto. Interior contrai; Exterior expande; Completo cria uma faixa ao redor do contorno.\n\nUnidades: mm ou in. Escolha o modo em vez de usar uma distância negativa.");
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
        arcClockwise = new CheckBox("Sentido horário (CW)"); arcClockwise.setSelected(true);
        arcClockwise.setId("geometry-arc-clockwise");
        arcClockwise.disableProperty().bind(arcMode.valueProperty().isEqualTo(EditorArc.Mode.START_END_THROUGH));
        ToolDescriptions.apply(arcMode, "Ordem dos pontos do arco", GeometryEditorDescriptions.of("arc").text());
        ToolDescriptions.apply(arcClockwise, "Sentido do arco",
                "Marcado: horário (CW); desmarcado: anti-horário (CCW). D alterna durante o desenho. "
                + "Em 132, o ponto no arco determina o sentido automaticamente.\n\nMilling Type Climb inverte a ordem final dos vértices, como no Python; confira o sentido ao gerar CNC.");
        arcMode.valueProperty().addListener((obs, old, value) -> {
            if (arcGesture != null) plotArea.resetEditorThreePointPlacement();
        });
        arcClockwise.selectedProperty().addListener((obs, old, value) -> {
            if (arcGesture != null) { plotArea.refreshEditorThreePointPreview(); arcInstruction(-1); }
        });
        curveSteps = new TextField(ToolDefaults.text("geometry.editorcirclesteps",true));
        curveSteps.setId("geometry-curve-steps"); curveSteps.setPrefColumnCount(5); curveSteps.setMinWidth(0);
        ToolDescriptions.apply(curveSteps,"Segmentos por círculo",
                ToolDefaultsDescriptions.text(ToolDefaults.setting("geometry.editorcirclesteps"),true));
        Button saveCurveSteps = new Button("Salvar resolução padrão"); saveCurveSteps.setId("geometry-curve-steps-save");
        ToolDescriptions.apply(saveCurveSteps,"Salvar resolução padrão",
                "Salva apenas a resolução atual para os próximos editores, em mm e in. Não altera formas existentes; também pode ser configurada em Preferências > Padrões das ferramentas > Editor Geometry.");
        saveCurveSteps.setOnAction(event -> {
            try { ToolDefaults.set("geometry.editorcirclesteps",true,Integer.toString(curveSteps())); instruction.setText("Resolução padrão salva; formas existentes foram mantidas."); }
            catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
        });
        curveSteps.textProperty().addListener((obs, old, value) -> {
            if (arcGesture != null) {
                plotArea.refreshEditorThreePointPreview();
                try { curveSteps(); arcInstruction(-1); } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
            } else if (drawingCircle) { plotArea.cancelPlacement(); instruction.setText("Resolução alterada; inicie o círculo novamente."); }
        });
        Label arcHelp = new Label("M: modo • D: sentido • J: coordenadas • Backspace: voltar um ponto. O terceiro clique conclui; Esc/botão direito cancela.");
        arcHelp.setWrapText(true);
        arcPane = new TitledPane("Arcos e curvas", new VBox(6, arcMode, arcClockwise,
                new HBox(8,new Label("Segmentos/volta:"),curveSteps),saveCurveSteps,arcHelp));
        arcPane.setExpanded(false); panel.getChildren().add(arcPane);
        textContent = new TextArea(); textContent.setId("geometry-text-content");
        textContent.setPromptText("Texto para converter em geometria"); textContent.setPrefRowCount(3);
        textContent.setWrapText(true);
        ToolDescriptions.apply(textContent, "Conteúdo do texto",
                "Texto simples, com até 512 caracteres; Enter acrescenta outra linha abaixo da primeira. O estilo vale para todo o conteúdo.\n\nAo inserir, as letras viram áreas editáveis: não será possível reabrir o resultado como texto. Editar este campo cancela uma prévia anterior, sem apagar formas já inseridas.");
        // Logical fonts work before enumeration; opening the editor must not scan the font directory.
        textFont = new ComboBox<>(FXCollections.observableArrayList("SansSerif", "Serif", "Monospaced"));
        textFont.setId("geometry-text-font"); textFont.setMaxWidth(Double.MAX_VALUE);
        textFont.setValue("SansSerif");
        ToolDescriptions.apply(textFont, "Fonte dos contornos",
                "Escolha uma fonte instalada. Fontes ausentes ou sem os caracteres necessários são recusadas.\n\nA Geometry salva contém os contornos vetoriais e não depende mais da fonte; métricas e curvas podem diferir das geradas pelo Python.");
        textSize = new TextField("10"); textSize.setId("geometry-text-size"); textSize.setPrefColumnCount(5);
        ToolDescriptions.apply(textSize, "Tamanho do texto",
                "Escala de tamanho do ParseFont Python, de 0.1 a 1000; não é a altura exata da letra.\n\nUnidades: os contornos são gerados em " + units + ". Confira as dimensões na prévia antes de usinar.");
        textBold = new CheckBox("Negrito"); textItalic = new CheckBox("Italico");
        for (var property : List.of(textContent.textProperty(), textSize.textProperty(), textFont.valueProperty()))
            property.addListener((obs, old, value) -> {
                if (!busy && !Boolean.TRUE.equals(textFont.getProperties().get("fonts-updating")))
                    plotArea.cancelPlacement();
            });
        textBold.selectedProperty().addListener((obs, old, value) -> { if (!busy) plotArea.cancelPlacement(); });
        textItalic.selectedProperty().addListener((obs, old, value) -> { if (!busy) plotArea.cancelPlacement(); });
        Button placeText = new Button("Gerar e posicionar texto"); placeText.setId("geometry-text-place");
        placeText.getStyleClass().add("primary-action"); placeText.setMaxWidth(Double.MAX_VALUE);
        ToolDescriptions.apply(placeText, "Prévia e inserção do texto",
                "Calcula os contornos em segundo plano e mostra uma prévia. A primeira linha usa o ponto clicado como linha de base; linhas seguintes descem.\n\nClique no plot para confirmar; Esc/botão direito cancela sem perder o formulário. Ctrl+Z desfaz a inserção inteira. Salvar e sair do editor aplica o rascunho ao objeto.");
        placeText.setOnAction(event -> generateText());
        Label textHelp = new Label("Texto vira areas editaveis com vazios das letras preservados. "
                + "A origem e a linha de base da primeira linha. Clique para inserir; Esc cancela.");
        textHelp.setWrapText(true);
        textPane = new TitledPane("Texto vetorial", new VBox(8, new Label("Fonte:"), textFont,
                new HBox(8, new Label("Tamanho:"), textSize), new HBox(8, textBold, textItalic),
                textContent, textHelp, placeText));
        textPane.setExpanded(false); panel.getChildren().add(textPane);
        GeometryEditSession fontSession = session;
        ComboBox<String> fontPicker = textFont;
        textPane.expandedProperty().addListener((obs, was, expanded) -> {
            if (!expanded || Boolean.TRUE.equals(fontPicker.getProperties().get("fonts-loaded"))
                    || Boolean.TRUE.equals(fontPicker.getProperties().get("fonts-loading"))) return;
            fontPicker.getProperties().put("fonts-loading", true);
            fontPicker.setDisable(true);
            fontPicker.setPromptText("Carregando fontes...");
            fontCatalog.load().whenComplete((fonts, failure) -> javafx.application.Platform.runLater(() -> {
                // Closing/reopening an editor must not let an old completion update its new controls.
                if (session != fontSession || textFont != fontPicker) return;
                fontPicker.getProperties().remove("fonts-loading");
                fontPicker.setDisable(false);
                if (failure != null) {
                    fontPicker.setPromptText("Reabra esta seção para tentar novamente");
                    host.log("Não foi possível listar as fontes. As fontes lógicas continuam disponíveis.");
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
        Button paintPreview = new Button("Calcular previa"); paintPreview.setId("geometry-paint-preview");
        Button paintConfirm = new Button("Confirmar Paint"); paintConfirm.setId("geometry-paint-confirm");
        Button paintCancel = new Button("Cancelar previa");
        paintPreview.setOnAction(event -> generatePaint());
        paintConfirm.setOnAction(event -> { if (rotationGesture == null) plotArea.confirmEditorFixedPreview(); });
        paintCancel.setOnAction(event -> plotArea.cancelPlacement());
        paintConfirm.disableProperty().bind(paintPreview.disableProperty());
        for (javafx.beans.value.ObservableValue<?> property : List.<javafx.beans.value.ObservableValue<?>>of(paintDiameter.textProperty(), paintOverlap.textProperty(), paintMargin.textProperty(), paintMethod.valueProperty()))
            property.addListener((obs, was, value) -> { if (!busy) plotArea.cancelPlacement(); });
        paintConnect.selectedProperty().addListener((obs, was, value) -> { if (!busy) plotArea.cancelPlacement(); });
        paintContour.selectedProperty().addListener((obs, was, value) -> { if (!busy) plotArea.cancelPlacement(); });
        ToolDescriptions.apply(paintDiameter, "Diametro do Paint", "Diametro positivo na unidade do objeto. Em Geometry com ferramentas, deve coincidir com a ferramenta das formas selecionadas; nao muda diametros existentes. Sem associacao, confira o mesmo diametro ao gerar CNC.");
        ToolDescriptions.apply(paintMargin, "Margem do Paint", "Positiva contrai a area; negativa expande.\n\nAtenção: margem negativa pode gerar cortes fora da forma original. Unidades: mm ou in.");
        ToolDescriptions.apply(paintPreview, "Previa do Paint", GeometryEditorDescriptions.of("paint").text());
        paintPane = new TitledPane("Paint Shape", new VBox(8,
                new HBox(8, new Label("Tool Dia:"), paintDiameter), new HBox(8, new Label("Overlap (%):"), paintOverlap),
                new HBox(8, new Label("Margem:"), paintMargin), new HBox(8, new Label("Method:"), paintMethod),
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
            ToolDescriptions.apply(f,"Pivô da transformação","Coordenada absoluta do pivô em " + units + ". Aceita ponto ou vírgula decimal. Alterar o valor cancela uma prévia anterior; não move as formas antes de confirmar.");
        }
        transformKind=new ComboBox<>(FXCollections.observableArrayList(TransformKind.values()));
        transformKind.setId("geometry-transform-kind"); transformKind.setValue(TransformKind.ROTATE);
        transformKind.setMaxWidth(Double.MAX_VALUE); transformKind.setMinWidth(0);
        scaleX=transformField("scale-x","1"); scaleY=transformField("scale-y","1");
        skewX=transformField("skew-x","0"); skewY=transformField("skew-y","0");
        offsetX=transformField("offset-x","0"); offsetY=transformField("offset-y","0");
        Button rotate = new Button("Pré-visualizar transformação"); rotate.setId("geometry-rotation-preview");
        Button dragRotate = new Button("Girar por arrasto"); dragRotate.setId("geometry-rotation-drag");
        Button pickPivot = new Button("Escolher pivô no plot"); pickPivot.setId("geometry-rotation-pick");
        rotationConfirm = new Button("Confirmar transformação"); rotationConfirm.setId("geometry-rotation-confirm");
        rotationCancel = new Button("Cancelar prévia"); rotationCancel.setId("geometry-rotation-cancel");
        ToolDescriptions.apply(rotationAngle,"Ângulo da rotação",GeometryEditorDescriptions.of("rotate").text());
        ToolDescriptions.apply(pickPivot,"Pivô no desenho","Escolha a transformação e os valores, clique neste botão e depois no pivô no Plot Area. O Snap da barra inferior é respeitado. A prévia é calculada após o clique; Esc/botão direito cancela sem mudar o rascunho.");
        rotate.setOnAction(event -> previewTransform());
        dragRotate.setOnAction(event -> startAngularRotation());
        ToolDescriptions.apply(dragRotate,"Rotação por arrasto","Usa o pivô escolhido. Pressione longe do pivô, arraste ao redor e solte para calcular a prévia exata. Durante o arrasto, somente a caixa gira para manter a fluidez; o ângulo aparece na instrução. O Snap vale para os pontos. Confira e confirme a transformação; soltar não altera o rascunho. Esc/botão direito cancela.");
        pickPivot.setOnAction(event -> pickRotationPivot());
        rotationConfirm.setOnAction(event -> { if (rotationReady) plotArea.confirmEditorFixedPreview(); });
        rotationCancel.setOnAction(event -> cancelRotationFromShortcut());
        for (var property : List.<javafx.beans.value.ObservableValue<?>>of(rotationAngle.textProperty(),rotationX.textProperty(),rotationY.textProperty(),rotationReference.valueProperty(),
                transformKind.valueProperty(),scaleX.textProperty(),scaleY.textProperty(),skewX.textProperty(),skewY.textProperty(),offsetX.textProperty(),offsetY.textProperty()))
            property.addListener((obs, old, value) -> { if (rotationGesture != null) cancelRotationFromShortcut(); });
        for (Button button : List.of(rotate, pickPivot, dragRotate)) button.disableProperty().bind(transformButton.disableProperty());
        transformRow(dragRotate,TransformKind.ROTATE);
        var angleRow=new HBox(8,new Label("Graus (+horário):"),rotationAngle);
        var scaleRow=new HBox(8,new Label("Fator X:"),scaleX,new Label("Y:"),scaleY);
        var skewRow=new HBox(8,new Label("Graus X:"),skewX,new Label("Y:"),skewY);
        var offsetRow=new HBox(8,new Label("Desloc. X:"),offsetX,new Label("Y:"),offsetY);
        transformRow(angleRow,TransformKind.ROTATE); transformRow(scaleRow,TransformKind.SCALE);
        transformRow(skewRow,TransformKind.SKEW); transformRow(offsetRow,TransformKind.OFFSET);
        ToolDescriptions.apply(transformKind,"Transformação","Escolha a operação e confira a prévia antes de confirmar. Escala e espelhos usam o pivô escolhido; inclinação aplica X/Y simultaneamente. Deslocamento soma X/Y nas unidades do objeto, independentemente do pivô. Ctrl+Z desfaz a operação inteira.");
        ToolDescriptions.apply(rotationReference,"Referência da transformação","Origem, centro/canto da caixa da seleção ou ponto absoluto. Centro significa centro da caixa, não centro de massa. Compartilhada por rotação, escala, espelho e inclinação; não muda o deslocamento X/Y.");
        ToolDescriptions.apply(rotate,"Prévia da transformação","Calcula fora da interface, sem alterar o rascunho. Confira o contorno e o pivô no Plot Area; confirme pelo botão ou clique. Esc/botão direito cancela. Valores e seleção alterados invalidam a prévia.");
        ToolDescriptions.apply(rotationConfirm,"Confirmar transformação","Aplica exatamente a prévia, mantendo IDs, ferramentas e seleção. Ctrl+Z desfaz e Ctrl+Y refaz em uma etapa. Salvar e sair publica o rascunho no objeto.");
        ToolDescriptions.apply(rotationCancel,"Cancelar prévia","Descarta a prévia sem alterar as formas. Durante o cálculo, Esc invalida o resultado pendente; o botão Cancelar da barra de progresso interrompe o trabalho quando possível.");
        var pivotRow=new HBox(8,new Label("Pivô X:"),rotationX,new Label("Y:"),rotationY);
        for(Node node:List.of(rotationReference,pivotRow,pickPivot)) {
            node.visibleProperty().bind(transformKind.valueProperty().isNotEqualTo(TransformKind.OFFSET));
            node.managedProperty().bind(node.visibleProperty());
        }
        VBox transformControls = new VBox(8,transformKind,angleRow,scaleRow,skewRow,offsetRow,rotationReference,
                pivotRow,pickPivot,dragRotate,rotate,rotationConfirm,rotationCancel);
        transformsPane = new TitledPane("Transformacoes das selecionadas", transformControls);
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
        Tooltip millingHelp = new Tooltip("Sentido de fresagem das formas novas (como o Milling Type do Editor Geometry "
                + "do Python). Climb inverte o sentido em que a forma foi desenhada; Conventional mantem. "
                + "Nao altera formas ja existentes.");
        climbRadio.setTooltip(millingHelp);
        conventionalRadio.setTooltip(millingHelp);
        panel.getChildren().add(new HBox(10, new Label("Milling Type (novas formas):"), climbRadio, conventionalRadio));
        if (tools.size() > 1) {
            toolChoice = new ComboBox<>();
            for (int i = 0; i < tools.size(); i++) {
                toolChoice.getItems().add(String.format(java.util.Locale.ROOT,
                        "Ferramenta %d - Ø %.4f", i + 1, tools.get(i).toolDiameter()));
            }
            toolChoice.getSelectionModel().selectFirst();
            toolChoice.setMaxWidth(Double.MAX_VALUE);
            ToolDescriptions.apply(toolChoice, "Ferramenta das novas formas",
                    "Associa os novos desenhos e o Texto a esta ferramenta da Geometry. Não muda o diâmetro nem transfere formas já existentes para outra ferramenta. Mover/copiar preservam a associação original; Borracha pode recortar formas de todas as ferramentas. Trocar aqui cancela a prévia atual.");
            toolChoice.valueProperty().addListener((obs, old, value) -> plotArea.cancelPlacement());
        }
        exitButton = new Button("Salvar e sair do editor");
        exitButton.getStyleClass().add("primary-action");
        exitButton.setMaxWidth(Double.MAX_VALUE);
        exitButton.setOnAction(event -> apply());
        discardButton = new Button("Descartar alteracoes");
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
        host.openToolPanel("Editor Geometry", panel);
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
            throw new IllegalArgumentException(name + " deve ser numerico.");
        }
    }

    void openTransforms() {
        if (readyForTool()) transformsPane.setExpanded(true);
    }

    private TextField transformField(String id,String initial) {
        var field=new TextField(initial); field.setId("geometry-transform-"+id);
        field.setPrefColumnCount(5); field.setMinWidth(0);
        String help=id.startsWith("scale") ? "Fator sem unidade no eixo indicado. 1 conserva o tamanho; valores negativos também espelham. Zero é recusado."
                : id.startsWith("skew") ? "Inclinação em graus no eixo indicado, entre -90 e 90 sem os extremos. X/Y são aplicados simultaneamente em torno do pivô. Combinações que achatam a forma são recusadas."
                : "Deslocamento no eixo indicado em " + units + ". Positivo aumenta a coordenada; negativo diminui. O pivô não altera o deslocamento.";
        ToolDescriptions.apply(field,"Parâmetro da transformação",help+" Alterar o valor cancela a prévia anterior; só confirma após nova prévia.");
        return field;
    }

    private void transformRow(Node row,TransformKind kind) {
        row.visibleProperty().bind(transformKind.valueProperty().isEqualTo(kind));
        row.managedProperty().bind(row.visibleProperty());
    }

    private Coordinate rotationPivot() {
        if (rotationReference.getValue() == RotationReference.ORIGIN) return new Coordinate(0,0);
        if (rotationReference.getValue() == RotationReference.POINT)
            return new Coordinate(parseTransformNumber(rotationX,"Pivô X"),parseTransformNumber(rotationY,"Pivô Y"));
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
            public void onCancel() { if (rotationGesture==gesture) stopRotation(gesture,"Escolha do pivô cancelada."); }
        });
        if (!started) stopRotation(gesture,"Não foi possível escolher o pivô.");
        else instruction.setText("Clique no pivô da transformação; depois confira a prévia. Esc/botão direito cancela.");
        refreshSelection();
    }

    private void previewTransform() {
        if (!readyForTool()) return;
        if (session.selectedCount()==0) { instruction.setText("Selecione formas para transformar."); return; }
        plotArea.cancelPlacement();
        GeometryEditSession editing=session;
        try {
            Coordinate pivot=transformKind.getValue()==TransformKind.OFFSET
                    ? session.selectedBounds().getEnvelopeInternal().centre() : rotationPivot();
            var request=switch(transformKind.getValue()) {
                case ROTATE -> editing.prepareRotation(parseTransformNumber(rotationAngle,"Ângulo"),pivot);
                case SCALE -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.scale(
                        parseTransformNumber(scaleX,"Fator X"),parseTransformNumber(scaleY,"Fator Y")),pivot);
                case MIRROR_X -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.scale(-1,1),pivot);
                case MIRROR_Y -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.scale(1,-1),pivot);
                case SKEW -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.skew(
                        parseTransformNumber(skewX,"Inclinação X"),parseTransformNumber(skewY,"Inclinação Y")),pivot);
                case OFFSET -> editing.prepareTransform(org.flatcam.cam.geometry.EditorTransform.offset(
                        parseTransformNumber(offsetX,"Deslocamento X"),parseTransformNumber(offsetY,"Deslocamento Y")),new Coordinate(0,0));
            };
            Object gesture=new Object(); rotationGesture=gesture; rotationReady=false;
            calculate(request,result -> {
                if (rotationGesture!=gesture) return;
                if (result.resultParts().isEmpty()) { stopRotation(gesture,"Nenhuma coordenada foi alterada pela transformação."); return; }
                var factory=result.resultParts().getFirst().geometry().getFactory();
                // Use the indexed overlay for dense shapes; only the small pivot marker is a placement preview.
                Geometry preview=factory.buildGeometry(result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
                boolean started=plotArea.beginEditorFixedPreview(pivotCross(pivot),new PlotAreaView.PlacementHandler() {
                    public void onCommit(double dx,double dy) {
                        if (session!=editing || rotationGesture!=gesture) return;
                        rotationGesture=null; rotationReady=false; plotArea.setEditorReference(null);
                        boolean changed=editing.applyTransform(result); refreshGeometry();
                        instruction.setText(changed ? "Transformação aplicada ao rascunho. Ctrl+Z desfaz; Ctrl+Y refaz."
                                : "A seleção ou o rascunho mudou; prévia descartada.");
                    }
                    public void onCancel() { if (rotationGesture==gesture) stopRotation(gesture,"Prévia da transformação descartada."); }
                });
                if (!started) { stopRotation(gesture,"Não foi possível mostrar a prévia da rotação."); return; }
                plotArea.setEditorReference(preview); rotationReady=true; refreshSelection();
                instruction.setText("Prévia da transformação: confira o novo contorno. Confirme pelo botão ou clique no plot; Esc/botão direito cancela.");
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
                            "Rotação: %.3f° (+horário). Solte para calcular a prévia exata; Esc cancela.",degrees));
                }
                public void onRelease(double degrees) {
                    if (session!=editing || rotationGesture!=gesture || !selected.equals(editing.selectedIndexOrder())) return;
                    rotationGesture = null;
                    rotationAngle.setText(Double.toString(degrees));
                    previewTransform();
                }
                public void onCancel() { stopRotation(gesture,"Arrasto cancelado; nenhuma forma alterada."); }
            });
            if (!started) stopRotation(gesture,"Não foi possível iniciar a rotação por arrasto.");
            else {
                refreshSelection();
                instruction.setText("Pressione longe do pivô, arraste ao redor e solte. A caixa acompanha o ângulo; depois confira a prévia exata e confirme. Esc/botão direito cancela.");
            }
        } catch (IllegalArgumentException invalid) { instruction.setText(invalid.getMessage()); }
    }

    boolean cancelRotationFromShortcut() {
        if (rotationGesture==null) return false;
        Object gesture=rotationGesture;
        stopRotation(gesture,busy ? "Transformação cancelada; o resultado pendente será descartado." : "Prévia da transformação descartada.");
        plotArea.cancelPlacement(); return true;
    }

    private void stopRotation(Object gesture,String message) {
        if (rotationGesture!=gesture) return;
        rotationGesture=null; rotationReady=false; plotArea.setEditorReference(null);
        refreshSelection(); if (message!=null) instruction.setText(message);
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
                    instruction.setText("Arco adicionado. Ctrl+Z desfaz; Ctrl+Y refaz.");
                    return true;
                } catch (IllegalArgumentException invalid) {
                    instruction.setText(invalid.getMessage() + " Escolha outro terceiro ponto ou use Backspace.");
                    return false;
                }
            }
            public void onCancel() {
                if (arcGesture == gesture) {
                    arcGesture = null; instruction.setText("Arco cancelado; nenhuma forma inserida.");
                }
            }
        })) {
            arcGesture = null; instruction.setText("Não foi possível iniciar o arco.");
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
                else instruction.setText("Modo 132: o ponto no arco determina o sentido automaticamente.");
                yield true;
            }
            case BACK_SPACE -> { plotArea.backtrackEditorThreePointPlacement(); yield true; }
            case ENTER -> { instruction.setText("Escolha os três pontos no plot; o terceiro clique conclui o arco."); yield true; }
            default -> false;
        };
    }

    private int curveSteps() {
        try { int steps = Integer.parseInt(curveSteps.getText().trim()); EditorArc.validateSteps(steps); return steps; }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Resolução deve ser um inteiro entre 4 e 4096 segmentos por círculo."); }
    }

    boolean requestCoordinates() {
        if (!isActive()) return false;
        if (busy) { host.log("Aguarde o cálculo antes de posicionar o cursor."); return true; }
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
                    instruction.setText(gesture!=null ? "Cursor posicionado; clique para confirmar o próximo ponto ou use J > Adicionar ponto."
                            : "Cursor posicionado; o gesto e seus pontos foram preservados. Continue pelo Plot Area.");
                }
            }
        });
        return true;
    }

    private static String arcModeLabel(EditorArc.Mode mode) {
        return switch (mode) {
            case CENTER_START_END -> "Centro → Início → Fim (C12)";
            case START_END_CENTER -> "Início → Fim → Centro (12C)";
            case START_END_THROUGH -> "Início → Fim → Ponto no arco (132)";
        };
    }

    private void arcInstruction(int count) {
        String[] next = switch (arcMode.getValue()) {
            case CENTER_START_END -> new String[]{"centro", "início", "direção do fim"};
            case START_END_CENTER -> new String[]{"início", "fim", "referência do centro"};
            case START_END_THROUGH -> new String[]{"início", "fim", "ponto no arco"};
        };
        instruction.setText("Arco — " + arcModeLabel(arcMode.getValue())
                + (count < 0 ? "" : ": clique no " + next[Math.min(count, 2)])
                + ". " + (arcMode.getValue() == EditorArc.Mode.START_END_THROUGH ? "Sentido automático" : arcClockwise.isSelected() ? "Horário (CW)" : "Anti-horário (CCW)")
                + "; M muda o modo, D o sentido, J abre coordenadas; Esc cancela.");
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
                            instruction.setText(circle ? "Clique no perimetro do circulo."
                                    : "Clique no canto oposto do retangulo.");
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
                            instruction.setText((circle ? "Circulo" : "Retangulo") + " adicionado.");
                            refreshGeometry();
                        } catch (IllegalArgumentException invalid) {
                            instruction.setText(invalid.getMessage());
                        }
                    }

                    @Override
                    public void onCancel() {
                        drawingCircle = false;
                        if (session == editing) {
                            instruction.setText("Desenho cancelado.");
                        }
                    }
                };
        boolean started = circle ? plotArea.beginEditorCirclePlacement(resolution,handler)
                : plotArea.beginEditorTwoPointPlacement(PlotAreaView.TwoPointShape.RECTANGLE,handler);
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

    void startText() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        textPane.setExpanded(true);
        textContent.requestFocus();
        instruction.setText("Configure o texto e clique em Gerar e posicionar texto.");
    }

    void startPaint() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        paintDiameter.setText(Double.toString(session.selectedToolDiameter(0.3)));
        paintPane.setExpanded(true);
        instruction.setText("Selecione areas fechadas, configure Paint e calcule a previa.");
    }

    private void generatePaint() {
        if (!readyForTool()) return;
        plotArea.cancelPlacement();
        GeometryEditSession editing = session;
        try {
            var p = new org.flatcam.cam.ncc.PaintParameters(List.of(parseTransformNumber(paintDiameter, "Diametro")),
                    parseTransformNumber(paintOverlap, "Overlap") / 100, parseTransformNumber(paintMargin, "Margem"),
                    paintMethod.getValue(), paintConnect.isSelected(), paintContour.isSelected(), org.flatcam.cam.ncc.NccOrder.NONE, false);
            calculate(editing.preparePaint(p, units), result -> {
                Geometry paths = new org.locationtech.jts.geom.GeometryFactory().buildGeometry(result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
                boolean started = plotArea.beginEditorFixedPreview(paths, new PlotAreaView.PlacementHandler() {
                    public void onCommit(double dx, double dy) {
                        if (session != editing) return;
                        if (editing.applyOperation(result)) { refreshGeometry(); instruction.setText("Paint adicionado; contornos preservados. Ctrl+Z desfaz."); }
                        else instruction.setText("Selecao ou rascunho mudou; previa descartada.");
                    }
                    public void onCancel() { if (session == editing) instruction.setText("Previa do Paint cancelada; formas preservadas."); }
                });
                instruction.setText(started ? "Confira a previa fixa; clique no plot ou Confirmar Paint. Esc cancela." : "Nao foi possivel mostrar a previa.");
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
                    parseTransformNumber(textSize, "Tamanho"), textBold.isSelected(), textItalic.isSelected(), units);
            calculate(editing.prepareText(p, toolIndex), result -> {
                Geometry text = new org.locationtech.jts.geom.GeometryFactory().buildGeometry(
                        result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
                boolean started = plotArea.beginEditorFlashPlacement(text, new PlotAreaView.PlacementHandler() {
                    public void onCommit(double x, double y) {
                        if (session != editing) return;
                        try {
                            editing.addGeneratedGeometry(AffineTransformation.translationInstance(x, y).transform(text), toolIndex);
                            refreshGeometry(); instruction.setText("Texto adicionado. Ctrl+Z desfaz.");
                        } catch (IllegalArgumentException error) { instruction.setText(error.getMessage()); }
                    }
                    public void onCancel() { if (session == editing) instruction.setText("Texto cancelado; rascunho preservado."); }
                });
                instruction.setText(started ? "Clique para posicionar a linha de base do texto; Esc cancela."
                        : "Nao foi possivel iniciar o posicionamento do texto.");
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
        instruction.setText(busy ? "Borracha encerrada; o cálculo pendente será descartado."
                : "Borracha encerrada. As operações confirmadas foram mantidas.");
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
                        instruction.setText("Borracha ativa: clique nos destinos para apagar; Esc/botão direito encerra.");
                    }
                    public void onCommit(double dx, double dy) {
                        if (eraser != gesture || session != gesture.editing) return;
                        try {
                            calculate(session.prepareErase(gesture.mask, dx, dy), erased -> {
                                if (eraser != gesture) return;
                                boolean changed = session.applyOperation(erased);
                                refreshGeometry();
                                armEraser(gesture);
                                instruction.setText((changed ? "Área apagada. Ctrl+Z desfaz. " : "Nenhuma forma alterada. ")
                                        + "Borracha ativa: clique no próximo destino; Esc/botão direito encerra.");
                            }, () -> stopEraser(gesture));
                        } catch (IllegalArgumentException error) {
                            stopEraser(gesture); instruction.setText(error.getMessage());
                        }
                    }
                    public void onCancel() {
                        if (eraser == gesture && !gesture.suspending) {
                            stopEraser(gesture);
                            refreshSelection();
                            instruction.setText("Borracha encerrada. As operações confirmadas foram mantidas.");
                        }
                    }
                });
        if (!started) stopEraser(gesture);
        instruction.setText(!started ? "Não foi possível iniciar a borracha."
                : gesture.anchor == null ? "Borracha: clique na origem do molde e depois nos destinos; Esc cancela."
                : "Borracha ativa: clique no próximo destino; Ctrl+Z desfaz; Esc/botão direito encerra.");
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
        boolean simplified = editing.selectedCount() > exactHighlightLimit;
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
        calculate(request, result -> {
            if (editing.applyOperation(result)) {
                refreshGeometry();
                instruction.setText("Operacao concluida. Ctrl+Z desfaz.");
            } else {
                refreshSelection();
                instruction.setText("O rascunho mudou durante o calculo ou nao houve alteracao; resultado descartado.");
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
        instruction.setText("Calculando " + (request.operation()==GeometryEditSession.Operation.AFFINE ? "transformação" : request.operation()==GeometryEditSession.Operation.ROTATE ? "rotação"
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
