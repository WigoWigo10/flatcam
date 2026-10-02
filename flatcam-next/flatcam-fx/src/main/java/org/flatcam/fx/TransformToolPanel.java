package org.flatcam.fx;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.flatcam.cam.transform.TransformOp;
import org.flatcam.cam.transform.TransformReference;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolTransform.py's standalone multi-object Transform tool: a
 * Reference mode (Origin/Selection/Point/Object), then Rotate, Skew X/Y
 * (linkable), Scale X/Y (linkable), Flip X/Y, Offset X/Y and Buffer. Each
 * button applies to whatever is
 * currently selected in the project tree, exactly like Python (there is no
 * single "Generate" step here, unlike every other tool panel in this app).
 *
 * <p>Every button here is plain text with no icon, matching Python's own
 * TransformUI exactly - only the Reset button carries an icon there
 * ({@code reset32.png}), which this port reuses too. Rotate/Skew/Flip icons
 * live on the Options menu's quick actions instead (see MainWindow), not here.
 */
final class TransformToolPanel {

    private TransformToolPanel() {
    }

    /**
     * @param onApply called with a function from "whatever tree items end up
     *                selected" to the TransformOp to apply to them - lets the
     *                caller (MainWindow) resolve the final selection and the
     *                "Selection center" pivot from exactly that same list.
     */
    static Node build(Function<List<TreeItem<String>>, Coordinate> selectionCenter,
                      Consumer<Function<List<TreeItem<String>>, TransformOp>> onApply, Runnable onClose) {
        return build(selectionCenter, List::of, ignored -> null, onApply, onClose);
    }

    static Node build(Function<List<TreeItem<String>>, Coordinate> selectionCenter,
                      java.util.function.Supplier<List<TreeItem<String>>> objects,
                      Function<TreeItem<String>, double[]> bounds,
                      Consumer<Function<List<TreeItem<String>>, TransformOp>> onApply, Runnable onClose) {
        ComboBox<String> referenceCombo = new ComboBox<>();
        referenceCombo.setId("transform-reference");
        referenceCombo.getItems().addAll("Origin", "Selection", "Point", "Object");
        referenceCombo.setTooltip(tooltip(
                "Origin: ponto (0, 0).\n"
                + "Selection: centro da caixa delimitadora combinada dos objetos selecionados.\n"
                + "Point: coordenada digitada abaixo.\nObject: centro da caixa do objeto escolhido, sem altera-lo se nao estiver selecionado."));
        ComboBox<TreeItem<String>> referenceObject = new ComboBox<>(); referenceObject.setId("transform-object");
        referenceObject.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TreeItem<String> item) { return item == null ? "" : item.getValue(); }
            public TreeItem<String> fromString(String value) { throw new UnsupportedOperationException(); }
        });
        referenceObject.getItems().setAll(objects.get()); referenceObject.getSelectionModel().selectFirst();
        referenceObject.setOnShowing(event -> {
            var previous=referenceObject.getValue(); referenceObject.getItems().setAll(objects.get());
            if (referenceObject.getItems().contains(previous)) referenceObject.setValue(previous);
            else referenceObject.getSelectionModel().selectFirst();
        });
        referenceObject.visibleProperty().bind(referenceCombo.valueProperty().isEqualTo("Object"));
        referenceObject.managedProperty().bind(referenceObject.visibleProperty());

        TextField pointXField = new TextField("0.0");
        TextField pointYField = new TextField("0.0");
        GridPane pointGrid = new GridPane();
        pointGrid.setHgap(8);
        pointGrid.setVgap(4);
        pointGrid.addRow(0, new Label("X:"), pointXField, new Label("Y:"), pointYField);
        pointGrid.visibleProperty().bind(referenceCombo.valueProperty().isEqualTo("Point"));
        pointGrid.managedProperty().bind(pointGrid.visibleProperty());

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Function<List<TreeItem<String>>, Coordinate> pivotResolver = selected -> switch (referenceCombo.getValue()) {
            case "Selection" -> selectionCenter.apply(selected);
            case "Point" -> new Coordinate(parse(pointXField,"Point X"), parse(pointYField,"Point Y"));
            case "Object" -> {
                var ref=referenceObject.getValue();
                var box=ref == null || !objects.get().contains(ref) ? null : bounds.apply(ref);
                if (box == null) throw new IllegalArgumentException("Escolha um objeto de referencia com geometria.");
                yield TransformReference.selectionCenter(List.of(box));
            }
            default -> TransformReference.origin();
        };

        // --- Rotate ---
        TextField rotateField = new TextField("90");
        Button rotateButton = new Button("Rotate");
        rotateButton.setMaxWidth(Double.MAX_VALUE);
        rotateButton.setTooltip(tooltip("Positivo = sentido horario. Negativo = sentido anti-horario."));
        rotateButton.setOnAction(e -> withValidAngle(rotateField, "Rotate", errorLabel, angle ->
                onApply.accept(selected -> new TransformOp.Rotate(-angle, pivotResolver.apply(selected)))));

        // --- Skew X/Y (linkable) ---
        CheckBox skewLinkCb = new CheckBox("Link");
        skewLinkCb.setSelected(true);
        TextField skewXField = new TextField("0.0");
        Button skewXButton = new Button("Skew X");
        skewXButton.setMaxWidth(Double.MAX_VALUE);
        TextField skewYField = new TextField("0.0");
        Button skewYButton = new Button("Skew Y");
        skewYButton.setMaxWidth(Double.MAX_VALUE);
        skewYField.visibleProperty().bind(skewLinkCb.selectedProperty().not());
        skewYField.managedProperty().bind(skewYField.visibleProperty());
        skewYButton.visibleProperty().bind(skewLinkCb.selectedProperty().not());
        skewYButton.managedProperty().bind(skewYButton.visibleProperty());
        skewXButton.setOnAction(e -> withValidSkewAngle(skewXField, "Skew X", errorLabel, angleX -> {
            double angleY = skewLinkCb.isSelected() ? angleX : 0;
            onApply.accept(selected -> new TransformOp.Skew(angleX, angleY, pivotResolver.apply(selected)));
        }));
        skewYButton.setOnAction(e -> withValidSkewAngle(skewYField, "Skew Y", errorLabel, angleY ->
                onApply.accept(selected -> new TransformOp.Skew(0, angleY, pivotResolver.apply(selected)))));

        // --- Scale X/Y (linkable) ---
        CheckBox scaleLinkCb = new CheckBox("Link");
        scaleLinkCb.setSelected(true);
        TextField scaleXField = new TextField("1.0");
        Button scaleXButton = new Button("Scale X");
        scaleXButton.setMaxWidth(Double.MAX_VALUE);
        TextField scaleYField = new TextField("1.0");
        Button scaleYButton = new Button("Scale Y");
        scaleYButton.setMaxWidth(Double.MAX_VALUE);
        scaleYField.visibleProperty().bind(scaleLinkCb.selectedProperty().not());
        scaleYField.managedProperty().bind(scaleYField.visibleProperty());
        scaleYButton.visibleProperty().bind(scaleLinkCb.selectedProperty().not());
        scaleYButton.managedProperty().bind(scaleYButton.visibleProperty());
        scaleXButton.setOnAction(e -> withValidScaleFactor(scaleXField, "Scale X", errorLabel, factorX -> {
            double factorY = scaleLinkCb.isSelected() ? factorX : 1;
            onApply.accept(selected -> new TransformOp.Scale(factorX, factorY, pivotResolver.apply(selected)));
        }));
        scaleYButton.setOnAction(e -> withValidScaleFactor(scaleYField, "Scale Y", errorLabel, factorY ->
                onApply.accept(selected -> new TransformOp.Scale(1, factorY, pivotResolver.apply(selected)))));

        // --- Flip X/Y ---
        Button flipXButton = new Button("Flip on X");
        flipXButton.setMaxWidth(Double.MAX_VALUE);
        flipXButton.setOnAction(e -> {
            try { errorLabel.setText(""); onApply.accept(selected -> new TransformOp.MirrorX(pivotResolver.apply(selected))); }
            catch (IllegalArgumentException error) { errorLabel.setText(error.getMessage()); }
        });
        Button flipYButton = new Button("Flip on Y");
        flipYButton.setMaxWidth(Double.MAX_VALUE);
        flipYButton.setOnAction(e -> {
            try { errorLabel.setText(""); onApply.accept(selected -> new TransformOp.MirrorY(pivotResolver.apply(selected))); }
            catch (IllegalArgumentException error) { errorLabel.setText(error.getMessage()); }
        });

        // --- Offset X/Y ---
        TextField offsetXField = new TextField("0.0");
        Button offsetXButton = new Button("Offset X");
        offsetXButton.setMaxWidth(Double.MAX_VALUE);
        offsetXButton.setOnAction(e -> withNonZero(offsetXField, "Offset X", errorLabel, dx ->
                onApply.accept(selected -> new TransformOp.Offset(dx, 0))));
        TextField offsetYField = new TextField("0.0");
        for (TextField field : List.of(pointXField, pointYField, rotateField,
                skewXField, skewYField, scaleXField, scaleYField, offsetXField, offsetYField)) {
            field.setPrefColumnCount(6);
            field.setMinWidth(0);
        }
        Button offsetYButton = new Button("Offset Y");
        offsetYButton.setMaxWidth(Double.MAX_VALUE);
        offsetYButton.setOnAction(e -> withNonZero(offsetYField, "Offset Y", errorLabel, dy ->
                onApply.accept(selected -> new TransformOp.Offset(0, dy))));

        Runnable resetToDefaults = () -> {
            referenceCombo.setValue("Selection");
            pointXField.setText("0.0");
            pointYField.setText("0.0");
            rotateField.setText("90");
            skewLinkCb.setSelected(true);
            skewXField.setText("0.0");
            skewYField.setText("0.0");
            scaleLinkCb.setSelected(true);
            scaleXField.setText("1.0");
            scaleYField.setText("1.0");
            offsetXField.setText("0.0");
            offsetYField.setText("0.0");
            errorLabel.setText("");
        };
        resetToDefaults.run();

        Button resetButton = new Button("Reset Tool");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(e -> resetToDefaults.run());
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        GridPane referenceGrid = new GridPane();
        referenceGrid.setHgap(8);
        referenceGrid.setVgap(8);
        referenceGrid.addRow(0, new Label("Reference:"), referenceCombo);
        referenceGrid.add(pointGrid, 0, 1, 2, 1);
        referenceGrid.add(referenceObject, 0, 2, 2, 1);

        TextField bufferDistance=new TextField("0.1"); bufferDistance.setId("transform-buffer-distance");
        TextField bufferPercent=new TextField("10"); bufferPercent.setId("transform-buffer-percent");
        CheckBox rounded=new CheckBox("Rounded"); rounded.setSelected(true);
        Button distanceBuffer=new Button("Buffer por distancia"); distanceBuffer.setId("transform-buffer-apply");
        Button factorBuffer=new Button("Buffer por percentual"); factorBuffer.setId("transform-buffer-factor-apply");
        distanceBuffer.setOnAction(event -> withNonZero(bufferDistance,"Buffer",errorLabel,value ->
                onApply.accept(selected -> new TransformOp.Buffer(value,false,rounded.isSelected()))));
        factorBuffer.setOnAction(event -> withNonZero(bufferPercent,"Buffer (%)",errorLabel,value ->
                onApply.accept(selected -> new TransformOp.Buffer(1+value/100,true,rounded.isSelected()))));
        ToolDescriptions.apply(distanceBuffer,"Buffer por distancia","Expande com distancia positiva; contrai com negativa. Unidades do objeto (mm ou in). Rounded arredonda cantos; desmarcado usa cantos mitrados.\n\nExcellon: altera diametros pelo valor informado, mantendo centros e slots, como o Python. Geometry/Gerber: altera areas/caminhos; nao e compensacao CNC. Resultado vazio e recusado. Execute em objetos com a mesma unidade.");
        ToolDescriptions.apply(factorBuffer,"Buffer por percentual","Usa fator 1 + percentual/100; exige percentual maior que -100 e diferente de zero. Escala formas em seus centros locais, nao pela referencia global. Excellon altera apenas diametros; slots mantem extremos. Nao muda diametros de ferramentas Geometry.\n\nGerber: contornos sao aproximados; metadados de aberturas permanecem originais. Confira resultado e exportacao.");
        VBox bufferControls=new VBox(6,new javafx.scene.layout.FlowPane(8,6,new Label("Distance:"),bufferDistance,distanceBuffer),
                new javafx.scene.layout.FlowPane(8,6,new Label("Percent (%):"),bufferPercent,factorBuffer),rounded);
        resetButton.addEventHandler(javafx.event.ActionEvent.ACTION,event -> { bufferDistance.setText("0.1"); bufferPercent.setText("10"); rounded.setSelected(true); });

        GridPane rotateGrid = new GridPane();
        rotateGrid.setHgap(8);
        rotateGrid.setVgap(8);
        rotateGrid.addRow(0, new Label("Angle:"), rotateField, rotateButton);

        GridPane skewGrid = new GridPane();
        skewGrid.setHgap(8);
        skewGrid.setVgap(8);
        skewGrid.addRow(0, new Label("Angle X:"), skewXField, skewXButton);
        skewGrid.addRow(1, new Label("Angle Y:"), skewYField, skewYButton);
        skewGrid.add(skewLinkCb, 0, 2);

        GridPane scaleGrid = new GridPane();
        scaleGrid.setHgap(8);
        scaleGrid.setVgap(8);
        scaleGrid.addRow(0, new Label("Factor X:"), scaleXField, scaleXButton);
        scaleGrid.addRow(1, new Label("Factor Y:"), scaleYField, scaleYButton);
        scaleGrid.add(scaleLinkCb, 0, 2);

        GridPane flipGrid = new GridPane();
        flipGrid.setHgap(8);
        flipGrid.setVgap(8);
        flipGrid.addRow(0, flipXButton, flipYButton);

        GridPane offsetGrid = new GridPane();
        offsetGrid.setHgap(8);
        offsetGrid.setVgap(8);
        offsetGrid.addRow(0, new Label("Value X:"), offsetXField, offsetXButton);
        offsetGrid.addRow(1, new Label("Value Y:"), offsetYField, offsetYButton);

        Label note = new Label("Cada botao aplica de imediato aos objetos selecionados no projeto.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");

        VBox box = new VBox(6, new Label("Transform Tool"),
                note, sectionTitle("REFERENCE"), referenceGrid,
                sectionTitle("ROTATE"), rotateGrid,
                sectionTitle("SKEW"), skewGrid,
                sectionTitle("SCALE"), scaleGrid,
                sectionTitle("FLIP"), flipGrid,
                sectionTitle("OFFSET"), offsetGrid,
                sectionTitle("BUFFER"), bufferControls,
                errorLabel, new Separator(), resetButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    /** See NccToolPanel's own copy of this helper for why showDuration needs extending. */
    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDuration(Duration.INDEFINITE);
        return tooltip;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-section-title");
        return label;
    }

    private interface DoubleConsumerWithLabel {
        void accept(double value);
    }

    private static void withValidAngle(TextField field, String name, Label errorLabel, DoubleConsumerWithLabel action) {
        try {
            double value = parse(field, name);
            errorLabel.setText("");
            action.accept(value);
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static void withValidSkewAngle(TextField field, String name, Label errorLabel, DoubleConsumerWithLabel action) {
        try {
            double value = parse(field, name);
            if (value == 0 || value == 90 || value == 180 || value == -90 || value == -180) {
                throw new IllegalArgumentException(name + ": angulo invalido (0, 90 e 180 nao sao permitidos)");
            }
            errorLabel.setText("");
            action.accept(value);
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static void withValidScaleFactor(TextField field, String name, Label errorLabel, DoubleConsumerWithLabel action) {
        try {
            double value = parse(field, name);
            if (value == 0 || value == 1) {
                throw new IllegalArgumentException(name + ": fator de escala nao pode ser 0 ou 1");
            }
            errorLabel.setText("");
            action.accept(value);
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static void withNonZero(TextField field, String name, Label errorLabel, DoubleConsumerWithLabel action) {
        try {
            double value = parse(field, name);
            if (value == 0) {
                throw new IllegalArgumentException(name + ": valor nao pode ser 0");
            }
            errorLabel.setText("");
            action.accept(value);
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
        }
    }

    private static double parse(TextField field, String name) {
        try {
            double value=Double.parseDouble(field.getText().trim().replace(',', '.'));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": numero invalido");
        }
    }

}
