package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.solderpaste.SolderPaste;

/**
 * appTools/ToolSolderPaste.py: dispensing geometry for the pads of a solder-paste mask Gerber, then the G-code for a
 * paste dispenser (Paste_1). Python's defaults: nozzles 1.0 and 0.3, Z 0.05 / 0.1 / 0.05, travel 0.1, tool change 1.0
 * at (0, 0), feeds 150 / 150 / 1.0, dispenser 300 rpm / 1 s forward and 200 rpm / 1 s reverse.
 */
final class SolderPasteToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        /** Geometry objects that carry per-nozzle paths (from the first step). */
        List<TreeItem<String>> pasteGeometries();

        TreeItem<String> initialGerber();

        /** Creates the dispensing Geometry; returns an error message, or null on success. */
        String createGeometry(TreeItem<String> gerber, List<Double> nozzles);

        /** Asks for a file and creates the CNC Job; returns an error message, or null (also when cancelled). */
        String createJob(TreeItem<String> geometry, SolderPaste.Parameters parameters);
    }

    private SolderPasteToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        SolderPaste.Parameters d = SolderPaste.defaults();

        ListView<Double> nozzles = new ListView<>();
        nozzles.getItems().setAll(1.0, 0.3);
        nozzles.setPrefHeight(90);
        TextField newNozzle = field(ToolDefaults.text("paste.newnozzle"));
        ToolDescriptions.apply(newNozzle,"Diâmetro do bico", "Diâmetro positivo do bico a adicionar à lista de dispensa.\n\nUnidades: unidade do objeto (mm ou in).");
        Button addNozzle = new Button("Adicionar bico");
        Button removeNozzle = new Button("Remover");

        ComboBox<TreeItem<String>> gerber = combo(host.gerbers());
        gerber.getSelectionModel().select(host.initialGerber());
        if (gerber.getValue() == null) {
            gerber.getSelectionModel().selectFirst();
        }
        ComboBox<TreeItem<String>> geometry = combo(host.pasteGeometries());
        geometry.getSelectionModel().selectLast();

        TextField zStart = field(num(d.zStart()));
        TextField zDispense = field(num(d.zDispense()));
        TextField zStop = field(num(d.zStop()));
        TextField zTravel = field(num(d.zTravel()));
        TextField zToolchange = field(num(d.zToolchange()));
        TextField xyToolchange = field("0.0, 0.0");
        TextField feedXY = field(num(d.feedXY()));
        TextField feedZ = field(num(d.feedZ()));
        TextField feedZDispense = field(num(d.feedZDispense()));
        TextField speedForward = field(num(d.speedForward()));
        TextField dwellForward = field(num(d.dwellForward()));
        TextField speedReverse = field(num(d.speedReverse()));
        TextField dwellReverse = field(num(d.dwellReverse()));

        GridPane form = new GridPane();
        form.setHgap(6);
        form.setVgap(6);
        form.addRow(0, new Label("Z inicio da dispensa:"), zStart);
        form.addRow(1, new Label("Z dispensa:"), zDispense);
        form.addRow(2, new Label("Z parada:"), zStop);
        form.addRow(3, new Label("Z deslocamento:"), zTravel);
        form.addRow(4, new Label("Z troca de bico:"), zToolchange);
        form.addRow(5, new Label("X,Y troca de bico:"), xyToolchange);
        form.addRow(6, new Label("Avanco XY:"), feedXY);
        form.addRow(7, new Label("Avanco Z:"), feedZ);
        form.addRow(8, new Label("Avanco Z dispensa:"), feedZDispense);
        form.addRow(9, new Label("Velocidade frente (rpm):"), speedForward);
        form.addRow(10, new Label("Espera frente (s):"), dwellForward);
        form.addRow(11, new Label("Velocidade reversa (rpm):"), speedReverse);
        form.addRow(12, new Label("Espera reversa (s):"), dwellReverse);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        addNozzle.setOnAction(event -> {
            errorLabel.setText("");
            try {
                double diameter = number(newNozzle);
                if (!(diameter > 0)) {
                    throw new IllegalArgumentException("Informe um diametro diferente de zero");
                }
                if (nozzles.getItems().stream().anyMatch(n -> Math.abs(n - diameter) < 1e-9)) {
                    throw new IllegalArgumentException("Cancelado: o bico ja esta na tabela");
                }
                nozzles.getItems().add(diameter);
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Use um numero");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        removeNozzle.setOnAction(event ->
                nozzles.getItems().removeAll(new ArrayList<>(nozzles.getSelectionModel().getSelectedItems())));

        Button createGeometry = new Button("Gerar geometria de dispensa");
        createGeometry.setMaxWidth(Double.MAX_VALUE);
        createGeometry.setOnAction(event -> {
            errorLabel.setText("");
            if (gerber.getValue() == null) {
                errorLabel.setText("Carregue o Gerber da mascara de pasta");
                return;
            }
            String error = host.createGeometry(gerber.getValue(), List.copyOf(nozzles.getItems()));
            errorLabel.setText(error == null ? "" : error);
            geometry.getItems().setAll(host.pasteGeometries());
            geometry.getSelectionModel().selectLast();
        });

        Button createJob = new Button("Gerar CNC Job de pasta");
        createJob.setMaxWidth(Double.MAX_VALUE);
        createJob.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (geometry.getValue() == null) {
                    throw new IllegalArgumentException("Nao ha geometria de pasta: gere-a primeiro");
                }
                double[] xy = xy(xyToolchange);
                SolderPaste.Parameters parameters = new SolderPaste.Parameters(number(zStart), number(zDispense),
                        number(zStop), number(zTravel), number(zToolchange), xy[0], xy[1], number(feedXY),
                        number(feedZ), number(feedZDispense), number(speedForward), number(dwellForward),
                        number(speedReverse), number(dwellReverse));
                String error = host.createJob(geometry.getValue(), parameters);
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Valor numerico invalido");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> onClose.run());

        TitledPane parameters = new TitledPane("Parametros do dispensador", form);
        parameters.setExpanded(true);
        VBox panel = new VBox(8,
                new Label("Bicos (diametros):"), nozzles, new HBox(6, newNozzle, addNozzle, removeNozzle),
                new Label("Gerber da mascara de pasta:"), gerber, createGeometry,
                parameters,
                new Label("Geometria de pasta:"), geometry, createJob,
                new Label("Pre-processador: Paste_1"), errorLabel, close);
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
        field.setPrefColumnCount(8);
        return field;
    }

    private static String num(double value) {
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.1f", value) : String.valueOf(value);
    }

    private static double number(TextField field) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }

    private static double[] xy(TextField field) {
        String[] parts = field.getText().trim().split("\\s*[;]\\s*|\\s*,\\s*(?=-?\\d)", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("X,Y da troca de bico: use dois numeros separados por virgula");
        }
        return new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
    }
}
