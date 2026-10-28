package org.flatcam.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The Preferences section that edits {@link ToolDefaults}: one group per tool, with the lengths shown for the chosen
 * units. Nothing is saved until "Salvar"; a panel that is already open keeps the values it opened with.
 */
final class ToolDefaultsPane {

    private ToolDefaultsPane() {
    }

    static Node build() {
        return build(null, null);
    }

    /** A cached preferences page: filtering never discards drafts; reset affects only these groups. */
    static Node build(java.util.Collection<String> selectedGroups, javafx.beans.value.ObservableStringValue filter) {
        boolean scoped = selectedGroups != null;
        java.util.Set<String> included = scoped ? java.util.Set.copyOf(selectedGroups) : ToolDefaults.byTool().keySet();
        // What the user typed and has not saved yet, by setting and units ("key@mm", "key@in" or "key").
        Map<String, String> pending = new LinkedHashMap<>();
        ToggleGroup units = new ToggleGroup();
        RadioButton millimetres = new RadioButton("mm");
        RadioButton inches = new RadioButton("in");
        millimetres.setToggleGroup(units);
        inches.setToggleGroup(units);
        millimetres.setSelected(true);
        ToolDescriptions.apply(millimetres, I18n.text("ui.a180682c8546"), I18n.text("ui.6c8a6a962323"));
        ToolDescriptions.apply(inches, I18n.text("ui.fe0af9665c07"), I18n.text("ui.d89a98cda79d"));

        Accordion groups = new Accordion();
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setId("tool-defaults-feedback");

        Runnable rebuild = () -> {
            String expanded = groups.getExpandedPane() == null ? null : groups.getExpandedPane().getText();
            groups.setExpandedPane(null);
            groups.getPanes().clear();
            boolean metric = millimetres.isSelected();
            for (Map.Entry<String, List<ToolDefaults.Setting>> tool : ToolDefaults.byTool().entrySet()) {
                if (!included.contains(tool.getKey())) continue;
                GridPane grid = new GridPane();
                grid.setHgap(10);
                grid.setVgap(6);
                grid.setPadding(new Insets(8));
                var labels = new javafx.scene.layout.ColumnConstraints();
                labels.setPercentWidth(60);
                var editors = new javafx.scene.layout.ColumnConstraints();
                editors.setPercentWidth(40);
                grid.getColumnConstraints().setAll(labels, editors);
                int row = 0;
                for (ToolDefaults.Setting setting : tool.getValue()) {
                    String help = ToolDefaultsDescriptions.text(setting, metric);
                    if (filter != null && !PreferencesSearch.matches(filter.get(), setting.tool(), setting.label(), setting.key(), help)) continue;
                    String slot = setting.perUnit() ? setting.key() + (metric ? "@mm" : "@in") : setting.key();
                    String value = pending.getOrDefault(slot, ToolDefaults.text(setting.key(), metric));
                    Node editor = editor(setting, value, text -> pending.put(slot, text));
                    editor.setId("tool-default-" + setting.key().replace('.', '-'));
                    Label label = new Label(setting.label() + (setting.perUnit() ? " (" + (metric ? "mm" : "in") + ")" : "") + ":");
                    label.setWrapText(true);
                    label.setMinWidth(0);
                    if (editor instanceof javafx.scene.layout.Region region) { region.setMinWidth(0); region.setMaxWidth(Double.MAX_VALUE); }
                    ToolDescriptions.apply(editor, setting.tool() + " — " + setting.label(), help);
                    if (setting.kind() == ToolDefaults.Kind.COLOR && editor instanceof VBox colorEditor) {
                        colorEditor.getChildren().forEach(child -> ToolDescriptions.apply(child, setting.label(), help));
                        label.setLabelFor(colorEditor.getChildren().getFirst());
                    }
                    ToolDescriptions.apply(label, setting.label(), help);
                    if (editor instanceof javafx.scene.control.Control control) label.setLabelFor(control);
                    grid.addRow(row++, label, editor);
                }
                if (row == 0) continue;
                TitledPane pane = new TitledPane(I18n.caption(tool.getKey()), grid);
                groups.getPanes().add(pane);
                if (pane.getText().equals(expanded) || scoped && groups.getExpandedPane() == null) {
                    groups.setExpandedPane(pane);
                }
            }
        };
        units.selectedToggleProperty().addListener((observable, before, now) -> rebuild.run());
        if (filter != null) filter.addListener((observable, before, now) -> rebuild.run());
        rebuild.run();

        Button save = new Button(scoped ? I18n.text("ui.8439d7e4000d") : I18n.text("ui.746268041a60"));
        if (scoped) save.getStyleClass().add("primary-action");
        save.setId("tool-defaults-save");
        save.setOnAction(event -> {
            List<String> problems = new ArrayList<>();
            int saved = 0;
            for (Map.Entry<String, String> entry : List.copyOf(pending.entrySet())) {
                String slot = entry.getKey();
                boolean metric = !slot.endsWith("@in");
                String key = slot.contains("@") ? slot.substring(0, slot.indexOf('@')) : slot;
                try {
                    ToolDefaults.set(key, metric, entry.getValue());
                    pending.remove(slot);
                    saved++;
                } catch (IllegalArgumentException invalid) {
                    problems.add(invalid.getMessage());
                }
            }
            feedback.setText(problems.isEmpty()
                    ? (saved == 0 ? I18n.text("ui.a4186f2d5e63") : I18n.text("ui.6558e77bc05b"))
                    : I18n.text("ui.f761c2605081") + String.join("; ", problems));
            rebuild.run();
        });
        Button reset = new Button(I18n.text("ui.8aff60723184"));
        reset.setId("tool-defaults-reset");
        // Two steps, in the page itself: restoring discards every saved value.
        Button confirmReset = new Button(scoped ? I18n.text("ui.df4455d91e1f") : I18n.text("ui.70b852ef71ec"));
        confirmReset.setId("tool-defaults-reset-confirm");
        confirmReset.setVisible(false);
        confirmReset.setManaged(false);
        ToolDescriptions.apply(save, I18n.text("ui.b3cc322a32df"), I18n.text("ui.7dca2aea650d"));
        ToolDescriptions.apply(reset, I18n.text("ui.3ab8ef119fc7"), I18n.text("ui.9c0d438ffb0c"));
        ToolDescriptions.apply(confirmReset, I18n.text("ui.e0350c3f94ac"), scoped
                ? I18n.text("ui.fe231b822e61")
                : I18n.text("ui.9b651e369510"));
        reset.setOnAction(event -> {
            confirmReset.setVisible(true);
            confirmReset.setManaged(true);
        });
        confirmReset.setOnAction(event -> {
            if (!scoped) ToolDefaults.resetAll();
            else for (var setting : ToolDefaults.settings()) if (included.contains(setting.tool())) {
                ToolDefaults.set(setting.key(), true, setting.factory(true));
                if (setting.perUnit()) ToolDefaults.set(setting.key(), false, setting.factory(false));
            }
            pending.clear();
            confirmReset.setVisible(false);
            confirmReset.setManaged(false);
            feedback.setText(I18n.text("ui.dbd7b0baeb20"));
            rebuild.run();
        });

        if (scoped) {
            ToolDescriptions.apply(save, I18n.text("ui.8439d7e4000d"), I18n.text("ui.0694e17603fd"));
            ToolDescriptions.apply(reset, I18n.text("ui.dbe2f683628c"), I18n.text("ui.5d1dd9743528"));
        }
        var actions = new javafx.scene.layout.FlowPane(8, 8, save, reset, confirmReset);
        HBox unitControls = new HBox(10, new Label(I18n.text("ui.757dc860a9c1")), millimetres, inches);
        unitControls.setId("tool-defaults-units");
        boolean hasLengths = ToolDefaults.settings().stream().anyMatch(s -> included.contains(s.tool()) && s.perUnit());
        unitControls.setVisible(hasLengths); unitControls.setManaged(hasLengths);
        VBox box = new VBox(8, unitControls, groups,
                actions, feedback);
        return box;
    }

    private static Node editor(ToolDefaults.Setting setting, String value, java.util.function.Consumer<String> onEdit) {
        if (setting.kind() == ToolDefaults.Kind.COLOR) {
            javafx.scene.paint.Color initial = value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")
                    ? javafx.scene.paint.Color.web(value) : ToolDefaults.color(setting.key());
            var picker = new javafx.scene.control.ColorPicker(initial);
            var hex = new TextField(value);
            String id = "tool-default-" + setting.key().replace('.', '-');
            picker.setId(id + "-picker"); hex.setId(id + "-hex");
            picker.setMinWidth(0); picker.setMaxWidth(Double.MAX_VALUE);
            hex.setMinWidth(0); hex.setPrefColumnCount(9);
            hex.textProperty().addListener((o, before, now) -> {
                onEdit.accept(now);
                if (now.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) picker.setValue(javafx.scene.paint.Color.web(now));
            });
            picker.setOnAction(e -> {
                var color = picker.getValue();
                hex.setText(String.format(java.util.Locale.ROOT, "#%02X%02X%02X%02X", Math.round(color.getRed()*255),
                        Math.round(color.getGreen()*255), Math.round(color.getBlue()*255), Math.round(color.getOpacity()*255)));
            });
            return new VBox(4, picker, hex);
        }
        if (setting.kind() == ToolDefaults.Kind.FLAG) {
            CheckBox box = new CheckBox();
            box.setSelected(Boolean.parseBoolean(value));
            box.selectedProperty().addListener((observable, before, now) -> onEdit.accept(Boolean.toString(now)));
            return box;
        }
        if (setting.kind() == ToolDefaults.Kind.CHOICE && !setting.choices().isEmpty()) {
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().setAll(setting.choices());
            combo.setValue(value);
            combo.valueProperty().addListener((observable, before, now) -> onEdit.accept(now));
            return combo;
        }
        TextField field = new TextField(value);
        field.setPrefColumnCount(8);
        field.textProperty().addListener((observable, before, now) -> onEdit.accept(now));
        return field;
    }
}
