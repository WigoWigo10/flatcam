package org.flatcam.fx;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gerber.GerberShape;

/**
 * The Gerber Editor's sidebar panel (CONTEXTO_E_PROGRESSO.md section 9.4,
 * editor): selection, shape operations, undo/redo and Apply/Cancel. The
 * legacy AppGerberEditorUI shows an apertures table whose rows highlight for
 * the selected shapes' apertures; with no aperture editing yet, a one-line
 * summary of those same apertures stands in for it.
 */
final class GerberEditToolPanel {

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
    private Button selectToolButton;
    private boolean editable;
    private boolean busy;
    private boolean dirty;
    private boolean canUndo;
    private boolean canRedo;
    private boolean placing;
    private int selectedCount;

    GerberEditToolPanel(String objectName, boolean shapesApproximated,
                        BiFunction<String, Double, Node> icon, Runnable onClearSelection,
                        Runnable onDelete, Runnable onMoveCanvas, Runnable onCopyCanvas,
                        BiConsumer<Double, Double> onMove, BiConsumer<Double, Double> onCopy,
                        Runnable onCancelPlacement, Runnable onUndo, Runnable onRedo,
                        Runnable onApply, Runnable onCancel) {
        editable = !shapesApproximated;
        Label info = new Label("Editando: " + objectName);
        Label help = new Label("Clique seleciona a forma sob o cursor; Ctrl+clique alterna. "
                + "Arrastar para a direita seleciona as formas envolvidas; para a esquerda, as tocadas. "
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
                case "copy", "delete", "move" -> !shapesApproximated;
                default -> false;
            };
            String unavailableReason = shapesApproximated &&
                    (command.id().equals("copy") || command.id().equals("delete") || command.id().equals("move"))
                    ? " — indisponível neste projeto antigo" : " — em desenvolvimento";
            tool.setTooltip(new Tooltip(command.label() + (available ? "" : unavailableReason)));
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

        TextField offsetX = new TextField("0");
        offsetX.setPromptText("Delta X");
        TextField offsetY = new TextField("0");
        offsetY.setPromptText("Delta Y");
        HBox offsetRow = new HBox(6, new Label("X:"), offsetX, new Label("Y:"), offsetY);
        offsetX.setPrefColumnCount(6);
        offsetY.setPrefColumnCount(6);

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
        root = new VBox(10, info, palette, help, selectionLabel, historyRow, deleteButton,
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

    void setPlacing(boolean placing) {
        this.placing = placing;
        placementLabel.setText(placing ? "Clique na origem da selecao no Plot Area." : "");
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
