package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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

/**
 * appTools/ToolPanelize.py: repeats a Gerber, Excellon or Geometry in a grid of columns and rows. The
 * copies are one reference box apart (the object's own box, or another object's) plus the spacing, and an
 * optional size limit trims the grid the way Python does. A Gerber can also become a Geometry panel. The plot
 * previews every copy's outline and the panel size before anything is created.
 */
final class PanelizeToolPanel {

    /** What the user asked for; {@code layout} is already trimmed by the size limit. */
    record Request(List<TreeItem<String>> sources, TreeItem<String> reference, double[] referenceBox,
                   Panelize.Layout layout, boolean gerberAsGeometry) {
        Request {
            sources = List.copyOf(sources); referenceBox = referenceBox.clone();
            if (sources.isEmpty()) throw new IllegalArgumentException("Escolha ao menos um objeto.");
            if (new java.util.HashSet<>(sources).size() != sources.size())
                throw new IllegalArgumentException("Um objeto nao pode aparecer duas vezes no conjunto.");
        }
        @Override public double[] referenceBox() { return referenceBox.clone(); }
        TreeItem<String> source() { return sources.getFirst(); }
    }

    record PreviewOptions(boolean showContent, boolean showOutline, boolean fillInterior, TreeItem<String> contour) { }

    interface Host {
        List<TreeItem<String>> sources();

        TreeItem<String> initialSource();

        default List<TreeItem<String>> selectedSources() {
            return initialSource() == null ? List.of() : List.of(initialSource());
        }

        /** {xmin, ymin, xmax, ymax} of an object, or null. */
        double[] bounds(TreeItem<String> item);

        boolean isGerber(TreeItem<String> item);

        default boolean canBeContour(TreeItem<String> item) { return true; }

        String units(TreeItem<String> item);

        void preview(Request request, PreviewOptions options, java.util.function.Consumer<String> state);

        void panelize(Request request);

        default void fitPreview() { }
    }

    private PanelizeToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> source = new ComboBox<>();
        source.setId("panelize-source");
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
        source.setMinWidth(0);

        CheckBox together = new CheckBox("Panelizar conjunto (mesma grade para todas as camadas)");
        together.setId("panelize-together");
        together.setTooltip(tooltip("Marque Gerbers, Excellons e contornos alinhados na origem. Todos recebem os mesmos "
                + "deslocamentos; as caixas individuais nao mudam o passo entre copias."));
        var chosen = new java.util.LinkedHashMap<TreeItem<String>, BooleanProperty>();
        for (TreeItem<String> item : host.sources()) chosen.put(item, new SimpleBooleanProperty(
                host.selectedSources().contains(item) || item == source.getValue()));
        ListView<TreeItem<String>> objects = new ListView<>();
        ToolDescriptions.apply(objects,"Camadas do conjunto", "Marque as camadas alinhadas a panelizar juntas. Todas usam os mesmos passos da grade, referência e espaçamentos.\n\nAtenção: unidades divergentes são recusadas; a ferramenta não recentra camadas desalinhadas automaticamente.");
        objects.setId("panelize-sources"); objects.getItems().setAll(host.sources());
        objects.setCellFactory(CheckBoxListCell.forListView(chosen::get, names));
        objects.setPrefHeight(150); objects.setMaxWidth(Double.MAX_VALUE);
        objects.setMinWidth(0); objects.setMinHeight(90);
        objects.visibleProperty().bind(together.selectedProperty());
        objects.managedProperty().bind(objects.visibleProperty());
        source.disableProperty().bind(together.selectedProperty());

        ToggleGroup referenceGroup = new ToggleGroup();
        RadioButton ownBox = new RadioButton("Caixa do proprio objeto");
        RadioButton otherBox = new RadioButton("Caixa de outro objeto");
        ownBox.setId("panelize-own-box"); otherBox.setId("panelize-other-box");
        ownBox.textProperty().bind(javafx.beans.binding.Bindings.when(together.selectedProperty())
                .then("Caixa combinada dos objetos").otherwise("Caixa do proprio objeto"));
        ownBox.setToggleGroup(referenceGroup);
        otherBox.setToggleGroup(referenceGroup);
        ownBox.setSelected(true);
        ComboBox<TreeItem<String>> reference = new ComboBox<>();
        reference.setId("panelize-reference");
        reference.getItems().setAll(host.sources());
        reference.setConverter(names);
        reference.setMaxWidth(Double.MAX_VALUE);
        reference.setMinWidth(0);
        reference.getSelectionModel().selectFirst();
        reference.visibleProperty().bind(otherBox.selectedProperty());
        reference.managedProperty().bind(reference.visibleProperty());
        reference.setTooltip(tooltip("As copias ficam separadas pelo tamanho desta caixa (por exemplo o contorno da "
                + "placa) mais o espacamento, mesmo que o objeto seja menor."));

        // FX starts with 2 x 2 for a useful preview; other defaults match Python.
        TextField columns = new TextField(ToolDefaults.text("panelize.columns"));
        TextField rows = new TextField(ToolDefaults.text("panelize.rows"));
        TextField spacingColumns = new TextField(ToolDefaults.text("panelize.spacingcolumns"));
        TextField spacingRows = new TextField(ToolDefaults.text("panelize.spacingrows"));
        columns.setId("panelize-columns"); rows.setId("panelize-rows");
        spacingColumns.setId("panelize-spacing-x"); spacingRows.setId("panelize-spacing-y");
        for (TextField field : List.of(columns, rows, spacingColumns, spacingRows)) {
            field.setPrefColumnCount(5);
        }
        CheckBox constrain = new CheckBox("Limitar o tamanho do painel");
        constrain.setId("panelize-limit");
        constrain.setTooltip(tooltip("Se a grade nao couber, colunas e linhas diminuem ate caber, como no Python."));
        TextField limitWidth = new TextField(ToolDefaults.text("panelize.limitwidth"));
        TextField limitHeight = new TextField(ToolDefaults.text("panelize.limitheight"));
        limitWidth.setPrefColumnCount(5);
        limitHeight.setPrefColumnCount(5);
        HBox limits = new HBox(6, new Label("Largura:"), limitWidth, new Label("Altura:"), limitHeight);
        limits.visibleProperty().bind(constrain.selectedProperty());
        limits.managedProperty().bind(limits.visibleProperty());
        CheckBox gerberAsGeometry = new CheckBox("Gerber: criar o painel como Geometry");
        gerberAsGeometry.setId("panelize-as-geometry");
        gerberAsGeometry.setTooltip(tooltip("Sem marcar, o painel de um Gerber e outro Gerber (com as aberturas)."));

        Label summary = new Label();
        summary.setId("panelize-summary");
        summary.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.setId("panelize-error");
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        CheckBox showPreview = new CheckBox("Pre-visualizar no plot"); showPreview.setSelected(true);
        CheckBox showContent = new CheckBox("Mostrar o conteudo replicado (cobre, furos)"); showContent.setSelected(true);
        CheckBox showOutline = new CheckBox("Mostrar o contorno da placa replicada"); showOutline.setSelected(true);
        CheckBox fillInterior = new CheckBox("Preencher o interior da placa (translucido)"); fillInterior.setSelected(true);
        showPreview.setId("panelize-preview"); showContent.setId("panelize-preview-content");
        showOutline.setId("panelize-preview-outline"); fillInterior.setId("panelize-preview-fill");
        showContent.disableProperty().bind(showPreview.selectedProperty().not());
        showOutline.disableProperty().bind(showPreview.selectedProperty().not());
        fillInterior.disableProperty().bind(showPreview.selectedProperty().not().or(showOutline.selectedProperty().not()));
        showPreview.setTooltip(tooltip("Previa visual como no 2-Sided Tool, calculada em segundo plano. Nao cria objetos."));
        showContent.setTooltip(tooltip("Repete o cobre, furos, rasgos ou caminhos dos objetos escolhidos, mantendo seus recortes."));
        javafx.scene.layout.FlowPane legend = new javafx.scene.layout.FlowPane(10, 4,
                legendItem("Gerber", PlotAreaView.LayerCategory.GERBER),
                legendItem("Excellon", PlotAreaView.LayerCategory.EXCELLON),
                legendItem("Geometry", PlotAreaView.LayerCategory.GEOMETRY));
        legend.setId("panelize-preview-legend");
        legend.visibleProperty().bind(showPreview.selectedProperty().and(showContent.selectedProperty()));
        legend.managedProperty().bind(legend.visibleProperty());
        showOutline.setTooltip(tooltip("Desenha a borda e os cortes internos do contorno escolhido; sem contorno usa caixas de referencia."));
        fillInterior.setTooltip(tooltip("Preenche somente contornos fechados, preservando cortes internos. Nao fecha linhas abertas automaticamente."));
        ComboBox<TreeItem<String>> contour = new ComboBox<>();
        contour.setId("panelize-contour"); contour.getItems().add(null);
        contour.getItems().addAll(host.sources().stream().filter(host::canBeContour).toList());
        contour.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(TreeItem<String> item) { return item == null ? "Sem contorno (usar caixas)" : item.getValue(); }
            @Override public TreeItem<String> fromString(String text) { return null; }
        });
        contour.setMaxWidth(Double.MAX_VALUE);
        contour.setMinWidth(0);
        contour.disableProperty().bind(showPreview.selectedProperty().not().or(showOutline.selectedProperty().not()));
        contour.setTooltip(tooltip("Edge_Cuts ou Geometry de contorno, nas mesmas unidades. Esta escolha e so para previa; "
                + "para criar tambem seu painel, marque o contorno na lista do conjunto."));
        TreeItem<String> initialContour = host.sources().stream().filter(host::canBeContour).filter(item ->
                String.valueOf(item.getValue()).matches("(?i).*?(edge.?cuts|outline|profile|contorno|boundary).*"))
                .filter(item -> source.getValue() != null && java.util.Objects.equals(host.units(item), host.units(source.getValue())))
                .sorted(java.util.Comparator.comparingInt(item -> String.valueOf(item.getValue())
                        .matches("(?i).*edge.?cuts.*") ? 0 : 1))
                .findFirst().orElse(null);
        contour.setValue(initialContour);
        if (initialContour != null) {
            reference.setValue(initialContour);
            otherBox.setSelected(true); // one physical board pitch, even when copper/drill extents differ
        }
        Label previewState = new Label(); previewState.setId("panelize-preview-state"); previewState.setWrapText(true);
        Button fitPreview = new Button("Enquadrar previa"); fitPreview.setId("panelize-preview-fit");
        fitPreview.setMaxWidth(Double.MAX_VALUE); fitPreview.disableProperty().bind(showPreview.selectedProperty().not());
        fitPreview.setTooltip(tooltip("Enquadra todas as copias da ultima previa calculada, sem criar objetos."));
        fitPreview.setOnAction(event -> host.fitPreview());
        for (CheckBox check : List.of(together, gerberAsGeometry, constrain, showPreview, showContent, showOutline, fillInterior)) {
            check.setWrapText(true); check.setMaxWidth(Double.MAX_VALUE); check.setMinWidth(0);
        }

        java.util.function.Supplier<Request> request = () -> {
            List<TreeItem<String>> items = together.isSelected() ? chosen.entrySet().stream()
                    .filter(entry -> entry.getValue().get()).map(java.util.Map.Entry::getKey).toList()
                    : source.getValue() == null ? List.of() : List.of(source.getValue());
            if (items.isEmpty() || items.stream().anyMatch(item -> host.bounds(item) == null)) {
                throw new IllegalArgumentException("Escolha um objeto com geometria");
            }
            requireSameUnits(host, items, otherBox.isSelected() ? reference.getValue() : null);
            Envelope combined = new Envelope();
            for (TreeItem<String> item : items) {
                double[] bounds = host.bounds(item);
                combined.expandToInclude(new Envelope(bounds[0], bounds[2], bounds[1], bounds[3]));
            }
            double[] box = {combined.getMinX(), combined.getMinY(), combined.getMaxX(), combined.getMaxY()};
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
            return new Request(items, otherBox.isSelected() ? reference.getValue() : null, box, layout, gerberAsGeometry.isSelected());
        };
        Runnable refresh = () -> {
            errorLabel.setText("");
            try {
                Request current = request.get();
                Panelize.Layout layout = current.layout();
                double[] box = current.referenceBox();
                if (showPreview.isSelected()) {
                    if (showOutline.isSelected() && contour.getValue() != null)
                        requireSameUnits(host, current.sources(), contour.getValue());
                    host.preview(current, new PreviewOptions(showContent.isSelected(), showOutline.isSelected(),
                            fillInterior.isSelected(), contour.getValue()), previewState::setText);
                } else host.preview(null, null, previewState::setText);
                double[] size = layout.size(box[2] - box[0], box[3] - box[1], number(spacingColumns, ""),
                        number(spacingRows, ""));
                summary.setText(String.format(Locale.ROOT, "%d objeto(s); %d x %d = %d copias; painel %.2f x %.2f %s%s",
                        current.sources().size(),
                        layout.columns(), layout.rows(), layout.columns() * layout.rows(), size[0], size[1],
                        host.units(current.source()),
                        layout.constrained() ? " (grade reduzida pelo limite)" : ""));
            } catch (IllegalArgumentException incomplete) {
                host.preview(null, null, previewState::setText);
                summary.setText("");
                errorLabel.setText(incomplete.getMessage());
            }
        };
        List<ObservableValue<?>> triggers = List.of(source.valueProperty(), reference.valueProperty(),
                referenceGroup.selectedToggleProperty(), columns.textProperty(), rows.textProperty(),
                spacingColumns.textProperty(), spacingRows.textProperty(), constrain.selectedProperty(),
                limitWidth.textProperty(), limitHeight.textProperty(), together.selectedProperty(),
                showPreview.selectedProperty(), showContent.selectedProperty(), showOutline.selectedProperty(),
                fillInterior.selectedProperty(), contour.valueProperty());
        for (ObservableValue<?> trigger : triggers) {
            trigger.addListener((observable, previous, next) -> refresh.run());
        }
        chosen.values().forEach(property -> property.addListener((observable, previous, next) -> refresh.run()));
        gerberAsGeometry.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> together.isSelected() || source.getValue() != null && host.isGerber(source.getValue()),
                source.valueProperty(), together.selectedProperty()));
        gerberAsGeometry.managedProperty().bind(gerberAsGeometry.visibleProperty());

        Button panelize = new Button("Criar painel");
        panelize.setId("panelize-create");
        panelize.setMaxWidth(Double.MAX_VALUE);
        panelize.setOnAction(event -> {
            try {
                Request current = request.get();
                errorLabel.setText("");
                host.preview(null, null, previewState::setText);
                host.panelize(current);
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button("Fechar");
        close.setId("panelize-close");
        close.setOnAction(event -> {
            host.preview(null, null, previewState::setText);
            onClose.run();
        });

        VBox panel = new VBox(8,
                new Label("Objeto a repetir:"), source, together, objects,
                new Label("Distancia entre copias baseada em:"), new VBox(4, ownBox, otherBox), reference,
                new HBox(6, new Label("Colunas:"), columns, new Label("Linhas:"), rows),
                new HBox(6, new Label("Espacamento colunas:"), spacingColumns),
                new HBox(6, new Label("Espacamento linhas:"), spacingRows),
                constrain, limits, gerberAsGeometry, summary, new Separator(), showPreview, showContent,
                legend, showOutline, new Label("Contorno da placa (previa):"), contour, fillInterior, previewState, fitPreview,
                new Separator(), panelize, errorLabel, close);
        panel.setPadding(new Insets(6));
        refresh.run();
        return panel;
    }

    private static Label legendItem(String name, PlotAreaView.LayerCategory category) {
        javafx.scene.paint.Color ink = PlotAreaView.toolPreviewColors(category, true).fill();
        var swatch = new javafx.scene.shape.Rectangle(10, 10,
                javafx.scene.paint.Color.color(ink.getRed(), ink.getGreen(), ink.getBlue()));
        swatch.setArcWidth(3); swatch.setArcHeight(3);
        swatch.setStroke(javafx.scene.paint.Color.gray(.4));
        var label = new Label(name, swatch);
        label.setTooltip(tooltip("Cor da categoria na previa; nao altera as cores dos objetos do projeto."));
        return label;
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
            double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Valor invalido em " + name.toLowerCase(Locale.ROOT));
        }
    }

    static void requireSameUnits(Host host, List<TreeItem<String>> items, TreeItem<String> reference) {
        String units = host.units(items.getFirst());
        if (!("MM".equals(units) || "IN".equals(units))) throw new IllegalArgumentException("Unidades nao reconhecidas.");
        if (items.stream().anyMatch(item -> !units.equals(host.units(item)))
                || reference != null && !units.equals(host.units(reference)))
            throw new IllegalArgumentException("Objetos, referencia e contorno devem usar as mesmas unidades (MM ou IN). Converta antes.");
    }

    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(340);
        tooltip.setShowDuration(Duration.seconds(30));
        return tooltip;
    }
}
