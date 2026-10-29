package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.NccMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class CamDraftResumeTest {
    @Test void sameSourceButtonAndSwitchingToolsKeepTheNccDraft() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            s.release.countDown();
            TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var original = panel(s); Node form = original.getContent();
                ((Spinner<?>) form.lookup("#ncc-margin")).getEditor().setText("3.14159");
                ((ComboBox<NccMethod>) form.lookup("#ncc-method")).setValue(NccMethod.LINES);
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                assertSame(original, panel(s), "Reopening the same source must resume, not reset");
                call(s.window, "generateIsolation", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                call(s.window, "openSelectedNccTool", new Class<?>[]{});
                assertSame(original, panel(s));
                assertEquals("3.14159", ((Spinner<?>) form.lookup("#ncc-margin")).getEditor().getText());
                assertEquals(NccMethod.LINES, ((ComboBox<?>) form.lookup("#ncc-method")).getValue());
                call(s.window, "closeToolPanel", new Class<?>[]{});
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                assertNotSame(original, panel(s), "Explicit close discards this session draft");
                return null;
            });
        }
    }
    @Test void globalNccButtonResumesAfterGeneratedGeometryBecomesSelected() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var original = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                return panel(s);
            });
            finish(s, s.start("NCC", false));
            TerminalPanelTest.fx(() -> {
                ((TabPane) field(s.window, "leftTabs")).getSelectionModel().select((Tab) field(s.window, "propertiesTab"));
                call(s.window, "openSelectedNccTool", new Class<?>[]{});
                assertSame(original, panel(s));
                assertSame(field(s.window, "toolTab"), ((TabPane) field(s.window, "leftTabs")).getSelectionModel().getSelectedItem());
                return null;
            });
        }
    }
    @Test void replacedSourceOrProjectCannotResumeStaleInputs() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            s.release.countDown();
            TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var original = panel(s);
                var replacement = GerberImage.of("MM", java.util.Map.of(), s.image.solidGeometry().copy(),
                        s.image.followGeometry(), java.util.Map.of());
                ((java.util.Map<TreeItem<String>, GerberImage>) field(s.window, "gerberByItem")).put(s.item, replacement);
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, replacement);
                assertNotSame(original, panel(s));
                call(s.window, "clearProject", new Class<?>[]{});
                call(s.window, "openSelectedNccTool", new Class<?>[]{});
                assertFalse(((Tab) field(s.window, "toolTab")).getContent().lookupAll("#ncc-generate").size() > 0);
                return null;
            });
        }
    }
}
