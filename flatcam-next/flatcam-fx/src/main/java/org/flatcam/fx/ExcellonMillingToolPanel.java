package org.flatcam.fx;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.flatcam.app.project.LegacyToolsDatabase;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonMillingGenerator.Kind;

/** Excellon Milling Tool: separate editable Geometry for hole and slot contours. */
final class ExcellonMillingToolPanel {
    record SourceCandidate(TreeItem<String> item, ExcellonImage image) {
        @Override public String toString() { return item.getValue(); }
    }

    record Result(SourceCandidate source, Set<Integer> toolIds, double millDiameter, Kind kind,
                  LegacyToolsDatabase.MillingTool databaseTool) { }

    private record ToolRow(int id, double diameter, int drills, int slots) { }

    private ExcellonMillingToolPanel() { }

    static Node build(List<SourceCandidate> sources, SourceCandidate initial,
                      Consumer<Result> onGenerate, Runnable onClose) {
        return build(sources, initial, List::of, onGenerate, onClose);
    }

    static Node build(List<SourceCandidate> sources, SourceCandidate initial,
                      Supplier<List<LegacyToolsDatabase.MillingTool>> database,
                      Consumer<Result> onGenerate, Runnable onClose) {
        ComboBox<SourceCandidate> source = new ComboBox<>(FXCollections.observableArrayList(sources));
        source.setValue(initial);
        source.setMaxWidth(Double.MAX_VALUE);
        TableView<ToolRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getColumns().add(column("#", row -> Integer.toString(row.id)));
        table.getColumns().add(column("Diameter", row -> String.format(Locale.ROOT, "%.4f", row.diameter)));
        table.getColumns().add(column("Drills", row -> Integer.toString(row.drills)));
        table.getColumns().add(column("Slots", row -> Integer.toString(row.slots)));
        Runnable reload = () -> {
            ExcellonImage image = source.getValue().image();
            table.getItems().setAll(image.toolDiameters().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> new ToolRow(entry.getKey(), entry.getValue(),
                            image.drillCounts().getOrDefault(entry.getKey(), 0),
                            image.slotCounts().getOrDefault(entry.getKey(), 0)))
                    .toList());
            table.setPrefHeight(Math.min(220, 34 + table.getItems().size() * 27));
            table.getSelectionModel().selectAll();
        };
        reload.run();
        source.valueProperty().addListener((obs, oldValue, value) -> {
            if (value != null) reload.run();
        });

        TextField diameter = new TextField(ToolDefaults.text("milling.tooldia", "MM".equalsIgnoreCase(initial.image().units())));
        Label error = new Label();
        error.getStyleClass().add("form-error-label");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        var imported = new java.util.concurrent.atomic.AtomicReference<LegacyToolsDatabase.MillingTool>();
        diameter.textProperty().addListener((obs, old, value) -> imported.set(null));
        VBox picker = DatabaseToolPicker.build("mill-db", database, selected -> {
            diameter.setText(Double.toString(selected.diameter())); imported.set(selected);
        }, error);
        Consumer<Result> transfer = result -> onGenerate.accept(new Result(result.source(), result.toolIds(),
                result.millDiameter(), result.kind(), imported.get()));
        Button drills = new Button("Generate Geometry for Drills");
        drills.getStyleClass().add("primary-action");
        Button slots = new Button("Generate Geometry for Slots");
        for (Button button : List.of(drills, slots)) button.setMaxWidth(Double.MAX_VALUE);
        drills.setOnAction(event -> submit(source, table, diameter, Kind.DRILLS, error, transfer));
        slots.setOnAction(event -> submit(source, table, diameter, Kind.SLOTS, error, transfer));
        Button close = new Button("Close");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(9, new Label("Milling Tool"), new Label("EXCELLON:"), source,
                new Label("Tools Table (selecione as ferramentas)"), table,
                new Label("Tool diameter (" + initial.image().units() + "):"), diameter, picker,
                new Label("Gera caminhos de centro para uma Geometry editavel. Depois crie o CNC Job pela Geometry."),
                error, drills, slots, close);
        panel.setPadding(new Insets(12));
        panel.setFillWidth(true);
        return panel;
    }

    private static void submit(ComboBox<SourceCandidate> source, TableView<ToolRow> table,
                               TextField diameter, Kind kind, Label error, Consumer<Result> callback) {
        try {
            if (source.getValue() == null) throw new IllegalArgumentException("Selecione um Excellon.");
            Set<Integer> selected = new LinkedHashSet<>();
            for (ToolRow row : table.getSelectionModel().getSelectedItems()) selected.add(row.id);
            if (selected.isEmpty()) throw new IllegalArgumentException("Selecione ao menos uma ferramenta.");
            double value = Double.parseDouble(diameter.getText().trim().replace(',', '.'));
            if (!Double.isFinite(value) || value <= 0)
                throw new IllegalArgumentException("O diametro da fresa deve ser positivo.");
            boolean any = kind == Kind.DRILLS
                    ? source.getValue().image().drills().stream().anyMatch(hit -> selected.contains(hit.toolId()))
                    : source.getValue().image().slots().stream().anyMatch(hit -> selected.contains(hit.toolId()));
            if (!any) throw new IllegalArgumentException("As ferramentas selecionadas nao possuem "
                    + (kind == Kind.DRILLS ? "furos" : "slots") + ".");
            for (int id : selected) {
                if (value > source.getValue().image().toolDiameters().get(id) + 1e-9)
                    throw new IllegalArgumentException("A fresa excede o diametro de T" + id + ".");
            }
            error.setText("");
            callback.accept(new Result(source.getValue(), Set.copyOf(selected), value, kind, null));
        } catch (NumberFormatException exception) {
            error.setText("Informe um diametro numerico valido.");
        } catch (IllegalArgumentException exception) {
            error.setText(exception.getMessage());
        }
    }

    private static TableColumn<ToolRow, String> column(String title,
                                                        java.util.function.Function<ToolRow, String> value) {
        TableColumn<ToolRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }
}
