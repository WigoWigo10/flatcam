package org.flatcam.fx;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
    private final Button undoButton = new Button("Desfazer");
    private final Button redoButton = new Button("Refazer");
    private final Button deleteButton = new Button("Excluir selecionadas");
    private final Button moveButton = new Button("Mover");
    private final Button copyButton = new Button("Copiar");
    private final Button applyButton = new Button("Aplicar");
    private final Button cancelButton = new Button("Cancelar");
    private boolean editable;
    private boolean busy;
    private boolean dirty;
    private boolean canUndo;
    private boolean canRedo;
    private int selectedCount;

    GerberEditToolPanel(String objectName, boolean shapesApproximated,
                        Runnable onDelete, BiConsumer<Double, Double> onMove,
                        BiConsumer<Double, Double> onCopy, Runnable onUndo, Runnable onRedo,
                        Runnable onApply, Runnable onCancel) {
        editable = !shapesApproximated;
        Label info = new Label("Editando: " + objectName);
        Label help = new Label("Clique seleciona a forma sob o cursor; Ctrl+clique alterna. "
                + "Arrastar para a direita seleciona as formas envolvidas; para a esquerda, as tocadas. "
                + "Pan: botao direito ou do meio.");
        help.setWrapText(true);
        Label note = new Label("Mover/Copiar usa o deslocamento X/Y abaixo, nas unidades do Gerber. "
                + "Aplicar cria um novo objeto Gerber; Cancelar descarta as alteracoes.");
        note.setWrapText(true);
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
        moveButton.setOnAction(e -> runOffsetAction(offsetX, offsetY, onMove));
        copyButton.setOnAction(e -> runOffsetAction(offsetX, offsetY, onCopy));
        HBox historyRow = new HBox(6, undoButton, redoButton);
        HBox operationRow = new HBox(6, moveButton, copyButton);

        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> onApply.run());
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> onCancel.run());

        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("form-error-label");
        root = new VBox(10, info, help, selectionLabel, historyRow, deleteButton, offsetRow, operationRow);
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

    private void updateButtons() {
        undoButton.setDisable(busy || !canUndo);
        redoButton.setDisable(busy || !canRedo);
        deleteButton.setDisable(busy || !editable || selectedCount == 0);
        moveButton.setDisable(busy || !editable || selectedCount == 0);
        copyButton.setDisable(busy || !editable || selectedCount == 0);
        applyButton.setDisable(busy || !dirty);
        cancelButton.setDisable(busy);
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
