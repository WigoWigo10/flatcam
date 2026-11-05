package org.flatcam.fx;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/** Native cells keep language names and keyboard navigation alongside decorative SVG flags. */
final class LanguagePicker {
    private LanguagePicker() { }

    static void decorate(ComboBox<UiLanguage> choice) {
        choice.getStyleClass().add("language-choice");
        choice.setCellFactory(view -> cell(false));
        choice.setButtonCell(cell(true));
    }

    private static ListCell<UiLanguage> cell(boolean compact) {
        return new ListCell<>() {
            {
                setAlignment(Pos.CENTER_LEFT);
                setGraphicTextGap(8);
                if (compact) setStyle("-fx-padding: 0 0 0 2;");
            }
            @Override protected void updateItem(UiLanguage language, boolean empty) {
                super.updateItem(language, empty);
                if (empty || language == null) {
                    setText(null); setGraphic(null); setAccessibleText(null);
                } else {
                    setText(language.label);
                    setGraphic(LanguageFlagIcons.icon(language, 22));
                    setAccessibleText(language.label);
                }
            }
        };
    }
}
