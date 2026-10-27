package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** File > Scripting: New / Open / Run Script and the script editor tab. */
@EnabledOnOs(OS.WINDOWS)
class ScriptingTest {
    @TempDir Path directory;

    private static Object field(MainWindow window, String name) throws Exception {
        var f = MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f.get(window);
    }

    private final class Session implements AutoCloseable {
        final Preferences prefs = Preferences.userRoot().node("flatcam-fx-tests/scripting-" + UUID.randomUUID());
        final JobExecutor jobs = new JobExecutor(1);
        final MainWindow window;
        Session() throws Exception {
            window = TerminalPanelTest.fx(() -> { var w = new MainWindow(jobs, new RecentFiles(prefs)); w.createScene(); return w; });
        }
        TabPane tabs() throws Exception { return (TabPane) field(window, "centerTabs"); }
        ScriptEditorPanel selectedScript() throws Exception {
            return TerminalPanelTest.fx(() -> (ScriptEditorPanel) tabs().getSelectionModel().getSelectedItem().getContent());
        }
        void waitTerminalIdle() throws Exception {
            TerminalPanel terminal = (TerminalPanel) TerminalPanelTest.fx(() -> field(window, "terminalPanel"));
            terminal.whenIdle().get(20, TimeUnit.SECONDS);
            TerminalPanelTest.fx(() -> null);
        }
        @Override public void close() throws Exception {
            TerminalPanelTest.fx(() -> {
                ((FluidTooltips) ((javafx.scene.Scene) field(window, "scene")).getProperties().get("fx.tip.handler")).closeNow();
                window.disposeViewport(); return null;
            });
            jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }

    @Test void runScriptExecutesTheWholeFileInTheTerminal() throws Exception {
        try (var s = new Session()) {
            Path script = directory.resolve("make.FlatScript");
            Files.writeString(script, "# comment\nset name geo\nnew_geometry ${name}_a\n"
                    + "foreach n {1 2} {\n    new_geometry ${name}_$n\n}\n");
            assertTrue(TerminalPanelTest.fx(() -> s.window.runScriptFile(script)));
            s.waitTerminalIdle();
            assertEquals(List.of("geo_a", "geo_1", "geo_2"), TerminalPanelTest.fx(s.window::objectNames));
            assertFalse(TerminalPanelTest.fx(() -> s.window.runScriptFile(directory.resolve("missing.FlatScript"))));
        }
    }

    @Test void openScriptShowsTheFileWithoutRunningItAndReusesItsTab() throws Exception {
        try (var s = new Session()) {
            Path script = directory.resolve("open.tcl");
            Files.writeString(script, "new_geometry should_not_run\n");
            TerminalPanelTest.fx(() -> { s.window.openScript(script); s.window.openScript(script); return null; });
            ScriptEditorPanel panel = s.selectedScript();
            assertEquals("new_geometry should_not_run\n", TerminalPanelTest.fx(panel::text));
            assertEquals(script.toAbsolutePath(), panel.file());
            assertFalse(TerminalPanelTest.fx(panel::isDirty));
            assertEquals(1, TerminalPanelTest.fx(() -> s.tabs().getTabs().stream()
                    .filter(tab -> tab.getContent() instanceof ScriptEditorPanel).count()).intValue());
            assertTrue(TerminalPanelTest.fx(s.window::objectNames).isEmpty());

            assertTrue(TerminalPanelTest.fx(panel::runScript));
            s.waitTerminalIdle();
            assertEquals(List.of("should_not_run"), TerminalPanelTest.fx(s.window::objectNames));
        }
    }

    @Test void newScriptUsesThePythonTemplateAndUnsavedTabsAskBeforeClosing() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> { s.window.newScript(); return null; });
            ScriptEditorPanel panel = s.selectedScript();
            assertEquals(ScriptEditorPanel.NEW_SCRIPT_TEMPLATE, TerminalPanelTest.fx(panel::text));
            assertTrue(TerminalPanelTest.fx(panel::isDirty), "a new script has never been saved");
            assertTrue(TerminalPanelTest.fx(panel::title).startsWith("new_script"));
            List<ScriptEditorPanel> asked = new ArrayList<>();
            TerminalPanelTest.fx(() -> {
                Tab tab = s.tabs().getSelectionModel().getSelectedItem();
                s.window.setScriptDiscardDecision(p -> { asked.add(p); return false; });
                var request = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
                tab.getOnCloseRequest().handle(request);
                assertTrue(request.isConsumed(), "keeping the tab when the user cancels");
                s.window.setScriptDiscardDecision(p -> true);
                var second = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
                tab.getOnCloseRequest().handle(second);
                assertFalse(second.isConsumed());
                return null;
            });
            assertEquals(List.of(panel), asked);
        }
    }

    @Test void scriptEditorSavesAtomicallyAndTracksChanges() throws Exception {
        Path target = directory.resolve("saved.FlatScript");
        List<String> runs = new ArrayList<>();
        ScriptEditorPanel panel = TerminalPanelTest.fx(() -> new ScriptEditorPanel("set a 1\n", null, new ScriptEditorPanel.Host() {
            @Override public boolean run(String script, String label) { runs.add(label + ":" + script); return runs.size() == 1; }
            @Override public Path chooseSaveTarget(Path suggested) { return target; }
            @Override public void log(String message) { }
        }));
        TerminalPanelTest.fx(() -> {
            assertTrue(panel.isDirty());
            assertTrue(panel.save());
            assertEquals("set a 1\n", Files.readString(target));
            assertFalse(panel.isDirty());
            assertEquals(target.toAbsolutePath(), panel.file());
            assertEquals("saved.FlatScript", panel.title());
            assertTrue(panel.runScript());
            assertFalse(panel.runScript(), "a busy Terminal refuses a second script");
            assertEquals(List.of("saved.FlatScript:set a 1\n", "saved.FlatScript:set a 1\n"), runs);
            panel.close();
            return null;
        });
        try (var stream = Files.list(directory)) {
            assertEquals(List.of(target), stream.toList(), "no staging files left behind");
        }
        assertNotNull(Map.of());
    }

    @Test void applicationExitCannotDiscardAnUnsavedScriptWithoutConfirmation() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                s.window.newScript();
                ScriptEditorPanel script = (ScriptEditorPanel) s.tabs().getSelectionModel().getSelectedItem().getContent();
                AtomicBoolean exited = new AtomicBoolean();
                List<ScriptEditorPanel> asked = new ArrayList<>();
                s.window.setScriptDiscardDecision(panel -> { asked.add(panel); return false; });
                s.window.setApplicationClose(() -> exited.set(true));
                s.window.requestApplicationClose();
                assertEquals(List.of(script), asked);
                assertFalse(exited.get());
                assertTrue(script.isDirty());
                assertTrue(s.tabs().getTabs().stream().anyMatch(tab -> tab.getContent() == script));
                return null;
            });
        }
    }

    @Test void exitChecksEveryDirtyScriptButDoesNotDiscardTabsWhenALaterOneCancels() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                s.window.newScript(); s.window.newScript();
                var scripts = s.tabs().getTabs().stream().map(Tab::getContent)
                        .filter(ScriptEditorPanel.class::isInstance).map(ScriptEditorPanel.class::cast).toList();
                List<ScriptEditorPanel> asked = new ArrayList<>(); AtomicBoolean exited = new AtomicBoolean();
                s.window.setScriptDiscardDecision(panel -> { asked.add(panel); return asked.size() == 1; });
                s.window.requestClose(() -> exited.set(true));
                assertEquals(scripts, asked); assertFalse(exited.get());
                assertTrue(scripts.stream().allMatch(ScriptEditorPanel::isDirty));
                assertEquals(2, s.tabs().getTabs().stream().filter(tab -> tab.getContent() instanceof ScriptEditorPanel).count());
                asked.clear(); s.window.setScriptDiscardDecision(panel -> { asked.add(panel); return true; });
                s.window.requestClose(() -> exited.set(true));
                assertTrue(exited.get()); assertEquals(scripts, asked);
                return null;
            });
        }
    }

    @Test void anAlreadySavedScriptDoesNotAskToDiscardOnExit() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                var clean = standaloneScript("set a 1", directory.resolve("saved.tcl"), directory.resolve("saved.tcl"));
                s.tabs().getTabs().add(new Tab("saved", clean));
                s.window.setScriptDiscardDecision(panel -> fail("A clean script must not prompt"));
                AtomicBoolean exited = new AtomicBoolean(); s.window.requestClose(() -> exited.set(true));
                assertTrue(exited.get()); clean.close(); return null;
            });
        }
    }

    private static ScriptEditorPanel standaloneScript(String text, Path file, Path saveTarget) {
        return new ScriptEditorPanel(text, file, new ScriptEditorPanel.Host() {
            public boolean run(String script, String label) { return false; }
            public Path chooseSaveTarget(Path suggested) { return saveTarget; }
            public void log(String message) { }
        });
    }

    @Test void savingAScriptDuringTheExitDecisionMustSucceedBeforeClosing() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                Path destination = directory.resolve("exit.tcl");
                var script = standaloneScript("set a 1", null, destination);
                s.tabs().getTabs().add(new Tab("script", script));
                s.window.setScriptDiscardDecision(ScriptEditorPanel::save);
                AtomicBoolean exited = new AtomicBoolean(); s.window.requestClose(() -> exited.set(true));
                assertTrue(exited.get()); assertFalse(script.isDirty());
                assertEquals("set a 1", Files.readString(destination)); script.close(); return null;
            });
        }
    }

    @Test void failedScriptSaveCancelsExitAndRetainsTheDraft() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                var script = standaloneScript("set a 1", null, directory.resolve("missing/exit.tcl"));
                s.tabs().getTabs().add(new Tab("script", script));
                s.window.setScriptDiscardDecision(ScriptEditorPanel::save);
                AtomicBoolean exited = new AtomicBoolean(); s.window.requestClose(() -> exited.set(true));
                assertFalse(exited.get()); assertTrue(script.isDirty()); script.close(); return null;
            });
        }
    }

    @Test void editsOrNewDraftsDuringOtherExitDialogsRequireANewDecision() throws Exception {
        for (boolean createAnother : List.of(false, true)) try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                s.window.newScript();
                ScriptEditorPanel first = (ScriptEditorPanel) s.tabs().getSelectionModel().getSelectedItem().getContent();
                s.window.newScript();
                s.window.setScriptDiscardDecision(panel -> {
                    if (panel != first) {
                        if (createAnother) s.window.newScript();
                        else ((CodeEditor) first.lookup("#script-editor-code")).setText("set changed 1");
                    }
                    return true;
                });
                AtomicBoolean exited = new AtomicBoolean(); s.window.requestClose(() -> exited.set(true));
                assertFalse(exited.get()); assertTrue(first.isDirty()); return null;
            });
        }
    }
}
