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
            throw new IllegalArgumentException("Valor invalido em: " + what);
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
        RadioButton itself = radio("O próprio objeto", reference, Reference.ITSELF);
        RadioButton area = radio("Áreas desenhadas", reference, Reference.AREA);
        RadioButton box = radio("Objeto de referência", reference, Reference.BOX);
        itself.setSelected(true);
        ComboBox<TreeItem<String>> referenceObject = chooser(host.references());
        ToolDescriptions.apply(referenceObject,"Limite de referência", "Gerber ou Geometry alinhado que fornece a caixa limite do Copper Thieving. Usado somente no modo objeto de referência.");
        referenceObject.getSelectionModel().selectFirst();
        referenceObject.disableProperty().bind(box.selectedProperty().not());
        ToggleGroup boxType = new ToggleGroup();
        RadioButton rectangular = radio("Retangular", boxType, BoxType.RECTANGULAR);
        RadioButton minimal = radio("Mínima (casco convexo)", boxType, BoxType.MINIMAL);
        rectangular.setSelected(true);
        HBox boxTypes = new HBox(8, new Label("Caixa:"), rectangular, minimal);
        boxTypes.visibleProperty().bind(itself.selectedProperty());
        boxTypes.managedProperty().bind(boxTypes.visibleProperty());

        List<double[]> zones = new ArrayList<>();
        Label zoneCount = new Label("Nenhuma área desenhada");
        Button drawZone = new Button("Desenhar área (2 cliques)");
        Button clearZones = new Button("Limpar áreas");
        drawZone.setOnAction(event -> host.pickPoint(first -> {
            if (first == null) {
                return;
            }
            status.setText("Clique no canto oposto da área.");
            host.pickPoint(second -> {
                if (second == null) {
                    status.setText("");
                    return;
                }
                zones.add(new double[] {Math.min(first.x, second.x), Math.min(first.y, second.y),
                        Math.max(first.x, second.x), Math.max(first.y, second.y)});
                zoneCount.setText(zones.size() + " área(s) desenhada(s)");
                status.setText("");
                host.showZones(zones);
            });
        }));
        clearZones.setOnAction(event -> {
            zones.clear();
            zoneCount.setText("Nenhuma área desenhada");
            host.showZones(zones);
        });
        HBox zoneButtons = new HBox(6, drawZone, clearZones);
        VBox zoneBox = new VBox(4, zoneButtons, zoneCount);
        zoneBox.visibleProperty().bind(area.selectedProperty());
        zoneBox.managedProperty().bind(zoneBox.visibleProperty());

        ToggleGroup fill = new ToggleGroup();
        RadioButton solid = radio("Sólido", fill, Fill.SOLID);
        RadioButton dots = radio("Pontos", fill, Fill.DOT);
        RadioButton squares = radio("Quadrados", fill, Fill.SQUARE);
        RadioButton lines = radio("Linhas", fill, Fill.LINE);
        solid.setSelected(true);
        TextField dotDiameter = number(ToolDefaults.text("thieving.dotdiameter"));
        TextField dotSpacing = number(ToolDefaults.text("thieving.dotspacing"));
        TextField squareSize = number(ToolDefaults.text("thieving.squaresize"));
        TextField squareSpacing = number(ToolDefaults.text("thieving.squarespacing"));
        TextField lineSize = number(ToolDefaults.text("thieving.linesize"));
        TextField lineSpacing = number(ToolDefaults.text("thieving.linespacing"));
        GridPane dotGrid = pairs("Diâmetro:", dotDiameter, "Espaço:", dotSpacing);
        GridPane squareGrid = pairs("Lado:", squareSize, "Espaço:", squareSpacing);
        GridPane lineGrid = pairs("Espessura:", lineSize, "Espaço:", lineSpacing);
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

        Button thieve = new Button("Adicionar Copper Thieving");
        thieve.setMaxWidth(Double.MAX_VALUE);
        thieve.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
                }
                Reference chosenReference = chosen(reference);
                if (chosenReference == Reference.BOX && referenceObject.getValue() == null) {
                    throw new IllegalArgumentException("Escolha o objeto de referencia");
                }
                Options options = new Options(parse(clearance, "distância"), parse(margin, "margem"),
                        parse(minArea, "área mínima"), chosenReference, chosen(boxType), d.circleSteps(), chosen(fill),
                        parse(dotDiameter, "diâmetro dos pontos"), parse(dotSpacing, "espaço dos pontos"),
                        parse(squareSize, "lado dos quadrados"), parse(squareSpacing, "espaço dos quadrados"),
                        parse(lineSize, "espessura das linhas"), parse(lineSpacing, "espaço das linhas"));
                thieve.setDisable(true);
                status.setText("Gerando o copper thieving...");
                host.thieve(gerber.getValue(), referenceObject.getValue(), List.copyOf(zones), options, message -> {
                    thieve.setDisable(false);
                    status.setText(message == null ? "Copper thieving criado." : "");
                    errorLabel.setText(message == null ? "" : message);
                });
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });

        VBox thievingBox = new VBox(6, new Label("Gerber:"), gerber,
                pairs("Distância:", clearance, "Margem:", margin),
                pairs("Área mínima:", minArea, "", new Label()),
                new Label("Preencher dentro de:"), new HBox(8, itself, area, box), boxTypes, zoneBox, referenceObject,
                new Label("Tipo de preenchimento:"), new HBox(8, solid, dots, squares, lines), dotGrid, squareGrid,
                lineGrid, thieve);
        thievingBox.setPadding(new Insets(6));

        // --- robber bar ---
        TextField robberMargin = number(ToolDefaults.text("thieving.robbermargin"));
        TextField robberThickness = number(ToolDefaults.text("thieving.robberthickness"));
        Button robber = new Button("Adicionar Robber Bar");
        robber.setMaxWidth(Double.MAX_VALUE);
        robber.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException("Carregue um Gerber");
                }
                String message = host.robberBar(gerber.getValue(), parse(robberMargin, "margem da barra"),
                        parse(robberThickness, "espessura da barra"));
                errorLabel.setText(message == null ? "" : message);
                status.setText(message == null ? "Robber bar criada." : "");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox robberBox = new VBox(6, pairs("Margem:", robberMargin, "Espessura:", robberThickness), robber);
        robberBox.setPadding(new Insets(6));

        // --- pattern plating mask ---
        ComboBox<TreeItem<String>> maskObject = chooser(host.gerbers());
        maskObject.getSelectionModel().selectFirst();
        TextField maskClearance = number(ToolDefaults.text("thieving.maskclearance"));
        ToggleGroup plating = new ToggleGroup();
        RadioButton both = radio("Ambos", plating, Plating.BOTH);
        RadioButton onlyThieving = radio("Thieving", plating, Plating.THIEVING);
        RadioButton onlyRobber = radio("Robber bar", plating, Plating.ROBBER);
        RadioButton nothing = radio("Nenhum", plating, Plating.NONE);
        both.setSelected(true);
        TextField platedArea = new TextField("0.0");
        platedArea.setEditable(false);
        platedArea.setPrefColumnCount(8);
        Button mask = new Button("Gerar máscara de galvanoplastia");
        mask.setMaxWidth(Double.MAX_VALUE);
        mask.setOnAction(event -> {
            errorLabel.setText("");
            try {
                if (maskObject.getValue() == null) {
                    throw new IllegalArgumentException("Escolha o Gerber da máscara de solda");
                }
                Object[] result = host.platingMask(maskObject.getValue(), parse(maskClearance, "distância da máscara"),
                        chosen(plating), parse(robberThickness, "espessura da barra"));
                if (result[1] != null) {
                    errorLabel.setText((String) result[1]);
                } else {
                    platedArea.setText(String.format(Locale.ROOT, "%.4f", (Double) result[0]));
                    status.setText("Máscara de galvanoplastia criada.");
                }
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        VBox maskBox = new VBox(6, new Label("Gerber da máscara de solda:"), maskObject,
                pairs("Distância:", maskClearance, "", new Label()), new Label("Incluir:"),
                new HBox(8, both, onlyThieving, onlyRobber, nothing),
                new HBox(8, new Label("Área galvanizada:"), platedArea), mask);
        maskBox.setPadding(new Insets(6));

        TitledPane thievingPane = new TitledPane("Copper Thieving", thievingBox);
        TitledPane robberPane = new TitledPane("Robber Bar", robberBox);
        TitledPane maskPane = new TitledPane("Pattern Plating Mask", maskBox);
        Button close = new Button("Fechar");
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
