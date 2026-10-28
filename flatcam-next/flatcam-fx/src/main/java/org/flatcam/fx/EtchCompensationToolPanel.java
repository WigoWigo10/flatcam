package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
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
import org.flatcam.cam.convert.EtchCompensation;

/**
 * appTools/ToolEtchCompensation.py: grows the copper to make up for the lateral etch. Python's default is an 18 um
 * foil, by etch factor.
 */
final class EtchCompensationToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** Units ("MM" or "IN") of the Gerber, to convert microns. */
        String unitsOf(TreeItem<String> gerber);

        /** Creates the compensated Gerber; returns an error message, or null on success. */
        String compensate(TreeItem<String> gerber, double offset);
    }

    private EtchCompensationToolPanel() {
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

        TextField oz = new TextField();
        oz.setPromptText(I18n.text("ui.f2cd0251837e"));
        Label ozMicrons = new Label("= um");
        oz.textProperty().addListener((o, a, b) -> ozMicrons.setText(converted(b, 34.798)));
        TextField mils = new TextField();
        mils.setPromptText(I18n.text("ui.6b506d13fe3f"));
        Label milsMicrons = new Label("= um");
        mils.textProperty().addListener((o, a, b) -> milsMicrons.setText(converted(b, 25.4)));
        ToolDescriptions.apply(oz, I18n.text("ui.c3018812eeb1"),
                I18n.text("ui.b08d0b440daa"));
        ToolDescriptions.apply(mils, I18n.text("ui.d367b4d1edb3"),
                I18n.text("ui.6886c2ed16c9"));

        TextField thickness = new TextField(ToolDefaults.text("etch.thickness"));
        ToggleGroup ratio = new ToggleGroup();
        RadioButton byFactor = new RadioButton(I18n.text("ui.f9e77c47e0b0"));
        RadioButton byEtchant = new RadioButton(I18n.text("ui.5a8eb626a568"));
        RadioButton manual = new RadioButton(I18n.text("ui.dcca8179d2c6"));
        for (RadioButton radio : List.of(byFactor, byEtchant, manual)) {
            radio.setToggleGroup(ratio);
        }
        byFactor.setSelected(true);
        TextField factor = new TextField();
        factor.setPromptText(I18n.text("ui.d0870cc2adb7"));
        ComboBox<String> etchants = new ComboBox<>();
        etchants.getItems().setAll("CuCl2", "Fe3Cl", I18n.text("ui.dd1a4d4efbad"));
        etchants.getSelectionModel().selectFirst();
        etchants.setMaxWidth(Double.MAX_VALUE);
        TextField offset = new TextField("0");
        factor.disableProperty().bind(byFactor.selectedProperty().not());
        etchants.disableProperty().bind(byEtchant.selectedProperty().not());
        offset.disableProperty().bind(manual.selectedProperty().not());

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.addRow(0, new Label("Oz -> um:"), new HBox(6, oz, ozMicrons));
        grid.addRow(1, new Label("Mils -> um:"), new HBox(6, mils, milsMicrons));
        grid.addRow(2, new Label(I18n.text("ui.7c4043f9dc80")), thickness);
        grid.addRow(3, new Label(I18n.text("ui.02834659a723")), factor);
        grid.addRow(4, new Label("Corrosivo:"), etchants);
        grid.addRow(5, new Label(I18n.text("ui.fd50f5112e67")), offset);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button compensate = new Button(I18n.text("ui.097afebc0dbf"));
        compensate.setMaxWidth(Double.MAX_VALUE);
        compensate.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                String units = host.unitsOf(gerber.getValue());
                double value;
                if (byFactor.isSelected()) {
                    value = EtchCompensation.offsetFromFactor(number(thickness), number(factor), units);
                } else if (byEtchant.isSelected()) {
                    EtchCompensation.Etchant etchant = switch (etchants.getSelectionModel().getSelectedIndex()) {
                        case 0 -> EtchCompensation.Etchant.CUCL2;
                        case 1 -> EtchCompensation.Etchant.FE3CL;
                        default -> EtchCompensation.Etchant.ALKALINE;
                    };
                    value = EtchCompensation.offsetFromEtchant(number(thickness), etchant, units);
                } else {
                    value = EtchCompensation.fromMicrons(number(offset), units);
                }
                String error = host.compensate(gerber.getValue(), value);
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8, new Label(I18n.text("ui.a06199ee1809")), gerber, grid,
                new Label("Metodo:"), new VBox(4, byFactor, byEtchant, manual), compensate, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static String converted(String text, double scale) {
        try {
            return String.format(Locale.ROOT, "= %.4f um", Double.parseDouble(text.trim().replace(',', '.')) * scale);
        } catch (NumberFormatException invalid) {
            return "= um";
        }
    }

    private static double number(TextField field) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }
}
