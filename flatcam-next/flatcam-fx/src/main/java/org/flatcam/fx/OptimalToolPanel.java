package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.analysis.MinimumDistance;

/**
 * appTools/ToolOptimal.py: the smallest gap between the copper features of a Gerber, how many pairs sit at it, where,
 * and the other gaps. Python's default precision is 4 decimals. Selecting a location jumps to it and marks it on the
 * plot (Python needs a click on "Locate").
 */
final class OptimalToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** Runs the search as a job; calls one of the two callbacks on the JavaFX thread when it ends. */
        void find(TreeItem<String> gerber, int precision, Consumer<MinimumDistance.Result> onResult,
                  Consumer<String> onError);

        /** Centres the plot on the gap and marks it; {@code null} clears the mark. */
        void locate(MinimumDistance.Pair pair);
    }

    private OptimalToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> gerber = new ComboBox<>();
        gerber.getItems().setAll(host.gerbers());
        gerber.setMaxWidth(Double.MAX_VALUE);
        gerber.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(TreeItem<String> item) {
                return item == null ? "" : item.getValue();
            }

            @Override
            public TreeItem<String> fromString(String text) {
                return null;
            }
        });
        gerber.getSelectionModel().select(host.initialGerber());
        if (gerber.getValue() == null) {
            gerber.getSelectionModel().selectFirst();
        }
        TextField precision = new TextField(ToolDefaults.text("optimal.precision"));
        precision.setPrefColumnCount(4);

        TextField minimum = new TextField("0.0");
        minimum.setEditable(false);
        minimum.setPrefColumnCount(8);
        TextField frequency = new TextField("0");
        frequency.setEditable(false);
        frequency.setPrefColumnCount(5);

        ListView<String> minimumLocations = new ListView<>();
        minimumLocations.setPrefHeight(90);
        ListView<String> otherDistances = new ListView<>();
        otherDistances.setPrefHeight(110);
        ListView<String> otherLocations = new ListView<>();
        otherLocations.setPrefHeight(90);

        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        final MinimumDistance.Result[] current = new MinimumDistance.Result[1];
        final int[] decimals = {4};
        final List<Double> distanceKeys = new ArrayList<>();
        final List<MinimumDistance.Pair> shownMinimum = new ArrayList<>();
        final List<MinimumDistance.Pair> shownOther = new ArrayList<>();

        minimumLocations.getSelectionModel().selectedIndexProperty().addListener((o, was, index) -> {
            if (index.intValue() >= 0 && index.intValue() < shownMinimum.size()) {
                otherLocations.getSelectionModel().clearSelection();
                host.locate(shownMinimum.get(index.intValue()));
            }
        });
        otherLocations.getSelectionModel().selectedIndexProperty().addListener((o, was, index) -> {
            if (index.intValue() >= 0 && index.intValue() < shownOther.size()) {
                minimumLocations.getSelectionModel().clearSelection();
                host.locate(shownOther.get(index.intValue()));
            }
        });
        otherDistances.getSelectionModel().selectedIndexProperty().addListener((o, was, index) -> {
            shownOther.clear();
            otherLocations.getItems().clear();
            if (current[0] != null && index.intValue() >= 0 && index.intValue() < distanceKeys.size()) {
                for (MinimumDistance.Pair pair : current[0].others().get(distanceKeys.get(index.intValue()))) {
                    shownOther.add(pair);
                    otherLocations.getItems().add(describe(pair, decimals[0]));
                }
            }
        });

        Button find = new Button("Encontrar a menor distância");
        find.setMaxWidth(Double.MAX_VALUE);
        find.setOnAction(event -> {
            errorLabel.setText("");
            if (gerber.getValue() == null) {
                errorLabel.setText("Carregue um Gerber");
                return;
            }
            int places;
            try {
                places = Integer.parseInt(precision.getText().trim());
            } catch (NumberFormatException invalid) {
                errorLabel.setText("A precisão deve ser um número inteiro");
                return;
            }
            find.setDisable(true);
            status.setText("Procurando a menor distância entre os elementos de cobre...");
            host.locate(null);
            host.find(gerber.getValue(), places, result -> {
                find.setDisable(false);
                status.setText(result.features() + " elementos de cobre comparados.");
                current[0] = result;
                decimals[0] = places;
                minimum.setText(String.format(Locale.ROOT, "%." + places + "f", result.minimum()));
                frequency.setText(String.valueOf(result.frequency()));
                shownMinimum.clear();
                minimumLocations.getItems().clear();
                for (MinimumDistance.Pair pair : result.minimumLocations()) {
                    shownMinimum.add(pair);
                    minimumLocations.getItems().add(describe(pair, places));
                }
                distanceKeys.clear();
                otherDistances.getItems().clear();
                shownOther.clear();
                otherLocations.getItems().clear();
                for (Map.Entry<Double, List<MinimumDistance.Pair>> entry : result.others().entrySet()) {
                    distanceKeys.add(entry.getKey());
                    otherDistances.getItems().add(String.format(Locale.ROOT, "%." + places + "f   (%d)", entry.getKey(),
                            entry.getValue().size()));
                }
                if (!shownMinimum.isEmpty()) {
                    minimumLocations.getSelectionModel().selectFirst();
                }
            }, message -> {
                find.setDisable(false);
                status.setText("");
                errorLabel.setText(message);
            });
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> {
            host.locate(null);
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label("Gerber a analisar:"), gerber,
                new HBox(6, new Label("Precisão (casas):"), precision),
                find, status,
                new HBox(8, new Label("Distância mínima:"), minimum, new Label("Pares:"), frequency),
                new Label("Locais da menor distância (clique para ir até lá):"), minimumLocations,
                new Label("Outras distâncias (entre parênteses, quantos pares):"), otherDistances,
                new Label("Locais da distância escolhida:"), otherLocations,
                errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static String describe(MinimumDistance.Pair pair, int places) {
        String format = "(%." + places + "f, %." + places + "f)";
        return String.format(Locale.ROOT, format + " ↔ " + format, pair.first().x, pair.first().y, pair.second().x,
                pair.second().y);
    }
}
