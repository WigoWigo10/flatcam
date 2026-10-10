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
        oz.setPromptText("Valor em oz");
        Label ozMicrons = new Label("= um");
        oz.textProperty().addListener((o, a, b) -> ozMicrons.setText(converted(b, 34.798)));
        TextField mils = new TextField();
        mils.setPromptText("Valor em mils");
        Label milsMicrons = new Label("= um");
        mils.textProperty().addListener((o, a, b) -> milsMicrons.setText(converted(b, 25.4)));

        TextField thickness = new TextField(ToolDefaults.text("etch.thickness"));
        ToggleGroup ratio = new ToggleGroup();
        RadioButton byFactor = new RadioButton("Fator de corrosao");
        RadioButton byEtchant = new RadioButton("Lista de corrosivos");
        RadioButton manual = new RadioButton("Deslocamento manual");
        for (RadioButton radio : List.of(byFactor, byEtchant, manual)) {
            radio.setToggleGroup(ratio);
        }
        byFactor.setSelected(true);
        TextField factor = new TextField();
        factor.setPromptText("Numero (ex.: 0.25)");
        ComboBox<String> etchants = new ComboBox<>();
        etchants.getItems().setAll("CuCl2", "Fe3Cl", "Banhos alcalinos");
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
        grid.addRow(2, new Label("Espessura do cobre (um):"), thickness);
        grid.addRow(3, new Label("Fator:"), factor);
        grid.addRow(4, new Label("Corrosivo:"), etchants);
        grid.addRow(5, new Label("Deslocamento (um):"), offset);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button compensate = new Button("Compensar");
        compensate.setMaxWidth(Double.MAX_VALUE);
        compensate.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
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
                errorLabel.setText("Valor numerico invalido");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8, new Label("Gerber a compensar:"), gerber, grid,
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
