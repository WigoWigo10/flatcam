package org.flatcam.fx;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Explicit, non-modal transfer: loading or selecting never modifies CAM parameters. */
final class DatabaseToolPicker {
    private DatabaseToolPicker() { }

    static <T> VBox build(String id, Supplier<List<T>> loader, Consumer<T> transfer, Label error) {
        ComboBox<T> choices = new ComboBox<>();
        choices.setId(id + "-choices");
        choices.setMaxWidth(Double.MAX_VALUE);
        ToolDescriptions.apply(choices,"Ferramenta da base", "Escolha a ferramenta a transferir. Selecionar não muda os parâmetros CAM; use Aplicar ferramenta da base e confira os campos suportados.\n\nAtenção: a base não converte mm/in automaticamente.");
        Button load = new Button("Pick from DB");
        load.setId(id + "-load");
        load.setMaxWidth(Double.MAX_VALUE);
        ToolsDatabaseDescriptions.apply(load, "Tools Database", "Carrega ferramentas compativeis da base aberta ou de um arquivo. Selecione uma ferramenta e use Aplicar para transferir; carregar nao modifica parametros CAM.");
        Button apply = new Button("Aplicar ferramenta da base");
        apply.setId(id + "-apply");
        apply.setMaxWidth(Double.MAX_VALUE);
        ToolsDatabaseDescriptions.apply(apply, "Transferir ferramenta", "Transfere somente os campos suportados para este formulario. Confira a unidade e revise os valores antes de gerar caminhos ou G-code. Esta acao nao altera o arquivo da base.");
        apply.disableProperty().bind(choices.valueProperty().isNull());
        load.setOnAction(event -> {
            try {
                choices.getItems().setAll(loader.get());
                choices.getSelectionModel().selectFirst();
                error.setText(choices.getItems().isEmpty() ? "Nenhuma ferramenta compativel na base." : "");
            } catch (RuntimeException invalid) { error.setText(invalid.getMessage()); }
        });
        apply.setOnAction(event -> {
            try { transfer.accept(choices.getValue()); error.setText(""); }
            catch (RuntimeException invalid) { error.setText(invalid.getMessage()); }
        });
        Label note = new Label("Valores na unidade do trabalho. Confira mm/in; a base nao converte unidades. "
                + "Somente os campos suportados sao transferidos.");
        note.setWrapText(true);
        VBox box = new VBox(6, load, choices, apply, note);
        choices.visibleProperty().bind(choices.itemsProperty().isNotNull().and(
                javafx.beans.binding.Bindings.isNotEmpty(choices.getItems())));
        choices.managedProperty().bind(choices.visibleProperty());
        apply.visibleProperty().bind(choices.visibleProperty());
        apply.managedProperty().bind(apply.visibleProperty());
        return box;
    }
}
