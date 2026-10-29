package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class PanelTextContrastTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void inlineDiameterEditorUsesTheInputPaletteInsideSelectedRows(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form=CamNumericInputTest.form("NCC","MM",new java.util.concurrent.atomic.AtomicReference<>());
            form.getStyleClass().add("tool-panel");var shell=new VBox(form);shell.getStyleClass().add("side-panel");
            var scene=new Scene(shell,450,1400);theme.applyTo(scene);shell.applyCss();shell.layout();
            var table=(TableView<?>)form.lookup("#ncc-tools");table.getSelectionModel().clearAndSelect(0);
            table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.F2,false,false,false,false));
            shell.applyCss();shell.layout();var input=(TextField)table.lookup("#tool-diameter-editor");assertNotNull(input);
            Color background=(Color)input.getBackground().getFills().getLast().getFill();
            assertReadable(input,background,"inline diameter / "+theme);
            if(Boolean.getBoolean("flatcam.tests.snapshots")){
                var snapshot=table.snapshot(null,null);var output=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
                javax.imageio.ImageIO.write(output,"png",java.nio.file.Path.of("target","table-edit-"+theme+".png").toFile());
            }
            return null;
        });
    }
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void generationFeedbackRemainsReadableInEveryTheme(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = (VBox) CamNumericInputTest.form("NCC", "MM", new java.util.concurrent.atomic.AtomicReference<>());
            var feedback = CamGenerationFeedback.of(form);
            feedback.started("FlatCAM FX\nOrigem: Placa de avalia\u00e7\u00e3o [MM]\n0.1981234567 mm; Seed; Connect: false\nMargem: 1.23456789", new TreeItem<>("board"));
            feedback.completed(new TreeItem<>("board_ncc"), item -> {});
            form.getChildren().remove(feedback);
            ((TitledPane) feedback.getChildren().get(1)).setAnimated(false);
            ((TitledPane) feedback.getChildren().get(1)).setExpanded(true);
            var shell = new VBox(feedback); shell.setPadding(new javafx.geometry.Insets(12));
            shell.getStyleClass().addAll("side-panel", "tool-panel");
            var scene = new Scene(shell, 460, 600); theme.applyTo(scene); shell.applyCss(); shell.layout();
            Color background = (Color) shell.getBackground().getFills().getFirst().getFill();
            assertReadable(feedback.getChildren().getFirst(), background, "generation feedback / " + theme);
            var textArea = (TextArea) ((TitledPane) feedback.getChildren().get(1)).getContent().lookup(".text-area");
            var region = (javafx.scene.layout.Region) textArea.lookup(".content");
            Paint paint = region.getBackground().getFills().getLast().getFill();
            Color editorBackground = paint instanceof Color color ? color : ((LinearGradient) paint).getStops().getLast().getColor();
            assertReadable(textArea, editorBackground, "generation recipe / " + theme);
            if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                var snapshot = shell.snapshot(null, null);
                var output = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y=0;y<output.getHeight();y++) for (int x=0;x<output.getWidth();x++) output.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
                javax.imageio.ImageIO.write(output,"png", java.nio.file.Path.of("target","ux-feedback-"+theme+".png").toFile());
            }
            return null;
        });
    }
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void panelCaptionsAndInputsStayReadableAcrossThemeAndDisabledStateChanges(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            for (String panelClass : List.of("tool-panel", "object-panel")) {
                Label margin = new Label("Margin:");
                CheckBox connect = new CheckBox("Connect");
                connect.setSelected(true);
                RadioButton choice = new RadioButton("Itself");
                TextField value = new TextField("0.0");
                Spinner<Double> spinner = new Spinner<>(0.0, 100.0, 0.0, 0.1);
                spinner.setEditable(true);
                VBox panel = new VBox(8, margin, connect, choice, value, spinner);
                panel.getStyleClass().add(panelClass);
                Scene scene = new Scene(panel, 340, 220);
                theme.applyTo(scene);
                for (ThemeOption current : List.of(theme, theme.isDark() ? ThemeOption.ICE_LIGHT : ThemeOption.CLASSIC_DARK, theme)) {
                    current.applyTo(scene);
                    for (boolean disabled : new boolean[]{false, true, false}) {
                        for (Node control : panel.getChildren()) control.setDisable(disabled);
                        panel.applyCss(); panel.layout();
                        Color background = (Color) panel.getBackground().getFills().getFirst().getFill();
                        for (Labeled control : List.of(margin, connect, choice)) {
                            assertReadable(control, background, current + " / " + panelClass + " / disabled=" + disabled);
                        }
                        for (TextField input : List.of(value, spinner.getEditor())) {
                            Paint paint = input.getBackground().getFills().getLast().getFill();
                            Color inputBackground = paint instanceof Color color ? color
                                    : ((LinearGradient) paint).getStops().getLast().getColor();
                            assertReadable(input, inputBackground, current + " / " + panelClass + " / input disabled=" + disabled);
                        }
                    }
                }
                if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                    var snapshot = panel.snapshot(null, null);
                    var output = new java.awt.image.BufferedImage((int) snapshot.getWidth(),
                            (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                        output.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(output, "png", java.nio.file.Path.of("target",
                            "panel-text-" + theme + "-" + panelClass + ".png").toFile());
                }
            }
            return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void actualNccMarginAndBothConnectControlsStayReadableWithRestAndMultipleSelection(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var source = new NccToolPanel.SourceCandidate(new TreeItem<>("board"), "board", "MM", true,
                    new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 10)));
            Parent content = (Parent) NccToolPanel.build(List.of(source), source, List.of(),
                    (chosen, shape, done, cancel) -> false, () -> {}, List::of, result -> {}, () -> {});
            content.getStyleClass().add("tool-panel");
            VBox shell = new VBox(content); shell.getStyleClass().add("side-panel");
            Scene scene = new Scene(shell, 450, 1400); theme.applyTo(scene);
            shell.applyCss(); shell.layout();
            TableView<?> table = (TableView<?>) content.lookup(".table-view");
            CheckBox rest = (CheckBox) content.lookupAll(".check-box").stream()
                    .filter(node -> ((CheckBox) node).getText().equals("Rest Machining")).findFirst().orElseThrow();
            for (boolean useRest : new boolean[]{false, true, false}) {
                rest.setSelected(useRest);
                for (boolean multiple : new boolean[]{false, true}) {
                    table.getSelectionModel().clearAndSelect(0);
                    if (multiple) table.getSelectionModel().selectAll();
                    shell.applyCss(); shell.layout();
                    Color background = (Color) shell.getBackground().getFills().getFirst().getFill();
                    var captions = new java.util.ArrayList<Node>(content.lookupAll(".label"));
                    captions.addAll(content.lookupAll(".check-box"));
                    int checked = 0;
                    for (Node node : captions) {
                        Labeled caption = (Labeled) node;
                        if ("Connect".equals(caption.getText()) || "Margin (comum):".equals(caption.getText())) {
                            assertReadable(caption, background, theme + " / NCC / rest=" + useRest + " / multiple=" + multiple);
                            checked++;
                        }
                    }
                    assertEquals(3, checked, "Margin and both Connect captions must be audited");
                }
            }
            return null;
        });
    }

    private static void assertReadable(Node control, Color background, String context) {
        Text text = (Text) control.lookup(".text");
        assertNotNull(text, control.toString());
        Color fill = (Color) text.getFill();
        // Include opacity on the skin and the control: a light fill can still become unreadable when faded twice.
        double opacity = 1;
        for (Node node = text; node != null; node = node.getParent()) opacity *= node.getOpacity();
        Color visible = background.interpolate(fill, opacity * fill.getOpacity());
        assertTrue(contrast(visible, background) >= 4.5,
                context + " / " + control + " fill=" + fill + " opacity=" + opacity);
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color) {
        double sum = 0;
        double[] channels = {color.getRed(), color.getGreen(), color.getBlue()}, weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) sum += weights[i] * (channels[i] <= 0.04045 ? channels[i] / 12.92
                : Math.pow((channels[i] + 0.055) / 1.055, 2.4));
        return sum;
    }
}
