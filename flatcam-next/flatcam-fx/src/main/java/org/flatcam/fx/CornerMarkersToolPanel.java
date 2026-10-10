package org.flatcam.fx;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.convert.CornerMarkers;

/**
 * appTools/ToolCorners.py: markers (and optional drills) at the corners of a Gerber. Python's defaults: 0.1 thick,
 * 3.0 long, margin 0, safe corners, 0.5 drill, no corner selected.
 */
final class CornerMarkersToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** Creates the Gerber with the markers; returns an error message, or null on success. */
        String addMarkers(TreeItem<String> gerber, Set<CornerMarkers.Corner> corners, CornerMarkers.Style style,
                          double thickness, double length, double margin);

        /** Creates an Excellon with a hole at each marker centre; error message or null. */
        String addDrills(TreeItem<String> gerber, Set<CornerMarkers.Corner> corners, double thickness, double margin,
                         double diameter);
    }

    private CornerMarkersToolPanel() {
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

        TextField thickness = field(ToolDefaults.text("corners.thickness"));
        TextField length = field(ToolDefaults.text("corners.length"));
        TextField margin = field(ToolDefaults.text("corners.margin"));
        TextField drill = field(ToolDefaults.text("corners.drill"));
        ToggleGroup styles = new ToggleGroup();
        RadioButton safe = new RadioButton("Canto (L)");
        RadioButton cross = new RadioButton("Cruz");
        safe.setToggleGroup(styles);
        cross.setToggleGroup(styles);
        safe.setSelected(true);

        CheckBox topLeft = new CheckBox("Superior esq.");
        CheckBox topRight = new CheckBox("Superior dir.");
        CheckBox bottomLeft = new CheckBox("Inferior esq.");
        CheckBox bottomRight = new CheckBox("Inferior dir.");
        CheckBox all = new CheckBox("Todos");
        all.selectedProperty().addListener((o, a, selected) -> {
            for (CheckBox box : List.of(topLeft, topRight, bottomLeft, bottomRight)) {
                box.setSelected(selected);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.addRow(0, new Label("Espessura:"), thickness);
        grid.addRow(1, new Label("Comprimento:"), length);
        grid.addRow(2, new Label("Margem:"), margin);
        grid.addRow(3, new Label("Diametro da broca:"), drill);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        java.util.function.Supplier<Set<CornerMarkers.Corner>> chosen = () -> {
            Set<CornerMarkers.Corner> corners = EnumSet.noneOf(CornerMarkers.Corner.class);
            if (topLeft.isSelected()) {
                corners.add(CornerMarkers.Corner.TOP_LEFT);
            }
            if (topRight.isSelected()) {
                corners.add(CornerMarkers.Corner.TOP_RIGHT);
            }
            if (bottomLeft.isSelected()) {
                corners.add(CornerMarkers.Corner.BOTTOM_LEFT);
            }
            if (bottomRight.isSelected()) {
                corners.add(CornerMarkers.Corner.BOTTOM_RIGHT);
            }
            return corners;
        };

        Button add = new Button("Adicionar marcadores");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
                }
                String error = host.addMarkers(gerber.getValue(), chosen.get(),
                        safe.isSelected() ? CornerMarkers.Style.CORNER : CornerMarkers.Style.CROSS, number(thickness),
                        number(length), number(margin));
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Valor numerico invalido");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button drills = new Button("Criar furos nos cantos");
        drills.setMaxWidth(Double.MAX_VALUE);
        drills.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
                }
                String error = host.addDrills(gerber.getValue(), chosen.get(), number(thickness), number(margin),
                        number(drill));
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Valor numerico invalido");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8, new Label("Gerber:"), gerber, grid,
                new Label("Tipo:"), new HBox(10, safe, cross),
                new Label("Cantos:"), all, new HBox(10, topLeft, topRight), new HBox(10, bottomLeft, bottomRight),
                add, drills, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static TextField field(String value) {
        TextField field = new TextField(value);
        field.setPrefColumnCount(6);
        return field;
    }

    private static double number(TextField field) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }
}
