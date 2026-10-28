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
        RadioButton safe = new RadioButton(I18n.text("ui.ad6151ff3968"));
        RadioButton cross = new RadioButton(I18n.text("ui.52f957fe645a"));
        safe.setToggleGroup(styles);
        cross.setToggleGroup(styles);
        safe.setSelected(true);

        CheckBox topLeft = new CheckBox(I18n.text("ui.ce333599fd1b"));
        CheckBox topRight = new CheckBox(I18n.text("ui.89fa85221098"));
        CheckBox bottomLeft = new CheckBox(I18n.text("ui.85b40fdb8382"));
        CheckBox bottomRight = new CheckBox(I18n.text("ui.8899ce2b53a3"));
        CheckBox all = new CheckBox(I18n.text("ui.bd02b9a7d71d"));
        all.selectedProperty().addListener((o, a, selected) -> {
            for (CheckBox box : List.of(topLeft, topRight, bottomLeft, bottomRight)) {
                box.setSelected(selected);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.addRow(0, new Label(I18n.text("ui.34dd1326833d")), thickness);
        grid.addRow(1, new Label(I18n.text("ui.0b431cc7476e")), length);
        grid.addRow(2, new Label(I18n.text("ui.16bab5b0d1de")), margin);
        grid.addRow(3, new Label(I18n.text("ui.8e5a2efb8f85")), drill);

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

        Button add = new Button(I18n.text("ui.138d5b0bf4c0"));
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                String error = host.addMarkers(gerber.getValue(), chosen.get(),
                        safe.isSelected() ? CornerMarkers.Style.CORNER : CornerMarkers.Style.CROSS, number(thickness),
                        number(length), number(margin));
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button drills = new Button(I18n.text("ui.b5bf33d64d67"));
        drills.setMaxWidth(Double.MAX_VALUE);
        drills.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                String error = host.addDrills(gerber.getValue(), chosen.get(), number(thickness), number(margin),
                        number(drill));
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8, new Label("Gerber:"), gerber, grid,
                new Label(I18n.text("ui.e369d261ecd1")), new HBox(10, safe, cross),
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
