package org.flatcam.fx;

import java.util.Locale;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import org.flatcam.cam.geometry.ToolProfile;

/** Original schematic vector artwork; not manufacturer's images or a change to machining semantics. */
final class ToolProfilePicker {
    private ToolProfilePicker() { }

    /** Pilot: only IsolationToolPanel opts in. Preserve values, width and the caller's action handler. */
    static void decorate(ComboBox<ToolProfile> choice) {
        choice.getStyleClass().add("tool-profile-choice");
        choice.setCellFactory(view -> cell(false));
        choice.setButtonCell(cell(true));
        choice.valueProperty().addListener((observable, before, profile) -> updateHelp(choice, profile));
        updateHelp(choice, choice.getValue());
    }

    private static ListCell<ToolProfile> cell(boolean compact) {
        return new ListCell<>() {
            {
                setAlignment(Pos.CENTER_LEFT);
                setGraphicTextGap(compact ? 3 : 10);
                // The table's type column is narrow: the shown value gets minimal padding (inline beats the theme's).
                if (compact) setStyle("-fx-padding: 0 0 0 2;");
            }
            @Override protected void updateItem(ToolProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                if (empty || profile == null) {
                    setText(null); setGraphic(null); setAccessibleText(null);
                    return;
                }
                setText(compact ? profile.name() : profile.name() + " — " + description(profile));
                setGraphic(icon(profile, compact ? 14 : 32, textFillProperty()));
                setAccessibleText(profile.name() + ": " + description(profile));
            }
        };
    }

    static String description(ToolProfile profile) {
        return switch (profile) {
            case C1 -> "Fresa plana · 1 dente";
            case C2 -> "Fresa plana · 2 dentes";
            case C3 -> "Fresa plana · 3 dentes";
            case C4 -> "Fresa plana · 4 dentes";
            case B -> "Fresa esférica";
            case V -> "Fresa em V";
        };
    }

    private static void updateHelp(ComboBox<ToolProfile> choice, ToolProfile profile) {
        String title = profile == null ? "Tipo de fresa" : profile.name() + " — " + description(profile);
        String text = profile == null ? "Escolha o tipo de fresa."
                : profile == ToolProfile.V
                ? "Ponta em V. Configure V-Tip Dia e Angle no CNC Job. Desenho esquematico, sem escala."
                : "Tipo informativo neste painel. C1–C4: a vista da ponta indica o numero de dentes. "
                        + "Desenho esquematico, sem escala; o perfil nao altera o calculo de isolamento.";
        // The window's FluidTooltips consumes these properties; avoid a second native popup.
        choice.setTooltip(null);
        choice.getProperties().put(FluidTooltips.TITLE_KEY, title);
        choice.getProperties().put(FluidTooltips.TEXT_KEY, text);
        choice.setAccessibleHelp(title + ". " + text);
    }

    /** Text-fill binding follows normal, hover and selection colours, including popup scenes and live theme swaps. */
    static Node icon(ToolProfile profile, double size, ObservableValue<? extends Paint> ink) {
        if (profile == null || !Double.isFinite(size) || size <= 0) throw new IllegalArgumentException("Invalid tool icon.");
        SVGPath outline = new SVGPath();
        outline.setContent(path(profile));
        outline.setFill(Color.TRANSPARENT);
        outline.strokeProperty().bind(ink);
        outline.setStrokeWidth(1.65);
        outline.setStrokeLineCap(StrokeLineCap.ROUND);
        outline.setStrokeLineJoin(StrokeLineJoin.ROUND);
        outline.getStyleClass().add("tool-profile-outline");
        Group scaled = new Group(outline);
        scaled.getTransforms().add(new Scale(size / 32, size / 32));
        Group measured = new Group(scaled);
        StackPane holder = new StackPane(measured);
        holder.setMinSize(size, size); holder.setPrefSize(size, size); holder.setMaxSize(size, size);
        holder.setMouseTransparent(true);
        holder.getStyleClass().add("tool-profile-icon");
        holder.setAccessibleText(description(profile));
        return holder;
    }

    static String path(ToolProfile profile) {
        if (profile == ToolProfile.B)
            return "M11 2 H21 V13 H23 V23 A7 7 0 0 1 9 23 V13 H11 Z M11 13 H21 M10 17 L22 21 M11 23 L19 28";
        if (profile == ToolProfile.V)
            return "M11 2 H21 V13 H25 L17 30 H15 L7 13 H11 Z M11 13 H21 M12 16 L16 27";
        int teeth = switch (profile) { case C1 -> 1; case C2 -> 2; case C3 -> 3; case C4 -> 4; default -> throw new IllegalArgumentException(); };
        StringBuilder path = new StringBuilder("M4 2 H12 V12 H14 V29 H2 V12 H4 Z M4 12 H12 M3 17 L13 21 M3 23 L13 27 "
                + "M29 22 A6 6 0 1 1 17 22 A6 6 0 1 1 29 22 ");
        for (int tooth = 0; tooth < teeth; tooth++) {
            double angle = -Math.PI / 2 + tooth * 2 * Math.PI / teeth;
            path.append(String.format(Locale.ROOT, "M%.3f %.3f L%.3f %.3f ",
                    23 + 1.6 * Math.cos(angle), 22 + 1.6 * Math.sin(angle),
                    23 + 5.8 * Math.cos(angle), 22 + 5.8 * Math.sin(angle)));
        }
        return path.toString();
    }
}
