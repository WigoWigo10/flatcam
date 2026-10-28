package org.flatcam.fx;

import javafx.beans.binding.Bindings;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gcode.ProbeToolChangeParameters;

/** Shared, explicit opt-in to machine-dependent Mach3 probing. No machine control is performed here. */
final class Mach3ProbeFields {
    private final TextField changeZ;
    private final TextField depth;
    private final TextField feed;
    private final TextField contactZ;
    private final TextField xy;
    private final CheckBox confirmation = new CheckBox(I18n.text("ui.b65e16eb6ee7"));
    private final VBox view;

    Mach3ProbeFields(boolean metric, TextField changeZ, boolean showChangeZ,
                     ProbeToolChangeParameters defaults, ComboBox<GCodePreprocessor> profiles, CheckBox change) {
        this.changeZ = changeZ;
        changeZ.setId("probe-change-z");
        if (defaults != null) changeZ.setText(Double.toString(defaults.toolChangeZ()));
        depth = field("probe-depth", defaults == null ? (metric ? "-5" : "-0.2") : Double.toString(defaults.probeDepth()));
        feed = field("probe-feed", defaults == null ? (metric ? "50" : "2") : Double.toString(defaults.feedRate()));
        contactZ = field("probe-contact-z", defaults == null ? "0" : Double.toString(defaults.contactZ()));
        xy = field("probe-xy", defaults == null || defaults.toolChangeX() == null ? "None"
                : defaults.toolChangeX() + ";" + defaults.toolChangeY());
        xy.setPromptText(I18n.text("ui.0ef93d2325f6"));
        confirmation.setId("probe-confirm");
        confirmation.setWrapText(true);
        ToolDescriptions.apply(confirmation,I18n.text("ui.00803175981a"), I18n.text("ui.76f7c9d80028"));
        Label warning = new Label(I18n.text("ui.8c1f4a09ed13"));
        warning.setWrapText(true);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        if (showChangeZ) grid.addRow(row++, new Label("Tool change Z:"), changeZ);
        grid.addRow(row++, new Label("Probe Z final (< 0):"), depth);
        grid.addRow(row++, new Label("Probe feed:"), feed);
        grid.addRow(row++, new Label(I18n.text("ui.e619d3b08a75")), contactZ);
        grid.addRow(row, new Label(I18n.text("ui.b76f82481874")), xy);
        view = new VBox(8, warning, grid, confirmation);
        view.setId("probe-settings");
        var probing = Bindings.createBooleanBinding(() -> profiles.getValue().requiresProbe(), profiles.valueProperty());
        view.visibleProperty().bind(probing);
        view.managedProperty().bind(probing);
        boolean[] previous = {change.isSelected()};
        profiles.valueProperty().addListener((obs, oldValue, value) -> {
            if (value.requiresProbe()) {
                previous[0] = change.isSelected();
                change.setSelected(true);
                confirmation.setSelected(false);
            } else if (oldValue.requiresProbe()) change.setSelected(previous[0]);
        });
        // Source changes/reset actions must not turn off the mandatory probing cycle.
        change.selectedProperty().addListener((obs, oldValue, value) -> {
            if (probing.get() && !value) change.setSelected(true);
        });
    }

    VBox view() { return view; }

    void resetConfirmation() { confirmation.setSelected(false); }

    void reset(boolean metric) {
        resetConfirmation();
        depth.setText(metric ? "-5" : "-0.2");
        feed.setText(metric ? "50" : "2");
        contactZ.setText("0");
        xy.setText("None");
    }

    void restore(ProbeToolChangeParameters parameters) {
        resetConfirmation();
        changeZ.setText(Double.toString(parameters.toolChangeZ()));
        depth.setText(Double.toString(parameters.probeDepth()));
        feed.setText(Double.toString(parameters.feedRate()));
        contactZ.setText(Double.toString(parameters.contactZ()));
        xy.setText(parameters.toolChangeX() == null ? "None" : parameters.toolChangeX() + ";" + parameters.toolChangeY());
    }

    ProbeToolChangeParameters parameters() {
        if (!confirmation.isSelected()) throw new IllegalArgumentException(I18n.text("ui.56df73d505f4"));
        Double x = null, y = null;
        String value = xy.getText().trim();
        if (!value.isEmpty() && !value.equalsIgnoreCase("None")) {
            String[] coordinates = value.split(value.contains(";") ? ";" : ",", -1);
            if (coordinates.length != 2) throw new IllegalArgumentException(I18n.text("ui.0a96cb1d4e8a"));
            x = parse(coordinates[0], I18n.text("ui.adefae085dd5"));
            y = parse(coordinates[1], I18n.text("ui.1dabad12a3c2"));
        }
        return new ProbeToolChangeParameters(parse(changeZ.getText(), "Tool change Z"),
                parse(depth.getText(), "Probe Z final"), parse(feed.getText(), "Probe feed"),
                parse(contactZ.getText(), "Contact Z"), x, y);
    }

    private static TextField field(String id, String value) {
        TextField field = new TextField(value);
        field.setId(id);
        field.setMinWidth(0);
        field.setPrefColumnCount(7);
        return field;
    }

    private static double parse(String value, String name) {
        try { return Double.parseDouble(value.trim().replace(',', '.')); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(name + I18n.text("ui.d33f783dcf1b")); }
    }
}
