package org.flatcam.fx;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * First slice of the Gerber Editor (CONTEXTO_E_PROGRESSO.md section 9.4, step
 * 1): only the edit session's Aplicar/Cancelar lifecycle, no drawing tools
 * yet - matches AppGerberEditor.py's toolbar in spirit (Apply/Cancel are the
 * only actions that survive without any select/pad/track/region tool), but
 * this port has none of those tools implemented, so this panel is
 * deliberately just the two buttons plus a status label rather than the
 * legacy apertures table + drawing toolbar.
 */
final class GerberEditToolPanel {

    private GerberEditToolPanel() {
    }

    /**
     * @param onApply  called when "Aplicar" is clicked.
     * @param onCancel called when "Cancelar" is clicked.
     */
    static Node build(String objectName, Runnable onApply, Runnable onCancel) {
        Label info = new Label("Editando: " + objectName);
        Label note = new Label(
                "Sessao de edicao (fatia inicial): ainda sem ferramentas de desenho. "
                        + "Aplicar cria um novo objeto Gerber editado; Cancelar descarta a sessao.");
        note.setWrapText(true);

        Button applyButton = new Button("Aplicar");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> onApply.run());

        Button cancelButton = new Button("Cancelar");
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> onCancel.run());

        VBox box = new VBox(10, info, note, applyButton, cancelButton);
        box.setPadding(new Insets(12));
        return box;
    }
}
