package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.PaintParameters;
import org.flatcam.cam.ncc.PaintToolSettings;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

/**
 * appTools/ToolPaint.py: fills polygons of a Gerber or Geometry with toolpaths (Standard, Seed, Lines or
 * Combo), tool by tool, with overlap, margin, connect, contour, tool order and rest machining. The polygons
 * to paint are all of them, one picked by a click, those touched by a rectangle or a polygon drawn on the
 * plot, or those touching a reference object. Python's Laser Lines method depends on Gerber apertures and
 * is not offered; the defaults are Python's (0.3 mm tool, 20% overlap, reverse order, connect and contour).
 */
final class PaintToolPanel {

    interface Host {
        /** The Gerber and Geometry objects that can be painted. */
        List<TreeItem<String>> sources();

        TreeItem<String> initialSource();

        String units(TreeItem<String> item);

        /** The filled polygons of the object, as one geometry (may be empty). */
        Geometry polygons(TreeItem<String> item);

        void pickPoint(Consumer<Coordinate> onPoint);

        void cancelPick();

        void selectArea(Geometry source, boolean polygonShape, Consumer<Geometry> onArea, Runnable onCancel);

        void cancelAreaSelection();

        void preview(Geometry geometry);

        void paint(TreeItem<String> source, String units, Geometry polygons, PaintParameters parameters);

        default List<org.flatcam.app.project.LegacyToolsDatabase.PaintTool> databaseTools() { return List.of(); }
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private PaintToolPanel() {
    }

    /** Diameters written as "0.3, 0.5;1" (comma, semicolon or space separated; a decimal comma is not accepted). */
    static List<Double> parseDiameters(String text) {
        List<Double> diameters = new ArrayList<>();
        for (String piece : text.split("[;,\\s]+")) {
            if (piece.isBlank()) {
                continue;
            }
            try {
                double value = Double.parseDouble(piece);
                if (!(value > 0) || !Double.isFinite(value)) {
                    throw new NumberFormatException();
                }
                if (!diameters.contains(value)) {
                    diameters.add(value);
                }
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("\"" + piece + I18n.text("ui.b722321d0b1d"));
            }
        }
        if (diameters.isEmpty()) {
            throw new IllegalArgumentException(I18n.text("ui.7c03b8199560"));
        }
        return diameters;
    }

    static List<Polygon> polygonsOf(Geometry geometry) {
        List<Polygon> polygons = new ArrayList<>();
        if (geometry != null) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry part = geometry.getGeometryN(i);
                if (part instanceof Polygon polygon && !polygon.isEmpty() && polygon.getArea() > 0) {
                    polygons.add(polygon);
                } else if (part.getNumGeometries() > 1) {
                    polygons.addAll(polygonsOf(part));
                }
            }
        }
        return polygons;
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> source = new ComboBox<>();
        source.getItems().setAll(host.sources());
        source.setMaxWidth(Double.MAX_VALUE);
        javafx.util.StringConverter<TreeItem<String>> names = new javafx.util.StringConverter<>() {
            @Override
            public String toString(TreeItem<String> item) {
                return item == null ? "" : item.getValue();
            }

            @Override
            public TreeItem<String> fromString(String text) {
                return null;
            }
        };
        source.setConverter(names);
        source.getSelectionModel().select(host.initialSource());
        if (source.getValue() == null) {
            source.getSelectionModel().selectFirst();
        }

        ToggleGroup selectionGroup = new ToggleGroup();
        RadioButton all = new RadioButton(I18n.text("ui.0364c14f702a"));
        RadioButton single = new RadioButton(I18n.text("ui.ba89e6f82dfb"));
        RadioButton rectangle = new RadioButton(I18n.text("ui.32d00abf8ddd"));
        RadioButton polygonArea = new RadioButton(I18n.text("ui.5848385a0a90"));
        RadioButton reference = new RadioButton(I18n.text("ui.a8e4c7c07fd3"));
        for (RadioButton radio : List.of(all, single, rectangle, polygonArea, reference)) {
            radio.setToggleGroup(selectionGroup);
        }
        all.setSelected(true);

        ComboBox<TreeItem<String>> referenceObject = new ComboBox<>();
        referenceObject.getItems().setAll(host.sources());
        referenceObject.setMaxWidth(Double.MAX_VALUE);
        referenceObject.setConverter(names);
        referenceObject.visibleProperty().bind(reference.selectedProperty());
        referenceObject.managedProperty().bind(referenceObject.visibleProperty());
        referenceObject.setTooltip(tooltip(I18n.text("ui.081c258617a2")));

        Label selectedLabel = new Label();
        Button choose = new Button(I18n.text("ui.6a57618c2593"));
        Button clear = new Button(I18n.text("ui.33a042c0fd07"));
        List<Polygon> chosen = new ArrayList<>();
        choose.visibleProperty().bind(all.selectedProperty().not());
        choose.managedProperty().bind(choose.visibleProperty());
        clear.visibleProperty().bind(all.selectedProperty().not());
        clear.managedProperty().bind(clear.visibleProperty());

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Runnable showSelection = () -> {
            if (all.isSelected()) {
                int count = source.getValue() == null ? 0 : polygonsOf(host.polygons(source.getValue())).size();
                selectedLabel.setText(count + I18n.text("ui.cf58180bf39e"));
                host.preview(null);
            } else {
                selectedLabel.setText(chosen.size() + I18n.text("ui.b0cf5d3ccf15"));
                host.preview(chosen.isEmpty() ? null : FACTORY.buildGeometry(new ArrayList<Geometry>(chosen)));
            }
        };
        Runnable stopPicking = () -> {
            host.cancelPick();
            host.cancelAreaSelection();
        };
        selectionGroup.selectedToggleProperty().addListener((observable, previous, next) -> {
            stopPicking.run();
            chosen.clear();
            errorLabel.setText("");
            showSelection.run();
        });
        source.valueProperty().addListener((observable, previous, next) -> {
            stopPicking.run();
            chosen.clear();
            showSelection.run();
        });
        clear.setOnAction(event -> {
            stopPicking.run();
            chosen.clear();
            showSelection.run();
        });
        choose.setOnAction(event -> {
            errorLabel.setText("");
            TreeItem<String> item = source.getValue();
            if (item == null) {
                errorLabel.setText(I18n.text("ui.4a12932cb623"));
                return;
            }
            Geometry polygons = host.polygons(item);
            List<Polygon> candidates = polygonsOf(polygons);
            stopPicking.run();
            if (single.isSelected()) {
                errorLabel.setText(I18n.text("ui.f0b1c3c71784"));
                pickNext(host, candidates, chosen, showSelection, errorLabel);
            } else if (rectangle.isSelected() || polygonArea.isSelected()) {
                errorLabel.setText(rectangle.isSelected() ? I18n.text("ui.ab86250ca50b") : I18n.text("ui.c4d3bb6091d9"));
                host.selectArea(polygons, polygonArea.isSelected(), area -> {
                    for (Polygon candidate : candidates) {
                        if (candidate.intersects(area) && !chosen.contains(candidate)) {
                            chosen.add(candidate);
                        }
                    }
                    errorLabel.setText("");
                    showSelection.run();
                }, () -> errorLabel.setText(""));
            } else if (reference.isSelected()) {
                TreeItem<String> ref = referenceObject.getValue();
                if (ref == null) {
                    errorLabel.setText(I18n.text("ui.b6d2a2388956"));
                    return;
                }
                Geometry referenceShape = host.polygons(ref);
                for (Polygon candidate : candidates) {
                    if (referenceShape != null && !referenceShape.isEmpty() && candidate.intersects(referenceShape)
                            && !chosen.contains(candidate)) {
                        chosen.add(candidate);
                    }
                }
                showSelection.run();
            }
        });
        showSelection.run();

        // --- parameters (Python's defaults) ---
        // The defaults of the units of the object the panel opens with (millimetres when there is none).
        boolean metric = host.initialSource() == null || !"IN".equalsIgnoreCase(host.units(host.initialSource()));
        TextField diameters = new TextField(ToolDefaults.text("paint.tooldia", metric));
        diameters.setMinWidth(0);
        HBox.setHgrow(diameters, javafx.scene.layout.Priority.ALWAYS);
        diameters.setTooltip(tooltip(I18n.text("ui.20642ea8eeba")));
        TextField overlap = new TextField(ToolDefaults.text("paint.overlap", metric));
        TextField margin = new TextField(ToolDefaults.text("paint.margin", metric));
        overlap.setPrefColumnCount(5);
        margin.setPrefColumnCount(5);
        ComboBox<NccMethod> method = new ComboBox<>();
        method.getItems().addAll(NccMethod.STANDARD, NccMethod.SEED, NccMethod.LINES, NccMethod.COMBO);
        method.setValue(ToolDefaults.choice("paint.method", NccMethod.class));
        CheckBox connect = new CheckBox(I18n.text("ui.9e2edea69e20"));
        connect.setSelected(ToolDefaults.flag("paint.connect"));
        CheckBox contour = new CheckBox(I18n.text("ui.8185e1c34543"));
        contour.setSelected(ToolDefaults.flag("paint.contour"));
        ComboBox<NccOrder> order = new ComboBox<>();
        order.getItems().addAll(NccOrder.values());
        order.setValue(ToolDefaults.choice("paint.order", NccOrder.class));
        CheckBox rest = new CheckBox("Rest machining");
        rest.setTooltip(tooltip(I18n.text("ui.9d7de9524372")));
        order.disableProperty().bind(rest.selectedProperty());
        TableView<Double> toolTable = new TableView<>(); toolTable.setId("paint-tools");
        toolTable.setMinWidth(0); toolTable.setPrefHeight(130);
        toolTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<Double, Number> diameterColumn = new TableColumn<>(I18n.text("ui.7cd23abc62e0"));
        diameterColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue()));
        toolTable.getColumns().add(diameterColumn);
        ToolDescriptions.apply(toolTable, I18n.text("ui.4957942fd71b"),
                I18n.text("ui.94944eac970d"));
        java.util.Map<Double, String[]> rows = new java.util.LinkedHashMap<>();
        java.util.function.Supplier<String[]> capture = () -> new String[]{overlap.getText(), margin.getText(), method.getValue().name(),
                Boolean.toString(connect.isSelected()), Boolean.toString(contour.isSelected())};
        java.util.function.Consumer<String[]> restore = s -> { overlap.setText(s[0]); margin.setText(s[1]);
            method.setValue(NccMethod.valueOf(s[2])); connect.setSelected(Boolean.parseBoolean(s[3])); contour.setSelected(Boolean.parseBoolean(s[4])); };
        Double[] editing = {0.3}; boolean[] syncing = {false};
        rows.put(0.3, capture.get()); toolTable.getItems().add(0.3); toolTable.getSelectionModel().selectFirst();
        Label parameterTitle = new Label(I18n.text("ui.298cd24b02eb"));
        toolTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (syncing[0] || selected == null) return;
            rows.put(editing[0], capture.get()); editing[0] = selected;
            restore.accept(rows.get(selected)); parameterTitle.setText(I18n.text("ui.1228ea6a807e") + selected);
        });
        Runnable synchronizeTools = () -> {
            List<Double> values = parseDiameters(diameters.getText());
            rows.put(editing[0], capture.get());
            rows.keySet().retainAll(values);
            for (double value : values) rows.putIfAbsent(value, capture.get());
            syncing[0] = true;
            try {
                toolTable.getItems().setAll(values);
                editing[0] = values.contains(editing[0]) ? editing[0] : values.getFirst();
                toolTable.getSelectionModel().select(editing[0]); restore.accept(rows.get(editing[0]));
                parameterTitle.setText(I18n.text("ui.1228ea6a807e") + editing[0]);
            } finally { syncing[0] = false; }
        };
        Button updateTools = new Button(I18n.text("ui.c370b549c472")); updateTools.setId("paint-update-tools");
        ToolDescriptions.apply(updateTools, I18n.text("ui.b61e1a7d9a7a"),
                I18n.text("ui.b6d5202e04ae"));
        updateTools.setOnAction(event -> { try { synchronizeTools.run(); errorLabel.setText(""); }
            catch (IllegalArgumentException invalid) { errorLabel.setText(invalid.getMessage()); } });
        Button applyAll = new Button(I18n.text("ui.36c3d0d83bb8")); applyAll.setId("paint-apply-all");
        applyAll.setMaxWidth(Double.MAX_VALUE);
        ToolDescriptions.apply(applyAll, I18n.text("ui.14162f7da79a"),
                I18n.text("ui.067a7657fa42"));
        applyAll.setOnAction(event -> { try { synchronizeTools.run(); for (double d : toolTable.getItems()) rows.put(d,capture.get()); }
            catch (IllegalArgumentException invalid) { errorLabel.setText(invalid.getMessage()); } });
        VBox database = DatabaseToolPicker.build("paint-db", host::databaseTools, selected -> {
            synchronizeTools.run();
            PaintParameters p = selected.parameters();
            double diameter = p.toolDiameters().getFirst();
            if (!toolTable.getItems().contains(diameter)) {
                List<Double> values = new ArrayList<>(toolTable.getItems()); values.add(diameter);
                diameters.setText(values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("; ")));
                synchronizeTools.run();
            }
            toolTable.getSelectionModel().select(Double.valueOf(diameter));
            overlap.setText(Double.toString(p.overlapFraction() * 100)); margin.setText(Double.toString(p.offset()));
            method.setValue(p.method()); connect.setSelected(p.connect()); contour.setSelected(p.contour());
        }, errorLabel);
        Label transferNote = new Label(I18n.text("ui.56a734d154cf"));
        transferNote.setWrapText(true);

        Button paint = new Button(I18n.text("ui.0e4f5ddd5cbf"));
        paint.setId("paint-generate"); overlap.setId("paint-overlap"); margin.setId("paint-margin");
        method.setId("paint-method"); diameters.setId("paint-diameters"); errorLabel.setId("paint-error");
        margin.setTooltip(tooltip(I18n.text("ui.1d8ad7c5e49e")));
        paint.setMaxWidth(Double.MAX_VALUE);
        ToolDescriptions.apply(paint, I18n.text("ui.0127d5fe2cc0"),
                I18n.text("ui.31777f4df7cb"));
        paint.setOnAction(event -> {
            try {
                TreeItem<String> item = source.getValue();
                if (item == null) {
                    throw new IllegalArgumentException(I18n.text("ui.4a12932cb623"));
                }
                Geometry polygons = all.isSelected() ? host.polygons(item)
                        : chosen.isEmpty() ? null : FACTORY.buildGeometry(new ArrayList<Geometry>(chosen));
                if (polygons == null || polygons.isEmpty()) {
                    throw new IllegalArgumentException(all.isSelected() ? I18n.text("ui.b422a75b49fb")
                            : I18n.text("ui.410a190f7174"));
                }
                synchronizeTools.run();
                java.util.Map<Double, PaintToolSettings> settings = new java.util.LinkedHashMap<>();
                for (double diameter : toolTable.getItems()) {
                    String[] s = rows.get(diameter);
                    try { settings.put(diameter,new PaintToolSettings(Double.parseDouble(s[0].trim().replace(',','.'))/100,
                            Double.parseDouble(s[1].trim().replace(',','.')), NccMethod.valueOf(s[2]),
                            Boolean.parseBoolean(s[3]),Boolean.parseBoolean(s[4]))); }
                    catch (RuntimeException invalid) { throw new IllegalArgumentException(I18n.text("ui.10f99bd620cc") + diameter + ": " + invalid.getMessage()); }
                }
                PaintParameters parameters = new PaintParameters(List.copyOf(toolTable.getItems()),
                        number(overlap, I18n.text("ui.ed0baca2b3b3")) / 100.0, number(margin, I18n.text("ui.c707749bff8f")), method.getValue(),
                        connect.isSelected(), contour.isSelected(), order.getValue(), rest.isSelected(), settings);
                stopPicking.run();
                errorLabel.setText("");
                host.paint(item, host.units(item), polygons, parameters);
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> {
            stopPicking.run();
            host.preview(null);
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.00c986260e98")), source,
                new Label(I18n.text("ui.83e7115ae275")),
                new VBox(4, all, single, rectangle, polygonArea, reference), referenceObject,
                new HBox(6, choose, clear), selectedLabel,
                new Separator(),
                new Label(I18n.text("ui.87aaa7e961d0")), new HBox(6,diameters,updateTools), toolTable, database, transferNote, parameterTitle,
                new HBox(6, new Label(I18n.text("ui.ea6b271e4830")), overlap, new Label(I18n.text("ui.16bab5b0d1de")), margin),
                new HBox(6, new Label("Metodo:"), method),
                new HBox(10, connect, contour), applyAll,
                new HBox(6, new Label(I18n.text("ui.3b41aae0bd17")), order), rest,
                paint, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    /** One click picks the polygon under it, then waits for the next click until Esc. */
    private static void pickNext(Host host, List<Polygon> candidates, List<Polygon> chosen, Runnable showSelection,
                                 Label errorLabel) {
        host.pickPoint(point -> {
            if (point == null) {
                errorLabel.setText("");
                return;
            }
            org.locationtech.jts.geom.Point at = FACTORY.createPoint(point);
            for (Polygon candidate : candidates) {
                if (candidate.contains(at)) {
                    if (!chosen.contains(candidate)) {
                        chosen.add(candidate);
                    }
                    break;
                }
            }
            showSelection.run();
            pickNext(host, candidates, chosen, showSelection, errorLabel);
        });
    }

    private static double number(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(I18n.text("ui.1cbfa91d7a0c") + name.toLowerCase(Locale.ROOT));
        }
    }

    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(340);
        tooltip.setShowDuration(Duration.seconds(30));
        return tooltip;
    }
}
