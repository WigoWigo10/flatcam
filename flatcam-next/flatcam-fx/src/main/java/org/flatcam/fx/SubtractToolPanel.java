package org.flatcam.fx;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * appTools/ToolSub.py: removes what a subtractor object covers from a target, for two Gerbers or for two
 * Geometries. The result is a new object {@code <target>_sub}; "Close paths" (Geometry) cuts the target as one
 * closed shape instead of ring by ring, and "Delete sources" removes both inputs afterwards.
 */
final class SubtractToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        List<TreeItem<String>> geometries();

        TreeItem<String> initialTarget();

        /** Returns an error message, or null when the new object was created. */
        String subtractGerber(TreeItem<String> target, TreeItem<String> subtractor, boolean deleteSources);

        String subtractGeometry(TreeItem<String> target, TreeItem<String> subtractor, boolean closePaths,
                                boolean deleteSources);
    }

    private SubtractToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ToggleGroup kindGroup = new ToggleGroup();
        RadioButton gerberKind = new RadioButton("Gerber");
        RadioButton geometryKind = new RadioButton("Geometry");
        gerberKind.setToggleGroup(kindGroup);
        geometryKind.setToggleGroup(kindGroup);
        boolean startWithGeometry = host.initialTarget() != null && host.geometries().contains(host.initialTarget());
        (startWithGeometry ? geometryKind : gerberKind).setSelected(true);

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
        ComboBox<TreeItem<String>> target = new ComboBox<>();
        ComboBox<TreeItem<String>> subtractor = new ComboBox<>();
        for (ComboBox<TreeItem<String>> combo : List.of(target, subtractor)) {
            combo.setConverter(names);
            combo.setMaxWidth(Double.MAX_VALUE);
        }
        Runnable fill = () -> {
            List<TreeItem<String>> candidates = gerberKind.isSelected() ? host.gerbers() : host.geometries();
            target.getItems().setAll(candidates);
            subtractor.getItems().setAll(candidates);
            TreeItem<String> initial = host.initialTarget();
            if (initial != null && candidates.contains(initial)) {
                target.getSelectionModel().select(initial);
            } else {
                target.getSelectionModel().selectFirst();
            }
            subtractor.getSelectionModel().select(candidates.size() > 1 ? candidates.get(candidates.size() - 1) : null);
            if (candidates.size() > 1 && subtractor.getValue() == target.getValue()) {
                subtractor.getSelectionModel().select(candidates.get(0) == target.getValue() ? candidates.get(1) : candidates.get(0));
            }
        };
        kindGroup.selectedToggleProperty().addListener((observable, previous, next) -> fill.run());
        fill.run();

        CheckBox closePaths = new CheckBox(I18n.text("ui.e65816e31e60"));
        closePaths.setSelected(true);
        closePaths.setTooltip(tooltip(I18n.text("ui.11fca7e57c5e")));
        closePaths.visibleProperty().bind(geometryKind.selectedProperty());
        closePaths.managedProperty().bind(closePaths.visibleProperty());
        CheckBox deleteSources = new CheckBox(I18n.text("ui.38bf900d7e10"));
        deleteSources.setTooltip(tooltip(I18n.text("ui.dfc40f243592")));

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button subtract = new Button(I18n.text("ui.306dd9dc882b"));
        subtract.setMaxWidth(Double.MAX_VALUE);
        subtract.setOnAction(event -> {
            TreeItem<String> targetItem = target.getValue();
            TreeItem<String> subtractorItem = subtractor.getValue();
            if (targetItem == null) {
                errorLabel.setText(I18n.text("ui.48a7e7b4e7c5"));
                return;
            }
            if (subtractorItem == null) {
                errorLabel.setText(I18n.text("ui.c70986a3cab4"));
                return;
            }
            if (targetItem == subtractorItem) {
                errorLabel.setText(I18n.text("ui.15a71576c75a"));
                return;
            }
            String error = gerberKind.isSelected()
                    ? host.subtractGerber(targetItem, subtractorItem, deleteSources.isSelected())
                    : host.subtractGeometry(targetItem, subtractorItem, closePaths.isSelected(),
                            deleteSources.isSelected());
            errorLabel.setText(error == null ? "" : error);
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.1dea7ffe4eaf")), new HBox(10, gerberKind, geometryKind),
                new Label(I18n.text("ui.3c51f0d21812")), target,
                new Label(I18n.text("ui.9ff5fae60765")), subtractor,
                closePaths, deleteSources, subtract, errorLabel, close);
        panel.setPadding(new Insets(6));
        return panel;
    }

    private static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(320);
        tooltip.setShowDuration(Duration.seconds(30));
        return tooltip;
    }
}
