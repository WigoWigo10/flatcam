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
        Button load = new Button("Pick from DB");
        load.setId(id + "-load");
        load.setMaxWidth(Double.MAX_VALUE);
        Button apply = new Button("Aplicar ferramenta da base");
        apply.setId(id + "-apply");
        apply.setMaxWidth(Double.MAX_VALUE);
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
