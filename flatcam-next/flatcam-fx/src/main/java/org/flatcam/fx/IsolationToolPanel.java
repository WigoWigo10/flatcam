package org.flatcam.fx;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.locationtech.jts.geom.Geometry;

/**
 * Parameters for isolation routing, as an embeddable panel rather than a
 * modal dialog - appTools/ToolIsolation.py's run() switches the LEFT
 * sidebar's own "Tool" tab (app.ui.tool_tab) to this tool's IsoUI instead of
 * popping a separate window, and switches back to the Properties tab once
 * generation finishes (see ToolIsolation.py around its final
 * app.ui.notebook.setCurrentWidget(app.ui.properties_tab)). MainWindow wires
 * this the same way via its own "Ferramenta" tab.
 */
final class IsolationToolPanel {

    @FunctionalInterface
    interface AreaSelectionStarter {
        boolean begin(boolean polygon, Consumer<Geometry> onSelected, Runnable onCancelled);
    }

    record ExceptionArea(String name, Geometry geometry) {
        @Override
        public String toString() {
            return name;
        }
    }

    record Result(IsolationParameters geometryParams, boolean combinePasses, boolean follow,
                  Geometry exceptionMask) {
    }

    private IsolationToolPanel() {
    }

    /**
     * @param onGenerate called with the parsed parameters when "Gerar" is clicked and they're valid.
     * @param onClose    called when "Fechar" is clicked - MainWindow uses it to restore the tool tab's placeholder.
     */
    static Node build(String units, List<ExceptionArea> exceptionAreas, AreaSelectionStarter areaStarter,
                      Runnable cancelArea,
                      Consumer<Result> onGenerate, Runnable onClose) {
        boolean metric = "MM".equals(units);

        TextField toolDiaField = new TextField(metric ? "0.2" : "0.008");
        TextField passesField = new TextField("1");
        TextField overlapField = new TextField("15");
        ComboBox<IsolationType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(IsolationType.values());
        typeCombo.setValue(IsolationType.BOTH);
        CheckBox combinePasses = new CheckBox("Combinar passes em uma Geometry");
        combinePasses.setSelected(true);
        CheckBox follow = new CheckBox("Follow: seguir centros de trilhas e pads sem afastamento");
        follow.setWrapText(true);
        follow.selectedProperty().addListener((observable, oldValue, selected) -> {
            passesField.setDisable(selected);
            overlapField.setDisable(selected);
            typeCombo.setDisable(selected);
            combinePasses.setDisable(selected);
        });
        ComboBox<ExceptionArea> exceptionCombo = new ComboBox<>();
        ExceptionArea none = new ExceptionArea("Nenhuma", null);
        exceptionCombo.getItems().add(none);
        exceptionCombo.getItems().addAll(exceptionAreas);
        exceptionCombo.setValue(none);
        exceptionCombo.setMaxWidth(Double.MAX_VALUE);
        Geometry[] drawnMask = new Geometry[1];
        Label areaStatus = new Label("Nenhuma area desenhada.");
        areaStatus.setWrapText(true);
        exceptionCombo.valueProperty().addListener((observable, oldValue, selected) -> {
            cancelArea.run();
            drawnMask[0] = null;
            areaStatus.setText("Nenhuma area desenhada.");
        });
        Button rectangleButton = new Button("Desenhar retangulo de excecao");
        Button polygonButton = new Button("Desenhar poligono de excecao");
        for (Button button : new Button[]{rectangleButton, polygonButton}) {
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> {
                boolean polygon = button == polygonButton;
                if (areaStarter.begin(polygon, area -> {
                    drawnMask[0] = area;
                    areaStatus.setText((polygon ? "Poligono" : "Retangulo") + " de excecao selecionado.");
                }, () -> areaStatus.setText("Selecao de area cancelada."))) {
                    areaStatus.setText(polygon
                            ? "Clique nos vertices; Enter ou botao direito conclui; Esc cancela."
                            : "Clique em dois cantos do retangulo; Esc cancela.");
                } else {
                    areaStatus.setText("Nao foi possivel iniciar a selecao de area.");
                }
            });
        }
        Button clearAreaButton = new Button("Limpar area desenhada");
        clearAreaButton.setMaxWidth(Double.MAX_VALUE);
        clearAreaButton.setOnAction(event -> {
            cancelArea.run();
            drawnMask[0] = null;
            areaStatus.setText("Nenhuma area desenhada.");
        });

        for (TextField field : new TextField[]{toolDiaField, passesField, overlapField}) {
            field.setPrefColumnCount(7);
            field.setMinWidth(0);
        }
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Diametro da ferramenta:"), toolDiaField);
        grid.addRow(1, new Label("Numero de passes:"), passesField);
        grid.addRow(2, new Label("Sobreposicao entre passes (%):"), overlapField);
        grid.addRow(3, new Label("Aneis a manter:"), typeCombo);
        grid.addRow(4, new Label("Excluir area (Geometry preenchida):"), exceptionCombo);
        Button generateButton = new Button("Gerar Geometry de isolamento");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        Button closeButton = new Button("Fechar");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            try {
                Result result = parseResult(toolDiaField, passesField, overlapField, typeCombo,
                        combinePasses.isSelected(), follow.isSelected(),
                        drawnMask[0] != null ? drawnMask[0] : exceptionCombo.getValue().geometry());
                errorLabel.setText("");
                onGenerate.accept(result);
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });
        closeButton.setOnAction(e -> onClose.run());

        Label workflowNote = new Label("O resultado sera uma Geometry editavel. Depois, use Geometry -> "
                + "Criar CNC Job para configurar corte e salvar G-code.");
        workflowNote.setWrapText(true);
        VBox box = new VBox(10,
                new Label("Parametros (unidades do arquivo: " + units + ")"),
                grid, rectangleButton, polygonButton, clearAreaButton, areaStatus,
                combinePasses, follow, workflowNote,
                errorLabel, generateButton, closeButton);
        box.setPadding(new Insets(12));
        return box;
    }

    private static Result parseResult(
            TextField toolDiaField, TextField passesField, TextField overlapField,
            ComboBox<IsolationType> typeCombo, boolean combinePasses, boolean follow,
            Geometry exceptionMask) {
        double toolDia = parseDouble(toolDiaField.getText(), "Diametro da ferramenta");
        double rawPasses = parseDouble(passesField.getText(), "Numero de passes");
        if (!Double.isFinite(rawPasses) || rawPasses != Math.rint(rawPasses)
                || rawPasses < 1 || rawPasses > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Numero de passes deve ser inteiro positivo.");
        }
        int passes = (int) rawPasses;
        double overlapPercent = parseDouble(overlapField.getText(), "Sobreposicao");
        IsolationType type = typeCombo.getValue();

        IsolationParameters geometryParams = new IsolationParameters(toolDia, passes, overlapPercent / 100.0, type);

        return new Result(geometryParams, combinePasses, follow, exceptionMask);
    }

    private static double parseDouble(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + ": numero invalido");
        }
    }
}
