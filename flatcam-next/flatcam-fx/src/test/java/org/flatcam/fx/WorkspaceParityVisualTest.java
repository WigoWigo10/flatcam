package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Actual merged workspace, offscreen: no preference changes, project files or synthetic mouse claims. */
@EnabledOnOs(OS.WINDOWS)
class WorkspaceParityVisualTest {
    static Object field(MainWindow window, String name) throws Exception {
        var member = MainWindow.class.getDeclaredField(name); member.setAccessible(true); return member.get(window);
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void mergedScriptShortcutsAndMeasurementViewsRenderAcrossThemes(ThemeOption theme) throws Exception {
        Preferences prefs = Preferences.userRoot().node("flatcam-fx-tests/workspace-visual-" + UUID.randomUUID());
        JobExecutor jobs = new JobExecutor(1);
        MainWindow window = TerminalPanelTest.fx(() -> {
            MainWindow created = new MainWindow(jobs, new RecentFiles(prefs));
            var selectedTheme = MainWindow.class.getDeclaredField("currentTheme"); selectedTheme.setAccessible(true);
            selectedTheme.set(created, theme);
            ((BooleanProperty) field(created, "darkIcons")).set(theme.isDark());
            Scene scene = created.createScene(); theme.applyTo(scene);
            ((PlotAreaView) field(created, "plotAreaView")).applyTheme(theme);
            ((javafx.scene.layout.Region) scene.getRoot()).setPrefSize(1280, 800);
            return created;
        });
        try {
            Parent root = TerminalPanelTest.fx(() -> ((Scene) field(window, "scene")).getRoot());
            TerminalPanelTest.fx(() -> { window.newScript(); return null; });
            var tabs = TerminalPanelTest.fx(() -> (TabPane) field(window, "centerTabs"));
            var script = TerminalPanelTest.fx(() -> (ScriptEditorPanel) tabs.getSelectionModel().getSelectedItem().getContent());
            TerminalPanelTest.fx(() -> {
                assertTrue(script.isDirty()); assertNotNull(script.lookup("#script-save"));
                render(root, theme, "script"); return null;
            });
            TerminalPanelTest.fx(() -> { window.openShortcutList(); window.openShortcutList(); return null; });
            TerminalPanelTest.fx(() -> {
                var shortcuts = (ShortcutListPanel) tabs.getSelectionModel().getSelectedItem().getContent();
                var table = (TableView<?>) shortcuts.lookup("#shortcut-table");
                assertTrue(table.getItems().size() > 20);
                assertEquals(1, tabs.getTabs().stream().filter(tab -> tab.getContent() instanceof ShortcutListPanel).count());
                render(root, theme, "shortcuts");
                return null;
            });
            TerminalPanelTest.fx(() -> { window.openMeasureTool(); return null; });
            TerminalPanelTest.fx(() -> {
                assertNotNull(field(window, "measurePanel")); render(root, theme, "measure");
                assertTrue(script.isDirty(), "Opening other views must not discard a script"); return null;
            });
        } finally {
            TerminalPanelTest.fx(() -> {
                ((TabPane) field(window, "centerTabs")).getTabs().stream()
                        .map(tab -> tab.getContent()).filter(ScriptEditorPanel.class::isInstance)
                        .map(ScriptEditorPanel.class::cast).forEach(ScriptEditorPanel::close);
                ((FluidTooltips) ((Scene) field(window, "scene")).getProperties().get("fx.tip.handler")).closeNow();
                window.disposeViewport();
                return null;
            });
            jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }

    private static void render(Parent root, ThemeOption theme, String view) throws Exception {
        for (double width : new double[]{1024, 1280}) {
            root.resize(width, 800); root.applyCss(); root.layout();
            assertEquals(width, root.getLayoutBounds().getWidth(), .01);
            assertNotNull(root.lookup(".tab-pane"));
        }
        if (Boolean.getBoolean("flatcam.tests.snapshots")) {
            var snapshot = root.snapshot(null, null);
            var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            javax.imageio.ImageIO.write(image, "png", Path.of("target", "workspace-" + view + "-" + theme + ".png").toFile());
        }
    }
}
