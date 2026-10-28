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
                setGraphic(icon(profile, compact ? 14 : 40, textFillProperty()));
                setAccessibleText(profile.name() + ": " + description(profile));
            }
        };
    }

    static String description(ToolProfile profile) {
        return switch (profile) {
            case C1 -> I18n.text("ui.f5085accfd4b");
            case C2 -> I18n.text("ui.f77e9f00cee3");
            case C3 -> I18n.text("ui.4242d1f815a0");
            case C4 -> I18n.text("ui.4370b8104a9c");
            case B -> I18n.text("ui.5040372c7260");
            case V -> I18n.text("ui.70f830fb105f");
        };
    }

    private static void updateHelp(ComboBox<ToolProfile> choice, ToolProfile profile) {
        String title = profile == null ? I18n.text("ui.2a93896585c1") : profile.name() + " — " + description(profile);
        String text = profile == null ? I18n.text("ui.38340108c894")
                : profile == ToolProfile.V
                ? I18n.text("ui.aac477b68304")
                : I18n.text("ui.1ce6bb3cc42c");
        // The window's FluidTooltips consumes these properties; avoid a second native popup.
        choice.setTooltip(null);
        ToolDescriptions.apply(choice.getProperties(), title, text);
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
        // Side views of real cutters: shank, shoulder, chamfered or rounded cutting end, helical flutes.
        if (profile == ToolProfile.B)
            return "M11 2 H21 V10 L22 12 V21 A6 6 0 0 1 10 21 V12 L11 10 Z M11 10 H21 "
                    + "M10 20 Q16 17 22 13 M10.5 25 Q16 22 21.5 18";
        if (profile == ToolProfile.V)
            return "M11 2 H21 V10 L22 12 L17 30 H15 L10 12 L11 10 Z M11 10 H21 "
                    + "M11.5 15 Q15.5 15 17.5 20 M13.5 22 Q15.5 22 16.5 26";
        int teeth = switch (profile) { case C1 -> 1; case C2 -> 2; case C3 -> 3; case C4 -> 4; default -> throw new IllegalArgumentException(); };
        StringBuilder path = new StringBuilder("M4 2 H12 V10 L13 12 V27 L11.5 29.5 H4.5 L3 27 V12 L4 10 Z M4 10 H12 "
                + "M3 22 Q8 20 13 14.5 M3 27 Q8 25 13 20 "
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
