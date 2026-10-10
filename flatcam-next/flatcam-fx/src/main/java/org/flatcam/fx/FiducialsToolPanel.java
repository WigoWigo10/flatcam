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
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.convert.Fiducials;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolFiducials.py: fiducial marks on a copper Gerber, and 2x-size openings for them on a solder-mask
 * Gerber. Python's defaults: 1.0 size, 1.0 margin, automatic, second mark up, circular, 0.25 cross line.
 */
final class FiducialsToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** {xmin, ymin, xmax, ymax} of the Gerber, or null. */
        double[] bounds(TreeItem<String> gerber);

        /** Creates the Gerber with the marks; returns an error message, or null on success. */
        String add(TreeItem<String> gerber, List<Coordinate> points, Fiducials.Type type, double size,
                   double thickness);

        /** Creates a solder-mask Gerber with round openings of {@code diameter}; error message or null. */
        String addOpenings(TreeItem<String> gerber, List<Coordinate> points, double diameter);

        /** Takes the next click on the plot; {@code null} means the pick was cancelled. */
        void pickPoint(Consumer<Coordinate> onPoint);

        void cancelPick();
    }

    private FiducialsToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> copper = combo(host.gerbers());
        copper.getSelectionModel().select(host.initialGerber());
        if (copper.getValue() == null) {
            copper.getSelectionModel().selectFirst();
        }
        ComboBox<TreeItem<String>> mask = combo(host.gerbers());

        TextField size = field(ToolDefaults.text("fiducials.size"));
        TextField margin = field(ToolDefaults.text("fiducials.margin"));
        TextField thickness = field(ToolDefaults.text("fiducials.thickness"));
        ToggleGroup modes = new ToggleGroup();
        RadioButton auto = new RadioButton("Automatico");
        RadioButton manual = new RadioButton("Manual");
        auto.setToggleGroup(modes);
        manual.setToggleGroup(modes);
        auto.setSelected(true);
        ToggleGroup seconds = new ToggleGroup();
        RadioButton up = new RadioButton("Acima");
        RadioButton down = new RadioButton("Abaixo");
        RadioButton none = new RadioButton("Nenhum");
        for (RadioButton radio : List.of(up, down, none)) {
            radio.setToggleGroup(seconds);
        }
        up.setSelected(true);
        ToggleGroup types = new ToggleGroup();
        RadioButton circular = new RadioButton("Circular");
        RadioButton cross = new RadioButton("Cruz");
        RadioButton chess = new RadioButton("Xadrez");
        for (RadioButton radio : List.of(circular, cross, chess)) {
            radio.setToggleGroup(types);
        }
        circular.setSelected(true);
        margin.disableProperty().bind(auto.selectedProperty().not());
        thickness.disableProperty().bind(cross.selectedProperty().not());

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.addRow(0, new Label("Tamanho:"), size);
        grid.addRow(1, new Label("Margem (auto):"), margin);
        grid.addRow(2, new Label("Espessura (cruz):"), thickness);

        List<Coordinate> used = new ArrayList<>();
        Label coordinates = new Label("Ultimos pontos: -");
        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Runnable finish = () -> {
            StringBuilder text = new StringBuilder("Ultimos pontos:");
            for (Coordinate point : used) {
                text.append(String.format(java.util.Locale.ROOT, " (%.4f, %.4f)", point.x, point.y));
            }
            coordinates.setText(text.toString());
            status.setText("");
        };

        Button add = new Button("Adicionar fiduciais");
        add.setMaxWidth(Double.MAX_VALUE);
        Button addOpenings = new Button("Adicionar aberturas na mascara");
        addOpenings.setMaxWidth(Double.MAX_VALUE);

        add.setOnAction(event -> {
            host.cancelPick();
            errorLabel.setText("");
            try {
                if (copper.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
                }
                double fiducialSize = number(size);
                double line = number(thickness);
                Fiducials.Type type = circular.isSelected() ? Fiducials.Type.CIRCULAR
                        : cross.isSelected() ? Fiducials.Type.CROSS : Fiducials.Type.CHESS;
                if (auto.isSelected()) {
                    double[] bounds = host.bounds(copper.getValue());
                    if (bounds == null) {
                        throw new IllegalArgumentException("O Gerber nao tem geometria");
                    }
                    Fiducials.SecondPoint second = up.isSelected() ? Fiducials.SecondPoint.UP
                            : down.isSelected() ? Fiducials.SecondPoint.DOWN : Fiducials.SecondPoint.NONE;
                    List<Coordinate> points = Fiducials.autoPoints(bounds, number(margin), second);
                    String error = host.add(copper.getValue(), points, type, fiducialSize, line);
                    errorLabel.setText(error == null ? "" : error);
                    if (error == null) {
                        used.clear();
                        used.addAll(points);
                        finish.run();
                    }
                } else {
                    int wanted = none.isSelected() ? 2 : 3;
                    List<Coordinate> clicks = new ArrayList<>();
                    String[] names = {"o primeiro fiducial (inferior esquerdo)", "o ultimo fiducial (superior direito)",
                            "o segundo fiducial (superior esquerdo ou inferior direito)"};
                    Consumer<Coordinate>[] next = new Consumer[1];
                    next[0] = point -> {
                        if (point == null) {
                            status.setText("Cancelado.");
                            return;
                        }
                        clicks.add(point);
                        if (clicks.size() < wanted) {
                            status.setText("Clique para " + names[clicks.size()] + "...");
                            host.pickPoint(next[0]);
                            return;
                        }
                        String error = host.add(copper.getValue(), List.copyOf(clicks), type, fiducialSize, line);
                        errorLabel.setText(error == null ? "" : error);
                        if (error == null) {
                            used.clear();
                            used.addAll(clicks);
                            finish.run();
                        } else {
                            status.setText("");
                        }
                    };
                    status.setText("Clique para " + names[0] + "...");
                    host.pickPoint(next[0]);
                }
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Valor numerico invalido");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        addOpenings.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (mask.getValue() == null) {
                    throw new IllegalArgumentException("Carregue o Gerber da mascara de solda");
                }
                if (used.isEmpty()) {
                    throw new IllegalArgumentException("Adicione os fiduciais primeiro: as aberturas usam os mesmos pontos");
                }
                String error = host.addOpenings(mask.getValue(), List.copyOf(used), number(size) * 2);
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Valor numerico invalido");
            }
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> {
            host.cancelPick();
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label("Gerber de cobre:"), copper, grid,
                new Label("Posicao:"), new HBox(10, auto, manual),
                new Label("Segundo fiducial (automatico):"), new HBox(10, up, down, none),
                new Label("Tipo:"), new HBox(10, circular, cross, chess),
                add, status, coordinates,
                new Label("Mascara de solda (abertura = 2x o tamanho):"), mask, addOpenings, errorLabel, close);
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

    private static TextField field(String value) {
        TextField field = new TextField(value);
        field.setPrefColumnCount(6);
        return field;
    }

    private static double number(TextField field) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }
}
