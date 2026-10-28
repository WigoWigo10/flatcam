package org.flatcam.fx;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.flatcam.cam.convert.InvertGerber;

/**
 * appTools/ToolInvertGerber.py: makes a new Gerber in which copper and empty space swap places inside a box
 * around the original, grown by a margin. Python's defaults: 0.1 margin and square corners.
 */
final class InvertGerberToolPanel {

    interface Host {
        List<TreeItem<String>> gerbers();

        TreeItem<String> initialGerber();

        /** Creates the inverted Gerber; returns an error message, or null on success. */
        String invert(TreeItem<String> gerber, double margin, InvertGerber.JoinStyle style);
    }

    private InvertGerberToolPanel() {
    }

    static Node build(Host host, Runnable onClose) {
        ComboBox<TreeItem<String>> gerber = new ComboBox<>();
        gerber.getItems().setAll(host.gerbers());
        gerber.setMaxWidth(Double.MAX_VALUE);
        gerber.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(TreeItem<String> item) {
                return item == null ? "" : item.getValue();
            }

            @Override
            public TreeItem<String> fromString(String text) {
                return null;
            }
        });
        gerber.getSelectionModel().select(host.initialGerber());
        if (gerber.getValue() == null) {
            gerber.getSelectionModel().selectFirst();
        }

        TextField margin = new TextField(ToolDefaults.text("invert.margin"));
        margin.setPrefColumnCount(6);
        margin.setTooltip(tooltip(I18n.text("ui.b7a1258dbdfe")));
        ToggleGroup joins = new ToggleGroup();
        RadioButton round = new RadioButton(I18n.text("ui.c4f828746ee5"));
        RadioButton bevel = new RadioButton(I18n.text("ui.b438811609f0"));
        RadioButton square = new RadioButton(I18n.text("ui.cb0a64bafea0"));
        for (RadioButton radio : List.of(round, bevel, square)) {
            radio.setToggleGroup(joins);
        }
        square.setSelected(true);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error-label");
        errorLabel.setWrapText(true);

        Button invert = new Button(I18n.text("ui.5ab376cf79ba"));
        invert.setMaxWidth(Double.MAX_VALUE);
        invert.setOnAction(event -> {
            try {
                if (gerber.getValue() == null) {
                    throw new IllegalArgumentException(I18n.text("ui.fa269858e24f"));
                }
                double value = Double.parseDouble(margin.getText().trim().replace(',', '.'));
                InvertGerber.JoinStyle style = round.isSelected() ? InvertGerber.JoinStyle.ROUND
                        : bevel.isSelected() ? InvertGerber.JoinStyle.BEVEL : InvertGerber.JoinStyle.SQUARE;
                String error = host.invert(gerber.getValue(), value, style);
                errorLabel.setText(error == null ? "" : error);
            } catch (NumberFormatException invalid) {
                errorLabel.setText(I18n.text("ui.68c1b5c68e94"));
            } catch (IllegalArgumentException invalid) {
                errorLabel.setText(invalid.getMessage());
            }
        });
        Button close = new Button(I18n.text("ui.0f2bd88ef0ac"));
        close.setOnAction(event -> onClose.run());

        VBox panel = new VBox(8,
                new Label(I18n.text("ui.726d6f3952cd")), gerber,
                new HBox(6, new Label(I18n.text("ui.16bab5b0d1de")), margin),
                new Label(I18n.text("ui.e5595bb67459")), new HBox(10, round, bevel, square),
                invert, errorLabel, close);
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

    static String describe(double area, String units) {
        return String.format(Locale.ROOT, "%.4f %s^2", area, units.toLowerCase(Locale.ROOT));
    }
}
