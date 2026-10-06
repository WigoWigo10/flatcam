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
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class DrillingExclusionPanelTest {
    private final GeometryFactory factory = new GeometryFactory();
    private CncExclusionArea area() {
        return CncExclusionArea.of(factory.toGeometry(new Envelope(4, 6, -1, 1)), CncExclusionArea.Strategy.AROUND, 0);
    }
    private DrillGCodeToolPanel.SourceCandidate source(String name, boolean active, List<CncExclusionArea> areas) {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "%", "T1", "X10.0Y0.0", "M30"));
        var options = new GCodeGenerator.DrillJobOptions(false, 15, .5, 0.0, 0.0).withExclusions(active, areas);
        return new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>(name), image,
                Map.of(1, new DrillGCodeParameters(3, 1, 100, 0, false)),
                new DrillCncSettings(GCodePreprocessor.FX_PORTABLE, options, List.of(1), DrillCncSettings.ToolOrder.NO));
    }
    private Parent root(List<DrillGCodeToolPanel.SourceCandidate> sources, AtomicReference<DrillGCodeToolPanel.Result> result) {
        var root = (Parent) DrillGCodeToolPanel.build(sources, sources.getFirst(), List::of, result::set, () -> {});
        new Scene(root, 410, 1000);
        root.applyCss(); root.layout();
        ((TitledPane) root.lookup("#cnc-exclusions-pane")).setExpanded(true);
        root.applyCss(); root.layout();
        return root;
    }
    @SuppressWarnings("unchecked") private TableView<CncExclusionArea> table(Parent root) {
        return (TableView<CncExclusionArea>) root.lookup("#cnc-exclusions");
    }
    private void generate(Parent root) { ((Button) root.lookup("#drill-generate")).fire(); }

    @Test void savedExclusionsReopenEditStrategyAndOverHeightAndGenerateWithThoseOptions() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var root = root(List.of(source("holes", true, List.of(area()))), result);
            assertEquals(List.of(area()), table(root).getItems());
            table(root).getSelectionModel().selectFirst();
            ((ComboBox<CncExclusionArea.Strategy>) root.lookup("#cnc-exclusion-strategy")).setValue(CncExclusionArea.Strategy.OVER);
            ((TextField) root.lookup("#cnc-exclusion-over-z")).setText("12");
            ((Button) root.lookup("#cnc-exclusion-apply")).fire();
            generate(root);
            assertNotNull(result.get(), ((Label) root.lookup("#drill-error")).getText());
            assertEquals(CncExclusionArea.Strategy.OVER, result.get().options().exclusions().getFirst().strategy());
            assertEquals(12, result.get().options().exclusions().getFirst().overZ());
            assertTrue(result.get().options().exclusionsEnabled());
            assertTrue(GCodeGenerator.generateDrillCncJob(result.get().source().image(), result.get().settingsByTool(),
                    result.get().orderedToolIds(), result.get().options()).gcode().contains("Z12.0000"));
            return null;
        });
    }

    @Test void activeAreasRefuseRolandButInactiveDraftsAreKeptAndAccepted() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var root = root(List.of(source("holes", true, List.of(area()))), result);
            ((ComboBox<GCodePreprocessor>) root.lookup("#drill-preprocessor")).setValue(GCodePreprocessor.ROLAND_MDX_20);
            generate(root);
            assertNull(result.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("Exclusoes"));
            ((CheckBox) root.lookup("#cnc-exclusions-enabled")).setSelected(false);
            generate(root);
            assertNotNull(result.get(), ((Label) root.lookup("#drill-error")).getText());
            assertFalse(result.get().options().exclusionsEnabled());
            assertEquals(List.of(area()), result.get().options().exclusions());
            return null;
        });
    }

    @Test void switchingSourceDoesNotLeakAreasAndResetClearsTheSavedDraftToo() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var saved = source("saved", true, List.of(area()));
            var clean = source("clean", false, List.of());
            var root = root(List.of(saved, clean), result);
            var combo = (ComboBox<DrillGCodeToolPanel.SourceCandidate>) root.lookup("#drill-source");
            combo.setValue(clean);
            generate(root);
            assertNotNull(result.get());
            assertTrue(result.get().options().exclusions().isEmpty());
            combo.setValue(saved);
            generate(root);
            assertEquals(List.of(area()), result.get().options().exclusions());
            combo.setValue(clean);
            ((Button) root.lookup("#drill-reset")).fire();
            generate(root);
            assertSame(saved, result.get().source());
            assertTrue(result.get().options().exclusions().isEmpty());
            assertFalse(result.get().options().exclusionsEnabled());
            return null;
        });
    }

    @Test void numericRectangleAddsSelectsPreviewsAndDeletesAndEmptyActiveStateIsRefused() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var preview = new AtomicReference<Geometry>();
            var source = source("clean", false, List.of());
            var root = (Parent) DrillGCodeToolPanel.build(List.of(source), source, List::of, null, preview::set,
                    () -> {}, result::set, () -> {});
            new Scene(root); root.applyCss(); root.layout();
            ((TitledPane) root.lookup("#cnc-exclusions-pane")).setExpanded(true);
            root.applyCss(); root.layout();
            ((Button) root.lookup("#cnc-exclusion-add")).fire();
            assertEquals(1, table(root).getItems().size());
            assertNotNull(preview.get());
            ((Button) root.lookup("#cnc-exclusion-delete")).fire();
            assertNull(preview.get());
            generate(root);
            assertNull(result.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("ao menos uma area"));
            return null;
        });
    }

    @Test void drawingCallbacksAndCancellationOnSourceChangeAreWiredAndStandaloneDrawingIsDisabled() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var sources = List.of(source("one", false, List.of()), source("two", false, List.of()));
            var selected = new AtomicReference<java.util.function.Consumer<Geometry>>();
            var shapeKind = new AtomicReference<Boolean>();
            var cancels = new java.util.concurrent.atomic.AtomicInteger();
            var root = (Parent) DrillGCodeToolPanel.build(sources, sources.getFirst(), List::of,
                    (polygon, accept, cancel) -> { selected.set(accept); shapeKind.set(polygon); },
                    shape -> {}, cancels::incrementAndGet, result::set, () -> {});
            new Scene(root); root.applyCss(); root.layout();
            ((TitledPane) root.lookup("#cnc-exclusions-pane")).setExpanded(true);
            root.applyCss(); root.layout();
            ((Button) root.lookup("#cnc-exclusion-draw-polygon")).fire();
            assertEquals(Boolean.TRUE, shapeKind.get());
            selected.get().accept(area().geometry());
            assertEquals(1, table(root).getItems().size());
            int before = cancels.get();
            ((ComboBox<DrillGCodeToolPanel.SourceCandidate>) root.lookup("#drill-source")).setValue(sources.getLast());
            assertTrue(cancels.get() > before);
            assertTrue(table(root).getItems().isEmpty());
            var standalone = root(sources, result);
            assertTrue(((Button) standalone.lookup("#cnc-exclusion-draw-rectangle")).isDisabled());
            return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void exclusionControlsRenderInEveryTheme(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            var source = source("holes", true, List.of(area()));
            var root = (Parent) DrillGCodeToolPanel.build(List.of(source), source, List::of,
                    (polygon, selected, cancelled) -> {}, shape -> {}, () -> {}, result::set, () -> {});
            root.getStyleClass().add("tool-panel");
            var scene = new Scene(root, 410, 1700);
            theme.applyTo(scene);
            ToolPanelIcons.decorate(root, file -> Icons.fromResource((theme.isDark() ? "dark/" : "") + file, 16));
            var pane = (TitledPane) root.lookup("#cnc-exclusions-pane");
            pane.setAnimated(false); pane.setExpanded(true);
            root.applyCss(); root.layout();
            table(root).getSelectionModel().selectFirst();
            root.applyCss(); root.layout();
            assertNotNull(((Button) root.lookup("#cnc-exclusion-draw-rectangle")).getGraphic());
            assertNotNull(pane.getGraphic());
            assertEquals(3, table(root).getColumns().size());
            var snapshot = pane.snapshot(null, null);
            assertTrue(snapshot.getWidth() >= 300);
            assertTrue(snapshot.getHeight() > 200);
            var picture = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < picture.getHeight(); y++) for (int x = 0; x < picture.getWidth(); x++)
                picture.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            java.nio.file.Path output = java.nio.file.Path.of("target", "drilling-exclusion-" + theme.name() + ".png");
            java.nio.file.Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(picture, "png", output.toFile());
            return null;
        });
    }
}
