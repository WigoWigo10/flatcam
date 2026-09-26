package org.flatcam.fx;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.gerber.edit.TrackBendMode;
import org.locationtech.jts.operation.buffer.BufferParameters;

/**
 * The Gerber Editor's sidebar panel (CONTEXTO_E_PROGRESSO.md section 9.4,
 * editor): selection, shape operations, undo/redo and Apply/Cancel. The
 * aperture table selects D-codes for drawing and supports standard aperture edits.
 */
final class GerberEditToolPanel {

    @FunctionalInterface
    interface ApertureCreator {
        String add(ApertureKind kind, double width, double height, int vertices, double rotation);
    }

    @FunctionalInterface
    interface ApertureRenamer {
        boolean rename(String oldCode, String newCode);
    }

    record PadArrayRequest(String code, boolean circular, double x, double y,
                           int count, double spacing, double angle, double stepAngle) {
    }

    record CircleRequest(double x, double y, double radius, double startAngle, double sweepAngle) {
    }

    record TransformRequest(String operation, double value, double pivotX, double pivotY) {
    }

    private record ApertureOption(String code, Aperture aperture) {
    }

    private final VBox root;
    private final Label selectionLabel = new Label();
    private final Label errorLabel = new Label();
    private final Label placementLabel = new Label();
    private final Button undoButton = new Button("Desfazer");
    private final Button redoButton = new Button("Refazer");
    private final Button deleteButton = new Button("Excluir selecionadas");
    private final Button moveButton = new Button("Mover");
    private final Button copyButton = new Button("Copiar");
    private final Button moveOffsetButton = new Button("Mover X/Y");
    private final Button copyOffsetButton = new Button("Copiar X/Y");
    private final Button cancelPlacementButton = new Button("Cancelar posicionamento");
    private final Button applyButton = new Button("Aplicar");
    private final Button cancelButton = new Button("Cancelar");
    private final Button addApertureButton = new Button("Adicionar abertura C");
    private final ComboBox<String> apertureTypeBox = new ComboBox<>();
    private final TextField apertureWidthField = new TextField("1.0");
    private final TextField apertureHeightField = new TextField("1.0");
    private final TextField apertureVerticesField = new TextField("6");
    private final TextField apertureRotationField = new TextField("0");
    private final Label apertureWidthLabel = new Label();
    private final Label apertureHeightLabel = new Label("Altura:");
    private final Label apertureVerticesLabel = new Label("Vertices:");
    private final Label apertureRotationLabel = new Label("Rotacao:");
    private final TextField editCodeField = new TextField();
    private final TextField editWidthField = new TextField();
    private final TextField editHeightField = new TextField();
    private final TextField editVerticesField = new TextField();
    private final TextField editRotationField = new TextField();
    private final Button renameApertureButton = new Button("Alterar D-code");
    private final Button resizeApertureButton = new Button("Alterar dimensoes");
    private final Button deleteApertureButton = new Button("Excluir abertura e formas");
    private final TextField scaleFactorField = new TextField("1.25");
    private final TextField bufferDistanceField = new TextField("0.1");
    private final ComboBox<String> bufferJoinBox = new ComboBox<>();
    private final Button scaleActionButton = new Button("Escalar selecionadas");
    private final Button bufferActionButton = new Button("Buffer selecionadas");
    private final Button polygonizeActionButton = new Button("Poligonizar selecionadas");
    private final Button padArrayActionButton = new Button("Criar array de pads");
    private final ComboBox<String> padArrayModeBox = new ComboBox<>();
    private final TextField arrayXField = new TextField("0");
    private final TextField arrayYField = new TextField("0");
    private final TextField arrayCountField = new TextField("3");
    private final TextField arraySpacingField = new TextField("1");
    private final TextField arrayAngleField = new TextField("0");
    private final TextField arrayStepField = new TextField("90");
    private final TextField discXField = new TextField("0");
    private final TextField discYField = new TextField("0");
    private final TextField discRadiusField = new TextField("1");
    private final TextField discStartField = new TextField("0");
    private final TextField discSweepField = new TextField("180");
    private final Button discActionButton = new Button("Criar disco");
    private final Button semiDiscActionButton = new Button("Criar semidisco");
    private final Button markAreaActionButton = new Button("Selecionar por area");
    private final TextField minAreaField = new TextField("0");
    private final TextField maxAreaField = new TextField("1");
    private final Button eraseActionButton = new Button("Apagar com selecionadas X/Y");
    private final Button transformActionButton = new Button("Transformar selecionadas");
    private final ComboBox<String> transformTypeBox = new ComboBox<>();
    private final TextField transformValueField = new TextField("90");
    private final TextField transformPivotXField = new TextField("0");
    private final TextField transformPivotYField = new TextField("0");
    private final TableView<ApertureOption> apertureTable;
    private Button selectToolButton;
    private Button padToolButton;
    private Button trackToolButton;
    private Button regionToolButton;
    private String selectedPadAperture;
    private String selectedTrackAperture;
    private boolean editable;
    private boolean busy;
    private boolean dirty;
    private boolean canUndo;
    private boolean canRedo;
    private boolean placing;
    private int selectedCount;

    GerberEditToolPanel(String objectName, String units, Map<String, Aperture> apertures,
                        boolean shapesApproximated,
                        BiFunction<String, Double, Node> icon, Runnable onClearSelection,
                        Runnable onDelete, Runnable onMoveCanvas, Runnable onCopyCanvas,
                        BiConsumer<Double, Double> onMove, BiConsumer<Double, Double> onCopy,
                        Consumer<String> onAddPad, Consumer<String> onAddTrack,
                        Runnable onAddRegion, Consumer<PadArrayRequest> onAddPadArray,
                        Consumer<CircleRequest> onAddDisc, Consumer<CircleRequest> onAddSemiDisc,
                        ApertureCreator onAddAperture,
                        ApertureRenamer onRenameAperture,
                        BiConsumer<String, Aperture> onResizeAperture, Consumer<String> onDeleteAperture,
                        DoubleConsumer onScale, BiConsumer<Double, Integer> onBuffer, Runnable onPolygonize,
                        BiConsumer<Double, Double> onMarkArea, BiConsumer<Double, Double> onErase,
                        Consumer<TransformRequest> onTransform,
                        Runnable onCancelPlacement, Runnable onUndo, Runnable onRedo,
                        Runnable onApply, Runnable onCancel) {
        editable = !shapesApproximated;
        Label info = new Label("Editando: " + objectName);
        Label help = new Label("Clique seleciona a forma sob o cursor; Ctrl+clique alterna. "
                + "Arrastar para a direita seleciona as formas envolvidas; para a esquerda, as tocadas. "
                + "Crie uma abertura C, R, O ou P (D-code automatico) ou selecione uma existente na tabela, "
                + "depois use Adicionar Pad. Para uma trilha, selecione uma abertura C e clique nos pontos do caminho. "
                + "Arrays, discos e transformacoes usam os campos numericos abaixo. "
                + "Pan: botao direito ou do meio.");
        help.setWrapText(true);
        Label note = new Label("Mover/Copiar usa dois cliques no Plot Area: origem e destino (Esc cancela). "
                + "Mover X/Y e Copiar X/Y aplicam deslocamentos exatos nas unidades do Gerber. "
                + "Aplicar cria um novo objeto Gerber; Cancelar descarta as alteracoes.");
        note.setWrapText(true);
        FlowPane palette = new FlowPane(4, 4);
        palette.getStyleClass().add("editor-tool-palette");
        for (LegacyUiManifest.Command command : LegacyUiManifest.GERBER_EDITOR) {
            Button tool = new Button(null, icon.apply(command.icon(), 20.0));
            boolean available = switch (command.id()) {
                case "select" -> true;
                case "copy", "delete", "move", "pad", "pad_array", "track", "region",
                        "polygonize", "disc", "semidisc", "scale", "buffer", "mark_area",
                        "eraser", "transform" -> !shapesApproximated;
                default -> false;
            };
            String unavailableReason = shapesApproximated &&
                    (command.id().equals("copy") || command.id().equals("delete")
                            || command.id().equals("move") || command.id().equals("pad")
                            || command.id().equals("pad_array")
                            || command.id().equals("track") || command.id().equals("region")
                            || command.id().equals("scale") || command.id().equals("buffer")
                            || command.id().equals("polygonize") || command.id().equals("disc")
                            || command.id().equals("semidisc") || command.id().equals("mark_area")
                            || command.id().equals("eraser") || command.id().equals("transform"))
                    ? " — indisponível neste projeto antigo" : " — em desenvolvimento";
            String helpText = command.id().equals("track") && available
                    ? " - abertura C; clique nos pontos e conclua com Enter ou botao direito"
                    : "";
            tool.setTooltip(new Tooltip(command.label() + helpText + (available ? "" : unavailableReason)));
            tool.setDisable(!available);
            if (!available) {
                tool.getStyleClass().add("planned-command");
            }
            switch (command.id()) {
                case "select" -> {
                    selectToolButton = tool;
                    selectToolButton.setOnAction(event -> onClearSelection.run());
                    palette.getChildren().add(selectToolButton);
                    continue;
                }
                case "pad" -> {
                    padToolButton = tool;
                    padToolButton.setOnAction(event -> onAddPad.accept(selectedPadAperture));
                }
                case "pad_array" -> {
                    tool.disableProperty().bind(padArrayActionButton.disableProperty());
                    tool.setOnAction(event -> padArrayActionButton.fire());
                }
                case "track" -> {
                    trackToolButton = tool;
                    trackToolButton.setOnAction(event -> onAddTrack.accept(selectedTrackAperture));
                }
                case "region" -> {
                    regionToolButton = tool;
                    regionToolButton.setOnAction(event -> onAddRegion.run());
                }
                case "polygonize" -> {
                    tool.disableProperty().bind(polygonizeActionButton.disableProperty());
                    tool.setOnAction(event -> polygonizeActionButton.fire());
                }
                case "disc" -> {
                    tool.disableProperty().bind(discActionButton.disableProperty());
                    tool.setOnAction(event -> discActionButton.fire());
                }
                case "semidisc" -> {
                    tool.disableProperty().bind(semiDiscActionButton.disableProperty());
                    tool.setOnAction(event -> semiDiscActionButton.fire());
                }
                case "mark_area" -> {
                    tool.disableProperty().bind(markAreaActionButton.disableProperty());
                    tool.setOnAction(event -> markAreaActionButton.fire());
                }
                case "eraser" -> {
                    tool.disableProperty().bind(eraseActionButton.disableProperty());
                    tool.setOnAction(event -> eraseActionButton.fire());
                }
                case "transform" -> {
                    tool.disableProperty().bind(transformActionButton.disableProperty());
                    tool.setOnAction(event -> transformActionButton.fire());
                }
                case "scale" -> {
                    tool.disableProperty().bind(scaleActionButton.disableProperty());
                    tool.setOnAction(event -> scaleActionButton.fire());
                }
                case "buffer" -> {
                    tool.disableProperty().bind(bufferActionButton.disableProperty());
                    tool.setOnAction(event -> bufferActionButton.fire());
                }
                case "copy" -> {
                    tool.disableProperty().bind(copyButton.disableProperty());
                    tool.setOnAction(event -> copyButton.fire());
                }
                case "delete" -> {
                    tool.disableProperty().bind(deleteButton.disableProperty());
                    tool.setOnAction(event -> deleteButton.fire());
                }
                case "move" -> {
                    tool.disableProperty().bind(moveButton.disableProperty());
                    tool.setOnAction(event -> moveButton.fire());
                }
                default -> { }
            }
            palette.getChildren().add(tool);
        }
        selectionLabel.setWrapText(true);
        showSelection(0, java.util.List.of());
        apertureTable = buildApertureTable(apertures);
        String defaultSize = "MM".equalsIgnoreCase(units) ? "1.0" : "0.040";
        apertureWidthField.setText(defaultSize);
        apertureHeightField.setText(defaultSize);
        apertureWidthField.setPrefColumnCount(6);
        apertureHeightField.setPrefColumnCount(6);
        apertureVerticesField.setPrefColumnCount(4);
        apertureRotationField.setPrefColumnCount(5);
        apertureWidthField.setMinWidth(0);
        apertureHeightField.setMinWidth(0);
        apertureTypeBox.getItems().setAll("C", "R", "O", "P");
        apertureTypeBox.getSelectionModel().select("C");
        apertureTypeBox.setOnAction(event -> updateApertureFields(units));
        updateApertureFields(units);
        addApertureButton.setOnAction(event -> {
            try {
                ApertureKind kind = selectedApertureKind();
                double width = parseDimension(apertureWidthField.getText());
                double height = kind == ApertureKind.CIRCLE || kind == ApertureKind.POLYGON
                        ? width : parseDimension(apertureHeightField.getText());
                int vertices = kind == ApertureKind.POLYGON
                        ? Integer.parseInt(apertureVerticesField.getText().trim()) : 0;
                double rotation = kind == ApertureKind.POLYGON
                        ? parseDimension(apertureRotationField.getText()) : 0;
                if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
                    throw new IllegalArgumentException("Informe dimensoes positivas.");
                }
                onAddAperture.add(kind, width, height, vertices, rotation);
                showError("");
            } catch (NumberFormatException exception) {
                showError("Informe dimensoes numericas.");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                showError(exception.getMessage());
            }
        });
        apertureWidthField.setOnAction(event -> addApertureButton.fire());
        apertureHeightField.setOnAction(event -> addApertureButton.fire());
        FlowPane addApertureRow = new FlowPane(6, 6,
                new Label("Nova abertura:"), apertureTypeBox, apertureWidthLabel, apertureWidthField,
                apertureHeightLabel, apertureHeightField, apertureVerticesLabel, apertureVerticesField,
                apertureRotationLabel, apertureRotationField, addApertureButton);

        for (TextField field : List.of(editCodeField, editWidthField, editHeightField,
                editVerticesField, editRotationField)) {
            field.setPrefColumnCount(5);
            field.setMinWidth(0);
        }
        renameApertureButton.setOnAction(event -> {
            try {
                ApertureOption selected = selectedAperture();
                if (selected != null) {
                    onRenameAperture.rename(selected.code(), editCodeField.getText().trim());
                    showError("");
                }
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        resizeApertureButton.setOnAction(event -> {
            try {
                ApertureOption selected = selectedAperture();
                if (selected != null) {
                    onResizeAperture.accept(selected.code(), editedAperture(selected.aperture()));
                    showError("");
                }
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        deleteApertureButton.setOnAction(event -> {
            try {
                ApertureOption selected = selectedAperture();
                if (selected != null) {
                    onDeleteAperture.accept(selected.code());
                    showError("");
                }
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        FlowPane editApertureRow = new FlowPane(6, 6,
                new Label("D:"), editCodeField, new Label("Largura/diametro:"), editWidthField,
                new Label("Altura:"), editHeightField, new Label("Vertices:"), editVerticesField,
                new Label("Rotacao:"), editRotationField,
                renameApertureButton, resizeApertureButton, deleteApertureButton);

        scaleFactorField.setPrefColumnCount(5);
        bufferDistanceField.setPrefColumnCount(5);
        bufferJoinBox.getItems().setAll("Arredondado", "Quadrado", "Chanfrado");
        bufferJoinBox.getSelectionModel().selectFirst();
        scaleActionButton.setOnAction(event -> {
            try {
                onScale.accept(parseDimension(scaleFactorField.getText()));
                showError("");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        bufferActionButton.setOnAction(event -> {
            try {
                int joinStyle = switch (bufferJoinBox.getSelectionModel().getSelectedIndex()) {
                    case 1 -> BufferParameters.JOIN_MITRE;
                    case 2 -> BufferParameters.JOIN_BEVEL;
                    default -> BufferParameters.JOIN_ROUND;
                };
                onBuffer.accept(parseDimension(bufferDistanceField.getText()), joinStyle);
                showError("");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        polygonizeActionButton.setOnAction(event -> {
            try {
                onPolygonize.run();
                showError("");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        FlowPane shapeOperations = new FlowPane(6, 6,
                polygonizeActionButton, new Label("Fator:"), scaleFactorField, scaleActionButton,
                new Label("Distancia:"), bufferDistanceField, bufferJoinBox, bufferActionButton);

        for (TextField field : List.of(arrayXField, arrayYField, arrayCountField,
                arraySpacingField, arrayAngleField, arrayStepField)) {
            field.setPrefColumnCount(5);
            field.setMinWidth(0);
        }
        padArrayModeBox.getItems().setAll("Linear", "Circular");
        padArrayModeBox.getSelectionModel().selectFirst();
        padArrayActionButton.setOnAction(event -> {
            try {
                boolean circular = padArrayModeBox.getSelectionModel().getSelectedIndex() == 1;
                onAddPadArray.accept(new PadArrayRequest(selectedPadAperture,
                        circular,
                        parseDimension(arrayXField.getText()), parseDimension(arrayYField.getText()),
                        Integer.parseInt(arrayCountField.getText().trim()),
                        parseDimension(arraySpacingField.getText()), parseDimension(arrayAngleField.getText()),
                        circular ? parseDimension(arrayStepField.getText()) : 0));
                showError("");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        FlowPane arrayRow = new FlowPane(6, 6,
                new Label("Array:"), padArrayModeBox, new Label("X:"), arrayXField,
                new Label("Y:"), arrayYField, new Label("Quantidade:"), arrayCountField,
                new Label("Passo/raio:"), arraySpacingField, new Label("Angulo inicial:"), arrayAngleField,
                new Label("Passo angular:"), arrayStepField, padArrayActionButton);
        Label arrayHelp = new Label("Linear: X/Y e primeiro pad; passo e distancia, angulo e direcao. "
                + "Circular: X/Y e centro; passo/raio e raio, angulo inicial e primeiro pad, passo angular e rotacao.");
        arrayHelp.setWrapText(true);

        for (TextField field : List.of(discXField, discYField, discRadiusField, discStartField, discSweepField)) {
            field.setPrefColumnCount(5);
            field.setMinWidth(0);
        }
        discActionButton.setOnAction(event -> runCircleAction(onAddDisc, false));
        semiDiscActionButton.setOnAction(event -> runCircleAction(onAddSemiDisc, true));
        FlowPane circleRow = new FlowPane(6, 6,
                new Label("Disco/semidisco:"), new Label("Centro X:"), discXField,
                new Label("Y:"), discYField, new Label("Raio:"), discRadiusField,
                new Label("Angulo inicial:"), discStartField, new Label("Arco (+CCW/-CW):"), discSweepField,
                discActionButton, semiDiscActionButton);
        minAreaField.setPrefColumnCount(5);
        maxAreaField.setPrefColumnCount(5);
        markAreaActionButton.setOnAction(event -> {
            try {
                onMarkArea.accept(parseDimension(minAreaField.getText()), parseDimension(maxAreaField.getText()));
                showError("");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        FlowPane areaRow = new FlowPane(6, 6, new Label("Area >"), minAreaField,
                new Label("e <"), maxAreaField, markAreaActionButton,
                new Label("Depois use Excluir selecionadas, se desejado."));

        transformTypeBox.getItems().setAll("Girar", "Espelhar X", "Espelhar Y",
                "Escalar X", "Escalar Y", "Inclinar X", "Inclinar Y");
        transformTypeBox.getSelectionModel().selectFirst();
        for (TextField field : List.of(transformValueField, transformPivotXField, transformPivotYField)) {
            field.setPrefColumnCount(5);
            field.setMinWidth(0);
        }
        transformActionButton.setOnAction(event -> {
            try {
                String operation = switch (transformTypeBox.getSelectionModel().getSelectedIndex()) {
                    case 1 -> "mirror_x";
                    case 2 -> "mirror_y";
                    case 3 -> "scale_x";
                    case 4 -> "scale_y";
                    case 5 -> "skew_x";
                    case 6 -> "skew_y";
                    default -> "rotate";
                };
                onTransform.accept(new TransformRequest(operation, parseDimension(transformValueField.getText()),
                        parseDimension(transformPivotXField.getText()), parseDimension(transformPivotYField.getText())));
                showError("");
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
        FlowPane transformRow = new FlowPane(6, 6, new Label("Transformacao:"), transformTypeBox,
                new Label("Angulo/fator:"), transformValueField, new Label("Referencia X:"),
                transformPivotXField, new Label("Y:"), transformPivotYField, transformActionButton);

        TextField offsetX = new TextField("0");
        offsetX.setPromptText("Delta X");
        TextField offsetY = new TextField("0");
        offsetY.setPromptText("Delta Y");
        HBox offsetRow = new HBox(6, new Label("X:"), offsetX, new Label("Y:"), offsetY);
        offsetX.setPrefColumnCount(6);
        offsetY.setPrefColumnCount(6);
        offsetX.setMinWidth(0);
        offsetY.setMinWidth(0);

        undoButton.setOnAction(e -> onUndo.run());
        redoButton.setOnAction(e -> onRedo.run());
        deleteButton.setOnAction(e -> onDelete.run());
        moveButton.setOnAction(e -> onMoveCanvas.run());
        copyButton.setOnAction(e -> onCopyCanvas.run());
        moveOffsetButton.setOnAction(e -> runOffsetAction(offsetX, offsetY, onMove));
        copyOffsetButton.setOnAction(e -> runOffsetAction(offsetX, offsetY, onCopy));
        eraseActionButton.setOnAction(e -> runOffsetAction(offsetX, offsetY, onErase));
        cancelPlacementButton.setOnAction(e -> onCancelPlacement.run());
        cancelPlacementButton.setMinWidth(0);
        cancelPlacementButton.setMaxWidth(Double.MAX_VALUE);
        cancelPlacementButton.setVisible(false);
        cancelPlacementButton.setManaged(false);
        placementLabel.setWrapText(true);
        placementLabel.setVisible(false);
        placementLabel.setManaged(false);
        HBox historyRow = new HBox(6, undoButton, redoButton);
        HBox operationRow = new HBox(6, moveButton, copyButton);
        FlowPane offsetActions = new FlowPane(6, 6, moveOffsetButton, copyOffsetButton);

        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> onApply.run());
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> onCancel.run());

        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("form-error-label");
        root = new VBox(10, info, palette, new Label("Aberturas (C/R/O/P/AM: pads; C: trilhas):"), apertureTable,
                addApertureRow, editApertureRow, arrayRow, arrayHelp, circleRow, areaRow, transformRow,
                help, selectionLabel, historyRow, deleteButton, shapeOperations,
                operationRow, placementLabel, cancelPlacementButton, offsetRow, offsetActions,
                eraseActionButton);
        if (shapesApproximated) {
            Label approximated = new Label("Este projeto antigo nao contem formas individuais. "
                    + "Abra o Gerber original para editar sem perder regioes ou separar incorretamente pads/trilhas.");
            approximated.setWrapText(true);
            approximated.getStyleClass().add("form-error-label");
            root.getChildren().add(approximated);
        }
        root.getChildren().addAll(errorLabel, note, applyButton, cancelButton);
        root.setPadding(new Insets(12));
        updateButtons();
    }

    Node node() {
        return root;
    }

    void showSelection(int shapeCount, Collection<String> apertureCodes) {
        selectedCount = shapeCount;
        if (shapeCount == 0) {
            selectionLabel.setText("Selecao: nenhuma forma");
            return;
        }
        String apertures = apertureCodes.stream()
                .map(code -> GerberShape.REGION_APERTURE.equals(code) ? "Regiao" : "D" + code)
                .collect(Collectors.joining(", "));
        selectionLabel.setText("Selecao: " + shapeCount + " forma(s) - " + apertures);
    }

    void showState(boolean dirty, boolean canUndo, boolean canRedo) {
        this.dirty = dirty;
        this.canUndo = canUndo;
        this.canRedo = canRedo;
        updateButtons();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateButtons();
    }

    boolean isBusy() {
        return busy;
    }

    void showError(String message) {
        errorLabel.setText(message);
    }

    void refreshApertures(Map<String, Aperture> apertures, String selectedCode) {
        apertureTable.getItems().setAll(apertureRows(apertures));
        apertureTable.setPrefHeight(Math.min(180, 30 + Math.max(1, apertureTable.getItems().size()) * 27));
        ApertureOption preferred = apertureTable.getItems().stream()
                .filter(row -> row.code().equals(selectedCode)).findFirst().orElse(null);
        ApertureOption fallback = apertureTable.getItems().stream()
                .filter(row -> supportsPad(row.aperture()))
                .findFirst().orElse(null);
        if (preferred != null || fallback != null) {
            apertureTable.getSelectionModel().select(preferred != null ? preferred : fallback);
        } else {
            apertureTable.getSelectionModel().clearSelection();
        }
    }

    String selectedPadAperture() {
        return selectedPadAperture;
    }

    void setPlacing(boolean placing) {
        setPlacementState(placing, "Clique na origem da selecao no Plot Area.");
    }

    void setPadPlacing(boolean placing) {
        setPlacementState(placing, "Clique no Plot Area para posicionar o pad. Esc cancela.");
    }

    void setTrackPlacing(boolean placing) {
        setPlacementState(placing, "Clique no primeiro ponto da trilha. T/R muda o modo; Esc cancela.");
    }

    void setRegionPlacing(boolean placing) {
        setPlacementState(placing, "Clique nos vertices da regiao. T/R muda o modo; Esc cancela.");
    }

    void showRegionProgress(int anchorCount, TrackBendMode mode) {
        if (placing) {
            placementLabel.setText("Regiao: " + anchorCount + " pontos; modo: " + mode.displayName()
                    + ". Enter, duplo clique ou botao direito conclui; Backspace volta.");
        }
    }

    void showTrackProgress(int anchorCount, TrackBendMode mode) {
        if (!placing) {
            return;
        }
        if (anchorCount == 0) {
            placementLabel.setText("Clique no primeiro ponto. Modo: " + mode.displayName()
                    + ". T/R muda o modo; Esc cancela.");
        } else {
            placementLabel.setText("Pontos: " + anchorCount + " - modo: " + mode.displayName()
                    + ". Clique continua; Enter/duplo clique/botao direito conclui; Backspace volta; T/R muda.");
        }
    }

    private void setPlacementState(boolean placing, String message) {
        this.placing = placing;
        placementLabel.setText(placing ? message : "");
        placementLabel.setVisible(placing);
        placementLabel.setManaged(placing);
        cancelPlacementButton.setVisible(placing);
        cancelPlacementButton.setManaged(placing);
        updateButtons();
    }

    void setPlacementAnchorChosen() {
        if (placing) {
            placementLabel.setText("Mova o cursor e clique no destino. Esc cancela.");
        }
    }

    private void updateButtons() {
        undoButton.setDisable(busy || placing || !canUndo);
        redoButton.setDisable(busy || placing || !canRedo);
        deleteButton.setDisable(busy || placing || !editable || selectedCount == 0);
        moveButton.setDisable(busy || placing || !editable || selectedCount == 0);
        copyButton.setDisable(busy || placing || !editable || selectedCount == 0);
        moveOffsetButton.setDisable(busy || placing || !editable || selectedCount == 0);
        copyOffsetButton.setDisable(busy || placing || !editable || selectedCount == 0);
        cancelPlacementButton.setDisable(busy || !placing);
        applyButton.setDisable(busy || placing || !dirty);
        cancelButton.setDisable(busy);
        selectToolButton.setDisable(busy || placing);
        padToolButton.setDisable(busy || placing || !editable || selectedPadAperture == null);
        padArrayActionButton.setDisable(busy || placing || !editable || selectedPadAperture == null);
        trackToolButton.setDisable(busy || placing || !editable || selectedTrackAperture == null);
        regionToolButton.setDisable(busy || placing || !editable);
        addApertureButton.setDisable(busy || placing || !editable);
        renameApertureButton.setDisable(busy || placing || !editable || selectedAperture() == null);
        resizeApertureButton.setDisable(busy || placing || !editable || selectedAperture() == null
                || selectedAperture().aperture().kind == ApertureKind.MACRO);
        deleteApertureButton.setDisable(busy || placing || !editable || selectedAperture() == null);
        scaleActionButton.setDisable(busy || placing || !editable || selectedCount == 0);
        bufferActionButton.setDisable(busy || placing || !editable || selectedCount == 0);
        polygonizeActionButton.setDisable(busy || placing || !editable || selectedCount == 0);
        discActionButton.setDisable(busy || placing || !editable);
        semiDiscActionButton.setDisable(busy || placing || !editable);
        markAreaActionButton.setDisable(busy || placing || !editable);
        eraseActionButton.setDisable(busy || placing || !editable || selectedCount == 0);
        transformActionButton.setDisable(busy || placing || !editable || selectedCount == 0);
        apertureTypeBox.setDisable(busy || placing || !editable);
        apertureWidthField.setDisable(busy || placing || !editable);
        apertureHeightField.setDisable(busy || placing || !editable);
        if (apertureTable != null) {
            apertureTable.setDisable(busy || placing);
        }
    }

    private TableView<ApertureOption> buildApertureTable(Map<String, Aperture> apertures) {
        List<ApertureOption> rows = apertureRows(apertures);
        TableColumn<ApertureOption, String> code = new TableColumn<>("D");
        code.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().code()));
        code.setMinWidth(45);
        code.setPrefWidth(45);
        code.setMaxWidth(55);
        TableColumn<ApertureOption, String> type = new TableColumn<>("Tipo");
        type.setCellValueFactory(cell -> new ReadOnlyStringWrapper(apertureType(cell.getValue().aperture())));
        type.setMinWidth(45);
        type.setPrefWidth(50);
        type.setMaxWidth(65);
        TableColumn<ApertureOption, String> size = new TableColumn<>("Dimensoes");
        size.setCellValueFactory(cell -> new ReadOnlyStringWrapper(apertureSize(cell.getValue().aperture())));
        size.setMinWidth(85);
        TableView<ApertureOption> table = new TableView<>();
        table.getColumns().addAll(List.of(code, type, size));
        table.getItems().setAll(rows);
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(Math.min(180, 30 + Math.max(1, rows.size()) * 27));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, selectedRow) -> {
            selectedPadAperture = selectedRow != null && supportsPad(selectedRow.aperture())
                    ? selectedRow.code() : null;
            selectedTrackAperture = selectedRow != null && supportsTrack(selectedRow.aperture())
                    ? selectedRow.code() : null;
            if (selectedRow != null) {
                editCodeField.setText(selectedRow.code());
                editWidthField.setText(Double.toString(selectedRow.aperture().width));
                editHeightField.setText(Double.toString(selectedRow.aperture().height));
                editVerticesField.setText(Integer.toString(selectedRow.aperture().polygonVertices()));
                editRotationField.setText(Double.toString(selectedRow.aperture().polygonRotation()));
            }
            updateButtons();
        });
        rows.stream().filter(row -> supportsPad(row.aperture()))
                .findFirst().ifPresent(table.getSelectionModel()::select);
        return table;
    }

    private void updateApertureFields(String units) {
        boolean circle = "C".equals(apertureTypeBox.getValue());
        boolean polygon = "P".equals(apertureTypeBox.getValue());
        apertureWidthLabel.setText((circle ? "Diametro" : "Largura") + " (" + units + "):");
        apertureHeightLabel.setText("Altura (" + units + "):");
        for (Node node : List.of(apertureHeightLabel, apertureHeightField)) {
            node.setVisible(!circle && !polygon);
            node.setManaged(!circle && !polygon);
        }
        for (Node node : List.of(apertureVerticesLabel, apertureVerticesField,
                apertureRotationLabel, apertureRotationField)) {
            node.setVisible(polygon);
            node.setManaged(polygon);
        }
        addApertureButton.setText("Adicionar abertura " + apertureTypeBox.getValue());
    }

    private ApertureKind selectedApertureKind() {
        return switch (apertureTypeBox.getValue()) {
            case "C" -> ApertureKind.CIRCLE;
            case "R" -> ApertureKind.RECTANGLE;
            case "O" -> ApertureKind.OBROUND;
            case "P" -> ApertureKind.POLYGON;
            default -> throw new IllegalArgumentException("Selecione C, R, O ou P.");
        };
    }

    private static double parseDimension(String input) {
        return Double.parseDouble(input.trim().replace(',', '.'));
    }

    private static boolean supportsPad(Aperture aperture) {
        if (aperture.kind == ApertureKind.MACRO) {
            return true;
        }
        return (aperture.kind == ApertureKind.CIRCLE || aperture.kind == ApertureKind.RECTANGLE
                || aperture.kind == ApertureKind.OBROUND || aperture.kind == ApertureKind.POLYGON)
                && Double.isFinite(aperture.width) && aperture.width > 0
                && Double.isFinite(aperture.height) && aperture.height > 0;
    }

    private static boolean supportsTrack(Aperture aperture) {
        return aperture.kind == ApertureKind.CIRCLE
                && Double.isFinite(aperture.width) && aperture.width > 0;
    }

    private ApertureOption selectedAperture() {
        return apertureTable == null ? null : apertureTable.getSelectionModel().getSelectedItem();
    }

    private Aperture editedAperture(Aperture previous) {
        double width = parseDimension(editWidthField.getText());
        double height = parseDimension(editHeightField.getText());
        return switch (previous.kind) {
            case CIRCLE -> Aperture.circle(width);
            case RECTANGLE -> Aperture.rectangle(width, height);
            case OBROUND -> Aperture.obround(width, height);
            case POLYGON -> Aperture.polygon(width,
                    Integer.parseInt(editVerticesField.getText().trim()),
                    parseDimension(editRotationField.getText()));
            case MACRO -> throw new IllegalArgumentException("Aberturas macro nao podem ser redimensionadas aqui.");
        };
    }

    private static List<ApertureOption> apertureRows(Map<String, Aperture> apertures) {
        return apertures.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> apertureCodeOrder(entry.getKey())))
                .map(entry -> new ApertureOption(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static int apertureCodeOrder(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String apertureSize(Aperture aperture) {
        return switch (aperture.kind) {
            case CIRCLE, POLYGON -> String.format(Locale.ROOT, "%.4f", aperture.width);
            case RECTANGLE, OBROUND -> String.format(Locale.ROOT, "%.4f x %.4f", aperture.width, aperture.height);
            case MACRO -> "—";
        };
    }

    private static String apertureType(Aperture aperture) {
        return switch (aperture.kind) {
            case CIRCLE -> "C";
            case RECTANGLE -> "R";
            case OBROUND -> "O";
            case POLYGON -> "P";
            case MACRO -> "AM";
        };
    }

    private void runOffsetAction(TextField x, TextField y, BiConsumer<Double, Double> action) {
        try {
            double dx = Double.parseDouble(x.getText().trim().replace(',', '.'));
            double dy = Double.parseDouble(y.getText().trim().replace(',', '.'));
            action.accept(dx, dy);
            errorLabel.setText("");
        } catch (NumberFormatException exception) {
            errorLabel.setText("Informe deslocamentos X/Y numericos.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            errorLabel.setText(exception.getMessage());
        }
    }

    private void runCircleAction(Consumer<CircleRequest> action, boolean arc) {
        try {
            action.accept(new CircleRequest(parseDimension(discXField.getText()),
                    parseDimension(discYField.getText()), parseDimension(discRadiusField.getText()),
                    arc ? parseDimension(discStartField.getText()) : 0,
                    arc ? parseDimension(discSweepField.getText()) : 180));
            showError("");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }
}
