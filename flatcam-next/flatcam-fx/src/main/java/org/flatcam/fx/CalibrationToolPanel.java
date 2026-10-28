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
            throw new IllegalArgumentException(I18n.text("ui.bf2ebc1d7ba4") + what);
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
        RadioButton fromObject = new RadioButton(I18n.text("ui.112a21487622"));
        RadioButton free = new RadioButton(I18n.text("ui.4988191318e7"));
        fromObject.setToggleGroup(source);
        free.setToggleGroup(source);
        fromObject.setSelected(true);
        ComboBox<TreeItem<String>> sourceObject = chooser(host.gerbersAndDrills());
        sourceObject.disableProperty().bind(fromObject.selectedProperty().not());
        ToolDescriptions.apply(sourceObject,I18n.text("ui.96ffcba04696"), I18n.text("ui.b9c1409f4616"));

        String[] names = {I18n.text("ui.74bd046f8134"), I18n.text("ui.67e7480bdc76"), I18n.text("ui.f3fa393016ae"), I18n.text("ui.4cd508015d68")};
        TextField[][] targets = new TextField[4][2];
        TextField[][] found = new TextField[4][2];
        GridPane table = new GridPane();
        table.setHgap(6);
        table.setVgap(4);
        table.add(new Label(I18n.text("ui.d6a379129f1f")), 0, 0);
        table.add(new Label(I18n.text("ui.303adc08cdf5")), 1, 0);
        table.add(new Label(I18n.text("ui.4a27d0e80a60")), 2, 0);
        table.add(new Label("Delta X"), 3, 0);
        table.add(new Label("Delta Y"), 4, 0);
        for (int i = 0; i < 4; i++) {
            table.add(new Label(names[i]), 0, i + 1);
            for (int axis = 0; axis < 2; axis++) {
                targets[i][axis] = field("", false);
                table.add(targets[i][axis], 1 + axis, i + 1);
                found[i][axis] = field(i == 0 ? I18n.text("ui.7b9afa8bda07") : "", i != 0);
                found[i][axis].setDisable(i == 0);
                found[i][axis].setPromptText("0");
                ToolDescriptions.apply(targets[i][axis], names[i] + " — " + (axis == 0 ? I18n.text("ui.303adc08cdf5") : I18n.text("ui.4a27d0e80a60")),
                        I18n.text("ui.20a6dfd13f35"));
                ToolDescriptions.apply(found[i][axis], names[i] + " — Delta " + (axis == 0 ? "X" : "Y"),
                        i == 1 || i == 2 ? I18n.text("ui.c7fae8eaa592")
                                : I18n.text("ui.03134c481ab7"));
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

        Button start = new Button(I18n.text("ui.e3954f843d1b"));
        Button cancel = new Button(I18n.text("ui.bb9dbb406dcb"));
        ToolDescriptions.apply(cancel,I18n.text("ui.5ac742ae60a6"), I18n.text("ui.ca87e2e1aee2"));
        String[] prompts = {I18n.text("ui.4bf02ce2bf44"),
                I18n.text("ui.adc75349570d"),
                I18n.text("ui.0d6a10477fc9"), I18n.text("ui.c67f2cce2e39")};
        final Runnable[] next = new Runnable[1];
        next[0] = () -> {
            if (count[0] >= 4) {
                status.setText(I18n.text("ui.e9f8fe1e7eca"));
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
                        errorLabel.setText(I18n.text("ui.0fefb010fbcc"));
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
                errorLabel.setText(I18n.text("ui.324ce521c201"));
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
        CheckBox zeroZ = new CheckBox(I18n.text("ui.5df44c962ab4"));
        TextField toolChangeZ = field(ToolDefaults.text("calibration.toolchangez"), true);
        TextField toolChangeXY = field("", true);
        toolChangeXY.setPromptText("x, y");
        ToggleGroup second = new ToggleGroup();
        RadioButton topLeft = new RadioButton(I18n.text("ui.f3fa393016ae"));
        RadioButton bottomRight = new RadioButton(I18n.text("ui.67e7480bdc76"));
        topLeft.setToggleGroup(second);
        bottomRight.setToggleGroup(second);
        topLeft.setSelected(true);
        GridPane zs = new GridPane();
        zs.setHgap(6);
        zs.setVgap(4);
        zs.add(new Label(I18n.text("ui.a0805c9b0c1d")), 0, 0);
        zs.add(travelZ, 1, 0);
        zs.add(new Label(I18n.text("ui.c4b53a777221")), 0, 1);
        zs.add(verificationZ, 1, 1);
        zs.add(new Label(I18n.text("ui.e939001ac123")), 0, 2);
        zs.add(toolChangeZ, 1, 2);
        zs.add(new Label(I18n.text("ui.96266efb6e33")), 0, 3);
        zs.add(toolChangeXY, 1, 3);
        Button gcode = new Button(I18n.text("ui.b2983dcb84cc"));
        gcode.setMaxWidth(Double.MAX_VALUE);
        gcode.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException(I18n.text("ui.befc19a71fc2"));
                }
                double[] xy = null;
                String text = toolChangeXY.getText().trim();
                if (!text.isEmpty()) {
                    String[] parts = text.split("[,;]");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException(I18n.text("ui.7fca5cfe0fc8"));
                    }
                    try {
                        xy = new double[] {Double.parseDouble(parts[0].trim().replace(',', '.')),
                                Double.parseDouble(parts[1].trim().replace(',', '.'))};
                    } catch (NumberFormatException invalid) {
                        throw new IllegalArgumentException(I18n.text("ui.7fca5cfe0fc8"));
                    }
                }
                GCodeSettings settings = new GCodeSettings(parse(travelZ, I18n.text("ui.231dcd7be1cb")),
                        parse(verificationZ, I18n.text("ui.7bc80ebbfc59")), zeroZ.isSelected(), parse(toolChangeZ, I18n.text("ui.2f8d0fa8b309")), xy,
                        topLeft.isSelected(), host.inches());
                String message = host.saveGCode(Calibration.verificationGCode(points, settings));
                errorLabel.setText(message == null ? "" : message);
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox gcodeBox = new VBox(6, zs, zeroZ, new HBox(8, new Label(I18n.text("ui.fc06cf8d24fd")), topLeft, bottomRight), gcode);
        gcodeBox.setPadding(new Insets(6));

        // --- step 3: factors ---
        TextField scaleX = field("1.0", true);
        TextField scaleY = field("1.0", true);
        TextField skewX = field("0.0", true);
        TextField skewY = field("0.0", true);
        Button calculate = new Button(I18n.text("ui.847b148daf36"));
        calculate.setMaxWidth(Double.MAX_VALUE);
        calculate.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException(I18n.text("ui.ee0c17c65dbc"));
                }
                Factors factors = Calibration.calculate(points,
                        new double[] {parse(found[1][0], I18n.text("ui.ecaf3f53903f")), parse(found[1][1], I18n.text("ui.4ac66b7adf69"))},
                        new double[] {parse(found[2][0], I18n.text("ui.99f2d5f73a15")), parse(found[2][1], I18n.text("ui.7c881ee753c0"))});
                scaleX.setText(String.valueOf(factors.scaleX()));
                scaleY.setText(String.valueOf(factors.scaleY()));
                skewX.setText(String.valueOf(factors.skewX()));
                skewY.setText(String.valueOf(factors.skewY()));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button applyScale = new Button(I18n.text("ui.88ead816dee3"));
        applyScale.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException(I18n.text("ui.ee0c17c65dbc"));
                }
                double[][] scaled = Calibration.scalePoints(points, parse(scaleX, I18n.text("ui.553531333a79")), parse(scaleY, I18n.text("ui.f95ded8918d8")));
                copy(scaled, points);
                refresh.run();
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button applySkew = new Button(I18n.text("ui.be267371692e"));
        applySkew.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] != 4) {
                    throw new IllegalArgumentException(I18n.text("ui.ee0c17c65dbc"));
                }
                double[][] skewed = Calibration.skewPoints(points, parse(skewX, I18n.text("ui.371dcbd6d813")), parse(skewY, I18n.text("ui.758cf75f2365")));
                copy(skewed, points);
                refresh.run();
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        GridPane factorGrid = new GridPane();
        factorGrid.setHgap(6);
        factorGrid.setVgap(4);
        factorGrid.add(new Label(I18n.text("ui.07046d7a1700")), 0, 0);
        factorGrid.add(scaleX, 1, 0);
        factorGrid.add(new Label(I18n.text("ui.908de0ea31ca")), 2, 0);
        factorGrid.add(scaleY, 3, 0);
        factorGrid.add(new Label(I18n.text("ui.6599efcea75f")), 0, 1);
        factorGrid.add(skewX, 1, 1);
        factorGrid.add(new Label(I18n.text("ui.fbf30a49d70c")), 2, 1);
        factorGrid.add(skewY, 3, 1);
        VBox factorsBox = new VBox(6, new Label(I18n.text("ui.11edfa71365c")), calculate,
                factorGrid, new HBox(6, applyScale, applySkew));
        factorsBox.setPadding(new Insets(6));

        // --- step 5: calibrate an object ---
        ComboBox<TreeItem<String>> target = chooser(host.allObjects());
        Button calibrate = new Button(I18n.text("ui.df9153b7f2cc"));
        calibrate.setMaxWidth(Double.MAX_VALUE);
        calibrate.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (count[0] < 1) {
                    throw new IllegalArgumentException(I18n.text("ui.2dea3961ae5b"));
                }
                if (target.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.0291c636b2d5"));
                }
                Factors factors = new Factors(parse(scaleX, I18n.text("ui.553531333a79")), parse(scaleY, I18n.text("ui.f95ded8918d8")),
                        parse(skewX, I18n.text("ui.371dcbd6d813")), parse(skewY, I18n.text("ui.758cf75f2365")));
                String message = host.calibrate(target.getValue(), factors, new Coordinate(points[0][0], points[0][1]));
                errorLabel.setText(message == null ? "" : message);
                status.setText(message == null ? I18n.text("ui.80c1d6f5dc74") : "");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox calibrateBox = new VBox(6, new Label(I18n.text("ui.c849f9948200")), target,
                calibrate);
        calibrateBox.setPadding(new Insets(6));

        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> {
            host.cancelPick();
            host.showPoints(points, 0);
            onClose.run();
        });
        VBox panel = new VBox(8, new TitledPane(I18n.text("ui.bf757cdc646b"), pointsBox),
                new TitledPane(I18n.text("ui.bed5979c0059"), gcodeBox), new TitledPane(I18n.text("ui.ec27b7664c57"), factorsBox),
                new TitledPane(I18n.text("ui.8a165c9b9d0d"), calibrateBox), status, errorLabel, close);
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
