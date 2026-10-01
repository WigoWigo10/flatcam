package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolAlignObjects.py: aligns a Gerber or Excellon (the "aligned" object) to another one (the "aligner") by
 * clicking pads or drills. Single point moves it; dual point also rotates it. Python's default is single point.
 */
final class AlignObjectsToolPanel {

    interface Host {
        /** Gerbers and Excellons. */
        List<TreeItem<String>> objects();

        TreeItem<String> initialObject();

        /** The pad or drill centre of {@code object} at the clicked point, or null when none is there. */
        Coordinate centerAt(TreeItem<String> object, Coordinate click);

        /** Takes the next click on the plot; {@code null} means the pick was cancelled. */
        void pickPoint(Consumer<Coordinate> onPoint);

        void cancelPick();

        /** Moves {@code aligned} so the points land (see {@code AlignObjects.plan}); error message or null. */
        String align(TreeItem<String> aligned, List<Coordinate> points);
    }

    private AlignObjectsToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> aligned = combo(host.objects());
        aligned.getSelectionModel().select(host.initialObject());
        if (aligned.getValue() == null) {
            aligned.getSelectionModel().selectFirst();
        }
        ComboBox<TreeItem<String>> aligner = combo(host.objects());
        for (TreeItem<String> candidate : host.objects()) {
            if (candidate != aligned.getValue()) {
                aligner.getSelectionModel().select(candidate);
                break;
            }
        }
        if (aligner.getValue() == null) {
            aligner.getSelectionModel().selectFirst();
        }

        ToggleGroup types = new ToggleGroup();
        RadioButton single = new RadioButton("Um ponto (translada)");
        RadioButton dual = new RadioButton("Dois pontos (translada e gira)");
        single.setToggleGroup(types);
        dual.setToggleGroup(types);
        single.setSelected(true);

        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button align = new Button("Alinhar (clique nos pontos)");
        align.setMaxWidth(Double.MAX_VALUE);
        align.setOnAction(event -> {
            host.cancelPick();
            errorLabel.setText("");
            TreeItem<String> moved = aligned.getValue();
            TreeItem<String> reference = aligner.getValue();
            if (moved == null || reference == null) {
                errorLabel.setText("Carregue os dois objetos");
                return;
            }
            if (moved == reference) {
                errorLabel.setText("Escolha objetos diferentes");
                return;
            }
            int wanted = dual.isSelected() ? 4 : 2;
            List<Coordinate> centres = new ArrayList<>();
            String[] prompts = {
                    "Primeiro ponto: clique num pad ou furo do objeto a alinhar (" + moved.getValue() + ").",
                    "Primeiro destino: clique no pad ou furo correspondente de " + reference.getValue() + ".",
                    "Segundo ponto: clique em outro pad ou furo de " + moved.getValue() + ".",
                    "Segundo destino: clique no pad ou furo correspondente de " + reference.getValue() + "."};
            @SuppressWarnings("unchecked")
            Consumer<Coordinate>[] next = new Consumer[1];
            next[0] = click -> {
                if (click == null) {
                    status.setText("Cancelado.");
                    return;
                }
                // The 1st and 3rd clicks are on the object being aligned, the 2nd and 4th on the reference.
                TreeItem<String> target = centres.size() % 2 == 0 ? moved : reference;
                Coordinate centre = host.centerAt(target, click);
                if (centre == null) {
                    errorLabel.setText("Nao ha pad ou furo de " + target.getValue() + " nesse ponto: clique em um.");
                    host.pickPoint(next[0]);
                    return;
                }
                errorLabel.setText("");
                centres.add(centre);
                if (centres.size() < wanted) {
                    status.setText(prompts[centres.size()]);
                    host.pickPoint(next[0]);
                    return;
                }
                status.setText("");
                String error = host.align(moved, List.copyOf(centres));
                errorLabel.setText(error == null ? "" : error);
            };
            status.setText(prompts[0]);
            host.pickPoint(next[0]);
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> {
            host.cancelPick();
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label("Objeto a alinhar:"), aligned,
                new Label("Objeto de referencia:"), aligner,
                new Label("Tipo:"), new VBox(4, single, dual),
                align, status, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static ComboBox<TreeItem<String>> combo(List<TreeItem<String>> items) {
        ComboBox<TreeItem<String>> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(TreeItem<String> item) {
                return item == null ? "" : item.getValue();
            }

            @Override
            public TreeItem<String> fromString(String text) {
                return null;
            }
        });
        return combo;
    }
}
