package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.flatcam.cam.transform.Calibration;
import org.flatcam.cam.transform.Calibration.Factors;
import org.flatcam.cam.transform.Calibration.GCodeSettings;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolCalibration.py: pick four points (on drills or pads of an object, or free), generate the verification
 * G-code, work out scale/skew factors from the deltas measured on the machine, and calibrate objects with them.
 */
final class CalibrationToolPanel {

    interface Host {
        List<TreeItem<String>> gerbersAndDrills();

        List<TreeItem<String>> allObjects();

        boolean inches();

        /** The next plot click; null = cancelled. */
        void pickPoint(java.util.function.Consumer<Coordinate> onPoint);

        void cancelPick();

        /** The centre of the drill or flashed pad of {@code source} under the click, or null if there is none. */
        Coordinate snap(TreeItem<String> source, Coordinate click);

        /** Marks the picked points on the plot. */
        void showPoints(double[][] points, int count);

        /** Asks where to save the verification G-code and writes it; error message or null. */
        String saveGCode(String gcode);

        /** Creates the calibrated copy of an object; error message or null. */
        String calibrate(TreeItem<String> object, Factors factors, Coordinate origin);
    }

    private CalibrationToolPanel() {
    }

    private static ComboBox<TreeItem<String>> chooser(List<TreeItem<String>> items) {
        ComboBox<TreeItem<String>> box = new ComboBox<>();
        box.getItems().setAll(items);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(TreeItem<String> item) {
                return item == null ? "" : item.getValue();
            }

            @Override
            public TreeItem<String> fromString(String text) {
                return null;
            }
        });
        box.getSelectionModel().selectFirst();
        return box;
    }

    private static TextField field(String value, boolean editable) {
        TextField text = new TextField(value);
        text.setEditable(editable);
        text.setPrefColumnCount(7);
        return text;
    }

    private static double parse(TextField field, String what) {
        String text = field.getText().trim().replace(',', '.');
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Valor invalido em: " + what);
        }
    }

    static Node build(Host host, Runnable onClose) {
        double[][] points = new double[4][2];
        int[] count = {0};

        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        // --- step 1: the points ---
        ToggleGroup source = new ToggleGroup();
        RadioButton fromObject = new RadioButton("Objeto (furos ou pads)");
        RadioButton free = new RadioButton("Clique livre");
        fromObject.setToggleGroup(source);
        free.setToggleGroup(source);
        fromObject.setSelected(true);
        ComboBox<TreeItem<String>> sourceObject = chooser(host.gerbersAndDrills());
        sourceObject.disableProperty().bind(fromObject.selectedProperty().not());
        ToolDescriptions.apply(sourceObject,"Origem dos pontos", "Gerber ou Excellon cujos pads ou furos serão usados para localizar os centros dos pontos de calibração.");

        String[] names = {"Inferior esquerdo", "Inferior direito", "Superior esquerdo", "Superior direito"};
        TextField[][] targets = new TextField[4][2];
        TextField[][] found = new TextField[4][2];
        GridPane table = new GridPane();
        table.setHgap(6);
        table.setVgap(4);
        table.add(new Label("Ponto"), 0, 0);
        table.add(new Label("X alvo"), 1, 0);
        table.add(new Label("Y alvo"), 2, 0);
        table.add(new Label("Delta X"), 3, 0);
        table.add(new Label("Delta Y"), 4, 0);
        for (int i = 0; i < 4; i++) {
            table.add(new Label(names[i]), 0, i + 1);
            for (int axis = 0; axis < 2; axis++) {
                targets[i][axis] = field("", false);
                table.add(targets[i][axis], 1 + axis, i + 1);
                found[i][axis] = field(i == 0 ? "Origem" : "", i != 0);
                found[i][axis].setDisable(i == 0);
                found[i][axis].setPromptText("0");
                ToolDescriptions.apply(targets[i][axis], names[i] + " — " + (axis == 0 ? "X alvo" : "Y alvo"),
                        "Coordenada obtida no plot; somente leitura. Não é o desvio medido na máquina.\n\nUnidades: unidade do objeto (mm ou in).");
                ToolDescriptions.apply(found[i][axis], names[i] + " — Delta " + (axis == 0 ? "X" : "Y"),
                        i == 1 || i == 2 ? "Desvio medido neste eixo para calcular os fatores de calibração. Vazio ou zero não aplica correção.\n\nUnidades: unidade do objeto (mm ou in)."
                                : "A origem e o quarto ponto não recebem deltas neste cálculo. Informe os desvios medidos nos pontos 2 e 3.");
                table.add(found[i][axis], 3 + axis, i + 1);
            }
        }
        found[3][0].setDisable(true);
        found[3][1].setDisable(true);
        Runnable refresh = () -> {
            for (int i = 0; i < 4; i++) {
                for (int axis = 0; axis < 2; axis++) {
                    targets[i][axis].setText(i < count[0] ? String.format(Locale.ROOT, "%.4f", points[i][axis]) : "");
                }
            }
            host.showPoints(points, count[0]);
        };

        Button start = new Button("Obter pontos");
        Button cancel = new Button("Cancelar");
        ToolDescriptions.apply(cancel,"Cancelar coleta", "Cancela a coleta de pontos no plot, sem calibrar nem apagar objetos do projeto.");
        String[] prompts = {"Clique no 1º ponto: inferior esquerdo (origem).",
                "Clique no 2º ponto: inferior direito (ou superior esquerdo).",
                "Clique no 3º ponto: superior esquerdo (ou inferior direito).", "Clique no 4º ponto: superior direito."};
        final Runnable[] next = new Runnable[1];
        next[0] = () -> {
            if (count[0] >= 4) {
                status.setText("Quatro pontos obtidos.");
                start.setDisable(false);
                return;
            }
            status.setText(prompts[count[0]]);
            host.pickPoint(click -> {
                if (click == null) {
                    status.setText("Cancelado.");
                    start.setDisable(false);
                    return;
                }
                Coordinate point = click;
                if (fromObject.isSelected()) {
                    point = host.snap(sourceObject.getValue(), click);
                    if (point == null) {
                        errorLabel.setText("Clique sobre um furo ou pad flashado do objeto escolhido.");
                        next[0].run();
                        return;
                    }
                }
                errorLabel.setText("");
                points[count[0]][0] = Calibration.round(point.x, 4);
                points[count[0]][1] = Calibration.round(point.y, 4);
                count[0]++;
                refresh.run();
                next[0].run();
            });
        };
        start.setOnAction(event -> {
            errorLabel.setText("");
            if (fromObject.isSelected() && sourceObject.getValue() == null) {
                errorLabel.setText("Escolha o objeto de origem dos pontos");
                return;
            }
            host.cancelPick();
            count[0] = 0;
            refresh.run();
            start.setDisable(true);
            next[0].run();
        });
        cancel.setOnAction(event -> {
            host.cancelPick();
            start.setDisable(false);
        });
        VBox pointsBox = new VBox(6, new HBox(8, fromObject, free), sourceObject, new HBox(6, start, cancel), table);
        pointsBox.setPadding(new Insets(6));

        // --- step 2: verification g-code ---
        TextField travelZ = field(ToolDefaults.text("calibration.travelz"), true);
        TextField verificationZ = field(ToolDefaults.text("calibration.verificationz"), true);
        CheckBox zeroZ = new CheckBox("Zerar Z antes");
        TextField toolChangeZ = field(ToolDefaults.text("calibration.toolchangez"), true);
        TextField toolChangeXY = field("", true);
        toolChangeXY.setPromptText("x, y");
        ToggleGroup second = new ToggleGroup();
        RadioButton topLeft = new RadioButton("Superior esquerdo");
        RadioButton bottomRight = new RadioButton("Inferior direito");
        topLeft.setToggleGroup(second);
        bottomRight.setToggleGroup(second);
        topLeft.setSelected(true);
        GridPane zs = new GridPane();
        zs.setHgap(6);
        zs.setVgap(4);
        zs.add(new Label("Z de deslocamento:"), 0, 0);
        zs.add(travelZ, 1, 0);
        zs.add(new Label("Z de verificação:"), 0, 1);
        zs.add(verificationZ, 1, 1);
        zs.add(new Label("Z de troca de ferramenta:"), 0, 2);
        zs.add(toolChangeZ, 1, 2);
        zs.add(new Label("XY de troca:"), 0, 3);
        zs.add(toolChangeXY, 1, 3);
        Button gcode = new Button("Gerar G-code de verificação");
        gcode.setMaxWidth(Double.MAX_VALUE);
        gcode.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException("Sao necessarios quatro pontos para gerar o G-code");
                }
                double[] xy = null;
                String text = toolChangeXY.getText().trim();
                if (!text.isEmpty()) {
                    String[] parts = text.split("[,;]");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("O XY de troca deve ser 'x, y'");
                    }
                    try {
                        xy = new double[] {Double.parseDouble(parts[0].trim().replace(',', '.')),
                                Double.parseDouble(parts[1].trim().replace(',', '.'))};
                    } catch (NumberFormatException invalid) {
                        throw new IllegalArgumentException("O XY de troca deve ser 'x, y'");
                    }
                }
                GCodeSettings settings = new GCodeSettings(parse(travelZ, "Z de deslocamento"),
                        parse(verificationZ, "Z de verificação"), zeroZ.isSelected(), parse(toolChangeZ, "Z de troca"), xy,
                        topLeft.isSelected(), host.inches());
                String message = host.saveGCode(Calibration.verificationGCode(points, settings));
                errorLabel.setText(message == null ? "" : message);
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox gcodeBox = new VBox(6, zs, zeroZ, new HBox(8, new Label("2º ponto:"), topLeft, bottomRight), gcode);
        gcodeBox.setPadding(new Insets(6));

        // --- step 3: factors ---
        TextField scaleX = field("1.0", true);
        TextField scaleY = field("1.0", true);
        TextField skewX = field("0.0", true);
        TextField skewY = field("0.0", true);
        Button calculate = new Button("Calcular fatores (a partir dos deltas)");
        calculate.setMaxWidth(Double.MAX_VALUE);
        calculate.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException("Obtenha os quatro pontos primeiro");
                }
                Factors factors = Calibration.calculate(points,
                        new double[] {parse(found[1][0], "delta X do ponto 2"), parse(found[1][1], "delta Y do ponto 2")},
                        new double[] {parse(found[2][0], "delta X do ponto 3"), parse(found[2][1], "delta Y do ponto 3")});
                scaleX.setText(String.valueOf(factors.scaleX()));
                scaleY.setText(String.valueOf(factors.scaleY()));
                skewX.setText(String.valueOf(factors.skewX()));
                skewY.setText(String.valueOf(factors.skewY()));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button applyScale = new Button("Aplicar escala aos pontos");
        applyScale.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException("Obtenha os quatro pontos primeiro");
                }
                double[][] scaled = Calibration.scalePoints(points, parse(scaleX, "escala X"), parse(scaleY, "escala Y"));
                copy(scaled, points);
                refresh.run();
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button applySkew = new Button("Aplicar inclinação aos pontos");
        applySkew.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException("Obtenha os quatro pontos primeiro");
                }
                double[][] skewed = Calibration.skewPoints(points, parse(skewX, "inclinação X"), parse(skewY, "inclinação Y"));
                copy(skewed, points);
                refresh.run();
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        GridPane factorGrid = new GridPane();
        factorGrid.setHgap(6);
        factorGrid.setVgap(4);
        factorGrid.add(new Label("Escala X:"), 0, 0);
        factorGrid.add(scaleX, 1, 0);
        factorGrid.add(new Label("Escala Y:"), 2, 0);
        factorGrid.add(scaleY, 3, 0);
        factorGrid.add(new Label("Inclinação X:"), 0, 1);
        factorGrid.add(skewX, 1, 1);
        factorGrid.add(new Label("Inclinação Y:"), 2, 1);
        factorGrid.add(skewY, 3, 1);
        VBox factorsBox = new VBox(6, new Label("Preencha os deltas medidos (pontos 2 e 3) na tabela."), calculate,
                factorGrid, new HBox(6, applyScale, applySkew));
        factorsBox.setPadding(new Insets(6));

        // --- step 5: calibrate an object ---
        ComboBox<TreeItem<String>> target = chooser(host.allObjects());
        Button calibrate = new Button("Calibrar objeto");
        calibrate.setMaxWidth(Double.MAX_VALUE);
        calibrate.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] < 1) {
                    throw new IllegalArgumentException("Obtenha ao menos o ponto de origem");
                }
                if (target.getValue() == null) {
                    throw new IllegalArgumentException("Escolha o objeto a calibrar");
                }
                Factors factors = new Factors(parse(scaleX, "escala X"), parse(scaleY, "escala Y"),
                        parse(skewX, "inclinação X"), parse(skewY, "inclinação Y"));
                String message = host.calibrate(target.getValue(), factors, new Coordinate(points[0][0], points[0][1]));
                errorLabel.setText(message == null ? "" : message);
                status.setText(message == null ? "Objeto calibrado criado." : "");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox calibrateBox = new VBox(6, new Label("Objeto a ajustar (escala e depois inclinação, pela origem):"), target,
                calibrate);
        calibrateBox.setPadding(new Insets(6));

        Button close = new Button("Fechar");
        close.setOnAction(event -> {
            host.cancelPick();
            host.showPoints(points, 0);
            onClose.run();
        });
        VBox panel = new VBox(8, new TitledPane("1. Pontos de calibração", pointsBox),
                new TitledPane("2. G-code de verificação", gcodeBox), new TitledPane("3. Ajustes", factorsBox),
                new TitledPane("4. Calibrar objeto", calibrateBox), status, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static void copy(double[][] from, double[][] to) {
        for (int i = 0; i < 4; i++) {
            to[i][0] = from[i][0];
            to[i][1] = from[i][1];
        }
    }
}
