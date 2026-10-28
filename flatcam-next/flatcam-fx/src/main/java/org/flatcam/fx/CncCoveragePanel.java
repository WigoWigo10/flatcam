package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gcode.MachiningCoverage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;

/** Worker-produced residuals; selection only chooses an existing geometry for the Plot overlay. */
final class CncCoveragePanel extends VBox {
    private final TableView<MachiningCoverage.Step> table = new TableView<>();
    private final ComboBox<String> scope = new ComboBox<>();
    private final Label status = new Label(I18n.text("ui.3d2950f19c59"));
    private final Button back = new Button(I18n.text("ui.2f8a65d815e1"));
    private final Button cancel = new Button(I18n.text("ui.425e03fa44bf"));
    private final Button finalResidue = new Button(I18n.text("ui.25db5f857075"));
    private final Consumer<Geometry> highlight;
    private final String units;
    private MachiningCoverage.Result result;

    CncCoveragePanel(String source, String units, List<ToolGeometry> tools, Consumer<Geometry> highlight,
                     Runnable onBack, Runnable onCancel) {
        super(8); this.highlight = highlight; this.units = units; setPadding(new javafx.geometry.Insets(12));
        setId("cnc-coverage-panel"); setMinWidth(0);
        var title = new Label(I18n.text("ui.925522398d63") + source); title.setWrapText(true); title.getStyleClass().add("tool-title");
        status.setId("cnc-coverage-status"); status.setWrapText(true);
        table.setId("cnc-coverage-tools"); table.setMinWidth(0); table.setPrefHeight(Math.min(200, 32 + tools.size() * 28));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(28);
        column(I18n.text("ui.6438c29c6536"), step -> "T" + (step.toolIndex() + 1) + " · " + CncPreflight.number(tools.get(step.toolIndex()).toolDiameter()));
        column("Individual", step -> step.hasIndividualTarget() ? area(step.ownRemainingArea()) : I18n.text("ui.dd9c7b6b0456"));
        column(I18n.text("ui.ab91481c454e"), step -> area(step.cumulativeRemainingArea()));
        scope.getItems().setAll(I18n.text("ui.dbd5c95b7fd1"), I18n.text("ui.4abaae208218")); scope.getSelectionModel().selectFirst();
        scope.setId("cnc-coverage-scope"); scope.setMinWidth(0); scope.setMaxWidth(Double.MAX_VALUE);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> showSelection());
        scope.valueProperty().addListener((obs, old, value) -> showSelection());
        finalResidue.setId("cnc-coverage-final"); finalResidue.setMaxWidth(Double.MAX_VALUE); finalResidue.setDisable(true);
        finalResidue.setOnAction(event -> {
            table.getSelectionModel().clearSelection(); highlight.accept(result.finalRemaining());
            status.setText(I18n.text("ui.0e8fed4126a9") + area(result.finalRemainingArea()) + " " + squareUnit() + I18n.text("ui.2d89e6105789"));
        });
        back.setId("cnc-coverage-back"); back.setMaxWidth(Double.MAX_VALUE); back.setDisable(true); back.setOnAction(event -> onBack.run());
        cancel.setId("cnc-coverage-cancel"); cancel.setMaxWidth(Double.MAX_VALUE); cancel.setOnAction(event -> onCancel.run());
        cancel.visibleProperty().bind(cancel.disableProperty().not()); cancel.managedProperty().bind(cancel.visibleProperty());
        Label limits = new Label(I18n.text("ui.41fe10f9f4d3") + squareUnit() + I18n.text("ui.89cfed77b61f")); limits.setWrapText(true);
        getChildren().addAll(title, status, table, scope, finalResidue, limits, cancel, back);
        ToolDescriptions.apply(table, I18n.text("ui.7be186918be8"), I18n.text("ui.2962b8cb0b69"));
        ToolDescriptions.apply(scope, I18n.text("ui.54852c8b48db"), I18n.text("ui.2887981fe06e"));
        ToolDescriptions.apply(finalResidue, I18n.text("ui.ec3b4ced5fad"), I18n.text("ui.863fc62316ab"));
        ToolDescriptions.apply(back, I18n.text("ui.44b8f7c01949"), I18n.text("ui.ed9001cce9fa"));
        ToolDescriptions.apply(cancel, I18n.text("ui.145f6546a757"), I18n.text("ui.dfc86fea57b1"));
    }
    void complete(MachiningCoverage.Result value) {
        result = value; table.getItems().setAll(value.steps()); setIdle(); finalResidue.setDisable(false); table.getSelectionModel().selectLast();
    }
    void failed(String message) { status.setText(message); setIdle(); highlight.accept(null); }
    private void setIdle() { back.setDisable(false); cancel.setDisable(true); }
    private void showSelection() {
        var step = table.getSelectionModel().getSelectedItem(); if (step == null) return;
        Geometry shape = scope.getSelectionModel().getSelectedIndex() == 0 ? step.cumulativeRemaining() : step.ownRemaining();
        double residualArea = scope.getSelectionModel().getSelectedIndex() == 0 ? step.cumulativeRemainingArea() : step.ownRemainingArea();
        highlight.accept(shape);
        status.setText(shape == null ? I18n.text("ui.c8ba13b80ea1")
                : "T" + (step.toolIndex() + 1) + ": " + scope.getValue() + " — " + area(residualArea) + " " + squareUnit() + I18n.text("ui.e5aec31b67ce"));
    }
    private void column(String title, java.util.function.Function<MachiningCoverage.Step, String> value) {
        var column = new TableColumn<MachiningCoverage.Step, String>(title); column.setSortable(false); column.setMinWidth(60);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue()))); table.getColumns().add(column);
    }
    private String squareUnit() { return "MM".equals(units) ? "mm²" : "in²"; }
    private static String area(double value) { return value > 0 && value < .000001 ? String.format(Locale.ROOT, "%.3g", value) : CncPreflight.number(value); }
}
