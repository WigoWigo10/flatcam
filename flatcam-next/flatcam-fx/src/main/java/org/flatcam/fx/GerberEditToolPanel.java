package org.flatcam.fx;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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

/**
 * The Gerber Editor's sidebar panel (CONTEXTO_E_PROGRESSO.md section 9.4,
 * editor): selection, shape operations, undo/redo and Apply/Cancel. The
 * aperture table selects D-codes for the first drawing tool and can add a
 * standard C/R/O apertures; editing/removing existing D-codes is a separate increment.
 */
final class GerberEditToolPanel {

    @FunctionalInterface
    interface ApertureCreator {
        String add(ApertureKind kind, double width, double height);
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
    private final Label apertureWidthLabel = new Label();
    private final Label apertureHeightLabel = new Label("Altura:");
    private final TableView<ApertureOption> apertureTable;
    private Button selectToolButton;
    private Button padToolButton;
    private Button trackToolButton;
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
                        ApertureCreator onAddAperture,
                        Runnable onCancelPlacement, Runnable onUndo, Runnable onRedo,
                        Runnable onApply, Runnable onCancel) {
        editable = !shapesApproximated;
        Label info = new Label("Editando: " + objectName);
        Label help = new Label("Clique seleciona a forma sob o cursor; Ctrl+clique alterna. "
                + "Arrastar para a direita seleciona as formas envolvidas; para a esquerda, as tocadas. "
                + "Crie uma abertura C, R ou O (D-code automatico) ou selecione uma existente na tabela, "
                + "depois use Adicionar Pad. Para uma trilha reta, selecione uma abertura C e clique no inicio e no fim. "
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
                case "copy", "delete", "move", "pad", "track" -> !shapesApproximated;
                default -> false;
            };
            String unavailableReason = shapesApproximated &&
                    (command.id().equals("copy") || command.id().equals("delete")
                            || command.id().equals("move") || command.id().equals("pad")
                            || command.id().equals("track"))
                    ? " — indisponível neste projeto antigo" : " — em desenvolvimento";
            String helpText = command.id().equals("track") && available
                    ? " - selecione uma abertura C; clique no inicio e no fim"
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
                case "track" -> {
                    trackToolButton = tool;
                    trackToolButton.setOnAction(event -> onAddTrack.accept(selectedTrackAperture));
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
        apertureWidthField.setMinWidth(0);
        apertureHeightField.setMinWidth(0);
        apertureTypeBox.getItems().setAll("C", "R", "O");
        apertureTypeBox.getSelectionModel().select("C");
        apertureTypeBox.setOnAction(event -> updateApertureFields(units));
        updateApertureFields(units);
        addApertureButton.setOnAction(event -> {
            try {
                ApertureKind kind = selectedApertureKind();
                double width = parseDimension(apertureWidthField.getText());
                double height = kind == ApertureKind.CIRCLE ? width : parseDimension(apertureHeightField.getText());
                if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
                    throw new IllegalArgumentException("Informe dimensoes positivas.");
                }
                onAddAperture.add(kind, width, height);
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
                apertureHeightLabel, apertureHeightField, addApertureButton);

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
        root = new VBox(10, info, palette, new Label("Aberturas (C/R/O: pads; C: trilhas):"), apertureTable,
                addApertureRow,
                help, selectionLabel, historyRow, deleteButton,
                operationRow, placementLabel, cancelPlacementButton, offsetRow, offsetActions);
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
        setPlacementState(placing, "Clique no inicio e depois no fim da trilha reta. Esc cancela.");
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
        trackToolButton.setDisable(busy || placing || !editable || selectedTrackAperture == null);
        addApertureButton.setDisable(busy || placing || !editable);
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
            updateButtons();
        });
        rows.stream().filter(row -> supportsPad(row.aperture()))
                .findFirst().ifPresent(table.getSelectionModel()::select);
        return table;
    }

    private void updateApertureFields(String units) {
        boolean circle = "C".equals(apertureTypeBox.getValue());
        apertureWidthLabel.setText((circle ? "Diametro" : "Largura") + " (" + units + "):");
        apertureHeightLabel.setText("Altura (" + units + "):");
        apertureHeightLabel.setVisible(!circle);
        apertureHeightLabel.setManaged(!circle);
        apertureHeightField.setVisible(!circle);
        apertureHeightField.setManaged(!circle);
        addApertureButton.setText("Adicionar abertura " + apertureTypeBox.getValue());
    }

    private ApertureKind selectedApertureKind() {
        return switch (apertureTypeBox.getValue()) {
            case "C" -> ApertureKind.CIRCLE;
            case "R" -> ApertureKind.RECTANGLE;
            case "O" -> ApertureKind.OBROUND;
            default -> throw new IllegalArgumentException("Selecione C, R ou O.");
        };
    }

    private static double parseDimension(String input) {
        return Double.parseDouble(input.trim().replace(',', '.'));
    }

    private static boolean supportsPad(Aperture aperture) {
        return (aperture.kind == ApertureKind.CIRCLE || aperture.kind == ApertureKind.RECTANGLE
                || aperture.kind == ApertureKind.OBROUND)
                && Double.isFinite(aperture.width) && aperture.width > 0
                && Double.isFinite(aperture.height) && aperture.height > 0;
    }

    private static boolean supportsTrack(Aperture aperture) {
        return aperture.kind == ApertureKind.CIRCLE
                && Double.isFinite(aperture.width) && aperture.width > 0;
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
}
