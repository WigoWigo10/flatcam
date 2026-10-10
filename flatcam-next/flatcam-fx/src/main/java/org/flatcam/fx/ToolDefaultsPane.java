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
        // What the user typed and has not saved yet, by setting and units ("key@mm", "key@in" or "key").
        Map<String, String> pending = new LinkedHashMap<>();
        ToggleGroup units = new ToggleGroup();
        RadioButton millimetres = new RadioButton("mm");
        RadioButton inches = new RadioButton("in");
        millimetres.setToggleGroup(units);
        inches.setToggleGroup(units);
        millimetres.setSelected(true);

        Accordion groups = new Accordion();
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setId("tool-defaults-feedback");

        Runnable rebuild = () -> {
            String expanded = groups.getExpandedPane() == null ? null : groups.getExpandedPane().getText();
            groups.getPanes().clear();
            boolean metric = millimetres.isSelected();
            for (Map.Entry<String, List<ToolDefaults.Setting>> tool : ToolDefaults.byTool().entrySet()) {
                GridPane grid = new GridPane();
                grid.setHgap(10);
                grid.setVgap(6);
                grid.setPadding(new Insets(8));
                int row = 0;
                for (ToolDefaults.Setting setting : tool.getValue()) {
                    String slot = setting.perUnit() ? setting.key() + (metric ? "@mm" : "@in") : setting.key();
                    String value = pending.getOrDefault(slot, ToolDefaults.text(setting.key(), metric));
                    Node editor = editor(setting, value, text -> pending.put(slot, text));
                    editor.setId("tool-default-" + setting.key().replace('.', '-'));
                    Label label = new Label(setting.label() + (setting.perUnit() ? " (" + (metric ? "mm" : "in") + ")" : "") + ":");
                    grid.addRow(row++, label, editor);
                }
                TitledPane pane = new TitledPane(tool.getKey(), grid);
                groups.getPanes().add(pane);
                if (tool.getKey().equals(expanded)) {
                    groups.setExpandedPane(pane);
                }
            }
        };
        units.selectedToggleProperty().addListener((observable, before, now) -> rebuild.run());
        rebuild.run();

        Button save = new Button("Salvar padrões das ferramentas");
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
                    ? (saved == 0 ? "Nada a salvar." : "Padrões salvos. Valem para os painéis abertos a partir de agora.")
                    : "Não salvo: " + String.join("; ", problems));
            rebuild.run();
        });
        Button reset = new Button("Restaurar padrões de fábrica");
        reset.setId("tool-defaults-reset");
        // Two steps, in the page itself: restoring discards every saved value.
        Button confirmReset = new Button("Confirmar: restaurar tudo");
        confirmReset.setId("tool-defaults-reset-confirm");
        confirmReset.setVisible(false);
        confirmReset.setManaged(false);
        reset.setOnAction(event -> {
            confirmReset.setVisible(true);
            confirmReset.setManaged(true);
        });
        confirmReset.setOnAction(event -> {
            ToolDefaults.resetAll();
            pending.clear();
            confirmReset.setVisible(false);
            confirmReset.setManaged(false);
            feedback.setText("Padrões de fábrica restaurados.");
            rebuild.run();
        });

        VBox box = new VBox(8, new HBox(10, new Label("Unidades dos comprimentos:"), millimetres, inches), groups,
                new HBox(8, save, reset, confirmReset), feedback);
        return box;
    }

    private static Node editor(ToolDefaults.Setting setting, String value, java.util.function.Consumer<String> onEdit) {
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
