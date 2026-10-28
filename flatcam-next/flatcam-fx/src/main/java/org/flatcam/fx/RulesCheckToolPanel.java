package org.flatcam.fx;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.flatcam.cam.analysis.RulesCheck;
import org.flatcam.cam.analysis.RulesCheck.Rule;
import org.flatcam.cam.analysis.RulesCheck.RuleResult;
import org.flatcam.cam.analysis.RulesCheck.Setting;
import org.locationtech.jts.geom.Coordinate;

/**
 * appTools/ToolRulesCheck.py: checks a board against design rules (trace size, copper/silk/mask clearances, annular
 * ring, hole distances and sizes). Each failed rule lists its violation points; selecting one jumps to it.
 */
final class RulesCheckToolPanel {

    /** The objects chosen for each role of the board; any may be null. */
    record Selection(TreeItem<String> copperTop, TreeItem<String> copperBottom, TreeItem<String> silkTop,
                     TreeItem<String> silkBottom, TreeItem<String> maskTop, TreeItem<String> maskBottom,
                     TreeItem<String> outline, TreeItem<String> drills1, TreeItem<String> drills2) {
    }

    interface Host {
        List<TreeItem<String>> gerbers();

        List<TreeItem<String>> excellons();

        /** Runs the checks as a job; calls one of the two callbacks on the JavaFX thread when it ends. */
        void check(Selection selection, Map<Rule, Setting> settings, Consumer<List<RuleResult>> onResult,
                   Consumer<String> onError);

        /** Centres the plot on the point and marks every violation; {@code null} clears the marks. */
        void locate(List<Coordinate> all, Coordinate focus);
    }

    private RulesCheckToolPanel() {
    }

    private static ComboBox<TreeItem<String>> chooser(List<TreeItem<String>> items) {
        ComboBox<TreeItem<String>> box = new ComboBox<>();
        box.getItems().add(null);
        box.getItems().addAll(items);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(TreeItem<String> item) {
                return item == null ? I18n.text("ui.4aca7f2ac230") : item.getValue();
            }

            @Override
            public TreeItem<String> fromString(String text) {
                return null;
            }
        });
        box.getSelectionModel().selectFirst();
        return box;
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> copperTop = chooser(host.gerbers());
        ComboBox<TreeItem<String>> copperBottom = chooser(host.gerbers());
        ComboBox<TreeItem<String>> silkTop = chooser(host.gerbers());
        ComboBox<TreeItem<String>> silkBottom = chooser(host.gerbers());
        ComboBox<TreeItem<String>> maskTop = chooser(host.gerbers());
        ComboBox<TreeItem<String>> maskBottom = chooser(host.gerbers());
        ComboBox<TreeItem<String>> outline = chooser(host.gerbers());
        ComboBox<TreeItem<String>> drills1 = chooser(host.excellons());
        ComboBox<TreeItem<String>> drills2 = chooser(host.excellons());

        GridPane objects = new GridPane();
        objects.setHgap(6);
        objects.setVgap(4);
        String[] labels = {I18n.text("ui.1189333a92a2"), I18n.text("ui.99112569de56"), I18n.text("ui.d88ec99de29a"), I18n.text("ui.b6f31b6b189c"), I18n.text("ui.50f56d318357"), I18n.text("ui.d9391dc057b5"),
                I18n.text("ui.8185e1c34543"), "Excellon 1", "Excellon 2"};
        List<ComboBox<TreeItem<String>>> boxes = List.of(copperTop, copperBottom, silkTop, silkBottom, maskTop,
                maskBottom, outline, drills1, drills2);
        for (int i = 0; i < labels.length; i++) {
            objects.add(new Label(labels[i] + ":"), 0, i);
            objects.add(boxes.get(i), 1, i);
            javafx.scene.layout.GridPane.setHgrow(boxes.get(i), javafx.scene.layout.Priority.ALWAYS);
        }

        Map<Rule, Setting> defaults = RulesCheck.defaults();
        Map<Rule, CheckBox> switches = new EnumMap<>(Rule.class);
        Map<Rule, TextField> values = new EnumMap<>(Rule.class);
        GridPane rules = new GridPane();
        rules.setHgap(6);
        rules.setVgap(4);
        int row = 0;
        for (Rule rule : Rule.values()) {
            CheckBox enabled = new CheckBox(I18n.caption(rule.title()));
            String key = "rules." + rule.name().toLowerCase(java.util.Locale.ROOT);
            enabled.setSelected(ToolDefaults.flag(key + ".enabled"));
            TextField value = new TextField(ToolDefaults.text(key));
            value.setPrefColumnCount(5);
            value.disableProperty().bind(enabled.selectedProperty().not());
            switches.put(rule, enabled);
            values.put(rule, value);
            rules.add(enabled, 0, row);
            rules.add(value, 1, row++);
        }

        ListView<String> report = new ListView<>();
        report.setPrefHeight(120);
        ListView<String> points = new ListView<>();
        points.setPrefHeight(110);
        Label status = new Label();
        status.setWrapText(true);
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        final List<RuleResult> shown = new ArrayList<>();
        final List<Coordinate> shownPoints = new ArrayList<>();

        report.getSelectionModel().selectedIndexProperty().addListener((o, was, index) -> {
            shownPoints.clear();
            points.getItems().clear();
            host.locate(null, null);
            if (index.intValue() < 0 || index.intValue() >= shown.size()) {
                return;
            }
            RuleResult result = shown.get(index.intValue());
            shownPoints.addAll(result.points());
            for (Coordinate point : result.points()) {
                points.getItems().add(String.format(Locale.ROOT, "(%.4f, %.4f)", point.x, point.y));
            }
            if (!shownPoints.isEmpty()) {
                host.locate(shownPoints, null);
            }
        });
        points.getSelectionModel().selectedIndexProperty().addListener((o, was, index) -> {
            if (index.intValue() >= 0 && index.intValue() < shownPoints.size()) {
                host.locate(shownPoints, shownPoints.get(index.intValue()));
            }
        });

        Button run = new Button(I18n.text("ui.867be1ab29ed"));
        run.setMaxWidth(Double.MAX_VALUE);
        run.setOnAction(event -> {
            errorLabel.setText("");
            Map<Rule, Setting> settings = new EnumMap<>(Rule.class);
            for (Rule rule : Rule.values()) {
                double limit;
                try {
                    limit = Double.parseDouble(values.get(rule).getText().trim().replace(',', '.'));
                } catch (NumberFormatException invalid) {
                    if (switches.get(rule).isSelected()) {
                        errorLabel.setText(I18n.text("ui.bf2ebc1d7ba4") + I18n.caption(rule.title()));
                        return;
                    }
                    limit = defaults.get(rule).value();
                }
                settings.put(rule, new Setting(switches.get(rule).isSelected(), limit));
            }
            Selection selection = new Selection(copperTop.getValue(), copperBottom.getValue(), silkTop.getValue(),
                    silkBottom.getValue(), maskTop.getValue(), maskBottom.getValue(), outline.getValue(),
                    drills1.getValue(), drills2.getValue());
            run.setDisable(true);
            status.setText(I18n.text("ui.ad655d1872bc"));
            host.locate(null, null);
            host.check(selection, settings, results -> {
                run.setDisable(false);
                shown.clear();
                shown.addAll(results);
                report.getItems().clear();
                int failures = 0;
                for (RuleResult result : results) {
                    String verdict;
                    if (!result.ran()) {
                        verdict = I18n.text("ui.f6ca53d83ff3") + result.error();
                    } else if (result.failed()) {
                        failures++;
                        verdict = I18n.text("ui.f89d9b6c31cf") + Math.max(result.points().size(), result.sizes().size()) + ")";
                    } else {
                        verdict = "OK" + (result.note() == null ? "" : " - " + result.note());
                    }
                    String title = result.title();
                    String canonical = result.rule().title();
                    if (title.endsWith(canonical)) title = title.substring(0,title.length()-canonical.length()) + I18n.caption(canonical);
                    report.getItems().add(title + ": " + verdict);
                }
                status.setText(results.isEmpty() ? I18n.text("ui.42937c745350")
                        : failures == 0 ? I18n.text("ui.69cd24e91dea")
                        : failures + I18n.text("ui.18bfe9bede99"));
            }, message -> {
                run.setDisable(false);
                status.setText("");
                errorLabel.setText(message);
            });
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> {
            host.locate(null, null);
            onClose.run();
        });

        VBox panel = new VBox(8, new Label(I18n.text("ui.bc34cbca8e0f")), objects, new Label(I18n.text("ui.d62a97bce2ea")), rules, run,
                status, new Label(I18n.text("ui.3d0b6b4cabcc")), report, new Label(I18n.text("ui.af36208e90f1")),
                points, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }
}
