package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledOnOs(OS.WINDOWS)
class DrillingPositionsPanelTest {
    private DrillGCodeToolPanel.SourceCandidate source(String name, boolean saved) {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "%", "T1", "X10.0Y0.0", "M30"));
        var settings = saved ? new DrillCncSettings(GCodePreprocessor.DEFAULT,
                new GCodeGenerator.DrillJobOptions(true, 15, .5, null, null).withPositions(20.0, 0.0, 5.0),
                List.of(1), DrillCncSettings.ToolOrder.NO) : null;
        return new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>(name), image,
                Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, saved)), settings);
    }
    private Parent root(List<DrillGCodeToolPanel.SourceCandidate> sources, AtomicReference<DrillGCodeToolPanel.Result> result) {
        var root = (Parent) DrillGCodeToolPanel.build(sources, sources.getFirst(), List::of, result::set, () -> {});
        new Scene(root, 410, 1600); root.applyCss(); root.layout(); return root;
    }
    private TextField field(Parent root, String id) {
        return (TextField) root.lookup(id.equals("tool-change-z") ? "#probe-change-z" : "#drill-" + id);
    }
    private void generate(Parent root) { ((Button) root.lookup("#drill-generate")).fire(); }

    @Test void savedPositionsRestoreAndDoNotLeakAcrossSourcesOrReset() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var saved = source("saved", true); var clean = source("clean", false);
            var root = root(List.of(saved, clean), result);
            assertEquals("20.0", field(root, "start-z").getText());
            assertEquals("0.0;5.0", field(root, "tool-change-xy").getText());
            generate(root); assertNotNull(result.get()); assertEquals(saved.cncSettings(), result.get().cncSettings());
            var combo = (ComboBox<DrillGCodeToolPanel.SourceCandidate>) root.lookup("#drill-source");
            combo.setValue(clean);
            assertEquals("None", field(root, "start-z").getText());
            assertEquals("None", field(root, "tool-change-xy").getText());
            combo.setValue(saved); ((Button) root.lookup("#drill-reset")).fire();
            assertEquals("None", field(root, "start-z").getText());
            assertEquals("None", field(root, "tool-change-xy").getText());
            result.set(null); generate(root); assertNotNull(result.get()); assertNull(result.get().options().startZ());
            return null;
        });
    }

    @Test void decimalCommaSemicolonAndNegativeCoordinatesGenerateUnambiguousPositions() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>(); var root = root(List.of(source("saved", true)), result);
            field(root, "start-z").setText("0,5"); field(root, "tool-change-xy").setText("-1,25;2,5");
            generate(root); assertNotNull(result.get(), ((Label) root.lookup("#drill-error")).getText());
            assertEquals(.5, result.get().options().startZ()); assertEquals(-1.25, result.get().options().toolChangeX());
            assertEquals(2.5, result.get().options().toolChangeY());
            assertTrue(GCodeGenerator.generateDrillCncJob(result.get().source().image(), result.get().settingsByTool(),
                    result.get().orderedToolIds(), result.get().options()).gcode().contains("X-1.2500 Y2.5000"));
            return null;
        });
    }

    @Test void inchSourceUsesInchDefaultsAndLeavesOptionalPositionsInThoseUnits() throws Exception {
        TerminalPanelTest.fx(() -> {
            var image = new ExcellonParser().parse(List.of("M48", "INCH", "T1C0.04", "%", "T1", "X1.0Y0.0", "M30"));
            var source = new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("inch"), image,
                    Map.of(1, new DrillGCodeParameters(.12, .04, 4, 0, true)));
            var result = new AtomicReference<DrillGCodeToolPanel.Result>(); var root = root(List.of(source), result);
            assertEquals("0.6", field(root, "tool-change-z").getText());
            assertEquals("0.02", field(root, "end-z").getText());
            field(root, "start-z").setText("0.8"); field(root, "tool-change-xy").setText("-0.1;0.2");
            generate(root); assertNotNull(result.get(), ((Label) root.lookup("#drill-error")).getText());
            assertEquals(.8, result.get().options().startZ()); assertEquals(.2, result.get().options().toolChangeY());
            assertEquals("IN", result.get().source().image().units()); return null;
        });
    }

    @ParameterizedTest @ValueSource(strings = {"1", "1;", "1;2;", "NaN;2", "Infinity;2"})
    void invalidCoordinatesDoNotDispatchAJob(String value) throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>(); var root = root(List.of(source("saved", true)), result);
            field(root, "tool-change-xy").setText(value); generate(root);
            assertNull(result.get()); assertFalse(((Label) root.lookup("#drill-error")).getText().isBlank()); return null;
        });
    }

    @Test void lowChangeHeightAndDisabledToolChangeRefuseInsteadOfDroppingXy() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>(); var root = root(List.of(source("saved", true)), result);
            field(root, "tool-change-z").setText("1"); generate(root); assertNull(result.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("Travel Z"));
            ((CheckBox) root.lookup("#drill-tool-change")).setSelected(false); generate(root); assertNull(result.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("Tool change ativado"));
            field(root, "tool-change-xy").setText("None"); generate(root); assertNotNull(result.get()); return null;
        });
    }

    @Test void switchingToUnsupportedProfileRequiresClearingPositionsAndAutomaticProfileUsesHeight() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>(); var root = root(List.of(source("saved", true)), result);
            var profile = (ComboBox<GCodePreprocessor>) root.lookup("#drill-preprocessor");
            profile.setValue(GCodePreprocessor.ROLAND_MDX_20); generate(root); assertNull(result.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("Start Z"));
            field(root, "start-z").setText("None"); field(root, "tool-change-xy").setText("None");
            generate(root); assertNotNull(result.get()); result.set(null);
            profile.setValue(GCodePreprocessor.ISEL_ICP_CNC);
            assertFalse(field(root, "tool-change-z").isDisabled());
            field(root, "tool-change-z").setText("18"); field(root, "tool-change-xy").setText("0;5");
            generate(root); assertNotNull(result.get()); assertEquals(18, result.get().options().toolChangeZ()); return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void commonPositionFieldsRenderWithTooltipsInEveryTheme(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var root = root(List.of(source("saved", true)), new AtomicReference<>());
            root.getStyleClass().add("tool-panel"); theme.applyTo(root.getScene()); root.applyCss(); root.layout();
            var grid = (Parent) field(root, "start-z").getParent();
            assertNotNull(field(root, "start-z").getTooltip()); assertNotNull(field(root, "tool-change-xy").getTooltip());
            assertTrue(field(root, "tool-change-xy").getWidth() >= 90);
            for (var label : grid.lookupAll(".label")) if (label instanceof Label caption && !caption.isWrapText())
                assertTrue(caption.getWidth() >= caption.prefWidth(-1), caption.getText());
            var parameters = new javafx.scene.SnapshotParameters();
            var backgrounds = ((javafx.scene.layout.Region) root).getBackground();
            if (backgrounds != null && !backgrounds.getFills().isEmpty()) parameters.setFill(backgrounds.getFills().getFirst().getFill());
            else parameters.setFill(theme.isDark() ? javafx.scene.paint.Color.web("#303030") : javafx.scene.paint.Color.WHITE);
            var snapshot = grid.snapshot(parameters, null);
            var picture = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < picture.getHeight(); y++) for (int x = 0; x < picture.getWidth(); x++)
                picture.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            var path = java.nio.file.Path.of("target", "drilling-positions-" + theme.name() + ".png");
            java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(picture, "png", path.toFile()); return null;
        });
    }
}
