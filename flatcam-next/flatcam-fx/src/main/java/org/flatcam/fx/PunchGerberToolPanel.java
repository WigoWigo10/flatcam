package org.flatcam.fx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.convert.ExtractDrills;

/**
 * appTools/ToolPunchGerber.py: holes in the pads of a Gerber, from an Excellon or sized like Extract Drills.
 * Python's defaults: fixed 0.5 hole, 80% proportion, 0.2 ring, only round pads.
 */
final class PunchGerberToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        List<TreeItem<String>> excellons();

        TreeItem<String> initialGerber();

        /** Aperture code to a short description, in file order. */
        Map<String, String> apertures(TreeItem<String> gerber);

        /** @param excellon null unless the method is "from Excellon"; returns an error message or null */
        String punch(TreeItem<String> gerber, TreeItem<String> excellon, ExtractDrills.Options options,
                     Set<String> apertures);
    }

    private PunchGerberToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> gerber = combo(host.gerbers());
        gerber.getSelectionModel().select(host.initialGerber());
        if (gerber.getValue() == null) {
            gerber.getSelectionModel().selectFirst();
        }
        ComboBox<TreeItem<String>> excellon = combo(host.excellons());
        excellon.getSelectionModel().selectFirst();

        ListView<String> apertureList = new ListView<>();
        ToolDescriptions.apply(apertureList,I18n.text("ui.e528deccc59a"), I18n.text("ui.156a2cc176e1"));
        apertureList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        apertureList.setPrefHeight(110);
        Map<String, String> described = new LinkedHashMap<>();
        Runnable fillApertures = () -> {
            described.clear();
            if (gerber.getValue() != null) {
                described.putAll(host.apertures(gerber.getValue()));
            }
            apertureList.getItems().setAll(described.keySet());
            apertureList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(String code, boolean empty) {
                    super.updateItem(code, empty);
                    setText(empty || code == null ? null : code + "  -  " + described.get(code));
                }
            });
            apertureList.getSelectionModel().selectAll();
        };
        gerber.valueProperty().addListener((o, a, b) -> fillApertures.run());
        fillApertures.run();
        Button all = new Button(I18n.text("ui.bd02b9a7d71d"));
        all.setOnAction(event -> apertureList.getSelectionModel().selectAll());
        Button none = new Button(I18n.text("ui.496a53a0f11e"));
        none.setOnAction(event -> apertureList.getSelectionModel().clearSelection());

        ToggleGroup methods = new ToggleGroup();
        RadioButton fromExcellon = new RadioButton("Excellon");
        RadioButton fixed = new RadioButton(I18n.text("ui.ef7d89f30019"));
        RadioButton ring = new RadioButton(I18n.text("ui.5ec4450f48fc"));
        RadioButton proportional = new RadioButton(I18n.text("ui.fecd5dfb1d1b"));
        for (RadioButton radio : List.of(fromExcellon, fixed, ring, proportional)) {
            radio.setToggleGroup(methods);
        }
        fixed.setSelected(true);

        CheckBox circular = new CheckBox(I18n.text("ui.f0ecde8a39fb"));
        CheckBox oblong = new CheckBox(I18n.text("ui.3f7f397308be"));
        CheckBox square = new CheckBox(I18n.text("ui.bcfd287153a7"));
        CheckBox rectangular = new CheckBox(I18n.text("ui.cb6a4f90cace"));
        CheckBox other = new CheckBox(I18n.text("ui.e6031e8fb508"));
        circular.setSelected(true);

        TextField fixedDiameter = field(ToolDefaults.text("punch.fixeddiameter"));
        TextField factor = field(ToolDefaults.text("punch.factor"));
        TextField ringCircular = field(ToolDefaults.text("punch.ringcircular"));
        TextField ringOblong = field(ToolDefaults.text("punch.ringoblong"));
        TextField ringSquare = field(ToolDefaults.text("punch.ringsquare"));
        TextField ringRectangular = field(ToolDefaults.text("punch.ringrectangular"));
        TextField ringOther = field(ToolDefaults.text("punch.ringother"));
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
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
        Label excellonLabel = new Label("Excellon:");
        VBox sizing = new VBox(8, new Label(I18n.text("ui.a14063dd4ab4")), new HBox(10, circular, oblong, square),
                new HBox(10, rectangular, other), grid);
        excellonLabel.visibleProperty().bind(fromExcellon.selectedProperty());
        excellonLabel.managedProperty().bind(fromExcellon.selectedProperty());
        excellon.visibleProperty().bind(fromExcellon.selectedProperty());
        excellon.managedProperty().bind(fromExcellon.selectedProperty());
        sizing.visibleProperty().bind(fromExcellon.selectedProperty().not());
        sizing.managedProperty().bind(fromExcellon.selectedProperty().not());

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button punch = new Button(I18n.text("ui.934d5301f1b3"));
        punch.setMaxWidth(Double.MAX_VALUE);
        punch.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                Set<String> chosen = Set.copyOf(apertureList.getSelectionModel().getSelectedItems());
                if (chosen.isEmpty()) {
                    throw new IllegalArgumentException(I18n.text("ui.554a9d2d8772"));
                }
                TreeItem<String> drills = null;
                if (fromExcellon.isSelected()) {
                    drills = excellon.getValue();
                    if (drills == null) {
                        throw new IllegalArgumentException(I18n.text("ui.1b5ee389225d"));
                    }
                }
                ExtractDrills.Mode mode = proportional.isSelected() ? ExtractDrills.Mode.PROPORTIONAL
                        : ring.isSelected() ? ExtractDrills.Mode.RING : ExtractDrills.Mode.FIXED;
                ExtractDrills.Options options = new ExtractDrills.Options(mode, number(fixedDiameter),
                        number(factor) / 100.0, number(ringCircular), number(ringOblong), number(ringSquare),
                        number(ringRectangular), number(ringOther), circular.isSelected(), oblong.isSelected(),
                        square.isSelected(), rectangular.isSelected(), other.isSelected());
                String error = host.punch(gerber.getValue(), drills, options, chosen);
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
                new Label(I18n.text("ui.d498d2460bd5")), gerber,
                new Label("Apertures:"), apertureList, new HBox(6, all, none),
                new Label(I18n.text("ui.f70d9060ff80")), new HBox(10, fromExcellon, fixed), new HBox(10, ring, proportional),
                excellonLabel, excellon, sizing, punch, errorLabel, close);
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
