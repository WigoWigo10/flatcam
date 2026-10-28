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
        RadioButton auto = new RadioButton(I18n.text("ui.b803f24d52ed"));
        RadioButton manual = new RadioButton("Manual");
        auto.setToggleGroup(modes);
        manual.setToggleGroup(modes);
        auto.setSelected(true);
        ToggleGroup seconds = new ToggleGroup();
        RadioButton up = new RadioButton(I18n.text("ui.60b0e7da6fab"));
        RadioButton down = new RadioButton(I18n.text("ui.64c4c44e2cd7"));
        RadioButton none = new RadioButton(I18n.text("ui.496a53a0f11e"));
        for (RadioButton radio : List.of(up, down, none)) {
            radio.setToggleGroup(seconds);
        }
        up.setSelected(true);
        ToggleGroup types = new ToggleGroup();
        RadioButton circular = new RadioButton("Circular");
        RadioButton cross = new RadioButton(I18n.text("ui.52f957fe645a"));
        RadioButton chess = new RadioButton(I18n.text("ui.896470333ae3"));
        for (RadioButton radio : List.of(circular, cross, chess)) {
            radio.setToggleGroup(types);
        }
        circular.setSelected(true);
        margin.disableProperty().bind(auto.selectedProperty().not());
        thickness.disableProperty().bind(cross.selectedProperty().not());

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.addRow(0, new Label(I18n.text("ui.999e8aab92fc")), size);
        grid.addRow(1, new Label(I18n.text("ui.e0235f412f3e")), margin);
        grid.addRow(2, new Label(I18n.text("ui.ac41340996a1")), thickness);

        List<Coordinate> used = new ArrayList<>();
        Label coordinates = new Label(I18n.text("ui.7e8b7c5a6acb"));
        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Runnable finish = () -> {
            StringBuilder text = new StringBuilder(I18n.text("ui.c82eb14352d8"));
            for (Coordinate point : used) {
                text.append(String.format(java.util.Locale.ROOT, " (%.4f, %.4f)", point.x, point.y));
            }
            coordinates.setText(text.toString());
            status.setText("");
        };

        Button add = new Button(I18n.text("ui.e755a5e35044"));
        add.setMaxWidth(Double.MAX_VALUE);
        Button addOpenings = new Button(I18n.text("ui.7e24e72d2548"));
        addOpenings.setMaxWidth(Double.MAX_VALUE);

        add.setOnAction(event -> {
            host.cancelPick();
            errorLabel.setText("");
            try {
                if (copper.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                double fiducialSize = number(size);
                double line = number(thickness);
                Fiducials.Type type = circular.isSelected() ? Fiducials.Type.CIRCULAR
                        : cross.isSelected() ? Fiducials.Type.CROSS : Fiducials.Type.CHESS;
                if (auto.isSelected()) {
                    double[] bounds = host.bounds(copper.getValue());
                    if (bounds == null) {
                        throw new IllegalArgumentException(I18n.text("ui.cdb3eee11a83"));
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
                    String[] names = {I18n.text("ui.8df47945ffc7"), I18n.text("ui.dd723e4e3a18"),
                            I18n.text("ui.bdb1a3f937aa")};
                    Consumer<Coordinate>[] next = new Consumer[1];
                    next[0] = point -> {
                        if (point == null) {
                            status.setText("Cancelado.");
                            return;
                        }
                        clicks.add(point);
                        if (clicks.size() < wanted) {
                            status.setText(I18n.text("ui.5c42089ff428") + names[clicks.size()] + "...");
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
                    status.setText(I18n.text("ui.5c42089ff428") + names[0] + "...");
                    host.pickPoint(next[0]);
                }
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        addOpenings.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (mask.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.6a9937095c55"));
                }
                if (used.isEmpty()) {
                    throw new IllegalArgumentException(I18n.text("ui.d2a508ea615a"));
                }
                String error = host.addOpenings(mask.getValue(), List.copyOf(used), number(size) * 2);
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> {
            host.cancelPick();
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.2862ceba4fb2")), copper, grid,
                new Label(I18n.text("ui.be143e43333f")), new HBox(10, auto, manual),
                new Label(I18n.text("ui.81b777698965")), new HBox(10, up, down, none),
                new Label(I18n.text("ui.e369d261ecd1")), new HBox(10, circular, cross, chess),
                add, status, coordinates,
                new Label(I18n.text("ui.a778118eaeae")), mask, addOpenings, errorLabel, close);
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
