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
        ToolDescriptions.apply(choices,I18n.text("ui.76fb2f1dd076"), I18n.text("ui.ad06a5a6a674"));
        Button load = new Button(I18n.text("ui.70f253485ae9"));
        load.setId(id + "-load");
        load.setMaxWidth(Double.MAX_VALUE);
        ToolsDatabaseDescriptions.apply(load, "Tools Database", I18n.text("ui.38ffa706774e"));
        Button apply = new Button(I18n.text("ui.fd6ff362d0fb"));
        apply.setId(id + "-apply");
        apply.setMaxWidth(Double.MAX_VALUE);
        ToolsDatabaseDescriptions.apply(apply, I18n.text("ui.728ad1206b55"), I18n.text("ui.8ccd8d5a696a"));
        apply.disableProperty().bind(choices.valueProperty().isNull());
        load.setOnAction(event -> {
            try {
                choices.getItems().setAll(loader.get());
                choices.getSelectionModel().selectFirst();
                error.setText(choices.getItems().isEmpty() ? I18n.text("ui.c934523e6f8a") : "");
            } catch (RuntimeException invalid) { error.setText(invalid.getMessage()); }
        });
        apply.setOnAction(event -> {
            try { transfer.accept(choices.getValue()); error.setText(""); }
            catch (RuntimeException invalid) { error.setText(invalid.getMessage()); }
        });
        Label note = new Label(I18n.text("ui.3f1ce3852335"));
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
