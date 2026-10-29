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
                return null;
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
}
