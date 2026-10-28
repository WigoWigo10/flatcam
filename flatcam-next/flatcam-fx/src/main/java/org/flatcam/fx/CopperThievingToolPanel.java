package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import org.flatcam.cam.convert.CopperThieving;
import org.flatcam.cam.convert.CopperThieving.BoxType;
import org.flatcam.cam.convert.CopperThieving.Fill;
import org.flatcam.cam.convert.CopperThieving.Options;
import org.flatcam.cam.convert.CopperThieving.Plating;
import org.flatcam.cam.convert.CopperThieving.Reference;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolCopperThieving.py: copper thieving (solid, dots, squares or lines), the robber bar and the pattern
 * plating mask. Defaults are Python's.
 */
final class CopperThievingToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        /** Gerbers and Geometry objects, for the "box" reference. */
        List<TreeItem<String>> references();

        TreeItem<String> initialGerber();

        /** Runs the fill as a job and creates the "_thief" Gerber; {@code onDone} gets an error message or null. */
        void thieve(TreeItem<String> gerber, TreeItem<String> reference, List<double[]> zones, Options options,
                    Consumer<String> onDone);

        /** Creates the "_robber" Gerber; error message or null. */
        String robberBar(TreeItem<String> gerber, double margin, double thickness);

        /**
         * Creates the "_plating_mask" Gerber from the last thieving and robber bar made here; returns the plated area
         * or an error message in the second slot.
         */
        Object[] platingMask(TreeItem<String> mask, double clearance, Plating choice, double robberThickness);

        void pickPoint(Consumer<Coordinate> onPoint);

        void cancelPick();

        /** Marks the drawn zones ({xmin, ymin, xmax, ymax}) on the plot. */
        void showZones(List<double[]> zones);
    }

    private CopperThievingToolPanel() {
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
        return box;
    }

    private static TextField number(String value) {
        TextField field = new TextField(value);
        field.setPrefColumnCount(5);
        return field;
    }

    private static TextField number(double value) {
        TextField field = new TextField(String.valueOf(value));
        field.setPrefColumnCount(5);
        return field;
    }

    private static double parse(TextField field, String what) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(I18n.text("ui.bf2ebc1d7ba4") + what);
        }
    }

    private static <T> RadioButton radio(String text, ToggleGroup group, T value) {
        RadioButton button = new RadioButton(text);
        button.setToggleGroup(group);
        button.setUserData(value);
        return button;
    }

    @SuppressWarnings("unchecked")
    private static <T> T chosen(ToggleGroup group) {
        return (T) group.getSelectedToggle().getUserData();
    }

    static Node build(Host host, Runnable onClose) {
        Options d = Options.defaults();
        ComboBox<TreeItem<String>> gerber = chooser(host.gerbers());
        gerber.getSelectionModel().select(host.initialGerber());
        if (gerber.getValue() == null) {
            gerber.getSelectionModel().selectFirst();
        }
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);
        Label status = new Label();
        status.setWrapText(true);

        // --- thieving ---
        TextField clearance = number(ToolDefaults.text("thieving.clearance"));
        TextField margin = number(ToolDefaults.text("thieving.margin"));
        TextField minArea = number(ToolDefaults.text("thieving.minarea"));
        ToggleGroup reference = new ToggleGroup();
        RadioButton itself = radio(I18n.text("ui.3d2b51c03aed"), reference, Reference.ITSELF);
        RadioButton area = radio(I18n.text("ui.c24f9b8e521c"), reference, Reference.AREA);
        RadioButton box = radio(I18n.text("ui.a8e4c7c07fd3"), reference, Reference.BOX);
        itself.setSelected(true);
        ComboBox<TreeItem<String>> referenceObject = chooser(host.references());
        ToolDescriptions.apply(referenceObject,I18n.text("ui.a66e08f4f6c3"), I18n.text("ui.467c07f2376a"));
        referenceObject.getSelectionModel().selectFirst();
        referenceObject.disableProperty().bind(box.selectedProperty().not());
        ToggleGroup boxType = new ToggleGroup();
        RadioButton rectangular = radio(I18n.text("ui.0d36407066ce"), boxType, BoxType.RECTANGULAR);
        RadioButton minimal = radio(I18n.text("ui.5fd3a7dfaa8f"), boxType, BoxType.MINIMAL);
        rectangular.setSelected(true);
        HBox boxTypes = new HBox(8, new Label("Caixa:"), rectangular, minimal);
        boxTypes.visibleProperty().bind(itself.selectedProperty());
        boxTypes.managedProperty().bind(boxTypes.visibleProperty());

        List<double[]> zones = new ArrayList<>();
        Label zoneCount = new Label(I18n.text("ui.a9a84277720a"));
        Button drawZone = new Button(I18n.text("ui.689fdb1ce672"));
        Button clearZones = new Button(I18n.text("ui.f60938e98613"));
        drawZone.setOnAction(event -> host.pickPoint(first -> {
            if (first == null) {
                return;
            }
            status.setText(I18n.text("ui.53d8917d5b0f"));
            host.pickPoint(second -> {
                if (second == null) {
                    status.setText("");
                    return;
                }
                zones.add(new double[] {Math.min(first.x, second.x), Math.min(first.y, second.y),
                        Math.max(first.x, second.x), Math.max(first.y, second.y)});
                zoneCount.setText(zones.size() + I18n.text("ui.6cf8f4df47e6"));
                status.setText("");
                host.showZones(zones);
            });
        }));
        clearZones.setOnAction(event -> {
            zones.clear();
            zoneCount.setText(I18n.text("ui.a9a84277720a"));
            host.showZones(zones);
        });
        HBox zoneButtons = new HBox(6, drawZone, clearZones);
        VBox zoneBox = new VBox(4, zoneButtons, zoneCount);
        zoneBox.visibleProperty().bind(area.selectedProperty());
        zoneBox.managedProperty().bind(zoneBox.visibleProperty());

        ToggleGroup fill = new ToggleGroup();
        RadioButton solid = radio(I18n.text("ui.40ca66a274a1"), fill, Fill.SOLID);
        RadioButton dots = radio(I18n.text("ui.6ac2b76d12ad"), fill, Fill.DOT);
        RadioButton squares = radio(I18n.text("ui.bcfd287153a7"), fill, Fill.SQUARE);
        RadioButton lines = radio(I18n.text("ui.078d9be289fe"), fill, Fill.LINE);
        solid.setSelected(true);
        TextField dotDiameter = number(ToolDefaults.text("thieving.dotdiameter"));
        TextField dotSpacing = number(ToolDefaults.text("thieving.dotspacing"));
        TextField squareSize = number(ToolDefaults.text("thieving.squaresize"));
        TextField squareSpacing = number(ToolDefaults.text("thieving.squarespacing"));
        TextField lineSize = number(ToolDefaults.text("thieving.linesize"));
        TextField lineSpacing = number(ToolDefaults.text("thieving.linespacing"));
        GridPane dotGrid = pairs(I18n.text("ui.56d3a428a0c7"), dotDiameter, I18n.text("ui.7c8bd4e9a824"), dotSpacing);
        GridPane squareGrid = pairs("Lado:", squareSize, I18n.text("ui.7c8bd4e9a824"), squareSpacing);
        GridPane lineGrid = pairs(I18n.text("ui.34dd1326833d"), lineSize, I18n.text("ui.7c8bd4e9a824"), lineSpacing);
        show(dotGrid, dots);
        show(squareGrid, squares);
        show(lineGrid, lines);
        // The line grid only works with the object itself as the reference (Python switches it back as well).
        lines.selectedProperty().addListener((o, was, now) -> {
            if (now) {
                itself.setSelected(true);
            }
        });
        area.disableProperty().bind(lines.selectedProperty());
        box.disableProperty().bind(lines.selectedProperty());

        Button thieve = new Button(I18n.text("ui.5e8ee1bc1239"));
        thieve.setMaxWidth(Double.MAX_VALUE);
        thieve.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                Reference chosenReference = chosen(reference);
                if (chosenReference == Reference.BOX && referenceObject.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.b6d2a2388956"));
                }
                Options options = new Options(parse(clearance, I18n.text("ui.dd7487fb3fad")), parse(margin, I18n.text("ui.5ac6dc6d3d08")),
                        parse(minArea, I18n.text("ui.b1ad3f8c2c5f")), chosenReference, chosen(boxType), d.circleSteps(), chosen(fill),
                        parse(dotDiameter, I18n.text("ui.98c498c982a8")), parse(dotSpacing, I18n.text("ui.d07b218c95b5")),
                        parse(squareSize, I18n.text("ui.8a3e40fb20d3")), parse(squareSpacing, I18n.text("ui.020f31f04c20")),
                        parse(lineSize, I18n.text("ui.846de4ebe749")), parse(lineSpacing, I18n.text("ui.5473dfdd2ada")));
                thieve.setDisable(true);
                status.setText(I18n.text("ui.55d2f163eff2"));
                host.thieve(gerber.getValue(), referenceObject.getValue(), List.copyOf(zones), options, message -> {
                    thieve.setDisable(false);
                    status.setText(message == null ? I18n.text("ui.03fc49e00700") : "");
                    errorLabel.setText(message == null ? "" : message);
                });
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });

        VBox thievingBox = new VBox(6, new Label("Gerber:"), gerber,
                pairs(I18n.text("ui.5c80e603ab8f"), clearance, I18n.text("ui.16bab5b0d1de"), margin),
                pairs(I18n.text("ui.2d2d2475344a"), minArea, "", new Label()),
                new Label(I18n.text("ui.3d1af529ff62")), new HBox(8, itself, area, box), boxTypes, zoneBox, referenceObject,
                new Label(I18n.text("ui.2a684468c060")), new HBox(8, solid, dots, squares, lines), dotGrid, squareGrid,
                lineGrid, thieve);
        thievingBox.setPadding(new Insets(6));

        // --- robber bar ---
        TextField robberMargin = number(ToolDefaults.text("thieving.robbermargin"));
        TextField robberThickness = number(ToolDefaults.text("thieving.robberthickness"));
        Button robber = new Button(I18n.text("ui.afa06dc5dab5"));
        robber.setMaxWidth(Double.MAX_VALUE);
        robber.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                String message = host.robberBar(gerber.getValue(), parse(robberMargin, I18n.text("ui.a7d447751716")),
                        parse(robberThickness, I18n.text("ui.afe8dc095e0b")));
                errorLabel.setText(message == null ? "" : message);
                status.setText(message == null ? I18n.text("ui.7aa3758df0a4") : "");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox robberBox = new VBox(6, pairs(I18n.text("ui.16bab5b0d1de"), robberMargin, I18n.text("ui.34dd1326833d"), robberThickness), robber);
        robberBox.setPadding(new Insets(6));

        // --- pattern plating mask ---
        ComboBox<TreeItem<String>> maskObject = chooser(host.gerbers());
        maskObject.getSelectionModel().selectFirst();
        TextField maskClearance = number(ToolDefaults.text("thieving.maskclearance"));
        ToggleGroup plating = new ToggleGroup();
        RadioButton both = radio(I18n.text("ui.727f1e023503"), plating, Plating.BOTH);
        RadioButton onlyThieving = radio("Thieving", plating, Plating.THIEVING);
        RadioButton onlyRobber = radio("Robber bar", plating, Plating.ROBBER);
        RadioButton nothing = radio(I18n.text("ui.496a53a0f11e"), plating, Plating.NONE);
        both.setSelected(true);
        TextField platedArea = new TextField("0.0");
        platedArea.setEditable(false);
        platedArea.setPrefColumnCount(8);
        Button mask = new Button(I18n.text("ui.8b3d64948680"));
        mask.setMaxWidth(Double.MAX_VALUE);
        mask.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (maskObject.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.eaecdb2b5149"));
                }
                Object[] result = host.platingMask(maskObject.getValue(), parse(maskClearance, I18n.text("ui.6d0c50c62515")),
                        chosen(plating), parse(robberThickness, I18n.text("ui.afe8dc095e0b")));
                if (result[1] != null) {
                    errorLabel.setText((String) result[1]);
                } else {
                    platedArea.setText(String.format(Locale.ROOT, "%.4f", (Double) result[0]));
                    status.setText(I18n.text("ui.8f27736ce96c"));
                }
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox maskBox = new VBox(6, new Label(I18n.text("ui.b139f4e81852")), maskObject,
                pairs(I18n.text("ui.5c80e603ab8f"), maskClearance, "", new Label()), new Label("Incluir:"),
                new HBox(8, both, onlyThieving, onlyRobber, nothing),
                new HBox(8, new Label(I18n.text("ui.3e8e8b66dc72")), platedArea), mask);
        maskBox.setPadding(new Insets(6));

        TitledPane thievingPane = new TitledPane("Copper Thieving", thievingBox);
        TitledPane robberPane = new TitledPane("Robber Bar", robberBox);
        TitledPane maskPane = new TitledPane("Pattern Plating Mask", maskBox);
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> {
            host.cancelPick();
            host.showZones(List.of());
            onClose.run();
        });
        VBox panel = new VBox(8, thievingPane, robberPane, maskPane, status, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static GridPane pairs(String firstLabel, TextField first, String secondLabel, Node second) {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(4);
        grid.add(new Label(firstLabel), 0, 0);
        grid.add(first, 1, 0);
        if (!secondLabel.isEmpty()) {
            grid.add(new Label(secondLabel), 2, 0);
            grid.add(second, 3, 0);
        }
        return grid;
    }

    private static GridPane pairs(String firstLabel, TextField first, String secondLabel, TextField second) {
        return pairs(firstLabel, first, secondLabel, (Node) second);
    }

    private static void show(Node node, RadioButton when) {
        node.visibleProperty().bind(when.selectedProperty());
        node.managedProperty().bind(node.visibleProperty());
    }
}
