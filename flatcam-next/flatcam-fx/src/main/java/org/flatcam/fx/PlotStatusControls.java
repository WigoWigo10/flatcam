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

    PlotStatusControls(PlotAreaView plot, Function<String, Node> icon, Node consoleToggle,
                       Consumer<String> feedback) {
        this.plot = plot;
        this.feedback = feedback;
        AppPreferences.PlotStatusSettings saved = AppPreferences.loadPlotStatusSettings();
        stepX = saved.gridX();
        stepY = saved.gridY();
        gridX.setText(Double.toString(stepX));
        gridY.setText(Double.toString(stepY));
        plot.setCoordinateListener(coordinates::setText);
        plot.setGridSnap(saved.gridSnap(), stepX, stepY);

        coordinates.setTooltip(new Tooltip("Coordenadas absolutas do cursor no Plot Area"));
        coordinates.getStyleClass().add("status-coordinates");

        gridSnap = toggle(icon.apply("grid32.png"), "Ativar/desativar snap na grade", saved.gridSnap());
        gridSnap.setOnAction(event -> {
            if (applySpacing()) {
                feedback.accept(gridSnap.isSelected() ? "Snap na grade ativado." : "Snap na grade desativado.");
            } else {
                gridSnap.setSelected(!gridSnap.isSelected());
                plot.setGridSnap(gridSnap.isSelected(), stepX, stepY);
            }
        });
        gridX.setTooltip(new Tooltip("Distancia do snap em X"));
        gridY.setTooltip(new Tooltip("Distancia do snap em Y"));
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
        link.setTooltip(new Tooltip("Usar a distancia X tambem em Y"));
        link.setOnAction(event -> {
            gridY.setDisable(link.isSelected());
            applySpacing();
        });
        gridY.setDisable(saved.gridLinked());

        axis = toggle(icon.apply("axis16.png"), "Mostrar/ocultar eixos", saved.axisVisible());
        axis.setOnAction(event -> {
            plot.setAxisVisible(axis.isSelected());
            save();
            feedback.accept(axis.isSelected() ? "Eixos visiveis." : "Eixos ocultos.");
        });
        plot.setAxisVisible(saved.axisVisible());
        hud = toggle(icon.apply("hud16.png"), "Mostrar/ocultar coordenadas no Plot Area", saved.hudVisible());
        hud.setOnAction(event -> {
            plot.setHudVisible(hud.isSelected());
            save();
            feedback.accept(hud.isSelected() ? "HUD visivel." : "HUD oculto.");
        });
        plot.setHudVisible(saved.hudVisible());
        workspace = toggle(new Label("A4"), "Mostrar/ocultar limites da area A4", saved.workspaceVisible());
        workspace.setOnAction(event -> {
            plot.setWorkspaceVisible(workspace.isSelected());
            save();
            feedback.accept(workspace.isSelected() ? "Area A4 visivel." : "Area A4 oculta.");
        });
        plot.setWorkspaceVisible(saved.workspaceVisible());

        Button preferences = new Button(null, icon.apply("settings18.png"));
        preferences.getStyleClass().addAll("status-control", "planned-command");
        preferences.setTooltip(new Tooltip("Preferencias - em desenvolvimento"));
        preferences.setDisable(true);

        root = new HBox(4, coordinates, gridSnap, gridX, link, gridY,
                axis, preferences, consoleToggle, hud, workspace);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.getStyleClass().add("plot-status-controls");
    }

    Node node() {
        return root;
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
            feedback.accept("Informe passos X/Y numericos e positivos.");
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
        AppPreferences.savePlotStatusSettings(new AppPreferences.PlotStatusSettings(
                gridSnap.isSelected(), stepX, stepY, link.isSelected(), axis.isSelected(),
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
