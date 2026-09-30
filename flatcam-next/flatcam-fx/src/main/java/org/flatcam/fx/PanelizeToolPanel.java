package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.beans.value.ObservableValue;
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
import org.flatcam.cam.panel.Panelize;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * appTools/ToolPanelize.py: repeats a Gerber, Excellon or Geometry in a grid of columns and rows. The
 * copies are one reference box apart (the object's own box, or another object's) plus the spacing, and an
 * optional size limit trims the grid the way Python does. A Gerber can also become a Geometry panel. The plot
 * previews every copy's outline and the panel size before anything is created.
 */
final class PanelizeToolPanel {

    /** What the user asked for; {@code layout} is already trimmed by the size limit. */
    record Request(TreeItem<String> source, Panelize.Layout layout, boolean gerberAsGeometry) {
    }

    interface Host {
        List<TreeItem<String>> sources();

        TreeItem<String> initialSource();

        /** {xmin, ymin, xmax, ymax} of an object, or null. */
        double[] bounds(TreeItem<String> item);

        boolean isGerber(TreeItem<String> item);

        void preview(Geometry outlines);

        void panelize(Request request);
    }

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private PanelizeToolPanel() {
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

        ToggleGroup referenceGroup = new ToggleGroup();
        RadioButton ownBox = new RadioButton("Caixa do proprio objeto");
        RadioButton otherBox = new RadioButton("Caixa de outro objeto");
        ownBox.setToggleGroup(referenceGroup);
        otherBox.setToggleGroup(referenceGroup);
        ownBox.setSelected(true);
        ComboBox<TreeItem<String>> reference = new ComboBox<>();
        reference.getItems().setAll(host.sources());
        reference.setConverter(names);
        reference.setMaxWidth(Double.MAX_VALUE);
        reference.getSelectionModel().selectFirst();
        reference.visibleProperty().bind(otherBox.selectedProperty());
        reference.managedProperty().bind(reference.visibleProperty());
        reference.setTooltip(tooltip("As copias ficam separadas pelo tamanho desta caixa (por exemplo o contorno da "
                + "placa) mais o espacamento, mesmo que o objeto seja menor."));

        // Python's defaults: 1 x 1, no spacing, limit 200 x 290 off, panel of the same kind.
        TextField columns = new TextField("2");
        TextField rows = new TextField("2");
        TextField spacingColumns = new TextField("0.0");
        TextField spacingRows = new TextField("0.0");
        for (TextField field : List.of(columns, rows, spacingColumns, spacingRows)) {
            field.setPrefColumnCount(5);
        }
        CheckBox constrain = new CheckBox("Limitar o tamanho do painel");
        constrain.setTooltip(tooltip("Se a grade nao couber, colunas e linhas diminuem ate caber, como no Python."));
        TextField limitWidth = new TextField("200.0");
        TextField limitHeight = new TextField("290.0");
        limitWidth.setPrefColumnCount(5);
        limitHeight.setPrefColumnCount(5);
        HBox limits = new HBox(6, new Label("Largura:"), limitWidth, new Label("Altura:"), limitHeight);
        limits.visibleProperty().bind(constrain.selectedProperty());
        limits.managedProperty().bind(limits.visibleProperty());
        CheckBox gerberAsGeometry = new CheckBox("Gerber: criar o painel como Geometry");
        gerberAsGeometry.setTooltip(tooltip("Sem marcar, o painel de um Gerber e outro Gerber (com as aberturas)."));

        Label summary = new Label();
        summary.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        java.util.function.Supplier<Request> request = () -> {
            TreeItem<String> item = source.getValue();
            if (item == null || host.bounds(item) == null) {
                throw new IllegalArgumentException("Escolha um objeto com geometria");
            }
            double[] box = host.bounds(item);
            if (otherBox.isSelected()) {
                TreeItem<String> other = reference.getValue();
                double[] otherBounds = other == null ? null : host.bounds(other);
                if (otherBounds == null) {
                    throw new IllegalArgumentException("O objeto de referencia nao tem geometria");
                }
                box = otherBounds;
            }
            Panelize.Layout layout = Panelize.layout(box, integer(columns, "Colunas"), integer(rows, "Linhas"),
                    number(spacingColumns, "Espacamento das colunas"), number(spacingRows, "Espacamento das linhas"),
                    constrain.isSelected() ? number(limitWidth, "Largura") : Double.NaN,
                    constrain.isSelected() ? number(limitHeight, "Altura") : Double.NaN);
            return new Request(item, layout, host.isGerber(item) && gerberAsGeometry.isSelected());
        };
        Runnable refresh = () -> {
            errorLabel.setText("");
            try {
                Request current = request.get();
                Panelize.Layout layout = current.layout();
                double[] box = otherBox.isSelected() ? host.bounds(reference.getValue()) : host.bounds(current.source());
                double[] own = host.bounds(current.source());
                List<Geometry> outlines = new ArrayList<>();
                for (double[] cell : layout.offsets()) {
                    outlines.add(FACTORY.toGeometry(new Envelope(own[0] + cell[0], own[2] + cell[0], own[1] + cell[1],
                            own[3] + cell[1])).getBoundary());
                }
                host.preview(FACTORY.buildGeometry(outlines));
                double[] size = layout.size(box[2] - box[0], box[3] - box[1], number(spacingColumns, ""),
                        number(spacingRows, ""));
                summary.setText(String.format(Locale.ROOT, "%d x %d = %d copias; painel %.2f x %.2f%s",
                        layout.columns(), layout.rows(), layout.columns() * layout.rows(), size[0], size[1],
                        layout.constrained() ? " (grade reduzida pelo limite)" : ""));
            } catch (IllegalArgumentException incomplete) {
                host.preview(null);
                summary.setText("");
                errorLabel.setText(incomplete.getMessage());
            }
        };
        List<ObservableValue<?>> triggers = List.of(source.valueProperty(), reference.valueProperty(),
                referenceGroup.selectedToggleProperty(), columns.textProperty(), rows.textProperty(),
                spacingColumns.textProperty(), spacingRows.textProperty(), constrain.selectedProperty(),
                limitWidth.textProperty(), limitHeight.textProperty());
        for (ObservableValue<?> trigger : triggers) {
            trigger.addListener((observable, previous, next) -> refresh.run());
        }
        gerberAsGeometry.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> source.getValue() != null && host.isGerber(source.getValue()), source.valueProperty()));
        gerberAsGeometry.managedProperty().bind(gerberAsGeometry.visibleProperty());

        Button panelize = new Button("Criar painel");
        panelize.setMaxWidth(Double.MAX_VALUE);
        panelize.setOnAction(event -> {
            try {
                Request current = request.get();
                errorLabel.setText("");
                host.preview(null);
                host.panelize(current);
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button("Fechar");
        close.setOnAction(event -> {
            host.preview(null);
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label("Objeto a repetir:"), source,
                new Label("Distancia entre copias baseada em:"), new VBox(4, ownBox, otherBox), reference,
                new HBox(6, new Label("Colunas:"), columns, new Label("Linhas:"), rows),
                new HBox(6, new Label("Espacamento colunas:"), spacingColumns),
                new HBox(6, new Label("Espacamento linhas:"), spacingRows),
                constrain, limits, gerberAsGeometry, summary,
                new Separator(), panelize, errorLabel, close);
        panel.setPadding(new Insets(6));
        refresh.run();
        return panel;
    }

    private static int integer(TextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " deve ser um inteiro positivo");
        }
    }

    private static double number(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Valor invalido em " + name.toLowerCase(Locale.ROOT));
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
