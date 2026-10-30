package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.control.*;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class CamWorkspaceTest {
    @TempDir Path directory;
    @Test void viewingNccStaysCleanButRawDraftEditsProtectClosingAndProjectReplacement() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            s.release.countDown(); s.window.saveProject(directory.resolve("clean.fcnproj"));
            TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                assertFalse(s.window.hasUnsavedProjectChanges(), "viewing defaults must stay clean");
                var margin = ((Spinner<?>) panel(s).getContent().lookup("#ncc-margin")).getEditor();
                margin.setText("unfinished"); assertTrue(s.window.hasUnsavedProjectChanges(), "even an invalid raw draft must be protected");
                var closed = new java.util.concurrent.atomic.AtomicInteger();
                s.window.setProjectDecisionProvider(editor -> UnsavedChangesGuard.Choice.CANCEL);
                s.window.requestClose(closed::incrementAndGet); assertEquals(0, closed.get());
                call(s.window, "newProject", new Class<?>[]{}); assertEquals("unfinished", margin.getText());
                s.window.setProjectDecisionProvider(editor -> UnsavedChangesGuard.Choice.DISCARD);
                s.window.requestClose(closed::incrementAndGet); assertEquals(1, closed.get()); return null;
            });
        }
    }
    @Test void nativeProjectRestoresNccDraftAndOriginalGenerationRecipe() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var handle = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var form = panel(s).getContent();
                ((TableView<?>) form.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                ((Spinner<?>) form.lookup("#ncc-margin")).getEditor().setText("3.1415926535");
                ((ComboBox<NccMethod>) form.lookup("#ncc-method")).setValue(NccMethod.LINES);
                ((Button) form.lookup("#ncc-generate")).fire(); return job(s);
            });
            finish(s, handle);
            Path file = directory.resolve("rascunho-área.fcnproj"); s.window.saveProject(file); s.window.openProject(file);
            TerminalPanelTest.fx(() -> {
                var gerbers = (Map<TreeItem<String>, GerberImage>) field(s.window, "gerberByItem");
                var source = gerbers.entrySet().iterator().next();
                var notes = (Map<?, ?>) field(s.window, "camResultNotes"); assertEquals(1, notes.size());
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, source.getKey(), source.getValue());
                var form = panel(s).getContent();
                assertEquals("3.1415926535", ((Spinner<?>) form.lookup("#ncc-margin")).getEditor().getText());
                assertEquals(NccMethod.LINES, ((ComboBox<?>) form.lookup("#ncc-method")).getValue());
                assertFalse(s.window.hasUnsavedProjectChanges(), "opening a restored draft must remain clean");
                return null;
            });
        }
    }
    @Test void editsDuringSaveRemainUnsavedAndExplicitCloseReturnsToLastSavedDraft() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            s.release.countDown(); Path file = directory.resolve("snapshot.fcnproj"); s.window.saveProject(file);
            var done = new java.util.concurrent.atomic.AtomicReference<Boolean>();
            var handle = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var margin = ((Spinner<?>) panel(s).getContent().lookup("#ncc-margin")).getEditor(); margin.setText("1.23456789");
                var project = call(s.window, "snapshotProject", new Class<?>[]{});
                var before = call(s.window, "projectStateStamp", new Class<?>[]{});
                call(s.window, "writeProject", new Class<?>[]{java.io.File.class, org.flatcam.app.project.ProjectFile.class,
                        ProjectChangeTracker.State.class, java.util.function.Consumer.class}, file.toFile(), project, before,
                        (java.util.function.Consumer<Boolean>) done::set);
                margin.setText("9.87654321"); return job(s);
            });
            finish(s, handle);
            TerminalPanelTest.fx(() -> {
                assertTrue(s.window.hasUnsavedProjectChanges()); assertEquals(Boolean.FALSE, done.get());
                assertEquals("9.87654321", ((Spinner<?>) panel(s).getContent().lookup("#ncc-margin")).getEditor().getText());
                call(s.window, "closeToolPanel", new Class<?>[]{});
                assertFalse(s.window.hasUnsavedProjectChanges(), "explicit close discards only the unsaved form");
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                assertEquals("1.23456789", ((Spinner<?>) panel(s).getContent().lookup("#ncc-margin")).getEditor().getText());
                assertFalse(s.window.hasUnsavedProjectChanges()); return null;
            });
            var saved = org.flatcam.app.project.ProjectFileIO.load(file);
            assertEquals("1.23456789", new org.json.JSONObject(saved.camWorkspace().get("ncc/gerber/board")).getString("margin"));
        }
    }
    @Test void draftEditedWhileProjectLoadsIsNotSilentlyReplaced() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            Path file = directory.resolve("loading.fcnproj"); s.window.saveProject(file);
            var handle = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                call(s.window, "openProjectFromMenu", new Class<?>[]{Path.class}, file);
                ((Spinner<?>) panel(s).getContent().lookup("#ncc-margin")).getEditor().setText("changed while loading");
                return job(s);
            });
            finish(s, handle);
            TerminalPanelTest.fx(() -> {
                assertTrue(s.window.hasUnsavedProjectChanges());
                assertEquals("changed while loading", ((Spinner<?>) panel(s).getContent().lookup("#ncc-margin")).getEditor().getText());
                assertTrue(((Map<?, ?>) field(s.window, "gerberByItem")).containsKey(s.item)); return null;
            });
        }
    }
    @Test void savingAnUnfinishedDiameterAndTableUndoBothTrackTheActualSavedDraft() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            s.release.countDown(); Path file = directory.resolve("diameter-draft.fcnproj");
            s.window.saveProject(file);
            TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var form = panel(s).getContent(); var codec = CamToolState.of(form);
                var state = codec.capture().get(); state.getJSONArray("tools").getJSONObject(0).put("diameterDraft", "0,");
                codec.restore().accept(state);
                assertTrue(s.window.hasUnsavedProjectChanges()); return null;
            });
            s.window.saveProject(file); assertFalse(TerminalPanelTest.fx(s.window::hasUnsavedProjectChanges));
            s.window.openProject(file);
            TerminalPanelTest.fx(() -> {
                var source = ((Map<TreeItem<String>, GerberImage>) field(s.window, "gerberByItem")).entrySet().iterator().next();
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, source.getKey(), source.getValue());
                var form = panel(s).getContent(); var table = (TableView<?>) form.lookup("#ncc-tools");
                assertEquals("0,", CamToolState.of(form).capture().get().getJSONArray("tools").getJSONObject(0).getString("diameterDraft"));
                assertFalse(s.window.hasUnsavedProjectChanges());
                var codec = CamToolState.of(form); var state = codec.capture().get();
                state.getJSONArray("tools").getJSONObject(0).remove("diameterDraft"); codec.restore().accept(state);
                assertTrue(s.window.hasUnsavedProjectChanges()); return null;
            });
            s.window.saveProject(file);
            TerminalPanelTest.fx(() -> {
                var form = panel(s).getContent(); var table = (TableView<?>) form.lookup("#ncc-tools");
                table.getSelectionModel().clearAndSelect(0);
                ((ComboBox<NccMethod>) form.lookup("#ncc-method")).setValue(NccMethod.LINES);
                assertTrue(s.window.hasUnsavedProjectChanges());
                table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.Z, false, true, false, false));
                assertFalse(s.window.hasUnsavedProjectChanges(), "undo must return to the actual saved state");
                table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.Y, false, true, false, false));
                assertTrue(s.window.hasUnsavedProjectChanges()); return null;
            });
        }
    }
    @Test void draftCodecPreservesImportedMachiningWithoutRounding() throws Exception {
        TerminalPanelTest.fx(() -> {
            var output = new java.util.concurrent.atomic.AtomicReference<Object>();
            var original = CamNumericInputTest.form("NCC", "IN", output);
            var codec = CamToolState.of(original); var saved = codec.capture().get();
            var params = new org.flatcam.cam.gcode.GeometryGCodeParameters(.1, .01, false, .01, 2.123456789, 12345, false);
            var machining = new org.flatcam.app.project.LegacyToolsDatabase.MillingTool("fine", .00781234567,
                    org.flatcam.cam.geometry.ToolProfile.C1, params, null);
            var row = saved.getJSONArray("tools").getJSONObject(0);
            row.put("diameter", machining.diameter()).put("machining", org.flatcam.app.project.LegacyToolsDatabase.millingDraftToJson(machining));
            row.put("overlap", "37.5123456").put("offset", "0.00123456789").put("offsetEnabled", true);
            saved.put("selected", new org.json.JSONArray(List.of(0)));
            var reopened = CamNumericInputTest.form("NCC", "IN", output);
            CamToolState.of(reopened).restore().accept(new org.json.JSONObject(saved.toString()));
            ((Button) reopened.lookup("#ncc-generate")).fire();
            var result = (NccToolPanel.Result) output.get(); assertNotNull(result);
            assertEquals(machining, result.machining().get(machining.diameter()));
            assertEquals(37.5123456 / 100, result.parameters().settingsFor(machining.diameter()).overlapFraction());
            assertEquals(.00123456789, result.parameters().settingsFor(machining.diameter()).copperOffset());
            return null;
        });
    }
    @Test void changedGeometryCannotReuseSavedDraftWithSameNameAndUnits() throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = CamNumericInputTest.form("NCC", "MM", new java.util.concurrent.atomic.AtomicReference<>());
            var codec = CamToolState.of(form); var saved = codec.capture().get();
            saved.put("sourceSignature", "obsolete");
            assertThrows(IllegalArgumentException.class, () -> codec.restore().accept(saved)); return null;
        });
    }
    @Test void profileChangedAfterDatabaseImportCanBeSavedAsADraftButStillNeedsReviewBeforeGeneration() throws Exception {
        TerminalPanelTest.fx(() -> {
            var output = new java.util.concurrent.atomic.AtomicReference<Object>();
            var form = CamNumericInputTest.form("NCC", "MM", output); var codec = CamToolState.of(form); var saved = codec.capture().get();
            var row = saved.getJSONArray("tools").getJSONObject(0); double diameter = row.getDouble("diameter");
            var parameters = new org.flatcam.cam.gcode.GeometryGCodeParameters(2, .1, false, .1, 123.456789, 12345, false);
            var imported = new org.flatcam.app.project.LegacyToolsDatabase.MillingTool("imported", diameter,
                    org.flatcam.cam.geometry.ToolProfile.C1, parameters, null);
            row.put("profile", "C1").put("machining", org.flatcam.app.project.LegacyToolsDatabase.millingDraftToJson(imported));
            saved.put("selected", new org.json.JSONArray(List.of(0))); codec.restore().accept(saved);
            // This is the valid intermediate draft produced by changing TT after a database import.
            var draft = codec.capture().get(); draft.getJSONArray("tools").getJSONObject(0).put("profile", "C2");
            var reopened = CamNumericInputTest.form("NCC", "MM", output); var reopenedCodec = CamToolState.of(reopened);
            assertDoesNotThrow(() -> reopenedCodec.restore().accept(new org.json.JSONObject(draft.toString())));
            assertEquals("C2", reopenedCodec.capture().get().getJSONArray("tools").getJSONObject(0).getString("profile"));
            ((Button) reopened.lookup("#ncc-generate")).fire(); assertNull(output.get(), "restoring a draft must not bypass machining validation");
            draft.getJSONArray("tools").getJSONObject(0).put("profile", "C1"); reopenedCodec.restore().accept(draft);
            ((Button) reopened.lookup("#ncc-generate")).fire(); assertNotNull(output.get());
            assertEquals(imported, ((NccToolPanel.Result) output.get()).machining().get(diameter)); return null;
        });
    }
}
