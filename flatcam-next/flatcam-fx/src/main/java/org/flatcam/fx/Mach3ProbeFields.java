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
    private final CheckBox confirmation = new CheckBox("Li os cuidados de sondagem e vou validar o arquivo na maquina.");
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
        xy.setPromptText("None ou X;Y (tambem aceita X,Y)");
        confirmation.setId("probe-confirm");
        confirmation.setWrapText(true);
        Label warning = new Label("Sondagem Z Mach3: teste sensor, curso, origem e macro M6. "
                + "Confirme contato nas duas pausas; sem contato, ABORTE. Retire placa/clips antes do corte. "
                + "G92 permanece ativo; nao combine com G52. Sem previa no Plot Area. "
                + "Valores nas unidades do objeto; confira-os ao trocar a origem.");
        warning.setWrapText(true);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        if (showChangeZ) grid.addRow(row++, new Label("Tool change Z:"), changeZ);
        grid.addRow(row++, new Label("Probe Z final (< 0):"), depth);
        grid.addRow(row++, new Label("Probe feed:"), feed);
        grid.addRow(row++, new Label("Contact Z / placa:"), contactZ);
        grid.addRow(row, new Label("Troca X,Y (opcional):"), xy);
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
        if (!confirmation.isSelected()) throw new IllegalArgumentException("Confirme os cuidados de sondagem antes de gerar.");
        Double x = null, y = null;
        String value = xy.getText().trim();
        if (!value.isEmpty() && !value.equalsIgnoreCase("None")) {
            String[] coordinates = value.split(value.contains(";") ? ";" : ",", -1);
            if (coordinates.length != 2) throw new IllegalArgumentException("Troca X,Y: use None ou X;Y.");
            x = parse(coordinates[0], "Troca X");
            y = parse(coordinates[1], "Troca Y");
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
        catch (NumberFormatException error) { throw new IllegalArgumentException(name + ": numero invalido."); }
    }
}
