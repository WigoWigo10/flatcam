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
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.flatcam.cam.svg.FilmExporter;

/**
 * appTools/ToolFilm.py: a printable SVG / PNG / PDF film of a Gerber or Geometry, framed by a box object.
 * Python's defaults: negative film, scale/skew/mirror off, bottom-left skew reference, SVG, A4 portrait, 96 dpi.
 */
final class FilmToolPanel {

    enum Punch { NONE, EXCELLON, PADS }

    /**
     * @param punch     only for a positive film of a Gerber
     * @param excellon  the punch reference for {@link Punch#EXCELLON}
     * @param padSize   hole size for {@link Punch#PADS}
     */
    record Request(TreeItem<String> film, TreeItem<String> box, FilmExporter.Options options, Punch punch,
                   TreeItem<String> excellon, double padSize) {
    }

    interface Host {
        /** Gerbers and Geometries. */
        List<TreeItem<String>> filmObjects();

        /** Gerbers, Geometries and Excellons. */
        List<TreeItem<String>> boxObjects();

        List<TreeItem<String>> excellons();

        TreeItem<String> initialFilm();

        String unitsOf(TreeItem<String> item);

        /** Asks for the file and writes the film; returns an error message, or null on success (or cancel). */
        String export(Request request);
    }

    private FilmToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> film = combo(host.filmObjects());
        ToolDescriptions.apply(film,I18n.text("ui.4452f722b9ff"), I18n.text("ui.f0aa1e7bdc62"));
        film.getSelectionModel().select(host.initialFilm());
        if (film.getValue() == null) {
            film.getSelectionModel().selectFirst();
        }
        ComboBox<TreeItem<String>> box = combo(host.boxObjects());
        ToolDescriptions.apply(box,I18n.text("ui.66e1f6a3cdc4"), I18n.text("ui.e344f52f921f"));
        box.getSelectionModel().select(film.getValue());
        film.valueProperty().addListener((o, a, b) -> {
            if (b != null && host.boxObjects().contains(b)) {
                box.getSelectionModel().select(b);
            }
        });

        ToggleGroup types = new ToggleGroup();
        RadioButton positive = new RadioButton(I18n.text("ui.6d28db2d208a"));
        RadioButton negative = new RadioButton(I18n.text("ui.4f45a05be9b3"));
        positive.setToggleGroup(types);
        negative.setToggleGroup(types);
        (ToolDefaults.flag("film.negative") ? negative : positive).setSelected(true);
        TextField boundary = field(ToolDefaults.text("film.boundary"));
        boundary.disableProperty().bind(negative.selectedProperty().not());
        TextField stroke = field(ToolDefaults.text("film.scalestroke"));
        TextField color = field("#000000");
        color.disableProperty().bind(negative.selectedProperty());

        CheckBox punch = new CheckBox(I18n.text("ui.cd7b7ec488e5"));
        punch.disableProperty().bind(negative.selectedProperty());
        ToggleGroup punchSource = new ToggleGroup();
        RadioButton fromExcellon = new RadioButton("Excellon");
        RadioButton fromPads = new RadioButton(I18n.text("ui.76970c236407"));
        fromExcellon.setToggleGroup(punchSource);
        fromPads.setToggleGroup(punchSource);
        fromExcellon.setSelected(true);
        ComboBox<TreeItem<String>> excellon = combo(host.excellons());
        ToolDescriptions.apply(excellon,I18n.text("ui.8afc063208bb"), I18n.text("ui.f8943804ff55"));
        excellon.getSelectionModel().selectFirst();
        TextField padSize = field(ToolDefaults.text("film.padsize"));
        HBox sources = new HBox(10, fromExcellon, fromPads);
        VBox punchBox = new VBox(6, punch, sources, excellon, new HBox(6, new Label(I18n.text("ui.babde76c8c21")), padSize));
        sources.visibleProperty().bind(punch.selectedProperty());
        sources.managedProperty().bind(punch.selectedProperty());
        excellon.visibleProperty().bind(punch.selectedProperty().and(fromExcellon.selectedProperty()));
        excellon.managedProperty().bind(excellon.visibleProperty());
        HBox padRow = (HBox) punchBox.getChildren().get(3);
        padRow.visibleProperty().bind(punch.selectedProperty().and(fromPads.selectedProperty()));
        padRow.managedProperty().bind(padRow.visibleProperty());

        CheckBox scale = new CheckBox(I18n.text("ui.6432ba75d257"));
        TextField scaleX = field(ToolDefaults.text("film.scalex"));
        TextField scaleY = field(ToolDefaults.text("film.scaley"));
        scaleX.disableProperty().bind(scale.selectedProperty().not());
        scaleY.disableProperty().bind(scale.selectedProperty().not());
        CheckBox skew = new CheckBox(I18n.text("ui.6bea59470163"));
        TextField skewX = field(ToolDefaults.text("film.skewx"));
        TextField skewY = field(ToolDefaults.text("film.skewy"));
        ToolDescriptions.apply(scaleX, I18n.text("ui.8fbdc16db68e"), I18n.text("ui.d43a0d1beb03"));
        ToolDescriptions.apply(scaleY, I18n.text("ui.b026e9c17f03"), I18n.text("ui.63780cb8c841"));
        ToolDescriptions.apply(skewX, I18n.text("ui.ca416b2c5658"), I18n.text("ui.5da8ae8bcc01"));
        ToolDescriptions.apply(skewY, I18n.text("ui.9d3cd60f96cf"), I18n.text("ui.6f642474d987"));
        ComboBox<String> skewReference = new ComboBox<>();
        skewReference.getItems().setAll(I18n.text("ui.b11ed988a375"), I18n.text("ui.68fa9b1439ce"), I18n.text("ui.a2db40298836"),
                I18n.text("ui.61fc75869a23"), I18n.text("ui.fed34f0449c2"));
        skewReference.getSelectionModel().selectFirst();
        skewX.disableProperty().bind(skew.selectedProperty().not());
        skewY.disableProperty().bind(skew.selectedProperty().not());
        skewReference.disableProperty().bind(skew.selectedProperty().not());
        CheckBox mirror = new CheckBox(I18n.text("ui.292669cd836a"));
        ComboBox<String> mirrorAxis = new ComboBox<>();
        mirrorAxis.getItems().setAll("X", "Y", I18n.text("ui.727f1e023503"));
        mirrorAxis.getSelectionModel().selectFirst();
        mirrorAxis.disableProperty().bind(mirror.selectedProperty().not());
        GridPane transforms = new GridPane();
        transforms.setHgap(6);
        transforms.setVgap(6);
        transforms.addRow(0, scale, new HBox(6, new Label("X"), scaleX, new Label("Y"), scaleY));
        transforms.addRow(1, skew, new HBox(6, new Label("X"), skewX, new Label("Y"), skewY));
        transforms.addRow(2, new Label(I18n.text("ui.f71f4710c6f5")), skewReference);
        transforms.addRow(3, mirror, mirrorAxis);

        ComboBox<String> fileType = new ComboBox<>();
        fileType.getItems().setAll("SVG", "PNG", "PDF");
        fileType.getSelectionModel().selectFirst();
        ComboBox<String> pageSize = new ComboBox<>();
        pageSize.getItems().setAll("Bounds", "A0", "A1", "A2", "A3", "A4", "A5", "A6", "Letter", "Legal", "Tabloid");
        pageSize.getSelectionModel().select("A4");
        ToggleGroup orientation = new ToggleGroup();
        RadioButton portrait = new RadioButton(I18n.text("ui.dd9b43ac3a92"));
        RadioButton landscape = new RadioButton(I18n.text("ui.d88a305edb3b"));
        portrait.setToggleGroup(orientation);
        landscape.setToggleGroup(orientation);
        portrait.setSelected(true);
        TextField dpi = field(ToolDefaults.text("film.dpi"));
        pageSize.disableProperty().bind(fileType.valueProperty().isNotEqualTo("PDF"));
        portrait.disableProperty().bind(pageSize.disableProperty());
        landscape.disableProperty().bind(pageSize.disableProperty());
        dpi.disableProperty().bind(fileType.valueProperty().isNotEqualTo("PNG"));
        GridPane output = new GridPane();
        output.setHgap(6);
        output.setVgap(6);
        output.addRow(0, new Label(I18n.text("ui.950a4cd778be")), fileType);
        output.addRow(1, new Label(I18n.text("ui.c6e8bfd29fb1")), pageSize);
        output.addRow(2, new Label(""), new HBox(10, portrait, landscape));
        output.addRow(3, new Label("DPI (PNG):"), dpi);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button export = new Button(I18n.text("ui.5b1e1a4f3287"));
        export.setMaxWidth(Double.MAX_VALUE);
        export.setOnAction(event -> {
            try {
                if (film.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.6442e82e7236"));
                }
                TreeItem<String> frame = box.getValue() == null ? film.getValue() : box.getValue();
                String units = host.unitsOf(film.getValue());
                double margin = negative.isSelected() ? number(boundary)
                        : FilmExporter.Options.defaults(false, units).boundary();
                FilmExporter.SkewReference reference = switch (skewReference.getSelectionModel().getSelectedIndex()) {
                    case 1 -> FilmExporter.SkewReference.BOTTOM_RIGHT;
                    case 2 -> FilmExporter.SkewReference.TOP_LEFT;
                    case 3 -> FilmExporter.SkewReference.TOP_RIGHT;
                    case 4 -> FilmExporter.SkewReference.CENTER;
                    default -> FilmExporter.SkewReference.BOTTOM_LEFT;
                };
                FilmExporter.Mirror axis = !mirror.isSelected() ? FilmExporter.Mirror.NONE
                        : switch (mirrorAxis.getSelectionModel().getSelectedIndex()) {
                            case 0 -> FilmExporter.Mirror.X;
                            case 1 -> FilmExporter.Mirror.Y;
                            default -> FilmExporter.Mirror.BOTH;
                        };
                FilmExporter.Options options = new FilmExporter.Options(negative.isSelected(), margin, number(stroke),
                        scale.isSelected() ? number(scaleX) : 1, scale.isSelected() ? number(scaleY) : 1,
                        skew.isSelected() ? number(skewX) : 0, skew.isSelected() ? number(skewY) : 0,
                        skew.isSelected() ? reference : FilmExporter.SkewReference.BOTTOM_LEFT, axis,
                        color.getText().trim(), FilmExporter.FileType.valueOf(fileType.getValue()), pageSize.getValue(),
                        portrait.isSelected(), (int) number(dpi));
                Punch mode = !punch.isSelected() || negative.isSelected() ? Punch.NONE
                        : fromExcellon.isSelected() ? Punch.EXCELLON : Punch.PADS;
                if (mode == Punch.EXCELLON && excellon.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.1b5ee389225d"));
                }
                String error = host.export(new Request(film.getValue(), frame, options, mode, excellon.getValue(),
                        mode == Punch.PADS ? number(padSize) : 0));
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.58bca1e9770a"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> onClose.run());

        GridPane look = new GridPane();
        look.setHgap(6);
        look.setVgap(6);
        look.addRow(0, new Label(I18n.text("ui.295b7174dd25")), boundary);
        look.addRow(1, new Label(I18n.text("ui.9a8cd9020a69")), stroke);
        look.addRow(2, new Label(I18n.text("ui.4fc386cae47c")), color);

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.c2e2866a582f")), film, new Label(I18n.text("ui.957bcc0d3f0a")), box,
                new HBox(10, positive, negative), look, punchBox,
                new TitledPane(I18n.text("ui.e7addca98dda"), transforms), output, export, errorLabel, close);
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
