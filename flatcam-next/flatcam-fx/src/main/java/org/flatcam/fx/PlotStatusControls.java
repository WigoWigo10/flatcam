package org.flatcam.fx;

import java.util.function.Consumer;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/** The Python infobar's coordinates, grid and canvas-state controls. */
final class PlotStatusControls {

    private final PlotAreaView plot;
    private final Consumer<String> feedback;
    private final Consumer<AppPreferences.PlotStatusSettings> saveSettings;
    private final HBox root;
    private final Label coordinates = new Label("X: -   Y: -");
    private final ToggleButton gridSnap;
    private final TextField gridX = new TextField("1.0");
    private final TextField gridY = new TextField("1.0");
    private final CheckBox link = new CheckBox();
    private final ToggleButton axis;
    private final ToggleButton hud;
    private final ToggleButton workspace;
    private double stepX = 1.0;
    private double stepY = 1.0;
    private boolean gridVisible = true;

    PlotStatusControls(PlotAreaView plot, Function<String, Node> icon, Node consoleToggle,
                       Consumer<String> feedback, Runnable openPreferences) {
        this(plot, icon, consoleToggle, feedback, openPreferences,
                AppPreferences.loadPlotStatusSettings(), AppPreferences::savePlotStatusSettings);
    }

    PlotStatusControls(PlotAreaView plot, Function<String, Node> icon, Node consoleToggle,
                       Consumer<String> feedback, Runnable openPreferences,
                       AppPreferences.PlotStatusSettings saved,
                       Consumer<AppPreferences.PlotStatusSettings> saveSettings) {
        this.plot = plot;
        this.feedback = feedback;
        this.saveSettings = saveSettings;
        gridVisible = saved.gridVisible();
        plot.setGridVisible(gridVisible);
        stepX = saved.gridX();
        stepY = saved.gridY();
        gridX.setText(Double.toString(stepX));
        gridY.setText(Double.toString(stepY));
        plot.setCoordinateListener(coordinates::setText);
        plot.setGridSnap(saved.gridSnap(), stepX, stepY);

        coordinates.setTooltip(new Tooltip(I18n.text("ui.25a5a72ab81e")));
        coordinates.getStyleClass().add("status-coordinates");
        coordinates.setMinWidth(0);

        gridSnap = toggle(icon.apply("grid32.png"), I18n.text("ui.281fa099ff7d"), saved.gridSnap());
        gridSnap.getStyleClass().add("status-grid-snap");
        gridSnap.setOnAction(event -> {
            boolean validSpacing = applySpacing();
            if (!validSpacing) {
                // Invalid text restores the last valid spacing, not the old toggle state.
                plot.setGridSnap(gridSnap.isSelected(), stepX, stepY);
                save();
            }
            feedback.accept((gridSnap.isSelected() ? I18n.text("ui.c5b4edda912d") : I18n.text("ui.9549fd607129"))
                    + (validSpacing ? "" : I18n.text("ui.fce026586c61")));
        });
        gridX.setTooltip(new Tooltip(I18n.text("ui.3b02d8aece50")));
        gridY.setTooltip(new Tooltip(I18n.text("ui.e01146273271")));
        ToolDescriptions.apply(gridSnap,I18n.text("ui.5000c1262593"), I18n.text("ui.dcf6b0981479"));
        ToolDescriptions.apply(gridX,I18n.text("ui.a8f7a4fe38bd"), I18n.text("ui.97375a83efec"));
        ToolDescriptions.apply(gridY,I18n.text("ui.d47de20b83be"), I18n.text("ui.bff40e9232a5"));
        gridX.getStyleClass().add("status-grid-step");
        gridY.getStyleClass().add("status-grid-step");
        gridX.setPrefColumnCount(4);
        gridY.setPrefColumnCount(4);
        gridX.setOnAction(event -> applySpacing());
        gridY.setOnAction(event -> applySpacing());
        gridX.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                applySpacing();
            }
        });
        gridY.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                applySpacing();
            }
        });
        link.setSelected(saved.gridLinked());
        link.setTooltip(new Tooltip(I18n.text("ui.150daa4aaac6")));
        link.setOnAction(event -> {
            gridY.setDisable(link.isSelected());
            applySpacing();
        });
        gridY.setDisable(saved.gridLinked());

        axis = toggle(icon.apply("axis16.png"), I18n.text("ui.2d1ba624dbd7"), saved.axisVisible());
        axis.getStyleClass().add("status-axis");
        axis.setOnAction(event -> {
            plot.setAxisVisible(axis.isSelected());
            save();
            feedback.accept(axis.isSelected() ? I18n.text("ui.87a581d3ae22") : I18n.text("ui.8d4a3b36cb2d"));
        });
        plot.setAxisVisible(saved.axisVisible());
        hud = toggle(icon.apply("hud16.png"), I18n.text("ui.ebfa196b1a57"), saved.hudVisible());
        hud.getStyleClass().add("status-hud");
        hud.setOnAction(event -> {
            plot.setHudVisible(hud.isSelected());
            save();
            feedback.accept(hud.isSelected() ? I18n.text("ui.8f999b0a2b63") : I18n.text("ui.fd4b1bd2712e"));
        });
        plot.setHudVisible(saved.hudVisible());
        workspace = toggle(new Label("A4"), I18n.text("ui.c84e18f6a854"), saved.workspaceVisible());
        workspace.getStyleClass().add("status-workspace");
        workspace.setOnAction(event -> {
            plot.setWorkspaceVisible(workspace.isSelected());
            save();
            feedback.accept(workspace.isSelected() ? I18n.text("ui.0641e7893fa6") : I18n.text("ui.1c290a6b8f2f"));
        });
        plot.setWorkspaceVisible(saved.workspaceVisible());

        Button preferences = new Button(null, icon.apply("settings18.png"));
        preferences.getStyleClass().add("status-control");
        preferences.getStyleClass().add("status-preferences");
        preferences.setTooltip(new Tooltip(I18n.text("ui.40fab4decb11")));
        preferences.setOnAction(event -> openPreferences.run());

        root = new HBox(4, coordinates, gridSnap, gridX, link, gridY,
                axis, preferences, consoleToggle, hud, workspace);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.getStyleClass().add("plot-status-controls");
    }

    Node node() {
        return root;
    }

    void applySettings(AppPreferences.PlotStatusSettings settings) {
        gridSnap.setSelected(settings.gridSnap());
        gridVisible = settings.gridVisible();
        stepX = settings.gridX();
        stepY = settings.gridLinked() ? settings.gridX() : settings.gridY();
        gridX.setText(Double.toString(stepX));
        gridY.setText(Double.toString(stepY));
        link.setSelected(settings.gridLinked());
        gridY.setDisable(settings.gridLinked());
        axis.setSelected(settings.axisVisible());
        hud.setSelected(settings.hudVisible());
        workspace.setSelected(settings.workspaceVisible());
        plot.setGridSnap(settings.gridSnap(), stepX, stepY);
        plot.setGridVisible(gridVisible);
        plot.setAxisVisible(settings.axisVisible());
        plot.setHudVisible(settings.hudVisible());
        plot.setWorkspaceVisible(settings.workspaceVisible());
        save();
    }

    void toggleGrid() {
        gridSnap.fire();
    }

    void toggleAxis() {
        axis.fire();
    }

    void toggleHud() {
        hud.fire();
    }

    void toggleWorkspace() {
        workspace.fire();
    }

    private boolean applySpacing() {
        try {
            double x = parsePositive(gridX.getText());
            double y = link.isSelected() ? x : parsePositive(gridY.getText());
            stepX = x;
            stepY = y;
            if (link.isSelected()) {
                gridY.setText(gridX.getText().trim());
            }
            plot.setGridSnap(gridSnap.isSelected(), x, y);
            save();
            return true;
        } catch (IllegalArgumentException exception) {
            gridX.setText(Double.toString(stepX));
            if (link.isSelected()) {
                stepY = stepX;
                gridY.setText(Double.toString(stepX));
                plot.setGridSnap(gridSnap.isSelected(), stepX, stepY);
            } else {
                gridY.setText(Double.toString(stepY));
            }
            feedback.accept(I18n.text("ui.32c266172766"));
            return false;
        }
    }

    private static double parsePositive(String text) {
        double value = Double.parseDouble(text.trim().replace(',', '.'));
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Grid step must be positive");
        }
        return value;
    }

    private void save() {
        saveSettings.accept(new AppPreferences.PlotStatusSettings(
                gridSnap.isSelected(), gridVisible, stepX, stepY, link.isSelected(), axis.isSelected(),
                hud.isSelected(), workspace.isSelected()));
    }

    private static ToggleButton toggle(Node graphic, String tooltip, boolean selected) {
        ToggleButton button = new ToggleButton(null, graphic);
        button.getStyleClass().add("status-control");
        button.setTooltip(new Tooltip(tooltip));
        button.setSelected(selected);
        return button;
    }
}
