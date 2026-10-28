package org.flatcam.fx;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Status-bar mirror of the console's progress, with the same cancellation action. */
final class CompactJobProgress extends HBox {
    CompactJobProgress(ObservableDoubleValue fraction, ObservableBooleanValue cancelDisabled, Runnable cancelAction) {
        super(6);
        setId("compact-job-progress");
        getStyleClass().add("compact-job-progress");
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Region.USE_PREF_SIZE);
        managedProperty().bind(visibleProperty());

        ProgressBar progress = new ProgressBar();
        progress.setId("compact-job-bar");
        progress.progressProperty().bind(Bindings.createDoubleBinding(
                () -> boundedFraction(fraction.get()), fraction));
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setMinHeight(16);
        progress.setPrefHeight(16);
        progress.setMaxHeight(16);
        progress.setAccessibleText("Progresso da operação");

        Label percentage = new Label();
        percentage.setId("compact-job-percent");
        percentage.getStyleClass().add("progress-percentage");
        percentage.setMouseTransparent(true);
        percentage.textProperty().bind(Bindings.createStringBinding(() -> {
            double value = boundedFraction(fraction.get());
            return value < 0 ? "..." : Math.round(value * 100) + "%";
        }, fraction));

        StackPane indicator = new StackPane(progress, percentage);
        indicator.setMinWidth(88);
        indicator.setPrefWidth(128);
        indicator.setMaxWidth(128);
        indicator.setMinHeight(20);
        indicator.setPrefHeight(20);
        indicator.setMaxHeight(20);
        HBox.setHgrow(indicator, Priority.ALWAYS);
        ToolDescriptions.apply(indicator, "Progresso da operação",
                "Mostra o progresso da etapa atual mesmo com o Terminal recolhido. Uma etapa nova pode reiniciar a porcentagem.\n\n"
                + "Progresso: a animação sem porcentagem indica que a duração ainda não é conhecida; não significa que a operação parou.");

        Button cancel = new Button(null, Icons.cancel(14));
        cancel.setId("compact-job-cancel");
        cancel.getStyleClass().add("job-cancel-button");
        ToolDescriptions.apply(cancel, "Cancelar operação",
                "Solicita a interrupção do trabalho em andamento. O botão fica disponível enquanto há uma operação cancelável.\n\n"
                + "Atenção: o encerramento pode aguardar o cálculo atual. Ações e comandos já concluídos não são desfeitos.");
        cancel.setAccessibleText("Cancelar a operação em andamento");
        cancel.disableProperty().bind(cancelDisabled);
        cancel.visibleProperty().bind(cancel.disableProperty().not());
        cancel.managedProperty().bind(cancel.visibleProperty());
        cancel.setOnAction(event -> cancelAction.run());
        getChildren().addAll(indicator, cancel);
    }

    private static double boundedFraction(double fraction) {
        return !Double.isFinite(fraction) || fraction < 0 ? -1 : Math.min(1, fraction);
    }
}
