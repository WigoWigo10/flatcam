package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class TableSelectionThemeTest {
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    private static final class Fixture {
        final TableView<String> table = new TableView<>();
        final VBox root = new VBox(table);
        final Scene scene = new Scene(root, 380, 190);

        Fixture(String containerClass) {
            if (!containerClass.isEmpty()) root.getStyleClass().add(containerClass);
            TableColumn<String, String> diameter = new TableColumn<>("Diameter");
            diameter.setPrefWidth(150);
            diameter.setCellValueFactory(value -> new SimpleStringProperty(value.getValue()));
            if (containerClass.equals("object-panel")) diameter.setCellFactory(column -> new TableCell<>() {
                { getStyleClass().add("total-cell"); }
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty); setText(empty ? null : item);
                }
            });
            TableColumn<String, String> profile = new TableColumn<>("TT");
            profile.setPrefWidth(180);
            profile.setCellValueFactory(value -> new SimpleStringProperty("C1"));
            profile.setCellFactory(column -> new TableCell<>() {
                final ComboBox<String> choice = new ComboBox<>();
                { choice.getItems().setAll("C1", "C2"); choice.getStyleClass().add("table-editor-combo"); }
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    choice.setValue(item); setGraphic(empty ? null : choice);
                }
            });
            table.getColumns().add(diameter); table.getColumns().add(profile);
            table.getItems().setAll("1.0", "0.5", "0.3");
            table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            table.getSelectionModel().selectFirst();
            table.getFocusModel().focus(0);
            root.resize(380, 190);
        }

        void apply(ThemeOption theme) { theme.applyTo(scene); root.applyCss(); root.layout(); }

        TableRow<?> row(int index) {
            return table.lookupAll(".table-row-cell").stream()
                    .filter(node -> node instanceof TableRow<?> row && row.getIndex() == index)
                    .map(node -> (TableRow<?>) node).findFirst().orElseThrow();
        }
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void initialUnfocusedSelectionAndEmbeddedEditorUseThemeColors(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            for (String container : List.of("", "tool-panel", "object-panel")) {
                Fixture fixture = new Fixture(container);
                fixture.apply(theme);
                assertFalse(fixture.table.isFocused());
                assertEquals(0, fixture.table.getSelectionModel().getSelectedIndex());
                assertRow(fixture.row(0), token(theme, "-fc-selection-bg-unfocused"),
                        token(theme, "-fc-selection-text-unfocused"));
                assertEditor(fixture.row(0), theme);
                if (Boolean.getBoolean("flatcam.tests.snapshots") && container.equals("tool-panel")) {
                    var snapshot = fixture.root.snapshot(null, null);
                    var output = new java.awt.image.BufferedImage((int) snapshot.getWidth(),
                            (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                        output.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(output, "png",
                            Path.of("target", "table-selection-" + theme + ".png").toFile());
                }
            }
            return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void focusHoverReselectionAndThemeSwitchDoNotLoseContrast(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            for (String container : List.of("", "tool-panel", "object-panel")) {
                Fixture fixture = new Fixture(container);
                fixture.apply(theme.isDark() ? ThemeOption.ICE_LIGHT : ThemeOption.CLASSIC_DARK);
                fixture.apply(theme);
                fixture.table.getSelectionModel().select(1);
                // Simulate the table's CSS focus, not a row FocusModel index: they are independent.
                fixture.table.pseudoClassStateChanged(FOCUSED, true);
                fixture.row(0).pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
                fixture.root.applyCss();
                assertRow(fixture.row(0), token(theme, "-fc-selection-row"), token(theme, "-fc-selection-row-text"));
                assertRow(fixture.row(1), token(theme, "-fc-selection-row"), token(theme, "-fc-selection-row-text"));
                assertEditor(fixture.row(0), theme);
                fixture.table.pseudoClassStateChanged(FOCUSED, false);
                fixture.root.applyCss();
                assertRow(fixture.row(0), token(theme, "-fc-selection-bg-unfocused"),
                        token(theme, "-fc-selection-text-unfocused"));
                fixture.table.getSelectionModel().clearSelection(0);
                fixture.root.applyCss();
                TableCell<?, ?> cell = textCell(fixture.row(0));
                Color deselectedText = token(theme, container.equals("object-panel") ? "-fc-accent" : "-fc-panel-text");
                assertEquals(deselectedText, cell.getTextFill());
                assertEquals(deselectedText, ((Text) cell.lookup(".text")).getFill());
                assertEquals(List.of(1), List.copyOf(fixture.table.getSelectionModel().getSelectedIndices()));
            }
            return null;
        });
    }

    private static TableCell<?, ?> textCell(TableRow<?> row) {
        return row.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?> cell && cell.getText() != null)
                .map(node -> (TableCell<?, ?>) node).findFirst().orElseThrow();
    }

    private static void assertRow(TableRow<?> row, Color background, Color foreground) {
        assertTrue(row.isSelected());
        assertEquals(background, row.getBackground().getFills().getFirst().getFill());
        TableCell<?, ?> cell = textCell(row);
        assertEquals(foreground, cell.getTextFill());
        assertEquals(foreground, ((Text) cell.lookup(".text")).getFill());
        double first = luminance(background), second = luminance(foreground);
        assertTrue((Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05) >= 4.5);
    }

    private static void assertEditor(TableRow<?> row, ThemeOption theme) throws Exception {
        var label = row.lookup(".combo-box > .list-cell");
        assertNotNull(label);
        assertEquals(token(theme, "-fc-panel-text"), ((Text) label.lookup(".text")).getFill());
    }

    private static Color token(ThemeOption theme, String token) throws Exception {
        String name = "theme/vars-" + theme.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + ".css";
        try (var stream = ThemeOption.class.getResourceAsStream(name)) {
            assertNotNull(stream);
            var matcher = Pattern.compile(Pattern.quote(token) + ":\\s*(#[0-9a-fA-F]{6})")
                    .matcher(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(matcher.find()); return Color.web(matcher.group(1));
        }
    }

    private static double luminance(Color color) {
        double result = 0;
        double[] channels = {color.getRed(), color.getGreen(), color.getBlue()}, weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) result += weights[i] * (channels[i] <= 0.04045 ? channels[i] / 12.92
                : Math.pow((channels[i] + 0.055) / 1.055, 2.4));
        return result;
    }
}
