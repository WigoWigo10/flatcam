package org.flatcam.fx;

import java.util.List;
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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.flatcam.cam.convert.ExtractDrills;

/**
 * appTools/ToolExtractDrills.py: drills at the flashed pads of a Gerber. Python's defaults: fixed 0.5 hole,
 * 80% proportion, 0.2 ring for every kind of pad, only round pads selected.
 */
final class ExtractDrillsToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** Creates the Excellon; returns an error message, or null on success. */
        String extract(TreeItem<String> gerber, ExtractDrills.Options options);
    }

    private ExtractDrillsToolPanel() {
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

        CheckBox circular = new CheckBox(I18n.text("ui.f0ecde8a39fb"));
        CheckBox oblong = new CheckBox(I18n.text("ui.3f7f397308be"));
        CheckBox square = new CheckBox(I18n.text("ui.bcfd287153a7"));
        CheckBox rectangular = new CheckBox(I18n.text("ui.cb6a4f90cace"));
        CheckBox other = new CheckBox(I18n.text("ui.e6031e8fb508"));
        circular.setSelected(true);

        ToggleGroup modes = new ToggleGroup();
        RadioButton fixed = new RadioButton(I18n.text("ui.ef7d89f30019"));
        RadioButton proportional = new RadioButton(I18n.text("ui.fecd5dfb1d1b"));
        RadioButton ring = new RadioButton(I18n.text("ui.5ec4450f48fc"));
        for (RadioButton radio : List.of(fixed, proportional, ring)) {
            radio.setToggleGroup(modes);
        }
        fixed.setSelected(true);

        TextField fixedDiameter = field(ToolDefaults.text("extract.fixeddiameter"));
        TextField factor = field(ToolDefaults.text("extract.factor"));
        TextField ringCircular = field(ToolDefaults.text("extract.ringcircular"));
        TextField ringOblong = field(ToolDefaults.text("extract.ringoblong"));
        TextField ringSquare = field(ToolDefaults.text("extract.ringsquare"));
        TextField ringRectangular = field(ToolDefaults.text("extract.ringrectangular"));
        TextField ringOther = field(ToolDefaults.text("extract.ringother"));

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        ColumnConstraints wide = new ColumnConstraints();
        wide.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(wide, new ColumnConstraints());
        grid.addRow(0, new Label(I18n.text("ui.87c58734c8b6")), fixedDiameter);
        grid.addRow(1, new Label(I18n.text("ui.6c63941cefbe")), factor);
        grid.addRow(2, new Label(I18n.text("ui.f81b6872474e")), ringCircular);
        grid.addRow(3, new Label(I18n.text("ui.7f15c9a99719")), ringOblong);
        grid.addRow(4, new Label(I18n.text("ui.fc7e76a93a2b")), ringSquare);
        grid.addRow(5, new Label(I18n.text("ui.09580507ab6f")), ringRectangular);
        grid.addRow(6, new Label(I18n.text("ui.0140900a7611")), ringOther);
        fixedDiameter.disableProperty().bind(fixed.selectedProperty().not());
        factor.disableProperty().bind(proportional.selectedProperty().not());
        for (TextField ringField : List.of(ringCircular, ringOblong, ringSquare, ringRectangular, ringOther)) {
            ringField.disableProperty().bind(ring.selectedProperty().not());
        }

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button extract = new Button(I18n.text("ui.4b125481a6e2"));
        extract.setMaxWidth(Double.MAX_VALUE);
        extract.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                ExtractDrills.Mode mode = fixed.isSelected() ? ExtractDrills.Mode.FIXED
                        : proportional.isSelected() ? ExtractDrills.Mode.PROPORTIONAL : ExtractDrills.Mode.RING;
                ExtractDrills.Options options = new ExtractDrills.Options(mode, number(fixedDiameter, 0.5),
                        number(factor, 80) / 100.0, number(ringCircular, 0.2), number(ringOblong, 0.2),
                        number(ringSquare, 0.2), number(ringRectangular, 0.2), number(ringOther, 0.2),
                        circular.isSelected(), oblong.isSelected(), square.isSelected(), rectangular.isSelected(),
                        other.isSelected());
                String error = host.extract(gerber.getValue(), options);
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.63aab0805458")), gerber,
                new Label(I18n.text("ui.a14063dd4ab4")), new HBox(10, circular, oblong, square),
                new HBox(10, rectangular, other),
                new Label(I18n.text("ui.77d8888074ba")), new HBox(10, fixed, proportional, ring),
                grid, extract, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static TextField field(String value) {
        TextField field = new TextField(value);
        field.setPrefColumnCount(6);
        return field;
    }

    private static double number(TextField field, double ignored) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }
}
