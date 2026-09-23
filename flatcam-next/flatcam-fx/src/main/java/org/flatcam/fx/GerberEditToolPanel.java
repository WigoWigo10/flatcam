package org.flatcam.fx;

import java.util.Collection;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gerber.GerberShape;

/**
 * The Gerber Editor's sidebar panel (CONTEXTO_E_PROGRESSO.md section 9.4,
 * slices 1-2): Aplicar/Cancelar plus a summary of the current selection. The
 * legacy AppGerberEditorUI shows an apertures table whose rows highlight for
 * the selected shapes' apertures; with no aperture editing yet, a one-line
 * summary of those same apertures stands in for it.
 */
final class GerberEditToolPanel {

    private final VBox root;
    private final Label selectionLabel = new Label();

    GerberEditToolPanel(String objectName, boolean shapesApproximated, Runnable onApply, Runnable onCancel) {
        Label info = new Label("Editando: " + objectName);
        Label help = new Label("Clique seleciona a forma sob o cursor; Ctrl+clique alterna. "
                + "Arrastar para a direita seleciona as formas envolvidas; para a esquerda, as tocadas. "
                + "Pan: botao direito ou do meio.");
        help.setWrapText(true);
        Label note = new Label("Ainda sem ferramentas de desenho: Aplicar cria um novo objeto Gerber "
                + "(\"_edit\") igual ao original; Cancelar descarta a sessao.");
        note.setWrapText(true);
        selectionLabel.setWrapText(true);
        showSelection(0, java.util.List.of());

        Button applyButton = new Button("Aplicar");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> onApply.run());
        Button cancelButton = new Button("Cancelar");
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> onCancel.run());

        root = new VBox(10, info, help, selectionLabel);
        if (shapesApproximated) {
            Label approximated = new Label("Objeto restaurado de projeto: as formas individuais nao foram salvas, "
                    + "entao pads/trilhas que se tocam na mesma abertura aparecem como uma forma so, "
                    + "e regioes nao sao selecionaveis.");
            approximated.setWrapText(true);
            approximated.getStyleClass().add("form-error-label");
            root.getChildren().add(approximated);
        }
        root.getChildren().addAll(note, applyButton, cancelButton);
        root.setPadding(new Insets(12));
    }

    Node node() {
        return root;
    }

    void showSelection(int shapeCount, Collection<String> apertureCodes) {
        if (shapeCount == 0) {
            selectionLabel.setText("Selecao: nenhuma forma");
            return;
        }
        String apertures = apertureCodes.stream()
                .map(code -> GerberShape.REGION_APERTURE.equals(code) ? "Regiao" : "D" + code)
                .collect(Collectors.joining(", "));
        selectionLabel.setText("Selecao: " + shapeCount + " forma(s) - " + apertures);
    }
}
