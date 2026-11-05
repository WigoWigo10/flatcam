package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class LanguagePickerTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void realPreferencesShowsSvgInSelectedValueAndEveryPopupRowWithoutSavingTheDraft(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var view = PreferencesPaneTest.view(theme);
            PreferencesPaneTest.select(view, "general");
            @SuppressWarnings("unchecked") var choice = (ComboBox<UiLanguage>)view.pane().lookup("#preferences-language");
            Scene scene = view.scene();
            Stage stage = new Stage(); stage.setOpacity(0); stage.setX(-20000); stage.setY(-20000); stage.setScene(scene);
            try {
                stage.show(); view.pane().applyCss(); view.pane().layout();
                assertEquals(List.of(UiLanguage.values()), List.copyOf(choice.getItems()));
                assertTrue(choice.getStyleClass().contains("language-choice"));
                for (UiLanguage language : UiLanguage.values()) {
                    choice.setValue(language); view.pane().applyCss(); view.pane().layout();
                    assertFlagCell(choice.getButtonCell(), language);
                    assertEquals(0, view.host().languageWrites, "selection remains a draft");
                }
                assertEquals(UiLanguage.PT_BR, view.host().language);
                ((Button)view.pane().lookup("#preferences-general-apply")).fire();
                assertEquals(UiLanguage.FR, view.host().language);
                assertEquals(1, view.host().languageWrites);
                ((Button)view.pane().lookup("#preferences-general-reset")).fire();
                view.pane().applyCss(); view.pane().layout();
                assertFlagCell(choice.getButtonCell(), UiLanguage.PT_BR);
                assertEquals(UiLanguage.FR, view.host().language, "reset also remains a draft");
                choice.show();
                var popup = ((ComboBoxListViewSkin<?>)choice.getSkin()).getPopupContent();
                popup.getScene().getWindow().setOpacity(0);
                popup.applyCss(); popup.autosize();
                if (popup instanceof javafx.scene.Parent parent) parent.layout();
                var cells = popup.lookupAll(".list-cell").stream()
                        .filter(n -> n instanceof ListCell<?> cell && !cell.isEmpty()).toList();
                assertEquals(4, cells.size());
                for (Node node : cells) {
                    var cell = (ListCell<?>)node;
                    assertFlagCell(cell, (UiLanguage)cell.getItem());
                }
                snapshot(popup, "popup-" + theme);
                snapshot(view.pane(), "preferences-" + theme);
                choice.hide();
                var empty = choice.getCellFactory().call(null);
                empty.updateIndex(-1);
                assertNull(empty.getText()); assertNull(empty.getGraphic()); assertNull(empty.getAccessibleText());
            } finally { choice.hide(); stage.close(); }
            return null;
        });
    }

    private static void assertFlagCell(ListCell<?> cell, UiLanguage language) {
        assertEquals(language.label, cell.getText());
        assertEquals(language.label, cell.getAccessibleText());
        Node graphic = cell.getGraphic(); assertNotNull(graphic);
        assertTrue(graphic.getStyleClass().contains("language-flag"));
        assertFalse(graphic.lookupAll("SVGPath").isEmpty());
        assertTrue(graphic.lookupAll("ImageView").isEmpty(), "flags must remain vectors");
        assertEquals(22, graphic.getLayoutBounds().getWidth(), .01);
        assertEquals(22, graphic.getLayoutBounds().getHeight(), .01);
    }

    @Test void flagsRetainCircularTransparencyAtDifferentRenderSizesAndCarryTheirLicense() throws Exception {
        TerminalPanelTest.fx(() -> {
            for (UiLanguage language : UiLanguage.values()) for (double size : List.of(22., 44., 66.)) {
                var icon = (Region)LanguageFlagIcons.icon(language, size);
                icon.resize(size, size); icon.layout();
                SnapshotParameters parameters = new SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
                var image = icon.snapshot(parameters, null);
                assertEquals((int)size, (int)image.getWidth());
                assertEquals((int)size, (int)image.getHeight());
                assertEquals(0, image.getPixelReader().getColor(0,0).getOpacity(), .001, language.tag);
                assertTrue(image.getPixelReader().getColor((int)(size/2),(int)(size/2)).getOpacity() > .99, language.tag);
            }
            var first = LanguageFlagIcons.icon(UiLanguage.EN, 22);
            var second = LanguageFlagIcons.icon(UiLanguage.EN, 22);
            assertNotSame(first, second, "cells must never share a reparented node");
            String license = AboutInfo.resource("circle-flags-LICENSE.txt");
            assertTrue(license.contains("HatScripts")); assertTrue(license.contains("Permission is hereby granted"));
            assertTrue(license.contains("English: FX composition"));
            assertThrows(IllegalArgumentException.class, () -> LanguageFlagIcons.icon(null,22));
            assertThrows(IllegalArgumentException.class, () -> LanguageFlagIcons.icon(UiLanguage.EN,Double.NaN));
            return null;
        });
    }

    private static void snapshot(Node node, String name) throws Exception {
        Path output = Path.of("target/language-flags-review"); Files.createDirectories(output);
        var image = node.snapshot(null, null);
        var buffered = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<buffered.getHeight(); y++) for (int x=0; x<buffered.getWidth(); x++)
            buffered.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(buffered,"png",output.resolve(name+".png").toFile());
    }
}
