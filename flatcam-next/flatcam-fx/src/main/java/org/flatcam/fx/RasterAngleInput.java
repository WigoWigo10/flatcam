package org.flatcam.fx;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.flatcam.cam.ncc.NccMethod;

/** Shared NCC/Paint angle field. Automatic preserves the legacy bounding-box choice. */
final class RasterAngleInput extends VBox {
    final CheckBox automatic = new CheckBox(I18n.text("cam.lines.auto"));
    final TextField degrees = new TextField("0");
    private final Canvas sample = new Canvas(48, 34);
    record Draft(boolean automatic, String degrees) { }

    RasterAngleInput(String prefix, ObservableValue<NccMethod> method) {
        super(5);
        setId(prefix + "-angle");
        automatic.setId(prefix + "-angle-auto");
        degrees.setId(prefix + "-angle-degrees");
        automatic.setSelected(true);
        degrees.setPrefColumnCount(5);
        degrees.setMinWidth(0);
        degrees.setMaxWidth(90);
        disableProperty().bind(Bindings.createBooleanBinding(
                () -> method.getValue() == null || !method.getValue().usesRaster(), method));
        degrees.disableProperty().bind(automatic.selectedProperty());
        var row = new HBox(6, degrees, new Label("°"), sample);
        row.setAlignment(Pos.CENTER_LEFT);
        getChildren().setAll(new Label(I18n.text("cam.lines.angle")), new FlowPane(8, 5, automatic, row));
        for (var node : java.util.List.of(this, automatic, degrees))
            ToolDescriptions.apply(node, I18n.text("cam.lines.angle"), I18n.text("cam.lines.angle.help"));
        automatic.selectedProperty().addListener((o, a, b) -> draw());
        degrees.textProperty().addListener((o, a, b) -> draw());
        // A sample is descriptive only. It never previews or changes a project geometry.
        sample.setAccessibleText(I18n.text("cam.lines.angle"));
        draw();
    }
    void draft(Draft value) {
        automatic.setSelected(value.automatic());
        degrees.setText(value.degrees());
    }

    static Double value(boolean auto, String text) {
        if (auto) return null;
        double value = CamNumericInput.number(text, I18n.text("cam.lines.angle"));
        if (value < 0 || value >= 180) throw new IllegalArgumentException(I18n.text("cam.lines.angle.invalid"));
        return value;
    }
    private void draw() {
        var g = sample.getGraphicsContext2D();
        g.clearRect(0, 0, 48, 34);
        // A light swatch with dark strokes remains legible in every application theme.
        g.setFill(Color.web("#D8E7EF"));
        g.fillRoundRect(0, 0, 48, 34, 5, 5);
        double angle;
        try { angle = automatic.isSelected() ? 0 : value(false, degrees.getText()); }
        catch (IllegalArgumentException invalid) {
            g.setFill(Color.web("#B42318"));
            g.fillText("!", 22, 22);
            return;
        }
        g.save();
        g.beginPath();
        g.rect(2, 2, 44, 30);
        g.clip();
        g.translate(24, 17);
        g.rotate(-angle);
        g.setStroke(Color.web("#215875"));
        g.setLineWidth(1.5);
        if (automatic.isSelected()) g.setLineDashes(3, 3);
        for (int y = -24; y <= 24; y += 8) g.strokeLine(-36, y, 36, y);
        g.restore();
    }
}
