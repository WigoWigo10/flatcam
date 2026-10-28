package org.flatcam.fx;

import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.distance.DistanceOp;

/**
 * Python's Distance Tool (ToolDistance.py) and Minimum Distance Tool (ToolDistanceMin.py) as one
 * read-only results panel: start/stop points, |dx|, |dy|, angle and total distance, plus the half
 * point for the minimum distance.
 */
final class MeasurementToolPanel extends VBox {

    /** Two measured points; results exactly as the Python tools display them. */
    record Measurement(double x1, double y1, double x2, double y2) {
        double dx() { return Math.abs(x2 - x1); }
        double dy() { return Math.abs(y2 - y1); }
        double distance() { return Math.hypot(x2 - x1, y2 - y1); }

        /** ToolDistance: atan2(dy, dx) from start to stop, in [0, 360). */
        double angle() {
            double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
            return angle < 0 ? angle + 360 : angle;
        }

        /**
         * ToolDistanceMin: atan(dy/dx) of first-minus-last, in (-90, 90]. Python leaves the previous
         * value when dx is zero (ZeroDivisionError); here a vertical pair reports 90.
         */
        double minimumAngle() {
            double dx = x1 - x2, dy = y1 - y2;
            return dx == 0 ? 90.0 : Math.toDegrees(Math.atan(dy / dx));
        }

        double[] halfPoint() {
            return new double[]{Math.min(x1, x2) + dx() / 2, Math.min(y1, y2) + dy() / 2};
        }
    }

    /** ToolDistanceMin's {@code nearest_points(first, second)}. */
    static Measurement nearest(Geometry first, Geometry second) {
        Coordinate[] points = DistanceOp.nearestPoints(first, second);
        return new Measurement(points[0].x, points[0].y, points[1].x, points[1].y);
    }

    private final boolean minimum;
    private final TextField start = output("measure-start");
    private final TextField stop = output("measure-stop");
    private final TextField dx = output("measure-dx");
    private final TextField dy = output("measure-dy");
    private final TextField angle = output("measure-angle");
    private final TextField distance = output("measure-distance");
    private final TextField half = output("measure-half");
    private final Label message = new Label();
    private final Button jumpHalf = new Button("Ir para o ponto médio");
    private Measurement current;

    MeasurementToolPanel(boolean minimum, String units, Runnable measure, java.util.function.Consumer<double[]> jumpTo,
                         Runnable close) {
        this.minimum = minimum;
        setSpacing(8);
        setPadding(new Insets(8));
        Label title = new Label(minimum ? "Distância Mínima" : "Medir Distância");
        title.getStyleClass().add("tool-title");
        Label help = new Label(minimum
                ? "Mede a menor distância entre os dois objetos selecionados."
                : "Clique no ponto inicial e depois no ponto final, no Plot. Snap da grade é usado quando ativo; Esc cancela.");
        help.setWrapText(true);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        String unit = units == null ? "" : " (" + units.toLowerCase(Locale.ROOT) + ")";
        int row = 0;
        grid.addRow(row++, new Label(minimum ? "Primeiro ponto" : "Início"), start);
        grid.addRow(row++, new Label(minimum ? "Segundo ponto" : "Fim"), stop);
        grid.addRow(row++, new Label("Dx" + unit), dx);
        grid.addRow(row++, new Label("Dy" + unit), dy);
        grid.addRow(row++, new Label("Ângulo (°)"), angle);
        grid.addRow(row++, new Label("Distância" + unit), distance);
        if (minimum) grid.addRow(row, new Label("Ponto médio"), half);
        Button run = new Button(minimum ? "Medir" : "Nova medição");
        run.setId("measure-run");
        run.setOnAction(event -> measure.run());
        jumpHalf.setId("measure-jump-half");
        jumpHalf.setDisable(true);
        jumpHalf.setOnAction(event -> { if (current != null) jumpTo.accept(current.halfPoint()); });
        Button closeButton = new Button("Fechar");
        closeButton.setOnAction(event -> close.run());
        String linear = "\n\nUnidades: " + units + ". Os resultados são somente leitura.";
        ToolDescriptions.apply(start, "Primeiro ponto", minimum
                ? "Ponto da primeira geometria que forma o segmento de menor distância até a segunda." + linear
                : "Coordenadas do primeiro clique da medição. O Snap ativo é respeitado." + linear);
        ToolDescriptions.apply(stop, "Segundo ponto", minimum
                ? "Ponto da segunda geometria mais próximo da primeira; não é necessariamente um centro de furo ou pad." + linear
                : "Coordenadas do segundo clique da medição. O Snap ativo é respeitado." + linear);
        ToolDescriptions.apply(dx, "Distância horizontal", "Valor absoluto da diferença X entre os dois pontos; não indica o sentido do movimento." + linear);
        ToolDescriptions.apply(dy, "Distância vertical", "Valor absoluto da diferença Y entre os dois pontos; não indica o sentido do movimento." + linear);
        ToolDescriptions.apply(distance, "Distância entre pontos", "Comprimento em linha reta entre os pontos. Não é o comprimento de um trajeto de corte." + linear);
        ToolDescriptions.apply(angle, "Ângulo da medição", minimum
                ? "Inclinação do segmento entre os pontos mais próximos, entre -90° e 90°; um segmento vertical mostra 90°.\n\nUnidades: graus."
                : "Direção do ponto inicial ao final, de 0° a menos de 360°, em sentido anti-horário a partir de X.\n\nUnidades: graus.");
        ToolDescriptions.apply(half, "Ponto médio", "Coordenadas no meio do segmento de menor distância entre os dois objetos." + linear);
        ToolDescriptions.apply(run, minimum ? "Medir distância mínima" : "Nova medição", minimum
                ? "Calcula os pontos mais próximos de exatamente dois objetos selecionados. Objetos que se tocam ou se intersectam têm distância zero.\n\nResultado: não modifica objetos nem cria caminhos CAM."
                : "Inicia outra medição por dois cliques no Plot Area. Substitui o resultado anterior sem modificar os objetos.\n\nAtalho: Esc cancela a coleta.");
        ToolDescriptions.apply(jumpHalf, "Localizar ponto médio", "Centraliza a vista no meio do segmento medido. Disponível após uma distância mínima maior que zero; não move os objetos.");
        ToolDescriptions.apply(closeButton, "Fechar medição", "Fecha o painel e remove sua marcação temporária do Plot Area. Não altera os objetos.");
        HBox buttons = minimum ? new HBox(6, run, jumpHalf, closeButton) : new HBox(6, run, closeButton);
        message.setWrapText(true);
        message.setId("measure-message");
        getChildren().addAll(title, help, grid, buttons, message);
        clear();
    }

    void clear() {
        current = null;
        for (TextField field : new TextField[]{start, stop}) field.setText("(0, 0)");
        for (TextField field : new TextField[]{dx, dy, angle, distance}) field.setText("0.0");
        half.setText("(0, 0)");
        jumpHalf.setDisable(true);
        message.setText("");
    }

    void showStart(double x, double y) {
        clear();
        start.setText(point(x, y));
        message.setText("Clique no ponto FINAL...");
    }

    void showResult(Measurement measurement) {
        current = measurement;
        start.setText(point(measurement.x1(), measurement.y1()));
        stop.setText(point(measurement.x2(), measurement.y2()));
        dx.setText(number(measurement.dx()));
        dy.setText(number(measurement.dy()));
        angle.setText(number(minimum ? measurement.minimumAngle() : measurement.angle()));
        distance.setText(number(measurement.distance()));
        double[] h = measurement.distance() != 0 ? measurement.halfPoint() : new double[]{0, 0};
        half.setText(point(h[0], h[1]));
        jumpHalf.setDisable(!minimum || measurement.distance() == 0);
        message.setText(minimum && measurement.distance() == 0
                ? "Os objetos se intersectam ou se tocam em " + point(measurement.halfPoint()[0], measurement.halfPoint()[1]) + "."
                : summary(measurement));
    }

    void showMessage(String text) {
        message.setText(text);
    }

    String summary(Measurement measurement) {
        return "MEDINDO: Resultado D(x) = " + number(measurement.dx()) + " | D(y) = " + number(measurement.dy())
                + " | Distância = " + number(measurement.distance());
    }

    private static String point(double x, double y) {
        return "(" + number(x) + ", " + number(y) + ")";
    }

    static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static TextField output(String id) {
        TextField field = new TextField();
        field.setId(id);
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.setPrefColumnCount(18);
        return field;
    }
}
