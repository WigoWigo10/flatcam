package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.convert.QrCodeMarker;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolQRCode.py: a QR code of copper squares placed on a Gerber with a click. Python's defaults: version 1,
 * error level L, box size 3, border 4, positive polarity, square mask.
 */
final class QrCodeToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** Takes the next click on the plot; {@code null} means the pick was cancelled. */
        void pickPoint(Consumer<Coordinate> onPoint);

        void cancelPick();

        /** Adds the code centred on {@code centre}; returns an error message, or null on success. */
        String place(TreeItem<String> gerber, QrCodeMarker.Options options, Coordinate centre);
    }

    private QrCodeToolPanel() {
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

        TextArea text = new TextArea();
        text.setPrefRowCount(3);
        text.setWrapText(true);
        TextField version = field(ToolDefaults.text("qrcode.version"));
        TextField boxSize = field(ToolDefaults.text("qrcode.boxsize"));
        TextField border = field(ToolDefaults.text("qrcode.border"));
        ToggleGroup levels = new ToggleGroup();
        RadioButton levelL = new RadioButton("L");
        RadioButton levelM = new RadioButton("M");
        RadioButton levelQ = new RadioButton("Q");
        RadioButton levelH = new RadioButton("H");
        for (RadioButton radio : List.of(levelL, levelM, levelQ, levelH)) {
            radio.setToggleGroup(levels);
        }
        levelL.setSelected(true);
        ToggleGroup polarities = new ToggleGroup();
        RadioButton positive = new RadioButton("Positiva");
        RadioButton negative = new RadioButton("Negativa");
        positive.setToggleGroup(polarities);
        negative.setToggleGroup(polarities);
        positive.setSelected(true);
        ToggleGroup masks = new ToggleGroup();
        RadioButton square = new RadioButton("Quadrada");
        RadioButton rounded = new RadioButton("Arredondada");
        square.setToggleGroup(masks);
        rounded.setToggleGroup(masks);
        square.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.addRow(0, new Label("Versao (1-40):"), version);
        grid.addRow(1, new Label("Tamanho da caixa:"), boxSize);
        grid.addRow(2, new Label("Borda (modulos):"), border);

        Label summary = new Label();
        summary.setWrapText(true);
        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        java.util.function.Supplier<QrCodeMarker.Options> options = () -> {
            QrCodeMarker.ErrorLevel level = levelM.isSelected() ? QrCodeMarker.ErrorLevel.M
                    : levelQ.isSelected() ? QrCodeMarker.ErrorLevel.Q
                    : levelH.isSelected() ? QrCodeMarker.ErrorLevel.H : QrCodeMarker.ErrorLevel.L;
            return new QrCodeMarker.Options(text.getText(), (int) number(version), level, (int) number(boxSize),
                    (int) number(border), negative.isSelected() ? QrCodeMarker.Polarity.NEGATIVE
                    : QrCodeMarker.Polarity.POSITIVE, rounded.isSelected());
        };
        Runnable refreshSummary = () -> {
            try {
                QrCodeMarker.Options current = options.get();
                QrCodeMarker.Code code = QrCodeMarker.encode(current);
                double side = QrCodeMarker.side(code, current);
                double total = side + 2 * current.border() * current.boxSize() / 10.0;
                summary.setText(String.format(Locale.ROOT, "Versao %d, %dx%d modulos, lado %.2f (com a borda %.2f)",
                        code.version(), code.size(), code.size(), side, total));
            } catch (RuntimeException invalid) {
                summary.setText("");
            }
        };
        text.textProperty().addListener((o, a, b) -> refreshSummary.run());
        for (TextField field : List.of(version, boxSize, border)) {
            field.textProperty().addListener((o, a, b) -> refreshSummary.run());
        }
        levels.selectedToggleProperty().addListener((o, a, b) -> refreshSummary.run());

        Button place = new Button("Colocar QR Code (clique no destino)");
        place.setMaxWidth(Double.MAX_VALUE);
        place.setOnAction(event -> {
            host.cancelPick();
            errorLabel.setText("");
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
                }
                QrCodeMarker.Options chosen = options.get();
                QrCodeMarker.encode(chosen);
                status.setText("Clique no ponto de destino (o centro do QR Code)...");
                TreeItem<String> target = gerber.getValue();
                host.pickPoint(point -> {
                    if (point == null) {
                        status.setText("Cancelado.");
                        return;
                    }
                    String error = host.place(target, chosen, point);
                    errorLabel.setText(error == null ? "" : error);
                    status.setText("");
                });
            } catch (NumberFormatException invalid) {
                errorLabel.setText("Valor numerico invalido");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> {
            host.cancelPick();
            onClose.run();
        });

        VBox panel = new VBox(8, new Label("Gerber de destino:"), gerber,
                new Label("Dados do QR Code:"), text, grid,
                new Label("Correcao de erro:"), new HBox(10, levelL, levelM, levelQ, levelH),
                new Label("Polaridade:"), new HBox(10, positive, negative),
                new Label("Mascara:"), new HBox(10, square, rounded),
                summary, place, status, errorLabel, close);
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
