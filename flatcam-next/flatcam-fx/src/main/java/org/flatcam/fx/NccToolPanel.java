package org.flatcam.fx;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.NccParameters;
import org.locationtech.jts.geom.Geometry;

/**
 * Multi-tool NCC form; the result is a multigeo Geometry object (one entry
 * per tool), matching Python's workflow. The tool list is a simple ordered
 * table of diameters (add/remove) - appTools/ToolNCC.py's own tools_table
 * also lets each row carry its own overlap/method/margin/etc. and offers a
 * Tools Database lookup, both deferred here (see NccParameters's class doc):
 * this port shares one set of clearing parameters across every tool.
 */
final class NccToolPanel {

    private static final String BOUNDARY_ITSELF = "Itself";
    private static final String BOUNDARY_REFERENCE = "Reference Object";

    /** One selectable entry for the "Reference Object" boundary combo - see MainWindow.generateNcc. */
    record ReferenceCandidate(String displayName, boolean isGerber, Geometry geometry) {
        @Override
        public String toString() {
            return displayName + (isGerber ? " (Gerber)" : " (Geometry)");
        }
    }

    record Result(NccParameters parameters, boolean checkValidity) {
    }

    private NccToolPanel() {
    }

    static Node build(String units, List<ReferenceCandidate> referenceCandidates,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equalsIgnoreCase(units);
        ObservableList<Double> diameters = FXCollections.observableArrayList(metric ? 0.5 : 0.020);

        TableView<Double> toolTable = new TableView<>(diameters);
        toolTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        toolTable.setPlaceholder(new Label("Nenhuma ferramenta"));
        TableColumn<Double, Double> diaColumn = new TableColumn<>("Diametro");
        diaColumn.setSortable(false);
        diaColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue()));
        diaColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format(item));
            }
        });
        toolTable.getColumns().add(diaColumn);
        toolTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Runnable updateTableHeight = () ->
                toolTable.setPrefHeight(Math.min(150, 32 + Math.max(diameters.size(), 1) * 28));
        diameters.addListener((javafx.collections.ListChangeListener<Double>) change -> updateTableHeight.run());
        updateTableHeight.run();

        TextField newDiaField = new TextField(metric ? "1.0" : "0.040");
        newDiaField.setPrefColumnCount(8);
        Tooltip.install(newDiaField, tooltip("Diametro da nova ferramenta a adicionar"));
        Button addToolButton = new Button("Adicionar");
        Button removeToolButton = new Button("Remover");
        removeToolButton.disableProperty().bind(Bindings.isEmpty(toolTable.getSelectionModel().getSelectedItems()));
        Label toolError = new Label();
        toolError.getStyleClass().add("form-error-label");
        toolError.setWrapText(true);

        Runnable addTool = () -> {
            try {
                double dia = parse(newDiaField, "Diametro da ferramenta");
                if (dia <= 0) {
                    throw new IllegalArgumentException("Diametro da ferramenta deve ser positivo");
                }
                for (double existing : diameters) {
                    if (Math.abs(existing - dia) < 1e-6) {
                        throw new IllegalArgumentException("Cancelado. Ferramenta ja esta na tabela.");
                    }
                }
                diameters.add(dia);
                diameters.sort(Comparator.naturalOrder());
                toolError.setText("");
            } catch (RuntimeException ex) {
                toolError.setText(ex.getMessage());
            }
        };
        addToolButton.setOnAction(e -> addTool.run());
        newDiaField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                addTool.run();
            }
        });
        removeToolButton.setOnAction(e -> {
            List<Double> selected = List.copyOf(toolTable.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                return;
            }
            if (diameters.size() - selected.size() < 1) {
                toolError.setText("E preciso manter ao menos uma ferramenta.");
                return;
            }
            diameters.removeAll(selected);
            toolError.setText("");
        });

        ComboBox<String> boundaryKindCombo = new ComboBox<>();
        boundaryKindCombo.getItems().add(BOUNDARY_ITSELF);
        if (!referenceCandidates.isEmpty()) {
            boundaryKindCombo.getItems().add(BOUNDARY_REFERENCE);
        }
        boundaryKindCombo.setValue(BOUNDARY_ITSELF);
        boundaryKindCombo.setTooltip(tooltip(
                "Itself: usa o contorno convexo do proprio Gerber como limite.\n"
                + "Reference Object: usa outro objeto (Gerber ou Geometry) ja carregado como limite."));
        ComboBox<ReferenceCandidate> referenceCombo = new ComboBox<>();
        referenceCombo.getItems().addAll(referenceCandidates);
        if (!referenceCandidates.isEmpty()) {
            referenceCombo.setValue(referenceCandidates.get(0));
        }
        javafx.beans.binding.BooleanBinding referenceChosen = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> BOUNDARY_REFERENCE.equals(boundaryKindCombo.getValue()), boundaryKindCombo.valueProperty());
        referenceCombo.visibleProperty().bind(referenceChosen);
        referenceCombo.managedProperty().bind(referenceChosen);

        GridPane boundaryGrid = new GridPane();
        boundaryGrid.setHgap(8);
        boundaryGrid.setVgap(8);
        boundaryGrid.addRow(0, new Label("Boundary:"), boundaryKindCombo);
        boundaryGrid.add(referenceCombo, 1, 1);

        CheckBox checkValidityCb = new CheckBox("Verificar validade dos diametros");
        checkValidityCb.setSelected(true);
        checkValidityCb.setTooltip(tooltip(
                "Se marcado, compara cada diametro com a menor distancia entre elementos de\n"
                + "cobre do Gerber e avisa se alguma ferramenta e grande demais para fazer um\n"
                + "isolamento completo. Apenas informativo - nao impede a geracao."));

        TextField overlapField = new TextField("40");
        TextField marginField = new TextField(metric ? "1.0" : "0.040");
        ComboBox<NccMethod> methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll(NccMethod.values());
        methodCombo.setValue(NccMethod.STANDARD);
        methodCombo.setTooltip(tooltip(
                "Standard: passes concentricas para dentro.\n"
                + "Seed: aneis crescentes a partir de um ponto central.\n"
                + "Lines: varredura paralela (raster).\n"
                + "Combo: tenta Lines, depois Seed, depois Standard."));
        CheckBox connectCb = new CheckBox("Connect");
        connectCb.setSelected(true);
        connectCb.setTooltip(tooltip("Une trajetos proximos quando o percurso de ligacao continua dentro da area segura da ferramenta."));
        CheckBox contourCb = new CheckBox("Contour");
        contourCb.setSelected(true);
        contourCb.setTooltip(tooltip("Adiciona um passe final contornando a borda interna da area limpa."));
        CheckBox offsetCb = new CheckBox("Copper offset");
        offsetCb.setTooltip(tooltip("Aumenta a distancia minima mantida em torno do cobre, alem da margem."));
        TextField offsetField = new TextField("0.0");
        offsetField.disableProperty().bind(offsetCb.selectedProperty().not());

        CheckBox restCb = new CheckBox("Rest Machining");
        restCb.setStyle("-fx-font-weight: bold;");
        restCb.setTooltip(tooltip(
                "Quando ativado, as ferramentas sao processadas da maior para a menor e cada uma\n"
                + "so limpa o que a anterior, maior, nao conseguiu alcancar. Forca a ordem Reverse\n"
                + "e desativa o controle de Order abaixo, assim como no FlatCAM Python."));
        ComboBox<NccOrder> orderCombo = new ComboBox<>();
        orderCombo.getItems().addAll(NccOrder.values());
        orderCombo.setValue(NccOrder.NONE);
        orderCombo.setTooltip(tooltip(
                "No: usa a ordem da tabela acima.\n"
                + "Forward: da menor para a maior ferramenta.\n"
                + "Reverse: da maior para a menor ferramenta."));
        // Rest Machining always forces largest-tool-first, same as Python
        // disabling its order radio the moment Rest Machining is checked.
        orderCombo.disableProperty().bind(restCb.selectedProperty());
        Label restHint = new Label("(ignorado - Rest Machining sempre usa a ordem Reverse)");
        restHint.setStyle("-fx-font-size: 10px; -fx-opacity: 0.75;");
        restHint.visibleProperty().bind(restCb.selectedProperty());
        restHint.managedProperty().bind(restHint.visibleProperty());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Overlap (%):"), overlapField);
        grid.addRow(1, new Label("Margin:"), marginField);
        grid.addRow(2, new Label("Method:"), methodCombo);
        grid.addRow(3, connectCb, contourCb);
        grid.addRow(4, offsetCb, offsetField);

        GridPane restGrid = new GridPane();
        restGrid.setHgap(8);
        restGrid.setVgap(8);
        restGrid.addRow(0, restCb, orderCombo);
        restGrid.add(restHint, 1, 1);

        Label note = new Label("O resultado sera criado em Geometry, uma entrada por ferramenta.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);
        Button generateButton = new Button("Gerar Geometry NCC");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                double overlap = parse(overlapField, "Overlap") / 100.0;
                double margin = parse(marginField, "Margin");
                double offset = offsetCb.isSelected() ? parse(offsetField, "Copper offset") : 0;
                NccBoundary boundary = new NccBoundary.Itself();
                if (BOUNDARY_REFERENCE.equals(boundaryKindCombo.getValue())) {
                    ReferenceCandidate candidate = referenceCombo.getValue();
                    if (candidate == null) {
                        throw new IllegalArgumentException("Selecione um objeto de referencia para o Boundary.");
                    }
                    boundary = candidate.isGerber()
                            ? new NccBoundary.ReferenceGerber(candidate.geometry())
                            : new NccBoundary.ReferenceGeometry(candidate.geometry());
                }
                NccParameters params = new NccParameters(List.copyOf(diameters), overlap, margin,
                        methodCombo.getValue(), connectCb.isSelected(), contourCb.isSelected(), offset,
                        restCb.isSelected(), orderCombo.getValue(), boundary);
                errorLabel.setText("");
                onGenerate.accept(new Result(params, checkValidityCb.isSelected()));
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setOnAction(e -> onClose.run());

        Region toolButtonsSpacer = new Region();
        HBox.setHgrow(toolButtonsSpacer, Priority.ALWAYS);
        HBox toolButtons = new HBox(8, newDiaField, addToolButton, toolButtonsSpacer, removeToolButton);
        toolButtons.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, new Label("NCC Tool (" + units + ")"),
                sectionTitle("FERRAMENTAS"), toolTable, toolButtons, toolError, checkValidityCb,
                sectionTitle("BOUNDARY"), boundaryGrid,
                sectionTitle("PARAMETROS DE LIMPEZA"), grid,
                sectionTitle("MULTI-FERRAMENTA"), restGrid,
                new Separator(), note, errorLabel, generateButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-section-title");
        return label;
    }

    /**
     * JavaFX's default Tooltip hides itself after a few seconds even while
     * the mouse stays put (showDuration defaults to 5s) - too short for the
     * longer, multi-line explanations used in this panel. Keep it open for
     * as long as the mouse actually hovers instead.
     */
    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDuration(Duration.INDEFINITE);
        return tooltip;
    }

    private static double parse(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": numero invalido");
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
