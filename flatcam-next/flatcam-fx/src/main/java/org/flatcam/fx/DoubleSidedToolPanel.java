package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.flatcam.cam.transform.TransformOp;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * appTools/ToolDblSided.py's 2-Sided Tool: turns the copper, drills or paths of one board face into
 * the other face by mirroring them, and makes the alignment holes that keep both faces registered.
 *
 * <p>Beyond Python: any number of objects mirror in one go (Python takes one at a time), the mirror line
 * can pass through the centre or any edge of the chosen object's box, of the objects being mirrored
 * themselves, or through a point that can be typed, taken from the origin or picked on the plot; the
 * result can be a mirrored copy instead of changing the original; and the plot previews the mirror line,
 * the mirrored outlines and the alignment holes before anything is applied. Alignment holes reuse the
 * mirror line, so both faces always agree.
 */
final class DoubleSidedToolPanel {

    /** What the panel needs from the application. */
    interface Host {
        /** Every Gerber, Excellon and Geometry object, in project order. */
        List<TreeItem<String>> objects();

        /** The objects currently selected in the project tree. */
        List<TreeItem<String>> selectedObjects();

        /** {xmin, ymin, xmax, ymax} of an object, or null when it has no geometry. */
        double[] bounds(TreeItem<String> item);

        String units(TreeItem<String> item);

        /** The filled shapes of an object (copper, drills, paths), or null. */
        Geometry content(TreeItem<String> item);

        /** The outline of an object as lines (a Gerber's centreline, a Geometry's shapes), or null. */
        Geometry outline(TreeItem<String> item);

        void mirror(List<TreeItem<String>> items, TransformOp op, boolean asCopy);

        /** Creates an Excellon with each hole and its mirror image through {@code op}. */
        void createAlignmentDrills(String units, double diameter, List<Coordinate> holes, TransformOp op);

        /**
         * The centre of the drill hole (or slot end) within a few pixels of {@code click}, or null when none is
         * that close; lets a click land on the exact centre of an existing hole.
         */
        Coordinate snapToDrill(Coordinate click);

        /** Takes the next click on the plot as a point; {@code null} means the pick was cancelled. */
        void pickPoint(Consumer<Coordinate> onPoint);

        void cancelPick();

        /** Shows overlay geometry on the plot (null clears it). */
        /** {@code mirrored} is drawn in full colour, {@code reference} (where things are now) faint and dashed. */
        void preview(Geometry mirrored, Geometry reference, Geometry mirroredFill, Geometry referenceFill,
                     Geometry mirroredContent);

        void log(String message);
    }

    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)(?:[eE][-+]?\\d+)?");
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final String SELECTED_BOX = I18n.text("ui.fc63cc2edfb4");

    private DoubleSidedToolPanel() {
    }

    /**
     * Reads "x, y" pairs, one per line or in Python's "(x, y), (x, y)" form.
     *
     * @throws IllegalArgumentException naming the entry that is not a pair of numbers
     */
    static List<Coordinate> parseHoles(String text) {
        List<Coordinate> holes = new ArrayList<>();
        for (String entry : text.replaceAll("[()\\[\\]]", " ").split("[\\r\\n;]+")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            List<Double> numbers = new ArrayList<>();
            Matcher matcher = NUMBER.matcher(trimmed);
            while (matcher.find()) {
                numbers.add(Double.parseDouble(matcher.group().replace(',', '.')));
            }
            if (numbers.size() % 2 != 0 || numbers.isEmpty()) {
                throw new IllegalArgumentException("\"" + trimmed + I18n.text("ui.aea2cdb9a61a"));
            }
            for (int i = 0; i < numbers.size(); i += 2) {
                holes.add(new Coordinate(numbers.get(i), numbers.get(i + 1)));
            }
        }
        return holes;
    }

    /** Python's axis names: mirroring on the X axis negates Y (a horizontal mirror line), on Y negates X. */
    static TransformOp mirrorOp(boolean axisX, Coordinate pivot) {
        return axisX ? new TransformOp.MirrorY(pivot) : new TransformOp.MirrorX(pivot);
    }

    /**
     * Where the mirror line passes. {@code anchor} picks the centre or one edge of the box; for the edges
     * only the coordinate the line depends on comes from the edge, the other stays at the centre.
     */
    static Coordinate boxPivot(double[] bounds, int anchor) {
        double centerX = 0.5 * (bounds[0] + bounds[2]);
        double centerY = 0.5 * (bounds[1] + bounds[3]);
        return switch (anchor) {
            case 1 -> new Coordinate(bounds[0], centerY);
            case 2 -> new Coordinate(bounds[2], centerY);
            case 3 -> new Coordinate(centerX, bounds[1]);
            case 4 -> new Coordinate(centerX, bounds[3]);
            default -> new Coordinate(centerX, centerY);
        };
    }

    static Node build(Host host, Runnable onClose) {
        // --- objects to mirror ---
        ListView<TreeItem<String>> objectList = new ListView<>();
        objectList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        objectList.setPrefHeight(130);
        objectList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(TreeItem<String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getValue());
            }
        });
        objectList.getItems().setAll(host.objects());
        Button useSelection = new Button(I18n.text("ui.20e67b960b56"));
        useSelection.setOnAction(event -> {
            objectList.getSelectionModel().clearSelection();
            for (TreeItem<String> item : host.selectedObjects()) {
                objectList.getSelectionModel().select(item);
            }
        });
        useSelection.fire();

        // --- axis ---
        ToggleGroup axisGroup = new ToggleGroup();
        RadioButton axisX = new RadioButton("X");
        RadioButton axisY = new RadioButton("Y");
        axisX.setToggleGroup(axisGroup);
        axisY.setToggleGroup(axisGroup);
        axisX.setSelected(true); // Python's tools_2sided_mirror_axis default
        Tooltip axisHelp = tooltip(I18n.text("ui.70a154fcc53f"));
        axisX.setTooltip(axisHelp);
        axisY.setTooltip(axisHelp);

        // --- reference: box or point ---
        ToggleGroup referenceGroup = new ToggleGroup();
        RadioButton boardReference = new RadioButton(I18n.text("ui.9ba550c9ee1d"));
        RadioButton boxReference = new RadioButton(I18n.text("ui.a7b0fc2b8661"));
        RadioButton pointReference = new RadioButton(I18n.text("ui.d6a379129f1f"));
        boardReference.setToggleGroup(referenceGroup);
        boxReference.setToggleGroup(referenceGroup);
        pointReference.setToggleGroup(referenceGroup);
        boardReference.setSelected(true);
        boardReference.setTooltip(tooltip(I18n.text("ui.b375ccba9602")));

        ComboBox<Object> boxObject = new ComboBox<>();
        boxObject.getItems().add(SELECTED_BOX);
        boxObject.getItems().addAll(host.objects());
        boxObject.getSelectionModel().selectFirst();
        boxObject.setMaxWidth(Double.MAX_VALUE);
        boxObject.setConverter(new javafx.util.StringConverter<>() {
            @Override
            @SuppressWarnings("unchecked")
            public String toString(Object value) {
                return value instanceof TreeItem<?> item ? String.valueOf(item.getValue()) : String.valueOf(value);
            }

            @Override
            public Object fromString(String text) {
                return text;
            }
        });
        boxObject.setTooltip(tooltip(I18n.text("ui.b485efdea02b")));
        ComboBox<String> boxAnchor = new ComboBox<>();
        boxAnchor.getItems().addAll(I18n.text("ui.fed34f0449c2"), I18n.text("ui.378af8f8ecca"), I18n.text("ui.d3441e31dc99"), I18n.text("ui.ca0e290b1ddd"), I18n.text("ui.dc9063e3a5fc"));
        boxAnchor.getSelectionModel().selectFirst();
        boxAnchor.setTooltip(tooltip(I18n.text("ui.679116b3a8c8")));
        VBox boxControls = new VBox(4, boxObject);
        HBox anchorRow = new HBox(6, new Label(I18n.text("ui.948b4860f3f1")), boxAnchor);
        anchorRow.visibleProperty().bind(boardReference.selectedProperty().or(boxReference.selectedProperty()));
        anchorRow.managedProperty().bind(anchorRow.visibleProperty());

        TextField pointX = new TextField("0.0");
        TextField pointY = new TextField("0.0");
        ToolDescriptions.apply(pointX,I18n.text("ui.e700fc426f30"), I18n.text("ui.c1362fb0c1e7"));
        ToolDescriptions.apply(pointY,I18n.text("ui.7d93ce62dce4"), I18n.text("ui.84d00154fcf8"));
        pointX.setPrefColumnCount(7);
        pointY.setPrefColumnCount(7);
        CheckBox snapToDrills = new CheckBox(I18n.text("ui.00e05a97562b"));
        snapToDrills.setSelected(true);
        snapToDrills.setTooltip(tooltip(I18n.text("ui.7f6feda50770")));
        Button pickOnPlot = new Button(I18n.text("ui.9f576afb4022"));
        Button useOrigin = new Button(I18n.text("ui.7b9afa8bda07"));
        useOrigin.setOnAction(event -> {
            pointX.setText("0.0");
            pointY.setText("0.0");
        });
        VBox pointControls = new VBox(4, new HBox(6, new Label("X:"), pointX, new Label("Y:"), pointY),
                new HBox(6, pickOnPlot, useOrigin));
        boxControls.visibleProperty().bind(boxReference.selectedProperty());
        boxControls.managedProperty().bind(boxControls.visibleProperty());
        pointControls.visibleProperty().bind(pointReference.selectedProperty());
        pointControls.managedProperty().bind(pointControls.visibleProperty());

        // --- mode ---
        CheckBox asCopy = new CheckBox(I18n.text("ui.ac3ef9f9a5b0"));
        asCopy.setTooltip(tooltip(I18n.text("ui.9f59f1720f0e")));
        CheckBox showPreview = new CheckBox(I18n.text("ui.1d3636f2daf9"));
        showPreview.setSelected(true);
        CheckBox showContent = new CheckBox(I18n.text("ui.027000d59a96"));
        showContent.setSelected(true);
        showContent.setTooltip(tooltip(I18n.text("ui.65f3fac8120b")));
        showContent.disableProperty().bind(showPreview.selectedProperty().not());
        CheckBox fillInterior = new CheckBox(I18n.text("ui.966c286e3cde"));
        fillInterior.setSelected(true);
        fillInterior.setTooltip(tooltip(I18n.text("ui.34bc57a96299")));
        CheckBox showBoardOutline = new CheckBox(I18n.text("ui.30f92f04abd9"));
        showBoardOutline.setSelected(true);
        showBoardOutline.setTooltip(tooltip(I18n.text("ui.a9e2950bb3c4")));
        showBoardOutline.disableProperty().bind(showPreview.selectedProperty().not());
        fillInterior.disableProperty().bind(showPreview.selectedProperty().not().or(showBoardOutline.selectedProperty().not()));

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        // --- alignment holes ---
        TextField diameter = new TextField(ToolDefaults.text("twosided.drilldia")); // Python's tools_2sided_drilldia default
        diameter.setPrefColumnCount(6);
        TextArea holesText = new TextArea();
        ToolDescriptions.apply(holesText,I18n.text("ui.51f8a511476e"), I18n.text("ui.74b88ad14102"));
        // The prompt text of a TextArea is too dim in some themes; a normal label is always readable.
        Label holesHint = new Label(I18n.text("ui.fe343a1eb1e8"));
        holesHint.setWrapText(true);
        holesText.setPrefRowCount(4);
        Button pickHole = new Button(I18n.text("ui.82aea5c74288"));
        Button clearHoles = new Button(I18n.text("ui.5585ca453806"));
        clearHoles.setOnAction(event -> holesText.clear());

        // --- shared resolution of "what is the mirror line" ---
        java.util.function.Supplier<List<TreeItem<String>>> chosen =
                () -> List.copyOf(objectList.getSelectionModel().getSelectedItems());
        java.util.function.Supplier<Coordinate> pivot = () -> {
            if (pointReference.isSelected()) {
                return new Coordinate(parse(pointX, "X"), parse(pointY, "Y"));
            }
            if (boardReference.isSelected()) {
                double[] board = combinedBounds(host, host.objects());
                if (board == null) {
                    throw new IllegalArgumentException(I18n.text("ui.53b808c530db"));
                }
                return boxPivot(board, Math.max(0, boxAnchor.getSelectionModel().getSelectedIndex()));
            }
            Object reference = boxObject.getValue();
            double[] box;
            if (reference instanceof TreeItem<?> item) {
                @SuppressWarnings("unchecked")
                TreeItem<String> typed = (TreeItem<String>) item;
                box = host.bounds(typed);
                if (box == null) {
                    throw new IllegalArgumentException(I18n.text("ui.57d0b77abcdf"));
                }
            } else {
                box = combinedBounds(host, chosen.get());
                if (box == null) {
                    throw new IllegalArgumentException(I18n.text("ui.39dc07fd77c6"));
                }
            }
            return boxPivot(box, Math.max(0, boxAnchor.getSelectionModel().getSelectedIndex()));
        };
        java.util.function.Supplier<TransformOp> operation = () -> mirrorOp(axisX.isSelected(), pivot.get());

        Runnable refresh = () -> {
            errorLabel.setText("");
            if (!showPreview.isSelected()) {
                host.preview(null, null, null, null, null);
                return;
            }
            try {
                TransformOp op = operation.get();
                Geometry[] shown = previewGeometry(host, chosen.get(), op, axisX.isSelected(), pivot.get(),
                        safeHoles(holesText), safeNumber(diameter), showBoardOutline.isSelected());
                Geometry[] fills = fillInterior.isSelected() && showBoardOutline.isSelected()
                        ? boardFills(host, op) : new Geometry[]{null, null};
                Geometry content = null;
                if (showContent.isSelected()) {
                    List<Geometry> mirroredContent = new ArrayList<>();
                    for (TreeItem<String> item : chosen.get()) {
                        Geometry shape = host.content(item);
                        if (shape != null && !shape.isEmpty()) {
                            mirroredContent.add(op.apply(shape));
                        }
                    }
                    content = mirroredContent.isEmpty() ? null : FACTORY.buildGeometry(mirroredContent);
                }
                host.preview(shown[0], shown[1], fills[0], fills[1], content);
            } catch (IllegalArgumentException incomplete) {
                host.preview(null, null, null, null, null);
            }
        };
        List<ObservableValue<?>> triggers = List.of(objectList.getSelectionModel().selectedItemProperty(),
                axisGroup.selectedToggleProperty(), referenceGroup.selectedToggleProperty(),
                boxObject.valueProperty(), boxAnchor.valueProperty(), pointX.textProperty(), pointY.textProperty(),
                showPreview.selectedProperty(), showBoardOutline.selectedProperty(), fillInterior.selectedProperty(), showContent.selectedProperty(),
                holesText.textProperty(),
                diameter.textProperty());
        for (ObservableValue<?> trigger : triggers) {
            trigger.addListener((observable, previous, next) -> refresh.run());
        }
        objectList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<TreeItem<String>>) change -> refresh.run());

        pickOnPlot.setOnAction(event -> {
            errorLabel.setText(I18n.text("ui.adb263a0248f"));
            host.pickPoint(raw -> {
                Coordinate point = snapped(host, raw, snapToDrills.isSelected());
                if (point != null) {
                    pointX.setText(format(point.x));
                    pointY.setText(format(point.y));
                    pointReference.setSelected(true);
                }
                errorLabel.setText(point != null && raw != null && !point.equals2D(raw)
                        ? I18n.text("ui.71533d58cbc4") + format(point.x) + ", " + format(point.y) + ")." : "");
            });
        });
        pickHole.setOnAction(event -> {
            errorLabel.setText(I18n.text("ui.c9350b8314d0"));
            host.pickPoint(raw -> {
                Coordinate point = snapped(host, raw, snapToDrills.isSelected());
                if (point != null) {
                    holesText.appendText((holesText.getText().isBlank() || holesText.getText().endsWith("\n") ? "" : "\n")
                            + format(point.x) + ", " + format(point.y) + "\n");
                }
                errorLabel.setText(point != null && raw != null && !point.equals2D(raw)
                        ? I18n.text("ui.66398d8c5d42") + format(point.x) + ", " + format(point.y) + ")." : "");
            });
        });

        Button mirrorButton = new Button(I18n.text("ui.292669cd836a"));
        mirrorButton.setMaxWidth(Double.MAX_VALUE);
        mirrorButton.setOnAction(event -> {
            try {
                List<TreeItem<String>> items = chosen.get();
                if (items.isEmpty()) {
                    throw new IllegalArgumentException(I18n.text("ui.8cfe09488a68"));
                }
                host.mirror(items, operation.get(), asCopy.isSelected());
                host.preview(null, null, null, null, null);
                errorLabel.setText("");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });

        Button createHoles = new Button(I18n.text("ui.b7474bbdaf5e"));
        createHoles.setMaxWidth(Double.MAX_VALUE);
        createHoles.setTooltip(tooltip(I18n.text("ui.eed2070143cf")));
        createHoles.setOnAction(event -> {
            try {
                List<Coordinate> holes = parseHoles(holesText.getText());
                if (holes.isEmpty()) {
                    throw new IllegalArgumentException(I18n.text("ui.4b583dee7138"));
                }
                double size = parse(diameter, I18n.text("ui.7cd23abc62e0"));
                if (size <= 0) {
                    throw new IllegalArgumentException(I18n.text("ui.f787e8eb9a03"));
                }
                String units = unitsFor(host, boxObject.getValue(), chosen.get());
                host.createAlignmentDrills(units, size, holes, operation.get());
                host.preview(null, null, null, null, null);
                errorLabel.setText("");
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });

        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> {
            host.cancelPick();
            host.preview(null, null, null, null, null);
            onClose.run();
        });

        GridPane axisRow = new GridPane();
        axisRow.setHgap(10);
        axisRow.addRow(0, new Label(I18n.text("ui.614df499a158")), axisX, axisY);

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.17c87b351ba6")),
                new Label(I18n.text("ui.e443ab4e32a5")), objectList, useSelection,
                axisRow,
                new Label(I18n.text("ui.af4adff9ed45")),
                new HBox(10, boardReference, boxReference, pointReference), boxControls, anchorRow, pointControls,
                asCopy, showPreview, showContent, showBoardOutline, fillInterior, mirrorButton,
                new Separator(),
                new Label(I18n.text("ui.c9ca5c1a9ebd")),
                new HBox(6, new Label(I18n.text("ui.56d3a428a0c7")), diameter),
                holesHint, holesText, new HBox(6, pickHole, clearHoles), snapToDrills, createHoles,
                errorLabel, close);
        panel.setPadding(new Insets(6));
        refresh.run();
        return panel;
    }

    /** The click moved to the centre of a nearby drill hole when snapping is on; null stays null. */
    private static Coordinate snapped(Host host, Coordinate click, boolean snap) {
        if (click == null || !snap) {
            return click;
        }
        Coordinate center = host.snapToDrill(click);
        return center == null ? click : center;
    }

    private static double[] combinedBounds(Host host, List<TreeItem<String>> items) {
        double[] total = null;
        for (TreeItem<String> item : items) {
            double[] box = host.bounds(item);
            if (box == null) {
                continue;
            }
            total = total == null ? box.clone() : new double[]{Math.min(total[0], box[0]), Math.min(total[1], box[1]),
                    Math.max(total[2], box[2]), Math.max(total[3], box[3])};
        }
        return total;
    }

    private static String unitsFor(Host host, Object reference, List<TreeItem<String>> items) {
        if (reference instanceof TreeItem<?> item) {
            @SuppressWarnings("unchecked")
            TreeItem<String> typed = (TreeItem<String>) item;
            return host.units(typed);
        }
        if (!items.isEmpty()) {
            return host.units(items.get(0));
        }
        List<TreeItem<String>> all = host.objects();
        return all.isEmpty() ? "MM" : host.units(all.get(0));
    }

    /** The mirror line, the mirrored outlines of the objects and the alignment holes with their mirrors. */
    private static Geometry[] previewGeometry(Host host, List<TreeItem<String>> items, TransformOp op, boolean axisX,
                                            Coordinate pivot, List<Coordinate> holes, double holeDiameter,
                                            boolean boardOutline) {
        double[] region = combinedBounds(host, items);
        Envelope extent = new Envelope(pivot);
        if (region != null) {
            extent.expandToInclude(region[0], region[1]);
            extent.expandToInclude(region[2], region[3]);
        }
        Geometry board = boardOutline ? boardOutline(host) : null;
        if (board != null) {
            extent.expandToInclude(board.getEnvelopeInternal());
        }
        for (Coordinate hole : holes) {
            extent.expandToInclude(hole);
            extent.expandToInclude(op.apply(hole));
        }
        double margin = Math.max(1, 0.1 * Math.max(extent.getWidth(), extent.getHeight()));
        List<Geometry> parts = new ArrayList<>();
        List<Geometry> original = new ArrayList<>();
        parts.add(axisX
                ? FACTORY.createLineString(new Coordinate[]{new Coordinate(extent.getMinX() - margin, pivot.y),
                        new Coordinate(extent.getMaxX() + margin, pivot.y)})
                : FACTORY.createLineString(new Coordinate[]{new Coordinate(pivot.x, extent.getMinY() - margin),
                        new Coordinate(pivot.x, extent.getMaxY() + margin)}));
        if (board != null) {
            parts.add(op.apply(board));
            original.add(board);
        }
        for (TreeItem<String> item : items) {
            double[] box = host.bounds(item);
            if (box != null) {
                Geometry outline = FACTORY.toGeometry(new Envelope(box[0], box[2], box[1], box[3])).getBoundary();
                parts.add(op.apply(outline));
                original.add(outline);
            }
        }
        double radius = holeDiameter > 0 ? holeDiameter / 2 : 0.5;
        for (Coordinate hole : holes) {
            parts.add(FACTORY.createPoint(hole).buffer(radius, 12).getBoundary());
            parts.add(FACTORY.createPoint(op.apply(hole)).buffer(radius, 12).getBoundary());
        }
        return new Geometry[]{FACTORY.buildGeometry(parts), original.isEmpty() ? null : FACTORY.buildGeometry(original)};
    }

    /** {mirrored interior, original interior} of the board: the area its outline closes, else the outline's hull. */
    private static Geometry[] boardFills(Host host, TransformOp op) {
        Geometry outline = boardOutline(host);
        if (outline == null) {
            return new Geometry[]{null, null};
        }
        Geometry area;
        try {
            area = org.flatcam.cam.convert.OutlineToArea.convert(outline).area();
        } catch (IllegalArgumentException open) {
            area = outline.convexHull();
        }
        return new Geometry[]{op.apply(area), area};
    }

    private static final Pattern OUTLINE_NAME = Pattern.compile("(?i)edge|outline|profile|contorno|board|cuts");

    /**
     * The board's outline: the first object named like one (Edge_Cuts, Outline, Profile...), as its own
     * lines; otherwise the box around every object. Null when the project has no geometry.
     */
    static Geometry boardOutline(Host host) {
        for (TreeItem<String> item : host.objects()) {
            if (OUTLINE_NAME.matcher(String.valueOf(item.getValue())).find()) {
                Geometry outline = host.outline(item);
                if (outline != null && !outline.isEmpty()) {
                    return outline;
                }
            }
        }
        double[] box = combinedBounds(host, host.objects());
        return box == null ? null
                : FACTORY.toGeometry(new Envelope(box[0], box[2], box[1], box[3])).getBoundary();
    }

    private static List<Coordinate> safeHoles(TextArea text) {
        try {
            return parseHoles(text.getText());
        } catch (IllegalArgumentException invalid) {
            return List.of();
        }
    }

    private static double safeNumber(TextField field) {
        try {
            return parse(field, "");
        } catch (IllegalArgumentException invalid) {
            return 0;
        }
    }

    private static double parse(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(I18n.text("ui.1cbfa91d7a0c") + name);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(340);
        tooltip.setShowDuration(Duration.seconds(30));
        return tooltip;
    }
}
