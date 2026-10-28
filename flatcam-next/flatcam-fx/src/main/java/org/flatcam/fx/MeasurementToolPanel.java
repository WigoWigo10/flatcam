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
    private final String units;
    private final TextField start = output("measure-start");
    private final TextField stop = output("measure-stop");
    private final TextField dx = output("measure-dx");
    private final TextField dy = output("measure-dy");
    private final TextField angle = output("measure-angle");
    private final TextField distance = output("measure-distance");
    private final TextField half = output("measure-half");
    private final Label message = new Label();
    private final Button jumpHalf = new Button(I18n.text("ui.30814c7f378a"));
    private Measurement current;

    MeasurementToolPanel(boolean minimum, String units, Runnable measure, java.util.function.Consumer<double[]> jumpTo,
                         Runnable close) {
        this.minimum = minimum;
        this.units = units;
        setSpacing(8);
        setPadding(new Insets(8));
        Label title = new Label(minimum ? I18n.text("ui.61ff53fbc31f") : I18n.text("ui.475f2d0d401e"));
        title.getStyleClass().add("tool-title");
        Label help = new Label(minimum
                ? I18n.text("ui.ac9e555f5c52")
                : I18n.text("ui.b2248df20fa5"));
        help.setWrapText(true);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        String unit = units == null ? "" : " (" + units.toLowerCase(Locale.ROOT) + ")";
        int row = 0;
        grid.addRow(row++, new Label(minimum ? I18n.text("ui.a9be6dbe6fbc") : I18n.text("ui.325dfc37c9ba")), start);
        grid.addRow(row++, new Label(minimum ? I18n.text("ui.11d195a76923") : I18n.text("ui.672ff2e837d3")), stop);
        grid.addRow(row++, new Label("Dx" + unit), dx);
        grid.addRow(row++, new Label("Dy" + unit), dy);
        grid.addRow(row++, new Label(I18n.text("ui.a252ef765e2d")), angle);
        grid.addRow(row++, new Label(I18n.text("ui.dfea8d0b0700") + unit), distance);
        if (minimum) grid.addRow(row, new Label(I18n.text("ui.961efe4595fe")), half);
        Button run = new Button(minimum ? I18n.text("ui.3c1cabb34511") : I18n.text("ui.20c31be7bcbc"));
        run.setId("measure-run");
        run.setOnAction(event -> measure.run());
        jumpHalf.setId("measure-jump-half");
        jumpHalf.setDisable(true);
        jumpHalf.setOnAction(event -> { if (current != null) jumpTo.accept(current.halfPoint()); });
        Button closeButton = new Button(I18n.text("ui.0f2bd88ef0ac"));
        closeButton.setOnAction(event -> close.run());
        String linear = I18n.text("ui.3970547f0ca7") + units + I18n.text("ui.957775cd1315");
        ToolDescriptions.apply(start, I18n.text("ui.a9be6dbe6fbc"), minimum
                ? I18n.text("ui.3228ce1d5c5d") + linear
                : I18n.text("ui.815d0d245b19") + linear);
        ToolDescriptions.apply(stop, I18n.text("ui.11d195a76923"), minimum
                ? I18n.text("ui.20ab022a43a4") + linear
                : I18n.text("ui.2555fcb347a0") + linear);
        ToolDescriptions.apply(dx, I18n.text("ui.4e3a4a8bfc84"), I18n.text("ui.354b35d0e907") + linear);
        ToolDescriptions.apply(dy, I18n.text("ui.37ad92d4d4a4"), I18n.text("ui.3022ec747448") + linear);
        ToolDescriptions.apply(distance, I18n.text("ui.6267165807a4"), I18n.text("ui.64ed4de6760c") + linear);
        ToolDescriptions.apply(angle, I18n.text("ui.01f3f8d16b82"), minimum
                ? I18n.text("ui.6c573f3174da")
                : I18n.text("ui.6b60aceeef2f"));
        ToolDescriptions.apply(half, I18n.text("ui.961efe4595fe"), I18n.text("ui.a22251c407db") + linear);
        ToolDescriptions.apply(run, minimum ? I18n.text("ui.7b1672e9941a") : I18n.text("ui.20c31be7bcbc"), minimum
                ? I18n.text("ui.0218a5758e61")
                : I18n.text("ui.3d8f0b298cf2"));
        ToolDescriptions.apply(jumpHalf, I18n.text("ui.50e6b55efd55"), I18n.text("ui.add7cf57e2f3"));
        ToolDescriptions.apply(closeButton, I18n.text("ui.fef72101391e"), I18n.text("ui.d6b5ab0b1f93"));
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
        message.setText(I18n.text("ui.2388cf3e696d"));
    }

    void showResult(Measurement measurement) {
        current = measurement;
        start.setText(point(measurement.x1(), measurement.y1()));
        stop.setText(point(measurement.x2(), measurement.y2()));
        dx.setText(display(measurement.dx()));
        dy.setText(display(measurement.dy()));
        angle.setText(display(minimum ? measurement.minimumAngle() : measurement.angle()));
        distance.setText(display(measurement.distance()));
        double[] h = measurement.distance() != 0 ? measurement.halfPoint() : new double[]{0, 0};
        half.setText(point(h[0], h[1]));
        jumpHalf.setDisable(!minimum || measurement.distance() == 0);
        message.setText(minimum && measurement.distance() == 0
                ? I18n.text("ui.442cc841183e") + point(measurement.halfPoint()[0], measurement.halfPoint()[1]) + "."
                : summary(measurement));
    }

    void showMessage(String text) {
        message.setText(text);
    }

    String summary(Measurement measurement) {
        return I18n.text("ui.d9910db83cb2") + display(measurement.dx()) + " | D(y) = " + display(measurement.dy())
                + I18n.text("ui.5e32900fa6e9") + display(measurement.distance());
    }

    private String point(double x, double y) {
        return "(" + display(x) + ", " + display(y) + ")";
    }

    private String display(double value) { return UiNumbers.fixed(value,units); }

    private static TextField output(String id) {
        TextField field = new TextField();
        field.setId(id);
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.setPrefColumnCount(18);
        return field;
    }
}
